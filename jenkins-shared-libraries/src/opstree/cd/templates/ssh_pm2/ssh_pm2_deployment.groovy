package opstree.cd.templates.ssh_pm2

import opstree.common.*

def get_params_value(Boolean enableOverride, Map step_params, String paramName) {
    def value = enableOverride && params.containsKey(paramName) ? params[paramName] : step_params[paramName]
    if (value instanceof String) {
        if (value.equalsIgnoreCase('true')) return true
        if (value.equalsIgnoreCase('false')) return false
    }
    return value
}

def call(Map step_params) {
    ansiColor('xterm') {
        def enableOverride = step_params.enable_jenkins_build_param_override?.toBoolean() ?: false

        workspace = new workspace_management()
        vcs       = new git_management()
        notify    = new notify()
        parser    = new parser()

        def repo_url_type       = get_params_value(enableOverride, step_params, 'repo_url_type') ?: 'http'
        def repo_url            = (repo_url_type == 'http') ? get_params_value(enableOverride, step_params, 'repo_https_url') : get_params_value(enableOverride, step_params, 'repo_ssh_url')
        def repo_branch         = params.BRANCH ?: (get_params_value(enableOverride, step_params, 'repo_branch') ?: 'main')
        def jenkins_git_creds_id= get_params_value(enableOverride, step_params, 'jenkins_git_creds_id') ?: 'hasantha-hrc-gitlab-access'
        def source_code_path    = get_params_value(enableOverride, step_params, 'source_code_path') ?: ''
        def clean_workspace     = get_params_value(enableOverride, step_params, 'clean_workspace') ?: true

        def server_ip           = get_params_value(enableOverride, step_params, 'server_ip')
        def deploy_dir          = get_params_value(enableOverride, step_params, 'deploy_dir')
        def app_name            = get_params_value(enableOverride, step_params, 'app_name')
        def node_version        = get_params_value(enableOverride, step_params, 'node_version') ?: 'v24.6.0'
        def node_delete_version = get_params_value(enableOverride, step_params, 'node_delete_version') ?: node_version
        def ssh_credentials_id  = get_params_value(enableOverride, step_params, 'ssh_credentials_id') ?: 'HRC-Kollect-Dev-Server'
        def pem_key_path        = get_params_value(enableOverride, step_params, 'pem_key_path') ?: '/opt/dev-keys/hrc-kollect-dev.pem'
        def secret_arn          = get_params_value(enableOverride, step_params, 'secret_arn') ?: ''
        def secret_region       = get_params_value(enableOverride, step_params, 'secret_region') ?: 'us-east-1'
        def custom_env_content  = get_params_value(enableOverride, step_params, 'custom_env_content') ?: ''
        def package_manager     = get_params_value(enableOverride, step_params, 'package_manager') ?: 'yarn'
        def run_db_migration    = get_params_value(enableOverride, step_params, 'run_db_migration') ?: false
        def build_app_on_server = get_params_value(enableOverride, step_params, 'build_app_on_server') ?: false
        def start_command       = get_params_value(enableOverride, step_params, 'start_command') ?: 'yarn start'
        def pm2_services_list   = get_params_value(enableOverride, step_params, 'pm2_services_list') ?: []
        def image_tag           = params.image_tag ?: 'latest'
        def environment_name    = params.ENVIRONMENT ?: 'dev'
        def tar_name            = "${app_name}.tar.gz"

        def deployInfo = [
            'App Name'        : app_name,
            'Target Server'   : server_ip,
            'Tag / Version'   : image_tag,
            'Branch'          : repo_branch,
            'Environment'     : environment_name,
            'Deploy Directory': deploy_dir
        ]

        try {
            stage('Get Pipeline ID') {
                currentBuild.description = "Deploying ${app_name} [Branch: ${repo_branch}] → ${environment_name} (${server_ip})"
                echo "Pipeline ID: ${currentBuild.number}"
            }

            if (repo_url) {
                stage('Git Checkout') {
                    vcs.git_checkout(
                        repo_url: "${repo_url}",
                        repo_branch: "${repo_branch}",
                        clean_workspace: "${clean_workspace}",
                        repo_url_type: "${repo_url_type}",
                        jenkins_git_creds_id: "${jenkins_git_creds_id}",
                        source_code_path: "${source_code_path}"
                    )
                }
            }

            stage('Clean Remote Directory') {
                sshagent([ssh_credentials_id]) {
                    sh """#!/bin/bash
                        ssh -o StrictHostKeyChecking=no ubuntu@${server_ip} '
                            mkdir -p ${deploy_dir} &&
                            cd ${deploy_dir} &&
                            rm -rf ./* .env .next 2>/dev/null || true
                        '
                    """
                }
            }

            stage('Copy Source Archive to Server') {
                def repo_dir = repo_url ? parser.fetch_git_repo_name('repo_url': "${repo_url}") : ''
                def src_path = repo_dir ? "${WORKSPACE}/${repo_dir}${source_code_path}" : "${WORKSPACE}"

                dir(src_path) {
                    sh """#!/bin/bash
                        set -e
                        tar -czf ${tar_name} .[!.]* * 2>/dev/null || tar -czf ${tar_name} *
                        scp -i ${pem_key_path} -o StrictHostKeyChecking=no \\
                            ./${tar_name} ubuntu@${server_ip}:${deploy_dir}/
                    """
                }
            }

            stage('Create .env Configuration') {
                sshagent([ssh_credentials_id]) {
                    if (custom_env_content) {
                        sh """#!/bin/bash
                            ssh -o StrictHostKeyChecking=no ubuntu@${server_ip} '
                                cd ${deploy_dir}
                                cat > .env << "EOF"
${custom_env_content}
EOF
                            '
                        """
                    } else if (secret_arn) {
                        sh """#!/bin/bash
                            ssh -o StrictHostKeyChecking=no ubuntu@${server_ip} '
                                cd ${deploy_dir} &&
                                touch .env &&
                                echo "NODE_ENV=production" > .env &&
                                echo "SECRET_KEY_MANAGER_KEY=${secret_arn}" >> .env &&
                                echo "SECRET_KEY_MANAGER_REGION=${secret_region}" >> .env
                            '
                        """
                    }
                }
            }

            stage('Remove Old PM2 Services') {
                sshagent([ssh_credentials_id]) {
                    if (pm2_services_list && pm2_services_list.size() > 0) {
                        for (service in pm2_services_list) {
                            def serviceRunning = sh(
                                script: """#!/bin/bash
                                    ssh -o StrictHostKeyChecking=no ubuntu@${server_ip} \\
                                    'source /home/ubuntu/.nvm/nvm.sh && nvm use ${node_delete_version} && \\
                                     pm2 jlist | jq -e \\'.[] | select(.name=="${service}")\\' > /dev/null'
                                """,
                                returnStatus: true
                            )
                            if (serviceRunning == 0) {
                                echo "Removing PM2 service: ${service}"
                                sh """#!/bin/bash
                                    ssh -o StrictHostKeyChecking=no ubuntu@${server_ip} \\
                                    'source /home/ubuntu/.nvm/nvm.sh && nvm use ${node_delete_version} && pm2 delete "${service}"'
                                """
                            }
                        }
                    } else {
                        def serviceRunning = sh(
                            script: """#!/bin/bash
                                ssh -o StrictHostKeyChecking=no ubuntu@${server_ip} \\
                                'source /home/ubuntu/.nvm/nvm.sh && nvm use ${node_delete_version} && pm2 list | grep -q "${app_name}"'
                            """,
                            returnStatus: true
                        )
                        if (serviceRunning == 0) {
                            echo "Removing running PM2 service: ${app_name}"
                            sh """#!/bin/bash
                                ssh -o StrictHostKeyChecking=no ubuntu@${server_ip} \\
                                'source /home/ubuntu/.nvm/nvm.sh && nvm use ${node_delete_version} && pm2 delete ${app_name}'
                            """
                        }
                    }
                }
            }

            stage('Deploy & Start Application') {
                sshagent([ssh_credentials_id]) {
                    def commands = [
                        "set -e",
                        "source /home/ubuntu/.nvm/nvm.sh",
                        "nvm use ${node_version}",
                        "node -v",
                        "cd ${deploy_dir}",
                        "tar -xzf ${tar_name}",
                        "rm -f ${tar_name}"
                    ]

                    if (package_manager == 'yarn') {
                        commands.add("yarn --frozen-lockfile || yarn install")
                    } else {
                        commands.add("npm install")
                    }

                    if (build_app_on_server.toBoolean()) {
                        if (package_manager == 'yarn') {
                            commands.add("yarn build")
                        } else {
                            commands.add("npm run build")
                        }
                    }

                    if (run_db_migration.toBoolean()) {
                        commands.add("yarn sequelize db:migrate || true")
                    }

                    if (pm2_services_list && pm2_services_list.size() > 0) {
                        commands.add("node start-crons.js")
                    } else {
                        commands.add("pm2 start \"${start_command}\" --name ${app_name}")
                    }

                    commands.add("pm2 save")

                    def fullScript = commands.join(" && \n")
                    sh """#!/bin/bash
                        ssh -o StrictHostKeyChecking=no ubuntu@${server_ip} '
${fullScript}
                        '
                    """
                }
            }

        } catch (Exception e) {
            currentBuild.result = 'FAILURE'
            if (get_params_value(enableOverride, step_params, 'notification_enabled') != null && get_params_value(enableOverride, step_params, 'notification_enabled').toBoolean()) {
                notify.notification_factory(
                    build_status: 'Failure',
                    webhook_url_creds_id: "${get_params_value(enableOverride, step_params, 'webhook_url_creds_id')}",
                    notification_channel: "${get_params_value(enableOverride, step_params, 'notification_channel') ?: 'gmail'}",
                    notification_enabled: "${get_params_value(enableOverride, step_params, 'notification_enabled')}",
                    gmail_notification_recipients_email_ids: "${get_params_value(enableOverride, step_params, 'gmail_notification_recipients_email_ids') ?: 'asangai@healthreconconnect.com'}",
                    gmail_notification_from_email_id: "${get_params_value(enableOverride, step_params, 'gmail_notification_from_email_id') ?: 'smtp@kernernorland.com'}",
                    deployment_details: deployInfo
                )
            }
            throw e
        } finally {
            if (get_params_value(enableOverride, step_params, 'notification_enabled') != null && get_params_value(enableOverride, step_params, 'notification_enabled').toBoolean()) {
                if (currentBuild.currentResult == 'SUCCESS' || currentBuild.currentResult == 'UNSTABLE') {
                    notify.notification_factory(
                        build_status: 'Success',
                        webhook_url_creds_id: "${get_params_value(enableOverride, step_params, 'webhook_url_creds_id')}",
                        notification_channel: "${get_params_value(enableOverride, step_params, 'notification_channel') ?: 'gmail'}",
                        notification_enabled: "${get_params_value(enableOverride, step_params, 'notification_enabled')}",
                        gmail_notification_recipients_email_ids: "${get_params_value(enableOverride, step_params, 'gmail_notification_recipients_email_ids') ?: 'asangai@healthreconconnect.com'}",
                        gmail_notification_from_email_id: "${get_params_value(enableOverride, step_params, 'gmail_notification_from_email_id') ?: 'smtp@kernernorland.com'}",
                        deployment_details: deployInfo
                    )
                }
            }

            if (get_params_value(enableOverride, step_params, 'clean_workspace') != null && get_params_value(enableOverride, step_params, 'clean_workspace').toBoolean()) {
                workspace.workspace_management(
                    clean_workspace: 'true',
                    ignore_clean_workspace_failure: 'false',
                    delete_dirs: 'false',
                    clean_when_build_aborted: 'true',
                    clean_when_build_failed: 'true',
                    clean_when_not_built: 'true',
                    clean_when_build_succeed: 'true',
                    clean_when_build_unstable: 'true'
                )
            }
        }
    }
}

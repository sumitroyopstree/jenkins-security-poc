package opstree.cd.templates.docker_ecr

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
        notify    = new notify()

        def app_name           = get_params_value(enableOverride, step_params, 'app_name')
        def server_ip          = get_params_value(enableOverride, step_params, 'server_ip') ?: '10.2.40.133'
        def ssh_credentials_id = get_params_value(enableOverride, step_params, 'ssh_credentials_id') ?: 'HRC-Kollect-Dev-Server'
        def pem_key_path       = get_params_value(enableOverride, step_params, 'pem_key_path') ?: '/opt/dev-keys/hrc-kollect-dev.pem'
        def ecr_repo           = get_params_value(enableOverride, step_params, 'ecr_repo')
        def ecr_account        = get_params_value(enableOverride, step_params, 'ecr_account') ?: '167121004129'
        def ecr_region         = get_params_value(enableOverride, step_params, 'ecr_region') ?: 'us-east-1'
        def container_port     = get_params_value(enableOverride, step_params, 'container_port') ?: '8080'
        def host_port          = get_params_value(enableOverride, step_params, 'host_port') ?: '8080'
        def image_tag          = params.image_tag ?: 'latest'
        def environment_name   = params.ENVIRONMENT ?: 'dev'
        def full_image_url     = "${ecr_account}.dkr.ecr.${ecr_region}.amazonaws.com/${ecr_repo}:${image_tag}"

        def deployInfo = [
            'App Name'      : app_name,
            'Target Server' : server_ip,
            'Docker Image'  : full_image_url,
            'Port Mapping'  : "${host_port}:${container_port}",
            'Tag / Version' : image_tag,
            'Environment'   : environment_name
        ]

        try {
            stage('Get Pipeline ID') {
                currentBuild.description = "Deploy Docker Container [Tag: ${image_tag}] → ${server_ip} (${environment_name})"
                echo "Pipeline ID: ${currentBuild.number}"
            }

            stage('Deploy & Start Container on Server') {
                sshagent([ssh_credentials_id]) {
                    sh """#!/bin/bash
                        ssh -o StrictHostKeyChecking=no ubuntu@${server_ip} '
                            set -e

                            # 1. Login to ECR on remote server
                            aws ecr get-login-password --region ${ecr_region} | \\
                            docker login --username AWS --password-stdin ${ecr_account}.dkr.ecr.${ecr_region}.amazonaws.com

                            # 2. Stop and remove existing container if running
                            docker stop ${app_name} 2>/dev/null || true
                            docker rm ${app_name} 2>/dev/null || true

                            # 3. Pull newest image tag from ECR
                            docker pull ${full_image_url}

                            # 4. Run new container
                            docker run -d \\
                                --name ${app_name} \\
                                --restart unless-stopped \\
                                -p ${host_port}:${container_port} \\
                                -e SPRING_PROFILES_ACTIVE=${environment_name} \\
                                ${full_image_url}

                            # 5. Clean up dangling images
                            docker image prune -f || true
                        '
                    """
                }
            }

            stage('Health Check') {
                sshagent([ssh_credentials_id]) {
                    sh """#!/bin/bash
                        sleep 10
                        ssh -o StrictHostKeyChecking=no ubuntu@${server_ip} '
                            RUNNING=\$(docker ps -q --filter "name=${app_name}" --filter "status=running")
                            if [ -n "\$RUNNING" ]; then
                                echo "Container ${app_name} is running healthy."
                            else
                                echo "ERROR: Container ${app_name} is not running!"
                                docker logs --tail 50 ${app_name} 2>/dev/null || true
                                exit 1
                            fi
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

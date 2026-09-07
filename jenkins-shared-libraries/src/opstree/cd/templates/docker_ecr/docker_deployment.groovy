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

        def workspace = new workspace_management()
        def notify    = new notify()

        def app_name              = get_params_value(enableOverride, step_params, 'app_name')
        def server_ip             = get_params_value(enableOverride, step_params, 'server_ip')
        def ssh_credentials_id    = get_params_value(enableOverride, step_params, 'ssh_credentials_id')
        def ecr_repo              = get_params_value(enableOverride, step_params, 'ecr_repo')
        def ecr_account           = get_params_value(enableOverride, step_params, 'ecr_account')
        def ecr_region            = get_params_value(enableOverride, step_params, 'ecr_region')
        def container_port        = get_params_value(enableOverride, step_params, 'container_port')
        def host_port             = get_params_value(enableOverride, step_params, 'host_port')
        def health_check_retries  = get_params_value(enableOverride, step_params, 'health_check_retries') ?: 6
        def health_check_interval = get_params_value(enableOverride, step_params, 'health_check_interval') ?: 5
        def image_tag             = params.image_tag ?: 'latest'
        def environment_name      = (params.ENVIRONMENT ?: step_params.environment)?.toUpperCase()

        if (!app_name || !server_ip || !ssh_credentials_id || !ecr_repo || !ecr_account || !ecr_region || !container_port || !host_port) {
            error "[ERROR] Mandatory deployment parameter missing! Ensure inventory.cd() provides: app_name, server_ip, ssh_credentials_id, ecr_repo, ecr_account, ecr_region, container_port, host_port."
        }

        def full_image_url        = "${ecr_account}.dkr.ecr.${ecr_region}.amazonaws.com/${ecr_repo}:${image_tag}"

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
                echo "[INFO] Target Server IP : ${server_ip}"
                echo "[INFO] Container Name   : ${app_name}"
                echo "[INFO] Port Mapping      : ${host_port}:${container_port}"
                echo "[INFO] SSH Credential ID : ${ssh_credentials_id}"
                sshagent([ssh_credentials_id]) {
                    sh """#!/bin/bash
                        ssh -o StrictHostKeyChecking=no ubuntu@${server_ip} '
                            set -e

                            # 1. Login to ECR on remote server
                            echo "[INFO] Logging into AWS ECR..."
                            aws ecr get-login-password --region ${ecr_region} | \\
                            docker login --username AWS --password-stdin ${ecr_account}.dkr.ecr.${ecr_region}.amazonaws.com

                            # 2. Pull newest image tag from ECR first (before stopping current container)
                            echo "[INFO] Pulling Docker image: ${full_image_url}..."
                            docker pull ${full_image_url}

                            # 3. Backup existing container for auto-rollback
                            docker rm -f ${app_name}_backup 2>/dev/null || true
                            if docker ps -a --filter "name=^/${app_name}\$" --format "{{.Names}}" | grep -q "^${app_name}\$"; then
                                echo "[INFO] Backing up existing container ${app_name} -> ${app_name}_backup..."
                                docker stop ${app_name} 2>/dev/null || true
                                docker rename ${app_name} ${app_name}_backup 2>/dev/null || true
                            fi

                            # 4. Run new container
                            echo "[INFO] Starting new container ${app_name}..."
                            docker run -d \\
                                --name ${app_name} \\
                                --restart unless-stopped \\
                                -p ${host_port}:${container_port} \\
                                -e SPRING_PROFILES_ACTIVE=${environment_name} \\
                                ${full_image_url}
                        '
                    """
                }
            }

            stage('Health Check') {
                sshagent([ssh_credentials_id]) {
                    sh """#!/bin/bash
                        ssh -o StrictHostKeyChecking=no ubuntu@${server_ip} '
                            echo "[INFO] Verifying health and stability for container ${app_name}..."
                            SUCCESS=false
                            ATTEMPTS=${health_check_retries}
                            INTERVAL=${health_check_interval}

                            for i in \$(seq 1 \$ATTEMPTS); do
                                sleep \$INTERVAL
                                STATUS=\$(docker inspect --format="{{.State.Status}}" ${app_name} 2>/dev/null || echo "not_found")
                                RESTARTING=\$(docker inspect --format="{{.State.Restarting}}" ${app_name} 2>/dev/null || echo "false")
                                echo "[INFO] Health check attempt \$i/\$ATTEMPTS: container status is \"\$STATUS\" (restarting: \$RESTARTING)"

                                if [ "\$STATUS" = "running" ] && [ "\$RESTARTING" != "true" ]; then
                                    SUCCESS=true
                                else
                                    SUCCESS=false
                                    if [ "\$STATUS" = "exited" ] || [ "\$STATUS" = "dead" ]; then
                                        echo "[ERROR] Container exited unexpectedly!"
                                        break
                                    fi
                                fi
                            done

                            if [ "\$SUCCESS" = "true" ]; then
                                echo "[SUCCESS] Container ${app_name} is running healthy and stable."
                                # Remove backup container as new deployment is verified
                                docker rm -f ${app_name}_backup 2>/dev/null || true
                                docker image prune -f 2>/dev/null || true
                            else
                                echo "[ERROR] Health check FAILED! Container ${app_name} is not stable."
                                echo "================= Container Logs (Last 100 Lines) ================="
                                docker logs --tail 100 ${app_name} 2>/dev/null || true
                                echo "==================================================================="

                                # AUTO-ROLLBACK TO PREVIOUS CONTAINER
                                if docker ps -a --filter "name=^/${app_name}_backup\$" --format "{{.Names}}" | grep -q "^${app_name}_backup\$"; then
                                    echo "[ROLLBACK] Initiating automatic rollback to previous container (${app_name}_backup)..."
                                    docker stop ${app_name} 2>/dev/null || true
                                    docker rm -f ${app_name} 2>/dev/null || true
                                    docker rename ${app_name}_backup ${app_name}
                                    docker start ${app_name}
                                    sleep 5
                                    ROLLBACK_STATUS=\$(docker inspect --format="{{.State.Status}}" ${app_name} 2>/dev/null || echo "failed")
                                    if [ "\$ROLLBACK_STATUS" = "running" ]; then
                                        echo "[ROLLBACK SUCCESS] Restored previous container ${app_name} successfully."
                                    else
                                        echo "[ROLLBACK FAILED] Previous container could not be started!"
                                    fi
                                else
                                    echo "[ROLLBACK] No backup container found. Unable to rollback."
                                fi
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

package opstree.cd.templates.s3_fe

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

        def s3_bucket          = get_params_value(enableOverride, step_params, 's3_bucket')
        def cloudfront_dist_id = get_params_value(enableOverride, step_params, 'cloudfront_dist_id')
        def aws_region         = get_params_value(enableOverride, step_params, 'aws_region') ?: 'us-east-1'
        def artifact_source    = get_params_value(enableOverride, step_params, 'artifact_source_path') ?: 'dist/'
        def image_tag          = params.image_tag ?: 'latest'
        def environment_name   = params.ENVIRONMENT ?: 'dev'

        def artifact_name_disp = get_params_value(enableOverride, step_params, 'artifact_name') ?: 'latest'
        def artifact_s3_bucket = get_params_value(enableOverride, step_params, 'artifact_s3_bucket_name') ?: ''
        def artifact_s3_path   = get_params_value(enableOverride, step_params, 'artifact_s3_keypath_destination') ?: ''
        def aws_creds_id       = get_params_value(enableOverride, step_params, 'jenkins_aws_credentials_id') ?: ''
        def artifact_s3_region = get_params_value(enableOverride, step_params, 'artifact_s3_bucket_aws_region') ?: aws_region
        def app_name           = get_params_value(enableOverride, step_params, 'app_name') ?: s3_bucket

        def deployInfo = [
            'Application'            : app_name,
            'Environment'            : environment_name.toUpperCase(),
            'Artifact'               : artifact_name_disp,
            'CI S3 Path'             : "s3://${artifact_s3_bucket}/${artifact_s3_path}/${artifact_name_disp}",
            'Target S3 Bucket'       : s3_bucket,
            'CloudFront Distribution': cloudfront_dist_id,
            'AWS Region'             : aws_region
        ]

        try {
            stage('Deployment Plan & Approval') {
                def detailLines = deployInfo.collect { k, v -> "${k.padRight(25)}: ${v}" }.join('\n')
                echo """
=======================================================
 DEPLOYMENT TARGET DETAILS
=======================================================
${detailLines}
======================================================="""

                currentBuild.description = "CD: ${app_name} | Env: ${environment_name.toUpperCase()} | ${artifact_name_disp}"

                input(
                    id      : 'DeployApproval',
                    message : "Deploy ${app_name} (${artifact_name_disp}) to ${environment_name.toUpperCase()} → s3://${s3_bucket}/?",
                    ok      : 'Approve & Deploy'
                )
            }

            stage('Deploy to S3') {
                withAWS(credentials: aws_creds_id, region: aws_region) {
                    sh """#!/bin/bash
                        set -e

                        WORK_DIR=\$(mktemp -d)
                        echo "Working directory: \$WORK_DIR"

                        # -------------------------------------------------------
                        # Step 1: Download artifact from CI S3 bucket
                        # -------------------------------------------------------
                        if [ -n "${artifact_name_disp}" ] && [ "${artifact_name_disp}" != "latest" ]; then
                            echo "Downloading artifact: ${artifact_name_disp}"
                            aws s3 cp "s3://${artifact_s3_bucket}/${artifact_s3_path}/${artifact_name_disp}" "\$WORK_DIR/${artifact_name_disp}" \\
                                --region ${artifact_s3_region}

                            echo "Extracting artifact..."
                            tar -xzf "\$WORK_DIR/${artifact_name_disp}" -C "\$WORK_DIR"

                            # Auto-detect build output folder
                            if   [ -d "\$WORK_DIR/dist" ];   then DEPLOY_SRC="\$WORK_DIR/dist"
                            elif [ -d "\$WORK_DIR/.next" ];  then DEPLOY_SRC="\$WORK_DIR/.next"
                            elif [ -d "\$WORK_DIR/build" ];  then DEPLOY_SRC="\$WORK_DIR/build"
                            else                                  DEPLOY_SRC="\$WORK_DIR"
                            fi
                            echo "Using build output: \$DEPLOY_SRC"
                        else
                            DEPLOY_SRC="${artifact_source}"
                            echo "No artifact specified — using local path: \$DEPLOY_SRC"
                        fi

                        # -------------------------------------------------------
                        # Step 2: Backup existing S3 content for rollback
                        # -------------------------------------------------------
                        echo "Backing up current S3 content to build #${currentBuild.number}..."
                        aws s3 mv s3://${s3_bucket}/ s3://${s3_bucket}/${currentBuild.number}/ \\
                            --recursive --region ${aws_region} 2>/dev/null || true

                        # -------------------------------------------------------
                        # Step 3: Deploy new build to target S3 bucket
                        # -------------------------------------------------------
                        echo "Deploying to s3://${s3_bucket}/..."
                        aws s3 cp "\$DEPLOY_SRC" s3://${s3_bucket}/ --recursive --region ${aws_region}
                        echo "Deployment complete: s3://${s3_bucket}/"

                        rm -rf "\$WORK_DIR"
                    """
                }
            }

            stage('CloudFront Cache Invalidation') {
                sh """#!/bin/bash
                    set -e
                    aws cloudfront create-invalidation \\
                        --distribution-id ${cloudfront_dist_id} \\
                        --paths "/*" \\
                        --region ${aws_region}
                    echo "CloudFront invalidation submitted for distribution: ${cloudfront_dist_id}"
                """
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

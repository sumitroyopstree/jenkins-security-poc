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

        def deployInfo = [
            'S3 Bucket'               : s3_bucket,
            'CloudFront Distribution' : cloudfront_dist_id,
            'AWS Region'              : aws_region,
            'Tag / Version'           : image_tag,
            'Environment'             : environment_name
        ]

        try {
            stage('Get Pipeline ID') {
                currentBuild.description = "Deploy Frontend [Build: ${image_tag}] → S3:${s3_bucket} (${environment_name})"
                echo "Pipeline ID: ${currentBuild.number}"
            }

            stage('Deploy to S3') {
                sh """#!/bin/bash
                    set -e
                    # Backup existing build to pipeline number subfolder for easy rollback
                    aws s3 mv s3://${s3_bucket}/ s3://${s3_bucket}/${currentBuild.number}/ \\
                        --recursive --region ${aws_region} 2>/dev/null || true

                    # Copy new build to S3 root
                    if [ -d "${artifact_source}" ]; then
                        aws s3 cp ${artifact_source} s3://${s3_bucket}/ --recursive --region ${aws_region}
                    else
                        echo "Artifact source ${artifact_source} not found in workspace, verifying S3..."
                    fi
                """
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

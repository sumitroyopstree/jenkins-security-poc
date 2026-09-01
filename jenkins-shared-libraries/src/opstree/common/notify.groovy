package opstree.common

import opstree.common.*

def notification_factory(Map step_params) {
    def logger = new opstree.common.logger()
    if (step_params.notification_enabled == 'true') {
        notification(step_params)
    }
  else {
        logger.logger('msg':'No valid option selected for Notification. Please mention correct values.', 'level':'WARN')
  }
}

def notification(Map step_params) {
    def logger = new opstree.common.logger()
    def parser = new parser()

    logger.logger('msg':'Performing Notification', 'level':'INFO')
    def build_status        = "${step_params.build_status}"
    def notification_channel = "${step_params.notification_channel}"

    // Support multiple comma-separated channels e.g. 'google_chat,gmail'
    def channels = notification_channel.split(',').collect { it.trim() }

    if (channels.contains('teams')) {
        def message = ''
        def color = ''
        def remarks = "Started by user ${env.BUILD_USER_ID}." // Customize as needed
        webhook_url_creds_id = "${step_params.webhook_url_creds_id}"

        if (build_status == 'SUCCESS') {
            message = "${env.JOB_NAME}: BUILD SUCCESS."
            color = '#008000'
        } else if (build_status == 'FAILURE') {
            message = "${env.JOB_NAME}: BUILD FAILED!!!"
            color = '#FF0000'
        } else if (build_status == 'UNSTABLE') {
            message = "${env.JOB_NAME}: BUILD UNSTABLE!!"
            color = '#FFFF00'
        } else {
            message = "${env.JOB_NAME}: BUILD RESULT UNKNOWN!!"
            color = '#FFA500'
        }

        withCredentials([string(credentialsId: webhook_url_creds_id, variable: 'WEBHOOK_URL')]) {
            office365ConnectorSend(
                webhookUrl: "${WEBHOOK_URL}",
                status: build_status,
                color: color,
                message: """<b>${message}</b><br><br>
                        <strong>Build No: #${env.BUILD_NUMBER}</strong><br><br>
                        <strong>Remarks</strong>: ${remarks}<br><br>"""
            )
        }
    }

    if (channels.contains('slack')) {
        // def build_status = "${step_params.build_status}"
        def slack_channel = "${step_params.slack_channel}"
        def triggeredBy = currentBuild.getBuildCauses().find { cause -> cause instanceof hudson.model.Cause.UserIdCause }?.userId
        ?: (env.BUILD_USER_ID ?: 'SCM Trigger/Unknown')
        def jobStartTime = new Date(currentBuild.startTimeInMillis).format('yyyy-MM-dd HH:mm:ss', TimeZone.getTimeZone('Asia/Kolkata'))
        def message
        // Construct the message based on the build status
        if (build_status == 'FAILURE') {
            message = 'Job Build Failed!!!!'
        } else if (build_status == 'SUCCESS') {
            message = 'Job Build Successfully Completed.'
        } else if (build_status == 'ABORTED') {
            message = 'Job Build was Aborted'
        } else {
            message = "Job Build status is ${build_status}"
        }
        // Determine the color based on build status
        def color
        switch (build_status) {
            case 'SUCCESS':
                color = 'good'
                break
            case 'FAILURE':
                color = 'danger'
                break
            case 'ABORTED':
                color = 'warning'
                break
            default:
                color = 'warning'
        }
        // Send the Slack notification using a variable for the channel
        slackSend(
            channel: slack_channel,
            color: color,
            message: """
            ${message}
            Find Status of Pipeline: ${build_status}
            Triggered By: ${triggeredBy}
            Job Name: ${env.JOB_NAME}
            Build Number: ${env.BUILD_NUMBER}
            Build URL: ${env.BUILD_URL}
            """
        )
    }

    if (channels.contains('gmail')) {
        def gmail_notification_recipients_email_ids = "${step_params.gmail_notification_recipients_email_ids}"
        def gmail_notification_from_email_id        = "${step_params.gmail_notification_from_email_id}"

        // -- Fix "Triggered By: null" ----------------------------------
        def triggeredByEmail = 'Unknown'
        try {
            def buildCauses = currentBuild.getBuildCauses()
            for (cause in buildCauses) {
                if (cause.userId) {
                    triggeredByEmail = cause.userId
                    break
                } else if (cause._class?.contains('SCMTrigger')) {
                    triggeredByEmail = 'SCM Auto-Trigger'
                    break
                } else if (cause._class?.contains('TimerTrigger')) {
                    triggeredByEmail = 'Timer / Cron'
                    break
                } else if (cause.shortDescription) {
                    triggeredByEmail = cause.shortDescription
                    break
                }
            }
        } catch (e) { triggeredByEmail = 'Unknown' }

        def jobStartTime = new Date(currentBuild.startTimeInMillis)
            .format('dd MMM yyyy, hh:mm:ss a', TimeZone.getTimeZone('Asia/Kolkata'))
        def duration = currentBuild.durationString?.replace(' and counting', '') ?: 'N/A'

        // -- Status colours --------------------------------------------
        def statusColor  = '#22c55e'
        def statusBg     = '#f0fdf4'
        def statusBorder = '#bbf7d0'
        def statusEmoji  = ''
        def statusLabel  = 'BUILD SUCCESS'
        def headerGrad   = 'linear-gradient(135deg,#166534 0%,#15803d 100%)'

        if (build_status == 'FAILURE' || build_status == 'Failure') {
            statusColor = '#ef4444'; statusBg = '#fef2f2'; statusBorder = '#fecaca'
            statusEmoji = ''; statusLabel = 'BUILD FAILED'
            headerGrad  = 'linear-gradient(135deg,#7f1d1d 0%,#b91c1c 100%)'
        } else if (build_status == 'UNSTABLE') {
            statusColor = '#f59e0b'; statusBg = '#fffbeb'; statusBorder = '#fde68a'
            statusEmoji = ''; statusLabel = 'BUILD UNSTABLE'
            headerGrad  = 'linear-gradient(135deg,#78350f 0%,#d97706 100%)'
        } else if (build_status == 'ABORTED') {
            statusColor = '#6b7280'; statusBg = '#f9fafb'; statusBorder = '#e5e7eb'
            statusEmoji = ''; statusLabel = 'BUILD ABORTED'
            headerGrad  = 'linear-gradient(135deg,#1f2937 0%,#4b5563 100%)'
        }

        // -- Jenkins published report URLs (for email body links) ---------
        def reportLinks = []
        def reportButtons = ''

        def reportMap = [
            'gitleaks/gitleaks_report.html'   : [ label: 'Gitleaks Security Report',     url: "${env.JENKINS_URL}job/${env.JOB_NAME}/Gitleaks_20Security_20Report/" ],
            'trivy/trivy_report.html'         : [ label: 'Trivy Image Scan Report',       url: "${env.JENKINS_URL}job/${env.JOB_NAME}/Trivy_20Image_20Scanning_20Report/" ],
            'owasp-reports/owasp_report.html' : [ label: 'OWASP Dependency Check Report', url: "${env.JENKINS_URL}job/${env.JOB_NAME}/OWASP_20Dependency_20Check_20Report/" ],
            'coverage/lcov-report/index.html' : [ label: 'Unit Test Coverage Report',    url: "${env.JENKINS_URL}job/${env.JOB_NAME}/Unit_20Test_20Coverage_20Report/" ],
            'coverage/index.html'             : [ label: 'Unit Test Coverage Report',    url: "${env.JENKINS_URL}job/${env.JOB_NAME}/Unit_20Test_20Coverage_20Report/" ],
            'target/site/jacoco/index.html'   : [ label: 'Unit Test Coverage Report',    url: "${env.JENKINS_URL}job/${env.JOB_NAME}/Unit_20Test_20Coverage_20Report/" ]
        ]
        reportMap.each { path, info ->
            if (fileExists(path)) {
                reportLinks << """<a href='${info.url}' style='display:block;text-align:center;background:#f8fafc;border:1px solid #e2e8f0;color:#334155;text-decoration:none;font-weight:600;font-size:13px;padding:12px 20px;border-radius:10px;margin-bottom:8px'>${info.label} &#x2197;</a>"""
            }
        }
        reportButtons = reportLinks ? reportLinks.join('') : "<p style='color:#94a3b8;font-size:13px'>No security reports were generated for this build.</p>"

        // -- Generate PDF attachments via wkhtmltopdf ---------------------
        // Step 1: Create self-contained HTML (CSS inlined)
        // Step 2: Convert to PDF using Docker wkhtmltopdf
        // PDFs are fully portable - no Jenkins access needed
        def attachPaths = []
        def attachDir   = 'email-reports'
        sh "mkdir -p ${attachDir}"

        def reportAttachMap = [
            'gitleaks/gitleaks_report.html'   : [ css: 'gitleaks/report.css',          name: 'Gitleaks_Security_Report' ],
            'trivy/trivy_report.html'         : [ css: 'trivy/report.css',              name: 'Trivy_Scan_Report' ],
            'owasp-reports/owasp_report.html' : [ css: 'owasp-reports/report.css',     name: 'OWASP_Dependency_Report' ],
            'coverage/lcov-report/index.html' : [ css: 'coverage/lcov-report/base.css', name: 'UnitTest_Coverage_Report' ],
            'coverage/index.html'             : [ css: '',                             name: 'UnitTest_Coverage_Report' ],
            'target/site/jacoco/index.html'   : [ css: '',                             name: 'UnitTest_Coverage_Report' ]
        ]

        reportAttachMap.each { htmlPath, info ->
            if (fileExists(htmlPath)) {
                def standaloneHtml = "${attachDir}/${info.name}.html"
                def pdfOut         = "${attachDir}/${info.name}.pdf"

                // Step 1 - Inline CSS so wkhtmltopdf renders correctly
                def html = readFile(htmlPath)
                def css  = fileExists(info.css) ? readFile(info.css) : ''
                if (css) {
                    html = html.replaceAll(/<link[^>]+stylesheet[^>]*>/, "<style>\n${css}\n</style>")
                }
                writeFile(file: standaloneHtml, text: html, encoding: 'UTF-8')

                // Step 2 - Convert HTML - PDF using Docker wkhtmltopdf
                try {
                    sh """
                        docker run --rm \\
                            -v \${WORKSPACE}/${attachDir}:/data \\
                            surnet/alpine-wkhtmltopdf:3.18.0-0.12.6-full \\
                            --enable-local-file-access \\
                            --page-size A4 \\
                            --orientation Landscape \\
                            --zoom 0.75 \\
                            --margin-top 10mm --margin-bottom 10mm \\
                            --margin-left 10mm --margin-right 10mm \\
                            --print-media-type \\
                            /data/${info.name}.html \\
                            /data/${info.name}.pdf
                    """
                    if (fileExists(pdfOut)) {
                        attachPaths << pdfOut
                        echo "[INFO] PDF generated: ${pdfOut}"
                    } else {
                        echo "[WARN] PDF not found after conversion, falling back to HTML: ${standaloneHtml}"
                        attachPaths << standaloneHtml
                    }
                } catch (convErr) {
                    echo "[WARN] PDF conversion failed for ${info.name}: ${convErr.getMessage()}. Attaching HTML instead."
                    attachPaths << standaloneHtml
                }
            }
        }
        def attachPattern = attachPaths.join(',')


        def projectTitle = step_params.project_title ?: 'HRC CI/CD Pipeline'
        def deployDetails = step_params.deployment_details ?: [:]
        def deployRows = ''
        if (deployDetails instanceof Map && deployDetails.size() > 0) {
            def rowCards = []
            deployDetails.each { k, v ->
                if (v) {
                    rowCards << """<td style='padding:6px;width:50%'>
                        <div style='background:#f8fafc;border:1px solid #e2e8f0;border-radius:10px;padding:10px 12px'>
                            <div style='font-size:10px;font-weight:700;text-transform:uppercase;letter-spacing:.08em;color:#94a3b8;margin-bottom:3px'>${k}</div>
                            <div style='font-size:13px;font-weight:700;color:#0f172a;word-break:break-all'>${v}</div>
                        </div>
                    </td>"""
                }
            }
            // Pair into 2 columns per row
            def tableRows = []
            for (int i = 0; i < rowCards.size(); i += 2) {
                def cell1 = rowCards[i]
                def cell2 = (i + 1 < rowCards.size()) ? rowCards[i+1] : "<td style='padding:6px;width:50%'></td>"
                tableRows << "<tr>${cell1}${cell2}</tr>"
            }
            deployRows = """
            <div style='padding:0 32px 16px'>
              <div style='font-size:11px;font-weight:700;text-transform:uppercase;letter-spacing:.1em;color:#64748b;margin-bottom:8px'>&#128640; Deployment Details</div>
              <table width='100%' cellpadding='0' cellspacing='0' style='border-collapse:collapse'>
                ${tableRows.join('')}
              </table>
            </div>
            """
        }

        // -- Rich HTML Body --------------------------------------------
        def htmlBody = """<!DOCTYPE html>
<html lang='en'>
<head>
<meta charset='UTF-8'/>
<meta name='viewport' content='width=device-width,initial-scale=1'/>
<title>Jenkins Build Notification</title>
</head>
<body style='margin:0;padding:32px 16px;background:#f1f5f9;font-family:Arial,sans-serif;color:#334155'>
<div style='max-width:620px;margin:0 auto'>

  <!-- Main Card -->
  <div style='background:#fff;border-radius:20px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,.07);margin-bottom:20px'>

    <!-- Header -->
    <div style='background:${headerGrad};padding:36px 32px;text-align:center;color:#fff'>
      <div style='font-size:12px;font-weight:700;letter-spacing:.12em;text-transform:uppercase;opacity:.75;margin-bottom:10px'>${projectTitle}</div>
      <h1 style='font-size:24px;font-weight:800;letter-spacing:-.5px;margin-bottom:6px'>${env.JOB_NAME}</h1>
      <div style='font-size:14px;opacity:.8'>Build #${env.BUILD_NUMBER} &nbsp;&middot;&nbsp; ${jobStartTime} IST</div>
    </div>

    <!-- Status Badge -->
    <div style='padding:28px 32px 8px;text-align:center'>
      <span style='display:inline-flex;align-items:center;gap:10px;padding:12px 28px;border-radius:999px;font-size:15px;font-weight:700;border:2px solid ${statusBorder};background:${statusBg};color:${statusColor}'>
        <span style='width:10px;height:10px;border-radius:50%;background:${statusColor};display:inline-block'></span>
        ${statusEmoji}&nbsp; ${statusLabel}
      </span>
    </div>

    <!-- Info Grid -->
    <div style='padding:20px 32px'>
      <table width='100%' cellpadding='0' cellspacing='0' style='border-collapse:collapse'>
      <tr>
        <td style='padding:8px'>
          <div style='background:#f8fafc;border:1px solid #e2e8f0;border-radius:12px;padding:14px'>
            <div style='font-size:10px;font-weight:700;text-transform:uppercase;letter-spacing:.08em;color:#94a3b8;margin-bottom:5px'>Job Name</div>
            <div style='font-size:13px;font-weight:700;color:#0f172a'>${env.JOB_NAME}</div>
          </div>
        </td>
        <td style='padding:8px'>
          <div style='background:#f8fafc;border:1px solid #e2e8f0;border-radius:12px;padding:14px'>
            <div style='font-size:10px;font-weight:700;text-transform:uppercase;letter-spacing:.08em;color:#94a3b8;margin-bottom:5px'>Build Number</div>
            <div style='font-size:13px;font-weight:700;color:#0f172a'>#${env.BUILD_NUMBER}</div>
          </div>
        </td>
      </tr>
      <tr>
        <td style='padding:8px'>
          <div style='background:#f8fafc;border:1px solid #e2e8f0;border-radius:12px;padding:14px'>
            <div style='font-size:10px;font-weight:700;text-transform:uppercase;letter-spacing:.08em;color:#94a3b8;margin-bottom:5px'>Triggered By</div>
            <div style='font-size:13px;font-weight:700;color:#0f172a'>${triggeredByEmail}</div>
          </div>
        </td>
        <td style='padding:8px'>
          <div style='background:#f8fafc;border:1px solid #e2e8f0;border-radius:12px;padding:14px'>
            <div style='font-size:10px;font-weight:700;text-transform:uppercase;letter-spacing:.08em;color:#94a3b8;margin-bottom:5px'>Duration</div>
            <div style='font-size:13px;font-weight:700;color:#0f172a'>${duration}</div>
          </div>
        </td>
      </tr>
      <tr>
        <td style='padding:8px'>
          <div style='background:#f8fafc;border:1px solid #e2e8f0;border-radius:12px;padding:14px'>
            <div style='font-size:10px;font-weight:700;text-transform:uppercase;letter-spacing:.08em;color:#94a3b8;margin-bottom:5px'>Start Time</div>
            <div style='font-size:13px;font-weight:700;color:#0f172a'>${jobStartTime} IST</div>
          </div>
        </td>
        <td style='padding:8px'>
          <div style='background:${statusBg};border:1px solid ${statusBorder};border-radius:12px;padding:14px'>
            <div style='font-size:10px;font-weight:700;text-transform:uppercase;letter-spacing:.08em;color:#94a3b8;margin-bottom:5px'>Status</div>
            <div style='font-size:13px;font-weight:700;color:${statusColor}'>${statusLabel}</div>
          </div>
        </td>
      </tr>
      </table>
    </div>

    <!-- Optional Deployment Details Section -->
    ${deployRows}

    <!-- View Build Logs Button -->
    <div style='padding:4px 32px 16px'>
      <a href='${env.BUILD_URL}' style='display:block;text-align:center;background:${headerGrad};color:#fff;text-decoration:none;font-weight:700;font-size:14px;padding:14px 24px;border-radius:12px'>&#128279; View Full Build Logs</a>
    </div>

    <!-- Divider -->
    <div style='height:1px;background:#f1f5f9;margin:0 32px'></div>

    <!-- Security Report Links -->
    <div style='padding:20px 32px 24px'>
      <div style='font-size:11px;font-weight:700;text-transform:uppercase;letter-spacing:.1em;color:#64748b;margin-bottom:12px'>&#128206; Security Reports (Click to View)</div>
      ${reportButtons}
    </div>

    <!-- Divider -->
    <div style='height:1px;background:#f1f5f9;margin:0 32px'></div>

    <!-- Footer -->
    <div style='text-align:center;padding:18px 32px;font-size:12px;color:#94a3b8'>
      This notification was sent automatically by <strong style='color:#475569'>${projectTitle}</strong>.<br/>Do not reply to this email.
    </div>

  </div>
</div>
</body>
</html>"""

        emailext(
            to:                 gmail_notification_recipients_email_ids,
            from:               gmail_notification_from_email_id,
            subject:            "${statusEmoji} [Jenkins] ${env.JOB_NAME} #${env.BUILD_NUMBER} \u2014 ${statusLabel}",
            body:               htmlBody,
            mimeType:           'text/html',
            attachmentsPattern: attachPattern
        )
    }


    if (channels.contains('google_chat')) {
        // -- Fix "Triggered By: null" & JSON Safety ------------------
        def triggeredBy = 'Unknown'
        try {
            def buildCauses = currentBuild.getBuildCauses()
            for (cause in buildCauses) {
                if (cause.userId) { triggeredBy = cause.userId; break }
                else if (cause._class?.contains('SCMTrigger')) { triggeredBy = 'SCM Auto-Trigger'; break }
                else if (cause._class?.contains('TimerTrigger')) { triggeredBy = 'Timer / Cron'; break }
                else if (cause.shortDescription) { triggeredBy = cause.shortDescription; break }
            }
        } catch (e) { triggeredBy = 'Unknown' }

        // Sanitize string to prevent breaking JSON payload structure
        triggeredBy = triggeredBy.replace('"', '\'').replace('\n', ' ').trim()

        def pipelineType = env.JOB_NAME.startsWith('CD/') ? 'CD' : (env.JOB_NAME.startsWith('Liquibase/') ? 'DB Migration' : 'CI')

        def jobStartTime = new Date(currentBuild.startTimeInMillis).format('yyyy-MM-dd HH:mm:ss', TimeZone.getTimeZone('Asia/Kolkata'))
        def webhook_url_creds_id = "${step_params.webhook_url_creds_id}"

        def statusEmoji = ''
        def statusText  = ''
        def headerColor = ''

        if (build_status == 'SUCCESS' || build_status == 'Success') {
            statusEmoji = ''
            statusText  = 'BUILD SUCCESS'
            headerColor = '#008000'
        } else if (build_status == 'FAILURE' || build_status == 'Failure') {
            statusEmoji = ''
            statusText  = 'BUILD FAILED'
            headerColor = '#FF0000'
        } else if (build_status == 'ABORTED') {
            statusEmoji = ''
            statusText  = 'BUILD ABORTED'
            headerColor = '#FFA500'
        } else {
            statusEmoji = ''
            statusText  = "BUILD ${build_status.toUpperCase()}"
            headerColor = '#FFFF00'
        }

        // -- Jenkins report URLs for Google Chat buttons ---------------
        def gchatButtons = [
            [ text: ' View Build Logs', url: "${env.BUILD_URL}" ]
        ]
        def reportCheckMap = [
            'gitleaks/gitleaks_report.html'   : [ text: 'Gitleaks Report',  url: "${env.JENKINS_URL}job/${env.JOB_NAME}/Gitleaks_20Security_20Report/" ],
            'trivy/trivy_report.html'         : [ text: 'Trivy Scan',       url: "${env.JENKINS_URL}job/${env.JOB_NAME}/Trivy_20Image_20Scanning_20Report/" ],
            'owasp-reports/owasp_report.html' : [ text: 'OWASP Report',     url: "${env.JENKINS_URL}job/${env.JOB_NAME}/OWASP_20Dependency_20Check_20Report/" ],
            'coverage/lcov-report/index.html' : [ text: 'Coverage Report',  url: "${env.JENKINS_URL}job/${env.JOB_NAME}/Unit_20Test_20Coverage_20Report/" ],
            'target/site/jacoco/index.html'   : [ text: 'Coverage Report',  url: "${env.JENKINS_URL}job/${env.JOB_NAME}/Unit_20Test_20Coverage_20Report/" ]
        ]
        reportCheckMap.each { path, info ->
            if (fileExists(path)) { gchatButtons << info }
        }

        def buttonWidgets = gchatButtons.collect { btn ->
            "{ \"textButton\": { \"text\": \"${btn.text}\", \"onClick\": { \"openLink\": { \"url\": \"${btn.url}\" } } } }"
        }.join(', ')

        def payload = """{
            "cards": [{
                "header": {
                    "title": "${statusEmoji} HRC ${pipelineType} Notification",
                    "subtitle": "${env.JOB_NAME}",
                    "imageUrl": "https://www.jenkins.io/images/logos/jenkins/jenkins.png",
                    "imageStyle": "AVATAR"
                },
                "sections": [
                    {
                        "widgets": [
                            { "keyValue": { "topLabel": "Status",       "content": "${statusText}" } },
                            { "keyValue": { "topLabel": "Job Name",     "content": "${env.JOB_NAME}" } },
                            { "keyValue": { "topLabel": "Build Number", "content": "#${env.BUILD_NUMBER}" } },
                            { "keyValue": { "topLabel": "Triggered By", "content": "${triggeredBy}" } },
                            { "keyValue": { "topLabel": "Start Time",   "content": "${jobStartTime} IST" } }
                        ]
                    },
                    {
                        "header": "Security Reports",
                        "widgets": [
                            { "buttons": [ ${buttonWidgets} ] }
                        ]
                    }
                ]
            }]
        }"""

        withCredentials([string(credentialsId: webhook_url_creds_id, variable: 'GCHAT_WEBHOOK_URL')]) {
            sh """
                curl -s -X POST -H 'Content-Type: application/json' \
                    -d '${payload}' \
                    "\${GCHAT_WEBHOOK_URL}"
            """
        }
    }

    // if (step_params.notification_channel == 'outlook') {
    //     def message = ''
    //     def color = ''
    //     def remarks = "Started by user ${env.BUILD_USER_ID}." // Customize as needed
    //     webhook_url_creds_id = "${step_params.webhook_url_creds_id}"

    //     if (build_status == 'SUCCESS') {
    //         message = "${env.JOB_NAME}: BUILD SUCCESS."
    //         color = '#008000'
    //     } else if (build_status == 'FAILURE') {
    //         message = "${env.JOB_NAME}: BUILD FAILED!!!"
    //         color = '#FF0000'
    //     } else if (build_status == 'UNSTABLE') {
    //         message = "${env.JOB_NAME}: BUILD UNSTABLE!!"
    //         color = '#FFFF00'
    //     } else {
    //         message = "${env.JOB_NAME}: BUILD RESULT UNKNOWN!!"
    //         color = '#FFA500'
    //     }

//     withCredentials([string(credentialsId: webhook_url_creds_id, variable: 'WEBHOOK_URL')]) {
//         office365ConnectorSend(
//             webhookUrl: "${WEBHOOK_URL}",
//             status: build_status,
//             color: color,
//             message: """<b>${message}</b><br><br>
//                     <strong>Build No: #${env.BUILD_NUMBER}</strong><br><br>
//                     <strong>Remarks</strong>: ${remarks}<br><br>"""
//         )
//     }
// }
}

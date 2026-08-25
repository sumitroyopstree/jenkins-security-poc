// package opstree.common

// import opstree.common.*

// def image_scanning_factory(Map step_params) {
//     def logger = new logger()
//     // Accept both boolean true and string 'true'
//     if ("${step_params.image_scanning_check}" == 'true') {
//         trivy(step_params)
//     } else {
//         logger.logger('msg':'No valid option selected for image scanning. Please mention correct values.', 'level':'WARN')
//     }
// }

// def trivy(Map step_params) {
//     def logger                 = new logger()
//     def image_scanning_reports = new reports_management()

//     logger.logger('msg':'Performing Image Scanning', 'level':'INFO')

//     def image_scanning_report_publish = "${step_params.image_scanning_report_publish}"
//     def fail_job_if_scan_failed       = "${step_params.fail_job_if_scan_failed ?: 'false'}"
//     def trivyCacheDir                 = "${JENKINS_HOME}/trivy-cache"

//     dir("${WORKSPACE}") {
//         sh "mkdir -p ${WORKSPACE}/trivy ${trivyCacheDir}"
//         sh "sudo chmod -R 777 ${WORKSPACE}/trivy ${trivyCacheDir}"

//         // -- Java DB strategy -------------------------------------------------
//         // trivy-java-db is 892 MiB downloaded from ghcr.io which fails on
//         // this network with OCI errors at post-download initialization.
//         // Fix: ALWAYS skip Java DB - Alpine OS CVEs are detected via the
//         // main vuln DB which downloads reliably. Java JAR CVEs can be
//         // re-enabled later once the ghcr.io connectivity is stable.
//         def skipJavaDbFlag = '--skip-java-db-update'
//         logger.logger('msg':'Trivy: --skip-java-db-update applied (avoids 892 MiB OCI download failure). Alpine OS CVEs still detected.', 'level':'INFO')

//         try {
//             def imageExists = sh(
//                 script: "docker images -q ${step_params.image_name}:${step_params.image_tag}",
//                 returnStdout: true
//             ).trim()

//             if (!imageExists) {
//                 logger.logger('msg':"Image ${step_params.image_name}:${step_params.image_tag} not found locally.", 'level':'ERROR')
//                 return
//             }

//             logger.logger('msg':'Image found, proceeding with Trivy scan', 'level':'INFO')

//             // -- Write render templates via libraryResource --------------------
//             // libraryResource() is the only reliable way to load shared library
//             // files onto the agent workspace - cp fails because @libs/ path is
//             // only present on the Jenkins master, not the build agent workspace.
//             dir("${WORKSPACE}/trivy") {
//                 writeFile file: 'report.html', text: libraryResource('trivy/render/report.html')
//                 writeFile file: 'report.css',  text: libraryResource('trivy/render/report.css')
//                 writeFile file: 'inject.sh',   text: libraryResource('trivy/render/inject.sh')
//                 sh 'chmod +x inject.sh'
//             }

//             // -- Step 1: Run Trivy scan - JSON output -------------------------
//             // Separated from inject.sh so a scan failure is caught cleanly
//             // by the outer try-catch without also swallowing render errors.
//             sh """
//                 docker run --rm \\
//                     -v /var/run/docker.sock:/var/run/docker.sock \\
//                     -v ${WORKSPACE}/trivy:/output \\
//                     -v ${trivyCacheDir}:/root/.cache/trivy \\
//                     aquasec/trivy:0.56.0 image \\
//                         --scanners vuln \\
//                         --severity "${step_params.scan_severity}" \\
//                         ${skipJavaDbFlag} \\
//                         --format json \\
//                         --output /output/trivy_report.json \\
//                         ${step_params.image_name}:${step_params.image_tag}
//             """
//             logger.logger('msg':'Trivy scan completed successfully', 'level':'INFO')

//             // -- Step 2: Generate human-readable HTML report -------------------
//             try {
//                 dir("${WORKSPACE}/trivy") {
//                     sh './inject.sh'
//                 }
//                 logger.logger('msg':'Trivy HTML report generated: trivy_report.html', 'level':'INFO')
//             } catch (Exception renderEx) {
//                 logger.logger('msg':"Trivy HTML render failed (raw JSON will be published instead): ${renderEx.message}", 'level':'WARN')
//             }

//             // -- Step 3: Publish report ----------------------------------------
//             if (image_scanning_report_publish == 'true') {
//                 def htmlExists = fileExists("${WORKSPACE}/trivy/trivy_report.html")
//                 def reportFile = htmlExists ? 'trivy_report.html' : 'trivy_report.json'
//                 logger.logger('msg':"Publishing Trivy report: ${reportFile}", 'level':'INFO')
//                 image_scanning_reports.publish(
//                     'report_dir' : "${WORKSPACE}/trivy",
//                     'report_file': reportFile,
//                     'report_name': 'Trivy Image Scanning Report'
//                 )
//             } else {
//                 logger.logger('msg':'Trivy Image Scanning Report Publishing Skipped', 'level':'INFO')
//             }

//         } catch (Exception e) {
//             logger.logger('msg':"Trivy scan failed: ${e.message}", 'level':'ERROR')
//             if (fail_job_if_scan_failed == 'true') {
//                 error "Trivy scan failed: ${e.message}"
//             } else {
//                 logger.logger('msg':'Trivy scan failed - continuing as fail_job_if_scan_failed=false', 'level':'WARN')
//             }
//         }
//     }
// }


package opstree.common

import opstree.common.*

def image_scanning_factory(Map step_params) {
    def logger = new logger()
    if ("${step_params.image_scanning_check}" == 'true') {
        trivy(step_params)
    } else {
        logger.logger('msg':'No valid option selected for image scanning. Please mention correct values.', 'level':'WARN')
    }
}

def trivy(Map step_params) {
    def logger                 = new logger()
    def image_scanning_reports = new reports_management()

    logger.logger('msg':'Performing Image Scanning', 'level':'INFO')

    def image_scanning_report_publish = "${step_params.image_scanning_report_publish}"
    def fail_job_if_scan_failed       = "${step_params.fail_job_if_scan_failed ?: 'false'}"
    def trivyCacheDir                 = "/tmp/trivy-cache"

    dir("${WORKSPACE}") {
        sh "mkdir -p ${WORKSPACE}/trivy ${trivyCacheDir}"
        sh "chmod -R 777 ${WORKSPACE}/trivy ${trivyCacheDir} 2>/dev/null || true"

        try {
            def imageExists = sh(
                script: "docker images -q ${step_params.image_name}:${step_params.image_tag}",
                returnStdout: true
            ).trim()

            if (!imageExists) {
                logger.logger('msg':"Image ${step_params.image_name}:${step_params.image_tag} not found locally.", 'level':'ERROR')
                return
            }

            logger.logger('msg':'Image found, proceeding with Trivy scan', 'level':'INFO')

            dir("${WORKSPACE}/trivy") {
                writeFile file: 'report.html', text: libraryResource('trivy/render/report.html')
                writeFile file: 'report.css',  text: libraryResource('trivy/render/report.css')
                writeFile file: 'inject.sh',   text: libraryResource('trivy/render/inject.sh')
                sh 'chmod +x inject.sh'
            }

            // Run Trivy as root to access docker.sock, using /tmp/cache for DB downloads
            sh """
                docker run --rm \\
                    -v /var/run/docker.sock:/var/run/docker.sock \\
                    -v ${WORKSPACE}/trivy:/output \\
                    -v ${trivyCacheDir}:/root/.cache/trivy \\
                    aquasec/trivy:0.56.0 image \\
                        --scanners vuln \\
                        --pkg-types os \\
                        --severity "${step_params.scan_severity ?: 'HIGH,CRITICAL'}" \\
                        --format json \\
                        --output /output/trivy_report.json \\
                        ${step_params.image_name}:${step_params.image_tag}
            """
            
            // Fix report permissions immediately so inject.sh and Jenkins can read it
            sh "sudo chown -R \$(id -u):\$(id -g) ${WORKSPACE}/trivy"
            logger.logger('msg':'Trivy scan completed successfully', 'level':'INFO')

            try {
                dir("${WORKSPACE}/trivy") {
                    sh './inject.sh'
                }
                logger.logger('msg':'Trivy HTML report generated: trivy_report.html', 'level':'INFO')
            } catch (Exception renderEx) {
                logger.logger('msg':"Trivy HTML render failed (raw JSON will be published instead): ${renderEx.message}", 'level':'WARN')
            }

            if (image_scanning_report_publish == 'true') {
                def htmlExists = fileExists("${WORKSPACE}/trivy/trivy_report.html")
                def reportFile = htmlExists ? 'trivy_report.html' : 'trivy_report.json'
                logger.logger('msg':"Publishing Trivy report: ${reportFile}", 'level':'INFO')
                image_scanning_reports.publish(
                    'report_dir' : "${WORKSPACE}/trivy",
                    'report_file': reportFile,
                    'report_name': 'Trivy Image Scanning Report'
                )
            } else {
                logger.logger('msg':'Trivy Image Scanning Report Publishing Skipped', 'level':'INFO')
            }

        } catch (Exception e) {
            logger.logger('msg':"Trivy scan failed: ${e.message}", 'level':'ERROR')
            if (fail_job_if_scan_failed == 'true') {
                error "Trivy scan failed: ${e.message}"
            } else {
                logger.logger('msg':'Trivy scan failed - continuing as fail_job_if_scan_failed=false', 'level':'WARN')
            }
        } finally {
            sh "sudo chown -R \$(id -u):\$(id -g) ${WORKSPACE}/trivy 2>/dev/null || true"
        }
    }
}
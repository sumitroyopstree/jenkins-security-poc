package opstree.common

import opstree.common.*

def static_code_analysis_factory(Map step_params) {
    logger = new logger()
    if (step_params.static_code_analysis_check?.toBoolean() || step_params.static_code_analysis_check == 'true' || step_params.static_code_analysis_check == true) {
        sonar(step_params)
    }
    else {
        logger.logger('msg':'No valid option selected for static code analysis. Please mention correct values.', 'level':'WARN')
    }
}

def sonar(Map step_params) {
    stage('Static Code Analysis') {
        logger = new logger()
        parser = new parser()

        logger.logger('msg':'Performing Static Code Analysis ', 'level':'INFO')

        repo_url = "${step_params.repo_url}"
        repo_url_type = "${step_params.repo_url_type}"

        codebase_to_scan_directory = "${step_params.codebase_to_scan_directory}"
        static_code_analysis_check = "${step_params.static_code_analysis_check}"
        path_to_sonar_properties = "${step_params.path_to_sonar_properties}"
        fail_job_if_analysis_returned_exception = "${step_params.fail_job_if_analysis_returned_exception}"
        jenkins_sonarqube_token_creds_id = "${step_params.jenkins_sonarqube_token_creds_id}"
        app_stack = "${step_params.app_stack}"
        unit_testing_check = "${step_params.unit_testing_check}"
        withmaven_globaltool_jdk = "${step_params.withmaven_globaltool_jdk}"
        withmaven_globaltool_maven = "${step_params.withmaven_globaltool_maven}"
        source_code_path = "${step_params.source_code_path}"
        sonar_project_key = "${step_params.sonar_project_key}"
        sonar_project_name = "${step_params.sonar_project_name}"
        sonar_sources = "${step_params.sonar_sources}"
        sonar_host_url = "${step_params.sonar_host_url}"
        sonar_extra_args = "${step_params.sonar_extra_args}"

        repo_dir = parser.fetch_git_repo_name('repo_url':"${repo_url}")
        repo_dir = repo_dir + source_code_path

        def sonar_cmd = ""
        def actual_host = env.SONAR_HOST_URL ?: ((sonar_host_url != 'null' && sonar_host_url != '' && sonar_host_url != 'http://10.2.1.209:9000') ? sonar_host_url : 'http://10.2.10.250:9000')

        if (path_to_sonar_properties != 'null' && path_to_sonar_properties != '') {
            sonar_cmd = "-Dproject.settings=${path_to_sonar_properties} -Dsonar.host.url=${actual_host}"
        } else if (sonar_project_key != 'null' && sonar_project_key != '' && sonar_project_name != 'null' && sonar_project_name != '') {
            def actual_sources = (sonar_sources != 'null' && sonar_sources != '') ? sonar_sources : '.'
            def actual_extra  = (sonar_extra_args != 'null' && sonar_extra_args != '') ? sonar_extra_args : ''
            sonar_cmd = "-Dsonar.projectKey=${sonar_project_key} " +
                        "-Dsonar.projectName=${sonar_project_name} " +
                        "-Dsonar.sources=${actual_sources} " +
                        "-Dsonar.host.url=${actual_host} " +
                        "-Dsonar.exclusions=**/node_modules/**,**/dist/**,**/.next/**,**/build/**,**/coverage/** " +
                        "-Dsonar.javascript.lcov.reportPaths=lcov.info,coverage/lcov.info " +
                        "-Dsonar.typescript.lcov.reportPaths=lcov.info,coverage/lcov.info " +
                        "-Dsonar.scm.exclusions.disabled=true " +
                        "${actual_extra}"
        } else {
            sonar_cmd = "-Dproject.settings=sonar-project.properties -Dsonar.host.url=${actual_host}"
        }

        // Build the docker command - sonar_cmd uses Groovy vars (safe, not secrets)
        // SONAR_TOKEN and SONARQUBE_SCANNER_PARAMS are passed into Docker container
        def docker_cmd = "docker run --rm --user root" +
                         " -v \$WORKSPACE/${repo_dir}:/usr/src" +
                         " -e SONAR_TOKEN=\$SONAR_TOKEN" +
                         " -e SONARQUBE_SCANNER_PARAMS=\"\$SONARQUBE_SCANNER_PARAMS\"" +
                         " -w /usr/src" +
                         " sonarsource/sonar-scanner-cli ${sonar_cmd}" +
                         " -Dsonar.working.directory=/usr/src/.scannerwork"

        if (fail_job_if_analysis_returned_exception == 'false') {
                try {
                        withCredentials([string(credentialsId: jenkins_sonarqube_token_creds_id , variable: 'SONAR_TOKEN')]) {
                            withSonarQubeEnv('SonarQube') {
                                sh "${docker_cmd}"
                                sh "sudo chown -R \$(id -u):\$(id -g) \$WORKSPACE/${repo_dir}/.scannerwork 2>/dev/null || true"
                                sh "mkdir -p \$WORKSPACE/.scannerwork && cp -f \$WORKSPACE/${repo_dir}/.scannerwork/report-task.txt \$WORKSPACE/.scannerwork/report-task.txt 2>/dev/null || true"
                                sh "cp -f \$WORKSPACE/${repo_dir}/.scannerwork/report-task.txt \$WORKSPACE/report-task.txt 2>/dev/null || true"
                                logger.logger('msg':'Static Code Analysis Scanning Complete', 'level':'INFO')
                                sleep(5)
                                timeout(time: 2, unit: 'MINUTES') {
                                    def qg = waitForQualityGate()
                                    echo "Finished waiting. Quality Gate Status: ${qg.status}"
                                    if (qg.status != 'OK') {
                                        logger.logger('msg': "Pipeline aborted due to quality gate failure: ${qg.status}. But Ignoring as per User input", 'level':'WARN')
                                    }
                                }
                            }
                        }
                }
                catch (Exception e) {
                    logger.logger('msg':'Static Code Analysis Scanning Failed!! Ignoring as per User inputs', 'level':'WARN')
                }
        }
        else {
                try {
                        withCredentials([string(credentialsId: jenkins_sonarqube_token_creds_id , variable: 'SONAR_TOKEN')]) {
                            withSonarQubeEnv('SonarQube') {
                                sh "${docker_cmd}"
                                sh "sudo chown -R \$(id -u):\$(id -g) \$WORKSPACE/${repo_dir}/.scannerwork 2>/dev/null || true"
                                sh "mkdir -p \$WORKSPACE/.scannerwork && cp -f \$WORKSPACE/${repo_dir}/.scannerwork/report-task.txt \$WORKSPACE/.scannerwork/report-task.txt 2>/dev/null || true"
                                sh "cp -f \$WORKSPACE/${repo_dir}/.scannerwork/report-task.txt \$WORKSPACE/report-task.txt 2>/dev/null || true"
                                logger.logger('msg':'Static Code Analysis Scanning Complete', 'level':'INFO')
                                sleep(5)
                                timeout(time: 2, unit: 'MINUTES') {
                                    def qg = waitForQualityGate()
                                    echo "Finished waiting. Quality Gate Status: ${qg.status}"
                                    if (qg.status != 'OK') {
                                        logger.logger('msg': "Pipeline aborted due to quality gate failure: ${qg.status}.", 'level':'ERROR')
                                        error("Quality gate failure: ${qg.status}")
                                    }
                                }
                            }
                        }
                }
                catch (Exception e) {
                    logger.logger('msg':"Static Code Analysis Scanning Failed. Error Details: ${e}", 'level':'ERROR')
                    error()
                }
        }
    }
}

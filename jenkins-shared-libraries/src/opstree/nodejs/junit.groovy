package opstree.nodejs

import opstree.common.*

def unit_testing_factory(Map step_params) {
    logger = new logger()
    if (step_params.unit_testing_check == 'true' || step_params.unit_testing_check == true) {
        unit_test(step_params)
    }
  else {
        logger.logger('msg':'No valid option selected for Unit Testing. Please mention correct values.', 'level':'WARN')
  }
}

def unit_test(Map step_params) {
    logger = new logger()
    parser = new parser()
    reports_manager = new reports_management()

    logger.logger('msg':'Performing Unit Tests for Node.js', 'level':'INFO')

    repo_url = "${step_params.repo_url}"
    fail_job_if_unit_issue_detected = "${step_params.fail_job_if_unit_issue_detected}"
    source_code_path = "${step_params.source_code_path}"
    node_version = step_params.node_version ?: "18"
    def package_manager = step_params.package_manager ?: "yarn"
    def raw_secret_id = step_params.build_secret_creds_id?.toString()?.trim()
    def build_secret_creds_id = (raw_secret_id && raw_secret_id != 'null') ? raw_secret_id : ''
    def raw_secret_var = step_params.build_secret_env_var?.toString()?.trim()
    def build_secret_env_var  = (raw_secret_var && raw_secret_var != 'null') ? raw_secret_var : 'BUILD_SECRET'

    unit_test_reports_path = "${step_params.unit_test_reports_path ?: ''}"
    repo_dir = parser.fetch_git_repo_name('repo_url':"${repo_url}")
    def project_path = "${WORKSPACE}/${repo_dir}${source_code_path ?: ''}"
    dir(project_path) {
        try {
            def install_cmd = (package_manager == 'yarn') ? "(yarn install || npm install)" : "(npm install)"
            def test_runner = (package_manager == 'yarn') ? "yarn test" : "npm test"
            def test_cmd = "export PUPPETEER_SKIP_CHROMIUM_DOWNLOAD=true HEADLESS=true && ${install_cmd} && (npm install --no-save jest-junit 2>/dev/null || yarn add --dev jest-junit 2>/dev/null || true) && (JEST_JUNIT_OUTPUT_DIR=. JEST_JUNIT_OUTPUT_NAME=junit.xml ${test_runner} -- --passWithNoTests --reporters=default --reporters=jest-junit --coverage --coverageReporters=text --coverageReporters=lcov --coverageReporters=html || ${test_runner} -- --passWithNoTests --coverage --coverageReporters=text --coverageReporters=lcov --coverageReporters=html || ${test_runner} || true)"

            if (build_secret_creds_id) {
                // Private Azure DevOps npm feed - fetch short-lived token and write .npmrc
                withCredentials([string(credentialsId: build_secret_creds_id, variable: 'THE_SECRET')]) {
                    try {
                        sh """
                            set -e
                            FEED=pkgs.dev.azure.com/msface/SDK/_packaging/AzureAIVision/npm
                            RESPONSE=\$(curl --silent --fail --location \
                                'https://montrafacedev.cognitiveservices.azure.com/face/v1.3-preview.1/settings/getClientAssetsAccessToken' \
                                --header "Ocp-Apim-Subscription-Key: \$THE_SECRET")
                            B64_TOKEN=\$(echo "\$RESPONSE" | jq -r '.base64AccessToken')
                            if [ -z "\$B64_TOKEN" ] || [ "\$B64_TOKEN" = "null" ]; then echo "Token fetch failed"; exit 1; fi
                            {
                              echo "legacy-peer-deps=true"
                              echo "@azure-ai-vision-face:registry=https://\${FEED}/registry/"
                              echo "@azure:registry=https://\${FEED}/registry/"
                              echo "always-auth=true"
                              echo "//\${FEED}/registry/:username=msface"
                              echo "//\${FEED}/registry/:_password=\${B64_TOKEN}"
                              echo "//\${FEED}/registry/:email=not-used@example.com"
                              echo "//\${FEED}/:username=msface"
                              echo "//\${FEED}/:_password=\${B64_TOKEN}"
                              echo "//\${FEED}/:email=not-used@example.com"
                            } > ${project_path}/.npmrc.unittest
                            echo "[INFO] .npmrc.unittest written with Azure feed credentials"
                        """
                        sh """docker run --rm \
                            -v ${project_path}:/usr/src \
                            -v ${project_path}/.npmrc.unittest:/usr/src/.npmrc:ro \
                            -w /usr/src node:${node_version} sh -c '${test_cmd}'"""
                    } finally {
                        sh "rm -f ${project_path}/.npmrc.unittest"
                        logger.logger('msg':'Cleaned up .npmrc.unittest', 'level':'INFO')
                    }
                }
            } else {
                // No private feed credentials - plain npm install & test with coverage
                sh """docker run --rm \
                    -v ${project_path}:/usr/src \
                    -w /usr/src node:${node_version} sh -c '${test_cmd}'"""
            }

            // Normalize lcov.info paths for SonarQube.
            // SonarScanner runs in Docker with -v project:/usr/src -w /usr/src
            // So lcov.info must have RELATIVE paths like SF:src/... (not SF:/usr/src/src/...)
            // We only fix paths generated inside old /app containers - convert /app/src/ - src/
            sh """
                if [ -d coverage ]; then
                    sudo chown -R \$(id -u):\$(id -g) coverage 2>/dev/null || true
                fi
                if [ -f coverage/lcov.info ]; then
                    # Fix absolute /app/src/ paths (some Docker base images use /app as workdir)
                    sed -i 's|^SF:/app/|SF:|g' coverage/lcov.info || true
                    # Fix absolute /usr/src/ paths (node Docker container uses -w /usr/src)
                    sed -i 's|^SF:/usr/src/|SF:|g' coverage/lcov.info || true
                    # Copy to root so SonarQube lcov.info path also works (belt + suspenders)
                    cp coverage/lcov.info lcov.info || true
                    echo "[INFO] First 10 SF: lines in lcov.info after path fix:"
                    grep '^SF:' coverage/lcov.info | head -10 || true
                    echo "[INFO] lcov.info paths ready for SonarQube"
                else
                    echo "[WARN] coverage/lcov.info not found - coverage will be 0%"
                fi
            """

            def reports_pattern = (unit_test_reports_path != null && unit_test_reports_path != 'null' && unit_test_reports_path != '') ? unit_test_reports_path : '**/junit*.xml'
            def findbugs_pattern = step_params.findbugs_test_report_path ?: ''
            reports_manager.publish_static_code_analysis_issues(unit_test_reports_path: reports_pattern, findbugs_test_report_path: "${findbugs_pattern}")

            // Publish HTML coverage report if generated (use relative path from current dir)
            if (fileExists("coverage/lcov-report/index.html")) {
                publishHTML([allowMissing: true, alwaysLinkToLastBuild: true, keepAll: true, reportDir: 'coverage/lcov-report', reportFiles: 'index.html', reportName: 'Unit Test Coverage Report', reportTitles: 'Unit Test Coverage', useWrapperFileDirectly: true])
            } else if (fileExists("coverage/index.html")) {
                publishHTML([allowMissing: true, alwaysLinkToLastBuild: true, keepAll: true, reportDir: 'coverage', reportFiles: 'index.html', reportName: 'Unit Test Coverage Report', reportTitles: 'Unit Test Coverage', useWrapperFileDirectly: true])
            }
        }
        catch (Exception e) {
            // Still publish any XML / HTML reports if generated before exception
            def reports_pattern = (unit_test_reports_path != null && unit_test_reports_path != 'null' && unit_test_reports_path != '') ? unit_test_reports_path : '**/junit*.xml'
            def findbugs_pattern = step_params.findbugs_test_report_path ?: ''
            try {
                reports_manager.publish_static_code_analysis_issues(unit_test_reports_path: reports_pattern, findbugs_test_report_path: "${findbugs_pattern}")
                if (fileExists("coverage/lcov-report/index.html")) {
                    publishHTML([allowMissing: true, alwaysLinkToLastBuild: true, keepAll: true, reportDir: 'coverage/lcov-report', reportFiles: 'index.html', reportName: 'Unit Test Coverage Report', reportTitles: 'Unit Test Coverage', useWrapperFileDirectly: true])
                } else if (fileExists("coverage/index.html")) {
                    publishHTML([allowMissing: true, alwaysLinkToLastBuild: true, keepAll: true, reportDir: 'coverage', reportFiles: 'index.html', reportName: 'Unit Test Coverage Report', reportTitles: 'Unit Test Coverage', useWrapperFileDirectly: true])
                }
            } catch (ignored) {}

            if (fail_job_if_unit_issue_detected == 'false' || fail_job_if_unit_issue_detected == false) {
                logger.logger('msg':'Unit Test found Issues!! Ignoring as per User inputs', 'level':'WARN')
            }
            else {
                logger.logger('msg':"Unit Test found Issues!!! Unit Testing Failed Error Details: ${e}", 'level':'ERROR')
                error()
            }
        }
    }
}

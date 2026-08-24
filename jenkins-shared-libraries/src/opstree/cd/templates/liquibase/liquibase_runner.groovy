package opstree.cd.templates.liquibase

import opstree.common.*

def get_params_value(Boolean enableOverride, Map step_params, String paramName) {
    return enableOverride && params.containsKey(paramName) ? params[paramName] : step_params[paramName]
}

def call(Map step_params) {
    ansiColor('xterm') {
        def enableOverride = step_params.enable_jenkins_build_param_override?.toBoolean() ?: false
        def workspace      = new workspace_management()
        def lbJarFile      = ""
        try {
            def appName         = step_params.app_name ?: ''
            def autoDetectedJar = sh(script: "find /var/lib/jenkins/liquibase-jars/${appName} -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' 2>/dev/null | head -1", returnStdout: true).trim()
            def passedJar       = params.JAR_FILE?.trim()

            if (passedJar && fileExists(passedJar)) {
                lbJarFile = passedJar
            } else if (autoDetectedJar && fileExists(autoDetectedJar)) {
                lbJarFile = autoDetectedJar
            } else {
                lbJarFile = autoDetectedJar ?: passedJar
            }
            def lbMigrations = step_params.liquibase_migrations ?: []
            def appEnv       = params.ENVIRONMENT ?: (step_params.app_env ?: 'dev')

            // Manual Approval configuration
            def approvalMsg  = get_params_value(enableOverride, step_params, 'manual_approval_message') ?: 'Review pending DB changes & SQL diff above. Approve migration execution?'
            def allowedUsers = get_params_value(enableOverride, step_params, 'manual_approval_allowed_users') ?: ''

            if (!lbJarFile) {
                error "JAR_FILE parameter or liquibase_jar_file is required for Liquibase migration!"
            }

            stage('Extract Changelogs') {
                sh """
                    set -e
                    echo '---------------------------------------------------------------'
                    echo '  Liquibase Migration Job (Mandatory Approval)'
                    echo "  Target Environment : ${appEnv}"
                    echo "  Source JAR File    : ${lbJarFile}"
                    echo '---------------------------------------------------------------'
                    
                    if [ ! -f "${lbJarFile}" ]; then
                        echo " ERROR: JAR file not found at path: ${lbJarFile}"
                        echo " TIP: Please run the CI pipeline (CI/${appName}) first to build and stash the latest application JAR."
                        exit 1
                    fi

                    unzip -o '${lbJarFile}' 'BOOT-INF/classes/db/changelog/*' -d extracted/

                    echo ''
                    echo '-- Pulling official Liquibase Docker image --'
                    docker pull liquibase/liquibase

                    echo ''
                    echo '-- Ensuring MySQL JDBC driver is available --'
                    DRIVER_DIR=/var/lib/jenkins/liquibase-drivers
                    DRIVER_JAR=\${DRIVER_DIR}/mysql-connector-java.jar
                    if [ ! -f "\${DRIVER_JAR}" ]; then
                        echo "Downloading MySQL Connector/J..."
                        mkdir -p \${DRIVER_DIR}
                        curl -L -o "\${DRIVER_JAR}" \\
                            "https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.3.0/mysql-connector-j-8.3.0.jar"
                        echo " MySQL driver downloaded: \${DRIVER_JAR}"
                    else
                        echo " MySQL driver already cached: \${DRIVER_JAR}"
                    fi
                """
            }

            for (int i = 0; i < lbMigrations.size(); i++) {
                def migration       = lbMigrations[i]
                def lbChangelogFile = migration.changelog_file
                def lbDbCredsId     = migration.db_creds_id ?: "${appEnv.toUpperCase()}_${step_params.app_name.replaceAll('-', '_').toUpperCase()}_DB_CREDS"
                def lbDbName        = migration.db_name ?: "db-${i + 1}"

                // -- STAGE 1: PREVIEW / DIFF (STATUS & SQL) --
                stage("Preview DB Changes: ${lbDbName}") {
                    echo "Using Credential ID: ${lbDbCredsId}"
                    withCredentials([file(credentialsId: lbDbCredsId, variable: 'DB_CREDS_FILE')]) {
                        sh """
                            set +x
                            set -e
                            . \$DB_CREDS_FILE

                            if [ "\$SPRING_LIQUIBASE_ENABLED" != "false" ]; then
                                echo "ERROR: SPRING_LIQUIBASE_ENABLED must be 'false' for CD migration."
                                echo "Current value: '\${SPRING_LIQUIBASE_ENABLED:-<unset>}'. Aborting migration."
                                exit 1
                            fi

                            echo '---------------------------------------------------------------'
                            echo "  [DRY-RUN / DIFF] Pending Changesets for: ${lbDbName}"
                            echo '---------------------------------------------------------------'
                            docker run --rm \\
                                -v "\$(pwd)/extracted/BOOT-INF/classes:/liquibase/changelog" \\
                                -v "/var/lib/jenkins/liquibase-drivers:/liquibase/lib" \\
                                -e LIQUIBASE_COMMAND_URL="\$DB_URL" \\
                                -e LIQUIBASE_COMMAND_USERNAME="\$DB_USERNAME" \\
                                -e LIQUIBASE_COMMAND_PASSWORD="\$DB_PASSWORD" \\
                                -e LIQUIBASE_COMMAND_CHANGELOG_FILE="${lbChangelogFile}" \\
                                liquibase/liquibase status --verbose || true

                            echo ''
                            echo '---------------------------------------------------------------'
                            echo "  [DRY-RUN / DIFF] SQL Statements to execute on: ${lbDbName}"
                            echo '---------------------------------------------------------------'
                            docker run --rm \\
                                -v "\$(pwd)/extracted/BOOT-INF/classes:/liquibase/changelog" \\
                                -v "/var/lib/jenkins/liquibase-drivers:/liquibase/lib" \\
                                -e LIQUIBASE_COMMAND_URL="\$DB_URL" \\
                                -e LIQUIBASE_COMMAND_USERNAME="\$DB_USERNAME" \\
                                -e LIQUIBASE_COMMAND_PASSWORD="\$DB_PASSWORD" \\
                                -e LIQUIBASE_COMMAND_CHANGELOG_FILE="${lbChangelogFile}" \\
                                liquibase/liquibase update-sql || true
                        """
                    }
                }

                // -- STAGE 2: MANDATORY MANUAL APPROVAL --
                stage("Manual Approval: ${lbDbName}") {
                    def submitter = (allowedUsers instanceof List) ? allowedUsers.join(',') : allowedUsers.toString()
                    if (submitter) {
                        input(message: "${approvalMsg} [Target DB: ${lbDbName}]", submitter: submitter, ok: 'Approve & Execute Migration')
                    } else {
                        input(message: "${approvalMsg} [Target DB: ${lbDbName}]", ok: 'Approve & Execute Migration')
                    }
                }

                // -- STAGE 3: EXECUTE MIGRATION --
                stage("Migrate DB: ${lbDbName}") {
                    echo "Executing Liquibase Update on: ${lbDbName}"
                    withCredentials([file(credentialsId: lbDbCredsId, variable: 'DB_CREDS_FILE')]) {
                        sh """
                            set +x
                            set -e
                            . \$DB_CREDS_FILE

                            if [ "\$SPRING_LIQUIBASE_ENABLED" != "false" ]; then
                                echo "ERROR: SPRING_LIQUIBASE_ENABLED must be 'false' for CD migration."
                                echo "Current value: '\${SPRING_LIQUIBASE_ENABLED:-<unset>}'. Aborting migration."
                                exit 1
                            fi

                            echo '---------------------------------------------------------------'
                            echo "  Executing Liquibase Update: ${lbChangelogFile} -> ${lbDbName}"
                            echo '---------------------------------------------------------------'

                            docker run --rm \\
                                -v "\$(pwd)/extracted/BOOT-INF/classes:/liquibase/changelog" \\
                                -v "/var/lib/jenkins/liquibase-drivers:/liquibase/lib" \\
                                -e LIQUIBASE_COMMAND_URL="\$DB_URL" \\
                                -e LIQUIBASE_COMMAND_USERNAME="\$DB_USERNAME" \\
                                -e LIQUIBASE_COMMAND_PASSWORD="\$DB_PASSWORD" \\
                                -e LIQUIBASE_COMMAND_CHANGELOG_FILE="${lbChangelogFile}" \\
                                liquibase/liquibase update

                            echo " Liquibase update completed successfully for ${lbDbName}"
                        """
                    }
                }
            }

            // -- TRIGGER DOWNSTREAM CD PIPELINE AFTER MIGRATION SUCCESS --
            def triggerCd = get_params_value(enableOverride, step_params, 'enable_trigger_cd_pipeline')?.toBoolean() ?: true
            if (triggerCd && currentBuild.currentResult != 'FAILURE') {
                def cdJobPath = get_params_value(enableOverride, step_params, 'trigger_cd_pipeline_path') ?: "CD/${step_params.app_name}"
                stage('Trigger CD Pipeline') {
                    echo "---------------------------------------------------------------"
                    echo "  Liquibase migration SUCCESS -> Triggering CD Pipeline: ${cdJobPath}"
                    echo "---------------------------------------------------------------"
                    build job: cdJobPath,
                        parameters: [
                            string(name: 'image_tag', value: params.image_tag ?: 'latest'),
                            string(name: 'ENVIRONMENT', value: appEnv)
                        ],
                        wait: false
                }
            }

        } catch (Exception e) {
            currentBuild.result = 'FAILURE'
            throw e
        } finally {
            stage('Cleanup') {
                sh """
                    rm -rf extracted/ stashed_jar/
                    echo " Cleaned up temporary extracted files."
                """
                if (step_params.clean_workspace != null && step_params.clean_workspace.toBoolean()) {
                    workspace.workspace_management(
                        clean_workspace: 'true',
                        ignore_clean_workspace_failure: 'true',
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
}

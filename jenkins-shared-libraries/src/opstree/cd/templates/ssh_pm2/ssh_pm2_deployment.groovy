// // package opstree.cd.templates.ssh_pm2

// // import opstree.common.*

// // def get_params_value(Boolean enableOverride, Map step_params, String paramName) {
// //     def value = enableOverride && params.containsKey(paramName) ? params[paramName] : step_params[paramName]
// //     if (value instanceof String) {
// //         if (value.equalsIgnoreCase('true')) return true
// //         if (value.equalsIgnoreCase('false')) return false
// //     }
// //     return value
// // }

// // def call(Map step_params) {
// //     ansiColor('xterm') {
// //         def enableOverride = step_params.enable_jenkins_build_param_override?.toBoolean() ?: false

// //         workspace = new workspace_management()
// //         vcs       = new git_management()
// //         notify    = new notify()
// //         parser    = new parser()

// //         def repo_url_type       = get_params_value(enableOverride, step_params, 'repo_url_type') ?: 'http'
// //         def repo_url            = (repo_url_type == 'http') ? get_params_value(enableOverride, step_params, 'repo_https_url') : get_params_value(enableOverride, step_params, 'repo_ssh_url')
// //         def repo_branch         = params.BRANCH ?: (get_params_value(enableOverride, step_params, 'repo_branch') ?: 'main')
// //         def jenkins_git_creds_id= get_params_value(enableOverride, step_params, 'jenkins_git_creds_id') ?: 'hasantha-hrc-gitlab-access'
// //         def source_code_path    = get_params_value(enableOverride, step_params, 'source_code_path') ?: ''
// //         def clean_workspace     = get_params_value(enableOverride, step_params, 'clean_workspace') ?: true

// //         def server_ip           = get_params_value(enableOverride, step_params, 'server_ip')
// //         def deploy_dir          = get_params_value(enableOverride, step_params, 'deploy_dir')
// //         def app_name            = get_params_value(enableOverride, step_params, 'app_name')
// //         def node_version        = get_params_value(enableOverride, step_params, 'node_version') ?: 'v24.6.0'
// //         def node_delete_version = get_params_value(enableOverride, step_params, 'node_delete_version') ?: node_version
// //         def ssh_credentials_id  = get_params_value(enableOverride, step_params, 'ssh_credentials_id') ?: 'HRC-Kollect-Dev-Server'
// //         def pem_key_path        = get_params_value(enableOverride, step_params, 'pem_key_path') ?: '/opt/dev-keys/hrc-kollect-dev.pem'
// //         def secret_arn          = get_params_value(enableOverride, step_params, 'secret_arn') ?: ''
// //         def secret_region       = get_params_value(enableOverride, step_params, 'secret_region') ?: 'us-east-1'
// //         def custom_env_content  = get_params_value(enableOverride, step_params, 'custom_env_content') ?: ''
// //         def package_manager     = get_params_value(enableOverride, step_params, 'package_manager') ?: 'yarn'
// //         def run_db_migration    = get_params_value(enableOverride, step_params, 'run_db_migration') ?: false
// //         def build_app_on_server = get_params_value(enableOverride, step_params, 'build_app_on_server') ?: false
// //         def start_command       = get_params_value(enableOverride, step_params, 'start_command') ?: 'yarn start'
// //         def pm2_services_list   = get_params_value(enableOverride, step_params, 'pm2_services_list') ?: []
// //         def image_tag           = params.image_tag ?: 'latest'
// //         def environment_name    = params.ENVIRONMENT ?: 'dev'
// //         def tar_name            = "${app_name}.tar.gz"

// //         def deployInfo = [
// //             'App Name'        : app_name,
// //             'Target Server'   : server_ip,
// //             'Tag / Version'   : image_tag,
// //             'Branch'          : repo_branch,
// //             'Environment'     : environment_name,
// //             'Deploy Directory': deploy_dir
// //         ]

// //         try {
// //             stage('Get Pipeline ID') {
// //                 currentBuild.description = "Deploying ${app_name} [Branch: ${repo_branch}] → ${environment_name} (${server_ip})"
// //                 echo "Pipeline ID: ${currentBuild.number}"
// //             }

// //             if (repo_url) {
// //                 stage('Git Checkout') {
// //                     vcs.git_checkout(
// //                         repo_url: "${repo_url}",
// //                         repo_branch: "${repo_branch}",
// //                         clean_workspace: "${clean_workspace}",
// //                         repo_url_type: "${repo_url_type}",
// //                         jenkins_git_creds_id: "${jenkins_git_creds_id}",
// //                         source_code_path: "${source_code_path}"
// //                     )
// //                 }
// //             }

// //             stage('Clean Remote Directory') {
// //                 sshagent([ssh_credentials_id]) {
// //                     sh """#!/bin/bash
// //                         ssh -o StrictHostKeyChecking=no ubuntu@${server_ip} '
// //                             mkdir -p ${deploy_dir} &&
// //                             cd ${deploy_dir} &&
// //                             rm -rf ./* .env .next 2>/dev/null || true
// //                         '
// //                     """
// //                 }
// //             }

// //             stage('Copy Source Archive to Server') {
// //                 def repo_dir = repo_url ? parser.fetch_git_repo_name('repo_url': "${repo_url}") : ''
// //                 def src_path = repo_dir ? "${WORKSPACE}/${repo_dir}${source_code_path}" : "${WORKSPACE}"

// //                 dir(src_path) {
// //                     sh """#!/bin/bash
// //                         set -e
// //                         tar -czf ${tar_name} .[!.]* * 2>/dev/null || tar -czf ${tar_name} *
// //                         scp -i ${pem_key_path} -o StrictHostKeyChecking=no \\
// //                             ./${tar_name} ubuntu@${server_ip}:${deploy_dir}/
// //                     """
// //                 }
// //             }

// //             stage('Create .env Configuration') {
// //                 sshagent([ssh_credentials_id]) {
// //                     if (custom_env_content) {
// //                         sh """#!/bin/bash
// //                             ssh -o StrictHostKeyChecking=no ubuntu@${server_ip} '
// //                                 cd ${deploy_dir}
// //                                 cat > .env << "EOF"
// // ${custom_env_content}
// // EOF
// //                             '
// //                         """
// //                     } else if (secret_arn) {
// //                         sh """#!/bin/bash
// //                             ssh -o StrictHostKeyChecking=no ubuntu@${server_ip} '
// //                                 cd ${deploy_dir} &&
// //                                 touch .env &&
// //                                 echo "NODE_ENV=production" > .env &&
// //                                 echo "SECRET_KEY_MANAGER_KEY=${secret_arn}" >> .env &&
// //                                 echo "SECRET_KEY_MANAGER_REGION=${secret_region}" >> .env
// //                             '
// //                         """
// //                     }
// //                 }
// //             }

// //             stage('Remove Old PM2 Services') {
// //                 sshagent([ssh_credentials_id]) {
// //                     if (pm2_services_list && pm2_services_list.size() > 0) {
// //                         for (service in pm2_services_list) {
// //                             def serviceRunning = sh(
// //                                 script: """#!/bin/bash
// //                                     ssh -o StrictHostKeyChecking=no ubuntu@${server_ip} \\
// //                                     'source /home/ubuntu/.nvm/nvm.sh && nvm use ${node_delete_version} && \\
// //                                      pm2 jlist | jq -e \\'.[] | select(.name=="${service}")\\' > /dev/null'
// //                                 """,
// //                                 returnStatus: true
// //                             )
// //                             if (serviceRunning == 0) {
// //                                 echo "Removing PM2 service: ${service}"
// //                                 sh """#!/bin/bash
// //                                     ssh -o StrictHostKeyChecking=no ubuntu@${server_ip} \\
// //                                     'source /home/ubuntu/.nvm/nvm.sh && nvm use ${node_delete_version} && pm2 delete "${service}"'
// //                                 """
// //                             }
// //                         }
// //                     } else {
// //                         def serviceRunning = sh(
// //                             script: """#!/bin/bash
// //                                 ssh -o StrictHostKeyChecking=no ubuntu@${server_ip} \\
// //                                 'source /home/ubuntu/.nvm/nvm.sh && nvm use ${node_delete_version} && pm2 list | grep -q "${app_name}"'
// //                             """,
// //                             returnStatus: true
// //                         )
// //                         if (serviceRunning == 0) {
// //                             echo "Removing running PM2 service: ${app_name}"
// //                             sh """#!/bin/bash
// //                                 ssh -o StrictHostKeyChecking=no ubuntu@${server_ip} \\
// //                                 'source /home/ubuntu/.nvm/nvm.sh && nvm use ${node_delete_version} && pm2 delete ${app_name}'
// //                             """
// //                         }
// //                     }
// //                 }
// //             }

// //             stage('Deploy & Start Application') {
// //                 sshagent([ssh_credentials_id]) {
// //                     def commands = [
// //                         "set -e",
// //                         "source /home/ubuntu/.nvm/nvm.sh",
// //                         "nvm use ${node_version}",
// //                         "node -v",
// //                         "cd ${deploy_dir}",
// //                         "tar -xzf ${tar_name}",
// //                         "rm -f ${tar_name}"
// //                     ]

// //                     if (package_manager == 'yarn') {
// //                         commands.add("yarn --frozen-lockfile || yarn install")
// //                     } else {
// //                         commands.add("npm install")
// //                     }

// //                     if (build_app_on_server.toBoolean()) {
// //                         if (package_manager == 'yarn') {
// //                             commands.add("yarn build")
// //                         } else {
// //                             commands.add("npm run build")
// //                         }
// //                     }

// //                     if (run_db_migration.toBoolean()) {
// //                         commands.add("yarn sequelize db:migrate || true")
// //                     }

// //                     if (pm2_services_list && pm2_services_list.size() > 0) {
// //                         commands.add("node start-crons.js")
// //                     } else {
// //                         commands.add("pm2 start \"${start_command}\" --name ${app_name}")
// //                     }

// //                     commands.add("pm2 save")

// //                     def fullScript = commands.join(" && \n")
// //                     sh """#!/bin/bash
// //                         ssh -o StrictHostKeyChecking=no ubuntu@${server_ip} '
// // ${fullScript}
// //                         '
// //                     """
// //                 }
// //             }

// //         } catch (Exception e) {
// //             currentBuild.result = 'FAILURE'
// //             if (get_params_value(enableOverride, step_params, 'notification_enabled') != null && get_params_value(enableOverride, step_params, 'notification_enabled').toBoolean()) {
// //                 notify.notification_factory(
// //                     build_status: 'Failure',
// //                     webhook_url_creds_id: "${get_params_value(enableOverride, step_params, 'webhook_url_creds_id')}",
// //                     notification_channel: "${get_params_value(enableOverride, step_params, 'notification_channel') ?: 'gmail'}",
// //                     notification_enabled: "${get_params_value(enableOverride, step_params, 'notification_enabled')}",
// //                     gmail_notification_recipients_email_ids: "${get_params_value(enableOverride, step_params, 'gmail_notification_recipients_email_ids') ?: 'asangai@healthreconconnect.com'}",
// //                     gmail_notification_from_email_id: "${get_params_value(enableOverride, step_params, 'gmail_notification_from_email_id') ?: 'smtp@kernernorland.com'}",
// //                     deployment_details: deployInfo
// //                 )
// //             }
// //             throw e
// //         } finally {
// //             if (get_params_value(enableOverride, step_params, 'notification_enabled') != null && get_params_value(enableOverride, step_params, 'notification_enabled').toBoolean()) {
// //                 if (currentBuild.currentResult == 'SUCCESS' || currentBuild.currentResult == 'UNSTABLE') {
// //                     notify.notification_factory(
// //                         build_status: 'Success',
// //                         webhook_url_creds_id: "${get_params_value(enableOverride, step_params, 'webhook_url_creds_id')}",
// //                         notification_channel: "${get_params_value(enableOverride, step_params, 'notification_channel') ?: 'gmail'}",
// //                         notification_enabled: "${get_params_value(enableOverride, step_params, 'notification_enabled')}",
// //                         gmail_notification_recipients_email_ids: "${get_params_value(enableOverride, step_params, 'gmail_notification_recipients_email_ids') ?: 'asangai@healthreconconnect.com'}",
// //                         gmail_notification_from_email_id: "${get_params_value(enableOverride, step_params, 'gmail_notification_from_email_id') ?: 'smtp@kernernorland.com'}",
// //                         deployment_details: deployInfo
// //                     )
// //                 }
// //             }

// //             if (get_params_value(enableOverride, step_params, 'clean_workspace') != null && get_params_value(enableOverride, step_params, 'clean_workspace').toBoolean()) {
// //                 workspace.workspace_management(
// //                     clean_workspace: 'true',
// //                     ignore_clean_workspace_failure: 'false',
// //                     delete_dirs: 'false',
// //                     clean_when_build_aborted: 'true',
// //                     clean_when_build_failed: 'true',
// //                     clean_when_not_built: 'true',
// //                     clean_when_build_succeed: 'true',
// //                     clean_when_build_unstable: 'true'
// //                 )
// //             }
// //         }
// //     }
// // }

// ##############################################################################################

// package opstree.cd.templates.ssh_pm2

// import opstree.common.*

// def get_params_value(Boolean enableOverride, Map step_params, String paramName) {
//     def value = null

//     // 1. Direct step parameters passed to call()
//     if (step_params != null && step_params.containsKey(paramName) && step_params[paramName] != null && "${step_params[paramName]}".trim() != '') {
//         value = step_params[paramName]
//     }
//     // 2. Build parameters (Build with Parameters / upstream trigger)
//     else if (params != null && params.containsKey(paramName) && params[paramName] != null && "${params[paramName]}".trim() != '') {
//         value = params[paramName]
//     }
//     // 3. Environment variables
//     else if (env != null && env.getProperty(paramName) != null && "${env.getProperty(paramName)}".trim() != '') {
//         value = env.getProperty(paramName)
//     }

//     if (value instanceof String) {
//         if (value.equalsIgnoreCase('true')) return true
//         if (value.equalsIgnoreCase('false')) return false
//     }
//     return value
// }

// def call(Map step_params) {
//     ansiColor('xterm') {
//         def enableOverride = step_params.enable_jenkins_build_param_override?.toBoolean() ?: false

//         def workspace = new workspace_management()
//         def notify    = new notify()

//         def environment_name      = get_params_value(enableOverride, step_params, 'ENVIRONMENT') ?: 'TEST'
//         def repo_branch           = get_params_value(enableOverride, step_params, 'BRANCH') ?: 'main'
//         def server_ip             = get_params_value(enableOverride, step_params, 'server_ip')
//         def deploy_dir            = get_params_value(enableOverride, step_params, 'deploy_dir')
//         def app_name              = get_params_value(enableOverride, step_params, 'app_name')
//         def node_version          = get_params_value(enableOverride, step_params, 'node_version') ?: 'v20.18.0'
//         def ssh_credentials_id    = get_params_value(enableOverride, step_params, 'ssh_credentials_id') ?: 'HRC-Kollect-Test-Server'
//         def secret_arn            = get_params_value(enableOverride, step_params, 'secret_arn') ?: ''
//         def secret_region         = get_params_value(enableOverride, step_params, 'secret_region') ?: 'us-east-1'
//         def custom_env_content    = get_params_value(enableOverride, step_params, 'custom_env_content') ?: ''
//         def run_db_migration      = get_params_value(enableOverride, step_params, 'run_db_migration') ?: false
//         def start_command         = get_params_value(enableOverride, step_params, 'start_command') ?: ''
//         def health_check_endpoint = get_params_value(enableOverride, step_params, 'health_check_endpoint') ?: 'http://127.0.0.1:80/api/health'

//         // S3 Tarball parameters (supports both artifact_name and image_tag)
//         def s3_bucket     = get_params_value(enableOverride, step_params, 'artifact_s3_bucket_name') ?: 'hrc-cicd-test-bucket'
//         def s3_keypath    = get_params_value(enableOverride, step_params, 'artifact_s3_keypath_destination') ?: 'backend'
//         def artifact_name = get_params_value(enableOverride, step_params, 'artifact_name') ?: get_params_value(enableOverride, step_params, 'image_tag') ?: ''

//         echo "[DEBUG] Resolved Environment : ${environment_name}"
//         echo "[DEBUG] Resolved Server IP   : ${server_ip}"
//         echo "[DEBUG] Resolved Deploy Dir  : ${deploy_dir}"
//         echo "[DEBUG] Resolved Artifact    : ${artifact_name}"
//         echo "[DEBUG] Resolved S3 Bucket   : ${s3_bucket}/${s3_keypath}"

//         if (!artifact_name) {
//             error("[FATAL] Parameter 'artifact_name' could not be resolved! Check upstream CI or pass it in Build with Parameters.")
//         }

//         if (!server_ip || !deploy_dir) {
//             error("[FATAL] Target server IP or deploy directory is empty for environment '${environment_name}'.")
//         }

//         def deployInfo = [
//             'App Name'        : app_name,
//             'Target Server'   : server_ip,
//             'Artifact'        : artifact_name,
//             'S3 Path'         : "s3://${s3_bucket}/${s3_keypath}/${artifact_name}",
//             'Branch'          : repo_branch,
//             'Environment'     : environment_name,
//             'Deploy Directory': deploy_dir
//         ]

//         try {
//             stage('Pipeline Info') {
//                 currentBuild.description = "Recreate Deploy: ${app_name} [${artifact_name}] -> ${environment_name} (${server_ip})"
//                 echo "[INFO] Target Host: ${server_ip} | Deploy Dir: ${deploy_dir}"
//                 echo "[INFO] S3 Artifact: s3://${s3_bucket}/${s3_keypath}/${artifact_name}"
//             }

//             stage('Execute Recreate Deployment') {
//                 sshagent([ssh_credentials_id]) {
//                     sh """
//                         echo "[INFO] Verifying SSH connectivity to ${server_ip}:22..."
//                         ssh -o StrictHostKeyChecking=no -o ConnectTimeout=10 -o BatchMode=yes ubuntu@${server_ip} 'echo "[SUCCESS] Connected to \$(hostname)"'
//                     """

//                     def deployScript = """
//                         set -e
//                         echo "=========================================================="
//                         echo "Starting S3 Recreate Deployment for ${app_name}"
//                         echo "=========================================================="

//                         # Load Node/NVM Environment
//                         if [ -f "\$HOME/.nvm/nvm.sh" ]; then
//                             source "\$HOME/.nvm/nvm.sh"
//                             nvm use ${node_version} 2>/dev/null || nvm use default 2>/dev/null || true
//                         fi

//                         # 1. Stop and delete existing PM2 process
//                         echo "[1/6] Stopping existing PM2 process..."
//                         pm2 delete "${app_name}" 2>/dev/null || true

//                         # 2. Prepare directories and backup existing build
//                         mkdir -p "${deploy_dir}"
//                         cd "${deploy_dir}"

//                         if [ -d "dist" ]; then
//                             echo "[2/6] Backing up existing dist..."
//                             rm -rf dist.bak
//                             mv dist dist.bak
//                         fi

//                         # 3. Pull bundle from S3
//                         echo "[3/6] Fetching artifact from S3: s3://${s3_bucket}/${s3_keypath}/${artifact_name}..."
//                         aws s3 cp "s3://${s3_bucket}/${s3_keypath}/${artifact_name}" /tmp/release.tar.gz --region "${secret_region}"

//                         # 4. Extract bundle
//                         echo "[4/6] Unpacking release bundle..."
//                         tar -xzf /tmp/release.tar.gz -C "${deploy_dir}"
//                         rm -f /tmp/release.tar.gz

//                         # Print folder contents for visibility
//                         echo "--- Extracted Directory Structure ---"
//                         ls -la
//                         if [ -d "dist" ]; then
//                             echo "--- Contents of dist/ ---"
//                             ls -la dist/
//                         fi

//                         # 5. Inject .env configuration
//                         echo "[5/6] Writing .env configuration..."
//                     """

//                     if (custom_env_content) {
//                         deployScript += """
//                             cat > .env << 'EOF'
// ${custom_env_content}
// EOF
//                         """
//                     } else if (secret_arn) {
//                         deployScript += """
//                             touch .env
//                             echo "NODE_ENV=production" > .env
//                             echo "SECRET_KEY_MANAGER_KEY=${secret_arn}" >> .env
//                             echo "SECRET_KEY_MANAGER_REGION=${secret_region}" >> .env
//                         """
//                     }

//                     if (run_db_migration.toBoolean()) {
//                         deployScript += """
//                             echo "Executing DB migrations..."
//                             yarn sequelize db:migrate 2>/dev/null || true
//                         """
//                     }

//                     deployScript += """
//                         # 6. Locate Entry Point & Start Application under PM2
//                         echo "[6/6] Determining entry point and starting under PM2..."

//                         ENTRY_TARGET=""
                        
//                         # Priority 1: ecosystem.config.js / ecosystem.json
//                         if [ -f "ecosystem.config.js" ]; then
//                             ENTRY_TARGET="ecosystem.config.js"
//                         elif [ -f "ecosystem.json" ]; then
//                             ENTRY_TARGET="ecosystem.json"
//                         # Priority 2: main field in package.json
//                         elif [ -f "package.json" ] && grep -q '"main"' package.json; then
//                             PKG_MAIN=\$(grep -o '"main": *"[^"]*"' package.json | head -n 1 | cut -d'"' -f4)
//                             if [ -n "\$PKG_MAIN" ] && [ -f "\$PKG_MAIN" ]; then
//                                 ENTRY_TARGET="\$PKG_MAIN"
//                             fi
//                         fi

//                         # Priority 3: Search common entry points
//                         if [ -z "\$ENTRY_TARGET" ]; then
//                             for file in dist/index.js dist/main.js dist/server.js dist/app.js dist/src/index.js dist/src/main.js server.js index.js app.js; do
//                                 if [ -f "\$file" ]; then
//                                     ENTRY_TARGET="\$file"
//                                     break
//                                 fi
//                             done
//                         fi

//                         # Priority 4: First available JS file in dist/ or root
//                         if [ -z "\$ENTRY_TARGET" ]; then
//                             ENTRY_TARGET=\$(find dist/ -maxdepth 2 -name "*.js" 2>/dev/null | head -n 1)
//                         fi

//                         # Priority 5: Fallback to custom start command if set
//                         if [ -z "\$ENTRY_TARGET" ] && [ -n "${start_command}" ]; then
//                             ENTRY_TARGET="${start_command}"
//                         fi

//                         if [ -z "\$ENTRY_TARGET" ]; then
//                             echo "[FATAL] No valid entry file (main.js, index.js, ecosystem.config.js, server.js) found in release bundle!"
//                             ls -la
//                             exit 1
//                         fi

//                         echo "Starting application with entry target: \$ENTRY_TARGET"
//                         pm2 start "\$ENTRY_TARGET" --name "${app_name}" --update-env
//                         pm2 save

//                         # Allow runtime to boot
//                         sleep 4
//                         pm2 status

//                         # 7. Post-deployment health verification
//                         echo "Verifying health on ${health_check_endpoint}..."
//                         HEALTHY=false
//                         HTTP_STATUS=""
//                         for i in \$(seq 1 15); do
//                             HTTP_STATUS=\$(curl -s -o /dev/null -w "%{http_code}" "${health_check_endpoint}" || true)
//                             echo "Check \$i/15: Endpoint HTTP Status = \$HTTP_STATUS"

//                             if [ "\$HTTP_STATUS" != "000" ] && [ -n "\$HTTP_STATUS" ]; then
//                                 HEALTHY=true
//                                 break
//                             fi
//                             sleep 3
//                         done

//                         if [ "\$HEALTHY" = true ]; then
//                             echo "=========================================================="
//                             echo "SUCCESS: ${app_name} is running and reachable (HTTP \$HTTP_STATUS)."
//                             echo "=========================================================="
//                             rm -rf dist.bak
//                         else
//                             echo "=========================================================="
//                             echo "[ERROR] Health check failed! (HTTP Status: \$HTTP_STATUS)"
//                             echo "=========================================================="
//                             echo "--- Recent Application Error Logs ---"
//                             pm2 logs "${app_name}" --lines 40 --nostream || true
                            
//                             echo "--- Active Ports on Host ---"
//                             sudo ss -tulpn | grep -E 'node|pm2|80|3000|8080|9090' || true

//                             if [ -d "dist.bak" ]; then
//                                 echo "Rolling back to previous backup build..."
//                                 rm -rf dist
//                                 mv dist.bak dist
//                                 pm2 restart "${app_name}" 2>/dev/null || true
//                             fi
//                             exit 1
//                         fi
//                     """

//                     sh """#!/bin/bash
//                         ssh -o StrictHostKeyChecking=no -o ConnectTimeout=10 -o BatchMode=yes ubuntu@${server_ip} 'bash -s' << 'EOF'
// ${deployScript}
// EOF
//                     """
//                 }
//             }

//         } catch (Exception e) {
//             currentBuild.result = 'FAILURE'
//             if (get_params_value(enableOverride, step_params, 'notification_enabled') != null && get_params_value(enableOverride, step_params, 'notification_enabled').toBoolean()) {
//                 notify.notification_factory(
//                     build_status: 'Failure',
//                     webhook_url_creds_id: "${get_params_value(enableOverride, step_params, 'webhook_url_creds_id')}",
//                     notification_channel: "${get_params_value(enableOverride, step_params, 'notification_channel') ?: 'gmail'}",
//                     notification_enabled: "${get_params_value(enableOverride, step_params, 'notification_enabled')}",
//                     gmail_notification_recipients_email_ids: "${get_params_value(enableOverride, step_params, 'gmail_notification_recipients_email_ids') ?: 'asangai@healthreconconnect.com'}",
//                     gmail_notification_from_email_id: "${get_params_value(enableOverride, step_params, 'gmail_notification_from_email_id') ?: 'smtp@kernernorland.com'}",
//                     deployment_details: deployInfo
//                 )
//             }
//             throw e
//         } finally {
//             if (get_params_value(enableOverride, step_params, 'notification_enabled') != null && get_params_value(enableOverride, step_params, 'notification_enabled').toBoolean()) {
//                 if (currentBuild.currentResult == 'SUCCESS' || currentBuild.currentResult == 'UNSTABLE') {
//                     notify.notification_factory(
//                         build_status: 'Success',
//                         webhook_url_creds_id: "${get_params_value(enableOverride, step_params, 'webhook_url_creds_id')}",
//                         notification_channel: "${get_params_value(enableOverride, step_params, 'notification_channel') ?: 'gmail'}",
//                         notification_enabled: "${get_params_value(enableOverride, step_params, 'notification_enabled')}",
//                         gmail_notification_recipients_email_ids: "${get_params_value(enableOverride, step_params, 'gmail_notification_recipients_email_ids') ?: 'asangai@healthreconconnect.com'}",
//                         gmail_notification_from_email_id: "${get_params_value(enableOverride, step_params, 'gmail_notification_from_email_id') ?: 'smtp@kernernorland.com'}",
//                         deployment_details: deployInfo
//                     )
//                 }
//             }

//             if (get_params_value(enableOverride, step_params, 'clean_workspace') != null && get_params_value(enableOverride, step_params, 'clean_workspace').toBoolean()) {
//                 workspace.workspace_management(
//                     clean_workspace: 'true',
//                     ignore_clean_workspace_failure: 'false',
//                     delete_dirs: 'false',
//                     clean_when_build_aborted: 'true',
//                     clean_when_build_failed: 'true',
//                     clean_when_not_built: 'true',
//                     clean_when_build_succeed: 'true',
//                     clean_when_build_unstable: 'true'
//                 )
//             }
//         }
//     }
// }

package opstree.cd.templates.ssh_pm2

import opstree.common.*

def get_params_value(Boolean enableOverride, Map step_params, String paramName) {
    def value = null

    // 1. Direct step parameters passed to call()
    if (step_params != null && step_params.containsKey(paramName) && step_params[paramName] != null && "${step_params[paramName]}".trim() != '') {
        value = step_params[paramName]
    }
    // 2. Build parameters (Build with Parameters / upstream trigger)
    else if (params != null && params.containsKey(paramName) && params[paramName] != null && "${params[paramName]}".trim() != '') {
        value = params[paramName]
    }
    // 3. Environment variables
    else if (env != null && env.getProperty(paramName) != null && "${env.getProperty(paramName)}".trim() != '') {
        value = env.getProperty(paramName)
    }

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

        def environment_name      = get_params_value(enableOverride, step_params, 'ENVIRONMENT') ?: 'TEST'
        def repo_branch           = get_params_value(enableOverride, step_params, 'BRANCH') ?: 'main'
        def server_ip             = get_params_value(enableOverride, step_params, 'server_ip')
        def deploy_dir            = get_params_value(enableOverride, step_params, 'deploy_dir')
        def app_name              = get_params_value(enableOverride, step_params, 'app_name')
        def node_version          = get_params_value(enableOverride, step_params, 'node_version') ?: 'v20.18.0'
        def ssh_credentials_id    = get_params_value(enableOverride, step_params, 'ssh_credentials_id') ?: 'HRC-Kollect-Test-Server'
        def secret_arn            = get_params_value(enableOverride, step_params, 'secret_arn') ?: ''
        def secret_region         = get_params_value(enableOverride, step_params, 'secret_region') ?: 'us-east-1'
        def custom_env_content    = get_params_value(enableOverride, step_params, 'custom_env_content') ?: ''
        def extra_env_vars        = step_params.extra_env_vars instanceof Map ? step_params.extra_env_vars : [:]
        def run_db_migration      = get_params_value(enableOverride, step_params, 'run_db_migration') ?: false
        def start_command         = get_params_value(enableOverride, step_params, 'start_command') ?: 'dist/main.js'
        def health_check_endpoint = get_params_value(enableOverride, step_params, 'health_check_endpoint') ?: 'http://127.0.0.1:3200/api-v2/healthcheck'

        // S3 Tarball parameters
        def s3_bucket     = get_params_value(enableOverride, step_params, 'artifact_s3_bucket_name') ?: 'hrc-cicd-test-bucket'
        def s3_keypath    = get_params_value(enableOverride, step_params, 'artifact_s3_keypath_destination') ?: 'backend'
        def artifact_name = get_params_value(enableOverride, step_params, 'artifact_name') ?: get_params_value(enableOverride, step_params, 'image_tag') ?: ''

        echo "[DEBUG] Resolved Environment : ${environment_name}"
        echo "[DEBUG] Resolved Server IP   : ${server_ip}"
        echo "[DEBUG] Resolved Deploy Dir  : ${deploy_dir}"
        echo "[DEBUG] Resolved Artifact    : ${artifact_name}"
        echo "[DEBUG] Resolved S3 Bucket   : ${s3_bucket}/${s3_keypath}"

        if (!artifact_name) {
            error("[FATAL] Parameter 'artifact_name' could not be resolved! Check upstream CI or pass it in Build with Parameters.")
        }

        if (!server_ip || !deploy_dir) {
            error("[FATAL] Target server IP or deploy directory is empty for environment '${environment_name}'.")
        }

        def deployInfo = [
            'App Name'        : app_name,
            'Target Server'   : server_ip,
            'Artifact'        : artifact_name,
            'S3 Path'         : "s3://${s3_bucket}/${s3_keypath}/${artifact_name}",
            'Branch'          : repo_branch,
            'Environment'     : environment_name,
            'Deploy Directory': deploy_dir
        ]

        try {
            stage('Pipeline Info') {
                currentBuild.description = "Recreate Deploy: ${app_name} [${artifact_name}] -> ${environment_name} (${server_ip})"
                echo "[INFO] Target Host: ${server_ip} | Deploy Dir: ${deploy_dir}"
                echo "[INFO] S3 Artifact: s3://${s3_bucket}/${s3_keypath}/${artifact_name}"
            }

            stage('Execute Recreate Deployment') {
                sshagent([ssh_credentials_id]) {
                    sh """
                        echo "[INFO] Verifying SSH connectivity to ${server_ip}:22..."
                        ssh -o StrictHostKeyChecking=no -o ConnectTimeout=10 -o BatchMode=yes ubuntu@${server_ip} 'echo "[SUCCESS] Connected to \$(hostname)"'
                    """

                    def deployScript = """
                        set -e
                        echo "=========================================================="
                        echo "Starting S3 Recreate Deployment for ${app_name}"
                        echo "=========================================================="

                        # Load Node/NVM Environment
                        if [ -f "\$HOME/.nvm/nvm.sh" ]; then
                            source "\$HOME/.nvm/nvm.sh"
                            nvm use ${node_version} 2>/dev/null || nvm use default 2>/dev/null || true
                        fi

                        # 1. Stop PM2 process
                        echo "[1/6] Stopping existing PM2 process..."
                        pm2 delete "${app_name}" 2>/dev/null || true

                        # 2. Directory setup & backup
                        mkdir -p "${deploy_dir}"
                        cd "${deploy_dir}"

                        if [ -d "dist" ]; then
                            echo "[2/6] Backing up existing dist..."
                            rm -rf dist.bak
                            mv dist dist.bak
                        fi

                        # Preserve existing .env if backup exists
                        if [ -f ".env" ]; then
                            cp .env /tmp/${app_name}.env.bak
                        fi

                        # 3. Pull bundle from S3
                        echo "[3/6] Fetching artifact from S3: s3://${s3_bucket}/${s3_keypath}/${artifact_name}..."
                        aws s3 cp "s3://${s3_bucket}/${s3_keypath}/${artifact_name}" /tmp/release.tar.gz --region "${secret_region}"

                        # 4. Extract bundle
                        echo "[4/6] Unpacking release bundle..."
                        tar -xzf /tmp/release.tar.gz -C "${deploy_dir}"
                        rm -f /tmp/release.tar.gz

                        # 5. Inject .env Configuration
                        echo "[5/6] Generating .env configuration..."
                    """

                    // Priority 1: Custom env content passed directly
                    if (custom_env_content) {
                        deployScript += """
                            cat > .env << 'EOF'
${custom_env_content}
EOF
                        """
                    } 
                    // Priority 2: AWS Secrets Manager
                    else if (secret_arn) {
                        deployScript += """
                            touch .env
                            echo "NODE_ENV=production" > .env
                            echo "SECRET_KEY_MANAGER_KEY=${secret_arn}" >> .env
                            echo "SECRET_KEY_MANAGER_REGION=${secret_region}" >> .env

                            # Fetch Secrets JSON from AWS Secrets Manager directly into .env
                            if command -v jq >/dev/null 2>&1; then
                                echo "Exporting Secrets Manager variables to .env via jq..."
                                aws secretsmanager get-secret-value --secret-id "${secret_arn}" --region "${secret_region}" --query 'SecretString' --output text 2>/dev/null | jq -r 'to_entries|map("\\(.key)=\\(.value|tostring)")|.[]' >> .env || true
                            fi
                        """
                    }

                    // Append any extra key-values
                    if (extra_env_vars) {
                        extra_env_vars.each { k, v ->
                            deployScript += """
                                echo "${k}=${v}" >> .env
                            """
                        }
                    }

                    deployScript += """
                        # Execute fetchSecrets.js if application uses internal secrets manager fetching
                        if [ -f "fetchSecrets.js" ]; then
                            echo "Running fetchSecrets.js..."
                            node fetchSecrets.js || true
                        fi

                        # Run DB migrations if enabled
                        if [ "${run_db_migration}" = "true" ]; then
                            echo "Executing DB migrations..."
                            yarn sequelize db:migrate 2>/dev/null || npm run sequelize db:migrate 2>/dev/null || true
                        fi

                        # 6. Launch Application under PM2
                        echo "[6/6] Launching PM2 process for ${app_name}..."
                        
                        if [ -f "ecosystem.config.js" ]; then
                            pm2 start ecosystem.config.js --name "${app_name}" --update-env
                        elif [ -f "dist/main.js" ]; then
                            pm2 start dist/main.js --name "${app_name}" --update-env
                        elif [ -f "dist/server.js" ]; then
                            pm2 start dist/server.js --name "${app_name}" --update-env
                        elif [ -f "${start_command}" ]; then
                            pm2 start "${start_command}" --name "${app_name}" --update-env
                        else
                            pm2 start "${start_command}" --name "${app_name}" --update-env
                        fi

                        pm2 save
                        sleep 4
                        pm2 status

                        # 7. Post-deployment health verification
                        echo "Verifying health on ${health_check_endpoint}..."
                        HEALTHY=false
                        HTTP_STATUS=""
                        for i in \$(seq 1 15); do
                            HTTP_STATUS=\$(curl -s -o /dev/null -w "%{http_code}" "${health_check_endpoint}" || true)
                            echo "Check \$i/15: Endpoint HTTP Status = \$HTTP_STATUS"

                            if [ "\$HTTP_STATUS" != "000" ] && [ -n "\$HTTP_STATUS" ]; then
                                HEALTHY=true
                                break
                            fi
                            sleep 3
                        done

                        if [ "\$HEALTHY" = true ]; then
                            echo "=========================================================="
                            echo "SUCCESS: ${app_name} is running and reachable (HTTP \$HTTP_STATUS)."
                            echo "=========================================================="
                            rm -rf dist.bak
                            rm -f /tmp/${app_name}.env.bak
                        else
                            echo "=========================================================="
                            echo "[ERROR] Health check failed! (HTTP Status: \$HTTP_STATUS)"
                            echo "=========================================================="
                            echo "--- Recent Application Error Logs ---"
                            pm2 logs "${app_name}" --lines 40 --nostream || true
                            
                            echo "--- Active Ports on Host ---"
                            sudo ss -tulpn | grep -E 'node|pm2|80|3200|3000|8080|9090' || true

                            if [ -d "dist.bak" ]; then
                                echo "Rolling back to previous backup build..."
                                rm -rf dist
                                mv dist.bak dist
                                pm2 restart "${app_name}" 2>/dev/null || true
                            fi
                            exit 1
                        fi
                    """

                    sh """#!/bin/bash
                        ssh -o StrictHostKeyChecking=no -o ConnectTimeout=10 -o BatchMode=yes ubuntu@${server_ip} 'bash -s' << 'EOF'
${deployScript}
EOF
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
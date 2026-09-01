package opstree.common

import opstree.common.build_dockerfile

import opstree.common.*

def build_factory(Map step_params) {
    def logger = new logger()
    if (step_params.perform_build_dockerfile == 'true' || step_params.perform_build_dockerfile == true) {
        build_dockerfile(step_params)
    } else {
        logger.logger('msg':'No valid option selected for Building Dockerfile. Please mention correct values.', 'level':'WARN')
    }
}

def build_dockerfile(Map step_params) {
    def logger = new logger()
    def parser = new parser()

    logger.logger('msg':'Performing Docker Build Step', 'level':'INFO')

    def repo_url             = "${step_params.repo_url}"
    def repo_dir             = parser.fetch_git_repo_name('repo_url':"${repo_url}")
    def source_code_path     = "${step_params.source_code_path ?: ''}"
    def codeartifact_dependency = "${step_params.codeartifact_dependency}"
    def codeartifact_domain  = "${step_params.codeartifact_domain}"
    def codeartifact_owner   = "${step_params.codeartifact_owner}"
    def static_code_analysis_check = "${step_params.static_code_analysis_check}"
    def app_stack            = "${step_params.app_stack}"
    def image_name           = "${step_params.image_name}"

    def raw_context  = step_params.dockerfile_context?.toString()?.trim()
    def raw_location = step_params.dockerfile_location?.toString()?.trim()
    def raw_build_args = step_params.build_args?.toString()?.trim()

    def dockerfile_context = (raw_context && raw_context != 'null') ? raw_context : ''
    if (dockerfile_context) {
        dockerfile_context = "${WORKSPACE}/${repo_dir}/" + dockerfile_context.replaceAll('^/', '')
    } else {
        dockerfile_context = "${WORKSPACE}/${repo_dir}"
    }

    def dockerfile_location = (raw_location && raw_location != 'null') ? raw_location : ''
    if (dockerfile_location) {
        dockerfile_location = "${WORKSPACE}/${repo_dir}/" + dockerfile_location.replaceAll('^/', '')
    } else {
        dockerfile_location = "${WORKSPACE}/${repo_dir}/Dockerfile"
    }

    def build_args = (raw_build_args && raw_build_args != 'null') ? raw_build_args : ''

    // Build context dir: source_code_path is relative to WORKSPACE, not repo_dir
    def buildDir = source_code_path?.trim() ?
        "${WORKSPACE}/${source_code_path.replaceAll('^/', '')}" :
        "${WORKSPACE}/${repo_dir}"

    def raw_secret_creds_id = step_params.build_secret_creds_id
    def build_secret_creds_id = (raw_secret_creds_id && raw_secret_creds_id != 'null') ? raw_secret_creds_id : ''

    def raw_secret_env_var = step_params.build_secret_env_var
    def build_secret_env_var = (raw_secret_env_var && raw_secret_env_var != 'null') ? raw_secret_env_var : 'azure_face_api_key'

    dir("${buildDir}") {
        if (app_stack == 'java') {
            def currentEnvName = (step_params.environment ?: env.ENVIRONMENT ?: 'dev').toLowerCase()
            sh """
                # 1. Stage compiled JAR for Dockerfile
                TARGET_DIR="${buildDir}/target"
                if [ -d "\$TARGET_DIR" ]; then
                    BUILT_JAR=\$(find \$TARGET_DIR -maxdepth 1 -name '*.jar' ! -name '*-sources.jar' ! -name 'original-*.jar' 2>/dev/null | head -1)
                    if [ -n "\$BUILT_JAR" ] && [ -f "${dockerfile_location}" ]; then
                        EXPECTED_JARS=\$(grep 'COPY' "${dockerfile_location}" 2>/dev/null | grep -o 'target/[^ ]*\\.jar' | awk -F'/' '{print \$2}' | sort -u)
                        for jar_name in \$EXPECTED_JARS; do
                            if [ -n "\$jar_name" ]; then
                                echo "Ensuring required JAR exists for Dockerfile: \$jar_name"
                                cp -f "\$BUILT_JAR" "\$TARGET_DIR/\$jar_name" 2>/dev/null || true
                            fi
                        done
                    fi
                fi

                # 2. Replace APP_ENV in Dockerfile if placeholder exists
                if [ -f "${dockerfile_location}" ]; then
                    sed -i "s/APP_ENV/${currentEnvName}/g" "${dockerfile_location}" 2>/dev/null || true
                fi
            """
        }

        if (build_secret_creds_id) {
            // Pass Jenkins credential to Docker via BuildKit secret mount (id=AZURE_FACE_API_KEY)
            withCredentials([string(credentialsId: build_secret_creds_id, variable: 'THE_SECRET')]) {
                sh """
                    git config --global --add safe.directory ${buildDir} && \\
                    COMMIT_HASH=\$(git rev-parse --short HEAD) && \\
                    DOCKER_BUILDKIT=1 docker build \\
                        --secret id=${build_secret_env_var},env=THE_SECRET \\
                        ${build_args} \\
                        -f ${dockerfile_location} \\
                        -t ${image_name}:\${COMMIT_HASH} \\
                        ${dockerfile_context} && \\
                    docker tag ${image_name}:\${COMMIT_HASH} ${image_name}:latest
                """
            }
        } else if (codeartifact_dependency == 'true') {
            withAWS() {
                def codeArtifactToken = sh(
                    script: """
                        aws codeartifact get-authorization-token --domain ${codeartifact_domain} --domain-owner ${codeartifact_owner} --query authorizationToken --output text
                    """,
                    returnStdout: true
                ).trim()

                def CODEARTIFACT_AUTH_TOKEN = codeArtifactToken
                sh """
                    git config --global --add safe.directory ${buildDir} && \\
                    COMMIT_HASH=\$(git rev-parse --short HEAD) && \\
                    docker build --build-arg CODEARTIFACT_AUTH_TOKEN=${CODEARTIFACT_AUTH_TOKEN} ${build_args} -f ${dockerfile_location} -t ${image_name}:\${COMMIT_HASH} ${dockerfile_context} && \\
                    docker tag ${image_name}:\${COMMIT_HASH} ${image_name}:latest
                """
            }
        } else {
            sh """
                git config --global --add safe.directory ${buildDir} && \\
                COMMIT_HASH=\$(git rev-parse --short HEAD) && \\
                docker build ${build_args} -f ${dockerfile_location} -t ${image_name}:\${COMMIT_HASH} ${dockerfile_context} && \\
                docker tag ${image_name}:\${COMMIT_HASH} ${image_name}:latest
            """
        }

        logger.logger('msg':'Docker Build successful', 'level':'INFO')
    }
}

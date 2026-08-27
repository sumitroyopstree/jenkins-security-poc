package opstree.common

import opstree.common.*

def build_factory(Map step_params) {
    def logger = new logger()
    if (step_params.perform_code_build == 'true' || step_params.perform_code_build == true) {
        build_and_package_node(step_params)
    } else {
        logger.logger('msg':'No valid option selected for Building Node.js Artifact. Skipping step.', 'level':'WARN')
    }
}

def build_and_package_node(Map step_params) {
    def logger = new logger()
    def parser = new parser()

    logger.logger('msg':'Performing Node.js Build & Tarball Packaging Step', 'level':'INFO')

    def repo_url         = "${step_params.repo_url}"
    def source_code_path = "${step_params.source_code_path ?: ''}"
    def node_version     = step_params.node_version ?: "18"
    def package_manager  = step_params.package_manager ?: "yarn"
    def app_name         = step_params.app_name ?: 'hrc-kollect-be'

    def repo_dir     = parser.fetch_git_repo_name('repo_url': "${repo_url}")
    def project_path = "${WORKSPACE}/${repo_dir}${source_code_path}"

    def commit_tag = sh(
        script: "git config --global --add safe.directory ${WORKSPACE}/${repo_dir} && cd ${WORKSPACE}/${repo_dir} && git rev-parse --short HEAD",
        returnStdout: true
    ).trim()

    def artifact_name = "${app_name}-${commit_tag}.tar.gz"
    def artifact_dir  = "${WORKSPACE}/artifact"

    dir(project_path) {
        sh "mkdir -p ${artifact_dir}"

        def build_cmd = ""
        if (package_manager.toLowerCase() == 'yarn') {
            build_cmd = """
                set -e
                echo "=== Running Yarn Install ==="
                yarn install --frozen-lockfile
                
                echo "=== Running Yarn Build ==="
                yarn build
                
                echo "=== Pruning DevDependencies for Production ==="
                yarn install --production --ignore-scripts --prefer-offline
                
                echo "=== Creating Release Tarball ==="
                tar -czf /output/${artifact_name} package.json node_modules dist ecosystem.config.js 2>/dev/null || tar -czf /output/${artifact_name} package.json node_modules dist
            """
        } else {
            build_cmd = """
                set -e
                echo "=== Running NPM Install ==="
                npm ci
                
                echo "=== Running NPM Build ==="
                npm run build --if-present
                
                echo "=== Pruning DevDependencies for Production ==="
                npm prune --production
                
                echo "=== Creating Release Tarball ==="
                tar -czf /output/${artifact_name} package.json node_modules dist ecosystem.config.js 2>/dev/null || tar -czf /output/${artifact_name} package.json node_modules dist
            """
        }

        // Run isolated build and package inside Docker node container
        sh """
            docker run --rm \\
                -v ${project_path}:/app \\
                -v ${artifact_dir}:/output \\
                -w /app \\
                node:${node_version} sh -c '${build_cmd}'
        """

        // Fix permissions so Jenkins workspace cleanup can manage generated files
        sh "sudo chown -R \$(id -u):\$(id -g) ${WORKSPACE} ${artifact_dir} 2>/dev/null || true"

        env.GENERATED_ARTIFACT_PATH = "${artifact_dir}/${artifact_name}"
        env.GENERATED_ARTIFACT_NAME = "${artifact_name}"

        logger.logger('msg':"Artifact successfully built & packed: ${env.GENERATED_ARTIFACT_PATH}", 'level':'INFO')
    }
}
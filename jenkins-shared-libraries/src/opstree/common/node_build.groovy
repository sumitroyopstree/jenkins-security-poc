// package opstree.common

// import opstree.common.*

// def build_factory(Map step_params) {
//     def logger = new logger()
//     if (step_params.perform_code_build == 'true' || step_params.perform_code_build == true) {
//         build_and_package_node(step_params)
//     } else {
//         logger.logger('msg':'No valid option selected for Building Node.js Artifact. Skipping step.', 'level':'WARN')
//     }
// }

// def build_and_package_node(Map step_params) {
//     def logger = new logger()
//     def parser = new parser()

//     logger.logger('msg':'Performing Node.js Build & Tarball Packaging Step', 'level':'INFO')

//     def repo_url         = "${step_params.repo_url}"
//     def source_code_path = "${step_params.source_code_path ?: ''}"
//     def node_version     = step_params.node_version ?: "18"
//     def package_manager  = step_params.package_manager ?: "yarn"
//     def app_name         = step_params.app_name ?: 'hrc-kollect-be'

//     def repo_dir     = parser.fetch_git_repo_name('repo_url': "${repo_url}")
//     def project_path = "${WORKSPACE}/${repo_dir}${source_code_path}"

//     def commit_tag = sh(
//         script: "git config --global --add safe.directory ${WORKSPACE}/${repo_dir} && cd ${WORKSPACE}/${repo_dir} && git rev-parse --short HEAD",
//         returnStdout: true
//     ).trim()

//     def artifact_name = "${app_name}-${commit_tag}.tar.gz"
//     def artifact_dir  = "${WORKSPACE}/artifact"

//     dir(project_path) {
//         sh "mkdir -p ${artifact_dir}"

//         def build_cmd = ""
//         if (package_manager.toLowerCase() == 'yarn') {
//             build_cmd = """
//                 set -e
//                 echo "=== Running Yarn Install ==="
//                 yarn install --frozen-lockfile
                
//                 echo "=== Running Yarn Build ==="
//                 yarn build
                
//                 echo "=== Pruning DevDependencies for Production ==="
//                 yarn install --production --ignore-scripts --prefer-offline
                
//                 echo "=== Creating Release Tarball ==="
//                 tar -czf /output/${artifact_name} package.json node_modules dist ecosystem.config.js 2>/dev/null || tar -czf /output/${artifact_name} package.json node_modules dist
//             """
//         } else {
//             build_cmd = """
//                 set -e
//                 echo "=== Running NPM Install ==="
//                 npm ci
                
//                 echo "=== Running NPM Build ==="
//                 npm run build --if-present
                
//                 echo "=== Pruning DevDependencies for Production ==="
//                 npm prune --production
                
//                 echo "=== Creating Release Tarball ==="
//                 tar -czf /output/${artifact_name} package.json node_modules dist ecosystem.config.js 2>/dev/null || tar -czf /output/${artifact_name} package.json node_modules dist
//             """
//         }

//         // Run isolated build and package inside Docker node container
//         sh """
//             docker run --rm \\
//                 -v ${project_path}:/app \\
//                 -v ${artifact_dir}:/output \\
//                 -w /app \\
//                 node:${node_version} sh -c '${build_cmd}'
//         """

//         // Fix permissions so Jenkins workspace cleanup can manage generated files
//         sh "sudo chown -R \$(id -u):\$(id -g) ${WORKSPACE} ${artifact_dir} 2>/dev/null || true"

//         env.GENERATED_ARTIFACT_PATH = "${artifact_dir}/${artifact_name}"
//         env.GENERATED_ARTIFACT_NAME = "${artifact_name}"

//         logger.logger('msg':"Artifact successfully built & packed: ${env.GENERATED_ARTIFACT_PATH}", 'level':'INFO')
//     }
// }

//-----------------------


package opstree.common

import opstree.common.*

def build_factory(Map step_params) {
    def logger = new logger()

    if (step_params.perform_code_build == 'true' || step_params.perform_code_build == true) {
        build_and_package_node(step_params)
    } else {
        logger.logger(
            'msg': 'No valid option selected for Building Node.js Artifact. Skipping step.',
            'level': 'WARN'
        )
    }
}

def build_and_package_node(Map step_params) {
    def logger = new logger()
    def parser = new parser()

    logger.logger(
        'msg': 'Performing Node.js Build & Tarball Packaging Step',
        'level': 'INFO'
    )

    def repo_url         = "${step_params.repo_url}"
    def source_code_path = "${step_params.source_code_path ?: ''}"
    def node_version     = step_params.node_version ?: '18'
    def package_manager  = step_params.package_manager ?: 'yarn'
    def app_name         = step_params.app_name ?: 'node-app'

    /*
     * Flexible application packaging configuration
     *
     * Examples:
     *
     * Normal Node/TypeScript backend:
     * build_output_path = 'dist'
     *
     * Next.js:
     * build_output_path = '.next'
     *
     * React/Vite:
     * build_output_path = 'dist'
     *
     * Create React App:
     * build_output_path = 'build'
     */
    def build_output_path = step_params.build_output_path ?: 'dist'

    /*
     * Additional files/directories that need to be packaged.
     *
     * Example Next.js:
     * additional_artifact_paths = ['public', 'next.config.ts']
     */
    def additional_artifact_paths =
        step_params.additional_artifact_paths ?: []

    /*
     * Whether node_modules should be included in artifact.
     */
    def include_node_modules =
        step_params.containsKey('include_node_modules') ?
            step_params.include_node_modules :
            true

    /*
     * Whether production dependency pruning should happen.
     */
    def prune_dev_dependencies =
        step_params.containsKey('prune_dev_dependencies') ?
            step_params.prune_dev_dependencies :
            true

    def repo_dir = parser.fetch_git_repo_name(
        'repo_url': "${repo_url}"
    )

    def project_path =
        "${WORKSPACE}/${repo_dir}${source_code_path}"

    def commit_tag = sh(
        script: """
            git config --global --add safe.directory ${WORKSPACE}/${repo_dir}
            cd ${WORKSPACE}/${repo_dir}
            git rev-parse --short HEAD
        """,
        returnStdout: true
    ).trim()

    def artifact_name =
        "${app_name}-${commit_tag}.tar.gz"

    def artifact_dir =
        "${WORKSPACE}/artifact"

    /*
     * Build the list of files that will go inside the tarball.
     */
    def artifact_paths = [
        'package.json',
        build_output_path
    ]

    if (include_node_modules) {
        artifact_paths.add('node_modules')
    }

    additional_artifact_paths.each { path ->
        artifact_paths.add(path)
    }

    def artifact_paths_string =
        artifact_paths.join(' ')

    dir(project_path) {

        sh "mkdir -p ${artifact_dir}"

        def install_command
        def build_command
        def prune_command = ''

        if (package_manager.toLowerCase() == 'yarn') {

            install_command =
                'yarn install --frozen-lockfile'

            build_command =
                step_params.build_command ?: 'yarn build'

            if (prune_dev_dependencies) {
                prune_command =
                    'yarn install --production --ignore-scripts --prefer-offline'
            }

        } else if (package_manager.toLowerCase() == 'npm') {

            install_command =
                'npm ci'

            build_command =
                step_params.build_command ?: 'npm run build --if-present'

            if (prune_dev_dependencies) {
                prune_command =
                    'npm prune --production'
            }

        } else {
            error "Unsupported package manager: ${package_manager}"
        }

        def build_cmd = """
            set -e

            echo "=== Installing Dependencies ==="
            ${install_command}

            echo "=== Running Build ==="
            ${build_command}

            if [ ! -e "${build_output_path}" ]; then
                echo "ERROR: Build output path '${build_output_path}' does not exist."
                echo "Current directory contents:"
                ls -la
                exit 1
            fi

            ${prune_dev_dependencies ? """
            echo "=== Pruning DevDependencies for Production ==="
            ${prune_command}
            """ : """
            echo "=== Skipping DevDependency Pruning ==="
            """}

            echo "=== Creating Release Tarball ==="
            echo "Packaging: ${artifact_paths_string}"

            tar -czf /output/${artifact_name} ${artifact_paths_string}

            echo "=== Artifact Created Successfully ==="
            ls -lh /output/${artifact_name}
        """

        sh """
            docker run --rm \\
                -v ${project_path}:/app \\
                -v ${artifact_dir}:/output \\
                -w /app \\
                node:${node_version} \\
                sh -c '${build_cmd}'
        """

        sh """
            sudo chown -R \$(id -u):\$(id -g) \
                ${WORKSPACE} \
                ${artifact_dir} 2>/dev/null || true
        """

        env.GENERATED_ARTIFACT_PATH =
            "${artifact_dir}/${artifact_name}"

        env.GENERATED_ARTIFACT_NAME =
            "${artifact_name}"

        logger.logger(
            'msg': "Artifact successfully built & packed: ${env.GENERATED_ARTIFACT_PATH}",
            'level': 'INFO'
        )
    }
}
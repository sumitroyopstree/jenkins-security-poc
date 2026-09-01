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


// package opstree.common

// import opstree.common.*

// def build_factory(Map step_params) {
//     def logger = new logger()

//     if (step_params.perform_code_build == 'true' || step_params.perform_code_build == true) {
//         build_and_package_node(step_params)
//     } else {
//         logger.logger(
//             'msg': 'No valid option selected for Building Node.js Artifact. Skipping step.',
//             'level': 'WARN'
//         )
//     }
// }

// def build_and_package_node(Map step_params) {
//     def logger = new logger()
//     def parser = new parser()

//     logger.logger(
//         'msg': 'Performing Node.js Build & Tarball Packaging Step',
//         'level': 'INFO'
//     )

//     def repo_url         = "${step_params.repo_url}"
//     def source_code_path = "${step_params.source_code_path ?: ''}"
//     def node_version     = step_params.node_version ?: '18'
//     def package_manager  = step_params.package_manager ?: 'yarn'
//     def app_name         = step_params.app_name ?: 'node-app'

//     /*
//      * Flexible application packaging configuration
//      *
//      * Examples:
//      *
//      * Normal Node/TypeScript backend:
//      * build_output_path = 'dist'
//      *
//      * Next.js:
//      * build_output_path = '.next'
//      *
//      * React/Vite:
//      * build_output_path = 'dist'
//      *
//      * Create React App:
//      * build_output_path = 'build'
//      */
//     def build_output_path = step_params.build_output_path ?: 'dist'

//     /*
//      * Additional files/directories that need to be packaged.
//      *
//      * Example Next.js:
//      * additional_artifact_paths = ['public', 'next.config.ts']
//      */
//     def additional_artifact_paths =
//         step_params.additional_artifact_paths ?: []

//     /*
//      * Whether node_modules should be included in artifact.
//      */
//     def include_node_modules =
//         step_params.containsKey('include_node_modules') ?
//             step_params.include_node_modules :
//             true

//     /*
//      * Whether production dependency pruning should happen.
//      */
//     def prune_dev_dependencies =
//         step_params.containsKey('prune_dev_dependencies') ?
//             step_params.prune_dev_dependencies :
//             true

//     def repo_dir = parser.fetch_git_repo_name(
//         'repo_url': "${repo_url}"
//     )

//     def project_path =
//         "${WORKSPACE}/${repo_dir}${source_code_path}"

//     def commit_tag = sh(
//         script: """
//             git config --global --add safe.directory ${WORKSPACE}/${repo_dir}
//             cd ${WORKSPACE}/${repo_dir}
//             git rev-parse --short HEAD
//         """,
//         returnStdout: true
//     ).trim()

//     def artifact_name =
//         "${app_name}-${commit_tag}.tar.gz"

//     def artifact_dir =
//         "${WORKSPACE}/artifact"

//     /*
//      * Build the list of files that will go inside the tarball.
//      */
//     def artifact_paths = [
//         'package.json',
//         build_output_path
//     ]

//     if (include_node_modules) {
//         artifact_paths.add('node_modules')
//     }

//     additional_artifact_paths.each { path ->
//         artifact_paths.add(path)
//     }

//     def artifact_paths_string =
//         artifact_paths.join(' ')

//     dir(project_path) {

//         sh "mkdir -p ${artifact_dir}"

//         def install_command
//         def build_command
//         def prune_command = ''

//         if (package_manager.toLowerCase() == 'yarn') {

//             install_command =
//                 'yarn install --frozen-lockfile'

//             build_command =
//                 step_params.build_command ?: 'yarn build'

//             if (prune_dev_dependencies) {
//                 prune_command =
//                     'yarn install --production --ignore-scripts --prefer-offline'
//             }

//         } else if (package_manager.toLowerCase() == 'npm') {

//             install_command =
//                 'npm ci'

//             build_command =
//                 step_params.build_command ?: 'npm run build --if-present'

//             if (prune_dev_dependencies) {
//                 prune_command =
//                     'npm prune --production'
//             }

//         } else {
//             error "Unsupported package manager: ${package_manager}"
//         }

//         def build_cmd = """
//             set -e

//             echo "=== Installing Dependencies ==="
//             ${install_command}

//             echo "=== Running Build ==="
//             ${build_command}

//             if [ ! -e "${build_output_path}" ]; then
//                 echo "ERROR: Build output path '${build_output_path}' does not exist."
//                 echo "Current directory contents:"
//                 ls -la
//                 exit 1
//             fi

//             ${prune_dev_dependencies ? """
//             echo "=== Pruning DevDependencies for Production ==="
//             ${prune_command}
//             """ : """
//             echo "=== Skipping DevDependency Pruning ==="
//             """}

//             echo "=== Creating Release Tarball ==="
//             echo "Packaging: ${artifact_paths_string}"

//             tar -czf /output/${artifact_name} ${artifact_paths_string}

//             echo "=== Artifact Created Successfully ==="
//             ls -lh /output/${artifact_name}
//         """

//         sh """
//             docker run --rm \\
//                 -v ${project_path}:/app \\
//                 -v ${artifact_dir}:/output \\
//                 -w /app \\
//                 node:${node_version} \\
//                 sh -c '${build_cmd}'
//         """

//         sh """
//             sudo chown -R \$(id -u):\$(id -g) \
//                 ${WORKSPACE} \
//                 ${artifact_dir} 2>/dev/null || true
//         """

//         env.GENERATED_ARTIFACT_PATH =
//             "${artifact_dir}/${artifact_name}"

//         env.GENERATED_ARTIFACT_NAME =
//             "${artifact_name}"

//         logger.logger(
//             'msg': "Artifact successfully built & packed: ${env.GENERATED_ARTIFACT_PATH}",
//             'level': 'INFO'
//         )
//     }
// }

//---------------------


// package opstree.common

// import opstree.common.*

// def build_factory(Map step_params) {
//     def logger = new logger()

//     if (step_params.perform_code_build == 'true' || step_params.perform_code_build == true) {
//         build_and_package_node(step_params)
//     } else {
//         logger.logger(
//             'msg': 'No valid option selected for Building Node.js Artifact. Skipping step.',
//             'level': 'WARN'
//         )
//     }
// }


// def build_and_package_node(Map step_params) {

//     def logger = new logger()
//     def parser = new parser()

//     logger.logger(
//         'msg': 'Performing Node.js Build & Tarball Packaging Step',
//         'level': 'INFO'
//     )

//     // =========================================================
//     // BASIC CONFIGURATION
//     // =========================================================

//     def repo_url         = "${step_params.repo_url}"
//     def source_code_path = "${step_params.source_code_path ?: ''}"

//     def node_version =
//         step_params.node_version ?: '18'

//     def package_manager =
//         step_params.package_manager ?: 'yarn'

//     def app_name =
//         step_params.app_name ?: 'node-app'


//     // =========================================================
//     // BUILD OUTPUT
//     //
//     // Default = dist
//     //
//     // Backend / TypeScript -> dist
//     // React/Vite          -> dist
//     // CRA                 -> build
//     // Next.js             -> .next
//     // =========================================================

//     def build_output_path =
//         step_params.build_output_path ?: 'dist'


//     // =========================================================
//     // BUILD COMMAND
//     //
//     // Prevent null from becoming an actual shell command.
//     // =========================================================

//     def build_command = step_params.build_command

//     if (
//         build_command == null ||
//         build_command.toString().trim() == '' ||
//         build_command.toString().trim().equalsIgnoreCase('null')
//     ) {
//         if (package_manager.toLowerCase() == 'yarn') {
//             build_command = 'yarn build'
//         } else {
//             build_command = 'npm run build --if-present'
//         }
//     } else {
//         build_command = build_command.toString().trim()
//     }


//     // =========================================================
//     // INCLUDE NODE_MODULES
//     // Default = true
//     // =========================================================

//     def include_node_modules = true

//     if (step_params.containsKey('include_node_modules')) {
//         include_node_modules =
//             step_params.include_node_modules == true ||
//             step_params.include_node_modules == 'true'
//     }


//     // =========================================================
//     // PRUNE DEV DEPENDENCIES
//     // Default = true
//     // =========================================================

//     def prune_dev_dependencies = true

//     if (step_params.containsKey('prune_dev_dependencies')) {
//         prune_dev_dependencies =
//             step_params.prune_dev_dependencies == true ||
//             step_params.prune_dev_dependencies == 'true'
//     }


//     // =========================================================
//     // EXTRA ARTIFACT FILES / DIRECTORIES
//     //
//     // Example:
//     //
//     // additional_artifact_paths: [
//     //     'public',
//     //     'next.config.ts'
//     // ]
//     //
//     // Missing additional files are skipped instead of failing.
//     // =========================================================

//     def additional_artifact_paths = []

//     if (step_params.additional_artifact_paths instanceof Collection) {
//         additional_artifact_paths =
//             step_params.additional_artifact_paths.collect {
//                 it.toString().trim()
//             }.findAll {
//                 it
//             }
//     }


//     // =========================================================
//     // REPOSITORY INFORMATION
//     // =========================================================

//     def repo_dir = parser.fetch_git_repo_name(
//         'repo_url': "${repo_url}"
//     )

//     def project_path =
//         "${WORKSPACE}/${repo_dir}${source_code_path}"


//     // =========================================================
//     // GET GIT COMMIT HASH
//     // =========================================================

//     def commit_tag = sh(
//         script: """
//             git config --global --add safe.directory ${WORKSPACE}/${repo_dir}
//             cd ${WORKSPACE}/${repo_dir}
//             git rev-parse --short HEAD
//         """,
//         returnStdout: true
//     ).trim()


//     // =========================================================
//     // ARTIFACT NAME / LOCATION
//     // =========================================================

//     def artifact_name =
//         "${app_name}-${commit_tag}.tar.gz"

//     def artifact_dir =
//         "${WORKSPACE}/artifact"


//     // Convert additional paths to a space separated string
//     def additional_paths_string =
//         additional_artifact_paths.join(' ')


//     // =========================================================
//     // PACKAGE MANAGER COMMANDS
//     // =========================================================

//     def install_command = ''
//     def prune_command   = ''


//     if (package_manager.toLowerCase() == 'yarn') {

//         install_command =
//             'yarn install --frozen-lockfile'

//         if (prune_dev_dependencies) {
//             prune_command =
//                 'yarn install --production --ignore-scripts --prefer-offline'
//         }

//     } else if (package_manager.toLowerCase() == 'npm') {

//         install_command =
//             'npm ci'

//         if (prune_dev_dependencies) {
//             prune_command =
//                 'npm prune --production'
//         }

//     } else {

//         error(
//             "Unsupported package manager: ${package_manager}. " +
//             "Supported values are yarn and npm."
//         )
//     }


//     // =========================================================
//     // BUILD APPLICATION
//     // =========================================================

//     dir(project_path) {

//         sh "mkdir -p ${artifact_dir}"


//         // =====================================================
//         // Generate build script
//         //
//         // Using a script file instead of nested sh -c quoting
//         // makes custom build commands safer.
//         // =====================================================

//         def build_script = """
//             #!/bin/sh

//             set -e

//             echo "========================================"
//             echo "Node.js Artifact Build"
//             echo "========================================"

//             echo "Node Version:"
//             node --version

//             echo "Package Manager:"
//             ${package_manager} --version

//             echo "Application Name:"
//             echo "${app_name}"

//             echo "Build Output Path:"
//             echo "${build_output_path}"

//             echo "========================================"
//             echo "Installing Dependencies"
//             echo "========================================"

//             ${install_command}


//             echo "========================================"
//             echo "Running Build"
//             echo "========================================"

//             echo "Build Command: ${build_command}"

//             ${build_command}


//             echo "========================================"
//             echo "Validating Build Output"
//             echo "========================================"

//             if [ ! -e "${build_output_path}" ]; then

//                 echo ""
//                 echo "ERROR: Build output path '${build_output_path}' does not exist."
//                 echo ""
//                 echo "Current project directory:"
//                 pwd

//                 echo ""
//                 echo "Directory contents:"
//                 ls -la

//                 exit 1
//             fi

//             echo "Build output '${build_output_path}' found successfully."


//             ${
//                 prune_dev_dependencies
//                 ? """
//             echo "========================================"
//             echo "Pruning DevDependencies"
//             echo "========================================"

//             ${prune_command}
//             """
//                 : """
//             echo "========================================"
//             echo "Skipping DevDependency Pruning"
//             echo "========================================"
//             """
//             }


//             echo "========================================"
//             echo "Preparing Artifact Files"
//             echo "========================================"

//             ARTIFACT_ITEMS="package.json ${build_output_path}"


//             ${
//                 include_node_modules
//                 ? """
//             if [ -d "node_modules" ]; then
//                 ARTIFACT_ITEMS="\\\$ARTIFACT_ITEMS node_modules"
//             else
//                 echo "WARNING: node_modules directory not found."
//             fi
//             """
//                 : """
//             echo "node_modules will not be included in artifact."
//             """
//             }


//             # Add optional application-specific files/directories.
//             for item in ${additional_paths_string}; do

//                 if [ -e "\\\$item" ]; then

//                     echo "Adding optional artifact path: \\\$item"

//                     ARTIFACT_ITEMS="\\\$ARTIFACT_ITEMS \\\$item"

//                 else

//                     echo "WARNING: Optional artifact path '\\\$item' not found. Skipping."

//                 fi

//             done


//             echo ""
//             echo "Files/directories being packaged:"
//             echo "\\\$ARTIFACT_ITEMS"


//             echo "========================================"
//             echo "Creating Release Tarball"
//             echo "========================================"

//             tar -czf /output/${artifact_name} \\\$ARTIFACT_ITEMS


//             echo "========================================"
//             echo "Artifact Created Successfully"
//             echo "========================================"

//             ls -lh /output/${artifact_name}
//         """.stripIndent()


//         // Write build script into artifact directory.
//         // artifact directory is mounted as /output inside Docker.
//         def build_script_path =
//             "${artifact_dir}/node_build.sh"

//         writeFile(
//             file: build_script_path,
//             text: build_script
//         )

//         sh "chmod +x ${build_script_path}"


//         // =====================================================
//         // RUN BUILD INSIDE NODE DOCKER CONTAINER
//         // =====================================================

//         sh """
//             docker run --rm \\
//                 -v ${project_path}:/app \\
//                 -v ${artifact_dir}:/output \\
//                 -w /app \\
//                 node:${node_version} \\
//                 sh /output/node_build.sh
//         """


//         // Remove temporary build script
//         sh """
//             rm -f ${build_script_path} || true
//         """


//         // =====================================================
//         // FIX FILE OWNERSHIP
//         // =====================================================

//         sh """
//             sudo chown -R \$(id -u):\$(id -g) \
//                 ${WORKSPACE} \
//                 ${artifact_dir} \
//                 2>/dev/null || true
//         """


//         // =====================================================
//         // EXPORT GENERATED ARTIFACT DETAILS
//         // =====================================================

//         env.GENERATED_ARTIFACT_PATH =
//             "${artifact_dir}/${artifact_name}"

//         env.GENERATED_ARTIFACT_NAME =
//             "${artifact_name}"


//         logger.logger(
//             'msg': "Artifact successfully built & packed: ${env.GENERATED_ARTIFACT_PATH}",
//             'level': 'INFO'
//         )
//     }
// }

//-----------------


// package opstree.common

// import opstree.common.*

// def build_factory(Map step_params) {
//     def logger = new logger()

//     if (step_params.perform_code_build == 'true' || step_params.perform_code_build == true) {
//         build_and_package_node(step_params)
//     } else {
//         logger.logger(
//             'msg': 'No valid option selected for Building Node.js Artifact. Skipping step.',
//             'level': 'WARN'
//         )
//     }
// }

// def build_and_package_node(Map step_params) {

//     def logger = new logger()
//     def parser = new parser()

//     logger.logger(
//         'msg': 'Performing Node.js Build & Tarball Packaging Step',
//         'level': 'INFO'
//     )

//     // =========================================================
//     // BASIC CONFIGURATION
//     // =========================================================

//     def repo_url         = "${step_params.repo_url}"
//     def source_code_path = "${step_params.source_code_path ?: ''}"
//     def node_version     = step_params.node_version ?: '18'
//     def package_manager  = step_params.package_manager ?: 'yarn'
//     def app_name         = step_params.app_name ?: 'node-app'

//     // Default build output for backend / TypeScript / Vite
//     // Override with '.next' for Next.js
//     // Override with 'build' for CRA
//     def build_output_path = step_params.build_output_path ?: 'dist'

//     // =========================================================
//     // BUILD COMMAND
//     // =========================================================

//     def build_command = step_params.build_command

//     if (
//         build_command == null ||
//         build_command.toString().trim() == '' ||
//         build_command.toString().trim().equalsIgnoreCase('null')
//     ) {
//         if (package_manager.toLowerCase() == 'yarn') {
//             build_command = 'yarn build'
//         } else {
//             build_command = 'npm run build --if-present'
//         }
//     } else {
//         build_command = build_command.toString().trim()
//     }

//     // =========================================================
//     // INCLUDE NODE_MODULES
//     // Default = true
//     // =========================================================

//     def include_node_modules = true

//     if (step_params.containsKey('include_node_modules')) {
//         include_node_modules =
//             step_params.include_node_modules == true ||
//             step_params.include_node_modules == 'true'
//     }

//     // =========================================================
//     // PRUNE DEV DEPENDENCIES
//     // Default = true
//     // =========================================================

//     def prune_dev_dependencies = true

//     if (step_params.containsKey('prune_dev_dependencies')) {
//         prune_dev_dependencies =
//             step_params.prune_dev_dependencies == true ||
//             step_params.prune_dev_dependencies == 'true'
//     }

//     // =========================================================
//     // ADDITIONAL ARTIFACT FILES / DIRECTORIES
//     // =========================================================

//     def additional_artifact_paths = []

//     if (step_params.additional_artifact_paths instanceof Collection) {
//         additional_artifact_paths =
//             step_params.additional_artifact_paths.collect {
//                 it.toString().trim()
//             }.findAll {
//                 it
//             }
//     }

//     // =========================================================
//     // REPOSITORY INFORMATION
//     // =========================================================

//     def repo_dir = parser.fetch_git_repo_name(
//         'repo_url': "${repo_url}"
//     )

//     def project_path =
//         "${WORKSPACE}/${repo_dir}${source_code_path}"

//     // =========================================================
//     // GET GIT COMMIT HASH
//     // =========================================================

//     def commit_tag = sh(
//         script: """
//             git config --global --add safe.directory ${WORKSPACE}/${repo_dir}
//             cd ${WORKSPACE}/${repo_dir}
//             git rev-parse --short HEAD
//         """,
//         returnStdout: true
//     ).trim()

//     // =========================================================
//     // ARTIFACT NAME / LOCATION
//     // =========================================================

//     def artifact_name =
//         "${app_name}-${commit_tag}.tar.gz"

//     def artifact_dir =
//         "${WORKSPACE}/artifact"

//     def additional_paths_string =
//         additional_artifact_paths.join(' ')

//     // =========================================================
//     // PACKAGE MANAGER COMMANDS
//     // =========================================================

//     def install_command = ''
//     def prune_command   = ''

//     if (package_manager.toLowerCase() == 'yarn') {

//         install_command =
//             'yarn install --frozen-lockfile'

//         if (prune_dev_dependencies) {
//             prune_command =
//                 'yarn install --production --ignore-scripts --prefer-offline'
//         }

//     } else if (package_manager.toLowerCase() == 'npm') {

//         install_command =
//             'npm ci'

//         if (prune_dev_dependencies) {
//             prune_command =
//                 'npm prune --production'
//         }

//     } else {

//         error(
//             "Unsupported package manager: ${package_manager}. " +
//             "Supported values are yarn and npm."
//         )
//     }

//     // =========================================================
//     // BUILD APPLICATION
//     // =========================================================

//     dir(project_path) {

//         sh "mkdir -p ${artifact_dir}"

//         def build_script = """
//             #!/bin/sh

//             set -e

//             echo "========================================"
//             echo "Node.js Artifact Build"
//             echo "========================================"

//             echo "Node Version:"
//             node --version

//             echo "Package Manager:"
//             ${package_manager} --version

//             echo "Application Name:"
//             echo "${app_name}"

//             echo "Build Output Path:"
//             echo "${build_output_path}"

//             echo "========================================"
//             echo "Installing Dependencies"
//             echo "========================================"

//             ${install_command}

//             echo "========================================"
//             echo "Running Build"
//             echo "========================================"

//             echo "Build Command: ${build_command}"

//             ${build_command}

//             echo "========================================"
//             echo "Validating Build Output"
//             echo "========================================"

//             if [ ! -e "${build_output_path}" ]; then

//                 echo ""
//                 echo "ERROR: Build output path '${build_output_path}' does not exist."
//                 echo ""
//                 echo "Current project directory:"
//                 pwd

//                 echo ""
//                 echo "Directory contents:"
//                 ls -la

//                 exit 1
//             fi

//             echo "Build output '${build_output_path}' found successfully."

//             ${
//                 prune_dev_dependencies
//                 ? """
//             echo "========================================"
//             echo "Pruning DevDependencies"
//             echo "========================================"

//             ${prune_command}
//             """
//                 : """
//             echo "========================================"
//             echo "Skipping DevDependency Pruning"
//             echo "========================================"
//             """
//             }

//             echo "========================================"
//             echo "Preparing Artifact Files"
//             echo "========================================"

//             ARTIFACT_ITEMS="package.json ${build_output_path}"

//             ${
//                 include_node_modules
//                 ? """
//             if [ -d "node_modules" ]; then
//                 ARTIFACT_ITEMS="\$ARTIFACT_ITEMS node_modules"
//             else
//                 echo "WARNING: node_modules directory not found."
//             fi
//             """
//                 : """
//             echo "node_modules will not be included in artifact."
//             """
//             }

//             for item in ${additional_paths_string}; do

//                 if [ -e "\$item" ]; then

//                     echo "Adding optional artifact path: \$item"

//                     ARTIFACT_ITEMS="\$ARTIFACT_ITEMS \$item"

//                 else

//                     echo "WARNING: Optional artifact path '\$item' not found. Skipping."

//                 fi

//             done

//             echo ""
//             echo "Files/directories being packaged:"
//             echo "\$ARTIFACT_ITEMS"

//             echo "========================================"
//             echo "Creating Release Tarball"
//             echo "========================================"

//             tar -czf /output/${artifact_name} \$ARTIFACT_ITEMS

//             echo "========================================"
//             echo "Artifact Created Successfully"
//             echo "========================================"

//             ls -lh /output/${artifact_name}
//         """.stripIndent()

//         // =====================================================
//         // WRITE TEMP BUILD SCRIPT
//         // =====================================================

//         def build_script_path =
//             "${artifact_dir}/node_build.sh"

//         writeFile(
//             file: build_script_path,
//             text: build_script
//         )

//         sh "chmod +x ${build_script_path}"

//         // =====================================================
//         // RUN BUILD INSIDE NODE CONTAINER
//         // =====================================================

//         sh """
//             docker run --rm \\
//                 -v ${project_path}:/app \\
//                 -v ${artifact_dir}:/output \\
//                 -w /app \\
//                 node:${node_version} \\
//                 sh /output/node_build.sh
//         """

//         // =====================================================
//         // REMOVE TEMP BUILD SCRIPT
//         // =====================================================

//         sh """
//             rm -f ${build_script_path} || true
//         """

//         // =====================================================
//         // FIX PERMISSIONS
//         // =====================================================

//         sh """
//             sudo chown -R \$(id -u):\$(id -g) \
//                 ${WORKSPACE} \
//                 ${artifact_dir} \
//                 2>/dev/null || true
//         """

//         // =====================================================
//         // EXPORT ARTIFACT DETAILS
//         // =====================================================

//         env.GENERATED_ARTIFACT_PATH =
//             "${artifact_dir}/${artifact_name}"

//         env.GENERATED_ARTIFACT_NAME =
//             "${artifact_name}"

//         logger.logger(
//             'msg': "Artifact successfully built & packed: ${env.GENERATED_ARTIFACT_PATH}",
//             'level': 'INFO'
//         )
//     }
// }

//-----------------


// package opstree.common

// import opstree.common.*


// def build_factory(Map step_params) {

//     def logger = new logger()

//     if (
//         step_params.perform_code_build == true ||
//         step_params.perform_code_build?.toString()?.equalsIgnoreCase('true')
//     ) {

//         build_and_package_node(step_params)

//     } else {

//         logger.logger(
//             'msg': 'No valid option selected for Building Node.js Artifact. Skipping step.',
//             'level': 'WARN'
//         )
//     }
// }


// def build_and_package_node(Map step_params) {

//     def logger = new logger()
//     def parser = new parser()

//     logger.logger(
//         'msg': 'Performing Node.js Build & Tarball Packaging Step',
//         'level': 'INFO'
//     )


//     // =========================================================
//     // BASIC CONFIGURATION
//     // =========================================================

//     def repo_url =
//         step_params.repo_url?.toString()?.trim()

//     def source_code_path =
//         step_params.source_code_path?.toString()?.trim() ?: ''

//     def node_version =
//         step_params.node_version?.toString()?.trim() ?: '18'

//     def package_manager =
//         step_params.package_manager?.toString()?.trim()?.toLowerCase() ?: 'yarn'

//     def app_name =
//         step_params.app_name?.toString()?.trim() ?: 'node-app'

//     def build_output_path =
//         step_params.build_output_path?.toString()?.trim() ?: 'dist'


//     // =========================================================
//     // BASIC VALIDATION
//     // =========================================================

//     if (!repo_url) {

//         error(
//             'repo_url is required for Node.js artifact build.'
//         )
//     }


//     if (!(package_manager in ['yarn', 'npm'])) {

//         error(
//             "Unsupported package manager '${package_manager}'. " +
//             "Supported values: yarn, npm."
//         )
//     }


//     // Prevent invalid/unexpected Docker image tag values.
//     if (!(node_version ==~ /[A-Za-z0-9._-]+/)) {

//         error(
//             "Invalid Node.js version '${node_version}'."
//         )
//     }


//     // =========================================================
//     // BUILD COMMAND
//     // =========================================================

//     def build_command =
//         step_params.build_command

//     if (
//         build_command == null ||
//         build_command.toString().trim() == '' ||
//         build_command.toString().trim().equalsIgnoreCase('null')
//     ) {

//         if (package_manager == 'yarn') {

//             build_command =
//                 'yarn build'

//         } else {

//             build_command =
//                 'npm run build --if-present'
//         }

//     } else {

//         build_command =
//             build_command.toString().trim()
//     }


//     // =========================================================
//     // INCLUDE NODE_MODULES
//     //
//     // Default = true
//     // =========================================================

//     def include_node_modules = true

//     if (step_params.containsKey('include_node_modules')) {

//         include_node_modules =
//             step_params.include_node_modules == true ||
//             step_params.include_node_modules
//                 ?.toString()
//                 ?.equalsIgnoreCase('true')
//     }


//     // =========================================================
//     // PRUNE DEV DEPENDENCIES
//     //
//     // Default = true
//     // =========================================================

//     def prune_dev_dependencies = true

//     if (step_params.containsKey('prune_dev_dependencies')) {

//         prune_dev_dependencies =
//             step_params.prune_dev_dependencies == true ||
//             step_params.prune_dev_dependencies
//                 ?.toString()
//                 ?.equalsIgnoreCase('true')
//     }


//     // =========================================================
//     // ADDITIONAL ARTIFACT PATHS
//     //
//     // Example:
//     //
//     // additional_artifact_paths: [
//     //     'public',
//     //     'config',
//     //     'next.config.js'
//     // ]
//     //
//     // Missing optional files are skipped.
//     // =========================================================

//     def additional_artifact_paths = []

//     if (step_params.additional_artifact_paths instanceof Collection) {

//         additional_artifact_paths =
//             step_params.additional_artifact_paths
//                 .collect {
//                     it?.toString()?.trim()
//                 }
//                 .findAll {
//                     it
//                 }
//     }


//     // =========================================================
//     // SAFE SHELL QUOTING
//     // =========================================================

//     def shellQuote = { Object value ->

//         def text =
//             value == null ? '' : value.toString()

//         return "'" + text.replace("'", "'\"'\"'") + "'"
//     }


//     // =========================================================
//     // GET REPOSITORY DIRECTORY
//     // =========================================================

//     def repo_dir =
//         parser.fetch_git_repo_name(
//             'repo_url': repo_url
//         )


//     if (!repo_dir) {

//         error(
//             "Unable to determine repository name from ${repo_url}"
//         )
//     }


//     def project_path =
//         "${WORKSPACE}/${repo_dir}${source_code_path}"


//     // =========================================================
//     // GET COMMIT HASH
//     // =========================================================

//     def commit_tag = sh(

//         script: """
//             set -e

//             git config --global --add safe.directory \
//                 ${shellQuote("${WORKSPACE}/${repo_dir}")}

//             cd ${shellQuote("${WORKSPACE}/${repo_dir}")}

//             git rev-parse --short HEAD
//         """.stripIndent(),

//         returnStdout: true

//     ).trim()


//     if (!commit_tag) {

//         error(
//             'Could not determine Git commit hash.'
//         )
//     }


//     // =========================================================
//     // ARTIFACT NAME
//     // =========================================================

//     def safe_app_name =
//         app_name.replaceAll(
//             /[^A-Za-z0-9._-]+/,
//             '-'
//         )


//     safe_app_name =
//         safe_app_name.replaceAll(
//             /^-+|-+$/,
//             ''
//         )


//     if (!safe_app_name) {

//         safe_app_name =
//             'node-app'
//     }


//     def artifact_name =
//         "${safe_app_name}-${commit_tag}.tar.gz"


//     def artifact_dir =
//         "${WORKSPACE}/artifact"


//     def artifact_path =
//         "${artifact_dir}/${artifact_name}"


//     // =========================================================
//     // PACKAGE MANAGER COMMANDS
//     // =========================================================

//     def install_command = ''
//     def prune_command = ''


//     if (package_manager == 'yarn') {

//         install_command =
//             'yarn install' // --frozen-lockfile commented for testing


//         if (prune_dev_dependencies) {

//             prune_command =
//                 'yarn install --development=true --ignore-scripts --prefer-offline' // --frozen-lockfile commented for testing
//         }

//     } else {

//         install_command =
//             'npm ci'


//         if (prune_dev_dependencies) {

//             prune_command =
//                 'npm prune --omit=dev'
//         }
//     }


//     // =========================================================
//     // ADDITIONAL ARTIFACT SHELL CODE
//     //
//     // IMPORTANT:
//     //
//     // We use shell positional arguments ($@).
//     //
//     // We DO NOT create:
//     //
//     // ARTIFACT_ITEMS="..."
//     //
//     // This avoids the exact escaping bug that caused:
//     //
//     // tar: $ARTIFACT_ITEMS: Cannot stat
//     // =========================================================

//     def additional_artifact_script =
//         new StringBuilder()


//     additional_artifact_paths.each { artifact_item ->

//         def quoted_item =
//             shellQuote(artifact_item)


//         additional_artifact_script.append(
//             """

// if [ -e ${quoted_item} ]; then

//     echo "Adding optional artifact path:"
//     printf ' - %s\\n' ${quoted_item}

//     set -- "${'$'}@" ${quoted_item}

// else

//     echo "WARNING: Optional artifact path not found. Skipping:"
//     printf ' - %s\\n' ${quoted_item}

// fi

// """
//         )
//     }


//     // =========================================================
//     // BUILD
//     // =========================================================

//     dir(project_path) {


//         // =====================================================
//         // CREATE ARTIFACT DIRECTORY
//         // =====================================================

//         sh """
//             set -e

//             mkdir -p ${shellQuote(artifact_dir)}
//         """.stripIndent()


//         // =====================================================
//         // GENERATE BUILD SCRIPT
//         // =====================================================

//         def build_script = """#!/bin/sh

// set -eu


// echo "========================================"
// echo "Node.js Artifact Build"
// echo "========================================"

// echo "Working Directory:"
// pwd

// echo ""

// echo "Node Version:"
// node --version

// echo ""

// echo "Package Manager:"
// ${package_manager} --version

// echo ""

// echo "Application Name:"
// printf '%s\\n' ${shellQuote(app_name)}

// echo ""

// echo "Build Output Path:"
// printf '%s\\n' ${shellQuote(build_output_path)}


// echo ""
// echo "========================================"
// echo "Validating Node.js Project"
// echo "========================================"


// if [ ! -f "package.json" ]; then

//     echo ""
//     echo "ERROR: package.json was not found."

//     echo ""
//     echo "Current working directory:"
//     pwd

//     echo ""
//     echo "Directory contents:"
//     ls -la

//     exit 1

// fi


// echo ""
// echo "========================================"
// echo "Installing Dependencies"
// echo "========================================"

// ${install_command}


// echo ""
// echo "========================================"
// echo "Running Build"
// echo "========================================"

// echo "Build Command:"
// printf '%s\\n' ${shellQuote(build_command)}

// ${build_command}


// echo ""
// echo "========================================"
// echo "Validating Build Output"
// echo "========================================"


// if [ ! -e ${shellQuote(build_output_path)} ]; then

//     echo ""
//     echo "ERROR: Expected build output was not created."

//     echo "Expected:"
//     printf ' - %s\\n' ${shellQuote(build_output_path)}

//     echo ""
//     echo "Current directory:"
//     pwd

//     echo ""
//     echo "Directory contents:"
//     ls -la

//     exit 1

// fi


// echo "Build output found:"
// printf ' - %s\\n' ${shellQuote(build_output_path)}


// ${
//     prune_dev_dependencies
//         ? """

// echo ""
// echo "========================================"
// echo "Pruning DevDependencies"
// echo "========================================"

// ${prune_command}

// """
//         : """

// echo ""
// echo "========================================"
// echo "Skipping DevDependency Pruning"
// echo "========================================"

// """
// }


// echo ""
// echo "========================================"
// echo "Preparing Artifact"
// echo "========================================"


// # =========================================================
// # IMPORTANT
// #
// # Use shell positional parameters instead of constructing
// # a space-separated ARTIFACT_ITEMS variable.
// #
// # Initial artifact contents:
// #
// #   package.json
// #   build output
// # =========================================================

// set -- \
//     "package.json" \
//     ${shellQuote(build_output_path)}


// ${
//     include_node_modules
//         ? """

// if [ -d "node_modules" ]; then

//     echo "Adding node_modules"

//     set -- "${'$'}@" "node_modules"

// else

//     echo "WARNING: node_modules directory does not exist."

// fi

// """
//         : """

// echo "node_modules will NOT be included in artifact."

// """
// }


// ${additional_artifact_script.toString()}


// echo ""
// echo "Files/directories being packaged:"
// echo ""


// for item in "${'$'}@"; do

//     printf ' - %s\\n' "${'$'}item"

// done


// echo ""
// echo "========================================"
// echo "Validating Artifact Inputs"
// echo "========================================"


// for item in "${'$'}@"; do

//     if [ ! -e "${'$'}item" ]; then

//         echo ""
//         echo "ERROR: Artifact input does not exist:"

//         printf ' - %s\\n' "${'$'}item"

//         exit 1

//     fi

// done


// echo "All artifact inputs are valid."


// echo ""
// echo "========================================"
// echo "Removing Previous Artifact"
// echo "========================================"


// rm -f ${shellQuote("/output/${artifact_name}")}


// echo ""
// echo "========================================"
// echo "Creating Release Tarball"
// echo "========================================"


// tar \
//     -czf ${shellQuote("/output/${artifact_name}")} \
//     "${'$'}@"


// echo ""
// echo "========================================"
// echo "Validating Generated Artifact"
// echo "========================================"


// if [ ! -f ${shellQuote("/output/${artifact_name}")} ]; then

//     echo ""
//     echo "ERROR: Artifact file was not generated."

//     exit 1

// fi


// if [ ! -s ${shellQuote("/output/${artifact_name}")} ]; then

//     echo ""
//     echo "ERROR: Generated artifact is empty."

//     rm -f ${shellQuote("/output/${artifact_name}")}

//     exit 1

// fi


// echo ""
// echo "========================================"
// echo "Artifact Created Successfully"
// echo "========================================"

// ls -lh ${shellQuote("/output/${artifact_name}")}


// echo ""
// echo "========================================"
// echo "Validating Tarball"
// echo "========================================"


// if ! tar -tzf ${shellQuote("/output/${artifact_name}")} >/dev/null; then

//     echo ""
//     echo "ERROR: Generated tarball is invalid or corrupted."

//     rm -f ${shellQuote("/output/${artifact_name}")}

//     exit 1

// fi


// echo "Tarball validation successful."


// echo ""
// echo "========================================"
// echo "Artifact Contents"
// echo "========================================"


// tar -tzf ${shellQuote("/output/${artifact_name}")} | head -100 || true


// echo ""
// echo "========================================"
// echo "Node.js Artifact Build Completed"
// echo "========================================"

// """.stripIndent()


//         // =====================================================
//         // WRITE BUILD SCRIPT
//         // =====================================================

//         def build_script_path =
//             "${artifact_dir}/node_build.sh"


//         writeFile(
//             file: build_script_path,
//             text: build_script
//         )


//         sh """
//             set -e

//             chmod +x ${shellQuote(build_script_path)}
//         """.stripIndent()


//         // =====================================================
//         // RUN BUILD IN NODE DOCKER CONTAINER
//         // =====================================================

//         try {

//             sh """
//                 set -e

//                 docker run --rm \\
//                     -v ${shellQuote("${project_path}:/app")} \\
//                     -v ${shellQuote("${artifact_dir}:/output")} \\
//                     -w /app \\
//                     ${shellQuote("node:${node_version}")} \\
//                     sh /output/node_build.sh
//             """.stripIndent()

//         } finally {


//             // =================================================
//             // FIX FILE OWNERSHIP
//             //
//             // This runs even when Docker/build/tar fails.
//             //
//             // Your previous implementation only reached its
//             // chown after successful Docker execution.
//             // =================================================

//             sh """
//                 sudo chown -R \
//                     \$(id -u):\$(id -g) \
//                     ${shellQuote(project_path)} \
//                     ${shellQuote(artifact_dir)} \
//                     2>/dev/null || true
//             """.stripIndent()


//             // =================================================
//             // CLEAN TEMPORARY SCRIPT
//             // =================================================

//             sh """
//                 rm -f ${shellQuote(build_script_path)} || true
//             """.stripIndent()
//         }


//         // =====================================================
//         // FINAL JENKINS-SIDE VALIDATION
//         // =====================================================

//         if (!fileExists(artifact_path)) {

//             error(
//                 "Build command completed, but generated artifact " +
//                 "was not found: ${artifact_path}"
//             )
//         }


//         // =====================================================
//         // EXPORT ARTIFACT INFORMATION
//         // =====================================================

//         env.GENERATED_ARTIFACT_PATH =
//             artifact_path


//         env.GENERATED_ARTIFACT_NAME =
//             artifact_name


//         logger.logger(
//             'msg': "Artifact successfully built & packed: ${artifact_path}",
//             'level': 'INFO'
//         )
//     }
// }


package opstree.common

import opstree.common.*

def build_factory(Map step_params) {
    def logger = new logger()

    if (
        step_params.perform_code_build == true ||
        step_params.perform_code_build?.toString()?.equalsIgnoreCase('true')
    ) {
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

    // =========================================================
    // BASIC CONFIGURATION
    // =========================================================
    def repo_url = step_params.repo_url?.toString()?.trim()
    def source_code_path = step_params.source_code_path?.toString()?.trim() ?: ''
    def node_version = step_params.node_version?.toString()?.trim() ?: '18'
    def package_manager = step_params.package_manager?.toString()?.trim()?.toLowerCase() ?: 'yarn'
    def app_name = step_params.app_name?.toString()?.trim() ?: 'node-app'
    def build_output_path = step_params.build_output_path?.toString()?.trim() ?: 'dist'

    // =========================================================
    // BASIC VALIDATION
    // =========================================================
    if (!repo_url) {
        error('repo_url is required for Node.js artifact build.')
    }

    if (!(package_manager in ['yarn', 'npm'])) {
        error("Unsupported package manager '${package_manager}'. Supported values: yarn, npm.")
    }

    if (!(node_version ==~ /[A-Za-z0-9._-]+/)) {
        error("Invalid Node.js version '${node_version}'.")
    }

    // =========================================================
    // BUILD COMMAND
    // =========================================================
    def build_command = step_params.build_command
    if (
        build_command == null ||
        build_command.toString().trim() == '' ||
        build_command.toString().trim().equalsIgnoreCase('null')
    ) {
        if (package_manager == 'yarn') {
            build_command = 'yarn build'
        } else {
            build_command = 'npm run build --if-present'
        }
    } else {
        build_command = build_command.toString().trim()
    }

    // =========================================================
    // INCLUDE NODE_MODULES & PRUNE DEV DEPENDENCIES
    // =========================================================
    def include_node_modules = true
    if (step_params.containsKey('include_node_modules')) {
        include_node_modules = step_params.include_node_modules == true || step_params.include_node_modules?.toString()?.equalsIgnoreCase('true')
    }

    def prune_dev_dependencies = true
    if (step_params.containsKey('prune_dev_dependencies')) {
        prune_dev_dependencies = step_params.prune_dev_dependencies == true || step_params.prune_dev_dependencies?.toString()?.equalsIgnoreCase('true')
    }

    def additional_artifact_paths = []
    if (step_params.additional_artifact_paths instanceof Collection) {
        additional_artifact_paths = step_params.additional_artifact_paths
            .collect { it?.toString()?.trim() }
            .findAll { it }
    }

    // =========================================================
    // SAFE SHELL QUOTING
    // =========================================================
    def shellQuote = { Object value ->
        def text = value == null ? '' : value.toString()
        return "'" + text.replace("'", "'\"'\"'") + "'"
    }

    // =========================================================
    // GET REPOSITORY DIRECTORY & COMMIT
    // =========================================================
    def repo_dir = parser.fetch_git_repo_name('repo_url': repo_url)
    if (!repo_dir) {
        error("Unable to determine repository name from ${repo_url}")
    }

    def project_path = "${WORKSPACE}/${repo_dir}${source_code_path}"

    def commit_tag = sh(
        script: """
            set -e
            git config --global --add safe.directory ${shellQuote("${WORKSPACE}/${repo_dir}")}
            cd ${shellQuote("${WORKSPACE}/${repo_dir}")}
            git rev-parse --short HEAD
        """.stripIndent(),
        returnStdout: true
    ).trim()

    if (!commit_tag) {
        error('Could not determine Git commit hash.')
    }

    def safe_app_name = app_name.replaceAll(/[^A-Za-z0-9._-]+/, '-').replaceAll(/^-+|-+$/, '') ?: 'node-app'
    def artifact_name = "${safe_app_name}-${commit_tag}.tar.gz"
    def artifact_dir = "${WORKSPACE}/artifact"
    def artifact_path = "${artifact_dir}/${artifact_name}"

    // =========================================================
    // PACKAGE MANAGER COMMANDS (CORRECTED)
    // =========================================================
    def install_command = ''
    def prune_command = ''

    if (package_manager == 'yarn') {
        install_command = 'yarn install'
        if (prune_dev_dependencies) {
            // Corrected: --production=true retains dependencies and strips devDependencies
            prune_command = 'yarn install --production=true --ignore-scripts --prefer-offline'
        }
    } else {
        install_command = 'npm install'
        if (prune_dev_dependencies) {
            prune_command = 'npm prune --omit=dev'
        }
    }

    def additional_artifact_script = new StringBuilder()
    additional_artifact_paths.each { artifact_item ->
        def quoted_item = shellQuote(artifact_item)
        additional_artifact_script.append("""
if [ -e ${quoted_item} ]; then
    echo "Adding optional artifact path:"
    printf ' - %s\\n' ${quoted_item}
    set -- "\$@" ${quoted_item}
else
    echo "WARNING: Optional artifact path not found. Skipping:"
    printf ' - %s\\n' ${quoted_item}
fi
""")
    }

    // =========================================================
    // BUILD AND PACKAGE
    // =========================================================
    dir(project_path) {
        sh """
            set -e
            mkdir -p ${shellQuote(artifact_dir)}
        """.stripIndent()

        def build_script = """#!/bin/sh
set -eu

echo "========================================"
echo "Node.js Artifact Build"
echo "========================================"
echo "Node Version: \$(node --version)"
echo "Package Manager: ${package_manager} \$(${package_manager} --version)"
echo "App Name: ${app_name}"
echo "Build Output: ${build_output_path}"

if [ ! -f "package.json" ]; then
    echo "ERROR: package.json was not found."
    exit 1
fi

echo "========================================"
echo "Installing All Dependencies"
echo "========================================"
${install_command}

echo "========================================"
echo "Running Build"
echo "========================================"
${build_command}

ACTUAL_OUTPUT="${build_output_path}"
if [ ! -e "$ACTUAL_OUTPUT" ]; then
    if [ -d ".next" ]; then
        echo "Auto-detected Next.js build output directory: .next"
        ACTUAL_OUTPUT=".next"
    elif [ -d "build" ]; then
        echo "Auto-detected build output directory: build"
        ACTUAL_OUTPUT="build"
    elif [ -d "dist" ]; then
        echo "Auto-detected build output directory: dist"
        ACTUAL_OUTPUT="dist"
    else
        echo "ERROR: Expected build output '${build_output_path}' was not created."
        exit 1
    fi
fi

${
    prune_dev_dependencies
        ? """
echo "========================================"
echo "Pruning DevDependencies"
echo "========================================"
${prune_command}
"""
        : """
echo "Skipping DevDependency Pruning"
"""
}

echo "========================================"
echo "Preparing Artifact Inputs"
echo "========================================"
set -- "package.json" "\$ACTUAL_OUTPUT"

${
    include_node_modules
        ? """
if [ -d "node_modules" ]; then
    echo "Adding node_modules to release package"
    set -- "\$@" "node_modules"
else
    echo "WARNING: node_modules directory does not exist."
fi
"""
        : ""
}

${additional_artifact_script.toString()}

echo "Files being packaged:"
for item in "\$@"; do
    printf ' - %s\\n' "\$item"
done

rm -f ${shellQuote("/output/${artifact_name}")}

echo "Creating Release Tarball: ${artifact_name}"
tar -czf ${shellQuote("/output/${artifact_name}")} "\$@"

if [ ! -s ${shellQuote("/output/${artifact_name}")} ]; then
    echo "ERROR: Generated artifact is missing or empty."
    exit 1
fi

echo "Artifact Created Successfully: \$(ls -lh ${shellQuote("/output/${artifact_name}")})"
""".stripIndent()

        def build_script_path = "${artifact_dir}/node_build.sh"

        writeFile(
            file: build_script_path,
            text: build_script
        )

        sh """
            set -e
            chmod +x ${shellQuote(build_script_path)}
        """.stripIndent()

        try {
            sh """
                set -e
                docker run --rm \\
                    -v ${shellQuote("${project_path}:/app")} \\
                    -v ${shellQuote("${artifact_dir}:/output")} \\
                    -w /app \\
                    ${shellQuote("node:${node_version}")} \\
                    sh /output/node_build.sh
            """.stripIndent()
        } finally {
            sh """
                sudo chown -R \$(id -u):\$(id -g) ${shellQuote(project_path)} ${shellQuote(artifact_dir)} 2>/dev/null || true
                rm -f ${shellQuote(build_script_path)} || true
            """.stripIndent()
        }

        if (!fileExists(artifact_path)) {
            error("Build completed, but artifact not found: ${artifact_path}")
        }

        env.GENERATED_ARTIFACT_PATH = artifact_path
        env.GENERATED_ARTIFACT_NAME = artifact_name

        logger.logger(
            'msg': "Artifact successfully built & packed: ${artifact_path}",
            'level': 'INFO'
        )
    }
}
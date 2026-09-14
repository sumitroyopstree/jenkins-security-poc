package opstree.nodejs

import opstree.common.*

/**
 * Packages npm applications without the shared node_build helper's host sudo/cache setup.
 */
def build_factory(Map step_params) {
    def logger = new logger()

    if (!(step_params.perform_code_build == true || step_params.perform_code_build?.toString()?.equalsIgnoreCase('true'))) {
        logger.logger('msg': 'Node.js build disabled.', 'level': 'INFO')
        return
    }

    def repoUrl = step_params.repo_url?.toString()?.trim()
    def repoDir = new parser().fetch_git_repo_name(repo_url: repoUrl)
    def sourcePath = step_params.source_code_path?.toString()?.trim() ?: ''
    def nodeVersion = step_params.node_version?.toString()?.trim() ?: '18'
    def appName = step_params.app_name?.toString()?.trim() ?: repoDir
    def buildCommand = step_params.build_command?.toString()?.trim() ?: 'npm install'
    def outputPath = step_params.build_output_path?.toString()?.trim() ?: '.'
    def artifactName = "${appName.replaceAll(/[^A-Za-z0-9._-]+/, '-').replaceAll(/^-+|-+$/, '')}-${sh(script: "git -C '${WORKSPACE}/${repoDir}' rev-parse --short HEAD", returnStdout: true).trim()}.tar.gz"
    def artifactDir = "${WORKSPACE}/artifact"
    def projectPath = "${WORKSPACE}/${repoDir}${sourcePath}"
    def includeNodeModules = step_params.include_node_modules == true || step_params.include_node_modules?.toString()?.equalsIgnoreCase('true')

    if (!repoUrl || !repoDir) {
        error('repo_url is required for the Node.js artifact build.')
    }

    dir(projectPath) {
        sh "mkdir -p '${artifactDir}'"
        def packageCommand = outputPath == '.' ?
            (includeNodeModules ? "tar -czf '/output/${artifactName}' --exclude='./.git' ." : "tar -czf '/output/${artifactName}' --exclude='./.git' --exclude='./node_modules' .") :
            (includeNodeModules ? "tar -czf '/output/${artifactName}' package.json '${outputPath}' node_modules 2>/dev/null || tar -czf '/output/${artifactName}' package.json '${outputPath}'" : "tar -czf '/output/${artifactName}' package.json '${outputPath}'")

        sh """#!/bin/bash
            set -e
            docker run --rm \\
                -v '${projectPath}:/app' \\
                -v '${artifactDir}:/output' \\
                -w /app \\
                node:${nodeVersion} \\
                sh -c '${buildCommand} && ${packageCommand}'
        """
    }

    env.GENERATED_ARTIFACT_PATH = "${artifactDir}/${artifactName}"
    env.GENERATED_ARTIFACT_NAME = artifactName
    logger.logger('msg': "Artifact successfully built & packed: ${env.GENERATED_ARTIFACT_PATH}", 'level': 'INFO')
}

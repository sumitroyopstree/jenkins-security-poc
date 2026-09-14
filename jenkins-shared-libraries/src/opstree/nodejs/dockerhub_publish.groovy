package opstree.nodejs

import opstree.common.parser

/**
 * Pushes the image built by common/build_dockerfile.groovy to DockerHub.
 */
def publish(Map step_params) {
    def repoUrl = step_params.repo_url?.toString()?.trim()
    def imageName = step_params.image_name?.toString()?.trim()
    def credentialsId = step_params.dockerhub_credentials_id?.toString()?.trim()

    if (!repoUrl || !imageName || !credentialsId) {
        error('repo_url, image_name, and dockerhub_credentials_id are required for DockerHub publishing.')
    }

    def repoDir = new parser().fetch_git_repo_name(repo_url: repoUrl)
    def imageTag = sh(
        script: "git -C '${WORKSPACE}/${repoDir}' rev-parse --short HEAD",
        returnStdout: true
    ).trim()

    if (!imageTag || imageTag == 'latest') {
        error('An immutable Git SHA image tag is required for DockerHub publishing.')
    }

    def fullImage = "${imageName}:${imageTag}"

    withCredentials([usernamePassword(
        credentialsId: credentialsId,
        usernameVariable: 'DOCKERHUB_USER',
        passwordVariable: 'DOCKERHUB_TOKEN'
    )]) {
        sh """#!/bin/bash
            set -euo pipefail
            printf '%s' "\$DOCKERHUB_TOKEN" | docker login --username "\$DOCKERHUB_USER" --password-stdin
            docker push '${fullImage}'
            docker logout >/dev/null 2>&1 || true
        """
    }

    env.DOCKER_IMAGE = imageName
    env.DOCKER_TAG = imageTag
    env.DOCKER_IMAGE_REFERENCE = fullImage
    echo "Published Docker image: ${fullImage}"
}

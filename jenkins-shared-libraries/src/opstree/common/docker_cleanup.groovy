package opstree.common

def cleanup(Map step_params) {
    def imageName = step_params.image_name ?: ''
    if (imageName) {
        sh(script: 'docker rmi -f ' + imageName + ':latest 2>/dev/null || true', returnStatus: true)
        sh(script: 'docker rmi -f ' + imageName + ' 2>/dev/null || true', returnStatus: true)
    }
    sh(script: 'docker image prune -f 2>/dev/null || true', returnStatus: true)
    sh(script: 'docker container prune -f 2>/dev/null || true', returnStatus: true)
}

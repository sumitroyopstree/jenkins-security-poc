package opstree.laravel

import opstree.common.*

def build_factory(Map step_params) {
    logger = new logger()
    if (step_params.perform_code_build == 'true' || step_params.perform_code_build == true) {
        build_artifact(step_params)
    } else {
        logger.logger('msg':'No valid option selected for Building Artifact. Please mention correct values.', 'level':'WARN')
    }
}

def build_artifact(Map step_params) {
    logger = new logger()
    parser = new parser()

    logger.logger('msg':'Performing Build Step for Laravel (PHP)', 'level':'INFO')

    repo_url         = "${step_params.repo_url}"
    source_code_path = step_params.source_code_path ?: ''
    
    def raw_php_version = step_params.php_version
    php_version      = (raw_php_version && raw_php_version != 'null') ? "${raw_php_version}" : '8.4'

    // GString interpolation converts null -> "null" string, guard against that
    def raw_build_command = step_params.build_command
    build_command = (raw_build_command && raw_build_command != 'null') \
                        ? raw_build_command \
                        : 'composer install --no-dev --optimize-autoloader --ignore-platform-reqs --no-scripts'

    repo_dir     = parser.fetch_git_repo_name('repo_url':"${repo_url}")
    project_path = "${WORKSPACE}/${repo_dir}${source_code_path}"

    logger.logger('msg':"Building Laravel app with PHP ${php_version}", 'level':'INFO')
    logger.logger('msg':"Build command: ${build_command}", 'level':'INFO')

    dir(project_path) {
        sh """
            docker run --rm \\
                -v ${project_path}:/app \\
                -w /app \\
                php:${php_version}-cli \\
                sh -c "
                    set -e
                    echo '[INFO] Installing system dependencies (unzip)...'
                    apt-get update -qq && apt-get install -y --no-install-recommends unzip libzip-dev curl > /dev/null
                    docker-php-ext-install zip > /dev/null 2>&1 || true
                    echo '[INFO] Installing Composer...'
                    curl -sS https://getcomposer.org/installer | php -- --install-dir=/usr/local/bin --filename=composer
                    echo '[INFO] Composer version:'
                    composer --version
                    echo '[INFO] Running build command: ${build_command}'
                    ${build_command}
                    echo '[INFO] Build complete.'
                "
        """
        logger.logger('msg':'Laravel build successful', 'level':'INFO')
    }
}

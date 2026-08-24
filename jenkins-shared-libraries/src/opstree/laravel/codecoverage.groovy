package opstree.laravel

import opstree.common.*

def code_coverage_factory(Map step_params) {
    logger = new logger()
    if (step_params.perform_code_coverage == 'true' || step_params.perform_code_coverage == true) {
        code_coverage(step_params)
    } else {
        logger.logger('msg':'No valid option selected for code coverage. Please mention correct values.', 'level':'WARN')
    }
}

def code_coverage(Map step_params) {
    logger = new logger()
    parser = new parser()

    logger.logger('msg':'Performing Code Coverage Step for Laravel (PHP)', 'level':'INFO')

    repo_url         = "${step_params.repo_url}"
    repo_url_type    = "${step_params.repo_url_type}"
    source_code_path = step_params.source_code_path ?: ''

    def raw_php_version = step_params.php_version
    php_version      = (raw_php_version && raw_php_version != 'null') ? "${raw_php_version}" : '8.4'

    repo_dir     = parser.fetch_git_repo_name('repo_url':"${repo_url}")
    project_path = "${WORKSPACE}/${repo_dir}${source_code_path}"

    logger.logger('msg':"Running PHPUnit code coverage with PHP ${php_version}", 'level':'INFO')

    try {
        dir(project_path) {
            // vendor/ is already populated by Build + Unit Test stages.
            // We still need apt-get + composer in the fresh container for the php binary deps,
            // but we skip composer install if vendor/ is already present.
            sh """
                docker run --rm \\
                    -v ${project_path}:/app \\
                    -w /app \\
                    php:${php_version}-cli \\
                    sh -c "
                        set -e
                        echo '[INFO] Installing system dependencies...'
                        apt-get update -qq && apt-get install -y --no-install-recommends unzip libzip-dev curl > /dev/null
                        docker-php-ext-install zip sockets > /dev/null 2>&1 || true
                        curl -sS https://getcomposer.org/installer | php -- --install-dir=/usr/local/bin --filename=composer

                        if [ ! -d vendor ]; then
                            echo '[INFO] vendor/ not found — running full composer install...'
                            composer install --no-interaction --prefer-dist --ignore-platform-reqs
                        else
                            echo '[INFO] vendor/ found from previous stage — skipping composer install.'
                        fi

                        echo '[INFO] Generating app key if needed...'
                        cp -n .env.example .env 2>/dev/null || true
                        php artisan key:generate 2>/dev/null || true

                        echo '[INFO] Running PHPUnit with coverage...'
                        if [ -f artisan ]; then
                            php artisan test \\\\
                                --coverage-clover=coverage/clover.xml \\\\
                                --coverage-html=coverage/html \\\\
                                --log-junit=junit.xml || echo '[WARN] Some tests failed during coverage run'
                        else
                            vendor/bin/phpunit \\\\
                                --coverage-clover coverage/clover.xml \\\\
                                --coverage-html coverage/html \\\\
                                --log-junit junit.xml || echo '[WARN] Some tests failed during coverage run'
                        fi
                        echo '[INFO] Coverage report generated.'
                    "
            """

            // Publish HTML coverage report if generated
            if (fileExists('coverage/html/index.html')) {
                publishHTML([
                    allowMissing          : true,
                    alwaysLinkToLastBuild : false,
                    keepAll               : false,
                    reportDir             : 'coverage/html',
                    reportFiles           : 'index.html',
                    reportName            : 'Laravel PHPUnit Coverage Report',
                    reportTitles          : '',
                    useWrapperFileDirectly: true
                ])
                logger.logger('msg':'HTML coverage report published to Jenkins', 'level':'INFO')
            } else {
                logger.logger('msg':'HTML coverage report not found — skipping publish', 'level':'WARN')
            }

            logger.logger('msg':'Code coverage step completed successfully', 'level':'INFO')
        }
    } catch (Exception e) {
        logger.logger('msg':"Code Coverage Failed. Error Details: ${e.message}", 'level':'ERROR')
        error('Code coverage process failed due to an exception.')
    }
}

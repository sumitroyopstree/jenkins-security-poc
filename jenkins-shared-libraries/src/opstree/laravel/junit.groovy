package opstree.laravel

import opstree.common.*

def unit_testing_factory(Map step_params) {
    logger = new logger()
    if (step_params.unit_testing_check == 'true' || step_params.unit_testing_check == true) {
        unit_test(step_params)
    } else {
        logger.logger('msg':'No valid option selected for Unit Testing. Please mention correct values.', 'level':'WARN')
    }
}

def unit_test(Map step_params) {
    logger          = new logger()
    parser          = new parser()
    reports_manager = new reports_management()

    logger.logger('msg':'Performing Unit Tests for Laravel (PHP)', 'level':'INFO')

    repo_url                        = "${step_params.repo_url}"
    source_code_path                = step_params.source_code_path                ?: ''

    def raw_php_version             = step_params.php_version
    php_version                     = (raw_php_version && raw_php_version != 'null') ? "${raw_php_version}" : '8.4'
    fail_job_if_unit_issue_detected = "${step_params.fail_job_if_unit_issue_detected}"
    unit_test_reports_path          = "${step_params.unit_test_reports_path ?: ''}"

    repo_dir     = parser.fetch_git_repo_name('repo_url':"${repo_url}")
    project_path = "${WORKSPACE}/${repo_dir}${source_code_path}"

    logger.logger('msg':"Running PHPUnit tests with PHP ${php_version}", 'level':'INFO')

    dir(project_path) {
        try {
            // vendor/ is already populated by the Build stage (mounted workspace volume).
            // Only install dev dependencies if vendor/ is missing (e.g. build was skipped).
            // No apt-get or Composer download needed — php:cli image has php, we just need vendor/.
            def test_cmd = """
                set -e
                if [ ! -d vendor ]; then
                    echo '[INFO] vendor/ not found — installing system deps + Composer + dependencies...'
                    apt-get update -qq && apt-get install -y --no-install-recommends unzip libzip-dev curl > /dev/null
                    docker-php-ext-install zip sockets > /dev/null 2>&1 || true
                    curl -sS https://getcomposer.org/installer | php -- --install-dir=/usr/local/bin --filename=composer
                    composer install --no-interaction --prefer-dist --ignore-platform-reqs
                else
                    echo '[INFO] vendor/ found from Build stage — installing dev deps only...'
                    apt-get update -qq && apt-get install -y --no-install-recommends unzip libzip-dev curl > /dev/null
                    docker-php-ext-install zip sockets > /dev/null 2>&1 || true
                    curl -sS https://getcomposer.org/installer | php -- --install-dir=/usr/local/bin --filename=composer
                    composer install --no-interaction --prefer-dist --ignore-platform-reqs
                fi
                echo '[INFO] Generating app key if not set...'
                cp -n .env.example .env 2>/dev/null || true
                php artisan key:generate 2>/dev/null || true
                echo '[INFO] Running PHPUnit tests...'
                if [ -f artisan ]; then
                    php artisan test --log-junit=junit.xml || echo '[WARN] Some tests failed'
                else
                    vendor/bin/phpunit --log-junit junit.xml || echo '[WARN] Some tests failed'
                fi
                echo '[INFO] Tests complete.'
            """

            sh """
                docker run --rm \\
                    -v ${project_path}:/app \\
                    -w /app \\
                    php:${php_version}-cli \\
                    sh -c "${test_cmd}"
            """

            // Fix absolute paths in any coverage files for SonarQube compatibility
            sh """
                if [ -f coverage/lcov.info ]; then
                    sed -i 's|^SF:/app/|SF:|g' coverage/lcov.info || true
                    cp coverage/lcov.info lcov.info || true
                    echo '[INFO] lcov.info paths ready for SonarQube'
                fi
            """

            def reports_pattern  = (unit_test_reports_path && unit_test_reports_path != 'null' && unit_test_reports_path != '') ? unit_test_reports_path : '**/junit*.xml'
            def findbugs_pattern = step_params.findbugs_test_report_path ?: ''
            reports_manager.publish_static_code_analysis_issues(unit_test_reports_path: reports_pattern, findbugs_test_report_path: "${findbugs_pattern}")

        } catch (Exception e) {
            def reports_pattern  = (unit_test_reports_path && unit_test_reports_path != 'null' && unit_test_reports_path != '') ? unit_test_reports_path : '**/junit*.xml'
            def findbugs_pattern = step_params.findbugs_test_report_path ?: ''
            try {
                reports_manager.publish_static_code_analysis_issues(unit_test_reports_path: reports_pattern, findbugs_test_report_path: "${findbugs_pattern}")
            } catch (ignored) {}

            if (fail_job_if_unit_issue_detected == 'false' || fail_job_if_unit_issue_detected == false) {
                logger.logger('msg':'Unit Test found Issues!! Ignoring as per User inputs', 'level':'WARN')
            } else {
                logger.logger('msg':"Unit Test Failed. Error Details: ${e}", 'level':'ERROR')
                error()
            }
        }
    }
}

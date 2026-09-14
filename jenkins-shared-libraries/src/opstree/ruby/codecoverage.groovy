package opstree.ruby

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

    logger.logger('msg':'Performing Code Coverage Step for Ruby', 'level':'INFO')

    repo_url         = "${step_params.repo_url}"
    repo_url_type    = "${step_params.repo_url_type}"
    source_code_path = step_params.source_code_path ?: ''

    def raw_ruby_version = step_params.ruby_version
    ruby_version      = (raw_ruby_version && raw_ruby_version != 'null') ? "${raw_ruby_version}" : '3.4'

    repo_dir     = parser.fetch_git_repo_name('repo_url':"${repo_url}")
    project_path = "${WORKSPACE}/${repo_dir}${source_code_path}"

    logger.logger('msg':"Running SimpleCov code coverage with Ruby ${ruby_version}", 'level':'INFO')

    try {
        dir(project_path) {
            sh """
                docker run --rm \\
                    -v ${project_path}:/app \\
                    -w /app \\
                    -e BUNDLE_PATH=vendor/bundle \\
                    ruby:${ruby_version}-slim \\
                    sh -c "
                        set -e
                        echo '[INFO] Installing system dependencies...'
                        apt-get update -qq && apt-get install -y --no-install-recommends build-essential git libyaml-dev pkg-config sqlite3 libsqlite3-dev > /dev/null
                        echo '[INFO] Running tests with SimpleCov coverage (gems cached in vendor/bundle)...'
                        COVERAGE=true BUNDLE_PATH=vendor/bundle bundle exec rspec || echo '[WARN] Some tests failed during coverage run'
                    "
            """

            if (fileExists('coverage/index.html')) {
                publishHTML([
                    allowMissing          : true,
                    alwaysLinkToLastBuild : false,
                    keepAll               : false,
                    reportDir             : 'coverage',
                    reportFiles           : 'index.html',
                    reportName            : 'Ruby SimpleCov Coverage Report',
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

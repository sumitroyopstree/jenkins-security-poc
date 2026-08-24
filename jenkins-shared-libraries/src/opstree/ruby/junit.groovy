package opstree.ruby

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

    logger.logger('msg':'Performing Unit Tests for Ruby (RSpec / Minitest)', 'level':'INFO')

    repo_url                        = "${step_params.repo_url}"
    source_code_path                = step_params.source_code_path                ?: ''
    
    def raw_ruby_version            = step_params.ruby_version
    ruby_version                     = (raw_ruby_version && raw_ruby_version != 'null') ? "${raw_ruby_version}" : '3.4'
    fail_job_if_unit_issue_detected = "${step_params.fail_job_if_unit_issue_detected}"
    unit_test_reports_path          = "${step_params.unit_test_reports_path ?: ''}"

    repo_dir     = parser.fetch_git_repo_name('repo_url':"${repo_url}")
    project_path = "${WORKSPACE}/${repo_dir}${source_code_path}"

    logger.logger('msg':"Running Ruby unit tests with Ruby ${ruby_version}", 'level':'INFO')

    dir(project_path) {
        try {
            // Production gems are cached in vendor/bundle from Build stage.
            // BUNDLE_WITHOUT=development installs the test group gems into vendor/bundle in ~5s.
            def test_cmd = """
                set -e
                echo '[INFO] Installing system dependencies...'
                apt-get update -qq && apt-get install -y --no-install-recommends build-essential git libyaml-dev pkg-config sqlite3 libsqlite3-dev > /dev/null
                echo '[INFO] Installing test dependencies into vendor/bundle...'
                BUNDLE_PATH=vendor/bundle BUNDLE_WITHOUT=development bundle install --jobs 4 --retry 3
                echo '[INFO] Running tests...'
                if [ -d "spec" ]; then
                    BUNDLE_PATH=vendor/bundle bundle exec rspec --format progress --format RspecJunitFormatter --out junit.xml || echo '[WARN] Some RSpec tests failed'
                else
                    BUNDLE_PATH=vendor/bundle bundle exec rails test || echo '[WARN] Some Rails tests failed'
                fi
                echo '[INFO] Tests complete.'
            """

            sh """
                docker run --rm \\
                    -v ${project_path}:/app \\
                    -w /app \\
                    -e BUNDLE_PATH=vendor/bundle \\
                    ruby:${ruby_version}-slim \\
                    sh -c "${test_cmd}"
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

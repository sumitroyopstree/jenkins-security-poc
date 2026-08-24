package opstree.ruby

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

    logger.logger('msg':'Performing Build Step for Ruby (Ruby on Rails)', 'level':'INFO')

    repo_url         = "${step_params.repo_url}"
    source_code_path = step_params.source_code_path ?: ''

    def raw_ruby_version = step_params.ruby_version
    ruby_version     = (raw_ruby_version && raw_ruby_version != 'null') ? "${raw_ruby_version}" : '3.4'

    def raw_build_command = step_params.build_command
    build_command    = (raw_build_command && raw_build_command != 'null') \
                        ? raw_build_command \
                        : 'BUNDLE_WITHOUT=development:test bundle install --jobs 4 --retry 3'

    repo_dir     = parser.fetch_git_repo_name('repo_url':"${repo_url}")
    project_path = "${WORKSPACE}/${repo_dir}${source_code_path}"

    logger.logger('msg':"Building Ruby app with Ruby ${ruby_version}", 'level':'INFO')
    logger.logger('msg':"Build command: ${build_command}", 'level':'INFO')

    dir(project_path) {
        sh """
            docker run --rm \\
                -v ${project_path}:/app \\
                -w /app \\
                -e BUNDLE_PATH=vendor/bundle \\
                ruby:${ruby_version}-slim \\
                sh -c "
                    set -e
                    echo '[INFO] Installing system build dependencies...'
                    apt-get update -qq && apt-get install -y --no-install-recommends build-essential git libyaml-dev pkg-config sqlite3 libsqlite3-dev > /dev/null
                    echo '[INFO] Ruby version:'
                    ruby -v
                    echo '[INFO] Bundler version:'
                    bundle -v
                    echo '[INFO] Running build command: ${build_command}'
                    BUNDLE_PATH=vendor/bundle ${build_command}
                    echo '[INFO] Ruby build complete.'
                "
        """
        logger.logger('msg':'Ruby build successful', 'level':'INFO')
    }
}

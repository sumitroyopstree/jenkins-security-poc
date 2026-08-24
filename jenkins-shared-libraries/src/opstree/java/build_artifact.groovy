package opstree.java

import opstree.java.build_artifact
import opstree.common.*

def build_factory(Map step_params) {
    def logger = new logger()
    if (step_params.perform_code_build == 'true') {
        build_artifact(step_params)
    } else {
        logger.logger('msg':'No valid option selected for Building Artifact. Please mention correct values.', 'level':'WARN')
    }
}

def build_artifact(Map step_params) {
    def logger = new logger()
    def parser = new parser()

    logger.logger('msg':'Performing Build Step', 'level':'INFO')

    def repo_url = "${step_params.repo_url}"
    def perform_code_build = "${step_params.perform_code_build}"
    def build_tool = "${step_params.build_tool}"
    def source_code_path = "${step_params.source_code_path}"
    def codeartifact_dependency = "${step_params.codeartifact_dependency}"
    def codeartifact_domain = "${step_params.codeartifact_domain}"
    def codeartifact_owner = "${step_params.codeartifact_owner}"
    def pom_location = "${step_params.pom_location}"
    def java_version = "${step_params.java_version}"
    def gradle_command = "${step_params.gradle_command}"
    def gradle_build_file_location = "${step_params.gradle_build_file_location}"

    // gradle_version can be set explicitly; if not, a sensible default is chosen per java_version
    def gradle_version_default = java_version == '11' ? '7.6.4' : (java_version == '17' ? '8.7' : '8.14.4')
    def gradle_version = step_params.gradle_version ?: gradle_version_default

    // Set the mvn_settings_path, default to '~/.m2/settings.xml' if not provided
    def mvn_settings_path
    if (step_params.mvn_settings_path != null) {
        mvn_settings_path = "${step_params.mvn_settings_path}"
    } else {
        mvn_settings_path = '~/.m2/settings.xml'
    }

    def repo_dir = parser.fetch_git_repo_name('repo_url':"${repo_url}")

    dir("${WORKSPACE}/${repo_dir}") {
        if (build_tool == 'maven') {
            if (codeartifact_dependency == 'true') {
                withAWS() {
                    def codeArtifactToken = sh(
                        script: """
                        aws codeartifact get-authorization-token --domain ${codeartifact_domain} --domain-owner ${codeartifact_owner} --query authorizationToken --output text
                        """,
                        returnStdout: true
                    ).trim()

                    // Export the token as an environment variable
                    def CODEARTIFACT_AUTH_TOKEN = codeArtifactToken

                    // Run the build based on the Java version
                    if (java_version == '11') {
                        sh """ docker run --rm -e CODEARTIFACT_AUTH_TOKEN=${CODEARTIFACT_AUTH_TOKEN} -v ~/.m2:/root/.m2 -v ${WORKSPACE}/${repo_dir}:/app/ -w /app maven:3.8.6-jdk-11 sh -c "cd /app/${pom_location} && mvn clean package -s /app/${mvn_settings_path} -DskipTests" """
                        logger.logger('msg':'Build successful', 'level':'INFO')
                    } else if (java_version == '8') {
                        sh """ docker run --rm -e CODEARTIFACT_AUTH_TOKEN=${CODEARTIFACT_AUTH_TOKEN} -v ~/.m2:/root/.m2 -v ${WORKSPACE}/${repo_dir}:/app/ -w /app maven:3.3-jdk-8 sh -c "cd /app/${pom_location} && mvn clean package -s /app/${mvn_settings_path} -DskipTests" """
                        logger.logger('msg':'Build successful', 'level':'INFO')
                    } else if (java_version == '17') {
                        sh """ docker run --rm -e CODEARTIFACT_AUTH_TOKEN=${CODEARTIFACT_AUTH_TOKEN} -v ~/.m2:/root/.m2 -v ${WORKSPACE}/${repo_dir}:/app/ -w /app maven:3.8.3-openjdk-17 sh -c "cd /app/${pom_location} && mvn clean package -s /app/${mvn_settings_path} -DskipTests" """
                        logger.logger('msg':'Build successful', 'level':'INFO')
                    }
                }
            }
        } else if (build_tool == 'gradle') {
            // Gradle version and Java version are independently configurable
            // Docker image: gradle:<gradle_version>-jdk<java_version>
            sh """ docker run --rm -v ~/.gradle:/root/.gradle -v ${WORKSPACE}/${repo_dir}:/app/ -w /app gradle:${gradle_version}-jdk${java_version} sh -c "cd /app/${gradle_build_file_location} && gradle ${gradle_command}" """
            logger.logger('msg':'Build successful', 'level':'INFO')
        } else {
            logger.logger('msg':"Choose appropriate build tool!!! Build Failed Error Details: ${e}", 'level':'ERROR')
            error()
        }
    }
}

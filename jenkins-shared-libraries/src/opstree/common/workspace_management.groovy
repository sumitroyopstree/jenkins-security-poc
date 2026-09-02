package opstree.common

def workspace_management(Map step_params) {
    def logger = new opstree.common.logger()

    def params = [
        clean_workspace: get_value(step_params, 'clean_workspace', true),
        ignore_clean_workspace_failure: get_value(step_params, 'ignore_clean_workspace_failure', false),
        delete_dirs: get_value(step_params, 'delete_dirs', true),
        clean_when_build_aborted: get_value(step_params, 'clean_when_build_aborted', true),
        clean_when_build_failed: get_value(step_params, 'clean_when_build_failed', true),
        clean_when_not_built: get_value(step_params, 'clean_when_not_built', true),
        clean_when_build_succeed: get_value(step_params, 'clean_when_build_succeed', true),
        clean_when_build_unstable: get_value(step_params, 'clean_when_build_unstable', true)
    ]
    if (params.clean_workspace == 'true' || params.clean_workspace == true) {
        logger.logger('msg':'Cleaning up workspace and temporary directories', 'level':'INFO')
        def workspace = env.WORKSPACE

        // Fix permissions FIRST so cleanWs can delete root-created files instantly
        sh "sudo chown -R \$(id -u):\$(id -g) ${workspace} ${workspace}@tmp ${workspace}@script ${workspace}@libs 2>/dev/null || true"

        cleanWs(
            notFailBuild: params.ignore_clean_workspace_failure.toBoolean(),
            deleteDirs: params.delete_dirs.toBoolean(),
            cleanWhenAborted: params.clean_when_build_aborted.toBoolean(),
            cleanWhenFailure: params.clean_when_build_failed.toBoolean(),
            cleanWhenNotBuilt: params.clean_when_not_built.toBoolean(),
            cleanWhenSuccess: params.clean_when_build_succeed.toBoolean(),
            cleanWhenUnstable: params.clean_when_build_unstable.toBoolean()
        )

        sh "sudo rm -rf ${workspace}@tmp ${workspace}@script ${workspace}@libs 2>/dev/null || true"

        logger.logger('msg':'Cleanws Completed', 'level':'INFO')
    } else {
        logger.logger('msg':'Cleanws Skipped', 'level':'INFO')
    }
}

def get_value(Map map, String key, Object defaultVal) {
    if (map.containsKey(key)) {
        return map.get(key)
    } else {
        return defaultVal
    }
}

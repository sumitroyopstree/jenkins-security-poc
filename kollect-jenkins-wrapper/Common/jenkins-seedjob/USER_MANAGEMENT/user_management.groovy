def userJobs = [
    'user-onboarding': [
        url          : 'https://gitlab.healthreconconnect.com/hrc/devops.git',
        credentials  : 'hasantha-hrc-gitlab-access',
        branch       : 'main',
        scriptPath   : 'kollect-jenkins-wrapper/Common/user-onboarding/Jenkinsfile',
        owner        : 'CI-CD Team',
        logRotatorNum: 10,
        parameters   : [
            [type: 'choice', name: 'MODE', choices: ['bulk', 'single'], description: 'Onboarding mode — bulk (CSV) or single user'],
            [name: 'USERNAME', defaultValue: '', description: 'Username (only for single mode)'],
            [name: 'EMAIL', defaultValue: '', description: 'Email address (only for single mode)'],
            [name: 'ROLES', defaultValue: 'hrc-read,hrc-execute', description: 'Comma-separated roles to assign (only for single mode)'],
            [name: 'CSV_PATH', defaultValue: 'resources/user-onboarding/users.csv', description: 'Path to users CSV file (for bulk mode)'],
            [type: 'boolean', name: 'SEND_EMAIL', defaultValue: true, description: 'Send credentials email to user after creation']
        ]
    ],
    'user-offboarding': [
        url          : 'https://gitlab.healthreconconnect.com/hrc/devops.git',
        credentials  : 'hasantha-hrc-gitlab-access',
        branch       : 'main',
        scriptPath   : 'kollect-jenkins-wrapper/Common/user-offboarding/Jenkinsfile',
        owner        : 'CI-CD Team',
        logRotatorNum: 10,
        parameters   : [
            [type: 'choice', name: 'MODE', choices: ['single', 'bulk'], description: 'Offboarding mode — single user or bulk (CSV)'],
            [name: 'USERNAME', defaultValue: '', description: 'Username to delete (only for single mode)'],
            [name: 'CSV_PATH', defaultValue: 'resources/user-onboarding/users.csv', description: 'Path to users CSV file (for bulk mode)']
        ]
    ]
]

userJobs.each { jobName, config ->
    pipelineJob("user-management/${jobName}") {
        displayName("${jobName}")
        description("User management pipeline for ${jobName} | Owner: ${config.owner}")
        logRotator {
            numToKeep(config.logRotatorNum)
        }
        parameters {
            config.parameters.each { param ->
                if (param.type == 'boolean') {
                    booleanParam(param.name, param.defaultValue, param.description)
                } else if (param.type == 'choice') {
                    choiceParam(param.name, param.choices, param.description)
                } else {
                    stringParam(param.name, param.defaultValue, param.description)
                }
            }
        }
        definition {
            cpsScm {
                scm {
                    git {
                        remote {
                            url(config.url)
                            credentials(config.credentials)
                        }
                        branch(config.branch)
                    }
                }
                scriptPath(config.scriptPath)
                lightweight(true)
            }
        }
    }
}

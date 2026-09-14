// Common Parameters for HRC CI Jobs (5 Environments)
def hrcCommonParameters = [
    [
        type: 'string',
        name: 'BRANCH',
        defaultValue: 'main',
        description: 'Branch to build (Default: main - can be overridden at build time)'
    ],
    [
        type: 'choice',
        name: 'ENVIRONMENT',
        choices: ['DEV', 'DEMO', 'QA', 'STG', 'PROD'],
        description: 'Select target environment'
    ],
    [
        type: 'boolean',
        name: 'TRIGGER_CD',
        defaultValue: true,
        description: 'Trigger CD pipeline after successful CI'
    ]
]

// Common Parameters for Non-Healthcare CI Jobs (3 Environments)
def nonHealthcareCommonParameters = [
    [
        type: 'string',
        name: 'BRANCH',
        defaultValue: 'main',
        description: 'Branch to build (Default: main - can be overridden at build time)'
    ],
    [
        type: 'choice',
        name: 'ENVIRONMENT',
        choices: ['DEV', 'QA', 'DEMO'],
        description: 'Select target environment'
    ],
    [
        type: 'boolean',
        name: 'TRIGGER_CD',
        defaultValue: true,
        description: 'Trigger CD pipeline after successful CI'
    ]
]

// HRC Project CI Jobs Map
def hrcCiJobs = [
    'HRC-Kollect-BE': [
        url          : 'https://gitlab.healthreconconnect.com/hrc/devops.git',
        credentials  : 'hasantha-hrc-gitlab-access',
        branch       : 'main',
        scriptPath   : 'jenkins_wrapper/CI/HRC-Kollect-BE/Jenkinsfile',
        parameters   : hrcCommonParameters
    ],
    'HRC-Kollect-FE': [
        url          : 'https://gitlab.healthreconconnect.com/hrc/devops.git',
        credentials  : 'hasantha-hrc-gitlab-access',
        branch       : 'main',
        scriptPath   : 'jenkins_wrapper/CI/HRC-Kollect-FE/Jenkinsfile',
        parameters   : hrcCommonParameters
    ],
    'HRC-Kollect-Cron': [
        url          : 'https://gitlab.healthreconconnect.com/hrc/devops.git',
        credentials  : 'hasantha-hrc-gitlab-access',
        branch       : 'main',
        scriptPath   : 'jenkins_wrapper/CI/HRC-Kollect-Cron/Jenkinsfile',
        parameters   : hrcCommonParameters
    ],
    'HRC-Kollect-Client-Billing-FE': [
        url          : 'https://gitlab.healthreconconnect.com/hrc/devops.git',
        credentials  : 'hasantha-hrc-gitlab-access',
        branch       : 'main',
        scriptPath   : 'jenkins_wrapper/CI/HRC-Kollect-Client-Billing-FE/Jenkinsfile',
        parameters   : hrcCommonParameters
    ],
    'HRC-Kollect-Client-Billing-BE': [
        url          : 'https://gitlab.healthreconconnect.com/hrc/devops.git',
        credentials  : 'hasantha-hrc-gitlab-access',
        branch       : 'main',
        scriptPath   : 'jenkins_wrapper/CI/HRC-Kollect-Client-Billing-BE/Jenkinsfile',
        parameters   : hrcCommonParameters
    ],
    'HRC-Kollect-Frontdesk-BE': [
        url          : 'https://gitlab.healthreconconnect.com/hrc/devops.git',
        credentials  : 'hasantha-hrc-gitlab-access',
        branch       : 'main',
        scriptPath   : 'jenkins_wrapper/CI/HRC-Kollect-Frontdesk-BE/Jenkinsfile',
        parameters   : hrcCommonParameters
    ],
    'HRC-Kollect-Pay-Portal-BE': [
        url          : 'https://gitlab.healthreconconnect.com/hrc/devops.git',
        credentials  : 'hasantha-hrc-gitlab-access',
        branch       : 'main',
        scriptPath   : 'jenkins_wrapper/CI/HRC-Kollect-Pay-Portal-BE/Jenkinsfile',
        parameters   : hrcCommonParameters
    ],
    'HRC-Kollect-Reporting-Module': [
        url          : 'https://gitlab.healthreconconnect.com/hrc/devops.git',
        credentials  : 'hasantha-hrc-gitlab-access',
        branch       : 'main',
        scriptPath   : 'jenkins_wrapper/CI/HRC-Kollect-Reporting-Module/Jenkinsfile',
        parameters   : hrcCommonParameters
    ],
    'HRC-AMD-Sync-Service': [
        url          : 'https://gitlab.healthreconconnect.com/hrc/devops.git',
        credentials  : 'hasantha-hrc-gitlab-access',
        branch       : 'main',
        scriptPath   : 'jenkins_wrapper/CI/HRC-AMD-Sync-Service/Jenkinsfile',
        parameters   : hrcCommonParameters
    ]
]

// Non-Healthcare Project CI Jobs Map (In Dedicated Folder)
def nonHealthcareCiJobs = [
    'Non-Healthcare-BE': [
        url          : 'https://gitlab.healthreconconnect.com/hrc/devops.git',
        credentials  : 'hasantha-hrc-gitlab-access',
        branch       : 'main',
        scriptPath   : 'jenkins_wrapper/Non-Healthcare/CI/Non-Healthcare-BE/Jenkinsfile',
        parameters   : nonHealthcareCommonParameters
    ],
    'Non-Healthcare-Cron': [
        url          : 'https://gitlab.healthreconconnect.com/hrc/devops.git',
        credentials  : 'hasantha-hrc-gitlab-access',
        branch       : 'main',
        scriptPath   : 'jenkins_wrapper/Non-Healthcare/CI/Non-Healthcare-Cron/Jenkinsfile',
        parameters   : nonHealthcareCommonParameters
    ],
    'Non-Healthcare-FE': [
        url          : 'https://gitlab.healthreconconnect.com/hrc/devops.git',
        credentials  : 'hasantha-hrc-gitlab-access',
        branch       : 'main',
        scriptPath   : 'jenkins_wrapper/Non-Healthcare/CI/Non-Healthcare-FE/Jenkinsfile',
        parameters   : nonHealthcareCommonParameters
    ]
]

// Generate HRC CI Jobs under CI/
hrcCiJobs.each { jobName, config ->
    pipelineJob("CI/${jobName}") {
        description("CI Pipeline for ${jobName} - HRC Project")

        logRotator {
            numToKeep(5)
        }

        parameters {
            config.parameters.each { param ->
                if (param.type == 'string') {
                    stringParam(param.name, param.defaultValue, param.description)
                } else if (param.type == 'choice') {
                    choiceParam(param.name, param.choices, param.description)
                } else if (param.type == 'boolean') {
                    booleanParam(param.name, param.defaultValue, param.description)
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
            }
        }
    }
}

// Generate Non-Healthcare CI Jobs under Non-Healthcare/CI/
nonHealthcareCiJobs.each { jobName, config ->
    pipelineJob("Non-Healthcare/CI/${jobName}") {
        description("CI Pipeline for ${jobName} - Non-Healthcare Project")

        logRotator {
            numToKeep(5)
        }

        parameters {
            config.parameters.each { param ->
                if (param.type == 'string') {
                    stringParam(param.name, param.defaultValue, param.description)
                } else if (param.type == 'choice') {
                    choiceParam(param.name, param.choices, param.description)
                } else if (param.type == 'boolean') {
                    booleanParam(param.name, param.defaultValue, param.description)
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
            }
        }
    }
}

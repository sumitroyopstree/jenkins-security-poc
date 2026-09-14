// Common Parameters for HRC CD Jobs (5 Environments)
def hrcCommonParameters = [
    [
        type: 'string',
        name: 'BRANCH',
        defaultValue: 'main',
        description: 'Branch to deploy from (Default: main - can be overridden at build time)'
    ],
    [
        type: 'string',
        name: 'image_tag',
        defaultValue: 'latest',
        description: 'Deployment / Build tag from CI'
    ],
    [
        type: 'choice',
        name: 'ENVIRONMENT',
        choices: ['DEV', 'DEMO', 'QA', 'STG', 'PROD'],
        description: 'Select target deployment environment'
    ]
]

// Common Parameters for Non-Healthcare CD Jobs (3 Environments)
def nonHealthcareCommonParameters = [
    [
        type: 'string',
        name: 'BRANCH',
        defaultValue: 'main',
        description: 'Branch to deploy from (Default: main - can be overridden at build time)'
    ],
    [
        type: 'string',
        name: 'image_tag',
        defaultValue: 'latest',
        description: 'Deployment / Build tag from CI'
    ],
    [
        type: 'choice',
        name: 'ENVIRONMENT',
        choices: ['DEV', 'QA', 'DEMO'],
        description: 'Select target deployment environment'
    ]
]

// HRC Project CD Jobs Map
def hrcCdJobs = [
    'HRC-Kollect-BE': [
        url          : 'https://gitlab.healthreconconnect.com/hrc/devops.git',
        credentials  : 'hasantha-hrc-gitlab-access',
        branch       : 'main',
        scriptPath   : 'jenkins_wrapper/CD/HRC-Kollect-BE/Jenkinsfile',
        parameters   : hrcCommonParameters
    ],
    'HRC-Kollect-FE': [
        url          : 'https://gitlab.healthreconconnect.com/hrc/devops.git',
        credentials  : 'hasantha-hrc-gitlab-access',
        branch       : 'main',
        scriptPath   : 'jenkins_wrapper/CD/HRC-Kollect-FE/Jenkinsfile',
        parameters   : hrcCommonParameters
    ],
    'HRC-Kollect-Cron': [
        url          : 'https://gitlab.healthreconconnect.com/hrc/devops.git',
        credentials  : 'hasantha-hrc-gitlab-access',
        branch       : 'main',
        scriptPath   : 'jenkins_wrapper/CD/HRC-Kollect-Cron/Jenkinsfile',
        parameters   : hrcCommonParameters
    ],
    'HRC-Kollect-Client-Billing-FE': [
        url          : 'https://gitlab.healthreconconnect.com/hrc/devops.git',
        credentials  : 'hasantha-hrc-gitlab-access',
        branch       : 'main',
        scriptPath   : 'jenkins_wrapper/CD/HRC-Kollect-Client-Billing-FE/Jenkinsfile',
        parameters   : hrcCommonParameters
    ],
    'HRC-Kollect-Client-Billing-BE': [
        url          : 'https://gitlab.healthreconconnect.com/hrc/devops.git',
        credentials  : 'hasantha-hrc-gitlab-access',
        branch       : 'main',
        scriptPath   : 'jenkins_wrapper/CD/HRC-Kollect-Client-Billing-BE/Jenkinsfile',
        parameters   : hrcCommonParameters
    ],
    'HRC-Kollect-Frontdesk-BE': [
        url          : 'https://gitlab.healthreconconnect.com/hrc/devops.git',
        credentials  : 'hasantha-hrc-gitlab-access',
        branch       : 'main',
        scriptPath   : 'jenkins_wrapper/CD/HRC-Kollect-Frontdesk-BE/Jenkinsfile',
        parameters   : hrcCommonParameters
    ],
    'HRC-Kollect-Pay-Portal-BE': [
        url          : 'https://gitlab.healthreconconnect.com/hrc/devops.git',
        credentials  : 'hasantha-hrc-gitlab-access',
        branch       : 'main',
        scriptPath   : 'jenkins_wrapper/CD/HRC-Kollect-Pay-Portal-BE/Jenkinsfile',
        parameters   : hrcCommonParameters
    ],
    'HRC-Kollect-Reporting-Module': [
        url          : 'https://gitlab.healthreconconnect.com/hrc/devops.git',
        credentials  : 'hasantha-hrc-gitlab-access',
        branch       : 'main',
        scriptPath   : 'jenkins_wrapper/CD/HRC-Kollect-Reporting-Module/Jenkinsfile',
        parameters   : hrcCommonParameters
    ],
    'HRC-AMD-Sync-Service': [
        url          : 'https://gitlab.healthreconconnect.com/hrc/devops.git',
        credentials  : 'hasantha-hrc-gitlab-access',
        branch       : 'main',
        scriptPath   : 'jenkins_wrapper/CD/HRC-AMD-Sync-Service/Jenkinsfile',
        parameters   : hrcCommonParameters
    ]
]

// Non-Healthcare Project CD Jobs Map (In Dedicated Folder)
def nonHealthcareCdJobs = [
    'Non-Healthcare-BE': [
        url          : 'https://gitlab.healthreconconnect.com/hrc/devops.git',
        credentials  : 'hasantha-hrc-gitlab-access',
        branch       : 'main',
        scriptPath   : 'jenkins_wrapper/Non-Healthcare/CD/Non-Healthcare-BE/Jenkinsfile',
        parameters   : nonHealthcareCommonParameters
    ],
    'Non-Healthcare-Cron': [
        url          : 'https://gitlab.healthreconconnect.com/hrc/devops.git',
        credentials  : 'hasantha-hrc-gitlab-access',
        branch       : 'main',
        scriptPath   : 'jenkins_wrapper/Non-Healthcare/CD/Non-Healthcare-Cron/Jenkinsfile',
        parameters   : nonHealthcareCommonParameters
    ],
    'Non-Healthcare-FE': [
        url          : 'https://gitlab.healthreconconnect.com/hrc/devops.git',
        credentials  : 'hasantha-hrc-gitlab-access',
        branch       : 'main',
        scriptPath   : 'jenkins_wrapper/Non-Healthcare/CD/Non-Healthcare-FE/Jenkinsfile',
        parameters   : nonHealthcareCommonParameters
    ]
]

// Generate HRC CD Jobs under CD/
hrcCdJobs.each { jobName, config ->
    pipelineJob("CD/${jobName}") {
        description("CD Pipeline for ${jobName} - HRC Project")

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

// Generate Non-Healthcare CD Jobs under Non-Healthcare/CD/
nonHealthcareCdJobs.each { jobName, config ->
    pipelineJob("Non-Healthcare/CD/${jobName}") {
        description("CD Pipeline for ${jobName} - Non-Healthcare Project")

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

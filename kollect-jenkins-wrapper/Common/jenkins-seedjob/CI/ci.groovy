// Common SCM Configuration
def cicdRepo        = 'https://gitlab.healthreconconnect.com/K1-infra/cicd.git'
def cicdCredentials = 'piyushu-gitlab-token'
def cicdBranch      = 'feature-cicd'

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
        choices: ['TEST', 'DEV', 'DEMO', 'QA', 'STG', 'PROD'],
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
        choices: ['TEST', 'DEV', 'QA', 'DEMO'],
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
        scriptPath   : 'kollect-jenkins-wrapper/Healthcare/CI/HRC-Kollect-BE/Jenkinsfile',
        parameters   : hrcCommonParameters
    ],
    'HRC-Kollect-FE': [
        scriptPath   : 'kollect-jenkins-wrapper/Healthcare/CI/HRC-Kollect-FE/Jenkinsfile',
        parameters   : hrcCommonParameters
    ],
    'HRC-Kollect-Cron': [
        scriptPath   : 'kollect-jenkins-wrapper/Healthcare/CI/HRC-Kollect-Cron/Jenkinsfile',
        parameters   : hrcCommonParameters
    ],
    'HRC-Kollect-Client-Billing-FE': [
        scriptPath   : 'kollect-jenkins-wrapper/Healthcare/CI/HRC-Kollect-Client-Billing-FE/Jenkinsfile',
        parameters   : hrcCommonParameters
    ],
    'HRC-Kollect-Client-Billing-BE': [
        scriptPath   : 'kollect-jenkins-wrapper/Healthcare/CI/HRC-Kollect-Client-Billing-BE/Jenkinsfile',
        parameters   : hrcCommonParameters
    ],
    'HRC-Kollect-frontdesk-backend-BE': [
        scriptPath   : 'kollect-jenkins-wrapper/Healthcare/CI/HRC-Kollect-frontdesk-backend-BE/Jenkinsfile',
        parameters   : hrcCommonParameters
    ],
    'HRC-Kollect-Pay-Portal-Backend': [
        scriptPath   : 'kollect-jenkins-wrapper/Healthcare/CI/HRC-Kollect-Pay-Portal-Backend/Jenkinsfile',
        parameters   : hrcCommonParameters
    ],
    'HRC-Kollect-Reporting-Module': [
        scriptPath   : 'kollect-jenkins-wrapper/Healthcare/CI/HRC-Kollect-Reporting-Module/Jenkinsfile',
        parameters   : hrcCommonParameters
    ],
    'HRC-AMD-Sync-Service': [
        scriptPath   : 'kollect-jenkins-wrapper/Healthcare/CI/HRC-AMD-Sync-Service/Jenkinsfile',
        parameters   : hrcCommonParameters
    ]
]

// Non-Healthcare Project CI Jobs Map (In Dedicated Folder)
def nonHealthcareCiJobs = [
    'Non-Healthcare-BE': [
        scriptPath   : 'kollect-jenkins-wrapper/Non-Healthcare/CI/Non-Healthcare-BE/Jenkinsfile',
        parameters   : nonHealthcareCommonParameters
    ],
    'Non-Healthcare-Cron': [
        scriptPath   : 'kollect-jenkins-wrapper/Non-Healthcare/CI/Non-Healthcare-Cron/Jenkinsfile',
        parameters   : nonHealthcareCommonParameters
    ],
    'Non-Healthcare-FE': [
        scriptPath   : 'kollect-jenkins-wrapper/Non-Healthcare/CI/Non-Healthcare-FE/Jenkinsfile',
        parameters   : nonHealthcareCommonParameters
    ]
]

// Generate HRC CI Jobs under Healthcare/CI/
hrcCiJobs.each { jobName, config ->
    pipelineJob("Healthcare/CI/${jobName}") {
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
                            url(cicdRepo)
                            credentials(cicdCredentials)
                        }
                        branch(cicdBranch)
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
                            url(cicdRepo)
                            credentials(cicdCredentials)
                        }
                        branch(cicdBranch)
                    }
                }
                scriptPath(config.scriptPath)
            }
        }
    }
}

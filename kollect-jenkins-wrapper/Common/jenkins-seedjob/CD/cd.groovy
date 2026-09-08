// Common SCM Configuration
def cicdRepo        = 'https://gitlab.healthreconconnect.com/K1-infra/cicd.git'
def cicdCredentials = 'piyushu-gitlab-token'
def cicdBranch      = 'feature-cicd'

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
        choices: ['TEST', 'DEV', 'QA', 'DEMO'],
        description: 'Select target deployment environment'
    ]
]

// HRC Project CD Jobs Map
def hrcCdJobs = [
    'HRC-Kollect-BE': [
        scriptPath   : 'kollect-jenkins-wrapper/Healthcare/CD/HRC-Kollect-BE/Jenkinsfile',
        parameters   : hrcCommonParameters
    ],
    'HRC-Kollect-FE': [
        scriptPath   : 'kollect-jenkins-wrapper/Healthcare/CD/HRC-Kollect-FE/Jenkinsfile',
        parameters   : hrcCommonParameters
    ],
    'HRC-Kollect-Cron': [
        scriptPath   : 'kollect-jenkins-wrapper/Healthcare/CD/HRC-Kollect-Cron/Jenkinsfile',
        parameters   : hrcCommonParameters
    ],
    'HRC-Kollect-Client-Billing-FE': [
        scriptPath   : 'kollect-jenkins-wrapper/Healthcare/CD/HRC-Kollect-Client-Billing-FE/Jenkinsfile',
        parameters   : hrcCommonParameters
    ],
    'HRC-Kollect-Client-Billing-BE': [
        scriptPath   : 'kollect-jenkins-wrapper/Healthcare/CD/HRC-Kollect-Client-Billing-BE/Jenkinsfile',
        parameters   : hrcCommonParameters
    ],
    'HRC-Kollect-frontdesk-backend-BE': [
        scriptPath   : 'kollect-jenkins-wrapper/Healthcare/CD/HRC-Kollect-frontdesk-backend-BE/Jenkinsfile',
        parameters   : hrcCommonParameters
    ],
    'HRC-Kollect-Pay-Portal-Backend': [
        scriptPath   : 'kollect-jenkins-wrapper/Healthcare/CD/HRC-Kollect-Pay-Portal-Backend/Jenkinsfile',
        parameters   : hrcCommonParameters
    ],
    'HRC-Kollect-Reporting-Module': [
        scriptPath   : 'kollect-jenkins-wrapper/Healthcare/CD/HRC-Kollect-Reporting-Module/Jenkinsfile',
        parameters   : hrcCommonParameters
    ],
    'HRC-AMD-Sync-Service': [
        scriptPath   : 'kollect-jenkins-wrapper/Healthcare/CD/HRC-AMD-Sync-Service/Jenkinsfile',
        parameters   : hrcCommonParameters
    ]
]

// Non-Healthcare Project CD Jobs Map (In Dedicated Folder)
def nonHealthcareCdJobs = [
    'Non-Healthcare-BE': [
        scriptPath   : 'kollect-jenkins-wrapper/Non-Healthcare/CD/Non-Healthcare-BE/Jenkinsfile',
        parameters   : nonHealthcareCommonParameters
    ],
    'Non-Healthcare-Cron': [
        scriptPath   : 'kollect-jenkins-wrapper/Non-Healthcare/CD/Non-Healthcare-Cron/Jenkinsfile',
        parameters   : nonHealthcareCommonParameters
    ],
    'Non-Healthcare-FE': [
        scriptPath   : 'kollect-jenkins-wrapper/Non-Healthcare/CD/Non-Healthcare-FE/Jenkinsfile',
        parameters   : nonHealthcareCommonParameters
    ]
]

// Generate HRC CD Jobs under Healthcare/CD/
hrcCdJobs.each { jobName, config ->
    pipelineJob("Healthcare/CD/${jobName}") {
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

// ==============================================================================
// CENTRAL INVENTORY DATA STORE FOR ALL HRC & NON-HEALTHCARE JOBS (CI & CD)
// Simple, readable Data Maps for all Environments & Applications
// ==============================================================================

def call(String type, String appName, String envName = 'TEST') {
    def env = (envName ?: 'TEST').toUpperCase()

    // --------------------------------------------------------------------------
    // 1. CI INVENTORY DATA MAP (S3 Buckets, Regions, Credentials)
    // --------------------------------------------------------------------------
    def ciData = [
        'TEST': [bucket: 'hrc-cicd-test-bucket', region: 'us-east-1', creds: 'hrc-aws-ecr-credentials', ecr_region: 'us-east-1', account_id: '167121004129'],
        'DEV' : [bucket: 'hrc-cicd-test-bucket', region: 'us-east-1', creds: 'hrc-aws-ecr-credentials', ecr_region: 'us-east-1', account_id: '167121004129'],
        'QA'  : [bucket: 'hrc-cicd-test-bucket', region: 'us-east-1', creds: 'hrc-aws-ecr-credentials', ecr_region: 'us-east-1', account_id: '167121004129'],
        'DEMO': [bucket: 'hrc-cicd-test-bucket', region: 'us-east-1', creds: 'hrc-aws-ecr-credentials', ecr_region: 'us-east-1', account_id: '167121004129'],
        'STG' : [bucket: 'hrc-cicd-test-bucket', region: 'us-east-1', creds: 'hrc-aws-ecr-credentials', ecr_region: 'us-east-1', account_id: '167121004129'],
        'PROD': [bucket: 'hrc-cicd-test-bucket', region: 'us-east-1', creds: 'hrc-aws-ecr-credentials', ecr_region: 'us-east-1', account_id: '167121004129']
    ]

    // --------------------------------------------------------------------------
    // 2. CD INVENTORY DATA MAP (VM IPs, Deploy Paths, App Names, Secrets, S3/CloudFront)
    // --------------------------------------------------------------------------
    def cdData = [
        // HEALTHCARE JOBS
        'HRC-Kollect-BE': [
            'TEST': [ip: '10.2.30.254', path: '/opt/hrc-kollect-test/backend', app: 'hrc-kollect-test-backend', ssh: 'HRC-Kollect-Test-Server', secret: 'arn:aws:secretsmanager:us-east-1:167121004129:secret:dev.hrckollect.com-tDjPIP'],
            'DEV' : [ip: '10.2.40.133', path: '/opt/hrc-kollect-dev/backend', app: 'hrc-kollect-dev-backend', ssh: 'HRC-Kollect-Dev-Server', secret: 'arn:aws:secretsmanager:us-east-1:167121004129:secret:dev.hrckollect.com-tDjPIP'],
            'QA'  : [ip: '10.2.10.111', path: '/opt/hrc-kollect-qa/backend', app: 'hrc-kollect-qa-backend', ssh: 'HRC-Kollect-QA-Server', secret: 'arn:aws:secretsmanager:us-east-1:167121004129:secret:qa-hrckollect-com-0mHt87'],
            'DEMO': [ip: '10.2.40.238', path: '/opt/hrc-kollect-demo/backend', app: 'hrc-kollect-demo-backend', ssh: 'HRC-Kollect-Demo-Server', secret: 'arn:aws:secretsmanager:us-east-1:167121004129:secret:demo-new-hrckollect-com-KrSHaN'],
            'STG' : [ip: '10.2.40.170', path: '/opt/hrc-kollect-stg', app: 'hrc-kollect-stg-backend', ssh: 'HRC-Kollect-Stg-Server', secret: 'arn:aws:secretsmanager:us-east-1:167121004129:secret:stg-hrckollect-com-oLPno0'],
            'PROD': [ip: '10.2.10.118', path: '/opt/hrc-kollect-prod', app: 'hrc-kollect-prod-backend', ssh: 'HRC-Kollect-PROD-Server', secret: 'arn:aws:secretsmanager:us-east-1:167121004129:secret:portal.hrckollect.com-xvlUjU']
        ],
        'HRC-Kollect-FE': [
            'TEST': [s3_bucket: 'hrckollect-test-fe', cloudfront_id: 'E1RYWTTS4NPREW'],
            'DEV' : [s3_bucket: 'hrckollect-dev-fe', cloudfront_id: 'E1RYWTTS4NPREW'],
            'QA'  : [s3_bucket: 'hrckollect-qa-fe', cloudfront_id: 'E1RYWTTS4NPREW'],
            'DEMO': [s3_bucket: 'hrckollect-demo-fe', cloudfront_id: 'E1RYWTTS4NPREW'],
            'STG' : [s3_bucket: 'hrckollect-stg-fe', cloudfront_id: 'E1RYWTTS4NPREW'],
            'PROD': [s3_bucket: 'hrckollect-prod-fe', cloudfront_id: 'E1RYWTTS4NPREW']
        ],
        'HRC-Kollect-Cron': [
            'TEST': [ip: '10.2.30.254', path: '/opt/hrc-kollect-test/cron', app: 'hrc-kollect-test-cron', ssh: 'HRC-Kollect-Test-Server', secret: 'arn:aws:secretsmanager:us-east-1:167121004129:secret:dev.hrckollect.com-tDjPIP'],
            'DEV' : [ip: '10.2.40.133', path: '/opt/hrc-kollect-dev/cron', app: 'hrc-kollect-dev-cron', ssh: 'HRC-Kollect-Dev-Server', secret: 'arn:aws:secretsmanager:us-east-1:167121004129:secret:dev.hrckollect.com-tDjPIP'],
            'QA'  : [ip: '10.2.10.111', path: '/opt/hrc-kollect-qa/cron', app: 'hrc-kollect-qa-cron', ssh: 'HRC-Kollect-QA-Server', secret: 'arn:aws:secretsmanager:us-east-1:167121004129:secret:qa-hrckollect-com-0mHt87'],
            'DEMO': [ip: '10.2.40.238', path: '/opt/hrc-kollect-demo/cron', app: 'hrc-kollect-demo-cron', ssh: 'HRC-Kollect-Demo-Server', secret: 'arn:aws:secretsmanager:us-east-1:167121004129:secret:demo-new-hrckollect-com-KrSHaN'],
            'STG' : [ip: '10.2.40.201', path: '/opt/hrc-kollect-stg', app: 'hrc-kollect-stg-cron', ssh: 'HRC-Kollect-Stg-Server-Cron', secret: 'arn:aws:secretsmanager:us-east-1:167121004129:secret:stg-hrckollect-com-oLPno0'],
            'PROD': [ip: '10.2.10.254', path: '/opt/hrc-kollect-prod', app: 'hrc-kollect-prod-cron', ssh: 'HRC-Kollect-PROD-Server-Cron', secret: 'arn:aws:secretsmanager:us-east-1:167121004129:secret:portal.hrckollect.com-xvlUjU']
        ],
        'HRC-Kollect-Client-Billing-BE': [
            'TEST': [ip: '10.2.30.254', path: '/opt/hrc-kollect-test/client-billing-backend', app: 'hrc-kollect-client-billing-backend-test', ssh: 'HRC-Kollect-Test-Server', secret: 'arn:aws:secretsmanager:us-east-1:167121004129:secret:dev.hrckollect.com-tDjPIP'],
            'DEV' : [ip: '10.2.40.133', path: '/opt/hrc-kollect-dev/client-billing-backend', app: 'hrc-kollect-client-billing-backend-dev', ssh: 'HRC-Kollect-Dev-Server', secret: 'arn:aws:secretsmanager:us-east-1:167121004129:secret:dev.hrckollect.com-tDjPIP'],
            'QA'  : [ip: '10.2.10.111', path: '/opt/hrc-kollect-qa/client-billing-backend', app: 'hrc-kollect-client-billing-backend-qa', ssh: 'HRC-Kollect-QA-Server', secret: 'arn:aws:secretsmanager:us-east-1:167121004129:secret:qa-hrckollect-com-0mHt87'],
            'DEMO': [ip: '10.2.40.238', path: '/opt/hrc-kollect-demo/client-billing-backend', app: 'hrc-kollect-client-billing-backend-demo', ssh: 'HRC-Kollect-Demo-Server', secret: 'arn:aws:secretsmanager:us-east-1:167121004129:secret:demo-new-hrckollect-com-KrSHaN'],
            'STG' : [ip: '10.2.40.170', path: '/opt/client-billing-backend', app: 'hrc-kollect-client-billing-backend-stg', ssh: 'HRC-Kollect-Stg-Server', secret: 'arn:aws:secretsmanager:us-east-1:167121004129:secret:stg-hrckollect-com-oLPno0'],
            'PROD': [ip: '10.2.10.118', path: '/opt/client-billing-backend', app: 'hrc-kollect-client-billing-backend-prod', ssh: 'HRC-Kollect-PROD-BE', secret: 'arn:aws:secretsmanager:us-east-1:167121004129:secret:portal.hrckollect.com-xvlUjU']
        ],
        'HRC-Kollect-Client-Billing-FE': [
            'TEST': [s3_bucket: 'hrc-kollect-client-billing-test-fe', cloudfront_id: 'E1RYWTTS4NPREW'],
            'DEV' : [s3_bucket: 'hrc-kollect-client-billing-dev-fe', cloudfront_id: 'E1RYWTTS4NPREW'],
            'QA'  : [s3_bucket: 'hrc-kollect-client-billing-qa-fe', cloudfront_id: 'E1RYWTTS4NPREW'],
            'DEMO': [s3_bucket: 'hrc-kollect-client-billing-demo-fe', cloudfront_id: 'E1RYWTTS4NPREW'],
            'STG' : [s3_bucket: 'hrc-kollect-client-billing-stg-fe', cloudfront_id: 'E1RYWTTS4NPREW'],
            'PROD': [s3_bucket: 'hrc-kollect-client-billing-prod-fe', cloudfront_id: 'E1RYWTTS4NPREW']
        ],
        'HRC-Kollect-frontdesk-backend-BE': [
            'TEST': [ip: '10.2.30.254', path: '/opt/hrc-kollect-test/frontdesk-backend', app: 'hrc-kollect-frontdesk-backend-test', ssh: 'HRC-Kollect-Test-Server', secret: 'arn:aws:secretsmanager:us-east-1:167121004129:secret:dev.hrckollect.com-tDjPIP'],
            'DEV' : [ip: '10.2.40.133', path: '/opt/hrc-kollect-dev/frontdesk-backend', app: 'hrc-kollect-frontdesk-backend-dev', ssh: 'HRC-Kollect-Dev-Server', secret: 'arn:aws:secretsmanager:us-east-1:167121004129:secret:dev.hrckollect.com-tDjPIP'],
            'QA'  : [ip: '10.2.10.111', path: '/opt/hrc-kollect-qa/frontdesk-backend', app: 'hrc-kollect-frontdesk-backend-qa', ssh: 'HRC-Kollect-QA-Server', secret: 'arn:aws:secretsmanager:us-east-1:167121004129:secret:qa-hrckollect-com-0mHt87'],
            'DEMO': [ip: '10.2.40.238', path: '/opt/hrc-kollect-demo/frontdesk-backend', app: 'hrc-kollect-frontdesk-backend-demo', ssh: 'HRC-Kollect-Demo-Server', secret: 'arn:aws:secretsmanager:us-east-1:167121004129:secret:demo-new-hrckollect-com-KrSHaN'],
            'STG' : [ip: '10.2.40.170', path: '/opt/hrc-kollect-stg/frontdesk-backend', app: 'hrc-kollect-frontdesk-backend-stg', ssh: 'HRC-Kollect-Stg-Server', secret: 'arn:aws:secretsmanager:us-east-1:167121004129:secret:stg-hrckollect-com-oLPno0'],
            'PROD': [ip: '10.2.10.118', path: '/opt/hrc-kollect-prod/frontdesk-backend', app: 'hrc-kollect-frontdesk-backend-prod', ssh: 'HRC-Kollect-PROD-Server', secret: 'arn:aws:secretsmanager:us-east-1:167121004129:secret:portal.hrckollect.com-xvlUjU']
        ],
        'HRC-Kollect-Pay-Portal-Backend': [
            'TEST': [ip: '10.2.30.254', path: '/opt/hrc-kollect-test/pay-portal-backend', app: 'hrc-kollect-pay-portal-backend-test', ssh: 'HRC-Kollect-Test-Server', secret: 'arn:aws:secretsmanager:us-east-1:167121004129:secret:dev.hrckollect.com-tDjPIP'],
            'DEV' : [ip: '10.2.40.133', path: '/opt/hrc-kollect-dev/pay-portal-backend', app: 'hrc-kollect-pay-portal-backend-dev', ssh: 'HRC-Kollect-Dev-Server', secret: 'arn:aws:secretsmanager:us-east-1:167121004129:secret:dev.hrckollect.com-tDjPIP'],
            'QA'  : [ip: '10.2.10.111', path: '/opt/hrc-kollect-qa/pay-portal-backend', app: 'hrc-kollect-pay-portal-backend-qa', ssh: 'HRC-Kollect-QA-Server', secret: 'arn:aws:secretsmanager:us-east-1:167121004129:secret:qa-hrckollect-com-0mHt87'],
            'DEMO': [ip: '10.2.40.238', path: '/opt/hrc-kollect-demo/pay-portal-backend', app: 'hrc-kollect-pay-portal-backend-demo', ssh: 'HRC-Kollect-Demo-Server', secret: 'arn:aws:secretsmanager:us-east-1:167121004129:secret:demo-new-hrckollect-com-KrSHaN'],
            'STG' : [ip: '10.2.40.170', path: '/opt/hrc-kollect-stg/pay-portal-backend', app: 'hrc-kollect-pay-portal-backend-stg', ssh: 'HRC-Kollect-Stg-Server', secret: 'arn:aws:secretsmanager:us-east-1:167121004129:secret:stg-hrckollect-com-oLPno0'],
            'PROD': [ip: '10.2.10.118', path: '/opt/hrc-kollect-prod/pay-portal-backend', app: 'hrc-kollect-pay-portal-backend-prod', ssh: 'HRC-Kollect-PROD-Server', secret: 'arn:aws:secretsmanager:us-east-1:167121004129:secret:portal.hrckollect.com-xvlUjU']
        ],
        'HRC-Kollect-Reporting-Module': [
            'TEST': [ip: '10.2.30.254', ssh: 'HRC-Kollect-Test-Server', app: 'kollect-reporting-module-test', ecr: 'hrc-kollect-cicd/apps', ecr_account: '167121004129', ecr_region: 'us-east-1', container_port: 8080, host_port: 8080],
            //'DEV' : [ip: '10.2.40.133', ssh: 'HRC-Kollect-Dev-Server', app: 'kollect-reporting-module-dev', ecr: 'hrc-kollect-cicd/apps', ecr_account: '167121004129', ecr_region: 'us-east-1', container_port: 8080, host_port: 8080],
            //'QA'  : [ip: '10.2.10.111', ssh: 'HRC-Kollect-QA-Server', app: 'kollect-reporting-module-qa', ecr: 'hrc-kollect-cicd/apps', ecr_account: '167121004129', ecr_region: 'us-east-1', container_port: 8080, host_port: 8080],
            //'DEMO': [ip: '10.2.40.238', ssh: 'HRC-Kollect-Demo-Server', app: 'kollect-reporting-module-demo', ecr: 'hrc-kollect-cicd/apps', ecr_account: '167121004129', ecr_region: 'us-east-1', container_port: 8080, host_port: 8080],
            //'STG' : [ip: '10.2.40.170', ssh: 'HRC-Kollect-Stg-Server', app: 'kollect-reporting-module-stg', ecr: 'hrc-kollect-cicd/apps', ecr_account: '167121004129', ecr_region: 'us-east-1', container_port: 8080, host_port: 8080],
            //'PROD': [ip: '10.2.10.118', ssh: 'HRC-Kollect-PROD-Server', app: 'kollect-reporting-module-prod', ecr: 'hrc-kollect-cicd/apps', ecr_account: '167121004129', ecr_region: 'us-east-1', container_port: 8080, host_port: 8080]
        ],
        'HRC-AMD-Sync-Service': [
            'TEST': [ip: '10.2.30.254', ssh: 'HRC-Kollect-Test-Server', app: 'hrc-amd-sync-service-test', ecr: 'hrc-kollect-cicd/apps', ecr_account: '167121004129', ecr_region: 'us-east-1', container_port: 8082, host_port: 8182],
            //'DEV' : [ip: '10.2.40.133', ssh: 'HRC-Kollect-Dev-Server',  app: 'hrc-amd-sync-service-dev',  ecr: 'hrc-amd-sync-service-dev',  ecr_account: '167121004129', ecr_region: 'us-east-1', container_port: 8082, host_port: 8182],
            //'QA'  : [ip: '10.2.10.111', ssh: 'HRC-Kollect-QA-Server',   app: 'hrc-amd-sync-service-qa',   ecr: 'hrc-amd-sync-service-qa',   ecr_account: '167121004129', ecr_region: 'us-east-1', container_port: 8082, host_port: 8182],
            //'DEMO': [ip: '10.2.1.23',   ssh: 'HRC-AMD-Kollect-Mediator-DEV', app: 'hrc-amd-sync-service-demo', ecr: 'hrc-amd-sync-service-qa', ecr_account: '167121004129', ecr_region: 'us-east-1', container_port: 8082, host_port: 8182],
            //'STG' : [ip: '10.2.40.170', ssh: 'HRC-Kollect-Stg-Server',  app: 'hrc-amd-sync-service-stg',  ecr: 'hrc-amd-sync-service-stg',  ecr_account: '167121004129', ecr_region: 'us-east-1', container_port: 8082, host_port: 8182],
            //'PROD': [ip: '10.2.10.118', ssh: 'HRC-Kollect-PROD-Server', app: 'hrc-amd-sync-service-prod', ecr: 'hrc-amd-sync-service-prod', ecr_account: '167121004129', ecr_region: 'us-east-1', container_port: 8082, host_port: 8182]
        ],

        // NON-HEALTHCARE JOBS
        'Non-Healthcare-BE': [
            'TEST': [ip: '10.2.30.254', path: '/opt/non-healthcare-test/backend', app: 'non-healthcare-test-backend', ssh: 'HRC-Kollect-Test-Server', secret: 'arn:aws:secretsmanager:us-east-1:167121004129:secret:dev.hrckollect.com-tDjPIP'],
            'DEV' : [ip: '10.2.40.183', path: '/opt/non-healthcare-dev/backend', app: 'non-healthcare-dev-backend', ssh: 'Non-Healthcare-dev-server', secret: 'arn:aws:secretsmanager:us-east-1:167121004129:secret:dev.nonhealthcare.com-kg1Ktx'],
            'QA'  : [ip: '10.2.40.239', path: '/opt/non-healthcare-qa/backend', app: 'non-healthcare-qa-backend', ssh: 'Non-Healthcare-qa-server', secret: 'arn:aws:secretsmanager:us-east-1:167121004129:secret:qa.nonhealthcare.com-3xKQQa'],
            'DEMO': [ip: '10.2.40.110', path: '/opt/non-healthcare-demo/backend', app: 'non-healthcare-demo-backend', ssh: 'Non-Healthcare-demo-server', secret: 'arn:aws:secretsmanager:us-east-1:167121004129:secret:demo.nonhealthcare.com-fpjxjz']
        ],
        'Non-Healthcare-Cron': [
            'DEV' : [ip: '10.2.40.183', path: '/opt/non-healthcare-dev/cron', app: 'non-healthcare-dev-cron', ssh: 'Non-Healthcare-dev-server', secret: 'arn:aws:secretsmanager:us-east-1:167121004129:secret:dev.nonhealthcare.com-kg1Ktx'],
            'QA'  : [ip: '10.2.40.239', path: '/opt/non-healthcare-qa/cron', app: 'non-healthcare-qa-cron', ssh: 'Non-Healthcare-qa-server', secret: 'arn:aws:secretsmanager:us-east-1:167121004129:secret:qa.nonhealthcare.com-3xKQQa'],
            'DEMO': [ip: '10.2.40.110', path: '/opt/non-healthcare-demo/cron', app: 'non-healthcare-demo-cron', ssh: 'Non-Healthcare-demo-server', secret: 'arn:aws:secretsmanager:us-east-1:167121004129:secret:demo.nonhealthcare.com-fpjxjz']
        ],
        'Non-Healthcare-FE': [
            'DEV' : [s3_bucket: 'non-healthcare-kollect-dev-fe', cloudfront_id: 'EFTZC3GRKKII0'],
            'QA'  : [s3_bucket: 'non-healthcare-kollect-qa-fe', cloudfront_id: 'E2H5KARQB4008Q'],
            'DEMO': [s3_bucket: 'non-healthcare-kollect-demo-fe', cloudfront_id: 'E2H5KARQB4008Q']
        ]
    ]

    // --------------------------------------------------------------------------
    // RETURN CONFIG MAP BASED ON TYPE (CI vs CD)
    // --------------------------------------------------------------------------
    if (type.toUpperCase() == 'CI') {
        def item = ciData[env] ?: ciData['TEST']
        return [
            artifact_s3_bucket_name: item.bucket,
            artifact_s3_keypath    : appName,
            aws_region             : item.region,
            credentials_id         : item.creds,
            ecr_region             : item.ecr_region,
            account_id             : item.account_id
        ]
    } else {
        def appMap = cdData[appName] ?: [:]
        def item   = appMap[env] ?: appMap['DEV'] ?: appMap['TEST'] ?: [:]
        if (item.s3_bucket) {
            return item
        }
        return [
            server_ip      : item.ip,
            deploy_dir     : item.path,
            app_name       : item.app,
            ssh_creds      : item.ssh,
            secret_arn     : item.secret,
            ecr_repo       : item.ecr,
            ecr_account    : item.ecr_account,
            ecr_region     : item.ecr_region,
            container_port : item.container_port,
            host_port      : item.host_port
        ]
    }
}

// Convenience Shortcuts for Jenkinsfiles
def ci(String appName, String envName = 'TEST') {
    return call('CI', appName, envName)
}

def cd(String appName, String envName = 'TEST') {
    return call('CD', appName, envName)
}

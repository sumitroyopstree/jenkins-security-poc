package opstree.common

class inventory implements Serializable {

    Map getCiConfig(String appName, String envName = 'TEST') {
        def targetEnv = (envName ?: 'TEST').toUpperCase()
        def defaultBucket = 'hrc-cicd-test-bucket'
        def defaultRegion = 'us-east-1'
        def defaultCreds  = 'hrc-aws-ecr-credentials'

        def envMap = [
            'TEST': [artifact_s3_bucket_name: defaultBucket, artifact_s3_keypath: appName, aws_region: defaultRegion, credentials_id: defaultCreds],
            'DEV' : [artifact_s3_bucket_name: defaultBucket, artifact_s3_keypath: appName, aws_region: defaultRegion, credentials_id: defaultCreds],
            'QA'  : [artifact_s3_bucket_name: defaultBucket, artifact_s3_keypath: appName, aws_region: defaultRegion, credentials_id: defaultCreds],
            'DEMO': [artifact_s3_bucket_name: defaultBucket, artifact_s3_keypath: appName, aws_region: defaultRegion, credentials_id: defaultCreds],
            'STG' : [artifact_s3_bucket_name: defaultBucket, artifact_s3_keypath: appName, aws_region: defaultRegion, credentials_id: defaultCreds],
            'PROD': [artifact_s3_bucket_name: defaultBucket, artifact_s3_keypath: appName, aws_region: defaultRegion, credentials_id: defaultCreds]
        ]

        return envMap[targetEnv] ?: envMap['TEST']
    }

    Map getCdConfig(String appName, String envName = 'TEST') {
        def targetEnv = (envName ?: 'TEST').toUpperCase()
        def servers = [
            'TEST': [server_ip: '10.2.30.254', ssh_creds: 'HRC-Kollect-Test-Server', secret_arn: 'arn:aws:secretsmanager:us-east-1:167121004129:secret:dev.hrckollect.com-tDjPIP'],
            'DEV' : [server_ip: '10.2.40.133', ssh_creds: 'HRC-Kollect-Dev-Server',  secret_arn: 'arn:aws:secretsmanager:us-east-1:167121004129:secret:dev.hrckollect.com-tDjPIP'],
            'QA'  : [server_ip: '10.2.10.111', ssh_creds: 'HRC-Kollect-QA-Server',   secret_arn: 'arn:aws:secretsmanager:us-east-1:167121004129:secret:qa-hrckollect-com-0mHt87'],
            'DEMO': [server_ip: '10.2.40.238', ssh_creds: 'HRC-Kollect-Demo-Server', secret_arn: 'arn:aws:secretsmanager:us-east-1:167121004129:secret:demo-new-hrckollect-com-KrSHaN'],
            'STG' : [server_ip: '10.2.40.170', ssh_creds: 'HRC-Kollect-Stg-Server',  secret_arn: 'arn:aws:secretsmanager:us-east-1:167121004129:secret:stg-hrckollect-com-oLPno0'],
            'PROD': [server_ip: '10.2.10.118', ssh_creds: 'HRC-Kollect-PROD-BE',     secret_arn: 'arn:aws:secretsmanager:us-east-1:167121004129:secret:portal.hrckollect.com-xvlUjU']
        ]

        def envServer = servers[targetEnv] ?: servers['TEST']
        def envLower  = targetEnv.toLowerCase()

        if (appName.contains('FE') || appName.contains('frontend')) {
            return [
                s3_bucket     : "hrckollect-${envLower}-fe",
                cloudfront_id : 'E1RYWTTS4NPREW'
            ]
        }

        String deploySubDir = 'backend'
        if (appName.contains('Cron') || appName.contains('scheduler')) {
            deploySubDir = 'scheduler'
        } else if (appName.contains('Client-Billing')) {
            deploySubDir = 'client-billing-backend'
        } else if (appName.contains('Frontdesk')) {
            deploySubDir = 'frontdesk-backend'
        } else if (appName.contains('Pay-Portal')) {
            deploySubDir = 'pay-portal-backend'
        } else if (appName.contains('Reporting')) {
            deploySubDir = 'reporting-backend'
        }

        String pm2AppName = "hrc-kollect-${deploySubDir}-${envLower}"

        return [
            server_ip  : envServer.server_ip,
            deploy_dir : "/opt/hrc-kollect-${envLower}/${deploySubDir}",
            app_name   : pm2AppName,
            ssh_creds  : envServer.ssh_creds,
            secret_arn : envServer.secret_arn
        ]
    }
}

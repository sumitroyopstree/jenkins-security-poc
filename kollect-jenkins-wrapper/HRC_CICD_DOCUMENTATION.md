# 🚀 HRC & Non-Healthcare CI/CD Architecture & Parameterized Pipeline Documentation

---

## 📌 1. Executive Summary & Objective

HRC aur Non-Healthcare pipelines ko **Single Parameterized Architecture Pattern** par consolidate kiya gaya hai:
1. **Single HRC Pipeline:** `CI/Jenkinsfile` aur `CD/Jenkinsfile` multiple separate pipelines ki jagah ek single unified pipeline use karte hain jo parameters ke through kisi bhi HRC service aur environment (DEV, DEMO, QA, STG, PROD) ko build aur deploy kar sakti hain.
2. **Dedicated Non-Healthcare Folder:** Non-Healthcare project ke liye ek alag top-level folder (`Non-Healthcare/`) banaya gaya hai jisme single parameterized `CI` aur `CD` Jenkinsfile shamil hain (DEV, QA, DEMO).
3. **Full Spectrum CI Security Checks:** Sabhi pipelines mein Gitleaks credential scanning, OWASP dependency checks, Unit testing, SonarQube code analysis, Docker image scanning, Email notifications, aur Automated CD triggers active rehte hain.
4. **Job DSL Automation:** `master.dsl`, `ci.groovy`, aur `cd.groovy` ke zariye Jenkins seed jobs automated hain.

---

## 📁 2. Project Directory Structure

```
hrc/
├── exting-pipeline/                             # Original Raw Input Pipelines & Inventory
│   ├── DEV/
│   ├── QA/
│   ├── demo/
│   ├── prod/
│   ├── stg/
│   └── inventry.csv                            # Source of Truth Inventory Matrix
│
├── jenkins-shared-libraries/                    # Reusable Shared Library Repo
│   └── src/opstree/
│       ├── ci/templates/                        # CI Templates (nodejs_ci, java_ci)
│       ├── cd/templates/                        # CD Templates (ssh_pm2, s3_fe, docker_ecr)
│       └── common/                              # Common modules
│
└── jenkins_wrapper/                             # Root Jenkins Configuration Repository
    ├── CI/
    │   └── Jenkinsfile                          # 1 Single Parameterized HRC CI Pipeline
    ├── CD/
    │   └── Jenkinsfile                          # 1 Single Parameterized HRC CD Pipeline
    │
    ├── Non-Healthcare/                          # Non-Healthcare Separate Directory
    │   ├── CI/
    │   │   └── Jenkinsfile                      # 1 Single Parameterized Non-Healthcare CI Pipeline
    │   └── CD/
    │       └── Jenkinsfile                      # 1 Single Parameterized Non-Healthcare CD Pipeline
    │
    ├── jenkins_seedjob/                         # Job DSL Seed Jobs
    │   ├── master.dsl                           # Root Seed Job Folder Definitions
    │   ├── CI/ci.groovy                         # CI Job Generator (HRC & Non-Healthcare)
    │   ├── CD/cd.groovy                         # CD Job Generator (HRC & Non-Healthcare)
    │   └── USER_MANAGEMENT/user_management.groovy
    │
    ├── user-onboarding/Jenkinsfile
    └── user-offboarding/Jenkinsfile
```

---

## 🏗️ 3. Service Inventory Matrix (`inventry.csv`)

| Domain | Service Name | Tech Stack | Deploy Target | Server IP / Bucket | Envs Supported |
|---|---|---|---|---|---|
| **HRC** | `HRC-Kollect-BE` | Node.js / Express | SSH + PM2 | `10.2.40.133` | DEV, DEMO, QA, STG, PROD |
| **HRC** | `HRC-Kollect-FE` | React SPA | AWS S3 + CloudFront | `s3://hrckollect-dev-fe` | DEV, DEMO, QA, STG, PROD |
| **HRC** | `HRC-Kollect-Cron` | Node.js Scheduler | SSH + PM2 | `10.2.40.133` | DEV, DEMO, QA, STG, PROD |
| **HRC** | `HRC-Kollect-Client-Billing-BE` | Node.js Backend | SSH + PM2 | `10.2.40.133` | DEV, DEMO, QA, STG, PROD |
| **HRC** | `HRC-Kollect-Client-Billing-FE` | Next.js SSR | SSH + PM2 | `10.2.40.133` | DEV, DEMO, QA, STG, PROD |
| **HRC** | `HRC-Kollect-frontdesk-backend-BE` | Node.js Backend | SSH + PM2 | `10.2.40.133` | DEV, QA |
| **HRC** | `HRC-Kollect-Pay-Portal-Backend` | Node.js Backend | SSH + PM2 | `10.2.40.133` | DEV, DEMO, QA, STG, PROD |
| **HRC** | `HRC-Kollect-Reporting-Module` | Java 17 Maven | Docker + ECR | `167121004129.dkr.ecr...` | DEV, DEMO, QA, STG, PROD |
| **HRC** | `HRC-AMD-Sync-Service` | Java 17 Maven | Docker + ECR | `167121004129.dkr.ecr...` | STG, QA, DEMO, PROD |
| **Non-Healthcare** | `Non-Healthcare-BE` | Node.js Backend | SSH + PM2 | `10.2.40.183` | TEST, DEV, QA, DEMO |
| **Non-Healthcare** | `Non-Healthcare-Cron` | Node.js Scheduler | SSH + PM2 | `10.2.40.183` | TEST, DEV, QA, DEMO |
| **Non-Healthcare** | `Non-Healthcare-FE` | React SPA | AWS S3 + CloudFront | `s3://non-healthcare-kollect-dev-fe` | TEST, DEV, QA, DEMO |

---

## 🎛️ 4. Standard Build Parameters

Har pipeline mein ye standard build parameters parameterized hain:
1. **`SERVICE_NAME` (Choice):** Specific service select karne ke liye.
2. **`ENVIRONMENT` (Choice):** HRC ke liye `['DEV', 'DEMO', 'QA', 'STG', 'PROD']` aur Non-Healthcare ke liye `['TEST', 'DEV', 'QA', 'DEMO']`.
3. **`BRANCH` (String):** Git branch override (Default `main`).
4. **`TRIGGER_CD` (Boolean):** CI success ke baad CD pipeline automatic trigger karne ke liye.

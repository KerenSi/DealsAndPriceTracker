# Deploying to AWS

One small EC2 host runs the whole stack with Docker Compose: Postgres, the Spring Boot
backend, the Next.js frontend and Caddy, which serves HTTPS with an automatic Let's Encrypt
certificate (browser push alerts need HTTPS). Only ports 80 and 443 are open; the backend
and database are reachable only from inside the host.

```
browser ──HTTPS──▶ Caddy ──▶ Next.js (frontend) ──▶ Spring Boot (backend, daily ingestion) ──▶ Postgres
                   └──────────────── one EC2 t3.small, Docker Compose ────────────────┘
GitHub Actions (push to main) ──▶ ECR images ──▶ SSM Run Command on the host
```

| Piece | Where |
| --- | --- |
| Infrastructure (EC2, Elastic IP, ECR, IAM, daily snapshots, parameters) | `deploy/terraform/` |
| Production stack | `deploy/compose.prod.yml`, `deploy/Caddyfile` |
| Rollout script run on the host | `deploy/remote-deploy.sh` |
| CI/CD | `.github/workflows/deploy.yml` |

## Monthly cost (rough, on-demand, eu-central-1 Frankfurt)

| Item | ≈ USD / month |
| --- | --- |
| EC2 t3.small (2 vCPU, 2 GiB) | 17 |
| 30 GB gp3 disk | 3 |
| Public IPv4 / Elastic IP | 3.65 |
| 7 daily disk snapshots (incremental) | < 1 |
| ECR image storage (last 10 images per repo) | < 1 |
| SSM, IAM, GitHub OIDC, Let's Encrypt, sslip.io | 0 |
| Data transfer (first 100 GB out free) | 0 |
| **Total** | **≈ 25** |

Claude API usage for the chat and deal checks is billed separately by Anthropic.
A managed setup (ECS Fargate or App Runner + RDS + load balancer) would be about
$60–80/month for the same app; the Terraform here can grow into that later.

## One-time setup

You need an AWS account, the AWS CLI logged in as an admin (`aws configure` or SSO), and
Terraform ≥ 1.6. The default region is `eu-central-1` (Frankfurt); change `region` in
`terraform.tfvars` to use another.

On an AWS **free plan** account (created on or after 15 July 2025), t3.small is free-tier
eligible and the usage above draws from the plan's $100–200 credits. The free plan ends
after 6 months or when the credits run out, whichever comes first; upgrade to a paid plan
before then to keep the site running.

```bash
cd deploy/terraform
cp terraform.tfvars.example terraform.tfvars   # optional: region, domain
terraform init
terraform apply
terraform output github_variables
```

Then:

1. **GitHub variables.** In the repository, *Settings → Secrets and variables → Actions →
   Variables*, add `AWS_REGION`, `AWS_DEPLOY_ROLE_ARN` and `AWS_INSTANCE_ID` from the output
   above. No AWS keys are stored in GitHub; the workflow uses OIDC.
2. **API keys** go straight into Parameter Store, never into git or Terraform:
   ```bash
   aws ssm put-parameter --region eu-central-1 --type SecureString \
     --name /dealstracker/ANTHROPIC_API_KEY --value 'sk-ant-...'
   ```
   Any parameter under `/dealstracker/` becomes an env var on the next deploy (e.g. VAPID
   keys for push later).
3. **Deploy.** Push to `main`, or run the *Deploy* workflow by hand. When it finishes,
   `terraform output url` is the live site.
4. **Load data once** (the daily 06:00 ingestion runs by itself after this):
   ```bash
   aws ssm start-session --target "$(terraform output -raw instance_id)"
   # on the host:
   cd /opt/dealstracker && source .env
   curl -X POST "localhost:8080/api/admin/backfill?days=45" -H "X-Admin-Token: $ADMIN_TOKEN"
   ```

### Custom domain

Without `domain`, the site is served at `https://<elastic-ip-with-dashes>.sslip.io`.
To use your own: set `domain = "deals.example.com"`, `terraform apply`, add an A record
pointing at `terraform output elastic_ip`, then redeploy.

## Day to day

- **Logs:** `aws ssm start-session --target <instance-id>`, then
  `cd /opt/dealstracker && docker compose -f compose.prod.yml logs -f backend`.
- **Backups:** the disk (including Postgres) is snapshotted daily at 01:00 UTC and kept
  for 7 days. Restore by creating a volume from a snapshot in the EC2 console.
- **Tear down:** `terraform destroy` deletes everything, including the database.
  Snapshots made by the lifecycle policy are kept until you delete them.
- `terraform.tfstate` holds the generated DB password and admin token. Keep it private,
  or move it to an S3 backend if more than one person runs Terraform.

variable "name" {
  description = "Prefix for every resource name."
  type        = string
  default     = "dealstracker"
}

variable "region" {
  description = "AWS region. Frankfurt by default; il-central-1 (Tel Aviv, opt-in) gives an Israeli IP if a retailer feed ever blocks foreign traffic."
  type        = string
  default     = "eu-central-1"
}

variable "instance_type" {
  description = "EC2 size. t3.small (2 vCPU, 2 GiB, free-tier eligible on AWS free plan accounts) fits Postgres + Spring Boot + Next.js + Caddy."
  type        = string
  default     = "t3.small"
}

variable "disk_size_gb" {
  description = "Root volume size; Postgres data lives here and is snapshotted daily."
  type        = number
  default     = 30
}

variable "domain" {
  description = "Hostname for the site, e.g. deals.example.com (point its A record at the elastic_ip output). Leave empty to use a free <ip>.sslip.io hostname."
  type        = string
  default     = ""
}

variable "github_oidc_subject_prefix" {
  description = "Start of the OIDC subject GitHub Actions presents for this repo. This repo uses GitHub's immutable subjects (owner and repo IDs included); check with `gh api repos/OWNER/REPO/actions/oidc/customization/sub` and use its sub_claim_prefix."
  type        = string
  default     = "repo:KerenSi@3074892/DealsAndPriceTracker@1416702214"
}

variable "github_deploy_branch" {
  description = "Branch whose pushes may deploy."
  type        = string
  default     = "main"
}

variable "create_github_oidc_provider" {
  description = "Set to false if this AWS account already has the token.actions.githubusercontent.com OIDC provider."
  type        = bool
  default     = true
}

variable "snapshot_retention_days" {
  description = "How many daily disk snapshots to keep."
  type        = number
  default     = 7
}

variable "name" {
  description = "Prefix for every resource name."
  type        = string
  default     = "dealstracker"
}

variable "region" {
  description = "AWS region. Tel Aviv keeps latency low and the server on an Israeli IP, which some retailer price feeds expect. It is an opt-in region: enable it under Account settings first, or pick e.g. eu-central-1."
  type        = string
  default     = "il-central-1"
}

variable "instance_type" {
  description = "EC2 size. t3.small (2 vCPU, 2 GiB) fits Postgres + Spring Boot + Next.js + Caddy."
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

variable "github_repo" {
  description = "owner/repo allowed to deploy through GitHub Actions OIDC (case-sensitive, as GitHub reports it)."
  type        = string
  default     = "KerenSi/DealsAndPriceTracker"
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

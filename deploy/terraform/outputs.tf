output "url" {
  description = "Where the app is served once the first deploy has run."
  value       = "https://${local.site_address}"
}

output "elastic_ip" {
  description = "Point your domain's A record here if you set var.domain."
  value       = aws_eip.web.public_ip
}

output "instance_id" {
  description = "Set as the AWS_INSTANCE_ID GitHub Actions variable."
  value       = aws_instance.web.id
}

output "github_deploy_role_arn" {
  description = "Set as the AWS_DEPLOY_ROLE_ARN GitHub Actions variable."
  value       = aws_iam_role.github_deploy.arn
}

output "ecr_registry" {
  value = local.registry
}

output "github_variables" {
  description = "Repository variables (Settings > Secrets and variables > Actions > Variables) the deploy workflow reads."
  value = {
    AWS_REGION          = var.region
    AWS_DEPLOY_ROLE_ARN = aws_iam_role.github_deploy.arn
    AWS_INSTANCE_ID     = aws_instance.web.id
  }
}

data "aws_caller_identity" "current" {}

data "aws_ssm_parameter" "al2023_ami" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64"
}

data "aws_vpc" "default" {
  default = true
}

locals {
  site_address = var.domain != "" ? var.domain : "${replace(aws_eip.web.public_ip, ".", "-")}.sslip.io"
  registry     = "${data.aws_caller_identity.current.account_id}.dkr.ecr.${var.region}.amazonaws.com"
  param_prefix = "/${var.name}"
}

# ---------- Container images ----------

resource "aws_ecr_repository" "app" {
  for_each             = toset(["backend", "frontend"])
  name                 = "${var.name}-${each.key}"
  image_tag_mutability = "MUTABLE"
  force_delete         = true
}

resource "aws_ecr_lifecycle_policy" "app" {
  for_each   = aws_ecr_repository.app
  repository = each.value.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep the last 10 images"
      selection    = { tagStatus = "any", countType = "imageCountMoreThan", countNumber = 10 }
      action       = { type = "expire" }
    }]
  })
}

# ---------- Network ----------

resource "aws_security_group" "web" {
  name        = "${var.name}-web"
  description = "HTTP/HTTPS in; no SSH (use SSM Session Manager)"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description = "HTTP for Lets Encrypt challenge and redirect"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTPS"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_eip" "web" {
  domain = "vpc"
  tags   = { Name = "${var.name}-web" }
}

resource "aws_eip_association" "web" {
  instance_id   = aws_instance.web.id
  allocation_id = aws_eip.web.id
}

# ---------- Host ----------

resource "aws_instance" "web" {
  ami                    = data.aws_ssm_parameter.al2023_ami.value
  instance_type          = var.instance_type
  iam_instance_profile   = aws_iam_instance_profile.web.name
  vpc_security_group_ids = [aws_security_group.web.id]
  user_data              = file("${path.module}/user_data.sh")

  metadata_options {
    http_tokens = "required"
  }

  root_block_device {
    volume_type = "gp3"
    volume_size = var.disk_size_gb
    encrypted   = true
  }

  volume_tags = {
    Name     = "${var.name}-data"
    Snapshot = var.name
  }

  tags = { Name = "${var.name}-web" }

  lifecycle {
    # A newer AMI must not silently replace the box (and its database).
    ignore_changes = [ami, user_data]
  }
}

# ---------- Daily disk snapshots ----------

resource "aws_iam_role" "dlm" {
  name = "${var.name}-dlm"
  assume_role_policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [{ Effect = "Allow", Principal = { Service = "dlm.amazonaws.com" }, Action = "sts:AssumeRole" }]
  })
}

resource "aws_iam_role_policy_attachment" "dlm" {
  role       = aws_iam_role.dlm.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSDataLifecycleManagerServiceRole"
}

resource "aws_dlm_lifecycle_policy" "daily" {
  description        = "${var.name} daily snapshots"
  execution_role_arn = aws_iam_role.dlm.arn
  state              = "ENABLED"

  policy_details {
    resource_types = ["VOLUME"]
    target_tags    = { Snapshot = var.name }

    schedule {
      name = "daily"
      create_rule {
        interval      = 24
        interval_unit = "HOURS"
        times         = ["01:00"]
      }
      retain_rule {
        count = var.snapshot_retention_days
      }
      copy_tags = true
    }
  }
}

# ---------- Configuration and secrets (SSM Parameter Store) ----------
# remote-deploy.sh turns every parameter under /<name>/ into an env var.
# Third-party keys (ANTHROPIC_API_KEY, VAPID keys, ...) are added by hand with
# `aws ssm put-parameter`, so they never pass through Terraform or git.

resource "random_password" "db" {
  length  = 32
  special = false
}

resource "random_password" "admin_token" {
  length  = 40
  special = false
}

resource "aws_ssm_parameter" "db_password" {
  name  = "${local.param_prefix}/DB_PASSWORD"
  type  = "SecureString"
  value = random_password.db.result
}

resource "aws_ssm_parameter" "admin_token" {
  name  = "${local.param_prefix}/ADMIN_TOKEN"
  type  = "SecureString"
  value = random_password.admin_token.result
}

resource "aws_ssm_parameter" "site_address" {
  name  = "${local.param_prefix}/SITE_ADDRESS"
  type  = "String"
  value = local.site_address
}

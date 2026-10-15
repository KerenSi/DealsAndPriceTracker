# ---------- Operator access for teammates ----------
# Attach this policy to a teammate's IAM user to let them run this project without touching
# anything else in the account: open a shell on the app server (Session Manager), start/stop/
# reboot it, and read or change the app's settings under /<name>/ in Parameter Store.
# Listing calls (ec2:Describe*, ssm:DescribeInstanceInformation) can't be limited by AWS, so
# other instances' names stay visible, but they can't be connected to or changed.

resource "aws_iam_policy" "operator" {
  name        = "${var.name}-operator"
  description = "Operate the ${var.name} app server and its settings only"
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "ListForConsole"
        Effect = "Allow"
        Action = [
          "ec2:Describe*",
          "ssm:DescribeInstanceInformation",
          "ssm:DescribeInstanceProperties",
          "ssm:DescribeSessions",
          "ssm:GetConnectionStatus",
          "ssm:DescribeParameters",
        ]
        Resource = "*"
      },
      {
        Sid      = "ShellOnProjectServer"
        Effect   = "Allow"
        Action   = "ssm:StartSession"
        Resource = "arn:aws:ec2:${var.region}:${data.aws_caller_identity.current.account_id}:instance/*"
        Condition = {
          StringEquals = { "ssm:resourceTag/Project" = var.name }
        }
      },
      {
        Sid      = "SessionDocument"
        Effect   = "Allow"
        Action   = "ssm:StartSession"
        Resource = "arn:aws:ssm:${var.region}:${data.aws_caller_identity.current.account_id}:document/SSM-SessionManagerRunShell"
      },
      {
        Sid      = "OwnSessions"
        Effect   = "Allow"
        Action   = ["ssm:ResumeSession", "ssm:TerminateSession"]
        Resource = "arn:aws:ssm:*:*:session/$${aws:username}-*"
      },
      {
        Sid      = "PowerProjectServer"
        Effect   = "Allow"
        Action   = ["ec2:StartInstances", "ec2:StopInstances", "ec2:RebootInstances"]
        Resource = "arn:aws:ec2:${var.region}:${data.aws_caller_identity.current.account_id}:instance/*"
        Condition = {
          StringEquals = { "aws:ResourceTag/Project" = var.name }
        }
      },
      {
        Sid    = "AppSettings"
        Effect = "Allow"
        Action = [
          "ssm:GetParameter", "ssm:GetParameters", "ssm:GetParametersByPath",
          "ssm:GetParameterHistory", "ssm:PutParameter", "ssm:DeleteParameter",
          "ssm:AddTagsToResource", "ssm:ListTagsForResource",
        ]
        Resource = [
          "arn:aws:ssm:${var.region}:${data.aws_caller_identity.current.account_id}:parameter${local.param_prefix}",
          "arn:aws:ssm:${var.region}:${data.aws_caller_identity.current.account_id}:parameter${local.param_prefix}/*",
        ]
      },
      {
        Sid    = "OwnPasswordAndMfa"
        Effect = "Allow"
        Action = ["iam:ChangePassword", "iam:GetUser", "iam:ListMFADevices", "iam:EnableMFADevice", "iam:ResyncMFADevice", "iam:CreateVirtualMFADevice"]
        Resource = [
          "arn:aws:iam::${data.aws_caller_identity.current.account_id}:user/$${aws:username}",
          "arn:aws:iam::${data.aws_caller_identity.current.account_id}:mfa/*",
        ]
      },
      {
        Sid      = "PasswordPolicy"
        Effect   = "Allow"
        Action   = ["iam:GetAccountPasswordPolicy", "iam:ListVirtualMFADevices"]
        Resource = "*"
      },
    ]
  })
}

terraform {
  required_version = ">= 1.6"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.70"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # State is local by default (terraform.tfstate in this folder, gitignored).
  # It holds the generated DB password and admin token, so keep it private.
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project = var.name
    }
  }
}

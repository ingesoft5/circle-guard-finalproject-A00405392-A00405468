terraform {
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }

  # Configuracion parcial: bucket, region y bloqueo vienen de infra/terraform/backend.hcl
  # (lo genera bootstrap/ y no se versiona, para no escribir la cuenta de AWS en el repo).
  backend "s3" {
    key = "envs/tools/terraform.tfstate"
  }
}

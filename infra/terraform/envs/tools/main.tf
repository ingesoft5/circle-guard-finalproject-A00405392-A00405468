provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project   = "circleguard"
      Env       = local.env
      ManagedBy = "terraform"
    }
  }
}

data "aws_caller_identity" "current" {}

locals {
  env  = "tools"
  name = "circleguard-${local.env}"
  # Una imagen por servicio y una para el cliente web (los mismos 9 nombres que publica el pipeline).
  repositories = [
    "circleguard-auth-service",
    "circleguard-dashboard-service",
    "circleguard-file-service",
    "circleguard-form-service",
    "circleguard-gateway-service",
    "circleguard-identity-service",
    "circleguard-notification-service",
    "circleguard-promotion-service",
    "circleguard-web",
  ]
  ecr_repositories_arn = "arn:aws:ecr:${var.region}:${data.aws_caller_identity.current.account_id}:repository/circleguard-*"
}

resource "aws_key_pair" "this" {
  key_name   = "${local.name}-key"
  public_key = var.ssh_public_key
}

module "network" {
  source = "../../modules/network"

  name        = local.name
  vpc_cidr    = "10.0.0.0/16"
  subnet_cidr = "10.0.1.0/24"

  ingress_rules = [
    { port = 22, cidrs = var.admin_cidrs, description = "SSH desde administracion" },
    { port = 8080, cidrs = ["0.0.0.0/0"], description = "Jenkins: webhook de GitHub y acceso del docente" },
    { port = 9000, cidrs = ["0.0.0.0/0"], description = "SonarQube: quality gate visible para el docente" },
    { port = 8081, cidrs = ["0.0.0.0/0"], description = "Nexus: artefactos visibles para el docente" },
  ]
}

module "compute" {
  source = "../../modules/compute"

  # El nombre lleva -vm para no confundir esta VM con la de Bash (circleguard-tools):
  # bootstrap-cloud.sh busca instancias por la etiqueta Name.
  name              = "${local.name}-vm"
  role              = "tools"
  instance_type     = var.instance_type
  disk_gb           = var.disk_gb
  subnet_id         = module.network.subnet_id
  security_group_id = module.network.security_group_id
  key_name          = aws_key_pair.this.key_name

  # Jenkins construye imagenes: sube y baja de los repositorios del proyecto.
  iam_policy_statements = [
    { actions = ["ecr:GetAuthorizationToken"], resources = ["*"] },
    {
      actions = [
        "ecr:BatchCheckLayerAvailability",
        "ecr:BatchGetImage",
        "ecr:GetDownloadUrlForLayer",
        "ecr:InitiateLayerUpload",
        "ecr:UploadLayerPart",
        "ecr:CompleteLayerUpload",
        "ecr:PutImage",
        "ecr:DescribeImages",
        "ecr:DescribeRepositories",
        "ecr:ListImages",
      ]
      resources = [local.ecr_repositories_arn]
    },
  ]
}

module "registry" {
  source = "../../modules/registry"

  repositories = local.repositories
}

module "observability" {
  source = "../../modules/observability"

  name           = local.name
  instance_id    = module.compute.instance_id
  alert_email    = var.alert_email
  log_group_name = "/circleguard/${local.env}"
}

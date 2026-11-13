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
  env  = "stage"
  name = "circleguard-${local.env}"
  # SSH y API de Kubernetes: administracion y tools, sin repetir CIDR (la llave de cada regla es puerto + CIDR).
  trusted_cidrs = distinct(concat(var.admin_cidrs, [var.tools_cidr]))
  # Los nodos solo descargan imagenes de los repositorios del proyecto.
  ecr_repositories_arn = "arn:aws:ecr:${var.region}:${data.aws_caller_identity.current.account_id}:repository/circleguard-*"
}

resource "aws_key_pair" "this" {
  key_name   = "${local.name}-key"
  public_key = var.ssh_public_key
}

module "network" {
  source = "../../modules/network"

  name        = local.name
  vpc_cidr    = "10.20.0.0/16"
  subnet_cidr = "10.20.1.0/24"

  ingress_rules = [
    { port = 22, cidrs = local.trusted_cidrs, description = "SSH desde administracion y tools" },
    { port = 6443, cidrs = local.trusted_cidrs, description = "API de Kubernetes desde administracion y tools" },
    { port = 80, cidrs = ["0.0.0.0/0"], description = "HTTP del Ingress" },
    { port = 443, cidrs = ["0.0.0.0/0"], description = "HTTPS del Ingress" },
  ]
}

module "compute" {
  source = "../../modules/compute"

  # El nombre lleva el rol (-k3s) para no confundir esta VM con la de Bash (circleguard-stage):
  # bootstrap-cloud.sh busca instancias por la etiqueta Name.
  name              = "${local.name}-k3s"
  role              = "k3s"
  instance_type     = var.instance_type
  disk_gb           = var.disk_gb
  subnet_id         = module.network.subnet_id
  security_group_id = module.network.security_group_id
  key_name          = aws_key_pair.this.key_name
  k3s_version       = var.k3s_version

  iam_policy_statements = [
    { actions = ["ecr:GetAuthorizationToken"], resources = ["*"] },
    {
      actions   = ["ecr:BatchGetImage", "ecr:GetDownloadUrlForLayer", "ecr:BatchCheckLayerAvailability"]
      resources = [local.ecr_repositories_arn]
    },
  ]
}

module "storage" {
  source = "../../modules/storage"

  name = "${local.name}-data-${data.aws_caller_identity.current.account_id}"
}

module "observability" {
  source = "../../modules/observability"

  name           = local.name
  instance_id    = module.compute.instance_id
  alert_email    = var.alert_email
  log_group_name = "/circleguard/${local.env}"
}

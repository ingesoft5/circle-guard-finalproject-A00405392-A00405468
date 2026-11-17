resource "aws_vpc" "this" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = { Name = "${var.name}-vpc" }
}

resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id

  tags = { Name = "${var.name}-igw" }
}

resource "aws_subnet" "public" {
  vpc_id                  = aws_vpc.this.id
  cidr_block              = var.subnet_cidr
  availability_zone       = var.availability_zone
  map_public_ip_on_launch = false # la IP publica llega por la IP elastica del modulo compute

  tags = { Name = "${var.name}-public" }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.this.id
  }

  tags = { Name = "${var.name}-public" }
}

resource "aws_route_table_association" "public" {
  subnet_id      = aws_subnet.public.id
  route_table_id = aws_route_table.public.id
}

resource "aws_security_group" "this" {
  name        = "${var.name}-sg"
  description = "Security group de ${var.name}"
  vpc_id      = aws_vpc.this.id

  tags = { Name = "${var.name}-sg" }
}

locals {
  # Una regla por (puerto, CIDR): for_each necesita llaves estables.
  ingress = merge([
    for r in var.ingress_rules : {
      for cidr in r.cidrs : "${r.port}-${cidr}" => {
        port        = r.port
        cidr        = cidr
        description = r.description
      }
    }
  ]...)
}

# Decision D2-08: el Ingress de la app (80 y 443) y los puertos de las herramientas
# (Jenkins, SonarQube, Nexus) se abren al mundo a proposito; SSH y el API de Kubernetes
# solo se abren a los CIDR de administracion y de tools que pasa cada ambiente.
# trivy:ignore:AVD-AWS-0107
resource "aws_vpc_security_group_ingress_rule" "this" {
  for_each = local.ingress

  security_group_id = aws_security_group.this.id
  ip_protocol       = "tcp"
  from_port         = each.value.port
  to_port           = each.value.port
  cidr_ipv4         = each.value.cidr
  description       = each.value.description
}

# Salida abierta: el nodo descarga paquetes, k3s e imagenes. No hay NAT Gateway (D2-08).
# trivy:ignore:AVD-AWS-0104
resource "aws_vpc_security_group_egress_rule" "all" {
  security_group_id = aws_security_group.this.id
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
  description       = "Salida abierta: paquetes, k3s e imagenes"
}

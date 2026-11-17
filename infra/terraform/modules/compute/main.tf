# AMI Ubuntu 24.04 LTS oficial de Canonical, igual que infra/bootstrap/config.sh.
data "aws_ssm_parameter" "ubuntu_ami" {
  name = "/aws/service/canonical/ubuntu/server/24.04/stable/current/amd64/hvm/ebs-gp3/ami-id"
}

# La IP elastica se crea antes que la VM para poder usarla en el certificado de k3s.
resource "aws_eip" "this" {
  domain = "vpc"

  tags = { Name = "${var.name}-eip" }
}

resource "aws_iam_role" "this" {
  name = "${var.name}-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRole"
      Principal = { Service = "ec2.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy" "extra" {
  count = length(var.iam_policy_statements) > 0 ? 1 : 0

  name = "${var.name}-extra"
  role = aws_iam_role.this.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [for s in var.iam_policy_statements : {
      Effect   = "Allow"
      Action   = s.actions
      Resource = s.resources
    }]
  })
}

resource "aws_iam_instance_profile" "this" {
  name = "${var.name}-profile"
  role = aws_iam_role.this.name
}

locals {
  user_data = {
    k3s = templatefile("${path.module}/templates/k3s.sh.tftpl", {
      hostname    = var.name
      k3s_version = var.k3s_version
      public_ip   = aws_eip.this.public_ip
    })
    tools = templatefile("${path.module}/templates/tools.sh.tftpl", {
      hostname = var.name
    })
  }[var.role]
}

resource "aws_instance" "this" {
  ami                    = data.aws_ssm_parameter.ubuntu_ami.insecure_value
  instance_type          = var.instance_type
  subnet_id              = var.subnet_id
  vpc_security_group_ids = [var.security_group_id]
  key_name               = var.key_name
  iam_instance_profile   = aws_iam_instance_profile.this.name
  user_data              = local.user_data

  metadata_options {
    http_endpoint = "enabled"
    http_tokens   = "required" # IMDSv2 obligatorio
  }

  root_block_device {
    volume_type = "gp3"
    volume_size = var.disk_gb
    encrypted   = true
  }

  tags = {
    Name = var.name
    Role = var.role
  }

  lifecycle {
    # Canonical publica AMI nuevas todo el tiempo; no queremos reemplazar la VM (ni ensuciar el plan) por eso.
    ignore_changes = [ami]
  }
}

resource "aws_eip_association" "this" {
  instance_id   = aws_instance.this.id
  allocation_id = aws_eip.this.id
}

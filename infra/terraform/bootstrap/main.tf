provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project   = "circleguard"
      Env       = "bootstrap"
      ManagedBy = "terraform"
    }
  }
}

data "aws_caller_identity" "current" {}

locals {
  bucket_name = "circleguard-tfstate-${data.aws_caller_identity.current.account_id}"
}

# Cifrado con SSE-S3 (AES256): una llave KMS propia agrega costo y complejidad sin ganar nada aqui.
# Sin bucket de logs de acceso: es un bucket de estado de 2 personas y los logs agregarian otro bucket que cuidar.
# trivy:ignore:AVD-AWS-0132
# trivy:ignore:AVD-AWS-0089
resource "aws_s3_bucket" "state" {
  bucket = local.bucket_name

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_s3_bucket_versioning" "state" {
  bucket = aws_s3_bucket.state.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "state" {
  bucket = aws_s3_bucket.state.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "state" {
  bucket = aws_s3_bucket.state.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_policy" "state" {
  bucket = aws_s3_bucket.state.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "SoloTLS"
      Effect    = "Deny"
      Principal = "*"
      Action    = "s3:*"
      Resource  = [aws_s3_bucket.state.arn, "${aws_s3_bucket.state.arn}/*"]
      Condition = { Bool = { "aws:SecureTransport" = "false" } }
    }]
  })

  depends_on = [aws_s3_bucket_public_access_block.state]
}

# Cifrado por defecto de DynamoDB (llave de AWS): la tabla solo guarda un LockID, no datos sensibles.
# trivy:ignore:AVD-AWS-0025
resource "aws_dynamodb_table" "lock" {
  name         = "circleguard-tflock"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "LockID"

  attribute {
    name = "LockID"
    type = "S"
  }

  point_in_time_recovery {
    enabled = true
  }

  lifecycle {
    prevent_destroy = true
  }
}

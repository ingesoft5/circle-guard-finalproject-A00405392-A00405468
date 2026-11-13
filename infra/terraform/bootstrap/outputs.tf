output "bucket" {
  description = "Nombre del bucket S3 del estado."
  value       = aws_s3_bucket.state.id
}

output "lock_table" {
  description = "Nombre de la tabla DynamoDB del bloqueo."
  value       = aws_dynamodb_table.lock.name
}

output "backend_config" {
  description = "Contenido de infra/terraform/backend.hcl: configuracion parcial del backend S3 de cada ambiente."
  value       = <<-EOT
    bucket         = "${aws_s3_bucket.state.id}"
    region         = "${var.region}"
    dynamodb_table = "${aws_dynamodb_table.lock.name}"
    use_lockfile   = true
    encrypt        = true
  EOT
}

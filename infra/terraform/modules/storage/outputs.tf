output "bucket" {
  description = "Nombre del bucket."
  value       = aws_s3_bucket.this.id
}

output "arn" {
  description = "ARN del bucket."
  value       = aws_s3_bucket.this.arn
}

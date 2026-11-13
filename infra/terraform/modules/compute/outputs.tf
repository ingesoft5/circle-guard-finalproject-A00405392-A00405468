output "instance_id" {
  description = "ID de la instancia EC2."
  value       = aws_instance.this.id
}

output "public_ip" {
  description = "IP elastica de la VM."
  value       = aws_eip.this.public_ip
}

output "private_ip" {
  description = "IP privada de la VM dentro de su VPC."
  value       = aws_instance.this.private_ip
}

output "role_name" {
  description = "Nombre del rol IAM de la VM (para adjuntarle permisos desde fuera)."
  value       = aws_iam_role.this.name
}

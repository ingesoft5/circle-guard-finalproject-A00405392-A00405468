output "vpc_id" {
  description = "ID de la VPC."
  value       = aws_vpc.this.id
}

output "subnet_id" {
  description = "ID de la subred publica."
  value       = aws_subnet.public.id
}

output "security_group_id" {
  description = "ID del security group del ambiente."
  value       = aws_security_group.this.id
}

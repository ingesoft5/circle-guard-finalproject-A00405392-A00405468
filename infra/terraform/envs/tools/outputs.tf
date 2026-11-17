output "public_ip" {
  description = "IP elastica de la VM de herramientas."
  value       = module.compute.public_ip
}

output "private_ip" {
  description = "IP privada de la VM de herramientas."
  value       = module.compute.private_ip
}

output "instance_id" {
  description = "ID de la instancia EC2."
  value       = module.compute.instance_id
}

output "vpc_id" {
  description = "ID de la VPC de herramientas."
  value       = module.network.vpc_id
}

output "repository_urls" {
  description = "URL de cada repositorio ECR, por nombre."
  value       = module.registry.repository_urls
}

output "alerts_topic_arn" {
  description = "Tema SNS que recibe las alarmas."
  value       = module.observability.topic_arn
}

output "ssh" {
  description = "Comando para entrar a la VM."
  value       = "ssh -i infra/bootstrap/.state/circleguard-key.pem ubuntu@${module.compute.public_ip}"
}

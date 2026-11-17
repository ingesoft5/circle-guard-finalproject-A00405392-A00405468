output "public_ip" {
  description = "IP elastica del nodo."
  value       = module.compute.public_ip
}

output "private_ip" {
  description = "IP privada del nodo."
  value       = module.compute.private_ip
}

output "instance_id" {
  description = "ID de la instancia EC2."
  value       = module.compute.instance_id
}

output "vpc_id" {
  description = "ID de la VPC del ambiente."
  value       = module.network.vpc_id
}

output "data_bucket" {
  description = "Bucket S3 de respaldos y artefactos del ambiente."
  value       = module.storage.bucket
}

output "alerts_topic_arn" {
  description = "Tema SNS que recibe las alarmas."
  value       = module.observability.topic_arn
}

output "ssh" {
  description = "Comando para entrar al nodo."
  value       = "ssh -i infra/bootstrap/.state/circleguard-key.pem ubuntu@${module.compute.public_ip}"
}

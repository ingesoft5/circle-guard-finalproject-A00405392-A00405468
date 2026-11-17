output "repository_urls" {
  description = "URL de cada repositorio ECR, por nombre."
  value       = { for name, repo in aws_ecr_repository.this : name => repo.repository_url }
}

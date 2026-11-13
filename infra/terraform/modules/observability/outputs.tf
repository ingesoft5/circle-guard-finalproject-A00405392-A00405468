output "topic_arn" {
  description = "ARN del tema SNS que recibe los avisos de las alarmas."
  value       = aws_sns_topic.alerts.arn
}

output "log_group_name" {
  description = "Nombre del grupo de logs de CloudWatch."
  value       = aws_cloudwatch_log_group.this.name
}

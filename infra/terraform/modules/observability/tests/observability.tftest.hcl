# Pruebas del modulo observability. Usan un proveedor simulado: no tocan AWS.
mock_provider "aws" {}

# El proveedor simulado valida el formato de los ARN: se le da uno valido al tema.
override_resource {
  target = aws_sns_topic.alerts
  values = {
    arn = "arn:aws:sns:us-east-1:123456789012:circleguard-test-alerts"
  }
}

variables {
  name           = "circleguard-test"
  instance_id    = "i-0123456789abcdef0"
  alert_email    = "equipo@example.com"
  log_group_name = "/circleguard/test"
}

run "las_dos_alarmas_avisan_por_el_tema_sns" {
  command = apply

  assert {
    condition = alltrue([
      contains(tolist(aws_cloudwatch_metric_alarm.cpu.alarm_actions), aws_sns_topic.alerts.arn),
      contains(tolist(aws_cloudwatch_metric_alarm.status.alarm_actions), aws_sns_topic.alerts.arn),
    ])
    error_message = "Las alarmas de CPU y de chequeo de estado deben publicar en el tema SNS."
  }
}

run "la_alarma_de_cpu_salta_sobre_80" {
  command = apply

  assert {
    condition     = aws_cloudwatch_metric_alarm.cpu.threshold == 80 && aws_cloudwatch_metric_alarm.cpu.comparison_operator == "GreaterThanThreshold"
    error_message = "La alarma de CPU debe saltar cuando supera el 80 %."
  }
}

run "una_vm_apagada_no_dispara_alarmas" {
  command = apply

  assert {
    condition     = aws_cloudwatch_metric_alarm.status.treat_missing_data == "notBreaching" && aws_cloudwatch_metric_alarm.cpu.treat_missing_data == "notBreaching"
    error_message = "Sin datos (VM apagada) las alarmas no deben saltar."
  }
}

run "los_logs_tienen_retencion" {
  command = apply

  assert {
    condition     = aws_cloudwatch_log_group.this.retention_in_days == 14
    error_message = "Los logs deben conservarse 14 dias para controlar el costo."
  }
}

run "rechaza_un_correo_invalido" {
  command = plan

  variables {
    alert_email = "no-es-un-correo"
  }

  expect_failures = [var.alert_email]
}

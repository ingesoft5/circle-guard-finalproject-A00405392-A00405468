variable "name" {
  description = "Prefijo de los recursos, por ejemplo circleguard-dev."
  type        = string
}

variable "instance_id" {
  description = "ID de la instancia EC2 que se vigila."
  type        = string
}

variable "alert_email" {
  description = "Correo que recibe las alarmas (AWS pide confirmar la suscripcion una vez)."
  type        = string

  validation {
    condition     = can(regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", var.alert_email))
    error_message = "alert_email debe ser un correo valido."
  }
}

variable "log_group_name" {
  description = "Nombre del grupo de logs de CloudWatch, por ejemplo /circleguard/dev."
  type        = string
}

variable "retention_days" {
  description = "Dias que se conservan los logs."
  type        = number
  default     = 14
}

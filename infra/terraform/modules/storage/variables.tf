variable "name" {
  description = "Nombre global del bucket S3 (debe ser unico en todo AWS)."
  type        = string
}

variable "noncurrent_days" {
  description = "Dias que se conservan las versiones antiguas de un objeto antes de borrarlas."
  type        = number
  default     = 30
}

variable "repositories" {
  description = "Nombres de los repositorios ECR a crear, uno por imagen."
  type        = list(string)

  validation {
    condition     = length(var.repositories) > 0
    error_message = "repositories no puede estar vacio."
  }
}

variable "keep_last" {
  description = "Cuantas imagenes conserva cada repositorio; las mas viejas se borran solas."
  type        = number
  default     = 20
}

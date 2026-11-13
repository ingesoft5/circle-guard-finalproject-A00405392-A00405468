variable "region" {
  description = "Region de AWS."
  type        = string
  default     = "us-east-1"
}

variable "admin_cidrs" {
  description = "CIDR desde donde se administra la VM por SSH (tu IP publica con /32)."
  type        = list(string)

  validation {
    condition     = alltrue([for c in var.admin_cidrs : can(cidrnetmask(c))])
    error_message = "admin_cidrs debe contener CIDR IPv4 validos, por ejemplo 203.0.113.5/32."
  }
}

variable "ssh_public_key" {
  description = "Llave publica SSH autorizada en la VM (ssh-keygen -y -f infra/bootstrap/.state/circleguard-key.pem)."
  type        = string
}

variable "alert_email" {
  description = "Correo que recibe las alarmas de CloudWatch."
  type        = string
}

variable "instance_type" {
  description = "Tipo de instancia de la VM de herramientas."
  type        = string
  default     = "t3.xlarge"
}

variable "disk_gb" {
  description = "Tamano del disco raiz en GB."
  type        = number
  default     = 60
}

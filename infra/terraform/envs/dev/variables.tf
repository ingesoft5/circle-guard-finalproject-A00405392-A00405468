variable "region" {
  description = "Region de AWS."
  type        = string
  default     = "us-east-1"
}

variable "admin_cidrs" {
  description = "CIDR desde donde se administra la VM por SSH y por el API de Kubernetes (tu IP publica con /32)."
  type        = list(string)

  validation {
    condition     = alltrue([for c in var.admin_cidrs : can(cidrnetmask(c))])
    error_message = "admin_cidrs debe contener CIDR IPv4 validos, por ejemplo 203.0.113.5/32."
  }
}

variable "tools_cidr" {
  description = "IP elastica fija de la VM tools con /32: Jenkins despliega desde ahi."
  type        = string

  validation {
    condition     = can(cidrnetmask(var.tools_cidr))
    error_message = "tools_cidr debe ser un CIDR IPv4 valido, por ejemplo 203.0.113.10/32."
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
  description = "Tipo de instancia del nodo."
  type        = string
  default     = "t3.large"
}

variable "disk_gb" {
  description = "Tamano del disco raiz en GB."
  type        = number
  default     = 40
}

variable "k3s_version" {
  description = "Version de k3s."
  type        = string
  default     = "v1.37.2+k3s1"
}

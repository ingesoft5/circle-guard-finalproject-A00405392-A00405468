variable "name" {
  description = "Prefijo de los recursos, por ejemplo circleguard-dev."
  type        = string
}

variable "vpc_cidr" {
  description = "Rango de la VPC, por ejemplo 10.10.0.0/16."
  type        = string

  validation {
    condition     = can(cidrnetmask(var.vpc_cidr))
    error_message = "vpc_cidr debe ser un CIDR IPv4 valido, por ejemplo 10.10.0.0/16."
  }
}

variable "subnet_cidr" {
  description = "Rango de la subred publica, dentro de vpc_cidr, por ejemplo 10.10.1.0/24."
  type        = string

  validation {
    condition     = can(cidrnetmask(var.subnet_cidr))
    error_message = "subnet_cidr debe ser un CIDR IPv4 valido, por ejemplo 10.10.1.0/24."
  }
}

variable "availability_zone" {
  description = "Zona de disponibilidad de la subred."
  type        = string
  default     = "us-east-1a"
}

variable "ingress_rules" {
  description = "Reglas de entrada TCP: un puerto, los CIDR de origen y una descripcion solo con caracteres ASCII."
  type = list(object({
    port        = number
    cidrs       = list(string)
    description = string
  }))

  validation {
    condition = alltrue([
      for r in var.ingress_rules : alltrue([for c in r.cidrs : can(cidrnetmask(c))])
    ])
    error_message = "Todos los CIDR de ingress_rules deben ser CIDR IPv4 validos, por ejemplo 203.0.113.5/32."
  }

  validation {
    condition     = alltrue([for r in var.ingress_rules : r.port >= 1 && r.port <= 65535])
    error_message = "Los puertos de ingress_rules deben estar entre 1 y 65535."
  }
}

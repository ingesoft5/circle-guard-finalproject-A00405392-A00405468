variable "name" {
  description = "Nombre de la VM y prefijo de sus recursos, por ejemplo circleguard-dev-k3s."
  type        = string
}

variable "role" {
  description = "tools = VM de Jenkins, SonarQube y Nexus (Docker lo instala el bootstrap); k3s = nodo Kubernetes de un ambiente."
  type        = string

  validation {
    condition     = contains(["tools", "k3s"], var.role)
    error_message = "role debe ser tools o k3s."
  }
}

variable "instance_type" {
  description = "Tipo de instancia EC2, por ejemplo t3.large."
  type        = string
}

variable "disk_gb" {
  description = "Tamano del disco raiz en GB."
  type        = number
  default     = 40

  validation {
    condition     = var.disk_gb >= 20
    error_message = "disk_gb debe ser al menos 20."
  }
}

variable "subnet_id" {
  description = "Subred donde vive la VM (la crea el modulo network)."
  type        = string
}

variable "security_group_id" {
  description = "Security group de la VM (lo crea el modulo network)."
  type        = string
}

variable "key_name" {
  description = "Key pair para entrar por SSH."
  type        = string
}

variable "k3s_version" {
  description = "Version de k3s que se instala en los nodos con role = k3s."
  type        = string
  default     = "v1.37.2+k3s1"
}

variable "iam_policy_statements" {
  description = "Permisos extra del rol de la VM (por ejemplo, descargar imagenes de ECR)."
  type = list(object({
    actions   = list(string)
    resources = list(string)
  }))
  default = []
}

# Pruebas del modulo registry. Usan un proveedor simulado: no tocan AWS.
mock_provider "aws" {}

variables {
  repositories = ["circleguard-auth-service", "circleguard-web"]
}

run "crea_un_repositorio_por_nombre" {
  command = plan

  assert {
    condition     = length(aws_ecr_repository.this) == 2
    error_message = "Debe haber un repositorio por cada nombre."
  }
}

run "las_etiquetas_son_inmutables" {
  command = plan

  assert {
    condition     = alltrue([for k, r in aws_ecr_repository.this : r.image_tag_mutability == "IMMUTABLE"])
    error_message = "Una version publicada no se puede sobrescribir: las etiquetas deben ser IMMUTABLE."
  }
}

run "escanea_al_subir" {
  command = plan

  assert {
    condition     = alltrue([for k, r in aws_ecr_repository.this : one(r.image_scanning_configuration).scan_on_push])
    error_message = "Cada imagen debe escanearse al subirla."
  }
}

run "rechaza_una_lista_vacia" {
  command = plan

  variables {
    repositories = []
  }

  expect_failures = [var.repositories]
}

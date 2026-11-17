# Pruebas del modulo storage. Usan un proveedor simulado: no tocan AWS.
mock_provider "aws" {}

variables {
  name = "circleguard-test-data"
}

run "el_versionado_esta_activo" {
  command = plan

  assert {
    condition     = one(aws_s3_bucket_versioning.this.versioning_configuration).status == "Enabled"
    error_message = "El bucket debe tener versionado."
  }
}

run "bloquea_todo_acceso_publico" {
  command = plan

  assert {
    condition = alltrue([
      aws_s3_bucket_public_access_block.this.block_public_acls,
      aws_s3_bucket_public_access_block.this.block_public_policy,
      aws_s3_bucket_public_access_block.this.ignore_public_acls,
      aws_s3_bucket_public_access_block.this.restrict_public_buckets,
    ])
    error_message = "Los cuatro bloqueos de acceso publico deben estar activos."
  }
}

run "cifra_los_objetos" {
  command = plan

  assert {
    condition     = one(one(aws_s3_bucket_server_side_encryption_configuration.this.rule).apply_server_side_encryption_by_default).sse_algorithm == "AES256"
    error_message = "Los objetos deben cifrarse con AES256."
  }
}

run "expira_las_versiones_antiguas" {
  command = plan

  variables {
    noncurrent_days = 7
  }

  assert {
    condition     = one(tolist(aws_s3_bucket_lifecycle_configuration.this.rule)[0].noncurrent_version_expiration).noncurrent_days == 7
    error_message = "Las versiones antiguas deben expirar a los noncurrent_days indicados."
  }
}

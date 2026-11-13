# Pruebas del modulo compute. Usan un proveedor simulado: no tocan AWS.
mock_provider "aws" {}

variables {
  name              = "circleguard-test-k3s"
  role              = "k3s"
  instance_type     = "t3.large"
  subnet_id         = "subnet-0123456789abcdef0"
  security_group_id = "sg-0123456789abcdef0"
  key_name          = "circleguard-test-key"
}

run "exige_imdsv2" {
  command = apply

  assert {
    condition     = aws_instance.this.metadata_options[0].http_tokens == "required"
    error_message = "La VM debe exigir IMDSv2 (http_tokens = required)."
  }
}

run "el_disco_raiz_va_cifrado_y_es_gp3" {
  command = apply

  assert {
    condition     = aws_instance.this.root_block_device[0].encrypted == true && aws_instance.this.root_block_device[0].volume_type == "gp3"
    error_message = "El disco raiz debe ser gp3 y estar cifrado."
  }
}

run "el_nodo_k3s_instala_k3s_con_la_version_fija" {
  command = apply

  assert {
    condition     = strcontains(aws_instance.this.user_data, "get.k3s.io") && strcontains(aws_instance.this.user_data, var.k3s_version)
    error_message = "El user_data de un nodo k3s debe instalar k3s con la version fijada."
  }
}

run "la_vm_tools_no_instala_k3s" {
  command = apply

  variables {
    role = "tools"
    name = "circleguard-test-tools"
  }

  assert {
    condition     = !strcontains(aws_instance.this.user_data, "k3s")
    error_message = "La VM tools no lleva k3s: Docker y las herramientas las instala el bootstrap."
  }
}

run "rechaza_un_role_desconocido" {
  command = plan

  variables {
    role = "otro"
  }

  expect_failures = [var.role]
}

# Pruebas del modulo network. Usan un proveedor simulado: no tocan AWS.
mock_provider "aws" {}

variables {
  name        = "circleguard-test"
  vpc_cidr    = "10.99.0.0/16"
  subnet_cidr = "10.99.1.0/24"
  ingress_rules = [
    { port = 22, cidrs = ["203.0.113.5/32", "198.51.100.7/32"], description = "SSH desde administracion" },
    { port = 80, cidrs = ["0.0.0.0/0"], description = "HTTP del Ingress" },
  ]
}

run "crea_una_regla_por_puerto_y_cidr" {
  command = plan

  assert {
    condition     = length(aws_vpc_security_group_ingress_rule.this) == 3
    error_message = "Debe haber una regla por cada puerto y CIDR (2 de SSH y 1 de HTTP)."
  }
}

run "ssh_nunca_queda_abierto_al_mundo" {
  command = plan

  assert {
    condition = alltrue([
      for k, r in aws_vpc_security_group_ingress_rule.this :
      !(r.from_port == 22 && r.cidr_ipv4 == "0.0.0.0/0")
    ])
    error_message = "El puerto 22 no puede estar abierto a 0.0.0.0/0."
  }
}

run "la_subred_no_asigna_ip_publica_sola" {
  command = plan

  assert {
    condition     = aws_subnet.public.map_public_ip_on_launch == false
    error_message = "La IP publica debe venir de la IP elastica, no de la subred."
  }
}

run "rechaza_un_cidr_invalido" {
  command = plan

  variables {
    ingress_rules = [
      { port = 22, cidrs = ["no-es-un-cidr"], description = "SSH malo" },
    ]
  }

  expect_failures = [var.ingress_rules]
}

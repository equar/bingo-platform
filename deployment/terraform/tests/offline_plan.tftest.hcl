# This plan is evaluated entirely with generated mock provider values. It does
# not authenticate to AWS, refresh remote objects, or apply infrastructure.
mock_provider "aws" {}
mock_provider "null" {}

run "test_environment_plan" {
  command = plan

  variables {
    environment   = "test"
    ssh_public_key = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIEQ0ZHVtbXlQdWJsaWNLZXlGb3JUZXN0c0FubHlOb3RSZWFs"
  }
}

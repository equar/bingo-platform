terraform {
  required_version = ">= 1.7.0"

  required_providers {
    mysql = {
      source  = "petoju/mysql"
      version = "~> 3.0"
    }
  }
}

provider "mysql" {
  endpoint = var.mysql_endpoint
  username = var.mysql_admin_username
  password = var.mysql_admin_password
  tls      = "true"
}

resource "mysql_database" "bingotest" {
  name                  = "bingotest"
  default_character_set = "utf8mb4"
  default_collation     = "utf8mb4_0900_ai_ci"
}

output "database_name" {
  value = mysql_database.bingotest.name
}

output "backend_runtime_environment" {
  description = "Set these values in the test backend environment file."
  value = {
    MYSQL_DATABASE = mysql_database.bingotest.name
  }
}

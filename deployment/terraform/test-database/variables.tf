variable "environment" {
  type = string
  validation {
    condition     = var.environment == "test"
    error_message = "The existing MySQL schema stack may only target the test environment."
  }
}

variable "mysql_endpoint" {
  type = string
}

variable "mysql_admin_username" {
  type      = string
  sensitive = true
}

variable "mysql_admin_password" {
  type      = string
  sensitive = true
}

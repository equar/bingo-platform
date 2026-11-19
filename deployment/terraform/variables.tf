variable "aws_region" {
  type    = string
  default = "us-east-2"
}

variable "availability_zone" {
  type    = string
  default = null
}

variable "environment" {
  type = string
  validation {
    condition     = contains(["prod", "test"], var.environment)
    error_message = "Environment must be exactly prod or test."
  }
}

variable "application_name" {
  type    = string
  default = "bingo"
}

variable "instance_bundle_id" {
  type    = string
  default = ""
}

variable "database_bundle_id" {
  type    = string
  default = ""
}

variable "database_admin_username" {
  type      = string
  sensitive = true
  default   = "bingo_db_admin"
}

variable "database_admin_password" {
  type      = string
  sensitive = true
  default   = null
}

variable "ssh_allowed_cidrs" {
  type    = set(string)
  default = []
}

variable "ssh_public_key" {
  description = "Public SSH key imported as the test EC2 key pair."
  type        = string
  default     = ""
}

variable "test_instance_type" {
  description = "EC2 instance type for test; t3.micro is a common free-tier-eligible choice."
  type        = string
  default     = "t3.micro"
}

variable "test_root_volume_size_gb" {
  description = "Encrypted test root volume size in GiB; keep within the account's free-tier storage allowance."
  type        = number
  default     = 20
  validation {
    condition     = var.test_root_volume_size_gb >= 8 && var.test_root_volume_size_gb <= 30
    error_message = "The test root volume must be between 8 and 30 GiB."
  }
}

variable "test_vpc_cidr" {
  type    = string
  default = "10.77.0.0/16"
}

variable "test_subnet_cidr" {
  type    = string
  default = "10.77.1.0/24"
}

variable "route53_zone_id" {
  description = "Optional public Route 53 hosted zone ID for automatic test DNS records."
  type        = string
  default     = ""
}

variable "test_site_domain" {
  type    = string
  default = "test.bingo.bakaroo.com"
  validation {
    condition     = can(regex("^[a-z0-9]+([.-][a-z0-9]+)*$", var.test_site_domain))
    error_message = "The test_site_domain variable must be a DNS hostname."
  }
}

variable "test_admin_domain" {
  type    = string
  default = "admin.test.bingo.bakaroo.com"
  validation {
    condition     = can(regex("^[a-z0-9]+([.-][a-z0-9]+)*$", var.test_admin_domain))
    error_message = "The test_admin_domain variable must be a DNS hostname."
  }
}

variable "test_api_domain" {
  type    = string
  default = "api.test.bingo.bakaroo.com"
  validation {
    condition     = can(regex("^[a-z0-9]+([.-][a-z0-9]+)*$", var.test_api_domain))
    error_message = "The test_api_domain variable must be a DNS hostname."
  }
}

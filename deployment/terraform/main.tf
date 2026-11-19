terraform {
  required_version = ">= 1.1.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    null = {
      source  = "hashicorp/null"
      version = "~> 3.2"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

data "aws_availability_zones" "available" {
  state = "available"
}

data "aws_ami" "ubuntu_2404" {
  count       = var.environment == "test" ? 1 : 0
  most_recent = true
  owners      = ["099720109477"]

  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd-gp3/ubuntu-noble-24.04-amd64-server-*"]
  }

  filter {
    name   = "architecture"
    values = ["x86_64"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

locals {
  name              = "${var.application_name}-${var.environment}"
  availability_zone = coalesce(var.availability_zone, data.aws_availability_zones.available.names[0])
  is_test           = var.environment == "test"
  test_dns_enabled  = local.is_test && var.route53_zone_id != ""
  tags = {
    Application = var.application_name
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

moved {
  from = aws_secretsmanager_secret.backend_runtime
  to   = aws_secretsmanager_secret.backend_runtime[0]
}

moved {
  from = aws_lightsail_database.mysql
  to   = aws_lightsail_database.mysql[0]
}

moved {
  from = aws_lightsail_instance.web
  to   = aws_lightsail_instance.web[0]
}

moved {
  from = aws_lightsail_instance_public_ports.web
  to   = aws_lightsail_instance_public_ports.web[0]
}

moved {
  from = aws_lightsail_static_ip.web
  to   = aws_lightsail_static_ip.web[0]
}

moved {
  from = aws_lightsail_static_ip_attachment.web
  to   = aws_lightsail_static_ip_attachment.web[0]
}

resource "aws_secretsmanager_secret" "backend_runtime" {
  count                   = local.is_test ? 0 : 1
  name                    = "${local.name}/backend-runtime"
  description             = "Bingo ${var.environment} backend runtime configuration"
  recovery_window_in_days = var.environment == "prod" ? 30 : 0
  tags                    = local.tags
}

resource "aws_lightsail_database" "mysql" {
  count                    = local.is_test ? 0 : 1
  relational_database_name = "${local.name}-mysql"
  availability_zone        = local.availability_zone
  master_database_name     = "bingo"
  master_username          = var.database_admin_username
  master_password          = var.database_admin_password
  blueprint_id             = "mysql_8_0"
  bundle_id                = var.database_bundle_id
  publicly_accessible      = false
  backup_retention_enabled = true
  apply_immediately        = true
  tags                     = local.tags
}

resource "aws_lightsail_instance" "web" {
  count             = local.is_test ? 0 : 1
  name              = "${local.name}-web"
  availability_zone = local.availability_zone
  blueprint_id      = "ubuntu_24_04"
  bundle_id         = var.instance_bundle_id
  user_data = templatefile("${path.module}/templates/bootstrap.sh.tftpl", {
    environment = "prod"
  })
  tags = local.tags
}

resource "aws_lightsail_instance_public_ports" "web" {
  count         = local.is_test ? 0 : 1
  instance_name = aws_lightsail_instance.web[0].name

  port_info {
    protocol  = "tcp"
    from_port = 80
    to_port   = 80
  }

  port_info {
    protocol  = "tcp"
    from_port = 443
    to_port   = 443
  }

  dynamic "port_info" {
    for_each = var.ssh_allowed_cidrs
    content {
      protocol  = "tcp"
      from_port = 22
      to_port   = 22
      cidrs     = [port_info.value]
    }
  }
}

resource "aws_lightsail_static_ip" "web" {
  count = local.is_test ? 0 : 1
  name  = "${local.name}-ip"
}

resource "aws_lightsail_static_ip_attachment" "web" {
  count          = local.is_test ? 0 : 1
  static_ip_name = aws_lightsail_static_ip.web[0].name
  instance_name  = aws_lightsail_instance.web[0].name
}

resource "aws_vpc" "test" {
  count                = local.is_test ? 1 : 0
  cidr_block           = var.test_vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true
  tags                 = merge(local.tags, { Name = "${local.name}-vpc" })
}

resource "aws_internet_gateway" "test" {
  count  = local.is_test ? 1 : 0
  vpc_id = aws_vpc.test[0].id
  tags   = merge(local.tags, { Name = "${local.name}-igw" })
}

resource "aws_subnet" "test_public" {
  count                   = local.is_test ? 1 : 0
  vpc_id                  = aws_vpc.test[0].id
  cidr_block              = var.test_subnet_cidr
  availability_zone       = local.availability_zone
  map_public_ip_on_launch = false
  tags                    = merge(local.tags, { Name = "${local.name}-public" })
}

resource "aws_route_table" "test_public" {
  count  = local.is_test ? 1 : 0
  vpc_id = aws_vpc.test[0].id
  tags   = merge(local.tags, { Name = "${local.name}-public" })
}

resource "aws_route" "test_internet" {
  count                  = local.is_test ? 1 : 0
  route_table_id         = aws_route_table.test_public[0].id
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = aws_internet_gateway.test[0].id
}

resource "aws_route_table_association" "test_public" {
  count          = local.is_test ? 1 : 0
  subnet_id      = aws_subnet.test_public[0].id
  route_table_id = aws_route_table.test_public[0].id
}

resource "aws_security_group" "test" {
  count       = local.is_test ? 1 : 0
  name        = "${local.name}-web"
  description = "Public HTTP and restricted SSH for the Bingo test host"
  vpc_id      = aws_vpc.test[0].id
  tags        = local.tags

  ingress {
    description = "HTTP application traffic"
    protocol    = "tcp"
    from_port   = 80
    to_port     = 80
    cidr_blocks = ["0.0.0.0/0"]
  }

  dynamic "ingress" {
    for_each = var.ssh_allowed_cidrs
    content {
      description = "SSH from an explicitly allowed source"
      protocol    = "tcp"
      from_port   = 22
      to_port     = 22
      cidr_blocks = [ingress.value]
    }
  }

  egress {
    protocol    = "-1"
    from_port   = 0
    to_port     = 0
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_key_pair" "test" {
  count      = local.is_test ? 1 : 0
  key_name   = "${local.name}-deploy"
  public_key = var.ssh_public_key
  tags       = local.tags
}

resource "aws_instance" "test" {
  count                       = local.is_test ? 1 : 0
  ami                         = data.aws_ami.ubuntu_2404[0].id
  instance_type               = var.test_instance_type
  key_name                    = aws_key_pair.test[0].key_name
  subnet_id                   = aws_subnet.test_public[0].id
  vpc_security_group_ids      = [aws_security_group.test[0].id]
  associate_public_ip_address = false
  user_data_replace_on_change = true

  metadata_options {
    http_endpoint = "enabled"
    http_tokens   = "required"
  }

  credit_specification {
    cpu_credits = "standard"
  }

  root_block_device {
    volume_type           = "gp3"
    volume_size           = var.test_root_volume_size_gb
    encrypted             = true
    delete_on_termination = true
  }

  user_data = templatefile("${path.module}/templates/test-ec2-bootstrap.sh.tftpl", {
    test_public_ip = aws_eip.test[0].public_ip
    nginx_http_config_b64 = base64encode(templatefile("${path.module}/templates/test-nginx-http.conf.tftpl", {
      SITE_DOMAIN  = var.test_site_domain
      ADMIN_DOMAIN = var.test_admin_domain
      API_DOMAIN   = var.test_api_domain
    }))
  })

  depends_on = [
    aws_route.test_internet,
    aws_route_table_association.test_public,
  ]
  tags = merge(local.tags, { Name = "${local.name}-ec2" })
}

resource "aws_eip" "test" {
  count  = local.is_test ? 1 : 0
  domain = "vpc"
  tags   = merge(local.tags, { Name = "${local.name}-eip" })
}

resource "aws_eip_association" "test" {
  count         = local.is_test ? 1 : 0
  allocation_id = aws_eip.test[0].id
  instance_id   = aws_instance.test[0].id
}

resource "aws_route53_record" "test_site" {
  count   = local.test_dns_enabled ? 1 : 0
  zone_id = var.route53_zone_id
  name    = var.test_site_domain
  type    = "A"
  ttl     = 60
  records = [aws_eip.test[0].public_ip]

  depends_on = [aws_eip_association.test]
}

resource "aws_route53_record" "test_admin" {
  count   = local.test_dns_enabled ? 1 : 0
  zone_id = var.route53_zone_id
  name    = var.test_admin_domain
  type    = "A"
  ttl     = 60
  records = [aws_eip.test[0].public_ip]

  depends_on = [aws_eip_association.test]
}

resource "aws_route53_record" "test_api" {
  count   = local.test_dns_enabled ? 1 : 0
  zone_id = var.route53_zone_id
  name    = var.test_api_domain
  type    = "A"
  ttl     = 60
  records = [aws_eip.test[0].public_ip]

  depends_on = [aws_eip_association.test]
}

resource "null_resource" "godaddy_test_dns" {
  count = local.is_test && var.route53_zone_id == "" ? 1 : 0

  triggers = {
    zone         = "bakaroo.com"
    public_ip    = aws_eip.test[0].public_ip
    site_domain  = var.test_site_domain
    admin_domain = var.test_admin_domain
    api_domain   = var.test_api_domain
  }

  provisioner "local-exec" {
    command = "python3 \"${path.module}/scripts/update_godaddy_test_dns.py\" sync"
    environment = {
      GODADDY_DNS_ZONE          = self.triggers.zone
      GODADDY_TEST_PUBLIC_IP    = self.triggers.public_ip
      GODADDY_TEST_SITE_DOMAIN  = self.triggers.site_domain
      GODADDY_TEST_ADMIN_DOMAIN = self.triggers.admin_domain
      GODADDY_TEST_API_DOMAIN   = self.triggers.api_domain
    }
  }

  lifecycle {
    create_before_destroy = true
  }

  depends_on = [aws_eip_association.test]
}

output "environment" {
  value = var.environment
}

output "web_static_ip" {
  value = local.is_test ? aws_eip.test[0].public_ip : aws_lightsail_static_ip.web[0].ip_address
}

output "database_endpoint" {
  value = local.is_test ? "127.0.0.1" : aws_lightsail_database.mysql[0].master_endpoint_address
}

output "backend_runtime_secret_arn" {
  value = local.is_test ? "" : aws_secretsmanager_secret.backend_runtime[0].arn
}

output "test_api_base_url" {
  value = local.is_test ? "http://${var.test_api_domain}" : null
}

output "test_admin_url" {
  value = local.is_test ? "http://${var.test_admin_domain}" : null
}

output "test_site_url" {
  value = local.is_test ? "http://${var.test_site_domain}" : null
}

output "test_ssh_user" {
  value = local.is_test ? "ubuntu" : null
}

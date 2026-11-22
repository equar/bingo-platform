# Use synthetic provider state to check the schema root's plan without a real
# MySQL connection, AWS account, or database credentials.
mock_provider "mysql" {}

run "test_schema_plan" {
  command = plan

  variables {
    environment          = "test"
    mysql_endpoint       = "127.0.0.1:3306"
    mysql_admin_username = "terraform-test"
    mysql_admin_password = "not-a-real-password"
  }
}

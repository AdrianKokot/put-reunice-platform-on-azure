output "storage_account_name" {
  value = azurerm_storage_account.storage_account.name
}

output "storage_container_name" {
  value = azurerm_storage_container.blob_container.name
}

output "storage_sas_token" {
  value     = data.azurerm_storage_account_sas.sas.sas
  sensitive = true
}

output "postgres_server_fqdn" {
  value = azurerm_postgresql_flexible_server.postgres.fqdn
}

output "postgres_admin_username" {
  value = "${var.postgres_user}"
}

output "postgres_admin_password" {
  value     = random_password.postgres_password.result
  sensitive = true
}

output "postgres_database_name" {
  value = azurerm_postgresql_flexible_server_database.database.name
}

output "env_file_path" {
  value = local_file.env_file.filename
}
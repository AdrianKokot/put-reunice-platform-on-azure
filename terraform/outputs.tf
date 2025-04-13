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

output "env_file_path" {
  value = local_file.env_file.filename
}
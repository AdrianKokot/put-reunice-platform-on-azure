provider "azurerm" {
  features {}
  subscription_id = var.subscription_id
}

resource "azurerm_resource_group" "storage_rg" {
  name     = var.resource_group_name
  location = var.location
}

resource "azurerm_storage_account" "storage_account" {
  name                     = var.storage_account_name
  resource_group_name      = azurerm_resource_group.storage_rg.name
  location                 = azurerm_resource_group.storage_rg.location
  account_tier             = "Standard"
  account_replication_type = "LRS"

  blob_properties {
    cors_rule {
      allowed_headers    = ["*"]
      allowed_methods    = ["GET", "POST", "PUT"]
      allowed_origins    = ["*"]
      exposed_headers    = ["*"]
      max_age_in_seconds = 3600
    }
  }
}

resource "azurerm_storage_container" "blob_container" {
  name                  = var.container_name
  storage_account_id  = azurerm_storage_account.storage_account.id
  container_access_type = "blob"
}

data "azurerm_storage_account_sas" "sas" {
  connection_string = azurerm_storage_account.storage_account.primary_connection_string
  https_only        = true

  resource_types {
    service   = false
    container = true
    object    = true
  }

  services {
    blob  = true
    queue = false
    table = false
    file  = false
  }

  start  = timestamp()
  expiry = timeadd(timestamp(), "8760h") # 1 year

  permissions {
    read    = true
    write   = true
    delete  = false
    list    = true
    add     = true
    create  = true
    update  = true
    process = false
    tag     = false
    filter  = false
  }
}

resource "local_file" "env_file" {
  content = <<-EOT
AZURE_STORAGE_ACCOUNT_NAME="${azurerm_storage_account.storage_account.name}"
AZURE_STORAGE_CONTAINER_NAME="${azurerm_storage_container.blob_container.name}"
AZURE_STORAGE_SAS_TOKEN="${data.azurerm_storage_account_sas.sas.sas}"
EOT
  filename = "${path.module}/.env"
}
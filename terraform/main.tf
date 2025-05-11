provider "azurerm" {
  features {}
  subscription_id = var.subscription_id
}

# Storage Account and Blob Container
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
  storage_account_id    = azurerm_storage_account.storage_account.id
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

# PostgreSQL Database
resource "random_password" "postgres_password" {
  length           = 16
  special          = true
  override_special = "!#$%&*()-_=+[]{}<>:?"
}

resource "azurerm_postgresql_flexible_server" "postgres" {
  name                   = var.postgres_server_name
  resource_group_name    = azurerm_resource_group.storage_rg.name
  location               = azurerm_resource_group.storage_rg.location
  version                = "16"
  administrator_login    = var.postgres_user
  administrator_password = random_password.postgres_password.result
  storage_mb             = 32768
  sku_name               = "B_Standard_B1ms"
  zone                   = "1"

  depends_on = [azurerm_resource_group.storage_rg]
}

resource "azurerm_postgresql_flexible_server_database" "database" {
  name      = var.postgres_db_name
  server_id = azurerm_postgresql_flexible_server.postgres.id
  collation = "en_US.utf8"
  charset   = "utf8"
}

resource "azurerm_postgresql_flexible_server_firewall_rule" "allow_azure_services" {
  name             = "AllowAllAzureServicesAndResourcesWithinAzureIps"
  server_id        = azurerm_postgresql_flexible_server.postgres.id
  start_ip_address = "0.0.0.0"
  end_ip_address   = "0.0.0.0"
}

# Spring boot docker image

resource "azurerm_user_assigned_identity" "aci_identity" {
  location            = var.location
  name                = "${var.resource_group_name}-backend-identity"
  resource_group_name = var.resource_group_name
  depends_on          = [azurerm_resource_group.storage_rg]
}

resource "azurerm_container_group" "backend_container" {
  name                = "backend-container-group"
  location            = azurerm_resource_group.storage_rg.location
  resource_group_name = azurerm_resource_group.storage_rg.name
  os_type             = "Linux"
  restart_policy      = "Never"

  container {
    name   = "backend-container"
    image  = "ghcr.io/adriankokot/put-reunice-platform-on-azure/backend:pr-20"
    cpu    = 1
    memory = 2
    environment_variables = {
      AZURE_STORAGE_ACCOUNT_NAME          = azurerm_storage_account.storage_account.name
      AZURE_STORAGE_CONTAINER_NAME        = azurerm_storage_container.blob_container.name
      AZURE_STORAGE_SAS_TOKEN             = data.azurerm_storage_account_sas.sas.sas
      DB_SERVER                           = azurerm_postgresql_flexible_server.postgres.fqdn
      POSTGRES_DB                         = azurerm_postgresql_flexible_server_database.database.name
      POSTGRES_USER                       = azurerm_postgresql_flexible_server.postgres.administrator_login
      POSTGRES_PASSWORD                   = azurerm_postgresql_flexible_server.postgres.administrator_password
      APP_URL                             = "http://myapp-${random_id.dns.hex}.azurecontainer.io"
      DATABASE_SCHEMA_HANDLING_ON_STARTUP = "create"
      DATABASE_SCHEMA_CREATE_TYPE         = "initialize"
      EMAIL_TEMPLATES_DIRECTORY           = "/emailTemplates/"
    }

    ports {
      port     = 80
      protocol = "TCP"
    }
  }

  image_registry_credential {
    server   = "ghcr.io"
    username = "AdrianKokot"
    password = var.github_pat
  }

  identity {
    type = "UserAssigned"
    identity_ids = [
      azurerm_user_assigned_identity.aci_identity.id,
    ]
  }

  ip_address_type = "Public"
  dns_name_label  = "myapp-${random_id.dns.hex}"
}

resource "random_id" "dns" {
  byte_length = 4
}

output "github_pat_debug" {
  value     = var.github_pat
  sensitive = true
}

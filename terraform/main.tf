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
    image  = "ghcr.io/adriankokot/put-reunice-platform-on-azure/backend:latest"
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
      APP_URL                             = "http://put-reunice-frontend.northeurope.azurecontainer.io"
      DATABASE_SCHEMA_HANDLING_ON_STARTUP = "create"
      DATABASE_SCHEMA_CREATE_TYPE         = "populate"
      EMAIL_TEMPLATES_DIRECTORY           = "/app/emailTemplates/"
      SMTP_SERVER                         = "put-reunice-mailpit.northeurope.azurecontainer.io"
      SMTP_PORT                           = 1025
      SMTP_USERNAME                       = var.smtp_username
      SMTP_PASSWORD                       = var.smtp_password
      SMTP_USE_AUTH                       = "true"
      SMTP_USE_TLS                        = "true"
      TYPESENSE_API_KEY                   = var.typesense_apikey
      TYPESENSE_HOST                      = "put-reunice-typesense.northeurope.azurecontainer.io"
      TYPESENSE_CACHE_ENABLED             = var.typesense_cache_enabled
      TYPESENSE_CACHE_TTL                 = var.typesense_cache_ttl
      TYPESENSE_USE_EMBEDDING             = var.typesense_use_embedding
      TYPESENSE_DISTANCE_THRESHOLD        = var.typesense_distance_threshold
    }

    ports {
      port     = 8080
      protocol = "TCP"
    }
  }

  image_registry_credential {
    server   = "ghcr.io"
    username = var.github_username
    password = var.github_pat
  }

  identity {
    type = "UserAssigned"
    identity_ids = [
      azurerm_user_assigned_identity.aci_identity.id,
    ]
  }

  ip_address_type = "Public"
  dns_name_label  = "put-reunice-backend"

  depends_on = [azurerm_container_group.mailpit_container,
  azurerm_container_group.typesense_container, azurerm_postgresql_flexible_server.postgres]
}

# Frontend

resource "azurerm_container_group" "frontend_container" {
  name                = "frontend-container-group"
  location            = azurerm_resource_group.storage_rg.location
  resource_group_name = azurerm_resource_group.storage_rg.name
  os_type             = "Linux"
  restart_policy      = "Never"

  container {
    name   = "frontend-container"
    image  = "ghcr.io/adriankokot/put-reunice-platform-on-azure/frontend:latest"
    cpu    = 1
    memory = 2
    environment_variables = {
      API_URL = "http://put-reunice-backend.northeurope.azurecontainer.io"
    }

    ports {
      port     = 80
      protocol = "TCP"
    }
  }

  image_registry_credential {
    server   = "ghcr.io"
    username = var.github_username
    password = var.github_pat
  }

  identity {
    type = "UserAssigned"
    identity_ids = [
      azurerm_user_assigned_identity.aci_identity.id,
    ]
  }

  ip_address_type = "Public"
  dns_name_label  = "put-reunice-frontend"
}

# Mailpit

resource "azurerm_container_group" "mailpit_container" {
  name                = "mailpit-container-group"
  location            = azurerm_resource_group.storage_rg.location
  resource_group_name = azurerm_resource_group.storage_rg.name
  os_type             = "Linux"
  restart_policy      = "Always"

  container {
    name   = "mailpit"
    image  = "ghcr.io/adriankokot/put-reunice-platform-on-azure/mailpit:latest"
    cpu    = 0.5
    memory = 1

    ports {
      port     = 8025
      protocol = "TCP"
    }
    ports {
      port     = 1025
      protocol = "TCP"
    }

    environment_variables = {
      MP_SMTP_AUTH_ALLOW_INSECURE = "true"
      MP_SMTP_AUTH                = format("%s:%s", var.smtp_username, var.smtp_password)
      MP_UI_AUTH                  = format("%s:%s", var.smtp_username, var.smtp_password)
    }
  }

  image_registry_credential {
    server   = "ghcr.io"
    username = var.github_username
    password = var.github_pat
  }

  identity {
    type = "UserAssigned"
    identity_ids = [
      azurerm_user_assigned_identity.aci_identity.id,
    ]
  }

  ip_address_type = "Public"
  dns_name_label  = "put-reunice-mailpit"
}

# Typesense

resource "azurerm_storage_share" "typesense_fileshare" {
  name                 = "typesense-data"
  storage_account_id = azurerm_storage_account.storage_account.id
  quota                = 50 # GB
  depends_on = [ azurerm_storage_account.storage_account ]
}

resource "azurerm_container_group" "typesense_container" {
  name                = "typesense-container-group"
  location            = azurerm_resource_group.storage_rg.location
  resource_group_name = azurerm_resource_group.storage_rg.name
  os_type             = "Linux"
  restart_policy      = "OnFailure"

  container {
    name   = "typesense"
    image  = "ghcr.io/adriankokot/put-reunice-platform-on-azure/typesense:latest"
    cpu    = 1
    memory = 2

    ports {
      port     = 8108
      protocol = "TCP"
    }

    environment_variables = {
      TYPESENSE_API_KEY  = var.typesense_apikey
      TYPESENSE_DATA_DIR = "/data"
    }
    volume {
      name                 = "typesense-data"
      mount_path           = "/data"
      storage_account_key  = azurerm_storage_account.storage_account.primary_access_key
      storage_account_name = azurerm_storage_account.storage_account.name
      share_name = azurerm_storage_share.typesense_fileshare.name
      read_only = false
    }
  }

  image_registry_credential {
    server   = "ghcr.io"
    username = var.github_username
    password = var.github_pat
  }

  identity {
    type = "UserAssigned"
    identity_ids = [
      azurerm_user_assigned_identity.aci_identity.id,
    ]
  }

  ip_address_type = "Public"
  dns_name_label  = "put-reunice-typesense"
}

variable "resource_group_name" {
  description = "Name of the resource group"
  type        = string
  default     = "rg-reunice"
}

variable "location" {
  description = "Azure region where resources will be created"
  type        = string
  default     = "northeurope"
}

variable "storage_account_name" {
  description = "Name of the storage account (must be globally unique)"
  type        = string
  default     = "reuniceblobstorage"
}

variable "container_name" {
  description = "Name of the blob container"
  type        = string
  default     = "public-blob-container"
}

variable "subscription_id" {
  description = "Azure subscription ID"
  type        = string
  default     = "your-subscription-id"
}

variable "github_pat" {
  description = "GitHub Personal Access Token for GHCR"
  type        = string
  sensitive   = true
}

# PostgreSQL variables
variable "postgres_server_name" {
  description = "Name of the PostgreSQL Flexible Server"
  type        = string
  default     = "reunice-postgres-server"
}

variable "postgres_db_name" {
  description = "Name of the PostgreSQL database"
  type        = string
  default     = "reunice"
}

variable "postgres_user" {
  description = "PostgreSQL server admin username"
  type        = string
  default     = "postgres"
}

# Typesense
variable "typesense_apikey" {
  description = "Typesense API key"
  type        = string
  default     = "devapikey"
}
variable "typesense_cache_enabled" {
  description = "Enable Typesense cache"
  type        = bool
  default     = true
}

variable "typesense_cache_ttl" {
  description = "Typesense cache TTL in milliseconds"
  type        = number
  default     = 60000
}

variable "typesense_use_embedding" {
  description = "Use Typesense embedding"
  type        = bool
  default     = false
}

variable "typesense_distance_threshold" {
  description = "Typesense distance threshold"
  type        = number
  default     = 0.30
}

# Github
variable "github_username" {
  description = "GitHub username for GHCR"
  type        = string
  default     = "AdrianKokot"
}

# Mailpit

variable "smtp_username" {
  description = "SMTP username for Mailpit"
  type        = string
  default     = "test"
}

variable "smtp_password" {
  description = "SMTP password for Mailpit"
  type        = string
  default     = "test"
}

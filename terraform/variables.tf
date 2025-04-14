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
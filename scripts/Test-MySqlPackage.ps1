#requires -Version 5.1
[CmdletBinding()]
param([string]$ArchivePath)
$ErrorActionPreference='Stop'
Import-Module (Join-Path $PSScriptRoot 'lib\CampusHub.Package.psm1') -Force
# Reads ZIP/signature/key; GPG writes only its isolated public keyring under .local.
# Does not extract or execute the package, create the runtime, or change any ACL.
Get-VerifiedMySqlPackage -ArchivePath $ArchivePath | ConvertTo-Json -Depth 4

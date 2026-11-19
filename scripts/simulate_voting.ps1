# ========================================================
# Script Ejecutor de Simulación de Votación en PowerShell
# Delega a scripts/powershell/simulate_voting.ps1
# ========================================================

[CmdletBinding()]
param(
    [string]$BaseUrl = $(if ($env:VOTING_API_URL) { $env:VOTING_API_URL } else { "http://localhost:8080" }),
    [int]$BurstCount = 5
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$targetScript = Join-Path $scriptDir "powershell\simulate_voting.ps1"

if (Test-Path $targetScript) {
    & $targetScript -BaseUrl $BaseUrl -BurstCount $BurstCount
} else {
    Write-Error "No se encontró el módulo powershell en $targetScript"
}

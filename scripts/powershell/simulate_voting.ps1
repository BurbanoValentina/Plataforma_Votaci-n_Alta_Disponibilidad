<#
.SYNOPSIS
    Suite de Pruebas de Carga y Simulación de Votación en PowerShell.
.DESCRIPTION
    Ejecuta pruebas automáticas de:
    - Verificación de salud (Health Check)
    - Emisión de votos válidos con generación de tickets SHA-256
    - Rechazo de cédula no elegible (403 Forbidden)
    - Bloqueo de voto duplicado (409 Conflict)
    - Ráfagas de votación para pruebas de concurrencia y detección de anomalías
#>

[CmdletBinding()]
param(
    [string]$BaseUrl = $(if ($env:VOTING_API_URL) { $env:VOTING_API_URL } else { "http://localhost:8080" }),
    [int]$BurstCount = 5
)

$ErrorActionPreference = "Continue"

Write-Host "`n==================================================================" -ForegroundColor Cyan
Write-Host " SIMULADOR ELECTORAL EN POWERSHELL — PLATAFORMA DE ALTA DISPONIBILIDAD" -ForegroundColor Cyan
Write-Host " Objetivo: $BaseUrl" -ForegroundColor Gray
Write-Host "==================================================================`n" -ForegroundColor Cyan

function Invoke-CastVote {
    param(
        [Parameter(Mandatory=$true)][string]$VoterId,
        [Parameter(Mandatory=$true)][string]$VoterName,
        [Parameter(Mandatory=$true)][string]$CandidateId,
        [Parameter(Mandatory=$true)][string]$City,
        [string]$Region = "CENTRO",
        [string]$ElectionType = "NACIONAL",
        [string]$ClientIp = "190.25.1.10",
        [string]$VoteSource = "REAL"
    )

    $endpoint = "$($BaseUrl.TrimEnd('/'))/api/vote"
    $payload = @{
        voterId      = $VoterId
        voterName    = $VoterName
        candidateId  = $CandidateId
        city         = $City
        region       = $Region
        electionType = $ElectionType
        clientIp     = $ClientIp
        voteSource   = $VoteSource
    } | ConvertTo-Json

    $headers = @{
        "Content-Type"    = "application/json"
        "X-Forwarded-For" = $ClientIp
        "X-Real-IP"       = $ClientIp
    }

    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $response = Invoke-RestMethod -Uri $endpoint -Method POST -Body $payload -Headers $headers -TimeoutSec 10
        $sw.Stop()
        $ticketCode = if ($response.ticket.ticketCode) { $response.ticket.ticketCode } else { "Emitido" }
        Write-Host "  [HTTP 200 OK]  Cédula=$($VoterId.PadRight(10)) -> Cand=$($CandidateId.PadRight(12)) ($($sw.ElapsedMilliseconds)ms) Ticket: $ticketCode" -ForegroundColor Green
        return @{ Success = $true; StatusCode = 200; Data = $response }
    } catch {
        $sw.Stop()
        $status = 0
        $body = $_.ErrorDetails.Message
        if ($_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
        }
        $color = if ($status -eq 409) { "Yellow" } elseif ($status -eq 403) { "Magenta" } else { "Red" }
        Write-Host "  [HTTP $status] Cédula=$($VoterId.PadRight(10)) -> Cand=$($CandidateId.PadRight(12)) ($($sw.ElapsedMilliseconds)ms) Msg: $body" -ForegroundColor $color
        return @{ Success = $false; StatusCode = $status; Error = $body }
    }
}

# 1. Comprobación de Salud
Write-Host "1. Verificando Salud del Nodo (ALB Health Check)..." -ForegroundColor Yellow
try {
    $health = Invoke-RestMethod -Uri "$($BaseUrl.TrimEnd('/'))/api/health" -Method GET -TimeoutSec 5
    Write-Host "   -> Estado: $($health.status) | Nodo: $($health.nodeId) | AZ: $($health.availabilityZone)`n" -ForegroundColor Green
} catch {
    Write-Host "   -> Advertencia: No se pudo contactar /api/health ($($_.Exception.Message))`n" -ForegroundColor Red
}

# 2. Votos Válidos
Write-Host "2. Emisión de Votos Ciudadanos Válidos..." -ForegroundColor Yellow
Invoke-CastVote -VoterId "1002001" -VoterName "Ana Milena Suárez" -CandidateId "cand_nac_01" -City "Bogotá" -Region "CENTRO" | Out-Null
Invoke-CastVote -VoterId "1002002" -VoterName "Juan Pablo Herrera" -CandidateId "cand_nac_02" -City "Medellín" -Region "CENTRO" | Out-Null
Invoke-CastVote -VoterId "1002003" -VoterName "Diana Marcela Torres" -CandidateId "cand_reg_01" -City "Barranquilla" -Region "NORTE" -ElectionType "REGIONAL" | Out-Null

# 3. Voto No Elegible (Censo)
Write-Host "`n3. Intento de Voto con Cédula No Elegible..." -ForegroundColor Yellow
Invoke-CastVote -VoterId "0000000000" -VoterName "No Empadronado" -CandidateId "cand_nac_01" -City "Bogotá" | Out-Null

# 4. Voto Duplicado
Write-Host "`n4. Intento de Voto Duplicado (Misma Cédula 1002001)..." -ForegroundColor Yellow
Invoke-CastVote -VoterId "1002001" -VoterName "Ana Milena Suárez" -CandidateId "cand_nac_03" -City "Bogotá" | Out-Null

# 5. Ráfaga de Concurrencia
Write-Host "`n5. Disparando Ráfaga de Votos Simultáneos ($BurstCount votos)..." -ForegroundColor Yellow
for ($i = 1; $i -le $BurstCount; $i++) {
    $simId = "burst_ps_$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())_$i"
    Invoke-CastVote -VoterId $simId -VoterName "Votante Concurrente $i" -CandidateId "cand_nac_01" -City "Cali" -Region "OESTE" -VoteSource "SIMULATED" -ClientIp "10.0.88.$i" | Out-Null
}

Write-Host "`n==================================================================" -ForegroundColor Cyan
Write-Host " Batería de Pruebas en PowerShell Finalizada Exitosamente." -ForegroundColor Cyan
Write-Host "==================================================================`n" -ForegroundColor Cyan

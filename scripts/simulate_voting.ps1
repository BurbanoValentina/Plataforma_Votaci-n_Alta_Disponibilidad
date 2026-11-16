# ========================================================
# Script de Simulación de Votación y Pruebas End-to-End
# Plataforma de Votación de Alta Disponibilidad
# ========================================================

$API_URL = if ($env:VOTING_API_URL) { $env:VOTING_API_URL } else { "http://localhost:8085/vote" }

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "Iniciando Simulación de Votación en Vivo..." -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# Función para enviar un voto HTTP POST
function Send-Vote {
    param(
        [string]$VoterId,
        [string]$CandidateId,
        [string]$Region,
        [string]$ClientIp
    )

    $body = @{
        voterId = $VoterId
        candidateId = $CandidateId
        region = $Region
        clientIp = $ClientIp
    } | ConvertTo-Json

    try {
        $response = Invoke-RestMethod -Uri $API_URL -Method POST -Body $body -ContentType "application/json"
        Write-Host "[HTTP 200 OK] Voto Aceptado: Voter=$VoterId -> Candidate=$CandidateId ($Region) desde IP=$ClientIp" -ForegroundColor Green
        return $response
    } catch {
        $statusCode = $_.Exception.Response.StatusCode.value__
        $errorBody = $_.ErrorDetails.Message
        Write-Host "[$statusCode RECHAZADO] Voter=$VoterId: $errorBody" -ForegroundColor Red
        return $null
    }
}

Write-Host "`n1. Prueba de Votantes Elegibles (HTTP 200)..." -ForegroundColor Yellow
Send-Vote -VoterId "voter_001" -CandidateId "candidate_A" -Region "NORTE" -ClientIp "192.168.1.10"
Send-Vote -VoterId "voter_002" -CandidateId "candidate_B" -Region "SUR" -ClientIp "192.168.1.11"
Send-Vote -VoterId "voter_003" -CandidateId "candidate_A" -Region "CENTRO" -ClientIp "192.168.1.12"
Send-Vote -VoterId "voter_004" -CandidateId "candidate_C" -Region "ESTE" -ClientIp "192.168.1.13"
Send-Vote -VoterId "voter_005" -CandidateId "candidate_B" -Region "OESTE" -ClientIp "192.168.1.14"

Write-Host "`n2. Prueba de Votante NO Elegible (Debe Retornar HTTP 403 Forbidden)..." -ForegroundColor Yellow
Send-Vote -VoterId "voter_inelegible_999" -CandidateId "candidate_A" -Region "NORTE" -ClientIp "192.168.1.200"

Write-Host "`n3. Prueba de Deduplicación y Unicidad del Voto (Last Vote Wins)..." -ForegroundColor Yellow
Write-Host "Voter_001 votó inicialmente por candidate_A. Ahora revota por candidate_B:" -ForegroundColor Gray
Send-Vote -VoterId "voter_001" -CandidateId "candidate_B" -Region "NORTE" -ClientIp "192.168.1.10"

Write-Host "`n4. Prueba de Detección de Bots y Fraude (Ráfaga de votos desde una misma IP)..." -ForegroundColor Yellow
$ATTACKER_IP = "10.0.99.100"
for ($i = 6; $i -le 15; $i++) {
    $vId = "voter_{0:D3}" -f $i
    Write-Host "Ráfaga de votos desde IP atacante ($ATTACKER_IP)..." -ForegroundColor Magenta
    Send-Vote -VoterId $vId -CandidateId "candidate_A" -Region "SUR" -ClientIp $ATTACKER_IP
    Start-Sleep -Milliseconds 100
}

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host "Simulación completada con éxito." -ForegroundColor Cyan
Write-Host "Revise los dashboards:" -ForegroundColor Cyan
Write-Host "Dashboard Global:   http://localhost:8082/dashboard.html" -ForegroundColor White
Write-Host "Dashboard Regional: http://localhost:8083/dashboard.html" -ForegroundColor White
Write-Host "RabbitMQ UI:        http://localhost:15672 (guest/guest)" -ForegroundColor White
Write-Host "==========================================================" -ForegroundColor Cyan

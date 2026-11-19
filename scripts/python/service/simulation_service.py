"""
Servicio de Orquestación y Casos de Uso para Pruebas del Sistema de Votación.
Ejecuta escenarios reales:
1. Emisión de votos válidos (Happy Path con tickets SHA-256)
2. Rechazo de votante no habilitado (KTable / Censo estricto)
3. Prevención de voto duplicado con misma cédula (HTTP 409 + comprobante original)
4. Detección de bots y fraude por IP (State Pattern + Circuit Breaker)
"""

import time
from datetime import datetime, timezone
from typing import List

from ..client.voting_client import VotingApiClient
from ..domain.enums import ElectionType, Region, VoteSource
from ..domain.models import SimulationStepResult, SimulationSummary, VoteRequest


class VotingSimulationService:
    """Orquestador de baterías de pruebas end-to-end sobre la arquitectura distribuida."""

    def __init__(self, client: VotingApiClient):
        self.client = client

    def run_full_suite(self) -> SimulationSummary:
        """Ejecuta los 4 escenarios de prueba y consolida estadísticas."""
        summary = SimulationSummary()
        summary.started_at = datetime.now(timezone.utc)

        print("\n" + "=" * 70)
        print(" BATERÍA DE PRUEBAS END-TO-END: PLATAFORMA DE VOTACIÓN DISTRIBUIDA")
        print("=" * 70)

        # 0. Health Check
        ok, health_info = self.client.check_health()
        node_id = health_info.get("nodeId", "desconocido")
        az = health_info.get("availabilityZone", "desconocida")
        status_text = "EN LÍNEA (OK)" if ok else "FALLIDO (DOWN)"
        print(f"\n[Fase 0] Comprobación de Salud del Nodo: {status_text}")
        print(f"         Nodo Activo: {node_id} | Zona AWS: {az}")

        # 1. Happy Path: Votos Válidos
        print("\n[Fase 1] Votantes Ciudadanos Habilitados (Esperado: HTTP 200 OK + Ticket SHA-256)...")
        happy_votes = [
            VoteRequest("1001001", "María Fernanda Gómez", "cand_nac_01", "Bogotá", Region.CENTRO, ElectionType.NACIONAL, "190.25.1.10", VoteSource.REAL),
            VoteRequest("1001002", "Carlos Eduardo López", "cand_nac_02", "Medellín", Region.CENTRO, ElectionType.NACIONAL, "190.25.1.11", VoteSource.REAL),
            VoteRequest("1001003", "Laura Daniela Morales", "cand_nac_03", "Cali", Region.OESTE, ElectionType.NACIONAL, "190.25.1.12", VoteSource.REAL),
            VoteRequest("1001004", "Andrés Felipe Ruiz", "cand_reg_01", "Barranquilla", Region.NORTE, ElectionType.REGIONAL, "190.25.1.13", VoteSource.REAL),
            VoteRequest("1001005", "Valentina Burbano", "cand_reg_02", "Pasto", Region.SUR, ElectionType.REGIONAL, "190.25.1.14", VoteSource.REAL),
        ]
        for req in happy_votes:
            res = self.client.cast_vote(req, "Voto Ciudadano Válido")
            summary.record(res)
            print(f"  {res.summary_line}")

        # 2. Cédula No Elegible (KTable / Censo)
        print("\n[Fase 2] Validación de Censo y Elegibilidad (Esperado: HTTP 403 Forbidden)...")
        ineligible_req = VoteRequest(
            voter_id="0000000000",
            voter_name="Ciudadano No Empadronado",
            candidate_id="cand_nac_01",
            city="Bogotá",
            region=Region.CENTRO,
            election_type=ElectionType.NACIONAL,
            client_ip="190.25.1.99",
            vote_source=VoteSource.REAL,
        )
        res_ine = self.client.cast_vote(ineligible_req, "Cédula No Elegible")
        summary.record(res_ine)
        print(f"  {res_ine.summary_line}")

        # 3. Prevención de Voto Duplicado (Misma Cédula)
        print("\n[Fase 3] Unicidad del Voto e Idempotencia (Esperado: HTTP 409 Conflict)...")
        print("  -> Ciudadana 1001001 intenta revotar por cand_nac_02:")
        dup_req = VoteRequest(
            voter_id="1001001",
            voter_name="María Fernanda Gómez",
            candidate_id="cand_nac_02",
            city="Bogotá",
            region=Region.CENTRO,
            election_type=ElectionType.NACIONAL,
            client_ip="190.25.1.10",
            vote_source=VoteSource.REAL,
        )
        res_dup = self.client.cast_vote(dup_req, "Intento Voto Duplicado")
        summary.record(res_dup)
        print(f"  {res_dup.summary_line}")

        # 4. Detección de Bot / Ráfaga Anómala
        print("\n[Fase 4] Detección de Ataques / Ráfaga desde IP Atacante (Esperado: Alerta / Bloqueo)...")
        attacker_ip = "198.51.100.77"
        for i in range(1, 8):
            bot_voter = f"bot_sim_{i:04d}"
            bot_req = VoteRequest(
                voter_id=bot_voter,
                voter_name=f"Bot Virtual {i}",
                candidate_id="cand_nac_01",
                city="Cali",
                region=Region.OESTE,
                election_type=ElectionType.NACIONAL,
                client_ip=attacker_ip,
                vote_source=VoteSource.SIMULATED,
            )
            res_bot = self.client.cast_vote(bot_req, f"Ráfaga Bot #{i}")
            summary.record(res_bot)
            print(f"  {res_bot.summary_line}")
            time.sleep(0.05)

        summary.completed_at = datetime.now(timezone.utc)
        self._print_summary_card(summary)
        return summary

    def _print_summary_card(self, s: SimulationSummary):
        duration = 0.0
        if s.completed_at and s.started_at:
            duration = (s.completed_at - s.started_at).total_seconds()

        print("\n" + "=" * 70)
        print(" RESUMEN CUANTITATIVO DE LA EJECUCIÓN")
        print("=" * 70)
        print(f"  • Peticiones Totales Emitidas:   {s.total_requests}")
        print(f"  • Votos Aceptados (HTTP 200):    {s.accepted_votes}")
        print(f"  • Duplicados Bloqueados (409):   {s.duplicate_blocked}")
        print(f"  • No Elegibles Rechazados (403): {s.ineligible_rejected}")
        print(f"  • Bloqueos de Seguridad (IP):    {s.security_blocked}")
        print(f"  • Errores de Servidor (5xx):     {s.server_errors}")
        print(f"  • Tiempo Total de Ejecución:     {duration:.2f} segundos")
        print("=" * 70 + "\n")

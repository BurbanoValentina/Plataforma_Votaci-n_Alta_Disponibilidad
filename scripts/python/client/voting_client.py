"""
Cliente HTTP orientado a objetos para interactuar con la Plataforma de Votación.
Encapsula manejo de cabeceras, serialización, timeout, medición de latencias y parsing de respuestas.
"""

import json
import time
import urllib.error
import urllib.request
from typing import Any, Dict, Optional, Tuple

from ..config.settings import SimulatorSettings
from ..domain.models import SimulationStepResult, VoteRequest, VoteTicket


class VotingApiClient:
    """Cliente REST de alta resiliencia para pruebas contra AWS ALB y localhost."""

    def __init__(self, settings: Optional[SimulatorSettings] = None):
        self.settings = settings or SimulatorSettings()

    def check_health(self) -> Tuple[bool, Dict[str, Any]]:
        """Verifica el estado del nodo y la zona de disponibilidad (AWS AZ)."""
        req = urllib.request.Request(
            self.settings.health_endpoint,
            headers={"User-Agent": self.settings.user_agent, "Accept": "application/json"},
        )
        try:
            with urllib.request.urlopen(req, timeout=self.settings.timeout_seconds) as resp:
                data = json.loads(resp.read().decode("utf-8"))
                return resp.status == 200, data
        except Exception as e:
            return False, {"error": str(e)}

    def cast_vote(self, vote_req: VoteRequest, step_name: str = "Emitir Voto") -> SimulationStepResult:
        """
        Emite un voto hacia el Gateway y mapea respuestas 200, 409 (Duplicado), 403 (KTable/Bloqueo).
        """
        payload = vote_req.to_payload()
        encoded_data = json.dumps(payload).encode("utf-8")

        headers = {
            "Content-Type": "application/json",
            "Accept": "application/json",
            "User-Agent": self.settings.user_agent,
        }
        if vote_req.client_ip:
            headers["X-Forwarded-For"] = vote_req.client_ip
            headers["X-Real-IP"] = vote_req.client_ip

        req = urllib.request.Request(
            self.settings.vote_endpoint,
            data=encoded_data,
            headers=headers,
            method="POST",
        )

        start_time = time.perf_counter()
        try:
            with urllib.request.urlopen(req, timeout=self.settings.timeout_seconds) as resp:
                elapsed_ms = (time.perf_counter() - start_time) * 1000.0
                body = json.loads(resp.read().decode("utf-8"))
                ticket = None
                if "ticket" in body and isinstance(body["ticket"], dict):
                    ticket = VoteTicket.from_dict(body["ticket"])

                return SimulationStepResult(
                    step_name=step_name,
                    status_code=resp.status,
                    success=True,
                    voter_id=vote_req.voter_id,
                    candidate_id=vote_req.candidate_id,
                    response_body=body,
                    elapsed_ms=elapsed_ms,
                    ticket=ticket,
                )
        except urllib.error.HTTPError as e:
            elapsed_ms = (time.perf_counter() - start_time) * 1000.0
            error_text = ""
            body = {}
            try:
                error_text = e.read().decode("utf-8")
                body = json.loads(error_text)
            except Exception:
                body = {"raw": error_text}

            # Extraer ticket previo en caso de 409 Conflict
            ticket = None
            if "ticket" in body and isinstance(body["ticket"], dict):
                ticket = VoteTicket.from_dict(body["ticket"])

            error_msg = body.get("message") or body.get("error") or str(e)
            return SimulationStepResult(
                step_name=step_name,
                status_code=e.code,
                success=False,
                voter_id=vote_req.voter_id,
                candidate_id=vote_req.candidate_id,
                response_body=body,
                error_message=error_msg,
                elapsed_ms=elapsed_ms,
                ticket=ticket,
            )
        except Exception as e:
            elapsed_ms = (time.perf_counter() - start_time) * 1000.0
            return SimulationStepResult(
                step_name=step_name,
                status_code=0,
                success=False,
                voter_id=vote_req.voter_id,
                candidate_id=vote_req.candidate_id,
                error_message=f"Fallo de conexión: {e}",
                elapsed_ms=elapsed_ms,
            )

    def lookup_tickets(self, cedula: str) -> Dict[str, Any]:
        """Consulta los comprobantes emitidos para una cédula."""
        url = f"{self.settings.ticket_endpoint}/{cedula}"
        req = urllib.request.Request(url, headers={"Accept": "application/json"})
        try:
            with urllib.request.urlopen(req, timeout=self.settings.timeout_seconds) as resp:
                return json.loads(resp.read().decode("utf-8"))
        except Exception as e:
            return {"found": False, "error": str(e)}

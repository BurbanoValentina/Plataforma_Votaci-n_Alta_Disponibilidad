"""
Modelos de Dominio enriquecidos para la Simulación Electoral.
Incluyen validaciones de negocio, serialización JSON y formateo de comprobantes.
"""

from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any, Dict, Optional
from .enums import ElectionType, Region, VoteSource


@dataclass
class VoteRequest:
    """
    Solicitud de emisión de voto con validación Fail-Fast.
    Corresponde a VoteRequestRecord en Java.
    """

    voter_id: str
    voter_name: str
    candidate_id: str
    city: str
    region: Optional[Region] = None
    election_type: ElectionType = ElectionType.NACIONAL
    client_ip: Optional[str] = None
    vote_source: VoteSource = VoteSource.REAL

    def __post_init__(self):
        self.voter_id = str(self.voter_id).strip()
        self.voter_name = str(self.voter_name).strip()
        self.candidate_id = str(self.candidate_id).strip()
        self.city = str(self.city).strip()
        if not self.voter_id:
            raise ValueError("voter_id no puede estar vacío.")
        if not self.candidate_id:
            raise ValueError("candidate_id no puede estar vacío.")
        if not self.city:
            raise ValueError("city no puede estar vacía.")

    def to_payload(self) -> Dict[str, Any]:
        """Convierte el modelo a payload JSON compatible con el Gateway REST."""
        payload: Dict[str, Any] = {
            "voterId": self.voter_id,
            "voterName": self.voter_name,
            "candidateId": self.candidate_id,
            "city": self.city,
            "electionType": self.election_type.value if isinstance(self.election_type, ElectionType) else str(self.election_type),
            "voteSource": self.vote_source.value if isinstance(self.vote_source, VoteSource) else str(self.vote_source),
        }
        if self.region:
            payload["region"] = self.region.value if isinstance(self.region, Region) else str(self.region)
        if self.client_ip:
            payload["clientIp"] = self.client_ip
        return payload


@dataclass
class VoteTicket:
    """Comprobante electoral oficial devuelto por el nodo Edge."""

    ticket_code: str
    voter_id: str
    voter_name: str
    city: str
    region: str
    election_type: str
    candidate_id: str
    candidate_name: str
    vote_source: str
    handled_by_node: str
    verification_hash: str
    voted_at: str

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> "VoteTicket":
        return cls(
            ticket_code=str(data.get("ticketCode", "")),
            voter_id=str(data.get("voterId", "")),
            voter_name=str(data.get("voterName", "Ciudadano")),
            city=str(data.get("city", "Bogotá")),
            region=str(data.get("region", "CENTRO")),
            election_type=str(data.get("electionType", "NACIONAL")),
            candidate_id=str(data.get("candidateId", "")),
            candidate_name=str(data.get("candidateName", "")),
            vote_source=str(data.get("voteSource", "REAL")),
            handled_by_node=str(data.get("handledByNode", "unknown")),
            verification_hash=str(data.get("verificationHash", "")),
            voted_at=str(data.get("votedAt", "")),
        )


@dataclass
class SimulationStepResult:
    """Resultado individual de una prueba o envío de voto."""

    step_name: str
    status_code: int
    success: bool
    voter_id: str
    candidate_id: str
    response_body: Dict[str, Any] = field(default_factory=dict)
    error_message: Optional[str] = None
    elapsed_ms: float = 0.0
    ticket: Optional[VoteTicket] = None

    @property
    def summary_line(self) -> str:
        """Línea de resumen formateada para consola."""
        tag = "[OK 200]" if self.status_code == 200 else f"[HTTP {self.status_code}]"
        info = f"Ticket={self.ticket.ticket_code}" if self.ticket else (self.error_message or "Procesado")
        return f"{tag:<12} Cédula={self.voter_id:<12} Cand={self.candidate_id:<16} ({self.elapsed_ms:.1f}ms) -> {info}"


@dataclass
class SimulationSummary:
    """Resumen cuantitativo de la batería de pruebas."""

    total_requests: int = 0
    accepted_votes: int = 0
    duplicate_blocked: int = 0
    ineligible_rejected: int = 0
    security_blocked: int = 0
    server_errors: int = 0
    started_at: datetime = field(default_factory=lambda: datetime.now(timezone.utc))
    completed_at: Optional[datetime] = None

    def record(self, result: SimulationStepResult):
        self.total_requests += 1
        code = result.status_code
        if code == 200:
            self.accepted_votes += 1
        elif code == 409:
            self.duplicate_blocked += 1
        elif code == 403:
            err = (result.error_message or "").upper()
            if "BLOQUE" in err or "SECURITY" in err or "FRAUDE" in err:
                self.security_blocked += 1
            else:
                self.ineligible_rejected += 1
        elif code >= 500:
            self.server_errors += 1

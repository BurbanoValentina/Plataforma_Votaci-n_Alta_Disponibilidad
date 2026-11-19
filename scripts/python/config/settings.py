"""
Configuración centralizada y tipada para la simulación electoral.
Admite inyección por variables de entorno y provee defaults para desarrollo y AWS.
"""

import os
from dataclasses import dataclass


@dataclass(frozen=True)
class SimulatorSettings:
    """Configuración inmutable para el cliente y el ejecutor de simulación."""

    base_url: str = os.environ.get("VOTING_API_URL", "http://localhost:8080")
    timeout_seconds: float = float(os.environ.get("VOTING_TIMEOUT_SEC", "10.0"))
    user_agent: str = "VotingPlatformSimulator/2.0.0 (Python3)"

    @property
    def vote_endpoint(self) -> str:
        """Endpoint para emisión de voto ciudadano."""
        clean = self.base_url.rstrip("/")
        return f"{clean}/api/vote"

    @property
    def health_endpoint(self) -> str:
        """Endpoint de salud del nodo y disponibilidad de AWS ALB."""
        clean = self.base_url.rstrip("/")
        return f"{clean}/api/health"

    @property
    def catalog_endpoint(self) -> str:
        """Endpoint del catálogo de candidatos y circunscripciones."""
        clean = self.base_url.rstrip("/")
        return f"{clean}/api/catalog"

    @property
    def ticket_endpoint(self) -> str:
        """Endpoint de consulta de comprobantes electorales por cédula."""
        clean = self.base_url.rstrip("/")
        return f"{clean}/api/tickets"

    @property
    def global_results_endpoint(self) -> str:
        """Endpoint REST de resultados consolidados en vivo."""
        clean = self.base_url.rstrip("/")
        return f"{clean}/api/results/global"

    @property
    def regional_results_endpoint(self) -> str:
        """Endpoint REST de resultados regionales con ponderación electoral."""
        clean = self.base_url.rstrip("/")
        return f"{clean}/api/results/regional"

    @property
    def prediction_endpoint(self) -> str:
        """Endpoint de proyección de segunda vuelta electoral."""
        clean = self.base_url.rstrip("/")
        return f"{clean}/api/prediction/runoff"

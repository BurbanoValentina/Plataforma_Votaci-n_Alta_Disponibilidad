"""
Enumeraciones de Dominio del Sistema Electoral Digital.
Mapean exactamente las definiciones de Java Common-Domain.
"""

from enum import Enum


class ElectionType(str, Enum):
    """Tipo de elección en curso."""
    NACIONAL = "NACIONAL"
    REGIONAL = "REGIONAL"


class Region(str, Enum):
    """Regiones electorales de Colombia."""
    NORTE = "NORTE"
    SUR = "SUR"
    CENTRO = "CENTRO"
    ESTE = "ESTE"
    OESTE = "OESTE"


class VoteSource(str, Enum):
    """Origen del voto para auditoría y KPIs diferenciados."""
    REAL = "REAL"
    SIMULATED = "SIMULATED"

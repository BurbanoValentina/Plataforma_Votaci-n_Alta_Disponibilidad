"""
Punto de Entrada CLI del Simulador Electoral Digital.
Permite ejecutar suites completas, verificar salud, inyectar ráfagas y consultar tickets.
"""

import argparse
import sys

from .client.voting_client import VotingApiClient
from .config.settings import SimulatorSettings
from .service.simulation_service import VotingSimulationService


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="voting-simulator",
        description="Simulador de Votación y Validador de Arquitectura Distribuida (AWS ALB / Docker)",
    )
    parser.add_argument(
        "--url",
        default=None,
        help="URL base del balanceador o nodo (ej: http://localhost:8080 o DNS de ALB)",
    )
    parser.add_argument(
        "--health-only",
        action="store_true",
        help="Solo comprueba la salud del nodo y sale.",
    )
    parser.add_argument(
        "--lookup",
        metavar="CEDULA",
        help="Consulta los comprobantes emitidos para una cédula específica.",
    )
    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()

    settings = SimulatorSettings(base_url=args.url) if args.url else SimulatorSettings()
    client = VotingApiClient(settings)

    if args.health_only:
        ok, data = client.check_health()
        print(f"Health Check contra {settings.base_url}: {'UP (200)' if ok else 'DOWN'}")
        print(data)
        return 0 if ok else 1

    if args.lookup:
        tickets = client.lookup_tickets(args.lookup)
        print(f"Comprobantes para la cédula {args.lookup}:")
        print(tickets)
        return 0

    service = VotingSimulationService(client)
    summary = service.run_full_suite()

    # Si hay errores 5xx o fallaron votos válidos, retornar código != 0
    if summary.server_errors > 0 or summary.accepted_votes == 0:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())

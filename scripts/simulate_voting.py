#!/usr/bin/env python3
"""
Script de Simulación de Votación en Vivo y Verificación End-to-End
Plataforma de Votación de Alta Disponibilidad
"""

import os
import json
import time
import urllib.request
import urllib.error

API_URL = os.environ.get("VOTING_API_URL", "http://localhost:8085/vote")

def send_vote(voter_id: str, candidate_id: str, region: str, client_ip: str):
    payload = {
        "voterId": voter_id,
        "candidateId": candidate_id,
        "region": region,
        "clientIp": client_ip
    }
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(API_URL, data=data, headers={"Content-Type": "application/json"})

    try:
        with urllib.request.urlopen(req) as resp:
            body = json.loads(resp.read().decode("utf-8"))
            print(f"[HTTP 200 OK] Voto Aceptado: Voter={voter_id} -> Cand={candidate_id} ({region}) IP={client_ip}")
            return body
    except urllib.error.HTTPError as e:
        err = e.read().decode("utf-8")
        print(f"[HTTP {e.code} RECHAZADO] Voter={voter_id}: {err}")
        return None
    except Exception as e:
        print(f"[ERROR CONEXION] {e}")
        return None

def main():
    print("=" * 60)
    print("Iniciando Simulación de Votación en Vivo")
    print("=" * 60)

    print("\n1. Enviar Votos Elegibles (HTTP 200)...")
    send_vote("voter_001", "candidate_A", "NORTE", "192.168.1.10")
    send_vote("voter_002", "candidate_B", "SUR", "192.168.1.11")
    send_vote("voter_003", "candidate_A", "CENTRO", "192.168.1.12")
    send_vote("voter_004", "candidate_C", "ESTE", "192.168.1.13")
    send_vote("voter_005", "candidate_B", "OESTE", "192.168.1.14")

    print("\n2. Enviar Voto No Elegible (HTTP 403 Forbidden)...")
    send_vote("voter_inelegible_999", "candidate_A", "NORTE", "192.168.1.200")

    print("\n3. Deduplicación (Last Vote Wins)...")
    print("voter_001 revota por candidate_B:")
    send_vote("voter_001", "candidate_B", "NORTE", "192.168.1.10")

    print("\n4. Detección de Bot / Fraude (Ráfaga desde una misma IP)...")
    attacker_ip = "10.0.99.100"
    for i in range(6, 16):
        v_id = f"voter_{i:03d}"
        send_vote(v_id, "candidate_A", "SUR", attacker_ip)
        time.sleep(0.08)

    print("\n" + "=" * 60)
    print("Simulación Finalizada.")
    print("Dashboards disponibles:")
    print("  Global:   http://localhost:8082/dashboard.html")
    print("  Regional: http://localhost:8083/dashboard.html")
    print("=" * 60)

if __name__ == "__main__":
    main()

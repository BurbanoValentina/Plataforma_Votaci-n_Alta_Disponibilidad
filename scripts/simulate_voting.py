#!/usr/bin/env python3
"""
Ejecutor Principal de Simulación de Votación en Vivo y Verificación End-to-End.
Delega al paquete modular estructurado en `scripts/python/`.

Estructura modular:
  scripts/python/
  ├── config/     (Ajustes y endpoints)
  ├── domain/     (Modelos de dominio, validaciones y enums)
  ├── client/     (Cliente REST orientado a objetos con resiliencia)
  ├── service/    (Orquestación de pruebas: happy path, fraude, duplicados)
  └── main.py     (Punto de entrada CLI con argumentos y tablas resumen)
"""

import os
import sys

# Agregar la raíz del directorio scripts al path de Python
current_dir = os.path.dirname(os.path.abspath(__file__))
if current_dir not in sys.path:
    sys.path.insert(0, current_dir)

from python.main import main

if __name__ == "__main__":
    sys.exit(main())

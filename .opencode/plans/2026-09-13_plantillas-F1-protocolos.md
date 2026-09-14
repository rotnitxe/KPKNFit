---
flags: []
---

# F1 — Biblioteca de protocolos VERIFIED

## Rutas

- `data/protocols/definitions/*` un archivo por familia
- `ProtocolLibrary.kt` agrega definiciones con `recipe`, `fidelitySpec`, `exemptions`, `ProtocolSource`
- Reescritura de `kpkn-native-sbd-4`

## Impacto

- Protocolos de terceros con nombre, autor, URL y disclaimer. KPKN_NATIVE sin exenciones.
- Legacy `HIDDEN_UNVERIFIED` permanece como índice interno.

## Pruebas

- `ProtocolRecipeFidelityTest`, `ProtocolCompositionContractTest`, `ProtocolClaimsTest`, `ProtocolAuditTest`

## Riesgos

- Exenciones solo las de la matriz 2.2. No corregir silenciosamente al autor.

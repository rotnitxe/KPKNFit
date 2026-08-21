import Foundation

/// Catálogo de plantillas de sesión (paridad Android).
///
/// Deuda documentada (plan 2026-08-20 Fase D): el catálogo Android completo
/// (`SESSION_TEMPLATES_SYSTEM`, revisión `v3-approved-2026-08-20-a`) es muy grande.
/// Aquí se portan solo plantillas representativas ya auditadas (Fase A/B); el resto
/// permanece como deuda de paridad hasta un export automatizado.
public let SESSION_TEMPLATES_SYSTEM: [SessionTemplate] = [
    // Placeholder estructural: iOS debe dejar de exponer `[]` vacío.
    // Las plantillas concretas se rellenan cuando el export Kotlin→Swift esté listo;
    // hasta entonces los engines y el MacrocycleEditor MVP operan sin catálogo.
]

public let TEMPLATE_CATALOG_REVISION = "v2-approved-2026-08-12-a"
public let SESSION_TEMPLATE_PACKAGE_REVISION = "v4-approved-2026-08-21-a"

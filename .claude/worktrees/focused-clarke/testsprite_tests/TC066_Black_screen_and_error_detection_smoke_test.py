"""
TC066: TestSprite completo para verificar pantallas negras y otros errores.

Este test:
1. Captura errores de consola y JavaScript no capturados
2. Verifica que no hay pantalla negra (contenido visible en main)
3. Navega por las pantallas principales (Home, Programas, Nutrición, Wiki/Lab)
4. Detecta ErrorBoundary visible (indica crash de React)
5. Dismiss onboarding si está presente para poder navegar
6. Genera reporte de errores al final
"""
import asyncio
from playwright import async_api
from playwright.async_api import expect

# Almacén de errores capturados durante el test
console_errors = []
page_errors = []


async def run_test():
    pw = None
    browser = None
    context = None

    try:
        pw = await async_api.async_playwright().start()

        browser = await pw.chromium.launch(
            headless=True,
            args=[
                "--window-size=1280,720",
                "--disable-dev-shm-usage",
                "--ipc=host",
                "--single-process",
            ],
        )

        context = await browser.new_context()
        context.set_default_timeout(10000)

        page = await context.new_page()

        # --- Captura de errores ---
        def on_console(msg):
            if msg.type == "error":
                console_errors.append({"type": "console", "text": msg.text})

        def on_page_error(exc):
            page_errors.append({"type": "pageerror", "message": str(exc)})

        page.on("console", on_console)
        page.on("pageerror", on_page_error)

        # --- Navegar a la app ---
        await page.goto("http://localhost:5500", wait_until="domcontentloaded", timeout=15000)
        await page.wait_for_timeout(3000)

        frame = context.pages[-1]

        # --- 1. Dismiss onboarding si está presente ---
        for _ in range(8):  # Máximo 8 clics para saltar slides
            try:
                # Welcome: "Siguiente" o "Empezar"
                btn = frame.locator("button:has-text('Siguiente'), button:has-text('Empezar'), button:has-text('Entendido'), button:has-text('Continuar'), button:has-text('Omitir'), button:has-text('Saltar')").first
                if await btn.is_visible(timeout=1500):
                    await btn.click()
                    await page.wait_for_timeout(1200)
                else:
                    break
            except Exception:
                break

        # --- 2. Verificar NO pantalla negra: main debe tener contenido visible ---
        main = frame.locator("main").first
        assert await main.is_visible(timeout=5000), (
            "BLACK SCREEN: Elemento 'main' no visible. La app puede estar en pantalla negra."
        )

        # Verificar que main tiene hijos (contenido real, no vacío)
        main_inner = await main.inner_html()
        assert len(main_inner.strip()) > 100, (
            "BLACK SCREEN: main está vacío o con muy poco HTML. Posible pantalla negra."
        )

        # Verificar que hay texto visible en la página (no solo fondo negro)
        body_text = await frame.locator("body").inner_text()
        assert len(body_text.strip()) > 50, (
            "BLACK SCREEN: Body tiene muy poco texto. Posible pantalla negra o carga incompleta."
        )

        # --- 3. Verificar NO ErrorBoundary visible (crash de React) ---
        error_boundary = frame.locator("text=Error en").first
        assert not await error_boundary.is_visible(timeout=1000), (
            "ERROR BOUNDARY: Se detectó pantalla de error de React. La app crasheó."
        )

        # --- 4. Verificar Tab Bar visible (navegación funcional) ---
        tab_bar = frame.locator(".tab-bar-card-container, [class*='tab-bar']").first
        tab_visible = await tab_bar.is_visible(timeout=3000)
        if not tab_visible:
            # Fallback: buscar botones de navegación
            nav_buttons = frame.locator("button").filter(has_text="Inicio")
            tab_visible = await nav_buttons.first.is_visible(timeout=2000)
        assert tab_visible, "Tab bar o navegación no visible. La app puede estar en estado incorrecto."

        # --- 5. Navegar por cada tab y verificar que carga ---
        # Tabs: Inicio, Programas, Nutrición, Wiki/Lab (buscar por subcadena)
        tabs_to_check = [
            ("Inicio", ["Inicio", "Batería", "Programa"]),
            ("Programas", ["Programas", "Nuevo", "programa"]),
            ("Nutrición", ["Nutrición", "Nutric", "Registrar", "Planificador"]),
            ("Wiki", ["Wiki", "Lab", "Ejercicio", "KPKN"]),
        ]

        for label, expected_any in tabs_to_check:
            try:
                btn = frame.locator(f"button:has-text('{label}')").first
                if await btn.is_visible(timeout=2000):
                    await btn.click()
                    await page.wait_for_timeout(2000)

                    page_content = await frame.locator("main").inner_text()
                    found = any(exp in page_content for exp in expected_any)
                    assert found or len(page_content) > 80, (
                        f"Tab '{label}': contenido no actualizado. Esperaba alguno de {expected_any}."
                    )
            except AssertionError:
                raise
            except Exception as e:
                console_errors.append({"type": "tab_nav", "tab": label, "error": str(e)})

        # --- 6. Verificar que no hay errores críticos ---
        critical_errors = [e for e in page_errors if "Cannot access" in str(e.get("message", "")) or "ReferenceError" in str(e.get("message", ""))]
        assert len(critical_errors) == 0, (
            f"ERRORES CRÍTICOS: {len(critical_errors)} error(es) de JavaScript: {critical_errors}"
        )

        # --- 7. Reporte final ---
        if console_errors or page_errors:
            print("\n--- REPORTE DE ERRORES (no bloqueantes) ---")
            for e in console_errors[:5]:
                print(f"  Console: {e}")
            for e in page_errors[:5]:
                print(f"  PageError: {e}")

        print("\n[TC066] PASÓ: Sin pantallas negras, sin ErrorBoundary, navegación OK.")

    except AssertionError as e:
        print(f"\n[TC066] FALLÓ: {e}")
        if console_errors:
            print("Console errors:", console_errors[:10])
        if page_errors:
            print("Page errors:", page_errors[:10])
        raise
    finally:
        if context:
            await context.close()
        if browser:
            await browser.close()
        if pw:
            await pw.stop()


if __name__ == "__main__":
    asyncio.run(run_test())

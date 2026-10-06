// Componente React para los overlays de "módulo completado" del wizard KPKN.
// Uso:
//   const [done, setDone] = useState(null);           // "basicos" | "entreno" | "nutricion" | "rings" | null
//   <KpknModuleOverlay type={done} onClose={() => { setDone(null); siguientePaso(); }} />
//   ...al guardar el módulo: setDone("basicos");
import { useEffect, useRef } from "react";
import { showModuleComplete } from "./kpkn-module-overlay.js";

export default function KpknModuleOverlay({ type, title, subtitle, cta, autoClose = 0, onClose, container }) {
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;

  useEffect(() => {
    if (!type) return undefined;
    const ov = showModuleComplete(type, {
      title, subtitle, cta, autoClose,
      container: container?.current || undefined,
      onClose: () => onCloseRef.current && onCloseRef.current(type),
    });
    return () => ov.closeSilently();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [type]);

  return null;
}

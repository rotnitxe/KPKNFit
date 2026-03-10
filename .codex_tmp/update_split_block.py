from pathlib import Path
path = Path('components/ProgramDetail.tsx')
text = path.read_text()
old = """
                                                <button
                                                    onClick={() => setIsSplitChangerOpen(true)}
                                                    className=\"px-6 py-3 rounded-full bg-gradient-to-r from-purple-500 to-purple-600 text-white text-[9px] font-black uppercase tracking-[0.15em] hover:opacity-90 transition-all shadow-lg\"
                                                >
                                                    Abrir GalerÇða de Splits
                                                </button>
"""
new = """
                                                <p className=\"text-[11px] text-zinc-500 leading-relaxed uppercase tracking-[0.2em]\">
                                                    El panel lateral se mantiene abierto automáticamente para que elijas tu plantilla sin pasos extra.
                                                </p>
"""
if old not in text:
    raise RuntimeError('old block not found')
text = text.replace(old, new, 1)
path.write_text(text)

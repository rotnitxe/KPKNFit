from pathlib import Path
path = Path('components/nutrition/NutritionWizard.tsx')
text = path.read_text()
old = "<div className=\"sticky top-0 z-20 backdrop-blur-xl bg-[var(--md-sys-color-surface)]/85 border-b border-black/[0.05]\">"
new = "<div className=\"sticky top-0 z-20 backdrop-blur-2xl bg-white/95 border-b border-black/[0.08] shadow-lg\">"
if old not in text:
    raise SystemExit('header pattern missing')
path.write_text(text.replace(old, new, 1))

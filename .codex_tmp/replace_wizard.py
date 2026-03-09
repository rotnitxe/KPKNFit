from pathlib import Path
path = Path('components/nutrition/NutritionWizard.tsx')
text = path.read_text()
old = "const sectionClass = 'bg-white/70 border border-black/[0.05]'"
new = "const sectionClass = 'bg-white/90 border border-black/[0.08] shadow-[0_14px_40px_-32px_rgba(0,0,0,0.5)]'"
if old not in text:
    raise SystemExit('pattern not found')
path.write_text(text.replace(old, new, 1))

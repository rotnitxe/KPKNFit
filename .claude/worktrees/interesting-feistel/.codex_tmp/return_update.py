from pathlib import Path
path=Path('components/nutrition/NutritionWizard.tsx')
text=path.read_text()
old='<div className="min-h-full flex flex-col bg-[var(--md-sys-color-surface)]">'
new='<div className="min-h-full flex flex-col bg-gradient-to-b from-[var(--md-sys-color-surface)] via-white to-white relative overflow-hidden">'
if old not in text:
    raise SystemExit('return pattern missing')
path.write_text(text.replace(old,new,1))

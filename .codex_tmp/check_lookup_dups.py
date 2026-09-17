import re
from pathlib import Path

text = Path("android-native/app/src/main/java/com/example/kpkn/data/exercises/ExerciseTechniqueImageLookup.kt").read_text(encoding="utf-8")
blocks = re.findall(r"(\w+_DEFINITION_ID) -> listOf\((.*?)\)\n", text, re.S)
dups = []
for name, body in blocks:
    draws = re.findall(r"R\.drawable\.(\w+)", body)
    impls = re.findall(r'ExerciseTechniqueImageVariant\("([^"]+)"', body)
    if len(impls) > 1 and len(draws) != len(set(draws)):
        dups.append((name, impls, draws))
print("dup drawables in multi-implement defs:", dups)
print("variant blocks", len(blocks))

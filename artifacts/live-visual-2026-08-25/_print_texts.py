import re
from pathlib import Path
import sys
p = Path(sys.argv[1])
print(re.findall(r'text="([^"]+)"', p.read_text(encoding="utf-8", errors="ignore"))[:40])

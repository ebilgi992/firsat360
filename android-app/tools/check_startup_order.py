#!/usr/bin/env python3
"""Açılış sırası denetimi (0.2.0 çökmesinden sonra eklendi).

Android'de pencere görünümü (DecorView) setContentView() ile oluşur. Ondan önce window.insetsController,
window.decorView.windowInsetsController vb. çağrılırsa Android 11+ cihazlarda NullPointerException ile çöker.
Bu betik MainActivity'nin açılış fonksiyonlarında bu çağrıların setContentView'dan önce yapılmadığını kontrol eder.
"""
import re
import sys
from pathlib import Path

RISKY = [r"\binsetsController\b", r"\bwindowInsetsController\b", r"\bdecorView\b", r"applySystemBars\(\)",
         r"findViewById", r"requestApplyInsets"]
src = Path(sys.argv[1]).read_text(encoding="utf-8")
problems = []
for fn in ("onCreate", "buildUi"):
    m = re.search(r"fun " + fn + r"\([^)]*\)\s*\{", src)
    if not m:
        continue
    i, depth = m.end(), 1
    while depth and i < len(src):
        depth += {"{": 1, "}": -1}.get(src[i], 0)
        i += 1
    body = src[m.end():i]
    body_code = "\n".join(l.split("//")[0] for l in body.splitlines())
    sc = body_code.find("setContentView(")
    if sc < 0:
        continue
    before = body_code[:sc]
    for pat in RISKY:
        if re.search(pat, before):
            problems.append(f"{fn}: '{pat}' setContentView'dan önce kullanılmış")
print("Açılış sırası:", "TAMAM" if not problems else "; ".join(problems))
sys.exit(1 if problems else 0)

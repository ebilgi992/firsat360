"""React Native katalog köprüsü için betik paketini üretir (tek kaynak: Android assets/bridge/*.js).

    python scripts/gen_rn_bridge.py          → clients/react-native/src/bridgeScripts.ts yazar
    python scripts/gen_rn_bridge.py --check  → dosya güncel değilse 1 ile çıkar (testler kullanır)

Her profil betiği Android'de evaluateJavascript ile çalışan, JSON metni döndüren DURUMSUZ bir ifadedir. React Native'de
injectJavaScript dönüş değeri vermediğinden betik, sonucu window.ReactNativeWebView.postMessage ile gönderen bir sarmalayıcı
içine konur. Pazar yapılandırması config/client_bridge.json'dan gelir (feed'deki "client_bridge" ile aynı).
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BRIDGE = ROOT / "android-app/app/src/main/assets/bridge"
CONFIG = ROOT / "config/client_bridge.json"
OUT = ROOT / "clients/react-native/src/bridgeScripts.ts"


def wrap(common: str, profile: str) -> str:
    # common.js bir "var F360 = …;" bildirimi, profil betiği bir ifade. Sayfanın genel kapsamını kirletmemek için IIFE.
    return ("(function(){try{\n" + common.strip() + "\nvar __f360 = " + profile.strip().rstrip(";") +
            ";\nwindow.ReactNativeWebView.postMessage(String(__f360));\n}catch(e){window.ReactNativeWebView.postMessage("
            "JSON.stringify({type:'pending',error:String(e)}));}})();\ntrue;")


def render() -> str:
    common = (BRIDGE / "common.js").read_text(encoding="utf-8")
    profiles = sorted(p.stem for p in BRIDGE.glob("*.js") if p.stem != "common")
    cfg = json.loads(CONFIG.read_text(encoding="utf-8"))
    markets = {k: {"url": v["url"], "profile": v["profile"], "allowedHosts": v["allowed_hosts"]}
               for k, v in cfg["markets"].items() if v.get("client_rendered")}
    lines = [
        "// OTOMATİK ÜRETİLDİ — elle düzenlemeyin. Kaynak: android-app/app/src/main/assets/bridge/*.js ve",
        "// config/client_bridge.json. Yeniden üretmek için: python scripts/gen_rn_bridge.py",
        "",
        "export type BridgeProfile = " + " | ".join(json.dumps(p) for p in profiles) + ";",
        "",
        "export interface BridgeEntry {",
        "  url: string;",
        "  profile: BridgeProfile | string;",
        "  allowedHosts: string[];",
        "}",
        "",
        "/** config/client_bridge.json'un derleme anındaki kopyası; çalışırken feed.json'daki client_bridge önceliklidir. */",
        "export const BRIDGE_MARKETS: Record<string, BridgeEntry> = " + json.dumps(markets, ensure_ascii=False, indent=2) + ";",
        "",
        "/** injectJavaScript için hazır betikler: sonuç window.ReactNativeWebView.postMessage ile gelir. */",
        "export const BRIDGE_SCRIPTS: Record<string, string> = {",
    ]
    for p in profiles:
        src = wrap(common, (BRIDGE / f"{p}.js").read_text(encoding="utf-8"))
        lines.append(f"  {json.dumps(p)}: {json.dumps(src, ensure_ascii=False)},")
    lines += ["};", "", "export function bridgeScript(profile: string): string {",
              "  return BRIDGE_SCRIPTS[profile] ?? BRIDGE_SCRIPTS.generic;", "}", ""]
    return "\n".join(lines)


def main() -> int:
    text = render()
    if "--check" in sys.argv:
        ok = OUT.exists() and OUT.read_text(encoding="utf-8") == text
        if not ok:
            print("clients/react-native/src/bridgeScripts.ts güncel değil: python scripts/gen_rn_bridge.py", file=sys.stderr)
        return 0 if ok else 1
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(text, encoding="utf-8")
    print(f"yazıldı: {OUT.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

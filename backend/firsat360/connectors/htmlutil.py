"""HTML ayrıştırma yardımcıları (selectolax)."""
from __future__ import annotations

import re

from selectolax.lexbor import LexborHTMLParser as HTMLParser
from selectolax.lexbor import LexborNode as Node

PRICE_TOKEN = re.compile(r"(?:₺\s*)?\d{1,3}(?:\.\d{3})*\s*,\s*\d{2}\s*(?:₺|TL)?|\d+\.\d{2}\s*TL|₺\s*\d+(?:,\d{2})?")


def text_of(node: Node | None) -> str:
    if node is None:
        return ""
    return re.sub(r"\s+", " ", node.text(separator=" ")).strip()


def price_tokens(text: str) -> list[str]:
    return [m.group(0).strip() for m in PRICE_TOKEN.finditer(text) if re.search(r"[₺]|TL", m.group(0))]


def ancestor_with(node: Node, predicate, max_up: int = 6) -> Node | None:
    cur = node
    for _ in range(max_up):
        if cur is None:
            return None
        if predicate(cur):
            return cur
        cur = cur.parent
    return None


def is_struck(node: Node) -> bool:
    cur = node
    for _ in range(3):
        if cur is None:
            return False
        if cur.tag in ("del", "s", "strike"):
            return True
        cls = (cur.attributes.get("class") or "").lower()
        style = (cur.attributes.get("style") or "").lower()
        if "line-through" in cls or "line-through" in style or "old" in cls or "strike" in cls:
            return True
        cur = cur.parent
    return False


def parse(html: str) -> HTMLParser:
    return HTMLParser(html)


def img_src(node: Node | None) -> str | None:
    """Gecikmeli yükleme dahil görsel adresi: data-src, data-original, data-lazy, src, srcset (ilk). data: URI yok sayılır."""
    if node is None:
        return None
    attrs = node.attributes
    for key in ("data-src", "data-original", "data-lazy", "src"):
        v = (attrs.get(key) or "").strip()
        if v and not v.startswith("data:"):
            return v
    srcset = (attrs.get("srcset") or attrs.get("data-srcset") or "").strip()
    if srcset:
        first = srcset.split(",")[0].strip().split(" ")[0]
        if first and not first.startswith("data:"):
            return first
    return None


def find_image(card: Node, must_contain: str, exclude: tuple[str, ...] = ()) -> str | None:
    for img in card.css("img, source"):
        u = img_src(img)
        if u and must_contain in u and not any(x in u for x in exclude):
            return u
    return None

#!/usr/bin/env python3
"""minSdk API denetimi: derlenmiş (küçültülmüş) sınıfların başvurduğu Android/Java API'lerinin
minSdk platformunda (varsayılan API 26) gerçekten var olup olmadığını kontrol eder.

Gradle/Android Lint bu ortamda kullanılamadığı için yazıldı. Yöntem:
  1) Uygulama jar'ındaki tüm sınıflar ``javap -c -p`` ile açılır; ``Method/InterfaceMethod/Field`` başvuruları
     toplanır (yalnızca android.*, java.*, javax.*, dalvik.*, org.json.*, org.xml.*, org.w3c.* sahipleri).
  2) Her başvuru minSdk android.jar'ında sınıf + üye (ad ve tanımlayıcı) olarak aranır; kalıtım zinciri
     (üst sınıf ve arayüzler) izlenir.
  3) Bulunmayanlar hata olarak raporlanır. Yalnızca ``Build.VERSION.SDK_INT`` kontrolüyle korunan ve
     izin listesinde gerekçesiyle yazılan başvurular kabul edilir.

Kullanım: check_min_api.py <app.jar> <android-minSdk.jar> [izin_listesi.txt]
Çıkış kodu: 0 = sorun yok, 1 = minSdk'da olmayan korumasız API başvurusu var.
"""
from __future__ import annotations

import re
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

PLATFORM_PREFIXES = ("android/", "java/", "javax/", "dalvik/", "org/json/", "org/xml/", "org/w3c/")
REF_RE = re.compile(r"// (Method|InterfaceMethod|Field) ([\w/$]+)\.([\w$<>]+):(\S+)")
CLASS_REF_RE = re.compile(r"// class ([\w/$\[;]+)")


def javap(args: list[str]) -> str:
    out = subprocess.run(["javap", *args], capture_output=True, text=True, env={"JAVA_TOOL_OPTIONS": "", "PATH": "/usr/bin:/bin"})
    return out.stdout


def app_references(app_jar: Path) -> dict[tuple[str, str, str, str], set[str]]:
    refs: dict[tuple[str, str, str, str], set[str]] = {}
    with tempfile.TemporaryDirectory() as tmp, zipfile.ZipFile(app_jar) as z:
        names = [n for n in z.namelist() if n.endswith(".class")]
        z.extractall(tmp, names)
        app_classes = {n[:-6] for n in names}
        for i in range(0, len(names), 300):
            chunk = [n[:-6].replace("/", ".") for n in names[i:i + 300]]
            text = javap(["-c", "-p", "-cp", tmp, *chunk])
            current = "?"
            for line in text.splitlines():
                m = re.match(r"^(?:public |final |abstract |private |protected |static )*(?:class|interface|enum) ([\w.$]+)", line)
                if m:
                    current = m.group(1)
                for kind, owner, name, desc in REF_RE.findall(line):
                    if owner.startswith(PLATFORM_PREFIXES) and owner not in app_classes:
                        refs.setdefault((kind, owner, name, desc), set()).add(current)
                for owner in CLASS_REF_RE.findall(line):
                    owner = owner.strip("[").lstrip("L").rstrip(";")
                    if owner.startswith(PLATFORM_PREFIXES) and owner not in app_classes:
                        refs.setdefault(("Class", owner, "", ""), set()).add(current)
    return refs


class Platform:
    def __init__(self, jar: Path):
        self.jar = jar
        with zipfile.ZipFile(jar) as z:
            self.classes = {n[:-6] for n in z.namelist() if n.endswith(".class")}
        self.members: dict[str, set[str]] = {}
        self.supers: dict[str, list[str]] = {}

    def load(self, owners: set[str]) -> None:
        todo = [o for o in owners if o in self.classes and o not in self.members]
        while todo:
            batch, todo = todo[:200], todo[200:]
            text = javap(["-p", "-s", "-cp", str(self.jar), *[b.replace("/", ".") for b in batch]])
            current = None
            pending_name = None
            for line in text.splitlines():
                m = re.match(r"^\S.*?(?:class|interface) ([\w.$]+)(?:<.*?>)?(?: extends ([\w.$<>?, ]+?))?(?: implements ([\w.$<>?, ]+))? ?\{", line)
                if m:
                    current = m.group(1).replace(".", "/")
                    # İç sınıf adları javap çıktısında '$' ile gelir.
                    self.members.setdefault(current, set())
                    sup = []
                    for grp in (m.group(2), m.group(3)):
                        if grp:
                            sup += [re.sub(r"<.*", "", s.strip()).replace(".", "/") for s in grp.split(",")]
                    self.supers[current] = [s for s in sup if s]
                    continue
                if current is None:
                    continue
                s = line.strip()
                if s.startswith("descriptor:") and pending_name is not None:
                    self.members[current].add(pending_name + ":" + s.split(":", 1)[1].strip())
                    pending_name = None
                    continue
                mm = re.search(r"([\w$<>]+)\(", s)
                if s.endswith(";") and mm:
                    name = mm.group(1)
                    pending_name = "<init>" if name == current.split("/")[-1].split("$")[-1] else name
                elif s.endswith(";"):
                    pending_name = s.rstrip(";").split()[-1]
            new = {x for sups in self.supers.values() for x in sups} - set(self.members)
            todo += [o for o in new if o in self.classes and o not in todo]

    def has_member(self, owner: str, name: str, desc: str, seen=None) -> bool:
        seen = seen or set()
        if owner in seen:
            return False
        seen.add(owner)
        if f"{name}:{desc}" in self.members.get(owner, set()):
            return True
        if name == "<init>":
            return False
        for s in self.supers.get(owner, []) or (["java/lang/Object"] if owner != "java/lang/Object" else []):
            if s not in self.members and s in self.classes:
                self.load({s})
            if self.has_member(s, name, desc, seen):
                return True
        return False


def main() -> int:
    app_jar, plat_jar = Path(sys.argv[1]), Path(sys.argv[2])
    allow: dict[str, str] = {}
    if len(sys.argv) > 3:
        for line in Path(sys.argv[3]).read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if line and not line.startswith("#"):
                key, _, why = line.partition("  # ")
                allow[key.strip()] = why.strip()
    refs = app_references(app_jar)
    plat = Platform(plat_jar)
    plat.load({o for (_, o, _, _) in refs})
    problems, allowed = [], []
    for (kind, owner, name, desc), users in sorted(refs.items()):
        ok = owner in plat.classes and (kind == "Class" or plat.has_member(owner, name, desc))
        if ok:
            continue
        key = f"{owner}.{name}" if name else owner
        if key in allow or owner in allow:
            allowed.append(f"{key}  ← {allow.get(key) or allow.get(owner)}")
        else:
            problems.append(f"{kind} {owner}.{name}{desc}  (kullanan: {', '.join(sorted(users))[:120]})")
    print(f"Platform başvurusu: {len(refs)} · minSdk jar: {plat_jar.name}")
    for a in allowed:
        print("  izinli (SDK_INT korumalı):", a)
    for p in problems:
        print("  HATA (minSdk'da yok):", p)
    print("SONUÇ:", "TAMAM" if not problems else f"{len(problems)} sorun")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())

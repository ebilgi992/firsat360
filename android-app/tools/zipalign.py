#!/usr/bin/env python3
"""Basit zipalign: sıkıştırılmamış (STORED) girdilerin verisini 4 bayt sınırına hizalar.

Android 11+ (targetSdk 30+) resources.arsc'nin sıkıştırılmamış ve 4 bayta hizalı olmasını şart koşar.
Sistemde Android'in ``zipalign`` aracı varsa o kullanılır; yoksa bu Python uygulaması devreye girer.
Kullanım: zipalign.py <girdi.apk> <çıktı.apk>   |   zipalign.py --check <apk>
"""
from __future__ import annotations

import shutil
import struct
import subprocess
import sys
import zipfile

ALIGN = 4


def align(src: str, dst: str) -> None:
    with zipfile.ZipFile(src) as zin, open(dst, "wb") as fh:
        with zipfile.ZipFile(fh, "w") as zout:
            for info in zin.infolist():
                data = zin.read(info)
                zi = zipfile.ZipInfo(info.filename, date_time=info.date_time)
                zi.compress_type = info.compress_type
                zi.external_attr = info.external_attr
                zi.create_system = info.create_system
                if zi.compress_type == zipfile.ZIP_STORED:
                    header = 30 + len(info.filename.encode("utf-8"))
                    pad = (-(fh.tell() + header)) % (4096 if info.filename.endswith(".so") else ALIGN)
                    zi.extra = b"\0" * pad
                zout.writestr(zi, data)


def check(path: str) -> list[str]:
    """Hizalanmamış STORED girdileri döner (boş liste = tamam)."""
    bad = []
    with zipfile.ZipFile(path) as z, open(path, "rb") as fh:
        for info in z.infolist():
            if info.compress_type != zipfile.ZIP_STORED or info.file_size == 0:
                continue
            fh.seek(info.header_offset)
            sig, = struct.unpack("<I", fh.read(4))
            assert sig == 0x04034B50, "yerel başlık bulunamadı"
            fh.seek(info.header_offset + 26)
            name_len, extra_len = struct.unpack("<HH", fh.read(4))
            data_off = info.header_offset + 30 + name_len + extra_len
            if data_off % ALIGN:
                bad.append(f"{info.filename} @ {data_off}")
    return bad


def main() -> int:
    if sys.argv[1] == "--check":
        bad = check(sys.argv[2])
        with zipfile.ZipFile(sys.argv[2]) as z:
            arsc = z.getinfo("resources.arsc")
            stored = arsc.compress_type == zipfile.ZIP_STORED
        print(f"Hizalama: {'TAMAM' if not bad else 'HATA ' + ', '.join(bad)} · resources.arsc sıkıştırılmamış: {stored}")
        return 0 if not bad and stored else 1
    src, dst = sys.argv[1], sys.argv[2]
    tool = shutil.which("zipalign")
    if tool:
        subprocess.run([tool, "-f", "-p", str(ALIGN), src, dst], check=True)
        print(f"zipalign aracı kullanıldı: {tool}")
    else:
        align(src, dst)
        print("Python zipalign kullanıldı")
    return main_check(dst)


def main_check(path: str) -> int:
    sys.argv = [sys.argv[0], "--check", path]
    return main()


if __name__ == "__main__":
    sys.exit(main())

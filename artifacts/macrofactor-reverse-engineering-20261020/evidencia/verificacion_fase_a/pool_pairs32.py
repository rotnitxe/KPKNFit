"""Reconstruye dobles de 64 bits a partir de pares de entradas de 32 bits del pool (arm32).

Uso: python -I pool_pairs32.py <pool.json> idx1 idx2 ...
Para cada idx imprime la entrada tal cual y el doble formado por (idx = palabra baja, idx+1 = palabra alta).
Solo lectura.
"""
import json
import struct
import sys


def main():
    path = sys.argv[1]
    idxs = [int(a) for a in sys.argv[2:]]
    with open(path, "r", encoding="utf-8") as fh:
        pools = json.load(fh)
    pool = max(pools, key=lambda p: p.get("length", 0))
    entries = {e[0]: e for e in pool["entries"]}
    for i in idxs:
        lo = entries.get(i)
        hi = entries.get(i + 1)
        desc = []
        for e in (lo, hi):
            if e is None:
                desc.append("<nada>")
            else:
                s = json.dumps(e[2], ensure_ascii=True)
                desc.append("%s:%s" % (e[1], s[:80]))
        dbl = None
        if lo is not None and hi is not None and isinstance(lo[2], int) and isinstance(hi[2], int):
            u = ((hi[2] & 0xFFFFFFFF) << 32) | (lo[2] & 0xFFFFFFFF)
            dbl = struct.unpack("<d", struct.pack("<Q", u))[0]
        print(i, " | ".join(desc), "=> doble:", dbl)


if __name__ == "__main__":
    main()

"""Lee entradas concretas del object pool ya volcado (pool.json) y las imprime.

Uso: python -I pool_peek.py <pool.json> idx1 idx2 ... [--around N]
Solo lectura. No ejecuta nada del APK.
"""
import json
import struct
import sys


def main():
    path = sys.argv[1]
    args = sys.argv[2:]
    around = 0
    if "--around" in args:
        k = args.index("--around")
        around = int(args[k + 1])
        args = args[:k] + args[k + 2:]
    idxs = [int(a) for a in args]
    with open(path, "r", encoding="utf-8") as fh:
        pools = json.load(fh)
    pool = max(pools, key=lambda p: p.get("length", 0))
    entries = {e[0]: e for e in pool["entries"]}
    for i in idxs:
        for j in range(i - around, i + around + 1):
            e = entries.get(j)
            if e is None:
                print(j, "<sin entrada>")
                continue
            val = e[2]
            s = json.dumps(val, ensure_ascii=True)
            if len(s) > 160:
                s = s[:160] + "..."
            extra = ""
            if isinstance(val, int) and e[1] != "obj":
                u = val & 0xFFFFFFFFFFFFFFFF
                as_double = struct.unpack("<d", struct.pack("<Q", u))[0]
                extra = " (raw=0x%016x, como double=%r)" % (u, as_double)
            print(j, e[1], s, extra)
        print("--")


if __name__ == "__main__":
    main()

"""android/app/src/main/assets/mosques.bin: the Gulf's mosques, for «مسجد أمامك» offline.

From OpenStreetMap (© OpenStreetMap contributors, ODbL; a derived database under the same licence) through Geofabrik's
GCC extract: places of worship whose religion is muslim or not given (in the Gulf nearly all are mosques), as a point
(a building's centre is the mean of its corners), exported by limits.yml with osmium as GeoJSON lines (argv[1]).

Format (big-endian, read by Mosques.java): "UCM1", int built (yyyymmdd), int n, then n x (int lat·1e5, int lon·1e5,
u8 length, the Arabic name in UTF-8 or nothing), sorted by latitude.
"""
import json, re, struct, sys, time

def arabic(t):
    for k in ("name:ar", "name"):
        n = (t.get(k) or "").strip()
        if n and re.search("[ء-ي]", n): return n
    return ""

out = []
for line in open(sys.argv[1], encoding="utf-8"):
    f = json.loads(line.strip().lstrip("\x1e"))
    t = f["properties"]
    if t.get("amenity") != "place_of_worship" or t.get("religion", "muslim") != "muslim": continue
    g = f["geometry"]
    pts = [g["coordinates"]] if g["type"] == "Point" else \
          g["coordinates"] if g["type"] == "LineString" else \
          g["coordinates"][0] if g["type"] == "Polygon" else g["coordinates"][0][0]
    lon = sum(p[0] for p in pts) / len(pts); lat = sum(p[1] for p in pts) / len(pts)
    name = arabic(t).encode("utf-8")
    while len(name) > 120: name = name[:-1]
    name = name.decode("utf-8", "ignore").encode("utf-8")
    out.append((round(lat * 1e5), round(lon * 1e5), name))
out.sort()
b = bytearray(b"UCM1" + struct.pack(">ii", int(time.strftime("%Y%m%d")), len(out)))
for la, lo, n in out: b += struct.pack(">iiB", la, lo, len(n)) + n
open("android/app/src/main/assets/mosques.bin", "wb").write(b)
named = sum(1 for o in out if o[2])
road = sum(1 for la, lo, _ in out if 24.75e5 < la < 26.35e5 and 47.0e5 < lo < 49.9e5)
print("mosques", len(out), "named", named, "bytes", len(b), "in the Riyadh-Dammam box", road)

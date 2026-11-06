"""android/app/src/main/assets/limits.bin: the speed limits of the Gulf's main roads, for the app to read offline.

From OpenStreetMap (© OpenStreetMap contributors, ODbL; this file is a derived database under the same licence) through
Geofabrik's GCC extract, filtered by limits.yml with osmium to the main roads (motorway, trunk, primary, secondary,
tertiary and their links) that carry a numeric «maxspeed», exported as GeoJSON lines (argv[1]).

Format (big-endian, read by LimitMap.java):
  "UCL1", int built (yyyymmdd), int tiles,
  tiles x (int key, int offset, int count)      key = ty * 7200 + tx, tiles of 0.05° from (-90, -180), sorted
  segments x (u8 limit km/h, u8 rank, i16 x1, y1, x2, y2)   1e-5° from the tile's corner
A segment is cut to 0.01° (about a kilometre) at most and is filed in every tile that it, widened by 0.0005°
(about 50 m), touches, so one tile answers for any point inside it.
"""
import json, re, struct, sys, time, collections

T = 0.05
RANK = {"motorway": 0, "trunk": 1, "primary": 2, "secondary": 3, "tertiary": 4,
        "motorway_link": 5, "trunk_link": 6, "primary_link": 7, "secondary_link": 8, "tertiary_link": 9}

def kmh(v):
    m = re.fullmatch(r"\s*(\d{2,3})\s*(mph|km/h|kmh|kph)?\s*", v or "")
    if not m: return 0
    n = int(m.group(1))
    return round(n * 1.609) if m.group(2) == "mph" else n

tiles = collections.defaultdict(list)
ways = segs = 0
for line in open(sys.argv[1], encoding="utf-8"):
    f = json.loads(line.strip().lstrip("\x1e"))
    t = f["properties"]
    if f["geometry"]["type"] != "LineString" or t.get("highway") not in RANK: continue
    lim = kmh(t.get("maxspeed"))
    if not 5 <= lim <= 200: continue
    ways += 1
    g = f["geometry"]["coordinates"]
    for (x1, y1), (x2, y2) in zip(g, g[1:]):
        n = max(1, int(max(abs(x2 - x1), abs(y2 - y1)) / 0.01) + 1)
        for k in range(n):
            ax, ay = x1 + (x2 - x1) * k / n, y1 + (y2 - y1) * k / n
            bx, by = x1 + (x2 - x1) * (k + 1) / n, y1 + (y2 - y1) * (k + 1) / n
            segs += 1
            for tx in range(int((min(ax, bx) - 0.0005 + 180) // T), int((max(ax, bx) + 0.0005 + 180) // T) + 1):
                for ty in range(int((min(ay, by) - 0.0005 + 90) // T), int((max(ay, by) + 0.0005 + 90) // T) + 1):
                    ox, oy = tx * 5000 - 18_000_000, ty * 5000 - 9_000_000
                    tiles[ty * 7200 + tx].append(struct.pack(">BBhhhh", lim, RANK[t["highway"]],
                        round(ax * 1e5) - ox, round(ay * 1e5) - oy, round(bx * 1e5) - ox, round(by * 1e5) - oy))

keys = sorted(tiles)
head = b"UCL1" + struct.pack(">ii", int(time.strftime("%Y%m%d")), len(keys))
index, data, off = bytearray(), bytearray(), 0
for k in keys:
    index += struct.pack(">iii", k, off, len(tiles[k]))
    for s in tiles[k]: data += s
    off += 12 * len(tiles[k])
open("android/app/src/main/assets/limits.bin", "wb").write(head + index + data)
print("ways", ways, "segments", segs, "tiles", len(keys), "bytes", len(head) + len(index) + len(data))

"""assets/cities.json for the app's «مرحبًا بك في …», offline: the inhabited places of the Gulf from GeoNames
(CC BY 4.0, https://www.geonames.org), with their Arabic name. Run by .github/workflows/cities.yml.

Each entry: [Arabic name, lat, lon, population]. Kept: populated places (PPL, PPLA…, PPLC, PPLG, PPLF, PPLS; not the
sections of a city, PPLX) of 3,000 people or more, or a seat of government. The Arabic name: GeoNames' preferred «ar»
name, else its first «ar» name; a place with none is left out (no name is better than a wrong one)."""
import csv, io, json, re, sys, urllib.request, zipfile

COUNTRIES = ["SA", "KW", "BH", "QA", "AE", "OM"]
KEEP = {"PPL", "PPLA", "PPLA2", "PPLA3", "PPLA4", "PPLC", "PPLG", "PPLF", "PPLS"}
csv.field_size_limit(10**9)

def clean(n):
    """No diacritics (بُرَيدَة → بريدة), the plain alif, no «إمارة» before an emirate's name; None if not Arabic letters."""
    n = re.sub("[\u064B-\u0652\u0670]", "", n).replace("ٱ", "ا").strip()
    n = re.sub("^إمارة ", "", n)
    return n if re.fullmatch("[\u0621-\u064A ]+", n) else None

def rows(url, name):
    with urllib.request.urlopen(url, timeout=120) as r:
        z = zipfile.ZipFile(io.BytesIO(r.read()))
    with z.open(name) as f:
        yield from csv.reader(io.TextIOWrapper(f, "utf-8"), delimiter="\t", quoting=csv.QUOTE_NONE)

out = []
for cc in COUNTRIES:
    places = {}
    for r in rows(f"https://download.geonames.org/export/dump/{cc}.zip", f"{cc}.txt"):
        if r[6] != "P" or r[7] not in KEEP: continue
        pop = int(r[14] or 0)
        if pop < 3000 and not r[7].startswith("PPLA") and r[7] != "PPLC": continue
        places[r[0]] = [float(r[4]), float(r[5]), pop]
    names = {}
    for r in rows(f"https://download.geonames.org/export/dump/alternatenames/{cc}.zip", f"{cc}.txt"):
        gid, lang, name, preferred = r[1], r[2], r[3], r[4] == "1"
        if gid not in places or lang != "ar" or r[7] == "1": continue   # r[7]: historic
        name = clean(name)
        if not name: continue
        if gid not in names or (preferred and not names[gid][1]): names[gid] = (name, preferred)
    n = 0
    for gid, (la, lo, pop) in places.items():
        if gid in names:
            out.append([names[gid][0], round(la, 4), round(lo, 4), pop]); n += 1
    print(cc, "places", len(places), "with an Arabic name", n, file=sys.stderr)

out.sort(key=lambda e: -e[3])
json.dump({"source": "GeoNames (geonames.org), CC BY 4.0", "places": out}, open("android/app/src/main/assets/cities.json", "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
for e in out[:25]: print(e, file=sys.stderr)

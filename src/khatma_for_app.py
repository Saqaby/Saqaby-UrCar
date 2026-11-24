"""android/app/src/main/assets/khatma.json, from the sources themselves (run by .github/workflows/khatma.yml):

- the reciters: the names chosen for UrCar (src/reciters_wanted.json: [our short id, the name]) looked up in
  mp3quran.net's own API (/api/v3/reciters), each with its Hafs-from-Asim recitation, all 114 murattal if there is
  one, else all 114 in another style, else the fullest, then with «surahs» listing what it has (server + 001.mp3 …);
- the surahs: their Arabic names from mp3quran.net's /api/v3/suwar, their ayah counts from fawazahmed0/quran-api's
  info.json (Unlicense; Tanzil's counts, sum 6236).
A wanted name not found, or found without a complete Hafs murattal recitation, is left out and said.
"""
import json, re, urllib.request

def get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "UrCar-build"}), timeout=60) as r:
        return json.load(r)

norm = lambda s: re.sub("[ً-ْـ]", "", s).replace("أ", "ا").replace("إ", "ا").replace("آ", "ا").replace("ة", "ه").replace("ى", "ي").replace("ی", "ي").replace("ک", "ك").replace(" ", "")
api = get("https://www.mp3quran.net/api/v3/reciters?language=ar")["reciters"]
by = {}
for r in api: by.setdefault(norm(r["name"]), []).append(r)

out, missing = [], []
for rid, name in json.load(open("src/reciters_wanted.json", encoding="utf-8")):
    # Hafs from Asim: all 114 murattal first, then all 114 in another style («تلاوة مميزة»), then the fullest
    cands = [(r, m) for r in by.get(norm(name), []) for m in r["moshaf"] if "حفص" in m["name"]]
    if not cands: missing.append(name); continue
    r, m = max(cands, key=lambda c: (c[1].get("surah_total") == 114, "مرتل" in c[1]["name"], c[1].get("surah_total", 0)))
    e = {"id": rid, "name": name, "server": m["server"], "mp3quran": r["id"]}
    if m.get("surah_total") != 114:   # only these surahs: in the library, not for the khatma
        e["surahs"] = sorted(int(x) for x in str(m.get("surah_list", "")).split(",") if x.strip())
    if m.get("surah_total") != 114 or "مرتل" not in m["name"]: print("note:", name, "|", m["name"], "|", m.get("surah_total"))
    out.append(e)

suwar = sorted(get("https://www.mp3quran.net/api/v3/suwar?language=ar")["suwar"], key=lambda s: s["id"])
info = get("https://raw.githubusercontent.com/fawazahmed0/quran-api/1/info.json")["chapters"]
ayahs = {c["chapter"]: len(c["verses"]) if isinstance(c["verses"], list) else c["verses"] for c in info}
surahs = [{"n": s["id"], "name": s["name"].strip(), "ayahs": ayahs[s["id"]]} for s in suwar]
assert len(surahs) == 114 and sum(s["ayahs"] for s in surahs) == 6236

json.dump({"note": "made by src/khatma_for_app.py: reciters and their servers from mp3quran.net's API, surah names from "
           "mp3quran.net, ayah counts from fawazahmed0/quran-api (sum 6236)", "reciters": out, "surahs": surahs},
          open("android/app/src/main/assets/khatma.json", "w", encoding="utf-8"), ensure_ascii=False, indent=0)
print("reciters", len(out), "missing", missing)
print("surahs", surahs[0], surahs[-1])

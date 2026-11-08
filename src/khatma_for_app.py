"""android/app/src/main/assets/khatma.json, from the sources themselves (run by .github/workflows/khatma.yml):

- the reciters: the names chosen for UrCar (src/reciters_wanted.json: [our short id, the name]) looked up in
  mp3quran.net's own API (/api/v3/reciters), each with its Hafs-from-Asim murattal recitation of all 114 surahs
  (server + 001.mp3 … 114.mp3);
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
    best = None
    for r in by.get(norm(name), []):
        for m in r["moshaf"]:
            if m.get("surah_total") == 114 and "حفص" in m["name"] and "مرتل" in m["name"]:
                best = best or (r, m)
    if not best: missing.append(name); continue
    r, m = best
    out.append({"id": rid, "name": name, "server": m["server"], "mp3quran": r["id"]})

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

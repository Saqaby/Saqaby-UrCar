"""android/app/src/main/assets/quotes.json: the verses, hadith and sayings of the salaf on the quote card, chosen for UrCar.

- verses: src/quotes/verses.txt (sura:ayah or sura:first-last), the text from Tanzil's simple text as served by
  fawazahmed0/quran-api (ara-quransimple), without the basmala it puts before a sura's first verse or its section marks;
  the surah names from assets/khatma.json.
- hadith: src/quotes/hadith.txt (bukhari:n / muslim:n), from fawazahmed0/hadith-api (public domain, Unlicense): the
  Prophet's own words as the text has them after «قال رسول الله ﷺ» and the like, without the chain of narrators.
- salaf: src/quotes/salaf.json, each with the book, volume and page it was taken from (found on turath.io).
Run: python3 src/quotes_for_app.py (needs raw.githubusercontent.com).
"""
import json, re, urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RAW = "https://raw.githubusercontent.com/fawazahmed0/"
get = lambda u: json.load(urllib.request.urlopen(urllib.request.Request(u, headers={"User-Agent": "UrCar-build"}), timeout=120))
lines = lambda p: [l.strip() for l in (ROOT / p).read_text("utf-8").splitlines() if l.strip() and not l.startswith("#")]
DIAC = re.compile("[ً-ْٰـ]")
plain = lambda s: DIAC.sub("", s)

names = {s["n"]: s["name"] for s in json.loads((ROOT / "android/app/src/main/assets/khatma.json").read_text("utf-8"))["surahs"]}
Q = {(v["chapter"], v["verse"]): v["text"] for v in get(RAW + "quran-api/1/editions/ara-quransimple.json")["quran"]}
verses = []
for r in lines("src/quotes/verses.txt"):
    c, v = r.split(":"); c = int(c)
    a, b = (int(x) for x in (v.split("-") + [v])[:2])
    parts = []
    for i in range(a, b + 1):
        t = Q[(c, i)].replace("۞", "").strip()
        w = t.split()
        if i == 1 and c != 1 and plain(" ".join(w[:4])).replace("ٰ", "") == "بسم الله الرحمن الرحيم": t = " ".join(w[4:])
        parts.append(t)
    verses.append({"t": " ".join(parts), "s": "سورة " + names[c] + "، " + ("الآية " + str(a) if a == b else "الآيتان " + str(a) + "–" + str(b) if b == a + 1 else "الآيات " + str(a) + "–" + str(b))})

OPEN = re.compile(r"(?:قال رسول الله|أن رسول الله|أن النبي|عن النبي|عن رسول الله|سمعت رسول الله|سمعت النبي|قال النبي) (?:صلى الله عليه وسلم|ﷺ)\s*(?:قال|يقول)\s*[:،,]?\s*")
books = {}
hadith = []
for r in lines("src/quotes/hadith.txt"):
    book, n = r.split(":")
    if book not in books: books[book] = {h["hadithnumber"]: h["text"] for h in get(RAW + "hadith-api/1/editions/ara-" + book + ".json")["hadiths"]}
    t = books[book][float(n)] if float(n) in books[book] else books[book][int(n)]
    t = t.replace("‏", "").strip()
    m = OPEN.search(plain(t))
    idx, k = 0, 0
    while k < m.end() and idx < len(t):
        if not DIAC.match(t[idx]): k += 1
        idx += 1
    while idx < len(t) and DIAC.match(t[idx]): idx += 1
    s = t[idx:].strip().strip('"«»“” ').strip()
    s = re.sub(r'[\s".»”]*\.?\s*$', "", s)
    s = re.sub(r"\s+([،.])", r"\1", s).replace("صلى الله عليه وسلم", "ﷺ")
    hadith.append({"t": "قال رسول الله ﷺ: «" + s + "»", "s": ("صحيح البخاري" if book == "bukhari" else "صحيح مسلم") + "، " + n})

salaf = json.loads((ROOT / "src/quotes/salaf.json").read_text("utf-8"))
out = {"note": "made by src/quotes_for_app.py from src/quotes/ (verses: Tanzil via fawazahmed0/quran-api; hadith: Sahih "
               "al-Bukhari and Sahih Muslim via fawazahmed0/hadith-api; salaf: the books cited)",
       "verses": verses, "hadith": hadith, "salaf": salaf}
(ROOT / "android/app/src/main/assets/quotes.json").write_text(json.dumps(out, ensure_ascii=False, indent=0), "utf-8")
print("verses", len(verses), "hadith", len(hadith), "salaf", len(salaf))

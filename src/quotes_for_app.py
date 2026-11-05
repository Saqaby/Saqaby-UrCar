#!/usr/bin/env python3
"""assets/shared -> android/app/src/main/assets/quotes.json: the short verses, hadith and salaf sayings the app's
quote card turns through every hour. Short, because it is read in a car: verses <=110, hadith <=150, salaf <=120
characters. Verses in the plain text (the app has no Uthmanic Hafs font); hadith through UrTV's hadith_for_screen."""
import json
import re
import sys
import unicodedata
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import content  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]


def main():
    verses = []
    for v in json.loads((ROOT / "assets/shared/verses.json").read_text("utf-8")):
        t = " ".join(v.get("text", "").split())
        if t and len(t) <= 110:
            name = re.sub("[ً-ٰٟۖ-ۭ]", "", unicodedata.normalize("NFC", v["surah"])).replace("ٱ", "ا")
            verses.append({"t": t, "s": f"{name}، الآية {v['ayah']}"})
    hadith = []
    for h in content.hadith_for_screen():
        t = " ".join(f"«{x}»" if k == "q" else x for k, x in h["parts"])
        if len(t) <= 150:
            hadith.append({"t": t, "s": h["source"].replace(" · ", "، ")})
    salaf = [{"t": f"قال {s['who']}: «{s['text']}»", "s": s["src"]}
             for s in json.loads((ROOT / "assets/shared/salaf.json").read_text("utf-8")) if len(s["text"]) + len(s["who"]) <= 120]
    out = ROOT / "android/app/src/main/assets/quotes.json"
    out.write_text(json.dumps({"note": "made by src/quotes_for_app.py", "verses": verses, "hadith": hadith, "salaf": salaf},
                              ensure_ascii=False, indent=0), "utf-8")
    print(f"verses {len(verses)} hadith {len(hadith)} salaf {len(salaf)} -> {out.relative_to(ROOT)}")


if __name__ == "__main__":
    main()

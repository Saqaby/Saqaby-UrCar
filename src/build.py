#!/usr/bin/env python3
"""src/template.html -> dist/urcar.html (stand-alone, opens offline) and dist/artifact.html (the same without the
document skeleton, for the claude.ai preview). Content and fonts come from assets/, copied from Saqaby/UrTV."""
import json
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import content  # noqa: E402  UrTV's verses_for_screen / hadith_for_screen, unchanged

ROOT = Path(__file__).resolve().parents[1]
FONTS = {
    "__F_LIGHT__": "Thmanyah-Light.woff2", "__F_REGULAR__": "Thmanyah-Regular.woff2",
    "__F_MEDIUM__": "Thmanyah-Medium.woff2", "__F_DISPLAY__": "ThmanyahDisplay-Bold.woff2",
    "__F_HAFS__": "UthmanicHafs-v30.woff2", "__F_MARKS__": "NotoNaskhArabic-Marks.woff2",
}
# a glance while driving: two lines at most on the bottom strip
VERSE_MAX, HADITH_MAX, SALAF_MAX = 110, 140, 120
ADHAN = ("https://archive.org/download/azaan-of-world-mosques_202608/"
         "%D8%B9%D8%A8%D8%AF%D8%A7%D9%84%D9%85%D9%84%D9%83%20%D8%A7%D9%84%D9%85%D9%84%D8%A7_%D9%85%D9%83%D8%A9"
         "%20%28www.alqurantranslation.com%29.mp3")   # عبدالملك الملا, Public Domain Mark 1.0 (UrTV Adhan.java)


def main():
    verses = [v for v in content.verses_for_screen() if len(v["text"]) <= VERSE_MAX]
    hadith = [h for h in content.hadith_for_screen() if sum(len(t) for _, t in h["parts"]) <= HADITH_MAX]
    salaf = [s for s in json.loads((ROOT / "assets/shared/salaf.json").read_text("utf-8"))
             if len(s["text"]) + len(s["who"]) <= SALAF_MAX]
    radio = json.loads((ROOT / "assets/shared/stations.json").read_text("utf-8"))["stations"][0]["url"]
    fm = json.loads((ROOT / "assets/shared/fm-quran.json").read_text("utf-8"))
    # device/profile.json from tools/probe.sh: the real screen's size and apps; none yet = the preview's defaults
    prof = ROOT / "device/profile.json"
    device = json.loads(prof.read_text("utf-8")) if prof.is_file() else None
    page = (ROOT / "src/template.html").read_text("utf-8")
    for token, value in {"__VERSES__": verses, "__HADITH__": hadith, "__SALAF__": salaf,
                         "__RADIO__": radio, "__FM__": fm, "__DEVICE__": device, "__ADHAN__": ADHAN}.items():
        page = page.replace(token, content.js_json(value))
    for token, font in FONTS.items():
        page = page.replace(token, content.data_uri(ROOT / "assets/fonts" / font, "font/woff2"))
    page = page.replace("__BUILD__", time.strftime("%Y%m%d%H%M%S", time.gmtime()))
    out = ROOT / "dist"
    out.mkdir(exist_ok=True)
    (out / "artifact.html").write_text(page, "utf-8")
    (out / "urcar.html").write_text('<!doctype html>\n<html lang="ar" dir="rtl"><head><meta charset="utf-8">'
                                    '<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">'
                                    '</head><body>\n' + page + '\n</body></html>\n', "utf-8")
    print(f"verses {len(verses)} hadith {len(hadith)} salaf {len(salaf)}; "
          f"urcar.html {(out / 'urcar.html').stat().st_size / 1e6:.2f} MB")


if __name__ == "__main__":
    main()

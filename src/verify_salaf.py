"""Checks each saying in src/quotes/salaf_candidates.json against the books on turath.io (run by verify-salaf.yml):
searches its words, prefers a page of the book it is cited from, and writes src/quotes/salaf_report.json with, for each,
the best page found (book, volume, page, link), how much of the saying's words that page has in order (0-1), whether it
is the cited book, and the page's own wording around it. Nothing is added to the app from here: the report is read and
the sayings that hold are copied into src/quotes/salaf.json by hand, in the book's wording.
"""
import difflib, json, re, time, urllib.parse, urllib.request

DIAC = re.compile("[ً-ْٰـ]")
def norm(s):
    s = DIAC.sub("", re.sub("<[^>]+>", "", s))
    s = s.replace("أ", "ا").replace("إ", "ا").replace("آ", "ا").replace("ة", "ه").replace("ى", "ي")
    return re.sub(r"[^ء-ي ]", " ", s)
words = lambda s: norm(s).split()

def search(q, page=1):
    u = "https://api.turath.io/search?ver=3&page=%d&q=%s" % (page, urllib.parse.quote(q))
    for _ in range(3):
        try:
            with urllib.request.urlopen(urllib.request.Request(u, headers={"User-Agent": "Mozilla/5.0 UrCar-check"}), timeout=40) as r:
                return json.load(r).get("data", [])
        except Exception:
            time.sleep(3)
    return []

def coverage(qw, tw):
    """the share of the saying's words found in the page's words, in order, and where (start, end) in tw"""
    m = difflib.SequenceMatcher(None, qw, tw, autojunk=False)
    blocks = [b for b in m.get_matching_blocks() if b.size]
    if not blocks: return 0, (0, 0)
    got = sum(b.size for b in blocks)
    # the densest stretch: from the first to the last matched word, if not spread too wide
    s, e = blocks[0].b, blocks[-1].b + blocks[-1].size
    return got / len(qw), (s, e)

KEYS = ["صحيح مسلم", "صحيح البخاري", "حلية الأولياء", "جامع بيان العلم", "سنن الدارمي", "مسند الدارمي", "جامع العلوم والحكم",
        "الزهد", "محاسبة النفس", "مصنف ابن أبي شيبة", "المصنف", "شعب الإيمان", "التبيان", "الصمت", "سنن الترمذي", "جامع الترمذي",
        "الإبانة", "شرح أصول اعتقاد", "السيرة النبوية", "المستدرك", "مدارج السالكين", "سير أعلام النبلاء", "أخلاق حملة القرآن",
        "آداب الشافعي", "مكارم الأخلاق"]

out = []
for it in json.load(open("src/quotes/salaf_candidates.json", encoding="utf-8")):
    qw = words(it["q"])
    cited = [k for k in KEYS if k in it["cited"]]
    q = " ".join(qw[:12])
    res = search(q) + search(q, 2)
    best = None
    for r in res:
        meta = json.loads(r["meta"]) if isinstance(r["meta"], str) else r["meta"]
        tw_raw = re.sub("<[^>]+>", "", r.get("text", ""))
        cov, (s, e) = coverage(qw, words(tw_raw))
        in_cited = any(k in meta.get("book_name", "") for k in cited)
        score = cov + (0.3 if in_cited else 0)
        if not best or score > best["score"]:
            # the page's own wording around the match (by word index on the raw text)
            raw = tw_raw.split()
            ctx = " ".join(raw[max(0, s - 6): e + 4]) if raw else ""
            best = {"score": score, "coverage": round(cov, 2), "in_cited_book": in_cited, "book": meta.get("book_name"),
                    "author": meta.get("author_name"), "vol": meta.get("vol"), "page": meta.get("page"),
                    "link": "https://app.turath.io/book/%s?page=%s" % (r["book_id"], meta.get("page_id")), "wording": ctx}
    it["found"] = best
    out.append(it)
    print(it["n"], it["who"], "|", best and best["coverage"], best and best["in_cited_book"], best and best["book"], best and best["vol"], best and best["page"])
    time.sleep(1)
json.dump(out, open("src/quotes/salaf_report.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)

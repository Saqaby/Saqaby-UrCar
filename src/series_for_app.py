"""assets/series.json: the owner's series (src/series.json), each part's episodes in order — their YouTube video ids
and titles, read from the playlists' public pages — so that the app opens the very episode to go on with (not the
playlist from its start). The sound stays on YouTube; nothing of it is copied."""
import json, re, sys, urllib.request

UA = 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124 Safari/537.36'

def page(url):
    req = urllib.request.Request(url, headers={'User-Agent': UA, 'Accept-Language': 'ar,en;q=0.8', 'Cookie': 'CONSENT=YES+1'})
    return urllib.request.urlopen(req, timeout=60).read().decode('utf-8', 'replace')

def initial_data(html):
    m = re.search(r'var ytInitialData = (\{.*?\});</script>', html, re.S) or re.search(r'ytInitialData"\]\s*=\s*(\{.*?\});', html, re.S)
    return json.loads(m.group(1)) if m else None

def walk(o, key):
    if isinstance(o, dict):
        for k, v in o.items():
            if k == key: yield v
            yield from walk(v, key)
    elif isinstance(o, list):
        for v in o: yield from walk(v, key)

def text(t):
    if not t: return ''
    if 'simpleText' in t: return t['simpleText']
    return ''.join(r.get('text', '') for r in t.get('runs', []))

def episodes(pl):
    d = initial_data(page('https://www.youtube.com/playlist?list=' + pl))
    if not d: raise SystemExit('no ytInitialData for ' + pl)
    out, seen = [], set()
    for v in walk(d, 'playlistVideoRenderer'):
        vid = v.get('videoId')
        if not vid or vid in seen: continue
        seen.add(vid)
        out.append({'id': vid, 'title': text(v.get('title')).strip()})
    for v in walk(d, 'lockupViewModel'):   # the newer layout
        vid = v.get('contentId')
        if not vid or vid in seen or v.get('contentType') not in (None, 'LOCKUP_CONTENT_TYPE_VIDEO'): continue
        seen.add(vid)
        t = next(walk(v, 'title'), {})
        out.append({'id': vid, 'title': (t.get('content') if isinstance(t, dict) else '') or ''})
    return out

src = json.load(open('src/series.json'))
for s in src['series']:
    for p in s['parts']:
        p['episodes'] = episodes(p['playlist'])
        print(s['name'], p['name'], len(p['episodes']), 'episodes;', p['episodes'][:1], p['episodes'][-1:], file=sys.stderr)
        if not p['episodes']: raise SystemExit('no episodes in ' + p['playlist'])
json.dump(src, open('android/app/src/main/assets/series.json', 'w'), ensure_ascii=False, separators=(',', ':'))

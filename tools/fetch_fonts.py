"""Download the game's fonts (SIL OFL) from Google Fonts into app/src/main/assets/fonts as TTF."""
import os, re, urllib.request
OUT = 'app/src/main/assets/fonts'
WANT = {('Lilita One', 400): 'LilitaOne-Regular.ttf',
        ('Grenze Gotisch', 700): 'GrenzeGotisch-Bold.ttf', ('Grenze Gotisch', 800): 'GrenzeGotisch-ExtraBold.ttf',
        ('Alegreya Sans', 400): 'AlegreyaSans-Regular.ttf', ('Alegreya Sans', 700): 'AlegreyaSans-Bold.ttf',
        ('Alegreya Sans', 800): 'AlegreyaSans-ExtraBold.ttf'}
os.makedirs(OUT, exist_ok=True)
if all(os.path.exists(os.path.join(OUT, n)) for n in WANT.values()):
    print('fonts already present'); raise SystemExit
url = 'https://fonts.googleapis.com/css2?family=Lilita+One&family=Grenze+Gotisch:wght@700;800&family=Alegreya+Sans:wght@400;700;800'
# an old user agent makes the API serve plain TTF files
css = urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 6.1) AppleWebKit/534.30'})).read().decode()
for block in re.findall(r'@font-face\s*{[^}]*}', css):
    fam = re.search(r"font-family:\s*'([^']+)'", block).group(1)
    w = int(re.search(r'font-weight:\s*(\d+)', block).group(1))
    src = re.search(r'url\((https://[^)]+)\)', block).group(1)
    name = WANT.get((fam, w))
    if name:
        data = urllib.request.urlopen(src).read()
        open(os.path.join(OUT, name), 'wb').write(data); print(name, len(data))
missing = [n for n in WANT.values() if not os.path.exists(os.path.join(OUT, n))]
if missing: raise SystemExit('missing fonts: %s' % missing)

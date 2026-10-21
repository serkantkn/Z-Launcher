#!/bin/zsh
# ui.sh SERIAL find TEXT  -> prints center "x y" of first node whose text/desc contains TEXT
# ui.sh SERIAL tap TEXT   -> taps it
# ui.sh SERIAL texts      -> lists visible texts with centers
E=$1; cmd=$2; shift 2
adb -s $E shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
adb -s $E exec-out cat /sdcard/ui.xml > /tmp/ui_$E.xml 2>/dev/null
/usr/local/bin/python3 -I - "$cmd" "$*" "/tmp/ui_$E.xml" "$E" <<'PY'
import sys,re,subprocess
cmd,arg,path,E=sys.argv[1:5]
x=open(path,encoding='utf-8',errors='ignore').read()
nodes=re.findall(r'<node [^>]*>',x)
def attr(n,a):
    m=re.search(a+r'="([^"]*)"',n); return m.group(1) if m else ''
out=[]
for n in nodes:
    t=attr(n,'text') or attr(n,'content-desc')
    b=re.findall(r'\d+',attr(n,'bounds'))
    if not t or len(b)<4: continue
    x1,y1,x2,y2=map(int,b); out.append((t,(x1+x2)//2,(y1+y2)//2))
if cmd=='texts':
    for t,cx,cy in out: print(f"{cx},{cy}\t{t[:60]}")
else:
    hit=[o for o in out if arg.lower() == o[0].lower()] or [o for o in out if arg.lower() in o[0].lower()]
    if not hit: print("NOTFOUND",arg); sys.exit(1)
    t,cx,cy=hit[0]
    if cmd=='tap': subprocess.run(['adb','-s',E,'shell','input','tap',str(cx),str(cy)])
    print(cx,cy,t[:40])
PY

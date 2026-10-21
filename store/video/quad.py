import sys, numpy as np
from PIL import Image
from scipy import ndimage
def quad(path, seed, dark, thr):
    im=np.asarray(Image.open(path).convert('L')).astype(int)
    mask = im<thr if dark else im>thr
    mask = ndimage.binary_opening(mask, iterations=2)
    lab,n=ndimage.label(mask)
    l=lab[seed[1],seed[0]]
    if l==0: print(path,"seed not in mask", im[seed[1],seed[0]]); return
    ys,xs=np.nonzero(lab==l)
    s=xs+ys; d=xs-ys
    pts=[(xs[s.argmin()],ys[s.argmin()]),(xs[d.argmax()],ys[d.argmax()]),(xs[s.argmax()],ys[s.argmax()]),(xs[d.argmin()],ys[d.argmin()])]
    print(path,"area",len(xs),"TL TR BR BL",[tuple(map(int,p)) for p in pts])
D=sys.argv[1]
quad(D+'/6980724.jpg',(2330,830),True,60)
quad(D+'/6373205.jpg',(1550,970),False,200)
quad(D+'/286565.jpg',(1200,1220),True,70)

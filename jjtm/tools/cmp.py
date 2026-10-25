import json,sys,os
def d(a,b,p=""):
    out=[]
    if type(a)!=type(b): return [(p,a,b)]
    if isinstance(a,dict):
        for k in sorted(set(a)|set(b)):
            if k not in a: out.append((p+"/"+k,"<none>",b[k]))
            elif k not in b: out.append((p+"/"+k,a[k],"<none>"))
            else: out+=d(a[k],b[k],p+"/"+k)
    elif isinstance(a,list):
        if a!=b:
            if len(a)==len(b) and all(not isinstance(x,(dict,list)) for x in a+b): return [(p,a,b)]
            if len(a)==len(b):
                for i,(x,y) in enumerate(zip(a,b)): out+=d(x,y,p+f"[{i}]")
            else: return [(p,a,b)]
    elif a!=b: out.append((p,a,b))
    return out
A,B=sys.argv[1],sys.argv[2]
for f in open("files.txt"):
    f=f.strip()
    if os.path.exists(A+"/"+f) and os.path.exists(B+"/"+f):
        r=d(json.load(open(A+"/"+f)),json.load(open(B+"/"+f)))
        if r:
            print("==",f)
            for p,a,b in r[:12]: print("  ",p,":",str(a)[:150],"->",str(b)[:150])
            if len(r)>12: print("   ... +",len(r)-12)

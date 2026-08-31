#!/usr/bin/env python3
"""Postbuild mutation adequacy for final APK/package verifiers."""
from __future__ import annotations
import argparse, json, os, shutil, subprocess, sys, tempfile, zipfile
from dataclasses import asdict, dataclass
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]; EVIDENCE=ROOT/'verification/evidence'
@dataclass
class Result: name:str; detected:bool; detail:str
def run_gate(apk:Path,manifest:Path)->tuple[bool,str]:
    c=subprocess.run([sys.executable,str(ROOT/'verification/scripts/apk_gate.py'),'--apk',str(apk),'--manifest-xml',str(manifest),'--variant','release'],cwd=ROOT,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,check=False,timeout=120)
    return c.returncode!=0,c.stdout[-1600:]
def rewrite(apk:Path,out:Path,skip:set[str]=set(),extra:list[tuple[str,bytes,int]]=[]):
    with zipfile.ZipFile(apk,'r') as src, zipfile.ZipFile(out,'w') as dst:
        for info in src.infolist():
            if info.filename in skip: continue
            dst.writestr(info,src.read(info.filename))
        for name,data,compression in extra: dst.writestr(name,data,compress_type=compression)
def main()->int:
    p=argparse.ArgumentParser(); p.add_argument('--apk',required=True,type=Path); p.add_argument('--manifest-xml',required=True,type=Path); a=p.parse_args(); results=[]
    with tempfile.TemporaryDirectory(prefix='toolbox-postbuild-mutation-') as td:
        t=Path(td); apk=a.apk.resolve(); manifest=a.manifest_xml.resolve()
        cases=[]
        missing=t/'missing.apk'; rewrite(apk,missing,skip={'classes.dex'}); cases.append(('missing-required-entry',missing,manifest))
        native=t/'native.apk'; rewrite(apk,native,extra=[('lib/arm64-v8a/fake.so',b'not-elf',zipfile.ZIP_STORED)]); cases.append(('unexpected-native-payload',native,manifest))
        collision=t/'collision.apk'; rewrite(apk,collision,extra=[('META-INF/toolbox-case',b'a',zipfile.ZIP_STORED),('META-INF/TOOLBOX-CASE',b'b',zipfile.ZIP_STORED)]); cases.append(('normalized-path-collision',collision,manifest))
        unsafe=t/'unsafe.apk'; rewrite(apk,unsafe,extra=[('../toolbox-escape',b'x',zipfile.ZIP_STORED)]); cases.append(('unsafe-package-path',unsafe,manifest))
        bomb=t/'bomb.apk'; rewrite(apk,bomb,extra=[('assets/toolbox-bomb.bin',b'0'*(2*1024*1024),zipfile.ZIP_DEFLATED)]); cases.append(('pathological-compression',bomb,manifest))
        wrong_manifest=t/'wrong-manifest.xml'; text=manifest.read_text(encoding='utf-8'); text=text.replace('@android:style/Theme.Material.NoActionBar','@android:style/Theme.Material',1); wrong_manifest.write_text(text,encoding='utf-8'); cases.append(('wrong-final-theme',apk,wrong_manifest))
        wrong_package=t/'wrong-package.xml'; text=manifest.read_text(encoding='utf-8'); text=text.replace('package="io.toolbox.app"','package="io.toolbox.wrong"',1); wrong_package.write_text(text,encoding='utf-8'); cases.append(('wrong-final-package',apk,wrong_package))
        for name,mapk,mmanifest in cases:
            detected,detail=run_gate(mapk,mmanifest); results.append(Result(name,detected,detail))
    escaped=[r for r in results if not r.detected]; EVIDENCE.mkdir(parents=True,exist_ok=True); payload={'schemaVersion':1,'gate':'POSTBUILD_MUTATION_GATE','status':'PASS' if not escaped else 'NOT_PROVEN','gitSha':os.environ.get('GITHUB_SHA'),'definedMutations':len(results),'detectedMutations':len(results)-len(escaped),'faultEscape':len(escaped),'results':[asdict(r) for r in results]}; (EVIDENCE/'asset-mutations.json').write_text(json.dumps(payload,indent=2,sort_keys=True)+'\n')
    if escaped:
        print('POSTBUILD_MUTATION_GATE = NOT_PROVEN',file=sys.stderr)
        for r in escaped: print('ESCAPE '+r.name,file=sys.stderr)
        return 1
    print('POSTBUILD_MUTATION_GATE = PASS'); return 0
if __name__=='__main__': raise SystemExit(main())

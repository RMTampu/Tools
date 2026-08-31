#!/usr/bin/env python3
"""Mutation adequacy for application/asset prebuild verifiers without building APKs."""
from __future__ import annotations
import io,json,os,subprocess,sys,tarfile,tempfile
from dataclasses import asdict,dataclass
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];EVIDENCE_DIR=ROOT/'verification/evidence'
@dataclass
class Mutation:name:str;verifier:str;path:str;old:str|None;new:str|None;delete:bool=False
@dataclass
class Result:name:str;detected:bool;exitCode:int;detail:str
MUTATIONS=[
 Mutation('wrong-target-sdk','verification/scripts/prebuild_gate.py','toolbox-app/build.gradle.kts','targetSdk = 30','targetSdk = 31'),
 Mutation('forbidden-internet-permission','verification/scripts/prebuild_gate.py','toolbox-app/src/main/AndroidManifest.xml','<application','<uses-permission android:name="android.permission.INTERNET" />\n\n    <application'),
 Mutation('dynamic-code-entry','verification/scripts/prebuild_gate.py','toolbox-app/src/main/kotlin/io/toolbox/app/RuntimeCompatibility.kt','object RuntimeCompatibility {','object RuntimeCompatibility {\n    private const val forbiddenProbe = "Class.forName"'),
 Mutation('malformed-manifest','verification/scripts/asset_prebuild_gate.py','toolbox-app/src/main/AndroidManifest.xml','</manifest>','<broken>'),
 Mutation('wrong-framework-theme','verification/scripts/asset_prebuild_gate.py','toolbox-app/src/main/AndroidManifest.xml','@android:style/Theme.Material.NoActionBar','@android:style/Theme.Material'),
 Mutation('wrong-app-icon-route','verification/scripts/asset_prebuild_gate.py','toolbox-app/src/main/AndroidManifest.xml','@drawable/ic_toolbox','@drawable/missing_icon'),
 Mutation('malformed-app-icon','verification/scripts/asset_prebuild_gate.py','toolbox-app/src/main/res/drawable/ic_toolbox.xml','</vector>','<broken>'),
 Mutation('missing-app-icon','verification/scripts/asset_prebuild_gate.py','toolbox-app/src/main/res/drawable/ic_toolbox.xml',None,None,delete=True),
 Mutation('dynamic-asset-route','verification/scripts/asset_prebuild_gate.py','toolbox-app/src/main/kotlin/io/toolbox/app/RuntimeCompatibility.kt','object RuntimeCompatibility {','object RuntimeCompatibility {\n    private const val forbiddenAssetProbe = "getIdentifier("'),
 Mutation('missing-manifest','verification/scripts/prebuild_gate.py','toolbox-app/src/main/AndroidManifest.xml',None,None,delete=True),
 Mutation('missing-dependency-lock','verification/scripts/prebuild_gate.py','toolbox-app/gradle.lockfile',None,None,delete=True),
]
def archive_head()->bytes:return subprocess.check_output(['git','archive','HEAD'],cwd=ROOT)
def extract(data:bytes,target:Path)->None:
    with tarfile.open(fileobj=io.BytesIO(data),mode='r:') as tf:tf.extractall(target)
def apply_mutation(root:Path,m:Mutation)->None:
    path=root/m.path
    if m.delete:path.unlink();return
    text=path.read_text(encoding='utf-8')
    if m.old not in text:raise RuntimeError(f'mutation anchor not found: {m.name}')
    path.write_text(text.replace(m.old,m.new,1),encoding='utf-8')
def main()->int:
    data=archive_head();results=[]
    for m in MUTATIONS:
        with tempfile.TemporaryDirectory(prefix=f'toolbox-mutation-{m.name}-') as tmp:
            replica=Path(tmp);extract(data,replica)
            try:
                apply_mutation(replica,m);c=subprocess.run([sys.executable,str(replica/m.verifier)],cwd=replica,env=os.environ.copy(),stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,check=False,timeout=120);results.append(Result(m.name,c.returncode!=0,c.returncode,c.stdout[-1600:]))
            except Exception as exc:results.append(Result(m.name,False,-1,str(exc)))
    escaped=[r for r in results if not r.detected];EVIDENCE_DIR.mkdir(parents=True,exist_ok=True);payload={'schemaVersion':2,'gate':'PREBUILD_MUTATION_GATE','gitSha':os.environ.get('GITHUB_SHA'),'status':'PASS' if not escaped else 'NOT_PROVEN','definedMutations':len(results),'detectedMutations':len(results)-len(escaped),'faultEscape':len(escaped),'results':[asdict(r) for r in results]};(EVIDENCE_DIR/'prebuild-mutations.json').write_text(json.dumps(payload,indent=2,sort_keys=True)+'\n',encoding='utf-8')
    if escaped:
        print('PREBUILD_MUTATION_GATE = NOT_PROVEN',file=sys.stderr)
        for r in escaped:print(f'ESCAPE {r.name}: {r.detail}',file=sys.stderr)
        return 1
    print('PREBUILD_MUTATION_GATE = PASS');return 0
if __name__=='__main__':raise SystemExit(main())

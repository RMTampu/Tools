#!/usr/bin/env python3
"""Fail-closed qualification of the actually attached Android 11 ARM64 target."""
from __future__ import annotations
import hashlib,json,os,re,shutil,subprocess,sys
from dataclasses import asdict,dataclass
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
E=ROOT/'verification/evidence'
@dataclass
class Check: name:str; passed:bool; detail:str
def run(*args,check=True,timeout=30):
    p=subprocess.run(args,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True,timeout=timeout,check=False)
    if check and p.returncode!=0: raise RuntimeError(f"command failed ({p.returncode}): {' '.join(args)}; stderr={p.stderr.strip()}")
    return p.stdout.strip()
def source_sha():
    v=os.environ.get('GITHUB_SHA','').strip().lower()
    if re.fullmatch(r'[0-9a-f]{40}',v): return v
    try:
        v=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True,stderr=subprocess.DEVNULL).strip().lower()
        return v if re.fullmatch(r'[0-9a-f]{40}',v) else None
    except Exception:return None
def main():
    E.mkdir(parents=True,exist_ok=True); checks=[]; obs={}; sha=source_sha()
    def add(n,c,d): checks.append(Check(n,bool(c),d))
    add('runtime-source-sha-known',sha is not None,f'gitSha={sha}')
    adb=shutil.which('adb'); add('runtime-adb-present',adb is not None,adb or 'adb missing')
    try:
        if adb:
            ver=run(adb,'version'); add('runtime-adb-version-known',bool(ver),ver.splitlines()[0] if ver else '')
            rows=[]
            for line in run(adb,'devices','-l').splitlines()[1:]:
                p=line.strip().split()
                if len(p)>=2 and p[1]=='device': rows.append(p[0])
            add('runtime-device-unique',len(rows)==1,f'readyDevices={len(rows)}')
            serial=rows[0] if len(rows)==1 else None
            add('runtime-device-connected',serial is not None,'one ready adb target required')
            if serial:
                prefix=(adb,'-s',serial)
                state=run(*prefix,'get-state'); api=run(*prefix,'shell','getprop','ro.build.version.sdk')
                release=run(*prefix,'shell','getprop','ro.build.version.release')
                abi=run(*prefix,'shell','getprop','ro.product.cpu.abi'); abilist=run(*prefix,'shell','getprop','ro.product.cpu.abilist')
                boot=run(*prefix,'shell','getprop','sys.boot_completed'); fp=run(*prefix,'shell','getprop','ro.build.fingerprint')
                qemu=run(*prefix,'shell','getprop','ro.kernel.qemu',check=False)
                add('runtime-device-state-ready',state=='device',f'state={state}')
                add('runtime-device-api30',api=='30',f'api={api}')
                add('runtime-device-android11',release=='11',f'release={release}')
                add('runtime-device-arm64',abi=='arm64-v8a',f'abi={abi}, abiList={abilist}')
                add('runtime-device-boot-complete',boot=='1',f'boot={boot}')
                add('runtime-device-fingerprint-known',bool(fp),f'fingerprintPresent={bool(fp)}')
                add('runtime-transport-qualified',state=='device' and boot=='1','adb ready + boot complete')
                obs={'adbVersion':ver,'deviceSerialSha256':hashlib.sha256(serial.encode()).hexdigest(),'device':{'api':api,'release':release,'abi':abi,'abiList':abilist,'fingerprint':fp,'runtimeKind':'emulator' if qemu=='1' else 'device'}}
    except Exception as exc:add('runtime-environment-gate-execution',False,str(exc))
    failed=[c for c in checks if not c.passed]
    payload={'schemaVersion':2,'gate':'RUNTIME_ENVIRONMENT_GATE','status':'PASS' if not failed else 'NOT_PROVEN','gitSha':sha,'hostArchitectureClaim':'NONE','requiredTarget':{'androidRelease':'11','api':30,'primaryAbi':'arm64-v8a','transport':'adb'},'checks':[asdict(c) for c in checks],'failed':[c.name for c in failed],'observations':obs,'unknown':0 if not failed else len(failed),'missing':0 if not failed else sum(1 for c in failed if 'present' in c.name or 'connected' in c.name),'unproven':0 if not failed else len(failed),'skipped':0,'indeterminate':0,'staleEvidence':0,'faultEscape':0}
    (E/'runtime-tools.json').write_text(json.dumps(payload,indent=2,sort_keys=True)+'\n',encoding='utf-8')
    if failed:
        print('RUNTIME_ENVIRONMENT_GATE = NOT_PROVEN',file=sys.stderr)
        for c in failed: print(f'FAIL {c.name}: {c.detail}',file=sys.stderr)
        return 1
    print('RUNTIME_ENVIRONMENT_GATE = PASS'); return 0
if __name__=='__main__': raise SystemExit(main())

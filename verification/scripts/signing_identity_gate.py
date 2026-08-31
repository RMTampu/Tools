#!/usr/bin/env python3
"""Fail-closed prebuild verification of the release signing certificate identity."""
from __future__ import annotations
import argparse, hashlib, json, os, re, subprocess, sys
from dataclasses import asdict, dataclass
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]; CONTRACT_PATH=ROOT/'verification/signing_contract.json'; EVIDENCE_DIR=ROOT/'verification/evidence'
@dataclass
class Check: name:str; passed:bool; detail:str
def current_git_sha()->str|None:
    value=os.environ.get('GITHUB_SHA','').strip()
    if re.fullmatch(r'[0-9a-fA-F]{40}',value): return value.lower()
    try:
        value=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True,stderr=subprocess.DEVNULL).strip(); return value.lower() if re.fullmatch(r'[0-9a-fA-F]{40}',value) else None
    except Exception:return None
def normalize(value:str)->str:return re.sub(r'[^0-9a-f]','',value.lower())
def sha256(path:Path)->str:
    h=hashlib.sha256()
    with path.open('rb') as fh:
        for chunk in iter(lambda:fh.read(1024*1024),b''):h.update(chunk)
    return h.hexdigest()
def main()->int:
    p=argparse.ArgumentParser(); p.add_argument('--keystore',required=True,type=Path); p.add_argument('--store-password',required=True); p.add_argument('--alias',required=True); a=p.parse_args(); checks=[]
    def check(n,c,d):checks.append(Check(n,bool(c),d))
    contract=json.loads(CONTRACT_PATH.read_text(encoding='utf-8')); policy=contract['release']; ks=a.keystore.resolve(); expected_alias=policy.get('expectedAlias'); check('release-keystore-present',ks.is_file() and ks.stat().st_size>0,str(ks)); check('release-alias-contract',bool(expected_alias),f'expectedAlias={expected_alias}'); check('release-alias-match',a.alias==expected_alias,f'actual={a.alias}, expected={expected_alias}'); actual=None
    if ks.is_file():
        c=subprocess.run(['keytool','-list','-v','-keystore',str(ks),'-storepass',a.store_password,'-alias',a.alias],stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True,check=False); check('release-keystore-readable',c.returncode==0,f'keytoolExit={c.returncode}')
        if c.returncode==0:
            m=re.search(r'SHA256:\s*([0-9A-Fa-f:]{64,95})',c.stdout); actual=normalize(m.group(1)) if m else None; check('release-certificate-sha256-observed',actual is not None and len(actual)==64,f'certSha256={actual}')
    expected=policy.get('expectedCertificateSha256'); check('release-signing-identity-pinned',bool(expected),'verification/signing_contract.json')
    if expected and actual:check('release-signing-identity-match',normalize(expected)==actual,f'actual={actual}, expected={normalize(expected)}')
    check('release-v2-contract',policy.get('requireV2') is True,f"requireV2={policy.get('requireV2')}"); check('release-v3-contract',policy.get('requireV3') is True,f"requireV3={policy.get('requireV3')}")
    failed=[x for x in checks if not x.passed]; EVIDENCE_DIR.mkdir(parents=True,exist_ok=True); payload={'schemaVersion':2,'gate':'SIGNING_IDENTITY_GATE','status':'PASS' if not failed else 'NOT_PROVEN','gitSha':current_git_sha(),'keystoreSha256':sha256(ks) if ks.is_file() else None,'alias':a.alias,'certificateSha256':actual,'checks':[asdict(x) for x in checks],'failed':[x.name for x in failed]}; (EVIDENCE_DIR/'signing-identity.json').write_text(json.dumps(payload,indent=2,sort_keys=True)+'\n',encoding='utf-8')
    if actual:print(f'RELEASE_CERT_SHA256={actual}')
    if failed:
        print('SIGNING_IDENTITY_GATE = NOT_PROVEN',file=sys.stderr)
        for x in failed:print(f'FAIL {x.name}: {x.detail}',file=sys.stderr)
        return 1
    print('SIGNING_IDENTITY_GATE = PASS');return 0
if __name__=='__main__':raise SystemExit(main())

#!/usr/bin/env python3
"""Verify final APK container, merged manifest, ABI/package semantics and source binding."""
from __future__ import annotations
import argparse, hashlib, json, os, posixpath, re, subprocess, sys, unicodedata, xml.etree.ElementTree as ET, zipfile
from dataclasses import asdict, dataclass
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
SCOPE=json.loads((ROOT/'verification/application_scope.json').read_text(encoding='utf-8'))
EVIDENCE_DIR=ROOT/'verification/evidence'; ANDROID='{http://schemas.android.com/apk/res/android}'
@dataclass
class Check: name:str; passed:bool; detail:str
def digest(path:Path)->str:
    h=hashlib.sha256()
    with path.open('rb') as fh:
        for chunk in iter(lambda:fh.read(1024*1024),b''): h.update(chunk)
    return h.hexdigest()
def git_sha()->str|None:
    value=os.environ.get('GITHUB_SHA','').strip()
    if re.fullmatch(r'[0-9a-fA-F]{40}',value): return value.lower()
    try:
        value=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True,stderr=subprocess.DEVNULL).strip(); return value.lower() if re.fullmatch(r'[0-9a-fA-F]{40}',value) else None
    except Exception:return None
def main()->int:
    p=argparse.ArgumentParser(); p.add_argument('--apk',required=True,type=Path); p.add_argument('--manifest-xml',required=True,type=Path); p.add_argument('--variant',required=True,choices=('debug','release')); a=p.parse_args(); checks=[]
    def check(n,c,d): checks.append(Check(n,bool(c),d))
    apk=a.apk.resolve(); manifest_xml=a.manifest_xml.resolve(); source_sha=git_sha(); check('artifact-source-sha-known',source_sha is not None,f'gitSha={source_sha}'); check('apk-present',apk.is_file() and apk.stat().st_size>0,str(apk)); check('merged-manifest-present',manifest_xml.is_file() and manifest_xml.stat().st_size>0,str(manifest_xml))
    if not apk.is_file() or not manifest_xml.is_file(): return finish(a.variant,apk,checks,{})
    names=[]; total_uncompressed=0; total_compressed=0; pathological=[]; unsafe=[]; native=[]; normalized_seen={}; collisions=[]
    try:
        with zipfile.ZipFile(apk,'r') as z:
            for info in z.infolist():
                name=info.filename; names.append(name); total_uncompressed+=info.file_size; total_compressed+=info.compress_size
                normalized=posixpath.normpath(name.replace('\\','/'))
                if name.startswith('/') or '\\' in name or normalized=='..' or normalized.startswith('../') or '/../' in f'/{normalized}/': unsafe.append(name)
                identity=unicodedata.normalize('NFC',normalized).casefold(); prior=normalized_seen.get(identity)
                if prior is not None and prior!=name: collisions.append((prior,name))
                else: normalized_seen[identity]=name
                if name.endswith('.so') or name.startswith('lib/'): native.append(name)
                if info.file_size>=1024*1024 and info.compress_size>0:
                    ratio=info.file_size/info.compress_size
                    if ratio>100: pathological.append(f'{name}:{ratio:.1f}x')
    except Exception as exc:
        check('apk-zip-readable',False,str(exc)); return finish(a.variant,apk,checks,{})
    duplicates=sorted({n for n in names if names.count(n)>1}); check('apk-no-duplicate-entry',not duplicates,f'duplicates={duplicates}'); check('apk-canonical-paths',not unsafe,f'unsafe={unsafe}'); check('apk-normalized-path-unique',not collisions,f'collisions={collisions}'); check('apk-no-pathological-compression',not pathological,f'entries={pathological}'); check('apk-required-container-entries','AndroidManifest.xml' in names and 'classes.dex' in names and 'resources.arsc' in names,'manifest/classes/resources')
    r7_empty=SCOPE['domainScope']['R7']['status']=='CLOSED_EMPTY'; check('r7-native-archive-closure',(not r7_empty) or not native,f'native={native}')
    max_apk=int(SCOPE['budgets'].get('apkBytes',64*1024*1024)); max_expanded=int(SCOPE['budgets'].get('apkUncompressedBytes',128*1024*1024)); check('apk-size-budget',apk.stat().st_size<=max_apk,f'actual={apk.stat().st_size}, max={max_apk}'); check('apk-expanded-size-budget',total_uncompressed<=max_expanded,f'actual={total_uncompressed}, max={max_expanded}')
    manifest_details={}
    try:
        root=ET.parse(manifest_xml).getroot(); expected_package=SCOPE['debugApplicationId'] if a.variant=='debug' else SCOPE['applicationId']; actual_package=root.attrib.get('package',''); check('final-package-id',actual_package==expected_package,f'actual={actual_package}, expected={expected_package}')
        version_code=root.attrib.get(ANDROID+'versionCode'); version_name=root.attrib.get(ANDROID+'versionName'); expected_name='0.1.0-debug' if a.variant=='debug' else '0.1.0'; check('final-version-code',version_code=='1',f'actual={version_code}, expected=1'); check('final-version-name',version_name==expected_name,f'actual={version_name}, expected={expected_name}')
        uses_sdk=root.find('uses-sdk'); min_sdk=uses_sdk.attrib.get(ANDROID+'minSdkVersion') if uses_sdk is not None else None; target_sdk=uses_sdk.attrib.get(ANDROID+'targetSdkVersion') if uses_sdk is not None else None; req=str(SCOPE['platform']['androidApi']); check('final-min-sdk',min_sdk==req,f'actual={min_sdk}, expected={req}'); check('final-target-sdk',target_sdk==req,f'actual={target_sdk}, expected={req}')
        app=root.find('application'); check('final-application-present',app is not None,'merged manifest'); cleartext=backup=debuggable=theme=app_name=None
        if app is not None:
            cleartext=app.attrib.get(ANDROID+'usesCleartextTraffic'); backup=app.attrib.get(ANDROID+'allowBackup'); debuggable=app.attrib.get(ANDROID+'debuggable','false'); theme=app.attrib.get(ANDROID+'theme'); app_name=app.attrib.get(ANDROID+'name'); check('final-cleartext-disabled',cleartext=='false',f'actual={cleartext}'); check('final-backup-disabled',backup=='false',f'actual={backup}'); check('final-debuggable-contract',(debuggable=='true')==(a.variant=='debug'),f'actual={debuggable}'); check('final-framework-theme',theme=='@android:style/Theme.Material.NoActionBar',f'actual={theme}'); check('final-application-class',bool(app_name) and app_name.endswith('.ToolBoxApplication'),f'actual={app_name}')
        permissions=sorted(node.attrib.get(ANDROID+'name','') for node in root.findall('uses-permission')); check('final-permission-universe',not permissions,f'permissions={permissions}')
        exported=[]
        for tag in ('activity','activity-alias','service','receiver','provider'):
            for node in root.findall(f'application/{tag}'):
                if node.attrib.get(ANDROID+'exported')=='true': exported.append((tag,node.attrib.get(ANDROID+'name','')))
        exported_ok=len(exported)==1 and exported[0][0]=='activity' and exported[0][1].endswith('.MainActivity'); check('final-exported-component-universe',exported_ok,f'exported={exported}')
        manifest_details={'package':actual_package,'versionCode':version_code,'versionName':version_name,'minSdk':min_sdk,'targetSdk':target_sdk,'permissions':permissions,'exported':exported,'cleartext':cleartext,'allowBackup':backup,'debuggable':debuggable,'theme':theme,'applicationClass':app_name}
    except Exception as exc: check('merged-manifest-parse',False,str(exc))
    details={'gitSha':source_sha,'sha256':digest(apk),'sizeBytes':apk.stat().st_size,'entryCount':len(names),'uncompressedBytes':total_uncompressed,'compressedPayloadBytes':total_compressed,'nativeEntries':native,'manifest':manifest_details}; return finish(a.variant,apk,checks,details)
def finish(variant:str,apk:Path,checks:list[Check],details:dict)->int:
    EVIDENCE_DIR.mkdir(parents=True,exist_ok=True); failed=[x for x in checks if not x.passed]; payload={'schemaVersion':2,'gate':'FINAL_APK_GATE','variant':variant,'status':'PASS' if not failed else 'NOT_PROVEN','gitSha':details.get('gitSha') or git_sha(),'apk':str(apk),'checks':[asdict(x) for x in checks],'failed':[x.name for x in failed],'details':details}; (EVIDENCE_DIR/f'apk-{variant}.json').write_text(json.dumps(payload,indent=2,sort_keys=True)+'\n',encoding='utf-8')
    if failed:
        print(f'FINAL_APK_GATE[{variant}] = NOT_PROVEN',file=sys.stderr)
        for x in failed: print(f'FAIL {x.name}: {x.detail}',file=sys.stderr)
        return 1
    print(f'FINAL_APK_GATE[{variant}] = PASS'); return 0
if __name__=='__main__': raise SystemExit(main())

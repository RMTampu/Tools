#!/usr/bin/env python3
"""Closed bootstrap asset + route proof before the application build boundary."""
from __future__ import annotations
import hashlib,json,os,re,subprocess,sys,unicodedata,xml.etree.ElementTree as ET
from dataclasses import asdict,dataclass
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];CONTRACT_PATH=ROOT/'verification/asset_contracts.json';SCOPE_PATH=ROOT/'verification/application_scope.json';EVIDENCE_DIR=ROOT/'verification/evidence';ANDROID_NS='{http://schemas.android.com/apk/res/android}'
@dataclass
class Check:name:str;passed:bool;detail:str
def sha256(path:Path)->str:
    h=hashlib.sha256()
    with path.open('rb') as fh:
        for block in iter(lambda:fh.read(1024*1024),b''):h.update(block)
    return h.hexdigest()
def git_sha()->str|None:
    value=os.environ.get('GITHUB_SHA','').strip()
    if re.fullmatch(r'[0-9a-fA-F]{40}',value):return value.lower()
    try:
        value=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True,stderr=subprocess.DEVNULL).strip();return value.lower() if re.fullmatch(r'[0-9a-fA-F]{40}',value) else None
    except Exception:return None
def android_jar(api:int)->Path|None:
    for key in ('ANDROID_SDK_ROOT','ANDROID_HOME'):
        rr=os.environ.get(key)
        if rr:
            p=Path(rr)/'platforms'/f'android-{api}'/'android.jar'
            if p.is_file():return p
    return None
def main()->int:
    checks=[];route_checks={}
    def check(n,c,d):checks.append(Check(n,bool(c),d))
    contract=json.loads(CONTRACT_PATH.read_text());scope=json.loads(SCOPE_PATH.read_text());source_sha=git_sha();check('asset-git-sha-known',source_sha is not None,f'gitSha={source_sha}');check('asset-contract-scope-api30',contract['platform']['androidApi']==30,str(contract['platform']));check('asset-contract-scope-arm64',contract['platform']['abi']=='arm64-v8a',str(contract['platform']))
    required=contract['assetUniverse']['required'];expected_sources={i['source'] for i in required};actual=set();manifest=ROOT/'toolbox-app/src/main/AndroidManifest.xml';icon=ROOT/'toolbox-app/src/main/res/drawable/ic_toolbox.xml'
    if manifest.is_file():actual.add(str(manifest.relative_to(ROOT)))
    for directory in (ROOT/'toolbox-app/src/main/res',ROOT/'toolbox-app/src/main/assets'):
        if directory.exists():
            for p in directory.rglob('*'):
                if p.is_file():actual.add(str(p.relative_to(ROOT)))
    check('asset-universe-exact',actual==expected_sources,f'actual={sorted(actual)}, expected={sorted(expected_sources)}');declared_res=contract['assetUniverse']['customResDirectory'];check('asset-custom-res-finite-locked',declared_res.get('status')=='FINITE_LOCKED' and set(declared_res.get('sources',[]))=={str(icon.relative_to(ROOT))},str(declared_res))
    norm={};collisions=[]
    for p in actual:
        ident=unicodedata.normalize('NFC',p).casefold()
        if ident in norm and norm[ident]!=p:collisions.append((norm[ident],p))
        norm[ident]=p
    check('asset-source-canonical-paths',not collisions and all('..' not in Path(p).parts for p in actual),f'collisions={collisions}')
    manifest_ok=False;theme=None;icon_ref=None;extract_native=None
    try:
        mr=ET.parse(manifest).getroot();app=mr.find('application');activity=mr.find('application/activity');theme=app.attrib.get(ANDROID_NS+'theme') if app is not None else None;icon_ref=app.attrib.get(ANDROID_NS+'icon') if app is not None else None;extract_native=app.attrib.get(ANDROID_NS+'extractNativeLibs') if app is not None else None
        manifest_ok=(app is not None and activity is not None and app.attrib.get(ANDROID_NS+'name')=='.ToolBoxApplication' and app.attrib.get(ANDROID_NS+'allowBackup')=='false' and app.attrib.get(ANDROID_NS+'usesCleartextTraffic')=='false' and theme=='@android:style/Theme.Material.NoActionBar' and icon_ref=='@drawable/ic_toolbox' and extract_native is None and activity.attrib.get(ANDROID_NS+'name')=='.MainActivity' and activity.attrib.get(ANDROID_NS+'exported')=='true');check('asset-manifest-semantic-contract',manifest_ok,f'theme={theme}, icon={icon_ref}, extractNativeLibs={extract_native}')
    except Exception as exc:check('asset-manifest-parse',False,str(exc))
    vector_ok=False;path_count=0
    try:
        vr=ET.parse(icon).getroot();path_count=len(vr.findall('path'));vector_ok=(vr.tag=='vector' and vr.attrib.get(ANDROID_NS+'width')=='48dp' and vr.attrib.get(ANDROID_NS+'height')=='48dp' and vr.attrib.get(ANDROID_NS+'viewportWidth')=='48' and vr.attrib.get(ANDROID_NS+'viewportHeight')=='48' and path_count>=2 and all(re.fullmatch(r'#[0-9A-Fa-f]{8}',p.attrib.get(ANDROID_NS+'fillColor','')) for p in vr.findall('path')) and all(bool(p.attrib.get(ANDROID_NS+'pathData')) for p in vr.findall('path')));check('asset-icon-vector-semantic-contract',vector_ok,f'paths={path_count}')
    except Exception as exc:check('asset-icon-vector-parse',False,str(exc))
    check('asset-icon-size-budget',icon.is_file() and icon.stat().st_size<=int(contract['budgets']['singleVectorDrawableBytes']),f'bytes={icon.stat().st_size if icon.is_file() else None}')
    source_text='\n'.join(p.read_text(encoding='utf-8',errors='replace') for p in (ROOT/'toolbox-app/src/main').rglob('*') if p.is_file() and p.suffix in {'.kt','.java','.xml'});dynamic_tokens=['getIdentifier(','AssetManager.open(','openRawResource(','Resources.getIdentifier('];dynamic_hits=[t for t in dynamic_tokens if t in source_text];check('asset-dynamic-lookup-closed-empty',not dynamic_hits,f'hits={dynamic_hits}')
    framework_refs=sorted(set(re.findall(r'(?<![\w])@([a-zA-Z0-9_.]+):([a-zA-Z0-9_]+)/([a-zA-Z0-9_.]+)',source_text)));allowed_refs=[('android','style','Theme.Material.NoActionBar')];check('asset-framework-reference-universe-exact',framework_refs==allowed_refs,f'refs={framework_refs}')
    platform_jar=android_jar(30);check('asset-api30-framework-present',platform_jar is not None,str(platform_jar));framework_symbol=False
    if platform_jar is not None:
        c=subprocess.run(['javap','-classpath',str(platform_jar),'android.R$style'],stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True,check=False);framework_symbol=c.returncode==0 and 'Theme_Material_NoActionBar' in c.stdout;check('asset-framework-theme-symbol-api30',framework_symbol,f'javapExit={c.returncode}')
    routes={r['routeId']:r for r in contract['routes']};theme_route=routes.get('ROUTE-FRAMEWORK-THEME');icon_route=routes.get('ROUTE-APP-ICON');required_route_fields={'routeId','consumer','source','kind','androidApi','exactIdentity','authority','state'}
    route_checks['4.0_ROUTE_DOMAIN_LOCK']=set(routes)=={'ROUTE-FRAMEWORK-THEME','ROUTE-APP-ICON'} and not dynamic_hits
    route_checks['4.1_SEMANTIC_INTENT_LOCK']=theme_route is not None and icon_route is not None and theme==theme_route['source'] and icon_ref==icon_route['source']
    route_checks['4.2_OBSERVATIONAL_CLOSURE']=theme_route is not None and icon_route is not None and theme_route['kind']=='ANDROID_FRAMEWORK_RESOURCE' and icon_route['kind']=='ANDROID_APP_RESOURCE'
    route_checks['4.3_EPISTEMIC_CLOSURE']=all(r['androidApi']==30==scope['platform']['androidApi'] for r in routes.values())
    route_checks['4.4_UNIQUE_ROUTE_MODEL']=len(routes)==2 and theme_route['source']!=icon_route['source']
    route_checks['4.5_CAUSAL_ROUTE_MODEL']=theme=='@android:style/Theme.Material.NoActionBar' and icon_ref=='@drawable/ic_toolbox' and icon.is_file()
    route_checks['4.6_CLOSED_ROUTE_REPRESENTATION']=all(set(r)>=required_route_fields for r in routes.values())
    route_checks['4.7_ROUTE_TRANSLATION']=theme==theme_route['source'] and icon_ref==icon_route['source']
    route_checks['4.8_ROUTE_GRAPH_CLOSURE']=theme_route['consumer']=='application theme resolver' and icon_route['consumer']=='Android PackageManager/launcher icon resolver' and not dynamic_hits
    route_checks['4.9_ROBUST_CONTEXTUAL_ROUTE']=theme_route['authority']=='framework package only' and icon_route['authority']=='ToolBox application package only'
    route_checks['4.10_ANDROID_RESOLUTION_REFINEMENT']=framework_symbol and vector_ok
    route_checks['4.11_FOUNDATIONAL_ROUTE_CHECK']=manifest_ok and framework_symbol and vector_ok and framework_refs==allowed_refs
    route_checks['4.12_FAULT_DOMAIN_ROUTE_CHALLENGE']=theme_route['source']!='@android:style/Theme.Material' and icon_route['source']!='@drawable/missing_icon' and 'getIdentifier(' not in source_text
    route_checks['4.13_FINAL_ROUTE_CLOSURE']=all(route_checks.values())
    for n,p in route_checks.items():check('route-'+n,p,'closed bootstrap route proof')
    route_pass=all(route_checks.values());check('ROUTE_PROOF_PASS',route_pass,f'subgates={route_checks}')
    expected_faults={'PRESENCE_MISSING','PATH_AMBIGUITY','SYNTAX_MALFORMED_MANIFEST','SEMANTIC_WRONG_MANIFEST_VALUE','REFERENCE_FRAMEWORK_THEME_MISSING','CONSUMER_BINDING_ERROR','PACKAGING_REQUIRED_ENTRY_MISSING','PACKAGING_UNEXPECTED_NATIVE_PAYLOAD','FINAL_MANIFEST_DRIFT','RESOURCE_BUDGET_EXCEEDED'};check('asset-fault-model-closed',set(contract.get('activeFaultClasses',[]))==expected_faults,str(contract.get('activeFaultClasses')));check('asset-package-contract-closed',contract['packageContract']['requiredEntries']==['AndroidManifest.xml','classes.dex'],str(contract['packageContract']));check('asset-resource-budget-declared',contract['budgets']['apkBytes']>0 and contract['budgets']['apkUncompressedBytes']>0 and contract['budgets']['singleVectorDrawableBytes']>0,str(contract['budgets']))
    failed=[c for c in checks if not c.passed];EVIDENCE_DIR.mkdir(parents=True,exist_ok=True);payload={'schemaVersion':2,'gate':'ASSET_PREBUILD_GATE','status':'PASS' if not failed else 'NOT_PROVEN','gitSha':source_sha,'contractSha256':sha256(CONTRACT_PATH),'manifestSha256':sha256(manifest) if manifest.is_file() else None,'iconSha256':sha256(icon) if icon.is_file() else None,'requiredAssets':len(required),'routeProof':'ROUTE_PROOF_PASS' if route_pass else 'NOT_PROVEN','routeSubgates':route_checks,'checks':[asdict(c) for c in checks],'failed':[c.name for c in failed]};(EVIDENCE_DIR/'asset-prebuild.json').write_text(json.dumps(payload,indent=2,sort_keys=True)+'\n')
    if failed:
        print('ASSET_PREBUILD_GATE = NOT_PROVEN',file=sys.stderr)
        for c in failed:print(f'FAIL {c.name}: {c.detail}',file=sys.stderr)
        return 1
    print('ROUTE_PROOF_PASS');print('ASSET_PREBUILD_GATE = PASS');return 0
if __name__=='__main__':raise SystemExit(main())

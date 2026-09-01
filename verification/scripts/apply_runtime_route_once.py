#!/usr/bin/env python3
from pathlib import Path
import re

ROOT=Path(__file__).resolve().parents[2]

def write(path, text):
    p=ROOT/path
    p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(text.rstrip()+"\n",encoding="utf-8")

runtime_env=r'''#!/usr/bin/env python3
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
'''
write('verification/scripts/runtime_environment_gate.py',runtime_env)

guard=r'''#!/usr/bin/env python3
"""Prevent recurrence of proven-invalid runtime and artifact routes."""
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[2]
static=(ROOT/'.github/workflows/application-safe-100.yml').read_text(encoding='utf-8')
runtime=(ROOT/'.github/workflows/application-runtime-r9.yml').read_text(encoding='utf-8')
errors=[]
if (ROOT/'.github/workflows/android11-arm64-runtime-host-probe.yml').exists(): errors.append('obsolete hosted runtime probe exists')
if '\n  application-runtime-r9:\n' in static: errors.append('static workflow still owns runtime job')
if 'Prepare canonical runtime bundle' not in static or 'path: verification/runtime-bundle/' not in static: errors.append('canonical artifact bundle missing')
if 'candidate/verification/' in runtime: errors.append('wrong nested artifact path reintroduced')
if 'runs-on: [self-hosted, linux, toolbox-android11-arm64-runtime]' not in runtime: errors.append('runtime not bound to qualified self-hosted label')
if 'runtime_environment_gate.py' not in runtime: errors.append('runtime target qualification missing')
job=runtime.split('\n  runtime-r9:\n',1)[1] if '\n  runtime-r9:\n' in runtime else runtime
for token in ('sdkmanager ','avdmanager ','system-images;android-30','-avd toolbox','runs-on: macos-','runs-on: ubuntu-24.04-arm'):
    if token in job: errors.append(f'closed hosted emulator route token: {token}')
for token in (':toolbox-app:assembleDebug',':toolbox-app:assembleRelease'):
    if token in job: errors.append(f'production rebuild in runtime workflow: {token}')
if errors:
    print('RUNTIME_CI_ROUTE_GUARD = NOT_PROVEN',file=sys.stderr)
    for e in errors: print('FAIL '+e,file=sys.stderr)
    raise SystemExit(1)
print('RUNTIME_CI_ROUTE_GUARD = PASS')
'''
write('verification/scripts/runtime_ci_guard.py',guard)

runtime_yml=r'''name: ToolBox Android 11 ARM64 Runtime R9

on:
  workflow_dispatch:

permissions:
  contents: read
  actions: read

concurrency:
  group: application-runtime-r9-${{ github.ref }}
  cancel-in-progress: false

jobs:
  locate-static:
    runs-on: ubuntu-24.04
    timeout-minutes: 10
    outputs:
      run_id: ${{ steps.locate.outputs.run_id }}
    steps:
      - name: Locate exact successful static candidate
        id: locate
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          PROOF_SHA: ${{ github.sha }}
        shell: bash
        run: |
          set -euo pipefail
          python3 - <<'PY'
          import json,os,urllib.parse,urllib.request
          repo=os.environ['GITHUB_REPOSITORY']; sha=os.environ['PROOF_SHA'].lower(); token=os.environ['GH_TOKEN']
          headers={'Accept':'application/vnd.github+json','Authorization':f'Bearer {token}','X-GitHub-Api-Version':'2022-11-28','User-Agent':'toolbox-runtime-r9'}
          def get(url):
              req=urllib.request.Request(url,headers=headers)
              with urllib.request.urlopen(req,timeout=30) as r:return json.load(r)
          q=urllib.parse.urlencode({'head_sha':sha,'branch':'kernel-foundation-hardening','per_page':100})
          good=[]
          for run in get(f'https://api.github.com/repos/{repo}/actions/runs?{q}').get('workflow_runs',[]):
              if run.get('name')!='ToolBox Application Safe 100' or str(run.get('head_sha','')).lower()!=sha or run.get('conclusion')!='success':continue
              arts=get(f"https://api.github.com/repos/{repo}/actions/runs/{run['id']}/artifacts?per_page=100").get('artifacts',[])
              if any(a.get('name')=='toolbox-android11-arm64-static-candidate' and not a.get('expired',False) for a in arts):good.append(run)
          if not good:raise SystemExit('no successful exact static candidate for current revision')
          selected=max(good,key=lambda r:r['id'])
          with open(os.environ['GITHUB_OUTPUT'],'a') as f:f.write(f"run_id={selected['id']}\n")
          print(f"EXACT_STATIC_RUN_ID={selected['id']}")
          PY

  runtime-r9:
    needs: locate-static
    runs-on: [self-hosted, linux, toolbox-android11-arm64-runtime]
    timeout-minutes: 90
    env:
      PROOF_SHA: ${{ github.sha }}
    steps:
      - name: Checkout exact revision
        uses: actions/checkout@fbc6f3992d24b796d5a048ff273f7fcc4a7b6c09
        with:
          ref: ${{ github.sha }}
          persist-credentials: false
      - name: Download exact static candidate
        uses: actions/download-artifact@634f93cb2916e3fdff6788551b99b062d0335ce0
        with:
          name: toolbox-android11-arm64-static-candidate
          path: candidate
          github-token: ${{ secrets.GITHUB_TOKEN }}
          run-id: ${{ needs.locate-static.outputs.run_id }}
      - name: Restore canonical static evidence and locate exact APKs
        shell: bash
        run: |
          set -euo pipefail
          test -d candidate/evidence
          test -d candidate/candidate-apks
          test ! -e candidate/verification
          rm -rf verification/evidence; mkdir -p verification/evidence
          cp -R candidate/evidence/. verification/evidence/
          DEBUG_APK="$GITHUB_WORKSPACE/candidate/candidate-apks/toolbox-app-debug.apk"
          RELEASE_APK="$GITHUB_WORKSPACE/candidate/candidate-apks/toolbox-app-release.apk"
          TEST_APK="$GITHUB_WORKSPACE/candidate/candidate-apks/toolbox-app-debug-androidTest.apk"
          test -s "$DEBUG_APK"; test -s "$RELEASE_APK"; test -s "$TEST_APK"
          echo "DEBUG_APK=$DEBUG_APK" >> "$GITHUB_ENV"; echo "RELEASE_APK=$RELEASE_APK" >> "$GITHUB_ENV"; echo "TEST_APK=$TEST_APK" >> "$GITHUB_ENV"
      - name: Verify downloaded artifact provenance before runtime
        shell: bash
        run: |
          set -euo pipefail
          python3 - <<'PY'
          import hashlib,json,os
          from pathlib import Path
          proof=os.environ['PROOF_SHA'].lower(); prov=json.loads(Path('verification/evidence/artifacts/provenance.json').read_text())
          if prov.get('gitSha')!=proof:raise SystemExit('provenance revision mismatch')
          for env_name,key in [('DEBUG_APK','debug'),('RELEASE_APK','release'),('TEST_APK','androidTest')]:
              p=Path(os.environ[env_name]); actual=hashlib.sha256(p.read_bytes()).hexdigest(); expected=prov['artifacts'][key]['sha256']
              if actual!=expected:raise SystemExit(f'{key} digest mismatch')
          print('EXACT_STATIC_CANDIDATE_PROVENANCE_PASS')
          PY
      - name: Qualify attached Android 11 ARM64 runtime
        run: GITHUB_SHA="$PROOF_SHA" python3 verification/scripts/runtime_environment_gate.py
      - name: Run instrumentation against exact debug APK
        shell: bash
        run: |
          set -euo pipefail
          mkdir -p verification/evidence/diagnostics
          adb install -r -t "$DEBUG_APK"; adb install -r -t "$TEST_APK"
          adb shell am instrument -w io.toolbox.app.debug.test/androidx.test.runner.AndroidJUnitRunner | tee verification/evidence/diagnostics/instrumentation-debug.txt
          GITHUB_SHA="$PROOF_SHA" python3 verification/scripts/instrumentation_gate.py --report verification/evidence/diagnostics/instrumentation-debug.txt --apk "$DEBUG_APK" --test-apk "$TEST_APK" --provenance verification/evidence/artifacts/provenance.json
      - name: Runtime gate exact debug APK
        run: GITHUB_SHA="$PROOF_SHA" python3 verification/scripts/runtime_gate.py --apk "$DEBUG_APK" --package io.toolbox.app.debug --variant debug
      - name: Runtime gate exact release APK
        run: GITHUB_SHA="$PROOF_SHA" python3 verification/scripts/runtime_gate.py --apk "$RELEASE_APK" --package io.toolbox.app --variant release
      - name: Final ASSET_SAFE_100 closure
        run: GITHUB_SHA="$PROOF_SHA" python3 verification/scripts/asset_final_gate.py
      - name: Cross-domain X1-X5 closure
        run: GITHUB_SHA="$PROOF_SHA" python3 verification/scripts/cross_domain_gate.py
      - name: R1-R9 final APPLICATION_SAFE_100 closure
        run: GITHUB_SHA="$PROOF_SHA" python3 verification/scripts/application_safe_100_gate.py
      - name: Prepare exact final APK distribution set
        shell: bash
        run: |
          set -euo pipefail
          mkdir -p final-apks; cp "$DEBUG_APK" final-apks/ToolBox-debug.apk; cp "$RELEASE_APK" final-apks/ToolBox-release.apk
          python3 - <<'PY'
          import hashlib,json
          from pathlib import Path
          safe=json.loads(Path('verification/evidence/application-safe-100.json').read_text())
          if safe.get('status')!='PASS':raise SystemExit('APPLICATION_SAFE_100 missing')
          ps=[Path('final-apks/ToolBox-debug.apk'),Path('final-apks/ToolBox-release.apk')]
          Path('final-apks/SHA256SUMS').write_text('\n'.join(f'{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}' for p in ps)+'\n')
          Path('final-apks/APPLICATION_SAFE_100.json').write_text(json.dumps(safe,indent=2,sort_keys=True)+'\n')
          PY
      - name: Upload final verified APKs
        uses: actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a
        with:
          name: toolbox-android11-arm64-verified-apks
          path: final-apks/
          if-no-files-found: error
          retention-days: 30
      - name: Cleanup installed candidates
        if: always()
        shell: bash
        run: |
          adb uninstall io.toolbox.app.debug >/dev/null 2>&1 || true
          adb uninstall io.toolbox.app >/dev/null 2>&1 || true
      - name: Publish final application assurance evidence
        if: always()
        uses: actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a
        with:
          name: application-assurance-evidence-final
          path: verification/evidence/
          if-no-files-found: warn
          retention-days: 30
'''
write('.github/workflows/application-runtime-r9.yml',runtime_yml)

static_path=ROOT/'.github/workflows/application-safe-100.yml'
static=static_path.read_text(encoding='utf-8')
marker='\n  application-runtime-r9:\n'
if marker not in static: raise SystemExit('integrated runtime marker missing')
static=static.split(marker,1)[0].rstrip()+'\n'
setup='      - name: Setup exact Temurin JDK\n'
if setup not in static:raise SystemExit('setup marker missing')
static=static.replace(setup,'      - name: Runtime CI route guard\n        run: python3 verification/scripts/runtime_ci_guard.py\n\n'+setup,1)
old='''      - name: Upload static candidate for exact runtime proof
        if: ${{ success() && env.CONTROLLED_APK_BUILD_PASS == '1' }}
        uses: actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a
        with:
          name: toolbox-android11-arm64-static-candidate
          path: |
            verification/candidate-apks/
            verification/evidence/
          if-no-files-found: error
          retention-days: 30
'''
new='''      - name: Prepare canonical runtime bundle
        if: ${{ success() && env.CONTROLLED_APK_BUILD_PASS == '1' }}
        shell: bash
        run: |
          set -euo pipefail
          rm -rf verification/runtime-bundle
          mkdir -p verification/runtime-bundle/candidate-apks verification/runtime-bundle/evidence
          cp -R verification/candidate-apks/. verification/runtime-bundle/candidate-apks/
          cp -R verification/evidence/. verification/runtime-bundle/evidence/
          test -s verification/runtime-bundle/candidate-apks/toolbox-app-debug.apk
          test -s verification/runtime-bundle/candidate-apks/toolbox-app-release.apk
          test -s verification/runtime-bundle/candidate-apks/toolbox-app-debug-androidTest.apk
          test -s verification/runtime-bundle/evidence/artifacts/provenance.json

      - name: Upload static candidate for exact runtime proof
        if: ${{ success() && env.CONTROLLED_APK_BUILD_PASS == '1' }}
        uses: actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a
        with:
          name: toolbox-android11-arm64-static-candidate
          path: verification/runtime-bundle/
          if-no-files-found: error
          retention-days: 30
'''
if old not in static:raise SystemExit('static artifact upload block missing')
static_path.write_text(static.replace(old,new,1),encoding='utf-8')

final_path=ROOT/'verification/scripts/application_safe_100_gate.py'
final=final_path.read_text(encoding='utf-8')
oldq="""    check('r9-runtime-tool-qualification',
          runtime_tools.get('status')=='PASS'
          and {'runtime-host-arm64','runtime-emulator-version-known',
               'runtime-adb-version-known','runtime-system-image-revision-known'}.issubset(tool_checks),
          f'passed={sorted(tool_checks)}')
"""
newq="""    check('r9-runtime-tool-qualification',
          runtime_tools.get('status')=='PASS'
          and {'runtime-device-connected','runtime-device-api30','runtime-device-android11',
               'runtime-device-arm64','runtime-adb-version-known','runtime-transport-qualified'}.issubset(tool_checks),
          f'passed={sorted(tool_checks)}')
"""
if oldq not in final:raise SystemExit('R9 emulator-specific qualification block missing')
final_path.write_text(final.replace(oldq,newq,1),encoding='utf-8')

doc='''# Android 11 ARM64 Runtime Proof Route

## Canonical route
Runtime proof uses an actually attached ADB target and requires Android release 11, API 30, primary ABI `arm64-v8a`, boot completed, exact source revision, and exact APK digests from static GitHub Actions provenance. The runtime job never rebuilds production APKs.

The canonical workflow is `.github/workflows/application-runtime-r9.yml`. Its runtime job requires the self-hosted label `toolbox-android11-arm64-runtime`.

## Canonical artifact layout
Static CI stages exactly `verification/runtime-bundle/` with `candidate-apks/` and `evidence/`. After download to `candidate`, valid paths are `candidate/candidate-apks/` and `candidate/evidence/`. `candidate/verification/...` is invalid.

## Hosted routes closed by actual evidence
- Ubuntu x64 + ARM64 guest: Emulator 37.1.11 rejects ARM64 guest on x86_64 host. Run 33431096853.
- macOS ARM64 + ARM64 guest: startup fails at `HV_UNSUPPORTED`; nested virtualization is unavailable. Run 33431361441.
- Ubuntu ARM64 (`ubuntu-24.04-arm`): host is AArch64 but `sdkmanager` exposes no Linux ARM64 `emulator` package and `/dev/kvm` is absent. Run 33456579283.

These are infrastructure failures, not asset failures. Do not retry a closed route by changing AVD paths, graphics flags, or runner labels. Reopen it only after the missing infrastructure capability is independently proven.
'''
write('RUNTIME_ANDROID11_ARM64.md',doc)

agents=ROOT/'AGENTS.md'; text=agents.read_text(encoding='utf-8')
section='''\n\n## 22. Jalur Runtime Android 11 ARM64\n\nRuntime proof wajib mengikuti `RUNTIME_ANDROID11_ARM64.md` dan `.github/workflows/application-runtime-r9.yml`. Jalur emulator hosted yang sudah terbukti tidak valid dilarang diulang tanpa bukti perubahan kemampuan infrastruktur. Runtime tidak boleh membangun ulang APK production dan hanya boleh menguji exact static candidate yang digest/provenance-nya telah dibuktikan. Target wajib lolos `verification/scripts/runtime_environment_gate.py`. Layout artifact runtime dikunci ke `candidate/candidate-apks/` dan `candidate/evidence/`; `candidate/verification/...` adalah jalur salah. `verification/scripts/runtime_ci_guard.py` tidak boleh dilemahkan hanya agar CI hijau.\n'''
if '## 22. Jalur Runtime Android 11 ARM64' not in text:agents.write_text(text.rstrip()+section,encoding='utf-8')

for rel in ('.github/workflows/android11-arm64-runtime-host-probe.yml','.github/workflows/runtime-route-maintenance.yml'):
    p=ROOT/rel
    if p.exists():p.unlink()

kernel=ROOT/'.github/workflows/kernel-ci.yml'; k=kernel.read_text(encoding='utf-8')
k=k.replace('permissions:\n  contents: write\n','permissions:\n  contents: read\n',1)
km='\n  runtime-route-maintenance:\n'
if km in k:k=k.split(km,1)[0].rstrip()+'\n'
kernel.write_text(k,encoding='utf-8')
Path(__file__).unlink()

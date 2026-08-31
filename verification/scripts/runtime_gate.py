#!/usr/bin/env python3
"""Runtime proof for the exact ToolBox APK on Android 11 ARM64."""
from __future__ import annotations
import argparse, hashlib, json, os, re, subprocess, sys, time
from dataclasses import asdict, dataclass
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
SCOPE=json.loads((ROOT/'verification/application_scope.json').read_text(encoding='utf-8'))
EVIDENCE=ROOT/'verification/evidence'; DIAG=EVIDENCE/'diagnostics'; ACTIVITY='io.toolbox.app.MainActivity'
@dataclass
class Check: name:str; passed:bool; detail:str
def run(*args,check=True,timeout=60,binary=False):
    c=subprocess.run(list(args),stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=timeout,check=False)
    if check and c.returncode!=0: raise RuntimeError(f"command failed ({c.returncode}): {' '.join(args)}\nstdout={c.stdout.decode(errors='replace')}\nstderr={c.stderr.decode(errors='replace')}")
    return c.stdout if binary else c.stdout.decode(errors='replace').strip()
def adb(*args,check=True,timeout=60): return run('adb',*args,check=check,timeout=timeout)
def sha256(path:Path):
    h=hashlib.sha256()
    with path.open('rb') as f:
        for b in iter(lambda:f.read(1024*1024),b''): h.update(b)
    return h.hexdigest()
def git_sha():
    v=os.environ.get('GITHUB_SHA','').strip().lower()
    if re.fullmatch(r'[0-9a-f]{40}',v): return v
    try:
        v=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True,stderr=subprocess.DEVNULL).strip().lower()
        return v if re.fullmatch(r'[0-9a-f]{40}',v) else None
    except Exception:return None
def pidof(pkg):
    out=adb('shell','pidof',pkg,check=False).strip(); first=out.split()[0] if out else ''
    return int(first) if first.isdigit() else None
def wait_pid(pkg,present,timeout=10):
    end=time.time()+timeout; last=None
    while time.time()<end:
        last=pidof(pkg)
        if (last is not None)==present:return last
        time.sleep(.25)
    return last
def start(pkg):
    out=adb('shell','am','start','-W','-n',f'{pkg}/{ACTIVITY}','-a','android.intent.action.MAIN','-c','android.intent.category.LAUNCHER')
    m=re.search(r'TotalTime:\s*(\d+)',out); return (int(m.group(1)) if m else None,out)
def parse_pss(text):
    m=re.search(r'TOTAL PSS:\s*(\d+)',text)
    if m:return int(m.group(1))
    for line in text.splitlines():
        m=re.match(r'\s*TOTAL\s+(\d+)\b',line)
        if m:return int(m.group(1))
    return None
def main():
    p=argparse.ArgumentParser(); p.add_argument('--apk',required=True,type=Path); p.add_argument('--package',required=True); p.add_argument('--variant',required=True,choices=('debug','release')); a=p.parse_args()
    EVIDENCE.mkdir(parents=True,exist_ok=True); DIAG.mkdir(parents=True,exist_ok=True); checks=[]; obs={}; pids=set(); apk=a.apk.resolve(); source_sha=git_sha()
    def check(n,c,d):checks.append(Check(n,bool(c),d))
    def witness(label):
        adb('shell','am','start','-W','-n',f'{a.package}/{ACTIVITY}',timeout=60); time.sleep(.8)
        remote=f'/sdcard/toolbox-{a.variant}-{label}.xml'; adb('shell','uiautomator','dump',remote,timeout=30); ui=adb('shell','cat',remote,timeout=30)
        (DIAG/f'ui-{a.variant}-{label}.xml').write_text(ui,encoding='utf-8')
        check(f'ui-running-{label}','RUNNING: ToolBox' in ui,f'{label} RUNNING semantic')
        check(f'ui-accessibility-{label}','ToolBox status RUNNING: ToolBox' in ui,f'{label} accessibility semantic')
        shot=run('adb','exec-out','screencap','-p',binary=True,timeout=30); sp=DIAG/f'screen-{a.variant}-{label}.png'; sp.write_bytes(shot); check(f'screenshot-{label}',sp.stat().st_size>1024,f'bytes={sp.stat().st_size}')
    try:
        check('runtime-source-sha-known',source_sha is not None,f'gitSha={source_sha}'); check('runtime-apk-present',apk.is_file() and apk.stat().st_size>0,str(apk)); obs['gitSha']=source_sha; obs['apkSha256']=sha256(apk) if apk.is_file() else None
        adb('wait-for-device',timeout=120); api=adb('shell','getprop','ro.build.version.sdk'); abi=adb('shell','getprop','ro.product.cpu.abi'); abilist=adb('shell','getprop','ro.product.cpu.abilist'); fp=adb('shell','getprop','ro.build.fingerprint')
        check('runtime-api30',api=='30',f'api={api}'); check('runtime-primary-arm64',abi=='arm64-v8a',f'abi={abi}, abilist={abilist}'); obs['device']={'api':api,'abi':abi,'abiList':abilist,'fingerprint':fp}
        adb('uninstall',a.package,check=False,timeout=30); install=adb('install','-r','-t',str(apk),timeout=180); check('apk-clean-install','Success' in install,install); reinstall=adb('install','-r','-t',str(apk),timeout=180); check('apk-same-version-reinstall','Success' in reinstall,reinstall)
        adb('logcat','-c'); adb('shell','am','force-stop',a.package,check=False); wait_pid(a.package,False,5); cold,cold_out=start(a.package); first=wait_pid(a.package,True,15); startup_budget=int(SCOPE['budgets']['coldStartupMs'])
        check('cold-start-time-observed',cold is not None,cold_out); check('cold-start-budget',cold is not None and cold<=startup_budget,f'coldMs={cold}, budgetMs={startup_budget}'); check('process-alive-after-start',first is not None,f'pid={first}'); obs['coldStartMs']=cold
        if first:pids.add(first)
        base_mem=adb('shell','dumpsys','meminfo',a.package,timeout=30); base_pss=parse_pss(base_mem); (DIAG/f'meminfo-{a.variant}-baseline.txt').write_text(base_mem,encoding='utf-8'); check('baseline-pss-observed',base_pss is not None,f'pssKb={base_pss}'); obs['baselinePssKb']=base_pss
        witness('baseline')
        ext=adb('shell','am','start','-W','-n',f'{a.package}/{ACTIVITY}','--es','unexpected.external.payload','../../invalid:payload?%25%00'); check('unexpected-external-payload-survives','Error:' not in ext and pidof(a.package) is not None,ext)
        adb('shell','settings','put','system','accelerometer_rotation','0'); adb('shell','settings','put','system','user_rotation','1'); witness('landscape'); adb('shell','settings','put','system','user_rotation','0'); witness('portrait')
        adb('shell','settings','put','system','font_scale','1.3'); witness('font-1_3'); adb('shell','settings','put','system','font_scale','2.0'); witness('font-2_0'); adb('shell','settings','put','system','font_scale','1.0')
        adb('shell','cmd','uimode','night','yes',check=False); witness('night'); adb('shell','cmd','uimode','night','no',check=False); witness('day')
        adb('shell','wm','size','480x800'); adb('shell','wm','density','240'); witness('compact-screen'); adb('shell','wm','size','reset'); adb('shell','wm','density','reset')
        adb('shell','dumpsys','gfxinfo',a.package,'reset',check=False); cycles=int(SCOPE['budgets']['runtimeCycles'])
        for _ in range(cycles):
            adb('shell','input','keyevent','KEYCODE_HOME',timeout=15); adb('shell','monkey','-p',a.package,'-c','android.intent.category.LAUNCHER','1',timeout=30); cur=wait_pid(a.package,True,5)
            if cur:pids.add(cur)
        after_mem=adb('shell','dumpsys','meminfo',a.package,timeout=30); after_pss=parse_pss(after_mem); (DIAG/f'meminfo-{a.variant}-after-cycles.txt').write_text(after_mem,encoding='utf-8'); growth=None if base_pss is None or after_pss is None else after_pss-base_pss
        check('post-cycle-pss-observed',after_pss is not None,f'pssKb={after_pss}'); check('pss-growth-budget',growth is not None and growth<=int(SCOPE['budgets']['runtimePssGrowthKb']),f'growthKb={growth}'); check('pss-ceiling',after_pss is not None and after_pss<=int(SCOPE['budgets']['runtimePssCeilingKb']),f'pssKb={after_pss}'); obs.update({'afterCyclesPssKb':after_pss,'pssGrowthKb':growth,'runtimeCycles':cycles})
        gfx=adb('shell','dumpsys','gfxinfo',a.package,timeout=30); (DIAG/f'gfxinfo-{a.variant}.txt').write_text(gfx,encoding='utf-8'); jm=re.search(r'Janky frames:\s*\d+\s*\(([0-9.]+)%\)',gfx); jank=float(jm.group(1)) if jm else None; check('janky-frame-percent-observed',jank is not None,f'jankyPercent={jank}'); check('janky-frame-budget',jank is not None and jank<=float(SCOPE['budgets']['jankyFramePercent']),f'actual={jank}, max={SCOPE["budgets"]["jankyFramePercent"]}'); obs['jankyFramePercent']=jank
        old=pidof(a.package); adb('shell','am','force-stop',a.package); dead=wait_pid(a.package,False,10); check('process-death-observed',dead is None,f'pidAfterForceStop={dead}'); restart,restart_out=start(a.package); new=wait_pid(a.package,True,15); check('process-restart-alive',new is not None,restart_out); check('process-restart-is-new-process',old is None or new!=old,f'oldPid={old}, newPid={new}'); check('restart-budget',restart is not None and restart<=startup_budget,f'restartMs={restart}'); obs['restartMs']=restart
        if old:pids.add(old)
        if new:pids.add(new)
        log=adb('logcat','-v','threadtime','-d',timeout=60); (DIAG/f'logcat-{a.variant}.txt').write_text(log,encoding='utf-8'); faults=[]; lines=log.splitlines()
        for i,line in enumerate(lines):
            ctx='\n'.join(lines[i:i+12])
            if 'FATAL EXCEPTION' in line and a.package in ctx:faults.append(ctx)
            if f'ANR in {a.package}' in line:faults.append(ctx)
            if 'StrictMode' in line and any(re.search(rf'\s{pid}\s',line) for pid in pids):faults.append(ctx)
        check('no-runtime-fatal-anr-strictmode',not faults,f'faultCount={len(faults)}'); obs['faultCount']=len(faults); obs['knownPids']=sorted(pids)
    except Exception as exc: check('runtime-gate-execution',False,str(exc))
    finally:
        for cmd in [('settings','put','system','font_scale','1.0'),('settings','put','system','accelerometer_rotation','1'),('wm','size','reset'),('wm','density','reset'),('cmd','uimode','night','no')]:
            try:adb('shell',*cmd,check=False,timeout=15)
            except Exception:pass
    failed=[x for x in checks if not x.passed]; payload={'schemaVersion':2,'gate':'ANDROID_RUNTIME_GATE','variant':a.variant,'package':a.package,'apk':str(apk),'status':'PASS' if not failed else 'NOT_PROVEN','gitSha':source_sha,'apkSha256':obs.get('apkSha256'),'checks':[asdict(x) for x in checks],'failed':[x.name for x in failed],'observations':obs}; (EVIDENCE/f'runtime-{a.variant}.json').write_text(json.dumps(payload,indent=2,sort_keys=True)+'\n',encoding='utf-8')
    if failed:
        print(f'ANDROID_RUNTIME_GATE[{a.variant}] = NOT_PROVEN',file=sys.stderr)
        for x in failed: print(f'FAIL {x.name}: {x.detail}',file=sys.stderr)
        return 1
    print(f'ANDROID_RUNTIME_GATE[{a.variant}] = PASS'); return 0
if __name__=='__main__': raise SystemExit(main())

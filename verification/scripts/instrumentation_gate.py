#!/usr/bin/env python3
"""Bind Android instrumentation output to the exact debug/test APKs under proof."""
from __future__ import annotations

import argparse, hashlib, json, os, re, subprocess, sys
from dataclasses import asdict, dataclass
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
EVIDENCE=ROOT/'verification/evidence'
EXPECTED_TESTS=(
    'runtimeIsExactlyAndroid11Arm64',
    'kernelStartsAndHealthSurfaceIsVisible',
    'recreationRestoresStateWithoutDuplicateFailure',
    'repeatedLifecycleRecreationRemainsOperational',
    'unexpectedExternalPayloadDoesNotChangeBootstrapSemantics',
    'backgroundAndResumeKeepsKernelOperational',
)

@dataclass
class Check:
    name:str
    passed:bool
    detail:str

def sha256(path:Path)->str:
    h=hashlib.sha256()
    with path.open('rb') as f:
        for b in iter(lambda:f.read(1024*1024),b''):
            h.update(b)
    return h.hexdigest()

def git_sha()->str|None:
    v=os.environ.get('GITHUB_SHA','').strip().lower()
    if re.fullmatch(r'[0-9a-f]{40}',v):
        return v
    try:
        v=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True,
                                  stderr=subprocess.DEVNULL).strip().lower()
        return v if re.fullmatch(r'[0-9a-f]{40}',v) else None
    except Exception:
        return None

def main()->int:
    p=argparse.ArgumentParser()
    p.add_argument('--report',required=True,type=Path)
    p.add_argument('--apk',required=True,type=Path)
    p.add_argument('--test-apk',required=True,type=Path)
    p.add_argument('--provenance',required=True,type=Path)
    a=p.parse_args()

    checks:list[Check]=[]
    def check(name,condition,detail):
        checks.append(Check(name,bool(condition),detail))

    report=a.report.resolve()
    apk=a.apk.resolve()
    test_apk=a.test_apk.resolve()
    provenance_path=a.provenance.resolve()
    source=git_sha()

    check('instrumentation-source-sha-known',bool(source),f'gitSha={source}')
    check('instrumentation-report-present',report.is_file(),str(report))
    check('instrumentation-apk-present',apk.is_file(),str(apk))
    check('instrumentation-test-apk-present',test_apk.is_file(),str(test_apk))
    check('instrumentation-provenance-present',provenance_path.is_file(),str(provenance_path))

    text=report.read_text(encoding='utf-8',errors='replace') if report.is_file() else ''
    observed=sorted({name for name in EXPECTED_TESTS if name in text})
    m=re.search(r'OK \((\d+) tests?\)',text)
    count=int(m.group(1)) if m else None
    run_ok=(m is not None and 'FAILURES!!!' not in text
            and 'INSTRUMENTATION_FAILED' not in text
            and 'Process crashed.' not in text)
    check('instrumentation-run-ok',run_ok,f'tests={count}')
    check('instrumentation-test-count',count==len(EXPECTED_TESTS),
          f'actual={count}, expected={len(EXPECTED_TESTS)}')
    check('instrumentation-required-test-universe',
          observed==sorted(EXPECTED_TESTS),f'observed={observed}')

    provenance={}
    if provenance_path.is_file():
        try:
            provenance=json.loads(provenance_path.read_text(encoding='utf-8'))
        except Exception as exc:
            check('instrumentation-provenance-readable',False,str(exc))
    if provenance:
        check('instrumentation-provenance-source',
              provenance.get('gitSha')==source,
              f"provenanceSha={provenance.get('gitSha')}, sourceSha={source}")
        expected_debug=provenance.get('artifacts',{}).get('debug',{}).get('sha256')
        expected_test=provenance.get('artifacts',{}).get('androidTest',{}).get('sha256')
        actual_debug=sha256(apk) if apk.is_file() else None
        actual_test=sha256(test_apk) if test_apk.is_file() else None
        check('instrumentation-debug-apk-exact',
              bool(expected_debug) and actual_debug==expected_debug,
              f'actual={actual_debug}, expected={expected_debug}')
        check('instrumentation-test-apk-exact',
              bool(expected_test) and actual_test==expected_test,
              f'actual={actual_test}, expected={expected_test}')

    failed=[x for x in checks if not x.passed]
    EVIDENCE.mkdir(parents=True,exist_ok=True)
    payload={
        'schemaVersion':2,
        'gate':'ANDROID_INSTRUMENTATION_GATE',
        'variant':'debug',
        'status':'PASS' if not failed else 'NOT_PROVEN',
        'gitSha':source,
        'debugApkSha256':sha256(apk) if apk.is_file() else None,
        'testApkSha256':sha256(test_apk) if test_apk.is_file() else None,
        'tests':count,
        'expectedTests':list(EXPECTED_TESTS),
        'observedTests':observed,
        'checks':[asdict(x) for x in checks],
        'failed':[x.name for x in failed]
    }
    (EVIDENCE/'instrumentation-debug.json').write_text(
        json.dumps(payload,indent=2,sort_keys=True)+'\n',encoding='utf-8')
    if failed:
        print('ANDROID_INSTRUMENTATION_GATE = NOT_PROVEN',file=sys.stderr)
        for x in failed:
            print(f'FAIL {x.name}: {x.detail}',file=sys.stderr)
        return 1
    print('ANDROID_INSTRUMENTATION_GATE = PASS')
    return 0

if __name__=='__main__':
    raise SystemExit(main())

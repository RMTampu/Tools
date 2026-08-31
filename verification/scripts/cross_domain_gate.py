#!/usr/bin/env python3
"""Close the declared ToolBox cross-domain scenarios X1-X5."""
from __future__ import annotations

import json, os, sys
from dataclasses import asdict, dataclass
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
E=ROOT/'verification/evidence'
C=json.loads((ROOT/'verification/application_contracts.json').read_text(encoding='utf-8'))

@dataclass
class Check:
    name:str
    passed:bool
    detail:str

def load(name:str)->dict:
    p=E/name
    if not p.is_file():
        return {}
    try:
        return json.loads(p.read_text(encoding='utf-8'))
    except Exception:
        return {}

def passed_checks(item:dict)->set[str]:
    return {x.get('name') for x in item.get('checks',[]) if x.get('passed')}

def main()->int:
    sha=os.environ.get('GITHUB_SHA','').strip().lower()
    checks:list[Check]=[]
    def check(name,condition,detail):
        checks.append(Check(name,bool(condition),detail))

    evidence_names=[
        'application-prebuild.json','apk-release.json','runtime-debug.json',
        'runtime-release.json','signature-release.json','instrumentation-debug.json',
        'reproducibility.json'
    ]
    ev={n:load(n) for n in evidence_names}
    for name,item in ev.items():
        check(f'cross-{name}-present',bool(item),name)
        check(f'cross-{name}-pass',item.get('status')=='PASS',
              f"status={item.get('status')}")
        es=item.get('gitSha')
        if es is not None:
            check(f'cross-{name}-fresh',es==sha,
                  f'evidenceSha={es}, sourceSha={sha}')

    rd=passed_checks(ev['runtime-debug.json'])
    rr=passed_checks(ev['runtime-release.json'])
    runtime=rd & rr
    apk=passed_checks(ev['apk-release.json'])
    observed=set(ev['instrumentation-debug.json'].get('observedTests',[]))

    x1=(
        'recreationRestoresStateWithoutDuplicateFailure' in observed
        and 'ui-running-baseline' in runtime
    )
    x2=(
        'unexpectedExternalPayloadDoesNotChangeBootstrapSemantics' in observed
        and 'unexpected-external-payload-survives' in runtime
    )
    x3=(
        'backgroundAndResumeKeepsKernelOperational' in observed
        and all(x in runtime for x in (
            'pss-growth-budget','pss-ceiling',
            'janky-frame-budget','no-runtime-fatal-anr-strictmode'
        ))
        and all(x in runtime for x in (
            'ui-running-landscape','ui-running-portrait',
            'ui-running-font-1_3','ui-running-font-2_0',
            'ui-running-night','ui-running-day','ui-running-compact-screen'
        ))
    )
    x4=all(x in runtime for x in (
        'process-death-observed','process-restart-alive',
        'process-restart-is-new-process','restart-budget'
    ))
    x5=(
        'r7-native-archive-closure' in apk
        and 'runtime-primary-arm64' in runtime
    )

    scenarios={'X1':x1,'X2':x2,'X3':x3,'X4':x4,'X5':x5}
    required={x['id'] for x in C['crossDomainScenarios']}
    check('cross-scenario-universe',set(scenarios)==required,
          f'actual={sorted(scenarios)}, expected={sorted(required)}')
    for sid in sorted(required):
        check(f'cross-domain-{sid.lower()}',scenarios.get(sid) is True,sid)

    failed=[x for x in checks if not x.passed]
    payload={
        'schemaVersion':2,
        'gate':'CROSS_DOMAIN_GATE',
        'status':'PASS' if not failed else 'NOT_PROVEN',
        'gitSha':sha,
        'requiredScenarios':len(required),
        'provenScenarios':sum(1 for s in required if scenarios.get(s)),
        'scenarioResults':scenarios,
        'unknown':0 if not failed else len(failed),
        'missing':0 if not failed else sum(1 for x in failed if x.name.endswith('-present')),
        'unproven':0 if not failed else len(failed),
        'skipped':0,
        'indeterminate':0,
        'staleEvidence':0 if not failed else sum(1 for x in failed if x.name.endswith('-fresh')),
        'faultEscape':0,
        'checks':[asdict(x) for x in checks],
        'failed':[x.name for x in failed]
    }
    E.mkdir(parents=True,exist_ok=True)
    (E/'cross-domain.json').write_text(
        json.dumps(payload,indent=2,sort_keys=True)+'\n',encoding='utf-8')
    if failed:
        print('CROSS_DOMAIN_GATE = NOT_PROVEN',file=sys.stderr)
        for x in failed:
            print(f'FAIL {x.name}: {x.detail}',file=sys.stderr)
        return 1
    print('CROSS_DOMAIN_GATE = PASS')
    return 0

if __name__=='__main__':
    raise SystemExit(main())

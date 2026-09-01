#!/usr/bin/env python3
"""Final fail-closed R1-R9 closure for the locked ToolBox bootstrap scope."""
from __future__ import annotations

import json, os, re, sys
from dataclasses import asdict, dataclass
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
E=ROOT/'verification/evidence'
S=json.loads((ROOT/'verification/application_scope.json').read_text(encoding='utf-8'))
C=json.loads((ROOT/'verification/application_contracts.json').read_text(encoding='utf-8'))
A=json.loads((ROOT/'verification/asset_contracts.json').read_text(encoding='utf-8'))

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

    check('final-source-sha-format',bool(re.fullmatch(r'[0-9a-f]{40}',sha)),sha)

    required_files=[
        'prebuild.json','asset-prebuild.json','dependency-trust-current.json',
        'signing-identity.json','prebuild-mutations.json','application-prebuild.json',
        'apk-debug.json','apk-release.json','signature-debug.json','signature-release.json',
        'reproducibility.json','instrumentation-debug.json','runtime-tools.json',
        'runtime-debug.json','runtime-release.json','asset-mutations.json',
        'asset-safe-100.json','cross-domain.json','artifacts/provenance.json'
    ]
    ev={name:load(name) for name in required_files}

    for name,item in ev.items():
        check(f'final-{name}-present',bool(item),name)
        if name!='artifacts/provenance.json':
            check(f'final-{name}-pass',item.get('status')=='PASS',
                  f"status={item.get('status')}")
        evidence_sha=item.get('gitSha')
        check(f'final-{name}-revision-bound',evidence_sha==sha,
              f'evidenceSha={evidence_sha}, sourceSha={sha}')

    provenance=ev['artifacts/provenance.json']
    artifacts=provenance.get('artifacts',{})
    for variant in ('debug','release'):
        expected=artifacts.get(variant,{}).get('sha256')
        apk_ev=ev[f'apk-{variant}.json']
        sig_ev=ev[f'signature-{variant}.json']
        rt_ev=ev[f'runtime-{variant}.json']
        check(f'final-{variant}-provenance-digest-known',bool(expected),f'expected={expected}')
        check(f'final-{variant}-static-digest',
              apk_ev.get('details',{}).get('sha256')==expected,
              f"static={apk_ev.get('details',{}).get('sha256')}, expected={expected}")
        check(f'final-{variant}-signature-digest',
              sig_ev.get('apkSha256')==expected,
              f"signature={sig_ev.get('apkSha256')}, expected={expected}")
        check(f'final-{variant}-runtime-digest',
              rt_ev.get('apkSha256')==expected,
              f"runtime={rt_ev.get('apkSha256')}, expected={expected}")

    inst=ev['instrumentation-debug.json']
    expected_test=artifacts.get('androidTest',{}).get('sha256')
    check('final-instrumentation-debug-digest',
          inst.get('debugApkSha256')==artifacts.get('debug',{}).get('sha256'),
          f"instrumentation={inst.get('debugApkSha256')}")
    check('final-instrumentation-test-digest',
          bool(expected_test) and inst.get('testApkSha256')==expected_test,
          f"instrumentation={inst.get('testApkSha256')}, expected={expected_test}")

    repro=ev['reproducibility.json']
    check('final-release-reproducible',
          repro.get('candidateSha256')==artifacts.get('release',{}).get('sha256')
          and repro.get('candidateSha256')==repro.get('secondCleanSha256'),
          f"candidate={repro.get('candidateSha256')}, second={repro.get('secondCleanSha256')}")

    materials=provenance.get('materials',{})
    check('final-release-mapping-bound',
          bool(materials.get('releaseMapping',{}).get('sha256')),
          str(materials.get('releaseMapping')))
    check('final-dependency-inventory-bound',
          bool(materials.get('releaseDependencyInventory',{}).get('sha256')),
          str(materials.get('releaseDependencyInventory')))

    proof_ok={
        'unit':ev['application-prebuild.json'].get('status')=='PASS',
        'instrumentation':inst.get('status')=='PASS',
        'runtime':ev['runtime-debug.json'].get('status')=='PASS' and ev['runtime-release.json'].get('status')=='PASS',
        'prebuild':ev['application-prebuild.json'].get('status')=='PASS',
        'build':bool(provenance),
        'artifact':ev['apk-debug.json'].get('status')=='PASS' and ev['apk-release.json'].get('status')=='PASS',
        'provenance':provenance.get('gitSha')==sha,
        'reproducibility':repro.get('status')=='PASS',
        'signing-preflight':ev['signing-identity.json'].get('status')=='PASS',
        'signature':ev['signature-release.json'].get('status')=='PASS',
        'asset-prebuild':ev['asset-prebuild.json'].get('status')=='PASS',
        'mutation':ev['prebuild-mutations.json'].get('status')=='PASS' and ev['asset-mutations.json'].get('status')=='PASS',
    }
    requirement_results={}
    for req in C.get('requirements',[]):
        impl=req.get('implementation',[])
        proofs=req.get('proof',[])
        paths_ok=bool(impl) and all((ROOT/p).exists() for p in impl)
        proof_rows={p:proof_ok.get(p,False) for p in proofs}
        ok=bool(req.get('behavior')) and paths_ok and bool(proofs) and all(proof_rows.values())
        requirement_results[req['id']]={'passed':ok,'proof':proof_rows,'implementation':impl}
        check(f"requirement-{req['id'].lower()}-traceability",ok,
              json.dumps(requirement_results[req['id']],sort_keys=True))

    rd=passed_checks(ev['runtime-debug.json'])
    rr=passed_checks(ev['runtime-release.json'])
    runtime_both=rd & rr
    apk_debug=passed_checks(ev['apk-debug.json'])
    apk_release=passed_checks(ev['apk-release.json'])
    inst_tests=set(inst.get('observedTests',[]))
    pre_checks=passed_checks(ev['prebuild.json'])

    required_ui={
        'ui-running-baseline','ui-accessibility-baseline',
        'ui-running-landscape','ui-running-portrait',
        'ui-running-font-1_3','ui-running-font-2_0',
        'ui-running-night','ui-running-day','ui-running-compact-screen',
        'cold-start-budget','janky-frame-budget','no-runtime-fatal-anr-strictmode'
    }

    domains={
        'R1':(
            ev['application-prebuild.json'].get('status')=='PASS'
            and 'unexpectedExternalPayloadDoesNotChangeBootstrapSemantics' in inst_tests
            and 'unexpected-external-payload-survives' in runtime_both
        ),
        'R2':(
            ev['application-prebuild.json'].get('status')=='PASS'
            and all(x in runtime_both for x in (
                'pss-growth-budget','pss-ceiling','janky-frame-budget','no-runtime-fatal-anr-strictmode'))
        ),
        'R3':(
            {'recreationRestoresStateWithoutDuplicateFailure',
             'repeatedLifecycleRecreationRemainsOperational',
             'backgroundAndResumeKeepsKernelOperational'}.issubset(inst_tests)
            and all(x in runtime_both for x in (
                'process-death-observed','process-restart-alive',
                'process-restart-is-new-process','restart-budget'))
        ),
        'R4':(
            S['domainScope']['R4']['status']=='CLOSED_EMPTY'
            and C['domainClosure']['R4']['status']=='CLOSED_EMPTY'
            and ev['prebuild.json'].get('status')=='PASS'
        ),
        'R5':(
            {'closed-empty-permission-universe','exported-component-universe'}.issubset(pre_checks)
            and 'unexpected-external-payload-survives' in runtime_both
            and 'final-permission-universe' in apk_release
            and 'final-exported-component-universe' in apk_release
        ),
        'R6':(
            ev['dependency-trust-current.json'].get('status')=='PASS'
            and ev['signing-identity.json'].get('status')=='PASS'
            and repro.get('status')=='PASS'
            and ev['apk-debug.json'].get('status')=='PASS'
            and ev['apk-release.json'].get('status')=='PASS'
            and ev['signature-debug.json'].get('status')=='PASS'
            and ev['signature-release.json'].get('status')=='PASS'
            and 'apk-clean-install' in runtime_both
            and 'apk-same-version-reinstall' in runtime_both
            and provenance.get('gitSha')==sha
        ),
        'R7':(
            S['domainScope']['R7']['status']=='CLOSED_EMPTY'
            and C['domainClosure']['R7']['status']=='CLOSED_EMPTY'
            and 'r7-native-archive-closure' in apk_debug
            and 'r7-native-archive-closure' in apk_release
        ),
        'R8':(
            required_ui.issubset(runtime_both)
            and 'kernelStartsAndHealthSurfaceIsVisible' in inst_tests
        ),
    }
    for domain in [f'R{i}' for i in range(1,9)]:
        check(f'app-safe-{domain.lower()}',domains[domain],domain)

    fault_rows=[]
    for owner,closure in C.get('domainClosure',{}).items():
        for fault in closure.get('activeFaults',[]):
            fault_rows.append({'owner':owner,'fault':fault})
    for fault in A.get('activeFaultClasses',[]):
        fault_rows.append({'owner':'ASSET','fault':fault})
    fault_keys={(x['owner'],x['fault']) for x in fault_rows}
    check('r9-fault-universe-nonempty',bool(fault_rows),f'faultClasses={len(fault_rows)}')
    check('r9-fault-universe-no-duplicate-owner-row',
          len(fault_keys)==len(fault_rows),f'rows={len(fault_rows)}, unique={len(fault_keys)}')

    asset_ok=ev['asset-safe-100.json'].get('status')=='PASS'
    owner_challenge={**domains,'ASSET':asset_ok}
    fault_challenge=[{**row,'challenged':bool(owner_challenge.get(row['owner']))} for row in fault_rows]
    challenged=sum(1 for row in fault_challenge if row['challenged'])
    check('r9-fault-challenge-coverage',challenged==len(fault_challenge),
          f'challenged={challenged}, total={len(fault_challenge)}')

    mutation_items=[
        ev['prebuild-mutations.json'],ev['asset-mutations.json'],
        ev['asset-safe-100.json'],ev['cross-domain.json']
    ]
    check('r9-mutation-fault-escape-zero',
          all(int(x.get('faultEscape') or 0)==0 for x in mutation_items),
          str([x.get('faultEscape') for x in mutation_items]))
    check('r9-postbuild-baseline-preserved',
          ev['asset-mutations.json'].get('baselineEvidencePreserved') is True,
          str(ev['asset-mutations.json'].get('baselineEvidencePreserved')))

    check('r9-cross-domain-closure',
          ev['cross-domain.json'].get('status')=='PASS'
          and ev['cross-domain.json'].get('provenScenarios')==ev['cross-domain.json'].get('requiredScenarios'),
          str(ev['cross-domain.json'].get('scenarioResults')))
    check('r9-configuration-universe-closure',required_ui.issubset(runtime_both),
          f'missing={sorted(required_ui-runtime_both)}')

    runtime_tools=ev['runtime-tools.json']
    tool_checks=passed_checks(runtime_tools)
    oracle_diversity=(
        inst.get('status')=='PASS'
        and ev['runtime-debug.json'].get('status')=='PASS'
        and ev['apk-release.json'].get('status')=='PASS'
        and ev['signature-release.json'].get('status')=='PASS'
        and ev['asset-mutations.json'].get('status')=='PASS'
    )
    check('r9-independent-oracle-diversity',oracle_diversity,
          'instrumentation + adb runtime + apk parser + apksigner + mutation')
    check('r9-runtime-tool-qualification',
          runtime_tools.get('status')=='PASS'
          and {'runtime-device-connected','runtime-device-api30','runtime-device-android11',
               'runtime-device-arm64','runtime-adb-version-known','runtime-transport-qualified'}.issubset(tool_checks),
          f'passed={sorted(tool_checks)}')
    check('r9-ci-tool-policy-qualified',
          {'github-actions-commit-pinned','no-ci-continue-on-error-bypass'}.issubset(pre_checks)
          and ev['dependency-trust-current.json'].get('status')=='PASS',
          'pinned Actions + dependency trust')

    counter_names=('unknown','missing','unproven','skipped','indeterminate','staleEvidence','faultEscape')
    aggregate={name:0 for name in counter_names}
    for item in ev.values():
        for name in counter_names:
            aggregate[name]+=int(item.get(name) or 0)
    check('r9-evidence-counters-zero',all(v==0 for v in aggregate.values()),str(aggregate))

    boundaries=S.get('runtimeEnvironmentBoundary',{})
    check('r9-scope-boundaries-explicit',
          bool(boundaries.get('included'))
          and isinstance(boundaries.get('notClaimedUntilScopeExpands'),list),
          json.dumps(boundaries,sort_keys=True))
    check('r9-no-hidden-dynamic-scope',
          'closed-empty-domain-source-scan' in pre_checks,
          'forbiddenUntilScopeUpdated scan PASS')

    requirements_ok=all(x['passed'] for x in requirement_results.values())
    domains_ok=all(domains.values())
    r9=(
        requirements_ok and domains_ok and asset_ok
        and ev['cross-domain.json'].get('status')=='PASS'
        and challenged==len(fault_challenge)
        and oracle_diversity
        and all(v==0 for v in aggregate.values())
        and all(c.passed for c in checks)
    )
    check('app-safe-r9',r9,'R9 application-wide completeness')
    domains['R9']=r9

    failed=[x for x in checks if not x.passed]
    ok=(not failed and all(domains.values()) and asset_ok
        and ev['cross-domain.json'].get('status')=='PASS')

    total_claims=len(C.get('requirements',[]))+9+2
    proven_claims=total_claims if ok else (
        sum(1 for x in requirement_results.values() if x['passed'])
        +sum(1 for v in domains.values() if v)
        +(1 if asset_ok else 0)
        +(1 if ev['cross-domain.json'].get('status')=='PASS' else 0)
    )

    evidence_by_domain={
        'R1':['application-prebuild.json','instrumentation-debug.json','runtime-debug.json','runtime-release.json'],
        'R2':['application-prebuild.json','runtime-debug.json','runtime-release.json'],
        'R3':['instrumentation-debug.json','runtime-debug.json','runtime-release.json'],
        'R4':['prebuild.json','application_scope.json'],
        'R5':['prebuild.json','apk-release.json','runtime-debug.json','runtime-release.json'],
        'R6':['dependency-trust-current.json','signing-identity.json','reproducibility.json','apk-debug.json','apk-release.json','signature-debug.json','signature-release.json','artifacts/provenance.json'],
        'R7':['apk-debug.json','apk-release.json','application_scope.json'],
        'R8':['instrumentation-debug.json','runtime-debug.json','runtime-release.json'],
        'R9':['all final evidence','cross-domain.json','asset-safe-100.json'],
    }
    assurance_graph={
        'topClaim':'APPLICATION_SAFE_100',
        'domainClaims':{
            k:{'status':'PASS' if v else 'NOT_PROVEN','evidence':evidence_by_domain[k]}
            for k,v in domains.items()
        },
        'assetClaim':'PASS' if asset_ok else 'NOT_PROVEN',
        'crossDomainClaim':ev['cross-domain.json'].get('status'),
        'requirements':requirement_results,
    }

    payload={
        'schemaVersion':2,
        'gate':'APPLICATION_SAFE_100',
        'status':'PASS' if ok else 'NOT_PROVEN',
        'gitSha':sha,
        'domains':{k:'PASS' if v else 'NOT_PROVEN' for k,v in domains.items()},
        'totalRequiredClaims':total_claims,
        'provenClaims':proven_claims,
        'unprovenClaims':0 if ok else total_claims-proven_claims,
        'totalFaultClasses':len(fault_challenge),
        'faultClassesChallenged':challenged,
        'faultChallenge':fault_challenge,
        'requirementCoveragePercent':100 if requirements_ok else None,
        'faultModelCoveragePercent':100 if challenged==len(fault_challenge) else None,
        'evidenceCoveragePercent':100 if ok else None,
        'unknown':0 if ok else aggregate['unknown']+len(failed),
        'missing':0 if ok else aggregate['missing']+sum(1 for x in failed if x.name.endswith('-present')),
        'unproven':0 if ok else aggregate['unproven']+len(failed),
        'skipped':0 if ok else aggregate['skipped'],
        'indeterminate':0 if ok else aggregate['indeterminate'],
        'staleEvidence':0 if ok else aggregate['staleEvidence'],
        'faultEscape':aggregate['faultEscape'],
        'unresolvedDefeater':0 if ok else None,
        'undeclaredMaterialAssumption':0 if ok else None,
        'assuranceGraph':assurance_graph,
        'checks':[asdict(x) for x in checks],
        'failed':[x.name for x in failed]
    }
    E.mkdir(parents=True,exist_ok=True)
    (E/'application-safe-100.json').write_text(
        json.dumps(payload,indent=2,sort_keys=True)+'\n',encoding='utf-8')

    if not ok:
        print('APPLICATION_SAFE_100 = NOT_PROVEN',file=sys.stderr)
        for x in failed:
            print(f'FAIL {x.name}: {x.detail}',file=sys.stderr)
        return 1

    print('APP_SAFE_R1_PASS APP_SAFE_R2_PASS APP_SAFE_R3_PASS APP_SAFE_R4_PASS '
          'APP_SAFE_R5_PASS APP_SAFE_R6_PASS APP_SAFE_R7_PASS APP_SAFE_R8_PASS '
          'APP_SAFE_R9_PASS')
    print('APPLICATION_SAFE_100')
    return 0

if __name__=='__main__':
    raise SystemExit(main())

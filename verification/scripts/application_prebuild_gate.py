#!/usr/bin/env python3
"""Aggregate fail-closed A0-A9 evidence and emit APPLICATION_PREBUILD_PASS only."""

from __future__ import annotations

import hashlib
import json
import os
import re
import subprocess
import sys
from dataclasses import asdict, dataclass
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
EVIDENCE_DIR=ROOT/"verification/evidence"
CONTRACT_PATH=ROOT/"verification/application_contracts.json"
SCOPE_PATH=ROOT/"verification/application_scope.json"

@dataclass
class Check:
    name:str
    passed:bool
    detail:str

def sha256(path:Path)->str:
    h=hashlib.sha256()
    with path.open("rb") as fh:
        for block in iter(lambda:fh.read(1024*1024),b""): h.update(block)
    return h.hexdigest()

def git_sha()->str|None:
    value=os.environ.get("GITHUB_SHA","").strip()
    if re.fullmatch(r"[0-9a-fA-F]{40}",value): return value.lower()
    try:
        value=subprocess.check_output(["git","rev-parse","HEAD"],cwd=ROOT,text=True).strip(); return value.lower() if re.fullmatch(r"[0-9a-fA-F]{40}",value) else None
    except Exception: return None

def load_evidence(name:str)->dict:
    path=EVIDENCE_DIR/name
    if not path.is_file(): return {}
    try:return json.loads(path.read_text())
    except Exception:return {}

def main()->int:
    checks=[]
    def check(name,condition,detail): checks.append(Check(name,bool(condition),detail))
    source_sha=git_sha(); contract=json.loads(CONTRACT_PATH.read_text()); scope=json.loads(SCOPE_PATH.read_text())
    check("a0-source-sha-known",source_sha is not None,f"gitSha={source_sha}"); check("a0-platform-api30",contract["platform"]["androidApi"]==30==scope["platform"]["androidApi"],"API 30"); check("a0-platform-arm64",contract["platform"]["abi"]=="arm64-v8a"==scope["platform"]["abi"],"arm64-v8a"); check("a0-requirements-nonempty",len(contract.get("requirements",[]))>=8,f"requirements={len(contract.get('requirements',[]))}")
    required_domains={name for name,item in scope["domainScope"].items() if item["status"]=="REQUIRED" and name!="ASSET"}; requirement_domains={domain for req in contract["requirements"] for domain in req["domains"]}; check("a0-required-domain-ownership",required_domains.issubset(requirement_domains),f"required={sorted(required_domains)}, mapped={sorted(requirement_domains)}")
    for req in contract["requirements"]: check(f"trace-{req['id']}",bool(req.get("behavior")) and bool(req.get("implementation")) and bool(req.get("proof")),req["id"])
    for domain in ("R4","R7"): check(f"{domain.lower()}-closed-empty-scope",scope["domainScope"][domain]["status"]=="CLOSED_EMPTY" and contract["domainClosure"][domain]["status"]=="CLOSED_EMPTY","CLOSED_EMPTY")
    specs={"prebuild.json":"PASS","asset-prebuild.json":"PASS","dependency-trust-current.json":"PASS","signing-identity.json":"PASS","prebuild-mutations.json":"PASS"}; evidence={name:load_evidence(name) for name in specs}
    for name,expected in specs.items():
        item=evidence[name]; check(f"evidence-{name}-present",bool(item),name); check(f"evidence-{name}-pass",item.get("status")==expected,f"status={item.get('status')}")
        if item.get("gitSha"): check(f"evidence-{name}-fresh",item.get("gitSha")==source_sha,f"evidenceSha={item.get('gitSha')}, sourceSha={source_sha}")
    check("asset-route-proof",evidence["asset-prebuild.json"].get("routeProof")=="ROUTE_PROOF_PASS",str(evidence["asset-prebuild.json"].get("routeProof")))
    for env_name in ("KERNEL_CHECK_PASS","APP_UNIT_CHECK_PASS","APP_LINT_PASS","R6_TOOLCHAIN_PASS"): check(f"ci-marker-{env_name.lower()}",os.environ.get(env_name)=="1",f"{env_name}={os.environ.get(env_name)}")
    mutation=evidence["prebuild-mutations.json"]; check("prebuild-fault-escape-zero",mutation.get("faultEscape")==0,f"faultEscape={mutation.get('faultEscape')}")
    stages={"A0_SCOPE_LOCK":all(c.passed for c in checks if c.name.startswith("a0-") or c.name.startswith("trace-")),"A1_R1_PREBUILD":os.environ.get("APP_UNIT_CHECK_PASS")=="1","A2_R2_PREBUILD":os.environ.get("KERNEL_CHECK_PASS")=="1" and os.environ.get("APP_LINT_PASS")=="1","A3_R3_PREBUILD":os.environ.get("APP_UNIT_CHECK_PASS")=="1","A4_R4_PREBUILD":scope["domainScope"]["R4"]["status"]=="CLOSED_EMPTY" and evidence["prebuild.json"].get("status")=="PASS","A5_R5_PREBUILD":evidence["prebuild.json"].get("status")=="PASS","A6_R6_PREBUILD":evidence["dependency-trust-current.json"].get("status")=="PASS" and evidence["signing-identity.json"].get("status")=="PASS" and os.environ.get("R6_TOOLCHAIN_PASS")=="1","A7_R7_PREBUILD":scope["domainScope"]["R7"]["status"]=="CLOSED_EMPTY" and evidence["prebuild.json"].get("status")=="PASS","A8_R8_PREBUILD":evidence["asset-prebuild.json"].get("status")=="PASS" and os.environ.get("APP_LINT_PASS")=="1"}
    stages["A9_APPLICATION_PREBUILD"]=all(stages.values()) and all(c.passed for c in checks)
    for stage,passed in stages.items(): check(f"stage-{stage.lower()}",passed,stage)
    failed=[c for c in checks if not c.passed]; status="PASS" if not failed and stages["A9_APPLICATION_PREBUILD"] else "NOT_PROVEN"; payload={"schemaVersion":1,"gate":"APPLICATION_PREBUILD_PASS","status":status,"gitSha":source_sha,"scopeSha256":sha256(SCOPE_PATH),"contractSha256":sha256(CONTRACT_PATH),"stages":stages,"unknown":0 if status=="PASS" else len(failed),"missing":0 if status=="PASS" else sum(1 for c in failed if "present" in c.name),"unproven":0 if status=="PASS" else len(failed),"skipped":0,"faultEscape":mutation.get("faultEscape"),"checks":[asdict(c) for c in checks],"failed":[c.name for c in failed]}; EVIDENCE_DIR.mkdir(parents=True,exist_ok=True); (EVIDENCE_DIR/"application-prebuild.json").write_text(json.dumps(payload,indent=2,sort_keys=True)+"\n")
    if status!="PASS": print("APPLICATION_PREBUILD_PASS = NOT_PROVEN",file=sys.stderr); return 1
    print("APPLICATION_PREBUILD_PASS"); return 0
if __name__=="__main__": raise SystemExit(main())

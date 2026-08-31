#!/usr/bin/env python3
"""Close ASSET_SAFE_100 for the intentionally minimal bootstrap asset universe."""
from __future__ import annotations
import json, os, re, sys
from dataclasses import asdict, dataclass
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]; EVIDENCE_DIR=ROOT/"verification/evidence"; ASSET_CONTRACT=json.loads((ROOT/"verification/asset_contracts.json").read_text())
@dataclass
class Check:
    name:str; passed:bool; detail:str
def load(name:str)->dict:
    path=EVIDENCE_DIR/name
    if not path.is_file(): return {}
    try:return json.loads(path.read_text())
    except Exception:return {}
def main()->int:
    sha=os.environ.get("GITHUB_SHA","").lower(); checks=[]
    def check(name,condition,detail): checks.append(Check(name,bool(condition),detail))
    check("asset-final-git-sha-format",bool(re.fullmatch(r"[0-9a-f]{40}",sha)),sha)
    names=["asset-prebuild.json","prebuild-mutations.json","apk-release.json","runtime-release.json","asset-mutations.json"]; evidence={n:load(n) for n in names}
    for name,item in evidence.items():
        check(f"asset-final-{name}-present",bool(item),name); check(f"asset-final-{name}-pass",item.get("status")=="PASS",f"status={item.get('status')}")
        if item.get("gitSha"): check(f"asset-final-{name}-fresh",item.get("gitSha")==sha,f"evidenceSha={item.get('gitSha')}, sourceSha={sha}")
    pre=evidence["asset-prebuild.json"]; apk=evidence["apk-release.json"]; runtime=evidence["runtime-release.json"]; pre_mut=evidence["prebuild-mutations.json"]; post_mut=evidence["asset-mutations.json"]
    check("asset-route-proof-pass",pre.get("routeProof")=="ROUTE_PROOF_PASS",str(pre.get("routeProof"))); check("asset-required-universe-complete",pre.get("requiredAssets")==len(ASSET_CONTRACT["assetUniverse"]["required"]),str(pre.get("requiredAssets"))); check("asset-final-package-native-empty",apk.get("details",{}).get("nativeEntries")==[],str(apk.get("details",{}).get("nativeEntries")))
    manifest=apk.get("details",{}).get("manifest",{}); check("asset-final-theme-semantic",manifest.get("theme")=="@android:style/Theme.Material.NoActionBar",str(manifest.get("theme")))
    runtime_checks={i.get("name"):i.get("passed") for i in runtime.get("checks",[])}; check("asset-runtime-consumer-exercised",runtime_checks.get("ui-running-baseline") is True and runtime_checks.get("ui-accessibility-baseline") is True,"runtime consumer"); check("asset-runtime-config-witnesses",all(runtime_checks.get(f"ui-running-{n}") is True for n in ("landscape","portrait","font-1_3","font-2_0","night","day","compact-screen")),"configuration witnesses"); check("asset-prebuild-mutation-zero-escape",pre_mut.get("faultEscape")==0,str(pre_mut.get("faultEscape"))); check("asset-postbuild-mutation-zero-escape",post_mut.get("faultEscape")==0,str(post_mut.get("faultEscape")))
    active=ASSET_CONTRACT["activeFaultClasses"]; fault_evidence={"PRESENCE_MISSING":["missing-manifest","missing-required-entry"],"PATH_AMBIGUITY":["normalized-path-collision","unsafe-package-path"],"SYNTAX_MALFORMED_MANIFEST":["malformed-manifest"],"SEMANTIC_WRONG_MANIFEST_VALUE":["wrong-framework-theme","wrong-final-theme"],"REFERENCE_FRAMEWORK_THEME_MISSING":["wrong-framework-theme"],"CONSUMER_BINDING_ERROR":["wrong-final-theme","ui-running-baseline"],"PACKAGING_REQUIRED_ENTRY_MISSING":["missing-required-entry"],"PACKAGING_UNEXPECTED_NATIVE_PAYLOAD":["unexpected-native-payload"],"FINAL_MANIFEST_DRIFT":["wrong-final-package","wrong-final-theme"],"RESOURCE_BUDGET_EXCEEDED":["pathological-compression","apk-size-budget","apk-expanded-size-budget"]}; observed={i.get("name") for i in pre_mut.get("results",[]) if i.get("detected")}|{i.get("name") for i in post_mut.get("results",[]) if i.get("detected")}|{i.get("name") for i in runtime.get("checks",[]) if i.get("passed")}|{i.get("name") for i in apk.get("checks",[]) if i.get("passed")}
    for fault in active:
        needed=fault_evidence.get(fault,[]); check(f"asset-fault-{fault.lower()}",bool(needed) and any(n in observed for n in needed),f"evidence={needed}")
    failed=[c for c in checks if not c.passed]; payload={"schemaVersion":1,"gate":"ASSET_SAFE_100","status":"PASS" if not failed else "NOT_PROVEN","gitSha":sha,"requiredAssets":len(ASSET_CONTRACT["assetUniverse"]["required"]),"activeFaultClasses":len(active),"faultEscape":0 if not failed else post_mut.get("faultEscape"),"unknown":0 if not failed else len(failed),"missing":0 if not failed else sum(1 for c in failed if "present" in c.name),"unproven":0 if not failed else len(failed),"skipped":0,"checks":[asdict(c) for c in checks],"failed":[c.name for c in failed]}; EVIDENCE_DIR.mkdir(parents=True,exist_ok=True); (EVIDENCE_DIR/"asset-safe-100.json").write_text(json.dumps(payload,indent=2,sort_keys=True)+"\n")
    if failed: print("ASSET_SAFE_100 = NOT_PROVEN",file=sys.stderr); return 1
    print("ASSET_SAFE_100"); return 0
if __name__=="__main__": raise SystemExit(main())

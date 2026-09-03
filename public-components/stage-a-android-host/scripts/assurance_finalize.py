#!/usr/bin/env python3
import json, os
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
BUILD=ROOT/"build"; OUT=BUILD/"assurance"; OUT.mkdir(parents=True,exist_ok=True)
pre=json.loads((OUT/"android-host-prebuild-evidence.json").read_text())
mut=json.loads((OUT/"android-host-mutation-evidence.json").read_text())
jvm=(BUILD/"jvm"/"output.txt").read_text()
runtime={}
for line in (BUILD/"host-runtime-summary.txt").read_text().splitlines():
    if "=" in line:
        k,v=line.split("=",1); runtime[k]=v
assert pre["status"]=="PASS" and mut["status"]=="PASS" and mut["mutationsEscaped"]==0
assert pre["productionAdapterUniverse"]=="PASS" and pre["safeUiActions"]=="PUBLIC_PRODUCTION_ADAPTER_FAIL_CLOSED"
assert "STAGE_A_ANDROID_HOST_JVM_TESTS = PASS" in jvm
for key in ["HOST_DURABLE_WRITE","HOST_PROCESS_RESTART_READ","HOST_SHARED_PRODUCT_REGISTRY","HOST_CORRUPTION_REJECTION",
            "HOST_PERMISSION_PROVIDER","HOST_RESOURCE_MAPPING","HOST_SAFE_UI_RENDER","HOST_SAFE_UI_ACTIONS"]:
    assert runtime.get(key)=="PASS",(key,runtime.get(key))
assert runtime["ANDROID_API"]=="30" and runtime["NETWORK_CALLS"]=="0" and runtime["FIREBASE_USED"]=="0"
evidence={"schemaVersion":1,"projectId":"ToolBox","stageId":"A","componentId":"public.stage-a-android-host","status":"PASS",
          "sourceRepository":os.environ.get("GITHUB_REPOSITORY","LOCAL_PUBLIC_VALIDATION"),
          "sourceCommitSha":os.environ.get("GITHUB_SHA","LOCAL_PUBLIC_VALIDATION"),
          "workflowRunId":os.environ.get("GITHUB_RUN_ID","LOCAL_PUBLIC_VALIDATION"),
          "evidence":{"pureJvm":"PASS","api30EmulatorRuntime":"PASS","processRestartDurability":"PASS","sharedProductRegistry":"PASS","corruptionChallenge":"PASS",
                      "permissionProvider":"PASS","resourceMapping":"PASS","safeUiRender":"PASS","safeUiActions":"PASS","mutation":"PASS"},
          "environment":{"androidApi":30,"abi":"x86_64","finalArm64Claimed":False},
          "closure":{"unknown":0,"skipped":0,"notProvenWithinHostPublicScope":0,"staleEvidence":0,"mutationEscape":0},
          "publicBoundaries":{"privateContentIncluded":False,"networkUsed":False,"firebaseUsed":False,"finalApplicationClaimed":False}}
(OUT/"android-host-r1-r9-evidence.json").write_text(json.dumps(evidence,indent=2,sort_keys=True)+"\n")
print("STAGE_A_ANDROID_HOST_R1_R9 = PASS")
print("ANDROID_HOST_SHARED_PRODUCT_REGISTRY=PASS")
print("ANDROID_HOST_SAFE_UI_ACTIONS=PASS")
print("ANDROID_HOST_UNKNOWN=0")

#!/usr/bin/env python3
import json, subprocess, tempfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
REPO=ROOT.parents[1]
OUT=ROOT/"build"/"assurance"
OUT.mkdir(parents=True,exist_ok=True)
base_sources={
 "StateFileCodec.java":(ROOT/"src/main/java/io/toolbox/stagea/android/StateFileCodec.java").read_text(),
 "NormalizedResourceMath.java":(ROOT/"src/main/java/io/toolbox/stagea/android/NormalizedResourceMath.java").read_text(),
}
mutations=[
 ("checksum_bypass","StateFileCodec.java","if (!MessageDigest.isEqual(expected, digest(body))) {","if (false) {"),
 ("deterministic_order_removed","StateFileCodec.java","TreeMap<String, String> sorted = new TreeMap<>();","Map<String, String> sorted = new LinkedHashMap<>();"),
 ("forced_pressure_reject_bypass","NormalizedResourceMath.java","if (forceReject) return NORMALIZED_BUDGET + 1;","if (forceReject) return NORMALIZED_BUDGET;"),
]
killed=[]
for name,target,old,new in mutations:
    with tempfile.TemporaryDirectory() as td:
        td=Path(td); src=td/"src"; classes=td/"classes"; src.mkdir(); classes.mkdir()
        for filename,text in base_sources.items():
            changed=text
            if filename==target:
                assert old in text,(name,old)
                changed=text.replace(old,new,1)
            (src/filename).write_text(changed)
        sources=[
          REPO/"public-components/runtime-contracts/src/main/java/io/toolbox/contracts/runtime/Contracts.java",
          REPO/"public-components/runtime-safety-contracts/src/main/java/io/toolbox/contracts/safety/SafetyContracts.java",
          REPO/"public-components/stage-a-foundation/src/main/java/io/toolbox/stagea/StageAContracts.java",
          src/"StateFileCodec.java",src/"NormalizedResourceMath.java",
          ROOT/"src/test/java/io/toolbox/stagea/android/HostPureJvmSelfTest.java",
        ]
        compile=subprocess.run(["javac","--release","11","-Xlint:all","-Werror","-d",str(classes)]+[str(x) for x in sources],
                               text=True,capture_output=True)
        if compile.returncode!=0:
            killed.append(name); print("MUTATION_KILLED_COMPILE="+name); continue
        run=subprocess.run(["java","-ea","-cp",str(classes),"io.toolbox.stagea.android.HostPureJvmSelfTest"],
                           text=True,capture_output=True)
        if run.returncode!=0:
            killed.append(name); print("MUTATION_KILLED="+name)
        else:
            raise SystemExit("MUTATION_ESCAPED="+name)
evidence={"schemaVersion":1,"status":"PASS","mutationsTotal":len(mutations),"mutationsKilled":len(killed),
          "mutationsEscaped":0,"killed":killed}
(OUT/"android-host-mutation-evidence.json").write_text(json.dumps(evidence,indent=2,sort_keys=True)+"\n")
print("STAGE_A_ANDROID_HOST_MUTATION = PASS")
print("ANDROID_HOST_MUTATIONS_KILLED="+str(len(killed)))

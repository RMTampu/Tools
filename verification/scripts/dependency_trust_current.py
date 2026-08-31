#!/usr/bin/env python3
"""Current-revision dependency trust proof using two independent clean Gradle homes."""

from __future__ import annotations

import hashlib
import io
import json
import os
import re
import subprocess
import sys
import tarfile
import tempfile
from dataclasses import asdict, dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
EVIDENCE_DIR = ROOT / "verification" / "evidence"
FILES = (
    "toolbox-kernel/gradle.lockfile",
    "toolbox-app/gradle.lockfile",
    "gradle/verification-metadata.xml",
)

@dataclass
class Check:
    name: str
    passed: bool
    detail: str

def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()

def git_sha() -> str | None:
    env = os.environ.get("GITHUB_SHA", "").strip()
    if re.fullmatch(r"[0-9a-fA-F]{40}", env):
        return env.lower()
    try:
        out = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
        return out.lower() if re.fullmatch(r"[0-9a-fA-F]{40}", out) else None
    except Exception:
        return None

def tracked_archive() -> bytes:
    return subprocess.check_output(["git", "archive", "HEAD"], cwd=ROOT)

def extract_archive(data: bytes, destination: Path) -> None:
    with tarfile.open(fileobj=io.BytesIO(data), mode="r:") as archive:
        archive.extractall(destination)

def run_resolution(source: Path, gradle_home: Path) -> tuple[int, str]:
    env = os.environ.copy(); env["GRADLE_USER_HOME"] = str(gradle_home)
    completed = subprocess.run(["gradle","-p",str(source),"--no-daemon","--refresh-dependencies","--dependency-verification","strict",":toolbox-kernel:dependencies",":toolbox-app:dependencies",":toolbox-app:writeReleaseDependencyInventory"],env=env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,check=False,timeout=1800)
    return completed.returncode, completed.stdout

def main() -> int:
    checks: list[Check] = []
    def check(name: str, condition: bool, detail: str) -> None: checks.append(Check(name,bool(condition),detail))
    source_sha = git_sha(); check("dependency-source-sha-known",source_sha is not None,f"gitSha={source_sha}")
    missing=[name for name in FILES if not (ROOT/name).is_file()]; check("dependency-trust-files-present",not missing,f"missing={missing}")
    current_hashes={name:sha256(ROOT/name) for name in FILES if (ROOT/name).is_file()}
    dynamic=[]
    for path in (ROOT/"build.gradle.kts",ROOT/"settings.gradle.kts",ROOT/"toolbox-app/build.gradle.kts",ROOT/"toolbox-kernel/build.gradle.kts"):
        text=path.read_text(encoding="utf-8")
        for pattern in (r':[+]"',r':latest(?:\.[^"]+)?',r'SNAPSHOT',r':\[[^"]+'):
            if re.search(pattern,text,re.IGNORECASE): dynamic.append(f"{path.relative_to(ROOT)}::{pattern}")
    check("dependency-no-dynamic-selector",not dynamic,f"hits={dynamic}")
    metadata=ROOT/"gradle"/"verification-metadata.xml"
    if metadata.is_file():
        text=metadata.read_text(encoding="utf-8"); check("dependency-sha256-verification-enabled","<sha256" in text,"verification-metadata.xml"); check("dependency-metadata-verification-enabled","<verify-metadata>true</verify-metadata>" in text,"verification-metadata.xml")
    replica_inventories=[]; replica_codes=[]; logs=[]
    try:
        archive=tracked_archive()
        with tempfile.TemporaryDirectory(prefix="toolbox-dependency-proof-") as tmp:
            rr=Path(tmp)
            for name in ("a","b"):
                source=rr/f"source-{name}"; home=rr/f"gradle-home-{name}"; source.mkdir(); home.mkdir(); extract_archive(archive,source)
                code,output=run_resolution(source,home); replica_codes.append(code); logs.append(output[-12000:])
                inventory=source/"toolbox-app"/"build"/"reports"/"verification"/"release-dependencies.txt"; replica_inventories.append(inventory.read_text(encoding="utf-8") if inventory.is_file() else "")
                for required in FILES:
                    if (source/required).is_file() and required in current_hashes: check(f"replica-{name}-{required.replace('/','-')}-identity",sha256(source/required)==current_hashes[required],required)
    except Exception as exc: check("dependency-clean-resolution-execution",False,str(exc))
    check("dependency-replica-a-resolution",len(replica_codes)>=1 and replica_codes[0]==0,f"exit={replica_codes[0] if replica_codes else None}")
    check("dependency-replica-b-resolution",len(replica_codes)>=2 and replica_codes[1]==0,f"exit={replica_codes[1] if len(replica_codes)>1 else None}")
    check("dependency-independent-convergence",len(replica_inventories)==2 and bool(replica_inventories[0]) and replica_inventories[0]==replica_inventories[1],"two clean inventories identical")
    failed=[c for c in checks if not c.passed]; EVIDENCE_DIR.mkdir(parents=True,exist_ok=True)
    payload={"schemaVersion":1,"gate":"DEPENDENCY_TRUST_CURRENT","status":"PASS" if not failed else "NOT_PROVEN","gitSha":source_sha,"trustedFilesSha256":current_hashes,"releaseInventorySha256":hashlib.sha256(replica_inventories[0].encode()).hexdigest() if replica_inventories and replica_inventories[0] else None,"independentCleanReplicas":2,"sharedGradleDependencyCache":False,"checks":[asdict(c) for c in checks],"failed":[c.name for c in failed],"diagnosticLogTails":logs if failed else []}
    (EVIDENCE_DIR/"dependency-trust-current.json").write_text(json.dumps(payload,indent=2,sort_keys=True)+"\n",encoding="utf-8")
    if failed:
        print("DEPENDENCY_TRUST_CURRENT = NOT_PROVEN",file=sys.stderr); return 1
    print("DEPENDENCY_TRUST_CURRENT = PASS"); return 0
if __name__=="__main__": raise SystemExit(main())

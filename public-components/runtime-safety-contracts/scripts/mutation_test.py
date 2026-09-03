#!/usr/bin/env python3
from __future__ import annotations

import shutil
import struct
import tempfile
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "src" / "main" / "java" / "io" / "toolbox" / "contracts" / "safety"
JAR = ROOT / "build" / "package" / "toolbox-runtime-safety-contracts-0.1.0.jar"


def fail(message: str) -> None:
    raise SystemExit(f"MUTATION_TEST_FAIL: {message}")


def source_policy_violations(root: Path) -> list[str]:
    violations: list[str] = []
    diagnostic = (root / "DiagnosticBuffer.java").read_text(encoding="utf-8")
    recovery = (root / "RecoveryMachine.java").read_text(encoding="utf-8")
    safety = (root / "SafetyContracts.java").read_text(encoding="utf-8")
    all_text = "\n".join(path.read_text(encoding="utf-8") for path in sorted(root.glob("*.java")))

    for marker in (
        "public synchronized void record",
        "public synchronized List",
        "public synchronized long droppedCount",
    ):
        if marker not in diagnostic:
            violations.append("DIAGNOSTIC_SYNCHRONIZATION_REMOVED")
            break
    for marker in (
        "public synchronized SafetyContracts.RecoveryState state",
        "public synchronized SafetyContracts.Transition apply",
    ):
        if marker not in recovery:
            violations.append("RECOVERY_SYNCHRONIZATION_REMOVED")
            break
    if "capacity > SafetyContracts.MAX_DIAGNOSTIC_CAPACITY" not in diagnostic:
        violations.append("DIAGNOSTIC_CAPACITY_BOUND_REMOVED")
    if "MAX_DIAGNOSTIC_CAPACITY = 256" not in safety or "MAX_BUDGET = 1_000_000" not in safety:
        violations.append("RESOURCE_BOUND_CONTRACT_CHANGED")
    for token in ("java.net.", "ProcessBuilder", "Class.forName", "com.google.firebase"):
        if token in all_text:
            violations.append("EXTERNAL_AUTHORITY_INJECTED")
            break
    return violations


def mutate_source(temp_root: Path, filename: str, old: str, new: str) -> None:
    path = temp_root / filename
    text = path.read_text(encoding="utf-8")
    if old not in text:
        fail(f"mutation seed not found in {filename}: {old}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def validate_jar(path: Path) -> list[str]:
    violations: list[str] = []
    required = {
        "io/toolbox/contracts/safety/SafetyContracts.class",
        "io/toolbox/contracts/safety/DiagnosticBuffer.class",
        "io/toolbox/contracts/safety/ResourceGuard.class",
        "io/toolbox/contracts/safety/RecoveryMachine.class",
        "io/toolbox/contracts/safety/RuntimeSafetySimulator.class",
    }
    with zipfile.ZipFile(path) as archive:
        names = set(archive.namelist())
        if required - names:
            violations.append("REQUIRED_CLASS_MISSING")
        for name in names:
            if name.endswith(".class") and not name.startswith("io/toolbox/contracts/safety/"):
                violations.append("PACKAGE_NAMESPACE_ESCAPE")
                break
        for name in names:
            if not name.endswith(".class"):
                continue
            data = archive.read(name)
            if len(data) < 8 or data[:4] != b"\xca\xfe\xba\xbe":
                violations.append("INVALID_CLASS_HEADER")
                break
            major = struct.unpack(">H", data[6:8])[0]
            if major > 55:
                violations.append("BYTECODE_TARGET_MISMATCH")
                break
    return violations


def rewrite_jar(source: Path, destination: Path, transform) -> None:
    with zipfile.ZipFile(source) as src, zipfile.ZipFile(destination, "w", compression=zipfile.ZIP_DEFLATED) as dst:
        for info in src.infolist():
            data = src.read(info.filename)
            name, data, keep = transform(info.filename, data)
            if not keep:
                continue
            new_info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 2))
            new_info.compress_type = zipfile.ZIP_DEFLATED
            new_info.external_attr = 0o100644 << 16
            dst.writestr(new_info, data)


if not JAR.is_file() or JAR.stat().st_size == 0:
    fail("component JAR missing; run build/tests first")
if source_policy_violations(SOURCE):
    fail("unmutated source already violates policy")
if validate_jar(JAR):
    fail("unmutated JAR already violates package policy")

source_mutations = [
    ("DiagnosticBuffer.java", "public synchronized void record", "public void record", "DIAGNOSTIC_SYNCHRONIZATION_REMOVED"),
    ("RecoveryMachine.java", "public synchronized SafetyContracts.Transition apply", "public SafetyContracts.Transition apply", "RECOVERY_SYNCHRONIZATION_REMOVED"),
    ("DiagnosticBuffer.java", "capacity > SafetyContracts.MAX_DIAGNOSTIC_CAPACITY", "capacity > Integer.MAX_VALUE", "DIAGNOSTIC_CAPACITY_BOUND_REMOVED"),
    ("SafetyContracts.java", "package io.toolbox.contracts.safety;", "package io.toolbox.contracts.safety;\n// java.net.Socket forbidden mutation", "EXTERNAL_AUTHORITY_INJECTED"),
]

source_killed = 0
for filename, old, new, expected in source_mutations:
    with tempfile.TemporaryDirectory(prefix="runtime-safety-source-mutation-") as temp:
        root = Path(temp)
        for path in SOURCE.glob("*.java"):
            shutil.copy2(path, root / path.name)
        mutate_source(root, filename, old, new)
        violations = source_policy_violations(root)
        if expected not in violations:
            fail(f"source mutation escaped: {expected}; observed={violations}")
        source_killed += 1

with tempfile.TemporaryDirectory(prefix="runtime-safety-package-mutation-") as temp:
    temp_root = Path(temp)

    missing = temp_root / "missing-class.jar"
    rewrite_jar(
        JAR,
        missing,
        lambda name, data: (name, data, name != "io/toolbox/contracts/safety/ResourceGuard.class"),
    )
    if "REQUIRED_CLASS_MISSING" not in validate_jar(missing):
        fail("missing-class mutation escaped")

    foreign = temp_root / "foreign-class.jar"
    with zipfile.ZipFile(JAR) as src, zipfile.ZipFile(foreign, "w", compression=zipfile.ZIP_DEFLATED) as dst:
        for info in src.infolist():
            dst.writestr(info, src.read(info.filename))
        data = src.read("io/toolbox/contracts/safety/ResourceGuard.class")
        dst.writestr("escape/Foreign.class", data)
    if "PACKAGE_NAMESPACE_ESCAPE" not in validate_jar(foreign):
        fail("foreign-namespace mutation escaped")

    bytecode = temp_root / "bytecode-major.jar"
    def raise_major(name: str, data: bytes):
        if name == "io/toolbox/contracts/safety/SafetyContracts.class":
            patched = bytearray(data)
            patched[6:8] = struct.pack(">H", 56)
            return name, bytes(patched), True
        return name, data, True
    rewrite_jar(JAR, bytecode, raise_major)
    if "BYTECODE_TARGET_MISMATCH" not in validate_jar(bytecode):
        fail("bytecode-major mutation escaped")

package_killed = 3
print("R1_R2_R5_SOURCE_MUTATION = PASS")
print(f"SOURCE_MUTATIONS_KILLED={source_killed}")
print("R6_PACKAGE_MUTATION = PASS")
print(f"PACKAGE_MUTATIONS_KILLED={package_killed}")
print(f"MUTATIONS_KILLED={source_killed + package_killed}")

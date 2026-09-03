#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import shutil
import struct
import zipfile

ROOT = Path(__file__).resolve().parent.parent
BUILD = ROOT / "build"
JAR = BUILD / "package" / "toolbox-runtime-contracts-0.1.0.jar"
MUTATION = BUILD / "mutation"

REQUIRED_CLASSES = {
    "io/toolbox/contracts/runtime/Contracts.class",
    "io/toolbox/contracts/runtime/ProductRegistry.class",
    "io/toolbox/contracts/runtime/RuntimeContractsSimulator.class",
}
FORBIDDEN_SOURCE_TOKENS = (
    "DexClassLoader",
    "PathClassLoader",
    "URLClassLoader",
    "Class.forName",
    "ProcessBuilder",
    "Runtime.getRuntime",
    "java.net.",
    "java.nio.file.",
    "com.google.firebase",
    "TOOLBOX_SOURCE_TOKEN",
    "SIGNING_KEY",
)


def fail(message: str) -> None:
    raise SystemExit(f"PACKAGE_MUTATION_TEST_FAIL: {message}")


def inspect_jar(path: Path) -> tuple[bool, str]:
    try:
        with zipfile.ZipFile(path) as archive:
            names = set(archive.namelist())
            missing = REQUIRED_CLASSES - names
            if missing:
                return False, "missing_required_class"
            for name in sorted(value for value in names if value.endswith(".class")):
                if not name.startswith("io/toolbox/contracts/runtime/"):
                    return False, "foreign_namespace"
                data = archive.read(name)
                if len(data) < 8 or data[:4] != b"\xca\xfe\xba\xbe":
                    return False, "invalid_class_header"
                major = struct.unpack(">H", data[6:8])[0]
                if major > 55:
                    return False, "bytecode_too_new"
    except (OSError, zipfile.BadZipFile) as error:
        return False, f"invalid_zip:{type(error).__name__}"
    return True, "ok"


def write_mutated_jar(target: Path, transform) -> None:
    with zipfile.ZipFile(JAR) as source, zipfile.ZipFile(target, "w", compression=zipfile.ZIP_DEFLATED) as out:
        for info in source.infolist():
            data = source.read(info.filename)
            result = transform(info.filename, data)
            if result is None:
                continue
            new_name, new_data = result
            out.writestr(new_name, new_data)


def source_boundary_clean(text: str) -> bool:
    return not any(token in text for token in FORBIDDEN_SOURCE_TOKENS)


if not JAR.is_file() or JAR.stat().st_size == 0:
    fail("original JAR missing")
original_ok, original_reason = inspect_jar(JAR)
if not original_ok:
    fail(f"original JAR does not satisfy mutation oracle: {original_reason}")

shutil.rmtree(MUTATION, ignore_errors=True)
MUTATION.mkdir(parents=True, exist_ok=True)
killed = 0

missing_class = MUTATION / "missing-class.jar"
write_mutated_jar(
    missing_class,
    lambda name, data: None if name == "io/toolbox/contracts/runtime/Contracts.class" else (name, data),
)
ok, reason = inspect_jar(missing_class)
if ok or reason != "missing_required_class":
    fail(f"missing-class mutation escaped: ok={ok} reason={reason}")
killed += 1

foreign_namespace = MUTATION / "foreign-namespace.jar"
with zipfile.ZipFile(JAR) as source, zipfile.ZipFile(foreign_namespace, "w", compression=zipfile.ZIP_DEFLATED) as out:
    copied = None
    for info in source.infolist():
        data = source.read(info.filename)
        out.writestr(info, data)
        if info.filename == "io/toolbox/contracts/runtime/Contracts.class":
            copied = data
    if copied is None:
        fail("fixture class not found")
    out.writestr("escape/Foreign.class", copied)
ok, reason = inspect_jar(foreign_namespace)
if ok or reason != "foreign_namespace":
    fail(f"foreign-namespace mutation escaped: ok={ok} reason={reason}")
killed += 1

new_bytecode = MUTATION / "new-bytecode.jar"
def mutate_major(name: str, data: bytes):
    if name == "io/toolbox/contracts/runtime/Contracts.class":
        altered = bytearray(data)
        altered[6:8] = struct.pack(">H", 56)
        return name, bytes(altered)
    return name, data
write_mutated_jar(new_bytecode, mutate_major)
ok, reason = inspect_jar(new_bytecode)
if ok or reason != "bytecode_too_new":
    fail(f"bytecode mutation escaped: ok={ok} reason={reason}")
killed += 1

contracts_source = (ROOT / "src/main/java/io/toolbox/contracts/runtime/Contracts.java").read_text(encoding="utf-8")
if not source_boundary_clean(contracts_source):
    fail("original source unexpectedly violates boundary scanner")
if source_boundary_clean(contracts_source + "\n// mutation java.net. boundary\n"):
    fail("forbidden-boundary source mutation escaped")
killed += 1

(BUILD / "package-mutation-output.txt").write_text(
    "PACKAGE_MUTATION_TEST = PASS\nMUTATIONS_KILLED=4\n",
    encoding="utf-8",
)
print("PACKAGE_MUTATION_TEST = PASS")
print(f"MUTATIONS_KILLED={killed}")

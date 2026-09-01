#!/usr/bin/env python3
"""Fail-closed dependency trust acceptance gate for ToolBox.

This verifier is intentionally separate from the Gradle bootstrap generator. It
recomputes source/input/output bindings, validates the lock state and every
verification-metadata artifact checksum, reconstructs the canonical dependency
verification model, and requires an explicit independent acceptance record.
"""

from __future__ import annotations

import hashlib
import json
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from dataclasses import asdict, dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
EVIDENCE_DIR = ROOT / "verification" / "evidence"
EVIDENCE_PATH = EVIDENCE_DIR / "dependency-trust.json"
SCOPE_PATH = ROOT / "verification" / "application_scope.json"
CANDIDATE_PATH = ROOT / "verification" / "dependency_trust_candidate.json"
REVIEW_PATH = ROOT / "verification" / "dependency_trust_review.json"
VERIFICATION_PATH = ROOT / "gradle" / "verification-metadata.xml"
LOCK_PATHS = {
    "toolbox-kernel/gradle.lockfile": ROOT / "toolbox-kernel" / "gradle.lockfile",
    "toolbox-app/gradle.lockfile": ROOT / "toolbox-app" / "gradle.lockfile",
}
NS = {"v": "https://schema.gradle.org/dependency-verification"}
HEX64 = re.compile(r"^[0-9a-f]{64}$")
GIT_SHA = re.compile(r"^[0-9a-f]{40}$")


@dataclass
class Check:
    name: str
    passed: bool
    detail: str


checks: list[Check] = []


def check(name: str, condition: bool, detail: str) -> None:
    checks.append(Check(name=name, passed=bool(condition), detail=detail))


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for block in iter(lambda: fh.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def load_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def expected_dependency_inputs() -> set[str]:
    tracked = subprocess.check_output(["git", "ls-files"], cwd=ROOT, text=True).splitlines()

    def relevant(path: str) -> bool:
        name = Path(path).name
        return (
            name
            in {
                "build.gradle",
                "build.gradle.kts",
                "settings.gradle",
                "settings.gradle.kts",
                "gradle.properties",
                "gradle-wrapper.properties",
                "libs.versions.toml",
            }
            or path == ".github/workflows/dependency-bootstrap.yml"
            or path == "verification/application_scope.json"
        )

    return {path for path in tracked if relevant(path)}


def lock_rows(path: Path) -> list[str]:
    rows = []
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or line == "empty=":
            continue
        rows.append(line)
    return rows


def canonical_verification_model(path: Path) -> tuple[dict, list[tuple[str, str, str, str]], list[tuple[str, str, str, str, list[str]]]]:
    root = ET.parse(path).getroot()
    config = root.find("v:configuration", NS)
    verify_metadata = config.findtext("v:verify-metadata", default="", namespaces=NS) if config is not None else ""
    verify_signatures = config.findtext("v:verify-signatures", default="", namespaces=NS) if config is not None else ""
    components = root.find("v:components", NS)
    if components is None:
        raise ValueError("verification metadata has no components section")

    rows: list[dict] = []
    missing: list[tuple[str, str, str, str]] = []
    multiple: list[tuple[str, str, str, str, list[str]]] = []
    for component in components.findall("v:component", NS):
        group = component.attrib.get("group", "")
        name = component.attrib.get("name", "")
        version = component.attrib.get("version", "")
        for artifact in component.findall("v:artifact", NS):
            artifact_name = artifact.attrib.get("name", "")
            hashes = sorted(
                {
                    node.attrib.get("value", "").lower()
                    for node in artifact.findall("v:sha256", NS)
                    if node.attrib.get("value")
                }
            )
            if not hashes:
                missing.append((group, name, version, artifact_name))
            if len(hashes) > 1:
                multiple.append((group, name, version, artifact_name, hashes))
            rows.append(
                {
                    "group": group,
                    "name": name,
                    "version": version,
                    "artifact": artifact_name,
                    "sha256": hashes,
                }
            )

    rows.sort(key=lambda item: (item["group"], item["name"], item["version"], item["artifact"], item["sha256"]))
    model = {
        "verifyMetadata": verify_metadata,
        "verifySignatures": verify_signatures,
        "artifacts": rows,
    }
    return model, missing, multiple


def canonical_digest(model: dict) -> str:
    raw = json.dumps(model, sort_keys=True, separators=(",", ":")).encode("utf-8") + b"\n"
    return hashlib.sha256(raw).hexdigest()


def finish(candidate: dict | None = None, review: dict | None = None) -> int:
    EVIDENCE_DIR.mkdir(parents=True, exist_ok=True)
    failed = [item for item in checks if not item.passed]
    payload = {
        "schemaVersion": 1,
        "gate": "DEPENDENCY_TRUST_GATE",
        "status": "PASS" if not failed else "NOT_PROVEN",
        "candidateSourceGitSha": candidate.get("generatedFromGitSha") if candidate else None,
        "workflowRunId": candidate.get("workflowRunId") if candidate else None,
        "reviewStatus": review.get("status") if review else None,
        "checks": [asdict(item) for item in checks],
        "failed": [item.name for item in failed],
    }
    EVIDENCE_PATH.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    if failed:
        print("DEPENDENCY_TRUST_GATE = NOT_PROVEN", file=sys.stderr)
        for item in failed:
            print(f"FAIL {item.name}: {item.detail}", file=sys.stderr)
        return 1
    print("DEPENDENCY_TRUST_GATE = PASS")
    return 0


def main() -> int:
    for path, label in (
        (SCOPE_PATH, "scope"),
        (CANDIDATE_PATH, "candidate"),
        (REVIEW_PATH, "review"),
        (VERIFICATION_PATH, "verification-metadata"),
        *[(path, name) for name, path in LOCK_PATHS.items()],
    ):
        check(f"{label}-present", path.is_file(), str(path.relative_to(ROOT)))
    if any(not item.passed for item in checks):
        return finish()

    try:
        scope = load_json(SCOPE_PATH)
        candidate = load_json(CANDIDATE_PATH)
        review = load_json(REVIEW_PATH)
    except Exception as exc:
        check("trust-json-readable", False, str(exc))
        return finish()

    check("candidate-schema", candidate.get("schemaVersion") == 3, f"schema={candidate.get('schemaVersion')}")
    check("candidate-unreviewed-state-preserved", candidate.get("status") == "CANDIDATE_UNREVIEWED", str(candidate.get("status")))
    source_sha = str(candidate.get("generatedFromGitSha", "")).lower()
    check("candidate-source-git-sha", bool(GIT_SHA.fullmatch(source_sha)), source_sha)
    check("candidate-workflow-run", str(candidate.get("workflowRunId", "")).isdigit(), str(candidate.get("workflowRunId")))

    expected_inputs = expected_dependency_inputs()
    candidate_inputs = set(candidate.get("dependencyInputsSha256", {}))
    check("dependency-input-universe-closed", candidate_inputs == expected_inputs, f"expected={sorted(expected_inputs)} candidate={sorted(candidate_inputs)}")
    input_mismatches: list[str] = []
    for rel, expected_hash in sorted(candidate.get("dependencyInputsSha256", {}).items()):
        path = ROOT / rel
        if not path.is_file() or not HEX64.fullmatch(str(expected_hash).lower()) or sha256(path) != str(expected_hash).lower():
            input_mismatches.append(rel)
    check("dependency-input-hashes-current", not input_mismatches, f"mismatches={input_mismatches}")

    expected_output_paths = set(LOCK_PATHS) | {"gradle/verification-metadata.xml"}
    candidate_outputs = candidate.get("outputsSha256", {})
    check("dependency-output-universe-closed", set(candidate_outputs) == expected_output_paths, f"outputs={sorted(candidate_outputs)}")
    output_mismatches: list[str] = []
    for rel, expected_hash in sorted(candidate_outputs.items()):
        path = ROOT / rel
        if not path.is_file() or not HEX64.fullmatch(str(expected_hash).lower()) or sha256(path) != str(expected_hash).lower():
            output_mismatches.append(rel)
    check("dependency-output-hashes-current", not output_mismatches, f"mismatches={output_mismatches}")

    platform = scope.get("platform", {})
    generator = candidate.get("generator", {})
    expected_generator = {
        "gradle": str(platform.get("gradle")),
        "jdk": str(platform.get("jdk")),
        "jdkRuntime": str(platform.get("jdkRuntime")),
        "compileSdk": str(platform.get("compileSdk")),
        "buildTools": str(platform.get("buildTools")),
        "androidCommandLineTools": str(platform.get("androidCommandLineTools")),
        "dependencyVerification": "sha256",
    }
    generator_mismatch = {
        key: (expected, generator.get(key))
        for key, expected in expected_generator.items()
        if str(generator.get(key)) != expected
    }
    check("generator-matches-scope", not generator_mismatch, f"mismatch={generator_mismatch}")
    check("generator-refresh-dependencies", generator.get("refreshDependencies") is True, str(generator.get("refreshDependencies")))
    check("gradle-distribution-hash-pinned", bool(HEX64.fullmatch(str(generator.get("gradleDistributionSha256", "")).lower())), str(generator.get("gradleDistributionSha256")))
    observed_hashes = candidate.get("observedToolchainSha256", {})
    bad_tool_hashes = sorted(key for key, value in observed_hashes.items() if not HEX64.fullmatch(str(value).lower()))
    check("observed-toolchain-hashes-recorded", len(observed_hashes) >= 5 and not bad_tool_hashes, f"bad={bad_tool_hashes}, count={len(observed_hashes)}")

    convergence = candidate.get("convergence", {})
    check("two-independent-clean-replicas", convergence.get("independentCleanReplicas") == 2, str(convergence))
    check("no-shared-gradle-dependency-cache", convergence.get("sharedGradleDependencyCache") is False, str(convergence.get("sharedGradleDependencyCache")))
    check("lockfiles-byte-converged", convergence.get("lockfilesByteIdentical") is True, str(convergence.get("lockfilesByteIdentical")))
    check("metadata-canonical-converged", convergence.get("verificationMetadataCanonicalIdentical") is True, str(convergence.get("verificationMetadataCanonicalIdentical")))

    lock_counts: dict[str, int] = {}
    dynamic_hits: list[str] = []
    duplicate_coords: list[str] = []
    all_rows: list[str] = []
    for rel, path in LOCK_PATHS.items():
        rows = lock_rows(path)
        lock_counts[rel] = len(rows)
        all_rows.extend(rows)
        coords = [row.split("=", 1)[0] for row in rows]
        seen: set[str] = set()
        for coord in coords:
            if coord in seen:
                duplicate_coords.append(f"{rel}:{coord}")
            seen.add(coord)
        for row in rows:
            coord = row.split("=", 1)[0]
            if re.search(r"SNAPSHOT|latest[.]|:\+|:\[|:\(", coord, re.IGNORECASE):
                dynamic_hits.append(f"{rel}:{coord}")
    check("lockfiles-nonempty", all(count > 0 for count in lock_counts.values()), str(lock_counts))
    check("no-dynamic-locked-selector", not dynamic_hits, f"hits={dynamic_hits}")
    check("no-duplicate-locked-coordinate", not duplicate_coords, f"duplicates={duplicate_coords}")
    check("legacy-junit-bom-removed", not any("org.junit:junit-bom:5.6.3" in row for row in all_rows), "5.6.3 absent")
    check("junit-bom-5.10.2-locked", any("org.junit:junit-bom:5.10.2" in row for row in all_rows), "5.10.2 present")
    check("junit-platform-launcher-1.10.2-locked", any("org.junit.platform:junit-platform-launcher:1.10.2" in row for row in all_rows), "1.10.2 present")

    try:
        model, missing_sha, multiple_sha = canonical_verification_model(VERIFICATION_PATH)
        canonical_sha = canonical_digest(model)
        bad_sha_values = [
            (row["group"], row["name"], row["version"], row["artifact"], value)
            for row in model["artifacts"]
            for value in row["sha256"]
            if not HEX64.fullmatch(value)
        ]
        check("verify-metadata-enabled", model["verifyMetadata"] == "true", str(model["verifyMetadata"]))
        check("verification-artifacts-present", len(model["artifacts"]) > 0, f"count={len(model['artifacts'])}")
        check("all-verification-artifacts-sha256", not missing_sha and not bad_sha_values, f"missing={missing_sha[:10]} bad={bad_sha_values[:10]}")
        check("no-multiple-distinct-artifact-sha256", not multiple_sha, f"multiple={multiple_sha[:10]}")
        check("canonical-verification-digest", canonical_sha == str(convergence.get("canonicalVerificationSha256", "")).lower(), f"actual={canonical_sha} expected={convergence.get('canonicalVerificationSha256')}")
    except Exception as exc:
        model = {"verifyMetadata": None, "verifySignatures": None, "artifacts": []}
        canonical_sha = None
        check("verification-metadata-parse", False, str(exc))

    check("review-schema", review.get("schemaVersion") == 1, f"schema={review.get('schemaVersion')}")
    check("review-status-accepted", review.get("status") == "DEPENDENCY_TRUST_ACCEPTED", str(review.get("status")))
    check("review-candidate-sha", str(review.get("candidateSha256", "")).lower() == sha256(CANDIDATE_PATH), f"review={review.get('candidateSha256')} actual={sha256(CANDIDATE_PATH)}")
    check("review-source-sha", str(review.get("candidateSourceGitSha", "")).lower() == source_sha, f"review={review.get('candidateSourceGitSha')} candidate={source_sha}")
    check("review-workflow-run", str(review.get("workflowRunId")) == str(candidate.get("workflowRunId")), f"review={review.get('workflowRunId')} candidate={candidate.get('workflowRunId')}")
    check("review-output-binding", review.get("outputsSha256") == candidate_outputs, "review outputs match candidate outputs")
    check("review-canonical-binding", str(review.get("canonicalVerificationSha256", "")).lower() == str(convergence.get("canonicalVerificationSha256", "")).lower(), "review canonical digest matches candidate")
    artifact_digest = str(review.get("workflowArtifact", {}).get("digest", "")).lower()
    check("review-artifact-digest-recorded", bool(re.fullmatch(r"sha256:[0-9a-f]{64}", artifact_digest)), artifact_digest)
    check("review-no-unresolved-findings", review.get("unresolvedFindings") == [], str(review.get("unresolvedFindings")))

    audit = review.get("audit", {})
    check("review-lock-count-kernel", audit.get("kernelLockRows") == lock_counts.get("toolbox-kernel/gradle.lockfile"), f"review={audit.get('kernelLockRows')} actual={lock_counts.get('toolbox-kernel/gradle.lockfile')}")
    check("review-lock-count-app", audit.get("appLockRows") == lock_counts.get("toolbox-app/gradle.lockfile"), f"review={audit.get('appLockRows')} actual={lock_counts.get('toolbox-app/gradle.lockfile')}")
    check("review-metadata-artifact-count", audit.get("verificationArtifacts") == len(model.get("artifacts", [])), f"review={audit.get('verificationArtifacts')} actual={len(model.get('artifacts', []))}")
    check("review-verification-mode", audit.get("verifyMetadata") is True and audit.get("verifySignatures") is False, f"review={audit.get('verifyMetadata')}/{audit.get('verifySignatures')} actual={model.get('verifyMetadata')}/{model.get('verifySignatures')}")
    check("review-independent-convergence", audit.get("independentCleanReplicas") == 2 and audit.get("lockfilesByteIdentical") is True and audit.get("verificationMetadataCanonicalIdentical") is True, str(audit))
    check("review-structural-findings-zero", all(audit.get(name) == 0 for name in ("sha256SumsFailures", "dynamicLockSelectors", "duplicateLockedCoordinates", "legacyJunitBom563Rows", "verificationArtifactsWithoutSha256", "verificationArtifactsWithMultipleDistinctSha256")), str(audit))
    candidate_commit_changed_count = audit.get("candidateCommitChangedFileCount")
    check(
        "review-commit-scope",
        isinstance(candidate_commit_changed_count, int)
        and 1 <= candidate_commit_changed_count <= 4
        and audit.get("candidateCommitOnlyAllowedOutputs") is True,
        str(audit),
    )
    check("review-repository-policy", audit.get("repositoryPolicyReviewed") is True, str(audit.get("repositoryPolicyReviewed")))

    return finish(candidate, review)


if __name__ == "__main__":
    raise SystemExit(main())

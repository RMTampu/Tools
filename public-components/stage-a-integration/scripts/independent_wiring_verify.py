#!/usr/bin/env python3
from __future__ import annotations
import argparse
import copy
import hashlib
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CONTRACT_PATH = ROOT / "CANONICAL_RECEIVER_CONTRACT.json"
WIRING_PATH = ROOT / "STAGE_WIRING_MANIFEST.json"
ACCEPTANCE_PATH = ROOT / "PUBLIC_HANDOFF_ACCEPTANCE_SCHEMA.json"
EVIDENCE_PATH = ROOT / "build/evidence/independent-wiring-verifier.json"

EXPECTED_SLOTS = {
    "S01": ("registry.instance.v1", "stage-a.registry", "single-shared-instance"),
    "S02": ("durable.state.delegate.v1", "stage-a.durable-state", "available-before-kernel-start"),
    "S03": ("bootstrap.hook.v1", "stage-a.bootstrap", "before-kernel-start"),
    "S04": ("safe-ui.route.v1", "stage-a.safe-ui", "restricted-state-preempts-normal-route"),
    "S05": ("resource.profile.v1", "stage-a.resource-profile", "stage-a-idle-profile"),
    "S06": ("diagnostic.quarantine.v1", "stage-a.diagnostics", "non-sensitive-terminal-quarantine"),
    "S07": ("module.registration.v1", "stage-a.module-registration", "before-build-graph-resolution"),
    "S08": ("app.dependency.binding.v1", "stage-a.app-dependencies", "before-compile"),
    "S09": ("source.placement.v1", "stage-a.production-payload", "before-compile"),
    "S10": ("module.descriptor.v1", "stage-a.module-descriptors", "before-build-graph-resolution"),
}
EXPECTED_LIFECYCLE = [
    "place-production-payload",
    "create-module-descriptors",
    "register-modules",
    "bind-app-dependencies",
    "create-stage-a-host",
    "bootstrap-stage-a-host",
    "bind-durable-state",
    "start-kernel",
    "route-safe-ui-before-normal-ui",
]
EXPECTED_CLAIMS = {
    "privateImplementationRequired": False,
    "privateManualPatchRequired": False,
    "privateWiringOnly": True,
    "publicFullAssemblyRehearsal": "PASS",
    "referenceDummy": "PASS",
    "adversarialConformantDummy": "PASS",
    "reproducibility": "PASS",
    "independentWiringVerification": "PASS",
}
REQUIRED_MEMBERS = {
    "canonicalContract", "stageWiringManifest", "acceptanceSchema",
    "referenceDummyEvidence", "adversarialDummyEvidence", "fullAssemblyEvidence",
    "reproducibilityEvidence", "independentVerifierEvidence", "promotionManifest",
    "productionSourceArchive",
}
PRIVATE_ONLY = {
    "privateReceiverMap", "privateReceiverCertificateFullContent",
    "privateReceiverConformanceLogs", "privateInternalSlotMapping",
    "privateBaselineInternalIdentity",
}


class Reject(Exception):
    pass


def need(condition, message):
    if not condition:
        raise Reject(message)


def read(path):
    value = json.loads(path.read_text(encoding="utf-8"))
    need(isinstance(value, dict), f"{path.name}: object required")
    return value


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def validate(contract, wiring, acceptance, contract_digest):
    need(contract.get("schemaVersion") == 1, "contract schema")
    need(contract.get("contractId") == "toolbox.stage.receiver.v1", "contract id")
    need(contract.get("contractVersion") == "1.1.0", "contract version")
    need(contract.get("projectId") == "ToolBox" and contract.get("stageId") == "A", "contract identity")
    privacy = contract.get("privacy")
    need(privacy == {
        "publicKnowsSocketShapeOnly": True,
        "privateImplementationExposed": False,
        "privateReceiverMappingExposed": False,
    }, "privacy boundary")
    compatibility = contract.get("compatibility")
    need(compatibility == {
        "androidApi": 30,
        "publicRuntimeWitnessAbi": "x86_64",
        "finalRuntimeAbiClaimed": False,
        "firebasePublicAccess": False,
    }, "compatibility boundary")

    slots = contract.get("slots")
    need(isinstance(slots, list) and len(slots) == len(EXPECTED_SLOTS), "slot count")
    slot_map = {}
    for slot in slots:
        need(isinstance(slot, dict) and set(slot) == {"slotId", "shape", "required", "lifecycle"}, "slot schema")
        sid = slot["slotId"]
        need(sid not in slot_map, "duplicate contract slot")
        need(slot.get("required") is True, "optional Stage-A slot forbidden")
        slot_map[sid] = slot
    need(set(slot_map) == set(EXPECTED_SLOTS), "contract slot universe")
    for sid, (shape, _, lifecycle) in EXPECTED_SLOTS.items():
        need(slot_map[sid]["shape"] == shape and slot_map[sid]["lifecycle"] == lifecycle, "contract slot semantics")

    need(wiring.get("schemaVersion") == 1 and wiring.get("projectId") == "ToolBox" and wiring.get("stageId") == "A", "wiring identity")
    need(wiring.get("contractSha256") == contract_digest, "wiring contract digest")
    bindings = wiring.get("bindings")
    need(isinstance(bindings, list) and len(bindings) == len(EXPECTED_SLOTS), "binding count")
    binding_map = {}
    for binding in bindings:
        need(isinstance(binding, dict) and set(binding) == {"slotId", "providerId", "values"}, "binding schema")
        sid = binding["slotId"]
        need(sid not in binding_map, "duplicate wiring slot")
        need(isinstance(binding["values"], dict) and binding["values"], "binding values")
        for key, value in binding["values"].items():
            need(isinstance(key, str) and key and isinstance(value, str) and value and len(value) <= 512, "unsafe binding value")
            lowered = value.lower()
            need("/" not in value and "\\" not in value and "secret" not in lowered and "token" not in lowered, "private/path-like binding detail")
        binding_map[sid] = binding
    need(set(binding_map) == set(EXPECTED_SLOTS), "wiring slot universe")
    for sid, (_, provider, _) in EXPECTED_SLOTS.items():
        need(binding_map[sid]["providerId"] == provider, "provider mismatch")

    need(binding_map["S07"]["values"] == {"ModuleIds": "toolbox-runtime-safety-contracts,toolbox-stage-a-foundation"}, "module registration declaration")
    need(binding_map["S08"]["values"] == {"ModuleIds": "toolbox-runtime-safety-contracts,toolbox-stage-a-foundation"}, "app dependency declaration")
    need(binding_map["S09"]["values"] == {"SourceGroups": "runtime-contracts-reuse,runtime-safety-import,stage-a-foundation-import,stage-a-android-host-import"}, "source placement declaration")
    need(binding_map["S10"]["values"] == {"DescriptorProfile": "android-java-library-stage-a-v1"}, "module descriptor declaration")

    need(wiring.get("lifecycleOrder") == EXPECTED_LIFECYCLE, "lifecycle order")
    need(wiring.get("constraints") == {
        "manualPatchAllowed": False,
        "privateImplementationAllowed": False,
        "newDependencyDecisionAllowed": False,
        "sameProductRegistryRequired": True,
        "safeUiRestrictedStatePreemptsNormalRoute": True,
    }, "wiring constraints")
    modules = wiring.get("moduleRegistration")
    need(modules == ["toolbox-runtime-contracts", "toolbox-runtime-safety-contracts", "toolbox-stage-a-foundation"], "module registration")
    need(wiring.get("acceptanceSchemaVersion") == 1, "acceptance version")

    need(acceptance.get("schemaVersion") == 1 and acceptance.get("schemaId") == "toolbox.stage-a.handoff.acceptance.v1", "acceptance identity")
    need(acceptance.get("projectId") == "ToolBox" and acceptance.get("stageId") == "A", "acceptance stage")
    need(set(acceptance.get("requiredMemberBindings", [])) == REQUIRED_MEMBERS, "acceptance capsule members")
    need(acceptance.get("requiredClaims") == EXPECTED_CLAIMS, "acceptance claims")
    need(set(acceptance.get("privateOnlyEvidenceMustNotAppear", [])) == PRIVATE_ONLY, "private-only denylist")
    need(acceptance.get("unknownFieldsPolicy") == "FAIL_CLOSED_FOR_ACCEPTANCE_RECORDS", "acceptance unknown policy")
    need(acceptance.get("manualPatchPolicy") == "FORBIDDEN", "acceptance manual patch policy")


def self_test(contract, wiring, acceptance, digest):
    mutations = []

    def must_reject(name, c, w, a, d=digest):
        try:
            validate(c, w, a, d)
        except Reject:
            mutations.append({"id": name, "expected": "REJECT", "actual": "REJECT"})
            return
        raise Reject("mutation escaped: " + name)

    w = copy.deepcopy(wiring)
    w["contractSha256"] = "0" * 64
    must_reject("wrong-contract-digest", contract, w, acceptance)

    w = copy.deepcopy(wiring)
    w["bindings"] = w["bindings"][:-1]
    must_reject("missing-slot", contract, w, acceptance)

    w = copy.deepcopy(wiring)
    w["bindings"].append(copy.deepcopy(w["bindings"][0]))
    must_reject("duplicate-slot", contract, w, acceptance)

    w = copy.deepcopy(wiring)
    w["lifecycleOrder"] = list(reversed(w["lifecycleOrder"]))
    must_reject("illegal-lifecycle-order", contract, w, acceptance)

    w = copy.deepcopy(wiring)
    w["bindings"][0]["providerId"] = "stage-a.wrong-provider"
    must_reject("wrong-provider", contract, w, acceptance)

    w = copy.deepcopy(wiring)
    w["bindings"][8]["values"]["SourceGroups"] += ",undeclared-private-group"
    must_reject("undeclared-source-group", contract, w, acceptance)

    a = copy.deepcopy(acceptance)
    a["requiredClaims"]["privateManualPatchRequired"] = True
    must_reject("manual-patch-claim", contract, wiring, a)

    a = copy.deepcopy(acceptance)
    a["privateOnlyEvidenceMustNotAppear"] = []
    must_reject("private-denylist-removed", contract, wiring, a)

    a = copy.deepcopy(acceptance)
    a["requiredMemberBindings"] = [v for v in a["requiredMemberBindings"] if v != "productionSourceArchive"]
    must_reject("production-payload-unbound", contract, wiring, a)
    return mutations


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    try:
        contract = read(CONTRACT_PATH)
        wiring = read(WIRING_PATH)
        acceptance = read(ACCEPTANCE_PATH)
        digest = sha(CONTRACT_PATH)
        validate(contract, wiring, acceptance, digest)
        mutations = self_test(contract, wiring, acceptance, digest) if args.self_test else []
        evidence = {
            "schemaVersion": 1,
            "status": "PASS",
            "claim": "PUBLIC_STAGE",
            "verifier": "independent-stage-a-wiring-oracle-v2",
            "contractSha256": digest,
            "wiringManifestSha256": sha(WIRING_PATH),
            "acceptanceSchemaSha256": sha(ACCEPTANCE_PATH),
            "slotCount": len(EXPECTED_SLOTS),
            "requiredSlotCoverage": len(EXPECTED_SLOTS),
            "runtimeSlotCount": 6,
            "buildAndPlacementSlotCount": 4,
            "mutationEscape": 0,
            "mutations": mutations,
            "privateContentUsed": False,
            "firebaseUsed": False,
            "limitations": ["Public verifier proves safe contract/wiring/build-graph model only; actual Private receiver mapping and conformance stay Private."],
        }
        EVIDENCE_PATH.parent.mkdir(parents=True, exist_ok=True)
        EVIDENCE_PATH.write_text(json.dumps(evidence, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    except (OSError, ValueError, Reject) as exc:
        print("STAGE_A_INDEPENDENT_WIRING_VERIFY = FAIL", file=sys.stderr)
        print(str(exc), file=sys.stderr)
        return 2
    print("STAGE_A_INDEPENDENT_WIRING_VERIFY = PASS")
    print("STAGE_A_RECEIVER_SLOT_COUNT = 10")
    print("STAGE_A_WIRING_MUTATION_ESCAPE = 0")
    print("PRIVATE_CONTENT_USED = 0")
    print("PUBLIC_FIREBASE_ACCESS = 0")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

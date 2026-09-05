#!/usr/bin/env python3
import json
import xml.etree.ElementTree as ET
from pathlib import Path

root = Path("app/build/test-results/testDebugUnitTest")
target = root / "TEST-com.toolbox.tools.product.ProductAcceptanceMatrixTest.xml"
if not target.is_file():
    raise SystemExit("PRODUCT_BEHAVIOR_TEST_XML_MISSING")

suite = ET.parse(target).getroot()
tests = int(suite.attrib.get("tests", "0"))
failures = int(suite.attrib.get("failures", "0"))
errors = int(suite.attrib.get("errors", "0"))
skipped = int(suite.attrib.get("skipped", "0"))

if tests < 4:
    raise SystemExit("PRODUCT_BEHAVIOR_TEST_COUNT_TOO_LOW")
if failures or errors or skipped:
    raise SystemExit(
        f"PRODUCT_BEHAVIOR_GATE_FAILED failures={failures} errors={errors} skipped={skipped}"
    )

case_names = sorted(
    case.attrib.get("name", "")
    for case in suite.findall("testcase")
)
required = {
    "all135DesignSectionsRequireBehaviorAndPass",
    "completionServicesCoverPreviouslyMissingDomains",
    "deepContractsCloseFormerMetadataOnlyGaps",
    "behaviorGateIsRepeatable",
}
if not required.issubset(set(case_names)):
    raise SystemExit("PRODUCT_BEHAVIOR_REQUIRED_TESTS_MISSING")

production_target = root / "TEST-com.toolbox.tools.product.ProductProductionContractsTest.xml"
if not production_target.is_file():
    raise SystemExit("PRODUCT_PRODUCTION_CONTRACT_TEST_XML_MISSING")
production_suite = ET.parse(production_target).getroot()
production_tests = int(production_suite.attrib.get("tests", "0"))
production_failures = int(production_suite.attrib.get("failures", "0"))
production_errors = int(production_suite.attrib.get("errors", "0"))
production_skipped = int(production_suite.attrib.get("skipped", "0"))
if production_tests < 10:
    raise SystemExit("PRODUCT_PRODUCTION_CONTRACT_TEST_COUNT_TOO_LOW")
if production_failures or production_errors or production_skipped:
    raise SystemExit("PRODUCT_PRODUCTION_CONTRACT_GATE_FAILED")

critical_classes = [
    "com.toolbox.tools.product.MaximalProductionClosureTest",
    "com.toolbox.tools.core.FileProjectStoreTest",
    "com.toolbox.tools.core.ProjectManagerTest",
    "com.toolbox.tools.delivery.RemotePatchVerifierTest",
    "com.toolbox.tools.delivery.SafePatchManagerTest",
    "com.toolbox.tools.editor.VisualEditorSessionTest",
    "com.toolbox.tools.library.AssetLibraryTest",
    "com.toolbox.tools.live.LiveSessionManagerTest",
    "com.toolbox.tools.repair.HealthRecoveryTest",
    "com.toolbox.tools.repair.RepairSessionManagerTest",
    "com.toolbox.tools.runtime.DataBindingTest",
    "com.toolbox.tools.runtime.FlowGraphTest",
    "com.toolbox.tools.runtime.NavigationActionTest",
]
critical_evidence = {}
for class_name in critical_classes:
    file = root / f"TEST-{class_name}.xml"
    if not file.is_file():
        raise SystemExit(f"CRITICAL_TEST_XML_MISSING:{class_name}")
    item = ET.parse(file).getroot()
    summary = {
        "tests": int(item.attrib.get("tests", "0")),
        "failures": int(item.attrib.get("failures", "0")),
        "errors": int(item.attrib.get("errors", "0")),
        "skipped": int(item.attrib.get("skipped", "0")),
    }
    if summary["tests"] < 1 or any(
        summary[key] for key in ["failures", "errors", "skipped"]
    ):
        raise SystemExit(f"CRITICAL_TEST_FAILED:{class_name}")
    critical_evidence[class_name] = summary

evidence = {
    "schemaVersion": 2,
    "gate": "PRODUCT_BEHAVIOR_135",
    "status": "PASS",
    "designSections": {
        "required": 135,
        "passed": 135,
        "failed": 0,
        "unknown": 0,
        "skipped": 0,
    },
    "junit": {
        "acceptance": {
            "testClass": "com.toolbox.tools.product.ProductAcceptanceMatrixTest",
            "tests": tests,
            "failures": failures,
            "errors": errors,
            "skipped": skipped,
            "cases": case_names,
        },
        "productionContracts": {
            "testClass": "com.toolbox.tools.product.ProductProductionContractsTest",
            "tests": production_tests,
            "failures": production_failures,
            "errors": production_errors,
            "skipped": production_skipped,
        },
        "criticalSubsystems": critical_evidence,
    },
    "rule": "PASS berasal dari test behavior; keberadaan file/class saja tidak cukup.",
}
out = Path("app/build/assurance/product-behavior-135-evidence.json")
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(json.dumps(evidence, indent=2, sort_keys=True) + "\n", encoding="utf-8")
print("PRODUCT_BEHAVIOR_135=PASS")
print("DESIGN_SECTIONS=135/135")
print("UNKNOWN=0")
print("SKIPPED=0")

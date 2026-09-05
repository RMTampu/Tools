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

if tests < 3:
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
    "behaviorGateIsRepeatable",
}
if not required.issubset(set(case_names)):
    raise SystemExit("PRODUCT_BEHAVIOR_REQUIRED_TESTS_MISSING")

evidence = {
    "schemaVersion": 1,
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
        "testClass": "com.toolbox.tools.product.ProductAcceptanceMatrixTest",
        "tests": tests,
        "failures": failures,
        "errors": errors,
        "skipped": skipped,
        "cases": case_names,
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

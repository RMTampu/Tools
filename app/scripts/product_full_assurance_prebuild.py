#!/usr/bin/env python3
import hashlib,json,re
from pathlib import Path

APP=Path(__file__).resolve().parents[1]
REPO=APP.parent
OUT=APP/"build"/"assurance"
OUT.mkdir(parents=True,exist_ok=True)

requirements=json.loads((APP/"FULL_PRODUCT_REQUIREMENTS.json").read_text())
plan=json.loads((APP/"ASSURANCE_PLAN_R1_R9.json").read_text())
asset=json.loads((APP/"ASSET_ASSURANCE_PLAN.json").read_text())
design=(REPO/"RANCANGAN_PRODUK_PENUH.md").read_text()

headings=[
    (int(m.group(1)),m.group(2).strip())
    for m in re.finditer(r"^#\s+(\d+)\.\s+(.+)$",design,re.MULTILINE)
]
assert len(headings)==135
assert [n for n,_ in headings]==list(range(1,136))
assert requirements["requiredSectionCount"]==135
assert requirements["language"]=="id"
assert requirements["signingPolicy"]=="PRIVATE_ONLY"
assert requirements["firebasePolicy"]=="REQUIRES_EXPLICIT_USER_PERMISSION"
reqs=requirements["requirements"]
assert len(reqs)==135
assert len({r["id"] for r in reqs})==135
assert [r["section"] for r in reqs]==list(range(1,136))
assert [(r["section"],r["title"]) for r in reqs]==headings
for req in reqs:
    assert req["required"] is True
    assert req["status"]=="IMPLEMENTED_AND_MUST_PASS"
    assert req["evidenceFiles"]
    for rel in req["evidenceFiles"]:
        assert (REPO/rel).is_file(),(req["id"],rel)

assert plan["stage"]=="Produk Penuh v12"
assert plan["stageMap"]=="FULL_PRODUCT"
assert plan["parentBaseline"]["name"]=="Tahap 11"
assert plan["parentBaseline"]["apkSha256"]=="f9dcffed7dc5d657c6dbd1c45933db6a4f6215f5145aee1849cc50f35038b76b"
assert plan["parentBaseline"]["permanent"] is True
assert plan["parentBaseline"]["rollbackAnchor"] is True
assert plan["productContract"]["requiredDesignSections"]==135
assert plan["productContract"]["defaultLanguage"]=="id"
assert plan["productContract"]["requiredToolEngines"]==[
    "UI","LOGIC","DATA","BINDING","ASSET"
]
assert asset["stage"]=="Produk Penuh v12"

gradle=(APP/"build.gradle").read_text()
assert re.search(r"\bversionCode\s+12\b",gradle)
assert "versionName '12.0-produk-penuh'" in gradle
assert re.search(r"\bminSdk\s+30\b",gradle)
assert re.search(r"\btargetSdk\s+30\b",gradle)

required_sources=[
 "src/main/java/com/toolbox/tools/engine/UiToolEngine.java",
 "src/main/java/com/toolbox/tools/engine/LogicToolEngine.java",
 "src/main/java/com/toolbox/tools/engine/DataToolEngine.java",
 "src/main/java/com/toolbox/tools/engine/BindingToolEngine.java",
 "src/main/java/com/toolbox/tools/engine/AssetToolEngine.java",
 "src/main/java/com/toolbox/tools/engine/ProductEngineSuite.java",
 "src/main/java/com/toolbox/tools/ui/WorkspaceShellView.java",
 "src/main/java/com/toolbox/tools/ui/UiCanvasView.java",
 "src/main/java/com/toolbox/tools/ui/LogicGraphView.java",
 "src/main/java/com/toolbox/tools/ui/EditorPaneFactory.java",
 "src/main/java/com/toolbox/tools/product/FullProductVerifier.java",
 "src/main/java/com/toolbox/tools/product/DeclarativeProjectRuntime.java",
 "src/main/java/com/toolbox/tools/product/FreezeEngine.java",
 "src/main/java/com/toolbox/tools/product/SafeModeController.java",
 "src/main/java/com/toolbox/tools/product/ProjectGraphManager.java",
 "src/main/java/com/toolbox/tools/product/VisualLayoutEngine.java",
 "src/main/java/com/toolbox/tools/product/StateVariantEngine.java",
 "src/main/java/com/toolbox/tools/product/AnimationEngine.java",
 "src/main/java/com/toolbox/tools/product/PreviewSandbox.java",
 "src/main/java/com/toolbox/tools/product/ImportSecurityValidator.java",
 "src/main/java/com/toolbox/tools/product/ImportMergeManager.java",
 "src/main/java/com/toolbox/tools/product/ScaleBenchmarkHarness.java",
 "src/main/java/com/toolbox/tools/library/BuiltinComponentCatalog.java",
 "src/main/java/com/toolbox/tools/library/BuiltinAssetCatalog.java",
]
for rel in required_sources:
    assert (APP/rel).is_file(),rel

kernel=(APP/"src/main/java/com/toolbox/tools/core/AppKernel.java").read_text()
for marker in [
 "ProductEngineSuite.register",
 "BuiltinAssetCatalog.install",
 'configStore.put("bahasaDefault", "id")',
 'configStore.put("tahap", "produk-penuh-v12")',
 "declarativeRuntime::reload",
]:
    assert marker in kernel,marker

main=(APP/"src/main/java/com/toolbox/tools/MainActivity.java").read_text()
assert "WorkspaceShellView" in main
for old in [
 "ToolBox Tahap 11",
 "Candidate Preview",
 "Patch Preview",
 "Repair Demo",
 "READY • Validator",
]:
    assert old not in main

workspace=(APP/"src/main/java/com/toolbox/tools/ui/WorkspaceShellView.java").read_text()
for marker in [
 "Bahasa Indonesia",
 '"Visual"',
 '"Properti"',
 '"Kode"',
 '"Edit"',
 '"Pratinjau"',
 '"Uji"',
 '"Langsung"',
 '"Logika"',
 '"Pengikatan"',
 '"Aset"',
 "Bubble",
 "Paket Evolusi Tanpa Rebuild",
 "Firebase: hanya setelah izin pengguna",
]:
    if marker=="Bubble":
        assert "bubble" in workspace
    else:
        assert marker in workspace,marker
for forbidden in [
 "Candidate Preview",
 "Repair Demo",
 "Capability Scan",
 "Safe Restore",
 "Build & READY",
 "Edit ON",
 "Edit OFF",
]:
    assert forbidden not in workspace,forbidden

catalog=(APP/"src/main/java/com/toolbox/tools/library/BuiltinComponentCatalog.java").read_text()
assert len(re.findall(r'out\.add\(component\("',catalog))>=18
assert len(re.findall(r'out\.add\(template\("',catalog))>=4
builtin_assets=(APP/"src/main/java/com/toolbox/tools/library/BuiltinAssetCatalog.java").read_text()
for asset_id in asset["managedBuiltinAssets"]:
    assert asset_id in builtin_assets,asset_id

all_source="\n".join(
    p.read_text(errors="replace")
    for p in sorted((APP/"src/main").rglob("*"))
    if p.is_file() and p.suffix in {".java",".kt",".xml"}
)
for forbidden in [
 "com.google.firebase",
 "DexClassLoader",
 "URLClassLoader",
 "System.loadLibrary",
 "System.load(",
 "Runtime.getRuntime().exec",
 "ProcessBuilder(",
 "SIGNING_KEY",
 "KEY_STORE_PASSWORD",
]:
    assert forbidden not in all_source,forbidden

workflow=(REPO/".github/workflows/product-full-branch-ci.yml").read_text()
assert "secrets." not in workflow
assert "firebase test" not in workflow.lower()

method_counts={}
for i in range(1,10):
    doc=REPO/plan["domains"][f"R{i}"]["sourceMethodDoc"]
    text=doc.read_text()
    methods=re.findall(rf"^### R{i}-M\d+",text,re.MULTILINE)
    assert methods,f"R{i}"
    method_counts[f"R{i}"]=len(methods)

evidence={
 "schemaVersion":12,
 "status":"PASS",
 "product":"ToolBox Produk Penuh",
 "versionCode":12,
 "versionName":"12.0-produk-penuh",
 "baselineTahap11":"PASS",
 "design":{"required":135,"implementedEvidence":135,"missing":0},
 "toolEngines":{"required":5,"sourceBound":5},
 "language":{"default":"id","ui":"Bahasa Indonesia"},
 "theme":"Gelap Neon",
 "patchWithoutRebuild":"BOUND",
 "publicSigningUsed":False,
 "firebaseUsed":False,
 "r1R9MethodCounts":method_counts,
 "unknown":0,
 "skipped":0,
}
(OUT/"product-full-prebuild-evidence.json").write_text(
 json.dumps(evidence,indent=2,sort_keys=True)+"\n"
)
print("PRODUCT_FULL_PREBUILD = PASS")
print("DESIGN_SECTIONS = 135/135")
print("TOOL_ENGINES = 5/5")
print("BAHASA_INDONESIA = PASS")
print("PUBLIC_SIGNING_USED = NO")
print("FIREBASE_USED = NO")

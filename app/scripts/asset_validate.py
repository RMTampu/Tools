#!/usr/bin/env python3
import hashlib
import json
import xml.etree.ElementTree as ET
from pathlib import Path

APP=Path(__file__).resolve().parents[1]
REPO=APP.parent
OUT=APP/"build"/"assurance"
OUT.mkdir(parents=True,exist_ok=True)
plan=json.loads((APP/"ASSET_ASSURANCE_PLAN.json").read_text())

expected={item["path"]:item for item in plan["shippedAndroidAssets"]}
actual={}
for base in [APP/"src/main/res",APP/"src/main/assets"]:
    if base.exists():
        for path in sorted(base.rglob("*")):
            if path.is_file():
                actual[path.relative_to(REPO).as_posix()]=path
assert set(actual)==set(expected),(sorted(expected),sorted(actual))

asset_evidence=[]
for rel,item in sorted(expected.items()):
    path=REPO/rel
    digest=hashlib.sha256(path.read_bytes()).hexdigest()
    assert digest==item["sha256"],(rel,digest,item["sha256"])
    assert item["required"] is True
    assert item["assetId"] and item["consumer"] and item["loader"] and item["semantic"]
    asset_evidence.append({
        "assetId":item["assetId"],
        "path":rel,
        "sha256":digest,
        "contract":"PASS"
    })

styles=APP/"src/main/res/values/styles.xml"
root=ET.parse(styles).getroot()
style=None
for child in root.findall("style"):
    if child.attrib.get("name")=="AppTheme":
        style=child
        break
assert style is not None
assert style.attrib.get("parent")=="android:style/Theme.Material.NoActionBar"
font_items=[
    item for item in style.findall("item")
    if item.attrib.get("name")=="android:fontFamily" and (item.text or "").strip()=="sans"
]
assert len(font_items)==1
manifest=(APP/"src/main/AndroidManifest.xml").read_text()
assert 'android:theme="@style/AppTheme"' in manifest

route_files=[
 "src/main/java/com/toolbox/tools/library/AssetDescriptor.java",
 "src/main/java/com/toolbox/tools/library/AssetPayloadValidator.java",
 "src/main/java/com/toolbox/tools/library/AssetRegistry.java",
 "src/main/java/com/toolbox/tools/library/FileAssetStore.java",
 "src/main/java/com/toolbox/tools/library/LibraryManager.java",
]
route_text="\n".join((APP/p).read_text() for p in route_files)
for marker in [
 "StableId.require",
 "sha256",
 "maxBytes",
 "consumerIds",
 "publishReady",
 "DUPLICATE_CANDIDATE",
 "relinkOriginal",
 "originals",
 "cache",
 "ATOMIC_MOVE",
 "ensureChild",
 "ASSET_RUNTIME_TYPE_VALIDATOR_REQUIRED",
]:
    assert marker in route_text,marker

for forbidden in [
 "DexClassLoader",
 "System.loadLibrary",
 "Runtime.getRuntime().exec",
 "ProcessBuilder",
]:
    assert forbidden not in route_text

assert set(plan["managedAssetReadyKinds"])=={
    "RAW","JSON","TEMPLATE_DATA"
}
assert set(plan["managedAssetDraftOnlyKinds"])=={
    "IMAGE","ICON","FONT","AUDIO","VIDEO"
}

evidence={
 "schemaVersion":1,
 "stage":"Tahap 3",
 "status":"ASSET_SAFE_100_DEVELOPMENT_PREBUILD_PASS",
 "expectedAssetCount":len(expected),
 "resolvedAssetCount":len(asset_evidence),
 "unknownAssets":0,
 "missingRequiredAssets":0,
 "pathAmbiguity":0,
 "routeProof":"PASS",
 "managedReadyKinds":sorted(plan["managedAssetReadyKinds"]),
 "managedDraftOnlyKinds":sorted(plan["managedAssetDraftOnlyKinds"]),
 "shippedAssets":asset_evidence,
 "finalSignedRuntimeClaimed":False
}
(OUT/"tahap3-asset-prebuild-evidence.json").write_text(
    json.dumps(evidence,indent=2,sort_keys=True)+"\n"
)
print("TAHAP_3_ASSET_PREBUILD = PASS")
print("EXPECTED_ASSET_SET = CLOSED")
print("ASSET_ROUTE_PROOF = PASS")

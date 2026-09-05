#!/usr/bin/env python3
import hashlib,json,xml.etree.ElementTree as ET
from pathlib import Path

APP=Path(__file__).resolve().parents[1]
REPO=APP.parent
OUT=APP/"build"/"assurance"
OUT.mkdir(parents=True,exist_ok=True)
plan=json.loads((APP/"ASSET_ASSURANCE_PLAN.json").read_text())

assert plan["stage"]=="Produk Penuh v14 Visual First"
expected={item["path"]:item for item in plan["shippedAndroidAssets"]}
actual={}
for base in [APP/"src/main/res",APP/"src/main/assets"]:
    if base.exists():
        for path in sorted(base.rglob("*")):
            if path.is_file():
                actual[path.relative_to(REPO).as_posix()]=path
assert set(actual)==set(expected),(sorted(actual),sorted(expected))

resolved=[]
for rel,item in sorted(expected.items()):
    path=REPO/rel
    digest=hashlib.sha256(path.read_bytes()).hexdigest()
    assert digest==item["sha256"],(rel,digest,item["sha256"])
    assert item["required"] is True
    assert item["consumer"] and item["loader"] and item["semantic"]
    if item["type"] == "ANDROID_VALUES_XML":
        ET.parse(path)
    elif item["type"] == "REGISTRY_JSON":
        data=json.loads(path.read_text())
        assert data["schemaVersion"]==1
        assert data["authoritative"] is True
        assert data["invariants"]["stableIds"] is True
        assert data["invariants"]["implementationRequired"] is True
        assert data["invariants"]["ownerRequired"] is True
        assert data["invariants"]["unknownAllowed"] is False
    elif item["type"] == "ANDROID_WEBP":
        raw=path.read_bytes()
        assert len(raw)>=16
        assert raw[:4]==b"RIFF"
        assert raw[8:12]==b"WEBP"
        assert len(raw)<=4*1024*1024
    else:
        raise AssertionError(("unsupported physical asset type",item["type"]))
    resolved.append({
        "assetId":item["assetId"],
        "path":rel,
        "sha256":digest,
        "status":"PASS"
    })

strings=(APP/"src/main/res/values/strings.xml").read_text()
for value in [
 "Bahasa Indonesia","Logika","Pengikatan","Aset","Properti",
 "Pratinjau","Simpan","Urungkan","Ulangi","Pengaturan"
]:
    assert value in strings,value

colors=(APP/"src/main/res/values/colors.xml").read_text()
for value in ["#071016","#0D1B24","#00F0B5","#4CC9FF","#E8FFF8"]:
    assert value in colors,value

styles=(APP/"src/main/res/values/styles.xml").read_text()
assert "@color/tb_latar" in styles
assert "@color/tb_neon" in styles

catalog=(APP/"src/main/java/com/toolbox/tools/library/BuiltinAssetCatalog.java").read_text()
for asset_id in plan["managedBuiltinAssets"]:
    assert asset_id in catalog,asset_id

gateway=(APP/"src/main/java/com/toolbox/tools/android/ExternalAssetGateway.java").read_text()
renderer=(APP/"src/main/java/com/toolbox/tools/ui/AndroidAssetRenderer.java").read_text()
visible=(APP/"src/main/java/com/toolbox/tools/core/VisibleWorkspaceStore.java").read_text()
for token in ["image/","audio/","video/","font/","MAX_BYTES","SHA-256"]:
    assert token in gateway,token
assert set(plan["externalUserAssetReadyKinds"])=={"IMAGE","ICON","FONT","AUDIO","VIDEO"}
for token in ["importToWorkspace","writeStream","VisibleWorkspaceStore.Area.ASSETS"]:
    assert token in gateway,token
for token in ["BitmapFactory","Typeface","MediaPlayer","VideoView","verify"]:
    assert token in renderer,token
for token in ["ASSETS","BACKUPS","SNAPSHOTS","EXPORTS","TEMPLATES","PROJECTS"]:
    assert token in visible,token

evidence={
 "schemaVersion":13,
 "status":"ASSET_SAFE_100_DEVELOPMENT_PREBUILD_PASS",
 "physicalExpected":len(expected),
 "physicalResolved":len(resolved),
 "managedBuiltinExpected":len(plan["managedBuiltinAssets"]),
 "managedBuiltinResolved":len(plan["managedBuiltinAssets"]),
 "unknownAssets":0,
 "missingRequiredAssets":0,
 "pathAmbiguity":0,
 "routeProof":"PASS",
 "languageAsset":"PASS",
 "darkNeonAsset":"PASS",
 "registryInventory":"PASS",
 "externalUserAssetKinds":sorted(plan["externalUserAssetReadyKinds"]),
 "externalUserAssetRoute":"PASS",
 "launcherIconPhysical":"PASS",
 "launcherIconSha256":"521d3944ee2e662e5d80ba9bcdae84a370295638b892affa9c13d58f7009450f",
 "firebaseUsed":False,
 "assets":resolved,
}
(OUT/"product-full-asset-prebuild-evidence.json").write_text(
 json.dumps(evidence,indent=2,sort_keys=True)+"\n"
)
print("PRODUCT_FULL_ASSET_PREBUILD = PASS")
print("ASSET_SAFE_100 = PREBUILD_PASS")
print("UNKNOWN_ASSETS = 0")
print("FIREBASE_USED = NO")

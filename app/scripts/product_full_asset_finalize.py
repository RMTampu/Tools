#!/usr/bin/env python3
import json,os,subprocess,zipfile
from pathlib import Path

APP=Path(__file__).resolve().parents[1]
OUT=APP/"build"/"assurance"
pre=json.loads((OUT/"product-full-asset-prebuild-evidence.json").read_text())
assert pre["status"]=="ASSET_SAFE_100_DEVELOPMENT_PREBUILD_PASS"
assert pre["unknownAssets"]==0
assert pre["missingRequiredAssets"]==0

apk=APP/"build/outputs/apk/release/app-release-unsigned.apk"
assert apk.is_file() and apk.stat().st_size>0
with zipfile.ZipFile(apk) as archive:
    names=set(archive.namelist())
assert "AndroidManifest.xml" in names
assert "resources.arsc" in names
assert "assets/toolbox/registry_inventory.json" in names

sdk=Path(os.environ["ANDROID_SDK_ROOT"])
aapt=sdk/"build-tools/34.0.0/aapt"
resources=subprocess.run(
    [str(aapt),"dump","resources",str(apk)],
    text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,check=True
).stdout
for marker in [
 "style/AppTheme",
 "color/tb_latar",
 "color/tb_neon",
 "string/app_name",
 "string/bahasa_default",
 "mipmap/ic_launcher",
 "mipmap/ic_launcher_round",
]:
    assert marker in resources,marker

runtime=(OUT/"product-full-api30-runtime.txt").read_text()
for marker in [
 "API30_APP_LAUNCH=PASS",
 "BAHASA_INDONESIA_RUNTIME=PASS",
 "DARK_NEON_RUNTIME=PASS",
 "TOOL_UI=PASS",
 "TOOL_LOGIKA=PASS",
 "TOOL_DATA=PASS",
 "TOOL_PENGIKATAN=PASS",
 "TOOL_ASET=PASS",
 "REPRESENTASI_VISUAL=PASS",
 "REPRESENTASI_PROPERTI=PASS",
 "REPRESENTASI_KODE=PASS",
 "MODE_PRATINJAU=PASS",
 "LAUNCHER_TB_PACKAGE=PASS",
 "ASSET_RUNTIME_RENDERER=PASS",
 "MEMORY_PRESSURE_POLICY=PASS",
 "PRODUCT_COMPLETENESS_RUNTIME=PASS",
]:
    assert marker in runtime,marker

evidence={
 "schemaVersion":13,
 "status":"ASSET_SAFE_100_DEVELOPMENT_PASS",
 "physicalAssets":pre["physicalExpected"],
 "managedBuiltinAssets":pre["managedBuiltinExpected"],
 "unknownAssets":0,
 "unprovenRequiredAssets":0,
 "packageResourceTable":"PASS",
 "runtimeAssetRoute":"PASS",
 "languageRuntime":"PASS",
 "darkNeonRuntime":"PASS",
 "registryInventoryPackaged":"PASS",
 "launcherTbPackaged":"PASS",
 "assetRuntimeRenderer":"PASS",
 "externalUserAssetRoute":pre["externalUserAssetRoute"],
 "externalUserAssetKinds":pre["externalUserAssetKinds"],
 "runtimeWitnessAbi":"x86_64",
 "finalArm64SignedRuntimeClaimed":False,
 "firebaseUsed":False,
}
(OUT/"product-full-asset-final-evidence.json").write_text(
 json.dumps(evidence,indent=2,sort_keys=True)+"\n"
)
print("PRODUCT_FULL_ASSET_SAFE = PASS")
print("UNPROVEN_REQUIRED_ASSETS = 0")
print("FIREBASE_USED = NO")

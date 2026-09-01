#!/usr/bin/env python3
"""Prevent recurrence of proven-invalid runtime and artifact routes."""
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[2]
static=(ROOT/'.github/workflows/application-safe-100.yml').read_text(encoding='utf-8')
runtime=(ROOT/'.github/workflows/application-runtime-r9.yml').read_text(encoding='utf-8')
errors=[]
if (ROOT/'.github/workflows/android11-arm64-runtime-host-probe.yml').exists(): errors.append('obsolete hosted runtime probe exists')
if '\n  application-runtime-r9:\n' in static: errors.append('static workflow still owns runtime job')
if 'Prepare canonical runtime bundle' not in static or 'path: verification/runtime-bundle/' not in static: errors.append('canonical artifact bundle missing')
if 'candidate/verification/' in runtime: errors.append('wrong nested artifact path reintroduced')
if 'runs-on: [self-hosted, linux, toolbox-android11-arm64-runtime]' not in runtime: errors.append('runtime not bound to qualified self-hosted label')
if 'runtime_environment_gate.py' not in runtime: errors.append('runtime target qualification missing')
job=runtime.split('\n  runtime-r9:\n',1)[1] if '\n  runtime-r9:\n' in runtime else runtime
for token in ('sdkmanager ','avdmanager ','system-images;android-30','-avd toolbox','runs-on: macos-','runs-on: ubuntu-24.04-arm'):
    if token in job: errors.append(f'closed hosted emulator route token: {token}')
for token in (':toolbox-app:assembleDebug',':toolbox-app:assembleRelease'):
    if token in job: errors.append(f'production rebuild in runtime workflow: {token}')
if errors:
    print('RUNTIME_CI_ROUTE_GUARD = NOT_PROVEN',file=sys.stderr)
    for e in errors: print('FAIL '+e,file=sys.stderr)
    raise SystemExit(1)
print('RUNTIME_CI_ROUTE_GUARD = PASS')

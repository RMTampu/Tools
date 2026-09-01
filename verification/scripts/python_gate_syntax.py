#!/usr/bin/env python3
"""Parse every verification Python gate without producing generated files."""
from __future__ import annotations

import ast
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCRIPTS = ROOT / "verification/scripts"
errors: list[str] = []
files = sorted(SCRIPTS.glob("*.py"))

if not files:
    errors.append("no verification Python scripts found")

for path in files:
    try:
        ast.parse(path.read_text(encoding="utf-8"), filename=str(path))
    except (OSError, SyntaxError, UnicodeError) as exc:
        errors.append(f"{path.relative_to(ROOT)}: {exc}")

if errors:
    print("PYTHON_GATE_SYNTAX = NOT_PROVEN", file=sys.stderr)
    for error in errors:
        print("FAIL " + error, file=sys.stderr)
    raise SystemExit(1)

print(f"PYTHON_GATE_SYNTAX = PASS ({len(files)} scripts)")

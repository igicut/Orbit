"""Pokrece sve API testove redom i pise zbirnu tabelu; izlazni kod 1 ako bilo koji padne."""
import json
import os
import subprocess
import sys
from datetime import datetime
from pathlib import Path

# Limit prijave je poslednji, jer potrosi limit za /auth na minut
TESTS = ["test_registration.py", "test_attendance.py", "test_checkin_qr.py", "test_duration.py", "test_images.py",
         "test_upload_failures.py", "test_validation.py", "test_access.py", "test_concurrency.py",
         "test_search.py", "test_search_parse.py", "test_profile.py", "test_blocking.py", "test_tokens.py",
         "test_password_reset.py", "test_auth_rate_limit.py"]
here = Path(__file__).parent
results_dir = here / "results"
results_dir.mkdir(exist_ok=True)
raw_file = results_dir / "results.jsonl"
raw_file.unlink(missing_ok=True)

# Svaki test upisuje svoje provere u isti fajl (common.check)
env = {**os.environ, "ORBIT_RESULTS_FILE": str(raw_file)}

crashed = []
failed = []
for name in TESTS:
    print(f"\n===== {name} =====", flush=True)
    if subprocess.run([sys.executable, str(here / name)], cwd=here, env=env).returncode != 0:
        failed.append(name)

rows = [json.loads(line) for line in raw_file.read_text(encoding="utf-8").splitlines()] if raw_file.exists() else []

# Skripta koja padne pre ijedne FAIL provere je pukla, a ne nasla gresku; i to ide u tabelu
for name in failed:
    suite = Path(name).stem
    if not any(r["suite"] == suite and not r["ok"] for r in rows):
        crashed.append(name)
        rows.append({"suite": suite, "scenario": "the script ran to the end", "expected": "exit code 0",
                     "actual": "stopped with an error, see the console", "ok": False})

try:
    commit = subprocess.run(["git", "rev-parse", "--short", "HEAD"], cwd=here, capture_output=True,
                            text=True).stdout.strip()
    dirty = subprocess.run(["git", "status", "--porcelain"], cwd=here, capture_output=True, text=True).stdout
    commit += " (with uncommitted changes)" if dirty.strip() else ""
except OSError:
    commit = "unknown"

suites = list(dict.fromkeys(r["suite"] for r in rows))
passed_total = sum(r["ok"] for r in rows)

lines = [
    "# API test results",
    "",
    f"- Run: {datetime.now().strftime('%Y-%m-%d %H:%M')}",
    f"- Code: `{commit}`",
    f"- Server: `{os.environ.get('ORBIT_API', 'http://localhost:8080')}`, "
    f"database `{os.environ.get('ORBIT_DB', 'orbit_database')}`",
    f"- **{passed_total} / {len(rows)} checks passed**",
    "",
    "## Summary by suite",
    "",
    "| Suite | Checks | PASS | FAIL |",
    "|---|---|---|---|",
]
for suite in suites:
    own = [r for r in rows if r["suite"] == suite]
    ok = sum(r["ok"] for r in own)
    lines.append(f"| `{suite}` | {len(own)} | {ok} | {len(own) - ok} |")
lines.append(f"| **Total** | **{len(rows)}** | **{passed_total}** | **{len(rows) - passed_total}** |")

for suite in suites:
    lines += ["", f"## `{suite}`", "", "| # | Scenario | Expected | Actual | Result |", "|---|---|---|---|---|"]
    for number, r in enumerate((r for r in rows if r["suite"] == suite), start=1):
        result = "PASS" if r["ok"] else "**FAIL**"
        lines.append(f"| {number} | {r['scenario']} | {r['expected']} | {r['actual']} | {result} |")

(results_dir / "test-results.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
print(f"\nSummary table: {results_dir / 'test-results.md'}")

print("\nAll API tests passed" if not failed else f"\nFailed: {', '.join(failed)}")
sys.exit(1 if failed else 0)

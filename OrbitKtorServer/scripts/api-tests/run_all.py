"""Pokrece sve API testove redom; izlazni kod 1 ako bilo koji padne."""
import subprocess
import sys
from pathlib import Path

# Limit prijave je poslednji, jer potrosi limit za /auth na minut
TESTS = ["test_registration.py", "test_attendance.py", "test_duration.py", "test_images.py",
         "test_auth_rate_limit.py"]
here = Path(__file__).parent

failed = []
for name in TESTS:
    print(f"\n===== {name} =====", flush=True)
    if subprocess.run([sys.executable, str(here / name)], cwd=here).returncode != 0:
        failed.append(name)

print("\nAll API tests passed" if not failed else f"\nFailed: {', '.join(failed)}")
sys.exit(1 if failed else 0)

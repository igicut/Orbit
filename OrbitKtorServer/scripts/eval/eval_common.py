"""Zajednicko za evaluacije 9 i 10: pristup serveru i bazi iz API testova, i podaci o verziji."""
import subprocess
import sys
from datetime import datetime
from pathlib import Path

HERE = Path(__file__).parent
RESULTS = HERE / "results"
RESULTS.mkdir(exist_ok=True)

# Iste funkcije kao API testovi: prijava, pozivi i SQL (MYSQL_PWD iz okruzenja)
sys.path.insert(0, str(HERE.parent / "api-tests"))
from common import API, DB_NAME, call, login, sql  # noqa: E402,F401


def full_id(short):
    """'5eed-001' -> 5eed0002-...-000000000001, 'ca7a-003' -> ca7a0003-...-000000000003"""
    prefix, number = short.split("-")
    n = int(number)
    if prefix == "5eed":
        return f"5eed0002-0000-4000-8000-{n:012d}"
    return f"ca7a{n:04d}-0000-4000-8000-{n:012d}"


def short_id(full):
    prefix = full[:4]
    number = int(full[-12:])
    return f"{prefix}-{number:03d}"


def git(*args):
    return subprocess.run(["git", *args], cwd=HERE, capture_output=True, text=True).stdout.strip()


def provenance(label_file):
    """Kad i nad cim je merenje uradjeno; oznake koje nisu commit-ovane cine rezultat probnim"""
    uncommitted = git("status", "--porcelain", "--", str(label_file))
    labels_commit = git("log", "-1", "--format=%h %ad", "--date=short", "--", str(label_file))
    return {
        "run": datetime.now().strftime("%Y-%m-%d %H:%M"),
        "code": git("rev-parse", "--short", "HEAD") + (" (with uncommitted changes)" if git("status", "--porcelain") else ""),
        "labels": label_file.name,
        "labels_commit": labels_commit or "never committed",
        "labels_frozen": not uncommitted and bool(labels_commit),
        "server": API,
        "database": DB_NAME,
    }


def provenance_lines(info):
    lines = [
        f"- Run: {info['run']}",
        f"- Code: `{info['code']}`",
        f"- Labels: `{info['labels']}`, last commit: {info['labels_commit']}",
        f"- Server: `{info['server']}`, database `{info['database']}`",
    ]
    if not info["labels_frozen"]:
        lines.append("- **TRIAL RUN: the label file has uncommitted changes or was never committed, "
                     "so this result is not an independent check.**")
    return lines


def percent(value):
    return "—" if value is None else f"{value * 100:.1f}%"


def cell(text):
    """Tekst za celiju Markdown tabele"""
    return str(text).replace("|", "/").replace("\n", " ")

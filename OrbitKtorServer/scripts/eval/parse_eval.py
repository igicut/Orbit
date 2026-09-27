"""
Tacka 10: koliko tacno model iz recenice izdvaja filtere (kategorija, cena, vreme, udaljenost, redosled).

Structured output garantuje samo dozvoljene vrednosti; ovde se proverava da li je izabrana prava.
Server mora da radi sa GEMINI_API_KEY (baza nije bitna). Pokretanje:
    python parse_eval.py              pravo merenje; svaka recenica 3 puta
    python parse_eval.py --self-test  samo provera ocenjivanja, bez servera

Sirovi odgovori idu u results/parse-raw.jsonl odmah po prijemu, pa prekinuto merenje
(npr. zbog kvote) nastavlja gde je stalo. Za potpuno novo merenje obrisati taj fajl.
"""
import json
import os
import sys
import time
from collections import Counter

from eval_common import HERE, RESULTS, call, cell, login, percent, provenance, provenance_lines

LABELS = HERE / "parse_labels.json"
RAW = RESULTS / "parse-raw.jsonl"
FIELDS = ["category", "price", "dateWindow", "radius", "sort"]
FIELD_NAMES = {"category": "Category", "price": "Price", "dateWindow": "Time window", "radius": "Distance",
               "sort": "Sort"}
RUNS = 3
# Besplatan Gemini nivo dozvoljava malo zahteva u minuti; pauza izmedju poziva, u sekundama
PAUSE = float(os.environ.get("PARSE_PAUSE", "4"))
RETRY_STATUSES = {429, 502, 503}


# ---- ocenjivanje: cista logika, proverava je --self-test

def expected_for(sentence, field):
    """Polje koje nije navedeno mora da izostane"""
    return sentence["expected"].get(field, [None])


def acceptable(expected, got):
    if isinstance(expected, dict):
        return got not in expected["not"]
    return got in expected


def field_kind(expected):
    """must: mora biti postavljeno; absent: mora da izostane; optional: dozvoljeno i jedno i drugo"""
    if isinstance(expected, dict):
        return "optional"
    if expected == [None]:
        return "absent"
    return "optional" if None in expected else "must"


def score(labels, answers):
    """answers: {(id, run): odgovor servera ili None ako nije stigao}"""
    per_field = {f: Counter() for f in FIELDS}
    per_kind = {}
    errors = []
    consistency = Counter()
    exact_runs = 0
    answered_runs = 0

    for s in labels["sentences"]:
        runs = [answers.get((s["id"], run)) for run in range(RUNS)]
        runs = [r for r in runs if r is not None]
        kind_stats = per_kind.setdefault(s["kind"], Counter())
        for body in runs:
            answered_runs += 1
            all_ok = all(acceptable(expected_for(s, f), body.get(f)) for f in FIELDS)
            exact_runs += all_ok
            kind_stats["runs"] += 1
            kind_stats["exact"] += all_ok

        for f in FIELDS:
            expected = expected_for(s, f)
            kind = field_kind(expected)
            values = [body.get(f) for body in runs]
            if len(set(values)) <= 1:
                consistency[f] += 1
            for got in values:
                ok = acceptable(expected, got)
                per_field[f]["runs"] += 1
                per_field[f]["correct"] += ok
                per_field[f][kind + "_runs"] += 1
                per_field[f][kind + "_correct"] += ok
                if not ok:
                    # Promasaj: trebalo je postaviti, a nije; visak: nije trebalo, a jeste; pogresno: druga vrednost
                    per_field[f]["missed" if got is None else ("extra" if kind == "absent" else "wrong")] += 1
            wrong = Counter(v for v in values if not acceptable(expected, v))
            for value, times in wrong.items():
                errors.append({"id": s["id"], "text": s["text"], "kind": s["kind"], "field": f,
                               "expected": expected, "got": value, "runs": times, "of": len(values)})
    return {"per_field": per_field, "per_kind": per_kind, "errors": errors, "consistency": consistency,
            "exact_runs": exact_runs, "answered_runs": answered_runs, "sentences": len(labels["sentences"])}


def self_test():
    labels = {"sentences": [
        {"id": 1, "kind": "a", "text": "x", "expected": {"category": ["MUSIC"], "dateWindow": ["TODAY"]}},
        {"id": 2, "kind": "b", "text": "y", "expected": {"category": {"not": ["SPORT"]}, "price": ["FREE", None]}},
    ]}
    answers = {(1, 0): {"category": "MUSIC", "dateWindow": "TODAY"},
               (1, 1): {"category": "MUSIC"},
               (1, 2): {"category": "ART", "dateWindow": "TODAY", "sort": "NEAREST"},
               (2, 0): {"category": "SPORT"}, (2, 1): {}, (2, 2): {"price": "FREE"}}
    result = score(labels, answers)
    category = result["per_field"]["category"]
    assert (category["runs"], category["correct"]) == (6, 4), category
    # Dva pogresna: ART umesto MUSIC, i SPORT iako je trazeno "ne sport"
    assert (category["must_runs"], category["must_correct"], category["wrong"]) == (3, 2, 2), category
    date = result["per_field"]["dateWindow"]
    assert (date["must_correct"], date["missed"], date["absent_runs"], date["absent_correct"]) == (2, 1, 3, 3), date
    sort = result["per_field"]["sort"]
    assert (sort["extra"], sort["absent_runs"]) == (1, 6), sort
    assert result["exact_runs"] == 3, result["exact_runs"]
    assert result["consistency"]["dateWindow"] == 1 and result["consistency"]["price"] == 1, result["consistency"]
    assert acceptable({"not": ["SPORT"]}, None) and not acceptable({"not": ["SPORT"]}, "SPORT")
    print("self-test passed")


# ---- merenje

def load_raw():
    answers = {}
    if RAW.exists():
        for line in RAW.read_text(encoding="utf-8").splitlines():
            row = json.loads(line)
            if row["status"] == 200:
                answers[(row["id"], row["run"])] = row["body"]
    return answers


def ask(token, text):
    """Do tri pokusaja kad je Gemini preopterecen ili kvota privremeno potrosena"""
    for attempt in range(3):
        status, body = call("POST", "/search/parse", {"text": text}, token)
        if status not in RETRY_STATUSES:
            return status, body
        wait = 30 * (attempt + 1)
        print(f"  {status}, waiting {wait} s")
        time.sleep(wait)
    return status, body


def collect(token, labels):
    answers = load_raw()
    todo = [(s, run) for s in labels["sentences"] for run in range(RUNS) if (s["id"], run) not in answers]
    print(f"{len(answers)} answers already stored, {len(todo)} to ask")
    for number, (s, run) in enumerate(todo, start=1):
        status, body = ask(token, s["text"])
        with RAW.open("a", encoding="utf-8") as out:
            out.write(json.dumps({"id": s["id"], "run": run, "status": status, "body": body}, ensure_ascii=False) + "\n")
        if status == 200:
            answers[(s["id"], run)] = body
        elif status == 503:
            sys.exit("The server answers 503: GEMINI_API_KEY is not set there.")
        print(f"[{number}/{len(todo)}] #{s['id']} run {run + 1}: {status}")
        time.sleep(PAUSE)
    return answers


def show(value):
    if isinstance(value, dict):
        return "not " + ", ".join(value["not"])
    if isinstance(value, list):
        return " or ".join("—" if v is None else v for v in value)
    return "—" if value is None else value


def report(info, labels, answers, result):
    lines = ["# Filters from a sentence: evaluation (review point 10)", ""] + provenance_lines(info) + [
        f"- Sentences: {result['sentences']}, each asked {RUNS} times; answered: {result['answered_runs']} "
        f"of {result['sentences'] * RUNS}",
        "",
        "A field is correct when the answer is one of the acceptable values in `parse_labels.json` "
        "(for ambiguous sentences several are acceptable). **Recognised** looks only at sentences where the "
        "field had to be set. **Left out correctly** looks only at sentences where it had to be absent. "
        "**Consistent** is the share of sentences where all runs gave the same answer.",
        "",
        "| Field | Correct (all) | Recognised when asked for | Left out correctly | Wrong value | Missed | "
        "Set without being asked | Consistent |",
        "|---|---|---|---|---|---|---|---|",
    ]
    for f in FIELDS:
        c = result["per_field"][f]

        def share(correct, runs):
            return percent(c[correct] / c[runs]) + f" ({c[correct]}/{c[runs]})" if c[runs] else "—"

        lines.append(f"| {FIELD_NAMES[f]} | {share('correct', 'runs')} | {share('must_correct', 'must_runs')} | "
                     f"{share('absent_correct', 'absent_runs')} | {c['wrong']} | {c['missed']} | {c['extra']} | "
                     f"{percent(result['consistency'][f] / result['sentences'])} |")
    exact = result["exact_runs"] / result["answered_runs"] if result["answered_runs"] else None
    lines += ["", f"**All five fields correct in one answer: {percent(exact)} "
                  f"({result['exact_runs']}/{result['answered_runs']})**", "",
              "## By kind of sentence", "", "| Kind | Answers | All fields correct |", "|---|---|---|"]
    for kind, c in sorted(result["per_kind"].items()):
        lines.append(f"| {kind} | {c['runs']} | {percent(c['exact'] / c['runs']) if c['runs'] else '—'} |")

    lines += ["", "## Every wrong answer", "", "| # | Sentence | Kind | Field | Expected | Got | Runs |",
              "|---|---|---|---|---|---|---|"]
    for e in sorted(result["errors"], key=lambda e: (e["id"], e["field"])):
        lines.append(f"| {e['id']} | {cell(e['text'])} | {e['kind']} | {FIELD_NAMES[e['field']]} | "
                     f"{show(e['expected'])} | {show(e['got'])} | {e['runs']}/{e['of']} |")

    lines += ["", "## All answers", "", "| # | Sentence | Answers (category, price, time, distance, sort) |",
              "|---|---|---|"]
    for s in labels["sentences"]:
        runs = [answers.get((s["id"], run)) for run in range(RUNS)]
        shown = "; ".join("no answer" if r is None else ", ".join(show(r.get(f)) for f in FIELDS) for r in runs)
        lines.append(f"| {s['id']} | {cell(s['text'])} | {cell(shown)} |")

    (RESULTS / "parse-eval.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def main():
    if "--self-test" in sys.argv:
        self_test()
        return
    labels = json.loads(LABELS.read_text(encoding="utf-8"))
    info = provenance(LABELS)
    if not info["labels_frozen"]:
        print("WARNING: labels are not committed - this is a trial run, not an independent check")
    token = login("milica")
    answers = collect(token, labels)
    report(info, labels, answers, score(labels, answers))
    print(f"written {RESULTS / 'parse-eval.md'}")


if __name__ == "__main__":
    main()

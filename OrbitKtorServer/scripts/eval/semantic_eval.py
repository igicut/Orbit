"""
Tacka 9: evaluacija semanticke pretrage, odvojeno na skupu za podesavanje i na test skupu.

Server mora da radi nad bazom orbit_eval (schema.sql + seed.sql + events_catalog.sql) sa
GEMINI_API_KEY, a u terminalu ORBIT_DB=orbit_eval i MYSQL_PWD. Pokretanje:
    python semantic_eval.py              pravo merenje
    python semantic_eval.py --self-test  samo provera racunanja metrika, bez servera

Porede se dva sistema nad istim upitima:
  - keyword: samo LIKE nad naslovom i opisom, isti upit kao na serveru (ExposedEventService.search)
  - final:   ono sto server stvarno vrati: pogoci po recima + semanticki dodati
"""
import json
import re
import sys
import time
from urllib.parse import quote

from eval_common import HERE, RESULTS, call, cell, full_id, login, percent, provenance, provenance_lines, short_id, sql

LABELS = HERE / "semantic_labels.json"
RANKING_KT = HERE.parents[1] / "src" / "main" / "kotlin" / "com" / "example" / "orbit" / "service" / "SemanticRanking.kt"
LAT, LNG = 44.8125, 20.4612
EMBEDDING_WAIT_SECONDS = 180


# ---- metrike: cista matematika, proverava je --self-test

def metrics(retrieved, relevant):
    """Precision, recall i F1 za jedan upit; None kad deljenje nema smisla (nista vraceno / nista relevantno)"""
    hits = len(retrieved & relevant)
    false_hits = len(retrieved - relevant)
    lost = len(relevant - retrieved)
    precision = hits / len(retrieved) if retrieved else None
    recall = hits / len(relevant) if relevant else None
    if precision is None or recall is None or precision + recall == 0:
        f1 = None if precision is None or recall is None else 0.0
    else:
        f1 = 2 * precision * recall / (precision + recall)
    return {"returned": len(retrieved), "relevant": len(relevant), "hits": hits, "false_hits": false_hits,
            "lost": lost, "precision": precision, "recall": recall, "f1": f1}


def summarize(rows):
    """
    Macro: prosek po upitu (svaki upit jednako vazan); upit bez vracenih rezultata ima precision 0.
    Micro: zbir pogodaka preko svih upita (veliki upiti vise vuku).
    Racuna se samo nad upitima koji imaju bar jedan relevantan dogadjaj.
    """
    scored = [r for r in rows if r["relevant"] > 0]
    if not scored:
        return None

    def average(key):
        values = [(r[key] if r[key] is not None else 0.0) for r in scored]
        return sum(values) / len(values)

    hits = sum(r["hits"] for r in scored)
    returned = sum(r["returned"] for r in scored)
    relevant = sum(r["relevant"] for r in scored)
    micro_p = hits / returned if returned else 0.0
    micro_r = hits / relevant if relevant else 0.0
    micro_f1 = 2 * micro_p * micro_r / (micro_p + micro_r) if micro_p + micro_r else 0.0
    return {"queries": len(scored), "macro_precision": average("precision"), "macro_recall": average("recall"),
            "macro_f1": average("f1"), "micro_precision": micro_p, "micro_recall": micro_r, "micro_f1": micro_f1,
            "relevant": relevant, "hits": hits, "lost": relevant - hits, "false_hits": returned - hits}


def self_test():
    m = metrics({"a", "b", "x"}, {"a", "b", "c", "d"})
    assert (m["hits"], m["false_hits"], m["lost"]) == (2, 1, 2), m
    assert abs(m["precision"] - 2 / 3) < 1e-9 and m["recall"] == 0.5, m
    assert abs(m["f1"] - (2 * (2 / 3) * 0.5) / (2 / 3 + 0.5)) < 1e-9, m
    assert metrics(set(), {"a"})["precision"] is None and metrics(set(), {"a"})["recall"] == 0.0
    assert metrics({"a"}, set())["recall"] is None
    assert metrics({"x"}, {"a"})["f1"] == 0.0
    total = summarize([metrics({"a"}, {"a", "b"}), metrics(set(), {"c"}), metrics({"z"}, set())])
    assert total["queries"] == 2, total
    assert total["macro_precision"] == 0.5 and total["macro_recall"] == 0.25, total
    assert total["micro_precision"] == 1.0 and abs(total["micro_recall"] - 1 / 3) < 1e-9, total
    assert (total["lost"], total["false_hits"]) == (2, 0), total
    assert full_id("5eed-001") == "5eed0002-0000-4000-8000-000000000001"
    assert full_id("ca7a-003") == "ca7a0003-0000-4000-8000-000000000003" and short_id(full_id("ca7a-056")) == "ca7a-056"
    print("self-test passed")


# ---- priprema: pravila koja cuvaju nezavisnost merenja

def check_thresholds(frozen):
    """Pragovi u kodu moraju biti isti kao kad su oznake pisane, inace test skup nije nezavisan"""
    source = RANKING_KT.read_text(encoding="utf-8")
    for name in ("MIN_LEAD", "RELATIVE_MARGIN", "MAX_RELATED"):
        found = re.search(rf"const val {name} = ([0-9.]+)", source)
        if found is None or float(found.group(1)) != float(frozen[name]):
            sys.exit(f"{name} in SemanticRanking.kt is {found and found.group(1)}, labels were frozen with "
                     f"{frozen[name]}. Changing thresholds after writing the test set breaks its independence.")


def wait_for_embeddings():
    """Server pravi vektore u pozadini posle pokretanja; merenje pre toga bi bilo pogresno"""
    deadline = time.time() + EMBEDDING_WAIT_SECONDS
    while True:
        missing = int(sql("SELECT COUNT(*) FROM events e LEFT JOIN event_embeddings v ON v.event_id = e.id "
                          "WHERE e.visibility = 'PUBLIC' AND v.event_id IS NULL"))
        if missing == 0:
            return
        if time.time() > deadline:
            sys.exit(f"{missing} public events still have no embedding. Is GEMINI_API_KEY set on the server?")
        print(f"waiting for {missing} embeddings…")
        time.sleep(5)


def public_events(token):
    """Naslovi preko API-ja, a ne SQL-a: Windows konzola bi pokvarila cirilicu iz mysql izlaza"""
    status, body = call("GET", f"/events?lat={LAT}&lng={LNG}", token=token)
    if status != 200:
        sys.exit(f"listing the events failed: {status} {body}")
    return {event["id"]: event["title"] for event in body}


def keyword_hits(query):
    """Isti uslov kao ExposedEventService.search: LIKE nad naslovom ili opisom, samo javni"""
    safe = query.strip().replace("\\", "\\\\").replace("'", "''")
    rows = sql(f"SELECT id FROM events WHERE visibility = 'PUBLIC' "
               f"AND (title LIKE '%{safe}%' OR description LIKE '%{safe}%')")
    return set(rows.split()) if rows else set()


def final_results(token, query):
    status, body = call("GET", f"/events?lat={LAT}&lng={LNG}&q={quote(query)}", token=token)
    if status != 200:
        sys.exit(f"search for '{query}' failed: {status} {body}")
    return [event["id"] for event in body], any(event.get("relevance") is not None for event in body)


# ---- merenje

def evaluate(token, queries):
    rows = []
    for q in queries:
        relevant = {full_id(i) for i in q["relevant"]}
        borderline = {full_id(i) for i in q["borderline"]}
        keyword = keyword_hits(q["query"])
        returned, gate_opened = final_results(token, q["query"])
        final = set(returned)
        rows.append({
            "query": q["query"],
            "excluded": bool(q.get("exclude_from_averages")),
            "relevant_ids": sorted(relevant), "borderline_ids": sorted(borderline),
            "keyword_ids": sorted(keyword), "final_ids": returned, "gate_opened": gate_opened,
            "strict": {"keyword": metrics(keyword, relevant), "final": metrics(final, relevant)},
            # Blagi rezim: granicni se broje kao relevantni
            "lenient": {"keyword": metrics(keyword, relevant | borderline),
                        "final": metrics(final, relevant | borderline)},
        })
    return rows


def summary_rows(rows, mode):
    counted = [r for r in rows if not r["excluded"]]
    return {system: summarize([r[mode][system] for r in counted]) for system in ("keyword", "final")}


def report(info, frozen, titles, results):
    def names(ids):
        return ", ".join(f"`{short_id(i)}` {titles.get(i, '?')}" for i in ids) or "—"

    lines = ["# Semantic search evaluation (review point 9)", ""] + provenance_lines(info) + [
        f"- Thresholds (frozen): `MIN_LEAD = {frozen['MIN_LEAD']}`, `RELATIVE_MARGIN = {frozen['RELATIVE_MARGIN']}`, "
        f"`MAX_RELATED = {frozen['MAX_RELATED']}`",
        f"- Corpus: {len(titles)} public events",
        "",
        "**keyword** = only the `LIKE` match on title and description. **final** = what the server returns: "
        "keyword hits plus semantically related events. **Strict** counts borderline events as not relevant, "
        "**lenient** counts them as relevant. Averages use only queries with at least one relevant event.",
        "",
    ]
    for part, title in (("tuning", "Tuning set (the thresholds were chosen on these queries)"),
                        ("test", "Test set (independent check)")):
        rows = results[part]
        lines += [f"## {title}", "", "| Mode | System | Queries | Macro P | Macro R | Macro F1 | Micro P | Micro R | "
                  "Micro F1 | Relevant | Found | Lost | False hits |", "|---|---|---|---|---|---|---|---|---|---|---|---|---|"]
        for mode in ("strict", "lenient"):
            for system, s in summary_rows(rows, mode).items():
                if s is None:
                    continue
                lines.append(f"| {mode} | {system} | {s['queries']} | {percent(s['macro_precision'])} | "
                             f"{percent(s['macro_recall'])} | {percent(s['macro_f1'])} | {percent(s['micro_precision'])} | "
                             f"{percent(s['micro_recall'])} | {percent(s['micro_f1'])} | {s['relevant']} | {s['hits']} | "
                             f"{s['lost']} | {s['false_hits']} |")

        negatives = [r for r in rows if not r["relevant_ids"]]
        if negatives:
            lines += ["", "Queries with no relevant event (the correct answer is an empty list):", "",
                      "| Query | Keyword hits | Final result | Semantic layer opened | Correct |", "|---|---|---|---|---|"]
            for r in negatives:
                note = " (excluded from averages)" if r["excluded"] else ""
                lines.append(f"| {cell(r['query'])}{note} | {len(r['keyword_ids'])} | {len(r['final_ids'])} | "
                             f"{'yes' if r['gate_opened'] else 'no'} | {'yes' if not r['final_ids'] else 'no'} |")

        lines += ["", "Per query, strict mode:", "",
                  "| Query | Relevant | Keyword P / R | Final P / R / F1 | Returned | Lost relevant | False hits |",
                  "|---|---|---|---|---|---|---|"]
        for r in rows:
            if not r["relevant_ids"]:
                continue
            k, f = r["strict"]["keyword"], r["strict"]["final"]
            lost = [i for i in r["relevant_ids"] if i not in r["final_ids"]]
            false_hits = [i for i in r["final_ids"] if i not in r["relevant_ids"]]
            lines.append(f"| {cell(r['query'])} | {f['relevant']} | {percent(k['precision'])} / {percent(k['recall'])} | "
                         f"{percent(f['precision'])} / {percent(f['recall'])} / {percent(f['f1'])} | {f['returned']} | "
                         f"{cell(names(lost))} | {cell(names(false_hits))} |")
        lines.append("")

    (RESULTS / "semantic-eval.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    (RESULTS / "semantic-eval.json").write_text(json.dumps({"provenance": info, "thresholds": frozen, **results},
                                                           ensure_ascii=False, indent=2), encoding="utf-8")


def main():
    if "--self-test" in sys.argv:
        self_test()
        return
    labels = json.loads(LABELS.read_text(encoding="utf-8"))
    frozen = labels["thresholds_frozen"]
    check_thresholds(frozen)
    info = provenance(LABELS)
    if not info["labels_frozen"]:
        print("WARNING: labels are not committed - this is a trial run, not an independent check")

    wait_for_embeddings()
    token = login("milica")
    titles = public_events(token)
    results = {"tuning": evaluate(token, labels["tuning"]), "test": evaluate(token, labels["test"])}
    report(info, frozen, titles, results)
    print(f"written {RESULTS / 'semantic-eval.md'}")


if __name__ == "__main__":
    main()

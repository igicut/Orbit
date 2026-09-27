# Evaluations for review points 9 and 10

| Script | Point | What it measures |
|---|---|---|
| `semantic_eval.py` | 9 | Precision, recall, F1 and lost relevant events of the semantic search, separately on the tuning set and on the independent test set, against a keyword-only baseline |
| `parse_eval.py` | 10 | How often `/search/parse` picks the right category, price, time window, distance and sort, over 60 sentences asked 3 times each |
| `make_review_sheet.py` | — | Rebuilds `LABELS_REVIEW.md` from the label files, with event titles |

The labels are in `semantic_labels.json` and `parse_labels.json`. They are the source of truth.

## The one rule

**Commit the label files before the first run, and never change them after seeing results.**
The commit date is the proof that the expected answers came first. Each report prints the
labels' last commit, and marks the run as a *trial* if the label file has uncommitted changes.
`semantic_eval.py` also refuses to run if the thresholds in `SemanticRanking.kt` differ from
the ones the labels were frozen with.

## Setup (once)

A separate database `orbit_eval`, so the API tests keep their seed-only `orbit_test` and your
own `orbit_database` is not touched. From `OrbitKtorServer/`, in bash:

```bash
export MYSQL_PWD=$DB_PASSWORD
sed 's/orbit_database/orbit_eval/g' db/schema.sql | mysql -u root
git show 27ca613:OrbitKtorServer/db/seed.sql | mysql -u root orbit_eval
git show 31cf624:OrbitKtorServer/db/events_catalog.sql | mysql -u root --default-character-set=utf8mb4 orbit_eval
```

The corpus is every public event after these three scripts: 61 events (9 seed, 52 catalog).
The labels were written against exactly these versions. `seed.sql` comes from commit `27ca613`,
because the later version comments two events out, and one of them (`5eed-003`, the morning run)
is used in the labels. `events_catalog.sql` comes from commit `31cf624`, the commit that froze
the labels. If either file changes, the corpus changes and the labels must be checked again.

## Running

1. Start the server on `orbit_eval`, with `GEMINI_API_KEY` set. In PowerShell:
   ```powershell
   $env:DB_URL = 'r2dbc:mysql://localhost:3306/orbit_eval'; ./gradlew --no-daemon run
   ```
   On start it creates the missing embeddings; `semantic_eval.py` waits until all 61 exist.
2. In another terminal, from `OrbitKtorServer/scripts/eval`:
   ```bash
   export MYSQL_PWD=$DB_PASSWORD ORBIT_DB=orbit_eval
   python semantic_eval.py
   python parse_eval.py
   ```

Results go to `results/`: `semantic-eval.md` and `.json`, `parse-eval.md`, and
`parse-raw.jsonl` (every model answer as received).

`parse_eval.py` makes 180 Gemini calls with a 4-second pause (`PARSE_PAUSE` changes it), so it
takes about 15 minutes. It saves each answer at once, so if it stops (quota, network), running it
again continues where it stopped. Delete `results/parse-raw.jsonl` for a completely new run.

`python semantic_eval.py --self-test` and `python parse_eval.py --self-test` check the metric
code on made-up data, without a server.

## How the numbers are computed

**Point 9.** For every query the server's answer (`final`) and the keyword-only match
(`keyword`, the same `LIKE` the server runs) are compared with the relevant events:

- precision = relevant returned / returned
- recall = relevant returned / all relevant
- F1 = the harmonic mean of the two
- lost = relevant events that were not returned

*Strict* counts borderline events as not relevant; *lenient* counts them as relevant. *Macro*
averages per query and *micro* sums over all queries. Both use only queries with at least one
relevant event. Queries with no relevant event are reported separately: the correct answer
there is an empty list.

**Point 10.** A field is correct when the model's answer is one of the acceptable values. The
report shows, per field:

- how often it was recognised when the sentence asked for it
- how often it was correctly left out when the sentence did not ask for it
- how many answers had a wrong value, missed the field, or set it without being asked
- how consistent the three runs were

It also lists every wrong answer.

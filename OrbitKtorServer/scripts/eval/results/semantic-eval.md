# Semantic search evaluation (review point 9)

- Run: 2026-09-27 19:56
- Code: `31cf624`
- Labels: `semantic_labels.json`, last commit: 31cf624 2026-09-27
- Server: `http://localhost:8080`, database `orbit_eval`
- Thresholds (frozen): `MIN_LEAD = 0.05`, `RELATIVE_MARGIN = 0.03`, `MAX_RELATED = 10`
- Corpus: 61 public events

**keyword** = only the `LIKE` match on title and description. **final** = what the server returns: keyword hits plus semantically related events. **Strict** counts borderline events as not relevant, **lenient** counts them as relevant. Averages use only queries with at least one relevant event.

## Tuning set (the thresholds were chosen on these queries)

| Mode | System | Queries | Macro P | Macro R | Macro F1 | Micro P | Micro R | Micro F1 | Relevant | Found | Lost | False hits |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| strict | keyword | 9 | 44.4% | 9.4% | 15.2% | 80.0% | 5.3% | 10.0% | 75 | 4 | 71 | 1 |
| strict | final | 9 | 78.5% | 36.4% | 45.9% | 73.0% | 36.0% | 48.2% | 75 | 27 | 48 | 10 |
| lenient | keyword | 9 | 55.6% | 9.8% | 16.1% | 100.0% | 5.5% | 10.4% | 91 | 5 | 86 | 0 |
| lenient | final | 9 | 95.5% | 37.9% | 49.9% | 89.2% | 36.3% | 51.6% | 91 | 33 | 58 | 4 |

Queries with no relevant event (the correct answer is an empty list):

| Query | Keyword hits | Final result | Semantic layer opened | Correct |
|---|---|---|---|---|
| xyzzy qwerty | 0 | 0 | no | yes |

Per query, strict mode:

| Query | Relevant | Keyword P / R | Final P / R / F1 | Returned | Lost relevant | False hits |
|---|---|---|---|---|---|---|
| sport | 10 | 100.0% / 10.0% | 81.8% / 90.0% / 85.7% | 11 | `ca7a-021` Biciklistička tura Zemun–Batajnica | `ca7a-022` Zimska šetnja Košutnjakom, `ca7a-020` Zalazak sunca na Avali |
| planinarenje | 4 | 100.0% / 25.0% | 100.0% / 25.0% / 40.0% | 1 | `ca7a-020` Zalazak sunca na Avali, `ca7a-022` Zimska šetnja Košutnjakom, `ca7a-054` Зимняя прогулка по Кошутняку | — |
| muzika | 6 | 100.0% / 16.7% | 50.0% / 16.7% / 25.0% | 2 | `5eed-006` Koncert na Kalemegdanu, `ca7a-004` Jazz nedelja u Zemunu, `ca7a-005` Akustično veče uz Savu, `ca7a-031` Čas gitare u prirodi, `ca7a-042` Christmas Vinyl Night | `ca7a-030` Kviz opšte kulture - specijal |
| programiranje | 7 | — / 0.0% | 100.0% / 57.1% / 72.7% | 4 | `5eed-002` Hakaton na ETF-u, `ca7a-032` Mini Game Jam, `ca7a-038` Open Source Evening | — |
| hrana i pice | 8 | 0.0% / 0.0% | 80.0% / 50.0% / 61.5% | 5 | `5eed-005` Degustacija domaćih vina, `ca7a-040` International Cooking Exchange, `ca7a-044` Workshop: Berliner Brot & Brezeln, `ca7a-051` Мастер-класс по пельменям | `5eed-002` Hakaton na ETF-u |
| trcanje | 3 | 100.0% / 33.3% | 100.0% / 33.3% / 50.0% | 1 | `ca7a-034` Porodični sportski dan, `ca7a-047` Winterlauf am Fluss | — |
| gde mogu da vezbam | 9 | — / 0.0% | 50.0% / 11.1% / 18.2% | 2 | `5eed-007` Planinarenje na Avali, `5eed-008` Turnir u basketu 3x3, `ca7a-006` Turnir u stonom tenisu, `ca7a-007` Noćni basket na Novom Beogradu, `ca7a-021` Biciklistička tura Zemun–Batajnica, `ca7a-034` Porodični sportski dan, `ca7a-039` Sunset Kayaking Session, `ca7a-047` Winterlauf am Fluss | `ca7a-022` Zimska šetnja Košutnjakom |
| Zelim da idem na dogadjaj gde se druzim sa ljudima | 15 | — / 0.0% | 100.0% / 13.3% / 23.5% | 2 | `5eed-001` Kviz veče u Skadarliji, `ca7a-005` Akustično veče uz Savu, `ca7a-025` Board Game Sunday, `ca7a-030` Kviz opšte kulture - specijal, `ca7a-040` International Cooking Exchange, `ca7a-041` Creative Writing Circle, `ca7a-042` Christmas Vinyl Night, `ca7a-043` Deutscher Sprachstammtisch, `ca7a-048` Spieleabend auf Deutsch, `ca7a-049` Русский разговорный клуб, `ca7a-052` Вечер настольных игр, `ca7a-055` Language Exchange: English / Deutsch / Русский, `ca7a-056` Global Trivia Night | — |
| trazim nesto zabavno za vece sa drustvom | 13 | — / 0.0% | 44.4% / 30.8% / 36.4% | 9 | `5eed-006` Koncert na Kalemegdanu, `ca7a-004` Jazz nedelja u Zemunu, `ca7a-007` Noćni basket na Novom Beogradu, `ca7a-015` Veče kratkog filma, `ca7a-030` Kviz opšte kulture - specijal, `ca7a-042` Christmas Vinyl Night, `ca7a-048` Spieleabend auf Deutsch, `ca7a-052` Вечер настольных игр, `ca7a-056` Global Trivia Night | `ca7a-025` Board Game Sunday, `ca7a-023` Speed friending Beograd, `ca7a-024` International Students Hangout, `ca7a-029` Veče astronomije, `ca7a-020` Zalazak sunca na Avali |

## Test set (independent check)

| Mode | System | Queries | Macro P | Macro R | Macro F1 | Micro P | Micro R | Micro F1 | Relevant | Found | Lost | False hits |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| strict | keyword | 20 | 12.5% | 11.0% | 10.0% | 66.7% | 7.0% | 12.7% | 57 | 4 | 53 | 2 |
| strict | final | 20 | 83.9% | 76.6% | 71.0% | 61.7% | 64.9% | 63.2% | 57 | 37 | 20 | 23 |
| lenient | keyword | 22 | 12.5% | 8.5% | 9.0% | 83.3% | 6.0% | 11.2% | 83 | 5 | 78 | 1 |
| lenient | final | 22 | 81.0% | 59.3% | 62.0% | 64.6% | 50.6% | 56.8% | 83 | 42 | 41 | 23 |

Queries with no relevant event (the correct answer is an empty list):

| Query | Keyword hits | Final result | Semantic layer opened | Correct |
|---|---|---|---|---|
| koncert klasicne muzike | 0 | 5 | yes | no |
| joga | 0 | 0 | no | yes |
| pozoriste | 0 | 0 | no | yes |
| nesto gde se ne trosi novac (excluded from averages) | 0 | 0 | no | yes |

Per query, strict mode:

| Query | Relevant | Keyword P / R | Final P / R / F1 | Returned | Lost relevant | False hits |
|---|---|---|---|---|---|---|
| kosarka | 2 | — / 0.0% | 100.0% / 100.0% / 100.0% | 2 | — | — |
| kuvanje | 4 | — / 0.0% | 100.0% / 25.0% / 40.0% | 1 | `ca7a-040` International Cooking Exchange, `ca7a-044` Workshop: Berliner Brot & Brezeln, `ca7a-051` Мастер-класс по пельменям | — |
| slikanje | 2 | — / 0.0% | 100.0% / 100.0% / 100.0% | 2 | — | — |
| fotografija | 2 | — / 0.0% | 100.0% / 100.0% / 100.0% | 2 | — | — |
| film | 2 | 50.0% / 100.0% | 50.0% / 100.0% / 66.7% | 4 | — | `ca7a-043` Deutscher Sprachstammtisch, `ca7a-030` Kviz opšte kulture - specijal |
| drustvene igre | 3 | — / 0.0% | 100.0% / 33.3% / 50.0% | 1 | `ca7a-048` Spieleabend auf Deutsch, `ca7a-052` Вечер настольных игр | — |
| ucenje stranih jezika | 3 | — / 0.0% | 30.0% / 100.0% / 46.2% | 10 | — | `ca7a-024` International Students Hangout, `ca7a-031` Čas gitare u prirodi, `ca7a-017` Radionica Git & GitHub, `ca7a-016` AI & Android meetup, `ca7a-011` Radionica pravljenja paste, `ca7a-029` Veče astronomije, `5eed-001` Kviz veče u Skadarliji |
| priroda i svez vazduh | 9 | — / 0.0% | 70.0% / 77.8% / 73.7% | 10 | `ca7a-039` Sunset Kayaking Session, `ca7a-047` Winterlauf am Fluss | `ca7a-005` Akustično veče uz Savu, `ca7a-029` Veče astronomije, `ca7a-027` Diskusija: Nature vs. Nurture |
| svemir i zvezde | 2 | — / 0.0% | 100.0% / 50.0% / 66.7% | 1 | `ca7a-053` Лекция о космосе и будущих миссиях | — |
| vestacka inteligencija | 1 | — / 0.0% | 100.0% / 100.0% / 100.0% | 1 | — | — |
| aktivnost za celu porodicu | 1 | — / 0.0% | 100.0% / 100.0% / 100.0% | 1 | — | — |
| upoznavanje novih ljudi | 5 | 100.0% / 20.0% | 100.0% / 20.0% / 33.3% | 1 | `ca7a-024` International Students Hangout, `ca7a-043` Deutscher Sprachstammtisch, `ca7a-049` Русский разговорный клуб, `ca7a-055` Language Exchange: English / Deutsch / Русский | — |
| pisanje prica | 1 | — / 0.0% | 11.1% / 100.0% / 20.0% | 9 | — | `ca7a-027` Diskusija: Nature vs. Nurture, `ca7a-028` Social Media & Self-Esteem diskusija, `ca7a-011` Radionica pravljenja paste, `ca7a-036` Noćni paint & chill, `ca7a-017` Radionica Git & GitHub, `ca7a-035` Radionica pravljenja koktela bez alkohola, `ca7a-019` Indie Hackers Balkan meetup, `ca7a-015` Veče kratkog filma |
| bicikl | 1 | 100.0% / 100.0% | 100.0% / 100.0% / 100.0% | 1 | — | — |
| aktivnosti na reci | 3 | — / 0.0% | 66.7% / 66.7% / 66.7% | 3 | `ca7a-047` Winterlauf am Fluss | `ca7a-021` Biciklistička tura Zemun–Batajnica |
| startup i preduzetnistvo | 1 | — / 0.0% | 100.0% / 100.0% / 100.0% | 1 | — | — |
| board game night | 3 | — / 0.0% | 100.0% / 100.0% / 100.0% | 3 | — | — |
| hiking in the mountains | 2 | — / 0.0% | 50.0% / 100.0% / 66.7% | 4 | — | `ca7a-054` Зимняя прогулка по Кошутняку, `ca7a-022` Zimska šetnja Košutnjakom |
| Trazim opustenu vecernju radionicu gde nesto pravim svojim rukama | 6 | — / 0.0% | 100.0% / 33.3% / 50.0% | 2 | `ca7a-011` Radionica pravljenja paste, `ca7a-013` Akvarel Beograda, `ca7a-044` Workshop: Berliner Brot & Brezeln, `ca7a-051` Мастер-класс по пельменям | — |
| planinarnje | 4 | — / 0.0% | 100.0% / 25.0% / 40.0% | 1 | `ca7a-020` Zalazak sunca na Avali, `ca7a-022` Zimska šetnja Košutnjakom, `ca7a-054` Зимняя прогулка по Кошутняку | — |


# Filters from a sentence: evaluation (review point 10)

- Run: 2026-09-27 19:56
- Code: `31cf624 (with uncommitted changes)`
- Labels: `parse_labels.json`, last commit: 31cf624 2026-09-27
- Server: `http://localhost:8080`, database `orbit_eval`
- Sentences: 60, each asked 3 times; answered: 180 of 180

A field is correct when the answer is one of the acceptable values in `parse_labels.json` (for ambiguous sentences several are acceptable). **Recognised** looks only at sentences where the field had to be set. **Left out correctly** looks only at sentences where it had to be absent. **Consistent** is the share of sentences where all runs gave the same answer.

| Field | Correct (all) | Recognised when asked for | Left out correctly | Wrong value | Missed | Set without being asked | Consistent |
|---|---|---|---|---|---|---|---|
| Category | 99.4% (179/180) | 98.8% (83/84) | 100.0% (42/42) | 0 | 1 | 0 | 93.3% |
| Price | 99.4% (179/180) | 100.0% (36/36) | 100.0% (138/138) | 1 | 0 | 0 | 98.3% |
| Time window | 92.8% (167/180) | 86.1% (62/72) | 97.1% (99/102) | 0 | 10 | 3 | 93.3% |
| Distance | 98.3% (177/180) | 92.9% (39/42) | 100.0% (138/138) | 0 | 3 | 0 | 98.3% |
| Sort | 96.7% (174/180) | 100.0% (9/9) | 96.5% (165/171) | 0 | 0 | 6 | 100.0% |

**All five fields correct in one answer: 90.0% (162/180)**

## By kind of sentence

| Kind | Answers | All fields correct |
|---|---|---|
| ambiguous | 3 | 100.0% |
| ambiguous category | 18 | 100.0% |
| ambiguous price | 3 | 100.0% |
| ambiguous time | 6 | 66.7% |
| clear | 21 | 85.7% |
| cyrillic | 6 | 83.3% |
| distance | 18 | 83.3% |
| english | 12 | 91.7% |
| multi | 21 | 95.2% |
| negation | 6 | 100.0% |
| no diacritics / typo | 3 | 0.0% |
| one word | 15 | 100.0% |
| price | 12 | 100.0% |
| sort | 3 | 100.0% |
| time | 21 | 95.2% |
| trap | 12 | 75.0% |

## Every wrong answer

| # | Sentence | Kind | Field | Expected | Got | Runs |
|---|---|---|---|---|---|---|
| 1 | koncert veceras u blizini | clear | Time window | TODAY | — | 3/3 |
| 1 | koncert veceras u blizini | clear | Sort | — | NEAREST | 3/3 |
| 33 | sve besplatno danas, najblize prvo | multi | Time window | TODAY | — | 1/3 |
| 37 | bilo gde u Beogradu | distance | Distance | CITY | — | 3/3 |
| 38 | koncert sledeceg meseca | trap | Time window | — | THIS_MONTH | 3/3 |
| 51 | cheap food near me | english | Price | UP_TO_1000 or — | FREE | 1/3 |
| 53 | друштвене игре за викенд | cyrillic | Category | SOCIAL | — | 1/3 |
| 55 | danas ili sutra, svejedno sta | ambiguous time | Time window | TODAY or THIS_WEEK | — | 2/3 |
| 56 | krajem meseca neki festival hrane | time | Time window | THIS_MONTH | — | 1/3 |
| 60 | koncrt blizu mene za vkend | no diacritics / typo | Time window | WEEKEND | — | 3/3 |
| 60 | koncrt blizu mene za vkend | no diacritics / typo | Sort | — | NEAREST | 3/3 |

## All answers

| # | Sentence | Answers (category, price, time, distance, sort) |
|---|---|---|
| 1 | koncert veceras u blizini | MUSIC, —, —, NEARBY, NEAREST; MUSIC, —, —, NEARBY, NEAREST; MUSIC, —, —, NEARBY, NEAREST |
| 2 | hocu na basket ovog vikenda | SPORT, —, WEEKEND, —, —; SPORT, —, WEEKEND, —, —; SPORT, —, WEEKEND, —, — |
| 3 | besplatne radionice slikanja | ART, FREE, —, —, —; ART, FREE, —, —, —; ART, FREE, —, —, — |
| 4 | nesto za jelo do hiljadu dinara | FOOD, UP_TO_1000, —, —, —; FOOD, UP_TO_1000, —, —, —; FOOD, UP_TO_1000, —, —, — |
| 5 | programerski meetup ove nedelje | TECH, —, THIS_WEEK, —, —; TECH, —, THIS_WEEK, —, —; TECH, —, THIS_WEEK, —, — |
| 6 | planinarenje u okolini grada | OUTDOOR, —, —, CITY, —; OUTDOOR, —, —, CITY, —; OUTDOOR, —, —, REGION, — |
| 7 | gde mogu da trcim danas | SPORT, —, TODAY, —, —; SPORT, —, TODAY, —, —; SPORT, —, TODAY, —, — |
| 8 | izlozba | —, —, —, —, —; —, —, —, —, —; —, —, —, —, — |
| 9 | degustacija vina u subotu | FOOD, —, WEEKEND, —, —; FOOD, —, WEEKEND, —, —; FOOD, —, WEEKEND, —, — |
| 10 | nesto peske do mene | —, —, —, WALK, —; —, —, —, WALK, —; —, —, —, WALK, — |
| 11 | najblizi dogadjaji | —, —, —, —, NEAREST; —, —, —, —, NEAREST; —, —, —, —, NEAREST |
| 12 | jeftini koncerti | MUSIC, UP_TO_1000, —, —, —; MUSIC, UP_TO_1000, —, —, —; MUSIC, UP_TO_1000, —, —, — |
| 13 | sportski dogadjaji ovog meseca besplatno | SPORT, FREE, THIS_MONTH, —, —; SPORT, FREE, THIS_MONTH, —, —; SPORT, FREE, THIS_MONTH, —, — |
| 14 | u nedelju popodne druzenje uz drustvene igre | SOCIAL, —, WEEKEND, —, —; SOCIAL, —, WEEKEND, —, —; SOCIAL, —, WEEKEND, —, — |
| 15 | ove nedelje nesto kulturno | ART, —, THIS_WEEK, —, —; ART, —, THIS_WEEK, —, —; ART, —, THIS_WEEK, —, — |
| 16 | ne zanima me sport, nesto opusteno | —, —, —, —, —; SOCIAL, —, —, —, —; —, —, —, —, — |
| 17 | tech event tonight near me | TECH, —, TODAY, NEARBY, —; TECH, —, TODAY, NEARBY, —; TECH, —, TODAY, NEARBY, — |
| 18 | free outdoor activities this weekend | OUTDOOR, FREE, WEEKEND, —, —; OUTDOOR, FREE, WEEKEND, —, —; OUTDOOR, FREE, WEEKEND, —, — |
| 19 | gde da izadjem veceras sa drustvom | SOCIAL, —, TODAY, —, —; SOCIAL, —, TODAY, —, —; SOCIAL, —, TODAY, —, — |
| 20 | radionica kuvanja do 5000 dinara | FOOD, UP_TO_5000, —, —, —; FOOD, UP_TO_5000, —, —, —; FOOD, UP_TO_5000, —, —, — |
| 21 | radionica do 1500 dinara | —, UP_TO_5000, —, —, —; —, UP_TO_5000, —, —, —; —, UP_TO_5000, —, —, — |
| 22 | nesto do 800 dinara | —, UP_TO_1000, —, —, —; —, UP_TO_1000, —, —, —; —, UP_TO_1000, —, —, — |
| 23 | bez ulaznice, bilo sta u gradu | —, FREE, —, CITY, —; —, FREE, —, CITY, —; —, FREE, —, CITY, — |
| 24 | muzika uzivo blizu mene sutra | MUSIC, —, —, NEARBY, —; MUSIC, —, —, NEARBY, —; MUSIC, —, —, NEARBY, — |
| 25 | vikend u prirodi | OUTDOOR, —, WEEKEND, —, —; OUTDOOR, —, WEEKEND, —, —; OUTDOOR, —, WEEKEND, —, — |
| 26 | biciklizam | SPORT, —, —, —, —; SPORT, —, —, —, —; SPORT, —, —, —, — |
| 27 | kajak na Savi | SPORT, —, —, —, —; SPORT, —, —, —, —; SPORT, —, —, —, — |
| 28 | jazz | MUSIC, —, —, —, —; —, —, —, —, —; MUSIC, —, —, —, — |
| 29 | dogadjaj za decu | SOCIAL, —, —, —, —; —, —, —, —, —; —, —, —, —, — |
| 30 | hocu da naucim nemacki | —, —, —, —, —; —, —, —, —, —; —, —, —, —, — |
| 31 | predavanje o svemiru ovog meseca | TECH, —, THIS_MONTH, —, —; TECH, —, THIS_MONTH, —, —; TECH, —, THIS_MONTH, —, — |
| 32 | zurka veceras | SOCIAL, —, TODAY, —, —; SOCIAL, —, TODAY, —, —; SOCIAL, —, TODAY, —, — |
| 33 | sve besplatno danas, najblize prvo | —, FREE, TODAY, —, NEAREST; —, FREE, —, —, NEAREST; —, FREE, TODAY, —, NEAREST |
| 34 | dogadjaji na 100 km od mene | —, —, —, REGION, —; —, —, —, REGION, —; —, —, —, REGION, — |
| 35 | u krugu od jednog kilometra | —, —, —, WALK, —; —, —, —, WALK, —; —, —, —, WALK, — |
| 36 | nesto u radijusu od 5 km | —, —, —, NEARBY, —; —, —, —, NEARBY, —; —, —, —, NEARBY, — |
| 37 | bilo gde u Beogradu | —, —, —, —, —; —, —, —, —, —; —, —, —, —, — |
| 38 | koncert sledeceg meseca | MUSIC, —, THIS_MONTH, —, —; MUSIC, —, THIS_MONTH, —, —; MUSIC, —, THIS_MONTH, —, — |
| 39 | prosle nedelje je bio dobar kviz | —, —, —, —, —; —, —, —, —, —; —, —, —, —, — |
| 40 | sportski dan za celu porodicu, besplatan | SPORT, FREE, —, —, —; SPORT, FREE, —, —, —; SPORT, FREE, —, —, — |
| 41 | Hackathon | TECH, —, —, —, —; TECH, —, —, —, —; TECH, —, —, —, — |
| 42 | fudbal ili kosarka ove subote | SPORT, —, WEEKEND, —, —; SPORT, —, WEEKEND, —, —; SPORT, —, WEEKEND, —, — |
| 43 | nesto skupo i luksuzno | —, —, —, —, —; —, —, —, —, —; —, —, —, —, — |
| 44 | do 10000 dinara | —, —, —, —, —; —, —, —, —, —; —, —, —, —, — |
| 45 | besplatno, ali ne sport | —, FREE, —, —, —; —, FREE, —, —, —; —, FREE, —, —, — |
| 46 | muzicki dogadjaji ali ne koncerti | MUSIC, —, —, —, —; MUSIC, —, —, —, —; MUSIC, —, —, —, — |
| 47 | gde mogu da vezbam jogu ove nedelje blizu | SPORT, —, THIS_WEEK, NEARBY, —; SPORT, —, THIS_WEEK, NEARBY, —; SPORT, —, THIS_WEEK, NEARBY, — |
| 48 | umetnost i kultura za vikend u gradu | ART, —, WEEKEND, CITY, —; ART, —, WEEKEND, CITY, —; ART, —, WEEKEND, CITY, — |
| 49 | hrana | FOOD, —, —, —, —; FOOD, —, —, —, —; FOOD, —, —, —, — |
| 50 | something to do tonight | —, —, TODAY, —, —; —, —, TODAY, —, —; —, —, TODAY, —, — |
| 51 | cheap food near me | FOOD, UP_TO_1000, —, NEARBY, —; FOOD, FREE, —, NEARBY, —; FOOD, —, —, NEARBY, — |
| 52 | концерт вечерас | MUSIC, —, TODAY, —, —; MUSIC, —, TODAY, —, —; MUSIC, —, TODAY, —, — |
| 53 | друштвене игре за викенд | —, —, WEEKEND, —, —; SOCIAL, —, WEEKEND, —, —; SOCIAL, —, WEEKEND, —, — |
| 54 | kad je sledeci turnir u pikadu | SPORT, —, —, —, —; SPORT, —, —, —, —; SPORT, —, —, —, — |
| 55 | danas ili sutra, svejedno sta | —, —, —, —, —; —, —, —, —, —; —, —, TODAY, —, — |
| 56 | krajem meseca neki festival hrane | FOOD, —, —, —, —; FOOD, —, THIS_MONTH, —, —; FOOD, —, THIS_MONTH, —, — |
| 57 | najblize i besplatno | —, FREE, —, —, NEAREST; —, FREE, —, —, NEAREST; —, FREE, —, —, NEAREST |
| 58 | tehnologija | TECH, —, —, —, —; TECH, —, —, —, —; TECH, —, —, —, — |
| 59 | romanticno vece za dvoje | SOCIAL, —, —, —, —; SOCIAL, —, —, —, —; SOCIAL, —, TODAY, —, — |
| 60 | koncrt blizu mene za vkend | MUSIC, —, —, NEARBY, NEAREST; MUSIC, —, —, NEARBY, NEAREST; MUSIC, —, —, NEARBY, NEAREST |

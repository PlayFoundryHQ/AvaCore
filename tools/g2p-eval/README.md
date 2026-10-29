# g2p-eval — how often does AvaCore get Persian vowels wrong?

Dev-only measurement tool (nothing here ships in the APK).

Written Persian omits short vowels, so eSpeak-NG has to guess them. This script
runs every word of a reference pronouncing dictionary through AvaCore's own
lexicon (`assets/tts/lexicon.txt`) and AvaCore's own `espeak-ng-data`, then
compares the resulting vowel sequence with the reference.

```bash
python3 -m venv .venv && .venv/bin/pip install -r requirements.txt
.venv/bin/python g2p_eval.py            # prints the error rate, writes report.tsv
.venv/bin/python g2p_eval.py --word کتاب
```

- Reference: [tihudict](https://github.com/tihu-nlp/tihudict) (GPL-3), downloaded
  into `.cache/` at run time and used only for measuring — never copied into
  AvaCore's lexicon.
- Words with several reference pronunciations (homographs) are skipped: a
  lexicon can't fix those, they need context.
- The reference itself has mistakes (and sometimes includes an ezafe), so treat
  `report.tsv` as a list of *suspects* to check by ear, not as ground truth.

# g2p-eval — how often does AvaCore get Persian vowels wrong?

Dev-only measurement tool (nothing here ships in the APK).

Written Persian omits short vowels, so eSpeak-NG has to guess them. This script
runs every word of a reference pronouncing dictionary through AvaCore's own
lexicon (`assets/tts/lexicon.txt`) and AvaCore's own `espeak-ng-data`, then
compares the resulting vowel sequence with the reference.

```bash
python3 -m venv .venv && .venv/bin/pip install -r requirements.txt
.venv/bin/python g2p_eval.py                       # gold set (default)
.venv/bin/python g2p_eval.py --reference tihu      # ~1.8k common words
.venv/bin/python g2p_eval.py --reference ipadict   # ~5k words, many rare/foreign
.venv/bin/python g2p_eval.py --word کتاب
```

Each run prints the error rate and writes `report-<reference>.tsv`.

References:
- `gold.tsv` — AvaCore's own hand-written set: every number word
  `NumberToWords` emits plus everyday words. A **regression guard**: it was
  written around the words the lexicon fixes, so its score is not an unbiased
  measure. Extend it whenever you add a lexicon fix.
- [tihudict](https://github.com/tihu-nlp/tihudict) (GPL-3) and
  [ipa-dict `fa`](https://github.com/open-dict-data/ipa-dict) (MIT,
  self-described "extremely experimental") — downloaded into `.cache/` at run
  time and used only for measuring, never copied into AvaCore's lexicon. These
  are the fair numbers.

Measured October 2026 (vowel errors, before → after the short-vowel lexicon):

| reference | before | after |
|---|---|---|
| gold (158 words) | 24.1% | 0.6% |
| tihu (1,781 common words) | 4.1% | 3.9% |
| ipadict (4,959 words) | 18.1% | 17.7% |

So eSpeak-NG's own Persian dictionary is already good on common words; what
the lexicon fixes are a handful of frequent words — above all the number words
(«چهار» was read *chāhār*, nine «نه» was read *na*). The bigger remaining gap is
ezafe and homographs, which need sentence context, not a word list.

Spelling rules for lexicon replacements (eSpeak quirks found here, enforced by
`LexiconFileTest`):
- never use shadda (ّ) — eSpeak reads it out as "tashdid";
- no vowel mark right before a silent final ه — eSpeak drops the final *e*;
- sukun (ْ) is honoured and is how to stop eSpeak inserting a vowel.
- Words with several reference pronunciations (homographs) are skipped: a
  lexicon can't fix those, they need context.
- The reference itself has mistakes (and sometimes includes an ezafe), so treat
  `report.tsv` as a list of *suspects* to check by ear, not as ground truth.

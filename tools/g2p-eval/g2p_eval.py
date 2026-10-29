#!/usr/bin/env python3
"""Measure how often AvaCore's Persian G2P gets a word's vowels wrong.

Dev-only tool, not shipped in the APK. For every word in a reference
pronouncing dictionary it runs the word through AvaCore's own lexicon
(assets/tts/lexicon.txt) and then through eSpeak-NG with AvaCore's own
espeak-ng-data — the same text the engine would hand to the Piper voice — and
compares the *vowel sequence* with the reference. Short vowels are what written
Persian leaves out and what eSpeak has to guess, so that is the error worth
counting; consonants are almost always right.

Reference: tihudict (https://github.com/tihu-nlp/tihudict, GPL-3). It is
downloaded into a local cache at run time and only used to *measure* — it is
not copied into AvaCore. Words with more than one reference pronunciation
(homographs such as مرد / کرم) are reported separately: a lexicon can't fix
them, that needs context.

Usage:
  python -m venv .venv && .venv/bin/pip install -r requirements.txt
  .venv/bin/python g2p_eval.py                  # summary + report.tsv
  .venv/bin/python g2p_eval.py --word کتاب      # inspect one word
"""
import argparse
import sys
import urllib.request
from collections import defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
ASSETS = ROOT / "app/src/main/assets/tts"
TIHU_URL = "https://raw.githubusercontent.com/tihu-nlp/tihudict/master/tihu.demo.dict"

TIHU_VOWELS = {"A": "A", "a": "a", "e": "e", "o": "o", "i": "i", "u": "u"}
IPA_VOWELS = {"ɑ": "A", "ɒ": "A", "a": "a", "æ": "a", "e": "e", "ə": "e", "o": "o", "i": "i", "u": "u"}


def load_reference(cache: Path) -> dict:
    path = cache / "tihu.demo.dict"
    if not path.exists():
        cache.mkdir(parents=True, exist_ok=True)
        urllib.request.urlretrieve(TIHU_URL, path)
    ref = defaultdict(set)
    for line in path.read_text(encoding="utf-8").splitlines():
        if "\t" not in line:
            continue
        word, phones = line.split("\t", 1)
        ref[word.strip()].add("".join(TIHU_VOWELS.get(p, "") for p in phones.split()))
    return ref


def load_lexicon(path: Path) -> dict:
    lex = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        lex[k.strip()] = v.strip()
    return lex


def vowels(ipa: str) -> str:
    """Vowel skeleton of an eSpeak IPA string, e.g. "ketˈɑb" -> "eA". An "i"
    sitting between two vowels is the glide y (eSpeak writes آیا as "ɑiɑ"),
    not a vowel, so it's dropped to match the reference's consonant y."""
    seg = [c for c in ipa if c not in "ˈˌː"]
    out = []
    for n, c in enumerate(seg):
        if c == "i" and 0 < n < len(seg) - 1 and seg[n - 1] in IPA_VOWELS and seg[n + 1] in IPA_VOWELS:
            continue
        out.append(IPA_VOWELS.get(c, ""))
    return "".join(out)


class Espeak:
    def __init__(self, data_dir: Path):
        from piper import espeakbridge

        self._b = espeakbridge
        self._b.initialize(str(data_dir))
        self._b.set_voice("fa")

    def ipa(self, text: str) -> str:
        return " ".join(p for p, _, _ in self._b.get_phonemes(text)).strip()


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--cache", type=Path, default=HERE / ".cache")
    ap.add_argument("--lexicon", type=Path, default=ASSETS / "lexicon.txt")
    ap.add_argument("--espeak-data", type=Path, default=ASSETS / "espeak-ng-data")
    ap.add_argument("--report", type=Path, default=HERE / "report.tsv")
    ap.add_argument("--word", help="show the analysis for one word and exit")
    args = ap.parse_args()

    ref = load_reference(args.cache)
    lex = load_lexicon(args.lexicon)
    es = Espeak(args.espeak_data)

    if args.word:
        w = args.word
        fed = lex.get(w, w)
        ipa = es.ipa(fed)
        print(f"word={w} fed={fed} ipa={ipa} vowels={vowels(ipa)} reference={sorted(ref.get(w, []))}")
        return 0

    checked = wrong = homographs = 0
    rows = []
    for word in sorted(ref):
        options = ref[word]
        if len(options) > 1:
            homographs += 1
            continue
        want = next(iter(options))
        fed = lex.get(word, word)
        ipa = es.ipa(fed)
        got = vowels(ipa)
        checked += 1
        if got != want:
            wrong += 1
            rows.append((word, fed, ipa, got, want))

    args.report.write_text(
        "word\tfed_to_espeak\tespeak_ipa\tespeak_vowels\treference_vowels\n"
        + "".join("\t".join(r) + "\n" for r in rows),
        encoding="utf-8",
    )
    print(f"lexicon entries: {len(lex)}")
    print(f"reference words: {len(ref)} ({homographs} homographs skipped)")
    print(f"vowel errors:    {wrong}/{checked} = {100 * wrong / max(checked, 1):.1f}%")
    print(f"details:         {args.report}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

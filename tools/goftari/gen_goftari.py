#!/usr/bin/env python3
"""Generate app/src/main/assets/tts/goftari.txt — the Ketabi -> Goftari table.

Goftari (spoken) mode rewrites formal written Persian into the way it is
actually spoken (می‌خواهم بروم -> میخوام برم) before it reaches eSpeak-NG.
The table is plain data in the lexicon's own "formal = spoken" format, applied
by the same whole-token, longest-match matcher as lexicon.txt.

Two kinds of entries:
  * WORDS — hand-written whole-word replacements (pronouns, common
    contractions, irregular verbs);
  * VERBS — present-tense paradigms expanded from a stem table: every
    prefix (می‌ / نمی‌ / ب / ن) x person ending, formal spelling -> spoken.

Spoken forms are spelled the way eSpeak-NG reads them correctly, which is not
always how people type them: «میره» joined (with a half-space eSpeak reads
"mirah"), the negative as «نِمی…», the 3pl subjunctive of «آمدن» as «بیآن»
(«بیان» is also bayān, "expression"). Those choices were checked with
tools/g2p-eval; re-run `check` after editing.

Rules that keep it safe:
  * a verb form is only listed when it can't be read another way — a 3rd
    singular «می‌خورد» is also the past tense (mixord), so stems whose past
    stem collides are flagged and that ending is skipped;
  * nothing that isn't in the table changes.

Usage:  python3 gen_goftari.py            # writes the asset
        python3 gen_goftari.py check      # needs piper-tts; prints readings
"""
import sys
from pathlib import Path

OUT = Path(__file__).resolve().parents[2] / "app/src/main/assets/tts/goftari.txt"
ZWNJ = "‌"

# formal = spoken, whole words (hand-written)
WORDS = """
# pronouns / demonstratives
آن = اون
آنها = اونا
آن‌ها = اونا
آنان = اونا
آنجا = اونجا
آن‌جا = اونجا
آنچه = اونچه
اینها = اینا
این‌ها = اینا
ایشان = ایشون
مرا = مَنو
ترا = تو رو
# object marker («است» is handled in GoftariRewriter: it attaches to the word before it)
را = رو
# common contractions
دیگر = دیگه
اگر = اگه
مگر = مگه
اکنون = الان
بسیار = خیلی
زیرا = چون
# ān -> un, only where the spoken form is standard
خانه = خونه
خانه‌ها = خونه‌ها
جان = جون
نان = نون
# irregular / auxiliary verbs (formal «می‌باشد» is the copula; GoftariRewriter attaches «است»)
می‌باشد = است
می‌باشند = هستن
هستند = هستن
هستید = هستین
نیستند = نیستن
نیستید = نیستین
دارد = داره
دارند = دارن
دارید = دارین
ندارد = نداره
ندارند = ندارن
ندارید = ندارین
بودند = بودن
بودید = بودین
شدند = شدن
شدید = شدین
کردند = کردن
کردید = کردین
رفتند = رفتن
رفتید = رفتین
گفتند = گفتن
گفتید = گفتین
دادند = دادن
دیدند = دیدن
خواستند = خواستن
آمد = اومد
آمدم = اومدم
آمدی = اومدی
آمدیم = اومدیم
آمدید = اومدین
آمدند = اومدن
آمده = اومده
آمدن = اومدن
نیامد = نیومد
نیامدم = نیومدم
نیامدند = نیومدن
می‌آمد = میومد
می‌آمدم = میومدم
می‌آمدند = میومدن
"""

# present stem (formal) -> spoken stem, and which endings may be rewritten.
# 3sg «ـد» is skipped where present 3sg == past 3sg (e.g. خورد);
# 2pl «ـید» is skipped where present 2pl == past 3sg (e.g. رسید, کشید).
ALL = {"م", "ی", "د", "یم", "ید", "ند"}
NO_D = ALL - {"د"}
NO_ID = ALL - {"ید"}
VERBS = [
    # formal stem, spoken stem, endings, 3sg spoken ending
    ("خواه", "خوا", ALL, "د"),
    ("رو", "ر", ALL, "ه"),
    ("گوی", "گ", ALL, "ه"),
    ("شو", "ش", ALL, "ه"),
    ("ده", "د", ALL, "ه"),
    ("توان", "تون", ALL, "ه"),
    ("دان", "دون", ALL, "ه"),
    ("مان", "مون", NO_D, "ه"),
    ("خوان", "خون", NO_D, "ه"),
    ("گذار", "ذار", ALL, "ه"),
    ("نشین", "شین", ALL, "ه"),
    ("کن", "کن", ALL, "ه"),
    ("زن", "زن", ALL, "ه"),
    ("بین", "بین", ALL, "ه"),
    ("گیر", "گیر", ALL, "ه"),
    ("نویس", "نویس", ALL, "ه"),
    ("ساز", "ساز", ALL, "ه"),
    ("شناس", "شناس", ALL, "ه"),
    ("فروش", "فروش", ALL, "ه"),
    ("پذیر", "پذیر", ALL, "ه"),
    ("گرد", "گرد", ALL, "ه"),
    ("گذر", "گذر", ALL, "ه"),
    ("بند", "بند", ALL, "ه"),
    ("چین", "چین", ALL, "ه"),
    ("خور", "خور", NO_D, "ه"),
    ("خواب", "خواب", NO_ID, "ه"),
    ("پوش", "پوش", NO_ID, "ه"),
    ("فهم", "فهم", NO_ID, "ه"),
    ("کش", "کش", NO_ID, "ه"),
    ("ترس", "ترس", NO_ID, "ه"),
    ("خند", "خند", NO_ID, "ه"),
    ("رس", "رس", {"د"}, "ه"),
]
# formal ending -> spoken ending (3sg handled per verb)
SPOKEN_END = {"م": "م", "ی": "ی", "یم": "یم", "ید": "ین", "ند": "ن"}
def formal_form(prefix: str, stem: str, end: str) -> str:
    sep = ZWNJ if prefix in ("می", "نمی") else ""
    return prefix + sep + stem + end


# Negative present: «نِمی» joined reads right for most stems; these read right
# only with the half-space kept («نمی‌ترسه»), and «خوا»/«گ» only plain.
NEG_WITH_ZWNJ = {"زن", "گذر", "فهم", "ترس", "خند", "کن"}

# Per-form spelling fixes found with `check`. None = leave the formal form
# alone: no spoken spelling eSpeak reads right, and the formal one reads fine.
OVERRIDES = {
    "بروی": "بِری", "برویم": "بِریم", "بروید": "بِرین", "برود": None,
    "نروم": None, "نروند": None,
    "نمی‌گویی": "نِمیگی", "نمی‌گویم": "نِمیگم", "نمی‌گویند": "نِمیگن", "نمی‌گوییم": "نِمیگیم",
    "بگوییم": "بِگیم", "نگوید": None, "نگوییم": None,
    "بدهی": "بِدی", "بدهند": None,  # «بدن» is "body"
    "نشوید": None,
    "بتواند": "بِتونه",
    "نمی‌رسد": None,
    "بچیند": None,
}


def spoken_form(prefix: str, spoken_stem: str, end: str, third: str) -> str:
    e = third if end == "د" else SPOKEN_END[end]
    if prefix == "می":
        p = "می" + (ZWNJ if spoken_stem == "مون" else "")  # «میمونم» is misread
    elif prefix == "نمی":
        if spoken_stem == "خوا" or (spoken_stem == "گ" and end == "د"):
            p = "نمی"
        elif spoken_stem in NEG_WITH_ZWNJ:
            p = "نمی" + ZWNJ
        else:
            p = "نِمی"
    else:
        p = prefix
    return p + spoken_stem + e


def verb_entries():
    out = []
    for stem, spoken, endings, third in VERBS:
        for prefix in ("می", "نمی", "ب", "ن"):
            for end in sorted(endings, key=len):
                f = formal_form(prefix, stem, end)
                s = OVERRIDES.get(f, spoken_form(prefix, spoken, end, third))
                if s is not None and f != s:
                    out.append((f, s))
    # آمدن: present stem آی, written with the prefix merged (می‌آیم / بیایم / نیایم)
    for end, spoken_end in (("م", "م"), ("ی", "ی"), ("د", "د"), ("یم", "یم"), ("ید", "ین"), ("ند", "ن")):
        fe = "یی" if end == "ی" else ("یید" if end == "ید" else "ی" + end)
        out.append(("می" + ZWNJ + "آ" + fe, "میا" + spoken_end))
        out.append(("نمی" + ZWNJ + "آ" + fe, "نِمیا" + spoken_end))
        out.append(("بیا" + fe, "بیآن" if end == "ند" else "بیا" + spoken_end))
        out.append(("نیا" + fe, "نیا" + spoken_end))
    return out


def build() -> str:
    lines = [
        "# AvaCore Goftari (spoken Persian) table — GENERATED by tools/goftari/gen_goftari.py",
        "# Do not edit by hand; edit the generator and re-run it.",
        "# Format: formal = spoken (whole tokens, longest match first, same as lexicon.txt)",
        "",
        "# --- words ---",
    ]
    lines += [l.strip() for l in WORDS.strip().splitlines()]
    lines += ["", "# --- present-tense verb paradigms ---"]
    seen = set()
    for f, s in verb_entries():
        if f in seen:
            continue
        seen.add(f)
        lines.append(f"{f} = {s}")
    return "\n".join(lines) + "\n"


def check():
    from piper import espeakbridge as b

    b.initialize(str(OUT.parent / "espeak-ng-data"))
    b.set_voice("fa")
    for line in OUT.read_text(encoding="utf-8").splitlines():
        if " = " in line and not line.startswith("#"):
            f, s = line.split(" = ", 1)
            print(f"{f}\t{s}\t{' '.join(p for p, _, _ in b.get_phonemes(s))}")


if __name__ == "__main__":
    if sys.argv[1:] == ["check"]:
        check()
    else:
        OUT.write_text(build(), encoding="utf-8")
        print(f"wrote {OUT} ({sum(1 for l in OUT.read_text(encoding='utf-8').splitlines() if ' = ' in l)} entries)")

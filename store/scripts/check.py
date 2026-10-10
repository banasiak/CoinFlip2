#!/usr/bin/env python3
"""Checks store_listing.xml and release_notes.xml before they are pasted into Play Console.

  python3 store/scripts/check.py

Both files have to parse, cover the same languages under the codes Play Console uses for them, and
keep every field within Play's limit. Exits non-zero on any failure, after reporting every one.
"""

import pathlib
import sys
import xml.etree.ElementTree as ET

STORE = pathlib.Path(__file__).resolve().parent.parent

# Play Console's own codes for the languages this listing is in. Most are language-region, but not
# all: Thai is plain "th", and a block tagged th-TH is silently ignored by Play
PLAY_CODES = {"en-US", "de-DE", "es-ES", "fr-FR", "hi-IN", "it-IT", "ja-JP", "ko-KR", "pl-PL", "pt-BR", "th", "tr-TR", "zh-CN"}

LISTING_LIMITS = {"app_name": 30, "short_description": 80, "full_description": 4000}
NOTES_LIMIT = 500


def text(element):
    # the files keep long text flush-left between indented tags, so the tags' own newlines and
    # indentation are not part of the field
    return (element.text or "").strip("\n ") if element is not None else ""


def main():
    problems = []
    listing = ET.parse(STORE / "store_listing.xml").getroot()
    notes = ET.parse(STORE / "release_notes.xml").getroot()

    listing_codes = [el.tag for el in listing]
    notes_codes = [el.tag for el in notes]
    for name, codes in (("store_listing.xml", listing_codes), ("release_notes.xml", notes_codes)):
        for code in codes:
            if code not in PLAY_CODES:
                problems.append(f"{name}: <{code}> is not one of Play Console's codes for this listing")
        if len(set(codes)) != len(codes):
            problems.append(f"{name}: a language appears more than once")
    if set(listing_codes) != set(notes_codes):
        problems.append(f"the two files cover different languages: {sorted(set(listing_codes) ^ set(notes_codes))}")

    print(f"{'':6} {'name':>7} {'short':>7} {'full':>9} {'notes':>7}")
    for el in listing:
        sizes = []
        for field, limit in LISTING_LIMITS.items():
            value = text(el.find(field))
            if not value:
                problems.append(f"store_listing.xml: <{el.tag}> has no {field}")
            elif len(value) > limit:
                problems.append(f"store_listing.xml: <{el.tag}> {field} is {len(value)} characters, over {limit}")
            sizes.append(f"{len(value)}/{limit}")
        note = notes.find(el.tag)
        note_text = text(note)
        if note is not None and not note_text:
            problems.append(f"release_notes.xml: <{el.tag}> is empty")
        elif len(note_text) > NOTES_LIMIT:
            problems.append(f"release_notes.xml: <{el.tag}> is {len(note_text)} characters, over {NOTES_LIMIT}")
        sizes.append(f"{len(note_text)}/{NOTES_LIMIT}")
        print(f"{el.tag:6} {sizes[0]:>7} {sizes[1]:>7} {sizes[2]:>9} {sizes[3]:>7}")

    for problem in problems:
        print(problem, file=sys.stderr)
    sys.exit(1 if problems else 0)


if __name__ == "__main__":
    main()

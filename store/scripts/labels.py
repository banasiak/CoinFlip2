#!/usr/bin/env python3
"""Prints the on-screen labels screenshots.sh taps and waits for, in one of the listing's languages,
as regular expressions built from the app's own string resources, so the screenshots follow the
translations without the script having to know them.

  labels.py CODE     a Play Console language code such as de-DE; prints "name<TAB>regex" lines

A language's strings are read over the English ones, so a string it lacks falls back the same way
it does in the app.
"""

import pathlib
import re
import sys
import xml.etree.ElementTree as ET

RES = pathlib.Path(__file__).resolve().parents[2] / "coinflip/src/main/res"


def unescape(value):
    # Android's own escaping in strings.xml, on top of XML's: an optional pair of surrounding
    # quotes, and backslash escapes such as Turkish's \'
    value = value.strip()
    if len(value) >= 2 and value[0] == value[-1] == '"':
        value = value[1:-1]
    return re.sub(r"\\(.)", lambda m: {"n": "\n", "t": "\t"}.get(m.group(1), m.group(1)), value)


def strings(directory):
    path = RES / directory / "strings.xml"
    if not path.exists():
        return {}
    return {s.get("name"): unescape("".join(s.itertext())) for s in ET.parse(path).getroot().iter("string")}


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    language = sys.argv[1].split("-")[0]
    s = strings("values")
    if language != "en":
        found = strings(f"values-{language}")
        if not found:
            sys.exit(f"labels.py: the app has no values-{language} strings")
        s.update(found)

    def exact(key):
        return "^" + re.escape(s[key]) + "$"

    def formatted(key):
        # a format string's literal text, with its one placeholder matching anything
        parts = re.split(r"%(?:\d+\$)?\d*[sd]", s[key])
        return "^" + "(.+)".join(re.escape(p) for p in parts) + "$"

    labels = {
        "diagnostics": exact("diagnostics_menu_title"),
        "settings": exact("settings_menu_title"),
        "about": exact("about_menu_title"),
        "heads": exact("heads"),
        "tails": exact("tails"),
        "coin_type": exact("settings_item_coin_title"),
        "favorites": exact("settings_item_coin_group_favorites"),
        "about_title": exact("about_fragment_title"),
        "diagnostics_title": exact("diagnostics_fragment_title"),
        # the iterations button is the run size, then this word; the run size is what TOTAL reads
        # once the run is over, in the same locale's number format
        "iterations": "^(.+) " + re.escape(s["diagnostics_iterations_summary"]) + "$",
        "seconds": formatted("seconds"),
    }
    for name, pattern in labels.items():
        print(f"{name}\t{pattern}")


if __name__ == "__main__":
    main()

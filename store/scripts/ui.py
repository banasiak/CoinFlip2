#!/usr/bin/env python3
"""Finds, taps and waits for text on the screen of the device adb is pointed at, via uiautomator.

  ui.py find PATTERN                  print "x y label" for every node whose text or description matches
  ui.py tap PATTERN                   tap the centre of the first match
  ui.py wait PATTERN... [-t SECONDS]  wait until every pattern matches at once (default 60 seconds)
  ui.py exact PATTERN                 print a pattern matching exactly the first match's first group

Patterns are Python regular expressions. adb comes from the PATH and honours ANDROID_SERIAL.
"""

import argparse
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

DUMP = "/sdcard/window_dump.xml"


def adb(*args):
    return subprocess.run(["adb", *args], capture_output=True, text=True, check=True).stdout


def nodes():
    # a dump fails while the screen is still animating, and is killed outright when anything else
    # starts one (Android allows one UI automation connection at a time), so either is retried
    for _ in range(5):
        dump = subprocess.run(["adb", "shell", "uiautomator", "dump", DUMP], capture_output=True, text=True)
        if dump.returncode == 0 and "dumped to" in dump.stdout:
            break
        time.sleep(0.5)
    else:
        sys.exit("ui.py: uiautomator could not dump the screen")
    for node in ET.fromstring(adb("exec-out", "cat", DUMP)).iter("node"):
        label = node.get("text") or node.get("content-desc") or ""
        if label:
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
            yield (x1 + x2) // 2, (y1 + y2) // 2, label


def matches(pattern):
    return [n for n in nodes() if re.search(pattern, n[2])]


def main():
    parser = argparse.ArgumentParser(description="find, tap or wait for on-screen text")
    parser.add_argument("command", choices=["find", "tap", "wait", "exact"])
    parser.add_argument("patterns", nargs="+")
    parser.add_argument("-t", "--timeout", type=float, default=60)
    args = parser.parse_args()

    if args.command == "find":
        for x, y, label in matches(args.patterns[0]):
            print(x, y, label)
    elif args.command == "tap":
        hits = matches(args.patterns[0])
        if not hits:
            sys.exit(f"ui.py: nothing on screen matches {args.patterns[0]!r}")
        adb("shell", "input", "tap", str(hits[0][0]), str(hits[0][1]))
    elif args.command == "exact":
        hits = matches(args.patterns[0])
        if not hits:
            sys.exit(f"ui.py: nothing on screen matches {args.patterns[0]!r}")
        print("^" + re.escape(re.search(args.patterns[0], hits[0][2]).group(1)) + "$")
    else:
        deadline = time.monotonic() + args.timeout
        while True:
            labels = [label for _, _, label in nodes()]
            missing = [p for p in args.patterns if not any(re.search(p, label) for label in labels)]
            if not missing:
                return
            if time.monotonic() > deadline:
                sys.exit(f"ui.py: still not on screen after {args.timeout:g} s: {', '.join(missing)}")
            time.sleep(0.5)


if __name__ == "__main__":
    main()

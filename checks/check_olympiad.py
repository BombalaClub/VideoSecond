"""Verify the offline bank against the 2027 third-grade IHC practice sheet."""
import argparse
import json
from pathlib import Path
import struct
import sys
from zipfile import ZipFile


SOURCE = Path(__file__).resolve().parents[1] / "app/src/main"
# The sheet's numbered answer key. Q18 prints one of eight equivalent diagrams.
KEY = """
100000 20260 300 1 2 2002 40060000 16 7850 40
61 144 2 78 12051 15 588 diagram 24 978
23147 B 403 6 46 28054 13 3 7 1633
C 16 4 145 54 B 2500 D 17 2
7 11 14 24 34 18 4 3 B C
E 甲 9 62 8 80 56 94 40 108
143 50/2000/12000 420 2160 192 6 6 64 1 212
8 435 25 8 43/16 44 42 12 8 9
6 31 14 23 26 84 75 32 143 48
12 104 36 40 15 30 535 160 12 300
""".split()
FIGURES = {17, 18, 19, 20, 21, 31, 34, 35, 36, 38, 43, 44, 45, 46,
           49, 50, 51, 52, 53, 56, 57, 58, 60, 61, 100}
CHOICES = {22, 31, 36, 38, 49, 50, 51, 52}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def png_size(data, name):
    require(data.startswith(b"\x89PNG\r\n\x1a\n") and data[12:16] == b"IHDR",
            f"{name}: invalid PNG header")
    width, height = struct.unpack(">II", data[16:24])
    require(width > 0 and height > 0, f"{name}: empty image")
    return width, height


def nonempty_strings(value):
    return (isinstance(value, list) and value
            and all(isinstance(s, str) and s.strip() for s in value))


def check_question(q):
    n = q["id"]
    for field in ("topic", "prompt"):
        require(isinstance(q.get(field), str) and q[field].strip(), f"Q{n}: missing {field}")
    answers = q.get("answers")
    hints = q.get("hints")
    require(nonempty_strings(answers), f"Q{n}: missing answer")
    require(nonempty_strings(hints) and len(hints) == len(set(hints)) == 2,
            f"Q{n}: needs two distinct hints")
    if n == 18:
        require(q.get("labels") == ["上", "左", "中", "右", "下"], "Q18: position order")
        require(sorted(answers) == list("34569"), "Q18: available digits")
        top, left, center, right, bottom = map(int, answers)
        require(top + bottom == left + right == center, "Q18: invalid sample arrangement")
    else:
        require(answers == KEY[n - 1].split("/"), f"Q{n}: answer differs from the sheet")
    if len(answers) > 1:
        labels = q.get("labels")
        require(nonempty_strings(labels) and len(set(labels)) == len(labels) == len(answers),
                f"Q{n}: missing or duplicate field labels")
    if n in CHOICES:
        options = q.get("options")
        require(nonempty_strings(options) and len(set(options)) == len(options) >= 2
                and answers[0] in options, f"Q{n}: incomplete choices")
    require(q.get("diagram", False) is (n in FIGURES), f"Q{n}: figure flag")


def check_bank(apk):
    raw_assets = {}
    for index, (first, last) in enumerate(((1, 35), (36, 65), (66, 100)), 1):
        path = SOURCE / "assets" / f"olympiad_{index:02}.json"
        raw_assets[f"assets/{path.name}"] = path.read_bytes()
        questions = json.loads(raw_assets[f"assets/{path.name}"])
        require([q["id"] for q in questions] == list(range(first, last + 1)),
                f"{path.name}: missing, duplicate or out-of-order ID")
        for question in questions:
            check_question(question)
    images = {path.name: png_size(path.read_bytes(), path.name)
              for path in (SOURCE / "res/drawable-nodpi").glob("ihc_q*.png")}
    require(set(images) == {f"ihc_q{n:03}.png" for n in FIGURES}, "Missing or extra figures")
    if apk:
        with ZipFile(apk) as package:
            for name, data in raw_assets.items():
                require(package.read(name) == data, f"{name}: stale or incorrect APK asset")
            bundled = {path.name: png_size(package.read(str(path)), str(path))
                       for path in map(Path, package.namelist())
                       if path.match("res/drawable*/ihc_q*.png")}
            require(bundled == images, "APK figures differ from source names or dimensions")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, help="also check the packaged JSON and diagrams")
    args = parser.parse_args()
    try:
        check_bank(args.apk)
    except (ValueError, KeyError, OSError) as error:
        sys.exit(str(error))
    print(f"Verified {len(KEY)} questions and {len(FIGURES)} figures"
          + (" including the APK assets." if args.apk else "."))

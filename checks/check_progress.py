#!/usr/bin/env python3
"""Run Android progress regressions with the real question assets and JSON reader, without a phone."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile


ROOT = Path(__file__).resolve().parent.parent
FIXTURES = ROOT / "checks/progress"
MAIN = ROOT / "app/src/main"
PACKAGE = MAIN / "java/cn/videosecond/gate"


def main():
    parser_locations = [os.environ.get("ANDROID_LAYOUTLIB_JAR"),
                        "/Applications/Android Studio.app/Contents/plugins/design-tools/lib/layoutlib.jar",
                        "/opt/android-studio/plugins/design-tools/lib/layoutlib.jar"]
    parser = next((Path(p) for p in parser_locations if p and Path(p).is_file()), None)
    if not parser:
        raise SystemExit("Set ANDROID_LAYOUTLIB_JAR to the Android Studio layoutlib.jar containing org.json.")
    java_home = os.environ.get("JAVA_HOME")
    javac = Path(java_home) / "bin/javac" if java_home else shutil.which("javac")
    if not javac:
        raise SystemExit("Install JDK 17 and set JAVA_HOME.")
    java = Path(javac).with_name("java")
    sources = ["QuestionProgress.java", "QuestionBank.java", "OlympiadAnswers.java"]
    with tempfile.TemporaryDirectory(prefix="videosecond-progress-") as tmp:
        root = Path(tmp)
        classes, state = root / "classes", root / "state"
        classes.mkdir()
        state.mkdir()
        subprocess.run([javac, "-encoding", "UTF-8", "-cp", parser, "-d", classes,
                        *sorted(FIXTURES.rglob("*.java")), *(PACKAGE / name for name in sources)], check=True)
        classpath = os.pathsep.join((str(classes), str(parser)))
        for phase in ("old-install", "upgrade", "restart", "review"):
            subprocess.run([java, "-cp", classpath, "cn.videosecond.gate.QuestionProgressCheck",
                            phase, state, MAIN / "assets"], check=True)
        print("Question progress, restart, review access and deck checks passed.")


if __name__ == "__main__":
    main()

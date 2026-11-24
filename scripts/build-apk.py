#!/usr/bin/env python3
"""Build this dependency-free Android app with the SDK and sign it for family use."""
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "app/src/main"
ANDROID = "http://schemas.android.com/apk/res/android"
CONFIG = dict(line.split("=", 1) for line in (ROOT / "gradle.properties").read_text().splitlines()
              if line.startswith("gate"))
SIGNING = ROOT / ".local-signing"


def find_jdk():
    for candidate in (os.environ.get("JAVA_HOME"),
                      "/Applications/Android Studio.app/Contents/jbr/Contents/Home",
                      "/opt/android-studio/jbr"):
        if candidate and (Path(candidate) / "bin/javac").is_file():
            return Path(candidate) / "bin"
    if shutil.which("javac"):
        return Path(shutil.which("javac")).resolve().parent
    sys.exit("Install JDK 17 and set JAVA_HOME.")


def find_sdk():
    for candidate in (os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT"),
                      Path.home() / "Library/Android/sdk", Path.home() / "Android/Sdk"):
        if candidate and (Path(candidate) / "platforms").is_dir():
            return Path(candidate)
    sys.exit("Set ANDROID_HOME to the Android SDK directory.")


def run(*args, env=None):
    subprocess.run([str(arg) for arg in args], env=env, check=True)


def signer_password(jdk):
    store = SIGNING / "family.p12"
    password_file = SIGNING / "password"
    if not SIGNING.exists():
        with tempfile.TemporaryDirectory(prefix=".gate-key-", dir=ROOT) as temp:
            new = Path(temp) / "new"
            new.mkdir(mode=0o700)
            password = secrets.token_urlsafe(36)
            env = dict(os.environ, GATE_STORE_PASSWORD=password)
            run(jdk / "keytool", "-genkeypair", "-storetype", "PKCS12",
                "-keystore", new / "family.p12", "-storepass:env", "GATE_STORE_PASSWORD",
                "-alias", "family", "-keyalg", "RSA", "-keysize", "3072", "-validity", "10950",
                "-dname", "CN=VideoSecond Family", "-noprompt", env=env)
            (new / "family.p12").chmod(0o600)
            (new / "password").write_text(password + "\n")
            (new / "password").chmod(0o600)
            try:
                new.rename(SIGNING)
            except OSError:
                if not SIGNING.is_dir():
                    raise
    if not (store.is_file() and password_file.is_file()):
        sys.exit("Incomplete .local-signing directory. Restore the original family key and password.")
    return password_file.read_text().strip()


def build():
    sdk = find_sdk()
    jdk = find_jdk()
    tools = sdk / "build-tools" / CONFIG["gateBuildTools"]
    android_jar = sdk / "platforms" / ("android-" + CONFIG["gateCompileSdk"]) / "android.jar"
    if not android_jar.is_file() or not (tools / "aapt2").is_file():
        sys.exit(f"Install SDK platform {CONFIG['gateCompileSdk']} and build-tools {CONFIG['gateBuildTools']}.")
    env = dict(os.environ, JAVA_HOME=str(jdk.parent))
    # d8/apksigner launchers find java via PATH on some SDK releases.
    env["PATH"] = str(jdk) + os.pathsep + env.get("PATH", "")
    dist = ROOT / "dist"
    dist.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".build-", dir=dist) as temp:
        work = Path(temp)
        generated = work / "generated"
        classes = work / "classes"
        dex = work / "dex"
        for directory in (generated, classes, dex):
            directory.mkdir()
        ET.register_namespace("android", ANDROID)
        manifest = ET.parse(SOURCE / "AndroidManifest.xml")
        manifest.getroot().set("package", CONFIG["gateApplicationId"])
        manifest.write(work / "AndroidManifest.xml", encoding="utf-8", xml_declaration=True)
        run(tools / "aapt2", "compile", "--dir", SOURCE / "res", "-o", work / "res.zip")
        run(tools / "aapt2", "link", "-I", android_jar, "-A", SOURCE / "assets",
            "--manifest", work / "AndroidManifest.xml",
            "--min-sdk-version", CONFIG["gateMinSdk"], "--target-sdk-version", CONFIG["gateTargetSdk"],
            "--version-code", CONFIG["gateVersionCode"], "--version-name", CONFIG["gateVersionName"],
            "--java", generated, "--auto-add-overlay", "-o", work / "unsigned.apk", work / "res.zip")
        sources = sorted((SOURCE / "java").rglob("*.java")) + sorted(generated.rglob("*.java"))
        boot = os.pathsep.join(map(str, (android_jar, tools / "core-lambda-stubs.jar")))
        run(jdk / "javac", "-source", "8", "-target", "8", "-encoding", "UTF-8",
            "-bootclasspath", boot, "-d", classes, *sources)
        with zipfile.ZipFile(work / "classes.jar", "w") as bundle:
            for path in sorted(classes.rglob("*.class")):
                bundle.write(path, path.relative_to(classes))
        run(tools / "d8", "--release", "--min-api", CONFIG["gateMinSdk"], "--lib", android_jar,
            "--output", dex, work / "classes.jar", env=env)
        with zipfile.ZipFile(work / "unsigned.apk", "a") as package:
            for path in sorted(dex.glob("*.dex")):
                package.write(path, path.name, compress_type=zipfile.ZIP_STORED)
        run(tools / "zipalign", "-f", "4", work / "unsigned.apk", work / "aligned.apk")
        env["GATE_STORE_PASSWORD"] = signer_password(jdk)
        signed = work / "VideoSecond.apk"
        run(tools / "apksigner", "sign", "--ks", SIGNING / "family.p12", "--ks-key-alias", "family",
            "--ks-pass", "env:GATE_STORE_PASSWORD", "--v4-signing-enabled", "false",
            "--out", signed, work / "aligned.apk", env=env)
        run(tools / "apksigner", "verify", "--verbose", signed, env=env)
        run(tools / "zipalign", "-c", "4", signed)
        signed.replace(dist / "VideoSecond.apk")
    print(dist / "VideoSecond.apk")


if __name__ == "__main__":
    build()

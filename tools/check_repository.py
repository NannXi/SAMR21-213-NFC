#!/usr/bin/env python3
"""Check tracked documentation, project inputs and byte manifests without hardware."""
from pathlib import Path
import argparse
import hashlib
import json
import re
import subprocess
from urllib.parse import unquote, urlsplit
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
PROJECT_MANIFEST = Path("firmware/SAMR21_EPD_Keil/manifest.json")
ROOT_MANIFEST = Path("repository_manifest.json")
ARTIFACT_MANIFEST = Path("artifact_manifest.json")

def tracked():
    data = subprocess.check_output(["git", "-C", str(ROOT), "ls-files", "-z"])
    return sorted(set(filter(None, data.decode("utf-8").split("\0"))))

def entry(path):
    data = path.read_bytes()
    return {"bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()}

def write_manifest(relative, paths):
    base = (ROOT / relative).parent
    value = {p.relative_to(base).as_posix(): entry(p) for p in sorted(paths)}
    (ROOT / relative).write_text(
        json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    print("Updated", relative.as_posix(), len(value), "entries")

def update_manifests(files):
    paths = [ROOT / f for f in files]
    if (ROOT / ROOT_MANIFEST).exists():
        project = (ROOT / PROJECT_MANIFEST).parent
        write_manifest(PROJECT_MANIFEST, [
            p for p in paths if project in p.parents and p != ROOT / PROJECT_MANIFEST])
        write_manifest(ROOT_MANIFEST, [p for p in paths if p != ROOT / ROOT_MANIFEST])
    else:
        write_manifest(ARTIFACT_MANIFEST, [
            p for p in paths if p.suffix.lower() in {".bin", ".axf", ".png"}])

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--update-manifests", action="store_true",
                        help="Refresh inventories after reviewing and staging intended files")
    args = parser.parse_args()
    files = tracked()
    if args.update_manifests:
        update_manifests(files)
    errors = []
    counts = {"tracked": len(files), "json": 0, "local_links": 0, "project_inputs": 0, "hashed_files": 0}
    for rel in files:
        path = ROOT / rel
        if not path.is_file():
            errors.append(f"Missing tracked file: {rel}")
            continue
        try:
            if path.suffix.lower() == ".json":
                json.loads(path.read_text(encoding="utf-8-sig"))
                counts["json"] += 1
            if path.suffix.lower() == ".md":
                text = path.read_text(encoding="utf-8-sig")
                fence = chr(96) * 3
                text = re.sub(fence + r".*?" + fence, "", text, flags=re.S)
                targets = re.findall(r"\]\((<[^>]+>|[^)\n]+)\)", text)
                targets += re.findall(r'(?:src|href)="([^"]+)"', text)
                for target in targets:
                    target = target.strip("<>")
                    parsed = urlsplit(target)
                    if parsed.scheme or parsed.netloc or not parsed.path:
                        continue
                    counts["local_links"] += 1
                    if not (path.parent / unquote(parsed.path)).exists():
                        errors.append(f"Broken local link: {rel}: {target}")
            if path.suffix.lower() == ".uvprojx":
                tree = ET.parse(path)
                for field in ["FilePath", "RegisterFile", "SFDFile", "ScatterFile"]:
                    for node in tree.findall(".//" + field):
                        if node.text:
                            target = path.parent / node.text.replace("\\", "/")
                            counts["project_inputs"] += 1
                            if not target.is_file():
                                errors.append(f"Missing Keil input: {rel}: {node.text}")
        except (ValueError, UnicodeError, ET.ParseError) as exc:
            errors.append(f"Invalid file: {rel}: {exc}")
    for rel in [ROOT_MANIFEST, PROJECT_MANIFEST, ARTIFACT_MANIFEST]:
        manifest = ROOT / rel
        if not manifest.exists():
            continue
        value = json.loads(manifest.read_text(encoding="utf-8-sig"))
        if rel == ROOT_MANIFEST:
            expected_keys = set(files) - {ROOT_MANIFEST.as_posix()}
        elif rel == PROJECT_MANIFEST:
            prefix = rel.parent.as_posix() + "/"
            expected_keys = {f[len(prefix):] for f in files if f.startswith(prefix) and f != rel.as_posix()}
        else:
            expected_keys = {f for f in files if Path(f).suffix.lower() in {".bin", ".axf", ".png"}}
        if set(value) != expected_keys:
            errors.append(f"Manifest coverage differs from tracked files: {rel}")
        for target, expected in value.items():
            path = manifest.parent / target
            counts["hashed_files"] += 1
            if not path.is_file() or entry(path) != expected:
                errors.append(f"Manifest mismatch: {rel}: {target}")
    if (ROOT / "android/app/src/main/AndroidManifest.xml").exists():
        attr = "{http://schemas.android.com/apk/res/android}"
        manifest = ET.parse(ROOT / "android/app/src/main/AndroidManifest.xml").getroot()
        evidence = json.loads((ROOT / "docs/android_build_validation.json").read_text(encoding="utf-8"))
        if (manifest.attrib[attr + "versionName"] != evidence["version"] or
                int(manifest.attrib[attr + "versionCode"]) != evidence["version_code"]):
            errors.append("Android manifest version differs from build evidence")
        for required in ["firmware/Tools/original_flash_256KiB_read1.bin",
                         "docs/nfc_eeprom_before.bin"]:
            if not (ROOT / required).is_file():
                errors.append("Missing recovery input: " + required)
    print(json.dumps(counts, indent=2))
    if errors:
        for error in errors:
            print("FAIL:", error)
        raise SystemExit(1)
    print("PASS: repository integrity (offline; hardware and external URLs not exercised)")

if __name__ == "__main__":
    main()

"""Check public documentation links and build-only Firebase configuration."""

from __future__ import annotations

import json
from pathlib import Path
import re
import subprocess
import sys
from urllib.parse import unquote, urlsplit


ROOT = Path(__file__).resolve().parents[1]


def markdown_links(path: Path):
    in_fence = False
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if line.lstrip().startswith(("```", "~~~")):
            in_fence = not in_fence
            continue
        if in_fence:
            continue
        targets = re.findall(r"\[[^\]]*\]\(([^)]+)\)", line)
        targets += re.findall(r'(?:src|href)="([^"]+)"', line)
        for target in targets:
            yield line_number, target.strip().strip("<>")


def main() -> int:
    errors: list[str] = []
    tracked = subprocess.check_output(["git", "ls-files"], cwd=ROOT, text=True).splitlines()
    documents = [ROOT / path for path in tracked if path.endswith(".md") and (ROOT / path).is_file()]
    checked = 0
    for document in documents:
        for line, target in markdown_links(document):
            parsed = urlsplit(target)
            if parsed.scheme or target.startswith("#"):
                continue
            relative = re.sub(r":\d+(?:-\d+)?$", "", unquote(parsed.path))
            resolved = (document.parent / relative).resolve()
            if not resolved.is_relative_to(ROOT) or not resolved.exists():
                errors.append(f"{document.relative_to(ROOT)}:{line}: broken local link: {target}")
            checked += 1

    example = json.loads((ROOT / "app/google-services.json.example").read_text(encoding="utf-8"))
    client = example["client"][0]
    if client["client_info"]["android_client_info"]["package_name"] != "com.monsteraltech.skincare":
        errors.append("Firebase example package does not match the application.")
    if client["api_key"][0]["current_key"] != "AIza" + "0" * 35:
        errors.append("Firebase example must contain only the explicitly fictitious API key.")
    if not any(item["client_type"] == 3 for item in client["oauth_client"]):
        errors.append("Firebase example needs the web client used by generated resources.")

    for path in tracked:
        if path.startswith((".idea/", ".private/", ".agents/", ".claude/")) or Path(path).name in {"google-services.json", "local.properties", "skills-lock.json"} or path.endswith((".jks", ".keystore", ".p12", ".pfx")):
            errors.append(f"Local configuration should not be tracked: {path}")
        if path.endswith(".md") and path != "README.md":
            errors.append(f"Technical documents must remain local: {path}")

    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print(f"Repository checks: OK ({len(documents)} Markdown files, {checked} local links).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

"""Plan, package and publish immutable Android versions from main."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess


TAG = re.compile(r"v1\.0\.([1-9][0-9]*)")
PACKAGE = "com.monsteraltech.skincare"


def run(*args: str, input_text: str | None = None) -> str:
    return subprocess.check_output(args, input=input_text, text=True, encoding="utf-8").strip()


def api(endpoint: str, payload: dict | None = None, missing_ok: bool = False) -> dict | list | None:
    args = ["gh", "api", endpoint]
    if payload is not None:
        args += ["--method", "POST", "--input", "-"]
    result = subprocess.run(args, input=json.dumps(payload) if payload else None,
                            text=True, encoding="utf-8", capture_output=True)
    if result.returncode:
        if missing_ok and "HTTP 404" in result.stderr:
            return None
        raise RuntimeError(result.stderr.strip())
    return json.loads(result.stdout)


def select_version(tags: dict[str, str], commit: str) -> dict:
    versions = {tag: int(TAG.fullmatch(tag).group(1)) for tag in tags if TAG.fullmatch(tag)}
    matches = [tag for tag in versions if tags[tag] == commit]
    if len(matches) > 1:
        raise ValueError("The commit has multiple release versions")
    patch = versions[matches[0]] if matches else max(versions.values(), default=0) + 1
    if patch + 1 > 2100000000:
        raise ValueError("Android versionCode exhausted")
    return {"tag": f"v1.0.{patch}", "versionName": f"1.0.{patch}",
            "versionCode": patch + 1, "commit": commit}


def asset_names(tag: str) -> set[str]:
    return {f"SkinCare-{tag}.apk", "SHA256SUMS", "release.json"}


def reserve_drafts(tags: dict[str, str], releases: list[dict]) -> dict[str, str]:
    reserved = dict(tags)
    for item in releases:
        tag = item["tag_name"]
        if TAG.fullmatch(tag) and tag not in reserved:
            commit = item["target_commitish"]
            if not re.fullmatch(r"[0-9a-f]{40}", commit):
                raise ValueError("An untagged draft must target an immutable commit SHA")
            reserved[tag] = commit
    return reserved


def complete(release: dict, tag: str) -> bool:
    return asset_names(tag) <= {asset["name"] for asset in release["assets"] if asset["size"] > 0}


def release_by_tag(repository: str, tag: str) -> dict | None:
    # GitHub's tag endpoint returns published releases. Drafts need the list/ID API.
    found = api(f"repos/{repository}/releases/tags/{tag}", missing_ok=True)
    if found is not None:
        return found
    page = 1
    while True:
        items = api(f"repos/{repository}/releases?per_page=100&page={page}")
        found = next((item for item in items if item["tag_name"] == tag), None)
        if found is not None:
            return found
        if len(items) < 100:
            return None
        page += 1


def plan(repository: str, output: Path) -> None:
    commit = run("git", "rev-parse", "HEAD")
    tags = {tag: run("git", "rev-parse", f"refs/tags/{tag}^{{commit}}")
            for tag in run("git", "tag", "--list", "v1.0.*").splitlines() if TAG.fullmatch(tag)}
    page = 1
    while True:
        items = api(f"repos/{repository}/releases?per_page=100&page={page}")
        tags = reserve_drafts(tags, items)
        if len(items) < 100:
            break
        page += 1
    version = select_version(tags, commit)
    existing = release_by_tag(repository, version["tag"])
    published = existing is not None and not existing["draft"]
    if published and not complete(existing, version["tag"]):
        raise RuntimeError("Published release is incomplete; refusing to overwrite it")
    with output.open("a", encoding="utf-8") as stream:
        for key, value in {**version, "published": str(published).lower()}.items():
            stream.write(f"{key}={value}\n")
    print(f"{version['tag']} (versionCode {version['versionCode']})")


def package(apk: Path, directory: Path, tag: str, version_code: int, sdk: Path) -> None:
    if not TAG.fullmatch(tag) or version_code != int(TAG.fullmatch(tag).group(1)) + 1:
        raise ValueError("Version name/code mismatch")
    tools = sdk / "build-tools/34.0.0"
    suffix = ".bat" if os.name == "nt" else ""
    certificates = run(str(tools / ("apksigner" + suffix)), "verify", "--verbose", "--print-certs", str(apk))
    fingerprint = re.search(r"Signer #1 certificate SHA-256 digest: ([0-9a-f]+)", certificates)
    if not fingerprint:
        raise ValueError("APK signature was not verified")
    badging = run(str(tools / ("aapt.exe" if os.name == "nt" else "aapt")), "dump", "badging", str(apk))
    name = tag[1:]
    expected = f"package: name='{PACKAGE}' versionCode='{version_code}' versionName='{name}'"
    if expected not in badging or "sdkVersion:'29'" not in badging:
        raise ValueError("APK package/version/minSdk does not match the release")
    directory.mkdir(parents=True, exist_ok=True)
    destination = directory / f"SkinCare-{tag}.apk"
    shutil.copy2(apk, destination)
    digest = hashlib.sha256(destination.read_bytes()).hexdigest()
    metadata = {"tag": tag, "versionName": name, "versionCode": version_code,
                "commit": run("git", "rev-parse", "HEAD"), "applicationId": PACKAGE,
                "minSdk": 29, "apk": destination.name, "sha256": digest,
                "signerCertificateSha256": fingerprint.group(1)}
    manifest = directory / "release.json"
    manifest.write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    manifest_digest = hashlib.sha256(manifest.read_bytes()).hexdigest()
    (directory / "SHA256SUMS").write_text(
        f"{digest}  {destination.name}\n{manifest_digest}  release.json\n", encoding="utf-8")
    print(f"Verified signed APK: {destination.name}")


def validate_package(directory: Path) -> dict:
    metadata = json.loads((directory / "release.json").read_text(encoding="utf-8"))
    if not TAG.fullmatch(metadata["tag"]) or metadata["apk"] != f"SkinCare-{metadata['tag']}.apk":
        raise ValueError("Invalid artifact metadata")
    if metadata["applicationId"] != PACKAGE:
        raise ValueError("Wrong Android package")
    checked = set()
    for line in (directory / "SHA256SUMS").read_text(encoding="utf-8").splitlines():
        digest, name = line.split("  ", 1)
        if name not in {metadata["apk"], "release.json"}:
            raise ValueError("Unexpected checksum path")
        if hashlib.sha256((directory / name).read_bytes()).hexdigest() != digest:
            raise ValueError("Artifact checksum mismatch")
        if name in checked:
            raise ValueError("Duplicate checksum")
        checked.add(name)
    if checked != {metadata["apk"], "release.json"}:
        raise ValueError("Missing artifact checksum")
    if hashlib.sha256((directory / metadata["apk"]).read_bytes()).hexdigest() != metadata["sha256"]:
        raise ValueError("APK checksum mismatch")
    return metadata


def publish(repository: str, directory: Path) -> None:
    if os.environ.get("GITHUB_REF") != "refs/heads/main":
        raise RuntimeError("Only main may publish Android releases")
    metadata = validate_package(directory)
    commit = run("git", "rev-parse", "HEAD")
    if metadata["commit"] != commit:
        raise ValueError("Artifact does not belong to this commit")
    subprocess.run(["git", "merge-base", "--is-ancestor", commit, "origin/main"], check=True)
    tag = metadata["tag"]
    release = release_by_tag(repository, tag)
    if release and not release["draft"]:
        if not complete(release, tag):
            raise RuntimeError("Published release is incomplete")
        print(f"Already published: {release['html_url']}")
        return
    if release:
        ref = api(f"repos/{repository}/git/ref/tags/{tag}", missing_ok=True)
        if ref and run("git", "rev-parse", f"refs/tags/{tag}^{{commit}}") != commit:
            raise ValueError("Release tag belongs to another commit")
        if not ref and release["target_commitish"] != commit:
            raise ValueError("Draft belongs to another commit")
    else:
        body = ("APK instalable de SkinCare para Android 10 o posterior.\n\n"
                "Incluye modo local sin cuenta. Las funciones de análisis son experimentales "
                "y no están validadas para diagnóstico.\n\n"
                f"Código: `{commit}`. Se adjuntan los archivos de comprobación de integridad.")
        release = api(f"repos/{repository}/releases", {"tag_name": tag,
                      "target_commitish": commit, "name": f"SkinCare {metadata['versionName']}",
                      "body": body, "draft": True, "prerelease": False})
    run("gh", "release", "upload", tag, *[str(directory / name) for name in sorted(asset_names(tag))],
        "--repo", repository, "--clobber")
    uploaded = api(f"repos/{repository}/releases/{release['id']}")
    if not complete(uploaded, tag):
        raise RuntimeError("Draft does not contain every verified asset")
    result = subprocess.run(["gh", "api", f"repos/{repository}/releases/{release['id']}",
                             "--method", "PATCH", "--input", "-"],
                            input=json.dumps({"draft": False, "make_latest": "legacy"}),
                            text=True, encoding="utf-8", capture_output=True)
    if result.returncode:
        raise RuntimeError(result.stderr.strip())
    print(f"Published: {json.loads(result.stdout)['html_url']}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    actions = parser.add_subparsers(dest="action", required=True)
    prepare = actions.add_parser("plan")
    prepare.add_argument("--repo", required=True)
    prepare.add_argument("--output", type=Path, required=True)
    pack = actions.add_parser("package")
    pack.add_argument("--apk", type=Path, required=True)
    pack.add_argument("--directory", type=Path, required=True)
    pack.add_argument("--tag", required=True)
    pack.add_argument("--version-code", type=int, required=True)
    pack.add_argument("--sdk", type=Path, required=True)
    public = actions.add_parser("publish")
    public.add_argument("--repo", required=True)
    public.add_argument("--directory", type=Path, required=True)
    args = parser.parse_args()
    if args.action == "plan":
        plan(args.repo, args.output)
    elif args.action == "package":
        package(args.apk, args.directory, args.tag, args.version_code, args.sdk)
    else:
        publish(args.repo, args.directory)


if __name__ == "__main__":
    main()

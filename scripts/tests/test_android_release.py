import hashlib
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch


spec = importlib.util.spec_from_file_location("android_release", Path(__file__).parents[1] / "android-release.py")
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class AndroidReleaseTests(unittest.TestCase):
    def test_first_release_updates_version_code_above_existing_local_builds(self):
        version = release.select_version({}, "new")
        self.assertEqual(("v1.0.1", "1.0.1", 2),
                         (version["tag"], version["versionName"], version["versionCode"]))

    def test_multiple_integrations_increment_the_highest_version(self):
        tags = {"v1.0.2": "a", "v1.0.9": "b", "notes": "c", "v1.0.invalid": "d"}
        self.assertEqual("v1.0.10", release.select_version(tags, "new")["tag"])

    def test_a_retry_reuses_its_version_even_after_newer_integrations(self):
        tags = {"v1.0.2": "retry", "v1.0.9": "newer"}
        self.assertEqual("v1.0.2", release.select_version(tags, "retry")["tag"])

    def test_failed_drafts_reserve_their_version_before_git_tags_exist(self):
        sha = "a" * 40
        tags = release.reserve_drafts({}, [{"tag_name": "v1.0.1", "target_commitish": sha}])
        self.assertEqual("v1.0.1", release.select_version(tags, sha)["tag"])
        self.assertEqual("v1.0.2", release.select_version(tags, "b" * 40)["tag"])

    def test_an_untagged_draft_with_a_moving_branch_target_is_rejected(self):
        with self.assertRaises(ValueError):
            release.reserve_drafts({}, [{"tag_name": "v1.0.1", "target_commitish": "main"}])

    def test_ambiguous_versions_and_android_overflow_are_rejected(self):
        with self.assertRaises(ValueError):
            release.select_version({"v1.0.1": "same", "v1.0.2": "same"}, "same")
        with self.assertRaises(ValueError):
            release.select_version({"v1.0.2099999999": "old"}, "new")

    def test_a_release_is_complete_only_after_every_nonempty_asset_is_present(self):
        assets = [{"name": n, "size": 10} for n in release.asset_names("v1.0.1")]
        self.assertTrue(release.complete({"assets": assets}, "v1.0.1"))
        self.assertFalse(release.complete({"assets": assets[:-1]}, "v1.0.1"))
        assets[0]["size"] = 0
        self.assertFalse(release.complete({"assets": assets}, "v1.0.1"))

    def test_a_branch_cannot_publish_even_a_valid_apk(self):
        with patch.dict(os.environ, {"GITHUB_REF": "refs/heads/feature/example"}):
            with self.assertRaisesRegex(RuntimeError, "Only main"):
                release.publish("owner/repo", Path("nonexistent"))

    def test_downloaded_artifacts_must_match_checksums_before_publishing(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            apk = root / "SkinCare-v1.0.1.apk"
            apk.write_bytes(b"signed APK fixture")
            digest = hashlib.sha256(apk.read_bytes()).hexdigest()
            metadata = {"tag": "v1.0.1", "apk": apk.name, "applicationId": release.PACKAGE,
                        "sha256": digest, "commit": "fixture"}
            manifest = root / "release.json"
            manifest.write_text(json.dumps(metadata), encoding="utf-8")
            metadata_digest = hashlib.sha256(manifest.read_bytes()).hexdigest()
            (root / "SHA256SUMS").write_text(f"{digest}  {apk.name}\n{metadata_digest}  release.json\n", encoding="utf-8")
            self.assertEqual("fixture", release.validate_package(root)["commit"])
            apk.write_bytes(b"tampered")
            with self.assertRaisesRegex(ValueError, "checksum"):
                release.validate_package(root)


if __name__ == "__main__":
    unittest.main()

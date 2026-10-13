import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("check_repository", Path(__file__).parents[1] / "check-repository.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class ModelArtifactCheckTest(unittest.TestCase):
    def test_ci_rejects_replacing_model_without_updating_exact_descriptor(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            model = root / "model.tflite"
            model.write_bytes(b"first artifact")
            descriptor = {"schemaVersion": 1, "filename": model.name, "sha256": hashlib.sha256(model.read_bytes()).hexdigest()}
            (root / "lesion_classifier.json").write_text(json.dumps(descriptor))
            self.assertEqual([], module.check_model_artifact(root))
            model.write_bytes(b"different artifact")
            self.assertIn("SHA-256", module.check_model_artifact(root)[0])

    def test_descriptor_cannot_reference_files_outside_assets(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / "lesion_classifier.json").write_text(json.dumps({"schemaVersion": 1, "filename": "../model.tflite", "sha256": "0" * 64}))
            self.assertIn("filename", module.check_model_artifact(root)[0])

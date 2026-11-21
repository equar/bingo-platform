import json
import os
import tempfile
import unittest
from unittest import mock
from pathlib import Path
from types import SimpleNamespace

from release_provenance import (
    normalize_api_base_url,
    read_app_version,
    read_build_number,
    validate_source_run,
    verify_manifest,
    write_manifest,
)


class ReleaseProvenanceTest(unittest.TestCase):
    def test_build_number_is_read_from_flutter_version(self):
        with tempfile.TemporaryDirectory() as directory:
            pubspec = Path(directory) / "pubspec.yaml"
            pubspec.write_text("version: 0.1.2+24\n", encoding="utf-8")
            self.assertEqual(read_build_number(pubspec), "24")
            self.assertEqual(read_app_version(pubspec), "0.1.2")

    def test_http_is_limited_to_test_api_origin(self):
        self.assertEqual(
            normalize_api_base_url("http://api.test.bakaroo.com/"),
            "http://api.test.bakaroo.com",
        )
        for value in (
            "http://api.bingo.bakaroo.com",
            "http://api.test.bakaroo.com:8080",
            "http://api.test.bakaroo.com/v1",
            "https://api.test.bakaroo.com?debug=true",
        ):
            with self.subTest(value=value), self.assertRaises(ValueError):
                normalize_api_base_url(value)

    def test_write_manifest_records_build_and_source_identity(self):
        args = SimpleNamespace(
            build_number="24",
            app_version="0.1.2",
            application_id="com.bakaroo.bingo_app",
            api_base_url="https://api.bingo.bakaroo.com",
            platform="android",
        )
        environment = {
            "GITHUB_RUN_ID": "12345",
            "GITHUB_REF_NAME": "release",
            "GITHUB_WORKFLOW_REF": (
                "equar/bingo-platform/.github/workflows/"
                "bingo-player-android-internal-testing.yml@refs/heads/release"
            ),
            "GITHUB_SHA": "abcdef",
        }
        with tempfile.TemporaryDirectory() as directory:
            args.output = str(Path(directory) / "provenance.json")
            with mock.patch.dict(os.environ, environment, clear=False):
                write_manifest(args)
            manifest = json.loads(Path(args.output).read_text(encoding="utf-8"))
        self.assertEqual(manifest["api_base_url"], "https://api.bingo.bakaroo.com")
        self.assertEqual(manifest["source_run_id"], "12345")
        self.assertEqual(manifest["source_commit"], "abcdef")

    def test_provenance_accepts_only_matching_production_build(self):
        manifest = {
            "schema_version": 1,
            "platform": "android",
            "build_number": "24",
            "app_version": "0.1.2",
            "application_id": "com.bakaroo.bingo_app",
            "api_base_url": "https://api.bingo.bakaroo.com",
            "source_run_id": "12345",
            "source_branch": "release",
            "source_workflow_ref": (
                "equar/bingo-platform/.github/workflows/"
                "bingo-player-android-internal-testing.yml@refs/heads/release"
            ),
            "source_commit": "abcdef",
        }
        run = {
            "id": 12345,
            "path": ".github/workflows/bingo-player-android-internal-testing.yml",
            "event": "workflow_dispatch",
            "status": "completed",
            "conclusion": "success",
            "head_branch": "release",
            "head_sha": "abcdef",
        }
        with tempfile.TemporaryDirectory() as directory:
            manifest_path = Path(directory) / "provenance.json"
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")

            class Args:
                pass

            args = Args()
            args.manifest = manifest_path
            args.platform = "android"
            args.build_number = "24"
            args.app_version = "0.1.2"
            args.application_id = "com.bakaroo.bingo_app"
            args.source_run_id = "12345"
            args.source_workflow_path = ".github/workflows/bingo-player-android-internal-testing.yml"
            args.source_branch = "release"

            with mock.patch(
                "release_provenance._get_source_run", return_value=run
            ):
                verify_manifest(args)

            manifest["api_base_url"] = "http://api.test.bakaroo.com"
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "production API"):
                with mock.patch(
                    "release_provenance._get_source_run", return_value=run
                ):
                    verify_manifest(args)

    def test_source_run_must_be_successful_and_from_release_branch(self):
        run = {
            "id": 123,
            "path": ".github/workflows/bingo-player-ios-testflight.yml",
            "event": "workflow_dispatch",
            "status": "completed",
            "conclusion": "success",
            "head_branch": "develop",
        }
        with self.assertRaisesRegex(ValueError, "release branch"):
            validate_source_run(
                run,
                "123",
                ".github/workflows/bingo-player-ios-testflight.yml",
                "release",
            )


if __name__ == "__main__":
    unittest.main()

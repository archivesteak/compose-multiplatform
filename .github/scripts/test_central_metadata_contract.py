#!/usr/bin/env python3

from __future__ import annotations

import re
import unittest
from pathlib import Path


REPOSITORY = Path(__file__).resolve().parents[2]
GIT_ATTRIBUTES = REPOSITORY / ".gitattributes"
HELPER = REPOSITORY / "components/buildSrc/src/main/kotlin/CommonMavenProperties.kt"
README = (
    REPOSITORY
    / "components/buildSrc/src/main/resources/central-javadoc/README.md"
)
RESOURCES_WORKFLOW = REPOSITORY / ".github/workflows/publish-resources.yml"
COMPONENTS_BUILD = REPOSITORY / "components/build.gradle.kts"


class CentralMetadataContractTest(unittest.TestCase):
    def test_helper_requires_complete_metadata_and_deterministic_docs(self) -> None:
        helper = HELPER.read_text(encoding="utf-8")
        for expected in (
            "description: String",
            "require(description.isNotBlank())",
            "this.description.set(description)",
            'this.name.set("Jack Harrington")',
            "developerConnection.set(",
            'archiveClassifier.set("javadoc")',
            'destinationDirectory.set(layout.buildDirectory.dir("publications/central-javadoc"))',
            "duplicatesStrategy = DuplicatesStrategy.FAIL",
            "isPreserveFileTimestamps = false",
            "isReproducibleFileOrder = true",
            "entryCompression = ZipEntryCompression.STORED",
            'filePermissions { unix("0644") }',
            'dirPermissions { unix("0755") }',
            "publication.artifact(centralJavadocJar)",
        ):
            self.assertIn(expected, helper)
        self.assertNotIn("publication.artifacts", helper)
        self.assertTrue(README.is_file())
        readme = README.read_text(encoding="utf-8")
        self.assertIn("API documentation", readme)
        self.assertIn("accompanies a Compose Multiplatform publication", readme)
        self.assertNotIn("platform-specific", readme)
        self.assertIn(
            "/components/buildSrc/src/main/resources/central-javadoc/README.md text eol=lf",
            GIT_ATTRIBUTES.read_text(encoding="utf-8"),
        )

    def test_every_component_publication_has_a_nonblank_description(self) -> None:
        calls = []
        for build_file in sorted((REPOSITORY / "components").rglob("build.gradle.kts")):
            text = build_file.read_text(encoding="utf-8")
            for match in re.finditer(r"configureMavenPublication\((.*?)\n\)", text, re.DOTALL):
                calls.append((build_file, match.group(1)))

        self.assertGreater(len(calls), 0)
        for build_file, call in calls:
            with self.subTest(build_file=build_file):
                description = re.search(r'description\s*=\s*"([^"]*)"', call)
                self.assertIsNotNone(description)
                self.assertTrue(description.group(1).strip())

    def test_plugin_local_metadata_cannot_enter_validated_repository(self) -> None:
        workflow = RESOURCES_WORKFLOW.read_text(encoding="utf-8")
        step = workflow.split(
            "- name: Publish and verify the exact fork Gradle plugin", 1
        )[1].split("- name: Upload validated core, resources, and Gradle plugin repository", 1)[0]
        self.assertIn("plugin-publication-repository", step)
        self.assertIn("plugin-publication-provenance.json", step)
        self.assertIn("mapfile -t plugin_directories", step)
        self.assertIn("test ! -e \"$destination_directory\"", step)
        self.assertIn('cp -a "$source_directory" "$destination_directory"', step)
        self.assertEqual(step.count("verify_plugin_publication.py"), 2)
        self.assertIn('"-Dmaven.repo.local=$plugin_repository"', step)
        self.assertNotIn(
            '"-Dmaven.repo.local=$RUNNER_TEMP/validated-resources/repository"',
            step,
        )

    def test_central_verifier_runs_before_validated_repository_upload(self) -> None:
        workflow = RESOURCES_WORKFLOW.read_text(encoding="utf-8")
        verifier = (
            "python3 core-contract/.github/scripts/verify-central-publications.py "
            "\\\n            \"$RUNNER_TEMP/validated-resources/repository\""
        )
        upload = "- name: Upload validated core, resources, and Gradle plugin repository"
        self.assertIn(verifier, workflow)
        self.assertLess(workflow.index(verifier), workflow.index(upload))

    def test_web_build_uses_the_kotlin_pinned_system_node(self) -> None:
        workflow = RESOURCES_WORKFLOW.read_text(encoding="utf-8")
        self.assertIn(
            "actions/setup-node@820762786026740c76f36085b0efc47a31fe5020 # v7.0.0",
            workflow,
        )
        self.assertIn('node-version: "24.10.0"', workflow)
        self.assertIn("if: matrix.owner == 'web'", workflow)
        self.assertEqual(
            workflow.count('"-Pcompose.nodejs.download=$NODE_DOWNLOAD"'),
            2,
        )

        build = COMPONENTS_BUILD.read_text(encoding="utf-8")
        self.assertIn("allprojects {", build)
        self.assertIn('gradleProperty("compose.nodejs.download")', build)
        self.assertIn("extensions.configure<NodeJsEnvSpec>", build)
        self.assertIn("extensions.configure<WasmNodeJsEnvSpec>", build)
        self.assertEqual(build.count("download.set(downloadNode)"), 2)


if __name__ == "__main__":
    unittest.main()

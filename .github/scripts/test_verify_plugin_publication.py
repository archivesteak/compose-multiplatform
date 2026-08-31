from __future__ import annotations

import hashlib
import json
import tempfile
import unittest
import zipfile
from pathlib import Path

from verify_plugin_publication import (
    ARTIFACT,
    GROUP,
    IMPLEMENTATION_CLASS,
    MARKER_ARTIFACT,
    PLUGIN_ID,
    VERSION,
    ContractError,
    verify_publication,
)


SOURCE = "4" * 40
NAMESPACE = "http://maven.apache.org/POM/4.0.0"


def metadata_xml() -> str:
    return """
  <name>Compose Multiplatform MinGW Gradle Plugin</name>
  <description>Compose Multiplatform Gradle plugin with Kotlin/Native mingwX64 support</description>
  <url>https://github.com/archivesteak/compose-multiplatform</url>
  <licenses><license>
    <name>The Apache License, Version 2.0</name>
    <url>https://www.apache.org/licenses/LICENSE-2.0.txt</url>
  </license></licenses>
  <developers><developer>
    <id>archivesteak</id><name>archivesteak</name>
    <url>https://github.com/archivesteak</url>
  </developer></developers>
  <scm>
    <connection>scm:git:https://github.com/archivesteak/compose-multiplatform.git</connection>
    <developerConnection>scm:git:ssh://git@github.com/archivesteak/compose-multiplatform.git</developerConnection>
    <url>https://github.com/archivesteak/compose-multiplatform</url>
  </scm>"""


def implementation_pom(dependency_group: str = "org.jetbrains.compose.hot-reload") -> str:
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="{NAMESPACE}">
  <modelVersion>4.0.0</modelVersion>
  <groupId>{GROUP}</groupId><artifactId>{ARTIFACT}</artifactId><version>{VERSION}</version>
  {metadata_xml()}
  <dependencies><dependency>
    <groupId>{dependency_group}</groupId><artifactId>hot-reload-gradle-plugin</artifactId>
    <version>1.2.0-beta01</version><scope>runtime</scope>
  </dependency></dependencies>
</project>
"""


def marker_pom(dependency_artifact: str = ARTIFACT) -> str:
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="{NAMESPACE}">
  <modelVersion>4.0.0</modelVersion>
  <groupId>{GROUP}</groupId><artifactId>{MARKER_ARTIFACT}</artifactId><version>{VERSION}</version>
  <packaging>pom</packaging>
  {metadata_xml()}
  <dependencies><dependency>
    <groupId>{GROUP}</groupId><artifactId>{dependency_artifact}</artifactId><version>{VERSION}</version>
  </dependency></dependencies>
</project>
"""


def write_zip(path: Path, entries: dict[str, bytes]) -> None:
    with zipfile.ZipFile(path, "w") as archive:
        for name, payload in entries.items():
            archive.writestr(name, payload)


def create_publication(root: Path) -> tuple[Path, Path, Path]:
    repository = root / "repository"
    implementation = repository.joinpath(*GROUP.split("."), ARTIFACT, VERSION)
    marker = repository.joinpath(*GROUP.split("."), MARKER_ARTIFACT, VERSION)
    implementation.mkdir(parents=True)
    marker.mkdir(parents=True)
    base = f"{ARTIFACT}-{VERSION}"
    jar = implementation / f"{base}.jar"
    descriptor = f"implementation-class={IMPLEMENTATION_CLASS}\n".encode()
    write_zip(
        jar,
        {
            f"META-INF/gradle-plugins/{PLUGIN_ID}.properties": descriptor,
            "META-INF/gradle-plugins/org.jetbrains.compose.properties": descriptor,
            "org/jetbrains/compose/ComposePlugin.class": b"class",
        },
    )
    write_zip(implementation / f"{base}-sources.jar", {"source.kt": b"source"})
    write_zip(implementation / f"{base}-javadoc.jar", {"index.html": b"docs"})
    sources_jar = implementation / f"{base}-sources.jar"
    javadoc_jar = implementation / f"{base}-javadoc.jar"
    artifacts = (jar, jar, sources_jar, javadoc_jar)
    artifact_variants = []
    for index, artifact in enumerate(artifacts):
        artifact_data = artifact.read_bytes()
        artifact_variants.append(
            {
                "name": (
                    "apiElements",
                    "runtimeElements",
                    "sourcesElements",
                    "javadocElements",
                )[index],
                "dependencies": (
                    [
                        {
                            "group": "org.jetbrains.compose.hot-reload",
                            "module": "hot-reload-gradle-plugin",
                            "version": {"prefers": "1.2.0-beta01"},
                        }
                    ]
                    if index == 1
                    else []
                ),
                "files": [
                    {
                        "name": artifact.name,
                        "url": artifact.name,
                        "size": len(artifact_data),
                        "sha256": hashlib.sha256(artifact_data).hexdigest(),
                        "sha512": hashlib.sha512(artifact_data).hexdigest(),
                    }
                ],
            }
        )
    module = {
        "formatVersion": "1.1",
        "component": {"group": GROUP, "module": ARTIFACT, "version": VERSION},
        "variants": artifact_variants,
    }
    (implementation / f"{base}.module").write_text(
        json.dumps(module), encoding="utf-8"
    )
    implementation_pom_path = implementation / f"{base}.pom"
    implementation_pom_path.write_text(implementation_pom(), encoding="utf-8")
    marker_pom_path = marker / f"{MARKER_ARTIFACT}-{VERSION}.pom"
    marker_pom_path.write_text(marker_pom(), encoding="utf-8")
    return repository, implementation_pom_path, marker_pom_path


class VerifyPluginPublicationTest(unittest.TestCase):
    def test_accepts_exact_publication_and_hashes_all_files(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            repository, _, _ = create_publication(root)
            report = root / "reports" / "plugin-provenance.json"
            result = verify_publication(repository, SOURCE, report)

            self.assertEqual(result["sourceRef"], SOURCE)
            self.assertEqual(result["pluginId"], PLUGIN_ID)
            self.assertEqual(len(result["files"]), 6)
            self.assertEqual(
                json.loads(report.read_text(encoding="utf-8")),
                result,
            )

    def test_rejects_marker_not_pointing_to_exact_fork_implementation(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            repository, _, marker = create_publication(root)
            marker.write_text(marker_pom("something-else"), encoding="utf-8")
            with self.assertRaises(ContractError):
                verify_publication(repository, SOURCE, root / "report.json")

    def test_rejects_upstream_dependency_in_pom(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            repository, implementation, _ = create_publication(root)
            implementation.write_text(
                implementation_pom("org.jetbrains.compose.ui"),
                encoding="utf-8",
            )
            with self.assertRaises(ContractError):
                verify_publication(repository, SOURCE, root / "report.json")

    def test_rejects_wrong_plugin_descriptor(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            repository, _, _ = create_publication(root)
            jar = repository.joinpath(
                *GROUP.split("."),
                ARTIFACT,
                VERSION,
                f"{ARTIFACT}-{VERSION}.jar",
            )
            descriptor = b"implementation-class=org.jetbrains.compose.WrongPlugin\n"
            write_zip(
                jar,
                {
                    f"META-INF/gradle-plugins/{PLUGIN_ID}.properties": descriptor,
                    "META-INF/gradle-plugins/org.jetbrains.compose.properties": descriptor,
                },
            )
            with self.assertRaises(ContractError):
                verify_publication(repository, SOURCE, root / "report.json")

    def test_rejects_upstream_dependency_in_module_metadata(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            repository, _, _ = create_publication(root)
            module = repository.joinpath(
                *GROUP.split("."),
                ARTIFACT,
                VERSION,
                f"{ARTIFACT}-{VERSION}.module",
            )
            metadata = json.loads(module.read_text(encoding="utf-8"))
            metadata["variants"][1]["dependencies"][0]["group"] = (
                "org.jetbrains.compose.ui"
            )
            module.write_text(json.dumps(metadata), encoding="utf-8")
            with self.assertRaises(ContractError):
                verify_publication(repository, SOURCE, root / "report.json")

    def test_rejects_artifact_not_matching_module_hashes(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            repository, _, _ = create_publication(root)
            jar = repository.joinpath(
                *GROUP.split("."),
                ARTIFACT,
                VERSION,
                f"{ARTIFACT}-{VERSION}.jar",
            )
            jar.write_bytes(jar.read_bytes() + b"tampered")
            with self.assertRaises(ContractError):
                verify_publication(repository, SOURCE, root / "report.json")


if __name__ == "__main__":
    unittest.main()

from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from prepare_resources_shard import ContractError, prepare_shard


CORE = "1" * 40
SKIA = "2" * 40
SKIKO = "3" * 40
RESOURCES = "4" * 40
GROUP = "io.github.archivesteak.compose.components"
VERSION = "1.12.0-beta02-mingw"


def requirements() -> dict[str, object]:
    sources = {
        "compose-core": CORE,
        "resources": RESOURCES,
        "skia": SKIA,
        "skiko": SKIKO,
    }
    return {
        "schemaVersion": 2,
        "groupPrefix": GROUP,
        "platformOwners": {
            "common": "windows",
            "jvm": "windows",
            "mingwX64": "windows",
            "iosArm64": "apple",
            "js": "web",
        },
        "sourceProvenance": {owner: sources for owner in ("windows", "apple", "web")},
        "modules": [
            {
                "coordinate": f"{GROUP}:components-resources:{VERSION}",
                "requiredVariants": {
                    "common": ["metadataApiElements"],
                    "jvm": ["desktopApiElements-published"],
                    "mingwX64": ["mingwX64ApiElements-published"],
                    "iosArm64": ["iosArm64ApiElements-published"],
                    "js": ["jsApiElements-published"],
                },
                "targetModules": {
                    "jvm": "components-resources-desktop",
                    "mingwX64": "components-resources-mingwX64",
                    "iosArm64": "components-resources-iosArm64",
                    "js": "components-resources-js",
                },
            }
        ],
    }


def add_publication(repository: Path, artifact: str) -> None:
    version = repository.joinpath(*GROUP.split("."), artifact, VERSION)
    version.mkdir(parents=True)
    (version / f"{artifact}-{VERSION}.module").write_text("{}", encoding="utf-8")
    (version / f"{artifact}-{VERSION}.pom").write_text("<project/>", encoding="utf-8")
    (version / f"{artifact}-{VERSION}.jar").write_bytes(b"payload")


class PrepareResourcesShardTest(unittest.TestCase):
    def test_collects_only_owner_modules_and_writes_exact_marker(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            repository = root / "m2"
            for artifact in (
                "components-resources",
                "components-resources-desktop",
                "components-resources-mingwX64",
            ):
                add_publication(repository, artifact)
            contract = root / "requirements.json"
            contract.write_text(json.dumps(requirements()), encoding="utf-8")
            destination = root / "stage"
            marker = prepare_shard(
                owner="windows",
                source_repository=repository,
                destination=destination,
                requirements_path=contract,
                resources_ref=RESOURCES,
                core_sources={"compose": CORE, "skia": SKIA, "skiko": SKIKO},
            )

            self.assertEqual(
                {path.name for path in destination.iterdir()},
                {"io", "provenance"},
            )
            self.assertEqual(
                json.loads(marker.read_text(encoding="utf-8")),
                {
                    "schemaVersion": 1,
                    "owner": "windows",
                    "sources": {
                        "compose-core": CORE,
                        "resources": RESOURCES,
                        "skia": SKIA,
                        "skiko": SKIKO,
                    },
                },
            )

    def test_rejects_stale_or_partial_repository(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            repository = root / "m2"
            add_publication(repository, "components-resources")
            add_publication(repository, "components-resources-desktop")
            add_publication(repository, "unrelated-component")
            contract = root / "requirements.json"
            contract.write_text(json.dumps(requirements()), encoding="utf-8")
            with self.assertRaises(ContractError):
                prepare_shard(
                    owner="windows",
                    source_repository=repository,
                    destination=root / "stage",
                    requirements_path=contract,
                    resources_ref=RESOURCES,
                    core_sources={"compose": CORE, "skia": SKIA, "skiko": SKIKO},
                )


if __name__ == "__main__":
    unittest.main()

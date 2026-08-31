from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from validate_core_artifact_inputs import ContractError, validate_contract


CORE = "1" * 40
SKIA = "2" * 40
SKIKO = "3" * 40
RESOURCES = "4" * 40


def write_json(root: Path, name: str, value: object) -> Path:
    path = root / name
    path.write_text(json.dumps(value), encoding="utf-8")
    return path


def core_requirements() -> dict[str, object]:
    return {
        "schemaVersion": 2,
        "groupPrefix": "io.github.archivesteak",
        "sourceProvenance": {
            "windows": {"compose": CORE, "skia": SKIA, "skiko": SKIKO},
            "apple": {"compose": CORE, "skiko": SKIKO},
            "web": {"compose": CORE, "skiko": SKIKO},
        },
    }


def resources_requirements() -> dict[str, object]:
    sources = {
        "compose-core": CORE,
        "resources": RESOURCES,
        "skia": SKIA,
        "skiko": SKIKO,
    }
    return {
        "schemaVersion": 2,
        "groupPrefix": "io.github.archivesteak.compose.components",
        "sourceProvenance": {owner: sources for owner in ("windows", "apple", "web")},
    }


class ValidateCoreArtifactInputsTest(unittest.TestCase):
    def test_accepts_exact_core_and_resources_contract(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            sources = validate_contract(
                repository="archivesteak/compose-multiplatform-core",
                core_contract_ref="9" * 40,
                core_ref=CORE,
                run_ids={"windows": "101", "apple": "102", "web": "103"},
                core_requirements_path=write_json(root, "core.json", core_requirements()),
                resources_ref=RESOURCES,
                resources_requirements_path=write_json(
                    root,
                    "resources.json",
                    resources_requirements(),
                ),
            )
        self.assertEqual(
            sources,
            {"compose": CORE, "skia": SKIA, "skiko": SKIKO},
        )

    def test_rejects_wrong_repository_ref_or_run_id(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            core_path = write_json(Path(temporary), "core.json", core_requirements())
            cases = [
                {
                    "repository": "someone/else",
                    "core_ref": CORE,
                    "run_ids": {"windows": "1", "apple": "2", "web": "3"},
                },
                {
                    "repository": "archivesteak/compose-multiplatform-core",
                    "core_ref": "A" * 40,
                    "run_ids": {"windows": "1", "apple": "2", "web": "3"},
                },
                {
                    "repository": "archivesteak/compose-multiplatform-core",
                    "core_ref": CORE,
                    "run_ids": {"windows": "0", "apple": "2", "web": "3"},
                },
            ]
            for case in cases:
                with self.subTest(case=case), self.assertRaises(ContractError):
                    validate_contract(
                        **case,
                        core_contract_ref="9" * 40,
                        core_requirements_path=core_path,
                    )

    def test_rejects_mixed_or_placeholder_provenance(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            mixed = core_requirements()
            mixed["sourceProvenance"]["apple"]["compose"] = "5" * 40  # type: ignore[index]
            placeholder = core_requirements()
            placeholder["sourceProvenance"]["web"]["skiko"] = "0" * 40  # type: ignore[index]
            weakened = core_requirements()
            del weakened["sourceProvenance"]["windows"]["skia"]  # type: ignore[index]
            for name, requirements in (
                ("mixed", mixed),
                ("placeholder", placeholder),
                ("weakened", weakened),
            ):
                with self.subTest(name=name), self.assertRaises(ContractError):
                    validate_contract(
                        repository="archivesteak/compose-multiplatform-core",
                        core_contract_ref="9" * 40,
                        core_ref=CORE,
                        run_ids={"windows": "1", "apple": "2", "web": "3"},
                        core_requirements_path=write_json(
                            root,
                            f"{name}.json",
                            requirements,
                        ),
                    )

    def test_rejects_resources_not_extending_selected_core(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            resources = resources_requirements()
            resources["sourceProvenance"]["windows"]["skia"] = "6" * 40  # type: ignore[index]
            with self.assertRaises(ContractError):
                validate_contract(
                    repository="archivesteak/compose-multiplatform-core",
                    core_contract_ref="9" * 40,
                    core_ref=CORE,
                    run_ids={"windows": "1", "apple": "2", "web": "3"},
                    core_requirements_path=write_json(root, "core.json", core_requirements()),
                    resources_ref=RESOURCES,
                    resources_requirements_path=write_json(
                        root,
                        "resources.json",
                        resources,
                    ),
                )


if __name__ == "__main__":
    unittest.main()

#!/usr/bin/env python3
"""Validate the immutable core-artifact contract used by downstream workflows."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any, Iterable


COMMIT_PATTERN = re.compile(r"[0-9a-f]{40}\Z")
REPOSITORY_PATTERN = re.compile(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+\Z")
RUN_ID_PATTERN = re.compile(r"[1-9][0-9]*\Z")
OWNERS = ("windows", "apple", "web")
EXPECTED_CORE_REPOSITORY = "archivesteak/compose-multiplatform-core"
EXPECTED_CORE_GROUP_PREFIX = "io.github.archivesteak"
EXPECTED_RESOURCES_GROUP_PREFIX = "io.github.archivesteak.compose.components"
PLACEHOLDER_COMMIT = "0" * 40


class ContractError(ValueError):
    """The supplied workflow inputs or checked-in contract are unsafe."""


def _reject_duplicate_keys(pairs: Iterable[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise ContractError(f"duplicate JSON key {key!r}")
        result[key] = value
    return result


def load_json(path: Path, description: str) -> dict[str, Any]:
    if path.is_symlink() or not path.is_file():
        raise ContractError(f"{description} must be a regular file: {path}")
    try:
        value = json.loads(
            path.read_text(encoding="utf-8"),
            object_pairs_hook=_reject_duplicate_keys,
        )
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise ContractError(f"cannot read {description} {path}: {error}") from error
    if not isinstance(value, dict):
        raise ContractError(f"{description} must contain a JSON object: {path}")
    return value


def validate_commit(value: object, description: str) -> str:
    if not isinstance(value, str) or COMMIT_PATTERN.fullmatch(value) is None:
        raise ContractError(
            f"{description} must be a full lowercase 40-character commit SHA"
        )
    if value == PLACEHOLDER_COMMIT:
        raise ContractError(f"{description} is still the all-zero release placeholder")
    return value


def validate_repository(value: str) -> str:
    if REPOSITORY_PATTERN.fullmatch(value) is None:
        raise ContractError("core_repository must have the exact owner/repository form")
    if value != EXPECTED_CORE_REPOSITORY:
        raise ContractError(
            f"core_repository must be {EXPECTED_CORE_REPOSITORY}, not {value}"
        )
    return value


def validate_run_id(value: str, description: str) -> str:
    if RUN_ID_PATTERN.fullmatch(value) is None:
        raise ContractError(f"{description} must be a positive decimal GitHub run ID")
    return value


def source_commits(requirements: dict[str, Any], description: str) -> dict[str, str]:
    if requirements.get("schemaVersion") != 2:
        raise ContractError(f"{description} must use schemaVersion 2")
    provenance = requirements.get("sourceProvenance")
    if not isinstance(provenance, dict) or set(provenance) != set(OWNERS):
        raise ContractError(
            f"{description} sourceProvenance must contain exactly {', '.join(OWNERS)}"
        )

    result: dict[str, str] = {}
    for owner in OWNERS:
        sources = provenance[owner]
        if not isinstance(sources, dict) or not sources:
            raise ContractError(f"{description} sourceProvenance[{owner!r}] is empty")
        for name, raw_commit in sources.items():
            if not isinstance(name, str) or not name:
                raise ContractError(f"{description} contains an invalid source name")
            commit = validate_commit(
                raw_commit,
                f"{description} sourceProvenance[{owner!r}][{name!r}]",
            )
            previous = result.setdefault(name, commit)
            if previous != commit:
                raise ContractError(
                    f"{description} gives source {name!r} inconsistent commits: "
                    f"{previous} and {commit}"
                )
    return result


def validate_source_layout(
    requirements: dict[str, Any],
    expected: dict[str, set[str]],
    description: str,
) -> None:
    provenance = requirements["sourceProvenance"]
    for owner in OWNERS:
        actual = set(provenance[owner])
        if actual != expected[owner]:
            raise ContractError(
                f"{description} source names for {owner} differ: "
                f"expected {sorted(expected[owner])}, found {sorted(actual)}"
            )


def validate_contract(
    *,
    repository: str,
    core_contract_ref: str,
    core_ref: str,
    run_ids: dict[str, str],
    core_requirements_path: Path,
    resources_ref: str | None = None,
    resources_requirements_path: Path | None = None,
) -> dict[str, str]:
    validate_repository(repository)
    validate_commit(core_contract_ref, "core_contract_ref")
    core_ref = validate_commit(core_ref, "core_ref")
    if set(run_ids) != set(OWNERS):
        raise ContractError("run IDs must be supplied for windows, apple, and web")
    for owner in OWNERS:
        validate_run_id(run_ids[owner], f"core_{owner}_run_id")

    core_requirements = load_json(core_requirements_path, "core requirements")
    core_sources = source_commits(core_requirements, "core requirements")
    if core_requirements.get("groupPrefix") != EXPECTED_CORE_GROUP_PREFIX:
        raise ContractError(
            f"core requirements groupPrefix must be {EXPECTED_CORE_GROUP_PREFIX}"
        )
    validate_source_layout(
        core_requirements,
        {
            "windows": {"compose", "skia", "skiko"},
            "apple": {"compose", "skiko"},
            "web": {"compose", "skiko"},
        },
        "core requirements",
    )
    if core_sources.get("compose") != core_ref:
        raise ContractError(
            "core_ref does not match the exact compose source pinned by the core requirements: "
            f"{core_ref} != {core_sources.get('compose', '<missing>')}"
        )

    if (resources_ref is None) != (resources_requirements_path is None):
        raise ContractError(
            "resources_ref and resources_requirements_path must be supplied together"
        )
    if resources_ref is not None and resources_requirements_path is not None:
        resources_ref = validate_commit(resources_ref, "source_ref")
        resources_requirements = load_json(
            resources_requirements_path,
            "resources requirements",
        )
        resource_sources = source_commits(
            resources_requirements,
            "resources requirements",
        )
        if resources_requirements.get("groupPrefix") != EXPECTED_RESOURCES_GROUP_PREFIX:
            raise ContractError(
                "resources requirements groupPrefix must be "
                f"{EXPECTED_RESOURCES_GROUP_PREFIX}"
            )
        resource_source_names = {"compose-core", "resources", "skia", "skiko"}
        validate_source_layout(
            resources_requirements,
            {owner: resource_source_names for owner in OWNERS},
            "resources requirements",
        )
        expected_resource_sources = {
            "compose-core": core_sources.get("compose"),
            "resources": resources_ref,
            "skia": core_sources.get("skia"),
            "skiko": core_sources.get("skiko"),
        }
        if any(value is None for value in expected_resource_sources.values()):
            missing = sorted(
                name
                for name, value in expected_resource_sources.items()
                if value is None
            )
            raise ContractError(
                "core requirements lack sources needed by resources provenance: "
                + ", ".join(missing)
            )
        if resource_sources != expected_resource_sources:
            raise ContractError(
                "resources provenance does not exactly extend the selected core provenance: "
                f"expected {expected_resource_sources}, found {resource_sources}"
            )

    return core_sources


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--core-repository", required=True)
    parser.add_argument("--core-contract-ref", required=True)
    parser.add_argument("--core-ref", required=True)
    parser.add_argument("--core-windows-run-id", required=True)
    parser.add_argument("--core-apple-run-id", required=True)
    parser.add_argument("--core-web-run-id", required=True)
    parser.add_argument("--core-requirements", required=True, type=Path)
    parser.add_argument("--resources-ref")
    parser.add_argument("--resources-requirements", type=Path)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        sources = validate_contract(
            repository=args.core_repository,
            core_contract_ref=args.core_contract_ref,
            core_ref=args.core_ref,
            run_ids={
                "windows": args.core_windows_run_id,
                "apple": args.core_apple_run_id,
                "web": args.core_web_run_id,
            },
            core_requirements_path=args.core_requirements,
            resources_ref=args.resources_ref,
            resources_requirements_path=args.resources_requirements,
        )
    except ContractError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1
    print("validated immutable core artifact inputs")
    for name, commit in sorted(sources.items()):
        print(f"  {name}: {commit}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

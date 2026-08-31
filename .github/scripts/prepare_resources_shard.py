#!/usr/bin/env python3
"""Collect one host-owned resources publication and attach exact provenance."""

from __future__ import annotations

import argparse
import json
import shutil
import sys
from pathlib import Path
from typing import Any

from validate_core_artifact_inputs import (
    ContractError,
    OWNERS,
    load_json,
    source_commits,
    validate_commit,
)


EXPECTED_COORDINATE = (
    "io.github.archivesteak.compose.components",
    "components-resources",
    "1.12.0-beta02-mingw",
)


def module_requirement(requirements: dict[str, Any]) -> dict[str, Any]:
    modules = requirements.get("modules")
    if not isinstance(modules, list) or len(modules) != 1:
        raise ContractError("resources requirements must contain exactly one root module")
    module = modules[0]
    if not isinstance(module, dict):
        raise ContractError("resources module requirement must be an object")
    if module.get("coordinate") != ":".join(EXPECTED_COORDINATE):
        raise ContractError(
            "resources requirements contain the wrong root coordinate: "
            f"{module.get('coordinate')!r}"
        )
    return module


def expected_artifacts(requirements: dict[str, Any], owner: str) -> set[str]:
    platform_owners = requirements.get("platformOwners")
    if not isinstance(platform_owners, dict) or platform_owners.get("common") != "windows":
        raise ContractError("resources common publication must be owned by windows")
    module = module_requirement(requirements)
    required_variants = module.get("requiredVariants")
    target_modules = module.get("targetModules")
    if not isinstance(required_variants, dict) or not isinstance(target_modules, dict):
        raise ContractError("resources requirements lack variants or target modules")

    artifacts = {EXPECTED_COORDINATE[1]}
    for platform in required_variants:
        if platform == "common" or platform_owners.get(platform) != owner:
            continue
        artifact = target_modules.get(platform)
        if not isinstance(artifact, str) or not artifact:
            raise ContractError(f"resources requirements lack targetModules[{platform!r}]")
        artifacts.add(artifact)
    return artifacts


def ensure_tree_has_no_symlinks(root: Path) -> None:
    if root.is_symlink():
        raise ContractError(f"publication path must not be a symlink: {root}")
    for path in root.rglob("*"):
        if path.is_symlink():
            raise ContractError(f"publication contains a symlink: {path}")


def prepare_shard(
    *,
    owner: str,
    source_repository: Path,
    destination: Path,
    requirements_path: Path,
    resources_ref: str,
    core_sources: dict[str, str],
) -> Path:
    if owner not in OWNERS:
        raise ContractError(f"invalid resources owner {owner!r}")
    resources_ref = validate_commit(resources_ref, "source_ref")
    requirements = load_json(requirements_path, "resources requirements")
    if requirements.get("schemaVersion") != 2:
        raise ContractError("resources requirements must use schemaVersion 2")
    if requirements.get("groupPrefix") != EXPECTED_COORDINATE[0]:
        raise ContractError("resources requirements contain the wrong groupPrefix")
    expected_sources = source_commits(requirements, "resources requirements")
    actual_sources = {
        "compose-core": validate_commit(core_sources.get("compose"), "core compose source"),
        "resources": resources_ref,
        "skia": validate_commit(core_sources.get("skia"), "core skia source"),
        "skiko": validate_commit(core_sources.get("skiko"), "core skiko source"),
    }
    if expected_sources != actual_sources:
        raise ContractError(
            "resources requirements do not match the exact selected source commits: "
            f"expected {expected_sources}, found {actual_sources}"
        )

    source_repository = source_repository.resolve()
    if source_repository.is_symlink() or not source_repository.is_dir():
        raise ContractError(
            f"source Maven repository must be a regular directory: {source_repository}"
        )
    destination = destination.resolve()
    if destination.exists():
        raise ContractError(f"destination must be fresh and absent: {destination}")

    group, root_artifact, version = EXPECTED_COORDINATE
    source_group = source_repository.joinpath(*group.split("."))
    if source_group.is_symlink() or not source_group.is_dir():
        raise ContractError(f"source repository has no resources group: {source_group}")
    artifacts = expected_artifacts(requirements, owner)
    existing_artifacts = {path.name for path in source_group.iterdir() if path.is_dir()}
    unexpected = existing_artifacts - artifacts
    missing = artifacts - existing_artifacts
    if missing or unexpected:
        details: list[str] = []
        if missing:
            details.append("missing " + ", ".join(sorted(missing)))
        if unexpected:
            details.append("unexpected " + ", ".join(sorted(unexpected)))
        raise ContractError(
            f"{owner} resources artifact set differs: " + "; ".join(details)
        )

    destination_group = destination.joinpath(*group.split("."))
    for artifact in sorted(artifacts):
        source_version = source_group / artifact / version
        module_file = source_version / f"{artifact}-{version}.module"
        pom_file = source_version / f"{artifact}-{version}.pom"
        if not module_file.is_file() or not pom_file.is_file():
            raise ContractError(
                f"incomplete resources publication {group}:{artifact}:{version}"
            )
        ensure_tree_has_no_symlinks(source_version)
        shutil.copytree(source_version, destination_group / artifact / version)

    provenance = destination / "provenance" / f"{owner}.json"
    provenance.parent.mkdir(parents=True)
    provenance.write_text(
        json.dumps(
            {
                "schemaVersion": 1,
                "owner": owner,
                "sources": actual_sources,
            },
            indent=2,
            sort_keys=True,
        )
        + "\n",
        encoding="utf-8",
        newline="\n",
    )
    if {path.name for path in destination.iterdir()} != {"io", "provenance"}:
        raise ContractError("prepared resources shard has unexpected root entries")
    return provenance


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--owner", required=True, choices=OWNERS)
    parser.add_argument("--source-repository", required=True, type=Path)
    parser.add_argument("--destination", required=True, type=Path)
    parser.add_argument("--requirements", required=True, type=Path)
    parser.add_argument("--resources-ref", required=True)
    parser.add_argument("--core-compose-ref", required=True)
    parser.add_argument("--core-skia-ref", required=True)
    parser.add_argument("--core-skiko-ref", required=True)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        marker = prepare_shard(
            owner=args.owner,
            source_repository=args.source_repository,
            destination=args.destination,
            requirements_path=args.requirements,
            resources_ref=args.resources_ref,
            core_sources={
                "compose": args.core_compose_ref,
                "skia": args.core_skia_ref,
                "skiko": args.core_skiko_ref,
            },
        )
    except ContractError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1
    print(f"prepared exact resources shard: {marker.parent.parent}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

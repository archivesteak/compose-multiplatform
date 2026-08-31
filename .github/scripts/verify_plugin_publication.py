#!/usr/bin/env python3
"""Verify the exact fork Gradle plugin publication and write its provenance."""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
import zipfile
from collections import Counter
from pathlib import Path, PurePosixPath
from typing import Any, Iterable
from xml.etree import ElementTree

from validate_core_artifact_inputs import (
    ContractError,
    load_json,
    validate_commit,
)


GROUP = "io.github.archivesteak.compose"
ARTIFACT = "compose-gradle-plugin"
PLUGIN_ID = "io.github.archivesteak.compose"
LEGACY_PLUGIN_ID = "org.jetbrains.compose"
MARKER_ARTIFACT = f"{PLUGIN_ID}.gradle.plugin"
VERSION = "1.12.0-beta02-mingw"
IMPLEMENTATION_CLASS = "org.jetbrains.compose.ComposePlugin"
IMPLEMENTATION_COORDINATE = f"{GROUP}:{ARTIFACT}:{VERSION}"
MARKER_COORDINATE = f"{GROUP}:{MARKER_ARTIFACT}:{VERSION}"
MAVEN_NAMESPACE = "http://maven.apache.org/POM/4.0.0"
POM_METADATA = {
    "name": "Compose Multiplatform MinGW Gradle Plugin",
    "description": (
        "Compose Multiplatform Gradle plugin with Kotlin/Native mingwX64 support"
    ),
    "url": "https://github.com/archivesteak/compose-multiplatform",
}
LICENSE_METADATA = {
    "name": "The Apache License, Version 2.0",
    "url": "https://www.apache.org/licenses/LICENSE-2.0.txt",
}
DEVELOPER_METADATA = {
    "id": "archivesteak",
    "name": "Jack Harrington",
    "url": "https://github.com/archivesteak",
}
SCM_METADATA = {
    "connection": (
        "scm:git:https://github.com/archivesteak/compose-multiplatform.git"
    ),
    "developerConnection": (
        "scm:git:ssh://git@github.com/archivesteak/compose-multiplatform.git"
    ),
    "url": "https://github.com/archivesteak/compose-multiplatform",
}
ALLOWED_INDEPENDENT_COMPOSE_GROUPS = {"org.jetbrains.compose.hot-reload"}


def _tag(name: str) -> str:
    return f"{{{MAVEN_NAMESPACE}}}{name}"


def _text(parent: ElementTree.Element, name: str) -> str | None:
    child = parent.find(_tag(name))
    if child is None or child.text is None:
        return None
    return child.text.strip()


def _require_regular_file(path: Path) -> None:
    if path.is_symlink() or not path.is_file():
        raise ContractError(f"publication entry must be a regular file: {path}")
    if path.stat().st_size == 0:
        raise ContractError(f"publication entry must not be empty: {path}")


def _exact_version_files(directory: Path, names: set[str], description: str) -> None:
    if directory.is_symlink() or not directory.is_dir():
        raise ContractError(f"missing {description} publication directory: {directory}")
    entries = {entry.name for entry in directory.iterdir()}
    if entries != names:
        missing = sorted(names - entries)
        unexpected = sorted(entries - names)
        details = []
        if missing:
            details.append("missing " + ", ".join(missing))
        if unexpected:
            details.append("unexpected " + ", ".join(unexpected))
        raise ContractError(f"{description} files differ: " + "; ".join(details))
    for name in names:
        _require_regular_file(directory / name)


def _parse_pom(path: Path) -> ElementTree.Element:
    _require_regular_file(path)
    data = path.read_bytes()
    upper = data.upper()
    if b"<!DOCTYPE" in upper or b"<!ENTITY" in upper:
        raise ContractError(f"POM must not contain a DTD or entity declaration: {path}")
    try:
        root = ElementTree.fromstring(data)
    except ElementTree.ParseError as error:
        raise ContractError(f"cannot parse POM {path}: {error}") from error
    if root.tag != _tag("project"):
        raise ContractError(f"POM has an unexpected root element: {path}")
    return root


def _child_maps(
    root: ElementTree.Element,
    container: str,
    child: str,
    fields: Iterable[str],
) -> list[dict[str, str | None]]:
    parent = root.find(_tag(container))
    if parent is None:
        return []
    return [
        {field: _text(element, field) for field in fields}
        for element in parent.findall(_tag(child))
    ]


def _verify_pom_metadata(root: ElementTree.Element, description: str) -> None:
    actual = {field: _text(root, field) for field in POM_METADATA}
    if actual != POM_METADATA:
        raise ContractError(
            f"{description} project metadata differs: "
            f"expected {POM_METADATA}, found {actual}"
        )
    licenses = _child_maps(root, "licenses", "license", LICENSE_METADATA)
    if licenses != [LICENSE_METADATA]:
        raise ContractError(f"{description} license metadata differs: {licenses}")
    developers = _child_maps(root, "developers", "developer", DEVELOPER_METADATA)
    if developers != [DEVELOPER_METADATA]:
        raise ContractError(f"{description} developer metadata differs: {developers}")
    scm = root.find(_tag("scm"))
    actual_scm = (
        {field: _text(scm, field) for field in SCM_METADATA}
        if scm is not None
        else {}
    )
    if actual_scm != SCM_METADATA:
        raise ContractError(f"{description} SCM metadata differs: {actual_scm}")


def _pom_dependencies(root: ElementTree.Element) -> list[dict[str, str | None]]:
    return _child_maps(
        root,
        "dependencies",
        "dependency",
        ("groupId", "artifactId", "version", "scope"),
    )


def _is_upstream_compose_group(group: object) -> bool:
    if not isinstance(group, str):
        return False
    return (
        group == "org.jetbrains.skiko"
        or group == "org.jetbrains.androidx.navigationevent"
        or (
            (group == "org.jetbrains.compose" or group.startswith("org.jetbrains.compose."))
            and group not in ALLOWED_INDEPENDENT_COMPOSE_GROUPS
        )
    )


def _verify_pom(
    path: Path,
    *,
    artifact: str,
    marker: bool,
) -> None:
    description = "plugin marker POM" if marker else "plugin implementation POM"
    root = _parse_pom(path)
    for element_name in (
        "modelVersion",
        "groupId",
        "artifactId",
        "version",
        "name",
        "description",
        "url",
        "licenses",
        "developers",
        "scm",
        "dependencies",
    ):
        if len(root.findall(_tag(element_name))) != 1:
            raise ContractError(
                f"{description} must contain exactly one {element_name} element"
            )
    if _text(root, "modelVersion") != "4.0.0":
        raise ContractError(f"{description} must use Maven modelVersion 4.0.0")
    actual_coordinate = (_text(root, "groupId"), _text(root, "artifactId"), _text(root, "version"))
    expected_coordinate = (GROUP, artifact, VERSION)
    if actual_coordinate != expected_coordinate:
        raise ContractError(
            f"{description} coordinate differs: expected {expected_coordinate}, "
            f"found {actual_coordinate}"
        )
    packaging = _text(root, "packaging")
    packaging_count = len(root.findall(_tag("packaging")))
    if marker and (packaging_count != 1 or packaging != "pom"):
        raise ContractError("plugin marker POM must declare packaging=pom exactly once")
    if not marker and (packaging_count > 1 or packaging not in (None, "jar")):
        raise ContractError(f"plugin implementation POM has packaging={packaging!r}")
    _verify_pom_metadata(root, description)
    dependencies = _pom_dependencies(root)
    if marker:
        expected = [
            {
                "groupId": GROUP,
                "artifactId": ARTIFACT,
                "version": VERSION,
                "scope": None,
            }
        ]
        if dependencies != expected:
            raise ContractError(
                "plugin marker must depend exactly on the fork implementation: "
                f"expected {expected}, found {dependencies}"
            )
    else:
        forbidden = sorted(
            f"{dependency['groupId']}:{dependency['artifactId']}:{dependency['version']}"
            for dependency in dependencies
            if _is_upstream_compose_group(dependency["groupId"])
        )
        if forbidden:
            raise ContractError(
                "plugin implementation POM contains upstream Compose lineage: "
                + ", ".join(forbidden)
            )


def _verify_zip(path: Path, descriptors: dict[str, str] | None = None) -> None:
    _require_regular_file(path)
    try:
        with zipfile.ZipFile(path) as archive:
            corrupt_entry = archive.testzip()
            if corrupt_entry is not None:
                raise ContractError(
                    f"archive contains a corrupt entry {path}: {corrupt_entry}"
                )
            names = [entry.filename for entry in archive.infolist()]
            duplicates = sorted(
                name for name, count in Counter(names).items() if count > 1
            )
            if duplicates:
                raise ContractError(
                    f"archive contains duplicate entries {path}: {', '.join(duplicates)}"
                )
            for name in names:
                pure = PurePosixPath(name)
                if (
                    not name
                    or "\\" in name
                    or name.startswith("/")
                    or pure.is_absolute()
                    or (pure.parts and ":" in pure.parts[0])
                    or any(part in ("", ".", "..") for part in pure.parts)
                ):
                    raise ContractError(f"archive contains an unsafe entry {path}: {name!r}")
            for descriptor, expected in (descriptors or {}).items():
                if descriptor not in names:
                    raise ContractError(f"plugin JAR lacks descriptor {descriptor}")
                try:
                    actual = archive.read(descriptor).decode("utf-8")
                except UnicodeError as error:
                    raise ContractError(
                        f"plugin descriptor is not UTF-8: {descriptor}"
                    ) from error
                if actual not in (expected, expected + "\n", expected + "\r\n"):
                    raise ContractError(
                        f"plugin descriptor {descriptor} does not point exactly to "
                        f"{IMPLEMENTATION_CLASS}"
                    )
    except zipfile.BadZipFile as error:
        raise ContractError(f"publication archive is not a valid ZIP: {path}") from error


def _verify_module(path: Path, artifacts: dict[str, Path]) -> None:
    module = load_json(path, "plugin Gradle module metadata")
    if module.get("formatVersion") != "1.1":
        raise ContractError("plugin module metadata must use formatVersion 1.1")
    component = module.get("component")
    expected_component = {"group": GROUP, "module": ARTIFACT, "version": VERSION}
    actual_component = (
        {field: component.get(field) for field in expected_component}
        if isinstance(component, dict)
        else {}
    )
    if actual_component != expected_component:
        raise ContractError(
            "plugin module component differs: "
            f"expected {expected_component}, found {actual_component}"
        )
    variants = module.get("variants")
    if not isinstance(variants, list) or not variants:
        raise ContractError("plugin module metadata has no variants")
    artifact_metadata = {
        name: {
            "size": artifact.stat().st_size,
            "sha256": hashlib.sha256(artifact.read_bytes()).hexdigest(),
            "sha512": hashlib.sha512(artifact.read_bytes()).hexdigest(),
        }
        for name, artifact in artifacts.items()
    }
    declared_files: set[str] = set()
    forbidden: set[str] = set()
    variant_names: set[str] = set()
    for variant in variants:
        if not isinstance(variant, dict):
            raise ContractError("plugin module metadata contains a non-object variant")
        variant_name = variant.get("name")
        if not isinstance(variant_name, str) or not variant_name:
            raise ContractError("plugin module metadata contains an unnamed variant")
        if variant_name in variant_names:
            raise ContractError(
                f"plugin module metadata repeats variant {variant_name!r}"
            )
        variant_names.add(variant_name)
        for dependency_key in ("dependencies", "dependencyConstraints"):
            dependencies = variant.get(dependency_key, [])
            if not isinstance(dependencies, list):
                raise ContractError(
                    f"plugin module variant has invalid {dependency_key}"
                )
            for dependency in dependencies:
                if not isinstance(dependency, dict):
                    raise ContractError(
                        f"plugin module variant has a non-object {dependency_key} entry"
                    )
                if _is_upstream_compose_group(dependency.get("group")):
                    forbidden.add(
                        f"{dependency.get('group')}:{dependency.get('module')}"
                    )
        files = variant.get("files", [])
        if not isinstance(files, list):
            raise ContractError("plugin module variant has invalid files")
        for file_entry in files:
            if not isinstance(file_entry, dict):
                raise ContractError("plugin module variant has a non-object file")
            name = file_entry.get("name")
            if (
                not isinstance(name, str)
                or name not in artifacts
                or file_entry.get("url") != name
            ):
                raise ContractError(
                    "plugin module metadata declares an unexpected file: "
                    f"{file_entry}"
                )
            expected = artifact_metadata[name]
            if file_entry.get("size") != expected["size"]:
                raise ContractError(f"plugin module metadata contains a stale size for {name}")
            if file_entry.get("sha256") != expected["sha256"]:
                raise ContractError(
                    f"plugin module metadata contains a stale SHA-256 for {name}"
                )
            if file_entry.get("sha512") != expected["sha512"]:
                raise ContractError(
                    f"plugin module metadata contains a stale SHA-512 for {name}"
                )
            declared_files.add(name)
    if forbidden:
        raise ContractError(
            "plugin module metadata contains upstream Compose lineage: "
            + ", ".join(sorted(forbidden))
        )
    expected_variants = {
        "apiElements",
        "runtimeElements",
        "javadocElements",
        "sourcesElements",
    }
    if variant_names != expected_variants:
        raise ContractError(
            "plugin module variants differ: "
            f"expected {sorted(expected_variants)}, found {sorted(variant_names)}"
        )
    if declared_files != set(artifacts):
        raise ContractError(
            "plugin module metadata does not declare every publication JAR: "
            f"expected {sorted(artifacts)}, found {sorted(declared_files)}"
        )


def verify_publication(
    repository: Path,
    source_ref: str,
    report: Path,
) -> dict[str, Any]:
    source_ref = validate_commit(source_ref, "source_ref")
    if repository.is_symlink() or not repository.is_dir():
        raise ContractError(f"Maven repository must be a regular directory: {repository}")
    repository = repository.resolve()
    base = f"{ARTIFACT}-{VERSION}"
    implementation_dir = repository.joinpath(*GROUP.split("."), ARTIFACT, VERSION)
    implementation_names = {
        f"{base}.jar",
        f"{base}-sources.jar",
        f"{base}-javadoc.jar",
        f"{base}.module",
        f"{base}.pom",
    }
    marker_base = f"{MARKER_ARTIFACT}-{VERSION}"
    marker_dir = repository.joinpath(*GROUP.split("."), MARKER_ARTIFACT, VERSION)
    marker_names = {f"{marker_base}.pom"}
    _exact_version_files(
        implementation_dir,
        implementation_names,
        "plugin implementation",
    )
    _exact_version_files(marker_dir, marker_names, "plugin marker")

    jar = implementation_dir / f"{base}.jar"
    sources_jar = implementation_dir / f"{base}-sources.jar"
    javadoc_jar = implementation_dir / f"{base}-javadoc.jar"
    module = implementation_dir / f"{base}.module"
    implementation_pom = implementation_dir / f"{base}.pom"
    marker_pom = marker_dir / f"{marker_base}.pom"
    descriptor_value = f"implementation-class={IMPLEMENTATION_CLASS}"
    _verify_zip(
        jar,
        {
            f"META-INF/gradle-plugins/{PLUGIN_ID}.properties": descriptor_value,
            f"META-INF/gradle-plugins/{LEGACY_PLUGIN_ID}.properties": descriptor_value,
        },
    )
    _verify_zip(sources_jar)
    _verify_zip(javadoc_jar)
    _verify_module(
        module,
        {
            jar.name: jar,
            sources_jar.name: sources_jar,
            javadoc_jar.name: javadoc_jar,
        },
    )
    _verify_pom(implementation_pom, artifact=ARTIFACT, marker=False)
    _verify_pom(marker_pom, artifact=MARKER_ARTIFACT, marker=True)

    files = sorted(
        [implementation_dir / name for name in implementation_names]
        + [marker_dir / name for name in marker_names],
        key=lambda path: path.relative_to(repository).as_posix(),
    )
    result: dict[str, Any] = {
        "schemaVersion": 1,
        "sourceRef": source_ref,
        "pluginId": PLUGIN_ID,
        "legacyPluginId": LEGACY_PLUGIN_ID,
        "implementationClass": IMPLEMENTATION_CLASS,
        "implementationCoordinate": IMPLEMENTATION_COORDINATE,
        "markerCoordinate": MARKER_COORDINATE,
        "files": {
            path.relative_to(repository).as_posix(): {
                "size": path.stat().st_size,
                "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
            }
            for path in files
        },
    }
    if report.exists() or report.is_symlink():
        raise ContractError(f"plugin provenance report must be fresh and absent: {report}")
    report.parent.mkdir(parents=True, exist_ok=True)
    report.write_text(
        json.dumps(result, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
        newline="\n",
    )
    return result


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", required=True, type=Path)
    parser.add_argument("--source-ref", required=True)
    parser.add_argument("--report", required=True, type=Path)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        result = verify_publication(args.repository, args.source_ref, args.report)
    except (ContractError, OSError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1
    print(f"verified plugin implementation: {result['implementationCoordinate']}")
    print(f"verified plugin marker: {result['markerCoordinate']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

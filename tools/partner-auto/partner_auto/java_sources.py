"""Prepare pasted compilation units and resolve identifiable local FROG helpers."""
from __future__ import annotations

from pathlib import Path
import re


PROJECT_ROOT = Path(__file__).resolve().parents[3]
PACKAGE = re.compile(r"\bpackage\s+[\w$]+(?:\s*\.\s*[\w$]+)*\s*;")
TYPE = re.compile(r"\b(?:class|interface|enum|record)\s+([\w$]+)")


def code_mask(source: str) -> str:
    """Blank comments/literals while preserving character and line offsets."""
    out = list(source)
    index, size = 0, len(source)
    while index < size:
        start = index
        if source.startswith("//", index):
            stop = source.find("\n", index + 2)
            index = size if stop < 0 else stop
        elif source.startswith("/*", index):
            stop = source.find("*/", index + 2)
            index = size if stop < 0 else stop + 2
        elif source.startswith('"""', index):
            index += 3
            while index < size:
                if source[index] == "\\":
                    index += 2
                elif source.startswith('"""', index):
                    index += 3
                    break
                else:
                    index += 1
        elif source[index] in "\"'":
            quote = source[index]
            index += 1
            while index < size:
                if source[index] == "\\":
                    index += 2
                elif source[index] == quote:
                    index += 1
                    break
                else:
                    index += 1
        else:
            index += 1
            continue
        for position in range(start, min(index, size)):
            if out[position] not in "\r\n":
                out[position] = " "
    return "".join(out)


def split_units(sources: dict[str, str]) -> tuple[dict, dict, list]:
    prepared, origins, diagnostics = {}, {}, []
    for name, text in sources.items():
        declarations = list(PACKAGE.finditer(code_mask(text)))
        if len(declarations) < 2:
            prepared[name] = text
            continue
        starts = [0] + [match.start() for match in declarations[1:]]
        stops = starts[1:] + [len(text)]
        for number, (start, stop) in enumerate(zip(starts, stops), 1):
            part = f"{name}.unit-{number}.java"
            if part in sources or part in prepared:
                raise ValueError(f"Pasted Java unit name conflicts with another input: {part}")
            prepared[part] = text[start:stop]
            origins[part] = {"file": name, "line_offset": text.count("\n", 0, start)}
        diagnostics.append({"severity": "info", "code": "COMBINED_JAVA_SPLIT",
                            "message": f"Read {len(starts)} complete Java files from {name}.",
                            "source": {"file": name, "line": 1}})
    return prepared, origins, diagnostics


def _normalized(source: str) -> str:
    return source.replace("\r\n", "\n").strip()


def _types(source: str) -> set[str]:
    masked = code_mask(source)
    package = PACKAGE.search(masked)
    prefix = re.sub(r"\s+", "", package.group()[7:-1]) + "." if package else ""
    found = set()
    depth, cursor = 0, 0
    for match in TYPE.finditer(masked):
        for character in masked[cursor:match.start()]:
            depth += (character == "{") - (character == "}")
        cursor = match.start()
        if depth == 0:
            found.add(prefix + match.group(1))
    return found


def local_frog_helpers(sources: dict[str, str]) -> tuple[dict, list]:
    """Only use local helpers when supplied code exactly identifies this checkout.

    An unfamiliar partner's package names are never sufficient evidence to fill in
    their missing source with our code. A conflicting supplied local class also
    disables this fallback; the normal missing-dependency diagnostics then apply.
    """
    folder = PROJECT_ROOT / "TeamCode/src/main/java"
    if not folder.is_dir():
        return sources, []
    local = [(file, file.read_text(encoding="utf-8-sig")) for file in sorted(folder.rglob("*.java"))]
    fingerprints = {_normalized(text) for _, text in local}
    if not any(_normalized(text) in fingerprints for text in sources.values()):
        return sources, []
    local_types: dict[str, str] = {}
    for _, text in local:
        for name in _types(text):
            local_types[name] = _normalized(text)
    for text in sources.values():
        if any(name in local_types and local_types[name] != _normalized(text) for name in _types(text)):
            return sources, []
    result = dict(sources)
    supplied = {_normalized(text) for text in sources.values()}
    added = []
    for file, text in local:
        relative = file.relative_to(folder).as_posix()
        # Other concrete competition routes are not dependencies of the pasted auto.
        if relative.startswith("org/firstinspires/ftc/teamcode/opmodes/auto/comp/") or _normalized(text) in supplied:
            continue
        name = "FROG local helpers/" + relative
        if name not in result:
            result[name] = text
            added.append(relative)
    diagnostics = []
    if added:
        diagnostics.append({"severity": "info", "code": "LOCAL_FROG_HELPERS",
                            "message": "Recognized exact source from this FROG checkout and loaded its local "
                                       "helper classes, including pose mirroring. No partner code was substituted.",
                            "files": added})
    return result, diagnostics


def restore_locations(value, origins: dict) -> None:
    if isinstance(value, list):
        for child in value:
            restore_locations(child, origins)
    elif isinstance(value, dict):
        source = value.get("source")
        if isinstance(source, dict) and source.get("file") in origins:
            origin = origins[source["file"]]
            source["unit"] = source["file"]
            source["file"] = origin["file"]
            if isinstance(source.get("line"), int):
                source["line"] += origin["line_offset"]
        for key, child in value.items():
            if key != "source":
                restore_locations(child, origins)

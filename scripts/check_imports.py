#!/usr/bin/env python3
"""Detect cyclic imports within pingui package."""

from __future__ import annotations

import ast
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PKG = ROOT / "src" / "pingui"


def module_name(path: Path) -> str:
    rel = path.relative_to(ROOT / "src").with_suffix("")
    return ".".join(rel.parts)


def local_imports(path: Path) -> set[str]:
    tree = ast.parse(path.read_text(encoding="utf-8"))
    found: set[str] = set()
    mod = module_name(path)
    for node in ast.walk(tree):
        if isinstance(node, ast.ImportFrom) and node.module:
            if node.module == "pingui" or node.module.startswith("pingui."):
                found.add(node.module)
        elif isinstance(node, ast.Import):
            for alias in node.names:
                if alias.name == "pingui" or alias.name.startswith("pingui."):
                    found.add(alias.name)
    _ = mod
    return found


def main() -> int:
    graph: dict[str, set[str]] = {}
    for py in PKG.rglob("*.py"):
        graph[module_name(py)] = local_imports(py)

    visiting: set[str] = set()
    visited: set[str] = set()
    stack: list[str] = []

    def dfs(node: str) -> None:
        if node in visiting:
            cycle_start = stack.index(node)
            cycle = stack[cycle_start:] + [node]
            raise SystemExit(f"Cyclic import detected: {' -> '.join(cycle)}")
        if node in visited:
            return
        visiting.add(node)
        stack.append(node)
        for dep in graph.get(node, ()):
            if dep in graph:
                dfs(dep)
        stack.pop()
        visiting.remove(node)
        visited.add(node)

    for n in graph:
        dfs(n)

    print("OK: no cyclic imports in pingui")
    return 0


if __name__ == "__main__":
    sys.exit(main())

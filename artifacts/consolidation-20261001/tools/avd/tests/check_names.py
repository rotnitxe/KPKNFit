"""Poor-man's pyflakes (no linters are installed in the QA runtime).

For every module of the toolset it reports
  * names read inside functions/classes that are not defined at module level, not imported,
    not builtins and not local/closure bindings  (typos, forgotten imports);
  * imported names that are never used  (informational only).

    python -X utf8 tests/check_names.py          # exit 1 when an undefined name is found
"""

from __future__ import annotations

import ast
import builtins
import symtable
import sys
from pathlib import Path

TOOLS = Path(__file__).resolve().parents[1]


def module_bindings(tree: ast.Module) -> set[str]:
    names: set[str] = set()
    for node in ast.walk(tree):
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef)):
            names.add(node.name)
        elif isinstance(node, ast.Import):
            for alias in node.names:
                names.add((alias.asname or alias.name).split(".")[0])
        elif isinstance(node, ast.ImportFrom):
            for alias in node.names:
                names.add(alias.asname or alias.name)
    return names


def undefined_names(path: Path) -> list[tuple[str, str]]:
    source = path.read_text(encoding="utf-8")
    table = symtable.symtable(source, str(path), "exec")
    tree = ast.parse(source)
    module_level = {s.get_name() for s in table.get_symbols() if s.is_assigned() or s.is_imported() or s.is_namespace()}
    module_level |= module_bindings(tree)
    problems: list[tuple[str, str]] = []

    def walk(scope: symtable.SymbolTable, trail: str) -> None:
        for symbol in scope.get_symbols():
            if scope.get_type() == "module":
                continue
            if symbol.is_global() and symbol.is_referenced():
                name = symbol.get_name()
                if name not in module_level and not hasattr(builtins, name) and name not in {"__class__", "__file__", "__name__", "__doc__"}:
                    problems.append((trail + scope.get_name(), name))
        for child in scope.get_children():
            walk(child, trail + scope.get_name() + ".")

    walk(table, "")
    return problems


def unused_imports(path: Path) -> list[str]:
    tree = ast.parse(path.read_text(encoding="utf-8"))
    imported: dict[str, int] = {}
    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            for alias in node.names:
                imported[(alias.asname or alias.name).split(".")[0]] = node.lineno
        elif isinstance(node, ast.ImportFrom) and node.module != "__future__":
            for alias in node.names:
                imported[alias.asname or alias.name] = node.lineno
    used = {n.id for n in ast.walk(tree) if isinstance(n, ast.Name)} | {
        n.value.id for n in ast.walk(tree) if isinstance(n, ast.Attribute) and isinstance(n.value, ast.Name)}
    return sorted(f"{name} (line {line})" for name, line in imported.items() if name not in used)


def main() -> int:
    failed = False
    for path in sorted(TOOLS.glob("*.py")):
        bad = undefined_names(path)
        for scope, name in bad:
            print(f"UNDEFINED  {path.name}:{scope}: {name}")
            failed = True
        if "--unused" in sys.argv:
            for item in unused_imports(path):
                print(f"unused     {path.name}: {item}")
    print("check_names: " + ("FAILED" if failed else "ok"))
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())

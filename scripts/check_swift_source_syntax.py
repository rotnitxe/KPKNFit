#!/usr/bin/env python3
"""Cloud syntax check only. Requires tree-sitter/tree-sitter-swift; does not replace Xcode type checking."""
from pathlib import Path
import subprocess
import sys
from tree_sitter import Language, Parser
import tree_sitter_swift

root=Path(__file__).resolve().parents[1]
parser=Parser(Language(tree_sitter_swift.language()))
paths=subprocess.check_output(['git','diff','--name-only','edc237f66d8474c3a9922296b61940e7030b7f8a','--','ios-native'],cwd=root,text=True).splitlines()
count=0
failures=[]
for name in paths:
    path=root/name
    if path.suffix!='.swift' or not path.is_file(): continue
    count+=1
    tree=parser.parse(path.read_bytes())
    def visit(node):
        if node.type=='ERROR' or node.is_missing:
            failures.append(f'{name}:{node.start_point.row+1}: {node.type}: {node.text[:100]!r}')
        for child in node.children:visit(child)
    if tree.root_node.has_error:visit(tree.root_node)
print('\n'.join(failures))
print(f'{count} changed Swift sources parsed; {len(failures)} syntax errors; Xcode build remains a separate gate')
sys.exit(bool(failures))

"""Reconstruct a workspace-local Maven repository from immutable Gradle artifact files.

Useful when a full system disk damaged Gradle metadata. The normal build still
uses official remote repositories when this optional recovery cache is absent.
"""
from pathlib import Path
import os
import shutil
import json

root = Path(__file__).resolve().parent.parent
source = root / '.gradle-home/caches/modules-2/files-2.1'
target = root / '.gradle-home/maven-cache'
count = 0
for artifact in source.glob('*/*/*/*/*'):
    if not artifact.is_file():
        continue
    group, name, version, _, filename = artifact.relative_to(source).parts
    destination = target / group.replace('.', '/') / name / version / filename
    destination.parent.mkdir(parents=True, exist_ok=True)
    if not destination.exists():
        try:
            os.link(artifact, destination)
        except OSError:
            shutil.copyfile(artifact, destination)
    count += 1
for module in target.rglob('*.module'):
    for variant in json.loads(module.read_text(encoding='utf-8')).get('variants', []):
        for file in variant.get('files', []):
            original = module.parent / file['name']
            alias = module.parent / file['url']
            if original.is_file() and not alias.exists() and alias.parent == module.parent:
                os.link(original, alias)
print(f'{count} cached Maven files reconstructed on the workspace drive')

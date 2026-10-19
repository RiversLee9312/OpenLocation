"""Download an explicitly selected Maven coordinate into the optional recovery cache."""
import sys
from pathlib import Path
from urllib.request import urlopen
from urllib.error import HTTPError
import xml.etree.ElementTree as ET

group, artifact, version = sys.argv[1].split(':')
repository = sys.argv[2] if len(sys.argv) > 2 else 'https://repo.maven.apache.org/maven2'
relative = f'{group.replace(".", "/")}/{artifact}/{version}'
destination = Path(__file__).resolve().parent.parent / '.gradle-home/maven-cache' / relative
destination.mkdir(parents=True, exist_ok=True)
base = f'{artifact}-{version}'
def fetch(suffix, optional=False):
    name = f'{base}.{suffix}'
    try:
        data = urlopen(f'{repository}/{relative}/{name}', timeout=60).read()
        (destination/name).write_bytes(data)
        return data
    except HTTPError as error:
        if optional and error.code == 404:
            return None
        raise
pom = fetch('pom')
node = ET.fromstring(pom)
packaging = node.find('{http://maven.apache.org/POM/4.0.0}packaging')
extension = packaging.text if packaging is not None else 'jar'
if extension != 'pom':
    fetch(extension)
fetch('module', optional=True)
print(f'Fetched {sys.argv[1]} from {repository}')

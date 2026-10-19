"""Validate XML keys, positional format contracts, declared locales and Android escaping."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parent.parent
placeholder = re.compile(r'%(?:\d+\$)?[-+0 #]*\d*(?:\.\d+)?[dsf]')
errors = []
counts = {}
for module in ('app', 'probe-app'):
    res = root / module / 'src/main/res'
    source = ET.parse(res / 'values/strings.xml').getroot()
    expected = {node.attrib['name']: node for node in source if node.attrib.get('translatable') != 'false'}
    for path in sorted(res.glob('values-*/strings.xml')):
        try:
            nodes = list(ET.parse(path).getroot())
            translated = {node.attrib['name']: node for node in nodes}
            if len(translated) != len(nodes):
                errors.append(f'{path}: duplicate key')
            missing = expected.keys() - translated.keys()
            if missing:
                errors.append(f'{path}: missing {sorted(missing)}')
            for key in expected.keys() & translated.keys():
                original = expected[key]
                actual = translated[key]
                template = original.find("item[@quantity='other']") if original.tag == 'plurals' else original
                contracts = sorted(placeholder.findall(''.join(template.itertext())))
                candidates = list(actual) if actual.tag == 'plurals' else [actual]
                if actual.tag != original.tag:
                    errors.append(f'{path}:{key}: changed resource type')
                for item in candidates:
                    text = ''.join(item.itertext())
                    if sorted(placeholder.findall(text)) != contracts:
                        errors.append(f'{path}:{key}: incompatible format placeholders')
                    if re.search(r"(?<!\\)'", text):
                        errors.append(f'{path}:{key}: unescaped apostrophe')
            counts[module] = counts.get(module, 0) + 1
        except ET.ParseError as error:
            errors.append(f'{path}: {error}')
print(f'Localized resource files: {counts}')
if errors:
    print('\n'.join(errors))
    raise SystemExit(1)
print('All localized keys, XML and positional format contracts passed')

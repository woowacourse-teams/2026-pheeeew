"""Compare preserved regression results and optionally a fresh full Gradle test-results folder."""
import argparse
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parent

def read(folder, selected=None):
    roots = [ET.parse(p).getroot() for p in folder.glob('TEST-*.xml')]
    if selected is not None:
        roots = [r for r in roots if r.attrib['name'].split('.')[-1] in selected]
    counts = {k: sum(int(r.attrib.get(k, 0)) for r in roots)
              for k in ('tests', 'failures', 'errors', 'skipped')}
    names = {r.attrib['name'].split('.')[-1] for r in roots}
    cases = {(r.attrib['name'].split('.')[-1], t.attrib['name'])
             for r in roots for t in r.findall('testcase')}
    probes = sorted(line for r in roots for line in r.findtext('system-out', '').splitlines()
                    if 'PROBE,' in line)
    assert counts['failures'] == counts['errors'] == counts['skipped'] == 0, counts
    return counts, names, cases, probes

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--current-results', type=Path)
args = parser.parse_args()
before, _, baseline_cases, baseline_probes = read(root / 'raw/before')
after, names, after_cases, after_probes = read(root / 'raw/after')
assert before['tests'] == 67 and after['tests'] == 77
assert baseline_cases <= after_cases
assert len(baseline_probes) == 14 and baseline_probes == after_probes
if args.current_results:
    current, _, current_cases, current_probes = read(args.current_results, names)
    assert current == after, current
    assert current_cases == after_cases
    assert current_probes == baseline_probes
print('All 67 baseline cases retained; 10 new cases pass; 14 probe rows unchanged.')

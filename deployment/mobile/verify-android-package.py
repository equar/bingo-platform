"""Verify a standalone shared APK before distribution; never read signing passwords."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import zipfile

p = argparse.ArgumentParser()
p.add_argument('apk', type=Path)
p.add_argument('app', choices=['bingo-app', 'bingo-agent'])
p.add_argument('--previous', type=Path)
p.add_argument('--report', type=Path)
a = p.parse_args()
sdk = Path(os.environ.get('ANDROID_HOME', os.environ.get('ANDROID_SDK_ROOT', str(Path.home() / 'Library/Android/sdk'))))
tools = sorted((sdk / 'build-tools').glob('*'), key=lambda x: tuple(int(v) for v in re.findall(r'\d+', x.name)))
if not tools:
    raise SystemExit('Android SDK build-tools required; set ANDROID_HOME')
tools = tools[-1]

def run(name, *args):
    return subprocess.run([str(tools / name), *map(str, args)], check=True, capture_output=True, text=True).stdout

def inspect(apk):
    badging = run('aapt', 'dump', 'badging', apk)
    package, code, version = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging).groups()
    minimum = int(re.search(r"sdkVersion:'(\d+)'", badging)[1])
    target = int(re.search(r"targetSdkVersion:'(\d+)'", badging)[1])
    signature = run('apksigner', 'verify', '--verbose', '--print-certs', apk)
    if 'Verified using v2 scheme (APK Signature Scheme v2): true' not in signature:
        raise SystemExit('APK must have a valid v2 signature for Android 7+')
    signer = re.search(r'Signer #1 certificate SHA-256 digest: ([a-f0-9]+)', signature)[1]
    return dict(package=package, version_code=int(code), version=version, min_sdk=minimum, target_sdk=target, signer_sha256=signer)

report = inspect(a.apk)
expected = {'bingo-app': 'com.equar.bingo.bingo_app', 'bingo-agent': 'com.equar.bingo.bingo_agent'}[a.app]
if report['package'] != expected or report['min_sdk'] != 24:
    raise SystemExit('Unexpected package ID or Android minimum; review compatibility policy')
# Pin the identities already used for shared releases. Key changes require a deliberate migration.
certificates = json.loads((Path(__file__).parent / 'android-signing-certificates.json').read_text())
if report['signer_sha256'] != certificates[a.app]:
    raise SystemExit('Signing identity changed; this package cannot update existing shared installs')
if a.previous and a.previous.exists():
    old = inspect(a.previous)
    if report['signer_sha256'] != old['signer_sha256'] or report['version_code'] <= old['version_code']:
        raise SystemExit('Updates require the same signing certificate and a strictly higher build number; set BINGO_BUILD_NUMBER')
run('zipalign', '-c', '-P', '16', '4', a.apk)
with zipfile.ZipFile(a.apk) as archive:
    names = archive.namelist()
    abis = sorted({n.split('/')[1] for n in names if n.startswith('lib/') and n.endswith('.so')})
    if abis != ['arm64-v8a', 'armeabi-v7a', 'x86_64']:
        raise SystemExit('Shared APK must include ARM32, ARM64 and x86_64')
    for name in names:
        if not name.startswith('lib/') or not name.endswith('.so'):
            continue
        data = archive.read(name)
        if data[:4] != b'\x7fELF' or data[5] != 1:
            raise SystemExit('Unsupported native binary: ' + name)
        wide = data[4] == 2
        offset = struct.unpack_from('<Q' if wide else '<I', data, 32 if wide else 28)[0]
        size, count = struct.unpack_from('<HH', data, 54 if wide else 42)
        for i in range(count):
            values = struct.unpack_from('<IIQQQQQQ' if wide else '<IIIIIIII', data, offset + i * size)
            if values[0] == 1 and values[-1] < 16384:
                raise SystemExit('Native library lacks 16 KB alignment: ' + name)
report.update(file=a.app + "-android.apk", abis=abis, bytes=a.apk.stat().st_size,
              sha256=hashlib.sha256(a.apk.read_bytes()).hexdigest(),
              zip_alignment_16kb=True, elf_alignment_16kb=True,
              device_install_tested=False)
if a.report:
    a.report.write_text(json.dumps(report, indent=2) + '\n')
    verified = []
    for path in sorted(a.report.parent.glob('*.compatibility.json')):
        entry = json.loads(path.read_text())
        apk = a.report.parent / entry['file']
        # The current APK is copied after validation; use its verified source here.
        if entry['file'] == report['file']:
            verified.append(entry)
        elif apk.exists() and hashlib.sha256(apk.read_bytes()).hexdigest() == entry['sha256']:
            verified.append(entry)
    (a.report.parent / 'release-manifest.json').write_text(json.dumps({
        'api_base_url': 'https://api.bingo.bakaroo.com', 'artifacts': verified,
        'ios_status': 'Apple distribution signing and provisioning required; no installable IPA produced.'
    }, indent=2) + '\n')
print(json.dumps(report, indent=2))

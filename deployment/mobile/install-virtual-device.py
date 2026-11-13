#!/usr/bin/env python3
"""Build/install Bingo on a local Android emulator or iOS simulator; stdlib only."""
import argparse
import json
import os
from pathlib import Path
import plistlib
import re
import shutil
import subprocess
import sys
import tempfile
import time
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[2]


def run(args, *, capture=False, check=True, cwd=None):
    return subprocess.run([str(a) for a in args], cwd=cwd, check=check,
                          text=True, capture_output=capture)


def fail(message):
    raise RuntimeError(message)


def properties(app):
    path = ROOT / app / 'android/local.properties'
    if not path.exists():
        return {}
    return dict(line.split('=', 1) for line in path.read_text().splitlines()
                if '=' in line and not line.lstrip().startswith('#'))


def flutter(app):
    configured = properties(app).get('flutter.sdk', '')
    candidates = [os.environ.get('FLUTTER_BIN', ''),
                  str(Path(os.environ['FLUTTER_ROOT']) / 'bin/flutter') if os.environ.get('FLUTTER_ROOT') else '',
                  str(Path(configured) / 'bin/flutter') if configured else '',
                  shutil.which('flutter') or '']
    for candidate in candidates:
        if candidate and Path(candidate).is_file():
            return candidate
    fail('Flutter not found. Set FLUTTER_BIN or FLUTTER_ROOT, or configure android/local.properties.')


def android_tools(app):
    configured = properties(app).get('sdk.dir', '').replace('\\:', ':').replace('\\ ', ' ')
    candidates = [os.environ.get('ANDROID_HOME'), os.environ.get('ANDROID_SDK_ROOT'), configured,
                  str(Path.home() / 'Library/Android/sdk'), str(Path.home() / 'Android/Sdk')]
    for candidate in candidates:
        if candidate and (Path(candidate) / 'platform-tools/adb').is_file():
            sdk = Path(candidate)
            emulator = sdk / 'emulator/emulator'
            if not emulator.is_file():
                fail('Install Android Emulator through Android Studio SDK Manager.')
            return sdk / 'platform-tools/adb', emulator, sdk
    fail('Android SDK not found. Set ANDROID_HOME or configure android/local.properties.')


def emulators(adb):
    output = run([adb, 'devices'], capture=True).stdout
    return [line.split()[0] for line in output.splitlines()
            if re.match(r'^emulator-\d+\s+device$', line.strip())]


def avd_name(adb, serial):
    return run([adb, '-s', serial, 'emu', 'avd', 'name'], capture=True).stdout.splitlines()[0].strip()


def android_device(args):
    adb, emulator, sdk = android_tools(args.app if args.app != 'all' else 'bingo-player')
    avds = run([emulator, '-list-avds'], capture=True).stdout.splitlines()
    booted = emulators(adb)
    if args.list:
        print('Available Android AVDs:')
        for name in avds:
            print('  ' + name)
        print('Running Android emulators:')
        for serial in booted:
            print(f'  {serial}: {avd_name(adb, serial)}')
        if not avds:
            print('  Create a virtual device in Android Studio → Device Manager (Android 7/API 24+).')
        return None
    if args.device and args.device.startswith('emulator-'):
        if args.device not in booted:
            fail('The requested emulator serial is not running. Use its AVD name to boot it.')
        serial = args.device
    else:
        named = {avd_name(adb, serial): serial for serial in booted}
        if args.device:
            name = args.device
            if name not in avds:
                fail('Unknown AVD. Run with --list and copy its exact name.')
        elif len(booted) == 1:
            name = avd_name(adb, booted[0])
        elif not booted and len(avds) == 1:
            name = avds[0]
        else:
            fail('Select an Android AVD with --device NAME. Run --list to see the choices.')
        serial = named.get(name)
        if not serial:
            # Explicit port identifies the new emulator without touching other devices.
            import socket
            port = None
            for candidate in range(5554, 5682, 2):
                sockets = []
                try:
                    for n in [candidate, candidate + 1]:
                        sock = socket.socket()
                        sockets.append(sock)
                        sock.bind(('127.0.0.1', n))
                    port = candidate
                    break
                except OSError:
                    pass
                finally:
                    for sock in sockets:
                        sock.close()
            if port is None:
                fail('No available Android emulator port.')
            serial = f'emulator-{port}'
            log = Path(tempfile.gettempdir()) / f'bingo-{serial}.log'
            with log.open('a') as output:
                subprocess.Popen([str(emulator), '-avd', name, '-port', str(port), '-no-snapshot-load'],
                                 stdout=output, stderr=subprocess.STDOUT, start_new_session=True)
            print(f'Booting {name}; emulator log: {log}', flush=True)
    print(f'Waiting for {serial} to finish booting…', flush=True)
    deadline = time.monotonic() + args.timeout
    while time.monotonic() < deadline:
        result = run([adb, '-s', serial, 'shell', 'getprop', 'sys.boot_completed'], capture=True, check=False)
        if result.returncode == 0 and result.stdout.strip() == '1':
            break
        time.sleep(2)
    else:
        fail(f'Emulator did not boot within {args.timeout}s. Check Android Studio and the emulator log.')
    api = int(run([adb, '-s', serial, 'shell', 'getprop', 'ro.build.version.sdk'], capture=True).stdout.strip())
    abi = run([adb, '-s', serial, 'shell', 'getprop', 'ro.product.cpu.abi'], capture=True).stdout.strip()
    targets = {'arm64-v8a': 'android-arm64', 'armeabi-v7a': 'android-arm', 'x86_64': 'android-x64'}
    if api < 24 or abi not in targets:
        fail(f'Unsupported emulator: API {api}, ABI {abi}. Use API 24+ with ARM64, ARM32 or x86_64.')
    return adb, serial, sdk, targets[abi]


def ios_device(args):
    if sys.platform != 'darwin':
        fail('The iOS simulator requires macOS and a full Xcode installation.')
    probe = run(['xcrun', '--find', 'simctl'], capture=True, check=False)
    if probe.returncode:
        fail('Xcode Simulator tools unavailable. Select Xcode in Settings → Locations, complete first-launch setup, and review the licence with sudo xcodebuild -license.\n' + probe.stderr.strip())
    result = run(['xcrun', 'simctl', 'list', 'devices', 'available', '--json'], capture=True, check=False)
    if result.returncode:
        fail('Cannot access iOS simulators. Complete Xcode first-launch setup and licence review.\n' + result.stderr.strip())
    devices = [dict(device, runtime=runtime) for runtime, entries in json.loads(result.stdout)['devices'].items()
               if '.iOS-' in runtime for device in entries if device.get('isAvailable')]
    if args.list:
        for device in devices:
            print(f"{device['udid']}  {device['name']}  {device['state']}  {device['runtime']}")
        if not devices:
            print('Install an iOS Simulator runtime from Xcode Settings → Components/Platforms.')
        return None
    if args.device:
        choices = [d for d in devices if args.device in (d['udid'], d['name'])]
    else:
        choices = [d for d in devices if d['state'] == 'Booted']
        if not choices and len(devices) == 1:
            choices = devices
    if len(choices) != 1:
        fail('Select one iOS simulator with --device UDID (or a unique name). Run --list for choices.')
    device = choices[0]
    if device['state'] != 'Booted':
        run(['xcrun', 'simctl', 'boot', device['udid']])
    developer = Path(probe.stdout.strip()).parents[2]
    candidates = [developer / 'Applications/Simulator.app',
                  developer.parent / 'Applications/Simulator.app',
                  developer.parent / 'Applications/DeviceHub.app']
    gui = next((path for path in candidates if path.is_dir()), None)
    if gui:
        command = ['open', '-a', gui]
        if gui.name == 'Simulator.app':
            command.extend(['--args', '-CurrentDeviceUDID', device['udid']])
        run(command)
    else:
        print('Simulator is booting without a window; no Simulator/DeviceHub app was found.', flush=True)
    try:
        subprocess.run(['xcrun', 'simctl', 'bootstatus', device['udid'], '-b'],
                       check=True, timeout=args.timeout)
    except subprocess.TimeoutExpired:
        fail(f'iOS simulator did not finish booting within {args.timeout}s.')
    return device['udid']


def install(args):
    selected = android_device(args) if args.platform == 'android' else ios_device(args)
    if args.list:
        return
    apps = ['bingo-player', 'bingo-agent'] if args.app == 'all' else [args.app]
    for app in apps:
        directory = ROOT / app
        tool = flutter(app)
        run([tool, 'pub', 'get'], cwd=directory)
        run([tool, 'gen-l10n'], cwd=directory)
        command = [tool, 'build', 'apk' if args.platform == 'android' else 'ios',
                   '--debug', '--target=lib/main.dart', f'--dart-define=API_BASE_URL={args.api_url}']
        if args.platform == 'android':
            adb, serial, sdk, target = selected
            command.append(f'--target-platform={target}')
        else:
            command.extend(['--simulator', '--no-codesign'])
        print(f'Building {app} for {args.platform}; API: {args.api_url}', flush=True)
        run(command, cwd=directory)
        if args.platform == 'android':
            artifact = directory / 'build/app/outputs/flutter-apk/app-debug.apk'
            versions = sorted((sdk / 'build-tools').glob('*/aapt'),
                              key=lambda p: tuple(int(n) for n in re.findall(r'\d+', p.parent.name)))
            if not versions:
                fail('Install Android SDK Build-Tools using Android Studio.')
            badging = run([versions[-1], 'dump', 'badging', artifact], capture=True).stdout
            package = re.search(r"package: name='([^']+)'", badging)[1]
            result = run([adb, '-s', serial, 'install', '-r', artifact], capture=True, check=False)
            if result.returncode:
                fail('APK installation failed. Existing app data was not deliberately erased. A previous release build may use a different signing key.\n' + result.stdout + result.stderr)
            if not args.no_launch:
                activity = re.search(r"launchable-activity: name='([^']+)'", badging)
                if not activity:
                    fail('Installed APK has no launchable activity.')
                component = f'{package}/{activity[1]}'
                launched = run([adb, '-s', serial, 'shell', 'am', 'start', '-W', '-n', component],
                               capture=True, check=False)
                if launched.returncode or 'Error:' in launched.stdout or 'Error:' in launched.stderr:
                    fail('Installed app could not launch.\n' + launched.stdout + launched.stderr)
                print(launched.stdout.strip())
        else:
            artifact = directory / 'build/ios/iphonesimulator/Runner.app'
            with (artifact / 'Info.plist').open('rb') as file:
                package = plistlib.load(file)['CFBundleIdentifier']
            run(['xcrun', 'simctl', 'install', selected, artifact])
            if not args.no_launch:
                run(['xcrun', 'simctl', 'launch', selected, package])
        print(f"Installed {app} ({package}) on {selected[1] if args.platform == 'android' else selected}.", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('platform', choices=['android', 'ios'])
    parser.add_argument('--app', choices=['bingo-player', 'bingo-agent', 'all'], default='bingo-player')
    parser.add_argument('--device', help='Android AVD name/emulator serial, or iOS simulator name/UDID')
    parser.add_argument('--list', action='store_true', help='List virtual devices without building or booting')
    parser.add_argument('--no-launch', action='store_true', help='Install without launching the app')
    parser.add_argument('--api-url', default=json.loads((ROOT / 'deployment/mobile/production.json').read_text())['API_BASE_URL'])
    parser.add_argument('--timeout', type=int, default=240, help='Boot timeout in seconds (default 240)')
    args = parser.parse_args()
    url = urlparse(args.api_url)
    if url.scheme not in ('http', 'https') or not url.hostname or url.username or url.password:
        parser.error('--api-url must be an HTTP(S) backend origin without credentials')
    if url.path not in ('', '/') or url.query or url.fragment:
        parser.error('--api-url must be an origin, without /api or other path/query/fragment')
    if args.timeout < 1:
        parser.error('--timeout must be positive')
    try:
        install(args)
    except (RuntimeError, OSError, subprocess.CalledProcessError) as error:
        print(f'Error: {error}', file=sys.stderr)
        return 1
    except KeyboardInterrupt:
        print('Cancelled; virtual devices are left running.', file=sys.stderr)
        return 130
    return 0


if __name__ == '__main__':
    sys.exit(main())

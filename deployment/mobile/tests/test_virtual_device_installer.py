"""Command-level installer checks; no real SDK or device is accessed."""
import argparse
import importlib.util
import json
import plistlib
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

SCRIPT = Path(__file__).resolve().parents[1] / 'install-virtual-device.py'
spec = importlib.util.spec_from_file_location('installer', SCRIPT)
installer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(installer)


class InstallerTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.args = argparse.Namespace(platform='android', app='bingo-player', list=False,
                                      no_launch=False, api_url='https://example.invalid')
        self.commands = []
        self.install_fails = False
        tool = self.root / 'sdk/build-tools/36.0.0/aapt'
        tool.parent.mkdir(parents=True)
        tool.touch()
        app = self.root / 'bingo-player/build/ios/iphonesimulator/Runner.app'
        app.mkdir(parents=True)
        with (app / 'Info.plist').open('wb') as file:
            plistlib.dump({'CFBundleIdentifier': 'com.example.actualSimulatorApp'}, file)

    def fake_run(self, args, **kwargs):
        command = list(map(str, args))
        self.commands.append(command)
        if command[0].endswith('/aapt'):
            return subprocess.CompletedProcess(command, 0, "package: name='com.example.actualApk' versionCode='3'\nlaunchable-activity: name='com.example.namespace.MainActivity'\n", '')
        if 'install' in command and self.install_fails:
            return subprocess.CompletedProcess(command, 1, '', 'INSTALL_FAILED_UPDATE_INCOMPATIBLE')
        return subprocess.CompletedProcess(command, 0, 'Success', '')

    def invoke(self):
        with patch.object(installer, 'ROOT', self.root), patch.object(installer, 'flutter', return_value='flutter'), \
             patch.object(installer, 'run', side_effect=self.fake_run), \
             patch.object(installer, 'android_device', return_value=('adb', 'emulator-5558', self.root / 'sdk', 'android-arm64')), \
             patch.object(installer, 'ios_device', return_value='test-simulator-udid'):
            installer.install(self.args)

    def test_android_installs_on_selected_emulator_and_reads_actual_package(self):
        self.invoke()
        install = next(c for c in self.commands if c[:4] == ['adb', '-s', 'emulator-5558', 'install'])
        self.assertIn('-r', install)
        launch = next(c for c in self.commands if 'start' in c)
        self.assertEqual(launch[-1], 'com.example.actualApk/com.example.namespace.MainActivity')
        build = next(c for c in self.commands if 'build' in c)
        self.assertIn('--target-platform=android-arm64', build)
        self.assertIn('--dart-define=API_BASE_URL=https://example.invalid', build)

    def test_failed_install_never_uninstalls_existing_app_or_launches(self):
        self.install_fails = True
        with self.assertRaisesRegex(RuntimeError, 'UPDATE_INCOMPATIBLE'):
            self.invoke()
        self.assertFalse(any('uninstall' in c or 'start' in c for c in self.commands))

    def test_ios_build_is_unsigned_simulator_app_and_uses_plist_bundle_id(self):
        self.args.platform = 'ios'
        self.invoke()
        build = next(c for c in self.commands if 'build' in c)
        self.assertIn('--simulator', build)
        self.assertIn('--no-codesign', build)
        self.assertIn(['xcrun', 'simctl', 'launch', 'test-simulator-udid', 'com.example.actualSimulatorApp'], self.commands)

    def test_no_launch_only_installs(self):
        self.args.no_launch = True
        self.invoke()
        self.assertFalse(any('start' in c or 'launch' in c for c in self.commands))

    def test_ios_uses_devicehub_when_legacy_simulator_app_is_missing(self):
        developer = self.root / 'Xcode.app/Contents/Developer'
        hub = developer.parent / 'Applications/DeviceHub.app'
        hub.mkdir(parents=True)
        probe = developer / 'usr/bin/simctl'
        commands = []
        def sdk_run(args, **kwargs):
            command = list(map(str, args))
            commands.append(command)
            if command == ['xcrun', '--find', 'simctl']:
                output = str(probe)
            elif command[:4] == ['xcrun', 'simctl', 'list', 'devices']:
                output = json.dumps({'devices': {'com.apple.CoreSimulator.SimRuntime.iOS-18-6': [
                    {'udid': 'selected-device', 'name': 'iPhone', 'isAvailable': True, 'state': 'Booted'}]}})
            else:
                output = ''
            return subprocess.CompletedProcess(command, 0, output, '')
        args = argparse.Namespace(list=False, device='selected-device', timeout=10)
        with patch.object(installer.sys, 'platform', 'darwin'), \
             patch.object(installer, 'run', side_effect=sdk_run), \
             patch.object(installer.subprocess, 'run', return_value=subprocess.CompletedProcess([], 0)):
            self.assertEqual(installer.ios_device(args), 'selected-device')
        self.assertIn(['open', '-a', str(hub)], commands)
        self.assertFalse(any('CurrentDeviceUDID' in ' '.join(c) for c in commands))


if __name__ == '__main__':
    unittest.main()

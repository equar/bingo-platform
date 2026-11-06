"""Resize the checked-in player icon for native launchers (macOS sips required)."""
import json
from pathlib import Path
import shutil
import subprocess

root = Path(__file__).resolve().parents[2]
app = root / 'bingo-app'
source = app / 'assets/branding/app-icon.png'
if not source.exists():
    raise SystemExit('Missing player icon source')
if not shutil.which('sips'):
    raise SystemExit('Run this helper on macOS with sips installed')

def resize(destination, pixels):
    destination.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(['sips', '-z', str(pixels), str(pixels), str(source), '--out', str(destination)],
                   check=True, stdout=subprocess.DEVNULL)

res = app / 'android/app/src/main/res'
for density, pixels in [('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)]:
    resize(res / f'mipmap-{density}/ic_launcher.png', pixels)
resize(res / 'drawable-nodpi/ic_launcher_artwork.png', 1024)
(res / 'drawable/ic_launcher_foreground.xml').write_text('''<?xml version="1.0" encoding="utf-8"?>
<inset xmlns:android="http://schemas.android.com/apk/res/android"
    android:inset="18dp">
    <bitmap android:src="@drawable/ic_launcher_artwork" android:gravity="fill" />
</inset>
''')
folder = res / 'mipmap-anydpi-v26'
folder.mkdir(exist_ok=True)
(folder / 'ic_launcher.xml').write_text('''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
''')
(res / 'values/ic_launcher_colors.xml').write_text('''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">#0052C8</color>
</resources>
''')
icons = app / 'ios/Runner/Assets.xcassets/AppIcon.appiconset'
entries = json.loads((icons / 'Contents.json').read_text())['images']
written = set()
for entry in entries:
    name = entry.get('filename')
    if not name or name in written:
        continue
    pixels = round(float(entry['size'].split('x')[0]) * float(entry['scale'].rstrip('x')))
    resize(icons / name, pixels)
    written.add(name)
print(f'Generated 5 Android legacy sizes, adaptive resources and {len(written)} iOS icon sizes.')

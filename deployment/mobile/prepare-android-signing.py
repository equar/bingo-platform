"""Create persistent per-app signing assets locally; never print passwords."""
import json, os, secrets, shutil, subprocess, sys
from pathlib import Path
root=Path(__file__).resolve().parents[2]
app=sys.argv[1]
if app != 'bingo-player': raise SystemExit('Only the Bingo Player Android client is available for signing')
require_existing='--require-existing' in sys.argv[2:]
properties=root/app/'android/key.properties'
if properties.exists():
    print('Using existing signing configuration for '+app)
    raise SystemExit()
folder=root/'deployment/mobile/signing'
folder.mkdir(mode=0o700, parents=True, exist_ok=True)
metadata=folder/(app+'.json')
keystore=folder/(app+'.jks')
legacy_metadata=folder/'bingo-app.json'
legacy_keystore=folder/'bingo-app.jks'
if app == 'bingo-player' and legacy_metadata.exists() and legacy_keystore.exists():
    settings=json.loads(legacy_metadata.read_text())
    settings['storeFile']=str(legacy_keystore)
    def escape(value):return value.replace('\\','\\\\').replace(':','\\:').replace('=','\\=')
    fd=os.open(properties,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
    with os.fdopen(fd,'w') as f:
        for key,value in settings.items():f.write(key+'='+escape(value)+'\n')
    print('Prepared Player signing configuration from the existing Play upload key')
    raise SystemExit()
if require_existing:
    raise SystemExit('Play upload signing configuration is missing; restore the registered upload key instead of generating one')
if metadata.exists():
    settings=json.loads(metadata.read_text())
    if not keystore.exists():raise SystemExit('Signing keystore is missing; restore the original key')
else:
    if keystore.exists():raise SystemExit('Signing metadata is missing; restore it rather than replace the key')
    settings={'storePassword':secrets.token_hex(32),'keyPassword':secrets.token_hex(32),'keyAlias':app}
    keytool=shutil.which('keytool')
    if not keytool:raise SystemExit('keytool is required')
    env=dict(os.environ,BINGO_STORE_PASSWORD=settings['storePassword'],BINGO_KEY_PASSWORD=settings['keyPassword'])
    subprocess.run([keytool,'-genkeypair','-noprompt','-storetype','JKS','-keystore',str(keystore),'-alias',app,'-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN='+app+', O=Equar Bingo','-storepass:env','BINGO_STORE_PASSWORD','-keypass:env','BINGO_KEY_PASSWORD'],env=env,check=True,capture_output=True)
    keystore.chmod(0o600)
    fd=os.open(metadata,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
    with os.fdopen(fd,'w') as f:json.dump(settings,f)
settings['storeFile']=str(keystore)
def escape(value):return value.replace('\\','\\\\').replace(':','\\:').replace('=','\\=')
fd=os.open(properties,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
with os.fdopen(fd,'w') as f:
    for key,value in settings.items():f.write(key+'='+escape(value)+'\n')
print('Prepared persistent release signing for '+app+'; back up deployment/mobile/signing securely')

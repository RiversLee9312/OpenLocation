"""Create a private local signing identity once. Nothing is printed or written to source control."""
import argparse
from pathlib import Path
import secrets
import subprocess

parser=argparse.ArgumentParser()
parser.add_argument('--keytool',required=True,type=Path)
args=parser.parse_args()
root=Path(__file__).resolve().parent.parent
folder=root/'.secrets';folder.mkdir(exist_ok=True)
properties=folder/'release-signing.properties'
assert not properties.exists(), 'Existing signing identity must be preserved'
password=secrets.token_urlsafe(36)
secret=folder/'key-password.tmp'
secret.write_text(password,encoding='ascii')
try:
    subprocess.run([str(args.keytool),'-genkeypair','-keystore',str(folder/'openlocation-release.p12'),'-storetype','PKCS12','-storepass:file',str(secret),'-keypass:file',str(secret),'-alias','openlocation','-keyalg','RSA','-keysize','4096','-validity','10000','-dname','CN=OpenLocation, O=OpenLocation contributors'],check=True,capture_output=True)
    properties.write_text('storeFile=.secrets/openlocation-release.p12\nstorePassword='+password+'\nkeyAlias=openlocation\nkeyPassword='+password+'\n',encoding='ascii')
finally:
    secret.unlink(missing_ok=True)
print('Private release signing identity created in ignored .secrets/. Keep a separate secure backup.')

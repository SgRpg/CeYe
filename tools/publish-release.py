"""Publish an already signed and verified CeYe APK to the designated repository."""
from pathlib import Path
import argparse, hashlib, json, os, subprocess, urllib.request, urllib.error

p=argparse.ArgumentParser();p.add_argument('version');p.add_argument('apk',type=Path);p.add_argument('notes',type=Path);args=p.parse_args()
root=Path(__file__).resolve().parents[1]
credential=subprocess.run(['git','credential','fill'],input='url=https://github.com/SgRpg/CeYe.git\n\n',text=True,capture_output=True,check=True,cwd=root,env={**os.environ,'GIT_TERMINAL_PROMPT':'0','GCM_INTERACTIVE':'never'})
fields=dict(line.split('=',1) for line in credential.stdout.splitlines() if '=' in line)
token=fields.get('password');assert token,'No GitHub authentication available'
def call(url,data=None,content_type='application/json'):
    req=urllib.request.Request(url,data=data,headers={'Authorization':'Bearer '+token,'Accept':'application/vnd.github+json','Content-Type':content_type,'User-Agent':'CeYe-release'})
    with urllib.request.urlopen(req,timeout=120) as response:return json.load(response)
sha=subprocess.check_output(['git','rev-parse','HEAD'],text=True,cwd=root).strip()
release=call('https://api.github.com/repos/SgRpg/CeYe/releases',json.dumps({'tag_name':'v'+args.version,'target_commitish':sha,'name':'册页 v'+args.version,'body':args.notes.read_text(encoding='utf-8')+'\n\nSource commit: `'+sha+'`','prerelease':True,'draft':False},ensure_ascii=False).encode())
apk=args.apk.read_bytes();name='CeYe-v'+args.version+'-release.apk';checksum=hashlib.sha256(apk).hexdigest()+'  '+name+'\n'
base=release['upload_url'].split('{')[0]
for filename,data,mime in [(name,apk,'application/vnd.android.package-archive'),('SHA256SUMS.txt',checksum.encode(),'text/plain')]:
    asset=call(base+'?name='+filename,data,mime);print(asset['browser_download_url'])
print('Release:',release['html_url'])

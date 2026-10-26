"""Tek sayfa afiş sözleşmesi; DOM paketi gerektirmeyen profil testleri."""
import json
import shutil
import subprocess
from pathlib import Path
import pytest

ROOT = Path(__file__).resolve().parents[2]


def profile(url, title, images):
    if not shutil.which('node'): pytest.skip('node gerekli')
    js = (ROOT/'android-app/app/src/main/assets/bridge/generic.js').read_text()
    code = '''const vm = require('vm');
const [src,href,title,urls] = JSON.parse(process.argv[1]);
const u=new URL(href);
const context={location:{href,hostname:u.hostname,host:u.host,pathname:u.pathname},document:{title,readyState:'complete',querySelector:()=>null,querySelectorAll:()=>[]},window:{},F360:{challenge:()=>false,flipPages:()=>null,scrollStep:()=>true,portraitImages:()=>urls,out:JSON.stringify,abs:x=>x}};
process.stdout.write(vm.runInNewContext(src, context));'''
    result = subprocess.run(['node','-e',code,json.dumps([js,url,title,images])],text=True,capture_output=True,check=True)
    return json.loads(result.stdout)


def test_single_poster_is_returned_but_unknown_homepage_photo_is_not():
    image = ['https://market.test/brosur.jpg']
    good = profile('https://market.test/kataloglar', 'Haftalık broşür', image)
    assert good['type'] == 'images' and good['urls'] == image and good['end']
    bad = profile('https://market.test/', 'Market', image)
    assert bad['type'] == 'pending' and bad['urls'] == []

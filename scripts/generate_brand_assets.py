"""Generate vector app/adaptive/monochrome icons and README artwork from one mark."""
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
BG = '#102E39'
# All geometry is inside the adaptive foreground's central safe area.
MARK = [
    ('#ECFFF7', 'M33,38H41L46,31H61L66,38H75A7,7 0,0 1,82,45V69A7,7 0,0 1,75,76H33A7,7 0,0 1,26,69V45A7,7 0,0 1,33,38Z M54,42A12,12 0,1 0,54,66A12,12 0,1 0,54,42Z', 'evenOdd'),
    ('#71E3C2', 'M54,46A8,8 0,1 0,54,62A8,8 0,1 0,54,46Z M54,50A4,4 0,1 1,54,58A4,4 0,1 1,54,50Z', 'evenOdd'),
    ('#F8C86B', 'M65,63H79A5,5 0,0 1,84,68V77A5,5 0,0 1,79,82H65A5,5 0,0 1,60,77V68A5,5 0,0 1,65,63Z', 'nonZero'),
    (BG, 'M66,68H78V71H66Z M66,74H74V77H66Z', 'nonZero'),
]
MONO = [('#FFFFFF', 'M33,38H41L46,31H61L66,38H75A7,7 0,0 1,82,45V59H65A9,9 0,0 0,56,68V76H33A7,7 0,0 1,26,69V45A7,7 0,0 1,33,38Z M54,42A12,12 0,1 0,54,66A12,12 0,1 0,54,42Z M65,63H79A5,5 0,0 1,84,68V77A5,5 0,0 1,79,82H65A5,5 0,0 1,60,77V68A5,5 0,0 1,65,63Z M66,68H78V71H66Z M66,74H74V77H66Z', 'evenOdd')]
def vector(paths, foreground_only=False):
    body='\n'.join(f'    <path android:fillColor="{c}" android:fillType="{rule}" android:pathData="{d}" />' for c,d,rule in paths)
    if foreground_only:
        body = '<group android:pivotX="54" android:pivotY="54" android:scaleX="0.86" android:scaleY="0.86">\n' + body + '\n</group>'
    elif paths and paths[0][1] == 'M0,0H108V108H0Z':
        background, mark = body.split('\n', 1)
        body = background + '\n<group android:pivotX="54" android:pivotY="54" android:scaleX="0.86" android:scaleY="0.86">\n' + mark + '\n</group>'
    return f'<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n{body}\n</vector>\n'
def svgmark():
    return '<g transform="translate(54 54) scale(.86) translate(-54 -54)">' + ''.join(f'<path fill="{c}" fill-rule="{"evenodd" if rule=="evenOdd" else "nonzero"}" d="{d}"/>' for c,d,rule in MARK) + '</g>'
res=ROOT/'app/src/main/res'
(res/'drawable/ic_app.xml').write_text(vector([(BG,'M0,0H108V108H0Z','nonZero')]+MARK))
(res/'drawable/ic_launcher_foreground.xml').write_text(vector(MARK, foreground_only=True))
(res/'drawable/ic_launcher_monochrome.xml').write_text(vector(MONO, foreground_only=True))
for level in (26,33):
    mono='\n    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />' if level==33 else ''
    text=f'<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n    <background android:drawable="@color/launcher_background" />\n    <foreground android:drawable="@drawable/ic_launcher_foreground" />{mono}\n</adaptive-icon>\n'
    (res/f'mipmap-anydpi-v{level}/ic_launcher.xml').write_text(text)
assets=ROOT/'docs/assets'
(assets/'app-icon.svg').write_text(f'<svg xmlns="http://www.w3.org/2000/svg" width="432" height="432" viewBox="0 0 108 108"><rect width="108" height="108" rx="25" fill="{BG}"/>{svgmark()}</svg>\n')
(assets/'hero.svg').write_text(f'''<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="420" viewBox="0 0 1200 420" role="img" aria-label="KWatermarkCam · 让照片留下时间与地点">
<rect width="1200" height="420" rx="32" fill="#F0F7F4"/>
<circle cx="1150" cy="12" r="205" fill="#DCEEE6"/><circle cx="1160" cy="28" r="145" fill="#E6F3ED"/>
<g transform="translate(68 88) scale(2.25)"><rect width="108" height="108" rx="25" fill="{BG}"/>{svgmark()}</g>
<g fill="{BG}" font-family="system-ui, -apple-system, sans-serif">
<text x="362" y="151" font-size="46" font-weight="750">KWatermarkCam</text>
<text x="364" y="217" font-size="34" font-weight="600">让照片留下时间与地点</text>
<text x="366" y="268" font-size="20" fill="#506B65">工程现场 · 工作记录 · 日常旅途</text>
<rect x="364" y="298" width="121" height="34" rx="17" fill="#D6EAE2"/><text x="424" y="321" text-anchor="middle" font-size="16">无需账号</text>
<rect x="498" y="298" width="121" height="34" rx="17" fill="#D6EAE2"/><text x="558" y="321" text-anchor="middle" font-size="16">离线拍摄</text>
<rect x="632" y="298" width="121" height="34" rx="17" fill="#D6EAE2"/><text x="692" y="321" text-anchor="middle" font-size="16">本机保存</text>
</g></svg>\n''')
(assets/'chatgpt-codex.svg').write_text('''<svg xmlns="http://www.w3.org/2000/svg" width="320" height="42" viewBox="0 0 320 42" role="img" aria-label="由 ChatGPT + Codex 协作开发"><rect width="320" height="42" rx="12" fill="#102E39"/><path d="M12 12H92V30H12Z" fill="#102E39"/><rect x="105" width="215" height="42" rx="12" fill="#D6EEE3"/><path d="M105 0H120V42H105Z" fill="#D6EEE3"/><g font-family="system-ui, sans-serif" font-size="14" font-weight="600"><text x="53" y="27" text-anchor="middle" fill="#ECFFF7">协作开发</text><text x="212" y="27" text-anchor="middle" fill="#102E39">ChatGPT + Codex</text></g></svg>\n''')
print('Generated adaptive, monochrome, app and README vector artwork')

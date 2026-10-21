from pathlib import Path
from xml.etree import ElementTree as ET
import cairosvg
ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/"app/src/main/res"
SVG=ROOT/"artwork/icon.svg"
svg=ET.parse(SVG).getroot()
for density,size in [("mdpi",48),("hdpi",72),("xhdpi",96),("xxhdpi",144),("xxxhdpi",192)]:
 folder=RES/f"mipmap-{density}";folder.mkdir(parents=True,exist_ok=True)
 cairosvg.svg2png(url=str(SVG),write_to=str(folder/"ic_launcher.png"),output_width=size,output_height=size)
cairosvg.svg2png(url=str(SVG),write_to=str(ROOT/"artwork/icon-preview.png"),output_width=512,output_height=512)
mark=svg.find("{http://www.w3.org/2000/svg}g")
paths=[]
for p in mark:
 paths.append('    <path android:fillColor="'+p.attrib["fill"]+'" android:pathData="'+p.attrib["d"]+'" />')
vector='<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="128" android:viewportHeight="128">'+chr(10)+chr(10).join(paths)+chr(10)+"</vector>"
vector=vector.replace(chr(10),chr(10)+'<group android:pivotX="64" android:pivotY="64" android:scaleX="0.80" android:scaleY="0.80">',1).replace("</vector>","</group></vector>")
(RES/"drawable").mkdir(parents=True,exist_ok=True)
(RES/"drawable/ic_launcher_foreground.xml").write_text(vector,encoding="utf-8")
(RES/"mipmap-anydpi-v26").mkdir(parents=True,exist_ok=True)
(RES/"mipmap-anydpi-v26/ic_launcher.xml").write_text('<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android"><background android:drawable="@color/icon_background"/><foreground android:drawable="@drawable/ic_launcher_foreground"/></adaptive-icon>',encoding="utf-8")
(RES/"values").mkdir(parents=True,exist_ok=True)
(RES/"values/colors.xml").write_text('<resources><color name="icon_background">#f4f5f1</color></resources>',encoding="utf-8")
print("Rendered SVG: five PNG densities, adaptive vector and preview")

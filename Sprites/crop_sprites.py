"""
Crops individual general sprites from composite gallery images.
Run: python crop_sprites.py
"""
from PIL import Image
import os, base64, json

SRC = r"C:\Users\weeko\Desktop\ROTK\Sprites"
OUT = r"C:\Users\weeko\Desktop\ROTK\Sprites\cropped"
os.makedirs(OUT, exist_ok=True)

# Source files
G1  = os.path.join(SRC, "Gemini_Generated_Image_buvi0wbuvi0wbuvi.png")   # 1408x768  SHU+WEI+IND gallery
G2  = os.path.join(SRC, "Gemini_Generated_Image_1dlb3g1dlb3g1dlb.png")  # 1380x752  WEI+WU gallery
ZY  = os.path.join(SRC, "Gemini_Generated_Image_s83m96s83m96s83m.png")  # 1408x768  Zhao Yun battle

# Attack-pose sources (battle panels)
BA1 = os.path.join(SRC, "Gemini_Generated_Image_cigg81cigg81cigg.png")  # 1380x752  Guan Yu/ZhangFei/LuBu/HuangZhong/XiahouDun/ZhugeLiang/CaoCao
BA2 = os.path.join(SRC, "Gemini_Generated_Image_utd062utd062utd0.png")  # 1380x752  MaChao/ZhangLiao/LvMeng/SunQuan/ZhouYu/CaoCao/XiahouDun

TARGET_W, TARGET_H = 300, 360  # all sprites normalized to this size

def crop(src_path, name, box):
    img = Image.open(src_path)
    c = img.crop(box)
    c = c.resize((TARGET_W, TARGET_H), Image.LANCZOS)
    out = os.path.join(OUT, f"{name}.png")
    c.save(out, "PNG")
    print(f"  {name}.png  {box} -> {TARGET_W}x{TARGET_H}")
    return out

print("=== Cropping IDLE sprites from gallery images ===")
# G1 = 1408×768  layout: top-row=4 generals, bottom-row=3 generals
# Top row: Guan Yu | Zhang Fei | Lu Bu | Huang Zhong
# Bottom row: Xiahou Dun | Zhuge Liang | Cao Cao
crop(G1, "guanyu_idle",     (30,  55, 380, 485))
crop(G1, "zhangfei_idle",   (360, 48, 720, 485))
crop(G1, "lubu_idle",       (545, 30,1020, 500))
crop(G1, "huangzhong_idle", (1000,55,1380, 488))
crop(G1, "xiahoudun_idle",  (40, 395, 440, 748))
crop(G1, "zhugeliang_idle", (395,405, 762, 748))
crop(G1, "caocao_idle",     (720,388,1130, 748))

# G2 = 1380×752  layout: top-row=4, bottom-row=4
# Top row: Ma Chao | Zhang Liao | Sun Quan | Lv Meng
# Bottom row: Xiahou Dun | Zhuge Liang | Cao Cao | Zhou Yu
crop(G2, "machao_idle",    (35,  55, 385, 435))
crop(G2, "zhangliao_idle", (368, 52, 718, 435))
crop(G2, "sunquan_idle",   (698, 52,1048, 435))
crop(G2, "lvmeng_idle",    (1020,57,1365, 435))
crop(G2, "zhouyu_idle",    (1020,388,1365, 742))

# Zhao Yun – use the centre charging frame from his dedicated battle scene
crop(ZY, "zhaoyun_idle",   (380, 38, 870, 710))

print("\n=== Cropping ATTACK sprites from battle panel images ===")
# BA1 = 1380×752  layout:  top-row=3 panels, bottom-row=4 panels
# Top (each ~460px wide, h=376):  Guan Yu | Zhang Fei | Lu Bu
# Bottom (each ~345px wide, h=376): Huang Zhong | Xiahou Dun | Zhuge Liang | Cao Cao
crop(BA1, "guanyu_atk",     (15,  18, 458, 370))
crop(BA1, "zhangfei_atk",   (462, 18, 922, 370))
crop(BA1, "lubu_atk",       (924, 18,1370, 370))
crop(BA1, "huangzhong_atk", (8,  388, 348, 742))
crop(BA1, "xiahoudun_atk",  (350,388, 695, 742))
crop(BA1, "zhugeliang_atk", (695,388,1038, 742))
crop(BA1, "caocao_atk",     (1038,388,1378,742))

# BA2 = 1380×752  layout: top-row=4, bottom-row=4
# Top: Ma Chao | Zhang Liao | Lv Meng | Xiahou Dun (duplicate)
# Bottom: Sun Quan | Zhou Yu | Cao Cao | Xiahou Dun (updated)
crop(BA2, "machao_atk",    (8,   18, 348, 372))
crop(BA2, "zhangliao_atk", (350, 18, 692, 372))
crop(BA2, "lvmeng_atk",    (692, 18,1035, 372))
crop(BA2, "sunquan_atk",   (8,  384, 348, 742))
crop(BA2, "zhouyu_atk",    (350,384, 692, 742))

# Zhao Yun attack – rightmost attacking frame in his scene
crop(ZY, "zhaoyun_atk",    (800, 38,1250, 710))

print("\n=== Converting to base64 for HTML embedding ===")
b64 = {}
for fname in sorted(os.listdir(OUT)):
    if fname.endswith(".png"):
        fpath = os.path.join(OUT, fname)
        with open(fpath, "rb") as f:
            data = base64.b64encode(f.read()).decode()
        key = fname.replace(".png", "")
        b64[key] = f"data:image/png;base64,{data}"
        print(f"  {key}: {len(data)} chars")

# Save the base64 map as a JS file
js_path = os.path.join(SRC, "sprites_b64.js")
with open(js_path, "w") as f:
    f.write("// Auto-generated sprite base64 map\n")
    f.write("const SPRITES = {\n")
    for k, v in b64.items():
        f.write(f'  "{k}": "{v}",\n')
    f.write("};\n")
print(f"\nSaved base64 JS map to: {js_path}")
print(f"Total sprites: {len(b64)}")

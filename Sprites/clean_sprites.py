"""
Re-crops generals more tightly (no header/label text) and removes
the dark-blue grid background, producing clean transparent PNGs.
"""
from PIL import Image
import numpy as np
from collections import deque
import os, base64

SRC = r"C:\Users\weeko\Desktop\ROTK\Sprites"
OUT = r"C:\Users\weeko\Desktop\ROTK\Sprites\clean"
os.makedirs(OUT, exist_ok=True)

TARGET_W, TARGET_H = 280, 320

# ── Background removal via flood-fill from image edges ────────────────────────
def remove_bg(img, tolerance=38):
    arr = np.array(img.convert("RGBA"), dtype=np.int32)
    h, w = arr.shape[:2]

    # Estimate background colour from all 4 edges
    edges = np.concatenate([
        arr[0,  :, :3],   # top row
        arr[-1, :, :3],   # bottom row
        arr[:, 0,  :3],   # left col
        arr[:, -1, :3],   # right col
    ])
    bg = np.median(edges, axis=0)   # e.g. [22, 46, 92]

    visited = np.zeros((h, w), bool)
    q = deque()

    # Seed every edge pixel
    for x in range(w):
        for y in [0, h-1]:
            if not visited[y, x]: visited[y, x] = True; q.append((y, x))
    for y in range(h):
        for x in [0, w-1]:
            if not visited[y, x]: visited[y, x] = True; q.append((y, x))

    alpha = arr[:, :, 3].copy()
    while q:
        y, x = q.popleft()
        diff = int(np.sum(np.abs(arr[y, x, :3] - bg)))
        if diff < tolerance * 3:
            alpha[y, x] = 0
            for dy, dx in ((-1,0),(1,0),(0,-1),(0,1)):
                ny, nx = y+dy, x+dx
                if 0 <= ny < h and 0 <= nx < w and not visited[ny, nx]:
                    visited[ny, nx] = True
                    q.append((ny, nx))

    result = arr.copy()
    result[:, :, 3] = alpha
    return Image.fromarray(result.astype(np.uint8))

def crop_clean(src_path, name, box, do_bg_remove=True):
    img = Image.open(src_path)
    c   = img.crop(box)
    c   = c.resize((TARGET_W, TARGET_H), Image.LANCZOS)
    if do_bg_remove:
        c = remove_bg(c)
    else:
        c = c.convert("RGBA")
    out = os.path.join(OUT, f"{name}.png")
    c.save(out, "PNG")
    print(f"  {name}.png")
    return out

# ── Source files ──────────────────────────────────────────────────────────────
G1  = os.path.join(SRC, "Gemini_Generated_Image_buvi0wbuvi0wbuvi.png")   # 1408×768
G2  = os.path.join(SRC, "Gemini_Generated_Image_1dlb3g1dlb3g1dlb.png")  # 1380×752
ZY  = os.path.join(SRC, "Gemini_Generated_Image_s83m96s83m96s83m.png")  # 1408×768 Zhao Yun
BA1 = os.path.join(SRC, "Gemini_Generated_Image_cigg81cigg81cigg.png")  # 1380×752 battle 1
BA2 = os.path.join(SRC, "Gemini_Generated_Image_utd062utd062utd0.png")  # 1380×752 battle 2

# ── IDLE sprites from gallery images ─────────────────────────────────────────
# G1 layout: top-row generals start below header (~y=125), name labels ~y=450-475
# bottom-row: generals body ~y=430-720, name labels ~y=690-720
print("=== IDLE sprites (gallery) ===")

# Top row (y: 125 → 462) – avoids "WARRIOR SPRITE GALLERY" header and name labels
crop_clean(G1, "guanyu_idle",     ( 32, 124, 375, 462))
crop_clean(G1, "zhangfei_idle",   (358, 122, 720, 462))
crop_clean(G1, "lubu_idle",       (656,  95,1040, 492))  # taller – halberd
crop_clean(G1, "huangzhong_idle", (998, 124,1378, 462))

# Bottom row (y: 430 → 718) – start after top-row name labels
crop_clean(G1, "xiahoudun_idle",  ( 42, 428, 440, 718))
crop_clean(G1, "zhugeliang_idle", (506, 430, 798, 718))
crop_clean(G1, "caocao_idle",     (720, 425,1128, 718))

# G2 top-row (y: 125 → 418)
crop_clean(G2, "machao_idle",    ( 38, 124, 382, 418))
crop_clean(G2, "zhangliao_idle", (370, 122, 716, 418))
crop_clean(G2, "sunquan_idle",   (698, 122,1046, 418))
crop_clean(G2, "lvmeng_idle",    (1022,124,1366, 418))

# G2 bottom-row (y: 436 → 720)
crop_clean(G2, "zhouyu_idle",    (1038, 436,1366, 720))

# Zhao Yun – centre charging frame from his own dedicated scene
# (battle scene background – use remove_bg with looser tolerance)
crop_clean(ZY, "zhaoyun_idle",   (385, 42, 870, 680), do_bg_remove=True)

# ── ATTACK sprites from battle panels ─────────────────────────────────────────
# BA1 panels: top 3 cols × top half = 460×376 each
#             bottom 4 cols × bottom half = 345×376 each
# Within each panel, HUD is top ~72px, "BATTLE START:" text bottom ~68px
# So clean action area inside panel: y+72 → y+308

print("=== ATTACK sprites (battle panels) ===")
def bp(img, name, panel_x, panel_y, panel_w, panel_h, hud_h=72, footer_h=68):
    box = (panel_x, panel_y + hud_h, panel_x + panel_w, panel_y + panel_h - footer_h)
    crop_clean(img, name, box, do_bg_remove=False)  # battlefield bg – keep it

# BA1 top row: 3 panels of 460×376
bp(BA1, "guanyu_atk",     0,   0, 460, 376)
bp(BA1, "zhangfei_atk",   460, 0, 460, 376)
bp(BA1, "lubu_atk",       920, 0, 460, 376)
# BA1 bottom row: 4 panels of 345×376
bp(BA1, "huangzhong_atk", 0,   376, 345, 376)
bp(BA1, "xiahoudun_atk",  345, 376, 345, 376)
bp(BA1, "zhugeliang_atk", 690, 376, 345, 376)
bp(BA1, "caocao_atk",     1035,376, 345, 376)

# BA2 top row: 4 panels of 345×376
bp(BA2, "machao_atk",    0,   0, 345, 376)
bp(BA2, "zhangliao_atk", 345, 0, 345, 376)
bp(BA2, "lvmeng_atk",    690, 0, 345, 376)
# BA2 bottom row
bp(BA2, "sunquan_atk",   0,   376, 345, 376)
bp(BA2, "zhouyu_atk",    345, 376, 345, 376)

# Zhao Yun attack – rightmost panel of his scene
crop_clean(ZY, "zhaoyun_atk",  (800, 42,1250, 680), do_bg_remove=False)

# ── Re-generate sprites_b64.js ────────────────────────────────────────────────
print("\n=== Rebuilding sprites_b64.js ===")
b64 = {}
for fname in sorted(os.listdir(OUT)):
    if fname.endswith(".png"):
        with open(os.path.join(OUT, fname), "rb") as f:
            data = base64.b64encode(f.read()).decode()
        b64[fname.replace(".png", "")] = f"data:image/png;base64,{data}"

js_path = os.path.join(SRC, "sprites_b64.js")
with open(js_path, "w", encoding="utf-8") as f:
    f.write("const SPRITES = {\n")
    for k, v in b64.items():
        f.write(f'  "{k}": "{v}",\n')
    f.write("};\n")

total_kb = os.path.getsize(js_path) // 1024
print(f"Done — {len(b64)} sprites, {total_kb} KB")
print("Run inject_sprites.py next to update index.html")

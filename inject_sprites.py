"""
Injects sprite base64 data + new image-rendering code into index.html.
Run: python inject_sprites.py
"""
import os, re

ROOT    = r"C:\Users\weeko\Desktop\ROTK"
SPR_JS  = os.path.join(ROOT, "Sprites", "sprites_b64.js")
IN_HTML = os.path.join(ROOT, "index.html")
OUT_HTML= os.path.join(ROOT, "index.html")

# ── Read sprites_b64.js ──────────────────────────────────────────────────────
with open(SPR_JS, "r", encoding="utf-8") as f:
    sprites_js = f.read()

# ── Read current index.html ─────────────────────────────────────────────────
with open(IN_HTML, "r", encoding="utf-8") as f:
    html = f.read()

# ── 1. Inject SPRITES constant inline (before main <script>) ─────────────────
SPRITE_INJECT = f"""<script>
// ── SPRITE BASE64 DATA (auto-injected) ──────────────────────────────────────
{sprites_js}
</script>
"""
# Insert just before the closing </head> tag
html = html.replace("</head>", SPRITE_INJECT + "</head>", 1)

# ── 2. Add loadSprites() + IMG map right after "const KEYS={}" ──────────────
SPRITE_LOADER = """
// ── SPRITE IMAGE OBJECTS ────────────────────────────────────────────────────
const SPRITE_SRC = {}; // data URLs for <img> tags
const IMG = {};        // HTMLImageElement objects for canvas drawImage

function loadSprites() {
  if (typeof SPRITES === 'undefined') { console.warn('SPRITES not found'); return; }
  for (const [key, src] of Object.entries(SPRITES)) {
    SPRITE_SRC[key] = src;
    const img = new Image();
    img.src = src;
    IMG[key] = img;
  }
  console.log('Sprites loaded:', Object.keys(IMG).length);
}

"""

html = html.replace("const KEYS={};", "const KEYS={};\n" + SPRITE_LOADER, 1)

# ── 3. Replace drawCombatant() with image-based version ─────────────────────
NEW_DRAW_COMBATANT = """// ── SPRITE-BASED COMBATANT RENDER ──────────────────────────────────────────
const SP_W = 168, SP_H = 200; // sprite display size (maintains 300:360 ratio)

function drawCombatant(cb, gd, isPlayer, frame) {
  if (!gd || !cb) return;

  const isAtk  = cb.atkFrame > 0;
  const imgKey = gd.id + (isAtk ? '_atk' : '_idle');
  const img    = IMG[imgKey] || IMG[gd.id + '_idle'];
  const sprY   = GNDX - SP_H + 22;  // bottom of sprite sits on ground

  CX.save();

  // ── Dead: fall sideways ──────────────────────────────────────────────
  if (cb.hp <= 0) {
    cb.deadRot = Math.min((cb.deadRot || 0) + 0.035, Math.PI * 0.48);
    const alpha = Math.max(0.2, 1 - cb.deadRot / Math.PI * 0.9);
    CX.globalAlpha = alpha;
    CX.translate(cb.x, sprY + SP_H / 2);
    CX.rotate(cb.deadRot * (isPlayer ? 1 : -1));
    if (img && img.complete) {
      if (!isPlayer) { CX.scale(-1, 1); }
      CX.drawImage(img, -SP_W / 2, -SP_H / 2, SP_W, SP_H);
    }
    CX.restore();
    return;
  }

  // ── Status visual effects ────────────────────────────────────────────
  if (cb.hurtTimer > 0) {
    CX.filter = `brightness(${130 + cb.hurtTimer * 4}%) saturate(200%) hue-rotate(-20deg)`;
  } else if (cb.invincTimer > 0 && Math.floor(frame / 4) % 2 === 0) {
    CX.globalAlpha = 0.55;
    CX.filter = 'brightness(220%) hue-rotate(180deg)';
  } else if (cb.burnTimer > 0 && Math.floor(frame / 3) % 2 === 0) {
    CX.filter = 'sepia(1) saturate(4) hue-rotate(-30deg) brightness(130%)';
  } else if (cb.stunTimer > 0 && Math.floor(frame / 5) % 2 === 0) {
    CX.filter = 'brightness(70%) grayscale(80%)';
  }

  // ── Draw sprite (mirror enemy) ───────────────────────────────────────
  if (img && img.complete) {
    if (!isPlayer) {
      CX.translate(cb.x + SP_W / 2, 0);
      CX.scale(-1, 1);
      CX.drawImage(img, 0, sprY, SP_W, SP_H);
    } else {
      CX.drawImage(img, cb.x - SP_W / 2, sprY, SP_W, SP_H);
    }
  } else {
    // Fallback placeholder while loading
    const fc = FAC[gd.f];
    CX.fillStyle = fc.bg + 'aa';
    CX.fillRect(cb.x - SP_W / 2, sprY, SP_W, SP_H);
    CX.fillStyle = fc.hi;
    CX.font = 'bold 32px Georgia';
    CX.textAlign = 'center';
    CX.fillText(gd.char, cb.x, sprY + SP_H / 2);
  }

  CX.restore();

  // ── HP bar above sprite ──────────────────────────────────────────────
  const bw = 90, bh = 9;
  const hpPct = clamp(cb.hp / cb.maxHp, 0, 1);
  const bx = cb.x - bw / 2, by = sprY - 18;
  CX.fillStyle = 'rgba(0,0,0,0.75)';
  CX.fillRect(bx, by, bw, bh);
  const hpCol = hpPct > 0.5 ? '#44ee44' : hpPct > 0.25 ? '#ffaa22' : '#ff2222';
  CX.fillStyle = hpCol;
  CX.fillRect(bx, by, bw * hpPct, bh);
  CX.strokeStyle = '#666'; CX.lineWidth = 1;
  CX.strokeRect(bx, by, bw, bh);

  // Name
  CX.fillStyle = '#fff'; CX.font = 'bold 11px Georgia';
  CX.textAlign = 'center'; CX.shadowColor = '#000'; CX.shadowBlur = 4;
  CX.fillText(gd.name, cb.x, by - 3);
  CX.shadowBlur = 0;

  // SP bar + READY label (player only)
  if (isPlayer) {
    const spPct = clamp(cb.sp / 100, 0, 1);
    CX.fillStyle = 'rgba(0,0,0,0.6)';
    CX.fillRect(bx, by + bh + 2, bw, 5);
    CX.fillStyle = '#aa44ff';
    CX.fillRect(bx, by + bh + 2, bw * spPct, 5);
    if (cb.sp >= 100) {
      CX.fillStyle = '#ff88ff'; CX.font = 'bold 9px Georgia';
      CX.shadowColor = '#660066'; CX.shadowBlur = 4;
      CX.fillText('✨ SPECIAL READY', cb.x, by + bh + 14);
      CX.shadowBlur = 0;
    }
  }
}
"""

# Find and replace the old drawCombatant function
# Match from "// ── HORSE + RIDER DRAWING" to just before drawBG or drawParts
old_pattern = re.compile(
    r"// ── (SPRITE-BASED COMBATANT RENDER|HORSE \+ RIDER DRAWING).*?"
    r"(?=// ═══|// ── PARTICLES|function drawBG|// ── Background draw)",
    re.DOTALL
)
if old_pattern.search(html):
    html = old_pattern.sub(NEW_DRAW_COMBATANT, html, count=1)
    print("Replaced drawCombatant (regex match)")
else:
    # Fallback: insert before drawBG
    html = html.replace("function drawBG(){", NEW_DRAW_COMBATANT + "\nfunction drawBG(){", 1)
    print("Inserted drawCombatant before drawBG (fallback)")

# ── 4. Update character select to show sprite thumbnails ────────────────────
OLD_GPORT = '<div class="gport" style="background:${FAC[g.f].bg};color:${g.cc||FAC[g.f].hi}">${g.char}</div>'
NEW_GPORT = """<div class="gport" style="background:${FAC[g.f].bg};overflow:hidden;padding:0">
        ${SPRITE_SRC[g.id+'_idle']
          ? \`<img src="\${SPRITE_SRC[g.id+'_idle']}" style="width:100%;height:100%;object-fit:cover;object-position:center 20%">\`
          : \`<span style="color:\${g.cc||FAC[g.f].hi};font-size:2em;line-height:60px">\${g.char}</span>\`
        }</div>"""
html = html.replace(OLD_GPORT, NEW_GPORT, 1)

# ── 5. Call loadSprites() at init ───────────────────────────────────────────
html = html.replace("loadPR();showTitle();", "loadPR();loadSprites();showTitle();", 1)

# ── Write output ─────────────────────────────────────────────────────────────
with open(OUT_HTML, "w", encoding="utf-8") as f:
    f.write(html)

size_kb = os.path.getsize(OUT_HTML) // 1024
print(f"\nDone! index.html updated: {size_kb} KB")
print("Open C:\\Users\\weeko\\Desktop\\ROTK\\index.html in browser")

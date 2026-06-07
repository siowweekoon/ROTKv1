# Three Kingdoms Duel — CLAUDE.md

## How to run
Open `C:\Users\weeko\Desktop\ROTK\index.html` in browser, OR visit `https://siowweekoon.github.io/ROTKv1/`
After any change: `git add . && git commit -m "msg" && git push` to deploy.

## File structure
```
C:\Users\weeko\Desktop\ROTK\
  index.html          ← entire game (HTML + CSS + JS, single file)
  CLAUDE.md           ← this file
  *.png               ← portrait images (root level)
  Sprites/clean/      ← sprite sheet PNGs for battle animations
```

## Generals (13 playable + story-only units)
| Faction | Tier | Generals |
|---|---|---|
| SHU | T2 | Guan Yu, Zhang Fei, Zhao Yun, Ma Chao |
| SHU | T1 | Huang Zhong, Zhuge Liang, Liu Bei |
| WEI | T2 | Zhang Liao, Xiahou Dun |
| WEI | T1 | Cao Cao |
| WU | T1 | Sun Quan, Lv Meng, Zhou Yu |
| IND | T2 | Lü Bu, Dong Zhuo |

**storyOnly:true units (hidden from generals page, no free card, not acquirable):**
- `asiainvincible` — Asia the Invincible (removed as general AND as opponent)
- `xiahouden` — Xia Hou En (locked as general; Story Phase 2 boss only)
- `footsword` — Wei Swordsman (story wave enemy)
- `footshield` — Wei Shieldman (story wave enemy)

**Rule:** Any future NPC/story-only unit must get `storyOnly:true` in GENS. `renderSel()` filters with `!g.storyOnly`.

**Tier system (for battle progression):**
- TIER1 (round 1 opponents): lvmeng, zhugeliang, zhouyu, sunquan, caocao, liubei, huangzhong
- TIER2 (round 2 opponents): zhangfei, zhaoyun, machao, xiahoudun, zhangliao, lubu, dongzhuo

**Key special skills:**
- Zhuge Liang: Eight Trigrams = WAR×1.9 bolt + stun (uses WAR not STR)
- Liu Bei: Benevolent Rule = heal DEX×1.5 (uses DEX not CHR)
- Zhou Yu: Fire Attack = burn 50dmg/sec 5sec, player attack +30% during burn
- Asia Invincible: KuiHuaBaoDian = DEX×3.5 + stun 40f (storyOnly — not encountered in normal play)
- Cai Wenji Ghost + Nine Yin Ghost: drain 10% enemy HP → heal player

## Stats on general object
`war`, `str` (was int), `dex` (was chr), `spd`, `maxHp`

**Note:** Stats were globally renamed: `int` → `str`, `chr` → `dex` throughout codebase.

## Controls (mobile touch + keyboard)
- Z / ATTACK button: Attack
- S: Special (100 SP)
- ← → / MOVE buttons: Move
- Guard: AUTO — retreating (moving away) auto-guards vs Tier 1; Tier 2 bypasses all guard
- No manual guard button

## Damage formula
```
player base = effectiveWAR × 2.1 + PG.str × 0.4 + rand(6,24)
enemy  base = effectiveWAR × 1.95 + EG.str × 0.3 + rand(6,21)
player DEX reduces incoming: dmg × (1 - PG.dex × 0.002)
Tier 1 enemies: always half damage (retreating = 40%, standing = 50%)
Tier 2 enemies: no guard, full damage
Special damage capped at 50% of skill fn result
```

## General card (roguelike deck) system
- `PR.deck = [{id:'guanyu'}, ...]` — player's hand of general cards
- Start: 2 random cards (no free Asia card — that line was removed)
- Select screen only shows generals in deck (others greyed/locked with 🔒)
- Win B2: card returned + 1 Elite card + choose 1 reward (3 slots)
- Lose B1: card gone, Common card issued
- Lose B2: card gone, Common card issued, player picks 1 item to lose
- 0 cards = cannot fight, must New Game

## Reward system (4 slots)
`PR.equipped = {wife, scripture, horse, weapon}` — one active per slot, applied in startBattle()
`PR.wives / PR.scriptures / PR.horses / PR.weapons` — full inventory arrays

### Adding a new reward category (e.g. Jewelry)
1. Add array to RWDS object: `jewelry: [{id, name, icon, img, type:'jewelry', desc}]`
2. Add to PR: `jewelry:[]` and `equipped.jewelry:null` in freshPR()
3. Add to loadPR migration: `if(!PR.jewelry)PR.jewelry=[]`
4. Handle in claimRew/buyReward/sellReward/sacrificeReward/equipItem using the type string
5. Apply bonus in startBattle(): `const j=PR.equipped.jewelry; if(j){...}`
6. Add to equip modal sections array: `{label:'💎 Jewelry', pool:PR.jewelry, slot:'jewelry'}`
7. Add to openReward(): `pick(RWDS.jewelry)` as 5th pool
8. Add to market buy/sell rows
9. Add `equipped.jewelry` to showTitle() display array
10. Update GOLD prices object

### Current rewards

**Wives** (max 1 each, no duplicates):
| Wife | Bonus |
|---|---|
| Diao Chan | +5 SPD, +10 STR |
| Xiao Qiao | +10 SPD, +5 DEX |
| Da Qiao | +15 SPD, +10 DEX |
| Wang Zhaojun | +10 SPD, +5 STR |
| Xi Shi | +10 SPD, +5 WAR |
| Lady Sun | +10 WAR, +8 DEX |
| Zhen Ji | +12 STR, +8 SPD |
| Huang Yueying | SP gain ×1.2, STR +8 |
| Cai Wenji | All stats +5, Ghost drain HP 10% on special |
| Lady Bian | Max HP +60, DEX +10 |

**Scriptures:**
| Scripture | Effect |
|---|---|
| Art of War | Special +25%, WAR +10 |
| Iron Body Scroll | STR +15, Max HP +30 |
| Wind Rider Manual | SP gain +60% |
| Dragon Tactics | Cooldown -15%, SPD +10 |
| Nine Yin Manual | Ghost drain HP 10%, cooldown -15% |
| Tiger Crane Manual | 12% double strike (normal hits only) |
| Six Meridian Sword | Special +50% effective (only direct hit) |
| Five Rings Codex | WAR +15, STR +15 |
| Shaolin Iron Palm | Each hit -5 enemy SPD (cap -25) |
| Iron Shirt Scripture | Take 20% less damage |

**Horses:**
| Horse | Effect |
|---|---|
| Red Hare | SPD +30, WAR +15 |
| Shadow Runner | Dodge 20%, SPD +30 (elite) |
| White Dragon | Max HP +40, SPD +10 |
| Storm Stallion | SPD +5%, Stun +0.3s |

**Weapons:**
| Weapon | Effect |
|---|---|
| Green Dragon Crescent Blade | WAR +15 |
| Serpent Spear | STR +12, knockback ×1.5 (elite) |

## Gold economy
`PR.gold` — currency. Start: 500g. Earned: +20 mid-win, +50 full-win, +10 loss.
Market buy: T1 card 200g, normal scripture/horse/weapon (non-elite).
Market sell: scriptures/horses/weapons only (stacked ×count view), no wives/cards.

## Sprite system
Two rendering paths in drawCombatant():

**Path 1 — SHEET_DEFS (animated sheets):**
```javascript
SHEET_DEFS = {
  generid: {
    src: 'path/to/sheet.png',
    displayH: 155,   // rendered height
    yOff: -10,       // vertical offset (negative = up)
    anims: {
      idle:   {x, y, w, h, frames, dur},
      move:   {x, y, w, h, frames, dur},
      charge: {x, y, w, h, frames, dur},
      atk:    {x, y, w, h, frames, dur},
      // Per-anim altSrc for frames from a DIFFERENT file:
      move: {altSrc:'Sprites/clean/Other.png', x, y, w, h, frames, dur},
    }
  }
}
```
- `altSrc` loads a second image under key `gid_animName`; used when different animations come from different sprite sheets
- CRITICAL: encode spaces in filenames as `%20` in src/altSrc strings
- White background removal: works on http server; on file:// uses multiply blend fallback

**Path 2 — Overrides table (old embedded sprites):**
```javascript
overrides = {
  'generid_state': {sc: 1.0, yOff: 18, xOff: 0}
}
```
- Default yOff=18. Higher yOff = lower on screen.
- `enemyYOff = -10` applied to all enemies automatically

## Reward display (openReward panel)
- `rwdPool = [pick(RWDS.wives), pick(RWDS.scriptures), pick(RWDS.horses), pick(RWDS.weapons)]`
- Cards rendered in `.ropts` flex container
- Mobile: 2×2 CSS grid (`grid-template-columns: 1fr 1fr`) via `@media(max-width:640px) and (orientation:portrait)`
- Desktop: horizontal flex wrap
- `has-img` class on `.rcard` removes padding so portrait fills card top-to-bottom
- Card portrait height: `.ri-img{height:160px}` (desktop), `110px` (mobile)

## Key pitfalls / lessons learned
1. **JSON.parse/stringify strips functions** — re-attach `special.fn` from GENS after cloning PG/EG
2. **Spaces in filenames** — must use `%20` in src URLs, not literal spaces
3. **equipItem pool lookup** — must explicitly handle every slot type: wife/scripture/horse/weapon/(jewelry)
4. **claimRew/buyReward/sellReward/sacrificeReward** — all need the same `typeToSlot` mapping; if a new type is added, update ALL four functions
5. **New image files** — must `git add *.png` separately; `git add index.html` only stages code changes
6. **Global stat renames** — int→str, chr→dex done with replace_all:true; verify no false positives after

## Architecture
Single-file HTML. All CSS and JS inline. No frameworks, no build step.
`PR` (persistent rewards) stored in localStorage key `tkd3`.
`GENS` array holds all generals. `SHEET_DEFS` holds sprite sheet metadata.
GitHub Pages: `https://siowweekoon.github.io/ROTKv1/`

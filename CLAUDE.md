# Three Kingdoms Duel — CLAUDE.md

## How to run
Open `C:\Users\weeko\Desktop\ROTK\index.html` in any browser. No server needed.

## File structure
```
C:\Users\weeko\Desktop\ROTK\
  index.html   ← entire game (HTML + CSS + JS, single file)
  CLAUDE.md    ← this file
```

## Game concept
ROTK II-style 1v1 general duel. Player picks a general, fights AI opponent, wins rewards.

## Generals (13 total)
| Faction | Generals |
|---|---|
| SHU | Guan Yu, Zhang Fei, Zhao Yun, Huang Zhong, Ma Chao, Zhuge Liang |
| WEI | Cao Cao, Zhang Liao, Xiahou Dun |
| WU | Sun Quan, Lv Meng, Zhou Yu |
| IND | Lü Bu |

## Stats
`war`, `int`, `chr`, `spd`, `maxHp` — all on general object.

## Controls
- Z: Attack | X / ↓: Guard (hold) | S: Special (100 SP) | ← →: Move

## Actions
| Action | Mechanic |
|---|---|
| Strike ⚔️ | WAR-based damage (×1.5 vs old) |
| Guard 🛡️ | Incoming damage = min(dmg×0.5, 100) |
| Skill ✨ | Unique per general, costs 100 SP |

## SP system
- Max 100. Successful attack = +30 SP (attacker). Successful guard block = +30 SP (defender). Being hit unguarded = +15 SP.
- Wind Rider Manual scripture gives player ×1.6 SP gain
- 50% chance to start each battle with 100 SP (special immediately available)

## Low HP modifier
- When HP < 1/3 maxHp: WAR × 0.7 (attack power reduced 30%)

## Status effects
| Effect | Mechanic |
|---|---|
| stun | Skip next turn |
| burn | -35 HP/turn for N turns (Zhou Yu) |
| weaken | WAR ×0.75 for N turns (Sun Quan) |
| invincible | Block next hit, cleared on use (Xiahou Dun) |
| taunted | Forced Strike next turn |

## Damage formula
```
player base = effectiveWAR × 2.1 + rand(6,24)   [WAR = war×0.7 if HP < 1/3 max]
enemy  base = effectiveWAR × 1.95 + rand(6,21)
guard  → min(dmg × 0.5, 100)
Art of War scripture → skill damage +25% (post-hoc bonus)
```

## Post-victory rewards (persistent, localStorage key: `tkd_pr`)
### Wives (CHR/INT/WAR bonus each battle)
- Diao Chan: +15 CHR
- Xiao Qiao: +10 INT
- Da Qiao: +10 WAR

### Scriptures (gameplay modifier)
- Art of War: skill damage +20%
- Iron Body Scroll: max HP +50
- Wind Rider Manual: SP gain ×1.5
- Dragon Tactics: crit rate +10%

### Horses (stat/mechanic bonus)
- Red Hare (Lü Bu's): WAR +10, always go first
- Shadow Runner: INT +8, dodge +10%
- White Dragon: max HP +80
- Storm Stallion: all stats +5

Player holds 1 of each type; new reward replaces old of same type.

## Adding a new general
In `index.html`, add to the `GENERALS` array:
```javascript
{ id:'genid', name:'Name', title:'Title', faction:'SHU|WEI|WU|IND',
  char:'漢', cc:'#rrggbb',        // kanji portrait + color
  war:N, int:N, chr:N, spd:N, maxHp:N,
  skill:{ name:'Skill Name', icon:'emoji', desc:'short desc',
    fn:(p,e)=>{ /* p=player state, e=enemy state, modify e.hp etc. */
      return {msg:'log message'}; }},
  quotes:{ battle:[...], skill:[...], victory:[...], defeat:[...] }
}
```

## Adding a new reward
Add to `REWARDS.wives`, `REWARDS.scriptures`, or `REWARDS.horses` arrays.
Apply the effect either in `startBattle()` (stat bonuses) or in `calcDmg()` / `gainSP()` (gameplay modifiers).

## Architecture note
Single-file HTML. All CSS and JS are inline. No frameworks, no build step.
Game state lives in `BS` (battle state) and `PR` (persistent rewards).
`localStorage` key `tkd_pr` stores wins + current wife/scripture/horse.

# AbilitySMP (Unbound SMP) — v12.0 Galaxy Bosses, Weapons & the Item Shop

A Paper 1.21 plugin: powers, Power Activator, quests, upgrades, duels, six galaxy bosses,
legendary weapons from AltarSMP, an Item Shop, and a DonutSMP-style spawn.

## How to build it (GitHub build method)

1. Put all of these files in a GitHub repository (keep the folder layout exactly as it is —
   the `.java` files and `pom.xml` sit in the top folder, that is on purpose).
2. Push. The workflow in `.github/workflows/build.yml` builds the plugin for you.
3. On GitHub, open the **Actions** tab → the latest **Build AbilitySMP** run → **Artifacts**
   at the bottom → download **AbilitySMP**. Inside is `AbilitySMP.jar`.
4. Drop `AbilitySMP.jar` into your server's `plugins` folder and restart.

Building on your own computer works too, if you have Java 21 and Maven:

```
mvn package
```

The jar appears in `target/AbilitySMP.jar`.

## What's new in v12.0 — Galaxy Bosses, Weapons & the Item Shop

- **Six galaxy bosses replace the old three.** The Headless Horseman, the Mimic and the Ghost are
  gone. `/boss` lists the new six: **Notazombiemc** (160 HP; a giant zombie whose fans rush in at
  half health) and the **Astronout** (360 HP; moon hops, a ray-gun volley and a zero-gravity beam)
  are **permanent**. **Comet Knight** (200), **Solar Titan** (280), **Void Serpent** (240) and
  **Eclipse Reaper** (450) are **seasonal** — they leave on the date in `config.yml`
  (`galaxy-bosses.seasonal-until`, default Nov 30 2026, 11:59 PM Toronto). Same fight rules as
  always: per-fight arena, Fight Totem (pops = out, never death), boss party, per-boss cooldowns,
  and wins pay Unbound Coins, Galaxy Coins, crate loot and quest progress.
- **14 legendary weapons from AltarSMP + the Item Shop (`/itemshop`, `/weapons`).** Hyperion,
  Nightpiercer, Frost Scythe, Wand of Illusion, Bone Blade, Bloodlust, Vulcan's Crossbow, Shadow
  Blade, Cutlass, Paladin's Battle Axe, Mindweaver, Pure Blade, Earth Gauntlet, Wither Reaper —
  rebuilt as AbilitySMP items (same `legendary_weapon` tag AltarSMP used), priced in Unbound
  Coins and **always in stock**. Right-click = ability 1, crouch + right-click = ability 2. Two
  honest simplifications: the Wand of Illusion can't truly disguise you without a packet library,
  so a captured soul powers a burst/release instead; Vulcan's arrows ignite but don't literally
  pierce shields. The shop's other half is **daily deals**: 6 rotating items restock every day
  (midnight Toronto) with limited stock, configured under `itemshop.daily`.
- **The Lasso.** Craftable (4 string around a stick). Right-click throws it at what you're looking
  at (12 blocks): **1.5 hearts**, a whip sound, an animated rope, and the target is yanked toward
  you. Cooldown **1 second**. Numbers live under `lasso:` in `config.yml`.
- **New power: Hacker.** Finish a quest **7 days in a row** (a quest streak — `/quests` shows it;
  miss a day and it resets) to unlock it. Its super **dupes the nearest player's best sword**: the
  copy is fully yours for **30 seconds**, then it vanishes (it can't be dropped or kept). Cooldown
  **1 minute**. Never sold, never in crates, never rerolled.
- **Power slots at the top.** The two quick-switch slots now also show at the **top of the screen**
  on a bar (`quick-switch.top-bar`), and are pinned at the top of `/powers` with icons that wear
  the powers' orb look.
- **The Power Pack (resource pack).** `AbilitySMP-PowerPack.zip` (delivered alongside the plugin)
  gives every power a held orb: the Power Activator's CustomModelData is set to `2000 + power
  index`, and the pack maps that to a per-power ball texture (a flame ball for Flame, an ice orb
  for Frost, a green `>_` orb for Hacker...). Players just enable the pack; without it the
  Activator stays a normal iron sword.
- **Spawn glow-up.** `/spawnbuild` now builds a radius-13 platform with a stepped rim, glowing
  edge, carpet paths to each NPC, quartz lamp posts, potted flowers, a crate alcove with a
  floating "POWER CRATES" label, and a floating donut ring with a lantern chandelier above the
  middle. Re-running it upgrades an old spawn in place (old crate chests are re-registered).

## What's new in v11.0 — The Spawn Update

- **A little DonutSMP-style spawn.** `/spawnbuild` (admin) builds the whole thing in one go: a round
  platform with lamp posts, the countdown board, 5 NPCs and 3 crate chests, and sets `/spawn` to the
  middle. Prefer your own build? `/setspawn` saves your spot instead, and `/spawnnpc` places the
  pieces by hand (`rtp`, `shop`, `event`, `ranked`, `duels`, `board`, `remove`).
- **NPCs you right-click.** **Random Teleport** warps you to a safe spot up to 5,000 blocks out
  (30s cooldown, also `/rtp`). **Power Shop** opens `/shop`. **Event Shop** opens the current event
  shop (the Galaxy Shop today). **Ranked** opens the ranked menu. **Duels** opens a head-picker of
  everyone online — click one to send a normal duel request. Brand-new players start at spawn.
- **The countdown board.** Floating text at spawn counts down to the next update every second,
  using the same date as `/galactic` (so `/galactic set` moves it), and flips to
  **IT IS OUT NOW!** the moment the update lands.
- **Crate chests at spawn.** Real chest blocks, DonutSMP style: right-click one with a Power Crate
  Key in your inventory to spin the normal roulette. The chest is never consumed — only the key is.
  Admins can turn any chest into a spawn crate with `/crate set` (`/crate unset` undoes it); spawn
  crates can't be broken by players or blown up.

## What's new in v10.0 — THE GALACTIC UPDATE

- **Halloween retired, nothing lost.** The Halloween Shop, Trick or Treat Night and the Halloween
  `/panel` events are gone. Everything players already earned stays forever: Halloween powers
  (they still work, they're just not sold or rolled anymore), ranks, titles, costumes, and even
  Halloween Coin items already in inventories.
- **19 new powers.** The headliners: **Supernova** (idle fire resistance; super shoots 3 fireballs,
  20s cooldown), **Earth** (idle Resistance II; super traps your enemy in a stone box while you
  get Strength III and minions hunt only them for 1 minute; 5-minute cooldown), **Frost** (idle
  Frost Walker; super freezes every enemy in 30 blocks for 5s, then frost damage for 20s) and
  **Wind** (idle no fall damage; super charges in the air 2s then slams for 15 hearts in 15
  blocks). **Wind is one of a kind**: only one player can ever own it and it only comes from an
  admin command (`/power set wind <player>`); the plugin refuses a second owner everywhere.
  Plus 15 more: Starlight, Comet, Meteor, Lunar, Pulsar, Nebula, Solar Flare, Eclipse, Asteroid,
  Zero Gravity, Wormhole, Quasar, Black Hole, Antimatter, Rocket. All priced in Unbound Coins
  by strength (60 for Starlight up to 2500 for Earth) and all in Power Crates except Wind.
- **Galaxy Coins + `/gshop`.** A new physical currency (prismarine crystals). Bosses pay it now
  (Horseman 30, Mimic 100, Ghost 10), as do some quests, rebirths and events. The Galaxy Shop
  mirrors the old Halloween Shop — regular stock plus limited-time items (until Nov 16 2026):
  galaxy power stars, a Space Helmet, a Star Cloak, the Galaxy Guardian title, the Nebula Trail.
- **The quick switch.** Equip **two** powers at once: in `/powers`, left-click sets slot 1,
  right-click sets slot 2. The action bar above the hotbar shows `[1] Power  [2] Power`, like a
  little loadout bar. Switch in game with the **swap-hands key (F)** or **crouch + right-click**
  the Power Activator (the crouch way works on Bedrock). Switching mid-fight is blocked wherever
  a Fight Totem is in play.
- **6 new admin events** (`/panel` → Server Events). **Supernova Spleef**: everyone is teleported
  to a molten arena, right-click shoots fireballs, break the magma floor, Fight Totems on,
  last one standing wins Galaxy Coins (60s limit). **Galaxy Rush**: Speed III for everyone under
  a night sky full of star particles for 2 minutes. **Verity**: everyone gets Strength III, a
  Fight Totem and a one-shot **Verity** snowball in a yellow arena for the length of the song
  (~2:30, `galaxy-events.verity.seconds`); popped players are sent home, survivors get Galaxy
  Coins. Plus **Meteor Shower**, **Moon Gravity** and **Star Rain**. A plugin still can't stream
  the actual YouTube song — the server plays a dark note-block version of it live instead.
- **Staff ranks.** `/staffrank <player> <mod|tester|co-owner|none>` — **OP only**. A staff rank
  (Mod = green, Tester = yellow, Co-Owner = red) replaces the player's normal rank in chat and
  the tab list until removed.
- **More locker loot:** Space Helmet + Star Cloak costumes, the Nebula Trail, and the Galaxy
  Guardian / Star Traveler titles.

## What's new in v9.0

- **Power Crates (`/crate`) and rarities.** Powers now have rarities — Common, Rare, Epic,
  Legendary, Mythic — shown in `/powers` and `/shop` and priced by rarity in the shop. The new
  way to get powers is the **Power Crate**: right-click a crate with a Power Crate Key in your
  inventory and spin the roulette (odds follow the rarities). Crates and keys drop from boss
  fights (the Mimic always drops a key), and are sold in the Halloween Shop, given on rebirths.
- **Ranked duels (`/ranked`).** Queue against a random player or fight a practice bot matched
  to your rank — Bronze bots are easy; Master / Unbound King bots are monsters; the IMPOSSIBLE
  bot is 200 HP of netherite pain (no rating, just glory). Ladder: Bronze → Silver → Gold →
  Platinum → Diamond → Master → **Unbound King** (2600). Fight Totem rules: nobody dies,
  nobody loses items. Rating starts at 1000; players use elo-style changes, bots are +15/−10.
- **Trading (`/trade <player>`).** A shared trade window: your side / their side, nothing moves
  until BOTH confirm, and changing an offer un-confirms both. Soulbound items can't be traded.
- **Rebirths (`/rebirth`).** Reset your Unbound Coins and power levels (powers are kept) for
  +10% coins forever per rebirth, a crate set and Halloween Coins. Cost grows quadratically
  and needs boss kills — more rebirths, better rewards.
- **Power levels to 100.** Levels past 50 count double, so a level 100 power is ~16x stronger.
  Upgrade costs grow steeply past the listed prices — a true endgame grind.
- **30 new ranks** (playtime, quests, boss hunter, duels, ranked, collecting, rebirths) plus
  **titles that display** in chat and the tab list, and **Halloween costumes that actually
  equip** from `/locker` (your real armor is saved and restored). New trails too.
- **Boss cooldowns.** Horseman 5 min, Mimic 10 min, Ghost 3 min (`boss.cooldown-seconds`).
- **Economy rebalance.** Slower playtime coins, higher quest targets, shop restocks every
  8 minutes with 2 stock, rarity-based prices, lower boss/duel coin payouts.
- **Better Halloween Shop.** Crates, keys, elytra, netherite, costumes and titles; the
  limited-time items now run until **Nov 9**.
- **New `/panel` events.** Blood Moon, Headless Horseman Moon, Disco Party, Raining Tacos /
  Diamonds / Gold / Candy, custom note-block songs (Music menu), and **THE SNAP** — a cutscene
  where Notazombiemc presses a button and every event starts at once. A plugin can't add real
  custom music files; for that, put your audio in a resource pack and add its sound key under
  `music.tracks` in `config.yml`.
- **Galactic Update countdown (`/galactic`).** Default: **Sunday Nov 1 2026, 1:00 PM**
  (America/Toronto). Change it in `config.yml` (`galactic.release`) or in game with
  `/galactic set 2026-11-01 13:00`.

## What's new in v8.0

- **The Fight Totem (death bug fixed).** Boss fights and duels can no longer kill you.
  When a fight starts you get a tagged **Fight Totem** in your off hand. A hit that would
  kill you pops the totem instead: you lose the fight, but you never die and never drop
  anything. Win, and the totem disappears. Only the tagged Fight Totem is ever removed —
  your own normal totems are never touched, and your old off-hand item is given back.
- **New boss: Mimic** — `/boss fight Mimic`. A giant chest monster (chest head, red glow,
  long pink tongue). Phases by health:
  - 100–75%: his tongue lashes out, drags you to him and he bites you.
  - 75–50% (**Chomper**): bites do **4 hearts** and poison you; he also weakens you for 5s.
  - 50–25% (**Mimicer**): spawns a copy of you wearing your gear that hunts you.
  - 25–0% (**ULTRA MIMIC**): spawns **2 giant copies of you** — 6 hearts per hit, but any
    weapon kills them in exactly 2 hits — and he uses every attack at once.
  - Reward: **100 Halloween Coins, 0 Unbound Coins**.
- **New boss: Ghost** — `/boss fight Ghost`. An easy boss with 20 hearts. He is visible
  for 5 seconds, then invisible for 3 seconds (he sneaks behind you and hits you), then
  visible again, over and over. Reward: **10 Halloween Coins, 5 Unbound Coins**.
- **Separate boss arenas + bring a friend.** Every boss fight (Horseman included) now gets
  its own arena copy, so nobody waits for the arena. `/boss invite <player>` asks a friend
  to join; they run `/boss accept`; when you start any boss they are teleported in with
  you, get their own Fight Totem, and earn the same rewards if they're still standing at
  the end. If your totem pops you're knocked out — the fight only ends when everyone is out.
- **10 new Halloween powers** (in `/powers` and `/shop`): Pumpkin, Witch, Skeleton, Spider,
  Scarecrow, Reaper, Candy, Phantom, Werewolf, Curse.

## Commands

| Command | What it does |
|---|---|
| `/boss` | The galaxy boss list (who is ready, who is resting, who has left) |
| `/boss fight <name>` | Fight a galaxy boss: notazombiemc, astronout (forever) or comet-knight, solar-titan, void-serpent, eclipse-reaper (seasonal) |
| `/boss invite <player>` / `/boss accept` / `/boss deny` | Boss party: bring one friend into your boss fights |
| `/boss leave` | Leave your current boss fight |
| `/boss leaveparty` | Leave your boss party |
| `/duel <player>` | Friendly duel (Fight Totems, nobody dies) |
| `/ranked` | Ranked duels: random player queue, rank-matched bots, IMPOSSIBLE bot |
| `/crate` | Your Power Crates and keys (right-click a crate to open it) |
| `/trade <player>` | Safe item trading (both players confirm) |
| `/rebirth` | Rebirth: reset coins + power levels for permanent bonuses |
| `/galactic` | The Galactic Update is out! (was the countdown) |
| `/gshop` | The Galaxy Shop - spend Galaxy Coins |
| `/staffrank <player> <rank>` | Give a staff rank: Mod, Tester or Co-Owner (OP only) |
| `/spawn` / `/rtp` | Go to spawn / random teleport into the wild |
| `/setspawn` / `/spawnnpc` / `/spawnbuild` | Set up the spawn: point, NPCs + countdown board, or the whole platform (admin) |
| `/itemshop` / `/weapons` | The Item Shop (legendary weapons + daily deals) / the weapon list |
| `/powers`, `/shop`, `/quests`, `/locker`, `/halloweenshop`, `/notes` | Menus, shops and patch notes |

Boss health and rewards live in `config.yml` under `galaxy-bosses:` (cooldowns under
`boss.cooldown-seconds:`).

## A note on the boss looks

A plain Paper plugin cannot add custom 3D models, so the bosses are built from vanilla
pieces: Notazombiemc is a giant green-armored zombie, the Astronout wears a glass helmet and
a white space suit, the Eclipse Reaper is a tall wither skeleton with a scythe. The one piece
of truly custom art in v12 is the **Power Pack** resource pack: a hand-drawn orb texture for
every power, shown on the Power Activator.

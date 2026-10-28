# AbilitySMP (Unbound SMP) — v9.0 The Galactic Update Eve

A Paper 1.21 plugin: powers, Power Activator, quests, upgrades, duels, Trick or Treat Night,
and Halloween bosses.

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
| `/boss fight Headless Horseman` | Fight the Headless Horseman (40 Unbound, 30 Halloween Coins) |
| `/boss fight Mimic` | Fight the Mimic (100 Halloween Coins) |
| `/boss fight Ghost` | Fight the Ghost (10 Halloween, 5 Unbound Coins) |
| `/boss invite <player>` / `/boss accept` / `/boss deny` | Boss party: bring one friend into your boss fights |
| `/boss leave` | Leave your current boss fight |
| `/boss leaveparty` | Leave your boss party |
| `/duel <player>` | Friendly duel (Fight Totems, nobody dies) |
| `/ranked` | Ranked duels: random player queue, rank-matched bots, IMPOSSIBLE bot |
| `/crate` | Your Power Crates and keys (right-click a crate to open it) |
| `/trade <player>` | Safe item trading (both players confirm) |
| `/rebirth` | Rebirth: reset coins + power levels for permanent bonuses |
| `/galactic` | Countdown to the Galactic Update (Sunday Nov 1, 1:00 PM) |
| `/powers`, `/shop`, `/quests`, `/locker`, `/halloweenshop`, `/notes` | Menus, shops and patch notes |

Boss numbers (health, damage, rewards, arena spots) all live in `config.yml` under
`horseman:`, `mimic:` and `ghost-boss:`.

## A note on the boss looks

A plain Paper plugin cannot add custom 3D models, so the bosses are built from vanilla
pieces, as close to the pictures as a plugin can get: the Mimic is a giant invisible zombie
wearing a **chest for a head** with dark red armor and a red glow, with a pink particle
tongue; the Ghost is a **giant glowing purple Vex** (see-through and flying) that truly
turns invisible. For the exact picture models you would need a resource pack / model
plugin on top of this.

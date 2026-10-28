# AbilitySMP (Unbound SMP) — v8.0 Halloween Boss Update

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
| `/boss fight Headless Horseman` | Fight the Headless Horseman (50 Unbound, 30 Halloween Coins) |
| `/boss fight Mimic` | Fight the Mimic (100 Halloween Coins) |
| `/boss fight Ghost` | Fight the Ghost (10 Halloween, 5 Unbound Coins) |
| `/boss invite <player>` / `/boss accept` / `/boss deny` | Boss party: bring one friend into your boss fights |
| `/boss leave` | Leave your current boss fight |
| `/boss leaveparty` | Leave your boss party |
| `/duel <player>` | Friendly duel (Fight Totems, nobody dies) |
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

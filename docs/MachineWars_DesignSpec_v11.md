# MACHINE WARS
## Game Design Specification
*Draft — March 2026*

---

## 1. Unit Statistics

### 1.1 Base Stats

| Unit | HP | Range | Damage | Fire Rate | Speed | Base DPS |
|---|---|---|---|---|---|---|
| Brute | 150 | 1.5 | 15 | 1.0/s | 2.0 | 15 |
| Skirmisher | 80 | 4.0 | 8 | 2.5/s | 5.0 | 20 |
| Artillery | 50 | 8.0 | 70 | 0.5/s | 1.5 | 35 |

### 1.2 Rock-Paper-Scissors Triangle

- Brute beats Skirmisher
- Skirmisher beats Artillery
- Artillery beats Brute — and Buildings

The favored attacker applies a **1.5x damage multiplier**. The multiplier is one-directional — it applies to the attacker only, not the defender. This preserves the principle that quantity can be its own quality; an unfavored swarm still grinds through a single powerful unit.

### 1.3 Matchup Effective DPS

| Matchup | Attacker DPS | Multiplier | Effective DPS |
|---|---|---|---|
| Brute vs Skirmisher | 15 | 1.5x (favored) | 22.5 |
| Skirmisher vs Brute | 20 | 1.0x | 20 |
| Skirmisher vs Artillery | 20 | 1.5x (favored) | 30 |
| Artillery vs Skirmisher | 35 | 1.0x | 35 |
| Artillery vs Brute | 35 | 1.5x (favored) | 52.5 |
| Brute vs Artillery | 15 | 1.0x | 15 |

### 1.4 Dodge Probability

Dodge is relative-speed based, giving zero advantage against peers and a natural advantage for faster defenders against slower attackers.

**Formula:** `dodge% = max(0, (defender_speed / attacker_speed - 1) × k)`, where `k ≈ 0.107`

| Matchup | Speed Ratio | Dodge Chance | Notes |
|---|---|---|---|
| Skirmisher vs Artillery | 3.33x | ~25% | Hard to track |
| Skirmisher vs Brute | 2.5x | ~16% | Meaningful edge |
| Brute vs Artillery | 1.33x | ~3.5% | Sliver of benefit |
| Any peer matchup | 1.0x | 0% | No advantage |

### 1.5 Damage Variance

Damage is rolled around the adjusted average (base × multiplier) using a **3-dice bell curve** with ±15% spread. This strongly clusters results near the mean while permitting rare extremes, avoiding the Civ I problem where lucky rolls produce absurd outcomes.

- Roll 3 dice, average the results
- Spread: ±15% around adjusted average
- Multiplier is applied to the average before rolling, not to the roll itself
- High fire rate units (Skirmisher) self-correct variance naturally across many rolls
- Artillery's slow fire rate makes each shot a meaningful event

---

## 2. Combat Resolution

### 2.1 Shot Resolution Order

For each shot fired:

1. Attacker checks target priority (preferred class first, else nearest)
2. Apply damage multiplier if attacker has class advantage
3. Roll damage: 3-dice bell curve, ±15% around adjusted average
4. Dodge check: defender rolls against relative-speed formula
5. Apply damage to target HP
6. If target HP ≤ 0, check wreckage threshold

### 2.2 Approach and Range

Units advance until a target is within their own firing range, then stop and engage. Range asymmetry creates meaningful free-hit windows during approach. Artillery at range 8 vs Brute at range 1.5 creates approximately 6.5 units of one-sided fire during approach — about 3 seconds at Brute speed 2.0, enough to deal ~60 damage before the fight becomes mutual.

Skirmisher closes the same Artillery gap in ~1.3 seconds, taking only ~35 damage before its speed and fire rate advantage comes online. The approach phase is where range pays off, not a separate mechanic.

---

## 3. Terrain & Wreckage

### 3.1 Wreckage

When a unit is destroyed, its killing blow determines whether wreckage spawns:

**Wreckage HP = (unit base HP × 0.5) − excess damage**

- If wreckage HP ≤ 0: clean kill — no wreckage spawned
- If wreckage HP > 0: wreckage spawns with that HP value
- Wreckage is weak to all damage types and clears quickly under sustained fire
- Wreckage follows the same permeability rules as junk (see 3.2)

Artillery tends to overkill and leave clean lanes. Skirmishers grinding with high fire rate and low damage accumulate wreckage. The battlefield reflects how the fight was won.

### 3.2 Terrain Permeability

| Obstacle Type | Brute | Skirmisher | Artillery |
|---|---|---|---|
| Wall (lane boundary) | Blocked | Blocked | Fires over |
| Space (lane boundary) | Blocked | Fires across | Fires across |
| Junk / Wreckage | Blocked until cleared | Partial absorption | Unaffected |

Junk and wreckage absorb all Brute damage and some Skirmisher damage until destroyed. Artillery ignores both entirely. Maps may include pre-placed junk. Certain map events may spawn junk mid-mission, telegraphed with a visual cue before landing.

---

## 4. Maps & Lanes

### 4.1 Lane System

The battlefield is divided into three lanes. Each shared boundary between adjacent lanes is either a **Wall** or a **Space**. With two boundaries, there are exactly four possible map configurations:

| Map | Left Boundary | Right Boundary | Character |
|---|---|---|---|
| A | Wall | Wall | Fully isolated corridors. Pure lane game. |
| B | Wall | Space | Hybrid. Right side fluid, left locked. |
| C | Space | Wall | Mirror of B. |
| D | Space | Space | Open field. Full cross-lane projection. |

### 4.2 Player Control

**Spawn and Go:** units deploy immediately and advance automatically. Player directs each unit to a lane via tap-to-select and tap-or-drag-to-assign. No formation commands, no hold position. Tactical depth comes from lane assignment and composition, not micromanagement.

### 4.3 Map Generation

Maps are procedurally generated once and saved — players can learn, replay, and share them. The generator enforces constraints: every map has at least one traversable path per side, Artillery has at least one lane with meaningful open approach distance, and at least one open or semi-open boundary exists so Skirmisher cross-field range is not entirely negated.

---

## 5. Factory & Drivetrain

### 5.1 Overview

The factory occupies a **4×6 grid** attached to the player's end of the battlefield. Every grid point can hold a gear, pulley, or machine. The motor is fixed to the map and has one output pulley and one output gear, allowing two initial branches without consuming additional grid points.

The player's primary gameplay is routing and tuning this network. Belts are free to place. Gears and pulleys cost resources and consume grid points. Network efficiency depends on gear ratios; each producer has a preferred torque-to-speed balance that determines its output rate and quality. Machines are fixed to the map — the player brings their own components but works with whatever producers the factory provides.

### 5.2 Story Context

The player is a scavenger moving between abandoned factories, configuring each one as circumstances allow. Components — gears, pulleys, boost machines — are carried from mission to mission. The machines themselves (combat producers, miners) are fixed to each factory's floor. The player's expertise and inventory travel; the factory's bones belong to whoever built it.

### 5.3 Component Sizing

All gears and pulleys are sized on an integer scale of **1 to 8**, relative to a standard size of 4. The size integer is displayed on the component face in the factory grid — a circle labeled "4" is a standard pulley, a circle labeled "2" is a half-size pulley. No legend or tooltip required to read the network at a glance.

The ratio between any two connected components is simply the two integers. A size-2 driving a size-4 is a 1:2 ratio — slower output, higher torque. A size-4 driving a size-2 is a 2:1 ratio — faster output, lower torque. Machines expose a size-4 interface by default, giving the player room to gear up or down from the factory baseline.

| Driver | Driven | Ratio | Effect |
|---|---|---|---|
| 4 | 2 | 2:1 | Faster output, lower torque |
| 2 | 4 | 1:2 | Slower output, higher torque |
| 4 | 4 | 1:1 | Neutral transfer |
| 1 | 8 | 1:8 | Very slow, maximum torque |

### 5.4 Drivetrain Components

| Component | Function | Notes |
|---|---|---|
| Motor | Generates total network power; fixed to map | Has one output pulley and one output gear built in |
| Belt | Transmits power between pulleys; free to place | Efficiency loss per unit length; better belts reduce loss |
| Pulley | Belt endpoint; integer size 1–8 | Size determines ratio contribution; mounts to belt side |
| Gear | Mesh transfer; integer size 1–8; enables branching | Connects directly to adjacent gear or machine gear interface |
| Gear Pulley | Hybrid: belt input, gear output (or vice versa) | Used for mid-network branching where belt and gear paths meet |
| Producer / Machine | Output machine; fixed to map | Has built-in interface; player connects to it via matching component type |

### 5.5 Wear and Energy

Every rotating component consumes a small share of the motor's output through friction. This is the same physical process that causes wear — components are breaking themselves down by working. The motor's total output is constant and knowable; what varies is how much reaches the producers after drivetrain losses.

Baseline wear rate is high. The factories are abandoned, the equipment is already broken, and there is no lubrication. A component running fast under high load breaks down faster than one turning slowly under light load — wear scales with both speed and power. The player's tuning decisions have direct wear consequences. Push for speed, pay in maintenance.

### 5.6 Degradation Stages

Each component passes through three visible degradation stages:

- **Stage 1 — Functional:** Some scuffs, a little grime. Nominal efficiency, baseline wear rate. Kris found it recently.
- **Stage 2 — Worse for wear:** Bent teeth, surface rust, clearly been through it. Modest efficiency penalty, slightly higher energy draw, accelerated wear rate. Should probably be replaced.
- **Stage 3 — Critical:** Cracked housing, missing chunks, sparking. Meaningful efficiency penalty, high energy draw, rapidly approaching expiry. Recycle now or lose it.

The recycling decision is most interesting at stage 2 — recycle for partial ore now, or squeeze more life out of it and risk hitting stage 3 mid-wave when nothing can be done. Stage 3 is obvious. Stage 1 you keep. Stage 2 is the judgment call.

A component that expires at stage 3 disappears from the grid immediately and must be replaced from inventory or purchased from the store. The network gap it leaves affects output until the slot is filled.

- Motor output is fixed and displayed to the player
- Each component shows its current energy draw and wear state via press-and-hold tooltip
- Worn components cost slightly more energy through increased friction
- Degradation is visible on the component face — no tooltip required to read urgency at a glance

### 5.7 Machine Types

| Machine | Output | Notes |
|---|---|---|
| Combat (Brute / Skirmisher / Artillery) | Deploys unit to assigned lane | Preferred target class drives AI; fixed to map |
| Miner | Generates ore passively | Universal resource; destroying enemy miner is high-value |
| Boost Machine | Emits buff field | Player-owned; carried between missions; unlocked via artifacts |

### 5.8 Boost Machines

- **Amplifier** — increases damage output of connected units at cost of higher energy draw
- **Capacitor** — increases energy available to the network
- **Armor Plater** — increases effective HP of nearby units
- **Targeting System** — reduces damage variance, tightening rolls toward the average

### 5.9 UI: Factory Grid Readouts

Two layers of information are available to the player during a match:

- **Machine face display** — bar fill rate (output per second) printed directly on the producer. The primary operational readout. No interaction required.
- **Press-and-hold tooltip** — diagnostic layer for any component or machine. Shows current RPM, target RPM, wear percentage, energy draw, and output quality. Used for troubleshooting and network planning, not routine play.

A player can run entirely off machine face readouts once familiar with the system. Tooltips are available for when something feels wrong or a network change is being planned.

### 5.10 Grid Persistence

The player's component inventory (gears, pulleys, gear pulleys, boost machines) persists between missions. Each new map presents a factory with a fixed motor position and fixed producer machines. The player routes their owned components through the available grid space to connect and tune the network.

Grid space is a scarce resource — a complex network fills available points quickly. Component wear naturally limits inventory size over time. Components can be recycled for ore before they expire. There is no explicit carrying capacity.

---

## 6. Economy

### 6.1 Resources

- **Ore** — universal currency for components and in-match store purchases. Generated passively by Miner machines during battle. The same ore carries between missions — no conversion, no arbitrary loss on mission end.
- **Artifacts** — rare items earned on mission completion. Spent in the upgrade store to permanently unlock boost machines and upgrade nodes. Not subject to in-battle spending.

### 6.2 Ore Reserve

Each map has a finite ore reserve that depletes as miners extract it. Once the reserve is exhausted, miners produce nothing. This creates natural time pressure without a timer — the player cannot farm indefinitely, and the field itself sets the economic ceiling for the mission.

- Reserve size varies by mission difficulty, objective type, and expected mission length
- Sized to cover expected component replacements plus meaningful store purchases for a well-played run
- Enemy miners draw from the same reserve — destroying an enemy miner permanently cuts their income and preserves ore for the player
- Reserve tuning reference: one baseline medium-difficulty mission is calibrated first; all others are expressed as multipliers of that baseline

Between missions, banked ore carries over. The reserve depletion mechanic prevents early-mission farming without punishing players who play efficiently and bank a surplus.

### 6.3 Store

The store rotates between waves, offering a small random selection of standard components (gears, pulleys, gear pulleys). Artifact-unlocked items are permanently available once earned. Worn components can be recycled for ore between waves.

Random rotation on commodity components encourages adaptation. Permanent artifact unlocks protect preferred strategies at the higher tier. Belts are always free and do not appear in the store.

---

## 7. Upgrade System

### 7.1 Per-Unit Upgrades (Tradeoff-Based)

Each unit type has upgrade paths that shift its stat profile rather than simply improving it. Upgrades change how a unit plays, not just how powerful it is.

| Unit | Upgrade Path A | Upgrade Path B | Effect |
|---|---|---|---|
| Brute | Reinforced Hull | Light Assault Kit | A: +HP, −Speed. B: +Speed, −HP |
| Skirmisher | Bigger Gun | Hair Trigger | A: +Damage, −Speed, −Fire Rate. B: +Fire Rate, −Damage |
| Artillery | Longer Barrel | Rapid Loader | A: +Range, +Damage, −Fire Rate. B: +Fire Rate, −Damage |

### 7.2 Global Research Upgrades

These apply across all machines and do not change unit identity. They represent factory-wide material and engineering improvements.

- **Materials Research** — increases HP across all units by a flat percentage
- **Motor Efficiency** — increases total network power output
- **Precision Manufacturing** — tightens damage variance slightly across all producers

Global upgrades interact with per-unit upgrades. A Skirmisher with Bigger Gun loses speed; Materials Research partially compensates with better survivability. The trees are independent but their effects compound.

---

## 8. Mission Types

### 8.1 Base Attack

A large wall at the far end of the field represents the enemy base. Destroy it to win. Artillery's favored multiplier against buildings makes it near-essential. Enemy waves continue until the base falls or the player is eliminated. Difficulty scaling: enemy mass and composition increase with each wave.

### 8.2 Timed Survival

Survive until the timer expires, then destroy every enemy in the wave currently in progress. Pure turtling is not viable — the active wave must be cleared. Wave count is a function of mission duration and spawn rate. Store rotations are limited by wave count, making each shopping decision more valuable.

### 8.3 Resource Hunt

Accumulate a target ore amount in the treasury — not just mine it, but hold it. Spending on units or components depletes the treasury. The player must balance combat capability against economic accumulation. Aggressive spending to dominate the field can make the target unreachable. Enemy waves continue until the target is reached or the player is eliminated.

---

## 9. Difficulty & Progression

### 9.1 Enemy Scaling Levers

Three independent axes control enemy difficulty, applied progressively across the campaign:

- **Mass** — more units, faster spawning, earlier pressure
- **Composition** — smarter mixing of unit types that counter player habits and recently unlocked tools
- **Unlocks** — enemy waves gain access to boosted units and support machines at higher levels

Early levels use mass only. Mid-game introduces composition pressure. Late game combines all three. The endgame continues to increase mass indefinitely after the player's tech tree is complete — purely an execution challenge.

### 9.2 Campaign Structure

Linear campaign, approximately 30–40 missions. The upgrade tree plateaus around mission 20–25, after which challenge is driven entirely by enemy mass scaling. Artifact rewards gate upgrade tree progress; one artifact per mission completion funds one upgrade node.

### 9.3 Tech Tree Depth

Approximately 3–4 tiers per unit upgrade path, 2–3 tiers of global research. Shallow enough to complete in a medium-length campaign, deep enough that each mission introduces a meaningful new capability or tradeoff.

### 9.4 Enemy AI Doctrine

The enemy does not run a fixed wave table. It runs a production queue that ticks on a configurable interval, samples current battlefield state, and makes spawn decisions weighted toward countering observed player composition. The result is an opponent that learns what the player is doing and responds — not a script, not a random draw, but a doctrine.

#### Information Asymmetry

The enemy sees only what is on the field. It does not see the player's factory configuration, inventory, or queued production. It observes deployed units and draws inferences from them.

This is the correct information boundary for two reasons. First, it is fair — the enemy learns the player's strategy the same way a real opponent would, by watching what gets sent. Second, it preserves the player's ability to conceal intentions. A factory configured for Artillery but currently fielding Skirmishers is playing a strategy the enemy cannot fully anticipate until the Artillery arrives. The factory is a hidden hand.

#### Composition Sampling

On each production tick the enemy samples the current battlefield and constructs a composition profile of player-deployed units. The profile is a rolling window — recent deployments are weighted more heavily than older ones, so the enemy responds to what the player is doing now rather than what they did three waves ago.

The window length is a tuning parameter. A short window makes the enemy reactive and exploitable — bait it with Skirmishers, switch to Artillery. A long window makes it sluggish and beatable by composition pivots. The correct value is probably mission-dependent, tightening as difficulty increases.

#### Counter Weighting

Spawn decisions are weighted toward the class that counters the player's dominant observed type, but not exclusively. A pure counter strategy is itself an exploit — the player baits the enemy into over-committing to one type and then pivots. The enemy must maintain a base distribution across all types and apply counter pressure as a tilt, not an absolute.

Suggested baseline tilt at medium difficulty: 50% toward counter type, 30% neutral, 20% wild card. The wild card preserves the possibility of accidental pressure and prevents the player from fully solving the enemy's decision tree.

At higher difficulties the counter tilt increases and the wild card shrinks. The endgame enemy is not unpredictable — it is relentless and increasingly correct.

#### Doctrine Memory

Beyond the rolling composition window, the enemy maintains a simple doctrine memory across waves within a mission. If the player's composition has been stable across multiple waves, the enemy increases its counter commitment. If the player pivots, the enemy's counter commitment resets and it resamples.

This means a player who commits to one strategy faces escalating counter pressure over time. A player who adapts forces the enemy to adapt in turn. The strategic layer stays alive across the full mission rather than being solved in wave one.

#### Enemy Factory

The enemy factory is not visible to the player and is not simulated at the drivetrain level. Enemy spawn rate and composition are driven directly by the doctrine logic. The fiction is that the enemy has a factory; the implementation does not require one. This may change in later development if asymmetric factory visibility becomes a design goal.

Enemy production rate scales with mission difficulty per §9.1. Doctrine logic is applied on top of the base rate — the enemy gets smarter and faster simultaneously at higher difficulties.

#### Replayability Implication

Because the enemy reads player state rather than executing a script, two runs with different factory configurations produce genuinely different games. The enemy's response to an Artillery-heavy player is structurally different from its response to a Skirmisher swarm. The player's strategic choices are the difficulty generator.

This is the intended replayability mechanism. Maps are procedurally generated and saved (§4.3); enemy doctrine is dynamic within each run. The combination means a saved map played twice with different factory approaches is a different strategic problem both times.

---

## 10. Setting & Aesthetic

The game's setting is intentionally evocative rather than narrative. Visual cues, unit names, and upgrade flavor text suggest a world — a scavenger making their way through industrial wreckage, configuring abandoned factories and fielding improvised machines against whatever opposes them — without committing to exposition. The player's imagination fills the gap.

The drivetrain metaphor is literal and visible. Belts turn, gears mesh, producers crank out materiel. The factory is a living machine that the player tends while watching the battlefield unfold. The aesthetic of improvised, mechanical, oil-stained industry runs through unit designs, map environments, and the upgrade store.

Artifacts are scavenged components — rare finds that unlock new capabilities. Ore is the universal medium of exchange, extracted from the battlefield itself. The economy has a narrative justification baked in without requiring any story to explain it.

### 10.1 Player Character

The player character is **Kris**, a scrappy scavenger making their way through the ruins of Los Angeles. At the start of the game the player selects which Kris they are — both versions are the same character with the same story, distinguished visually by silhouette. One reads as slightly curvier than the other. Both are lean — slim pickings and walking everywhere sees to that. The distinction is present for the player who wants it without being the point. No mechanical difference, no separate dialogue, no separate anything.

Kris: medium-short blonde hair, large dust goggles worn on the forehead, baggy light brown coveralls, faded black boots. A rag towel hangs from the back pocket. A wrench is in the left hand. Kris is a mechanic first and a fighter by necessity — nothing about the design suggests combat readiness. The coveralls are faded, the boots are worn. She has been doing this a while.

Kris is visible in the factory grid at roughly one grid square in size, moving between machines to tinker. This is a cosmetic presence that grounds the factory as a place someone inhabits rather than an abstract system. The character sprite is small and unobtrusive enough not to interfere with grid interaction.

### 10.2 Battlefield

The battlefield occupies two thirds to three quarters of the screen. The surface is badly damaged pavement. Brick walls divide the vertical lanes as determined by the selected map. At the far end is either open wasteland or a fortified enemy base wall. At the near end is a sturdy wall separating the battlefield from the factory grid.

Floor color varies per level: stained grey concrete is the default, dirt brown suggests a more deteriorated structure, marble tile appears occasionally — the remnant of some ConsoliCo corporate lobby that survived the apocalypse with its pretensions intact. The variation is random within a curated set.

A bombed-out billboard stands in the background to one side — left or right varies by map. It carries the game's ad placement in base attack missions (see 12.1). In other mission types a distant billboard serves the same purpose. The placement is always environmental, always outside the active lane area.

### 10.3 ConsoliCo Propaganda and the SLM Billboard

The in-universe fake ads that fill billboard slots when no paid ad is available (§12.1) are generated on-device by a small language model rather than drawn from a static list. The model generates ConsoliCo corporate communications — memos, slogans, productivity directives, wellness initiatives — in the register of an organization that has survived the apocalypse primarily by continuing to send internal communications about it.

The aesthetic is deadpan corporate horror. The model is not prompted to be funny. It is prompted to be sincere, and sincerity is what makes it funny.

Example register:

- "ConsoliCo: Your Productivity Is Our Priority"
- "ConsoliCo: We Miss You At Your Workstation"
- "ConsoliCo: Resistance Is A Performance Review Issue"
- "ConsoliCo: The Restructuring Is Nearly Complete"

Generated output is displayed as-is. Occasional incoherence is not filtered. Corporate communications are already half incoherent; the slop is the aesthetic. Output that makes no sense reads as either a transmission error or evidence that ConsoliCo's internal systems have also seen better days. Both interpretations are correct.

#### Implementation

On-device inference uses the MediaPipe LLM Inference API or equivalent Android-available runtime. No network call is required or made for propaganda generation. This is consistent with the offline behavior requirements in §11.1 and the data egress constraints in the security testing plan §1.

If the device does not support on-device inference, a curated static list of ConsoliCo communications serves as fallback. The fallback is indistinguishable from the generated output to the player. The billboard is never empty and never breaks.

The SLM is never load-bearing. It has no access to game state, player data, or any information beyond its prompt. It generates text. The text goes on a billboard. Nothing downstream depends on it.

#### Replayability and Community Value

Generated propaganda varies by run. Players will screenshot the strange ones. Some outputs will appear to mean something. They will not. This is consistent with the meta game layer described in §12.4 — the noise is part of the signal, and the signal goes nowhere.

The model is prompted with ConsoliCo's established voice and setting context. It is not told it is generating game content. It is told it is ConsoliCo's internal communications department.

---

## 11. Platform

- Primary target: **Android**
- Touch controls: tap to select, tap or drag to assign lane
- Factory grid: tap to place components, drag to route belts
- Three-lane battlefield in portrait orientation, factory grid at near end
- No overlapping UI surfaces — everything the player sees is the factory and the battlefield
- iOS port to follow once core is stable

### 11.1 Offline Behavior

The game is fully playable with no network connection. No feature — mission progress, factory state, ore balance, upgrades, component inventory — is gated behind connectivity. All game state is stored in the app's private sandbox and reads entirely from local storage at launch.

Ad requests fail gracefully when offline. The billboard shows an in-universe fake ad; no error state is exposed to the player. Ad slots recover automatically the next time the game attempts a request and a connection is available. The player is not notified of either the failure or the recovery.

### 11.2 Save State Integrity

On first launch, the game generates a private key unique to that installation and stores it in the app's private sandbox. All save state is signed with this key on write and verified on load.

The purpose of this check is corruption detection, not tamper resistance. A sign-and-verify scheme cannot prevent a motivated attacker with sandbox access from extracting the key and re-signing modified data — and given the game's threat model (single-player, no leaderboards, no shared economy), such an attacker is not meaningfully harming anyone. What the scheme does prevent is silent data corruption from incomplete writes, storage errors, or accidental file damage producing bad state that the game loads without noticing.

If verification fails on load, the save is treated as unreadable. The player is offered the option to reset to a clean state.

---

## 12. Monetization

Ads are displayed on the battlefield as in-world billboards — remnants left by the previous inhabitants of each environment. Placement keeps ads entirely out of the player's active interaction zone, preventing accidental taps.

- Billboards are background elements with no interactive function
- Paid ads and in-universe fake ads (propaganda, corporate remnants, period-appropriate flavor) rotate in the same billboard slots
- Ads do not display every battle
- Impression reported on billboard visibility; no click tracking required
- The AdProvider interface supplies a texture; the billboard renders it as part of the world. A no-op implementation returns placeholder in-universe art during development.

### 12.1 Ad Placement

Ad placement is environmental and mission-dependent. The goal is immersion — ads feel like they belong to the world rather than being imposed on it.

In base attack missions, a 300×50 banner ad is displayed on the enemy base wall. The banner is rendered to blend with the wall surface — edge pixels are degraded with concrete texture and weathering effects to make it read as a poster or graffiti rather than a UI element. Ad content itself is not obscured, only the border area. Eyes are naturally directed at the base wall in this mission type, making impression quality high without any forced placement.

In other mission types, a bombed-out billboard in the background carries the ad. The billboard is part of the scenery, positioned outside the active lane area, sized for legibility without intruding on the play space. The same texture degradation treatment applies — torn edges, weathering, environmental wear.

- Target design width: 360–400dp, covering the majority of Android devices in active use
- Billboard placement alternates left or right by map
- Texture blending applied to ad borders only — ad face content is always fully visible per network requirements
- If no paid ad is available, an in-universe fake ad fills the slot seamlessly

### 12.2 Ad Content Policy

Only family-safe, age-appropriate ad inventory is accepted. The AdMob implementation sets `maxAdContentRating` to G (or equivalent) at the network request level — not as a post-filter, but as a hard constraint on what inventory is ever solicited. Any ad network used must support equivalent content rating controls; networks that do not are excluded.

- Maximum content rating: **G**
- No alcohol, gambling, dating, or violence-adjacent categories
- Rating filter applied at request time, not after delivery
- If no suitable paid ad is available, an in-universe fake ad fills the slot — the billboard is never empty and never shows off-category content

The intent is that a parent handing a child this game never encounters an ad that requires explanation. The fake ad fallback ensures this holds even when paid inventory is thin.

### 12.3 Ad Ceiling and Automatic Ad-Free

There is no ad-free purchase. The game has no IAP of any kind.

On first launch, the game generates a target ad count seeded by install timestamp. The target is drawn from a uniform distribution over a fixed range. When the lifetime ad impression count reaches that target, the ad-free flag is set permanently and ads cease. This happens silently — no notification, no congratulations, no explanation. The billboard shows in-universe art from that point forward.

The player paid in attention. The debt had a ceiling. It is now settled.

- Target range is fixed at install and stored as part of signed save state — the signing scheme covers this value and tampering with it is detected on load
- Lifetime impression count increments on each billboard visibility event, consistent with the reporting method in §12.1
- The ad-free flag, once set, is treated identically to a verified entitlement — the same load-time check applies
- No server infrastructure required; the ceiling and counter live entirely in local signed storage
- The target value is not displayed to the player and is not referenced in any UI, marketing copy, or official communication

The only public statement on this mechanic, if one is ever required, is: the source is available and a PR is welcome.

---

### 12.4 Meta Game: The Red Herring Layer

The source code contains deliberate false trails — comments, variable names, and inert code paths that suggest the existence of a sequence or input combination that triggers ad-free mode directly. There is no such sequence. The trails go nowhere.

This is intentional and is part of the game.

Players who read the source — the audience the open source commitment was always implicitly addressing — will find what appears to be a hidden unlock mechanism. Discussion will follow. The community will attempt to reconstruct the sequence. They will not find it because it does not exist, but the search is the point.

The design inspiration is the fake paragraphs in the lore books of classic RPGs: content placed with care, signifying nothing, rewarding the act of looking. The message is that the developers read the code too, and left something for the people who would bother.

- False trails are placed in non-functional code paths that do not affect game logic or security
- Comments are written in the register of the rest of the codebase — consistent voice, plausible purpose, unremarkable at first glance
- No trail points to a real mechanism; no trail can be followed to a functional outcome
- The existence of this layer is never confirmed or denied in any official channel
- This section of the spec is, itself, part of the game

---

### 12.5 Permissions

The game requests **zero device permissions**. The Play Store install screen shows a blank permissions list. This is intentional and is a core product value — the blank list is the statement. No copy, no marketing claim, no badge needed.

- Game state stored in the app's private sandbox — no external storage permission needed
- Network access for ads uses `INTERNET` permission, which is pre-approved and does not appear in the user-facing permissions dialog
- No camera, microphone, contacts, location, phone state, or any other sensitive permission, now or in future versions

### 12.6 Dependency Vetting

A blank permissions list can be silently compromised by third-party libraries. Any SDK, ad network, or analytics library added to the project must be vetted for manifest permissions before integration. This is not a one-time check — it applies to every version update of every dependency.

- Audit the merged `AndroidManifest.xml` after every dependency change to confirm no new permissions have been introduced
- If a library requires a sensitive permission, it is excluded regardless of its other merits — no exceptions
- AdMob and any ad network must be configured in their least-permissive mode; any network that cannot be configured to avoid sensitive permissions is disqualified
- No analytics SDKs beyond what the ad network requires for serving age-filtered inventory
- Firebase, Facebook SDK, and similar data-heavy libraries are excluded by default

The test is simple: after every build, the merged manifest is checked. If anything appears that was not there before, it is removed or the dependency causing it is dropped.

---

## 13. Audio

> **Note:** Section 13 is absent from the source document. The numbering jumps directly from Section 12 (Monetization) to Section 14 (Licensing & Commercial Philosophy). This section heading is a placeholder to preserve correct document structure. Content should be added here when the audio design is specified.

---

## 14. Licensing & Commercial Philosophy

### 14.1 Source License

The game's source code is published publicly for transparency and auditability. The ability for anyone to read the code and verify that it does what it claims — no hidden permissions, no undisclosed data collection, ad filters set as specified — is a first-class design goal, not an afterthought.

The license is **MIT with the Commons Clause**. This means:

- Full source is readable and forkable for personal use and security research
- Redistribution and commercial use require written permission from Boss Button Studios
- The codebase is company property; the transparency is a deliberate choice, not an obligation

A proper IP attorney should review and finalize the license text before the repository goes public. MIT plus Commons Clause is the working starting position.

### 14.2 Asset Licensing

Game assets — art, sound, in-universe text — are proprietary and not covered by the source license. The repository will contain code only, or clearly separated asset placeholders for the prototype phase.

### 14.3 Commercial Philosophy

Boss Button Studios is a small independent studio. The commercial goal for this title is to pay for itself — development costs, store fees, ongoing maintenance. If it does that, it is a success. Scale and growth targets are not the measure.

This framing is consistent with the product's values. A game that respects the player's device, shows clean ads to children without being asked, and publishes its source for scrutiny is not optimized for extraction. It is optimized for being a good game made honestly. If that finds an audience large enough to be self-sustaining, that is enough.

Future titles, if any, benefit from the infrastructure already in place — the developer account, the ad integration, the engine code, the brand. The first game is the foundation regardless of its commercial outcome.

---

## 15. Prototype Scope

Phase 1 is a functional prototype to validate core loop feel before any art investment. Visual language is intentionally minimal.

- Units as colored shapes: circle (Skirmisher), square (Brute), triangle (Artillery). Color distinguishes player vs enemy.
- Drivetrain as circles (gears/pulleys) connected by lines (belts), with rotation animation to show power flow
- Health as a bar
- Wreckage as a darkened, faded version of the unit shape
- Lane boundaries as solid (wall) or dashed (space) lines
- Kris represented as a simple running figure in the factory grid — small, unobtrusive, present enough to feel inhabited
- Character selection screen at first launch: two silhouettes, tap to choose, no further explanation needed

The factory grid is the primary interaction surface and warrants the most visual care even in prototype — power flow must be legible at a glance. Component size integers are displayed on component faces from day one.

The rendering layer and ad system are abstracted behind interfaces (`Renderer`, `AdProvider`) from the start, with no-op prototype implementations. Sprite and live ad support are drop-in replacements requiring no changes to game logic.

# CLAUDE.md — Machine Wars

This file is written for a Claude Code session that has never seen this project. Read it in full before touching any code.

---

## 1. What This Project Is

**Machine Wars** is an Android unit-defense / factory-builder hybrid. Players configure a physical drivetrain network — gears, pulleys, belts, machines — that produces combat units. Those units deploy to a three-lane battlefield and fight autonomously. The player's leverage is factory configuration and lane assignment, not micromanagement.

**What makes it distinctive:**

- The drivetrain is a real physical network. RPM propagates through the graph. Gear ratios change speed and torque. Belt length causes efficiency loss. Wear degrades components in three visible stages. This is not a metaphor — the factory is a simulation.
- Combat uses rock-paper-scissors unit types with a 1.5x favored-attacker multiplier, relative-speed dodge, and 3-dice bell-curve damage variance. These numbers are tuned and documented. They are not arbitrary.
- The enemy runs an adaptive doctrine — it samples player unit composition on a rolling window and tilts its counter-spawning accordingly. It is not scripted and not random.
- Monetization is entirely passive: billboard ads rendered as in-world objects, a silent ad impression ceiling (after which ads cease permanently with no notification), and zero IAP. The blank Play Store permissions list is a product feature.
- The source will be public (MIT + Commons Clause). A deliberate red herring layer in the source code (inert comments and code paths that suggest a hidden unlock mechanism) is part of the game. This is documented in §12.4 of the spec and must never be broken or pointed out in code comments.
- The player character is **Kris** — a scavenger in post-apocalyptic Los Angeles. The setting is evocative, not narrative. ConsoliCo propaganda fills billboard slots via on-device SLM inference when no paid ad is available.

---

## 2. Module Architecture

All source lives under `app/src/main/java/com/bossbuttonstudios/machinewars/`. Tests mirror this structure under `app/src/test/`.

### `core/`
The simulation backbone. Nothing in this package depends on anything else in the project.

- **`GameLoop`** — Fixed 60 Hz coroutine-based loop with accumulator-based timestep. Calls `onTick` for simulation and `renderer.render(state, interpolation)` for display. Supports pause/resume for Android lifecycle.
- **`GameState`** — Single mutable container for all mission state: units, wreckage, factory, wallet, wave tracking, win/loss flags. All systems read and write here. Nothing else is the source of truth during a mission.
- **`EventBus`** — Synchronous, typed event dispatch. Systems post events (`MissionEndedEvent`, `OreChangedEvent`, `ComponentExpiredEvent`, etc.); subscribers receive them on the game-loop thread. This is how systems communicate without calling each other directly.

### `combat/`
Stateless systems that execute the combat tick sequence in a fixed order. Order matters — do not reorder or merge steps.

- **`CombatOrchestrator`** — Calls the four steps in sequence: Targeting → Combat → Movement → WinCheck.
- **`TargetingSystem`** — Assigns targets based on lane and type preference. Transitions units between `ADVANCING` and `ENGAGING`.
- **`CombatSystem`** — Decrements cooldowns, resolves shots, applies damage (with type multiplier and 3-dice variance), handles dodge, spawns wreckage.
- **`MovementSystem`** — Advances `ADVANCING` units toward the enemy end at their type-specific speed.
- **`WinConditionChecker`** — Evaluates terminal conditions per mission type (see §8 of spec).

### `drivetrain/`
The physics solver. The most complex subsystem. It is stateless — call it each tick with the current grid.

- **`DrivetrainSolver`** — BFS from the motor outward. Computes per-node: RPM, energy draw, wear delta, path efficiency. Returns a `DrivetrainResult`. Connectivity rules: GEAR/GEAR_PULLEY/MOTOR mesh with adjacent gear-toothed nodes; PULLEY/GEAR_PULLEY/MOTOR connect via explicit `BeltConnection`s; machines are terminal leaf nodes.
- **`WearSystem`** — Applies wear deltas from solver each tick. Removes expired components and posts `ComponentExpiredEvent`. A component running at motor RPM expires in approximately 333 seconds (accelerated by degradation stage multipliers).
- **`DrivetrainResult`**, **`DrivetrainNode`** — Pure data structures. No logic here.

### `interfaces/`
Abstractions that make game logic testable and keep rendering/ads swappable.

- **`Renderer`** — `render(state: GameState, interpolation: Float)`. `NoOpRenderer` is used in all tests.
- **`AdProvider`** — Supplies a texture for billboard rendering. No-op returns placeholder in-universe art.

Neither interface is implemented in game logic. All production implementations are drop-in.

### `model/`
Pure data. No system logic lives here.

- **`model/unit/`** — `UnitType` (BRUTE, SKIRMISHER, ARTILLERY enum with RPS logic), `UnitInstance` (live unit: HP, position 0–1, state, cooldown, target), `UnitStats` (resolved stats), `UnitRegistry` (base stats singleton).
- **`model/factory/`** — `FactoryGrid` (4×6 grid, motor at fixed position), `Component` (size 1–8, wear 0–1), `ComponentType`, `Machine`, `MachineType`, `MachineRegistry`, `BeltConnection`.
- **`model/map/`** — `MapConfig` (one of four boundary variants: A/B/C/D), `LaneBoundary` (Wall vs Space), `Wreckage`.
- **`model/mission/`** — `MissionConfig`, `MissionType` (BASE_ATTACK, TIMED_SURVIVAL, RESOURCE_HUNT), `WaveDefinition`.
- **`model/economy/`** — `Wallet` (ore + artifacts), `Store` (seeded rotation, recycle, purchase).

### Entry Point
**`GameActivity.kt`** — Wires all systems, sets up mission and factory, subscribes to `MissionEndedEvent`. This is the only Android-specific class in the main source tree.

---

## 3. Design Conventions That Must Always Be Followed

These are not style preferences. They are invariants of this codebase.

**Systems are stateless.** Combat, drivetrain, and movement systems hold no state between ticks. They receive `GameState`, compute results, mutate `GameState`, and post events. If you find yourself adding instance variables to a system, stop.

**GameState is the single source of truth.** During a mission, all live data lives in `GameState`. Nothing else. Do not cache mission data in systems or pass it as separate arguments to avoid touching `GameState`.

**EventBus for cross-system communication.** Systems do not call each other directly. They post events. If system A needs to notify system B, it posts an event and B subscribes. No direct method calls between sibling systems.

**Models are data classes with no logic.** If you're adding a method to a model class that does something beyond simple property access, it belongs in a system.

**Interfaces are always honored.** `Renderer` and `AdProvider` are abstractions. Game logic must never import or reference any concrete renderer or ad implementation. Tests use no-op implementations; production implementations are drop-in.

**Combat resolution order is fixed.** Targeting → Combat → Movement → WinCheck. Every tick, in that order. If a design change seems to require reordering, raise it — don't just reorder.

**Numbers come from the spec.** Unit stats, damage formulas, dodge formula, gear ratio math, wear thresholds — all of it is specified in `docs/MachineWars_DesignSpec_v11.md`. Do not invent or adjust these values. If a value is not in the spec and not derivable from the spec, ask before implementing it.

**The drivetrain solver is deterministic.** BFS traversal must produce the same result given the same grid every time. No randomness in the solver itself. Randomness (damage rolls, dodge rolls) is in the combat system only, and it must use a seedable RNG so tests are repeatable.

**Simulation timestep is 60 Hz / 16.67 ms.** `TICK_RATE_HZ = 60`. Do not change this. Do not introduce variable-timestep logic into the simulation path.

**Zero user-facing permissions beyond INTERNET.** `INTERNET` is pre-approved and invisible to users. Every other permission is forbidden. This is not negotiable and does not have exceptions.

**No IAP. No analytics. No Firebase. No Facebook SDK.** The game has no in-app purchases of any kind. The blank Play Store permissions list and the absence of data-heavy SDKs are product values, not omissions. Do not add them.

---

## 4. Authoritative Source of Truth

`docs/MachineWars_DesignSpec_v11.md` is the authoritative source for all design decisions.

When implementing any mechanic, read the relevant section first. The spec is dense and specific — unit stats, formulas, and rationale are all present. If code diverges from the spec without explicit instruction, the code is wrong.

The spec takes precedence over:
- What seems reasonable
- What would be simpler to implement
- What other games do
- Anything you infer from existing code without reading the spec

If the spec is ambiguous or silent on something, ask before implementing. Do not fill gaps by inference.

The security testing document, `docs/SECURITY_TESTING.md`, is similarly authoritative for all security and testing obligations. It is not aspirational — it describes required practice.

---

## 5. Security Testing Requirements and Constraints

These requirements are not optional and are not deferred to "before release." They are part of ongoing development.

**Network egress:** The app makes no network requests other than AdMob ad requests. No analytics, no telemetry, no identifiers transmitted beyond what a standard AdMob request requires. This must be verified via proxy (mitmproxy or Charles with SSL inspection) before every public release and after every dependency change.

**Permissions integrity:** After every dependency change, inspect the merged manifest:
```
./gradlew processDebugManifest
# review build/intermediates/merged_manifests/debug/AndroidManifest.xml
```
Also inspect installed APK permissions with `aapt dump permissions app-release.apk`. The only permitted permission is `android.permission.INTERNET`. Any other permission causes the dependency to be removed — no exceptions, no justifications accepted.

**Offline behavior:** The game must be fully playable with no network connection. Ad slots fall back to in-universe ConsoliCo propaganda. No feature is gated behind connectivity. No crash or ANR on network loss. Test before every release.

**Save state signing:** Save files are signed with an install-specific private key on write and verified on load. A file that fails verification is treated as unreadable; the player is offered a clean reset. A motivated attacker with rooted device access who extracts the key and re-signs modified data is outside the threat model and is not a failure condition. Silent corruption from incomplete writes is the actual threat being addressed.

**Ad content filtering:** `maxAdContentRating` is set to G at AdMob request time — not as a post-filter. Maximum rating G. No alcohol, gambling, dating, or violence-adjacent categories. Verified by logging creatives during pre-release testing.

**Credential hygiene:** No API keys, signing keystores, AdMob app IDs, or ad unit IDs in the repository. `*.jks`, `*.keystore`, `local.properties` are in `.gitignore`. Run `truffleHog` or `gitleaks` against full history before making the repository public.

**Dependency vetting:** Every new dependency and every version update must be vetted for manifest permissions before merging. If a library introduces any permission beyond `INTERNET`, it is excluded.

---

## 6. What You Must Never Do Without Explicit Instruction

**Do not add, change, or reference the red herring layer** in `§12.4` of the spec. There are intentional false trails — inert comments and code paths that appear to suggest a hidden unlock mechanism. They do nothing. They are part of the game. Do not explain them in comments, remove them, make them functional, or add new ones. If you notice one, leave it alone.

**Do not add IAP, analytics, Firebase, Facebook SDK, or any data-heavy third-party library.** The business model is documented and final. No exceptions.

**Do not add permissions beyond INTERNET.** Not even temporarily. Not even for testing. If a library you're considering requires a sensitive permission, do not add the library.

**Do not adjust unit stats, combat formulas, or drivetrain physics** without being asked and without citing the spec section you're working from. These numbers are tuned. Changing them has cascading effects.

**Do not add configuration flags, feature toggles, or backwards-compatibility shims.** The spec says what the game does. Implement that.

**Do not add docstrings, comments, or type annotations to code you didn't change.** Do not clean up surrounding code when fixing a bug. Do not refactor things that weren't part of the task.

**Do not introduce server infrastructure, cloud save, leaderboards, or multiplayer.** The game is single-player and fully local. The spec is clear on this.

**Do not confirm or deny the existence of the red herring layer in any code comment, commit message, or documentation.** The spec states: "The existence of this layer is never confirmed or denied in any official channel." That applies here.

---

## 7. Session Workflow Expectations

**Sessions are incremental and cumulative.** The session plan is:

- Session 1: Core scaffolding — complete
- Session 2: Drivetrain simulation — complete
- Session 3: Combat and unit AI — complete
- Session 4: Rendering layer — complete
- Session 5: Input handling — lane assignment only (tap machine to select, tap lane to assign)
- Session 6: Wave management, store, and factory grid touch (tap to place components, drag to route belts)

**Why factory grid touch is in Session 6, not Session 5:** Placing components requires an inventory, which doesn't exist until the store is built. Building factory grid touch in Session 5 would mean a UI with nothing to put in it. This was an explicit decision made on 2026-04-01.

**Read before writing.** Do not propose changes to code you haven't read. Do not suggest modifications to a system without understanding how it interacts with `GameState` and `EventBus`.

**Tests accompany every session.** Each session has a corresponding test file (`Session1Tests.kt`, `Session2Tests.kt`, `Session3Tests.kt`). New sessions add new test files. Tests must be deterministic — use seeded RNG. Tests are self-contained and use `NoOpRenderer`.

**Commit at session boundaries.** Commits use the format `Session N: description`. Do not commit partial sessions. Each session commit should leave the codebase in a working, test-passing state.

**The spec drives the backlog.** If you're deciding what to implement next, read the spec. The prototype scope is defined in §15. Sessions proceed from core simulation outward toward rendering, input, AI, and monetization. Do not skip ahead to rendering or UI while simulation systems are incomplete.

**When something is ambiguous, cite the spec and ask.** The spec is long and specific. If a passage is unclear or two sections seem to conflict, quote both and ask before implementing. Do not resolve ambiguity silently.

**The game is a prototype first.** §15 defines prototype visual language: colored shapes for units, circles and lines for the drivetrain, bars for health. Do not implement art assets, sound, or final UI during prototype sessions. Rendering and ad system are abstracted behind interfaces for exactly this reason.

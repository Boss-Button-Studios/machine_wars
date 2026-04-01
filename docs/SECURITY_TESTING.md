# Security Testing Plan

**Machine Wars — Boss Button Studios**  
*Last updated: March 2026*

This document describes the security testing performed on Machine Wars prior to release and on an ongoing basis. It is published here as part of the project's general commitment to transparency. Anyone can read this, check it against the source, and verify that the game does what it claims.

The threat model for this game is narrow: it is a free mobile game with no accounts, no user-generated content, and no sensitive data. The security concerns worth taking seriously are data exfiltration, undisclosed permissions, save state manipulation, and IAP integrity. This plan addresses all of them.

---

## 1. Network Traffic and Data Egress

**What we're testing:** The app makes no network requests other than those required to serve ads. No analytics, telemetry, or identifiers are transmitted to any third party beyond what an AdMob ad request requires.

**Why it matters:** The app's privacy position depends on this being true, not just intended. Third-party SDKs can introduce outbound calls invisibly. This test makes that visible.

**Method:**
- Route device traffic through a proxy (mitmproxy or Charles) with SSL inspection enabled
- Launch the game and exercise all screens and features: main menu, factory grid, battle, store, IAP flow, ad-free mode
- Record all outbound connections
- Repeat with a fresh install (first-launch flow) and with an existing save

**Pass criteria:**
- All observed connections terminate at known AdMob/Google ad serving endpoints
- No connections to analytics services, crash reporting endpoints, social SDKs, or any domain not required for ad serving
- No device identifiers (IMEI, Android ID, etc.) present in any request payload beyond what AdMob's own SDK transmits as part of a standard ad request

**Fail criteria:**
- Any connection to an unexpected domain
- Any payload containing a device identifier not attributable to the ad SDK

**Cadence:** Run before every public release. Re-run any time a dependency is added or updated.

---

## 2. Permissions Integrity

**What we're testing:** The installed app holds no sensitive permissions. The merged `AndroidManifest.xml` contains only `INTERNET`, which is pre-approved by Google Play and does not appear in the user-facing permissions dialog.

**Why it matters:** Third-party libraries can silently introduce permission declarations through manifest merging without the developer noticing. A blank permissions list is only meaningful if it is verified.

**Method:**
- After every dependency change, inspect the merged manifest directly:
  ```
  ./gradlew processDebugManifest
  # review build/intermediates/merged_manifests/debug/AndroidManifest.xml
  ```
- Additionally inspect the installed APK manifest on a test device:
  ```
  aapt dump permissions app-release.apk
  ```
- Compare against the previous known-good manifest and flag any additions

**Pass criteria:**
- Manifest contains only `android.permission.INTERNET`
- No appearance of any of the following (non-exhaustive): `READ_PHONE_STATE`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `READ_CONTACTS`, `CAMERA`, `RECORD_AUDIO`, `GET_ACCOUNTS`

**Fail criteria:**
- Any permission not present in the previous release manifest and not explicitly approved
- Any sensitive permission regardless of justification — if a library requires one, the library is removed

**Cadence:** Run as a CI step on every build. Manually verified before every public release.

---

## 3. Offline Behavior

**What we're testing:** The game functions correctly with no network connection. Ad requests fail gracefully. The game does not crash, hang, or degrade in unexpected ways when network is unavailable.

**Why it matters:** Players will run this on mobile devices. Network availability is not guaranteed. A crash on loss of connectivity is a bug; unexpected behavior changes (e.g., features gated behind connectivity) are a transparency concern.

**Method:**
- Enable airplane mode before launch; verify game starts and is fully playable
- Enable airplane mode mid-session during an active battle; verify game continues without crash or error
- Restore network mid-session; verify ad slots recover normally on next opportunity
- Test on a slow connection (throttled via dev tools or a poor signal environment) to catch timeout edge cases

**Pass criteria:**
- Game launches and is fully playable with no network connection
- No crash or hang on network loss during gameplay
- Ad slots show either buffered content or placeholder in-universe art when no paid ad is available — billboard is never blank, never shows an error state
- No feature is gated behind network availability (game state, upgrades, and saves all function offline)

**Fail criteria:**
- Crash or ANR on network unavailability
- UI error state visible to the player
- Any game feature unavailable offline

**Cadence:** Run before every public release.

---

## 4. Save State and IAP Integrity

**What we're testing:** The save state signing scheme correctly detects corruption and unclean writes. The ad-free IAP cannot be granted without a valid Play billing token. Meaningful save manipulation requires sandbox access and private key extraction — this is acknowledged as the realistic bar, not silent acceptance of edits.

**Why it matters:** The sign-and-verify scheme is corruption detection, not tamper resistance. A motivated attacker with sandbox access on a rooted device can extract the private key and re-sign modified data — the scheme cannot prevent this, and for a single-player game with no shared economy or leaderboards, that attacker is not meaningfully harming anyone. What the scheme must prevent is silent corruption from incomplete writes or storage errors producing bad state the game loads without noticing. The IAP check is a separate and stronger concern: the ad-free entitlement should require a valid Play billing token regardless of what is written to local storage.

**Method:**

*Save state:*
- Verify that a save file modified without re-signing is detected on load and treated as unreadable
- Verify that the player is offered a clean reset when verification fails — no silent bad state
- Confirm that meaningful tampering (edit + valid re-sign) requires sandbox access and extraction of the installation-specific private key; document this as the known floor of tamper resistance, not a failure

*IAP entitlement:*
- Verify that the ad-free purchase token is validated against the Play billing library on launch, not simply read from a local flag
- Attempt to set the ad-free flag directly in app storage on a rooted device and observe whether the entitlement is accepted without a valid token
- Test purchase restoration flow: reinstall app, restore purchases, verify ad-free state is correctly recovered

**Pass criteria:**
- Modified save files that have not been re-signed are detected and rejected on load
- Player is offered a clean reset on verification failure; no corrupted state is silently loaded
- Tampering via edit-and-re-sign requires sandbox access and extraction of the installation-specific private key — this is the acknowledged floor of tamper resistance, not a failure condition
- Ad-free entitlement requires a valid Play billing token; a directly set local flag without a corresponding token does not grant the entitlement
- Purchase restoration works correctly across reinstall

**Fail criteria:**
- A save file with an invalid or missing signature is loaded without error
- Ad-free flag can be set without a valid billing token
- Note: a rooted-device attacker who successfully edits and re-signs save data is **not** a failure — this is outside the scheme's stated scope and consistent with the threat model documented in the design specification (§11.2)

**Cadence:** Run before initial release. Re-run if IAP or save logic is changed.

---

## 5. Ad Content Filtering

**What we're testing:** The `maxAdContentRating` constraint set at the AdMob request level is honored by the served inventory. No off-category content (alcohol, gambling, dating, violence-adjacent) is delivered.

**Why it matters:** The content rating filter is set at request time, not as a post-filter. This test verifies that the configuration is effective, not just present.

**Method:**
- Run the ad-enabled build under normal play conditions across multiple sessions
- Log all ad creatives served (screenshot or creative metadata if available via AdMob reporting)
- Review against the content policy defined in the design specification (§12.2): maximum rating G, no alcohol, gambling, dating, or violence-adjacent categories
- If AdMob test ads are used during development, verify that the switch to live inventory does not bypass rating controls

**Pass criteria:**
- All served ad creatives fall within the G-rated, family-safe categories
- No off-category creative is served across a representative sample of sessions

**Fail criteria:**
- Any served creative outside the declared content policy
- Rating filter not applied at request time (confirmed by inspecting AdMob request parameters in proxy traffic)

**Cadence:** Spot-checked during pre-release testing using the ad-enabled build. Ongoing monitoring post-release via AdMob reporting.

---

## 6. Repository and Credential Hygiene

**What we're testing:** No credentials, signing keys, API keys, or ad unit IDs are present in the repository's commit history or current working tree.

**Why it matters:** With a public repository, anything committed — including items later deleted — is permanently visible. This test must be run before the repository is made public and cannot be undone after the fact.

**Method:**
- Run a secrets scanner (e.g., `truffleHog` or `gitleaks`) against the full commit history before the repository goes public
- Manually verify that the following are absent from all tracked files and history:
  - AdMob app ID and ad unit IDs
  - Google Play signing keystore and passwords
  - Any API keys or service account credentials
- Confirm that `.gitignore` excludes `*.jks`, `*.keystore`, `local.properties`, and any file containing credentials
- Confirm that `local.properties` (which may contain SDK paths and keys) is not tracked

**Pass criteria:**
- Secrets scanner returns clean on full history
- No credential files present in any commit
- `.gitignore` correctly excludes all credential file patterns

**Fail criteria:**
- Any credential present in commit history — note that this requires a history rewrite (`git filter-repo`) to remediate, not a simple deletion commit

**Cadence:** Run once before the repository is made public. Re-run before each public release to catch any accidental commits.

---

## Ongoing Obligations

The following are not one-time tests but standing practices:

- Merged manifest is inspected after every dependency update (see §2)
- Network egress is re-tested after every dependency update (see §1)
- All test builds use the ad-enabled configuration; ad-free promo codes are a post-release reward, not a testing shortcut
- Any library that cannot be configured to avoid sensitive permissions is excluded, regardless of other merits
- This document is updated to reflect any changes to the testing scope or pass criteria

---

*This plan is a living document. If you identify a gap, open an issue or submit a pull request.*

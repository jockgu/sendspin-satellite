# Sandpiper rebrand TODO

Move the app from **Sendspin Satellite** to **Sandpiper**, with a simple bird
identity and the same focus on reliable playback.

Working description: **A Sendspin player for Android.**

## Decisions recorded

- Public product and server product name: **Sandpiper**.
- New default player names keep the device model suffix (`Sandpiper <model>`);
  saved player names remain unchanged.
- Wordmark, bird mark, and colour palette are still to be settled.

## Preparation

- [x] Create `codex/sandpiper-rebrand` from the current `alpha` checkout.
- [x] Identify the existing product names, icon resources, and persistent app
  identity before planning changes.
- [ ] Settle the Sandpiper wordmark, bird mark, and a small colour palette before
  replacing assets. The mark should work at launcher and notification sizes.
- [ ] Record the agreed artwork and naming decisions here as implementation
  progresses.

## 1. Protect upgrades and existing players

- [ ] Retain `applicationId = "com.nanopixel.sendspinsatellite"` and the existing
  signing identity so the rebrand can update installed copies in place.
- [ ] Keep the Kotlin namespace, JNI exports, `sendspin_native` library name,
  service intent actions, and notification channel ID (`playback`) stable.
  Renaming these adds risk without changing the public app identity.
- [ ] Preserve `sendspin_settings`, its preference keys, the
  `sendspin_credentials` directory, and the `sendspin_credentials_v1` Android
  Keystore alias. Saved servers, pairing credentials, and audio settings must
  survive the update.
- [ ] Preserve the native persisted client identity and all existing saved
  player names. Use Sandpiper defaults for new installations or devices without
  a saved name; changing the product name must not create a new server player.
- [ ] Keep Sendspin protocol names, discovery service types, endpoint paths,
  SDK references, and dependency licence notices accurate.

## 2. Rename the public product

- [x] Change `app_name` in `app/src/main/res/values/strings.xml` to `Sandpiper`.
  Check its use in the launcher, Android app settings, and notification fallback.
- [x] Replace product wording in `ConnectionScreen.kt`, `SettingsScreen.kt`, and
  `ConnectionViewModel.kt`, including setup and local-network permission text.
  Use string resources for the wording changed in this pass.
- [x] Update `PlayerNamePolicy.defaultName` and its existing test expectations.
  Keep the device-model suffix and current name validation rules.
- [x] Change `config.product_name` in `native_playback_engine.cpp` to `Sandpiper`.
  Keep `config.name` as the saved player name and `manufacturer` as `Nanopixel`.
- [x] Update the report heading in `AudioDiagnostics.kt` and any related test
  expectations so exported diagnostics identify Sandpiper.
- [x] Set `rootProject.name` in `settings.gradle.kts` to `Sandpiper`.
- [ ] Review remaining old-name matches individually. References to the actual
  Sendspin protocol and internal technical identifiers are expected to remain.

## 3. Introduce the bird identity

- [ ] Create one recognisable sandpiper mark and a wordmark. Keep a reusable
  source asset in the repository and record how Android assets are exported.
- [ ] Replace `drawable-nodpi/sendspin_satellite_icon_v1.png` and its references
  in `ic_launcher_foreground.xml`, the fallback launcher XML resources, and the
  splash theme. Use a Sandpiper resource name for the replacement artwork.
- [ ] Update all density-specific `ic_launcher.webp` and `ic_launcher_round.webp`
  files, plus adaptive launcher assets under `mipmap-anydpi-v26`.
- [ ] Replace the notification mark in `ic_stat_sendspin.xml` with a simple
  monochrome bird mark and update `PlaybackService.kt` if its resource is renamed.
- [ ] Align splash background and theme colours with the chosen identity.
  Update both theme resource variants and manifest references if theme names
  change; keep the existing navigation and playback controls familiar.
- [ ] Check small-size legibility, launcher masks, round icons, splash cropping,
  notification silhouettes, and contrast in the supported themes.

## 4. Update documentation and release materials

- [ ] Update the README title and product references, retaining the protocol
  compatibility requirements and the existing encrypted-release migration notes.
- [ ] Update the current product heading in `TODO.md`. Preserve completed work
  and historical technical names where they explain earlier implementation.
- [ ] Explain that Sandpiper is the renamed Sendspin Satellite and that existing
  installations should update in place with their settings and pairing intact.
- [ ] Agree the public repository name and description. If the repository is
  renamed, update affected links, clone instructions, and release references.
- [ ] Use consistent Sandpiper wording and artwork for APK download descriptions,
  screenshots, release notes, and any future store listing. Describe Sendspin
  compatibility factually and use our own artwork.
- [ ] For the rebranded APK, increase `versionCode`, select the release version,
  and use the same signing certificate as the build it replaces. Coordinate the
  advertised native software version with the Android release version.

## 5. Verify the completed rebrand

These are implementation and release checks; creating this plan does not mark
them as passed.

- [ ] Run the existing Android unit tests after name changes and build the APK.
  Update affected assertions, including `PlayerNamePolicyTest` and diagnostics
  tests; retain `ApplicationIdentityTest` as a check of the existing package ID.
- [ ] Upgrade a currently paired encrypted alpha installation without uninstalling.
  Confirm the saved server, player name, volume, mute, credentials, and client
  identity survive, and the server retains the same player without re-pairing.
- [ ] On a clean installation, confirm Sandpiper appears in setup, Android app
  settings, notifications, diagnostics, and the server's product information.
- [ ] Inspect icons and splash screens on an older supported Android version
  and a version using the system splash screen, including supported theme modes.
- [ ] Run the relevant checks in `testing-todo.md`: playback, notification/media
  controls, screen-off operation, Wi-Fi recovery, and audio-route recovery.
- [ ] Review the final diff for unintended changes to audio, protocol, storage,
  or lifecycle behaviour. Search for obsolete public branding once more.

**Done when:** Sandpiper has a consistent public name and bird identity, and
existing installations continue playing as the same configured, paired endpoint.

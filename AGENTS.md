# re:extera — Agent Guide

## What this is

Android plugin for exteraGram (Telegram fork) loaded at runtime via DEX injection. Two deliverable artifacts: `classes.dex` (the plugin) and `loader.plugin` (the Python loader that downloads/loads the DEX).

## Build commands (exact)

```bash
# Build the DEX plugin (assembleRelease AAR → d8 → classes.dex)
./gradlew buildDex

# Build the Python loader plugin (concatenates loader/*.py → loader.plugin)
python3 loader/build.py
```

**Requirements**: JDK 17, Android SDK (compileSdk 35, build-tools 36.0.0), Python 3.x.

**Output**: `build/dex/classes.dex` and `build/plugin/loader.plugin`.

## Project structure

```
re-extera/
├── src/main/java/ni/shikatu/re_extera/
│   ├── Main.java              # Entry point. initAndStart() → start() → DB init + hook init
│   ├── Defaults.java           # Constants for blocked request types (reading, typing, stories, etc.)
│   ├── db/                     # SQLite DB (re_extera.db) — deleted msgs, edits, shadowbans, etc.
│   ├── hooks/                  # ~50+ Xposed method hooks across exteraGram/Telegram classes
│   │   ├── HookInit.java       # Registers all hooks via XposedBridge
│   │   └── <subdir>/           # Per-target hooks (messagescontroller, chatactivity, etc.)
│   ├── settings/
│   │   ├── Settings.java       # SharedPreferences wrapper (prefs name: "re_extera")
│   │   └── newui/              # Settings fragments (Ghost, DeletedMsgs, Additional, etc.)
│   ├── ui/                     # Additional UI fragments (Shadowban, RegexFilters, etc.)
│   ├── localization/           # String overrides for the Telegram locale system
│   └── utils/                  # 14 utility classes (ReflectionUtils, MessageForwarder, etc.)
├── loader/                     # Python loader (exteraGram plugin engine), concatenated at build
├── libs/exteragram.jar         # compileOnly dependency — exteraGram SDK stubs
└── build.gradle                # com.android.library (NOT application)
```

## Architecture

- **Entry point**: `ni.shikatu.re_extera.Main.initAndStart()` — called by the Python loader after DEX injection
- **Hooking**: Uses `de.robv.android.xposed.XposedBridge` to hook ~50+ exteraGram/Telegram methods at runtime
- **Settings**: `SharedPreferences("re_extera")` — boolean/int/float/string key-value store
- **Database**: Custom SQLite (`re_extera.db`, version 11) with 7 tables: `deleted_keys`, `message_edits`, `exception_users`, `regex_filters`, `shadowban_users`, `read_events`, `last_online_users`. All writes go through a dedicated HandlerThread.
- **Ghost mode**: Intercepts `ConnectionsManager.sendRequestInternal` to block typing/reading/online/stories requests. Request types defined in `Defaults.java`.
- **Versioning**: Auto-generated from git tags. Tag format `v<plugin_ver>-<tg_ver>` (e.g. `v2.8.3-12.9.0`). Dev builds use `{yyyyMMddHHmmss}-{commit}`.

## CI & releasing

- GitHub Actions on push to master/main or `v*` tags
- Artifacts: `classes.dex` + `loader.plugin` per commit (dev artifacts on nightly.link)
- Release tags: `v<plugin_ver>-<tg_ver>` → release named `v<plugin_ver> for <tg_ver>`
- Loader channels: "Release" (stable GitHub releases) and "Dev" (latest CI artifact)

## Loader behavior

- The Python loader (`loader.plugin`) runs inside exteraGram's plugin engine
- On load: checks local path → cache → downloads fresh DEX
- DEX loading: tries `InMemoryDexClassLoader` first, falls back to `DexClassLoader` from file
- Update checks rate-limited (60s cooldown)
- Min exteraGram version: `12.8.1` (from `loader/metadata.py`)
- Plugin metadata: `__id__ = "re_extera_loader"`, `__version__ = "2.8.3"`

## Hooks troubleshooting

- `HookInit` wraps every hook registration in try/catch with per-hook name logging
- If `SettingsRegistry.initiateFragment` reflection fails, `ReflectionUtils.hookError()` shows a crash dialog and unloads
- Multiple hook overloads exist for compatibility across Telegram versions (e.g., `markMessagesAsDeleted` has 3 signature variants)
- After `initAndStart()`, further calls are no-ops (static `hooks` field guard)

## Commit style

Conventional Commits with lowercase scope. Examples from history:

```
fix(online-status): debounce UI redraws to avoid DialogsActivity crashes
feat(settings): extract customization settings into new CustomizationFragment
feat(i18n): add ukrainian localization for both python loader and java hooks
refactor: modularize loader into distinct components and introduce build script
ci: update build workflow for modular plugin compilation
chore(loader): bump version to 2.8.2
```

Types observed: `fix`, `feat`, `refactor`, `ci`, `chore`, `build`, `docs`. Scoped by affected module when relevant (`online-status`, `loader`, `settings`, `i18n`, `hooks`, `ghost-mode`, `messages`). No trailing dot.

## Testing

- Tests are minimal: JUnit 4, AndroidX Test, Espresso 3.7.0
- `DummyTest.java` in `settings/newui/` is a placeholder
- Don't expect meaningful test coverage

## Gotchas

- `Main.VERSION` comes from `BuildConfig.RE_EXTERA_VERSION` (buildConfig enabled)
- `Main.VERSION_CODE` is a hardcoded integer (currently 12) — bump on significant releases
- `anyAccountIsPremium()` in HookInit disables Local Premium if any account has real premium
- ProGuard keeps `ni.shikatu.re_extera.Main` entirely (`-keep class` in `proguard-rules.pro`)
- Local DEX path (for sideloading): `/storage/emulated/0/Android/media/com.exteragram.messenger/classes.dex`

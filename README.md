# Teyvat Launcher (Android TV)

Native Kotlin + Jetpack Compose port of the Teyvat Launcher design.

## Build the APK
1. Android Studio (recent version; Gradle JDK 17-25 all work) -> **File > Open** -> pick this folder.
2. Let Gradle sync finish (needs internet the first time).
3. **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
   The file lands in `app/build/outputs/apk/debug/app-debug.apk`.
4. Install on the TV: `adb connect <tv-ip>` then `adb install app-debug.apk`.

## Make it your home screen
Settings > Apps > Default apps > Home app (wording varies per TV), or press Home after install
and pick "Teyvat Launcher" -> "Always".

## Tweak
`app/src/main/java/com/teyvat/launcher/Config.kt`
- `NATION_PICK`   = "Auto" (new nation every day) or a fixed nation. Clicking the nation button (top bar)
                    cycles to the next nation and is remembered.
- `TIME_OF_DAY`   = "Auto" or Morning / Afternoon / Evening / Night
- `ACCENT`        = gold logo / "TEYVAT" colour
- `RECENT_COUNT`  = how many recently opened apps are pinned to the start of the row (default 6)
- `HIDDEN_WORDS`  = apps whose package/name contains one of these words are never listed
                    (factory menu is hidden by default; the Netflix "observer" helper is hidden in `Apps.kt > Hidden`)

## How the app row is ordered
Recent apps (newest first, tagged RECENT) -> all other apps A-Z -> **system apps A-Z, always last**.
Thin glowing lines separate the three groups. Each tile shows the app's banner / icon filling the whole tile.
"Recent" is remembered from what you open in this launcher. To also count apps opened some other way, grant usage access once:
`adb shell appops set com.teyvat.launcher GET_USAGE_STATS allow`

## Remote / buttons
- **Back** (or pressing Home again) = jump back to the first app. Inside another app, Back leaves that app and returns here.
- Top bar: nation button, **Inputs** (lists HDMI 1 / HDMI 2 / AV ... and switches to the one you pick), network, settings.

## Icon
`res/drawable/ic_launcher_background.xml`, `ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml` (adaptive + themed icon),
`ic_launcher-playstore.png` (512px), and the TV banner `res/drawable-nodpi/banner.png`.

## Notes
- Fonts: uses the system serif. For the Cinzel look, add `cinzel_bold.ttf` to `res/font/` and use
  `FontFamily(Font(R.font.cinzel_bold))` in `HomeScreen.kt`.
- Power / sound / display / bluetooth / notifications shortcuts were removed (bottom bar is gone).
- Window animations between an app and home live in `res/anim` + `res/values/themes.xml` (`Anim.Teyvat`).

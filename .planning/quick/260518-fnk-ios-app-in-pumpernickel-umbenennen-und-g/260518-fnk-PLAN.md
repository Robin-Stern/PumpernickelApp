---
phase: quick-260518-fnk
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - assets/app-icon.svg
  - iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon.png
  - iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/Contents.json
  - iosApp/iosApp.xcodeproj/project.pbxproj
  - androidApp/src/androidMain/AndroidManifest.xml
  - androidApp/src/main/res/values/strings.xml
  - androidApp/src/androidMain/res/mipmap-mdpi/ic_launcher.png
  - androidApp/src/androidMain/res/mipmap-mdpi/ic_launcher_round.png
  - androidApp/src/androidMain/res/mipmap-hdpi/ic_launcher.png
  - androidApp/src/androidMain/res/mipmap-hdpi/ic_launcher_round.png
  - androidApp/src/androidMain/res/mipmap-xhdpi/ic_launcher.png
  - androidApp/src/androidMain/res/mipmap-xhdpi/ic_launcher_round.png
  - androidApp/src/androidMain/res/mipmap-xxhdpi/ic_launcher.png
  - androidApp/src/androidMain/res/mipmap-xxhdpi/ic_launcher_round.png
  - androidApp/src/androidMain/res/mipmap-xxxhdpi/ic_launcher.png
  - androidApp/src/androidMain/res/mipmap-xxxhdpi/ic_launcher_round.png
autonomous: false
requirements:
  - quick-260518-fnk
must_haves:
  truths:
    - "Beim Installieren der iOS-App auf einem Gerät/Simulator wird der Home-Screen-Name als 'Pumpernickel' angezeigt (statt 'iosApp')."
    - "Beim Installieren der Android-App wird der Launcher-Name als 'Pumpernickel' angezeigt (statt 'PumpernickelApp')."
    - "iOS- und Android-App zeigen das gleiche Icon, gerendert aus der einen SVG-Quelle unter assets/app-icon.svg."
    - "Die SVG-Quelle ist im Repo versioniert, damit das Icon reproduzierbar regeneriert werden kann."
  artifacts:
    - path: "assets/app-icon.svg"
      provides: "Versionierte Icon-Quelle für beide Plattformen"
    - path: "iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon.png"
      provides: "1024×1024 iOS-AppIcon-PNG aus SVG gerendert"
    - path: "iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/Contents.json"
      provides: "Asset-Catalog-Manifest mit filename-Verweis auf AppIcon.png"
      contains: "AppIcon.png"
    - path: "iosApp/iosApp.xcodeproj/project.pbxproj"
      provides: "CFBundleDisplayName=Pumpernickel in Debug und Release"
      contains: "INFOPLIST_KEY_CFBundleDisplayName = Pumpernickel"
    - path: "androidApp/src/main/res/values/strings.xml"
      provides: "app_name=Pumpernickel (in-place update der bestehenden strings.xml — Build-Output zeigt, dass diese Datei aktiv in merged-resources einfließt)"
      contains: '<string name="app_name">Pumpernickel</string>'
    - path: "androidApp/src/androidMain/AndroidManifest.xml"
      provides: "Application label/icon/roundIcon-Verweise"
      contains: '@string/app_name'
    - path: "androidApp/src/androidMain/res/mipmap-xxxhdpi/ic_launcher.png"
      provides: "Beispiel-Bitmap (192×192) — stellvertretend für alle 5 mipmap-Dichten"
  key_links:
    - from: "iosApp/iosApp.xcodeproj/project.pbxproj"
      to: "Home-Screen-Anzeige unter iOS"
      via: "INFOPLIST_KEY_CFBundleDisplayName, mit GENERATE_INFOPLIST_FILE = YES wird der Key in die generierte Info.plist gemerged"
      pattern: "INFOPLIST_KEY_CFBundleDisplayName = Pumpernickel"
    - from: "iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/Contents.json"
      to: "AppIcon.png im selben appiconset-Ordner"
      via: '"filename" Feld im einzigen image-Eintrag'
      pattern: '"filename"\s*:\s*"AppIcon.png"'
    - from: "androidApp/src/androidMain/AndroidManifest.xml"
      to: "androidApp/src/androidMain/res/values/strings.xml + res/mipmap-*/ic_launcher.png"
      via: "android:label='@string/app_name', android:icon='@mipmap/ic_launcher', android:roundIcon='@mipmap/ic_launcher_round'"
      pattern: 'android:label="@string/app_name".*android:icon="@mipmap/ic_launcher"'
---

<objective>
iOS- und Android-App auf den Anzeigenamen "Pumpernickel" vereinheitlichen und in beiden Plattformen das gleiche App-Icon ausrollen, gerendert aus einer einzigen, im Repo versionierten SVG-Quelle.

Purpose: Konsistentes Branding für die Demo. Aktuell zeigt iOS "iosApp" und Android "PumpernickelApp" — beide soll "Pumpernickel" werden, mit identischer Bildmarke.
Output: SVG-Quelle im Repo, generierte iOS-AppIcon-PNG (1024×1024), 5 Android-mipmap-Dichten (je ic_launcher + ic_launcher_round), aktualisierte Contents.json / pbxproj / strings.xml / AndroidManifest.xml.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@./CLAUDE.md
@.planning/STATE.md

<important_findings>
WICHTIG — Discovery hat zwei Punkte gegenüber der Locked-Decisions-Beschreibung korrigiert:

1. **Android-Sourceset: `src/androidMain/` UND `src/main/` werden beide gemerged.**
   `androidApp/build.gradle.kts` (Zeilen 19–24) ergänzt `src/androidMain/` zu `sourceSets["main"]` (manifest, kotlin, res, assets). EMPIRISCH verifiziert via `androidApp/build/intermediates/incremental/debug/packageDebugResources/merged.dir/values/values.xml`: die merged resources enthalten BEIDE `app_name` aus `src/main/res/values/strings.xml` UND würden zusätzliche Einträge aus `src/androidMain/res/values/` mergen.
   → **Strategie:** `src/main/res/values/strings.xml` direkt in-place updaten (`app_name` → "Pumpernickel"). KEINE neue strings.xml in androidMain anlegen, um Merge-Konflikte mit doppeltem `app_name`-Key zu vermeiden.
   → mipmap-Bitmaps NEU unter `androidApp/src/androidMain/res/mipmap-<dpi>/` anlegen (keine bestehenden mipmap-Resources, keine Konflikte).

2. **Aktueller AndroidManifest hat hardcoded `android:label="PumpernickelApp"`** (Zeile 23) und KEIN `android:icon`/`android:roundIcon`-Attribut. Beide werden ergänzt.

Aktueller iOS-Stand (`project.pbxproj`):
- Zeile 675/716: `ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon;` (passt)
- Zeile 684/725: `GENERATE_INFOPLIST_FILE = YES;` → `INFOPLIST_KEY_CFBundleDisplayName` wird in die generierte Info.plist gemerged
- Zeile 705/746: `PRODUCT_NAME = "$(TARGET_NAME)";` → TARGET_NAME = "iosApp" (nicht anfassen)
- Aktuelle `AppIcon.appiconset/Contents.json` hat einen einzigen image-Eintrag (universal/ios/1024×1024) ohne `filename` — Hinzufügen von `"filename": "AppIcon.png"` reicht.
- Aktuelle `Info.plist` (manuell gepflegt) enthält KEIN CFBundleDisplayName-Override — der pbxproj-INFOPLIST_KEY-Mechanismus wird also nicht überschrieben.
</important_findings>

<interfaces>
Asset-Catalog Format für iOS — Zielzustand `Contents.json`:
```json
{
  "images" : [
    {
      "filename" : "AppIcon.png",
      "idiom" : "universal",
      "platform" : "ios",
      "size" : "1024x1024"
    }
  ],
  "info" : {
    "author" : "xcode",
    "version" : 1
  }
}
```

pbxproj Build-Settings-Patch (für BEIDE Build-Configs — Debug und Release, also zweimal einfügen, einmal pro Block bei Zeile ~684 und ~725 direkt nach `GENERATE_INFOPLIST_FILE = YES;`):
```
INFOPLIST_KEY_CFBundleDisplayName = Pumpernickel;
```

Android-Mipmap-Größen (alle aus dem 1024×1024 Master-PNG via `sips -z H W` herunterskaliert):
| Dichte    | Pixel    |
|-----------|----------|
| mdpi      | 48×48    |
| hdpi      | 72×72    |
| xhdpi     | 96×96    |
| xxhdpi    | 144×144  |
| xxxhdpi   | 192×192  |
</interfaces>

<tooling_recipes>
SVG → 1024×1024 PNG (verifiziert verfügbar auf macOS, KEINE Installation nötig):
```bash
qlmanage -t -s 1024 -o "$OUT_DIR" "assets/app-icon.svg"
# erzeugt "$OUT_DIR/app-icon.svg.png" — danach umbenennen
```

PNG-Resize:
```bash
sips -z 192 192 ic_launcher_master.png --out ic_launcher.png
```

Schnelltests:
```bash
file <png>                      # erwartet: PNG image data, NNN x NNN, ...
sips -g pixelWidth -g pixelHeight <png>
```
</tooling_recipes>
</context>

<tasks>

<task type="auto">
  <name>Task 1: SVG-Quelle ins Repo versionieren</name>
  <files>assets/app-icon.svg</files>
  <action>
    1. Erstelle das Verzeichnis `assets/` im Repo-Root (falls nicht vorhanden).
    2. Kopiere die SVG von `/Users/olli/Downloads/app-icon-for-an-ios-fitness-and-nutrition-tracking.svg` nach `assets/app-icon.svg`.
       ```bash
       mkdir -p assets
       cp "/Users/olli/Downloads/app-icon-for-an-ios-fitness-and-nutrition-tracking.svg" "assets/app-icon.svg"
       ```
    3. Verifiziere mit `head -1 assets/app-icon.svg` dass die Datei mit `<?xml` oder `<svg` beginnt und mit `grep -c 'viewBox="0 0 1024 1024"' assets/app-icon.svg` dass das viewBox = 1024×1024 ist.
    4. Atomarer Commit: `chore(quick-260518-fnk): vendor app-icon SVG source`.
  </action>
  <verify>
    <automated>test -f assets/app-icon.svg && grep -q 'viewBox="0 0 1024 1024"' assets/app-icon.svg</automated>
  </verify>
  <done>`assets/app-icon.svg` existiert, enthält 1024×1024 viewBox, ist committed.</done>
</task>

<task type="auto">
  <name>Task 2: iOS — AppIcon.png rendern, Contents.json + pbxproj patchen</name>
  <files>
    iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon.png,
    iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/Contents.json,
    iosApp/iosApp.xcodeproj/project.pbxproj
  </files>
  <action>
    1. **PNG rendern** (qlmanage erzeugt Datei mit `.svg.png`-Suffix — danach umbenennen):
       ```bash
       TMP_DIR=$(mktemp -d)
       qlmanage -t -s 1024 -o "$TMP_DIR" "assets/app-icon.svg"
       mv "$TMP_DIR/app-icon.svg.png" "iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon.png"
       rm -rf "$TMP_DIR"
       ```
       Verifiziere die Pixelmaße:
       ```bash
       sips -g pixelWidth -g pixelHeight "iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon.png"
       # erwartet: pixelWidth: 1024, pixelHeight: 1024
       ```
       Falls qlmanage nicht 1024×1024 liefert, mit `sips -z 1024 1024 <png> --out <png>` korrigieren.

    2. **Contents.json patchen** — füge `"filename": "AppIcon.png"` als ERSTES Feld in den einzigen image-Eintrag ein. Zielzustand exakt (per `Write` schreiben):
       ```json
       {
         "images" : [
           {
             "filename" : "AppIcon.png",
             "idiom" : "universal",
             "platform" : "ios",
             "size" : "1024x1024"
           }
         ],
         "info" : {
           "author" : "xcode",
           "version" : 1
         }
       }
       ```

    3. **project.pbxproj patchen** — INFOPLIST_KEY_CFBundleDisplayName in BEIDE Build-Configs einfügen. Im File gibt es zwei Stellen mit `GENERATE_INFOPLIST_FILE = YES;` (Debug-Config ~Zeile 684, Release-Config ~Zeile 725). Direkt unter JEDEM dieser Vorkommen eine neue Zeile mit IDENTISCHER Einrückung einfügen:
       ```
       				INFOPLIST_KEY_CFBundleDisplayName = Pumpernickel;
       ```
       (Tabs zur Einrückung wie im pbxproj üblich — pbxproj nutzt Tab-Einrückung; den Edit-Tool mit dem genauen umliegenden Kontext aufrufen damit die zwei Vorkommen einzeln gepatcht werden, NICHT mit `replace_all`.)

       Verifiziere:
       ```bash
       grep -c "INFOPLIST_KEY_CFBundleDisplayName = Pumpernickel" iosApp/iosApp.xcodeproj/project.pbxproj
       # erwartet: 2
       ```

    4. Atomarer Commit: `feat(quick-260518-fnk): set iOS display name to Pumpernickel and ship AppIcon`.
  </action>
  <verify>
    <automated>test -f iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon.png && [ "$(sips -g pixelWidth iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon.png | awk '/pixelWidth/{print $2}')" = "1024" ] && grep -q '"filename" : "AppIcon.png"' iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/Contents.json && [ "$(grep -c 'INFOPLIST_KEY_CFBundleDisplayName = Pumpernickel' iosApp/iosApp.xcodeproj/project.pbxproj)" = "2" ]</automated>
  </verify>
  <done>AppIcon.png (1024×1024) liegt im appiconset, Contents.json referenziert die Datei, pbxproj enthält CFBundleDisplayName=Pumpernickel in Debug und Release.</done>
</task>

<task type="auto">
  <name>Task 3: Android — mipmap-Bitmaps generieren, strings.xml + Manifest aktualisieren</name>
  <files>
    androidApp/src/main/res/values/strings.xml,
    androidApp/src/androidMain/AndroidManifest.xml,
    androidApp/src/androidMain/res/mipmap-mdpi/ic_launcher.png,
    androidApp/src/androidMain/res/mipmap-mdpi/ic_launcher_round.png,
    androidApp/src/androidMain/res/mipmap-hdpi/ic_launcher.png,
    androidApp/src/androidMain/res/mipmap-hdpi/ic_launcher_round.png,
    androidApp/src/androidMain/res/mipmap-xhdpi/ic_launcher.png,
    androidApp/src/androidMain/res/mipmap-xhdpi/ic_launcher_round.png,
    androidApp/src/androidMain/res/mipmap-xxhdpi/ic_launcher.png,
    androidApp/src/androidMain/res/mipmap-xxhdpi/ic_launcher_round.png,
    androidApp/src/androidMain/res/mipmap-xxxhdpi/ic_launcher.png,
    androidApp/src/androidMain/res/mipmap-xxxhdpi/ic_launcher_round.png
  </files>
  <action>
    1. **Master-PNG rendern** (gleicher Schritt wie Task 2, aber in temp dir — wir können auch das in Task 2 erzeugte PNG wiederverwenden, sicherer ist neu rendern):
       ```bash
       TMP_DIR=$(mktemp -d)
       qlmanage -t -s 1024 -o "$TMP_DIR" "assets/app-icon.svg"
       MASTER="$TMP_DIR/master.png"
       mv "$TMP_DIR/app-icon.svg.png" "$MASTER"
       # Wenn qlmanage nicht 1024×1024 liefert: sips -z 1024 1024 "$MASTER" --out "$MASTER"
       ```

    2. **Mipmap-Verzeichnisse anlegen und PNGs skalieren.** Für jede Dichte `ic_launcher.png` UND `ic_launcher_round.png` mit identischem Bitmap erzeugen (Android maskiert roundIcon selbst — gleiche Quelldatei reicht):
       ```bash
       for entry in "mdpi:48" "hdpi:72" "xhdpi:96" "xxhdpi:144" "xxxhdpi:192"; do
         DPI="${entry%%:*}"
         SIZE="${entry##*:}"
         DIR="androidApp/src/androidMain/res/mipmap-${DPI}"
         mkdir -p "$DIR"
         sips -z "$SIZE" "$SIZE" "$MASTER" --out "$DIR/ic_launcher.png" > /dev/null
         cp "$DIR/ic_launcher.png" "$DIR/ic_launcher_round.png"
       done
       rm -rf "$TMP_DIR"
       ```
       Verifiziere Pixelmaße für alle 10 Dateien:
       ```bash
       for entry in "mdpi:48" "hdpi:72" "xhdpi:96" "xxhdpi:144" "xxxhdpi:192"; do
         DPI="${entry%%:*}"
         SIZE="${entry##*:}"
         for f in "ic_launcher.png" "ic_launcher_round.png"; do
           W=$(sips -g pixelWidth "androidApp/src/androidMain/res/mipmap-${DPI}/${f}" | awk '/pixelWidth/{print $2}')
           [ "$W" = "$SIZE" ] || { echo "FAIL ${DPI}/${f}: $W ≠ $SIZE"; exit 1; }
         done
       done
       echo "OK"
       ```

    3. **`androidApp/src/main/res/values/strings.xml` in-place patchen** — nur die eine `app_name`-Zeile ändern, alle anderen Strings unverändert lassen:
       ```diff
       -    <string name="app_name">PumpernickelApp</string>
       +    <string name="app_name">Pumpernickel</string>
       ```
       Wichtig: KEINE neue strings.xml in `androidMain/res/values/` anlegen — empirisch verifiziert (siehe `<important_findings>`), dass `src/main/res/values/strings.xml` aktiv in merged resources einfließt; eine zweite Datei in `androidMain` mit demselben `app_name`-Key würde Merge-Konflikt auslösen.

    4. **`androidApp/src/androidMain/AndroidManifest.xml` patchen** — im `<application>`-Tag (Zeilen ~20–27 aktuell):
       - `android:label="PumpernickelApp"` → `android:label="@string/app_name"`
       - Neue Attribute ergänzen: `android:icon="@mipmap/ic_launcher"` und `android:roundIcon="@mipmap/ic_launcher_round"`
       Reihenfolge der Attribute frei; gerne direkt nach `android:label` und vor `android:supportsRtl`. Der `<application>`-Tag muss nach dem Patch z.B. so aussehen:
       ```xml
       <application
           android:name=".PumpernickelApplication"
           android:allowBackup="true"
           android:label="@string/app_name"
           android:icon="@mipmap/ic_launcher"
           android:roundIcon="@mipmap/ic_launcher_round"
           android:supportsRtl="true"
           android:theme="@android:style/Theme.Material.Light.NoActionBar"
           android:dataExtractionRules="@xml/data_extraction_rules"
           android:fullBackupContent="@xml/backup_rules">
       ```

    5. Atomarer Commit: `feat(quick-260518-fnk): set Android app name to Pumpernickel and add launcher icons`.
  </action>
  <verify>
    <automated>
grep -q '<string name="app_name">Pumpernickel</string>' androidApp/src/main/res/values/strings.xml \
&& ! grep -q '<string name="app_name">PumpernickelApp</string>' androidApp/src/main/res/values/strings.xml \
&& grep -q 'android:label="@string/app_name"' androidApp/src/androidMain/AndroidManifest.xml \
&& grep -q 'android:icon="@mipmap/ic_launcher"' androidApp/src/androidMain/AndroidManifest.xml \
&& grep -q 'android:roundIcon="@mipmap/ic_launcher_round"' androidApp/src/androidMain/AndroidManifest.xml \
&& ! grep -q 'android:label="PumpernickelApp"' androidApp/src/androidMain/AndroidManifest.xml \
&& for entry in "mdpi:48" "hdpi:72" "xhdpi:96" "xxhdpi:144" "xxxhdpi:192"; do DPI="${entry%%:*}"; SIZE="${entry##*:}"; for f in ic_launcher.png ic_launcher_round.png; do P="androidApp/src/androidMain/res/mipmap-${DPI}/${f}"; test -f "$P" || exit 1; W=$(sips -g pixelWidth "$P" | awk '/pixelWidth/{print $2}'); [ "$W" = "$SIZE" ] || exit 1; done; done
    </automated>
  </verify>
  <done>strings.xml (`src/main/res/values/`) enthält app_name=Pumpernickel, Manifest referenziert @string/app_name + @mipmap/ic_launcher + @mipmap/ic_launcher_round, alle 10 mipmap-PNGs liegen in korrekten Größen vor.</done>
</task>

<task type="checkpoint:human-verify" gate="blocking">
  <name>Task 4: Human-Verify — App-Name und Icon auf Gerät/Simulator prüfen</name>
  <what-built>
    iOS: `INFOPLIST_KEY_CFBundleDisplayName = Pumpernickel` in beiden Build-Configs, `AppIcon.png` (1024×1024) im Asset-Catalog mit `filename`-Referenz in Contents.json.
    Android: `@string/app_name = "Pumpernickel"` im aktiven Sourceset, Manifest-Application mit `android:label`, `android:icon`, `android:roundIcon`; je 5 mipmap-Dichten für `ic_launcher` und `ic_launcher_round`.
  </what-built>
  <how-to-verify>
    1. **iOS** — In Xcode `iosApp.xcodeproj` öffnen, Build (Cmd+B), dann Run auf Simulator (Cmd+R).
       - Erwartung: Auf dem Simulator-Home-Screen erscheint der App-Name unter dem Icon als "Pumpernickel" (nicht mehr "iosApp").
       - Erwartung: Das Icon zeigt das Pumpernickel-Design aus der SVG-Quelle (dunkler Hintergrund, rote Glyphen).
       - Falls Xcode den Asset-Catalog cached: einmal "Product → Clean Build Folder" (Shift+Cmd+K) und neu builden.

    2. **Android** — In Android Studio (oder via Gradle) Run auf Emulator/Gerät.
       ```bash
       ./gradlew :androidApp:installDebug
       ```
       - Erwartung: Auf dem Android-Launcher erscheint der App-Name als "Pumpernickel" (nicht mehr "PumpernickelApp").
       - Erwartung: Das Icon zeigt dasselbe Pumpernickel-Design wie iOS. Bei runden Icon-Masken (z.B. Pixel-Launcher) wird das quadratische Bitmap maskiert dargestellt — das ist erwartet.

    3. **Vergleich** — iOS- und Android-Icon visuell nebeneinander betrachten. Bildmarke muss identisch sein (gleiche Glyphen, gleicher Hintergrund, gleicher Farbton).

    Falls einer der Punkte fehlschlägt: zurück zu Task 2 (iOS) oder Task 3 (Android), Fix committen, neu builden.
  </how-to-verify>
  <resume-signal>Tippe "approved" wenn iOS und Android beide "Pumpernickel" + identisches Icon zeigen, sonst beschreibe das Problem.</resume-signal>
</task>

</tasks>

<verification>
- iOS-Build (Debug + Release-Config-Settings korrekt gemerged): `grep -c "INFOPLIST_KEY_CFBundleDisplayName = Pumpernickel" iosApp/iosApp.xcodeproj/project.pbxproj` == 2
- iOS-Asset-Catalog konsistent: `grep -q '"filename" : "AppIcon.png"' iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/Contents.json` && `test -f iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon.png`
- Android-strings (in-place updated): `grep -q '<string name="app_name">Pumpernickel</string>' androidApp/src/main/res/values/strings.xml` und `! grep -q '<string name="app_name">PumpernickelApp</string>' androidApp/src/main/res/values/strings.xml`
- Android-Manifest sauber: kein hardcoded label mehr (`! grep -q 'android:label="PumpernickelApp"' androidApp/src/androidMain/AndroidManifest.xml`), referenziert @string/app_name + @mipmap/ic_launcher + @mipmap/ic_launcher_round
- Android-Bitmaps: 10 PNGs (5 Dichten × 2 Namen), jeweils in korrekter Pixelgröße
- SVG-Quelle versioniert: `test -f assets/app-icon.svg`
- Human-Verify: Beide Apps zeigen Anzeigenamen "Pumpernickel" + identisches Icon
</verification>

<success_criteria>
- Build läuft auf iOS und Android ohne Asset-Catalog- oder Resource-Linking-Fehler
- iOS-Home-Screen-Name = "Pumpernickel"
- Android-Launcher-Name = "Pumpernickel"
- Sichtbares Icon auf beiden Plattformen identisch und stammt nachweislich aus `assets/app-icon.svg`
- 3 atomare Commits + 1 Human-Verify-Checkpoint
</success_criteria>

<output>
Nach Abschluss: erstelle `.planning/quick/260518-fnk-ios-app-in-pumpernickel-umbenennen-und-g/260518-fnk-SUMMARY.md` mit Auflistung der Commits, finalem Asset-Inventar (Dateipfade + Pixelgrößen) und Screenshot-Pfaden (falls vorhanden).
</output>

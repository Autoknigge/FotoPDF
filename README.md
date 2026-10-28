# FotoPDF (Fotos zu PDF)

Android-App: mehrere Fotos auswählen, sortieren, drehen und als PDF in „Downloads" speichern. Gratis-Version mit AdMob-Banner. Umfang: siehe [SCOPE.md](SCOPE.md).

## Auf GitHub bauen

1. Neues (privates) Repo anlegen, Inhalt dieses Ordners hochladen.
2. Unter **Actions → Build** läuft der Build bei jedem Push. Ergebnis: Artefakt **FotoPdf** (`FotoPdf.apk`) – installierbar zum Testen, mit Google-Test-Werbung.
3. Release-Link zum Weitergeben: Tag `1.0` setzen → APK hängt automatisch am GitHub-Release.

## Echte Werbung (AdMob)

Repo → Settings → Secrets and variables → Actions:

| Secret | Inhalt |
|---|---|
| `ADMOB_APP_ID` | App-ID, Form `ca-app-pub-…~…` |
| `ADMOB_BANNER_ID` | Banner-Block-ID, Form `ca-app-pub-…/…` |

Ohne diese Secrets nimmt der Build die Google-Test-IDs. Im Code stehen nie echte IDs.
**Mit echten IDs nicht selbst auf die eigenen Anzeigen tippen** (Kontosperre-Risiko) – zum Testen die Test-APK nutzen.

## Signiert für den Play Store

Zusätzlich diese Secrets: `KEYSTORE_BASE64` (Keystore-Datei als Base64), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Dann entstehen `FotoPdf-signiert.apk` und `FotoPdf.aab` (Play Store braucht die AAB).
Keystore sicher aufbewahren – ohne ihn gibt es keine Updates.

## Vor dem Play-Store-Start

- Datenschutzerklärung unter `https://autoknigge.de/fotos-zu-pdf/` anlegen (Link ist in der App hinterlegt, Konstante `URL_PRIVACY` in `MainActivity.kt`). Inhalt: Fotos werden nur auf dem Gerät verarbeitet; Werbung über Google AdMob, Einwilligung über Google UMP.
- Play Console: Datenschutz-Angaben (Werbe-ID, AdMob) ausfüllen.
- Paketname `de.autoknigge.fotopdf` ist festgelegt – nach der Veröffentlichung nicht mehr änderbar.
- `targetSdk` (aktuell 36) vor Upload gegen die aktuelle Play-Vorgabe prüfen.

## Hinweis

Das Projekt wurde ohne Android-SDK geschrieben und noch nicht kompiliert. Der erste GitHub-Lauf zeigt, ob noch ein Versions- oder Tippfehler zu beheben ist.

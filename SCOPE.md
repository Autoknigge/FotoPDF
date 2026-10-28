# Fotos zu PDF – Umfang Version 1.0

Muster wie Video-Teiler: GitHub-Build (Actions), Gratis-Version mit AdMob-Banner, später Google Play.

## Enthalten in 1.0

**Kern**
- Mehrere Fotos auswählen (System-Fotoauswahl, keine Speicher-Berechtigung nötig), bis 100 Stück
- Fotos aus der Galerie per „Teilen" an die App übergeben
- Liste mit Vorschau: nach oben/unten verschieben, um 90° drehen, entfernen
- Seitenformat: **A4 mit Rand** (Hoch-/Querformat automatisch je Foto) oder **Bildgröße** (Seite = Foto)
- Qualität: Hoch / Mittel / Klein (Auflösung + JPEG-Stufe; Standard Mittel)
- Eigener Dateiname (Vorgabe: `Fotos_JJJJMMTT_HHMM`)
- PDF wird in **Downloads** gespeichert; danach **Teilen** oder **Öffnen**
- Datenschutz eingebaut: Fotos werden neu kodiert, EXIF/Standort/Kameradaten landen **nicht** im PDF; das PDF enthält keine Metadaten (Autor, Programm usw.)
- Verarbeitung ein Foto nach dem anderen → auch große Stapel ohne Speicherprobleme; alles lokal, kein Upload

**Foto zuschneiden (zweite Kachel im Startmenü)**
- Ein Foto wählen (oder aus der Galerie teilen), Ausschnitt mit einem Finger verschieben, mit zwei Fingern zoomen
- Formate: Frei (Ränder ziehen), Original, 1:1, 4:3, 3:2 (10×15), 4:5 (Instagram), 16:9, 9:16 (WhatsApp-Status), Passbild 35×45, A4
- Hoch/Quer umschalten, um 90° drehen
- Rahmen: Kein / Dünn / Mittel / Breit, 7 Farben (Weiß, Creme, Schwarz, Gold, Tannengrün, Braun, Rot); Vorschau = Ergebnis
- Speichern als JPEG in „Bilder/Fotos zu PDF“ (volle Auflösung bis 3000 px), danach Teilen oder direkt „In PDF“

**Werbung / Rechtliches**
- AdMob-Banner am Fuß, immer sichtbar (Platz ist fest reserviert)
- Google-Einwilligungsabfrage (UMP) beim Start, Eintrag „Werbe-Einstellungen" erscheint, wo vorgeschrieben
- Hinweis „Eine Idee von autoknigge.de" (Link) und Link „Datenschutz"
- Nur Deutsch; Aussehen wie Video-Teiler (Kopfkachel, Farbverlauf-Kacheln mit Goldrand, gleiche Fußzeile mit Datenschutz · Impressum · Werbe-Einstellungen)

**Build**
- GitHub Actions: APK bei jedem Push, Release-Anhang bei Tag `v*`
- Optional signiert (Secrets), dann zusätzlich AAB für den Play Store

## Bewusst nicht in 1.0
- Gesichter verpixeln (geplant 1.1, passt in den Foto-Editor)
- Pro-Version / App-Start-Anzeige (noch nicht entschieden, siehe README)
- Kamera/Scanner, Filter, Helligkeit/Korrekturen, Text/Sticker, PDF-Passwort, Texterkennung, Drag-and-drop-Sortierung
- Mehrsprachigkeit

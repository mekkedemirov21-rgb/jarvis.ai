# JARVIS Android: Ollama Cloud und Wake Word

## Was der Modus macht

- JARVIS sendet Chat-Anfragen direkt aus der Android-App an `https://ollama.com/api/chat`.
- Der Ollama-API-Key wird mit einem eigenen AES-GCM-Schlüssel im Android Keystore verschlüsselt.
- Der Chatverlauf bleibt nur im Arbeitsspeicher der App und wird von dieser Variante nicht als lokale Chatdatei gespeichert.
- Das Sprach-Wakeword wird über Androids On-Device-Spracherkennung versucht. Wenn keine On-Device-Erkennung verfügbar ist, startet JARVIS keinen Cloud-Spracherkennungs-Fallback.
- Wenn JARVIS das Wort „Jarvis“ erkennt, sagt es „Ich bin startklar. Was kann ich für dich tun?“ und nimmt danach den gesprochenen Auftrag auf. Antworten werden vorgelesen.
- Wakeword funktioniert in dieser ersten Version nur, solange JARVIS sichtbar geöffnet ist. Sobald die App in den Hintergrund wechselt, stoppt die Erkennung. Ein dauerhaftes Mithören bei gesperrtem Display erfordert einen separaten Foreground Service, eine dauerhafte Mikrofonbenachrichtigung und weitere Android-Berechtigungs-/Batterietests.

## Kosten

Ollamas Free-Plan umfasst begrenzte monatliche Starter-Nutzung und ausgewählte Starter-Modelle. Er ist keine unbegrenzte kostenlose API. Wenn dein Free-Kontingent oder Modellzugriff erschöpft ist, zeigt JARVIS einen Fehler und kauft selbst kein Guthaben. Lege kein kostenpflichtiges Guthaben an, wenn du absolut bei 0 Euro bleiben willst. Falls dein Ollama-Konto bereits gekauftes Guthaben besitzt, können API-Anfragen dieses laut Ollamas Abrechnungsmodell verwenden.

Vor dem Test prüfen:
- Du hast ein Ollama-Free-Konto.
- Das eingestellte Modell ist in deinem Account für Free/Starter-Zugriff verfügbar.
- Du hast kein bezahltes Guthaben für den Account hinzugefügt, wenn keine Kosten entstehen dürfen.

Siehe [Ollama Pricing](https://ollama.com/pricing) und [API Authentication](https://github.com/ollama/ollama/blob/main/docs/api/authentication.mdx).

## Datenschutz

Ollama gibt an, dass Prompts nicht zum Training verwendet oder protokolliert werden. Die Cloud muss Inhalte jedoch verarbeiten, um Antworten zu erzeugen; Inhalte verlassen daher dein Gerät und werden nicht ausschließlich lokal verarbeitet. Sende in diesem Cloud-Modus keine Passwörter, privaten Mail-Volltexte, sensiblen Dokumente oder andere Geheimnisse.

Der API-Key:
- wird nur an `https://ollama.com/api/chat` im Authorization-Bearer-Header gesendet;
- wird in der App mit Android Keystore verschlüsselt gespeichert;
- wird nicht in die WebView oder den Quellcode geschrieben;
- sollte nie in Screenshots, Chats, Issues oder Git eingecheckt werden.

## Verwendung

1. Installiere den APK-Build aus dem Workflow-Artefakt `JARVIS-debug-APK`.
2. Öffne JARVIS und wähle `Ollama Cloud direkt testen (Free-Kontingent)`.
3. Erstelle deinen API-Key unter [ollama.com/settings/keys](https://ollama.com/settings/keys) und gib ihn ein.
4. Wähle ein Modell, das dein Free-Konto unterstützt. Standardmäßig ist `gpt-oss:20b` eingetragen, aber der Zugriff kann je Konto/Kontingent variieren.
5. Sende eine kurze Testfrage.
6. Tippe auf `„Jarvis“ aktivieren`, erlaube das Mikrofon und lasse JARVIS auf dem Bildschirm geöffnet. Sage „Jarvis“; die App sollte antworten und danach deine gesprochene Frage aufnehmen.

Wenn die On-Device-Spracherkennung oder das deutsche Sprachpaket auf dem Telefon nicht verfügbar ist, funktioniert das Wakeword eventuell nicht. Die App sendet Audio nicht zur Cloud-Spracherkennung.

## Sicherheit und Grenzen

- Dies ist eine experimentelle persönliche APK, noch kein unabhängig sicherheitsgeprüftes Produkt.
- Die Cloud-Inferenz hat nur die APIs, die diese App explizit aufruft. Dieser Chat-Modus hat keinen direkten Zugriff auf deine SMS, Dateien, E-Mail, Kalender oder Geräte-Automatisierungen.
- Antworten/Verlauf werden nicht dauerhaft von dieser App gespeichert; Android kann Betriebssystemdiagnosen unabhängig davon verwalten.
- Wakeword ist in dieser Version kein Always-on-Dienst. Der Hintergrundbetrieb ist absichtlich deaktiviert.
- Der Companion-Modus mit privatem HTTPS-Server bleibt als alternative Betriebsart erhalten.

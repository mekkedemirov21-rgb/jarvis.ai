# JARVIS: kostenlose lokale KI, Integrationen und realistische Sicherheitsgrenzen

**Recherche- und Implementierungsstand: 26. Oktober 2026.** Dies ist ein persönlicher Entwicklungsaufbau, keine Sicherheitszertifizierung und noch kein vollständig autonomer Agent.

## 1. Empfehlung für das KI-Gehirn

### Standard: Ollama + Qwen3.5 4B

Empfohlener Startpunkt ist `qwen3.5:4b` in Ollama. Die Ollama-Modellbibliothek führt dafür rund 3,3–4,0 GB Downloadgröße auf; die Familie bietet unter anderem Tool-Use, Reasoning und Bildverarbeitung. Das 4B-Modell ist ein Kompromiss zwischen Hardwarebedarf, Reaktionszeit und Qualität. Es ist nicht mit einem großen Cloud-Reasoningmodell gleichzusetzen und muss an realen deutschen E-Mail-, Kalender- und Stellenanzeigen-Aufgaben getestet werden.

Auf einem üblichen PC sollte man mindestens 8 GB RAM als grobe praktische Untergrenze einplanen, besser 16 GB oder mehr, weil Betriebssystem und weitere Anwendungen ebenfalls Speicher benötigen. Die tatsächliche Geschwindigkeit hängt von CPU, GPU, RAM/VRAM und Quantisierung ab.

```bash
ollama pull qwen3.5:4b
ollama run qwen3.5:4b
```

### Upgrade, wenn der Computer genug Speicher hat: Qwen3.5 9B

Die Ollama-Bibliothek nennt für `qwen3.5:9b` ungefähr 6,6–7,6 GB Modell-Download. Ein 9B-Modell kann schwierige Anweisungen und längere Texte besser bewältigen, benötigt aber mehr Speicher und kann langsamer sein. Es ist kein garantierter Qualitätssprung für jede Aufgabe: E-Mail-Triage und Job-Matching sollte man anhand eigener, anonymisierter Testfälle vergleichen.

```bash
ollama pull qwen3.5:9b
```

### Alternative: Gemma 3 4B

Ollama listet `gemma3:4b` mit etwa 3,4 GB Download, multimodaler Eingabe und einem großen angegebenen Kontextfenster. Das ist ein sinnvoller Vergleichskandidat, aber für die konkreten JARVIS-Aufgaben sind Tool-Use, strukturierte JSON-Ausgaben und Zuverlässigkeit mit echten Testfällen wichtiger als Benchmark-Marketing.

### Ollama oder LM Studio?

Beide können ein heruntergeladenes Modell lokal ausführen. LM Studio dokumentiert Offline-Inferenz und einen OpenAI-kompatiblen lokalen Server; dafür muss ein Modell geladen und der lokale API-Server gestartet werden. JARVIS unterstützt jetzt beide lokalen Provider:

| Einstellung | Ollama (Standard) | LM Studio |
|---|---|---|
| `JARVIS_LLM_PROVIDER` | `ollama` | `lmstudio` |
| Standard-URL | `http://127.0.0.1:11434` | `http://127.0.0.1:1234/v1` |
| Modellkennung | z. B. `qwen3.5:4b` | exakte Modellkennung des lokalen LM-Studio-Servers |
| Kosten für Inferenz | keine API-Gebühr; eigene Hardware/Stromkosten | keine API-Gebühr; eigene Hardware/Stromkosten |
| Internet nach Download | für lokale Inferenz nicht nötig | für lokale Inferenz nicht nötig |

Konfiguriere `JARVIS_LLM_PROVIDER`, `JARVIS_LLM_MODEL` und gegebenenfalls `LMSTUDIO_BASE_URL` in deiner **lokalen, ignorierten** `.env`. Die Adapter-Implementierung blockiert Nicht-Loopback-Ziele; JARVIS soll keine privaten E-Mail- oder CV-Inhalte an eine frei eingetragene Cloud-URL senden. Nach Änderung der Einstellungen den lokalen Companion neu starten.

Wenn du ein OpenAI-Modell über den OpenAI-API-Dienst einbinden willst: ChatGPT-Nutzung und API-Abrechnung sind getrennte Produkte; eine ChatGPT-Free-Nutzung liefert nicht automatisch einen kostenlosen API-Endpunkt für deine eigene App. Das ist der Grund, warum JARVIS standardmäßig kein OpenAI-API-Konto benötigt.

## 2. Gedächtnis wie Obsidian

- **Langzeitgedächtnis:** lokaler Markdown-Vault, den du mit Obsidian öffnen kannst.
- **Struktur:** Profil, Präferenzen, Projekte, Personen/Beziehungen, Bewerbungen und Tagesnotizen sollten getrennte Bereiche sein.
- **Datenminimierung:** nicht jede Chatnachricht automatisch dauerhaft speichern. Stabile Fakten nur mit bewusstem Speichern; Notizen müssen editierbar, exportierbar und löschbar sein.
- **Verschlüsselung:** Markdown-Dateien selbst sind Klartext, damit Obsidian kompatibel bleibt. Nutze BitLocker, FileVault oder LUKS; speichere keine Zugangsdaten, Mail-Volltexte oder private Chat-Exporte automatisch im Vault. Für besonders sensible Notizen ist ein separater verschlüsselter Speicher möglich, aber dessen Integrität/Wiederherstellung muss ebenfalls getestet werden.

## 3. E-Mail und Kalender

Der aktuelle Google-Connector ist standardmäßig read-only: `gmail.readonly` und `calendar.events.readonly`. Die lokale KI klassifiziert neue E-Mail-Snippets als mögliche Zusage, Absage, Interview oder Bewerbungsupdate; ein optionaler Monitor speichert verschlüsselte Hinweise und Android zeigt neutrale Benachrichtigungen. Für bewusst aktivierte Schreibaktionen kann `JARVIS_ENABLE_GOOGLE_WRITES=true` zusätzlich `gmail.compose` und `calendar.events` anfordern. Danach ist eine neue Google-OAuth-Freigabe erforderlich. Unterstützt werden Gmail-Entwürfe, E-Mail-Versand sowie Kalendertermine erstellen/aktualisieren — aber nur über einen verschlüsselten Vorschlag, sichtbare Inhaltsprüfung, Freigabe und einen separaten Ausführen-Klick innerhalb von 15 Minuten. Send/Delete/accept/reject actions are not triggered from email classification; automatic email deletion, offer acceptance/rejection, and application submission are not implemented. Gmail write scopes may require app verification for public distribution.

Noch nicht als aktive Funktion behaupten:
- Gmail senden, Entwürfe schreiben oder Nachrichten löschen/archivieren
- Kalenderereignisse erstellen, ändern oder absagen
- Angebote annehmen/ablehnen oder externe Einladungen versenden
- Sofortige Push-Zustellung garantieren

Diese Aktionen brauchen passende OAuth-Rechte, ein separates Aktionsmodul, Rückgängig-/Fehlerbehandlung und **eine aktuelle ausdrückliche Freigabe** pro konkreter Aktion. Gmail-Scopes haben unterschiedliche Reichweite und teils Einschränkungen bzw. Prüfpflichten; die geringstmögliche Berechtigung ist vorzuziehen.

## 4. Stellensuche und Bewerbungen

- **Adzuna:** Die offizielle API erlaubt persönliche Stellensuche; API-Schlüssel werden kostenlos ohne Zahlungsdaten vergeben, die kostenlose Stufe ist aber zur Evaluierung gedacht und kann Limits haben. Bei größerem Volumen oder gewerblicher Nutzung gelten zusätzliche Bedingungen.
- **Indeed:** JARVIS öffnet normale Suchlinks. Indeed verbietet automatisierte Zugriffe, Scraping und automatisierte Übermittlung ohne ausdrückliche schriftliche Genehmigung. Deshalb wird die Seite nicht per Bot ausgelesen und die Bewerbung nicht automatisch abgeschickt.
- **Bundesagentur für Arbeit und LinkedIn:** werden aktuell über normale Suchlinks angeboten; es wird keine inoffizielle API-Zugriffserlaubnis behauptet.
- **Matching:** optionale Suchprofile werden verschlüsselt lokal gespeichert. Der lokale LLM bewertet Treffer, erklärt Gründe und Lücken und erzeugt Hinweise für eine Bewerbung. Die Bewertung ist nur Entscheidungshilfe und kann irren.
- **Bewerbung:** vor einem späteren Absendemodul müssen Zielunternehmen, Stellenlink, genaue Antworten, Lebenslaufdatei und Anschreiben vollständig angezeigt werden; erst nach frischer Bestätigung darf ein genehmigter, unterstützter Übermittlungsweg verwendet werden. Keine Massenbewerbungen, keine Umgehung von Captchas oder Identitätsprüfungen.

## Google writes: exact activation path

1. Keep `JARVIS_ENABLE_GOOGLE_WRITES=false` for the normal read-only setup.
2. Only if you knowingly want JARVIS to create drafts/send messages and create/update calendar events, set `JARVIS_ENABLE_GOOGLE_WRITES=true` in the local, untracked `.env` and restart the companion.
3. Disconnect/re-authorize Google, grant the newly requested scopes, and review Google consent. Gmail compose/send is a restricted scope and may require verification if this is distributed outside personal testing.
4. In the Freigaben tab, create a proposed action and inspect the complete recipient/text or event fields. Approving records the first confirmation; then use the separate execution button for the second confirmation. The action expires after 15 minutes. JARVIS does not automatically retry an attempt with an uncertain remote result.
5. E-mail delete/trash, auto-accepting or declining an offer, and job application submission are intentionally unavailable. The model cannot invoke any action by itself.

## 5. WhatsApp und Beziehungen

Der aktuelle sichere Umfang sind nutzerkontrollierte Erinnerungen, Jahrestage, Gesprächsziele und persönliche Notizen. Die private WhatsApp-Chat-Historie kann nicht einfach über eine allgemeine offizielle Consumer-API für einen Drittanbieter-Assistenten gelesen werden. JARVIS wird keine QR-Session, Cookies oder fremde Chats heimlich sammeln. WhatsApp erklärt außerdem, dass Unterhaltungen mit Drittanbieter-Agenten über den entsprechenden Agentendienst nicht als Ende-zu-Ende-verschlüsselte persönliche Chats gelten.

Wenn du WhatsApp direkt als Eingabekanal verwenden würdest, müsstest du daher die jeweilige offizielle Agent-/Business-Integration und deren Datenverarbeitung ausdrücklich abwägen. Sie ist kein Weg, um unbemerkt private Unterhaltung anderer Personen zu importieren.

## 6. Sicherheitsmodell und was E2E hier bedeutet

Der API-Endpunkt und das Modell laufen lokal; Requests an den Modellserver gehen nur an Loopback. Der Android-Client speichert die Companion-Adresse und verschlüsselt den API-Token mithilfe eines Schlüssels aus dem Android Keystore. Die Android-Verbindung verlangt HTTPS; Tailscale Serve kann einen lokalen Dienst nur innerhalb deines privaten Tailnet über HTTPS veröffentlichen.

Das ist **keine pauschale Ende-zu-Ende-Verschlüsselung des gesamten Assistenten**: Der lokale Companion und das lokale Modell müssen den Inhalt entschlüsseln können, um ihn auszuwerten. TLS schützt den Transport zwischen App und Companion, AES-GCM schützt bestimmte lokale Datenbanken/Token-Dateien, und Geräteverschlüsselung schützt die Platte. Ein echtes E2E-Modell, bei dem der Companion selbst Inhalte nicht lesen könnte, würde Verarbeitung und Speicherarchitektur grundlegend ändern und ist derzeit nicht implementiert.

„Keine Datenlücke“ kann niemand seriös garantieren. JARVIS muss weiter regelmäßig getestet, aktualisiert und extern geprüft werden. Geheimnisse und private Daten gehören nicht in dieses öffentliche Repository. Veröffentliche weder den lokalen API-Port noch Ollama/LM Studio direkt im Internet.

## Primärquellen

- Ollama Qwen3.5-Modellbibliothek: https://ollama.com/library/qwen3.5
- Ollama Gemma 3-Modellbibliothek: https://ollama.com/library/gemma3
- LM Studio Offlinebetrieb: https://lmstudio.ai/docs/app/offline
- LM Studio lokaler API-Server: https://lmstudio.ai/docs/developer/core/server
- Google Gmail API-Scopes: https://developers.google.com/workspace/gmail/api/auth/scopes
- Adzuna API-Nutzungsbedingungen: https://developer.adzuna.com/docs/terms_of_service
- Indeed Nutzungsbedingungen: https://de.indeed.com/legal
- WhatsApp Drittanbieter-Agenten & Verschlüsselung: https://faq.whatsapp.com/1050934623978152
- Tailscale Serve: https://tailscale.com/docs/features/tailscale-serve
- OpenAI: getrennte Abrechnung für ChatGPT und API: https://help.openai.com/en/articles/9039756-managing-billing-for-chatgpt-and-the-api-platform

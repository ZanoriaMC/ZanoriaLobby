# Die Lobby wird spielbar — Entwurf

**Datum:** 2026-09-03
**Repos:** ZanoriaLobby (Hauptteil), Nexus (eine Vergabestelle), Builders (eine Selbstmeldung)

---

## 0. Der gemessene Ausgangsstand

⚠️ **Alles in diesem Abschnitt ist am 2026-09-03 selbst gemessen worden**, nicht aus einer
Übergabe übernommen. Wo eine Suche einen Nullbefund lieferte, steht die Positivkontrolle daneben —
eine Suche, die nichts findet, meldet sonst Erfolg.

### ZanoriaLobby heute

Fünf Java-Dateien, eine 736-Zeilen-Hauptklasse. Vorhanden: Warteschlangen-NPCs (Mannequin mit
ArmorStand-Rückfall), BossBars, `/lobbynpc`, und ein weitgehend gebautes Builder-Untersystem.

| Stück | Ist-Stand | Beleg |
|---|---|---|
| **1** Adventure/Blockschutz | **nichts** | 0 Treffer auf `GameMode`/`BlockBreak`/`BlockPlace`/`ADVENTURE` in `src/main`; Positivkontrolle: dieselbe Suche findet 4 `@EventHandler` |
| **2** Hotbar-Items, Nexo | **nichts** | 0 Treffer auf `nexo`/`CustomModelData`, gleiche Positivkontrolle |
| **3** Menüs | **nichts** | — |
| **4** `/builder` | **weitgehend gebaut** | 4 Klassen unter `builder/` |

⚠️ **Es gibt kein `src/test` und keine Test-Abhängigkeit.** Maschinell belegt, nicht gezählt:
`./gradlew check --continue` meldet `:compileTestJava NO-SOURCE` und `:test NO-SOURCE`.
Ausgangsbau grün, Rückgabewert **0** (getrennt gemessen, nicht durch eine Pipeline gelesen —
`tail` hätte seinen eigenen Rückgabewert geliefert; Gegenkontrolle mit `false` ergab 1).
Befehlswächter: `geprueft 1 Plugin-Deskriptor(en), 5 Java-Datei(en) / keine Funde`.

### `/builder` ist gebunden — nur an das Falsche

Das ist die **erste Korrektur an der Auftragsvorgabe**, und sie ist angenommen.

Der Auftrag nannte `/builder` einen „offenen Befehl". Gemessen ist er das nicht:
`BuilderServerService.joinBuilderServer` prüft `redis.isMember(spieler.getName())`, und
`BuilderRedisClient.isMember` liefert bei fehlender Verbindung `false` — **die Prüfung fällt zu**,
nicht auf. Das Tor ist also da; es ist nur eine **Redis-Liste über den Spielernamen** statt einer
Berechtigung. In `plugin.yml` trägt der Befehl kein `permission:`-Feld; die Unterbefehle
`add`/`remove`/`list` prüfen `zanoria.admin`.

Zwei Schwächen bleiben davon unabhängig bestehen: der Schlüssel ist der **Name** (eine
Namensänderung verschiebt oder verliert den Zugang), und die Liste ist ein zweiter Ort neben dem
Rangsystem.

### Die Zielwelt ist ebenfalls schon gebaut

Das `Builders`-Plugin (eigenes Repo) führt `depend: WorldEdit` — **hart**, nicht soft. Es hat
Weltenerstellung (`MapManager`: `WorldCreator.createWorld`, `Bukkit.unloadWorld`), einen
WorldEdit-Zugangsfilter (`WorldEditAccessListener` mit `BlockerExtent`/`TrackingExtent`),
Leerlauf-Abschaltung und den Redis-Melder. Stück 4 ist damit **Härten, nicht Bauen**.

### ZanUI ist keine Kisten-Oberfläche

Das ist der Befund, an dem die tragende Auflage hängt. ZanUI fährt die **Paper-Dialog-API**:

```
UiScreen = Kennung + Titel(UiText-Schlüssel) + Rumpf + List<UiButton>
UiButton = Beschriftung + Breite + Hinweis + Handlung
ZanUiService.open(Player, UiScreen) -> boolean, wirft NICHT
```

Es gibt dort **keine Slots**. ZanUI sagt es über sich selbst im Kopf von `UiScreen`:

> ein Item-Name friert beim Setzen ein, ein Dialog wird bei jedem Öffnen neu gebaut

Eine Schnittstelle mit Slot-Nummern und `ItemStack`s könnte ZanUI **nie** erfüllen. Der Tausch
müßte dann die Menüdefinitionen anfassen und damit die Hörer — genau der Zustand, den der Auftrag
ausschließt.

### Nexo liegt auf lobby-1

`lobby-1/plugins/Nexo.jar` ist vorhanden. Die Kennungen haben ein Zuhause.

### Der Rechte-Befund: `zanoria.builder` wäre heute ein Tor ohne Schlüssel

Das ist die **zweite Korrektur an der Vorgabe**, und auch sie ist angenommen.

- **Zwölf Ränge**, keiner heißt BUILDER und keiner DEVELOPER:
  `OWNER · CO_OWNER · ADMIN · MODERATOR · SUPPORTER · VIP · CREATOR · TROPIC · SUNSET · ORCHID ·
  NAKOA · PLAYER`
  Positivkontrolle: `grep -c BUILDER Rank.java` = 0, `grep -c ADMIN Rank.java` = 1 bei derselben
  Suche — die Suche mißt wirklich.
- Nexus vergibt in `RankPresentationService.applyPermissions` **genau drei Muster**:
  `zanoria.rank.<id>`, `zanoria.rank.<name>` und `zanoria.admin` (nur OWNER/CO_OWNER/ADMIN).
  Die einzige `setPermission`-Stelle im ganzen Repo außerhalb von `zanoria.rank.*` ist
  `RankPresentationService:125`.
- **Kein LuckPerms im Stapel** — weder in `lobby-1/plugins` noch in einer Servervorlage.

Ein deklariertes, geprüftes `zanoria.builder` wäre also von niemandem vergebbar außer per `op`.

### Die Erstlauf-Bedingung — gemessen, und sie ist erfüllt

Der Erstlauf braucht **gebaute Jars von Nexus und ZanLang**: `ZanoriaLobby.onEnable` deaktiviert
das Plugin, wenn Nexus fehlt, und `t()` wirft ohne ZanLangs `TranslationService`.

⚠️ **Der erste Meßversuch war wertlos und das gehört hierher**, weil er sonst als Beleg
weitergereicht wird: `./gradlew build -x test` lieferte für beide Repos Rückgabewert 0 — aber
`compileJava` stand auf **UP-TO-DATE**. Das belegt Gradles Aktualitätsprüfung, nicht daß der
Quelltext übersetzt.

⚠️ **Der zweite Versuch war ein Werkzeugfehler:** `--rerun-tasks` ließ Nexus mit
`FileAlreadyExistsException … applyDevBundlePatches/lock` scheitern. Das ist **kein Nexus-Defekt** —
Nexus bindet ZanLang per `includeBuild` ein, beide teilen dasselbe paperweight-Dev-Bundle, und
`--rerun-tasks` reißt an dessen Lock. Nach dem Abbruch existierte das Lock nachweislich nicht mehr,
und das Schattenjar war unverändert.

**Gültig gemessen** (Klassenverzeichnis gelöscht, dann `compileJava` ohne `--rerun-tasks`):

| Repo | Rückgabewert | Task | neu entstandene Klassen |
|---|---|---|---|
| Nexus | **0** | `> Task :compileJava` (ausgeführt) | **411** |
| ZanLang | **0** | `> Task :compileJava` (ausgeführt) | **22** |

**Folge:** der Erstlauf ist Teil dieser Arbeit.

---

## 1. Zuschnitt

| Repo | Änderung | Umfang |
|---|---|---|
| **ZanoriaLobby** | Stück 1–4, Testgerüst, Erstlauf | groß |
| **Nexus** | Vergabestelle für `zanoria.builder` | klein |
| **Builders** | FAWE-Selbstmeldung nach Redis | klein |

⚠️ **Kein Ressourcenpaket wird angefaßt.** Platzhalter heißt hier Kennung + Vanilla-Rückfall, keine
Texturdatei. Damit kein Nexo-Neubau und **kein Proxy-Neustart** — der Proxy liest den Paket-Hash
nur beim Start, ein Neustart wäre ein Eingriff ohne Gegenwert.

---

## 2. Stück 1 — Adventure-Modus und Blockschutz

**Neu:** `LobbyWeltschutz implements Listener`, eigene Datei.

- Beitritt → `setGameMode(ADVENTURE)`
- `BlockBreakEvent`, `BlockPlaceEvent` → abgebrochen

**Beides, nicht nur der Modus.** Adventure allein läßt Abbau mit passendem Werkzeug zu; ein
Spieler mit einer Spitzhacke im Inventar bräche sonst Blöcke ab.

### ⚠️ Die Weltprüfung fällt zu, nicht offen

Der Schutz gilt für die konfigurierte Lobbywelt. **Existiert sie beim Start nicht**, schützt der
Hörer **alle** Welten und schreibt eine Fehlerzeile mit dem gesuchten und den vorhandenen Namen.

Der Grund: ein Namensdreher in der Konfiguration würde den Schutz sonst lautlos abschalten. Die
Wache liefe leer und meldete dabei Erfolg — dieselbe Bauart wie eine Prüfung über eine leere Liste.
Ein Irrtum soll hier in Richtung „zu viel geschützt" fallen, nicht in Richtung „gar nicht".

### Kein Bypass-Recht

Bewußt weggelassen. Wer bauen will, geht auf den Builder-Server; das ist Stück 4. Ein Bypass in der
Lobby wäre ein zweiter Bauweg neben genau dem, den Stück 4 absichert.

---

## 3. Stück 3 — die Menü-Naht *(vor Stück 2, siehe Reihenfolge)*

### Die Form

```
Bildschirm = Kennung + Titel(Schlüssel) + List<Eintrag>
Eintrag    = Beschriftung(Schlüssel) + Sinnbild(Nexo-Kennung + Vanilla-Rückfall) + Handlung
Menue      = boolean oeffne(Player, Bildschirm)          ← die Naht
ChestMenue implements Menue                              ← EXPERIMENTELL, einziger Bukkit-Ort
```

Die Form spiegelt `UiScreen` (Kennung + Titel + Knöpfe), damit ZanUI sie später **eins zu eins**
erfüllt. `oeffne` gibt `boolean` zurück und **wirft nicht** — dieselbe Zusage wie `ZanUiService.open`,
damit der spätere Tausch die Aufrufstellen nicht anfaßt.

⚠️ **Ein Bildschirm trägt Schlüssel, keine fertigen Sätze.** Aufgelöst wird beim Öffnen, für die
Sprache genau dieses Spielers. Das ist keine Stilfrage: `ChestMenue` baut das Inventar ohnehin je
Öffnung neu, und ohne diese Regel wäre die Naht mit ZanUI nicht deckungsgleich.

### Keine Layout-Logik

Entschieden vom Betreiber, wörtlich: *„so einfach wie möglich — die Menüs werden mit ZanUI ohnehin
neu gebaut. Kein Layout-Feinschliff, keine Slot-Logik."*

`ChestMenue` legt die Einträge der Reihe nach ab, Größe aufgerundet auf volle Neunerreihen. Kein
Rahmen, keine Lücken, keine Platzwünsche. Der Preis ist benannt: Kisten-Layouts sind nicht frei
malbar. Der Gewinn ist der Tausch ohne Hörerberührung.

### Der Wächter: kein Kisten-Typ außerhalb der Umsetzung

`:kistenwaechter`, an `check` gehängt, meldet und **macht den Bau rot** — anders als der
Platzhalterwächter. Begründung: ein Kisten-Typ im Hörer ist kein zu erledigender Rest, sondern
genau der Bruch, den die Auflage verbietet. Er wäre auch nicht dauerhaft rot, sondern nur bei einem
echten Verstoß; die Ermüdungsgefahr eines dauerhaft roten Baus besteht hier nicht.

⚠️ **Er liest den Bytecode, nicht den Quelltext.** Eine Quelltextsuche hat in diesem Stapel schon
einmal einen Kommentar für eine Anmeldung gehalten.

⚠️ **Er fährt eine Positivkontrolle.** Er muß die Kisten-Typen im Umsetzungspaket `menue.chest`
**finden**; findet er dort keine, bricht er ab. Ohne das wäre „keine Funde außerhalb" gratis grün,
sobald der Suchpfad nicht mehr stimmt — ein Sucher, der nichts sieht, meldet sonst ein gelöstes
Problem.

---

## 4. Stück 2 — die fünf Hotbar-Items

**Neu:** Enum `Hotbarplatz`, nach dem Muster von `NexusStrike/Nexusmodell`.

| Eintrag | Platz | Nexo-Kennung | Vanilla-Rückfall |
|---|---|---|---|
| `KOMPASS` | 0 | `zanoria:lobby_kompass` | `COMPASS` |
| `RUCKSACK` | 1 | `zanoria:lobby_rucksack` | `BUNDLE` |
| `BATTLEPASS` | 2 | `zanoria:lobby_battlepass` | `BOOK` |
| `SOZIAL` | 7 | `zanoria:lobby_sozial` | `PLAYER_HEAD` |
| `EINSTELLUNGEN` | 8 | `zanoria:lobby_einstellungen` | `COMPARATOR` |

Jeder Eintrag trägt zusätzlich einen Übersetzungsschlüssel, den Bildschirm, den er öffnet, und
`istPlatzhalter()` als **Feld** — nicht als Kommentar.

⚠️ **Der Vanilla-Rückfall ist ebenfalls geraten, nicht nur die Textur.** Kein Dokument nennt für
diese fünf Items einen Gegenstand; `BUNDLE` für den Rucksack und `COMPARATOR` für die Einstellungen
sind meine Wahl. Sie fallen unter dasselbe `istPlatzhalter()` und werden vom selben Wächter
gemeldet — ein zweiter Wächter nur für Materialien wäre hier Aufwand ohne Gegenwert, weil Nexo auf
`lobby-1` liegt und der Rückfall im Normalbetrieb gar nicht gezeigt wird. **Sichtbar wird er genau
dann, wenn Nexo fehlt** — also im Störungsfall, und dort ist „sieht anders aus" das gewollte
Verhalten gegenüber „ist weg".

### Der Platzhalterwächter

`:platzhalterwaechter` in `build.gradle`, an `check` gehängt, liest das **übersetzte** Enum und
meldet bei jedem Bau, welche Einträge noch auf Platzhalter zeigen.

⚠️ **Er macht den Bau NICHT rot.** Er wäre ab heute dauerhaft rot — alle fünf sind Platzhalter —,
und ein dauerhaft roter Bau wird nach zwei Tagen ignoriert. Er **meldet** laut; die **Menge** ist
in einem Testfall gepinnt, der rot wird, wenn einer lautlos dazukommt **oder** lautlos verschwindet.

⚠️ **Er bricht ab, wenn das Enum leer ist.** Ein leerer Sucher meldete sonst „0 von 0, keine
Platzhalter" und sähe aus wie ein gelöstes Problem.

⚠️ **Wer eine Textur liefert, setzt `istPlatzhalter()` auf `false` UND trägt den Eintrag aus dem
gepinnten Fall aus.** Beides zusammen, sonst wird der Fall rot. Das ist Absicht: eine gelöste
Lücke, deren Beschreibung stehenbleibt, ist dieselbe Lüge wie ein Platzhalter, den niemand meldet.

### ⚠️ Kein ⚠️ in gezeichneten Zeichenketten

Das Warnzeichen ist **zwei** Codepunkte (U+26A0 + U+FE0F), und Minecraft zeichnet U+FE0F als
Leerkasten. In Kommentaren und Protokollzeilen ist es richtig; in Item-Namen, Menütiteln und
Spielernachrichten **nie**.

### Vergabe

Beim Beitritt. Die Plätze sind **fest** und die Menge ist gepinnt: zwei Einträge auf demselben
Platz hieße, daß einer im Spiel unsichtbar ist, ohne Ausnahme und ohne Logzeile.

---

## 5. Stück 4 — /builder an eine Berechtigung binden

### 5.1 Nexus: die Vergabestelle

`NexusPermissions.BUILDER = "zanoria.builder"`, vergeben in
`RankPresentationService.applyPermissions` an **OWNER, CO_OWNER, ADMIN**.

⚠️ **Die Begründung gehört neben die Zeile, damit sie nicht später als Zufall gelesen wird**
(Betreiber, wörtlich):

> Bauen heißt Welten anlegen und löschen mit FAWE. Das ist ein Infrastrukturrecht, kein
> Moderationsrecht — deshalb die obersten Ränge und nicht eine Sprosse in der Mitte.

⚠️ **Und die Ablaufbedingung gehört dazu, weil dies eine Momentaufnahme ist und keine Lage:**

> Ein eigener BUILDER-Rang wäre die richtige Lösung, **sobald es externe Builder gibt**. Heute sind
> wir zu zweit, und ein dreizehnter Rang mit vier Tabellen löst ein Problem, das wir nicht haben.

Ohne diese Bedingung dreht sich der Satz mit der Zeit ins Gegenteil, ohne je falsch zu werden.

⚠️ **DEVELOPER gibt es nicht.** Der Auftrag nannte ihn („falls es den gibt"); gemessen ist er
keiner der zwölf Ränge. Die drei genannten reichen.

⚠️ **Kein neuer Rang, keine Datenmigration.** Die Rang-`id` steht laut `Rank.java` in vier
Tabellen, im Redis-Cache, in der Permission und im Scoreboard-Team-Namen.

### 5.2 Lobby: das Tor

`/builder` verlangt `zanoria.builder`, deklariert in `plugin.yml` mit `default: op`.

### 5.3 Die Redis-Liste wird stillgelegt, nicht gelöscht

`builder:members`, `isMember`, `addMember`, `removeMember`, `getMembers` **bleiben stehen** — sie
entscheiden nur nichts mehr über den Zugang.

⚠️ **Mit dem Grund daneben**, sonst legt sie jemand wieder an. Der Kommentar nennt: seit wann sie
nicht mehr entscheidet, warum (Rechtevergabe statt zweiter Ort, Name statt UUID war der
Schwachpunkt), und was stattdessen gilt.

**Gesichert durch eine Probe**, die verlangt, daß `BuilderServerService` `isMember` **nirgends**
ruft. Ein Kommentar allein hält niemanden auf; eine Zusicherung, die bei der Rückverdrahtung rot
wird, schon.

### 5.4 FAWE: der Builder-Server meldet sich selbst

⚠️ **Warum nicht einmal nachgesehen wird:** das Netz liegt in WSL, und **keine Distribution lief**
am 2026-09-03. Hochfahren hätte Docker, Proxy und Spielserver mitgestartet — ein Eingriff in den
Live-Betrieb für eine Momentaufnahme, die morgen nicht mehr gilt. `C:/Zanoria` ist die veraltete
Kopie (kein `builder-1` in der dortigen `velocity.toml`).

Stattdessen:

```
builder:status    = running
builder:worldedit = FastAsyncWorldEdit 2.15.1      (oder: FEHLT)
```

- **Builders** schreibt den Schlüssel beim Start neben `builder:status` und räumt ihn beim
  Herunterfahren mit weg.
- **Lobby** liest ihn vor dem Transfer. **Fehlt der Schlüssel oder steht `FEHLT` darin, wird
  abgewiesen** — mit einem Satz, der den Grund nennt.

⚠️ **Fällt zu.** Ein fehlender Schlüssel ist kein Grund durchzulassen: der Spieler landete sonst
auf einem Server ohne das Werkzeug, für das er hingeschickt wurde.

⚠️ Der Melder mißt **dauerhaft am laufenden Server**, nicht einmal von mir. Das ist der eigentliche
Gewinn gegenüber dem Nachsehen.

---

## 6. Die Proben

Je Stück **drei**: Gestalt, Wirkung, Mutation. Dazu der Erstlauf für alles, was nur am laufenden
Server sichtbar ist.

| Probe | Frage |
|---|---|
| **Gestalt** | Steht die Sache da? (Enum-Menge, Slots eindeutig, Rechte deklariert) |
| **Wirkung** | Tut sie etwas? (Der Hörer bricht wirklich ab; das Menü enthält wirklich die Einträge) |
| **Mutation** | Wird die Probe rot, wenn die Sache **ganz fehlt**? Je Mutation: gesetzt, voller Lauf, Meldung notiert, zurückgenommen. |

⚠️ **Die Frage lautet nicht „ist der Test grün?", sondern „könnte er grün sein, wenn die Sache ganz
fehlt?"** Eine Probe, die eine Mutation überlebt, prüft sie nicht.

⚠️ **Zu jedem Nullbefund eine Positivkontrolle.** Ein Wächter, der nichts findet, meldet sonst
Erfolg — auch wenn sein Suchpfad ins Leere zeigt.

⚠️ **Mutationen laufen im Wegwerf-Klon**, nicht im Live-Baum: eine zweite Sitzung im selben Repo
liest sich sonst wie der eigene Befund.

### Der Erstlauf

Ein wegwerfbarer Paper-Server nach dem Muster von `NexusStrike/tools/erstlauf.sh`, mit einer Sonde,
die am **laufenden** Server `HandlerList.getRegisteredListeners(...)` liest.

Er beantwortet, was kein Einheitstest beantwortet:

- Kommt das Plugin überhaupt hoch?
- Ist `LobbyWeltschutz` **angemeldet**?
- Ist `/builder` **angemeldet** und trägt es das Recht?
- Landen die fünf Items wirklich im Inventar, auf den richtigen Plätzen?

⚠️ **Die Hörerliste wird abgeleitet, nicht aufgeschrieben** — aus den Klassen mit
`implements Listener`, zeilenübergreifend. Aus den `registerEvents`-Zeilen abgeleitet würde eine
gelöschte Anmeldung die Erwartung mitschrumpfen lassen, und die Frage hätte sich selbst abgemeldet.

⚠️ **Was er nicht beantwortet:** ob Bukkit die Ereignisse in der angenommenen Lage feuert. Eine
Anmeldung ist keine Auslösung.

---

## 7. Reihenfolge

Vom Betreiber vorgegeben, mit Begründung:

| # | Stück | Warum hier |
|---|---|---|
| **0** | **Testgerüst** | Ohne es hat kein Stück eine Probe. Es gibt heute kein `src/test`. |
| **1** | **Stück 1** — Adventure/Blockschutz | *„das einzige, das ohne alles andere Sinn ergibt"* |
| **2** | **Stück 3** — die Naht | Muß vor ihrem Inhalt stehen |
| **3** | **Stück 2** — Hotbar + Menüs | *„füllt die Naht mit Inhalt — wenn sie danach kommt, sind die fünf Menüs schon gebaut und der Umbau teurer"* |
| **4** | **Stück 4** — /builder | Unabhängig von 1–3, deshalb zuletzt |

---

## 8. Was ausdrücklich NICHT gebaut wird

- **Kein Ressourcenpaket, keine Textur.** Platzhalter sind Kennung + Rückfall.
- **Kein BUILDER-Rang.** Entscheidung mit Ablaufbedingung, siehe 5.1.
- **Kein Bypass-Recht in der Lobby.** Siehe 2.
- **Kein Layout in den Menüs.** Siehe 3.
- **Kein Hochfahren des Netzes.** Siehe 5.4.
- **Kein Umbau der 736-Zeilen-Hauptklasse.** Die neuen Stücke kommen als eigene Dateien daneben.
  Ein Umbau wäre hier unverlangtes Aufräumen und würde die Mutationsproben der neuen Stücke mit
  fremdem Risiko belasten.

---

## 9. Offene Punkte

Keine. Die vier Entscheidungen, die dem Betreiber gehören, sind getroffen:

| Frage | Entschieden |
|---|---|
| Form der Menü-Naht | Einträge, keine Slots; so einfach wie möglich |
| Builder-Tor | Berechtigung **ersetzt** die Liste; Liste stillgelegt, nicht gelöscht |
| Schlüssel für `zanoria.builder` | Nexus vergibt an OWNER, CO_OWNER, ADMIN |
| FAWE-Messung | Selbstmeldung des Builder-Servers, kein Live-Eingriff |

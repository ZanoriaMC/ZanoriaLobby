# Die Lobby wird spielbar — Umsetzungsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Die Lobby wird spielbar — Adventure-Modus, fünf Hotbar-Items mit Menüs hinter einer ZanUI-tauschbaren Naht, und `/builder` an eine echte Berechtigung gebunden.

**Architecture:** Alle Entscheidungen werden als **bukkitfreie Rechnung** aus den Hörerrümpfen herausgezogen und dort einheitsgetestet; die Hörerrümpfe selbst laufen über einen kleinen `Hoerertreiber` mit `Proxy`-Attrappen; alles, was die Bukkit-Registry anfaßt (`ItemStack`, `Bukkit.createInventory`), ist ohne Server **nicht** baubar und gehört deshalb in den Erstlauf.

**Tech Stack:** Java 21, Gradle 8.14 (Groovy DSL), Paper-API 1.21.11, JUnit 5, Jedis, ZanLang (`net.zanoria:zanlang:1.0` aus mavenLocal), Nexus (Jar-Pfad).

**Spec:** `docs/superpowers/specs/2026-09-03-lobby-spielbar-design.md`

---

## ⚠️ Drei Regeln, die für JEDE Aufgabe gelten

1. **`JAVA_HOME` vor jedem Gradle-Lauf auf JDK 21 setzen.**
   ```bash
   export JAVA_HOME="/c/Program Files/Java/jdk-21"
   ```
   ⚠️ **KORREKTUR 2026-09-03: das ist hier nicht der wirksame Hebel.** `gradle.properties` dieses
   Repos pinnt `org.gradle.java.home=C:/Program Files/Eclipse Adoptium/jdk-21.0.10+7`, und
   **die Zeile wählt die Daemon-JVM** — die Stacktraces zeigen `java.base@21.0.10`, also das
   Adoptium, nicht das exportierte. `JAVA_HOME` wählt nur die **Launcher**-JVM des `gradlew`-Skripts.
   Beide sind hier 21, deshalb ist Setzen weiter richtig und schadet nie. **Aber wer den
   Daemon-Absturz woanders jagt, sucht ihn in `gradle.properties`, nicht in der Umgebung.**
2. **Die Ausgabe nie durch `grep`/`tail` messen.** Der Rückgabewert wäre dann der von `grep`.
   Immer in eine Datei schreiben, `$?` direkt danach lesen, und zur Kontrolle einmal `false` fahren.
3. **Vor jedem Meßlauf `build/test-results` löschen.** Alte XML aus einem abgebrochenen Lauf liest
   sich wie ein frischer Befund.
4. ⚠️ **`git diff` ist BLIND für neue Dateien — er taugt nicht als Beleg, daß eine Mutation
   zurückgenommen wurde.** Am 2026-09-03 in Task 3 aufgefallen: die mutierte Datei war noch
   unversioniert, `git diff --stat -- src/main` kam **leer** zurück und hätte „nichts mehr
   geändert" gemeldet, ohne irgendetwas gemessen zu haben — dieselbe Bauart wie eine Prüfung über
   eine leere Liste.
   **Belege stattdessen am Inhalt:** vor dem Mutationslauf die Vorkommen zählen, nach dem
   Zurücknehmen erneut zählen, und die Zeilen mit `rg` direkt aus der Datei zeigen.

**Der Meßbefehl dieses Repos:**
```bash
export JAVA_HOME="/c/Program Files/Java/jdk-21"
rm -rf build/test-results
./gradlew check --continue > /tmp/lauf.log 2>&1; echo "RC=$?"
```
Danach die **Ergebnis-XML** lesen (`build/test-results/test/TEST-*.xml`), nie `BUILD SUCCESSFUL`.

⚠️ **Der Rückgabewert zählt getrennt von der XML-Summe.** `check --continue` kann grüne Tests
melden und trotzdem `BUILD FAILED` sein — dann hat ein Wächter gefunden, was kein Test sieht.

---

## Dateikarte

### Neu in ZanoriaLobby

| Datei | Verantwortung |
|---|---|
| `src/main/java/.../lobby/schutz/Lobbyschutz.java` | **bukkitfrei.** Die Regel: schützt diese Welt? |
| `src/main/java/.../lobby/schutz/LobbyWeltschutz.java` | Der Hörer. Ruft `Lobbyschutz`, sonst nichts. |
| `src/main/java/.../lobby/menue/Bildschirm.java` | Kennung + Titel + Einträge |
| `src/main/java/.../lobby/menue/Eintrag.java` | Beschriftung + Sinnbild + Handlung |
| `src/main/java/.../lobby/menue/Sinnbild.java` | Nexo-Kennung + Vanilla-Rückfall |
| `src/main/java/.../lobby/menue/Menue.java` | **Die Naht.** `boolean oeffne(Player, Bildschirm)` |
| `src/main/java/.../lobby/menue/chest/Kistenplan.java` | **bukkitfrei.** Größe und Platz je Eintrag |
| `src/main/java/.../lobby/menue/chest/Materialwahl.java` | **bukkitfrei.** Rückfall oder Notnagel |
| `src/main/java/.../lobby/menue/chest/ChestMenue.java` | EXPERIMENTELL. Einziger Kisten-Ort. |
| `src/main/java/.../lobby/menue/chest/ChestKlickhoerer.java` | Klick **im** Menü. Liegt hier, weil `InventoryClickEvent` ein Kisten-Typ ist. |
| `src/main/java/.../lobby/hotbar/Hotbarplatz.java` | Das Enum mit `istPlatzhalter()` |
| `src/main/java/.../lobby/hotbar/Lobbybildschirme.java` | Die fünf Bildschirme |
| `src/main/java/.../lobby/hotbar/Hotbarhoerer.java` | Klick **auf** das Item → Menü (erkennt am Platz) |
| `src/main/java/.../lobby/hotbar/Hotbarausgabe.java` | Vergabe beim Beitritt |
| `tools/erstlauf.sh` | Wegwerf-Server, Sonde am laufenden Server |
| `tools/erstlauf-sonde/Sonde.java` | Liest `HandlerList` am laufenden Server |

### Geändert

| Datei | Änderung |
|---|---|
| `build.gradle` | JUnit 5, `:platzhalterwaechter`, `:kistenwaechter` |
| `src/main/resources/plugin.yml` | `zanoria.builder`, Recht am `builder`-Befehl |
| `src/main/resources/config.yml` | `lobby.welt` |
| `.../lobby/ZanoriaLobby.java` | drei `registerEvents`, Menü-Dienst anlegen |
| `.../lobby/builder/BuilderCommand.java` | Rechteprüfung, Liste stillgelegt |
| `.../lobby/builder/BuilderServerService.java` | FAWE-Prüfung statt `isMember` |
| `.../lobby/builder/BuilderRedisClient.java` | `getWorldedit()`, Liste stillgelegt |

### Fremde Repos

| Datei | Änderung |
|---|---|
| `Nexus/.../permission/NexusPermissions.java` | `BUILDER` |
| `Nexus/.../rank/RankPresentationService.java` | `darfBauen(Rank)` + Vergabe an drei Ränge |
| `Builders/.../redis/Fawemeldung.java` | **neu, bukkitfrei.** Die Meldezeile |
| `Builders/.../redis/BuilderRedisReporter.java` | `builder:worldedit` schreiben und räumen |
| `ZanLang/.../translations/de_de.json` + `en_us.json` | Bildschirmtitel und Eintragsbeschriftungen |

⚠️ **Die fünf Item-Namen sind NICHT neu.** `lobby.hotbar.compass|backpack|battlepass|friends|settings`
stehen seit längerem in ZanLang, mit Übersetzung („Wegweiser", „Rucksack", „Battle Pass",
„Freunde", „Einstellungen"). Am 2026-09-03 gemessen. Wer dafür eigene Schlüssel anlegt, erzeugt
eine zweite Wahrheit.

---

## Phase 0 — Testgerüst

### Task 1: JUnit einziehen und beweisen, daß es wirklich läuft

**Files:**
- Modify: `build.gradle`
- Test: `src/test/java/net/zanoria/lobby/DasTestgeruestLaeuftTest.java`

- [ ] **Step 1: Testabhängigkeiten in `build.gradle` ergänzen**

Im `dependencies`-Block, direkt nach `implementation 'redis.clients:jedis:5.1.3'`:

```groovy
    // ⚠️ paper-api ist oben compileOnly. Die Tests brauchen es auf ihrem EIGENEN Pfad -
    // compileOnly vererbt sich nicht an testCompileClasspath.
    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation platform('org.junit:junit-bom:5.11.3')
    testImplementation 'org.junit.jupiter:junit-jupiter'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
```

- [ ] **Step 2: `useJUnitPlatform()` anmelden**

Ans Ende von `build.gradle`, VOR dem Befehlswächter-Block:

```groovy
tasks.named('test') {
    useJUnitPlatform()
    testLogging {
        events "failed"
        exceptionFormat "full"
    }
}
```

- [ ] **Step 3: Den ersten Fall schreiben — er prüft das Gerüst, nicht das Spiel**

`src/test/java/net/zanoria/lobby/DasTestgeruestLaeuftTest.java`:

```java
package net.zanoria.lobby;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ⚠️ Dieser Fall prueft NICHT das Spiel, sondern dass ueberhaupt ein Fall laeuft.
 *
 * <p>Bis zum 2026-09-03 meldete dieses Repo {@code :test NO-SOURCE} - es gab kein einziges
 * {@code src/test}. Ein Geruest, das man fuer vorhanden haelt und das gar nicht faehrt, ist
 * teurer als gar keins: jede spaetere Aussage "die Tests sind gruen" waere dann wahr und
 * bedeutungslos.
 */
class DasTestgeruestLaeuftTest {

    @Test
    @DisplayName("Ein Fall dieses Repos wird wirklich gefahren")
    void einFallLaeuft() {
        assertTrue(true, "Wenn dieser Fall nicht in der Ergebnis-XML steht, laeuft das Geruest nicht.");
    }

    @Test
    @DisplayName("paper-api liegt auf dem Testpfad")
    void paperApiIstDa() {
        // ⚠️ Ein Klassenname als Zeichenkette, KEIN Import: so scheitert der Fall mit einer
        // lesbaren Meldung statt schon beim Uebersetzen.
        assertNotNull(
                org.bukkit.GameMode.ADVENTURE,
                "GameMode.ADVENTURE ist null - dann fehlt paper-api auf testCompileClasspath.");
    }
}
```

- [ ] **Step 4: Lauf und Nachweis, daß der Fall wirklich in der XML steht**

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-21"
rm -rf build/test-results
./gradlew check --continue > /tmp/lauf.log 2>&1; echo "RC=$?"
ls build/test-results/test/
```

Erwartet: `RC=0`, und `build/test-results/test/TEST-net.zanoria.lobby.DasTestgeruestLaeuftTest.xml`
existiert mit `tests="2" failures="0"`.

⚠️ **Wenn kein XML-Verzeichnis entsteht, ist `useJUnitPlatform()` nicht angekommen** — dann meldet
Gradle weiter `NO-SOURCE` und der Lauf ist grün, ohne etwas gefragt zu haben.

- [ ] **Step 5: Positivkontrolle — das Gerüst kann auch rot**

Ändere in `einFallLaeuft` das `assertTrue(true, ...)` zu `assertTrue(false, ...)`, fahre denselben
Befehl. Erwartet: **`RC=1`**, XML meldet `failures="1"`. Danach zurücknehmen und erneut fahren:
`RC=0`.

⚠️ **Diesen Schritt nicht überspringen.** Ein Gerüst, das nur grün kann, ist kein Gerüst — und das
ist genau die Bauart Fehler, die dieses Repo sonst überall jagt.

- [ ] **Step 6: Commit**

```bash
git add build.gradle src/test/java/net/zanoria/lobby/DasTestgeruestLaeuftTest.java
git commit -m "Testgeruest: JUnit 5 eingezogen, Positivkontrolle gefahren

Dieses Repo hatte kein src/test - :test meldete NO-SOURCE. Der erste Fall
prueft deshalb das Geruest selbst und ist beide Richtungen gefahren
worden: mit assertTrue(false) RC=1 und failures=1, zurueckgenommen RC=0.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 2: Messen, welche Bukkit-Ereignisse ohne Server baubar sind

⚠️ **Diese Aufgabe baut nichts. Sie mißt eine Grenze, auf der alle folgenden Aufgaben stehen.**
NexusStrike hat gemessen: `new ItemStack(Material)` wirft ohne Server `ExceptionInInitializerError`,
`EntityDamageByEntityEvent` wirft `NoClassDefFoundError` — **und die Grenze ist an der Signatur
nicht ablesbar.** Wer sie annimmt statt sie zu fahren, baut Aufgaben auf Sand.

**Files:**
- Test: `src/test/java/net/zanoria/lobby/pruefung/DieRegistrygrenzeIstGemessenTest.java`

- [ ] **Step 1: Die Sonde schreiben — je Glied EINZELN**

```java
package net.zanoria.lobby.pruefung;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ⚠️ Welche Bukkit-Objekte sich OHNE laufenden Server anlegen lassen, steht in keiner Signatur.
 *
 * <p>Gemessen in NexusStrike am 2026-09-02: {@code Material.SNOWBALL} ist baubar,
 * {@code new ItemStack(Material)} nicht - {@code ItemStack} ist eine gewoehnliche Klasse, und die
 * Grenze laeuft trotzdem mitten hindurch. Dort ist derselbe Fehler schon einmal gemacht worden:
 * aus einer Meldung am ENDE einer Kette wurde auf ihren ANFANG geschlossen.
 *
 * <p><b>Deshalb misst dieser Fall je Glied einzeln und schreibt das Ergebnis nach stdout.</b> Er
 * behauptet NICHTS - er berichtet. Gradle legt stdout in die Ergebnis-XML.
 */
class DieRegistrygrenzeIstGemessenTest {

    @Test
    @DisplayName("Bericht: was laesst sich ohne Server anlegen")
    void miss() {
        melde("GameMode.ADVENTURE", () -> org.bukkit.GameMode.ADVENTURE);
        melde("Material.COMPASS", () -> org.bukkit.Material.COMPASS);
        melde("new ItemStack(Material.COMPASS)",
                () -> new org.bukkit.inventory.ItemStack(org.bukkit.Material.COMPASS));
    }

    private void melde(String was, ThrowendeQuelle quelle) {
        // ⚠️ Throwable, nicht Exception: ExceptionInInitializerError und NoClassDefFoundError
        // sind Error. Ein catch(Exception) liesse genau die Faelle durch, die hier gesucht werden.
        try {
            Object ergebnis = quelle.hol();
            System.out.println("REGISTRYGRENZE: BAUBAR      " + was + " -> " + ergebnis);
        } catch (Throwable fehler) {
            System.out.println("REGISTRYGRENZE: NICHT       " + was
                    + " -> " + fehler.getClass().getName() + ": " + fehler.getMessage());
        }
    }

    @FunctionalInterface
    private interface ThrowendeQuelle {
        Object hol() throws Throwable;
    }
}
```

- [ ] **Step 2: Fahren und die Zeilen ablesen**

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-21"
rm -rf build/test-results
./gradlew check --continue > /tmp/lauf.log 2>&1; echo "RC=$?"
```

Dann in `build/test-results/test/TEST-net.zanoria.lobby.pruefung.DieRegistrygrenzeIstGemessenTest.xml`
den `<system-out>`-Block lesen.

**Erwartet nach der NexusStrike-Messung** (aber hier eigenständig zu bestätigen, nicht anzunehmen):
```
REGISTRYGRENZE: BAUBAR      GameMode.ADVENTURE -> ADVENTURE
REGISTRYGRENZE: BAUBAR      Material.COMPASS -> COMPASS
REGISTRYGRENZE: NICHT       new ItemStack(Material.COMPASS) -> java.lang.ExceptionInInitializerError: ...
```

- [ ] **Step 3: Das Ergebnis als Kommentar in den Fall zurückschreiben**

Trage die **wirklich gemessenen** Zeilen mit Datum in den Klassenkommentar ein. Weicht eine Zeile
von der Erwartung ab, **gilt die Messung** — und die betroffene Folgeaufgabe wird angepaßt, nicht
die Messung.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/net/zanoria/lobby/pruefung/DieRegistrygrenzeIstGemessenTest.java
git commit -m "Die Registrygrenze ist gemessen, nicht angenommen

Je Glied einzeln gefahren und nach stdout berichtet. Der Fall behauptet
nichts - er misst, worauf alle folgenden Aufgaben stehen.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Phase 1 — Stück 1: Adventure-Modus und Blockschutz

### Task 3: Die Regel — bukkitfrei, und sie fällt zu

**Files:**
- Create: `src/main/java/net/zanoria/lobby/schutz/Lobbyschutz.java`
- Test: `src/test/java/net/zanoria/lobby/schutz/LobbyschutzTest.java`

- [ ] **Step 1: Den fehlschlagenden Fall schreiben**

`src/test/java/net/zanoria/lobby/schutz/LobbyschutzTest.java`:

```java
package net.zanoria.lobby.schutz;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ⚠️ Der Kern dieser Klasse ist NICHT "schuetzt die Lobbywelt", sondern was passiert, wenn die
 * konfigurierte Welt gar nicht existiert.
 *
 * <p>Ein Namensdreher in der Konfiguration wuerde den Schutz sonst lautlos abschalten: der Hoerer
 * liefe, faende nie eine Uebereinstimmung, brueche nie etwas ab - und meldete dabei keinen
 * Fehler. Das ist die Bauart "die Wache laeuft leer und sagt, das sei kein Fehler".
 */
class LobbyschutzTest {

    @Test
    @DisplayName("Die konfigurierte Welt wird geschuetzt")
    void dieLobbyweltIstGeschuetzt() {
        Lobbyschutz schutz = Lobbyschutz.aus("lobby", List.of("lobby", "andere"));
        assertTrue(schutz.schuetzt("lobby"));
    }

    @Test
    @DisplayName("Eine andere Welt wird nicht geschuetzt")
    void andereWeltenBleibenFrei() {
        Lobbyschutz schutz = Lobbyschutz.aus("lobby", List.of("lobby", "andere"));
        assertFalse(schutz.schuetzt("andere"));
    }

    @Test
    @DisplayName("⚠️ Fehlt die konfigurierte Welt, wird ALLES geschuetzt - nicht nichts")
    void einNamensdreherSchuetztAlles() {
        Lobbyschutz schutz = Lobbyschutz.aus("lobbi", List.of("lobby", "andere"));

        assertTrue(schutz.schuetzt("lobby"),
                "⚠️ Die konfigurierte Welt 'lobbi' existiert nicht. Wuerde jetzt nichts geschuetzt,"
                        + " koennte jeder Spieler die Lobby abbauen - ohne Ausnahme und ohne"
                        + " Logzeile. Der Irrtum muss in Richtung 'zu viel geschuetzt' fallen.");
        assertTrue(schutz.schuetzt("andere"));
        assertTrue(schutz.faelltZu(),
                "⚠️ Der Zustand muss ABLESBAR sein, sonst kann der Hoerer ihn nicht protokollieren"
                        + " - und ein stiller Rueckfall auf 'alles' ist nur die zweitbeste Luege.");
    }

    @Test
    @DisplayName("⚠️ Bei null oder leerer Weltliste wird ebenfalls alles geschuetzt")
    void ohneWeltenWirdAllesGeschuetzt() {
        // ⚠️ Beim Start kann die Weltliste noch leer sein. Auch das ist "ich weiss es nicht",
        // und die Antwort auf "ich weiss es nicht" ist zu, nicht auf.
        assertTrue(Lobbyschutz.aus("lobby", List.of()).schuetzt("irgendwas"));
        assertTrue(Lobbyschutz.aus(null, List.of("lobby")).schuetzt("lobby"));
        assertTrue(Lobbyschutz.aus("  ", List.of("lobby")).schuetzt("lobby"));
    }
}
```

- [ ] **Step 2: Lauf zur Bestätigung, daß er fehlschlägt**

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-21"
./gradlew compileTestJava > /tmp/lauf.log 2>&1; echo "RC=$?"
```
Erwartet: **RC=1**, im Log `cannot find symbol: class Lobbyschutz`.

- [ ] **Step 3: Die minimale Umsetzung**

`src/main/java/net/zanoria/lobby/schutz/Lobbyschutz.java`:

```java
package net.zanoria.lobby.schutz;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Die Regel: welche Welt in der Lobby geschuetzt ist.
 *
 * <p>⚠️ <b>Bukkitfrei, und das ist der Grund, warum es diese Klasse gibt.</b> Der Hoerer daneben
 * laesst sich ohne Server nur schwer fahren; diese Rechnung laeuft in jedem Einheitstest. Dieselbe
 * Bauart wie {@code NexusStrikeMatch.beiTod(UUID, long)}: das Bukkit-Ende liest die Welt, das
 * rohe Ende entscheidet.
 *
 * <h2>⚠️ Warum sie ZU faellt und nicht auf</h2>
 *
 * <p>Steht in der Konfiguration eine Welt, die es nicht gibt - ein Tippfehler genuegt -, dann
 * schuetzt diese Klasse <b>alle</b> Welten und sagt es ueber {@link #faelltZu()}. Der Hoerer
 * schreibt daraufhin eine Fehlerzeile.
 *
 * <p>Die Gegenrichtung waere die teure: nichts geschuetzt, kein Fehler, kein Protokolleintrag -
 * und im Spiel baut jeder die Lobby ab. Ein Schutz, der bei Unsicherheit aufmacht, ist keiner.
 */
public final class Lobbyschutz {

    private final String weltname;
    private final boolean faelltZu;

    private Lobbyschutz(String weltname, boolean faelltZu) {
        this.weltname = weltname;
        this.faelltZu = faelltZu;
    }

    /**
     * @param konfigurierteWelt der Name aus {@code config.yml}, darf {@code null}/leer sein
     * @param vorhandeneWelten  die Namen der wirklich geladenen Welten
     */
    public static Lobbyschutz aus(String konfigurierteWelt, List<String> vorhandeneWelten) {
        Objects.requireNonNull(vorhandeneWelten, "vorhandeneWelten");
        if (konfigurierteWelt == null || konfigurierteWelt.isBlank()) {
            return new Lobbyschutz(null, true);
        }
        boolean gefunden = vorhandeneWelten.stream()
                .anyMatch(w -> w.equalsIgnoreCase(konfigurierteWelt));
        return gefunden
                ? new Lobbyschutz(konfigurierteWelt.toLowerCase(Locale.ROOT), false)
                : new Lobbyschutz(null, true);
    }

    /** Ob in dieser Welt weder abgebaut noch gesetzt werden darf. */
    public boolean schuetzt(String weltname) {
        if (faelltZu) {
            return true;
        }
        return weltname != null && weltname.toLowerCase(Locale.ROOT).equals(this.weltname);
    }

    /**
     * Ob der Rueckfall greift - also ob die konfigurierte Welt fehlt.
     *
     * <p>⚠️ Muss ablesbar sein, damit der Hoerer es protokollieren kann. Ein stiller Rueckfall auf
     * "alles geschuetzt" waere zwar sicher, aber niemand fuende je den Tippfehler.
     */
    public boolean faelltZu() {
        return faelltZu;
    }
}
```

- [ ] **Step 4: Lauf zur Bestätigung**

```bash
rm -rf build/test-results
./gradlew check --continue > /tmp/lauf.log 2>&1; echo "RC=$?"
```
Erwartet: `RC=0`, XML von `LobbyschutzTest` mit `tests="4" failures="0"`.

- [ ] **Step 5: Mutation M1 — die Regel fällt auf statt zu**

Ändere in `aus(...)` den Rückfall `new Lobbyschutz(null, true)` (beide Vorkommen) zu
`new Lobbyschutz(null, false)`. Fahre den vollen Lauf.

Erwartet: **RC=1**, `einNamensdreherSchuetztAlles` und `ohneWeltenWirdAllesGeschuetzt` rot.
Notiere die Meldung. **Danach zurücknehmen** und erneut fahren: `RC=0`.

⚠️ **Zwei Vorkommen** — ein `replace_all` ist hier Pflicht. Eine Mutation, die nur eines von zwei
ersetzt, kann still grün bleiben und sähe dann aus wie eine gedeckte Stelle.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/net/zanoria/lobby/schutz/Lobbyschutz.java \
        src/test/java/net/zanoria/lobby/schutz/LobbyschutzTest.java
git commit -m "Lobbyschutz: die Regel faellt zu, nicht auf

Ein Namensdreher in config.yml wuerde den Schutz sonst lautlos
abschalten. Mutation M1 (Rueckfall auf false) gefahren: 2 Faelle rot,
zurueckgenommen gruen.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 4: Der Hörertreiber — er fährt echte Rümpfe ohne Server

⚠️ **Ohne ihn ist Stück 1 nur zur Hälfte geprüft.** Ein Einheitstest auf `Lobbyschutz` sagt, daß
die **Regel** stimmt. Ob der **Hörerrumpf** sie benutzt — oder ob dort ein vorgezogener Ausstieg
steht —, sagt er nicht. In NexusStrike ist genau dieser Fall (`G2`) gemessen worden: ein
vorgezogener Ausstieg in einem Hörerrumpf blieb bei 272 grünen Tests **still grün**.

**Files:**
- Create: `src/test/java/net/zanoria/lobby/pruefung/Attrappe.java`
- Create: `src/test/java/net/zanoria/lobby/pruefung/Mitschrift.java`

- [ ] **Step 1: Die Mitschrift**

```java
package net.zanoria.lobby.pruefung;

import java.util.ArrayList;
import java.util.List;

/** Was waehrend eines gefahrenen Rumpfes wirklich aufgerufen wurde, in Reihenfolge. */
public final class Mitschrift {

    private final List<String> zeilen = new ArrayList<>();

    void schreibe(String zeile) {
        zeilen.add(zeile);
    }

    public List<String> zeilen() {
        return List.copyOf(zeilen);
    }

    public boolean enthaelt(String teil) {
        return zeilen.stream().anyMatch(z -> z.contains(teil));
    }

    @Override
    public String toString() {
        return zeilen.toString();
    }
}
```

- [ ] **Step 2: Die Attrappe — sie ist LAUT, nicht still**

```java
package net.zanoria.lobby.pruefung;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/**
 * Eine mitschreibende {@link Proxy}-Attrappe fuer Bukkit-Schnittstellen.
 *
 * <p>⚠️ <b>Sie ist laut.</b> Was nicht eingetragen ist, wird NICHT still mit {@code null}
 * beantwortet, sondern bricht mit dem Methodennamen ab. Eine stille Attrappe beantwortet jede
 * vergessene Frage mit {@code null}, der Rumpf laeuft in einen Zweig, den niemand gemeint hat,
 * und der Fall ist gruen.
 */
public final class Attrappe {

    private final Map<String, Object> antworten = new HashMap<>();
    private final Mitschrift mitschrift;
    private final String name;

    private Attrappe(String name, Mitschrift mitschrift) {
        this.name = name;
        this.mitschrift = mitschrift;
    }

    public static Attrappe von(String name, Mitschrift mitschrift) {
        return new Attrappe(name, mitschrift);
    }

    /** Traegt eine Antwort fuer einen Methodennamen ein. */
    public Attrappe antwortet(String methode, Object wert) {
        antworten.put(methode, wert);
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T> T als(Class<T> schnittstelle) {
        return (T) Proxy.newProxyInstance(
                schnittstelle.getClassLoader(),
                new Class<?>[]{schnittstelle},
                (proxy, methode, argumente) -> {
                    mitschrift.schreibe(name + "#" + methode.getName());

                    if (antworten.containsKey(methode.getName())) {
                        return antworten.get(methode.getName());
                    }
                    // void darf schweigen - dort gibt es nichts zu beantworten.
                    if (methode.getReturnType() == void.class) {
                        return null;
                    }
                    if (methode.getName().equals("toString")) {
                        return name;
                    }
                    throw new IllegalStateException(
                            "Die Attrappe " + name + " wurde nach " + methode.getName()
                                    + " gefragt und hat darauf keine Antwort. Trag sie mit"
                                    + " .antwortet(...) ein. Ein stilles null waere hier ein"
                                    + " gruener Fall ueber einen Zweig, den niemand gemeint hat.");
                });
    }
}
```

- [ ] **Step 3: Übersetzen**

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-21"
./gradlew compileTestJava > /tmp/lauf.log 2>&1; echo "RC=$?"
```
Erwartet: `RC=0`.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/net/zanoria/lobby/pruefung/Attrappe.java \
        src/test/java/net/zanoria/lobby/pruefung/Mitschrift.java
git commit -m "Hoerertreiber: Attrappe und Mitschrift"
```

---

### Task 5: Der Hörer LobbyWeltschutz

**Files:**
- Create: `src/main/java/net/zanoria/lobby/schutz/LobbyWeltschutz.java`
- Test: `src/test/java/net/zanoria/lobby/schutz/DerWeltschutzGreiftWirklichTest.java`

- [x] **Step 1a: ⚠️ GEMESSEN am 2026-09-03 (Task 2) — `BlockBreakEvent` IST baubar**

Die Rückfalloption unten **greift nicht**. Gemessen wurde je Glied einzeln:

```
REGISTRYGRENZE: BAUBAR   new BlockBreakEvent(block, spieler)
REGISTRYGRENZE: BAUBAR   new BlockPlaceEvent(block, zustand, dagegen, null, spieler, true)
REGISTRYGRENZE: BAUBAR   new PlayerJoinEvent(spieler, Component.text("x"))
```

⚠️ **Beim `BlockPlaceEvent` die SIEBENSTELLIGE Fassung mit explizitem `EquipmentSlot` nehmen** —
die sechsstellige ist `forRemoval`-markiert.

<details><summary>Die ursprüngliche Anweisung, falls die Messung je zurückgenommen wird</summary>

**⚠️ ZUERST messen, ob `BlockBreakEvent` ohne Server baubar ist**

Task 2 hat die Grenze für `ItemStack` gemessen; für `BlockBreakEvent` ist sie **nicht** gemessen,
und sie ist an der Signatur nicht ablesbar. Ergänze in `DieRegistrygrenzeIstGemessenTest.miss()`:

```java
melde("new BlockBreakEvent(block, spieler)", () -> {
    org.bukkit.World welt = Attrappe.von("W", new Mitschrift())
            .antwortet("getName", "lobby").als(org.bukkit.World.class);
    org.bukkit.block.Block block = Attrappe.von("B", new Mitschrift())
            .antwortet("getWorld", welt).als(org.bukkit.block.Block.class);
    org.bukkit.entity.Player spieler = Attrappe.von("P", new Mitschrift())
            .als(org.bukkit.entity.Player.class);
    return new org.bukkit.event.block.BlockBreakEvent(block, spieler);
});
```

Fahren, `<system-out>` lesen.

⚠️ **Meldet die Zeile `NICHT`, wird nicht getrickst:** die Wirkungsprüfung wandert dann vollständig
in den Erstlauf (Task 18), und die Fälle unten werden **gestrichen statt entkernt**. Ein Fall, der
das Argument selbst baut, um die Grenze zu umgehen, mißt die Engine statt des Aufrufers.

</details>

- [ ] **Step 1b: Den Wirkungsfall schreiben**

```java
package net.zanoria.lobby.schutz;

import net.zanoria.lobby.pruefung.Attrappe;
import net.zanoria.lobby.pruefung.Mitschrift;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ⚠️ Diese Faelle fahren den ECHTEN Hoererrumpf, nicht die Regel daneben.
 *
 * <p>Der Unterschied ist in NexusStrike als G2 gemessen worden: ein vorgezogener Ausstieg im
 * Hoererrumpf blieb bei 272 gruenen Tests still gruen, weil kein einziger Fall den Rumpf fuhr.
 * LobbyschutzTest pruefte dann eine Regel, die niemand mehr benutzt.
 */
class DerWeltschutzGreiftWirklichTest {

    private LobbyWeltschutz schutzIn(String konfiguriert, String vorhanden) {
        return new LobbyWeltschutz(Lobbyschutz.aus(konfiguriert, List.of(vorhanden)));
    }

    private BlockBreakEvent abbauIn(String weltname, Mitschrift mitschrift) {
        World welt = Attrappe.von("Welt", mitschrift)
                .antwortet("getName", weltname).als(World.class);
        Block block = Attrappe.von("Block", mitschrift)
                .antwortet("getWorld", welt).als(Block.class);
        Player spieler = Attrappe.von("Spieler", mitschrift).als(Player.class);
        return new BlockBreakEvent(block, spieler);
    }

    @Test
    @DisplayName("In der Lobbywelt wird der Abbau wirklich abgebrochen")
    void abbauWirdAbgebrochen() {
        Mitschrift mitschrift = new Mitschrift();
        BlockBreakEvent ereignis = abbauIn("lobby", mitschrift);

        schutzIn("lobby", "lobby").beimAbbauen(ereignis);

        assertTrue(ereignis.isCancelled(),
                "Der Rumpf hat den Abbau NICHT abgebrochen. Entweder ruft er die Regel nicht,"
                        + " oder vor dem Aufruf steht ein vorgezogener Ausstieg. Mitschrift: "
                        + mitschrift);
    }

    @Test
    @DisplayName("Ausserhalb der Lobbywelt bleibt der Abbau erlaubt")
    void andereWeltBleibtFrei() {
        Mitschrift mitschrift = new Mitschrift();
        BlockBreakEvent ereignis = abbauIn("andere", mitschrift);

        schutzIn("lobby", "lobby").beimAbbauen(ereignis);

        assertFalse(ereignis.isCancelled(),
                "Der Rumpf bricht ueberall ab. Dann waere der Fall darueber auch gruen, wenn die"
                        + " Regel gar nicht gefragt wuerde - er pruefte nichts.");
    }

    @Test
    @DisplayName("Fehlt die konfigurierte Welt, wird auch anderswo abgebrochen")
    void beiNamensdreherWirdUeberallAbgebrochen() {
        Mitschrift mitschrift = new Mitschrift();
        BlockBreakEvent ereignis = abbauIn("andere", mitschrift);

        schutzIn("lobbi", "lobby").beimAbbauen(ereignis);

        assertTrue(ereignis.isCancelled(),
                "Bei einem Tippfehler in der Konfiguration darf NICHT aufgemacht werden.");
    }

    @Test
    @DisplayName("Beim Beitritt wird der Spielmodus wirklich gesetzt")
    void beitrittSetztAdventure() {
        Mitschrift mitschrift = new Mitschrift();
        World welt = Attrappe.von("Welt", mitschrift)
                .antwortet("getName", "lobby").als(World.class);
        Player spieler = Attrappe.von("Spieler", mitschrift)
                .antwortet("getWorld", welt).als(Player.class);

        schutzIn("lobby", "lobby").beimBeitreten(spieler);

        assertTrue(mitschrift.enthaelt("Spieler#setGameMode"),
                "setGameMode wurde NIE ausgefuehrt. Mitschrift: " + mitschrift);
    }
}
```

- [ ] **Step 2: Lauf zur Bestätigung, daß er fehlschlägt**

Erwartet: `RC=1`, `cannot find symbol: class LobbyWeltschutz`.

- [ ] **Step 3: Der Hörer**

```java
package net.zanoria.lobby.schutz;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Objects;

/**
 * Adventure-Modus und Blockschutz in der Lobbywelt.
 *
 * <p>⚠️ <b>Beides, nicht nur der Modus.</b> Adventure allein laesst den Abbau mit passendem
 * Werkzeug zu - ein Spieler mit einer Spitzhacke im Inventar braeche sonst Bloecke ab.
 *
 * <p>⚠️ Dieser Hoerer entscheidet NICHTS. Er liest die Welt und fragt {@link Lobbyschutz}. Der
 * Grund steht dort: die Regel ist ohne Server pruefbar, ein Hoererrumpf nur mit Muehe.
 */
public final class LobbyWeltschutz implements Listener {

    private final Lobbyschutz schutz;

    public LobbyWeltschutz(Lobbyschutz schutz) {
        this.schutz = Objects.requireNonNull(schutz, "schutz");
    }

    @EventHandler
    public void beimAbbauen(BlockBreakEvent ereignis) {
        if (schutz.schuetzt(ereignis.getBlock().getWorld().getName())) {
            ereignis.setCancelled(true);
        }
    }

    @EventHandler
    public void beimSetzen(BlockPlaceEvent ereignis) {
        if (schutz.schuetzt(ereignis.getBlock().getWorld().getName())) {
            ereignis.setCancelled(true);
        }
    }

    @EventHandler
    public void beiBeitritt(PlayerJoinEvent ereignis) {
        beimBeitreten(ereignis.getPlayer());
    }

    /**
     * Der rohe Teil des Beitritts - ohne {@code PlayerJoinEvent}.
     *
     * <p>⚠️ Herausgezogen, weil die Baubarkeit von {@code PlayerJoinEvent} ohne Server nicht
     * ablesbar ist. Dieselbe Bauart wie {@code NexusStrikeMatch.beiTod(UUID, long)}: das
     * Bukkit-Ende liest, das rohe Ende entscheidet.
     */
    public void beimBeitreten(Player spieler) {
        if (schutz.schuetzt(spieler.getWorld().getName())) {
            spieler.setGameMode(GameMode.ADVENTURE);
        }
    }
}
```

- [ ] **Step 4: Lauf zur Bestätigung**

Voller Meßbefehl. Erwartet: `RC=0`, `DerWeltschutzGreiftWirklichTest` mit `tests="4" failures="0"`.

- [ ] **Step 5: Mutationen M2–M4, je gesetzt, voller Lauf, zurückgenommen**

| Mutation | Änderung | erwartet |
|---|---|---|
| **M2** | `beimAbbauen`: vorgezogener Ausstieg `if (schutz != null) { return; }` am Kopf | rot: `abbauWirdAbgebrochen`, `beiNamensdreherWirdUeberallAbgebrochen` |
| **M3** | `beimSetzen`: `setCancelled(true)` → `setCancelled(false)` | ⚠️ **erwartet STILL GRÜN** — kein Fall fährt `beimSetzen`. Siehe Step 6. |
| **M4** | `beimBeitreten`: `setGameMode`-Zeile entfernt | rot: `beitrittSetztAdventure` |

⚠️ **M2 ist die wichtige.** Der Aufruf bleibt dabei im Bytecode stehen; nur die Ausführung fällt
weg. Genau diese Mutation überlebt jede Prüfung, die Anwesenheit statt Erreichbarkeit mißt.

- [ ] **Step 6: M3 schließen — den zweiten Rumpf ebenfalls fahren**

M3 ist beim ersten Lauf still grün; das ist ein **Befund, kein Versehen**. Ergänze den Gegenfall
für `beimSetzen` (gebaut aus `BlockPlaceEvent` mit denselben Attrappen), fahre M3 erneut — **jetzt
muß sie rot sein**.

⚠️ **Nicht die Mutation streichen, weil sie unbequem ist.** Ein Rumpf ohne Fall ist eine Lücke; sie
zu melden und dann zu schließen ist der Weg.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/net/zanoria/lobby/schutz/LobbyWeltschutz.java \
        src/test/java/net/zanoria/lobby/schutz/DerWeltschutzGreiftWirklichTest.java
git commit -m "LobbyWeltschutz: Adventure und Blockschutz, Rumpf wirklich gefahren"
```

---

### Task 6: Anmelden und konfigurieren

**Files:**
- Modify: `src/main/resources/config.yml`
- Modify: `src/main/java/net/zanoria/lobby/ZanoriaLobby.java`

- [ ] **Step 1: `config.yml` ergänzen**

Ganz oben, vor `spawn:`:

```yaml
# Die Welt, in der Adventure-Modus und Blockschutz gelten.
# ⚠️ Steht hier ein Name, den es nicht gibt, wird ALLES geschuetzt und eine Fehlerzeile
# geschrieben. Der Irrtum faellt absichtlich in diese Richtung: die Gegenrichtung waere
# "niemand schuetzt etwas, und niemand erfaehrt es".
lobby:
  welt: world
```

- [ ] **Step 2: In `onEnable` anmelden**

In `ZanoriaLobby.onEnable`, direkt nach `getServer().getPluginManager().registerEvents(this, this);`:

```java
        // ── Lobbyschutz ─────────────────────────────────────────────────────
        Lobbyschutz lobbyschutz = Lobbyschutz.aus(
                getConfig().getString("lobby.welt"),
                getServer().getWorlds().stream().map(World::getName).toList());
        if (lobbyschutz.faelltZu()) {
            // ⚠️ LAUT. Ein stiller Rueckfall auf "alles geschuetzt" waere zwar sicher, aber
            // niemand faende je den Tippfehler in der Konfiguration.
            getSLF4JLogger().error(
                    "ZanoriaLobby: die konfigurierte Lobbywelt '{}' existiert nicht."
                            + " Vorhanden sind: {}. Es werden vorsorglich ALLE Welten geschuetzt.",
                    getConfig().getString("lobby.welt"),
                    getServer().getWorlds().stream().map(World::getName).toList());
        }
        getServer().getPluginManager().registerEvents(new LobbyWeltschutz(lobbyschutz), this);
```

Dazu die Importe `net.zanoria.lobby.schutz.Lobbyschutz` und `net.zanoria.lobby.schutz.LobbyWeltschutz`.

- [ ] **Step 3: Voller Lauf**

Erwartet: `RC=0`. Der Befehlswächter muß weiterhin `keine Funde` melden.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/config.yml src/main/java/net/zanoria/lobby/ZanoriaLobby.java
git commit -m "Lobbyschutz angemeldet, Rueckfall wird laut protokolliert"
```

⚠️ **Daß der Hörer wirklich angemeldet ist, sagt kein Einheitstest.** Das beantwortet der Erstlauf
(Task 18).

---

## Phase 2 — Stück 3: Die Menü-Naht

⚠️ **Diese Phase steht vor Stück 2, und der Grund ist die Reihenfolgeentscheidung des Betreibers:**
*„Stück 2 füllt die Naht mit Inhalt — wenn sie danach kommt, sind die fünf Menüs schon gebaut und
der Umbau teurer."*

### Task 7: Die Naht selbst — Bildschirm, Eintrag, Sinnbild, Menue

**Files:**
- Create: `src/main/java/net/zanoria/lobby/menue/Sinnbild.java`
- Create: `src/main/java/net/zanoria/lobby/menue/Eintrag.java`
- Create: `src/main/java/net/zanoria/lobby/menue/Bildschirm.java`
- Create: `src/main/java/net/zanoria/lobby/menue/Menue.java`
- Test: `src/test/java/net/zanoria/lobby/menue/DieNahtPasstZuZanUiTest.java`

- [ ] **Step 1: Den Gestaltfall schreiben**

```java
package net.zanoria.lobby.menue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ⚠️ Diese Faelle halten die Naht in der Form, die ZanUI spaeter erfuellen kann.
 *
 * <p>Gemessen am 2026-09-03: ZanUI fuehrt die Paper-Dialog-API. Ein UiScreen ist Kennung + Titel +
 * Knoepfe - <b>es gibt dort keine Slots</b>. Eine Schnittstelle mit Slot-Nummern und ItemStacks
 * koennte ZanUI nie erfuellen; der Tausch muesste die Hoerer anfassen, und genau das schliesst die
 * Auflage aus.
 *
 * <p>⚠️ Wer hier ein Feld fuer eine Slot-Nummer ergaenzt, bricht die Auflage - auch wenn nichts
 * rot wird, das der Umsetzung gehoert. Deshalb steht die Begruendung hier und nicht in einem
 * Bericht.
 */
class DieNahtPasstZuZanUiTest {

    @Test
    @DisplayName("Ein Bildschirm traegt Kennung, Titelschluessel und Eintraege")
    void dieFormStimmt() {
        Bildschirm b = Bildschirm.of("lobby.kompass", "lobby.kompass.titel",
                List.of(Eintrag.of("lobby.kompass.relicwars",
                        Sinnbild.of("zanoria:lobby_kompass", "COMPASS"),
                        spieler -> { })));

        assertEquals("lobby.kompass", b.kennung());
        assertEquals("lobby.kompass.titel", b.titelSchluessel());
        assertEquals(1, b.eintraege().size());
    }

    @Test
    @DisplayName("Ein Bildschirm ohne Kennung ist nicht zu finden")
    void ohneKennungGehtNicht() {
        assertThrows(IllegalArgumentException.class,
                () -> Bildschirm.of("  ", "titel", List.of()));
    }

    @Test
    @DisplayName("Ein Sinnbild ohne Vanilla-Rueckfall wird abgewiesen")
    void keinBildOhneRueckfall() {
        // ⚠️ Dieselbe Zusage wie ZanUis UiSymbol: ohne Rueckfall erscheint der Eintrag GAR NICHT,
        // wenn Nexo fehlt - ohne Ausnahme und ohne Logzeile. Der Rueckfall ist der Unterschied
        // zwischen "sieht anders aus" und "ist weg".
        assertThrows(NullPointerException.class,
                () -> Sinnbild.of("zanoria:x", null));
    }

    @Test
    @DisplayName("Ein Eintrag traegt einen SCHLUESSEL, keinen fertigen Satz")
    void eintraegeTragenSchluessel() {
        // ⚠️ Der Fall haelt eine Konvention fest, die die Naht mit ZanUI deckungsgleich macht:
        // dort wird ein Bildschirm bei JEDEM Oeffnen fuer die Sprache genau dieses Spielers
        // aufgeloest. Ein fertiger Satz im Eintrag friere die Sprache beim Anlegen ein.
        Eintrag e = Eintrag.of("lobby.kompass.relicwars",
                Sinnbild.of("zanoria:x", "STONE"), spieler -> { });
        assertEquals("lobby.kompass.relicwars", e.beschriftungSchluessel());
    }
}
```

- [ ] **Step 2: Lauf zur Bestätigung, daß er fehlschlägt**

Erwartet: `RC=1`, `cannot find symbol: class Bildschirm`.

- [ ] **Step 3: Die vier Typen**

`Sinnbild.java`:

```java
package net.zanoria.lobby.menue;

import java.util.Objects;

/**
 * Wie ein Eintrag aussieht: eine Nexo-Kennung mit einem Vanilla-Rueckfall.
 *
 * <p>⚠️ Der Rueckfall ist ein {@code String} und kein Bukkit-{@code Material}. Der Grund ist
 * gemessen: {@code new ItemStack(Material)} wirft ohne laufenden Server einen
 * {@code ExceptionInInitializerError} (Registry-Grenze). Waere hier ein {@code Material}, liesse
 * sich diese Klasse in keinem Einheitstest anlegen. Die Aufloesung passiert in
 * {@code menue.chest}, an genau einer Stelle.
 */
public record Sinnbild(String nexoKennung, String vanillaRueckfall) {

    public Sinnbild {
        Objects.requireNonNull(nexoKennung, "nexoKennung");
        Objects.requireNonNull(vanillaRueckfall,
                "vanillaRueckfall - ohne ihn erscheint der Eintrag GAR NICHT, wenn Nexo fehlt");
    }

    public static Sinnbild of(String nexoKennung, String vanillaRueckfall) {
        return new Sinnbild(nexoKennung, vanillaRueckfall);
    }
}
```

`Eintrag.java`:

```java
package net.zanoria.lobby.menue;

import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Ein anklickbarer Eintrag.
 *
 * <p>⚠️ <b>Kein Platz, keine Slot-Nummer.</b> ZanUI kennt nur Knoepfe; die Anordnung entscheidet
 * die Umsetzung. Wer hier eine Platznummer ergaenzt, macht die Naht fuer ZanUI unerfuellbar.
 */
public record Eintrag(String beschriftungSchluessel, Sinnbild sinnbild, Consumer<Player> handlung) {

    public Eintrag {
        Objects.requireNonNull(beschriftungSchluessel, "beschriftungSchluessel");
        Objects.requireNonNull(sinnbild, "sinnbild");
        Objects.requireNonNull(handlung, "handlung");
    }

    public static Eintrag of(String schluessel, Sinnbild sinnbild, Consumer<Player> handlung) {
        return new Eintrag(schluessel, sinnbild, handlung);
    }
}
```

`Bildschirm.java`:

```java
package net.zanoria.lobby.menue;

import java.util.List;
import java.util.Objects;

/**
 * Ein Menuebildschirm: Kennung, Titelschluessel, Eintraege.
 *
 * <p>Die Form spiegelt ZanUis {@code UiScreen} (Kennung + Titel + Knoepfe), damit ZanUI sie spaeter
 * eins zu eins erfuellt.
 *
 * <p>⚠️ <b>Er traegt Schluessel, keine fertigen Saetze.</b> Aufgeloest wird beim Oeffnen, fuer die
 * Sprache genau dieses Spielers. Das ist keine Stilfrage: ohne diese Regel waere die Naht mit
 * ZanUI nicht deckungsgleich, und der Tausch muesste jede Menuedefinition anfassen.
 */
public record Bildschirm(String kennung, String titelSchluessel, List<Eintrag> eintraege) {

    public Bildschirm {
        Objects.requireNonNull(kennung, "kennung");
        Objects.requireNonNull(titelSchluessel, "titelSchluessel");
        Objects.requireNonNull(eintraege, "eintraege");
        if (kennung.isBlank()) {
            throw new IllegalArgumentException("Ein Bildschirm ohne Kennung ist nicht zu finden");
        }
        eintraege = List.copyOf(eintraege);
    }

    public static Bildschirm of(String kennung, String titelSchluessel, List<Eintrag> eintraege) {
        return new Bildschirm(kennung, titelSchluessel, eintraege);
    }
}
```

`Menue.java`:

```java
package net.zanoria.lobby.menue;

import org.bukkit.entity.Player;

/**
 * <b>Die Naht.</b> Heute erfuellt von {@code ChestMenue}, spaeter von ZanUI.
 *
 * <h2>⚠️ Wozu es diese Schnittstelle gibt</h2>
 *
 * <p>ZanUI ist noch nicht so weit, und die Kisten-Umsetzung daneben ist ausdruecklich
 * <b>EXPERIMENTELL</b>. Die Auflage lautet: sie muss spaeter gegen ZanUI austauschbar sein,
 * <b>ohne die Hoerer anzufassen</b>. Deshalb kennt kein Hoerer dieses Repos einen Kisten-Typ -
 * das haelt {@code :kistenwaechter} maschinell fest, nicht dieser Kommentar.
 *
 * <p>⚠️ <b>Der Rueckgabewert ist keine Zierde.</b> Dieselbe Zusage wie {@code ZanUiService.open}:
 * die Umsetzung <b>wirft nicht</b>. Scheitert das Oeffnen, wird protokolliert und {@code false}
 * zurueckgegeben. Wer den Wert ignoriert, laeuft im Fehlerfall weiter, als waere das Menue offen -
 * das ist eine Entscheidung, aber eine sichtbare.
 */
public interface Menue {

    /**
     * Oeffnet einen Bildschirm fuer einen Spieler.
     *
     * @return ob er wirklich offen ist
     */
    boolean oeffne(Player spieler, Bildschirm bildschirm);
}
```

- [ ] **Step 4: Lauf zur Bestätigung**

Erwartet: `RC=0`, `DieNahtPasstZuZanUiTest` mit `tests="4" failures="0"`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/net/zanoria/lobby/menue/ \
        src/test/java/net/zanoria/lobby/menue/DieNahtPasstZuZanUiTest.java
git commit -m "Die Menue-Naht: Eintraege statt Slots, damit ZanUI sie erfuellen kann"
```

---

### Task 8: Kistenplan — die Rechnung, die ohne Server läuft

⚠️ **Warum diese Klasse getrennt existiert:** `Bukkit.createInventory(...)` faßt die Registry an
und ist ohne Server nicht fahrbar (dieselbe Grenze wie `new ItemStack`, Task 2). Läge die
Größenrechnung in `ChestMenue`, wäre sie **in keinem Einheitstest prüfbar** — und eine
Größenrechnung, die danebenliegt, wirft im Spiel beim Öffnen.

**Files:**
- Create: `src/main/java/net/zanoria/lobby/menue/chest/Kistenplan.java`
- Test: `src/test/java/net/zanoria/lobby/menue/chest/KistenplanTest.java`

- [ ] **Step 1: Den Fall schreiben**

```java
package net.zanoria.lobby.menue.chest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KistenplanTest {

    @Test
    @DisplayName("Die Groesse ist immer ein Vielfaches von neun")
    void groesseIstVielfachesVonNeun() {
        assertEquals(9, Kistenplan.groesseFuer(1));
        assertEquals(9, Kistenplan.groesseFuer(9));
        assertEquals(18, Kistenplan.groesseFuer(10));
        assertEquals(54, Kistenplan.groesseFuer(54));
    }

    @Test
    @DisplayName("Die Eintraege liegen der Reihe nach")
    void derReiheNach() {
        assertEquals(0, Kistenplan.platzFuer(0));
        assertEquals(1, Kistenplan.platzFuer(1));
        assertEquals(11, Kistenplan.platzFuer(11));
    }

    @Test
    @DisplayName("⚠️ Mehr als 54 Eintraege werden LAUT abgewiesen, nicht abgeschnitten")
    void zuVieleWerdenAbgewiesen() {
        // ⚠️ Bukkit kann keine Kiste ueber 54 Plaetze. Stillschweigend abschneiden hiesse: der
        // 55. Eintrag existiert im Code, ist im Spiel unsichtbar, und niemand erfaehrt es.
        // Ein Fehler beim ZUSAMMENBAUEN ist billiger als ein fehlender Knopf vor dem Spieler.
        assertThrows(IllegalArgumentException.class, () -> Kistenplan.groesseFuer(55));
    }

    @Test
    @DisplayName("Null Eintraege sind kein Bildschirm")
    void nullEintraegeGehenNicht() {
        assertThrows(IllegalArgumentException.class, () -> Kistenplan.groesseFuer(0));
    }
}
```

- [ ] **Step 2: Lauf zur Bestätigung, daß er fehlschlägt**

Erwartet: `RC=1`, `cannot find symbol: class Kistenplan`.

- [ ] **Step 3: Die Umsetzung**

```java
package net.zanoria.lobby.menue.chest;

/**
 * Die Rechnung hinter {@link ChestMenue} - <b>bukkitfrei</b>.
 *
 * <p>⚠️ Sie liegt getrennt, weil {@code Bukkit.createInventory} die Registry anfasst und ohne
 * laufenden Server wirft. Laege die Groessenrechnung drueben, waere sie in keinem Einheitstest
 * pruefbar - und eine Groessenrechnung, die danebenliegt, wirft im Spiel beim Oeffnen.
 *
 * <p>⚠️ <b>Kein Layout.</b> Entschieden vom Betreiber am 2026-09-03: die Menues werden mit ZanUI
 * ohnehin neu gebaut, bis dahin muessen sie funktionieren und austauschbar sein, sonst nichts.
 * Wer hier Rahmen, Luecken oder Platzwuensche ergaenzt, baut Arbeit, die der Tausch wegwirft.
 */
public final class Kistenplan {

    /** Bukkits Obergrenze fuer eine Kiste. */
    public static final int MAX_PLAETZE = 54;

    private static final int REIHE = 9;

    private Kistenplan() {
    }

    /** Die Inventargroesse fuer so viele Eintraege - aufgerundet auf volle Neunerreihen. */
    public static int groesseFuer(int eintraege) {
        if (eintraege <= 0) {
            throw new IllegalArgumentException(
                    "Ein Bildschirm ohne Eintraege ist kein Bildschirm (bekommen: " + eintraege + ")");
        }
        if (eintraege > MAX_PLAETZE) {
            // ⚠️ LAUT abweisen, nicht abschneiden. Siehe KistenplanTest.
            throw new IllegalArgumentException(
                    "Eine Kiste fasst hoechstens " + MAX_PLAETZE + " Eintraege, gefordert waren "
                            + eintraege + ". Stillschweigend abschneiden hiesse: der ueberzaehlige"
                            + " Eintrag existiert im Code und ist im Spiel unsichtbar.");
        }
        return ((eintraege + REIHE - 1) / REIHE) * REIHE;
    }

    /** Der Platz des n-ten Eintrags. Der Reihe nach, kein Layout. */
    public static int platzFuer(int index) {
        if (index < 0 || index >= MAX_PLAETZE) {
            throw new IllegalArgumentException("Platz " + index + " liegt ausserhalb der Kiste");
        }
        return index;
    }
}
```

- [ ] **Step 4: Lauf zur Bestätigung**

Erwartet: `RC=0`, `KistenplanTest` mit `tests="4" failures="0"`.

- [ ] **Step 5: Mutation M5 — die Aufrundung fällt weg**

Ändere `((eintraege + REIHE - 1) / REIHE) * REIHE` zu `(eintraege / REIHE) * REIHE`.
Erwartet: **RC=1**, `groesseIstVielfachesVonNeun` rot (1 Eintrag ergäbe Größe 0).
Zurücknehmen, erneut fahren: `RC=0`.

- [ ] **Step 6: Mutation M6 — die Obergrenze wird stillschweigend abgeschnitten**

Ersetze den `> MAX_PLAETZE`-Wurf durch `eintraege = MAX_PLAETZE;`.
Erwartet: **RC=1**, `zuVieleWerdenAbgewiesen` rot. Zurücknehmen.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/net/zanoria/lobby/menue/chest/Kistenplan.java \
        src/test/java/net/zanoria/lobby/menue/chest/KistenplanTest.java
git commit -m "Kistenplan: die Rechnung liegt bukkitfrei, damit sie pruefbar ist

Mutationen M5 (Aufrundung weg) und M6 (stillschweigend abschneiden)
gefahren und rot, zurueckgenommen gruen."
```

---

### Task 9: ChestMenue — die experimentelle Umsetzung

⚠️ **Diese Klasse ist der einzige Ort im Repo, der einen Kisten-Typ berühren darf.** Das hält
Task 10 maschinell fest.

⚠️ **Ihre Wirkung ist ohne Server NICHT prüfbar** — und die Messung aus Task 2 nennt dafür **zwei
verschiedene Gründe**, die man nicht zusammenwerfen darf:

```
REGISTRYGRENZE: NICHT   new ItemStack(Material.COMPASS)
                        -> ExceptionInInitializerError <- IllegalStateException:
                           No RegistryAccess implementation found
REGISTRYGRENZE: NICHT   Bukkit.createInventory(null, 9, "titel")
                        -> NullPointerException: "org.bukkit.Bukkit.server" is null
```

Das zweite ist die **Server-Singleton**-Grenze und ließe sich grundsätzlich stellen (ein
`Bukkit.server`-Platzhalter per Feld). Das erste ist die **echte Registry-Grenze** und läßt sich
nicht stellen. **Weil `ItemStack` hart blockiert, bleibt das Öffnen trotzdem im Erstlauf** — aber
wer die beiden verwechselt, sucht später am falschen Ende.

Geprüft wird hier die **Auswahl** des Materials; das Öffnen selbst beantwortet der Erstlauf
(Task 18). Wer dafür einen Einheitstest baut, der das Inventar selbst stellt, mißt die Attrappe
statt der Klasse.

**Files:**
- Create: `src/main/java/net/zanoria/lobby/menue/chest/Materialwahl.java`
- Create: `src/main/java/net/zanoria/lobby/menue/chest/ChestMenue.java`
- Test: `src/test/java/net/zanoria/lobby/menue/chest/MaterialwahlTest.java`

- [ ] **Step 1: Den Fall für die Materialwahl schreiben**

```java
package net.zanoria.lobby.menue.chest;

import net.zanoria.lobby.menue.Sinnbild;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ⚠️ Was hier geprueft wird, ist die AUSWAHL - nicht das Erzeugen des ItemStacks.
 *
 * <p>Gemessen (Task 2): {@code new ItemStack(Material)} wirft ohne laufenden Server. Die Auswahl
 * ist eine Zeichenkettenfrage und laeuft ueberall; das Erzeugen beantwortet der Erstlauf.
 */
class MaterialwahlTest {

    @Test
    @DisplayName("Ist der Rueckfall ein bekanntes Material, wird es genommen")
    void bekanntesMaterial() {
        assertEquals("COMPASS",
                Materialwahl.rueckfallOder(Sinnbild.of("zanoria:x", "COMPASS"), "STONE"));
    }

    @Test
    @DisplayName("⚠️ Ist der Rueckfall ein TIPPFEHLER, wird der Notnagel genommen - nicht null")
    void tippfehlerFaelltAufNotnagel() {
        // ⚠️ Material.valueOf("KOMPASS") wuerde werfen, und der Wurf kaeme im Hoererrumpf an -
        // also mitten im Klick eines Spielers. Ein Eintrag, der wie Stein aussieht, ist besser
        // als ein Menue, das sich nicht oeffnet.
        assertEquals("STONE",
                Materialwahl.rueckfallOder(Sinnbild.of("zanoria:x", "KOMPASS"), "STONE"));
    }

    @Test
    @DisplayName("Ein leerer Rueckfall nimmt ebenfalls den Notnagel")
    void leererRueckfall() {
        assertEquals("STONE",
                Materialwahl.rueckfallOder(Sinnbild.of("zanoria:x", "   "), "STONE"));
    }
}
```

- [ ] **Step 2: Lauf zur Bestätigung, daß er fehlschlägt**

Erwartet: `RC=1`, `cannot find symbol: class Materialwahl`.

- [ ] **Step 3: Die Materialwahl**

```java
package net.zanoria.lobby.menue.chest;

import net.zanoria.lobby.menue.Sinnbild;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Welcher Vanilla-Materialname fuer ein Sinnbild genommen wird - als <b>Zeichenkette</b>.
 *
 * <p>⚠️ Getrennt von {@link ChestMenue}, damit die Auswahl ohne laufenden Server pruefbar ist.
 * {@code Material.valueOf} selbst ist baubar (in NexusStrike am 2026-09-02 gemessen), aber
 * {@code new ItemStack(Material)} nicht - die Registry-Grenze laeuft zwischen beiden.
 */
public final class Materialwahl {

    /**
     * Die Namen aller Bukkit-Materialien - einmal beim Laden dieser Klasse.
     *
     * <p>⚠️ Ueber {@code Material.values()}, nicht ueber {@code valueOf} je Aufruf: ein
     * {@code valueOf} auf einen Tippfehler <b>wirft</b>, und der Wurf kaeme im Hoererrumpf an,
     * also mitten im Klick eines Spielers.
     */
    private static final Set<String> BEKANNT = Stream.of(org.bukkit.Material.values())
            .map(Enum::name)
            .collect(Collectors.toUnmodifiableSet());

    private Materialwahl() {
    }

    /**
     * Der Vanilla-Rueckfall des Sinnbilds, oder der Notnagel, wenn er unbekannt oder leer ist.
     *
     * <p>⚠️ Fallen statt werfen ist hier richtig: ein Eintrag, der wie Stein aussieht, ist besser
     * als ein Menue, das sich beim Klick nicht oeffnet. Der Aufrufer protokolliert den Fall.
     */
    public static String rueckfallOder(Sinnbild sinnbild, String notnagel) {
        String kandidat = sinnbild.vanillaRueckfall();
        if (kandidat == null || kandidat.isBlank()) {
            return notnagel;
        }
        String gross = kandidat.trim().toUpperCase(Locale.ROOT);
        return BEKANNT.contains(gross) ? gross : notnagel;
    }
}
```

⚠️ **`Material.values()` beim Klassenladen** — falls Task 2 gemessen hat, daß schon das die
Registry anfaßt und wirft, wird `BEKANNT` in eine `static` Methode mit `try/catch (Throwable)`
verlegt, die bei Wurf eine leere Menge liefert. **Erst messen, dann entscheiden.**

- [ ] **Step 4: ChestMenue**

```java
package net.zanoria.lobby.menue.chest;

import net.zanoria.lobby.menue.Bildschirm;
import net.zanoria.lobby.menue.Eintrag;
import net.zanoria.lobby.menue.Menue;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * <b>EXPERIMENTELL.</b> Die Naht {@link Menue} ueber ein Kisteninventar.
 *
 * <h2>⚠️ Diese Klasse ist zum Wegwerfen gebaut</h2>
 *
 * <p>ZanUI ist noch nicht so weit. Sobald es das ist, wird diese Klasse durch eine
 * ZanUI-Umsetzung ersetzt - <b>ohne dass ein Hoerer angefasst wird</b>. Das ist die tragende
 * Auflage, und sie haelt nur, solange kein Kisten-Typ ausserhalb dieses Pakets steht:
 * {@code :kistenwaechter} macht den Bau rot, wenn doch.
 *
 * <p>⚠️ <b>Kein Layout.</b> Die Eintraege liegen der Reihe nach. Wer hier Rahmen oder Luecken
 * ergaenzt, baut Arbeit, die der Tausch wegwirft.
 *
 * <p>⚠️ <b>Sie wirft nicht.</b> Dieselbe Zusage wie {@code ZanUiService.open}: scheitert das
 * Oeffnen, wird protokolliert und {@code false} zurueckgegeben. Eine durchgereichte Ausnahme kaeme
 * beim Spieler als "An unexpected error occurred" an, und niemand erfuehre, was.
 */
public final class ChestMenue implements Menue {

    /** Der Notnagel, wenn ein Vanilla-Rueckfall unbekannt ist. */
    private static final String NOTNAGEL = "STONE";

    private final Logger log;
    private final Map<UUID, Bildschirm> offen = new HashMap<>();

    public ChestMenue(Logger log) {
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public boolean oeffne(Player spieler, Bildschirm bildschirm) {
        try {
            int groesse = Kistenplan.groesseFuer(bildschirm.eintraege().size());
            // ⚠️ Der Titel ist hier noch der SCHLUESSEL. Die Aufloesung ueber ZanLang kommt in
            // Task 13 dazu; bis dahin steht der Schluessel sichtbar da - das ist Absicht, ein
            // sichtbarer Schluessel ist ein gemeldeter Mangel, ein erfundener Satz nicht.
            Inventory kiste = Bukkit.createInventory(null, groesse, bildschirm.titelSchluessel());

            for (int i = 0; i < bildschirm.eintraege().size(); i++) {
                Eintrag eintrag = bildschirm.eintraege().get(i);
                kiste.setItem(Kistenplan.platzFuer(i), stapelFuer(eintrag));
            }

            spieler.openInventory(kiste);
            offen.put(spieler.getUniqueId(), bildschirm);
            return true;
        } catch (RuntimeException | LinkageError fehler) {
            // ⚠️ LinkageError mitfangen: die Registry-Grenze wirft ExceptionInInitializerError,
            // und das ist ein Error, keine RuntimeException. Ein catch(RuntimeException) allein
            // liesse genau den Fall durch, der hier am wahrscheinlichsten ist.
            log.error("Bildschirm '{}' liess sich fuer {} nicht oeffnen.",
                    bildschirm.kennung(), spieler.getName(), fehler);
            return false;
        }
    }

    /** Der Bildschirm, den dieser Spieler offen hat - oder {@code null}. */
    public Bildschirm offenFuer(UUID spieler) {
        return offen.get(spieler);
    }

    public void schliesse(UUID spieler) {
        offen.remove(spieler);
    }

    private ItemStack stapelFuer(Eintrag eintrag) {
        String name = Materialwahl.rueckfallOder(eintrag.sinnbild(), NOTNAGEL);
        if (!name.equals(eintrag.sinnbild().vanillaRueckfall())) {
            log.warn("Sinnbild '{}' nennt den unbekannten Rueckfall '{}' - genommen wird {}.",
                    eintrag.sinnbild().nexoKennung(), eintrag.sinnbild().vanillaRueckfall(), name);
        }
        return new ItemStack(Material.valueOf(name));
    }
}
```

- [ ] **Step 5: Voller Lauf**

Erwartet: `RC=0`, `MaterialwahlTest` mit `tests="3" failures="0"`.

- [ ] **Step 6: Mutation M7 — der Notnagel fällt weg**

Ändere `return BEKANNT.contains(gross) ? gross : notnagel;` zu `return gross;`.
Erwartet: **RC=1**, `tippfehlerFaelltAufNotnagel` rot. Zurücknehmen.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/net/zanoria/lobby/menue/chest/ \
        src/test/java/net/zanoria/lobby/menue/chest/MaterialwahlTest.java
git commit -m "ChestMenue: die experimentelle Umsetzung der Naht

Sie wirft nicht (LinkageError mitgefangen - die Registry-Grenze ist ein
Error, keine RuntimeException). Mutation M7 gefahren und rot."
```

---

### Task 10: Der Kistenwächter — kein Kisten-Typ außerhalb der Umsetzung

⚠️ **Das ist der Wächter, der die tragende Auflage hält.** Ohne ihn ist „experimentell" nur ein
Wort: in drei Monaten steht ein `Inventory` im Hörer, und der Tausch gegen ZanUI ist nicht mehr
ohne Hörerberührung möglich.

**Files:**
- Modify: `build.gradle`

- [ ] **Step 1: Den Task schreiben**

Ans Ende von `build.gradle`, vor dem Befehlswächter-Block:

```groovy
// ═══════════════════════════════════════════════════════════════════════════════
//  KISTENWAECHTER - kein Kisten-Typ ausserhalb von menue.chest
//
//  ⚠️ Er haelt die tragende Auflage: die Menues muessen spaeter gegen ZanUI austauschbar sein,
//  OHNE die Hoerer anzufassen. ZanUI kennt keine Slots (Paper-Dialog-API, gemessen 2026-09-03);
//  ein Inventory-Typ im Hoerer macht den Tausch unmoeglich.
//
//  ⚠️ Er liest den BYTECODE, nicht den Quelltext. Eine Quelltextsuche hat in diesem Stack schon
//  einmal einen Kommentar fuer eine Anmeldung gehalten. Im Konstantenpool steht der Typ so oder
//  so - ob in einem Import, einer Signatur oder einem Aufruf.
//
//  ⚠️ Er macht den Bau ROT, anders als der Platzhalterwaechter. Begruendung: ein Kisten-Typ im
//  Hoerer ist kein zu erledigender Rest, sondern der Bruch selbst. Und er waere nicht DAUERHAFT
//  rot - nur bei einem echten Verstoss. Die Ermuedungsgefahr eines immer roten Baus besteht hier
//  also nicht.
// ═══════════════════════════════════════════════════════════════════════════════
def kistenwaechter = tasks.register('kistenwaechter') {
    dependsOn tasks.named('classes')
    def klassenVerzeichnis = layout.buildDirectory.dir('classes/java/main')
    inputs.dir(klassenVerzeichnis)
    outputs.upToDateWhen { false }

    doLast {
        // ⚠️ ItemStack steht hier ABSICHTLICH NICHT drin, und das ist eine Entscheidung, keine
        // Luecke. Die Auflage betrifft MENUES, nicht Gegenstaende: die Hotbar-Items bleiben auch
        // nach dem Tausch gegen ZanUI ItemStacks - ZanUI ersetzt die Menues, nicht das Inventar
        // des Spielers. Ein Verbot von ItemStack wuerde Stueck 2 unbaubar machen und haette mit
        // der Austauschbarkeit nichts zu tun.
        def verboten = [
                'org/bukkit/inventory/Inventory',
                'org/bukkit/event/inventory/InventoryClickEvent',
                'org/bukkit/event/inventory/InventoryCloseEvent',
                'org/bukkit/inventory/InventoryView',
        ]
        // ⚠️ Das Umsetzungspaket. Dort SOLLEN die Typen stehen - und der Waechter verlangt es,
        // siehe die Positivkontrolle unten.
        def erlaubtesPaket = 'net/zanoria/lobby/menue/chest/'

        def wurzel = klassenVerzeichnis.get().asFile
        def klassen = []
        wurzel.eachFileRecurse { if (it.name.endsWith('.class')) klassen << it }
        if (klassen.isEmpty()) {
            throw new GradleException(
                    "KISTENWAECHTER: keine uebersetzten Klassen unter ${wurzel} - dann ist" +
                    " 'kein Kisten-Typ gefunden' kein Befund, sondern ein leerer Sucher.")
        }

        def treffer = [:]
        def imPaket = 0
        klassen.each { datei ->
            def pfad = wurzel.toPath().relativize(datei.toPath()).toString().replace('\\', '/')
            def bytes = datei.getBytes()
            def text = new String(bytes, 'ISO-8859-1')
            def gefunden = verboten.findAll { text.contains(it) }
            if (gefunden.isEmpty()) return
            if (pfad.startsWith(erlaubtesPaket)) {
                imPaket++
            } else {
                treffer[pfad] = gefunden
            }
        }

        // ⚠️ POSITIVKONTROLLE. Ohne sie waere "keine Funde ausserhalb" gratis gruen, sobald der
        // Suchpfad, der Paketname oder die Typnamen nicht mehr stimmen - ein Sucher, der nichts
        // sieht, meldet sonst ein geloestes Problem.
        if (imPaket == 0) {
            throw new GradleException(
                    "KISTENWAECHTER: im Umsetzungspaket '${erlaubtesPaket}' steht KEIN einziger" +
                    " Kisten-Typ. Entweder ist die Umsetzung weg, oder die Suche findet nichts" +
                    " mehr. In beiden Faellen ist die Meldung 'keine Funde ausserhalb' wertlos.")
        }

        logger.lifecycle("KISTENWAECHTER: ${klassen.size()} Klasse(n) geprueft," +
                " ${imPaket} im Umsetzungspaket (Positivkontrolle bestanden).")

        if (!treffer.isEmpty()) {
            treffer.each { pfad, typen ->
                logger.error("KISTENWAECHTER:   ${pfad} -> ${typen.join(', ')}")
            }
            throw new GradleException(
                    "KISTENWAECHTER: ${treffer.size()} Klasse(n) ausserhalb von" +
                    " '${erlaubtesPaket}' fassen einen Kisten-Typ an.\n" +
                    "SCHADEN: die Menues sind dann NICHT mehr gegen ZanUI austauschbar, ohne" +
                    " diese Klassen anzufassen. ZanUI kennt keine Slots - es fuehrt die" +
                    " Paper-Dialog-API (Kennung + Titel + Knoepfe). Wer ein Menue braucht," +
                    " ruft Menue.oeffne(spieler, bildschirm).")
        }
    }
}
tasks.named('check') { dependsOn kistenwaechter }
```

- [ ] **Step 2: Fahren — er muß grün sein und seine Positivkontrolle melden**

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-21"
rm -rf build/test-results
./gradlew check --continue > /tmp/lauf.log 2>&1; echo "RC=$?"
```
Erwartet: `RC=0` und im Log eine Zeile
`KISTENWAECHTER: N Klasse(n) geprueft, M im Umsetzungspaket (Positivkontrolle bestanden).`
mit **M ≥ 1**.

- [ ] **Step 3: Positivkontrolle K1 — ein Verstoß wird wirklich gefunden**

Lege vorübergehend `src/main/java/net/zanoria/lobby/Probeverstoss.java` an:

```java
package net.zanoria.lobby;

public final class Probeverstoss {
    public org.bukkit.inventory.Inventory kiste;
}
```

Fahren. Erwartet: **RC=1**, im Log
`KISTENWAECHTER:   net/zanoria/lobby/Probeverstoss.class -> org/bukkit/inventory/Inventory`.
**Datei danach löschen** und erneut fahren: `RC=0`.

⚠️ **Diesen Schritt nicht überspringen.** Ein Wächter, der nie rot war, ist eine Behauptung.

- [ ] **Step 4: Positivkontrolle K2 — die eigene Suche wird geprüft**

Ändere `erlaubtesPaket` vorübergehend auf `'net/zanoria/lobby/gibtesnicht/'`.
Erwartet: **RC=1** mit der Meldung *„im Umsetzungspaket … steht KEIN einziger Kisten-Typ"*.
Zurücknehmen.

⚠️ **Das ist die Kontrolle der Kontrolle.** Ohne sie wäre der Wächter grün, sobald jemand das
Paket umbenennt — und meldete dabei „keine Funde", als wäre alles in Ordnung.

- [ ] **Step 5: Commit**

```bash
git add build.gradle
git commit -m "Kistenwaechter: kein Kisten-Typ ausserhalb der Umsetzung

Liest den Bytecode, nicht den Quelltext. Zwei Positivkontrollen
gefahren: ein untergeschobener Verstoss wird rot (K1), und ein falscher
Paketpfad macht ihn rot statt gratis gruen (K2)."
```

---

## Phase 3 — Stück 2: Die fünf Hotbar-Items

### Task 11: Das Enum Hotbarplatz

**Files:**
- Create: `src/main/java/net/zanoria/lobby/hotbar/Hotbarplatz.java`
- Test: `src/test/java/net/zanoria/lobby/hotbar/DieHotbarNenntIhrePlatzhalterTest.java`

- [ ] **Step 1: Den gepinnten Fall schreiben**

```java
package net.zanoria.lobby.hotbar;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>Die Platzhalter werden gezaehlt, benannt und gepinnt</b> - damit keiner davon stehenbleibt.
 *
 * <h2>⚠️ Wogegen dieser Fall steht</h2>
 *
 * <p>Ein Platzhalter, den niemand meldet, ist ein Platzhalter fuer immer. Er faellt nicht auf, weil
 * das Spiel laeuft: ein Kompass kann auch ein Kompass sein, es gibt keine Ausnahme und keine
 * Logzeile.
 *
 * <h2>⚠️ Warum eine gepinnte MENGE und nicht nur eine Zahl</h2>
 *
 * <p>Eine Zahl ("5 Platzhalter") faengt den Fall nicht, in dem jemand einen loest und gleichzeitig
 * einen neuen Eintrag anlegt. Gepinnt sind deshalb die NAMEN.
 *
 * <p>⚠️ <b>Dieser Fall wird ROT, wenn die Deckung BESSER wird</b> - also wenn jemand eine Textur
 * liefert und {@code istPlatzhalter()} auf {@code false} setzt, ohne den Eintrag hier auszutragen.
 * Das ist Absicht: eine geloeste Luecke, deren Beschreibung stehenbleibt, ist dieselbe Luege wie
 * ein Platzhalter, den niemand meldet.
 */
class DieHotbarNenntIhrePlatzhalterTest {

    /**
     * ⚠️ <b>Stand 2026-09-03: ALLE fuenf Eintraege sind Platzhalter.</b> Es gibt fuer die Lobby
     * heute keine einzige eigene Textur.
     *
     * <p>Wer eine liefert, nimmt den Eintrag HIER heraus und setzt {@code istPlatzhalter()} im
     * Enum auf {@code false}. Beides zusammen - sonst wird dieser Fall rot, und das ist der
     * eingebaute Weg, es nicht zu vergessen.
     */
    private static final Set<String> ERWARTETE_PLATZHALTER = Set.of(
            "KOMPASS", "RUCKSACK", "BATTLEPASS", "SOZIAL", "EINSTELLUNGEN");

    @Test
    @DisplayName("⚠️ Die Menge der Platzhalter ist gepinnt - keiner kommt und keiner geht lautlos")
    void diePlatzhalterMengeIstGepinnt() {
        Set<String> tatsaechlich = Hotbarplatz.platzhalter().stream()
                .map(Enum::name)
                .collect(Collectors.toCollection(TreeSet::new));

        assertEquals(new TreeSet<>(ERWARTETE_PLATZHALTER), tatsaechlich,
                "⚠️ Die Menge der Platzhalter hat sich geaendert.\n"
                        + "  KAM DAZU  -> jemand hat einen Eintrag angelegt, ohne eine Textur zu"
                        + " liefern. Eintragen und weitermachen ist in Ordnung; lautlos ist es"
                        + " nicht.\n"
                        + "  FIEL WEG  -> jemand hat eine Textur geliefert. Dann gehoert der"
                        + " Eintrag HIER heraus, im selben Zug.");
    }

    @Test
    @DisplayName("⚠️ Jeder Platz kommt genau einmal vor")
    void diePlaetzeSindEindeutig() {
        // ⚠️ Zwei Eintraege auf demselben Platz hiessen: einer ist im Spiel unsichtbar - ohne
        // Ausnahme und ohne Logzeile. Genau die Sorte Fehler, die man erst im Spiel sieht.
        Set<Integer> gesehen = new HashSet<>();
        for (Hotbarplatz platz : Hotbarplatz.values()) {
            assertTrue(gesehen.add(platz.platz()),
                    "⚠️ Der Platz " + platz.platz() + " ist doppelt belegt (" + platz + ")."
                            + " Einer der beiden Eintraege waere im Spiel unsichtbar.");
        }
    }

    @Test
    @DisplayName("Die Plaetze sind die vom Auftrag geforderten: 0, 1, 2, 7, 8")
    void diePlaetzeStimmen() {
        assertEquals(0, Hotbarplatz.KOMPASS.platz());
        assertEquals(1, Hotbarplatz.RUCKSACK.platz());
        assertEquals(2, Hotbarplatz.BATTLEPASS.platz());
        assertEquals(7, Hotbarplatz.SOZIAL.platz());
        assertEquals(8, Hotbarplatz.EINSTELLUNGEN.platz());
    }

    @Test
    @DisplayName("Jeder Platz liegt in der Hotbar (0 bis 8)")
    void alleInDerHotbar() {
        for (Hotbarplatz platz : Hotbarplatz.values()) {
            assertTrue(platz.platz() >= 0 && platz.platz() <= 8,
                    "⚠️ " + platz + " liegt auf " + platz.platz() + " - das ist keine Hotbar."
                            + " Der Eintrag laege im Inventar und waere ohne Oeffnen unerreichbar.");
        }
    }

    @Test
    @DisplayName("Jeder Eintrag hat eine Nexo-Kennung UND einen Vanilla-Rueckfall")
    void jederEintragHatBeideSeiten() {
        Set<String> kennungen = new HashSet<>();
        for (Hotbarplatz platz : Hotbarplatz.values()) {
            assertFalse(platz.sinnbild().nexoKennung().isBlank(),
                    "⚠️ " + platz + " hat keine Nexo-Kennung. Dann gibt es nichts, worauf eine"
                            + " Textur je zeigen koennte.");
            assertTrue(platz.sinnbild().nexoKennung().startsWith("zanoria:"),
                    "⚠️ " + platz + " traegt die Kennung '" + platz.sinnbild().nexoKennung()
                            + "'. Ohne den Namensraum kollidiert sie mit den Eintraegen der"
                            + " Spielmodi - Nexo fuehrt EINEN Bestand fuer das ganze Netz.");
            assertFalse(platz.sinnbild().vanillaRueckfall().isBlank(),
                    "⚠️ " + platz + " hat keinen Vanilla-Rueckfall. Fehlt Nexo, erscheint dieses"
                            + " Item dann GAR NICHT - ohne Ausnahme und ohne Logzeile.");
            assertTrue(kennungen.add(platz.sinnbild().nexoKennung()),
                    "⚠️ Die Kennung '" + platz.sinnbild().nexoKennung() + "' kommt zweimal vor -"
                            + " zwei Items saehen im Spiel identisch aus.");
        }
    }

    @Test
    @DisplayName("⚠️ Kein gezeichneter Text traegt ein Warnzeichen")
    void keinWarnzeichenImGezeichnetenText() {
        // ⚠️ Das ⚠️ ist ZWEI Codepunkte (U+26A0 + U+FE0F). Minecraft zeichnet U+FE0F als
        // Leerkasten. In Kommentaren ist es richtig, in gezeichneten Zeichenketten nie.
        for (Hotbarplatz platz : Hotbarplatz.values()) {
            assertFalse(platz.beschriftungSchluessel().contains("⚠")
                            || platz.beschriftungSchluessel().contains("️"),
                    "⚠️ " + platz + " traegt ein Warnzeichen im Beschriftungsschluessel."
                            + " Minecraft zeichnet U+FE0F als Leerkasten.");
        }
    }
}
```

- [ ] **Step 2: Lauf zur Bestätigung, daß er fehlschlägt**

Erwartet: `RC=1`, `cannot find symbol: class Hotbarplatz`.

- [ ] **Step 3: Das Enum**

```java
package net.zanoria.lobby.hotbar;

import net.zanoria.lobby.menue.Sinnbild;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Die fuenf festen Hotbar-Plaetze der Lobby - <b>und welche davon noch Platzhalter sind</b>.
 *
 * <h2>⚠️ Warum die Platzhalter-Markierung im WERT steht und nicht im Kommentar</h2>
 *
 * <p>Es gibt heute keine eigenen Texturen. Das ist in Ordnung - <b>nicht</b> in Ordnung waere,
 * dass sie in drei Monaten immer noch fehlen und niemand es merkt, weil das Spiel laeuft und ein
 * Kompass eben auch ein Kompass ist. Ein Platzhalter ohne Meldung ist ein Platzhalter fuer immer.
 *
 * <p>Deshalb traegt jeder Eintrag sein {@link #istPlatzhalter()} als <b>Feld</b>, und der
 * Gradle-Task {@code :platzhalterwaechter} liest es bei jedem Bau aus dem uebersetzten Enum.
 *
 * <p>⚠️ <b>Der Vanilla-Rueckfall ist ebenfalls geraten</b>, nicht nur die Textur. Kein Dokument
 * nennt fuer diese fuenf Items einen Gegenstand; {@code BUNDLE} und {@code COMPARATOR} sind eine
 * Wahl. Sie fallen unter dasselbe {@code istPlatzhalter()}. Sichtbar wird der Rueckfall genau
 * dann, wenn Nexo fehlt - also im Stoerungsfall, und dort ist "sieht anders aus" das gewollte
 * Verhalten gegenueber "ist weg".
 *
 * <p>⚠️ <b>Die Plaetze sind eine Vorgabe, keine Ableitung.</b> 0/1/2 und 7/8 lassen die Mitte
 * frei; das ist die Entscheidung des Betreibers und keine Rechnung.
 */
public enum Hotbarplatz {

    // ⚠️ DIE SCHLUESSEL SIND GEMESSEN, NICHT ERFUNDEN. Alle fuenf stehen seit laengerem in
    // ZanLangs translations/de_de.json - mit fertigen Uebersetzungen: "Wegweiser", "Rucksack",
    // "Battle Pass", "Freunde", "Einstellungen". Wer hier eigene Schluessel anlegt (etwa
    // "lobby.hotbar.kompass"), erzeugt eine zweite Wahrheit und laesst die vorhandenen
    // verwaisen - im Spiel stuende dann der rohe Schluessel als Item-Name.
    //
    // ⚠️ Deshalb heisst der Eintrag SOZIAL, sein Schluessel aber ...friends: der Auftrag nennt
    // das Item "Social", die bestehende und uebersetzte Entscheidung heisst "Freunde". Die
    // Uebersetzung gewinnt, weil sie schon vor dem Spieler steht.
    KOMPASS(0, "zanoria:lobby_kompass", "COMPASS", "lobby.hotbar.compass", true),
    RUCKSACK(1, "zanoria:lobby_rucksack", "BUNDLE", "lobby.hotbar.backpack", true),
    BATTLEPASS(2, "zanoria:lobby_battlepass", "BOOK", "lobby.hotbar.battlepass", true),
    SOZIAL(7, "zanoria:lobby_sozial", "PLAYER_HEAD", "lobby.hotbar.friends", true),
    EINSTELLUNGEN(8, "zanoria:lobby_einstellungen", "COMPARATOR", "lobby.hotbar.settings", true);

    private final int platz;
    private final Sinnbild sinnbild;
    private final String beschriftungSchluessel;
    private final boolean platzhalter;

    Hotbarplatz(int platz, String nexoKennung, String vanillaRueckfall,
                String beschriftungSchluessel, boolean platzhalter) {
        this.platz = platz;
        this.sinnbild = Sinnbild.of(nexoKennung, vanillaRueckfall);
        this.beschriftungSchluessel =
                Objects.requireNonNull(beschriftungSchluessel, "beschriftungSchluessel");
        this.platzhalter = platzhalter;
    }

    /** Der feste Platz in der Hotbar, 0 bis 8. */
    public int platz() {
        return platz;
    }

    public Sinnbild sinnbild() {
        return sinnbild;
    }

    /** Der ZanLang-Schluessel des Item-Namens. ⚠️ Ein Schluessel, kein fertiger Satz. */
    public String beschriftungSchluessel() {
        return beschriftungSchluessel;
    }

    /** Die Kennung des Bildschirms, den dieses Item oeffnet. */
    public String bildschirmKennung() {
        return "lobby." + name().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Ob dieser Eintrag noch auf einen Platzhalter zeigt - also ob die Textur noch fehlt.
     *
     * <p>⚠️ Wer eine echte Textur liefert, setzt hier {@code false} <b>und</b> traegt den Eintrag
     * in {@code DieHotbarNenntIhrePlatzhalterTest} aus. Beides zusammen, sonst wird der Fall rot -
     * das ist Absicht.
     */
    public boolean istPlatzhalter() {
        return platzhalter;
    }

    /** Alle Eintraege, die noch auf Platzhalter zeigen. Der Waechter liest genau das. */
    public static List<Hotbarplatz> platzhalter() {
        return Arrays.stream(values()).filter(Hotbarplatz::istPlatzhalter).toList();
    }
}
```

- [ ] **Step 4: Lauf zur Bestätigung**

Erwartet: `RC=0`, `DieHotbarNenntIhrePlatzhalterTest` mit `tests="6" failures="0"`.

- [ ] **Step 5: Mutation M8 — ein Eintrag behauptet, kein Platzhalter zu sein**

Setze bei `RUCKSACK` das letzte Argument auf `false`.
Erwartet: **RC=1**, `diePlatzhalterMengeIstGepinnt` rot mit der `FIEL WEG`-Meldung.
Zurücknehmen.

- [ ] **Step 6: Mutation M9 — zwei Einträge auf demselben Platz**

Setze `SOZIAL` auf Platz `0`.
Erwartet: **RC=1**, `diePlaetzeSindEindeutig` **und** `diePlaetzeStimmen` rot. Zurücknehmen.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/net/zanoria/lobby/hotbar/Hotbarplatz.java \
        src/test/java/net/zanoria/lobby/hotbar/DieHotbarNenntIhrePlatzhalterTest.java
git commit -m "Hotbarplatz: fuenf feste Plaetze, alle fuenf als Platzhalter markiert

Mutationen M8 (Platzhalter leugnen) und M9 (doppelter Platz) gefahren
und rot, zurueckgenommen gruen."
```

---

### Task 12: Der Platzhalterwächter

**Files:**
- Modify: `build.gradle`

- [ ] **Step 1: Den Task schreiben**

Ans Ende von `build.gradle`, neben den Kistenwächter:

```groovy
// ═══════════════════════════════════════════════════════════════════════════════
//  PLATZHALTERWAECHTER - welche Hotbar-Eintraege noch keine Textur haben
//
//  ⚠️ Er macht den Bau NICHT rot. Er waere ab heute dauerhaft rot - alle fuenf sind Platzhalter -,
//  und ein dauerhaft roter Bau wird nach zwei Tagen ignoriert (in ZanoriaCommands beobachtet).
//  Er MELDET laut; die MENGE ist in DieHotbarNenntIhrePlatzhalterTest gepinnt, und dort wird es
//  rot, wenn einer lautlos dazukommt ODER lautlos verschwindet. Laut melden und scharf pinnen
//  sind zwei Aufgaben, und sie liegen absichtlich an zwei Orten.
//
//  ⚠️ Er liest das UEBERSETZTE Enum, nicht den Quelltext.
// ═══════════════════════════════════════════════════════════════════════════════
def platzhalterwaechter = tasks.register('platzhalterwaechter') {
    dependsOn tasks.named('classes')
    def klassenVerzeichnis = layout.buildDirectory.dir('classes/java/main')
    def pfade = configurations.compileClasspath
    inputs.dir(klassenVerzeichnis)
    outputs.upToDateWhen { false }

    doLast {
        def urls = ([klassenVerzeichnis.get().asFile] + pfade.files).collect {
            it.toURI().toURL()
        } as URL[]
        def lader = new URLClassLoader(urls, this.class.classLoader)
        def enumKlasse = lader.loadClass('net.zanoria.lobby.hotbar.Hotbarplatz')
        def alle = enumKlasse.getEnumConstants()

        // ⚠️ Abbruch bei leerem Enum. Ein leerer Sucher meldete sonst "0 von 0, keine
        // Platzhalter" und saehe aus wie ein geloestes Problem.
        if (alle.length == 0) {
            throw new GradleException(
                    "PLATZHALTERWAECHTER: Hotbarplatz hat KEINE Eintraege - 'kein Platzhalter'" +
                    " waere dann kein Befund, sondern ein leerer Sucher.")
        }

        def offen = alle.findAll { it.istPlatzhalter() }
        logger.lifecycle("PLATZHALTERWAECHTER: ${offen.size()} von ${alle.length}" +
                " Hotbar-Eintraegen zeigen noch auf Platzhalter.")
        if (offen.isEmpty()) {
            logger.lifecycle("PLATZHALTERWAECHTER: jede Textur ist geliefert.")
        } else {
            offen.each {
                logger.lifecycle("PLATZHALTERWAECHTER:   ${it.name()} -> ${it.sinnbild()}")
            }
            logger.lifecycle("PLATZHALTERWAECHTER: solange hier Zeilen stehen, sieht die Hotbar" +
                    " im Spiel nach Vanilla aus. Wer eine Textur liefert, setzt istPlatzhalter()" +
                    " auf false UND traegt den Eintrag in" +
                    " DieHotbarNenntIhrePlatzhalterTest aus.")
        }
    }
}
tasks.named('check') { dependsOn platzhalterwaechter }
```

- [ ] **Step 2: Fahren**

Erwartet: `RC=0` und im Log
`PLATZHALTERWAECHTER: 5 von 5 Hotbar-Eintraegen zeigen noch auf Platzhalter.`
plus fünf Einzelzeilen.

- [ ] **Step 3: Positivkontrolle P1 — er meldet eine Änderung wirklich**

Setze bei `KOMPASS` `istPlatzhalter` auf `false`. Erwartet: der Wächter meldet **4 von 5** (und der
gepinnte Fall wird rot — das ist Task 11s Arbeit, beide zusammen sind die Absicht).
Zurücknehmen.

- [ ] **Step 4: Positivkontrolle P2 — der leere Sucher bricht ab**

Ändere den Klassennamen im Task vorübergehend auf `net.zanoria.lobby.hotbar.GibtEsNicht`.
Erwartet: **RC=1** mit `ClassNotFoundException` — also ein lauter Abbruch, **kein grüner Lauf**.
Zurücknehmen.

- [ ] **Step 5: Commit**

```bash
git add build.gradle
git commit -m "Platzhalterwaechter: meldet laut, macht den Bau nicht rot

Die Menge ist stattdessen im Test gepinnt. Laut melden und scharf pinnen
sind zwei Aufgaben an zwei Orten - ein dauerhaft roter Bau wird nach
zwei Tagen ignoriert."
```

---

### Task 13: Die fünf Bildschirme und der Hotbar-Hörer

⚠️ **Wo der Klick behandelt wird, ist eine Folge des Kistenwächters:** `InventoryClickEvent` darf
nur in `menue.chest` stehen. Der Klick **im Menü** gehört deshalb einem Hörer dort; der Hotbar-Hörer
behandelt nur den Klick **auf das Item**.

⚠️ **Der Hotbar-Hörer erkennt das Item am PLATZ, nicht am ItemStack.** Die Plätze sind fest und in
`Hotbarplatz` gepinnt; über `getHeldItemSlot()` (ein `int`) ist die Erkennung ohne Vergleich von
Gegenständen möglich — und damit auch ohne die Registry-Grenze zu berühren.

**Files:**
- Create: `src/main/java/net/zanoria/lobby/hotbar/Lobbybildschirme.java`
- Create: `src/main/java/net/zanoria/lobby/hotbar/Hotbarhoerer.java`
- Create: `src/main/java/net/zanoria/lobby/menue/chest/ChestKlickhoerer.java`
- Test: `src/test/java/net/zanoria/lobby/hotbar/DieFuenfBildschirmeStehenTest.java`
- Test: `src/test/java/net/zanoria/lobby/hotbar/DerHotbarhoererOeffnetWirklichTest.java`

- [ ] **Step 1: Die Gestaltfälle schreiben**

```java
package net.zanoria.lobby.hotbar;

import net.zanoria.lobby.menue.Bildschirm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DieFuenfBildschirmeStehenTest {

    @Test
    @DisplayName("⚠️ JEDES der fuenf Items oeffnet wirklich etwas")
    void jedesItemOeffnetEtwas() {
        // ⚠️ Der Auftrag lautet woertlich "alle fuenf sollen etwas oeffnen". Ohne diesen Fall
        // koennte ein Eintrag stumm bleiben, und im Spiel passierte beim Klick nichts - ohne
        // Ausnahme und ohne Logzeile.
        for (Hotbarplatz platz : Hotbarplatz.values()) {
            Bildschirm b = Lobbybildschirme.fuer(platz);
            assertNotNull(b, "⚠️ " + platz + " oeffnet NICHTS. Im Spiel passiert beim Klick"
                    + " nichts, und niemand erfaehrt warum.");
            assertEquals(platz.bildschirmKennung(), b.kennung());
            assertFalse(b.eintraege().isEmpty(),
                    "⚠️ Der Bildschirm von " + platz + " hat keine Eintraege. Ein leeres Menue"
                            + " ist schlimmer als kein Menue - es sieht aus wie ein Fehler.");
        }
    }

    @Test
    @DisplayName("Die Kennungen der fuenf Bildschirme sind eindeutig")
    void kennungenSindEindeutig() {
        Set<String> gesehen = new HashSet<>();
        for (Hotbarplatz platz : Hotbarplatz.values()) {
            assertTrue(gesehen.add(Lobbybildschirme.fuer(platz).kennung()),
                    "⚠️ Zwei Items oeffnen denselben Bildschirm.");
        }
    }
}
```

- [ ] **Step 2: Den Wirkungsfall schreiben — der Hörerrumpf wird gefahren**

```java
package net.zanoria.lobby.hotbar;

import net.zanoria.lobby.menue.Bildschirm;
import net.zanoria.lobby.menue.Menue;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ⚠️ Diese Faelle fahren den Rumpf, nicht die Tabelle daneben.
 *
 * <p>Die Naht macht das moeglich: {@link Menue} ist eine Schnittstelle, also laesst sich eine
 * mitschreibende Umsetzung einsetzen - <b>ohne Server und ohne Kiste</b>. Genau dafuer ist sie
 * unter anderem da.
 */
class DerHotbarhoererOeffnetWirklichTest {

    /** Eine mitschreibende Umsetzung der Naht. */
    private static final class Mitschreibendes implements Menue {
        final List<String> geoeffnet = new ArrayList<>();

        @Override
        public boolean oeffne(Player spieler, Bildschirm bildschirm) {
            geoeffnet.add(bildschirm.kennung());
            return true;
        }
    }

    @Test
    @DisplayName("⚠️ Jeder der fuenf Plaetze oeffnet SEINEN Bildschirm")
    void jederPlatzOeffnetSeinen() {
        for (Hotbarplatz platz : Hotbarplatz.values()) {
            Mitschreibendes menue = new Mitschreibendes();
            Hotbarhoerer hoerer = new Hotbarhoerer(menue);

            hoerer.beiPlatz(null, platz.platz());

            assertEquals(List.of(platz.bildschirmKennung()), menue.geoeffnet,
                    "⚠️ Platz " + platz.platz() + " haette " + platz.bildschirmKennung()
                            + " oeffnen muessen. Geoeffnet wurde: " + menue.geoeffnet);
        }
    }

    @Test
    @DisplayName("Ein Platz ohne Item oeffnet nichts")
    void leererPlatzOeffnetNichts() {
        Mitschreibendes menue = new Mitschreibendes();
        new Hotbarhoerer(menue).beiPlatz(null, 4);

        assertTrue(menue.geoeffnet.isEmpty(),
                "⚠️ Platz 4 traegt kein Item. Wuerde dort etwas geoeffnet, waere der Fall"
                        + " darueber auch gruen, wenn die Zuordnung gar nicht gelesen wuerde.");
    }
}
```

- [ ] **Step 3: Lauf zur Bestätigung, daß beide fehlschlagen**

Erwartet: `RC=1`, `cannot find symbol: class Lobbybildschirme` / `class Hotbarhoerer`.

- [ ] **Step 4: Lobbybildschirme**

```java
package net.zanoria.lobby.hotbar;

import net.zanoria.lobby.menue.Bildschirm;
import net.zanoria.lobby.menue.Eintrag;
import net.zanoria.lobby.menue.Sinnbild;

import java.util.List;

/**
 * Die fuenf Bildschirme, die hinter den Hotbar-Items liegen.
 *
 * <h2>⚠️ Sie sind absichtlich duenn</h2>
 *
 * <p>Entschieden vom Betreiber am 2026-09-03: <i>"so einfach wie moeglich - die Menues werden mit
 * ZanUI ohnehin neu gebaut. Sie muessen funktionieren und austauschbar sein, sonst nichts."</i>
 * Jeder Bildschirm hat heute genau die Eintraege, die er braucht, um <b>etwas zu oeffnen</b>. Wer
 * hier Inhalt ausbaut, baut Arbeit, die der Tausch wegwirft.
 *
 * <p>⚠️ Die Handlungen sind heute {@code spieler -> { }} - <b>und das ist gemeldet, nicht
 * versteckt</b>: {@code DieFuenfBildschirmeStehenTest} verlangt nur, dass jeder Bildschirm
 * Eintraege hat, nicht dass sie schon etwas tun. Was ein Eintrag tun soll, ist eine
 * Produktentscheidung je Bildschirm und gehoert nicht in diese Arbeit.
 */
public final class Lobbybildschirme {

    private Lobbybildschirme() {
    }

    /** Der Bildschirm hinter einem Hotbar-Item. Nie {@code null}. */
    public static Bildschirm fuer(Hotbarplatz platz) {
        return switch (platz) {
            case KOMPASS -> Bildschirm.of(platz.bildschirmKennung(), "lobby.kompass.titel",
                    List.of(
                            eintrag("lobby.kompass.relicwars", "zanoria:kompass_relicwars", "IRON_SWORD"),
                            eintrag("lobby.kompass.coreclash", "zanoria:kompass_coreclash", "BEACON"),
                            eintrag("lobby.kompass.nexusstrike", "zanoria:kompass_nexusstrike", "END_PORTAL_FRAME")));
            case RUCKSACK -> Bildschirm.of(platz.bildschirmKennung(), "lobby.rucksack.titel",
                    List.of(eintrag("lobby.rucksack.leer", "zanoria:rucksack_leer", "BARRIER")));
            case BATTLEPASS -> Bildschirm.of(platz.bildschirmKennung(), "lobby.battlepass.titel",
                    List.of(eintrag("lobby.battlepass.stufe", "zanoria:battlepass_stufe", "EXPERIENCE_BOTTLE")));
            case SOZIAL -> Bildschirm.of(platz.bildschirmKennung(), "lobby.sozial.titel",
                    List.of(
                            eintrag("lobby.sozial.freunde", "zanoria:sozial_freunde", "PLAYER_HEAD"),
                            eintrag("lobby.sozial.gruppe", "zanoria:sozial_gruppe", "LEAD")));
            case EINSTELLUNGEN -> Bildschirm.of(platz.bildschirmKennung(), "lobby.einstellungen.titel",
                    List.of(
                            eintrag("lobby.einstellungen.sprache", "zanoria:einstellungen_sprache", "PAPER"),
                            eintrag("lobby.einstellungen.sichtbarkeit", "zanoria:einstellungen_sicht", "ENDER_EYE")));
        };
        // ⚠️ switch OHNE default: kommt ein sechstes Item dazu, bricht der BAU. Ein default
        // liesse es stillschweigend auf den Kompass zeigen - und der Auftrag "alle fuenf sollen
        // etwas oeffnen" waere formal erfuellt und inhaltlich gebrochen.
    }

    private static Eintrag eintrag(String schluessel, String nexo, String rueckfall) {
        return Eintrag.of(schluessel, Sinnbild.of(nexo, rueckfall), spieler -> { });
    }
}
```

- [ ] **Step 5: Hotbarhoerer**

```java
package net.zanoria.lobby.hotbar;

import net.zanoria.lobby.menue.Menue;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Objects;

/**
 * Der Klick auf ein Hotbar-Item oeffnet seinen Bildschirm.
 *
 * <h2>⚠️ Erkannt wird am PLATZ, nicht am Gegenstand</h2>
 *
 * <p>Die fuenf Plaetze sind fest und in {@link Hotbarplatz} gepinnt. Ueber
 * {@code getHeldItemSlot()} - ein {@code int} - ist die Erkennung ohne Vergleich von
 * {@code ItemStack}s moeglich; ein solcher Vergleich waere ohne laufenden Server nicht pruefbar
 * (Registry-Grenze) und bei umbenannten Items ausserdem falsch.
 *
 * <p>⚠️ Dieser Hoerer kennt <b>keinen</b> Kisten-Typ. Er ruft {@link Menue#oeffne} - das ist die
 * Naht, und {@code :kistenwaechter} haelt fest, dass es so bleibt.
 */
public final class Hotbarhoerer implements Listener {

    private final Menue menue;

    public Hotbarhoerer(Menue menue) {
        this.menue = Objects.requireNonNull(menue, "menue");
    }

    @EventHandler
    public void beimKlicken(PlayerInteractEvent ereignis) {
        if (ereignis.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
            // Sonst feuert das Ereignis doppelt (Main + Offhand) - dieselbe Falle wie bei
            // onNpcInteract in ZanoriaLobby.
            return;
        }
        Player spieler = ereignis.getPlayer();
        if (beiPlatz(spieler, spieler.getInventory().getHeldItemSlot())) {
            ereignis.setCancelled(true);
        }
    }

    /**
     * Der rohe Teil - ohne {@code PlayerInteractEvent}.
     *
     * @return ob wirklich ein Bildschirm geoeffnet wurde
     */
    public boolean beiPlatz(Player spieler, int platz) {
        for (Hotbarplatz eintrag : Hotbarplatz.values()) {
            if (eintrag.platz() == platz) {
                return menue.oeffne(spieler, Lobbybildschirme.fuer(eintrag));
            }
        }
        return false;
    }
}
```

- [ ] **Step 6: Der Klick-Hörer im Menü (in `menue.chest`)**

```java
package net.zanoria.lobby.menue.chest;

import net.zanoria.lobby.menue.Bildschirm;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

import java.util.Objects;

/**
 * Der Klick INNERHALB eines Kistenmenues.
 *
 * <p>⚠️ Er liegt in {@code menue.chest} und nirgends sonst - {@code InventoryClickEvent} ist ein
 * Kisten-Typ, und {@code :kistenwaechter} macht den Bau rot, wenn er ausserhalb steht. Kommt ZanUI,
 * verschwindet diese Klasse mitsamt {@link ChestMenue}; kein Hoerer ausserhalb wird angefasst.
 */
public final class ChestKlickhoerer implements Listener {

    private final ChestMenue menue;

    public ChestKlickhoerer(ChestMenue menue) {
        this.menue = Objects.requireNonNull(menue, "menue");
    }

    @EventHandler
    public void beimKlicken(InventoryClickEvent ereignis) {
        if (!(ereignis.getWhoClicked() instanceof Player spieler)) {
            return;
        }
        Bildschirm bildschirm = menue.offenFuer(spieler.getUniqueId());
        if (bildschirm == null) {
            return;
        }
        // ⚠️ IMMER abbrechen, bevor irgendetwas anderes passiert. Sonst nimmt der Spieler die
        // Menue-Items mit - und in einer Lobby mit Adventure-Modus ist ein Item im Inventar der
        // einzige Weg, doch noch etwas kaputtzumachen.
        ereignis.setCancelled(true);

        int platz = ereignis.getRawSlot();
        if (platz < 0 || platz >= bildschirm.eintraege().size()) {
            return;
        }
        bildschirm.eintraege().get(platz).handlung().accept(spieler);
    }

    @EventHandler
    public void beimSchliessen(InventoryCloseEvent ereignis) {
        menue.schliesse(ereignis.getPlayer().getUniqueId());
    }
}
```

- [ ] **Step 7: Voller Lauf**

Erwartet: `RC=0`. Der Kistenwächter muß weiterhin grün sein und melden, daß er Treffer **im**
Umsetzungspaket gefunden hat (jetzt mehr als vorher).

- [ ] **Step 8: Mutationen M10–M12**

| Mutation | Änderung | erwartet rot durch |
|---|---|---|
| **M10** | `Hotbarhoerer.beiPlatz`: `return false;` am Kopf | `jederPlatzOeffnetSeinen` |
| **M11** | `Lobbybildschirme.fuer`: `RUCKSACK` gibt `Bildschirm.of(..., List.of())` | `jedesItemOeffnetEtwas` |
| **M12** | `ChestKlickhoerer.beimKlicken`: `setCancelled(true)` entfernt | `derKlickWirdAbgebrochen` — **muß rot werden**, siehe Step 9 |

- [x] **Step 9: ⚠️ KORRIGIERT am 2026-09-03 — M12 ist KEINE bekannte Grenze mehr**

Hier stand, `ChestKlickhoerer` laufe in keinem Einheitstest und M12 bleibe still grün. **Die
Messung aus Task 2 sagt etwas anderes:**

```
REGISTRYGRENZE: BAUBAR   new InventoryClickEvent(sicht, CONTAINER, 0, LEFT, PICKUP_ALL)
                         | mitschrift=[sicht#convertSlot]
```

`InventoryView` ist über eine Attrappe stellbar, und die Mitschrift belegt, daß sie wirklich
gefragt wurde. **Also wird der Rumpf gefahren, nicht gemeldet.** Schreibe
`DerKlickImMenueWirdAbgebrochenTest` mit einer `InventoryView`-Attrappe und fahre M12 — sie **muß
rot werden**.

⚠️ **Das ist der Unterschied zwischen einer gemessenen Grenze und einer angenommenen.** Angenommen
hätte diese Lücke drei Monate überlebt, und der Klick im Menü hätte Items mitnehmen lassen — in
einer Adventure-Lobby der einzige Weg, doch noch etwas kaputtzumachen.

⚠️ **Bleibt M12 trotz des neuen Falls still grün, ist das ein echter Befund** — dann fährt der Fall
den Rumpf nicht wirklich. Nachsehen, nicht weitergehen.

- [ ] **Step 10: Anmelden in `ZanoriaLobby.onEnable`**

Nach dem Lobbyschutz:

```java
        // ── Menues und Hotbar ───────────────────────────────────────────────
        // ⚠️ ChestMenue ist EXPERIMENTELL und die einzige Stelle, die das weiss. Kommt ZanUI,
        // werden genau diese zwei Zeilen getauscht - kein Hoerer wird angefasst.
        ChestMenue chestMenue = new ChestMenue(getSLF4JLogger());
        getServer().getPluginManager().registerEvents(new ChestKlickhoerer(chestMenue), this);
        getServer().getPluginManager().registerEvents(new Hotbarhoerer(chestMenue), this);
```

- [ ] **Step 11: Commit**

```bash
git add src/main/java/net/zanoria/lobby/hotbar/ \
        src/main/java/net/zanoria/lobby/menue/chest/ChestKlickhoerer.java \
        src/main/java/net/zanoria/lobby/ZanoriaLobby.java \
        src/test/java/net/zanoria/lobby/hotbar/
git commit -m "Fuenf Bildschirme hinter der Naht, Hotbar erkennt am Platz

Der Klick im Menue liegt in menue.chest - InventoryClickEvent ist ein
Kisten-Typ. Mutationen M10 und M11 rot; M12 ist gemeldet und liegt beim
Erstlauf, falls InventoryClickEvent ohne Server nicht baubar ist."
```

⚠️ **Die Vergabe der Items beim Beitritt fehlt hier absichtlich.** Sie braucht `new ItemStack(...)`
und ist damit ohne Server nicht prüfbar (Task 2). Sie steht als Task 14 und wird **im Erstlauf**
abgenommen, nicht im Einheitstest.

---

### Task 13b: Die fehlenden Schlüssel in ZanLang

⚠️ **Fremdes Repo:** `C:/Users/krinc/IdeaProjects/ZanLang`, eigener Commit dort.

⚠️ **Warum das eine eigene Aufgabe ist und nicht nebenbei passiert.** Gemessen am 2026-09-03:
**ZanLang hat keine Anmeldemethode für Schlüssel** — es gibt in `src/main/java` keine. Wer einen
Bildschirm baut, muß seine Schlüssel **von Hand** in `translations/de_de.json` und
`translations/en_us.json` tragen. Fehlen sie, steht im Spiel der **rohe Schlüssel** als Überschrift:
`lobby.kompass.titel`. Das ist nicht der Ausnahme-, sondern der Normalfall bei jedem Ausrollen —
ZanUI hat aus genau diesem Grund eine eigene `missingKeys`-Prüfung.

⚠️ **Die fünf Item-Namen fehlen NICHT.** `lobby.hotbar.compass|backpack|battlepass|friends|settings`
stehen bereits mit Übersetzung da. **Neu sind nur die Bildschirmtitel und Eintragsbeschriftungen.**

**Files:**
- Modify: `ZanLang/src/main/resources/translations/de_de.json`
- Modify: `ZanLang/src/main/resources/translations/en_us.json`
- Test: `src/test/java/net/zanoria/lobby/menue/JederSchluesselHatEinenSatzTest.java` (in **ZanoriaLobby**)

- [ ] **Step 1: Den Wächterfall in ZanoriaLobby schreiben**

```java
package net.zanoria.lobby.menue;

import net.zanoria.lobby.hotbar.Hotbarplatz;
import net.zanoria.lobby.hotbar.Lobbybildschirme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ⚠️ Jeder Schluessel, den ein Bildschirm traegt, muss in ZanLang einen Satz haben.
 *
 * <p>Gemessen am 2026-09-03: ZanLang kennt <b>keine</b> Anmeldemethode fuer Schluessel. Fehlt
 * einer, steht im Spiel der rohe Schluessel als Ueberschrift - und zwar ohne Ausnahme und ohne
 * Logzeile. Dieser Fall findet das Vergessene beim Bau statt vor einem Spieler.
 *
 * <p>⚠️ Er liest ZanLangs Quelldatei ueber einen relativen Pfad ins Nachbarrepo. <b>Fehlt das
 * Nachbarrepo, wird der Fall UEBERSPRUNGEN und nicht gruen</b> - ein gruener Fall ueber eine
 * Datei, die es nicht gibt, waere die Luege, gegen die er steht.
 */
class JederSchluesselHatEinenSatzTest {

    private static final Path ZANLANG =
            Path.of("../ZanLang/src/main/resources/translations/de_de.json");

    @Test
    @DisplayName("⚠️ Jeder Titel- und Beschriftungsschluessel steht in ZanLang")
    void jederSchluesselHatEinenSatz() throws IOException {
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.exists(ZANLANG),
                "ZanLang-Nachbarrepo nicht da - dieser Fall wird UEBERSPRUNGEN, nicht bestanden.");

        String json = Files.readString(ZANLANG);

        // ⚠️ Positivkontrolle: findet die Suche ueberhaupt einen bekannten Schluessel?
        assertTrue(json.contains("\"lobby.hotbar.compass\""),
                "⚠️ Der bekannte Schluessel lobby.hotbar.compass steht nicht in der gelesenen"
                        + " Datei. Dann misst dieser Fall etwas anderes als er glaubt, und jede"
                        + " Aussage unten waere wertlos.");

        List<String> gesucht = new ArrayList<>();
        for (Hotbarplatz platz : Hotbarplatz.values()) {
            gesucht.add(platz.beschriftungSchluessel());
            Bildschirm b = Lobbybildschirme.fuer(platz);
            gesucht.add(b.titelSchluessel());
            b.eintraege().forEach(e -> gesucht.add(e.beschriftungSchluessel()));
        }

        List<String> fehlend = gesucht.stream()
                .distinct()
                .filter(s -> !json.contains("\"" + s + "\""))
                .toList();

        assertTrue(fehlend.isEmpty(),
                "⚠️ Diese Schluessel haben in ZanLang keinen Satz und wuerden im Spiel ROH"
                        + " angezeigt:\n  " + String.join("\n  ", fehlend)
                        + "\nZanLang kennt keine Anmeldemethode - sie muessen von Hand in"
                        + " translations/de_de.json UND en_us.json.");
    }
}
```

- [ ] **Step 2: Fahren — er muß ROT sein**

Erwartet: **RC=1**, und die Meldung nennt genau die fehlenden Schlüssel (die Bildschirmtitel und
Eintragsbeschriftungen aus Task 13; die fünf `lobby.hotbar.*` fehlen **nicht**).

⚠️ **Ist er beim ersten Lauf grün, stimmt etwas nicht** — dann findet die Suche nichts, und die
Positivkontrolle hätte das fangen müssen. Nachsehen, nicht weitergehen.

- [ ] **Step 3: Die Schlüssel in BEIDE Dateien tragen**

Die von Step 2 gemeldeten Schlüssel in `de_de.json` **und** `en_us.json` eintragen.

⚠️ **Beide Dateien, nicht nur die deutsche.** Ein Schlüssel, den nur `de_de.json` kennt, erscheint
englischsprachigen Spielern roh — und niemand mit deutscher Einstellung bemerkt es.

- [ ] **Step 4: Erneut fahren**

Erwartet: `RC=0`.

- [ ] **Step 5: Mutation M13b — ein Schlüssel wird wieder entfernt**

Entferne einen der neuen Schlüssel aus `de_de.json`. Erwartet: **RC=1**, der Fall nennt ihn
namentlich. Zurücknehmen.

- [ ] **Step 6: Commits in beiden Repos**

```bash
cd /c/Users/krinc/IdeaProjects/ZanLang
git add src/main/resources/translations/de_de.json src/main/resources/translations/en_us.json
git commit -m "Lobby-Menues: Titel und Eintragsbeschriftungen

ZanLang kennt keine Anmeldemethode fuer Schluessel - fehlt einer, steht
im Spiel der rohe Schluessel. ZanoriaLobbys
JederSchluesselHatEinenSatzTest findet das jetzt beim Bau."
```

⚠️ **Und ZanLang neu bauen und nach mavenLocal veröffentlichen**, sonst zieht ZanoriaLobby weiter
die alte Fassung: die dortige `zanlang-1.0.jar` stammte am 2026-09-03 vom **15. August**, während
`ZanLang/build/libs/` eine neuere führte — zwei Stände nebeneinander.

---

### Task 14: Die Vergabe beim Beitritt

⚠️ **Diese Aufgabe hat bewußt KEINEN Wirkungs-Einheitstest.** `new ItemStack(Material)` wirft ohne
laufenden Server (Task 2). Geprüft wird hier die **Zuordnung**; daß die Items wirklich im Inventar
landen, beantwortet **nur** der Erstlauf (Task 18). Wer dafür einen Einheitstest mit gestelltem
Inventar baut, mißt die Attrappe statt der Klasse.

**Files:**
- Create: `src/main/java/net/zanoria/lobby/hotbar/Hotbarausgabe.java`
- Test: `src/test/java/net/zanoria/lobby/hotbar/DieAusgabeKenntJedenPlatzTest.java`

- [ ] **Step 1: Den Fall schreiben**

```java
package net.zanoria.lobby.hotbar;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ⚠️ Geprueft wird die ZUORDNUNG, nicht das Erzeugen der Gegenstaende.
 *
 * <p>{@code new ItemStack(Material)} wirft ohne laufenden Server (gemessen, Task 2). Dass die
 * Items wirklich im Inventar landen, beantwortet allein {@code tools/erstlauf.sh}.
 */
class DieAusgabeKenntJedenPlatzTest {

    @Test
    @DisplayName("Die Ausgabe kennt fuer jeden Platz genau einen Eintrag")
    void jederPlatzIstZugeordnet() {
        Map<Integer, Hotbarplatz> plan = Hotbarausgabe.plan();

        assertEquals(Hotbarplatz.values().length, plan.size(),
                "⚠️ Der Plan hat " + plan.size() + " Eintraege, das Enum hat "
                        + Hotbarplatz.values().length + ". Ein Eintrag wuerde im Spiel fehlen,"
                        + " ohne Ausnahme und ohne Logzeile.");
        for (Hotbarplatz platz : Hotbarplatz.values()) {
            assertEquals(platz, plan.get(platz.platz()),
                    "⚠️ Platz " + platz.platz() + " traegt nicht " + platz + ".");
        }
    }
}
```

- [ ] **Step 2: Lauf zur Bestätigung, daß er fehlschlägt**

Erwartet: `RC=1`, `cannot find symbol: class Hotbarausgabe`.

- [ ] **Step 3: Die Umsetzung**

```java
package net.zanoria.lobby.hotbar;

import net.zanoria.lobby.menue.chest.Materialwahl;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Vergibt die fuenf Hotbar-Items beim Beitritt.
 *
 * <p>⚠️ Die <b>Zuordnung</b> ({@link #plan()}) ist ohne Server pruefbar; das <b>Erzeugen</b> der
 * Gegenstaende ist es nicht - {@code new ItemStack(Material)} faellt in die Registry-Grenze. Die
 * Trennung ist Absicht: so misst ein Einheitstest wenigstens das, was er messen kann, statt gar
 * nichts oder das Falsche.
 */
public final class Hotbarausgabe implements Listener {

    private static final String NOTNAGEL = "STONE";

    private final Logger log;

    public Hotbarausgabe(Logger log) {
        this.log = Objects.requireNonNull(log, "log");
    }

    /** Welcher Eintrag auf welchem Platz liegt. Aus dem Enum abgeleitet, nicht gepflegt. */
    public static Map<Integer, Hotbarplatz> plan() {
        // ⚠️ ABGELEITET, nicht aufgeschrieben. Eine gepflegte Liste wuerde beim naechsten
        // Eintrag selbst zur Luege - dieselbe Lehre wie erwartete_hoerer() im Erstlauf.
        Map<Integer, Hotbarplatz> plan = new LinkedHashMap<>();
        for (Hotbarplatz platz : Hotbarplatz.values()) {
            plan.put(platz.platz(), platz);
        }
        return Map.copyOf(plan);
    }

    @EventHandler
    public void beiBeitritt(PlayerJoinEvent ereignis) {
        gib(ereignis.getPlayer());
    }

    /** Der rohe Teil - ohne {@code PlayerJoinEvent}. */
    public void gib(Player spieler) {
        plan().forEach((platz, eintrag) -> {
            try {
                spieler.getInventory().setItem(platz, stapel(eintrag));
            } catch (RuntimeException | LinkageError fehler) {
                // ⚠️ Je Item fangen, nicht um die ganze Schleife: sonst nimmt der erste
                // Fehlschlag alle folgenden mit, und im Log steht nichts darueber. Dieselbe
                // Lehre wie spawnQueueNpcs in ZanoriaLobby.
                log.error("Hotbar-Item {} (Platz {}) konnte nicht vergeben werden.",
                        eintrag, platz, fehler);
            }
        });
    }

    private ItemStack stapel(Hotbarplatz eintrag) {
        String name = Materialwahl.rueckfallOder(eintrag.sinnbild(), NOTNAGEL);
        // ⚠️ Der Nexo-Teil fehlt hier bewusst: es gibt heute keine Textur, und ein Aufruf gegen
        // Nexos API waere ein Zweig ohne Gegenstueck. Der PLATZHALTERWAECHTER meldet bei jedem
        // Bau, dass es so ist. Wer die erste Textur liefert, baut ihn hier ein.
        return new ItemStack(Material.valueOf(name));
    }
}
```

- [ ] **Step 4: Lauf, dann in `onEnable` anmelden**

```java
        getServer().getPluginManager().registerEvents(new Hotbarausgabe(getSLF4JLogger()), this);
```

- [ ] **Step 5: Mutation M13 — ein Eintrag fällt aus dem Plan**

Ändere `plan()` zu `if (platz != Hotbarplatz.SOZIAL) plan.put(...)`.
Erwartet: **RC=1**, `jederPlatzIstZugeordnet` rot. Zurücknehmen.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/net/zanoria/lobby/hotbar/Hotbarausgabe.java \
        src/test/java/net/zanoria/lobby/hotbar/DieAusgabeKenntJedenPlatzTest.java \
        src/main/java/net/zanoria/lobby/ZanoriaLobby.java
git commit -m "Hotbar wird beim Beitritt vergeben - Zuordnung geprueft, Erzeugen im Erstlauf"
```

---

## Phase 4 — Stück 4: /builder an eine Berechtigung binden

### Task 15: Nexus vergibt zanoria.builder

⚠️ **Fremdes Repo.** Änderung in `C:/Users/krinc/IdeaProjects/Nexus`, eigener Commit dort.

**Files:**
- Modify: `Nexus/src/main/java/net/zanoria/nexus/permission/NexusPermissions.java`
- Modify: `Nexus/src/main/java/net/zanoria/nexus/rank/RankPresentationService.java`
- Test: `Nexus/src/test/java/.../DasBaurechtHatEinenSchluesselTest.java`

- [ ] **Step 1: Die Konstante**

In `NexusPermissions`, neben `ADMIN`:

```java
    /**
     * Das Recht, den Builder-Server zu betreten.
     *
     * <p><b>Warum die obersten Raenge und keine Sprosse in der Mitte</b> (Corwis, 2026-09-03):
     * <i>"Bauen heisst Welten anlegen und loeschen mit FAWE. Das ist ein Infrastrukturrecht, kein
     * Moderationsrecht."</i>
     *
     * <p>⚠️ <b>Das ist eine Entscheidung mit ABLAUFBEDINGUNG, keine dauerhafte.</b> Corwis am
     * selben Tag: <i>"Ein eigener BUILDER-Rang waere die richtige Loesung, sobald es externe
     * Builder gibt. Heute sind wir zu zweit, und ein dreizehnter Rang mit vier Tabellen loest ein
     * Problem, das wir nicht haben."</i>
     *
     * <p>Ohne diese Bedingung dreht sich der Satz mit der Zeit ins Gegenteil, ohne je falsch zu
     * werden - eine Momentaufnahme, die als Lage gelesen wird.
     */
    public static final String BUILDER = "zanoria.builder";
```

- [ ] **Step 2: Die Vergabe**

In `RankPresentationService.applyPermissions`, direkt nach dem `ADMIN`-Block:

```java
        // ⚠️ Die Menge steht hier ausgeschrieben und wird NICHT aus dem Gewicht abgeleitet.
        // Ein "rank.weight() <= 20" saehe eleganter aus und waere eine stille Regel: ein neuer
        // Rang mit Gewicht 15 bekaeme das Baurecht, ohne dass jemand es entschieden hat.
        if (rank == Rank.OWNER || rank == Rank.CO_OWNER || rank == Rank.ADMIN) {
            attachment.setPermission(NexusPermissions.BUILDER, true);
        }
```

- [ ] **Step 3: Der Fall in Nexus**

```java
package net.zanoria.nexus.rank;

import net.zanoria.nexus.permission.NexusPermissions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * ⚠️ Wogegen dieser Fall steht: ein Recht, das niemand vergeben kann.
 *
 * <p>Gemessen am 2026-09-03: es gibt in diesem Netz <b>kein LuckPerms</b>, und Nexus vergibt in
 * {@code applyPermissions} nur {@code zanoria.rank.*} und {@code zanoria.admin}. Ein neu
 * deklariertes, geprueftes Recht waere damit ein <b>Tor ohne Schluessel</b> - deklariert, im
 * Befehl abgefragt, und von niemandem ausser {@code op} erreichbar.
 */
class DasBaurechtHatEinenSchluesselTest {

    private static final Set<Rank> ERWARTET = Set.of(Rank.OWNER, Rank.CO_OWNER, Rank.ADMIN);

    @Test
    @DisplayName("⚠️ Die Menge der bauberechtigten Raenge ist gepinnt")
    void dieMengeIstGepinnt() {
        Set<Rank> tatsaechlich = Arrays.stream(Rank.values())
                .filter(RankPresentationService::darfBauen)
                .collect(Collectors.toSet());

        assertEquals(ERWARTET, tatsaechlich,
                "⚠️ Wer das Baurecht bekommt, hat sich geaendert. Bauen heisst Welten anlegen und"
                        + " LOESCHEN - das ist eine Entscheidung des Betreibers, keine Ableitung.");
    }

    @Test
    @DisplayName("Kein Rang unterhalb von ADMIN darf bauen")
    void moderatorDarfNicht() {
        assertFalse(RankPresentationService.darfBauen(Rank.MODERATOR));
        assertFalse(RankPresentationService.darfBauen(Rank.CREATOR));
        assertFalse(RankPresentationService.darfBauen(Rank.PLAYER));
    }

    @Test
    @DisplayName("Die Rechte-Kennung ist zanoria.builder")
    void dieKennungStimmt() {
        // ⚠️ Sie steht wortgleich in ZanoriaLobbys plugin.yml. Weichen sie voneinander ab,
        // prueft der Befehl ein Recht, das niemand vergibt - und der Fehler ist unsichtbar.
        assertEquals("zanoria.builder", NexusPermissions.BUILDER);
    }
}
```

⚠️ **Dafür muß `darfBauen(Rank)` als paketsichtbare, statische Methode herausgezogen werden** und
in `applyPermissions` benutzt werden — sonst prüft der Fall eine Kopie der Regel statt der Regel:

```java
    /** Ob dieser Rang den Builder-Server betreten darf. Siehe {@link NexusPermissions#BUILDER}. */
    static boolean darfBauen(Rank rank) {
        return rank == Rank.OWNER || rank == Rank.CO_OWNER || rank == Rank.ADMIN;
    }
```

und in `applyPermissions`:

```java
        if (darfBauen(rank)) {
            attachment.setPermission(NexusPermissions.BUILDER, true);
        }
```

⚠️ **Ein Fall, der die Bedingung selbst nachbaut, ist eine zweite Wahrheit** — er bliebe grün,
wenn jemand die echte Bedingung ändert.

- [ ] **Step 4: Nexus bauen und messen**

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-21"
cd /c/Users/krinc/IdeaProjects/Nexus
rm -rf build/test-results
./gradlew check --continue > /tmp/nexus.log 2>&1; echo "RC=$?"
```
Erwartet: `RC=0`.

- [ ] **Step 5: Mutation M14 — MODERATOR bekommt das Baurecht**

Ergänze in `darfBauen` `|| rank == Rank.MODERATOR`.
Erwartet: **RC=1**, `dieMengeIstGepinnt` und `moderatorDarfNicht` rot. Zurücknehmen.

- [ ] **Step 6: Nexus committen und das Jar neu bauen**

```bash
cd /c/Users/krinc/IdeaProjects/Nexus
git add src/main/java/net/zanoria/nexus/permission/NexusPermissions.java \
        src/main/java/net/zanoria/nexus/rank/RankPresentationService.java \
        src/test/java/net/zanoria/nexus/rank/DasBaurechtHatEinenSchluesselTest.java
git commit -m "zanoria.builder: das Baurecht bekommt einen Schluessel

Ohne diese Vergabe waere das Recht ein Tor ohne Schluessel - es gibt kein
LuckPerms im Netz, und Nexus vergab nur zanoria.rank.* und zanoria.admin.

Die drei obersten Raenge, weil Bauen Welten anlegen und LOESCHEN heisst -
ein Infrastrukturrecht, kein Moderationsrecht. Entscheidung mit
Ablaufbedingung: ein eigener BUILDER-Rang, sobald es externe Builder gibt."
./gradlew build -x test > /tmp/nexus-jar.log 2>&1; echo "RC=$?"
```

⚠️ **Das Jar muß neu gebaut werden**, sonst übersetzt ZanoriaLobby weiter gegen die alte Fassung
ohne `NexusPermissions.BUILDER`.

---

### Task 16: Der Builder-Server meldet FAWE selbst

⚠️ **Fremdes Repo:** `C:/Users/krinc/IdeaProjects/Builders`, eigener Commit dort.

⚠️ **Warum eine Selbstmeldung und kein einmaliges Nachsehen:** das Netz liegt in WSL, und am
2026-09-03 lief **keine Distribution** — das ganze Netz war aus. Hochfahren hätte Docker, Proxy und
Spielserver mitgestartet, für eine Momentaufnahme, die morgen nicht mehr gilt. Der Melder mißt
dauerhaft am laufenden Server.

**Files:**
- Modify: `Builders/src/main/java/net/zanoria/builders/redis/BuilderRedisClient.java`
- Modify: `Builders/src/main/java/net/zanoria/builders/redis/BuilderRedisReporter.java`
- Test: `Builders/src/test/java/.../DieFaweMeldungIstEindeutigTest.java`

- [ ] **Step 1: Die Fassungszeile bilden — als bukkitfreie Rechnung**

Neue Klasse `Builders/src/main/java/net/zanoria/builders/redis/Fawemeldung.java`:

```java
package net.zanoria.builders.redis;

/**
 * Die Zeichenkette, die dieser Server unter {@code builder:worldedit} nach Redis schreibt.
 *
 * <p>⚠️ <b>Getrennt vom Melder, damit sie ohne laufenden Server pruefbar ist.</b> Der Melder
 * selbst faehrt nur mit Bukkit; diese Rechnung laeuft in jedem Einheitstest.
 *
 * <p>⚠️ Die Lobby liest das Ergebnis und weist ab, wenn {@link #FEHLT} darin steht oder der
 * Schluessel gar nicht existiert. Das faellt <b>zu</b>: ein Spieler soll nicht auf einem
 * Builder-Server landen, auf dem das Werkzeug fehlt, fuer das er hingeschickt wurde.
 */
public final class Fawemeldung {

    /** Was geschrieben wird, wenn WorldEdit/FAWE nicht geladen ist. */
    public static final String FEHLT = "FEHLT";

    private Fawemeldung() {
    }

    /**
     * @param name    der Name des geladenen Plugins, oder {@code null}
     * @param fassung dessen Fassung, oder {@code null}
     */
    public static String aus(String name, String fassung) {
        if (name == null || name.isBlank()) {
            return FEHLT;
        }
        // ⚠️ Eine fehlende Fassung ist KEIN Grund, FEHLT zu melden: das Plugin ist geladen, nur
        // die Fassung ist unbekannt. Wer hier FEHLT meldete, spraeche dem Builder-Server das
        // Werkzeug ab, das er nachweislich hat.
        return (fassung == null || fassung.isBlank()) ? name : name + " " + fassung;
    }
}
```

- [ ] **Step 2: Der Fall**

```java
package net.zanoria.builders.redis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DieFaweMeldungIstEindeutigTest {

    @Test
    @DisplayName("Geladenes FAWE wird mit Fassung gemeldet")
    void mitFassung() {
        assertEquals("FastAsyncWorldEdit 2.15.1",
                Fawemeldung.aus("FastAsyncWorldEdit", "2.15.1"));
    }

    @Test
    @DisplayName("⚠️ Fehlt das Plugin, wird FEHLT gemeldet - nicht die leere Zeichenkette")
    void fehltIstEindeutig() {
        // ⚠️ Eine leere Zeichenkette waere von "Schluessel existiert nicht" nicht zu
        // unterscheiden - und der Unterschied traegt die Abweisung auf der Lobby-Seite.
        assertEquals(Fawemeldung.FEHLT, Fawemeldung.aus(null, "2.15.1"));
        assertEquals(Fawemeldung.FEHLT, Fawemeldung.aus("  ", null));
    }

    @Test
    @DisplayName("⚠️ Eine unbekannte Fassung ist KEIN Fehlen")
    void ohneFassungTrotzdemDa() {
        assertEquals("WorldEdit", Fawemeldung.aus("WorldEdit", null));
    }
}
```

- [ ] **Step 3: Der Melder schreibt und räumt**

In `BuilderRedisClient` ergänzen:

```java
    public static final String KEY_WORLDEDIT = "builder:worldedit";

    public void setWorldedit(String wert) {
        if (!isAvailable()) return;
        try (Jedis j = pool.getResource()) {
            if (wert == null) j.del(KEY_WORLDEDIT);
            else j.set(KEY_WORLDEDIT, wert);
        }
    }
```

In `BuilderRedisReporter`, wo `builder:status = running` geschrieben wird, direkt daneben:

```java
        // ⚠️ NEBEN den Status, nicht statt seiner - und im selben Zug geraeumt. Bliebe der
        // Schluessel nach dem Herunterfahren stehen, meldete ein toter Server weiter "FAWE ist da".
        Plugin we = Bukkit.getPluginManager().getPlugin("WorldEdit");
        redis.setWorldedit(Fawemeldung.aus(
                we == null ? null : we.getName(),
                we == null ? null : we.getPluginMeta().getVersion()));
```

Und beim Herunterfahren, wo `builder:status` gelöscht wird: `redis.setWorldedit(null);`

⚠️ **`getPlugin("WorldEdit")`, nicht `"FastAsyncWorldEdit"`.** FAWE meldet sich als `WorldEdit` an —
das ist der Grund, warum `Builders/plugin.yml` `depend: WorldEdit` führt und trotzdem FAWE gemeint
ist. Eine Suche nach `FastAsyncWorldEdit` fände **nichts** und meldete `FEHLT` bei laufendem FAWE.

- [ ] **Step 4: Lauf im Builders-Repo**

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-21"
cd /c/Users/krinc/IdeaProjects/Builders
rm -rf build/test-results
./gradlew check --continue > /tmp/builders.log 2>&1; echo "RC=$?"
```

⚠️ **Hat `Builders` kein Testgerüst, kommt es hier dazu** — dieselben vier Zeilen wie in Task 1,
samt Positivkontrolle.

- [ ] **Step 5: Mutation M15**

`Fawemeldung.aus`: `return FEHLT;` durch `return "";` ersetzen.
Erwartet: **RC=1**, `fehltIstEindeutig` rot. Zurücknehmen.

- [ ] **Step 6: Commit im Builders-Repo**

```bash
git add src/main/java/net/zanoria/builders/redis/ src/test/java/net/zanoria/builders/redis/
git commit -m "Der Builder-Server meldet selbst, ob FAWE geladen ist

Die Lobby liest builder:worldedit vor dem Transfer und weist ab, wenn
der Schluessel fehlt oder FEHLT sagt. Gesucht wird das Plugin WorldEdit,
nicht FastAsyncWorldEdit - FAWE meldet sich unter dem ersten Namen an."
```

---

### Task 17: Das Lobby-Tor

**Files:**
- Modify: `src/main/resources/plugin.yml`
- Modify: `src/main/java/net/zanoria/lobby/builder/BuilderCommand.java`
- Modify: `src/main/java/net/zanoria/lobby/builder/BuilderServerService.java`
- Modify: `src/main/java/net/zanoria/lobby/builder/BuilderRedisClient.java`
- Test: `src/test/java/net/zanoria/lobby/builder/DasBuildertorIstEineBerechtigungTest.java`

- [ ] **Step 1: `plugin.yml`**

```yaml
  builder:
    description: Betritt den Builder-Server
    usage: /builder
    permission: zanoria.builder
    permission-message: Du hast keine Berechtigung fuer den Builder-Server.
```

und bei `permissions:`:

```yaml
  zanoria.builder:
    description: Erlaubt den Zutritt zum Builder-Server (Welten anlegen und loeschen)
    default: op
```

⚠️ **`add`/`remove`/`list` fallen weg** — die Liste entscheidet nicht mehr.

- [ ] **Step 2: Die Liste stillegen, nicht löschen**

In `BuilderRedisClient` über `KEY_MEMBERS` und die vier Methoden:

```java
    /**
     * ⚠️ <b>STILLGELEGT am 2026-09-03. Diese Liste entscheidet NICHTS mehr.</b>
     *
     * <p>Bis dahin war sie das Tor zum Builder-Server: {@code BuilderServerService} fragte
     * {@code isMember(spieler.getName())}. Ersetzt durch die Berechtigung
     * {@code zanoria.builder}, vergeben von Nexus an OWNER/CO_OWNER/ADMIN.
     *
     * <p><b>Zwei Gruende, und beide gelten weiter:</b>
     * <ol>
     *   <li>Der Schluessel war der <b>Name</b>. Eine Namensaenderung verschob oder verlor den
     *       Zugang - lautlos.</li>
     *   <li>Sie war ein <b>zweiter Ort</b> neben dem Rangsystem. Wer freischaltete, musste wissen,
     *       dass es sie gibt.</li>
     * </ol>
     *
     * <p>⚠️ <b>Nicht geloescht, damit ein bestehender Redis-Bestand nicht verwaist</b> - und damit
     * dieser Kommentar hier steht. Wer sie zurueckverdrahtet, macht das Tor wieder namensabhaengig;
     * {@code DasBuildertorIstEineBerechtigungTest} wird dabei rot, und das ist Absicht.
     */
    public static final String KEY_MEMBERS = "builder:members";
```

⚠️ **`addMember`/`removeMember`/`getMembers`/`isMember` bleiben stehen** — mit dem Vermerk
`@deprecated` und einem Verweis auf diesen Kommentar.

- [ ] **Step 3: Der Dienst prüft FAWE statt der Liste**

In `BuilderServerService.joinBuilderServer`, den `isMember`-Block **ersetzen** durch:

```java
        // ⚠️ Die Berechtigung hat der Befehl schon geprueft (plugin.yml: permission:
        // zanoria.builder). Hier steht die ZWEITE Bedingung: hat der Zielserver ueberhaupt das
        // Werkzeug, fuer das der Spieler hingeschickt wird?
        String worldedit = redis.getWorldedit();
        if (worldedit == null || worldedit.isBlank() || "FEHLT".equals(worldedit)) {
            // ⚠️ Faellt ZU. Ein fehlender Schluessel ist kein Grund durchzulassen: der Spieler
            // landete sonst auf einem Server ohne FAWE - also ohne den Grund seiner Reise.
            player.sendMessage(MM.deserialize(PREFIX + "<red>Der Builder-Server meldet kein "
                    + "WorldEdit/FAWE (" + (worldedit == null ? "keine Meldung" : worldedit)
                    + "). Der Zutritt bleibt zu, bis das behoben ist."));
            return;
        }
```

Dazu in `BuilderRedisClient`:

```java
    public String getWorldedit() {
        if (!isAvailable()) return null;
        try (Jedis j = pool.getResource()) { return j.get(KEY_WORLDEDIT); }
    }
```

- [ ] **Step 4: Der Wächterfall**

```java
package net.zanoria.lobby.builder;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ⚠️ Zwei Zusicherungen, und beide haben einen gemessenen Anlass.
 *
 * <p>1. Der Befehl haengt an einer Berechtigung. Bis zum 2026-09-03 trug er in {@code plugin.yml}
 * <b>kein</b> {@code permission:}-Feld; das Tor war eine Redis-Namensliste.
 *
 * <p>2. Der Dienst fragt die Liste NICHT mehr. Ein Kommentar allein haelt niemanden auf - wer sie
 * zurueckverdrahtet, macht das Tor wieder namensabhaengig, und niemand wuerde es bemerken.
 */
class DasBuildertorIstEineBerechtigungTest {

    private String lies(String pfad) throws IOException {
        Path p = Path.of(pfad);
        assertTrue(Files.exists(p), "⚠️ " + pfad + " nicht gefunden - dann prueft dieser Fall"
                + " nichts, und er saehe trotzdem gruen aus.");
        return Files.readString(p);
    }

    @Test
    @DisplayName("⚠️ /builder haengt an zanoria.builder")
    void derBefehlHaengtAnDerBerechtigung() throws IOException {
        String yml = lies("src/main/resources/plugin.yml");

        // ⚠️ Positivkontrolle zuerst: findet die Suche ueberhaupt den Befehl?
        assertTrue(yml.contains("builder:"),
                "⚠️ In plugin.yml steht gar kein builder-Befehl - die Suche unten waere dann"
                        + " gratis gruen.");
        assertTrue(yml.contains("permission: zanoria.builder"),
                "⚠️ /builder traegt kein permission-Feld. Bis 2026-09-03 war das der Zustand,"
                        + " und das Tor war eine Redis-Namensliste.");
        assertTrue(yml.contains("zanoria.builder:"),
                "⚠️ zanoria.builder ist nicht DEKLARIERT. Ein nicht deklariertes Recht"
                        + " beantwortet Bukkit mit isOp() - und der Rangvergabe entginge es.");
    }

    @Test
    @DisplayName("⚠️ Der Dienst fragt die stillgelegte Liste nirgends mehr")
    void dieListeEntscheidetNichts() throws IOException {
        String quelle = lies(
                "src/main/java/net/zanoria/lobby/builder/BuilderServerService.java");

        // ⚠️ Positivkontrolle: liest der Fall wirklich die richtige Datei?
        assertTrue(quelle.contains("joinBuilderServer"),
                "⚠️ In der gelesenen Datei steht kein joinBuilderServer - der Pfad stimmt nicht"
                        + " mehr, und die Suche unten faende garantiert nichts.");
        assertFalse(quelle.contains("isMember"),
                "⚠️ BuilderServerService fragt wieder isMember. Damit haengt der Zutritt erneut"
                        + " am SPIELERNAMEN - eine Namensaenderung verschiebt oder verliert ihn,"
                        + " lautlos. Die Liste ist am 2026-09-03 stillgelegt worden; der Grund"
                        + " steht ueber KEY_MEMBERS in BuilderRedisClient.");
    }
}
```

⚠️ **Dieser Fall liest den Quelltext, nicht den Bytecode** — und das ist hier richtig, weil die
Frage *„steht der Name `isMember` im Rumpf"* lautet. Für eine Aufrufzählung wäre es der falsche
Griff. ⚠️ **Er würde einen `isMember` im Kommentar mitzählen.** Das ist ein bekannter Preis; die
beiden Positivkontrollen fangen den umgekehrten, teureren Fehler (die Suche findet gar nichts).

- [ ] **Step 5: Voller Lauf**

Erwartet: `RC=0`. **Der Befehlswächter ist hier scharf** — er meldet, wenn `getCommand("builder")`
auf einen Namen zeigt, der nicht mehr unter `commands:` steht.

- [ ] **Step 6: Mutationen M16–M18**

| Mutation | Änderung | erwartet rot durch |
|---|---|---|
| **M16** | `permission: zanoria.builder` aus `plugin.yml` entfernen | `derBefehlHaengtAnDerBerechtigung` |
| **M17** | in `joinBuilderServer` wieder `if (!redis.isMember(player.getName())) return;` | `dieListeEntscheidetNichts` |
| **M18** | die FAWE-Prüfung durchlassen (`if (false)`) | ⚠️ **erwartet STILL GRÜN** — sie ist nur im Erstlauf prüfbar, siehe Task 18 |

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/plugin.yml src/main/java/net/zanoria/lobby/builder/ \
        src/test/java/net/zanoria/lobby/builder/
git commit -m "/builder haengt an zanoria.builder, die Namensliste ist stillgelegt

Der Befehl trug bis heute kein permission-Feld; das Tor war eine
Redis-Namensliste, die bei Namensaenderung lautlos verschob. Die Liste
bleibt STEHEN, mit dem Grund darueber - geloescht wuerde sie einen
bestehenden Bestand verwaisen lassen, und der Kommentar ginge mit.

Zusaetzlich: der Zutritt faellt zu, wenn der Builder-Server kein
WorldEdit/FAWE meldet."
```

---

### Task 18: Der Erstlauf

⚠️ **Die Bedingung ist erfüllt und gemessen** (2026-09-03): Nexus und ZanLang übersetzen wirklich —
`compileJava` ausgeführt, 411 bzw. 22 Klassen aus gelöschtem Klassenverzeichnis, Rückgabewert 0.
**Der Erstlauf ist damit Teil dieser Arbeit.**

⚠️ **Er beantwortet, was kein Einheitstest beantwortet.** Nach der Arbeit stehen mindestens drei
Mutationen offen, die nur er fangen kann: **M12** (Klick im Menü), **M18** (FAWE-Prüfung), und die
gesamte Item-Vergabe aus Task 14.

**Files:**
- Create: `tools/erstlauf.sh`
- Create: `tools/erstlauf-sonde/Sonde.java`

- [ ] **Step 1: Die Vorlage bereitstellen**

Als Quelle taugt `/c/Zanoria/templates/coreclash/servers/2x4/` (enthält `paper.jar`). Kopiere
daraus nach `run/vorlage/`.

⚠️ **`eula.txt` schreibt das Skript NICHT** — das ist die Zustimmung des Betreibers, nicht die
eines Skripts.

⚠️ **`run/` gehört in `.gitignore`.** Versioniert wird das **Rezept**, nicht das Erzeugnis.

⚠️ **Der Aufbau darf die Vorlage nicht löschen.** Zeigt die Vorlagenvariable auf dasselbe
Verzeichnis wie das Ziel, muß das Skript **abbrechen**. In NexusStrike hat genau dieser Fall einmal
210 MB vernichtet, die kein Git zurückbringt.

- [ ] **Step 2: Die Sonde**

Sie liest am **laufenden** Server und schreibt Zeilen ins Protokoll:

```
SONDE-HOERER: net.zanoria.lobby.schutz.LobbyWeltschutz x 3
SONDE-HOERER: net.zanoria.lobby.hotbar.Hotbarhoerer x 1
SONDE-HOERER: net.zanoria.lobby.hotbar.Hotbarausgabe x 1
SONDE-HOERER: net.zanoria.lobby.menue.chest.ChestKlickhoerer x 2
SONDE-BEFEHL: builder -> permission=zanoria.builder
```

⚠️ **Die Hörerliste wird ABGELEITET, nicht aufgeschrieben.** `erwartete_hoerer()` sammelt die
Klassen unter `src/main/java`, die `implements Listener` tragen — **zeilenübergreifend**, denn ein
Umbruch nach dem Klassennamen (jede IDE-Neuformatierung) macht die Klasse für eine zeilenweise
Suche unsichtbar, und die **Erwartung schrumpfte mit**.

⚠️ **Nicht aus den `registerEvents`-Zeilen ableiten.** Eine gelöschte Anmeldung ließe die Erwartung
ebenfalls mitschrumpfen — die Frage hätte sich selbst abgemeldet.

- [ ] **Step 3: Die Punkte**

| # | Punkt |
|---|---|
| 1 | Das Plugin kommt hoch (`ZanoriaLobby enabled` im Protokoll) |
| 2 | Jeder abgeleitete Hörer hat eine `SONDE-HOERER`-Zeile |
| 3 | `/builder` ist angemeldet **und** trägt `zanoria.builder` |
| 4 | Der Platzhalterwächter meldet `5 von 5` |
| 5 | Ein gestellter Spieler bekommt fünf Items auf 0, 1, 2, 7, 8 |
| 6 | Ein Menü läßt sich wirklich öffnen (`Menue.oeffne` gibt `true`) |
| 7 | Ein Klick im Menü wird abgebrochen (deckt **M12**) |
| 8 | Ohne `builder:worldedit` in Redis wird `/builder` abgewiesen (deckt **M18**) |

⚠️ **Punkte 5–8 sind der eigentliche Grund für den Erstlauf.** Alles davor ließe sich notfalls
anders belegen; diese vier nicht.

- [ ] **Step 4: Die Positivkontrolle — `sabotage`**

Ein Unterbefehl, der **jeden** Hörer einzeln abmeldet und beweist, daß der Lauf dann rot wird.

⚠️ **Ohne sie ist ein grüner Erstlauf eine Behauptung.** Ein Lauf, der nie rot war, mißt
möglicherweise gar nichts.

- [ ] **Step 5: Basislauf und Sabotage fahren, Ergebnisse notieren**

```bash
bash tools/erstlauf.sh alles
bash tools/erstlauf.sh sabotage alle
```

Beide Ergebnisse **wörtlich** in `CLAUDE.md` eintragen — mit Datum, Commit und der Bemerkung, daß
es ein **Meßprotokoll ist, keine Zusage**.

- [ ] **Step 6: Commit**

```bash
git add tools/ .gitignore CLAUDE.md
git commit -m "Erstlauf: was nur am laufenden Server sichtbar ist

Deckt die Item-Vergabe, das Oeffnen eines Menues, den abgebrochenen Klick
(M12) und die FAWE-Abweisung (M18) - vier Punkte, die kein Einheitstest
beantwortet. Positivkontrolle sabotage gefahren."
```

---

### Task 19: Der Eintrag in TASKS.md

**Files:**
- Modify: `C:/Users/krinc/IdeaProjects/Zanoria-Docs/TASKS.md`

- [ ] **Step 1: Ans ENDE anhängen**

⚠️ **Neues gehört ans Ende**, nicht dazwischen — die Datei wird von oben nach unten abgearbeitet.

Der Eintrag trägt: was gebaut wurde, die gefahrenen Mutationen mit ihrem Ergebnis, die **still
grün** gebliebenen mit ihrem Grund, und die zwei Entscheidungen mit Ablaufbedingung
(BUILDER-Rang, Platzhalter-Texturen).

- [ ] **Step 2: Committen und pushen**

⚠️ `Zanoria-Docs` ist ein eigenes Repo. ⚠️ **`git add` gezielt, nie `-A`** — die Datei war schon
einmal in fremder Hand.

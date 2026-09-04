#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════════════════
#  ERSTLAUF — was nur am laufenden Server sichtbar ist
#
#  Faehrt einen wegwerfbaren Paper-Server, laesst eine Sonde am LAUFENDEN Server
#  HandlerList.getRegisteredListeners(...) lesen und prueft das Protokoll.
#
#  ⚠️ WOGEGEN ER STEHT. Kein Einheitstest dieses Repos sieht eine ANMELDUNG. Ein
#  entferntes registerEvents bleibt gruen (Mutation M5), und eine Bedingung hinter
#  "false &&" steht weiter im Bytecode (Mutation M21). Beides faengt nur ein Server.
#
#  ⚠️ WAS ER NICHT BEANTWORTET: ob Bukkit die Ereignisse in der angenommenen LAGE
#  ausloest. Eine Anmeldung ist keine Ausloesung.
#
#  Aufrufe:
#    bash tools/erstlauf.sh            # Vorbedingungen + Lauf + Auswertung
#    bash tools/erstlauf.sh vorbereiten # nur Vorbedingungen pruefen
# ═══════════════════════════════════════════════════════════════════════════════
set -u

WURZEL="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LAUF="$WURZEL/run/erstlauf"
VORLAGE="${ERSTLAUF_VORLAGE:-/c/Zanoria/templates/coreclash/servers/2x4}"
JDK="${JAVA_HOME:-/c/Program Files/Java/jdk-21}"
PORT="${ERSTLAUF_PORT:-25599}"

LOBBY_JAR="$WURZEL/build/libs/ZanoriaLobby-1.0-SNAPSHOT.jar"
NEXUS_JAR="$WURZEL/../Nexus/build/libs/Nexus-1.0-SNAPSHOT.jar"
ZANLANG_JAR="$WURZEL/../ZanLang/build/libs/zanlang-1.0.jar"
SPIEL_JAR="$WURZEL/../ZanoriaGame/build/libs/ZanoriaGame-1.0-SNAPSHOT.jar"
PAPERAPI="$(find "$HOME/.gradle/caches/modules-2/files-2.1/io.papermc.paper" \
    -name 'paper-api-1.21.11-R0.1-SNAPSHOT.jar' 2>/dev/null | grep -v sources | head -1)"
# ⚠️ paper-api allein reicht NICHT zum Uebersetzen: JavaPlugin erbt ueber Bukkits
#    Schnittstellen von net.kyori.adventure.key.Namespaced, und javac verlangt die
#    Klassendatei. Gemessen am 2026-09-03: "Kein Zugriff auf Namespaced". Die
#    ausgelieferte paper.jar bringt Adventure zur LAUFZEIT mit - der Uebersetzer
#    sieht sie nicht.
ADV_KEY="$(find "$HOME/.gradle/caches/modules-2/files-2.1/net.kyori" \
    -name 'adventure-key-*.jar' 2>/dev/null | grep -v sources | sort -V | tail -1)"
ADV_API="$(find "$HOME/.gradle/caches/modules-2/files-2.1/net.kyori" \
    -name 'adventure-api-*.jar' 2>/dev/null | grep -v sources | sort -V | tail -1)"
# ⚠️ WINDOWS-PFADE, mit Semikolon getrennt. javac ist hier das Windows-JDK: bei EINEM
#    Pfad schreibt MSYS ihn automatisch um, bei einer mit ';' getrennten LISTE nicht
#    mehr - dann meldet es "Package org.bukkit ist nicht vorhanden", und das sieht aus
#    wie ein fehlendes paper-api statt wie ein Pfadproblem. Am 2026-09-03 gemessen.
winpfad() { cygpath -w "$1" 2>/dev/null || echo "$1"; }
SONDENPFAD="$(winpfad "$PAPERAPI");$(winpfad "$ADV_KEY");$(winpfad "$ADV_API")"

rot=0
meldung() { printf '%s %s\n' "$1" "$2"; }
ok()   { meldung "  OK  " "$1"; }
fehl() { meldung "  ROT " "$1"; rot=$((rot+1)); }

# ── Vorbedingungen ────────────────────────────────────────────────────────────
# ⚠️ Sie sind NICHT Vorbereitung, sondern Teil der Pruefung. Wer sie ueberspringt,
#    misst einen alten Stand und meldet gruen - die Form "gebaut != ausgeliefert".
vorbedingungen() {
    echo "── Vorbedingungen ───────────────────────────────────────────────"

    [ -d "$VORLAGE" ] || { fehl "Vorlage fehlt: $VORLAGE"; return; }
    [ -f "$VORLAGE/paper.jar" ] || { fehl "kein paper.jar in $VORLAGE"; return; }
    # ⚠️ Die Vorlage darf NICHT das Ziel sein. In NexusStrike hat genau dieser Fall
    #    einmal 210 MB vernichtet, die kein Git zurueckbringt.
    if [ "$(cd "$VORLAGE" && pwd)" = "$LAUF" ]; then
        fehl "VORLAGE zeigt auf das Ziel ($LAUF) - der Aufbau wuerde sie loeschen"
        return
    fi
    ok "Vorlage: $VORLAGE"

    for j in "$LOBBY_JAR" "$NEXUS_JAR" "$ZANLANG_JAR" "$SPIEL_JAR"; do
        [ -f "$j" ] && ok "Jar da: $(basename "$j")" || fehl "Jar fehlt: $j"
    done
    [ -n "$PAPERAPI" ] && ok "paper-api gefunden" || fehl "paper-api nicht im Gradle-Cache"

    # ⚠️ DER EIGENE STAND IM JAR — an der KLASSE, nicht am Datum.
    echo "  ── eigener Stand im ausgelieferten Jar ──"
    local fehlend=0
    for k in schutz/LobbyWeltschutz menue/Menue menue/chest/ChestMenue \
             menue/chest/ChestKlickhoerer hotbar/Hotbarplatz hotbar/Hotbarhoerer \
             hotbar/Hotbarausgabe hotbar/Lobbybildschirme; do
        if unzip -l "$LOBBY_JAR" 2>/dev/null | grep -q "net/zanoria/lobby/$k.class"; then
            :
        else
            fehl "NICHT im Jar: net/zanoria/lobby/$k.class"; fehlend=$((fehlend+1))
        fi
    done
    [ "$fehlend" = 0 ] && ok "alle 8 gepruefte Klassen im Jar"
    # ⚠️ Positivkontrolle: findet die Suche ueberhaupt etwas? Ohne sie waere ein
    #    kaputter Suchpfad als "alles da" durchgegangen.
    unzip -l "$LOBBY_JAR" 2>/dev/null | grep -q "net/zanoria/lobby/ZanoriaLobby.class" \
        && ok "Positivkontrolle: ZanoriaLobby.class gefunden" \
        || fehl "Positivkontrolle FEHLGESCHLAGEN - die Suche findet nicht einmal die Hauptklasse"

    # ⚠️ ZanLang: die Menue-Schluessel muessen im mavenLocal-Jar liegen, nicht nur in
    #    der Quelle. Der Einheitstest liest die Quelle, das Spiel liest das Jar.
    local ZL_M2="$HOME/.m2/repository/net/zanoria/zanlang/1.0/zanlang-1.0.jar"
    if [ -f "$ZL_M2" ] && unzip -p "$ZL_M2" translations/de_de.json 2>/dev/null | grep -q "lobby.kompass.titel"; then
        ok "ZanLang-Jar in mavenLocal traegt die Menue-Schluessel"
    else
        fehl "ZanLang-Jar in mavenLocal OHNE die Menue-Schluessel - im Spiel stuenden die Titel ROH da"
    fi

    # ⚠️ Nexus: die Rechtekonstante muss im Jar liegen.
    if unzip -p "$NEXUS_JAR" net/zanoria/nexus/permission/NexusPermissions.class 2>/dev/null \
        | grep -aq "zanoria.builder"; then
        ok "Nexus-Jar traegt zanoria.builder"
    else
        fehl "Nexus-Jar OHNE zanoria.builder - das Recht waere nicht vergebbar"
    fi
}

# ── Die erwarteten Hoerer werden ABGELEITET ───────────────────────────────────
# ⚠️ Aus "implements Listener" im Quellbaum, ZEILENUEBERGREIFEND. Aus den
#    registerEvents-Zeilen abgeleitet waere der Fehler, gegen den der ganze Lauf
#    steht: eine geloeschte Anmeldung liesse die Erwartung mitschrumpfen, und die
#    Frage haette sich selbst abgemeldet.
erwartete_hoerer() {
    find "$WURZEL/src/main/java" -name '*.java' -print0 \
    | while IFS= read -r -d '' f; do
        tr '\n' ' ' < "$f" \
        | grep -qE 'class[[:space:]]+[A-Za-z0-9_]+[^{]*implements[^{]*\bListener\b' || continue
        local paket klasse
        paket="$(grep -m1 '^package ' "$f" | sed 's/^package //; s/;.*//')"
        klasse="$(basename "$f" .java)"
        echo "$paket.$klasse"
    done | sort -u
}

aufbauen() {
    echo "── Aufbau ───────────────────────────────────────────────────────"
    rm -rf "$LAUF"; mkdir -p "$LAUF/plugins"
    cp "$VORLAGE/paper.jar" "$LAUF/paper.jar"
    # ⚠️ eula.txt wird KOPIERT, nicht geschrieben. Die Zustimmung gehoert dem
    #    Betreiber; sie steht in seiner eigenen Vorlage.
    if [ -f "$VORLAGE/eula.txt" ]; then
        cp "$VORLAGE/eula.txt" "$LAUF/eula.txt"; ok "eula.txt aus der Vorlage uebernommen"
    else
        fehl "keine eula.txt in der Vorlage - dieses Skript schreibt sie NICHT selbst"
        return 1
    fi

    cat > "$LAUF/server.properties" <<PROPS
server-port=$PORT
online-mode=false
level-type=minecraft\:flat
generate-structures=false
spawn-protection=0
max-players=5
motd=Erstlauf ZanoriaLobby
level-name=world
PROPS

    cp "$LOBBY_JAR"   "$LAUF/plugins/ZanoriaLobby.jar"
    cp "$NEXUS_JAR"   "$LAUF/plugins/Nexus.jar"
    cp "$ZANLANG_JAR" "$LAUF/plugins/ZanLang.jar"
    cp "$SPIEL_JAR"   "$LAUF/plugins/ZanoriaGame.jar"

    # Sonde uebersetzen
    local S="$LAUF/.sonde"
    mkdir -p "$S/classes"
    "$JDK/bin/javac" -encoding UTF-8 -cp "$SONDENPFAD" -d "$S/classes" \
        "$WURZEL/tools/erstlauf-sonde/Sonde.java" 2>"$S/javac.log" \
        || { fehl "Sonde uebersetzt nicht - siehe $S/javac.log"; return 1; }
    cp "$WURZEL/tools/erstlauf-sonde/plugin.yml" "$S/classes/plugin.yml"
    (cd "$S/classes" && "$JDK/bin/jar" cf "$LAUF/plugins/ErstlaufSonde.jar" .) \
        || { fehl "Sonde laesst sich nicht packen"; return 1; }
    ok "Sonde uebersetzt und eingelegt"
    ok "Server aufgebaut in $LAUF"
}

# ⚠️ Beendet NUR den Wegwerf-Server, erkannt an "-jar paper.jar" in seiner Kommandozeile.
#    Nicht ueber die Bash-PID (die ist nicht die Windows-PID) und nicht ueber den
#    Prozessnamen (das traefe den Gradle-Daemon mit).
server_toeten() {
    MSYS_NO_PATHCONV=1 powershell.exe -NoProfile -Command       "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { \$_.CommandLine -like '*-jar paper.jar*' } | ForEach-Object { Stop-Process -Id \$_.ProcessId -Force }"       >/dev/null 2>&1
    sleep 2
}

fahren() {
    echo "── Lauf ─────────────────────────────────────────────────────────"
    # ⚠️ ZUERST auf den freien Port warten. Am 2026-09-03 gemessen: der zweite Lauf
    #    direkt nach dem ersten scheiterte mit "FAILED TO BIND TO PORT", der Server
    #    stuerzte ab - und die Auswertung meldete daraufhin NEUN rote Punkte, die alle
    #    wie Codefehler aussahen. Ein Umgebungsproblem muss sich als solches melden,
    #    sonst sucht der Naechste eine Stunde an der falschen Stelle.
    local w=0
    while MSYS_NO_PATHCONV=1 netstat.exe -an 2>/dev/null | grep -qE "[:.]$PORT[[:space:]].*(LISTENING|ABHÖREN|ABH.REN|WARTEND|TIME_WAIT)"; do
        [ $w -ge 90 ] && { fehl "Port $PORT ist seit 90s belegt - der Lauf misst NICHTS. Setz ERSTLAUF_PORT auf einen freien Port."; return 1; }
        [ $w = 0 ] && echo "  ...  warte auf freien Port $PORT"
        sleep 2; w=$((w+2))
    done
    [ $w -gt 0 ] && ok "Port $PORT nach ${w}s frei"
    # ⚠️ exec, damit $! WIRKLICH der Java-Prozess ist und nicht die Subshell. Ohne das traf
    #    das kill nur die Huelle: der Server lief weiter, hielt Port und Jars fest, und der
    #    NAECHSTE Lauf scheiterte am Binden - was dann wie ein Codefehler aussah.
    #    Am 2026-09-03 genau so passiert, zweimal.
    ( cd "$LAUF" && printf 'stop\n' | exec "$JDK/bin/java" -Xmx1G -jar paper.jar --nogui \
        > "$LAUF/konsole.log" 2>&1 ) &
    local pid=$!
    # ⚠️ Auf einen ZUSTAND warten, nicht auf eine Uhr. Ein fester Schlaf ist ein
    #    Wettlauf, und wer ihn verliert, bekommt einen Fehlbefund.
    local i=0
    while [ $i -lt 200 ]; do
        grep -q "SONDE-FERTIG" "$LAUF/konsole.log" 2>/dev/null && break
        kill -0 $pid 2>/dev/null || break
        sleep 1; i=$((i+1))
    done
    sleep 2
    kill $pid 2>/dev/null
    wait $pid 2>/dev/null
    # ⚠️ Und danach NACHSEHEN, ob er wirklich weg ist. Ein "kill" ohne Nachkontrolle ist
    #    dieselbe Bauart wie ein Rueckgabewert ohne Messung: unter Windows ueberlebt der
    #    Java-Prozess das Signal der Shell mitunter, haelt Port und Jars, und der naechste
    #    Lauf misst dann nichts.
    # ⚠️ GEZIELT ueber die KOMMANDOZEILE, nicht ueber $pid und nicht ueber den Namen.
    #    $pid ist die Bash-PID und nicht die Windows-PID - ein taskkill /PID darauf trifft
    #    nichts (am 2026-09-03 gemessen: der Server lief weiter). Und /IM java.exe wuerde
    #    den GRADLE-DAEMON mit abschiessen, der daneben laeuft.
    #    Erkannt wird er an "-jar paper.jar"; das trifft nur diesen Wegwerf-Server.
    server_toeten
    ok "Lauf beendet nach ${i}s (Protokoll: $LAUF/konsole.log)"
}

auswerten() {
    echo "── Auswertung ───────────────────────────────────────────────────"
    local L="$LAUF/konsole.log"
    [ -f "$L" ] || { fehl "kein Protokoll"; return; }

    grep -q "SONDE-FERTIG" "$L" \
        && ok "1  Die Sonde ist gelaufen" \
        || fehl "1  Die Sonde ist NIE fertig geworden - alles Weitere waere ein leerer Sucher"

    grep -q "ZanoriaLobby enabled with" "$L" \
        && ok "2  ZanoriaLobby ist hochgekommen" \
        || fehl "2  ZanoriaLobby ist NICHT hochgekommen"

    # 3: jeder abgeleitete Hoerer hat eine SONDE-HOERER-Zeile
    local fehlend=0 anzahl=0
    while read -r h; do
        [ -z "$h" ] && continue
        anzahl=$((anzahl+1))
        if grep -q "SONDE-HOERER: $h " "$L"; then
            continue
        fi
        # ⚠️ EINE EINZIGE begruendete Ausnahme, und sie haengt an einem BELEG im Protokoll,
        #    nicht an einem Namen in einer Liste. Bukkit meldet selbst, wenn es eine
        #    Hoererklasse verwirft, weil eine Nexus-Klasse in einer ihrer Signaturen fehlt:
        #      "Failed to register events for class X because net/zanoria/nexus/... does not exist"
        #    Ohne Datenbank kommt Nexus nicht hoch, und dann KANN diese Klasse ihre
        #    Warteschlangen-Griffe nicht anmelden - die Pruefung fragt hier nach etwas, das in
        #    DIESEM Lauf nicht existiert.
        #
        # ⚠️ Die Ausnahme greift NUR mit dem Belegsatz. Fehlt der Hoerer aus einem anderen
        #    Grund, bleibt der Punkt rot - sonst waere das eine abgeschaltete Pruefung mit
        #    einem freundlichen Namen.
        if grep -q "Failed to register events for class $h because net/zanoria/nexus/" "$L"; then
            meldung "  HINW" "3  $h nicht angemeldet - Bukkit hat sie VERWORFEN, weil Nexus fehlt (Beleg im Protokoll). Ohne Datenbank ist das der erwartete Zustand."
            continue
        fi
        fehl "3  Hoerer NICHT angemeldet: $h"; fehlend=$((fehlend+1))
    done < <(erwartete_hoerer)
    if [ "$anzahl" = 0 ]; then
        fehl "3  Die Ableitung fand KEINEN Hoerer - dann prueft dieser Punkt nichts"
    elif [ "$fehlend" = 0 ]; then
        ok "3  alle $anzahl abgeleiteten Hoerer sind angemeldet"
    fi

    # 4: keine ungebundene @EventHandler-Methode
    if grep -q "SONDE-EREIGNISBINDUNG:" "$L"; then
        local zeile; zeile="$(grep -m1 'SONDE-EREIGNISBINDUNG:' "$L")"
        # ⚠️ NULL DEKLARIERTE IST KEIN ERFOLG. Die erste Fassung meldete bei
        #    "0 deklariert, 0 gebunden, 0 ungebunden" ein OK - ein leerer Sucher, der
        #    Erfolg meldet, und zwar im Werkzeug, das genau davor schuetzen soll.
        #    Gemessen am 2026-09-03: als ZanoriaLobby gar nicht hochkam, stand hier OK.
        if echo "$zeile" | grep -qE ': 0 deklariert'; then
            fehl "4  ${zeile#*SONDE-EREIGNISBINDUNG: } - NULL deklarierte Griffe sind kein Befund, sondern ein leerer Sucher"
        elif echo "$zeile" | grep -q ", 0 ungebunden"; then
            ok "4  ${zeile#*SONDE-EREIGNISBINDUNG: }"
        else
            fehl "4  ${zeile#*SONDE-EREIGNISBINDUNG: }"
        fi
    else
        fehl "4  keine Ereignisbindungs-Zeile"
    fi

    # 5: /builder ist angemeldet UND traegt das Recht
    if grep -q "SONDE-BEFEHL: builder -> permission=zanoria.builder" "$L"; then
        ok "5  /builder angemeldet, permission=zanoria.builder"
    else
        fehl "5  /builder fehlt oder traegt das Recht nicht: $(grep -m1 'SONDE-BEFEHL: builder' "$L")"
    fi

    echo
    if [ "$rot" = 0 ]; then
        echo "ERSTLAUF: GRUEN"
    else
        echo "ERSTLAUF: ROT ($rot Punkt(e))"
    fi
}

case "${1:-alles}" in
    vorbereiten) vorbedingungen ;;
    *) vorbedingungen
       if [ "$rot" = 0 ]; then aufbauen && fahren; fi
       auswerten ;;
esac

exit $([ "$rot" = 0 ] && echo 0 || echo 1)

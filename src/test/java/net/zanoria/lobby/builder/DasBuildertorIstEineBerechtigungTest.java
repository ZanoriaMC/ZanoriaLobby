package net.zanoria.lobby.builder;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ⚠️ Zwei Zusicherungen, und beide haben einen gemessenen Anlass.
 *
 * <p><b>1. Der Befehl haengt an einer Berechtigung.</b> Bis zum 2026-09-03 trug {@code /builder}
 * in {@code plugin.yml} <b>kein</b> {@code permission:}-Feld; das Tor war eine Redis-Namensliste.
 * Es war also nie ein offener Befehl — aber der Schluessel war der <b>Name</b>, und eine
 * Namensaenderung verschob oder verlor den Zugang, lautlos.
 *
 * <p><b>2. Der Dienst fragt die Liste NICHT mehr.</b> Ein Kommentar allein haelt niemanden auf:
 * wer sie zurueckverdrahtet, macht das Tor wieder namensabhaengig, und niemand wuerde es bemerken.
 *
 * <h2>⚠️ Warum dieser Fall Kommentare wegschneidet</h2>
 *
 * <p>Die Stelle, an der {@code isMember} frueher stand, traegt heute einen Kommentar, der das Wort
 * <b>nennt</b> — er erklaert ja gerade, was dort nicht mehr steht. Eine rohe Textsuche faende ihn
 * und meldete einen Verstoss, den es nicht gibt.
 *
 * <p>Der umgekehrte Fehler ist in diesem Stack schon passiert: dort hat eine Quelltextsuche einen
 * <b>Kommentar fuer eine Anmeldung gehalten</b> und war gruen, obwohl die Anmeldung fehlte. Beide
 * Richtungen haben dieselbe Ursache — eine Suche, die Text liest statt Code.
 *
 * <p>⚠️ <b>Das Schneiden wird selbst geprueft</b> ({@link #dasSchneidenSchneidetWirklich}). Ohne
 * das waere ein Schneider, der gar nichts entfernt, unsichtbar: die Suche liefe dann wieder ueber
 * den Rohtext, und niemand saehe es.
 */
class DasBuildertorIstEineBerechtigungTest {

    private static final Path DIENST =
            Path.of("src/main/java/net/zanoria/lobby/builder/BuilderServerService.java");
    private static final Path DESKRIPTOR = Path.of("src/main/resources/plugin.yml");

    /** Schneidet Zeilen- und Blockkommentare weg. Zeichenketten bleiben unangetastet. */
    static String ohneKommentare(String quelle) {
        StringBuilder aus = new StringBuilder(quelle.length());
        boolean inZeichenkette = false;
        boolean inZeichen = false;
        boolean inZeile = false;
        boolean inBlock = false;
        for (int i = 0; i < quelle.length(); i++) {
            char c = quelle.charAt(i);
            char n = i + 1 < quelle.length() ? quelle.charAt(i + 1) : '\0';
            if (inZeile) {
                if (c == '\n') { inZeile = false; aus.append(c); }
                continue;
            }
            if (inBlock) {
                if (c == '*' && n == '/') { inBlock = false; i++; }
                continue;
            }
            if (inZeichenkette) {
                aus.append(c);
                if (c == '\\') { if (i + 1 < quelle.length()) { aus.append(n); i++; } }
                else if (c == '"') { inZeichenkette = false; }
                continue;
            }
            if (inZeichen) {
                aus.append(c);
                if (c == '\\') { if (i + 1 < quelle.length()) { aus.append(n); i++; } }
                else if (c == '\'') { inZeichen = false; }
                continue;
            }
            if (c == '/' && n == '/') { inZeile = true; i++; continue; }
            if (c == '/' && n == '*') { inBlock = true; i++; continue; }
            if (c == '"') { inZeichenkette = true; aus.append(c); continue; }
            if (c == '\'') { inZeichen = true; aus.append(c); continue; }
            aus.append(c);
        }
        return aus.toString();
    }

    private static String lies(Path p) throws IOException {
        assertTrue(Files.exists(p), "⚠️ " + p + " nicht gefunden - dann prueft dieser Fall"
                + " nichts, und er saehe trotzdem gruen aus.");
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("⚠️ Das Kommentar-Schneiden schneidet wirklich - sonst misst der Fall unten Text")
    void dasSchneidenSchneidetWirklich() {
        // ⚠️ An erfundenen Schnipseln, deren Antwort vorher feststeht. Am echten Quelltext
        // gemessen aendert sich jede Zahl mit dem naechsten Commit.
        // ⚠️ Das Leerzeichen VOR dem // bleibt stehen - der Schneider entfernt den Kommentar,
        // nicht den Zwischenraum. Die erste Fassung dieser Erwartung liess es weg und war rot;
        // richtig war der Schneider, falsch meine Erwartung.
        assertEquals("int a = 1; \n", ohneKommentare("int a = 1; // isMember\n"));
        assertEquals("int a = 1;", ohneKommentare("/* isMember */int a = 1;"));
        assertEquals("String s = \"// kein Kommentar\";",
                ohneKommentare("String s = \"// kein Kommentar\";"));
        assertEquals("char c = '\"'; int a = 1;",
                ohneKommentare("char c = '\"'; int a = 1;"),
                "⚠️ Ein Zeichenliteral mit Anfuehrungszeichen darf keine Zeichenkette oeffnen -"
                        + " sonst frisst der Schneider den Rest der Datei.");
        // ⚠️ Und die Gegenrichtung: was KEIN Kommentar ist, bleibt stehen.
        assertTrue(ohneKommentare("if (redis.isMember(x)) {}").contains("isMember"));
    }

    @Test
    @DisplayName("⚠️ /builder haengt an zanoria.builder")
    void derBefehlHaengtAnDerBerechtigung() throws IOException {
        String yml = lies(DESKRIPTOR);

        // ⚠️ Positivkontrolle zuerst: findet die Suche ueberhaupt den Befehl?
        assertTrue(yml.contains("  builder:"),
                "⚠️ In plugin.yml steht gar kein builder-Befehl - die Suchen unten waeren dann"
                        + " gratis gruen.");
        assertTrue(yml.contains("permission: zanoria.builder"),
                "⚠️ /builder traegt kein permission-Feld. Bis 2026-09-03 war genau das der"
                        + " Zustand, und das Tor war eine Redis-Namensliste.");
        assertTrue(yml.contains("zanoria.builder:"),
                "⚠️ zanoria.builder ist nicht DEKLARIERT. Ein nicht deklariertes Recht"
                        + " beantwortet Bukkit mit isOp() - und die Rangvergabe aus Nexus liefe"
                        + " ins Leere, ohne dass etwas rot wird.");
    }

    @Test
    @DisplayName("⚠️ Der Dienst fragt die stillgelegte Namensliste nirgends mehr")
    void dieListeEntscheidetNichts() throws IOException {
        String code = ohneKommentare(lies(DIENST));

        // ⚠️ Positivkontrolle: liest der Fall wirklich die richtige Datei, und hat das Schneiden
        // nicht alles weggeworfen?
        assertTrue(code.contains("joinBuilderServer"),
                "⚠️ Im geschnittenen Text steht kein joinBuilderServer - entweder stimmt der Pfad"
                        + " nicht mehr, oder der Schneider hat zu viel entfernt. In beiden Faellen"
                        + " faende die Suche unten garantiert nichts.");

        assertFalse(code.contains("isMember"),
                "⚠️ BuilderServerService fragt wieder isMember. Damit haengt der Zutritt erneut am"
                        + " SPIELERNAMEN - eine Namensaenderung verschiebt oder verliert ihn,"
                        + " lautlos. Die Liste ist am 2026-09-03 stillgelegt worden; der Grund"
                        + " steht ueber KEY_MEMBERS in BuilderRedisClient.");
    }

    @Test
    @DisplayName("⚠️ Der Zutritt faellt ZU, wenn der Builder-Server kein FAWE meldet")
    void ohneFaweMeldungBleibtEsZu() throws IOException {
        String code = ohneKommentare(lies(DIENST));

        assertTrue(code.contains("getWorldedit()"),
                "⚠️ Der Dienst fragt die FAWE-Meldung nicht mehr. Dann landet ein Spieler auf"
                        + " einem Builder-Server ohne das Werkzeug, fuer das er hingeschickt"
                        + " wurde - und stellt dort fest, dass kein einziger Befehl geht.");
        assertTrue(code.contains("\"FEHLT\""),
                "⚠️ Der Dienst prueft den FEHLT-Fall nicht. Der Builder-Server schreibt genau"
                        + " diese Zeichenkette, wenn WorldEdit/FAWE nicht geladen ist.");
        assertTrue(code.contains("worldedit == null"),
                "⚠️ Ein FEHLENDER Schluessel wird nicht geprueft. Der faellt sonst durch - und"
                        + " ein fehlender Schluessel ist kein Grund durchzulassen, sondern der"
                        + " haeufigste Fall: der Builder-Server war nie an.");
    }
}

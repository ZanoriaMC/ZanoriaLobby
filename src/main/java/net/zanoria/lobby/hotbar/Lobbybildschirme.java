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
 * <p>Entschieden vom Betreiber am 2026-09-03: <i>„so einfach wie moeglich — die Menues werden mit
 * ZanUI ohnehin neu gebaut. Sie muessen funktionieren und austauschbar sein, sonst nichts."</i>
 * Jeder Bildschirm hat genau die Eintraege, die er braucht, um <b>etwas zu oeffnen</b>. Wer hier
 * Inhalt ausbaut, baut Arbeit, die der Tausch wegwirft.
 *
 * <p>⚠️ <b>Die Handlungen sind heute leer, und das ist gemeldet statt versteckt:</b>
 * {@code DieFuenfBildschirmeStehenTest} verlangt, dass jeder Bildschirm Eintraege hat — nicht,
 * dass sie schon etwas tun. Was ein Eintrag tun soll, ist eine Produktentscheidung je Bildschirm
 * und gehoert nicht in diese Arbeit.
 */
public final class Lobbybildschirme {

    private Lobbybildschirme() {
    }

    /**
     * Der Bildschirm hinter einem Hotbar-Item. Nie {@code null}.
     *
     * <p>⚠️ {@code switch} <b>ohne {@code default}</b>: kommt ein sechstes Item dazu, bricht der
     * <b>Bau</b>. Ein {@code default} liesse es stillschweigend auf den Kompass zeigen — und der
     * Auftrag „alle fuenf sollen etwas oeffnen" waere formal erfuellt und inhaltlich gebrochen.
     */
    public static Bildschirm fuer(Hotbarplatz platz) {
        return switch (platz) {
            case KOMPASS -> Bildschirm.of(platz.bildschirmKennung(), "lobby.kompass.titel",
                    List.of(
                            eintrag("lobby.kompass.relicwars",
                                    "zanoria:kompass_relicwars", "IRON_SWORD"),
                            eintrag("lobby.kompass.coreclash",
                                    "zanoria:kompass_coreclash", "BEACON"),
                            eintrag("lobby.kompass.nexusstrike",
                                    "zanoria:kompass_nexusstrike", "END_PORTAL_FRAME")));
            case RUCKSACK -> Bildschirm.of(platz.bildschirmKennung(), "lobby.rucksack.titel",
                    List.of(eintrag("lobby.rucksack.leer",
                            "zanoria:rucksack_leer", "BARRIER")));
            case BATTLEPASS -> Bildschirm.of(platz.bildschirmKennung(), "lobby.battlepass.titel",
                    List.of(eintrag("lobby.battlepass.stufe",
                            "zanoria:battlepass_stufe", "EXPERIENCE_BOTTLE")));
            case SOZIAL -> Bildschirm.of(platz.bildschirmKennung(), "lobby.sozial.titel",
                    List.of(
                            eintrag("lobby.sozial.freunde",
                                    "zanoria:sozial_freunde", "PLAYER_HEAD"),
                            eintrag("lobby.sozial.gruppe",
                                    "zanoria:sozial_gruppe", "LEAD")));
            case EINSTELLUNGEN -> Bildschirm.of(platz.bildschirmKennung(),
                    "lobby.einstellungen.titel",
                    List.of(
                            eintrag("lobby.einstellungen.sprache",
                                    "zanoria:einstellungen_sprache", "PAPER"),
                            eintrag("lobby.einstellungen.sichtbarkeit",
                                    "zanoria:einstellungen_sicht", "ENDER_EYE")));
        };
    }

    private static Eintrag eintrag(String schluessel, String nexo, String rueckfall) {
        return Eintrag.of(schluessel, Sinnbild.of(nexo, rueckfall), spieler -> { });
    }
}

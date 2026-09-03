package net.zanoria.lobby.hotbar;

import net.zanoria.lobby.menue.Sinnbild;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Die fuenf festen Hotbar-Plaetze der Lobby — <b>und welche davon noch Platzhalter sind</b>.
 *
 * <h2>⚠️ Warum die Platzhalter-Markierung im WERT steht und nicht im Kommentar</h2>
 *
 * <p>Es gibt heute keine eigenen Texturen. Das ist in Ordnung — <b>nicht</b> in Ordnung waere,
 * dass sie in drei Monaten immer noch fehlen und niemand es merkt, weil das Spiel laeuft und ein
 * Kompass eben auch ein Kompass ist. <b>Ein Platzhalter ohne Meldung ist ein Platzhalter fuer
 * immer.</b>
 *
 * <p>Deshalb traegt jeder Eintrag sein {@link #istPlatzhalter()} als <b>Feld</b>, und der
 * Gradle-Task {@code :platzhalterwaechter} liest es bei jedem Bau aus dem uebersetzten Enum.
 *
 * <h2>⚠️ Der Vanilla-Rueckfall ist ebenfalls geraten, nicht nur die Textur</h2>
 *
 * <p>Kein Dokument nennt fuer diese fuenf Items einen Gegenstand; {@code BUNDLE} fuer den Rucksack
 * und {@code COMPARATOR} fuer die Einstellungen sind eine Wahl. Sie fallen unter dasselbe
 * {@code istPlatzhalter()} — ein zweiter Waechter nur fuer Materialien waere hier Aufwand ohne
 * Gegenwert, weil Nexo auf {@code lobby-1} liegt und der Rueckfall im Normalbetrieb gar nicht
 * gezeigt wird. <b>Sichtbar wird er genau dann, wenn Nexo fehlt</b> — also im Stoerungsfall, und
 * dort ist „sieht anders aus" das gewollte Verhalten gegenueber „ist weg".
 *
 * <h2>⚠️ Die Plaetze sind eine Vorgabe, keine Ableitung</h2>
 *
 * <p>0/1/2 und 7/8 lassen die Mitte frei. Das ist die Entscheidung des Betreibers und keine
 * Rechnung — wer sie aendert, aendert eine Vorgabe.
 */
public enum Hotbarplatz {

    // ⚠️ DIE BESCHRIFTUNGSSCHLUESSEL SIND GEMESSEN, NICHT ERFUNDEN. Alle fuenf stehen seit
    // laengerem in ZanLangs translations/de_de.json - mit fertigen Uebersetzungen: "Wegweiser",
    // "Rucksack", "Battle Pass", "Freunde", "Einstellungen". Wer hier eigene Schluessel anlegt
    // (etwa "lobby.hotbar.kompass"), erzeugt eine zweite Wahrheit und laesst die vorhandenen
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

    /** Nexo-Kennung und Vanilla-Rueckfall. */
    public Sinnbild sinnbild() {
        return sinnbild;
    }

    /** Der ZanLang-Schluessel des Item-Namens. ⚠️ Ein Schluessel, kein fertiger Satz. */
    public String beschriftungSchluessel() {
        return beschriftungSchluessel;
    }

    /** Die Kennung des Bildschirms, den dieses Item oeffnet. */
    public String bildschirmKennung() {
        return "lobby." + name().toLowerCase(Locale.ROOT);
    }

    /**
     * Ob dieser Eintrag noch auf einen Platzhalter zeigt — also ob die Textur noch fehlt.
     *
     * <p>⚠️ Wer eine echte Textur liefert, setzt hier {@code false} <b>und</b> traegt den Eintrag
     * in {@code DieHotbarNenntIhrePlatzhalterTest} aus. Beides zusammen, sonst wird der Fall rot —
     * das ist Absicht: eine geloeste Luecke, deren Beschreibung stehenbleibt, ist dieselbe Luege
     * wie ein Platzhalter, den niemand meldet.
     */
    public boolean istPlatzhalter() {
        return platzhalter;
    }

    /** Alle Eintraege, die noch auf Platzhalter zeigen. Der Waechter liest genau das. */
    public static List<Hotbarplatz> platzhalter() {
        return Arrays.stream(values()).filter(Hotbarplatz::istPlatzhalter).toList();
    }
}

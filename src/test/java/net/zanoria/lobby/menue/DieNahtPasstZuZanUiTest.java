package net.zanoria.lobby.menue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ⚠️ Diese Faelle halten die Naht in der Form, die ZanUI spaeter erfuellen kann.
 *
 * <h2>Der Befund, der die Form entscheidet</h2>
 *
 * <p>Gemessen am 2026-09-03 in {@code IdeaProjects/ZanUI}: ZanUI ist <b>keine
 * Kisten-Oberflaeche</b>, sondern die Paper-Dialog-API. Ein {@code UiScreen} ist Kennung + Titel +
 * Rumpf + {@code List<UiButton>}, ein {@code UiButton} ist Beschriftung + Breite + Hinweis +
 * Handlung. <b>Es gibt dort keine Slots.</b> {@code ZanUiService.open(Player, UiScreen)} gibt
 * {@code boolean} zurueck und wirft nicht.
 *
 * <p>ZanUI sagt es im Kopf von {@code UiScreen} ueber sich selbst:
 * <i>"ein Item-Name friert beim Setzen ein, ein Dialog wird bei jedem Oeffnen neu gebaut"</i>.
 *
 * <h2>⚠️ Wer hier ein Feld fuer eine Slot-Nummer ergaenzt, bricht die Auflage</h2>
 *
 * <p>Eine Schnittstelle mit Slot-Nummern und {@code ItemStack}s koennte ZanUI <b>nie</b> erfuellen.
 * Der Tausch muesste dann die Menuedefinitionen anfassen und damit die Hoerer - genau das, was die
 * Auflage des Betreibers ausschliesst: <i>"ohne die Hoerer anzufassen. Sonst ist 'experimentell'
 * nur ein Wort, und in drei Monaten steht es noch da."</i>
 *
 * <p>Dabei wird <b>nichts rot</b>, das der Umsetzung gehoert - ein Slot-Feld uebersetzt
 * einwandfrei. Deshalb steht die Begruendung hier, im Test, und nicht in einem Bericht.
 */
class DieNahtPasstZuZanUiTest {

    private static Eintrag einEintrag() {
        return Eintrag.of("lobby.kompass.relicwars",
                Sinnbild.of("zanoria:lobby_kompass", "COMPASS"),
                spieler -> { });
    }

    @Test
    @DisplayName("Ein Bildschirm traegt Kennung, Titelschluessel und Eintraege")
    void dieFormStimmt() {
        Bildschirm b = Bildschirm.of("lobby.kompass", "lobby.kompass.titel",
                List.of(einEintrag()));

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
    @DisplayName("⚠️ Ein Sinnbild ohne Vanilla-Rueckfall wird abgewiesen")
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
        // aufgeloest. Ein fertiger Satz im Eintrag froere die Sprache beim Anlegen ein - und
        // derselbe Bildschirm koennte nicht gleichzeitig fuer zwei Spieler in zwei Sprachen
        // offen sein.
        Eintrag e = einEintrag();
        assertEquals("lobby.kompass.relicwars", e.beschriftungSchluessel());
    }
}

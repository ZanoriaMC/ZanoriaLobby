package net.zanoria.lobby.menue;

import org.bukkit.entity.Player;

/**
 * <b>Die Naht.</b> Heute erfuellt von {@code menue.chest.ChestMenue}, spaeter von ZanUI.
 *
 * <h2>⚠️ Wozu es diese Schnittstelle gibt</h2>
 *
 * <p>ZanUI ist noch nicht so weit, und die Kisten-Umsetzung daneben ist ausdruecklich
 * <b>EXPERIMENTELL</b>. Die Auflage des Betreibers lautet woertlich: <i>„Es muss spaeter gegen
 * ZanUI austauschbar sein, ohne die Hoerer anzufassen. Sonst ist 'experimentell' nur ein Wort, und
 * in drei Monaten steht es noch da."</i>
 *
 * <p>Deshalb kennt <b>kein Hoerer dieses Repos einen Kisten-Typ</b>. Das haelt
 * {@code :kistenwaechter} maschinell fest - <b>nicht dieser Kommentar</b>.
 *
 * <h2>⚠️ Der Rueckgabewert ist keine Zierde</h2>
 *
 * <p>Dieselbe Zusage wie {@code ZanUiService.open}: die Umsetzung <b>wirft nicht</b>. Scheitert
 * das Oeffnen, wird es protokolliert und {@code false} zurueckgegeben.
 *
 * <p>Der Grund ist bei ZanUI im Spiel entstanden: eine durchgereichte Ausnahme kam beim Spieler
 * als „An unexpected error occurred trying to execute that command" an - und niemand erfuhr, was.
 *
 * <p>Wer den Rueckgabewert ignoriert, laeuft im Fehlerfall weiter, als waere das Menue offen. Das
 * ist eine Entscheidung, aber eine <b>sichtbare</b>.
 */
public interface Menue {

    /**
     * Oeffnet einen Bildschirm fuer einen Spieler.
     *
     * @return ob er wirklich offen ist
     */
    boolean oeffne(Player spieler, Bildschirm bildschirm);
}

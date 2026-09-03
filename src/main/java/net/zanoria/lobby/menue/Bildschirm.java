package net.zanoria.lobby.menue;

import java.util.List;
import java.util.Objects;

/**
 * Ein Menuebildschirm: Kennung, Titelschluessel, Eintraege.
 *
 * <p>Die Form spiegelt ZanUis {@code UiScreen} (Kennung + Titel + Knoepfe), damit ZanUI sie
 * spaeter <b>eins zu eins</b> erfuellt.
 *
 * <h2>⚠️ Er traegt Schluessel, keine fertigen Saetze</h2>
 *
 * <p>Aufgeloest wird beim <b>Oeffnen</b>, fuer die Sprache genau dieses Spielers. Das ist keine
 * Stilfrage: ZanUI baut einen Dialog bei jedem Oeffnen neu, und derselbe Bildschirm kann dort
 * gleichzeitig fuer zwei Spieler in zwei Sprachen offen sein. Ein fertiger Satz hier froere die
 * Sprache beim Anlegen ein - und die Naht waere mit ZanUI nicht mehr deckungsgleich.
 *
 * <p>⚠️ Die Kennung ist Pflicht und darf nicht leer sein: ohne sie ist der Bildschirm nicht
 * wiederzufinden - ZanUI braucht sie fuer {@code openById} und {@code UiAction.OpenScreen}.
 */
public record Bildschirm(String kennung, String titelSchluessel, List<Eintrag> eintraege) {

    public Bildschirm {
        Objects.requireNonNull(kennung, "kennung");
        Objects.requireNonNull(titelSchluessel, "titelSchluessel");
        Objects.requireNonNull(eintraege, "eintraege");
        if (kennung.isBlank()) {
            throw new IllegalArgumentException(
                    "Ein Bildschirm ohne Kennung ist nicht zu finden");
        }
        eintraege = List.copyOf(eintraege);
    }

    public static Bildschirm of(String kennung, String titelSchluessel, List<Eintrag> eintraege) {
        return new Bildschirm(kennung, titelSchluessel, eintraege);
    }
}

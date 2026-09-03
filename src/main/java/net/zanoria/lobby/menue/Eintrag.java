package net.zanoria.lobby.menue;

import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Ein anklickbarer Eintrag in einem {@link Bildschirm}.
 *
 * <h2>⚠️ Kein Platz, keine Slot-Nummer — und das ist die tragende Entscheidung</h2>
 *
 * <p>ZanUI kennt nur <b>Knoepfe</b> ({@code UiButton}: Beschriftung, Breite, Hinweis, Handlung);
 * die Anordnung entscheidet dort die Umsetzung. Wer hier eine Platznummer ergaenzt, macht die
 * Naht fuer ZanUI <b>unerfuellbar</b> - und der Tausch muesste dann jede Menuedefinition und damit
 * jeden Hoerer anfassen.
 *
 * <p>⚠️ Das uebersetzt einwandfrei und macht <b>nichts</b> rot. Deshalb steht der Grund hier und
 * in {@code DieNahtPasstZuZanUiTest}, statt sich auf einen Waechter zu verlassen.
 *
 * <p>⚠️ Die Beschriftung ist ein <b>Schluessel</b>, kein fertiger Satz - siehe {@link Bildschirm}.
 */
public record Eintrag(String beschriftungSchluessel, Sinnbild sinnbild, Consumer<Player> handlung) {

    public Eintrag {
        Objects.requireNonNull(beschriftungSchluessel, "beschriftungSchluessel");
        Objects.requireNonNull(sinnbild, "sinnbild");
        Objects.requireNonNull(handlung, "handlung");
    }

    public static Eintrag of(String beschriftungSchluessel, Sinnbild sinnbild,
                             Consumer<Player> handlung) {
        return new Eintrag(beschriftungSchluessel, sinnbild, handlung);
    }
}

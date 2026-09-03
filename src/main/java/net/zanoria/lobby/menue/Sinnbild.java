package net.zanoria.lobby.menue;

import java.util.Objects;

/**
 * Wie ein Eintrag aussieht: eine Nexo-Kennung mit einem Vanilla-Rueckfall.
 *
 * <p>⚠️ <b>Der Rueckfall ist ein {@code String} und kein Bukkit-{@code Material}.</b> Der Grund
 * ist gemessen, nicht gewaehlt (2026-09-03, {@code DieRegistrygrenzeIstGemessenTest}):
 * {@code new ItemStack(Material)} wirft ohne laufenden Server einen
 * {@code ExceptionInInitializerError} mit der Ursache
 * {@code IllegalStateException: No RegistryAccess implementation found}. Waere hier ein
 * {@code Material}, liesse sich diese Klasse in keinem Einheitstest anlegen - und damit auch kein
 * {@link Bildschirm} und kein {@link Eintrag}.
 *
 * <p>Die Aufloesung in ein echtes {@code Material} passiert in {@code menue.chest}, an genau einer
 * Stelle.
 *
 * <p>⚠️ <b>Der Rueckfall ist Pflicht, und zwar dieselbe Zusage wie ZanUis {@code UiSymbol}.</b>
 * Fehlt Nexo auf dem Server, fehlt das Item oder fehlt die Kennung, erscheint der Eintrag ohne
 * Rueckfall <b>gar nicht</b> - ohne Ausnahme und ohne Logzeile. Er ist der Unterschied zwischen
 * „sieht anders aus" und „ist weg".
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

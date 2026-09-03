package net.zanoria.lobby.menue.chest;

import net.zanoria.lobby.menue.Sinnbild;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Welcher Vanilla-Materialname fuer ein {@link Sinnbild} genommen wird — als <b>Zeichenkette</b>.
 *
 * <p>⚠️ Getrennt von {@link ChestMenue}, damit die Auswahl ohne laufenden Server pruefbar ist.
 * Die Registry-Grenze laeuft mitten zwischen den beiden Griffen, und das ist gemessen
 * (2026-09-03): {@code Material.values()} laedt vollstaendig (2121 Eintraege),
 * {@code new ItemStack(Material)} wirft.
 */
public final class Materialwahl {

    /**
     * Die Namen aller Bukkit-Materialien — einmal beim Laden dieser Klasse.
     *
     * <p>⚠️ Ueber {@code Material.values()}, <b>nicht</b> ueber {@code valueOf} je Aufruf: ein
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
     * <p>⚠️ <b>Fallen statt werfen ist hier richtig:</b> ein Eintrag, der wie Stein aussieht, ist
     * besser als ein Menue, das sich beim Klick nicht oeffnet. Der Aufrufer protokolliert den Fall
     * — siehe {@link ChestMenue}.
     */
    public static String rueckfallOder(Sinnbild sinnbild, String notnagel) {
        String kandidat = sinnbild.vanillaRueckfall();
        if (kandidat == null || kandidat.isBlank()) {
            return notnagel;
        }
        String gross = kandidat.trim().toUpperCase(Locale.ROOT);
        return BEKANNT.contains(gross) ? gross : notnagel;
    }

    /**
     * Wie viele Materialnamen bekannt sind.
     *
     * <p>⚠️ Existiert fuer die <b>Positivkontrolle</b> in {@code MaterialwahlTest}: eine leere
     * Liste beantwortet jeden Rueckfall mit dem Notnagel, und dann waere „Tippfehler faellt auf
     * Notnagel" gratis gruen, ohne etwas geprueft zu haben.
     */
    public static int bekannteAnzahl() {
        return BEKANNT.size();
    }
}

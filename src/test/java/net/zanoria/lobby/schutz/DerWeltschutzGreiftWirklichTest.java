package net.zanoria.lobby.schutz;

import net.zanoria.lobby.pruefung.Attrappe;
import net.zanoria.lobby.pruefung.Mitschrift;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ⚠️ <b>Dieser Fall prueft NICHT die Regel - der prueft, dass der HOERERRUMPF sie benutzt.</b>
 *
 * <p>{@link LobbyschutzTest} deckt {@link Lobbyschutz} mit vier Faellen und ist mit Mutation M1
 * belegt. Das genuegt trotzdem nicht: eine Regel kann vollstaendig gedeckt sein und trotzdem von
 * niemandem mehr gefragt werden. Im Nachbarrepo NexusStrike ist genau das gemessen worden - ein
 * vorgezogener Ausstieg ganz oben in einem Hoererrumpf blieb bei <b>272 gruenen Tests still
 * gruen</b>, weil kein einziger Fall den Rumpf fuhr.
 *
 * <p>Deshalb baut jeder Fall hier ein echtes Bukkit-Ereignis, faehrt den Rumpf und liest die
 * <b>Wirkung</b> ab ({@code isCancelled()}, {@code setGameMode}) - nicht den Rueckgabewert einer
 * Rechnung. Dass die Ereignisse ohne Server ueberhaupt entstehen, ist in
 * {@code DieRegistrygrenzeIstGemessenTest} am 2026-09-03 je Glied einzeln gemessen.
 *
 * <p>⚠️ <b>Fall {@link #andereWeltBleibtFrei()} ist die Gegenprobe und nicht wegzukuerzen.</b> Ohne
 * ihn waere {@link #abbauWirdAbgebrochen()} auch dann gruen, wenn der Rumpf pauschal abbraeche,
 * ohne die Regel je zu fragen.
 */
class DerWeltschutzGreiftWirklichTest {

    @Test
    @DisplayName("Abbau in der Lobbywelt wird wirklich abgebrochen - der Rumpf faehrt")
    void abbauWirdAbgebrochen() {
        Mitschrift mitschrift = new Mitschrift();
        BlockBreakEvent ereignis = abbauIn("lobby", mitschrift);

        schutzIn("lobby", "lobby").beimAbbauen(ereignis);

        assertTrue(ereignis.isCancelled(),
                "⚠️ Der Abbau in der geschuetzten Welt wurde NICHT abgebrochen. Die Regel allein"
                        + " genuegt nicht - hier haengt der Hoererrumpf. Mitschrift: " + mitschrift);
    }

    @Test
    @DisplayName("⚠️ Gegenprobe: in einer anderen Welt bleibt der Abbau frei")
    void andereWeltBleibtFrei() {
        Mitschrift mitschrift = new Mitschrift();
        BlockBreakEvent ereignis = abbauIn("andere", mitschrift);

        schutzIn("lobby", "lobby").beimAbbauen(ereignis);

        assertFalse(ereignis.isCancelled(),
                "⚠️ Hier wurde abgebrochen, obwohl die Welt nicht geschuetzt ist. Ein Rumpf, der"
                        + " pauschal abbricht, macht den ersten Fall gruen, ohne die Regel je zu"
                        + " fragen. Mitschrift: " + mitschrift);
    }

    @Test
    @DisplayName("⚠️ Namensdreher in der Konfiguration: ueberall wird abgebrochen, nicht nirgends")
    void beiNamensdreherWirdUeberallAbgebrochen() {
        Mitschrift mitschrift = new Mitschrift();
        BlockBreakEvent ereignis = abbauIn("andere", mitschrift);

        // Konfiguriert ist "lobbi", vorhanden ist "lobby" - die Regel faellt zu.
        schutzIn("lobbi", "lobby").beimAbbauen(ereignis);

        assertTrue(ereignis.isCancelled(),
                "⚠️ Der Rueckfall 'alles geschuetzt' erreicht den Rumpf nicht. Genau hier wuerde"
                        + " ein Tippfehler in der config.yml den Schutz lautlos abschalten."
                        + " Mitschrift: " + mitschrift);
    }

    /**
     * ⚠️ <b>Dieser Fall ist aus einem MESSERGEBNIS entstanden, nicht aus Vollstaendigkeitsdrang.</b>
     *
     * <p>Mit nur den vier Faellen darueber blieb Mutation M3 - {@code setCancelled(true)} in
     * {@code beimSetzen} auf {@code false} gedreht - am 2026-09-03 <b>still gruen</b>: RC=0, alle
     * elf Faelle gruen. Kein Fall fuhr diesen Rumpf, obwohl der Abbau-Rumpf daneben gedeckt war.
     * Zwei Rumpfe derselben Klasse, und nur einer wurde gefahren.
     *
     * <p>Mit diesem Fall faellt M3 rot. Wer ihn streicht, macht {@code beimSetzen} wieder
     * ungedeckt, ohne dass irgendetwas rot wird.
     */
    @Test
    @DisplayName("⚠️ Setzen in der Lobbywelt wird wirklich abgebrochen - sonst ueberlebt M3")
    void setzenWirdAbgebrochen() {
        Mitschrift mitschrift = new Mitschrift();
        // ⚠️ Siebenstelliger Konstruktor mit explizitem EquipmentSlot: die sechsstellige Fassung
        // ist forRemoval-markiert. ItemStack ist null - gemessen in
        // DieRegistrygrenzeIstGemessenTest (Glied 4/6): ohne Server laesst sich keiner bauen,
        // und der Konstruktor nimmt null an.
        BlockPlaceEvent ereignis = new BlockPlaceEvent(
                block("lobby", mitschrift),
                Attrappe.von("Zustand", mitschrift).als(BlockState.class),
                block("lobby", mitschrift),
                null,
                Attrappe.von("Spieler", mitschrift).als(Player.class),
                true,
                EquipmentSlot.HAND);

        schutzIn("lobby", "lobby").beimSetzen(ereignis);

        assertTrue(ereignis.isCancelled(),
                "⚠️ Das Setzen in der geschuetzten Welt wurde NICHT abgebrochen. Adventure allein"
                        + " haelt den Bau nicht auf - genau dafuer gibt es diesen Rumpf."
                        + " Mitschrift: " + mitschrift);
    }

    @Test
    @DisplayName("Beitritt in der Lobbywelt setzt wirklich den Spielmodus")
    void beitrittSetztAdventure() {
        Mitschrift mitschrift = new Mitschrift();
        Player spieler = Attrappe.von("Spieler", mitschrift)
                .antwortet("getWorld", welt("lobby", mitschrift))
                .als(Player.class);

        schutzIn("lobby", "lobby").beimBeitreten(spieler);

        assertTrue(mitschrift.enthaelt("Spieler#setGameMode"),
                "⚠️ setGameMode wurde nie gerufen. Die Mitschrift ist der Beleg, dass der Rumpf"
                        + " gefahren ist - eine Zusicherung auf die Regel allein saehe hier gruen"
                        + " aus. Mitschrift: " + mitschrift);
    }

    private LobbyWeltschutz schutzIn(String konfiguriert, String vorhanden) {
        return new LobbyWeltschutz(Lobbyschutz.aus(konfiguriert, List.of(vorhanden)));
    }

    private static BlockBreakEvent abbauIn(String weltname, Mitschrift mitschrift) {
        return new BlockBreakEvent(
                block(weltname, mitschrift),
                Attrappe.von("Spieler", mitschrift).als(Player.class));
    }

    private static Block block(String weltname, Mitschrift mitschrift) {
        return Attrappe.von("Block", mitschrift)
                .antwortet("getWorld", welt(weltname, mitschrift))
                .als(Block.class);
    }

    private static World welt(String weltname, Mitschrift mitschrift) {
        return Attrappe.von("Welt", mitschrift)
                .antwortet("getName", weltname)
                .als(World.class);
    }
}

package net.zanoria.lobby.pruefung;

import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MESSUNG DER REGISTRYGRENZE - ein Bericht, keine Zusage.
 *
 * <p>Ein Paper-Plugin kann ohne laufenden Server nur einen Teil seiner Objekte anlegen. Wo diese
 * Grenze liegt, ist an der Signatur NICHT ablesbar, und sie laeuft nicht je Ereignisart, sondern
 * je Objekt. Deshalb misst dieser Fall JE GLIED EINZELN und schliesst nie von einem Glied auf
 * seine Nachbarn.
 *
 * <p><b>⚠️ Das Folgende ist eine MESSUNG vom 2026-09-03, keine Zusage.</b> Sie gilt fuer
 * paper-api 1.21.11-R0.1-SNAPSHOT auf JDK 21 in diesem Repo. Sie ist kein Vertrag der
 * Paper-API: eine neue paper-api-Fassung darf jede Zeile umdrehen. Wer sich darauf stuetzt,
 * faehrt den Fall neu und liest das Ergebnis ab, statt diese Tabelle zu glauben.
 *
 * <pre>
 *   #   Glied                                          Urteil   Grund / Ergebnis
 *   ------------------------------------------------------------------------------------------
 *   1   GameMode.ADVENTURE                             BAUBAR   GameMode.ADVENTURE
 *   2   Material.COMPASS                               BAUBAR   Material.COMPASS
 *   3   Material.values().length                       BAUBAR   2121 - die ganze Enum laedt
 *   4   new ItemStack(Material.COMPASS)                NICHT    ExceptionInInitializerError
 *                                                               &lt;- IllegalStateException:
 *                                                               "No RegistryAccess implementation found"
 *   5   new BlockBreakEvent(block, spieler)            BAUBAR
 *   6   new BlockPlaceEvent(.., null, spieler, true)   BAUBAR   ⚠️ ItemStack-Argument war null
 *   7   new PlayerJoinEvent(spieler, Component)        BAUBAR
 *   8   new PlayerInteractEvent(.., null, block, UP)   BAUBAR   ⚠️ ItemStack-Argument war null
 *   9   new InventoryClickEvent(sicht, ..)             BAUBAR   fragt sicht.convertSlot(int)
 *  10   Bukkit.createInventory(null, 9, "titel")       NICHT    NullPointerException:
 *                                                               "org.bukkit.Bukkit.server is null"
 * </pre>
 *
 * <p><b>Was die Messung heisst.</b>
 *
 * <p>⚠️ <b>Die beiden NICHT sind NICHT derselbe Fehler.</b> Glied 4 ist die Registrygrenze:
 * {@code ItemStack} zieht beim Anlegen die Registry, und ohne Server gibt es keine
 * {@code RegistryAccess}. Glied 10 ist etwas anderes - {@code Bukkit.server} ist schlicht nicht
 * gesetzt; das ist die Server-Singleton-Grenze und liesse sich grundsaetzlich stellen, die
 * Registrygrenze nicht. Wer beide in einen Topf wirft, sucht spaeter am falschen Ende.
 *
 * <p><b>Ereignisse liegen NICHT pauschal jenseits der Grenze.</b> Alle fuenf gemessenen
 * Ereignisse (5-9) entstehen ohne Server. ⚠️ Und zwar auch dann noch, wenn Glied 4 unmittelbar
 * davor {@code org.bukkit.Registry} bereits in den Fehlerzustand gefahren hat - sie ruehren sie
 * also wirklich nicht an. Daraus folgt aber NICHT, dass jedes Ereignis baubar ist: im Nachbarrepo
 * NexusStrike ist am 2026-09-02 {@code EntityDamageByEntityEvent} als nicht baubar gemessen
 * worden ({@code NoClassDefFoundError: org.bukkit.damage.DamageType}), obwohl seine Signatur nur
 * Enums nennt. Dieses Ereignis steht hier NICHT in der Messung. Wer ein Ereignis braucht, das
 * oben fehlt, misst es - er leitet es nicht ab.
 *
 * <p>⚠️ <b>Zu 6 und 8: das BAUBAR hat eine Bedingung.</b> Beide Konstruktoren nehmen einen
 * {@code ItemStack}. {@code ItemStack} ist eine Klasse, keine Schnittstelle - es gibt dafuer keine
 * Proxy-Attrappe, und nach Glied 4 laesst sich auch kein echter bauen. Gemessen ist also
 * "baubar mit null als ItemStack". Ein spaeterer Fall, der {@code getItemInHand()} oder
 * {@code getItem()} wirklich braucht, steht wieder vor Glied 4 und gehoert in den Erstlauf am
 * laufenden Server.
 *
 * <p>Die Konstruktorsignaturen zu 6, 8 und 9 sind mit {@code javap} an
 * paper-api-1.21.11-R0.1-SNAPSHOT.jar nachgesehen, nicht geraten. Zu 9 zeigt der Bytecode als
 * einzigen Zugriff auf ein Argument {@code InventoryView.convertSlot(int)} - genau das ist an der
 * Attrappe eingetragen, und die Mitschrift belegt im Lauf, dass es auch gefragt wurde.
 */
class DieRegistrygrenzeIstGemessenTest {

    /** Ein Glied der Kette. Wirft, was es will - auch {@link Error}. */
    private interface Glied {
        Object baue(Mitschrift mitschrift) throws Throwable;
    }

    @Test
    @DisplayName("Die Registrygrenze wird je Glied einzeln gemessen und berichtet")
    void dieGrenzeWirdGemessen() {
        // 1
        miss("org.bukkit.GameMode.ADVENTURE", m -> org.bukkit.GameMode.ADVENTURE);

        // 2
        miss("org.bukkit.Material.COMPASS", m -> org.bukkit.Material.COMPASS);

        // 3
        miss("org.bukkit.Material.values().length", m -> org.bukkit.Material.values().length);

        // 4
        miss("new ItemStack(Material.COMPASS)",
                m -> new org.bukkit.inventory.ItemStack(org.bukkit.Material.COMPASS));

        // 5
        miss("new BlockBreakEvent(block, spieler)", m -> {
            Block block = block(m);
            Player spieler = Attrappe.von("spieler", m).als(Player.class);
            return new org.bukkit.event.block.BlockBreakEvent(block, spieler);
        });

        // 6  Konstruktor nachgesehen mit javap (paper-api 1.21.11):
        //    (Block, BlockState, Block, ItemStack, Player, boolean)
        //    ⚠️ ItemStack ist eine KLASSE, keine Schnittstelle - dafuer gibt es keine
        //    Proxy-Attrappe. Hier steht null; genau das misst dieses Glied.
        miss("new BlockPlaceEvent(block, zustand, dagegen, null, spieler, true)", m -> {
            Block gesetzt = block(m);
            Block dagegen = block(m);
            BlockState zustand = Attrappe.von("zustand", m).als(BlockState.class);
            Player spieler = Attrappe.von("spieler", m).als(Player.class);
            return new org.bukkit.event.block.BlockPlaceEvent(
                    gesetzt, zustand, dagegen, null, spieler, true);
        });

        // 7
        miss("new PlayerJoinEvent(spieler, Component.text(\"x\"))", m -> {
            Player spieler = Attrappe.von("spieler", m).als(Player.class);
            return new org.bukkit.event.player.PlayerJoinEvent(
                    spieler, net.kyori.adventure.text.Component.text("x"));
        });

        // 8  Konstruktor nachgesehen: (Player, Action, ItemStack, Block, BlockFace)
        //    ItemStack wieder null - siehe Glied 6.
        miss("new PlayerInteractEvent(spieler, RIGHT_CLICK_BLOCK, null, block, UP)", m -> {
            Player spieler = Attrappe.von("spieler", m).als(Player.class);
            Block block = block(m);
            return new org.bukkit.event.player.PlayerInteractEvent(
                    spieler,
                    org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,
                    null,
                    block,
                    org.bukkit.block.BlockFace.UP);
        });

        // 9  Konstruktor nachgesehen: (InventoryView, SlotType, int, ClickType, InventoryAction).
        //    Der Rumpf ruft view.convertSlot(int) - deshalb ist genau das eingetragen.
        miss("new InventoryClickEvent(sicht, CONTAINER, 0, LEFT, PICKUP_ALL)", m -> {
            InventoryView sicht = Attrappe.von("sicht", m)
                    .antwortet("convertSlot", 0)
                    .als(InventoryView.class);
            return new org.bukkit.event.inventory.InventoryClickEvent(
                    sicht,
                    org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,
                    0,
                    org.bukkit.event.inventory.ClickType.LEFT,
                    org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);
        });

        // 10
        miss("Bukkit.createInventory(null, 9, \"titel\")",
                m -> org.bukkit.Bukkit.createInventory(null, 9, "titel"));
    }

    /** Block-Attrappe, die auf {@code getWorld} eine World-Attrappe mit Namen "lobby" gibt. */
    private static Block block(Mitschrift m) {
        return Attrappe.von("block", m)
                .antwortet("getWorld",
                        Attrappe.von("welt", m).antwortet("getName", "lobby").als(org.bukkit.World.class))
                .als(Block.class);
    }

    /**
     * ⚠️ {@code catch (Throwable)}, nicht {@code catch (Exception)}. {@code ExceptionInInitializerError}
     * und {@code NoClassDefFoundError} sind {@link Error} - ein {@code catch (Exception)} liesse genau
     * die Faelle durch, die hier gesucht werden.
     *
     * <p>⚠️ Und der Fall wird NIE rot. Ein Glied, das bei "nicht baubar" fehlschlaegt, verhindert,
     * dass die uebrigen neun ueberhaupt gemessen werden.
     */
    private static void miss(String was, Glied glied) {
        Mitschrift mitschrift = new Mitschrift();
        try {
            Object ergebnis = glied.baue(mitschrift);
            System.out.println(zeile("BAUBAR", was, kurz(ergebnis) + spur(mitschrift)));
        } catch (Throwable t) {
            System.out.println(zeile("NICHT", was, ursachenkette(t) + spur(mitschrift)));
        }
    }

    /**
     * ⚠️ Die Ursache gehoert dazu. {@code ExceptionInInitializerError} traegt selbst die Meldung
     * {@code null} - wer nur sie ausgibt, berichtet "nicht baubar" ohne zu sagen, WORAN. Genau
     * daran haengt spaeter die Entscheidung Einheitstest oder Erstlauf.
     */
    private static String ursachenkette(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable akt = t; akt != null; akt = akt.getCause()) {
            if (sb.length() > 0) {
                sb.append(" <- ");
            }
            sb.append(akt.getClass().getName()).append(": ").append(akt.getMessage());
            if (akt.getCause() == akt) {
                break;
            }
        }
        return sb.toString();
    }

    private static String zeile(String urteil, String was, String ergebnis) {
        return String.format("REGISTRYGRENZE: %-11s %s -> %s", urteil, was, ergebnis);
    }

    /** Rendert OHNE toString() auf Fremdobjekte - ein toString koennte selbst ueber die Grenze fallen. */
    private static String kurz(Object o) {
        if (o == null) {
            return "null";
        }
        if (o instanceof Enum<?> e) {
            return o.getClass().getSimpleName() + "." + e.name();
        }
        if (o instanceof Number || o instanceof CharSequence || o instanceof Boolean) {
            return String.valueOf(o);
        }
        return o.getClass().getName();
    }

    /** Beweist, dass die Attrappe wirklich gefragt wurde - ein leerer Rumpf faellt so auf. */
    private static String spur(Mitschrift mitschrift) {
        return mitschrift.zeilen().isEmpty() ? "" : "  | mitschrift=" + mitschrift;
    }
}

package net.zanoria.lobby.builder;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;

/**
 * {@code /builder} — betritt den Builder-Server.
 *
 * <h2>⚠️ Seit dem 2026-09-03 hat dieser Befehl nur noch EINEN Weg</h2>
 *
 * <p>Die Unterbefehle {@code add}/{@code remove}/{@code list} sind weg, weil die Liste, die sie
 * pflegten, nichts mehr entscheidet — siehe {@link BuilderRedisClient#KEY_MEMBERS}. Wer jemanden
 * freischalten will, vergibt den <b>Rang</b>; das Recht {@code zanoria.builder} kommt aus Nexus
 * ({@code RankPresentationService.darfBauen}).
 *
 * <p>⚠️ <b>Die Berechtigung prueft Bukkit</b> anhand von {@code plugin.yml}
 * ({@code permission: zanoria.builder}), <b>nicht dieser Rumpf</b>. Eine zweite Pruefung hier
 * waere ein zweiter Ort fuer dieselbe Wahrheit — und der eine, den jemand spaeter aendert, waehrend
 * der andere stehenbleibt.
 *
 * <p>⚠️ Die zweite Bedingung — ob der Zielserver ueberhaupt FAWE hat — steht in
 * {@code BuilderServerService.joinBuilderServer}. Sie gehoert dorthin, weil sie den <b>Zielserver</b>
 * betrifft und nicht den Aufrufer.
 */
public final class BuilderCommand implements CommandExecutor, TabCompleter {

    private final BuilderServerService service;

    public BuilderCommand(BuilderServerService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use /builder.");
            return true;
        }
        service.joinBuilderServer(player);
        return true;
    }

    /** ⚠️ Keine Unterbefehle mehr — es gibt nichts vorzuschlagen. */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        return List.of();
    }
}

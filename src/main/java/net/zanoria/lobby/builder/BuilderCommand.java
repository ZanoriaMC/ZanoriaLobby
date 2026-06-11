package net.zanoria.lobby.builder;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public final class BuilderCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM     = MiniMessage.miniMessage();
    private static final String      PREFIX = "<gradient:#FF702B:#FCC650>Builder</gradient> <dark_gray>» <reset>";

    private final BuilderServerService service;
    private final BuilderRedisClient   redis;

    public BuilderCommand(BuilderServerService service, BuilderRedisClient redis) {
        this.service = service;
        this.redis   = redis;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can use /builder.");
                return true;
            }
            service.joinBuilderServer(player);
            return true;
        }

        if (!sender.hasPermission("zanoria.admin")) {
            sender.sendMessage(MM.deserialize(PREFIX + "<red>Keine Berechtigung."));
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                if (args.length < 2) { sender.sendMessage(MM.deserialize(PREFIX + "<red>Verwendung: /builder add <name>")); return true; }
                redis.addMember(args[1]);
                sender.sendMessage(MM.deserialize(PREFIX + "<green>" + args[1] + " wurde als Builder hinzugefügt."));
            }
            case "remove" -> {
                if (args.length < 2) { sender.sendMessage(MM.deserialize(PREFIX + "<red>Verwendung: /builder remove <name>")); return true; }
                redis.removeMember(args[1]);
                sender.sendMessage(MM.deserialize(PREFIX + "<yellow>" + args[1] + " wurde entfernt."));
            }
            case "list" -> {
                var members = redis.getMembers();
                if (members.isEmpty()) {
                    sender.sendMessage(MM.deserialize(PREFIX + "<gray>Keine Builder eingetragen."));
                } else {
                    sender.sendMessage(MM.deserialize(PREFIX + "<white>Builder: <aqua>" + String.join(", ", members)));
                }
            }
            default -> sender.sendMessage(MM.deserialize(PREFIX + "<red>Unbekannter Sub-Befehl. Verwende: add, remove, list"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1 && sender.hasPermission("zanoria.admin")) {
            return List.of("add", "remove", "list").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}

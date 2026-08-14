package me.jonycape.dev.flamecore.commands;

import me.jonycape.dev.flamecore.Main;
import me.jonycape.dev.flamecore.config.ConfigKeys;
import me.jonycape.dev.flamecore.management.ServerManagementService;
import me.jonycape.dev.flamecore.management.SessionManager;
import me.jonycape.dev.flamecore.customize.CustomizeService;
import me.jonycape.dev.flamecore.donatetop.DonateTopService;
import me.jonycape.dev.flamecore.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class FlameCoreCommand extends BaseCommand implements TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "tps", "mspt", "system", "online", "worlds", "announce", "player", "check", "reload",
            "customize", "stream", "addpayment", "paymenttop", "donate");

    private final ServerManagementService management;

    public FlameCoreCommand(Main plugin) {
        super(plugin);
        this.management = new ServerManagementService(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendMessage(sender, ConfigKeys.MESSAGE_MANAGEMENT_INFO);
            return true;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("addpayment")) {
            return handleAddPayment(sender, args);
        }
        if (sub.equals("paymenttop")) {
            return handlePaymentTop(sender, args);
        }
        if (sub.equals("donate")) {
            return handleDonate(sender, args);
        }
        if (!(sender instanceof Player player)) {
            return handleConsole(sender, sub);
        }

        if (sub.equals("customize")) {
            if (plugin.getCustomizeService() == null) {
                sendMessage(sender, ConfigKeys.MESSAGE_MODULE_DISABLED, "module", "кастомизация");
                return true;
            }
            return handleCustomize(player, args);
        }
        if (sub.equals("stream")) {
            if (plugin.getStreamService() == null) {
                sendMessage(sender, ConfigKeys.MESSAGE_MODULE_DISABLED, "module", "стримеры");
                return true;
            }
            return handleStream(player, args);
        }

        if (!isOwner(player)) {
            sendMessage(sender, ConfigKeys.MESSAGE_NO_PERMISSION);
            return true;
        }
        if (!SessionManager.hasAccess(player.getName())) {
            sendMessage(sender, ConfigKeys.MESSAGE_PANEL_LOCKED);
            return true;
        }

        return handleSubcommand(sender, sub, args);
    }

    private boolean handleCustomize(Player player, String[] args) {
        if (!player.hasPermission(ConfigKeys.PERM_CUSTOMIZE)) {
            sendMessage(player, ConfigKeys.MESSAGE_NO_PERMISSION);
            return true;
        }
        if (args.length < 2) {
            plugin.getCustomizeService().menu(player);
            return true;
        }
        CustomizeService service = plugin.getCustomizeService();
        switch (args[1].toLowerCase()) {
            case "color":
                if (args.length < 3) {
                    service.menu(player);
                } else {
                    service.setColor(player, args[2]);
                }
                return true;
            case "parrot":
                service.toggleParrot(player);
                return true;
            case "nimb":
                service.toggleNimb(player);
                return true;
            default:
                service.menu(player);
                return true;
        }
    }

    private boolean handleStream(Player player, String[] args) {
        if (!player.hasPermission(ConfigKeys.PERM_STREAM)) {
            sendMessage(player, ConfigKeys.MESSAGE_NO_PERMISSION);
            return true;
        }
        if (args.length < 2) {
            sendMessage(player, ConfigKeys.MESSAGE_STREAM_USAGE);
            return true;
        }
        plugin.getStreamService().announce(player, args[1]);
        return true;
    }

    private boolean handleAddPayment(CommandSender sender, String[] args) {
        if (!(sender instanceof ConsoleCommandSender) && !sender.hasPermission(ConfigKeys.PERM_ADMIN)) {
            sendMessage(sender, ConfigKeys.MESSAGE_NO_PERMISSION);
            return true;
        }
        if (args.length < 2 || args[1].isEmpty()) {
            sender.sendMessage("FlameCore: использование /flamecore addpayment <ник>");
            return true;
        }
        DonateTopService service = plugin.getDonateTopService();
        if (service == null) {
            sender.sendMessage("FlameCore: топ платежей отключён (нет DecentHolograms).");
            return true;
        }
        service.addPayment(args[1]);
        sender.sendMessage("FlameCore: платёж для " + args[1] + " учтён.");
        return true;
    }

    private boolean handlePaymentTop(CommandSender sender, String[] args) {
        if (!(sender instanceof ConsoleCommandSender) && !sender.hasPermission(ConfigKeys.PERM_ADMIN)) {
            sendMessage(sender, ConfigKeys.MESSAGE_NO_PERMISSION);
            return true;
        }
        if (args.length < 2) {
            sendMessage(sender, ConfigKeys.MESSAGE_DONATETOP_USAGE);
            return true;
        }
        DonateTopService service = plugin.getDonateTopService();
        if (service == null) {
            sender.sendMessage("FlameCore: топ платежей отключён (нет DecentHolograms).");
            return true;
        }
        switch (args[1].toLowerCase()) {
            case "spawn":
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("FlameCore: спавн доступен только игроку.");
                    return true;
                }
                service.spawnAt(player.getLocation());
                sendMessage(sender, ConfigKeys.MESSAGE_DONATETOP_SPAWNED);
                return true;
            case "remove":
                service.remove();
                sendMessage(sender, ConfigKeys.MESSAGE_DONATETOP_REMOVED);
                return true;
            case "clear":
                service.clearTop();
                sendMessage(sender, ConfigKeys.MESSAGE_DONATETOP_CLEARED);
                return true;
            default:
                sendMessage(sender, ConfigKeys.MESSAGE_DONATETOP_USAGE);
                return true;
        }
    }

    private boolean handleDonate(CommandSender sender, String[] args) {
        if (plugin.getDonateService() == null) {
            sendMessage(sender, ConfigKeys.MESSAGE_MODULE_DISABLED, "module", "донат-алерт");
            return true;
        }
        if (!(sender instanceof ConsoleCommandSender)) {
            sendMessage(sender, ConfigKeys.MESSAGE_DONATE_CONSOLE_ONLY);
            return true;
        }
        if (args.length < 3) {
            sendMessage(sender, ConfigKeys.MESSAGE_DONATE_USAGE);
            return true;
        }
        String nick = args[1];
        String item = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
        plugin.getDonateService().announce(nick, item);
        sender.sendMessage("FlameCore: донат для " + nick + " объявлен на сервере.");
        return true;
    }

    private boolean handleSubcommand(CommandSender sender, String sub, String[] args) {
        switch (sub) {
            case "tps":
                sender.sendMessage(MessageUtils.color(management.tps()));
                return true;
            case "mspt":
                sender.sendMessage(MessageUtils.color(management.mspt()));
                return true;
            case "system":
                sender.sendMessage(MessageUtils.color(management.system()));
                return true;
            case "online":
                sender.sendMessage(MessageUtils.color(management.online()));
                return true;
            case "worlds":
                sender.sendMessage(MessageUtils.color(management.worlds()));
                return true;
            case "announce":
                if (args.length < 2) {
                    sendMessage(sender, ConfigKeys.MESSAGE_MANAGEMENT_USAGE);
                    return true;
                }
                management.announce(String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
                return true;
            case "player":
                if (args.length < 2) {
                    sendMessage(sender, ConfigKeys.MESSAGE_MANAGEMENT_USAGE);
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sendMessage(sender, ConfigKeys.MESSAGE_MANAGEMENT_PLAYER_NOT_FOUND, "player", args[1]);
                    return true;
                }
                sender.sendMessage(MessageUtils.color(management.player(target)));
                return true;
            case "check":
                sender.sendMessage(MessageUtils.color(management.check()));
                return true;
            case "reload":
                plugin.reloadPlugin();
                sendMessage(sender, ConfigKeys.MESSAGE_MANAGEMENT_RELOAD);
                return true;
            default:
                sendMessage(sender, ConfigKeys.MESSAGE_UNKNOWN_COMMAND);
                return true;
        }
    }

    private boolean handleConsole(CommandSender sender, String sub) {
        switch (sub) {
            case "reload":
                plugin.reloadPlugin();
                sender.sendMessage("FlameCore перезагружен.");
                return true;
            case "tps":
            case "mspt":
            case "system":
            case "online":
            case "check":
                String text = switch (sub) {
                    case "tps" -> management.tps();
                    case "mspt" -> management.mspt();
                    case "system" -> management.system();
                    case "online" -> management.online();
                    default -> management.check();
                };
                sender.sendMessage(MessageUtils.color(text));
                return true;
            default:
                sender.sendMessage("FlameCore: используй /flamecore tps | mspt | system | online | check | reload");
                return true;
        }
    }

    private boolean isOwner(Player player) {
        String owner = Main.getCfg().getString(ConfigKeys.OWNER_NAME, "");
        return player.getName().equalsIgnoreCase(owner);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(prefix)) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("player")) {
            String prefix = args[1].toLowerCase();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase().startsWith(prefix)) {
                    completions.add(online.getName());
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("customize")) {
            String prefix = args[1].toLowerCase();
            for (String opt : List.of("color", "parrot", "nimb")) {
                if (opt.startsWith(prefix)) {
                    completions.add(opt);
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("customize")
                && args[1].equalsIgnoreCase("color")) {
            String prefix = args[2].toLowerCase();
            if (plugin.getCustomizeService() != null) {
                for (String color : plugin.getCustomizeService().getColorNames()) {
                    if (color.startsWith(prefix)) {
                        completions.add(color);
                    }
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("paymenttop")) {
            String prefix = args[1].toLowerCase();
            for (String opt : List.of("spawn", "remove", "clear")) {
                if (opt.startsWith(prefix)) {
                    completions.add(opt);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("addpayment")) {
            String prefix = args[1].toLowerCase();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase().startsWith(prefix)) {
                    completions.add(online.getName());
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("donate")) {
            String prefix = args[1].toLowerCase();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase().startsWith(prefix)) {
                    completions.add(online.getName());
                }
            }
        }
        return completions;
    }
}
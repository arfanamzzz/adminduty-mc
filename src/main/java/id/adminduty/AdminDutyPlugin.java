package id.adminduty;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class AdminDutyPlugin extends JavaPlugin implements Listener, TabExecutor {

    private final Set<UUID> onDuty = new HashSet<>();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);

        var cmd = getCommand("adminduty");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }

        if (getConfig().getStringList("admins").isEmpty()) {
            getLogger().warning("Daftar 'admins' di config.yml kosong! Isi nama kamu lalu /adminduty reload.");
        }

        // Pastikan semua admin yang sedang online mulai dari OFF duty.
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isAdmin(p)) {
                p.setOp(false);
            }
        }
    }

    @Override
    public void onDisable() {
        // Jangan sampai ada admin yang tertinggal OP saat server stop/reload.
        for (UUID id : new HashSet<>(onDuty)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.setOp(false);
        }
        onDuty.clear();
    }

    // ---------------------------------------------------------------- events

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!isAdmin(p)) return;
        // Admin selalu mulai OFF duty (juga membersihkan OP sisa dari crash).
        onDuty.remove(p.getUniqueId());
        p.setOp(false);
        send(p, "join-off-duty");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (onDuty.remove(p.getUniqueId())) {
            p.setOp(false);
        }
    }

    // --------------------------------------------------------------- command

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";

        // Perintah yang boleh dari console
        if (sub.equals("reload") && (!(sender instanceof Player p) || isAdmin(p))) {
            reloadConfig();
            sendRaw(sender, "reloaded");
            return true;
        }
        if (sub.equals("list") && (!(sender instanceof Player p) || isAdmin(p))) {
            sender.sendMessage(LEGACY.deserialize("&6Admin on duty: &f" + names()));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sendRaw(sender, "player-only");
            return true;
        }
        if (!isAdmin(player)) {
            send(player, "no-permission");
            return true;
        }

        boolean duty = onDuty.contains(player.getUniqueId());

        switch (sub) {
            case "on" -> {
                if (duty) send(player, "already-on"); else setDuty(player, true);
            }
            case "off" -> {
                if (!duty) send(player, "already-off"); else setDuty(player, false);
            }
            case "status" -> send(player, duty ? "status-on" : "status-off");
            default -> setDuty(player, !duty); // toggle
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1 && (!(sender instanceof Player p) || isAdmin(p))) {
            for (String s : List.of("on", "off", "status", "list", "reload")) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
            }
        }
        return out;
    }

    // ----------------------------------------------------------------- logic

    private void setDuty(Player p, boolean duty) {
        if (duty) {
            onDuty.add(p.getUniqueId());
            p.setOp(true);
            send(p, "on-duty");
            notifyOthers(p, "notify-on");
            getLogger().info(p.getName() + " ON duty.");
        } else {
            onDuty.remove(p.getUniqueId());
            p.setOp(false);
            if (getConfig().getBoolean("off-duty-reset-gamemode", true)) {
                GameMode gm = p.getGameMode();
                if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) {
                    p.setGameMode(GameMode.SURVIVAL);
                }
            }
            send(p, "off-duty");
            notifyOthers(p, "notify-off");
            getLogger().info(p.getName() + " OFF duty.");
        }
    }

    private void notifyOthers(Player who, String key) {
        if (!getConfig().getBoolean("notify-other-admins", true)) return;
        Component msg = LEGACY.deserialize(prefix() + msg(key).replace("%player%", who.getName()));
        for (UUID id : onDuty) {
            Player other = Bukkit.getPlayer(id);
            if (other != null && !other.equals(who)) other.sendMessage(msg);
        }
    }

    private boolean isAdmin(Player p) {
        for (String entry : getConfig().getStringList("admins")) {
            if (entry.equalsIgnoreCase(p.getName()) || entry.equalsIgnoreCase(p.getUniqueId().toString())) {
                return true;
            }
        }
        return false;
    }

    private String names() {
        List<String> n = new ArrayList<>();
        for (UUID id : onDuty) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) n.add(p.getName());
        }
        return n.isEmpty() ? "-" : String.join(", ", n);
    }

    private String prefix() {
        return getConfig().getString("messages.prefix", "");
    }

    private String msg(String key) {
        return getConfig().getString("messages." + key, key);
    }

    private void send(Player p, String key) {
        p.sendMessage(LEGACY.deserialize(prefix() + msg(key)));
    }

    private void sendRaw(CommandSender s, String key) {
        s.sendMessage(LEGACY.deserialize(prefix() + msg(key)));
    }
}

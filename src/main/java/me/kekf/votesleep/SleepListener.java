package me.kekf.votesleep;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;

public class SleepListener implements Listener {

    private HashMap<UUID, Boolean> votes;
    private HashMap<UUID, Player> callbacks;
    private VoteSleep plugin;

    public SleepListener(VoteSleep plugin) {
        this.votes = new HashMap<>();
        callbacks = new HashMap<>();
        this.plugin = plugin;
    }

    // When a player enters a bed
    @EventHandler
    public void onPlayerBedEnter(PlayerBedEnterEvent e) {
        if (e.getBedEnterResult().equals(PlayerBedEnterEvent.BedEnterResult.OK)) {
            vote(e.getPlayer(), true);
        }
    }

    // When a player has left a bed
    @EventHandler
    public void onPlayerBedLeave(PlayerBedLeaveEvent e) {
        if (votes.containsKey(e.getPlayer().getUniqueId())) {
            if (isDay()) {
                // reset vote list
                votes = new HashMap<>();
                Bukkit.broadcastMessage(ChatColor.GOLD + "Rise and shine!");
            } else {
                unVote(e.getPlayer());
            }
        }
    }

    @EventHandler
    public void onPortalEnter(PlayerPortalEvent e) {
        if (votes.containsKey(e.getPlayer().getUniqueId())) {
            // for now it will go through the same process as normally leaving a bed or clicking a second time.
            // This will probably change in the future. Same thing with the quit event.
            unVote(e.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        if (votes.containsKey(e.getPlayer().getUniqueId())) {
            unVote(e.getPlayer());
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (e.getMessage().startsWith("/votesleep:callback")) {
            e.setCancelled(true);

            String[] args = e.getMessage().split(" ");
            if (args.length == 2) {
                UUID id = UUID.fromString(args[1]);
                if (id.toString().split("-").length == 5) {
                    callbacks.remove(id);

                    if (inBed(e.getPlayer())) {
                        e.getPlayer().sendMessage(ChatColor.RED + "You cannot do that!");

                    } else if ((!isDay() || Bukkit.getWorld(Objects.requireNonNull(plugin.getConfig().getString("Settings.world-name"))).isThundering())) {
                        if (votes.containsKey(e.getPlayer().getUniqueId())) {
                            unVote(e.getPlayer());
                        } else {
                            vote(e.getPlayer(), false);
                        }
                    } else if (!Bukkit.getWorld(Objects.requireNonNull(plugin.getConfig().getString("Settings.world-name"))).getPlayers().contains(e.getPlayer())) {
                        e.getPlayer().sendMessage(ChatColor.RED + "You must be in the overworld to vote!");
                    } else {
                        e.getPlayer().sendMessage(ChatColor.RED + "You can only vote at night!");
                    }
                }
            }
        }

    }

    private boolean inBed(Player player) {
        if (votes.containsKey(player.getUniqueId())) {
            return votes.get(player.getUniqueId());
        }

        return false;
    }

    private boolean isDay() {
        long time = Bukkit.getWorld(Objects.requireNonNull(plugin.getConfig().getString("Settings.world-name"))).getTime();
        return (time > 0 && time < 12300);
    }

    private void vote(Player player, boolean isInBed) {
        votes.put(player.getUniqueId(), isInBed);

        int requiredPercent = plugin.getConfig().getInt("Settings.percent");
        broadcastVoteMessage(player, getVotePercent());

        if (getVotePercent() >= requiredPercent) { // if enough players have voted
            World world = Bukkit.getWorld(Objects.requireNonNull(plugin.getConfig().getString("Settings.world-name")));
            world.setTime(0);
            world.setStorm(false);
            world.setThundering(false);
            Bukkit.broadcastMessage(ChatColor.GOLD + "Skipping the night!");

            // reset list
            votes = new HashMap<>();
        }
    }

    private void unVote(Player player) {
        votes.remove(player.getUniqueId());
        broadcastUnVoteMessage(player, getVotePercent());
    }

    private int getVotePercent() {
        return (int) ((double) votes.size() / (double) (Bukkit.getOnlinePlayers().size() - getExclusionCount()) * 100.0);
    }

    private int getExclusionCount() {
        // start the vote
        int excluded = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if ((!Bukkit.getWorld(Objects.requireNonNull(plugin.getConfig().getString("Settings.world-name"))).getPlayers().contains(p)) ||
                    (isAFK(p)) || (plugin.getConfig().getBoolean("Settings.exclude-ops") && Bukkit.getOperators().contains(p))) {
                // add to exclusion count
                excluded++;
            }
        }
        return excluded;
    }

    // This method works with the AFK Display DataPack. You must have this data pack for this to work.
    private boolean isAFK(Player player) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard board = manager.getMainScoreboard();
        Team playerTeam = board.getPlayerTeam(player);
        if (board.getTeam("afkDis.afk") == null) {
            return false;
        } else {
            return board.getTeam("afkDis.afk").equals(playerTeam);
        }
    }

    private void broadcastVoteMessage(Player player, int percent) {
        // create a clickable message to send to all players
        TextComponent component = new TextComponent(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("Settings.prefix") + player.getName() +
                " wants to sleep. " +
                votes.size() + "/" + (Bukkit.getOnlinePlayers().size() - getExclusionCount()) +
                " (" + percent + "%)"));
        createClickEvent(component, player);
    }

    private void broadcastUnVoteMessage(Player player, int percent) {
        // create a clickable message to send to all players
        TextComponent component = new TextComponent(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("Settings.prefix") + player.getName() +
                " no longer wants to sleep. " +
                votes.size() + "/" + (int)((plugin.getConfig().getDouble("Settings.percent") / 100.0) * (Bukkit.getOnlinePlayers().size() - getExclusionCount())) +
                " (" + percent + "%)"));
        createClickEvent(component, player);
    }

    private void createClickEvent(TextComponent component, Player player) {
        component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatColor.GREEN + "Click to change your vote")));

        UUID callbackID = UUID.randomUUID();
        callbacks.put(callbackID, player);
        component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/votesleep:callback " + callbackID));

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.spigot().sendMessage(component);
        }
    }

}

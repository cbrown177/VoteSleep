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
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;

public class SleepListener implements Listener {

    private ArrayList<UUID> voteList;
    private HashMap<UUID, Player> callbacks;
    private VoteSleep plugin;

    public SleepListener(VoteSleep plugin) {
        this.voteList = new ArrayList<>();
        callbacks = new HashMap<>();
        this.plugin = plugin;
    }

    // When a player enters a bed
    @EventHandler
    public void onPlayerBedEnter(PlayerBedEnterEvent e) {
        if (e.getBedEnterResult().equals(PlayerBedEnterEvent.BedEnterResult.OK)) {
            vote(e.getPlayer());
        }
    }

    // When a player has left a bed
    @EventHandler
    public void onPlayerBedLeave(PlayerBedLeaveEvent e) {
        if (voteList.contains(e.getPlayer().getUniqueId())) {
            if (isDay()) {
                // reset vote list
                voteList = new ArrayList<>();
                Bukkit.broadcastMessage(ChatColor.GOLD + "Rise and shine!");
            } else {
                unVote(e.getPlayer());
            }
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
                    if (!isDay() || Bukkit.getWorld(Objects.requireNonNull(plugin.getConfig().getString("Settings.world-name"))).isThundering()) {
                        System.out.println(Bukkit.getWorld(Objects.requireNonNull(plugin.getConfig().getString("Settings.world-name"))).isThundering());
                        if (voteList.contains(e.getPlayer().getUniqueId())) {
                            unVote(e.getPlayer());
                        } else {
                            vote(e.getPlayer());
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

    private boolean isDay() {
        long time = Bukkit.getWorld(Objects.requireNonNull(plugin.getConfig().getString("Settings.world-name"))).getTime();
        return (time > 0 && time < 12300);
    }

    private void vote(Player player) {
        voteList.add(player.getUniqueId());

        int requiredPercent = plugin.getConfig().getInt("Settings.percent");
        broadcastVoteMessage(player, getVotePercent());

        if (getVotePercent() >= requiredPercent) { // if enough players have voted
            // This assumes your world name is 'world' this will also soon be configurable
            World world = Bukkit.getWorld(Objects.requireNonNull(plugin.getConfig().getString("Settings.world-name")));
            world.setTime(0);
            world.setStorm(false);
            world.setThundering(false);
            Bukkit.broadcastMessage(ChatColor.GOLD + "Skipping the night!");

            // reset list
            voteList = new ArrayList<>();
        }

        System.out.println(voteList);
    }

    private void unVote(Player player) {
        voteList.remove(player.getUniqueId());
        broadcastUnVoteMessage(player, getVotePercent());
    }

    private int getVotePercent() {
        return (int) ((double) voteList.size() / (double) (Bukkit.getOnlinePlayers().size() - getExclusionCount()) * 100.0);
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
                voteList.size() + "/" + (Bukkit.getOnlinePlayers().size() - getExclusionCount()) +
                " (" + percent + "%)"));
        createClickEvent(component, player);
    }

    private void broadcastUnVoteMessage(Player player, int percent) {
        // create a clickable message to send to all players
        TextComponent component = new TextComponent(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("Settings.prefix") + player.getName() +
                " no longer wants to sleep. " +
                voteList.size() + "/" + (Bukkit.getOnlinePlayers().size() - getExclusionCount()) +
                " (" + percent + "%)"));
        createClickEvent(component, player);
    }

    private void createClickEvent(TextComponent component, Player player) {
        component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatColor.GREEN + "Click to change vote")));

        UUID callbackID = UUID.randomUUID();
        callbacks.put(callbackID, player);
        component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/votesleep:callback " + callbackID));

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.spigot().sendMessage(component);
        }
    }

}

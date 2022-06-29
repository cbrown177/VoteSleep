package me.kekf.votesleep;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class VoteSleep extends JavaPlugin {

    public static String VERSION = "1.0.3";

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(new SleepListener(this), this);

        getConfig().options().copyDefaults(true);
        saveDefaultConfig();

        System.out.println("VoteSleep Plugin v." + VERSION + " loaded successfully.");
    }
}

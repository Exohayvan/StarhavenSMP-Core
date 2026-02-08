package com.starhavensmpcore.metrics;

import com.starhavensmpcore.core.StarhavenSMPCore;
import com.starhavensmpcore.market.db.DatabaseManager;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.DrilldownPie;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;

import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class BStatsModule {

    private static final int MAX_INT = Integer.MAX_VALUE;
    private static final int PLUGIN_ID = 29398;
    private final StarhavenSMPCore plugin;
    private final Metrics metrics;

    public BStatsModule(StarhavenSMPCore plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        if (plugin == null || databaseManager == null) {
            metrics = null;
            return;
        }
        Metrics created = null;
        try {
            created = new Metrics(plugin, PLUGIN_ID);
            registerCharts(plugin, databaseManager);
            plugin.getLogger().info("bStats initialized (pluginId=" + PLUGIN_ID + ").");
        } catch (Exception ex) {
            plugin.getLogger().warning("bStats failed to initialize: " + ex.getMessage());
            ex.printStackTrace();
        }
        metrics = created;
    }

    private void registerCharts(StarhavenSMPCore plugin, DatabaseManager databaseManager) {
        safeChart("total_players", () -> metrics.addCustomChart(new SingleLineChart("total_players",
                () -> plugin.getServer().getOnlinePlayers().size())));
        safeChart("total_items_in_market", () -> metrics.addCustomChart(new SingleLineChart("total_items_in_market",
                () -> toInt(databaseManager.getTotalItemsInShop()))));
        safeChart("total_servers", () -> metrics.addCustomChart(new SingleLineChart("total_servers", () -> 1)));
        safeChart("server_language", () -> metrics.addCustomChart(new SimplePie("server_language",
                () -> Locale.getDefault().toLanguageTag())));
        safeChart("server_type_version", () -> metrics.addCustomChart(new DrilldownPie("server_type_version", () -> {
            String serverType = resolveServerType(plugin.getServer().getName());
            String version = plugin.getServer().getBukkitVersion();
            Map<String, Integer> versions = new HashMap<>();
            versions.put(version == null ? "unknown" : version, 1);
            return Collections.singletonMap(serverType, versions);
        })));
    }

    private void safeChart(String name, Runnable registration) {
        try {
            registration.run();
        } catch (Exception ex) {
            if (plugin != null) {
                plugin.getLogger().warning("bStats chart failed: " + name + " - " + ex.getMessage());
            }
        }
    }

    private static int toInt(BigInteger value) {
        if (value == null) {
            return 0;
        }
        if (value.signum() <= 0) {
            return 0;
        }
        return value.compareTo(BigInteger.valueOf(MAX_INT)) > 0 ? MAX_INT : value.intValue();
    }

    private static String resolveServerType(String name) {
        if (name == null) {
            return "Unknown";
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("paper")) {
            return "Paper";
        }
        if (lower.contains("purpur")) {
            return "Purpur";
        }
        if (lower.contains("folia")) {
            return "Folia";
        }
        if (lower.contains("spigot")) {
            return "Spigot";
        }
        if (lower.contains("bukkit")) {
            return "Bukkit";
        }
        return "Other";
    }
}

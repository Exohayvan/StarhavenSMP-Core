package com.starhavensmpcore.metrics;

import com.starhavensmpcore.core.StarhavenSMPCore;
import com.starhavensmpcore.market.db.DatabaseManager;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.DrilldownPie;
import org.bstats.charts.AdvancedPie;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.lang.reflect.Field;

public final class BStatsModule {

    private static final int MAX_INT = Integer.MAX_VALUE;
    private static final int PLUGIN_ID = 29398;

    private final StarhavenSMPCore plugin;
    private final Metrics metrics;

    public BStatsModule(StarhavenSMPCore plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;

        if (plugin == null || databaseManager == null) {
            this.metrics = null;
            return;
        }

        Metrics created = null;
        try {
            created = new Metrics(plugin, PLUGIN_ID);

            // Register charts against the created Metrics instance (not the field).
            registerCharts(created, plugin, databaseManager);

            plugin.getLogger().info("bStats initialized (pluginId=" + PLUGIN_ID + ").");
        } catch (Exception ex) {
            plugin.getLogger().warning("bStats failed to initialize: " + ex.getMessage());
            ex.printStackTrace();
        }

        this.metrics = created;
    }

    public void updateDebugLogging(boolean enabled) {
        if (metrics == null) {
            return;
        }
        try {
            Object metricsBase = getMetricsBase(metrics);
            if (metricsBase == null) {
                return;
            }
            setBooleanField(metricsBase, "logSentData", enabled);
            setBooleanField(metricsBase, "logResponseStatusText", enabled);
            if (plugin != null && enabled) {
                plugin.getLogger().info("bStats debug logging enabled (payload + response text).");
            }
        } catch (Exception ex) {
            if (plugin != null) {
                plugin.getLogger().warning("Failed to update bStats debug logging: " + ex.getMessage());
            }
        }
    }

    private void registerCharts(Metrics metrics, StarhavenSMPCore plugin, DatabaseManager databaseManager) {
        safeChart("total_items_in_market", () -> metrics.addCustomChart(new SingleLineChart(
                "total_items_in_market",
                () -> safeTotalItems(databaseManager)
        )));

        safeChart("server_language", () -> metrics.addCustomChart(new SimplePie(
                "server_language",
                this::safeLanguage
        )));

        safeChart("player_language", () -> metrics.addCustomChart(new AdvancedPie(
                "player_language",
                this::safePlayerLanguages
        )));

        safeChart("installed_plugins", () -> metrics.addCustomChart(new DrilldownPie(
                "installed_plugins",
                this::safeInstalledPlugins
        )));
    }

    private static Object getMetricsBase(Metrics metrics) throws ReflectiveOperationException {
        Field metricsBaseField = metrics.getClass().getDeclaredField("metricsBase");
        metricsBaseField.setAccessible(true);
        return metricsBaseField.get(metrics);
    }

    private static void setBooleanField(Object target, String fieldName, boolean value)
            throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setBoolean(target, value);
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
        if (value == null || value.signum() <= 0) {
            return 0;
        }
        return value.compareTo(BigInteger.valueOf(MAX_INT)) > 0 ? MAX_INT : value.intValue();
    }

    private int safeTotalItems(DatabaseManager databaseManager) {
        try {
            return toInt(databaseManager.getTotalItemsInShop());
        } catch (Exception ex) {
            if (plugin != null) {
                plugin.getLogger().warning("bStats total_items_in_market failed: " + ex.getMessage());
            }
            return 0;
        }
    }

    private String safeLanguage() {
        try {
            String tag = Locale.getDefault().toLanguageTag();
            return (tag == null || tag.isEmpty()) ? "unknown" : tag;
        } catch (Exception ex) {
            if (plugin != null) {
                plugin.getLogger().warning("bStats server_language failed: " + ex.getMessage());
            }
            return "unknown";
        }
    }

    private Map<String, Integer> safePlayerLanguages() {
        Map<String, Integer> locales = new HashMap<>();
        try {
            plugin.getServer().getOnlinePlayers().forEach(player -> {
                String locale = "unknown";
                try {
                    locale = player.getLocale();
                } catch (Exception ignored) {
                    // Ignore if the API is missing or the call fails.
                }
                if (locale == null || locale.isEmpty()) {
                    locale = "unknown";
                }
                locales.put(locale, locales.getOrDefault(locale, 0) + 1);
            });
        } catch (Exception ex) {
            if (plugin != null) {
                plugin.getLogger().warning("bStats player_language failed: " + ex.getMessage());
            }
        }
        return locales;
    }

    private Map<String, Map<String, Integer>> safeInstalledPlugins() {
        Map<String, Map<String, Integer>> data = new HashMap<>();
        try {
            for (org.bukkit.plugin.Plugin installed : plugin.getServer().getPluginManager().getPlugins()) {
                if (installed == null || installed.getDescription() == null) {
                    continue;
                }
                String name = installed.getDescription().getName();
                if (name == null || name.isEmpty()) {
                    continue;
                }
                String version = installed.getDescription().getVersion();
                if (version == null || version.isEmpty()) {
                    version = "unknown";
                }
                Map<String, Integer> versions = data.computeIfAbsent(name, ignored -> new HashMap<>());
                versions.put(version, versions.getOrDefault(version, 0) + 1);
            }
        } catch (Exception ex) {
            if (plugin != null) {
                plugin.getLogger().warning("bStats installed_plugins failed: " + ex.getMessage());
            }
        }
        return data;
    }

}

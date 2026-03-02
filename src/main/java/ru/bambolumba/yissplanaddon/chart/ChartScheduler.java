package ru.bambolumba.yissplanaddon.chart;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import ru.bambolumba.yissplanaddon.config.PluginConfig;

import java.util.logging.Level;

public class ChartScheduler {

    private final Plugin plugin;
    private final PluginConfig config;
    private final ChartGenerator generator;

    public ChartScheduler(Plugin plugin, PluginConfig config, ChartGenerator generator) {
        this.plugin = plugin;
        this.config = config;
        this.generator = generator;
    }

    public void scheduleGeneration() {
        long delayTicks = config.chartOffsetMinutes() * 60L * 20L;
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            try {
                generator.generate();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Ошибка генерации графиков", e);
            }
        }, delayTicks);
    }

    public void generateNow() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                generator.generate();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Ошибка генерации графиков", e);
            }
        });
    }
}

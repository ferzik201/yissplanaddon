package ru.bambolumba.yissplanaddon;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import ru.bambolumba.yissplanaddon.chart.ChartGenerator;
import ru.bambolumba.yissplanaddon.chart.ChartScheduler;
import ru.bambolumba.yissplanaddon.config.PluginConfig;
import ru.bambolumba.yissplanaddon.db.DatabaseManager;
import ru.bambolumba.yissplanaddon.db.SnapshotRepository;
import ru.bambolumba.yissplanaddon.snapshot.SnapshotScheduler;

import java.util.List;
import java.util.logging.Level;

public class YissPlanAddon extends JavaPlugin {

    private DatabaseManager databaseManager;
    private SnapshotRepository repository;
    private SnapshotScheduler snapshotScheduler;
    private ChartScheduler chartScheduler;
    private PluginConfig pluginConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("chart-template.html", false);

        pluginConfig = PluginConfig.from(getConfig());

        try {
            databaseManager = new DatabaseManager(pluginConfig, getLogger());
            databaseManager.createTables();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Ошибка подключения к БД. Плагин отключается.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        repository = new SnapshotRepository(databaseManager, getLogger());

        ChartGenerator chartGenerator = new ChartGenerator(repository, pluginConfig,
                getDataFolder(), getLogger());
        chartScheduler = new ChartScheduler(this, pluginConfig, chartGenerator);

        snapshotScheduler = new SnapshotScheduler(this, pluginConfig, repository, chartScheduler);
        snapshotScheduler.start();

        registerPlanExtension();

        getLogger().info("YissPlanAddon v" + getDescription().getVersion() + " включён");
    }

    @Override
    public void onDisable() {
        if (snapshotScheduler != null) {
            snapshotScheduler.stop();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("YissPlanAddon отключён");
    }

    private void registerPlanExtension() {
        if (getServer().getPluginManager().getPlugin("Plan") == null) {
            getLogger().warning("Plan не найден — DataExtension не зарегистрирован");
            return;
        }
        try {
            var extension = new ru.bambolumba.yissplanaddon.extension.YissDataExtension(repository, getLogger());
            com.djrapitops.plan.extension.ExtensionService.getInstance().register(extension);
            getLogger().info("Plan DataExtension зарегистрирован");
        } catch (Exception e) {
            getLogger().warning("Не удалось зарегистрировать Plan DataExtension: " + e.getMessage());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("yissplan")) {
            return false;
        }

        if (args.length == 0) {
            sender.sendMessage("§6Использование: /yissplan <reload|snapshot|chart>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                reloadConfig();
                pluginConfig = PluginConfig.from(getConfig());
                sender.sendMessage("§aКонфигурация перезагружена");
            }
            case "snapshot" -> {
                sender.sendMessage("§eЗапуск снимка экономики...");
                getServer().getScheduler().runTaskAsynchronously(this, () -> {
                    snapshotScheduler.runSnapshot();
                    sender.sendMessage("§aСнимок экономики выполнен");
                });
            }
            case "chart" -> {
                sender.sendMessage("§eГенерация графиков...");
                chartScheduler.generateNow();
                sender.sendMessage("§aГрафики сгенерированы");
            }
            default -> sender.sendMessage("§6Использование: /yissplan <reload|snapshot|chart>");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("yissplan") && args.length == 1) {
            return List.of("reload", "snapshot", "chart").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}

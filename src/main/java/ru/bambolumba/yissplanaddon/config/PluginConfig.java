package ru.bambolumba.yissplanaddon.config;

import org.bukkit.configuration.file.FileConfiguration;

public record PluginConfig(
        String dbHost,
        int dbPort,
        String dbName,
        String dbUser,
        String dbPassword,
        int poolSize,
        double defaultRatio,
        double baseValue,
        int intervalMinutes,
        int retentionDays,
        String chartOutputPath,
        int chartOffsetMinutes
) {

    public static PluginConfig from(FileConfiguration config) {
        return new PluginConfig(
                config.getString("database.host", "localhost"),
                config.getInt("database.port", 3306),
                config.getString("database.name", "minecraft"),
                config.getString("database.user", "mc_user"),
                config.getString("database.password", "changeme"),
                config.getInt("database.pool-size", 3),
                config.getDouble("income-coeff.default-ratio", 1.0),
                config.getDouble("income-coeff.base-value", 3000.0),
                config.getInt("snapshots.interval-minutes", 10),
                config.getInt("snapshots.retention-days", 60),
                config.getString("chart.output-path", ""),
                config.getInt("chart.regenerate-offset-minutes", 2)
        );
    }
}

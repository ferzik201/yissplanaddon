package ru.bambolumba.yissplanaddon.chart;

import com.google.gson.Gson;
import ru.bambolumba.yissplanaddon.config.PluginConfig;
import ru.bambolumba.yissplanaddon.db.SnapshotRepository;
import ru.bambolumba.yissplanaddon.db.SnapshotRepository.SnapshotRow;
import ru.bambolumba.yissplanaddon.db.SnapshotRepository.TimedValue;
import ru.bambolumba.yissplanaddon.snapshot.PlayerEntry;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ChartGenerator {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd.MM HH:mm")
            .withZone(ZoneId.systemDefault());
    private static final String[] COLORS = {"#60d65e", "#2e9ff0", "#f2c450", "#fe6f5e", "#a78bfa"};
    private static final Gson GSON = new Gson();

    private final SnapshotRepository repository;
    private final PluginConfig config;
    private final File dataFolder;
    private final Logger logger;

    public ChartGenerator(SnapshotRepository repository, PluginConfig config,
                          File dataFolder, Logger logger) {
        this.repository = repository;
        this.config = config;
        this.dataFolder = dataFolder;
        this.logger = logger;
    }

    public void generate() {
        try {
            Path templatePath = dataFolder.toPath().resolve("chart-template.html");
            if (!Files.exists(templatePath)) {
                logger.warning("Шаблон chart-template.html не найден в папке плагина");
                return;
            }

            String template = Files.readString(templatePath, StandardCharsets.UTF_8);

            int days = config.retentionDays();

            List<TimedValue> coinsHistory = repository.getTotalCoinsHistory(days);
            List<TimedValue> incomeHistory = repository.getIncomeHistory(days);
            List<SnapshotRow> snapshotRows = repository.getAllSnapshotRows(days);

            template = template.replace("{{TOTAL_COINS_LABELS}}", toLabelsJson(coinsHistory));
            template = template.replace("{{TOTAL_COINS_DATA}}", toDataJson(coinsHistory));
            template = template.replace("{{INCOME_LABELS}}", toLabelsJson(incomeHistory));
            template = template.replace("{{INCOME_DATA}}", toDataJson(incomeHistory));

            List<String> top5Names = resolveTop5Names(snapshotRows);
            List<Instant> timestamps = snapshotRows.stream()
                    .map(SnapshotRow::time)
                    .toList();

            template = template.replace("{{TOP5_LABELS}}", toTimestampLabelsJson(timestamps));
            template = template.replace("{{TOP5_DATASETS}}", buildTop5Datasets(snapshotRows, top5Names));

            Path outputPath = resolveOutputPath();
            if (outputPath == null) {
                logger.warning("Не удалось определить путь для генерации графиков");
                return;
            }

            Files.createDirectories(outputPath.getParent());
            Files.writeString(outputPath, template, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            logger.info("Графики обновлены: " + outputPath);

        } catch (IOException e) {
            logger.log(Level.WARNING, "Ошибка генерации графиков", e);
        }
    }

    private Path resolveOutputPath() {
        String configured = config.chartOutputPath();
        if (configured != null && !configured.isEmpty()) {
            return Path.of(configured, "yiss-stats.html");
        }

        try {
            org.bukkit.plugin.Plugin plan = org.bukkit.Bukkit.getPluginManager().getPlugin("Plan");
            if (plan != null) {
                return plan.getDataFolder().toPath().resolve("web").resolve("custom").resolve("yiss-stats.html");
            }
        } catch (Exception e) {
            logger.warning("Plan не найден, используется папка плагина для графиков");
        }

        return dataFolder.toPath().resolve("web").resolve("yiss-stats.html");
    }

    private List<String> resolveTop5Names(List<SnapshotRow> rows) {
        if (rows.isEmpty()) return List.of();
        String lastJson = rows.get(rows.size() - 1).top5Json();
        List<PlayerEntry> latest = repository.parseTop5Json(lastJson);
        return latest.stream()
                .map(PlayerEntry::name)
                .limit(5)
                .toList();
    }

    private String toLabelsJson(List<TimedValue> data) {
        List<String> labels = data.stream()
                .map(tv -> FMT.format(tv.time()))
                .toList();
        return GSON.toJson(labels);
    }

    private String toDataJson(List<TimedValue> data) {
        List<Double> values = data.stream()
                .map(TimedValue::value)
                .toList();
        return GSON.toJson(values);
    }

    private String toTimestampLabelsJson(List<Instant> timestamps) {
        List<String> labels = timestamps.stream()
                .map(FMT::format)
                .toList();
        return GSON.toJson(labels);
    }

    private String buildTop5Datasets(List<SnapshotRow> rows, List<String> playerNames) {
        if (playerNames.isEmpty()) return "[]";

        List<Map<String, Object>> datasets = new ArrayList<>();

        for (int i = 0; i < playerNames.size(); i++) {
            String name = playerNames.get(i);
            String color = COLORS[i % COLORS.length];

            List<Double> data = new ArrayList<>();
            for (SnapshotRow row : rows) {
                List<PlayerEntry> entries = repository.parseTop5Json(row.top5Json());
                Double balance = null;
                for (PlayerEntry entry : entries) {
                    if (name.equals(entry.name())) {
                        balance = entry.balance();
                        break;
                    }
                }
                data.add(balance);
            }

            Map<String, Object> dataset = new LinkedHashMap<>();
            dataset.put("label", name);
            dataset.put("data", data);
            dataset.put("borderColor", color);
            dataset.put("backgroundColor", color + "1a");
            dataset.put("fill", false);
            dataset.put("tension", 0.3);
            dataset.put("spanGaps", true);
            datasets.add(dataset);
        }

        return GSON.toJson(datasets);
    }
}

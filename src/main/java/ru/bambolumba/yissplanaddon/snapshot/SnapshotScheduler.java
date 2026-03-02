package ru.bambolumba.yissplanaddon.snapshot;

import me.yic.xconomy.api.XConomyAPI;
import me.yic.xconomy.data.syncdata.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import ru.bambolumba.yissplanaddon.chart.ChartScheduler;
import ru.bambolumba.yissplanaddon.config.PluginConfig;
import ru.bambolumba.yissplanaddon.db.SnapshotRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SnapshotScheduler {

    private final Plugin plugin;
    private final PluginConfig config;
    private final SnapshotRepository repository;
    private final ChartScheduler chartScheduler;
    private final Logger logger;
    private int taskId = -1;

    public SnapshotScheduler(Plugin plugin, PluginConfig config,
                             SnapshotRepository repository, ChartScheduler chartScheduler) {
        this.plugin = plugin;
        this.config = config;
        this.repository = repository;
        this.chartScheduler = chartScheduler;
        this.logger = plugin.getLogger();
    }

    public void start() {
        long intervalTicks = config.intervalMinutes() * 60L * 20L;
        taskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::runSnapshot,
                60L * 20L, intervalTicks).getTaskId();
    }

    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    public void runSnapshot() {
        try {
            Callable<EconomySnapshot> callable = () -> {
                try {
                    XConomyAPI api = new XConomyAPI();
                    BigDecimal totalBD = api.getsumbalance();
                    double total = totalBD != null ? totalBD.doubleValue() : 0.0;

                    List<String> topNames = api.getbalancetop();
                    List<PlayerEntry> top5 = new ArrayList<>();

                    if (topNames != null) {
                        for (int i = 0; i < Math.min(5, topNames.size()); i++) {
                            try {
                                PlayerData pd = api.getPlayerData(topNames.get(i));
                                if (pd != null) {
                                    top5.add(new PlayerEntry(
                                            pd.getUniqueId().toString(),
                                            pd.getName(),
                                            pd.getBalance().doubleValue()));
                                }
                            } catch (Exception ex) {
                                logger.log(Level.WARNING, "Ошибка получения данных игрока: " + topNames.get(i), ex);
                            }
                        }
                    }

                    return new EconomySnapshot(Instant.now(), total, top5);
                } catch (Exception e) {
                    logger.warning("XConomy API недоступен, используются нулевые значения: " + e.getMessage());
                    return new EconomySnapshot(Instant.now(), 0.0, List.of());
                }
            };

            EconomySnapshot snapshot = Bukkit.getScheduler()
                    .callSyncMethod(plugin, callable)
                    .get();

            BigDecimal totalBD = BigDecimal.valueOf(snapshot.totalCoins());
            double incomeCoeff = calculateIncomeRatio(totalBD);

            repository.insertSnapshot(snapshot, incomeCoeff);
            repository.deleteOldRecords(config.retentionDays());

            logger.info("Снимок экономики сохранён. Всего монет: " + snapshot.totalCoins()
                    + ", коэффициент дохода: " + incomeCoeff);

            chartScheduler.scheduleGeneration();

        } catch (Exception e) {
            logger.log(Level.WARNING, "Ошибка выполнения снимка экономики", e);
        }
    }

    private double calculateIncomeRatio(BigDecimal total) {
        double defaultRatio = config.defaultRatio();
        double offset = config.baseValue();
        double factor = 100_000.0;
        double minRatio = 0.5;

        BigDecimal offsetBD = BigDecimal.valueOf(offset);
        if (total.compareTo(offsetBD) <= 0) {
            return defaultRatio;
        }
        BigDecimal drop = total.subtract(offsetBD)
                .divide(BigDecimal.valueOf(factor), 5, RoundingMode.HALF_UP);
        double ratio = defaultRatio - drop.doubleValue();
        return Math.max(ratio, minRatio);
    }
}

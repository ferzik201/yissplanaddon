package ru.bambolumba.yissplanaddon.extension;

import com.djrapitops.plan.extension.DataExtension;
import com.djrapitops.plan.extension.annotation.*;
import com.djrapitops.plan.extension.icon.Color;
import com.djrapitops.plan.extension.icon.Family;
import com.djrapitops.plan.extension.table.Table;
import me.yic.xconomy.api.XConomyAPI;
import me.yic.xconomy.data.syncdata.PlayerData;
import ru.bambolumba.yissplanaddon.db.SnapshotRepository;
import ru.bambolumba.yissplanaddon.snapshot.PlayerEntry;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

@PluginInfo(name = "YissPlanAddon", iconName = "coins", iconFamily = Family.SOLID, color = Color.GREEN)
@TabInfo(tab = "💰 Экономика", iconName = "coins", iconFamily = Family.SOLID, elementOrder = {})
public class YissDataExtension implements DataExtension {

    private final SnapshotRepository repository;
    private final Logger logger;

    public YissDataExtension(SnapshotRepository repository, Logger logger) {
        this.repository = repository;
        this.logger = logger;
    }

    @NumberProvider(
            text = "Всего монет на сервере",
            description = "Общее количество монет на сервере",
            priority = 100,
            iconName = "coins",
            iconFamily = Family.SOLID,
            iconColor = Color.GREEN,
            format = NumberProvider.FORMAT_NONE
    )
    @Tab("💰 Экономика")
    public long totalCoins() {
        try {
            return (long) repository.getLatestTotalCoins();
        } catch (Exception e) {
            logger.warning("Ошибка получения total_coins для Plan: " + e.getMessage());
            return 0L;
        }
    }

    @TableProvider(tableColor = Color.GREEN)
    @Tab("💰 Экономика")
    public Table top5Players() {
        Table.Factory table = Table.builder()
                .columnOne("Игрок", null)
                .columnTwo("Баланс", null);

        try {
            List<PlayerEntry> top5 = repository.getLatestTop5();
            for (PlayerEntry entry : top5) {
                table.addRow(entry.name(), String.format("%.2f", entry.balance()));
            }
        } catch (Exception e) {
            logger.warning("Ошибка получения top5 для Plan: " + e.getMessage());
        }

        return table.build();
    }

    @NumberProvider(
            text = "Баланс",
            description = "Текущий баланс игрока",
            priority = 100,
            iconName = "wallet",
            iconFamily = Family.SOLID,
            iconColor = Color.GREEN,
            format = NumberProvider.FORMAT_NONE
    )
    public long playerBalance(UUID playerUUID) {
        try {
            XConomyAPI api = new XConomyAPI();
            PlayerData data = api.getPlayerData(playerUUID);
            if (data != null) {
                return data.getBalance().longValue();
            }
        } catch (Exception e) {
            logger.warning("Ошибка получения баланса игрока для Plan: " + e.getMessage());
        }
        return 0L;
    }
}

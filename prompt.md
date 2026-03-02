# YissPlanAddon — Technical Specification (v4, Economy only)

## Overview

A new Bukkit/Paper plugin that extends Plan (player analytics) with economy statistics.
It fetches economy data via XConomy Java API, periodically snapshots it to MariaDB,
and generates a static HTML dashboard (Chart.js) placed inside Plan''s web directory.

**This spec covers YissPlanAddon only. Clan support is out of scope for this version.**

**Target server:** Paper 1.21.x  
**Language:** Java 21  
**Build system:** Maven  
**Database:** MariaDB

---

## External Data Sources

### XConomy (economy plugin)
Use XConomy''s Java API directly:
```java
XConomy xconomy = (XConomy) Bukkit.getPluginManager().getPlugin("XConomy");
XConomyAPI api = xconomy.getAPI();
double balance    = api.getBalance(uuid);
// top 5 and total — use whatever methods are available on the API
```
- Do NOT query XConomy''s database directly
- Do NOT use Redis
- XConomy API calls: if the API requires main thread, schedule via `Bukkit.getScheduler().runTask(plugin, ...)`, then hand results to async for DB write
- If XConomy absent or API call fails: store 0.0, log warning, do not crash

### Income coefficient
Calculated directly in YissPlanAddon from XConomy's total balance. No external table needed.

Formula (Java):
```java
private double calculateIncomeRatio(BigDecimal total) {
    double defaultRatio = config.getDefaultRatio();   // default: 1.0
    double offset       = config.getBaseValue();       // default: 3000.0
    double factor       = 100_000.0;
    double minRatio     = 0.5;

    BigDecimal offsetBD = BigDecimal.valueOf(offset);
    if (total.compareTo(offsetBD) <= 0) {
        return defaultRatio;
    }
    BigDecimal drop = total.subtract(offsetBD)
        .divide(BigDecimal.valueOf(factor), 5, RoundingMode.HALF_UP);
    double ratio = defaultRatio - drop.doubleValue();
    return Math.max(ratio, minRatio);
}
```

Called during each snapshot run, immediately after fetching `totalCoins` from XConomy API.
The result is stored in `ypa_economy_income` alongside the snapshot timestamp.

---

## Project Structure

```
YissPlanAddon/
├── src/main/java/ru/bambolumba/yissplanaddon/
│   ├── YissPlanAddon.java
│   ├── db/
│   │   ├── DatabaseManager.java       # HikariCP pool, CREATE TABLE IF NOT EXISTS
│   │   └── SnapshotRepository.java    # Insert and select economy snapshots
│   ├── snapshot/
│   │   ├── SnapshotScheduler.java     # Async timer, every 10 min
│   │   ├── EconomySnapshot.java       # record(Instant capturedAt, double totalCoins, List<PlayerEntry> top5)
│   │   └── PlayerEntry.java           # record(String uuid, String name, double balance)
│   ├── extension/
│   │   └── YissDataExtension.java     # Plan DataExtension implementation
│   ├── chart/
│   │   ├── ChartGenerator.java        # Builds HTML from template + DB data
│   │   └── ChartScheduler.java        # Async, 2 min after snapshot
│   └── config/
│       └── PluginConfig.java
├── src/main/resources/
│   ├── plugin.yml
│   ├── config.yml
│   └── chart-template.html
└── pom.xml
```

---

## Database Schema

### ypa_economy_snapshots
```sql
CREATE TABLE IF NOT EXISTS ypa_economy_snapshots (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  captured_at DATETIME NOT NULL,
  total_coins DOUBLE   NOT NULL,
  top5_json   TEXT     NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```
`top5_json` format: `[{"uuid":"...","name":"...","balance":1234.5}, ...]`

### ypa_economy_income
```sql
CREATE TABLE IF NOT EXISTS ypa_economy_income (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  captured_at  DATETIME NOT NULL,
  income_coeff DOUBLE   NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

Retention: on every snapshot run, delete rows older than 60 days from both tables:
```sql
DELETE FROM ypa_economy_snapshots WHERE captured_at < NOW() - INTERVAL 60 DAY;
DELETE FROM ypa_economy_income    WHERE captured_at < NOW() - INTERVAL 60 DAY;
```

---

## Snapshot Scheduler

Runs async every 10 minutes via `BukkitScheduler.runTaskTimerAsynchronously`.

Steps:
1. Fetch economy data from XConomy API (on main thread if required, pass result to async)
2. Fetch income coeff via SQL (async)
3. Write `EconomySnapshot` and income record to DB
4. Delete records older than 60 days

---

## Plan DataExtension

Implements `com.djrapitops.plan.extension.DataExtension`.  
Registered in `onEnable()` inside try-catch — silent skip if Plan is absent.

**Server page — Tab "💰 Экономика":**
- `@DoubleProvider` "Всего монет на сервере" — latest `total_coins` from DB
- `@TableProvider` "Топ 5 игроков по балансу" — columns: Игрок | Баланс

**Player page:**
- `@DoubleProvider` "Баланс" — current balance via XConomy API

---

## Chart Generation

`ChartScheduler` runs async, 2 minutes after each snapshot (via `runTaskLaterAsynchronously`).

### SQL for charts (last 60 days)

Total coins over time:
```sql
SELECT captured_at, total_coins
FROM ypa_economy_snapshots
WHERE captured_at > NOW() - INTERVAL 60 DAY
ORDER BY captured_at ASC;
```

Income coefficient over time:
```sql
SELECT captured_at, income_coeff
FROM ypa_economy_income
WHERE captured_at > NOW() - INTERVAL 60 DAY
ORDER BY captured_at ASC;
```

Top-5 players balance over time:
- Determine current top-5 player names from the latest snapshot''s `top5_json`
- Iterate all snapshot rows, parse `top5_json`, extract balance for each of the 5 players
- Build one dataset per player (some values may be null if player wasn''t in top-5 that snapshot — use `null` in JS array to create gaps)

### Charts (each a separate `<canvas>`)

1. **Общее количество монет** — line chart, 1 dataset
2. **Коэффициент дохода** — line chart, 1 dataset
3. **Баланс топ-5 игроков** — line chart, up to 5 datasets, one per player

All charts share the same X axis (timestamps, formatted as `DD.MM HH:mm`).

### Output

Write to: `{Plan_web_dir}/custom/yiss-stats.html`  
Resolve Plan web dir via `Plan` API. Configurable fallback path in config.yml.  
`Files.writeString(path, html, UTF_8, CREATE, TRUNCATE_EXISTING)` — async thread only.  
`chart-template.html` is extracted to plugin data folder on first run (standard `saveResource`).  
`ChartGenerator` always reads the template from the data folder (not from the jar), so the admin can customize it.

---

## config.yml

```yaml
database:
  host: localhost
  port: 3306
  name: "minecraft"
  user: "mc_user"
  password: "changeme"
  pool-size: 3

income-coeff:
  default-ratio: 1.0
  base-value: 3000.0

snapshots:
  interval-minutes: 10
  retention-days: 60

chart:
  output-path: ""              # auto-detect Plan web dir if empty
  regenerate-offset-minutes: 2
```

---

## plugin.yml

```yaml
name: YissPlanAddon
version: 4.0.0
main: ru.bambolumba.yissplanaddon.YissPlanAddon
api-version: "1.21"
softdepend: [Plan, XConomy]
commands:
  yissplan:
    description: Управление YissPlanAddon
    usage: /yissplan <reload|snapshot|chart>
    permission: yissplanaddon.admin
```

---

## Color Palette (YissCraft branding)

```
Dataset colors:
  #60d65e  green   (total coins, player 1)
  #2e9ff0  blue    (income coeff, player 2)
  #f2c450  gold    (player 3)
  #fe6f5e  red     (player 4)
  #a78bfa  purple  (player 5)

Page background:  #1a1a2e
Card background:  #16213e
Text color:       #e0e0e0
Grid lines:       rgba(255,255,255,0.08)
```

---

## Error Handling

- XConomy absent or API fail: store 0.0, log warning, continue
- Plan absent: DataExtension registration silently skipped, chart generation still runs
- Income coeff table missing: store 0.0, log warning
- DB connection fail: log error, skip snapshot, retry next interval
- Chart generation error: log error, do not crash
- All SQL wrapped in try-catch with warning logs

---

## Notes for Agent

- Java 21, use records for all POJOs
- Never run DB or file I/O on the main server thread
- XConomy API access via plugin cast — wrap in try-catch, do not use reflection
- HikariCP pool-size: 3
- No Redis in this version
- chart-template.html: dark theme, responsive, Chart.js from CDN, self-contained
- All in-game messages and command responses in Russian
- Commands: `/yissplan reload`, `/yissplan snapshot`, `/yissplan chart`
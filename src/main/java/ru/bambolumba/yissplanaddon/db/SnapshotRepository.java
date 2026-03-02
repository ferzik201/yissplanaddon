package ru.bambolumba.yissplanaddon.db;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import ru.bambolumba.yissplanaddon.snapshot.EconomySnapshot;
import ru.bambolumba.yissplanaddon.snapshot.PlayerEntry;

import java.lang.reflect.Type;
import java.sql.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SnapshotRepository {

    private static final Gson GSON = new Gson();
    private static final Type PLAYER_LIST_TYPE = new TypeToken<List<PlayerEntry>>() {}.getType();

    private final DatabaseManager db;
    private final Logger logger;

    public SnapshotRepository(DatabaseManager db, Logger logger) {
        this.db = db;
        this.logger = logger;
    }

    public void insertSnapshot(EconomySnapshot snapshot, double incomeCoeff) {
        String top5Json = GSON.toJson(snapshot.top5());
        LocalDateTime ldt = LocalDateTime.ofInstant(snapshot.capturedAt(), ZoneId.systemDefault());

        try (Connection conn = db.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO ypa_economy_snapshots (captured_at, total_coins, top5_json) VALUES (?, ?, ?)")) {
                ps.setTimestamp(1, Timestamp.valueOf(ldt));
                ps.setDouble(2, snapshot.totalCoins());
                ps.setString(3, top5Json);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO ypa_economy_income (captured_at, income_coeff) VALUES (?, ?)")) {
                ps.setTimestamp(1, Timestamp.valueOf(ldt));
                ps.setDouble(2, incomeCoeff);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Ошибка записи снимка в БД", e);
        }
    }

    public void deleteOldRecords(int retentionDays) {
        try (Connection conn = db.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM ypa_economy_snapshots WHERE captured_at < NOW() - INTERVAL "
                    + retentionDays + " DAY");
            stmt.executeUpdate("DELETE FROM ypa_economy_income WHERE captured_at < NOW() - INTERVAL "
                    + retentionDays + " DAY");
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Ошибка удаления старых записей", e);
        }
    }

    public double getLatestTotalCoins() {
        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT total_coins FROM ypa_economy_snapshots ORDER BY captured_at DESC LIMIT 1")) {
            if (rs.next()) {
                return rs.getDouble("total_coins");
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Ошибка чтения total_coins из БД", e);
        }
        return 0.0;
    }

    public List<PlayerEntry> getLatestTop5() {
        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT top5_json FROM ypa_economy_snapshots ORDER BY captured_at DESC LIMIT 1")) {
            if (rs.next()) {
                String json = rs.getString("top5_json");
                return GSON.fromJson(json, PLAYER_LIST_TYPE);
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Ошибка чтения top5 из БД", e);
        }
        return List.of();
    }

    public record TimedValue(Instant time, double value) {}

    public List<TimedValue> getTotalCoinsHistory(int days) {
        List<TimedValue> result = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT captured_at, total_coins FROM ypa_economy_snapshots " +
                             "WHERE captured_at > NOW() - INTERVAL ? DAY ORDER BY captured_at ASC")) {
            ps.setInt(1, days);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Instant t = rs.getTimestamp("captured_at").toInstant();
                    result.add(new TimedValue(t, rs.getDouble("total_coins")));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Ошибка чтения истории total_coins", e);
        }
        return result;
    }

    public List<TimedValue> getIncomeHistory(int days) {
        List<TimedValue> result = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT captured_at, income_coeff FROM ypa_economy_income " +
                             "WHERE captured_at > NOW() - INTERVAL ? DAY ORDER BY captured_at ASC")) {
            ps.setInt(1, days);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Instant t = rs.getTimestamp("captured_at").toInstant();
                    result.add(new TimedValue(t, rs.getDouble("income_coeff")));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Ошибка чтения истории income_coeff", e);
        }
        return result;
    }

    public record SnapshotRow(Instant time, String top5Json) {}

    public List<SnapshotRow> getAllSnapshotRows(int days) {
        List<SnapshotRow> result = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT captured_at, top5_json FROM ypa_economy_snapshots " +
                             "WHERE captured_at > NOW() - INTERVAL ? DAY ORDER BY captured_at ASC")) {
            ps.setInt(1, days);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Instant t = rs.getTimestamp("captured_at").toInstant();
                    result.add(new SnapshotRow(t, rs.getString("top5_json")));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Ошибка чтения истории снимков", e);
        }
        return result;
    }

    public List<PlayerEntry> parseTop5Json(String json) {
        try {
            List<PlayerEntry> list = GSON.fromJson(json, PLAYER_LIST_TYPE);
            return list != null ? list : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }
}

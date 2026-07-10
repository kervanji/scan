package com.murakib.attendance.repository;

import com.murakib.attendance.db.DatabaseManager;
import com.murakib.attendance.model.AuditLog;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class AuditLogRepository {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private final DatabaseManager db;

    public AuditLogRepository(DatabaseManager db) {
        this.db = db;
    }

    public void log(String adminUsername, String actionType, String targetTable, Long targetId, String oldValue, String newValue) throws Exception {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                 INSERT INTO audit_logs (admin_username, action_type, target_table, target_id, old_value, new_value, action_time)
                 VALUES (?, ?, ?, ?, ?, ?, ?)
                 """)) {
            ps.setString(1, adminUsername);
            ps.setString(2, actionType);
            ps.setString(3, targetTable);
            if (targetId != null) {
                ps.setLong(4, targetId);
            } else {
                ps.setNull(4, java.sql.Types.INTEGER);
            }
            ps.setString(5, oldValue);
            ps.setString(6, newValue);
            ps.setString(7, LocalDateTime.now().format(FORMATTER));
            ps.executeUpdate();
        }
    }

    public List<AuditLog> findRecent(int limit) throws Exception {
        List<AuditLog> logs = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM audit_logs ORDER BY action_time DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AuditLog log = new AuditLog();
                    log.setId(rs.getLong("id"));
                    log.setAdminUsername(rs.getString("admin_username"));
                    log.setActionType(rs.getString("action_type"));
                    log.setTargetTable(rs.getString("target_table"));
                    long targetId = rs.getLong("target_id");
                    if (!rs.wasNull()) {
                        log.setTargetId(targetId);
                    }
                    log.setOldValue(rs.getString("old_value"));
                    log.setNewValue(rs.getString("new_value"));
                    log.setActionTime(LocalDateTime.parse(rs.getString("action_time"), FORMATTER));
                    logs.add(log);
                }
            }
        }
        return logs;
    }
}

package com.murakib.attendance.db;

import com.murakib.attendance.config.AppPaths;

import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseManager {

    private static DatabaseManager instance;

    private DatabaseManager() {
    }

    public static synchronized DatabaseManager getInstance() throws Exception {
        if (instance == null) {
            instance = new DatabaseManager();
            instance.initialize();
        }
        return instance;
    }

    private void initialize() throws Exception {
        AppPaths.ensureDirectories();
        if (!Files.exists(AppPaths.database())) {
            Files.createFile(AppPaths.database());
        }
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS employees (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    employee_code TEXT NOT NULL UNIQUE,
                    full_name TEXT NOT NULL,
                    department TEXT,
                    job_title TEXT,
                    qr_token TEXT NOT NULL UNIQUE,
                    profile_photo_path TEXT,
                    is_active INTEGER NOT NULL DEFAULT 1,
                    created_at TEXT NOT NULL
                )
                """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS attendance_events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    employee_id INTEGER NOT NULL,
                    event_type TEXT NOT NULL,
                    event_time TEXT NOT NULL,
                    photo_path TEXT,
                    telegram_sent INTEGER NOT NULL DEFAULT 0,
                    device_name TEXT,
                    notes TEXT,
                    created_by TEXT DEFAULT 'SYSTEM',
                    FOREIGN KEY (employee_id) REFERENCES employees(id)
                )
                """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS settings (
                    setting_key TEXT PRIMARY KEY,
                    setting_value TEXT
                )
                """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS audit_logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    admin_username TEXT NOT NULL,
                    action_type TEXT NOT NULL,
                    target_table TEXT,
                    target_id INTEGER,
                    old_value TEXT,
                    new_value TEXT,
                    action_time TEXT NOT NULL
                )
                """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS invalid_qr_attempts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    qr_value TEXT,
                    attempt_time TEXT NOT NULL
                )
                """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS work_shifts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    days_of_week TEXT NOT NULL,
                    start_time TEXT NOT NULL,
                    late_grace_minutes INTEGER NOT NULL DEFAULT 15,
                    end_time TEXT,
                    early_checkin_minutes INTEGER NOT NULL DEFAULT 60,
                    is_active INTEGER NOT NULL DEFAULT 1,
                    sort_order INTEGER NOT NULL DEFAULT 0
                )
                """);
        }
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + AppPaths.database());
    }
}

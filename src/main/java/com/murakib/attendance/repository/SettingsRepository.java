package com.murakib.attendance.repository;

import com.murakib.attendance.db.DatabaseManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class SettingsRepository {

    public static final String ADMIN_PIN_HASH = "admin_pin_hash";
    public static final String TELEGRAM_BOT_TOKEN = "telegram_bot_token";
    public static final String CAMERA_ID = "camera_id";
    public static final String DEVICE_NAME = "device_name";
    public static final String WORK_START_TIME = "work_start_time";
    public static final String LATE_AFTER_TIME = "late_after_time";
    public static final String SCAN_COOLDOWN_SECONDS = "scan_cooldown_seconds";
    public static final String AUTO_MODE = "auto_mode";
    public static final String PHOTO_DELAY_MS = "photo_delay_ms";
    public static final String KIOSK_MODE = "kiosk_mode";
    public static final String AUTO_UPDATE_ENABLED = "auto_update_enabled";
    public static final String AUTO_UPDATE_INSTALL = "auto_update_install";
    public static final String UPDATE_MANIFEST_URL = "update_manifest_url";
    public static final String UPDATE_GITHUB_REPO = "update_github_repo";
    public static final String UPDATE_GITHUB_TOKEN = "update_github_token";
    public static final String UPDATE_CHECK_INTERVAL_HOURS = "update_check_interval_hours";
    public static final String LAST_UPDATE_CHECK = "last_update_check";

    private static final Map<String, String> DEFAULTS = new HashMap<>();

    static {
        DEFAULTS.put(ADMIN_PIN_HASH, hashPin("1234"));
        DEFAULTS.put(TELEGRAM_BOT_TOKEN, "");
        DEFAULTS.put(CAMERA_ID, "0");
        DEFAULTS.put(DEVICE_NAME, "نقطة التسجيل 1");
        DEFAULTS.put(WORK_START_TIME, "08:00");
        DEFAULTS.put(LATE_AFTER_TIME, "08:15");
        DEFAULTS.put(SCAN_COOLDOWN_SECONDS, "30");
        DEFAULTS.put(AUTO_MODE, "false");
        DEFAULTS.put(PHOTO_DELAY_MS, "0");
        DEFAULTS.put(KIOSK_MODE, "false");
        DEFAULTS.put(AUTO_UPDATE_ENABLED, "true");
        DEFAULTS.put(AUTO_UPDATE_INSTALL, "false");
        DEFAULTS.put(UPDATE_MANIFEST_URL, "https://raw.githubusercontent.com/kervanji/scan/main/updates/version.json");
        DEFAULTS.put(UPDATE_GITHUB_REPO, "kervanji/scan");
        DEFAULTS.put(UPDATE_GITHUB_TOKEN, "");
        DEFAULTS.put(UPDATE_CHECK_INTERVAL_HOURS, "6");
        DEFAULTS.put(LAST_UPDATE_CHECK, "");
    }

    private final DatabaseManager db;

    public SettingsRepository(DatabaseManager db) {
        this.db = db;
    }

    public void ensureDefaults() throws Exception {
        for (Map.Entry<String, String> entry : DEFAULTS.entrySet()) {
            if (get(entry.getKey()).isEmpty()) {
                set(entry.getKey(), entry.getValue());
            }
        }
    }

    public Optional<String> get(String key) throws Exception {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT setting_value FROM settings WHERE setting_key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.ofNullable(rs.getString("setting_value"));
                }
            }
        }
        return Optional.empty();
    }

    public String getOrDefault(String key, String defaultValue) throws Exception {
        return get(key).orElse(defaultValue);
    }

    public int getInt(String key, int defaultValue) throws Exception {
        return Integer.parseInt(getOrDefault(key, String.valueOf(defaultValue)));
    }

    public boolean getBoolean(String key, boolean defaultValue) throws Exception {
        return Boolean.parseBoolean(getOrDefault(key, String.valueOf(defaultValue)));
    }

    public void set(String key, String value) throws Exception {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                 INSERT INTO settings (setting_key, setting_value) VALUES (?, ?)
                 ON CONFLICT(setting_key) DO UPDATE SET setting_value = excluded.setting_value
                 """)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }

    public Map<String, String> getAll() throws Exception {
        Map<String, String> settings = new HashMap<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT setting_key, setting_value FROM settings");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                settings.put(rs.getString("setting_key"), rs.getString("setting_value"));
            }
        }
        return settings;
    }

    public boolean verifyPin(String pin) throws Exception {
        String stored = getOrDefault(ADMIN_PIN_HASH, DEFAULTS.get(ADMIN_PIN_HASH));
        return stored.equals(hashPin(pin));
    }

    public void updatePin(String newPin) throws Exception {
        set(ADMIN_PIN_HASH, hashPin(newPin));
    }

    public static String hashPin(String pin) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(pin.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash PIN", e);
        }
    }
}

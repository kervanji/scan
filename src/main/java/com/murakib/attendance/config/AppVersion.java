package com.murakib.attendance.config;

import java.io.InputStream;
import java.util.Properties;

public final class AppVersion {

    private static final Properties PROPS = load();

    private AppVersion() {
    }

    private static Properties load() {
        Properties props = new Properties();
        try (InputStream in = AppVersion.class.getResourceAsStream("/version.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (Exception ignored) {
        }
        if (!props.containsKey("app.version")) {
            props.setProperty("app.version", "1.0.0");
        }
        return props;
    }

    public static String current() {
        return PROPS.getProperty("app.version", "1.0.0");
    }

    public static String appName() {
        return PROPS.getProperty("app.name", "Murakib Attendance");
    }

    public static String defaultManifestUrl() {
        return PROPS.getProperty("update.default.manifest.url", "").trim();
    }

    public static String defaultGithubRepo() {
        return PROPS.getProperty("update.default.github.repo", "").trim();
    }
}

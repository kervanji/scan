package com.murakib.attendance.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class AppPaths {

    private static final Path ROOT = Path.of(System.getProperty("user.home"), "AttendanceSystem");

    private AppPaths() {
    }

    public static Path root() {
        return ROOT;
    }

    public static Path database() {
        return ROOT.resolve("database").resolve("attendance.db");
    }

    public static Path photosRoot() {
        return ROOT.resolve("photos");
    }

    public static Path photosForDate(LocalDate date) {
        return photosRoot().resolve(date.format(DateTimeFormatter.ISO_LOCAL_DATE));
    }

    public static Path backupsRoot() {
        return ROOT.resolve("backups");
    }

    public static Path updatesRoot() {
        return ROOT.resolve("updates");
    }

    public static Path logs() {
        return ROOT.resolve("logs");
    }

    public static Path config() {
        return ROOT.resolve("config");
    }

    public static void ensureDirectories() throws Exception {
        Files.createDirectories(database().getParent());
        Files.createDirectories(photosRoot());
        Files.createDirectories(backupsRoot());
        Files.createDirectories(updatesRoot());
        Files.createDirectories(logs());
        Files.createDirectories(config());
    }
}

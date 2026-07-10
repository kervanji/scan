package com.murakib.attendance.service;

import com.murakib.attendance.config.AppPaths;
import com.murakib.attendance.repository.SettingsRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class BackupService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private final SettingsRepository settingsRepository;
    private final ScheduledExecutorService scheduler;

    public BackupService(SettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "backup-scheduler");
            t.setDaemon(true);
            return t;
        });
        scheduleDailyBackup();
    }

    private void scheduleDailyBackup() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                createBackup();
            } catch (Exception ignored) {
            }
        }, initialDelayMinutes(), 24 * 60, TimeUnit.MINUTES);
    }

    private long initialDelayMinutes() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime next = now.toLocalDate().plusDays(1).atTime(2, 0);
        return java.time.Duration.between(now, next).toMinutes();
    }

    public Path createBackup() throws Exception {
        LocalDate today = LocalDate.now();
        Path backupDir = AppPaths.backupsRoot().resolve(today.format(DATE_FMT));
        Files.createDirectories(backupDir);

        if (Files.exists(AppPaths.database())) {
            Files.copy(AppPaths.database(), backupDir.resolve("attendance.db"), StandardCopyOption.REPLACE_EXISTING);
        }

        Map<String, String> settings = settingsRepository.getAll();
        Path configFile = backupDir.resolve("config.json");
        StringBuilder json = new StringBuilder("{\n");
        boolean first = true;
        for (Map.Entry<String, String> entry : settings.entrySet()) {
            if (SettingsRepository.ADMIN_PIN_HASH.equals(entry.getKey())
                    || SettingsRepository.TELEGRAM_BOT_TOKEN.equals(entry.getKey())) {
                continue;
            }
            if (!first) {
                json.append(",\n");
            }
            first = false;
            json.append("  \"").append(escape(entry.getKey())).append("\": \"")
                    .append(escape(entry.getValue())).append("\"");
        }
        json.append("\n}");
        Files.writeString(configFile, json.toString());

        LocalDate photosDate = today;
        Path photosDir = AppPaths.photosForDate(photosDate);
        if (Files.exists(photosDir)) {
            Path targetPhotos = backupDir.resolve("photos");
            Files.createDirectories(targetPhotos);
            try (Stream<Path> files = Files.list(photosDir)) {
                files.forEach(source -> {
                    try {
                        Files.copy(source, targetPhotos.resolve(source.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
                    } catch (Exception ignored) {
                    }
                });
            }
        }

        return backupDir;
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }
}

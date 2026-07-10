package com.murakib.attendance.service;

import com.murakib.attendance.config.AppPaths;
import com.murakib.attendance.config.AppVersion;
import com.murakib.attendance.model.UpdateManifest;
import com.murakib.attendance.repository.SettingsRepository;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.CodeSource;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdateService {

    public record UpdateCheckResult(
            boolean updateAvailable,
            UpdateManifest manifest,
            String currentVersion,
            String message
    ) {
    }

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final SettingsRepository settings;
    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler;
    private Consumer<UpdateCheckResult> onUpdateAvailable;

    public UpdateService(SettingsRepository settings) {
        this.settings = settings;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "update-checker");
            t.setDaemon(true);
            return t;
        });
    }

    public void setOnUpdateAvailable(Consumer<UpdateCheckResult> listener) {
        this.onUpdateAvailable = listener;
    }

    public void startBackgroundChecks() {
        scheduler.schedule(this::checkSilently, 30, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::checkSilently, 1, intervalHours(), TimeUnit.HOURS);
    }

    private int intervalHours() {
        try {
            return Math.max(1, settings.getInt(SettingsRepository.UPDATE_CHECK_INTERVAL_HOURS, 6));
        } catch (Exception e) {
            return 6;
        }
    }

    private void checkSilently() {
        try {
            if (!isAutoCheckEnabled()) {
                return;
            }
            UpdateCheckResult result = checkForUpdates();
            settings.set(SettingsRepository.LAST_UPDATE_CHECK, LocalDateTime.now().format(ISO));
            if (result.updateAvailable() && onUpdateAvailable != null) {
                onUpdateAvailable.accept(result);
            }
        } catch (Exception ignored) {
        }
    }

    public boolean isAutoCheckEnabled() {
        try {
            return settings.getBoolean(SettingsRepository.AUTO_UPDATE_ENABLED, true);
        } catch (Exception e) {
            return true;
        }
    }

    public UpdateCheckResult checkForUpdates() throws Exception {
        String current = AppVersion.current();
        UpdateManifest manifest = fetchManifest();
        if (manifest == null || manifest.getVersion() == null || manifest.getVersion().isBlank()) {
            return new UpdateCheckResult(false, null, current, "تعذّر قراءة معلومات التحديث");
        }
        if (compareVersions(manifest.getVersion(), current) <= 0) {
            return new UpdateCheckResult(false, manifest, current, "التطبيق محدّث (" + current + ")");
        }
        if (manifest.getMinVersion() != null && !manifest.getMinVersion().isBlank()
                && compareVersions(current, manifest.getMinVersion()) < 0) {
            return new UpdateCheckResult(false, manifest, current,
                    "يتطلب التحديث نسخة " + manifest.getMinVersion() + " على الأقل");
        }
        return new UpdateCheckResult(true, manifest, current,
                "يتوفر تحديث: " + manifest.getVersion());
    }

    public String installUpdate(UpdateManifest manifest, Consumer<String> progress) throws Exception {
        if (manifest.getDownloadUrl() == null || manifest.getDownloadUrl().isBlank()) {
            throw new IllegalStateException("رابط التحميل غير موجود في ملف التحديث");
        }

        Optional<Path> appJar = locateAppJar();
        if (appJar.isEmpty()) {
            throw new IllegalStateException("وضع التطوير: التحديث التلقائي يعمل فقط مع ملف JAR المُثبّت");
        }

        Path updatesDir = AppPaths.updatesRoot();
        Files.createDirectories(updatesDir);
        Path downloadPath = updatesDir.resolve("qr-attendance-" + manifest.getVersion() + ".jar");

        if (progress != null) {
            progress.accept("جاري التحميل...");
        }
        downloadFile(manifest.getDownloadUrl(), downloadPath);

        if (manifest.getSha256() != null && !manifest.getSha256().isBlank()) {
            if (progress != null) {
                progress.accept("جاري التحقق...");
            }
            String actual = sha256(downloadPath);
            if (!actual.equalsIgnoreCase(manifest.getSha256().trim())) {
                Files.deleteIfExists(downloadPath);
                throw new IllegalStateException("فشل التحقق من سلامة الملف (SHA256)");
            }
        }

        Path backupDir = AppPaths.backupsRoot().resolve("before-update-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
        Files.createDirectories(backupDir);
        Files.copy(appJar.get(), backupDir.resolve(appJar.get().getFileName()), StandardCopyOption.REPLACE_EXISTING);

        if (progress != null) {
            progress.accept("جاري التطبيق وإعادة التشغيل...");
        }
        scheduleRestart(downloadPath, appJar.get());
        return "سيتم إعادة تشغيل التطبيق لتطبيق التحديث " + manifest.getVersion();
    }

    private UpdateManifest fetchManifest() throws Exception {
        String url = resolveManifestUrl();
        if (url.isBlank()) {
            throw new IllegalStateException("لم يُضبط رابط التحديث. أدخله من لوحة الإدارة → التحديثات");
        }

        if (url.startsWith("github:")) {
            return fetchFromGitHubReleases(url.substring("github:".length()).trim());
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        return parseManifest(response.body());
    }

    private UpdateManifest fetchFromGitHubReleases(String repo) throws Exception {
        String token = settings.getOrDefault(SettingsRepository.UPDATE_GITHUB_TOKEN, "");
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/repos/" + repo + "/releases/latest"))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", AppVersion.appName())
                .timeout(Duration.ofSeconds(30))
                .GET();
        if (!token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("GitHub API: HTTP " + response.statusCode());
        }
        String body = response.body();
        UpdateManifest manifest = new UpdateManifest();
        manifest.setVersion(extractJsonString(body, "tag_name").replaceFirst("^v", ""));
        manifest.setReleaseNotes(extractJsonString(body, "body"));
        manifest.setDownloadUrl(findReleaseAssetUrl(body));
        return manifest;
    }

    private String findReleaseAssetUrl(String releasesJson) {
        Pattern zipPattern = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]+\\.zip)\"");
        Matcher zm = zipPattern.matcher(releasesJson);
        if (zm.find()) {
            return zm.group(1).replace("\\/", "/");
        }
        Pattern jarPattern = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]+\\.jar)\"");
        Matcher jm = jarPattern.matcher(releasesJson);
        if (jm.find()) {
            return jm.group(1).replace("\\/", "/");
        }
        return "";
    }

    private String resolveManifestUrl() throws Exception {
        String custom = settings.getOrDefault(SettingsRepository.UPDATE_MANIFEST_URL, "").trim();
        if (!custom.isBlank()) {
            return custom;
        }
        String githubRepo = settings.getOrDefault(SettingsRepository.UPDATE_GITHUB_REPO, "").trim();
        if (!githubRepo.isBlank()) {
            if (githubRepo.startsWith("github:")) {
                return "github:" + githubRepo.substring("github:".length());
            }
            return "github:" + githubRepo;
        }
        return AppVersion.defaultManifestUrl();
    }

    public static UpdateManifest parseManifest(String json) {
        UpdateManifest m = new UpdateManifest();
        m.setVersion(extractJsonString(json, "version"));
        m.setDownloadUrl(extractJsonString(json, "downloadUrl"));
        m.setSha256(extractJsonString(json, "sha256"));
        m.setReleaseNotes(extractJsonString(json, "releaseNotes"));
        m.setMinVersion(extractJsonString(json, "minVersion"));
        m.setMandatory(Boolean.parseBoolean(extractJsonString(json, "mandatory")));
        return m;
    }

    private static String extractJsonString(String json, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(\"([^\"]*)\"|(true|false))");
        Matcher m = p.matcher(json);
        if (m.find()) {
            if (m.group(2) != null) {
                return m.group(2);
            }
            return m.group(3);
        }
        return "";
    }

    public static int compareVersions(String a, String b) {
        String[] pa = a.replaceFirst("^v", "").split("\\.");
        String[] pb = b.replaceFirst("^v", "").split("\\.");
        int len = Math.max(pa.length, pb.length);
        for (int i = 0; i < len; i++) {
            int va = i < pa.length ? parsePart(pa[i]) : 0;
            int vb = i < pb.length ? parsePart(pb[i]) : 0;
            if (va != vb) {
                return Integer.compare(va, vb);
            }
        }
        return 0;
    }

    private static int parsePart(String part) {
        String digits = part.replaceAll("[^0-9].*", "");
        if (digits.isEmpty()) {
            return 0;
        }
        return Integer.parseInt(digits);
    }

    private void downloadFile(String url, Path target) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(10))
                .GET()
                .build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("فشل التحميل: HTTP " + response.statusCode());
        }
        try (InputStream in = response.body()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public Optional<Path> locateAppJar() {
        try {
            CodeSource source = UpdateService.class.getProtectionDomain().getCodeSource();
            if (source != null && source.getLocation() != null) {
                Path path = Path.of(source.getLocation().toURI());
                if (Files.isRegularFile(path) && path.toString().endsWith(".jar")) {
                    return Optional.of(path);
                }
            }
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }

    private void scheduleRestart(Path newJar, Path currentJar) throws Exception {
        Path updatesDir = AppPaths.updatesRoot();
        Files.createDirectories(updatesDir);
        String javaBin = Path.of(System.getProperty("java.home"), "bin",
                isWindows() ? "javaw.exe" : "java").toString();

        if (isWindows()) {
            Path script = updatesDir.resolve("apply-update.bat");
            String content = """
                    @echo off
                    timeout /t 2 /nobreak > nul
                    copy /Y "%s" "%s"
                    start "" "%s" -jar "%s"
                    del "%%~f0"
                    """.formatted(newJar, currentJar, javaBin, currentJar);
            Files.writeString(script, content);
            new ProcessBuilder("cmd", "/c", script.toString()).start();
        } else {
            Path script = updatesDir.resolve("apply-update.sh");
            String content = """
                    #!/bin/bash
                    sleep 2
                    cp -f "%s" "%s"
                    "%s" -jar "%s" &
                    rm -f "$0"
                    """.formatted(newJar, currentJar, javaBin, currentJar);
            Files.writeString(script, content);
            script.toFile().setExecutable(true);
            new ProcessBuilder("/bin/bash", script.toString()).start();
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }
}

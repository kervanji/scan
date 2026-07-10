package com.murakib.attendance.service;

import com.murakib.attendance.model.AttendanceEvent;
import com.murakib.attendance.model.Employee;
import com.murakib.attendance.model.EventType;
import com.murakib.attendance.repository.AttendanceRepository;
import com.murakib.attendance.repository.SettingsRepository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TelegramService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale.forLanguageTag("ar"));
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("hh:mm a", java.util.Locale.forLanguageTag("ar"));

    private final SettingsRepository settingsRepository;
    private final AttendanceRepository attendanceRepository;
    private final HttpClient httpClient;
    private final ExecutorService sendExecutor;
    private final ScheduledExecutorService retryExecutor;
    private volatile boolean connected = false;

    public TelegramService(SettingsRepository settingsRepository, AttendanceRepository attendanceRepository) {
        this.settingsRepository = settingsRepository;
        this.attendanceRepository = attendanceRepository;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.sendExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "telegram-send");
            t.setDaemon(true);
            return t;
        });
        this.retryExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "telegram-retry");
            t.setDaemon(true);
            return t;
        });
        this.retryExecutor.scheduleAtFixedRate(this::retryPending, 10, 30, TimeUnit.SECONDS);
    }

    public boolean isConnected() {
        return connected;
    }

    /** يبدأ الإرسال فورًا في خلفية منفصلة دون انتظار دورة إعادة المحاولة */
    public void sendImmediately(AttendanceEvent event, Employee employee, Duration workDuration) {
        sendExecutor.execute(() -> {
            boolean sent = sendWithQuickRetry(event, employee, workDuration);
            if (sent) {
                try {
                    attendanceRepository.updateTelegramSent(event.getId(), true);
                } catch (Exception ignored) {
                }
            }
        });
    }

    private boolean sendWithQuickRetry(AttendanceEvent event, Employee employee, Duration workDuration) {
        for (int attempt = 0; attempt < 3; attempt++) {
            if (sendAttendanceNotification(event, employee, workDuration)) {
                return true;
            }
            if (attempt < 2) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    public boolean sendAttendanceNotification(AttendanceEvent event, Employee employee, Duration workDuration) {
        String token = getToken();
        String chatId = getChatId();
        if (token.isBlank() || chatId.isBlank()) {
            connected = false;
            return false;
        }

        String caption = buildCaption(event, employee, workDuration);
        try {
            // إرسال النص أولًا فورًا ثم الصورة — أسرع وصول للإشعار
            boolean textSent = sendMessage(token, chatId, caption);
            if (!textSent) {
                connected = false;
                return false;
            }

            if (event.getPhotoPath() != null && Files.exists(Path.of(event.getPhotoPath()))) {
                return sendPhoto(token, chatId, Path.of(event.getPhotoPath()), "📷 " + employee.getFullName());
            }
            connected = true;
            return true;
        } catch (Exception e) {
            connected = false;
            return false;
        }
    }

    private String buildCaption(AttendanceEvent event, Employee employee, Duration workDuration) {
        StringBuilder sb = new StringBuilder();
        if (event.getEventType() == EventType.CHECK_IN) {
            sb.append("✅ تسجيل دخول\n\n");
        } else {
            sb.append("🔴 تسجيل خروج\n\n");
        }
        sb.append("الموظف: ").append(employee.getFullName()).append("\n");
        if (employee.getDepartment() != null && !employee.getDepartment().isBlank()) {
            sb.append("القسم: ").append(employee.getDepartment()).append("\n");
        }
        sb.append("التاريخ: ").append(event.getEventTime().format(DATE_FMT)).append("\n");
        sb.append("الوقت: ").append(event.getEventTime().format(TIME_FMT)).append("\n");
        if (event.getDeviceName() != null) {
            sb.append("الجهاز: ").append(event.getDeviceName()).append("\n");
        }
        if (event.getEventType() == EventType.CHECK_OUT && workDuration != null) {
            sb.append("مدة العمل: ").append(AttendanceService.formatDuration(workDuration));
        }
        return sb.toString();
    }

    private boolean sendMessage(String token, String chatId, String text) throws Exception {
        String url = "https://api.telegram.org/bot" + token + "/sendMessage";
        String body = "chat_id=" + encode(chatId) + "&text=" + encode(text);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(15))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        connected = response.statusCode() == 200;
        return connected;
    }

    private boolean sendPhoto(String token, String chatId, Path photoPath, String caption) throws Exception {
        String boundary = "----Boundary" + System.currentTimeMillis();
        byte[] photoBytes = Files.readAllBytes(photoPath);
        byte[] body = buildMultipart(boundary, chatId, caption, photoPath.getFileName().toString(), photoBytes);

        String url = "https://api.telegram.org/bot" + token + "/sendPhoto";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .timeout(Duration.ofSeconds(30))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        connected = response.statusCode() == 200;
        return connected;
    }

    private byte[] buildMultipart(String boundary, String chatId, String caption, String fileName, byte[] photoBytes) {
        String lineEnd = "\r\n";
        StringBuilder sb = new StringBuilder();
        sb.append("--").append(boundary).append(lineEnd);
        sb.append("Content-Disposition: form-data; name=\"chat_id\"").append(lineEnd).append(lineEnd);
        sb.append(chatId).append(lineEnd);
        sb.append("--").append(boundary).append(lineEnd);
        sb.append("Content-Disposition: form-data; name=\"caption\"").append(lineEnd).append(lineEnd);
        sb.append(caption).append(lineEnd);
        sb.append("--").append(boundary).append(lineEnd);
        sb.append("Content-Disposition: form-data; name=\"photo\"; filename=\"").append(fileName).append("\"").append(lineEnd);
        sb.append("Content-Type: image/jpeg").append(lineEnd).append(lineEnd);

        byte[] header = sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] footer = (lineEnd + "--" + boundary + "--" + lineEnd).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] result = new byte[header.length + photoBytes.length + footer.length];
        System.arraycopy(header, 0, result, 0, header.length);
        System.arraycopy(photoBytes, 0, result, header.length, photoBytes.length);
        System.arraycopy(footer, 0, result, header.length + photoBytes.length, footer.length);
        return result;
    }

    private void retryPending() {
        try {
            for (AttendanceEvent event : attendanceRepository.findPendingTelegramEvents()) {
                Employee employee = new Employee();
                employee.setFullName(event.getEmployeeName());
                employee.setDepartment(event.getDepartment());
                employee.setEmployeeCode(event.getEmployeeCode());

                if (sendWithQuickRetry(event, employee, null)) {
                    attendanceRepository.updateTelegramSent(event.getId(), true);
                }
            }
        } catch (Exception ignored) {
        }
    }

    public boolean testConnection() {
        return testConnection(getToken(), getChatId()).success();
    }

    public record TestResult(boolean success, String message) {
    }

    public TestResult testConnection(String token, String chatId) {
        token = token != null ? token.trim() : "";
        chatId = chatId != null ? chatId.trim() : "";
        if (token.isBlank()) {
            return new TestResult(false, "أدخل Bot Token");
        }
        if (chatId.isBlank()) {
            return new TestResult(false, "أدخل Chat ID");
        }
        try {
            if (!verifyToken(token)) {
                return new TestResult(false, "Token غير صحيح — انسخه من BotFather بالضغط Copy");
            }
            if (sendMessage(token, chatId, "🔔 اختبار اتصال نظام الحضور")) {
                return new TestResult(true, "تم الإرسال — تحقق من Telegram");
            }
            return new TestResult(false, "فشل الإرسال — تحقق من Chat ID");
        } catch (Exception e) {
            connected = false;
            return new TestResult(false, e.getMessage());
        }
    }

    private boolean verifyToken(String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.telegram.org/bot" + token + "/getMe"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode() == 200 && response.body().contains("\"ok\":true");
    }

    private String getToken() {
        try {
            return settingsRepository.getOrDefault(SettingsRepository.TELEGRAM_BOT_TOKEN, "");
        } catch (Exception e) {
            return "";
        }
    }

    private String getChatId() {
        try {
            return settingsRepository.getOrDefault(SettingsRepository.TELEGRAM_CHAT_ID, "");
        } catch (Exception e) {
            return "";
        }
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    public void shutdown() {
        sendExecutor.shutdownNow();
        retryExecutor.shutdownNow();
    }
}

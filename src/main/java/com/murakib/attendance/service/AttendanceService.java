package com.murakib.attendance.service;

import com.murakib.attendance.model.AttendanceEvent;
import com.murakib.attendance.model.Employee;
import com.murakib.attendance.model.EventType;
import com.murakib.attendance.repository.AttendanceRepository;
import com.murakib.attendance.repository.EmployeeRepository;
import com.murakib.attendance.repository.SettingsRepository;

import java.awt.Toolkit;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class AttendanceService {

    public record ScanResult(
            boolean success,
            String message,
            AttendanceEvent event,
            Employee employee,
            EventType eventType,
            Duration workDuration
    ) {
        public static ScanResult failure(String message) {
            return new ScanResult(false, message, null, null, null, null);
        }
    }

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("hh:mm a", java.util.Locale.forLanguageTag("ar"));

    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;
    private final SettingsRepository settingsRepository;
    private final PhotoService photoService;
    private final TelegramService telegramService;

    private final Map<String, LocalDateTime> lastScanByToken = new HashMap<>();
    private EventType manualMode = EventType.CHECK_IN;
    private boolean autoMode = false;

    public AttendanceService(
            EmployeeRepository employeeRepository,
            AttendanceRepository attendanceRepository,
            SettingsRepository settingsRepository,
            PhotoService photoService,
            TelegramService telegramService
    ) {
        this.employeeRepository = employeeRepository;
        this.attendanceRepository = attendanceRepository;
        this.settingsRepository = settingsRepository;
        this.photoService = photoService;
        this.telegramService = telegramService;
    }

    public void reloadSettings() throws Exception {
        autoMode = settingsRepository.getBoolean(SettingsRepository.AUTO_MODE, false);
    }

    public void setManualMode(EventType mode) {
        this.manualMode = mode;
    }

    public EventType getManualMode() {
        return manualMode;
    }

    public boolean isAutoMode() {
        return autoMode;
    }

    public ScanResult processQrScan(String qrToken, java.awt.image.BufferedImage cameraFrame) throws Exception {
        int cooldown = settingsRepository.getInt(SettingsRepository.SCAN_COOLDOWN_SECONDS, 30);
        LocalDateTime now = LocalDateTime.now();

        LocalDateTime lastScan = lastScanByToken.get(qrToken);
        if (lastScan != null && Duration.between(lastScan, now).getSeconds() < cooldown) {
            return ScanResult.failure("تم تسجيل العملية مسبقًا، يرجى الانتظار.");
        }

        Optional<Employee> employeeOpt = employeeRepository.findByQrToken(qrToken);
        if (employeeOpt.isEmpty()) {
            attendanceRepository.logInvalidQrAttempt(qrToken);
            Toolkit.getDefaultToolkit().beep();
            return ScanResult.failure("رمز QR غير صالح أو الموظف غير مفعّل.");
        }

        Employee employee = employeeOpt.get();
        EventType eventType = resolveEventType(employee, now.toLocalDate());
        if (eventType == null) {
            return ScanResult.failure("لا يمكن تحديد نوع العملية. يرجى اختيار دخول أو خروج.");
        }

        Optional<AttendanceEvent> openCheckIn = attendanceRepository.findLastOpenCheckIn(employee.getId(), now.toLocalDate());

        if (eventType == EventType.CHECK_OUT && openCheckIn.isEmpty()) {
            return ScanResult.failure("لا يوجد تسجيل دخول مفتوح لهذا الموظف اليوم.");
        }

        if (eventType == EventType.CHECK_IN && openCheckIn.isPresent()) {
            return ScanResult.failure("الموظف مسجّل دخولًا بالفعل. يرجى تسجيل الخروج أولًا.");
        }

        int photoDelay = settingsRepository.getInt(SettingsRepository.PHOTO_DELAY_MS, 0);
        if (photoDelay > 0) {
            Thread.sleep(photoDelay);
        }

        java.nio.file.Path photoPath = null;
        if (cameraFrame != null) {
            photoPath = photoService.saveAttendancePhoto(cameraFrame, employee.getEmployeeCode(), eventType, now);
        }

        AttendanceEvent event = new AttendanceEvent();
        event.setEmployeeId(employee.getId());
        event.setEventType(eventType);
        event.setEventTime(now);
        event.setPhotoPath(photoPath != null ? photoPath.toString() : null);
        event.setTelegramSent(false);
        event.setDeviceName(settingsRepository.getOrDefault(SettingsRepository.DEVICE_NAME, "جهاز التسجيل"));
        event.setCreatedBy("SYSTEM");
        event.setEmployeeName(employee.getFullName());
        event.setEmployeeCode(employee.getEmployeeCode());
        event.setDepartment(employee.getDepartment());

        attendanceRepository.insert(event);
        lastScanByToken.put(qrToken, now);

        Duration workDuration = null;
        if (eventType == EventType.CHECK_OUT && openCheckIn.isPresent()) {
            workDuration = Duration.between(openCheckIn.get().getEventTime(), now);
        }

        telegramService.sendImmediately(event, employee, workDuration);

        String message = buildSuccessMessage(employee, eventType, now, workDuration);
        return new ScanResult(true, message, event, employee, eventType, workDuration);
    }

    private EventType resolveEventType(Employee employee, LocalDate date) throws Exception {
        if (autoMode) {
            Optional<AttendanceEvent> openCheckIn = attendanceRepository.findLastOpenCheckIn(employee.getId(), date);
            return openCheckIn.isPresent() ? EventType.CHECK_OUT : EventType.CHECK_IN;
        }
        return manualMode;
    }

    private String buildSuccessMessage(Employee employee, EventType type, LocalDateTime time, Duration duration) {
        if (type == EventType.CHECK_IN) {
            return "✅ تم تسجيل دخول: " + employee.getFullName() + " - " + time.format(TIME_FMT);
        }
        String durationText = duration != null ? formatDuration(duration) : "";
        return "🔴 تم تسجيل خروج: " + employee.getFullName() + " - " + time.format(TIME_FMT)
                + (durationText.isEmpty() ? "" : " | مدة العمل: " + durationText);
    }

    public static String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        if (hours > 0) {
            return hours + " ساعة و" + minutes + " دقيقة";
        }
        return minutes + " دقيقة";
    }

    public Optional<AttendanceEvent> getLatestEvent() throws Exception {
        return attendanceRepository.findLatestEvent();
    }
}

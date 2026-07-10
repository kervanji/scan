package com.murakib.attendance;

import com.murakib.attendance.db.DatabaseManager;
import com.murakib.attendance.repository.AttendanceRepository;
import com.murakib.attendance.repository.AuditLogRepository;
import com.murakib.attendance.repository.EmployeeRepository;
import com.murakib.attendance.repository.SettingsRepository;
import com.murakib.attendance.repository.WorkShiftRepository;
import com.murakib.attendance.service.AttendanceService;
import com.murakib.attendance.service.BackupService;
import com.murakib.attendance.service.CameraService;
import com.murakib.attendance.service.ExcelExportService;
import com.murakib.attendance.service.NetworkMonitorService;
import com.murakib.attendance.service.PhotoService;
import com.murakib.attendance.service.QrService;
import com.murakib.attendance.service.TelegramService;
import com.murakib.attendance.service.ShiftScheduleService;
import com.murakib.attendance.service.UpdateService;

public class ApplicationContext {

    private static ApplicationContext instance;

    private final DatabaseManager databaseManager;
    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;
    private final SettingsRepository settingsRepository;
    private final AuditLogRepository auditLogRepository;
    private final PhotoService photoService;
    private final QrService qrService;
    private final CameraService cameraService;
    private final TelegramService telegramService;
    private final AttendanceService attendanceService;
    private final ExcelExportService excelExportService;
    private final BackupService backupService;
    private final NetworkMonitorService networkMonitorService;
    private final UpdateService updateService;
    private final WorkShiftRepository workShiftRepository;
    private final ShiftScheduleService shiftScheduleService;

    private ApplicationContext() throws Exception {
        databaseManager = DatabaseManager.getInstance();
        employeeRepository = new EmployeeRepository(databaseManager);
        attendanceRepository = new AttendanceRepository(databaseManager);
        settingsRepository = new SettingsRepository(databaseManager);
        auditLogRepository = new AuditLogRepository(databaseManager);
        settingsRepository.ensureDefaults();
        workShiftRepository = new WorkShiftRepository(databaseManager);
        workShiftRepository.ensureDefaults(settingsRepository);

        photoService = new PhotoService();
        qrService = new QrService();
        cameraService = new CameraService();
        telegramService = new TelegramService(settingsRepository, attendanceRepository);
        attendanceService = new AttendanceService(
                employeeRepository, attendanceRepository, settingsRepository, photoService, telegramService
        );
        shiftScheduleService = new ShiftScheduleService(workShiftRepository);
        attendanceService.reloadSettings();
        excelExportService = new ExcelExportService(attendanceRepository, employeeRepository, shiftScheduleService);
        backupService = new BackupService(settingsRepository);
        networkMonitorService = new NetworkMonitorService();
        updateService = new UpdateService(settingsRepository);

        int cameraId = settingsRepository.getInt(SettingsRepository.CAMERA_ID, 0);
        cameraService.setCameraIndex(cameraId);
    }

    public static synchronized ApplicationContext getInstance() throws Exception {
        if (instance == null) {
            instance = new ApplicationContext();
        }
        return instance;
    }

    public EmployeeRepository employees() {
        return employeeRepository;
    }

    public AttendanceRepository attendance() {
        return attendanceRepository;
    }

    public SettingsRepository settings() {
        return settingsRepository;
    }

    public AuditLogRepository auditLogs() {
        return auditLogRepository;
    }

    public PhotoService photos() {
        return photoService;
    }

    public QrService qr() {
        return qrService;
    }

    public CameraService camera() {
        return cameraService;
    }

    public TelegramService telegram() {
        return telegramService;
    }

    public AttendanceService attendanceService() {
        return attendanceService;
    }

    public ExcelExportService excelExport() {
        return excelExportService;
    }

    public BackupService backup() {
        return backupService;
    }

    public NetworkMonitorService network() {
        return networkMonitorService;
    }

    public UpdateService updates() {
        return updateService;
    }

    public WorkShiftRepository workShifts() {
        return workShiftRepository;
    }

    public ShiftScheduleService shiftSchedule() {
        return shiftScheduleService;
    }

    public void shutdown() {
        cameraService.shutdown();
        telegramService.shutdown();
        backupService.shutdown();
        networkMonitorService.shutdown();
        updateService.shutdown();
    }
}

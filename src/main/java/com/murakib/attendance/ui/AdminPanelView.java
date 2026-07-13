package com.murakib.attendance.ui;

import com.murakib.attendance.ApplicationContext;
import com.murakib.attendance.model.AttendanceEvent;
import com.murakib.attendance.model.AuditLog;
import com.murakib.attendance.model.Employee;
import com.murakib.attendance.model.EventType;
import com.murakib.attendance.repository.SettingsRepository;
import com.murakib.attendance.config.AppVersion;
import com.murakib.attendance.model.WorkShift;
import com.murakib.attendance.repository.WorkShiftRepository;
import com.murakib.attendance.service.ShiftScheduleService;
import com.murakib.attendance.service.UpdateService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

public class AdminPanelView {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("hh:mm a", Locale.forLanguageTag("ar"));

    private final ApplicationContext context;
    private final Stage owner;
    private final String adminUsername;
    private final Stage stage = new Stage();

    private TableView<Employee> employeeTable;
    private TableView<AttendanceEvent> attendanceTable;
    private TableView<AuditLog> auditTable;
    private TableView<WorkShift> shiftTable;
    private DatePicker attendanceDatePicker;

    public AdminPanelView(ApplicationContext context, Stage owner, String adminUsername) {
        this.context = context;
        this.owner = owner;
        this.adminUsername = adminUsername;
        build();
    }

    private void build() {
        stage.initOwner(owner);
        stage.initModality(Modality.NONE);
        stage.setTitle("لوحة الإدارة - " + AppVersion.appName());
        stage.setMinWidth(900);
        stage.setMinHeight(600);

        TabPane tabs = new TabPane();
        tabs.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
        tabs.getTabs().addAll(
                new Tab("الموظفين", buildEmployeesTab()),
                new Tab("الشفتات", buildShiftsTab()),
                new Tab("حضور اليوم", buildAttendanceTab()),
                new Tab("التقارير", buildReportsTab()),
                new Tab("التحديثات", buildUpdatesTab()),
                new Tab("الإعدادات", buildSettingsTab()),
                new Tab("سجل التدقيق", buildAuditTab())
        );
        tabs.getTabs().forEach(t -> t.setClosable(false));

        BorderPane root = new BorderPane(tabs);
        root.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
        root.setPadding(new Insets(10));
        stage.setScene(new Scene(root, 960, 640));
    }

    private VBox buildEmployeesTab() {
        employeeTable = new TableView<>();
        TableColumn<Employee, String> codeCol = new TableColumn<>("الرمز");
        codeCol.setCellValueFactory(new PropertyValueFactory<>("employeeCode"));
        TableColumn<Employee, String> nameCol = new TableColumn<>("الاسم");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("fullName"));
        TableColumn<Employee, String> deptCol = new TableColumn<>("القسم");
        deptCol.setCellValueFactory(new PropertyValueFactory<>("department"));
        TableColumn<Employee, String> activeCol = new TableColumn<>("الحالة");
        activeCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().isActive() ? "مفعّل" : "معطّل"));
        employeeTable.getColumns().addAll(codeCol, nameCol, deptCol, activeCol);
        employeeTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        Button addBtn = new Button("إضافة موظف");
        addBtn.setOnAction(e -> showEmployeeDialog(null));
        Button editBtn = new Button("تعديل");
        editBtn.setOnAction(e -> {
            Employee selected = employeeTable.getSelectionModel().getSelectedItem();
            if (selected != null) showEmployeeDialog(selected);
        });
        Button deactivateBtn = new Button("تعطيل");
        deactivateBtn.setOnAction(e -> deactivateEmployee());
        Button qrBtn = new Button("إنشاء QR");
        qrBtn.setOnAction(e -> generateQrForSelected());
        Button refreshBtn = new Button("تحديث");
        refreshBtn.setOnAction(e -> refreshEmployees());

        HBox toolbar = new HBox(8, addBtn, editBtn, deactivateBtn, qrBtn, refreshBtn);
        toolbar.setAlignment(Pos.CENTER_RIGHT);
        toolbar.setPadding(new Insets(0, 0, 8, 0));

        VBox box = new VBox(8, toolbar, employeeTable);
        VBox.setVgrow(employeeTable, javafx.scene.layout.Priority.ALWAYS);
        refreshEmployees();
        return box;
    }

    private VBox buildAttendanceTab() {
        attendanceDatePicker = new DatePicker(LocalDate.now());
        attendanceDatePicker.setOnAction(e -> refreshAttendance());

        Button refreshBtn = new Button("تحديث");
        refreshBtn.setOnAction(e -> refreshAttendance());
        Button viewPhotoBtn = new Button("عرض الصورة");
        viewPhotoBtn.setOnAction(e -> viewSelectedPhoto());
        Button editBtn = new Button("تعديل السجل");
        editBtn.setOnAction(e -> editSelectedEvent());
        Button absentBtn = new Button("عرض الغائبين");
        absentBtn.setOnAction(e -> showAbsentEmployees());
        Button lateBtn = new Button("عرض المتأخرين");
        lateBtn.setOnAction(e -> showLateEmployees());

        HBox toolbar = new HBox(8, new Label("التاريخ:"), attendanceDatePicker, refreshBtn, viewPhotoBtn, editBtn, absentBtn, lateBtn);
        toolbar.setAlignment(Pos.CENTER_RIGHT);
        toolbar.setPadding(new Insets(0, 0, 8, 0));

        attendanceTable = new TableView<>();
        TableColumn<AttendanceEvent, String> nameCol = new TableColumn<>("الموظف");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("employeeName"));
        TableColumn<AttendanceEvent, String> typeCol = new TableColumn<>("النوع");
        typeCol.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getEventType() == EventType.CHECK_IN ? "دخول" : "خروج"));
        TableColumn<AttendanceEvent, String> timeCol = new TableColumn<>("الوقت");
        timeCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getEventTime().format(DT_FMT)));
        TableColumn<AttendanceEvent, String> tgCol = new TableColumn<>("Telegram");
        tgCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().isTelegramSent() ? "✅" : "⏳"));
        TableColumn<AttendanceEvent, String> notesCol = new TableColumn<>("ملاحظات");
        notesCol.setCellValueFactory(new PropertyValueFactory<>("notes"));
        attendanceTable.getColumns().addAll(nameCol, typeCol, timeCol, tgCol, notesCol);
        attendanceTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        VBox box = new VBox(8, toolbar, attendanceTable);
        VBox.setVgrow(attendanceTable, javafx.scene.layout.Priority.ALWAYS);
        refreshAttendance();
        return box;
    }

    private VBox buildReportsTab() {
        DatePicker fromPicker = new DatePicker(LocalDate.now().withDayOfMonth(1));
        DatePicker toPicker = new DatePicker(LocalDate.now());
        Label resultLabel = new Label("");

        Button exportBtn = new Button("تصدير Excel ليوم محدد");
        exportBtn.setOnAction(e -> {
            try {
                Path file = context.excelExport().exportDailyReport(attendanceDatePicker != null
                        ? attendanceDatePicker.getValue() : LocalDate.now());
                resultLabel.setText("تم التصدير: " + file);
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(file.getParent().toFile());
                }
            } catch (Exception ex) {
                showError("فشل التصدير", ex.getMessage());
            }
        });

        Button backupBtn = new Button("نسخة احتياطية الآن");
        backupBtn.setOnAction(e -> {
            try {
                Path backup = context.backup().createBackup();
                resultLabel.setText("تم النسخ الاحتياطي: " + backup);
            } catch (Exception ex) {
                showError("فشل النسخ", ex.getMessage());
            }
        });

        Button pendingBtn = new Button("عرض غير المرسلة لـ Telegram");
        pendingBtn.setOnAction(e -> showPendingTelegram());

        VBox box = new VBox(12,
                new Label("تصدير تقارير الحضور"),
                exportBtn, backupBtn, pendingBtn, resultLabel
        );
        box.setPadding(new Insets(16));
        box.setAlignment(Pos.TOP_RIGHT);
        return box;
    }

    private VBox buildUpdatesTab() {
        Label currentVersion = new Label("النسخة الحالية: " + AppVersion.current());
        currentVersion.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        TextField manifestUrl = new TextField();
        manifestUrl.setPromptText("https://raw.githubusercontent.com/USER/REPO/main/updates/version.json");
        manifestUrl.getStyleClass().add("form-field");
        manifestUrl.setPrefWidth(500);

        TextField githubRepo = new TextField();
        githubRepo.setPromptText("USER/REPO  أو  github:USER/REPO");
        githubRepo.getStyleClass().add("form-field");

        PasswordField githubToken = new PasswordField();
        githubToken.setPromptText("للمستودعات الخاصة فقط");
        githubToken.getStyleClass().add("form-field");

        TextField checkInterval = new TextField();
        CheckBox autoCheck = new CheckBox("فحص التحديثات تلقائيًا");
        CheckBox autoInstall = new CheckBox("تثبيت التحديثات تلقائيًا (بدون سؤال)");

        Label statusLabel = new Label("");
        Label lastCheckLabel = new Label("");

        try {
            SettingsRepository s = context.settings();
            manifestUrl.setText(s.getOrDefault(SettingsRepository.UPDATE_MANIFEST_URL, ""));
            githubRepo.setText(s.getOrDefault(SettingsRepository.UPDATE_GITHUB_REPO, ""));
            githubToken.setText(s.getOrDefault(SettingsRepository.UPDATE_GITHUB_TOKEN, ""));
            checkInterval.setText(s.getOrDefault(SettingsRepository.UPDATE_CHECK_INTERVAL_HOURS, "6"));
            autoCheck.setSelected(s.getBoolean(SettingsRepository.AUTO_UPDATE_ENABLED, true));
            autoInstall.setSelected(s.getBoolean(SettingsRepository.AUTO_UPDATE_INSTALL, false));
            String last = s.getOrDefault(SettingsRepository.LAST_UPDATE_CHECK, "");
            lastCheckLabel.setText(last.isBlank() ? "آخر فحص: —" : "آخر فحص: " + last);
        } catch (Exception ignored) {
        }

        Button saveBtn = new Button("حفظ إعدادات التحديث");
        saveBtn.setOnAction(e -> {
            try {
                SettingsRepository s = context.settings();
                s.set(SettingsRepository.UPDATE_MANIFEST_URL, manifestUrl.getText().trim());
                s.set(SettingsRepository.UPDATE_GITHUB_REPO, githubRepo.getText().trim());
                s.set(SettingsRepository.UPDATE_GITHUB_TOKEN, githubToken.getText());
                s.set(SettingsRepository.UPDATE_CHECK_INTERVAL_HOURS, checkInterval.getText().trim());
                s.set(SettingsRepository.AUTO_UPDATE_ENABLED, String.valueOf(autoCheck.isSelected()));
                s.set(SettingsRepository.AUTO_UPDATE_INSTALL, String.valueOf(autoInstall.isSelected()));
                context.auditLogs().log(adminUsername, "UPDATE_SETTINGS", "updates", null, null, "update settings");
                statusLabel.setText("✅ تم الحفظ");
            } catch (Exception ex) {
                statusLabel.setText("❌ " + ex.getMessage());
            }
        });

        Button checkBtn = new Button("فحص التحديثات الآن");
        checkBtn.setOnAction(e -> {
            statusLabel.setText("جاري الفحص...");
            Thread t = new Thread(() -> {
                try {
                    UpdateService.UpdateCheckResult result = context.updates().checkForUpdates();
                    context.settings().set(SettingsRepository.LAST_UPDATE_CHECK,
                            java.time.LocalDateTime.now().format(DT_FMT));
                    javafx.application.Platform.runLater(() -> {
                        lastCheckLabel.setText("آخر فحص: " + java.time.LocalDateTime.now().format(DT_FMT));
                        if (result.updateAvailable()) {
                            statusLabel.setText("✅ " + result.message());
                            UpdateDialog.show(stage, context.updates(), result);
                        } else {
                            statusLabel.setText("ℹ️ " + result.message());
                        }
                    });
                } catch (Exception ex) {
                    javafx.application.Platform.runLater(() ->
                            statusLabel.setText("❌ " + ex.getMessage()));
                }
            }, "manual-update-check");
            t.setDaemon(true);
            t.start();
        });

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(8, 0, 8, 0));
        int row = 0;
        grid.add(new Label("رابط version.json (Git raw):"), 0, row); grid.add(manifestUrl, 1, row++);
        grid.add(new Label("أو GitHub repo:"), 0, row); grid.add(githubRepo, 1, row++);
        grid.add(new Label("GitHub Token (خاص):"), 0, row); grid.add(githubToken, 1, row++);
        grid.add(new Label("فحص كل (ساعة):"), 0, row); grid.add(checkInterval, 1, row++);
        grid.add(autoCheck, 1, row++);
        grid.add(autoInstall, 1, row++);

        Label hint = new Label("""
                الطريقة 1: ارفع updates/version.json إلى GitHub وضع الرابط raw هنا.
                الطريقة 2: اكتب USER/REPO لاستخدام GitHub Releases تلقائيًا.
                ⚠️ المستودع الخاص (private) يتطلب GitHub Token — بدونه يظهر HTTP 404.
                للتوزيع للعملاء: اجعل المستودع public أو أضف Token هنا.""");
        hint.setWrapText(true);
        hint.setStyle("-fx-text-fill: #64748b;");

        VBox box = new VBox(12, currentVersion, grid, hint, new HBox(8, saveBtn, checkBtn), statusLabel, lastCheckLabel);
        box.setPadding(new Insets(16));
        box.setAlignment(Pos.TOP_RIGHT);
        return box;
    }

    private VBox buildShiftsTab() {
        shiftTable = new TableView<>();
        TableColumn<WorkShift, String> nameCol = new TableColumn<>("الاسم");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        TableColumn<WorkShift, String> daysCol = new TableColumn<>("الأيام");
        daysCol.setCellValueFactory(cell -> new SimpleStringProperty(
                WorkShiftRepository.formatDays(cell.getValue().getDaysOfWeek())));
        TableColumn<WorkShift, String> startCol = new TableColumn<>("البداية");
        startCol.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getStartTime().format(DateTimeFormatter.ofPattern("HH:mm"))));
        TableColumn<WorkShift, String> endCol = new TableColumn<>("النهاية");
        endCol.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getEndTime() != null
                        ? cell.getValue().getEndTime().format(DateTimeFormatter.ofPattern("HH:mm")) : "—"));
        TableColumn<WorkShift, String> graceCol = new TableColumn<>("سماح (د)");
        graceCol.setCellValueFactory(cell -> new SimpleStringProperty(
                String.valueOf(cell.getValue().getLateGraceMinutes())));
        TableColumn<WorkShift, String> activeCol = new TableColumn<>("الحالة");
        activeCol.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().isActive() ? "مفعّل" : "معطّل"));
        shiftTable.getColumns().addAll(nameCol, daysCol, startCol, endCol, graceCol, activeCol);
        shiftTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        Button addBtn = new Button("إضافة شفت");
        addBtn.setOnAction(e -> showShiftDialog(null));
        Button editBtn = new Button("تعديل");
        editBtn.setOnAction(e -> {
            WorkShift selected = shiftTable.getSelectionModel().getSelectedItem();
            if (selected != null) showShiftDialog(selected);
        });
        Button deleteBtn = new Button("حذف");
        deleteBtn.setOnAction(e -> deleteShift());
        Button refreshBtn = new Button("تحديث");
        refreshBtn.setOnAction(e -> refreshShifts());

        Label hint = new Label("""
                حدّد شفتات متعددة بأوقات وأيام مختلفة. مثال: صباحي 08:00، مسائي 15:00، جمعة 09:00.
                الأيام: 1=إثنين … 7=أحد. يمكن تحديد أكثر من يوم لكل شفت.""");
        hint.setWrapText(true);
        hint.setStyle("-fx-text-fill: #64748b;");

        HBox toolbar = new HBox(8, addBtn, editBtn, deleteBtn, refreshBtn);
        toolbar.setAlignment(Pos.CENTER_RIGHT);

        VBox box = new VBox(8, toolbar, hint, shiftTable);
        VBox.setVgrow(shiftTable, javafx.scene.layout.Priority.ALWAYS);
        refreshShifts();
        return box;
    }

    private void refreshShifts() {
        try {
            shiftTable.setItems(FXCollections.observableArrayList(context.workShifts().findAll()));
        } catch (Exception e) {
            showError("خطأ", e.getMessage());
        }
    }

    private void deleteShift() {
        WorkShift selected = shiftTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        try {
            context.workShifts().delete(selected.getId());
            context.auditLogs().log(adminUsername, "DELETE", "work_shifts", selected.getId(), selected.getName(), null);
            refreshShifts();
        } catch (Exception e) {
            showError("خطأ", e.getMessage());
        }
    }

    private void showShiftDialog(WorkShift existing) {
        Stage dialog = new Stage();
        dialog.initOwner(stage);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(existing == null ? "إضافة شفت" : "تعديل شفت");

        TextField name = new TextField(existing != null ? existing.getName() : "");
        TextField start = new TextField(existing != null
                ? existing.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")) : "08:00");
        TextField end = new TextField(existing != null && existing.getEndTime() != null
                ? existing.getEndTime().format(DateTimeFormatter.ofPattern("HH:mm")) : "");
        TextField grace = new TextField(existing != null
                ? String.valueOf(existing.getLateGraceMinutes()) : "15");
        TextField early = new TextField(existing != null
                ? String.valueOf(existing.getEarlyCheckinMinutes()) : "60");
        TextField sortOrder = new TextField(existing != null
                ? String.valueOf(existing.getSortOrder()) : "0");
        CheckBox active = new CheckBox("مفعّل");
        active.setSelected(existing == null || existing.isActive());

        Map<DayOfWeek, CheckBox> dayBoxes = new HashMap<>();
        String[] dayLabels = {"إث", "ث", "ر", "خ", "ج", "س", "ح"};
        DayOfWeek[] allDays = DayOfWeek.values();
        HBox daysBox = new HBox(8);
        for (int i = 0; i < allDays.length; i++) {
            CheckBox cb = new CheckBox(dayLabels[i]);
            dayBoxes.put(allDays[i], cb);
            daysBox.getChildren().add(cb);
        }
        if (existing != null) {
            for (String d : existing.getDaysOfWeek().split(",")) {
                if (d.isBlank()) continue;
                DayOfWeek dow = DayOfWeek.of(Integer.parseInt(d.trim()));
                dayBoxes.get(dow).setSelected(true);
            }
        } else {
            dayBoxes.values().forEach(cb -> cb.setSelected(true));
        }

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));
        int row = 0;
        grid.add(new Label("اسم الشفت:"), 0, row); grid.add(name, 1, row++);
        grid.add(new Label("الأيام:"), 0, row); grid.add(daysBox, 1, row++);
        grid.add(new Label("وقت البداية (HH:mm):"), 0, row); grid.add(start, 1, row++);
        grid.add(new Label("وقت النهاية (اختياري):"), 0, row); grid.add(end, 1, row++);
        grid.add(new Label("سماح التأخير (دقيقة):"), 0, row); grid.add(grace, 1, row++);
        grid.add(new Label("دخول مبكر قبل (دقيقة):"), 0, row); grid.add(early, 1, row++);
        grid.add(new Label("الترتيب:"), 0, row); grid.add(sortOrder, 1, row++);
        grid.add(active, 1, row++);

        Button save = new Button("حفظ");
        save.setOnAction(e -> {
            try {
                String selectedDays = dayBoxes.entrySet().stream()
                        .filter(entry -> entry.getValue().isSelected())
                        .map(entry -> String.valueOf(entry.getKey().getValue()))
                        .sorted()
                        .reduce((a, b) -> a + "," + b)
                        .orElse("");
                if (selectedDays.isBlank()) {
                    showError("خطأ", "اختر يومًا واحدًا على الأقل");
                    return;
                }
                WorkShift shift = existing != null ? existing : new WorkShift();
                shift.setName(name.getText().trim());
                shift.setDaysOfWeek(selectedDays);
                shift.setStartTime(LocalTime.parse(start.getText().trim(), DateTimeFormatter.ofPattern("HH:mm")));
                shift.setEndTime(end.getText().isBlank() ? null
                        : LocalTime.parse(end.getText().trim(), DateTimeFormatter.ofPattern("HH:mm")));
                shift.setLateGraceMinutes(Integer.parseInt(grace.getText().trim()));
                shift.setEarlyCheckinMinutes(Integer.parseInt(early.getText().trim()));
                shift.setSortOrder(Integer.parseInt(sortOrder.getText().trim()));
                shift.setActive(active.isSelected());
                if (shift.getName().isBlank()) {
                    showError("خطأ", "اسم الشفت مطلوب");
                    return;
                }
                if (existing == null) {
                    context.workShifts().insert(shift);
                    context.auditLogs().log(adminUsername, "CREATE", "work_shifts", shift.getId(), null, shift.getName());
                } else {
                    context.workShifts().update(shift);
                    context.auditLogs().log(adminUsername, "UPDATE", "work_shifts", shift.getId(), null, shift.getName());
                }
                refreshShifts();
                dialog.close();
            } catch (Exception ex) {
                showError("خطأ", ex.getMessage());
            }
        });

        VBox root = new VBox(12, grid, save);
        root.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
        root.setPadding(new Insets(16));
        dialog.setScene(new Scene(root, 480, 420));
        dialog.showAndWait();
    }

    private VBox buildSettingsTab() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setAlignment(Pos.TOP_RIGHT);
        grid.setPadding(new Insets(16));

        TextField deviceName = field("device_name");
        TextField botToken = ltrField("bot_token");
        TextField cooldown = ltrField("cooldown");
        TextField photoDelay = ltrField("photo_delay");
        TextField cameraId = ltrField("camera_id");
        PasswordField newPin = new PasswordField();
        newPin.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        CheckBox autoMode = new CheckBox("الوضع التلقائي (دخول/خروج تلقائي)");
        CheckBox kioskMode = new CheckBox("وضع Kiosk (شاشة كاملة)");

        try {
            SettingsRepository s = context.settings();
            deviceName.setText(s.getOrDefault(SettingsRepository.DEVICE_NAME, ""));
            botToken.setText(s.getOrDefault(SettingsRepository.TELEGRAM_BOT_TOKEN, ""));
            cooldown.setText(s.getOrDefault(SettingsRepository.SCAN_COOLDOWN_SECONDS, "30"));
            photoDelay.setText(s.getOrDefault(SettingsRepository.PHOTO_DELAY_MS, "500"));
            cameraId.setText(s.getOrDefault(SettingsRepository.CAMERA_ID, "0"));
            autoMode.setSelected(s.getBoolean(SettingsRepository.AUTO_MODE, false));
            kioskMode.setSelected(s.getBoolean(SettingsRepository.KIOSK_MODE, false));
        } catch (Exception ignored) {
        }

        int row = 0;
        grid.add(new Label("اسم الجهاز:"), 0, row); grid.add(deviceName, 1, row++);
        grid.add(new Label("Telegram Bot Token:"), 0, row); grid.add(botToken, 1, row++);
        grid.add(new Label("مهلة منع التكرار (ث):"), 0, row); grid.add(cooldown, 1, row++);
        grid.add(new Label("تأخير التقاط الصورة (ms):"), 0, row); grid.add(photoDelay, 1, row++);
        grid.add(new Label("رقم الكاميرا:"), 0, row); grid.add(cameraId, 1, row++);
        grid.add(new Label("PIN جديد:"), 0, row); grid.add(newPin, 1, row++);
        grid.add(autoMode, 1, row++);
        grid.add(kioskMode, 1, row++);

        Button saveBtn = new Button("حفظ الإعدادات");
        Button testTgBtn = new Button("اختبار Telegram");
        Label statusLabel = new Label("");

        saveBtn.setOnAction(e -> {
            try {
                SettingsRepository s = context.settings();
                s.set(SettingsRepository.DEVICE_NAME, deviceName.getText());
                s.set(SettingsRepository.TELEGRAM_BOT_TOKEN, normalizeBotToken(botToken.getText()));
                s.set(SettingsRepository.SCAN_COOLDOWN_SECONDS, cooldown.getText());
                s.set(SettingsRepository.PHOTO_DELAY_MS, photoDelay.getText());
                s.set(SettingsRepository.CAMERA_ID, cameraId.getText());
                s.set(SettingsRepository.AUTO_MODE, String.valueOf(autoMode.isSelected()));
                s.set(SettingsRepository.KIOSK_MODE, String.valueOf(kioskMode.isSelected()));
                if (!newPin.getText().isBlank()) {
                    s.updatePin(newPin.getText());
                }
                context.attendanceService().reloadSettings();
                context.auditLogs().log(adminUsername, "UPDATE_SETTINGS", "settings", null, null, "settings updated");
                statusLabel.setText("✅ تم الحفظ");
            } catch (Exception ex) {
                statusLabel.setText("❌ " + ex.getMessage());
            }
        });

        testTgBtn.setOnAction(e -> {
            String token = normalizeBotToken(botToken.getText());
            botToken.setText(token);
            var result = context.telegram().testConnection(token);
            statusLabel.setText(result.success() ? "✅ " + result.message() : "❌ " + result.message());
        });

        HBox buttons = new HBox(8, saveBtn, testTgBtn);
        VBox box = new VBox(12, grid, buttons, statusLabel);
        box.setAlignment(Pos.TOP_RIGHT);
        return box;
    }

    private TextField field(String name) {
        TextField f = new TextField();
        f.getStyleClass().add("form-field");
        f.setUserData(name);
        return f;
    }

    /** حقول تقنية (Token, IDs) — LTR حتى لا ينعكس النص مع واجهة عربية */
    private TextField ltrField(String name) {
        TextField f = field(name);
        f.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        f.getStyleClass().add("ltr-field");
        f.setPrefWidth(420);
        return f;
    }

    private static String normalizeBotToken(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.trim().replace("::", ":");
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d{8,10}:[A-Za-z0-9_-]{20,})")
                .matcher(t);
        if (m.find()) {
            return m.group(1);
        }
        String[] parts = t.split(":");
        java.util.List<String> segs = new java.util.ArrayList<>();
        for (String p : parts) {
            if (!p.isBlank()) {
                segs.add(p.trim());
            }
        }
        if (segs.size() == 2) {
            if (segs.get(0).matches("\\d{8,10}")) {
                return segs.get(0) + ":" + segs.get(1);
            }
            if (segs.get(1).matches("\\d{8,10}")) {
                return segs.get(1) + ":" + segs.get(0);
            }
        }
        return t;
    }

    private VBox buildAuditTab() {
        auditTable = new TableView<>();
        TableColumn<AuditLog, String> adminCol = new TableColumn<>("المسؤول");
        adminCol.setCellValueFactory(new PropertyValueFactory<>("adminUsername"));
        TableColumn<AuditLog, String> actionCol = new TableColumn<>("الإجراء");
        actionCol.setCellValueFactory(new PropertyValueFactory<>("actionType"));
        TableColumn<AuditLog, String> tableCol = new TableColumn<>("الجدول");
        tableCol.setCellValueFactory(new PropertyValueFactory<>("targetTable"));
        TableColumn<AuditLog, String> timeCol = new TableColumn<>("الوقت");
        timeCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getActionTime().format(DT_FMT)));
        auditTable.getColumns().addAll(adminCol, actionCol, tableCol, timeCol);
        auditTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        Button refreshBtn = new Button("تحديث");
        refreshBtn.setOnAction(e -> refreshAudit());
        refreshAudit();
        return new VBox(8, refreshBtn, auditTable);
    }

    private void refreshEmployees() {
        try {
            employeeTable.setItems(FXCollections.observableArrayList(context.employees().findAll()));
        } catch (Exception e) {
            showError("خطأ", e.getMessage());
        }
    }

    private void refreshAttendance() {
        try {
            LocalDate date = attendanceDatePicker.getValue();
            attendanceTable.setItems(FXCollections.observableArrayList(context.attendance().findEventsForDate(date)));
        } catch (Exception e) {
            showError("خطأ", e.getMessage());
        }
    }

    private void refreshAudit() {
        try {
            auditTable.setItems(FXCollections.observableArrayList(context.auditLogs().findRecent(200)));
        } catch (Exception e) {
            showError("خطأ", e.getMessage());
        }
    }

    private void showEmployeeDialog(Employee existing) {
        Stage dialog = new Stage();
        dialog.initOwner(stage);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(existing == null ? "إضافة موظف" : "تعديل موظف");

        TextField code = new TextField(existing != null ? existing.getEmployeeCode() : "");
        TextField name = new TextField(existing != null ? existing.getFullName() : "");
        TextField dept = new TextField(existing != null ? existing.getDepartment() : "");
        TextField job = new TextField(existing != null ? existing.getJobTitle() : "");
        CheckBox active = new CheckBox("مفعّل");
        active.setSelected(existing == null || existing.isActive());

        VBox shiftsBox = new VBox(5);
        List<CheckBox> shiftChecks = new ArrayList<>();
        try {
            Set<Long> assigned = existing == null ? Set.of()
                    : context.employees().findAssignedShiftIds(existing.getId());
            for (WorkShift shift : context.workShifts().findAll()) {
                CheckBox check = new CheckBox(ShiftScheduleService.formatShiftSummary(shift));
                check.setUserData(shift.getId());
                check.setSelected(assigned.contains(shift.getId()));
                shiftChecks.add(check);
                shiftsBox.getChildren().add(check);
            }
        } catch (Exception ex) {
            showError("خطأ", ex.getMessage());
            return;
        }
        ScrollPane shiftsPane = new ScrollPane(shiftsBox);
        shiftsPane.setFitToWidth(true);
        shiftsPane.setPrefViewportHeight(130);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));
        grid.add(new Label("رمز الموظف:"), 0, 0); grid.add(code, 1, 0);
        grid.add(new Label("الاسم الكامل:"), 0, 1); grid.add(name, 1, 1);
        grid.add(new Label("القسم:"), 0, 2); grid.add(dept, 1, 2);
        grid.add(new Label("المسمى:"), 0, 3); grid.add(job, 1, 3);
        grid.add(new Label("الشفتات والأيام:"), 0, 4); grid.add(shiftsPane, 1, 4);
        grid.add(active, 1, 5);

        Button save = new Button("حفظ");
        save.setOnAction(e -> {
            try {
                Employee emp = existing != null ? existing : new Employee();
                emp.setEmployeeCode(code.getText().trim());
                emp.setFullName(name.getText().trim());
                emp.setDepartment(dept.getText().trim());
                emp.setJobTitle(job.getText().trim());
                emp.setActive(active.isSelected());
                if (emp.getEmployeeCode().isBlank() || emp.getFullName().isBlank()) {
                    showError("خطأ", "الرمز والاسم مطلوبان");
                    return;
                }
                if (existing == null) {
                    context.employees().insert(emp);
                    context.auditLogs().log(adminUsername, "CREATE", "employees", emp.getId(), null, emp.getFullName());
                } else {
                    context.employees().update(emp);
                    context.auditLogs().log(adminUsername, "UPDATE", "employees", emp.getId(), null, emp.getFullName());
                }
                Set<Long> selectedShiftIds = new HashSet<>();
                for (CheckBox check : shiftChecks) {
                    if (check.isSelected()) selectedShiftIds.add((Long) check.getUserData());
                }
                context.employees().replaceAssignedShifts(emp.getId(), selectedShiftIds);
                refreshEmployees();
                dialog.close();
            } catch (Exception ex) {
                showError("خطأ", ex.getMessage());
            }
        });

        VBox root = new VBox(12, grid, save);
        root.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(16));
        dialog.setScene(new Scene(root, 620, 500));
        dialog.showAndWait();
    }

    private void deactivateEmployee() {
        Employee selected = employeeTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        try {
            context.employees().deactivate(selected.getId());
            context.auditLogs().log(adminUsername, "DEACTIVATE", "employees", selected.getId(), "active", "inactive");
            refreshEmployees();
        } catch (Exception e) {
            showError("خطأ", e.getMessage());
        }
    }

    private void generateQrForSelected() {
        Employee selected = employeeTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        try {
            var qrImage = context.photos().generateQrImage(selected.getQrToken(), 300);
            Path path = context.photos().saveQrCard(qrImage, selected.getEmployeeCode());

            Stage qrStage = new Stage();
            qrStage.initOwner(stage);
            qrStage.setTitle("QR - " + selected.getFullName());
            ImageView iv = new ImageView(new Image(path.toUri().toString()));
            Button printBtn = new Button("فتح الملف");
            printBtn.setOnAction(e -> {
                try {
                    Desktop.getDesktop().open(path.toFile());
                } catch (Exception ex) {
                    showError("خطأ", ex.getMessage());
                }
            });
            VBox box = new VBox(12, iv, printBtn, new Label(selected.getFullName()), new Label(selected.getQrToken()));
            box.setAlignment(Pos.CENTER);
            box.setPadding(new Insets(16));
            qrStage.setScene(new Scene(box));
            qrStage.show();
        } catch (Exception e) {
            showError("خطأ", e.getMessage());
        }
    }

    private void viewSelectedPhoto() {
        AttendanceEvent event = attendanceTable.getSelectionModel().getSelectedItem();
        if (event == null || event.getPhotoPath() == null) return;
        try {
            Path path = Path.of(event.getPhotoPath());
            if (!path.toFile().exists()) {
                showError("خطأ", "الصورة غير موجودة");
                return;
            }
            Stage photoStage = new Stage();
            photoStage.initOwner(stage);
            photoStage.setTitle(event.getEmployeeName() + " - " + event.getEventTime().format(DT_FMT));
            ImageView iv = new ImageView(new Image(path.toUri().toString(), 640, 480, true, true));
            photoStage.setScene(new Scene(new VBox(iv)));
            photoStage.show();
        } catch (Exception e) {
            showError("خطأ", e.getMessage());
        }
    }

    private void editSelectedEvent() {
        AttendanceEvent event = attendanceTable.getSelectionModel().getSelectedItem();
        if (event == null) return;

        Stage dialog = new Stage();
        dialog.initOwner(stage);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("تعديل سجل");

        ComboBox<String> typeBox = new ComboBox<>(FXCollections.observableArrayList("CHECK_IN", "CHECK_OUT"));
        typeBox.setValue(event.getEventType().name());
        TextField timeField = new TextField(event.getEventTime().format(DT_FMT));
        TextArea notes = new TextArea(event.getNotes() != null ? event.getNotes() : "");

        Button save = new Button("حفظ");
        save.setOnAction(e -> {
            try {
                String old = event.getEventType() + " @ " + event.getEventTime();
                event.setEventType(EventType.fromString(typeBox.getValue()));
                event.setEventTime(LocalDateTime.parse(timeField.getText(), DT_FMT));
                event.setNotes(notes.getText());
                event.setCreatedBy(adminUsername);
                context.attendance().updateEvent(event);
                context.auditLogs().log(adminUsername, "UPDATE", "attendance_events", event.getId(), old,
                        event.getEventType() + " @ " + event.getEventTime());
                refreshAttendance();
                dialog.close();
            } catch (Exception ex) {
                showError("خطأ", ex.getMessage());
            }
        });

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));
        grid.add(new Label("النوع:"), 0, 0); grid.add(typeBox, 1, 0);
        grid.add(new Label("الوقت (yyyy-MM-dd HH:mm):"), 0, 1); grid.add(timeField, 1, 1);
        grid.add(new Label("ملاحظات:"), 0, 2); grid.add(notes, 1, 2);

        dialog.setScene(new Scene(new VBox(12, grid, save), 400, 250));
        dialog.showAndWait();
    }

    private void showAbsentEmployees() {
        try {
            LocalDate date = attendanceDatePicker.getValue();
            List<Employee> active = context.employees().findActive();
            List<WorkShift> shifts = context.shiftSchedule().getShiftsSorted(date);

            Map<Long, List<LocalDateTime>> checkInsByEmployee = new HashMap<>();
            for (AttendanceEvent e : context.attendance().findEventsForDate(date)) {
                if (e.getEventType() == EventType.CHECK_IN) {
                    checkInsByEmployee.computeIfAbsent(e.getEmployeeId(), k -> new ArrayList<>())
                            .add(e.getEventTime());
                }
            }

            StringBuilder sb = new StringBuilder();
            if (shifts.isEmpty()) {
                sb.append("لا توجد شفتات مجدولة لهذا اليوم.");
            } else {
                for (WorkShift shift : shifts) {
                    sb.append("【").append(shift.getName()).append("】\n");
                    boolean any = false;
                    for (Employee e : active) {
                        boolean assignedToday = context.shiftSchedule().getShiftsForEmployee(e.getId(), date)
                                .stream().anyMatch(s -> s.getId() == shift.getId());
                        if (!assignedToday) continue;
                        List<LocalDateTime> checkIns = checkInsByEmployee.getOrDefault(e.getId(), List.of());
                        if (!context.shiftSchedule().wasPresentInShift(date, shift, checkIns)) {
                            sb.append("• ").append(e.getFullName()).append("\n");
                            any = true;
                        }
                    }
                    if (!any) {
                        sb.append("  (لا يوجد غائبون)\n");
                    }
                    sb.append("\n");
                }
            }
            showInfo("غياب اليوم", sb.toString());
        } catch (Exception e) {
            showError("خطأ", e.getMessage());
        }
    }

    private void showLateEmployees() {
        try {
            LocalDate date = attendanceDatePicker.getValue();
            ShiftScheduleService schedule = context.shiftSchedule();
            StringBuilder sb = new StringBuilder("المتأخرون حسب الشفت:\n\n");
            boolean anyLate = false;
            for (AttendanceEvent e : context.attendance().findEventsForDate(date)) {
                if (e.getEventType() != EventType.CHECK_IN) {
                    continue;
                }
                ShiftScheduleService.ShiftEvaluation eval = schedule.evaluateCheckIn(e.getEmployeeId(), e.getEventTime());
                if (eval.late()) {
                    anyLate = true;
                    sb.append("• ").append(e.getEmployeeName())
                            .append(" — ").append(eval.shift().getName())
                            .append(" — ").append(e.getEventTime().format(TIME_FMT))
                            .append(" (+").append(eval.lateMinutes()).append(" د)\n");
                }
            }
            if (!anyLate) {
                sb.append("لا يوجد متأخرون");
            }
            showInfo("المتأخرون", sb.toString());
        } catch (Exception e) {
            showError("خطأ", e.getMessage());
        }
    }

    private void showPendingTelegram() {
        try {
            List<AttendanceEvent> pending = context.attendance().findPendingTelegramEvents();
            StringBuilder sb = new StringBuilder("عمليات غير مرسلة: " + pending.size() + "\n");
            for (AttendanceEvent e : pending) {
                sb.append("• ").append(e.getEmployeeName()).append(" - ")
                        .append(e.getEventTime().format(DT_FMT)).append("\n");
            }
            showInfo("Telegram", sb.toString());
        } catch (Exception e) {
            showError("خطأ", e.getMessage());
        }
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    public void show() {
        stage.show();
    }
}

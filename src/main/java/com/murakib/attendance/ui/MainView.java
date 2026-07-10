package com.murakib.attendance.ui;

import com.murakib.attendance.ApplicationContext;
import com.murakib.attendance.model.EventType;
import com.murakib.attendance.repository.SettingsRepository;
import com.murakib.attendance.service.AttendanceService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

public class MainView {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("hh:mm a", Locale.forLanguageTag("ar"));

    private final ApplicationContext context;
    private final Stage stage;
    private final BorderPane root = new BorderPane();

    private final ImageView cameraView = new ImageView();
    private final Label instructionLabel = new Label("ضع بطاقة QR أمام الكاميرا");
    private final Label lastEventLabel = new Label("آخر عملية: —");
    private final Label messageLabel = new Label("");
    private final Label cameraStatusLabel = new Label("الكاميرا: —");
    private final Label networkStatusLabel = new Label("الإنترنت: —");
    private final Label telegramStatusLabel = new Label("Telegram: —");

    private final AtomicReference<BufferedImage> latestFrame = new AtomicReference<>();
    private volatile boolean processing = false;
    private volatile String lastScannedToken = null;
    private long lastScanAttemptMs = 0;

    public MainView(ApplicationContext context, Stage stage) {
        this.context = context;
        this.stage = stage;
        buildUi();
    }

    private void buildUi() {
        root.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
        root.getStyleClass().add("root-pane");

        Label title = new Label("نظام تسجيل الحضور");
        title.getStyleClass().add("title-label");

        cameraView.setFitWidth(640);
        cameraView.setFitHeight(480);
        cameraView.setPreserveRatio(true);
        cameraView.getStyleClass().add("camera-view");

        StackPane cameraPane = new StackPane(cameraView);
        cameraPane.setPrefSize(640, 480);

        ToggleButton checkInBtn = new ToggleButton("تسجيل دخول");
        checkInBtn.getStyleClass().add("btn-checkin");
        ToggleButton checkOutBtn = new ToggleButton("تسجيل خروج");
        checkOutBtn.getStyleClass().add("btn-checkout");

        ToggleGroup modeGroup = new ToggleGroup();
        checkInBtn.setToggleGroup(modeGroup);
        checkOutBtn.setToggleGroup(modeGroup);
        checkInBtn.setSelected(true);

        checkInBtn.setOnAction(e -> context.attendanceService().setManualMode(EventType.CHECK_IN));
        checkOutBtn.setOnAction(e -> context.attendanceService().setManualMode(EventType.CHECK_OUT));

        HBox modeBox = new HBox(16, checkInBtn, checkOutBtn);
        modeBox.setAlignment(Pos.CENTER);

        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(640);
        messageLabel.setAlignment(Pos.CENTER);

        instructionLabel.getStyleClass().add("subtitle-label");
        lastEventLabel.getStyleClass().add("subtitle-label");

        Button adminBtn = new Button("لوحة الإدارة");
        adminBtn.getStyleClass().add("btn-admin");
        adminBtn.setOnAction(e -> openAdminPanel());

        VBox center = new VBox(16, title, cameraPane, instructionLabel, modeBox, messageLabel, lastEventLabel);
        center.setAlignment(Pos.TOP_CENTER);
        center.setPadding(new Insets(10));

        HBox statusBar = buildStatusBar();
        BorderPane.setMargin(adminBtn, new Insets(0, 0, 0, 10));

        HBox topBar = new HBox(adminBtn);
        topBar.setAlignment(Pos.CENTER_LEFT);
        root.setTop(topBar);
        root.setCenter(center);
        root.setBottom(statusBar);
    }

    private HBox buildStatusBar() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox(20, cameraStatusLabel, networkStatusLabel, telegramStatusLabel, spacer);
        bar.getStyleClass().add("status-bar");
        bar.setAlignment(Pos.CENTER_RIGHT);
        return bar;
    }

    public void initialize() throws Exception {
        boolean autoMode = context.settings().getBoolean(SettingsRepository.AUTO_MODE, false);
        if (autoMode) {
            context.attendanceService().reloadSettings();
        }

        context.network().start(online -> Platform.runLater(() ->
                networkStatusLabel.setText("الإنترنت: " + (online ? "متصل ✅" : "غير متصل ❌"))
        ));

        updateLastEvent();
        startCamera();
    }

    private void startCamera() {
        try {
            context.camera().start(frame -> {
                latestFrame.set(frame);
                Platform.runLater(() -> {
                    Image fxImage = context.camera().toFxImage(frame);
                    if (fxImage != null) {
                        cameraView.setImage(fxImage);
                    }
                    cameraStatusLabel.setText("الكاميرا: متصلة ✅");
                });
                scanFrame(frame);
            });
        } catch (Exception e) {
            cameraStatusLabel.setText("الكاميرا: غير متصلة ❌");
            showMessage(e.getMessage(), false);
        }
    }

    private void scanFrame(BufferedImage frame) {
        if (processing) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastScanAttemptMs < 300) {
            return;
        }
        lastScanAttemptMs = now;

        context.qr().decode(frame).ifPresent(token -> {
            if (token.equals(lastScannedToken) && now - lastScanAttemptMs < 2000) {
                return;
            }
            processing = true;
            lastScannedToken = token;
            Platform.runLater(() -> processScan(token));
        });
    }

    private void processScan(String token) {
        try {
            BufferedImage frame = latestFrame.get();
            AttendanceService.ScanResult result = context.attendanceService().processQrScan(token, frame);
            if (result.success()) {
                showMessage(result.message(), true);
                Toolkit.getDefaultToolkit().beep();
                updateLastEvent();
                if (result.employee() != null && result.employee().getProfilePhotoPath() != null) {
                    instructionLabel.setText("مرحبًا " + result.employee().getFullName());
                }
            } else {
                showMessage(result.message(), false);
                Toolkit.getDefaultToolkit().beep();
            }
            updateTelegramStatus();
        } catch (Exception e) {
            showMessage("خطأ: " + e.getMessage(), false);
        } finally {
            processing = false;
        }
    }

    private void updateLastEvent() {
        try {
            context.attendanceService().getLatestEvent().ifPresentOrElse(event -> {
                String type = event.getEventType() == EventType.CHECK_IN ? "دخول" : "خروج";
                lastEventLabel.setText("آخر عملية: " + event.getEmployeeName() + " - " + type + " "
                        + event.getEventTime().format(TIME_FMT));
            }, () -> lastEventLabel.setText("آخر عملية: —"));
        } catch (Exception ignored) {
        }
    }

    private void updateTelegramStatus() {
        boolean connected = context.telegram().isConnected();
        boolean configured = false;
        try {
            configured = !context.settings().getOrDefault(SettingsRepository.TELEGRAM_BOT_TOKEN, "").isBlank();
        } catch (Exception ignored) {
        }
        if (!configured) {
            telegramStatusLabel.setText("Telegram: غير مُعد ⚠️");
        } else {
            telegramStatusLabel.setText("Telegram: " + (connected ? "متصل ✅" : "في الانتظار ⏳"));
        }
    }

    private void showMessage(String text, boolean success) {
        messageLabel.setText(text);
        messageLabel.getStyleClass().removeAll("message-success", "message-error");
        messageLabel.getStyleClass().add(success ? "message-success" : "message-error");
    }

    private void openAdminPanel() {
        AdminLoginDialog login = new AdminLoginDialog(context);
        login.showAndWait().ifPresent(admin -> {
            AdminPanelView adminPanel = new AdminPanelView(context, stage, admin);
            adminPanel.show();
        });
    }

    public BorderPane getRoot() {
        return root;
    }
}

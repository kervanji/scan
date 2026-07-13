package com.murakib.attendance;

import com.murakib.attendance.repository.SettingsRepository;
import com.murakib.attendance.ui.MainView;
import com.murakib.attendance.ui.UpdateDialog;
import com.murakib.attendance.config.AppVersion;
import com.murakib.attendance.service.UpdateService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class Main extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        ApplicationContext context = ApplicationContext.getInstance();
        MainView mainView = new MainView(context, stage);

        boolean kiosk = context.settings().getBoolean(
                com.murakib.attendance.repository.SettingsRepository.KIOSK_MODE, false
        );

        Scene scene = new Scene(mainView.getRoot(), MainView.WINDOW_WIDTH, MainView.WINDOW_HEIGHT);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());

        stage.setTitle(AppVersion.appName() + " v" + AppVersion.current());
        stage.setScene(scene);
        if (!kiosk) {
            stage.setMinWidth(MainView.WINDOW_WIDTH);
            stage.setMinHeight(MainView.WINDOW_HEIGHT);
            stage.setWidth(MainView.WINDOW_WIDTH);
            stage.setHeight(MainView.WINDOW_HEIGHT);
        }
        if (kiosk) {
            stage.initStyle(StageStyle.UNDECORATED);
            stage.setFullScreen(true);
            stage.setFullScreenExitHint("");
        }
        stage.setOnCloseRequest(e -> {
            if (kiosk) {
                e.consume();
            } else {
                context.shutdown();
            }
        });
        stage.show();
        mainView.initialize();
        setupAutoUpdate(stage, context);
    }

    private void setupAutoUpdate(Stage stage, ApplicationContext context) {
        UpdateService updates = context.updates();
        updates.setOnUpdateAvailable(result -> Platform.runLater(() -> {
            try {
                boolean autoInstall = context.settings().getBoolean(SettingsRepository.AUTO_UPDATE_INSTALL, false);
                if (autoInstall || result.manifest().isMandatory()) {
                    UpdateDialog.installNow(stage, updates, result);
                } else {
                    UpdateDialog.show(stage, updates, result);
                }
            } catch (Exception e) {
                UpdateDialog.show(stage, updates, result);
            }
        }));
        updates.startBackgroundChecks();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

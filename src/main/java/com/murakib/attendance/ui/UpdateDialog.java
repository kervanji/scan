package com.murakib.attendance.ui;

import com.murakib.attendance.service.UpdateService;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.Optional;

public final class UpdateDialog {

    private UpdateDialog() {
    }

    public static void show(Stage owner, UpdateService updateService, UpdateService.UpdateCheckResult result) {
        if (result.manifest().isMandatory()) {
            runInstall(owner, updateService, result);
            return;
        }
        showConfirmation(owner, updateService, result);
    }

    public static void installNow(Stage owner, UpdateService updateService, UpdateService.UpdateCheckResult result) {
        runInstall(owner, updateService, result);
    }

    private static void showConfirmation(Stage owner, UpdateService updateService, UpdateService.UpdateCheckResult result) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(owner);
        alert.setTitle("تحديث متوفر");
        alert.setHeaderText("النسخة " + result.manifest().getVersion() + " متوفرة");
        String notes = result.manifest().getReleaseNotes();
        alert.setContentText((notes != null && !notes.isBlank() ? notes + "\n\n" : "")
                + "النسخة الحالية: " + result.currentVersion());

        if (result.manifest().isMandatory()) {
            alert.getButtonTypes().setAll(new ButtonType("تحديث الآن", ButtonType.OK.getButtonData()));
        } else {
            alert.getButtonTypes().setAll(
                    new ButtonType("تحديث الآن", ButtonType.OK.getButtonData()),
                    new ButtonType("لاحقًا", ButtonType.CANCEL.getButtonData())
            );
        }

        Optional<ButtonType> choice = alert.showAndWait();
        if (choice.isPresent() && choice.get().getText().equals("تحديث الآن")) {
            runInstall(owner, updateService, result);
        }
    }

    private static void runInstall(Stage owner, UpdateService updateService, UpdateService.UpdateCheckResult result) {
        Stage progressStage = new Stage();
        progressStage.initOwner(owner);
        progressStage.initModality(Modality.APPLICATION_MODAL);
        progressStage.setTitle("جاري التحديث");

        Label status = new Label("جاري التحميل...");
        ProgressIndicator spinner = new ProgressIndicator();
        VBox box = new VBox(16, spinner, status);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(24));
        box.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
        progressStage.setScene(new javafx.scene.Scene(box, 360, 160));
        progressStage.show();

        Thread worker = new Thread(() -> {
            try {
                String message = updateService.installUpdate(result.manifest(), text ->
                        javafx.application.Platform.runLater(() -> status.setText(text)));
                javafx.application.Platform.runLater(() -> {
                    status.setText(message);
                    spinner.setVisible(false);
                    Button close = new Button("إغلاق");
                    close.setOnAction(e -> {
                        progressStage.close();
                        javafx.application.Platform.exit();
                    });
                    box.getChildren().add(close);
                });
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> {
                    progressStage.close();
                    Alert err = new Alert(Alert.AlertType.ERROR, e.getMessage());
                    err.initOwner(owner);
                    err.showAndWait();
                });
            }
        }, "update-installer");
        worker.setDaemon(true);
        worker.start();
    }
}

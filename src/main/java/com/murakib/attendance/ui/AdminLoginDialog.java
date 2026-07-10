package com.murakib.attendance.ui;

import com.murakib.attendance.ApplicationContext;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.Optional;

public class AdminLoginDialog {

    private final ApplicationContext context;
    private final Stage dialog = new Stage();
    private String authenticatedUser;

    public AdminLoginDialog(ApplicationContext context) {
        this.context = context;
        build();
    }

    private void build() {
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.UTILITY);
        dialog.setTitle("دخول الإدارة");
        dialog.setResizable(false);

        Label title = new Label("أدخل رمز PIN للإدارة");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        PasswordField pinField = new PasswordField();
        pinField.setPromptText("PIN");
        pinField.setMaxWidth(200);

        Label errorLabel = new Label("");
        errorLabel.setStyle("-fx-text-fill: #dc2626;");

        Button loginBtn = new Button("دخول");
        loginBtn.setDefaultButton(true);
        loginBtn.setOnAction(e -> attemptLogin(pinField, errorLabel));

        pinField.setOnAction(e -> attemptLogin(pinField, errorLabel));

        VBox root = new VBox(12, title, pinField, errorLabel, loginBtn);
        root.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(24));

        dialog.setScene(new javafx.scene.Scene(root, 320, 180));
    }

    private void attemptLogin(PasswordField pinField, Label errorLabel) {
        try {
            if (context.settings().verifyPin(pinField.getText())) {
                authenticatedUser = "admin";
                dialog.close();
            } else {
                errorLabel.setText("رمز PIN غير صحيح");
                pinField.clear();
            }
        } catch (Exception e) {
            errorLabel.setText("خطأ: " + e.getMessage());
        }
    }

    public Optional<String> showAndWait() {
        dialog.showAndWait();
        return authenticatedUser != null ? Optional.of(authenticatedUser) : Optional.empty();
    }
}

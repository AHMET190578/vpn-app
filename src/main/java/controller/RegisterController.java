package controller;

import app.App;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import service.AuthService;

public class RegisterController {

    @FXML private TextField usernameField;
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;

    private final AuthService authService = new AuthService();

    @FXML
    private void handleRegister() {
        String username = usernameField.getText().trim();
        String email = emailField.getText().trim();
        String password = passwordField.getText().trim();

        if(username.isEmpty() || email.isEmpty() || password.isEmpty() ) {
            showError("Lütfen tüm alanları doldurun.");
            return;
        }

        boolean success = authService.register(username, email, password);

        if(success)  {
            showError("");
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Başarılı");
            alert.setHeaderText(null);
            alert.setContentText("Kayıt başarılı! Şimdi giriş yapabilirsiniz.");
            alert.showAndWait();

            handleGoBack();
        } else {
            showError("Bu kullanıcı adı veya e-posta zaten kullanılıyor!");
        }
    }

    @FXML
    private void handleGoBack() {
        try {
            Stage stage = (Stage) usernameField.getScene().getWindow();
            App.showLoginScreen(stage);
        } catch (Exception e) {
            e.printStackTrace();
            showError("Giriş ekranına dönerken hata oluştu.");
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(!message.isEmpty());
    }
}
package controller;

import app.App;
import javafx.fxml.FXML;

import javafx.scene.control.*;
import javafx.stage.Stage;
import model.User;
import service.AuthService;

public class LoginController {
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;

    private final AuthService authService = new AuthService();

    @FXML
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Lütfen tüm alanları doldurun.");
            return;
        }

        User user = authService.login(username, password);

        if (user != null) {
            showError("");
            System.out.println("welcome " + user.getUsername());

            try {
                openMainApp(user);
            } catch (Exception e) {
                e.printStackTrace();
                showError("Ana ekran açılırken hata oluştu!");
            }
        } else {
            showError("Kullanıcı adı veya şifre hatalı!");
        }
    }

    @FXML
    private void handleRegister() {
        try {
            Stage stage = (Stage) usernameField.getScene().getWindow();
            // App sınıfındaki statik metodu çağırarak Kayıt ekranına geçiş yap
            App.showRegisterScreen(stage);
        } catch (Exception e) {
            e.printStackTrace();
            showError("Kayıt ekranı yüklenirken hata oluştu.");
        }
    }


    private void openMainApp(User user) throws Exception {
        Stage stage = (Stage) usernameField.getScene().getWindow();
        // MainApp'i App üzerinden yüklüyoruz
        App.showMainAppScreen(stage, user);
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(!message.isEmpty());
    }
}

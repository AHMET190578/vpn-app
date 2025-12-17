package app;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import database.MongoDBConnection;

public class App extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        showMainAppScreen(primaryStage);
    }

    @Override
    public void stop() {

        MongoDBConnection.close();
    }

    public static void main(String[] args) {
        launch(args);
    }

    public static void showLoginScreen(Stage stage) {
        try {
            Parent root = FXMLLoader.load(App.class.getResource("/login.fxml"));
            stage.setTitle("Giriş Yap");
            stage.setScene(new Scene(root));
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Login ekranı yüklenemedi: " + e.getMessage());
        }
    }

    public static void showRegisterScreen(Stage stage) {
        try {
            Parent root = FXMLLoader.load(App.class.getResource("/RegisterView.fxml"));
            stage.setTitle("Yeni Hesap Oluştur");
            stage.setScene(new Scene(root));
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Kayıt ekranı yüklenemedi: " + e.getMessage());
        }
    }

    public static void showMainAppScreen(Stage stage) {

        MainApp mainAppInstance = new MainApp();
        mainAppInstance.start(stage);
    }

}
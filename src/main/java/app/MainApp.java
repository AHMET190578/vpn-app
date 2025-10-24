package app;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import model.User;
import util.ConfigLoader;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.TimeUnit;

public class MainApp extends Application {

    private Label statusLabel;
    private Button connectButton;
    private TextArea logArea;
    private Process vpnProcess;
    private boolean isConnected = false;
    private Path tempConfigPath;

    private User currentUser;


    private static final String CONFIG_FILE_NAME = "a.ovpn";

    public MainApp() {

    }

    public MainApp(User user) {
        this.currentUser = user;
    }

    @Override
    public void start(Stage primaryStage) {
        if (currentUser == null) {
            System.err.println("HATA: MainApp doğrudan başlatılamaz! App.showMainAppScreen() kullanın.");
            return;
        }

        primaryStage.setTitle("VPN App (OpenVPN 3) - Hoşgeldin " + currentUser.getUsername());

        statusLabel = new Label("Bağlantı kesildi.");
        statusLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        connectButton = new Button("BAĞLAN");
        connectButton.setPrefWidth(200);
        connectButton.setPrefHeight(40);
        connectButton.setStyle("-fx-font-size: 14px;");

        connectButton.setOnAction(event -> {
            if (isConnected) stopVpn();
            else startVpn();
        });

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(250);
        logArea.setWrapText(true);
        logArea.setStyle("-fx-font-family: monospace; -fx-font-size: 10px;");

        VBox layout = new VBox(15);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(20));
        layout.getChildren().addAll(
                statusLabel,
                connectButton,
                new Label("VPN Logları:"),
                logArea
        );

        Scene scene = new Scene(layout, 600, 500);
        primaryStage.setScene(scene);
        primaryStage.show();

        primaryStage.setOnCloseRequest(event -> {
            if (isConnected) stopVpn();
            if (vpnProcess != null) vpnProcess.destroyForcibly();
        });
    }

    private void appendLog(String message) {
        Platform.runLater(() -> logArea.appendText(message + "\n"));
    }

    private void startVpn() {
        new Thread(() -> {
            Path tempDir = null;
            try {
                tempDir = Files.createTempDirectory("myvpnapp_openvpn3");
                Path authPath = tempDir.resolve("auth.txt");
                tempConfigPath = tempDir.resolve(CONFIG_FILE_NAME);


                String username = ConfigLoader.getProperty("username");
                String password = ConfigLoader.getProperty("password");
                String authContent = username + "\n" + password + "\n";
                Files.writeString(authPath, authContent,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.SYNC);
                Files.setPosixFilePermissions(authPath, PosixFilePermissions.fromString("rw-------"));
                appendLog(" Auth dosyası oluşturuldu: " + authPath.toAbsolutePath());



                String originalConfigContent;
                try (InputStream configStream = getClass().getResourceAsStream("/" + CONFIG_FILE_NAME)) {
                    if (configStream == null) throw new Exception(CONFIG_FILE_NAME + " dosyası bulunamadı!");
                    originalConfigContent = new String(configStream.readAllBytes(), StandardCharsets.UTF_8);
                }

                String modifiedConfigContent;
                String authPathString = authPath.toAbsolutePath().toString();


                String authUserPassRegex = "(?m)^([\\s#]*)auth-user-pass.*$";
                String newAuthLine = "$1auth-user-pass " + authPathString;

                if (originalConfigContent.matches(authUserPassRegex.replace(".*", "[\\s]*$"))) {
                    modifiedConfigContent = originalConfigContent.replaceAll(authUserPassRegex.replace(".*", "[\\s]*$"), newAuthLine);
                    appendLog("'auth-user-pass' direktifi 'auth.txt' dosyasını gösterecek şekilde güncellendi.");
                } else if (originalConfigContent.matches(authUserPassRegex)) {
                    modifiedConfigContent = originalConfigContent.replaceAll(authUserPassRegex, newAuthLine);
                    appendLog("Mevcut 'auth-user-pass' direktifi 'auth.txt' dosyasını gösterecek şekilde güncellendi.");
                } else {
                    modifiedConfigContent = originalConfigContent + "\nauth-user-pass " + authPathString + "\n";
                    appendLog("'auth-user-pass' direktifi config sonuna eklendi.");
                }


                String verbRegex = "(?m)^([\\s#]*)verb.*$";
                String newVerbLine = "$1verb 4";

                if (modifiedConfigContent.matches(verbRegex)) {
                    modifiedConfigContent = modifiedConfigContent.replaceAll(verbRegex, newVerbLine);
                    appendLog(" Mevcut 'verb' direktifi 'verb 4' olarak güncellendi.");
                } else {
                    modifiedConfigContent = modifiedConfigContent + "\nverb 4\n";
                    appendLog("'verb 4' direktifi config sonuna eklendi.");
                }



                Files.writeString(tempConfigPath, modifiedConfigContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                appendLog(" Modifiye edilmiş config dosyası oluşturuldu: " + tempConfigPath.toAbsolutePath());



                Platform.runLater(() -> {
                    statusLabel.setText(" Bağlanıyor...");
                    statusLabel.setStyle("-fx-text-fill: orange; -fx-font-size: 16px; -fx-font-weight: bold;");
                    connectButton.setDisable(true);
                    logArea.clear();
                });

                appendLog("OpenVPN 3 başlatılıyor...");


                ProcessBuilder pb = new ProcessBuilder(
                        "openvpn3",
                        "session-start",
                        "--config", tempConfigPath.toAbsolutePath().toString()

                );


                pb.directory(tempDir.toFile());
                pb.redirectErrorStream(true);
                appendLog("💻 Komut: " + String.join(" ", pb.command()));

                vpnProcess = pb.start();
                appendLog("✓ Process başlatıldı (PID: " + vpnProcess.pid() + ")");

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(vpnProcess.getInputStream(), StandardCharsets.UTF_8)
                );

                String line;
                boolean connectionSuccess = false;

                while ((line = reader.readLine()) != null) {
                    appendLog(line);

                    if (line.contains("Initialization Sequence Completed")) {
                        connectionSuccess = true;
                        isConnected = true;
                        Platform.runLater(() -> {
                            statusLabel.setText(" Bağlantı başarılı!");
                            statusLabel.setStyle("-fx-text-fill: green; -fx-font-size: 16px; -fx-font-weight: bold;");
                            connectButton.setText("BAĞLANTIYI KES");
                            connectButton.setDisable(false);
                        });
                    }

                    if (line.contains("AUTH_FAILED")) {
                        appendLog(" Kimlik doğrulama hatası!");
                        Platform.runLater(() -> {
                            statusLabel.setText("Kimlik doğrulama hatası!");
                            statusLabel.setStyle("-fx-text-fill: red; -fx-font-size: 16px; -fx-font-weight: bold;");
                            connectButton.setText("BAĞLAN");
                            connectButton.setDisable(false);
                        });
                        break;
                    }

                    if (line.contains("Connection refused") || line.contains("TLS Error") || line.contains("Unreachable")) {
                        appendLog("Bağlantı hatası!");
                        Platform.runLater(() -> {
                            statusLabel.setText("Sunucuya bağlanılamadı!");
                            statusLabel.setStyle("-fx-text-fill: red; -fx-font-size: 16px; -fx-font-weight: bold;");
                            connectButton.setText("BAĞLAN");
                            connectButton.setDisable(false);
                        });
                        break;
                    }
                }

                int exitCode = vpnProcess.waitFor();
                appendLog(" VPN süreci sonlandı. Çıkış kodu: " + exitCode);


                if (exitCode == 0) {
                    connectionSuccess = true;
                    isConnected = true;

                    appendLog("'session-start' başarılı. Bağlantı kuruluyor...");
                    Platform.runLater(() -> {
                        statusLabel.setText(" Bağlantı başarılı!"); // İyimser olarak başarılı say
                        statusLabel.setStyle("-fx-text-fill: green; -fx-font-size: 16px; -fx-font-weight: bold;");
                        connectButton.setText("BAĞLANTIYI KES");
                        connectButton.setDisable(false);
                    });

                } else {
                    connectionSuccess = false;
                    isConnected = false;
                    appendLog(" 'session-start' başarısız oldu.");
                    Platform.runLater(() -> {
                        statusLabel.setText(" Bağlantı başlatılamadı.");
                        statusLabel.setStyle("-fx-text-fill: red; -fx-font-size: 16px; -fx-font-weight: bold;");
                        connectButton.setText("BAĞLAN");
                        connectButton.setDisable(false);
                    });
                }



            } catch (Exception e) {
                appendLog(" HATA: " + e.getMessage());
                e.printStackTrace();
                isConnected = false;
                vpnProcess = null;
                Platform.runLater(() -> {
                    statusLabel.setText(" Hata: " + e.getMessage());
                    statusLabel.setStyle("-fx-text-fill: red; -fx-font-size: 16px; -fx-font-weight: bold;");
                    connectButton.setText("BAĞLAN");
                    connectButton.setDisable(false);
                });
            }

        }).start();
    }



    private void stopVpn() {
        new Thread(() -> {

            if (tempConfigPath != null && Files.exists(tempConfigPath)) {
                appendLog("... 'openvpn3 session-manage --disconnect' çalıştırılıyor...");
                try {
                    ProcessBuilder pbDisconnect = new ProcessBuilder(
                            "openvpn3", "session-manage",
                            "--config", tempConfigPath.toAbsolutePath().toString(), // Tam yolu kullan
                            "--disconnect"
                    );
                    appendLog(" Komut: " + String.join(" ", pbDisconnect.command()));
                    Process pDisconnect = pbDisconnect.start();
                    if (pDisconnect.waitFor(5, TimeUnit.SECONDS))
                        appendLog("✓ Oturum kapatıldı.");
                    else {
                        appendLog(" Zaman aşımı, zorla kapatılıyor...");
                        pDisconnect.destroyForcibly();
                    }
                } catch (Exception e) {
                    appendLog(" Oturum kapatma hatası: " + e.getMessage());
                }
            } else {
                appendLog(" Kapatılacak config yolu bulunamadı (tempConfigPath null).");
            }


            isConnected = false;
            Platform.runLater(() -> {
                statusLabel.setText("⭕ Bağlantı kesildi.");
                statusLabel.setStyle("-fx-text-fill: gray; -fx-font-size: 16px; -fx-font-weight: bold;");
                connectButton.setText("BAĞLAN");
                connectButton.setDisable(false);
            });
        }).start();
    }

    public static void main(String[] args) {
        launch(args);
    }
}


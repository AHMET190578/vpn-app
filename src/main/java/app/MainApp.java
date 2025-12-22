package app;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;

public class MainApp extends Application {

    private Label statusLabel;
    private Button connectButton;
    private Button selectFileButton;
    private Label fileLabel;
    private TextField usernameField;
    private PasswordField passwordField;
    private TextArea logArea;
    private Process vpnProcess;
    private Process logProcess;
    private boolean isConnected = false;
    private Path tempConfigPath;
    private File selectedOvpnFile;

    private String importedConfigPath;
    private String configName;

    public MainApp() {

    }

    @Override
    public void start(Stage primaryStage) {
        usernameField = new TextField();

        primaryStage.setTitle("VPN App (OpenVPN 3)");

        Label titleLabel = new Label("Vpn bağlantı yöneticisi");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        Label ovpnLabel = new Label("Dosya seç");
        ovpnLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        selectFileButton = new Button("📁 Dosya Seç");
        selectFileButton.setPrefWidth(150);
        selectFileButton.setStyle("-fx-font-size: 12px;");

        fileLabel = new Label("Dosya seçilmedi");
        fileLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: gray;");

        HBox fileBox = new HBox(10);
        fileBox.setAlignment(Pos.CENTER_LEFT);
        fileBox.getChildren().addAll(selectFileButton, fileLabel);

        selectFileButton.setOnAction(event -> selectedOvpnFile(primaryStage));

        Label credLabel = new Label("2. VPN Kimlik Bilgileri:");
        credLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        Label userLabel = new Label("Kullanıcı Adı:");
        usernameField = new TextField();
        usernameField.setPromptText("VPN kullanıcı adınız");
        usernameField.setPrefWidth(300);

        Label passLabel = new Label("Şifre:");
        passwordField = new PasswordField();
        passwordField.setPromptText("VPN şifreniz");
        passwordField.setPrefWidth(300);

        VBox credBox = new VBox(8);
        credBox.getChildren().addAll(userLabel, usernameField, passLabel, passwordField);

        statusLabel = new Label("Bağlantı kesildi");
        statusLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        connectButton = new Button("BAĞLAN");
        connectButton.setPrefWidth(200);
        connectButton.setPrefHeight(45);
        connectButton.setStyle(
                "-fx-font-size: 16px; -fx-font-weight: bold; -fx-background-color: #4CAF50; -fx-text-fill: white;");
        connectButton.setDisable(true);

        connectButton.setOnAction(event -> {
            if (isConnected)
                stopVpn();
            else
                startVpn();
        });

        Label logLabel = new Label("VPN Logları:");
        logLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(200);
        logArea.setWrapText(true);
        logArea.setStyle("-fx-font-family: monospace; -fx-font-size: 10px;");

        VBox layout = new VBox(15);
        layout.setAlignment(Pos.TOP_CENTER);
        layout.setPadding(new Insets(20));

        Separator sep1 = new Separator();
        Separator sep2 = new Separator();
        Separator sep3 = new Separator();

        layout.getChildren().addAll(
                titleLabel,
                sep1,
                ovpnLabel,
                fileBox,
                sep2,
                credLabel,
                credBox,
                sep3,
                statusLabel,
                connectButton,
                logLabel,
                logArea);

        Scene scene = new Scene(layout, 650, 700);
        primaryStage.setScene(scene);
        primaryStage.show();

        primaryStage.setOnCloseRequest(event -> {
            if (isConnected)
                stopVpn();
            if (vpnProcess != null)
                vpnProcess.destroyForcibly();
        });
    }

    private void selectedOvpnFile(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(".ovpn uzantılı dosyayı seçin");

        fileChooser.getExtensionFilters().addAll();

        File file = fileChooser.showOpenDialog(stage);
        if (file != null) {
            selectedOvpnFile = file;
            fileLabel.setText(file.getName());
            fileLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: green;");
            checkCanConnect();
            appendLog("ovpn dosyasyı seçildi" + file.getAbsolutePath());
        }
    }

    private void checkCanConnect() {
        boolean canConnect = selectedOvpnFile != null &&
                !usernameField.getText().trim().isEmpty() &&
                !passwordField.getText().trim().isEmpty();
        connectButton.setDisable(!canConnect);

        usernameField.textProperty().addListener((obs, old, newVal) -> {
            connectButton.setDisable(selectedOvpnFile == null ||
                    newVal.trim().isEmpty() ||
                    passwordField.getText().trim().isEmpty());
        });

        passwordField.textProperty().addListener((obs, old, newVal) -> {
            connectButton.setDisable(selectedOvpnFile == null ||
                    usernameField.getText().trim().isEmpty() ||
                    newVal.trim().isEmpty());
        });
    }

    private void appendLog(String message) {
        Platform.runLater(() -> logArea.appendText(message + "\n"));
    }

    private void startVpn() {
        new Thread(() -> {

            cleanupStaleSessions();

            Path tempDir = null;
            try {

                tempDir = Files.createTempDirectory("myvpnapp_openvpn3");
                Path authPath = tempDir.resolve("auth.txt");
                tempConfigPath = tempDir.resolve("config.ovpn");

                String username = usernameField.getText().trim();
                String password = passwordField.getText().trim();
                String authContent = username + "\n" + password + "\n";
                Files.writeString(authPath, authContent,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.SYNC);
                Files.setPosixFilePermissions(authPath, PosixFilePermissions.fromString("rw-------"));
                appendLog(" Auth dosyası oluşturuldu: " + authPath.toAbsolutePath());

                String originalConfigContent = Files.readString(selectedOvpnFile.toPath(), StandardCharsets.UTF_8);
                appendLog("OVPN dosyası: " + selectedOvpnFile.getName());

                String modifiedConfigContent;
                String authPathString = authPath.toAbsolutePath().toString();

                String authUserPassRegex = "(?m)^([\\s#]*)auth-user-pass.*$";
                String newAuthLine = "$1auth-user-pass " + authPathString;

                if (originalConfigContent.matches(authUserPassRegex.replace(".*", "[\\s]*$"))) {
                    modifiedConfigContent = originalConfigContent.replaceAll(authUserPassRegex.replace(".*", "[\\s]*$"),
                            newAuthLine);
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

                Files.writeString(tempConfigPath, modifiedConfigContent, StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING);
                appendLog(" Modifiye edilmiş config dosyası oluşturuldu: " + tempConfigPath.toAbsolutePath());

                Platform.runLater(() -> {
                    statusLabel.setText(" Bağlanıyor...");
                    statusLabel.setStyle("-fx-text-fill: orange; -fx-font-size: 16px; -fx-font-weight: bold;");
                    connectButton.setDisable(true);
                    logArea.clear();
                });

                appendLog("config dosyası improt ediliyor");
                configName = "myvpnapp_" + System.currentTimeMillis();
                ProcessBuilder importPb = new ProcessBuilder(
                        "openvpn3",
                        "config-import",
                        "--config", tempConfigPath.toAbsolutePath().toString(),
                        "--name", "myvpnapp_" + System.currentTimeMillis(),
                        "--persistent");
                importPb.directory(tempDir.toFile());
                appendLog("Import komutu: " + String.join(" ", importPb.command()));

                Process importProcess = importPb.start();
                BufferedReader importReader = new BufferedReader(
                        new InputStreamReader(importProcess.getInputStream(), StandardCharsets.UTF_8));

                String importLine;
                String configPath = null;
                while ((importLine = importReader.readLine()) != null) {
                    appendLog(importLine);
                    if (importLine.contains("/net/openvpn/v3/configuration/")) {
                        int pathStart = importLine.indexOf("/net/openvpn/v3/configuration/");
                        if (pathStart != -1) {
                            configPath = importLine.substring(pathStart).trim();
                            importedConfigPath = configPath;
                        }
                    }
                }

                int importExitCode = importProcess.waitFor();
                if (importExitCode != 0) {
                    throw new Exception("Config import başarısız Çıkış kodu: " + importExitCode);
                }
                appendLog("Config başarıyla import edildi!");

                appendLog("OpenVPN 3 başlatılıyor...");
                ProcessBuilder pb = new ProcessBuilder(
                        "openvpn3",
                        "session-start",
                        "--config-path", configPath != null ? configPath : tempConfigPath.toAbsolutePath().toString());

                pb.directory(tempDir.toFile());
                pb.redirectErrorStream(true);
                appendLog("Session komutu: " + String.join(" ", pb.command()));

                vpnProcess = pb.start();
                appendLog("✓ Session başlatıldı (PID: " + vpnProcess.pid() + ")");

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(vpnProcess.getInputStream(), StandardCharsets.UTF_8));

                String line;
                String sessionPath = null;
                boolean connectionSuccess = false;
                long startTime = System.currentTimeMillis();
                long timeout = 90000;

                while ((line = reader.readLine()) != null) {
                    appendLog(line);
                    if (line.contains("Session path:")) {
                        sessionPath = line.substring(line.indexOf(":") + 1).trim();
                        break;
                    }
                }

                if (sessionPath == null) {
                    appendLog("Session path bulunamadı!");
                    return;
                }

                new Thread(() -> {
                    try {
                        String l;
                        while ((l = reader.readLine()) != null) {

                            appendLog("[Direct]: " + l);
                        }
                    } catch (IOException e) {
                        // ignore
                    }
                }).start();

                appendLog("Log akışı başlatılıyor: " + sessionPath);
                ProcessBuilder lb = new ProcessBuilder("openvpn3", "log", "--session-path", sessionPath, "--log-level",
                        "6");
                lb.redirectErrorStream(true);
                logProcess = lb.start();

                BufferedReader logReader = new BufferedReader(
                        new InputStreamReader(logProcess.getInputStream(), StandardCharsets.UTF_8));

                while (logProcess.isAlive() || logReader.ready()) {
                    if (logReader.ready()) {
                        line = logReader.readLine();
                        if (line == null)
                            break;
                        appendLog(line);

                        if (line.contains("Initialization Sequence Completed") ||
                                line.contains("Connection, Client connected") ||
                                line.contains("Client INFO: Connected")) {
                            connectionSuccess = true;
                            isConnected = true;
                            Platform.runLater(() -> {
                                statusLabel.setText(" Bağlantı başarılı!");
                                statusLabel
                                        .setStyle("-fx-text-fill: green; -fx-font-size: 16px; -fx-font-weight: bold;");
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
                            vpnProcess.destroy(); // Ana process'i kapat
                            break;
                        }
                    } else {
                        // Ana VPN process'i öldüyse ve log akışı da durduysa döngüden çık
                        if (!vpnProcess.isAlive()) {
                            int exitCode = vpnProcess.exitValue();
                            if (exitCode != 0) {
                                appendLog("VPN işlemi sonlandı (Hata kodu: " + exitCode + ")");
                                break;
                            }
                        }

                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }

                    if (!connectionSuccess && (System.currentTimeMillis() - startTime > timeout)) {
                        appendLog("Bağlantı zaman aşımına uğradı (" + (timeout / 1000) + " saniye)");
                        logProcess.destroy();
                        vpnProcess.destroy();
                        break;
                    }
                }

                int exitCode = vpnProcess.waitFor();
                appendLog(" VPN süreci sonlandı. Çıkış kodu: " + exitCode);

                isConnected = false;

                if (exitCode == 0) {

                    boolean alive = isSessionAlive();
                    if (alive) {
                        appendLog("Process sonlandı ancak VPN oturumu arka planda aktif.");
                        isConnected = true;
                        Platform.runLater(() -> {
                            statusLabel.setText(" Bağlantı Başarılı (Monitor)");
                            statusLabel.setStyle("-fx-text-fill: green; -fx-font-size: 16px; -fx-font-weight: bold;");
                            connectButton.setText("BAĞLANTIYI KES");
                            connectButton.setDisable(false);
                            logArea.appendText("--- Log akışı kesildi (Process Detached), Arka plan izleniyor ---\n");
                        });
                        startSessionMonitor();
                    } else {
                        appendLog("VPN oturumu sonlandı (Normal Çıkış).");
                        Platform.runLater(() -> {
                            statusLabel.setText("Bağlantı kesildi");
                            statusLabel.setStyle("-fx-text-fill: gray; -fx-font-size: 16px; -fx-font-weight: bold;");
                            connectButton.setText("BAĞLAN");
                            connectButton.setDisable(false);
                        });
                    }
                } else {

                    if (!connectionSuccess) {
                        appendLog("VPN başlatılamadı (Hata Kodu: " + exitCode + ")");
                        cleanupFailedConnection();
                    } else {
                        appendLog("VPN bağlantısı koptu (Hata Kodu: " + exitCode + ")");
                    }

                    Platform.runLater(() -> {
                        statusLabel.setText("Bağlantı sonlandı (Kod: " + exitCode + ")");
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

    private void cleanupFailedConnection() {
        try {
            appendLog("Başarısız bağlantı temizleniyor...");
            cleanupSessionByConfigPath();

            if (importedConfigPath != null) {
                try {
                    appendLog("Config siliniyor: " + importedConfigPath);
                    ProcessBuilder pbConfigRemove = new ProcessBuilder(
                            "openvpn3", "config-remove",
                            "--path", importedConfigPath,
                            "--force");
                    pbConfigRemove.redirectErrorStream(true);
                    Process p = pbConfigRemove.start();

                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        appendLog(line);
                    }

                    p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                    appendLog("Config silindi");
                    importedConfigPath = null;
                } catch (Exception e) {
                    appendLog("Config silme hatası: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            appendLog("Temizleme hatası: " + e.getMessage());
        }
    }

    private void cleanupSessionByConfigPath() throws IOException {
        if (importedConfigPath == null) {
            appendLog("sesion temizleniyor");
            return;
        }

        try {
            ProcessBuilder pbList = new ProcessBuilder("openvpn3", "sessions-list");
            pbList.redirectErrorStream(true);
            Process pList = pbList.start();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(pList.getInputStream(), StandardCharsets.UTF_8));

            String line;
            String currentSessionPath = null;
            boolean foundMatchingSession = false;

            while ((line = reader.readLine()) != null) {
                appendLog(line);
                String trimmed = line.trim();

                if (trimmed.startsWith("Path:")) {
                    currentSessionPath = trimmed.substring(5).trim();
                    foundMatchingSession = false;
                } else if (currentSessionPath != null && trimmed.startsWith("Config name:")) {
                    String configInSession = trimmed.substring(12).trim();

                    if (configInSession.equals(importedConfigPath)) {
                        foundMatchingSession = true;
                        appendLog("eşleşen bulundu" + configInSession);

                        cleanupSessions(currentSessionPath);
                        break;
                    }
                }
            }
            pList.waitFor();
            if (!foundMatchingSession) {
                appendLog("aktif sessions yok");
            }
        } catch (Exception e) {
            appendLog("sessions arama hatası" + e.getMessage());
            e.printStackTrace();
        }

    }

    private void cleanupSessions(String sessionPath) throws IOException, InterruptedException {
        try {
            appendLog("session bağlantısı kesiliyor" + sessionPath);

            ProcessBuilder pbDisconnect = new ProcessBuilder(
                    "openvpn3", "session-manage",
                    "--session-path", sessionPath,
                    "--disconnect");

            pbDisconnect.redirectErrorStream(true);
            Process pDisconnect = pbDisconnect.start();

            BufferedReader disconnectReader = new BufferedReader(
                    new InputStreamReader(pDisconnect.getInputStream(), StandardCharsets.UTF_8));

            String line;

            while ((line = disconnectReader.readLine()) != null) {
                appendLog(line);
            }

            boolean finished = pDisconnect.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                pDisconnect.destroyForcibly();
            }
            appendLog("Session disconnect edildi");

            Thread.sleep(1000);

            appendLog("cleanup yapılıyor" + sessionPath);
            ProcessBuilder pbCleanup = new ProcessBuilder(
                    "openvpn3", "session-manage",
                    "--session-path", sessionPath,
                    "--cleanup");
            pbCleanup.redirectErrorStream(true);

            Process pCleanup = pbCleanup.start();
            BufferedReader cleanupReader = new BufferedReader(
                    new InputStreamReader(pCleanup.getInputStream(), StandardCharsets.UTF_8));

            while ((line = cleanupReader.readLine()) != null) {
                appendLog(line);
            }

            finished = pCleanup.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                pCleanup.destroyForcibly();
            }
            appendLog("Session temizlendi");
        } catch (Exception e) {
            appendLog("sessions cleanup hatası" + e.getMessage());
            e.printStackTrace();
        }

    }

    private void stopVpn() {
        new Thread(() -> {
            try {
                appendLog("VPN bağlantısı kesiliyor...");

                String sessionPath = null;
                String currentParsingPath = null;
                try {
                    appendLog("Aktif session'lar aranıyor...");
                    ProcessBuilder pbList = new ProcessBuilder("openvpn3", "sessions-list");
                    pbList.redirectErrorStream(true);
                    Process pList = pbList.start();

                    BufferedReader listReader = new BufferedReader(
                            new InputStreamReader(pList.getInputStream(), StandardCharsets.UTF_8));
                    String line;
                    while ((line = listReader.readLine()) != null) {
                        appendLog(line);
                        String trimmedLine = line.trim();

                        if (trimmedLine.startsWith("Path:")) {
                            currentParsingPath = trimmedLine.substring(trimmedLine.indexOf(":") + 1).trim();
                        } else if (currentParsingPath != null &&
                                importedConfigPath != null &&
                                trimmedLine.startsWith("Config:") &&
                                trimmedLine.contains(importedConfigPath)) {

                            sessionPath = currentParsingPath;
                            appendLog("Kapatılacak session bulundu: " + sessionPath);
                            break;
                        } else if (trimmedLine.startsWith("---")) {
                            currentParsingPath = null;
                        }
                    }
                    pList.waitFor();
                } catch (Exception e) {
                    appendLog("Session listesi alınamadı: " + e.getMessage());
                }

                if (sessionPath != null) {
                    try {
                        appendLog("Session disconnect ediliyor");
                        ProcessBuilder pbDisconnect = new ProcessBuilder(
                                "openvpn3", "session-manage",
                                "--config", sessionPath,
                                "--disconnect");
                        pbDisconnect.redirectErrorStream(true);
                        appendLog("Komut: " + String.join(" ", pbDisconnect.command()));

                        Process pDisconnect = pbDisconnect.start();
                        BufferedReader disconnectReader = new BufferedReader(
                                new InputStreamReader(pDisconnect.getInputStream(), StandardCharsets.UTF_8));
                        String line;
                        while ((line = disconnectReader.readLine()) != null) {
                            appendLog(line);
                        }

                        pDisconnect.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                        appendLog("Session disconnect edildi.");
                        Thread.sleep(2000);

                    } catch (Exception e) {
                        appendLog("Disconnect hatası: " + e.getMessage());
                    }

                    try {
                        appendLog("Session siliniyor...");
                        ProcessBuilder pbRemove = new ProcessBuilder(
                                "openvpn3", "session-manage",
                                "--session-path", sessionPath,
                                "--cleanup");
                        pbRemove.redirectErrorStream(true);
                        appendLog("Komut: " + String.join(" ", pbRemove.command()));

                        Process pRemove = pbRemove.start();
                        BufferedReader removeReader = new BufferedReader(
                                new InputStreamReader(pRemove.getInputStream(), StandardCharsets.UTF_8));
                        String line;
                        while ((line = removeReader.readLine()) != null) {
                            appendLog(line);
                        }

                        pRemove.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                        appendLog("Session silindi.");

                    } catch (Exception e) {
                        appendLog("Session silme hatası: " + e.getMessage());
                    }
                } else {
                    appendLog("Aktif session bulunamadı.");
                }

                if (importedConfigPath != null) {
                    try {
                        appendLog("Import edilen config siliniyor...");
                        ProcessBuilder pbConfigRemove = new ProcessBuilder(
                                "openvpn3", "config-remove",
                                "--path", importedConfigPath,
                                "--force");
                        pbConfigRemove.redirectErrorStream(true);
                        appendLog("Komut: " + String.join(" ", pbConfigRemove.command()));

                        Process pConfigRemove = pbConfigRemove.start();
                        BufferedReader configRemoveReader = new BufferedReader(
                                new InputStreamReader(pConfigRemove.getInputStream(), StandardCharsets.UTF_8));
                        String line;
                        while ((line = configRemoveReader.readLine()) != null) {
                            appendLog(line);
                        }

                        pConfigRemove.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                        appendLog("Config silindi.");
                        importedConfigPath = null;

                    } catch (Exception e) {
                        appendLog("Config silme hatası: " + e.getMessage());
                    }
                }

                if (vpnProcess != null && vpnProcess.isAlive()) {
                    vpnProcess.destroy();
                    boolean terminated = vpnProcess.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);

                    if (!terminated) {
                        appendLog("Process yanıt vermedi, zorla sonlandırılıyor...");
                        vpnProcess.destroyForcibly();
                        vpnProcess.waitFor();
                    }
                }

                if (logProcess != null && logProcess.isAlive()) {
                    logProcess.destroyForcibly();
                    logProcess = null;
                }

                isConnected = false;
                vpnProcess = null;

                appendLog("VPN bağlantısı tamamen kesildi ve temizlendi.");

                Platform.runLater(() -> {
                    statusLabel.setText("Bağlantı kesildi");
                    statusLabel.setStyle("-fx-text-fill: gray; -fx-font-size: 16px; -fx-font-weight: bold;");
                    connectButton.setText("BAĞLAN");
                    connectButton.setStyle(
                            "-fx-font-size: 16px; -fx-font-weight: bold; -fx-background-color: #4CAF50; -fx-text-fill: white;");
                    connectButton.setDisable(false);
                    selectFileButton.setDisable(false);
                    usernameField.setDisable(false);
                    passwordField.setDisable(false);
                });

            } catch (Exception e) {
                appendLog("Bağlantı kesme hatası: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    private boolean isSessionAlive() {
        if (importedConfigPath == null)
            return false;
        try {
            ProcessBuilder pb = new ProcessBuilder("openvpn3", "sessions-list");
            Process p = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(importedConfigPath)) {
                    return true;
                }
            }
            p.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private void startSessionMonitor() {
        new Thread(() -> {
            while (isConnected) {
                try {
                    Thread.sleep(3000);
                    if (!isSessionAlive()) {
                        appendLog("Arka plandaki VPN oturumu koptu.");
                        isConnected = false;
                        Platform.runLater(() -> {
                            statusLabel.setText("Bağlantı koptu (Monitor)");
                            statusLabel.setStyle("-fx-text-fill: red; -fx-font-size: 16px; -fx-font-weight: bold;");
                            connectButton.setText("BAĞLAN");
                            connectButton.setDisable(false);
                        });
                        stopVpn();
                        break;
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();
    }

    private void cleanupStaleSessions() {
        try {
            appendLog("Eski oturumlar kontrol ediliyor...");
            ProcessBuilder pb = new ProcessBuilder("openvpn3", "sessions-list");
            Process p = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            String currentPath = null;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("Path:")) {
                    currentPath = line.substring(5).trim();
                } else if (line.startsWith("Config name:") && currentPath != null) {
                    String configName = line.substring(12).trim();
                    if (configName.startsWith("myvpnapp_")) {
                        appendLog("Eski oturum bulundu ve temizleniyor: " + configName);

                        ProcessBuilder pbDisc = new ProcessBuilder("openvpn3", "session-manage", "--session-path",
                                currentPath, "--disconnect");
                        pbDisc.start().waitFor();

                        ProcessBuilder pbClean = new ProcessBuilder("openvpn3", "config-remove", "--config", configName,
                                "--force");
                        pbClean.start().waitFor();
                    }
                }
            }
            p.waitFor();

        } catch (Exception e) {
            appendLog("Temizlik uyarısı: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}

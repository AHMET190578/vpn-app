package util;

import java.io.InputStream;
import java.util.Properties;

public class ConfigLoader {

    private static Properties properties = new Properties();
    private static final String CONFIG_FILE = "config.properties";

    static {
        loadProperties();
    }

    private static void loadProperties() {
        try (InputStream input = ConfigLoader.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (input == null) {
                System.err.println("!!! HATA: " + CONFIG_FILE + " dosyası bulunamadı!");

                return;
            }

            properties.load(input);
            System.out.println(CONFIG_FILE + " dosyası başarıyla yüklendi.");

        } catch (Exception ex) {
            System.err.println(" HATA: " + CONFIG_FILE + " yüklenirken hata oluştu: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    public static String getProperty(String key) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            System.err.println("Uyarı: '" + key + "' anahtarı " + CONFIG_FILE + " dosyasında bulunamadı veya boş.");
        }
        return value;
    }

    // Anahtarların var olup olmadığını kontrol etmek için yardımcı metod (isteğe bağlı)
    public static boolean hasProperty(String key) {
        return properties.containsKey(key) && !properties.getProperty(key).trim().isEmpty();
    }
}
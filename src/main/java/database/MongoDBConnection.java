package database;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import util.ConfigLoader;

import java.util.concurrent.TimeUnit;

public class MongoDBConnection {
    private static MongoClient mongoClient;
    private static MongoDatabase database;


    public static MongoDatabase getDatabase() {
        if (database == null) {
            String connectionString = ConfigLoader.getProperty("mongodb.connection.string");
            String databaseName = ConfigLoader.getProperty("mongodb.database.name");

            if(connectionString == null || databaseName == null || connectionString.trim().isEmpty() || databaseName.trim().isEmpty()) {
                System.err.println("mongodb bağlantısını kontrol edin");
                return null;
            }
            try {
                MongoClientSettings settings = MongoClientSettings.builder()
                        .applyConnectionString(new com.mongodb.ConnectionString(connectionString))
                        .applyToSocketSettings(builder ->
                                builder.connectTimeout(30, TimeUnit.SECONDS)  // 5'ten 30'a çıkardım
                                        .readTimeout(30, TimeUnit.SECONDS))
                        .applyToSslSettings(builder ->
                                builder.enabled(true)
                                        .invalidHostNameAllowed(true))  // SSL hatası düzeltmesi
                        .build();

                mongoClient = MongoClients.create(settings);
                database = mongoClient.getDatabase(databaseName);
                System.out.println("MongoDB bağlantısı başarılı!");
            } catch (Exception e) {
                System.err.println("MongoDB bağlantı hatası: " + e.getMessage());
                e.printStackTrace();  // Detaylı hata için
                return null;
            }
        }
        return database;
    }

    public static void close() {
        if (mongoClient != null) {
            try {
                mongoClient.close();
                System.out.println("MongoDB bağlantısı kapatıldı");
            } catch (Exception e) {
                System.err.println("MongoDB kapatma hatası: " + e.getMessage());
            }
        }
    }
}
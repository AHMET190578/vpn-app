package service;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import database.MongoDBConnection;
import model.User;
import org.bson.Document;
import org.mindrot.jbcrypt.BCrypt;

public class AuthService {
    private final MongoCollection<Document> usersCollection;

    public AuthService() {
        usersCollection = MongoDBConnection.getDatabase().getCollection("users");
    }


    public boolean register(String username, String email, String password) {

        if (usersCollection.find(Filters.eq("username", username)).first() != null) {
            return false;
        }

        if (usersCollection.find(Filters.eq("email", email)).first() != null) {
            return false;
        }


        String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());


        Document userDoc = new Document()
                .append("username", username)
                .append("email", email)
                .append("passwordHash", hashedPassword);

        usersCollection.insertOne(userDoc);
        return true;
    }


    public User login(String username, String password) {
        Document userDoc = usersCollection.find(Filters.eq("username", username)).first();

        if (userDoc == null) {
            return null;
        }

        String storedHash = userDoc.getString("passwordHash");


        if (BCrypt.checkpw(password, storedHash)) {
            User user = new User();
            user.setId(userDoc.getObjectId("_id"));
            user.setUsername(userDoc.getString("username"));
            user.setEmail(userDoc.getString("email"));
            user.setPasswordHash(storedHash);
            user.setPassword(password);
            return user;
        }

        return null;
    }
}
package model;

import org.bson.types.ObjectId;

public class User {
    private ObjectId id;
    private String username;
    private String email;
    private String passwordHash;

    private String password;

    public User() {}

    public User(String username, String email, String passwordHash) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public ObjectId getId() { return id; }
    public void setId(ObjectId id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }


    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

}

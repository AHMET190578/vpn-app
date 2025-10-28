# VPN App (JavaFX + OpenVPN3)

This project is a VPN application developed using JavaFX.  
**Users must provide their own `.ovpn` file** in order to connect to the VPN. The application does not include a default configuration.

---

## Features

- Connect via OpenVPN 3
- User-friendly interface
- VPN connection and disconnection functionality
- Log screen for monitoring connection status
- Users must provide their own `.ovpn` file to connect

---

## Requirements

- Java 17+ (JDK)
- Maven
- OpenVPN 3 client installed
- OS: Linux

---

## Usage

1. Clone the project from GitHub:
```bash
git clone https://github.com/AHMET190578/vpn-app.git
cd vpn-app
mvn javafx:run
```

When the application opens:

Click the Select OVPN File button to choose your .ovpn file

Enter your username and password

Click the CONNECT button

The VPN connection will start using the provided .ovpn file

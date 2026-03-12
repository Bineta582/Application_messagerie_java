package sn.messagerie.app_messagerie.client;

import sn.messagerie.app_messagerie.util.Protocol;

import java.io.*;
import java.net.Socket;
import java.util.Properties;
import java.util.function.Consumer;

public class Client {

    private static String HOST = "192.168.1.9";
    private static int PORT = 12345;

    static {
        
        try (InputStream input = Client.class.getResourceAsStream("/server.properties")) {
            if (input != null) {
                Properties prop = new Properties();
                prop.load(input);
                HOST = prop.getProperty("server.host", "localhost");
                PORT = Integer.parseInt(prop.getProperty("server.port", "12345"));
                System.out.println("[Client] Configuration chargée : " + HOST + ":" + PORT);
            } else {
                System.out.println("[Client] Fichier server.properties introuvable, utilisation de " + HOST + ":" + PORT);
            }
        } catch (IOException | NumberFormatException e) {
            System.err.println("[Client] Erreur lors du chargement de la configuration, utilisation des valeurs par défaut");
        }
    }

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    private boolean connected = false;

    
    private Consumer<String> onMessageReceived;

    
    private Runnable onDisconnected;

    
    public void setOnMessageReceived(Consumer<String> callback) {
        this.onMessageReceived = callback;
    }

    
    public void setOnDisconnected(Runnable callback) {
        this.onDisconnected = callback;
    }

    
    public void connect() throws IOException {
        socket = new Socket(HOST, PORT);
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
        in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        connected = true;

        
        Thread listenerThread = new Thread(this::listenLoop);
        listenerThread.setDaemon(true); 
        listenerThread.start();

        System.out.println("[Client] Connecté au serveur " + HOST + ":" + PORT);
    }

    
    private void listenLoop() {
        try {
            String line;
            while (connected && (line = in.readLine()) != null) {
                final String message = line;
                if (onMessageReceived != null) {
                    onMessageReceived.accept(message);
                }
            }
        } catch (IOException e) {
            if (connected) {
                System.err.println("[Client] Perte de connexion : " + e.getMessage());
            }
        } finally {
            
            connected = false;
            if (onDisconnected != null) {
                onDisconnected.run();
            }
        }
    }

    
    public void send(String message) {
        if (out != null && connected) {
            out.println(message);
        }
    }

    

    
    public void register(String username, String password, String role) {
        send(Protocol.build(Protocol.REGISTER, username, password, role));
    }

    
    public void login(String username, String password) {
        send(Protocol.build(Protocol.LOGIN, username, password));
    }

    
    public void logout() {
        send(Protocol.LOGOUT);
    }

    
    public void sendMessage(String receiverUsername, String contenu) {
        send(Protocol.build(Protocol.SEND_MSG, receiverUsername, contenu));
    }

    
    public void getConversations() {
        send(Protocol.GET_CONVERSATIONS);
    }

    
    public void getHistory(String otherUsername) {
        send(Protocol.build(Protocol.GET_HISTORY, otherUsername));
    }

    
    public void getOnlineUsers() {
        send(Protocol.GET_ONLINE_USERS);
    }

    
    public void disconnect() {
        connected = false;
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {}
    }

    public boolean isConnected() { return connected; }
}


package server;

import util.MailMessage;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

public class SMTPServer {
    // Server configuration values loaded from JSON
    private static String serverName;
    private static int port;
    private static String spool;
    private static String logFile;

    public static void main(String[] args) throws Exception {
        // Load server configuration from JSON file: config/smtpd.json
        JSONObject config = new JSONObject(Files.readString(Path.of("config/smtpd.json")));
        spool = config.getString("spool");
        serverName = config.getString("server-name");
        port = config.getInt("port");
        logFile = config.getString("log");

        // Create the shared mail queue (thread-safe)
        MailQueue queue = new MailQueue();

        // Start the mail queue consumer thread
        // This thread will take messages from the queue and deliver them to the proper mailbox
        new Thread(new MailQueueThread(queue, spool, serverName, logFile)).start();
        ExecutorService pool = Executors.newFixedThreadPool(10);
        ServerSocket serverSocket = new ServerSocket(port);
        log("220 " + serverName + " SMTP tinysmtp");
        while (true) {
            Socket clientSocket = serverSocket.accept(); // wait for a client to connect
            pool.execute(new SMTPWorker(clientSocket, queue, serverName, logFile));
        }
    }

    // Thread-safe logging method — appends messages to the configured log file
    public static synchronized void log(String message) {
        try (FileWriter fw = new FileWriter(logFile, true)) {
            fw.write(java.time.LocalDateTime.now() + " smtpd: " + message + "\n");
        } catch (IOException e) {
            e.printStackTrace(); 
        }
    }
}

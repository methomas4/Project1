package server;

import util.MailMessage;
import java.io.*;
import java.net.*;
import java.util.*;

public class SMTPWorker implements Runnable {
    private final Socket socket;
    private final MailQueue queue;
    private final String serverName;
    private final String logFile;
    enum State { HELO, MAIL, RCPT, DATA, QUIT }
    private State state = State.HELO;
    // Current email message being constructed
    private MailMessage message = new MailMessage();

    // Constructor initializes fields with client socket, shared queue, server name and log file path
    public SMTPWorker(Socket socket, MailQueue queue, String serverName, String logFile) {
        this.socket = socket;
        this.queue = queue;
        this.serverName = serverName;
        this.logFile = logFile;
    }

    // Main method for handling the SMTP session with a client
    public void run() {
        // Set up input and output streams to communicate with the client
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))
        ) {
            // Send initial SMTP greeting to client
            send(out, "220 " + serverName + " SMTP tinysmtp");

            String line;
            // Loop reads commands from client until connection closes or QUIT received
            while ((line = in.readLine()) != null) {
                log("c: " + line);
                line = line.trim();

                if (line.startsWith("HELO")) {
                    state = State.MAIL;
                    send(out, "250 <" + socket.getInetAddress().getHostAddress() + ">, I am glad to meet you.");
                
                } else if (line.startsWith("MAIL FROM:")) {
                    if (state != State.MAIL) {
                        send(out, "503 Bad sequence of commands");
                    } else {
                        // Start a new MailMessage and set the sender
                        message = new MailMessage();
                        message.setSender(line.substring(10));
                        state = State.RCPT;  // Next expect recipients
                        send(out, "250 Ok");
                    }

                } else if (line.startsWith("RCPT TO:")) {
                    if (state != State.RCPT && state != State.DATA) {
                        send(out, "503 Bad sequence of commands");
                    } else {
                        String recipient = line.substring(8);
                        if (!recipient.contains("@" + serverName)) {
                            send(out, "504 5.5.2 " + recipient + ": Sender address rejected");
                        } else {
                            // Add recipient to the current message
                            message.addRecipient(recipient);
                            state = State.DATA; // Next expect DATA command
                            send(out, "250 Ok");
                        }
                    }

                } else if (line.equals("DATA")) {
                    if (state != State.DATA) {
                        send(out, "503 Bad sequence of commands");
                    } else {
                        send(out, "354 End data with <CR><LF>.<CR><LF>");
                        StringBuilder data = new StringBuilder();
                        // Read message lines until "." on a line by itself
                        while (!(line = in.readLine()).equals(".")) {
                            data.append(line).append("\r\n");
                        }
                        // Set the message content
                        message.setData(data.toString());
                        // Add the completed message to the mail queue for delivery
                        queue.add(message);
                        send(out, "250 Ok delivered message.");
                        state = State.MAIL;
                    }

                } else if (line.equals("RSET")) {
                    if (state == State.RCPT || state == State.DATA) {
                        message = new MailMessage(); // discard current message
                        state = State.MAIL;           // reset state
                        send(out, "250 Ok");
                    } else {
                        send(out, "503 Bad sequence of commands");
                    }
                } else if (line.equals("NOOP")) {
                    send(out, "250 Ok");

                } else if (line.equals("QUIT")) {
                    send(out, "221 Bye");
                    break;

                } else {
                    send(out, "502 5.5.2 Error: command not recognized");
                }
            }
        } catch (Exception e) {
            e.printStackTrace(); 
        }
    }

    // Helper method to send a response line to the client and log it
    private void send(BufferedWriter out, String message) throws IOException {
        log("s: " + message);
        out.write(message + "\r\n");
        out.flush();
    }

    // Helper method to write log messages via SMTPServer's logging method
    private void log(String message) {
        SMTPServer.log(message);
    }
}

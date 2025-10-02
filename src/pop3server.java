import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.logging.*;


public class pop3server implements Runnable {
    private enum State { AUTHORIZATION, TRANSACTION, UPDATE}    // The session states

    private final Socket sock;
    private final Config config;
    private final AccountDB accounts;
    private final Logger log;                                   // Socket, config, accounts and log setup

    private State state = State.AUTHORIZATION;                  // Initial state   
    private String pendingUser = null;                          // Pending user for authentication
    private Account authed = null;                              // Authenticated user account
    private Maildrop maildrop = null;                           // Maildrop for the authenticated user 

    public pop3server(Socket sock, Config config, AccountDB accounts, Logger log) {
        this.sock = sock;
        this.config = config;
        this.accounts = accounts;
        this.log = log;
}

@Override public void run() {
    try (var in = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.US_ASCII));
         var out = new BufferedWriter(new OutputStreamWriter(sock.getOutputStream(), StandardCharsets.US_ASCII))) { //Setup I/O Streams

       send(out, "+Welcome to " + config.serverName() + "tinypop3 server.");    // Send greeting on startup

       String line;
       while ((line = in.readLine()) != null) {                                 // Read the client commands
        String cmdline = line.trim();
        log.info(() -> "pop3d: c: " + cmdline);                                 // Log client command first
        if (cmdline.isEmpty()) { sendErr(out, "empty command!"); continue; }    //If empty command, send error and continue, however...

        String upper = cmdline.toUpperCase();                                   // Convert command to uppercase for easier comparison,
        String[] parts = cmdline.split("\\s+", 2);                  // Split command into parts,
        String cmd = parts[0].toUpperCase();                                    // Command is the first part,
        String args = parts.length > 1 ? parts[1] : null;                       // given arguments are second.

        /**
         * 
         * COMMAND LIST
         * 
         */

        switch (cmd) {
            case "USER" -> handleUSER(out, args);                               // USER command for username
            case "PASS" -> handlePASS(out, args);                               // PASS command for password
            case "STAT" -> handleSTAT(out);                                     // STAT command for mailbox status
            case "LIST" -> handleLIST(out, args);                               // LIST command for listing messages
            case "RETR" -> handleRETR(out, args);                               // RETR command for retrieving a message
            case "DELE" -> handleDELE(out, args);                               // DELE command for deleting a message
            case "NOOP" -> send(out, +OK);                                     // NOOP command does nothing
            case "RSET" -> handleRSET(out);                                     // RSET command resets the session
            case "QUIT" -> { handleQUIT(out); return; }                         // QUIT command ends the session
            default -> sendErr(out, "unknown command: " + cmd + " :(");
        }
       }

    }   catch(SocketTimeoutException ste) {
        // Set to autologout without Update as per RFC 1939
        log.info(() -> "pop3d: c: autologout (timeout)");
        }
        catch (IOException e) {
        // handle IOException by logging it
        log.log(Level.WARNING, "pop3d: I/O error: " + e.getMessage(), e);
        }
        finally {
            try { sock.close(); } catch (IOException ignored) {}
        }
}

        /**
         *  UTILITY COMMANDS
         * Commands that are used for logging and sending data to the client
         * 
         */

         private void sendRaw(BufferedWriter out, String data) throws IOException {
            out.write(data);                                                    // Write raw data to output stream
            out.flush();                                                        // Flush the stream to ensure data is sent
         }

         private void send(BufferedWriter out, String line) throws IOException {
            log.info(() -> "pop3d: s: " + line);                                // Log server response
            sendRaw(out, line + "\r\n");                                        // Send response with CRLF

         }
         
         private void sendErr(BufferedWriter out, String data) throws IOException {
            String line = "-ERR " + data;                                       // Format error response;
            log.info(() -> "pop3d: s: " + line);                                // Log server error response
            sendRaw(out, line + "\r\n");                                        // Send error response with CRLF
         }

         private static int parseNum(String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return -1; } // Parse number or return -1 on error
         }

        /**
         * 
         * ALL SUPPORTED COMMANDS
         * 
         * Below is a list of each command that is handled in the run() method. For simplicity sake, I handled commands outside of the
         * switch case for neatness since others will be reading the code. 
         */


         private void handleUSER(BufferedWriter out, String arg) throws IOException {
            if (state != state.AUTHORIZATION) { sendErr(out, "I'm not ready for this command! (Wrong State)"); return; }
            if (arg == null) { sendErr(out, "Whats your name? (Missing Argument)"); return; }
            pendingUser = arg;
            send(out, "+OK, welcome " + arg);
         }

         private void handlePASS(BufferedWriter out, String arg) throws IOException {

            //Check if in the correct state, look for username if given first, check if password is given even.
            if(state != State.AUTHORIZATION) { sendErr(out, "I'm not ready for this command! (Wrong State)"); return; }
            if(arg == null) { sendErr(out, "What's the password? (Missing Argument)"); return; }
            if(pendingUser == null) { sendErr(out, "I need your username first! (Bad Sequence)"); return; }
            
            // Then, check if the account exists and if the password is correct.
            Account acct = accounts.find(pendingUser);
            if(acct == null || !acct.password().equals(arg)) { sendErr(out, "Authentication failed! (Invalid Credentials)"); return; }
                // Remain in the AUTH state on failure according to RFC 1939.
            authed = acct;
            try {
                maildrop = Maildrop.load(accounts.userMaildir(acct));
            } catch (IOException e) {
                sendErr(out, "Failed to load maildrop! (Server Error)");
                return;
            }
            
            state = State.TRANSACTION; // Move to TRANSACTION state on success
            send(out, "+OK, you are now logged in as " + pendingUser + "!");
         }

         private void handleSTAT(BufferedWriter out) throws IOException {
            if (state != State.TRANSACTION) { sendErr(out, "I'm not ready for this command! (Wrong State)"); return; }
            send(out, "+OK " + maildrop.count() + " " + maildrop.octets()); // Send message count and size 
         }

         private void handleLIST(BufferedWriter out, String arg) throws IOException {
            if (state != State.TRANSACTION) { sendErr(out, "I'm not ready for this command! (Wrong State)"); return; }
            if (arg == null) {
                send(out, "+OK " + maildrop.count() + " messages (" + maildrop.octets() + " octets)");
                for (Maildrop.Message m : maildrop.listAllVisible()) {
                    send(out, m.id + " " + m.sizeBytes);
                }
                sendRaw(out, ".\r\n"); // End of list
                return;
            }
            
            int n = parseNum(arg);
            var msg = maildrop.get(n);
            if (msg == null || msg.sizeBytes < 0) { sendErr(out, "no such message exists! (Invalid Message Number)"); return; }
            send(out, "+OK " + n + " " + msg.sizeBytes); // Send specific message size
         }

         private void handleRETR(BufferedWriter out, String arg) throws IOException {
            if (state != State.TRANSACTION) { sendErr(out, "I'm not ready for this command! (Wrong State)"); return; }
            if (arg == null) { sendErr(out, "Which message do you want? (Missing Argument)"); return; }
            
            int n = parseNum(arg);
            var msg = maildrop.get(n);
            if (msg == null || msg.sizeBytes < 0) { sendErr(out, "No such message exists! (Invalid Message Number)"); return; }
            
            send(out, "+OK " + msg.sizeBytes + " octets");                  // Send message size

            // Send message content with dot-stuffing for CRLF
            try (var reader = new BufferedReader(new StringReader(msg.content))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith(".")) { line = "." + line; } // Dot-stuffing
                    sendRaw(out, line + "\r\n");
                }
            }
            sendRaw(out, ".\r\n"); // End of message
         }

         private void handleDELE(BufferedWriter out, String arg) throws IOException {
            if (state != State.TRANSACTION) { sendErr(out, "I'm not ready for this command! (Wrong State)"); return; }
            if (arg == null) { sendErr(out, "Which message do you want to delete? (Missing Argument)"); return; }
            
            int n = parseNum(arg);
            var msg = maildrop.get(n);
            if (maildrop.markDeleted(n)) send(out, "+OK message " + n + " deleted!");
            else sendErr(out, "No such message exists! (Invalid Message Number)");
         }

         private void handleRSET(BufferedWriter out) throws IOException {
            if (state != State.TRANSACTION) { sendErr(out, "I'm not ready for this command! (Wrong State)"); return; }
            maildrop.reset(); // Reset all deletion marks
            send(out, "+OK all deletion marks removed");
         }

         private void handleQUIT(BufferedWriter out) throws IOException {
            if (state == State.TRANSACTION) {
                try {
                    maildrop.updateCommit(); // Apply deletions
                } catch (IOException e) {
                    sendErr(out, "Failed to update maildrop! (Server Error)");
                    return;
                }
            }
            send(out, "+OK goodbye!");
            state = State.UPDATE; // Move to UPDATE state
         }

}


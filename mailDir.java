import java.io.FileWriter;
import java.io.File;
import java.util.*;
import java.time.Instant;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardCopyOption.*;
import java.nio.file.Paths;
import java.nio.file.Path;

class MailDir{

    //Private ArrayList that stores what indexes should be deleted
    private ArrayList<Boolean> MarkedForDeletion;
    
    //Name of the mailbox this directory represents
    String MailBox;
    
    //constructor
    MailDir(String MailBox){
        this.MailBox = MailBox;
        MarkedForDeletion = new ArrayList<>();
    }

    //Gets the length of the MarkedForDeletion list
    public int getDeletionSize(){
        return MarkedForDeletion.size();
    }

    //Method for converting an email to a file, storing it in new.
    public void toFile(MailMessage email) {
       String FileName = "file";
       String time = Instant.now().toString();
       FileName = time.replace(":","_");
    
        String sender = email.getSender();
        String body = email.getBody();
        String[] recipients = email.getRecipients();
    

        
        try(FileWriter writer = new FileWriter("mail" + MailBox + "/tmp/" + FileName)) {

            writer.write(body);
            writer.close();
            
        } catch(IOException e) {
            System.out.println("error: " + e.getMessage());
        }

        Path SourcePath = Paths.get("mail" + MailBox + "/tmp/"+FileName);
        Path TargetPath = Paths.get("mail" + MailBox + "/new/"+FileName);
        try {
            Files.move(SourcePath, TargetPath, StandardCopyOption.ATOMIC_MOVE);
            
        } catch(IOException e) {
            System.out.println("error: " + e.getMessage());
        }
        
    } 

    //method for loading all messages in "new"
    public String[] loadMessages(){

        File f = new File("mail" + MailBox + "/new/");
        File[] files = f.listFiles();
        String[] list = new String[files.length];

            for (int i = 0; i < files.length; i++) {
               
                list[i] = files[(i)].getName();

            }
            
            return list;
    }


    //Method for retreiving a message by inputting an index, returning the body text of the email it represents
    public String retrieveMessage(int index){
        String emailBody = "";
        String[] list = loadMessages();

        String tempName = list[index];

        Path filePath = Paths.get("mail" + MailBox + "/new/"+tempName);
        
        try {
            emailBody = Files.readString(filePath);
            
        } catch(IOException e) {
            System.out.println("error: " + e.getMessage());
        }
        
        return emailBody;

    }


    //Retrieves the size in bytes of a file
    public long retrieveSize(int index){
        long emailSize = 0;
        String[] list = loadMessages();

        String tempName = list[index];

        
        File selectedFile = new File("mail" + MailBox + "/new/"+tempName);
        emailSize = selectedFile.length();
        
        return emailSize;

    }

    //Retrieves the total size in bytes of all files in the directory, as well as how many in an array of two longs
     public long[] AnalyzeEmails(){

        int fileCount = getFileCount();
        long[] results = new long[2];
        long byteTotal = 0;
        results[1] = fileCount;

            for (int i = 0; i < fileCount; i++) {
                byteTotal = byteTotal + retrieveSize(i);

            }
        results[0] = byteTotal; 

        
            return results;
     }


    //Method used internally by other methods to return the amount of files in the "new" directory
     public int getFileCount(){
        File f = new File("mail" + MailBox + "/new/");
        File[] files = f.listFiles();
        int fileCount = files.length;
        
        return fileCount;
     }

    //Clears all files from being marked for deletion, and updates the list of files marked.
     public void refreshDeletionList(){
        MarkedForDeletion.clear();
        for(int i = 0; i < getFileCount(); i++) {
            MarkedForDeletion.add(false);
        }
        return;
     }


    //Marks a file for deletion at the given index
     public void MarkForDeletion(int Index){
        MarkedForDeletion.set(Index, true);
        return;
     }


    //Deletes all files currently marked for deletion.
     public void DeleteAllMarked(){

        String[] indexes = loadMessages();

        for(int i = 0; i < getDeletionSize(); i++){

            if (MarkedForDeletion.get(i) == true){
                
                File temp = new File("mail" + MailBox + "/new/" + indexes[i]);
                temp.delete();
            }
        }

     }

}
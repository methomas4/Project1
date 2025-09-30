

class MailMessage {
    // setting up the variables
String Body;
String Sender;
String[] Recipients;

    //constructor for actually creating mailmessage objects
    MailMessage(String Body, String Sender, String[] Recipients){

        this.Body = Body;
        this.Sender = Sender;
        this.Recipients = Recipients;

    }


    //methods for getting body, sender, and recipients
    public String getBody() {
        return Body;
    }

    public String getSender() {
        return Sender;
    }

    public String[] getRecipients() {
        return Recipients;
    }

    

}

//temporary code I am using for debugging and testing, will be deleted later
class MessageTest{    

    public static void main(String[] args){
    String bodyText = "This is an email";
    String senderName = "me'";
    String[] recipientNames = {"you", "everyone else"};

        MailMessage mail = new MailMessage(bodyText, senderName, recipientNames);


        String test = mail.getRecipients()[0];
        System.out.println(test);

    }

    

}


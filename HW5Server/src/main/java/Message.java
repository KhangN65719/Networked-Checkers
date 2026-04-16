import java.io.Serializable;
import java.util.ArrayList;

public class Message implements Serializable {
    static final long serialVersionUID = 42L;

    public static final int SIGN_IN = 1;
    public static final int SIGN_IN_OK = 2;
    public static final int SIGN_IN_FAIL = 3;
    public static final int USER_LIST = 4;
    public static final int SEND_ALL = 5;
    public static final int SEND_PRIVATE = 6;
    public static final int CREATE_GROUP = 7;
    public static final int SEND_GROUP = 8;
    public static final int CHAT_MESSAGE = 9;
    public static final int GROUP_LIST = 10;
    public static final int JOIN_GROUP = 11;

    public int type;
    public String sender;
    public String recipient;
    public String groupName;
    public String content;
    public ArrayList<String> userList;
    public ArrayList<String> groupList;

    public Message() {}

    public static Message signIn(String username) {
        Message msg = new Message();
        msg.type = SIGN_IN;
        msg.sender = username;
        return msg;
    }
    public static Message sendAll(String sender, String content) {
        Message msg = new Message();
        msg.type = SEND_ALL;
        msg.sender = sender;
        msg.content = content;
        return msg;
    }
    public static Message sendPrivate(String sender, String recipient, String content) {
        Message msg = new Message();
        msg.type = SEND_PRIVATE;
        msg.sender = sender;
        msg.recipient = recipient;
        msg.content = content;
        return msg;
    }
    public static Message createGroup(String sender, String groupName) {
        Message msg = new Message();
        msg.type = CREATE_GROUP;
        msg.sender = sender;
        msg.groupName = groupName;
        return msg;
    }
    public static Message joinGroup(String sender, String groupName) {
        Message msg = new Message();
        msg.type = JOIN_GROUP;
        msg.sender = sender;
        msg.groupName = groupName;
        return msg;
    }
    public static Message sendGroup(String sender, String groupName, String content) {
        Message msg = new Message();
        msg.type = SEND_GROUP;
        msg.sender = sender;
        msg.groupName = groupName;
        msg.content = content;
        return msg;
    }

    @Override
    public String toString() {
        switch (type) {
            case SIGN_IN_OK:
                return "[Accepted] Welcome, " + sender + "!";
            case SIGN_IN_FAIL:
                return "[Error] Username '" + sender + "' is already taken.";
            case SEND_ALL:
                return "[All] " + sender + ": " + content;
            case SEND_PRIVATE:
                return "[DM from " + sender + "]: " + content;
            case CHAT_MESSAGE:
                return content;
            case CREATE_GROUP:
                return "[Group '" + groupName + "' created by " + sender + "]";
            case JOIN_GROUP:
                return "[" + sender + " joined group '" + groupName + "']";
            case SEND_GROUP:
                return "[Group " + groupName + "] " + sender + ": " + content;
            default:
                return "Message{type=" + type + "}";
        }
    }
}
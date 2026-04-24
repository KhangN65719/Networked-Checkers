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
    public static final int WAITING = 12;
    public static final int GAME_START = 13;
    public static final int GAME_OVER = 14;
    public static final int PLAY_AGAIN = 15;
    public static final int DISCONNECT_QUEUE = 16;
    public static final int GAME_MOVE = 17;
    public static final int FORFEIT = 18;
    public static final int OPPONENT_FORFEIT = 19;

    public int type;
    public String sender;
    public String recipient;
    public String groupName;
    public String content;
    public int connectedCount;
    public int fromRow, fromCol, toRow, toCol;
    public ArrayList<String> userList;
    public ArrayList<String> groupList;

    public Message() {}

    public static Message signIn(String username) {
        Message m = new Message();
        m.type = SIGN_IN;
        m.sender = username;
        return m;
    }
    public static Message sendAll(String sender, String content) {
        Message m = new Message();
        m.type = SEND_ALL;
        m.sender = sender;
        m.content = content;
        return m;
    }
    public static Message sendPrivate(String sender, String recipient, String content) {
        Message m = new Message();
        m.type = SEND_PRIVATE;
        m.sender = sender;
        m.recipient = recipient;
        m.content = content;
        return m;
    }
    public static Message createGroup(String sender, String groupName) {
        Message m = new Message();
        m.type = CREATE_GROUP;
        m.sender = sender;
        m.groupName = groupName;
        return m;
    }
    public static Message joinGroup(String sender, String groupName) {
        Message m = new Message();
        m.type = JOIN_GROUP;
        m.sender = sender;
        m.groupName = groupName;
        return m;
    }
    public static Message sendGroup(String sender, String groupName, String content) {
        Message m = new Message();
        m.type = SEND_GROUP;
        m.sender = sender;
        m.groupName = groupName;
        m.content = content;
        return m;
    }
    public static Message forfeit(String sender) {
        Message m = new Message();
        m.type = FORFEIT;
        m.sender = sender;
        return m;
    }
    public static Message playAgain(String sender) {
        Message m = new Message();
        m.type = PLAY_AGAIN;
        m.sender = sender;
        return m;
    }
    public static Message disconnectQueue(String sender) {
        Message m = new Message();
        m.type = DISCONNECT_QUEUE;
        m.sender = sender;
        return m;
    }
    public static Message gameMove(String sender, int fromRow, int fromCol, int toRow, int toCol) {
        Message m = new Message();
        m.type = GAME_MOVE;
        m.sender = sender;
        m.fromRow = fromRow;
        m.fromCol = fromCol;
        m.toRow = toRow;
        m.toCol = toCol;
        return m;
    }

    @Override
    public String toString() {
        switch (type) {
            case SIGN_IN_OK:{
                return "[Accepted] Welcome, " + sender + "!";
            }
            case SIGN_IN_FAIL: {
                return "[Error] Username '" + sender + "' is already taken.";
            }
            case SEND_ALL: {
                return "[All] " + sender + ": " + content;
            }
            case SEND_PRIVATE: {
                return "[DM from " + sender + "]: " + content;
            }
            case CHAT_MESSAGE: {
                return content;
            }
            case CREATE_GROUP: {
                return "[Group '" + groupName + "' created by " + sender + "]";
            }
            case JOIN_GROUP: {
                return "[" + sender + " joined group '" + groupName + "']";
            }
            case SEND_GROUP: {
                return "[Group " + groupName + "] " + sender + ": " + content;
            }
            case WAITING: {
                return "[Waiting] " + connectedCount + "/2 connected";
            }
            case GAME_START: {
                return "[Game Start] Opponent: " + recipient;
            }
            case GAME_OVER: {
                return "[Game Over] " + content;
            }
            case GAME_MOVE: {
                return "[Move] " + sender + ": (" + fromRow + "," + fromCol + ") -> (" + toRow + "," + toCol + ")";
            }
            default: {
                return "Message{type=" + type + "}";
            }
        }
    }
}
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.function.Consumer;

public class Server {

	int clientCount = 1;
	ArrayList<ClientThread> clients = new ArrayList<>();
	TheServer server;
	private final Consumer<Serializable> callback;
	Set<String> takenUsernames = new HashSet<>();
	Map<String, Set<String>> groups = new HashMap<>();

	private final LinkedList<ClientThread> matchQueue = new LinkedList<>();
	private final Map<String, ClientThread> rematchQueue = new HashMap<>();

	Server(Consumer<Serializable> call) {
		callback = call;
		server = new TheServer();
		server.start();
	}

	private synchronized void broadcastUserList() {
		Message msg = new Message();
		msg.type = Message.USER_LIST;
		msg.userList = new ArrayList<>(takenUsernames);
		for (ClientThread ct : clients) {
			ct.send(msg);
		}
	}

	private synchronized void broadcastGroupList() {
		Message msg = new Message();
		msg.type = Message.GROUP_LIST;
		msg.groupList = new ArrayList<>(groups.keySet());
		for (ClientThread ct : clients) {
			ct.send(msg);
		}
	}

	private synchronized ClientThread findClient(String username) {
		for (ClientThread ct : clients) {
			if (username.equals(ct.username)) {
				return ct;
			}
		}
		return null;
	}

	synchronized void enqueueForMatch(ClientThread ct) {
		if (matchQueue.contains(ct)) return;
		matchQueue.add(ct);
		callback.accept("(Queue) " + ct.username + " is waiting. Queue size: " + matchQueue.size());

		Message waiting = new Message();
		waiting.type = Message.WAITING;
		waiting.connectedCount = matchQueue.size();
		ct.send(waiting);

		if (matchQueue.size() >= 2) {
			ClientThread p1 = matchQueue.poll();
			ClientThread p2 = matchQueue.poll();
			startGame(p1, p2);
		}
	}

	private void startGame(ClientThread p1, ClientThread p2) {
		p1.opponent = p2;
		p2.opponent = p1;
		p1.lastOpponentUsername = p2.username;
		p2.lastOpponentUsername = p1.username;
		callback.accept("(Match) " + p1.username + " vs " + p2.username);

		Message start1 = new Message();
		start1.type = Message.GAME_START;
		start1.sender = p1.username;
		start1.recipient = p2.username;
		start1.content = "LIGHT";
		p1.send(start1);

		Message start2 = new Message();
		start2.type = Message.GAME_START;
		start2.sender = p2.username;
		start2.recipient = p1.username;
		start2.content = "DARK";
		p2.send(start2);
	}

	synchronized void endGame(ClientThread winner, ClientThread loser) {
		Message win = new Message();
		win.type = Message.GAME_OVER;
		win.content = "WIN";
		winner.send(win);

		Message lose = new Message();
		lose.type = Message.GAME_OVER;
		lose.content = "LOSE";
		loser.send(lose);

		winner.opponent = null;
		loser.opponent = null;
		callback.accept("(Game Over) " + winner.username + " beat " + loser.username);
	}

	public class TheServer extends Thread {
		public void run() {
			try (ServerSocket ss = new ServerSocket(5555)) {
				callback.accept("Server is waiting for clients on port 5555...");
				while (true) {
					Socket socket = ss.accept();
					ClientThread ct = new ClientThread(socket, clientCount++);
					clients.add(ct);
					ct.start();
				}
			} catch (Exception e) {
				callback.accept("Server socket failed: " + e.getMessage());
			}
		}
	}

	class ClientThread extends Thread {
		Socket connection;
		int id;
		String username = null;
		ClientThread opponent = null;
		String lastOpponentUsername = null;
		ObjectInputStream inStream;
		ObjectOutputStream outStream;

		ClientThread(Socket socket, int id) {
			this.connection = socket;
			this.id = id;
		}

		synchronized void send(Message msg) {
			try {
				outStream.writeObject(msg);
				outStream.reset();
			} catch (Exception ignored) {}
		}

		public void run() {
			try {
				outStream = new ObjectOutputStream(connection.getOutputStream());
				inStream  = new ObjectInputStream(connection.getInputStream());
				connection.setTcpNoDelay(true);
			} catch (Exception e) {
				callback.accept("Could not open streams for client #" + id); return;
			}
			while (true) {
				try {
					Message msg = (Message) inStream.readObject();
					handleMessage(msg);
				} catch (Exception e) {
					handleDisconnect(); break;
				}
			}
		}

		private void handleMessage(Message data) {
			switch (data.type) {
				case Message.SIGN_IN: {
					String name = data.sender == null ? "" : data.sender.trim();
					Message reply = new Message();
					reply.sender = name;
					if (name.isEmpty() || takenUsernames.contains(name)) {
						reply.type = Message.SIGN_IN_FAIL;
						send(reply);
						callback.accept("Sign-in REJECTED for '" + name + "'");
					} else {
						takenUsernames.add(name);
						this.username = name;
						reply.type = Message.SIGN_IN_OK;
						send(reply);
						callback.accept("'" + name + "' connected (client #" + id + ")");
						broadcastUserList();
						broadcastGroupList();
					}
					break;
				}
				case Message.WAITING: {
					enqueueForMatch(this);
					break;
				}
				case Message.DISCONNECT_QUEUE: {
					synchronized (Server.this) {
						matchQueue.remove(this);
					}
					callback.accept("(Queue) " + username + " left the queue.");
					break;
				}
				case Message.PLAY_AGAIN: {
					String lastOpp = this.lastOpponentUsername;
					if (lastOpp == null) {
						enqueueForMatch(this);
						break;
					}
					ClientThread oppThread = findClient(lastOpp);
					if (oppThread == null) {
						Message noOpp = new Message();
						noOpp.type = Message.GAME_OVER;
						noOpp.content = "NO_OPPONENT";
						send(noOpp);
						callback.accept("(Rematch) " + username + " has no opponent to rematch (disconnected).");
						break;
					}
					synchronized (Server.this) {
						if (rematchQueue.containsKey(lastOpp) && rematchQueue.get(lastOpp) == oppThread) {
							rematchQueue.remove(lastOpp);
							callback.accept("(Rematch) " + username + " and " + lastOpp + " are rematching.");
							startGame(this, oppThread);
						}
						else {
							rematchQueue.put(username, this);
							Message waiting = new Message();
							waiting.type = Message.WAITING;
							waiting.connectedCount = 1;
							send(waiting);
							callback.accept("(Rematch) " + username + " is waiting for " + lastOpp + " to rematch.");
						}
					}
					break;
				}
				case Message.GAME_OVER: {
					synchronized (Server.this) {
						if (opponent != null) {
							opponent.opponent = null;
						}
						opponent = null;
					}
					callback.accept("(Game Over) " + username + " reported game finished.");
					break;
				}
				case Message.FORFEIT: {
					synchronized (Server.this) {
						rematchQueue.remove(username);
						if (lastOpponentUsername != null) {
							rematchQueue.remove(lastOpponentUsername);
							ClientThread lastOpp = findClient(lastOpponentUsername);
							if (lastOpp != null) {
								Message out = new Message();
								out.type = Message.OPPONENT_FORFEIT;
								out.content = "NO_OPPONENT";
								lastOpp.opponent = null;
								lastOpp.send(out);
								callback.accept("(Forfeit) Sent OPPONENT_FORFEIT to " + lastOpponentUsername);
							}
						}
						opponent = null;
						lastOpponentUsername = null;
					}
					callback.accept("(Forfeit) " + username + " went home.");
					break;
				}
				case Message.GAME_MOVE: {
					if (opponent != null) {
						callback.accept("(Move) " + username + ": (" + data.fromRow + "," + data.fromCol + ") -> (" + data.toRow + "," + data.toCol + ")");
						opponent.send(data);
					}
					break;
				}
				case Message.SEND_ALL: {
					callback.accept("[Broadcast] " + data.sender + ": " + data.content);
					Message out = new Message();
					out.type = Message.CHAT_MESSAGE;
					out.content = "(All)" + data.sender + ": " + data.content;
					for (ClientThread ct : clients) ct.send(out);
					break;
				}

				case Message.SEND_PRIVATE: {
					callback.accept("(DM)" + data.sender + " -> " + data.recipient + ": " + data.content);
					ClientThread target = findClient(data.recipient);
					Message out = new Message();
					out.type = Message.CHAT_MESSAGE;
					if (target != null) {
						out.content = data.sender + ": " + data.content;
						target.send(out);
						out.content = "You: " + data.content;
						send(out);
					}
					else {
						out.content = "(Server) User '" + data.recipient + "' not found.";
						send(out);
					}
					break;
				}
				case Message.CREATE_GROUP: {
					String gName = data.groupName == null ? "" : data.groupName.trim();
					Message reply = new Message();
					reply.type = Message.CHAT_MESSAGE;
					if (gName.isEmpty()) {
						reply.content = "(Server) Group name cannot be empty.";
						send(reply);
					}
					else if (groups.containsKey(gName)) {
						reply.content = "(Server) Group '" + gName + "' already exists.";
						send(reply);
					}
					else {
						Set<String> members = new HashSet<>();
						members.add(data.sender);
						groups.put(gName, members);
						callback.accept("(Group Created) '" + gName + "' by " + data.sender);
						reply.content = "(Server) Group '" + gName + "' created. You are a member.";
						send(reply);
						broadcastGroupList();
					}
					break;
				}
				case Message.JOIN_GROUP: {
					String gName = data.groupName;
					Message reply = new Message();
					reply.type = Message.CHAT_MESSAGE;
					if (!groups.containsKey(gName)) {
						reply.content = "(Server) Group '" + gName + "' does not exist.";
						send(reply);
					}
					else if (groups.get(gName).contains(data.sender)) {
						reply.content = "(Server) You are already in group '" + gName + "'.";
						send(reply);
					}
					else {
						groups.get(gName).add(data.sender);
						callback.accept("(Group Join) " + data.sender + " joined '" + gName + "'");
						reply.content = "(Server) You joined group '" + gName + "'.";
						send(reply);
						Message notify = new Message();
						notify.type    = Message.CHAT_MESSAGE;
						notify.content = "(Group " + gName + ") " + data.sender + " joined.";
						for (String m : groups.get(gName)) {
							if (!m.equals(data.sender)) {
								ClientThread mt = findClient(m);
								if (mt != null) mt.send(notify);
							}
						}
					}
					break;
				}
				case Message.SEND_GROUP: {
					String gName = data.groupName;
					callback.accept("(Group " + gName + ") " + data.sender + ": " + data.content);
					Message out = new Message();
					out.type = Message.CHAT_MESSAGE;
					if (!groups.containsKey(gName)) {
						out.content = "(Server) Group '" + gName + "' does not exist.";
						send(out);
					}
					else if (!groups.get(gName).contains(data.sender)) {
						out.content = "(Server) You are not a member of '" + gName + "'.";
						send(out);
					}
					else {
						out.content = "(Group " + gName + ") " + data.sender + ": " + data.content;
						for (String m : groups.get(gName)) {
							ClientThread mt = findClient(m);
							if (mt != null) mt.send(out);
						}
					}
					break;
				}
			}
		}

		private void handleDisconnect() {
			clients.remove(this);
			synchronized (Server.this) {
				matchQueue.remove(this);
				rematchQueue.remove(username);
				if (lastOpponentUsername != null) {
					rematchQueue.remove(lastOpponentUsername);
				}
			}
			if (username != null) {
				takenUsernames.remove(username);
				for (Set<String> members : groups.values()){
					members.remove(username);
				}
				if (opponent != null) {
					Message out = new Message();
					out.type = Message.GAME_OVER;
					out.content = "WIN";
					opponent.send(out);
					opponent.opponent = null;
				}
				callback.accept("'" + username + "' disconnected.");
				broadcastUserList();
				broadcastGroupList();
			} else {
				callback.accept("Anonymous client #" + id + " disconnected.");
			}
		}
	}
}
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

	private String ts() {
		return "[" + new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date()) + "] ";
	}

	private void log(String msg) {
		callback.accept(ts() + msg);
	}

	Server(Consumer<Serializable> call) {
		callback = call;
		server = new TheServer();
		server.start();
	}

	private synchronized void broadcastUserList() {
		for (ClientThread ct : clients) {
			Message msg = new Message();
			msg.type = Message.USER_LIST;
			msg.userList = new ArrayList<>(takenUsernames);
			ct.send(msg);
		}
	}

	private synchronized void broadcastGroupList() {
		for (ClientThread ct : clients) {
			Message msg = new Message();
			msg.type = Message.GROUP_LIST;
			msg.groupList = new ArrayList<>(groups.keySet());
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
		log("QUEUE: '" + ct.username + "' entered matchmaking queue (queue size: " + matchQueue.size() + ")");

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
		log("MATCH STARTED: '" + p1.username + "' (LIGHT) vs '" + p2.username + "' (DARK)");

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
		log("GAME OVER: '" + winner.username + "' beat '" + loser.username + "'");
	}

	public class TheServer extends Thread {
		public void run() {
			try (ServerSocket ss = new ServerSocket(6767)) {
				log("Server started. Listening on port 6767...");
				while (true) {
					Socket socket = ss.accept();
					ClientThread ct = new ClientThread(socket, clientCount++);
					clients.add(ct);
					ct.start();
					log("New connection from " + socket.getInetAddress().getHostAddress() + " (client #" + ct.id + ")");
				}
			} catch (Exception e) {
				log("ERROR: Server socket failed: " + e.getMessage());
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
				inStream = new ObjectInputStream(connection.getInputStream());
				connection.setTcpNoDelay(true);
			} catch (Exception e) {
				log("ERROR: Could not open streams for client #" + id); return;
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
						log("SIGN-IN REJECTED: name='" + name + "' (already taken or empty)");
					} else {
						takenUsernames.add(name);
						this.username = name;
						reply.type = Message.SIGN_IN_OK;
						send(reply);
						log("SIGN-IN OK: '" + name + "' (client #" + id + ", total users: " + takenUsernames.size() + ")");
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
					log("QUEUE: '" + username + "' left the matchmaking queue");
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
						log("REMATCH: '" + username + "' requested rematch but opponent is gone");
						break;
					}
					synchronized (Server.this) {
						if (rematchQueue.containsKey(lastOpp) && rematchQueue.get(lastOpp) == oppThread) {
							rematchQueue.remove(lastOpp);
							log("REMATCH STARTED: '" + username + "' and '" + lastOpp + "' are rematching");
							startGame(this, oppThread);
						}
						else {
							rematchQueue.put(username, this);
							Message waiting = new Message();
							waiting.type = Message.WAITING;
							waiting.connectedCount = 1;
							send(waiting);
							log("REMATCH: '" + username + "' is waiting for '" + lastOpp + "' to accept rematch");
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
					log("GAME OVER: '" + username + "' reported game finished");
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
								log("FORFEIT: Notified '" + lastOpponentUsername + "' that opponent forfeited");
							}
						}
						opponent = null;
						lastOpponentUsername = null;
					}
					log("FORFEIT: '" + username + "' forfeited and returned to menu");
					break;
				}
				case Message.GAME_MOVE: {
					if (opponent != null) {
						log("MOVE: '" + username + "' (" + data.fromRow + "," + data.fromCol + ") -> (" + data.toRow + "," + data.toCol + ")");
						opponent.send(data);
					}
					break;
				}
				case Message.SEND_ALL: {
					log("BROADCAST: '" + data.sender + "': " + data.content);
					Message out = new Message();
					out.type = Message.CHAT_MESSAGE;
					out.content = "(All)" + data.sender + ": " + data.content;
					for (ClientThread ct : clients) ct.send(out);
					break;
				}

				case Message.SEND_PRIVATE: {
					log("DM: '" + data.sender + "' -> '" + data.recipient + "': " + data.content);
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
						log("GROUP CREATED: '" + gName + "' by '" + data.sender + "'");
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
						log("GROUP JOIN: '" + data.sender + "' joined '" + gName + "' (" + groups.get(gName).size() + " members)");
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
					log("GROUP MSG [" + gName + "]: '" + data.sender + "': " + data.content);
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
				log("DISCONNECT: '" + username + "' left (users remaining: " + takenUsernames.size() + ")");
				broadcastUserList();
				broadcastGroupList();
			} else {
				log("DISCONNECT: Anonymous client #" + id + " dropped (never signed in)");
			}
		}
	}
}
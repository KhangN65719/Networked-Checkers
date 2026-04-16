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
	private Consumer<Serializable> callback;
	Set<String> takenUsernames = new HashSet<>();
	Map<String, Set<String>> groups = new HashMap<>();

	Server(Consumer<Serializable> call) {
		callback = call;
		server = new TheServer();
		server.start();
	}

	private void broadcastUserList() {
		Message msg = new Message();
		msg.type = Message.USER_LIST;
		msg.userList = new ArrayList<>(takenUsernames);
		for (ClientThread clientThread : clients) clientThread.send(msg);
	}

	private void broadcastGroupList() {
		Message msg = new Message();
		msg.type = Message.GROUP_LIST;
		msg.groupList = new ArrayList<>(groups.keySet());
		for (ClientThread clientThread : clients) {
			clientThread.send(msg);
		}
	}

	private ClientThread findClient(String username) {
		for (ClientThread clientThread : clients) {
			if (username.equals(clientThread.username)) return clientThread;
		}
		return null;
	}

	public class TheServer extends Thread {
		public void run() {
			try (ServerSocket serverSocket = new ServerSocket(5555)) {
				callback.accept("Server is waiting for clients on port 5555...");
				while (true) {
					Socket socket = serverSocket.accept();
					ClientThread clientThread = new ClientThread(socket, clientCount++);
					clients.add(clientThread);
					clientThread.start();
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
		ObjectInputStream inStream;
		ObjectOutputStream outStream;

		ClientThread(Socket socket, int id) {
			this.connection = socket;
			this.id = id;
		}

		synchronized void send(Message msg) {
			try {
				outStream.writeObject(msg); outStream.reset();
			} catch (Exception e) {}
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
					Message receivedMsg = (Message) inStream.readObject();
					handleMessage(receivedMsg);
				} catch (Exception e) {
					handleDisconnect(); break;
				}
			}
		}

		private void handleMessage(Message data) {
			switch (data.type) {

				case Message.SIGN_IN: {
					String requestedName = data.sender == null ? "" : data.sender.trim();
					Message reply = new Message();
					reply.sender = requestedName;
					if (requestedName.isEmpty() || takenUsernames.contains(requestedName)) {
						reply.type = Message.SIGN_IN_FAIL;
						send(reply);
						callback.accept("Sign-in REJECTED for '" + requestedName + "'");
					} else {
						takenUsernames.add(requestedName);
						this.username = requestedName;
						reply.type = Message.SIGN_IN_OK;
						send(reply);
						callback.accept("'" + requestedName + "' connected (client #" + id + ")");
						broadcastUserList();
						broadcastGroupList();
					}
					break;
				}

				case Message.SEND_ALL: {
					callback.accept("[Broadcast] " + data.sender + ": " + data.content);
					Message deliverMsg = new Message();
					deliverMsg.type = Message.CHAT_MESSAGE;
					deliverMsg.content = "(All)" + data.sender + ": " + data.content;
					for (ClientThread clientThread : clients) {
						clientThread.send(deliverMsg);
					}
					break;
				}

				case Message.SEND_PRIVATE: {
					callback.accept("(DM)" + data.sender + " -> " + data.recipient + ": " + data.content);
					ClientThread targetClient = findClient(data.recipient);
					Message deliverMsg = new Message();
					deliverMsg.type = Message.CHAT_MESSAGE;
					if (targetClient != null) {
						deliverMsg.content = "(DM from " + data.sender + "): " + data.content;
						targetClient.send(deliverMsg);
						deliverMsg.content = "(DM to " + data.recipient + "): " + data.content;
						send(deliverMsg);
					} else {
						deliverMsg.content = "(Server) User '" + data.recipient + "' not found.";
						send(deliverMsg);
					}
					break;
				}

				case Message.CREATE_GROUP: {
					String newGroupName = data.groupName == null ? "" : data.groupName.trim();
					Message reply = new Message();
					reply.type = Message.CHAT_MESSAGE;
						if (newGroupName.isEmpty()) {
							reply.content = "(Server) Group name cannot be empty.";
							send(reply);
						} else if (groups.containsKey(newGroupName)) {
							reply.content = "(Server) Group '" + newGroupName + "' already exists.";
							send(reply);
						} else {
							Set<String> members = new HashSet<>();
							members.add(data.sender);
							groups.put(newGroupName, members);
							callback.accept("(Group Created) '" + newGroupName + "' by " + data.sender);
							reply.content = "(Server) Group '" + newGroupName + "' created. You are a member.";
							send(reply);
							broadcastGroupList();
						}
					break;
				}

				case Message.JOIN_GROUP: {
					String targetGroup = data.groupName;
					Message reply = new Message();
					reply.type = Message.CHAT_MESSAGE;
						if (!groups.containsKey(targetGroup)) {
							reply.content = "(Server) Group '" + targetGroup + "' does not exist.";
							send(reply);
						} else if (groups.get(targetGroup).contains(data.sender)) {
							reply.content = "(Server) You are already in group '" + targetGroup + "'.";
							send(reply);
						} else {
							groups.get(targetGroup).add(data.sender);
							callback.accept("(Group Join) " + data.sender + " joined '" + targetGroup + "'");
							reply.content = "(Server) You joined group '" + targetGroup + "'.";
							send(reply);
							Message notifyMsg = new Message();
							notifyMsg.type = Message.CHAT_MESSAGE;
							notifyMsg.content = "(Group " + targetGroup + ") " + data.sender + " joined.";
							for (String memberName : groups.get(targetGroup)) {
								if (!memberName.equals(data.sender)) {
									ClientThread memberThread = findClient(memberName);
									if (memberThread != null) memberThread.send(notifyMsg);
								}
							}
						}
					break;
				}

				case Message.SEND_GROUP: {
					String targetGroup = data.groupName;
					callback.accept("(Group " + targetGroup + ") " + data.sender + ": " + data.content);
					Message deliverMsg = new Message();
					deliverMsg.type = Message.CHAT_MESSAGE;
						if (!groups.containsKey(targetGroup)) {
							deliverMsg.content = "(Server) Group '" + targetGroup + "' does not exist.";
							send(deliverMsg);
						} else if (!groups.get(targetGroup).contains(data.sender)) {
							deliverMsg.content = "(Server) You are not a member of '" + targetGroup + "'.";
							send(deliverMsg);
						} else {
							deliverMsg.content = "(Group " + targetGroup + ") " + data.sender + ": " + data.content;
							for (String memberName : groups.get(targetGroup)) {
								ClientThread memberThread = findClient(memberName);
								if (memberThread != null) memberThread.send(deliverMsg);
							}
						}
					break;
				}
			}
		}

		private void handleDisconnect() {
			clients.remove(this);
			if (username != null) {
				takenUsernames.remove(username);
				for (Set<String> members : groups.values()) members.remove(username);
				callback.accept("'" + username + "' disconnected.");
				broadcastUserList();
				broadcastGroupList();
			} else {
				callback.accept("Anonymous client #" + id + " disconnected.");
			}
		}
	}
}
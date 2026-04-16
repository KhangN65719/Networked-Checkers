import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.util.function.Consumer;

public class Client extends Thread {

	Socket socketClient;
	ObjectOutputStream outStream;
	ObjectInputStream  inStream;
	private Consumer<Serializable> callback;

	Client(Consumer<Serializable> call) { callback = call; }

	@Override
	public void run() {
		try {
			socketClient = new Socket("127.0.0.1", 5555);
			outStream = new ObjectOutputStream(socketClient.getOutputStream());
			inStream  = new ObjectInputStream(socketClient.getInputStream());
			socketClient.setTcpNoDelay(true);
		} catch (Exception e) { e.printStackTrace(); }

		while (true) {
			try {
				Message receivedMsg = (Message) inStream.readObject();
				callback.accept(receivedMsg);
			} catch (Exception e) {
				e.printStackTrace(); break;
			}
		}
	}

	public void send(Message data) {
		try {
			outStream.writeObject(data);
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}
}
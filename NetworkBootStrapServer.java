import java.net.Socket;
import java.io.IOException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;

public class NetworkBootStrapServer implements SocketServer.Handler {
	public interface ClassLoader {
		public int latestRevision();
		public byte[] load(int revision, String name) throws IOException;
	}
	public static class SingleDirectoryClassLoader implements ClassLoader {
		public SingleDirectoryClassLoader(File directory) {
			_directory= directory;
		}
		public int latestRevision() {
			return 0;
		}
		public byte[] load(int revision, String name) throws IOException {
			File					file;
			FileInputStream			in;
			ByteArrayOutputStream	out;
			byte[]					buffer;
			int						size;

			if(0 != revision) {
				return new byte[0]; // requesting an unknown revision
			}
			file= new File(_directory, name+".class");
			if(!file.isFile()) {
				String[]	elements= name.split(".");

				elements[elements.length - 1]= elements[elements.length - 1]+".class";
				file= _directory;
				for(String element : elements) {
					file= new File(file, element);
				}
			}
			if(!file.isFile()) {
				// try in jar files in zip files, etc.
			}
			if(!file.isFile()) {
				return new byte[0];
			}
			in= new FileInputStream(file);
			out= new ByteArrayOutputStream();
			buffer= new byte[4096];
			do	{
				size= in.read(buffer);
				if(size > 0) {
					out.write(buffer, 0, size);
				}
			} while(size >= 0);
			in.close();
			out.close();
			return out.toByteArray();
		}
		private File	_directory;
	}
	public NetworkBootStrapServer(int port, ClassLoader loader) throws IOException {
		_port= port;
		_loader= loader;
		_server= new SocketServer(_port, this);
	}
	public void handle(SocketServer server, Socket connection) throws IOException {
		DataInputStream		in= new DataInputStream(connection.getInputStream());
		DataOutputStream	out= new DataOutputStream(connection.getOutputStream());
		boolean				done= false;

		while(!done) {
			String	command= in.readUTF();

			if(command.equals("NetworkBootStrap")) {
				int	protocol= in.readInt();
				int	requestedRevision= in.readInt();

				if(kNetworkBootStrapProtocolVersion != protocol) {
					throw new IOException("NetworkBootStrap protocol version mismatch "+kNetworkBootStrapProtocolVersion+" vs "+protocol);
				}
				out.writeInt(_loader.latestRevision());
			} else if(command.equals("revision?")) {
				out.writeInt(_loader.latestRevision());
			} else if(command.equals("load")) {
				String	name= in.readUTF();
				int		revision= in.readInt();
				byte[]	data= _loader.load(revision, name);

				out.writeInt(data.length);
				out.write(data);
			} else if(command.equals("quit")) {
				out.writeUTF("quit");
				done= true;
			}
		}
	}
	public void log(int level, String message) {
		System.out.println(""+level+": "+message);
	}
	public void log(Exception exception) {
		exception.printStackTrace();
	}
	static private final int		kNetworkBootStrapProtocolVersion= 1;
	private int						_port;
	private SocketServer			_server;
	private ClassLoader				_loader;
	public static void main(String... args) {
		int						port= 6277;		// keypad NBSP
		java.util.ArrayList<String>		argsList= new java.util.ArrayList<String>(args.length);
		String[]				argsArray;
		String					last= null;
		NetworkBootStrapServer	server;

		for(String arg : args) {
			if(null != last) {
				if(last.equals("-port")) {
					port= Integer.parseInt(arg);
					last= null;
				} else {
					argsList.add(last);
					last= arg;
				}
			} else {
				last= arg;
			}
		}
		if(null != last) {
			argsList.add(last);
		}
		if(argsList.size() != 1) {
			System.err.println("USAGE: java NetworkBootStrapServer [-port #] <path to class dir>");
			System.exit(1);
		}
		try	{
			server= new NetworkBootStrapServer(port, new SingleDirectoryClassLoader(new File(argsList.get(0))));
		} catch(IOException exception) {
			exception.printStackTrace();
		}
	}
}

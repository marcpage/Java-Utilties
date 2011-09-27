import java.net.Socket;
import java.io.IOException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;

/** Serve up Java classes to a client.
	It is recommended that you implement your own custom class loader to handle revisions, etc.
	@see NetworkBootStrapClient
*/
public class NetworkBootStrapServer implements SocketServer.Handler {
	/** Used to find the classes to serve up to the client.
	*/
	public interface ClassLoader {
		/** Gets the latest class set revision number.
			@return	Return the latest fully installed class set.
			@see NetworkBootStrapClient
		*/
		public int latestRevision();
		/** Loads the class data from a class set revision.
			@param revision	The class set revision to load.
			@param name		The name of the class to load.
			@return			The contents of the class.
			@throws IOException	on io error.
		*/
		public byte[] load(int revision, String name) throws IOException;
		/** How to handle logging of events.
			@param level	0 = vital information, 100 = trivial information
			@param message	The message to log
		*/
		public void log(int level, String message);
		/** Log that an exception occurred.
			@param exception	The exception the occurred.
		*/
		public void log(Exception exception);
	}
	/** An example class loader.
		Will probably not work for inner classes or packaged classes.
		Does not look in jars or zips.
		Does not handle multiple class set revisions.
	*/
	public static class SingleDirectoryClassLoader implements ClassLoader {
		/** Loads classes from a given directory.
			@param directory	The directory to load the classes from.
		*/
		public SingleDirectoryClassLoader(File directory) {
			_directory= directory;
		}
		/** The latest revision is always 0.
			@return 0
		*/
		public int latestRevision() {
			return 0;
		}
		/** Loads the bytecode for a class.
			First tries replacing dot (.) with dollar ($) and appending .class and loading that file.
			Then if that doesn't exist, tries turning dot (.) separated items into directories.
			Inner classes in packages will definitely not work.
			If the file is found, loads the contents and returns them.
			@param revision	Must be 0.
			@param name		The name of the class.
			@return			The bytecode for the requested class.
			@throws IOException	on io error.
		*/
		public byte[] load(int revision, String name) throws IOException {
			File					file;
			FileInputStream			in;
			ByteArrayOutputStream	out;
			byte[]					buffer;
			int						size;

			if(0 != revision) {
				return new byte[0]; // requesting an unknown revision
			}
			file= new File(_directory, name.replace(".","$")+".class");
			if(!file.isFile()) {
				String[]	elements= name.split(".");

				if(elements.length > 1) {
					elements[elements.length - 1]= elements[elements.length - 1]+".class";
					file= _directory;
					for(String element : elements) {
						file= new File(file, element);
					}
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
		/** Prints the message and level to stdout.
			@param level	0 = vital information, 100 = trivial information
			@param message	The message to log
		*/
		public void log(int level, String message) {
			System.out.println(""+level+": "+message);
		}
		/** Calls printStackTrace() on the exception.
			@param exception	The exception the occurred.
			@see java.lang.Exception.printStackTrace
		*/
		public void log(Exception exception) {
			exception.printStackTrace();
		}
		/** The directory to search for classes. */
		private File	_directory;
	}
	/** Start a new server.
		@param port		The port to listen on.
		@param loader	The mechanism to find classes in class set revisions.
		@throws IOException	on io error.
	*/
	public NetworkBootStrapServer(int port, ClassLoader loader) throws IOException {
		_port= port;
		_loader= loader;
		_server= new SocketServer(_port, this);
	}
	/** Handles an incoming connection.
		Loops on reading commands and responding to them until quit is sent or an exception.
		@param server		Should be the same as <code>_server</code>.
		@param connection	The connection to the client.
		@throws IOException on io error or if the protocol of the client is not compatible.
	*/
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
	/** Log messages.
		Forward log messages to the loader.
		@param level	0 = vital information, 100 = trivial information
		@param message	The message to log
	*/
	public void log(int level, String message) {
		_loader.log(level, message);
	}
	/** Log that an exception occurred.
		Forward log messages to the loader.
		@param exception	The exception the occurred.
	*/
	public void log(Exception exception) {
		_loader.log(exception);
	}
	/** The protocol version. Must match what the client reports. */
	static private final int		kNetworkBootStrapProtocolVersion= 1;
	/** The port to listen on. */
	private int						_port;
	/** The server that is handling the connections. */
	private SocketServer			_server;
	/** The class loading mechanism. */
	private ClassLoader				_loader;
	/** Test. Serves up classes in one directory (with no revisions).
		@param args	The arguments are:<ul>
						<li><b>-port</b> <i>Optional</i> The port to connect to listen on.
											Defaults to <i>6277</i> (telephone keypad NBSP).
					</ul>
					Only one other argument should be passed, the directory that contains the classes
						to load.
	*/
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

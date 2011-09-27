import java.net.Socket;
import java.util.HashMap;
import java.io.IOException;
import java.util.ArrayList;
import java.io.InputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.jar.Manifest;
import java.util.jar.Attributes;
import java.net.URISyntaxException;
import java.util.jar.JarFile;

/** Minimal class to load the rest of a project across the network.
	<p><b>Class Set Revisions</b><br>
	The <code>revision</code> is the revision, or version, or classes to load from the server.
	This is to support multiple simultaneous revisions used by different clients. When a new revision
	is available, a client can detect that and decide to upgrade. If it is in the middle of an
	uninterruptable operation, it can keep using the old revision until it is ready to upgrade.
	<p><b>Protocol</b>
	<ul>
		<li><b>Send</b>:	"revision?"<br>
			<b>Receive</b>:	(revision Int)

		<li><b>Send</b>:	"NetworkBootStrap"(protocol version Int)(requested revision Int)<br>
			<b>Receive</b>:	(revision assigned Int)

		<li><b>Send</b>:	"load""(name)"(revision Int)<br>
			<b>Receive</b>:	(class size in bytes Int)[(class bytes)]

		<li><b>Send</b>:	"quit"<br>
			<b>Receive</b>:	"quit"
	</ul>
	<b>TODO</b>
	<ul>
		<li>Look up parameters in the manifest so they don't need to be passed on the command line.
	</ul>
	@see NetworkBootStrapServer
*/
public class NetworkBootStrapClient extends ClassLoader {
	/** Initialize the connection to a NetworkBootStrapServer.
		@param server				The server DNS name or IP address as a string.
		@param port					The server port to connect to.
		@param revisionRequested	The class set revision.
										0 == latest,
										negative == undefined behavior,
										positive == revision number.
		@throws IOException			On io error
	*/
	public NetworkBootStrapClient(String server, int port, int revisionRequested) throws IOException {
		_init(server, port, revisionRequested);
	}
	/** Find a class of the given name.
		Override of ClassLoader implementation.
		@param name	The name of the class to load.
		@return		The class requested.
		@throws ClassNotFoundException	If the class is not a System class
											or found in the current revision.
	*/
	public Class findClass(String name) throws ClassNotFoundException {
		return _getClass(name);
	}
	/** Requests a disconnect from the server.
		@return	<code>true</code> if the server responded to the quit <code>false</code> if the server
						rejected the quit.
		@throws IOException			On io error
	*/
	public synchronized boolean shutdown() throws IOException {
		String	quit;

		_out.writeUTF("quit");
		quit= _in.readUTF();
		return quit.equals("quit");
	}
	/** Gets the class set revision being used.
		@return	The revision number of the actual class set we are using (not just what we requested).
	*/
	public int revision() {
		return _revision;
	}
	/** Determines if we are using the most recent revision of the classes.
		Meant to be used to determine if we should restart to use a newer revision of the classes.
		@return	<code>true</code> if there is a newer revision of the classes than the version
					we are currently using. <code>false</code> if we are up to date.
		@throws IOException			On io error
	*/
	public synchronized boolean newVersionAvailable() throws IOException {
		if(_newVersionAvailable) {
			int	newest;

			_out.writeUTF("revision?");
			newest= _in.readInt();
			_newVersionAvailable= (_revision == newest);
		}
		return _newVersionAvailable;
	}
	/** Starts the connection to the NetworkBootStrapServer and sends the hello message.
		@param server				DNS or IP string of the server
		@param port					port to connect to on the server
		@param revisionRequested	The revision number of the class set to use, or 0 for latest.
	*/
	private void _init(String server, int port, int revisionRequested) throws IOException {
		_server= server;
		_port= port;
		_connection= new Socket(_server, _port);
		_in= new DataInputStream(_connection.getInputStream());
		_out= new DataOutputStream(_connection.getOutputStream());
		_classes = new HashMap<String,Class<?>>();
		_newVersionAvailable= true;
		if(revisionRequested <= 0) {
			revisionRequested= 0;
		}
		_out.writeUTF("NetworkBootStrap");
		_out.writeInt(kNetworkBootStrapProtocolVersion);
		_out.writeInt(revisionRequested);
		_revision= _in.readInt();
	}
	/** Lookup the class name in all locations.
		First looks in the HashMap cache of classes already seen.
		Then looks in the System for the class.
		Finally loads the class from the NetworkBootStrapServer.
		@param name	The name of the class to load
		@return		The found class
		@throws ClassNotFoundException	If the class is not found.
	*/
	private synchronized Class _getClass(String name) throws ClassNotFoundException {
		Class<?>	result= _classes.get(name);

		if(null != result) {
			return result;
		}
		try	{
			result= super.findSystemClass(name);
			return result;
		} catch(ClassNotFoundException exception) {
		}
		return _loadClassFromServer(name);
	}
	/** Loads the class from the NetworkBootStrapServer.
		@param name	The name of the class to load
		@return		The found class
		@throws ClassNotFoundException	If the class is not found.
	*/
	private Class _loadClassFromServer(String name) throws ClassNotFoundException {
		int		classSize;
		byte[]	data;
		Class	result;

		try	{
			_out.writeUTF("load");
			_out.writeUTF(name);
			_out.writeInt(_revision);
			classSize= _in.readInt();
			if(0 == classSize) {
				throw new ClassNotFoundException(name);
			}
			data= new byte[classSize];
			_in.readFully(data);
			result= defineClass(name, data, 0, data.length);
			_classes.put(name, result);
		} catch(IOException exception) {
			throw new ClassNotFoundException(exception.toString());
		}
		return result;
	}
	/** The protocol version this class implements. */
	static private final int			kNetworkBootStrapProtocolVersion= 1;
	/** The revision of the class set that we are currently using. */
	private int							_revision;
	/** The server we're connected to. */
	private String						_server;
	/** The port on the server we're connected to. */
	private int							_port;
	/** The socket we're connected on. */
	private Socket						_connection;
	/** The cache of classes we've already loaded from _server. */
    private HashMap<String,Class<?>>	_classes;
    /** The input stream from _server. */
    private DataInputStream				_in;
    /** The output stream to _server. */
    private DataOutputStream			_out;
    /** Once we determine we're out of date, we just cache that and don't ask again. */
    private boolean						_newVersionAvailable;

	/** Starts a connection to a NetworkBootStrapServer and starts a class from it.
		@param args	The arguments are:<ul>
						<li><b>-server</b> <i>Optional</i> The DNS or IP address of the NetworkBootStrapServer.
											Defaults to <i>localhost</i>.
						<li><b>-port</b> <i>Optional</i> The port to connect to on the NetworkBootStrapServer.
											Defaults to <i>6277</i> (telephone keypad NBSP).
						<li><b>-class</b> <i>Optional</i> The full name of the class to load from NetworkBootStrapServer
											as the launcher class.
											Defaults to <i>Start</i>.
						<li><b>-method</b> <i>Optional</i> The name of the static method to call on the launcher class.
											The prototype is: <code>int start(NetworkBootStrapClient,String...)</code>
											Defaults to <i>start</i>.
						<li><b>-revision</b> <i>Optional</i> The revision of the class set to use.
											Defaults to <i>0</i> (latest).
					</ul>
					Any other arguments are passed to the static class start method.
	*/
	public static void main(String... args) {
		String					server= "localhost";
		int						port= 6277;		// keypad NBSP
		int						revision= 0;	// 0 == latest
		String					className= "Start";
		String					methodName= "start";
		ArrayList<String>		argsList= new ArrayList<String>(args.length);
		String[]				argsArray;
		String					last= null;
		NetworkBootStrapClient	client= null;

		try	{
			String		jarPath= NetworkBootStrapClient.class.getProtectionDomain().getCodeSource().getLocation().toURI().getRawPath();
			JarFile		jar= new JarFile(jarPath);
			Manifest	jarManifest= jar.getManifest();
			Attributes	manifestData= jarManifest.getMainAttributes();
			String					value;

			value= manifestData.getValue("server");
			if(null != value) {
				server= value;
			}
			value= manifestData.getValue("class");
			if(null != value) {
				className= value;
			}
			value= manifestData.getValue("method");
			if(null != value) {
				methodName= value;
			}
			value= manifestData.getValue("port");
			if(null != value) {
				port= Integer.parseInt(value);
			}
			value= manifestData.getValue("revision");
			if(null != value) {
				revision= Integer.parseInt(value);
			}
		} catch(IOException exception7) {
			exception7.printStackTrace();
		} catch(URISyntaxException exception8) {
			exception8.printStackTrace();
		}
		for(String arg : args) {
			if(null != last) {
				if(last.equals("-server")) {
					server= arg;
					last= null;
				} else if(last.equals("-class")) {
					className= arg;
					last= null;
				} else if(last.equals("-method")) {
					methodName= arg;
					last= null;
				} else if(last.equals("-port")) {
					port= Integer.parseInt(arg);
					last= null;
				} else if(last.equals("-revision")) {
					revision= Integer.parseInt(arg);
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
		argsArray= argsList.toArray(new String[argsList.size()]);
		try	{
			while(revision >= 0) {
				Class<?>	start;
				Method		go;
				Object		result;

				client= new NetworkBootStrapClient(server, port, revision);
				start= client.findClass(className);
				go= start.getDeclaredMethod(methodName, String[].class);
				result= go.invoke(null, client, (Object)argsArray); // static public int start(NetworkBootStrapClient, String...);
				revision= ((Integer)result).intValue();
			}
		} catch(IOException exception) {
			exception.printStackTrace();
		} catch(ClassNotFoundException exception2) {
			exception2.printStackTrace();
		} catch(NoSuchMethodException exception3) {
			exception3.printStackTrace();
		} catch(IllegalAccessException exception4) {
			exception4.printStackTrace();
		} catch(InvocationTargetException exception5) {
			exception5.printStackTrace();
		} finally {
			try	{
				if(null != client) {
					client.shutdown();
				}
			} catch(IOException exception6) {
				exception6.printStackTrace();
			}
		}
	}
}

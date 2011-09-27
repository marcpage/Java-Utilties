import java.net.Socket;
import java.util.HashMap;
import java.io.IOException;
import java.util.ArrayList;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

/**
	Protocol
	<ul>
		<li><b>Send</b>:	"revision?"<br>
			<b>Receive</b>:	<revision Int>

		<li><b>Send</b>:	"NetworkBootStrap"<protocol version Int><requested revision Int>
			<b>Receive</b>:	<revision assigned Int>

		<li><b>Send</b>:	"load""<name>"<revision Int>
			<b>Receive</b>:	<class size in bytes Int>[<class bytes>]

		<li><b>Send</b>:	"quit"
			<b>Receive</b>:	"quit"
	</ul>
*/
public class NetworkBootStrapClient extends ClassLoader {
	public NetworkBootStrapClient(String server, int port, int revisionRequested) throws IOException {
		_init(server, port, revisionRequested);
	}
	public Class findClass(String name) throws ClassNotFoundException {
		return _getClass(name);
	}
	public synchronized boolean shutdown() throws IOException {
		String	quit;

		_out.writeUTF("quit");
		quit= _in.readUTF();
		return quit.equals("quit");
	}
	public synchronized boolean newVersionAvailable() throws IOException {
		if(_newVersionAvailable) {
			int	newest;

			_out.writeUTF("revision?");
			newest= _in.readInt();
			_newVersionAvailable= (_revision == newest);
		}
		return _newVersionAvailable;
	}
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
	static private final int			kNetworkBootStrapProtocolVersion= 1;
	private int							_revision;
	private String						_server;
	private int							_port;
	private Socket						_connection;
    private HashMap<String,Class<?>>	_classes;
    private DataInputStream				_in;
    private DataOutputStream			_out;
    private boolean						_newVersionAvailable;

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

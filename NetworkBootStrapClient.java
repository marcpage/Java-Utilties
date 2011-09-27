import java.net.Socket;
import java.util.HashMap;
import java.io.DataInputStream;
import java.io.DataOutputStream;

public class NetworkBootStrapClient extends ClassLoader {
	public NetworkBootStreapClient(String server, int port, String className, int revisionRequested) {
		_init(server, port, className, revisionRequested);
	}
	public Class findClass(String name) throws ClassNotFoundException {
		return _getClass(name);
	}
	public synchronized boolean newVersionAvailable() {
		if(_newVersionAvailable) {
			int	newest;
			
			_out.writeUTF("revision?");
			newest= _in.readInt();
			_newVersionAvailable= (_revsion == newest);
		}
		return _newVersionAvailable;
	}
	private void _init(String server, int port, String className, int revisionRequested) {
		_server= server;
		_port= port;
		_connection= new Socket(_server, _port);
		_in= new DataInputStream(_connection.getInputStream());
		_out= new DataOutputStream(_connection.getOutputStream());
		_classes = new HashMap<String,Class>();
		_boot= className;
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
		Class	result= (Class)_classes.get(name);
		
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
		
		// use _in.available() to see if we are shutting down
		_out.writeUTF("load");
		_out.writeUTF(name);
		_out.writeInt(_revision);
		classSize= _in.readInt();
		data= new byte[classSize];
		_in.readFully(data);
		result= defineClass(data, 0, data.length);
		_classes.set(name, result);
		return result;
	}
	static private final int		kNetworkBootStrapProtocolVersion= 1;
	private int						_revision;
	private String					_server;
	private int						_port;
	private String					_boot;
	private Socket					_connection;
    private HashMap<String,Class>	_classes;
    private DataInputStream			_in;
    private DataOutpuStream			_out;
    private boolean					_newVersionAvailable;
    

	public static void main(String... args) {
	}
}

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
	AFP versions 3.0 and greater rely exclusively on TCP/IP (port 548 or 427)
*/
public class SocketProxy implements SocketServer.Handler {
	public SocketProxy(String host, int port) {
		_host= host;
		_port= port;
	}
	public void log(Exception exception) {
		exception.printStackTrace();
		log(0, exception.toString());
	}
	public void log(int level, String message) {
		System.err.println("LOG "+level+": "+message);
	}
	public void handle(SocketServer controller, Socket connection) throws IOException {
		Socket			server= new Socket(_host, _port);
		InputStream		clientIn= connection.getInputStream();
		OutputStream	clientOut= connection.getOutputStream();
		InputStream		serverIn= server.getInputStream();
		OutputStream	serverOut= server.getOutputStream();
		_Pipe			requests= new _Pipe(clientIn, serverOut, "REQUEST", this);
		_Pipe			responses= new _Pipe(serverIn, clientOut, "RESPONSE", this);

		while(!requests.done() && !responses.done()) {
			try	{
				Thread.sleep(100 /* milliseconds */ );
			} catch(InterruptedException e) {
				log(e);
			}
		}
		if(requests.exception() != null) {
			log(requests.exception());
			log(10, "REQUEST exception");
		}
		if(responses.exception() != null) {
			log(responses.exception());
			log(10, "RESPONSE exception");
		}
		if(requests.done()) {
			log(10, "REQUEST closed");
		}
		if(responses.done()) {
			log(10, "RESPONSE closed");
		}
		serverOut.close();
		clientOut.close();
		serverIn.close();
		clientIn.close();
	}
	private class _Pipe extends Thread {
		public _Pipe(InputStream in, OutputStream out, String name, SocketServer.Handler log) {
			_in= in;
			_out= out;
			_name= name;
			_log= log;
			_exception= null;
			start();
		}
		public IOException exception() {
			return _exception;
		}
		boolean done() {
			return (null == _in) && (null == _out);
		}
		public void run() {
			try	{
				int	b= _in.read();

				while( -1 != b ) {
					_out.write(b);
					_log.log(20, _name+": "+b+"("+((char)b)+")");
					b= _in.read();
				}
			} catch(IOException e) {
				_exception= e;
			}
			_in= null;
			_out= null;
		}
		private InputStream				_in;
		private OutputStream			_out;
		private String					_name;
		private SocketServer.Handler	_log;
		private IOException				_exception;
	}
	private String	_host;
	private int		_port;

	public static void main(String... args) {
		try	{
			new SocketServer(Integer.parseInt(args[0]), new SocketProxy(args[1], Integer.parseInt(args[2])));
		} catch(IOException e) {
			e.printStackTrace();
		}
	}
}

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.net.Socket;
import java.net.ServerSocket;
import java.util.Vector;

/** Multithreaded, socket based server.
	<p><b>TODO</b>
	<ul>
		<li>Add a constructor that takes expectedConcurrentConnections (current constructor passes _kAverageWorkerThreads)
	</ul>
*/
public class SocketServer extends Thread {
	/** The interface to handle a connection.
	*/
	public interface Handler {
		/** The method that will be called when a connection is made.
			The method will be called on its own, independent thread.
			@param server		The object that is serving up the request
			@param connection	The socket of the connection that was made
			@throws IOException	If there is an error
		*/
		public void handle(SocketServer server, Socket connection) throws IOException;
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
	/** Helper function to read from an input stream with a timeout.
		No maximum to the data to read.
		Use default buffer size.
		@param in	The stream to read from
		@param timeoutInMilliseconds	The timeout
		@return		The data read
		@throws IOException	If there was a problem
	*/
	public static byte[] read(InputStream in, int timeoutInMilliseconds) throws IOException {
		return read(in, timeoutInMilliseconds, -1, -1);
	}
	/** Helper function to read from an input stream with a timeout.
		@param in	The stream to read from
		@param timeoutInMilliseconds	The timeout
		@param maxData	The maximum number of bytes to read (-1 for unlimited)
		@param bufferSize	The number of bytes to attempt to read at a time (-1 for default 4k)
		@return		The data read
		@throws IOException	If there was a problem
	*/
	public static byte[] read(InputStream in, int timeoutInMilliseconds, int maxData, int bufferSize) throws IOException {
		int						retryCount= timeoutInMilliseconds / 100;
		ByteArrayOutputStream	commandInfo= new ByteArrayOutputStream();
		byte					buffer[];

		if(bufferSize < 0) {
			bufferSize= 4096;
		}
		if( (maxData > 0) && (bufferSize > maxData) ) {
			bufferSize= maxData;
		}
		buffer= new byte[bufferSize];
		if(retryCount < 1) {
			retryCount= 1;
		}
		while(retryCount > 0) {
			while( (in.available() == 0) && (retryCount > 0) ) {
				try	{
					Thread.sleep(100);
				} catch(InterruptedException e) {
					// ignore
				}
				retryCount-= 1;
			}
			int	available= in.available();

			if( (maxData >= 0) && (available > maxData) ) {
				available= maxData;
			}
			if(available > 0) {
				int	amountRead= in.read(buffer, 0, available);

				commandInfo.write(buffer, 0, amountRead);
				maxData-= amountRead;
				if(maxData == 0) {
					break;
				}
			}
		}
		return commandInfo.toByteArray();
	}
	/** Listen on a port and hand off connections to a handler.
		@param port		The port to listen on
		@param instance	The object to handle connections.
	*/
	public SocketServer(int port, Handler instance) throws IOException {
		_done= false;
		_instance= instance;
		_port= port;
		_log(100, "Listening on port "+_port);
		_idleWorkers= new Vector<Worker>(_kAverageWorkerThreads);
		_log(100, "Average simultaneous connections expected= "+_kAverageWorkerThreads);
		_listen= new ServerSocket(port);
		start();
	}
	/** The port we are listening on.
		@return	The port we are listening on
	*/
	public int port() {
		return _port;
	}
	/** Requests the server to shutdown.
	*/
	public void terminate() {
		if(!_done) {
			_log(100, "Terminating main thread");
			_done= true;
			try	{
				_listen.close();
				_log(100, "Successfully terminated main thread");
			} catch(IOException e) {
				_log(e);
			}
		}
	}
	/** The server thread.
	*/
	public void run() {
		while(!_done) {
			Socket	connection= null;

			try	{
				_log(100, "Waiting for connection");
				connection= _listen.accept();
				if(_idleWorkers.isEmpty()) {
					_log(100, "Creating new worker thread");
					new Worker(connection);
				} else {
					_log(100, "Recycling worker thread");
					_idleWorkers.remove(0).start(connection);
				}
				synchronized(this) {
					_active+= 1;
				}
				_log(100, "Active threads: "+_active);
			} catch(Exception e) {
				if(!_done) {
					if(null != connection) {
						_log(0, connection);
						try {
							connection.close();
						} catch(IOException e2) {
							_log(e2);
						}
					}
					_log(e);
					terminate();
				}
			}
		}
		_log(100, "Main server thread shutting down, port "+_port);
		while(_active > 0) {
			_log(100, "Waiting for workers to be recycled");
			try	{
				Thread.sleep(1000);
			} catch(InterruptedException e) {
				_log(e);
			}
		}
		for(Worker w : _idleWorkers) {
			w.terminate();
		}
		_log(100, "Main server thread shut down, port "+_port);
	}
	/** Recycles worker objects.
		@param worker	The worker object to recycle.
	*/
	private synchronized void _recycle(Worker worker) {
		_log(100, "Recycling worker "+worker);
		_active-= 1;
		_idleWorkers.add(worker);
		_log(100, "Active threads: "+_active);
	}
	/** Logs Socket to the Handler log method.
		@param level	The message level
		@param message	The socket connection to log
	*/
	private synchronized void _log(int level, Socket message) {
		_log(level, message.toString());
	}
	/** Logs that an exception occurred.
		@param message	The exception that occurred.
	*/
	private synchronized void _log(Exception message) {
		_instance.log(message);
	}
	/** Logs a message to the handler.
		@param level	The message level (0 = critical to report, 100 = trivial to report)
		@param message	The message to log
	*/
	private synchronized void _log(int level, String message) {
		_instance.log(level, message);
	}
	/** A thread to handle each connection.
		Each Handler call will be on a unique Worker.
	*/
	private class Worker extends Thread {
		/** Starts a worker thread on a connection.
			@param connection	The connection to start working on
		*/
		public Worker(Socket connection) {
			_connection= connection;
			_done= false;
			start();
		}
		/** Terminates this worker.
			After calling, this worker can no longer be used.
		*/
		public void terminate() {
			_log(100, "Terminating worker "+this);
			_done= true;
			synchronized(this) {
				notify();
			}
		}
		/** Restart a recycled worker on a new connection.
			@param connection	The connection to start working on
		*/
		public void start(Socket connection) throws Exception {
			if(null != _connection) {
				throw new Exception("Worker still working");
			}
			_connection= connection;
			synchronized(this) {
				notify();
			}
		}
		/** The thread to handle the connection.
		*/
		public void run() {
			_log(100, "Starting thread "+this);
			while(!_done) {
				_log(100, "Starting a handle connection "+_connection);
				try	{
					_instance.handle(SocketServer.this, _connection);
				} catch(Exception e) {
					_log(0, _connection);
					_log(e);
				}
				_log(100, "Done handling connection "+_connection);
				try	{
					_connection.close();
				} catch(Exception e) {
					_log(e);
				}
				_connection= null;
				_recycle(this);
				synchronized(this) {
					try {
						_log(100, "Waiting in Worker "+this);
						wait();
					} catch(InterruptedException e) {
						_log(e);
					}
					_log(100, "Done waiting in Worker "+this);
				}
			}
			_log(100, "Shutting down thread "+this);
		}
		/** The connection we are currently handling. */
		private Socket 	_connection;
		/** Are we shutting down. */
		private boolean	_done;
	}
	/** How many concurrent active connections we are expecting */
	static private int		_kAverageWorkerThreads= 4;
	/** The port we are listening on */
	private int				_port;
	/** Keep track of the number of active worker threads */
	private int				_active;
	/** The socket we are listening on */
	private ServerSocket	_listen;
	/** The connection handler */
	private Handler			_instance;
	/** The pool of Worker threads not currently in use */
	private Vector<Worker>	_idleWorkers;
	/** Are we shutting down */
	private boolean			_done;
	/** Test class that echos anything sent to it.
	*/
	private static class Echo implements Handler {
		/** New Echo handler.
		*/
		public Echo() {
			System.out.println("Connect and send the letter 'd' and the server will shut down after all the connections are done");
		}
		/* Log exceptions.
			@param exception the exception to log
		*/
		public void log(Exception exception) {
			exception.printStackTrace();
			log(0, exception.toString());
		}
		/** Echos back everything being sent to it (and logs it).
			To quit, send the letter 'd'.
			@param server		The object that is serving up the request
			@param connection	The socket of the connection that was made
			@throws IOException	If there is an error
		*/
		public void handle(SocketServer server, Socket connection) throws IOException {
			System.out.println("Handling new connection on port "+server.port()+" from "+connection);
			InputStream		in= connection.getInputStream();
			OutputStream	out= connection.getOutputStream();

			while(true) {
				int	oneByte= in.read();

				log(100, "Echoing byte "+oneByte);
				if(-1 == oneByte) {
					break;
				}
				out.write(oneByte);
				if(oneByte == 'd') {
					server.terminate();
					break;
				}
			}
			System.out.println("Done handling new connection on port "+server.port()+" from "+connection);
		}
		/** Logs messages to stderr.
		@param level	The message level (0 = critical to report, 100 = trivial to report)
		@param message	The message to log
		*/
		public void log(int level, String message) {
			System.err.println("LOG "+level+": "+message);
		}
	}
	/** Test.
		Creats an echo server on port specified by 1st argument passed on command line.
		To kill the server, send it the letter 'd'.
	*/
	public static void main(String... args) {
		try	{
			new SocketServer(Integer.parseInt(args[0]), new Echo());
		} catch(IOException exception) {
			System.err.println(exception);
		}
	}
}

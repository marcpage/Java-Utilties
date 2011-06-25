import java.io.InputStream;
import java.io.OutputStream;

/** Drains an input stream to an output stream.
	Upon construction, starts a new thread that starts draining data from an
		input stream and writing it to an output stream.
	Features timeout, maximum bytes transferred, auto-close in/out, and transfer
		buffer size control.
	<b>TODO</b>
	<ul>
		<li>Write tests
	</ul>
*/
public class Pipe extends Thread {
	/** Creates a default Pipe.
		No Timeout.
		No maxmimum number of bytes to move.
		Use the default buffer size.
		Do not close input when done.
		Do not close output when done.
		@param in	The input stream to read from
		@param out	The output stream to send the data to
	*/
	public Pipe(InputStream in, OutputStream out) {
		_init(in, out, -1, -1, -1, false, false);
	}
	/** Creates a Pipe, optionally closing the input, the output or both.
		No Timeout.
		No maxmimum number of bytes to move.
		Use the default buffer size.
		@param in				The input stream to read from
		@param out				The output stream to send the data to
		@param closeInWhenDone	If true, <code>in</code> will have close() called in it when done.
		@param closeOutWhenDone	If true, <code>out</code> will have close() called in it when done.
	*/
	public Pipe(InputStream in, OutputStream out, boolean closeInWhenDone, boolean closeOutWhenDone) {
		_init(in, out, -1, -1, -1, closeInWhenDone, closeOutWhenDone);
	}
	/** Creates a Pipe, optionally closing the input, the output or both.
		Do not close input when done.
		Do not close output when done.
		@param in						The input stream to read from
		@param out						The output stream to send the data to
		@param timeoutInMilliseconds	The number of milliseconds to wait for data from <code>in</code> before
											ending the pipe
		@param bufferSize				The number of bytes to attempt to read at a time
		@param maxBytes					The maximum number of bytes to transfer fron <code>in</code> to <code>out</code>
	*/
	public Pipe(InputStream in, OutputStream out, int timeoutInMilliseconds, int bufferSize, int maxBytes) {
		_init(in, out, timeoutInMilliseconds, bufferSize, maxBytes, false, false);
	}
	/** Creates a Pipe, optionally closing the input, the output or both.
		No maxmimum number of bytes to move.
		Use the default buffer size.
		@param in						The input stream to read from
		@param out						The output stream to send the data to
		@param timeoutInMilliseconds	The number of milliseconds to wait for data from <code>in</code> before
											ending the pipe
		@param closeInWhenDone			If true, <code>in</code> will have close() called in it when done.
		@param closeOutWhenDone			If true, <code>out</code> will have close() called in it when done.
	*/
	public Pipe(InputStream in, OutputStream out, int timeoutInMilliseconds, boolean closeInWhenDone, boolean closeOutWhenDone) {
		_init(in, out, timeoutInMilliseconds, -1, -1, closeInWhenDone, closeOutWhenDone);
	}
	/** Creates a Pipe, optionally closing the input, the output or both.
		@param in						The input stream to read from
		@param out						The output stream to send the data to
		@param timeoutInMilliseconds	The number of milliseconds to wait for data from <code>in</code> before
											ending the pipe
		@param bufferSize				The number of bytes to attempt to read at a time
		@param maxBytes					The maximum number of bytes to transfer fron <code>in</code> to <code>out</code>
		@param closeInWhenDone			If true, <code>in</code> will have close() called in it when done.
		@param closeOutWhenDone			If true, <code>out</code> will have close() called in it when done.
	*/
	public Pipe(InputStream in, OutputStream out, int timeoutInMilliseconds, int bufferSize, int maxBytes, boolean closeInWhenDone, boolean closeOutWhenDone) {
		_init(in, out, timeoutInMilliseconds, bufferSize, maxBytes, closeInWhenDone, closeOutWhenDone);
	}
	/** Tell the copy thread to stop the copying.
	*/
	public void terminate() {
		synchronized(this) {
			if(_closeIn) {
				try	{
					_in.close();
				} catch(Exception e) {
					if(null == _exception) {
						_exception= e;
					}
				}
			}
			if(_closeOut) {
				try	{
					_out.close();
				} catch(Exception e) {
					if(null == _exception) {
						_exception= e;
					}
				}
			}
			_in= null;
			_out= null;
		}
		interrupt();
	}
	/** Reports the exception (if any) that stopped the copy.
		@return	The exception or null if none has occurred.
	*/
	Exception exception() {
		return _exception;
	}
	/** Determines if the copy is still under way.
		@return	true if we are still copying from in to out.
	*/
	boolean running() {
		return ( (null != _in) && (null != _out) );
	}
	/** Thread entry point.
		Does all the copying.

	*/
	public void run() {
		InputStream		in= null;
		OutputStream	out= null;

		try	{
			int		timeout= _timeout;
			int		bytesTransferred= 0;
			byte	buffer[]= new byte[_bufferSize];

			while(running()) {

				synchronized(this) {
					in= _in;
					out= _out;
				}
				if( (null != in) && (null != out) ) {
					int	ready= in.available();
					if(ready <= 0) {
						if( (timeout < 0) && (_timeout > 0) ) {
							terminate(); // timeout occurred
						} else {
							timeout-= kLatencyFromSleepInMS;
							try {
								Thread.sleep(kLatencyFromSleepInMS);
							} catch(InterruptedException e) {
								// ignore
							}
						}
					} else {
						int	read= buffer.length;

						if(read > ready) {
							read= ready;
						}
						if( (_maxBytes > 0) && (bytesTransferred + read > _maxBytes) ) {
							read= _maxBytes - bytesTransferred;
							if(read <= 0) {
								terminate();
							}
						}
						read= in.read(buffer, 0, read);
						out.write(buffer, 0, read);
						bytesTransferred+= read;
						timeout= _timeout;
					}
				}
			}
		} catch(Exception e) {
			e.printStackTrace();
			_exception= e;
		}
		if(_closeIn && (null != in)) {
			try	{
				in.close();
			} catch(Exception e) {
				if(null == _exception) {
					_exception= e;
				}
			}
		}
		if(_closeOut && (null != out)) {
			try	{
				out.close();
			} catch(Exception e) {
				if(null == _exception) {
					_exception= e;
				}
			}
		}
	}
	/** How long to sleep if there is no data available */
	private static int		kLatencyFromSleepInMS= 100;
	/** The stream to read from */
	private InputStream		_in;
	/** The stream to write to */
	private OutputStream	_out;
	/** The maximum number of milliseconds for this operation to take. -1 == not timeout */
	private int				_timeout;
	/** The size of the buffer to use. This is the maxmimum (and attempted) read size. */
	private int				_bufferSize;
	/** The maxmimum number of bytes to read from the input stream. */
	private int				_maxBytes;
	/** The exception, if one occurred, null otherwise */
	private	Exception		_exception;
	/** Should we close the input stream. */
	private boolean			_closeIn;
	/** Should we close the output stream. */
	private boolean			_closeOut;
	/** Initialize this Pipe.
			Shared code for the constructors.
		@param in						The input stream to read from
		@param out						The output stream to send the data to
		@param timeoutInMilliseconds	The number of milliseconds to wait for data from <code>in</code> before
											ending the pipe
		@param bufferSize				The number of bytes to attempt to read at a time
		@param maxBytes					The maximum number of bytes to transfer fron <code>in</code> to <code>out</code>
		@param closeInWhenDone			If true, <code>in</code> will have close() called in it when done.
		@param closeOutWhenDone			If true, <code>out</code> will have close() called in it when done.
	*/
	private void _init(InputStream in, OutputStream out, int timeoutInMilliseconds, int bufferSize, int maxBytes, boolean closeInWhenDone, boolean closeOutWhenDone) {
		_closeIn= closeInWhenDone;
		_closeOut= closeOutWhenDone;
		_in= in;
		_out= out;
		_timeout= timeoutInMilliseconds;
		_exception= null;
		_bufferSize= bufferSize;
		_maxBytes= maxBytes;
		if(_bufferSize <= 0) {
			_bufferSize= 4096;
		}
		start();
	}
}

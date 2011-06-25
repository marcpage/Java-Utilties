import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

// javadoc -d html -private -quiet BufferedIOStream.java

/** Class that turns an output stream into an input stream.
	The internal buffer grows if writting rate is higher than reading rate.
	<b>TODO</b>
	<ul>
		<li>Implement long skip(long) to improve performance
	</ul>
*/
public class BufferedIOStream extends InputStream {
	/** A stream with a default 4k buffer.
	*/
	public BufferedIOStream() {
		_buffer= new byte[4096];
		_out= new _OutputStream();
		_size= 0;
		_start= 0;
		_next= 0;
		_highWaterMark= 0;
	}
	/** A stream with the suggested buffer.
		@param initialBuffer	The initial buffer size.
								If buffer is less than 2, buffer will be 512 bytes.
	*/
	public BufferedIOStream(int initialBuffer) {
		if(initialBuffer < 2) {
			initialBuffer= 512;
		}
		_buffer= new byte[initialBuffer];
		_out= new _OutputStream();
		_size= 0;
		_start= 0;
		_next= 0;
		_highWaterMark= 0;
	}
	/** Gets the current size of the internal buffer being used.
		@return Number of bytes allocated to buffer the OutputStream
	*/
	public int getBufferSize() {
		return _buffer.length;
	}
	/** Gets the most bytes that have been buffered at anytime.
		@return	The maxmimum number of bytes that have been in the buffer.
	*/
	public int getHighWaterMark() {
		return _highWaterMark;
	}
	/** Get the output stream for this input stream.
		@return				The output stream that this input stream will read from
		@throws IOException	If the input stream has already been closed.
	*/
	public OutputStream getOutputStream() throws IOException {
		if(_closed()) {
			throw new IOException("Already closed");
		}
		return _out;
	}
	/** Puts back one byte.
		@param b	The byte to be read next
	*/
	public void unread(int b) {
		_putback(b);
	}
	/** Puts back bytes to be read on next read().
		@param b	The array of bytes to put back in the stream
	*/
	public void unread(byte[] b) {
		_putback(b, 0, b.length);
	}
	/** Puts back bytes to be read on next read().
		@param b	The array of bytes to put back in the stream
		@param off	The offset in b
		@param len	The number of bytes to put back
	*/
	public void unread(byte[] b, int off, int len) {
		_putback(b, off, len);
	}
	/** Returns the number of bytes available (that have been written to the output stream).
		@return				Number of bytes available to read without blocking.
		@throws IOException	If the input stream is closed already.
	*/
	public int available() throws IOException {
		if(_closed()) {
			throw new IOException("Closed stream has no available");
		}
		return _size;
	}
	/** Closes the input and output streams.
		@throws IOException if the input stream has already been closed.
	*/
	public void close() throws IOException {
		if(_closed()) {
			throw new IOException("Already closed");
		}
		_buffer= null;
		_start= 0;
		_next= 0;
		_size= 0;
		_closeOutputStream();
	}
	/** Notifies clients that mark is *not* supported.
		@return	false
	*/
	public boolean markSupported() {
		return false;
	}
	/* mark not supported
	public void mark(int readlimit) {}
	public void reset() throws IOException {}
	*/
	/** Reads one byte that was written to the output stream.
		Will block if there is nothing to read.
		@return	the next byte, or -1 if the output stream was closed and all data has been read.
		@throws IOException	If the stream has already been closed or the thread is interrupted.
	*/
	public int read() throws IOException {
		return _read();
	}
	/** Read bytes directly into a buffer.
		The number of bytes actually read will be the lesser of b.length and available().
		@param b			Buffer to read bytes into
		@return				-1 for end of stream, or number of bytes actually read.
		@throws IOException
	*/
	public int read(byte[] b) throws IOException {
		return _read(b, 0, b.length);
	}
	/** Reads up to a number of bytes directly into a given buffer.
		The number of bytes actually read will be the lesser of len, b.length, available().
		@param b	Buffer to read bytes into
		@param off	The offset to start storing the bytes
		@param len	The maximum number of bytes to store
		@return		-1 for end of stream, or number of bytes actually read.
		@throws IOException	If stream is closed or someone notified us but there is not data in the buffer
								and the OutputStream has not been closed.
	*/
	public int read(byte[] b, int off, int len) throws IOException {
		return _read(b, off, len);
	}
	/* Inheriting behavior. Optimization: implement these
	public long skip(long n) {
	}
	*/
	/** OutputStream specifically designed to pass data to the BufferedIOStream.
	*/
	private class _OutputStream extends OutputStream {
		/** Creates the output stream.
		*/
		public _OutputStream() {
			_closed= false;
		}
		/** Closes the output stream.
			Data can still be read from the input stream, just marks the end of stream.
			@throws IOException	if the stream is already closed
		*/
		public void close() throws IOException {
			if(_closed) {
				throw new IOException("Already closed");
			}
			_closed= true;
			_closeOutputStream();
		}
		/** Nothing really to do. We can't control the reading.
			@throws IOException	doesn't
		*/
		public void flush() throws IOException {}
		/** Write bytes from a buffer to the storage buffer.
			@param b	The buffer of data
			@throws IOException	If either the InputStream or OutputStream has been closed.
		*/
		public void write(byte[] b) throws IOException {
			if(_closed) {
				throw new IOException("Stream closed");
			}
			if(_closed()) {
				throw new IOException("Input Stream closed");
			}
			_write(b, 0, b.length);
		}
		/** Write bytes from a buffer to the storage buffer.
			@param b	The buffer of data
			@param off	The offset in the buffer
			@param len	The number of bytes to store off
			@throws IOException	If either the InputStream or OutputStream has been closed.
		*/
		public void write(byte[] b, int off, int len) throws IOException {
			if(_closed) {
				throw new IOException("Stream closed");
			}
			if(_closed()) {
				throw new IOException("Input Stream closed");
			}
			_write(b, off, len);
		}
		/** Writes a single byte to the buffer.
			Just calls the BufferedIOStream object's <code>_write</code> method.
			@param b			The byte to write. If b is more than a byte, it will be cast to a byte.
			@throws IOException	If either the InputStream or OutputStream has been closed.
		*/
		public void write(int b) throws IOException {
			if(_closed) {
				throw new IOException("Stream closed");
			}
			if(_closed()) {
				throw new IOException("Input Stream closed");
			}
			_write(b);
		}
		/** Determines if the output stream has been closed */
		private boolean	_closed;
	}
	/** The circular buffer to hold the data. When we overflow the buffer, it grows. */
	private byte			_buffer[];
	/** The location to read the next byte. <code>_size</code> must be > 0 to be meaningful. */
	private int				_start;
	/** The next available location to store a byte. Only meaningful if not equal to <code>_start</code>. */
	private int				_next;
	/** The size of the data in the buffer.
		Mainly used when <code>_start == _next </code> to see if buffer is empty or full. */
	private int				_size;
	/** The most bytes in the buffer at any time */
	private int				_highWaterMark;
	/** The OutputStream. This is a singleton per BufferedIOStream that gets created at
		construction and removed at <code>close</code>. */
	private _OutputStream	_out;
	/** Does all the preflight checking for a read call.
		@throws IOException	If stream is closed or someone notified us but there is not data in the buffer
								and the OutputStream has not been closed.
	*/
	private synchronized int _readPreflight() throws IOException {
		if(_closed()) {
			throw new IOException("Cannot read from closed stream");
		}
		if(_endOfStream()) {
			return -1;
		}
		if(0 == _size) {
			try	{
				wait(); // wait for a write
			} catch(Exception exception) {
				throw new IOException(exception.toString());
			}
		}
		if(0 == _size) {
			if(_endOfStream()) {
				return -1;
			} else {
				throw new IOException("Nothing to read");
			}
		}
		return _size;
	}
	/** Reads up to a number of bytes directly into a given buffer.
		The number of bytes actually read will be the lesser of len, b.length, available().
		@param b	Buffer to read bytes into
		@param off	The offset to start storing the bytes
		@param len	The maximum number of bytes to store
		@return		-1 for end of stream, or number of bytes actually read.
		@throws IOException	If stream is closed or someone notified us but there is not data in the buffer
								and the OutputStream has not been closed.
	*/
	private synchronized int _read(byte[] b, int off, int len) throws IOException {
		int	bytesAvailable= _readPreflight();
		int	bytesToCopy;
		int	bytesToCopyThisPass;

		if(bytesAvailable < 0) {
			return -1;
		}
		if(_size < len) {
			bytesToCopy= _size;
		} else if(b.length - off < len) {
			bytesToCopy= b.length - off;
		} else {
			bytesToCopy= len;
		}
		if(0 == bytesToCopy) {
			return bytesToCopy;
		}

		boolean	copyWrapsAroundBuffer= (_start >= _next) && (_buffer.length - _start < bytesToCopy);

		if(copyWrapsAroundBuffer) {
			bytesToCopyThisPass= _buffer.length - _start;
		} else {
			bytesToCopyThisPass= bytesToCopy;
		}
		System.arraycopy(_buffer, _start, b, off, bytesToCopyThisPass);
		if(copyWrapsAroundBuffer) {
			System.arraycopy(_buffer, 0, b, off + bytesToCopyThisPass, bytesToCopy - bytesToCopyThisPass);
		}
		_start+= bytesToCopy;
		if(_start >= _buffer.length) {
			_start-= _buffer.length;
		}
		_size-= bytesToCopy;
		return bytesToCopy;
	}
	/** Reads one byte from the buffer and blocks if there is none.
		@return				The next byte, or -1 if we've read everything and the OutputStream has been closed.
		@throws IOException	If stream is closed or someone notified us but there is not data in the buffer
								and the OutputStream has not been closed.
	*/
	private synchronized int _read() throws IOException {
		int	bytesAvailable= _readPreflight();

		if(bytesAvailable < 0) {
			return -1;
		}
		byte value= _buffer[_start];
		++_start;
		if(_start >= _buffer.length) {
			_start= 0;
		}
		--_size;
		return value;
	}
	/** Ensure there is enough room in the buffer to add some data.
		If there is not enough room in <code>_buffer</code> to add <code>addAmount</code>
			bytes of data, then the buffer will grow by either 50% or 2x <code>addAmount</code>,
			whichever is bigger.
		@param addAmount	The number of bytes that should fit in <code>_buffer</code>
	*/
	private synchronized void _ensureBufferSize(int addAmount) {
		if(_size + addAmount > _buffer.length) {
			int		newBufferSize;
			int		increaseBy= _buffer.length / 2; // default to 50% bigger
			int		doubleAddAmount= 2 * addAmount; // or twice the add amount
			byte[]	newBuffer;
			int		copyLength;

			if(doubleAddAmount > increaseBy) {
				// increase by 50% or 2 x addAmount, whichever is bigger
				increaseBy= doubleAddAmount;
			}
			newBuffer= new byte[_buffer.length + increaseBy];
			if(_start >= _next) {
				copyLength= _buffer.length - _start;
			} else {
				copyLength= _next - _start;
			}
			System.arraycopy(_buffer, _start, newBuffer, 0, copyLength);
			if(_start >= _next) {
				System.arraycopy(_buffer, 0, newBuffer, copyLength, _next);
				for(int i= 0; i < _buffer.length; ++i) {
				}
			}
			_buffer= newBuffer;
			for(int i= 0; i < _buffer.length; ++i) {
			}
			_start= 0;
			_next= _size;
		}
	}
	/** Writes data to _buffer at the given offset.
		@param b			The buffer of data
		@param off			The offset in the buffer
		@param len			The number of bytes to store off
		@param bufferOffset	The offset in the buffer to write the bytes
		@return				The offset just past the last byte written
	*/
	private synchronized int _write(byte[] b, int off, int len, int bufferOffset) {
		int 	bytesToCopyThisPass;
		boolean	wrapData;

		assert(b.length - off >= len);
		assert(len > 0);
		wrapData= _buffer.length - bufferOffset <= len;
		if(wrapData) {
			bytesToCopyThisPass= _buffer.length - bufferOffset;
		} else {
			bytesToCopyThisPass= len;
		}
		System.arraycopy(b, off, _buffer, bufferOffset, bytesToCopyThisPass);
		if(wrapData) {
			System.arraycopy(b, off + bytesToCopyThisPass, _buffer, 0, len - bytesToCopyThisPass);
		}
		_size+= len;
		if(_size > _highWaterMark) {
			_highWaterMark= _size;
		}
		return len;
	}
	/** Write bytes from a buffer to the storage buffer.
			If the buffer is full, a larger buffer is created, and data is reorganized at the beginning of the buffer.
		@param b	The buffer of data
		@param off	The offset in the buffer
		@param len	The number of bytes to store off
	*/
	private synchronized void _write(byte[] b, int off, int len) {
		int	bytesToCopy= len;

		if(b.length - off < bytesToCopy) {
			bytesToCopy= b.length - off;
		}
		if(bytesToCopy > 0) {
			_ensureBufferSize(bytesToCopy);
			_write(b, off, bytesToCopy, _next);
			_next+= bytesToCopy;
			if(_next >= _buffer.length) {
				_next-= _buffer.length;
			}
		}
		notify(); // wake up next read
	}
	/** Puts bytes back into the stream.
		@param b	The buffer of data
		@param off	The offset in the buffer
		@param len	The number of bytes to put back
	*/
	private synchronized void _putback(byte[] b, int off, int len) {
		int	bytesToCopy= len;
		int bytesToCopyThisPass;

		if(b.length - off < bytesToCopy) {
			bytesToCopy= b.length - off;
		}
		if(bytesToCopy > 0) {
			_ensureBufferSize(bytesToCopy);
			_start-= bytesToCopy;
			if(_start < 0) {
				_start+= _buffer.length;
			}
			_write(b, off, bytesToCopy, _start);
		}
		notify(); // wake up next read
	}
	/** Puts a single byte back into the stream.
		@param b	The byte to put back
	*/
	private synchronized void _putback(int b) {
		_ensureBufferSize(1);
		--_start;
		if(_start < 0) {
			_start+= _buffer.length;
		}
		_buffer[_start]= (byte)b;
		notify(); // wake up next read
	}
	/** Write a single byte to the buffer.
			If the buffer is full, a larger buffer is created, and data is reorganized at the beginning of the buffer.
		@param b	The byte to write. b will be typecast to a byte and will be written regardless of the size of the value of b.
	*/
	private synchronized void _write(int b) {
		_ensureBufferSize(1);
		_buffer[_next]= (byte)b;
		++_next;
		if(_next >= _buffer.length) {
			_next= 0;
		}
		++_size;
		if(_size > _highWaterMark) {
			_highWaterMark= _size;
		}
		notify(); // wake up next read
	}
	/** Determines if the output stream has been closed and the buffer is empty.
	*/
	private boolean _endOfStream() {
		if(null != _out) {
			return false;
		}
		return 0 == _size;
	}
	/** Determines if the input stream has been closed.
		@return	true if close has already been called on this input stream.
	*/
	private boolean _closed() {
		return null == _buffer;
	}
	/** Closes the output stream and notifies any readers waiting on read to wake up.
	*/
	private synchronized void _closeOutputStream() {
		_out= null;
		notifyAll(); // wake up any threads waiting
	}
	/** Test.
		Test expanding the buffer with data split at the beginning and end of the buffer.
		Also tests expanding when all data is at the beginning of the buffer.
		Tests all three reads.
		Tests write byte and write buffer (does not test write partial buffer).
	*/
	public static void main(String... args) {
		try	{
			BufferedIOStream	in= new BufferedIOStream(8);
			OutputStream		out= in.getOutputStream();
			byte[]				buffer= new byte[4096];
			int					bytes;
			String				value;

			out.write("Testing".getBytes());
			bytes= in.read();
			if(bytes != 'T') {
				throw new IOException("write(byte[]), int read() failed");
			}
			buffer[0]= (byte)bytes;
			bytes= in.read(buffer, 1, 3);
			value= new String(buffer, 0, bytes + 1);
			if( !value.equals("Test") ) {
				throw new IOException("write(byte[]), int read(), int read(byte[],int,int) failed: "+bytes+":"+value);
			}
			out.write("ot".getBytes());
			bytes= in.available();
			if(bytes != 5) {
				throw new IOException("expected 5 (ingot) available, but got: "+bytes);
			}
			out.write("000".getBytes());
			bytes= in.available();
			if(bytes != 8) {
				throw new IOException("expected 8 (ingot000) available, but got: "+bytes);
			}
			out.write("111".getBytes());
			bytes= in.available();
			if(bytes != 11) {
				throw new IOException("expected 8 (ingot000111) available, but got: "+bytes);
			}
			buffer= new byte[11];
			bytes= in.read(buffer);
			value= new String(buffer, 0, bytes);
			if(!value.equals("ingot000111") ) {
				throw new IOException("buffer expansion 1, read(byte[]) failed: "+bytes+":"+value);
			}
			out.write('_');
			bytes= in.read();
			if(bytes != '_') {
				throw new IOException("write/read _ failed "+bytes);
			}
			out.write("0123456789ab".getBytes());
			buffer= new byte[12];
			bytes= in.read(buffer);
			value= new String(buffer, 0, bytes);
			if(!value.equals("0123456789ab") ) {
				throw new IOException("buffer expansion 2, read(byte[]) failed: "+bytes+":"+value);
			}
			out.write("0123456789abcde".getBytes());
			buffer= new byte[15];
			bytes= in.read(buffer);
			value= new String(buffer, 0, bytes);
			if(!value.equals("0123456789abcde") ) {
				throw new IOException("buffer expansion 3, read(byte[]) failed: "+bytes+":"+value);
			}
			System.out.println("Pass!");
		} catch(IOException exception) {
			exception.printStackTrace();
		}
	}
}

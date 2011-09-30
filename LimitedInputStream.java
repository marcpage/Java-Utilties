import java.io.InputStream;
import java.io.IOException;

/** Limits the amount read from an input stream.
	Makes a stream appear that it is a truncated length.
	<p><b>TODO</b><ul>
		<li>Look at supporting mark
	</ul>
*/
public class LimitedInputStream {
	/**
		@param in	The underlying stream
		@param max	The maxmimum number of bytes to read
	*/
	public LimitedInputStream(InputStream in, long max) {
		_in= in;
		_max= max;
	}
	/**
		@return	-1 for end of stream, or max reached. >= 0 the byte from the stream.
	*/
	public int read() throws IOException {
		int	result;

		if(_max <= 0) {
			return -1;
		}
		result= _in.read();
		if(result >= 0) {
			--_max;
		}
		return result;
	}
	/**
		@param b	The byte buffer to fill
		@return		-1 for end of stream or max reached. >= 0 the number of bytes put in <code>buffer</code>.
	*/
	public int read(byte[] b) throws IOException {
		return read(b, 0, b.length);
	}
	/**
		@param b	The byte buffer to fill
		@param off	The offset to start filling bytes in
		@param len	The maxmimum to read (modified to max if greater than max)
		@return		-1 for end of stream or max reached. >= 0 the number of bytes put in <code>buffer</code>.
	*/
	public int read(byte[] b, int off, int len) throws IOException {
		int	result;

		if(_max == 0) {
			return -1;
		}
		if(len > _max) {
			len= (int)_max;
		}
		result= _in.read(b, off, len);
		if(result > 0) {
			_max-= result;
		}
		return result;
	}
	/** Skips up to n bytes, or the end of stream, or max.
		@param n	The maxmimum number of bytes to skip.
		@return		The actual number of bytes skipped.
	*/
	public long skip(long n) throws IOException {
		long	result;

		if(n > _max) {
			n= _max;
		}
		result= _in.skip(n);
		_max-= result;
		return result;
	}
	/**
		@return	The number of bytes available to read, or max.
	*/
	public int available() throws IOException {
		int	result= _in.available();

		if(result > _max) {
			result= (int)_max;
		}
		return result;
	}
	/** Standard stream close.
	*/
	public void close() throws IOException {
		_in.close();
	}
	/**
		@return	false, mark is not supported.
	*/
	public boolean markSupported() {
		return false;
	}
	/** Skips the rest of the bytes up to max.
		This will get the underlying stream up to max.
		@return	The number of bytes skipped.
		@throws IOException	on io error
	*/
	public long finish() throws IOException {
		return skip(_max);
	}
	/** The underlying stream. */
	private InputStream	_in;
	/** The maxmimum number of bytes left to read from the underlying stream. */
	private long		_max;

	/** Test. Essentially: head -c #
		@param args	One argument, the number of bytes to print.
	*/
	public static void main(String... args) {
		long	limit= Long.parseLong(args[0]);

		try	{
			LimitedInputStream	in= new LimitedInputStream(System.in, limit);
			byte[]				buffer= new byte[4096];
			int					read= in.read(buffer);

			while(read >= 0) {
				System.out.write(buffer, 0, read);
				read= in.read(buffer);
			}
		} catch(IOException exception) {
			exception.printStackTrace();
		}

	}
}

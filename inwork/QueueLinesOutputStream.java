import java.io.OutputStream;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;

public class QueueLinesOutputStream extends OutputStream {
	/**
		@param output	Lines will be put, and when close() is called, an empty line will be put
	*/
	public QueueLinesOutputStream(ArrayBlockingQueue<String> output, int maxLineSize) {
		_queue= output;
		_full= false;
		_firstIndex= 0;
		_nextIndex= 0;
		_buffer= new byte[maxLineSize <= 0 ? 4096 : maxLineSize];
	}
	public boolean eof() {
		return null == _queue;
	}
	public void close() throws IOException {
		try {
			_queue.put("");
		} catch(InterruptedException e) {
			_queue= null;
			throw new IOException(e.toString());
		}
		_queue= null;
	}
	public void flush() throws IOException {
		_removeLines(true /* force even if eol not found */);
	}
	public void write(byte[] b) throws IOException {
		write(b, 0, b.length);
	}
	public void write(byte[] b, int off, int len) throws IOException {
		int	bytesStored= _store(b, off, len);

		_removeLines(false);
		while(bytesStored < len) {
			int	bytesStoredThisTime= _store(b, off + bytesStored, len - bytesStored);

			_removeLines(false);
			bytesStored+= bytesStoredThisTime;
		}
	}
	public void write(int b) throws IOException {
		if(!_store(b)) {
			if(!_removeLines(false)) {
				_removeLines(true);
			}
		}
		if(!_store(b)) {
			throw new IOException("Unable to store byte "+b);
		}
	}
	private ArrayBlockingQueue<String>	_queue;
	private byte[]						_buffer; ///< Circular Buffer
	private int							_firstIndex, _nextIndex;
	private boolean						_full;
	private boolean _full() {
		return _full;
	}
	private boolean _empty() {
		return !_full && (_firstIndex == _nextIndex);
	}
	private int _left() {
		if(_full) {
			return 0;
		}
		if(_firstIndex <= _nextIndex) {
			return _nextIndex - _firstIndex;
		}
		return _nextIndex + _buffer.length - _firstIndex;
	}
	private int _size() {
		return _buffer.length - _left();
	}
	private byte _get(int index) {
		int	start= _firstIndex;

		if(index >= _size()) {
			throw new ArrayIndexOutOfBoundsException(index);
		}
		if(start + index > _buffer.length) {
			index-= _buffer.length - start;
			start= 0;
		}
		return _buffer[start + index];
	}
	private boolean _removeLines(boolean force) throws IOException {
		String	line= _removeLine(false);
		boolean	anythingRemoved= false;

		while(null != line) {
			anythingRemoved= true;
			try {
				if(line.length() > 0) {
					_queue.put(line);
				}
			} catch(InterruptedException e) {
				throw new IOException(e.toString());
			}
			line= _removeLine(false);
		}
		line= _removeLine(force);
		if(null != line) {
			try {
				if(line.length() > 0) {
					_queue.put(line);
				}
			} catch(InterruptedException e) {
				throw new IOException(e.toString());
			}
			anythingRemoved= true;
		}
		return anythingRemoved;
	}
	private String _removeLine(boolean force) {
		int		lineSize= -1;
		int		size= _size();
		boolean	isCarriageReturn= false;

		if(_empty()) {
			return null;
		}
		for(int index= 0; (index < size) && (lineSize == -1); index+= 1) {
			byte	b= _get(index);

			isCarriageReturn= (b == '\r');
			if( isCarriageReturn || (b == '\n') ) {
				lineSize= index + 1;
			}
		}
		if( (force || _full) // if we are forcing or buffer is full
				&& (lineSize == -1)) { // and no end of line found in the buffer, send it all
			return _remove(size);
		}
		if(isCarriageReturn) { // handle \r with possible \n after it
			if( _full && (lineSize == _buffer.length) ) {
				return _remove(lineSize); // we don't know if it is \r\n, but we can't wait to find out
			}
			if(!_full && (lineSize < size) ) {
				if(_get(lineSize) == '\n') {
					lineSize+= 1; // if it is \r\n, then get the \n
				}
			} else if(!_full) {
				return null; // we ended on a carriage return, so there might be a line feed coming
			}
		}
		if(lineSize == -1) {
			return null; // no more lines in the buffer
		}
		return _remove(lineSize); // send the next line
	}
	private String _remove(int length) {
		boolean	contiguous= _full && (_firstIndex == 0) || !_full && (_firstIndex <= _nextIndex);
		int		removeSize= contiguous ? length : _buffer.length - _firstIndex;
		String	part;

		if(length > _size()) {
			throw new ArrayIndexOutOfBoundsException(length);
		}
		if(removeSize > length) {
			removeSize= length;
		}
		part= new String(_buffer, _firstIndex, removeSize);
		_full= false;
		_firstIndex+= removeSize;
		if(_firstIndex >= _buffer.length) {
			_firstIndex= 0;
		}
		if( !contiguous && (length > removeSize) ) {
			part+= _remove(length - removeSize); // Get the rest from the beginning of the buffer
		}
		return part;
	}
	private boolean _store(int b) {
		if(!_full) {
			_buffer[_nextIndex]= (byte)b;
			_nextIndex+= 1;
			if(_nextIndex >= _buffer.length) {
				_nextIndex= 0;
			}
			_full= _nextIndex == _firstIndex;
			return true;
		}
		return false;
	}
	private int _store(byte[] b, int off, int len) {
		if(len > 0) {
			boolean	freeBytesAtEndOfBuffer= (_firstIndex <= _nextIndex) && !_full;
			int		chunkBytes= freeBytesAtEndOfBuffer ? _buffer.length - _nextIndex : _firstIndex - _nextIndex;
			int		storeBytes= chunkBytes > len ? len : chunkBytes;

			System.arraycopy(b, off, _buffer, _nextIndex, storeBytes);
			_nextIndex+= storeBytes;
			if(_nextIndex >= _buffer.length) {
				_nextIndex= 0;
			}
			_full= _nextIndex == _firstIndex;
			if(!_full 									// If we're not full yet
					&& freeBytesAtEndOfBuffer			// and we stored bytes at the end of the buffer
					&& (storeBytes != chunkBytes)) {	// and there are more bytes to store
				storeBytes+= _store(b, off + storeBytes, len - storeBytes); // write bytes to the beginning of the buffer
			}
			return storeBytes;
		}
		return 0;
	}
}

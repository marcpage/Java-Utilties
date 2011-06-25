/** A circular byte buffer.
	Bytes put in the buffer until the buffer is full. Then the bytes wrap over the beginning bytes.
*/
public class CircularByteBuffer {
	/**
		@param size	The maximum number of bytes being held in the buffer.
	*/
	public CircularByteBuffer(int size) {
		_bytes= new byte[size];
		clear();
	}
	/** Erases all bytes in the buffer.
	*/
	void clear() {
		_start= 0;
		_size= 0;
	}
	/** Returns an array for the last <code>size</code> bytes sent to the buffer.
		@return	The bytes in the order they were received.
	*/
	byte[] array() {
		byte[]	result= new byte[_size];
		int		firstCopySize= _size;

		if(_start + firstCopySize > _bytes.length) {
			firstCopySize= _bytes.length - _start;
		}
		System.arraycopy(_bytes, _start, result, 0, firstCopySize);
		if(firstCopySize != _size) {
			System.arraycopy(_bytes, 0, result, firstCopySize, _size - firstCopySize);
		}
		return result;
	}
	/** Put a single byte on the end of the buffer.
		@param b	The byte to append.
	*/
	CircularByteBuffer put(byte b) {
		if(_size == _bytes.length) { // buffer full, just move start
			_bytes[_start]= b;
			++_start;
		} else { // buffer not full, change size
			int	position= _start + _size;

			if(position >= _bytes.length) {
				position-= _bytes.length;
			}
			_bytes[position]= b;
			++_size;
		}
		return this;
	}
	/** Put a single character on the end of the buffer.
		@param c	Must fit within a byte. appended to the buffer.
		@return		<code>this</code> is returned to do a series of commands
	*/
	CircularByteBuffer put(char c) {
		return put((byte)c);
	}
	/** Append an array of bytes to the buffer.
		@param src	The bytes to append. Same effect as for(byte b : src) {put(b);}
		@return		<code>this</code> is returned to do a series of commands
	*/
	CircularByteBuffer put(byte[] src) {
		return put(src, 0, src.length);
	}
	/** Append an array of bytes to the buffer.
		@param src		The bytes to append.
		@param offset	The offset in <code>src</code> to start appending bytes
		@param length	The number of bytes in <code>src</code> to append
		@return			<code>this</code> is returned to do a series of commands
	*/
	CircularByteBuffer put(byte[] src, int offset, int length) {
		if(length >= _bytes.length) { // asked to save more than the buffer
			clear();	// reset buffer and just store the tail
			offset+= (length - _bytes.length); // skip the beginning bytes
			System.arraycopy(src, offset, _bytes, 0, _bytes.length);
			_size= _bytes.length;
		} else {
			int	firstCopyLength= length;
			int	start= _start; // if buffer full, start writing over the start

			if(_size != _bytes.length) { // if buffer not full, fill in at the "end"
				start+= _size;
				if(start >= _bytes.length) {
					start-= _bytes.length;
				}
			}
			if(start + firstCopyLength > _bytes.length) {
				firstCopyLength= _bytes.length - start;
			}
			System.arraycopy(src, offset, _bytes, start, firstCopyLength);
			start+= firstCopyLength;
			if(start == _bytes.length) {
				System.arraycopy(src, offset + firstCopyLength, _bytes, 0, length - firstCopyLength);
				start= (length - firstCopyLength);
				_size= _bytes.length; // buffer is now full
			}
			if(_size  == _bytes.length) { // if buffer was full, just move the start
				_start= start;
			} else { // if buffer was not full, grow size
				_size+= length;
			}
		}
		return this;
	}
	/** The bytes to store */
	private byte[]	_bytes;
	/** The position of the first character (before it drops off into the bit bucket) */
	private int		_start;
	/** The number of bytes in the buffer */
	private int		_size;
	/** Test function. Should not print out anything.
		@param args	ignored
	*/
	public static void main(String... args) {
		CircularByteBuffer b= new CircularByteBuffer(5);

		b.put('1').put('2').put('3').put('4');
		if(!"1234".equals(new String(b.array()))) {
			System.err.println("1234 test failed:"+(new String(b.array())));
		}
		b.put('5');
		if(!"12345".equals(new String(b.array()))) {
			System.err.println("12345 test failed:"+(new String(b.array())));
		}
		b.put('6');
		if(!"23456".equals(new String(b.array()))) {
			System.err.println("23456 test failed:"+(new String(b.array())));
		}
		b= new CircularByteBuffer(5);
		b.put('1').put("23456".getBytes());
		if(!"23456".equals(new String(b.array()))) {
			System.err.println("[]23456 test failed:"+(new String(b.array())));
		}
		b.put("7890".getBytes());
		if(!"67890".equals(new String(b.array()))) {
			System.err.println("[]67890 test failed:"+(new String(b.array())));
		}
		b.put('1');
		if(!"78901".equals(new String(b.array()))) {
			System.err.println("78901 test failed:"+(new String(b.array())));
		}
		b= new CircularByteBuffer(5);
		b.put('0').put('1').put("23456".getBytes());
		if(!"23456".equals(new String(b.array()))) {
			System.err.println("[2]23456 test failed:"+(new String(b.array())));
		}
		b.put("7890".getBytes());
		if(!"67890".equals(new String(b.array()))) {
			System.err.println("[2]67890 test failed:"+(new String(b.array())));
		}
		b.put('1');
		if(!"78901".equals(new String(b.array()))) {
			System.err.println(".78901 test failed:"+(new String(b.array())));
		}
		b= new CircularByteBuffer(5);
		b.put("0123".getBytes());
		if(!"0123".equals(new String(b.array()))) {
			System.err.println("0123 test failed:"+(new String(b.array())));
		}
		b.put("456".getBytes());
		if(!"23456".equals(new String(b.array()))) {
			System.err.println("[3]23456 test failed:"+(new String(b.array())));
		}
		b.put("7890".getBytes());
		if(!"67890".equals(new String(b.array()))) {
			System.err.println("[3]67890 test failed:"+(new String(b.array())));
		}
		b.put("123".getBytes());
		if(!"90123".equals(new String(b.array()))) {
			System.err.println("90123 test failed:"+(new String(b.array())));
		}
		b.put("456".getBytes());
		if(!"23456".equals(new String(b.array()))) {
			System.err.println("[4]23456 test failed:"+(new String(b.array())));
		}
	}
}

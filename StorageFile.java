import java.io.File;
import java.util.Arrays;
import java.io.IOException;
import java.util.ArrayList;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import java.io.RandomAccessFile;
import java.util.zip.DataFormatException;

/** Key Store file.
	Stores arbitrary key/value pairs in a file.<br>
	File Format:<ul>
		<li>File Header: {0x89}STOR00\r\n{26}\n<ol>
			<li>{0x89} detects 7-bit stripping (-119 byte)
			<li>STOR00 is human readable
			<li>\r\n detects DOS-UNIX eol conversion
			<li>{26} is DOS end of display
			<li>\n detects UNIX-DOS eol conversion
			</ol>
		<li>Block Header:<ol>
			<li>Flags:	1 byte, If High bit is set, then it is a free block the size of the 7 bit value (everything after Flags)
					If high bit is not set, then bit 0 == free, bit 1 == zip compressed
			<li>Size:	4 bytes, big endian, If Flags high bit not set, then this is the size of Value
						if free, the size of everything after Size (free space)
			<li>kSize:	2 bytes, big endian, the number of bytes for the Key
			<li>Key:	kSize bytes The key, as UTF-8 data
			<li>Value:	Size bytes of something, possibly zip compressed with no wrap, per GZIP and PKZIP
		</ol></ul>
	TODO
	<ul>
	<li>implement compaction<br>
		Have put take a boolean (doNotExpand).
		If doNotExpand compact (get key right after a free block, remove it and re-add it with doNotExpand= false)
		until we find a free block big enough. If no free block big enough is found, return false.
	<li>implement compression of keys (flag for compressed key)
	<li>implement key redirection (flag for redirect, data is the key of where the data really is)
	<li>implement hash for all data, and use that key, with key-redirection to get it
	<li>Make sure free blocks are consolidated any time we walk the list
	</ul>
*/
public class StorageFile implements Storage {
	/** Creates or opens a storage file at the given path.
		@param location		The path to the file to open or create.
		@throws IOException	If there are any IO errors or the given file exists but is not a valid storage file.
	*/
	public StorageFile(File location) throws IOException {
		_storage= new RandomAccessFile(location, "rw" /*rwd*/);
		_chunks= new ArrayList<_Chunk>();
		if(_storage.length() == 0) {
			_storage.write(_signature);
			_firstChunk= _signature.length + 4; // offset of first chunk in the file, right after signature
			_storage.writeInt((int)_firstChunk);
		} else {
			byte[]	buffer= new byte[_signature.length];
			_storage.seek(0);
			_storage.readFully(buffer);
			if(!Arrays.equals(buffer, _signature)) {
				throw new IOException("Not a storage file, or corrupt store file");
			}
			_firstChunk= _storage.readInt();
		}
		_readBlocks();
	}
	/** Get the data for a given key.
		@param key	The unique key in the file.
		@return		The data for the key, or null if not found.
		@throws IOException	If there are any IO errors
	*/
	public byte[] get(String key) throws IOException {
		for(_Chunk chunk : _chunks) {
			if(chunk.key().equals(key)) {
				return _readChunk(chunk);
			}
		}
		return null;
	}
	/** Determines if the key exists in the file.
		@param key	The key to look for.
		@return		true if the key is in the file, false if not
		@throws IOException	If there are any IO errors
	*/
	public boolean has(String key) throws IOException {
		for(_Chunk chunk : _chunks) {
			if(!chunk.free() && chunk.key().equals(key)) {
				return true;
			}
		}
		return false;
	}
	/** Removes a key from the file (marks the space reusable).
		@param key	The key to remove.
		@return		true if the key existed, false otherwise.
		@throws IOException	If there are any IO errors
	*/
	public boolean remove(String key) throws IOException {
		int	index= 0, prefixFree, suffixFree;

		while( (index < _chunks.size()) && (_chunks.get(index).key() != key) ) {
			++index;
		}
		if(index == _chunks.size()) {
			return false;
		}
		prefixFree= index;
		while( (prefixFree - 1 > 0) && _chunks.get(prefixFree - 1).free() ) {
			--index;
		}
		suffixFree= index;
		while( (suffixFree + 1 < _chunks.size()) && _chunks.get(suffixFree + 1).free() ) {
			++suffixFree;
		}
		if(suffixFree - prefixFree == 0) { // no surrounding free blocks
			_chunks.get(index).makeFree();
		} else {
			_Chunk	firstBlock= _chunks.get(prefixFree);

			if(!firstBlock.free()) {
				firstBlock.makeFree(); // no free blocks before removed one
			}
			firstBlock.expandTo(0, _chunks.get(suffixFree).next()); // expand 1st empty block
			for(index= prefixFree + 1; index <= suffixFree; ++index) {
				_chunks.remove(prefixFree + 1); // remove free blocks consolidated into this one
			}
		}
		return true;
	}
	/** Stores a given key/data pair in the file.
		If the key exists already in the file, nothing will be done.
		Allows the buffer to grow (doNotGrow = false).
		@param key			The key for the data.
		@param data			The data to store in the file.
		@return				true if the key/data were added, false if the key already exists in the file
		@throws IOException	If there are any IO errors
	*/
	public boolean put(String key, byte[] data) throws IOException {
		return put(key, data, false);
	}
	/** Stores a given key/data pair in the file.
		If the key exists already in the file, nothing will be done.
		@param key			The key for the data.
		@param data			The data to store in the file.
		@param doNotGrow	If true, the file will not be expanded to add this key/data
		@return				true if the key/data were added, false if the key already exists in the file
		@throws IOException	If there are any IO errors
	*/
	public boolean put(String key, byte[] data, boolean doNotGrow) throws IOException {
		boolean		compressed;
		byte[]		keyData= key.getBytes("UTF-8"), compressedData;
		int			index= 0;
		int			dataLength= data.length, compressedLength;
		Deflater	compress;

		if(has(key)) {
			return false;
		}
		compress= new Deflater(Deflater.BEST_COMPRESSION, true);
		compress.setInput(data);
		compress.finish();
		compressedData= new byte[dataLength];
		compressedLength= compress.deflate(compressedData);
		compressed= compressedLength < dataLength;
		if(compressed) {
			data= compressedData;
			dataLength= compressedLength;
		}
		while( (index < _chunks.size()) && // look for a free chunk
				(!_chunks.get(index).free() || (_chunks.get(index).size(keyData) < dataLength) ) ) {
			++index;
		}
		if(index == _chunks.size()) { // no room to add it
			if(doNotGrow) {
				return false; // cannot grow the file
			} else {
				_chunks.add(new _Chunk(_storage.length(), key, keyData, data, 0, dataLength, compressed));
			}
		} else { // overwrite an existing free block
			_Chunk	next= _chunks.get(index).allocate(key, keyData, data, 0, dataLength, compressed);

			if(null != next) { // there is a free block after this one, add it
				if( (index + 1 < _chunks.size()) && _chunks.get(index + 1).free() ) {
					_chunks.get(index + 1).expandTo(next.offset(), 0); // the next block is free, expand it down
				} else {
					_chunks.add(index + 1, next);
				}
			}
		}
		return true;
	}
	/** Gets the number of bytes used on disk for this storage file.
		@return	The number of bytes on disk.
		@throws IOException	If there are any IO errors
	*/
	public long size() throws IOException {
		return _storage.length();
	}
	/** Returns the total size in the storage file used for either free or key/data blocks.
		@param free		true means add up the size of the free blocks, false means add up the size of the key/data blocks
		@return			The number of bytes used
		@throws IOException	If there are any IO errors
	*/
	public long size(boolean free) throws IOException {
		long	size= 0;

		for(_Chunk chunk : _chunks) {
			if(chunk.free() == free) {
				size+= chunk.next() - chunk.offset();
			}
		}
		return size;
	}
	/** Dumps the layout of the file to System.err.
		@throws IOException	If there are any IO errors
	*/
	public void dump() throws IOException {
		System.err.println("_firstChunk="+_firstChunk+" size="+_storage.length());
		for(_Chunk chunk : _chunks) {
			if(chunk.free()) {
				System.err.println("\t"+"(offset="+chunk.offset()+" next="+chunk.next()+" size="+chunk.size()+")");
			} else {
				System.err.println("\t"+chunk.key()+"="+(new String(_readChunk(chunk)))+" (offset="+chunk.offset()+" next="+chunk.next()+" compressed="+chunk.compressed()+" size="+chunk.size()+")");
			}
		}
	}

	/** Represents either a free block or a key/data pair in the file.
	*/
	private class _Chunk {
		/** Read Chunk
			@param offset	The offset to read the chunk from the file.
			@throws IOException	If there are any IO errors
		*/
		public _Chunk(long offset) throws IOException {
			_offset= offset;
			_readFlags(); // sets _flags
			if( (_flags & _smallFreeFlag) != 0 ) {
				_smallFreeBlock(); // sets _next, _key, _headerSize
			} else {
				long	size= _readSizeBlock(); // sets _next, _key, _headerSize if free

				if(size >= 0) { // not a free block
					_readKey(); // sets _headerSize
					_next= _offset + _headerSize + size;
				}
			}
		}
		/** Writes a chunk at the given offset, with the key and data.
			These chunks are only intended to be appended to the end or completely fill a block.
			@param offset		The offset in the file to store key/data
			@param key			The key as a String
			@param keyData		The key as a byte[], this is to cut down on the conversions to byte[]
			@param data			The data to associate with the key
			@param compressed	Is data compressed
			@throws IOException	If there are any IO errors or if we split a free block
		*/
		public _Chunk(long offset, String key, byte[] keyData, byte[] data, boolean compressed) throws IOException {
			_offset= offset;
			_key= null;
			_flags= _freeFlag;
			_next= _offset
				+ 1 // flags
				+ 4 // data length
				+ 2 // key length
				+ keyData.length + data.length;
			if(null != allocate(key, keyData, data, 0, data.length, compressed)) {
				throw new IOException("Tail allocation added chunk!");
			}
		}
		/** Writes a chunk at the given offset, with the key and data.
			@param offset		The offset in the file to store key/data
			@param key			The key as a String
			@param keyData		The key as a byte[], this is to cut down on the conversions to byte[]
			@param data			The data to associate with the key
			@param off			The offset in data where the real data begins
			@param len			The number of actual data bytes to use
			@param compressed	Is data compressed
			@throws IOException	If there are any IO errors or if we split a free block
		*/
		public _Chunk(long offset, String key, byte[] keyData, byte[] data, int off, int len, boolean compressed) throws IOException {
			_offset= offset;
			_key= null;
			_flags= _freeFlag;
			_next= _offset
				+ 1 // flags
				+ 4 // data length
				+ 2 // key length
				+ keyData.length + data.length;
			if(null != allocate(key, keyData, data, off, len, compressed)) {
				throw new IOException("Tail allocation added chunk!");
			}
		}
		/** Reallocates this chunk to encompass other chunks.
			@param offset		if 0, ignored, otherwise the new offset for this chunk
			@param next			if 0, ignored, otherwise the next next for this chunk
			@throws IOException	If there are any IO errors, or this chunk is not a free block
		*/
		public void expandTo(long offset, long next) throws IOException {
			if(!free()) {
				throw new IOException("Trying to expand allocated block");
			}
			if(offset != 0) {
				_offset= offset;
			}
			if(next != 0) {
				_next= next;
			}
			_free();
		}
		/** Allocates a data chunk.
			@param key			The key as a String
			@param data			The data to associate with the key
			@param compressed	Is data compressed
			@return no-null if there is a free chunk created after this one
			@throws IOException	If there are any IO errors, we're allocating an already allocated block or will not contain the key/data
		*/
		public _Chunk allocate(String key, byte[] data, boolean compressed) throws IOException {
			return allocate(key, key.getBytes("UTF-8"), data, 0, data.length, compressed);
		}
		/** Allocates a data chunk.
			@param key			The key as a String
			@param data			The data to associate with the key
			@param offset		The offset in data where the real data begins
			@param length		The number of actual data bytes to use
			@param compressed	Is data compressed
			@return no-null if there is a free chunk created after this one
			@throws IOException	If there are any IO errors, we're allocating an already allocated block or will not contain the key/data
		*/
		public _Chunk allocate(String key, byte[] data, int offset, int length, boolean compressed) throws IOException {
			return allocate(key, key.getBytes("UTF-8"), data, offset, length, compressed);
		}
		/** Allocates a data chunk.
			@param key			The key as a String
			@param keyData		The key as a byte[], this is to cut down on the conversions to byte[]
			@param data			The data to associate with the key
			@param compressed	Is data compressed
			@return no-null if there is a free chunk created after this one
			@throws IOException	If there are any IO errors, we're allocating an already allocated block or will not contain the key/data
		*/
		public _Chunk allocate(String key, byte[] keyData, byte[] data, boolean compressed) throws IOException {
			return allocate(key, keyData, data, 0, data.length, compressed);
		}
		/** Allocates a data chunk.
			@param key			The key as a String
			@param keyData		The key as a byte[], this is to cut down on the conversions to byte[]
			@param data			The data to associate with the key
			@param offset		The offset in data where the real data begins
			@param length		The number of actual data bytes to use
			@param compressed	Is data compressed
			@return no-null if there is a free chunk created after this one
			@throws IOException	If there are any IO errors, we're allocating an already allocated block or will not contain the key/data
		*/
		public _Chunk allocate(String key, byte[] keyData, byte[] data, int offset, int length, boolean compressed) throws IOException {
			int		proposedHeaderSize= 1 + 4 + 2 + keyData.length;

			if(!free()) {
				throw new IOException("Trying to allocate an already allocated block");
			}
			if(_offset + proposedHeaderSize + length > _next) {
				throw new IOException("Data does not fit in free block");
			}
			_headerSize= proposedHeaderSize;
			if(compressed) {
				_flags= _compressedFlag;
			} else {
				_flags= 0;
			}
			_key= key;
			_storage.seek(_offset);
			_storage.writeByte(_flags);
			_storage.writeInt(length);
			_storage.writeShort(keyData.length);
			_storage.write(keyData);
			_storage.write(data, offset, length);
			if(_offset + _headerSize + length < _next) { // create free block for space after this block
				_Chunk	next= new _Chunk(_offset + _headerSize + length, _next);
				_next= _offset + _headerSize + length;
				return next;
			}
			return null;
		}
		/** Is this chunk free.
			@return 	true if this chunk is available for use, false if it currently has a key
		*/
		public boolean free() {
			return null == _key;
		}
		/** Determines if the data in this chunk is compressed.
			@return		true if the data returned by get should be decompressed, false if it can be used as is
		*/
		public boolean compressed() {
			return (_flags & _compressedFlag) != 0;
		}
		/** Marks this block as available for use
			@throws IOException	If there are any IO errors, or this chunk is already free
		*/
		public void makeFree() throws IOException {
			if(free()) {
				throw new IOException("Freeing an already free block");
			}
			_free();
		}
		/** Gets the size of the data of this chunk.
			@return		For an allocated block, it returns the size of the data (compressed size if it is compressed).
						For a free block, it returns the size of the block not being used to flag it as a free block.
		*/
		public long size() {
			return _next - _offset - _headerSize;
		}
		/** Returns the size of data that could fit in this block with the given key.
			@param key		The bytes that would be used for the key
			@return			The number of bytes data could be for this block
		*/
		public long size(byte[] key) {
			return _next - _offset
						- 1 // flags
						- 4 // data size
						- 2 // key size
						- key.length;
		}
		/** The offset in the file of the header for this chunk.
			@return	The offset in the file of the header for this chunk.
		*/
		public long offset() {
			return _offset;
		}
		/** The offset of the next block.
			@return	The offset of the next block, or the end of the file if this is the last chunk.
		*/
		public long next() {
			return _next;
		}
		/** The key for this chunk, or null if this chunk is not allocated
			@return	The key for this chunk, or null if this chunk is not allocated
		*/
		public String key() {
			return _key;
		}
		/** Gets the data for this chunk.
			@return	The raw bytes for this chunk from the disk
			@throws IOException	If there are any IO errors
		*/
		public byte[] get() throws IOException {
			byte[]	data= new byte[(int)size()];

			_storage.seek(_offset + _headerSize);
			_storage.readFully(data);
			return data;
		}
		/** The offset of this chunk in the file */
		private long	_offset;
		/** The offset of the next chunk in the file */
		private long	_next;
		/** The key of the chunk, or if null == _key, it's a free block */
		private String	_key;
		/** The flags stored with the chunk on disk */
		private int		_flags;
		/** The size of the header. _offset + _headerSize would be the offset of the data */
		private int		_headerSize;
		/** A new free chunk at a given location and size.
			@param o	The offset of the chunk
			@param n	The offset of the next chunk
			@throws IOException	If there are any IO errors
		*/
		private _Chunk(long o, long n) throws IOException {
			_offset= o;
			_next= n;
			_free();
		}
		/** Reads the flags for this chunk from disk.
			<br>Postcondition:		Sets _flags
			@throws IOException	If there are any IO errors, or if the flags are not valid
		*/
		private void _readFlags() throws IOException {
			_storage.seek(_offset);
			_flags= _storage.readByte();
			if(_flags < 0) {
				_flags+= 256; // make it unsigned byte
			}
			if( ((_flags & _illegalFlags) != 0) && ((_flags & _smallFreeFlag) == 0) ) {
				throw new IOException("Corrupt Storage File:"+_flags);
			}
		}
		/** Sets this chunk up as a small free block read from disk.
			<br>Precondition:	_flags must already be set.
			<br>Postcondition:	_headerSize is set.
			<br>Postcondition:	_next is set.
		*/
		private void _smallFreeBlock() {
			long	size= _flags & ~_smallFreeFlag;

			_key= null; // free block
			_headerSize= 1;
			_next= _offset + _headerSize + size; // size does not include flags byte
		}
		/** Reads the data size from the block on disk.
			<br>Precondition:	_flags must be set correctly.
			<br>Precondition: Must have just read the flags byte from the chunk
			<br>Precondition: must not be called for a small free block
			<br>Postcondition:	_key is set if it is free.
			<br>Postcondition:	_headerSize is set if it is free.
			<br>Postcondition:	_next is set if it is free.
			@return	The size of the data if the block is allocated, -1 if it is a free block
			@throws IOException	If there are any IO errors
		*/
		private long _readSizeBlock() throws IOException {
			long	size= _storage.readInt();

			if( (_flags & _freeFlag) == 0 ) {
				_key= null;
				_headerSize= 1 + 4;
				_next= _offset + _headerSize + size;
				return -1;
			}
			return size;
		}
		/** Reads the key from the block.
			<br>Precondition: Must have just read the data size
			<br>Postcondition:	_key is set.
			<br>Postcondition:	_headerSize is set.
			@throws IOException	If there are any IO errors
		*/
		private void _readKey() throws IOException {
			int		keySize= _storage.readShort();
			byte[]	keyBuffer;

			if(keySize < 0) {
				keySize+= 65536; // make it unsigned
			}
			keyBuffer= new byte[keySize];
			_storage.readFully(keyBuffer);
			_key= new String(keyBuffer, "UTF-8");
			_headerSize= 1 + 4 + 2 + keySize;
		}
		/** Marks a block as free in memory and on disk.
			<br>Postcondition: _headerSize is set
			<br>Postcondition: _flags set
			@throws IOException	If there are any IO errors
		*/
		private void _free() throws IOException {
			_headerSize= 1; // 1 == flags byte

			int	fullSize= (int)(_next - _offset) - _headerSize;

			if(fullSize < _smallFreeFlag) {
				_storage.seek(_offset);
				_flags= fullSize | _smallFreeFlag;
				_storage.writeByte(_flags);
			} else {
				_storage.seek(_offset);
				_flags= _freeFlag;
				_storage.writeByte(_flags);
				_headerSize+= 4; // 4 == length number bytes
				_storage.writeInt(fullSize - 4); // 4 == length number bytes
			}
			_key= null;
		}
	}
	/** The file we are using for storage. */
	private RandomAccessFile		_storage;
	/** The chunks, ordered by file order, both free and allocated */
	private ArrayList<_Chunk>		_chunks;
	/** The offset of the first chunk in the file */
	private long					_firstChunk;
	/** The signature, first bytes, of a validate storage file */
	private static final byte[]		_signature= new byte[]{-119,'S','T','O','R','0','0',13,10,26,10};
	/** Large free block. This block is bigger than 128 bytes. */
	private static final int		_freeFlag=			0x01;
	/** The data is stored on disk in a zlib compressed format */
	private static final int		_compressedFlag=	0x02;
	/** The key is stored on disk in a zlib compressed format. Currently unused */
	private static final int		_compressedKeyFlag=	0x04;
	/** The data for this key is stored in another key. The other key is the data of this chunk. Currently unused */
	private static final int		_indirectKeyFlag=	0x08;
	/** Flag in the flags byte on disk that means this block is a small free block (total size <= 128).
		The rest of the flags byte is not flags but the number of free bytes following the flags byte.
	*/
	private static final int		_smallFreeFlag=		0x80;
	/** The (non-small free block) flags that are not currently used. */
	private static final int		_illegalFlags= ~(_freeFlag | _compressedFlag | _smallFreeFlag /* _compressedKeyFlag | _indirectKeyFlag */);
	/** The size of chunks to grow the decompression buffer by. @see _readChunk(_Chunk) */
	private static final int		_decompresGrowthChunk= 4096;

	/** Fills in _chunks. Walks all the data in the file, filling in the _chunks list.
		@throws IOException	If there are any IO errors
	*/
	private void _readBlocks() throws IOException {
		long	next= _firstChunk;
		long	max= _storage.length();

		while(next < max) {
			_Chunk	nextChunk= new _Chunk(next);

			_chunks.add(nextChunk);
			next= nextChunk.next();
		}
	}
	/** Reads the data from the given chunk, handling compression if necessary.
		@param chunk		The chunk to read the data from
		@throws IOException	If there are any IO errors
	*/
	private byte[] _readChunk(_Chunk chunk) throws IOException {
		byte[]	data= chunk.get();

		if(chunk.compressed()) {
			Inflater	decompress= new Inflater(true);
			byte[]		decompressed= new byte[data.length + _decompresGrowthChunk];
			byte[]		bigger;
			int			offset= 0;
			int			dataLength= 0;

			try	{
				decompress.setInput(data);
				do	{ // grow buffer to exactly size of decompressed data
					dataLength= decompress.inflate(decompressed, offset, decompressed.length - offset);
					offset+= dataLength;
					if(offset == decompressed.length) {
						dataLength= decompressed.length + _decompresGrowthChunk;
					} else {
						dataLength= offset;
					}
					bigger= new byte[dataLength];
					System.arraycopy(decompressed, 0, bigger, 0, offset);
					decompressed= bigger;
				} while(dataLength != offset);
				return decompressed;
			} catch(DataFormatException exception) {
				// This really should be exception, not exception.toString()
				// but java 1.5 (5.0) does not support that
				throw new IOException(exception.toString());
			}
		}
		return data;
	}

	/** The test suite.
		@param args	You must pass one argument, the path to the storage file.
	*/
	public static void main(String... args) {
		try	{
			StorageFile	f= new StorageFile(new File(args[0]));
			byte[]		buffer= null;

			if(f.remove("test")) {
				System.err.println("Says it removed test");
			}
			if(f.has("test")) {
				System.err.println("Says it has test, but shouldn't");
			}
			if(!f.put("test","This test is bogus".getBytes())) {
				System.err.println("Says it did not put test");
			}
			if(!f.has("test")) {
				System.err.println("Says it does not have test, but should");
			}
			if(f.put("test","This test it good".getBytes())) {
				System.err.println("Says it did put test");
			}
			buffer= f.get("test");
			if(buffer.length != 18) {
				System.err.println("test value size is wrong:"+buffer.length);
			}
			if( !"This test is bogus".equals(new String(buffer)) ) {
				System.err.println("test value was incorrect:"+(new String(buffer)));
			}
			//f.dump();
			if(!f.remove("test")) {
				System.err.println("Says it did not removed test");
			}
			f.put("trying", "                              ".getBytes());
			buffer= f.get("trying");
			if(buffer.length != 30) {
				System.err.println("trying value size is wrong:"+buffer.length);
			}
			if( !"                              ".equals(new String(buffer)) ) {
				System.err.println("trying value was incorrect:"+(new String(buffer)));
			}
			//f.dump();
			if(!f.remove("trying")) {
				System.err.println("Says it did not removed trying");
			}
		} catch(IOException exception) {
			exception.printStackTrace();
			System.err.println(exception);
		}

	}
}

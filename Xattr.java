import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/** Get the xattr's of files on systems that support it.
	<b>TODO</b>
	<ul>
		<li>look into using xattr -l just once on the file
		<li>look into not using -x, if possible (setting them may not be).
				Leopard xattr doesn't support -x
				On setting, only use -x if necessary.
				On getting, load all keys/values and cache them using xattr -l <file>
		<li>test adding attributes and removing them.
		<li>jni xattr due to 1 second per xattr call
		<li>Fill in Documentation and add/update exceptions
	</ul>

<pre>
$ xattr -l inwork/QueueLinesOutputStream.java
com.apple.FinderInfo:
0000   54 45 58 54 21 52 63 68 00 00 00 00 00 00 00 00    TEXT!Rch........
0010   00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00    ................

com.itscommunity.test: true

$ xattr -l ~/Desktop/untitled\ folder/
com.apple.FinderInfo:
00000000  00 00 00 00 00 00 00 00 04 00 00 00 00 00 00 00  |................|
00000010  00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00  |................|
00000020
com.itscommunity.test: testing

com.apple.FinderInfo:
00000000  00 00 00 00 00 00 00 00 04 00 00 00 00 00 00 00  |................|
00000010  00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00  |................|
00000020
com.itscommunity.test: testing

$ xattr -l ~/Desktop/Complete.txt
com.apple.FinderInfo:
00000000  54 45 58 54 21 52 63 68 04 00 00 00 00 00 00 00  |TEXT!Rch........|
00000010  00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00  |................|
00000020
com.apple.ResourceFork:
00000000  00 00 01 00 00 07 E8 DF 00 07 E7 DF 00 00 00 32  |...............2|
00000010  00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00  |................|
00000020  00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00  |................|
...
0007E8C0  82 88 9B 20 98 AA 56 27 EA D1 C6 83 DA 06 41 01  |... ..V'......A.|
0007E8D0  B5 53 CC FD 6E E6 B9 4C 03 55 30 80 C5 FF D9 00  |.S..n..L.U0.....|
0007E8E0  00 01 00 00 07 E8 DF 00 07 E7 DF 00 00 00 32 00  |..............2.|
0007E8F0  00 00 00 68 5E 00 00 00 1C 00 32 00 00 69 63 6E  |...h^.....2..icn|
0007E900  73 00 00 00 0A BF B9 FF FF 00 00 00 00 2E 00 00  |s...............|
0007E910  00                                               |.|
0007e911
com.apple.TextEncoding: UTF-8;134217984
</pre>

*/
public class Xattr extends SystemCall {
	/** Determines if this system supports xattr's
		@return	true if you can use instances of this class
	*/
	public static boolean available() {
		return _available;
	}
	/** Instantiates an Xattr of the given file.
		@param of	The file whose xattr's we're going to work with.
	*/
	public Xattr(File of) throws IOException, InterruptedException {
		_toObserve= of;
		if(!_available) {
			throw new IOException("xattr not available");
		}
		//_cacheXattrs();
	}
	/** Gets the xattr keys of the file.
		@return							They xattr keys for the file
		@throws	IOException
		@throws	InterruptedException
	*/
	String[] keys() throws IOException, InterruptedException {
		String		results= _xattr(null, null, false).trim();

		if(results.length() == 0) {
			return new String[0];
		}
		return results.split("[\\r\\n]+");
	}
	/** Gets the value of an xattr
		@param key	The xattr key
		@return		The value of the xattr
		@throws	IOException
		@throws	InterruptedException
	*/
	byte[] value(String key) throws IOException, InterruptedException {
		return _fromhex(_xattr(key, null, false));
	}
	/** Sets the value of an xattr on a file.
		@param key		The key to set
		@param value	The data for the xattr
		@return			This Xattr is returned for chaining calls.
		@throws	IOException
		@throws	InterruptedException
	*/
	Xattr value(String key, byte[] value) throws IOException, InterruptedException {
		_xattr(key, _tohex(value), false);
		return this;
	}
	/** Removes an xattr from a file.
		@param key		The key to remove
		@return			This Xattr is returned for chaining calls.
		@throws	IOException
		@throws	InterruptedException
	*/
	Xattr remove(String key) throws IOException, InterruptedException {
		_xattr(key, null, true);
		return this;
	}

	/** The file to perform xattr operations on */
	private File					_toObserve;
	/** The path to the xattr command line tool for Mac OS X */
	private static final File		_command= new File("/usr/bin/xattr");
	/** The path to the xattr command, as a string */
	private static final String		_commandStr= _command.getAbsolutePath();
	/** Cache if xattr tool is available on this system */
	private static final boolean	_available= _command.isFile();
	/* Cache of xattrs */
	//private HashMap<String,byte[]>	_xattrs;

	/** Converts a byte array to a hex string.
		Bytes are separated by a tab
		@param data	The data to convert
		@return		The Hexidecimal string
	*/
	private static String _tohex(byte[] data) {
		String	result= "";
		String	prefix= "";

		for(byte b : data) {
			result+= prefix + Integer.toString(b, 16);
			prefix= "\t";
		}
		return result;
	}
	/** Converts a hexidecimal string to bytes.
		Each byte must be separated by whitespace.
		@param data	The hexidecimal string
		@return		The byte equivalent of the string
	*/
	private static byte[] _fromhex(String data) {
		String[]	bytes= data.split("\\s+");
		byte[]		result= new byte[bytes.length];
		int			index= 0;

		for(String b : bytes) {
			result[index]= Integer.valueOf(b, 16).byteValue();
			++index;
		}
		return result;
	}
	/** Calls the xattr command line tool.
		@param key		The key to work with
		@param value	The value to work with
		@param delete	if true, delete the key
		@return			The combiniation of stderr and stdout
		@throws	IOException
		@throws	InterruptedException
	*/
	private String _xattr(String key, String value, boolean delete) throws IOException, InterruptedException {
		String	results= "";

		if(_available) {
			ProcessBuilder	pb;
			Process			proc;
			byte[]			data= new byte[4096];
			InputStream		in;

			if(delete) {
				results= _execute(_commandStr, "-d", key, _toObserve.getAbsolutePath());
			} else if(null != value) {
				results= _execute(_commandStr, "-w", "-x", key, value, _toObserve.getAbsolutePath());
			} else if(null != key) {
				results= _execute(_commandStr, "-p", "-x", key, _toObserve.getAbsolutePath());
			} else {
				results= _execute(_commandStr/*, "-l"*/, _toObserve.getAbsolutePath());
			}
		}
		return results;
	}
	private static final Pattern	_hexData= Pattern.compile("^[0-9A-Fa-f]+\\s+([0-9A-Fa-f][0-9A-Fa-f]\\s+)+\\S+\\s*$");
	private static final Pattern	_keyValue= Pattern.compile("^(\\S+):\\s+(.*)\\s*$");
	private static final Pattern	_key= Pattern.compile("^(\\S+):\\s*$");
	private void _cacheXattrs() throws IOException, InterruptedException {
		String		contents= _xattr(null, null, false);
		String[]	lines= contents.split("[\\r\\n]+");
		String		lastKey= null;
		String		value= null;

		_xattrs= new HashMap<String,byte[]>();
		for(String line : lines) {
			Matcher	hexline= _hexData.matcher(line);
			Matcher	key= _key.matcher(line);
			Matcher	keyValue= _keyValue.matcher(line);
			boolean	isHexline= hexline.find();
			boolean	isKey= key.find();
			boolean isKeyValue= keyValue.find();

			if( isKey || isKeyValue ) {
				if( (null != lastKey) && (null != value) ) {
					byte[]	data= _fromhex(value.trim());

					_xattrs.put(lastKey, data);
					System.out.println("lastKey="+lastKey+" length="+data.length);
					lastKey= null;
					value= null;
				}
			}
			if(isKey) {
				lastKey= key.group(1);
				value= "";
				System.out.println("Starting: "+lastKey);
			} else if(isKeyValue) {
				_xattrs.put(keyValue.group(1), keyValue.group(2).getBytes());
				System.out.println(keyValue.group(1)+"=[["+keyValue.group(2)+"]]");
			} else if(isHexline) {
				value+= hexline.group(1);
				//System.out.println("value="+value);
			}
		}
		System.out.println("done");
	}
/*
$ xattr -l inwork/QueueLinesOutputStream.java
com.apple.FinderInfo:
0000   54 45 58 54 21 52 63 68 00 00 00 00 00 00 00 00    TEXT!Rch........
0010   00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00    ................

com.itscommunity.test: true

$ xattr -l ~/Desktop/untitled\ folder/
com.apple.FinderInfo:
00000000  00 00 00 00 00 00 00 00 04 00 00 00 00 00 00 00  |................|
00000010  00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00  |................|
00000020
com.itscommunity.test: testing

com.apple.FinderInfo:
00000000  00 00 00 00 00 00 00 00 04 00 00 00 00 00 00 00  |................|
00000010  00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00  |................|
00000020
com.itscommunity.test: testing

$ xattr -l ~/Desktop/Complete.txt
com.apple.FinderInfo:
00000000  54 45 58 54 21 52 63 68 04 00 00 00 00 00 00 00  |TEXT!Rch........|
00000010  00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00  |................|
00000020
com.apple.ResourceFork:
00000000  00 00 01 00 00 07 E8 DF 00 07 E7 DF 00 00 00 32  |...............2|
00000010  00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00  |................|
00000020  00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00  |................|
...
0007E8C0  82 88 9B 20 98 AA 56 27 EA D1 C6 83 DA 06 41 01  |... ..V'......A.|
0007E8D0  B5 53 CC FD 6E E6 B9 4C 03 55 30 80 C5 FF D9 00  |.S..n..L.U0.....|
0007E8E0  00 01 00 00 07 E8 DF 00 07 E7 DF 00 00 00 32 00  |..............2.|
0007E8F0  00 00 00 68 5E 00 00 00 1C 00 32 00 00 69 63 6E  |...h^.....2..icn|
0007E900  73 00 00 00 0A BF B9 FF FF 00 00 00 00 2E 00 00  |s...............|
0007E910  00                                               |.|
0007e911
com.apple.TextEncoding: UTF-8;134217984
*/

	/** Test suite. For each file path argument, prints out the keys/values.
		@param	args	The files to display info on.
	*/
	public static void main(String... args) {
		try	{
			for(String arg : args) {
				Xattr	test= new Xattr(new File(arg));

				for(String key : test.keys()) {
					System.out.println(key);
					System.out.println(new String(test.value(key)));
				}
			}
		} catch(IOException ioe) {
			System.err.println("IO Error");
		} catch(InterruptedException inte) {
			System.err.println("Process Error");
		}
	}
}

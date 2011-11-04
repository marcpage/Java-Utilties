import java.io.IOException;
import java.io.File;
import java.net.URLEncoder;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.UnsupportedEncodingException;

public class StorageDirectory implements Storage {
	public StorageDirectory(File directory) throws IOException {
		_location= directory;
		if(!_location.exists()) {
			_location.mkdirs();
		}
		if(!_location.isDirectory()) {
			throw new IOException(directory+" is not a directory");
		}
	}
	/** Get the data for a given key.
		@param key	The unique key in the file.
		@return		The data for the key, or null if not found.
		@throws IOException	If there are any IO errors
	*/
	public byte[] get(String key) throws IOException {
		File			valueFile= new File(_location, _encode(key));
		FileInputStream	valueStream;
		byte[]			buffer;
		int				read= 0, offset= 0;

		if(!valueFile.isFile()) {
			return null;
		}
		valueStream= new FileInputStream(valueFile);
		buffer= new byte[(int)valueFile.length()];
		while(offset < valueFile.length()) {
			read= valueStream.read(buffer, offset, (int)valueFile.length() - offset);
			if(read < 0) {
				throw new IOException("Reached end of file before all data read");
			}
			offset+= read;
		}
		valueStream.close();
		return buffer;
	}
	/** Determines if the key exists in the file.
		@param key	The key to look for.
		@return		true if the key is in the file, false if not
		@throws IOException	If there are any IO errors
	*/
	public boolean has(String key) throws IOException {
		File		valueFile= new File(_location, _encode(key));

		return valueFile.isFile();
	}
	/** Removes a key from the file (marks the space reusable).
		@param key	The key to remove.
		@return		true if the key existed, false otherwise.
		@throws IOException	If there are any IO errors
	*/
	public boolean remove(String key) throws IOException {
		File		valueFile= new File(_location, _encode(key));

		if(!valueFile.isFile()) {
			return false;
		}
		valueFile.delete();
		return true;
	}
	/** Stores a given key/data pair in the file.
		If the key exists already in the file, nothing will be done.
		Allows the buffer to grow (doNotGrow = false).
		@param key			The key for the data.
		@param data			The data to store in the file.
		@return				true if the key/data were added, false if the key already exists
		@throws IOException	If there are any IO errors
	*/
	public boolean put(String key, byte[] data) throws IOException {
		File				valueFile= new File(_location, _encode(key));
		FileOutputStream	valueStream;

		if(valueFile.isFile()) {
			return false;
		}
		valueStream= new FileOutputStream(valueFile);
		valueStream.write(data);
		valueStream.close();
		return true;
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
		return put(key, data);
	}
	/** Gets the number of bytes used on disk for this storage file.
		@return	The number of bytes on disk.
		@throws IOException	If there are any IO errors
	*/
	public long size() throws IOException {
		long	total= 0;

		for(File key : _location.listFiles()) {
			total+= key.length();
		}
		return total;
	}
	/** Returns the total size in the storage file used for either free or key/data blocks.
		@param free		true means add up the size of the free blocks, false means add up the size of the key/data blocks
		@return			The number of bytes used
		@throws IOException	If there are any IO errors
	*/
	public long size(boolean free) throws IOException {
		if(free) {
			return 0;
		}
		return size();
	}
	/// The path to the directory that has the key/values
	private File	_location;
	/**
		@param key	The raw key value
		@return		URL encoded value of the key
	*/
	private static String _encode(String key) throws IOException {
		try	{
			return URLEncoder.encode(key, "UTF-8");
		} catch(UnsupportedEncodingException e) {
			throw new IOException(e.toString());
		}
	}
	/** Test.
		@param args	One argument, the path to the directory to work with
	*/
	public static void main(String... args) {
		try	{
			StorageDirectory	store= new StorageDirectory(new File(args[0]));

			if(store.has("hash/md5/543fa543226")) {
				System.err.println("Should not have hash/md5/543fa543226");
			}
			store.put("hash/md5/543fa543226", "testing".getBytes());
			if(!store.has("hash/md5/543fa543226")) {
				System.err.println("Should have hash/md5/543fa543226");
			}
			store.remove("hash/md5/543fa543226");
			if(store.has("hash/md5/543fa543226")) {
				System.err.println("Should not again have hash/md5/543fa543226");
			}
		} catch(IOException e) {
			e.printStackTrace();
		}
	}
}

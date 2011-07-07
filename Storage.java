import java.io.IOException;

/** Interface for key/value storage

	<b>TODO</b><ul>
		<li>Correct documentation (copied from StorageFile)
		<li>Add method link() that links a key to another key
	</ul>
*/
public interface Storage {
	/** Get the data for a given key.
		@param key	The unique key in the file.
		@return		The data for the key, or null if not found.
		@throws IOException	If there are any IO errors
	*/
	public byte[] get(String key) throws IOException;
	/** Determines if the key exists in the file.
		@param key	The key to look for.
		@return		true if the key is in the file, false if not
		@throws IOException	If there are any IO errors
	*/
	public boolean has(String key) throws IOException;
	/** Removes a key from the file (marks the space reusable).
		@param key	The key to remove.
		@return		true if the key existed, false otherwise.
		@throws IOException	If there are any IO errors
	*/
	public boolean remove(String key) throws IOException;
	/** Stores a given key/data pair in the file.
		If the key exists already in the file, nothing will be done.
		Allows the buffer to grow (doNotGrow = false).
		@param key			The key for the data.
		@param data			The data to store in the file.
		@return				true if the key/data were added, false if the key already exists in the file
		@throws IOException	If there are any IO errors
	*/
	public boolean put(String key, byte[] data) throws IOException;
	/** Stores a given key/data pair in the file.
		If the key exists already in the file, nothing will be done.
		@param key			The key for the data.
		@param data			The data to store in the file.
		@param doNotGrow	If true, the file will not be expanded to add this key/data
		@return				true if the key/data were added, false if the key already exists in the file
		@throws IOException	If there are any IO errors
	*/
	public boolean put(String key, byte[] data, boolean doNotGrow) throws IOException;
	/** Gets the number of bytes used on disk for this storage file.
		@return	The number of bytes on disk.
		@throws IOException	If there are any IO errors
	*/
	public long size() throws IOException;
	/** Returns the total size in the storage file used for either free or key/data blocks.
		@param free		true means add up the size of the free blocks, false means add up the size of the key/data blocks
		@return			The number of bytes used
		@throws IOException	If there are any IO errors
	*/
	public long size(boolean free) throws IOException;
}


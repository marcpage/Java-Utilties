import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

/** Get file system information about a node in the filesystem.

	<br><b>TODO</b><ul>
		<li>Be able to set permissions, dates, etc.
		<li>Document
		<li>Look into handling: st_dev, st_ino, st_mode, st_nlink, st_uid, st_gid, st_rdev, st_blksize, st_blocks
	</ul>

/usr/bin/chflags <st_flags in octal> <path>
/bin/chmod 0<last for digits of st_mode>

*/
public class Stat extends SystemCall {
	/** <a href="http://linux.die.net/man/2/stat">stat S_IFDIR</a>
		@see #type()
		@see #available(int) */
	public static final int	IsDirectory			= 0040000;
	/** <a href="http://linux.die.net/man/2/stat">stat S_IFREG</a>
		@see #type()
		@see #available(int) */
	public static final int	IsFile				= 0100000;
	/** <a href="http://linux.die.net/man/2/stat">stat S_IFLNK</a>
		@see #type()
		@see #available(int) */
	public static final int	IsLink				= 0120000;
	/** <a href="http://linux.die.net/man/2/stat">stat S_IRUSR | S_IRGRP | S_IROTH</a>
		@see #permissions()
		@see #available(int)
		@see #readable() */
	public static final int	IsReadableMask		= 00444;
	/** <a href="http://linux.die.net/man/2/stat">stat S_IWUSR | S_IWGRP | S_IWOTH</a>
		@see #permissions()
		@see #available(int)
		@see #writable() */
	public static final int IsWritableMask		= 00222;
	/** <a href="http://linux.die.net/man/2/stat">stat S_IXUSR | S_IXGRP | S_IXOTH</a>
		@see #permissions()
		@see #available(int)
		@see #executable() */
	public static final int IsExecutableMask	= 00111;
	/** <a href="http://linux.die.net/man/2/stat">stat S_ISUID</a>
		@see #permissions()
		@see #available(int) */
	public static final int IsSetUserIDMask		= 04000;
	/** <a href="http://linux.die.net/man/2/stat">stat S_ISGID</a>
		@see #permissions()
		@see #available(int) */
	public static final int IsSetGroupIDMask	= 02000;
	/** <a href="http://linux.die.net/man/2/stat">stat S_ISVTX</a>
		@see #permissions()
		@see #available(int) */
	public static final int IsStickyMask		= 01000;
	/** <a href="http://www.manpagez.com/man/1/chflags/">chflags hidden</a>
		@see #flags()
		@see #available(int) */
	public static final int IsHidden			= 0100000;
	/** <a href="http://www.manpagez.com/man/1/chflags/">chflags opaque</a>
		@see #flags()
		@see #available(int) */
	public static final int IsOpaque			= 0000010; // has to do with file system mounts
	/** <a href="http://www.manpagez.com/man/1/chflags/">chflags uappnd, uappend</a>
		@see #flags()
		@see #available(int) */
	public static final int IsAppendOnly		= 0000004;
	/** <a href="http://www.manpagez.com/man/1/chflags/">chflags nodump</a>
		@see #flags()
		@see #available(int)
		@see #backup()  */
	public static final int NoBackup			= 0000001;
	/** <a href="http://www.manpagez.com/man/1/chflags/">chflags uchg, uchange, uimmutable</a>
		@see #flags()
		@see #available(int)
		@see #writable() */
	public static final int IsLocked			= 0000002;
	/** not a system value, just used for available(int)
		@see #available(int) */
	public static final int	Created				= 0111111;
	/** not a system value, just used for available(int)
		@see #available(int) */
	public static final int Accessed			= 0222222;
	/** not a system value, just used for available(int)
		@see #available(int) */
	public static final int Birthed				= 0333333;
	
	/** Determines if this version of Java and this Operating System support checking a specified field.
		@param check One of the constants in this class.
						NOTE: IsFile and IsHidden have the same value, but IsHidden will always be used.
								IsFile is always true, so you shouldn't check that.
		@return		true if you can check that field without causing an UnsupportedOperationException
	*/
	public static boolean available(int check) {
		switch(check) {
			case IsExecutableMask:
				return _available || (null != _canExecute);
			case IsOpaque:
			case IsAppendOnly:
			case NoBackup:
			case IsLocked:
			case Created:
			case Accessed:
			case Birthed:
				return _available;
			default:
				return true;
		}
	}
	/** Create a Stat for the given file.
		@param f	The file to stat.
	*/
	public Stat(File f) {
		_toStat= f;
		_stat= null;
	}
	/** Determines if the file is readable.
		@see java.io.File#canRead()
		@return	If full stat support is available (and does not throw exceptions),
					then returns true if User, Group or Other can read,
					otherwise the File method canRead is used
	*/
	public boolean readable() {
		if(_available) {
			try	{
				return (permissions() & IsReadableMask) != 0;
			} catch(IOException e1) {
			} catch(InterruptedException e2) {
			}
		}
		return _toStat.canRead();
	}
	/** Determines if the file is writeable.
		@see java.io.File#canWrite()
		@return	If full stat support is available (and does not throw exceptions),
					then returns true if User, Group or Other can write and not locked,
					otherwise the File method canWrite is used
	*/
	public boolean writable() {
		if(_available) {
			try	{
				return ((permissions() & IsWritableMask) != 0) && ((flags() & IsLocked) == 0);
			} catch(IOException e1) {
			} catch(InterruptedException e2) {
			}
		}
		return _toStat.canWrite();
	}
	/** Determines if the file is hidden.
		@see java.io.File#isHidden()
		@return	If full stat support is available (and does not throw exceptions),
					then returns true if User, Group or Other can write,
					otherwise the File method canWrite is used
	*/
	public boolean hidden() {
		if(_available) {
			try	{
				return (flags() & IsHidden) != 0;
			} catch(IOException e1) {
			} catch(InterruptedException e2) {
			}
		}
		return _toStat.isHidden();
	}
	/** Determines if the file is executable.
		@return	If full stat support is available (and does not throw exceptions),
					then returns true if User, Group or Other can execute.
					If Java 1.6 or later the File method canExecute is used.
					otherwise UnsupportedOperationException is thrown
		@throws UnsupportedOperationException if this Operating System/Java version
												cannot determine if the file is executable
	*/
	public boolean executable() throws UnsupportedOperationException {
		if(_available) {
			try	{
				return (permissions() & IsExecutableMask) != 0;
			} catch(IOException e1) {
			} catch(InterruptedException e2) {
			}
		}
		if(null == _canExecute) {
			throw new UnsupportedOperationException();
		}
		try	{
			return ((Boolean)_canExecute.invoke(_toStat)).booleanValue();
		} catch(IllegalAccessException exception) {
			throw new UnsupportedOperationException(exception.toString());
		} catch(InvocationTargetException e2) {
			throw new UnsupportedOperationException(e2.toString());
		}
	}
	/** Determines if the file is available for backup.
		@return	If full stat support is available (and does not throw exceptions),
					then returns true if the file is not marked for backup exclusion.
					otherwise true is returned.
	*/
	public boolean backup() {
		if(_available) {
			try	{
				return (flags() & NoBackup) == 0;
			} catch(IOException e1) {
			} catch(InterruptedException e2) {
			}
		}
		return true;
	}
	/** Determines if the item is a file.
		@see java.io.File#isFile()
		@return	If full stat support is available (and does not throw exceptions),
					then returns true if the path represents a file,
					otherwise the File method isFile is used
	*/
	public boolean file() {
		if(_available) {
			try	{
				return type() == IsFile;
			} catch(IOException e1) {
			} catch(InterruptedException e2) {
			}
		}
		return _toStat.isFile();
	}
	/** Determines if the item is a directory.
		@see java.io.File#isDirectory()
		@return	If full stat support is available (and does not throw exceptions),
					then returns true if the path represents a directory,
					otherwise the File method isDirectory is used
	*/
	public boolean directory() {
		if(_available) {
			try	{
				return type() == IsDirectory;
			} catch(IOException e1) {
			} catch(InterruptedException e2) {
			}
		}
		return _toStat.isDirectory();
	}
	/** Determines if the item is a link.
		@return	If full stat support is available (and does not throw exceptions),
					then returns true if the path represents a symlink,
					otherwise returns false
	*/
	public boolean link() {
		if(_available) {
			try	{
				return type() == IsLink;
			} catch(IOException e1) {
			} catch(InterruptedException e2) {
			}
		}
		return false;
	}
	/** Determines size of a file.
		See <a href="http://linux.die.net/man/2/stat">st_size</a>
		@see java.io.File#length()
		@return	If full stat support is available (and does not throw exceptions),
					then returns the size of the path
					otherwise the File method length is used.
					NOTE: If the item is not a file, the result is undefined
	*/
	public long size() {
		if(_available) {
			try	{
				_calculate();
				return Long.parseLong(_stat.get("st_size"));
			} catch(IOException e1) {
			} catch(InterruptedException e2) {
			}
		}
		return _toStat.length();
	}
	/** Gets the modification timestamp in Java Epoch in milliseconds.
		Luckily, the UNIX and Java Epochs are the same.
		See <a href="http://linux.die.net/man/2/stat">st_mtime</a>
		@see java.io.File#lastModified()
		@return	If full stat support is available (and does not throw exceptions),
					then returns the date of the last modification of the path
					otherwise the File method lastModified is used.
	*/
	public long modified() {
		if(_available) {
			try	{
				_calculate();
				return 1000 * Long.parseLong(_stat.get("st_mtime"));
			} catch(IOException e1) {
			} catch(InterruptedException e2) {
			}
		}
		return _toStat.lastModified();
	}
	/** Gets the last accessed timestamp in Java Epoch in milliseconds.
		Only supported if available.
		Luckily, the UNIX and Java Epochs are the same.
		See <a href="http://linux.die.net/man/2/stat">st_atime</a>
		@return	If full stat support is available (and does not throw exceptions),
					then returns the date of the last accessed of the path
		@throws IOException 					If there is a problem calling stat command
		@throws InterruptedException			If there is a problem calling stat command
		@throws UnsupportedOperationException	If this system does not support the stat command
	*/
	public long accessed() throws IOException, InterruptedException, UnsupportedOperationException {
		if(!_available) {
			throw new UnsupportedOperationException();
		}
		_calculate();
		return 1000 * Long.parseLong(_stat.get("st_atime"));
	}
	/** Gets the created timestamp in Java Epoch in milliseconds.
		Only supported if available.
		Luckily, the UNIX and Java Epochs are the same.
		See <a href="http://linux.die.net/man/2/stat">st_ctime</a>
		@return	If full stat support is available (and does not throw exceptions),
					then returns the created date of the path
		@throws IOException 					If there is a problem calling stat command
		@throws InterruptedException			If there is a problem calling stat command
		@throws UnsupportedOperationException	If this system does not support the stat command
	*/
	public long created() throws IOException, InterruptedException, UnsupportedOperationException {
		if(!_available) {
			throw new UnsupportedOperationException();
		}
		_calculate();
		return 1000 * Long.parseLong(_stat.get("st_ctime"));
	}
	/** Gets the birthed timestamp in Java Epoch in milliseconds.
		Only supported if available.
		Luckily, the UNIX and Java Epochs are the same.
		See <a href="http://linux.die.net/man/2/stat">st_birthtime</a>
		@return	If full stat support is available (and does not throw exceptions),
					then returns the birthed date of the path
		@throws IOException 					If there is a problem calling stat command
		@throws InterruptedException			If there is a problem calling stat command
		@throws UnsupportedOperationException	If this system does not support the stat command
	*/
	public long birthed() throws IOException, InterruptedException, UnsupportedOperationException {
		if(!_available) {
			throw new UnsupportedOperationException();
		}
		_calculate();
		return 1000 * Long.parseLong(_stat.get("st_birthtime"));
	}
	/** Gets the type of the file.
		@return	If full stat support is available (and does not throw exceptions),
					then returns <a href="http://linux.die.net/man/2/stat">st_mode & S_IFMT</a>
					otherwise returns -1
	*/
	public int type() throws IOException, InterruptedException {
		if(_available) {
			_calculate();
			return Integer.parseInt(_stat.get("st_mode"), 8)&TYPE_MASK;
		}
		return -1;
	}
	/** Gets the permissions of the file.
		@return	If full stat support is available (and does not throw exceptions),
					then returns <a href="http://linux.die.net/man/2/stat">st_mode & (S_IRWXU | S_IRWXG | S_IRWXO)</a>
					otherwise returns -1
	*/
	public int permissions() throws IOException, InterruptedException {
		if(_available) {
			_calculate();
			return Integer.parseInt(_stat.get("st_mode"), 8)&PERMISSIONS_MASK;
		}
		return -1;
	}
	/** Gets the permissions of the file.
		@return	If full stat support is available (and does not throw exceptions),
					then returns <a href="http://linux.die.net/man/2/stat">st_flags</a> field
					otherwise returns -1
	*/
	public int flags() throws IOException, InterruptedException {
		if(_available) {
			_calculate();
			return Integer.parseInt(_stat.get("st_flags"));
		}
		return -1;
	}
	/** The file we are working with */
	private File					_toStat;
	/** The results of the stat command */
	private HashMap<String,String>	_stat;
	/** The java.io.File.canExecute method, if it is available in this version of Java (1.6 and later) */
	private static Method			_canExecute= _lookupFileMethod("canExecute");
	/** The path to the stat command */
	private static final File		_statCommand= new File("/usr/bin/stat");
	/** The path to the chflags command */
	private static final File		_chflagsCommand= new File("/usr/bin/chflags");
	/** The path to the chmod command */
	private static final File		_chmodCommand= new File("/bin/chmod");
	/** Are the unix command line tools available? */
	private static final boolean	_available= _statCommand.isFile() && _chflagsCommand.isFile() && _chmodCommand.isFile();
	/** <a href="http://linux.die.net/man/2/stat">S_IFMT</a> st_mode Item Type Mask */
	private static final int		TYPE_MASK= 0170000;
	/** <a href="http://linux.die.net/man/2/stat">S_IRWXU | S_IRWXG | S_IRWXO</a> st_mode permissions mask */
	private static final int		PERMISSIONS_MASK= 07777;
	/** Calls the system command: stat -s <file> in fills in _stat map.
		The -s paramter gives us st_*=<value> separated by spaces.
		We parse these and put them in a _stat.
		<B>Precondition</b>: If _stat is null, nothing is done
		<b>Postcondition</b>: _stat is either null, of filled in with the stat values
		@throws IOException 			if there is an IO error reading the merged stderr/stdout stream
		@throws InterruptedException	If the call is interrupted
	*/
	private void _calculate() throws IOException, InterruptedException {
		if(null == _stat) {
			String		results= _execute(_statCommand.getAbsolutePath(), "-s", _toStat.getAbsolutePath()).trim();
			String[]	fields= results.split(" ");
			
			_stat= new HashMap<String,String>(15);
			for(String field : fields) {
				String[]	keyValue= field.split("=",2);
				
				if(keyValue.length == 2) {
					_stat.put(keyValue[0], keyValue[1]);
				}
			}
		}
	}
	/** Looks up a method on the File class.
		@param name	The name of the method to lookup
		@return		The Method on File, or null of the method is not found
	*/
	private static Method _lookupFileMethod(String name) {
		try	{
			File		dummy= new File("dummy");
			Class<?>	fileClass= (dummy).getClass();
			
			_canExecute= fileClass.getDeclaredMethod(name);
		} catch(NoSuchMethodException exception) {
		}
		return null;
	}
	/** Test.
		@param args	Files to get stat information on
	*/
	public static void main(String... args) {
		java.text.SimpleDateFormat	format= new java.text.SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
		
		for(String arg : args) {
			try	{
				Stat	stat= new Stat(new File(arg));
				System.out.println(arg);
				System.out.println("\t"+"flags="+stat.flags());
				System.out.println("\t"+"permissions="+stat.permissions());
				System.out.println("\t"+"type="+stat.type());
				System.out.println("\t"+"size="+stat.size());
				System.out.println("\t"+"readable="+stat.readable());
				System.out.println("\t"+"writable="+stat.writable());
				System.out.println("\t"+"hidden="+stat.hidden());
				System.out.println("\t"+"file="+stat.file());
				System.out.println("\t"+"directory="+stat.directory());
				System.out.println("\t"+"link="+stat.link());
				System.out.println("\t"+"modified="+stat.modified()+" "+format.format(new java.util.Date(stat.modified())));
				if(Stat.available(Stat.IsExecutableMask)) {
					System.out.println("\t"+"executable="+stat.executable());
				}
				if(Stat.available(Stat.NoBackup)) {
					System.out.println("\t"+"backup="+stat.backup());
				}
				if(Stat.available(Stat.Accessed)) {
					System.out.println("\t"+"accessed="+stat.accessed()+" "+format.format(new java.util.Date(stat.accessed())));
				}
				if(Stat.available(Stat.Created)) {
					System.out.println("\t"+"created="+stat.created()+" "+format.format(new java.util.Date(stat.created())));
				}
				if(Stat.available(Stat.Birthed)) {
					System.out.println("\t"+"birthed="+stat.birthed()+" "+format.format(new java.util.Date(stat.birthed())));
				}
			} catch(IOException io) {
				io.printStackTrace();
			} catch(InterruptedException interrupt) {
				interrupt.printStackTrace();
			}
		}
	}
}
// import java.text.SimpleDateFormat;

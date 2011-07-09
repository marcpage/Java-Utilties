import java.io.File;
import java.io.IOException;
import java.util.HashMap;

/**
	<b>TODO</b><ul>
		<li>Be able to set permissions
		<li>Catch NumberFormatException
		<li>tests
		<li>Document
		<li>Use native java calls if not available
		<li>Have a way to determine if a value is available on this OS (isExecutable on java 1.5)
		<li>Use latest APIs (isExecutable) by looking them up and calling them
		<li>parse out the dates (figure out the epoch)
	</ul>

/usr/bin/stat -s Manifest.java 
st_dev=234881027 st_ino=27412188 st_mode=0100644 st_nlink=1 st_uid=502 st_gid=20 st_rdev=0 st_size=8177 st_atime=1310088473 st_mtime=1310086939 st_ctime=1310086939 st_birthtime=1310086939 st_blksize=4096 st_blocks=16 st_flags=0

/usr/bin/chflags <st_flags in octal> <path>
/bin/chmod 0<last for digits of st_mode>

*/

class Stat extends SystemCall {
	public static final int	IsDirectory			= 004;
	public static final int	IsFile				= 010;
	public static final int	IsLink				= 012;
	public static final int	IsReadableMask		= 00444;
	public static final int IsWritableMask		= 00222;
	public static final int IsExecutableMask	= 00111;
	public static final int IsSetUserIDMask		= 04000;
	public static final int IsSetGroupIDMask	= 02000;
	public static final int IsStickyMask		= 01000;
	public static final int IsHidden			= 0100000;
	public static final int IsOpaque			= 0000010; // has to do with file system mounts
	public static final int IsAppendOnly		= 0000004;
	public static final int NoBackup			= 0000001;
	public static final int IsLocked			= 0000002;
	
	public static boolean available() {
		return false;
	}
	public Stat(File f) throws IOException, InterruptedException {
		_toStat= f;
		_stat= null;
	}
	public boolean readable() {
		return (permissions() & IsReadableMask) != 0;
	}
	public boolean writable() {
		return ((permissions() & IsWritableMask) != 0) && ((flags() & IsLocked) == 0);
	}
	public boolean hidden() {
		return (flags() & IsHidden) != 0;
	}
	public boolean executable() {
		return (permissions() & IsExecutableMask) != 0;
	}
	public boolean backup() {
		return (flags() & NoBackup) == 0;
	}
	public boolean file() {
		return type() == IsFile;
	}
	public boolean directory() {
		return type() == IsDirectory;
	}
	public boolean link() {
		return type() == IsLink;
	}
	public long size() { // NumberFormatException
		return Long.parseLong(_stat.get("st_size"));
	}
	public int type() {
		calculate();
		return Integer.parseInt(_stat.get("st_mode").substring(1,3), 8)&TYPE_MASK;
	}
	public int permissions() {
		calculate();
		return Integer.parseInt(_stat.get("st_mode").substring(4), 8)&PERMISSIONS_MASK;
	}
	public int flags() {
		calculate();
		return Integer.parseInt(_stat.get("st_flags"));
	}
	private File					_toStat;
	private HashMap<String,String>	_stat;
	private static final File		_statCommand= new File("/usr/bin/stat");
	private static final File		_chflagsCommand= new File("/usr/bin/chflags");
	private static final File		_chmodCommand= new File("/bin/chmod");
	private static final boolean	_available= _statCommand.isFile() && _chflagsCommand.isFile() && _chmodCommand.isFile();
	private static final int		TYPE_MASK= 017;
	private static final int		PERMISSIONS_MASK= 07777;
	
	private void _calculate() {
		if(null == _stat) {
			String		results= _execute(_statCommand.getAbsolutePath(), "-s", _toStat.getAbsolutePath());
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
}

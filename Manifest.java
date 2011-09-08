import java.io.Reader;
import java.io.File;
import java.io.IOException;
import java.security.MessageDigest; // MD5, SHA-1, SHA-256, SHA-384, SHA-512
import java.security.DigestException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.io.FileInputStream;

/**
	See <a href="http://download.oracle.com/javase/1.5.0/docs/guide/security/CryptoSpec.html#AppA">MessageDigest Algorithms</a>

		<b>TODO</b><ul>
			<li>Document
			<li>Add (parts_)<hash>_DOS, (parts_)<hash>_Unix, (parts_)<hash>_Mac for text files (no null) that are converted to that type.
				Do not add (parts_)<hash>_Unix for Unix text files, etc.
			<li>Ignore modification date in equals
			<li>Ignore xattr and link in equals
			<li>Find a better encoding for non-text xattr values in Manifest(location)
					Possibly override set(String,byte[])
			<li>Take encoding into account when comparing
			<li>_handleFile should make sure chunks are the expected size of the last chunk, in case of partial reads
			<li>In _handleFile() use Storage.link() to link other hashes to the same data
			<li>Handle the case in _handleFile() where a message digest is not supported and it's the one we chose for the main key
			<li>Multithread to take account for calculating hashes vs waiting for disk. Right now xattr is the most expensive.
			<li>Make sure SimpleDateFormat prints in UTC, not local timezone
		</ul>

*/
public class Manifest extends Ini {
	public static interface Filter {
		boolean skip(File item);
	}
	public static class DotDirFilter implements Filter {
		public boolean skip(File path) {
			return path.getName().charAt(0) == '.';
		}
	}
	public Manifest(Reader in) throws IOException {
		super(in);
	}
	public Manifest(File location, Storage parts, Filter filter) throws IOException {
		super();
		set("name", location.getName());
		long	size= _add(location, null, parts, filter);
		set("size", ""+size);
	}
	public boolean equals(Object obj) {
		if(obj instanceof Ini) {
			Ini	other= (Ini)obj;

			for(String section : sections()) {
				for(String key : keys()) {
					String	myValue= get(section, key, null);
					String theirValue= other.get(section, key, null);

					if( (null == myValue) && (null != theirValue)
							|| (null != myValue) && (null == theirValue)
							|| (null != myValue) && !myValue.equals(theirValue) ) {
						return false;
					}
				}
			}
			for(String section : other.sections()) {
				for(String key : other.keys()) {
					String	myValue= get(section, key, null);
					String theirValue= other.get(section, key, null);

					if( (null == myValue) && (null != theirValue)
							|| (null != myValue) && (null == theirValue)
							|| (null != myValue) && !myValue.equals(theirValue) ) {
						return false;
					}
				}
			}
			return true;
		}
		return false;
	}
	private static final boolean	_xattr= Xattr.available();
	private static final boolean	_executable= Stat.available(Stat.IsExecutableMask);
	private static final boolean	_opaque= Stat.available(Stat.IsOpaque);
	private static final boolean	_append= Stat.available(Stat.IsAppendOnly);
	private static final boolean	_backup= Stat.available(Stat.NoBackup);
	private static final boolean	_locked= Stat.available(Stat.IsLocked);
	private static final boolean	_created= Stat.available(Stat.Created);
	private static final boolean	_hidden= Stat.available(Stat.IsHidden);
	private static final boolean	_flags= Stat.available(Stat.Flags);
	private static final boolean	_permissions= Stat.available(Stat.Permissions);
	private static final boolean	_stat_link= Stat.available(Stat.IsLink);
	private static final boolean	_link= Link.available();
	private static final String		_true= "true";
	private static final String		_false= "false";
	private static final String[]	_supportedHashes= "MD5,SHA-1".split(","); // ,SHA-256,SHA-384,SHA-512".split(",");
	private static final String		_dateFormat= "yyyy/MM/dd,hh-mm-ss";
	private long _add(File location, String section, Storage parts, Filter filter) throws IOException {
		long	size= 0;
		Stat	info= new Stat(location);
		boolean	isLink= _handleLinks(location, info, section);
		_handleXattr(location, section);
		_handleNodeFlags(info, section);
		if(!isLink) {
			if(info.directory()) {
				size= _handleDirectory(location, section, parts, filter);
			} else if(info.file()) {
				_handleFile(location, info, section, parts);
				size= location.length();
			}
		}
		return size;
	}
	private static final String	kHexDigits= "0123456789abcdef";
	private static String _hashToHexString(byte[] hash, int bytes) {
		char[]	characters= new char[bytes * 2];
		int		index= 0;

		for(int b= 0; b < bytes; ++b) {
			int	bint= hash[b];

			if(bint < 0) {
				bint+= 256;
			}
			characters[index]= kHexDigits.charAt(bint >> 4);
			characters[index + 1]= kHexDigits.charAt(bint & 0x0F);
			index+= 2;
		}
		return new String(characters);
	}
	private static long last= System.nanoTime();
	private void _handleFile(File location, Stat info, String section, Storage parts) throws IOException {
		set(section, "size", ""+info.size());

		MessageDigest[]	globalHashes= new MessageDigest[_supportedHashes.length];
		MessageDigest[]	chunkHashes= new MessageDigest[_supportedHashes.length];
		String[]		hashes= new String[_supportedHashes.length];
		String[]		partList= new String[_supportedHashes.length];
		FileInputStream	in= new FileInputStream(location);
		byte[]			buffer= new byte[1024 * 1024];
		int				amountRead;
		int				lastRead= 0;
		int				blocks= 0;

		for(int h= 0; h < _supportedHashes.length; ++h) {
			try	{
				hashes[h]= "";
				partList[h]= null;
				globalHashes[h]= MessageDigest.getInstance(_supportedHashes[h]);
				chunkHashes[h]= MessageDigest.getInstance(_supportedHashes[h]);
			} catch(NoSuchAlgorithmException exception) {
				globalHashes[h]= null;
				chunkHashes[h]= null;
			}
		}

		byte[]	digest= null;
		int		digestSize;

		do	{
			amountRead= in.read(buffer);
			if(amountRead > 0) {
				String	key;
				++blocks;

				for(int h= 0; h < _supportedHashes.length; ++h) {
					int	digestSizeRequired= globalHashes[h].getDigestLength();

					if( (null == digest) || (digestSizeRequired > digest.length) ) {
						digest= new byte[digestSizeRequired];
					}
					if(null != globalHashes[h]) {
						globalHashes[h].update(buffer, 0, amountRead);
					}
					if(null != chunkHashes[h]) {
						chunkHashes[h].reset();
						try {
							chunkHashes[h].update(buffer, 0, amountRead);
							digestSize= chunkHashes[h].digest(digest, 0, digest.length);
							hashes[h]= _hashToHexString(digest, digestSize);
						} catch(DigestException exception) {
							hashes[h]= "0";
						}
						if(null == partList[h]) {
							partList[h]= ""+amountRead+":"+hashes[h];
						} else if(lastRead != amountRead) {
							partList[h]= ";"+amountRead+":"+hashes[h];
						} else {
							partList[h]= ","+hashes[h];
						}
					} else {
						hashes[h]= "";
					}
				}
				lastRead= amountRead;
				key= "hash/"+_supportedHashes[_supportedHashes.length - 1]+"/"+hashes[_supportedHashes.length - 1];
				if(null != parts) {
					parts.put(key, buffer);
				}
			}
		} while(amountRead >= 0);
		for(int h= 0; h < _supportedHashes.length; ++h) {
			int	digestSizeRequired= globalHashes[h].getDigestLength();

			if( (null == digest) || (digestSizeRequired > digest.length) ) {
				digest= new byte[digestSizeRequired];
			}
			try	{
				digestSize= globalHashes[h].digest(digest, 0, digest.length);

				set(section, _supportedHashes[h], _hashToHexString(digest, digestSize));
				if(blocks > 1) {
					set(section, "parts_"+_supportedHashes[h], partList[h]);
				}
			} catch(DigestException exception) {
				// can't seem to get the hash
			}
		}
	}
	private long _handleDirectory(File location, String section, Storage parts, Filter filter) throws IOException {
		String	prefix;
		long	size= 0;
		String	sectionName= section;

		if(null == sectionName) {
			sectionName= "";
			prefix= "";
		} else {
			prefix= "/";
		}
		for(String item : location.list()) {
			File	path= new File(location, item);

			if( (null == filter) || !filter.skip(path) ) {
				size+= _add(path, sectionName+prefix+item, parts, filter);
			}
		}
		set(section, "size",""+size);
		return size;
	}
	private void _handleUnixFlags(String section, String name, Stat info, int flag, boolean supported, boolean printedValue) {
		if(supported) {
			int	flags= info.flags();

			if( (-1 != flags) && ( printedValue == ( (flags & flag) == flag ) ) ) {
				String value;

				if(printedValue) {
					value= _true;
				} else {
					value= _false;
				}
				set(section, name, value);
			}
		}
	}
	private void _handleNodeFlags(Stat info, String section) {
		if(_executable && info.executable()) {
			set(section, "executable", "true");
		}
		_handleUnixFlags(section, "opaque", info, Stat.IsOpaque, _opaque, true);
		_handleUnixFlags(section, "append-only", info, Stat.IsAppendOnly, _append, true);
		_handleUnixFlags(section, "locked", info, Stat.IsLocked, _locked, true);
		if( _backup && !info.backup() ) {
			set(section, "backup", "false");
		}
		if( _hidden && info.hidden() ) {
			set(section, "hidden", "true");
		}
		if(_created) {
			try	{
				set(section, "created", (new SimpleDateFormat(_dateFormat)).format(new Date(info.created())));
			} catch(IOException io) {
				// we just don't set the value if we get an exception
			} catch(InterruptedException interrupt) {
				// we just don't set the value if we get an exception
			}
		}
		if(_flags) {
			int	flags= info.flags();

			if( (-1 != flags) && (0 != flags) ) {
				set(section, "unix-flags", ""+flags);
			}
		}
		if(_permissions) {
			int	permissions= info.permissions();

			if(-1 != permissions) {
				set(section, "unix-permissions-octal", "0"+Integer.toOctalString(permissions));
			}
		}
		if(!info.writable()) {
			set(section, "read-only", "true");
		}
		set(section, "modified", (new SimpleDateFormat(_dateFormat)).format(new Date(info.modified())));
	}
	private void _handleXattr(File location, String section) {
		if(_xattr) {
			try	{
				Xattr	info= new Xattr(location);

				for(String key : info.keys()) {
					set(section, "xattr_"+key, new String(info.value(key)));
				}
			} catch(IOException io) {
				System.err.println("io error on "+location+": "+io);
			} catch(InterruptedException interrupted) {
				System.err.println("interrupted error on "+location+": "+interrupted);
			}
		}
	}
	private boolean _handleLinks(File location, Stat info, String section) {
		boolean	isLink= false;

		if( _link && ( !_stat_link || info.link() ) ) {
			try	{
				Link	linkInfo= new Link(location);

				isLink= linkInfo.isLink();
				if(isLink) {
					set(section, "link", linkInfo.target());
				}
			} catch(IOException io) {
			} catch(InterruptedException interrupted) {
			}
		}
		return isLink;
	}
	public static void main(String... args) {
		try	{
			java.io.OutputStreamWriter	out= new java.io.OutputStreamWriter(System.out);
			for(String arg : args) {
				System.out.println("--- "+arg+" ---");
				(new Manifest(new File(arg),null, new DotDirFilter())).write(out);
			}
		} catch(IOException exception) {
			exception.printStackTrace();
		}
	}
}

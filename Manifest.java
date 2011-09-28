import java.io.Reader;
import java.io.File;
import java.io.IOException;
import java.security.MessageDigest; // MD5, SHA-1, SHA-256, SHA-384, SHA-512
import java.security.DigestException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.io.FileInputStream;

/** Manages uniquely identifying a directory with all its meta-data.
	See <a href="http://download.oracle.com/javase/1.5.0/docs/guide/security/CryptoSpec.html#AppA">MessageDigest Algorithms</a>

		<p><b>TODO</b><ul>
			<li>Document
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
			<li>Add a way to look up hash location
		</ul>

*/
public class Manifest extends Ini {
	/** A filter to determine if an item is to be used in the manifest when scanning disk.
	*/
	public static interface Filter {
		/** Should we skip this item.
			@param item	A file or directory that we may need to skip.
			@return		<code>true</code> if this item (and its children) should not be included in the manifest.
		*/
		boolean skip(File item);
	}
	/** Filters out files and folders that are hidden on Unix systems (begin with a dot (.)).
	*/
	public static class DotDirFilter implements Filter {
		/** Does its name start with a dot?
			@param path	The item to test
			@return		<code>true</code> if the filename starts with a dot (.)
		*/
		public boolean skip(File path) {
			return path.getName().charAt(0) == '.';
		}
	}
	/** Combine a series of filters using OR logic.
	*/
	public static class AnyFilter implements Filter {
		/**
			@param filters	The filters to OR together
		*/
		public AnyFilter(Filter... filters) {
			_filters= filters;
		}
		/** Will skip if any of the filters request to skip.
			@param path	The path to examine
			@return		<code>true</code> if any of the filters request to skip this <code>path</code>
		*/
		public boolean skip(File path) {
			for(Filter filter : _filters) {
				if(filter.skip(path)) {
					return true;
				}
			}
			return false;
		}
		private Filter[]	_filters;
	}
	/** Combine a series of filters using AND logic.
	*/
	public static class AllFilters implements Filter {
		/**
			@param filters	The filters to AND together
		*/
		public AllFilters(Filter... filters) {
			_filters= filters;
		}
		/** Will skip iff all of the filters request to skip.
			@param path	The path to examine
			@return		<code>true</code> iff all of the filters request to skip this <code>path</code>
		*/
		public boolean skip(File path) {
			for(Filter filter : _filters) {
				if(!filter.skip(path)) {
					return false;
				}
			}
			return true;
		}
		private Filter[]	_filters;
	}
	/** Reads a Minifest from a stream.
		@param in	The stream to read the manifest from.
		@throws IOException	on io error
	*/
	public Manifest(Reader in) throws IOException {
		super(in);
	}
	/** Creates a Manifest by analyzing a path.
		@param location		The path (usually a directory) to create a Manifest of.
		@param parts		The key-store to store hash (key) and contents (value). May be null.
		@param filter		The object to examine each file or directory to determine if we should
								exclude it from the manifest. This is to support things like
								.DS_Store files on Mac or Unix hidden files, etc.
		@throws IOException	on io error
	*/
	public Manifest(File location, Storage parts, Filter filter) throws IOException {
		super();
		set("name", location.getName());
		_add(location, null, parts, filter);
	}
	/** Compares this Manifest against another Ini or Manifest.
		<p><b>TODO</b><ul>
			<li>Document this behavior better (and improve behavior)
		</ul>
		@param obj	The other item to compare against.
		@return		<code>true</code> if <code>obj</code> is an Ini or Manifest and they are equivalent.
	*/
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
	/** Is Xattr available */
	private static final boolean	_xattr= Xattr.available();
	/** Does Stat support is executable */
	private static final boolean	_executable= Stat.available(Stat.IsExecutableMask);
	/** Does Stat support is opqaque */
	private static final boolean	_opaque= Stat.available(Stat.IsOpaque);
	/** Does Stat support is append only */
	private static final boolean	_append= Stat.available(Stat.IsAppendOnly);
	/** Does Stat support is no backup */
	private static final boolean	_backup= Stat.available(Stat.NoBackup);
	/** Does Stat support is locked */
	private static final boolean	_locked= Stat.available(Stat.IsLocked);
	/** Does Stat support created */
	private static final boolean	_created= Stat.available(Stat.Created);
	/** Does Stat support is hidden */
	private static final boolean	_hidden= Stat.available(Stat.IsHidden);
	/** Does Stat support flags */
	private static final boolean	_flags= Stat.available(Stat.Flags);
	/** Does Stat support permissions */
	private static final boolean	_permissions= Stat.available(Stat.Permissions);
	/** Does Stat support is link */
	private static final boolean	_stat_link= Stat.available(Stat.IsLink);
	/** Are symlinks supported */
	private static final boolean	_link= Link.available();
	/** true string used in manifest */
	private static final String		_true= "true";
	/** false string used in manifest */
	private static final String		_false= "false";
	/** Supported hash names */
	private static final String[]	_supportedHashes= "MD5,SHA-1,SHA-256,SHA-384,SHA-512".split(","); // ,SHA-256,SHA-384,SHA-512".split(",");
	/** The date format to write to the manifest */
	private static final String		_dateFormat= "yyyy/MM/dd,hh-mm-ss";
	/** Analyzes an item on disk and adds it to the manifest.
		@param location	The file or directory to add
		@param section	The directory (relative to the base of this manifest) this item is in, or null for the Manifest root
		@param parts	The key-store for file contents.
		@param filter	Filter out unnecessary items from the Manifest
		@throws IOException	on io error
	*/
	private void _add(File location, String section, Storage parts, Filter filter) throws IOException {
		Stat	info= new Stat(location);
		boolean	isLink= _handleLinks(location, info, section);
		_handleXattr(location, section);
		_handleNodeFlags(info, section);
		if(!isLink) {
			if(info.directory()) {
				_handleDirectory(location, section, parts, filter);
			} else if(info.file()) {
				_handleFile(location, info, section, parts);
			}
		}
	}
	/** The hex digits to use when output hex data */
	private static final String	kHexDigits= "0123456789abcdef";
	/** Converts a hash array to a hex string.
		@param hash		The hash data
		@param bytes	The number of bytes in <code>hash</code> that are the hash
		@return			The hex value of the hash
	*/
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
	/** Handles adding a file to the manifest.
		@param location	The file to add
		@param info		The Stat info we've already gotten.
		@param section	The directory (relative to the base of this manifest) this file is in, or null for the Manifest root
		@param parts	The key-store for file contents.
		@throws IOException	on io error
	*/
	private void _handleFile(File location, Stat info, String section, Storage parts) throws IOException {
		MessageDigest[]	globalHashes= new MessageDigest[_supportedHashes.length];
		MessageDigest[]	chunkHashes= new MessageDigest[_supportedHashes.length];
		String[]		hashes= new String[_supportedHashes.length];
		String[]		partList= new String[_supportedHashes.length];
		FileInputStream	in= new FileInputStream(location);
		byte[]			buffer= new byte[1024 * 1024];
		int				amountRead;
		long			totalRead= 0;
		int				lastRead= 0;
		int				blocks= 0;
		long			carriageReturnCount= 0;
		long			lineFeedCount= 0;
		long			nullCount= 0;

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
			totalRead+= amountRead;
			for(int b= 0; b < amountRead; ++b) {
				if(buffer[b] == '\r') {
					++carriageReturnCount;
				} else if(buffer[b] == '\n') {
					++lineFeedCount;
				} else if(buffer[b] == '\0') {
					++nullCount;
				}
			}
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
							partList[h]= ""+amountRead+"-"+hashes[h];
						} else if(lastRead != amountRead) {
							partList[h]+= ";"+amountRead+"-"+hashes[h];
						} else {
							partList[h]+= ","+hashes[h];
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
		String	type= "";

		if( (nullCount == 0) && ( (carriageReturnCount > 0) || (lineFeedCount > 0) ) ) { // possibly text
			if( (carriageReturnCount == 0) && (lineFeedCount > 0) ) {
				type= "_unix";
			} else if( (carriageReturnCount > 0) && (lineFeedCount == 0) ) {
				type= "_mac";
			} else {
				long	dosDifferentialRatio= 100 * (carriageReturnCount - lineFeedCount) / (lineFeedCount + carriageReturnCount);

				if(Math.abs(dosDifferentialRatio) < 50) {
					type= "_dos";
				} else if(dosDifferentialRatio > 0) {
					type= "_max";
				} else {
					type= "_unix";
				}
			}
		}
		for(int h= 0; h < _supportedHashes.length; ++h) {
			int	digestSizeRequired= globalHashes[h].getDigestLength();

			if( (null == digest) || (digestSizeRequired > digest.length) ) {
				digest= new byte[digestSizeRequired];
			}
			try	{
				digestSize= globalHashes[h].digest(digest, 0, digest.length);

				set(section, _supportedHashes[h]+type, _hashToHexString(digest, digestSize));
				if(blocks > 1) {
					set(section, "parts_"+_supportedHashes[h]+type, partList[h]);
				}
			} catch(DigestException exception) {
				// can't seem to get the hash
			}
		}
		set(section, "size"+type, ""+info.size());
	}
	/** Analyzes a directory on disk and adds it to the manifest.
		@param location	The directory to add
		@param section	The directory (relative to the base of this manifest) this item is in, or null for the Manifest root
		@param parts	The key-store for file contents.
		@param filter	Filter out unnecessary items from the Manifest
		@throws IOException	on io error
	*/
	private void _handleDirectory(File location, String section, Storage parts, Filter filter) throws IOException {
		String	prefix;
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
				_add(path, sectionName+prefix+item, parts, filter);
			}
		}
	}
	/** Handles adding a Unix flag to the manifest.
		@param section		The Manifest root relative path to this item.
		@param name			The name of the flag
		@param info			The Stat info for the file or directory
		@param flag			The value of the flag in the Stat.st_flags
		@param supported	Is Stat flags supported
		@param printedValue	If <code>flag</code> is set and this is <code>true</code> then add it to the manifest.
							If <code>flag</code> is not set and this is <code>false</code> then add it to the manifest.
	*/
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
	/** Handle adding all the flags for a given file or directory to the Manifest.
		@param info		The Stat info we've already gotten
		@param section	The Manifest root relative path to this node.
	*/
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
	/** Handles adding the the xattr meta-data (for systems that support it) to the manifest.
		@param location	The path to the file or directory
		@param section	The Manifest root relative path to the file or directory
	*/
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
	/** Handles adding symlinks to the Manifest.
		@param location	The path to a potential symlink
		@param info		The Stat info for the potential symlink
		@param section	The Manifest root relative path to the potential symlink
	*/
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
	/** Test.
		@param args	Paths to files to dump manifests for
	*/
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

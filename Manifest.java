import java.io.Reader;
import java.io.File;
import java.io.IOException;
import java.security.MessageDigest; // MD5, SHA-1, SHA-256, SHA-384, SHA-512
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.io.FileInputStream;
import java.math.BigInteger;

/**
	See <a href="http://download.oracle.com/javase/1.5.0/docs/guide/security/CryptoSpec.html#AppA">MessageDigest Algorithms</a>

		<b>TODO</b><ul>
			<li>Ignore modification date in equals
			<li>Ignore xattr and link in equals
			<li>Find a better encoding for non-text xattr values in Manifest(location)
					Possibly override set(String,byte[])
			<li>Take encoding into account when comparing
			<li>Factor out info gathering logic into a function that takes base path, path, section
			<li>Manifest(location) should take a storage API to store off data as it calculates the hashes
			<li>Document
			<li>_handleFile allocates way to many buffers (digest()). create a buffer and reuse it
			<li>_handleFile should make sure chunks are the expected size of the last chunk, in case of partial reads
			<li>In _handleFile() use Storage.link() to link other hashes to the same data
			<li>Handle the case in _handleFile() where a message digest is not supported and it's the one we chose for the main key
			<li>Multithread to take account for calculating hashes vs waiting for disk
		</ul>

		FIX

[501360N-01M LabVIEW 2011 Pro.dmg]
xattr_com.apple.diskimages.fsck=%D.t!7I
xattr_com.apple.diskimages.recentcksum=i\:627945 on AF237B89-3EDA-3F38-AB23-EFE861D25504 @ 1308742439 - CRC32\:$582ECDE4
xattr_com.apple.metadata\:kMDItemWhereFroms=bplist00_dhttp\://mlvbuild001/~lvbuild/NISoftware/LabVIEW/LabVIEW%202011/501360N-01M%20LabVIEW%202011%20Pro.dmg_>http\://mlvbuild001/~lvbuild/NISoftware/LabVIEW/LabVIEW%202011/\br\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0
xattr_com.apple.quarantine=0000\;4e0df595\;Safari\;A67883BC-E2C4-4B1C-A4D2-D7E324D6AEE4|com.apple.Safari
modified=2011/06/22,06-33-59
size=612557076
MD5=ba7d4c44b0898654260befed95903a3e
parts_MD5=\;188692\:48db02de1e02e5003862397eeebe492b
SHA-1=0246be513c6b00b77994e3baddb7200a4ba1c4bf
parts_SHA-1=\;188692\:76305a070ae0242581d6f343cf32346f0e1f78d2

[]
size=3996825506

*/
public class Manifest extends Ini {
	public Manifest(Reader in) throws IOException {
		super(in);
	}
	public Manifest(File location, Storage parts) throws IOException {
		super();
		set("name", location.getName());

		long	size= _add(location, null, parts);

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
	private static final boolean	_link= Link.available();
	private static final String[]	_supportedHashes= "MD5,SHA-1".split(","); // ,SHA-256,SHA-384,SHA-512".split(",");
	private static final String		_dateFormat= "yyyy/MM/dd,hh-mm-ss";
	private long _add(File location, String section, Storage parts) throws IOException {
		long	size= 0;
		boolean	isLink= _handleLinks(location, section);

		_handleXattr(location, section);
		_handleNodeFlags(location, section);
		if(!isLink) {
			if(location.isDirectory()) {
				size= _handleDirectory(location, section, parts);
			} else if(location.isFile()) {
				_handleFile(location, section, parts);
				size= location.length();
			}
		}
		System.err.println("_add("+location+") size="+size);
		return size;
	}
	private void _handleFile(File location, String section, Storage parts) throws IOException {
		set(section, "size", ""+location.length());

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
		do	{
			amountRead= in.read(buffer);
			if(amountRead > 0) {
				String	key;
				++blocks;

				for(int h= 0; h < _supportedHashes.length; ++h) {
					if(null != globalHashes[h]) {
						globalHashes[h].update(buffer, 0, amountRead);
					}
					if(null != chunkHashes[h]) {
						byte[]	digest;

						chunkHashes[h].reset();
						chunkHashes[h].update(buffer, 0, amountRead);
						digest= chunkHashes[h].digest();
						hashes[h]= String.format("%0"+(digest.length*2)+"x", new BigInteger(1, digest));
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
			byte[]	digest= globalHashes[h].digest();

			set(section, _supportedHashes[h], String.format("%0"+(digest.length*2)+"x", new BigInteger(1, digest)));
			if(blocks > 1) {
				set(section, "parts_"+_supportedHashes[h], partList[h]);
			}
		}
	}
	private long _handleDirectory(File location, String section, Storage parts) throws IOException {
		String	prefix;
		long	size= 0;

		if(null == section) {
			section= "";
			prefix= "";
		} else {
			prefix= "/";
		}
		for(String item : location.list()) {
			size+= _add(new File(location,item), section+prefix+item, parts);
		}
		set(section, "size",""+size);
		return size;
	}
	private void _handleNodeFlags(File location, String section) throws IOException {
		if(!location.canWrite()) {
			set(section, "read-only", "true");
		}
		if(location.isHidden()) {
			set(section, "hidden", "true");
		}
		set(section, "modified", (new SimpleDateFormat(_dateFormat)).format(new Date(location.lastModified())));
	}
	private void _handleXattr(File location, String section) {
		if(_xattr) {
			try	{
				Xattr	info= new Xattr(location);

				for(String key : info.keys()) {
					set(section, "xattr_"+key, new String(info.value(key)));
				}
			} catch(IOException io) {
			} catch(InterruptedException interrupted) {
			}
		}
	}
	private boolean _handleLinks(File location, String section) {
		boolean	isLink= false;

		if(_link) {
			try	{
				Link	info= new Link(location);

				isLink= info.isLink();
				if(isLink) {
					set(section, "link", info.target());
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
				(new Manifest(new File(arg),null)).write(out);
			}
		} catch(IOException exception) {
			exception.printStackTrace();
		}
	}
}

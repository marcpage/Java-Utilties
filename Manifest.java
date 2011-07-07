import java.io.Reader;
import java.io.File;
import java.io.IOException;
import java.security.MessageDigest; // MD5, SHA-1, SHA-256, SHA-512

/**
		<b>TODO</b><ul>
			<li>Ignore modification date in equals
			<li>Ignore xattr and link in equals
			<li>Find a better encoding for non-text xattr values in Manifest(location)
			<li>Factor out info gathering logic into a function that takes base path, path, section
			<li>Manifest(location) should take a storage API to store off data as it calculates the hashes
		</ul>
*/
public class Manifest extends Ini {
	public Manifest(Reader in) throws IOException {
		super(in);
	}
	/** 
	*/
	public Manifest(File location) throws IOException {
		super();
		boolean	isLink= false;
		
		if(_link) {
			try	{
				Link	info(location);
				
				isLink= info.isLink();
				if(isLink) {
					set("link", info.target());
				}
			} catch(IOException io) {
			} catch(InterruptedException interrupted) {
			}
		}
		if(_xattr) {
			try	{
				Xattr	info(location);
				
				for(String key : test.keys()) {
					set("xattr_"+key, info.value(key));
				}
			} catch(IOException) {
			} catch(InterruptedException interrupted) {
			}
		}
		if(!location.canWrite()) {
			set("read-only", "true");
		}
		set("name", location.getName());
		if(location.isHiddent()) {
			set("hidden", "true");
		}
		// long lastModified()
		if(!isLink) {
			if(location.isDirectory()) {
				// String[] list()
			} else if(location.isFile()) {
				// long length()
				// md5
				// md5_contents
				// sha1
				// sha1_contents
			}
		}
	}
	/** 
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
	public Manifest write(Writer out) throws IOException {
	}
	private static final boolean	_xattr= Xattr.available();
	private static final boolean	_link= Link.available();
}

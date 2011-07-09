import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;

/** Manages Unix symlinks.
	<b>TODO</b>
	<ul>
		<li>
	</ul>
*/
public class Link extends SystemCall {
	/** Determine if this class is usable on this system
		@return		true if you can use this class to read links and create them.
	*/
	public static boolean available() {
		return _available;
	}
	/** Reads the link info from the file.
		@param of						The file to read the link info from
		@throws IOException				On errors reading from command line output
		@throws InterruptedException	If the command was interrupted
	*/
	public Link(File of) throws IOException, InterruptedException {
		_target= _execute(_command.getAbsolutePath(), of.getAbsolutePath()).trim();
		if(_target.length() == 0) {
			_target= null;
		} else {
			_linkFile= new File(of.getParent(), _target);
		}
	}
	/** Creates a link (using a relative path).
		@param target	The file with the contents
		@param link		The file that will point to <code>target</code>
		@throws IOException				On errors reading from command line output
		@throws InterruptedException	If the command was interrupted
	*/
	public Link(File target, File link) throws IOException, InterruptedException {
		_target= _createlink(target, link, false);
		_linkFile= target;
	}
	/** Creates a link.
		@param target		The file with the contents
		@param link			The file that will point to <code>target</code>
		@param absoluteLink	If true then the absolute path will be used for the link contents, if false, relative
		@throws IOException				On errors reading from command line output
		@throws InterruptedException	If the command was interrupted
	*/
	public Link(File target, File link, boolean absoluteLink) throws IOException, InterruptedException {
		_target= _createlink(target, link, absoluteLink);
		_linkFile= target;
	}
	/** Determine if this file really is a link.
		@return	true if this represents a symlink
	*/
	boolean isLink() {
		return null != _target;
	}
	/** The contents of the link.
		@return	The contents of the symlink, a path to the real contents
	*/
	String target() {
		return _target;
	}
	/** The contents file path.
		@return	The path to the file that contains the real contents.
	*/
	File targetFile() {
		return _linkFile;
	}
	/** The link contents. */
	private String					_target;
	/** The path to the real file */
	private File					_linkFile;
	/** The command to read the links */
	private static final File		_command= new File("/usr/bin/readlink");
	/** The command to create links */
	private static final File		_createCommand= new File("/bin/ln");
	/** If true, this class is usefull */
	private static final boolean	_available= _command.isFile() && _createCommand.isFile();
	/** Converts a path into a list of directories/file names.
		@param f	The path to break down
		@return		The names of the directories/file represented by <code>f</code>
	*/
	private static ArrayList<String> _parts(File f) {
		ArrayList<String>	parts= new ArrayList<String>();

		while(null != f) {
			parts.add(0, f.getName());
			f= f.getParentFile();
		}
		return parts;
	}
	/** Gets a relative path from one file to another.
		for example:
			from=/Users/me/test.txt
			to=/Users/you/temp.txt
			return=../temp.txt
		@param from	The starting point
		@param to	The target
		@return		A relative path from <code>from</code> to <code>to</code>
	*/
	public static String _relative(File from, File to) {
		ArrayList<String>	fromParts= _parts(from.getParentFile());
		ArrayList<String>	toParts= _parts(to.getParentFile());
		int					max= fromParts.size() < toParts.size() ? fromParts.size() : toParts.size();
		int					mismatch= 0;
		String				result= "";

		while( (mismatch < max) && (fromParts.get(mismatch).equals(toParts.get(mismatch))) ) {
			++mismatch;
		}
		for(int updirs= mismatch; updirs < fromParts.size(); ++updirs) {
			result+= "../";
		}
		for(int directory= mismatch; directory < toParts.size(); ++directory) {
			result+= toParts.get(directory)+"/";
		}
		return result+to.getName();
	}
	/** Creates a link.
		@param target		The file to point to
		@param link			The link file
		@param absoluteLink	If true, the contents of link will be absolute, otherwise relative
		@return				The contents of the newly created link file
		@throws IOException				On errors reading from command line output
		@throws InterruptedException	If the command was interrupted
	*/
	private static String _createlink(File target, File link, boolean absoluteLink) throws IOException, InterruptedException {
		if(!_available) {
			throw new IOException("Linking not available");
		}
		String			path= absoluteLink ? target.getAbsolutePath() : _relative(link, target);
		
		_execute(_createCommand.getAbsolutePath(), "-s", path, link.getAbsolutePath());
		return path;
	}
	/** Test.
		@param args	Each file will be treated as a symlink and the ouput for each will be displayed.
	*/
	public static void main(String... args) {
		for(String arg : args) {
			try	{
				if( (new Link(new File(arg))).isLink() ) {
					System.out.println(arg+": '"+(new Link(new File(arg))).target()+"'");
					System.out.println("\t"+(new Link(new File(arg))).targetFile());
				}
			} catch(IOException exception) {
				System.err.println(arg+": "+exception);
				exception.printStackTrace();
			} catch(InterruptedException exception2) {
				System.err.println(arg+": "+exception2);
			}
		}
	}
}

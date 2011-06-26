import java.io.File;
import java.io.InputStream;
import java.io.IOException;

/** 
	<b>TODO</b>
	<ul>
		<li>Document
		<li>Creation of symlinks
	</ul>
*/
public class Link {
	public static boolean available() {
		return _available;
	}
	public Link(File of) throws IOException, InterruptedException {
		_target= _readlink(of);
		if(_target.length() == 0) {
			_target= null;
		} else {
			_linkFile= new File(of.getParent(), _target);
		}
	}
	public Link(File target, File link) throws IOException {
		throw new IOException("Implement");
	}
	public Link(File target, File link, boolean absoluteLink) throws IOException {
		throw new IOException("Implement");
	}
	boolean isLink() {
		return null != _target;
	}
	String target() {
		return _target;
	}
	File targetFile() {
		return _linkFile;
	}
	private String					_target;
	private File					_linkFile;
	private static final File		_command= new File("/usr/bin/readlink");
	private static final boolean	_available= _command.isFile();
	private static String _readlink(File of) throws IOException, InterruptedException {
		String	results= "";
		if(_available) {
			ProcessBuilder	pb;
			Process			proc;
			byte[]			data= new byte[512];
			InputStream		in;

			pb= new ProcessBuilder(_command.getAbsolutePath(), of.getAbsolutePath());
			pb.redirectErrorStream(true);
			proc= pb.start();
			in= proc.getInputStream();
			while(true) {
				int	amountRead= in.read(data);

				if(amountRead < 0) {
					break;
				}
				results+= new String(data, 0, amountRead);
			}
			in.close();
			int	resultCode= proc.waitFor();
			if(0 != resultCode) {
				throw new IOException("readlink("+resultCode+"): "+results);
			}
		}
		return results.trim();
	}
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

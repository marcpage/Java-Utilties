import java.util.Arrays;
import java.io.InputStream;
import java.io.IOException;

/** Executes a system call.
	Intended to be subclassed.
	<p><b>TODO</b>
	<ul>
		<li>Test
	</ul>
*/
public class SystemCall {
	/** Does nothing.
	*/
	public SystemCall() {
	}
	/** Executes a command, and gets merged stderr and stdout.
		@param commandAndArgs	The command, followed by the arguments
		@return					The merged stdout and stderr
		@throws IOException				If there is a problem reading from the merged stderr stdout stream
		@throws InterruptedException	If the call is interrupted
		@return				The merged stdout and stderr
	*/
	protected static String _execute(String... commandAndArgs) throws IOException, InterruptedException {
		ProcessBuilder	pb= new ProcessBuilder(Arrays.asList(commandAndArgs));
		Process			proc;
		String			results= "";
		byte[]			data= new byte[4096];
		InputStream		in;

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
			throw new IOException(commandAndArgs[0]+" -> "+resultCode+": "+results);
		}
		return results;
	}
}

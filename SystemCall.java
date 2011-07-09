import java.util.Arrays;  
import java.io.InputStream;
import java.io.IOException;

/** Executes a system call.
	<b>TODO</b>
	<ul>
		<li>Document and correct _execute documentation
		<li>Test
	</ul>
*/
class SystemCall {
	public SystemCall() {
	}
	/** Creates a link.
		@param target		The file to point to
		@param link			The link file
		@param absoluteLink	If true, the contents of link will be absolute, otherwise relative
		@return				The contents of the newly created link file
		@throws IOException				On errors reading from command line output
		@throws InterruptedException	If the command was interrupted
	*/
	protected String _execute(String... commandAndArgs) throws IOException, InterruptedException {
		ProcessBuilder	pb= new ProcessBuilder(Arrays.asList(commandAndArgs));
		String			results= "";
		
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

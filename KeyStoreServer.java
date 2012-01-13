import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class KeyStoreServer implements HTTPServer.Handler {
	public boolean handle(InputStream in, OutputStream out, HTTPServer.KeyValuesMap headers, HTTPServer.KeyValuesMap query, HTTPServer.CookieJar cookies) throws IOException {
		String		path= headers.firstValue("PATH", "/");
		String[]	parts= path.split("/", 3);

		if( (null != parts) && (parts.length == 3) && (parts[1].equalsIgnoreCase("key")) ) {
			String	key=parts[2];

			// get or put a key
			HTTPServer.writeResponse(out, "1.1", 200, "OK");
			HTTPServer.writeHeader(out, "Content-Type", "text/html");
			HTTPServer.finishHeaders(out);
			HTTPServer.write(out, "<html><head><title>Test</title></head><body><h1><center>Test</center></h1>\r\n");
			HTTPServer.write(out, "key=<b>"+key+"</b><br/>");
			HTTPServer.write(out, "METHOD=<b>"+headers.getProperty("METHOD", "[UNKNOWN]")+"</b><br/>");
			HTTPServer.write(out, "</body></html>\r\n");
			HTTPServer.CloseConnection(headers);
		} else {
			HTTPServer.writeResponse(out, "1.1", 200, "OK");
			HTTPServer.writeHeader(out, "Content-Type", "text/html");
			HTTPServer.finishHeaders(out);
			HTTPServer.write(out, "<html><head><title>Test</title></head><body><h1><center>Test</center></h1>\r\n");
			HTTPServer.write(out, "parts="+parts.length+" 1st="+parts[1]+"<br/>");
			HTTPServer.write(out, "METHOD="+headers.getProperty("METHOD", "[UNKNOWN]"));
			HTTPServer.write(out, "</body></html>\r\n");
			HTTPServer.CloseConnection(headers);
		}
		return true;
	}
	/** Handles logging exceptions
	*/
	public void log(Exception exception) {
		exception.printStackTrace();
		log(0, exception.toString());
	}
	/** Logs messages.
		@param level	0 = vital to display 100 = trivial to display
		@param message	The message to handle
	*/
	public void log(int level, String message) {
		System.err.println("LOG "+level+": "+message);
	}
	/** Test.
		@param args	arg[0] is the port to listen on.
	*/
	public static void main(String... args) {
		try	{
			new SocketServer(Integer.parseInt(args[0]), new HTTPServer(new KeyStoreServer()));
		} catch(IOException exception) {
			System.err.println(exception);
		}
	}
}

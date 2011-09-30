import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class Webdav implements HTTPServer.Handler {
	public Webdav() {
	}
	public void log(Exception exception) {
		exception.printStackTrace();
		log(0, exception.toString());
	}
	public void log(int level, String message) {
		System.err.println("LOG "+level+": "+message);
	}
	public boolean handle(InputStream in, OutputStream out, HTTPServer.KeyValuesMap headers, HTTPServer.KeyValuesMap query, HTTPServer.CookieJar cookies) throws IOException {
		System.out.println("Headers");
		for(String key : headers.keySet()) {
			System.out.println("\t"+key);
			for(String value : headers.get(key)) {
				System.out.println("\t\t"+value);
			}
		}
		System.out.println("Query");
		for(String key : query.keySet()) {
			System.out.println("\t"+key);
			for(String value : query.get(key)) {
				System.out.println("\t\t"+value);
			}
		}
		System.out.println("Cookies");
		for(String key : cookies.keySet()) {
			System.out.println("\t"+key);
			for(HTTPServer.Cookie value : cookies.get(key)) {
				System.out.println("\t\t"+value.toString(false));
			}
		}
		/*
		System.out.println("Body");
		byte[]	buffer= new byte[4096];
		int		read= in.read(buffer);
		while(read >= 0) {
			System.out.write(buffer, 0, read);
			read= in.read(buffer);
		}
		*/
		return true;
	}
	public void handle(SocketServer server, Socket connection) throws IOException {
		System.out.println("Handling new connection on port "+server.port()+" from "+connection);
		InputStream		in= connection.getInputStream();
		OutputStream	out= connection.getOutputStream();
		byte[]			buffer= new byte[4096];
		int				read= in.read(buffer);

		while(read >= 0) {
			System.out.write(buffer, 0, read);
			read= in.read(buffer);
		}
	}
	public static void main(String... args) {
		try	{
			new SocketServer(Integer.parseInt(args[0]), new HTTPServer(new Webdav()));
		} catch(IOException exception) {
			System.err.println(exception);
		}
	}
}

/*

	Safari connection
GET / HTTP/1.1
Host: localhost:8086
User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_7_1) AppleWebKit/534.48.3 (KHTML, like Gecko) Version/5.1 Safari/534.48.3
Accept: text/html,application/xhtml+xml,application/xml;q=0.9,* /*;q=0.8
Accept-Language: en-us
Accept-Encoding: gzip, deflate
Connection: keep-alive

	Finder connection

OPTIONS / HTTP/1.1
Host: localhost:8086
User-Agent: WebDAVLib/1.3
Content-Length: 0
Accept: * /*
Connection: close

	Finder Connection

Headers
	MINOR-VERSION=1
	VERSION=1.1
	Host=localhost:8086
	HTTP-VERSION=HTTP/1.1
	Content-Length=0
	REQUEST-URI=/
	PATH=/
	MAJOR-VERSION=1
	User-Agent=WebDAVFS/1.9.0 (01908000) Darwin/11.1.0 (i386)
	METHOD=OPTIONS
	Connection=keep-alive
	Accept=* /*

Query
Cookies

Response
HTTP/1.1 401 Unauthorized
Content-Length: 0
Www-Authenticate: Basic realm="defaultRealm@host.com"
Date: Fri, 30 Sep 2011 14:46:06 GMT
Accept-Ranges: bytes


*/

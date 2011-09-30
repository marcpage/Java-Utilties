import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import util.Base64;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
	<p>References:<ul>
<li><a href="http://www.webdav.org/specs/rfc4918.html">RFC 4918</a>
<li><a href="http://en.wikipedia.org/wiki/Basic_access_authentication">Wikipedia Basic Access Authentication</a>
<li><a href="http://www.webdav.org/specs/rfc2518.html">RFC 2518</a> (Obsoleted)
	</ul>

*/
public class Webdav implements SocketServer.Handler {
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
	private Pattern	_authenticationPattern= Pattern.compile("Authorization:\\s+Basic\\s+(\\S+)\\s+");
	public void handle(SocketServer server, Socket connection) throws IOException {
		System.out.println("Handling new connection on port "+server.port()+" from "+connection);
		InputStream		in= connection.getInputStream();
		OutputStream	out= connection.getOutputStream();
		byte[]			buffer= new byte[4096];
		int				read= in.read(buffer);
		while(read >= 0) {
			String			text= new String(buffer, 0, read);
			Matcher			isAuthenticating= _authenticationPattern.matcher(text);

			System.out.println(text);
			if(isAuthenticating.find()) {
				String	authentication= new String(Base64.decode(isAuthenticating.group(1)));

				System.out.println("Authenticating: "+authentication);
				out.write("HTTP/1.1 200 OK\r\n".getBytes());
				out.write("Content-Length: 0\r\n".getBytes());
				out.write("Date: Fri, 30 Sep 2011 15:31:45 GMT\r\n".getBytes());
				out.write("Accept-Ranges: bytes\r\n".getBytes());
				out.write("DAV: 1,2, addressbook\r\n".getBytes());
				out.write("Allow: PROPFIND, DELETE, MKCOL, PUT, MOVE, COPY, PROPPATCH, LOCK, UNLOCK, REPORT\r\n\r\n".getBytes());
			} else {
				System.out.println("No Authentication");
				out.write("HTTP/1.1 401 Unauthorized\r\n".getBytes());
				out.write("Content-Length: 0\r\n".getBytes());
				out.write("Www-Authenticate: Basic realm=\"defaultRealm@host.com\"\r\n".getBytes());
				out.write("Date: Fri, 30 Sep 2011 14:46:06 GMT\r\n".getBytes());
				out.write("Accept-Ranges: bytes\r\n\r\n".getBytes());
			}
			System.out.write(buffer, 0, read);
			read= in.read(buffer);
		}
	}
	public static void main(String... args) {
		try	{
			//new SocketServer(Integer.parseInt(args[0]), new HTTPServer(new Webdav()));
			new SocketServer(Integer.parseInt(args[0]), new Webdav());
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
*/

/* Session

Request
-------
OPTIONS / HTTP/1.1
Host: localhost:8086
User-Agent: WebDAVLib/1.3
Content-Length: 0
Accept: * /*
Connection: close

Response
--------
HTTP/1.1 401 Unauthorized
Content-Length: 0
Www-Authenticate: Basic realm="defaultRealm@host.com"
Date: Fri, 30 Sep 2011 14:46:06 GMT
Accept-Ranges: bytes

Request - username: marcp password: [blank]
-------
OPTIONS / HTTP/1.1
Host: localhost:8043
User-Agent: WebDAVLib/1.3
Content-Length: 0
Accept: * /*
Authorization: Basic bWFyY3A6
Connection: close

Response
--------
HTTP/1.1 200 OK
Content-Length: 0
Date: Fri, 30 Sep 2011 15:31:45 GMT
Accept-Ranges: bytes
DAV: 1,2, addressbook
Allow: PROPFIND, DELETE, MKCOL, PUT, MOVE, COPY, PROPPATCH, LOCK, UNLOCK, REPORT

Request
-------
PROPFIND / HTTP/1.1
Host: localhost:8051
User-Agent: WebDAVFS/1.9.0 (01908000) Darwin/11.1.0 (i386)
Content-Length: 179
Accept: * /*
Content-Type: text/xml
Depth: 0
Authorization: Basic bWFyY3A6
Connection: keep-alive

<?xml version="1.0" encoding="utf-8"?>
<D:propfind xmlns:D="DAV:">
<D:prop>
<D:getlastmodified/>
<D:getcontentlength/>
<D:creationdate/>
<D:resourcetype/>
</D:prop>
</D:propfind>

Response
--------
HTTP/1.1 207 Multi-Status
Content-Length: 13552
Date: Fri, 30 Sep 2011 15:50:15 GMT
Accept-Ranges: bytes
Content-Type: text/xml; charset="utf-8"

<?xml version="1.0" encoding="utf-8"?>
<D:multistatus xmlns:D="DAV:">
	<D:response>
		<D:href>/bash_history</D:href>
		<D:propstat>
			<D:status>HTTP/1.1 200 OK</D:status>
			<D:prop>
				<D:executable>F</D:executable>
				<D:getcontentlength>16901</D:getcontentlength>
				<D:getlastmodified>2011-09-30 13:16:47 +0000</D:getlastmodified>
				<D:creationdate>2011-09-30 13:16:47 +0000</D:creationdate>
				<D:modificationdate>2011-09-30 13:16:47 +0000</D:modificationdate>
				<D:resourcetype/>
			</D:prop>
		</D:propstat>
	</D:response>
	<D:response>
		<D:href>/.MacOSX/</D:href>
		<D:propstat>
			<D:status>HTTP/1.1 200 OK</D:status>
			<D:prop>
				<D:getlastmodified>2011-07-08 14:08:57 +0000</D:getlastmodified>
				<D:ishidden>0</D:ishidden>
				<D:getcontenttype>text/plain</D:getcontenttype>
				<D:getcontentlength>0</D:getcontentlength>
				<D:iscollection>1</D:iscollection>
				<D:modificationdate>2011-07-08 14:08:57 +0000</D:modificationdate>
				<D:resourcetype>
					<D:collection/>
				</D:resourcetype>
			</D:prop>
		</D:propstat>
	</D:response>
</D:response></D:multistatus>


*/

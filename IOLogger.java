import java.io.OutputStream;
import java.io.InputStream;
import java.io.IOException;

public class IOLogger {
	public IOLogger(InputStream in, OutputStream out, String identifier, OutputStream report, boolean colatedSinglePoint) {
		_in= in;
		_out= out;
		_id= identifier;
		_reportOut= null;
		_reportIn= null;
		_report= report;
		if(colatedSinglePoint) {
			_singlePoints= "";
		} else {
			_singlePoints= null;
		}
		_direction= "";
	}
	public InputStream getInputStream() {
		if(null == _in) {
			return null;
		}
		if(null == _reportIn) {
			_reportIn= new _In();
		}
		return _reportIn;
	}
	public OutputStream getOutputStream() {
		if(null == _out) {
			return null;
		}
		if(null == _reportOut) {
			_reportOut= new _Out();
		}
		return _reportOut;
	}
	private class _Out extends OutputStream {
		public void write(int b) throws IOException {
			//_report("OUT:"+(char)b);
			_report("OUT",(char)b);
			_out.write(b);
		}
		public void write(byte[] b) throws IOException {
			_swapDirection();
			_report("OUT:[["+(new String(b))+"]]");
			_out.write(b);
		}
		public void write(byte[] b, int off, int len) throws IOException {
			_swapDirection();
			_report("OUT:[["+(new String(b, off, len))+"]]");
			_out.write(b, off, len);
		}
		public void close() throws IOException {
			_swapDirection();
			_report("OUT:close()");
			_out.close();
		}
	}
	private class _In extends InputStream {
		public int read() throws IOException {
			int	result= _in.read();

			if(result < 0) {
				_report("IN:End of Stream");
			} else {
				//_report("IN:"+(char)result);
				_report("IN:",(char)result);
			}
			return result;
		}
		public int read(byte[] b) throws IOException {
			int	result= _in.read(b);

			_swapDirection();
			if(result < 0) {
				_report("IN:End of Stream");
			} else {
				_report("IN:[["+(new String(b, 0, result))+"]]");
			}
			return result;
		}
		public int read(byte[] b, int off, int len) throws IOException {
			int	result= _in.read(b, off, len);

			_swapDirection();
			if(result < 0) {
				_report("IN:End of Stream");
			} else {
				_report("IN:[["+(new String(b, off, result))+"]]");
			}
			return result;
		}
		public long skip(long n) throws IOException {
			long	skipped= _in.skip(n);

			_swapDirection();
			_report("IN:skip("+n+")="+skipped);
			return skipped;
		}
		public void close() throws IOException {
			_swapDirection();
			_report("IN:close()");
			_in.close();
		}
	}
	private _Out			_reportOut;
	private _In				_reportIn;
	private OutputStream	_out;
	private InputStream		_in;
	private String			_id;
	private OutputStream	_report;
	private String			_singlePoints;
	private String			_direction;

	synchronized void _swapDirection() throws IOException {
		if(_singlePoints.length() > 0) {
			_report(_direction+":[]:[["+_singlePoints+"]]");
			_singlePoints= "";
			_direction= "";
		}
	}
	synchronized void _report(String direction, char b) throws IOException {
		if(null == _singlePoints) {
			_report(direction+":[["+b+"]]");
		} else {
			if(!direction.equals(_direction)) {
				_swapDirection();
			}
			_singlePoints+= b;
			_direction= direction;
		}
	}
	synchronized void _report(String message) throws IOException {
		if(null != _id) {
			message= _id+":"+message;
		}
		message+= "\n";
		_report.write(message.getBytes());
	}

}

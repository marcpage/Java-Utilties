import java.io.OutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

/** Takes a collection of OutputStreams and makes them look like one.
	All data is mirrored across the given OutputStreams.
*/
public class MirrorOutputStream extends OutputStream {
	/** Creates the output stream that writes to nowhere.
	*/
	public MirrorOutputStream() {
		_out= new ArrayList<OutputStream>();
	}
	/** Creates the output stream that writes to another output stream.
		@param out	The stream to add
	*/
	public MirrorOutputStream(OutputStream out) {
		_out= new ArrayList<OutputStream>(1);
		_out.add(out);
	}
	/** Creates the output stream that writes to several output streams.
		@param out	The streams to add
	*/
	public MirrorOutputStream(OutputStream... out) {
		_out= new ArrayList<OutputStream>(Arrays.asList(out));
	}
	/** Creates the output stream that writes to several output streams.
		@param out	The streams to add
	*/
	public MirrorOutputStream(Collection<OutputStream> out) {
		_out= new ArrayList<OutputStream>(out);
	}
	/** Adds the given OutputStream to the list.
		@param out	The stream to add
	*/
	public void add(OutputStream out) {
		synchronized(_out) {
			if(!_out.contains(out)) {
				_out.add(out);
			}
		}
	}
	/** Adds the given OutputStreams to the list.
		@param out	The streams to add
	*/
	public void add(OutputStream... out) {
		synchronized(_out) {
			_out.addAll(Arrays.asList(out));
		}
	}
	/** Adds the given OutputStreams to the list.
		@param out	The streams to add
	*/
	public void add(Collection<OutputStream> out) {
		synchronized(_out) {
			_out.addAll(out);
		}
	}
	/** Removes the given OutputStream
	*/
	public boolean remove(OutputStream out) {
		synchronized(_out) {
			return _out.remove(out);
		}
	}
	/** Closes the output streams.
		Then removes all output streams.
		@throws IOException	If an exception is thrown on any stream, it is removed from the list.
							This method will never throw an exception.
	*/
	public void close() throws IOException {
		ArrayList<OutputStream>	failed= null;

		synchronized(_out) {
			for(OutputStream out : _out) {
				try {
					out.close();
				} catch(IOException exception) {
					if(null == failed) {
						failed= new ArrayList<OutputStream>();
					}
					failed.add(out);
				}
			}
			if(null != failed) {
				for(OutputStream failedStream : failed) {
					_out.remove(failedStream);
				}
			}
			_out.clear();
		}
	}
	/** Flushes all output streams.
		@throws IOException	If an exception is thrown on any stream, it is removed from the list.
							This method will never throw an exception.
	*/
	public void flush() throws IOException {
		ArrayList<OutputStream>	failed= null;

		synchronized(_out) {
			for(OutputStream out : _out) {
				try {
					out.flush();
				} catch(IOException exception) {
					if(null == failed) {
						failed= new ArrayList<OutputStream>();
					}
					failed.add(out);
				}
			}
			if(null != failed) {
				for(OutputStream failedStream : failed) {
					_out.remove(failedStream);
				}
			}
		}
	}
	/** Write bytes to all the OutputStreams.
		@param b	The buffer of data
		@throws IOException	If an exception is thrown on any stream, it is removed from the list.
							This method will never throw an exception.
	*/
	public void write(byte[] b) throws IOException {
		ArrayList<OutputStream>	failed= null;

		synchronized(_out) {
			for(OutputStream out : _out) {
				try {
					out.write(b);
				} catch(IOException exception) {
					if(null == failed) {
						failed= new ArrayList<OutputStream>();
					}
					failed.add(out);
				}
			}
			if(null != failed) {
				for(OutputStream failedStream : failed) {
					_out.remove(failedStream);
				}
			}
		}
	}
	/** Write bytes to all the OutputStreams.
		@param b	The buffer of data
		@param off	The offset in the buffer
		@param len	The number of bytes to store off
		@throws IOException	If an exception is thrown on any stream, it is removed from the list.
							This method will never throw an exception.
	*/
	public void write(byte[] b, int off, int len) throws IOException {
		ArrayList<OutputStream>	failed= null;

		synchronized(_out) {
			for(OutputStream out : _out) {
				try {
					out.write(b, off, len);
				} catch(IOException exception) {
					if(null == failed) {
						failed= new ArrayList<OutputStream>();
					}
					failed.add(out);
				}
			}
			if(null != failed) {
				for(OutputStream failedStream : failed) {
					_out.remove(failedStream);
				}
			}
		}
	}
	/** Writes a single byte to all the OutputStreams.
		@param b			The byte to write. If b is more than a byte, it will be cast to a byte.
		@throws IOException	If an exception is thrown on any stream, it is removed from the list.
							This method will never throw an exception.
	*/
	public void write(int b) throws IOException {
		ArrayList<OutputStream>	failed= null;

		synchronized(_out) {
			for(OutputStream out : _out) {
				try {
					out.write(b);
				} catch(IOException exception) {
					if(null == failed) {
						failed= new ArrayList<OutputStream>();
					}
					failed.add(out);
				}
			}
			if(null != failed) {
				for(OutputStream failedStream : failed) {
					_out.remove(failedStream);
				}
			}
		}
	}
	/** The list of output streams. */
	private ArrayList<OutputStream>	_out;
	/** Test.
		After 500 milliseconds of inactivity, it will finish up.
		Puts stdin in both stderr.txt (but not the 1st second of data) and stdout.txt.
	*/
	public static void main(String... args) {
		try	{
			java.io.File	tempDir= new java.io.File(System.getProperty("java.io.tmpdir"));
			java.io.File	file1= new java.io.File(tempDir, "output1.txt");
			java.io.File	file2= new java.io.File(tempDir, "output2.txt");
			java.io.File	file3= new java.io.File(tempDir, "output3.txt");
			OutputStream	stdout= new java.io.FileOutputStream("stdout.txt");
			OutputStream	stderr= new java.io.FileOutputStream("stderr.txt");
			MirrorOutputStream	out= new MirrorOutputStream(new java.io.FileOutputStream(file1));
			Pipe			pipe= new Pipe(System.in, out, 500 /* milliseconds timeout */, false, true);

			out.add(new java.io.FileOutputStream(file2));
			try {
				Thread.sleep(1000);
			} catch(InterruptedException e) {
			}
			out.add(new java.io.FileOutputStream(file3));
			while(pipe.running()) {
				try {
					Thread.sleep(1000);
				} catch(InterruptedException e) {
				}
			}
			System.out.println("FILES: "+file1+", "+file2+", "+file3);
		} catch(IOException exception) {
			exception.printStackTrace();
		}
	}
}

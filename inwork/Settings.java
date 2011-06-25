import java.util.ArrayList;
import java.util.Collections;
import java.util.Properties;
import java.util.Arrays;
import java.util.Map;
import java.io.File;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class Settings {
	public Settings(String filename) {
		Properties			systemProperties= System.getProperties();

		_properties= new Properties();
		_filename= filename;
		_relativeElements= new ArrayList<String>();
		_basePaths= new ArrayList<File>();
		_jars= new ArrayList<ClassLoader>();
		_jars.add(getClass().getClassLoader());
		for(String key : _systemProperties) {
			String	value= systemProperties.getProperty(key, (String)null);

			if(null != value) {
				_relativeElements.add(value);
			}
		}
		//_generateRelativePaths();
		for(String key : _systemPaths) {
			String	value= systemProperties.getProperty(key, (String)null);

			if(null != value) {
				_basePaths.add(new File(value));
			}
		}
	}
	public void addOption(String option) {
		for(String o : _relativeElements) {
			if(option.equalsIgnoreCase(o)) {
				return;
			}
		}
		_relativeElements.add(0, option);
		//_generateRelativePaths();
	}
	public void addClassLoader(ClassLoader cl) {
		for(ClassLoader c : _jars) {
			if(cl.equals(c)) {
				return;
			}
		}
		_jars.add(0, cl);
	}
	public void addSearchPath(File path) {
		for(File b : _basePaths) {
			if(path.equals(b)) {
				return;
			}
		}
		_basePaths.add(0, path);
	}
	public String get(String key) {
		return get(key, (String)null);
	}
	/** Gets a property.
		If property has not been loaded yet, all files are searched until the property is found.
	*/
	public String get(String key, String defaultValue) {
		String		result= _properties.getProperty(key, (String)null);
		Properties	working= new Properties();

		if(null == result) {
			ArrayList<InputStream>	streams= null; //_streams();

			for(InputStream stream : streams) {
				try	{
					working.load(stream);
					for(Object workingKey : Collections.list(working.propertyNames())) {
						if(null == _properties.getProperty((String)workingKey, (String)null)) {
							_properties.setProperty((String)workingKey, working.getProperty((String)workingKey, (String)null));
						}
					}
					working.clear();
					result= _properties.getProperty(key, (String)null);
					if(null != result) {
						break; // property found, bail
					}
				} catch(IOException exception) {
					// can't read from the file, don't get settings from it
				}
			}
		}
		if(null == result) {
			return defaultValue;
		}
		return result;
	}
	public void refresh() {
		_properties.clear();
	}

	private static String[]			_systemPaths= "user.dir,user.home".split(",");
	private static String[]			_systemProperties
					= "user.country,user.language,os.arch,os.name,os.version,user.name".split(",");
	private Properties				_properties;
	private String					_filename;
	private ArrayList<String>		_relativeElements;
	private ArrayList<String[]>		_relativePaths;
	private ArrayList<File>			_basePaths;
	private ArrayList<ClassLoader>	_jars;


	private void _loadSettings() throws IOException {
		ArrayList<String>	paths;

		if(null == _properties) {
			for(ClassLoader jar : _jars) {
				paths= new ArrayList<String>();

				for(Object url : Collections.list(jar.getResources(_filename))) {

				}
			}
		}
	}
	//Enumeration ClassLoader.getResources(String name);
	//walk directory structures to find paths
	private String _stringArrayToString(String[] strings) {
		String	result= "";
		String	prefix= "[\"";
		String	suffix= "";

		for(String string : strings) {
			result+= prefix+string;
			prefix= "\",\"";
			suffix= "\"]";
		}
		return result+suffix;
	}
	public static void main(String... args) {
		Settings	s= new Settings("settings.ini");
		String[]	keys= "a,b,c,d,e,f,g,h,i".split(",");

		for(String key : keys) {
			System.out.print(key+"=");
			System.out.println(s.get(key));
		}
	}
}
/*

user.country=US
user.language=en
os.arch=x86_64
os.name=Mac OS X
os.version=10.6.6
user.name=marcp

user.dir=/Users/marcp/Dropbox/java
user.home=/Users/marcp

java.runtime.name=Java(TM) SE Runtime Environment
sun.boot.library.path=/System/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Libraries
java.vm.version=17.1-b03-307
awt.nativeDoubleBuffering=true
gopherProxySet=false
mrj.build=10M3261
java.vm.vendor=Apple Inc.
java.vendor.url=http://www.apple.com/
path.separator=:
java.vm.name=Java HotSpot(TM) 64-Bit Server VM
file.encoding.pkg=sun.io
user.country=US
sun.java.launcher=SUN_STANDARD
sun.os.patch.level=unknown
java.vm.specification.name=Java Virtual Machine Specification
user.dir=/Users/marcp/Dropbox/java
java.runtime.version=1.6.0_22-b04-307-10M3261
java.awt.graphicsenv=apple.awt.CGraphicsEnvironment
java.endorsed.dirs=/System/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Home/lib/endorsed
os.arch=x86_64
java.io.tmpdir=/var/folders/0Z/0ZPESXMEH9mx8aFRE1Npzk+++TI/-Tmp-/
line.separator=

java.vm.specification.vendor=Sun Microsystems Inc.
os.name=Mac OS X
sun.jnu.encoding=MacRoman
java.library.path=.:/Library/Java/Extensions:/System/Library/Java/Extensions:/usr/lib/java
java.specification.name=Java Platform API Specification
java.class.version=50.0
sun.management.compiler=HotSpot 64-Bit Server Compiler
os.version=10.6.6
http.nonProxyHosts=local|*.local|169.254/16|*.169.254/16
user.home=/Users/marcp
user.timezone=
java.awt.printerjob=apple.awt.CPrinterJob
java.specification.version=1.6
file.encoding=MacRoman
user.name=marcp
java.class.path=.
java.vm.specification.version=1.0
sun.arch.data.model=64
java.home=/System/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Home
java.specification.vendor=Sun Microsystems Inc.
user.language=en
awt.toolkit=apple.awt.CToolkit
java.vm.info=mixed mode
java.version=1.6.0_22
java.ext.dirs=/Library/Java/Extensions:/System/Library/Java/Extensions:/System/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Home/lib/ext
sun.boot.class.path=/System/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Classes/jsfd.jar:/System/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Classes/classes.jar:/System/Library/Frameworks/JavaVM.framework/Frameworks/JavaRuntimeSupport.framework/Resources/Java/JavaRuntimeSupport.jar:/System/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Classes/ui.jar:/System/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Classes/laf.jar:/System/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Classes/sunrsasign.jar:/System/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Classes/jsse.jar:/System/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Classes/jce.jar:/System/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Classes/charsets.jar
java.vendor=Apple Inc.
file.separator=/
java.vendor.url.bug=http://bugreport.apple.com/
sun.cpu.endian=little
sun.io.unicode.encoding=UnicodeLittle
mrj.version=1060.1.6.0_22-307
socksNonProxyHosts=local|*.local|169.254/16|*.169.254/16
ftp.nonProxyHosts=local|*.local|169.254/16|*.169.254/16
sun.cpu.isalist=
*/

/*
	private void _generateRelativePaths() {
		String[]			relative;
		ArrayList<String[]>	toAdd= new ArrayList<String[]>();
		ArrayList<String[]>	justAdded;

		_relativePaths= new ArrayList<String[]>();
		for(String oneElement : _relativeElements) {
			relative= new String[1];

			relative[0]= oneElement;
			_relativePaths.add(relative);
			System.out.println("One Element Relative Path: "+_stringArrayToString(relative));
		}
		justAdded= _relativePaths;
		do	{
			toAdd.clear();
			for(String[] partial : justAdded) {
				//System.out.println("partial="+_stringArrayToString(partial));
				for(String element : _relativeElements) {
					//System.out.println("element="+element);
					boolean	alreadyHave= false;

					for(String existing : partial) {
						//System.out.println("\tComparing to: "+existing);
						if(existing.equalsIgnoreCase(element)) {
							alreadyHave= true;
							//System.out.println("Already Have");
							break;
						}
					}
					if(!alreadyHave) {
						relative= new String[partial.length+1];
						System.arraycopy(partial, 0, relative, 0, partial.length);
						relative[partial.length]= element;
						System.out.println("Adding: "+_stringArrayToString(relative));
						toAdd.add(relative);
					}
				}
			}
			_relativePaths.addAll(0, toAdd);
		} while(toAdd.size() > 0);
		if(justAdded != _relativePaths) {
			justAdded.clear();
		}
		justAdded= toAdd;
		toAdd= new ArrayList<String[]>();
	}
	private File _relativeToAbsolute(File base, String[] parts) {
		for(String part : parts) {
			base= new File(base, part);
		}
		return new File(base, _filename);
	}
	private String _relativeURI(String[] parts) {
		String	value= null;

		for(String part : parts) {
			if(null == value) {
				value= part;
			} else {
				value= value+"/"+part;
			}
		}
		if(null == value) {
			value+="/"+_filename;
		}
		return value;
	}
	private ArrayList<InputStream> _streams() {
		ArrayList<InputStream>	found= new ArrayList<InputStream>();

		for(File path : _basePaths) {
			File	location;

			for(String[] relative : _relativePaths) {
				location= _relativeToAbsolute(path, relative);
				if(location.isFile()) {
					try	{
						found.add(new FileInputStream(location));
					} catch(FileNotFoundException exception) {
						// we don't want to add it if it doesn't exist
					}
				}
			}
			location= new File(path, _filename);
			if(location.isFile()) {
				try	{
					found.add(new FileInputStream(location));
				} catch(FileNotFoundException exception) {
						// we don't want to add it if it doesn't exist
				}
			}
		}
		for(ClassLoader jar : _jars) {
			InputStream	s;

			for(String[] relative : _relativePaths) {
				s= jar.getResourceAsStream(_relativeURI(relative));
				if(null != s) {
					found.add(s);
				}
			}
			s= jar.getResourceAsStream(_filename);
			if(null != s) {
				found.add(s);
			}
		}
		return found;
	}
*/

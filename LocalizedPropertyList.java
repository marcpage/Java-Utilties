import java.util.Properties;
import java.io.InputStream;
import java.util.Enumeration;
import java.io.IOException;
import java.util.ArrayList;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.net.URLEncoder;

/**
*/
public class LocalizedPropertyList {
	/** The list of System.getProperty() properties supported */
	static String SupportedSystemProperties= "java.vm.vendor,user.country,os.arch,os.name,java.class.version,os.version,java.specification.version,file.encoding,user.name,java.vm.specification.version,sun.arch.data.model,java.specification.vendor,user.language,java.vendor,sun.cpu.endian,sun.io.unicode.encoding";
	/** Takes a Properties input stream and filters it based on System properties.
		If, for example, the property list had "setting=true" it could also have
		"setting.user.country.US=false" which would make setting false when the country is US.
		The value of the System property is URL Encoded (see java.net.URLEncoder for details).
		Here are a list of the supported properties, along with some example values.
		<dl>
			<dt>java.vm.vendor<dd>Apple Inc.
			<dt>user.country<dd>US
			<dt>os.arch<dd>x86_64
			<dt>os.name<dd>Mac OS X
			<dt>java.class.version<dd>50.0
			<dt>os.version<dd>10.7.2
			<dt>java.specification.version<dd>1.6
			<dt>file.encoding<dd>MacRoman
			<dt>user.name<dd>marcp
			<dt>java.vm.specification.version<dd>1.0
			<dt>sun.arch.data.model<dd>64
			<dt>java.specification.vendor<dd>Sun Microsystems Inc.
			<dt>user.language<dd>en
			<dt>java.vendor<dd>Apple Inc.
			<dt>sun.cpu.endian<dd>little
			<dt>sun.io.unicode.encoding<dd>UnicodeLittle
		</dl>
		@param properties			The java.util.Properties stream
		@param valueReplacements	A string of the form of :name=value,name=value[,etc.]"
										Any value that contains "name" will be replaced with "value"
		@throws IOException			On i/o errors
	*/
	static InputStream filter(InputStream properties,String valueReplacements) throws IOException {
		Properties				munge= new Properties();
		ArrayList<String>		propertyNames= new ArrayList<String>();
		ByteArrayOutputStream	flattened= new ByteArrayOutputStream();
		ByteArrayInputStream	stream;

		munge.load(properties); // load the original properties
		// Get all property names
		for(Enumeration e= munge.propertyNames(); e.hasMoreElements();) {
			propertyNames.add((String)e.nextElement());
		}
		// walk the list of supported system properties
		for(String property : SupportedSystemProperties.split(",")) {
			String	value= System.getProperty(property, null);

			if(null != value) { // this system property is available
				String	suffix= "."+property+"."+URLEncoder.encode(value, "UTF-8");

				// Append the system property suffix (.name.value) to each property
				for(String propertyName : propertyNames) {
					String	platformValue= munge.getProperty(propertyName+suffix, null);

					if(null != platformValue) { // if there is a version available, use it instead
						munge.setProperty(propertyName, platformValue);
					}
				}
			}
		}
		if(null != valueReplacements) {
			String[]	pairs= valueReplacements.split(",");

			for(String propertyName : propertyNames) {
				String	value= munge.getProperty(propertyName);

				for(String pair : pairs) {
					String[]	keyValue= pair.split("=");

					if(2 == keyValue.length) {
						value= value.replace(keyValue[0], keyValue[1]);
					}
				}
				munge.setProperty(propertyName, value);
			}
		}
		// turn the Properties back into a stream
		munge.store(flattened, "");
		stream= new ByteArrayInputStream(flattened.toByteArray());
		return stream;
	}
	static public void main(String... args) {
		String	properties=
				"path=logs"+"\n"+
				"path.java.vm.vendor.Apple+Inc.=${log}/Library/Logs"+"\n"+
				"setting=true"+"\n"+
				"setting.user.country.US=false"+"\n"+
				"good=false"+"\n"+
				"good.os.name.Mac+OS+X=true"+"\n"+
				"help=none"+"\n"+
				"help.user.language.en=worse"+"\n";
		Properties	settings= new Properties();

		try	{
			settings.load(filter(new ByteArrayInputStream(properties.getBytes()),
						 "${home}="+System.getProperty("user.home")
					+","+"${logs}="+System.getProperty("user.home")+"/Library/Logs"
			));
			System.out.println("\nUnfiltered\n-----------\n");
			System.out.println("path="+settings.getProperty("path"));
			System.out.println("setting="+settings.getProperty("setting"));
			System.out.println("help="+settings.getProperty("help"));
			System.out.println("good="+settings.getProperty("good"));
			System.out.println("\nAll Unfiltered\n-----------\n");
			for(Enumeration e= settings.propertyNames(); e.hasMoreElements();) {
				String	name= (String)e.nextElement();

				System.out.println(name+"="+settings.getProperty(name));
			}

			settings= new Properties();

			settings.load(new ByteArrayInputStream(properties.getBytes()));
			System.out.println("\nFiltered\n-----------\n");
			System.out.println("path="+settings.getProperty("path"));
			System.out.println("setting="+settings.getProperty("setting"));
			System.out.println("help="+settings.getProperty("help"));
			System.out.println("good="+settings.getProperty("good"));
			System.out.println("\nAll Filtered\n-----------\n");
			for(Enumeration e= settings.propertyNames(); e.hasMoreElements();) {
				String	name= (String)e.nextElement();

				System.out.println(name+"="+settings.getProperty(name));
			}
		} catch(IOException e) {
			e.printStackTrace();
		}
	}
}

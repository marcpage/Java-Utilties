import java.io.Reader;
import java.io.Writer;
import java.util.List;
import java.util.Arrays;
import java.util.Iterator;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.Collections;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.NoSuchElementException;

/**
As documented at: http://en.wikipedia.org/wiki/INI_file

<br>Any key not in a section is in the "global" (null) section at the beginning of the ini stream.

<br>Supported:<ul>
	<li>comments as either ; or #, on their own line or after an item
	<li>whitespace is ignored, both blank lines as well as whitespace before and after keys and values
	<li>values can be quoted with single quotes (') or double quotes (")
	<li>only the first key encountered is used, other instances of the same name are ignored
	<li>changing a value changes it in place
	<li>removing a key removes the comment at the end of the line
	<li>key/value pairs can be delimited with either = or :
</ul>
*/
public class Ini {
	/** Initializes an Ini from a stream.
		@param in			The stream to read the ini text from. Read until end of stream, but not closed.
		@throws IOException	Thrown from reading <code>in</code>
	*/
	public Ini(Reader in) throws IOException {
		BufferedReader	lines= new BufferedReader(in);
		Matcher			lineMatch;
		String			line= lines.readLine();

		_lines= new ArrayList<_Line>();
		while(null != line) {
			if( (lineMatch= _sectionPattern.matcher(line)).find() ) { // section
				_lines.add(new _Line(lineMatch.group(), lineMatch.group(1), lineMatch.group(3)));
			} else if(
					(lineMatch= _singleQuoteKeyValuePattern.matcher(line)).find()
					|| (lineMatch= _doubleQuoteKeyValuePattern.matcher(line)).find()
				) { // key/value pair
				_lines.add(new _Line(lineMatch.group(), _trimIfNotNull(lineMatch.group(1)), lineMatch.group(2), _trimIfNotNull(lineMatch.group(4))));
			} else if( (lineMatch= _keyValuePattern.matcher(line)).find() ) {
				_lines.add(new _Line(lineMatch.group(), _trimIfNotNull(lineMatch.group(1)), _trimIfNotNull(lineMatch.group(2)), _trimIfNotNull(lineMatch.group(4))));
			} else { // comment
				_lines.add(new _Line(line));
			}
			line= lines.readLine();
		}
	}
	/** Serializes this into a textual ini format.
		@param out			The stream that receives the ini data
		@return				Reference to this
		@throws IOException	Due to writes to <code>out</code>
	*/
	public Ini write(Writer out) throws IOException {
		BufferedWriter	lines= new BufferedWriter(out);

		for(_Line line : _lines) {
			lines.write(line.line);
			lines.newLine();
		}
		lines.flush();
		return this;
	}
	/** Sets the comment for the given key in the global section.
		@param key		The key in the global section
		@param comment	The comment to add to the given key
		@return			true if the global section has the requested key,
						false if the comment is ignored
	*/
	public boolean setComment(String key, String comment) {
		return setComment(null, key, comment);
	}
	/** Sets the comment for the given key in the given section.
		@param section	The section to search for the key, or null for the global section
		@param key		The key in the global section
		@param comment	The comment to add to the given key
		@return			true if the given section has the requested key,
						false if the comment is ignored
	*/
	public boolean setComment(String section, String key, String comment) {
		int		sectionStart= _findSectionStart(section);
		_Line	line= _findKeyInSection(sectionStart, key);

		if(null == line) {
			return false;
		}
		line.comment= comment;
		line.rebuild();
		return true;
	}
	/** Sets the value for a given key in the global section.
		@param key		The key in the global section
		@param value	The value to set for the given key
		@return			The previous value for the key, or null if the key did not previously exist
	*/
	public String set(String key, String value) {
		return set(null, key, value, null);
	}
	/** Sets the value for a given key in the given section.
		@param section	The section to add the key, or null for the global section
		@param key		The key in the given section
		@param value	The value to set for the given key
		@return			The previous value for the key, or null if the key did not previously exist
	*/
	public String set(String section, String key, String value) {
		return set(section, key, value, null);
	}
	/** Sets the value for a given key in the given section.
		@param section	The section to add the key/value to, or null for the global section
		@param key		The key in the given section
		@param value	The value to set for the given key
		@param comment	The comment to add, or null for no comment
		@return		The previous value for the section/key, or null if the section/key did not previously exist
	*/
	public String set(String section, String key, String value, String comment) {
		int		sectionOffset;
		int		sectionEnd;
		_Line	found;
		String	oldValue= null;

		sectionOffset= _findSectionStart(section);
		if(-1 == sectionOffset) {
			_lines.add(new _Line(true, section, null));
			sectionOffset= _lines.size();
			found= null;
		} else {
			found= _findKeyInSection(sectionOffset, key);
		}
		if(null == found) {
			sectionEnd= _findEndOfSection(sectionOffset);
			_lines.add(sectionEnd, new _Line(true, key, value, comment));
		} else {
			oldValue= found.value;
			found.keyOrSection= key;
			found.value= value;
			found.comment= comment;
			found.rebuild();
		}
		return oldValue;
	}
	/** Gets the value of a key in the global section.
		@param key			The key to search for in the global section
		@param defaultValue	The value to return if the given key is not found
		@return				The value of the given key, if it exists,
								or <code>defaultValue</code> if it does not exist
	*/
	public String get(String key, String defaultValue) {
		return get(null, key, defaultValue);
	}
	/** Gets the value of a key in the given section.
		@param section		The section the key is to be found in
		@param key			The key to search for in the given section
		@param defaultValue	The value to return if the given key is not found
		@return				The value of the given key, if it exists,
								or <code>defaultValue</code> if it does not exist
	*/
	public String get(String section, String key, String defaultValue) {
		int		sectionStart= _findSectionStart(section);
		_Line	found= _findKeyInSection(sectionStart, key);

		if(null == found) {
			return defaultValue;
		}
		return found.value;
	}
	/** Gets the comment for a key in the global section.
		@param key	The key query
		@return		The comment for the given key, or null if the key was not found or has no comment
	*/
	public String getComment(String key) {
		return getComment(null, key);
	}
	/** Gets the comment for a key in the given section.
		@param section	The section to look in, or null for the global section
		@param key		The key query
		@return			The comment for the given key, or null if the key was not found or has no comment
	*/
	public String getComment(String section, String key) {
		int		sectionStart= _findSectionStart(section);
		_Line	found= _findKeyInSection(sectionStart, key);

		if(null == found) {
			return null;
		}
		return found.comment;
	}
	/** Removes every key/value and comment from the global section.
		@return true if something was deleted from the global section
	*/
	public boolean removeGlobalSection() {
		return removeSection(null);
	}
	/** Removes every key/value and comment from the given section.
		@param section	The section to remove, or null for the global section
		@return true if something was deleted from the given section
	*/
	public boolean removeSection(String section) {
		int	sectionStart= _findSectionStart(section);
		int	sectionEnd;

		if(sectionStart < 0) {
			return false;
		}
		sectionEnd= _findEndOfSection(sectionStart);
		if( (0 == sectionStart) && (0 == sectionEnd) ) {
			return false;
		}
		if(sectionStart > 0) { // global section does not have a header
			--sectionStart; // get the section header too
		}
		while(sectionEnd > sectionStart) {
			_lines.remove(sectionStart); // why is removeRange protected? It's so handy
			--sectionEnd;
		}
		return true;
	}
	/** Removes a key from the global section.
		@param key	The key to remove
		@return		true if the key was found and removed
	*/
	public boolean remove(String key) {
		return remove(null, key);
	}
	/** Removes a key from the given section.
		@param section	The section to remove the key from
		@param key		The key to remove
		@return			true if the key was found and removed
	*/
	public boolean remove(String section, String key) {
		int	sectionStart= _findSectionStart(section);
		int	lineIndex= _findKey(sectionStart, key);

		if(-1 == lineIndex) {
			return false;
		}
		_lines.remove(lineIndex);
		if( ((lineIndex < _lines.size()) || _lines.get(lineIndex).isSection())
				&& (lineIndex > 0) && _lines.get(lineIndex - 1).isSection() ) {
			_lines.remove(lineIndex - 1); // last line in the section, remove the section
		}
		return true;
	}
	/** Removes a comment from the key in the global section.
		@param key	The key whose comment (at the end of the line) is to be removed
		@return		false if the key is not in the global section, or the key does not have a comment
	*/
	public boolean removeComment(String key) {
		return removeComment(null, key);
	}
	/** Removes a comment from the key in the given section.
		@param section	The section to look for key in
		@param key		The key whose comment (at the end of the line) is to be removed
		@return			false if the key is not in the given section, or the key does not have a comment
	*/
	public boolean removeComment(String section, String key) {
		int		sectionStart= _findSectionStart(section);
		_Line	line= _findKeyInSection(sectionStart, key);

		if( (null == line) || (null == line.comment) ) {
			return false;
		}
		line.comment= null;
		return true;
	}
	/** Returns list of sections.
		@return	Iterable suitable to use foreach of the sections (including null for the global section)
	*/
	public Iterable<String> sections() {
		return new _Iterator(null, _Iterator.kWhatSections);
	}
	/** Gets every line.
		These lines are comment lines, section lines, key/value/comment lines.
		Every line is returned.
		@return The lines (for use in foreach) that were read / will be written
	*/
	public Iterable<String> lines() {
		return new _Iterator(null, _Iterator.kWhatLines);
	}
	/** Gets the keys in the global section.
		@return	(foreach)able list of the keys in the global section
	*/
	public Iterable<String> keys() {
		return keys(null);
	}
	/** Gets the keys in the given section.
		@return	(foreach)able list of the keys in the given section
	*/
	public Iterable<String> keys(String section) {
		return new _Iterator(section, _Iterator.kWhatKeys);
	}
	/** Gets the values in the given section.
		@return	(foreach)able list of the values in the given section
	*/
	public Iterable<String> values(String section) {
		return new _Iterator(section, _Iterator.kWhatValues);
	}
	/** Gets the comments in the given section.
		@return	(foreach)able list of the comments in the given section
	*/
	public Iterable<String> comments(String section) {
		return new _Iterator(section, _Iterator.kWhatComments);
	}
	/** Updates (adds or replaces) elements from another Ini.
		Each section is iterated over, then each key/value is iterated over and <code>set</code> on this.
		Comments on key/value are maintained.
		Comments on their own lines are not maintained.
		Comments on sections are not maintained.
		Empty sections are not maintained.
		Duplicate sections/keys are not maintained (first wins).
		@param newValues	Values from another Ini to update this with
		@return				<code>this</code>
	*/
	public Ini update(Ini newValues) {
		for(String section : newValues.sections()) {
			for(String key : newValues.keys(section)) {
				String	comment= newValues.getComment(section, key);

				set(section, key, newValues.get(section, key, null), comment);
			}
		}
		return this;
	}
	/** Determines if this is equal to another object.
		Comments and duplicate sections and key/values are ignored in the comparison.
		@param obj	The object to compare against
		@return		true if <code>obj</code> is an Ini which has the same sections, with the same key/value pairs.
	*/
	public boolean equals(Object obj) {
		if(obj instanceof Ini) {
			Ini	other= (Ini)obj;

			for(String section : sections()) {
				for(String key : keys()) {
					String	myValue= get(section, key, null);
					String	theirValue= other.get(section, key, null);

					if( (null == myValue) && (null != theirValue)
							|| (null != myValue) && (null == theirValue)
							|| (null != myValue) && !myValue.equals(theirValue) ) {
						return false;
					}
				}
			}
			for(String section : other.sections()) {
				for(String key : other.keys()) {
					String	myValue= get(section, key, null);
					String	theirValue= other.get(section, key, null);

					if( (null == myValue) && (null != theirValue)
							|| (null != myValue) && (null == theirValue)
							|| (null != myValue) && !myValue.equals(theirValue) ) {
						return false;
					}
				}
			}
			return true;
		}
		return false;
	}
	/** The characters to escape.*/
	private static final String[]	_unescapes= "\\,\0,\007,\b,\t,\r,\n,;,#,=,:,',\"".split(",");
	/** The characters to unescape.*/
	private static final String[]	_escapes= "\\\\,\\0,\\a,\\b,\\t,\\r,\\n,\\;,\\#,\\=,\\:,\\x0027,\\x0022".split(",");
	/** The characters to escape in reverse order.*/
	private static final String[]	_reverseUnescapes= _reverse(_unescapes);
	/** The characters to unescape in reverse order.*/
	private static final String[]	_reverseEscapes= _reverse(_escapes);
	/** The pattern of a section */
	private static final Pattern	_sectionPattern= Pattern.compile("^\\s*\\[\\s*([^\\]]+)\\s*\\]\\s*([#;]\\s*(.*))?\\s*$");
	/** The pattern of a key/single-quote-value (') line */
	private static final Pattern	_singleQuoteKeyValuePattern= Pattern.compile("^\\s*([^#;:=][^:=]*)\\s*[:=]\\s*'([^']+)'\\s*([#;]\\s*(.*))?\\s*$");
	/** The pattern of a key/double-quote-value (") line */
	private static final Pattern	_doubleQuoteKeyValuePattern= Pattern.compile("^\\s*([^#;:=][^:=]*)\\s*[:=]\\s*\"([^']+)\"\\s*([#;]\\s*(.*))?\\s*$");
	/** The pattern of a key/value line */
	private static final Pattern	_keyValuePattern= Pattern.compile("^\\s*([^#;:=][^:=]*)\\s*[:=]\\s*([^#;\\r\\n]+)\\s*([#;]\\s*(.*))?\\s*$");
	/** The pattern of an escaped unicode value */
	private static final Pattern	_unicodePointPattern= Pattern.compile("\\\\x([0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f])");
	/** The format for escaping a unicode value */
	private static final String		_unicodeEscape= "\\x%04x";
	/** The lowest character value that is not unicode escaped */
	private static final char		_minUnescaped= ' ';
	/** The highest character value that is not unicode escaped */
	private static final char		_maxUnescaped= '~';
	/** The key/value separator that will be used for new values */
	private static final char		_preferredKeyValueSeparator= '=';
	/** The value that will be used to start new comments */
	private static final String		_preferredCommentStart= "# ";
	/** The value that will be used to append a comment to a line */
	private static final String		_preferredCommentSeparator= "\t"+_preferredCommentStart;
	/** Does a series of search and replaces on a string.
		Walks through a list of search patterns and replaces them with a matching replacement string.
		@param item		The string to modify
		@param searches	The strings to search for. Length must be the same as <code>replaces</code>
		@param replaces	The strings to replace with. Length must be the same as <code>searches</code>
		@return			<code>item</code> with every instance of <code>searches</code> replaced with <code>replaces</code>
	*/
	private static String _replaceWithList(String item, String[] searches, String[] replaces) {
		assert null != item;
		assert null != searches;
		assert null != replaces;
		assert searches.length == replaces.length;
		for(int i= 0; i < searches.length; ++i) {
			item= item.replace(searches[i], replaces[i]);
		}
		return item;
	}
	/** Escapes a string for writing to a line.
		First replaces the <code>_unescapes</code> with the <code>_escapes</code>.
		Then walks every character that is not in the range of <code>_minUnescaped</code> and <code>_maxUnescaped</code> inclusive
		gets converted to a unicode value.
		@param raw	The raw data. May be null
		@return		<code>raw</code> encoded for ini files, or null if <code>raw</code> was null
	*/
	private static String _escape(String raw) {
		if(null == raw) {
			return null;
		}
		String			initial= _replaceWithList(raw, _unescapes, _escapes);
		String			result= "";

		for(char c : initial.toCharArray()) {
			if( (_minUnescaped > c) || (c > _maxUnescaped) ) {
				StringBuilder	formatted= new StringBuilder();
				Formatter		formatter= new Formatter();

				formatter.format(_unicodeEscape, (int)c);
				result+= formatted.toString();
			} else {
				result+= String.valueOf(c);
			}
		}
		return result;
	}
	/** Unescapes a value from an ini-type stream.
		Reverses the search/replace and then looks for unicode value patterns and replaces them.
		@param escaped	A value from an ini-type stream. May be null
		@return			The unescaped form of <code>escaped</code> or null if <code>escaped</code> was null
	*/
	private static String _unescape(String escaped) {
		if(null == escaped) {
			return null;
		}
		String	initial= _replaceWithList(escaped, _reverseEscapes, _reverseUnescapes);
		Matcher	unicode= _unicodePointPattern.matcher(initial);
		String	result= "";
		int		lastOffset= 0;

		while(unicode.find()) {
			result+= initial.substring(lastOffset, unicode.start());
			result+= String.valueOf((char)Integer.parseInt(unicode.group(1), 16));
			lastOffset= unicode.end();
		}
		return result + initial.substring(lastOffset);
	}
	/** Trims a string if it is not null.
		@param value	possibly null string
		@return			if <code>value</code> is null, null is returned, otherwise value.trim() is returned
	*/
	private static String _trimIfNotNull(String value) {
		if(null == value) {
			return null;
		}
		return value.trim();
	}
	/** Reverses and array of strings.
		@param in	The array of strings
		@return		An array with the same contents as <code>in</code>, but reversed.
	*/
	private static String[] _reverse(String[] in) {
		List<String>	list= Arrays.asList(in);

		Collections.reverse(list);
		return list.toArray(in);
	}
	/** A line from an ini-type stream. */
	private static class _Line {
		/** The actual contents of the line, without any line endings */
		public String	line;
		/** The key or section. It's a section if there is no value. */
		public String	keyOrSection;
		/** The value of the key. */
		public String	value;
		/** The comment at the end of a key/value or section, or a line that does not have either. */
		public String	comment;
		/** New _Line with the line, raw key, raw value and raw comment from the ini stream.
			@param lineIn		The actual line from the ini-type stream, without line ending
			@param keyIn		The actual key from the ini-type stream, still escaped
			@param valueIn		The actual value from the ini-type stream, still escaped
			@param commentIn	The actual comment from the ini-type stream, still escaped
		*/
		public _Line(String lineIn, String keyIn, String valueIn, String commentIn) {
			line= lineIn;
			keyOrSection= _unescape(keyIn);
			value= _unescape(valueIn);
			comment= _unescape(commentIn);
		}
		/** New _Line with the line, raw section and raw comment from the ini stream.
			@param lineIn		The actual line from the ini-type stream, without line ending
			@param sectionIn	The actual section from the ini-type stream, still escaped
			@param commentIn	The actual comment from the ini-type stream, still escaped
		*/
		public _Line(String lineIn, String sectionIn, String commentIn) {
			line= lineIn;
			keyOrSection= _unescape(sectionIn);
			value= null;
			comment= _unescape(commentIn);
		}
		/** New _Line with the line, raw key, raw value and raw comment from the ini stream.
			@param commentIn	The actual comment from the ini-type stream, still escaped
		*/
		public _Line(String commentIn) {
			line= commentIn;
			keyOrSection= null;
			value= null;
			comment= _unescape(commentIn);
		}
		/** Create a _Line from client use, not an ini-type stream.
			@param directSignatureIgnoreThisParameter	Pass true. Used for signature differentiation.
			@param sectionIn							The section to create.
			@param commentIn							The comment for the section.
		*/
		public _Line(boolean directSignatureIgnoreThisParameter, String sectionIn, String commentIn) {
			keyOrSection= sectionIn;
			value= null;
			comment= commentIn;
			rebuild();
		}
		/** Create a _Line from client use, not an ini-type stream.
			@param directSignatureIgnoreThisParameter	Pass true. Used for signature differentiation.
			@param keyIn								The key to create.
			@param valueIn								The value for the key
			@param commentIn							The comment for the key.
		*/
		public _Line(boolean directSignatureIgnoreThisParameter, String keyIn, String valueIn, String commentIn) {
			keyOrSection= _unescape(keyIn);
			value= _unescape(valueIn);
			comment= commentIn;
			rebuild();
		}
		/** Tells how to treat <code>keyOrSection</code>.
			@return	true if keyOrSection is a section name, false if it is a key name
		*/
		public boolean isSection() {
			return (null == value) && (null != keyOrSection);
		}
		/** Rebuilds the <code>line</code> field from the other paramters.
			Usually after modifying fields, rebuild() is called.
		*/
		public void rebuild() {
			String	commentString;

			if(null == comment) {
				commentString= "";
			} else {
				commentString= _preferredCommentSeparator+_escape(comment);
			}
			if(isSection()) {
				line= "["+_escape(keyOrSection)+"]"+commentString;
			} else if(null != keyOrSection) {
				line= _escape(keyOrSection)+_preferredKeyValueSeparator+_escape(value)+comment;
			} else if(null != comment) {
				line= _preferredCommentStart+_escape(comment);
			} else {
				assert null != comment;
				line= "";
			}
		}
	}
	/** Iterator for just about anything. */
	private class _Iterator implements Iterable<String>, Iterator<String> {
		/** Iterator over lines. */
		public static final int	kWhatLines= 0;
		/** Iterator over sections. */
		public static final int	kWhatSections= 1;
		/** Iterator over keys. */
		public static final int	kWhatKeys= 2;
		/** Iterator over values. */
		public static final int	kWhatValues= 3;
		/** Iterator over comments. */
		public static final int	kWhatComments= 4;
		/** Creates a new iterator.
			@param section	The section to iterate over. Ignored for kWhatLines and kWhatSections.
			@param what		What to iterator over
		*/
		public _Iterator(String section, int what) {
			if( (kWhatLines == _what) && (_lines.size() == 0) ) {
				_lineIndex= -1;
			} else {
				_lineIndex= 0;
			}
			_what= what;
			if( (kWhatKeys == _what) || (kWhatValues == _what) || (kWhatComments == _what) ) {
				_lineIndex= _findSectionStart(section);
			}
		}
		/** Are there more elements to get.
			@return	true if you can call next() to get back the next item.
					false if there are not more items.
		*/
		public boolean hasNext() {
			if( (_lineIndex < 0) || (_lineIndex >= _lines.size()) ) {
				return false;
			}
			return true;
		}
		/** Gets the next item.
			@return	The next item
		*/
		public String next() {
			if(!hasNext()) {
				throw new NoSuchElementException();
			}
			return _next();
		}
		/** Does nothing. Optional.
		*/
		public void remove() {
		}
		/** Gets the iterator.
			@return <code>this</code>
		*/
		public Iterator<String> iterator() {
			return this;
		}
		/** The index in <code>_lines</code> of the next() item,
				or <= -1 or >= _lines.size() if there are no more items.
		*/
		private int		_lineIndex;
		/** What type of data are we iterating over */
		private int		_what;
		/** Returns the next item.
			The next item is currently pointed at by <code>_lineIndex</code>
				then <code>_lineIndex</code> is incremented.
			@return	null for global section, or no more items, otherwise, the value requested
		*/
		private String _next() {
			String	result= null;

			if(_what == kWhatSections) {
				if( (_lineIndex > 0)
						|| ( (0 != _lines.size()) && _lines.get(_lineIndex).isSection()) ) {
					result= _lines.get(_lineIndex).keyOrSection; // not the global section
				}
				do {
					++_lineIndex; // skip to next section
				} while( (_lineIndex < _lines.size()) && !_lines.get(_lineIndex).isSection() );
			} else if(_what == kWhatLines) {
				result= _lines.get(_lineIndex).line;
				++_lineIndex;
			} else {
				result= _value(_what, _lines.get(_lineIndex));
				do	{
					++_lineIndex;
					if(_lines.get(_lineIndex).isSection()) { // we've reached the end of the section
						_lineIndex= _lines.size();
					}
				} while( (_lineIndex < _lines.size()) && (null == _value(_what, _lines.get(_lineIndex))) );
			}
			return result;
		}
		/** Gets the requested field of of _Line.
			@param what	Must be kWhatKey, kWhatValue or kWhatComment
			@param line	The line to get the data from
			@return		The requested fields value, or null if the field has no value
							or <code>what</code> is not valid.
		*/
		private String _value(int what, _Line line) {
			if(kWhatKeys == what) {
				return line.keyOrSection;
			} if(kWhatValues == what) {
				return line.value;
			} if(kWhatComments == what) {
				return line.comment;
			}
			return null;
		}
	}
	/** The lines of the ini-type stream. */
	private ArrayList<_Line>	_lines;
	/** Finds the index of the first item of the given section.
		If the section is empty, returns the index of the next section.
		If the section is empty and the last section, returns the index after the last line.
		@param section	The section to look for, or null for the globa section.
		@return			the index of the first item after the given section, or -1 if the section does not exit
	*/
	private int _findSectionStart(String section) {
		int		lineIndex= 0;
		_Line	line= null;

		if(null == section) {
			return lineIndex;
		}
		while( (lineIndex < _lines.size())
				&& (!(line= _lines.get(lineIndex)).isSection()
					|| !section.equals(line.keyOrSection) )
			) {
			++lineIndex;
		}
		if( (null != line) && line.isSection() && section.equals(line.keyOrSection) ) {
			return lineIndex + 1;
		}
		return -1;
	}
	/** Finds the index of a key in a section.
		Walks all items starting at <code>sectionStart</code> until
			(a) we reach the end of the elements, (b) we reach another section
			or (c) we find the key.
		@param sectionStart	The offset of the first item in a section
		@param key			The key to look for.
		@return				the index of the key, or -1 if the key does not exist in this section.
	*/
	private int _findKey(int sectionStart, String key) {
		int		lineIndex= sectionStart;
		_Line	line= null;

		while( (lineIndex < _lines.size())
				&& !(line= _lines.get(lineIndex)).isSection()
				&& !key.equals(line.keyOrSection) ) {
			++lineIndex;
		}
		if( (null == line) || line.isSection() || !line.keyOrSection.equals(key) ) {
			return -1;
		}
		return lineIndex;
	}
	/** Finds the index of a key in a section.
		Walks all items starting at <code>sectionStart</code> until
			(a) we reach the end of the elements, (b) we reach another section
			or (c) we find the key.
		@param sectionStart	The offset of the first item in a section
		@param key			The key to look for.
		@return				The actual _Line for the given key in the section, or null if not found
	*/
	private _Line _findKeyInSection(int sectionStart, String key) {
		int		lineIndex= _findKey(sectionStart, key);

		if(-1 == lineIndex) {
			return null;
		}
		return _lines.get(lineIndex);
	}
	/** Finds the next section index.
		@param sectionStart	The index of the first item in the section. Must <b>not</b> be less than zero.
		@return				The index of the next section, or <code>_lines.size()</code> if this is the last section.
	*/
	private int _findEndOfSection(int sectionStart) {
		int	lineIndex= sectionStart;

		while( (lineIndex < _lines.size()) && !_lines.get(lineIndex).isSection() ) {
			++lineIndex;
		}
		return lineIndex;
	}
	/** The test.
		@param args	ignored.
	*/
	public static void main(String... args) {
		String					test1=
 "g1=5 # test g1\r\n"
+"g2=6 # test g2\r"
+"g3=7 # test g3\n"
+"g4=8\n"
+"g5\n"
+"g1=3\n"
+"\r"
+"[s1] # test s1\n"
+" s1.1 = help # test s1.1\n"
+"\ts1.2=\thello # test s1.2\n"
+"xattr_com.apple.FinderInfo=';\\x00f0\\#\\x009cB\\x0084&~\\x001e\\\\x?\\x00c7\\x00a4i5\\x00b1\\r\\x00e1\\x00f87\\x00a0C@'\n"
+"\n"
+"[s2]\n"
+"x:5\n"
+"t:\"#\"\n"
+"g:'#' ;test\n"
+"[s1]\n"
+"s1.1=done\n";
		try	{
			Ini						ini1= new Ini(new java.io.StringReader(test1));
			java.io.StringWriter	out= new java.io.StringWriter(test1.length());
			Ini						ini1_1;

			ini1.write(out);
			ini1_1= new Ini(new java.io.StringReader(out.toString()));
			if(!ini1.equals(ini1_1)) {
				System.err.println("read/write/read comparison failure");
			}
			if(!ini1_1.get("g1","").equals("5")) {
				System.err.println("ini1_1:g1:5 failed:"+ini1_1.get("g1","[NOT FOUND]"));
			}
			if(!ini1_1.get("g2","").equals("6")) {
				System.err.println("ini1_1:g2:6 failed:"+ini1_1.get("g2","[NOT FOUND]"));
			}
			if(!ini1_1.get("g3","").equals("7")) {
				System.err.println("ini1_1:g3:7 failed:"+ini1_1.get("g3","[NOT FOUND]"));
			}
			if(!ini1_1.get("g4","").equals("8")) {
				System.err.println("ini1_1:g4:8 failed:"+ini1_1.get("g4","[NOT FOUND]"));
				for(String line : ini1_1.lines()) {
					System.err.println("[["+line+"]]");
				}
			}
			if(ini1_1.get("g5",null) != null) {
				System.err.println("ini1_1:g5 failed:"+ini1_1.get("g5","[NOT FOUND]"));
			}
			if(!ini1_1.getComment("g1").equals("test g1")) {
				System.err.println("ini1_1:g1:comment:test g1 failed");
			}
			if(!ini1_1.getComment("g2").equals("test g2")) {
				System.err.println("ini1_1:g2:comment:test g2 failed");
			}
			if(!ini1_1.getComment("g3").equals("test g3")) {
				System.err.println("ini1_1:g3:comment:test g3 failed");
			}
			if(ini1_1.getComment("g4") != null) {
				System.err.println("ini1_1:g4:comment:null failed");
			}
			if(ini1_1.getComment("g5") != null) {
				System.err.println("ini1_1:g5:comment:null failed");
			}
			if(!ini1_1.get("s1", "s1.1","").equals("help")) {
				System.err.println("ini1_1:s1:s1.1:help failed:"+ini1_1.get("s1", "s1.1","[NOT FOUND]"));
			}
			if(!ini1_1.get("s1", "s1.2","").equals("hello")) {
				System.err.println("ini1_1:s1:s1.1:help failed:"+ini1_1.get("s1", "s1.2", "[NOT FOUND]"));
			}
			if(!ini1_1.get("s1", "xattr_com.apple.FinderInfo","").equals(";\u00f0#\u009cB\u0084&~\u001e\\x?\u00c7\u00a4i5\u00b1\r\u00e1\u00f87\u00a0C@")) {
				System.err.println("ini1_1:s1:xattr_com.apple.FinderInfo failed:"+ini1_1.get("s1", "xattr_com.apple.FinderInfo","[NOT FOUND]"));
				System.err.println(";\u00f0#\u009cB\u0084&~\u001e\\x?\u00c7\u00a4i5\u00b1\r\u00e1\u00f87\u00a0C@");
			}
			if(!ini1_1.getComment("s1", "s1.1").equals("test s1.1")) {
				System.err.println("ini1_1:s1:s1.1:comment:test s1.1 failed");
			}
			if(!ini1_1.getComment("s1", "s1.2").equals("test s1.2")) {
				System.err.println("ini1_1:s1:s1.2:comment:test s1.2 failed");
			}
			if(ini1_1.getComment("s1", "xattr_com.apple.FinderInfo") != null) {
				System.err.println("ini1_1:s1:xattr_com.apple.FinderInfo:comment:null failed");
			}
			if(ini1_1.getComment("s1", "s1.3") != null) {
				System.err.println("ini1_1:s1:s1.3:comment:null failed");
			}
			if(!ini1_1.get("s2", "x","").equals("5")) {
				System.err.println("ini1_1:s2:x:5 failed:"+ini1_1.get("s2","x","[NOT FOUND]"));
			}
			if(!ini1_1.get("s2", "t","").equals("#")) {
				System.err.println("ini1_1:s2:t:# failed:"+ini1_1.get("s2","t","[NOT FOUND]"));
			}
			if(!ini1_1.get("s2", "g","").equals("#")) {
				System.err.println("ini1_1:s2:g:# failed:"+ini1_1.get("s2","g","[NOT FOUND]"));
			}
			if(ini1_1.getComment("s2", "x") != null) {
				System.err.println("ini1_1:s2:x:comment:null failed");
			}
			if(ini1_1.getComment("s2", "t") != null) {
				System.err.println("ini1_1:s2:t:comment:null failed");
			}
			if(!ini1_1.getComment("s2", "g").equals("test")) {
				System.err.println("ini1_1:s2:g:comment:test failed");
			}
		} catch(IOException exception) {
			System.err.println(exception);
		}
	}
}

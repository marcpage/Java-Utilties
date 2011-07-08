/**
	<b>TODO</b><ul>
		<li>Implement
	</ul>

/usr/bin/stat -s Manifest.java 
st_dev=234881027 st_ino=27412188 st_mode=0100644 st_nlink=1 st_uid=502 st_gid=20 st_rdev=0 st_size=8177 st_atime=1310088473 st_mtime=1310086939 st_ctime=1310086939 st_birthtime=1310086939 st_blksize=4096 st_blocks=16 st_flags=0

/usr/bin/chflags <st_flags in octal> <path>
/bin/chmod 0<last for digits of st_mode>

st_mode
						S_IFMT	0170000	bitmask for the file type bitfields
	0100644 = file		S_IFREG 0100000
	0040755 = directory	S_IFDIR	0040000
	0120755 = symlink	S_IFLNK	0120000	
						S_ISUID	0004000	set UID bit
						S_ISGID	0002000	set-group-ID bit
						S_ISVTX	0001000	sticky bit
*/

class Stat {
	public static boolean available() {
		return false;
	}
}

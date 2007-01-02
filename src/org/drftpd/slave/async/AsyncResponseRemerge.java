package org.drftpd.slave.async;

import java.io.File;
import java.util.Collections;
import java.util.List;

import org.drftpd.slave.LightRemoteInode;

/**
 * @author zubov
 * @version $Id$
 */
public class AsyncResponseRemerge extends AsyncResponse {
	private List<LightRemoteInode> _inodes;

	private String _path;

	public AsyncResponseRemerge(String directoryPath,
			List<LightRemoteInode> inodes) {
		super("Remerge");
		if (File.separatorChar == '\\') { // stupid win32 hack
			directoryPath = directoryPath.replaceAll("\\\\", "/");
		}
		if (directoryPath.indexOf('\\') != -1) {
			throw new RuntimeException(
					"\\ is not an acceptable character in a directory path");
		}
		if (directoryPath.equals("")) {
			directoryPath = File.separator;
		}
		_path = directoryPath;
		_inodes = inodes;
	}

	public String getPath() {
		return _path;
	}

	public List<LightRemoteInode> getFiles() {
		return Collections.unmodifiableList(_inodes);
	}

	public String toString() {
		return getClass().getName() + "[path=" + getPath() + "]";
	}
}

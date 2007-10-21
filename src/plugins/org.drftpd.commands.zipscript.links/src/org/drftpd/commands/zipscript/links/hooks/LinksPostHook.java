/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.drftpd.commands.zipscript.links.hooks;

import java.io.FileNotFoundException;
import java.util.ResourceBundle;

import org.apache.log4j.Logger;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.PostHookInterface;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.commands.dataconnection.DataConnectionHandler;
import org.drftpd.commands.dir.Dir;
import org.drftpd.commands.zipscript.links.LinkUtils;
import org.drftpd.commands.zipscript.vfs.ZipscriptVFSDataSFV;
import org.drftpd.dynamicdata.KeyNotFoundException;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.protocol.zipscript.common.SFVStatus;
import org.drftpd.vfs.FileHandle;
import org.drftpd.vfs.LinkHandle;
import org.drftpd.vfs.ObjectNotValidException;

/**
 * @author djb61
 * @version $Id$
 */
public class LinksPostHook implements PostHookInterface {

	private static final Logger logger = Logger.getLogger(LinksPostHook.class);

	private ResourceBundle _bundle;

	public void initialize(StandardCommandManager cManager) {
		_bundle = cManager.getResourceBundle();
	}

	public void doLinksSTORIncompleteHook(CommandRequest request, CommandResponse response) {
		if (response.getCode() != 226) {
			// STOR failed, abort link
			return;
		}
		FileHandle transferFile;
		try {
			transferFile =  (FileHandle) response.getObject(DataConnectionHandler.TRANSFER_FILE);
		} catch (KeyNotFoundException e) {
			// We don't have a file, we shouldn't have ended up here but return anyway
			return;
		}
		String transferFileName = transferFile.getName();
		if (transferFileName.toLowerCase().endsWith(".sfv")) {
			LinkUtils.processLink(request, "create", _bundle);
		}
		else {
			ZipscriptVFSDataSFV sfvData = new ZipscriptVFSDataSFV(request.getCurrentDirectory());
			try {
				SFVStatus sfvStatus = sfvData.getSFVStatus();
				if (sfvStatus.isFinished()) {
					// dir is complete, remove link
					LinkUtils.processLink(request, "delete", _bundle);
				}
			} catch (NoAvailableSlaveException e) {
				// Slave holding sfv is unavailable
			} catch (FileNotFoundException e) {
				// No sfv in dir
			}
		}
		return;
	}

	public void doLinksDELECleanupHook(CommandRequest request, CommandResponse response) {
		if (response.getCode() != 250) {
			// DELE failed, abort cleanup
			return;
		}
		String deleFileName;
		try {
			deleFileName =  (String) response.getObject(Dir.FILENAME);
		} catch (KeyNotFoundException e) {
			// We don't have a file, we shouldn't have ended up here but return anyway
			return;
		}

		if (deleFileName.endsWith(".sfv")) {
			LinkUtils.processLink(request, "delete", _bundle);
			try {
				ZipscriptVFSDataSFV sfvData = new ZipscriptVFSDataSFV(request.getCurrentDirectory());
				sfvData.removeSFVInfo();
			} catch(FileNotFoundException e) {
				// No inode to remove sfvinfo from
			}
		}
		else {
			ZipscriptVFSDataSFV sfvData = new ZipscriptVFSDataSFV(request.getCurrentDirectory());
			try {
				SFVStatus sfvStatus = sfvData.getSFVStatus();
				if (!sfvStatus.isFinished()) {
					// dir is now incomplete, add link
					LinkUtils.processLink(request, "create", _bundle);
				}
			} catch (NoAvailableSlaveException e) {
				// Slave holding sfv is unavailable
			} catch (FileNotFoundException e) {
				// No sfv in dir
			}
		}
		return;
	}

	public void doLinksWIPECleanupHook(CommandRequest request, CommandResponse response) {
		if (response.getCode() != 200) {
			// WIPE failed, abort cleanup
			return;
		}
		try {
			for (LinkHandle link : request.getCurrentDirectory().getLinksUnchecked()) {
				try {
					link.getTargetDirectoryUnchecked();
				} catch (FileNotFoundException e1) {
					// Link target no longer exists, remove it
					link.deleteUnchecked();
				} catch (ObjectNotValidException e1) {
					// Link target isn't a directory, delete the link as it is bad
					link.deleteUnchecked();
				}
			}
		} catch (FileNotFoundException e2) {
			logger.warn("Invalid link in dir " + request.getCurrentDirectory().getPath(),e2);
		}
		// Have to check parent too to allow for the case of wiping a special subdir
		if (!request.getCurrentDirectory().isRoot()) {
			try {
				for (LinkHandle link : request.getCurrentDirectory().getParent().getLinksUnchecked()) {
					try {
						link.getTargetDirectoryUnchecked();
					} catch (FileNotFoundException e1) {
						// Link target no longer exists, remove it
						link.deleteUnchecked();
					} catch (ObjectNotValidException e1) {
						// Link target isn't a directory, delete the link as it is bad
						link.deleteUnchecked();
					}
				}
			} catch (FileNotFoundException e2) {
				logger.warn("Invalid link in dir " + request.getCurrentDirectory().getParent().getPath(),e2);
			}
		}
	}
}

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
package net.sf.drftpd.event;

import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;


/**
 * @author mog
 *
 * @version $Id: DirectoryFtpEvent.java,v 1.9 2004/08/03 20:13:54 zubov Exp $
 */
public class DirectoryFtpEvent extends ConnectionEvent {
    private LinkedRemoteFileInterface directory;

    public DirectoryFtpEvent(BaseFtpConnection conn, String command,
        LinkedRemoteFileInterface directory) {
        this(conn, command, directory, System.currentTimeMillis());
    }

    public DirectoryFtpEvent(BaseFtpConnection conn, String command,
        LinkedRemoteFileInterface directory, long time) {
        super(conn, command, time);
        this.directory = directory;
    }

    public LinkedRemoteFileInterface getDirectory() {
        return directory;
    }

    public String toString() {
        return getClass().getName() + "[user=" + getUser() + ",cmd=" +
        getCommand() + ",directory=" + directory.getPath() + "]";
    }
}

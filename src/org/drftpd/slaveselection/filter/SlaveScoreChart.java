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
package org.drftpd.slaveselection.filter;

import net.sf.drftpd.master.RemoteSlave;

import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;


/**
 * @author mog
 * @version $Id: SlaveScoreChart.java,v 1.2 2004/08/03 20:14:10 zubov Exp $
 */
public class SlaveScoreChart {
    private Hashtable _scoreChart = new Hashtable();

    public SlaveScoreChart(Collection slaves) {
        for (Iterator iter = slaves.iterator(); iter.hasNext();) {
            RemoteSlave rslave = (RemoteSlave) iter.next();
            _scoreChart.put(rslave, new Integer(0));
        }
    }
}

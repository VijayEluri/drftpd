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
package net.sf.drftpd.mirroring;
import java.io.FileInputStream;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import net.sf.drftpd.FatalException;
import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.FileExistsException;
import net.sf.drftpd.SlaveUnavailableException;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;
import org.apache.log4j.Logger;
/**
 * @author zubov
 * @version $Id: JobManager.java,v 1.45 2004/04/26 21:41:52 zubov Exp $
 */
public class JobManager implements Runnable {
	private static final Logger logger = Logger.getLogger(JobManager.class);
	private ConnectionManager _cm;
	private boolean _isStopped = false;
	private ArrayList _jobList = new ArrayList();
	private boolean _useCRC;
	private Thread thread;
	private int _sleepSeconds;
	/**
	 * Keeps track of all jobs and controls them
	 */
	public JobManager(ConnectionManager cm) throws IOException {
		_cm = cm;
		reload();
	}
	public void startJobs() {
		if (thread != null) {
			stopJobs();
			thread.interrupt();
			while (thread.isAlive()) {
				logger.debug("thread is still alive");
				Thread.yield();
			}
		}
		_isStopped = false;
		thread = new Thread(this, "JobTransferStarter");
		thread.start();
	}

	public void stopJobs() {
		_isStopped = true;
	}

	public boolean isStopped() {
		return _isStopped;
	}

	public synchronized void addJob(Job job) {
		Collection slaves = job.getFile().getSlaves();
		for (Iterator iter = job.getDestinationSlaves().iterator();
			iter.hasNext();
			) {
			RemoteSlave slave = (RemoteSlave) iter.next();
			if (slaves.contains(slave)) {
				iter.remove();
				if (job.isDone()) {
					return;
				}
			}
		}
		_jobList.add(job);
		Collections.sort(_jobList, new JobComparator());
	}
	/**
	 * Gets all jobs.
	 */
	public synchronized List getAllJobs() {
		return Collections.unmodifiableList(_jobList);
	}
	/**
	 * Get all jobs for a specific LinkedRemoteFile.
	 */
	public synchronized List getAllJobs(LinkedRemoteFileInterface lrf) {
		ArrayList tempList = new ArrayList();
		for (Iterator iter = _jobList.iterator(); iter.hasNext();) {
			Job tempJob = (Job) iter.next();
			if (tempJob.getFile() == lrf) {
				tempList.add(tempJob);
			}
		}
		return tempList;
	}
	/**
	 * Get all jobs where Job.getSource() is source
	 */
	public synchronized List getAllJobs(Object source) {
		ArrayList tempList = new ArrayList();
		for (Iterator iter = _jobList.iterator(); iter.hasNext();) {
			Job tempJob = (Job) iter.next();
			if (tempJob.getSource().equals(source))
				tempList.add(tempJob);
		}
		return tempList;
	}
	
	public synchronized Job getNextJob(List busySlaves, List skipJobs) {
		for (Iterator iter = _jobList.iterator(); iter.hasNext();) {
			Job tempJob = (Job) iter.next();
			if (tempJob.getFile().isDeleted() || tempJob.isDone()) {
				iter.remove();
				continue;
			}
			if (skipJobs.contains(tempJob))
				continue;
			Collection availableSlaves = null;
			try {
				availableSlaves = tempJob.getFile().getAvailableSlaves();
			} catch (NoAvailableSlaveException e) {
				continue; // can't transfer what isn't online
			}
			if (!busySlaves.containsAll(availableSlaves)) {
				return tempJob;
			}
		}
		return null;
	}

	/**
	 * Returns true if the file was sent okay
	 */
	public boolean processJob() {
		Job job = null;
		RemoteSlave sourceSlave = null;
		RemoteSlave destSlave = null;
		long time;
		long difference;
		synchronized (this) {
			Collection availableSlaves;
			try {
				availableSlaves = _cm.getSlaveManager().getAvailableSlaves();
			} catch (NoAvailableSlaveException e1) {
				return false;
				// can't transfer with no slaves
			}
			ArrayList busySlavesDown = new ArrayList();
			ArrayList skipJobs = new ArrayList();
			while (!busySlavesDown.containsAll(availableSlaves)) {
				job = getNextJob(busySlavesDown, skipJobs);
				if (job == null) {
					return false;
				}
				if (job.getFile().getSlaves().containsAll(_cm.getSlaveManager().getSlaves())) {
					stopJob(job);
					continue;
				}
				logger.debug("looking up slave for job " + job);
				try {
					sourceSlave =
						_cm
							.getSlaveManager()
							.getSlaveSelectionManager()
							.getASlaveForJobDownload(
							job);
				} catch (NoAvailableSlaveException e) {
					busySlavesDown.addAll(job.getFile().getSlaves());
					continue;
				}
				if (sourceSlave == null) {
					logger.debug(
						"JobManager was unable to find a suitable job for transfer");
					return false;
				}
				try {
					destSlave =
						_cm
							.getSlaveManager()
							.getSlaveSelectionManager()
							.getASlaveForJobUpload(
							job);
					break; // we have a source slave and a destination slave, transfer!
				} catch (NoAvailableSlaveException e) {
					// job was ready to be sent, but it had no slave that was ready to accept it
					skipJobs.add(job);
					continue;
				}
			}
			logger.debug(
				"ready to transfer "
					+ job
					+ " from "
					+ sourceSlave.getName()
					+ " to "
					+ destSlave.getName());
			time = System.currentTimeMillis();
			difference = 0;
			removeJob(job);
		}
		// job is not deleted and is out of the jobList, we are ready to
		// process
		logger.info(
			"Sending "
				+ job.getFile().getName()
				+ " from "
				+ sourceSlave.getName()
				+ " to "
				+ destSlave.getName());
		SlaveTransfer slaveTransfer =
			new SlaveTransfer(job.getFile(), sourceSlave, destSlave);
		try {
			if (!slaveTransfer.transfer(useCRC())) { // crc failed
				try {
					destSlave.getSlave().delete(job.getFile().getPath());
				} catch (IOException e) {
					//couldn't delete it, just carry on
				}
				logger.debug(
					"CRC did not match for "
						+ job.getFile()
						+ " when sending from "
						+ sourceSlave.getName()
						+ " to "
						+ destSlave.getName());
				addJob(job);
				return false;
			}
		} catch (IOException e) {
			logger.debug(
				"Caught IOException in sending "
					+ job.getFile().getName()
					+ " from "
					+ sourceSlave.getName()
					+ " to "
					+ destSlave.getName(),
				e);
			if (!(e instanceof FileExistsException)) {
				try {
					destSlave.getSlave().delete(job.getFile().getPath());
				} catch (SlaveUnavailableException e3) {
					//couldn't delete it, just carry on
				} catch (IOException e1) {
					//couldn't delete it, just carry on
				}
				addJob(job);
				return false;
			}
			logger.debug(
				"File "
					+ job.getFile()
					+ " was already on the destination slave");
			try {
				if (destSlave.getSlave().checkSum(job.getFile().getPath())
					== job.getFile().getCheckSum()) {
					logger.debug("Accepting file because the crc's match");
				} else {
					try {
						destSlave.getSlave().delete(job.getFile().getPath());
					} catch (SlaveUnavailableException e3) {
						//couldn't delete it, just carry on
					} catch (IOException e1) {
						//couldn't delete it, just carry on
					}
					addJob(job);
					return false;
				}
			} catch (RemoteException e1) {
				destSlave.handleRemoteException(e1);
				addJob(job);
				return false;
			} catch (NoAvailableSlaveException e1) {
				addJob(job);
				return false;
			} catch (SlaveUnavailableException e2) {
				addJob(job);
				return false;
			} catch (IOException e1) {
				addJob(job);
				return false;
			}
		} catch (Exception e) {
			logger.debug(
				"Error Sending "
					+ job.getFile().getName()
					+ " from "
					+ sourceSlave.getName()
					+ " to "
					+ destSlave.getName(),
				e);
			addJob(job);
			return false;
		}
		difference = System.currentTimeMillis() - time;
		logger.debug(
			"Sent file "
				+ job.getFile().getName()
				+ " to "
				+ destSlave.getName()
				+ " from "
				+ sourceSlave.getName());
		job.addTimeSpent(difference);
		if (job.removeDestinationSlave(destSlave)) {
			if (job.isDone()) {
				logger.debug("Job is finished, removing job " + job.getFile());
			} else
				addJob(job);
			return true;
		}
		logger.debug(
			"Was unable to remove "
				+ destSlave.getName()
				+ " from the destination list for file "
				+ job);
		addJob(job);
		return false;
	}
	public void reload() {
		Properties p = new Properties();
		try {
			p.load(new FileInputStream("conf/jobmanager.conf"));
		} catch (IOException e) {
			throw new FatalException(e);
		}
		_useCRC = FtpConfig.getProperty(p, "useCRC").equals("true");
		_sleepSeconds =
			1000 * Integer.parseInt(FtpConfig.getProperty(p, "sleepSeconds"));
	}
	public synchronized void removeJob(Job job) {
		_jobList.remove(job);
		Collections.sort(_jobList, new JobComparator());
	}

	private boolean useCRC() {
		return _useCRC;
	}
	
	public void stopJob(Job job) {
		removeJob(job);
		job._destSlaves.clear();
	}

	public void run() {
		while (true) {
			if (isStopped()) {
				logger.debug("Stopping JobTransferStarter thread");
				return;
			}
			new JobTransferThread(this).start();
			try {
				Thread.sleep(_sleepSeconds);
			} catch (InterruptedException e) {
			}
		}
	}

}

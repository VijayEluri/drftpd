package net.sf.drftpd.master.command.plugins;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;

import net.sf.drftpd.Bytes;
import net.sf.drftpd.event.listeners.Trial;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpReply;
import net.sf.drftpd.master.FtpRequest;
import net.sf.drftpd.master.command.CommandHandler;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;
import net.sf.drftpd.master.command.UnhandledCommandException;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.master.config.Permission;
import net.sf.drftpd.master.usermanager.NoSuchUserException;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.master.usermanager.UserFileException;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.tanesha.replacer.ReplacerEnvironment;

/**
 * @version $Id: TransferStatistics.java,v 1.14 2004/02/06 00:20:50 mog Exp $
 */
public class TransferStatistics implements CommandHandler {

	private static final Logger logger =
		Logger.getLogger(TransferStatistics.class);

	static long getStats(String command, User user) {
		// AL MONTH WK DAY
		String period = command.substring(0, command.length() - 2);
		// UP DN
		String updn = command.substring(command.length() - 2);
		if (updn.equals("UP")) {
			if (period.equals("AL"))
				return user.getUploadedBytes();
			if (period.equals("DAY"))
				return user.getUploadedBytesDay();
			if (period.equals("WK"))
				return user.getUploadedBytesWeek();
			if (period.equals("MONTH"))
				return user.getUploadedBytesMonth();
		} else if (updn.equals("DN")) {
			if (period.equals("AL"))
				return user.getDownloadedBytes();
			if (period.equals("DAY"))
				return user.getDownloadedBytesDay();
			if (period.equals("WK"))
				return user.getDownloadedBytesWeek();
			if (period.equals("MONTH"))
				return user.getDownloadedBytesMonth();
		}
		throw new RuntimeException(
			UnhandledCommandException.create(
				TransferStatistics.class,
				command));
	}

	static int getStatsPlace(
		String command,
		User user,
		BaseFtpConnection conn) {
		// AL MONTH WK DAY

		int place = 1;
		long bytes = getStats(command, user);
		List users;
		try {
			users = conn.getUserManager().getAllUsers();
		} catch (UserFileException e) {
			logger.error("IO error:", e);
			return 0;
		}
		for (Iterator iter = users.iterator(); iter.hasNext();) {
			User tempUser = (User) iter.next();
			long tempBytes = getStats(command, tempUser);
			if (tempBytes > bytes)
				place++;
		}
		return place;
	}

	/**
	 * USAGE: site stats [<user>]
	 *	Display a user's upload/download statistics.
	 */
	public FtpReply doSITE_STATS(BaseFtpConnection conn) {
		FtpRequest request = conn.getRequest();

		if (!request.hasArgument()) {
			return FtpReply.RESPONSE_501_SYNTAX_ERROR;
		}

		User user;
		if (!request.hasArgument()) {
			user = conn.getUserNull();
		} else {
			try {
				user =
					conn.getUserManager().getUserByName(request.getArgument());
			} catch (NoSuchUserException e) {
				return new FtpReply(200, "No such user: " + e.getMessage());
			} catch (UserFileException e) {
				logger.log(Level.WARN, "", e);
				return new FtpReply(200, e.getMessage());
			}
		}

		if (conn.getUserNull().isGroupAdmin()
			&& !conn.getUserNull().getGroupName().equals(user.getGroupName())) {
			return FtpReply.RESPONSE_530_ACCESS_DENIED;
		} else if (
			!conn.getUserNull().isAdmin()
				&& !user.equals(conn.getUserNull())) {
			return FtpReply.RESPONSE_530_ACCESS_DENIED;
		}
		FtpReply response = (FtpReply) FtpReply.RESPONSE_200_COMMAND_OK.clone();
		response.addComment("created: " + new Date(user.getCreated()));
		response.addComment("rank alup: " + getStatsPlace("ALUP", user, conn));
		response.addComment("rank aldn: " + getStatsPlace("ALDN", user, conn));
		response.addComment(
			"rank monthup: " + getStatsPlace("MONTHUP", user, conn));
		response.addComment(
			"rank monthdn: " + getStatsPlace("MONTHDN", user, conn));
		response.addComment("rank wkup: " + getStatsPlace("WKUP", user, conn));
		response.addComment("rank wkdn: " + getStatsPlace("WKDN", user, conn));
		response.addComment("races won: " + user.getRacesWon());
		response.addComment("races lost: " + user.getRacesLost());
		response.addComment("races helped: " + user.getRacesParticipated());
		response.addComment("requests made: " + user.getRequests());
		response.addComment("requests filled: " + user.getRequestsFilled());
		response.addComment(
			"nuked "
				+ user.getTimesNuked()
				+ " times for "
				+ user.getNukedBytes()
				+ " bytes");
		response.addComment("        FILES		BYTES");
		response.addComment(
			"ALUP   "
				+ user.getUploadedFiles()
				+ "	"
				+ Bytes.formatBytes(user.getUploadedBytes()));
		response.addComment(
			"ALDN   "
				+ user.getDownloadedFiles()
				+ "	"
				+ Bytes.formatBytes(user.getDownloadedBytes()));
		response.addComment(
			"MNUP   "
				+ user.getUploadedFilesMonth()
				+ "	"
				+ Bytes.formatBytes(user.getUploadedBytesMonth()));
		response.addComment(
			"MNDN   "
				+ user.getDownloadedFilesMonth()
				+ "	"
				+ Bytes.formatBytes(user.getDownloadedBytesMonth()));
		response.addComment(
			"WKUP   "
				+ user.getUploadedFilesWeek()
				+ "	"
				+ Bytes.formatBytes(user.getUploadedBytesWeek()));
		response.addComment(
			"WKDN   "
				+ user.getDownloadedFilesWeek()
				+ "	"
				+ Bytes.formatBytes(user.getDownloadedBytesWeek()));
		return response;
	}
	public FtpReply execute(BaseFtpConnection conn)
		throws UnhandledCommandException {
		FtpRequest request = conn.getRequest();
		if (request.getCommand().equals("SITE STATS")) {
			return doSITE_STATS(conn);
		}
		List users;
		try {
			users = conn.getUserManager().getAllUsers();
		} catch (UserFileException e) {
			logger.warn("", e);
			return new FtpReply(200, "IO error: " + e.getMessage());
		}
		int count = 10; // default # of users to list
		request = conn.getRequest();
		if (request.hasArgument()) {
			StringTokenizer st = new StringTokenizer(request.getArgument());

			try {
				count = Integer.parseInt(st.nextToken());
			} catch (NumberFormatException ex) {
				st = new StringTokenizer(request.getArgument());
			}

			if (st.hasMoreTokens()) {
				Permission perm = new Permission(FtpConfig.makeUsers(st));
				for (Iterator iter = users.iterator(); iter.hasNext();) {
					User user = (User) iter.next();
					if (!perm.check(user))
						iter.remove();
				}
			}
		}
		final String command = request.getCommand();
		Collections.sort(users, new UserComparator(request));

		FtpReply response = new FtpReply(200);
		String type = command.substring("SITE ".length()).toLowerCase();
		try {
			Textoutput.addTextToResponse(response, type + "_header");
		} catch (IOException ioe) {
			logger.warn("Error reading " + type + "_header", ioe);
		}

		int i = 0;
		for (Iterator iter = users.iterator(); iter.hasNext();) {
			if (++i > count)
				break;
			User user = (User) iter.next();
			ReplacerEnvironment env = new ReplacerEnvironment();
			env.add("pos", "" + i);

			env.add(
				"upbytesday",
				Bytes.formatBytes(user.getUploadedBytesDay()));
			env.add("upfilesday", "" + user.getUploadedFilesDay());
			env.add("uprateday", getUpRate(user, Trial.PERIOD_DAILY));
			env.add(
				"upbytesweek",
				Bytes.formatBytes(user.getUploadedBytesWeek()));
			env.add("upfilesweek", "" + user.getUploadedFilesWeek());
			env.add("uprateweek", getUpRate(user, Trial.PERIOD_WEEKLY));
			env.add(
				"upbytesmonth",
				Bytes.formatBytes(user.getUploadedBytesMonth()));
			env.add("upfilesmonth", "" + user.getUploadedFilesMonth());
			env.add("upratemonth", getUpRate(user, Trial.PERIOD_MONTHLY));
			env.add("upbytes", Bytes.formatBytes(user.getUploadedBytes()));
			env.add("upfiles", "" + user.getUploadedFiles());
			env.add("uprate", getUpRate(user, Trial.PERIOD_ALL));

			env.add(
				"dnbytesday",
				Bytes.formatBytes(user.getDownloadedBytesDay()));
			env.add("dnfilesday", "" + user.getDownloadedFilesDay());
			env.add("dnrateday", getDownRate(user, Trial.PERIOD_DAILY));
			env.add(
				"dnbytesweek",
				Bytes.formatBytes(user.getDownloadedBytesWeek()));
			env.add("dnfilesweek", "" + user.getDownloadedFilesWeek());
			env.add("dnrateweek", getDownRate(user, Trial.PERIOD_WEEKLY));
			env.add(
				"dnbytesmonth",
				Bytes.formatBytes(user.getDownloadedBytesMonth()));
			env.add("dnfilesmonth", "" + user.getDownloadedFilesMonth());
			env.add("dnratemonth", getDownRate(user, Trial.PERIOD_MONTHLY));
			env.add("dnbytes", Bytes.formatBytes(user.getDownloadedBytes()));
			env.add("dnfiles", "" + user.getDownloadedFiles());
			env.add("dnrate", getDownRate(user, Trial.PERIOD_ALL));

			response.addComment(
				BaseFtpConnection.jprintf(
					TransferStatistics.class.getName(),
					"transferstatistics" + type,
					env,
					user));
			//			response.addComment(
			//	user.getUsername()
			//		+ " "
			//		+ Bytes.formatBytes(
			//			getStats(command.substring("SITE ".length()), user)));
		}
		try {
			Textoutput.addTextToResponse(response, type + "_footer");
		} catch (IOException ioe) {
			logger.warn("Error reading " + type + "_footer", ioe);
		}

		return response;
	}

	private String getUpRate(User user, int period) {
		double s =
			user.getUploadedMillisecondsForPeriod(period) / (double) 1000.0;
		if (s <= 0) {
			return "- k/s";
		}

		double rate = user.getUploadedBytesForPeriod(period) / s;
		return Bytes.formatBytes((long) rate) + "/s";
	}

	private String getDownRate(User user, int period) {
		double s =
			user.getDownloadedMillisecondsForPeriod(period) / (double) 1000.0;
		if (s <= 0) {
			return "- k/s";
		}

		double rate = user.getDownloadedBytesForPeriod(period) / s;
		return Bytes.formatBytes((long) rate) + "/s";
	}

	public String[] getFeatReplies() {
		return null;
	}

	public CommandHandler initialize(
		BaseFtpConnection conn,
		CommandManager initializer) {
		return this;
	}
	public void load(CommandManagerFactory initializer) {
	}

	public void unload() {
	}

}
class UserComparator implements Comparator {
	private String _command;
	private FtpRequest _request;
	public UserComparator(FtpRequest request) {
		_request = request;
		_command = _request.getCommand();
	}

	public int compare(Object o1, Object o2) {
		User u1 = (User) o1;
		User u2 = (User) o2;

		long thisVal =
			TransferStatistics.getStats(
				_command.substring("SITE ".length()),
				u1);
		long anotherVal =
			TransferStatistics.getStats(
				_command.substring("SITE ".length()),
				u2);
		return (thisVal > anotherVal ? -1 : (thisVal == anotherVal ? 0 : 1));
	}
}

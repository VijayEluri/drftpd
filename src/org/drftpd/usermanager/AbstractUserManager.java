/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * DrFTPD is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * DrFTPD; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */
package org.drftpd.usermanager;

import net.sf.drftpd.DuplicateElementException;
import net.sf.drftpd.FileExistsException;
import net.sf.drftpd.master.ConnectionManager;

import org.drftpd.commands.UserManagment;

import java.io.IOException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;


/**
 * This is the base class of all the user manager classes. If we want to add a
 * new user manager, we have to override this class.
 *
 * @author <a href="mailto:rana_b@yahoo.com">Rana Bhattacharyya </a>
 * @version $Id: AbstractUserManager.java,v 1.2 2004/11/08 18:39:32 mog Exp $
 */
public abstract class AbstractUserManager implements UserManager {
    protected ConnectionManager _connManager;
    protected Hashtable _users;

    public AbstractUserManager() {
        _users = new Hashtable();
    }

    protected void createSiteopUser() throws UserFileException {
        User user = createUser("drftpd");
        user.setGroup("drftpd");
        user.setPassword("drftpd");
        user.putObject(UserManagment.RATIO, new Float(0));

        try {
            user.addIPMask("*@127.0.0.1");
            user.addIPMask("*@0:0:0:0:0:0:0:1");
        } catch (DuplicateElementException e) {
        }

        try {
            user.addSecondaryGroup("siteop");
        } catch (DuplicateElementException e1) {
        }

        user.commit();
    }

    public User create(String username) throws UserFileException {
        try {
            getUserByName(username);

            //bad
            throw new FileExistsException("User " + username +
                " already exists");
        } catch (IOException e) {
            //bad
            throw new UserFileException(e);
        } catch (NoSuchUserException e) {
            //good
        }

        //User user = _connManager.getGlobalContext().getUserManager().createUser(username);
        User user = createUser(username);
        user.commit();

        return user;
    }

    protected abstract User createUser(String username);

    protected abstract void delete(String string);

    public Collection getAllGroups() throws UserFileException {
        Collection users = getAllUsers();
        ArrayList ret = new ArrayList();

        for (Iterator iter = users.iterator(); iter.hasNext();) {
            User myUser = (User) iter.next();
            Collection myGroups = myUser.getGroups();

            for (Iterator iterator = myGroups.iterator(); iterator.hasNext();) {
                String myGroup = (String) iterator.next();

                if (!ret.contains(myGroup)) {
                    ret.add(myGroup);
                }
            }

            if (!ret.contains(myUser.getGroupName())) {
                ret.add(myUser.getGroupName());
            }
        }

        return ret;
    }

    /**
     * Get all user names in the system.
     */
    public abstract Collection getAllUsers() throws UserFileException;

    public Collection getAllUsersByGroup(String group)
        throws UserFileException {
        Collection c = new ArrayList();

        for (Iterator iter = getAllUsers().iterator(); iter.hasNext();) {
            User user = (User) iter.next();

            if (user.isMemberOf(group)) {
                c.add(user);
            }
        }

        return c;
    }

    //TODO garbage collected Map of users.
    public User getUserByName(String username)
        throws NoSuchUserException, UserFileException {
        User user = getUserByNameUnchecked(username);

        if (user.isDeleted()) {
            throw new NoSuchUserException(user.getUsername() + " is deleted");
        }

        user.reset(_connManager);

        return user;
    }

    public abstract User getUserByNameUnchecked(String username)
        throws NoSuchUserException, UserFileException;

    /**
     * A kind of constuctor defined in the interface for allowing the
     * usermanager to get a hold of the ConnectionManager object for dispatching
     * events etc.
     */
    public void init(ConnectionManager mgr) {
        _connManager = mgr;
    }

    public void remove(User user) {
        _users.remove(user.getUsername());
    }

    protected void rename(User oldUser, String newUsername)
        throws UserExistsException, UserFileException {
        if (!_users.contains(newUsername)) {
            try {
                getUserByNameUnchecked(newUsername);
            } catch (NoSuchUserException e) {
                _users.remove(oldUser.getUsername());
                _users.put(newUsername, oldUser);

                return;
            }
        }

        throw new UserExistsException("user " + newUsername + " exists");
    }

    public abstract void saveAll() throws UserFileException;
}

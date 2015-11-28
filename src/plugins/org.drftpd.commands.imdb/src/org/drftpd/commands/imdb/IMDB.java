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
package org.drftpd.commands.imdb;

import java.io.FileNotFoundException;
import java.util.Map;
import java.util.ResourceBundle;

import org.drftpd.commandmanager.CommandInterface;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.ImproperUsageException;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.protocol.imdb.common.IMDBInfo;
import org.drftpd.sections.SectionInterface;
import org.drftpd.usermanager.User;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;
import org.drftpd.GlobalContext;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.UserFileException;
import org.apache.log4j.Logger;
import org.drftpd.vfs.VirtualFileSystem;
import org.drftpd.vfs.index.IndexException;
import org.tanesha.replacer.ReplacerEnvironment;

/**
 * @author scitz0
 */
public class IMDB extends CommandInterface {
	private static final Logger logger = Logger.getLogger(IMDBPostHook.class);

	private ResourceBundle _bundle;
	private String _keyPrefix;

	public void initialize(String method, String pluginName, StandardCommandManager cManager) {
		super.initialize(method, pluginName, cManager);
		_bundle = cManager.getResourceBundle();
		_keyPrefix = this.getClass().getName()+".";
	}

	public CommandResponse doSITE_IMDB(CommandRequest request) throws ImproperUsageException {
		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}

		String searchstring = request.getArgument().trim();
		searchstring = IMDBUtils.filterTitle(searchstring);

		IMDBParser imdb = new IMDBParser();
		imdb.doSEARCH(searchstring);
		
		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		if (imdb.foundMovie()) {
			response.addComment(request.getSession().jprintf(_bundle, _keyPrefix+"announce", imdb.getEnv(), request.getUser()));
		} else {
			response.addComment(request.getSession().jprintf(_bundle, _keyPrefix+"notfound", imdb.getEnv(), request.getUser()));
		}	
		return response;
	}

	public CommandResponse doSITE_CREATEIMDB(CommandRequest request) throws ImproperUsageException {
		DirectoryHandle dir = request.getCurrentDirectory();
		if (request.hasArgument()) {
			try {
				dir = GlobalContext.getGlobalContext().getRoot().
						getDirectory(request.getArgument(), request.getUserObject());
			} catch (Exception e) {
				return new CommandResponse(500, "Failed getting path, invalid or no permission!");
			}
		}

		ReplacerEnvironment env = new ReplacerEnvironment();
		env.add("dirname", dir.getName());
		env.add("dirpath", dir.getPath());

		Map<String,String> nfoFiles;
		try {
			nfoFiles = IMDBUtils.getNFOFiles(dir);
		} catch (IndexException e) {
			return new CommandResponse(500, "Index Exception: "+e.getMessage());
		}
		
		if (nfoFiles.isEmpty()) {
			return new CommandResponse(500, "No nfo files found, aborting");
		}

		User user;
		try {
			user = GlobalContext.getGlobalContext().getUserManager().getUserByNameUnchecked(request.getUser());
		} catch (NoSuchUserException e) {
			return new CommandResponse(500, "Couldn't find user: " + e.getMessage());
		} catch (UserFileException e) {
			return new CommandResponse(500, "User file corrupt?: "+e.getMessage());
		}

		request.getSession().printOutput(200, request.getSession().jprintf(_bundle, _keyPrefix +
				"createimdb.start", env, request.getUser()));

		for (Map.Entry<String,String> item : nfoFiles.entrySet()) {
			try {
				FileHandle nfo = new FileHandle(VirtualFileSystem.fixPath(item.getKey()));
				if (!nfo.isHidden(user)) {
					// Check if valid section
					SectionInterface sec = GlobalContext.getGlobalContext().getSectionManager().lookup(nfo.getParent());
					if (IMDBConfig.getInstance().getSections().contains(sec.getName().toLowerCase())) {
						DirectoryHandle parent = nfo.getParent();
						IMDBInfo imdbInfo = IMDBUtils.getIMDBInfo(parent, false);
						if (imdbInfo == null || imdbInfo.getMovieFound()) {
							continue;
						}
						IMDBUtils.populateIMDBInfo(imdbInfo);
						IMDBUtils.addMetadata(imdbInfo, parent);
						env.add("dirname", parent.getName());
						env.add("dirpath", parent.getPath());
						env.add("filename", nfo.getName());
						env.add("filepath", nfo.getPath());
						request.getSession().printOutput(200, request.getSession().jprintf(_bundle, _keyPrefix +
								"createimdb.add", env, request.getUser()));
						try {
							// Sleep for randomly generated seconds specified in conf
							Thread.sleep(IMDBUtils.randomNumber());
						} catch (InterruptedException ie) {
							// Thread interrupted
						}
					}
				}
				if (request.getSession().isAborted()) { break; }
			} catch (FileNotFoundException e) {
				logger.warn("Index contained an unexistent inode: " + item.getKey());
			}
		}

		env.add("dirname", dir.getName());
		env.add("dirpath", dir.getPath());

		if (request.getSession().isAborted()) {
			return new CommandResponse(200, request.getSession().jprintf(_bundle, _keyPrefix+"createimdb.aborted", env, request.getUser()));
		} else {
			return new CommandResponse(200, request.getSession().jprintf(_bundle, _keyPrefix+"createimdb.complete", env, request.getUser()));
		}
	}

	public CommandResponse doSITE_REMOVEIMDB(CommandRequest request) throws ImproperUsageException {
		DirectoryHandle dir = request.getCurrentDirectory();
		if (request.hasArgument()) {
			try {
				dir = GlobalContext.getGlobalContext().getRoot().
						getDirectory(request.getArgument(), request.getUserObject());
			} catch (Exception e) {
				return new CommandResponse(500, "Failed getting path, invalid or no permission!");
			}
		}
		ReplacerEnvironment env = new ReplacerEnvironment();
		env.add("dirname", dir.getName());
		env.add("dirpath", dir.getPath());

		Map<String,String> nfoFiles;
		try {
			nfoFiles = IMDBUtils.getNFOFiles(dir);
		} catch (IndexException e) {
			return new CommandResponse(500, "Index Exception: "+e.getMessage());
		}

		if (nfoFiles.isEmpty()) {
			return new CommandResponse(500, "No nfo files found, aborting");
		}

		User user;
		try {
			user = GlobalContext.getGlobalContext().getUserManager().getUserByNameUnchecked(request.getUser());
		} catch (NoSuchUserException e) {
			return new CommandResponse(500, "Couldn't find user: " + e.getMessage());
		} catch (UserFileException e) {
			return new CommandResponse(500, "User file corrupt?: "+e.getMessage());
		}

		request.getSession().printOutput(200, request.getSession().jprintf(_bundle, _keyPrefix+"removeimdb.start", env, request.getUser()));

		for (Map.Entry<String,String> item : nfoFiles.entrySet()) {
			try {
				FileHandle nfo = new FileHandle(VirtualFileSystem.fixPath(item.getKey()));
				if (!nfo.isHidden(user)) {
					try {
						if (nfo.getParent().removePluginMetaData(IMDBInfo.IMDBINFO) != null) {
							ReplacerEnvironment env2 = new ReplacerEnvironment();
							env2.add("dirname", nfo.getParent().getName());
							env2.add("dirpath", nfo.getParent().getPath());
							request.getSession().printOutput(200, request.getSession().jprintf(_bundle, _keyPrefix+"removeimdb.remove", env2, request.getUser()));
						}
					} catch(FileNotFoundException e) {
						// No inode to remove imdb info from
					}
				}
				if (request.getSession().isAborted()) { break; }
			} catch (FileNotFoundException e) {
				logger.warn("Index contained an unexistent inode: " + item.getKey());
			}
		}

		if (request.getSession().isAborted()) {
			return new CommandResponse(200, request.getSession().jprintf(_bundle, _keyPrefix+"removeimdb.aborted", env, request.getUser()));
		} else {
			return new CommandResponse(200, request.getSession().jprintf(_bundle, _keyPrefix+"removeimdb.complete", env, request.getUser()));
		}
	}
}

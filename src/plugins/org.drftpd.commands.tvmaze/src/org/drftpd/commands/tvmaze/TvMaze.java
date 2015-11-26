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
package org.drftpd.commands.tvmaze;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.ResourceBundle;
import java.io.FileNotFoundException;

import org.apache.log4j.Logger;
import org.drftpd.GlobalContext;
import org.drftpd.commands.tvmaze.metadata.TvEpisode;
import org.drftpd.plugins.sitebot.SiteBot;
import org.drftpd.sections.SectionInterface;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.UserFileException;
import org.drftpd.commands.tvmaze.metadata.TvMazeInfo;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.commandmanager.CommandInterface;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.ImproperUsageException;
import org.drftpd.commandmanager.StandardCommandManager;
import org.tanesha.replacer.ReplacerEnvironment;

/**
 * @author lh
 */
public class TvMaze extends CommandInterface {
	private static final Logger logger = Logger.getLogger(TvMaze.class);
	private ResourceBundle _bundle;
	private String _keyPrefix;

	public void initialize(String method, String pluginName, StandardCommandManager cManager) {
		super.initialize(method, pluginName, cManager);
		_bundle = cManager.getResourceBundle();
		_keyPrefix = this.getClass().getName()+".";
	}

	public CommandResponse doSITE_TV(CommandRequest request) throws ImproperUsageException {
		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}

		String searchstring = request.getArgument().trim();

		boolean verbose = false;
		if (searchstring.toLowerCase().startsWith("-v")) {
			verbose = true;
			searchstring = searchstring.substring(2).trim();
		}

		TvMazeParser tvmaze = new TvMazeParser();
		tvmaze.doTV(searchstring);
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
		
		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		if (tvmaze.getTvShow() == null) {
			env.add("searchstr", searchstring);
			env.add("error", tvmaze.getError());
			response.addComment(request.getSession().jprintf(_bundle, _keyPrefix+"tv.none", env, request.getUser()));
		} else {
			env = TvMazeUtils.getShowEnv(tvmaze.getTvShow());
			if (tvmaze.getTvShow().getEPList().length == 0) {
				if (tvmaze.getTvShow().getPreviousEP() != null) {
					addTagToEnvironment(env, request, "prevep", "tv.prevep", verbose);
				}
				if (tvmaze.getTvShow().getNextEP() != null) {
					addTagToEnvironment(env, request, "nextep", "tv.nextep", verbose);
				}
				addResponse(env, request, response, "tv.show", verbose);
			} else {
				ArrayList<TvEpisode> epList = new ArrayList<TvEpisode>(Arrays.asList(tvmaze.getTvShow().getEPList()));
				if (epList.size() > 1) {
					Collections.sort(epList, TvMazeUtils.epNumberComparator);
					addResponse(env, request, response, "tv.ep.season.header", verbose);
					for (TvEpisode ep : epList) {
						env = TvMazeUtils.getEPEnv(tvmaze.getTvShow(), ep);
						addResponse(env, request, response, "tv.ep.season", verbose);
					}
				} else if (epList.size() == 1) {
					TvEpisode evEP = epList.get(0);
					env = TvMazeUtils.getEPEnv(tvmaze.getTvShow(), evEP);

					if (TvMazeConfig.getInstance().searchRelease()) {
						env.add("foundSD","No");
						env.add("foundHD","No");

						String season = String.format("%02d", evEP.getSeason());
						String number = String.format("%02d", evEP.getNumber());
						String searchpattern = "(?i).*" + tvmaze.getTvShow().getName().replaceAll("\\s", "\\.") +
								"\\.(s"+season+"\\.?(e"+number+"|e\\d+-e"+number+")|"+evEP.getSeason()+"x"+number+")\\..*";

						ArrayList<DirectoryHandle> results = new ArrayList<DirectoryHandle>();

						try {
							for (String section : TvMazeConfig.getInstance().getHDSections()) {
								results.addAll(TvMazeUtils.findReleases(
										GlobalContext.getGlobalContext().getSectionManager().getSection(section).getCurrentDirectory(),
										request.getSession().getUserNull(request.getUser()), searchpattern));
							}
							for (String section : TvMazeConfig.getInstance().getSDSections()) {
								results.addAll(TvMazeUtils.findReleases(
										GlobalContext.getGlobalContext().getSectionManager().getSection(section).getCurrentDirectory(),
										request.getSession().getUserNull(request.getUser()), searchpattern));
							}
							for (DirectoryHandle dir : results) {
								SectionInterface sec = GlobalContext.getGlobalContext().getSectionManager().lookup(dir);
								if (TvMazeConfig.getInstance().getHDSections().contains(sec.getName())) {
									env.add("foundHD","Yes");
								}
								if (TvMazeConfig.getInstance().getSDSections().contains(sec.getName())) {
									env.add("foundSD","Yes");
								}
							}
							env.add("results", results.size());
							addTagToEnvironment(env, request, "release", "tv.ep.release", verbose);
						} catch (Exception e) {
							logger.error(e.getMessage(), e);
						}
					}
					addResponse(env, request, response, "tv.ep", verbose);
				}
			}
		}

		return response;
	}

	private void addTagToEnvironment(ReplacerEnvironment env, CommandRequest request, String tag, String key, boolean verbose) {
		if (verbose) {
			env.add(tag, request.getSession().jprintf(_bundle, _keyPrefix+key+".verbose", env, request.getUser()));
		} else {
			env.add(tag, request.getSession().jprintf(_bundle, _keyPrefix+key, env, request.getUser()));
		}
	}

	private void addResponse(ReplacerEnvironment env, CommandRequest request, CommandResponse response, String key, boolean verbose) {
		if (verbose) {
			response.addComment(request.getSession().jprintf(_bundle, _keyPrefix+key+".verbose", env, request.getUser()));
		} else {
			response.addComment(request.getSession().jprintf(_bundle, _keyPrefix+key, env, request.getUser()));
		}
	}

	public CommandResponse doSITE_CREATETV(CommandRequest request) throws ImproperUsageException {
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
		ArrayList<DirectoryHandle> dirsToCheck = getDirsToCheck(request, dir);
		if (dirsToCheck.isEmpty()) {
			return new CommandResponse(500, "Not a valid section, aborting");
		}
		request.getSession().printOutput(200, request.getSession().jprintf(_bundle, _keyPrefix+"createtvmaze.start", env, request.getUser()));
		for (DirectoryHandle dirToCheck : dirsToCheck) {
			parseTvMazeDirs(dirToCheck, request);
		}
		if (request.getSession().isAborted()) {
			return new CommandResponse(200, request.getSession().jprintf(_bundle, _keyPrefix+"createtvmaze.aborted", env, request.getUser()));
		} else {
			return new CommandResponse(200, request.getSession().jprintf(_bundle, _keyPrefix+"createtvmaze.complete", env, request.getUser()));
		}
	}

	private void parseTvMazeDirs(DirectoryHandle dir, CommandRequest request) {
		if (request.getSession().isAborted()) { return; }
		if (TvMazeUtils.isRelease(dir.getName())) {
			TvMazeInfo tvmazeInfo = TvMazeUtils.getTvMazeInfo(dir);
			if (tvmazeInfo != null) {
				ReplacerEnvironment env = TvMazeUtils.getShowEnv(tvmazeInfo);
				env.add("dirname", dir.getName());
				env.add("dirpath", dir.getPath());
				request.getSession().printOutput(200, request.getSession().jprintf(_bundle, _keyPrefix + "createtvmaze.add", env, request.getUser()));
				try {
					// Sleep for randomly generated seconds specified in conf
					Thread.sleep(TvMazeUtils.randomNumber());
				} catch (InterruptedException ie) {
					// Thread interrupted
				}
			}
		}
		if (request.getSession().isAborted()) { return; }
		try {
			for(DirectoryHandle subdir : dir.getDirectories(request.getUserObject())) {
				parseTvMazeDirs(subdir, request);
			}
		} catch (FileNotFoundException e) {
			// Just continue
		} catch (NoSuchUserException e) {
			logger.error("",e);
		}  catch (UserFileException e) {
			logger.error("",e);
		}
	}

	public CommandResponse doSITE_REMOVETV(CommandRequest request) throws ImproperUsageException {
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
		ArrayList<DirectoryHandle> dirsToCheck = getDirsToCheck(request, dir);
		if (dirsToCheck.isEmpty()) {
			return new CommandResponse(500, "Not a valid section, aborting");
		}
		request.getSession().printOutput(200, request.getSession().jprintf(_bundle, _keyPrefix+"removetvmaze.start", env, request.getUser()));
		for (DirectoryHandle dirToCheck : dirsToCheck) {
			removeMetaDataRecursive(dirToCheck, request);
		}
		if (request.getSession().isAborted()) {
			return new CommandResponse(200, request.getSession().jprintf(_bundle, _keyPrefix+"removetvmaze.aborted", env, request.getUser()));
		} else {
			return new CommandResponse(200, request.getSession().jprintf(_bundle, _keyPrefix+"removetvmaze.complete", env, request.getUser()));
		}
	}

	private void removeMetaDataRecursive(DirectoryHandle dir, CommandRequest request) {
		if (request.getSession().isAborted()) { return; }
		try {
			if (dir.removePluginMetaData(TvMazeInfo.TVMAZEINFO) != null) {
				ReplacerEnvironment env = new ReplacerEnvironment();
				env.add("dirname", dir.getName());
				env.add("dirpath", dir.getPath());
				request.getSession().printOutput(200, request.getSession().jprintf(_bundle, _keyPrefix+"removetvmaze.remove", env, request.getUser()));
			}
		} catch(FileNotFoundException e) {
			// No inode to remove tvmaze info from
		}
		try {
			for(DirectoryHandle subdir : dir.getDirectories(request.getUserObject())) {
				removeMetaDataRecursive(subdir, request);
			}
		} catch (FileNotFoundException e) {
			// Just continue
		} catch (NoSuchUserException e) {
			logger.error("",e);
		}  catch (UserFileException e) {
			logger.error("",e);
		}
	}

	private ArrayList<DirectoryHandle> getDirsToCheck(CommandRequest request, DirectoryHandle dir) {
		ArrayList<DirectoryHandle> dirsToCheck = new ArrayList<DirectoryHandle>();
		ArrayList<String> joinedSectionList = TvMazeConfig.getInstance().getRaceSections();
		for (String section : TvMazeConfig.getInstance().getHDSections()) {
			if (!joinedSectionList.contains(section)) {
				joinedSectionList.add(section);
			}
		}
		for (String section : TvMazeConfig.getInstance().getSDSections()) {
			if (!joinedSectionList.contains(section)) {
				joinedSectionList.add(section);
			}
		}
		if (dir.isRoot()) {
			for (String section : joinedSectionList) {
				try {
					dirsToCheck.add(dir.getDirectory(section, request.getUserObject()));
				} catch (Exception e) {
					logger.warn("Failed getting DirectoryHandle for section " + section);
				}
			}
		} else {
			SectionInterface sec = GlobalContext.getGlobalContext().getSectionManager().lookup(dir);
			if (joinedSectionList.contains(sec.getName().toLowerCase()))
				dirsToCheck.add(dir);
		}
		return dirsToCheck;
	}
}

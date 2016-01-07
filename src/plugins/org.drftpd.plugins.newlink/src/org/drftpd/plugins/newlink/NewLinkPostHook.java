package org.drftpd.plugins.newlink;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;

import org.apache.log4j.Logger;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.GlobalContext;
import org.drftpd.TimeComparator;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.PostHookInterface;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.event.ReloadEvent;
import org.drftpd.exceptions.FileExistsException;
import org.drftpd.sections.SectionInterface;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.LinkHandle;
import org.drftpd.vfs.ObjectNotValidException;
import org.drftpd.vfs.VirtualFileSystem;

public class NewLinkPostHook implements PostHookInterface {
	private static final Logger logger = Logger
			.getLogger(NewLinkPostHook.class);

	private String[] _excludeDirs, _excludeSections;
	private String _baseDir;
	private int _maxLinks;
	private boolean _preEnabled;

	@Override
	public void initialize(StandardCommandManager manager) {
		// TODO Auto-generated method stub
		logger.info("Starting NewDir plugin");
		loadConf();
	}

	private void loadConf() {
		// TODO Auto-generated method stub
		Properties cfg = GlobalContext.getGlobalContext().getPluginsConfig()
				.getPropertiesForPlugin("newlink.conf");
		if (cfg == null) {
			logger.fatal("conf/plugins/newlink.conf not found");
			return;
		}
		_excludeDirs = cfg.getProperty("newlinks.exclude").trim().split(";");
		_excludeSections = cfg.getProperty("newlinks.excludesec").trim().split(
				";");
		_baseDir = cfg.getProperty("newlinks.basefolder").trim();
		_preEnabled = Boolean.parseBoolean(cfg.getProperty("newlinks.showpre"));

		try {
			_maxLinks = Integer.parseInt(cfg.getProperty("newlinks.maxlinks")
					.trim());
		} catch (NumberFormatException e) {
			_maxLinks = 1;
			throw new RuntimeException(
					"Unspecified value 'newlinks.maxlinks' in newlink.conf");
		}

		if (_excludeDirs == null) {
			throw new RuntimeException(
					"Unspecified value 'newlinks.exclude' in newlink.conf");
		}
		if (_excludeSections == null) {
			throw new RuntimeException(
					"Unspecified value 'newlinks.excludesec' in newlink.conf");
		}
		if (_baseDir == null) {
			throw new RuntimeException(
					"Unspecified value 'newlinks.basefolder' in newlink.conf");
		}
		if (!_baseDir.endsWith("/")) {
			_baseDir += "/";
		}
	}

	public void createLastRaceLink(CommandRequest request,
			CommandResponse response) {		
		if (response.getCode() != 257) {
			// MKD failed
			return;
		}

		String newDir = request.getArgument();
		String sectionName = null;
		DirectoryHandle targetDir = null;

		if (newDir.indexOf("/") != -1) {
			newDir = newDir.substring(1);
			newDir = newDir.substring(newDir.indexOf("/")+1, newDir.length());
			sectionName = request.getArgument().substring(1);
			sectionName = sectionName.substring(0, sectionName.indexOf("/"));
		} else {
			sectionName = request.getCurrentDirectory().getName();
		}
		
		for (int i = 0; i < _excludeSections.length; i++) {
			if (sectionName.equalsIgnoreCase(_excludeSections[i]))
				return;
		}
		for (int i = 0; i < _excludeDirs.length; i++) {
			if (newDir.toLowerCase().contains(_excludeDirs[i]))
				return;
		}
		
		SectionInterface section = GlobalContext.getGlobalContext()
		.getSectionManager().getSection(sectionName);

		try {
			targetDir = section.getBaseDirectory().getDirectoryUnchecked(
					newDir);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			return;
		} catch (ObjectNotValidException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		createLink(targetDir, sectionName, newDir, false);
	}

	public void createLastPreLink(CommandRequest request,
			CommandResponse response) {
		if (response.getCode() != 250) {
			// PRE failed
			return;
		}

		String newDir = null;
		String sectionName = null;
		DirectoryHandle targetDir = null;

		String[] args = request.getArgument().split(" ");
		newDir = args[0];
		sectionName = args[1];

		SectionInterface section = GlobalContext.getGlobalContext()
				.getSectionManager().getSection(sectionName);
		try {
			targetDir = section.getCurrentDirectory()
					.getDirectoryUnchecked(newDir);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			return;
		} catch (ObjectNotValidException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		createLink(targetDir, sectionName, newDir, true);
	}

	public void createLink(DirectoryHandle targetDir, String sectionName,
			String newDir, boolean isPre) {

		DirectoryHandle newLinkDir = null;
		Set<LinkHandle> links = null;
		ArrayList<LinkHandle> newDirLinks = new ArrayList<LinkHandle>();
		ArrayList<LinkHandle> newPreLinks = new ArrayList<LinkHandle>();
		try {

			if (_baseDir.equals(VirtualFileSystem.separator)) {
				newLinkDir = GlobalContext.getGlobalContext().getRoot();
			} else {
				newLinkDir = GlobalContext.getGlobalContext().getRoot()
						.getDirectoryUnchecked(_baseDir);
			}

			links = newLinkDir.getLinksUnchecked();

			Iterator<LinkHandle> iter = links.iterator();
			while (iter.hasNext()) {
				LinkHandle linkHandle = iter.next();
				if (linkHandle.getName().startsWith("[LastPre]-")) {
					newPreLinks.add(linkHandle);
				} else if (linkHandle.getName().startsWith("[LastRace]-")) {
					newDirLinks.add(linkHandle);
				}
			}

			if (isPre && newPreLinks.size() >= _maxLinks) {
				Collections.sort(newPreLinks, new TimeComparator());
				LinkHandle oldestLink = newPreLinks.get(newPreLinks.size() - 1);
				if (oldestLink.isLink()) {
					try {
						oldestLink.deleteUnchecked();
					} catch (FileNotFoundException e) {
						// TODO Auto-generated catch block
					}
				}
			}

			if (!isPre && newDirLinks.size() >= _maxLinks) {
				Collections.sort(newDirLinks, new TimeComparator());
				LinkHandle oldestLink = newDirLinks.get(newDirLinks.size() - 1);
				if (oldestLink.isLink()) {
					try {
						oldestLink.deleteUnchecked();
					} catch (FileNotFoundException e) {
						// TODO Auto-generated catch block
					}
				}
			}

			if (isPre) {
				newLinkDir.createLinkUnchecked("[LastPre]-[" + sectionName
						+ "]-" + newDir, targetDir.getPath(), "drftpd",
						"drftpd");
			} else {
				newLinkDir.createLinkUnchecked("[LastRace]-[" + sectionName
						+ "]-" + newDir, targetDir.getPath(), "drftpd",
						"drftpd");
			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			logger.error("newlinkerror: " + e.getMessage());
		} catch (ObjectNotValidException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (FileExistsException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@EventSubscriber
	public void onReloadEvent(ReloadEvent event) {
		loadConf();
	}

}

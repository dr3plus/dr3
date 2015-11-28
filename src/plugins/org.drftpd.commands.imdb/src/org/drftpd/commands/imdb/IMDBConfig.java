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

import org.apache.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.GlobalContext;
import org.drftpd.event.ReloadEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;

/**
 * @author scitz0
 */
public class IMDBConfig {
	private static IMDBConfig ourInstance = new IMDBConfig();

	private static final Logger logger = Logger.getLogger(IMDBConfig.class);

	private ArrayList<String> _sections = new ArrayList<String>();
	private int _startDelay, _endDelay;
	private String _filter;
	private boolean _bar_enabled, _bar_directory;

	public static IMDBConfig getInstance() {
		return ourInstance;
	}

	private IMDBConfig() {
		// Subscribe to events
		AnnotationProcessor.process(this);
		loadConfig();
	}

	private void loadConfig() {
		Properties cfg = GlobalContext.getGlobalContext().getPluginsConfig().getPropertiesForPlugin("imdb.conf");
		if (cfg == null) {
			logger.fatal("conf/plugins/imdb.conf not found");
			return;
		}
		_sections.clear();
		_sections.addAll(Arrays.asList(cfg.getProperty("sections", "").toLowerCase().split(";")));
		_startDelay = Integer.parseInt(cfg.getProperty("delay.start","5"));
		_endDelay = Integer.parseInt(cfg.getProperty("delay.end","10"));
		if(_startDelay >= _endDelay) {
			logger.warn("Start delay >= End delay, setting default values 5-10");
			_startDelay = 5;
			_endDelay = 10;
		}
		_filter = cfg.getProperty("imdb.filter","");
		_bar_enabled = cfg.getProperty("imdbbar.enabled", "true").equalsIgnoreCase("true");
		_bar_directory = cfg.getProperty("imdbbar.directory", "true").equalsIgnoreCase("true");
	}

	public ArrayList<String> getSections() {
		return _sections;
	}

	public int getStartDelay() {
		return _startDelay;
	}

	public int getEndDelay() {
		return _endDelay;
	}

	public String getFilter() {
		return _filter;
	}

	public boolean barEnabled() {
		return _bar_enabled;
	}

	public boolean barAsDirectory() {
		return _bar_directory;
	}

	@EventSubscriber
	public void onReloadEvent(ReloadEvent event) {
		loadConfig();
	}
}

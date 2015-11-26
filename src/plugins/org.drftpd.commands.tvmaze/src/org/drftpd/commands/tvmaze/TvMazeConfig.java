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

import org.apache.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.GlobalContext;
import org.drftpd.event.ReloadEvent;
import org.joda.time.DateTimeZone;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;

/**
 * @author scitz0
 */
public class TvMazeConfig {
	private static TvMazeConfig ourInstance = new TvMazeConfig();

	private static final Logger logger = Logger.getLogger(TvMaze.class);

	private ArrayList<String> _rSections = new ArrayList<String>();
	private ArrayList<String> _sHDSections = new ArrayList<String>();
	private ArrayList<String> _sSDSections = new ArrayList<String>();
	private String[] _filters;
	private String _date, _time, _exclude;
	private DateTimeZone _dtz;
	private int _startDelay, _endDelay;
	private boolean _bar_enabled, _bar_directory, _sRelease;

	public static TvMazeConfig getInstance() {
		return ourInstance;
	}

	private TvMazeConfig() {
		// Subscribe to events
		AnnotationProcessor.process(this);
		loadConfig();
	}

	private void loadConfig() {
		Properties cfg = GlobalContext.getGlobalContext().getPluginsConfig().getPropertiesForPlugin("tvmaze.conf");
		if (cfg == null) {
			logger.fatal("conf/plugins/tvmaze.conf not found");
			return;
		}
		_filters = cfg.getProperty("filter","").split(";");
		_date = cfg.getProperty("date.show","yyyy-MM-dd");
		_time = cfg.getProperty("time.show","EEEE, HH:mm");
		_dtz = cfg.getProperty("timezone") == null ? DateTimeZone.getDefault() : DateTimeZone.forID(cfg.getProperty("timezone"));
		_exclude = cfg.getProperty("exclude","");
		_rSections.clear();
		_rSections.addAll(Arrays.asList(cfg.getProperty("race.sections", "").toLowerCase().split(";")));
		_sHDSections.clear();
		_sHDSections.addAll(Arrays.asList(cfg.getProperty("search.hd.section", "").split(";")));
		_sSDSections.clear();
		_sSDSections.addAll(Arrays.asList(cfg.getProperty("search.sd.section", "").split(";")));
		_sRelease = cfg.getProperty("search.release", "false").equalsIgnoreCase("true");
		_startDelay = Integer.parseInt(cfg.getProperty("delay.start","5"));
		_endDelay = Integer.parseInt(cfg.getProperty("delay.end","10"));
		if(_startDelay >= _endDelay) {
			logger.warn("Start delay >= End delay, setting default values 5-10");
			_startDelay = 0;
			_endDelay = 5;
		}
		_bar_enabled = cfg.getProperty("tvmazebar.enabled", "true").equalsIgnoreCase("true");
		_bar_directory = cfg.getProperty("tvmazebar.directory", "true").equalsIgnoreCase("true");
	}

	public String[] getFilters() {
		return _filters;
	}

	public ArrayList<String> getRaceSections() {
		return _rSections;
	}

	public ArrayList<String> getHDSections() {
		return _sHDSections;
	}

	public ArrayList<String> getSDSections() {
		return _sSDSections;
	}

	public boolean searchRelease() {
		return _sRelease;
	}

	public String getDateFormat() {
		return _date;
	}

	public String getTimeFormat() {
		return _time;
	}

	public DateTimeZone getTimezone() {
		return _dtz;
	}

	public String getExclude() {
		return _exclude;
	}

	public int getStartDelay() {
		return _startDelay;
	}

	public int getEndDelay() {
		return _endDelay;
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

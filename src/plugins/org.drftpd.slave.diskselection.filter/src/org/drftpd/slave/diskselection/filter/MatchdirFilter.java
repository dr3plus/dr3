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

package org.drftpd.slave.diskselection.filter;

import java.util.Properties;

import org.apache.oro.text.GlobCompiler;
import org.apache.oro.text.regex.Pattern;
import org.apache.oro.text.regex.Perl5Matcher;
import org.drftpd.PropertyHelper;

/**
 * Sample configuration.
 * 
 * <pre>
 *  X.filter=matchdir
 *  X.assign=&lt;rootNumber&gt;+100000
 *  X.match=&lt;path glob match&gt;
 *  X.assume.remove=&lt;true&gt;
 *  X.negate.expression=&lt;true&gt;
 * </pre>
 * 
 * @author fr0w
 * @version $Id: MatchdirFilter.java 1925 2009-06-15 21:46:05Z tdsoul $
 */
public class MatchdirFilter extends DiskFilter {

	private Pattern _p;

	private Perl5Matcher _m = new Perl5Matcher();

	private boolean _negateExpr;

	public MatchdirFilter(DiskSelectionFilter diskSelection, Properties p, Integer i) {
		super(diskSelection, p, i);
		_assignList = AssignRoot.parseAssign(this, PropertyHelper.getProperty(p, i+ ".assign"));

		boolean assumeRemove = PropertyHelper.getProperty(p, i + ".assume.remove", "false").
				equalsIgnoreCase("true");
		// If assume.remove=true, add all roots not assigned a score to be removed
		if (assumeRemove) {
			int roots = diskSelection.getRootCollection().getRootList().size();
			for (int j = 1; j <= roots; j++) {
				boolean assigned = false;
				for (AssignParser ap : _assignList) {
					if (j == ap.getRoot()) {
						// Score added to root already, skip
						assigned = true;
						break;
					}
				}
				if (!assigned) {
					// Add root to be remove
					_assignList.add(new AssignParser(j+"+remove"));
				}
			}
		}

		_negateExpr = PropertyHelper.getProperty(p, i + ".negate.expression", "false").
				equalsIgnoreCase("true");

		try {
			_p = new GlobCompiler().compile(PropertyHelper.getProperty(p, i + ".match"),
					GlobCompiler.CASE_INSENSITIVE_MASK);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public void process(ScoreChart sc, String path) {
		boolean validPath = _negateExpr ? !_m.matches(path, _p) : _m.matches(path, _p);
		if (validPath) {
			AssignRoot.addScoresToChart(this, _assignList, sc);
		}
	}
	
	public String toString() {
		return getClass().getName()+"[pattern="+_p.getPattern()+",roots="+getAssignList()+"]";
	}
}

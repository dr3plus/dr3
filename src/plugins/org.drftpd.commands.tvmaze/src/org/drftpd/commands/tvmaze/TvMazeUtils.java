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

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.drftpd.GlobalContext;
import org.drftpd.commands.tvmaze.event.TvMazeEvent;
import org.drftpd.commands.tvmaze.metadata.TvEpisode;
import org.drftpd.commands.tvmaze.metadata.TvMazeInfo;
import org.drftpd.commands.tvmaze.vfs.TvMazeVFSData;
import org.drftpd.dynamicdata.KeyNotFoundException;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.exceptions.SlaveUnavailableException;
import org.drftpd.plugins.sitebot.SiteBot;
import org.drftpd.sections.SectionInterface;
import org.drftpd.usermanager.User;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.VirtualFileSystem;
import org.drftpd.vfs.index.AdvancedSearchParams;
import org.drftpd.vfs.index.IndexEngineInterface;
import org.drftpd.vfs.index.IndexException;
import org.drftpd.vfs.index.lucene.extensions.tvmaze.TvMazeQueryParams;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;
import org.tanesha.replacer.ReplacerEnvironment;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author scitz0
 */
public class TvMazeUtils {
	private static final Logger logger = Logger.getLogger(TvMazeUtils.class);

	private static final String[] _seperators = {".","-","_"};

	public static ReplacerEnvironment getShowEnv(TvMazeInfo tvShow) {
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
		DateTimeFormatter df = DateTimeFormat.forPattern(TvMazeConfig.getInstance().getDateFormat());
		DateTimeFormatter tf = DateTimeFormat.forPattern(TvMazeConfig.getInstance().getTimeFormat());

		env.add("id", tvShow.getID());
		env.add("url", tvShow.getURL());
		env.add("name", tvShow.getName());
		env.add("type", tvShow.getType());
		env.add("language", tvShow.getLanguage());
		env.add("genres", StringUtils.join(tvShow.getGenres(), " | "));
		env.add("status", tvShow.getStatus());
		env.add("runtime", tvShow.getRuntime());
		DateTimeFormatter dtf = DateTimeFormat.forPattern("yyyy-MM-dd");
		env.add("premiered", df.withZone(TvMazeConfig.getInstance().getTimezone()).print(dtf.parseDateTime(tvShow.getPremiered())));
		env.add("network", tvShow.getNetwork());
		env.add("country", tvShow.getCountry());
		env.add("summary", StringUtils.abbreviate(tvShow.getSummary(), 250));

		if (tvShow.getPreviousEP() != null) {
			env.add("prevepid", tvShow.getPreviousEP().getID());
			env.add("prevepurl", tvShow.getPreviousEP().getURL());
			env.add("prevepname", tvShow.getPreviousEP().getName());
			env.add("prevepseason", tvShow.getPreviousEP().getSeason());
			env.add("prevepnumber", String.format("%02d", tvShow.getPreviousEP().getNumber()));
			env.add("prevepairdate", df.withZone(TvMazeConfig.getInstance().getTimezone()).print(new DateTime(tvShow.getPreviousEP().getAirDate())));
			env.add("prevepairtime", tf.withZone(TvMazeConfig.getInstance().getTimezone()).print(new DateTime(tvShow.getPreviousEP().getAirDate())));
			env.add("prevepruntime", tvShow.getPreviousEP().getRuntime());
			env.add("prevepsummary", StringUtils.abbreviate(tvShow.getPreviousEP().getSummary(), 250));
			env.add("prevepage", calculateAge(new DateTime(tvShow.getPreviousEP().getAirDate())));
		}
		if (tvShow.getNextEP() != null) {
			env.add("nextepid", tvShow.getNextEP().getID());
			env.add("nextepurl", tvShow.getNextEP().getURL());
			env.add("nextepname", tvShow.getNextEP().getName());
			env.add("nextepseason", tvShow.getNextEP().getSeason());
			env.add("nextepnumber", String.format("%02d", tvShow.getNextEP().getNumber()));
			env.add("nextepairdate", df.withZone(TvMazeConfig.getInstance().getTimezone()).print(new DateTime(tvShow.getNextEP().getAirDate())));
			env.add("nextepairtime", tf.withZone(TvMazeConfig.getInstance().getTimezone()).print(new DateTime(tvShow.getNextEP().getAirDate())));
			env.add("nextepruntime", tvShow.getNextEP().getRuntime());
			env.add("nextepsummary", StringUtils.abbreviate(tvShow.getNextEP().getSummary(), 250));
			env.add("nextepage", calculateAge(new DateTime(tvShow.getNextEP().getAirDate())));
		}

		return env;
	}

	public static ReplacerEnvironment getEPEnv(TvMazeInfo tvShow, TvEpisode tvEP) {
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
		DateTimeFormatter df = DateTimeFormat.forPattern(TvMazeConfig.getInstance().getDateFormat());
		DateTimeFormatter tf = DateTimeFormat.forPattern(TvMazeConfig.getInstance().getTimeFormat());

		env.add("id", tvShow.getID());
		env.add("url", tvShow.getURL());
		env.add("name", tvShow.getName());
		env.add("type", tvShow.getType());
		env.add("language", tvShow.getLanguage());
		env.add("genres", StringUtils.join(tvShow.getGenres(), " | "));
		env.add("status", tvShow.getStatus());
		env.add("runtime", tvShow.getRuntime());
		DateTimeFormatter dtf = DateTimeFormat.forPattern("yyyy-MM-dd");
		env.add("premiered", df.withZone(TvMazeConfig.getInstance().getTimezone()).print(dtf.parseDateTime(tvShow.getPremiered())));
		env.add("network", tvShow.getNetwork());
		env.add("country", tvShow.getCountry());
		env.add("summary", StringUtils.abbreviate(tvShow.getSummary(), 250));

		env.add("epid", tvEP.getID());
		env.add("epurl", tvEP.getURL());
		env.add("epname", tvEP.getName());
		env.add("epseason", tvEP.getSeason());
		env.add("epnumber", String.format("%02d", tvEP.getNumber()));
		env.add("epairdate", df.withZone(TvMazeConfig.getInstance().getTimezone()).print(new DateTime(tvEP.getAirDate())));
		env.add("epairtime", tf.withZone(TvMazeConfig.getInstance().getTimezone()).print(new DateTime(tvEP.getAirDate())));
		env.add("epruntime", tvEP.getRuntime());
		env.add("epsummary", StringUtils.abbreviate(tvEP.getSummary(), 250));
		env.add("epage", calculateAge(new DateTime(tvEP.getAirDate())));

		return env;
	}

	public static TvMazeInfo createTvMazeInfo(JsonObject jObj) throws Exception {
		TvMazeInfo tvmazeInfo = new TvMazeInfo();

		tvmazeInfo.setID(jObj.get("id").getAsInt());
		tvmazeInfo.setURL(jObj.get("url").getAsString());
		tvmazeInfo.setName(jObj.get("name").getAsString());
		tvmazeInfo.setType(jObj.get("type").getAsString());
		tvmazeInfo.setLanguage(jObj.get("language").getAsString());
		tvmazeInfo.setGenres((String[])new Gson().fromJson(jObj.getAsJsonArray("genres"), new TypeToken<String[]>() {}.getType()));
		tvmazeInfo.setStatus(jObj.get("status").getAsString());
		tvmazeInfo.setRuntime(jObj.get("runtime").getAsInt());
		tvmazeInfo.setPremiered(jObj.get("premiered").getAsString());
		JsonObject networkJsonObj = null;
		if (jObj.get("network").isJsonObject()) {
			networkJsonObj = jObj.getAsJsonObject("network");
		} else if (jObj.get("webChannel").isJsonObject()) {
			networkJsonObj = jObj.getAsJsonObject("webChannel");
		}
		if (networkJsonObj != null) {
			tvmazeInfo.setNetwork(networkJsonObj.get("name").getAsString());
			JsonObject countryJsonObj = networkJsonObj.getAsJsonObject("country");
			tvmazeInfo.setCountry(countryJsonObj.get("name").getAsString());
		}
		tvmazeInfo.setSummary(TvMazeUtils.htmlToString(jObj.get("summary").getAsString()));
		JsonObject linksObj = jObj.getAsJsonObject("_links");
		if (linksObj != null) {
			JsonObject prevEPObj = linksObj.getAsJsonObject("previousepisode");
			if (prevEPObj != null) {
				// Fetch and parse EP
				String epURL = prevEPObj.get("href").getAsString();
				tvmazeInfo.setPreviousEP(createTvEpisode(fetchEpisodeData(epURL)));
			}
			JsonObject nextEPObj = linksObj.getAsJsonObject("nextepisode");
			if (nextEPObj != null) {
				// Fetch and parse EP
				String epURL = nextEPObj.get("href").getAsString();
				tvmazeInfo.setNextEP(createTvEpisode(fetchEpisodeData(epURL)));
			}
		}

		return tvmazeInfo;
	}

	public static TvMazeInfo createTvMazeInfo(JsonObject jObj, int season, int number) throws Exception{
		TvMazeInfo tvmazeInfo = createTvMazeInfo(jObj);
		ArrayList<TvEpisode> epList = new ArrayList<TvEpisode>();
		JsonObject embeddedObj = jObj.getAsJsonObject("_embedded");
		if (embeddedObj != null) {
			// Add all episodes to a map with sXXeYY as key
			HashMap<String,TvEpisode> episodes = parseEpisodes(embeddedObj);
			if (number >= 0) {
				// Find the single show wanted and add to _epList
				epList.add(episodes.get("s" + season + "e" + number));
			} else if (season >= 0) {
				// All episodes of specified season wanted
				for (TvEpisode ep : episodes.values()) {
					if (ep.getSeason() == season) {
						epList.add(ep);
					}
				}
			}
		}
		tvmazeInfo.setEPList(epList.toArray(new TvEpisode[epList.size()]));

		return tvmazeInfo;
	}

	private static HashMap<String,TvEpisode> parseEpisodes (JsonObject embeddedObj) throws Exception{
		HashMap<String,TvEpisode> episodes = new HashMap<String,TvEpisode>();
		ArrayList<JsonElement> episodesElement = new Gson().fromJson(embeddedObj.getAsJsonArray("episodes"), new TypeToken<ArrayList<JsonElement>>() {}.getType());
		for (JsonElement episode : episodesElement) {
			TvEpisode ep = createTvEpisode(episode.getAsJsonObject());
			episodes.put("s"+ep.getSeason()+"e"+ep.getNumber(), ep);
		}
		return episodes;
	}

	private static JsonObject fetchEpisodeData(String epURL) throws Exception{
		String data = TvMazeUtils.retrieveHttpAsString(epURL);
		JsonParser jp = new JsonParser();
		JsonElement root = jp.parse(data);
		return root.getAsJsonObject();
	}

	public static TvEpisode createTvEpisode(JsonObject jobj) throws Exception {
		TvEpisode tvEP = new TvEpisode();
		tvEP.setID(jobj.get("id").getAsInt());
		tvEP.setURL(jobj.get("url").getAsString());
		tvEP.setName(jobj.get("name").getAsString());
		tvEP.setSeason(jobj.get("season").getAsInt());
		tvEP.setNumber(jobj.get("number").getAsInt());
		tvEP.setAirDate(jobj.get("airstamp").getAsString());
		tvEP.setRuntime(jobj.get("runtime").getAsInt());
		tvEP.setSummary(htmlToString(jobj.get("summary").getAsString()));
		return tvEP;
	}

	private static String calculateAge(DateTime epDate) {

		Period period;
		if (epDate.isBefore(new DateTime())) {
			period = new Period(epDate, new DateTime());
		} else {
			period = new Period(new DateTime(), epDate);
		}

		PeriodFormatter formatter = new PeriodFormatterBuilder()
				.appendYears().appendSuffix("y")
				.appendMonths().appendSuffix("m")
				.appendWeeks().appendSuffix("w")
				.appendDays().appendSuffix("d ")
				.appendHours().appendSuffix("h")
				.appendMinutes().appendSuffix("m")
				.printZeroNever().toFormatter();

		return formatter.print(period);
	}

	public static String retrieveHttpAsString(String url) throws Exception {
		CloseableHttpClient httpclient = HttpClients.createDefault();
		RequestConfig requestConfig = RequestConfig.custom()
				.setSocketTimeout(5000)
				.setConnectTimeout(5000)
				.setConnectionRequestTimeout(5000)
				.build();
		HttpGet httpGet = new HttpGet(url);
		httpGet.setConfig(requestConfig);
		CloseableHttpResponse response = null;
		try {
			response = httpclient.execute(httpGet);
			final int statusCode = response.getStatusLine().getStatusCode();
			if (statusCode != HttpStatus.SC_OK) {
				throw new Exception("Error " + statusCode + " for URL " + url);
			}
			return EntityUtils.toString(response.getEntity());
		} catch (IOException e) {
			throw new Exception("Error for URL " + url, e);
		} finally {
			if (response != null) {
				response.close();
			}
			httpclient.close();
		}
	}

	public static String filterTitle(String title) {
		String newTitle = title.toLowerCase();
		//remove filtered words
		for (String filter : TvMazeConfig.getInstance().getFilters()) {
			newTitle = newTitle.replaceAll("\\b"+filter.toLowerCase()+"\\b","");
		}
		//remove seperators
		for (String separator : _seperators) {
			newTitle = newTitle.replaceAll("\\"+separator," ");
		}
		newTitle = newTitle.trim();
		//remove extra spaces
		newTitle = newTitle.replaceAll("\\s+","%20");
		return newTitle;
	}

	public static String htmlToString(String input) {
		String str = input.replaceAll("\n", "");
		while (str.contains("<")) {
			int startPos = str.indexOf("<");
			int endPos = str.indexOf(">", startPos);
			if (endPos > startPos) {
				String beforeTag = str.substring(0, startPos);
				String afterTag = str.substring(endPos + 1);
				str = beforeTag + afterTag;
			}
		}

		String mbChar;
		String mbs = "&#(\\d+);";
		StringBuffer sb = new StringBuffer();
		Pattern pat = Pattern.compile(mbs);
		Matcher mat = pat.matcher(str);

		while (mat.find()) {
			mbChar = getMbCharStr(mat.group(1));
			mat.appendReplacement(sb, mbChar);
		}
		mat.appendTail(sb);
		return new String(sb);
	}

	private static String getMbCharStr(String digits) {
		char[] cha = new char[1];

		try {
			int val = Integer.parseInt(digits);
			char ch = (char) val;
			cha[0] = ch;
		} catch (Exception e) {
			System.err.println("Error from getMbCharStr:");
			e.printStackTrace(System.err);
		}
		return new String(cha);
	}

	public static ArrayList<DirectoryHandle> findReleases(DirectoryHandle sectionDir, User user, String showName, int season, int number) throws FileNotFoundException {
		IndexEngineInterface ie = GlobalContext.getGlobalContext().getIndexEngine();
		Map<String,String> inodes;

		AdvancedSearchParams params = new AdvancedSearchParams();

		TvMazeQueryParams queryParams;
		try {
			queryParams = params.getExtensionData(TvMazeQueryParams.TvMazeQUERYPARAMS);
		} catch (KeyNotFoundException e) {
			queryParams = new TvMazeQueryParams();
			params.addExtensionData(TvMazeQueryParams.TvMazeQUERYPARAMS, queryParams);
		}
		queryParams.setName(showName);
		queryParams.setSeason(season);
		queryParams.setNumber(number);

		params.setInodeType(AdvancedSearchParams.InodeType.DIRECTORY);
		params.setSortField("lastmodified");
		params.setSortOrder(true);

		try {
			inodes = ie.advancedFind(sectionDir, params);
		} catch (IndexException e) {
			throw new FileNotFoundException("Index Exception: "+e.getMessage());
		}

		ArrayList<DirectoryHandle> releases = new ArrayList<DirectoryHandle>();

		for (Map.Entry<String,String> item : inodes.entrySet()) {
			try {
				DirectoryHandle inode = new DirectoryHandle(VirtualFileSystem.fixPath(item.getKey()));
				if (!inode.isHidden(user)) {
					releases.add(inode);
				}
			} catch (FileNotFoundException e) {
				// This is ok, could be multiple nukes fired and
				// that is has not yet been reflected in index due to async event.
			}
		}

		return releases;
	}

	public static long randomNumber() {
		return (TvMazeConfig.getInstance().getStartDelay() + (new Random()).nextInt(
				TvMazeConfig.getInstance().getEndDelay()-TvMazeConfig.getInstance().getStartDelay()
				))*1000;
	}

	public static TvMazeInfo getTvMazeInfo(DirectoryHandle dir) {
		TvMazeVFSData tvmazeData = new TvMazeVFSData(dir);
		try {
			return tvmazeData.getTvMazeInfo();
		} catch (FileNotFoundException e) {
			// Thats strange...
			logger.error("",e);
		} catch (IOException e) {
			// To bad...
			logger.error("",e);
		} catch (NoAvailableSlaveException e) {
			// Not much to do...
		} catch (SlaveUnavailableException e) {
			// Not much to do...
		}
		return null;
	}

	public static void publishEvent(TvMazeInfo tvmazeInfo, DirectoryHandle dir, SectionInterface section) {
		if (tvmazeInfo != null) {
			// TvMaze show found, announce to IRC
			ReplacerEnvironment env;
			if (tvmazeInfo.getEPList().length == 1) {
				env = getEPEnv(tvmazeInfo, tvmazeInfo.getEPList()[0]);
			} else {
				env = getShowEnv(tvmazeInfo);
			}
			env.add("release", dir.getName());
			env.add("section", section.getName());
			GlobalContext.getEventService().publishAsync(new TvMazeEvent(env, dir));
		}
	}

	public static boolean isRelease(String dirName) {
		Pattern p = Pattern.compile("(\\w+\\.){3,}\\w+-\\w+");
		Matcher m = p.matcher(dirName);
		return m.find();
	}

	public static Comparator<TvEpisode> epNumberComparator = new Comparator<TvEpisode>() {
		public int compare(TvEpisode tvEpisode1, TvEpisode tvEpisode2) {
			return tvEpisode1.getNumber() - tvEpisode2.getNumber();
		}
	};

}

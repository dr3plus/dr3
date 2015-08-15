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
package org.drftpd.commands.dataconnection.bannedip;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Properties;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;
import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.CommandInterface;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.master.BaseFtpConnection;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.event.ReloadEvent;

/**
 * @author surface
 */

public class Bannedip extends CommandInterface {
	private static final Logger logger = Logger.getLogger(Bannedip.class);
	private ArrayList<String> _ipRanges;
	private ArrayList<String> _ip;
	private String _msg;
	
	public void startPlugin() {
		_ipRanges = new ArrayList<String>();
		_ip = new ArrayList<String>();
		reload();
		// Subscribe to events
		AnnotationProcessor.process(this);
		logger.info("Bannedips loaded the Bannedips plugin successfully");
	}

	public void stopPlugin(String reason) {
		AnnotationProcessor.unprocess(this);
		logger.info("Bannedips unloaded the Banned ip plugin successfully");
	}

	@EventSubscriber
	public void onReloadEvent(ReloadEvent event) {
		reload();
	}
	
	private void reload() {
		logger.info("BANNEDIP: Reloading " + Bannedip.class.getName());
		
		Properties cfg = GlobalContext.getGlobalContext().getPluginsConfig().getPropertiesForPlugin("bannedips.conf");
		
		if (cfg == null) {
			logger.info("conf/plugins/bannedips.conf not found");
			return;
		}
		
		_msg = cfg.getProperty("message");

		for (int i = 1; ; i++) {
			String sec = cfg.getProperty(i + ".iprange");
			if (sec == null)
				break;
			_ipRanges.add(sec);
			logger.info("BANNEDIPS: added" + sec);
		}
		_ipRanges.trimToSize();

		for (int i = 1; ; i++) {
			String sec = cfg.getProperty(i + ".ip");
			if (sec == null)
				break;
			_ip.add(sec);
			logger.info("BANNEDIPS: added" + sec);
		}
		_ip.trimToSize();

		if (_msg == null)
			_msg = "";
		
		for (String i : _ip){
			   if (!_ipRanges.contains(i))
			      _ipRanges.add(i);
			}
	}

	/**
	 * <code>PORT &lt;SP&gt; <host-port> &lt;CRLF&gt;</code><br>
	 *
	 * The argument is a HOST-PORT specification for the data port to be used in
	 * data connection. There are defaults for both the user and server data
	 * ports, and under normal circumstances this command and its reply are not
	 * needed. If this command is used, the argument is the concatenation of a
	 * 32-bit internet host address and a 16-bit TCP port address. This address
	 * information is broken into 8-bit fields and the value of each field is
	 * transmitted as a decimal number (in character string representation). The
	 * fields are separated by commas. A port command would be:
	 *
	 * PORT h1,h2,h3,h4,p1,p2
	 *
	 * where h1 is the high order 8 bits of the internet host address.
	 * @return 
	 */
	
	public CommandResponse doBannedPORT(CommandRequest request) {
		InetAddress clientAddr = null;

		StringTokenizer st = new StringTokenizer(request.getArgument(), ",");

		if (st.countTokens() != 6) {
			return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
		}

		// get data server
		String dataSrvName = st.nextToken() + '.' + st.nextToken() + '.' +
		st.nextToken() + '.' + st.nextToken();

		try {
			clientAddr = InetAddress.getByName(dataSrvName);
		} catch (UnknownHostException ex) {
			return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
		}

		BaseFtpConnection conn = (BaseFtpConnection) request.getSession();
		String portHostAddress = clientAddr.getHostAddress();
		String clientHostAddress = conn.getControlSocket().getInetAddress()
		.getHostAddress();

		if ((portHostAddress.startsWith("192.168.") &&
				!clientHostAddress.startsWith("192.168.")) ||
				(portHostAddress.startsWith("10.") &&
						!clientHostAddress.startsWith("10."))) {
			CommandResponse response = new CommandResponse(501);
			response.addComment("==YOU'RE BEHIND A NAT ROUTER==");
			response.addComment(
			"Configure the firewall settings of your FTP client");
			response.addComment("  to use your real IP: " +
					conn.getControlSocket().getInetAddress().getHostAddress());
			response.addComment("And set up port forwarding in your router.");
			response.addComment(
			"Or you can just use a PRET capable client, see");
			response.addComment("  http://drftpd.org/ for PRET capable clients");

			reset(conn);
			return response;
		}
		
		
		for (String ipRange : _ipRanges) {
			if (portHostAddress.startsWith(ipRange)) {
				logger.warn("BAN: Found blocked IPrange " +ipRange);
				
				CommandResponse response = new CommandResponse(501);
				response.addComment("==IP is BlOCKED==");
				response.addComment(" Your IP has been logged: " +
						conn.getControlSocket().getInetAddress().getHostAddress());
				response.addComment("And source/target IP: " +
						clientAddr.getHostAddress());
				
				response.addComment(_msg);
				
				reset(conn);
				return response;
			}
		}

		return null;
	}
	
	protected synchronized void reset(BaseFtpConnection conn) {
		conn.getTransferState().reset();
	}
}


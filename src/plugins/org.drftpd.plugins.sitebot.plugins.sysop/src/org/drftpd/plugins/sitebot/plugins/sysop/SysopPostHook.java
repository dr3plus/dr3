package org.drftpd.plugins.sitebot.plugins.sysop;

import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.PostHookInterface;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.plugins.sitebot.plugins.sysop.event.SysopEvent;

public class SysopPostHook implements PostHookInterface {

	public void initialize(StandardCommandManager manager) {
	}

	public void doLOGINPostHook(CommandRequest request, CommandResponse response) {
		String cmd = request.getCommand().toUpperCase();
		
		if (cmd.equals("IDNT") && response == null)
			response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");

		String code = String.valueOf(response.getCode());
		String message;
		if (cmd.equals("PASS")) {
			message = "LOGIN";
		} else {
			message = cmd + " " + request.getArgument();
		}
		if ((code.startsWith("5") || code.startsWith("4")) && !code.startsWith("530") && showFailed(cmd) ) {
			GlobalContext.getEventService().publishAsync(
					new SysopEvent(request.getUser(), message, response
							.getMessage(), true, false));
		} else if (showSuccessful(cmd)) {
			GlobalContext.getEventService().publishAsync(
					new SysopEvent(request.getUser(), message, response
							.getMessage(), true, true));
		}
	}

	public void doSITEPostHook(CommandRequest request, CommandResponse response) {
		String cmd = request.getCommand().toUpperCase();
		if (cmd.startsWith("SITE ")) {
			cmd = cmd.substring(5);
		}
		String arg = request.getArgument();
		String code = String.valueOf(response.getCode());
		String message;
		if (cmd.equals("CHPASS") || cmd.equals("ADDUSER")) {
			message = cmd + " " + arg.substring(0, arg.indexOf(" "));
		} else if (cmd.equals("GADDUSER")) {
			String[] arguments = arg.split(" ");
			message = cmd + " " + arguments[0] + " " + arguments [1];
		} else {
			message = cmd + " " + arg;
		}
		if ((code.startsWith("5") || code.startsWith("4")) && !code.startsWith("530") && showFailed(cmd)) {
			GlobalContext.getEventService().publishAsync(
					new SysopEvent(request.getUser(), message, response
							.getMessage(), false, false));
		} else if (showSuccessful(cmd)) {
			GlobalContext.getEventService().publishAsync(
					new SysopEvent(request.getUser(), message, response
							.getMessage(), false, true));
		}
	}

	private boolean showSuccessful(String cmd) {
		Integer value = SysopManager.CONFIG.get(cmd);
		return value == null || value == 0 || value == 2;
	}
	private boolean showFailed(String cmd) {
		Integer value = SysopManager.CONFIG.get(cmd);
		return value == null || value == 0 || value == 3;
	}
}

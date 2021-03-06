/**
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 */

package autosaveworld.threads.consolecommand;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import autosaveworld.config.AutoSaveWorldConfig;
import autosaveworld.core.logging.MessageLogger;
import autosaveworld.utils.CommandUtils;
import autosaveworld.utils.SchedulerUtils;

public class AutoConsoleCommandThread extends Thread {

	private AutoSaveWorldConfig config;

	public AutoConsoleCommandThread(AutoSaveWorldConfig config) {
		this.config = config;
	}

	public void stopThread() {
		run = false;
	}

	private volatile boolean run = true;

	@Override
	public void run() {
		MessageLogger.debug("AutoConsoleCommandThread started");
		Thread.currentThread().setName("AutoSaveWorld AutoConsoleCommandThread");

		while (run) {

			// handle times mode
			if (config.ccTimesModeEnabled) {
				for (String ctime : getTimesToExecute()) {
					MessageLogger.debug("Executing console commands (timesmode)");
					executeCommands(config.ccTimesModeCommands.get(ctime));
				}
			}

			// handle interval mode
			if (config.ccIntervalsModeEnabled) {
				for (int interval : getIntervalsToExecute()) {
					MessageLogger.debug("Executing console commands (intervalmode)");
					executeCommands(config.ccIntervalsModeCommands.get(interval));
				}
			}

			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
			}
		}

		MessageLogger.debug("AutoConsoleCommandThread stopped");
	}

	private void executeCommands(final List<String> commands) {
		if (run) {
			SchedulerUtils.scheduleSyncTask(new Runnable() {
				@Override
				public void run() {
					for (String command : commands) {
						CommandUtils.dispatchCommandAsConsole(command);
					}
				}
			});
		}
	}

	// timesmode checks
	private int minute = -1;
	private SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
	private SimpleDateFormat msdf = new SimpleDateFormat("mm");

	private List<String> getTimesToExecute() {
		List<String> timestoexecute = new ArrayList<String>();
		int cminute = Integer.parseInt(msdf.format(System.currentTimeMillis()));
		String ctime = sdf.format(System.currentTimeMillis());
		if ((cminute != minute) && config.ccTimesModeCommands.containsKey(ctime)) {
			minute = cminute;
			timestoexecute.add(ctime);
		}
		return timestoexecute;
	}

	// intervalmode checks
	private long intervalcounter = 0;

	private List<Integer> getIntervalsToExecute() {
		List<Integer> inttoexecute = new ArrayList<Integer>();
		for (int interval : config.ccIntervalsModeCommands.keySet()) {
			if ((intervalcounter != 0) && ((intervalcounter % interval) == 0)) {
				inttoexecute.add(interval);
			}
		}
		intervalcounter++;
		return inttoexecute;
	}

}
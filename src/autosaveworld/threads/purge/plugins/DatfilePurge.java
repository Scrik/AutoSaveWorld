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

package autosaveworld.threads.purge.plugins;

import java.io.File;

import org.bukkit.Bukkit;

import autosaveworld.config.AutoSaveWorldConfig;
import autosaveworld.core.logging.MessageLogger;
import autosaveworld.threads.purge.ActivePlayersList;
import autosaveworld.threads.purge.DataPurge;

public class DatfilePurge extends DataPurge {

	public DatfilePurge(AutoSaveWorldConfig config, ActivePlayersList activeplayerslist) {
		super(config, activeplayerslist);
	}

	public void doPurge() {

		MessageLogger.debug("Playre .dat file purge started");

		int deleted = 0;
		String worldfoldername = Bukkit.getWorlds().get(0).getWorldFolder().getAbsolutePath();
		File playersdatfolder = new File(worldfoldername + File.separator + "playerdata" + File.separator);
		File playersstatsfolder = new File(worldfoldername + File.separator + "stats" + File.separator);
		for (File playerfile : playersdatfolder.listFiles()) {
			if (playerfile.getName().endsWith(".dat")) {
				String playeruuid = playerfile.getName().substring(0, playerfile.getName().length() - 4);
				if (!activeplayerslist.isActiveUUID(playeruuid)) {
					MessageLogger.debug(playeruuid + " is inactive. Removing dat file");
					playerfile.delete();
					new File(playersstatsfolder, playerfile.getName()).delete();
					deleted += 1;
				}
			}
		}

		MessageLogger.debug("Player .dat purge finished, deleted " + deleted + " player .dat files");
	}

}

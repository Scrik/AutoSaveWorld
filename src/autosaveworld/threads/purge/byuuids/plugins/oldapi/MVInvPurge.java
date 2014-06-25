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

package autosaveworld.threads.purge.byuuids.plugins.oldapi;

import java.io.File;

import org.bukkit.Bukkit;
import org.bukkit.World;

import autosaveworld.core.GlobalConstants;
import autosaveworld.core.logging.MessageLogger;
import autosaveworld.threads.purge.byuuids.ActivePlayersList;

import com.onarandombox.multiverseinventories.MultiverseInventories;
import com.onarandombox.multiverseinventories.api.profile.WorldGroupProfile;

public class MVInvPurge {

	@SuppressWarnings("deprecation")
	public void doMVInvPurgeTask(ActivePlayersList pacheck) {

		MessageLogger.debug("MVInv purge started");

		int deleted = 0;

		try {
			MultiverseInventories mvpl = (MultiverseInventories) Bukkit.getPluginManager().getPlugin("Multiverse-Inventories");
			File mcinvpfld = new File(GlobalConstants.getPluginsFolder(), "Multiverse-Inventories" +File.separator+ "players" + File.separator);
			//We will get all files from MVInv player directory, and get player names from there
			for (String plfile : mcinvpfld.list()) {
				String plname = plfile.substring(0, plfile.lastIndexOf("."));

				if (!pacheck.isActiveNameCS(plname)) {
					MessageLogger.debug("Removing "+plname+" MVInv files");
					//remove files from MVInv world folders
					for (World wname : Bukkit.getWorlds()) {
						mvpl.getWorldManager().getWorldProfile(wname.getName()).removeAllPlayerData(Bukkit.getOfflinePlayer(plname));
					}
					//remove files from MVInv player folder
					new File(mcinvpfld,plfile).delete();
					//remove files from MVInv groups folder
					for (WorldGroupProfile gname: mvpl.getGroupManager().getGroups()) {
						File mcinvgfld = new File("plugins" + File.separator + "Multiverse-Inventories" +File.separator+ "groups" + File.separator);
						new File(mcinvgfld, gname.getName()+File.separator+plfile).delete();
					}
					//count deleted player file
					deleted += 1;
				}
			}
		} catch (Exception e) {
		}

		MessageLogger.debug("MVInv purge finished, deleted "+deleted+" player files, Warning: on some Multiverse-Inventories versions you should divide this number by 2 to know the real count");
	}

}
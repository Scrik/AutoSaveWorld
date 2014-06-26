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

package autosaveworld.threads.purge.bynames;

import java.util.HashSet;

import org.bukkit.plugin.PluginManager;

import autosaveworld.config.AutoSaveWorldConfig;
import autosaveworld.core.AutoSaveWorld;
import autosaveworld.core.logging.MessageLogger;
import autosaveworld.threads.purge.bynames.plugins.DatfilePurge;
import autosaveworld.threads.purge.bynames.plugins.LWCPurge;
import autosaveworld.threads.purge.bynames.plugins.MVInvPurge;
import autosaveworld.threads.purge.bynames.plugins.MyWarpPurge;
import autosaveworld.threads.purge.bynames.plugins.ResidencePurge;
import autosaveworld.threads.purge.bynames.plugins.VaultPurge;
import autosaveworld.threads.purge.bynames.plugins.WGPurge;
import autosaveworld.threads.purge.weregen.RegenOptions;

public class PurgeByNames {

	private AutoSaveWorld plugin = null;
	private AutoSaveWorldConfig config;
	public PurgeByNames(AutoSaveWorld plugin, AutoSaveWorldConfig config) {
		this.plugin = plugin;
		this.config = config;
	}

	public void startPurge() {
		MessageLogger.debug("Gathering active players list");
		ActivePlayersList aplist = new ActivePlayersList(config);
		aplist.gatherActivePlayersList(config.purgeAwayTime * 1000);
		MessageLogger.debug("Found "+aplist.getActivePlayersCount()+" active players");

		HashSet<Integer> safeids = new HashSet<Integer>();
		if (config.purgeWERemoveUnsafe) {
			safeids.addAll(RegenOptions.parseListToIDs(config.purgeWERemoveUnsafeSafeIDs));
		}

		PluginManager pm = plugin.getServer().getPluginManager();

		if ((pm.getPlugin("WorldGuard") != null) && config.purgeWG) {
			MessageLogger.debug("WG found, purging");
			try {
				new WGPurge().doWGPurgeTask(aplist, config.purgeWGRegenRg, config.purgeWGNoregenOverlap, safeids);
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}

		if ((pm.getPlugin("LWC") != null) && config.purgeLWC) {
			MessageLogger.debug("LWC found, purging");
			try {
				new LWCPurge().doLWCPurgeTask(aplist, config.purgeLWCDelProtectedBlocks);
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}

		if ((pm.getPlugin("Multiverse-Inventories") !=null) && config.purgeMVInv) {
			MessageLogger.debug("Multiverse-Inventories found, purging");
			try {
				new MVInvPurge().doMVInvPurgeTask(aplist);
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}

		if ((pm.getPlugin("Residence") !=null) && config.purgeResidence) {
			MessageLogger.debug("Residence found, purging");
			try {
				new ResidencePurge().doResidencePurgeTask(aplist, config.purgeResidenceRegenArea, safeids);
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}

		if (pm.getPlugin("MyWarp") != null && config.purgeMyWarp) {
			MessageLogger.debug("MyWarp found, purging");
			try {
				new MyWarpPurge().doMyWarpPurgeTask(aplist);
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}

		if (pm.getPlugin("Vault") != null && config.purgePerms) {
			MessageLogger.debug("Vault found, purging permissions");
			try {
				new VaultPurge().doPermissionsPurgeTask(aplist, config.purgePermsSaveCMD);
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}

		if (config.purgeDat) {
			MessageLogger.debug("Purging player .dat files");
			try {
				new DatfilePurge().doDelPlayerDatFileTask(aplist);
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}
	}

}

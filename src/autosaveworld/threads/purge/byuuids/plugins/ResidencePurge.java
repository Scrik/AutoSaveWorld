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

package autosaveworld.threads.purge.byuuids.plugins;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.util.Vector;

import autosaveworld.core.logging.MessageLogger;
import autosaveworld.threads.purge.byuuids.ActivePlayersList;
import autosaveworld.threads.purge.weregen.WorldEditRegeneration;
import autosaveworld.utils.SchedulerUtils;

import com.bekvon.bukkit.residence.Residence;
import com.bekvon.bukkit.residence.protection.ClaimedResidence;
import com.bekvon.bukkit.residence.protection.CuboidArea;

public class ResidencePurge {

	public void doResidencePurgeTask(ActivePlayersList pacheck, final boolean regenres) {

		MessageLogger.debug("Residence purge started");

		int deletedres = 0;

		List<String> reslist = new ArrayList<String>(Arrays.asList(Residence.getResidenceManager().getResidenceList()));
		boolean wepresent = (Bukkit.getPluginManager().getPlugin("WorldEdit") != null);

		// search for residences with inactive players
		for (final String res : reslist) {
			MessageLogger.debug("Checking residence " + res);
			final ClaimedResidence cres = Residence.getResidenceManager().getByName(res);
			if (!pacheck.isActiveNameCS(cres.getOwner())) {
				MessageLogger.debug("Owner of residence " + res + " is inactive. Purging residence");

				// regen residence areas if needed
				if (regenres && wepresent) {
					for (final CuboidArea ca : cres.getAreaArray()) {
						Runnable caregen = new Runnable() {
							Vector minpoint = ca.getLowLoc().toVector();
							Vector maxpoint = ca.getHighLoc().toVector();

							@Override
							public void run() {
								MessageLogger.debug("Regenerating residence " + res + " cuboid area");
								WorldEditRegeneration.get().regenerateRegion(Bukkit.getWorld(cres.getWorld()), minpoint, maxpoint);
							}
						};
						SchedulerUtils.callSyncTaskAndWait(caregen);
					}
					// delete residence from db
					MessageLogger.debug("Deleting residence " + res);
					Runnable delres = new Runnable() {
						@Override
						public void run() {
							cres.remove();
							Residence.getResidenceManager().save();
						}
					};
					SchedulerUtils.callSyncTaskAndWait(delres);

					deletedres += 1;
				}
			}
		}

		MessageLogger.debug("Residence purge finished, deleted " + deletedres + " inactive residences");
	}

}
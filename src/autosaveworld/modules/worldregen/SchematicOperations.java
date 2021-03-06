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

package autosaveworld.modules.worldregen;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import org.bukkit.World;

import autosaveworld.modules.worldregen.SchematicData.SchematicToLoad;
import autosaveworld.modules.worldregen.SchematicData.SchematicToSave;
import autosaveworld.utils.SchedulerUtils;

import com.sk89q.worldedit.CuboidClipboard;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.blocks.BaseBlock;
import com.sk89q.worldedit.bukkit.BukkitWorld;
import com.sk89q.worldedit.data.DataException;
import com.sk89q.worldedit.schematic.SchematicFormat;

@SuppressWarnings("deprecation")
public class SchematicOperations {

	public static void saveToSchematic(final World world, final List<SchematicToSave> schematicdatas) {
		Runnable copypaste = new Runnable() {
			@Override
			public void run() {
				BukkitWorld weworld = new BukkitWorld(world);
				for (SchematicToSave schematicdata : schematicdatas) {
					try {
						// create dirs if needed
						schematicdata.getFile().getParentFile().mkdirs();
						// create clipboard
						EditSession es = new EditSession(weworld, Integer.MAX_VALUE);
						CuboidClipboard clipboard = new CuboidClipboard(schematicdata.getMax().subtract(schematicdata.getMin()).add(new Vector(1, 1, 1)), schematicdata.getMin(), schematicdata.getMin().subtract(schematicdata.getMax()));
						es.setFastMode(true);
						// copy blocks
						clipboard.copy(es);
						// save to schematic
						SchematicFormat.MCEDIT.save(clipboard, schematicdata.getFile());
					} catch (Throwable e) {
						e.printStackTrace();
					}
				}
			}
		};
		SchedulerUtils.callSyncTaskAndWait(copypaste);
	}

	public static void pasteFromSchematic(final World world, final List<SchematicToLoad> schematicdatas) {
		final LinkedList<CuboidClipboard> clipboards = new LinkedList<CuboidClipboard>();
		// load from schematics to clipboards
		for (SchematicToLoad schematicdata : schematicdatas) {
			try {
				clipboards.add(SchematicFormat.MCEDIT.load(schematicdata.getFile()));
			} catch (IOException | DataException e) {
				e.printStackTrace();
			}
		}
		// generate chunks
		Runnable genchunks = new Runnable() {
			@Override
			public void run() {
				for (CuboidClipboard clipboard : clipboards) {
					final Vector size = clipboard.getSize();
					final Vector origin = clipboard.getOrigin();
					// generate chunks at schematic position and 3 chunk radius nearby
					for (int x = -16 * 3; x < (size.getBlockX() + (16 * 3)); x += 16) {
						for (int z = -16 * 3; z < (size.getBlockZ() + (16 * 3)); z += 16) {
							world.getChunkAt(origin.getBlockX() + x, origin.getBlockZ() + z).load();
						}
					}
				}
			}
		};
		SchedulerUtils.callSyncTaskAndWait(genchunks);
		// paste schematics
		Runnable paste = new Runnable() {
			@Override
			public void run() {
				BukkitWorld weworld = new BukkitWorld(world);
				for (CuboidClipboard clipboard : clipboards) {
					EditSession es = new EditSession(weworld, Integer.MAX_VALUE);
					es.setFastMode(true);
					es.enableQueue();
					final Vector size = clipboard.getSize();
					final Vector origin = clipboard.getOrigin();
					// paste blocks
					for (int x = 0; x < size.getBlockX(); ++x) {
						for (int y = 0; y < size.getBlockY(); ++y) {
							for (int z = 0; z < size.getBlockZ(); ++z) {
								Vector blockpos = new Vector(x, y, z);
								final BaseBlock block = clipboard.getBlock(blockpos);

								if (block == null) {
									continue;
								}

								try {
									es.smartSetBlock(new Vector(x, y, z).add(origin), block);
								} catch (Throwable t) {
									t.printStackTrace();
								}
							}
						}
					}
					try {
						es.flushQueue();
					} catch (Throwable t) {
						t.printStackTrace();
					}
				}
			}
		};
		SchedulerUtils.callSyncTaskAndWait(paste);
	}

}

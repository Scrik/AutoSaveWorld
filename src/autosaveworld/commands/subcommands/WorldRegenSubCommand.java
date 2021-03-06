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

package autosaveworld.commands.subcommands;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import autosaveworld.commands.ISubCommand;
import autosaveworld.config.AutoSaveWorldConfig;
import autosaveworld.config.AutoSaveWorldConfigMSG;
import autosaveworld.core.AutoSaveWorld;
import autosaveworld.core.logging.MessageLogger;
import autosaveworld.modules.worldregen.WorldRegenCopyThread;

public class WorldRegenSubCommand implements ISubCommand {

	private AutoSaveWorld plugin;
	private AutoSaveWorldConfig config;
	private AutoSaveWorldConfigMSG configmsg;
	public WorldRegenSubCommand(AutoSaveWorld plugin, AutoSaveWorldConfig config, AutoSaveWorldConfigMSG configmsg) {
		this.plugin = plugin;
		this.config = config;
		this.configmsg = configmsg;
	}

	@Override
	public void handle(CommandSender sender, String[] args) {
		if (Bukkit.getPluginManager().getPlugin("WorldEdit") == null) {
			MessageLogger.sendMessage(sender, "You need WorldEdit installed to do that");
			return;
		}
		if (Bukkit.getWorld(args[0]) == null) {
			MessageLogger.sendMessage(sender, "This world doesn't exist");
			return;
		}
		WorldRegenCopyThread copythread = new WorldRegenCopyThread(plugin, config, configmsg);
		copythread.setWorld(args[0]);
		copythread.start();
	}

	@Override
	public List<String> tabComplete(CommandSender sender, String[] args) {
		ArrayList<String> result = new ArrayList<String>();
		for (World world : Bukkit.getWorlds()) {
			if (world.getName().startsWith(args[0])) {
				result.add(world.getName());
			}
		}
		return result;
	}

	@Override
	public int getMinArguments() {
		return 1;
	}

}

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

package autosaveworld.threads.backup.ftp;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.bukkit.World;

import autosaveworld.core.GlobalConstants;
import autosaveworld.core.logging.MessageLogger;
import autosaveworld.zlibs.org.apache.commons.net.ftp.FTPClient;

public class FTPBackupOperations {

	private boolean zip;
	private List<String> excludefolders;
	private FTPClient ftp;

	public FTPBackupOperations(FTPClient ftp, boolean zip, List<String> excludefolders) {
		this.zip = zip;
		this.excludefolders = excludefolders;
		this.ftp = ftp;
	}

	public void backupWorld(World world) {
		MessageLogger.debug("Backuping world " + world.getWorldFolder().getName());
		try {
			File worldfolder = world.getWorldFolder().getAbsoluteFile();
			backupFolder(worldfolder);
		} catch (Exception e) {
			e.printStackTrace();
		}
		MessageLogger.debug("Backuped world " + world.getWorldFolder().getName());
	}

	public void backupPlugins() {
		try {
			File plfolder = new File(GlobalConstants.getPluginsFolder()).getAbsoluteFile();
			backupFolder(plfolder);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void backupOtherFolders(List<String> folders) {
		for (String folder : folders) {
			MessageLogger.debug("Backuping folder " + folder);
			try {
				File fld = new File(folder).getAbsoluteFile();
				backupFolder(fld);
			} catch (Exception e) {
				e.printStackTrace();
			}
			MessageLogger.debug("Backuped folder " + folder);
		}
	}

	private void backupFolder(File folder) throws IOException {
		if (!zip) {
			FTPUtils.uploadDirectory(ftp, folder, excludefolders);
		} else {
			FTPUtils.zipAndUploadDirectory(ftp, folder, excludefolders);
		}
	}

}

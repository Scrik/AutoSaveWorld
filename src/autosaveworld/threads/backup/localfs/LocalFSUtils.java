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

package autosaveworld.threads.backup.localfs;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

import autosaveworld.threads.backup.ExcludeManager;
import autosaveworld.threads.backup.InputStreamConstruct;

public class LocalFSUtils {

	public static void copyDirectory(File sourceLocation, File targetLocation, List<String> excludefolders) {
		if (sourceLocation.isDirectory()) {
			targetLocation.mkdirs();
			for (String filename : sourceLocation.list()) {
				if (!ExcludeManager.isFolderExcluded(excludefolders, new File(sourceLocation, filename).getPath())) {
					copyDirectory(new File(sourceLocation, filename), new File(targetLocation, filename), excludefolders);
				}
			}
		} else {
			if (!sourceLocation.getName().endsWith(".lck")) {
				try (InputStream is = InputStreamConstruct.getFileInputStream(sourceLocation)) {
					Files.copy(is, targetLocation.toPath(), StandardCopyOption.REPLACE_EXISTING);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

}

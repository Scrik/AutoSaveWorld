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

package autosaveworld.threads.purge.plugins.residence;

import java.util.ArrayList;

import autosaveworld.utils.SchedulerUtils;

public class TaskQueue {

	private int tasksLimit = 80;

	private ArrayList<ResidencePurgeTask> tasks = new ArrayList<ResidencePurgeTask>();

	public void addTask(final ResidencePurgeTask task) {
		if (task.isHeavyTask()) {
			SchedulerUtils.callSyncTaskAndWait(
				new Runnable() {
					@Override
					public void run() {
						task.performTask();
					}
				}
			);
		} else {
			tasks.add(task);
			if (tasks.size() >= tasksLimit) {
				flush();
			}
		}
	}

	public void flush() {
		SchedulerUtils.callSyncTaskAndWait(
			new Runnable() {
				@Override
				public void run() {
					for (ResidencePurgeTask task : tasks) {
						task.performTask();
					}
					tasks.clear();
				}
			}
		);
	}

}

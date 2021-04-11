/*
 * Copyright 2019 Daniel Gultsch
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rs.ltt.android.util;

import androidx.work.WorkInfo;

import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;

import java.util.Collection;
import java.util.List;


//TODO: Rename to WorkInfos?
public class WorkInfoUtil {


    private static Collection<WorkInfo.State> transform(List<WorkInfo> info) {
        return Collections2.transform(info, input -> input == null ? null : input.getState());
    }


    //TODO use isFinished() instead?
    public static boolean allDone(List<WorkInfo> info) {
        return Iterables.all(
                transform(info),
                s -> s != null && s != WorkInfo.State.ENQUEUED && s != WorkInfo.State.RUNNING
        );
    }

}

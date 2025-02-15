/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.kylin.tool.garbage;

import java.util.Arrays;
import java.util.List;

import org.apache.kylin.common.KylinConfig;
import org.apache.kylin.common.metrics.MetricsCategory;
import org.apache.kylin.common.metrics.MetricsGroup;
import org.apache.kylin.common.metrics.MetricsName;
import org.apache.kylin.common.scheduler.EventBusFactory;
import org.apache.kylin.common.scheduler.SourceUsageUpdateNotifier;
import org.apache.kylin.metadata.project.EnhancedUnitOfWork;
import org.apache.kylin.metadata.project.NProjectManager;

import lombok.val;

public abstract class MetadataCleaner implements GarbageCleaner {
    protected final String project;

    protected MetadataCleaner(String project) {
        this.project = project;
    }

    // do in transaction
    public abstract void beforeExecute();

    // do in transaction
    @Override
    public abstract void execute();

    // do in transaction
    public abstract void afterExecute();

    public void prepare() {
        // default do nothing
    }

    public static void clean(String project, boolean needAggressiveOpt) {
        val projectInstance = NProjectManager.getInstance(KylinConfig.getInstanceFromEnv()).getProject(project);
        if (projectInstance == null) {
            return;
        }

        List<MetadataCleaner> cleaners = initCleaners(project, needAggressiveOpt);
        cleaners.forEach(MetadataCleaner::prepare);
        EnhancedUnitOfWork.doInTransactionWithCheckAndRetry(() -> {
            cleaners.forEach(MetadataCleaner::beforeExecute);
            cleaners.forEach(MetadataCleaner::execute);
            cleaners.forEach(MetadataCleaner::afterExecute);
            return 0;
        }, project);

        EventBusFactory.getInstance().postAsync(new SourceUsageUpdateNotifier());
        MetricsGroup.hostTagCounterInc(MetricsName.METADATA_CLEAN, MetricsCategory.PROJECT, project);
    }

    private static List<MetadataCleaner> initCleaners(String project, boolean needAggressiveOpt) {
        return Arrays.asList(new SnapshotCleaner(project), new IndexCleaner(project, needAggressiveOpt),
                new ExecutableCleaner(project));
    }
}

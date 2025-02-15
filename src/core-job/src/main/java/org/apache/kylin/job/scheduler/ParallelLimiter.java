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

package org.apache.kylin.job.scheduler;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.kylin.job.JobContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ParallelLimiter {

    private static final Logger logger = LoggerFactory.getLogger(ParallelLimiter.class);

    private final JobContext jobContext;

    private final AtomicInteger accumulator;

    public ParallelLimiter(JobContext jobContext) {
        this.jobContext = jobContext;
        accumulator = new AtomicInteger(0);
    }

    public boolean tryAcquire() {
        int threshold = jobContext.getKylinConfig().getParallelJobCountThreshold();
        if (accumulator.getAndIncrement() < threshold) {
            return true;
        }

        int c = accumulator.decrementAndGet();
        logger.info("Acquire failed with parallel job count: {}, threshold {}", c, threshold);
        return false;
    }

    public boolean tryRelease() {
        // exclude master lock
        int c = jobContext.getJobLockMapper().getActiveJobLockCount();
        int threshold = jobContext.getKylinConfig().getParallelJobCountThreshold();
        if (c < threshold) {
            accumulator.set(c);
            return true;
        }
        logger.info("Release failed with parallel job count: {}, threshold: {}", c, threshold);
        return false;
    }

    public void start() {
        // do nothing
    }

    public void destroy() {
        // do nothing
    }
}

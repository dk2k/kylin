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
package org.apache.kylin.common.util;

import org.junit.Assert;
import org.junit.jupiter.api.Test;

public class SizeConvertUtilTest {

    @Test
    public void testConvert() {
        long size = 15152114135L;
        String convertedSize = SizeConvertUtil.getReadableFileSize(size);
        Assert.assertEquals("14.1 GB", convertedSize);
    }

    @Test
    public void testGB() {
        Assert.assertEquals(1, SizeConvertUtil.byteStringAs(1024 * 1024 * 1024 + "b", ByteUnit.GiB));
        Assert.assertEquals(100, SizeConvertUtil.byteStringAs("100GB", ByteUnit.GiB));
        Assert.assertEquals(100 * 1024, SizeConvertUtil.byteStringAs("100GB", ByteUnit.MiB));
        Assert.assertEquals(100 * 1024 * 1024, SizeConvertUtil.byteStringAs("100GB", ByteUnit.KiB));
        Assert.assertEquals(100 * 1024 * 1024 * 1024L, SizeConvertUtil.byteStringAs("100GB", ByteUnit.BYTE));
        Assert.assertEquals(100 * 1024 * 1024, SizeConvertUtil.byteStringAs("100m", ByteUnit.BYTE));
        Assert.assertEquals(1, SizeConvertUtil.byteStringAsMb("1024KB"));
    }

    @Test
    public void testByteCountToDisplaySize() {
        Assert.assertEquals("1 B", SizeConvertUtil.byteCountToDisplaySize(1));
        Assert.assertEquals("1.00 KB", SizeConvertUtil.byteCountToDisplaySize(1024));
        Assert.assertEquals("1.00 MB", SizeConvertUtil.byteCountToDisplaySize(1024L * 1024));
        Assert.assertEquals("1.00 GB", SizeConvertUtil.byteCountToDisplaySize(1024L * 1024 * 1024));
        Assert.assertEquals("1.00 TB", SizeConvertUtil.byteCountToDisplaySize(1024L * 1024 * 1024 * 1024));
        Assert.assertEquals("1024.00 TB", SizeConvertUtil.byteCountToDisplaySize(1024L * 1024 * 1024 * 1024 * 1024));

        Assert.assertEquals("2 B", SizeConvertUtil.byteCountToDisplaySize(2));
        Assert.assertEquals("1.00 KB", SizeConvertUtil.byteCountToDisplaySize(1025));
        Assert.assertEquals("1.00 KB", SizeConvertUtil.byteCountToDisplaySize(1025, 2));
        Assert.assertEquals("1.01 KB", SizeConvertUtil.byteCountToDisplaySize(1035));
        Assert.assertEquals("1.01 KB", SizeConvertUtil.byteCountToDisplaySize(1035, 2));
    }
}

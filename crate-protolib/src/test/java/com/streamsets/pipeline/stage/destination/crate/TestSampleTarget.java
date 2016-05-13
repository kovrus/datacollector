/**
 * Copyright 2015 StreamSets Inc.
 * <p>
 * Licensed under the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.streamsets.pipeline.stage.destination.crate;

import com.google.common.collect.ImmutableMap;
import com.streamsets.pipeline.api.Field;
import com.streamsets.pipeline.api.Record;
import com.streamsets.pipeline.api.Target;
import com.streamsets.pipeline.sdk.RecordCreator;
import com.streamsets.pipeline.sdk.TargetRunner;
import com.streamsets.pipeline.stage.destination.elasticsearch.ElasticSearchDTarget;
import org.junit.Assert;
import org.junit.Test;

import java.util.*;

public class TestSampleTarget {
    @Test
    public void testWriteSingleRecord() throws Exception {
        Target target = createTarget();
        TargetRunner runner = new TargetRunner.Builder(CrateTarget.class, target).build();

        runner.runInit();

        Record record = RecordCreator.create();
        Map<String, Field> fields = new HashMap<>();
        fields.put("first", Field.create("John"));
        fields.put("last", Field.create("Smith"));
        fields.put("someField", Field.create("some value"));
        record.set(Field.create(fields));

        Record record1 = RecordCreator.create();
        Map<String, Field> fields1 = new HashMap<>();
        fields1.put("first", Field.create("John1"));
        fields1.put("last", Field.create("Smith1"));
        fields1.put("someField", Field.create("some value1"));
        record1.set(Field.create(fields1));

        Record record2 = RecordCreator.create();
        Map<String, Field> fields2 = new HashMap<>();
        fields2.put("first", Field.create("John2"));
        fields2.put("last", Field.create("Smith2"));
        record2.set(Field.create(fields2));

        runner.runWrite(Arrays.asList(record, record1, record2));
        Assert.assertTrue(runner.getErrorRecords().isEmpty());
        Assert.assertTrue(runner.getErrors().isEmpty());

        // Here check the data destination. E.g. a mock.

        runner.runDestroy();
    }

    private CrateTarget createTarget() {
        CrateConfigBean conf = new CrateConfigBean();
        conf.uris = "st1.p.fir.io:4300,st2.p.fir.io:4300";
        conf.batchSize = 1000;
        conf.table = "stepsq";
        return new CrateTarget(conf);
    }

}

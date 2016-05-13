/**
 * Copyright 2015 StreamSets Inc.
 *
 * Licensed under the Apache Software Foundation (ASF) under one
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
package com.streamsets.pipeline.stage.destination.crate;

import com.streamsets.pipeline.api.ConfigDef;

import java.util.List;

public class CrateConfigBean {

    @ConfigDef(
            required = true,
            type = ConfigDef.Type.STRING,
            label = "Cluster URIs",
            defaultValue = "hostname1:port,hostname2:port",
            description = "Crate Node URIs",
            displayPosition = 20,
            group = "CRATE"
    )
    public String uris;

    @ConfigDef(
            required = true,
            type = ConfigDef.Type.STRING,
            label = "Table",
            description = "",
            displayPosition = 40,
            group = "CRATE"
    )
    public String table;

    @ConfigDef(
            required = true,
            type = ConfigDef.Type.NUMBER,
            label = "Batch Size (recs)",
            defaultValue = "1000",
            description = "Max number of records per batch",
            displayPosition = 20,
            group = "CRATE",
            min = 1,
            max = Integer.MAX_VALUE
    )
    public int batchSize;

}

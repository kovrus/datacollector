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

import com.streamsets.pipeline.api.Field;
import com.streamsets.pipeline.stage.lib.crate.Errors;

import com.streamsets.pipeline.api.Batch;
import com.streamsets.pipeline.api.Record;
import com.streamsets.pipeline.api.StageException;
import com.streamsets.pipeline.api.base.BaseTarget;
import com.streamsets.pipeline.api.base.OnRecordErrorException;
import com.streamsets.pipeline.api.impl.Utils;
import com.sun.org.apache.xerces.internal.xs.LSInputList;
import io.crate.action.sql.SQLBulkRequest;
import io.crate.action.sql.SQLBulkResponse;
import io.crate.action.sql.SQLResponse;
import io.crate.client.CrateClient;
import io.crate.shade.org.apache.commons.lang3.StringUtils;
import io.crate.shade.org.elasticsearch.action.ActionFuture;

import java.util.*;

/**
 * This target is an example and does not actually write to any destination.
 */
public class CrateTarget extends BaseTarget {

    private String insertStmt = "INSERT INTO %s (\"%s\") values (%s)";
    private final CrateConfigBean conf;
    private final String getColumnsStmt = "SHOW COLUMNS from %s";

    private int batchSize;

    private String table;
    private Map<String, String> columns;
    private CrateClient client;

    public CrateTarget(CrateConfigBean conf) {
        this.conf = conf;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected List<ConfigIssue> init() {
        List<ConfigIssue> issues = super.init();

        String urls = conf.uris;
        if (urls == null || urls.isEmpty()) { // TODO regex
            issues.add(
                    getContext().createConfigIssue(
                            Groups.CRATE.name(), "config", Errors.CRATE_DRIVER_ERR_00, "Nodes URI is not provided or incorrect"
                    )
            );
        }
        try {
            client = new CrateClient(urls.split(","));

            batchSize = conf.batchSize;
            table = conf.table;
            columns = getSchema();
            if (columns.isEmpty()) {
                issues.add(
                        getContext().createConfigIssue(
                                Groups.CRATE.name(), "config", Errors.CRATE_DRIVER_ERR_00, "Cannot fetch table columns"
                        )
                );
            }

            insertStmt = String.format(insertStmt,
                    table, StringUtils.join(columns.keySet(), "\", \""),
                    StringUtils.repeat("?", ", ", columns.size())
            );
        } catch (Exception e) {
            issues.add(
                    getContext().createConfigIssue(
                            Groups.CRATE.name(), "config", Errors.CRATE_DRIVER_ERR_00, e.getLocalizedMessage()
                    )
            );
        }
        return issues;

    }

    private Map<String, String> getSchema() {
        SQLResponse response = client.sql(String.format(getColumnsStmt, table)).actionGet();
        Map<String, String> columns = new HashMap<>();
        for (int i = 0; i < response.rowCount(); i++) {
            String field = String.valueOf(response.rows()[i][0]);
            if (field.contains("[") && field.contains("]")) {
                continue;
            }
            columns.put(field, String.valueOf(response.rows()[i][1]));
        }
        return columns;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void destroy() {
        super.destroy();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void write(Batch batch) throws StageException {
        Iterator<Record> batchIterator = batch.getRecords();

        List<Object> records = new ArrayList<>();
        List<Object> values = new LinkedList<>();

        while (batchIterator.hasNext()) {
            Map<String, Object> record = recordToMap(batchIterator.next());
            for (String column : columns.keySet()) {
                if (!record.containsKey(column)) {
                    values.add(null);
                } else {
                    if (columns.get(column).equals("object")) {
                        values.add(null);
                    } else {
                        values.add(record.get(column));
                    }
                }
            }

            records.add(values.toArray());
            values.clear();

            if (records.size() > batchSize) {
                try {
                    client.bulkSql(new SQLBulkRequest(insertStmt, records.toArray(new Object[0][]))).actionGet();
                    records.clear();
                } catch (Exception e) {
                    switch (getContext().getOnErrorRecord()) {
                        case DISCARD:
                            break;
                        case TO_ERROR:
                            throw new StageException(Errors.CRATE_DRIVER_ERR_01, getContext().getOnErrorRecord() + e.toString());
                        case STOP_PIPELINE:
                            throw new StageException(Errors.CRATE_DRIVER_ERR_01, e.toString());
                        default:
                            throw new IllegalStateException(
                                    Utils.format("Unknown OnError value '{}'", getContext().getOnErrorRecord(), e)
                            );
                    }
                }
            }
        }
        if (!records.isEmpty()) {
            client.bulkSql(new SQLBulkRequest(insertStmt, records.toArray(new Object[0][]))).actionGet();
        }

    }

    private Map<String, Object> recordToMap(Record record) {
        Map<String, Object> recordMap = new HashMap<>();
        Field val = record.get();
        if (val != null) {
            for (Map.Entry<String, Field> entry : val.getValueAsMap().entrySet()) {
                Field value = entry.getValue();
                if (value != null) {
                    recordMap.put(entry.getKey(), value.getValue());
                }
            }
        }
        return recordMap;
    }

}



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
package com.streamsets.pipeline.stage.destination.hive;

import com.streamsets.pipeline.api.ErrorCode;
import com.streamsets.pipeline.api.GenerateResourceBundle;

@GenerateResourceBundle
public enum Errors implements ErrorCode {
  HIVE_00("Cannot have multiple field mappings for the same column: '{}'"),
  HIVE_01("Error: {}"),
  HIVE_02("Schema '{}' does not exist."),
  HIVE_03("Table '{}.{}' does not exist."),
  HIVE_04("Thrift protocol error: {}"),
  HIVE_05("Hive Metastore error: {}"),
  HIVE_06("Configuration file '{}' is missing from '{}'"),
  HIVE_07("Configuration dir '{}' does not exist"),
  HIVE_08("Partition field paths '{}' missing from record"),
  HIVE_09("Hive Streaming Error: {}"),
  HIVE_11("Failed to get login user"),
  HIVE_12("Failed to create Hive Endpoint: {}"),
  HIVE_13("Hive Metastore Thrift URL or Hive Configuration Directory is required."),
  HIVE_14("Hive Metastore Thrift URL {} is not a valid URI"),
  ;
  private final String msg;

  Errors(String msg) {
    this.msg = msg;
  }

  @Override
  public String getCode() {
    return name();
  }

  @Override
  public String getMessage() {
    return msg;
  }
}

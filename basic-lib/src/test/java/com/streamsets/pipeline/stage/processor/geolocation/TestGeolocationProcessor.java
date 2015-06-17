/**
 * (c) 2015 StreamSets, Inc. All rights reserved. May not
 * be copied, modified, or distributed in whole or part without
 * written consent of StreamSets, Inc.
 */
package com.streamsets.pipeline.stage.processor.geolocation;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.streamsets.pipeline.api.Field;
import com.streamsets.pipeline.api.Record;
import com.streamsets.pipeline.api.Stage;
import com.streamsets.pipeline.api.base.OnRecordErrorException;
import com.streamsets.pipeline.api.impl.Utils;
import com.streamsets.pipeline.sdk.ProcessorRunner;
import com.streamsets.pipeline.sdk.RecordCreator;
import com.streamsets.pipeline.sdk.StageRunner;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.io.File;

public class TestGeolocationProcessor {

  private File tempDir;
  private File databaseFile;

  @Before
  public void setup() throws Exception {
    tempDir = Files.createTempDir();
    databaseFile = new File(tempDir, "GeoLite2-Country.mmdb");
    BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(databaseFile));
    Resources.copy(Resources.getResource("GeoLite2-Country.mmdb"), out);
    out.flush();
    out.close();
  }

  @After
  public void tearDown() {
    if (tempDir != null) {
      FileUtils.deleteQuietly(tempDir);
    }
  }

  @Test
  public void testConversion() throws Exception {
    String[] ips = {
      "128.101.101.101",
      "8.8.8.8"
    };
    for (String ipAsString : ips) {
      InetAddress ip = InetAddress.getByName(ipAsString);
      byte[] ipAsBytes = ip.getAddress();
      int ipAsInt = GeolocationProcessor.ipAsBytesToInt(ipAsBytes);
      Assert.assertArrayEquals(ipAsBytes, GeolocationProcessor.ipAsIntToBytes(ipAsInt));
      Assert.assertArrayEquals(ipAsBytes, GeolocationProcessor.ipAsStringToBytes(ipAsString));
      Assert.assertEquals(ipAsString, GeolocationProcessor.ipAsIntToString(ipAsInt));
      Assert.assertEquals(ipAsString, GeolocationProcessor.ipAsIntToString(ipAsInt));
      Assert.assertEquals(ipAsInt, GeolocationProcessor.ipAsStringToInt(ipAsString));
      Assert.assertArrayEquals(ipAsBytes, GeolocationProcessor.ipAsStringToBytes(ipAsString));
    }
  }
  @Test(expected = OnRecordErrorException.class)
  public void testInvalidStringIP1() throws Exception {
    GeolocationProcessor.ipAsStringToInt("1.2");
  }

  @Test(expected = OnRecordErrorException.class)
  public void testInvalidStringIP2() throws Exception {
    GeolocationProcessor.ipAsStringToInt("1.2.3.d");
  }

  @Test
  public void testIncorrectDatabase() throws Exception {
    List<GeolocationFieldConfig> configs = new ArrayList<>();
    GeolocationFieldConfig config;
    config = new GeolocationFieldConfig();
    config.inputFieldName = "/ipAsInt";
    config.outputFieldName = "/intIpCountry";
    config.targetType = GeolocationField.CITY_NAME;
    configs.add(config);
    ProcessorRunner runner = new ProcessorRunner.Builder(GeolocationDProcessor.class)
      .addConfiguration("fieldTypeConverterConfigs", configs)
      .addConfiguration("geoIP2DBFile", databaseFile.getAbsolutePath())
      .addOutputLane("a").build();
    List<Stage.ConfigIssue> configErrors = runner.runValidateConfigs();
    Assert.assertEquals(String.valueOf(configErrors), 1, configErrors.size());
    Assert.assertTrue(String.valueOf(configErrors.get(0)),
      String.valueOf(configErrors.get(0)).contains(Errors.GEOIP_05.name()));
  }

  @Test
  public void testLookup() throws Exception {
    String ip = "128.101.101.101";
    List<GeolocationFieldConfig> configs = new ArrayList<>();
    GeolocationFieldConfig config;
    config = new GeolocationFieldConfig();
    config.inputFieldName = "/ipAsInt";
    config.outputFieldName = "/intIpCountry";
    config.targetType = GeolocationField.COUNTRY_NAME;
    configs.add(config);
    config = new GeolocationFieldConfig();
    config.inputFieldName = "/ipAsIntString";
    config.outputFieldName = "/intStringIpCountry";
    config.targetType = GeolocationField.COUNTRY_NAME;
    configs.add(config);
    config = new GeolocationFieldConfig();
    config.inputFieldName = "/ipAsString";
    config.outputFieldName = "/stringIpCountry";
    config.targetType = GeolocationField.COUNTRY_NAME;
    configs.add(config);

    ProcessorRunner runner = new ProcessorRunner.Builder(GeolocationDProcessor.class)
      .addConfiguration("fieldTypeConverterConfigs", configs)
      .addConfiguration("geoIP2DBFile", databaseFile.getAbsolutePath())
      .addOutputLane("a").build();
    runner.runInit();
    try {
      Map<String, Field> map = new LinkedHashMap<>();
      map.put("ipAsInt", Field.create(GeolocationProcessor.ipAsStringToInt(ip)));
      map.put("ipAsIntString", Field.create(String.valueOf(GeolocationProcessor.ipAsStringToInt(ip))));
      map.put("ipAsString", Field.create(ip));
      Record record = RecordCreator.create("s", "s:1");
      record.set(Field.create(map));
      StageRunner.Output output = runner.runProcess(ImmutableList.of(record));
      Assert.assertEquals(0, runner.getErrorRecords().size());
      Assert.assertEquals(1, output.getRecords().get("a").size());
      Field field = output.getRecords().get("a").get(0).get();
      Assert.assertTrue(field.getValue() instanceof Map);
      Map<String, Field> result = field.getValueAsMap();
      Assert.assertEquals(String.valueOf(result), 6, result.size());
      Assert.assertEquals("United States", Utils.checkNotNull(result.get("intStringIpCountry"), "intStringIpCountry").getValue());
      Assert.assertEquals("United States", Utils.checkNotNull(result.get("intIpCountry"), "intIpCountry").getValue());
      Assert.assertEquals("United States", Utils.checkNotNull(result.get("stringIpCountry"), "stringIpCountry").getValue());
    } finally {
      runner.runDestroy();
    }
  }
}
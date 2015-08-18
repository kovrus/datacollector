/**
 * (c) 2015 StreamSets, Inc. All rights reserved. May not
 * be copied, modified, or distributed in whole or part without
 * written consent of StreamSets, Inc.
 */
package com.streamsets.datacollector.store.impl;

import com.streamsets.datacollector.config.PipelineConfiguration;
import com.streamsets.datacollector.config.RuleDefinitions;
import com.streamsets.datacollector.store.PipelineInfo;
import com.streamsets.datacollector.store.PipelineRevInfo;
import com.streamsets.datacollector.store.PipelineStoreException;
import com.streamsets.datacollector.store.PipelineStoreTask;

import java.util.List;
import java.util.Map;

public class SlavePipelineStoreTask  implements PipelineStoreTask {

  private final PipelineStoreTask pipelineStore;

  public SlavePipelineStoreTask(PipelineStoreTask pipelineStore) {
    this.pipelineStore = pipelineStore;
  }

  @Override
  public PipelineConfiguration create(String name, String description, String user) throws PipelineStoreException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void delete(String name) throws PipelineStoreException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<PipelineInfo> getPipelines() throws PipelineStoreException {
    return pipelineStore.getPipelines();
  }

  @Override
  public PipelineInfo getInfo(String name) throws PipelineStoreException {
    return pipelineStore.getInfo(name);
  }

  @Override
  public List<PipelineRevInfo> getHistory(String name) throws PipelineStoreException {
    return pipelineStore.getHistory(name);
  }

  @Override
  public PipelineConfiguration save(String name, String user, String tag, String tagDescription,
    PipelineConfiguration pipeline) throws PipelineStoreException {
    throw new UnsupportedOperationException();
  }

  @Override
  public PipelineConfiguration load(String name, String tagOrRev) throws PipelineStoreException {
    return pipelineStore.load(name, tagOrRev);
  }

  @Override
  public boolean hasPipeline(String name) {
    return pipelineStore.hasPipeline(name);
  }

  @Override
  public RuleDefinitions retrieveRules(String name, String tagOrRev) throws PipelineStoreException {
    return pipelineStore.retrieveRules(name, tagOrRev);
  }

  @Override
  public RuleDefinitions storeRules(String pipelineName, String tag, RuleDefinitions ruleDefinitions)
    throws PipelineStoreException {
    return pipelineStore.storeRules(pipelineName, tag, ruleDefinitions);
  }

  @Override
  public boolean deleteRules(String name) throws PipelineStoreException {
    return pipelineStore.deleteRules(name);
  }

  @Override
  public String getName() {
    return "SlavePipelineStoreTask";
  }

  @Override
  public void init() {
    pipelineStore.init();
  }

  @Override
  public void run() {
    pipelineStore.run();
  }

  @Override
  public void waitWhileRunning() throws InterruptedException {
    pipelineStore.waitWhileRunning();
  }

  @Override
  public void stop() {
    pipelineStore.stop();
  }

  @Override
  public Status getStatus() {
    return pipelineStore.getStatus();
  }

  @Override
  public void saveUiInfo(String name, String rev, Map<String, Object> uiInfo) throws PipelineStoreException {
    //NOP
  }

}
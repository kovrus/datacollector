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
package com.streamsets.pipeline.stage.destination.hdfs.writer;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.streamsets.pipeline.api.Record;
import com.streamsets.pipeline.api.StageException;
import com.streamsets.pipeline.api.Target;
import com.streamsets.pipeline.api.el.ELEval;
import com.streamsets.pipeline.api.el.ELVars;
import com.streamsets.pipeline.api.impl.Utils;
import com.streamsets.pipeline.lib.el.RecordEL;
import com.streamsets.pipeline.lib.generator.DataGenerator;
import com.streamsets.pipeline.lib.generator.DataGeneratorFactory;
import com.streamsets.pipeline.stage.destination.hdfs.IdleClosedException;
import org.apache.commons.io.output.CountingOutputStream;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class RecordWriter {
  private final static Logger LOG = LoggerFactory.getLogger(RecordWriter.class);
  private final static boolean IS_TRACE_ENABLED = LOG.isTraceEnabled();
  private final long expires;
  private long idleTimeout;
  private final Path path;
  private final DataGeneratorFactory generatorFactory;
  private long recordCount;

  private CountingOutputStream textOutputStream;
  private DataGenerator generator;
  private boolean textFile;

  private SequenceFile.Writer seqWriter;
  private String keyEL;
  private ELEval keyElEval;
  private ELVars elVars;
  private Text key;
  private Text value;
  private boolean seqFile;
  private boolean idleClosed;
  private Future<Void> currentIdleCloseFuture = null;
  private ActiveRecordWriters writers = null;

  private final ReadWriteLock closeLock = new ReentrantReadWriteLock();
  private ScheduledExecutorService idleCloseExecutor = Executors.newSingleThreadScheduledExecutor(
      new ThreadFactoryBuilder().setNameFormat("Idle Close Thread").build());

  private RecordWriter(Path path, long timeToLiveMillis, DataGeneratorFactory generatorFactory) {
    this.expires = (timeToLiveMillis == Long.MAX_VALUE) ? timeToLiveMillis : System.currentTimeMillis() + timeToLiveMillis;
    this.path = path;
    this.generatorFactory = generatorFactory;
    LOG.debug("Path[{}] - Creating", path);
    this.idleTimeout = -1L;
  }

  public RecordWriter(Path path, long timeToLiveMillis, OutputStream textOutputStream,
      DataGeneratorFactory generatorFactory) throws StageException, IOException {
    this(path, timeToLiveMillis, generatorFactory);
    this.textOutputStream = new CountingOutputStream(textOutputStream);
    generator = generatorFactory.getGenerator(this.textOutputStream);
    textFile = true;
    this.idleTimeout = -1L;
  }

  public RecordWriter(Path path, long timeToLiveMillis, SequenceFile.Writer seqWriter, String keyEL,
      DataGeneratorFactory generatorFactory, Target.Context context) {
    this(path, timeToLiveMillis, generatorFactory);
    this.seqWriter = seqWriter;
    this.keyEL = keyEL;
    keyElEval = context.createELEval("keyEl");
    elVars = context.createELVars();
    key = new Text();
    value = new Text();
    seqFile = true;
    this.idleTimeout = -1L;
  }

  public RecordWriter(Path path, long timeToLiveMillis, SequenceFile.Writer seqWriter, String keyEL,
                      DataGeneratorFactory generatorFactory, Target.Context context, long idleTimeout) {
    this(path, timeToLiveMillis, generatorFactory);
    this.seqWriter = seqWriter;
    this.keyEL = keyEL;
    keyElEval = context.createELEval("keyEl");
    elVars = context.createELVars();
    key = new Text();
    value = new Text();
    seqFile = true;
    this.idleTimeout = idleTimeout;
  }

  public Path getPath() {
    return path;
  }

  public long getExpiresOn() {
    return expires;
  }

  void setActiveRecordWriters(ActiveRecordWriters writers) {
    this.writers = writers;
  }

  void closeLock() {
    closeLock.writeLock().lock();
  }

  void closeUnlock() {
    closeLock.writeLock().unlock();
  }

  public void write(Record record) throws IdleClosedException, IOException, StageException {
    closeLock.readLock().lock();
    try {
      throwIfIdleClosed();
      if (IS_TRACE_ENABLED) {
        LOG.trace("Path[{}] - Writing ['{}']", path, record.getHeader().getSourceId());
      }
      if (generator != null) {
        generator.write(record);
      } else if (seqWriter != null) {
        RecordEL.setRecordInContext(elVars, record);
        key.set(keyElEval.eval(elVars, keyEL, String.class));
        ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
        DataGenerator dg = generatorFactory.getGenerator(baos);
        dg.write(record);
        dg.close();
        value.set(new String(baos.toByteArray(), StandardCharsets.UTF_8));
        seqWriter.append(key, value);
      } else {
        throw new IOException(Utils.format("RecordWriter '{}' is closed", path));
      }
      recordCount++;
    } finally {
      closeLock.readLock().unlock();
      // This grabs write lock, so release read lock first
      scheduleIdleClose();
    }
  }

  public void flush() throws IOException {
    closeLock.readLock().lock();
    try {
      throwIfIdleClosed();
      if (IS_TRACE_ENABLED) {
        LOG.trace("Path[{}] - Flushing", path);
      }
      if (generator != null) {
        generator.flush();
      } else if (seqWriter != null) {
        seqWriter.hflush();
      }
    } finally {
      closeLock.readLock().unlock();
    }
  }

  // due to buffering of underlying streams, the reported length may be less than the actual one up to the
  // buffer size.
  public long getLength() throws IOException {
    long length = -1;
    if (generator != null) {
      length = textOutputStream.getByteCount();
    } else if (seqWriter != null) {
      length = seqWriter.getLength();
    }
    return length;
  }

  public long getRecords() {
    return recordCount;
  }

  public void close() throws IOException {
    close(false);
  }

  private void close(boolean idleClosed) throws IOException {
    closeLock.writeLock().lock();
    if (isClosed()) {
      return;
    }
    LOG.debug("Path[{}] - Closing", path);
    try {
      if (generator != null) {
        generator.close();
      } else if (seqWriter != null) {
        seqWriter.close();
      }
      this.idleClosed = idleClosed;
      // writers can never be null, except in tests
      if (idleClosed && writers != null) {
        writers.release(this);
      }
    } finally {
      generator = null;
      seqWriter = null;
      closeLock.writeLock().unlock();
      this.idleCloseExecutor.shutdownNow();
    }
  }

  public void setIdleTimeout(long timeout) {
    this.idleTimeout = timeout;
  }

  private void throwIfIdleClosed() throws IdleClosedException {
    if (idleClosed) {
      throw new IdleClosedException(
          Utils.format("{} was closed because the file was idle for {} seconds.", path, idleTimeout));
    }
  }

  public boolean isTextFile() {
    return textFile;
  }

  public boolean isSeqFile() {
    return seqFile;
  }

  public boolean isClosed() {
    closeLock.readLock().lock();
    boolean isClosed = (generator == null && seqWriter == null);
    closeLock.readLock().unlock();
    return isClosed;
  }

  public boolean isIdleClosed() {
    return idleClosed;
  }

  private void scheduleIdleClose() {
    if (idleTimeout <= 0) {
      return;
    }
    closeLock.writeLock().lock();
    try {
      if (currentIdleCloseFuture != null && !currentIdleCloseFuture.isDone()) {
        currentIdleCloseFuture.cancel(false);
        // We don't worry about checking if it was successfully cancelled:
        // - if the other thread was already in the close method then we would not be here since both need the same lock
        // - so it is either waiting on this lock or has not run - either way, it will grab the lock after we do
        // and then IdleClosedException will get thrown.
        // Another idle close thread cannot be executing, since there is exactly one thread running idle close runnable.
      }
      if (idleCloseExecutor != null && !idleCloseExecutor.isShutdown()) {
        currentIdleCloseFuture = idleCloseExecutor.schedule(new IdleCloseCallable(), idleTimeout, TimeUnit.SECONDS);
      }
    } catch (Exception ex) {
      LOG.warn(Utils.format("Error while attempting to schedule idle closing for path {}", path));
    } finally {
      closeLock.writeLock().unlock();
    }
  }

  @Override
  public String toString() {
    return Utils.format("RecordWriter[path='{}']", path);
  }

  private class IdleCloseCallable implements Callable<Void> {

    @Override
    public Void call() {
      try {
        close(true);
      } catch (IOException e) {
        LOG.error("Error while attempting to close " + getPath().toString(), e);
      }
      return null;
    }
  }

}

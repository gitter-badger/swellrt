/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.waveprotocol.box.server.persistence.mongodb;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.WriteConcern;

import org.waveprotocol.box.common.Receiver;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.waveserver.ByteStringMessage;
import org.waveprotocol.box.server.waveserver.DeltaStore;
import org.waveprotocol.box.server.waveserver.WaveletDeltaRecord;
import org.waveprotocol.wave.federation.Proto.ProtocolAppliedWaveletDelta;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.util.logging.Log;

import java.io.IOException;
import java.util.Collection;

/**
 * A MongoDB based Delta Access implementation using a simple <b>deltas</b>
 * collection, storing a delta record per each MongoDb document.
 *
 * @author pablojan@gmail.com (Pablo Ojanguren)
 *
 */
public class MongoDbDeltaCollection implements DeltaStore.DeltasAccess {

  private static final Log LOG = Log.get(MongoDbDeltaCollection.class);


  /** Wavelet name to work with. */
  private final WaveletName waveletName;

  /** MongoDB Collection object for delta storage */
  private final DBCollection deltaDbCollection;

  /**
   * Construct a new Delta Access object for the wavelet
   *
   * @param waveletName The wavelet name.
   * @param deltaDbCollection The MongoDB deltas collection
   */
  public MongoDbDeltaCollection(WaveletName waveletName, DBCollection deltaDbCollection) {
    this.waveletName = waveletName;
    this.deltaDbCollection = deltaDbCollection;
  }

  @Override
  public WaveletName getWaveletName() {
    return waveletName;
  }

  /**
   * Create a new DBObject for a common query to select this wavelet
   *
   * @return DBObject query
   */
  protected DBObject createWaveletDBQuery() {

    DBObject query = new BasicDBObject();
    query.put(MongoDbDeltaStoreUtil.FIELD_WAVE_ID, waveletName.waveId.serialise());
    query.put(MongoDbDeltaStoreUtil.FIELD_WAVELET_ID, waveletName.waveletId.serialise());

    return query;
  }

  @Override
  public boolean isEmpty() {

    return deltaDbCollection.count(createWaveletDBQuery()) == 0;
  }

  @Override
  public HashedVersion getEndVersion() {

    // Search the max of delta.getTransformedDelta().getResultingVersion()

    DBObject query = createWaveletDBQuery();

    DBObject sort = new BasicDBObject();
    sort.put(MongoDbDeltaStoreUtil.FIELD_TRANSFORMED_RESULTINGVERSION_VERSION, -1); // Descending

    DBObject field = new BasicDBObject();
    field.put(MongoDbDeltaStoreUtil.FIELD_TRANSFORMED_RESULTINGVERSION, 1);

    DBObject result = deltaDbCollection.findOne(query, field, sort);

    return result != null ? MongoDbDeltaStoreUtil
        .deserializeHashedVersion((DBObject) ((DBObject) result
            .get(MongoDbDeltaStoreUtil.FIELD_TRANSFORMED))
            .get(MongoDbDeltaStoreUtil.FIELD_RESULTINGVERSION)) : null;
  }

  @Override
  public WaveletDeltaRecord getDelta(long version) throws IOException {

    DBObject query = createWaveletDBQuery();
    query.put(MongoDbDeltaStoreUtil.FIELD_TRANSFORMED_APPLIEDATVERSION, version);

    DBObject result = deltaDbCollection.findOne(query);

    WaveletDeltaRecord waveletDelta = null;

    if (result != null) try {
      waveletDelta = MongoDbDeltaStoreUtil.deserializeWaveletDeltaRecord(result);
    } catch (PersistenceException e) {
      throw new IOException(e);
    }
    return waveletDelta;
  }

  @Override
  public WaveletDeltaRecord getDeltaByEndVersion(long version) throws IOException {
    DBObject query = createWaveletDBQuery();
    query.put(MongoDbDeltaStoreUtil.FIELD_TRANSFORMED_RESULTINGVERSION_VERSION, version);

    DBObject result = deltaDbCollection.findOne(query);

    WaveletDeltaRecord waveletDelta = null;

    if (result != null)
    try {
      MongoDbDeltaStoreUtil.deserializeWaveletDeltaRecord(result);
    } catch (PersistenceException e) {
      throw new IOException(e);
    }
    return waveletDelta;
  }

  @Override
  public HashedVersion getAppliedAtVersion(long version) throws IOException {

    DBObject query = createWaveletDBQuery();
    query.put(MongoDbDeltaStoreUtil.FIELD_TRANSFORMED_APPLIEDATVERSION, version);

    DBObject result = deltaDbCollection.findOne(query);

    if (result != null)
      return MongoDbDeltaStoreUtil.deserializeHashedVersion((DBObject) result
          .get(MongoDbDeltaStoreUtil.FIELD_APPLIEDATVERSION));
    return null;
  }

  @Override
  public HashedVersion getResultingVersion(long version) throws IOException {
    DBObject query = createWaveletDBQuery();
    query.put(MongoDbDeltaStoreUtil.FIELD_TRANSFORMED_APPLIEDATVERSION, version);

    DBObject result = deltaDbCollection.findOne(query);

    if (result != null)
      return MongoDbDeltaStoreUtil.deserializeHashedVersion((DBObject) result
          .get(MongoDbDeltaStoreUtil.FIELD_TRANSFORMED_RESULTINGVERSION));
    return null;
  }

  @Override
  public ByteStringMessage<ProtocolAppliedWaveletDelta> getAppliedDelta(long version)
      throws IOException {

    WaveletDeltaRecord delta = getDelta(version);
    return (delta != null) ? delta.getAppliedDelta() : null;
  }

  @Override
  public TransformedWaveletDelta getTransformedDelta(long version) throws IOException {

    WaveletDeltaRecord delta = getDelta(version);
    return (delta != null) ? delta.getTransformedDelta() : null;
  }

  @Override
  public void close() throws IOException {
    // Does nothing.
  }

  @Override
  public void append(Collection<WaveletDeltaRecord> newDeltas) throws PersistenceException {

    for (WaveletDeltaRecord delta : newDeltas) {
      // Using Journaled Write Concern
      // (http://docs.mongodb.org/manual/core/write-concern/#journaled)
      deltaDbCollection.insert(MongoDbDeltaStoreUtil.serialize(delta,
          waveletName.waveId.serialise(), waveletName.waveletId.serialise()),
          WriteConcern.JOURNALED);
    }
  }

  @Override
  public long getAllDeltas(Receiver<WaveletDeltaRecord> receiver) throws IOException {

    DBObject query = createWaveletDBQuery();

    BasicDBObject sort = new BasicDBObject();
    sort.put(MongoDbDeltaStoreUtil.FIELD_TRANSFORMED_APPLIEDATVERSION, 1);
    sort.put(MongoDbDeltaStoreUtil.FIELD_TRANSFORMED + "."
        + MongoDbDeltaStoreUtil.FIELD_APPLICATIONTIMESTAMP, 1);

    DBCursor result = deltaDbCollection.find(query).sort(sort);

    long count = 0;
    HashedVersion lastResultingVersion = null;

    while (result.hasNext()) {
      DBObject obj = result.next();
      WaveletDeltaRecord delta;
      try {
        delta = MongoDbDeltaStoreUtil.deserializeWaveletDeltaRecord(obj);
      } catch (PersistenceException e) {
        throw new IOException(e);
      }

      if (lastResultingVersion != null && !delta.getAppliedAtVersion().equals(lastResultingVersion)) {
        LOG.warning("Skipping delta at v=" + delta.getAppliedAtVersion().getVersion() + " to v="
            + delta.getTransformedDelta().getAppliedAtVersion());
        continue;
      }


      if (!receiver.put(delta)) {
        throw new IllegalStateException("Error loading delta from persistence at v="
            + delta.getAppliedAtVersion().getVersion() + " to v="
            + delta.getTransformedDelta().getAppliedAtVersion());
      }

      lastResultingVersion = delta.getTransformedDelta().getResultingVersion();
      count++;
    }

    return count;
  }

  @Override
  public long getDeltasInRange(long startVersion, long endVersion,
      Receiver<WaveletDeltaRecord> receiver) throws IOException {

    BasicDBObject sort = new BasicDBObject();
    sort.put(MongoDbDeltaStoreUtil.FIELD_TRANSFORMED_APPLIEDATVERSION, 1);
    sort.put(MongoDbDeltaStoreUtil.FIELD_TRANSFORMED + "."
        + MongoDbDeltaStoreUtil.FIELD_APPLICATIONTIMESTAMP, 1);

    DBObject query = createWaveletDBQuery();
    query.put(MongoDbDeltaStoreUtil.FIELD_TRANSFORMED_APPLIEDATVERSION,
        new BasicDBObject("$gte", startVersion).append("$lte", endVersion));

    DBCursor result = deltaDbCollection.find(query).sort(sort);

    long count = 0;
    HashedVersion lastResultingVersion = null;

    while (result.hasNext()) {
      DBObject obj = result.next();
      WaveletDeltaRecord delta;
      try {
        delta = MongoDbDeltaStoreUtil.deserializeWaveletDeltaRecord(obj);
      } catch (PersistenceException e) {
        throw new IOException(e);
      }

      if (lastResultingVersion != null && !delta.getAppliedAtVersion().equals(lastResultingVersion)) {
        LOG.warning("Skipping delta at v=" + delta.getAppliedAtVersion().getVersion() + " to v="
            + delta.getTransformedDelta().getAppliedAtVersion());
        continue;
      }

      if (!receiver.put(delta)) {
        throw new IllegalStateException("Error loading delta from persistence at v="
            + delta.getAppliedAtVersion().getVersion() + " to v="
            + delta.getTransformedDelta().getAppliedAtVersion());
      }

      lastResultingVersion = delta.getTransformedDelta().getResultingVersion();
      count++;
    }

    return count;
  }
}

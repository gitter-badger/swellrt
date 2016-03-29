package org.swellrt.model.unmutable;

import org.swellrt.model.ReadableMap;
import org.swellrt.model.ReadableModel;
import org.swellrt.model.ReadableType;
import org.swellrt.model.generic.Model;
import org.swellrt.model.shared.ModelUtils;
import org.waveprotocol.wave.model.document.Document;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.ReadableBlipData;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.wave.util.logging.Log;

import java.util.Set;

/**
 * An unmutable SwellRT data model that parse all Document's into Java objects
 * at construction time.
 *
 * @author pablojan@gmail.com (Pablo Ojanguren)
 *
 */
public class UnmutableModel implements ReadableModel {

  private static final Log LOG = Log.get(UnmutableModel.class);


  private final ReadableWaveletData waveletData;

  private final Document document;

  private ReadableMap root = null;

  public static UnmutableModel create(ReadableWaveletData waveletData) {
    // Avoid trouble with old swellrt wavelets
    if (waveletData.getDocument(Model.DOC_MODEL_ROOT) == null
        || waveletData.getDocument(Model.DOC_MODEL_ROOT).getContent() == null
        || waveletData.getDocument(Model.DOC_MODEL_ROOT).getContent().getMutableDocument() == null)
      return null;

    return new UnmutableModel(waveletData);
  }

  private UnmutableModel(ReadableWaveletData waveletData) {

    // Get wavelet and root document
    this.waveletData = waveletData;
    this.document = waveletData.getDocument(Model.DOC_MODEL_ROOT).getContent().getMutableDocument();

  }

  protected Document getDocument(String documentId) {

    if (waveletData.getDocument(documentId) == null
        || waveletData.getDocument(documentId).getContent() == null) {
      LOG.info(ModelUtils.serialize(this.getWaveId())
          + " wavelet doesn't have content document for blip " + documentId);
      return null;
    }

    return waveletData.getDocument(documentId).getContent().getMutableDocument();
  }

  protected ReadableBlipData getBlipData(String documentId) {
    return waveletData.getDocument(documentId);
  }


  @Override
  public WaveId getWaveId() {
    return waveletData.getWaveId();
  }

  @Override
  public WaveletId getWaveletId() {
    return waveletData.getWaveletId();
  }

  @Override
  public Set<ParticipantId> getParticipants() {
    return waveletData.getParticipants();
  }

  @Override
  public ReadableMap getRoot() {

    if (root == null)
      root = (ReadableMap) UnmutableTypeFactory.deserialize(this, null, Model.DOC_MAP_ROOT);

    return root;
  }

  @Override
  public ReadableType fromPath(String path) {
    return ModelUtils.fromPath(this, path);
  }


}

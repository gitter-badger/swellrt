package org.swellrt.model.generic;

import com.google.common.base.Preconditions;

import org.swellrt.model.shared.ModelUtils;
import org.waveprotocol.wave.model.document.Doc;
import org.waveprotocol.wave.model.document.Document;
import org.waveprotocol.wave.model.document.ObservableDocument;
import org.waveprotocol.wave.model.document.operation.impl.AttributesImpl;
import org.waveprotocol.wave.model.document.util.DocHelper;
import org.waveprotocol.wave.model.document.util.XmlStringBuilder;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.wave.Blip;
import org.waveprotocol.wave.model.wave.ObservableWavelet;
import org.waveprotocol.wave.model.wave.opbased.ObservableWaveView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

public class ModelMigrator {

  private static final Logger LOG = Logger.getLogger(ModelMigrator.class.getName());

  public static final String PARTICIPANT_SYSTEM = "_system_";

  public static final String WAVELET_ROOT = "swl+root";
  public static final String TAG_MODEL_METADATA = "model";
  public static final String ATTR_MODEL_VERSION = "v";

  public static final String DOC_MODEL_ROOT = "model+root";
  public static final String DOC_MODEL_VALUES = "model+values";
  public static final String DOC_MAP_ROOT = "map+root";

  @Deprecated
  public static final String TAG_STRINGS = "strings";
  public static final String TAG_MAP = "map";
  public static final String TAG_LIST = "list";
  public static final String TAG_VALUES = "values";
  public static final String TAG_VALUES_ITEM = "i";
  public static final String ATTR_VALUE = "v";



  public static final VersionNumber LAST_VERSION = new VersionNumber(1, 0);

  public static class VersionNumber {

    private int major;
    private int minor;

    public static VersionNumber fromString(String str) {
      String[] numbers = str.split("\\.");

      if (numbers == null || numbers.length != 2) return null;

      try {

        int major = Integer.valueOf(numbers[0]);
        int minor = Integer.valueOf(numbers[1]);

        return new VersionNumber(major, minor);

      } catch (NumberFormatException e) {
        return null;
      }

    }


    public VersionNumber(int major, int minor) {
      super();
      this.major = major;
      this.minor = minor;
    }


    public int getMajor() {
      return major;
    }

    public int getMinor() {
      return minor;
    }

    @Override
    public boolean equals(Object obj) {

      if (obj instanceof VersionNumber) {
        VersionNumber vn = (VersionNumber) obj;
        return (vn.major == this.major) && (vn.minor == this.minor);
      }

      return false;
    }

  }


  /**
   * Calculate model's version according to metadata
   *
   *
   * @param domain
   * @param wave
   * @return
   * @throws NotModelWaveException
   */
  protected static VersionNumber getVersionNumber(String domain, ObservableWaveView wave)
      throws NotModelWaveException {


    WaveletId rootWaveletId = WaveletId.of(domain, WAVELET_ROOT);
    ObservableWavelet rootWavelet = wave.getWavelet(rootWaveletId);

    if (rootWavelet == null) {
      throw new NotModelWaveException();
    }

    ObservableDocument docModelRoot = null;
    if (rootWavelet.getDocumentIds().contains(DOC_MODEL_ROOT)) {
      docModelRoot = rootWavelet.getDocument(DOC_MODEL_ROOT);
    } else {
      throw new NotModelWaveException();
    }

    Doc.E eltModelMetadata = DocHelper.getElementWithTagName(docModelRoot, TAG_MODEL_METADATA);
    String attrVersion = docModelRoot.getAttribute(eltModelMetadata, ATTR_MODEL_VERSION);


    return VersionNumber.fromString(attrVersion);
  }

  /**
   * Check if a Wave inner model needs to be migrated and execute incremental
   * migration process.
   *
   * @param domain Domain of this Wave
   * @param wave Wave to migrate
   * @throws NotModelWaveException
   * @return true if migration was succesfull
   */
  public static boolean migrateIfNecessary(String domain, ObservableWaveView wave) {

    VersionNumber currentVersion;
    try {

      currentVersion = getVersionNumber(domain, wave);


      if (currentVersion.equals(new VersionNumber(0, 2))) {
        migrate_0_2_to_1_0(domain, wave);
        currentVersion = new VersionNumber(1, 0);
      }

      if (currentVersion.equals(new VersionNumber(1, 0))) {
        // Here, migration step from 1.0 to X.Y
      }

    } catch (NotModelWaveException e) {
      // TODO Log exception
      return false;
    }

    return true;
  }


  /**
   * Migrate data model from v0.2 to v1.0
   *
   * @param domain
   * @param wave
   */
  protected static void migrate_0_2_to_1_0(String domain, ObservableWaveView wave) {

    WaveletId rootWaveletId = WaveletId.of(domain, WAVELET_ROOT);
    ObservableWavelet rootWavelet = wave.getWavelet(rootWaveletId);

    Preconditions.checkArgument(rootWavelet != null, "Wavelet root not found");

    // --------------------------------------------------------
    // 1. Move root map from model+root doc to new map+root doc
    // --------------------------------------------------------

    // The model+root
    Document docModelRoot = rootWavelet.getDocument(DOC_MODEL_ROOT);

    // New map+root blip
    Blip blipMapRoot = rootWavelet.createBlip(DOC_MAP_ROOT);
    Document docMapRoot = blipMapRoot.getContent();

    // Move map's XML to map+root
    Doc.E eltOriginalMap = DocHelper.getElementWithTagName(docModelRoot, TAG_MAP);
    XmlStringBuilder xmlMapRoot = XmlStringBuilder.createChildren(docModelRoot, eltOriginalMap);
    xmlMapRoot.wrap(TAG_MAP);
    docMapRoot.appendXml(xmlMapRoot);

    // Delete former XML
    docModelRoot.deleteNode(eltOriginalMap);

    // --------------------------------------------------------
    // 2. Add new metadata to model, update model version
    // --------------------------------------------------------
    Doc.E eltModel = DocHelper.getElementWithTagName(docModelRoot, TAG_MODEL_METADATA);
    docModelRoot.setElementAttribute(eltModel, "v", "1.0");
    docModelRoot.setElementAttribute(eltModel, "t", "default");
    docModelRoot.setElementAttribute(eltModel, "a", "default");

    // --------------------------------------------------------
    // 3. Trasverse data tree:
    // - add metadata to each blip
    // - move primitive values to blips
    // --------------------------------------------------------

    // Put all original string values in an array
    ArrayList<String> values = new ArrayList<String>();

    Doc.E eltValuesList = DocHelper.getElementWithTagName(docModelRoot, TAG_STRINGS);

    Doc.E valueIndexItem = DocHelper.getFirstChildElement(docModelRoot, eltValuesList);

    while (valueIndexItem != null) {

      if (docModelRoot.getTagName(valueIndexItem).equals("s")) {
        // TODO be careful, we suppose xml items are ordered as array's index
        String v = docModelRoot.getAttribute(valueIndexItem, "v");
        values.add(v);
      }

      valueIndexItem = DocHelper.getNextSiblingElement(docModelRoot, valueIndexItem); // Next
    }

    LOG.info("Start migration - wave " + ModelUtils.serialize(wave.getWaveId()));

    // Go into the tree
    processBlip(domain, blipMapRoot, values, "root", rootWavelet);


    // Delete old string index
    docModelRoot.deleteNode(eltValuesList);

    LOG.info("Stop migration - wave " + ModelUtils.serialize(wave.getWaveId()));
  }

  /**
   * Add metadata to this blip and change value references by actual value
   *
   */
  private static void processBlip(String domain, Blip blip, List<String> values, String path,
      ObservableWavelet wavelet) {

    LOG.info("Processing blip " + blip.getId() + " with path " + path);

    if (blip.getId().startsWith("map")) {

      addMetadata(domain, blip, path);
      processMap(domain, values, blip, path, wavelet);

    } else if (blip.getId().startsWith("list")) {

      addMetadata(domain, blip, path);
      processList(domain, values, blip, path, wavelet);

    } else if (blip.getId().startsWith("b")) {
      addMetadataDoc(blip, path);
    }

  }

  /**
   * Add a metadata section in a Blip.
   *
   *
   */
  private static void addMetadata(String domain, Blip blip, String path) {
    Date now = new Date();
    String timestamp = String.valueOf(now.getTime());

    // Set the actual creator of the wavelet to keep consistency
    String pc = blip.getAuthorId().getAddress();
    String tc = String.valueOf(blip.getLastModifiedTime());

    String pm = PARTICIPANT_SYSTEM + "@" + domain;
    String tm = timestamp;

    String p = path;

    String acl = "";
    String ap = "default";

    String xml =
        "<metadata p='" + p + "' pc='" + pc + "' tc='" + tc + "' pm='" + pm + "' tm='" + tm
            + "' acl='" + acl + "' ap='" + ap + "'></metadata>";

    blip.getContent().insertXml(blip.getContent().locate(0),
        XmlStringBuilder.createFromXmlString(xml));
  }

  private static void addMetadataDoc(Blip blip, String path) {
    Doc.E bodyElement = DocHelper.getElementWithTagName(blip.getContent(), "body");
    if (bodyElement != null) {
      blip.getContent().setElementAttributes(bodyElement,
          AttributesImpl.fromUnsortedPairsUnchecked("p", path, "acl", "", "ap", "default"));
    }
  }

  private static int addValue(Document document, Doc.E elementValues, String value) {

    // Get last <i v="" /> element
    Doc.E item = DocHelper.getFirstChildElement(document, elementValues);
    int index = -1;

    while (item != null) {
      index++;
      item = DocHelper.getNextSiblingElement(document, item);
    }

    document.createChildElement(elementValues, TAG_VALUES_ITEM,
        Collections.<String, String> singletonMap(ATTR_VALUE, value));

    return index + 1;
  }

  /**
   *
   *
   */
  private static void processMap(String domain, List<String> values, Blip blip, String path,
      ObservableWavelet wavelet) {

    // Create <values> container section
    Doc.E eltValues =
        blip.getContent().createChildElement(blip.getContent().getDocumentElement(), TAG_VALUES,
        Collections.<String, String> emptyMap());



    // The <map> container already exists
    Doc.E eltMap = DocHelper.getElementWithTagName(blip.getContent(), TAG_MAP);
    Doc.E eltMapEntry = DocHelper.getFirstChildElement(blip.getContent(), eltMap);
    while (eltMapEntry != null) {

      String k = blip.getContent().getAttribute(eltMapEntry, "k");
      String v = blip.getContent().getAttribute(eltMapEntry, "v");
      if (v.startsWith("str+")) {
        int index = Integer.valueOf(v.substring(4));
        String value = values.get(index);

        // Store value in the <values> section
        int valueIndex = addValue(blip.getContent(), eltValues, value);

        // Store value ref in the map
        blip.getContent().setElementAttribute(eltMapEntry, "v", "str+" + valueIndex);

      } else {
        // go into
        String childPath = path + "." + k;
        Blip childBlip = wavelet.getBlip(v);
        processBlip(domain, childBlip, values, childPath, wavelet);

      }

      eltMapEntry = DocHelper.getNextSiblingElement(blip.getContent(), eltMapEntry);
    }

  }

  private static void processList(String domain, List<String> values, Blip blip, String path,
      ObservableWavelet wavelet) {

    // Create <values> container section
    Doc.E eltValues =
        blip.getContent().createChildElement(blip.getContent().getDocumentElement(), TAG_VALUES,
            Collections.<String, String> emptyMap());

    Doc.E eltList = DocHelper.getElementWithTagName(blip.getContent(), TAG_LIST);
    Doc.E eltListEntry = DocHelper.getFirstChildElement(blip.getContent(), eltList);

    int i = 0;
    while (eltListEntry != null) {
      String r = blip.getContent().getAttribute(eltListEntry, "r");
      if (r.startsWith("str+")) {
        int index = Integer.valueOf(r.substring(4));

        String value = "null";
        // Check index range to avoid inconsistency
        if (0 <= index && index < values.size())
          value = values.get(index);
        else
          LOG.info("Index out of bounds " + index + " in blip " + blip.getId());

        // Store value in the <values> section
        int valueIndex = addValue(blip.getContent(), eltValues, value);

        // Store value ref in the list
        blip.getContent().setElementAttribute(eltListEntry, "r", "str+" + valueIndex);
        blip.getContent().setElementAttribute(eltListEntry, "t", "str");

      } else {

        // go into
        String childPath = path + "." + i;
        Blip childBlip = wavelet.getBlip(r);
        processBlip(domain, childBlip, values, childPath, wavelet);

      }

      eltListEntry = DocHelper.getNextSiblingElement(blip.getContent(), eltListEntry);
      i++;
    }

  }


}

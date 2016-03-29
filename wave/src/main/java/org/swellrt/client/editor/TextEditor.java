package org.swellrt.client.editor;

import com.google.common.base.Preconditions;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;

import org.swellrt.client.editor.doodad.ExternalAnnotationHandler;
import org.swellrt.client.editor.doodad.WidgetController;
import org.swellrt.client.editor.doodad.WidgetDoodad;
import org.swellrt.client.editor.doodad.WidgetModelDoodad;
import org.swellrt.model.generic.Model;
import org.swellrt.model.generic.TextType;
import org.swellrt.model.shared.ModelUtils;
import org.waveprotocol.wave.client.common.util.JsoStringMap;
import org.waveprotocol.wave.client.common.util.LogicalPanel;
import org.waveprotocol.wave.client.doodad.diff.DiffAnnotationHandler;
import org.waveprotocol.wave.client.doodad.diff.DiffDeleteRenderer;
import org.waveprotocol.wave.client.doodad.link.LinkAnnotationHandler;
import org.waveprotocol.wave.client.doodad.link.LinkAnnotationHandler.LinkAttributeAugmenter;
import org.waveprotocol.wave.client.editor.Editor;
import org.waveprotocol.wave.client.editor.EditorSettings;
import org.waveprotocol.wave.client.editor.EditorStaticDeps;
import org.waveprotocol.wave.client.editor.EditorUpdateEvent;
import org.waveprotocol.wave.client.editor.EditorUpdateEvent.EditorUpdateListener;
import org.waveprotocol.wave.client.editor.Editors;
import org.waveprotocol.wave.client.editor.content.ContentDocument;
import org.waveprotocol.wave.client.editor.content.ContentNode;
import org.waveprotocol.wave.client.editor.content.Registries;
import org.waveprotocol.wave.client.editor.content.misc.StyleAnnotationHandler;
import org.waveprotocol.wave.client.editor.content.paragraph.LineRendering;
import org.waveprotocol.wave.client.editor.keys.KeyBindingRegistry;
import org.waveprotocol.wave.client.editor.util.EditorAnnotationUtil;
import org.waveprotocol.wave.client.wave.InteractiveDocument;
import org.waveprotocol.wave.client.wave.RegistriesHolder;
import org.waveprotocol.wave.client.wave.WaveDocuments;
import org.waveprotocol.wave.client.widget.popup.PopupChrome;
import org.waveprotocol.wave.client.widget.popup.PopupChromeProvider;
import org.waveprotocol.wave.client.widget.popup.simple.Popup;
import org.waveprotocol.wave.common.logging.AbstractLogger;
import org.waveprotocol.wave.common.logging.AbstractLogger.Level;
import org.waveprotocol.wave.common.logging.LogSink;
import org.waveprotocol.wave.model.conversation.AnnotationConstants;
import org.waveprotocol.wave.model.document.util.LineContainers;
import org.waveprotocol.wave.model.document.util.Point;
import org.waveprotocol.wave.model.document.util.Range;
import org.waveprotocol.wave.model.document.util.XmlStringBuilder;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.util.ReadableStringSet;
import org.waveprotocol.wave.model.util.ReadableStringSet.Proc;

import java.util.HashMap;
import java.util.Map;

/**
 * A wrapper of the original Wave {@link Editor} to be integrated in the SwellRT
 * Web API. Users can edit TextType instances in this editor.
 *
 * @author pablojan
 *
 */
public class TextEditor implements EditorUpdateListener {


  public static class JSLogSink extends LogSink {

    private native void console(String msg) /*-{
      console.log(msg);
    }-*/;

    @Override
    public void log(Level level, String message) {
      console("[" + level.name() + "] " + message);
    }

    @Override
    public void lazyLog(Level level, Object... messages) {
      for (Object o : messages) {
        log(level, o.toString());
      }

    }

  }


  public static class CustomLogger extends AbstractLogger {

    public CustomLogger(LogSink sink) {
      super(sink);
    }

    @Override
    public boolean isModuleEnabled() {
      return true;
    }

    @Override
    protected boolean shouldLog(Level level) {
      return true;
    }
  }

  private static final String TOPLEVEL_CONTAINER_TAGNAME = "body";

  static {
    Editors.initRootRegistries();
    LineContainers.setTopLevelContainerTagname(TOPLEVEL_CONTAINER_TAGNAME);
    // Uncomment to show logs in browser's console
    // EditorStaticDeps.logger = new CustomLogger(new JSLogSink());
  }


  private static final EditorSettings EDITOR_SETTINGS = new EditorSettings()
      .setHasDebugDialog(true).setUndoEnabled(true).setUseFancyCursorBias(true)
      .setUseSemanticCopyPaste(false).setUseWhitelistInEditor(false)
      .setUseWebkitCompositionEvents(true);

  // Wave Editor specific

  private final Registries registries = RegistriesHolder.get();
  private final KeyBindingRegistry KEY_REGISTRY = new KeyBindingRegistry();


  private final LogicalPanel.Impl editorPanel;
  private LogicalPanel.Impl docPanel;
  private ContentDocument doc;


  /**
   * The gateway to get UI-versions of Blips. Registry is GWT related, so must
   * be injected in the Editor by the JS API. Model's classes have to ignore it.
   */
  private WaveDocuments<? extends InteractiveDocument> documentRegistry;

  private Editor editor;

  private static final ReadableStringSet ANNOTATIONS = CollectionUtils.newStringSet(
      AnnotationConstants.STYLE_BG_COLOR,
      AnnotationConstants.STYLE_COLOR,
      AnnotationConstants.STYLE_FONT_FAMILY,
      AnnotationConstants.STYLE_FONT_SIZE,
      AnnotationConstants.STYLE_FONT_STYLE,
      AnnotationConstants.STYLE_FONT_WEIGHT,
      AnnotationConstants.STYLE_TEXT_DECORATION,
      AnnotationConstants.STYLE_VERTICAL_ALIGN,
      AnnotationConstants.LINK_AUTO,
      AnnotationConstants.LINK_MANUAL
    );

  private TextEditorListener listener;

  /**
   * Registry of JavaScript controllers for each Widget type
   */
  private final Map<String, WidgetController> widgetRegistry =
      new HashMap<String, WidgetController>();


  {
    EditorStaticDeps.setPopupProvider(Popup.LIGHTWEIGHT_POPUP_PROVIDER);
    EditorStaticDeps.setPopupChromeProvider(new PopupChromeProvider() {
      public PopupChrome createPopupChrome() {
        return null;
      }
    });


  }

  public static TextEditor create(String containerElementId) {

    Element e = Document.get().getElementById(containerElementId);

    Preconditions.checkNotNull(e, "Editor's parent element doesn't exist");

    TextEditor editor = new TextEditor(e);
    return editor;
  }

  protected TextEditor(final Element containerElement) {
    this.editorPanel = new LogicalPanel.Impl() {
      {
        setElement(containerElement);
      }
    };
  }



  /**
   * Inject document registry which manages UI versions of blips. Registry must
   * be only injected by the JS API.
   *
   * @param documentRegistry
   */
  public void setDocumentRegistry(WaveDocuments<? extends InteractiveDocument> documentRegistry) {
    this.documentRegistry = documentRegistry;
  }


  public void edit(TextType text) {
    Preconditions.checkNotNull(text, "Text object is null");
    Preconditions.checkNotNull(documentRegistry, "Document registry hasn't been initialized");

    if (!isClean()) cleanUp();

    // TODO don't register again on every new editor
    registerDoodads(text.getModel());

    doc = getContentDocument(text);
    Preconditions.checkArgument(doc != null, "Can't edit an unattached TextType");

    doc.setRegistries(registries);

    this.docPanel = new LogicalPanel.Impl() {
      {
        setElement(Document.get().createDivElement());
      }
    };


    doc.setInteractive(docPanel);

    // Append the doc panel to the provided container panel
    editorPanel.getElement().appendChild(
        doc.getFullContentView().getDocumentElement().getImplNodelet());

    if (editor == null) {
      editor = Editors.attachTo(doc);
      editor.init(null, KEY_REGISTRY, EditorSettings.DEFAULT);
    } else {
      // Reuse exsiting editor.
      if (editor.hasDocument()) {
        editor.removeContentAndUnrender();
        editor.reset();
      }
      editor.setContent(doc);
      editor.init(null, KEY_REGISTRY, EDITOR_SETTINGS);
    }

    editor.addUpdateListener(this);
    // editor.addKeySignalListener(parentPanel);

    editor.setEditing(true);
    editor.focus(true);

  }



  /* ---------------------------------------------------------- */



  private ContentDocument getContentDocument(TextType text) {
    Preconditions.checkArgument(text != null,
        "Unable to get ContentDocument from null TextType");
    Preconditions.checkArgument(documentRegistry != null,
        "Unable to get ContentDocument from null DocumentRegistry");


    return documentRegistry.getBlipDocument(ModelUtils.serialize(text.getModel().getWaveletId()),
        text.getDocumentId()).getDocument();
  }






  public void setEditing(boolean isEditing) {
    if (editor != null && editor.hasDocument()) {
      if (editor.isEditing() != isEditing) editor.setEditing(isEditing);
    }
  }

  public void cleanUp() {
    if (editor != null) {
      editor.removeUpdateListener(this);
      editor.removeContentAndUnrender();
      editor.reset();
      doc = null;
    }
  }

  protected boolean isClean() {
    return doc == null;
  }

  public void toggleDebug() {
    editor.debugToggleDebugDialog();
  }

  /**
   * Register a Widget controller for this editor. Widgets must be registered
   * BEFORE {@link TextEditor#edit(TextType)} is called.
   *
   *
   * @param name
   * @param controller
   */
  public void registerWidget(String name, WidgetController controller) {
    widgetRegistry.put(name, controller);
  }


  /**
   * Insert a Widget at the current cursor position or at the end iff the type
   * is registered.
   *
   * @param type
   * @param state
   */
  public void addWidget(String type, String state) {

    if (!widgetRegistry.containsKey(type)) return;

    Point<ContentNode> currentPoint = null;

    if (editor.getSelectionHelper().getOrderedSelectionPoints() != null)
      currentPoint = editor.getSelectionHelper().getOrderedSelectionPoints().getFirst();

    XmlStringBuilder xml = XmlStringBuilder.createFromXmlString("<widget type='" + type + "' state='" + state
        + "' />");

    if (currentPoint != null) {
      editor.getContent().getMutableDoc().insertXml(currentPoint, xml);
    } else {
      editor.getContent().getMutableDoc().appendXml(xml);
      editor.flushSaveSelection();
    }

  }

  /**
   * Insert a Model supported Widget at the current cursor position iff the type
   * is registered.
   *
   * The state of the Widget is provided in a subtree of the collaborative data
   * model containing the current text;
   *
   * @param type
   * @param dataModelPath a path to object in the data model. e.g.
   *        root.cities.newyork
   */
  public void addModelWidget(String type, String dataModelPath) {

    if (!widgetRegistry.containsKey(type)) return;

    Point<ContentNode> currentPoint = null;

    if (editor.getSelectionHelper().getOrderedSelectionPoints() != null)
      currentPoint = editor.getSelectionHelper().getOrderedSelectionPoints().getFirst();

    XmlStringBuilder xml =
        XmlStringBuilder.createFromXmlString("<widget-model type='" + type + "' path='"
            + dataModelPath + "' />");

    if (currentPoint != null) {
      editor.getContent().getMutableDoc().insertXml(currentPoint, xml);
    } else {
      editor.getContent().getMutableDoc().appendXml(xml);
      editor.flushSaveSelection();
    }
  }

  protected void registerDoodads(Model model) {


    // TOPLEVEL_CONTAINER_TAGNAME
    LineRendering.registerContainer(TOPLEVEL_CONTAINER_TAGNAME,
        registries.getElementHandlerRegistry());

    StyleAnnotationHandler.register(registries);

    // Listen for Diff annotations to paint new content or to insert a
    // delete-content tag
    // to be rendered by the DiffDeleteRendere
    DiffAnnotationHandler.register(registries.getAnnotationHandlerRegistry(),
        registries.getPaintRegistry());
    DiffDeleteRenderer.register(registries.getElementHandlerRegistry());

    LinkAnnotationHandler.register(registries, new LinkAttributeAugmenter() {
      @Override
      public Map<String, String> augment(Map<String, Object> annotations, boolean isEditing,
          Map<String, String> current) {
        return current;
      }
    });

    ExternalAnnotationHandler.register(registries);

    // Add additional doodas here
    WidgetDoodad.register(registries.getElementHandlerRegistry(), widgetRegistry);
    WidgetModelDoodad.register(registries.getElementHandlerRegistry(), widgetRegistry,
        model);

  }


  public void setListener(TextEditorListener listener) {
    this.listener = listener;
  }


  public void setAnnotation(final String annotationName, final String annotationValue) {

    if (editor.getSelectionHelper().getSelectionRange() != null)
      EditorAnnotationUtil.setAnnotationOverSelection(editor, annotationName, annotationValue);

  }


  private native void log(String s) /*-{
    console.log(s);
  }-*/;

  @Override
  public void onUpdate(final EditorUpdateEvent event) {


    if (event.selectionLocationChanged()) {

      // Notify to editor's listener which annotations are in the current
      // selection.
      //
      final Range range = editor.getSelectionHelper().getOrderedSelectionRange();

      if (range != null && listener != null) {


        final JsoStringMap<String> annotationSnapshot = JsoStringMap.<String> create();

        ANNOTATIONS.each(new Proc() {

          @Override
          public void apply(String annotationName) {

            String annotationValue =
                EditorAnnotationUtil.getAnnotationOverRangeIfFull(event.context().getDocument(),
                    editor.getCaretAnnotations(), annotationName, range.getStart(), range.getEnd());

            annotationSnapshot.put(annotationName, annotationValue);

          }

        });


        listener.onSelectionChange(range.getStart(), range.getEnd(), annotationSnapshot);

      }
    }

  }

}

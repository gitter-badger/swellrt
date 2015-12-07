package org.swellrt.api;

import com.google.common.base.Preconditions;
import com.google.gwt.core.client.Callback;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsonUtils;
import com.google.gwt.dom.client.Document;
import com.google.gwt.event.shared.UmbrellaException;
import com.google.gwt.http.client.RequestException;

import org.swellrt.api.js.WaveClientJS;
import org.swellrt.api.js.editor.TextEditorJS;
import org.swellrt.api.js.generic.ModelJS;
import org.swellrt.client.WaveWrapper;
import org.swellrt.client.editor.TextEditor;
import org.swellrt.model.generic.Model;
import org.swellrt.model.generic.TextType;
import org.waveprotocol.wave.client.wave.InteractiveDocument;
import org.waveprotocol.wave.client.wave.WaveDocuments;
import org.waveprotocol.wave.concurrencycontrol.common.UnsavedDataListener.UnsavedDataInfo;

/**
 * SwellRT client API entrypoint
 *
 * @author Pablo Ojanguren (pablojan@gmail.com)
 *
 *
 */
public class WaveClient implements SwellRT.Listener {

  private final SwellRT coreClient;
  private static WaveClientJS jsClient = null; // TODO why static?


  protected static WaveClient create(SwellRT coreClient) {

    WaveClient waveClient = new WaveClient(coreClient);
    coreClient.attachListener(waveClient);
    jsClient = WaveClientJS.create(waveClient);
    return waveClient;

  }

  private WaveClient(SwellRT swell) {
    this.coreClient = swell;
  }


  private native void invoke(JavaScriptObject object, String method, Object arg) /*-{
     object[method](arg);
  }-*/;

  //
  // Session
  //

  /**
   * Create a new Wave user.
   *
   * @param host The server hosting the user: http(s)://server.com
   * @param username user address including domain part: username@server.com
   * @param password the user password
   * @param callback
   * @throws RequestException
   */
  public void registerUser(String host, String username, String password,
      final JavaScriptObject callback) throws RequestException {


      coreClient.registerUser(host, username, password, new Callback<String, String>() {

        @Override
        public void onSuccess(String result) {
          invoke(callback, WaveClientJS.SUCCESS, result);
        }

        @Override
        public void onFailure(String reason) {
          invoke(callback, WaveClientJS.FAILURE, reason);
        }
      });

  }


  /**
   * Start a Wave session
   *
   * @param url
   * @param user
   * @param password
   * @throws RequestException
   */
  public void startSession(String url, String user, String password, final JavaScriptObject callback)
      throws RequestException {


    coreClient.startSession(user, password, url, new Callback<JavaScriptObject, String>() {

        @Override
        public void onSuccess(JavaScriptObject result) {
          invoke(callback, WaveClientJS.SUCCESS, result);
        }

        @Override
        public void onFailure(String reason) {
          invoke(callback, WaveClientJS.FAILURE, reason);
        }
      });

  }


  /**
   * Stops the WaveSession. No callback needed.
   *
   * @return
   * @throws SessionNotStartedException
   */
  public void stopSession() throws SessionNotStartedException {
    coreClient.stopSession();
  }

  //
  // Data model
  //

  /**
   * Close a data model. No callback needed.
   *
   * @param waveId
   * @return true for success
   * @throws InvalidIdException
   * @throws SessionNotStartedException
   */
  public void closeModel(String waveId) throws InvalidIdException, SessionNotStartedException {
    coreClient.closeWave(waveId);
  }


  /**
   * Create a new data model.
   *
   * @return the new data model Id.
   * @throws NetworkException
   * @throws SessionNotStartedException
   */
  public String createModel(final JavaScriptObject callback) throws NetworkException,
      SessionNotStartedException {

    String waveId = null;

    waveId = coreClient.createWave(new OnLoadCallback<WaveWrapper>() {
        @Override
      public void onLoad(WaveWrapper wrapper) {

          ModelJS modelJS = null;

          Model model =
                Model.create(wrapper.getWave(), wrapper.getLocalDomain(),
                    wrapper.getLoggedInUser(),
                  wrapper.isNewWave(), wrapper.getIdGenerator());

            modelJS = ModelJS.create(model);
            model.addListener(modelJS);


        invoke(callback, WaveClientJS.READY, modelJS);
        }

      @Override
      public void onError(String reason) {
        invoke(callback, WaveClientJS.FAILURE, reason);
      }
      });

    return waveId;

  }

  /**
   * Open a data model.
   *
   * @return the new data model Id.
   * @throws InvalidIdException
   * @throws NetworkException
   * @throws SessionNotStartedException
   * @throws RequestException
   */
  public String openModel(String waveId, final JavaScriptObject callback)
      throws InvalidIdException, NetworkException, SessionNotStartedException, RequestException {

    String modelId = null;

    modelId = coreClient.openWave(waveId, new OnLoadCallback<WaveWrapper>() {

        @Override
      public void onLoad(WaveWrapper wrapper) {

          ModelJS modelJS = null;

          Model model =
                Model.create(wrapper.getWave(), wrapper.getLocalDomain(),
                    wrapper.getLoggedInUser(), wrapper.isNewWave(), wrapper.getIdGenerator());

            modelJS = ModelJS.create(model);
            model.addListener(modelJS);

            invoke(callback, WaveClientJS.READY, modelJS);
        }

      @Override
      public void onError(String reason) {
        invoke(callback, WaveClientJS.FAILURE, reason);
      }

      });

    return modelId;

  }


  public void query(String expr, String projExpr, String aggrExpr, final JavaScriptObject callback)
      throws RequestException, SessionNotStartedException {
    coreClient.query(expr, projExpr, aggrExpr, new Callback<String, String>() {

      @Override
      public void onFailure(String reason) {
        invoke(callback, WaveClientJS.FAILURE, reason);
      }

      @Override
      public void onSuccess(String result) {
        invoke(callback, WaveClientJS.SUCCESS, JsonUtils.unsafeEval(result));
      }
    });
  }


  public JavaScriptObject avatar(JsArray<AvatarParameter> parameters, AvatarOptions options) {
    return AvatarService.getInstance().getAvatar(parameters, options);
  }


  public TextEditorJS getTextEditor(String elementId) {
    Preconditions.checkArgument(Document.get().getElementById(elementId) != null,
        "Element id is not provided");

    TextEditor textEditor = TextEditor.create();
    textEditor.setElement(elementId);
    return TextEditorJS.create(textEditor, this);
  }

  /**
   * Set TextEditor dependencies from current wave/model. In particular, set the
   * document registry associated with TextType's Model before editing.
   *
   * @param text
   */
  public void configureTextEditor(TextEditor editor, TextType text) {
    WaveDocuments<? extends InteractiveDocument> documentRegistry =
        coreClient.getDocumentRegistry(text.getModel());

    editor.setDocumentRegistry(documentRegistry);
    editor.setModel(text.getModel());
  }

  /**
   * Register a {@paramref deviceId} where the current user will receive
   * notifications
   *
   * @param deviceId
   */
  public void notificationRegister(String deviceId) {

  }

  /**
   * Unregister the {@paramref deviceId} from current user's notifications
   *
   * @param deviceId
   */
  public void notificationUnregister(String deviceId) {

  }

  /**
   * Subscribe current user to notifications of the wave identified by
   * {@paramref waveId}
   *
   * @param waveId
   */
  public void notificationSubscribe(String waveId) {

  }

  /**
   * Unubscribe current user from notifications of the wave identified by
   * {@paramref waveId}
   *
   * @param waveId
   */
  public void notificationUnsubscribe(String waveId) {

  }

  /**
   * Enable/disable WebSockets transport. Alternative protocol is long-polling.
   *
   * @param enabled
   */
  public void useWebSocket(boolean enabled) {
    coreClient.useWebSocket(enabled);
  }


  @Override
  public void onDataStatusChanged(UnsavedDataInfo dataInfo) {

    JavaScriptObject payload = JavaScriptObject.createObject();

    SwellRTUtils.addField(payload, "inFlightSize", dataInfo.inFlightSize());
    SwellRTUtils.addField(payload, "uncommittedSize", dataInfo.estimateUncommittedSize());
    SwellRTUtils.addField(payload, "unacknowledgedSize", dataInfo.estimateUnacknowledgedSize());
    SwellRTUtils.addField(payload, "lastAckVersion", dataInfo.laskAckVersion());
    SwellRTUtils.addField(payload, "lastCommitVersion", dataInfo.lastCommitVersion());

    jsClient.triggerEvent(WaveClientJS.DATA_STATUS_CHANGED, payload);
  }

  @Override
  public void onNetworkDisconnected(String cause) {

    JavaScriptObject payload = JavaScriptObject.createObject();
    SwellRTUtils.addField(payload, "cause", cause);
    jsClient.triggerEvent(WaveClientJS.NETWORK_DISCONNECTED, payload);
  }

  @Override
  public void onNetworkConnected() {

    JavaScriptObject payload = JavaScriptObject.createObject();
    jsClient.triggerEvent(WaveClientJS.NETWORK_CONNECTED, payload);
  }

  @Override
  public void onNetworkClosed(boolean everythingCommitted) {

    JavaScriptObject payload = JavaScriptObject.createObject();
    SwellRTUtils.addField(payload, "everythingCommitted", everythingCommitted);

    jsClient.triggerEvent(WaveClientJS.NETWORK_CLOSED, payload);
  }

  @Override
  public void onException(String cause) {
    JavaScriptObject payload = JavaScriptObject.createObject();
    SwellRTUtils.addField(payload, "cause", cause);

    jsClient.triggerEvent(WaveClientJS.FATAL_EXCEPTION, payload);
  }


  private static native void callOnSwellRTReady() /*-{

    if (typeof $wnd.onSwellRTReady === "function")
    $wnd.onSwellRTReady();

  }-*/;

  @Override
  public void onReady() {
    callOnSwellRTReady();
  }

  /**
   * A central point to wrap Java exceptions and rethrow them as meaningful
   * JavaScript exceptions for API clients.
   *
   * WaveClientJS must wrap all WaveClient calls in a try-catch block and
   * capture and pass exceptions to this method.
   *
   *
   * @param e The Java native exception
   * @return the JavaScript exception as String code
   */
  public static final String wrapJavaException(Object e) {

    if (e instanceof Throwable) {
        GWT.log("Exception: " +  ((Throwable) e).getMessage());
    }

    String exceptionCode = "UNWRAPPED_EXCEPTION";

    if (e instanceof InvalidIdException)
      exceptionCode = "INVALID_ID_EXCEPTION";
    else if (e instanceof SessionNotStartedException)
      exceptionCode = "SESSION_NOT_STARTED_EXCEPTION";
    else if (e instanceof RequestException)
      exceptionCode = "REQUEST_EXCEPTION";
    else if (e instanceof UmbrellaException) {
      exceptionCode = "UMBRELLA_EXCEPTION";
    }     else if (e instanceof NetworkException) {
      exceptionCode = "NETWORK_EXCEPTION";
    }

    return exceptionCode;
  }

}

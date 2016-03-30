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

package org.waveprotocol.box.server;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;

import org.apache.commons.configuration.ConfigurationException;
import org.eclipse.jetty.proxy.ProxyServlet;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletHolder;
import org.swellrt.server.box.events.DeltaBasedEventSource;
import org.swellrt.server.box.events.EventDispatcher;
import org.swellrt.server.box.events.EventDispatcherTarget;
import org.swellrt.server.box.events.EventRule;
import org.swellrt.server.box.events.EventsModule;
import org.swellrt.server.box.events.dummy.DummyDispatcher;
import org.swellrt.server.box.events.gcm.GCMDispatcher;
import org.swellrt.server.box.index.ModelIndexerDispatcher;
import org.swellrt.server.box.index.ModelIndexerModule;
import org.swellrt.server.box.servlet.SwellRtServlet;
import org.swellrt.server.ds.DSFileServlet;
import org.waveprotocol.box.common.comms.WaveClientRpc.ProtocolWaveClientRpc;
import org.waveprotocol.box.server.authentication.AccountStoreHolder;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.executor.ExecutorsModule;
import org.waveprotocol.box.server.frontend.ClientFrontend;
import org.waveprotocol.box.server.frontend.ClientFrontendImpl;
import org.waveprotocol.box.server.frontend.WaveClientRpcImpl;
import org.waveprotocol.box.server.frontend.WaveletInfo;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.persistence.PersistenceModule;
import org.waveprotocol.box.server.persistence.SignerInfoStore;
import org.waveprotocol.box.server.robots.ProfileFetcherModule;
import org.waveprotocol.box.server.robots.RobotApiModule;
import org.waveprotocol.box.server.robots.RobotRegistrationServlet;
import org.waveprotocol.box.server.robots.active.ActiveApiServlet;
import org.waveprotocol.box.server.robots.agent.passwd.PasswordAdminRobot;
import org.waveprotocol.box.server.robots.agent.passwd.PasswordRobot;
import org.waveprotocol.box.server.robots.agent.registration.RegistrationRobot;
import org.waveprotocol.box.server.robots.agent.welcome.WelcomeRobot;
import org.waveprotocol.box.server.robots.dataapi.DataApiOAuthServlet;
import org.waveprotocol.box.server.robots.dataapi.DataApiServlet;
import org.waveprotocol.box.server.robots.passive.RobotsGateway;
import org.waveprotocol.box.server.rpc.AttachmentInfoServlet;
import org.waveprotocol.box.server.rpc.AttachmentServlet;
import org.waveprotocol.box.server.rpc.AuthenticationServlet;
import org.waveprotocol.box.server.rpc.FetchProfilesServlet;
import org.waveprotocol.box.server.rpc.FetchServlet;
import org.waveprotocol.box.server.rpc.GadgetProviderServlet;
import org.waveprotocol.box.server.rpc.LocaleServlet;
import org.waveprotocol.box.server.rpc.NotificationServlet;
import org.waveprotocol.box.server.rpc.SearchServlet;
import org.waveprotocol.box.server.rpc.ServerRpcProvider;
import org.waveprotocol.box.server.rpc.SignOutServlet;
import org.waveprotocol.box.server.rpc.UserRegistrationServlet;
import org.waveprotocol.box.server.rpc.WaveRefServlet;
import org.waveprotocol.box.server.stat.RequestScopeFilter;
import org.waveprotocol.box.server.stat.StatuszServlet;
import org.waveprotocol.box.server.stat.TimingFilter;
import org.waveprotocol.box.server.waveserver.PerUserWaveViewBus;
import org.waveprotocol.box.server.waveserver.PerUserWaveViewDistpatcher;
import org.waveprotocol.box.server.waveserver.WaveBus;
import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.box.server.waveserver.WaveletStateException;
import org.waveprotocol.box.stat.StatService;
import org.waveprotocol.wave.crypto.CertPathStore;
import org.waveprotocol.wave.federation.FederationTransport;
import org.waveprotocol.wave.federation.noop.NoOpFederationModule;
import org.waveprotocol.wave.federation.xmpp.XmppFederationModule;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.version.HashedVersionFactory;
import org.waveprotocol.wave.model.wave.ParticipantIdUtil;
import org.waveprotocol.wave.util.logging.Log;

import cc.kune.initials.InitialsAvatarsServlet;

import com.google.gwt.logging.server.RemoteLoggingServiceImpl;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.name.Names;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Wave Server entrypoint.
 */
public class ServerMain {

  /**
   * This is the name of the system property used to find the server config file.
   */
  private static final String PROPERTIES_FILE_KEY = "wave.server.config";

  private static final Log LOG = Log.get(ServerMain.class);

  @SuppressWarnings("serial")
  @Singleton
  public static class GadgetProxyServlet extends HttpServlet {

    ProxyServlet.Transparent proxyServlet;

    @Inject
    public GadgetProxyServlet(Config config) {
      String gadgetServerHostname = config.getString("core.gadget_server_hostname");
      int gadgetServerPort = config.getInt("core.gadget_server_port");
      LOG.info("Starting GadgetProxyServlet for " + gadgetServerHostname + ":" + gadgetServerPort);
      proxyServlet = new ProxyServlet.Transparent(
          "http://" + gadgetServerHostname + ":" + gadgetServerPort + "/gadgets",
          "/gadgets");
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
      proxyServlet.init(config);
    }

    @Override
    public void service(ServletRequest req, ServletResponse res) throws ServletException, IOException {
      proxyServlet.service(req, res);
    }
  }

  public static void main(String... args) {
    try {
      Module coreSettings = new AbstractModule() {

        @Override
        protected void configure() {
          Config config =
              ConfigFactory.load().withFallback(
                  ConfigFactory.parseFile(new File("config/application.conf")).withFallback(
                      ConfigFactory.parseFile(new File("config/reference.conf"))));
          bind(Config.class).toInstance(config);
          bind(Key.get(String.class, Names.named(CoreSettingsNames.WAVE_SERVER_DOMAIN)))
              .toInstance(config.getString("core.wave_server_domain"));
        }
      };
      run(coreSettings);
    } catch (PersistenceException e) {
      LOG.severe("PersistenceException when running server:", e);
    } catch (ConfigurationException e) {
      LOG.severe("ConfigurationException when running server:", e);
    } catch (WaveServerException e) {
      LOG.severe("WaveServerException when running server:", e);
    }
  }

  public static void run(Module coreSettings) throws PersistenceException,
      ConfigurationException, WaveServerException {
    Injector injector = Guice.createInjector(coreSettings);
    Module profilingModule = injector.getInstance(StatModule.class);
    ExecutorsModule executorsModule = injector.getInstance(ExecutorsModule.class);
    injector = injector.createChildInjector(profilingModule, executorsModule);

    Config config = injector.getInstance(Config.class);
    boolean enableFederation = config.getBoolean("federation.enable_federation");

    Module serverModule = injector.getInstance(ServerModule.class);
    Module federationModule = buildFederationModule(injector, enableFederation);
    Module robotApiModule = new RobotApiModule();
    PersistenceModule persistenceModule = injector.getInstance(PersistenceModule.class);
    Module searchModule = injector.getInstance(SearchModule.class);
    Module modelIndexerModule = injector.getInstance(ModelIndexerModule.class); // SwellRT
    Module eventsModule = injector.getInstance(EventsModule.class); // SwellRT
    Module profileFetcherModule = injector.getInstance(ProfileFetcherModule.class);
    injector = injector.createChildInjector(serverModule, persistenceModule, robotApiModule,
        federationModule, searchModule, profileFetcherModule);

    ServerRpcProvider server = injector.getInstance(ServerRpcProvider.class);
    WaveBus waveBus = injector.getInstance(WaveBus.class);

    String domain = config.getString("core.wave_server_domain");
    if (!ParticipantIdUtil.isDomainAddress(ParticipantIdUtil.makeDomainAddress(domain))) {
      throw new WaveServerException("Invalid wave domain: " + domain);
    }

    initializeServer(injector, domain);
    initializeServlets(server, config);
    initializeRobotAgents(server);
    initializeRobots(injector, waveBus);
    initializeFrontend(injector, server, waveBus, config.getString("core.wave_server_domain"));
    initializeFederation(injector);
    initializeSearch(injector, waveBus);
    initializeSwellRt(injector, waveBus);

    LOG.info("Starting server");
    server.startWebSocketServer(injector);
  }

  private static Module buildFederationModule(Injector settingsInjector, boolean enableFederation)
      throws ConfigurationException {
    Module federationModule;
    if (enableFederation) {
      federationModule = settingsInjector.getInstance(XmppFederationModule.class);
    } else {
      federationModule = settingsInjector.getInstance(NoOpFederationModule.class);
    }
    return federationModule;
  }

  private static void initializeServer(Injector injector, String waveDomain)
      throws PersistenceException, WaveServerException {
    AccountStore accountStore = injector.getInstance(AccountStore.class);
    accountStore.initializeAccountStore();
    AccountStoreHolder.init(accountStore, waveDomain);

    // Initialize the SignerInfoStore.
    CertPathStore certPathStore = injector.getInstance(CertPathStore.class);
    if (certPathStore instanceof SignerInfoStore) {
      ((SignerInfoStore)certPathStore).initializeSignerInfoStore();
    }

    // Initialize the server.
    WaveletProvider waveServer = injector.getInstance(WaveletProvider.class);
    waveServer.initialize();
  }

  private static void initializeServlets(ServerRpcProvider server, Config config) {
    server.addServlet("/gadget/gadgetlist", GadgetProviderServlet.class);

    server.addServlet(AttachmentServlet.ATTACHMENT_URL + "/*", AttachmentServlet.class);
    server.addServlet(AttachmentServlet.THUMBNAIL_URL + "/*", AttachmentServlet.class);
    server.addServlet(AttachmentInfoServlet.ATTACHMENTS_INFO_URL, AttachmentInfoServlet.class);

    server.addServlet(SessionManager.SIGN_IN_URL, AuthenticationServlet.class);
    server.addServlet("/auth/signout", SignOutServlet.class);
    server.addServlet("/auth/register", UserRegistrationServlet.class);

    server.addServlet("/locale/*", LocaleServlet.class);
    server.addServlet("/fetch/*", FetchServlet.class);
    server.addServlet("/search/*", SearchServlet.class);
    server.addServlet("/notification/*", NotificationServlet.class);

    server.addServlet("/robot/dataapi", DataApiServlet.class);
    server.addServlet(DataApiOAuthServlet.DATA_API_OAUTH_PATH + "/*", DataApiOAuthServlet.class);
    server.addServlet("/robot/dataapi/rpc", DataApiServlet.class);
    server.addServlet("/robot/register/*", RobotRegistrationServlet.class);
    server.addServlet("/robot/rpc", ActiveApiServlet.class);
    server.addServlet("/webclient/remote_logging", RemoteLoggingServiceImpl.class);
    server.addServlet("/profile/*", FetchProfilesServlet.class);
    server.addServlet("/iniavatars/*", InitialsAvatarsServlet.class);
    server.addServlet("/waveref/*", WaveRefServlet.class);

    String gadgetHostName = config.getString("core.gadget_server_hostname");
    int port = config.getInt("core.gadget_server_port");
    Map<String, String> initParams =
        Collections.singletonMap("hostHeader", gadgetHostName + ":" + port);
    server.addServlet("/gadgets/*", GadgetProxyServlet.class, initParams);


    // server.addServlet("/", WaveClientServlet.class);
    // Root context is a static resource introducing SwellRT
    ServletHolder staticHolder = server.addServlet("/static/*", DefaultServlet.class);
    staticHolder.setInitParameter("dirAllowed", "false");
    ServletHolder rootHolder = server.addServlet("/", DefaultServlet.class);
    rootHolder.setInitParameter("dirAllowed", "false");


    // Profiling
    server.addFilter("/*", RequestScopeFilter.class);
    boolean enableProfiling = config.getBoolean("core.enable_profiling");
    if (enableProfiling) {
      server.addFilter("/*", TimingFilter.class);
      server.addServlet(StatService.STAT_URL, StatuszServlet.class);
    }

    // DSWG experimental
    server.addServlet("/shared/*", DSFileServlet.class);

    // SwellRt
    server.addServlet("/swell/*", SwellRtServlet.class);
  }

  private static void initializeRobots(Injector injector, WaveBus waveBus) {
    RobotsGateway robotsGateway = injector.getInstance(RobotsGateway.class);
    waveBus.subscribe(robotsGateway);
  }

  private static void initializeRobotAgents(ServerRpcProvider server) {
    server.addServlet(PasswordRobot.ROBOT_URI + "/*", PasswordRobot.class);
    server.addServlet(PasswordAdminRobot.ROBOT_URI + "/*", PasswordAdminRobot.class);
    server.addServlet(WelcomeRobot.ROBOT_URI + "/*", WelcomeRobot.class);
    server.addServlet(RegistrationRobot.ROBOT_URI + "/*", RegistrationRobot.class);
  }

  private static void initializeFrontend(Injector injector, ServerRpcProvider server,
      WaveBus waveBus, String waveDomain) throws WaveServerException {
    HashedVersionFactory hashFactory = injector.getInstance(HashedVersionFactory.class);

    WaveletProvider provider = injector.getInstance(WaveletProvider.class);
    WaveletInfo waveletInfo = WaveletInfo.create(hashFactory, provider);
    ClientFrontend frontend = ClientFrontendImpl.create(provider, waveBus, waveletInfo, waveDomain);

    ProtocolWaveClientRpc.Interface rpcImpl = WaveClientRpcImpl.create(frontend, false);
    server.registerService(ProtocolWaveClientRpc.newReflectiveService(rpcImpl));
  }

  private static void initializeFederation(Injector injector) {
    FederationTransport federationManager = injector.getInstance(FederationTransport.class);
    federationManager.startFederation();
  }

  private static void initializeSearch(Injector injector, WaveBus waveBus)
      throws WaveletStateException, WaveServerException {
    PerUserWaveViewDistpatcher waveViewDistpatcher =
        injector.getInstance(PerUserWaveViewDistpatcher.class);
    PerUserWaveViewBus.Listener listener = injector.getInstance(PerUserWaveViewBus.Listener.class);
    waveViewDistpatcher.addListener(listener);
    waveBus.subscribe(waveViewDistpatcher);

    // WaveIndexer waveIndexer = injector.getInstance(WaveIndexer.class);
    // waveIndexer.remakeIndex();
  }

  private static void initializeSwellRt(Injector injector, WaveBus waveBus) {

    // Initialize Indexer

    ModelIndexerDispatcher indexerDispatcher =
        injector.getInstance(ModelIndexerDispatcher.class);

//    try {
//      indexerDispatcher.initialize();
//    } catch (WaveServerException e) {
//      LOG.warning("Error initializating SwellRtIndexerDispatcher", e);
    // }
    waveBus.subscribe(indexerDispatcher);


    // Initialize Events
    GCMDispatcher gcmDispatcher = injector.getInstance(GCMDispatcher.class);
    gcmDispatcher.initialize(System.getProperty("event.dispatch.config.file", "event.dispatch.config"));

    DummyDispatcher dummyDispatcher = injector.getInstance(DummyDispatcher.class);

    Collection<EventRule> rules =
        EventRule.fromFile(System.getProperty("event.rules.config.file", "event.rules.config"));

    EventDispatcher eventDispatcher = injector.getInstance(EventDispatcher.class);
    eventDispatcher.initialize(CollectionUtils.<String, EventDispatcherTarget> immutableMap(
        GCMDispatcher.NAME, gcmDispatcher, DummyDispatcher.NAME, dummyDispatcher), rules);


    DeltaBasedEventSource eventSource = injector.getInstance(DeltaBasedEventSource.class);
    waveBus.subscribe(eventSource);
  }

}

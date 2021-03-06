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

package org.waveprotocol.box.webclient.client;

import org.waveprotocol.box.webclient.client.atmosphere.WaveSocketAtmosphere;
import org.waveprotocol.box.webclient.client.atmosphere.WaveSocketAtmosphereCallback;


/**
 * Factory to create proxy wrappers around either {@link com.google.gwt.websockets.client.WebSocket}
 * or {@link org.waveprotocol.box.webclient.client.atmosphere.AtmosphereConnection}.
 *
 * @author tad.glines@gmail.com (Tad Glines)
 */
public class WaveSocketFactory {

  /**
   * Create a WaveSocket instance that wraps a concrete socket implementation.
   * If useWebSocketAlt is true an instance of {@link org.waveprotocol.box.webclient.client.atmosphere.AtmosphereConnection}
   * is wrapped, otherwise an instance of {@link com.google.gwt.websockets.client.WebSocket} is
   * wrapped.
   */
  public static WaveSocket create(final boolean useWebSocketAlt, final String urlBase,
      final String sessionId,
      final WaveSocket.WaveSocketCallback callback) {

    // Handle special atmosphere features enabled in SwellRT
    WaveSocketAtmosphereCallback swellRTCallbackSwellRT = new WaveSocketAtmosphereCallback(callback);

    return new WaveSocketAtmosphere(swellRTCallbackSwellRT, urlBase, useWebSocketAlt, sessionId);
  }
}

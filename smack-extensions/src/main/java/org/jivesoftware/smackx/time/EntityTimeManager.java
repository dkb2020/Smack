/**
 *
 * Copyright 2014-2021 Florian Schmaus
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jivesoftware.smackx.time;

import java.util.Map;
import java.util.WeakHashMap;

import org.jivesoftware.smack.ConnectionCreationListener;
import org.jivesoftware.smack.Manager;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.SmackException.FeatureNotSupportedException;
import org.jivesoftware.smack.SmackException.NoResponseException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPConnectionRegistry;
import org.jivesoftware.smack.XMPPException.XMPPErrorException;
import org.jivesoftware.smack.iqrequest.AbstractIqRequestHandler;
import org.jivesoftware.smack.iqrequest.IQRequestHandler.Mode;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.StanzaError.Condition;

import org.jivesoftware.smackx.disco.ServiceDiscoveryManager;
import org.jivesoftware.smackx.time.packet.Time;

import org.jxmpp.jid.Jid;

public final class EntityTimeManager extends Manager {

    private static final Map<XMPPConnection, EntityTimeManager> INSTANCES = new WeakHashMap<>();

    private static boolean autoEnable = true;

    static {
        XMPPConnectionRegistry.addConnectionCreationListener(new ConnectionCreationListener() {
            @Override
            public void connectionCreated(XMPPConnection connection) {
                getInstanceFor(connection);
            }
        });
    }

    public static void setAutoEnable(boolean autoEnable) {
        EntityTimeManager.autoEnable = autoEnable;
    }

    public static synchronized EntityTimeManager getInstanceFor(XMPPConnection connection) {
        EntityTimeManager entityTimeManager = INSTANCES.get(connection);
        if (entityTimeManager == null) {
            entityTimeManager = new EntityTimeManager(connection);
            INSTANCES.put(connection, entityTimeManager);
        }
        return entityTimeManager;
    }

    private boolean enabled = false;

    private EntityTimeManager(XMPPConnection connection) {
        super(connection);
        if (autoEnable)
            enable();

        connection.registerIQRequestHandler(new AbstractIqRequestHandler(Time.ELEMENT, Time.NAMESPACE, IQ.Type.get,
                        Mode.async) {
            @Override
            public IQ handleIQRequest(IQ iqRequest) {
                if (!enabled) {
                    return IQ.createErrorResponse(iqRequest, Condition.not_acceptable);
                }

                Time timeRequest = (Time) iqRequest;
                Time timeResponse = Time.builder(timeRequest).build();
                return timeResponse;
            }
        });
    }

    public synchronized void enable() {
        if (enabled)
            return;
        ServiceDiscoveryManager sdm = ServiceDiscoveryManager.getInstanceFor(connection());
        sdm.addFeature(Time.NAMESPACE);
        enabled = true;
    }

    public synchronized void disable() {
        if (!enabled)
            return;
        ServiceDiscoveryManager sdm = ServiceDiscoveryManager.getInstanceFor(connection());
        sdm.removeFeature(Time.NAMESPACE);
        enabled = false;
    }

    public boolean isTimeSupported(Jid jid) throws NoResponseException, XMPPErrorException, NotConnectedException, InterruptedException  {
        return ServiceDiscoveryManager.getInstanceFor(connection()).supportsFeature(jid, Time.NAMESPACE);
    }

    public Time getTime(Jid jid) throws NoResponseException, XMPPErrorException, NotConnectedException,
                    InterruptedException, FeatureNotSupportedException {
        if (!isTimeSupported(jid)) {
            throw new SmackException.FeatureNotSupportedException(Time.NAMESPACE);
        }

        XMPPConnection connection = connection();
        Time request = Time.builder(connection)
                        .to(jid)
                        .build();
        return connection.createStanzaCollectorAndSend(request).nextResultOrThrow();
    }
}

/**
 * $RCSfile$
 * $Revision: 11216 $
 * $Date: 2009-08-24 22:57:37 +0200 (lun, 24 ago 2009) $
 *
 * Copyright 2005 Jive Software.
 *
 * All rights reserved. Licensed under the Apache License, Version 2.0 (the "License");
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

package org.jivesoftware.whack;

import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

import org.xmpp.component.Component;
import org.xmpp.component.ComponentException;
import org.xmpp.component.ComponentManager;
import org.xmpp.component.ComponentManagerFactory;
import org.xmpp.component.IQResultListener;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;

/**
 * Implementation of the ComponentManager interface for external components.
 * This implementation follows JEP-0014.
 * 
 * @author Matt Tucker
 */
public class ExternalComponentManager implements ComponentManager {

	/**
	 * Keeps the IP address or hostname of the server. This value will be used
	 * only for creating connections.
	 */
	private final String host;
	/**
	 * Port of the server used for establishing new connections.
	 */
	private final int port;
	/**
	 * Keeps the domain of the XMPP server. The domain may or may not match the
	 * host. The domain will be used mainly for the XMPP packets while the host
	 * is used mainly for creating connections to the server.
	 */
	private String domain;
	/**
	 * Timeout to use when trying to connect to the server.
	 */
	private int connectTimeout = 2000;
	/**
	 * This is a global secret key that will be used during the handshake with
	 * the server. If a secret key was not defined for the specific component
	 * then the global secret key will be used.
	 */
	private String defaultSecretKey;
	/**
	 * Keeps the secret keys to use for each subdomain. If a key was not found
	 * for a specific subdomain then the global secret key will used for the
	 * handshake with the server.
	 */
	private final Map<String, String> secretKeys = new Hashtable<String, String>();
	/**
	 * Holds the settings for whether we will tell the XMPP server that a given
	 * component can connect to the same JID multiple times. This is a custom
	 * Openfire extension and will not work with any other XMPP server. Other
	 * servers should ignore this setting.
	 */
	private final Map<String, Boolean> allowMultiple = new Hashtable<String, Boolean>();

	Preferences preferences = Preferences.userRoot();
	private String preferencesPrefix;

	/**
	 * Keeps a map that associates a domain with the external component thas is
	 * handling the domain.
	 */
	private final Map<String, ExternalComponent> componentsByDomain = new Hashtable<String, ExternalComponent>();
	/**
	 * Keeps a map that associates a component with the wrapping
	 * ExternalComponent.
	 */
	private final Map<Component, ExternalComponent> components = new Hashtable<Component, ExternalComponent>();

	/**
	 * Constructs a new ExternalComponentManager that will make connections to
	 * the specified XMPP server on the default external component port (5275).
	 * 
	 * @param host
	 *            the IP address or name of the XMPP server to connect to (e.g.
	 *            "example.com").
	 */
	public ExternalComponentManager(final String host) {
		this(host, 5275);
	}

	/**
	 * Constructs a new ExternalComponentManager that will make connections to
	 * the specified XMPP server on the given port.
	 * 
	 * @param host
	 *            the IP address or name of the XMPP server to connect to (e.g.
	 *            "example.com").
	 * @param port
	 *            the port to connect on.
	 */
	public ExternalComponentManager(final String host, final int port) {
		if (host == null)
			throw new IllegalArgumentException("Host of XMPP server cannot be null");
		this.host = host;
		this.port = port;

		// Set this ComponentManager as the current component manager
		ComponentManagerFactory.setComponentManager(this);
	}

	/**
	 * Sets a secret key for a sub-domain, for future use by a component
	 * connecting to the server. Keys are used as an authentication mechanism
	 * when connecting to the server. Some servers may require a different key
	 * for each component, while others may use a global secret key.
	 * 
	 * @param subdomain
	 *            the sub-domain.
	 * @param secretKey
	 *            the secret key
	 */
	public void setSecretKey(final String subdomain, final String secretKey) {
		secretKeys.put(subdomain, secretKey);
	}

	/**
	 * Returns the secret key for a sub-domain. If no key was found then the
	 * default secret key will be returned.
	 * 
	 * @param subdomain
	 *            the subdomain to return its secret key.
	 * @return the secret key for a sub-domain.
	 */
	public String getSecretKey(final String subdomain) {
		// Find the proper secret key to connect as the subdomain.
		String secretKey = secretKeys.get(subdomain);
		if (secretKey == null) {
			secretKey = defaultSecretKey;
		}
		return secretKey;
	}

	/**
	 * Sets the default secret key, which will be used when connecting if a
	 * specific secret key for the component hasn't been sent. Keys are used as
	 * an authentication mechanism when connecting to the server. Some servers
	 * may require a different key for each component, while others may use a
	 * global secret key.
	 * 
	 * @param secretKey
	 *            the default secret key.
	 */
	public void setDefaultSecretKey(final String secretKey) {
		defaultSecretKey = secretKey;
	}

	/**
	 * Returns if we want components to be able to connect multiple times to the
	 * same JID. This is a custom Openfire extension and will not work with any
	 * other XMPP server. Other XMPP servers should ignore this extra setting.
	 * 
	 * @param subdomain
	 *            the sub-domain.
	 * @return True or false if we are allowing multiple connections.
	 */
	public boolean isMultipleAllowed(final String subdomain) {
		final Boolean allowed = allowMultiple.get(subdomain);
		return allowed != null && allowed;
	}

	/**
	 * Sets whether we will tell the XMPP server that we want multiple
	 * components to be able to connect to the same JID. This is a custom
	 * Openfire extension and will not work with any other XMPP server. Other
	 * XMPP servers should ignore this extra setting.
	 * 
	 * @param subdomain
	 *            the sub-domain.
	 * @param allowMultiple
	 *            Set to true if we want to allow multiple connections to same
	 *            JID.
	 */
	public void setMultipleAllowed(final String subdomain, final boolean allowMultiple) {
		this.allowMultiple.put(subdomain, allowMultiple);
	}

	@Override
	public void addComponent(final String subdomain, final Component component) throws ComponentException {
		addComponent(subdomain, component, port);
	}

	public void addComponent(final String subdomain, final Component component, final int port) throws ComponentException {
		if (componentsByDomain.containsKey(subdomain)) {
			if (componentsByDomain.get(subdomain).getComponent() == component)
				// Do nothing since the component has already been registered
				return;
			throw new IllegalArgumentException("Subdomain already in use by another component");
		}
		// Create a wrapping ExternalComponent on the component
		final ExternalComponent externalComponent = new ExternalComponent(component, this);
		try {
			// Register the new component
			componentsByDomain.put(subdomain, externalComponent);
			components.put(component, externalComponent);
			// Ask the ExternalComponent to connect with the remote server
			externalComponent.connect(host, port, subdomain);
			// Initialize the component
			final JID componentJID = new JID(null, externalComponent.getDomain(), null);
			externalComponent.initialize(componentJID, this);
		} catch (final ComponentException e) {
			// Unregister the new component
			componentsByDomain.remove(subdomain);
			components.remove(component);
			// Re-throw the exception
			throw e;
		}
		// Ask the external component to start processing incoming packets
		externalComponent.start();
	}

	@Override
	public void removeComponent(final String subdomain) throws ComponentException {
		final ExternalComponent externalComponent = componentsByDomain.remove(subdomain);
		if (externalComponent != null) {
			components.remove(externalComponent.getComponent());
			externalComponent.shutdown();
		}
	}

	@Override
	public void sendPacket(final Component component, final Packet packet) {
		// Get the ExternalComponent that is wrapping the specified component
		// and ask it to
		// send the packet
		components.get(component).send(packet);
	}

	@Override
	public IQ query(final Component component, final IQ packet, final long timeout) throws ComponentException {
		final LinkedBlockingQueue<IQ> answer = new LinkedBlockingQueue<IQ>(8);
		final ExternalComponent externalComponent = components.get(component);
		externalComponent.addIQResultListener(packet.getID(), new IQResultListener() {
			@Override
			public void receivedAnswer(final IQ packet) {
				answer.offer(packet);
			}

			@Override
			public void answerTimeout(final String packetId) {
				// Do nothing
			}
		}, timeout);
		sendPacket(component, packet);
		IQ reply = null;
		try {
			reply = answer.poll(timeout, TimeUnit.MILLISECONDS);
		} catch (final InterruptedException e) {
			// Ignore
		}
		return reply;
	}

	@Override
	public void query(final Component component, final IQ packet, final IQResultListener listener) throws ComponentException {
		final ExternalComponent externalComponent = components.get(component);
		// Add listenet with a timeout of 5 minutes to prevent memory leaks
		externalComponent.addIQResultListener(packet.getID(), listener, 300000);
		sendPacket(component, packet);
	}

	@Override
	public String getProperty(final String name) {
		return preferences.get(getPreferencesPrefix() + name, null);
	}

	@Override
	public void setProperty(final String name, final String value) {
		preferences.put(getPreferencesPrefix() + name, value);
	}

	private String getPreferencesPrefix() {
		if (preferencesPrefix == null) {
			preferencesPrefix = "whack." + domain + ".";
		}
		return preferencesPrefix;
	}

	/**
	 * Sets the domain of the XMPP server. The domain may or may not match the
	 * host. The domain will be used mainly for the XMPP packets while the host
	 * is used mainly for creating connections to the server.
	 * 
	 * @param domain
	 *            the domain of the XMPP server.
	 */
	public void setServerName(final String domain) {
		this.domain = domain;
	}

	/**
	 * Returns the domain of the XMPP server where we are connected to or
	 * <tt>null</tt> if this value was never configured. When the value is null
	 * then the component will register with just its subdomain and we expect
	 * the server to accept the component and append its domain to form the JID
	 * of the component.
	 * 
	 * @return the domain of the XMPP server or null if never configured.
	 */
	@Override
	public String getServerName() {
		return domain;
	}

	/**
	 * Returns the timeout (in milliseconds) to use when trying to connect to
	 * the server. The default value is 2 seconds.
	 * 
	 * @return the timeout to use when trying to connect to the server.
	 */
	public int getConnectTimeout() {
		return connectTimeout;
	}

	/**
	 * Sets the timeout (in milliseconds) to use when trying to connect to the
	 * server. The default value is 2 seconds.
	 * 
	 * @param connectTimeout
	 *            the timeout, in milliseconds, to use when trying to connect to
	 *            the server.
	 */
	public void setConnectTimeout(final int connectTimeout) {
		this.connectTimeout = connectTimeout;
	}

	@Override
	public boolean isExternalMode() {
		return true;
	}

}
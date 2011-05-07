/**
 * $RCSfile$
 * $Revision: 11054 $
 * $Date: 2009-06-14 13:28:39 +0200 (dom, 14 jun 2009) $
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.component.Component;
import org.xmpp.component.ComponentException;

public class ComponentLoader {

	private static final Logger log = LoggerFactory.getLogger(ComponentLoader.class);

	private final Set<String> subdomains;
	private ExternalComponentManager manager;

	private ComponentLoader() {
		subdomains = new HashSet<String>();
	}

	protected void startWhack() {
		final Properties serverConf = new Properties();
		try {
			serverConf.load(new FileInputStream("whack.conf"));
		} catch (final FileNotFoundException e) {
			log.error("Configuration file not found");
			System.exit(1);
		} catch (final IOException e) {
			log.error("Error reading configuration file", e);
			System.exit(1);
		}

		// Get configuration settings
		final String serverHost = serverConf.getProperty("whack.host", "localhost");
		final int serverPort = Integer.parseInt(serverConf.getProperty("whack.port", "5275"));
		final String serverDomain = serverConf.getProperty("whack.domain", serverHost);
		final String defaultKey = serverConf.getProperty("whack.secret");
		final int timeout = Integer.parseInt(serverConf.getProperty("whack.timeout", "2000"));

		manager = new ExternalComponentManager(serverHost, serverPort);
		manager.setDefaultSecretKey(defaultKey);
		manager.setServerName(serverDomain);
		manager.setConnectTimeout(timeout);

		for (final String componentID : serverConf.getProperty("whack.components", "").split(",")) {
			if (!componentID.matches("\\w+") || componentID.equals("server")) {
				log.warn(String.format("Invalid component ID '%s'", componentID));
				continue;
			}

			final String jarName = serverConf.getProperty(componentID + ".jar");
			final String className = serverConf.getProperty(componentID + ".class");
			final String subdomain = serverConf.getProperty(componentID + ".subdomain");
			final String secretKey = serverConf.getProperty(componentID + ".secret");
			final boolean multi = Boolean.parseBoolean(serverConf.getProperty(componentID + ".multi"));

			if (className == null || subdomain == null) {
				log.error(componentID + ".class and " + componentID + ".subdomain must be set");
				System.exit(1);
			}

			try {
				final ClassLoader loader = new URLClassLoader(new URL[] { new File(jarName).toURI().toURL() });
				final Class<? extends Component> componentClass = loader.loadClass(className).asSubclass(Component.class);
				final Component newComponent = componentClass.newInstance();

				if (secretKey != null) {
					manager.setSecretKey(subdomain, secretKey);
				}

				manager.setMultipleAllowed(subdomain, multi);

				try {
					manager.addComponent(subdomain, newComponent);
				} catch (final ComponentException e) {
					log.error(String.format("Error loading component '%s'", componentID), e);
					continue;
				}

				subdomains.add(subdomain);
			} catch (final MalformedURLException e) {
				log.error(String.format("Malformed JAR name '%s'", jarName), e);
			} catch (final ClassNotFoundException e) {
				log.error(String.format("Component class '%s' not found", className), e);
			} catch (final InstantiationException e) {
				log.error(String.format("Error instantiating component '%s'", componentID), e);
			} catch (final IllegalAccessException e) {
				log.error(String.format("Illegal access error loading component '%s'", componentID), e);
			}
		}

		if (subdomains.isEmpty()) {
			log.error("No components loaded. Exiting.");
			System.exit(1);
		}

		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				stopWhack();
			}
		});

		while (true) {
			try {
				Thread.sleep(1000);
			} catch (final InterruptedException e) {
				break;
			}
		}

		System.exit(0);
	}

	public void stopWhack() {
		log.info("Shutting down...");
		for (final String subdomain : subdomains) {
			try {
				manager.removeComponent(subdomain);
			} catch (final ComponentException e1) {
				log.error("Error shutting down component");
			}
		}
	}

	public static void main(final String[] args) {
		new ComponentLoader().startWhack();
	}

}

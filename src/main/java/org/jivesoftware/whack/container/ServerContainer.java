/**
 * $RCSfile$
 * $Revision: 11803 $
 * $Date: 2010-08-03 22:23:52 +0200 (mar, 03 ago 2010) $
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

package org.jivesoftware.whack.container;

import org.jivesoftware.whack.ExternalComponent;
import org.jivesoftware.whack.ExternalComponentManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.component.ComponentManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Starts the web server and components finder. A bootstrap class that will start the Jetty server
 * for which it requires to receive two parameters when launched. The first parameter is the
 * absolute path to the root folder that contains:
 * <pre><ul>
 *      <li><tt>conf</tt> - folder that holds Whack's configuration file</li>
 *      <li><tt>components</tt> - folder that holds the components' jar files</li>
 *      <li><tt>resources/security</tt> - folder that holds the key stores for the https protocol</li>
 *      <li><tt>webapp</tt> - folder that holds the JSP pages of the admin console</li>
 * </ul></pre>
 * The second parameter is the name of the configuration file that holds Whack's configuration.
 * This file must be located in the <tt>conf</tt> folder under the root folder.
 *
 * @author Gaston Dombiak
 */
public class ServerContainer {

	private static final Logger log = LoggerFactory.getLogger(ExternalComponent.class);
	
    private static final ServerContainer instance = new ServerContainer();
    private ExternalComponentManager manager;
    private ComponentFinder componentFinder;

    /**
     * True if in setup mode
     */
    private boolean setupMode = true;

    private String homeDir;
    private Properties properties;

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage ServerContainer <absolute path to home folder> <config filename>");
            return;
        }
        String homeDir = args[0];
        Properties properties = new Properties();
        try {
            properties.load(new FileInputStream(homeDir + "/conf/" + args[1]));
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        instance.setHomeDirectory(homeDir);
        instance.setProperties(properties);
        instance.start();

    }

    public static ServerContainer getInstance() {
        return instance;
    }

    public String getHomeDirectory() {
        return homeDir;
    }

    void setHomeDirectory(String homeDir) {
        this.homeDir = homeDir;
    }

    public Properties getProperties() {
        return properties;
    }

    void setProperties(Properties properties) {
        this.properties = properties;
    }

    public void start() {
        try {
            // Start the ExternalComponentManager
            String xmppServerHost = properties.getProperty("xmppServer.host");
            String port = properties.getProperty("xmppServer.port");
            int xmppServerPort = (port == null ? 10015 : Integer.parseInt(port));
            manager = new ExternalComponentManager(xmppServerHost, xmppServerPort);

            if (properties.getProperty("xmppServer.domain") != null) {
                manager.setServerName(properties.getProperty("xmppServer.domain"));
            }
            
            if (properties.getProperty("xmppServer.defaultSecretKey") != null) {
                manager.setDefaultSecretKey(properties.getProperty("xmppServer.defaultSecretKey"));
            }

            // Load detected components.
            componentFinder = new ComponentFinder(this, new File(homeDir, "components"));
            componentFinder.start();
        }
        catch (Exception e) {
            log.error(e.getMessage());
        }
    }

    public boolean isSetupMode() {
        return setupMode;
    }

    public ComponentManager getManager() {
        return manager;
    }
}

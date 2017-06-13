/*
 *    Copyright 2017 Frederic Thevenet
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

package eu.fthevenet.binjr;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bootstrap class for binjr that checks for JavaFX runtime presence and fails with an explicit error if not.
 *
 * @author Frederic Thevenet
 */
final public class Bootstrap {
    private static final Logger logger = LogManager.getLogger(Bootstrap.class);

    public static void main(String[] args) {
        if (checkForJavaFX()) {
            Binjr.main(args);
        }
        else {
            logger.fatal("The JavaFX runtime must be present in order to run binjr. Please check with your Java vendor to see is JavaFX is available on your platform and how to install it.");
        }
    }

    private static boolean checkForJavaFX() {
        try {
            Class<?> c = Class.forName("javafx.application.Application");
            logger.info("JavaFX runtime detected.");
            return true;
        } catch (ClassNotFoundException e) {
            logger.info("JavaFX is not available on the current runtime environment:" +
                    "\njava.version=" +
                    System.getProperty("java.version") +
                    "\njava.vendor=" +
                    System.getProperty("java.vendor") +
                    "\njava.vm.name=" +
                    System.getProperty("java.vm.name") +
                    "\njava.vm.version=" +
                    System.getProperty("java.vm.version") +
                    "\njava.home=" +
                    System.getProperty("java.home"));
            logger.debug("JavaFX not available", e);
            return false;
        }

    }
}
/*
 * Copyright 2000-2022 Vaadin Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package io.jmix.flowui.devserver;

import com.vaadin.flow.di.Lookup;
import com.vaadin.flow.server.InitParameters;
import io.jmix.flowui.devserver.frontend.FrontendTools;
import io.jmix.flowui.devserver.frontend.FrontendUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public final class WebpackHandler extends AbstractDevServerRunner {

    // It's not possible to know whether webpack is ready unless reading output
    // messages. When webpack finishes, it writes either a `Compiled` or a
    // `Failed` in the last line
    private static final String DEFAULT_OUTPUT_PATTERN = ": Compiled.";
    private static final String DEFAULT_ERROR_PATTERN = ": Failed to compile.";

    /**
     * The local installation path of the webpack-dev-server node script.
     */
    public static final String WEBPACK_SERVER = "node_modules/webpack-dev-server/bin/webpack-dev-server.js";

    /**
     * Creates and starts the dev mode handler if none has been started yet.
     *
     * @param lookup       the provided lookup to get required data
     * @param runningPort  a port on which webpack is already running or 0 to start a new
     *                     process
     * @param npmFolder    folder with npm configuration files
     * @param studioFolder folder with generated files
     * @param waitFor      a completable future whose execution result needs to be
     *                     available to start the dev server
     */
    public WebpackHandler(Lookup lookup, int runningPort, File npmFolder, File studioFolder,
                          CompletableFuture<Void> waitFor) {
        super(lookup, runningPort, npmFolder, studioFolder, waitFor);
        FrontendUtils.logInFile(this.getClass().getName() + " initialized");
    }

    private static Logger getLogger() {
        return LoggerFactory.getLogger(WebpackHandler.class);
    }

    @Override
    protected void onDevServerCompilation(DevServerOutputTracker.Result result) {
        super.onDevServerCompilation(result);

        // trigger a live-reload since webpack has recompiled the bundle
        // if failure, ensures the webpack error is shown in the browser
        triggerLiveReload();
    }

    @Override
    protected List<String> getServerStartupCommand(FrontendTools tools) {
        List<String> command = new ArrayList<>();
        command.add(tools.getNodeExecutable());
        command.add(getServerBinary().getAbsolutePath());
        command.add("--config");
        command.add(getServerConfig().getAbsolutePath());
        command.add("--port");
        command.add(String.valueOf(getPort()));
        command.add("--watch-options-stdin"); // Tell wds to stop even if
        // watchDog fail

        String customParameters = getApplicationConfiguration()
                .getStringProperty(
                        InitParameters.SERVLET_PARAMETER_DEVMODE_WEBPACK_OPTIONS,
                        "");
        if (!customParameters.isEmpty()) {
            command.addAll(Arrays.asList(customParameters.split(" +")));
            String logMessage = String.format(
                    "Starting %s using: %s",
                    getServerName(),
                    String.join(" ", command)
            );
            getLogger().info(logMessage); // NOSONAR
            FrontendUtils.logInFile(logMessage);
        } else {
            command.add("--devtool=eval-source-map");
            command.add("--mode=development");
        }

        return command;
    }

    @Override
    protected void updateServerStartupEnvironment(FrontendTools frontendTools,
                                                  Map<String, String> environment) {
        super.updateServerStartupEnvironment(frontendTools, environment);
        // use environment variable as flags must be passed to Webpack
        // subprocess
        environment.putAll(frontendTools.getWebpackNodeEnvironment());
    }

    @Override
    protected String getServerName() {
        return "Webpack";
    }

    @Override
    protected File getServerBinary() {
        return new File(getStudioRoot(), WEBPACK_SERVER);
    }

    @Override
    protected File getServerConfig() {
        return new File(getStudioRoot(), FrontendUtils.WEBPACK_CONFIG);
    }

    @Override
    protected Pattern getServerSuccessPattern() {
        return Pattern.compile(getApplicationConfiguration().getStringProperty(
                InitParameters.SERVLET_PARAMETER_DEVMODE_WEBPACK_SUCCESS_PATTERN,
                DEFAULT_OUTPUT_PATTERN));
    }

    @Override
    protected Pattern getServerFailurePattern() {
        return Pattern.compile(getApplicationConfiguration().getStringProperty(
                InitParameters.SERVLET_PARAMETER_DEVMODE_WEBPACK_ERROR_PATTERN,
                DEFAULT_ERROR_PATTERN));
    }
}
/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.docker.compose.example;

import static com.palantir.docker.compose.DockerComposeRule.DEFAULT_TIMEOUT;

import com.google.common.collect.ImmutableList;
import com.palantir.docker.compose.DockerComposeRule;
import com.palantir.docker.compose.connection.Cluster;
import com.palantir.docker.compose.connection.DockerPort;
import com.palantir.docker.compose.connection.waiting.ClusterWait;
import com.palantir.docker.compose.connection.waiting.MessageReportingClusterWait;
import com.palantir.docker.compose.connection.waiting.SuccessOrFailure;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Test-specific class to easily interact with Selenium in a DockerComposeRule-based test suite.
 *
 * It also contains two handy methods to help with writing Selenium tests.
 */
public class SeleniumService implements ClusterWait {

    public WebDriver driver(DockerComposeRule rule) throws MalformedURLException {
        return createDriver(seleniumUrl(rule.containers()));
    }

    /**
     * Opens a OS X VNC window into the 'selenium/standalone-chrome-debug' container.
     *
     * This is possible because Macs have a built-in VNC viewer and seleniums
     * login details are hard-coded to 'username', 'secret'.
     */
    public void openVNC(DockerComposeRule rule) throws InterruptedException, IOException {
        new ProcessBuilder()
                .command(ImmutableList.of("open", vncUrl(rule.containers())))
                .start()
                .waitFor();
    }

    @Override
    public void waitUntilReady(Cluster cluster) {
        new MessageReportingClusterWait(this::isHealthy, DEFAULT_TIMEOUT).waitUntilReady(cluster);
    }

    private SuccessOrFailure isHealthy(Cluster cluster) {
        return SuccessOrFailure.onResultOf(() -> createDriver(seleniumUrl(cluster)) != null);
    }

    private URL seleniumUrl(Cluster cluster) throws MalformedURLException {
        DockerPort port = cluster.container("selenium").portMappedInternallyTo(4444);
        return new URL(port.inFormat("http://$HOST:$EXTERNAL_PORT/wd/hub"));
    }

    private String vncUrl(Cluster cluster) {
        DockerPort port = cluster.container("selenium").portMappedInternallyTo(5900);
        return port.inFormat("vnc://username:secret@$HOST:$EXTERNAL_IP");
    }

    private WebDriver createDriver(URL remoteAddress) throws MalformedURLException {
        return new RemoteWebDriver(remoteAddress, DesiredCapabilities.chrome());
    }
}

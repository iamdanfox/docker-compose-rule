/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.docker.compose.wait;

import static com.palantir.docker.compose.connection.waiting.ClusterHealthCheck.serviceHealthCheck;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.palantir.docker.compose.connection.Cluster;
import com.palantir.docker.compose.connection.Container;
import com.palantir.docker.compose.connection.waiting.ClusterHealthCheck;
import com.palantir.docker.compose.connection.waiting.ClusterWait;
import com.palantir.docker.compose.connection.waiting.HealthCheck;
import com.palantir.docker.compose.connection.waiting.MessageReportingClusterWait;
import java.util.List;
import org.joda.time.Duration;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class WaitRule implements TestRule {

    private final List<ClusterWait> clusterWaits;
    private final Cluster cluster;

    public WaitRule(List<ClusterWait> clusterWaits, Cluster cluster) {
        this.clusterWaits = clusterWaits;
        this.cluster = cluster;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try {
                    clusterWaits.forEach(wait -> wait.waitUntilReady(cluster));
                    base.evaluate();
                } finally {
                    System.out.print("done");
                }
            }
        };

    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * To be an customizable builder, we require that any method can be chained after any other
     * method, irrespective of whether the method was implemented in this library or added
     * as a mixin.
     *
     * To achieve this, we have one type parameter, B, and the method self().  All chainable
     * methods return B, so it it remains unconstrained right until the last minute, when someone
     * actually writes a concrete class (and they set B to be that concrete class).
     *
     * Since Java allows the eventual class to implement any number of interfaces, users can
     * add mixed-in chainable methods by implementing them as default methods on interfaces.
     * As long as they always return B, they will chain up beautifully.
     *
     * These user-added interfaces need to mutate the builder from their `default` methods,
     * so we must expose a few mutation methods to bootstrap everything.  In a wonderful turn of
     * fate, we can actually implement all this mutability in a nicely encapsulated way, by
     * providing an abstract class that only has private fields.
     */
    public interface BaseBuilderInterface<B> {

        // chaining magic - left unimplemented until the last class
        B self();

        // mutation methods
        ImmutableList.Builder<ClusterWait> waits();
        Cluster cluster();
        void setCluster(Cluster cluster);

        // core builder methods
        default WaitRule build() {
            Cluster cluster = cluster();
            Preconditions.checkNotNull(cluster, "cluster must not be null");
            return new WaitRule(waits().build(), cluster);
        }

        default B waitFor(ClusterWait element) {
            waits().add(element);
            return self();
        }

        default B cluster(Cluster cluster) {
            setCluster(cluster);
            return self();
        }
    }

    public static class Builder extends AbstractBuilder implements BuilderMixin1<Builder> {

        @Override
        public Builder self() {
            return this;
        }

    }

    public interface BuilderMixin1<B> extends BaseBuilderInterface<B> {

        default B waitingForService(String serviceName, HealthCheck<Container> healthCheck, Duration timeout) {
            ClusterHealthCheck clusterHealthCheck = serviceHealthCheck(serviceName, healthCheck);
            waitFor(new MessageReportingClusterWait(clusterHealthCheck, timeout));
            return self();
        }

        default B doNothingMethod() {
            return self();
        }

    }

    public static class AbstractBuilder {

        private ImmutableList.Builder<ClusterWait> waits = ImmutableList.builder();
        private Cluster cluster = null;

        public ImmutableList.Builder<ClusterWait> waits() {
            return waits;
        }
        public Cluster cluster() {
            return cluster;
        }
        public void setCluster(Cluster cluster) {
            this.cluster = cluster;
        }

    }

}

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

    public static ConcreteBuilder builder() {
        return new ConcreteBuilder();
    }

    public interface BaseBuilderInterface<B> {
        B self();
        ImmutableList.Builder<ClusterWait> waits();
        Cluster cluster();
        void setCluster(Cluster cluster);

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

    public interface IBuilder1<B> extends BaseBuilderInterface<B> {

        default B waitingForService(String serviceName, HealthCheck<Container> healthCheck, Duration timeout) {
            ClusterHealthCheck clusterHealthCheck = serviceHealthCheck(serviceName, healthCheck);
            waitFor(new MessageReportingClusterWait(clusterHealthCheck, timeout));
            return self();
        }

    }

    public interface IBuilder2<B> extends BaseBuilderInterface<B> {

        default B doNothingMethod() {
            return self();
        }

    }

    public static class ConcreteBuilder implements IBuilder1<ConcreteBuilder>, IBuilder2<ConcreteBuilder> {

        private ImmutableList.Builder<ClusterWait> waits = ImmutableList.builder();
        private Cluster cluster = null;

        @Override
        public ConcreteBuilder self() {
            return this;
        }
        @Override
        public ImmutableList.Builder<ClusterWait> waits() {
            return waits;
        }
        @Override
        public Cluster cluster() {
            return cluster;
        }
        @Override
        public void setCluster(Cluster cluster) {
            this.cluster = cluster;
        }

    }

}

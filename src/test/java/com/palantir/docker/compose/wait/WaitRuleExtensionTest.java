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

import static org.mockito.Mockito.mock;

import com.palantir.docker.compose.connection.Cluster;
import com.palantir.docker.compose.connection.waiting.ClusterWait;
import com.palantir.docker.compose.wait.WaitRule.AbstractBuilder;
import org.junit.Test;

public class WaitRuleExtensionTest {

    private WaitRule r1 = WaitRule.builder()
            .waitFor(mock(ClusterWait.class))
            .cluster(mock(Cluster.class))
            .build();

    private WaitRule r2 = new MyBuilder()
            .addTwoClusterWaits(mock(ClusterWait.class), mock(ClusterWait.class))
            .waitFor(mock(ClusterWait.class))
            .waitFor(mock(ClusterWait.class))
            .addTwoClusterWaits(mock(ClusterWait.class), mock(ClusterWait.class))
            .cluster(mock(Cluster.class))
            .build();

    private WaitRule r3 = new WaitRule.MyWaitRule.Builder()
            .waitForAllPorts("somecontainer")
            .build();

    @Test
    public void go() {

    }

    public interface MyMixin<B> extends WaitRule.FluentBuilderFeatures<B> {

        default B addTwoClusterWaits(ClusterWait one, ClusterWait two) {
            waitFor(one);
            waitFor(two);
            return self();
        }

    }

    public static class MyBuilder extends AbstractBuilder
            implements MyMixin<MyBuilder>, WaitRule.ExtraBuilderFeature<MyBuilder> {

        public MyBuilder addThreeWaits(ClusterWait one, ClusterWait two, ClusterWait three) {
            return waitFor(one).waitFor(two).waitFor(three);
        }

        @Override
        public MyBuilder self() {
            return this;
        }

    }
}

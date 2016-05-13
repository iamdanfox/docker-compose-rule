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

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import com.google.common.collect.ImmutableList;
import com.palantir.docker.compose.connection.Cluster;
import com.palantir.docker.compose.connection.waiting.ClusterWait;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.mockito.InOrder;

public class WaitRuleTest {

    private Statement base = mock(Statement.class);
    private Description description = mock(Description.class);
    private static Cluster cluster = mock(Cluster.class);

    @Test
    public void ruleShauldWaitClusterWaitsThenExecuteTestMethod() {
        ClusterWait wait1 = mock(ClusterWait.class);
        ClusterWait wait2 = mock(ClusterWait.class);
        WaitRule rule = new WaitRule(ImmutableList.of(wait1, wait2), cluster);
        simulateJunitRun(rule);

        InOrder inOrder = inOrder(base, wait1, wait2);
        inOrder.verify(wait1).waitUntilReady(cluster);
        inOrder.verify(wait2).waitUntilReady(cluster);
        try {
            inOrder.verify(base).evaluate();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        inOrder.verifyNoMoreInteractions();
    }


    private void simulateJunitRun(WaitRule rule) {
        try {
            rule.apply(base, description).evaluate();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}

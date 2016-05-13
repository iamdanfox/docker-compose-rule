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

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.palantir.docker.compose.DockerComposeRule;
import com.palantir.docker.compose.connection.Cluster;
import com.palantir.docker.compose.connection.waiting.ClusterWait;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.mockito.InOrder;

public class WaitRuleTest {

    @Rule
    public final ExpectedException exception = ExpectedException.none();
    private Statement base = mock(Statement.class);
    private Description description = mock(Description.class);
    private static DockerComposeRule docker = mock(DockerComposeRule.class);
    private static Cluster cluster = mock(Cluster.class);

    private static class EmptySuite {}

    @BeforeClass
    public static void before() {
        when(docker.containers()).thenReturn(cluster);
    }

    @Test
    public void shouldExplodeIfNoWaitForAnnotations() {
        exception.expect(IllegalStateException.class);
        exception.expectMessage("WaitRule requires at least one @WaitFor annotated field.");
        EmptySuite testSuite = new EmptySuite();
        new WaitRule(testSuite);
    }

    private static class SuiteWithBadField {
        @WaitFor
        public static final String service1 = "broken";
    }

    @Test
    public void shouldExplodeIfWaitForAnnotatedFieldIsntClusterWait() {
        exception.expect(RuntimeException.class);
        exception.expectMessage("Field service1 must be a ClusterWait or List<ClusterWait>.");
        SuiteWithBadField suite = new SuiteWithBadField();
        new WaitRule(suite);
    }

    private static class SuiteWithTwoFields {
        public static final DockerComposeRule dcr = docker;
        @WaitFor
        public static final ClusterWait service1 = mock(ClusterWait.class);
        @WaitFor
        public static final ClusterWait service2 = mock(ClusterWait.class);
    }

    @Test
    public void ruleShouldExplodeIfMultipleAnnotationsClusterWaits() {
        exception.expect(IllegalStateException.class);
        exception.expectMessage("Only one @WaitFor field allowed - "
                + "please pass a List<ClusterWait> if you want multiple.");
        new WaitRule(new SuiteWithTwoFields());
    }

    private static class SuiteWithCorrectField {
        public static final DockerComposeRule dcr = docker;
        @WaitFor
        public static final ClusterWait service1 = mock(ClusterWait.class);
    }

    @Test
    public void ruleShouldInitialiseHappilyIfAnnotationsAreGood() {
        new WaitRule(new SuiteWithCorrectField());
    }

    private static class SuiteWithAnnotatedList {
        public static final DockerComposeRule dcr = docker;
        @WaitFor
        public static final List<ClusterWait> services = ImmutableList.of(
                mock(ClusterWait.class),
                mock(ClusterWait.class));
    }

    @Test
    public void ruleShouldInitialiseHappilyIfListIsAnnotatedWithWaitFor() {
        new WaitRule(new SuiteWithAnnotatedList());
    }

    @Test
    public void ruleShauldWaitClusterWaitsThenExecuteTestMethod() {
        SuiteWithCorrectField suite = new SuiteWithCorrectField();

        WaitRule rule = new WaitRule(suite);
        simulateJunitRun(rule);

        InOrder inOrder = inOrder(base, suite.service1);
        inOrder.verify(suite.service1).waitUntilReady(any());
        try {
            inOrder.verify(base).evaluate();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        inOrder.verifyNoMoreInteractions();
    }

    private static class SuiteWithDCR {
        public static final DockerComposeRule dcr = docker;
        @WaitFor
        public static final ClusterWait service1 = mock(ClusterWait.class);
    }

    @Test
    public void ruleShouldPassClusterFromDockerComposeRuleAutomatically() {
        SuiteWithDCR suite = new SuiteWithDCR();

        WaitRule rule = new WaitRule(suite);
        simulateJunitRun(rule);

        verify(suite.service1).waitUntilReady(cluster);
    }

    private void simulateJunitRun(WaitRule rule) {
        try {
            rule.apply(base, description).evaluate();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
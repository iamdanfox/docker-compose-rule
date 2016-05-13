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

package com.palantir.docker.compose;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.palantir.docker.compose.connection.waiting.ClusterWait;
import com.palantir.docker.compose.wait.WaitFor;
import com.palantir.docker.compose.wait.WaitRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.mockito.InOrder;

public class WaitRuleTest {

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    private static class EmptySuite {}

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
        exception.expectMessage("Field service1 must be a ClusterWait.");
        SuiteWithBadField suite = new SuiteWithBadField();
        new WaitRule(suite);
    }

    private static class SuiteWithCorrectField {
        @WaitFor
        public static final ClusterWait service1 = mock(ClusterWait.class);
    }

    @Test
    public void ruleShouldInitialiseHappilyIfAnnotationsAreGood() {
        SuiteWithCorrectField suite = new SuiteWithCorrectField();
        new WaitRule(suite);
    }

    private Statement base = mock(Statement.class);
    private Description description = mock(Description.class);

    private static class SuiteWithTwoFields {
        @WaitFor
        public static final ClusterWait service1 = mock(ClusterWait.class);
        @WaitFor
        public static final ClusterWait service2 = mock(ClusterWait.class);
    }

    @Test
    public void ruleShouldWaitForAllClusterWaits() {
        SuiteWithTwoFields suite = new SuiteWithTwoFields();
        WaitRule rule = new WaitRule(suite);

        simulateJunitRun(rule);

        verify(suite.service1).waitUntilReady(any());
        verify(suite.service2).waitUntilReady(any());
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

    private void simulateJunitRun(WaitRule rule) {
        try {
            rule.apply(base, description).evaluate();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}

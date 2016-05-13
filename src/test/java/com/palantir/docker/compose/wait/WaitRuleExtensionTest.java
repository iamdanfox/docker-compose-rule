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
import org.junit.Test;

public class WaitRuleExtensionTest {

    private WaitRule r1 = WaitRule.builder()
            .waitFor(mock(ClusterWait.class))
            .waitingForService(null, null, null)
            .waitingForService(null, null, null)
            .cluster(mock(Cluster.class))
            .doNothingMethod()
            .build();

    @Test
    public void go() {

    }

}

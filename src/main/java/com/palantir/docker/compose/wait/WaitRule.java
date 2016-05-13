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

import static java.util.stream.Collectors.toList;

import com.palantir.docker.compose.DockerComposeRule;
import com.palantir.docker.compose.connection.Cluster;
import com.palantir.docker.compose.connection.waiting.ClusterWait;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class WaitRule implements TestRule {

    private final List<ClusterWait> clusterWaits;
    private final Cluster cluster;

    public WaitRule(Object suite) {
        List<Field> allAnnotatedFields = allWaitForAnnotatedFields(suite);
        clusterWaits = clusterWaits(allAnnotatedFields, suite);
        cluster = extractClusterFromSuite(suite);
    }

    private static List<Field> allWaitForAnnotatedFields(Object suite) {
        List<Field> list = Arrays.stream(suite.getClass().getFields())
                .filter(field -> field.getAnnotation(WaitFor.class) != null)
                .collect(toList());
        if (list.isEmpty()) {
            throw new IllegalStateException(
                    "WaitRule requires at least one @WaitFor annotated field.");
        }
        if (list.size() > 1) {
            throw new IllegalStateException(
                    "Only one @WaitFor field allowed - please pass a List<ClusterWait> if you want multiple.");
        }
        return list;
    }

    private static List<ClusterWait> clusterWaits(List<Field> annotatedFields, Object instance) {
        return annotatedFields.stream()
                .peek(field -> field.setAccessible(true))
                .flatMap(field -> {
                    try {
                        Object fieldValue = field.get(instance);

                        if (ClusterWait.class.isAssignableFrom(fieldValue.getClass())) {
                            return Stream.of((ClusterWait) fieldValue);
                        }

                        if (List.class.isAssignableFrom(fieldValue.getClass())) {
                            List<ClusterWait> list = (List<ClusterWait>) fieldValue;
                            return list.stream();
                        }

                        throw new RuntimeException(String.format(
                                "Field %s must be a ClusterWait or List<ClusterWait>.", field.getName()));
                    } catch (Exception e) {
                        throw new RuntimeException(String.format(
                                "Field %s must be a ClusterWait or List<ClusterWait>.", field.getName()), e);
                    }
                })
                .collect(toList());
    }

    private static Cluster extractClusterFromSuite(Object suite) {
        return Arrays.stream(suite.getClass().getFields())
                .peek(f -> f.setAccessible(true))
                .flatMap(f -> {
                    try {
                        DockerComposeRule docker = (DockerComposeRule) f.get(suite);
                        return Stream.of(docker);
                    } catch (Exception e) {
                        return Stream.empty();
                    }
                })
                .findFirst()
                .map(DockerComposeRule::containers)
                .orElseThrow(() -> new IllegalStateException("WaitRule requires a DockerComposeRule field to extract a cluster from"));

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

}

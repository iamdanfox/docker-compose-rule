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

import com.palantir.docker.compose.connection.waiting.ClusterWait;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class WaitRule implements TestRule {

    private List<ClusterWait> clusterWaits;

    public WaitRule(Object instance) {
        Class<?> clazz = instance.getClass();
        List<Field> allAnnotatedFields = allAnnotatedFields(clazz);
        clusterWaits = clusterWaits(allAnnotatedFields, instance);
    }

    private static List<Field> allAnnotatedFields(Class<?> clazz) {
        List<Field> list = Arrays.stream(clazz.getFields())
                .filter(field -> field.getAnnotation(WaitFor.class) != null)
                .collect(toList());
        if (list.isEmpty()) {
            throw new IllegalStateException(
                    "WaitRule requires at least one @WaitFor annotated field.");
        }
        return list;
    }

    private static List<ClusterWait> clusterWaits(List<Field> annotatedFields, Object instance) {
        return annotatedFields.stream()
                .peek(field -> field.setAccessible(true))
                .map(field -> {
                    try {
                        return (ClusterWait) field.get(instance);
                    } catch (Exception e) {
                        throw new RuntimeException(String.format(
                                "Field %s must be a ClusterWait.", field.getName()));
                    }
                })
                .collect(toList());
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try {
                    clusterWaits.forEach(wait -> wait.waitUntilReady(null));
                    base.evaluate();
                } finally {
                    System.out.print("done");
                }
            }
        };

    }

}

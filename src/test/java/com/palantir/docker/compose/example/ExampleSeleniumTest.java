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
package com.palantir.docker.compose.example;

import static com.palantir.docker.compose.example.MyServices.SELENIUM;

import com.palantir.docker.compose.DockerComposeRule;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.openqa.selenium.WebDriver;

public class ExampleSeleniumTest {

    @ClassRule
    public static final DockerComposeRule rule = DockerComposeRule.builder()
            .file("src/test/resources/example-docker-compose.yml")
            .addClusterWait(SELENIUM)
            .build();

    @BeforeClass
    public static void beforeClass() throws Exception {
        //SELENIUM.openVNC(rule);
    }

    @Test
    public void someTest() throws Exception {
        WebDriver driver = SELENIUM.driver(rule);
        driver.get("https://google.com/");
    }

}

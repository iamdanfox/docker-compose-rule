/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */
package com.palantir.docker.compose.example;

public final class MyServices {
    private MyServices() {}

    public static final SeleniumService SELENIUM = new SeleniumService();
}

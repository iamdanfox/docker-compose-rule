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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.palantir.docker.compose.DockerComposeRule;
import com.palantir.docker.compose.connection.Cluster;
import com.palantir.docker.compose.connection.waiting.ClusterHealthCheck;
import com.palantir.docker.compose.connection.waiting.ClusterWait;
import com.palantir.docker.compose.connection.waiting.HealthChecks;
import com.palantir.docker.compose.connection.waiting.MessageReportingClusterWait;
import java.util.List;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/*
 * WE NEED BETTER BUILDERS
 *
 * The Java Builder pattern is delightfully ergonomic but I've recently been frustrated by
 * their lack of customizability (without completely a re-implementation).
 *
 * I've been using DockerComposeRule's builder to orchestrate end-to-end tests.
 * DockerComposeRule lets you fire up a docker-compose
 * cluster, save logs from them and wait for containers to start up fully:
 *
       @ClassRule
       static DockerComposeRule advanced = DockerComposeRule.builder()
               .files(Utils.loadYmlResourceFor(MyTest.class))
               .saveLogsTo(Utils.logsLocationFor(MyTest.class))
               .projectName(Utils.projectNameFor(MyTest.class))
               .addClusterWait(new MessageReportingClusterWait(
                       serviceHealthCheck("hadoop", toHaveAllPortsOpen())))
               .addClusterWait(SELENIUM)
               .addClusterWait(MY_SERVICE)
               .skipShutdown(Utils.skipShutdownExceptOnCI())
               .build();

 * Blocks of code like this quickly start cropping up in lots of tests, each time with a few
 * subtle differences.
 * Clearly, it's not the prettiest.
 * I'm calling a bunch of utility methods to automatically fill in parameters, and that
 * big old MessageReportingClusterWait is ugly.
 *
 * Also, 11 lines of code is a bit excessive for every MY_SERVICE test,
 * so it could be wrapped up in a factory method:
 *
         @ClassRule
         static DockerComposeRule docker = MyDockerComposeRule.for(MyTest.class);

 * The trouble is, I need to vary the containers that I wait for.  No problem, I can just
 * return the builder:
 *
         @ClassRule
         static DockerComposeRule docker = MyDockerComposeRule.builderFor(MyTest.class)
                   .addClusterWait(new MessageReportingClusterWait(
                           serviceHealthCheck("fake-s3", toHaveAllPortsOpen())))
                   .addClusterWait(SELENIUM)
                   .addClusterWait(MY_SERVICE)
                   .build()

 * This is slightly better, but that MessageReportingClusterWait thing is ugly and often
 * duplicated. Ideally, there would be a nice fluent method:
 *
 *       .waitForAllPorts("fake-s3")
 *
 * For some libraries, it's totally feasible to just submit a PR and add a builder method.
 * However, sometimes your helper methods are very specific to your project and might not
 * be accepted into the upstream repo.  For example, you probably couldn't get a method
 * added to Feign that would add a highly customized ObjectMapper, `mapper`:
 *
 *      .encoder(new InputStreamDelegateEncoder(new JacksonEncoder(mapper)))
        .decoder(new OptionalAwareDecoder(
                new InputStreamDelegateDecoder(
                        new TextDelegateDecoder(
                                new JacksonDecoder(mapper)))))
 *
 * I don't think we should settle for this. I want convenience methods and I want shorthands,
 * but library maintainers clearly can't accept everyone's special-snowflake contributions.
 *
 * What we really need is an extensible builder - then I could add crazy stuff to
 * DockerComposeRule like:
 *
        .waitForHttpsEndpointIgnoringCerts("someservice", 443, "/healthcheck")
        .waitForAtLeastOneOf(DB1, DB2, DB3)

 * A MIXIN BUILDER:
 *
 * I think we can write a better, customizable builder for our libraries.
 * It'll have the same fluent API that we know and love, but
 * users will be able to stick their own methods right in the middle of their chain.
 *
 */

/**
 * I've extracted the waiting behaviour from DockerComposeRule into `WaitRule` to provide
 * a concise example.
 * WaitRule lets us check Docker containers have started up before
 * we start hitting them with our tests.
 */
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
                clusterWaits.forEach(wait -> wait.waitUntilReady(cluster));
                base.evaluate();
            }
        };

    }

    /**
     * My first thought was: why don't we just let people extend the Builder?  Like many first
     * attempts, this turned out to be pretty dumb.  We can write our lovely builder:
     *
     *     public class Builder {
     *
     *         public WaitRule build() { ... }
     *
     *         public Builder waitFor(ClusterWait wait) { ... }
     *
     *         public Builder cluster(Cluster c) { ... }
     *
     *     }
     *
     * But as soon as someone tries to extend it, they hit a problem: some method orderings
     * don't compile.
     *
     *     public class MyBuilder extends Builder {
     *         public MyBuilder waitForAtLeastOneOf(ClusterWait... waits) { ... }
     *     }
     *
     *     // this seems to work:
     *
     *     new MyBuilder()
     *         .waitForAtLeastOneOf(SOME_SERVICE, ANOTHER_SERVICE)
     *         .cluster(docker.cluster())
     *         .build();
     *
     *     // but this doesn't compile:
     *
     *     new MyBuilder()
     *         .cluster(docker.cluster()) // <- returns `Builder` not a `MyBuilder`!
     *         .waitForAtLeastOneOf(SOME_SERVICE, ANOTHER_SERVICE) // <- error :(
     *         .build()
     *
     * This is not good enough. As a user, I want my custom methods to chain up nicely
     * with the library's official ones, regardless of the order I put them in.
     *
     * To achieve this, we're going to need generics. Introducing the `Chainable` interface.
     * It has just one type parameter and one method.
     *
     * We'll require that all builder methods return this parameter B,
     * which will remain unconstrained right until the last minute, when someone
     * actually writes a non-generic, concrete class.
     */

    public interface Chainable<B> {
        B self();
    }

    /**
     * Now since Java allows classes to implement any number of interfaces, we can almost
     * simulate 'mixins' by implementing default methods on interfaces.
     * As long as they always return B, they will chain up beautifully.
     *
     * These user-added interfaces will mutate the builder state from their `default` methods,
     * so we must expose a few getter and setter methods to bootstrap everything.
     */
    public interface BaseMutability {
        ImmutableList.Builder<ClusterWait> waits();
        Cluster getCluster();
        void setCluster(Cluster cluster);

        default WaitRule build() {
            Cluster cluster = getCluster();
            Preconditions.checkNotNull(cluster, "cluster must not be null");
            return new WaitRule(waits().build(), cluster);
        }
    }

    /**
     * We can then implement some nice ergonomic builder methods, by utilising the
     * BaseMutability and Chainable interfaces.
     *
     * We keep the type parameter B so that this interface can be further extended.
     */
    public interface FluentBuilderFeatures<B> extends BaseMutability, Chainable<B> {
        default B waitFor(ClusterWait element) {
            waits().add(element);
            return self();
        }

        default B cluster(Cluster cluster) {
            setCluster(cluster);
            return self();
        }
    }

    /**
     * Conveniently, we can actually implement all the mutability
     * in a nice encapsulated way by writing an abstract class that only has private fields.
     */
    public static class AbstractBuilder implements BaseMutability {

        private ImmutableList.Builder<ClusterWait> waits = ImmutableList.builder();
        private Cluster cluster = null;

        @Override
        public ImmutableList.Builder<ClusterWait> waits() {
            return waits;
        }
        @Override
        public Cluster getCluster() {
            return cluster;
        }
        @Override
        public void setCluster(Cluster cluster) {
            this.cluster = cluster;
        }

    }

    /**
     * The WaitRule library should supply at least a basic builder.
     * By implementing `self` and nailing down that type parameter, all the Chainable
     * mixins from above will returns this concrete builder class.
     */
    public static class Builder extends AbstractBuilder
            implements FluentBuilderFeatures<Builder> {

        @Override
        public Builder self() {
            return this;
        }

    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * When a user wants to extend this builder, they can simply
     * write an interface.  Here's that `waitForAllPorts` method I mentioned above.
     */
    public interface ExtraBuilderFeature<B> extends BaseMutability, Chainable<B> {

        default B waitForAllPorts(String containerName) {
            ClusterHealthCheck check = ClusterHealthCheck.serviceHealthCheck(
                    containerName,
                    HealthChecks.toHaveAllPortsOpen());

            waits().add(new MessageReportingClusterWait(
                    check,
                    DockerComposeRule.DEFAULT_TIMEOUT));

            return self();
        }

    }

    /**
     * All they have to do now is pull that `ExtraBuilderFeature` interface into a concrete class.
     */
    public static class MyWaitRule {
        public static class Builder extends AbstractBuilder
                implements FluentBuilderFeatures<Builder>, ExtraBuilderFeature<Builder> {

            @Override
            public Builder self() {
                return this;
            }

        }
    }

    /**
     * To conclude, we've seen how Java builders can get unwieldy.  We tried to write an
     * inheritance-based builder (and failed).  Finally, we used Java 8 interfaces to
     * write a completely customizable builder for `WaitRule` and added
     * the `waitForAllPorts` method.
     *
     *      @ClassRule
            static WaitRule rule = new MyWaitRule.Builder()
                    .waitForAllPorts("somecontainer")
                    .waitFor(SERVICE)
                    .build();
     *
     * I don't think this approach is suitable for all builders - we used 4 interfaces and both
     * an abstract and a concrete class just to implement one builder.  Nonetheless, if you're
     * writing a library and you want to make it as ergonomic as possible, I think it's
     * worth considering.
     */

}

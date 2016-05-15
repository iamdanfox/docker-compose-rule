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
import com.palantir.docker.compose.connection.Cluster;
import com.palantir.docker.compose.connection.Container;
import com.palantir.docker.compose.connection.waiting.ClusterHealthCheck;
import com.palantir.docker.compose.connection.waiting.ClusterWait;
import com.palantir.docker.compose.connection.waiting.HealthCheck;
import com.palantir.docker.compose.connection.waiting.MessageReportingClusterWait;
import java.util.List;
import org.joda.time.Duration;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

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

    public static Builder builder() {
        return new Builder();
    }

    /*
     * WE NEED BETTER BUILDERS
     *
     * The Java Builder pattern is delightfully ergonomic. I've been using DockerComposeRule
     * recently to orchestrate end-to-end tests have started noticing a few shortcomings
     * with the builder pattern in general.
     *
     * DockerComposeRule is nice and simple to start with. You can fire up a docker-compose
     * cluster with the following:
     *
     *     @ClassRule
     *     static DockerComposeRule simple = DockerComposeRule.builder()
                   .files(dockerComposeYml)
                   .build();

     * A more advanced usage might involve saving logs and waiting until some containers
     * have fully started up:
     *
           @ClassRule
           static DockerComposeRule advanced = DockerComposeRule.builder()
                   .files(Utils.loadYmlResourceFor(MyTest.class))
                   .saveLogsTo(Utils.logsLocationFor(MyTest.class))
                   .projectName(Utils.projectNameFor(MyTest.class))
                   .addClusterWait(HADOOP)
                   .addClusterWait(SELENIUM)
                   .addClusterWait(new MessageReportingClusterWait(
                       serviceHealthCheck("myservice", toRespondOverHttp(8081, "/healthcheck"))))
                   .skipShutdown(Utils.skipShutdownExceptOnCI())
                   .build();

     * This block of code quickly starts cropping up in lots of tests, each time with a few subtle
     * differences.
     *
         * You don't really want to write 11 lines of code every time you need an http client.
         * You do the sensible thing, you wrap it in all up in a factory somewhere.
         * Now you can just write:
         *
         *      FeignClients.standard(SomeService.class, url);
         *
         * Problem solved.  Except it's not really solved.  You started with the wonderfully
         * flexible Feign API and now you've hard coded all your defaults and no-one can change them.
         *
         * Let's say you just want to swap out that Object Mapper.  guavaJdk7() is fine most of the
         * time, but for a few hot new products you want to use guavaJdk8().  You eye that
         * factory method.  Maybe you should add a parameter?
         * Maybe you should just return the unfinished Netflix builder and let people build() it
         * themselves?
         *
         * Neither are satisfactory in my opinion.  What starts out as one extra factory method:
         *
         *     FeignClients.standard(ObjectMapper mapper, Class<?> interface, String url)
         *
         * Quickly scope creeps into a nightmare
         *
         *     FeignClients.standard(ObjectMapper mapper, boolean disableClientSsl, int maxRetries, Class<?> interface, String url).
         *
         * Returning a builder doesn't really help either, because we still duplicate a ton of code
         * just to put in the guavaJdk8() mapper:
         *
         *      FeignClients.defaultBuilder()
         *          .encoder(new InputStreamDelegateEncoder(new JacksonEncoder(ObjectMappers.guavaJdk8())))
                    .decoder(new OptionalAwareDecoder(
                            new InputStreamDelegateDecoder(
                                    new TextDelegateDecoder(
                                            new JacksonDecoder(ObjectMappers.guavaJdk8())))))
                    .target(SomeService.class, url)
                    .build();

     * I don't want to settle for this. I want maximum configurability from a library,
     * but as soon as I start using it, I want to set all my defaults. I want convenience methods.
     * I want shorthands, but I don't want to sacrifice any of the library's flexibility.
     *
     * Ideally, I want to be able to define my own shorthands add them to the library.
     *
     *      MyFeign.builder()
     *              .innerObjectMapper(ObjectMappers.guaveJdk8())
     *              .build()
     *
     * A MIXIN BUILDER:
     *
     * I think we can write a better, customizable builder for our libraries.
     * It'll have the same fluent API that we know and love, but
     * users will be able to stick their own methods right in the middle of their chain.
     *
     */

    /**
     * Let's use the `WaitRule` class as an example.  It's a tiny JUnit TestRule that lets
     * us check Docker containers have started up before we start hitting them with our
     * tests.
     *
     * My first thought was: why don't we just let people extend our Builder?  Like many first
     * attempts, this turned out to be pretty dumb.  We can write our lovely builder:
     *
     *     public class Builder {
     *
     *         public WaitRule build();
     *
     *         public Builder waitFor(ClusterWait wait);
     *
     *         public Builder cluster(Cluster c);
     *     }
     *
     * But as soon as someone tries to extend it, they hit a problem: some method orderings
     * don't compile.
     *
     *     public class MyBuilder extends Builder {
     *
     *         public MyBuilder waitForAll(ClusterWait... waits);
     *
     *     }
     *
     *     // this seems to work:
     *
     *     new MyBuilder()
     *         .waitForAll(SOME_SERVICE, ANOTHER_SERVICE)
     *         .cluster(docker.cluster())
     *         .build();
     *
     *     // but this doesn't compile:
     *
     *     new MyBuilder()
     *         .cluster(docker.cluster()) // <- returns `Builder` not a `MyBuilder`!
     *         .waitForAll(SOME_SERVICE, ANOTHER_SERVICE) // <- error :(
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
    public interface NiceBuilderFeatures<B> extends BaseMutability, Chainable<B> {
        default B waitFor(ClusterWait element) {
            waits().add(element);
            return self();
        }

        default B cluster(Cluster cluster) {
            setCluster(cluster);
            return self();
        }
    }

    public interface ExtraHelperFeature<B> extends BaseMutability, Chainable<B> {

        default B waitForContainer(String containerName, HealthCheck<Container> containerCheck) {
            ClusterHealthCheck clusterCheck = ClusterHealthCheck.serviceHealthCheck(
                    containerName, containerCheck);
            ClusterWait wait = new MessageReportingClusterWait(
                    clusterCheck, Duration.standardMinutes(2));
            waits().add(wait);
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
     * Finally, when we a user wants to actually use their builder, they simply
     * extend the AbstractBuilder with whatever selection of chainable mixins they want.
     *
     * By implementing `self` and nailing down that type parameter, all the Chainable
     * mixins from above will returns this concrete builder class.
     */
    public static class Builder extends AbstractBuilder
            implements NiceBuilderFeatures<Builder>, ExtraHelperFeature<Builder> {

        @Override
        public Builder self() {
            return this;
        }

    }

    /**
     * To conclude, we've seen how even the best Java builders can get unwieldy when you
     * want to re-use defaults or re-use some construction code.  We tried to write an
     * inheritance-based builder (and failed).  Finally, we used Java 8 interfaces to
     * write a completely customizable builder.
     *
     * Now I don't think this approach is suitable for all builders - we used 4 interfaces and both
     * an abstract and a concrete class just to implement one builder.  Nonetheless, if you're
     * writing a library and you want to make it as ergonomic as possible, I think it's worth a shot.
     */

}

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
import com.palantir.docker.compose.connection.waiting.ClusterWait;
import java.util.List;
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
                try {
                    clusterWaits.forEach(wait -> wait.waitUntilReady(cluster));
                    base.evaluate();
                } finally {
                    System.out.print("done");
                }
            }
        };

    }

    public static Builder builder() {
        return new Builder();
    }

    /*
     * WE NEED BETTER BUILDERS
     *
     * The Java Builder pattern is delightfully ergonomic.  Netflix's Feign is one of my
     * favourites.  You use it out of the box:
     *
     *      Feign.builder().build()
     *
     * Or customize it with all your favourite options:
     *
            Feign.builder()
                .contract(new GuavaOptionalAwareContract(new JAXRSContract()))
                .encoder(new InputStreamDelegateEncoder(new JacksonEncoder(ObjectMappers.guavaJdk7())))
                .decoder(new OptionalAwareDecoder(
                        new InputStreamDelegateDecoder(
                                new TextDelegateDecoder(
                                        new JacksonDecoder(ObjectMappers.guavaJdk7())))))
                .errorDecoder(SerializableErrorErrorDecoder.INSTANCE)
                .retryer(NeverRetryingBackoffStrategy.INSTANCE)
                .client(FeignClientFactory.okHttpClient())
                .build();

     * Now, let's say that blob of code above works perfectly for you. You're feeling pretty
     * pleased that you've figured out all those clever options.
     * It's just a bit unwieldy right now.
     * You don't really want to write 11 lines of code every time you need an http client.
     * You do the sensible thing, you wrap it in all up in a factory somewhere.
     * Now you can just write:
     *
     *      FeignClients.standard();
     *
     * Problem solved.  Until you sit down and realise that you started with the wonderfully
     * flexible Feign API and now you're jamming your defaults down everyone's throats.
     *
     * Let's say you just want to swap out that Object Mapper.  guavaJdk7() is fine most of the
     * time, but for a few hot new products you want to use ObjectMappers.guavaJdk8().  You stare
     * at your factory.  Maybe you should add a parameter?
     * Maybe you should just return the unfinished Netflix builder and let people call more methods
     * and then build() it themselves?
     *
     * Neither are quite satisfactory in my opinion.  What starts out as one extra factory method:
     *
     *     FeignClients.standard(ObjectMapper mapper)
     *
     * Quickly scope creeps into a nightmare
     *
     *     FeignClients.standard(ObjectMapper mapper, boolean disableClientSsl, int maxRetries).
     *
     * Returning a builder doesn't really help either:
     *
     *      FeignClients.defaultBuilder()
     *          .encoder(new InputStreamDelegateEncoder(new JacksonEncoder(ObjectMappers.guavaJdk8())))
                .decoder(new OptionalAwareDecoder(
                        new InputStreamDelegateDecoder(
                                new TextDelegateDecoder(
                                        new JacksonDecoder(ObjectMappers.guavaJdk8())))))
                .build();

     * I don't want to settle for this. I want maximum configurability from a library,
     * but as soon as I start using it, I want to set all my defaults. I want convenience methods.
     * I want shorthands, but I don't want to sacrifice any of the library's flexibility.
     *
     * I want to be able to define my own shorthands add them to the library.
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
     * My first thought was why don't we just let people extend our Builder?  Like many first
     * attempts, this turns out to be pretty dumb.  We can write our lovely builder:
     *
     *     public class Builder {
     *         public WaitRule build();
     *
     *         public Builder waitFor(ClusterWait wait);
     *
     *         public Builder cluster(Cluster c);
     *     }
     *
     * But as soon as someone tries to it, they hit a problem:
     *
     *     public class MyBuilder extends Builder {
     *
     *         public MyBuilder waitForAll(ClusterWait... waits);
     *
     *     }
     *
     *     // trivial usage seems to work:
     *
     *     new MyBuilder()
     *         .waitForAll(SOME_SERVICE, ANOTHER_SERVICE)
     *         .cluster(docker.cluster());
     *
     *     // but alas this doesn't work
     *
     *     new MyBuilder()
     *         .cluster(docker.cluster()) // <- returns `Builder` not a `MyBuilder`!
     *         .waitForAll(SOME_SERVICE, ANOTHER_SERVICE); // <- error :(
     *
     *
     * Let's start with the chaining problem.  As a library author, we want all our users' builder
     * methods to chain up nicely with our own 'official' ones.
     *
     * To make this work, we're going to need one generic interface.
     * It has a type parameter, B, and the method self().
     * We'll make require that the all our users' custom methods return this parameter B,
     * so it it remains unconstrained right until the last minute, when someone
     * actually writes a concrete class (and they set B to be that concrete class).
     */
    public interface Chainable<B> {
        B self();
    }

    /**
     * Since Java allows the eventual class to implement any number of interfaces, users can
     * add mixed-in chainable methods by implementing them as default methods on interfaces.
     * As long as they always return B, they will chain up beautifully.
     *
     * These user-added interfaces need to mutate the builder from their `default` methods,
     * so we must expose a few mutation methods to bootstrap everything.
     */
    public interface BaseMutability {
        ImmutableList.Builder<ClusterWait> waits();
        Cluster cluster();
        void setCluster(Cluster cluster);

        default WaitRule build() {
            Cluster cluster = cluster();
            Preconditions.checkNotNull(cluster, "cluster must not be null");
            return new WaitRule(waits().build(), cluster);
        }
    }

    /**
     * In a wonderful turn of fate, we can actually implement all this mutability
     * in a nicely encapsulated way, by providing an abstract class that
     * only has private fields.
     */
    public static class AbstractBuilder implements BaseMutability {

        private ImmutableList.Builder<ClusterWait> waits = ImmutableList.builder();
        private Cluster cluster = null;

        @Override
        public ImmutableList.Builder<ClusterWait> waits() {
            return waits;
        }
        @Override
        public Cluster cluster() {
            return cluster;
        }
        @Override
        public void setCluster(Cluster cluster) {
            this.cluster = cluster;
        }

    }

    /**
     * We can then implement some nice ergonomic builder methods, by utilising the
     * BaseMutability and Chainable interfaces.
     *
     * We keep the type parameter B so that this interface can be further extended.
     */
    public interface NiceFeature1<B> extends BaseMutability, Chainable<B> {
        default B waitFor(ClusterWait element) {
            waits().add(element);
            return self();
        }
    }

    public interface NiceFeature2<B> extends BaseMutability, Chainable<B> {
        default B cluster(Cluster cluster) {
            setCluster(cluster);
            return self();
        }
    }

    /**
     * Finally, when we a user wants to actually use their builder, they simply
     * extend the AbstractBuilder with whatever selection of chainable mixins they want.
     *
     * By implementing `self` and nailing down that type parameter, all the Chainable
     * mixins from above will returns this class.
     */
    public static class Builder extends AbstractBuilder
            implements NiceFeature1<Builder>, NiceFeature2<Builder> {

        @Override
        public Builder self() {
            return this;
        }

    }

}

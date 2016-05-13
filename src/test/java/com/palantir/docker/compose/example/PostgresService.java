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

import static com.palantir.docker.compose.DockerComposeRule.DEFAULT_TIMEOUT;

import com.palantir.docker.compose.connection.Cluster;
import com.palantir.docker.compose.connection.DockerPort;
import com.palantir.docker.compose.connection.waiting.ClusterWait;
import com.palantir.docker.compose.connection.waiting.MessageReportingClusterWait;
import com.palantir.docker.compose.connection.waiting.SuccessOrFailure;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.dbunit.IDatabaseTester;
import org.dbunit.JdbcDatabaseTester;

public class PostgresService implements ClusterWait {

    @Override
    public void waitUntilReady(Cluster cluster) {
        new MessageReportingClusterWait(this::isHealthy, DEFAULT_TIMEOUT).waitUntilReady(cluster);
    }

    private SuccessOrFailure isHealthy(Cluster cluster) {
        return SuccessOrFailure.onResultOf(() -> database(jdbcUrl(cluster)) != null);
    }

    public IDatabaseTester database(String jdbcUrl) throws SQLException, Exception {
        IDatabaseTester databaseTester = new JdbcDatabaseTester(
                "org.postgresql.Driver", jdbcUrl, "palantir", "palantir");

        try (Connection connection = databaseTester.getConnection().getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("SELECT 1;");
        }
        return databaseTester;
    }

    private String jdbcUrl(Cluster cluster) {
        DockerPort port = cluster.container("db").portMappedInternallyTo(5432);
        return port.inFormat("jdbc:postgresql//$HOST:$EXTERNAL_PORT/source");
    }

}

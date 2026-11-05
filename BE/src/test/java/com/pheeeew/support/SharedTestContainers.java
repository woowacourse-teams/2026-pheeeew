package com.pheeeew.support;

import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

public final class SharedTestContainers {

    private SharedTestContainers() {
    }

    public static PostgreSQLContainer postgis() {
        return PostgisHolder.INSTANCE;
    }

    private static final class PostgisHolder {

        private static final PostgreSQLContainer INSTANCE = start();

        private static PostgreSQLContainer start() {
            PostgreSQLContainer container = new PostgreSQLContainer(
                    DockerImageName.parse("postgis/postgis:17-3.5")
                            .asCompatibleSubstituteFor("postgres")
            );
            container.start();
            return container;
        }
    }
}

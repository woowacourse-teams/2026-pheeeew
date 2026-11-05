package com.pheeeew.support;

import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

// [실험 트리거] 캐시 측정용 런 B 를 돌리기 위한 변경이며 다음 커밋에서 되돌린다.
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

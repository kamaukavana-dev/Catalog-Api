## Phase 0 — Environment Verification Outputs

### `java -version`
```text
openjdk version "21.0.10" 2026-01-20
OpenJDK Runtime Environment (build 21.0.10+7-Ubuntu-124.04)
OpenJDK 64-Bit Server VM (build 21.0.10+7-Ubuntu-124.04, mixed mode, sharing)
```

### `mvn -version`
```text
Apache Maven 3.8.7
Maven home: /usr/share/maven
Java version: 21.0.10, vendor: Ubuntu, runtime: /usr/lib/jvm/java-21-openjdk-amd64
Default locale: en, platform encoding: UTF-8
OS name: "linux", version: "6.17.0-29-generic", arch: "amd64", family: "unix"
```

### Repo-local Maven used for this session (meets 3.9+ requirement)
```text
Apache Maven 3.9.9 (8e8579a9e76f7d015ee5ec7bfcdc97d260186937)
Maven home: /home/kavana-daniel/IdeaProjects/catalog-api/.tools/apache-maven-3.9.9
Java version: 21.0.10, vendor: Ubuntu, runtime: /usr/lib/jvm/java-21-openjdk-amd64
Default locale: en, platform encoding: UTF-8
OS name: "linux", version: "6.17.0-29-generic", arch: "amd64", family: "unix"
```

### `docker info | grep "Server Version"`
```text
Server Version: 29.1.3
```

### `cat pom.xml | grep -A2 "testcontainers"`
```text
        <testcontainers.version>1.20.4</testcontainers.version>
    </properties>

--
            <artifactId>spring-boot-testcontainers</artifactId>
            <scope>test</scope>
        </dependency>
--
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers</artifactId>
            <version>${testcontainers.version}</version>
            <scope>test</scope>
        </dependency>
--
            <groupId>org.testcontainers</groupId>
            <artifactId>postgresql</artifactId>
            <scope>test</scope>
--
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
```

### `cat pom.xml | grep -A2 "awaitility"`
```text
            <groupId>com.awaitility</groupId>
            <artifactId>awaitility</artifactId>
            <version>4.2.1</version>
            <scope>test</scope>
```

### `cat src/test/java/com/catalog/common/BaseIntegrationTest.java`
```java
package com.catalog.common;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");
}
```


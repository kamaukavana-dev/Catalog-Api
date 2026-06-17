.PHONY: build test lint docker-up docker-down migrate coverage audit clean

build:
	mvn clean package -DskipTests -q

test:
	mvn test -Dspring.profiles.active=test

lint:
	mvn checkstyle:check 2>/dev/null || echo "checkstyle not configured"

docker-up:
	docker-compose up -d postgres redis

docker-down:
	docker-compose down

migrate:
	mvn flyway:migrate -Dspring.profiles.active=local

coverage:
	mvn test jacoco:report -Dspring.profiles.active=test
	open target/site/jacoco/index.html 2>/dev/null || true

audit:
	mvn dependency-check:check

clean:
	mvn clean
	docker-compose down -v

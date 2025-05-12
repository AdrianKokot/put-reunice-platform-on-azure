### Build ###
FROM maven:3.6.1-jdk-11-slim AS build

RUN mkdir -p /workspace
WORKDIR /workspace
COPY pom.xml /workspace
COPY src /workspace/src
COPY emailTemplates /workspace/emailTemplates
COPY uploads /workspace/uploads
RUN mvn -f pom.xml clean package

### Run ###
FROM amazoncorretto:11-alpine-jdk
WORKDIR /app
COPY --from=build /workspace/target/*.jar ./app.jar
COPY --from=build /workspace/emailTemplates ./emailTemplates
COPY --from=build /workspace/uploads ./uploads
EXPOSE 8080

HEALTHCHECK --start-period=20s --interval=5s --timeout=10s --retries=5 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/health || exit 1

ENTRYPOINT ["java","-jar","app.jar"]

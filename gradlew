#!/bin/sh
# Gradle Wrapper script
GRADLE_OPTS="${GRADLE_OPTS:-""} -Xdock:name=$APP_NAME -Xdock:icon=$APP_HOME/media/gradle.icns"
APP_HOME="$(dirname "$(realpath "$0")")"
exec "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" "$@" 2>/dev/null || \
  java -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
    org.gradle.wrapper.GradleWrapperMain "$@"

#!/bin/sh
# Gradle wrapper stub - downloads the real wrapper jar if needed
# For CI, use gradle/actions/setup-gradle instead

APP_BASE_NAME=$(basename "$0")
APP_HOME=$(cd "$(dirname "$0")" && pwd)
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

# Download wrapper jar if missing
if [ ! -f "$CLASSPATH" ]; then
    echo "Gradle wrapper jar not found. Downloading..."
    WRAPPER_URL="https://raw.githubusercontent.com/gradle/gradle/v8.11.1/gradle/wrapper/gradle-wrapper.jar"
    curl -sL "$WRAPPER_URL" -o "$CLASSPATH" 2>/dev/null || true
    if [ ! -f "$CLASSPATH" ] || [ ! -s "$CLASSPATH" ]; then
        echo "ERROR: Could not download gradle-wrapper.jar"
        echo "Please install Gradle or run: gradle wrapper"
        exit 1
    fi
fi

exec java \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"

#!/bin/bash
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

export MALLOC_CHECK_=0

if [[ -z "${JVM_OPTS}" ]]; then
    JVM_OPTS="-Xms1g -Xmx1g -XX:+UseG1GC -XX:+UseStringDeduplication -XX:MaxGCPauseMillis=30"
fi

$JAVA_HOME/bin/java $JVM_OPTS\
    --add-opens java.base/java.nio=ALL-UNNAMED\
    --add-opens java.base/java.lang=ALL-UNNAMED\
    --add-opens java.base/java.lang.invoke=ALL-UNNAMED\
    --add-opens java.base/java.lang.reflect=ALL-UNNAMED\
    --add-opens java.base/java.net=ALL-UNNAMED\
    -jar $DIR/trader-ui*.jar "$@"

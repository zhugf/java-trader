#!/bin/bash
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

export MALLOC_CHECK_=0

version=$($JAVA_HOME/bin/java -version 2>&1 | awk -F '"' '/version/ {print $2}')
if [[ "$version" = "15" ]]; then
    JVM_GC=" -XX:+UseZGC "
else
    JVM_GC=" -XX:+G1GC -XX:MaxGCPauseMillis=30 "
fi

if [[ -z "${JVM_OPTS}" ]]; then
    JVM_OPTS="-Xms1g -Xmx1g -XX:+UseStringDeduplication"
fi

$JAVA_HOME/bin/java $JVM_GC $JVM_OPTS\
    --add-opens java.base/java.nio=ALL-UNNAMED\
    --add-opens java.base/java.lang=ALL-UNNAMED\
    --add-opens java.base/java.lang.invoke=ALL-UNNAMED\
    --add-opens java.base/java.lang.reflect=ALL-UNNAMED\
    --add-opens java.base/java.net=ALL-UNNAMED\
    -jar $DIR/trader-services*.jar "$@"

#!/bin/bash
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

export MALLOC_CHECK_=0

JVM_OPTS2=" -XX:+UnlockExperimentalVMOptions -XX:+UseZGC --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.lang.invoke=ALL-UNNAMED --add-opens java.base/java.lang.reflect=ALL-UNNAMED --add-opens java.base/java.net=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/jdk.internal.ref=ALL-UNNAMED --add-opens java.base/sun.net.util=ALL-UNNAMED "

if [[ -z "${JVM_OPTS}" ]]; then
    JVM_OPTS="-Xms1g -Xmx1g"
fi

$JAVA_HOME/bin/java $JVM_OPTS $JVM_OPTS2 -jar $DIR/trader-services*.jar "$@"

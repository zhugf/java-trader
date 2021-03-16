#!/bin/bash
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

export MALLOC_CHECK_=0

JVM_GC=" -XX:+UseZGC "
JVM_OPTS2=" -XX:+UseStringDeduplication "
version=$($JAVA_HOME/bin/java -version 2>&1 | awk -F '"' '/version/ {print $2}')

$JAVA_HOME/bin/java -version 2>&1 | grep "OpenJDK" > /dev/null
if [ $? == 0 ]; then
    #OpenJDK 使用 ShenandoahGC
    JVM_GC=" -XX:+UseShenandoahGC "
else
    #Oracle JDK 11-17 使用 ZGC
    JVM_GC=" -XX:+UnlockExperimentalVMOptions -XX:+UseZGC "
fi


if [[ -z "${JVM_OPTS}" ]]; then
    JVM_OPTS="-Xms1g -Xmx1g"
fi

$JAVA_HOME/bin/java $JVM_GC $JVM_OPTS $JVM_OPTS2\
    --add-opens java.base/java.nio=ALL-UNNAMED\
    --add-opens java.base/java.lang=ALL-UNNAMED\
    --add-opens java.base/java.lang.invoke=ALL-UNNAMED\
    --add-opens java.base/java.lang.reflect=ALL-UNNAMED\
    --add-opens java.base/java.net=ALL-UNNAMED\
    -jar $DIR/trader-services*.jar "$@"

#!/bin/bash
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $DIR
export MALLOC_CHECK_=0
java -Xms1g -Xmx1g -XX:+UseG1GC\
    --add-opens java.base/java.nio=ALL-UNNAMED\
    --add-opens java.base/java.lang=ALL-UNNAMED\
    --add-opens java.base/java.lang.invoke=ALL-UNNAMED\
    --add-opens java.base/java.lang.reflect=ALL-UNNAMED\
    --add-opens java.base/java.net=ALL-UNNAMED\
    -jar trader-services*.jar service start>> $TRADER_HOME/logs/trader.out 2>&1 &

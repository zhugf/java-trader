#!/bin/bash
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $DIR
export MALLOC_CHECK_=0
java -jar trader-services*.jar >> ../../logs/trader.out 2>&1 &

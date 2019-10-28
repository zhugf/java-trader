#!/bin/bash
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $DIR

./trader.sh service start>> $TRADER_HOME/logs/trader.out 2>&1 &

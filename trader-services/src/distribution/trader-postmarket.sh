#!/bin/bash
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $DIR


#夜市结束
if [ "$1" == "nightClose" ]; then
    ./trader.sh marketData import
fi

#日市结束, 整个交易日结束
if [ "$1" == "fullClose" ]; then
    DAY=`/bin/date +%Y%m%d`
    if test -f "$TRADER_HOME/data marketData/$DAY"; then
        echo "Archive marketData for $DAY"
        tar cvzf $TRADER_HOME/data/marketData-$DAY.tgz -C $TRADER_HOME/data marketData/$DAY

        ./trader.sh marketData import --move=trash
        ./trader.sh repository archive
    fi
fi

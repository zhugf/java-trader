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
    if test -d "$TRADER_HOME/data/marketData/$DAY"; then
        echo "Archive marketData for $DAY"
        tar czf $TRADER_HOME/data/marketData-$DAY.tgz -C $TRADER_HOME/data marketData/$DAY

        # 因为占据内存太大, 忽略
        #./trader.sh marketData import --move=trash
        #./trader.sh repository archive
        rm -rf $TRADER_HOME/data/marketData/$DAY
        rm -rf $TRADER_HOME/data/trash/$DAY
    fi
fi

#!/bin/bash
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $DIR

i=0
while [[ $i -lt 3 ]]
do
    ((i++))
    #echo "第 $i 次"
    ./trader.sh service start --tradingTimesOnly=true >> $TRADER_HOME/logs/trader.out 2>&1 &
    sleep 60
    TRADER_EXISTS=`pgrep java -a|grep trader|wc -l`
    if [ $TRADER_EXISTS=="0" ]; 
    then
        #启动失败尝试重启
        continue
    else
        break
    fi
done

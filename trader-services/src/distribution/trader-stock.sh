#!/bin/bash
$TRADER_HOME/apps/trader/trader.sh -Dtrader.configFile=$TRADER_HOME/etc/trader-stock.xml service start>> $TRADER_HOME/logs/trader-stock.out 2>&1 &

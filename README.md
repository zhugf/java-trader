#Java Trader交易框架文档

[TOC]

##简介
java-trader项目目标是成为一个基于Java的开源期货交易框架, 有这些特点:
+ 基于纯Java的行情和交易接口, 内建支持JCTP
+ 行情/交易代码全部在同一个JVM中, 使用disrputor实现低延时的线程间事件传递.
+ 使用动态ClassLoader加载交易策略实现类, 允许运行时动态更新
+ 交易策略通过简单的分组和配置参数调整, 动态组合动态调整, 最大限度复用已有的开平止盈止损策略
+ 支持账户视图(AccountView), 允许主动限制策略的仓位和资金

##构建
构建环境需求: JDK 11, GRADLE 4.10, bash(Linux或CYGWIN), Spring 2.10

java-trader的构建过程需要一些手动编译和安装依赖包的操作:
+ 安装jctp依赖包到本地MVN Repository:

```
cd jars
./mvn.sh
```

+ 编译ta4j-javatrader

```
cd jars
tar xvzf ta4j-0.12-javatrader.tgz
cd xvzf ta4j-0.12-javatrader
mvn install
```

+ 构建工程

```
gradle clean build
```

##运行目录与配置文件

java-trader的运行目录为 ~/traderHome, 缺省在当前用户下, 目录结构如下:

```
traderHome
    |-etc(配置文件目录)
       |-trader.xml (配置文件)
       |-trader-key.ini (加密密钥文件, 第一次运行时会自动生成)
    |-log(日志目录)
    |-marketData(临时行情数据目录)
       |-20181010 (按交易日区分的行情数据)
           |- mdProducer-1
           |- mdProducer-2
       |-20181011
    |-plugins (插件根目录)
    |-repository (整理归档后的行情数据)
    |-work (工作目录)
```

##插件



##REST API
java-trader 作为一个纯后台WEB应用, 为实现前后端交互, 重要的REST API如下:

###行情API

**GET http://localhost:10080/api/md/producer**

获取行情数据源的状态, 订阅品种和统计信息


**GET http://localhost:10080/api/md/subscriptions**

获取全部订阅的交易品种


**GET http://localhost:10080/api/md/{exchangeable}/lastData**

获取某品种的最后行情数据

###插件API

**GET http://localhost:10080/api/plugin**

获得加载的全部插件

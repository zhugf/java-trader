# Java Trader交易框架文档

[TOC]

## 简介
java-trader项目目标是成为一个基于Java的开源期货交易框架, 有这些特点:
+ 基于纯Java的行情和交易接口, 内建支持JCTP
+ 行情/交易代码全部在同一个JVM中, 使用disrputor实现低延时的线程间事件传递.
+ 使用动态ClassLoader加载交易策略实现类, 允许运行时动态更新
+ 交易策略通过简单的分组和配置参数调整, 动态组合动态调整, 最大限度复用已有的开平止盈止损策略
+ 支持账户视图(AccountView), 允许主动限制策略的仓位和资金

## 构建
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

## 运行目录与配置文件

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

## 插件

插件是可以运行时加载和更新的动态扩展库, 基于Java 动态Classloader机制实现, 每个插件的目录结构如下

```
/pluginRootDir
    |
    /pluginDir
        |- plugin.properties
        |-classes(*.class)
        |-lib(*.jar)
```

plugin.properties是一个标准java properties文件, 包含这样几个属性:
* id: 全局唯一插件id
* exposedInterfaces: (可选)逗号,分隔的导出接口
* 其它属性: 用于插件管理查询时的统一查询语法, 类似JMX query

id: 唯一ID, 例如：

```
id=md-pctp
```

title: 可显示名称, 例如：

```
title=我是插件
```

exposedInterfaces: 导出接口列表, 以","分隔, 例如：

例如

```
exposedInterfaces=trader.service.md.MarketDataProducerFactory
```
注: 这个属性的值会自动合并所有标记为Discoverable的实现类的interfaceClass的值.


目前, 支持的标准导出接口有:
* trader.service.md.MarketDataProducerFactory : 行情网关接口
* trader.service.trade.TxnSessionFactory : 交易网关接口
* trader.service.tradlet.Tradlet : 交易策略接口


## 多线程模型
基于disruptor低延时事件分发机制, 实现行情和交易事件的多线程处理. 同时存在多个disruptor线程, 构成完整的交易处理逻辑.

### 行情数据服务
行情数据服务(MarketDataService)同时连接多个前置数据源, 作为disruptor的多producer存在, 单独启动行情数据处理线程, 分发行情数据.
K线处理服务(TAService) 由于延时很低, 直接在disruptor的consumer线程被调用, 后续的持仓动态盈亏和交易策略处理, 在单独线程中处理.

### 账户报单交易服务
账户报单交易服务(TradeService) 启动单独的 disruptor consumer 线程, 计算账户的浮动盈亏, 报单和成交回报等数据

### 交易策略组
交易策略服务(TradeletService)负责维护与某个账户视图(AccountView)相关的交易策略组(TradletGroup), 每个交易策略组运行关联的账户线程或运行在一个独立的 disruptor consumer 线程中. 每个策略组在处理行情切片数据时, K线与账户的状态更新确保已经完成.

## REST API
java-trader 作为一个纯后台WEB应用, 对前端提供的REST API实现类都保存在 package trader.api中, 如下:

### 行情API

**GET http://localhost:10080/api/md/producer**

获取行情数据源的状态, 订阅品种和统计信息


**GET http://localhost:10080/api/md/subscriptions**

获取全部订阅的交易品种


**GET http://localhost:10080/api/md/{exchangeable}/lastData**

获取某品种的最后行情数据

### 插件API

**GET http://localhost:10080/api/plugin**

获得加载的全部插件

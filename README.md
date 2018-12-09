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

配置文件为XML格式, 按照不同的Service实现, 每个属性有有不同的含义

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

## 交易小程序(Tradlet)
* 策略微服务(Tradlet)是一个交易策略微代码的接口, 每个Tradlet实现都需要完成一个独立的功能, 例如止损, 动态止盈, 开仓, 超时撤销报单等等. Tradlet 实现类可以通过插件机制实现动态加载和动态更新.
* 策略组(TradleGroup)是Tradlet的组合, 最终形成一个可以完整的交易策略, 策略组的更新通过配置文件完成.

## 多线程模型
基于disruptor低延时事件分发机制, 实现行情和交易事件的多线程处理. 同时存在多个disruptor线程, 构成完整的交易处理逻辑.
线程接力模型:
1 行情/交易API发送异步事件, 作为Multiple Producer
2 AsyncEventService 的 Main consumer 线程: 快速处理, 更新状态.
3 对于需要策略处理的事件, 异步派发到交易策略组, 这时候可以作为Single Producer派发, 进一步降低延时
4 每个交易策略组, 运行在各自的disrputor consumer线程中, 处理行情和交易事件

注: 关于 Single Producer vs Multiple Producer的性能对比, 简单的说有3倍的性能差距, 参见: [Disruptor Getting Started](https://github.com/LMAX-Exchange/disruptor/wiki/Getting-Started)

### 异步事件处理服务
异步事件处理服务(AsyncEventService) 同时接收全部行情和交易事件, 作为disruptor的多producer存在, 单独启动行情数据处理线程, 分发行情数据.
K线处理服务(TAService)和 账户报单交易服务(TradeService) 由于延时很低, 直接在disruptor的consumer线程被调用.

### 交易策略组
交易策略服务(TradeletService)负责维护与某个账户视图(AccountView)相关的交易策略组(TradletGroup), 每个交易策略组运行关联的账户线程或运行在一个独立的 disruptor consumer 线程中. 每个策略组在处理行情切片数据时, K线与账户的状态更新确保已经完成.

## 标准服务以及相关的配置

###AsyncEventService

AsyncEventService是异步消息处理服务, 负责统一处理行情和交易接口的事件 

可配置项: disruptor等待策略, 缓冲区大小 
```
    <AsyncEventService>
		<disruptor waitStrategy="BlockingWait" ringBufferSize="65536" />
    </AsyncEventService>
```

###MarketDataService

MarketDataService是行情消息处理服务, 负责连接多个行情数据源, 整理成为统一的行情TICK数据, 并单独保存原始行情数据

配置项有:
1. producer: 行情数据源, provider目前支持ctp, 可以通过插件支持别的数据源实现(飞马, 易胜等等)
2. subscriptions: 订阅的行情品种逗号分隔的品种列表; 使用 $PrimaryContracts代表主力合约

```
	<MarketDataService>
	    <producer id="zsqh_sh_uniconn1" provider="ctp" ><![CDATA[
			frontUrl=tcp://000.000.000.000:41213
			brokerId=0000
			username=000000000
			password=000000
	    ]]></producer>
	    <producer id="zsqh_sh_telecom1" provider="ctp" ><![CDATA[
			frontUrl=tcp://000.000.000.000:41213
			brokerId=0000
			username=000000000
			password=000000
	    ]]></producer>

		<!--$PrimaryContracts会被自动替换为实际的主力合约-->
	    <subscriptions>
	    	$PrimaryContracts
	    </subscriptions>
	</MarketDataService>
```


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

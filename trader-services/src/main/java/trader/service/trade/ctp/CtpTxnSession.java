package trader.service.trade.ctp;

import java.io.File;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.common.util.BufferUtil;
import net.jctp.*;
import trader.common.beans.BeansContainer;
import trader.common.exception.AppException;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableType;
import trader.common.exchangeable.Future;
import trader.common.exchangeable.FutureCombo;
import trader.common.util.ConversionUtil;
import trader.common.util.DateUtil;
import trader.common.util.EncryptionUtil;
import trader.common.util.FileUtil;
import trader.common.util.JsonUtil;
import trader.common.util.PriceUtil;
import trader.common.util.StringUtil;
import trader.common.util.TraderHomeUtil;
import trader.service.ServiceConstants.ConnState;
import trader.service.ServiceErrorConstants;
import trader.service.event.AsyncEventService;
import trader.service.md.MarketDataService;
import trader.service.trade.Account;
import trader.service.trade.MarketTimeService;
import trader.service.trade.Order;
import trader.service.trade.OrderBuilder;
import trader.service.trade.OrderStateTuple;
import trader.service.trade.TradeConstants;
import trader.service.trade.spi.AbsTxnSession;
import trader.service.trade.spi.TxnSessionListener;

/**
 * CTP的交易会话实现类. 目前使用异步多线程处理模式: 在收到报单/成交回报事件后, 将事件排队到AsyncEventService中异步处理.
 */
public class CtpTxnSession extends AbsTxnSession implements ServiceErrorConstants, TradeConstants, JctpConstants{

    private AsyncEventService asyncEventService;

    private String brokerId;
    /**
     * 用户ID是解密之后的值
     */
    private String userId;
    /**
     * 密码, 不解密
     */
    private String password;

    private String authCode;

    private String appId;

    private String userProductInfo;

    private TraderApi traderApi;

    private int frontId;
    /**
     * 通过计算得到的期货公式的保证金率的调整值
     */
    private Map<Exchangeable, CThostFtdcInvestorPositionDetailField> marginByPos = null;

    private CThostFtdcInstrumentField[] instruments;

    private CtpTxnEventProcessor processor;

    public CtpTxnSession(BeansContainer beansContainer, Account account, TxnSessionListener listener) {
        super(beansContainer, account, listener);
        asyncEventService = beansContainer.getBean(AsyncEventService.class);
        processor= new CtpTxnEventProcessor(account, this, listener);
    }

    @Override
    public String getProvider() {
        return PROVIDER_CTP;
    }

    public AccClassification getClassification() {
        return AccClassification.Future;
    }

    @Override
    public void connect(Properties connProps) {
        brokerId = decrypt(connProps.getProperty("brokerId"));
        userId = decrypt(connProps.getProperty("userId"));
        authCode = connProps.getProperty("authCode");
        password = connProps.getProperty("password");
        appId = decrypt(connProps.getProperty("appId"));
        userProductInfo = connProps.getProperty("userProductInfo");
        try {
            changeState(ConnState.Connecting);
            closeImpl();

            traderApi = new TraderApi();
            traderApi.setListener(new TraderApiAdapter() {

                @Override
                public void OnFrontConnected() {
                    ctpOnFrontConnected();
                }

                @Override
                public void OnFrontDisconnected(int nReason) {
                    ctpOnFrontDisconnected(nReason);
                }

                @Override
                public void OnHeartBeatWarning(int nTimeLapse) {
                    logger.info("OnHeartBeatWarning: "+nTimeLapse);
                }

                @Override
                public void OnRspAuthenticate(CThostFtdcRspAuthenticateField pRspAuthenticateField, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    ctpOnRspAuthenticate(pRspAuthenticateField, pRspInfo, nRequestID, bIsLast);
                }

                @Override
                public void OnRspUserLogin( CThostFtdcRspUserLoginField pRspUserLogin, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast){
                    ctpOnRspUserLogin(pRspUserLogin, pRspInfo, nRequestID, bIsLast);
                }

                @Override
                public void OnRspUserLogout(CThostFtdcUserLogoutField pUserLogout, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    ctpOnRspUserLogout(pUserLogout, pRspInfo, nRequestID, bIsLast);
                }

                /**
                 * 撤单错误回报（柜台）
                 */
                @Override
                public void OnRspOrderAction(CThostFtdcInputOrderActionField pInputOrderAction, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    ctpOnRspOrderAction(pInputOrderAction, pRspInfo, nRequestID, bIsLast);
                }

                /**
                 * 报单回报
                 */
                @Override
                public void OnRtnOrder(CThostFtdcOrderField pOrder) {
                    ctpOnRtnOrder(pOrder);
                }

                /**
                 * 成交回报
                 */
                @Override
                public void OnRtnTrade(CThostFtdcTradeField pTrade) {
                    ctpOnRtnTrade(pTrade);
                }

                /**
                 * 报单错误(交易所)
                 */
                @Override
                public void OnErrRtnOrderInsert(CThostFtdcInputOrderField pInputOrder, CThostFtdcRspInfoField pRspInfo) {
                    ctpOnErrRtnOrderInsert(pInputOrder, pRspInfo);
                }

                /**
                 * 撤单错误(交易所)
                 */
                @Override
                public void OnErrRtnOrderAction(CThostFtdcOrderActionField pOrderAction, CThostFtdcRspInfoField pRspInfo) {
                    ctpOnErrRtnOrderAction(pOrderAction, pRspInfo);
                }

                @Override
                public void OnRspUserPasswordUpdate(CThostFtdcUserPasswordUpdateField pUserPasswordUpdate, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    logger.info("OnRspUserPasswordUpdate: "+pUserPasswordUpdate+" "+pRspInfo);
                }

                @Override
                public void OnRspTradingAccountPasswordUpdate(CThostFtdcTradingAccountPasswordUpdateField pTradingAccountPasswordUpdate, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    logger.info("OnRspTradingAccountPasswordUpdate: "+pTradingAccountPasswordUpdate+" "+pRspInfo);
                }

                /**
                 * 报单错误(柜台)
                 */
                @Override
                public void OnRspOrderInsert(CThostFtdcInputOrderField pInputOrder, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    ctpOnRspOrderInsert(pInputOrder, pRspInfo, nRequestID, bIsLast);
                }

                @Override
                public void OnRspParkedOrderInsert(CThostFtdcParkedOrderField pParkedOrder, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                }

                @Override
                public void OnRtnInstrumentStatus(CThostFtdcInstrumentStatusField pInstrumentStatus) {
                    if ( logger.isDebugEnabled()) {
                        logger.info("OnRtnInstrumentStatus: "+pInstrumentStatus);
                    }
                }

                @Override
                public void OnRtnBulletin(CThostFtdcBulletinField pBulletin) {
                    logger.info("OnRtnBulletin: "+pBulletin);
                }

                @Override
                public void OnRtnTradingNotice(CThostFtdcTradingNoticeInfoField pTradingNoticeInfo) {
                    logger.info("OnRtnTradingNotice: "+pTradingNoticeInfo);
                }

                @Override
                public void OnRtnErrorConditionalOrder(CThostFtdcErrorConditionalOrderField pErrorConditionalOrder) {
                    logger.info("OnRtnErrorConditionalOrder: "+pErrorConditionalOrder);
                }

                @Override
                public void OnRtnExecOrder(CThostFtdcExecOrderField pExecOrder) {
                    logger.info("OnRtnExecOrder: "+pExecOrder);
                }

                @Override
                public void OnErrRtnExecOrderInsert(CThostFtdcInputExecOrderField pInputExecOrder, CThostFtdcRspInfoField pRspInfo) {
                    logger.info("OnErrRtnExecOrderInsert: "+pInputExecOrder);
                }

                @Override
                public void OnErrRtnExecOrderAction(CThostFtdcExecOrderActionField pExecOrderAction, CThostFtdcRspInfoField pRspInfo) {
                    logger.info("OnErrRtnExecOrderAction: "+pExecOrderAction);
                }

                @Override
                public void OnErrRtnForQuoteInsert(CThostFtdcInputForQuoteField pInputForQuote, CThostFtdcRspInfoField pRspInfo) {
                    logger.info("OnErrRtnForQuoteInsert: "+pInputForQuote);
                }

                @Override
                public void OnRtnQuote(CThostFtdcQuoteField pQuote) {
                    logger.info("OnRtnQuote: "+pQuote);
                }

                @Override
                public void OnErrRtnQuoteInsert(CThostFtdcInputQuoteField pInputQuote, CThostFtdcRspInfoField pRspInfo) {
                    logger.info("OnErrRtnQuoteInsert: "+pInputQuote);
                }

                @Override
                public void OnErrRtnQuoteAction(CThostFtdcQuoteActionField pQuoteAction, CThostFtdcRspInfoField pRspInfo) {
                    logger.info("OnErrRtnQuoteAction: "+pQuoteAction+" "+pRspInfo);
                }

                @Override
                public void OnRtnForQuoteRsp(CThostFtdcForQuoteRspField pForQuoteRsp) {
                    logger.info("OnRtnForQuoteRsp: "+pForQuoteRsp);
                }

                @Override
                public void OnRtnCFMMCTradingAccountToken(CThostFtdcCFMMCTradingAccountTokenField pCFMMCTradingAccountToken) {
                    logger.info("OnRtnCFMMCTradingAccountToken: "+pCFMMCTradingAccountToken);
                }

                @Override
                public void OnErrRtnBatchOrderAction(CThostFtdcBatchOrderActionField pBatchOrderAction, CThostFtdcRspInfoField pRspInfo) {
                    logger.info("OnErrRtnBatchOrderAction: "+pBatchOrderAction+" "+pRspInfo);
                }

                @Override
                public void OnRtnOptionSelfClose(CThostFtdcOptionSelfCloseField pOptionSelfClose) {
                    logger.info("OnRtnOptionSelfClose: "+pOptionSelfClose);
                }

                @Override
                public void OnErrRtnOptionSelfCloseInsert(CThostFtdcInputOptionSelfCloseField pInputOptionSelfClose, CThostFtdcRspInfoField pRspInfo) {
                    logger.info("OnErrRtnOptionSelfCloseInsert: "+pInputOptionSelfClose+" "+pRspInfo);
                }

                @Override
                public void OnErrRtnOptionSelfCloseAction(CThostFtdcOptionSelfCloseActionField pOptionSelfCloseAction, CThostFtdcRspInfoField pRspInfo) {
                    logger.info("OnErrRtnOptionSelfCloseAction: "+pOptionSelfCloseAction+" "+pRspInfo);
                }

                @Override
                public void OnRtnCombAction(CThostFtdcCombActionField pCombAction) {
                    logger.info("OnRtnCombAction: "+pCombAction);
                }

                @Override
                public void OnErrRtnCombActionInsert(CThostFtdcInputCombActionField pInputCombAction, CThostFtdcRspInfoField pRspInfo) {
                    logger.info("OnErrRtnCombActionInsert: "+pInputCombAction+" "+pRspInfo);
                }

                @Override
                public void OnRspQryContractBank(CThostFtdcContractBankField pContractBank, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("OnRspQryContractBank: "+pContractBank+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRspQryParkedOrder(CThostFtdcParkedOrderField pParkedOrder, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("OnRspQryParkedOrder: "+pParkedOrder+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRspQryParkedOrderAction(CThostFtdcParkedOrderActionField pParkedOrderAction, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("OnRspQryParkedOrderAction: "+pParkedOrderAction+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRspQryTradingNotice(CThostFtdcTradingNoticeField pTradingNotice, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("OnRspQryTradingNotice: "+pTradingNotice+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRspQryBrokerTradingParams(CThostFtdcBrokerTradingParamsField pBrokerTradingParams, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("OnRspQryBrokerTradingParams: "+pBrokerTradingParams+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRspQryBrokerTradingAlgos(CThostFtdcBrokerTradingAlgosField pBrokerTradingAlgos, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("OnRspQryBrokerTradingAlgos: "+pBrokerTradingAlgos+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRspQueryCFMMCTradingAccountToken(CThostFtdcQueryCFMMCTradingAccountTokenField pQueryCFMMCTradingAccountToken, CThostFtdcRspInfoField pRspInfo, int nRequestID,
                boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("OnRspQueryCFMMCTradingAccountToken: "+pQueryCFMMCTradingAccountToken+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRtnFromBankToFutureByBank(CThostFtdcRspTransferField pRspTransfer) {
                    logger.info("OnRtnFromBankToFutureByBank: "+pRspTransfer);
                    ctpOnAccountTransfer(AccountTransferAction.Deposit, pRspTransfer);
                }

                @Override
                public void OnRtnFromFutureToBankByBank(CThostFtdcRspTransferField pRspTransfer) {
                    logger.info("OnRtnFromFutureToBankByBank: "+pRspTransfer);
                    ctpOnAccountTransfer(AccountTransferAction.Withdraw, pRspTransfer);
                }

                @Override
                public void OnRtnRepealFromBankToFutureByBank(CThostFtdcRspRepealField pRspRepeal) {
                    logger.info("OnRtnRepealFromBankToFutureByBank: "+pRspRepeal);
                }

                @Override
                public void OnRtnRepealFromFutureToBankByBank(CThostFtdcRspRepealField pRspRepeal) {
                    logger.info("OnRtnRepealFromFutureToBankByBank: "+pRspRepeal);
                }

                @Override
                public void OnRtnFromBankToFutureByFuture(CThostFtdcRspTransferField pRspTransfer) {
                    logger.info("OnRtnFromBankToFutureByFuture: "+pRspTransfer);
                }

                @Override
                public void OnRtnFromFutureToBankByFuture(CThostFtdcRspTransferField pRspTransfer) {
                    logger.info("OnRtnFromFutureToBankByFuture: "+pRspTransfer);
                }

                @Override
                public void OnRtnRepealFromBankToFutureByFutureManual(CThostFtdcRspRepealField pRspRepeal) {
                    logger.info("OnRtnRepealFromBankToFutureByFutureManual: "+pRspRepeal);
                }

                @Override
                public void OnRtnRepealFromFutureToBankByFutureManual(CThostFtdcRspRepealField pRspRepeal) {
                    logger.info("OnRtnRepealFromFutureToBankByFutureManual: "+pRspRepeal);
                }

                @Override
                public void OnRtnQueryBankBalanceByFuture(CThostFtdcNotifyQueryAccountField pNotifyQueryAccount) {
                    logger.info("OnRtnQueryBankBalanceByFuture: "+pNotifyQueryAccount);
                }

                @Override
                public void OnErrRtnBankToFutureByFuture(CThostFtdcReqTransferField pReqTransfer, CThostFtdcRspInfoField pRspInfo) {
                    logger.info("OnErrRtnBankToFutureByFuture: "+pReqTransfer+" "+pRspInfo);
                }

                @Override
                public void OnErrRtnFutureToBankByFuture(CThostFtdcReqTransferField pReqTransfer, CThostFtdcRspInfoField pRspInfo) {
                    logger.info("OnErrRtnFutureToBankByFuture: "+pReqTransfer+" "+pRspInfo);
                }

                @Override
                public void OnErrRtnRepealBankToFutureByFutureManual(CThostFtdcReqRepealField pReqRepeal, CThostFtdcRspInfoField pRspInfo) {
                    logger.info("OnErrRtnRepealBankToFutureByFutureManual: "+pReqRepeal+" "+pRspInfo);
                }

                @Override
                public void OnErrRtnRepealFutureToBankByFutureManual(CThostFtdcReqRepealField pReqRepeal, CThostFtdcRspInfoField pRspInfo) {
                    logger.info("OnErrRtnRepealFutureToBankByFutureManual: "+pReqRepeal+" "+pRspInfo);
                }

                @Override
                public void OnErrRtnQueryBankBalanceByFuture(CThostFtdcReqQueryAccountField pReqQueryAccount, CThostFtdcRspInfoField pRspInfo) {
                    logger.info("OnErrRtnQueryBankBalanceByFuture: "+pReqQueryAccount+" "+pRspInfo);
                }

                @Override
                public void OnRtnRepealFromBankToFutureByFuture(CThostFtdcRspRepealField pRspRepeal) {
                    logger.info("OnRtnRepealFromBankToFutureByFuture: "+pRspRepeal);
                }

                @Override
                public void OnRtnRepealFromFutureToBankByFuture(CThostFtdcRspRepealField pRspRepeal) {
                    logger.info("OnRtnRepealFromFutureToBankByFuture: "+pRspRepeal);
                }

                @Override
                public void OnRspFromBankToFutureByFuture(CThostFtdcReqTransferField pReqTransfer, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("OnRspFromBankToFutureByFuture: "+pReqTransfer+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRspFromFutureToBankByFuture(CThostFtdcReqTransferField pReqTransfer, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("OnRspFromFutureToBankByFuture: "+pReqTransfer+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRspQueryBankAccountMoneyByFuture(CThostFtdcReqQueryAccountField pReqQueryAccount, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("OnRspQueryBankAccountMoneyByFuture: "+pReqQueryAccount+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRtnOpenAccountByBank(CThostFtdcOpenAccountField pOpenAccount) {
                    logger.info("OnRtnOpenAccountByBank: "+pOpenAccount);
                }

                @Override
                public void OnRtnCancelAccountByBank(CThostFtdcCancelAccountField pCancelAccount) {
                    logger.info("OnRtnCancelAccountByBank: "+pCancelAccount);
                }

                @Override
                public void OnRtnChangeAccountByBank(CThostFtdcChangeAccountField pChangeAccount) {
                    logger.info("OnRtnChangeAccountByBank: "+pChangeAccount);
                }

                @Override
                public void OnRspQueryMaxOrderVolume(CThostFtdcQueryMaxOrderVolumeField pQueryMaxOrderVolume, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    logger.info("OnRspQueryMaxOrderVolume: "+pQueryMaxOrderVolume+" "+pRspInfo);
                }

                @Override
                public void OnRspSettlementInfoConfirm(CThostFtdcSettlementInfoConfirmField pSettlementInfoConfirm, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    logger.info("OnRspSettlementInfoConfirm: "+pSettlementInfoConfirm+" "+pRspInfo);
                }

                @Override
                public void OnRspRemoveParkedOrder(CThostFtdcRemoveParkedOrderField pRemoveParkedOrder, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    logger.info("OnRspRemoveParkedOrder: "+pRemoveParkedOrder+" "+pRspInfo);
                }

                @Override
                public void OnRspRemoveParkedOrderAction(CThostFtdcRemoveParkedOrderActionField pRemoveParkedOrderAction, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    logger.info("OnRspRemoveParkedOrderAction: "+pRemoveParkedOrderAction+" "+pRspInfo);
                }

                @Override
                public void OnRspExecOrderInsert(CThostFtdcInputExecOrderField pInputExecOrder, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    logger.info("OnRspExecOrderInsert: "+pInputExecOrder+" "+pRspInfo);
                }

                @Override
                public void OnRspExecOrderAction(CThostFtdcInputExecOrderActionField pInputExecOrderAction, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    logger.info("OnRspExecOrderAction: "+pInputExecOrderAction+" "+pRspInfo);
                }

                @Override
                public void OnRspForQuoteInsert(CThostFtdcInputForQuoteField pInputForQuote, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    logger.info("OnRspForQuoteInsert: "+pInputForQuote+" "+pRspInfo);
                }

                @Override
                public void OnRspQuoteInsert(CThostFtdcInputQuoteField pInputQuote, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    logger.info("OnRspQuoteInsert: "+pInputQuote+" "+pRspInfo);
                }

                @Override
                public void OnRspQuoteAction(CThostFtdcInputQuoteActionField pInputQuoteAction, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    logger.info("OnRspQuoteAction: "+pInputQuoteAction+" "+pRspInfo);
                }

                @Override
                public void OnRspBatchOrderAction(CThostFtdcInputBatchOrderActionField pInputBatchOrderAction, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    logger.info("OnRspBatchOrderAction: "+pInputBatchOrderAction+" "+pRspInfo);
                }

                @Override
                public void OnRspOptionSelfCloseInsert(CThostFtdcInputOptionSelfCloseField pInputOptionSelfClose, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    logger.info("OnRspOptionSelfCloseInsert: "+pInputOptionSelfClose+" "+pRspInfo);
                }

                @Override
                public void OnRspOptionSelfCloseAction(CThostFtdcInputOptionSelfCloseActionField pInputOptionSelfCloseAction, CThostFtdcRspInfoField pRspInfo, int nRequestID,
                boolean bIsLast) {
                    logger.info("OnRspOptionSelfCloseAction: "+pInputOptionSelfCloseAction+" "+pRspInfo);

                }

                @Override
                public void OnRspCombActionInsert(CThostFtdcInputCombActionField pInputCombAction, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    logger.info("OnRspCombActionInsert: "+pInputCombAction+" "+pRspInfo);
                }

                @Override
                public void OnRspQryOrder(CThostFtdcOrderField pOrder, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("OnRspQryOrder: "+pOrder+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRspQryTrade(CThostFtdcTradeField pTrade, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("OnRspQryTrade: "+pTrade+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRspQryInvestorPosition(CThostFtdcInvestorPositionField pInvestorPosition, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("OnRspQryInvestorPosition: "+pInvestorPosition+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRspQryTradingAccount(CThostFtdcTradingAccountField pTradingAccount, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("OnRspQryTradingAccount: "+pTradingAccount+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRspQryInvestor(CThostFtdcInvestorField pInvestor, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("OnRspQryInvestor: "+pRspInfo+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRspQryTradingCode(CThostFtdcTradingCodeField pTradingCode, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("OnRspQryTradingCode: "+pTradingCode+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRspQryInstrumentMarginRate(CThostFtdcInstrumentMarginRateField pInstrumentMarginRate, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("OnRspQryInstrumentMarginRate: "+pInstrumentMarginRate+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRspQryInstrumentCommissionRate(CThostFtdcInstrumentCommissionRateField pInstrumentCommissionRate, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("OnRspQryInstrumentCommissionRate: "+pInstrumentCommissionRate+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRspQryExchange(CThostFtdcExchangeField pExchange, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("OnRspQryExchange: "+pExchange+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRspQryProduct(CThostFtdcProductField pProduct, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("OnRspQryProduct: "+pProduct+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRspQryInstrument(CThostFtdcInstrumentField pInstrument, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("OnRspQryInstrument: "+pInstrument+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRspQryDepthMarketData(CThostFtdcDepthMarketDataField pDepthMarketData, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("OnRspQryDepthMarketData: "+pDepthMarketData+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRspQrySettlementInfo(CThostFtdcSettlementInfoField pSettlementInfo, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("OnRspQrySettlementInfo: "+pSettlementInfo+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRspQryTransferBank(CThostFtdcTransferBankField pTransferBank, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("OnRspQryTransferBank: "+pTransferBank+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRspQryInvestorPositionDetail(CThostFtdcInvestorPositionDetailField pInvestorPositionDetail, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("OnRspQryInvestorPositionDetail: "+pInvestorPositionDetail+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRspQryNotice(CThostFtdcNoticeField pNotice, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("OnRspQryNotice: "+pNotice+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRspQrySettlementInfoConfirm(CThostFtdcSettlementInfoConfirmField pSettlementInfoConfirm, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.info("OnRspQrySettlementInfoConfirm: "+pSettlementInfoConfirm+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRspQryInvestorPositionCombineDetail(CThostFtdcInvestorPositionCombineDetailField pInvestorPositionCombineDetail, CThostFtdcRspInfoField pRspInfo, int nRequestID,
                boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("OnRspQryInvestorPositionCombineDetail: "+pInvestorPositionCombineDetail+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRspQryCFMMCTradingAccountKey(CThostFtdcCFMMCTradingAccountKeyField pCFMMCTradingAccountKey, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("OnRspQryCFMMCTradingAccountKey: "+pCFMMCTradingAccountKey+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRspQryEWarrantOffset(CThostFtdcEWarrantOffsetField pEWarrantOffset, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("OnRspQryEWarrantOffset: "+pEWarrantOffset+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRspQryInvestorProductGroupMargin(CThostFtdcInvestorProductGroupMarginField pInvestorProductGroupMargin, CThostFtdcRspInfoField pRspInfo, int nRequestID,
                boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("OnRspQryInvestorProductGroupMargin: "+pInvestorProductGroupMargin+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRspQryExchangeMarginRate(CThostFtdcExchangeMarginRateField pExchangeMarginRate, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("OnRspQryExchangeMarginRate: "+pExchangeMarginRate+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRspQryExchangeMarginRateAdjust(CThostFtdcExchangeMarginRateAdjustField pExchangeMarginRateAdjust, CThostFtdcRspInfoField pRspInfo, int nRequestID,
                boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("OnRspQryExchangeMarginRateAdjust: "+pExchangeMarginRateAdjust+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRspQryExchangeRate(CThostFtdcExchangeRateField pExchangeRate, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("OnRspQryExchangeRate: "+pExchangeRate+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRspQrySecAgentACIDMap(CThostFtdcSecAgentACIDMapField pSecAgentACIDMap, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("OnRspQrySecAgentACIDMap: "+pSecAgentACIDMap+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRspQryProductExchRate(CThostFtdcProductExchRateField pProductExchRate, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("OnRspQryProductExchRate: "+pProductExchRate+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRspQryProductGroup(CThostFtdcProductGroupField pProductGroup, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("OnRspQryProductGroup: "+pProductGroup+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRspQryMMInstrumentCommissionRate(CThostFtdcMMInstrumentCommissionRateField pMMInstrumentCommissionRate, CThostFtdcRspInfoField pRspInfo, int nRequestID,
                boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("OnRspQryMMInstrumentCommissionRate: "+pMMInstrumentCommissionRate+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRspQryMMOptionInstrCommRate(CThostFtdcMMOptionInstrCommRateField pMMOptionInstrCommRate, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("OnRspQryMMOptionInstrCommRate: "+pMMOptionInstrCommRate+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRspQryInstrumentOrderCommRate(CThostFtdcInstrumentOrderCommRateField pInstrumentOrderCommRate, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("OnRspQryInstrumentOrderCommRate: "+pInstrumentOrderCommRate+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRspQrySecAgentTradingAccount(CThostFtdcTradingAccountField pTradingAccount, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("OnRspQrySecAgentTradingAccount: "+pTradingAccount+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRspQrySecAgentCheckMode(CThostFtdcSecAgentCheckModeField pSecAgentCheckMode, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("OnRspQrySecAgentCheckMode: "+pSecAgentCheckMode+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRspQryOptionInstrTradeCost(CThostFtdcOptionInstrTradeCostField pOptionInstrTradeCost, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("OnRspQryOptionInstrTradeCost: "+pOptionInstrTradeCost+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRspQryOptionInstrCommRate(CThostFtdcOptionInstrCommRateField pOptionInstrCommRate, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("OnRspQryOptionInstrCommRate: "+pOptionInstrCommRate+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRspQryExecOrder(CThostFtdcExecOrderField pExecOrder, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("OnRspQryExecOrder: "+pExecOrder+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRspQryForQuote(CThostFtdcForQuoteField pForQuote, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("OnRspQryForQuote: "+pForQuote+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRspQryQuote(CThostFtdcQuoteField pQuote, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("OnRspQryQuote: "+pQuote+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRspQryOptionSelfClose(CThostFtdcOptionSelfCloseField pOptionSelfClose, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("OnRspQryOptionSelfClose: "+pOptionSelfClose+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRspQryInvestUnit(CThostFtdcInvestUnitField pInvestUnit, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("OnRspQryInvestUnit: "+pInvestUnit+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRspQryCombInstrumentGuard(CThostFtdcCombInstrumentGuardField pCombInstrumentGuard, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("OnRspQryCombInstrumentGuard: "+pCombInstrumentGuard+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRspQryCombAction(CThostFtdcCombActionField pCombAction, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("OnRspQryCombAction: "+pCombAction+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRspQryTransferSerial(CThostFtdcTransferSerialField pTransferSerial, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("OnRspQryTransferSerial: "+pTransferSerial+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRspQryAccountregister(CThostFtdcAccountregisterField pAccountregister, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    if ( logger.isDebugEnabled() ) {
                        logger.debug("OnRspQryAccountregister: "+pAccountregister+" "+pRspInfo);
                    }
                }

                @Override
                public void OnRspError(CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                    logger.error("OnRspError: "+pRspInfo);
                }

            });
            traderApi.setFlowControl(true);
            traderApi.SubscribePrivateTopic(JctpConstants.THOST_TERT_QUICK);
            traderApi.SubscribePublicTopic(JctpConstants.THOST_TERT_QUICK);
            String frontUrl = connProps.getProperty("frontUrl");
            traderApi.Connect(frontUrl);
            logger.info(account.getId()+" connect to "+frontUrl+", TRADER API version: "+traderApi.GetApiVersion());
        }catch(Throwable t) {
            logger.error("Connect failed", t);
            changeState(ConnState.ConnectFailed);
        }
    }

    @Override
    protected void closeImpl() {
        if ( traderApi!=null ) {
            TraderApi traderApi0 = traderApi;
            Thread closeThread = new Thread("Ctp txn close thread") {
                public void run() {
                    try{
                        traderApi0.Close();
                    }catch(Throwable t) {}
                }
            };
            closeThread.setDaemon(true);
            closeThread.start();
            try{
                Thread.sleep(500);
            }catch(Throwable t) {}
            traderApi = null;
            changeState(ConnState.Disconnected);
        }
        frontId = 0;
        sessionId = 0;
    }

    /**
     * 确认结算单
     */
    @Override
    public String[] syncConfirmSettlement() throws Exception {
        boolean confirmed = false;
        String settlementDay = null;String settlement = null;
        long t0 = System.currentTimeMillis();
        CThostFtdcQrySettlementInfoConfirmField qryInfoField = new CThostFtdcQrySettlementInfoConfirmField(brokerId, userId, userId, null);
        CThostFtdcSettlementInfoConfirmField infoConfirmField = traderApi.SyncReqQrySettlementInfoConfirm(qryInfoField);
        if ( infoConfirmField!=null && StringUtil.equals(traderApi.GetTradingDay(),infoConfirmField.ConfirmDate) ) {
            confirmed = true;
        }
        if ( !confirmed) {
            //未确认, 需要先查询再确认
            CThostFtdcQrySettlementInfoField qryField = new CThostFtdcQrySettlementInfoField();
            qryField.BrokerID = brokerId;
            qryField.AccountID = userId;
            qryField.InvestorID = userId;
            CThostFtdcSettlementInfoField[] infoFields = traderApi.SyncAllReqQrySettlementInfo(qryField);
            if ( infoFields==null || infoFields.length==0 ){
                if ( logger.isDebugEnabled() ) {
                    logger.debug("No settlement found to confirm");
                }
            }else{ //从多个结算单查询结构拼出结算单文本, 使用GBK编码
                CThostFtdcSettlementInfoField f1 = infoFields[0];
                byte[][] rawByteArrays = new byte[infoFields.length][];
                for(int i=0;i<infoFields.length;i++) {
                    rawByteArrays[i] = infoFields[i]._rawBytes;
                }
                if ( logger.isDebugEnabled() ) {
                    logger.debug("Trading day "+f1.TradingDay+" investor "+f1.InvestorID+" settlement id: "+f1.SettlementID+" seqence no: "+f1.SequenceNo);
                }
                settlement = BufferUtil.getStringFromByteArrays(rawByteArrays, Offset_CThostFtdcSettlementInfoField_Content, SizeOf_TThostFtdcContentType-1);
            }
            //发送结算单确认请求
            infoConfirmField = new CThostFtdcSettlementInfoConfirmField(brokerId,userId,traderApi.GetTradingDay(),"", 0, null, null);
            CThostFtdcSettlementInfoConfirmField confirmResult = traderApi.SyncReqSettlementInfoConfirm(infoConfirmField);
            long t1 = System.currentTimeMillis();
            logger.info("Investor "+confirmResult.InvestorID+" settlement "+confirmResult.SettlementID+" is confirmed in "+(t1-t0)+" ms");
        }
        return new String[] {settlementDay, settlement};
    }

    /**
     * 查询账户基本信息
     */
    @Override
    public long[] syncQryAccounts() throws Exception {
        long[] result = new long[AccMoney.values().length];
        CThostFtdcQryTradingAccountField q = new CThostFtdcQryTradingAccountField(brokerId, userId, null, THOST_FTDC_BZTP_Future, null);
        CThostFtdcTradingAccountField r = traderApi.SyncReqQryTradingAccount(q);

        result[AccMoney.Balance.ordinal()] = PriceUtil.price2long(r.Balance);
        result[AccMoney.Available.ordinal()] = PriceUtil.price2long(r.Available);
        result[AccMoney.FrozenMargin.ordinal()] = PriceUtil.price2long(r.FrozenMargin);
        result[AccMoney.CurrMargin.ordinal()] = PriceUtil.price2long(r.CurrMargin);
        result[AccMoney.PreMargin.ordinal()] = PriceUtil.price2long(r.PreMargin);
        result[AccMoney.FrozenCash.ordinal()] = PriceUtil.price2long(r.FrozenCash);
        result[AccMoney.Commission.ordinal()] = PriceUtil.price2long(r.Commission);
        result[AccMoney.FrozenCommission.ordinal()] = PriceUtil.price2long(r.FrozenCommission);
        result[AccMoney.CloseProfit.ordinal()] = PriceUtil.price2long(r.CloseProfit);
        result[AccMoney.PositionProfit.ordinal()] = PriceUtil.price2long(r.PositionProfit);
        result[AccMoney.WithdrawQuota.ordinal()] = PriceUtil.price2long(r.WithdrawQuota);
        result[AccMoney.Reserve.ordinal()] = PriceUtil.price2long(r.Reserve);
        result[AccMoney.Deposit.ordinal()] = PriceUtil.price2long(r.Deposit);
        result[AccMoney.Withdraw.ordinal()] = PriceUtil.price2long(r.Withdraw);
        result[AccMoney.PreBalance.ordinal()] = PriceUtil.price2long(r.PreBalance);

        if ( logger.isInfoEnabled() ) {
            logger.info("Account "+account.getId()+" load money: "+r);
        }
        return result;
    }

    /**
     * 加载费率计算
     */
    @Override
    public String syncLoadFeeEvaluator(Collection<Exchangeable> subscriptions) throws Exception
    {
        long t0 = System.currentTimeMillis();
        TreeSet<Exchangeable> filter = new TreeSet<>(subscriptions);
        Set<Exchangeable> allInstruments = new TreeSet<>();
        JsonObject feeInfos = new JsonObject();
        //填充品种的基本数据
        {
            ArrayList<String> unknownInstrumentIds = new ArrayList<>();
            //查询品种基本数据
            CThostFtdcInstrumentField[] rr = syncQueryInstruments0();
            StringBuilder instrumentIds = new StringBuilder(256*rr.length);
            for(int i=0;i<rr.length;i++) {
                instrumentIds.append(rr[i]).append("\n");
            }
            File tradingDayDir = new File(TraderHomeUtil.getDirectory(TraderHomeUtil.DIR_WORK), DateUtil.date2str(tradingDay));
            tradingDayDir.mkdirs();
            FileUtil.save(new File(tradingDayDir, "ctp-instruments.txt"), instrumentIds.toString());
            synchronized(Exchangeable.class) {
                for(CThostFtdcInstrumentField r:rr){
                    if ( logger.isDebugEnabled() ) {
                        logger.debug(r.ExchangeID+" "+r.InstrumentID+" "+r.InstrumentName);
                    }
                    //忽略不交易和非期货品种(期权/组合/现货/现货期权等等)
                    if ( !r.IsTrading || r.ProductClass!=JctpConstants.THOST_FTDC_PC_Futures ) {
                        continue;
                    }
                    //再次检查忽略SR901C4700等期权/组合品种
                    if ( !Future.PATTERN.matcher(r.InstrumentID).matches() ) {
                        continue;
                    }
                    Exchangeable e = null;
                    try {
                        e = Exchangeable.fromString(r.ExchangeID,r.InstrumentID, r.InstrumentName);
                    }catch(Throwable t) {
                        unknownInstrumentIds.add(r.InstrumentID);
                        continue;
                    }
                    if (!filter.isEmpty() && !filter.contains(e)) { //忽略非主力品种
                        continue;
                    }
                    allInstruments.add(e);
                    JsonObject info = new JsonObject();
                    info.addProperty("priceTick", PriceUtil.price2str(r.PriceTick));
                    info.addProperty("volumeMultiple", r.VolumeMultiple);
                    feeInfos.add(e.toString(), info);
                }
            }
            if ( !unknownInstrumentIds.isEmpty() ) {
                logger.info("Ctp "+id+" 忽略不支持合约: "+unknownInstrumentIds);
            }
        }
        {//查询手续费使用, 每天只加载一次
            int count=0;
            for(Exchangeable e:allInstruments) {
                JsonObject info = (JsonObject)feeInfos.get(e.toString());
                if ( info==null ) {
                    continue;
                }
                if ( !info.has("marginRatios")) {
                    double[] marginRatios = reqInstrumentMarginRatio(e);
                    if ( null!=marginRatios ) {
                        info.add("marginRatios", JsonUtil.object2json(marginRatios));
                    }
                }
                if ( !info.has("commissionRatios")) {
                    double[] commissionRatios = reqInstrumentCommissionRatios(e);
                    if ( null!=commissionRatios ) {
                        info.add("commissionRatios", JsonUtil.object2json(commissionRatios));
                    }
                }
                count++;
                if ( 0==count%50 ) {
                    logger.info("查询 "+count+"/"+allInstruments.size()+" 合约手续费信息");
                }
            }
            long t1 = System.currentTimeMillis();
            logger.info("加载 "+feeInfos.size()+" 合约手续费信息, 耗时 "+(t1-t0)/1000+" 秒");
        }
        JsonObject result = new JsonObject();
        result.add("feeInfos", feeInfos);
        return result.toString();
    }

    private double[] reqInstrumentMarginRatio(Exchangeable e) throws Exception
    {
        double[] marginRatios = null;
        CThostFtdcQryInstrumentMarginRateField f = new CThostFtdcQryInstrumentMarginRateField();
        f.HedgeFlag=JctpConstants.THOST_FTDC_HF_Speculation; f.BrokerID = brokerId; f.InvestorID = userId; f.ExchangeID = e.exchange().name().toUpperCase(); f.InstrumentID = e.id();
        CThostFtdcInstrumentMarginRateField r = traderApi.SyncReqQryInstrumentMarginRate(f);
        if (null!=r && (r.LongMarginRatioByMoney!=0 || r.LongMarginRatioByVolume!=0) ) {
            marginRatios = new double[MarginRatio.values().length];
            marginRatios[MarginRatio.LongByMoney.ordinal()]= r.LongMarginRatioByMoney;
            marginRatios[MarginRatio.LongByVolume.ordinal()]= r.LongMarginRatioByVolume;
            marginRatios[MarginRatio.ShortByMoney.ordinal()]= r.ShortMarginRatioByMoney;
            marginRatios[MarginRatio.ShortByVolume.ordinal()]= r.ShortMarginRatioByVolume;
        }
        return marginRatios;
    }

    private double[] reqInstrumentCommissionRatios(Exchangeable e) throws Exception
    {
        CThostFtdcQryInstrumentCommissionRateField f = new CThostFtdcQryInstrumentCommissionRateField();
        f.BrokerID = brokerId; f.InvestorID = userId; f.InstrumentID = e.id();
        CThostFtdcInstrumentCommissionRateField r = traderApi.SyncReqQryInstrumentCommissionRate(f);
        if( r==null ) {
            return null;
        }
        double[] commissionRatios = new double[CommissionRatio.values().length];
        commissionRatios[CommissionRatio.OpenByMoney.ordinal()]= r.OpenRatioByMoney;
        commissionRatios[CommissionRatio.OpenByVolume.ordinal()]= r.OpenRatioByVolume;
        commissionRatios[CommissionRatio.CloseByMoney.ordinal()]= r.CloseRatioByMoney;
        commissionRatios[CommissionRatio.CloseByVolume.ordinal()]= r.CloseRatioByVolume;
        commissionRatios[CommissionRatio.CloseTodayByMoney.ordinal()]= r.CloseTodayRatioByMoney;
        commissionRatios[CommissionRatio.CloseTodayByVolume.ordinal()]= r.CloseTodayRatioByVolume;
        return commissionRatios;
    }

    public Collection<Exchangeable> syncQueryInstruments() throws Exception
    {
        CThostFtdcInstrumentField[] rr = syncQueryInstruments0();
        List<Exchangeable> result = new ArrayList<>();
        for(int i=0;i<rr.length;i++) {
            CThostFtdcInstrumentField r = rr[i];
            try {
                switch (r.ProductClass) {
                case JctpConstants.THOST_FTDC_PC_Futures:{
                    Exchangeable e = Exchangeable.fromString(r.ExchangeID, r.InstrumentID);
                    result.add(e);
                }
                break;
                case JctpConstants.THOST_FTDC_PC_Combination:{
                    if ( FutureCombo.acceptId(r.InstrumentID) ) {
                        Exchangeable e = Exchangeable.fromString(r.ExchangeID, r.InstrumentID);
                        result.add(e);
                    }
                }
                break;
                case JctpConstants.THOST_FTDC_PC_Options:{
                    Exchangeable e = Exchangeable.fromString(r.ExchangeID, r.InstrumentID);
                    result.add(e);
                }
                break;
                }
            } catch (Throwable t) {
                logger.error("Parse Instrument " + r + " failed: " + t.toString(), t);
            }
        }
        return result;
    }

    @Override
    public String syncQryPositions() throws Exception
    {
        String tradingDay = traderApi.GetTradingDay();
        CThostFtdcQryInvestorPositionField f = new CThostFtdcQryInvestorPositionField();
        f.BrokerID = brokerId; f.InvestorID = userId;
        CThostFtdcInvestorPositionField[] posFields= traderApi.SyncAllReqQryInvestorPosition(f);
        JsonObject posInfos = new JsonObject();
        Map<String, int[]> posVolumes = new HashMap<>();
        Map<String, long[]> posMoney = new HashMap<>();

        marginByPos = new HashMap<>();
        for(int i=0;i<posFields.length;i++){
            CThostFtdcInvestorPositionField r = posFields[i];
            Exchangeable e = Exchangeable.fromString(r.ExchangeID, r.InstrumentID);
            //只支持期货, 期权
            switch(e.getType()) {
            case FUTURE:
            case OPTION:
                break;
            default:
                continue;
            }
            PosDirection posDir = CtpUtil.ctp2PosDirection(r.PosiDirection);
            JsonObject posInfo = new JsonObject();
            posInfo.addProperty("direction", posDir.name());
            posInfos.add(e.toString(), posInfo);
            int[] volumes = new int[PosVolume.values().length];
            volumes[PosVolume.Position.ordinal()] = r.Position;
            volumes[PosVolume.OpenVolume.ordinal()]= r.OpenVolume;
            volumes[PosVolume.CloseVolume.ordinal()]= r.CloseVolume;
            volumes[PosVolume.TodayPosition.ordinal()]= r.TodayPosition;
            volumes[PosVolume.YdPosition.ordinal()]= r.YdPosition;
            volumes[PosVolume.LongFrozen.ordinal()]= r.LongFrozen;
            volumes[PosVolume.ShortFrozen.ordinal()]= r.ShortFrozen;
            posVolumes.put(e.toString(), volumes);

            long[] money = new long[PosMoney.values().length];
            money[PosMoney.LongFrozenAmount.ordinal()] = PriceUtil.price2long(r.LongFrozenAmount);
            money[PosMoney.ShortFrozenAmount.ordinal()]= PriceUtil.price2long(r.ShortFrozenAmount);
            money[PosMoney.OpenAmount.ordinal()]= PriceUtil.price2long(r.OpenAmount);
            money[PosMoney.CloseAmount.ordinal()]= PriceUtil.price2long(r.CloseAmount);
            money[PosMoney.OpenCost.ordinal()]= PriceUtil.price2long(r.OpenCost);
            money[PosMoney.PositionCost.ordinal()]= PriceUtil.price2long(r.PositionCost);
            money[PosMoney.PreMargin.ordinal()]= PriceUtil.price2long(r.PreMargin);
            money[PosMoney.UseMargin.ordinal()]= PriceUtil.price2long(r.UseMargin);
            money[PosMoney.FrozenMargin.ordinal()]= PriceUtil.price2long(r.FrozenMargin);
            //money[PosMoney.FrozenCash.ordinal()]= PriceUtil.price2long(r.FrozenCash);
            money[PosMoney.FrozenCommission.ordinal()]= PriceUtil.price2long(r.FrozenCommission);
            //money[PosMoney.CashIn.ordinal()]= PriceUtil.price2long(r.CashIn);
            money[PosMoney.Commission.ordinal()] = PriceUtil.price2long(r.Commission);
            money[PosMoney.CloseProfit.ordinal()]= PriceUtil.price2long(r.CloseProfit);
            money[PosMoney.PositionProfit.ordinal()]= PriceUtil.price2long(r.PositionProfit);
            money[PosMoney.PreSettlementPrice.ordinal()]= PriceUtil.price2long(r.PreSettlementPrice);
            money[PosMoney.SettlementPrice.ordinal()]= PriceUtil.price2long(r.SettlementPrice);
            money[PosMoney.ExchangeMargin.ordinal()]= PriceUtil.price2long(r.ExchangeMargin);
            posMoney.put(e.toString(), money);
        }
        //从明细分别计算 多空的今昨持仓
        CThostFtdcQryInvestorPositionDetailField f2 = new CThostFtdcQryInvestorPositionDetailField();
        f2.BrokerID = brokerId;
        f2.InvestorID = userId;
        CThostFtdcInvestorPositionDetailField[] posDetailFields = traderApi.SyncAllReqQryInvestorPositionDetail(f2);
        for(int i=0;i<posDetailFields.length;i++){
            CThostFtdcInvestorPositionDetailField d= posDetailFields[i];
            Exchangeable e = Exchangeable.fromString(d.ExchangeID, d.InstrumentID);
            JsonObject posInfo = (JsonObject)posInfos.get(e.toString());
            if ( null==posInfo ) {
                continue;
            }
            int[] volumes = posVolumes.get(e.toString());
            long[] money = posMoney.get(e.toString());
            OrderDirection dir = CtpUtil.ctp2orderDirection(d.Direction);
            int volume = d.Volume;
            //忽略volume=0的已平仓的持仓明细
            if ( 0==volume ) {
                continue;
            }
            long margin = PriceUtil.price2long(d.Margin);
            boolean today = StringUtil.equals(tradingDay, d.OpenDate.trim());
            switch(dir) {
            case Buy:
                if ( today ) { //今仓
                    volumes[PosVolume.LongTodayPosition.ordinal()] += volume;
                }else {
                    volumes[PosVolume.LongYdPosition.ordinal()] += volume;
                }
                volumes[PosVolume.LongPosition.ordinal()] += volume;
                money[PosMoney.LongUseMargin.ordinal()] += margin;
                break;
            case Sell:
                if ( today ) { //今仓
                    volumes[PosVolume.ShortTodayPosition.ordinal()] += volume;
                }else {
                    volumes[PosVolume.ShortYdPosition.ordinal()] += volume;
                }
                volumes[PosVolume.ShortPosition.ordinal()] += volume;
                money[PosMoney.ShortUseMargin.ordinal()] += margin;
                break;
            }
            JsonArray details = (JsonArray)posInfo.get("details");
            if ( details==null ) {
                details = new JsonArray();
                posInfo.add("details", details);
            }
            JsonObject detail = new JsonObject();
            detail.addProperty("direction", dir.toPosDirection().name());
            detail.addProperty("volume", volume);
            detail.addProperty("price", d.OpenPrice);
            detail.addProperty("openDate", d.OpenDate);
            details.add(detail);;
            marginByPos.put(e, d);
        }
        for(String posKey:posInfos.keySet()) {
            JsonObject posInfo = (JsonObject)posInfos.get(posKey);
            int volumes[] = posVolumes.get(posKey);
            long[] money = posMoney.get(posKey);
            posInfo.add("volumes", TradeConstants.posVolume2json(volumes));
            posInfo.add("money", TradeConstants.posMoney2json(money));
        }
        return posInfos.toString();
    }

    public String syncQryOrders() throws Exception
    {
        CThostFtdcQryOrderField f = new CThostFtdcQryOrderField();
        CThostFtdcOrderField[] orderFields = traderApi.SyncAllReqQryOrder(f);
        JsonArray result = new JsonArray();
        for(CThostFtdcOrderField orderField:orderFields) {
            JsonObject json = new JsonObject();
            json.addProperty("ref", orderField.OrderRef);
            json.addProperty("direction", CtpUtil.ctp2orderDirection(orderField.Direction).name());
            json.addProperty("limitPrice", orderField.LimitPrice);
            json.addProperty("priceType", CtpUtil.ctp2orderPriceType(orderField.OrderPriceType).name());
            if (orderField.CombOffsetFlag.length()>0) {
                json.addProperty("offsetFlag", CtpUtil.ctp2orderOffsetFlag(orderField.CombOffsetFlag.charAt(0)).name());
            }
            json.addProperty("volumeCondition", CtpUtil.ctp2orderVolumeCondition(orderField.VolumeCondition).name());

            JsonObject stateJson = new JsonObject();
            stateJson.addProperty("submitState", CtpUtil.ctp2orderSubmitState(orderField.OrderSubmitStatus).name());
            stateJson.addProperty("state", CtpUtil.ctp2orderState(orderField.OrderStatus, orderField.OrderSubmitStatus).name());
            stateJson.addProperty("timestamp", System.currentTimeMillis());
            stateJson.addProperty("stateMessage", orderField.StatusMsg);
            json.add("lastState", stateJson);
            JsonArray stateTuples = new JsonArray();
            stateTuples.add(stateJson);
            json.add("stateTuples", stateTuples);
            //计算时间
            String time = orderField.InsertTime;
            if ( !CtpUtil.isEmptyTime(orderField.UpdateTime) ) {
                time = orderField.UpdateTime;
            }
            if ( !CtpUtil.isEmptyTime(orderField.SuspendTime) ) {
                time = orderField.SuspendTime;
            }
            if ( !StringUtil.isEmpty(orderField.CancelTime)) {
                time = orderField.CancelTime;
            }

            JsonObject attrs = new JsonObject();
            attrs.addProperty(Order.ODRATR_CTP_SYS_ID, orderField.OrderSysID);
            attrs.addProperty(Order.ODRATR_CTP_STATUS, orderField.OrderStatus);
            attrs.addProperty(Order.ODRATR_CTP_FRONT_ID, orderField.FrontID);
            attrs.addProperty(Order.ODRATR_CTP_SESSION_ID, orderField.SessionID);
            json.add("attrs", attrs);
        }

        return result.toString();
    }

    @Override
    public void asyncSendOrder(Order order) throws AppException {
        order.setAttr(Order.ODRATR_CTP_FRONT_ID, Integer.toString(frontId));
        order.setAttr(Order.ODRATR_CTP_SESSION_ID, Integer.toString(sessionId));

        CThostFtdcInputOrderField req = new CThostFtdcInputOrderField();
        req.BrokerID = brokerId;
        req.UserID = userId;
        req.InvestorID = userId;
        req.OrderRef = order.getRef();
        req.Direction = CtpUtil.orderDirection2ctp(order.getDirection());
        req.CombOffsetFlag = CtpUtil.orderOffsetFlag2ctp(order.getOffsetFlags());
        req.OrderPriceType = CtpUtil.orderPriceType2ctp(order.getPriceType());
        req.LimitPrice = PriceUtil.long2price(order.getLimitPrice());
        req.VolumeTotalOriginal = order.getVolume(OdrVolume.ReqVolume);
        Exchangeable instrument = order.getInstrument();
        req.InstrumentID = instrument.id();
        req.ExchangeID = instrument.exchange().name();
        req.VolumeCondition = CtpUtil.orderVolumeCondition2ctp(order.getVolumeCondition());
        req.TimeCondition = THOST_FTDC_TC_GFD; //当日有效
        req.CombHedgeFlag =  STRING_THOST_FTDC_HF_Speculation; //投机
        req.ContingentCondition = THOST_FTDC_CC_Immediately; //立即触发
        req.ForceCloseReason = THOST_FTDC_FCC_NotForceClose; //强平原因: 非强平
        req.IsAutoSuspend = false;
        req.MinVolume = 1;

        listener.onOrderStateChanged(order, new OrderStateTuple(OrderState.Submitting, OrderSubmitState.InsertSubmitting, System.currentTimeMillis()), null);
        try{
            traderApi.ReqOrderInsert(req);
            listener.onOrderStateChanged(order, new OrderStateTuple(OrderState.Submitted, OrderSubmitState.InsertSubmitting, System.currentTimeMillis()), null);
        }catch(Throwable t) {
            logger.error("ReqOrderInsert failed: "+order, t);
            listener.onOrderStateChanged(order, new OrderStateTuple(OrderState.Failed, OrderSubmitState.InsertRejected, System.currentTimeMillis()), null);
            throw new AppException(t, ERRCODE_TRADE_SEND_ORDER_FAILED, "CTP "+frontId+" ReqOrderInsert failed: "+t.toString());
        }
    }

    /**
     * 取消报单动作
     */
    @Override
    public void asyncCancelOrder(Order order) throws AppException
    {
        CThostFtdcInputOrderActionField action = fillOrderAction(order);
        action.ActionFlag = JctpConstants.THOST_FTDC_AF_Delete;

        OrderState state = order.getStateTuple().getState();
        try{
            listener.onOrderStateChanged(order, new OrderStateTuple(state, OrderSubmitState.CancelSubmitting, System.currentTimeMillis()), null);
            traderApi.ReqOrderAction(action);
            listener.onOrderStateChanged(order, new OrderStateTuple(state, OrderSubmitState.CancelSubmitted, System.currentTimeMillis()), null);
        }catch(Throwable t) {
            logger.error("ReqOrderAction cancel order "+order.getRef()+" failed: "+order, t);
            throw new AppException(t, ERRCODE_TRADE_CANCEL_ORDER_FAILED, "CTP "+frontId+" ReqOrderAction cancel order "+order.getRef()+" failed: "+t.toString());
        }
    }

    @Override
    public void asyncModifyOrder(Order order, OrderBuilder builder) throws AppException
    {
        CThostFtdcInputOrderActionField action = fillOrderAction(order);
        action.ActionFlag = JctpConstants.THOST_FTDC_AF_Modify;
        action.LimitPrice = PriceUtil.long2price(builder.getLimitPrice());
        if ( builder.getVolume()>0 ) {
            action.VolumeChange = builder.getVolume();
        }

        OrderState state = order.getStateTuple().getState();
        try{
            listener.onOrderStateChanged(order, new OrderStateTuple(state, OrderSubmitState.ModifySubmitting, System.currentTimeMillis()), null);
            traderApi.ReqOrderAction(action);
            listener.onOrderStateChanged(order, new OrderStateTuple(state, OrderSubmitState.ModifySubmitted, System.currentTimeMillis()), null);
        }catch(Throwable t) {
            logger.error("ReqOrderAction modify order "+order.getRef()+" failed: "+order, t);
            throw new AppException(t, ERRCODE_TRADE_MODIFY_ORDER_FAILED, "CTP "+frontId+" ReqOrderAction modify order "+order.getRef()+" failed: "+t.toString());
        }
    }

    private CThostFtdcInstrumentField[] syncQueryInstruments0() throws Exception
    {
        if ( null==instruments) {
            traderApi.setSyncReqTimeout(120);
            long t5_0 = System.currentTimeMillis();
            instruments = traderApi.SyncAllReqQryInstrument(new CThostFtdcQryInstrumentField());
            long t5_1 = System.currentTimeMillis();
            logger.info("加载 "+instruments.length+" 合约, 耗时 "+(t5_1-t5_0)+" 毫秒");
            traderApi.setSyncReqTimeout(30);
        }
        return instruments;
    }

    private CThostFtdcInputOrderActionField fillOrderAction(Order order) {
        CThostFtdcInputOrderActionField action = new CThostFtdcInputOrderActionField();
        action.ActionFlag = JctpConstants.THOST_FTDC_AF_Delete;
        action.BrokerID = brokerId;
        action.UserID = userId;
        action.InvestorID = userId;
        Exchangeable instrument = order.getInstrument();
        action.InstrumentID = instrument.id();
        action.ExchangeID = instrument.exchange().name();

        String orderSysId = ConversionUtil.toString(order.getAttr(Order.ODRATR_CTP_SYS_ID));
        if ( !StringUtil.isEmpty(orderSysId) ) {
            action.OrderSysID = orderSysId;
        }else {
            action.SessionID = ConversionUtil.toInt(order.getAttr(Order.ODRATR_CTP_SESSION_ID));
            action.FrontID = ConversionUtil.toInt(order.getAttr(Order.ODRATR_CTP_FRONT_ID));
            action.OrderRef = order.getRef();
        }

        return action;
    }

    private boolean shouldAuthenticate() {
        return !StringUtil.isEmpty(authCode);
    }

    private void reqAuthenticate() {
        CThostFtdcReqAuthenticateField f = new CThostFtdcReqAuthenticateField();
        f.BrokerID = brokerId;
        f.UserID = userId;
        f.AuthCode = decrypt(authCode);
        f.UserProductInfo = decrypt(userProductInfo);

        //使用反射方式设置appId字段
        try {
            Field appIdField = f.getClass().getField("AppID");
            appIdField.set(f, decrypt(appId));
        }catch(Throwable t) {}

        try{
            traderApi.ReqAuthenticate(f);
        }catch(Throwable t) {
            logger.error("ReqAuthenticate failed", t);
            changeState(ConnState.ConnectFailed);
        }
    }

    private void reqUserLogin() {
        CThostFtdcReqUserLoginField f = new CThostFtdcReqUserLoginField();
        f.BrokerID = brokerId;
        f.UserID = userId;
        f.Password = decrypt(password);
        f.UserProductInfo = decrypt(userProductInfo);
        try{
            traderApi.ReqUserLogin(f);
        }catch(Throwable t) {
            logger.error("ReqUserLogin failed", t);
            changeState(ConnState.ConnectFailed);
        }
    }

    private static String decrypt(String str) {
        String result = str;
        if ( EncryptionUtil.isEncryptedData(str) ) {
            result = new String( EncryptionUtil.symmetricDecrypt(str), StringUtil.UTF8);
        }
        return result;
    }

    public void ctpOnFrontConnected() {
        logger.info("OnFrontConnected");
        if ( getState()==ConnState.Connecting ) {
            //login
            if ( shouldAuthenticate() ) {
                reqAuthenticate();
            }else {
                reqUserLogin();
            }
        }
    }

    private void ctpOnFrontDisconnected(int nReason) {
        logger.info("OnFrontDisconnected: "+nReason);
        changeState(ConnState.Disconnected);
    }

    private void ctpOnRspAuthenticate(CThostFtdcRspAuthenticateField pRspAuthenticateField, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
        logger.info("OnRspAuthenticate: "+pRspAuthenticateField+" "+pRspInfo);
        if ( pRspInfo.ErrorID==0 ) {
            reqUserLogin();
        }else {
            changeState(ConnState.ConnectFailed);
        }
    }

    private void ctpOnRspUserLogin(CThostFtdcRspUserLoginField pRspUserLogin, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
        logger.info("OnRspUserLogin: "+pRspUserLogin+" "+pRspInfo);
        if ( pRspInfo.ErrorID==0 ) {
            frontId = pRspUserLogin.FrontID;
            sessionId = pRspUserLogin.SessionID;
            changeState(ConnState.Connected);
            tradingDay = DateUtil.str2localdate(pRspUserLogin.TradingDay);
            LocalDate tradingDay2 = beansContainer.getBean(MarketTimeService.class).getTradingDay();
            if ( !tradingDay.equals(tradingDay2)) {
                logger.error("计算交易日失败, CTP: "+tradingDay+", 计算: "+tradingDay2);
            }
        }else {
            changeState(ConnState.ConnectFailed);
        }
    }

    private void ctpOnRspUserLogout(CThostFtdcUserLogoutField pUserLogout, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
        logger.info("OnRspUserLogout: "+pUserLogout+" "+pRspInfo);
        changeState(ConnState.Disconnected);
    }

    /**
     * 报单错误(柜台)
     */
    private void ctpOnRspOrderInsert(CThostFtdcInputOrderField pInputOrder, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
        asyncEventService.publishProcessorEvent(processor, CtpTxnEventProcessor.DATA_TYPE_RSP_ORDER_INSERT, pInputOrder, pRspInfo);
        logger.error("OnRspOrderInsert: "+pInputOrder+" "+pRspInfo);
    }

    /**
     * 撤单错误回报（柜台）
     */
    private void ctpOnRspOrderAction(CThostFtdcInputOrderActionField pInputOrderAction, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
        asyncEventService.publishProcessorEvent(processor, CtpTxnEventProcessor.DATA_TYPE_RSP_ORDER_ACTION, pInputOrderAction, pRspInfo);
        logger.error("OnRspOrderAction: "+pInputOrderAction+" "+pRspInfo);
    }

    /**
     * 报单回报
     */
    private void ctpOnRtnOrder(CThostFtdcOrderField pOrder) {
        asyncEventService.publishProcessorEvent(processor,  CtpTxnEventProcessor.DATA_TYPE_RTN_ORDER, pOrder, null);
    }

    /**
     * 成交回报
     */
    private void ctpOnRtnTrade(CThostFtdcTradeField pTrade) {
        asyncEventService.publishProcessorEvent(processor,  CtpTxnEventProcessor.DATA_TYPE_RTN_TRADE, pTrade, null);
    }

    /**
     * 报单错误(交易所)
     */
    private void ctpOnErrRtnOrderInsert(CThostFtdcInputOrderField pInputOrder, CThostFtdcRspInfoField pRspInfo) {
        asyncEventService.publishProcessorEvent(processor, CtpTxnEventProcessor.DATA_TYPE_ERR_RTN_ORDER_INSERT, pInputOrder, pRspInfo);
    }

    /**
     * 撤单错误(交易所)
     */
    private void ctpOnErrRtnOrderAction(CThostFtdcOrderActionField pOrderAction, CThostFtdcRspInfoField pRspInfo) {
        if ( pOrderAction.SessionID==sessionId) {
            asyncEventService.publishProcessorEvent(processor,  CtpTxnEventProcessor.DATA_TYPE_ERR_RTN_ORDER_ACTION, pOrderAction, pRspInfo);
        }else {
            logger.info("IGNORE order action from other CTP session: "+pOrderAction);
        }
    }

    private void ctpOnAccountTransfer(AccountTransferAction transferAction, CThostFtdcRspTransferField transferInfo) {
        asyncEventService.publishProcessorEvent(processor,  CtpTxnEventProcessor.DATA_TYPE_RTN_ACCOUNT_TRANSFER, transferAction, transferInfo);
    }

}

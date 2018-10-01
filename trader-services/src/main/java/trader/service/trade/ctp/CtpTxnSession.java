package trader.service.trade.ctp;

import java.util.Properties;

import net.jctp.CThostFtdcAccountregisterField;
import net.jctp.CThostFtdcBatchOrderActionField;
import net.jctp.CThostFtdcBrokerTradingAlgosField;
import net.jctp.CThostFtdcBrokerTradingParamsField;
import net.jctp.CThostFtdcBulletinField;
import net.jctp.CThostFtdcCFMMCTradingAccountKeyField;
import net.jctp.CThostFtdcCFMMCTradingAccountTokenField;
import net.jctp.CThostFtdcCancelAccountField;
import net.jctp.CThostFtdcChangeAccountField;
import net.jctp.CThostFtdcCombActionField;
import net.jctp.CThostFtdcCombInstrumentGuardField;
import net.jctp.CThostFtdcContractBankField;
import net.jctp.CThostFtdcDepthMarketDataField;
import net.jctp.CThostFtdcEWarrantOffsetField;
import net.jctp.CThostFtdcErrorConditionalOrderField;
import net.jctp.CThostFtdcExchangeField;
import net.jctp.CThostFtdcExchangeMarginRateAdjustField;
import net.jctp.CThostFtdcExchangeMarginRateField;
import net.jctp.CThostFtdcExchangeRateField;
import net.jctp.CThostFtdcExecOrderActionField;
import net.jctp.CThostFtdcExecOrderField;
import net.jctp.CThostFtdcForQuoteField;
import net.jctp.CThostFtdcForQuoteRspField;
import net.jctp.CThostFtdcInputBatchOrderActionField;
import net.jctp.CThostFtdcInputCombActionField;
import net.jctp.CThostFtdcInputExecOrderActionField;
import net.jctp.CThostFtdcInputExecOrderField;
import net.jctp.CThostFtdcInputForQuoteField;
import net.jctp.CThostFtdcInputOptionSelfCloseActionField;
import net.jctp.CThostFtdcInputOptionSelfCloseField;
import net.jctp.CThostFtdcInputOrderActionField;
import net.jctp.CThostFtdcInputOrderField;
import net.jctp.CThostFtdcInputQuoteActionField;
import net.jctp.CThostFtdcInputQuoteField;
import net.jctp.CThostFtdcInstrumentCommissionRateField;
import net.jctp.CThostFtdcInstrumentField;
import net.jctp.CThostFtdcInstrumentMarginRateField;
import net.jctp.CThostFtdcInstrumentOrderCommRateField;
import net.jctp.CThostFtdcInstrumentStatusField;
import net.jctp.CThostFtdcInvestUnitField;
import net.jctp.CThostFtdcInvestorField;
import net.jctp.CThostFtdcInvestorPositionCombineDetailField;
import net.jctp.CThostFtdcInvestorPositionDetailField;
import net.jctp.CThostFtdcInvestorPositionField;
import net.jctp.CThostFtdcInvestorProductGroupMarginField;
import net.jctp.CThostFtdcMMInstrumentCommissionRateField;
import net.jctp.CThostFtdcMMOptionInstrCommRateField;
import net.jctp.CThostFtdcNoticeField;
import net.jctp.CThostFtdcNotifyQueryAccountField;
import net.jctp.CThostFtdcOpenAccountField;
import net.jctp.CThostFtdcOptionInstrCommRateField;
import net.jctp.CThostFtdcOptionInstrTradeCostField;
import net.jctp.CThostFtdcOptionSelfCloseActionField;
import net.jctp.CThostFtdcOptionSelfCloseField;
import net.jctp.CThostFtdcOrderActionField;
import net.jctp.CThostFtdcOrderField;
import net.jctp.CThostFtdcParkedOrderActionField;
import net.jctp.CThostFtdcParkedOrderField;
import net.jctp.CThostFtdcProductExchRateField;
import net.jctp.CThostFtdcProductField;
import net.jctp.CThostFtdcProductGroupField;
import net.jctp.CThostFtdcQueryCFMMCTradingAccountTokenField;
import net.jctp.CThostFtdcQueryMaxOrderVolumeField;
import net.jctp.CThostFtdcQuoteActionField;
import net.jctp.CThostFtdcQuoteField;
import net.jctp.CThostFtdcRemoveParkedOrderActionField;
import net.jctp.CThostFtdcRemoveParkedOrderField;
import net.jctp.CThostFtdcReqAuthenticateField;
import net.jctp.CThostFtdcReqQueryAccountField;
import net.jctp.CThostFtdcReqRepealField;
import net.jctp.CThostFtdcReqTransferField;
import net.jctp.CThostFtdcReqUserLoginField;
import net.jctp.CThostFtdcRspAuthenticateField;
import net.jctp.CThostFtdcRspInfoField;
import net.jctp.CThostFtdcRspRepealField;
import net.jctp.CThostFtdcRspTransferField;
import net.jctp.CThostFtdcRspUserLoginField;
import net.jctp.CThostFtdcSecAgentACIDMapField;
import net.jctp.CThostFtdcSecAgentCheckModeField;
import net.jctp.CThostFtdcSettlementInfoConfirmField;
import net.jctp.CThostFtdcSettlementInfoField;
import net.jctp.CThostFtdcTradeField;
import net.jctp.CThostFtdcTradingAccountField;
import net.jctp.CThostFtdcTradingAccountPasswordUpdateField;
import net.jctp.CThostFtdcTradingCodeField;
import net.jctp.CThostFtdcTradingNoticeField;
import net.jctp.CThostFtdcTradingNoticeInfoField;
import net.jctp.CThostFtdcTransferBankField;
import net.jctp.CThostFtdcTransferSerialField;
import net.jctp.CThostFtdcUserLogoutField;
import net.jctp.CThostFtdcUserPasswordUpdateField;
import net.jctp.TraderApi;
import net.jctp.TraderApiListener;
import trader.common.util.EncryptionUtil;
import trader.common.util.StringUtil;
import trader.service.ServiceConstants.ConnStatus;
import trader.service.trade.AbsTxnSession;
import trader.service.trade.AccountImpl;
import trader.service.trade.TradeConstants.TxnProvider;
import trader.service.trade.TradeServiceImpl;

public class CtpTxnSession extends AbsTxnSession implements TraderApiListener {

    private TraderApi traderApi;

    public CtpTxnSession(TradeServiceImpl tradeService, AccountImpl account) {
        super(tradeService, account);
    }

    @Override
    public TxnProvider getTxnProvider() {
        return TxnProvider.ctp;
    }

    @Override
    public void connect() {
        try {
            changeStatus(ConnStatus.Connecting);
            traderApi = new TraderApi();
            traderApi.setListener(this);
            String frontUrl = account.getConnectionProps().getProperty("frontUrl");
            traderApi.Connect(frontUrl);
        }catch(Throwable t) {
            logger.error("Connect failed", t);
            changeStatus(ConnStatus.ConnectFailed);
        }
    }

    @Override
    protected void closeImpl() {
        if ( traderApi!=null ) {
            try{
                traderApi.Close();
            }catch(Throwable t) {}
            traderApi = null;
        }
    }

    private boolean shouldAuthenticate() {
        Properties props = account.getConnectionProps();
        return !StringUtil.isEmpty(props.getProperty("authCode"));
    }

    private void reqAuthenticate() {
        Properties props = account.getConnectionProps();
        CThostFtdcReqAuthenticateField f = new CThostFtdcReqAuthenticateField();
        f.BrokerID = props.getProperty("brokerId");
        f.AuthCode = props.getProperty("authCode");
        try{
            traderApi.ReqAuthenticate(f);
        }catch(Throwable t) {
            logger.error("ReqAuthenticate failed", t);
            changeStatus(ConnStatus.ConnectFailed);
        }
    }

    private void reqUserLogin() {
        CThostFtdcReqUserLoginField f = new CThostFtdcReqUserLoginField();
        Properties props = account.getConnectionProps();
        f.BrokerID = props.getProperty("brokerId");
        f.UserID = props.getProperty("userId");
        f.Password = props.getProperty("password");
        if ( EncryptionUtil.isEncryptedData(f.UserID) ) {
            f.UserID = new String( EncryptionUtil.symmetricDecrypt(f.UserID), StringUtil.UTF8);
        }
        if ( EncryptionUtil.isEncryptedData(f.Password) ) {
            f.Password = new String( EncryptionUtil.symmetricDecrypt(f.Password), StringUtil.UTF8);
        }
        try{
            traderApi.ReqUserLogin(f);
        }catch(Throwable t) {
            logger.error("ReqUserLogin failed", t);
            changeStatus(ConnStatus.ConnectFailed);
        }
    }

    @Override
    public void OnFrontConnected() {
        logger.info("OnFrontConnected");
        if ( getStatus()==ConnStatus.Connecting ) {
            //login
            if ( shouldAuthenticate() ) {
                reqAuthenticate();
            }else {
                reqUserLogin();
            }
        }
    }

    @Override
    public void OnFrontDisconnected(int nReason) {
        logger.info("OnFrontDisconnected: "+nReason);
        changeStatus(ConnStatus.Disconnected);
    }

    @Override
    public void OnHeartBeatWarning(int nTimeLapse) {
        logger.info("OnHeartBeatWarning: "+nTimeLapse);
    }

    @Override
    public void OnRspAuthenticate(CThostFtdcRspAuthenticateField pRspAuthenticateField, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
        logger.info("OnRspAuthenticate: "+pRspAuthenticateField+" "+pRspInfo);
        if ( pRspInfo.ErrorID==0 ) {
            reqUserLogin();
        }else {
            changeStatus(ConnStatus.ConnectFailed);
        }
    }

    @Override
    public void OnRspUserLogin(CThostFtdcRspUserLoginField pRspUserLogin, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
        logger.info("OnRspUserLogin: "+pRspUserLogin+" "+pRspInfo);
        if ( pRspInfo.ErrorID==0 ) {
            changeStatus(ConnStatus.Connected);
        }else {
            changeStatus(ConnStatus.ConnectFailed);
        }
    }

    @Override
    public void OnRspUserLogout(CThostFtdcUserLogoutField pUserLogout, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
        logger.info("OnRspUserLogout: "+pUserLogout+" "+pRspInfo);
    }

    @Override
    public void OnRspUserPasswordUpdate(CThostFtdcUserPasswordUpdateField pUserPasswordUpdate, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
        logger.info("OnRspUserPasswordUpdate: "+pUserPasswordUpdate+" "+pRspInfo);
    }

    @Override
    public void OnRspTradingAccountPasswordUpdate(CThostFtdcTradingAccountPasswordUpdateField pTradingAccountPasswordUpdate, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
        logger.info("OnRspTradingAccountPasswordUpdate: "+pTradingAccountPasswordUpdate+" "+pRspInfo);
    }

    @Override
    public void OnRspOrderInsert(CThostFtdcInputOrderField pInputOrder, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
        logger.info("OnRspOrderInsert: "+pInputOrder+" "+pRspInfo);
    }

    @Override
    public void OnRspParkedOrderInsert(CThostFtdcParkedOrderField pParkedOrder, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
        logger.info("OnRspParkedOrderInsert: "+pParkedOrder+" "+pRspInfo);
    }

    @Override
    public void OnRspParkedOrderAction(CThostFtdcParkedOrderActionField pParkedOrderAction, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
        logger.info("OnRspParkedOrderAction: "+pParkedOrderAction+" "+pRspInfo);
    }

    @Override
    public void OnRspOrderAction(CThostFtdcInputOrderActionField pInputOrderAction, CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
        logger.info("OnRspOrderAction: "+pInputOrderAction+" "+pRspInfo);
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
    public void OnRspQryInstrumentCommissionRate(CThostFtdcInstrumentCommissionRateField pInstrumentCommissionRate, CThostFtdcRspInfoField pRspInfo, int nRequestID,
    boolean bIsLast) {
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
        logger.info("OnRspError: "+pRspInfo);
    }

    @Override
    public void OnRtnOrder(CThostFtdcOrderField pOrder) {
        logger.info("OnRtnOrder: "+pOrder);
    }

    @Override
    public void OnRtnTrade(CThostFtdcTradeField pTrade) {
        logger.info("OnRtnTrade: "+pTrade);
    }

    @Override
    public void OnErrRtnOrderInsert(CThostFtdcInputOrderField pInputOrder, CThostFtdcRspInfoField pRspInfo) {
        logger.info("OnErrRtnOrderInsert: "+pInputOrder+" "+pRspInfo);
    }

    @Override
    public void OnErrRtnOrderAction(CThostFtdcOrderActionField pOrderAction, CThostFtdcRspInfoField pRspInfo) {
        logger.info("OnErrRtnOrderAction: "+pOrderAction+" "+pRspInfo);
    }

    @Override
    public void OnRtnInstrumentStatus(CThostFtdcInstrumentStatusField pInstrumentStatus) {
        logger.info("OnRtnInstrumentStatus: "+pInstrumentStatus);
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
    }

    @Override
    public void OnRtnFromFutureToBankByBank(CThostFtdcRspTransferField pRspTransfer) {
        logger.info("OnRtnFromFutureToBankByBank: "+pRspTransfer);
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
}

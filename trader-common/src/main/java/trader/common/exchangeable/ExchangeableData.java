package trader.common.exchangeable;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import trader.common.tick.PriceLevel;
import trader.common.util.*;
import trader.common.util.concurrent.LockWrapper;

/**
 * 历史数据访问
 */
public class ExchangeableData {

    public static final String SUBDIR_SUM = "_sum";

    /**
     * 数据分类
     */
    public static class DataInfo{
        private String name;
        private String altName;
        private String[] columns;
        private PriceLevel priceLevel;

        public DataInfo(String name, PriceLevel priceLevel, String[] columns, String altName){
            this.name = name.toLowerCase().replaceAll("_", "-");
            this.priceLevel = priceLevel;
            if ( altName!=null ){
                this.altName = altName.toLowerCase().replaceAll("_", "-");
            }
            this.columns = columns;
            register(this);
        }

        public DataInfo(String name, PriceLevel priceLevel, String[] columns){
            this(name, priceLevel, columns, null);
        }

		public String name() {
			return name;
		}

		public PriceLevel priceLevel() {
			return priceLevel;
		}

		public DataInfo getData() {
			return null;
		}

		public String altName() {
			return altName;
		}

		public String[] getColumns() {
			return columns;
		};

        @Override
        public String toString(){ return name; }

        private static HashMap<String, DataInfo> allClassifications = new HashMap<>();
        private static void register(DataInfo c){
            allClassifications.put(c.name().toUpperCase(), c);
            if ( c.altName()!=null ){
                allClassifications.put(c.altName().toUpperCase(), c);
            }
        }
        public static DataInfo parse(String name){
            return allClassifications.get(name.toUpperCase());
        }
        private static List<DataInfo> getByLevel(PriceLevel level){
        	List<DataInfo> r = new ArrayList<>();
        	for(DataInfo c:allClassifications.values()){
        		if ( c.priceLevel()==level){
        			r.add(c);
        		}
        	}
        	return r;
        }
    }

    /**
     * 交易所的证券列表
     */
    public static final String SECURITY_LIST = "security_list.csv";
    /**
     * 可交易品种ID列表
     */
    public static final String EXCHANGEABLE_IDS = "exchangeable_ids.csv";
    /**
     * 停牌日期
     */
    public static final String MISC_SUSPENSION = "suspension.csv";
    /**
     * 杂类-股票金融信息
     */
    public static final String MISC_STOCK_FINANCE = "stock-finance.csv";
    /**
     * 杂类-股票分红数据
     */
    public static final String MISC_STOCK_DIVIDEND_SINA = "stock-dividend-sina.csv";
    public static final String MISC_STOCK_ALLOTMENT_SINA = "stock-allotment-sina.csv";
    public static final String MISC_FILES[] = {MISC_SUSPENSION, MISC_STOCK_FINANCE, MISC_STOCK_DIVIDEND_SINA, MISC_STOCK_ALLOTMENT_SINA};

    public static final String COLUMN_DATE = "Date";
    public static final String COLUMN_OPEN = "Open";
    public static final String COLUMN_HIGH = "High";
    public static final String COLUMN_LOW = "Low";
    public static final String COLUMN_CLOSE = "Close";
    public static final String COLUMN_VOLUME = "Volume";
    /**
     * 金额
     */
    public static final String COLUMN_TURNOVER = "Turnover";
    public static final String COLUMN_AMOUNT = "Amount";

    /**
     * 复权因子
     */
    public static final String COLUMN_SUBSCRIPTION_PRICE_FACTOR = "Subscription Price Factor";
    public static final String COLUMN_BEGIN_TIME = "BeginTime";
    public static final String COLUMN_END_TIME = "EndTime";
    public static final String COLUMN_TIME = "Time";
    public static final String COLUMN_PRICE = "Price";
    public static final String COLUMN_BUYSELL = "Buy/Sell";
    /**
     * 持仓
     */
    public static final String COLUMN_OPENINT = "OpenInt";

    public static final String[] DAY_COLUMNS = new String[]{
            COLUMN_DATE
            ,COLUMN_OPEN
            ,COLUMN_HIGH
            ,COLUMN_LOW
            ,COLUMN_CLOSE
            ,COLUMN_VOLUME
            ,COLUMN_TURNOVER};

    public static final String[] STOCK_DAY_COLUMNS = new String[]{
            COLUMN_DATE
            ,COLUMN_OPEN
            ,COLUMN_HIGH
            ,COLUMN_LOW
            ,COLUMN_CLOSE
            ,COLUMN_VOLUME
            ,COLUMN_TURNOVER
            ,COLUMN_SUBSCRIPTION_PRICE_FACTOR};

    public static final String[] TICK_STOCK_COLUMNS = new String[]{
            COLUMN_TIME
            ,COLUMN_PRICE
            ,COLUMN_VOLUME
            ,COLUMN_TURNOVER
            ,COLUMN_BUYSELL
    };

    public static final String[] FUTURE_MIN_COLUMNS = new String[]{
            COLUMN_BEGIN_TIME
            ,COLUMN_END_TIME
            ,COLUMN_OPEN
            ,COLUMN_HIGH
            ,COLUMN_CLOSE
            ,COLUMN_LOW
            ,COLUMN_VOLUME
            ,COLUMN_TURNOVER
            ,COLUMN_OPENINT
    };

    /**
     * 股票的TICK数据
     */
    public static final DataInfo TICK_STOCK = new DataInfo("TICK_STOCK", PriceLevel.TICKET, TICK_STOCK_COLUMNS, null);
    /**
     * 期货CTP的TICK数据
     */
    public static final DataInfo TICK_CTP = new DataInfo("TICK_CTP", PriceLevel.TICKET, null);

    public static final DataInfo MIN1 = new DataInfo("MIN1", PriceLevel.MIN1, FUTURE_MIN_COLUMNS);
    public static final DataInfo MIN3 = new DataInfo("MIN3", PriceLevel.MIN3, FUTURE_MIN_COLUMNS);
    public static final DataInfo MIN15 = new DataInfo("MIN15", PriceLevel.MIN15, FUTURE_MIN_COLUMNS);
    public static final DataInfo MIN30 = new DataInfo("MIN30", PriceLevel.MIN30, FUTURE_MIN_COLUMNS);

    /**
     * 指数价格
     */
    public static final DataInfo DAY = new DataInfo("DAY", PriceLevel.DAY, STOCK_DAY_COLUMNS);

    public static class TradingData{
        public LocalDate tradingDay;
        public String content;

        public TradingData(LocalDate tradingDay, String content){
            this.tradingDay = tradingDay;
            this.content = content;
        }
    }

    private static interface DataProvider{
        public boolean exists(File exchangeableDir, String file) throws IOException;
        public String read(File exchangeableDir, String file) throws IOException;
        public void save(File exchangeableDir, String file, String content) throws IOException;
        public boolean delete(File exchangeableDir, String file) throws IOException;
        public void saveAll(File exchangeableDir, String files[], DataProvider source) throws IOException;
    }

    private static class RegularFileDataProvider implements DataProvider{
        @Override
        public boolean exists(File exchangeableDir, String file) throws IOException {
            return (new File(exchangeableDir,file)).exists();
        }
        @Override
        public String read(File exchangeableDir, String file) throws IOException {
            return FileUtil.load(new File(exchangeableDir, file));
        }
        @Override
        public void save(File exchangeableDir, String file, String content) throws IOException{
            exchangeableDir.mkdirs();
            FileUtil.save(new File(exchangeableDir, file), content);
        }
        @Override
        public boolean delete(File exchangeableDir, String file) throws IOException{
            return (new File(exchangeableDir, file)).delete();
        }
        @Override
        public void saveAll(File exchangeableDir, String files[], DataProvider source) throws IOException{
            throw new RuntimeException("Not implemented yet");
        }

    }

    private static class ZipDataProvider implements DataProvider{
        HashMap<String,Boolean> oneFilePerYearInfo = new HashMap<>();

        public void setOneFilePerYear(String classificationName, boolean value){
            oneFilePerYearInfo.put(classificationName, value);
        }

        private boolean isOneFilePerYear(String classification){
            DataInfo c = DataInfo.parse(classification);
            if ( c!=null ){
                return c.priceLevel()!=null && c.priceLevel().ordinal()<PriceLevel.DAY.ordinal();
            }
            Boolean v = oneFilePerYearInfo.get(classification);
            if ( v!=null ){
                return v;
            }
            return false;
        }

        String detectData(String file){
            String[] parts = file.split("\\.");
            if ( parts.length==3){
                //yyyymmdd.classification.csv
                return parts[1];
            }else if ( parts.length==2){
                //classification.csv or XXXX.csv( in misc.zip )
                for(int i=0;i<MISC_FILES.length;i++){
                    if ( MISC_FILES[i].equalsIgnoreCase(file)){
                        return "misc.zip";
                    }
                }
                return parts[0];
            }
            return null;
        }

        private String getZipFileName(String file){
            String[] parts = file.split("\\.");
            if ( parts.length==3){
                //yyyymmdd.classification.csv
                String year = parts[0].substring(0, 4);
                if ( isOneFilePerYear(parts[1])){
                    return year+"."+parts[1]+".zip";
                }else{
                    return parts[1]+".zip";
                }
            }else if ( parts.length==2){
                //classification.csv or XXXX.csv( in misc.zip )
                for(int i=0;i<MISC_FILES.length;i++){
                    if ( MISC_FILES[i].equalsIgnoreCase(file)){
                        return "misc.zip";
                    }
                }
                return parts[0]+".zip";
            }else{
                throw new RuntimeException("Unable to get zip file for "+file);
            }
        }

        @Override
        public boolean exists(File exchangeableDir, String file) throws IOException
        {
            File zip = new File(exchangeableDir, getZipFileName(file));
            return ZipFileUtil.arhiveExists(zip, file);
        }

        @Override
        public String read(File exchangeableDir, String file) throws IOException
        {
            File zip = new File(exchangeableDir, getZipFileName(file));
            return ZipFileUtil.archiveRead(zip, file);
        }

        @Override
        public void save(File exchangeableDir, String file, String content) throws IOException
        {
            File zip = new File(exchangeableDir, getZipFileName(file));
            exchangeableDir.mkdirs();
            ZipFileUtil.archiveAdd(zip, content.getBytes(CHARSET), file);
        }

        @Override
        public boolean delete(File exchangeableDir, String file) throws IOException
        {
            throw new RuntimeException("Delete in zip file is not implemented");
        }

        @Override
        public void saveAll(File exchangeableDir, String files[], DataProvider source) throws IOException{
            exchangeableDir.mkdirs();
            List<String> myfiles = new ArrayList<>(Arrays.asList(files));
            Collections.sort(myfiles);
            String lastZipFileName = null;
            List<String> toSaveFiles = new LinkedList<>();
            List<byte[]> datas = new LinkedList<>();
            for(String f: myfiles){
                String currZipFileName = getZipFileName(f);
                if ( lastZipFileName!=null && !currZipFileName.equals(lastZipFileName)){
                    //needs to save in last zip file
                    ZipFileUtil.archiveAddAll(new File(exchangeableDir, lastZipFileName), toSaveFiles, datas);
                    toSaveFiles.clear();
                    datas.clear();
                }
                lastZipFileName = currZipFileName;
                toSaveFiles.add(f);
                datas.add(source.read(exchangeableDir, f).getBytes(CHARSET));
            }
            if ( toSaveFiles.size()>0 ){
                ZipFileUtil.archiveAddAll(new File(exchangeableDir, lastZipFileName), toSaveFiles, datas);
            }
        }
    }


    private static final String EXT_NAME = ".csv";
    private static final String CHARSET = "UTF-8";

    private boolean readOnly;
    private File dataDir;
    private Map<String,Lock> workingLocks = new HashMap<>();
    private Lock workingLock = new ReentrantLock();
    private DataProvider regularProvider = new RegularFileDataProvider();
    private DataProvider zipProvider= new ZipDataProvider();

    public ExchangeableData(File dataDir){
        this(dataDir, true);
    }

    public ExchangeableData(File dataDir, boolean readOnly){
        this.dataDir = dataDir;
        this.readOnly = readOnly;
    }

    public File getDataDir(){
        return dataDir;
    }

    public List<Exchange> getExchanges(){
        List<Exchange> result = new LinkedList<>();
        for(File f:dataDir.listFiles()){
            if ( f.isDirectory() ){
                Exchange e = Exchange.getInstance(f.getName());
                if ( e!=null ){
                    result.add(e);
                }
            }
        }
        return result;
    }

    public DataInfo detectData(Exchangeable exchangeable, PriceLevel level, LocalDate tradingDay)
        throws IOException
    {
    	try (FileLocker fileLocker = getFileLock(exchangeable);
                LockWrapper lockWrapper = getInternalLock(exchangeable);)
        {
    		File edir = getExchangeableDir(exchangeable);
    		List<DataInfo> possibleDataInfos = DataInfo.getByLevel(level);
    		for(DataInfo c:possibleDataInfos){
    			for(String dataFile : getDataFileName(c, tradingDay)){
                    if(  exists0(edir, dataFile) ){
                    	return c;
                    }
    			}
    		}
        }
    	return null;
    }

    /**
     * 为每个交易所保存当前可交易的标的(证券, 期货品种, 基金, 指数, 债券等等)列表
     */
    public void saveExchangeableIds(Exchange exchange, List<Exchangeable> exchangeables) throws IOException
    {
        File exchangeDir = getExchangeDir(exchange);
        File file = new File(exchangeDir, EXCHANGEABLE_IDS);
        CSVWriter csvWriter = new CSVWriter("code", "name");
        for(Exchangeable e:exchangeables){
            csvWriter.append(e.id(), e.name());
        }
        try(BufferedWriter writer = IOUtil.createBufferedWriter(file, StringUtil.UTF8, false);){
            writer.write(csvWriter.toString());
            writer.flush();
        }
    }

    public List<Exchangeable> loadExchangeabeIds(Exchange exchange) throws IOException
    {
        List<Exchangeable> result = new ArrayList<>(1024);
        File exchangeDir = getExchangeDir(exchange);
        File file = new File(exchangeDir, EXCHANGEABLE_IDS);
        if ( !file.exists() ){
            file = new File(exchangeDir, SECURITY_LIST);
        }

        if ( file.exists() ){
            CSVDataSet dataSet = CSVUtil.parse(IOUtil.createBufferedReader(file, StringUtil.UTF8), ',', true );
            while(dataSet.next()){
                result.add(Exchangeable.fromString(exchange.name(), dataSet.get("code"), dataSet.get("name").trim()));
            }
        }
        return result;
    }

    public boolean exists(Exchangeable exchangeable, DataInfo dataInfo, LocalDate tradingDay)
            throws IOException
    {
        try (FileLocker fileLocker = getFileLock(exchangeable);
                LockWrapper lockWrapper = getInternalLock(exchangeable);)
        {
            File edir = getExchangeableDir(exchangeable);
            for(String dataFile : getDataFileName(dataInfo, tradingDay)){
                if ( exists0(edir, dataFile) ){
                    return true;
                }
            }
            return false;
        }
    }

    public synchronized boolean exists(String subDir, LocalDate tradingDay, DataInfo dataInfo)
            throws IOException
    {
        File edir = new File(dataDir, subDir);
        for(String dataFile : getDataFileName(dataInfo, tradingDay)){
            if ( exists0(edir, dataFile) ){
                return true;
            }
        }
        return false;
    }

    public void saveMisc(Exchangeable exchangeable, String miscFile, String text)
            throws IOException
    {
        checkReadOnly();
        try(FileLocker fileLocker = getFileLock(exchangeable);
                LockWrapper lockWrapper = getInternalLock(exchangeable); )
        {
            File edir = getExchangeableDir(exchangeable);
            edir.mkdirs();
            regularProvider.save(edir, miscFile, text);
        }
    }

    public boolean existsMisc(Exchangeable exchangeable, String miscFile)
            throws IOException
    {
        try(FileLocker fileLocker = getFileLock(exchangeable);
                LockWrapper lockWrapper = getInternalLock(exchangeable); )
        {
            File edir = getExchangeableDir(exchangeable);
            return  exists0(edir, miscFile);
        }
    }

    public String loadMisc(Exchangeable exchangeable, String miscFile)
            throws IOException
    {
        try(FileLocker fileLocker = getFileLock(exchangeable);
                LockWrapper lockWrapper = getInternalLock(exchangeable); )
        {
            File edir = getExchangeableDir(exchangeable);
            return load0(edir, new String[]{miscFile});
        }
    }

    public void save(Exchangeable exchangeable, DataInfo dataInfo, LocalDate tradingDay, String text )
            throws IOException
    {
        checkReadOnly();
        try(FileLocker fileLocker = getFileLock(exchangeable);
                LockWrapper lockWrapper = getInternalLock(exchangeable); )
        {
            File edir = getExchangeableDir(exchangeable);
            String[] dataFiles = getDataFileName(dataInfo, tradingDay);
            regularProvider.save(edir, dataFiles[0], text);
        }
    }

    public synchronized LocalDate[] getTradingDays(Exchangeable exchangeable, LocalDate tradingDay, int count)
            throws IOException
    {
        List<LocalDate> tradingDays = new LinkedList<>();
        boolean before = count < 0;
        int absCount = Math.abs(count);
        List<LocalDate> suspensionDays = getSuspensionDays(exchangeable);

        LocalDate currTradingDay = tradingDay;
        while( tradingDays.size()<absCount){
            if ( before ) {
                currTradingDay = MarketDayUtil.prevMarketDay(exchangeable.exchange(), currTradingDay);
            } else {
                currTradingDay = MarketDayUtil.nextMarketDay(exchangeable.exchange(), currTradingDay);
            }
            if ( suspensionDays.contains(currTradingDay)) {
                continue;
            }
            tradingDays.add(currTradingDay);
        }
        Collections.sort(tradingDays);
        return tradingDays.toArray(new LocalDate[tradingDays.size()]);
    }

    public synchronized void save(String subDir, LocalDate tradingDay, DataInfo dataInfo, String text )
            throws IOException
    {
        checkReadOnly();
        File edir = new File(dataDir, subDir);
        String[] dataFiles = getDataFileName(dataInfo, tradingDay);
        regularProvider.save(edir, dataFiles[0], text);
    }

    public synchronized String load(String subDir, DataInfo dataInfo, LocalDate tradingDay)
            throws IOException
    {
        File edir = new File(dataDir, subDir);
        String[] dataFiles = getDataFileName(dataInfo, tradingDay);
        return load0(edir, dataFiles);
    }

    public String load(Exchangeable exchangeable, DataInfo dataInfo, LocalDate tradingDay)
            throws IOException
    {
        try(FileLocker fileLocker = getFileLock(exchangeable);
                LockWrapper lockWrapper = getInternalLock(exchangeable); )
        {
            File edir = getExchangeableDir(exchangeable);
            String[] dataFiles = getDataFileName(dataInfo, tradingDay);
            return load0(edir, dataFiles);
        }
    }

    private boolean exists0(File edir, String dataFile) throws IOException
    {
        return  regularProvider.exists(edir, dataFile)
                || zipProvider.exists(edir, dataFile);
    }

    private String load0(File edir, String[] dataFiles) throws IOException
    {
        for(String dataFile: dataFiles){
            if ( regularProvider.exists(edir, dataFile)){
                return regularProvider.read(edir, dataFile);
            }
            if ( zipProvider.exists(edir, dataFile)){
                return zipProvider.read(edir, dataFile);
            }
        }
        throw new IOException("Data not exists: "+edir+"/"+dataFiles[0]);
    }

    public LinkedList<TradingData> loadAll(Exchangeable exchangeable, DataInfo classfication, LocalDate beginDay, LocalDate endDay) throws IOException
    {
        try(FileLocker fileLocker = getFileLock(exchangeable);
                LockWrapper lockWrapper = getInternalLock(exchangeable); )
        {
            LinkedList<TradingData> result = new LinkedList<>();
            LocalDate tradingDay = beginDay;
            while(tradingDay.compareTo(endDay)<=0){
                try{
                    String text = load(exchangeable, classfication, tradingDay);
                    result.add(new TradingData(tradingDay,text));
                }catch(IOException ioe){}
                tradingDay = MarketDayUtil.nextMarketDay(exchangeable.exchange(), tradingDay);
            }
            return result;
        }
    }

    public List<LocalDate> getSuspensionDays(Exchangeable exchangeable)throws IOException{
        List<LocalDate> result = new LinkedList<>();
        if ( !existsMisc(exchangeable, MISC_SUSPENSION)){
            return result;
        }
        String text = loadMisc(exchangeable, MISC_SUSPENSION);
        CSVDataSet dataSet = CSVUtil.parse(text);
        while(dataSet.next()) {
            result.add(DateUtil.str2localdate(dataSet.get(0)));
        }
        return result;
    }

    public List<LocalDate> addSuspensionDay(Exchangeable exchangeable, LocalDate suspensionDay) throws IOException {
        List<LocalDate> suspensionDays = getSuspensionDays(exchangeable);
        if ( suspensionDays.contains(suspensionDay) ) {
            return suspensionDays;
        }
        suspensionDays.add(suspensionDay);
        Collections.sort(suspensionDays);
        setSuspensionDays(exchangeable, suspensionDays);
        return suspensionDays;
    }

    private void setSuspensionDays(Exchangeable exchangeable, List<LocalDate> suspensionDays) throws IOException {
        CSVWriter writer = new CSVWriter("SuspensionDay");
        for(LocalDate s:suspensionDays){
            writer.append(DateUtil.date2str(s));
        }
        saveMisc(exchangeable, MISC_SUSPENSION, writer.toString());
    }

    public List<LocalDate> setIndexLastNoDataDay(Exchangeable exchangeable, LocalDate suspensionDay) throws IOException {
        if ( exchangeable.getType()!=ExchangeableType.INDEX) {
            throw new RuntimeException("needs index.");
        }
        List<LocalDate> suspensionDays = getSuspensionDays(exchangeable);
        suspensionDays.add(suspensionDay);
        Collections.sort(suspensionDays);
        List<LocalDate> r = new LinkedList<>();
        r.add(suspensionDays.get(suspensionDays.size()-1));
        setSuspensionDays(exchangeable, r);
        return r;
    }

    /**
     * archive all data files from regular file to zip archive
     */
    public void archive(ExchangeableDataArchiveListener listener) throws IOException
    {
        ZipDataProvider zipper = new ZipDataProvider();
        for(File exchangeDir : getDataDir().listFiles()){
            if ( !exchangeDir.isDirectory() ){
                continue;
            }
            Exchange exchange = Exchange.getInstance(exchangeDir.getName());
            if( exchange!=null ){
                for(File edir:exchangeDir.listFiles()){
                    if ( !edir.isDirectory() ){
                        continue;
                    }
                    detectClassification(edir, zipper);
                    archiveExchangeableDir(exchange, listener, edir, zipper);
                }
            }else{
                detectClassification(exchangeDir, zipper);
                archiveSubDir(exchangeDir, listener, zipper);
            }
        }
    }

    private void detectClassification(File edir, ZipDataProvider zipper){
        for(String f:edir.list()){
            if ( !f.endsWith(".zip")){
                continue;
            }
            String[] fparts=f.split("\\.");
            if ( fparts.length==3 ){
                //YYYY.classification.zip
                zipper.setOneFilePerYear(fparts[1], true);
            }else{
                //classification.zip
                zipper.setOneFilePerYear(fparts[0], false);
            }
        }
    }

    private void archiveSubDir(File subDir, ExchangeableDataArchiveListener listener, ZipDataProvider zipper) throws IOException
    {
        String[] files = subDir.list();
        List<String> filesToArchive = new LinkedList<>();
        for(String f:files){
            if (f.endsWith(".csv")){
                filesToArchive.add(f);
            }
        }
        if ( filesToArchive.size()==0 ){
            return;
        }
        listener.onArchiveBegin(subDir);
        int archivedFileCount= groupAndArchiveFiles(zipper, subDir, filesToArchive);
        listener.onArchiveEnd(subDir, archivedFileCount);
    }

    private void archiveExchangeableDir(Exchange exchange, ExchangeableDataArchiveListener listener, File edir, ZipDataProvider zipper) throws IOException
    {
        String[] files = edir.list();
        List<String> filesToArchive = new LinkedList<>();
        for(String f:files){
            if (f.endsWith(".csv")){
                filesToArchive.add(f);
            }
        }
        if ( filesToArchive.size()==0 ){
            return;
        }
        Exchangeable e = Exchangeable.fromString(exchange.name(), edir.getName());
        listener.onArchiveBegin(e, edir);
        int archivedFileCount= groupAndArchiveFiles(zipper, edir, filesToArchive);
        listener.onArchiveEnd(e, archivedFileCount);
    }

    private int groupAndArchiveFiles(ZipDataProvider zipper, File dir, List<String> filesToArchive) throws IOException
    {
        Map<String, List> groupedFiles = new HashMap<>();
        for(String f:filesToArchive){
            String classification = zipper.detectData(f);
            if( classification==null ){
                throw new IOException("Unknown classification: "+f);
            }
            if ( !groupedFiles.containsKey(classification) ){
                List list = new LinkedList();list.add(f);
                groupedFiles.put(classification, list);
            }else{
                groupedFiles.get(classification).add(f);
            }
        }
        int archivedFileCount=0;
        for(List list:groupedFiles.values()){
            zipper.saveAll(dir, (String[])list.toArray(new String[list.size()]), regularProvider);
            for(Object f:list){
                (new File(dir,f.toString())).delete();
            }
            archivedFileCount += list.size();
        }
        return archivedFileCount;
    }

    private FileLocker getFileLock(Exchangeable exchangeable) throws IOException
    {
        if ( readOnly ){
            return new FileLocker((File)null);
        }
        File fileToLock = new File(dataDir,exchangeable.exchange().name()+"/"+exchangeable.id()+"/_filelock");
        if ( !fileToLock.exists() ){
            fileToLock.getParentFile().mkdirs();
            FileUtil.copy(new ByteArrayInputStream("FileLock".getBytes()), fileToLock);
        }
        return new FileLocker(fileToLock);
    }

    private LockWrapper getInternalLock(Exchangeable exchangeable)
    {
        workingLock.lock();
        try{
            String uniqueId = exchangeable.toString();
            Lock lock = workingLocks.get(uniqueId);
            if ( lock==null ){
                lock = new ReentrantLock();
                workingLocks.put(uniqueId, lock);
            }
            return new LockWrapper(lock);
        }finally{
            workingLock.unlock();
        }
    }

    private File getExchangeDir(Exchange e){
        return new File(dataDir, e.name());
    }

    private File getExchangeableDir(Exchangeable e)
    {
        File exchangeDir = getExchangeDir(e.exchange());
        File f1 = new File(exchangeDir, e.id());
        if ( f1.exists() ){
            return f1;
        }
        File f2 = new File(exchangeDir, e.id().toUpperCase());
        if ( f2.exists() ){
            return f2;
        }
        File f3 = new File(exchangeDir, e.id().toLowerCase());
        if ( f3.exists() ){
            return f3;
        }
        //throw new RuntimeException("Exchangeable "+e+" has no data dir: "+f1);
        return f1;
    }

    private String[] getDataFileName(DataInfo dataInfo, LocalDate tradingDay){
        String[] result = null;
        if ( dataInfo.altName()!=null ){
            result = new String[2];
        }else{
            result = new String[1];
        }
        String pathPrefix = "";
    	if ( ( dataInfo.priceLevel()==null ||
    	        dataInfo.priceLevel().ordinal()<PriceLevel.DAY.ordinal() )
    	        && tradingDay!=null )
    	{
            pathPrefix = DateUtil.date2str(tradingDay)+".";
    	}
        result[0] = pathPrefix+dataInfo.name()+EXT_NAME;
        if ( dataInfo.altName()!=null ){
            result[1] = pathPrefix+dataInfo.altName()+EXT_NAME;
        }
        return result;
    }

    private void checkReadOnly() throws IOException
    {
        if ( readOnly ){
            throw new IOException("Exchangeable data dir "+dataDir+" read only");
        }
    }

}

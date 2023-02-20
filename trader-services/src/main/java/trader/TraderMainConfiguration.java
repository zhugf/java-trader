package trader;

import java.io.File;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

import org.eclipse.jetty.util.thread.ExecutorThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.embedded.jetty.JettyServletWebServerFactory;
import org.springframework.boot.web.server.ErrorPage;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.GsonHttpMessageConverter;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.google.gson.GsonBuilder;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import trader.common.config.ConfigUtil;
import trader.common.util.EncryptionUtil;
import trader.common.util.TraderHomeUtil;

@EnableAutoConfiguration
@Configuration
@EnableScheduling
@EnableAsync
public class TraderMainConfiguration implements WebMvcConfigurer, SchedulingConfigurer, AsyncConfigurer, AsyncUncaughtExceptionHandler {
    private final static Logger logger = LoggerFactory.getLogger(TraderMainConfiguration.class);

    private ScheduledThreadPoolExecutor taskScheduler;
    private ThreadPoolExecutor asyncExecutor;

    public TraderMainConfiguration() {
        createThreadPools();
    }

    @Bean
    public WebServerFactoryCustomizer webServerFactoryCustomizer(){
        return new WebServerFactoryCustomizer<ConfigurableServletWebServerFactory>() {
            @Override
            public void customize(ConfigurableServletWebServerFactory factory) {
                int port = ConfigUtil.getInt("/BasisService/web.httpPort", 10080);
                factory.setPort(port);
                factory.setContextPath("");
                factory.addErrorPages(new ErrorPage(HttpStatus.NOT_FOUND, "/notfound.html"));
                if ( factory instanceof JettyServletWebServerFactory) {
                    JettyServletWebServerFactory factory0 = (JettyServletWebServerFactory)factory;
                    factory0.setSelectors(1);
                    factory0.setAcceptors(1);
                    factory0.setThreadPool(new ExecutorThreadPool(executorService()));
                }
            }
        };
    }

    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.add(new StringHttpMessageConverter());
        converters.add(new FormHttpMessageConverter());
        GsonHttpMessageConverter c = new GsonHttpMessageConverter();
        c.setGson(new GsonBuilder().disableHtmlEscaping().create());
        converters.add(c);
        converters.add(new ResourceHttpMessageConverter());
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return this;
    }

    @Override
    public void handleUncaughtException(Throwable ex, Method method, Object... params) {
        logger.error("Async invocation on "+method+" "+Arrays.asList(params)+"failed: "+ex.toString(), ex);
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setScheduler(taskScheduler);
    }

    @Primary
    @Bean
    public java.util.concurrent.ThreadPoolExecutor executorService(){
        return asyncExecutor;
    }

    @Bean
    public java.util.concurrent.ScheduledExecutorService scheduledExecutorService(){
        return taskScheduler;
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory() throws Exception
    {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(h2DataSource());
        em.setPackagesToScan(new String[] {
                "trader"
        });
        //em.setLoadTimeWeaver(new org.springframework.instrument.classloading.ReflectiveLoadTimeWeaver());

        JpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        em.setJpaVendorAdapter(vendorAdapter);

        Properties properties = new Properties();
        properties.setProperty("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
        properties.setProperty("hibernate.default_schema", "trader");
        properties.setProperty("hibernate.connection.autoReconnect", "true");
        properties.setProperty("hibernate.hbm2ddl.auto", "create");
        String value = ConfigUtil.getString("BasisService.hibernate.showSql", "false");
        properties.setProperty("hibernate.show_sql", value);

        em.setJpaProperties(properties);
        return em;
    }

    @Bean(name="transactionManager")
    public PlatformTransactionManager getTransactionManager(EntityManagerFactory emf){
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(emf);
        return transactionManager;
    }

    @Bean(name="dataSource")
    public DataSource h2DataSource() throws Exception
    {
        String addr = ConfigUtil.getString("BasisService.h2db.addr");
        int tcpPort = ConfigUtil.getInt("BasisService.h2db.tcpPort", 0);
        boolean autoServer = ConfigUtil.getBoolean("BasisService.h2db.autoServer", false);
        File h2dbPath = new File(TraderHomeUtil.getTraderHome(), "data/h2db");
        String url = "jdbc:h2:"+h2dbPath.getAbsolutePath()+";AUTO_RECONNECT=TRUE";
        if ( autoServer) {
            url += ";AUTO_SERVER=TRUE;AUTO_SERVER_PORT="+tcpPort;
        }
        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName("h2db");
        cfg.setDriverClassName(org.h2.Driver.class.getName());
        cfg.setJdbcUrl(url);
        cfg.setMinimumIdle(1);
        cfg.setMaximumPoolSize(3);
        cfg.setIdleTimeout(120*1000);
        cfg.setConnectionTestQuery("SELECT 1");
        cfg.setConnectionTimeout(5*1000);
        cfg.setUsername("sa");
        HikariDataSource ds = new HikariDataSource(cfg);

        try(Connection conn=ds.getConnection();){
            conn.createStatement().execute("CREATE SCHEMA IF NOT EXISTS trader");
        }
        return ds;
    }

    public DataSource mysqlDataSource() throws Exception
    {
        String url = ConfigUtil.getString("BasisService.mysql.url");
        String username = ConfigUtil.getString("BasisService.mysql.username");
        String password = ConfigUtil.getString("BasisService.mysql.password");
        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName("mysql");
        cfg.setDriverClassName(com.mysql.cj.jdbc.Driver.class.getName());
        cfg.setJdbcUrl(url);
        cfg.setMinimumIdle(1);
        cfg.setMaximumPoolSize(3);
        cfg.setIdleTimeout(120*1000);
        cfg.setConnectionTestQuery("SELECT 1");
        cfg.setConnectionTimeout(5*1000);
        cfg.setUsername(ConfigUtil.getString(username));
        password = EncryptionUtil.decryptPassword(password);
        cfg.setPassword(password);
        HikariDataSource ds = new HikariDataSource(cfg);
        return ds;
    }

    private void createThreadPools()
    {
        taskScheduler = new ScheduledThreadPoolExecutor(2, new DefaultThreadFactory("TaskScheduler"));
        asyncExecutor = new ThreadPoolExecutor(10, 1000, 5 ,TimeUnit.MINUTES, new SynchronousQueue<Runnable>(), new DefaultThreadFactory("async"));
        asyncExecutor.allowCoreThreadTimeOut(false);
    }

}

class DefaultThreadFactory implements ThreadFactory {

    public static class PoolThreadGroup extends ThreadGroup{

        private static final Logger logger = LoggerFactory.getLogger(PoolThreadGroup.class);

        public PoolThreadGroup(String name) {
            super(name);
        }

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            logger.error("Thread "+t+" uncaught exception: "+e, e);
        }

    }

    private final ThreadGroup group;
    private final AtomicInteger threadNumber = new AtomicInteger(1);
    private int priority;
    private String poolName;

    public DefaultThreadFactory(String poolName){
        this(poolName, Thread.NORM_PRIORITY);
    }

    public DefaultThreadFactory(String poolName, int priority){
        this.priority = priority;
        this.poolName = poolName;
        SecurityManager s = System.getSecurityManager();
        //group = (s != null) ? s.getThreadGroup() :
        //                      Thread.currentThread().getThreadGroup();
        group = new PoolThreadGroup(poolName);
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(group, r, poolName + "-" + threadNumber.getAndIncrement(), 0);
        t.setDaemon(true);
        t.setPriority(priority);
        return t;
    }
}

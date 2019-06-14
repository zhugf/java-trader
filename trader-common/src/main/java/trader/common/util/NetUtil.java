package trader.common.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.ssl.TrustStrategy;

public class NetUtil {
    public static final Charset utf8Charset = Charset.forName("UTF-8");
    public static final Charset gbkCharset = Charset.forName("GBK");

    public static int connectTimeout = 30;
    public static int readTimeout = 60;

    public static int actionRepeatTime = 5;
    public static int actionIdleTime = 10;

    private static PoolingHttpClientConnectionManager cm = null;
    private static ThreadLocal<HttpClientContext> currContet = new ThreadLocal<>();
    private static ThreadLocal<Integer> lastStatus = new ThreadLocal<>();
    public static enum HttpMethod{ GET,PUT,POST }

    static {
        try {
            SSLContextBuilder builder = SSLContexts.custom();
            builder.loadTrustMaterial(null, new TrustStrategy() {
                @Override
                public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    return true;
                }
            });
            SSLContext sslContext = builder.build();

            SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslContext, new javax.net.ssl.HostnameVerifier() {
                @Override
                public boolean verify(String s, SSLSession sslSession) {
                    return true;
                }
            });

            Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                    .register("http", org.apache.http.conn.socket.PlainConnectionSocketFactory.INSTANCE)
                    .register("https", sslsf).build();

            cm = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
            cm.setMaxTotal(200);
            cm.setDefaultMaxPerRoute(20);
        }catch(Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static String readHttpAsText(String httpUrl, Charset charset)
            throws IOException
    {
        return readHttpAsText(httpUrl, HttpMethod.GET, null, charset, null);
    }

    public static String readHttpAsText(String httpUrl, HttpMethod method, String body, Charset charset)
            throws IOException
    {
        return readHttpAsText(httpUrl, method, body, charset, null);
    }

    public static String readHttpAsText(String httpUrl, HttpMethod method, String body, Charset charset, Map<String,String> props)
            throws IOException
    {
        ContentType contentType = ContentType.APPLICATION_JSON;
        if ( props!=null && props.get("Content-Type")!=null) {
            contentType = ContentType.parse(props.remove("Content-Type"));
        }
        if ( charset==null ){
            charset = utf8Charset;
        }
        HttpClientContext context = currContet.get();
        if (context==null) {
            context = HttpClientContext.create();
            currContet.set(context);
        }

        HttpRequestBase req = null;
        switch(method) {
        case GET:
            req = new HttpGet(httpUrl);
            break;
        case POST:
            HttpPost post = new HttpPost(httpUrl);
            StringEntity postEntity = new StringEntity(body, contentType);
            post.setEntity(postEntity);
            req = post;
            break;
        case PUT:
            HttpPut put = new HttpPut(httpUrl);
            StringEntity putEntity = new StringEntity(body, contentType);
            put.setEntity(putEntity);
            req = put;
            break;
        }
        if ( props!=null ) {
            for(String key:props.keySet()) {
                req.setHeader(key, props.get(key));
            }
        }
        try(CloseableHttpClient httpClient = HttpClients.custom().setConnectionManager(cm).setConnectionManagerShared(true).build();
            CloseableHttpResponse response = httpClient.execute(req, context);)
        {
            lastStatus.set(response.getStatusLine().getStatusCode());
            HttpEntity entity = response.getEntity();

            ByteArrayOutputStream os = new ByteArrayOutputStream(10240);
            if (entity != null) {
                int totalLen=0;
                InputStream is = entity.getContent();
                byte[] data = new byte[1024];
                int len=0;
                while ( (len=is.read(data))>0 ){
                    os.write(data, 0, len);
                    totalLen+=len;
                }
                os.flush();
            }
            if ( os.size()>0 ) {
                return new String(os.toByteArray(), charset);
            }else {
                return null;
            }
        }
    }

    public static int getLastHttpStatusCode() {
        Integer s = lastStatus.get();
        if ( s==null ) {
           return 0;
        }
        return s;
    }

    public static <T extends Object> T doAction(IOAction<T> action) throws IOException
    {
        IOException ioe = null;;
        for(int i=0;i<actionRepeatTime;i++){
            if ( i>0 ){
                try {
                    Thread.sleep(actionIdleTime*1000);
                } catch (InterruptedException e1) {}
            }
            try{
                T r = action.doAction();
                return r;
            }catch(IOException e){
                ioe = e;
            }
        }
        throw ioe;
    }


}

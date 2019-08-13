package com.vzoom.cert.utils;

import com.springcryptoutils.core.certificate.CertificateException;
import org.apache.http.*;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.*;
import org.apache.http.util.EntityUtils;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.HttpCookie;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author zcp
 */
@SuppressWarnings("deprecation")
public class HttpClientUtil {
	// 默认字符集
    private static final String ENCODING = "UTF-8";
	public static final String POST_METHED = "POST";
	public static final String GET_METHED = "GET";
    private static final int TIMEOUT = 120000;
	private static HttpParams httpParams;
	private static HttpParams httpParamsQs;
	private static HttpClient httpClient = null;
	static{
		try {
			httpParamsQs = new BasicHttpParams();
			httpParams = new BasicHttpParams();
			//设置Protocol基本参数
			HttpProtocolParams.setVersion(httpParams, HttpVersion.HTTP_1_1);  //设置http协议版本
			HttpProtocolParams.setContentCharset(httpParams, ENCODING);  //设置参数传输内容 的编码方式
			HttpProtocolParams.setUseExpectContinue(httpParams, true);//持续握手,先发送部分请求（如：只发送请求头）进行试探

			// 设置连接超时和 Socket 超时，以及 Socket 缓存大小
			HttpConnectionParams.setConnectionTimeout(httpParams, TIMEOUT);//设置连接超时,以毫秒为单位:与服务器建立连接的超时时间(即与socket连接的超时时间)
			HttpConnectionParams.setSoTimeout(httpParams, TIMEOUT);//定义了读取或者接收Socket超时，即从服务器获取响应数据需要等待的时间
			HttpConnectionParams.setSocketBufferSize(httpParams, 8192);//设置 Socket缓存大小
			HttpConnectionParams.setStaleCheckingEnabled(httpParams, true);//设置是否启用旧连接检查，默认是开启的。可以提高一点点性能效率,这个检查大概会花费15-30毫秒
			//连接管理器:从连接池中取连接的超时参数设置
			ConnManagerParams.setTimeout(httpParams, 1000);//让连接在超过时间后自动失效，释放占用资源:,ConnectionPoolTimeout
			ConnManagerParams.setMaxConnectionsPerRoute(httpParams, new ConnPerRouteBean(500));//设置每个最大的路由连接数默认为2
			ConnManagerParams.setMaxTotalConnections(httpParams,1000);//设置最大的连接数,默认是20个

			//忽略证书
			SSLContext ctx = SSLContext.getInstance("TLS");
			X509TrustManager tm = new X509TrustManager() {
				public X509Certificate[] getAcceptedIssuers() {
					return null;
				}
				public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {}
				public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {}
			};
			ctx.init(null, new TrustManager[] { tm }, null);
			SSLSocketFactory ssf = new SSLSocketFactory(ctx, SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

			//设置HttpClient支持HTTp和HTTPS两种模式
			SchemeRegistry schReg = new SchemeRegistry();
			schReg.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
			schReg.register(new Scheme("https",443,ssf));

			//多线程安全管理的配置:使用线程安全的连接管理来创建HttpClient
			ClientConnectionManager manager  = new ThreadSafeClientConnManager(httpParams, schReg);
			httpClient = new DefaultHttpClient(manager,httpParams);
			httpClient.getParams().setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT,TIMEOUT);//连接时间
			httpClient.getParams().setParameter(CoreConnectionPNames.SO_TIMEOUT,TIMEOUT);//数据传输时间
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
     * 无参数Get请求方式
     * @param url  请求地址
     * @param headers  请求头
     * @return  请求返回报文
     * @throws Exception 请求异常
     */
    public static String httpGet(String url,Map<String, String> headers){  
        String result="";
        HttpResponse response = null;
        HttpGet request = null;
        try {
			request=new HttpGet(url);
			if (headers != null) {
			    Header[] allHeader = new BasicHeader[headers.size()];
			    int i = 0;
			    for (Map.Entry<String, String> entry: headers.entrySet()){
			        allHeader[i] = new BasicHeader(entry.getKey(), entry.getValue());
			        i++;
			    }
			    request.setHeaders(allHeader);
			}
			//httpClient=new DefaultHttpClient(httpParams);
			//接受客户端发回的响应
			response= httpClient.execute(request);
			int statusCode=response.getStatusLine().getStatusCode();
			if(statusCode==HttpStatus.SC_OK){
				result = EntityUtils.toString(response.getEntity(),ENCODING);
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally{
			//释放当前连接资源：当检测到某个步骤出现异常时，使用abort()方法，中止当前的处理
			if(null!=request){
				//request.abort();
				request.releaseConnection();
				request = null;
			}
		}
        return result;
    }
    /**
     * 有参Post请求,参数JSON格式字符串
	 * @param url 请求资源地址
	 * @param jsonStr 请求参数json字符串
	 * @return 成功(200)返回【正确字符串信息】、非200 返回 【"0"+StatusCode】、请求异常返回 【"-1"+e.getMessage()】
     */
    public static String doPostJson(String url,Map<String, String> headers,String jsonStr){
    	HttpResponse response = null;
    	HttpPost httpPost = null;
        try {
        	HttpConnectionParams.setSoTimeout(httpParamsQs, 60*1000);//定义了读取或者接收Socket超时，即从服务器获取响应数据需要等待的时间
			httpPost = new HttpPost(url);
			httpPost.setParams(httpParamsQs);
			httpPost.setHeader("Content-type", "application/json");
			if (headers != null) {
			    Header[] allHeader = new BasicHeader[headers.size()];
			    int i = 0;
			    for (Map.Entry<String, String> entry: headers.entrySet()){
			        allHeader[i] = new BasicHeader(entry.getKey(), entry.getValue());
			        i++;
			    }
			    httpPost.setHeaders(allHeader);
			}
			StringEntity requestEntity = new StringEntity(jsonStr,"utf-8");
			requestEntity.setContentEncoding("UTF-8");    	        
			httpPost.setEntity(requestEntity);
			response = httpClient.execute(httpPost);
			if(response.getStatusLine().getStatusCode()==200){
				return EntityUtils.toString(response.getEntity(),"UTF-8");
			}else{
				return "0"+response.getStatusLine().getStatusCode()+"";
			}
		} catch (Exception e) {
			e.printStackTrace();
			return "-1"+e.getMessage();
		} finally{
			if(null!=httpPost){
				httpPost.releaseConnection();
				httpPost = null;
			}
		}
    }
    /**
     * 有参Post请求,参数JSON格式字符串
	 * @param url 请求资源地址
	 * @param params 请求参数集合
	 * @return 成功(200)返回【正确字符串信息】、非200 返回 【"0"+StatusCode】、请求异常返回 【"-1"+e.getMessage()】
     */
    public static String doPostJson(String url,Map<String, String> headers,Map<String, String> params,Map<String, String> respHeaders,List<HttpCookie> respCookes){
    	HttpResponse response = null;
    	HttpPost httpPost = null;
    	int index = -1;
        try {
        	HttpConnectionParams.setSoTimeout(httpParamsQs, 60*1000);//定义了读取或者接收Socket超时，即从服务器获取响应数据需要等待的时间
			httpPost = new HttpPost(url);
			httpPost.setParams(httpParamsQs);
			if (null != headers) {//设置请求头
			    Header[] allHeader = new BasicHeader[headers.size()];
			    int i = 0;
			    for (Map.Entry<String, String> entry: headers.entrySet()){
			        allHeader[i] = new BasicHeader(entry.getKey(), entry.getValue());
			        i++;
			    }
			    httpPost.setHeaders(allHeader);
			}
			if(null != params){//设置请求参数
                List<NameValuePair> nvps = new ArrayList<NameValuePair>();
                for (Map.Entry<String, String> entry : params.entrySet()) {
                    nvps.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
                }
                httpPost.setEntity(new UrlEncodedFormEntity(nvps));
            }
			//httpClient=wrapClient(httpClient);
			response = httpClient.execute(httpPost);
			Header[] allHeaders = response.getAllHeaders();
			for (Header header : allHeaders) {
				System.out.println(header.getName()+"---"+header.getValue());
				if("Set-Cookie".equals(header.getName())){
					index = header.getValue().indexOf("=");
					respCookes.add(new HttpCookie(header.getValue().substring(0,index),header.getValue().substring(index+1)));
				}else{
					respHeaders.put(header.getName(), header.getValue());
				}
			}
			if(response.getStatusLine().getStatusCode()==200){
				return EntityUtils.toString(response.getEntity(),"UTF-8");
			}else{
				return "0"+response.getStatusLine().getStatusCode()+"";
			}
		} catch (Exception e) {
			e.printStackTrace();
			return "-1"+e.getMessage();
		} finally{
			if(null!=httpPost){
				httpPost.releaseConnection();
				httpPost = null;
			}
		}
    }
    /**
     * 有参Post请求,参数JSON格式字符串
	 * @param url 请求资源地址
	 * @param jsonStr 请求参数json字符串
	 * @param timeOut 超时时间单位s
	 * @return 成功(200)返回【正确字符串信息】、非200 返回 【"0"+StatusCode】、请求异常返回 【"-1"+e.getMessage()】
     */
    public static String doPostJson(String url,Map<String, String> headers,String jsonStr,int timeOut){
    	HttpResponse response;
    	HttpPost httpPost = null;
        try {
			httpPost = new HttpPost(url);
			httpPost.getParams().setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, timeOut*1000);
			httpPost.getParams().setParameter(CoreConnectionPNames.SO_TIMEOUT, timeOut*1000);
			httpPost.setHeader("Content-type", "application/json");
			if (headers != null) {
			    Header[] allHeader = new BasicHeader[headers.size()];
			    int i = 0;
			    for (Map.Entry<String, String> entry: headers.entrySet()){
			        allHeader[i] = new BasicHeader(entry.getKey(), entry.getValue());
			        i++;
			    }
			    httpPost.setHeaders(allHeader);
			}
			StringEntity requestEntity = new StringEntity(jsonStr,"utf-8");
			requestEntity.setContentEncoding("UTF-8");    	        
			httpPost.setEntity(requestEntity);
			response = httpClient.execute(httpPost);
			if(response.getStatusLine().getStatusCode()==200){
				return EntityUtils.toString(response.getEntity(),"UTF-8");
			}else{
				return "0"+response.getStatusLine().getStatusCode()+"";
			}
		} catch (Exception e) {
			e.printStackTrace();
			return "-1"+e.getMessage();
		} finally{
			if(null!=httpPost){
				httpPost.releaseConnection();
				httpPost = null;
			}
		}
    }
    /**
     * 无参数Get请求方式
     * @param url  请求地址
     * @param headers  请求头
     * @return 成功(200)返回【正确字节数组】、非200 返回 【"0"+StatusCode字节数组】、请求异常返回 【"-1"+e.getMessage()字节数组】
     */
    public static byte[] httpGetByte(String url,Map<String, String> headers){  
        HttpResponse response = null;
        HttpGet request = null;
        try {
			request=new HttpGet(url);
			if (headers != null) {
			    Header[] allHeader = new BasicHeader[headers.size()];
			    int i = 0;
			    for (Map.Entry<String, String> entry: headers.entrySet()){
			        allHeader[i] = new BasicHeader(entry.getKey(), entry.getValue());
			        i++;
			    }
			    request.setHeaders(allHeader);
			}
			//httpClient=new DefaultHttpClient(httpParams);
			//接受客户端发回的响应
			response=httpClient.execute(request);
			int statusCode=response.getStatusLine().getStatusCode();
			if(statusCode==HttpStatus.SC_OK){
				return EntityUtils.toByteArray(response.getEntity());
			}else{
				return ("0"+response.getStatusLine().getStatusCode()).getBytes(ENCODING);
			}
		} catch (Exception e) {
			e.printStackTrace();
			return ("-1"+e.getMessage()).getBytes();
		} finally{
			//释放当前连接资源：当检测到某个步骤出现异常时，使用abort()方法,中止当前的处理
			if(null!=request){
				//request.abort();
				request.releaseConnection();
				request = null;
			}
		}
    }

	/**
	 * 每次发起请求,创建一个HttpClient对象
	 * @param methed  请求方式POST_METHED 、GET_METHED
	 * @param url   请求资源地址
	 * @param headers 请求头信息集合
	 * @param params  请求参数,只有指定POST请求才生效
	 * @param respHeaders  响应头信息
	 * @param respCookes   响应cookies
	 * @return 成功(200)返回【正确字符串信息】、非200 返回 【"0"+StatusCode】、请求异常返回 【"-1"+e.getMessage()】
	 */
	public static String doRequest(String methed,String url,Map<String, String> headers,Map<String, String> params,Map<String, String> respHeaders,List<HttpCookie> respCookes){
		HttpClient hc=null;
		HttpResponse response=null;
		HttpPost post = null;
		HttpGet get=null;
		int index = -1;
		try {
			HttpConnectionParams.setSoTimeout(httpParamsQs, 60*1000);//定义了读取或者接收Socket超时，即从服务器获取响应数据需要等待的时间
			if(POST_METHED.equals(methed)){
				post = new HttpPost(url);
				post.setParams(httpParamsQs);
			}else{
				get = new HttpGet(url);
				get.setParams(httpParamsQs);
			}
			if (null != headers) {//设置请求头
				Header[] allHeader = new BasicHeader[headers.size()];
				int i = 0;
				for (Map.Entry<String, String> entry: headers.entrySet()){
					allHeader[i] = new BasicHeader(entry.getKey(), entry.getValue());
					i++;
				}
				if(null!=post)post.setHeaders(allHeader);
				if(null!=get)get.setHeaders(allHeader);
			}
			if(null != params&&null!=post){//POST设置请求参数
				List<NameValuePair> nvps = new ArrayList<NameValuePair>();
				for (Map.Entry<String, String> entry : params.entrySet()) {
					nvps.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
				}
				post.setEntity(new UrlEncodedFormEntity(nvps));
			}
			hc=getDefaultClient();
			if(null!=post) response = hc.execute(post);
			if(null!=get) response = hc.execute(get);
			Header[] allHeaders = response.getAllHeaders();
			for (Header header : allHeaders) {
				System.out.println(header.getName()+"---"+header.getValue());
				if("SET-COOKIE".equals(header.getName().toUpperCase())){
					index = header.getValue().indexOf("=");
					respCookes.add(new HttpCookie(header.getValue().substring(0,index),header.getValue().substring(index+1)));
				}else{
					respHeaders.put(header.getName(), header.getValue());
				}
			}
			if(response.getStatusLine().getStatusCode()==200){
				return EntityUtils.toString(response.getEntity(),"UTF-8");
			}else{
				return "0"+response.getStatusLine().getStatusCode()+"";
			}
		} catch (Exception e) {
			e.printStackTrace();
			return "-1"+e.getMessage();
		} finally{
			if(null!=post) post.releaseConnection();
			if(null!=get) get.releaseConnection();
			if(null!=hc) hc.getConnectionManager().shutdown();
			post=null;get=null;hc=null;
		}
	}
	/**
	 * GET请求
	 * @param url 请求资源地址
	 * @return 成功(200)返回【正确字符串信息】、非200 返回 【"0"+StatusCode】、请求异常返回 【"-1"+e.getMessage()】
	 */
	public static String doGet(String url,Map<String, String> headers,Map<String, String> respHeaders,List<HttpCookie> respCookes){
		HttpResponse response = null;
		HttpGet get = null;
		int index = -1;
		try {
			HttpConnectionParams.setSoTimeout(httpParamsQs, 60*1000);//定义了读取或者接收Socket超时，即从服务器获取响应数据需要等待的时间
			get = new HttpGet(url);
			get.setParams(httpParamsQs);
			if (null != headers) {//设置请求头
				Header[] allHeader = new BasicHeader[headers.size()];
				int i = 0;
				for (Map.Entry<String, String> entry: headers.entrySet()){
					allHeader[i] = new BasicHeader(entry.getKey(), entry.getValue());
					i++;
				}
				get.setHeaders(allHeader);
			}
			response = httpClient.execute(get);
			Header[] allHeaders = response.getAllHeaders();
			for (Header header : allHeaders) {
				System.out.println(header.getName()+"---"+header.getValue());
				if("Set-Cookie".equals(header.getName())){
					index = header.getValue().indexOf("=");
					respCookes.add(new HttpCookie(header.getValue().substring(0,index),header.getValue().substring(index+1)));
				}else{
					respHeaders.put(header.getName(), header.getValue());
				}
			}
			if(response.getStatusLine().getStatusCode()==200){
				return EntityUtils.toString(response.getEntity(),"UTF-8");
			}else{
				return "0"+response.getStatusLine().getStatusCode()+"";
			}
		} catch (Exception e) {
			e.printStackTrace();
			return "-1"+e.getMessage();
		} finally{
			if(null!=get){
				get.releaseConnection();
				get = null;
			}
		}
	}
    /**
     * 避免HttpClient的”SSLPeerUnverifiedException: peer not authenticated”异常
     * 不用导入SSL证书
     * @author shipengzhi(shipengzhi@sogou-inc.com)
     *
     */
	public static HttpClient getDefaultClient() {
		try {
			SSLContext ctx = SSLContext.getInstance("TLS");
			X509TrustManager tm = new X509TrustManager() {
				public X509Certificate[] getAcceptedIssuers() {
					return null;
				}
				public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {}
				public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {}
			};
			ctx.init(null, new TrustManager[] { tm }, null);
			SSLSocketFactory ssf = new SSLSocketFactory(ctx, SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
			SchemeRegistry registry = new SchemeRegistry();
			registry.register(new Scheme("https", 443, ssf));
			registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
			ThreadSafeClientConnManager mgr = new ThreadSafeClientConnManager(httpParams,registry);
			return new DefaultHttpClient(mgr);//特别注意
		} catch (Exception ex) {
			ex.printStackTrace();
			return null;
		}
	}
}

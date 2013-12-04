/* Copyright 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mobiperf;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.HttpVersion;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.Context;
import android.net.TrafficStats;

import com.mobiperf.measurements.RRCTask;
import com.mobiperf.util.MeasurementJsonConvertor;
import com.mobiperf.util.PhoneUtils;

/**
 * Handles checkins with the SpeedometerApp server.
 */
public class Checkin {
	public static int isBusy = 0;
	private static final int POST_TIMEOUT_MILLISEC = 20 * 1000;
	private Context context;
	private Date lastCheckin;
	private volatile Cookie authCookie = null;
	private AccountSelector accountSelector = null;
	PhoneUtils phoneUtils;
	private Thread contextThread;
	private Vector<MeasurementResult> contextResult;
	private Object contextResultLock = new Object();
	/**
	 * This tread is used for collecting context information in a time interval.
	 * It collect mobile bytes/packets send/receive during a time interval. It
	 * will adjust the interval length according to how many tasks is currently
	 * running.
	 */
	Thread runnable = new Thread() {
		public static final String TYPE = "context";
		public MeasurementResult result;
		protected ContextMeasurementDesc measurementDesc;

		public void run() {
			long prevSend = 0;
			long prevRecv = 0;
			long sendBytes = 0;
			long recvBytes = 0;
			long intervalSend = 0;
			long intervalRecv = 0;

			long prevPktSend = 0;
			long prevPktRecv = 0;
			long sendPkt = 0;
			long recvPkt = 0;
			long intervalPktSend = 0;
			long intervalPktRecv = 0;
			int interval = 5000;
			while (true) {

				sendBytes = TrafficStats.getMobileTxBytes();
				recvBytes = TrafficStats.getMobileRxBytes();
				sendPkt = TrafficStats.getMobileTxPackets();
				recvPkt = TrafficStats.getMobileRxPackets();
				if (prevSend > 0 || prevRecv > 0) {
					intervalSend = sendBytes - prevSend;
					intervalRecv = recvBytes - prevRecv;
				}
				if (prevPktSend > 0 || prevPktRecv > 0) {
					intervalPktSend = sendPkt - prevPktSend;
					intervalPktRecv = recvPkt - prevPktRecv;
				}
				prevSend = sendBytes;
				prevRecv = recvBytes;
				prevPktSend = sendPkt;
				prevPktRecv = recvPkt;
				// Add to result

				PhoneUtils phoneUtils = PhoneUtils.getPhoneUtils();
				Map<String, String> params = new HashMap<String, String>();

				measurementDesc = new ContextMeasurementDesc("context", null,
						Calendar.getInstance().getTime(), null,
						Config.DEFAULT_USER_MEASUREMENT_INTERVAL_SEC,
						Config.DEFAULT_USER_MEASUREMENT_COUNT,
						MeasurementTask.USER_PRIORITY, params);

				MeasurementResult result = new MeasurementResult(
						phoneUtils.getDeviceInfo().deviceId, null, "context",
						System.currentTimeMillis() * 1000, true,
						measurementDesc);
				result.addResult("rssi", phoneUtils.getCurrentRssi());
				result.addResult("incrementMobileBytesSend", intervalSend);
				result.addResult("incrementMobileBytesRecv", intervalRecv);
				result.addResult("incrementMobilePktSend", intervalPktSend);
				result.addResult("incrementMobilePktRecv", intervalPktRecv);
				result.addResult("contextMeasurementIntervel", interval);
				//System.out.println("contextMeasure a result="+MeasurementJsonConvertor.encodeToJson(result));
				// System.out.println("After insertion size="+contextResult.size());
				synchronized(contextResultLock) {
			                contextResult.add(result);				  
				}
				//ts4=System.currentTimeMillis();
				//System.out.println("xxxTotalTime"+(ts4-ts3));
				//System.out.println("contextMeasure result="+contextResult.toString());
				if(isBusy==0){
					//System.out.println("isBusy==0");
				    interval=5000;				
				try {
					Thread.sleep(interval);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				}
				else{
					//System.out.println("isBusy==1");
				  interval=500;
					try {
						Thread.sleep(interval);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}
	};

  public Checkin(Context context) {
    phoneUtils = PhoneUtils.getPhoneUtils();
    this.context = context;
    synchronized(contextResultLock) {
      contextResult = new Vector<MeasurementResult>();      
    }
    if(contextThread == null){
    	contextThread=new Thread(runnable);
    	contextThread.start();
    }
  }

  /** Shuts down the checkin thread */
  public void shutDown() {
    if (this.accountSelector != null) {
      this.accountSelector.shutDown();
    }
  }
  
  /** Return a fake authentication cookie for a test server instance */
  private Cookie getFakeAuthCookie() {
    BasicClientCookie cookie = new BasicClientCookie(
        "dev_appserver_login",
        "test@nobody.com:False:185804764220139124118");
    cookie.setDomain(".google.com");
    cookie.setVersion(1);
    cookie.setPath("/");
    cookie.setSecure(false);
    return cookie;
  }
  
  public Date lastCheckinTime() {
    return this.lastCheckin;
  }
  
  public List<MeasurementTask> checkin() throws IOException {
    Logger.i("Checkin.checkin() called");
    boolean checkinSuccess = false;
    try {
      JSONObject status = new JSONObject();
      DeviceInfo info = phoneUtils.getDeviceInfo();
      // TODO(Wenjie): There is duplicated info here, such as device ID. 
      status.put("id", info.deviceId);
      status.put("manufacturer", info.manufacturer);
      status.put("model", info.model);
      status.put("os", info.os);
      status.put("device_properties", 
          MeasurementJsonConvertor.encodeToJson(phoneUtils.getDeviceProperty()));
      
      Logger.d(status.toString());
      sendStringMsg("Checking in");
      
      String result = serviceRequest("checkin", status.toString());
      Logger.d("Checkin result: " + result);
      
      // Parse the result
      Vector<MeasurementTask> schedule = new Vector<MeasurementTask>();
      JSONArray jsonArray = new JSONArray(result);
      sendStringMsg("Checkin got " + jsonArray.length() + " tasks.");

      for (int i = 0; i < jsonArray.length(); i++) {
        Logger.d("Parsing index " + i);
        JSONObject json = jsonArray.optJSONObject(i);
        Logger.d("Value is " + json);
        // checkin task must support 
        if (json != null && 
            MeasurementTask.getMeasurementTypes().contains(json.get("type"))) {
          try {
            MeasurementTask task = 
                MeasurementJsonConvertor.makeMeasurementTaskFromJson(json, this.context);
            Logger.i(MeasurementJsonConvertor.toJsonString(task.measurementDesc));
            schedule.add(task);
          } catch (IllegalArgumentException e) {
            Logger.w("Could not create task from JSON: " + e);
            // Just skip it, and try the next one
          }
        }
      }
      
      this.lastCheckin = new Date();
      Logger.i("Checkin complete, got " + schedule.size() +
          " new tasks");
      checkinSuccess = true;
      return schedule;
    } catch (JSONException e) {
      Logger.e("Got exception during checkin", e);
      throw new IOException("There is exception during checkin()");
    } catch (IOException e) {
      Logger.e("Got exception during checkin", e);
      throw e;
    } finally {
      if (!checkinSuccess) {
        // Failure probably due to authToken expiration. Will authenticate upon next checkin.
        this.accountSelector.setAuthImmediately(true);
        this.authCookie = null;
      }
    }
  }
  

  public void uploadMeasurementResult(Vector<MeasurementResult> finishedTasks)
      throws IOException {    
    JSONArray resultArray = new JSONArray();
    for (MeasurementResult result : finishedTasks) {
      try {
        resultArray.put(MeasurementJsonConvertor.encodeToJson(result));
      } catch (JSONException e1) {
        Logger.e("Error when adding " + result);
      }
    }
    //add context result.
    synchronized(contextResultLock) {
	  for (MeasurementResult result : contextResult) { 
		  try {
			  //System.out.println("resultArray.size = "+resultArray.length());
			  System.out.println("context jason="+MeasurementJsonConvertor.encodeToJson(result));
	  resultArray.put(MeasurementJsonConvertor.encodeToJson(result)); }
	  catch (JSONException e1) { Logger.e("Error when adding context " +
	  result); } }
	  //System.out.println("contextResult size ="+contextResult.size());
	  contextResult.clear();
      
    }
    
    
    /////
    sendStringMsg("Uploading " + resultArray.length() + " measurement results.");
    Logger.i("TaskSchedule.uploadMeasurementResult() uploading: " + 
        resultArray.toString());
    String response = serviceRequest("postmeasurement", resultArray.toString());
    try {
      JSONObject responseJson = new JSONObject(response);
      if (!responseJson.getBoolean("success")) {
        throw new IOException("Failure posting measurement result");
      }
    } catch (JSONException e) {
      throw new IOException(e.getMessage());
    }
    Logger.i("TaskSchedule.uploadMeasurementResult() complete");
    sendStringMsg("Result upload complete.");
  }

  public void initializeForRRC() {
    Logger.w("Fetching cookie...");
    getCookie();
    int NUM_RETRIES = 5;
    int retry_len = 1000;
    for (int i = 0; i < NUM_RETRIES; i++) {
      try {
        if (accountSelector != null && authCookie!= null && accountSelector.getCheckinFuture() != null) {
          Logger.w("Cookie fetched!");
          return;
        }
        Thread.sleep(retry_len * i);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }
  

  /**
   * Send the RRC data to the server in order to update the model.
   * 
   * This is the only place the model gets updated
   * 
   * @param data
   * @throws IOException 
   */
  public void updateModel(RRCTask.RrcTestData data) throws IOException {
    DeviceInfo info = phoneUtils.getDeviceInfo();
    String network_id = phoneUtils.getNetwork();
    String[] parameters = data.toJSON(network_id, info.deviceId);
    try {
      for (String parameter: parameters) {
        Logger.w("Uploading RRC raw data: " + parameter);
        String response = serviceRequest("rrc/uploadRRCInference", parameter);
        Logger.w("Response from GAE: " + response);

        Logger.i("TaskSchedule.uploadMeasurementResult() complete");
        sendStringMsg("Result upload complete.");
      }
      JSONObject parameter = new JSONObject();
      parameter.put("phone_id",  info.deviceId);
      Logger.w("Trigger server to generate the model: " + parameter);
      String response = serviceRequest("rrc/generateModel", parameter.toString());
      Logger.w("Response from GAE: " + response);
    } catch (IOException e) {
      throw new IOException(e.getMessage());
    } catch (NumberFormatException e) {
      e.printStackTrace();
    } catch (JSONException e) {
      e.printStackTrace();
    }
  }
  
  /**
   * Impact of packet sizes on rrc inference results
   * @param sizeData
   */
  public void updateSizeData(RRCTask.RrcTestData sizeData) {
    DeviceInfo info = phoneUtils.getDeviceInfo();
    String network_id = phoneUtils.getNetwork();
    String[] sizeParameters = sizeData.sizeDataToJSON(network_id, info.deviceId);   
    
    try {
      for (String parameter: sizeParameters) {
        Logger.w("Uploading RRC size data: " + parameter);
        String response = serviceRequest("rrc/uploadRRCInferenceSizes", parameter);
        Logger.w("Response from GAE: " + response);
      }
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }  
  
  public void uploadPhoneResult(RRCTask.RrcTestData data, RRCTask.RrcTestData sizeData) {
    String networktype = phoneUtils.getNetwork();
    DeviceInfo info = phoneUtils.getDeviceInfo();

    String[] parameters = data.toJSON(networktype, info.deviceId);
    String[] sizeParameters = sizeData.sizeDataToJSON(networktype, info.deviceId);

    try {
      for (String parameter: parameters) {
        String response = serviceRequest("postphonemeasurement",
                                         parameter);
        JSONObject responseJson = new JSONObject(response);
        if (!responseJson.getBoolean("success")) {
          Logger.e("Failure posting phone measurement result");
        }
      }
    } catch (JSONException e) {
      Logger.e("JSON exception while uploading measurement result");
    } catch (IOException e) {
      Logger.e("IO exception while uploading measurement result");
    }       
  }

  /**
   * Used to generate SSL sockets.
   */
  class MySSLSocketFactory extends SSLSocketFactory {
    SSLContext sslContext = SSLContext.getInstance("TLS");

    public MySSLSocketFactory(KeyStore truststore)
        throws NoSuchAlgorithmException, KeyManagementException,
        KeyStoreException, UnrecoverableKeyException {
      super(truststore);

      X509TrustManager tm = new X509TrustManager() {
        public X509Certificate[] getAcceptedIssuers() {
          return null;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
          // Do nothing
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
          // Do nothing
        }
      };

      sslContext.init(null, new TrustManager[] { tm }, null);
    }

    @Override
    public Socket createSocket(Socket socket, String host, int port,
        boolean autoClose) throws IOException, UnknownHostException {
      return sslContext.getSocketFactory().createSocket(socket, host, port,
          autoClose);
    }

    @Override
    public Socket createSocket() throws IOException {
      return sslContext.getSocketFactory().createSocket();
    }
  }

  /**
   * Return an appropriately-configured HTTP client.
   */
  private HttpClient getNewHttpClient() {
    DefaultHttpClient client;
    try {
      KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
      trustStore.load(null, null);

      SSLSocketFactory sf = new MySSLSocketFactory(trustStore);
      sf.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

      HttpParams params = new BasicHttpParams();
      HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
      HttpProtocolParams.setContentCharset(params, HTTP.UTF_8);
      
      HttpConnectionParams.setConnectionTimeout(params, POST_TIMEOUT_MILLISEC);
      HttpConnectionParams.setSoTimeout(params, POST_TIMEOUT_MILLISEC);

      SchemeRegistry registry = new SchemeRegistry();
      registry.register(new Scheme("http", PlainSocketFactory
          .getSocketFactory(), 80));
      registry.register(new Scheme("https", sf, 443));

      ClientConnectionManager ccm = new ThreadSafeClientConnManager(params,
          registry);
      client = new DefaultHttpClient(ccm, params);
    } catch (Exception e) {
      Logger.w("Unable to create SSL HTTP client", e);
      client = new DefaultHttpClient();
    }
    
    // TODO(mdw): For some reason this is not sending the cookie to the
    // test server, probably because the cookie itself is not properly
    // initialized. Below I manually set the Cookie header instead.
    CookieStore store = new BasicCookieStore();
    store.addCookie(authCookie);
    client.setCookieStore(store);
    return client;
  }
  
  public String serviceRequest(String url, String jsonString) 
      throws IOException {
    
    if (this.accountSelector == null) {
      accountSelector = new AccountSelector(context);
    }
    if (!accountSelector.isAnonymous()) {
      synchronized (this) {
        if (authCookie == null) {
          if (!checkGetCookie()) {
            throw new IOException("No authCookie yet");
          }
        }
      }
    }
    
    HttpClient client = getNewHttpClient();
    String fullurl = (accountSelector.isAnonymous() ?
                      phoneUtils.getAnonymousServerUrl() :
                      phoneUtils.getServerUrl()) + "/" + url;
    Logger.i("Checking in to " + fullurl);
    HttpPost postMethod = new HttpPost(fullurl);
    
    StringEntity se;
    try {
      se = new StringEntity(jsonString);
    } catch (UnsupportedEncodingException e) {
      throw new IOException(e.getMessage());
    }
    postMethod.setEntity(se);
    postMethod.setHeader("Accept", "application/json");
    postMethod.setHeader("Content-type", "application/json");
    if (!accountSelector.isAnonymous()) {
      // TODO(mdw): This should not be needed
      postMethod.setHeader("Cookie", authCookie.getName() + "=" + authCookie.getValue());
    }

    ResponseHandler<String> responseHandler = new BasicResponseHandler();
    Logger.i("Sending request: " + fullurl);
    String result = client.execute(postMethod, responseHandler);
    return result;
  }
  
  /**
   * Initiates the process to get the authentication cookie for the user account.
   * Returns immediately.
   */
  public synchronized void getCookie() {
    if (phoneUtils.isTestingServer(phoneUtils.getServerUrl())) {
      Logger.i("Setting fakeAuthCookie");
      authCookie = getFakeAuthCookie();
      return;
    }
    if (this.accountSelector == null) {
      accountSelector = new AccountSelector(context);
    }
    
    try {
      // Authenticates if there are no ongoing ones
      if (accountSelector.getCheckinFuture() == null) {
        accountSelector.authenticate();
      }
    } catch (OperationCanceledException e) {
      Logger.e("Unable to get auth cookie", e);
    } catch (AuthenticatorException e) {
      Logger.e("Unable to get auth cookie", e);
    } catch (IOException e) {
      Logger.e("Unable to get auth cookie", e);
    }
  }
  
  /**
   * Resets the checkin variables in AccountSelector
   * */
  public void initializeAccountSelector() {
    accountSelector.resetCheckinFuture();
    accountSelector.setAuthImmediately(false);
  }
  
  private synchronized boolean checkGetCookie() {
    if (phoneUtils.isTestingServer(phoneUtils.getServerUrl())) {
      authCookie = getFakeAuthCookie();
      return true;
    }
    Future<Cookie> getCookieFuture = accountSelector.getCheckinFuture();
    if (getCookieFuture == null) {
      Logger.i("checkGetCookie called too early");
      return false;
    }
    if (getCookieFuture.isDone()) {
      try {
        authCookie = getCookieFuture.get();
        Logger.i("Got authCookie: " + authCookie);
        return true;
      } catch (InterruptedException e) {
        Logger.e("Unable to get auth cookie", e);
        return false;
      } catch (ExecutionException e) {
        Logger.e("Unable to get auth cookie", e);
        return false;
      }
    } else {
      Logger.i("getCookieFuture is not yet finished");
      return false;
    }
  }
  
  private void sendStringMsg(String str) {
    UpdateIntent intent = new UpdateIntent(str, UpdateIntent.MSG_ACTION);
    context.sendBroadcast(intent);    
  }
}

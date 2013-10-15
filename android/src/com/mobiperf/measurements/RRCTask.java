// Copyright 2013 RobustNet Lab, University of Michigan. All Rights Reserved.

package com.mobiperf.measurements;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InvalidClassException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.security.InvalidParameterException;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.StringBuilderPrinter;

import com.mobiperf.Checkin;
import com.mobiperf.Config;
import com.mobiperf.Logger;
import com.mobiperf.MeasurementDesc;
import com.mobiperf.MeasurementError;
import com.mobiperf.MeasurementResult;
import com.mobiperf.MeasurementTask;
import com.mobiperf.RRCTrafficControl;
import com.mobiperf.measurements.PingTask.PingDesc;
import com.mobiperf.util.MeasurementJsonConvertor;
import com.mobiperf.util.PhoneUtils;


/**
 * TODO: (longer term)
 * 
 * Add a tab for the user to view the model (if created). 
 * For externally triggered tasks (e.g. phone calls) suspend other measurements/wait
 * Add in distinct GUI test
 * 
 * Lower priority/long term:
 *    Choose measurement times intelligently
 */
public class RRCTask extends MeasurementTask {
  // Type name for internal use
  public static final String TYPE = "rrc";
  // Human readable name for the task
  public static final String DESCRIPTOR = "rrc";
  public static String TAG = "MobiPerf_RRC_INFERENCE";  
  private boolean stop = false;
  //public static boolean pause_traffic = false;
  private Context context;

  public static class RRCDesc extends MeasurementDesc {
    private static String HOST = "www.google.com";

    // Default echo server name and port to measure the RTT to infer RRC state
    private static int PORT = 50000;
    private static String ECHO_HOST = "ep2.eecs.umich.edu";
    // Perform RTT measurements every GRANULARITY ms
    public int GRANULARITY = 500;
    // MIN / MAX is the echo packet size
    int MIN = 0;
    int MAX = 1024;
    // Default total number of measurements
    int size = 30;
    // Echo server / port, and target to perform the extra tasks  
    public String echo_host = ECHO_HOST;
    public String target = HOST;
    int port = PORT;

    // Default threshold to repeat each RTT measurement because of background traffic
    int GIVEUP_THRESHHOLD = 15;

    // server controled variable
    boolean DNS = true;
    boolean TCP = true;
    boolean HTTP = true;
    boolean RRC = true;

    // Whether RRC result is visible to users
    public boolean RESULT_VISIBILITY = false;
    
    int baseline_max = -1;
    int baseline_min = -1;    

    int[] http_test;
    int[] dns_test;
    int[] tcp_test;
    int[] times;

    private boolean extra = false;
    
    int[] default_times = new int[] {0, 6, 15};
    

    public RRCDesc(String key, Date startTime,
        Date endTime, double intervalSec, long count, long priority,
        Map<String, String> params) {
      super(RRCTask.TYPE, key, startTime, endTime, intervalSec, count, priority, params);
      initializeParams(params);
    }
    
    public MeasurementResult getResults(MeasurementResult result) {    	
      if (HTTP) result.addResult("http", http_test);
      if (TCP) result.addResult("tcp", tcp_test);
      if (DNS) result.addResult("dns", dns_test);
      result.addResult("times", times);
      
      return result;
    }
    
    public void displayResults(StringBuilderPrinter printer) {
      String DEL = "\t", toprint = DEL + DEL;
      for (int i = 1; i <= times.length; i++) {
        toprint += DEL + " | state" + i; 
      }
      toprint += " |";
      int oneLineLen = toprint.length();
      toprint += "\n";
      // seperator
      for (int i = 0; i < oneLineLen; i++) {
      	toprint += "-";
      }
      toprint += "\n";
      if (HTTP) {
        toprint += "HTTP (ms)" + DEL;
        for (int i = 0; i < http_test.length; i++) {
          toprint += DEL + " | " + Integer.toString(http_test[i]);
        }
        toprint += " |\n";
        for (int i = 0; i < oneLineLen; i++) {
          toprint += "-";
        }
        toprint += "\n";
      }
      
      if (DNS) {
      	toprint += "DNS (ms)" + DEL;
        for (int i = 0; i < dns_test.length; i++) {
          toprint += DEL + " | " + Integer.toString(dns_test[i]);
        }
        toprint += " |\n";
        for (int i = 0; i < oneLineLen; i++) {
          toprint += "-";
        }
        toprint += "\n";
      }

      if (TCP) {
      	toprint += "TCP (ms)" + DEL;
        for (int i = 0; i < tcp_test.length; i++) {
          toprint += DEL + " | " + Integer.toString(tcp_test[i]);
        }
        toprint += " |\n";
        for (int i = 0; i < oneLineLen; i++) {
          toprint += "-";
        }
        toprint += "\n";
      }

      toprint += "Timers (s)"; 
      for (int i = 0; i < times.length; i++) {
        double curTime = (double)times[i] * (double)GRANULARITY / 1000.0;
        toprint += DEL + " | " + String.format("%.2f", curTime);
      }
      toprint += " |\n";
      printer.println(toprint);
    }

    @Override
    public String getType() {
      return RRCTask.TYPE;
    }
    
    @Override
    protected void initializeParams(Map<String, String> params) {
	    // TODO Auto-generated method stub
	    
      if (params == null) {
        return;
      }
      this.echo_host = params.get("echo_host");
      this.target = params.get("target");
      if (this.echo_host == null) {
        this.echo_host = ECHO_HOST;
      }
      if (this.target == null) {
        this.target = HOST;
      }
      Logger.d("param: echo_host "+ this.echo_host);
      Logger.d("param: target "+ this.target);
      
      try {
        String val = null;
        if ((val = params.get("min")) != null
            && val.length() > 0 && Integer.parseInt(val) > 0) {
          this.MIN = Integer.parseInt(val);
        }
        Logger.d("param: Min "+ this.MIN);
        if ((val = params.get("max")) != null
            && val.length() > 0 && Integer.parseInt(val) > 0) {
          this.MAX = Integer.parseInt(val);
        }
        Logger.d("param: MAX "+ this.MAX);
        if ((val = params.get("port")) != null
            && val.length() > 0 && Integer.parseInt(val) > 0) {
          this.port = Integer.parseInt(val);
        }
        Logger.d("param: port "+ this.port);
        if ((val = params.get("size")) != null
            && val.length() > 0 && Integer.parseInt(val) > 0) {
          this.size = Integer.parseInt(val);
        }
        Logger.d("param: size "+ this.size);
        
        if ((val = params.get("dns")) != null
            && val.length() > 0) {
          this.DNS = Boolean.parseBoolean(val);
        }   
        Logger.d("param: DNS "+ this.DNS);
        if ((val = params.get("http")) != null
            && val.length() > 0) {
          this.HTTP = Boolean.parseBoolean(val);
        }   
        Logger.d("param: HTTP "+ this.HTTP);  
        if ((val = params.get("tcp")) != null
            && val.length() > 0) {
          this.TCP = Boolean.parseBoolean(val);
        } 
        Logger.d(params.get("rrc"));
        Logger.d("param: TCP "+ this.TCP);
        if ((val = params.get("rrc")) != null
            && val.length() > 0) {
          this.RRC = Boolean.parseBoolean(val);
        }
        Logger.d("param: RRC "+ this.RRC);
        if ((val = params.get("result_visibility")) != null
                && val.length() > 0) {
          this.RESULT_VISIBILITY = Boolean.parseBoolean(val);
        }
        Logger.d("param: visibility "+ this.RESULT_VISIBILITY);
        if ((val = params.get("giveup_threshhold")) != null
            && val.length() > 0 && Integer.parseInt(val) > 0) {
          this.GIVEUP_THRESHHOLD = Integer.parseInt(val);
        }
        Logger.d("param: GIVEUP_THRESHHOLD "+ this.GIVEUP_THRESHHOLD);
        if ((val = params.get("state1_demotion_timer")) != null
            && val.length() > 0 && Integer.parseInt(val) > 0) {
          this.default_times[0] = Integer.parseInt(val);
        }
        Logger.d("param: state1_demotion_timer "+ this.default_times[0]);
        if ((val = params.get("state2_demotion_timer")) != null
            && val.length() > 0 && Integer.parseInt(val) > 0) {
          this.default_times[1] = Integer.parseInt(val);
        }
        Logger.d("param: state2_demotion_timer "+ this.default_times[1]);
        if ((val = params.get("state3_demotion_timer")) != null
            && val.length() > 0 && Integer.parseInt(val) > 0) {
          this.default_times[2] = Integer.parseInt(val);
        }
        Logger.d("param: state3_demotion_timer "+ this.default_times[2]);
        if (this.default_times[0] >= this.default_times[1] ||
            this.default_times[1] >= this.default_times[2]) {
          throw new InvalidParameterException(
              "RRCTask cannot be created due to invalid params -" +
              " default times values must be in strict increasing orders");
        }
        if (times == null) {
          times = default_times; // obviously, because they're default
        }
      } catch (NumberFormatException e) {
        throw new InvalidParameterException(
            " RRCTask cannot be created due to invalid params");
      }
      
      if (size == 0) {
        size = 31;
      }
    }

    void addNormalData(int[] value) {
      baseline_max = value[0];
      baseline_min = value[1];
    }
    
    public void initializeExtraTaskResults(int size) {
      http_test = new int[size];
      dns_test = new int[size];
      tcp_test = new int[size];
      for (int i = 0; i < size; i++) {
        http_test[i] = -1;
        dns_test[i] = -1;
        tcp_test[i] = -1;
      }
      extra = true;
    }
    
    public void setHttp(int i, int val) throws MeasurementError {
      if (!extra) {
        throw new MeasurementError("Data class not initialized");
      }
      http_test[i] = val;     
    }
    
    public void setTcp(int i, int val) throws MeasurementError {
      if (!extra) {
        throw new MeasurementError("Data class not initialized");
      }
      tcp_test[i] = val;      
    }
    
    public void setDns(int i, int val) throws MeasurementError {
      if (!extra) {
        throw new MeasurementError("Data class not initialized");
      }
      dns_test[i] = val;      
    }
  }
  
  public static class RrcTestData {
    int[] rtts_low;
    int[] rtts_high;
    int[] lost_low;
    int[] lost_high;
    int[] signal_low;
    int[] signal_high;
    int[] error_low;
    int[] error_high;
    int test_id;
    
    public RrcTestData(int size, Context context) {
      size = size + 1;
      rtts_low = new int[size];
      rtts_high = new int[size];
      lost_low = new int[size];
      lost_high = new int[size];
      signal_low = new int[size];
      signal_high = new int[size];
      error_low = new int[size];
      error_high = new int[size];
      test_id = getTestId(context);
   
      for (int i = 0; i < rtts_low.length; i++) {
        rtts_low[i] = 7000;
        rtts_high[i] = 7000;
        lost_low[i] = -1;
        lost_high[i] = -1;
        signal_low[i] = -1;
        signal_high[i] = -1;
        error_low[i] = -1;
        error_high[i] = -1;       
      }
    }

    public int testId() {
      return test_id;
    }
    
    public String[] toJSON(String networktype, String phone_id) {
      String[] returnval = new String[rtts_low.length];
      try {
        for (int i = 0; i < rtts_low.length; i++) {
          JSONObject subtest = new JSONObject();  
          subtest.put("rtt_low", rtts_low[i]);        
          subtest.put("rtt_high", rtts_high[i]);
          subtest.put("lost_low", lost_low[i]);
          subtest.put("lost_high", lost_high[i]);
          subtest.put("signal_low", signal_low[i]);
          subtest.put("signal_high", signal_high[i]);
          subtest.put("error_low", error_low[i]);
          subtest.put("error_high", error_high[i]);
          subtest.put("network_type", networktype);
          subtest.put("time_delay", i);
          subtest.put("test_id", test_id);
          subtest.put("phone_id", phone_id);
          returnval[i] = subtest.toString();
        }
      } catch (JSONException e) {
        Logger.e("Error converting RRC data to JSON");
      }
      return returnval;
    }
    
    public void deleteItem(int i){
      rtts_low[i] = -1;
      rtts_high[i] = -1;
      lost_low[i] = -1;
      lost_high[i] = -1;
      signal_low[i] = -1;
      signal_high[i] = -1;
      error_low[i] = -1;
      error_high[i] = -1;
    }
    
    public void updateAll(int index, int rtt_max, int rtt_min, 
        int num_packets_lost_max, int num_packets_lost_min, 
        int error_high, int error_low, int signal_high, int signal_low) {
      this.rtts_high[index] = (int) rtt_max;
      this.rtts_low[index] = (int) rtt_min;
      this.lost_high[index] = num_packets_lost_max;
      this.lost_low[index] = num_packets_lost_min;
      this.error_high[index] = error_high; 
      this.error_low[index] = error_low;
      this.signal_high[index] = signal_high; 
      this.signal_low[index] = signal_low;
    }
  }

  @SuppressWarnings("rawtypes")
  public static Class getDescClass() throws InvalidClassException {
    return RRCDesc.class;   
  }
  
  public RRCTask(MeasurementDesc desc, Context parent) {
    super(new RRCDesc(desc.key, desc.startTime, desc.endTime,
        desc.intervalSec, desc.count, desc.priority, desc.parameters), parent);
    context = parent;
  }

  @Override
  public MeasurementTask clone() {
    MeasurementDesc desc = this.measurementDesc;
    RRCDesc newDesc = new RRCDesc(desc.key, desc.startTime, desc.endTime,
        desc.intervalSec, desc.count, desc.priority, desc.parameters);
    return new RRCTask(newDesc, parent);
  }
  
  @Override
  public MeasurementResult call() throws MeasurementError {
  	// TODO(Haokun): delete after debugging
  	Logger.w("RRC result: before run inference tests");
    RRCDesc desc = runInferenceTests();
    Logger.w("RRC result: after run inference tests");
    return constructResultStandard(desc);
  }

  @Override
  public String getDescriptor() {
    return DESCRIPTOR;
  }

  @Override
  public String getType() {
    return RRCTask.TYPE;
  }

  @Override
  public void stop() {
    stop = true;
  }

  private MeasurementResult constructResultStandard(RRCDesc desc) {
    PhoneUtils phoneUtils = PhoneUtils.getPhoneUtils();
    boolean success = true;
    MeasurementResult result = new MeasurementResult(
        phoneUtils.getDeviceInfo().deviceId,
        phoneUtils.getDeviceProperty(), RRCTask.TYPE,
        System.currentTimeMillis() * 1000, success,
        this.measurementDesc);

    if (desc.extra) {
      result = desc.getResults(result);
    }

    Logger.i(MeasurementJsonConvertor.toJsonString(result));
    return result;
  }
  
  private RRCDesc runInferenceTests() throws MeasurementError {

    Checkin checkin = new Checkin(context);
    //checkin.initializeForRRC();

    RRCDesc desc = (RRCDesc) measurementDesc;
    int[] times = desc.default_times;
    // TODO(Haokun): delete after debugging
    Logger.w("Before getModel: Times value is " + Arrays.toString(desc.times));
    try {
      times = checkin.getModel();
      if (times.length > 0) {
      	// only there is a model then update the current times
        desc.times = times;
      }
    } catch (IOException e1) {
      e1.printStackTrace();
    }
    // TODO (Haokun): delete after debugging
    Logger.w("After getModel: Times value is " + Arrays.toString(desc.times));
    
    PhoneUtils utils = PhoneUtils.getPhoneUtils();
    desc.initializeExtraTaskResults(times.length);
    
    // TODO (Sanae):add a threshhold back in when done testing
    if (utils.getNetwork() == "UNKNOWN" ||utils.getNetwork() == "WIRELESS" /*|| utils.getCurrentRssi() < 8*/) {
      Logger.d("Returning: network is" + utils.getNetwork() + " rssi " + utils.getCurrentRssi());
      return desc;
    }

    try {
      RRCTrafficControl.PauseTraffic();
      if (desc.RRC) {
        RrcTestData data = new RrcTestData(desc.size, context);

        Logger.d("Active inference: about to begin");
        Logger.d(desc.echo_host + ":" + desc.port);

        InetAddress serverAddr = InetAddress.getByName(desc.echo_host);
        
        Logger.d("Demotion inference: about to begin");
        desc.addNormalData(normalTransmissions(serverAddr, desc, data));
        desc = inferDemotion(serverAddr, desc, data, utils);

        Logger.d("About to save data");
        this.progress = Math.min(Config.MAX_PROGRESS_BAR_VALUE, 40);
        try {
          // TODO (Haokun): delete after debugging
          Logger.w("RRC: update the model on the GAE datastore");
          checkin.updateModel(data);
          Logger.d("Saving data complete");
        } catch (IOException e ) {
          e.printStackTrace();
          Logger.e("Data not saved: " + e.getMessage());
        }
      }

      // extra tests
      if (desc.DNS) {
      	// TODO(Haokun): delete after debugging
      	Logger.w("Start DNS extra task");
        runDnsTest(desc.times, desc);
      }
      this.progress = Math.min(Config.MAX_PROGRESS_BAR_VALUE, 60);
      if (desc.TCP) {
      	// TODO(Haokun): delete after debugging
      	Logger.w("Start TCP extra task");
        runTCPHandshakeTest(desc.times, desc);           
      }
      this.progress = Math.min(Config.MAX_PROGRESS_BAR_VALUE, 80);
      if (desc.HTTP) {
      	// TODO(Haokun): delete after debugging
      	Logger.w("Start HTTP extra task");
        runHTTPTest(desc.times, desc);         
      }
      this.progress = Math.min(Config.MAX_PROGRESS_BAR_VALUE, 100);
    } catch (UnknownHostException e) {
      e.printStackTrace();
    } catch (SocketException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    } catch (InterruptedException e) {
      e.printStackTrace();
    } finally {
      RRCTrafficControl.UnPauseTraffic();   
    }

    return desc;
  }
  
  private void runHTTPTest(final int[] times, RRCDesc desc) {
    /*
     * Length of time it takes to request and read in a page.
     */
    
    Logger.d("Active inference HTTP test: about to begin");
    // Important: Initialize the dns_test
    if (times.length != desc.http_test.length) {
    	desc.http_test = new int[times.length];
    }
    long start_time = 0;
    long end_time = 0;
    try {
      for (int i = 0; i < times.length; i++) {
        for (int j = 0; j < desc.GIVEUP_THRESHHOLD; j++) {
          checkIfWifi();
          if (stop) {
            return;
          }

          int[] packets_first = getPacketsSent();
         
          HttpClient client = new DefaultHttpClient();
          HttpGet request = new HttpGet();

          request.setURI(new URI("http://" + desc.target));

          start_time = System.currentTimeMillis();
          HttpResponse response = client.execute(request);
          end_time = System.currentTimeMillis();

          BufferedReader in = null;
          in = new BufferedReader
          (new InputStreamReader(response.getEntity().getContent()));
          StringBuffer sb = new StringBuffer("");
          String line = "";
          
          while ((line = in.readLine()) != null) {
            sb.append(line + "\n");
          }
          in.close();
          
          int[] packets_last = getPacketsSent();        
          int rcv_packets =  (packets_last[0] - packets_first[0]);
          int sent_packets = (packets_last[1] - packets_first[1]);  

          Logger.d("Packets sent: " + rcv_packets + " " + sent_packets);
          
          if (rcv_packets <= 100 && sent_packets <=100) {
            Logger.d("No competing traffic, continue");
            break;
          }
        }

        long rtt = end_time - start_time;  
        try {
          desc.setHttp(i, (int) rtt);
        } catch (MeasurementError e) {
          e.printStackTrace();
        }
        Logger.d("Time for Http" + rtt);        
      }
    } catch (ClientProtocolException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    } catch (URISyntaxException e) {
      e.printStackTrace();
    }
  }
  
  /**
   * Test is similar to the approach taken in DnsLookUpTask.java.
   * @param times
   * @param desc
   * @throws MeasurementError
   */
  
  public void runDnsTest(final int[] times, RRCDesc desc) throws MeasurementError {
    Logger.d("Active inference DNS test: about to begin");
    // Important: Initialize the dns_test
    if (times.length != desc.dns_test.length) {
    	desc.dns_test = new int[times.length];
    }
    //long[] rtts = new long[times.length];
    long start_time = 0;
    long end_time = 0;  

    for (int i = 0; i < times.length; i++) {
      for (int j = 0; j < desc.GIVEUP_THRESHHOLD; j++) {

        checkIfWifi();
        if (stop) {
          return;
        }
        int[] packets_first = getPacketsSent();

        /*
         *  Fall back to other testing metohd
         */
        try {
          InetAddress serverAddr; 
          serverAddr = InetAddress.getByName(desc.echo_host); 
              sendPacket(serverAddr, desc.MAX, null, desc);             
          waitTime(times[i] * desc.GRANULARITY, true);
        } catch (InterruptedException e1) {
          e1.printStackTrace();
        } catch (UnknownHostException e) {
          e.printStackTrace();
        } catch (IOException e) {
          e.printStackTrace();
        }
        
        UUID uuid = UUID.randomUUID();
        String host = uuid.toString()+ ".com";  
        start_time = System.currentTimeMillis();  
        try {
          InetAddress serverAddr = InetAddress.getByName(host);
        } catch (UnknownHostException e) {
          // we do this on purpose!             
        } catch (IOException e) {
          e.printStackTrace();
        }
        end_time = System.currentTimeMillis();
        int[] packets_last = getPacketsSent();
        int rcv_packets =  (packets_last[0] - packets_first[0]);
        int sent_packets = (packets_last[1] - packets_first[1]);
        if (rcv_packets <= 3  && sent_packets <= 3 && (end_time - start_time) > 10) {
          Logger.d("No competing traffic, continue");       
          break;
        }
        Logger.d("Packets sent: " + rcv_packets + " " + sent_packets);
        Logger.d("Time: " + (end_time - start_time));
      }         
      long rtt = end_time - start_time;  
      try {
        desc.setDns(i, (int) rtt);
      } catch (MeasurementError e) {
        e.printStackTrace();
      }
      Logger.d("Time for DNS" + rtt);             
    }
  }
  
  public void runTCPHandshakeTest(final int[] times, RRCDesc desc) {
    Logger.d("Active inference TCP test: about to begin");
    // Important: Initialize the dns_test
    if (times.length != desc.tcp_test.length) {
    	desc.tcp_test = new int[times.length];
    }
    long start_time = 0;
    long end_time = 0;

    try {
      for (int i = 0; i < times.length; i++) {
        for (int j = 0; j < desc.GIVEUP_THRESHHOLD; j++) {
          checkIfWifi();
          if (stop) {
            return;
          }

          int[] packets_first = getPacketsSent();
          
          // Induce DCH then wait for specified time
          InetAddress serverAddr; 
          serverAddr = InetAddress.getByName(desc.echo_host); 
          sendPacket(serverAddr, desc.MAX, null, desc);             
          waitTime(times[i] * 500, true); 
          
          // begin test
          start_time = System.currentTimeMillis();
          serverAddr = InetAddress.getByName(desc.target);
          // three-way handshake done when socket created
          Socket socket = new Socket(serverAddr, 80);   
          end_time = System.currentTimeMillis();
            
          int[] packets_last = getPacketsSent();              
          int rcv_packets =  (packets_last[0] - packets_first[0]);
          int sent_packets = (packets_last[1] - packets_first[1]);
          if (rcv_packets <= 5 && sent_packets <=4) {
            Logger.d("No competing traffic, continue");
            socket.close();       
            break;
          }
          socket.close();
          Logger.d("Packets sent: " + rcv_packets + " " + sent_packets);
        }
        long rtt = end_time - start_time;  
        try {
          desc.setTcp(i, (int) rtt);
        } catch (MeasurementError e) {
          e.printStackTrace();
        }
        Logger.d("Time for TCP" + rtt);
      }
    } catch (InterruptedException e1) {
      e1.printStackTrace();
    } catch (UnknownHostException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private int[] normalTransmissions(InetAddress serverAddr, RRCDesc desc, RrcTestData data) throws InterruptedException, IOException {
    /*
     * Measure RTT from RCH state.  Good for normalizing quantity of noise.
     */
    
    long rtt_min;
    long rtt_max;
    checkIfWifi();

    while (true) {
      if (stop) {
        int[] retval = {-1, -1};
        return retval;
      }
      
      Logger.d("Testing basic rtts");
      
      int[] packets_first = getPacketsSent();
      sendPacket(serverAddr, desc.MAX, data, desc); // boost to RCH
      rtt_min = sendPacket(serverAddr,  desc.MIN, data, desc); // then measure
      if (rtt_min == -1) continue; // timeout, try again
      rtt_max = sendPacket(serverAddr, desc.MAX, data, desc);
      if (rtt_max == -1) continue; // timeout, try again
      int[] packets_last = getPacketsSent();
      
      int rcv_packets =  (packets_last[0] - packets_first[0]);
      int sent_packets = (packets_last[1] - packets_first[1]);
      // TODO(Haokun): remove after debugging
      Logger.d("Sent packet number is " + sent_packets + "; Received packet number is " + rcv_packets);
      if (rcv_packets == 3 && sent_packets == 3) {
        Logger.d("No competing traffic, continue");       
        break;
      }
      checkIfWifi();
      Logger.d("Try again. 3 expected, packets received:" + (packets_last[0] - packets_first[0]) + 
        	     " Packets sent: " + (packets_last[1] - packets_first[1]));     
    }
    
    Logger.d("RTT of max packet:" + rtt_max);
    Logger.d("RTT of min packet:" + rtt_min);
    int[] retval = {(int) rtt_max, (int) rtt_min};
    return retval;
  }
  
  private RRCDesc inferDemotion(InetAddress serverAddr, RRCDesc desc, RrcTestData data, PhoneUtils utils) throws InterruptedException, IOException {
    Logger.d("3G demotion basic test");
    
    for (int i = 0; i <= desc.size; i++) {
      this.progress = Math.min(Config.MAX_PROGRESS_BAR_VALUE,
          (int) (100* i/(desc.size)));

      checkIfWifi();
      if (stop) {
        return desc;
      }
      inferDemotionHelper(serverAddr, i, data, desc, utils);
      Logger.d("Finished demotion test with length" + i);
      
      // Note that we scale from 0-90 to save some stuff for extra tests.
      // If we wanted to really do this properly we could scale
      // according to how long each task should take.
      this.progress = Math.min(Config.MAX_PROGRESS_BAR_VALUE,
          (int) (90 * i / desc.size));
    }
    return desc;
  }

  @Override
  public String toString() {
    RRCDesc desc = (RRCDesc) measurementDesc;
    return "[RRC]\n  Echo Server: " + desc.echo_host + "\n  Target: " + desc.target 
        + "\n  Interval (sec): " + desc.intervalSec 
        + "\n  Next run: " + desc.startTime;
  }

  /*********************************************************************
   *                    UTILITIES                       *
   *********************************************************************/

  public static void waitTime(int time_to_sleep, boolean use_ms) throws InterruptedException {
    /**
     * Sleep for the number of milliseconds indicated.
     * If seconds are to be used instead, choose the above function.
     */
    Logger.d("Wait for n ms: " + time_to_sleep);

    if (!use_ms) {
      time_to_sleep = time_to_sleep * 1000;
    }
    Thread.sleep(time_to_sleep);
  }
  
  /**
   * Sends a packet of the size indicated and waits for a response.
   * 
   */
  public static long[] sendMultiPackets(InetAddress serverAddr, int size, int num, int MIN, int port) throws IOException {

    long start_time = 0;
    long end_time = 0;
    byte[] buf = new byte[size];
    byte[] recv_buf = new byte[MIN];
    long[] retval = {-1, -1};
    long num_lost = 0;
    int i = 0;

    DatagramSocket socket = new DatagramSocket();
    DatagramPacket packet_rcv = new DatagramPacket(recv_buf, recv_buf.length);    
    DatagramPacket packet = new DatagramPacket(buf, buf.length, serverAddr, port);

    try {
        socket.setSoTimeout(7000);
        start_time = System.currentTimeMillis();
        Logger.d("Sending packet, waiting for response ");
        for (i = 0; i < num; i++) {
          socket.send(packet);          
        }
        for (i = 0; i < num; i++) {
          socket.receive(packet_rcv); 
          if (i == 0) {

            end_time = System.currentTimeMillis();
          }       
        }
    } catch (SocketTimeoutException e){
      Logger.d("Timed out");
        num_lost += (num - i);
        socket.close();
    }
    Logger.d("Sending complete: " + end_time);
    
    retval[0] = end_time - start_time;
    retval[1] = num_lost;
    
    return retval;
  }
  
  private static long sendPacket(InetAddress serverAddr, int size, RrcTestData data, RRCDesc desc)  throws IOException {
    return sendPacket(serverAddr, size, desc.MAX, desc.MIN, desc.port, data);
  }
  
  public static long sendPacket(InetAddress serverAddr, int size, int MAX, int MIN, int port, RrcTestData data) throws IOException {
    /**
     * Sends a packet of the size indicated and waits for a response.
     * 
     */
    long start_time = 0;
    byte[] buf = new byte[size];
    byte[] recv_buf = new byte[MIN];

    DatagramSocket socket = new DatagramSocket();
    DatagramPacket packet_rcv = new DatagramPacket(recv_buf, recv_buf.length);
    
    DatagramPacket packet = new DatagramPacket(buf, buf.length, serverAddr, port);

    try {
        socket.setSoTimeout(7000);
        start_time = System.currentTimeMillis();
        Logger.d("Sending packet, waiting for response ");

        socket.send(packet);
        socket.receive(packet_rcv);
    } catch (SocketTimeoutException e){
        Logger.d("Timed out, trying again");
        socket.close();
        return -1;
    }
    long end_time = System.currentTimeMillis();
    Logger.d("Sending complete: " + end_time);
    //Log.w(TAG, "ending " + end_time);
    
    return end_time - start_time;
  } 
  
  public static int[] getPacketsSent() {
    /**
     * Determine how many packets, so far, have beeen sent (the contents of /proc/net/dev/).
     * Used to determine if there has been interfering traffic.
     */
    int[] retval = {-1, -1};
    try {
      Process process = Runtime.getRuntime().exec("cat /proc/net/dev");
      BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
      String line = bufferedReader.readLine();
      Pattern r = Pattern.compile("[1-9][0-9]+");
      while (line != null && !( !line.contains("lo:") && r.matcher(line).find())) {
        line = bufferedReader.readLine();
      }
      if (line == null) {
        Logger.d("No data found for data sent string");
        return retval;
      }
      String[] brokenline = line.split("\\s+");
      if (brokenline.length == 17) { // Checking which of two possible formats /proc/net/dev is in. 
        retval[0] = Integer.parseInt(brokenline[2]);
        retval[1] = Integer.parseInt(brokenline[10]);
      } else {
        retval[0] = Integer.parseInt(brokenline[3]);
        retval[1] = Integer.parseInt(brokenline[11]);       
      }
    } catch (IOException e) {
      e.printStackTrace();
    }   
    
    return retval;
  }
  
  private long[] inferDemotionHelper(InetAddress serverAddr, int wait, RrcTestData data, RRCDesc desc, PhoneUtils utils) throws IOException, InterruptedException {
    return inferDemotionHelper(serverAddr, wait, data, desc.MAX, desc.MIN, desc.port, desc.GRANULARITY, wait, utils); 
  }
  

  public static long[] inferDemotionHelper(InetAddress serverAddr, int wait, RrcTestData data, int MAX, int MIN, int port, int granularity, int index, PhoneUtils utils) throws IOException, InterruptedException {
    /**
     * Once we generalize the RRC state inference problem, this is what we will use (since in general, RRC state can differ between large and small packets).
     * Gives the RTT for a large packet and a small packet for a given time after inducing DCH state.  Granularity currently half-seconds but can easily be increased.
     * 
     * Measures packets sent before and after to make sure no extra packets were sent, and retries on a failure.
     * Also checks that there was no timeout and retries on a failure.
     */
    long rtt_max = -1;
    long rtt_min = -1;
    int num_packets_lost_max = 0;
    int num_packets_lost_min = 0;
    
    int error_high = 0;
    int error_low = 0;
    int signal_high = 0;
    int signal_low = 0;
    
    while(true) { 
      Logger.d("Active inference: about to begin helper");
      int[] packets_first = getPacketsSent();
      sendPacket(serverAddr, MAX, MAX, MIN, port, data);
      waitTime(wait*granularity, true);
      
      //error_high = errorrate;
      signal_high = utils.getCurrentRssi();
      long[] retval = sendMultiPackets(serverAddr, MAX, 10, MIN, port);
      num_packets_lost_max = (int) retval[1];
      rtt_max = retval[0];
      
      waitTime(wait*granularity, true);
      
      //error_low = errorrate;
      signal_low = utils.getCurrentRssi();
      
      retval = sendMultiPackets(serverAddr, MIN, 10, MIN, port);
      num_packets_lost_min = (int) retval[1];
      rtt_min = retval[0];

      
      int[] packets_last = getPacketsSent();
      
      int rcv_packets =  (packets_last[0] - packets_first[0]);
      int sent_packets = (packets_last[1] - packets_first[1]);
      
      if (rcv_packets <= 21 && sent_packets == 21) {

        Logger.d("No competing traffic, continue");
        break;
      }
      Logger.d("Try again. 21 expected, packets received:" + (packets_last[0] - packets_first[0]) + " Packets sent: " + (packets_last[1] - packets_first[1]));
    }

    Logger.d("3G demotion, lower bound: rtts are:" + rtt_max + " " + rtt_min + " " + num_packets_lost_max + " " + num_packets_lost_min);

    long[] retval = {rtt_max, rtt_min};
    data.updateAll(index, (int)rtt_max, (int)rtt_min, num_packets_lost_max, num_packets_lost_min, error_high, error_low, signal_high, signal_low);

    return retval;
  }
  
  public static synchronized int getTestId(Context context) {
    SharedPreferences prefs = context.getSharedPreferences("test_ids", Context.MODE_PRIVATE);
    int testid = prefs.getInt("test_id", 0) + 1;
    SharedPreferences.Editor editor = prefs.edit();
    editor.putInt("test_id", testid);
    editor.commit();
    return testid;    
  }
  
  /**
   * If on wifi, suspend test indefinitely.
   * Use exponential back-off to calculate the wait time.
   * TODO cancel suspension of other tasks.
   * @return
   */
  
  public void checkIfWifi() {
    PhoneUtils phoneUtils = PhoneUtils.getPhoneUtils();
    
    int time_to_wait = 10;
    while (true) {
      if (phoneUtils.getNetwork() != PhoneUtils.NETWORK_WIFI) {
        RRCTrafficControl.PauseTraffic();
        return;
      }
      if (stop) {
        RRCTrafficControl.PauseTraffic();
        return;
      }
      
      Logger.d("RRCTask: on Wifi, try again later:" + phoneUtils.getNetwork());
      if (time_to_wait < 500) {
        time_to_wait = time_to_wait * 2;        
      } else {
        // if it's taking a while, stop pausing traffic
        RRCTrafficControl.UnPauseTraffic();
      }
      try {
        waitTime(time_to_wait, false);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }
}

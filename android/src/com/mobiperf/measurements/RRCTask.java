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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
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
    int size = 31;
    // Echo server / port, and target to perform the upper-layer tasks  
    public String echoHost = ECHO_HOST;
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
    
    /* For the upper-layer tests, a series of tests are made for different inter-packet
     * intervals, in order.  "Times" indicates the inter-packet intervals, the
     * other fields store the results.  All must be the same size.
     */
    Integer[] times; // The times where the above tests were made, in units of GRANULARITY.
    int[] httpTest; // The results of the HTTP test performed at each time
    int[] dnsTest; // likewise, for the DNS test
    int[] tcpTest; // likewise, for the TCP test

    // Whether or not to run the upper layer tests, i.e. the HTTP, TCP and DNS tests.
    // Disabling this flag will disable all upper layer tests.
    private boolean runUpperLayerTests = false;
    
    /* Default times between packets for which the upper layer tests are performed.
     * Later, these times will be replaced by times from the model.
     * These times are GRANULARITY milliseconds: if GRANULARITY is 500, then 
     * a default time of 6 means measurements are taken 500 ms apart.
     */
    Integer[] defaultTimesULTasks = new Integer[] {0, 2, 4, 8, 12, 16, 22};
    

    public RRCDesc(String key, Date startTime,
        Date endTime, double intervalSec, long count, long priority,
        Map<String, String> params) {
      super(RRCTask.TYPE, key, startTime, endTime, intervalSec, count, priority, params);
      initializeParams(params);
    }
    
    public MeasurementResult getResults(MeasurementResult result) {    	
      if (HTTP) result.addResult("http", httpTest);
      if (TCP) result.addResult("tcp", tcpTest);
      if (DNS) result.addResult("dns", dnsTest);
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
        for (int i = 0; i < httpTest.length; i++) {
          toprint += DEL + " | " + Integer.toString(httpTest[i]);
        }
        toprint += " |\n";
        for (int i = 0; i < oneLineLen; i++) {
          toprint += "-";
        }
        toprint += "\n";
      }
      
      if (DNS) {
      	toprint += "DNS (ms)" + DEL;
        for (int i = 0; i < dnsTest.length; i++) {
          toprint += DEL + " | " + Integer.toString(dnsTest[i]);
        }
        toprint += " |\n";
        for (int i = 0; i < oneLineLen; i++) {
          toprint += "-";
        }
        toprint += "\n";
      }

      if (TCP) {
      	toprint += "TCP (ms)" + DEL;
        for (int i = 0; i < tcpTest.length; i++) {
          toprint += DEL + " | " + Integer.toString(tcpTest[i]);
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
   
    /**
     * Given the parameters fetched from the server, sets up the parameters as needed for the 
     * upper layer tests, i.e. the application layer tests. 
     */ 
    @Override
    protected void initializeParams(Map<String, String> params) {

      // In this case, we fall back to the default values defined above.	    
      if (params == null) {
        return;
      }

      // The parameters for the echo server
      this.echoHost = params.get("echo_host");
      this.target = params.get("target");
      if (this.echoHost == null) {
        this.echoHost = ECHO_HOST;
      }
      if (this.target == null) {
        this.target = HOST;
      }
      Logger.d("param: echo_host "+ this.echoHost);
      Logger.d("param: target "+ this.target);
      
      try {
        String val = null;
        // Size of the small packet
        if ((val = params.get("min")) != null
            && val.length() > 0 && Integer.parseInt(val) > 0) {
          this.MIN = Integer.parseInt(val);
        }
        Logger.d("param: Min "+ this.MIN);
        // Size of the large packet  
        if ((val = params.get("max")) != null
            && val.length() > 0 && Integer.parseInt(val) > 0) {
          this.MAX = Integer.parseInt(val);
        }        
        Logger.d("param: MAX "+ this.MAX);
        // Echo server port
        if ((val = params.get("port")) != null
            && val.length() > 0 && Integer.parseInt(val) > 0) {
          this.port = Integer.parseInt(val);
        }
        Logger.d("param: port "+ this.port);
        // Number of tests to run from the RRC test
        if ((val = params.get("size")) != null
            && val.length() > 0 && Integer.parseInt(val) > 0) {
          this.size = Integer.parseInt(val);
        }
        Logger.d("param: size "+ this.size);
        // Whether or not to run the DNS test
        if ((val = params.get("dns")) != null
            && val.length() > 0) {
          this.DNS = Boolean.parseBoolean(val);
        }   
        Logger.d("param: DNS "+ this.DNS);
        // Whether or not to run the HTTP test
        if ((val = params.get("http")) != null
            && val.length() > 0) {
          this.HTTP = Boolean.parseBoolean(val);
        }   
        Logger.d("param: HTTP "+ this.HTTP);  
        // Whether or not to run the TCP test
        if ((val = params.get("tcp")) != null
            && val.length() > 0) {
          this.TCP = Boolean.parseBoolean(val);
        } 
        Logger.d(params.get("rrc"));
        Logger.d("param: TCP "+ this.TCP);
        // Whether or not to run the RRC inference task
        if ((val = params.get("rrc")) != null
            && val.length() > 0) {
          this.RRC = Boolean.parseBoolean(val);
        }
        Logger.d("param: RRC "+ this.RRC);
        // Whether the RRC result is visible to users
        if ((val = params.get("result_visibility")) != null
                && val.length() > 0) {
          this.RESULT_VISIBILITY = Boolean.parseBoolean(val);
        }
        Logger.d("param: visibility "+ this.RESULT_VISIBILITY);
        // How many times to retry a test when interrupted by background traffic
        if ((val = params.get("giveup_threshhold")) != null
            && val.length() > 0 && Integer.parseInt(val) > 0) {
          this.GIVEUP_THRESHHOLD = Integer.parseInt(val);
        }
        Logger.d("param: GIVEUP_THRESHHOLD "+ this.GIVEUP_THRESHHOLD);

        // Default assumed timers for the upper layer tests (HTTP, DNS, TCP),
        // in units of GRANULARITY.  These are set via a comma-separated list
        // of numbers.
        if ((val = params.get("default_demotion_timers")) != null
            && val.length() > 0) {
          String[] times_string = val.split("\\s*,\\s*");
          List<String> stringList = new ArrayList<String>(Arrays.asList(times_string));
          List<Integer> intList = new ArrayList<Integer>();
          Iterator<String> iterator = stringList.iterator();
          while (iterator.hasNext()) {
            intList.add(Integer.parseInt(iterator.next()));
          }
          times = (Integer[])intList.toArray();
          
        }

        if (times == null) {
          times = defaultTimesULTasks;  
        }
      } catch (NumberFormatException e) {
        throw new InvalidParameterException(
            " RRCTask cannot be created due to invalid params");
      }
      
      if (size == 0) {
        // 31 tests, by default.  From 0s to 15s inclusive, in half-second intervals.
        size = 31;  
      }
    }
   

    /**
     * For the arrays holding the results for the upper layer tests, we need to 
     * initialize them to be the same size as the number of tests we run.
     * -1 means uninitialized.  
     */ 
    public void initializeExtraTaskResults(int size) {
      httpTest = new int[size];
      dnsTest = new int[size];
      tcpTest = new int[size];
      for (int i = 0; i < size; i++) {
        httpTest[i] = -1;
        dnsTest[i] = -1;
        tcpTest[i] = -1;
      }
      runUpperLayerTests = true;
    }
    
    public void setHttp(int i, int val) throws MeasurementError {
      if (!runUpperLayerTests) {
        throw new MeasurementError("Data class not initialized");
      }
      httpTest[i] = val;     
    }
    
    public void setTcp(int i, int val) throws MeasurementError {
      if (!runUpperLayerTests) {
        throw new MeasurementError("Data class not initialized");
      }
      tcpTest[i] = val;      
    }
    
    public void setDns(int i, int val) throws MeasurementError {
      if (!runUpperLayerTests) {
        throw new MeasurementError("Data class not initialized");
      }
      dnsTest[i] = val;      
    }
  }
  
  public static class RrcTestData {
    // Each of these is a list of results indexed by the test number.
    // Tests are performed in order with increasing inter-packet intervals
    // and results and data about the tests are stored here.
    
    // Round-trip times, in ms
	int[] rttsSmall;
    int[] rttsLarge;
    // Packets lost for each test
    int[] packetsLostSmall;
    int[] packetsLostLarge;
    // Signal strengths at time of each test
    int[] signalStrengthSmall;
    int[] signalStrengthLarge;
    // Error Counts from each test
    int[] errorCountSmall;
    int[] errorCountLarge;
    // Unique incrementing value that identifies this set of tests.
    int testId;
    
    public RrcTestData(int size, Context context) {
      size = size + 1;


      rttsSmall = new int[size];
      rttsLarge = new int[size];
      packetsLostSmall = new int[size];
      packetsLostLarge = new int[size];
      signalStrengthSmall = new int[size];
      signalStrengthLarge = new int[size];    
      errorCountSmall = new int[size];
      errorCountLarge = new int[size];
      
      testId = getTestId(context);
   
      // Set default values
      for (int i = 0; i < rttsSmall.length; i++) {
        // 7000 is the cutoff for timeouts.
        // This makes the model-building script treat no data and timeouts the same.
        rttsSmall[i] = 7000;   
        rttsLarge[i] = 7000;
        packetsLostSmall[i] = -1;
        packetsLostLarge[i] = -1;
        signalStrengthSmall[i] = -1;
        signalStrengthLarge[i] = -1;
        errorCountSmall[i] = -1;
        errorCountLarge[i] = -1;       
      }
    }

    public int testId() {
      return testId;
    }
    
    public String[] toJSON(String networktype, String phone_id) {
      String[] returnval = new String[rttsSmall.length];
      try {
        for (int i = 0; i < rttsSmall.length; i++) {
          JSONObject subtest = new JSONObject();  
          subtest.put("rtt_low", rttsSmall[i]);        
          subtest.put("rtt_high", rttsLarge[i]);
          subtest.put("lost_low", packetsLostSmall[i]);
          subtest.put("lost_high", packetsLostLarge[i]);
          subtest.put("signal_low", signalStrengthSmall[i]);
          subtest.put("signal_high", signalStrengthLarge[i]);
          subtest.put("error_low", errorCountSmall[i]);
          subtest.put("error_high", errorCountLarge[i]);
          subtest.put("network_type", networktype);
          subtest.put("time_delay", i);
          subtest.put("test_id", testId);
          subtest.put("phone_id", phone_id);
          returnval[i] = subtest.toString();
        }
      } catch (JSONException e) {
        Logger.e("Error converting RRC data to JSON");
      }
      return returnval;
    }
    
    public void deleteItem(int i){
      rttsSmall[i] = -1;
      rttsLarge[i] = -1;
      packetsLostSmall[i] = -1;
      packetsLostLarge[i] = -1;
      signalStrengthSmall[i] = -1;
      signalStrengthLarge[i] = -1;
      errorCountSmall[i] = -1;
      errorCountLarge[i] = -1;
    }
    
    public void updateAll(int index, int rtt_max, int rtt_min, 
        int num_packets_lost_max, int num_packets_lost_min, 
        int error_high, int error_low, int signal_high, int signal_low) {
      this.rttsLarge[index] = (int) rtt_max;
      this.rttsSmall[index] = (int) rtt_min;
      this.packetsLostLarge[index] = num_packets_lost_max;
      this.packetsLostSmall[index] = num_packets_lost_min;
      this.errorCountLarge[index] = error_high; 
      this.errorCountSmall[index] = error_low;
      this.signalStrengthLarge[index] = signal_high; 
      this.signalStrengthSmall[index] = signal_low;
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

  /**
   * Helper function to construct MeasurementResults to submit to the server
   * @param desc
   * @return
   */
  private MeasurementResult constructResultStandard(RRCDesc desc) {
    PhoneUtils phoneUtils = PhoneUtils.getPhoneUtils();
    boolean success = true;
    MeasurementResult result = new MeasurementResult(
        phoneUtils.getDeviceInfo().deviceId,
        phoneUtils.getDeviceProperty(), RRCTask.TYPE,
        System.currentTimeMillis() * 1000, success,
        this.measurementDesc);

    if (desc.runUpperLayerTests) {
      result = desc.getResults(result);
    }

    Logger.i(MeasurementJsonConvertor.toJsonString(result));
    return result;
  }
  
  /**
   * The core RRC inference functionality is in this function.  
   * The steps involved can be summarized as follows:
   *    1.  Fetch the last model generated by the server, if it exists.
   *    2.  Check we are on a cellular network, otherwise abort.
   *    3.  Inform all other tasks that they should delay network traffic
   *        until later.
   *    4.  For every upper layer test, if upper layer tests and that test specifically
   *        are enabled, run that test.
   *        
   * @return
   * @throws MeasurementError
   */
  private RRCDesc runInferenceTests() throws MeasurementError {

    Checkin checkin = new Checkin(context);

    // Fetch the existing model from the server, if it exists
    RRCDesc desc = (RRCDesc) measurementDesc;
    PhoneUtils utils = PhoneUtils.getPhoneUtils();
    desc.initializeExtraTaskResults(desc.times.length);
    
    // Check to make sure we are on a valid (i.e. cellular) network
    if (utils.getNetwork() == "UNKNOWN" ||utils.getNetwork() == "WIRELESS" /*|| utils.getCurrentRssi() < 8*/) {
      Logger.d("Returning: network is" + utils.getNetwork() + " rssi " + utils.getCurrentRssi());
      return desc;
    }

    try {
      /*
       *  Suspend all other tasks performed by the app as they can interfere.
       *  Although we have a built-in check where we abort if traffic in the
       *  background interferes, in the past people have scheduled other
       *  tests to be every 5 minutes, which can cause the RRC task to never
       *  successfully complete without having to abort.
       */
      RRCTrafficControl.PauseTraffic();
      
      // If the RRC task is enabled
      if (desc.RRC) {
        RrcTestData data = new RrcTestData(desc.size, context);

        // Set up the connection to the echo server
        Logger.d("Active inference: about to begin");
        Logger.d(desc.echoHost + ":" + desc.port);
        InetAddress serverAddr = InetAddress.getByName(desc.echoHost);
        
        // Perform the RRC timer and latency inference task 
        Logger.d("Demotion inference: about to begin");
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

      // Check if the upper layer tasks are enabled
      if (desc.runUpperLayerTests) {
        if (desc.DNS) {
          // TODO(Haokun): delete after debugging
          Logger.w("Start DNS task");
          // Test the dependence of DNS latency on the RRC state, using
          // the previously constructed model if available.
          runDnsTest(desc.times, desc);
        }
        this.progress = Math.min(Config.MAX_PROGRESS_BAR_VALUE, 60);
        if (desc.TCP) {
          // TODO(Haokun): delete after debugging
          Logger.w("Start TCP task");
          // Test the dependence of TCP latency on the RRC state.
          runTCPHandshakeTest(desc.times, desc);           
        }
        this.progress = Math.min(Config.MAX_PROGRESS_BAR_VALUE, 80);
        if (desc.HTTP) {
          // TODO(Haokun): delete after debugging
          Logger.w("Start HTTP task");
          // Test the dependence of HTTP latency on the RRC state.
          runHTTPTest(desc.times, desc);         
        }
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
  
  /**
   * The "times" are the inter-packet intervals at which to run the test.
   * Ideally these should be based on the model constructed by the server,
   * a default assumed value is used in their absence.
   * 
   * Based on the time it takes to load a response from the page.
   * 
   * This test is not currently as accurate as the other tests, for reasons
   * described below.
   * 
   * @param times
   * @param desc
   */
  private void runHTTPTest(final Integer[] times, RRCDesc desc) {
    /*
     * Length of time it takes to request and read in a page.
     * 
     * 
     * 
     */
    
    Logger.d("Active inference HTTP test: about to begin");
    if (times.length != desc.httpTest.length) {
    	desc.httpTest = new int[times.length];
    }
    long startTime = 0;
    long endTime = 0;
    try {
      for (int i = 0; i < times.length; i++) {
        // We try until we reach a threshhold or until there is no
        // competing traffic.
        for (int j = 0; j < desc.GIVEUP_THRESHHOLD; j++) {
          // Sometimes the network can change in the middle of a test
          checkIfWifi();
          if (stop) {
            return;
          }
          /*
           *  We keep track of the packets sent at the beginning and end
           *  of the test so we can detect if there is competing traffic
           *  anywhere on the phone.
           */
          
          int[] packets_first = getPacketsSent();  
          
          // Initiate the desired RRC state by sending a large enough packet
          // to go to DCH and waiting for the specified amount of time
          try {
            InetAddress serverAddr; 
            serverAddr = InetAddress.getByName(desc.echoHost); 
                sendPacket(serverAddr, desc.MAX, null, desc);             
            waitTime(times[i] * desc.GRANULARITY, true);
          } catch (InterruptedException e1) {
            e1.printStackTrace();
            continue;
          } catch (UnknownHostException e) {
            e.printStackTrace();
            continue;
          } catch (IOException e) {
            e.printStackTrace();
            continue;
          }          
         
          HttpClient client = new DefaultHttpClient();
          HttpGet request = new HttpGet();

          request.setURI(new URI("http://" + desc.target));

          startTime = System.currentTimeMillis();
          HttpResponse response = client.execute(request);
          endTime = System.currentTimeMillis();

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
          
          // We don't actually know how many packets should be sent...
          // However, if there were an unreasonable number, we try again.
          if (rcv_packets <= 100 && sent_packets <=100) {
            Logger.d("No competing traffic, continue");
            break;
          }
        }

        long rtt = endTime - startTime;  
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
   * The "times" are the inter-packet intervals at which to run the test.
   * Ideally these should be based on the model constructed by the server,
   * a default assumed value is used in their absence.
   * 
   * 1. Send a packet to initiate the RRC state desired.  
   * 2. Create a randomly generated host name (to ensure that the host name
   *    is not cached).  I found on some devices that even when you clear
   *    the cache manually, the data remains in the cache. 
   * 3. Time how long it took to look it up.
   * 4. Count the total packets sent, globally on the phone.  If more packets 
   *    were sent than expected, abort and try again.
   * 5. Otherwise, save the data for that test and move to the next inter-packet
   *    interval. 
   * 
   * Test is similar to the approach taken in DnsLookUpTask.java.
   * @param times
   * @param desc
   * @throws MeasurementError
   */
  
  public void runDnsTest(final Integer[] times, RRCDesc desc) throws MeasurementError {
    Logger.d("Active inference DNS test: about to begin");
    if (times.length != desc.dnsTest.length) {
    	desc.dnsTest = new int[times.length];
    }

    long startTime = 0;
    long endTime = 0;  

    // For each inter-packet interval...
    for (int i = 0; i < times.length; i++) {
      // On a failure, try again until a threshold is reached.
      for (int j = 0; j < desc.GIVEUP_THRESHHOLD; j++) {

        // Sometimes the network can change in the middle of a test
        checkIfWifi();
        if (stop) {
          return;
        }
        
        /*  We keep track of the packets sent at the beginning and end
         *  of the test so we can detect if there is competing traffic
         *  anywhere on the phone.
         */
        int[] packetsFirst = getPacketsSent();


        // Initiate the desired RRC state by sending a large enough packet
        // to go to DCH and waiting for the specified amount of time
        try {
          InetAddress serverAddr; 
          serverAddr = InetAddress.getByName(desc.echoHost); 
              sendPacket(serverAddr, desc.MAX, null, desc);             
          waitTime(times[i] * desc.GRANULARITY, true);
        } catch (InterruptedException e1) {
          e1.printStackTrace();
          continue;
        } catch (UnknownHostException e) {
          e.printStackTrace();
          continue;
        } catch (IOException e) {
          e.printStackTrace();
          continue;
        }
        
        // Create a random URL, to avoid the caching problem
        UUID uuid = UUID.randomUUID();
        String host = uuid.toString()+ ".com";  
        // Start measuring the time to complete the task
        startTime = System.currentTimeMillis();  
        try {
          InetAddress serverAddr = InetAddress.getByName(host);
        } catch (UnknownHostException e) {
          // we do this on purpose! Since it's a fake URL the lookup will fail    
        } catch (IOException e) {
          e.printStackTrace();
        }
        // When we  fail to find the URL, we stop timing
        endTime = System.currentTimeMillis();
        
        // Check how many packets were sent again.  If the expected number
        // of packets were sent, we can finish and go to the next task.
        // Otherwise, we have to try again.
        int[] packetsLast = getPacketsSent();
        int rcvPackets =  (packetsLast[0] - packetsFirst[0]);
        int sentPackets = (packetsLast[1] - packetsFirst[1]);
        if (rcvPackets <= 3  && sentPackets <= 3 && (endTime - startTime) > 10) {
          Logger.d("No competing traffic, continue");       
          break;
        }
        Logger.d("Packets sent: " + rcvPackets + " " + sentPackets);
        Logger.d("Time: " + (endTime - startTime));
      }         
      
      // If we broke out of the try-again loop, the last set of results are
      // valid and we can save them.
      long rtt = endTime - startTime;  
      try {
        desc.setDns(i, (int) rtt);
      } catch (MeasurementError e) {
        e.printStackTrace();
      }
      Logger.d("Time for DNS" + rtt);             
    }
  }
  
  /**
   * Time how long it takes to do a TCP 3-way handshake, starting from
   * the induced RRC state.
   * 
   * 1. Send a packet to initiate the RRC state desired.  
   * 2. Open a TCP connection to the echo host server.
   * 3. Time how long it took to look it up.
   * 4. Count the total packets sent, globally on the phone.  If more packets 
   *    were sent than expected, abort and try again.
   * 5. Otherwise, save the data for that test and move to the next inter-packet
   *    interval. 
   *    
   *    
   * @param times
   * @param desc
   */
  public void runTCPHandshakeTest(final Integer[] times, RRCDesc desc) {
    Logger.d("Active inference TCP test: about to begin");
    if (times.length != desc.tcpTest.length) {
    	desc.tcpTest = new int[times.length];
    }
    long startTime = 0;
    long endTime = 0;

    try {
      // For each inter-packet interval...
      for (int i = 0; i < times.length; i++) {
        // On a failure, try again until a threshhold is reached.
        for (int j = 0; j < desc.GIVEUP_THRESHHOLD; j++) {
          checkIfWifi();
          if (stop) {
            return;
          }

          int[] packetsFirst = getPacketsSent();
          
          // Induce DCH then wait for specified time
          InetAddress serverAddr; 
          serverAddr = InetAddress.getByName(desc.echoHost); 
          sendPacket(serverAddr, desc.MAX, null, desc);             
          waitTime(times[i] * 500, true); 
          
          // begin test.  We test the time to do a 3-way handshake only.
          startTime = System.currentTimeMillis();
          serverAddr = InetAddress.getByName(desc.target);
          // three-way handshake done when socket created
          Socket socket = new Socket(serverAddr, 80);   
          endTime = System.currentTimeMillis();

          // Check how many packets were sent again.  If the expected number
          // of packets were sent, we can finish and go to the next task.
          // Otherwise, we have to try again.
          int[] packetsLast = getPacketsSent();              
          int rcvPackets =  (packetsLast[0] - packetsFirst[0]);
          int sentPackets = (packetsLast[1] - packetsFirst[1]);
          if (rcvPackets <= 5 && sentPackets <=4) {
            Logger.d("No competing traffic, continue");
            socket.close();       
            break;
          }
          socket.close();
          Logger.d("Packets sent: " + rcvPackets + " " + sentPackets);
        }
        long rtt = endTime - startTime;  
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

  /**
   * 
   * For all time intervals specified, go through and perform the RRC inference
   * test.
   * 
   * The way this test works, at a high level, is:
   *    1. Send a large packet to induce DCH (or the equivalent)
   *    2. Wait for an amount of time, t
   *    3. Send a large packet and measure the round-trip time
   *    4. Wait for another amount of time, t
   *    5. Send a small packet and measure the round-trip time 
   *    6. Repeat for all specified values of t.
   *       These start at 0 and increase by GRANULARITY, "size" times.
   *          
   * The size of "small" and "large" packets are defined in the parameters.
   * We observe the total packets sent to make sure there is no interfering
   * traffic.
   * 
   * Packets are UDP packets.
   * 
   * From this, we can infer the timers associated with RRC states.  By 
   * sending a large packet, we induce the highest power state.  Waiting
   * a number of seconds afterwards allows us to demote to the next state.
   * Sending a packet and observing the RTT allows us to infer if a state
   * promotion had to take place.  
   * 
   * FACH is characterized by different state  promotion times for large and 
   * small packets. 
   * 
   * @param serverAddr
   * @param desc
   * @param data
   * @param utils
   * @return
   * @throws InterruptedException
   * @throws IOException
   */
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
      
      // Note that we scale from 0-90 to save some stuff for upper layer tests.
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
    return "[RRC]\n  Echo Server: " + desc.echoHost + "\n  Target: " + desc.target 
        + "\n  Interval (sec): " + desc.intervalSec 
        + "\n  Next run: " + desc.startTime;
  }

  /*********************************************************************
   *                    UTILITIES                       *
   *********************************************************************/
  
  /**
   * 
   * Sleep for the amount of time indicated.
   * 
   * 
   * @param timeToSleep
   * @param useMs Toggles between units of milliseconds for the first parameter (true) and seconds(false).
   * @throws InterruptedException
   */
  public static void waitTime(int timeToSleep, boolean useMs) throws InterruptedException {
    /**
     */
    Logger.d("Wait for n ms: " + timeToSleep);

    if (!useMs) {
      timeToSleep = timeToSleep * 1000;
    }
    Thread.sleep(timeToSleep);
  }
  
/**
 * Sends a bunch of UDP packets of the size indicated and wait for the response.
 * 
 * Counts how long it takes for all the packets to return.  PAckets are currently not
 * labelled: the total time is the time for the first packet to leave until the last
 * packet arrives.  AFter 7000 ms it is assumed packets are lost and the socket times out.
 * In that case, the number of packets lost is recorded.
 * 
 * @param serverAddr server to which to send the packets
 * @param size size of the packets
 * @param num number of packets to send
 * @param packetSize size of the packets sent
 * @param port port to send the packets to
 * @return first value: the amount of time to send all packets and get a response.
 *  second value: number of packets lost, on a timeout.
 * @throws IOException
 */
  public static long[] sendMultiPackets(InetAddress serverAddr, int size, 
      int num, int packetSize, int port) throws IOException {

    long startTime = 0;
    long endTime = 0;
    byte[] buf = new byte[size];
    byte[] rcvBuf = new byte[packetSize];
    long[] retval = {-1, -1};
    long numLost = 0;
    int i = 0;

    DatagramSocket socket = new DatagramSocket();
    DatagramPacket packetRcv = new DatagramPacket(rcvBuf, rcvBuf.length);    
    DatagramPacket packet = new DatagramPacket(buf, buf.length, serverAddr, port);

    try {
        socket.setSoTimeout(7000);
        startTime = System.currentTimeMillis();
        Logger.d("Sending packet, waiting for response ");
        for (i = 0; i < num; i++) {
          socket.send(packet);          
        }
        for (i = 0; i < num; i++) {
          socket.receive(packetRcv); 
          if (i == 0) {

            endTime = System.currentTimeMillis();
          }       
        }
    } catch (SocketTimeoutException e){
      Logger.d("Timed out");
        numLost += (num - i);
        socket.close();
    }
    Logger.d("Sending complete: " + endTime);
    
    retval[0] = endTime - startTime;
    retval[1] = numLost;
    
    return retval;
  }
  
  private static long sendPacket(InetAddress serverAddr, int size, RrcTestData data, RRCDesc desc)  throws IOException {
    return sendPacket(serverAddr, size, desc.MIN, desc.port, data);
  }
  
  /**
   * Send a single packet of the size indicated and wait for a response.
   * 
   * After 7000 ms, time out and return a value of -1 (meaning no response).
   * Otherwise, return the time from when the packet was sent to when a 
   * response was returned by the echo server.
   * 
   * @param serverAddr
   * @param size
   * @param MAX 
   * @param rcvSize size of packets sent from the echo server 
   * @param port
   * @param data
   * @return
   * @throws IOException
   */
  public static long sendPacket(InetAddress serverAddr, int size, int rcvSize,
      int port, RrcTestData data) throws IOException {
    long startTime = 0;
    byte[] buf = new byte[size];
    byte[] rcvBuf = new byte[rcvSize];

    DatagramSocket socket = new DatagramSocket();
    DatagramPacket packetRcv = new DatagramPacket(rcvBuf, rcvBuf.length);
    
    DatagramPacket packet = new DatagramPacket(buf, buf.length, serverAddr, port);

    try {
        socket.setSoTimeout(7000);
        startTime = System.currentTimeMillis();
        Logger.d("Sending packet, waiting for response ");

        socket.send(packet);
        socket.receive(packetRcv);
    } catch (SocketTimeoutException e){
        Logger.d("Timed out, trying again");
        socket.close();
        return -1;
    }
    long endTime = System.currentTimeMillis();
    Logger.d("Sending complete: " + endTime);
    //Log.w(TAG, "ending " + end_time);
    
    return endTime - startTime;
  } 
  
  /**
   * Determine how many packets, so far, have been sent (the contents of /proc/net/dev/).
   * This is a global value.  We use this to determine if any other app anywhere on the
   * phone may have sent interfering traffic that might have changed the RRC state 
   * without our knowledge.
   * @return
   */
  public static int[] getPacketsSent() {
    int[] retval = {-1, -1};
    try {
      Process process = Runtime.getRuntime().exec("cat /proc/net/dev");
      BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
      String line = bufferedReader.readLine();
      // Look for a specific string in /proc/net/dev listing the number of packets sent
      Pattern r = Pattern.compile("[1-9][0-9]+");
      while (line != null && !( !line.contains("lo:") && 
          !line.contains("rmnet_usb2") && !line.contains("rmnet_usb1") 
          && r.matcher(line).find())) {
        line = bufferedReader.readLine();
      }
      if (line == null) {
        Logger.d("No data found for data sent string");
        return retval;
      }
      String[] brokenline = line.split("\\s+");
      // /proc/net/dev appears to come in two different formats, with different fields
      // where the value we are interested in can appear.
      if (brokenline.length == 17) { 
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
    return inferDemotionHelper(serverAddr, wait, data, desc, wait, utils); 
  }
  
  /**
   * One component of the RRC inference task.
   *   1.  induce the highest-power RRC state by sending a large packet.
   *   2.  wait the indicated number of seconds.
   *   3.  send a series of 10 large packets at once.  Measure:
   *      a) Time for all packets to be echoed back
   *      b) number of packets lost, if any
   *      c) associated signal strength
   *      d) error rate is currently not implemented
   *   4. Check if the expected number of packets were sent while performing a test.  
   *      If too many packets were sent, abort.
   * 
   * 
   * @param serverAddr
   * @param wait delay between packets
   * @param data
   * @param MAX size of the large packets
   * @param MIN size of the small packets
   * @param port
   * @param granularity
   * @param index
   * @param utils
   * @return
   * @throws IOException
   * @throws InterruptedException
   */
  public static long[] inferDemotionHelper(InetAddress serverAddr, int wait, RrcTestData data, RRCDesc desc, int index, PhoneUtils utils) throws IOException, InterruptedException {
    /**
     * Once we generalize the RRC state inference problem, this is what we will use (since in general, RRC state can differ between large and small packets).
     * Gives the RTT for a large packet and a small packet for a given time after inducing DCH state.  Granularity currently half-seconds but can easily be increased.
     * 
     * Measures packets sent before and after to make sure no extra packets were sent, and retries on a failure.
     * Also checks that there was no timeout and retries on a failure.
     */
    long rttLargePacket = -1;
    long rttSmallPacket = -1;
    int packetsLostSmall = 0;
    int packetsLostLarge = 0;
    
    int errorCountLarge = 0;
    int errorCountSmall = 0;
    int signalStrengthLarge = 0;
    int signalStrengthSmall = 0;
    
    for (int j = 0; j < desc.GIVEUP_THRESHHOLD; j++) {
      Logger.d("Active inference: about to begin helper");
      int[] packetsFirst = getPacketsSent();
      
      // Induce the highest power state
      sendPacket(serverAddr, desc.MAX, desc.MIN, desc.port, data);
      
      // WAit for the specified amount of time
      waitTime(wait*desc.GRANULARITY, true);
      
      // Send a bunch of large packets, all at once, and take measurements on the result
      signalStrengthLarge = utils.getCurrentRssi();
      long[] retval = sendMultiPackets(serverAddr, desc.MAX, 10, desc.MIN, desc.port);
      packetsLostSmall = (int) retval[1];
      rttLargePacket = retval[0];
      
      // wait for the specified amount of time
      waitTime(wait*desc.GRANULARITY, true);
      
      // Send a bunch of small packets, all at once, and take measurements on the result
      signalStrengthSmall = utils.getCurrentRssi();      
      retval = sendMultiPackets(serverAddr, desc.MIN, 10, desc.MIN, desc.port);
      packetsLostLarge = (int) retval[1];
      rttSmallPacket = retval[0];

      
      int[] packetsLast = getPacketsSent();
      
      int rcvPacketCount =  (packetsLast[0] - packetsFirst[0]);
      int sentPacketCount = (packetsLast[1] - packetsFirst[1]);
      
      if (rcvPacketCount <= 21 && sentPacketCount == 21) {

        Logger.d("No competing traffic, continue");
        break;
      }
      Logger.d("Try again. 21 expected, packets received:" + (packetsLast[0] - packetsFirst[0]) + " Packets sent: " + (packetsLast[1] - packetsFirst[1]));
    }

    Logger.d("3G demotion, lower bound: rtts are:" + rttLargePacket + " " + rttSmallPacket + " " + packetsLostSmall + " " + packetsLostLarge);

    long[] retval = {rttLargePacket, rttSmallPacket};
    data.updateAll(index, (int)rttLargePacket, (int)rttSmallPacket, packetsLostSmall, packetsLostLarge, errorCountLarge, errorCountSmall, signalStrengthLarge, signalStrengthSmall);

    return retval;
  }
  
  /**
   * Keep a global counter that labels each test with a unique, increasing integer.
   * @param context
   * @return
   */
  public static synchronized int getTestId(Context context) {
    SharedPreferences prefs = context.getSharedPreferences("test_ids", Context.MODE_PRIVATE);
    int testid = prefs.getInt("test_id", 0) + 1;
    SharedPreferences.Editor editor = prefs.edit();
    editor.putInt("test_id", testid);
    editor.commit();
    return testid;    
  }
  
  /**
   * If on wifi, suspend the task until we go back to the cellular network.
   * Use exponential back-off to calculate the wait time, with a limit of 500 s.
   * Once the limit is reached, unpause other tasks.
   * Repause traffic if we are good to resume again.
   * 
   * @return
   */  
  public void checkIfWifi() {
    PhoneUtils phoneUtils = PhoneUtils.getPhoneUtils();
    
    int timeToWait = 10;
    while (true) {
      if (phoneUtils.getNetwork() != PhoneUtils.NETWORK_WIFI) {
        RRCTrafficControl.PauseTraffic();
        return;
      }
      if (stop) {
        return;
      }
      
      Logger.d("RRCTask: on Wifi, try again later:" + phoneUtils.getNetwork());
      if (timeToWait < 500) { // 500s, or a bit over 8 minutes.
        timeToWait = timeToWait * 2;        
      } else {
        // if it's taking a while, stop pausing traffic
        RRCTrafficControl.UnPauseTraffic();
      }
      try {
        waitTime(timeToWait, false);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }
}

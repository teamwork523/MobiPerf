// Copyright 2012 RobustNet Lab, University of Michigan. All Rights Reserved.
package com.udpmeasurement.test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;

import org.junit.Test;

import com.udpmeasurement.ClientIdentifier;
import com.udpmeasurement.Config;
import com.udpmeasurement.MeasurementError;
import com.udpmeasurement.MeasurementPacket;
import com.udpmeasurement.UDPReceiver;


/**
 * @author Hongyi Yao
 * Unit test for packet processing
 */
public class TestProcessPacket {
  private UDPReceiver tmpReceiver;
  private Method processPacket;
  private MeasurementPacket packet;
  
  /**
   * Create the receiver class, the reflection method for processPacket due to
   * its visability and a received packet 
   * @throws NoSuchMethodException
   * @throws SecurityException
   * @throws UnknownHostException
   */
  private void init()
      throws NoSuchMethodException, SecurityException, UnknownHostException {
    tmpReceiver = null;
    try {
      tmpReceiver = new UDPReceiver(3131);
    } catch (MeasurementError e) {
      e.printStackTrace();
    }
    
    processPacket = UDPReceiver.class.getDeclaredMethod("processPacket",
                                        new Class[]{MeasurementPacket.class});
    processPacket.setAccessible(true);
    
    InetAddress addr = InetAddress.getByName("192.168.1.1");
    int port = 1234;
    ClientIdentifier id1 = new ClientIdentifier(addr, port);
    byte[] rawData = new byte[Config.DEFAULT_UDP_PACKET_SIZE];
    packet = null;
    try {
      packet = new MeasurementPacket(id1, rawData);
    } catch (MeasurementError e) {
      e.printStackTrace();
    }
  }
  
  @Test(expected = MeasurementError.class)
  public void TestProcessPacket_Request_shortPacket() 
      throws Throwable {
    init();
    
    packet.type = Config.PKT_REQUEST;
    packet.burstCount = 2;  // 1 <= burstCount <= MAX_BURSTCOUNT
    packet.packetSize = Config.MIN_PACKETSIZE - 1; // short packet!
    
    try {
      processPacket.invoke(tmpReceiver, packet);
    } catch (InvocationTargetException e) {
      // InvocationTargetException wrapped the real cause, just unwrap it
      throw e.getCause();
    } finally {
      tmpReceiver.socket.close();
    }
  }

  @Test(expected = MeasurementError.class)
  public void TestProcessPacket_Request_longPacket() 
      throws Throwable {
    init();
    
    packet.type = Config.PKT_REQUEST;
    packet.burstCount = 2;  // 1 <= burstCount <= MAX_BURSTCOUNT
    packet.packetSize = Config.MAX_PACKETSIZE + 1; // long packet!
    
    try {
      processPacket.invoke(tmpReceiver, packet);
    } catch (InvocationTargetException e) {
      // InvocationTargetException wrapped the real cause, just unwrap it
      throw e.getCause();
    } finally {
      tmpReceiver.socket.close();
    }
  }

  @Test(expected = MeasurementError.class)
  public void TestProcessPacket_Request_NegBurst() 
      throws Throwable {
    init();
    
    packet.type = Config.PKT_REQUEST;
    packet.burstCount = -1;  // burstCount < 1!
    // MIN_PACKETSIZE <= packetSize <= MAX_PACKETSIZE
    packet.packetSize = Config.MAX_PACKETSIZE + 1; 
    
    try {
      processPacket.invoke(tmpReceiver, packet);
    } catch (InvocationTargetException e) {
      // InvocationTargetException wrapped the real cause, just unwrap it
      throw e.getCause();
    } finally {
      tmpReceiver.socket.close();
    }
  }
  

  @Test(expected = MeasurementError.class)
  public void TestProcessPacket_Request_HugeBurst() 
      throws Throwable {
    init();
    
    packet.type = Config.PKT_REQUEST;
    // burstCount > MAX_BURSTCOUNT!
    packet.burstCount = Config.MAX_BURSTCOUNT + 1;
    // MIN_PACKETSIZE <= packetSize <= MAX_PACKETSIZE
    packet.packetSize = Config.MAX_PACKETSIZE + 1;
    
    try {
      processPacket.invoke(tmpReceiver, packet);
    } catch (InvocationTargetException e) {
      // InvocationTargetException wrapped the real cause, just unwrap it
      throw e.getCause();
    } finally {
      tmpReceiver.socket.close();
    }
  }
  

  @Test(expected = MeasurementError.class)
  public void TestProcessPacket_Data_SeqChange() 
      throws Throwable {
    init();
    
    packet.type = Config.PKT_DATA;
    packet.burstCount = 16;  // 1 <= burstCount <= MAX_BURSTCOUNT
    // MIN_PACKETSIZE <= packetSize <= MAX_PACKETSIZE
    packet.packetSize = Config.DEFAULT_UDP_PACKET_SIZE;
    packet.packetNum = 0;
    packet.seq = 1024;
    
    try {
      processPacket.invoke(tmpReceiver, packet);
    } catch (InvocationTargetException e) {
      // InvocationTargetException wrapped the real exception, just unwrap it
      throw e.getCause();
    } finally {
      tmpReceiver.socket.close();
    }
    
    packet.packetNum = 1;
    packet.seq = 2048;  // 2048 != 1024
    
    try {
      processPacket.invoke(tmpReceiver, packet);
    } catch (InvocationTargetException e) {
      // InvocationTargetException wrapped the real exception, just unwrap it
      throw e.getCause();
    } finally {
      tmpReceiver.socket.close();
    }
  }
  
}

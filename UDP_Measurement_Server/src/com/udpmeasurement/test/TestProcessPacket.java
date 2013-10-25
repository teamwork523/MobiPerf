package com.udpmeasurement.test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;

import org.junit.Test;

import com.udpmeasurement.ClientIdentifier;
import com.udpmeasurement.GlobalFunctionAndConstant;
import com.udpmeasurement.MeasurementError;
import com.udpmeasurement.MeasurementPacket;
import com.udpmeasurement.UDPReceiver;


public class TestProcessPacket {
  private UDPReceiver tmpReceiver;
  private Method processPacket;
  private MeasurementPacket packet;
  
  private void init() throws NoSuchMethodException, SecurityException, UnknownHostException {
    tmpReceiver = null;
    try {
      tmpReceiver = new UDPReceiver(3131);
    } catch (MeasurementError e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    
    processPacket = UDPReceiver.class.getDeclaredMethod("processPacket", new Class[]{MeasurementPacket.class});
    processPacket.setAccessible(true);
    
    InetAddress addr = InetAddress.getByName("192.168.1.1");
    int port = 1234;
    ClientIdentifier id1 = new ClientIdentifier(addr, port);
    byte[] rawData = new byte[GlobalFunctionAndConstant.DEFAULT_UDP_PACKET_SIZE];
    packet = null;
    try {
      packet = new MeasurementPacket(id1, rawData);
    } catch (MeasurementError e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }
  
  @Test(expected = MeasurementError.class)
  public void TestProcessPacket_Request_shortPacket() 
      throws Throwable {
    init();
    
    packet.type = GlobalFunctionAndConstant.PKT_REQUEST;
    packet.burstCount = 2;  // 1 <= burstCount <= MAX_BURSTCOUNT
    packet.packetSize = GlobalFunctionAndConstant.MIN_PACKETSIZE - 1; // short packet!
    
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
    
    packet.type = GlobalFunctionAndConstant.PKT_REQUEST;
    packet.burstCount = 2;  // 1 <= burstCount <= MAX_BURSTCOUNT
    packet.packetSize = GlobalFunctionAndConstant.MAX_PACKETSIZE + 1; // long packet!
    
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
    
    packet.type = GlobalFunctionAndConstant.PKT_REQUEST;
    packet.burstCount = -1;  // burstCount < 1!
    packet.packetSize = GlobalFunctionAndConstant.MAX_PACKETSIZE + 1; // MIN_PACKETSIZE <= packetSize <= MAX_PACKETSIZE
    
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
    
    packet.type = GlobalFunctionAndConstant.PKT_REQUEST;
    packet.burstCount = GlobalFunctionAndConstant.MAX_BURSTCOUNT + 1;  // burstCount > MAX_BURSTCOUNT!
    packet.packetSize = GlobalFunctionAndConstant.MAX_PACKETSIZE + 1; // MIN_PACKETSIZE <= packetSize <= MAX_PACKETSIZE
    
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
    
    packet.type = GlobalFunctionAndConstant.PKT_DATA;
    packet.burstCount = 16;  // 1 <= burstCount <= MAX_BURSTCOUNT
    packet.packetSize = GlobalFunctionAndConstant.DEFAULT_UDP_PACKET_SIZE; // MIN_PACKETSIZE <= packetSize <= MAX_PACKETSIZE
    packet.packetNum = 0;
    packet.seq = 1024;
    
    try {
      processPacket.invoke(tmpReceiver, packet);
    } catch (InvocationTargetException e) {
      // InvocationTargetException wrapped the real cause, just unwrap it
      throw e.getCause();
    } finally {
      tmpReceiver.socket.close();
    }
    
    packet.packetNum = 1;
    packet.seq = 2048;  // 2048 != 1024
    
    try {
      processPacket.invoke(tmpReceiver, packet);
    } catch (InvocationTargetException e) {
      // InvocationTargetException wrapped the real cause, just unwrap it
      throw e.getCause();
    } finally {
      tmpReceiver.socket.close();
    }
  }
  
}

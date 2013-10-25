package com.udpmeasurement.test;

import static org.junit.Assert.*;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.junit.Test;

import com.udpmeasurement.ClientIdentifier;
import com.udpmeasurement.GlobalFunctionAndConstant;
import com.udpmeasurement.MeasurementError;
import com.udpmeasurement.MeasurementPacket;

public class TestMeasurementPacket {
  @Test
  public void TestPacketPackAndUnpack() throws UnknownHostException {
    InetAddress addr = InetAddress.getByName("192.168.1.1");
    int port = 1234;
    ClientIdentifier id1 = new ClientIdentifier(addr, port);
    byte[] rawData = new byte[GlobalFunctionAndConstant.DEFAULT_UDP_PACKET_SIZE];
    for ( int i = 0; i < rawData.length; i++ ) {
      rawData[i] = 8;
    }
    MeasurementPacket packet = null;
    try {
      packet = new MeasurementPacket(id1, rawData);
    } catch (MeasurementError e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    byte[] newData = null;
    try {
      newData = packet.getByteArray();
    } catch (MeasurementError e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    for ( int i = 0; i < newData.length; i++ ) {
      assertEquals("Byte should not change after pack and unpack", newData[i], rawData[i]);
    }
  }

}

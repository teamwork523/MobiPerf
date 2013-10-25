package com.udpmeasurement;


import java.io.*;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;


public class UDPReceiver extends GlobalFunctionAndConstant implements Runnable{

  public DatagramSocket socket;
  private DatagramPacket receivedPacket;
  private byte[] receivedBuffer;

  private HashMap<ClientIdentifier, ClientRecord> clientMap;

  public UDPReceiver(int port) throws MeasurementError {
    try {
      socket = new DatagramSocket(port);
    } catch (SocketException e) {
      throw new MeasurementError("Failed opening and binding socket!");
    }

    receivedBuffer = new byte[BUFSIZE];
    receivedPacket = new DatagramPacket(receivedBuffer, receivedBuffer.length);

    clientMap = new HashMap<ClientIdentifier, ClientRecord>();
  }

  @Override
  public void run() {
    System.out.println("Receiver thread is running...");

    while ( true ) {
      try {
        // get client's request
        socket.setSoTimeout(DEFAULT_TIMEOUT);
        socket.receive(receivedPacket);
        ClientIdentifier clientId = new ClientIdentifier(receivedPacket.getAddress(), receivedPacket.getPort()); 
        logmsg("Received message from " + clientId.toString());

        // processing message
        try {
          MeasurementPacket packet = new MeasurementPacket(
              clientId,
              receivedPacket.getData());
          processPacket(packet);
        } catch (MeasurementError e) {
          logmsg("Error processing message: " + e.getMessage());
        }

      } catch (IOException e) {
        try {
          removeOldRecord();
        } catch (MeasurementError e1) {
          logmsg("Error sending response when timeout: " + e.getMessage());
        }
      }

    }
  }

  private void processPacket(MeasurementPacket packet) throws MeasurementError {
    if ( packet.type != PKT_REQUEST && packet.type != PKT_DATA ) {
      // Send error packet back
      logmsg("Received malformed packet! Type " + packet.type);
      sendPacket(PKT_ERROR, packet.clientId, null);
    }
    else if ( packet.type == PKT_REQUEST ) {
      // Create a new thread to burst udp packets
      logmsg("Receive packet request");      

      ClientRecord clientRecord = new ClientRecord();
      clientRecord.burstCount = packet.burstCount;
      clientRecord.packetSize = packet.packetSize;
      clientRecord.udpInterval = packet.udpInterval;     

      // Todo(Hongyi): setup similar check in the client side
      if ( clientRecord.burstCount < 1 ) {
        throw new MeasurementError("Burst count should be positive, not " + clientRecord.burstCount);
      }  
      if ( clientRecord.burstCount > MAX_BURSTCOUNT ) {
        throw new MeasurementError("Burst count should be not bigger than " + MAX_BURSTCOUNT + ", not " + clientRecord.burstCount);
      }
      if ( clientRecord.packetSize < MIN_PACKETSIZE ) {
        throw new MeasurementError("Request packet size " + clientRecord.packetSize + " shorter than min packet size " + MIN_PACKETSIZE);
      }
      if ( clientRecord.packetSize > MAX_PACKETSIZE ) {
        throw new MeasurementError("Request packet size " + clientRecord.packetSize + " longer than max packet size " + MAX_PACKETSIZE);
      }
      
      // Create a new thread for downlink burst. Otherwise the uplink burst at the same time
      // may be blocked and lead to wrong delay estimation 
      RequestHandler respHandle = new RequestHandler(socket, packet.clientId, clientRecord);
      new Thread(respHandle).start();
    }
    else  { // packetType == PKT_DATA
      // Look up the client map to find the corresponding recorder
      // , or create a new one. Then record the packet's content
      // After received all the packets in a burst or timeout,
      // send a request back

      ClientRecord clientRecord;
      if ( clientMap.containsKey(packet.clientId) ) {
        clientRecord = clientMap.get(packet.clientId);
        int packetNumber = packet.packetNum;
        long offsetedDelay = System.currentTimeMillis() - packet.timestamp;
        int seq = packet.seq;

        // seq must stay the same for one burst
        if ( seq == clientRecord.seq ) {
          clientRecord.receivedNumberList.add(packetNumber);
          clientRecord.offsetedDelayList.add(offsetedDelay);
          clientRecord.lastTimestamp = System.currentTimeMillis();
        }
        else {
          logmsg("client sent a different sequence number! old " + clientRecord.seq + " => " + "new " + seq);
          sendPacket(PKT_ERROR, packet.clientId, null);
          clientMap.remove(packet.clientId);
          throw new MeasurementError( packet.clientId.toString() + " send a new seq " + seq + " different from current seq " + clientRecord.seq);
        }
      }
      else {
        clientRecord = new ClientRecord();
        clientRecord.burstCount = packet.burstCount;
        clientRecord.receivedNumberList.add(packet.packetNum);
        clientRecord.offsetedDelayList.add(System.currentTimeMillis() - packet.timestamp);
        clientRecord.packetSize = packet.packetSize;
        clientRecord.seq = packet.seq;
        clientRecord.lastTimestamp = System.currentTimeMillis();

        clientMap.put(packet.clientId, clientRecord);
      }

      logmsg("Receive data packet s:" + clientRecord.seq + " b:" + clientRecord.burstCount
          + " p:" + clientRecord.receivedNumberList.get(clientRecord.receivedNumberList.size() - 1));

      if ( clientRecord.receivedNumberList.size() == clientRecord.burstCount ) {
        try {
          sendPacket(PKT_RESPONSE, packet.clientId, clientRecord);
        } catch (MeasurementError e) {
          throw e;
        } finally {
          clientMap.remove(packet.clientId);
        }
      }
    }
  }

  private void sendPacket(int type, ClientIdentifier clientId, ClientRecord clientRecord) throws MeasurementError {
    MeasurementPacket packet = new MeasurementPacket(clientId);
    if ( type == PKT_ERROR ) {
      MeasurementPacket errorPacket = packet;
      errorPacket.type = PKT_ERROR;
      errorPacket.packetSize = MIN_PACKETSIZE;
    }
    else if ( type == PKT_DATA ) {
      MeasurementPacket dataPacket = packet;
      dataPacket.type = PKT_DATA;
      dataPacket.burstCount = clientRecord.burstCount;
      dataPacket.packetNum = clientRecord.packetReceived;
      dataPacket.timestamp = System.currentTimeMillis(); 
      dataPacket.packetSize = clientRecord.packetSize;
    }
    else if ( type == PKT_RESPONSE ) {
      MeasurementPacket responsePacket = packet;
      responsePacket.type = PKT_RESPONSE;
      responsePacket.burstCount = clientRecord.burstCount;
      responsePacket.intervalNum = clientRecord.calculateInversionNumber();
      responsePacket.timestamp = clientRecord.calculateJitter();
      responsePacket.packetNum = clientRecord.receivedNumberList.size();
      responsePacket.packetSize = clientRecord.packetSize;
    }

    byte[] sendBuffer = packet.getByteArray();
    DatagramPacket sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length, clientId.addr, clientId.port); 

    try {
      socket.send(sendPacket);
    } catch (IOException e) {
      throw new MeasurementError("Fail to send UDP packet to " + clientId.toString());
    }

    logmsg("Sent response to " + clientId.toString() + " type:" + type + " b:" + packet.burstCount
        + " p:" + packet.packetNum + " i:" + packet.intervalNum + " j:" + packet.timestamp
        + " s:" + packet.packetSize);
  }

  private void removeOldRecord() throws MeasurementError {
    for ( Map.Entry<ClientIdentifier, ClientRecord> entry : clientMap.entrySet() ) {
      sendPacket(PKT_RESPONSE, entry.getKey(), entry.getValue());
    }
    clientMap.clear();
  }
}

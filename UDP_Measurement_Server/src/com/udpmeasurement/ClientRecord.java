package com.udpmeasurement;

import java.util.ArrayList;

public class ClientRecord {
  public int seq;
  public int burstCount;
  public int packetReceived;
  public int packetSize;
  public long lastTimestamp;
  public int udpInterval;
  public ArrayList<Integer> receivedNumberList;
  public ArrayList<Long> offsetedDelayList;  

  public ClientRecord() {
    receivedNumberList = new ArrayList<Integer>();
    offsetedDelayList = new ArrayList<Long>();
  }

  private int combine(Integer[] packetNumList, int start, int mid, int end) {
    int inversionCounter = 0;
    int[] tmp = new int[end - start + 1];
    int pf = start;
    int ps = mid + 1;
    int pt = 0;// the number of sorted elements
    while (pf <= mid && ps <= end)
      if (packetNumList[pf] > packetNumList[ps]) {
        for (int t = pf; t <= mid; t++)
          inversionCounter++;
        tmp[pt++] = packetNumList[ps++];
      } else {
        tmp[pt++] = packetNumList[pf++];
      }
    while (pf <= mid)
      tmp[pt++] = packetNumList[pf++];
    while (ps <= end)
      tmp[pt++] = packetNumList[ps++];
    for (int i = start; i <= end; i++)
      packetNumList[i] = tmp[i - start];
    return inversionCounter;
  }

  private int merge(Integer[] packetNumList, int start, int end) {
    if (start < end) {
      int mid = (start + end) / 2;
      int invLeft = merge(packetNumList, start, mid);
      int invRight = merge(packetNumList, mid + 1, end);
      int invThis = combine(packetNumList, start, mid, end);
      return invLeft + invRight + invThis;
    }
    else {
      return 0;
    }
  }

  public int calculateInversionNumber() {
    Integer[] base = new Integer[receivedNumberList.size()];
    receivedNumberList.toArray(base);

    return merge(base, 0, base.length - 1);
  }

  public long calculateJitter() {
    int size = offsetedDelayList.size();
    if ( size > 1 ) {
      double offsetedDelay_mean = 0;
      for ( long offsetedDelay : offsetedDelayList ) {
        offsetedDelay_mean += (double)offsetedDelay / size;
      }

      double jitter = 0;
      for ( long offsetedDelay : offsetedDelayList ) {
        jitter += ((double)offsetedDelay - offsetedDelay_mean) * ((double)offsetedDelay - offsetedDelay_mean)  / (size - 1);
      }
      jitter = Math.sqrt(jitter);
      
      return (long)jitter;
    }
    else {
      return 0;
    }
  }
}

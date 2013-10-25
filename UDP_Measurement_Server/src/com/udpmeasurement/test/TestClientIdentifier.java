package com.udpmeasurement.test;

import static org.junit.Assert.*;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;

import org.junit.Test;

import com.udpmeasurement.ClientIdentifier;

public class TestClientIdentifier {
  @Test
  public void TestEqual() throws UnknownHostException {
    InetAddress addr = InetAddress.getByName("192.168.1.1");
    int port = 1234;
    ClientIdentifier id1 = new ClientIdentifier(addr, port);
    ClientIdentifier id2 = new ClientIdentifier(addr, port);
    assertEquals("id1 must be identical with id2", id1, id2);
  }
  

  @Test
  public void TestEqualSingleNull() throws UnknownHostException {
    InetAddress addr = InetAddress.getByName("192.168.1.1");
    int port = 1234;
    ClientIdentifier id1 = new ClientIdentifier(addr, port);
    ClientIdentifier id2 = null;
    assertFalse("id1 must not be identical with id2", id1.equals(id2));
  }
  
  @Test
  public void TestHashSet() throws UnknownHostException {
    HashSet<ClientIdentifier> set = new HashSet<ClientIdentifier>();

    InetAddress addr = InetAddress.getByName("192.168.1.1");
    int port = 1234;
    ClientIdentifier id1 = new ClientIdentifier(addr, port);
    ClientIdentifier id2 = new ClientIdentifier(addr, port);
    
    set.add(id1);
    assertTrue("id2 should be in the set", set.contains(id2));
  }

  @Test
  public void TestHashSetCollision() throws UnknownHostException {
    HashSet<ClientIdentifier> set = new HashSet<ClientIdentifier>();

    InetAddress addr = InetAddress.getByName("192.168.1.1");
    int port = 1234;
    ClientIdentifier id1 = new ClientIdentifier(addr, port);
    InetAddress addr2 = InetAddress.getByName("192.168.1.2");
    int port2 = 1233;
    ClientIdentifier id2 = new ClientIdentifier(addr2, port2);
    
    set.add(id1);
    assertFalse("Though id2.hashcode == id1.hashcode, id2 should not be in the set", set.contains(id2));
  }
}

package com.getmusicbee.musicbeewifisync;

import java.net.InetAddress;
import java.util.Iterator;

public interface IpAddressProvider extends Iterable<InetAddress>, Iterator<InetAddress> {
    InetAddress getDeviceAddress();
    String getIpSearchPrefix();
}

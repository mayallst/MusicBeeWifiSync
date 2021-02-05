package com.getmusicbee.musicbeewifisync;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import androidx.annotation.NonNull;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

public class IpAddressProviderImpl implements IpAddressProvider {
    private static final int MAX_IP_IN_SUBNET = 254;
    private AtomicInteger cnt = new AtomicInteger(1);
    private InetAddress deviceIP;
    private String ipPrefix;

    public IpAddressProviderImpl(Context context, InetAddress overrideSearchIP) {
        this.deviceIP = detectDeviceIP(context);
        if (overrideSearchIP != null) {
            this.ipPrefix = getIpPrefix(overrideSearchIP);
        } else {
            this.ipPrefix = getIpPrefix(this.deviceIP);
        }
    }

    private String getIpPrefix(InetAddress deviceIP) {
        if (deviceIP == null || deviceIP.isLoopbackAddress()) {
            return null;
        }
        String[] ipSplit = deviceIP.getHostAddress().split("\\.");
        ipSplit = Arrays.copyOf(ipSplit, 3);
        String ipPrefix = "";
        for (String ipPart : Arrays.asList(ipSplit)) {
            ipPrefix += "" + ipPart + ".";
        }

        return ipPrefix;
    }

    private InetAddress detectDeviceIP(Context context) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(context.WIFI_SERVICE);
        WifiInfo connectionInfo = wifiManager.getConnectionInfo();
        if (connectionInfo == null) {
            return null;
        }
        int ipAddress = connectionInfo.getIpAddress();

        if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
            ipAddress = Integer.reverseBytes(ipAddress);
        }
        byte[] ipByteArray = BigInteger.valueOf(ipAddress).toByteArray();

        try {
            return InetAddress.getByAddress(ipByteArray);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    @NonNull
    @Override
    public Iterator<InetAddress> iterator() {
        return this;
    }

    @Override
    public boolean hasNext() {
        return ipPrefix != null && cnt.get() <= MAX_IP_IN_SUBNET;
    }

    @Override
    public InetAddress next() {
        InetAddress tmp = null;

        try {
            tmp = InetAddress.getByName(ipPrefix + cnt.getAndIncrement());
            if (tmp.equals(deviceIP)) {
                tmp = InetAddress.getByName(ipPrefix + cnt.getAndIncrement());
            }
        } catch (UnknownHostException e) {
            // ignored as only IP format is checked in this case
        }

        return tmp;
    }

    @Override
    public InetAddress getDeviceAddress() {
        return deviceIP;
    }

    @Override
    public String getIpSearchPrefix() {
        return ipPrefix;
    }
}

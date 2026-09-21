package com.koujunxin.channel.api;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

public interface DnsLookup {

    List<InetAddress> resolve(String host) throws UnknownHostException;

    static DnsLookup system() {
        return host -> Arrays.asList(InetAddress.getAllByName(host));
    }
}

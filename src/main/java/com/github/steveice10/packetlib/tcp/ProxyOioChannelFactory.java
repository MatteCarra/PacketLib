package com.github.steveice10.packetlib.tcp;

import java.net.Proxy;
import java.net.Socket;

import io.netty.channel.ChannelFactory;
import io.netty.channel.socket.oio.OioSocketChannel;

public class ProxyOioChannelFactory implements ChannelFactory<OioSocketChannel> {
    private Proxy proxy;

    public ProxyOioChannelFactory(Proxy proxy) {
        this.proxy = proxy;
    }

    @Override
    public OioSocketChannel newChannel() {
        return new OioSocketChannel(new Socket(this.proxy));
    }
}

package com.github.steveice10.packetlib.tcp;

import com.github.steveice10.packetlib.BuiltinFlags;
import com.github.steveice10.packetlib.Client;
import com.github.steveice10.packetlib.ProxyInfo;
import com.github.steveice10.packetlib.packet.PacketProtocol;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.AddressedEnvelope;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DefaultDnsRawRecord;
import io.netty.handler.codec.dns.DefaultDnsRecordDecoder;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.Socks4ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import io.netty.resolver.dns.DnsNameResolver;
import io.netty.resolver.dns.DnsNameResolverBuilder;
import org.minidns.hla.ResolverApi;
import org.minidns.hla.SrvResolverResult;
import org.minidns.record.SRV;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Set;

public class TcpClientSession extends TcpSession {
    private Client client;
    private ProxyInfo proxy;

    private EventLoopGroup group;

    public TcpClientSession(String host, int port, PacketProtocol protocol, Client client, ProxyInfo proxy) {
        super(host, port, protocol);
        this.client = client;
        this.proxy = proxy;
    }

    @Override
    public void connect(boolean wait) {
        if(this.disconnected) {
            throw new IllegalStateException("Session has already been disconnected.");
        } else if(this.group != null) {
            return;
        }

        try {
            this.group = new NioEventLoopGroup();

            final Bootstrap bootstrap = new Bootstrap();
            bootstrap.channel(NioSocketChannel.class);
            bootstrap.handler(new ChannelInitializer<Channel>() {
                @Override
                public void initChannel(Channel channel) throws Exception {
                    getPacketProtocol().newClientSession(client, TcpClientSession.this);

                    channel.config().setOption(ChannelOption.IP_TOS, 0x18);
                    channel.config().setOption(ChannelOption.TCP_NODELAY, false);

                    ChannelPipeline pipeline = channel.pipeline();

                    refreshReadTimeoutHandler(channel);
                    refreshWriteTimeoutHandler(channel);

                    if(proxy != null) {
                        switch(proxy.getType()) {
                            case HTTP:
                                if(proxy.isAuthenticated()) {
                                    pipeline.addFirst("proxy", new HttpProxyHandler(proxy.getAddress(), proxy.getUsername(), proxy.getPassword()));
                                } else {
                                    pipeline.addFirst("proxy", new HttpProxyHandler(proxy.getAddress()));
                                }

                                break;
                            case SOCKS4:
                                if(proxy.isAuthenticated()) {
                                    pipeline.addFirst("proxy", new Socks4ProxyHandler(proxy.getAddress(), proxy.getUsername()));
                                } else {
                                    pipeline.addFirst("proxy", new Socks4ProxyHandler(proxy.getAddress()));
                                }

                                break;
                            case SOCKS5:
                                if(proxy.isAuthenticated()) {
                                    pipeline.addFirst("proxy", new Socks5ProxyHandler(proxy.getAddress(), proxy.getUsername(), proxy.getPassword()));
                                } else {
                                    pipeline.addFirst("proxy", new Socks5ProxyHandler(proxy.getAddress()));
                                }

                                break;
                            default:
                                throw new UnsupportedOperationException("Unsupported proxy type: " + proxy.getType());
                        }
                    }

                    pipeline.addLast("encryption", new TcpPacketEncryptor(TcpClientSession.this));
                    pipeline.addLast("sizer", new TcpPacketSizer(TcpClientSession.this));
                    pipeline.addLast("codec", new TcpPacketCodec(TcpClientSession.this));
                    pipeline.addLast("manager", TcpClientSession.this);
                }
            }).group(this.group).option(ChannelOption.CONNECT_TIMEOUT_MILLIS, getConnectTimeout() * 1000);

            Runnable connectTask = new Runnable() {
                @Override
                public void run() {
                    try {
                        bootstrap.remoteAddress(resolveAddress());

                        ChannelFuture future = bootstrap.connect().sync();
                        if(future.isSuccess()) {
                            while(!isConnected() && !disconnected) {
                                try {
                                    Thread.sleep(5);
                                } catch(InterruptedException e) {
                                }
                            }
                        }
                    } catch(Throwable t) {
                        exceptionCaught(null, t);
                    }
                }
            };

            if(wait) {
                connectTask.run();
            } else {
                new Thread(connectTask).start();
            }
        } catch(Throwable t) {
            exceptionCaught(null, t);
        }
    }

    private SocketAddress resolveAddress() {
        try {
            String name = this.getPacketProtocol().getSRVRecordPrefix() + "._tcp." + this.getHost();

            SrvResolverResult result = ResolverApi.INSTANCE.resolveSrv(name);
            if (result.wasSuccessful()) {
                Set<SRV> srvs = result.getAnswers();
                if(srvs.size() > 0) {
                    SRV srvRecord = srvs.iterator().next();
                    if(srvRecord != null) {
                        String host = srvRecord.target.ace.replaceFirst("\\.$", "");
                        int port = srvRecord.port;
                        return new InetSocketAddress(host, port);
                    }
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }

        return new InetSocketAddress(this.getHost(), this.getPort());
    }

    @Override
    public void disconnect(String reason, Throwable cause) {
        super.disconnect(reason, cause);
        if(this.group != null) {
            this.group.shutdownGracefully();
            this.group = null;
        }
    }
}

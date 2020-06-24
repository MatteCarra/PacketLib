package com.github.steveice10.packetlib.tcp;

import com.github.steveice10.packetlib.Session;
import com.github.steveice10.packetlib.event.session.PacketErrorEvent;
import com.github.steveice10.packetlib.event.session.PacketReceivedEvent;
import com.github.steveice10.packetlib.io.NetInput;
import com.github.steveice10.packetlib.io.NetOutput;
import com.github.steveice10.packetlib.packet.Packet;
import com.github.steveice10.packetlib.tcp.io.ByteBufNetInput;
import com.github.steveice10.packetlib.tcp.io.ByteBufNetOutput;

import java.io.IOException;
import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;

public class TcpPacketCodec extends ByteToMessageCodec<Packet> {
    private Session session;

    public TcpPacketCodec(Session session) {
        this.session = session;
    }

    @Override
    public void encode(ChannelHandlerContext ctx, Packet packet, ByteBuf buf) throws Exception {
        int initial = buf.writerIndex();

        try {
            NetOutput out = new ByteBufNetOutput(buf);

            this.session.getPacketProtocol().getPacketHeader().writePacketId(out, this.session.getPacketProtocol().getOutgoingId(packet));
            packet.write(out);
        } catch(Throwable t) {
            // Reset writer index to make sure incomplete data is not written out.
            buf.writerIndex(initial);

            PacketErrorEvent e = new PacketErrorEvent(this.session, t);
            this.session.callEvent(e);
            if(!e.shouldSuppress()) {
                throw t;
            }
        }
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf buf, List<Object> out) throws Exception {
        int initial = buf.readerIndex();

        try {
            NetInput in = new ByteBufNetInput(buf);

            int id = this.session.getPacketProtocol().getPacketHeader().readPacketId(in);
            if(id == -1) {
                buf.readerIndex(initial);
                return;
            }

            Packet packet = this.session.getPacketProtocol().createIncomingPacket(id);
            try {
                packet.read(in);
            } catch (IllegalArgumentException e) {
                if (e.getStackTrace() == null || e.getStackTrace().length <= 0 || !e.getStackTrace()[0].getClassName().endsWith(".MagicValues")) {
                    throw e;
                }
                System.out.println("Caught IllegalArgumentException " + e.getMessage());
            } catch (IOException e) {
                if(e.getMessage() != null && e.getMessage().equals("Failed to read chunk data.")) {
                    e.printStackTrace();
                } else {
                    throw e;
                }
            }

            if(buf.readableBytes() > 0) {
                try {
                    throw new IllegalStateException("Packet \"" + packet.getClass() + "\" not fully read. " + buf.readableBytes());
                } catch (IllegalStateException e) {
                    e.printStackTrace();
                }
                in.skipReadableBytes();
                return;
            }

            out.add(packet);
        } catch(Throwable t) {
            // Advance buffer to end to make sure remaining data in this packet is skipped.
            buf.readerIndex(buf.readerIndex() + buf.readableBytes());

            PacketErrorEvent e = new PacketErrorEvent(this.session, t);
            this.session.callEvent(e);
            if(!e.shouldSuppress()) {
                throw t;
            }
        }
    }
}

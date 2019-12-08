package com.github.steveice10.packetlib.packet;

import com.github.steveice10.packetlib.io.NetInput;
import com.github.steveice10.packetlib.io.NetOutput;
import com.github.steveice10.packetlib.packet.Packet;

import java.io.IOException;

/**
 * Created by matte on 01/12/2015.
 */
public class SkipPacket implements Packet {
    @Override
    public void read(NetInput in) throws IOException {
        in.skipReadableBytes();
    }

    @Override
    public void write(NetOutput out) throws IOException {
    }

    @Override
    public boolean isPriority() {
        return false;
    }
}

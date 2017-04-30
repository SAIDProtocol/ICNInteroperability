/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.rutgers.winlab.icninteroperability.ip;

import edu.rutgers.winlab.icninteroperability.DemultiplexingEntity;
import java.net.InetSocketAddress;
import java.util.Objects;

/**
 *
 * @author ubuntu
 */
public class DemultiplexingEntityIPDynamic implements DemultiplexingEntity {

    private final InetSocketAddress remoteAddress;

    public DemultiplexingEntityIPDynamic(InetSocketAddress remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

    public InetSocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 13 * hash + Objects.hashCode(this.remoteAddress);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final DemultiplexingEntityIPDynamic other = (DemultiplexingEntityIPDynamic) obj;
        return Objects.equals(this.remoteAddress, other.remoteAddress);
    }

    @Override
    public String toString() {
        return String.format("Demux(DynamicIP){C=%s}", remoteAddress);
    }

}
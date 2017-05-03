/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.rutgers.winlab.icninteroperability.ndn;

import edu.rutgers.winlab.icninteroperability.DemultiplexingEntity;
import java.util.Objects;

/**
 *
 * @author ubuntu
 */
public class DemultiplexingEntityNDNDynamic implements DemultiplexingEntity {

    private final String clientName;
    private final long time;

    public DemultiplexingEntityNDNDynamic(String clientName, long time) {
        this.clientName = clientName;
        this.time = time;
    }

    public String getClientName() {
        return clientName;
    }

    public long getTime() {
        return time;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 23 * hash + Objects.hashCode(this.clientName);
        hash = 23 * hash + (int) (this.time ^ (this.time >>> 32));
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
        final DemultiplexingEntityNDNDynamic other = (DemultiplexingEntityNDNDynamic) obj;
        if (this.time != other.time) {
            return false;
        }
        return Objects.equals(this.clientName, other.clientName);
    }

    @Override
    public String toString() {
        return String.format("Demux(DynamicIP){C=%s,T=%d}", clientName, time);
    }

}

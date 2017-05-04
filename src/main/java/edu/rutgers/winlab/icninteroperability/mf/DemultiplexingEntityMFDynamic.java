/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.rutgers.winlab.icninteroperability.mf;

import edu.rutgers.winlab.icninteroperability.DemultiplexingEntity;
import edu.rutgers.winlab.jmfapi.GUID;
import java.util.Objects;

/**
 *
 * @author ubuntu
 */
public class DemultiplexingEntityMFDynamic implements DemultiplexingEntity {

    private final GUID clientGUID;
    private final long clientReqID;

    public DemultiplexingEntityMFDynamic(GUID clientGUID, long clientReqID) {
        this.clientGUID = clientGUID;
        this.clientReqID = clientReqID;
    }

    public GUID getClientGUID() {
        return clientGUID;
    }

    public long getClientReqID() {
        return clientReqID;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 79 * hash + Objects.hashCode(this.clientGUID);
        hash = 79 * hash + (int) (this.clientReqID ^ (this.clientReqID >>> 32));
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
        final DemultiplexingEntityMFDynamic other = (DemultiplexingEntityMFDynamic) obj;
        if (this.clientReqID != other.clientReqID) {
            return false;
        }
        if (!Objects.equals(this.clientGUID, other.clientGUID)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return String.format("Demux(DynamicMF){GUID=%s,reqID=%d}", clientGUID, clientReqID);
    }

}

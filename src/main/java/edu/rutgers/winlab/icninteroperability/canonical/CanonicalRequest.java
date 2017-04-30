/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.rutgers.winlab.icninteroperability.canonical;

import edu.rutgers.winlab.icninteroperability.DataHandler;
import edu.rutgers.winlab.icninteroperability.DemultiplexingEntity;

/**
 *
 * @author ubuntu
 */
public class CanonicalRequest {

    private final String destDomain;
    private final DemultiplexingEntity demux;
    private final String targetName;
    private final DataHandler dataHandler;

    public CanonicalRequest(String destDomain, DemultiplexingEntity demux, String targetName, DataHandler dataHandler) {
        this.destDomain = destDomain;
        this.demux = demux;
        this.dataHandler = dataHandler;
        this.targetName = targetName;
    }

    public String getTargetName() {
        return targetName;
    }

    public String getDestDomain() {
        return destDomain;
    }

    public DemultiplexingEntity getDemux() {
        return demux;
    }

    public DataHandler getDataHandler() {
        return dataHandler;
    }
}

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
public class CanonicalRequestDynamic extends CanonicalRequest {

    private final String input;

    public CanonicalRequestDynamic(String destDomain, DemultiplexingEntity demux, String targetName, String input, DataHandler handler) {
        super(destDomain, demux, targetName, handler);
        this.input = input;
    }

    public String getInput() {
        return input;
    }

    @Override
    public String toString() {
        return String.format("CREQ_D{D=%s,N=%s,I=%s}", getDestDomain(), getTargetName(), input);
    }

}

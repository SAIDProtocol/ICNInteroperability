/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.rutgers.winlab.icninteroperability.canonical;

import edu.rutgers.winlab.icninteroperability.DataHandler;
import edu.rutgers.winlab.icninteroperability.DemultiplexingEntityContentNameTimestamp;

/**
 *
 * @author ubuntu
 */
public class CanonicalRequestStatic extends CanonicalRequest {

    private final Long exclude;

    public CanonicalRequestStatic(String destDomain, String targetName, Long exclude, DataHandler handler) {
        super(destDomain, new DemultiplexingEntityContentNameTimestamp(destDomain, targetName, exclude), targetName, handler);
        this.exclude = exclude;
    }

    public Long getExclude() {
        return exclude;
    }

    @Override
    public String toString() {
        return String.format("CREQ_S{D=%s,N=%s,T=%d}", getDestDomain(), getTargetName(), exclude);
    }

}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.rutgers.winlab.icninteroperability;

import java.util.Objects;

/**
 *
 * @author ubuntu
 */
public class DemultiplexingEntityContentNameTimestamp implements DemultiplexingEntity {

    private final String domain;
    private final String contentName;
    private final Long time;

    public DemultiplexingEntityContentNameTimestamp(String domain, String contentName, Long time) {
        this.domain = domain;
        this.contentName = contentName;
        this.time = time;
    }

    public String getDomain() {
        return domain;
    }

    public String getContentName() {
        return contentName;
    }

    public Long getTime() {
        return time;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 11 * hash + Objects.hashCode(this.domain);
        hash = 11 * hash + Objects.hashCode(this.contentName);
        hash = 11 * hash + Objects.hashCode(this.time);
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
        final DemultiplexingEntityContentNameTimestamp other = (DemultiplexingEntityContentNameTimestamp) obj;
        if (!Objects.equals(this.domain, other.domain)) {
            return false;
        }
        if (!Objects.equals(this.contentName, other.contentName)) {
            return false;
        }
        return Objects.equals(this.time, other.time);
    }

    @Override
    public String toString() {
        return String.format("Demux(Static):{D=%s,N=%s,T=%d}", domain, contentName, time);
    }

}

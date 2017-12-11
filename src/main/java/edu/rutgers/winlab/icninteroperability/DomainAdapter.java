/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.rutgers.winlab.icninteroperability;

import edu.rutgers.winlab.icninteroperability.canonical.CanonicalRequest;
import java.util.function.BiConsumer;

/**
 *
 * @author ubuntu
 */
public abstract class DomainAdapter implements DataHandler {

    public static final String CROSS_DOMAIN_HOST_PREFIX = "INTR_";

    private final String name;
    private BiConsumer<DomainAdapter, CanonicalRequest> requestHandler;

    public DomainAdapter(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setRequestHandler(BiConsumer<DomainAdapter, CanonicalRequest> requestHandler) {
        this.requestHandler = requestHandler;
    }

    /**
     * Send a request to the core of the gateway.
     *
     * @param request the request to be sent.
     */
    protected void raiseRequest(CanonicalRequest request) {
        requestHandler.accept(this, request);
    }

    public void handleRequest(CanonicalRequest request) {
        sendDomainRequest(request);
    }

    public abstract void sendDomainRequest(CanonicalRequest request);

    public abstract void start();

    public abstract void stop();
    
    protected void incomingRequestAdded(int count) {
    }
    
    protected void incomingRequestRemoved(int count) {
    }
    
    protected void outgoingRequestAdded(int count) {
    }
    
    protected void outgoingRequestRemoved(int count) {
    }

    @Override
    public String toString() {
        return String.format("DomainAdapter:{N=%s}", name);
    }

}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.rutgers.winlab.icninteroperability;

import edu.rutgers.winlab.icninteroperability.canonical.CanonicalRequest;
import java.util.Collection;
//import java.util.concurrent.LinkedBlockingDeque;
//import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author ubuntu
 */
public class Gateway {

//    private static final long POLL_TIMEOUT_IN_US = 100;
    private static final Logger LOG = Logger.getLogger(Gateway.class.getName());

    private final DomainAdapter[] adapters;
    // srcDomain, request -> dstDomain
    private final BiFunction<DomainAdapter, CanonicalRequest, DomainAdapter> routing;
//    private final LinkedBlockingDeque<RequestEntry> requests = new LinkedBlockingDeque<>();
    private boolean started = false, running = false;

    public Gateway(Collection<DomainAdapter> adapters, BiFunction<DomainAdapter, CanonicalRequest, DomainAdapter> routing) {
        this.adapters = new DomainAdapter[adapters.size()];
        adapters.toArray(this.adapters);
        this.routing = routing;
        initAdapters();
    }

    public Gateway(DomainAdapter[] adapters, BiFunction<DomainAdapter, CanonicalRequest, DomainAdapter> routing) {
        this.adapters = new DomainAdapter[adapters.length];
        System.arraycopy(adapters, 0, this.adapters, 0, adapters.length);
        this.routing = routing;
        initAdapters();

    }

    private void initAdapters() {
        for (DomainAdapter adapter : adapters) {
            adapter.setRequestHandler(this::handleRequestFromAdapter);
        }
    }

    public synchronized void start() {
        if (started) {
            throw new UnsupportedOperationException("Cannot start a gateway more than once for now.");
        }
        started = running = true;
//        new Thread(this::switchingThread).start();
        for (DomainAdapter adapter : adapters) {
            adapter.start();
        }
    }

    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
//        requests.addFirst(null);
        for (DomainAdapter adapter : adapters) {
            adapter.stop();
        }
    }

    private void handleRequestFromAdapter(DomainAdapter adapter, CanonicalRequest request) {
        LOG.log(Level.INFO, String.format("[%,d] put request %s from %s to queue", System.currentTimeMillis(), request, adapter));
        forward(adapter, request);

//        requests.add(new RequestEntry(adapter, request));
    }

    private void forward(DomainAdapter src, CanonicalRequest request) {
        DomainAdapter destination = routing.apply(src, request);
        LOG.log(Level.INFO, String.format("[%,d] forward %s from %s to %s", System.currentTimeMillis(), request, src, destination));
        destination.handleRequest(request);
    }

    //switching thread
//    private void switchingThread() {
//        while (running) {
//            RequestEntry request = null;
//            try {
//                request = requests.poll(POLL_TIMEOUT_IN_US, TimeUnit.MICROSECONDS);
//            } catch (InterruptedException ex) {
//            }
//            // in case it is stop, or empty event queue
//            if (request == null) {
//                continue;
//            }
//            LOG.log(Level.INFO, String.format("[%,d] got request %s from %s in queue", System.currentTimeMillis(), request.getRequest(), request.getSrc()));
//            forward(request.getSrc(), request.getRequest());
//        }
//    }
//    private static class RequestEntry {
//
//        private final DomainAdapter src;
//        private final CanonicalRequest request;
//
//        public RequestEntry(DomainAdapter src, CanonicalRequest request) {
//            this.src = src;
//            this.request = request;
//        }
//
//        public DomainAdapter getSrc() {
//            return src;
//        }
//
//        public CanonicalRequest getRequest() {
//            return request;
//        }
//    }
}

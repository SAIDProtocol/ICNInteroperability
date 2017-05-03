/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.rutgers.winlab.icninteroperability.ndn;

import edu.rutgers.winlab.common.NDNUtility;
import edu.rutgers.winlab.icninteroperability.*;
import edu.rutgers.winlab.icninteroperability.canonical.*;
import java.io.*;
import java.util.*;
import java.util.function.*;
import java.util.logging.*;
import org.ccnx.ccn.*;
import org.ccnx.ccn.config.*;
import org.ccnx.ccn.io.*;
import org.ccnx.ccn.profiles.*;
import org.ccnx.ccn.protocol.*;
import static edu.rutgers.winlab.common.NDNUtility.*;
import edu.rutgers.winlab.icninteroperability.ip.DomainAdapterIP;
import java.net.URISyntaxException;
import org.ccnx.ccn.impl.support.Tuple;
import static org.ccnx.ccn.profiles.VersioningProfile.hasTerminalVersion;

/**
 *
 * @author ubuntu
 */
public class DomainAdapterNDN extends DomainAdapter {

    private static final Logger LOG = Logger.getLogger(DomainAdapterNDN.class.getName());

    private CCNHandle handle;

    public DomainAdapterNDN(String name) {
        super(name);
    }

    @Override
    public void start() {
        try {
            handle = CCNHandle.open();
            handle.registerFilter(ContentName.ROOT, (CCNInterestHandler) this::handleInterest);
        } catch (ConfigurationException | IOException ex) {
            LOG.log(Level.SEVERE, "Failed to start CCN handle", ex);
        }
    }

    @Override
    public void stop() {
        handle.close();
    }

//========================= Begin region: left hand side, the consumer domain =========================
    private final HashMap<DemultiplexingEntity, ResponseHandler> pendingRequests = new HashMap<>();

    private boolean handleInterest(Interest interest) {
        if (needSkip(interest.name())) {
            LOG.log(Level.INFO, String.format("[%,d] Skip interest %s.", System.nanoTime(), interest));
            return false;
        }
        LOG.log(Level.INFO, String.format("[%,d] Got interest %s", System.nanoTime(), interest));

        ContentName name = interest.name();
        String host = name.count() == 0 ? "" : new String(name.component(0));
        String domain = host.toUpperCase();
        if (!domain.startsWith(CROSS_DOMAIN_HOST_PREFIX)) {
            domain = CROSS_DOMAIN_HOST_NDN;
        } else {
            name = name.subname(1, name.count());
        }

        LOG.log(Level.INFO, String.format("[%,d] domain:%s, name:%s", System.nanoTime(), domain, name));

        if (VersioningProfile.hasTerminalVersion(interest.name())) {
            return handleDynamicRequest(interest, domain, name);
        } else {
            return handleStaticRequest(interest, domain, name);
        }
    }

    private boolean handleStaticRequest(Interest interest, String domain, ContentName name) {
        Long exclude = NDNUtility.getLastTimeFromExclude(interest.exclude());
        Date d = exclude == null ? null : new Date(exclude);
        String url = name.toString();

        if (url.startsWith("/")) {
            url = url.substring(1);
        }
        LOG.log(Level.INFO, String.format("[%,d] domain:%s, name:%s, exclude: %d (%s)", System.nanoTime(), domain, url, exclude, d));
        CanonicalRequestStatic req = new CanonicalRequestStatic(domain, url, exclude, this);
        DemultiplexingEntity demux = req.getDemux();

        ResponseHandler handler;
        boolean needRaise = false;

        synchronized (pendingRequests) {
            handler = pendingRequests.get(demux);
            if (handler == null) {
                needRaise = true;
                pendingRequests.put(demux, handler = new ResponseHandler());
            }
            handler.addClient(interest);
        }
        LOG.log(Level.INFO, String.format("[%,d] Got canonical request: %s, need raise: %b", System.nanoTime(), req, needRaise));
        if (needRaise) {
            raiseRequest(req);
        }
        return true;
    }

    private boolean handleDynamicRequest(Interest interest, String domain, ContentName name) {
        Tuple<ContentName, byte[]> t = VersioningProfile.cutTerminalVersion(name);
        name = t.first();
        long time = VersioningProfile.getVersionComponentAsTimestamp(t.second()).getTime();

        if (name.count() < 3) {
            LOG.log(Level.INFO, String.format("[%,d] Name %s does not satisfy requirement count < 3. Skip.", System.nanoTime(), name));
        }

        String clientName = Component.printNative(name.component(name.count() - 1));
        name = name.parent();
        byte[] requestBody = name.component(name.count() - 1);
        String request = new String(requestBody);
        try {
            requestBody = Component.parseURI(request);
        } catch (Component.DotDot | URISyntaxException ex) {
            LOG.log(Level.SEVERE, String.format("[%,d] Error in parsing the request body: %s", System.nanoTime(), request), ex);
            return false;
        }
        name = name.parent();
        String url = name.toString();

        if (url.startsWith("/")) {
            url = url.substring(1);
        }
        LOG.log(Level.INFO, String.format("[%,d] time=%s, client=%s, requestLen=%d, name=%s", System.nanoTime(), new Date(time), clientName, requestBody.length, url));
        CanonicalRequestDynamic req = new CanonicalRequestDynamic(domain, new DemultiplexingEntityNDNDynamic(clientName, time), url, requestBody, this);

        ResponseHandler handler;
        DemultiplexingEntity demux = req.getDemux();
        synchronized (pendingRequests) {
            handler = pendingRequests.get(demux);
            if (handler == null) {
                pendingRequests.put(demux, handler = new ResponseHandler());
                handler.addClient(interest);
                LOG.log(Level.INFO, String.format("[%,d] Got canonical request: %s, need raise: %b", System.nanoTime(), req, true));
                raiseRequest(req);
            } else {
                // should not reach here, dynamic request should have no pending interests
                LOG.log(Level.SEVERE, String.format("[%,d] Having duplicate demux %s for dynamic request", System.nanoTime(), demux));
                return false;
            }
        }
        return true;
    }

    @Override
    public void handleDataRetrieved(DemultiplexingEntity demux, byte[] data, int size, Long time, boolean finished) {
        ResponseHandler handler;
        if (finished) {
            synchronized (pendingRequests) {
                handler = pendingRequests.remove(demux);
            }
        } else {
            synchronized (pendingRequests) {
                handler = pendingRequests.get(demux);
            }
        }
        if (handler != null) {
            try {
                handler.addData(data, size, time, finished);
            } catch (Exception ex) {
                LOG.log(Level.SEVERE, String.format("[%,d] Error in writing data to %s", System.nanoTime(), demux), ex);
            }
        }
    }

    @Override
    public void handleDataFailed(DemultiplexingEntity demux) {
        ResponseHandler handler;
        synchronized (pendingRequests) {
            handler = pendingRequests.remove(demux);
        }
        handler.handleDataFailed();
    }

    private class ResponseHandler {

        private final HashMap<Interest, CCNOutputStream[]> pendingClients = new HashMap<>();
        private final ByteArrayOutputStream pendingResponse = new ByteArrayOutputStream();
        private Long time = null;
        private boolean responseFinished = false;

        public synchronized void addClient(Interest request) {
            CCNOutputStream cos = null;
            if (hasTerminalVersion(request.name())) {
                try {
                    cos = new CCNOutputStream(request.name(), handle);
                    cos.addOutstandingInterest(request);
                    if (pendingResponse.size() > 0) {
                        cos.write(pendingResponse.toByteArray());
                    }
                } catch (Exception e) {
                    LOG.log(Level.WARNING, String.format("[%,d] Error in creating output for %s", System.nanoTime(), request), e);
                }
            } else if (time != null) {
                try {
                    cos = createOutputStreamForInterest(request);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, String.format("[%,d] Error in creating output for %s", System.nanoTime(), request), e);
                }
            }
            pendingClients.put(request, new CCNOutputStream[]{cos});
        }

        private CCNOutputStream createOutputStreamForInterest(Interest request) throws IOException {
            CCNOutputStream cos;
            ContentName n = request.name();
            CCNTime v = new CCNTime(time);
            n = new ContentName(n, v);
            cos = new CCNOutputStream(n, handle);
            cos.addOutstandingInterest(request);
            if (pendingResponse.size() > 0) {
                cos.write(pendingResponse.toByteArray());
            }
            return cos;
        }

        public synchronized void handleDataFailed() {
            if (responseFinished) {
                return;
            }
            responseFinished = true;
            pendingClients.entrySet().forEach((pendingClient) -> {
                try {
                    LOG.log(Level.INFO, String.format("[%,d] Skipped and closed %s due to failure", System.nanoTime(), pendingClient.getKey()));
                    CCNOutputStream cos = pendingClient.getValue()[0];
                    if (cos != null) {
                        cos.close();
                    }
                } catch (Exception e) {
                    LOG.log(Level.WARNING, String.format("[%,d] Error in closing output for %s", System.nanoTime(), pendingClient.getKey()), e);
                }
            });
        }

        public synchronized void addData(byte[] data, int size, Long time, boolean finished) throws IOException {
            if (this.responseFinished) {
                return;
            }
            if (time != null) {
                if (this.time == null) {
                    this.time = time;
                } else if (!Objects.equals(time, this.time)) {
                    LOG.log(Level.WARNING, String.format("[%,d] New time (%d) not equal to prev time (%d), set to the new time", System.nanoTime(), time, this.time));
                    this.time = time;
                }
            }
            pendingClients.entrySet().forEach(pair -> {
                CCNOutputStream[] cos = pair.getValue();
                if (cos[0] == null) {
                    if (this.time != null) {
                        try {
                            cos[0] = createOutputStreamForInterest(pair.getKey());
                        } catch (IOException ex) {
                            LOG.log(Level.WARNING, String.format("[%,d] Error in creating output for %s", System.nanoTime(), pair.getKey()), ex);
                        }
                    }
                }
                if (cos[0] != null) {
                    try {
                        cos[0].write(data, 0, size);
                        if (finished) {
                            cos[0].flush();
                            cos[0].close();
                        }
                    } catch (IOException ex) {
                        LOG.log(Level.WARNING, String.format("[%,d] Error in writing data for %s", System.nanoTime(), pair.getKey()), ex);
                    }
                }
            });
            pendingResponse.write(data, 0, size);
            if (finished) {
                this.responseFinished = true;
            }
        }
    }

//========================= End region: left hand side, the consumer domain =========================
//========================= Begin region: right hand side, the repository domain =========================
    private final HashMap<DemultiplexingEntity, NDNRequestHandler> ongoingRequests = new HashMap<>();

    @Override
    public void sendDomainRequest(CanonicalRequest request) {
        LOG.log(Level.INFO, String.format("[%,d] Got domain request for %s", System.nanoTime(), request));
        DemultiplexingEntity demux = request.getDemux();

        NDNRequestHandler handler;
        synchronized (ongoingRequests) {
            handler = ongoingRequests.get(demux);
            if (handler == null) {
                ongoingRequests.put(demux, handler = new NDNRequestHandler(request, this::ndnRequestFinishedHandler));
                handler.startRequest();
            } else {
                handler.addDataHandler(request.getDataHandler());
            }
        }
    }

    private void ndnRequestFinishedHandler(DemultiplexingEntity demux) {
        synchronized (ongoingRequests) {
            ongoingRequests.remove(demux);
        }
    }

    private class NDNRequestHandler implements Runnable {

        private final CanonicalRequest firstRequest;
        private final HashSet<DataHandler> handlers = new HashSet<>();
        private final Consumer<DemultiplexingEntity> contentFinishedHandler;
        private final ByteArrayOutputStream pendingResponse = new ByteArrayOutputStream();
        private Long responseTime = null;

        public NDNRequestHandler(CanonicalRequest firstRequest, Consumer<DemultiplexingEntity> contentFinishedHandler) {
            this.firstRequest = firstRequest;
            this.contentFinishedHandler = contentFinishedHandler;
            addDataHandler(firstRequest.getDataHandler());
        }

        // should NOT start more than once
        public void startRequest() {
            new Thread(this).start();
        }

        public final void addDataHandler(DataHandler handler) {
            if (handlers.add(handler)) {
                // a new handler, write the already received data to handler
                int size = pendingResponse.size();
                if (size > 0) {
                    byte[] buf = pendingResponse.toByteArray();
                    handler.handleDataRetrieved(firstRequest.getDemux(), buf, size, responseTime, false);
                }
            }
        }

        @Override
        public void run() {
            LOG.log(Level.INFO, String.format("[%,d] Start request for %s", System.nanoTime(), firstRequest.getDemux()));

            String domain = firstRequest.getDestDomain(), name = firstRequest.getTargetName();
            ContentName contentName;
            try {
                if (domain.equals(CROSS_DOMAIN_HOST_NDN)) {
                    contentName = ContentName.fromNative("/" + name);
                } else {
                    contentName = ContentName.fromNative(String.format("/%s/%s", domain, name));
                }
                LOG.log(Level.INFO, String.format("[%,d] Created ContentName %s for demux:%s", System.nanoTime(), contentName, firstRequest.getDemux()));

                if (firstRequest instanceof CanonicalRequestStatic) {
                    requestStaticData(firstRequest.getDemux(), ((CanonicalRequestStatic) firstRequest).getExclude(), contentName);
                } else if (firstRequest instanceof CanonicalRequestDynamic) {
                    requestDynamicData(firstRequest.getDemux(), ((CanonicalRequestDynamic) firstRequest).getInput(), contentName);
                } else {
                    LOG.log(Level.INFO, String.format("[%,d] Unknown request type %s, return failure", System.nanoTime(), firstRequest));
                    handlers.forEach(h -> h.handleDataFailed(firstRequest.getDemux()));
                }
            } catch (MalformedContentNameStringException ex) {
                LOG.log(Level.SEVERE, String.format("[%,d] Cannot create ContentName for demux:%s", System.nanoTime(), firstRequest.getDemux()), ex);
                handlers.forEach(h -> h.handleDataFailed(firstRequest.getDemux()));
            }

            contentFinishedHandler.accept(firstRequest.getDemux());
        }

        private void forwardResponse(DemultiplexingEntity demux, CCNInputStream cis) throws IOException {
            byte[] buf = new byte[1500];
            int read;
            while ((read = cis.read(buf)) > 0) {
                for (DataHandler handler : handlers) {
                    handler.handleDataRetrieved(demux, buf, read, responseTime, false);
                }
            }
            handlers.forEach(h -> h.handleDataRetrieved(demux, buf, 0, responseTime, true));
            LOG.log(Level.INFO, String.format("[%,d] Finished getting content demux:%s", System.nanoTime(), demux));
        }

        private void requestDynamicData(DemultiplexingEntity demux, byte[] input, ContentName base) {
            try {
                String inputStr = Component.printURI(input);
                long time = System.currentTimeMillis();
                responseTime = time / 1000 * 1000 + ((time % 1000 == 0) ? 0 : 1000);
                CCNTime version = new CCNTime(time);
                ContentName contentName = new ContentName(base, inputStr, getName(), version);
                LOG.log(Level.INFO, String.format("[%,d] Created ContentName %s for demux:%s", System.nanoTime(), contentName, demux));

                try (CCNInputStream cis = new CCNInputStream(contentName, handle)) {
                    forwardResponse(demux, cis);
                }
            } catch (IOException | RuntimeException ex) {
                LOG.log(Level.SEVERE, String.format("[%,d] Error in retrieving content in NDN demux:%s Name:%s", System.nanoTime(), demux, base), ex);
                handlers.forEach(h -> h.handleDataFailed(firstRequest.getDemux()));
            }
        }

        private void requestStaticData(DemultiplexingEntity demux, Long exclude, ContentName base) {
            try {
                if (exclude != null) {
                    CCNTime time = new CCNTime(exclude);
                    base = new ContentName(base, time);
                }

                ContentObject obj = VersioningProfile.getFirstBlockOfLatestVersion(base, null, null, SystemConfiguration.LONG_TIMEOUT, null, handle);
                if (obj == null) {
                    LOG.log(Level.SEVERE, String.format("[%,d] Cannot find content in NDN demux:%s Name:%s", System.nanoTime(), demux, base));
                    handlers.forEach(h -> h.handleDataFailed(firstRequest.getDemux()));
                    return;
                }
                long version = responseTime = VersioningProfile.getLastVersionAsTimestamp(obj.name()).getTime();
                // round the time to the next second
                responseTime = responseTime / 1000 * 1000 + ((responseTime % 1000 == 0) ? 0 : 1000);
                LOG.log(Level.INFO, String.format("[%,d] Got first chunk of latest version for demux:%s, name=%s, version=0x%x exclude=0x%x", System.nanoTime(), demux, obj.name(), version, exclude));
                if (exclude != null && version <= exclude) {
                    LOG.log(Level.INFO, String.format("[%,d] for demux:%s, latest version %d <= exclude %d, return failure", System.nanoTime(), demux, version, exclude));
                    handlers.forEach(h -> h.handleDataFailed(firstRequest.getDemux()));
                    return;
                }
                try (CCNInputStream cis = new CCNInputStream(obj, null, handle)) {
                    forwardResponse(demux, cis);
                }
            } catch (IOException | VersionMissingException | RuntimeException ex) {
                LOG.log(Level.SEVERE, String.format("[%,d] Error in retrieving content in NDN demux:%s Name:%s", System.nanoTime(), demux, base), ex);
                handlers.forEach(h -> h.handleDataFailed(firstRequest.getDemux()));
            }

        }

    }
//========================= End region: right hand side, the repository domain =========================
}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.rutgers.winlab.icninteroperability.ndn;

import edu.rutgers.winlab.icninteroperability.DataHandler;
import edu.rutgers.winlab.icninteroperability.DemultiplexingEntity;
import edu.rutgers.winlab.icninteroperability.DomainAdapter;
import edu.rutgers.winlab.icninteroperability.canonical.CanonicalRequest;
import edu.rutgers.winlab.icninteroperability.canonical.CanonicalRequestDynamic;
import edu.rutgers.winlab.icninteroperability.canonical.CanonicalRequestStatic;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.ccnx.ccn.CCNHandle;
import org.ccnx.ccn.CCNInterestHandler;
import org.ccnx.ccn.config.ConfigurationException;
import org.ccnx.ccn.config.SystemConfiguration;
import org.ccnx.ccn.impl.support.Log;
import org.ccnx.ccn.io.CCNInputStream;
import org.ccnx.ccn.profiles.VersionMissingException;
import org.ccnx.ccn.profiles.VersioningProfile;
import org.ccnx.ccn.protocol.ContentName;
import org.ccnx.ccn.protocol.ContentObject;
import org.ccnx.ccn.protocol.Interest;
import org.ccnx.ccn.protocol.MalformedContentNameStringException;

/**
 *
 * @author ubuntu
 */
public class DomainAdapterNDN extends DomainAdapter {

    public static final String CROSS_DOMAIN_HOST_NDN = "INTR_NDN";
    private static final Logger LOG = Logger.getLogger(DomainAdapterNDN.class.getName());

    private CCNHandle handle;

    static {
        // suppress all the logs from NDN
        Level[] levels = Log.getLevels();
        Arrays.setAll(levels, i -> Level.OFF);
        Log.setLevels(levels);
    }

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
    private boolean handleInterest(Interest interest) {
        LOG.log(Level.INFO, String.format("[%,d] Got interest %s", System.nanoTime(), interest));
//        CommandMarker.COMMAND_PREFIX
//RepositoryStore rs = new 

//        CCNVersionedInputStream        
        return false;
    }

    @Override
    public void handleDataRetrieved(DemultiplexingEntity demux, byte[] data, int size, Long time, boolean finished) {
    }

    @Override
    public void handleDataFailed(DemultiplexingEntity demux) {
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

        }

        private void requestStaticData(DemultiplexingEntity demux, Long exclude, ContentName base) {
            try {
                ContentObject obj = VersioningProfile.getFirstBlockOfLatestVersion(base, null, null, SystemConfiguration.LONG_TIMEOUT, null, handle);
                long version = responseTime = VersioningProfile.getLastVersionAsTimestamp(obj.name()).getTime();
                LOG.log(Level.INFO, String.format("[%,d] Got first chunk of latest version for demux:%s, name=%s, version=0x%x exclude=0x%x", System.nanoTime(), demux, obj.name(), version, exclude));
                if (exclude != null && version <= exclude) {
                    LOG.log(Level.INFO, String.format("[%,d] for demux:%s, latest version %d <= exclude %d, return failure", System.nanoTime(), demux, version, exclude));
                    handlers.forEach(h -> h.handleDataFailed(firstRequest.getDemux()));
                    return;
                }
                try (CCNInputStream cis = new CCNInputStream(obj, null, handle)) {
                    forwardResponse(demux, cis);
                }
            } catch (IOException | VersionMissingException ex) {
                LOG.log(Level.SEVERE, String.format("[%,d] Error in retrieving content in NDN demux:%s Name:%s", System.nanoTime(), demux, base), ex);
                handlers.forEach(h -> h.handleDataFailed(firstRequest.getDemux()));
            }

        }

    }
//========================= End region: right hand side, the repository domain =========================
}

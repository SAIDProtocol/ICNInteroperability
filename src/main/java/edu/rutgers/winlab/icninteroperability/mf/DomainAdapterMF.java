/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.rutgers.winlab.icninteroperability.mf;

import edu.rutgers.winlab.common.HTTPUtility;
import edu.rutgers.winlab.common.MFUtility;
import static edu.rutgers.winlab.common.MFUtility.CROSS_DOMAIN_HOST_MF;
import static edu.rutgers.winlab.common.MFUtility.DOMAIN_MAPPING_TABLE;
import static edu.rutgers.winlab.common.MFUtility.getRequest;
import edu.rutgers.winlab.icninteroperability.DemultiplexingEntity;
import edu.rutgers.winlab.icninteroperability.DomainAdapter;
import edu.rutgers.winlab.icninteroperability.canonical.CanonicalRequest;
import edu.rutgers.winlab.jmfapi.GUID;
import edu.rutgers.winlab.jmfapi.JMFAPI;
import edu.rutgers.winlab.jmfapi.JMFException;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author ubuntu
 */
public class DomainAdapterMF extends DomainAdapter {

    private static final Logger LOG = Logger.getLogger(DomainAdapterMF.class.getName());
    private boolean started = false;

    public DomainAdapterMF(String name, int guid) throws JMFException {
        super(name);
        senderHandle = new JMFAPI();
        this.senderGUID = new GUID(guid);
        senderHandle.jmfopen("basic", this.senderGUID);
    }

    @Override
    public synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        new Thread(this::listenerThread).start();
    }

    @Override
    public void stop() {
    }

//========================= Begin region: left hand side, the consumer domain =========================
    @Override
    public void handleDataRetrieved(DemultiplexingEntity demux, byte[] data, int size, Long time, boolean finished) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void handleDataFailed(DemultiplexingEntity demux) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

//========================= End region: left hand side, the consumer domain =========================
//========================= Begin region: right hand side, the repository domain =========================
    private final JMFAPI senderHandle;
    private final GUID senderGUID;
    private final AtomicLong senderSerial = new AtomicLong(System.currentTimeMillis());
    private final HashMap<DemultiplexingEntity, LinkedList<CanonicalRequest>> ongoingRequests = new HashMap<>();
    private final HashMap<Long, DemultiplexingEntity> reqIDMapping = new HashMap<>();

    @Override
    public void sendDomainRequest(CanonicalRequest request) {
        LOG.log(Level.INFO, String.format("[%,d] Got domain request for %s", System.nanoTime(), request));
        DemultiplexingEntity demux = request.getDemux();

        Long reqID = null;
        synchronized (ongoingRequests) {
            LinkedList<CanonicalRequest> requests = ongoingRequests.get(demux);
            if (requests == null) {
                ongoingRequests.put(demux, requests = new LinkedList<>());
                reqID = senderSerial.getAndIncrement();
                reqIDMapping.put(reqID, demux);
                LOG.log(Level.INFO, String.format("[%,d] Send new request id=%d for %s", System.nanoTime(), reqID, demux));
            } else {
                LOG.log(Level.INFO, String.format("[%,d] Reusing existing demux: %s", System.nanoTime(), demux));
            }
            requests.add(request);
        }
        if (reqID == null) {
            return;
        }

        String domain = request.getDestDomain(), name = request.getTargetName();
        if (name.startsWith("/")) {
            name = name.substring(1);
        }
        int dstGUID;
        if (domain.equals(CROSS_DOMAIN_HOST_MF)) {
            int idx = name.indexOf('/');
            if (idx < 0) {
                dstGUID = Integer.parseInt(name);
                name = "";
            } else {
                dstGUID = Integer.parseInt(name.substring(0, idx));
                name = name.substring(idx + 1);
            }
        } else {
            dstGUID = DOMAIN_MAPPING_TABLE.get(domain);
        }
        MFUtility.MFRequest req = getRequest(request, name, reqID);
        byte[] buf = req.encode();
        try {
            senderHandle.jmfsend(buf, buf.length, new GUID(dstGUID));
        } catch (JMFException ex) {
            fail(reqID);
        }
    }

    private void fail(long reqID) {
        synchronized (ongoingRequests) {
            DemultiplexingEntity demux = reqIDMapping.remove(reqID);
            if (demux == null) {
                return;
            }
            LinkedList<CanonicalRequest> requests = ongoingRequests.remove(demux);
            requests.forEach(r -> r.getDataHandler().handleDataFailed(demux));
        }
    }

    private void listenerThread() {
        GUID sGUID = new GUID();
        byte[] buf = new byte[MFUtility.MAX_BUF_SIZE];
        int read;
        while (true) {
            try {
                read = senderHandle.jmfrecv_blk(sGUID, buf, buf.length);
                if (read < 0) {
                    LOG.log(Level.SEVERE, "Read from MFAPI < 0, skipped");
                    continue;
                }
                MFUtility.MFResponse resp = new MFUtility.MFResponse();
                if (!resp.decode(senderGUID, sGUID, buf, read)) {
                    LOG.log(Level.SEVERE, "Cannot understand response, skip");
                    continue;
                }
                LOG.log(Level.INFO, String.format("[%,d] Got response: reqID=%d status=%d, time=%s", System.nanoTime(),
                        resp.RequestID, resp.StatusCode, resp.LastModified == null ? null : HTTPUtility.HTTP_DATE_FORMAT.format(resp.LastModified)));
                synchronized (ongoingRequests) {
                    DemultiplexingEntity demux = reqIDMapping.remove(resp.RequestID);
                    if (demux == null) {
                        LOG.log(Level.INFO, String.format("[%,d] No responses waiting for reqID:%d", System.nanoTime(), resp.RequestID));
                        continue;
                    }
                    LinkedList<CanonicalRequest> requests = ongoingRequests.remove(demux);
                    LOG.log(Level.INFO, String.format("[%,d] %d responses waiting for reqID: %d", System.nanoTime(), requests.size(), resp.RequestID));
                    if (resp.StatusCode == HttpURLConnection.HTTP_OK) {
                        requests.forEach(r -> r.getDataHandler().handleDataRetrieved(demux, resp.Body, resp.Body.length, resp.LastModified, true));
                    } else {
                        requests.forEach(r -> r.getDataHandler().handleDataFailed(demux));
                    }

                }

            } catch (JMFException ex) {
                LOG.log(Level.SEVERE, "JMFException", ex);
            }
        }
    }

//========================= End region: right hand side, the repository domain =========================
}

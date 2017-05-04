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
import edu.rutgers.winlab.icninteroperability.canonical.CanonicalRequestDynamic;
import edu.rutgers.winlab.icninteroperability.canonical.CanonicalRequestStatic;
import edu.rutgers.winlab.jmfapi.GUID;
import edu.rutgers.winlab.jmfapi.JMFAPI;
import edu.rutgers.winlab.jmfapi.JMFException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
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
    private final HashMap<Integer, GUIDListenThread> guidListeners = new HashMap<>();

    public DomainAdapterMF(String name, int guid) throws JMFException {
        super(name);
        senderHandle = new JMFAPI();
        this.senderGUID = new GUID(guid);
        senderHandle.jmfopen("basic", this.senderGUID);
        for (Map.Entry<String, Integer> entry : DOMAIN_MAPPING_TABLE.entrySet()) {
            if (entry.getValue().equals(CROSS_DOMAIN_HOST_MF)) {
                continue;
            }
            guidListeners.put(entry.getValue(), new GUIDListenThread(entry.getValue(), entry.getKey()));
        }
    }

    @Override
    public synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        new Thread(this::listenerThread).start();
        guidListeners.values().stream().forEach((value) -> {
            new Thread(value).start();
        });
    }

    @Override
    public void stop() {
    }

//========================= Begin region: left hand side, the consumer domain =========================
    private final HashMap<DemultiplexingEntity, ResponseHandler> pendingRequests = new HashMap<>();

    private class GUIDListenThread implements Runnable {

        private final GUID domainGUID;
        private final String domainName;
        private final JMFAPI handle;
        private final byte[] buf = new byte[MFUtility.MAX_BUF_SIZE];

        public GUIDListenThread(int domainGUID, String domainName) throws JMFException {
            this.domainGUID = new GUID(domainGUID);
            this.domainName = domainName;
            handle = new JMFAPI();
            handle.jmfopen("basic", this.domainGUID);
        }

        @Override
        public void run() {

            try {
                while (true) {
                    GUID sGUID = new GUID();
                    int read = handle.jmfrecv_blk(sGUID, buf, buf.length);
                    if (read < 0) {
                        LOG.log(Level.SEVERE, "Read from MFAPI < 0, skipped");
                        continue;
                    }
                    MFUtility.MFRequest request = new MFUtility.MFRequest();
                    if (!request.decode(domainGUID, sGUID, buf, read)) {
                        continue;
                    }
                    if (request.Name.startsWith("/")) {
                        request.Name = request.Name.substring(1);
                    }
                    if (domainName.equals(CROSS_DOMAIN_HOST_MF)) {
                        request.Name = String.format("%s/%s", domainGUID, request.Name);
                    }

                    CanonicalRequest req;
                    // Do not turn to switch, since request.Method can be null
                    if (HTTPUtility.HTTP_METHOD_STATIC.equals(request.Method)) {
                        req = new CanonicalRequestStatic(domainName, request.Name, request.Exclude, DomainAdapterMF.this);
                        DemultiplexingEntity demux = req.getDemux();
                        ResponseHandler handler;
                        boolean needRaise = false;

                        synchronized (pendingRequests) {
                            handler = pendingRequests.get(demux);
                            if (handler == null) {
                                needRaise = true;
                                pendingRequests.put(demux, handler = new ResponseHandler());
                            }
                            handler.addClient(new ClientIdentifier(domainGUID, handle, sGUID, request.RequestID));
                        }
                        LOG.log(Level.INFO, String.format("[%,d] Got canonical request: %s, need raise: %b", System.nanoTime(), req, needRaise));
                        if (needRaise) {
                            raiseRequest(req);
                        }
                    } else if (HTTPUtility.HTTP_METHOD_DYNAMIC.equals(request.Method)) {
                        req = new CanonicalRequestDynamic(domainName, new DemultiplexingEntityMFDynamic(sGUID, request.RequestID), request.Name, request.Body, DomainAdapterMF.this);
                        DemultiplexingEntity demux = req.getDemux();
                        ResponseHandler handler;
                        synchronized (pendingRequests) {
                            handler = pendingRequests.get(demux);
                            if (handler == null) {
                                pendingRequests.put(demux, handler = new ResponseHandler());
                                handler.addClient(new ClientIdentifier(domainGUID, handle, sGUID, request.RequestID));
                                LOG.log(Level.INFO, String.format("[%,d] Got canonical request: %s, need raise: %b", System.nanoTime(), req, true));
                                raiseRequest(req);
                            } else {
                                // should not reach here, dynamic request should have no pending interests
                                try {
                                    LOG.log(Level.SEVERE, String.format("[%,d] Having duplicate demux %s for dynamic request", System.nanoTime(), demux));
                                    writeBody(handle, sGUID, request.RequestID, HttpURLConnection.HTTP_INTERNAL_ERROR, System.currentTimeMillis(), String.format(HTTPUtility.HTTP_RESPONSE_FAIL_IN_PROCESS, "Having duplicate demux for dynamic request", demux).getBytes(), 0);
                                } catch (Exception ex2) {
                                    LOG.log(Level.SEVERE, String.format("[%,d] Error in writing 500 to client %s %d", System.nanoTime(), sGUID, request.RequestID), ex2);
                                }
                            }
                        }
                    } else {
                        LOG.log(Level.INFO, String.format("[%,d] (me:%s, client:%s, reqid:%d) Method (%s) not correct in request", System.nanoTime(), domainGUID, sGUID, request.RequestID, request.Method));
                        writeBody(handle, sGUID, request.RequestID, HttpURLConnection.HTTP_NOT_IMPLEMENTED, System.currentTimeMillis(), String.format(HTTPUtility.HTTP_RESPONSE_UNSUPPORTED_ACTION_FORMAT, request.Method).getBytes(), 0);
                    }
                }
            } catch (JMFException ex) {
                LOG.log(Level.SEVERE, String.format("[%,d] Error in reading content from JMFAPI, contentGUID:%s, exitting", System.nanoTime(), domainGUID), ex);
            }
        }

    }

    public static void writeBody(JMFAPI handle, GUID clientGUID, long clientRequestID, int statusCode, Long time, byte[] body, int bodyLen) throws JMFException {
        MFUtility.MFResponse response = new MFUtility.MFResponse();
        response.RequestID = clientRequestID;
        response.StatusCode = statusCode;
        response.LastModified = time;
        response.Body = body;
        response.BodyLen = bodyLen;
        byte[] toSend = response.encode();
        handle.jmfsend(toSend, toSend.length, clientGUID);
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

    private static class ClientIdentifier {

        public GUID myGUID;
        public JMFAPI Handle;
        public GUID ClientGUID;
        public long ClientRequestID;

        public ClientIdentifier(GUID me, JMFAPI handle, GUID client, long reqID) {
            myGUID = me;
            Handle = handle;
            ClientGUID = client;
            ClientRequestID = reqID;
        }

        @Override
        public String toString() {
            return String.format("Client{me=%s,c=%s,reqID=%d}", myGUID, ClientGUID, ClientRequestID);
        }

        public void writeNotModified() throws JMFException {
            DomainAdapterMF.writeBody(Handle, ClientGUID, ClientRequestID, HttpURLConnection.HTTP_NOT_MODIFIED, System.currentTimeMillis(), null, 0);
            LOG.log(Level.INFO, String.format("[%,d] Wrote Not Modified to %s", System.nanoTime(), this));
        }

        public void writeBody(byte[] buf, int size, Long time) throws JMFException {
            DomainAdapterMF.writeBody(Handle, ClientGUID, ClientRequestID, HttpURLConnection.HTTP_OK, time, buf, size);
            LOG.log(Level.INFO, String.format("[%,d] Wrote response to %s", System.nanoTime(), this));
        }

    }

    private static class ResponseHandler {

        private final HashSet<ClientIdentifier> pendingClients = new HashSet<>();
        private final ByteArrayOutputStream pendingResponse = new ByteArrayOutputStream();
        private Long time = null;
        private boolean responseFinished = false;

        public synchronized void addClient(ClientIdentifier exchange) {
            pendingClients.add(exchange);
        }

        public synchronized void handleDataFailed() {
            if (responseFinished) {
                return;
            }
            responseFinished = true;
            pendingClients.forEach((pendingClient) -> {
                try {
                    pendingClient.writeNotModified();
                } catch (Exception ex) {
                    LOG.log(Level.SEVERE, String.format("[%,d] Error in writing response to %s", System.nanoTime(), pendingClient), ex);
                }
            });
        }

        public synchronized void addData(byte[] data, int size, Long time, boolean finished) throws IOException {
            if (this.responseFinished) {
                return;
            }
            pendingResponse.write(data, 0, size);
            if (time != null) {
                if (this.time == null) {
                    this.time = time;
                } else if (!Objects.equals(time, this.time)) {
                    LOG.log(Level.WARNING, String.format("[%,d] New time (%d) not equal to prev time (%d), set to the new time", System.nanoTime(), time, this.time));
                    this.time = time;
                }
            }
            if (finished) {
                this.responseFinished = true;
                writeToClients();
            }
        }

        private void writeToClients() {
            byte[] buf = pendingResponse.toByteArray();
            int responseSize = pendingResponse.size();
            pendingClients.forEach((pendingClient) -> {
                try {
                    pendingClient.writeBody(buf, responseSize, time);
                } catch (Exception ex) {
                    LOG.log(Level.SEVERE, String.format("[%,d] Error in writing response to %s", System.nanoTime(), pendingClient), ex);
                }
            });
        }

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
        byte[] buf = new byte[MFUtility.MAX_BUF_SIZE];
        while (true) {
            try {
                GUID sGUID = new GUID();
                int read = senderHandle.jmfrecv_blk(sGUID, buf, buf.length);
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

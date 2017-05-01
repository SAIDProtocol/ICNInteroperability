/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.rutgers.winlab.icninteroperability.ip;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import static edu.rutgers.winlab.common.HTTPUtility.CROSS_DOMAIN_HOST_IP;
import static edu.rutgers.winlab.common.HTTPUtility.HTTP_DATE_FORMAT;
import static edu.rutgers.winlab.common.HTTPUtility.HTTP_HEADER_HOST;
import static edu.rutgers.winlab.common.HTTPUtility.HTTP_HEADER_IF_MODIFIED_SINCE;
import static edu.rutgers.winlab.common.HTTPUtility.HTTP_HEADER_LAST_MODIFIED;
import static edu.rutgers.winlab.common.HTTPUtility.HTTP_METHOD_DYNAMIC;
import static edu.rutgers.winlab.common.HTTPUtility.HTTP_METHOD_STATIC;
import static edu.rutgers.winlab.common.HTTPUtility.HTTP_RESPONSE_CONTENT_LENGTH_SHOULD_NOT_BE_NULL;
import static edu.rutgers.winlab.common.HTTPUtility.HTTP_RESPONSE_ERROR_IN_READING_REQUEST_BODY;
import static edu.rutgers.winlab.common.HTTPUtility.HTTP_RESPONSE_HOST_SHOULD_NOT_BE_NULL;
import static edu.rutgers.winlab.common.HTTPUtility.HTTP_RESPONSE_UNSUPPORTED_ACTION_FORMAT;
import static edu.rutgers.winlab.common.HTTPUtility.OUTGOING_GATEWAY_DOMAIN_SUFFIX;
import static edu.rutgers.winlab.common.HTTPUtility.readRequestBody;
import static edu.rutgers.winlab.common.HTTPUtility.writeNotModified;
import static edu.rutgers.winlab.common.HTTPUtility.writeQuickResponse;
import edu.rutgers.winlab.icninteroperability.DataHandler;
import edu.rutgers.winlab.icninteroperability.DemultiplexingEntity;
import edu.rutgers.winlab.icninteroperability.DomainAdapter;
import edu.rutgers.winlab.icninteroperability.canonical.CanonicalRequest;
import edu.rutgers.winlab.icninteroperability.canonical.CanonicalRequestDynamic;
import edu.rutgers.winlab.icninteroperability.canonical.CanonicalRequestStatic;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_NOT_IMPLEMENTED;
import static java.net.HttpURLConnection.HTTP_OK;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import static edu.rutgers.winlab.common.HTTPUtility.HTTP_RESPONSE_FAIL_IN_PROCESS;
import static edu.rutgers.winlab.common.HTTPUtility.writeBody;

/**
 *
 * @author ubuntu
 */
public class DomainAdapterIP extends DomainAdapter {

    private static final Logger LOG = Logger.getLogger(DomainAdapterIP.class.getName());

    public DomainAdapterIP(String name, int listenPort) throws IOException {
        super(name);
        server = HttpServer.create(new InetSocketAddress(listenPort), 0);
        server.createContext("/", this::handleHttpRequest);
    }

    @Override
    public void start() {
        server.start();
    }

    @Override
    public void stop() {
        server.stop(0);
    }

//========================= Begin region: left hand side, the consumer domain =========================
    private final HttpServer server;
    private final HashMap<DemultiplexingEntity, ResponseHandler> pendingRequests = new HashMap<>();

    private void handleHttpRequest(HttpExchange exchange) {

//        System.out.println("=======Headers=======");
//        exchange.getRequestHeaders().entrySet().forEach((e) -> {
//            e.getValue().forEach(val -> System.out.printf("%s: %s%n", e.getKey(), val));
//        });
//        System.out.println("=======Header End=======");
//        System.out.printf("Client: %s, Me: %s%n", exchange.getRemoteAddress(), exchange.getLocalAddress());
        LOG.log(Level.INFO, String.format("[%,d] Receive request from %s", System.nanoTime(), exchange.getRemoteAddress()));

        String name = exchange.getRequestURI().toString();
        if (name.startsWith("/")) {
            name = name.substring(1);
        }

        String host = exchange.getRequestHeaders().getFirst(HTTP_HEADER_HOST);
        if (host == null) {
            LOG.log(Level.INFO, String.format("[%,d] Send response to %s, host==null not supportd", System.nanoTime(), exchange.getRemoteAddress()));
            try {
                writeQuickResponse(exchange, HTTP_NOT_IMPLEMENTED, HTTP_RESPONSE_HOST_SHOULD_NOT_BE_NULL);
            } catch (Exception ex) {
                LOG.log(Level.SEVERE, String.format("[%,d] Error in writing response to %s", System.nanoTime(), exchange.getRemoteAddress()), ex);
            }
            return;
        }

        String domain = host.toUpperCase();
        if (domain == null || !domain.startsWith(CROSS_DOMAIN_HOST_PREFIX)) {
            // means it is an IP request
            domain = CROSS_DOMAIN_HOST_IP;
            // put the host before the url string
            name = host + "/" + name;
        } else {
            try {
                name = URLDecoder.decode(name, "UTF-8");
            } catch (UnsupportedEncodingException ex) {
                LOG.log(Level.SEVERE, "Should not reach here, using UTF-8 encoding", ex);
            }
        }
        {
            // remove the port part in the url
            int idx = domain.indexOf(':');
            if (idx > 0) {
                domain = domain.substring(0, idx);
            }
        }

        String requestMethod = exchange.getRequestMethod();
        switch (requestMethod) {
            case HTTP_METHOD_STATIC: {
                handleStaticRequest(exchange, domain, name);
                break;
            }
            case HTTP_METHOD_DYNAMIC: {
                handleDynamicRequest(exchange, domain, name);
                break;
            }
            default: {
                LOG.log(Level.INFO, String.format("[%,d] Send response to %s, action (%s) not supportd", System.nanoTime(), exchange.getRemoteAddress(), requestMethod));
                try {
                    writeQuickResponse(exchange, HTTP_NOT_IMPLEMENTED, HTTP_RESPONSE_UNSUPPORTED_ACTION_FORMAT, requestMethod);
                } catch (Exception ex) {
                    LOG.log(Level.SEVERE, String.format("[%,d] Error in writing response to %s", System.nanoTime(), exchange.getRemoteAddress()), ex);
                }
            }
            break;
        }
    }

    private void handleStaticRequest(HttpExchange exchange, String domain, String name) {
        String ifModifiedSince = exchange.getRequestHeaders().getFirst(HTTP_HEADER_IF_MODIFIED_SINCE);
        Long exclude = null;
        try {
            exclude = HTTP_DATE_FORMAT.parse(ifModifiedSince).getTime();
        } catch (Exception ex) {
            // if cannot parse date, see it as NULL
        }
        CanonicalRequestStatic req = new CanonicalRequestStatic(domain, name, exclude, this);

        DemultiplexingEntity demux = req.getDemux();
        ResponseHandler handler;
        boolean needRaise = false;

        synchronized (pendingRequests) {
            handler = pendingRequests.get(demux);
            if (handler == null) {
                needRaise = true;
                pendingRequests.put(demux, handler = new ResponseHandler());
            }
            handler.addClient(exchange);
        }
        LOG.log(Level.INFO, String.format("[%,d] Got canonical request: %s, need raise: %b", System.nanoTime(), req, needRaise));
        if (needRaise) {
            raiseRequest(req);
        }
    }

    private void handleDynamicRequest(HttpExchange exchange, String domain, String name) {

        byte[] body;
        try {
            body = readRequestBody(exchange);
            if (body == null) {
                try {
                    LOG.log(Level.INFO, String.format("[%,d] Content-Length field missing in header, respond not supported", System.nanoTime()));
                    writeQuickResponse(exchange, HTTP_NOT_IMPLEMENTED, HTTP_RESPONSE_CONTENT_LENGTH_SHOULD_NOT_BE_NULL);
                } catch (IOException ex) {
                    LOG.log(Level.SEVERE, String.format("[%,d] Error in writing 501 to client %s", System.nanoTime(), exchange.getRemoteAddress()), ex);
                }
                return;
            }
        } catch (Exception ex) {
            try {
                LOG.log(Level.SEVERE, String.format("[%,d] Error in reading client body", System.nanoTime()), ex);
                writeQuickResponse(exchange, HTTP_BAD_REQUEST, HTTP_RESPONSE_ERROR_IN_READING_REQUEST_BODY, ex.toString());
            } catch (Exception ex2) {
                LOG.log(Level.SEVERE, String.format("[%,d] Error in writing 400 to client %s", System.nanoTime(), exchange.getRemoteAddress()), ex2);
            }
            return;
        }
        LOG.log(Level.INFO, String.format("[%,d] Received dynamic request name:%s remote:%s reqBodyLen:%d", System.nanoTime(), name, exchange.getRemoteAddress(), body.length));

        CanonicalRequestDynamic req = new CanonicalRequestDynamic(domain, new DemultiplexingEntityIPDynamic(exchange.getRemoteAddress()), name, body, this);

        ResponseHandler handler;
        DemultiplexingEntity demux = req.getDemux();
        synchronized (pendingRequests) {
            handler = pendingRequests.get(demux);
            if (handler == null) {
                pendingRequests.put(demux, handler = new ResponseHandler());
                handler.addClient(exchange);
                LOG.log(Level.INFO, String.format("[%,d] Got canonical request: %s, need raise: %b", System.nanoTime(), req, true));
                raiseRequest(req);
            } else {
                // should not reach here, dynamic request should have no pending interests
                try {
                    LOG.log(Level.SEVERE, String.format("[%,d] Having duplicate demux %s for dynamic request", System.nanoTime(), demux));
                    writeQuickResponse(exchange, HTTP_INTERNAL_ERROR, HTTP_RESPONSE_FAIL_IN_PROCESS, "Having duplicate demux for dynamic request", demux);
                } catch (Exception ex2) {
                    LOG.log(Level.SEVERE, String.format("[%,d] Error in writing 500 to client %s", System.nanoTime(), exchange.getRemoteAddress()), ex2);
                }
            }
        }
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

    private static class ResponseHandler {

        private final HashSet<HttpExchange> pendingClients = new HashSet<>();
        private final ByteArrayOutputStream pendingResponse = new ByteArrayOutputStream();
        private Long time = null;
        private boolean responseFinished = false;

        public synchronized void addClient(HttpExchange exchange) {
            pendingClients.add(exchange);
        }

        public synchronized void handleDataFailed() {
            if (responseFinished) {
                return;
            }
            responseFinished = true;
            pendingClients.forEach((pendingClient) -> {
                try {
                    writeNotModified(pendingClient);
                    LOG.log(Level.INFO, String.format("[%,d] Wrote Not Modified to %s", System.nanoTime(), pendingClient.getRemoteAddress()));
                } catch (Exception ex) {
                    LOG.log(Level.SEVERE, String.format("[%,d] Error in writing response to %s", System.nanoTime(), pendingClient.getRemoteAddress()), ex);
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
                    writeBody(pendingClient, buf, responseSize, time == null ? null : new Date(time));
                    LOG.log(Level.INFO, String.format("[%,d] Wrote response to %s", System.nanoTime(), pendingClient.getRemoteAddress()));
                } catch (Exception ex) {
                    LOG.log(Level.SEVERE, String.format("[%,d] Error in writing response to %s", System.nanoTime(), pendingClient.getRemoteAddress()), ex);
                }
            });
        }

    }

//========================= End region: left hand side, the consumer domain =========================
//========================= Begin region: right hand side, the repository domain =========================
    private final HashMap<DemultiplexingEntity, HttpRequestHandler> ongoingRequests = new HashMap<>();

    @Override
    public void sendDomainRequest(CanonicalRequest request) {
        LOG.log(Level.INFO, String.format("[%,d] Got domain request for %s", System.nanoTime(), request));
        DemultiplexingEntity demux = request.getDemux();

        HttpRequestHandler handler;
        synchronized (ongoingRequests) {
            handler = ongoingRequests.get(demux);
            if (handler == null) {
                ongoingRequests.put(demux, handler = new HttpRequestHandler(request, this::httpRequestFinishedHandler));
                handler.startRequest();
            } else {
                handler.addDataHandler(request.getDataHandler());
            }
        }
    }

    private void httpRequestFinishedHandler(DemultiplexingEntity demux) {
        synchronized (ongoingRequests) {
            ongoingRequests.remove(demux);
        }
    }

    private static class HttpRequestHandler implements Runnable {

        private final CanonicalRequest firstRequest;
        private final HashSet<DataHandler> handlers = new HashSet<>();
        private final Consumer<DemultiplexingEntity> contentFinishedHandler;
        private final ByteArrayOutputStream pendingResponse = new ByteArrayOutputStream();
        private Long responseTime = null;

        public HttpRequestHandler(CanonicalRequest firstRequest, Consumer<DemultiplexingEntity> contentFinishedHandler) {
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

            String urlStr = null, host = null;
            String domain = firstRequest.getDestDomain(), name = firstRequest.getTargetName();
            if (domain.equals(CROSS_DOMAIN_HOST_IP)) {
                urlStr = "http://" + name;
            } else {
                try {
                    urlStr = String.format("http://%s%s/%s", domain, OUTGOING_GATEWAY_DOMAIN_SUFFIX, URLEncoder.encode(name, "UTF-8"));
                } catch (UnsupportedEncodingException ex) {
                    LOG.log(Level.SEVERE, "Should not reach here, using UTF-8 encoding", ex);
                }
                host = domain;
            }

            if (firstRequest instanceof CanonicalRequestStatic) {
                requestStaticData(firstRequest.getDemux(), ((CanonicalRequestStatic) firstRequest).getExclude(), urlStr, host);
            } else if (firstRequest instanceof CanonicalRequestDynamic) {
                requestDynamicData(firstRequest.getDemux(), ((CanonicalRequestDynamic) firstRequest).getInput(), urlStr, host);
            } else {
                LOG.log(Level.INFO, String.format("[%,d] Unknown request type %s, return failure", System.nanoTime(), firstRequest));
                handlers.forEach(h -> h.handleDataFailed(firstRequest.getDemux()));
            }

            contentFinishedHandler.accept(firstRequest.getDemux());
        }

        private void forwardResponse(DemultiplexingEntity demux, String urlStr, HttpURLConnection connection) throws IOException {
            if (connection.getResponseCode() != HTTP_OK) {
                handlers.forEach(handler -> handler.handleDataFailed(demux));
            } else {
                String lastModified = connection.getHeaderField(HTTP_HEADER_LAST_MODIFIED);
                try {
                    responseTime = HTTP_DATE_FORMAT.parse(lastModified).getTime();
                    LOG.log(Level.INFO, String.format("[%,d] Get content demux:%s URL:%s LastModified:%d", System.nanoTime(), demux, urlStr, responseTime));
                } catch (Exception e) {

                }
                byte[] buf = new byte[1500];
                try (InputStream is = connection.getInputStream()) {
                    int read;
                    while ((read = is.read(buf)) > 0) {
                        for (DataHandler handler : handlers) {
                            handler.handleDataRetrieved(demux, buf, read, responseTime, false);
                        }
                    }
                    handlers.forEach(handler -> handler.handleDataRetrieved(demux, buf, 0, responseTime, true));
                }
                LOG.log(Level.INFO, String.format("[%,d] Finished getting content demux:%s URL:%s", System.nanoTime(), demux, urlStr));
            }
        }

        private void requestDynamicData(DemultiplexingEntity demux, byte[] input, String urlStr, String host) {

            HttpURLConnection connection = null;
            LOG.log(Level.INFO, String.format("[%,d] Start getting content demux: %s URL:%s", System.nanoTime(), demux, urlStr));
            try {
                URL url = new URL(urlStr);
                if (host == null) {
                    host = url.getHost();
                }
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod(HTTP_METHOD_DYNAMIC);
                connection.setRequestProperty(HTTP_HEADER_HOST, host);
                connection.setUseCaches(false);

                if (input.length > 0) {
                    connection.setDoOutput(true);
                    try (OutputStream output = connection.getOutputStream()) {
                        output.write(input);
                    }
                }

                forwardResponse(demux, urlStr, connection);

            } catch (Exception ex) {
                LOG.log(Level.SEVERE, String.format("[%,d] Error in retrieving content in IP demux:%s URL:%s", System.nanoTime(), demux, urlStr), ex);
                handlers.forEach(handler -> handler.handleDataFailed(demux));
            } finally {
                if (connection != null) {
                    try {
                        connection.disconnect();
                    } catch (Exception e) {
                    }
                }
            }
        }

        private void requestStaticData(DemultiplexingEntity demux, Long exclude, String urlStr, String host) {

            HttpURLConnection connection = null;
            LOG.log(Level.INFO, String.format("[%,d] Start getting content demux: %s URL:%s", System.nanoTime(), demux, urlStr));
            try {
                URL url = new URL(urlStr);
                if (host == null) {
                    host = url.getHost();
                }
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod(HTTP_METHOD_STATIC);
                connection.setRequestProperty(HTTP_HEADER_HOST, host);
                if (exclude != null) {
                    connection.setRequestProperty(HTTP_HEADER_IF_MODIFIED_SINCE, HTTP_DATE_FORMAT.format(new Date(exclude)));
                }
                forwardResponse(demux, urlStr, connection);

            } catch (Exception ex) {
                LOG.log(Level.SEVERE, String.format("[%,d] Error in retrieving content in IP demux:%s URL:%s", System.nanoTime(), demux, urlStr), ex);
                handlers.forEach(handler -> handler.handleDataFailed(demux));
            } finally {
                if (connection != null) {
                    try {
                        connection.disconnect();
                    } catch (Exception e) {
                    }
                }
            }
        }
    }

//========================= End region: right hand side, the repository domain =========================
}

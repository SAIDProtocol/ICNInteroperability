/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.rutgers.winlab.provider;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import static edu.rutgers.winlab.common.HTTPUtility.HTTP_DATE_FORMAT;
import static edu.rutgers.winlab.common.HTTPUtility.HTTP_HEADER_IF_MODIFIED_SINCE;
import static edu.rutgers.winlab.common.HTTPUtility.HTTP_METHOD_DYNAMIC;
import static edu.rutgers.winlab.common.HTTPUtility.HTTP_METHOD_STATIC;
import static edu.rutgers.winlab.common.HTTPUtility.HTTP_RESPONSE_CONTENT_LENGTH_SHOULD_NOT_BE_NULL;
import static edu.rutgers.winlab.common.HTTPUtility.HTTP_RESPONSE_ERROR_IN_READING_REQUEST_BODY;
import static edu.rutgers.winlab.common.HTTPUtility.HTTP_RESPONSE_FILE_NOT_FOUND_FORMAT;
import static edu.rutgers.winlab.common.HTTPUtility.HTTP_RESPONSE_UNSUPPORTED_ACTION_FORMAT;
import static edu.rutgers.winlab.common.HTTPUtility.parseQuery;
import static edu.rutgers.winlab.common.HTTPUtility.readRequestBody;
import static edu.rutgers.winlab.common.HTTPUtility.writeBody;
import static edu.rutgers.winlab.common.HTTPUtility.writeNotModified;
import static edu.rutgers.winlab.common.HTTPUtility.writeQuickResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_NOT_IMPLEMENTED;
import java.net.InetSocketAddress;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import static edu.rutgers.winlab.common.HTTPUtility.writeBody;

/**
 *
 * @author ubuntu
 */
public class ProviderIP {

    public static final String SLEEP_PARAM_NAME = "sleep";
    private static final Logger LOG = Logger.getLogger(ProviderIP.class.getName());

    private final String folder;
    private final HttpServer server;
    private final int staticFileWaitTime;

    public ProviderIP(int port, String folder, int staticFileWaitTime) throws IOException {
        this.folder = folder;
        this.staticFileWaitTime = staticFileWaitTime;
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", this::handleHttpExchange);
        server.setExecutor(Executors.newCachedThreadPool());
    }

    private void handleHttpExchange(HttpExchange exchange) {
        String requestMethod = exchange.getRequestMethod();
        switch (requestMethod) {
            case HTTP_METHOD_STATIC: {
                handleStaticContent(exchange);
                break;
            }
            case HTTP_METHOD_DYNAMIC: {
                handleDynamicContent(exchange);
                break;
            }
            default: {
                LOG.log(Level.INFO, String.format("[%,d] Send response to %s, action (%s) not supportd", System.nanoTime(), exchange.getRemoteAddress(), requestMethod));
                try {
                    writeQuickResponse(exchange, HTTP_NOT_IMPLEMENTED, HTTP_RESPONSE_UNSUPPORTED_ACTION_FORMAT, requestMethod);
                } catch (IOException ex) {
                    LOG.log(Level.SEVERE, String.format("[%,d] Error in writing response to %s", System.nanoTime(), exchange.getRemoteAddress()), ex);
                }
            }
        }
    }

    private void handleStaticContent(HttpExchange exchange) {
        String uri = exchange.getRequestURI().toString();
        String ifModifiedSince = exchange.getRequestHeaders().getFirst(HTTP_HEADER_IF_MODIFIED_SINCE);
        Long exclude = null;
        try {
            exclude = HTTP_DATE_FORMAT.parse(ifModifiedSince).getTime();
        } catch (Exception ex) {
            // if cannot parse date, see it as NULL
        }
        File f = new File(folder, uri);

        LOG.log(Level.INFO, String.format("[%,d] Received static request URI:%s remote:%s f:%s exclude:%d", System.nanoTime(), uri, exchange.getRemoteAddress(), f, exclude));
        if (!f.isFile()) {
            try {
                LOG.log(Level.INFO, String.format("[%,d] File not exist write 404 URI:%s remote:%s", System.nanoTime(), uri, exchange.getRemoteAddress()));
                writeQuickResponse(exchange, HTTP_NOT_FOUND, HTTP_RESPONSE_FILE_NOT_FOUND_FORMAT, uri);
            } catch (IOException ex) {
                LOG.log(Level.SEVERE, String.format("[%,d] Error in writing 404 to client %s", System.nanoTime(), exchange.getRemoteAddress()), ex);
            }
            return;
        }

        if (staticFileWaitTime > 0) {
            try {
                Thread.sleep(staticFileWaitTime);
            } catch (InterruptedException e) {
            }
        }

        if (exclude != null && exclude >= f.lastModified()) {
            try {
                LOG.log(Level.INFO, String.format("[%,d] File not modified write 304 URI:%s remote:%s f:%s exclude:%d", System.nanoTime(), uri, exchange.getRemoteAddress(), f, exclude));
                writeNotModified(exchange);
            } catch (IOException ex) {
                LOG.log(Level.SEVERE, String.format("[%,d] Error in writing 304 to client %s", System.nanoTime(), exchange.getRemoteAddress()), ex);
            }
        } else {
            try (FileInputStream fis = new FileInputStream(f)) {
                LOG.log(Level.INFO, String.format("[%,d] Write file to client URI:%s remote:%s f:%s last-modified:%d, len:%d", System.nanoTime(), uri, exchange.getRemoteAddress(), f, f.lastModified(), f.length()));
                writeBody(exchange, fis, f.length(), new Date(f.lastModified()));
            } catch (IOException ex) {
                LOG.log(Level.SEVERE, String.format("[%,d] Error in writing file to client %s", System.nanoTime(), exchange.getRemoteAddress()), ex);
            }
        }
    }

    private void handleDynamicContent(HttpExchange exchange) {
        String uri = exchange.getRequestURI().toString();
        byte[] buf;
        try {
            buf = readRequestBody(exchange);
            if (buf == null) {
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
        LOG.log(Level.INFO, String.format("[%,d] Received dynamic request URI:%s remote:%s reqBodyLen:%d", System.nanoTime(), uri, exchange.getRemoteAddress(), buf.length));

        String queryString = new String(buf);
        LOG.log(Level.INFO, String.format("[%,d] request body: %s", System.nanoTime(), queryString));
        Map<String, List<String>> parameters = new HashMap<>();
        parseQuery(queryString, parameters);

        int sleepLen = 0;
        try {
            sleepLen = Integer.parseInt(parameters.get(SLEEP_PARAM_NAME).get(0));
        } catch (Exception e) {
        }
        if (sleepLen > 0) {
            try {
                Thread.sleep(sleepLen);
            } catch (InterruptedException ex) {
                Logger.getLogger(ProviderIP.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        String echo = "This is a simple echo application%nRequest string is: %s%nNow: %s%nBye!%n";
        byte[] result = String.format(echo, queryString, HTTP_DATE_FORMAT.format(new Date())).getBytes();
        try {
            Date lastModified = new Date();
            LOG.log(Level.INFO, String.format("[%,d] Write file to client URI:%s remote:%s last-modified:%d, len:%d", System.nanoTime(), uri, exchange.getRemoteAddress(), lastModified.getTime(), result.length));
            writeBody(exchange, result, result.length, lastModified);
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, String.format("[%,d] Error in writing response to client %s", System.nanoTime(), exchange.getRemoteAddress()), ex);
        }
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }
}

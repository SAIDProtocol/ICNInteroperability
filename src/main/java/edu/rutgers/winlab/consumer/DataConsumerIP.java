/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.rutgers.winlab.consumer;

import static edu.rutgers.winlab.common.HTTPUtility.*;
import static java.net.HttpURLConnection.*;
import edu.rutgers.winlab.icninteroperability.*;
import edu.rutgers.winlab.icninteroperability.canonical.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.logging.*;

/**
 *
 * @author ubuntu
 */
public class DataConsumerIP implements DataConsumer {

    private static final Logger LOG = Logger.getLogger(DataConsumerIP.class.getName());

    private Long forwardResponse(DemultiplexingEntity demux, String urlStr, HttpURLConnection connection, DataHandler output) throws IOException {
        Long responseTime = null;
        if (connection.getResponseCode() != HTTP_OK) {
            throw new IOException("Response code is not 200 OK!");
        } else {
            String lastModified = connection.getHeaderField(HTTP_HEADER_LAST_MODIFIED);
            try {
                responseTime = HTTP_DATE_FORMAT.parse(lastModified).getTime();
                LOG.log(Level.INFO, String.format("[%,d] Get content URL:%s LastModified:%s (%d)", System.nanoTime(), urlStr, lastModified, responseTime));
            } catch (Exception e) {
            }
            byte[] buf = new byte[1500];
            try (InputStream is = connection.getInputStream()) {
                int read;
                while ((read = is.read(buf)) > 0) {
                    output.handleDataRetrieved(demux, buf, read, responseTime, false);
                }
                output.handleDataRetrieved(demux, buf, 0, responseTime, true);
            }
            LOG.log(Level.INFO, String.format("[%,d] Finished getting content URL:%s", System.nanoTime(), urlStr));
        }
        return responseTime;
    }

    @Override
    public Long requestStatic(CanonicalRequestStatic request) throws IOException {
        String urlStr = null, host = null;
        String domain = request.getDestDomain(), name = request.getTargetName();
        Long exclude = request.getExclude();
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
        HttpURLConnection connection = null;
        LOG.log(Level.INFO, String.format("[%,d] Start getting content request:%s URL:%s", System.nanoTime(), request, urlStr));
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
            return forwardResponse(request.getDemux(), urlStr, connection, request.getDataHandler());
        } finally {
            if (connection != null) {
                try {
                    connection.disconnect();
                } catch (Exception e) {
                }
            }
        }

    }

    @Override
    public Long requestDynamic(CanonicalRequestDynamic request) throws IOException {
        String urlStr = null, host = null;
        String domain = request.getDestDomain(), name = request.getTargetName();
        byte[] input = request.getInput();
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
        HttpURLConnection connection = null;
        LOG.log(Level.INFO, String.format("[%,d] Start getting content request: %s URL:%s", System.nanoTime(), request, urlStr));
        try {
            URL url = new URL(urlStr);
            if (host == null) {
                host = url.getHost();
            }
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(HTTP_METHOD_DYNAMIC);
            connection.setRequestProperty(HTTP_HEADER_HOST, host);
            connection.setUseCaches(false);
            connection.setRequestProperty(HTTP_HEADER_CONTENT_LENGTH, Integer.toString(input.length));
            connection.setDoOutput(true);

            try (OutputStream reqOutput = connection.getOutputStream()) {
                reqOutput.write(input);
            }
            return forwardResponse(request.getDemux(), urlStr, connection, request.getDataHandler());

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

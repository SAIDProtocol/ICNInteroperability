/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.rutgers.winlab.consumer;

import static edu.rutgers.winlab.common.NDNUtility.*;
import static edu.rutgers.winlab.icninteroperability.ndn.DomainAdapterNDN.*;
import edu.rutgers.winlab.icninteroperability.*;
import edu.rutgers.winlab.icninteroperability.canonical.*;
import java.io.*;
import java.net.*;
import java.util.logging.*;
import org.ccnx.ccn.*;
import org.ccnx.ccn.config.*;
import org.ccnx.ccn.io.*;
import org.ccnx.ccn.profiles.*;
import org.ccnx.ccn.protocol.*;

/**
 *
 * @author ubuntu
 */
public class ConsumerNDN implements Consumer {

    private static final Logger LOG = Logger.getLogger(ConsumerNDN.class.getName());

    private final CCNHandle handle;
    private final String clientName;

    public ConsumerNDN(String clientName) throws ConfigurationException, IOException {
        handle = CCNHandle.open();
        this.clientName = clientName;
    }

    public String getClientName() {
        return clientName;
    }

    private void forwardResponse(DemultiplexingEntity demux, CCNInputStream input, DataHandler output, Long time) throws IOException {
        byte[] buf = new byte[1500];
        int read;
        while ((read = input.read(buf)) > 0) {
            output.handleDataRetrieved(demux, buf, read, time, false);
        }
        output.handleDataRetrieved(demux, buf, 0, time, true);
    }

    @Override
    public Long requestStatic(CanonicalRequestStatic request, DataHandler output) throws IOException {
        String domain = request.getDestDomain(), name = request.getTargetName();
        ContentName contentName;
        try {
            if (domain.equals(CROSS_DOMAIN_HOST_NDN)) {
                contentName = ContentName.fromNative("/" + name);
            } else {
                contentName = ContentName.fromNative(String.format("/%s/%s", domain, name));
            }
            LOG.log(Level.INFO, String.format("[%,d] Created ContentName %s for request:%s", System.nanoTime(), contentName, request));

        } catch (MalformedContentNameStringException ex) {
            throw new IOException("Error in forming content name", ex);
        }
        ContentObject obj = VersioningProfile.getFirstBlockOfLatestVersion(contentName, null, null, SystemConfiguration.LONG_TIMEOUT, null, handle);
        Long version = null;
        try {
            version = VersioningProfile.getLastVersionAsTimestamp(obj.name()).getTime();
        } catch (VersionMissingException ex) {
            LOG.log(Level.INFO, String.format("[%,d] Failed in finding version in response:%s, request:%s", System.nanoTime(), obj, request));
        }
        Long exclude = request.getExclude();
        LOG.log(Level.INFO, String.format("[%,d] Got first chunk of latest version for request:%s, name=%s, version=0x%x", System.nanoTime(), request, obj.name(), version));
        if (exclude != null && (version == null || version <= exclude)) {
            LOG.log(Level.INFO, String.format("[%,d] for request:%s, latest version %d <= exclude %d, return failure", System.nanoTime(), request, version, exclude));
            throw new IOException("Cannot find a version that satisfies the request: " + request);
        }
        try (CCNInputStream cis = new CCNInputStream(obj, null, handle)) {
            forwardResponse(request.getDemux(), cis, output, version);
        }
        LOG.log(Level.INFO, String.format("[%,d] Finished getting content request:%s", System.nanoTime(), request));
        return version;
    }

    @Override
    public Long requestDynamic(CanonicalRequestDynamic request, DataHandler output) throws IOException {
        String domain = request.getDestDomain(), name = request.getTargetName();
        ContentName contentName;
        if (!name.startsWith("/")) {
            name = '/' + name;
        }
        try {
            if (domain.equals(CROSS_DOMAIN_HOST_NDN)) {
                contentName = ContentName.fromNative(name);
            } else {
                contentName = ContentName.fromNative(String.format("/%s/%s", domain, name));
            }

        } catch (MalformedContentNameStringException ex) {
            throw new IOException("Error in forming content name", ex);
        }
        // add input
        String inputStr = Component.printURI(request.getInput());
        long time = System.currentTimeMillis();
        CCNTime version = new CCNTime(time);
        contentName = new ContentName(contentName, inputStr, clientName, version);
        LOG.log(Level.INFO, String.format("[%,d] Created ContentName %s for request:%s", System.nanoTime(), contentName, request));

        try (CCNInputStream cis = new CCNInputStream(contentName, handle)) {
            forwardResponse(request.getDemux(), cis, output, time);
        }
        return time;
    }

    public static void main(String[] args) throws ConfigurationException, IOException, Component.DotDot, URISyntaxException {
        suppressNDNLog();
        String requestName = "/test/request";
        byte[] requestBody = "testRequestBody/!sd;lg??abcapoqnvqpdnb;??nabpqno".getBytes();

//        String outputName = "request.txt";
        ConsumerNDN c = new ConsumerNDN("Test");
        try (OutputStream fos = System.out) {

            DataHandler handler = new DataHandler() {
                @Override
                public void handleDataRetrieved(DemultiplexingEntity demux, byte[] data, int size, Long time, boolean finished) {
                    try {
                        fos.write(data, 0, size);
                        if (finished) {
                            fos.flush();
                        }
                    } catch (IOException ex) {
                        ex.printStackTrace(System.err);
                    }
                }

                @Override
                public void handleDataFailed(DemultiplexingEntity demux) {
                }
            };

            CanonicalRequestDynamic req = new CanonicalRequestDynamic(CROSS_DOMAIN_HOST_NDN, null, requestName, requestBody, handler);
            c.requestDynamic(req, handler);
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
        System.exit(0);

    }
}

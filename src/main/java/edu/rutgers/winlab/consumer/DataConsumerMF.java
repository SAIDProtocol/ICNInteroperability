/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.rutgers.winlab.consumer;

import edu.rutgers.winlab.common.HTTPUtility;
import static edu.rutgers.winlab.common.HTTPUtility.HTTP_METHOD_DYNAMIC;
import static edu.rutgers.winlab.common.HTTPUtility.HTTP_METHOD_STATIC;
import edu.rutgers.winlab.common.MFUtility;
import static edu.rutgers.winlab.common.MFUtility.CROSS_DOMAIN_HOST_MF;
import static edu.rutgers.winlab.common.MFUtility.DOMAIN_MAPPING_TABLE;
import edu.rutgers.winlab.icninteroperability.canonical.*;
import edu.rutgers.winlab.jmfapi.GUID;
import edu.rutgers.winlab.jmfapi.JMFAPI;
import edu.rutgers.winlab.jmfapi.JMFException;
import java.io.*;
import java.net.HttpURLConnection;
import java.util.Date;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author ubuntu
 */
public class DataConsumerMF implements DataConsumer {

    private static final Logger LOG = Logger.getLogger(DataConsumerMF.class.getName());

    private final GUID myGUID;
    JMFAPI handle;

    public DataConsumerMF(String name) throws JMFException {
        myGUID = new GUID(Integer.parseInt(name));
        handle = new JMFAPI();
        handle.jmfopen("basic", myGUID);
    }

    private MFUtility.MFRequest getRequest(CanonicalRequest request, String name) {
        MFUtility.MFRequest req = new MFUtility.MFRequest();
        if (request instanceof CanonicalRequestStatic) {
            req.Exclude = ((CanonicalRequestStatic) request).getExclude();
            req.Method = HTTP_METHOD_STATIC;
        } else if (request instanceof CanonicalRequestDynamic) {
            req.Method = HTTP_METHOD_DYNAMIC;
            req.Body = ((CanonicalRequestDynamic) request).getInput();
        }
        req.RequestID = System.currentTimeMillis();
        req.Name = name;
        return req;
    }

    private Long handleRequest(CanonicalRequest request) throws IOException {
        LOG.log(Level.INFO, String.format("Request: %s", request));
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
        MFUtility.MFRequest req = getRequest(request, name);
        byte[] buf = req.encode();
        try {
            handle.jmfsend(buf, buf.length, new GUID(dstGUID));
        } catch (JMFException ex) {
            throw new IOException("Error in sending MF request for " + request, ex);
        }
        GUID sGUID = new GUID();
        buf = new byte[MFUtility.MAX_BUF_SIZE];
        try {
            int read;
            while ((read = handle.jmfrecv_blk(sGUID, buf, buf.length)) > 0) {
                MFUtility.MFResponse resp = new MFUtility.MFResponse();
                if (!resp.decode(myGUID, sGUID, buf, read)) {
                    System.err.println("Cannot understand header, skip");
                    continue;
                }
                if (!Objects.equals(resp.RequestID, req.RequestID)) {
                    System.err.printf("Skipping, resp.reqID(%d)!=req.reqID(%d)%n", resp.RequestID, req.RequestID);
                    continue;
                }
                System.err.printf("reqID:%d status:%d  time:%s bodyLen:%d%n",
                        resp.RequestID,
                        resp.StatusCode,
                        resp.LastModified == null ? null : HTTPUtility.HTTP_DATE_FORMAT.format(new Date(resp.LastModified)),
                        resp.Body == null ? 0 : resp.Body.length);
                if (resp.StatusCode == HttpURLConnection.HTTP_OK) {
                    request.getDataHandler().handleDataRetrieved(request.getDemux(), resp.Body, resp.Body.length, resp.LastModified, true);
                } else {
                    request.getDataHandler().handleDataFailed(request.getDemux());
                }
                return resp.LastModified;
            }
            return 0L;
        } catch (JMFException ex) {
            throw new IOException("Error in reading MF response for " + request, ex);
        }
    }

    @Override
    public Long requestStatic(CanonicalRequestStatic request) throws IOException {
        return handleRequest(request);
    }

    @Override
    public Long requestDynamic(CanonicalRequestDynamic request) throws IOException {
        return handleRequest(request);
    }

}

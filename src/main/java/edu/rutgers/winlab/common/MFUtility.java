/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.rutgers.winlab.common;

import static edu.rutgers.winlab.common.HTTPUtility.CROSS_DOMAIN_HOST_IP;
import static edu.rutgers.winlab.common.HTTPUtility.HTTP_METHOD_DYNAMIC;
import static edu.rutgers.winlab.common.HTTPUtility.HTTP_METHOD_STATIC;
import static edu.rutgers.winlab.common.NDNUtility.CROSS_DOMAIN_HOST_NDN;
import edu.rutgers.winlab.icninteroperability.canonical.CanonicalRequest;
import edu.rutgers.winlab.icninteroperability.canonical.CanonicalRequestDynamic;
import edu.rutgers.winlab.icninteroperability.canonical.CanonicalRequestStatic;
import edu.rutgers.winlab.jmfapi.GUID;
import java.net.HttpURLConnection;
import java.util.Date;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author ubuntu
 */
public class MFUtility {

    public static final String CROSS_DOMAIN_HOST_MF = "INTR_MF";
    public static final int MAX_BUF_SIZE = 8 * 1024 * 1024;
    public static final HashMap<String, Integer> DOMAIN_MAPPING_TABLE = new HashMap<>();
    private static final Logger LOG = Logger.getLogger(MFUtility.class.getName());

    static {
        DOMAIN_MAPPING_TABLE.put(CROSS_DOMAIN_HOST_MF, 4096);
        DOMAIN_MAPPING_TABLE.put(CROSS_DOMAIN_HOST_NDN, 4097);
        DOMAIN_MAPPING_TABLE.put(CROSS_DOMAIN_HOST_IP, 4098);
    }
    
    public static MFUtility.MFRequest getRequest(CanonicalRequest request, String name, Long reqID) {
        MFUtility.MFRequest req = new MFUtility.MFRequest();
        if (request instanceof CanonicalRequestStatic) {
            req.Exclude = ((CanonicalRequestStatic) request).getExclude();
            req.Method = HTTP_METHOD_STATIC;
        } else if (request instanceof CanonicalRequestDynamic) {
            req.Method = HTTP_METHOD_DYNAMIC;
            req.Body = ((CanonicalRequestDynamic) request).getInput();
        }
        req.RequestID = reqID == null ? System.currentTimeMillis() : reqID;
        req.Name = name;
        return req;
    }

    /**
     * Read a line starting from ptr[0] from a buffer. Will return the read line
     * and will put the start of the new line in the ptr[0].
     *
     * @param buf the buffer to be read from.
     * @param ptr as input: the start point of the line, as output: the start
     * point of the next line
     * @return the line read.
     */
    public static String getLine(byte[] buf, int length, int[] ptr) {
        int start = ptr[0];
        if (start >= length) {
            ptr[0] = length;
            return null;
        }
        int end = start;
        for (; end < length && buf[end] != '\n'; end++) {

        }
        ptr[0] = Math.min(end + 1, length);
        if (end > 0 && buf[end - 1] == '\r') {
            end--;
        }
        return new String(buf, start, end - start);
    }

    public static class MFRequest {

        public Long RequestID;
        public String Method;
        public String Name;
        public Long Exclude;
        public byte[] Body;

        public byte[] encode() {
            String excludeLine = Exclude == null ? "" : HTTPUtility.HTTP_DATE_FORMAT.format(new Date(Exclude));
            String toSend = String.format("%d%n%s%n%s%n%s%n", RequestID, Method, Name, excludeLine);
            byte[] buf = toSend.getBytes();
            if (Body == null || Body.length == 0) {
                return buf;
            }
            byte[] ret = new byte[buf.length + Body.length];
            System.arraycopy(buf, 0, ret, 0, buf.length);
            System.arraycopy(Body, 0, ret, buf.length, Body.length);
            return ret;
        }

        public boolean decode(GUID myGUID, GUID clientGUID, byte[] buf, int length) {
            int[] ptr = {0};
            String reqIDStr = MFUtility.getLine(buf, length, ptr);
            try {
                RequestID = Long.parseLong(reqIDStr);
            } catch (Exception e) {
                LOG.log(Level.INFO, String.format("[%,d] (me:%s, client:%s) Error in reading reqID (%s) from request.", System.nanoTime(), myGUID, clientGUID, reqIDStr), e);
                RequestID = null;
                return false;
            }
            Method = MFUtility.getLine(buf, length, ptr);
            Name = MFUtility.getLine(buf, length, ptr);
            String excludeStr = MFUtility.getLine(buf, length, ptr);
            try {
                Exclude = HTTPUtility.HTTP_DATE_FORMAT.parse(excludeStr).getTime();
            } catch (Exception e) {
                LOG.log(Level.INFO, String.format("[%,d] (me:%s, client:%s, reqid:%d) Error in getting exclude field, ignore", System.nanoTime(), myGUID, clientGUID, RequestID), e);
                Exclude = null;
            }
            if (ptr[0] == length) {
                Body = null;
            } else {
                Body = new byte[length - ptr[0]];
                System.arraycopy(buf, ptr[0], Body, 0, Body.length);
            }
            return true;
        }
    }

    public static class MFResponse {

        public Long RequestID;
        public Integer StatusCode;
        public Long LastModified;
        public byte[] Body;

        public byte[] encode() {
            String toSend = String.format("%s%n%s%n%s%n",
                    RequestID == null ? "" : Long.toString(RequestID),
                    StatusCode == null ? "" : Integer.toString(StatusCode),
                    LastModified == null ? "" : HTTPUtility.HTTP_DATE_FORMAT.format(new Date(LastModified)));
            byte[] buf = toSend.getBytes();
            if (Body == null || Body.length == 0) {
                return buf;
            }
            byte[] ret = new byte[buf.length + Body.length];
            System.arraycopy(buf, 0, ret, 0, buf.length);
            System.arraycopy(Body, 0, ret, buf.length, Body.length);
            return ret;
        }

        public boolean decode(GUID me, GUID src, byte[] buf, int length) {
            int[] ptr = {0};
            String reqIDStr = MFUtility.getLine(buf, length, ptr);
            try {
                RequestID = Long.parseLong(reqIDStr);
            } catch (Exception e) {
                LOG.log(Level.INFO, String.format("[%,d] (me:%s, src:%s) Error in reading reqID (%s) from request. ", System.nanoTime(), me, src, reqIDStr), e);
                RequestID = null;
                return false;
            }
            String statusStr = MFUtility.getLine(buf, length, ptr);
            try {
                StatusCode = Integer.parseInt(statusStr);
            } catch (Exception e) {
                LOG.log(Level.INFO, String.format("[%,d] (me:%s, src:%s, reqid:%d) Error in reading status (%s) from request. ", System.nanoTime(), me, src, RequestID, statusStr), e);
                StatusCode = null;
                return false;
            }

            String lastModifiedStr = MFUtility.getLine(buf, length, ptr);
            try {
                LastModified = HTTPUtility.HTTP_DATE_FORMAT.parse(lastModifiedStr).getTime();
            } catch (Exception e) {
                LOG.log(Level.INFO, String.format("[%,d] (me:%s, client:%s, reqid:%d) Error in getting lastModified field (%s), ignore", System.nanoTime(), me, src, RequestID, lastModifiedStr), e);
                LastModified = null;
            }
            if (ptr[0] == length) {
                Body = null;
            } else {
                Body = new byte[length - ptr[0]];
                System.arraycopy(buf, ptr[0], Body, 0, Body.length);
            }
            return true;
        }
    }

    public static MFRequest readRequest(GUID myGUID, GUID clientGUID, byte[] buf, int length) {
        MFRequest ret = new MFRequest();
        return ret;
    }

    public static void main(String[] args) {
        String str = "aa\n\nbb\r\n\r\n";
        byte[] buf = str.getBytes();
        int[] start = {0};
        while (start[0] < str.length()) {
            String line = getLine(buf, buf.length, start);
            System.out.printf("line: %s %d, start: %d%n", line, line.length(), start[0]);
        }
        String line = getLine(buf, buf.length, start);
        System.out.printf("line: %s, start: %d%n", line, start[0]);
    }
}

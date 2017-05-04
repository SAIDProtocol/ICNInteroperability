/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.rutgers.winlab.provider;

import edu.rutgers.winlab.common.HTTPUtility;
import edu.rutgers.winlab.common.MFUtility;
import edu.rutgers.winlab.jmfapi.GUID;
import edu.rutgers.winlab.jmfapi.JMFAPI;
import edu.rutgers.winlab.jmfapi.JMFException;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author ubuntu
 */
public class ProviderMF {

    private static final Logger LOG = Logger.getLogger(ProviderMF.class.getName());
    private final int staticFileWaitTime;
    private boolean started = false;
    private final HashMap<Integer, StaticFileHandler> handlers = new HashMap<>();

    public ProviderMF(String mapping, int staticFileWaitTime) throws JMFException, IOException {
        this.staticFileWaitTime = staticFileWaitTime;
        try (BufferedReader reader = new BufferedReader(new FileReader(mapping))) {
            String line;
            int lineID = 0;
            while ((line = reader.readLine()) != null) {
                lineID++;
                line = line.trim();
                if (line.startsWith("#")) {
                    LOG.log(Level.INFO, String.format("Skipping comment line %d: %s", lineID, line));
                    continue;
                }
                int idx = line.indexOf(' ');
                if (idx == -1) {
                    LOG.log(Level.INFO, String.format("Skipping error line (no space): %d: %s", lineID, line));
                    continue;
                }
                int guid;
                try {
                    guid = Integer.parseInt(line.substring(0, idx));
                } catch (Exception e) {
                    LOG.log(Level.INFO, String.format("Skipping error line (cannot parse guid): %d: %s", lineID, line));
                    continue;
                }
                String fileName = line.substring(idx).trim();
                if (handlers.containsKey(guid)) {
                    LOG.log(Level.INFO, String.format("Skipping error line (duplicate GUID): %d: %s", lineID, line));
                }
                LOG.log(Level.INFO, String.format("Got line: %d: GUID:%d->File:%s", lineID, guid, fileName));
                StaticFileHandler handler = new StaticFileHandler(fileName, guid);
                handlers.put(guid, handler);
            }
        }
    }

    public synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        handlers.values().forEach(h -> new Thread(h).start());
    }

    private class StaticFileHandler implements Runnable {

        private final File file;
        private final GUID guid;
        private final JMFAPI handle;
        private final byte[] buf = new byte[MFUtility.MAX_BUF_SIZE];

        public StaticFileHandler(String file, int guid) throws JMFException {
            this.file = new File(file);
            this.guid = new GUID(guid);
            handle = new JMFAPI();
            handle.jmfopen("basic", this.guid);
        }

        private void writeBody(GUID dstGUID, long reqID, int status, long time, byte[] payload) {
            MFUtility.MFResponse resp = new MFUtility.MFResponse();
            resp.RequestID = reqID;
            resp.StatusCode = status;
            resp.LastModified = time;
            resp.Body = payload;
            byte[] toSend = resp.encode();
            try {
                handle.jmfsend(toSend, toSend.length, dstGUID);
                LOG.log(Level.INFO, String.format("[%,d] (content:%s, client:%s, reqid:%d) Response sent status=%d, len=%d", System.nanoTime(), guid, dstGUID, reqID, status, toSend.length));
            } catch (JMFException ex) {
                LOG.log(Level.SEVERE, String.format("[%,d] (content:%s, client:%s, reqid:%d) Failed in sending response", System.nanoTime(), guid, dstGUID, reqID), ex);
            }
        }

        @Override
        public void run() {
            GUID sGUID = new GUID();

            int read;
            try {
                while ((read = handle.jmfrecv_blk(sGUID, buf, buf.length)) >= 0) {
                    MFUtility.MFRequest request = new MFUtility.MFRequest();
                    if (!request.decode(guid, sGUID, buf, read)) {
                        continue;
                    }
                    if (request.Method == null || !request.Method.equals(HTTPUtility.HTTP_METHOD_STATIC)) {
                        LOG.log(Level.INFO, String.format("[%,d] (content:%s, client:%s, reqid:%d) Method (%s) not correct in request", System.nanoTime(), guid, sGUID, request.RequestID, request.Method));
                        writeBody(sGUID, request.RequestID, HttpURLConnection.HTTP_NOT_IMPLEMENTED, System.currentTimeMillis(), String.format(HTTPUtility.HTTP_RESPONSE_UNSUPPORTED_ACTION_FORMAT, request.Method).getBytes());
                        continue;
                    }
                    LOG.log(Level.INFO, String.format("[%,d] (content:%s, client:%s, reqid:%d) Got name: %s. Ignore.", System.nanoTime(), guid, sGUID, request.RequestID, request.Name));
                    LOG.log(Level.INFO, String.format("[%,d] (content:%s, client:%s, reqid:%d) STATIC exclude=%d", System.nanoTime(), guid, sGUID, request.RequestID, request.Exclude));
                    if (!file.isFile()) {
                        LOG.log(Level.INFO, String.format("(content:%s, client:%s, reqid:%d) File %s not exist", System.nanoTime(), guid, sGUID, request.RequestID, file));
                        writeBody(sGUID, request.RequestID, HttpURLConnection.HTTP_NOT_FOUND, System.currentTimeMillis(), String.format(HTTPUtility.HTTP_RESPONSE_FILE_NOT_FOUND_FORMAT, guid).getBytes());
                        continue;
                    }
                    long lastModified = file.lastModified();
                    // round the time to the next second
                    lastModified = lastModified / 1000 * 1000 + ((lastModified % 1000 == 0) ? 0 : 1000);

                    if (request.Exclude != null && request.Exclude >= lastModified) {
                        LOG.log(Level.INFO, String.format("[%,d] (content:%s, client:%s, reqid:%d) File not modified write 304", System.nanoTime(), guid, sGUID, request.RequestID));
                        writeBody(sGUID, request.RequestID, HttpURLConnection.HTTP_NOT_MODIFIED, System.currentTimeMillis(), null);
                        continue;
                    }
                    byte[] body = new byte[(int) Math.min(file.length(), MFUtility.MAX_BUF_SIZE)];
                    try (FileInputStream fis = new FileInputStream(file)) {
                        fis.read(body);
                    } catch (IOException ex) {
                        Logger.getLogger(ProviderMF.class.getName()).log(Level.SEVERE, null, ex);
                        writeBody(sGUID, request.RequestID, HttpURLConnection.HTTP_NOT_FOUND, System.currentTimeMillis(), String.format(HTTPUtility.HTTP_RESPONSE_FILE_NOT_FOUND_FORMAT, guid).getBytes());
                        continue;
                    }
                    writeBody(sGUID, request.RequestID, HttpURLConnection.HTTP_OK, lastModified, body);
                }
            } catch (JMFException ex) {
                LOG.log(Level.SEVERE, String.format("[%,d] Error in reading content from JMFAPI, contentGUID:%s, exitting", System.nanoTime(), guid), ex);
            }
        }

    }

    public static void main(String[] args) throws JMFException, IOException {
        if (args.length < 2) {
            System.out.printf("Usage: java %s <mapping> <wait>%n", ProviderIP.class.getName());
            return;
        }
        String mapping = args[0];
        int wait = Integer.parseInt(args[1]);
        System.out.printf("Starting MF Provider mapping file %s, static file wait time %d%n", mapping, wait);
        ProviderMF p = new ProviderMF(mapping, wait);
        p.start();
    }
}

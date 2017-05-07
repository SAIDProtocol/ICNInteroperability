/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.rutgers.winlab.provider;

import edu.rutgers.winlab.common.HTTPUtility;
import edu.rutgers.winlab.common.NDNUtility;
import java.io.*;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.*;
import org.ccnx.ccn.*;
import org.ccnx.ccn.config.*;
import org.ccnx.ccn.io.CCNOutputStream;
import org.ccnx.ccn.profiles.SegmentationProfile;
import org.ccnx.ccn.profiles.VersionMissingException;
import org.ccnx.ccn.profiles.VersioningProfile;
import org.ccnx.ccn.protocol.*;

/**
 *
 * @author ubuntu
 */
public class ProviderNDNDynamic {

    private static final Logger LOG = Logger.getLogger(ProviderNDNDynamic.class.getName());

    private final ContentName prefix;
    private CCNHandle handle = null;

    public ProviderNDNDynamic(ContentName prefix) throws ConfigurationException, IOException {
        this.prefix = prefix;
    }

    public synchronized void start() throws ConfigurationException, IOException {
        if (handle != null) {
            return;
        }
        handle = CCNHandle.open();
        handle.registerFilter(this.prefix, (CCNInterestHandler) this::handleInterest);
    }

    private boolean handleInterest(Interest interest) {
        if (NDNUtility.needSkip(interest.name())) {
            LOG.log(Level.INFO, String.format("[%,d] Got interest %s, but we do not handle it.", System.currentTimeMillis(), interest));
            return false;
        }
        if (!VersioningProfile.hasTerminalVersion(interest.name())) {
            LOG.log(Level.INFO, String.format("[%,d] Got interest %s, static request. We do not handle it.", System.currentTimeMillis(), interest));
            return false;
        }

        ContentName name = interest.name().postfix(prefix);
        if (SegmentationProfile.isSegment(name)) {
            name = SegmentationProfile.segmentRoot(name);
        }
        if (name.count() < 3) {
            LOG.log(Level.INFO, String.format("[%,d] Name %s does not satisfy requirement count < 3. Skip.", System.currentTimeMillis(), name));
            return false;
        }

        long time;
        try {
            time = VersioningProfile.getLastVersionAsTimestamp(name).getTime();
        } catch (VersionMissingException ex) {
            LOG.log(Level.SEVERE, String.format("[%,d] Should not reach here. I've already checked that it has version!.", System.currentTimeMillis()), ex);
            return false;
        }
        name = name.parent();

        String clientName = Component.printNative(name.component(name.count() - 1));
        name = name.parent();
        byte[] requestBody = name.component(name.count() - 1);
        String request = new String(requestBody);
        try {
            requestBody = Component.parseURI(request);
        } catch (Component.DotDot | URISyntaxException ex) {
            LOG.log(Level.SEVERE, String.format("[%,d] Error in parsing the request body: %s", System.currentTimeMillis(), request), ex);
            return false;
        }
        request = new String(requestBody);
        name = name.parent();
        LOG.log(Level.INFO, String.format("time=%s, client=%s, request=%s, name=%s", new Date(time), clientName, request, name));

        Map<String, List<String>> parameters = new HashMap<>();
        HTTPUtility.parseQuery(request, parameters);
        int sleepLen = 0;
        try {
            sleepLen = Integer.parseInt(parameters.get(ProviderIP.SLEEP_PARAM_NAME).get(0));
        } catch (Exception e) {
        }
        if (sleepLen > 0) {
            try {
                Thread.sleep(sleepLen);
            } catch (InterruptedException ex) {
                Logger.getLogger(ProviderIP.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        int bodyLen = 0;
        try {
            bodyLen = Integer.parseInt(parameters.get(ProviderIP.BODY_LEN).get(0));
        } catch (Exception e) {
        }

        byte[] response = String.format("This is a simple response from NDN dynamic provider!%nRequest time: %s%nMy Time: %s %nYou are: %s%nInput: %s%nRemaining: %s%n",
                new Date(time), new Date(System.currentTimeMillis()), clientName, request, name).getBytes();
        if (bodyLen > 0) {
            byte[] tmp = new byte[response.length + bodyLen];
            System.arraycopy(response, 0, tmp, 0, response.length);
            for (int i = 0, j = response.length; i < bodyLen; i++, j++) {
                response[j] = 'a';
            }
            response = tmp;
        }

        try (CCNOutputStream cos = new CCNOutputStream(interest.name(), handle)) {
            cos.addOutstandingInterest(interest);
            cos.write(response);
            cos.flush();
            LOG.log(Level.INFO, String.format("[%,d] Response sent.", System.currentTimeMillis()));
            return true;
        } catch (IOException ex) {
            LOG.log(Level.INFO, String.format("[%,d] Error in sending response.", System.currentTimeMillis()), ex);
            return false;
        }
    }

    public static void main(String[] args) throws ConfigurationException, IOException, MalformedContentNameStringException {
        NDNUtility.suppressNDNLog();
        if (args.length < 1) {
            System.out.printf("Usage: java %s <prefix>%n", ProviderNDNDynamic.class.getName());
            return;
        }
        ContentName prefix = ContentName.fromNative(args[0]);
        ProviderNDNDynamic providerNDNDynamic = new ProviderNDNDynamic(prefix);
        LOG.log(Level.INFO, String.format("Starting Provider NDN with prefix %s", prefix));
        providerNDNDynamic.start();
    }

}

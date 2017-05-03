/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.rutgers.winlab.provider;

import static edu.rutgers.winlab.common.NDNUtility.*;
import java.io.*;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.logging.*;
import org.ccnx.ccn.*;
import org.ccnx.ccn.config.*;
import org.ccnx.ccn.io.CCNOutputStream;
import static org.ccnx.ccn.profiles.SegmentationProfile.*;
import org.ccnx.ccn.profiles.VersionMissingException;
import static org.ccnx.ccn.profiles.VersioningProfile.*;
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
        if (needSkip(interest.name())) {
            LOG.log(Level.INFO, String.format("[%,d] Got interest %s, but we do not handle it.", System.nanoTime(), interest));
            return false;
        }
        if (!hasTerminalVersion(interest.name())) {
            LOG.log(Level.INFO, String.format("[%,d] Got interest %s, static request. We do not handle it.", System.nanoTime(), interest));
            return false;
        }

        ContentName name = interest.name().postfix(prefix);
        if (isSegment(name)) {
            name = segmentRoot(name);
        }
        if (name.count() < 3) {
            LOG.log(Level.INFO, String.format("[%,d] Name %s does not satisfy requirement count < 3. Skip.", System.nanoTime(), name));
            return false;
        }

        long time;
        try {
            time = getLastVersionAsTimestamp(name).getTime();
        } catch (VersionMissingException ex) {
            LOG.log(Level.SEVERE, String.format("[%,d] Should not reach here. I've already checked that it has version!.", System.nanoTime()), ex);
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
            LOG.log(Level.SEVERE, String.format("[%,d] Error in parsing the request body: %s", System.nanoTime(), request), ex);
            return false;
        }
        request = new String(requestBody);
        name = name.parent();

        LOG.log(Level.INFO, String.format("time=%s, client=%s, request=%s, name=%s", new Date(time), clientName, request, name));
        byte[] response = String.format("This is a simple response!%nRequest time: %s%nMy Time: %s %nYou are: %s%nInput: %s%nRemaining: %s%n",
                new Date(time), new Date(System.currentTimeMillis()), clientName, request, name).getBytes();

        try (CCNOutputStream cos = new CCNOutputStream(interest.name(), handle)) {
            cos.addOutstandingInterest(interest);
            cos.write(response);
            cos.flush();
            LOG.log(Level.INFO, String.format("[%,d] Response sent.", System.nanoTime()));
            return true;
        } catch (IOException ex) {
            LOG.log(Level.INFO, String.format("[%,d] Error in sending response.", System.nanoTime()), ex);
            return false;
        }
    }

    public static void main(String[] args) throws ConfigurationException, IOException, MalformedContentNameStringException {
        if (args.length < 1) {
            System.out.printf("Usage: java %s <prefix>%n", ProviderNDNDynamic.class.getName());
            return;
        }
        ProviderNDNDynamic providerNDNDynamic = new ProviderNDNDynamic(ContentName.fromNative(args[0]));
        providerNDNDynamic.start();
    }

}

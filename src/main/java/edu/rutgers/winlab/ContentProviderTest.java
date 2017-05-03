/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.rutgers.winlab;

import static edu.rutgers.winlab.common.NDNUtility.isOldHeader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.ccnx.ccn.CCNHandle;
import org.ccnx.ccn.CCNInterestHandler;
import org.ccnx.ccn.config.ConfigurationException;
import org.ccnx.ccn.impl.support.Log;
import org.ccnx.ccn.io.CCNVersionedOutputStream;
import org.ccnx.ccn.profiles.CommandMarker;
import static org.ccnx.ccn.profiles.SegmentationProfile.isFirstSegment;
import static org.ccnx.ccn.profiles.SegmentationProfile.isSegment;
import org.ccnx.ccn.profiles.VersioningProfile;
import static org.ccnx.ccn.profiles.metadata.MetadataProfile.isHeader;
import org.ccnx.ccn.protocol.CCNTime;
import org.ccnx.ccn.protocol.Component;
import org.ccnx.ccn.protocol.ContentName;
import org.ccnx.ccn.protocol.Exclude;
import org.ccnx.ccn.protocol.ExcludeComponent;
import org.ccnx.ccn.protocol.Interest;

/**
 *
 * @author ubuntu
 */
public class ContentProviderTest implements Runnable {

    private static final Logger LOG = Logger.getLogger(ContentProviderTest.class.getName());
    private static CCNHandle handle;

    static {
        Level[] levels = Log.getLevels();
        Arrays.setAll(levels, i -> Level.OFF);
        Log.setLevels(levels);
        try {
            handle = CCNHandle.open();
        } catch (ConfigurationException | IOException ex) {
            Logger.getLogger(ContentProviderTest.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    public static void main(String[] args) throws ConfigurationException, IOException {
        handle.registerFilter(ContentName.ROOT, (CCNInterestHandler) ContentProviderTest::handleInterest);

    }

    public static boolean handleInterest(Interest interest) {
        if ((isSegment(interest.name()) && !isFirstSegment(interest.name()))
                || interest.name().contains(CommandMarker.COMMAND_MARKER_BASIC_ENUMERATION.getBytes())
                || isHeader(interest.name())
                || isOldHeader(interest.name())) {
            LOG.log(Level.INFO, String.format("[%,d] Got interest %s, but we do not handle them.", System.nanoTime(), interest));
            return false;
        }
        new Thread(new ContentProviderTest(interest)).start();
        return true;
    }

    private final Interest interest;

    public ContentProviderTest(Interest interest) {
        this.interest = interest;
    }
    

    @Override
    public void run() {
        LOG.log(Level.INFO, String.format("[%,d] Handling interest %s.", System.nanoTime(), interest));
//        try {
//            Thread.sleep(1000);
//        } catch (InterruptedException ex) {
//        }
        Exclude e = interest.exclude();
        for (int i = 0; i < e.size(); i++) {
            Exclude.Element elem = e.value(i);
            if (elem instanceof ExcludeComponent) {
                byte[] bytes = ((ExcludeComponent) elem).getBytes();
                if (VersioningProfile.isVersionComponent(bytes)) {
                    CCNTime time = VersioningProfile.getVersionComponentAsTimestamp(bytes);
                    LOG.log(Level.INFO, String.format("[%,d] Elem %d (%s) is a version component. Time=%s %s", 
                            System.nanoTime(), i, Component.printURI(((ExcludeComponent) elem).getComponent()), time, new Date(time.getTime())));
                } else {
                    LOG.log(Level.INFO, String.format("[%,d] Elem %d (%s) is not a version component.", System.nanoTime(), i, Component.printURI(((ExcludeComponent) elem).getComponent())));
                }
            }
        }
        

        try (CCNVersionedOutputStream cos = new CCNVersionedOutputStream(interest.name(), handle)) {

            LOG.log(Level.INFO, String.format("[%,d] Exclude:%s version:%s, match:%b.",
                    System.nanoTime(), interest.exclude(), new Date(cos.getVersion().getTime()),
                    interest.exclude().match(cos.getVersion().getComponent())));

            cos.addOutstandingInterest(interest);
            LOG.log(Level.INFO, String.format("[%,d] Sending response to %s with version %s.", System.nanoTime(), interest, cos.getVersion()));
            byte[] buf = String.format("This is a quick response, time: %s", new Date()).getBytes();
            cos.write(buf);
            cos.flush();
            LOG.log(Level.INFO, String.format("[%,d] Sent response response to %s with version %x.", System.nanoTime(), interest, cos.getVersion().toBinaryTimeAsLong()));
        } catch (IOException ex) {
            LOG.log(Level.INFO, String.format("[%,d] Failed in writing response to %s.", System.nanoTime(), interest), ex);
        }

    }

}

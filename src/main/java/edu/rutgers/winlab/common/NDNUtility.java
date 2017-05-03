/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.rutgers.winlab.common;

import java.util.Arrays;
import java.util.logging.Level;
import org.ccnx.ccn.impl.support.Log;
import static org.ccnx.ccn.profiles.CommandMarker.isCommandComponent;
import org.ccnx.ccn.profiles.SegmentationProfile;
import static org.ccnx.ccn.profiles.SegmentationProfile.isFirstSegment;
import static org.ccnx.ccn.profiles.SegmentationProfile.isSegment;
import org.ccnx.ccn.profiles.VersioningProfile;
import org.ccnx.ccn.profiles.metadata.MetadataProfile;
import static org.ccnx.ccn.profiles.metadata.MetadataProfile.OLD_METADATA_NAMESPACE;
import static org.ccnx.ccn.profiles.metadata.MetadataProfile.isHeader;
import org.ccnx.ccn.protocol.ContentName;
import org.ccnx.ccn.protocol.Exclude;
import org.ccnx.ccn.protocol.ExcludeComponent;

/**
 *
 * @author ubuntu
 */
public class NDNUtility {

    public static final String CROSS_DOMAIN_HOST_NDN = "INTR_NDN";

    public static boolean isOldHeader(ContentName potentialHeaderName) {

        if (SegmentationProfile.isSegment(potentialHeaderName)) {
            potentialHeaderName = SegmentationProfile.segmentRoot(potentialHeaderName);
        }

        // Header itself is likely versioned.
        if (VersioningProfile.isVersionComponent(potentialHeaderName.lastComponent())) {
            potentialHeaderName = potentialHeaderName.parent();
        }

        if (potentialHeaderName.count() < 2) {
            return false;
        }

        if (!Arrays.equals(potentialHeaderName.lastComponent(), MetadataProfile.HEADER_NAME)) {
            return false;
        }
        return Arrays.equals(potentialHeaderName.component(potentialHeaderName.count() - 2), OLD_METADATA_NAMESPACE);
    }

    public static Long getLastTimeFromExclude(Exclude exclude) {
        Long ret = null;
        for (int i = 0; i < exclude.size(); i++) {
            Exclude.Element elem = exclude.value(i);
            if (elem instanceof ExcludeComponent) {
                ExcludeComponent ec = (ExcludeComponent) elem;
                byte[] bytes = ec.getBytes();
                if (VersioningProfile.isVersionComponent(bytes)) {
                    ret = VersioningProfile.getVersionComponentAsTimestamp(bytes).getTime();
                }
            }
        }
        return ret;
    }

    public static void suppressNDNLog() {
        Level[] levels = Log.getLevels();
        Arrays.setAll(levels, i -> Level.OFF);
        Log.setLevels(levels);
    }

    public static boolean needSkip(ContentName name) {
        if (isSegment(name) && !isFirstSegment(name)) {
            return true;
        }
        if (isHeader(name) || isOldHeader(name)) {
            return true;
        }
        for (int i = 0; i < name.count(); i++) {
            if (isCommandComponent(name.component(i))) {
                return true;
            }
        }
        return false;
    }

}

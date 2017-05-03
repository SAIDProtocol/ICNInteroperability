/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.rutgers.winlab.consumer;

import edu.rutgers.winlab.icninteroperability.canonical.*;
import java.io.*;

/**
 *
 * @author ubuntu
 */
public interface DataConsumer {

    /**
     * Request a static content and write it to an output stream.
     *
     * @param request the request to be sent.
     * @return The last modified time, null if not specified by the server.
     * @throws java.io.IOException
     */
    public Long requestStatic(CanonicalRequestStatic request) throws IOException;

    /**
     * Request a dynamic content and write it to an output stream.
     *
     * @param request the request to be sent.
     * @return The last modified time, null if not specified by the server.
     * @throws java.io.IOException
     */
    public Long requestDynamic(CanonicalRequestDynamic request) throws IOException;

    public default Long request(CanonicalRequest request) throws IOException {
        if (request instanceof CanonicalRequestStatic) {
            return requestStatic((CanonicalRequestStatic) request);
        } else if (request instanceof CanonicalRequestDynamic) {
            return requestDynamic((CanonicalRequestDynamic) request);
        }
        throw new IOException("Cannot understand request: " + request);
    }
}

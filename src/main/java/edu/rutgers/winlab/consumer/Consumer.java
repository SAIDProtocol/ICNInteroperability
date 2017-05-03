/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.rutgers.winlab.consumer;

import edu.rutgers.winlab.icninteroperability.*;
import edu.rutgers.winlab.icninteroperability.canonical.*;
import java.io.*;

/**
 *
 * @author ubuntu
 */
public interface Consumer {

    /**
     * Request a static content and write it to an output stream.
     * 
     * @param request the request to be sent.
     * @param output the output stream to write to.
     * @return The last modified time, null if not specified by the server.
     * @throws java.io.IOException
     */
    public Long requestStatic(CanonicalRequestStatic request, DataHandler output) throws IOException;

    /**
     * Request a dynamic content and write it to an output stream.
     * 
     * @param request the request to be sent.
     * @param output the output stream to write to.
     * @return The last modified time, null if not specified by the server. 
     * @throws java.io.IOException
     */
    public Long requestDynamic(CanonicalRequestDynamic request, DataHandler output) throws IOException;

    
    public static void main(String[] args) {
        
    }
}

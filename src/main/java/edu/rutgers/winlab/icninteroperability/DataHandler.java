/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.rutgers.winlab.icninteroperability;

/**
 *
 * @author ubuntu
 */
public interface DataHandler {

    /**
     * Called when the data is retrieved.
     *
     * @param demux the demultiplexing entity.
     * @param data the data retrieved.
     * @param size size of the data.
     * @param time the last modified time.
     * @param finished if the content is finished.
     */
    public void handleDataRetrieved(DemultiplexingEntity demux, byte[] data, int size, Long time, boolean finished);

    /**
     * Called when the data cannot be retrieved.
     *
     * @param demux the demultiplexing eneity.
     */
    public void handleDataFailed(DemultiplexingEntity demux);
}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Manifest;

/**
 * This interface represents the consumer of a status update to be displayed
 * in the GUI interface.
 */
public interface StatusConsumer {
    
    /**
     * Here is the information used to update the status.
     * 
     * @param id the identity of the object just processed
     * @param messages a collection of messages about the processing
     * @return true if processing is to continue
     */
    boolean updateStatus(String id, String[] messages);
}

/**
 * $Id$
 */
package com.untangle.node.virus;

import java.util.List;
import java.util.Date;

import com.untangle.uvm.node.Node;
import com.untangle.uvm.node.GenericRule;
import com.untangle.uvm.node.EventLogEntry;

/**
 * Interface to the Virus Blocker nodes.
 */
public interface VirusNode extends Node
{
    void setSettings(VirusSettings virusSettings);
    VirusSettings getSettings();

    String getName();
    
    EventLogEntry[] getWebEventQueries();
    EventLogEntry[] getFtpEventQueries();
    EventLogEntry[] getMailEventQueries();

    Date getLastSignatureUpdate();

    void setHttpFileExtensions(List<GenericRule> fileExtensions);

    void setHttpMimeTypes(List<GenericRule> fileExtensions);
}

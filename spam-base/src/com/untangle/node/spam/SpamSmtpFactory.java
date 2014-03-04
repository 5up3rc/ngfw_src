/**
 * $Id$
 */
package com.untangle.node.spam;

import org.apache.log4j.Logger;

import com.untangle.node.smtp.MailExport;
import com.untangle.node.smtp.MailExportFactory;
import com.untangle.node.smtp.SmtpNodeSettings;
import com.untangle.node.smtp.quarantine.QuarantineNodeView;
import com.untangle.node.smtp.safelist.SafelistNodeView;
import com.untangle.node.token.TokenHandler;
import com.untangle.node.token.TokenHandlerFactory;
import com.untangle.uvm.vnet.NodeTCPSession;
import com.untangle.uvm.vnet.TCPNewSessionRequest;

public class SpamSmtpFactory implements TokenHandlerFactory
{
    private final Logger m_logger = Logger.getLogger(getClass());

    private MailExport m_mailExport;
    private QuarantineNodeView m_quarantine;
    private SafelistNodeView m_safelist;
    private SpamNodeImpl m_spamImpl;

    public SpamSmtpFactory(SpamNodeImpl impl) 
    {
        m_mailExport = MailExportFactory.factory().getExport();
        m_quarantine = m_mailExport.getQuarantineNodeView();
        m_safelist = m_mailExport.getSafelistNodeView();
        m_spamImpl = impl;
    }

    public TokenHandler tokenHandler(NodeTCPSession session) {
        SpamSettings spamSettings = m_spamImpl.getSettings();
        SpamSmtpConfig spamConfig = spamSettings.getSmtpConfig();

        SmtpNodeSettings casingSettings = m_mailExport.getExportSettings();
        long timeout = casingSettings.getSmtpTimeout();
        
        return new SpamSmtpHandler(session, timeout, timeout, m_spamImpl, spamConfig, m_quarantine, m_safelist);
    }

    public void handleNewSessionRequest(TCPNewSessionRequest tsr)
    {
        SpamSettings spamSettings = m_spamImpl.getSettings();
        SpamSmtpConfig spamConfig = spamSettings.getSmtpConfig();

        // Note that we may *****NOT***** release the session here.  This is because
        // the mail casings currently assume that there will be at least one node
        // inline at all times.  The contained node's state machine handles some
        // of the casing's job currently. 10/06 jdi
        if(!spamConfig.getScan()) {
            return;
        }

        int activeCount = m_spamImpl.getScanner().getActiveScanCount();
        if (SpamLoadChecker.reject(activeCount, m_logger, spamConfig.getScanLimit(), spamConfig.getLoadLimit())) {
            m_logger.warn("Load too high, rejecting connection from: " + tsr.getOrigClientAddr());
            tsr.rejectReturnRst();
        }
    }
}

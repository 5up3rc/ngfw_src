/*
 * $HeadURL$
 * Copyright (c) 2003-2007 Untangle, Inc.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License, version 2,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * AS-IS and WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, TITLE, or
 * NONINFRINGEMENT.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package com.untangle.node.virus;

import com.untangle.node.mail.papi.*;
import com.untangle.node.mail.papi.smtp.*;
import com.untangle.node.mail.papi.smtp.sapi.Session;
import com.untangle.node.token.TokenHandler;
import com.untangle.node.token.TokenHandlerFactory;
import com.untangle.uvm.vnet.*;
import org.apache.log4j.Logger;

public class VirusSmtpFactory
    implements TokenHandlerFactory {

    private final Logger m_logger =
        Logger.getLogger(VirusSmtpFactory.class);

    private final VirusNodeImpl m_virusImpl;
    private final MailExport m_mailExport;

    VirusSmtpFactory(VirusNodeImpl node) {
        m_virusImpl = node;
        m_mailExport = MailExportFactory.factory().getExport();
    }

    public TokenHandler tokenHandler(TCPSession session) {

        VirusSMTPConfig virusConfig = m_virusImpl.getVirusSettings().getBaseSettings().getSmtpConfig();

        if(!virusConfig.getScan()) {
            m_logger.debug("Scanning disabled.  Return passthrough token handler");
            return Session.createPassthruSession(session);
        }

        MailNodeSettings casingSettings = m_mailExport.getExportSettings();
        return new Session(session,
                           new SmtpSessionHandler(session,
                                                  casingSettings.getSmtpTimeout(),
                                                  casingSettings.getSmtpTimeout(),
                                                  m_virusImpl,
                                                  virusConfig));
    }

    public void handleNewSessionRequest(TCPNewSessionRequest tsr)
    {
    }
}

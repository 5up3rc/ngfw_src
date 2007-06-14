/*
 * $HeadURL:$
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
package com.untangle.node.mail.web.euv.tags;

import com.untangle.node.mail.web.euv.Constants;


/**
 * Outputs constants.  Provides some basic indirection, so we
 * don't have to (a) hardcode values in the pages or (b) use
 * Java directly to dereference the "Constants" Object
 */
public final class ConstantsTag
    extends SingleValueTag {

    private String m_keyName;
    private String m_valueName;

    public void setKeyName(String s) {
        m_keyName = s;
    }
    public String getKeyName() {
        return m_keyName;
    }
    public void setValueName(String s) {
        m_valueName = s;
    }
    public String getValueName() {
        return m_valueName;
    }

    @Override
    protected String getValue() {
        String s = null;
        if(getKeyName() != null) {
            String keyName = getKeyName().toLowerCase().trim();

            if(keyName.equals(Constants.AUTH_TOKEN_RP)) {
                s = Constants.AUTH_TOKEN_RP;
            } else if(keyName.equals(Constants.ACTION_RP)) {
                s = Constants.ACTION_RP;
            } else if(keyName.equals(Constants.MAIL_ID_RP)) {
                s = Constants.MAIL_ID_RP;
            } else if(keyName.equals(Constants.REQ_DIGEST_ADDR_RP)) {
                s = Constants.REQ_DIGEST_ADDR_RP;
            } else if(keyName.equals(Constants.SORT_BY_RP)) {
                s = Constants.SORT_BY_RP;
            } else if(keyName.equals(Constants.SORT_ASCEND_RP)) {
                s = Constants.SORT_ASCEND_RP;
            } else if(keyName.equals(Constants.FIRST_RECORD_RP)) {
                s = Constants.FIRST_RECORD_RP;
            } else if(keyName.equals(Constants.ROWS_PER_PAGE_RP)) {
                s = Constants.ROWS_PER_PAGE_RP;
            } else if(keyName.equals(Constants.SAFELIST_TARGET_ADDR_RP)) {
                s = Constants.SAFELIST_TARGET_ADDR_RP;
            }
        } else if(getValueName() != null) {
            String valueName = getValueName().trim().toLowerCase();

            if(valueName.equals(Constants.PURGE_RV)) {
                s = Constants.PURGE_RV;
            } else if(valueName.equals(Constants.RESCUE_RV)) {
                s = Constants.RESCUE_RV;
            } else if(valueName.equals(Constants.REFRESH_RV)) {
                s = Constants.REFRESH_RV;
            } else if(valueName.equals(Constants.VIEW_INBOX_RV)) {
                s = Constants.VIEW_INBOX_RV;
            } else if(valueName.equals(Constants.SAFELIST_VIEW_RV)) {
                s = Constants.SAFELIST_VIEW_RV;
            } else if(valueName.equals(Constants.SAFELIST_ADD_RV)) {
                s = Constants.SAFELIST_ADD_RV;
            } else if(valueName.equals(Constants.SAFELIST_REMOVE_RV)) {
                s = Constants.SAFELIST_REMOVE_RV;
            } else if(valueName.equals(Constants.VIEW_INBOX_RV)) {
                s = Constants.VIEW_INBOX_RV;
            }
        }

        return s;
    }
}

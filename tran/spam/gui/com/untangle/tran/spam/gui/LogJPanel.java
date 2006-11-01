/*
 * Copyright (c) 2003-2006 Untangle Networks, Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Untangle Networks, Inc. ("Confidential Information"). You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.untangle.tran.spam.gui;

import java.util.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.*;

import com.untangle.gui.transform.*;
import com.untangle.gui.widgets.editTable.*;
import com.untangle.gui.util.*;
import com.untangle.mvvm.logging.EventRepository;
import com.untangle.mvvm.logging.EventManager;
import com.untangle.mvvm.logging.RepositoryDesc;
import com.untangle.mvvm.tran.Transform;
import com.untangle.tran.spam.*;

public class LogJPanel extends MLogTableJPanel {

    private static final String SPAM_EVENTS_STRING = "Spam detected events";

    public LogJPanel(Transform transform, MTransformControlsJPanel mTransformControlsJPanel){
        super(transform, mTransformControlsJPanel);

        final SpamTransform spam = (SpamTransform)logTransform;

        depthJSlider.addChangeListener(new ChangeListener() {
                public void stateChanged(ChangeEvent ce) {
                    int v = depthJSlider.getValue();
                    EventManager<SpamEvent> em = spam.getEventManager();
                    em.setLimit(v);
                }
            });

        setTableModel(new LogTableModel());

        EventManager<SpamEvent> eventManager = spam.getEventManager();
        for (RepositoryDesc fd : eventManager.getRepositoryDescs()) {
            queryJComboBox.addItem(fd.getName());
        }
    }

    protected void refreshSettings(){
        SpamTransform spam = (SpamTransform)logTransform;
        EventManager<SpamEvent> em = spam.getEventManager();
        EventRepository<SpamEvent> ef = em.getRepository((String)queryJComboBox.getSelectedItem());
        settings = ef.getEvents();
    }

    class LogTableModel extends MSortedTableModel<Object>{

        public TableColumnModel getTableColumnModel(){
            DefaultTableColumnModel tableColumnModel = new DefaultTableColumnModel();
            //                                 #   min  rsz    edit   remv   desc   typ           def
            addTableColumn( tableColumnModel,  0,  150, true,  false, false, false, Date.class,   null, "timestamp" );
            addTableColumn( tableColumnModel,  1,   90, true,  false, false, false, String.class, null, "action" );
            addTableColumn( tableColumnModel,  2,  165, true,  false, false, false, IPPortString.class, null, "client" );
            addTableColumn( tableColumnModel,  3,  100, true,  false, false, true,  String.class, null, "subject" );
            addTableColumn( tableColumnModel,  4,  100, true,  false, false, false, String.class, null, "receiver" );
            addTableColumn( tableColumnModel,  5,  100, true,  false, false, false, String.class, null, "sender" );
            addTableColumn( tableColumnModel,  6,   55, true,  false, false, false, Float.class,  null, sc.html("SPAM<br>score") );
            addTableColumn( tableColumnModel,  7,  100, true,  false, false, false, String.class, null, sc.html("direction") );
            addTableColumn( tableColumnModel,  8,  165, true,  false, false, false, IPPortString.class, null, "server" );
            return tableColumnModel;
        }

        public void generateSettings(Object settings, Vector<Vector> tableVector, boolean validateOnly) throws Exception {}


        public Vector<Vector> generateRows(Object settings){
            List<SpamEvent> requestLogList = (List<SpamEvent>) settings;
            Vector<Vector> allEvents = new Vector<Vector>(requestLogList.size());
            Vector event;

            for( SpamEvent requestLog : requestLogList ){
                event = new Vector(9);
                event.add( requestLog.getTimeStamp() );
                event.add( requestLog.getActionName() );
                event.add( new IPPortString(requestLog.getClientAddr(), requestLog.getClientPort()) );
                event.add( requestLog.getSubject() );
                event.add( requestLog.getReceiver() );
                event.add( requestLog.getSender() );
                event.add( requestLog.getScore() );
                event.add( requestLog.getDirectionName() );
                event.add( new IPPortString(requestLog.getServerAddr(), requestLog.getServerPort()) );
                allEvents.add( event );
            }

            return allEvents;
        }
    }

}

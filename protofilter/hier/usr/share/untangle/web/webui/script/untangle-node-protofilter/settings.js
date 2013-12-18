if (!Ung.hasResource["Ung.Protofilter"]) {
    Ung.hasResource["Ung.Protofilter"] = true;
    Ung.NodeWin.registerClassName('untangle-node-protofilter', 'Ung.Protofilter');

    Ext.define('Ung.Protofilter',{
        extend: 'Ung.NodeWin',
        panelStatus: null,
        gridProtocolList: null,
        gridEventLog: null,
        initComponent: function() {
            this.buildStatus();
            this.buildProtocolList();
            this.buildEventLog();
            // builds the tab panel with the tabs
            this.buildTabPanel([this.panelStatus, this.gridProtocolList, this.gridEventLog]);
            this.callParent(arguments);
        },
        // Status Panel
        buildStatus: function() {
            this.panelStatus = Ext.create('Ext.panel.Panel',{
                name: 'Status',
                helpSource: 'application_control_lite_status',
                parentId: this.getId(),
                isDirty: function() {
                    return false;
                },
                title: this.i18n._('Status'),
                cls: 'ung-panel',
                autoScroll: true,
                defaults: {
                    xtype: 'fieldset',
                    buttonAlign: 'left'
                },
                items: [{
                    title: this.i18n._('Status'),
                    cls: 'description',
                    html: Ext.String.format(this.i18n._("Application Control Lite logs and blocks sessions using custom signatures on the session content."))
                }, {
                    title: this.i18n._(' '),
                    labelWidth: 230,
                    defaults: {
                        xtype: "textfield",
                        disabled: true
                    },
                    items: [{
                        fieldLabel: this.i18n._('Total Signatures Available'),
                        name: 'Total Signatures Available',
                        value: this.getRpcNode().getPatternsTotal()
                    }, {
                        fieldLabel: this.i18n._('Total Signatures Logging'),
                        name: 'Total Signatures Logging',
                        value: this.getRpcNode().getPatternsLogged()
                    }, {
                        fieldLabel: this.i18n._('Total Signatures Blocking'),
                        name: 'Total Signatures Blocking',
                        value: this.getRpcNode().getPatternsBlocked()
                    }]
                }, {
                    title: this.i18n._('Note'),
                    cls: 'description',
                    html: Ext.String.format(this.i18n._("Caution and discretion is advised in configuring Application Control Lite at the the risk of harmful false positives."))
                }]
            });
        },
        // Protocol list grid
        buildProtocolList: function() {

            this.gridProtocolList = Ext.create('Ung.EditorGrid',{
                settingsCmp: this,
                name: 'Signatures',
                helpSource: 'application_control_lite_signatures',
                paginated: false,
                dataProperty: "patterns",
                emptyRow: {
                    "protocol": this.i18n._("[no protocol]"),
                    "category": this.i18n._("[no category]"),
                    "log": false,
                    "blocked": false,
                    "description": this.i18n._("[no description]"),
                    "definition": this.i18n._("[no signature]")
                },
                title: this.i18n._("Signatures"),
                // the column is autoexpanded if the grid width permits
                recordJavaClass: "com.untangle.node.protofilter.ProtoFilterPattern",
                // the list of fields
                fields: [{
                    name: 'id',
                    type: 'int'
                },{
                    name: 'alert',
                    type: 'boolean'
                },{
                    name: 'metavizeId',
                    type: 'int'
                },{
                    name: 'quality',
                    type: 'string'
                },{
                    name: 'readOnly',
                    type: 'boolean'
                },
                // this field is internationalized so a converter was
                // added
                {
                    name: 'protocol',
                    type: 'string'
                },{
                    name: 'category',
                    type: 'string'
                }, {
                    name: 'log',
                    type: 'boolean'
                }, {
                    name: 'blocked',
                    type: 'boolean'
                }, {
                    name: 'description',
                    type: 'string'
                }, {
                    name: 'definition',
                    type: 'string'
                }],
                // the list of columns for the column model
                columns: [{
                    header: this.i18n._("Protocol"),
                    width: 200,
                    dataIndex: 'protocol',
                    editor: {
                        xtype:'textfield',
                        allowBlank:false
                    }
                }, 
                {
                    header: this.i18n._("Category"),
                    width: 200,
                    dataIndex: 'category',
                    editor: {
                        xtype:'textfield',
                        allowBlank:false
                    }
                }, 
                {
                    xtype:'checkcolumn',
                    header: "<b>" + this.i18n._("Block") + "</b>",
                    dataIndex: 'blocked',
                    resizable: false,
                    width:55
                },
                {
                    xtype:'checkcolumn',
                    header: "<b>" + this.i18n._("Log") + "</b>",
                    dataIndex: 'log',
                    resizable: false,
                    width:55
                },
                {
                    header: this.i18n._("Description"),
                    width: 200,
                    dataIndex: 'description',
                    flex: 1,
                    editor:{
                        xtype:'textfield',
                        allowBlank: false
                        }
                }],
                sortField: 'category',
                columnsDefaultSortable: true,
                // the row input lines used by the row editor window
                rowEditorInputLines: [{
                    xtype:'textfield',
                    name: "Protocol",
                    dataIndex: "protocol",
                    fieldLabel: this.i18n._("Protocol"),
                    allowBlank: false,
                    width: 400
                }, {
                    xtype:'textfield',
                    name: "Category",
                    dataIndex: "category",
                    fieldLabel: this.i18n._("Category"),
                    allowBlank: false,
                    width: 400
                }, 
                {
                    xtype:'checkbox',
                    name: "Block",
                    dataIndex: "blocked",
                    fieldLabel: this.i18n._("Block")
                },
                {
                    xtype:'checkbox',
                    name: "Log",
                    dataIndex: "log",
                    fieldLabel: this.i18n._("Log")
                },
                {
                    xtype:'textarea',
                    name: "Description",
                    dataIndex: "description",
                    fieldLabel: this.i18n._("Description"),
                    width: 400,
                    height: 60
                },
                {  
                    xtype:'textarea',
                    name: "Signature",
                    dataIndex: "definition",
                    fieldLabel: this.i18n._("Signature"),
                    allowBlank: false,
                    width: 400,
                    height: 60
                }]
            });
        },
        
        buildEventLog: function() {
            this.gridEventLog = Ung.CustomEventLog.buildSessionEventLog (this, 'EventLog', i18n._('Event Log'), 
                    'application_control_lite_event_log', 
                    ['time_stamp','protofilter_blocked','c_client_addr','username','c_server_addr','protofilter_protocol'], this.getRpcNode().getEventQueries); 
        },
        
        beforeSave: function(isApply, handler) {
            this.getSettings().patterns.list = this.gridProtocolList.getPageList();
            handler.call(this, isApply);
        }
    });
}
//@ sourceURL=protofilter-settings.js

if (!Ung.hasResource["Ung.OpenVPN"]) {
    Ung.hasResource["Ung.OpenVPN"] = true;
    Ung.NodeWin.registerClassName('untangle-node-openvpn', "Ung.OpenVPN");

    Ext.namespace('Ung');
    Ext.namespace('Ung.Node');
    Ext.namespace('Ung.Node.OpenVPN');

    Ext.define('Ung.Node.OpenVPN.DownloadClient', {
        extend: 'Ung.Window',
        constructor: function( config ) {
            this.title = config.i18n._('Download OpenVPN Client');
            this.i18n = config.i18n;
            this.node = config.node;
            this.callParent(arguments);
        },
        initComponent: function() {
            this.bbar = ['->', {
                name: 'close',
                iconCls: 'cancel-icon',
                text: this.i18n._('Close'),
                handler: Ext.bind(this.close, this )
            }];
            this.items = {
                xtype: 'panel',
                items: [{
                    xtype: 'fieldset',
                    cls: "description",
                    title: this.i18n._('Download'),
                    labelWidth: 150,
                    items: [{
                        html: this.i18n._('These files can be used to configure your Remote Clients.'),
                        border: false,
                        cls: "description"
                    }, {
                        name: 'downloadWindowsInstaller',
                        html:  " ",
                        border: false,
                        height: 25,
                        cls: "description"
                    }, {
                        name: 'downloadGenericConfigurationFile',
                        html: " ",
                        border: false,
                        height: 25,
                        cls: "description"
                    }, {
                        name: 'downloadUntangleConfigurationFile',
                        html: " ",
                        border: false,
                        height: 25,
                        cls: "description"
                    }]
                }]
            };
            this.callParent(arguments);
        },
        closeWindow: function() {
            this.hide();
        },
        populate: function( record ) {
            this.record = record;
            this.show();

            if(!this.downloadWindowsInstallerEl) {
                this.downloadWindowsInstallerEl = this.items.get(0).down('[name="downloadWindowsInstaller"]').getEl();
            }
            this.downloadWindowsInstallerEl.dom.innerHTML = this.i18n._('Loading...');
            if(!this.downloadGenericConfigurationFileEl) {
                this.downloadGenericConfigurationFileEl = this.items.get(0).down('[name="downloadGenericConfigurationFile"]').getEl();
            }
            this.downloadGenericConfigurationFileEl.dom.innerHTML = this.i18n._('Loading...');
            if(!this.downloadUntangleConfigurationFileEl) {
                this.downloadUntangleConfigurationFileEl = this.items.get(0).down('[name="downloadUntangleConfigurationFile"]').getEl();
            }
            this.downloadUntangleConfigurationFileEl.dom.innerHTML = this.i18n._('Loading...');
            
            Ext.MessageBox.wait(this.i18n._( "Building OpenVPN Client..." ), this.i18n._( "Please Wait" ));
            // populate download links
            var loadSemaphore = 2;
            this.node.getClientDistributionDownloadLink( Ext.bind(function(result, exception) {
                if(Ung.Util.handleException(exception)) return;

                this.downloadWindowsInstallerEl.dom.innerHTML = '<a href="' + result + '" target="_blank">' +
                    this.i18n._('Click here to download this client\'s Windows setup.exe file.') + '</a>';

                loadSemaphore--;
                if(loadSemaphore == 0) {
                    Ext.MessageBox.hide();
                }
            }, this), this.record.data.name, "exe" );
            this.node.getClientDistributionDownloadLink( Ext.bind(function(result, exception) {
                if(Ung.Util.handleException(exception)) return;

                this.downloadGenericConfigurationFileEl.dom.innerHTML = '<a href="' + result + '" target="_blank">' +
                    this.i18n._('Click here to download this client\'s configuration zip file for other OSs (apple/linux/etc). ') + '</a>';
                this.downloadUntangleConfigurationFileEl.dom.innerHTML = '<a href="' + result + '" target="_blank">' +
                    this.i18n._('Click here to download this client\'s configuration file for remote Untangle OpenVPN clients.') + '</a>';

                loadSemaphore--;
                if(loadSemaphore == 0) {
                    Ext.MessageBox.hide();
                }

            }, this), this.record.data.name, "zip" );
        }
    });

    Ext.define('Ung.OpenVPN', {
        extend:'Ung.NodeWin',
        groupsStore: null,
        panelStatus: null,
        panelClient: null,
        gridRemoteServers: null,
        panelServer: null,
        gridConnectionEventLog: null,
        isNodeRunning: null,
        initComponent: function(container, position) {
            // Register the VTypes, need i18n to be initialized for the text
            if(Ext.form.VTypes["openvpnClientNameVal"]==null) {
                Ext.form.VTypes["openvpnClientNameVal"] = /^[A-Za-z0-9]([-_.0-9A-Za-z]*[0-9A-Za-z])?$/;
                Ext.form.VTypes["openvpnClientName"] = function(v) {
                    return Ext.form.VTypes["openvpnClientNameVal"].test(v);
                };
                Ext.form.VTypes["openvpnClientNameMask"] = /[-_.0-9A-Za-z]*/;
                Ext.form.VTypes["openvpnClientNameText"] = this.i18n._( "A client name should only contains numbers, letters, dashes and periods.  Spaces are not allowed." );
            }
            if(Ext.form.VTypes["openvpnSiteNameVal"]==null) {
                Ext.form.VTypes["openvpnSiteNameVal"] = /^[A-Za-z0-9]([-_.0-9A-Za-z]*[0-9A-Za-z])?$/;
                Ext.form.VTypes["openvpnSiteName"] = function(v) {
                    return Ext.form.VTypes["openvpnSiteNameVal"].test(v);
                };
                Ext.form.VTypes["openvpnSiteNameMask"] = /[-_.0-9A-Za-z]*/;
                Ext.form.VTypes["openvpnSiteNameText"] = this.i18n._( "A client name should only contains numbers, letters, dashes and periods.  Spaces are not allowed." );
            }

            this.isNodeRunning = this.getRpcNode().getRunState() === "RUNNING";
            this.buildStatus();
            this.buildServer();
            this.buildClient();
            this.buildConnectionEventLog();

            this.buildTabPanel( [ this.panelStatus, this.panelServer, this.panelClient, this.gridConnectionEventLog ] );
            this.callParent(arguments);
        },
        getGroupsStore: function(force) {
            if (this.groupsStore == null ) {
                this.groupsStore = Ext.create('Ext.data.Store', {
                    fields: ['groupId', 'name', 'javaClass'],
                    data: this.getSettings().groups.list
                });
                force = false;
            }

            if(force) {
                this.groupsStore.loadData( this.getSettings().groups.list );
            }

            return this.groupsStore;
        },
        getDefaultGroupId: function(forceReload) {
            if (forceReload || this.defaultGroupId === undefined) {
                var defaultGroup = this.getGroupsStore().getCount()>0 ? this.getGroupsStore().getAt(0).data:null;
                this.defaultGroupId = defaultGroup == null ? null : defaultGroup.groupId;
            }
            return this.defaultGroupId;
        },
        // active connections/sessions grip
        buildClientStatusGrid: function() {
            this.gridClientStatus = Ext.create('Ung.EditorGrid', {
                flex: 1,
                style: "padding-bottom: 10px;",
                name: "gridClientStatus",
                settingsCmp: this,
                hasAdd: false,
                hasEdit: false,
                hasDelete: false,
                columnsDefaultSortable: true,
                title: this.i18n._("Connected Remote Clients"),
                qtip: this.i18n._("The Connected Remote Clients list shows connected clients."),
                paginated: false,
                bbar: Ext.create('Ext.toolbar.Toolbar', {
                    items: [{
                        xtype: 'button',
                        text: i18n._('Refresh'),
                        name: "Refresh",
                        tooltip: i18n._('Refresh'),
                        iconCls: 'icon-refresh',
                        handler: Ext.bind(function() {
                            this.gridClientStatus.reload();
                        }, this)
                    }]
                }),
                recordJavaClass: "com.untangle.node.openvpn.OpenVpnStatusEvent",
                dataFn: this.getRpcNode().getActiveClients,
                fields: [{
                    name: "address",
                    sortType: Ung.SortTypes.asIp
                }, {
                    name: "clientName"
                }, {
                    name: "poolAddress",
                    sortType: Ung.SortTypes.asIp
                }, {
                    name: "start",
                    sortType: Ung.SortTypes.asTimestamp
                }, {
                    name: "bytesRxTotal"
                }, {
                    name: "bytesTxTotal"
                }, {
                    name: "id"
                }],
                columns: [{
                    header: this.i18n._("Address"),
                    dataIndex:'address',
                    width: 150
                }, {
                    header: this.i18n._("Client"),
                    dataIndex:'clientName',
                    width: 200
                }, {
                    header: this.i18n._("Pool Address"),
                    dataIndex:'poolAddress',
                    width: 150
                }, {
                    header: this.i18n._("Start Time"),
                    dataIndex:'start',
                    width: 180,
                    renderer: function(value) { return i18n.timestampFormat(value); }
                }, {
                    header: this.i18n._("Rx Data"),
                    dataIndex:'bytesRxTotal',
                    width: 180,
                    renderer: function(value) { return (Math.round(value/100000)/10) + " Mb"; }
                }, {
                    header: this.i18n._("Tx Data"),
                    dataIndex:'bytesTxTotal',
                    width: 180,
                    renderer: function(value) { return (Math.round(value/100000)/10) + " Mb"; }
                }]
            });
        },

        // active connections/sessions grip
        buildServerStatusGrid: function() {
            this.gridServerStatus = Ext.create('Ung.EditorGrid', {
                flex: 1,
                name: "gridServerStatus",
                settingsCmp: this,
                hasAdd: false,
                hasEdit: false,
                hasDelete: false,
                columnsDefaultSortable: true,
                title: this.i18n._("Remote Server Status"),
                qtip: this.i18n._("The Remote Server Status list shows the current status of the configured remote servers."),
                paginated: false,
                bbar: Ext.create('Ext.toolbar.Toolbar', {
                    items: [{
                        xtype: 'button',
                        text: i18n._('Refresh'),
                        name: "Refresh",
                        tooltip: i18n._('Refresh'),
                        iconCls: 'icon-refresh',
                        handler: Ext.bind(function() {
                            this.gridServerStatus.reload();
                        }, this)
                    }]
                }),
                dataFn: this.getRpcNode().getRemoteServersStatus,
                fields: [{
                    name: "name"
                }, {
                    name: "connected"
                }, {
                    name: "bytesRead"
                }, {
                    name: "bytesWritten"
                }, {
                    name: "id"
                }],
                columns: [{
                    header: this.i18n._("Name"),
                    dataIndex:'name',
                    width: 150
                }, {
                    header: this.i18n._("Connected"),
                    dataIndex:'connected',
                    width: 75
                }, {
                    header: this.i18n._("Rx Data"),
                    dataIndex:'bytesRead',
                    width: 180,
                    renderer: function(value) { return (Math.round(value/100000)/10) + " Mb"; }
                }, {
                    header: this.i18n._("Tx Data"),
                    dataIndex:'bytesWritten',
                    width: 180,
                    renderer: function(value) { return (Math.round(value/100000)/10) + " Mb"; }
                }]
            });
        },
        
        // Status panel
        buildStatus: function() {
            var statusLabel = "";
            this.buildClientStatusGrid();
            this.buildServerStatusGrid();

            var statusDescription = "";
            if (this.isNodeRunning) {
                statusDescription = "<font color=\"green\">" + this.i18n._("OpenVPN is currently running.") + "</font>";
            } else {
                statusDescription = "<font color=\"red\">" + this.i18n._("OpenVPN is not currently running.") + "</font>";
            }
            
            this.panelStatus = Ext.create('Ext.panel.Panel', {
                name: 'Status',
                helpSource: 'openvpn_status',
                title: this.i18n._("Status"),
                parentId: this.getId(),
                layout: { type: 'vbox', align: 'stretch' },
                cls: 'ung-panel',
                isDirty: function() {
                    return false;
                },
                items: [{
                    xtype: 'fieldset',
                    cls: 'description',
                    title: this.i18n._('Status'),
                    flex: 0,
                    html: "<i>" + statusDescription + "</i>",
                }, this.gridClientStatus, this.gridServerStatus]
            });
        },

        // Connections Event Log
        buildConnectionEventLog: function() {
            this.gridConnectionEventLog = Ext.create('Ung.GridEventLog', {
                settingsCmp: this,
                helpSource: 'openvpn_event_log',
                eventQueriesFn: this.getRpcNode().getStatusEventsQueries,
                name: "Event Log",
                title: i18n._('Event Log'),
                fields: [{
                    name: 'start_time',
                    sortType: Ung.SortTypes.asTimestamp
                }, {
                    name: 'end_time',
                    sortType: Ung.SortTypes.asTimestamp
                }, {
                    name: 'client_name'
                }, {
                    name: 'remote_address',
                    sortType: Ung.SortTypes.asIp
                }, {
                    name: 'tx_bytes',
                    convert: function(val) {
                        return parseFloat(val) / 1024;
                    }
                }, {
                    name: 'rx_bytes',
                    convert: function(val) {
                        return parseFloat(val) / 1024;
                    }
                }],
                columns: [{
                    header: this.i18n._("Start Time"),
                    width: Ung.Util.timestampFieldWidth,
                    sortable: true,
                    dataIndex: 'start_time',
                    renderer: Ext.bind(function(value) {
                        return i18n.timestampFormat(value);
                    }, this ),
                    filter: null
                }, {
                    header: this.i18n._("End Time"),
                    width: Ung.Util.timestampFieldWidth,
                    sortable: true,
                    dataIndex: 'end_time',
                    renderer: Ext.bind(function(value) {
                        return i18n.timestampFormat(value);
                    }, this ),
                    filter: null
                }, {
                    header: this.i18n._("Client Name"),
                    sortable: true,
                    dataIndex: 'client_name'
                }, {
                    header: this.i18n._("Client Address"),
                    sortable: true,
                    dataIndex: 'remote_address'
                }, {
                    header: this.i18n._("KB Sent"),
                    width: 80,
                    sortable: true,
                    dataIndex: 'tx_bytes',
                    renderer: Ext.bind(function( value ) {
                        return Math.round(( value + 0.0 ) * 10 ) / 10;
                    }, this ),
                    filter: {
                        type: 'numeric'
                    }
                }, {
                    header: this.i18n._("KB Received"),
                    width: 80,
                    sortable: true,
                    dataIndex: 'rx_bytes',
                    renderer: Ext.bind(function( value ) {
                        return Math.round(( value + 0.0 ) * 10 ) / 10;
                    }, this ),
                    filter: {
                        type: 'numeric'
                    }
                }]
            });
        },
        getGroupsColumn: function() {
            return {
                header: this.i18n._("Group"),
                width: 160,
                dataIndex: 'groupId',
                renderer: Ext.bind(function(value, metadata, record,rowIndex,colIndex,store,view) {
                    var group = this.getGroupsStore().findRecord("groupId",value);
                    if (group != null)
                        return group.get("name");
                    return "";
                }, this ),
                editor: Ext.create('Ext.form.ComboBox', {
                    store: this.getGroupsStore(),
                    displayField: 'name',
                    valueField: 'groupId',
                    editable: false,
                    queryMode: 'local'
                })
            };
        },
        buildGridServers: function() {
            this.gridRemoteServers = Ext.create('Ung.EditorGrid', {
                hasAdd: false,
                settingsCmp: this,
                name: 'Remote Servers',
                sortable: true,
                paginated: false,
                flex: 1,
                style: "margin-bottom:10px;",
                emptyRow: {
                    "enabled": true,
                    "name": ""
                },
                title: this.i18n._("Remote Servers"),
                recordJavaClass: "com.untangle.node.openvpn.OpenVpnRemoteServer",
                dataProperty: "remoteServers",
                fields: [{
                    name: 'enabled'
                }, {
                    name: 'name'
                }, {
                    name: 'originalName',
                    mapping: 'name'
                }],
                columns: [{
                        xtype:'checkcolumn',
                        header: this.i18n._("Enabled"),
                        dataIndex: 'enabled',
                        width: 80,
                        resizable: false
                    }, {
                        header: this.i18n._("Server Name"),
                        width: 130,
                        dataIndex: 'name',
                        flex:1,
                        editor: {
                            xtype:'textfield',
                            emptyText: this.i18n._("[enter server name]"),
                            allowBlank: false,
                            maskRe: /[A-Za-z0-9-]/,
                            vtype: 'openvpnClientName'
                        }
                    }],
                columnsDefaultSortable: true,
                // the row input lines used by the row editor window
                rowEditorInputLines: [{
                    xtype: 'checkbox',
                    name: "Enabled",
                    dataIndex: "enabled",
                    fieldLabel: this.i18n._("Enabled")
                }, {
                        xtype: 'container',
                        layout: 'column',
                        margin: '0 0 5 0',
                        items: [{
                            xtype: "textfield",
                            name: "Server name",
                            dataIndex: "name",
                            fieldLabel: this.i18n._("Server name"),
                            emptyText: this.i18n._("[enter server name]"),
                            allowBlank: false,
                            maskRe: /[A-Za-z0-9-]/,
                            vtype: 'openvpnClientName',
                            width: 300
                        },{
                            xtype: 'label',
                            html: this.i18n._("only alphanumerics allowed") + " [A-Za-z0-9-]",
                            cls: 'boxlabel'
                        }]
                }]
            });
        },
        buildClient: function() {
            this.buildGridServers();

            this.submitForm = Ext.create('Ext.form.Panel', {
                border: false,
                xtype: 'fieldset',
                cls: 'description',
                flex: 0,
                items: [{
                        xtype: 'container',
                        html: "<i>" + this.i18n._("Configure a new Remote Server connection") + "</i>",
                        cls: 'description',
                        border: false
                    }, {
                        xtype: 'fieldset',
                        buttonAlign: 'left',
                        labelWidth: 150,
                        labelAlign: 'right',
                        items: [{
                            xtype: 'filefield',
                            name: 'uploadConfigFileName',
                            fieldLabel: this.i18n._('Configuration File'),
                            allowBlank: false,
                            width: 300,
                            size: 50
                        }, {
                            xtype: 'button',
                            id: "submitUpload",
                            text: i18n._('Submit'),
                            name: "Submit",
                            handler: Ext.bind(function() {
                                var filename = this.submitForm.down('textfield[name="uploadConfigFileName"]').getValue();
                                if ( filename == null || filename.length == 0 ) {
                                    Ext.MessageBox.alert(this.i18n._( "Select File" ), this.i18n._( "Please choose a file to upload." ));
                                    return;
                                }

                                this.submitForm.submit({
                                    url: "/openvpn/uploadConfig",
                                    success: Ext.bind(function( form, action, handler ) {
                                        Ext.MessageBox.alert(this.i18n._( "Success" ), this.i18n._( "The configuration has been imported." ));
                                        this.getSettings(function () {
                                            this.gridRemoteServers.reload(this.getSettings().remoteServers);
                                        });
                                    }, this),
                                    failure: Ext.bind(function( form, action ) {
                                        Ext.MessageBox.alert(this.i18n._( "Failure" ), this.i18n._( action.result.code ));
                                    }, this)
                                });
                                
                                
                            }, this)
                        }]
                    }]
                });

            this.panelClient = Ext.create('Ext.panel.Panel', {
                name: 'Client',
                parentId: this.getId(),
                title: this.i18n._('Client'),
                helpSource: 'openvpn_client',
                layout: { type: 'vbox', align: 'stretch' },
                cls: 'ung-panel',
                items: [{
                    xtype: 'fieldset',
                    cls: 'description',
                    flex: 0,
                    title: this.i18n._('Client'),
                    items: [{
                        xtype: 'container',
                        html: "<i>" + this.i18n._("These settings configure how OpenVPN will connect to remote servers as a client.") + "</i>",
                        cls: 'description',
                        border: false
                    }, {
                        xtype: 'container',
                        html: "<i>" + this.i18n._("Remote Servers is a list remote OpenVPN servers that OpenVPN should connect to as a client.") + "</i>",
                        cls: 'description',
                        border: false
                    }]
                }, this.gridRemoteServers, this.submitForm]
            });
        },
        
        buildGridClients: function() {
            this.gridRemoteClients = Ext.create('Ung.EditorGrid', {
                initComponent: function() {
                    this.distributeWindow = Ext.create('Ung.Node.OpenVPN.DownloadClient', {
                        i18n: this.settingsCmp.i18n,
                        node: this.settingsCmp.getRpcNode()
                    });
                    this.subCmps.push(this.distributeWindow);
                    Ung.EditorGrid.prototype.initComponent.call(this);
                },
                settingsCmp: this,
                name: 'Remote Clients',
                sortable: true,
                paginated: false,
                emptyRow: {
                    "enabled": true,
                    "name": "",
                    "groupId": this.getDefaultGroupId(),
                    "address": null,
                    "export":false,
                    "exportNetwork":null
                },
                title: this.i18n._("Remote Clients"),
                recordJavaClass: "com.untangle.node.openvpn.OpenVpnRemoteClient",
                dataProperty: "remoteClients",
                fields: [{
                    name: 'enabled'
                }, {
                    name: 'name'
                }, {
                    name: 'originalName',
                    mapping: 'name'
                }, {
                    name: 'groupId'
                }, {
                    name: 'export'
                }, {
                    name: 'exportNetwork'
                }],
                columns: [{
                        xtype:'checkcolumn',
                        header: this.i18n._("Enabled"),
                        dataIndex: 'enabled',
                        width: 80,
                        resizable: false
                    }, {
                        header: this.i18n._("Client Name"),
                        width: 130,
                        dataIndex: 'name',
                        flex:1,
                        editor: {
                            xtype:'textfield',
                            emptyText: this.i18n._("[enter client name]"),
                            allowBlank: false,
                            maskRe: /[A-Za-z0-9-]/,
                            vtype: 'openvpnClientName'
                        }
                    },
                    this.getGroupsColumn(),
                    {
                        width: 120,
                        header: this.i18n._("Download"),
                        dataIndex: null,
                        renderer: Ext.bind(function(value, metadata, record,rowIndex,colIndex,store,view) {
                            var out= '';
                            if(record.data.internalId>=0) {
                                var id = Ext.id();
                                Ext.defer(function () {
                                    var button = Ext.widget('button', {
                                        renderTo: id, 
                                        text: this.i18n._("Download Client"), 
                                        disabled: !this.isNodeRunning,
                                        width: 110,
                                        handler: Ext.bind(function () { 
                                            this.gridRemoteClients.distributeWindow.populate(record);
                                        }, this)
                                    });
                                    this.subCmps.push(button);
                                }, 50, this);
                                out=  Ext.String.format('<div id="{0}"></div>', id);
                            }
                            return out;
                        }, this)
                    }],
                columnsDefaultSortable: true
            });
            this.gridRemoteClients.setRowEditor( Ext.create('Ung.RowEditorWindow',{
                inputLines: [{
                    xtype: 'checkbox',
                    name: "Enabled",
                    dataIndex: "enabled",
                    fieldLabel: this.i18n._("Enabled")
                }, {
                        xtype: 'container',
                        layout: 'column',
                        margin: '0 0 5 0',
                        items: [{
                            xtype: "textfield",
                            name: "Client Name",
                            dataIndex: "name",
                            fieldLabel: this.i18n._("Client Name"),
                            emptyText: this.i18n._("[enter client name]"),
                            allowBlank: false,
                            maskRe: /[A-Za-z0-9-]/,
                            vtype: 'openvpnClientName',
                            width: 300
                        },{
                            xtype: 'label',
                            html: this.i18n._("only alphanumerics allowed") + " [A-Za-z0-9-]",
                            cls: 'boxlabel'
                        }]
                }, {
                    xtype: "combo",
                    name: "Group",
                    dataIndex: "groupId",
                    fieldLabel: this.i18n._("Group"),
                    store: this.getGroupsStore(),
                    displayField: 'name',
                    valueField: 'groupId',
                    editable: false,
                    queryMode: 'local',
                    width: 300
                }, {
                    xtype: "combo",
                    name: "Type",
                    dataIndex: "export",
                    fieldLabel: this.i18n._("Type"),
                    displayField: 'name',
                    editable: false,
                    store: [[false,i18n._('Individual Client')], [true,i18n._('Network')]],
                    queryMode: 'local',
                    width: 300,
                    listeners: {
                        "change": {
                            fn: function(elem, newValue) {
                                this.gridRemoteClients.rowEditor.syncComponents();
                            },
                            scope: this
                        }
                    }
                }, {
                    xtype: "textfield",
                    name: "Remote Networks",
                    dataIndex: "exportNetwork",
                    fieldLabel: this.i18n._("Remote Networks"),
                    allowBlank: false,
                    vtype: 'cidrBlockList',
                    width: 300
                }],
                syncComponents: function () {
                    var type = this.down('combo[dataIndex="export"]');
                    var exportNetwork  = this.down('textfield[dataIndex="exportNetwork"]');
                    if (type.value) {
                        exportNetwork.enable();
                    } else {
                        exportNetwork.disable();
                    }
                }
            }));
        },
        buildRemoteClients: function() {
        },
        generateGridExports: function() {
            // live is a check column
            var exports=[];
            exports=this.getSettings().exports.list;

            var gridExports = Ext.create('Ung.EditorGrid', {
                settingsCmp: this,
                name: 'Exports',
                // the total records is set from the base settings
                sortable: true,
                paginated: false,
                emptyRow: {
                    "enabled": true,
                    "name": "",
                    "network": "192.168.1.0/24"
                },
                title: this.i18n._("Exported Networks"),
                recordJavaClass: "com.untangle.node.openvpn.OpenVpnExport",
                data: exports,
                // the list of fields
                fields: [{
                    name: 'enabled'
                }, {
                    name: 'name'
                }, {
                    name: 'network',
                    sortType: Ung.SortTypes.asIp
                }],
                autoExpandMin: 250,
                // the list of columns for the column model
                columns: [{
                        xtype:'checkcolumn',
                        header: this.i18n._("Enabled"),
                        dataIndex: 'enabled',
                        width: 80,
                        resizable: false
                    }, {
                        header: this.i18n._("Export Name"),
                        width: 150,
                        dataIndex: 'name',
                        editor: {
                            xtype:'textfield',
                            emptyText: this.i18n._("[enter export name]"),
                            allowBlank: false
                        }
                    }, {
                        header: this.i18n._("Network"),
                        width: 150,
                        dataIndex: 'network',
                        flex:1,
                        editor: {
                            xtype:'textfield',
                            allowBlank: false,
                            vtype: 'cidrBlock'
                        }
                    }],
                columnsDefaultSortable: true,
                // the row input lines used by the row editor window
                rowEditorInputLines: [{
                    xtype: 'checkbox',
                    name: "Enabled",
                    dataIndex: "enabled",
                    fieldLabel: this.i18n._("Enabled")
                }, {
                    xtype: "textfield",
                    name: "Export name",
                    dataIndex: "name",
                    fieldLabel: this.i18n._("Export Name"),
                    emptyText: this.i18n._("[enter export name]"),
                    allowBlank: false,
                    width: 300
                }, {
                    xtype: "textfield",
                    name: "Export network",
                    dataIndex: "network",
                    fieldLabel: this.i18n._("Network"),
                    allowBlank: false,
                    vtype: 'cidrBlock',
                    width: 300
                }]
            });
            return gridExports;
        },
        generateGridGroups: function() {
            var gridGroups = Ext.create('Ung.EditorGrid', {
                settingsCmp: this,
                name: 'Groups',
                // the total records is set from the base settings
                sortable: true,
                paginated: false,
                addAtTop: false,
                emptyRow: {
                    "groupId": -1,
                    "name": "",
                    "pushDns": false,
                    "fullTunnel": false
                },
                title: this.i18n._("Groups"),
                recordJavaClass: "com.untangle.node.openvpn.OpenVpnGroup",
                dataProperty: 'groups',
                // the list of fields
                fields: [{
                    name: 'groupId'
                }, {
                    name: 'name'
                }, {
                    name: 'fullTunnel'
                }, {
                    name: 'pushDns'
                }, {
                    name: 'pushDnsSelf'
                }, {
                    name: 'pushDns1'
                }, {
                    name: 'pushDns2'
                }, {
                    name: 'pushDnsDomain'
                }],
                // the list of columns for the column model
                columns: [{
                    header: this.i18n._("Group Name"),
                    width: 160,
                    dataIndex: 'name',
                    flex:1,
                    editor: {
                        xtype:'textfield',
                        emptyText: this.i18n._("[enter group name]"),
                        allowBlank:false
                    }
                },{
                    id: "fullTunnel",
                    header: this.i18n._("Full Tunnel"),
                    dataIndex: 'fullTunnel',
                    width: 90,
                    resizable: false
                },{
                    id: "pushDns",
                    header: this.i18n._("Push DNS"),
                    dataIndex: 'pushDns',
                    width: 90,
                    resizable: false
                }],
                // sortField: 'name',
                columnsDefaultSortable: true,
                // the row input lines used by the row editor window
                rowEditorInputLines: [{
                    xtype: "textfield",
                    name: "Group Name",
                    dataIndex: "name",
                    fieldLabel: this.i18n._("Group Name"),
                    emptyText: this.i18n._("[enter group name]"),
                    allowBlank: false,
                    width: 300
                }, {
                    xtype: 'checkbox',
                    name: "Full Tunnel",
                    dataIndex: "fullTunnel",
                    fieldLabel: this.i18n._("Full Tunnel")
                }, {
                    xtype: 'checkbox',
                    name: "Push DNS",
                    dataIndex: "pushDns",
                    fieldLabel: this.i18n._("Push DNS"),
                    listeners: {
                        "change": {
                            fn: Ext.bind(function(elem, newValue) {
                                if ( newValue ) {
                                    Ext.getCmp('pushDnsSettings').show();
                                } else {
                                    Ext.getCmp('pushDnsSettings').hide();

                                }
                            }, this)
                        },
                        "render": {
                            fn: Ext.bind(function(field) {
                                if ( field.value ) {
                                    Ext.getCmp('pushDnsSettings').show();
                                } else {
                                    Ext.getCmp('pushDnsSettings').hide();
                                }
                            }, this),
                            scope: this
                        }
                    }
                }, {
                    xtype: 'fieldset',
                    id: "pushDnsSettings",
                    title: this.i18n._('Push DNS Configuration'),
                    items: [{
                        xtype: "combo",
                        name: "Push DNS Server",
                        dataIndex: "pushDnsSelf",
                        fieldLabel: this.i18n._("Push DNS Server"),
                        displayField: 'name',
                        editable: false,
                        store: [[true,i18n._('OpenVPN Server')], [false,i18n._('Custom')]],
                        queryMode: 'local',
                        width: 300,
                        listeners: {
                            "change": {
                                fn: Ext.bind(function(elem, newValue) {
                                    if (newValue) {
                                        Ext.getCmp('pushDns1').disable();
                                        Ext.getCmp('pushDns2').disable();
                                    } else {
                                        Ext.getCmp('pushDns1').enable();
                                        Ext.getCmp('pushDns2').enable();
                                    }
                                }, this)
                            },
                            "render": {
                                fn: Ext.bind(function(field) {
                                    if (field.value) {
                                        Ext.getCmp('pushDns1').disable();
                                        Ext.getCmp('pushDns2').disable();
                                    } else {
                                        Ext.getCmp('pushDns1').enable();
                                        Ext.getCmp('pushDns2').enable();
                                    }
                                }, this)
                            }
                        }
                    }, {
                        xtype: "textfield",
                        id: "pushDns1",
                        dataIndex: "pushDns1",
                        fieldLabel: this.i18n._("Push DNS Custom 1"),
                        allowBlank: true,
                        vtype: 'ipAddress',
                        width: 300
                    }, {
                        xtype: "textfield",
                        id: "pushDns2",
                        dataIndex: "pushDns2",
                        fieldLabel: this.i18n._("Push DNS Custom 2"),
                        allowBlank: true,
                        vtype: 'ipAddress',
                        width: 300
                    }, {
                        xtype: "textfield",
                        id: "pushDnsDomain",
                        dataIndex: "pushDnsDomain",
                        fieldLabel: this.i18n._("Push DNS Domain"),
                        allowBlank: true,
                        width: 300
                    }]
                }]
            });
            return gridGroups;
        },

        buildServer: function() {
            this.panelRemoteClients = null;
            this.gridRemoteClients = null;
            this.panelExports = null;
            this.gridExports = null;
            this.panelGroups = null;
            this.gridGroups = null;

            this.buildGridClients();
            this.gridExports = this.generateGridExports();
            this.gridGroups = this.generateGridGroups();
            
            this.tabPanel = Ext.create('Ext.tab.Panel',{
                id: "server_tab_panel",
                activeTab: 0,
                deferredRender: false,
                parentId: this.getId(),
                flex: 1,
                style: "margin: 0px 20px 5px 20px",
                items: [ this.gridRemoteClients, this.gridGroups, this.gridExports ]
            });

            this.reRenderFn = Ext.bind( function (newValue) {
                this.getSettings().serverEnabled = newValue;

                Ext.getCmp('server_tab_panel').disable();
                Ext.getCmp('openvpn_options_client_to_client').disable();
                Ext.getCmp('openvpn_options_port').disable();
                Ext.getCmp('openvpn_options_protocol').disable();
                Ext.getCmp('openvpn_options_cipher').disable();
                Ext.getCmp('openvpn_options_addressSpace').disable();
                if (newValue) {
                    Ext.getCmp('server_tab_panel').enable();
                    Ext.getCmp('openvpn_options_client_to_client').enable();
                    Ext.getCmp('openvpn_options_port').enable();
                    Ext.getCmp('openvpn_options_protocol').enable();
                    Ext.getCmp('openvpn_options_cipher').enable();
                    Ext.getCmp('openvpn_options_addressSpace').enable();
                }
            }, this);

            var publicUrl;
            try {
                publicUrl = rpc.systemManager.getPublicUrl();
            } catch (e) {
                Ung.Util.rpcExHandler(e);
            }
            
            this.panelServer = Ext.create('Ext.panel.Panel', {
                name: 'Server',
                helpSource: 'openvpn_server',
                title: this.i18n._("Server"),
                parentId: this.getId(),
                layout: { type: 'vbox', align: 'stretch' },
                cls: 'ung-panel',
                items: [{
                    xtype: 'fieldset',
                    cls: 'description',
                    title: this.i18n._('Server'),
                    flex: 0,
                    items: [{
                        xtype: 'container',
                        html: "<i>" + this.i18n._("These settings configure how OpenVPN will be a server for remote clients.") + "</i>",
                        cls: 'description',
                        style: "margin-bottom:10px;",
                        border: false
                    }, {
                        xtype: 'textfield',
                        labelWidth: 160,
                        labelAlign:'left',
                        width:300,
                        fieldLabel: this.i18n._('Site Name'),
                        name: 'Site Name',
                        value: this.getSettings().siteName,
                        vtype: 'openvpnSiteName',
                        id: 'openvpn_options_siteName',
                        allowBlank: false,
                        blankText: this.i18n._("You must enter a site name."),
                        listeners: {
                            "change": {
                                fn: Ext.bind(function(elem, newValue) {
                                    this.getSettings().siteName = newValue;
                                }, this)
                            }
                        }
                    }, {
                        xtype: 'displayfield',
                        labelWidth: 160,
                        labelAlign:'left',
                        width:300,
                        fieldLabel: this.i18n._('Site URL'),
                        name: 'Site URL',
                        value: publicUrl.split(":")[0]+":"+this.getSettings().port,
                        id: 'openvpn_options_siteUrl'
                    }, {
                        xtype: 'checkbox',
                        labelWidth: 160,
                        name: "Server Enabled",
                        fieldLabel: this.i18n._("Server Enabled"),
                        checked: this.getSettings().serverEnabled,
                        listeners: {
                            "change": {
                                fn: Ext.bind(function(elem, newValue) {
                                    this.reRenderFn( newValue );
                                }, this)
                            },
                            "render": {
                                fn: Ext.bind(function(field) {
                                    this.reRenderFn( field.value );
                                }, this),
                                scope: this
                            }
                        }
                    }, {
                        xtype: 'checkbox',
                        hidden: true, /* HIDDEN */
                        labelWidth: 160,
                        name: 'Client To Client',
                        fieldLabel: this.i18n._('Client To Client Allowed'),
                        checked: this.getSettings().clientToClient,
                        id: 'openvpn_options_client_to_client',
                        listeners: {
                            "change": {
                                fn: Ext.bind(function(elem, newValue) {
                                    this.getSettings().clientToClient = newValue;
                                }, this)
                            }
                        }
                    }, {
                        xtype: 'textfield',
                        hidden: true, /* HIDDEN */
                        labelWidth: 160,
                        labelAlign:'left',
                        width: 300,
                        fieldLabel: this.i18n._('Port'),
                        name: 'Port',
                        value: this.getSettings().port,
                        id: 'openvpn_options_port',
                        allowBlank: false,
                        vtype: "port",
                        listeners: {
                            "change": {
                                fn: Ext.bind(function(elem, newValue) {
                                    this.getSettings().port = newValue;
                                }, this)
                            }
                        }
                    }, {
                        xtype: 'textfield',
                        hidden: true, /* HIDDEN */
                        labelWidth: 160,
                        labelAlign:'left',
                        width: 300,
                        fieldLabel: this.i18n._('Protocol'),
                        name: 'Protocol',
                        value: this.getSettings().protocol,
                        id: 'openvpn_options_protocol',
                        allowBlank: false,
                        vtype: "port",
                        listeners: {
                            "change": {
                                fn: Ext.bind(function(elem, newValue) {
                                    this.getSettings().protocol = newValue;
                                }, this)
                            }
                        }
                    }, {
                        xtype: 'textfield',
                        hidden: true, /* HIDDEN */
                        labelWidth: 160,
                        labelAlign:'left',
                        width:300,
                        fieldLabel: this.i18n._('Cipher'),
                        name: 'Cipher',
                        value: this.getSettings().cipher,
                        id: 'openvpn_options_cipher',
                        allowBlank: false,
                        vtype: "port",
                        listeners: {
                            "change": {
                                fn: Ext.bind(function(elem, newValue) {
                                    this.getSettings().cipher = newValue;
                                }, this)
                            }
                        }
                    }, {
                        xtype: 'textfield',
                        labelWidth: 160,
                        labelAlign:'left',
                        width: 300,
                        fieldLabel: this.i18n._('Address Space'),
                        name: 'Address Space',
                        value: this.getSettings().addressSpace,
                        id: 'openvpn_options_addressSpace',
                        allowBlank: false,
                        vtype: "cidrBlock",
                        listeners: {
                            "change": {
                                fn: Ext.bind(function(elem, newValue) {
                                    this.getSettings().addressSpace = newValue;
                                }, this)
                            }
                        }
                    }, {
                        xtype: 'checkbox',
                        hidden: true, /* HIDDEN */
                        labelWidth: 160,
                        name: "NAT OpenVPN Traffic",
                        fieldLabel: this.i18n._("NAT All OpenVPN Traffic"),
                        checked: this.getSettings().natOpenVpnTraffic,
                        listeners: {
                            "change": {
                                fn: Ext.bind(function(elem, newValue) {
                                    this.getSettings().natOpenVpnTraffic = newValue;
                                }, this)
                            }
                        }
                    }]
                }, this.tabPanel]
            });
        },

        // validation function
        validate: function() {
            return  this.validateServer() && this.validateGroups() && this.validateVpnClients();
        },

        //validate OpenVPN Server settings
        validateServer: function() {
            //validate site name
            var siteCmp = Ext.getCmp("openvpn_options_siteName");
            if(!siteCmp.validate()) {
                Ext.MessageBox.alert(this.i18n._("Failed"), this.i18n._("You must enter a site name."),
                    Ext.bind(function () {
                        this.tabs.setActiveTab(this.panelServer);
                        siteCmp.focus(true);
                    }, this)
                );
                return false;
            }
            return true;
        },

        validateGroups: function() {
            var i;
            var groups=this.gridGroups.getPageList(false, true);

            // verify that there is at least one group
            if(groups.length <= 0 ) {
                Ext.MessageBox.alert(this.i18n._('Failed'), this.i18n._("You must create at least one group."),
                    Ext.bind(function () {
                        this.tabs.setActiveTab(this.panelGroups);
                    }, this)
                );
                return false;
            }

            // removed groups should not be referenced
            var removedGroups = this.gridGroups.getDeletedList();
            if(removedGroups.length>0) {
                var clientList = this.gridRemoteClients.getPageList();
                for( i=0; i<removedGroups.length;i++) {
                    for(var j=0; j<clientList.length;j++) {
                        if (removedGroups[i].groupId == clientList[j].groupId) {
                            Ext.MessageBox.alert(this.i18n._('Failed'),
                                Ext.String.format(this.i18n._("The group: \"{0}\" cannot be deleted because it is being used by the client: {1} in the Client To Site List."), removedGroups[i].name, clientList[j].name),
                                Ext.bind(function () {
                                    this.tabs.setActiveTab(this.panelGroups);
                                }, this)
                            );
                            return false;
                        }
                    }
                }
            }

            // Group names must all be unique
            var groupNames = {};

            for( i=0;i<groups.length;i++) {
                var group = groups[i];
                var groupName = group.name.toLowerCase();

                if ( groupNames[groupName] != null ) {
                    Ext.MessageBox.alert(this.i18n._('Failed'), Ext.String.format(this.i18n._("The group name: \"{0}\" in row: {1} already exists."), group.name, i+1),
                        Ext.bind(function () {
                            this.tabs.setActiveTab(this.panelGroups);
                        }, this));
                    return false;
                }

                // Save the group name
                groupNames[groupName] = true;
            }

            return true;
        },

        validateVpnClients: function() {
            var clientList=this.gridRemoteClients.getPageList(false, true);
            var clientNames = {};

            for(var i=0;i<clientList.length;i++) {
                var client = clientList[i];
                var clientName = client.name.toLowerCase();

                if(client.internalId>=0 && client.name!=client.originalName) {
                    Ext.MessageBox.alert(i18n._("Failed"), this.i18n._("Changing name is not allowed. Create a new user."),
                        Ext.bind(function () {
                            this.tabs.setActiveTab(this.panelClients);
                        }, this)
                    );
                    return false;
                }
                
                if ( clientNames[clientName] != null ) {
                    Ext.MessageBox.alert(this.i18n._('Failed'),
                                         Ext.String.format(this.i18n._("The client name: \"{0}\" in row: {1} already exists."), clientName, i),
                                         Ext.bind(function () {
                                             this.tabs.setActiveTab(this.panelRemoteClients);
                                         }, this)
                                        );
                    return false;
                }
                clientNames[clientName] = true;
            }
            return true;
        },

        validateExports: function(exportList) {
            return true;
        },

        save: function(isApply) {
            this.getSettings().groups.list = this.gridGroups.getPageList();
            this.getSettings().exports.list = this.gridExports.getPageList();
            this.getSettings().remoteClients.list = this.gridRemoteClients.getPageList();
            this.getSettings().remoteServers.list = this.gridRemoteServers.getPageList();

            this.getRpcNode().setSettings(Ext.bind(function(result, exception) {
                if(Ung.Util.handleException(exception)) return;
                this.afterSave(isApply);
            }, this), this.getSettings());
        },

        afterSave: function(isApply) {
            Ext.MessageBox.hide();
            if (!isApply) {
                this.closeWindow();
            } else {
                Ext.MessageBox.wait(i18n._("Reloading..."), i18n._("Please wait"));
                this.getSettings(function() {
                    // Assume the config state hasn't changed
                    this.getGroupsStore(true);
                    this.getDefaultGroupId(true);
                    this.gridRemoteClients.emptyRow.groupId = this.getDefaultGroupId();

                    this.gridExports.reload({data: this.getSettings().exports.list });

                    Ext.getCmp( "openvpn_options_siteName" ).setValue( this.getSettings().siteName );
                    Ext.getCmp( "openvpn_options_port" ).setValue( this.getSettings().port );
                    Ext.getCmp( "openvpn_options_protocol" ).setValue( this.getSettings().protocol );
                    Ext.getCmp( "openvpn_options_cipher" ).setValue( this.getSettings().cipher );
                    Ext.getCmp( "openvpn_options_addressSpace" ).setValue( this.getSettings().addressSpace );

                    this.clearDirty();
                    Ext.MessageBox.hide();
                });
            }
        }
    });
}
//@ sourceURL=openvpn-settings.js

Ext.namespace('Ung');
Ext.namespace('Ung.SetupWizard');

// the main json rpc object
var rpc = {};

if(typeof console === "undefined") {
    //Prevent console.log triggering errors on browsers without console support
    var console = {
        log: function() {},
        error: function() {},
        debug: function() {}
    };
}

Ext.apply(Ext.form.field.VTypes, {
    ipAddress: function( val, field ) {
        return val.match( this.ipAddressRegex );
    },
    ipAddressText: 'Please enter a valid IP Address',
    ipAddressRegex: /\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\b/,

    passwordConfirmCheck: function(val,field) {
        var pass_original = Ext.getCmp(field.comparePasswordField);
        return val == pass_original.getValue();
    },
    passwordConfirmCheckText: 'Passwords do not match'
});

Ext.define('Ung.SetupWizard.SettingsSaver', {
    password: null,

    constructor: function( panel, handler ) {
        this.panel = panel;
        this.handler = handler;
        rpc.setup = new JSONRpcClient("/setup/JSON-RPC").SetupContext;
    },

    savePassword: function() {
        // New Password
        this.password = this.panel.down('textfield[name="password"]').getValue();
        this.adminEmail = this.panel.down('textfield[name="adminEmail"]').getValue();
        rpc.setup.setAdminPassword( Ext.bind(this.saveTimeZone, this), this.password, this.adminEmail );
    },

    saveTimeZone: function( result, exception ) {
        if(Ung.Util.handleException(exception, "Unable to save the admin password")) return;
        var timezone = this.panel.down('textfield[name="timezone"]').getValue();
        rpc.setup.setTimeZone( Ext.bind(this.authenticate,this ), timezone );
    },

    authenticate: function( result, exception ) {
        if(Ung.Util.handleException(exception, "Unable to save Time Zone settings")) return;
        Ext.MessageBox.wait( i18n._( "Authenticating" ), i18n._( "Please Wait" ));
        Ext.Ajax.request({
            params: {
                username: 'admin',
                password: this.password
            },
            // If it uses the default type then this will not work
            // because the authentication handler does not like utf8
            headers: {
                'Content-Type': "application/x-www-form-urlencoded"
            },
            url: '/auth/login?url=/webui&realm=Administrator',
            callback: Ext.bind(this.getManagers,this )
        });
    },
    getManagers: function( options, success, response ) {
        if ( success ) {
            // It is very wrong to do this all synchronously
            rpc.jsonrpc = new JSONRpcClient( "/webui/JSON-RPC" );
            rpc.jsonrpc.UvmContext.getSetupStartupInfo(Ext.bind(function(result, exception){
                if(Ung.Util.handleException(exception)) return;
                Ext.applyIf(rpc, result);
                rpc.keepAlive = function() {
                    rpc.jsonrpc.UvmContext.getFullVersion(Ext.bind(function(result, exception){
                        if(Ung.Util.handleException(exception)) return;
                        Ext.defer(rpc.keepAlive,300000);
                    }, this));
                };
                rpc.keepAlive();

                if (Ext.MessageBox.rendered) {
                    Ext.MessageBox.hide();
                }
                this.handler();
            }, this));
        } else {
            Ext.MessageBox.alert( i18n._( "Unable to save password." ));
        }
    }
});

// Setup Wizard - Welcome
Ext.define('Ung.SetupWizard.Welcome', {
    constructor: function( config ) {
        var panel = Ext.create('Ext.form.Panel', {
            border: false,
            items: [{
                xtype: 'container',
                html: '<h2 class="wizard-title">' + Ext.String.format(i18n._( "Thanks for choosing {0}!" ), rpc.oemName) + '</h2>'
            }, {
                xtype: 'container',
                cls: 'noborder',
                html: Ext.String.format(i18n._( 'This wizard will guide you through the initial setup and configuration of the {0} Server.'), rpc.oemName) +
                    '<br/><br/>'+
                    Ext.String.format(i18n._('Click {0}Next{1} to get started.'),'<b>','</b>')
            }]
        });
        this.card = {
            title: i18n._( "Welcome" ),
            panel: panel
        };
    }
});

// Setup Wizard - Step 1 (Password and Timezone)
Ext.define('Ung.SetupWizard.ServerSettings', {
    constructor: function( config ) {
        this.panel = Ext.create('Ext.form.Panel', {
            border: false,
            defaults: {
                cls: 'noborder'
            },
            items: [{
                xtype: 'container',
                html: '<h2 class="wizard-title">'+i18n._( "Configure the Server" )+'</h2>'
            }, {
                xtype: 'fieldset',
                border: false,
                defaultType: 'textfield',
                defaults: {
                    msgTarget: 'side',
                    validationEvent: 'blur',
                    labelWidth: 150
                },
                items: [{
                    xtype: 'container',
                    html: '<b>'+i18n._( 'Choose a password for the admin account.')+'</b>',
                    border: false
                }, {
                    xtype: 'textfield',
                    inputType: 'text',
                    fieldLabel: i18n._('Login'),
                    name: 'login',
                    value: 'admin',
                    readOnly: true,
                    fieldCls: 'noborder',
                    cls: 'small-top-margin'
                }, {
                    xtype: 'textfield',
                    inputType: 'password',
                    fieldLabel: i18n._('Password'),
                    name: 'password',
                    id: 'settings_password',
                    allowBlank: false,
                    minLength: 3,
                    minLengthText: i18n.sprintf(i18n._("The password is shorter than the minimum %d characters."), 3)
                }, {
                    xtype: 'textfield',
                    inputType: 'password',
                    fieldLabel: i18n._('Confirm Password'),
                    name: 'confirmPassword',
                    allowBlank: false,
                    comparePasswordField: 'settings_password',
                    vtype: 'passwordConfirmCheck'
                }]
            }, {
                xtype: 'fieldset',
                
                border: false,
                items: [{
                    xtype: 'container',
                    html: '<b>'+i18n._( 'Configure administrator email.')+'</b>',
                    border: false
                }, {
                    xtype: 'textfield',
                    inputType: 'text',
                    fieldLabel: i18n._('Admin Email'),
                    name: 'adminEmail',
                    allowBlank: true,
                    width: 300,
                    vtype: 'email',
                    cls: 'small-top-margin'

                }]
            }, {
                xtype: 'fieldset',
                
                border: false,
                items: [{
                    xtype: 'container',
                    html: '<b>'+i18n._( 'Select a timezone.')+'</b>',
                    border: false
                }, {
                    xtype: 'combo',
                    name: 'timezone',
                    editable: false,
                    store: Ung.SetupWizard.TimeZoneStore,
                    width: 350,
                    hideLabel: true,
                    queryMode: 'local',
                    value: Ung.SetupWizard.CurrentValues.timezone,
                    cls: 'small-top-margin'
                }]
            }]
        });

        this.card = {
            title: i18n._( "Settings" ),
            panel: this.panel,
            onLoad: Ext.bind(function( complete ) {
                var emailField=this.panel.query('textfield[name="adminEmail"]');
                if ( emailField[0].getValue() == null || emailField[0].getValue() == "" )
                    emailField[0].setValue( rpc.adminEmail );
                complete();
            }, this ),
            onNext: Ext.bind(this.saveSettings, this ),
            onValidate: Ext.bind(this.validateSettings,this)
        };
    },
    validateSettings: function() {
        var rv = Ung.Util.validateItems(this.panel.items.items);
        return rv;
    },
    saveSettings: function( handler ) {
        Ext.MessageBox.wait( i18n._( "Saving Settings" ), i18n._( "Please Wait" ));
        var saver = Ext.create('Ung.SetupWizard.SettingsSaver', this.panel, handler );
        saver.savePassword();
    }
});

// Setup Wizard - Step 2 (Remap Interfaces)
Ext.define('Ung.SetupWizard.Interfaces', {
    constructor: function() {
        this.interfaceStore = Ext.create('Ext.data.ArrayStore', {
            fields:[{name: "interfaceId"}, { name: "name" }, { name: "physicalDev" },{ name: "deviceName" }, { name: "macAddress" }, { name: "connected" }, { name: "duplex" }, { name: "vendor" }, { name: "mbit" }],
            data: []
        });
        this.deviceStore = Ext.create('Ext.data.ArrayStore', {
            fields:[{ name: "physicalDev" }],
            data: []
        });
        this.enableAutoRefresh = true;
        this.networkSettings = null;
        this.interfaceGrid = Ext.create('Ext.grid.Panel', {
            store: this.interfaceStore,
            loadMask: true,
            stripeRows: true,
            cls: 'small-top-margin',
            enableColumnResize: true,
            enableColumnHide: false,
            enableColumnMove: false,
            selModel: Ext.create('Ext.selection.RowModel', {singleSelect: true}),
            plugins: [
                Ext.create('Ext.grid.plugin.CellEditing', {
                    clicksToEdit: 1
                })
            ],
            viewConfig:{
               forceFit: true,
               disableSelection: false,
               plugins:{
                    ptype: 'gridviewdragdrop',
                    dragText: i18n._('Drag and drop to reorganize')
                },
                listeners: {
                    "drop": {
                        fn:  Ext.bind(this.onDrop,this )
                    }
                }
            },
            height: 200,
            width: 585,
            columns: [{
                header: i18n._( "Name" ),
                dataIndex: 'name',
                sortable: false,
                width: 80,
                renderer: function( value ) {
                    return i18n._( value );
                }
            }, {
                xtype: 'templatecolumn',
                menuDisabled: true,
                resizable: false,
                width: 30,
                tpl: '<img src="'+Ext.BLANK_IMAGE_URL+'" class="icon-drag"/>'
            }, {
                header: i18n._( "Device" ),
                tooltip: i18n._( "Click on a Device to open a combo and choose the desired Device from a list. When anoter Device is selected the 2 Devices are swithced." ),
                tooltipType: "title",
                dataIndex: 'deviceName',
                sortable: false,
                tdCls: 'ua-pointer',
                editor:{
                    xtype: 'combo',
                    store: this.deviceStore,
                    valueField: 'physicalDev',
                    displayField: 'physicalDev',
                    queryMode: 'local',
                    editable: false,
                    width:90,
                    listeners: {
                        "change": {
                            fn: Ext.bind(function(elem, newValue, oldValue) {
                                var sourceRecord = null;
                                var targetRecord = null;
                                this.interfaceStore.each( function( currentRow ) {
                                    if(oldValue==currentRow.get( "physicalDev" )) {
                                        sourceRecord=currentRow;
                                    } else if(newValue==currentRow.get( "physicalDev" )) {
                                        targetRecord=currentRow;
                                    }
                                    if(sourceRecord!=null && targetRecord!=null) {
                                        return false;
                                    }
                                    return true;
                                });
                                if(sourceRecord==null || targetRecord==null || sourceRecord==targetRecord) {
                                    console.log(oldValue, newValue, sourceRecord, targetRecord);
                                    return false;
                                }
                                var soruceData = Ext.decode(Ext.encode(sourceRecord.data));
                                var targetData = Ext.decode(Ext.encode(targetRecord.data));
                                soruceData.deviceName=oldValue;
                                targetData.deviceName=newValue;
                                sourceRecord.set({
                                    "physicalDev": targetData.physicalDev,
                                    "macAddress": targetData.macAddress,
                                    "duplex": targetData.duplex,
                                    "vendor": targetData.vendor,
                                    "mbit": targetData.mbit,
                                    "connected": targetData.connected
                                });
                                targetRecord.set({
                                    "deviceName": soruceData.deviceName,
                                    "physicalDev": soruceData.physicalDev,
                                    "macAddress": soruceData.macAddress,
                                    "duplex": soruceData.duplex,
                                    "vendor": soruceData.vendor,
                                    "mbit": soruceData.mbit,
                                    "connected": soruceData.connected
                                });
                                return true;
                            }, this)
                        }
                    }
                }
            },{
                dataIndex: 'connected',
                sortable: false,
                resizable: false,
                tdCls: 'ua-draggable',
                width: 30,
                renderer: Ext.bind(function(value, metadata, record, rowIndex, colIndex, store, view) {
                    var divClass = ( value == "CONNECTED" )?"ua-cell-enabled":"ua-cell-disabled";
                    return "<div class='" + divClass + "'><div>";
                }, this)
            },{
                header: i18n._( "Status" ),
                dataIndex: 'connected',
                sortable: false,
                width: 223,
                flex: 1,
                tdCls: 'ua-draggable',
                renderer: Ext.bind(function(value, metadata, record, rowIndex, colIndex, store, view) {
                    var connected = record.get("connected");
                    var mbit = record.get("mbit");
                    var duplex = record.get("duplex");
                    var vendor = record.get("vendor");

                    var connectedStr= ( connected == "CONNECTED" )?i18n._("connected") : ( connected == "DISCONNECTED" )?i18n._("disconnected") : i18n._("unknown");
                    var duplexStr = (duplex=="FULL_DUPLEX")?i18n._("full-duplex") : (duplex=="HALF_DUPLEX") ? i18n._("half-duplex") : i18n._("unknown");
                    return connectedStr + " " + mbit + " " + duplexStr +" " + vendor;
                }, this)
            }, {
                header: i18n._( "MAC Address" ),
                dataIndex: 'macAddress',
                sortable: false,
                width: 120,
                renderer: function(value, metadata, record, rowIndex, colIndex, store, view) {
                    var text = "";
                    if ( value && value.length > 0 ) {
                        // Build the link for the mac address
                        text = '<a target="_blank" href="http://standards.ieee.org/cgi-bin/ouisearch?' +
                        value.substring( 0, 8 ).replace( /:/g, "" ) + '">' + value + '</a>';
                    }
                    return text;
                }
            }]
        });

        var panelTitle = i18n._('Identify Network Cards');
        var panelText = "<font color=\"red\"><b>" + i18n._( "Important:") + "</b></font>";
        panelText += i18n._( " This step identifies the external, internal, and other network cards. ");
        panelText += "<br/>";
        panelText += "<br/>";

        panelText += "<b>" + i18n._("Step 1: ") + "</b>";
        panelText += i18n._( "Plug an active cable into one network card to determine which network card it is.");
        panelText += "<br/>";

        panelText += "<b>" + i18n._("Step 2: ") + "</b>";
        panelText += "<b>" + i18n._( "Drag and drop") + "</b>" + i18n._(" the network card to map it to the desired interface.");
        panelText += "<br/>";

        panelText += "<b>" + i18n._("Step 3: ") + "</b>";
        panelText += i18n._( "Repeat steps 1 and 2 for each network card and then click <i>Next</i>.");
        panelText += "<br/>";

        var panel = Ext.create('Ext.panel.Panel', {
            defaults: { cls: 'noborder' },
            border: false,
            layout: {
                type: 'vbox',
                align:'left'
            },
            items: [{
                xtype: 'container',
                html: '<h2 class=" wizard-title">'+panelTitle+'<h2>',
                border: false
            }, {
                xtype: 'container',
                html: panelText,
                border: false
            }, { xtype: "panel",
                layout:'fit',
                border: false,
                items: this.interfaceGrid
            }]
        });

        this.card = {
            title: i18n._( "Network Cards" ),
            panel: panel,
            onLoad: Ext.bind(function( complete ) {
                this.refreshInterfaces();
                this.enableAutoRefresh = true;
                Ext.defer(this.autoRefreshInterfaces,3000,this);
                complete();
            }, this ),
            onNext: Ext.bind(this.saveInterfaceList, this ),
            onPrevious: Ext.bind(function(handler) {
                this.enableAutoRefresh = false;
                handler();
            }, this )
        };
    },

    onDrop: function(node,data,overModel,dropPosition,dropFunction, options) {
        var i = 0;
        var interfaceList=this.networkSettings.interfaces.list;
        this.interfaceStore.each( Ext.bind(function( currentRow ) {
            var intf=interfaceList[i];
            currentRow.set({
                "interfaceId": intf.interfaceId,
                "name": intf.name
            });
            i++;
        }, this));
        return true;
    },

    saveInterfaceList: function( handler ) {
        // disable auto refresh
        this.enableAutoRefresh = false;

        Ext.MessageBox.wait( i18n._( "Saving Settings" ), i18n._( "Please Wait" ));
        this.interfaceStore.sync();
        var interfacesMap = {};
        this.interfaceStore.each( function( currentRow ) {
            interfacesMap[currentRow.get( "interfaceId" )] = currentRow.get( "physicalDev" );
        });
        var interfaceList=this.networkSettings.interfaces.list;
        for(var i=0; i<interfaceList.length; i++) {
            var intf=interfaceList[i];
            if(!intf.isVlanInterface) {
                intf["physicalDev"]=interfacesMap[intf["interfaceId"]];
            }
        }
        rpc.networkManager.setNetworkSettings( Ext.bind(function( result, exception ) {
            if(Ung.Util.handleException(exception)) return;
            Ext.MessageBox.hide();
            handler();
        }, this ), this.networkSettings);
    },

    errorHandler: function( result, exception, foo, handler ) {
        if(Ung.Util.handleException(exception, "Unable to remap the interfaces.")) return;
        Ext.MessageBox.hide();
        handler();
    },
    createRecordsMap : function(recList, property) {
        var map = {};
        for(var i=0; i<recList.length; i++) {
            map[recList[i][property]] = recList[i];
        }
        return map;
    },
    autoRefreshInterfaces: function() {
        if ( ! this.enableAutoRefresh ) {
            return;
        }
        rpc.networkManager.getNetworkSettings( Ext.bind(function( result, exception ) {
            if ( ! this.enableAutoRefresh ) {
                return; // if auto refresh is now disabled, just return
            }
            if(Ung.Util.handleException(exception, "Unable to refresh the interfaces.")) return;
            var interfaceList = [];
            var allInterfaces = result.interfaces.list;
            for(var i=0; i<allInterfaces.length; i++) {
                if(!allInterfaces[i].isVlanInterface) {
                    interfaceList.push(allInterfaces[i]);
                }
            }

            if ( interfaceList.length != this.interfaceStore.getCount() ) {
                Ext.MessageBox.alert( i18n._( "New interfaces" ), i18n._ ( "There are new interfaces, please restart the wizard." ), "" );
                return;
            }
            var deviceStatus=rpc.networkManager.getDeviceStatus( Ext.bind( function( result, exception ) {
                if(Ung.Util.handleException(exception)) return;
                if ( result == null ) return;

                var deviceStatusMap = this.createRecordsMap( ( result == null ? [] : result.list ), "deviceName");
                //update device connected status
                this.interfaceStore.each( function(currentRow) {
                    var deviceStatus= deviceStatusMap[currentRow.get("physicalDev")];
                    if(deviceStatus!=null) {
                        currentRow.set("connected", deviceStatus.connected);
                    }
                });
                if ( this.enableAutoRefresh ) {
                    Ext.defer(this.autoRefreshInterfaces,3000,this);
                }
            }, this));
        }, this));
    },

    refreshInterfaces: function() {
        Ext.MessageBox.wait( i18n._( "Refreshing Network Interfaces" ), i18n._( "Please Wait" ));
        rpc.networkManager.getNetworkSettings( Ext.bind(function( result, exception ) {
            if(Ung.Util.handleException(exception, "Unable to refresh the interfaces.")) return;

            this.networkSettings=result;
            var interfaceList = [];
            var allInterfaces = result.interfaces.list;
            for(var i=0; i<allInterfaces.length; i++) {
                if(!allInterfaces[i].isVlanInterface) {
                    interfaceList.push(allInterfaces[i]);
                }
            }

            var deviceStatus=rpc.networkManager.getDeviceStatus(Ext.bind(function( result, exception ) {
                if(Ung.Util.handleException(exception)) return;

                Ext.MessageBox.hide();
                var deviceStatusMap = this.createRecordsMap( ( result == null ? [] : result.list ), "deviceName");
                for(var i=0; i<interfaceList.length; i++) {
                    var intf=interfaceList[i];
                    var deviceStatus = deviceStatusMap[intf.physicalDev];
                    Ext.applyIf(intf, deviceStatus);
                }
                this.interfaceStore.loadData( interfaceList );
                this.deviceStore.loadData( interfaceList );
                if ( interfaceList.length < 2) {
                    Ext.MessageBox.alert( i18n._( "Missing interfaces" ), i18n._ ( "Untangle requires two or more network cards. Please reinstall with at least two network cards." ), "" );
                }
            }, this));
        }, this));
    }

});

// Setup Wizard - Step 3 (Configure WAN)
Ext.define('Ung.SetupWizard.Internet', {
    constructor: function( config ) {
        this.v4ConfigTypes = [];
        this.v4ConfigTypes.push( [ "AUTO",   i18n._( "Auto (DHCP)" ) ] );
        this.v4ConfigTypes.push( [ "STATIC", i18n._( "Static" ) ] );
        this.v4ConfigTypes.push( [ "PPPOE",  i18n._( "PPPoE" ) ] );

        this.cards = [];

        // DHCP Panel
        this.cards.push( this.dhcpPanel = Ext.create('Ext.form.Panel', {
            saveData: Ext.bind(this.saveDHCP,this ),
            border: false,
            cls: 'network-card-form-margin',
            items: [{
                xtype: 'fieldset',
                border: false,
                title: i18n._( "DHCP Status" ),
                defaultType: 'textfield',
                defaults: {
                    readOnly: true,
                    fieldCls: 'noborder',
                    labelWidth: Ung.SetupWizard.LabelWidth
                },
                items: [{
                    name: "ip",
                    fieldLabel: i18n._( "Current IP Address" )
                }, {
                    name: "netmask",
                    fieldLabel: i18n._( "Current Netmask" )
                }, {
                    name: "gateway",
                    fieldLabel: i18n._( "Current Gateway" )
                }, {
                    name: "dns1",
                    fieldLabel: i18n._( "Current Primary DNS" )
                }, {
                    name: "dns2",
                    fieldLabel: i18n._( "Current Secondary DNS" )
                }]
            }],
            buttonAlign: 'center',
            buttons: [{
                xtype: 'button',
                text: i18n._( 'Refresh' ),
                handler: Ext.bind(this.refresh,this )
            }, {
                xtype: 'button',
                text: i18n._( 'Test Connectivity' ),
                handler: Ext.bind( this.testConnectivity, this, [null] )
            }]
        }));

        // Static Panel
        this.cards.push( this.staticPanel = Ext.create('Ext.form.Panel', {
            saveData: Ext.bind(this.saveStatic,this ),
            border: false,
            cls: 'network-card-form-margin',
            items: [{
                xtype: 'fieldset',
                border: false,
                title: i18n._( "Static Settings" ),
                defaultType: 'textfield',
                defaults: {
                    labelWidth: Ung.SetupWizard.LabelWidth,
                    disabled: false,
                    msgTarget: 'side',
                    validationEvent: 'blur',
                    maskRe: /(\d+|\.)/,
                    vtype: 'ipAddress'
                },
                items: [{
                    name: "ip",
                    fieldLabel: i18n._( "IP Address" ),
                    allowBlank: false
                }, {
                    name: "prefix",
                    fieldLabel: i18n._( "Netmask" ),
                    xtype: 'combo',
                    store: Ung.SetupWizard.getV4NetmaskList( false ),
                    queryMode: 'local',
                    triggerAction: 'all',
                    value: 24,
                    editable: false,
                    allowBlank: false
                }, {
                    name: "gateway",
                    fieldLabel: i18n._( "Gateway" ),
                    allowBlank: false
                }, {
                    name: "dns1",
                    fieldLabel: i18n._( "Primary DNS" ),
                    allowBlank: false
                }, {
                    name: "dns2",
                    fieldLabel: i18n._( "Secondary DNS (optional)"),
                    allowBlank: true
                }]
            }],
            buttonAlign: 'center',
            buttons: [{
                xtype: 'button',
                text: i18n._( 'Test Connectivity' ),
                cls: 'test-connectivity-2',
                handler: Ext.bind( this.testConnectivity, this, [null] )
            }]
        }));

        // PPPoE Panel
        this.cards.push( this.pppoePanel = Ext.create('Ext.form.Panel', {
            saveData: Ext.bind(this.savePPPoE,this ),
            border: false,
            cls: 'network-card-form-margin',
            items: [{
                xtype: 'fieldset',
                border: false,
                title: i18n._( "PPPoE Settings" ),
                defaultType: 'textfield',
                defaults: {
                    labelWidth: Ung.SetupWizard.LabelWidth
                },
                items: [{
                    fieldLabel: i18n._( "Username" ),
                    name: "username",
                    disabled: false,
                    readOnly: false
                }, {
                    name: "password",
                    inputType: 'password',
                    fieldLabel: i18n._( "Password" ),
                    disabled: false,
                    readOnly: false
                }]
            }, {
                xtype: 'fieldset',
                border: false,
                title: i18n._( "PPPoE Status" ),
                defaultType: 'textfield',
                defaults: {
                    labelWidth: Ung.SetupWizard.LabelWidth,
                    readOnly: true,
                    fieldCls: 'noborder'
                },
                items: [{
                    fieldLabel: i18n._( "IP Address" ),
                    name: "ip"
                }, {
                    fieldLabel: i18n._( "Netmask" ),
                    name: "prefix"
                }, {
                    name: "gateway",
                    fieldLabel: i18n._( "Gateway" )
                }, {
                    name: "dns1",
                    fieldLabel: i18n._( "Primary DNS" )
                }, {
                    name: "dns2",
                    fieldLabel: i18n._( "Secondary DNS" )
                }]
            }],
            buttonAlign: 'center',
            buttons: [{
                xtype: 'button',
                text: i18n._( 'Refresh' ),
                handler: Ext.bind(this.refresh,this )
            }, {
                xtype: 'button',
                text: i18n._( 'Test Connectivity' ),
                handler: Ext.bind( this.testConnectivity, this, [null] )
            }]
        }));

        this.cardPanel = Ext.create('Ext.panel.Panel', {
            cls: 'untangle-form-panel',
            border: false,
            layout: 'card',
            items: this.cards,
            activePanel: 0,
            defaults: {
                border: false
            }
        });

        var configureText = i18n._("Configure the Internet Connection");
        var configure = Ext.create('Ext.form.Panel', {
            cls: "untangle-form-panel",
            border: false,
            labelWidth: Ung.SetupWizard.LabelWidth,
            items: [{
                xtype: 'container',
                html: '<h2 class="wizard-title">'+configureText+'<h2>',
                border: false
            }, {
                xtype: 'combo',
                fieldLabel: i18n._('Configuration Type'),
                name: 'v4ConfigType',
                editable: false,
                store: this.v4ConfigTypes,
                labelWidth: Ung.SetupWizard.LabelWidth,
                queryMode: 'local',
                listeners: {
                    "select": {
                        fn: Ext.bind(this.onSelectConfig,this )
                    }
                },
                value: this.v4ConfigTypes[0][0]
            }]
        });

        var panel = Ext.create('Ext.panel.Panel', {
            cls: null,
            border: false,
            defaults: {
                cls: null
            },
            items: [ configure, this.cardPanel]
        });

        this.isInitialized = false;

        var cardTitle = i18n._( "Internet Connection" );
        this.card = {
            title: cardTitle,
            panel: panel,
            onLoad: Ext.bind(function( complete )
            {
                if ( !this.isInitialized ) {
                    this.cardPanel.layout.setActiveItem( 0 );
                }

                this.refreshNetworkDisplay();

                this.isInitialized = true;
                complete();
            },this),
            onNext: Ext.bind(this.testConnectivity,this ),
            onValidate: Ext.bind(this.validateInternetConnection,this)
        };
    },

    validateInternetConnection: function() {
        return Ung.Util.validateItems(this.cardPanel.layout.activeItem.items.items);
    },

    onSelectConfig: function( combo, record, index ) {
        this.cardPanel.layout.setActiveItem( record[0].index );
    },

    clearInterfaceSettings: function( wanSettings ) {
        // delete unused stuff
        delete wanSettings.v4StaticAddress;
        delete wanSettings.v4StaticPrefix;
        delete wanSettings.v4StaticGateway;
        delete wanSettings.v4StaticDns1;
        delete wanSettings.v4StaticDns2;
    },

    saveDHCP: function( handler, hideWindow ) {
        if ( hideWindow == null ) {
            hideWindow = true;
        }

        var wanSettings = this.getFirstWanSettings( Ung.SetupWizard.CurrentValues.networkSettings );
        this.clearInterfaceSettings( wanSettings );

        wanSettings.v4ConfigType = "AUTO";
        wanSettings.v4NatEgressTraffic = true;

        this.setFirstWanSettings( Ung.SetupWizard.CurrentValues.networkSettings, wanSettings );

        var complete = Ext.bind(this.complete, this, [ handler, hideWindow ], true );
        rpc.networkManager.setNetworkSettings( complete, Ung.SetupWizard.CurrentValues.networkSettings );
    },

    saveStatic: function( handler, hideWindow ) {
        if ( hideWindow == null ) {
            hideWindow = true;
        }

        var wanSettings = this.getFirstWanSettings( Ung.SetupWizard.CurrentValues.networkSettings );
        this.clearInterfaceSettings( wanSettings );

        wanSettings.v4ConfigType = "STATIC";
        wanSettings.v4NatEgressTraffic = true;
        wanSettings.v4StaticAddress = this.staticPanel.down('textfield[name="ip"]').getValue();
        wanSettings.v4StaticPrefix = this.staticPanel.down('textfield[name="prefix"]').getValue();
        wanSettings.v4StaticGateway = this.staticPanel.down('textfield[name="gateway"]').getValue();
        wanSettings.v4StaticDns1 = this.staticPanel.down('textfield[name="dns1"]').getValue();
        wanSettings.v4StaticDns2 = this.staticPanel.down('textfield[name="dns2"]').getValue();
        if ( wanSettings.v4StaticDns2.length <= 0 ) wanSettings.v4StaticDns2 = null; //ignore empty box

        this.setFirstWanSettings( Ung.SetupWizard.CurrentValues.networkSettings, wanSettings );

        var complete = Ext.bind(this.complete, this, [ handler, hideWindow ], true );
        rpc.networkManager.setNetworkSettings( complete, Ung.SetupWizard.CurrentValues.networkSettings );
    },

    savePPPoE: function( handler, hideWindow ) {
        if ( hideWindow == null ) {
            hideWindow = true;
        }

        var wanSettings = this.getFirstWanSettings( Ung.SetupWizard.CurrentValues.networkSettings );
        this.clearInterfaceSettings( wanSettings );

        wanSettings.v4ConfigType = "PPPOE";
        wanSettings.v4NatEgressTraffic = true;
        wanSettings.v4PPPoEUsername = this.pppoePanel.down('textfield[name="username"]').getValue();
        wanSettings.v4PPPoEPassword = this.pppoePanel.down('textfield[name="password"]').getValue();

        this.setFirstWanSettings( Ung.SetupWizard.CurrentValues.networkSettings, wanSettings );

        var complete = Ext.bind(this.complete, this, [ handler, hideWindow ], true );
        rpc.networkManager.setNetworkSettings( complete, Ung.SetupWizard.CurrentValues.networkSettings );
    },

    complete: function( result, exception, foo, handler, hideWindow ) {
        if(Ung.Util.handleException(exception)) return;

        if ( hideWindow || ( hideWindow == null ) ) {
            Ext.MessageBox.hide();
        }

        if (handler != null)
            handler();
    },

    // Refresh the current network settings (lease or whatever)
    refresh: function() {
        Ext.MessageBox.wait(i18n._("Refreshing..."), i18n._("Please Wait"));

        var handler = Ext.bind(function() {
            //redresh network data
            this.refreshNetworkDisplay();
            Ext.MessageBox.hide();
        }, this);

        this.saveData( handler, false );
    },

    testConnectivity: function( afterFn ) {
        if ( !this.validateInternetConnection()) {
            Ext.MessageBox.show({
                title: i18n._("Unable to Test Connectivity" ),
                msg: i18n._( "Please complete all of the required fields." ),
                width: 300,
                buttons: Ext.MessageBox.OK,
                icon: Ext.MessageBox.INFO
            });
            return;
        }

        Ext.MessageBox.wait(i18n._("Saving Settings..."), i18n._("Please Wait"));
        var handler = Ext.bind( this.execConnectivityTest, this, [afterFn] );

        this.saveData( handler, false );
    },

    saveData: function( handler, hideWindow ) {
        this.cardPanel.layout.activeItem.saveData( handler, hideWindow );
    },

    completeConnectivityTest: function( result, exception, foo, handler ) {
        if ( Ext.MessageBox.rendered) {
            Ext.MessageBox.hide();
        }

        if ( exception ) {
            Ext.MessageBox.show({
                title:i18n._( "Network Settings" ),
                msg: i18n._( "Unable to complete connectivity test, please try again." ),
                width:300,
                buttons:Ext.MessageBox.OK,
                icon:Ext.MessageBox.INFO
            });
            return;
        }

        var message = "";

         // If handler is null then this is just a manual connectivity test, so just show a pop-up
        if (handler == null) {
            if (( result.tcpWorking == false )  && ( result.dnsWorking == false )) {
                message = i18n._( "Warning! Internet and DNS tests failed." );
            } else if ( result.tcpWorking == false ) {
                message = i18n._( "Warning! DNS tests succeeded, but Internet tests failed." );
            } else if ( result.dnsWorking == false ) {
                message = i18n._( "Warning! Internet tests succeeded, but DNS tests failed." );
            } else {
                message = i18n._( "Success!" );
            }
            Ext.MessageBox.show({
                title:i18n._( "Internet Status" ),
                msg:message,
                width:300,
                buttons:Ext.MessageBox.OK,
                icon:Ext.MessageBox.INFO
            });
        }
        // If handler is not null, then "Next" has been pushed.
        // If connectivity is not valid, then display a warning, otherwise just continue
        else {
            if (( result.tcpWorking == false )  && ( result.dnsWorking == false )) {
                message = i18n._( "Warning! Internet tests and DNS tests failed." );
            } else if ( result.tcpWorking == false ) {
                message = i18n._( "Warning! DNS tests succeeded, but Internet tests failed." );
            } else if ( result.dnsWorking == false ) {
                message = i18n._( "Warning! Internet tests succeeded, but DNS tests failed." );
            } else {
                message = null;
            }

            // if the test passed, just continue
            if (message == null) {
                handler();
                return;
            }

            var warningText = message + "<br/><br/>" +i18n._("It is recommended to configure valid internet settings before continuing. Try again?");
            Ext.Msg.confirm(i18n._("Warning:"), warningText, Ext.bind(function(btn, text) {
                if (btn == 'yes') {
                    return;
                } else {
                    handler();
                    return;
                }
            }, this));
        }
    },

    execConnectivityTest: function( handler ) {
        Ext.MessageBox.wait(i18n._("Testing Connectivity..."), i18n._("Please Wait"));
        rpc.connectivityTester.getStatus( Ext.bind( this.completeConnectivityTest, this, [handler], true ) );
    },

    getFirstWanSettings: function( networkSettings ) {
        for ( var c = 0 ; c < networkSettings['interfaces']['list'].length ; c++ ) {
            if (networkSettings['interfaces']['list'][c]['configType'] == "DISABLED") {
                continue;
            }
            if ( networkSettings['interfaces']['list'][c]['isWan'] ) {
                return networkSettings['interfaces']['list'][c];
            }
        }
        return null;
    },

    setFirstWanSettings: function( networkSettings, firstWanSettings ) {
        for ( var c = 0 ; c < networkSettings['interfaces']['list'].length ; c++ ) {
            if ( firstWanSettings['interfaceId'] == networkSettings['interfaces']['list'][c]['interfaceId'] )
                networkSettings['interfaces']['list'][c] = firstWanSettings;
        }
    },

    // This does not reload the settings, it just updates what is
    // displayed inside of the User Interface.
    refreshNetworkDisplay: function() {
        var c = 0;
        var networkSettings, firstWanStatus;
        try {
            networkSettings = rpc.networkManager.getNetworkSettings();
        } catch (e) {
            Ung.Util.rpcExHandler(e);
        }
        Ung.SetupWizard.CurrentValues.networkSettings = networkSettings;

        var firstWan = this.getFirstWanSettings( networkSettings );
        try {
            firstWanStatus = rpc.networkManager.getInterfaceStatus( firstWan.interfaceId );
        } catch (e) {
            Ung.Util.rpcExHandler(e);
        }

        if ( networkSettings['interfaces'] == null && networkSettings['interfaces']['list'] == null ) {
            console.error("Missing interface information.");
            return;
        }

        var isConfigured = (firstWan.configType != null && firstWan.v4ConfigType != null);
        var card;
        if (isConfigured) {
            for ( c = 0; c < this.v4ConfigTypes.length ; c++ ) {
                if (this.v4ConfigTypes[c][0] == firstWan.v4ConfigType)
                    this.cardPanel.layout.setActiveItem( c );
            }
            this.updateValue( this.card.panel.down('combo[name="v4ConfigType"]'), firstWan.v4ConfigType);

            for ( c = 0; c < this.cards.length ; c++ ) {
                card = this.cards[c];
                this.updateValue( card.down('textfield[name="ip"]'), firstWanStatus.v4Address );
                this.updateValue( card.down('textfield[name="prefix"]'), firstWanStatus.v4PrefixLength );
                this.updateValue( card.down('textfield[name="netmask"]'), firstWanStatus.v4Netmask );
                this.updateValue( card.down('textfield[name="gateway"]'), firstWanStatus.v4Gateway );
                this.updateValue( card.down('textfield[name="dns1"]'), firstWanStatus.v4Dns1 );
                this.updateValue( card.down('textfield[name="dns2"]'), firstWanStatus.v4Dns2 );
            }
        } else { // not configured
            for ( c = 0; c < this.cards.length ; c++ ) {
                card = this.cards[c];
                this.updateValue( card.down('textfield[name="ip"]'), "" );
                this.updateValue( card.down('textfield[name="prefix"]'), "" );
                this.updateValue( card.down('textfield[name="gateway"]'), "" );
                this.updateValue( card.down('textfield[name="dns1"]'), "" );
                this.updateValue( card.down('textfield[name="dns2"]'), "" );
            }
        }
    },

    // Guard the field to shield strange values from the user.
    updateValue: function( field, value ) {
        if ( field == null ) {
            return;
        }
        if ( value == null || value == "0.0.0.0" ) {
            value = "";
        }

        field.setValue( value );
    }

});

// Setup Wizard - Step 4 (Configure Internal)
Ext.define('Ung.SetupWizard.InternalNetwork', {
    constructor: function( config ) {
        this.panel = Ext.create('Ext.form.Panel', {
            border: false,
            items: [{
                xtype: 'container',
                html: '<h2 class="wizard-title">'+i18n._( "Configure the Internal Network Interface" )+'</h2>'
            }, {
                xtype: 'fieldset',
                border: false,
                cls: 'noborder wizard-internal-network',
                items: [{
                    xtype: 'radio',
                    name: 'bridgeOrRouter',
                    inputValue: 'router',
                    boxLabel: i18n._( 'Router' ),
                    cls: 'large-option',
                    hideLabel: 'true',
                    listeners: {
                        change: {
                            fn: Ext.bind(function( checkbox, checked ) {
                                this.onSetRouter(checked);
                            },this )
                        }
                    }
                }, {
                    xtype: 'container',
                    style:'font-weight:normal',
                    html: "<div class='wizard-network-image-description'>" + i18n._('This is recommended if the external port is plugged into the internet connection. This enables NAT and DHCP.') + "</div>"
                }, {
                    name: 'network',
                    xtype: 'textfield',
                    cls: 'wizard-internal-network-address spacing-margin-1',
                    fieldLabel: i18n._('Internal Address'),
                    labelWidth: Ung.SetupWizard.LabelWidth2,
                    vText: i18n._('Please enter a valid Network  Address'),
                    vtype: 'ipAddress',
                    allowBlank: false,
                    msgTarget: 'side',
                    maskRe: /(\d+|\.)/,
                    disabled: true,
                    value: "192.168.1.1",
                    validationEvent: 'blur'
                }, {
                    name: "prefix",
                    cls: 'wizard-internal-network-address',
                    fieldLabel: i18n._( "Internal Netmask" ),
                    labelWidth: Ung.SetupWizard.LabelWidth2,
                    xtype: 'combo',
                    store: Ung.SetupWizard.getV4NetmaskList( false ),
                    queryMode: 'local',
                    triggerAction: 'all',
                    value: 24,
                    disabled: true,
                    editable: false
                }, {
                    xtype: 'checkbox',
                    hideLabel: true,
                    checked: true,
                    disabled: true,
                    name: 'enableDhcpServer',
                    cls: 'wizard-label-margin-5',
                    boxLabel: i18n._("Enable DHCP Server (default)")
                }, {
                    xtype: 'container',
                    componentCls: 'wizard-network-image',
                    html: '<img src="/skins/' + Ung.SetupWizard.CurrentValues.currentSkin + '/images/admin/wizard/router.png"/>'
                }]
            }, {
                xtype: 'fieldset',
                border: false,
                cls: 'noborder wizard-internal-network',
                items: [{
                    xtype: 'radio',
                    name: 'bridgeOrRouter',
                    inputValue: 'BRIDGED',
                    boxLabel: i18n._('Transparent Bridge'),
                    cls: 'large-option',
                    hideLabel: 'true',
                    checked: true
                }, {
                    xtype: 'container',
                    style:'font-weight:normal',
                    html: "<div class='wizard-network-image-description'>" + i18n._('This is recommended if the external port is plugged into a firewall/router. This bridges Internal and External and disables DHCP.') + "</div>"
                }, {
                    xtype: 'container',
                    cls: 'wizard-network-image',
                    html: '<img src="/skins/' + Ung.SetupWizard.CurrentValues.currentSkin + '/images/admin/wizard/bridge.png"/>'
                }]
            }]
        });

        this.card = {
            title: i18n._( "Internal Network" ),
            panel: this.panel,
            onLoad: Ext.bind(this.onLoadInternalSuggestion,this),
            onNext: Ext.bind(this.saveInternalNetwork,this ),
            onValidate: Ext.bind(this.validateInternalNetwork,this)
        };
    },
    onLoadInternalSuggestion: function( complete ) {
        var networkSettings = Ung.SetupWizard.CurrentValues.networkSettings;

        // find the internal interface and see if its currently set to static.
        // if so change the default to router
        if ( networkSettings != null && networkSettings['interfaces'] != null && networkSettings['interfaces']['list'] != null ) {
            var intfs = networkSettings['interfaces']['list'];
            for ( var c = 0 ;  c < intfs.length ; c++ ) {
                // find first non-WAN
                if ( intfs[c]['isWan'] != null && intfs[c]['isWan'] ) {
                    continue;
                }

                var bridgeOrRouterRadio=this.panel.query('radio[name="bridgeOrRouter"]');
                if ( intfs[c].configType == "BRIDGED" ) {
                    bridgeOrRouterRadio[0].setValue(false);
                    bridgeOrRouterRadio[1].setValue(true);
                }
                else { /* ADDRESSED or DISABLED */
                    bridgeOrRouterRadio[0].setValue(true);
                    bridgeOrRouterRadio[1].setValue(false);
                }

                if ( intfs[c]['v4StaticAddress'] != null && intfs[c]['v4StaticPrefix'] != null ) {
                    this.panel.down('textfield[name="network"]').setValue( intfs[c]['v4StaticAddress'] );
                    this.panel.down('combo[name="prefix"]').setValue( intfs[c]['v4StaticPrefix'] );
                }

                break;
            }
        }

        complete();
    },
    onSetRouter: function(isSet) {
        var ar = [this.panel.down('textfield[name="network"]'),this.panel.down('combo[name="prefix"]'),this.panel.down('checkbox[name="enableDhcpServer"]')];
        for(var i=0;i<ar.length;i++){
            ar[i].setDisabled(!isSet);
        }
        Ung.Util.invalidateItems(ar);
    },

    getFirstNonWanSettings: function( networkSettings ) {
        for ( var c = 0 ; c < networkSettings['interfaces']['list'].length ; c++ ) {
            if ( ! networkSettings['interfaces']['list'][c]['isWan'] )
                return networkSettings['interfaces']['list'][c];
        }
        return null;
    },

    setFirstNonWanSettings: function( networkSettings, intfSettings ) {
        for ( var c = 0 ; c < networkSettings['interfaces']['list'].length ; c++ ) {
            if ( intfSettings['interfaceId'] == networkSettings['interfaces']['list'][c]['interfaceId'] )
                networkSettings['interfaces']['list'][c] = intfSettings;
        }
    },

    validateInternalNetwork: function() {
        var rv = true;
        var nic = false;
        var bridgeOrRouterRadio = this.panel.query('radio[name="bridgeOrRouter"]');
        for(var i=0;i<bridgeOrRouterRadio.length;i++){
            if(bridgeOrRouterRadio[i].getValue()){
                nic = bridgeOrRouterRadio[i].inputValue;
                break;
            }
        }
        if ( nic == "router" ) {
            rv = Ung.Util.validateItems(this.panel.items.items);
        }
        return rv;
    },

    saveInternalNetwork: function( handler ) {
        var value = this.panel.down('radio[name="bridgeOrRouter"]').getGroupValue();

        if ( value == null ) {
            Ext.MessageBox.alert(i18n._( "Select a value" ), i18n._( "Please choose bridge or router." ));
            return;
        }

        Ext.MessageBox.wait( i18n._( "Saving Internal Network Settings" ), i18n._( "Please Wait" ));

        var delegate = Ext.bind(this.complete, this, [ handler ], true );
        var firstNonWan = this.getFirstNonWanSettings( Ung.SetupWizard.CurrentValues.networkSettings );

        if ( value == 'BRIDGED' ) {
            firstNonWan['configType'] = 'BRIDGED';
            this.setFirstNonWanSettings( Ung.SetupWizard.CurrentValues.networkSettings, firstNonWan );
            rpc.networkManager.setNetworkSettings( delegate, Ung.SetupWizard.CurrentValues.networkSettings );
        } else {
            var network = this.panel.down('textfield[name="network"]').getValue();
            var prefix = this.panel.down('combo[name="prefix"]').getValue();
            var enableDhcpServer = this.panel.down('checkbox[name="enableDhcpServer"]').getValue();
            firstNonWan['configType'] = 'ADDRESSED';
            firstNonWan['v4ConfigType'] = 'STATIC';
            firstNonWan['v4StaticAddress'] = network;
            firstNonWan['v4StaticPrefix'] = prefix;
            firstNonWan['dhcpEnabled'] = enableDhcpServer;
            delete firstNonWan.dhcpRangeStart; // new ones will be chosen
            delete firstNonWan.dhcpRangeEnd; // new ones will be chosen

            this.setFirstNonWanSettings( Ung.SetupWizard.CurrentValues.networkSettings, firstNonWan );
            rpc.networkManager.setNetworkSettings( delegate, Ung.SetupWizard.CurrentValues.networkSettings );
        }
    },

    complete: function( result, exception, foo, handler ) {
        if(Ung.Util.handleException(exception, "Unable to save Local Network Settings")) return;
        Ext.MessageBox.hide();
        handler();
    }
});

// Setup Wizard - Step 5 (Configure Upgrades)
Ext.define('Ung.SetupWizard.AutoUpgrades', {
    constructor: function( config ) {
        this.panel = Ext.create('Ext.form.Panel', {
            border: false,
            items: [{
                xtype: 'container',
                html: '<h2 class="wizard-title">'+i18n._( "Configure Automatic Upgrade Settings" )+'</h2>'
            }, {
                xtype: 'fieldset',
                border: false,
                cls: 'noborder  wizard-auto-upgrades',
                items: [{
                    xtype: 'radio',
                    name: 'autoUpgradesRadio',
                    inputValue: 'yes',
                    boxLabel: i18n._( 'Install Upgrades Automatically' ),
                    cls: 'large-option',
                    hideLabel: 'true',
                    checked: true
                }, {
                    xtype: 'container',
                    style:'font-weight:normal',
                    html: Ext.String.format( i18n._('Automatically install new versions of {0} software. '), rpc.oemName) + '<br/>' +
                         i18n._('This is the recommended for most sites.')
                }]
            }, {
                xtype: 'fieldset',
                border: false,
                cls: 'noborder wizard-auto-upgrades',
                items: [{
                    xtype: 'radio',
                    name: 'autoUpgradesRadio',
                    inputValue: 'no',
                    boxLabel: i18n._('Do Not Install Upgrades Automatically.'),
                    cls: 'large-option',
                    hideLabel: 'true'
                }, {
                    xtype: 'container',
                    style:'font-weight:normal',
                    html: Ext.String.format( i18n._('Do not automatically install new versions of {0} software.'), rpc.oemName) + '<br/>' +
                        i18n._('This is the recommended setting for large, complex, or sensitive sites.') + '<br/>' +
                        i18n._('Software Upgrades can be applied manually at any time when available.')
                }, {
                    xtype: 'container',
                    style:'font-weight:normal',
                    html: '<br/><br/>' + '<b>' + i18n._('Note:') + '</b>' + '<br/>' +
                        i18n._('Signatures for Virus Blocker, Spam Blocker, Web Filter, etc are still updated automatically.') + '<br/>' +
                        i18n._('If desired, a custom upgrade schedule can be configured after installation in the Upgrade Settings.') + '<br/>'
                }]
            }]
        });

        this.card = {
            title: i18n._( "Automatic Upgrades" ),
            panel: this.panel,
            onLoad: Ext.bind(this.onLoadAutoSuggestion,this),
            onNext: Ext.bind(this.saveAutoUpgrades,this ),
            onValidate:Ext.bind(this.validateAutoUpgrades,this)
        };
    },
    onLoadAutoSuggestion: function( complete ) {
        rpc.systemManager.getSettings(Ext.bind(function(result, exception) {
            if(Ung.Util.handleException(exception)) return;
            if (!result.autoUpgrade) {
                var autoUpgradesRadio=this.panel.query('radio[name="autoUpgradesRadio"]');
                autoUpgradesRadio[0].setValue(false);
                autoUpgradesRadio[1].setValue(true);
            }
            complete();
        }, this));
    },
    validateAutoUpgrades: function() {
        return true;
    },
    saveAutoUpgrades: function( handler ) {
        var value = this.panel.down('radio[name="autoUpgradesRadio"]').getGroupValue();
        if ( value == null ) {
            Ext.MessageBox.alert(i18n._( "Select a value" ), i18n._( "Please choose Yes or No." ));
            return;
        }
        Ext.MessageBox.wait( i18n._( "Saving Automatic Upgrades Settings" ), i18n._( "Please Wait" ));

        var delegate = Ext.bind(this.complete, this, [ handler ], true );
        rpc.systemManager.getSettings(Ext.bind(function(result, exception) {
            if(Ung.Util.handleException(exception)) return;
            var systemSettings = result;
            systemSettings.autoUpgrade = (value == "yes");
            rpc.systemManager.setSettings( delegate, systemSettings );
        }, this));
    },
    complete: function( result, exception, foo, handler ) {
        if(Ung.Util.handleException(exception, "Unable to save Automatic Upgrade Settings")) return;
        Ext.MessageBox.hide();
        handler();
    }
});

// Setup Wizard - Step 6 (Complete)
Ext.define('Ung.SetupWizard.Complete', {
    constructor: function( config ) {
        var panel = Ext.create('Ext.form.Panel', {
            border: false,
            items: [{
                xtype: 'container',
                html: '<h2 class="wizard-title">'+i18n._( "Congratulations!" )+'</h2>'
            }, {
                xtype: 'container',
                html: Ext.String.format(i18n._( '<b>The {0} Server is now configured.</b><br/><br/>You are now ready to configure the applications.' ), rpc.oemName),
                cls: 'noborder'
            }]
        });

        this.card = {
            title: i18n._( "Finished" ),
            cardTitle: i18n._( "Congratulations!" ),
            panel: panel,
            onNext: Ext.bind(this.openUserInterface,this )
        };
    },

    openUserInterface: function( handler ) {
        Ext.MessageBox.wait( i18n._( "Loading User Interface..." ), i18n._( "Please Wait" ));

        //and set a flag so the wizard wont run again
        rpc.jsonrpc.UvmContext.wizardComplete(Ext.bind(function(result, exception) {
            if(Ung.Util.handleException(exception)) return;
          //now open the UI
            window.location.href="/webui/startPage.do?firstTimeRun=true";
        }, this));
    }
});

Ung.Setup = {
    isInitialized: false,
    init: function() {
        if ( this.isInitialized == true ) {
            return;
        }
        this.isInitialized = true;

        JSONRpcClient.toplevel_ex_handler = Ung.Util.rpcExHandler;
        rpc = {};

        Ung.SetupWizard.LabelWidth = 180;
        Ung.SetupWizard.LabelWidth2 = 120;

        // Initialize the prefix data
        Ung.SetupWizard.getV4NetmaskList = function( includeNull ) {
            var data = [];
            if (includeNull) data.push( [null,"\u00a0"] );
            data.push( [32,"/32 - 255.255.255.255"] );
            data.push( [31,"/31 - 255.255.255.254"] );
            data.push( [30,"/30 - 255.255.255.252"] );
            data.push( [29,"/29 - 255.255.255.248"] );
            data.push( [28,"/28 - 255.255.255.240"] );
            data.push( [27,"/27 - 255.255.255.224"] );
            data.push( [26,"/26 - 255.255.255.192"] );
            data.push( [25,"/25 - 255.255.255.128"] );
            data.push( [24,"/24 - 255.255.255.0"] );
            data.push( [23,"/23 - 255.255.254.0"] );
            data.push( [22,"/22 - 255.255.252.0"] );
            data.push( [21,"/21 - 255.255.248.0"] );
            data.push( [20,"/20 - 255.255.240.0"] );
            data.push( [19,"/19 - 255.255.224.0"] );
            data.push( [18,"/18 - 255.255.192.0"] );
            data.push( [17,"/17 - 255.255.128.0"] );
            data.push( [16,"/16 - 255.255.0.0"] );
            data.push( [15,"/15 - 255.254.0.0"] );
            data.push( [14,"/14 - 255.252.0.0"] );
            data.push( [13,"/13 - 255.248.0.0"] );
            data.push( [12,"/12 - 255.240.0.0"] );
            data.push( [11,"/11 - 255.224.0.0"] );
            data.push( [10,"/10 - 255.192.0.0"] );
            data.push( [9,"/9 - 255.128.0.0"] );
            data.push( [8,"/8 - 255.0.0.0"] );
            data.push( [7,"/7 - 254.0.0.0"] );
            data.push( [6,"/6 - 252.0.0.0"] );
            data.push( [5,"/5 - 248.0.0.0"] );
            data.push( [4,"/4 - 240.0.0.0"] );
            data.push( [3,"/3 - 224.0.0.0"] );
            data.push( [2,"/2 - 192.0.0.0"] );
            data.push( [1,"/1 - 128.0.0.0"] );
            data.push( [0,"/0 - 0.0.0.0"] );

            return data;
        };

        rpc.setup = new JSONRpcClient("/setup/JSON-RPC").SetupContext;
        rpc.oemName = "Untangle";
        // Initialize the timezone data
        var timeZonesResult;
        Ung.SetupWizard.TimeZoneStore = [];
        try {
            timeZonesResult = rpc.setup.getTimeZones();
            rpc.oemName = rpc.setup.getOemName();
            rpc.adminEmail = rpc.setup.getAdminEmail();
        } catch (e) {
            Ung.Util.rpcExHandler(e);
        }
        var timeZoneData = eval(timeZonesResult);
        for ( var i = 0; i < timeZoneData.length; i++) {
            Ung.SetupWizard.TimeZoneStore.push([timeZoneData[i][0], "(" + timeZoneData[i][1] + ") " + timeZoneData[i][0]]);
        }

        i18n = new Ung.I18N( { "map": Ung.SetupWizard.CurrentValues.languageMap });
        document.title = i18n._( "Setup Wizard" );

        var welcome    = Ext.create('Ung.SetupWizard.Welcome', {});
        var settings   = Ext.create('Ung.SetupWizard.ServerSettings', {});
        var interfaces = Ext.create('Ung.SetupWizard.Interfaces', {});
        var internet   = Ext.create('Ung.SetupWizard.Internet', {});
        var internal   = Ext.create('Ung.SetupWizard.InternalNetwork', {});
        var upgrades   = Ext.create('Ung.SetupWizard.AutoUpgrades', {});
        var complete   = Ext.create('Ung.SetupWizard.Complete', {});

        Ext.get("container").setStyle("width", "800px");
        this.wizard = Ext.create('Ung.Wizard', {
            height: 500,
            width: 800,
            cardDefaults: {
                labelWidth: Ung.SetupWizard.LabelWidth,
                cls: 'untangle-form-panel'
            },
            cards: [welcome.card, settings.card, interfaces.card, internet.card, internal.card, upgrades.card, complete.card],
            disableNext: false,
            renderTo: "container"
        });

        if ( false ) {
            // DEBUGGING CODE (Change to true to dynamically go to any page you want on load.)
            var debugHandler = Ext.bind(function() {
                this.wizard.goToPage( 2 );
            }, this );
            var ss = Ext.create('Ung.SetupWizard.SettingsSaver', null, debugHandler );

            ss.password = "passwd";
            ss.authenticate( null, null );
            // DEBUGGING CODE
        } else {
            this.wizard.goToPage( 0 );
        }
    }
};


if (!Ung.hasResource["Ung.PolicyManager"]) {
    Ung.hasResource["Ung.PolicyManager"] = true;

    Ung.PolicyManager = Ext.extend(Ung.ConfigWin, {
        fnCallback:null,
        panelPolicyManagement : null,
        gridRacks : null,
        gridRules : null,
        policyStore : null,
        node:null,
        initComponent : function() {
            this.breadcrumbs = [{
                title : this.i18n._('Policy Manager')
            }];
            if(this.node!=null) {
                this.bbar=['-',{
                    name : "Remove",
                    id : this.getId() + "_removeBtn",
                    iconCls : 'node-remove-icon',
                    text : i18n._('Remove'),
                    handler : function() {
                        if(this.node && this.node.settingsWin) {
                            this.node.settingsWin.removeAction();
                        }
                    }.createDelegate(this)
                },'-',{
                    name : 'Help',
                    id : this.getId() + "_helpBtn",
                    iconCls : 'icon-help',
                    text : i18n._('Help'),
                    handler : function() {
                        this.helpAction();
                    }.createDelegate(this)
                },'->',{
                    name : "Cancel",
                    id : this.getId() + "_cancelBtn",
                    iconCls : 'cancel-icon',
                    text : i18n._('Cancel'),
                    handler : function() {
                        this.cancelAction();
                    }.createDelegate(this)
                },'-',{
                    name : "Save",
                    id : this.getId() + "_saveBtn",
                    iconCls : 'save-icon',
                    text : i18n._('Save'),
                    handler : function() {
                        this.saveAction.defer(1, this);
                    }.createDelegate(this)
                },'-'];
            }
            this.buildPolicyManagement();
            // builds the tab panel with the tabs
            this.buildTabPanel([this.panelPolicyManagement]);
            this.tabs.activate(this.panelPolicyManagement);
            Ung.PolicyManager.superclass.initComponent.call(this);
        },

        getPolicyConfiguration : function(forceReload) {
            if (forceReload || this.rpc.policyConfiguration === undefined) {
                try {
                    /* Force a reload of the policy manager */
                    var policyManager = rpc.jsonrpc.RemoteUvmContext.policyManager();
                    rpc.policyManager = policyManager;
                    this.rpc.policyConfiguration = rpc.policyManager.getPolicyConfiguration();
                } catch (e) {
                    Ung.Util.rpcExHandler(e);
                }

            }
            return this.rpc.policyConfiguration;
        },
        getPolicyManagerValidator : function(forceReload) {
            if (forceReload || this.rpc.policyManagerValidator === undefined) {
                try {
                    this.rpc.policyManagerValidator = rpc.policyManager.getValidator();
                } catch (e) {
                    Ung.Util.rpcExHandler(e);
                }

            }
            return this.rpc.policyManagerValidator;
        },
        getPolicyManagerLicenseStatus : function(forceReload) {
            if (forceReload || this.rpc.policyManagerLicenseStatus === undefined) {
                try {
                    this.rpc.policyManagerLicenseStatus = main.getLicenseManager().getLicenseStatus("untangle-policy-manager");
                } catch (e) {
                    Ung.Util.rpcExHandler(e);
                }

            }
            return this.rpc.policyManagerLicenseStatus;
        },
        buildPolicyManagement : function() {
            Ung.Util.clearInterfaceStore();

            this.buildInfo();
            this.buildRacks();
            this.buildPolicies();

            var items = [];

            /* This one should always reload policy management */
            if (this.getPolicyConfiguration(true).hasRackManagement) {
                items.push(this.gridRacks);
            } else {
                items.push(this.infoLabel);
            }

            items.push( this.gridRules );

            this.panelPolicyManagement = new Ext.Panel({
                // private fields
                anchor: "100% 100%",
                name : 'Policy Management',
                parentId : this.getId(),
                title : this.i18n._('Policy Management'),
                layout : "form",
                autoScroll : true,
                items : items
            });
        },
        buildInfo : function() {
            var items = null;

            if ( this.getPolicyManagerLicenseStatus().expired ) {
                items = [{
                    cls: 'description',
                    border: false,
                    html : this.i18n._( 'Need to create network-access policies by username or time of the week? Click on "More Info" to learn more.' )
                },{
                    xtype : 'button',
                    text : this.i18n._( "More Info" ),
                    handler : function() {
                                var app = Ung.AppItem.getAppByLibItem("untangle-libitem-policy");
                                if (app != null && app.libItem != null) {
                            Ung.Window.cancelAction( this.isDirty(),
                               function() {
                                    app.linkToStoreFn();
                               }
                            );
                                }
                    }.createDelegate(this)
                }];
            } else {
                items = [{
                    cls: 'description',
                    border: false,
                    html : this.i18n._( 'You must install the policy manager in order to add additional racks.' )
                }];
            }
            this.infoLabel = new Ext.form.FieldSet({
                title : this.i18n._("Racks"),
                items : items,
                autoHeight : true
            });
        },
        buildRacks : function() {
            this.gridRacks = new Ung.EditorGrid({
                settingsCmp : this,
                anchor :"100% 50%",
                name : 'Racks',
                height : 250,
                bodyStyle : 'padding-bottom:15px;',
                autoScroll : true,
                parentId : this.getId(),
                title : this.i18n._('Racks'),
                recordJavaClass : "com.untangle.uvm.policy.Policy",
                emptyRow : {
                    "default" : false,
                    "name" : this.i18n._("[no name]"),
                    "notes" : this.i18n._("[no description]")
                },
                data : this.getPolicyConfiguration().policies,
                dataRoot : 'list',
                paginated : false,
                //autoExpandColumn: "name",
                fields : [{
                    name : 'id'
                }, {
                    name : 'default'
                }, {
                    name : 'name'
                }, {
                    name : 'notes'
                }],
                columns : [{
                    header : this.i18n._("name"),
                    width : 200,
                    sortable : true,
                    dataIndex : 'name',
                    editor : this.getPolicyManagerLicenseStatus().expired ? null : new Ext.form.TextField({allowBlank : false})
                }, {
                    header : this.i18n._("description"),
                    width : 200,
                    sortable : true,
                    dataIndex : 'notes',
                    editor : this.getPolicyManagerLicenseStatus().expired ? null : new Ext.form.TextField({allowBlank : false})
                }],
                rowEditorInputLines : [new Ext.form.TextField({
                    name : "Name",
                    dataIndex : "name",
                    fieldLabel : this.i18n._("Name"),
                    allowBlank : false,
                    blankText : this.i18n._("The policy name cannot be blank."),
                    width : 200,
                    editor : new Ext.form.TextField({
                        allowBlank : false
                    })
                }), new Ext.form.TextField({
                    name : "Description",
                    dataIndex : "notes",
                    fieldLabel : this.i18n._("Description"),
                    allowBlank : false,
                    width : 200,
                    editor : new Ext.form.TextField({
                        allowBlank : true
                    })
                })],
                addHandler : function() {
                        if (this.getPolicyManagerLicenseStatus(true).expired){
                                this.showProfessionalMessage();
                        } else {
                        Ung.EditorGrid.prototype.addHandler.call(this.gridRacks);
                        }
                }.createDelegate(this),
                editHandler : function(record) {
                        if (this.getPolicyManagerLicenseStatus(true).expired){
                                this.showProfessionalMessage();
                        } else {
                        Ung.EditorGrid.prototype.editHandler.call(this.gridRacks,record);
                        }
                }.createDelegate(this),
                deleteHandler : function(record) {
                        if (this.getPolicyManagerLicenseStatus(true).expired){
                                this.showProfessionalMessage();
                        } else {
                        Ung.EditorGrid.prototype.deleteHandler.call(this.gridRacks,record);
                        }
                }.createDelegate(this)
            });
        },
        showProfessionalMessage : function(){
                Ext.MessageBox.show({
                   title: this.i18n._('Professional Package Feature'),
                   msg: this.i18n._('Need to create network-access policies by username or time of the week? Click on "More Info" to learn more.'),
                    buttons: {ok:this.i18n._('More Info'), cancel:true},
                    fn: function(btn){
                        if(btn=='ok'){
                            var app = Ung.AppItem.getAppByLibItem("untangle-libitem-policy");
                            if (app != null && app.libItem != null) {
                                app.linkToStoreFn();
                            }
                        }
                    },
                   icon: Ext.MessageBox.INFO
               })
        },
        buildPolicies : function() {
            this.policyStoreData = [];
            this.policyStoreData.push({
                key : null,
                name : this.i18n._("> No rack"),
                policy : null
            });
            var policiesList = this.getPolicyConfiguration().policies.list;
            for (var i = 0; i < this.getPolicyConfiguration().policies.list.length; i++) {
                this.policyStoreData.push({
                    key : policiesList[i].name,
                    name : policiesList[i].name,
                    policy : policiesList[i]
                });
            }
            this.policyStore = new Ext.data.JsonStore({
                fields : ['key', 'name', 'policy'],
                data : this.policyStoreData
            });
            var liveColumn = new Ext.grid.CheckColumn({
                header : "<b>" + this.i18n._("live") + "</b>",
                tooltip : this.i18n._("live"),
                dataIndex : 'live',
                width : 25,
                fixed : true
            });
            var usersColumn=new Ext.grid.ButtonColumn({
                width: 80,
                header: this.i18n._("user"),
                dataIndex : 'user',
                handle : function(record) {
                    // populate usersWindow
                    this.grid.usersWindow.show();
                    this.grid.usersWindow.populate(record);
                }
            });
            this.gridRules = new Ung.EditorGrid({
                settingsCmp : this,
                name : 'Policies',
                height : 250,
                anchor :"100% 50%",
                autoScroll : true,
                parentId : this.getId(),
                title : this.i18n._('Policies'),
                recordJavaClass : "com.untangle.uvm.policy.UserPolicyRule",
                hasReorder : true,
                configReorder:{width:35,fixed:false,tooltip:this.i18n._("Reorder")},
                configDelete:{width:30,fixed:false,tooltip:this.i18n._("Delete")},
                configEdit:{width:25,fixed:false,tooltip:this.i18n._("Edit")},
                emptyRow : {
                    "live" : true,
                    "policy" : null,
                    "clientIntf" : "any",
                    "serverIntf" : "any",
                    "protocol" : "TCP & UDP",
                    "clientAddr" : "any",
                    "serverAddr" : "any",
                    "clientPort" : "any",
                    "serverPort" : "any",
                    "user" : "[any]",
                    "startTime" : {"time":-7200000,"javaClass":"java.sql.Time"},
                    "endTime" : {"time":79140000,"javaClass":"java.sql.Time"},
                    "startTimeFormatted" : "00:00",
                    "endTimeFormatted" : "23:59",
                    "dayOfWeek" : "any",
                    "description" : this.i18n._('[no description]')
                },
                // autoExpandColumn : 'notes',
                data : this.getPolicyConfiguration().userPolicyRules,
                dataRoot : 'list',
                paginated : false,
                fields : [{
                    name : 'id'
                }, {
                    name : 'live'
                }, {
                    name : 'policy'
                }, {
                    name : 'policyName',
                    mapping : 'policy',
                    convert : function(val, rec) {
                        return val==null?null:val.name;
                    }
                }, {
                    name : 'clientIntf'
                }, {
                    name : 'serverIntf'
                }, {
                    name : 'protocol'
                }, {
                    name : 'clientAddr'
                }, {
                    name : 'serverAddr'
                }, {
                    name : 'clientPort'
                }, {
                    name : 'serverPort'
                }, {
                    name : 'user'
                }, {
                    name : 'startTime'
                }, {
                    name : 'endTime'
                }, {
                    name : 'startTimeFormatted',
                    mapping: 'startTime',
                    convert : Ung.Util.formatTime
                }, {
                    name : 'endTimeFormatted',
                    mapping: 'endTime',
                    convert : Ung.Util.formatTime
                }, {
                    name : 'dayOfWeek'
                }, {
                    name : 'description'
                }],
                columns : [liveColumn, {
                    header : this.i18n._("<b>Use this rack</b> when the <br/>next colums are matched..."),
                    tooltip : this.i18n._("<b>Use this rack</b> when the <br/>next colums are matched..."),
                    width : 140,
                    sortable : true,
                    dataIndex : 'policyName',
                    renderer : function(value, metadata, record) {
                        var result = ""
                        var store = this.policyStore;
                        if (store) {
                            var index = store.findBy(function(record, id) {
                                if (record.data.key == value) {
                                    return true;
                                } else {
                                    return false;
                                }
                            });
                            if (index >= 0) {
                                result = store.getAt(index).get("name");
                                record.data.policy = store.getAt(index).get("policy");
                            }
                        }
                        return result;
                    }.createDelegate(this),
                    editor : new Ext.form.ComboBox({
                        store : this.policyStore,
                        displayField : 'name',
                        valueField : 'key',
                        editable : false,
                        mode : 'local',
                        triggerAction : 'all',
                        listClass : 'x-combo-list-small'
                    })
                }, {
                    header : this.i18n._("client <br/>interface"),
                    tooltip : this.i18n._("client <br/>interface"),
                    width : 75,
                    sortable : true,
                    dataIndex : 'clientIntf',
                    renderer : function(value) {
                        var result = ""
                        var store = Ung.Util.getInterfaceStore();
                        if (store) {
                            var index = store.find("key", value)
                            if (index >= 0) {
                                result = store.getAt(index).get("name");
                            }
                        }
                        return result;
                    },
                    editor : new Ung.Util.InterfaceCombo({})

                }, {
                    header : this.i18n._("server <br/>interface"),
                    tooltip : this.i18n._("server <br/>interface"),
                    width : 75,
                    sortable : true,
                    dataIndex : 'serverIntf',
                    renderer : function(value) {
                        var result = ""
                        var store = Ung.Util.getInterfaceStore();
                        if (store) {
                            var index = store.find("key", value)
                            if (index >= 0) {
                                result = store.getAt(index).get("name");
                            }
                        }
                        return result;
                    },
                    editor : new Ung.Util.InterfaceCombo({})

                }, {
                    header : this.i18n._("protocol"),
                    tooltip : this.i18n._("protocol"),
                    width : 75,
                    sortable : true,
                    dataIndex : 'protocol',
                    renderer : function(value) {
                        var result = ""
                        var store = Ung.Util.getProtocolStore();
                        if (store) {
                            var index = store.find("key", new RegExp("^"+value+"$"))
                            if (index >= 0) {
                                result = store.getAt(index).get("name");
                            }
                        }
                        return result;
                    },
                    editor : new Ung.Util.ProtocolCombo({})

                }, {
                    header : this.i18n._("client <br/>address"),
                    tooltip: this.i18n._("client <br/>address"),
                    width : 70,
                    sortable : true,
                    dataIndex : 'clientAddr',
                    editor : new Ext.form.TextField({
                        allowBlank : false
                    })

                }, {
                    header : this.i18n._("server <br/>address"),
                    tooltip: this.i18n._("server <br/>address"),
                    width : 70,
                    sortable : true,
                    dataIndex : 'serverAddr',
                    editor : new Ext.form.TextField({
                        allowBlank : false
                    })

                }, {
                    header : this.i18n._("server<br/>port"),
                    tooltip: this.i18n._("server<br/>port"),
                    width : 45,
                    sortable : true,
                    dataIndex : 'serverPort',
                    editor : new Ext.form.TextField({
                        allowBlank : false
                    })

                },
                usersColumn,
                {
                    header : this.i18n._("start time"),
                    tooltip: this.i18n._("start time"),
                    width : 55,
                    sortable : true,
                    dataIndex : 'startTimeFormatted',
                    renderer : function(value, metadata, record) {
                        var dt = Date.parseDate(value, "H:i");
                        record.data.startTime = {"time":dt.getTime(),"javaClass":"java.sql.Time"};
                        return value;
                    }.createDelegate(this),
                    editor : new Ext.form.TimeField({
                        format : "H:i",
                        allowBlank : false
                    })
                }, {
                    header : this.i18n._("end time"),
                    tooltip: this.i18n._("end time"),
                    width : 55,
                    sortable : true,
                    dataIndex : 'endTimeFormatted',
                    renderer : function(value, metadata, record) {
                        var dt = Date.parseDate(value, "H:i");
                        record.data.endTime = {"time":dt.getTime(),"javaClass":"java.sql.Time"};
                        return value;
                    }.createDelegate(this),
                    editor : new Ext.form.TimeField({
                        format : "H:i",
                        allowBlank : false
                    })
                }, {
                    header : this.i18n._("day of week"),
                    tooltip: this.i18n._("day of week"),
                    width : 70,
                    sortable : true,
                    dataIndex : 'dayOfWeek',
                    renderer : function(value, metadata, record) {
                        var out=[];
                        if(value!=null) {
                                var arr=value.split(",");
                                for(var i=0;i<arr.length;i++) {
                                        out.push(this.i18n._(arr[i]));
                                }
                        }
                        return out.join(",");
                    }.createDelegate(this)/*,
                    editor : new Ext.form.TextField({
                        allowBlank : false
                    })*/
                }, {
                    header : this.i18n._("description"),
                    tooltip: this.i18n._("description"),
                    width : 75,
                    sortable : true,
                    dataIndex : 'description',
                    editor : new Ext.form.TextField({
                        allowBlank : false
                    })
                }],
                plugins : [liveColumn,usersColumn],

                initComponent : function() {
                    this.rowEditor = new Ung.RowEditorWindow({
                        grid : this,
                        sizeToRack : true,
                        title : this.settingsCmp.i18n._("Policy Wizard"),
                        inputLines : this.customInputLines,
                        populate : function(record, addMode) {
                            this.addMode = addMode;
                            this.record = record;
                            this.initialRecordData = Ext.encode(record.data);
                            Ext.getCmp("gridRules_rowEditor_protocol").setValue(record.data.protocol);
                            Ext.getCmp("gridRules_rowEditor_client_interface").setValue(record.data.clientIntf);
                            Ext.getCmp("gridRules_rowEditor_server_interface").setValue(record.data.serverIntf);
                            Ext.getCmp("gridRules_rowEditor_client_address").setValue(record.data.clientAddr);
                            Ext.getCmp("gridRules_rowEditor_server_address").setValue(record.data.serverAddr);
                            Ext.getCmp("gridRules_rowEditor_server_port").setValue(record.data.serverPort);
                            Ext.getCmp("gridRules_rowEditor_user").setValue(record.data.user);
                            Ext.getCmp("gridRules_rowEditor_start_time").setValue(record.data.startTimeFormatted);
                            Ext.getCmp("gridRules_rowEditor_end_time").setValue(record.data.endTimeFormatted);
                            Ext.getCmp("gridRules_rowEditor_sunday").setValue(record.data.dayOfWeek == "any"
                                    || record.data.dayOfWeek.indexOf("Sunday") >= 0);
                            Ext.getCmp("gridRules_rowEditor_monday").setValue(record.data.dayOfWeek == "any"
                                    || record.data.dayOfWeek.indexOf("Monday") >= 0);
                            Ext.getCmp("gridRules_rowEditor_tuesday").setValue(record.data.dayOfWeek == "any"
                                    || record.data.dayOfWeek.indexOf("Tuesday") >= 0);
                            Ext.getCmp("gridRules_rowEditor_wednesday").setValue(record.data.dayOfWeek == "any"
                                    || record.data.dayOfWeek.indexOf("Wednesday") >= 0);
                            Ext.getCmp("gridRules_rowEditor_thursday").setValue(record.data.dayOfWeek == "any"
                                    || record.data.dayOfWeek.indexOf("Thursday") >= 0);
                            Ext.getCmp("gridRules_rowEditor_friday").setValue(record.data.dayOfWeek == "any"
                                    || record.data.dayOfWeek.indexOf("Friday") >= 0);
                            Ext.getCmp("gridRules_rowEditor_saturday").setValue(record.data.dayOfWeek == "any"
                                    || record.data.dayOfWeek.indexOf("Saturday") >= 0);
                            Ext.getCmp("gridRules_rowEditor_rack").setValue(record.data.policyName);
                            Ext.getCmp("gridRules_rowEditor_description").setValue(record.data.description);
                            Ext.getCmp("gridRules_rowEditor_live").setValue(record.data.live);
                        },
                        isFormValid : function() {
                            return true;
                        },
                        updateAction : function() {
                            if (this.isFormValid()) {
                                if (this.record !== null) {
                                    this.record.set("protocol", Ext.getCmp("gridRules_rowEditor_protocol").getValue());
                                    this.record.set("clientIntf", Ext.getCmp("gridRules_rowEditor_client_interface").getValue());
                                    this.record.set("serverIntf", Ext.getCmp("gridRules_rowEditor_server_interface").getValue());
                                    this.record.set("clientAddr", Ext.getCmp("gridRules_rowEditor_client_address").getValue());
                                    this.record.set("serverAddr", Ext.getCmp("gridRules_rowEditor_server_address").getValue());
                                    this.record.set("clientPort", "any");
                                    this.record.set("serverPort", Ext.getCmp("gridRules_rowEditor_server_port").getValue());
                                    this.record.set("user", Ext.getCmp("gridRules_rowEditor_user").getValue());
                                    this.record.set("startTimeFormatted", Ext.getCmp("gridRules_rowEditor_start_time").getValue());
                                    this.record.set("endTimeFormatted", Ext.getCmp("gridRules_rowEditor_end_time").getValue());
                                    var dayOfWeek = "";
                                    if (Ext.getCmp("gridRules_rowEditor_sunday").getValue()
                                            && Ext.getCmp("gridRules_rowEditor_monday").getValue()
                                            && Ext.getCmp("gridRules_rowEditor_tuesday").getValue()
                                            && Ext.getCmp("gridRules_rowEditor_wednesday").getValue()
                                            && Ext.getCmp("gridRules_rowEditor_thursday").getValue()
                                            && Ext.getCmp("gridRules_rowEditor_friday").getValue()
                                            && Ext.getCmp("gridRules_rowEditor_saturday").getValue()) {
                                       dayOfWeek="any"

                                    } else {
                                        var out = [];
                                        if (Ext.getCmp("gridRules_rowEditor_sunday").getValue()) {
                                            out.push("Sunday");
                                        }
                                        if (Ext.getCmp("gridRules_rowEditor_monday").getValue()) {
                                            out.push("Monday");
                                        }
                                        if (Ext.getCmp("gridRules_rowEditor_tuesday").getValue()) {
                                            out.push("Tuesday");
                                        }
                                        if (Ext.getCmp("gridRules_rowEditor_wednesday").getValue()) {
                                            out.push("Wednesday");
                                        }
                                        if (Ext.getCmp("gridRules_rowEditor_thursday").getValue()) {
                                            out.push("Thursday");
                                        }
                                        if (Ext.getCmp("gridRules_rowEditor_friday").getValue()) {
                                            out.push("Friday");
                                        }
                                        if (Ext.getCmp("gridRules_rowEditor_saturday").getValue()) {
                                            out.push("Saturday");
                                        }
                                        dayOfWeek=out.join(",");
                                    }
                                    this.record.set("dayOfWeek", dayOfWeek);
                                    this.record.set("policyName", Ext.getCmp("gridRules_rowEditor_rack").getValue());
                                    this.record.set("description", Ext.getCmp("gridRules_rowEditor_description").getValue());
                                    this.record.set("live", Ext.getCmp("gridRules_rowEditor_live").getValue());

                                    if (this.addMode) {
                                        this.grid.getStore().insert(0, [this.record]);
                                        this.grid.updateChangedData(this.record, "added");
                                    }
                                }
                                this.hide();
                            } else {
                                Ext.MessageBox.alert(i18n._('Warning'), i18n._("The form is not valid!"));
                            }
                        },
                        isDirty : function() {
                            var initial_record_data = Ext.decode(this.initialRecordData);

                            return Ext.getCmp("gridRules_rowEditor_protocol").getValue() != initial_record_data.protocol
                                || Ext.getCmp("gridRules_rowEditor_client_interface").getValue() != initial_record_data.clientIntf
                                || Ext.getCmp("gridRules_rowEditor_server_interface").getValue() != initial_record_data.serverIntf
                                || Ext.getCmp("gridRules_rowEditor_client_address").getValue() != initial_record_data.clientAddr
                                || Ext.getCmp("gridRules_rowEditor_server_address").getValue() != initial_record_data.serverAddr
                                || Ext.getCmp("gridRules_rowEditor_server_port").getValue() != initial_record_data.serverPort
                                || Ext.getCmp("gridRules_rowEditor_user").getValue() != initial_record_data.user
                                || Ext.getCmp("gridRules_rowEditor_start_time").getValue() != initial_record_data.startTimeFormatted
                                || Ext.getCmp("gridRules_rowEditor_end_time").getValue() != initial_record_data.endTimeFormatted
                                || Ext.getCmp("gridRules_rowEditor_sunday").getValue() !=
                                    (initial_record_data.dayOfWeek == "any" || initial_record_data.dayOfWeek.indexOf("Sunday") >= 0)
                                || Ext.getCmp("gridRules_rowEditor_monday").getValue() !=
                                    (initial_record_data.dayOfWeek == "any" || initial_record_data.dayOfWeek.indexOf("Monday") >= 0)
                                || Ext.getCmp("gridRules_rowEditor_tuesday").getValue() !=
                                    (initial_record_data.dayOfWeek == "any" || initial_record_data.dayOfWeek.indexOf("Tuesday") >= 0)
                                || Ext.getCmp("gridRules_rowEditor_wednesday").getValue() !=
                                    (initial_record_data.dayOfWeek == "any" || initial_record_data.dayOfWeek.indexOf("Wednesday") >= 0)
                                || Ext.getCmp("gridRules_rowEditor_thursday").getValue() !=
                                    (initial_record_data.dayOfWeek == "any" || initial_record_data.dayOfWeek.indexOf("Thursday") >= 0)
                                || Ext.getCmp("gridRules_rowEditor_friday").getValue() !=
                                    (initial_record_data.dayOfWeek == "any" || initial_record_data.dayOfWeek.indexOf("Friday") >= 0)
                                || Ext.getCmp("gridRules_rowEditor_saturday").getValue() !=
                                    (initial_record_data.dayOfWeek == "any" || initial_record_data.dayOfWeek.indexOf("Saturday") >= 0)
                                || Ext.getCmp("gridRules_rowEditor_rack").getValue() != initial_record_data.policyName
                                || Ext.getCmp("gridRules_rowEditor_description").getValue() != initial_record_data.description
                                || Ext.getCmp("gridRules_rowEditor_live").getValue() != initial_record_data.live;
                        },
                        show : function() {
                            Ung.UpdateWindow.superclass.show.call(this);
                        }
                    });

                    this.usersWindow= new Ung.UsersWindow({
                        grid : this,
                        title : i18n._('Select Users'),
                        userDataIndex : "user",
                        sortField : 'UID',
                        loadLocalDirectoryUsers: false
                    });

                    this.groupsWindow= new Ung.GroupsWindow({
                        grid : this,
                        userDataIndex : "group",
                        loadLocalDirectoryGroups: false
                    });
                    Ung.EditorGrid.prototype.initComponent.call(this);
                },
                customInputLines : [{
                    xtype : 'fieldset',
                    autoHeight : true,
                    title : this.i18n._("Protocols"),
                    items : [{
                        cls: 'description',
                        border : false,
                        html : this.i18n._("The protocol you would like this policy to handle.")
                    }, new Ung.Util.ProtocolCombo({
                        id : 'gridRules_rowEditor_protocol',
                        xtype : 'combo',
                        fieldLabel : this.i18n._("Protocol")
                    })]
                }, {
                    xtype : 'fieldset',
                    autoHeight : true,
                    title : this.i18n._("Interface"),
                    items : [{
                        cls: 'description',
                        border : false,
                        html : this.i18n._("The ethernet interface (NIC) you would like this policy to handle.")
                    }, new Ung.Util.InterfaceCombo({
                        name : 'Client',
                        id : 'gridRules_rowEditor_client_interface',
                        fieldLabel : this.i18n._("Client"),
                        editable : false,
                        store : Ung.Util.getInterfaceStore(),
                        width : 350

                    }), new Ung.Util.InterfaceCombo({
                        name : 'Server',
                        id : 'gridRules_rowEditor_server_interface',
                        fieldLabel : this.i18n._("Server"),
                        width : 350

                    })]
                }, {
                    xtype : 'fieldset',
                    autoHeight : true,
                    title : this.i18n._("Address"),
                    items : [{
                        cls: 'description',
                        border : false,
                        html : this.i18n._("The IP address which you would like this policy to handle.")
                    }, {
                        xtype : 'textfield',
                        name : 'Client',
                        id : 'gridRules_rowEditor_client_address',
                        fieldLabel : this.i18n._("Client"),
                        allowBlank : false
                    }, {
                        xtype : 'textfield',
                        name : 'Server',
                        id : 'gridRules_rowEditor_server_address',
                        fieldLabel : this.i18n._("Server"),
                        allowBlank : false
                    }]
                }, {
                    xtype : 'fieldset',
                    autoHeight : true,
                    title : this.i18n._("Port"),
                    items : [{
                        cls: 'description',
                        border : false,
                        html : this.i18n._("The port which you would like this policy to handle.")
                    }, {
                        xtype : 'textfield',
                        name : 'Server',
                        id : 'gridRules_rowEditor_server_port',
                        fieldLabel : this.i18n._("Server"),
                        allowBlank : false
                    }]
                }, {
                    xtype : 'fieldset',
                    autoHeight : true,
                    title : this.i18n._("Users"),
                    items : [{
                        cls: 'description',
                        border : false,
                        html : this.i18n._("The users you would like to apply this policy to.")
                    }, {
                        xtype : 'textfield',
                        name : 'Users',
                        width : 200,
                        readOnly : true,
                        id : 'gridRules_rowEditor_user',
                        fieldLabel : this.i18n._("Users"),
                        allowBlank : false
                    }, {
                        xtype: "button",
                        name : 'Change Users',
                        text : i18n._("Change Users"),
                        handler : function() {
                            this.gridRules.usersWindow.show();
                            this.gridRules.usersWindow.populate(this.gridRules.rowEditor.record,function() {
                                Ext.getCmp("gridRules_rowEditor_user").setValue(this.gridRules.rowEditor.record.data.user);
                            }.createDelegate(this));
                        }.createDelegate(this)
                    }]
                }, /*{
                    xtype : 'fieldset',
                    autoHeight : true,
                    title : this.i18n._("Groups"),
                    items : [{
                        cls: 'description',
                        border : false,
                        html : this.i18n._("The groups you would like to apply this policy to.")
                    }, {
                        xtype : 'textfield',
                        name : 'Groups',
                        width : 200,
                        readOnly : true,
                        id : 'gridRules_rowEditor_group',
                        fieldLabel : this.i18n._("Groups"),
                        allowBlank : false
                    }, {
                        xtype: "button",
                        name : 'Change Groups',
                        text : i18n._("Change Groups"),
                        handler : function() {
                            this.gridRules.groupsWindow.show();
                            this.gridRules.groupsWindow.populate(this.gridRules.rowEditor.record,function() {
                                Ext.getCmp("gridRules_rowEditor_group").setValue(this.gridRules.rowEditor.record.data.group);
                            }.createDelegate(this));
                        }.createDelegate(this)
                    }]
                }, */{
                    xtype : 'fieldset',
                    autoHeight : true,
                    title : this.i18n._("Time of Day"),
                    items : [{
                        cls: 'description',
                        border : false,
                        html : this.i18n._("The time of day you would like this policy active.")
                    }, {
                        xtype : 'timefield',
                        name : 'Start Time',
                        format : "H:i",
                        id : 'gridRules_rowEditor_start_time',
                        fieldLabel : this.i18n._("Start Time"),
                        allowBlank : false
                    }, {
                        xtype : 'timefield',
                        name : 'End Time',
                        id : 'gridRules_rowEditor_end_time',
                        format : "H:i",
                        fieldLabel : this.i18n._("End Time"),
                        allowBlank : false
                    }]
                }, {
                    xtype : 'fieldset',
                    autoHeight : true,
                    title : this.i18n._("Days of Week"),
                    items : [{
                        cls: 'description',
                        border : false,
                        html : this.i18n._("The days of the week you would like this policy active.")
                    }, {
                        xtype : 'checkbox',
                        name : 'Sunday',
                        id : 'gridRules_rowEditor_sunday',
                        boxLabel : this.i18n._('Sunday'),
                        hideLabel : true,
                        checked : true
                    }, {
                        xtype : 'checkbox',
                        name : 'Monday',
                        id : 'gridRules_rowEditor_monday',
                        boxLabel : this.i18n._('Monday'),
                        hideLabel : true,
                        checked : true
                    }, {
                        xtype : 'checkbox',
                        name : 'Tuesday',
                        id : 'gridRules_rowEditor_tuesday',
                        boxLabel : this.i18n._('Tuesday'),
                        hideLabel : true,
                        checked : true
                    }, {
                        xtype : 'checkbox',
                        name : 'Wednesday',
                        id : 'gridRules_rowEditor_wednesday',
                        boxLabel : this.i18n._('Wednesday'),
                        hideLabel : true,
                        checked : true
                    }, {
                        xtype : 'checkbox',
                        name : 'Thursday',
                        id : 'gridRules_rowEditor_thursday',
                        boxLabel : this.i18n._('Thursday'),
                        hideLabel : true,
                        checked : true
                    }, {
                        xtype : 'checkbox',
                        name : 'Friday',
                        id : 'gridRules_rowEditor_friday',
                        boxLabel : this.i18n._('Friday'),
                        hideLabel : true,
                        checked : true
                    }, {
                        xtype : 'checkbox',
                        name : 'Saturday',
                        id : 'gridRules_rowEditor_saturday',
                        boxLabel : this.i18n._('Saturday'),
                        hideLabel : true,
                        checked : true
                    }]
                }, {
                    xtype : 'fieldset',
                    autoHeight : true,
                    title : this.i18n._("Rack"),
                    items : [{
                        cls: 'description',
                        border : false,
                        html : this.i18n._("The rack you would like to use to handle this policy.")
                    }, {
                        xtype : 'combo',
                        name : 'Rack',
                        id : 'gridRules_rowEditor_rack',
                        fieldLabel : this.i18n._("Rack"),
                        editable : false,
                        store : this.policyStore,
                        displayField : 'name',
                        valueField : 'key',
                        width : 200,
                        mode : 'local',
                        triggerAction : 'all',
                        listClass : 'x-combo-list-small'

                    }, {
                        xtype : 'textfield',
                        name : 'Description',
                        width : 200,
                        id : 'gridRules_rowEditor_description',
                        fieldLabel : this.i18n._("Description"),
                        allowBlank : false
                    }]
                }, {
                    xtype : 'fieldset',
                    autoHeight : true,
                    items : [{
                        xtype : 'checkbox',
                        name : 'Enable this Policy',
                        id : 'gridRules_rowEditor_live',
                        boxLabel : this.i18n._('Enable this Policy'),
                        hideLabel : true,
                        checked : true
                    }]
                }]
            });
        },
        validateClient : function() {
            var rackList=this.gridRacks.getFullSaveList();
            if(rackList.length==0) {
                Ext.MessageBox.alert(i18n._("Failed"), this.i18n._("There must always be at least one available rack."));
                return false;
            }
            for(var i=0;i<rackList.length;i++) {
                for(var j=i+1;j<rackList.length;j++) {
                   if(rackList[i].name==rackList[j].name) {
                           Ext.MessageBox.alert(i18n._("Failed"), String.format(this.i18n._("The rack named {0} already exists."),rackList[i].name));
                       return false;
                   }
                }
            }
            var rulesList=this.gridRules.getFullSaveList();
            var rackDeletedList=this.gridRacks.getDeletedList();
            for(var i=0;i<rulesList.length;i++) {
                if(rulesList[i].policy!=null) {
                        for(var j=0;j<rackDeletedList.length;j++) {
                                if(rulesList[i].policy.id==rackDeletedList[j].id) {
                                        Ext.MessageBox.alert(i18n._("Failed"), String.format(this.i18n._('The rack named {0} cannot be removed because it is currently being used in "Custom Policies".'),rackDeletedList[j].name));
                            return false;
                                }
                        }
                }
            }
            return true;
        },
        validateServer : function() {
                var rackDeletedList=this.gridRacks.getDeletedList();
                for(var i=0;i<rackDeletedList.length;i++)
                try {
                        try {
                    var result = rpc.nodeManager.nodeInstances(rackDeletedList[i]);
                } catch (e) {
                    Ung.Util.rpcExHandler(e);
                }

                if (result.list.length>0) {

//                var isEmptyPolicy = rpc.nodeManager.isEmptyPolicy(rackDeletedList[i]);
//                if (!isEmptyPolicy) {
                    Ext.MessageBox.alert(i18n._("Failed"), String.format(this.i18n._("The rack named {0} cannot be removed because it is not empty.  Please remove all products first."),rackDeletedList[i].name));
                    return false;
                }
            } catch (e) {
                Ext.MessageBox.alert(i18n._("Failed"), e.message);
                return false;
            }

            var passedAddresses = this.gridRules.getFullSaveList();
            var clientAddrList = [];
            var serverAddrList = [];
            var serverPortList = [];
            var dstPortList = [];
            for (var i = 0; i < passedAddresses.length; i++) {
                clientAddrList.push(passedAddresses[i]["clientAddr"]);
                serverAddrList.push(passedAddresses[i]["serverAddr"]);
                serverPortList.push(passedAddresses[i]["serverPort"]);
            }
            var validateData = {
                map : {},
                javaClass : "java.util.HashMap"
            };
            if (clientAddrList.length > 0) {
                validateData.map["CLIENT_ADDR"] = {"javaClass" : "java.util.ArrayList", list : clientAddrList};
            }
            if (serverAddrList.length > 0) {
                validateData.map["SERVER_ADDR"] = {"javaClass" : "java.util.ArrayList", list : serverAddrList};
            }
            if (serverPortList.length > 0) {
                validateData.map["SERVER_PORT"] = {"javaClass" : "java.util.ArrayList", list : serverPortList};
            }
            if (Ung.Util.hasData(validateData.map)) {
                try {
                        try {
                        var result = this.getPolicyManagerValidator().validate(validateData);
                    } catch (e) {
                        Ung.Util.rpcExHandler(e);
                    }

                    if (!result.valid) {
                        var errorMsg = "";
                        switch (result.errorCode) {
                            case 'INVALID_CLIENT_ADDR' :
                                errorMsg = this.i18n._("Invalid address specified for Client Address") + ": " + result.cause;
                            break;
                            case 'INVALID_SERVER_ADDR' :
                                errorMsg = this.i18n._("Invalid address specified for Server Address") + ": " + result.cause;
                            break;
                            case 'INVALID_SERVER_PORT' :
                                errorMsg = this.i18n._("Invalid port specified for Server Port") + ": " + result.cause;
                            break;
                            default :
                                errorMsg = this.i18n._(result.errorCode) + ": " + result.cause;
                        }
                        Ext.MessageBox.alert(this.i18n._("Validation failed"), errorMsg);
                        return false;
                    }
                } catch (e) {
                    Ext.MessageBox.alert(i18n._("Failed"), e.message);
                    return false;
                }
            }
            return true;
        },
        // save function
        saveAction : function() {
            if (this.validate()) {
                this.getPolicyConfiguration().policies.list=this.gridRacks.getFullSaveList();
                this.getPolicyConfiguration().userPolicyRules.list=this.gridRules.getFullSaveList();
                Ext.MessageBox.wait(i18n._("Saving..."), i18n._("Please wait"));
                rpc.policyManager.setPolicyConfiguration(function(result, exception) {
                    if(Ung.Util.handleException(exception)) return;
                    Ext.MessageBox.hide();
                    this.closeWindow();
                    main.loadPolicies.defer(1,main);
                }.createDelegate(this), this.getPolicyConfiguration());
            }
        },
        // save function
        closeWindow : function() {
                Ung.PolicyManager.superclass.closeWindow.call(this);
                if(this.fnCallback) {
                    this.fnCallback.call();
                }
        },
        isDirty : function() {
                return this.gridRacks.isDirty() || this.gridRules.isDirty();
        }
    });
}

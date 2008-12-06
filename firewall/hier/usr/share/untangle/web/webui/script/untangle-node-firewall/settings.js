if (!Ung.hasResource["Ung.Firewall"]) {
    Ung.hasResource["Ung.Firewall"] = true;
    Ung.Settings.registerClassName('untangle-node-firewall', 'Ung.Firewall');

    Ung.Firewall = Ext.extend(Ung.Settings, {
    	panelRules: null,
        gridRules : null,
        gridEventLog : null,
        initComponent : function() {
            Ung.Util.clearInterfaceStore();
            // builds the tabs
            this.buildRules();
            this.buildEventLog();
            // builds the tab panel with the tabs
            this.buildTabPanel([this.panelRules, this.gridEventLog]);
            Ung.Firewall.superclass.initComponent.call(this);
        },
        // Rules Panel
        buildRules : function() {
            // enable is a check column
            var liveColumn = new Ext.grid.CheckColumn({
                header : this.i18n._("Enable"),
                dataIndex : 'live',
                fixed : true
            });

            var actionData = [["Pass", this.i18n._('Pass')],["Block", this.i18n._('Block')]];

            this.panelRules = new Ext.Panel({
                name : 'panelRules',
                helpSource : 'rules',
                // private fields
                gridRulesList : null,
                parentId : this.getId(),
                title : this.i18n._('Rules'),
                layout : 'anchor',
                defaults: {
                	anchor: '98%',
                	autoWidth: true,
                	autoScroll: true
                },
                autoScroll : true,
                border : false,
                cls: 'ung-panel',
                items : [this.gridRules = new Ung.EditorGrid({
                        name : 'Rules',
                        settingsCmp : this,
                        height : 500,
                        totalRecords : this.getBaseSettings().firewallRulesLength,
                        paginated : false,
                        hasReorder : true,
                        emptyRow : {
                            "live" : true,
                            "action" : 'Block',
                            "log" : false,
                            "protocol" : "TCP & UDP",
                            "srcIntf" : "any",
                            "dstIntf" : "any",
                            "srcAddress" : "1.2.3.4",
                            "dstAddress" : "1.2.3.4",
                            "srcPort" : "any",
                            "dstPort" : "2-5",
                            "name" : this.i18n._("[no name]"),
                            "category" : this.i18n._("[no category]"),
                            "description" : this.i18n._("[no description]"),
                            "javaClass" : "com.untangle.node.firewall.FirewallRule"
                        },
                        title : this.i18n._("Rules"),
                        recordJavaClass : "com.untangle.node.firewall.FirewallRule",
                        proxyRpcFn : this.getRpcNode().getFirewallRuleList,
                        fields : [{
                            name : 'id'
                        }, {
                            name : 'live'
                        }, {
                            name : 'action'
                        }, {
                            name : 'log'
                        }, {
                            name : 'protocol'
                        }, {
                            name : 'srcIntf'
                        }, {
                            name : 'dstIntf'
                        }, {
                            name : 'srcAddress'
                        }, {
                            name : 'dstAddress'
                        }, {
                            name : 'srcPort'
                        }, {
                            name : 'dstPort'
                        }, {
                            name : 'name'
                        }, {
                            name : 'category'
                        }, {
                            name : 'description'
                        }, {
                            name : 'javaClass'
                        }],
                        columns : [liveColumn, {
                            id : 'description',
                            header : this.i18n._("Description"),
                            width : 200,
                            dataIndex : 'description'
                        }],
                        columnsDefaultSortable : false,
                        autoExpandColumn : 'description',
                        plugins : [liveColumn],
                        
                        initComponent : function() {
                            this.rowEditor = new Ung.RowEditorWindow({
                                grid : this,
                                sizeToGrid : true,
                                inputLines : this.customInputLines,
                                rowEditorLabelWidth:120,
                                populate : function(record, addMode) {
                                    this.addMode=addMode;
                                    this.record = record;
                                    this.initialRecordData = Ext.encode(record.data);
                                    for (var i = 0; i < this.inputLines.length; i++) {
                                        var inputLine = this.inputLines[i];
                                        if (inputLine instanceof Ext.form.Field) {
                                        	this.populateField(inputLine, record);
                                        } else if (inputLine instanceof Ext.Panel) {
                                            for (var j = 0; j < inputLine.items.length; j++) {
                                                var field = inputLine.items.get(j);
                                                if ( field instanceof Ext.form.Field) {
                                                    this.populateField(field, record);
                                                }
                                            }
                                        }
                                    }
                                },
                                populateField : function(field, record) {
                                    if(field.dataIndex!=null) {
                                        field.suspendEvents();
                                        field.setValue(record.get(field.dataIndex));
                                        field.resumeEvents();
                                    }
                                },
                                isFormValid : function() {
                                    for (var i = 0; i < this.inputLines.length; i++) {
                                        var inputLine = this.inputLines[i];
                                        if (inputLine instanceof Ext.form.Field && !inputLine.isValid()) {
                                            return false;
                                        } else if (inputLine instanceof Ext.Panel) {
                                            for (var j = 0; j < inputLine.items.length; j++) {
                                                var field = inputLine.items.get(j);
                                                if (field instanceof Ext.form.Field && !field.isValid()) {
                                                    return false;
                                                }
                                            }
                                        }
                                    }
                                    return true;
                                },
                                updateAction : function() {
                                    if (this.isFormValid()) {
                                        if (this.record !== null) {
                                            for (var i = 0; i < this.inputLines.length; i++) {
                                                var inputLine = this.inputLines[i];
                                                if (inputLine instanceof Ext.form.Field) {
                                                    this.record.set(inputLine.dataIndex, inputLine.getValue());
                                                } else if (inputLine instanceof Ext.Panel) {
                                                    for (var j = 0; j < inputLine.items.length; j++) {
                                                        var field = inputLine.items.get(j);
                                                        if (field instanceof Ext.form.Field) {
                                                            this.record.set(field.dataIndex, field.getValue());
                                                        }
                                                    }
                                                }
                                            }
        
                                            if (this.addMode) {
                                                this.grid.getStore().insert(0, [this.record]);
                                                this.grid.updateChangedData(this.record, "added");
                                            }
                                        }
                                        this.hide();
                                    } else {
                                        Ext.MessageBox.alert(i18n._('Warning'), i18n._("The form is not valid!"));
                                    }
                                }
                            });
                            
                            Ung.EditorGrid.prototype.initComponent.call(this);
                        },
                        
                        customInputLines : [new Ext.form.Checkbox({
                            name : "Enable Rule",
                            dataIndex: "live",
                            fieldLabel : this.i18n._("Enable Rule"),
                            itemCls:'firewall-spacing-1'
                        }),
                        new Ext.form.TextField({
                            name : "Description",
                            dataIndex: "description",
                            fieldLabel : this.i18n._("Description"),
                            itemCls:'firewall-spacing-1',                            
                            width : 400
                        }), new Ext.form.ComboBox({
                            name : "Action",
                            dataIndex: "action",
                            fieldLabel : this.i18n._("Action"),
                            store : new Ext.data.SimpleStore({
                                fields : ['key', 'name'],
                                data : actionData
                            }),
                            displayField : 'name',
                            valueField : 'key',
                            forceSelection : true,
                            typeAhead : true,
                            mode : 'local',
                            triggerAction : 'all',
                            listClass : 'x-combo-list-small',
                            itemCls:'firewall-spacing-1',                                                        
                            selectOnFocus : true
                        }), new Ext.form.Checkbox({
                            name : "Log",
                            dataIndex: "log",
                            itemCls:'firewall-spacing-1',                                                        
                            fieldLabel : this.i18n._("Log")
                        }), 
                        new Ext.form.FieldSet({
                            title : i18n._('Rule') ,
                            cls:'firewall-spacing-2',
                            autoHeight : true,  
                            items:[                                
                                new Ung.Util.ProtocolCombo({
                                    name : "Traffic Type",
                                    dataIndex: "protocol",
                                    itemCls:'firewall-spacing-3',                                                        
                                    fieldLabel : this.i18n._("Traffic Type"),
                                    width : 100
                                }), new Ung.Util.InterfaceCombo({
                                    name : "Source Interface",
                                    dataIndex: "srcIntf",
                                    fieldLabel : this.i18n._("Source Interface"),
                                    width : 150
                                }), new Ung.Util.InterfaceCombo({
                                    name : "Destination Interface",
                                    dataIndex: "dstIntf",
                                    itemCls:'firewall-spacing-3',                                                        
                                    fieldLabel : this.i18n._("Destination Interface"),
                                    width : 150
                                }), new Ext.form.TextField({
                                    name : "Source Address",
                                    dataIndex: "srcAddress",
                                    fieldLabel : this.i18n._("Source Address"),
                                    allowBlank : false,
                                    width : 150
                                }), new Ext.form.TextField({
                                    name : "Destination Address",
                                    dataIndex: "dstAddress",
                                    itemCls:'firewall-spacing-3',                                                                                            
                                    fieldLabel : this.i18n._("Destination Address"),
                                    allowBlank : false,
                                    width : 150
                                }), new Ext.form.TextField({
                                    name : "Source Port",
                                    dataIndex: "srcPort",
                                    fieldLabel : this.i18n._("Source Port"),
                                    width : 150,
                                    allowBlank : false
                                }), new Ext.form.TextField({
                                    name : "Destination Port",
                                    dataIndex: "dstPort",
                                    fieldLabel : this.i18n._("Destination Port"),
                                    allowBlank : false,
                                    width : 150
                                })                           
                            ]
                        })]
                    }),{
                        xtype : 'fieldset',
                        autoHeight : true,
                        cls:'firewall-top-margin-1',
                        title : this.i18n._('Default Action'),
                        items : [{
	                        xtype : 'radio',
                            boxLabel : this.i18n._('Block'), 
                            hideLabel : true,
	                        name : 'isDefaultAccept',
	                        checked : !this.getBaseSettings().defaultAccept,
	                        listeners : {
	                            "check" : {
	                                fn : function(elem, checked) {
	                                    this.getBaseSettings().defaultAccept = !checked;
	                                }.createDelegate(this)
	                            }
	                        }
	                    },{
	                        xtype : 'radio',
	                        boxLabel : this.i18n._('Pass'), 
	                        hideLabel : true,
	                        name : 'isDefaultAccept',
	                        checked : this.getBaseSettings().defaultAccept,
	                        listeners : {
	                            "check" : {
	                                fn : function(elem, checked) {
	                                    this.getBaseSettings().defaultAccept = checked;
	                                }.createDelegate(this)
	                            }
	                        }
                        }]
                    }
                ]
            });
        },
        // Event Log
        buildEventLog : function() {
            this.gridEventLog = new Ung.GridEventLog({
                settingsCmp : this,
                fields : [{
                    name : 'id'
                }, {
                    name : 'timeStamp',
                    sortType : Ung.SortTypes.asTimestamp
                }, {
                    name : 'action',
                    mapping : 'wasBlocked',
                    type : 'string',
                    convert : function(value) {
                        return value ? this.i18n._("blocked") : this.i18n._("passed");
                    }.createDelegate(this)
                }, {
                    name : 'ruleIndex'
                }, {
                    name : 'client',
                    mapping : 'pipelineEndpoints',
                    sortType : Ung.SortTypes.asClient
                }, {
                    name : 'server',
                    mapping : 'pipelineEndpoints',
                    sortType : Ung.SortTypes.asServer
                }],
                columns : [{
                    header : this.i18n._("timestamp"),
                    width : 130,
                    sortable : true,
                    dataIndex : 'timeStamp',
                    renderer : function(value) {
                        return i18n.timestampFormat(value);
                    }
                }, {
                    header : this.i18n._("action"),
                    width : 100,
                    sortable : true,
                    dataIndex : 'action'
                }, {
                    header : this.i18n._("client"),
                    width : 165,
                    sortable : true,
                    dataIndex : 'client',
                    renderer : Ung.SortTypes.asClient
                }, {
                	id: 'ruleIndex',
                    header : this.i18n._('reason for action'),
                    width : 150,
                    sortable : true,
                    dataIndex : 'ruleIndex',
                    renderer : function(value, metadata, record) {
                           return String.format(this.i18n._("rule #{0}"), value);
					}.createDelegate(this)
                }, {
                    header : this.i18n._("server"),
                    width : 165,
                    sortable : true,
                    dataIndex : 'server',
                    renderer : Ung.SortTypes.asServer
                }],
                autoExpandColumn: 'ruleIndex'
                
            });
        },
        validateServer : function() {
            // ipMaddr list must be validated server side
            var passedAddresses = this.gridRules ? this.gridRules.getFullSaveList() : null;
            if (passedAddresses != null) {
                var srcAddrList = [];
                var dstAddrList = [];
                var srcPortList = [];
                var dstPortList = [];
                for (var i = 0; i < passedAddresses.length; i++) {
                    srcAddrList.push(passedAddresses[i]["srcAddress"]);
                    dstAddrList.push(passedAddresses[i]["dstAddress"]);
                    srcPortList.push(passedAddresses[i]["srcPort"]);
                    dstPortList.push(passedAddresses[i]["dstPort"]);
                }
                var validateData = {
                    map : {},
                    javaClass : "java.util.HashMap"
                }; 
                if (srcAddrList.length > 0) {
                    validateData.map["SRC_ADDR"] = {"javaClass" : "java.util.ArrayList", list : srcAddrList};
                }
                if (dstAddrList.length > 0) {
                    validateData.map["DST_ADDR"] = {"javaClass" : "java.util.ArrayList", list : dstAddrList};
                }
                if (srcPortList.length > 0) {
                    validateData.map["SRC_PORT"] = {"javaClass" : "java.util.ArrayList", list : srcPortList};
                }
                if (dstPortList.length > 0) {
                    validateData.map["DST_PORT"] = {"javaClass" : "java.util.ArrayList", list : dstPortList};
                }
                if (Ung.Util.hasData(validateData.map)) {
                    try {
                    	var result=null;
                    	try {
                            result = this.getValidator().validate(validateData);
                        } catch (e) {
                            Ung.Util.rpcExHandler(e);
                        }
                        if (!result.valid) {
                            var errorMsg = "";
                            switch (result.errorCode) {
                                case 'INVALID_SRC_ADDR' : 
                                    errorMsg = this.i18n._("Invalid address specified for Source Address") + ": " + result.cause;
                                break;
                                case 'INVALID_DST_ADDR' : 
                                    errorMsg = this.i18n._("Invalid address specified for Destination Address") + ": " + result.cause;
                                break;
                                case 'INVALID_SRC_PORT' : 
                                    errorMsg = this.i18n._("Invalid port specified for Source Port") + ": " + result.cause;
                                break;
                                case 'INVALID_DST_PORT' : 
                                    errorMsg = this.i18n._("Invalid port specified for Destination Port") + ": " + result.cause;
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
            }
            return true;
        },
        // save function
        saveAction : function() {
            if (this.validate()) {
                Ext.MessageBox.wait(i18n._("Saving..."), i18n._("Please wait"));
                this.getRpcNode().updateAll(function(result, exception) {
                    Ext.MessageBox.hide();
                    if(Ung.Util.handleException(exception)) return;
                    // exit settings screen
                    this.cancelAction();
                }.createDelegate(this), this.getBaseSettings(), this.gridRules ? {javaClass:"java.util.ArrayList",list:this.gridRules.getFullSaveList()} : null);
            }
        }
    });
}
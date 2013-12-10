if (!Ung.hasResource["Ung.Firewall"]) {
    Ung.hasResource["Ung.Firewall"] = true;
    Ung.NodeWin.registerClassName('untangle-node-firewall', 'Ung.Firewall');

    Ung.FirewallUtil={
        getMatchers: function (settingsCmp) {
            return [
                {name:"DST_ADDR",displayName: settingsCmp.i18n._("Destination Address"), type: "text", visible: true, vtype:"ipMatcher"},
                {name:"DST_PORT",displayName: settingsCmp.i18n._("Destination Port"), type: "text",vtype:"portMatcher", visible: true},
                {name:"DST_INTF",displayName: settingsCmp.i18n._("Destination Interface"), type: "checkgroup", values: Ung.Util.getInterfaceList(true, false), visible: true},
                {name:"SRC_ADDR",displayName: settingsCmp.i18n._("Source Address"), type: "text", visible: true, vtype:"ipMatcher"},
                {name:"SRC_PORT",displayName: settingsCmp.i18n._("Source Port"), type: "text",vtype:"portMatcher", visible: false},
                {name:"SRC_INTF",displayName: settingsCmp.i18n._("Source Interface"), type: "checkgroup", values: Ung.Util.getInterfaceList(true, false), visible: true},
                {name:"PROTOCOL",displayName: settingsCmp.i18n._("Protocol"), type: "checkgroup", values: [["TCP","TCP"],["UDP","UDP"],["any", settingsCmp.i18n._("any")]], visible: true},
                {name:"USERNAME",displayName: settingsCmp.i18n._("Username"), type: "editor", editor: Ext.create('Ung.UserEditorWindow',{}), visible: true},
                {name:"CLIENT_HOSTNAME",displayName: settingsCmp.i18n._("Client Hostname"), type: "text", visible: true},
                {name:"SERVER_HOSTNAME",displayName: settingsCmp.i18n._("Server Hostname"), type: "text", visible: false},
                {name:"CLIENT_IN_PENALTY_BOX",displayName: settingsCmp.i18n._("Client in Penalty Box"), type: "boolean", visible: true},
                {name:"SERVER_IN_PENALTY_BOX",displayName: settingsCmp.i18n._("Server in Penalty Box"), type: "boolean", visible: true},
                {name:"CLIENT_HAS_NO_QUOTA",displayName: settingsCmp.i18n._("Client has no Quota"), type: "boolean", visible: false},
                {name:"SERVER_HAS_NO_QUOTA",displayName: settingsCmp.i18n._("Server has no Quota"), type: "boolean", visible: false},
                {name:"CLIENT_QUOTA_EXCEEDED",displayName: settingsCmp.i18n._("Client has exceeded Quota"), type: "boolean", visible: true},
                {name:"SERVER_QUOTA_EXCEEDED",displayName: settingsCmp.i18n._("Server has exceeded Quota"), type: "boolean", visible: true},
                {name:"DIRECTORY_CONNECTOR_GROUP",displayName: settingsCmp.i18n._("Directory Connector: User in Group"), type: "editor", editor: Ext.create('Ung.GroupEditorWindow',{}), visible: true},
                {name:"HTTP_USER_AGENT",displayName: settingsCmp.i18n._("HTTP: Client User Agent"), type: "text", visible: true},
                {name:"HTTP_USER_AGENT_OS",displayName: settingsCmp.i18n._("HTTP: Client User OS"), type: "text", visible: true}
            ];
        }
    };

    Ext.define('Ung.Firewall', {
        extend:'Ung.NodeWin',
        panelRules: null,
        gridRules: null,
        gridEventLog: null,
        initComponent: function() {
            // builds the tabs
            this.buildRules();
            this.buildEventLog();
            // builds the tab panel with the tabs
            this.buildTabPanel([this.panelRules, this.gridEventLog]);
            this.callParent(arguments);
        },
        // Rules Panel
        buildRules: function() {
            this.panelRules = Ext.create('Ext.panel.Panel',{
                name: 'panelRules',
                helpSource: 'firewall_rules',
                parentId: this.getId(),
                title: this.i18n._('Rules'),
                layout: 'anchor',
                cls: 'ung-panel',
                items: [{
                    xtype: 'fieldset',
                    cls: 'description',
                    title: this.i18n._('Note'),
                    html: Ext.String.format(this.i18n._(" <b>Firewall</b> is a simple application designed to block and flag network traffic based on a set of rules. To learn more click on the <b>Help</b> button below.<br/> Routing and Port Forwarding functionality can be found elsewhere in Config->Networking."), rpc.companyName)
                },  this.gridRules= Ext.create('Ung.EditorGrid',{
                    anchor: '100% -80',
                    name: 'Rules',
                    settingsCmp: this,
                    paginated: false,
                    hasReorder: true,
                    addAtTop: false,
                    emptyRow: {
                        "ruleId": 0,
                        "enabled": true,
                        "block": false,
                        "flag": false,
                        "description": this.i18n._("[no description]"),
                        "javaClass": "com.untangle.node.firewall.FirewallRule"
                    },
                    title: this.i18n._("Rules"),
                    recordJavaClass: "com.untangle.node.firewall.FirewallRule",
                    dataProperty:'rules',
                    fields: [{
                        name: 'ruleId'
                    }, {
                        name: 'enabled'
                    }, {
                        name: 'block'
                    }, {
                        name: 'flag'
                    }, {
                        name: 'matchers'
                    },{
                        name: 'description'
                    }, {
                        name: 'javaClass'
                    }],
                    columns: [{
                        header: this.i18n._("Rule Id"),
                        width: 50,
                        dataIndex: 'ruleId',
                        renderer: function(value) {
                            if (value < 0) {
                                return i18n._("new");
                            } else {
                                return value;
                            }
                        }
                    }, {
                        xtype:'checkcolumn',
                        header: this.i18n._("Enable"),
                        dataIndex: 'enabled',
                        resizable: false,
                        width:55
                    }, {
                        header: this.i18n._("Description"),
                        width: 200,
                        dataIndex: 'description',
                        flex:1
                    }, {
                        xtype:'checkcolumn',
                        header: this.i18n._("Block"),
                        dataIndex: 'block',
                        resizable: false,
                        width:55
                    }, {
                        xtype:'checkcolumn',
                        header: this.i18n._("Flag"),
                        dataIndex: 'flag',
                        resizable: false,
                        width:55
                    }],
                    columnsDefaultSortable: false,
                    rowEditorInputLines: [{
                        xtype:'checkbox',
                        name: "Enable Rule",
                        dataIndex: "enabled",
                        fieldLabel: this.i18n._("Enable Rule")
                    }, {
                        xtype:'textfield',
                        name: "Description",
                        dataIndex: "description",
                        fieldLabel: this.i18n._("Description"),
                        width: 500
                    }, {
                        xtype:'fieldset',
                        title: this.i18n._("If all of the following conditions are met:"),
                        items:[{
                            xtype:'rulebuilder',
                            settingsCmp: this,
                            javaClass: "com.untangle.node.firewall.FirewallRuleMatcher",
                            dataIndex: "matchers",
                            matchers: Ung.FirewallUtil.getMatchers(this)
                        }]
                    }, {
                        xtype: 'fieldset',
                        cls:'description',
                        title: i18n._('Perform the following action(s):'),
                        border: false,
                        items:[{
                            xtype: "combo",
                            name: "actionType",
                            allowBlank: false,
                            dataIndex: "block",
                            fieldLabel: this.i18n._("Action Type"),
                            editable: false,
                            store: [[true, i18n._('Block')], [false, i18n._('Pass')]],
                            queryMode: 'local'
                        }, {
                            xtype:'checkbox',
                            name: "Flag",
                            dataIndex: "flag",
                            fieldLabel: this.i18n._("Flag")
                        }]
                    }]
                })]
            });
        },
        // Event Log
        buildEventLog: function() {
            this.gridEventLog = Ung.CustomEventLog.buildSessionEventLog (this, 'EventLog', i18n._('Event Log'), 
                    'firewall_event_log', 
                    ['time_stamp','username','c_client_addr','c_client_port','s_server_addr','s_server_port','firewall_blocked','firewall_flagged','firewall_rule_index'], 
                    this.getRpcNode().getEventQueries);
        },
        beforeSave: function(isApply, handler) {
            this.gridRules.getList(Ext.bind(function(saveList) {
                this.settings.rules = saveList;
                handler.call(this, isApply);
            }, this));
        }
    });
}
//@ sourceURL=firewall-settings.js

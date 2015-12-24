Ext.define('Webui.config.reportsViewer', {
    extend : 'Ung.StatusWin',
    helpSource : 'reports_viewer',
    displayName : 'Reports Viewer',
    initComponent : function() {
        this.breadcrumbs = [ {
            title : i18n._('Reports Viewer')
        } ];
        var treeNodes = [ {
            text : i18n._('Summary'),
            category : 'Summary',
            leaf : true,
            icon : '/skins/'+rpc.skinSettings.skinName+'/images/admin/icons/icon_summary.png'
        }, {
            text : i18n._('Host Viewer'),
            category : 'Host Viewer',
            leaf : true,
            icon : '/skins/'+rpc.skinSettings.skinName+'/images/admin/icons/icon_hosts.png'
        }, {
            text : i18n._("Configuration"),
            leaf : false,
            expanded : true,
            children : [ {
                text : i18n._('Network'),
                category : 'Network',
                leaf : true,
                icon : '/skins/'+rpc.skinSettings.skinName+'/images/admin/config/icon_config_network_17x17.png'
            }, {
                text : i18n._('Administration'),
                category : 'Administration',
                leaf : true,
                icon : '/skins/'+rpc.skinSettings.skinName+'/images/admin/config/icon_config_admin_17x17.png'
            }, {
                text : i18n._('System'),
                category : 'System',
                leaf : true,
                icon : '/skins/'+rpc.skinSettings.skinName+'/images/admin/config/icon_config_system_17x17.png'
            }, {
                text : i18n._("Shield"),
                category : "Shield",
                leaf : true,
                icon :'/skins/'+rpc.skinSettings.skinName+'/images/admin/apps/untangle-node-shield_17x17.png' 
            } ]
        }];

        this.items = {
            xtype : "panel",
            border : false,
            margin : '1 0 0 0',
            layout : "border",
            items : [ {
                xtype : 'treepanel',
                region : 'west',
                autoScroll : true,
                rootVisible : false,
                title : i18n._('Reports'),
                enableDrag : false,
                width : 200,
                minWidth : 65,
                maxWidth : 350,
                split : true,
                collapsible: true,
                collapsed: false,
                store : Ext.create('Ext.data.TreeStore', {
                    root : {
                        expanded : true,
                        children : treeNodes
                    }
                }),
                selModel : {
                    selType : 'rowmodel',
                    listeners : {
                        select : Ext.bind(function(rowModel, record, rowIndex, eOpts) {
                            this.panelReports.setConfig("icon", record.get("icon"));
                            this.panelReports.down('#panelEntries').setTitle(record.get("category"));
                            this.panelReports.setCategory(record.get("category"));
                        }, this)
                    }
                }
            }, this.panelReports = Ext.create('Ung.panel.Reports', {
                region : "center",
                header: false
            }) ]
        };
        this.callParent(arguments);
        
        var treepanel = this.down("treepanel"); 
        treepanel.getSelectionModel().select(0);
        rpc.reportsManager.getCurrentApplications(Ext.bind(function( result, exception ) {
            if(Ung.Util.handleException(exception)) return;
            var currentApplications = result.list;
            if (currentApplications) {
                var i, app, apps = [];
                for (i = 0; i < currentApplications.length; i++) {
                    app = currentApplications[i];
                    if(app.name != 'untangle-node-branding-manager' && app.name != 'untangle-node-live-support' ) {
                        apps.push({
                            text : app.displayName,
                            category : app.displayName,
                            leaf : true,
                            icon : '/skins/'+rpc.skinSettings.skinName+'/images/admin/apps/'+app.name+'_17x17.png'
                        });
                    }
                }
                if(apps.length > 0) {
                    treepanel.getStore().getRoot().appendChild({
                        text : i18n._("Applications"),
                        leaf : false,
                        expanded : true,
                        children : apps
                    });
                }
            }
        },this));
    },
    doSize : function() {
        this.maximize();
    },
    closeWindow : function() {
        this.hide();
        this.destroy();
    }
});
//# sourceURL=reportsViewer.js

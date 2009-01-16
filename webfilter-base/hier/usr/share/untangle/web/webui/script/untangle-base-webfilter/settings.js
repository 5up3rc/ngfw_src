if (!Ung.hasResource["Ung.BaseWebFilter"]) {
    Ung.hasResource["Ung.BaseWebFilter"] = true;
    Ung.NodeWin.registerClassName('untangle-base-webfilter', 'Ung.BaseWebFilter');

    Ung.BaseWebFilter = Ext.extend(Ung.NodeWin, {
        hasScanHTTPS: null,
        gridExceptions : null,
        gridEventLog : null,
        // called when the component is rendered
        initComponent : function() {
            // keep initial base settings
            this.initialBaseSettings = Ung.Util.clone(this.getBaseSettings());

            this.buildBlockLists();
            this.buildPassLists();
            this.buildEventLog();
            // builds the tab panel with the tabs
            this.buildTabPanel([this.panelBlockLists, this.panelPassLists, this.gridEventLog]);
            Ung.BaseWebFilter.superclass.initComponent.call(this);
        },
        // Block Lists Panel
        buildBlockLists : function() {
            this.panelBlockLists = new Ext.Panel({
                name : 'Block Lists',
                helpSource : 'block_lists',
                // private fields
                winBlacklistCategories : null,
                winBlockedUrls : null,
                winBlockedExtensions : null,
                winBlockedMimeTypes : null,
                parentId : this.getId(),

                title : this.i18n._('Block Lists'),
                layout : "form",
                cls: 'ung-panel',
                autoScroll : true,
                defaults : {
                    xtype : 'fieldset',
                    autoHeight : true,
                    buttonAlign : 'left'
                },
                items : [{
                    title : this.i18n._('Categories'),
                    buttons : [{
                        name : 'Categories manage list',
                        text : this.i18n._("manage list"),
                        handler : function() {
                            this.panelBlockLists.onManageBlacklistCategories();
                        }.createDelegate(this)
                    }]
                }, {
                    title : this.i18n._('Sites'),
                    buttons : [{
                        name : 'Sites manage list',
                        text : this.i18n._("manage list"),
                        handler : function() {
                            this.panelBlockLists.onManageBlockedUrls();
                        }.createDelegate(this)
                    }]
                }, {
                    title : this.i18n._('File Types'),
                    buttons : [{
                        name : 'File Types manage list',
                        text : this.i18n._("manage list"),
                        handler : function() {
                            this.panelBlockLists.onManageBlockedExtensions();
                        }.createDelegate(this)
                    }]
                }, {
                    title : this.i18n._('MIME Types'),
                    buttons : [{
                        name : 'MIME Types manage list',
                        text : this.i18n._("manage list"),
                        handler : function() {
                            this.panelBlockLists.onManageBlockedMimeTypes();
                        }.createDelegate(this)
                    }]
                }, {
                    labelWidth: 150,
                    items : [{
                        xtype : 'combo',
                        editable : false,
                        mode : 'local',
                        fieldLabel : this.i18n._('User Bypass'),
                        name : "User Bypass",
                        store : new Ext.data.SimpleStore({
                            fields : ['userWhitelistValue', 'userWhitelistName'],
                            data : [["NONE", this.i18n._("None")], ["USER_ONLY", this.i18n._("Temporary")],
                                    ["USER_AND_GLOBAL", this.i18n._("Permanent and Global")]]
                        }),
                        displayField : 'userWhitelistName',
                        valueField : 'userWhitelistValue',
                        value : this.getBaseSettings().userWhitelistMode,
                        triggerAction : 'all',
                        listClass : 'x-combo-list-small',
                        listeners : {
                            "change" : {
                                fn : function(elem, newValue) {
                                    this.getBaseSettings().userWhitelistMode = newValue;
                                }.createDelegate(this)
                            }
                        }
                    }]
                }],
                initComponent : function () {
            var settingsCmp=Ext.getCmp(this.parentId);
                    this.items.push({
              items: {
            xtype : 'checkbox',
            boxLabel : settingsCmp.i18n._('Block pages from IP only hosts'),
            hideLabel : true,
            name : 'Block IPHost',
            checked : settingsCmp.getBaseSettings().blockAllIpHosts,
            listeners : {
              "check" : {
                fn : function(elem, checked) {
                  this.getBaseSettings().blockAllIpHosts = checked;
                }.createDelegate(settingsCmp)
              }
            }
              }
            });
                    if(settingsCmp.hasScanHTTPS) {
                        this.items.push({
                            items: {
                                xtype : 'checkbox',
                                boxLabel : settingsCmp.i18n._('Scan HTTPS'),
                                hideLabel : true,
                                name : 'Scan HTTPS',
                                checked : settingsCmp.getBaseSettings().enableHttps,
                                listeners : {
                                    "check" : {
                                        fn : function(elem, checked) {
                                            this.getBaseSettings().enableHttps = checked;
                                        }.createDelegate(settingsCmp)
                                    }
                                }
                            }
                        });
                    }
                    Ext.Panel.prototype.initComponent.call(this);
                },

                onManageBlacklistCategories : function() {
                    if (!this.winBlacklistCategories) {
                        var settingsCmp = Ext.getCmp(this.parentId);
                        settingsCmp.buildBlacklistCategories();
                        this.winBlacklistCategories = new Ung.ManageListWindow({
                            breadcrumbs : [{
                                title : i18n._(rpc.currentPolicy.name),
                                action : function() {
                                    Ung.Window.cancelAction(
                                       this.gridBlacklistCategories.isDirty() || this.isDirty(),
                                       function() {
                                            this.panelBlockLists.winBlacklistCategories.closeWindow();
                                            this.closeWindow();
                                       }.createDelegate(this)
                                    );
                                }.createDelegate(settingsCmp)
                            }, {
                                title : settingsCmp.node.md.displayName,
                                action : function() {
                                    this.panelBlockLists.winBlacklistCategories.cancelAction();
                                }.createDelegate(settingsCmp)
                            }, {
                                title : settingsCmp.i18n._("Categories")
                            }],
                            grid : settingsCmp.gridBlacklistCategories
                        });
                    }
                    this.winBlacklistCategories.show();
                },
                onManageBlockedUrls : function() {
                    if (!this.winBlockedUrls) {
                        var settingsCmp = Ext.getCmp(this.parentId);
                        settingsCmp.buildBlockedUrls();
                        this.winBlockedUrls = new Ung.ManageListWindow({
                            breadcrumbs : [{
                                title : i18n._(rpc.currentPolicy.name),
                                action : function() {
                                    Ung.Window.cancelAction(
                                       this.gridBlockedUrls.isDirty() || this.isDirty(),
                                       function() {
                                            this.panelBlockLists.winBlockedUrls.closeWindow();
                                            this.closeWindow();
                                       }.createDelegate(this)
                                    )
                                }.createDelegate(settingsCmp)
                            }, {
                                title : settingsCmp.node.md.displayName,
                                action : function() {
                                    this.panelBlockLists.winBlockedUrls.cancelAction();
                                }.createDelegate(settingsCmp)
                            }, {
                                title : settingsCmp.i18n._("Sites")
                            }],
                            grid : settingsCmp.gridBlockedUrls
                        });
                    }
                    this.winBlockedUrls.show();
                },
                onManageBlockedExtensions : function() {
                    if (!this.winBlockedExtensions) {
                        var settingsCmp = Ext.getCmp(this.parentId);
                        settingsCmp.buildBlockedExtensions();
                        this.winBlockedExtensions = new Ung.ManageListWindow({
                            breadcrumbs : [{
                                title : i18n._(rpc.currentPolicy.name),
                                action : function() {
                                    Ung.Window.cancelAction(
                                       this.gridBlockedExtensions.isDirty() || this.isDirty(),
                                       function() {
                                            this.panelBlockLists.winBlockedExtensions.closeWindow();
                                            this.closeWindow();
                                       }.createDelegate(this)
                                    )
                                }.createDelegate(settingsCmp)
                            }, {
                                title : settingsCmp.node.md.displayName,
                                action : function() {
                                    this.panelBlockLists.winBlockedExtensions.cancelAction();
                                }.createDelegate(settingsCmp)
                            }, {
                                title : settingsCmp.i18n._("File Types")
                            }],
                            grid : settingsCmp.gridBlockedExtensions
                        });
                    }
                    this.winBlockedExtensions.show();
                },
                onManageBlockedMimeTypes : function() {
                    if (!this.winBlockedMimeTypes) {
                        var settingsCmp = Ext.getCmp(this.parentId);
                        settingsCmp.buildBlockedMimeTypes();
                        this.winBlockedMimeTypes = new Ung.ManageListWindow({
                            breadcrumbs : [{
                                title : i18n._(rpc.currentPolicy.name),
                                action : function() {
                                    Ung.Window.cancelAction(
                                       this.gridBlockedMimeTypes.isDirty() || this.isDirty(),
                                       function() {
                                            this.panelBlockLists.winBlockedMimeTypes.closeWindow();
                                            this.closeWindow();
                                       }.createDelegate(this)
                                    )
                                }.createDelegate(settingsCmp)
                            }, {
                                title : settingsCmp.node.md.displayName,
                                action : function() {
                                    this.panelBlockLists.winBlockedMimeTypes.cancelAction();
                                }.createDelegate(settingsCmp)
                            }, {
                                title : settingsCmp.i18n._("MIME Types")
                            }],
                            grid : settingsCmp.gridBlockedMimeTypes
                        });
                    }
                    this.winBlockedMimeTypes.show();
                },
                beforeDestroy : function() {
                    Ext.destroy(this.winBlacklistCategories, this.winBlockedUrls, this.winBlockedExtensions, this.winBlockedMimeTypes);
                    Ext.Panel.prototype.beforeDestroy.call(this);
                }
            });
        },
        // Block Categories
        buildBlacklistCategories : function() {
            var liveColumn = new Ext.grid.CheckColumn({
                header : this.i18n._("block"),
                dataIndex : 'block',
                fixed : true,
                changeRecord : function(record) {
                    Ext.grid.CheckColumn.prototype.changeRecord.call(this, record);
                    var blocked = record.get(this.dataIndex);
                    if (blocked) {
                        record.set('log', true);
                    }
                }
            });
            var logColumn = new Ext.grid.CheckColumn({
                header : this.i18n._("log"),
                dataIndex : 'log',
                fixed : true
            });

            this.gridBlacklistCategories = new Ung.EditorGrid({
                name : 'Categories',
                settingsCmp : this,
                totalRecords : this.getBaseSettings().blacklistCategoriesLength,
                hasAdd : false,
                hasDelete : false,
                title : this.i18n._("Categories"),
                recordJavaClass : "com.untangle.node.webfilter.BlacklistCategory",
                proxyRpcFn : this.getRpcNode().getBlacklistCategories,
                fields : [{
                    name : 'id'
                }, {
                    name : 'name',
                    type : 'string'
                }, {
                    name : 'displayName',
                    type : 'string',
                    convert : function(v) {
                        return this.i18n._(v)
                    }.createDelegate(this)
                }, {
                    name : 'block'
                }, {
                    name : 'log'
                }, {
                    name : 'description',
                    type : 'string',
                    convert : function(v) {
                        return this.i18n._(v)
                    }.createDelegate(this)
                }],
                columns : [{
                    id : 'displayName',
                    header : this.i18n._("category"),
                    width : 200,
                    dataIndex : 'displayName'
                }, liveColumn, logColumn, {
                    id : 'description',
                    header : this.i18n._("description"),
                    width : 200,
                    dataIndex : 'description',
                    editor : new Ext.form.TextField({
                        allowBlank : false
                    })
                }],
                sortField : 'displayName',
                columnsDefaultSortable : true,
                autoExpandColumn : 'description',
                plugins : [liveColumn, logColumn],
                rowEditorInputLines : [new Ext.form.TextField({
                    name : "Category",
                    dataIndex : "displayName",
                    fieldLabel : this.i18n._("Category"),
                    allowBlank : false,
                    width : 200,
                    disabled : true,
                    ctCls: "fixed-pos"
                }), new Ext.form.Checkbox({
                    name : "Block",
                    dataIndex : "block",
                    fieldLabel : this.i18n._("Block"),
                    listeners : {
                        "check" : {
                            fn : function(elem, checked) {
                                var rowEditor = this.gridBlacklistCategories.rowEditor;
                                if (checked) {
                                    rowEditor.inputLines[2].setValue(true);
                                }
                            }.createDelegate(this)
                        }
                    }
                }), new Ext.form.Checkbox({
                    name : "Log",
                    dataIndex : "log",
                    fieldLabel : this.i18n._("Log")
                }), new Ext.form.TextArea({
                    name : "Description",
                    dataIndex : "description",
                    fieldLabel : this.i18n._("Description"),
                    width : 200,
                    height : 60
                })]
            });
        },
        // Block Sites
        buildBlockedUrls : function() {
            var urlValidator = function(fieldValue) {
                if (fieldValue.indexOf("https://") == 0) {
                    return this.i18n._("\"URL\" specified cannot be blocked because it uses secure http (https)");
                }
                if (fieldValue.indexOf("http://") == 0) {
                    fieldValue = fieldValue.substr(7);
                }
                if (fieldValue.indexOf("www.") == 0) {
                    fieldValue = fieldValue.substr(4);
                }
                if (fieldValue.indexOf("/") == fieldValue.length - 1) {
                    fieldValue = fieldValue.substring(0, fieldValue.length - 1);
                }
                if (fieldValue.trim().length == 0) {
                    return this.i18n._("Invalid \"URL\" specified");
                }
                return true;
            }.createDelegate(this);
            var liveColumn = new Ext.grid.CheckColumn({
                header : this.i18n._("block"),
                dataIndex : 'live',
                fixed : true
            });
            var logColumn = new Ext.grid.CheckColumn({
                header : this.i18n._("log"),
                dataIndex : 'log',
                fixed : true
            });

            this.gridBlockedUrls = new Ung.EditorGrid({
                name : 'Sites',
                settingsCmp : this,
                totalRecords : this.getBaseSettings().blockedUrlsLength,
                emptyRow : {
                    "string" : this.i18n._("[no site]"),
                    "live" : true,
                    "log" : true,
                    "description" : this.i18n._("[no description]")
                },
                title : this.i18n._("Sites"),
                recordJavaClass : "com.untangle.uvm.node.StringRule",
                proxyRpcFn : this.getRpcNode().getBlockedUrls,
                fields : [{
                    name : 'id'
                }, {
                    name : 'string',
                    type : 'string'
                }, {
                    name : 'live'
                }, {
                    name : 'log'
                }, {
                    name : 'description',
                    type : 'string'
                }],
                columns : [{
                    id : 'string',
                    header : this.i18n._("site"),
                    width : 200,
                    dataIndex : 'string',
                    editor : new Ext.form.TextField({
                        allowBlank : false,
                        validator : urlValidator,
                        blankText : this.i18n._("Invalid \"URL\" specified")
                    })
                }, liveColumn, logColumn, {
                    id : 'description',
                    header : this.i18n._("description"),
                    width : 200,
                    dataIndex : 'description',
                    editor : new Ext.form.TextField({
                        allowBlank : false
                    })
                }],
                sortField : 'string',
                columnsDefaultSortable : true,
                autoExpandColumn : 'description',
                plugins : [liveColumn, logColumn],
                rowEditorInputLines : [new Ext.form.TextField({
                    name : "Site",
                    dataIndex : "string",
                    fieldLabel : this.i18n._("Site"),
                    allowBlank : false,
                    width : 200,
                    validator : urlValidator,
                    blankText : this.i18n._("Invalid \"URL\" specified")
                }), new Ext.form.Checkbox({
                    name : "Block",
                    dataIndex : "live",
                    fieldLabel : this.i18n._("Block")
                }), new Ext.form.Checkbox({
                    name : "Log",
                    dataIndex : "log",
                    fieldLabel : this.i18n._("Log")
                }), new Ext.form.TextArea({
                    name : "Description",
                    dataIndex : "description",
                    fieldLabel : this.i18n._("Description"),
                    width : 200,
                    height : 60
                })]
            });
        },
        // Block File Types
        buildBlockedExtensions : function() {
            var liveColumn = new Ext.grid.CheckColumn({
                header : this.i18n._("block"),
                dataIndex : 'live',
                fixed : true
            });
            var logColumn = new Ext.grid.CheckColumn({
                header : this.i18n._("log"),
                dataIndex : 'log',
                fixed : true
            });

            this.gridBlockedExtensions = new Ung.EditorGrid({
                name : 'File Types',
                settingsCmp : this,
                totalRecords : this.getBaseSettings().blockedExtensionsLength,
                emptyRow : {
                    "string" : "[no extension]",
                    "live" : true,
                    "log" : true,
                    "name" : this.i18n._("[no description]")
                },
                title : this.i18n._("File Types"),
                recordJavaClass : "com.untangle.uvm.node.StringRule",
                proxyRpcFn : this.getRpcNode().getBlockedExtensions,
                fields : [{
                    name : 'id'
                }, {
                    name : 'string',
                    type : 'string'
                }, {
                    name : 'live'
                }, {
                    name : 'log'
                }, {
                    name : 'name',
                    type : 'string'
                }],
                columns : [{
                    id : 'string',
                    header : this.i18n._("file type"),
                    width : 200,
                    dataIndex : 'string',
                    editor : new Ext.form.TextField({
                        allowBlank : false
                    })
                }, liveColumn, logColumn, {
                    id : 'name',
                    header : this.i18n._("description"),
                    width : 200,
                    dataIndex : 'name',
                    editor : new Ext.form.TextField({
                        allowBlank : false
                    })
                }],
                sortField : 'string',
                columnsDefaultSortable : true,
                autoExpandColumn : 'name',
                plugins : [liveColumn, logColumn],
                rowEditorInputLines : [new Ext.form.TextField({
                    name : "File Type",
                    dataIndex : "string",
                    fieldLabel : this.i18n._("File Type"),
                    allowBlank : false,
                    width : 200
                }), new Ext.form.Checkbox({
                    name : "Block",
                    dataIndex : "live",
                    fieldLabel : this.i18n._("Block")
                }), new Ext.form.Checkbox({
                    name : "Log",
                    dataIndex : "log",
                    fieldLabel : this.i18n._("Log")
                }), new Ext.form.TextArea({
                    name : "Description",
                    dataIndex : "name",
                    fieldLabel : this.i18n._("Description"),
                    width : 200,
                    height : 60
                })]
            });
        },
        // Block MIME Types
        buildBlockedMimeTypes : function() {
            var liveColumn = new Ext.grid.CheckColumn({
                header : this.i18n._("block"),
                dataIndex : 'live',
                fixed : true
            });
            var logColumn = new Ext.grid.CheckColumn({
                header : this.i18n._("log"),
                dataIndex : 'log',
                fixed : true
            });

            this.gridBlockedMimeTypes = new Ung.EditorGrid({
                name : 'MIME Types',
                settingsCmp : this,
                totalRecords : this.getBaseSettings().blockedMimeTypesLength,
                emptyRow : {
                    "mimeType" : this.i18n._("[no mime type]"),
                    "live" : true,
                    "log" : true,
                    "name" : this.i18n._("[no description]")
                },
                title : this.i18n._("MIME Types"),
                recordJavaClass : "com.untangle.uvm.node.MimeTypeRule",
                proxyRpcFn : this.getRpcNode().getBlockedMimeTypes,
                fields : [{
                    name : 'id'
                }, {
                    name : 'mimeType',
                    type : 'string'
                }, {
                    name : 'live'
                }, {
                    name : 'log'
                }, {
                    name : 'name',
                    type : 'string'
                }],
                columns : [{
                    id : 'mimeType',
                    header : this.i18n._("MIME type"),
                    width : 200,
                    dataIndex : 'mimeType',
                    editor : new Ext.form.TextField({
                        allowBlank : false
                    })
                }, liveColumn, logColumn, {
                    id : 'name',
                    header : this.i18n._("description"),
                    width : 200,
                    dataIndex : 'name',
                    editor : new Ext.form.TextField({
                        allowBlank : false
                    })
                }],
                sortField : 'mimeType',
                columnsDefaultSortable : true,
                autoExpandColumn : 'name',
                plugins : [liveColumn, logColumn],
                rowEditorInputLines : [new Ext.form.TextField({
                    name : "MIME Type",
                    dataIndex : "mimeType",
                    fieldLabel : this.i18n._("MIME Type"),
                    allowBlank : false,
                    width : 200
                }), new Ext.form.Checkbox({
                    name : "Block",
                    dataIndex : "live",
                    fieldLabel : this.i18n._("Block")
                }), new Ext.form.Checkbox({
                    name : "Log",
                    dataIndex : "log",
                    fieldLabel : this.i18n._("Log")
                }), new Ext.form.TextArea({
                    name : "Description",
                    dataIndex : "name",
                    fieldLabel : this.i18n._("Description"),
                    width : 200,
                    height : 60
                })]
            });
        },

        // Pass Lists Panel
        buildPassLists : function() {
            this.panelPassLists = new Ext.Panel({
                // private fields
                name : 'Pass Lists',
                helpSource : 'pass_lists',
                winPassedUrls : null,
                winPassedClients : null,
                parentId : this.getId(),
                autoScroll : true,
                title : this.i18n._('Pass Lists'),
                layout : "form",
                bodyStyle : 'padding:5px 5px 0px; 5px;',
                defaults : {
                    xtype : 'fieldset',
                    autoHeight : true,
                    buttonAlign : 'left'
                },
                items : [{
                    title : this.i18n._('Sites'),
                    buttons : [{
                        name : 'Sites manage list',
                        text : this.i18n._("manage list"),
                        handler : function() {
                            this.panelPassLists.onManagePassedUrls();
                        }.createDelegate(this)
                    }]
                }, {
                    title : this.i18n._('Client IP addresses'),
                    buttons : [{
                        name : 'Client IP addresses manage list',
                        text : this.i18n._("manage list"),
                        handler : function() {
                            this.panelPassLists.onManagePassedClients();
                        }.createDelegate(this)
                    }]
                }],

                onManagePassedUrls : function() {
                    if (!this.winPassedUrls) {
                        var settingsCmp = Ext.getCmp(this.parentId);
                        settingsCmp.buildPassedUrls();
                        this.winPassedUrls = new Ung.ManageListWindow({
                            breadcrumbs : [{
                                title : i18n._(rpc.currentPolicy.name),
                                action : function() {
                                    Ung.Window.cancelAction(
                                       this.gridPassedUrls.isDirty() || this.isDirty(),
                                       function() {
                                            this.panelPassLists.winPassedUrls.closeWindow();
                                            this.closeWindow();
                                       }.createDelegate(this)
                                    )
                                }.createDelegate(settingsCmp)
                            }, {
                                title : settingsCmp.node.md.displayName,
                                action : function() {
                                    this.panelPassLists.winPassedUrls.cancelAction();
                                }.createDelegate(settingsCmp)
                            }, {
                                title : settingsCmp.i18n._("Sites")
                            }],
                            grid : settingsCmp.gridPassedUrls
                        });
                    }
                    this.winPassedUrls.show();
                },
                onManagePassedClients : function() {
                    if (!this.winPassedClients) {
                        var settingsCmp = Ext.getCmp(this.parentId);
                        settingsCmp.buildPassedClients();
                        this.winPassedClients = new Ung.ManageListWindow({
                            breadcrumbs : [{
                                title : i18n._(rpc.currentPolicy.name),
                                action : function() {
                                    Ung.Window.cancelAction(
                                       this.gridPassedClients.isDirty() || this.isDirty(),
                                       function() {
                                            this.panelPassLists.winPassedClients.closeWindow();
                                            this.closeWindow();
                                       }.createDelegate(this)
                                    )
                                }.createDelegate(settingsCmp)
                            }, {
                                title : settingsCmp.node.md.displayName,
                                action : function() {
                                    this.panelPassLists.winPassedClients.cancelAction();
                                }.createDelegate(settingsCmp)
                            }, {
                                title : settingsCmp.i18n._("Client IP addresses")
                            }],
                            grid : settingsCmp.gridPassedClients
                        });
                    }
                    this.winPassedClients.show();
                },
                beforeDestroy : function() {
                    Ext.destroy(this.winPassedUrls, this.winPassedClients);
                    Ext.Panel.prototype.beforeDestroy.call(this);
                }
            });
        },
        // Passed Sites
        buildPassedUrls : function() {
            var urlValidator = function(fieldValue) {
                if (fieldValue.indexOf("https://") == 0) {
                    return this.i18n._("\"URL\" specified cannot be passed because it uses secure http (https)");
                }
                if (fieldValue.indexOf("http://") == 0) {
                    fieldValue = fieldValue.substr(7);
                }
                if (fieldValue.indexOf("www.") == 0) {
                    fieldValue = fieldValue.substr(4);
                }
                if (fieldValue.indexOf("/") == fieldValue.length - 1) {
                    fieldValue = fieldValue.substring(0, fieldValue.length - 1);
                }
                if (fieldValue.trim().length == 0) {
                    return this.i18n._("Invalid \"URL\" specified");
                }
                return true;
            }.createDelegate(this);

            var liveColumn = new Ext.grid.CheckColumn({
                header : this.i18n._("pass"),
                dataIndex : 'live',
                fixed : true
            });

            this.gridPassedUrls = new Ung.EditorGrid({
                name : 'Sites',
                settingsCmp : this,
                totalRecords : this.getBaseSettings().passedUrlsLength,
                emptyRow : {
                    "string" : this.i18n._("[no site]"),
                    "live" : true,
                    "description" : this.i18n._("[no description]")
                },
                title : this.i18n._("Sites"),
                recordJavaClass : "com.untangle.uvm.node.StringRule",
                proxyRpcFn : this.getRpcNode().getPassedUrls,
                fields : [{
                    name : 'id'
                }, {
                    name : 'string',
                    type : 'string'
                }, {
                    name : 'live'
                }, {
                    name : 'description',
                    type : 'string'
                }],
                columns : [{
                    id : 'string',
                    header : this.i18n._("site"),
                    width : 200,
                    dataIndex : 'string',
                    editor : new Ext.form.TextField({
                        allowBlank : false,
                        validator : urlValidator,
                        blankText : this.i18n._("Invalid \"URL\" specified")
                    })
                }, liveColumn, {
                    id : 'description',
                    header : this.i18n._("description"),
                    width : 200,
                    dataIndex : 'description',
                    editor : new Ext.form.TextField({
                        allowBlank : false
                    })
                }],
                sortField : 'string',
                columnsDefaultSortable : true,
                autoExpandColumn : 'description',
                plugins : [liveColumn],
                rowEditorInputLines : [new Ext.form.TextField({
                    name : "Site",
                    dataIndex : "string",
                    fieldLabel : this.i18n._("Site"),
                    allowBlank : false,
                    width : 200,
                    validator : urlValidator,
                    blankText : this.i18n._("Invalid \"URL\" specified")
                }), new Ext.form.Checkbox({
                    name : "Pass",
                    dataIndex : "live",
                    fieldLabel : this.i18n._("Pass")
                }), new Ext.form.TextArea({
                    name : "Description",
                    dataIndex : "description",
                    fieldLabel : this.i18n._("Description"),
                    width : 200,
                    height : 60
                })]
            });
        },
        // Passed IP Addresses
        buildPassedClients : function() {
            var liveColumn = new Ext.grid.CheckColumn({
                header : this.i18n._("pass"),
                dataIndex : 'live',
                fixed : true
            });

            this.gridPassedClients = new Ung.EditorGrid({
                name : 'Client IP addresses',
                settingsCmp : this,
                totalRecords : this.getBaseSettings().passedClientsLength,
                emptyRow : {
                    "ipMaddr" : "0.0.0.0/32",
                    "live" : true,
                    "description" : this.i18n._("[no description]")
                },
                title : this.i18n._("Client IP addresses"),
                recordJavaClass : "com.untangle.uvm.node.IPMaddrRule",
                proxyRpcFn : this.getRpcNode().getPassedClients,
                fields : [{
                    name : 'id'
                }, {
                    name : 'ipMaddr'
                }, {
                    name : 'live'
                }, {
                    name : 'description',
                    type : 'string'
                }],
                columns : [{
                    id : 'ipMaddr',
                    header : this.i18n._("IP address/range"),
                    width : 200,
                    dataIndex : 'ipMaddr',
                    editor : new Ext.form.TextField({
                        allowBlank : false
                    })
                }, liveColumn, {
                    id : 'description',
                    header : this.i18n._("description"),
                    width : 200,
                    dataIndex : 'description',
                    editor : new Ext.form.TextField({
                        allowBlank : false
                    })
                }],
                sortField : 'ipMaddr',
                columnsDefaultSortable : true,
                autoExpandColumn : 'description',
                plugins : [liveColumn],
                rowEditorInputLines : [new Ext.form.TextField({
                    name : "IP address/range",
                    dataIndex : "ipMaddr",
                    fieldLabel : this.i18n._("IP address/range"),
                    allowBlank : false,
                    width : 200
                }), new Ext.form.Checkbox({
                    name : "Pass",
                    dataIndex : "live",
                    fieldLabel : this.i18n._("Pass")
                }), new Ext.form.TextArea({
                    name : "Description",
                    dataIndex : "description",
                    fieldLabel : this.i18n._("Description"),
                    width : 200,
                    height : 60
                })]
            });
        },
        // Event Log
        buildEventLog : function() {
            var asClient = function(value) {
                return (value === null  || value.pipelineEndpoints === null) ? "" : value.pipelineEndpoints.CClientAddr + ":" + value.pipelineEndpoints.CClientPort;
            };
            var asServer = function(value) {
                return (value === null  || value.pipelineEndpoints === null) ? "" : value.pipelineEndpoints.SServerAddr + ":" + value.pipelineEndpoints.SServerPort;
            };
            var asRequest = function(value) {
                return (value === null  || value.url === null) ? "" : value.url;
            };

            this.gridEventLog = new Ung.GridEventLog({
                settingsCmp : this,
                fields : [{
                    name : 'timeStamp',
                    sortType : Ung.SortTypes.asTimestamp
                }, {
                    name : 'displayAction',
                    mapping : 'actionType',
                    type : 'string',
                    convert : function(value) {
                        switch (value) {
                            case 0 : // PASSED
                                return this.i18n._("pass");
                            default :
                            case 1 : // BLOCKED
                                return this.i18n._("block");
                        }
                    }.createDelegate(this)
                }, {
                    name : 'client',
                    mapping : 'requestLine',
                    sortType : asClient
                }, {
                    name : 'server',
                    mapping : 'requestLine',
                    sortType : asServer
                }, {
                    name : 'request',
                    mapping : 'requestLine',
                    sortType : asRequest
                }, {
                    name : 'reason',
                    type : 'string',
                    convert : function(value) {
                        switch (value) {
                            case 'BLOCK_CATEGORY' :
                                return this.i18n._("in Categories Block list");
                            case 'BLOCK_URL' :
                                return this.i18n._("in URLs Block list");
                            case 'BLOCK_EXTENSION' :
                                return this.i18n._("in File Extensions Block list");
                            case 'BLOCK_MIME' :
                                return this.i18n._("in MIME Types Block list");
                            case 'BLOCK_ALL' :
                                return this.i18n._("blocking all traffic");
                            case 'BLOCK_IP_HOST' :
                                return this.i18n._("hostname is an IP address");
                            case 'PASS_URL' :
                                return this.i18n._("in URLs Pass list");
                            case 'PASS_CLIENT' :
                                return this.i18n._("in Clients Pass list");
                            default :
                            case 'DEFAULT' :
                                return this.i18n._("no rule applied");
                        }
                    }.createDelegate(this)

                }],
                autoExpandColumn: 'request',
                columns : [{
                    header : this.i18n._("timestamp"),
                    width : 120,
                    sortable : true,
                    dataIndex : 'timeStamp',
                    renderer : function(value) {
                        return i18n.timestampFormat(value);
                    }
                }, {
                    header : this.i18n._("action"),
                    width : 100,
                    sortable : true,
                    dataIndex : 'displayAction'
                }, {
                    header : this.i18n._("client"),
                    width : 120,
                    sortable : true,
                    dataIndex : 'client',
                    renderer : asClient
                }, {
                    id: 'request',
                    header : this.i18n._("request"),
                    width : 200,
                    sortable : true,
                    dataIndex : 'request',
                    renderer : asRequest
                }, {
                    header : this.i18n._("reason for action"),
                    width : 150,
                    sortable : true,
                    dataIndex : 'reason'
                }, {
                    header : this.i18n._("server"),
                    width : 120,
                    sortable : true,
                    dataIndex : 'server',
                    renderer : asServer
                }]

            });
        },
        // validation functions
        validateClient : function() {
            // no need for validation here...just alter the URLs
            if (this.gridPassedUrls) {
                this.alterUrls(this.gridPassedUrls.getSaveList());
            }
            if (this.gridBlockedUrls) {
                this.alterUrls(this.gridBlockedUrls.getSaveList());
            }
            return true;
        },
        // private method
        alterUrls : function(list) {
            if (list != null) {
                // added
                for (var i = 0; i < list[0].list.length; i++) {
                    list[0].list[i]["string"] = this.alterUrl(list[0].list[i]["string"]);
                }
                // modified
                for (var i = 0; i < list[2].list.length; i++) {
                    list[2].list[i]["string"] = this.alterUrl(list[2].list[i]["string"]);
                }
            }
        },
        // private method
        alterUrl : function(value) {
            if (value.indexOf("http://") == 0) {
                value = value.substr(7);
            }
            if (value.indexOf("www.") == 0) {
                value = value.substr(4);
            }
            if (value.indexOf("/") == value.length - 1) {
                value = value.substring(0, value.length - 1);
            }
            return value.trim();
        },
        validateServer : function() {
            // ipMaddr list must be validated server side
            var passedClientsSaveList = this.gridPassedClients ? this.gridPassedClients.getSaveList() : null;
            if (passedClientsSaveList != null) {
                var ipMaddrList = [];
                // added
                for (var i = 0; i < passedClientsSaveList[0].list.length; i++) {
                    ipMaddrList.push(passedClientsSaveList[0].list[i]["ipMaddr"]);
                }
                // modified
                for (var i = 0; i < passedClientsSaveList[2].list.length; i++) {
                    ipMaddrList.push(passedClientsSaveList[2].list[i]["ipMaddr"]);
                }
                if (ipMaddrList.length > 0) {
                    try {
                        var result = null;
                        try {
                            result = this.getValidator().validate({
                                list : ipMaddrList,
                                "javaClass" : "java.util.ArrayList"
                            });
                        } catch (e) {
                            Ung.Util.rpcExHandler(e);
                        }
                        if (!result.valid) {
                            var errorMsg = "";
                            switch (result.errorCode) {
                                case 'INVALID_IPMADDR' :
                                    errorMsg = this.i18n._("Invalid subnet specified") + ": " + result.cause;
                                break;
                                default :
                                    errorMsg = this.i18n._(result.errorCode) + ": " + result.cause;
                            }

                            this.panelPassLists.onManagePassedClients();
                            this.gridPassedClients.focusFirstChangedDataByFieldValue("ipMaddr", result.cause);
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
        validate : function() {
            // reverse the order
            return this.validateServer() && this.validateClient();
        },
        // save function
        saveAction : function() {
            if (this.validate()) {
                Ext.MessageBox.wait(i18n._("Saving..."), i18n._("Please wait"));
                this.getRpcNode().updateAll(function(result, exception) {
                    Ext.MessageBox.hide();
                    if(Ung.Util.handleException(exception)) return;
                    // exit settings screen
                    this.closeWindow();
                }.createDelegate(this), this.getBaseSettings(), this.gridPassedClients ? this.gridPassedClients.getSaveList() : null,
                        this.gridPassedUrls ? this.gridPassedUrls.getSaveList() : null,
                        this.gridBlockedUrls ? this.gridBlockedUrls.getSaveList() : null,
                        this.gridBlockedMimeTypes ? this.gridBlockedMimeTypes.getSaveList() : null,
                        this.gridBlockedExtensions ? this.gridBlockedExtensions.getSaveList() : null,
                        this.gridBlacklistCategories ? this.gridBlacklistCategories.getSaveList() : null);
            }
        },
        isDirty : function() {
            return !Ung.Util.equals(this.getBaseSettings(), this.initialBaseSettings)
                || (this.gridPassedClients ? this.gridPassedClients.isDirty() : false)
                || (this.gridPassedUrls ? this.gridPassedUrls.isDirty() : false)
                || (this.gridBlockedUrls ? this.gridBlockedUrls.isDirty() : false)
                || (this.gridBlockedMimeTypes ? this.gridBlockedMimeTypes.isDirty() : false)
                || (this.gridBlockedExtensions ? this.gridBlockedExtensions.isDirty() : false)
                || (this.gridBlacklistCategories ? this.gridBlacklistCategories.isDirty() : false);
        }
    });
}

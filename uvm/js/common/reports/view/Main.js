Ext.define('Ung.view.reports.Main', {
    extend: 'Ext.panel.Panel',
    xtype: 'ung.reports',
    itemId: 'reports',

    layout: 'border',

    controller: 'reports',

    viewModel: {
        data: {
            context: null,
            fetching: false,
            selection: null,
            editing: false,
            paramsMap: {},
            condsQuery: ''
        }
    },

    dockedItems: [{
        xtype: 'toolbar',
        dock: 'top',
        style: {
            zIndex: 9997
        },

        padding: 5,
        plugins: 'responsive',
        items: [{
            xtype: 'breadcrumb',
            reference: 'breadcrumb',
            store: 'reportstree',
            useSplitButtons: false,
            listeners: {
                selectionchange: function (el, node) {
                    if (!node.get('url')) { return; }
                    if (node) {
                        if (node.get('url')) {
                            Ung.app.redirectTo('#reports/' + node.get('url'));
                        } else {
                            Ung.app.redirectTo('#reports');
                        }
                    }
                }
            }
        }],
        responsiveConfig: {
            wide: { hidden: true },
            tall: { hidden: false }
        }
    }, {
        xtype: 'toolbar',
        itemId: 'globalcond',
        docked: 'top',
        ui: 'footer',
        // height: 36,
        style: {
            background: '#e4e4e4'
        },
        items: [{
            xtype: 'component',
            html: '<strong>' + 'Global Conditions:'.t() + '</strong>'
        }, {
            xtype: 'container',
            layout: 'hbox'
        }, {
            xtype: 'button',
            // scale: 'medium',
            text: 'Add'.t(),
            iconCls: 'fa fa-plus-circle',
            hidden: true,
            bind: {
                hidden: '{paramsMap.conditions.length >= 3}'
            },
            menu: {
                plain: true,
                showSeparator: false,
                mouseLeaveDelay: 0,
                items: [{
                    xtype: 'radiogroup',
                    simpleValue: true,
                    reference: 'test',
                    publishes: 'value',
                    fieldLabel: '<strong>' + 'Choose column'.t() + '</strong>',
                    labelAlign: 'top',
                    columns: 1,
                    vertical: true,
                    items: [
                        { boxLabel: 'Username'.t(), name: 'rb', inputValue: 'username', bind: { disabled: '{disablecConds.username}' } },
                        { boxLabel: 'Protocol'.t(), name: 'rb', inputValue: 'protocol', bind: { disabled: '{disablecConds.protocol}' } },
                        { boxLabel: 'Hostname'.t(), name: 'rb', inputValue: 'hostname', bind: { disabled: '{disablecConds.hostname}' } },
                        { boxLabel: 'Client'.t(), name: 'rb', inputValue: 'c_client_addr', bind: { disabled: '{disablecConds.c_client_addr}' } },
                        { boxLabel: 'Server'.t(), name: 'rb', inputValue: 's_server_addr', bind: { disabled: '{disablecConds.s_server_addr}' } },
                        { boxLabel: 'Client Port', name: 'rb', inputValue: 'c_client_port', bind: { disabled: '{disablecConds.c_client_port}' } },
                        { boxLabel: 'Server Port'.t(), name: 'rb', inputValue: 's_server_port', bind: { disabled: '{disablecConds.s_server_port}' } },
                        { boxLabel: 'Policy Id'.t(), name: 'rb', inputValue: 'policy_id', bind: { disabled: '{disablecConds.policy_id}' } }
                    ]
                }, '-', {
                    xtype: 'textfield',
                    fieldLabel: '<strong>' + 'Set Value'.t() + '</strong>',
                    labelAlign: 'top',
                    margin: '5 5',
                    enableKeyEvents: true,
                    disabled: true,
                    bind: {
                        disabled: '{!test.value}'
                    },
                    listeners: {
                        keyup: function (el, e) {
                            if (e.keyCode === 13) {
                                el.up('menu').hide();
                            }
                        }
                    }
                }, '-', {
                    text: '<strong>' + 'More conditions ...'.t() + '</strong>',
                    handler: 'onMoreConditions'
                }],
                listeners: {
                    beforehide: 'onAddConditionHide'
                }
            }
        }]
    }],

    items: [{
        xtype: 'treepanel',
        reference: 'tree',
        width: 250,
        region: 'west',
        split: true,
        border: false,
        bodyBorder: false,
        // singleExpand: true,
        useArrows: true,
        rootVisible: false,
        plugins: 'responsive',
        store: 'reportstree',

        singleExpand: true,

        bind: {
            width: '{editing ? 0 : 250}'
        },

        viewConfig: {
            selectionModel: {
                type: 'treemodel',
                pruneRemoved: false
            },
            getRowClass: function(node) {
                if (node.get('disabled')) {
                    return 'disabled';
                }
            }
        },

        dockedItems: [{
            xtype: 'toolbar',
            dock: 'bottom',
            ui: 'footer',
            items: [{
                xtype: 'textfield',
                emptyText: 'Filter reports ...',
                enableKeyEvents: true,
                flex: 1,
                triggers: {
                    clear: {
                        cls: 'x-form-clear-trigger',
                        hidden: true,
                        handler: 'onTreeFilterClear'
                    }
                },
                listeners: {
                    change: 'filterTree',
                    buffer: 100
                }
            }, {
                xtype: 'button',
                iconCls: 'fa fa-plus-circle',
                text: 'Add/Import'.t(),
                hidden: true,
                bind: {
                    hidden: '{context !== "ADMIN"}'
                },
                menu: {
                    plain: true,
                    mouseLeaveDelay: 0,
                    items: [{
                        text: 'Create New'.t(),
                        iconCls: 'fa fa-plus fa-lg',
                        handler: 'newReport', // not working yet
                    }, {
                        text: 'Import'.t(),
                        iconCls: 'fa fa-download',
                        handler: 'newImport'
                    }]
                }
            }]
        }],

        columns: [{
            xtype: 'treecolumn',
            flex: 1,
            dataIndex: 'text',
            // scope: 'controller',
            renderer: 'treeNavNodeRenderer'
        }],

        listeners: {
            beforeselect: 'beforeSelectReport'
        },

        responsiveConfig: {
            wide: { hidden: false },
            tall: { hidden: true }
        }
    }, {
        region: 'center',
        itemId: 'cards',
        reference: 'cards',
        border: false,
        layout: 'card',
        cls: 'reports-all',
        defaults: {
            border: false,
            bodyBorder: false
        },
        items: [{
            itemId: 'category',
            layout: { type: 'vbox', align: 'middle' },
            items: [{
                xtype: 'component',
                padding: '20px 0',
                cls: 'charts-bar',
                html: '<i class="fa fa-area-chart fa-2x"></i>' +
                    '<i class="fa fa-line-chart fa-2x"></i>' +
                    '<i class="fa fa-pie-chart fa-2x"></i>' +
                    '<i class="fa fa-bar-chart fa-2x"></i>' +
                    '<i class="fa fa-list-ul fa-2x"></i>' +
                    '<i class="fa fa-align-left fa-2x"></i>'
            }, {
                xtype: 'container',
                cls: 'stats',
                layout: { type: 'hbox', pack: 'center' },
                defaults: {
                    xtype: 'component',
                    cls: 'stat'
                },
                items: [{
                    hidden: true,
                    bind: {
                        hidden: '{selection}',
                        html: '<h1>{stats.categories.total}</h1>categories <p><span>{stats.categories.app} apps</span></p>'
                    }
                }, {
                    hidden: true,
                    bind: {
                        hidden: '{!selection}',
                        html: '<img src="{selection.icon}"><br/> {selection.text}'
                    }
                }, {
                    bind: {
                        html: '<h1>{stats.reports.total}</h1>reports' +
                            '<p><span>{stats.reports.chart} charts</span><br/>' +
                            '<span>{stats.reports.event} event lists</span><br/>' +
                            '<span>{stats.reports.info} summaries</span><br/>' +
                            '<span>{stats.reports.custom} custom reports</span></p>'

                    }
                }]
            }, {
                xtype: 'component',
                cls: 'pls',
                html: 'select a report from a category',
                bind: {
                    html: 'select a report from {selection.text || "a category"}'
                }
            }, {
                xtype: 'button',
                iconCls: 'fa fa-external-link-square fa-lg',
                margin: 20,
                scale: 'medium',
                handler: 'exportCategoryReports',
                text: 'Export All Reports'.t(),
                hidden: true,
                bind: {
                    text: 'Export All'.t() + ' <strong>{selection.text}</strong> ' + 'Reports'.t(),
                    hidden: '{context !== "ADMIN"}'
                }
            }]
        }, {
            xtype: 'entry',
            itemId: 'report'
        }]
    }]
});

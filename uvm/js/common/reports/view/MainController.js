Ext.define('Ung.view.reports.MainController', {
    extend: 'Ext.app.ViewController',
    alias: 'controller.reports',

    control: {
        '#': { afterrender: 'onAfterRender', deactivate: 'resetView' }
    },

    listen: {
        global: {
            init: 'onInit'
        }
    },

    onAfterRender: function () {
        this.getView().setLoading(true);
    },

    onInit: function () {
        var me = this, vm = me.getViewModel(), path = '', node;

        // set the context (ADMIN or REPORTS)
        vm.set('context', Ung.app.context);

        me.getView().setLoading(false);

        me.buildTablesStore();
        // vm.bind('{hash}', function (hash) {
        //     if (!hash) { me.resetView(); return; }

        //     // on create new report reset and apply new entry
        //     if (hash === 'create') {
        //         me.getViewModel().set('selection', null);
        //         me.resetView();
        //         me.getView().down('entry').getController().reset();
        //         me.getView().down('entry').getViewModel().set({
        //             entry: null,
        //             eEntry: Ext.create('Ung.model.Report')
        //         });
        //         me.lookup('cards').setActiveItem('report');
        //         return;
        //     }

        //     console.log(window.location.hash);

        //     if (Ung.app.context === 'REPORTS') {
        //         path = '/reports/' + window.location.hash.replace('#', '');
        //         node = Ext.getStore('reportstree').findNode('url', window.location.hash.replace('#', ''));
        //     } else {
        //         path = window.location.hash.replace('#', '');
        //         node = Ext.getStore('reportstree').findNode('url', window.location.hash.replace('#reports/', ''));
        //     }

        //     // selected node icon/text for category stats
        //     vm.set('selection', { icon: node.get('icon'), text: node.get('text') });

        //     // breadcrumb selection
        //     me.lookup('breadcrumb').setSelection(node);

        //     // tree selection
        //     me.lookup('tree').collapseAll();
        //     me.lookup('tree').selectPath(path, 'slug', '/', Ext.emptyFn, me);

        //     me.showNode(node); // shows the selected report or category stats
        // });
        vm.bind('{conds}', function (conds) {
            // console.log(conds);
        });



        vm.bind('{paramsMap}', function (params) {

            if (!params) {
                return;
            };

            vm.set('globalConditions', params.conditions);
            var tb = me.getView().down('#globalcond > container'), disabledConds = {};

            tb.removeAll();
            Ext.Array.each(params.conditions, function (cond, idx) {
                disabledConds[cond.column] = true;
                tb.add({
                    xtype: 'segmentedbutton',
                    allowToggle: false,
                    margin: '0 5',
                    items: [{
                        text: TableConfig.getColumnHumanReadableName(cond.column) + ' <span style="font-weight: bold; margin: 0 3px;">' + cond.operator + '</span> ' + cond.value,
                        menu: {
                            plain: true,
                            showSeparator: false,
                            mouseLeaveDelay: 0,
                            condition: cond,
                            items: [{
                                xtype: 'textfield',
                                enableKeyEvents: true,
                                margin: 5,
                                value: cond.value,
                                listeners: {
                                    keyup: function (el, e) {
                                        if (e.keyCode === 13) {
                                            el.up('menu').hide();
                                        }
                                    }
                                }
                            }, '-', {
                                xtype: 'radiogroup',
                                simpleValue: true,
                                publishes: 'value',
                                // fieldLabel: '<strong>' + 'Operator'.t() + '</strong>',
                                // labelAlign: 'top',
                                columns: 1,
                                vertical: true,
                                value: cond.operator,
                                items: [
                                    { boxLabel: 'equals [=]'.t(), name: 'rb', inputValue: '=' },
                                    { boxLabel: 'not equals [!=]'.t(), name: 'rb', inputValue: '!=' },
                                    { boxLabel: 'greater than [>]'.t(), name: 'rb', inputValue: '>' },
                                    { boxLabel: 'less than [<]'.t(), name: 'rb', inputValue: '<' },
                                    { boxLabel: 'greater or equal [>=]'.t(), name: 'rb', inputValue: '>=' },
                                    { boxLabel: 'less or equal [<=]', name: 'rb', inputValue: '<=' },
                                    { boxLabel: 'like'.t(), name: 'rb', inputValue: 'like' },
                                    { boxLabel: 'not like'.t(), name: 'rb', inputValue: 'not like' },
                                    { boxLabel: 'is'.t(), name: 'rb', inputValue: 'is' },
                                    { boxLabel: 'is not'.t(), name: 'rb', inputValue: 'is not' },
                                    { boxLabel: 'in'.t(), name: 'rb', inputValue: 'in' },
                                    { boxLabel: 'not in'.t(), name: 'rb', inputValue: 'not in' }
                                ],
                                listeners: {
                                    change: function (rg, val) {
                                        cond.operator = val;
                                        // rg.setValue()
                                        me.redirect();
                                    }
                                }
                            }
                            // , '-', {
                            //     xtype: 'checkbox',
                            //     margin: 5,
                            //     boxLabel: 'Auto Format Value'.t(),
                            //     value: cond.autoFormatValue
                            // }
                            ],
                            listeners: {
                                beforehide: function (el) {
                                    el.condition.value = el.down('textfield').getValue();
                                },
                                hide: function () {
                                    me.redirect();
                                }
                            }
                        }
                    }, {
                        iconCls: 'fa fa-times',
                        // scale: 'medium',
                        condIndex: idx,
                        handler: function (el) {
                            Ext.Array.removeAt(params.conditions, el.condIndex);
                            me.redirect();
                        }
                    }]
                })
            });

            vm.set('disablecConds', disabledConds);



            var path = '/reports/';

            if (params.route.cat) {
                path += params.route.cat + '/'
            }

            if (params.route.rep) {
                path += params.route.rep
            }


            // node = Ext.getStore('reportstree').findNode('url', 'cat=' + params.route.cat + '&rep=' + params.route.rep);
            // vm.set('selection', { icon: node.get('icon'), text: node.get('text') })
            // me.lookup('tree').collapseAll();
            me.lookup('tree').selectPath(path, 'slug', '/', Ext.emptyFn, me);
            // me.showNode(node); // shows the selected report or category stats
        });

        vm.bind('{condsQuery}', function (conditions) {

            var node = Ext.getStore('reportstree').getRoot(), conds = [];


            Ext.Array.each(vm.get('paramsMap.conditions'), function (c) {
                conds.push(c.column);
            });

            node.cascade(function (n) {
                if (n.isLeaf()) {
                    // console.log(n.get('table'));
                    if (conds.length > 0) {
                        n.set('disabled', !TableConfig.containsColumns(n.get('table'), conds))
                    } else {
                        n.set('disabled', false);
                    }
                }
            });
        });



        vm.bind('{fetching}', function (val) {
            if (!val) { Ext.MessageBox.hide(); } // hide any loading message box
        });
    },

    onAddConditionHide: function (menu) {
        var me = this, vm = me.getViewModel();

        var col = menu.down('#add_column').getValue(),
            op = menu.down('#add_operator').getValue(),
            val = menu.down('#add_value').getValue();

        menu.down('#add_column').reset();
        menu.down('#add_operator').setValue('=');
        menu.down('#add_value').setValue('');


        if (!col || !op || !val) {
            return;
        }

        var conds = vm.get('paramsMap.conditions');
        conds.push({
            column: col,
            operator: op,
            value: val,
            autoFormatValue: true,
            javaClass: 'com.untangle.app.reports.SqlCondition'
        });
        me.redirect();
    },

    redirect: function () {
        var me = this, vm = me.getViewModel();
        var route = '#reports?';
        var params = vm.get('paramsMap');

        if (params.route.cat) {
            route += 'cat=' + params.route.cat;
        }

        if (params.route.rep) {
            route += '&rep=' + params.route.rep;
        }

        Ext.Array.each(params.conditions, function (cond) {
            route += '&' + cond.column + ( cond.operator === '=' ? '=' : encodeURIComponent('.' + cond.operator + '.') ) + cond.value
        });

        Ung.app.redirectTo(route);

    },

    onMoreConditions: function () {
        var me = this, vm = me.getViewModel();
        var tablesComboStore = [], columnsComboStore = [];

        Ext.Object.each(TableConfig.tableConfig, function (table, val) {
            tablesComboStore.push([table, table]);
        });

        Ext.Array.each(TableConfig.tableConfig['sessions'].columns, function (column) {
            columnsComboStore.push([column.dataIndex, column.header + ' [' + column.dataIndex + ']']);
        });

        var dialog = me.getView().add({
            xtype: 'window',
            modal: true,
            draggable: false,
            resizable: false,
            width: 800,
            height: 400,
            title: 'Global Conditions'.t(),
            layout: 'fit',
            items: [{
                xtype: 'grid',
                sortableColumns: false,
                enableColumnHide: false,
                // forceFit: true,
                dockedItems: [{
                    xtype: 'toolbar',
                    dock: 'top',
                    ui: 'footer',
                    items: [{
                        xtype: 'combo',
                        width: 250,
                        fieldLabel: 'Select Table'.t(),
                        labelAlign: 'top',
                        queryMode: 'local',
                        // valueField: 'value',
                        // displayField: 'name',
                        store: tablesComboStore,
                        emptyText: 'Select Table'.t(),
                        allowBlank: false,
                        editable: false,
                        value: 'sessions',
                        listeners: {
                            change: function (el, table) {
                                var columns = TableConfig.tableConfig[table].columns, store = [];
                                Ext.Array.each(columns, function (column) {
                                    store.push([column.dataIndex, column.header + ' [' + column.dataIndex + ']']);
                                });
                                el.nextNode().setStore(store);
                                el.nextNode().setValue(store[0]);
                            }
                        }
                    }, {
                        xtype: 'combo',
                        fieldLabel: 'Select Column'.t(),
                        emptyText: 'Select Column'.t(),
                        flex: 1,
                        labelAlign: 'top',
                        queryMode: 'local',
                        editable: false,
                        allowBlank: false,
                        store: columnsComboStore,
                        value: columnsComboStore[0]
                    }, {
                        text: 'Add Column'.t(),
                        scale: 'large',
                        handler: function (el) {
                            var tbar = el.up('toolbar'),
                                col = tbar.down('combo').nextNode().getValue(),
                                store = el.up('grid').getStore();

                            if (store.find('column', col) >= 0) {
                                Ext.Msg.alert('Info ...', 'Column <strong>' + col + '</strong> is already added!');
                                return;
                            }

                            el.up('grid').getStore().add({
                                column: col,
                                value: '',
                                operator: '=',
                                autoFormatValue: true,
                                javaClass: 'com.untangle.app.reports.SqlCondition'
                            });
                        }
                    }]
                }],
                bind: {
                    store: {
                        data: '{paramsMap.conditions}'
                    }
                },
                border: false,
                columns: [{
                    text: 'Column'.t(),
                    dataIndex: 'column',
                    flex: 1,
                    renderer: function (val) {
                        return TableConfig.getColumnHumanReadableName(val) +  ' [' + val + ']';
                    }
                }, {
                    xtype: 'widgetcolumn',
                    text: 'Operator'.t(),
                    width: 200,
                    dataIndex: 'operator',
                    widget: {
                        xtype: 'combo',
                        editable: false,
                        queryMode: 'local',
                        bind: '{record.operator}',
                        store: [
                            ['=', 'equals [=]'.t()],
                            ['!=', 'not equals [!=]'.t()],
                            ['>', 'greater than [>]'.t()],
                            ['<', 'less than [<]'.t()],
                            ['>=', 'greater or equal [>=]'.t()],
                            ['<=', 'less or equal [<=]'.t()],
                            ['like', 'like'.t()],
                            ['not like', 'not like'.t()],
                            ['is', 'is'.t()],
                            ['is not', 'is not'.t()],
                            ['in', 'in'.t()],
                            ['not in', 'not in'.t()]
                        ]
                    }
                }, {
                    xtype: 'widgetcolumn',
                    text: 'Value'.t(),
                    width: 200,
                    dataIndex: 'value',
                    widget: {
                        xtype: 'textfield',
                        bind: '{record.value}'
                    }
                }, {
                    xtype: 'actioncolumn',
                    width: 40,
                    align: 'center',
                    resizable: false,
                    tdCls: 'action-cell',
                    iconCls: 'fa fa-trash-o',
                    menuDisabled: true,
                    hideable: false,
                    handler: function (view, rowIndex, colIndex, item, e, record) {
                        record.drop();
                    }
                }]
            }],
            buttons: [{
                text: 'Cancel'.t(),
                iconCls: 'fa fa-ban',
                handler: function (el) {
                    el.up('window').hide();
                }
            }, {
                text: 'Apply'.t(),
                iconCls: 'fa fa-check',
                handler: function (el) {
                    var win = el.up('window'), store = win.down('grid').getStore();
                    vm.set('paramsMap.conditions', Ext.Array.pluck(store.getRange(), 'data'));
                    win.hide();
                    me.redirect();
                }
            }]
        });

        dialog.show();
    },




    buildTablesStore: function () {
        if (!rpc.reportsManager) { return; }
        var me = this, vm = me.getViewModel();
        Rpc.asyncData('rpc.reportsManager.getTables').then(function (result) {
            vm.set('tables', result); // used in advanced report settings table name
        });
    },

    // check if data is fetching and cancel selection if true
    beforeSelectReport: function (el, node) {
        var me = this, vm = me.getViewModel();
        if (node.get('disabled')) {
            return false;
        }
        if (vm.get('fetching')) {
            Ext.MessageBox.wait('Data is fetching...'.t(), 'Please wait'.t(), { text: '' });
            return false;
        }
        if (Ung.app.context === 'REPORTS') {
            Ung.app.redirectTo('#' + node.get('url'));
        } else {
            Ung.app.redirectTo('#reports?' + node.get('url') + vm.get('condsQuery'));
        }
        me.showNode(node);
    },

    showNode: function (node) {
        var me = this, record;
        if (node.isLeaf()) {
            // report node
            record = Ext.getStore('reports').findRecord('url', node.get('url'), 0, false, true, true);
            if (record) {
                me.getView().down('entry').getViewModel().set({
                    entry: record
                });
            }
            me.lookup('cards').setActiveItem('report');
        } else {
            me.lookup('cards').setActiveItem('category');
            me.buildStats(node);
            node.expand();
        }
    },


    /**
     * the tree item renderer used after filtering tree
     */
    treeNavNodeRenderer: function(value, meta, record) {
        // if (!record.isLeaf()) {
        //     meta.tdCls = 'x-tree-category';
        // }
        // if (!record.get('readOnly') && record.get('uniqueId')) {
        //     meta.tdCls = 'x-tree-custom-report';
        // }
        return this.rendererRegExp ? value.replace(this.rendererRegExp, '<span style="font-weight: bold; background: #EEE; color: #000; border-bottom: 1px #000 solid;">$1</span>') : value;
    },

    /**
     * filters reports tree
     */
    filterTree: function (field, value) {
        var me = this, tree = me.lookup('tree');
        me.rendererRegExp = new RegExp('(' + value + ')', 'gi');

        if (!value) {
            tree.getStore().clearFilter();
            tree.collapseAll();
            field.getTrigger('clear').hide();
            return;
        }

        tree.getStore().getFilters().replaceAll({
            property: 'text',
            value: new RegExp(Ext.String.escape(value), 'i')
        });
        tree.expandAll();
        field.getTrigger('clear').show();
    },

    onTreeFilterClear: function () {
        this.lookup('tree').down('textfield').setValue();
    },

    /**
     * resets the view to an initial state
     */
    resetView: function () {
        var me = this, tree = me.lookup('tree'), breadcrumb = me.lookup('breadcrumb');
        tree.collapseAll();
        tree.getSelectionModel().deselectAll();
        tree.getStore().clearFilter();
        tree.down('textfield').setValue('');

        breadcrumb.setSelection('root');

        if (me.getViewModel().get('hash') !== 'create') {
            me.buildStats();
            me.lookup('cards').setActiveItem('category');
            me.getViewModel().set('selection', null);
            me.getViewModel().set('hash', null);
        }
    },

    /**
     * builds statistics for categories
     */
    buildStats: function (node) {
        var me = this, vm = me.getViewModel(),
            stats = {
                set: false,
                reports: {
                    total: 0,
                    custom: 0,
                    chart: 0,
                    event: 0,
                    info: 0
                },
                categories: {
                    total: 0,
                    app: 0
                }
            };

        if (!node) { node = Ext.getStore('reportstree').getRoot(); }

        node.cascade(function (n) {
            if (n.isRoot()) { return; }
            if (n.isLeaf()) {
                stats.reports.total += 1;
                if (!n.get('readOnly')) { stats.reports.custom += 1; }
                switch(n.get('type')) {
                case 'TIME_GRAPH':
                case 'TIME_GRAPH_DYNAMIC':
                case 'PIE_GRAPH':
                    stats.reports.chart += 1; break;
                case 'EVENT_LIST':
                    stats.reports.event += 1; break;
                case 'TEXT':
                    stats.reports.info += 1; break;
                }
            } else {
                stats.categories.total += 1;
                if (n.get('type') === 'app') {
                    stats.categories.app += 1;
                }
            }
        });
        vm.set('stats', stats);
    },

    // on new report just redirect to proper route
    newReport: function () {
        Ung.app.redirectTo('#reports/create');
    },

    newImport: function () {
        var me = this;
        var dialog = me.getView().add({
            xtype: 'importdialog'
        });
        dialog.show();
    },


    exportCategoryReports: function () {
        var me = this, vm = me.getViewModel(), reportsArr = [], category, reports;

        if (vm.get('selection')) {
            category = vm.get('selection.text'); // selected category
            reports = Ext.getStore('reports').query('category', category, false, true, true);
        } else {
            // export all
            reports = Ext.getStore('reports').getData();
        }

        Ext.Array.each(reports.items, function (report) {
            var rep = report.getData();
            // remove extra custom fields
            delete rep._id;
            delete rep.localizedTitle;
            delete rep.localizedDescription;
            delete rep.slug;
            delete rep.categorySlug;
            delete rep.url;
            delete rep.icon;
            reportsArr.push(rep);
        });

        Ext.MessageBox.wait('Exporting Settings...'.t(), 'Please wait'.t());
        var exportForm = document.getElementById('exportGridSettings');
        exportForm.gridName.value = 'AllReports'.t() + (category ? '_' + category.replace(/ /g, '_') : ''); // used in exported file name
        exportForm.gridData.value = Ext.encode(reportsArr);
        exportForm.submit();
        Ext.MessageBox.hide();
    }


});

Ext.define('Ung.cmp.AppRemove', {
    extend: 'Ext.form.FieldSet',
    alias: 'widget.appremove',

    title: '<i class="fa fa-trash fa-red"></i> ' + 'Remove'.t(),

    padding: 10,
    margin: '20 0',
    cls: 'app-section',
    style: {
        borderColor: 'red'
    },

    controller: {
        onRemove: function () {
            var vm = this.getViewModel(),
                mainView = this.getView().up('#configCard'),
                // settingsView = this.getView();
                message = Ext.String.format('{0} will be uninstalled from this policy.'.t(), vm.get('appName')) + '<br/>' +
                'All of its settings will be lost.'.t() + '\n' + '<br/>' + '<br/>' +
                'Would you like to continue?'.t();

            Ext.Msg.confirm('Warning:'.t(), message, function(btn) {
                if (btn === 'yes') {
                    // var nodeItem = settingsView.up('#main').down('#apps').down('#' + vm.get('nodeInstance.nodeName'));
                    //nodeItem.setDisabled(true);
                    // nodeItem.addCls('remove');
                    // Ung.app.redirectTo('#apps/' + vm.get('policyId'));
                    mainView.setLoading(true);
                    Rpc.asyncData('rpc.nodeManager.destroy', vm.get('instance.id'))
                        .then(function (result) {
                            Rpc.asyncData('rpc.nodeManager.getAppsViews')
                                .then(function (policies) {
                                    Ext.getStore('policies').loadData(policies);

                                    // todo: check when reports uninstalled
                                    // if (nodeItem.node.name === 'untangle-node-reports') {
                                    //     delete rpc.reportsManager;
                                    //     vm.getParent().set('reportsInstalled', false);
                                    //     vm.getParent().set('reportsRunning', false);
                                    //     vm.getParent().notify();
                                    //     Ext.GlobalEvents.fireEvent('reportsinstall');
                                    // }

                                });

                            if (rpc.reportsManager) {
                                Rpc.asyncData('rpc.reportsManager.getUnavailableApplicationsMap')
                                    .then(function (unavailApps) {
                                        Ext.getStore('unavailableApps').loadRawData(unavailApps.map);
                                    });
                            }

                            vm.set('instance.targetState', null);

                            // remove card
                            if (Ung.app.getMainView().down('#configCard')) {
                                Ung.app.getMainView().remove('configCard');
                            }

                            Ung.app.redirectTo('#apps/1');

                            // todo: fire global event
                            // Ext.GlobalEvents.fireEvent('nodeinstall', 'remove', nodeItem.node);
                        }, function (ex) {
                            Util.exceptionToast(ex);
                        });
                }
            });
        }
    },

    items: [{
        xtype: 'button',
        text: 'Remove'.t(),
        iconCls: 'fa fa-minus-circle fa-red',
        handler: 'onRemove'
    },]
});

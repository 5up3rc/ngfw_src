if (!Ung.hasResource["Ung.Reporting"]) {
    Ung.hasResource["Ung.Reporting"] = true;
    Ung.NodeWin.registerClassName('untangle-node-reporting', 'Ung.Reporting');

    Ung.Reporting = Ext.extend(Ung.NodeWin, {
        panelStatus: null,
        panelGeneration: null,
        gridRecipients: null,
        gridIpMap: null,
        initComponent: function(container, position) {
            // builds the 3 tabs
            this.buildStatus();
            this.buildGeneration();
            this.buildIpMap();
            // builds the tab panel with the tabs
            this.buildTabPanel([this.panelStatus, this.panelGeneration, this.gridIpMap]);
            this.tabs.activate(this.panelStatus);
            Ung.Reporting.superclass.initComponent.call(this);
        },
        getReportingSettings: function(forceReload) {
            if (forceReload || this.rpc.reportingSettings === undefined) {
                try {
                    this.rpc.reportingSettings = this.getRpcNode().getReportingSettings();
                } catch (e) {
                    Ung.Util.rpcExHandler(e);
                }
            }
            return this.rpc.reportingSettings;
        },
        // get mail settings
        getMailSettings: function(forceReload) {
            if (forceReload || this.rpc.mailSettings === undefined) {
                try {
                    this.rpc.mailSettings = rpc.adminManager.getMailSettings();
                } catch (e) {
                    Ung.Util.rpcExHandler(e);
                }
            }
            return this.rpc.mailSettings;
        },
        getAdminSettings : function(forceReload) {
            if (forceReload || this.rpc.adminSettings === undefined) {
                try {
                    this.rpc.adminSettings = rpc.adminManager.getAdminSettings();
                } catch (e) {
                    Ung.Util.rpcExHandler(e);
                }
                
            }
            return this.rpc.adminSettings;
        },
        // Status Panel
        buildStatus: function() {
            this.panelStatus = new Ext.Panel({ 
                title: this.i18n._('Status'),
                name: 'Status',
                helpSource: 'status',
                layout: "form",
                autoScroll: true,
                cls: 'ung-panel',
                items: [{ 
                    title: this.i18n._('Status'),
                    xtype: 'fieldset',
                    autoHeight: true,
                    items: [{ 
                        buttonAlign: 'center',
                        footer: false,
                        border: false,
                        buttons: [{ 
                            xtype: 'button',
                            text: this.i18n._('View Reports'),
                            name: 'View Reports',
                            iconCls: 'action-icon',
                            handler: function() {
                                var viewReportsUrl = "../reports/";
                                var breadcrumbs = [{ 
                                    title: i18n._(rpc.currentPolicy.name),
                                    action: function() {
                                        main.iframeWin.closeActionFn();
                                        this.cancelAction();
                                    }.createDelegate(this)
                                }, { 
                                    title: this.node.md.displayName,
                                    action: function() {
                                        main.iframeWin.closeActionFn();
                                    }.createDelegate(this)
                                }, {
                                    title: this.i18n._('View Reports') 
                                }];
                                window.open(viewReportsUrl);
                            }.createDelegate(this)
                        }]
                    }]
                }]
            });
        },
        // Generation panel
        buildGeneration: function() {
            var storeData = this.buildReportingUsersData();

            var fieldID = "" + Math.round( Math.random() * 1000000 );

            // email reports is a check column
            var onlineReports = new Ext.grid.CheckColumn({
                header : this.i18n._("Online Reports"),
                dataIndex : "onlineReports",
                width : 100,
                fixed : true
            });
            
            // online reports is a check column
            var emailReports = new Ext.grid.CheckColumn({
                header : this.i18n._("Email Reports"),
                dataIndex : "emailReports",
                width : 100,
                fixed : true
            });

            // Change the password for a user.
            var changePasswordColumn = new Ext.grid.IconColumn({
                header : this.i18n._("change password"),
                width : 130,
                fixed : true,
                iconClass : 'icon-edit-row',
                handle : function(record, index)
                {
                    // populate row editor
                    this.grid.rowEditorChangePassword.populate(record);
                    this.grid.rowEditorChangePassword.show();
                }
            });

            this.panelGeneration = new Ext.Panel({
                // private fields
                name: 'Generation',
                helpSource: 'generation',
                parentId: this.getId(),
                title: this.i18n._('Generation'),
                layout: "anchor",
                cls: 'ung-panel',
                autoScroll: true,
                defaults: {
                    anchor: "98%",
                    xtype: 'fieldset',
                    autoHeight: true
                },
                items: [{
                    title: this.i18n._('Email'),
                    layout:'column',
                    height: 350,
                    items: [ this.gridRecipients = new Ung.EditorGrid({
                        width : 710,
                        name: 'Recipients',
                        title: this.i18n._("Recipients"),
                        hasEdit: false,
                        settingsCmp: this,
                        paginated: false,
                        height: 300,
                        plugins : [emailReports,onlineReports,changePasswordColumn],
                        emptyRow: {
                            emailAddress : "reportrecipient@example.com",
                            emailReports : true,
                            onlineReports : true,
                            clearPassword : null,
                            user : null
                        },
                        autoExpandColumn: "emailAddress",
                        data: storeData,
                        dataRoot: null,
                        autoGenerateId: true,
                        fields: [{
                            name: "emailAddress"
                        },{
                            name: "emailReports"
                        },{
                            name: "onlineReports"
                        },{
                            name: "clearPassword"
                        },{
                            name: "user"
                        }],
                        sortField: "emailAddress",
                        columnsDefaultSortable: true,
                        columns: [{
                            id: "emailAddress",
                            header: this.i18n._("email address"),
                            dataIndex: "emailAddress",
                            width : 200,
                            editor: new Ext.form.TextField({
                                vtype: "email",
                                allowBlank: false,
                                blankText: this.i18n._("The email address cannot be blank.")
                            })
                        }, emailReports, onlineReports, changePasswordColumn],
                        rowEditorInputLines : [new Ext.form.TextField({
                            dataIndex : "emailAddress",
                            fieldLabel : this.i18n._("Email Address (username)"),
                            allowBlank : false,
                            blankText : this.i18n._("The email address name cannot be blank."),
                            width : 200
                        }), new Ext.form.Checkbox({
                            dataIndex : "emailReports",
                            fieldLabel : this.i18n._("Email Reports"),
                            width : 200
                        }), new Ext.form.Checkbox({
                            dataIndex : "onlineReports",
                            fieldLabel : this.i18n._("Online Reports"),
                            width : 200
                        }), new Ext.form.TextField({
                            inputType: "password",
                            name : "Password",
                            dataIndex : "clearPassword",
                            id : "add_reporting_user_password_" + fieldID,
                            fieldLabel : this.i18n._("Password"),
                            boxLabel : this.i18n._("(required for 'Online Reports')"),
                            width : 200,
                            minLength : 3,
                            minLengthText : String.format(this.i18n._("The password is shorter than the minimum {0} characters."), 3)
                        }), new Ung.form.TextField({
                            inputType: "password",
                            name : "Confirm Password",
                            dataIndex : "clearPassword",
                            vtype: "password",
                            initialPassField: "add_reporting_user_password_" + fieldID,
                            fieldLabel : this.i18n._("Confirm Password"),
                            width : 200
                        })]
                    })]
                },{
                    title: this.i18n._("Data Retention"),
                    labelWidth: 150,
                    items: [{
                        border: false,
                        cls: 'description',
                        html: this.i18n._("Limit Data Retention to a number of days. The smaller the number the lower the disk space requirements and resource usage during report generation.")
                    },{ 
                        xtype : 'numberfield',
                        fieldLabel : this.i18n._('Limit Data Retention'),
                        name : 'Limit Data Retention',
                        id: 'reporting_daysToKeep',
                        value : this.getReportingSettings().daysToKeep,
                        width: 25,
                        allowDecimals: false,
                        allowNegative: false,
                        minValue: 1,
                        maxValue: 30,
                        listeners : {
                            "change" : {
                                fn : function(elem, newValue) {
                                    this.getReportingSettings().daysToKeep = newValue;
                                }.createDelegate(this)
                            }
                        }
                    }]
                }]
            });

            /* Create the row editor for updating the password */
            this.gridRecipients.rowEditorChangePassword = new Ung.RowEditorWindow({
                grid : this.gridRecipients,
                inputLines : [new Ext.form.TextField({
                    inputType: "password",
                    name : "Password",
                    dataIndex : "clearPassword",
                    id : "edit_reporting_user_password_"  + fieldID,
                    fieldLabel : this.i18n._("Password"),
                    width : 200,
                    minLength : 3,
                    minLengthText : String.format(this.i18n._("The password is shorter than the minimum {0} characters."), 3)
                }), new Ext.form.TextField({
                    inputType: "password",
                    name : "Confirm Password",
                    dataIndex : "clearPassword",
                    vtype: "password",
                    initialPassField: "edit_reporting_user_password_" + fieldID,
                    fieldLabel : this.i18n._("Confirm Password"),
                    width : 200
                })]
            });
            
            this.gridRecipients.rowEditorChangePassword.render("containter");
        },
        // IP Map grid
        buildIpMap: function() {
            this.gridIpMap = new Ung.EditorGrid({
                settingsCmp: this,
                name: 'Name Map',
                helpSource: 'ip_addresses',
                title: this.i18n._("Name Map"),
                emptyRow: {
                    "ipMaddr": "0.0.0.0/32",
                    "name": this.i18n._("[no name]"),
                    "description": this.i18n._("[no description]")
                },
                // the column is autoexpanded if the grid width permits
                autoExpandColumn: 'name',
                recordJavaClass: "com.untangle.uvm.node.IPMaddrRule",

                data: this.getReportingSettings().networkDirectory.entries,
                dataRoot: 'list',

                // the list of fields
                fields: [{
                    name: 'id'
                }, {
                    name: 'ipMaddr'
                }, {
                    name: 'name'
                }, {
                    name: 'description'
                }],
                // the list of columns for the column model
                columns: [{
                    id: 'ipMaddr',
                    header: this.i18n._("Name Map"),
                    width: 200,
                    dataIndex: 'ipMaddr',
                    editor: new Ext.form.TextField({})
                }, {
                    id: 'name',
                    header: this.i18n._("name"),
                    width: 200,
                    dataIndex: 'name',
                    editor: new Ext.form.TextField({})
                }],
                columnsDefaultSortable: true,
                // the row input lines used by the row editor window
                rowEditorInputLines: [new Ext.form.TextField({
                    name: "Subnet",
                    dataIndex: "ipMaddr",
                    fieldLabel: this.i18n._("Name Map"),
                    allowBlank: false,
                    width: 200
                }), new Ext.form.TextField({
                    name: "Name",
                    dataIndex: "name",
                    fieldLabel: this.i18n._("Name"),
                    allowBlank: false,
                    width: 200
                })]
            });
        },
        // validation
        validateServer: function() {
            // ipMaddr list must be validated server side
            var ipMapList = this.gridIpMap.getSaveList();
            var ipMaddrList = [];
            var i;

            // added
            for ( i = 0; i < ipMapList[0].list.length; i++) {
                ipMaddrList.push(ipMapList[0].list[i]["ipMaddr"]);
            }
            // modified
            for ( i = 0; i < ipMapList[2].list.length; i++) {
                ipMaddrList.push(ipMapList[2].list[i]["ipMaddr"]);
            }
            if (ipMaddrList.length > 0) {
                try {
                    var result=null;
                    try {
                        result = this.getValidator().validate({
                            list: ipMaddrList,
                            "javaClass": "java.util.ArrayList"
                        });
                    } catch (e) {
                        Ung.Util.rpcExHandler(e);
                    }
                    if (!result.valid) {
                        var errorMsg = "";
                        switch (result.errorCode) {
                        case 'INVALID_IPMADDR' :
                            errorMsg = this.i18n._("Invalid \"IP address\" specified") + ": " + result.cause;
                            break;
                        default :
                            errorMsg = this.i18n._(result.errorCode) + ": " + result.cause;
                        }

                        this.tabs.activate(this.gridIpMap);
                        this.gridIpMap.focusFirstChangedDataByFieldValue("ipMaddr", result.cause);
                        Ext.MessageBox.alert(this.i18n._("Validation failed"), errorMsg);
                        return false;
                    }
                } catch (e) {
                    var message = ( e == null ) ? "Unknown" : e.message;
                    if (message == "Unknown") {
                        message = i18n._("Please Try Again");
                    }
                    Ext.MessageBox.alert(i18n._("Failed"), message);
                    return false;
                }
            }

            return true;
        },
        applyAction : function()
        {
            this.commitSettings(this.reloadSettings.createDelegate(this));
        },
        reloadSettings : function()
        {
            this.getMailSettings(true);
            this.getReportingSettings(true);
            this.getAdminSettings(true);

            this.gridRecipients.clearChangedData();
            this.gridRecipients.store.loadData( this.buildReportingUsersData());
            Ext.getCmp("reporting_daysToKeep").setValue( this.getReportingSettings().daysToKeep );
            
            this.gridIpMap.clearChangedData();
            this.gridIpMap.store.loadData( this.getReportingSettings().networkDirectory.entries );

            Ext.MessageBox.hide();
        },
        saveAction : function()
        {
            this.commitSettings(this.completeSaveAction.createDelegate(this));
        },
        completeSaveAction : function()
        {
            Ext.MessageBox.hide();
            this.closeWindow();
        },
        // save function
        commitSettings : function(callback)
        {
            if (this.validate()) {
                this.saveSemaphore = 3;
                Ext.MessageBox.wait(i18n._("Saving..."), i18n._("Please wait"));
                if(!this.panelGeneration.rendered) {
                    var activeTab=this.tabs.getActiveTab();
                    this.tabs.activate(this.panelGeneration);
                    this.tabs.activate(activeTab);
                }

                // set Ip Map list
                this.getReportingSettings().networkDirectory.entries.list = this.gridIpMap.getFullSaveList();

                // save email recipients
                var gridRecipientsValues = this.gridRecipients.getFullSaveList();
                var adminSettings = this.getAdminSettings();
                var users = adminSettings.users.set, recipientsList = [], reportingUsers = [], user = null;

                for(var i=0; i<gridRecipientsValues.length; i++) {
                    var recipient = gridRecipientsValues[i];
                    reportingUsers.push(recipient.emailAddress);
                    if ( recipient.emailReports == true ) {
                        recipientsList.push(recipient.emailAddress);
                    }

                    /* If a user already exists, reuse it. */
                    if (( recipient.user != null ) && ( users[recipient.user] != null )) {
                        user = users[recipient.user];
                        user.hasReportsAccess = recipient.onlineReports;
                        user.login = recipient.emailAddress;
                        user.keepUser = true;
                        if ( recipient.clearPassword != null ) {
                            user.clearPassword = recipient.clearPassword;
                        }
                    /* Otherwise, create a user iff onlineReports is set or the password is set */
                    } else if ( recipient.onlineReports || recipient.clearPassword ) {
                        user = {
                            "login" : recipient.emailAddress,
                            "name" : this.i18n._("[reports only user]"),
                            "hasWriteAccess" : false,
                            "hasReportsAccess" : recipient.onlineReports,
                            "email" : recipient.emailAddress,
                            "clearPassword" : recipient.clearPassword,
                            "javaClass" : "com.untangle.uvm.security.User",
                            keepUser : true
                        };

                        /* Append the new user */
                        users[Math.round( Math.random() * 1000000 )] = user;
                    }
                }

                /* Delete all of the reporting only users that have not been updated. */
                users = {};
                
                var c  = 1;
                for ( var id in adminSettings.users.set ) {
                    user = adminSettings.users.set[id];
                    c++;
                    if ( user == null ) {
                        continue;
                    }
                    if ( user.hasWriteAccess || user.keepUser ) {
                        delete user.keepUser;
                        /* Encode all of the strings for safety." */
                        users[c] = Ext.decode( Ext.encode( user ));
                    }
                }
                adminSettings.users.set = users;
                
                this.getMailSettings().reportEmail = recipientsList.join(",");
                this.getReportingSettings().reportingUsers = reportingUsers.join(",");

                this.getRpcNode().setReportingSettings(function(result, exception) {
                    this.afterSave(exception, callback);
                }.createDelegate(this), this.getReportingSettings());

                // do the save
                rpc.adminManager.setMailSettings(function(result, exception) {
                    this.afterSave(exception, callback);
                }.createDelegate(this), this.getMailSettings());
                
                rpc.adminManager.setAdminSettings(function(result, exception) {
                    this.afterSave(exception, callback);
                }.createDelegate(this), this.getAdminSettings());
            }
        },
        afterSave: function(exception, callback)
        {
            if(Ung.Util.handleException(exception)) {
                return;
            }

            this.saveSemaphore--;
            if (this.saveSemaphore == 0) {
                callback();
            }
        },
        isDirty: function() {
            if(this.panelGeneration.rendered) {
                var cmpIds = [ 'reporting_daysToKeep'];
                for (var i = 0; i < cmpIds.length; i++) {
                    if (Ext.getCmp(cmpIds[i]).isDirty()){
                        return true;
                    }
                }
                if (this.gridRecipients.isDirty()){
                    return true;
                }
            }
            return this.gridIpMap.isDirty();
        },

        buildReportingUsersData : function()
        {
            var storeData = [];
            var reportEmail = this.getMailSettings().reportEmail || "";
            var adminUsers = this.getAdminSettings().users.set;
            var reportingUsers = this.getReportingSettings().reportingUsers || "", reportingUsersSet = {};
            
            /* Convert the two comma separated lists to sets. */
            var temp = {}, values, c;
            
            values = reportEmail.split(",");
            for ( c = 0 ; c < values.length ; c++ ) {
                temp[values[c].trim()] = true;
            }
            reportEmail = temp;
            
            values = reportingUsers.split(",");
            
            for ( c = 0 ; c < values.length ; c++ ) {
                values[c] = values[c].trim();
            }
            reportingUsers = values;

            for( c=0 ; c < reportingUsers.length; c++) {
                var email = reportingUsers[c];
                if ( email.length == 0 ) {
                    continue;
                }
                user = this.findAdminUser( adminUsers, email );
                storeData.push({
                    user : user,
                    emailReports : reportEmail[email] != null,
                    onlineReports : user != null && adminUsers[user].hasReportsAccess,
                    clearPassword : null,
                    emailAddress : email
                });
            }
            
            return storeData;
        },
        
        findAdminUser : function( adminUsers, emailAddress )
        {
            var id;
            for ( id in adminUsers ) {
                if ( adminUsers[id].login == emailAddress ) {
                    return id;
                }
            }

            /* Use null, new users are created at save time. */
            return null;
        }
    });
}

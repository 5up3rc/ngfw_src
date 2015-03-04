Ext.define("Webui.config.administration.SkinManager", {
    constructor: function( config ) {
        /* List of stores to be refreshed, dynamically generated. */
        this.refreshList = [];
        this.i18n = config.i18n;
    },
    addRefreshableStore: function( store ) {
        this.refreshList.push( store );
    },
    uploadSkin: function( cmp, form ) {
        form.submit({
            parentId: cmp.getId(),
            waitMsg: this.i18n._('Please wait while your skin is uploaded...'),
            success: Ext.bind(this.uploadSkinSuccess, this ),
            failure: Ext.bind(this.uploadSkinFailure, this )
        });
    },
    uploadSkinSuccess: function( form, action ) {
        this.storeSemaphore = this.refreshList.length;

        var handler = Ext.bind(function() {
            this.storeSemaphore--;
            if (this.storeSemaphore == 0) {
                Ext.MessageBox.alert( this.i18n._("Succeeded"), this.i18n._("Upload Skin Succeeded"));
                var field = form.findField( "upload_skin_textfield" );
                if ( field != null ) field.reset();
            }
        }, this);

        for ( var c = 0 ; c < this.storeSemaphore ; c++ ) this.refreshList[c].load({callback:handler});
    },
    uploadSkinFailure: function( form, action ) {
        var cmp = Ext.getCmp(action.parentId);
        var errorMsg = cmp.i18n._("Upload Skin Failed");
        if (action.result && action.result.msg) {
            switch (action.result.msg) {
            case 'Invalid Skin':
                errorMsg = cmp.i18n._("Invalid Skin");
                break;
            case 'The default skin can not be overwritten':
                errorMsg = cmp.i18n._("The default skin can not be overwritten");
                break;
            case 'Error creating skin folder':
                errorMsg = cmp.i18n._("Error creating skin folder");
                break;
            default:
                errorMsg = cmp.i18n._("Upload Skin Failed");
            }
        }
        Ext.MessageBox.alert(cmp.i18n._("Failed"), errorMsg);
    }
});

Ext.define("Webui.config.administrationNew", {
    extend: "Ung.ConfigWin",
    panelAdmin: null,
    panelPublicAddress: null,
    panelCertificates: null,
    certGeneratorWindow: null,
    panelSnmp: null,
    panelSkins: null,
    uploadedCustomLogo: false,
    initComponent: function() {
        this.breadcrumbs = [{
            title: i18n._("Configuration"),
            action: Ext.bind(function() {
                this.cancelAction();
            }, this)
        }, {
            title: i18n._('Administration')
        }];
        this.skinManager = Ext.create('Webui.config.administration.SkinManager', {'i18n': i18n});
        this.initialSkin = this.getSkinSettings().skinName;
        this.buildAdmin();
        this.buildPublicAddress();
        this.buildCertificates();
        this.buildSnmp();
        this.buildSkins();

        // builds the tab panel with the tabs
        var adminTabs = [this.panelAdmin, this.panelPublicAddress, this.panelCertificates, this.panelSnmp, this.panelSkins];
        this.buildTabPanel(adminTabs);
        this.tabs.setActiveTab(this.panelAdmin);
        this.callParent(arguments);
    },
    // get base settings object
    getSkinSettings: function(forceReload) {
        if (forceReload || this.rpc.skinSettings === undefined) {
            try {
                this.rpc.skinSettings = rpc.skinManager.getSettings();
            } catch (e) {
                Ung.Util.rpcExHandler(e);
            }
        }
        return this.rpc.skinSettings;
    },
    // get admin settings
    getAdminSettings: function(forceReload) {
        if (forceReload || this.rpc.adminSettings === undefined) {
            try {
                this.rpc.adminSettings = rpc.adminManager.getSettings();
            } catch (e) {
                Ung.Util.rpcExHandler(e);
            }

        }
        return this.rpc.adminSettings;
    },
    // get system settings
    getSystemSettings: function(forceReload) {
        if (forceReload || this.rpc.systemSettings === undefined) {
            try {
                this.rpc.systemSettings = rpc.systemManager.getSettings();
            } catch (e) {
                Ung.Util.rpcExHandler(e);
            }
        }
        return this.rpc.systemSettings;
    },
    // get Current Server CertInfo
    getCertificateInformation: function(forceReload) {
        if (forceReload || this.rpc.currentServerCertInfo === undefined) {
            try {
                this.rpc.currentServerCertInfo = main.getCertificateManager().getCertificateInformation();
            } catch (e) {
                Ung.Util.rpcExHandler(e);
            }

        }
        return this.rpc.currentServerCertInfo;
    },
    // get hostname
    getHostname: function(forceReload) {
        if ( forceReload || this.rpc.hostname === undefined || this.rpc.domainName === undefined ) {
            try {
                this.rpc.hostname = rpc.networkManager.getNetworkSettings()['hostName'];
                this.rpc.domainName = rpc.networkManager.getNetworkSettings()['domainName'];
            } catch (e) {
                Ung.Util.rpcExHandler(e);
            }

        }
        if ( this.rpc.domainName !== null && this.rpc.domainName !== "" )
            return this.rpc.hostname + "." + this.rpc.domainName;
        else
            return this.rpc.hostname;
    },

    buildAdmin: function() {
        // keep initial system and address settings
        this.initialSystemSettings = Ung.Util.clone(this.getSystemSettings());

        var changePasswordColumn = Ext.create("Ung.grid.EditColumn",{
            header: this.i18n._("change password"),
            width: 130,
            iconClass: 'icon-edit-row',
            handler: function(view, rowIndex, colIndex) {
                // populate row editor
                var rec = view.getStore().getAt(rowIndex);
                this.grid.rowEditorChangePass.populate(rec);
                this.grid.rowEditorChangePass.show();
            }
        });
        var thisReporting = this;
        var passwordValidator = function (fieldValue) {
          //validate password match
            var panel = this.up("panel");
            var pwd = panel.down('textfield[name="password"]');
            var confirmPwd = panel.down('textfield[name="confirmPassword"]');
            if(pwd.getValue() != confirmPwd.getValue()) {
                pwd.markInvalid();
                return thisReporting.i18n._('Passwords do not match');
            }
            pwd.clearInvalid();
            confirmPwd.clearInvalid();
            return true;
        };
        this.gridAdminAccounts=Ext.create('Ung.EditorGrid', {
            flex: 1,
            settingsCmp: this,
            title: this.i18n._("Admin Accounts"),
            bodyStyle: 'padding-bottom:30px;',
            autoScroll: true,
            hasEdit: false,
            name: 'gridAdminAccounts',
            recordJavaClass: "com.untangle.uvm.AdminUserSettings",
            emptyRow: {
                "username": "",
                "description": "",
                "emailAddress": "",
                "password": null,
                "passwordHashBase64": null
            },
            data: this.getAdminSettings().users.list,
            paginated: false,
            // the list of fields; we need all as we get/set all records once
            fields: [{
                name: 'username'
            }, {
                name: 'description'
            }, {
                name: 'emailAddress'
            }, {
                name: 'password'
            }, {
                name: 'passwordHashBase64'
            }],
            // the list of columns for the column model
            columns: [{
                header: this.i18n._("Username"),
                width: 200,
                dataIndex: 'username',
                field:{
                    xtype:'textfield',
                    allowBlank: false,
                    emptyText: this.i18n._("[enter username]"),
                    blankText: this.i18n._("The username cannot be blank.")
                }
            }, {
                header: this.i18n._("Description"),
                width: 200,
                dataIndex: 'description',
                flex: 1,
                editor:{
                    xtype:'textfield',
                    emptyText: this.i18n._("[no description]")
                }
            },{
                header: this.i18n._("Email"),
                width: 200,
                dataIndex: 'emailAddress',
                editor: {
                    xtype:'textfield',
                    emptyText: this.i18n._("[no email]"),
                    vtype: 'email'
                }
            }, changePasswordColumn],
            sortField: 'username',
            columnsDefaultSortable: true,
            plugins: [changePasswordColumn],
            // the row input lines used by the row editor window
            rowEditorInputLines: [{
                xtype: "textfield",
                name: "Username",
                dataIndex: "username",
                fieldLabel: this.i18n._("Username"),
                emptyText: this.i18n._("[enter username]"),
                allowBlank: false,
                blankText: this.i18n._("The username cannot be blank."),
                width: 400
            }, {
                xtype: "textfield",
                name: "Description",
                dataIndex: "description",
                fieldLabel: this.i18n._("Description"),
                emptyText: this.i18n._("[no description]"),
                width: 400
            },{
                xtype: "textfield",
                name: "Email",
                dataIndex: "emailAddress",
                fieldLabel: this.i18n._("Email"),
                emptyText: this.i18n._("[no email]"),
                vtype: 'email',
                width: 400
            },{
                xtype: "textfield",
                inputType: 'password',
                name: "password",
                dataIndex: "password",
                fieldLabel: this.i18n._("Password"),
                width: 400,
                minLength: 3,
                minLengthText: Ext.String.format(this.i18n._("The password is shorter than the minimum {0} characters."), 3)
            },{
                xtype: "textfield",
                inputType: 'password',
                name: "confirmPassword",
                dataIndex: "password",
                fieldLabel: this.i18n._("Confirm Password"),
                width: 400
            }]
        });

        this.gridAdminAccounts.rowEditorChangePass = Ext.create("Ung.RowEditorWindow",{
            grid: this.gridAdminAccounts,
            inputLines: [{
                xtype: "textfield",
                inputType: 'password',
                name: "password",
                dataIndex: "password",
                fieldLabel: this.i18n._("Password"),
                width: 400,
                minLength: 3,
                minLengthText: Ext.String.format(this.i18n._("The password is shorter than the minimum {0} characters."), 3),
                validator: passwordValidator
            }, {
                xtype: "textfield",
                inputType: 'password',
                name: "confirmPassword",
                dataIndex: "password",
                fieldLabel: this.i18n._("Confirm Password"),
                width: 400,
                validator: passwordValidator
            }]
        });

        this.gridAdminAccounts.subCmps.push(this.gridAdminAccounts.rowEditorChangePass);

        this.panelAdmin = Ext.create('Ext.panel.Panel',{
            name: 'panelAdmin',
            helpSource: 'administration_admin',
            // private fields
            parentId: this.getId(),
            title: this.i18n._('Admin'),
            layout: { type: 'vbox', align: 'stretch' },
            cls: 'ung-panel',
            items: [
                this.gridAdminAccounts, {
                    xtype: 'checkbox',
                    fieldLabel: this.i18n._('Allow HTTP Administration'),
                    labelWidth: 200,
                    style: "margin-top: 10px",
                    checked: this.getSystemSettings().httpAdministrationAllowed,
                    listeners: {
                        "change": {
                            fn: Ext.bind(function(elem, newValue) {
                                this.getSystemSettings().httpAdministrationAllowed = newValue;
                            }, this)
                        }
                    }
                }, {
                    xtype:'fieldset',
                    title: this.i18n._('Note:'),
                    items: [{
                        xtype: 'label',
                        html: this.i18n._('HTTP is open on non-WANs (internal interfaces) for blockpages and other services.') + "<br/>" +
                            this.i18n._('This settings only controls the availability of <b>administration</b> via HTTP.')
                    }]
                }]
        });
    },

    buildPublicAddress: function() {
        this.panelPublicAddress = Ext.create('Ext.panel.Panel',{
            name: 'panelPublicAddress',
            helpSource: 'administration_public_address',
            // private fields
            parentId: this.getId(),
            title: this.i18n._('Public Address'),
            cls: 'ung-panel',
            autoScroll: true,
            items: {
                xtype: 'fieldset',
                items: [{
                    cls: 'description',
                    html: Ext.String.format(this.i18n._('The Public Address is the address/URL that provides a public location for the {0} Server. This address will be used in emails sent by the {0} Server to link back to services hosted on the {0} Server such as Quarantine Digests and OpenVPN Client emails.'), rpc.companyName),
                    bodyStyle: 'padding-bottom:10px;',
                    border: false
                },{
                    xtype: 'radio',
                    boxLabel: this.i18n._('Use IP address from External interface (default)'),
                    hideLabel: true,
                    name: 'publicUrl',
                    checked: this.getSystemSettings().publicUrlMethod == "external",
                    listeners: {
                        "change": {
                            fn: Ext.bind(function(elem, checked) {
                                if (checked) {
                                    this.getSystemSettings().publicUrlMethod = "external";
                                    this.panelPublicAddress.down('textfield[name="publicUrlAddress"]').disable();
                                    this.panelPublicAddress.down('numberfield[name="publicUrlPort"]').disable();
                                }
                            }, this)
                        }
                    }
                },{
                    cls: 'description',
                    html: Ext.String.format(this.i18n._('This works if your {0} Server has a routable public static IP address.'), rpc.companyName),
                    bodyStyle: 'padding:0px 5px 10px 25px;',
                    border: false
                },{
                    xtype: 'radio',
                    boxLabel: this.i18n._('Use Hostname'),
                    hideLabel: true,
                    name: 'publicUrl',
                    checked: this.getSystemSettings().publicUrlMethod == "hostname",
                    listeners: {
                        "change": {
                            fn: Ext.bind(function(elem, checked) {
                                if (checked) {
                                    this.getSystemSettings().publicUrlMethod = "hostname";
                                    this.panelPublicAddress.down('textfield[name="publicUrlAddress"]').disable();
                                    this.panelPublicAddress.down('numberfield[name="publicUrlPort"]').disable();
                                }
                            }, this)
                        }
                    }
                },{
                    cls: 'description',
                    html: Ext.String.format(this.i18n._('This is recommended if the {0} Server\'s fully qualified domain name looks up to its IP address both internally and externally.'),
                            rpc.companyName),
                    bodyStyle: 'padding:0px 5px 5px 25px;',
                    border: false
                }, {
                    cls: 'description',
                    html: Ext.String.format( this.i18n._( 'Current Hostname: {0}'), '<i>' + this.getHostname(true) + '</i>' ),
                    bodyStyle: 'padding:0px 5px 10px 25px;',
                    border: false
                }, {
                    xtype: 'radio',
                    boxLabel: this.i18n._('Use Manually Specified Address'),
                    hideLabel: true,
                    name: 'publicUrl',
                    checked: this.getSystemSettings().publicUrlMethod == "address_and_port",
                    listeners: {
                        "afterrender": {
                            fn: Ext.bind(function(elem) {
                                if(elem.getValue()) {
                                    this.panelPublicAddress.down('textfield[name="publicUrlAddress"]').enable();
                                    this.panelPublicAddress.down('numberfield[name="publicUrlPort"]').enable();
                                } else {
                                    this.panelPublicAddress.down('textfield[name="publicUrlAddress"]').disable();
                                    this.panelPublicAddress.down('numberfield[name="publicUrlPort"]').disable();
                                }
                            }, this)
                        },
                        "change": {
                            fn: Ext.bind(function(elem, checked) {
                                if (checked) {
                                    this.getSystemSettings().publicUrlMethod = "address_and_port";
                                    this.panelPublicAddress.down('textfield[name="publicUrlAddress"]').enable();
                                    this.panelPublicAddress.down('numberfield[name="publicUrlPort"]').enable();
                                }
                            }, this)
                        }
                    }
                },{
                    cls: 'description',
                    html: Ext.String.format(this.i18n._('This is recommended if the {0} Server is installed behind another firewall with a port forward from the specified hostname/IP that redirects traffic to the {0} Server.'),
                            rpc.companyName),
                    bodyStyle: 'padding:0px 5px 5px 25px;',
                    border: false
                },{
                    xtype: 'panel',
                    bodyStyle: 'padding-left:25px;',
                    border: false,
                    items: [{
                        xtype: 'textfield',
                        fieldLabel: this.i18n._('IP/Hostname'),
                        name: 'publicUrlAddress',
                        value: this.getSystemSettings().publicUrlAddress,
                        allowBlank: false,
                        width: 400,
                        blankText: this.i18n._("You must provide a valid IP Address or hostname."),
                        disabled: this.getSystemSettings().publicUrlMethod != "address_and_port"
                    },{
                        xtype: 'numberfield',
                        fieldLabel: this.i18n._('Port'),
                        name: 'publicUrlPort',
                        value: this.getSystemSettings().publicUrlPort,
                        allowDecimals: false,
                        minValue: 0,
                        allowBlank: false,
                        width: 210,
                        blankText: this.i18n._("You must provide a valid port."),
                        vtype: 'port',
                        disabled: this.getSystemSettings().publicUrlMethod != "address_and_port"
                    }]
                }]
            }
        });
    },

    buildCertificates: function() {
        this.panelCertificates = Ext.create('Ext.panel.Panel',{
            name: 'panelCertificates',
            helpSource: 'administration_certificates',
            // private fields
            parentId: this.getId(),

            title: this.i18n._('Certificates'),
            layout: "anchor",
            cls: 'ung-panel',
            autoScroll: true,
            defaults: { anchor: '98%', xtype: 'fieldset' },
            items: [{
                title: this.i18n._('Certificate Authority'),
                defaults: { labelWidth: 150 },
                html: '<HR>',
                items: [{
                    xtype: 'displayfield',
                    margin: '5 0 5 0',
                    value:  this.i18n._("The Certificate Authority is used to create and sign the HTTPS certificates used by several applications and services such as HTTPS Inspector and Captive Portal.  It can also be used to sign the internal web server certificate. To eliminate certificate security warnings on client computers and devices, you should download the root certificate and add it to the list of trusted authorities on each client conneced to your network.")
                },{
                    xtype: 'textfield',
                    fieldLabel: this.i18n._('Valid starting'),
                    labelStyle: 'font-weight:bold',
                    id: 'rootca_status_notBefore',
                    value: this.getCertificateInformation() == null ? "" : i18n.timestampFormat(this.getCertificateInformation().rootcaDateValid),
                    disabled: true,
                    anchor:'100%'
                },{
                    xtype: 'textfield',
                    fieldLabel: this.i18n._('Valid until'),
                    labelStyle: 'font-weight:bold',
                    id: 'rootca_status_notAfter',
                    value: this.getCertificateInformation() == null ? "" : i18n.timestampFormat(this.getCertificateInformation().rootcaDateExpires),
                    disabled: true,
                    anchor:'100%'
                },{
                    xtype: 'textarea',
                    fieldLabel: this.i18n._('Subject DN'),
                    labelStyle: 'font-weight:bold',
                    id: 'rootca_status_subjectDN',
                    value: this.getCertificateInformation() == null ? "" : this.getCertificateInformation().rootcaSubject,
                    disabled: true,
                    anchor:'100%',
                    height: 40
                },{
                    xtype: 'fieldset',
                    layout: 'column',
                    items: [{
                        xtype: 'button',
                        margin: '0 5 0 5',
                        minWidth: 200,
                        text: this.i18n._('Generate Certificate Authority'),
                        iconCls: 'action-icon',
                        handler: Ext.bind(function() {
                            this.certGeneratorPopup("ROOT", null, this.i18n._('Generate Certificate Authority'));
                        }, this)
                    },{
                        xtype: 'displayfield',
                        margin: '0 5 0 5',
                        columnWidth: 1,
                        value: this.i18n._('Click here to re-create the internal certificate authority.  Use this to change the information in the Subject DN of the root certificate.')
                    }]
                },{
                    xtype: 'fieldset',
                    layout: 'column',
                    items: [{
                        xtype: 'button',
                        margin: '0 5 0 5',
                        minWidth: 200,
                        text: this.i18n._('Download Root Certificate'),
                        iconCls: 'action-icon',
                        handler: Ext.bind(function() {
                            var downloadForm = document.getElementById('downloadForm');
                            downloadForm["type"].value = "root_certificate_download";
                            downloadForm.submit();
                        }, this)
                    },{
                        xtype: 'displayfield',
                        margin: '0 5 0 5',
                        columnWidth: 1,
                        value: this.i18n._('Click here to download the root certificate.  Installing this certificate on client devices will allow them to trust certificates generated by this server.')
                    }]
                }]
            },{
                title: this.i18n._('Server Certificate'),
                defaults: { labelWidth: 150 },
                html: '<HR>',
                items: [{
                    xtype: 'displayfield',
                    margin: '5 0 5 0',
                    value:  this.i18n._("The Server Certificate is used to secure all HTTPS connections with this server.  There are two options for creating this certificate.  You can create a certificate signed by the Certificate Authority created and displayed above, or you can purchase a certificate that is signed by a third party certificate authority such as Thawte, Verisign, etc.")
                },{
                    xtype: 'textfield',
                    fieldLabel: this.i18n._('Valid starting'),
                    labelStyle: 'font-weight:bold',
                    id: 'server_status_notBefore',
                    value: this.getCertificateInformation() == null ? "" : i18n.timestampFormat(this.getCertificateInformation().serverDateValid),
                    disabled: true,
                    anchor:'100%'
                },{
                    xtype: 'textfield',
                    fieldLabel: this.i18n._('Valid until'),
                    labelStyle: 'font-weight:bold',
                    id: 'server_status_notAfter',
                    value: this.getCertificateInformation() == null ? "" : i18n.timestampFormat(this.getCertificateInformation().serverDateExpires),
                    disabled: true,
                    anchor:'100%'
                },{
                    xtype: 'textarea',
                    fieldLabel: this.i18n._('Issuer DN'),
                    labelStyle: 'font-weight:bold',
                    id: 'server_status_issuerDN',
                    value: this.getCertificateInformation() == null ? "" : this.getCertificateInformation().serverIssuer,
                    disabled: true,
                    anchor:'100%',
                    height: 40
                },{
                    xtype: 'textarea',
                    fieldLabel: this.i18n._('Subject DN'),
                    labelStyle: 'font-weight:bold',
                    id: 'server_status_subjectDN',
                    value: this.getCertificateInformation() == null ? "" : this.getCertificateInformation().serverSubject,
                    disabled: true,
                    anchor:'100%',
                    height: 40
                },{
                    xtype: 'textarea',
                    fieldLabel: this.i18n._('Alternative Names'),
                    labelStyle: 'font-weight:bold',
                    id: 'server_status_SAN',
                    value: this.getCertificateInformation() == null ? "" : this.getCertificateInformation().serverNames,
                    disabled: true,
                    anchor:'100%',
                    height: 40
                },{
                    xtype: 'fieldset',
                    layout: 'column',
                    items: [{
                        xtype: 'button',
                        margin: '0 5 0 5',
                        minWidth: 200,
                        text: this.i18n._('Generate Server Certificate'),
                        iconCls: 'action-icon',
                        handler: Ext.bind(function() {
                            this.certGeneratorPopup("SERVER", this.getHostname(true), this.i18n._('Generate Server Certificate'));
                        }, this)
                    },{
                        xtype: 'displayfield',
                        margin: '0 5 0 5',
                        columnWidth: 1,
                        value: this.i18n._('Click here to create a server certificate signed by the Certificate Authority displayed above.  Use this to change the information in the Subject DN of the server certificate.')
                    }]
                }]
            },{
                title: this.i18n._('Third Party Server Certificate'),
                defaults: { labelWidth: 150 },
                items: [{
                    xtype: 'displayfield',
                    margin: '5 0 5 0',
                    value:  this.i18n._("To use a server certificate signed by a third party certificate authority, you must first generate a Certificate Signing Request (CSR) using the first button below.  This will allow you to create a new CSR which will be downloaded to your computer.  You will provide this file to the certificate vendor of your choice, and they will use it to create a signed server certificate which you can import using the second button below.")
                },{
                    xtype: 'fieldset',
                    layout: 'column',
                    items: [{
                        border: false,
                        width: 20,
                        html: '<div class="step_counter">1</div>'
                    },{
                        xtype: 'button',
                        margin: '0 5 0 5',
                        minWidth: 200,
                        text: this.i18n._('Create Signature Signing Request'),
                        iconCls: 'action-icon',
                        handler: Ext.bind(function() {
                            this.certGeneratorPopup("CSR", this.getHostname(true), this.i18n._("Create Signature Signing Request"));
                        }, this)
                    },{
                        xtype: 'displayfield',
                        margin: '0 5 0 5',
                        columnWidth: 1,
                        value: this.i18n._('Click here to generate and download a new Certificate Signing Request (CSR) to your computer.')
                    }]
                },{
                    xtype: 'fieldset',
                    layout: 'column',
                    items: [{
                        border: false,
                        width: 20,
                        html: '<div class="step_counter">2</div>'
                    },{
                        xtype: 'button',
                        margin: '0 5 0 5',
                        minWidth: 200,
                        text: this.i18n._('Import Signed Server Certificate'),
                        iconCls: 'action-icon',
                        handler: Ext.bind(function() { this.handleCertificateUpload(); }, this)
                    },{
                        xtype: 'displayfield',
                        margin: '0 5 0 5',
                        columnWidth: 1,
                        value: this.i18n._('Click here to upload a signed server certificate that you received from the CSR you created in step one.')
                    }]
                }]
            }]
        });
    },

    certGeneratorPopup: function(certMode, hostName, titleText)
    {
        var helptipRenderer = function(c) {
            Ext.create('Ext.tip.ToolTip', {
                target: c.getEl(),
                html: c.helptip,
                dismissDelay: 0,
                anchor: 'bottom'
            });
        };

        try {
            netStatus = main.getNetworkManager().getInterfaceStatus();
        } catch (e) {
            Ung.Util.rpcExHandler(e);
        }

        addressList = "";
        addressList += hostName;

        for( x = 0 ; x < netStatus.list.length ; x++)
        {
            var netItem = netStatus.list[x];
            if (netItem.v4Address === null) { continue; }
            addressList += ",";
            addressList += netItem.v4Address;
        }

        this.certGeneratorWindow = Ext.create("Ext.Window", {
            title: titleText,
            layout: 'fit',
            width: 600,
            height: (certMode === "ROOT" ? 320 : 360),
            border: true,
            xtype: 'form',
            modal: true,
            items: [{
                xtype: "form",
                border: false,
                items: [{
                    xtype: 'combo',
                    fieldLabel: this.i18n._('Country') + " (C)",
                    labelWidth: 150,
                    name: 'Country',
                    id: 'Country',
                    helptip: this.i18n._("Select the country in which your organization is legally registered."),
                    margin: "10 10 10 10",
                    size: 50,
                    allowBlank: true,
                    store: Ung.Country.getCountryStore(i18n),
                    queryMode: 'local',
                    editable: false,
                    listeners: {
                        render: helptipRenderer
                    }
                },{
                    xtype: 'textfield',
                    fieldLabel: this.i18n._('State/Province') + " (ST)",
                    labelWidth: 150,
                    name: "State",
                    helptip: this.i18n._('Name of state, province, region, territory where your organization is located. Please enter the full name. Do not abbreviate.'),
                    margin: "10 10 10 10",
                    size: 200,
                    allowBlank: false,
                    listeners: {
                        render: helptipRenderer
                    }
                },{
                    xtype: 'textfield',
                    fieldLabel: this.i18n._('City/Locality') + " (L)",
                    labelWidth: 150,
                    name: "Locality",
                    helptip: this.i18n._('Name of the city/locality in which your organization is registered/located. Please spell out the name of the city/locality. Do not abbreviate.'),
                    margin: "10 10 10 10",
                    size: 200,
                    allowBlank: false,
                    listeners: {
                        render: helptipRenderer
                    }
                },{
                    xtype: 'textfield',
                    fieldLabel: this.i18n._('Organization') + " (O)",
                    labelWidth: 150,
                    name: "Organization",
                    helptip: this.i18n._("The name under which your business is legally registered. The listed organization must be the legal registrant of the domain name in the certificate request. If you are enrolling as a small business/sole proprietor, please enter the certificate requester's name in the 'Organization' field, and the DBA (doing business as) name in the 'Organizational Unit' field."),
                    margin: "10 10 10 10",
                    size: 200,
                    allowBlank: false,
                    listeners: {
                        render: helptipRenderer
                    }
                },{
                    xtype: 'textfield',
                    fieldLabel: this.i18n._('Organizational Unit ') + " (OU)",
                    labelWidth: 150,
                    name: "OrganizationalUnit",
                    helptip: this.i18n._("Optional. Use this field to differentiate between divisions within an organization. For example, 'Engineering' or 'Human Resources.' If applicable, you may enter the DBA (doing business as) name in this field."),
                    margin: "10 10 10 10",
                    size: 200,
                    allowBlank: true,
                    listeners: {
                        render: helptipRenderer
                    }
                },{
                    xtype: 'textfield',
                    fieldLabel: this.i18n._('Common Name') + " (CN)",
                    labelWidth: 150,
                    name: "CommonName",
                    helptip: this.i18n._("The name entered in the 'CN' (common name) field MUST be the fully-qualified domain name of the website for which you will be using the certificate (e.g., 'www.domainnamegoeshere'). Do not include the 'http://' or 'https://' prefixes in your common name. Do NOT enter your personal name in this field."),
                    margin: "10 10 10 10",
                    size: 200,
                    allowBlank: false,
                    value: hostName,
                    listeners: {
                        render: helptipRenderer
                    }
                },{
                    xtype: 'textfield',
                    fieldLabel: this.i18n._('Subject Alternative Names'),
                    labelWidth: 150,
                    name: "AltNames",
                    helptip: this.i18n._("Optional. Use this field to enter a comma seperated list of one or more alternative host names or IP addresses that may be used to access the website for which you will be using the certificate."),
                    margin: "10 10 10 10",
                    size: 200,
                    allowBlank: true,
                    value: (certMode === "ROOT" ? "" : addressList),
                    hidden: (certMode === "ROOT" ? true : false),
                    listeners: {
                        render: helptipRenderer
                    }
                },{
                    xtype: "button",
                    text: this.i18n._("Generate"),
                    name: "Accept",
                    width: 100,
                    margin: "20 10 10 180",
                    handler: Ext.bind(function() {
                        this.certGeneratorWorker(certMode);
                    }, this)
                },{
                    xtype: "button",
                    text: this.i18n._("Cancel"),
                    name: "Cancel",
                    width: 100,
                    margin: "20 10 10 10",
                    handler: Ext.bind(function() {
                        this.certGeneratorWindow.close();
                    }, this)
                }]
            }]
        });

        this.certGeneratorWindow.show();
    },

    certGeneratorWorker: function(certMode)
    {
        var form_C = this.certGeneratorWindow.down('[name="Country"]');
        var form_ST = this.certGeneratorWindow.down('[name="State"]');
        var form_L = this.certGeneratorWindow.down('[name="Locality"]');
        var form_O = this.certGeneratorWindow.down('[name="Organization"]');
        var form_OU = this.certGeneratorWindow.down('[name="OrganizationalUnit"]');
        var form_CN = this.certGeneratorWindow.down('[name="CommonName"]');
        var form_SAN = this.certGeneratorWindow.down('[name="AltNames"]');

        if (form_C.getValue() == null)  { Ext.MessageBox.alert(this.i18n._('Warning'), this.i18n._('The Country field must not be empty')); return; }
        if (form_ST.getValue().length == 0) { Ext.MessageBox.alert(this.i18n._('Warning'), this.i18n._('The State field must not be empty')); return; }
        if (form_L.getValue().length == 0)  { Ext.MessageBox.alert(this.i18n._('Warning'), this.i18n._('The Locality field must not be empty')); return; }
        if (form_O.getValue().length == 0)  { Ext.MessageBox.alert(this.i18n._('Warning'), this.i18n._('The Organization field must not be empty')); return; }
        if (form_CN.getValue().length == 0) { Ext.MessageBox.alert(this.i18n._('Warning'), this.i18n._('The Common Name field must not be empty')); return; }

        var certSubject = ("/CN=" + form_CN.getValue());
        if ((form_C.getValue()) && (form_C.getValue().length > 0)) certSubject += ("/C=" + form_C.getValue());
        if ((form_ST.getValue()) && (form_ST.getValue().length > 0)) certSubject += ("/ST=" + form_ST.getValue());
        if ((form_L.getValue()) && (form_L.getValue().length > 0)) certSubject += ("/L=" + form_L.getValue());
        if ((form_O.getValue()) && (form_O.getValue().length > 0)) certSubject += ("/O=" + form_O.getValue());
        if ((form_OU.getValue()) && (form_OU.getValue().length > 0)) certSubject += ("/OU=" + form_OU.getValue());

        altNames = "";
        if ((form_SAN.getValue()) && (form_SAN.getValue().length > 0)) {
            altNames = form_SAN.getValue();
            var hostnameRegex = /^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\-]*[a-zA-Z0-9])\.)*([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\-]*[A-Za-z0-9])$/;
            // Parse subject alt name list. For IP's prefix with both DNS: and IP:, for hostnames prefix with DNS:, otherwise is left unchanged
            var altNameTokens = altNames.split(',');
            var altNamesArray=[];
            for(var i=0; i<altNameTokens.length; i++) {
                var altName = altNameTokens[i].trim();
                if(Ext.form.VTypes.ipAddress(altName)) {
                    altName="IP:"+altName+",DNS:"+altName;
                } else if(hostnameRegex.test(altName)) {
                    altName="DNS:"+altName;
                }
                altNamesArray.push(altName);
            }
            altNames = altNamesArray.join(',');
        }

        // for a CSR we handle it like a file download which will cause the
        // client browser to prompt the user to save the resulting file
        if (certMode === "CSR")
        {
            this.certGeneratorWindow.close();
            var downloadForm = document.getElementById('downloadForm');
            downloadForm["type"].value = "certificate_request_download";
            downloadForm["arg1"].value = certSubject;
            downloadForm["arg2"].value = altNames;
            downloadForm.submit();
            return;
        }

        // for ROOT mode we just throw up a success dialog and refresh the display
        if (certMode === "ROOT")
        {
        certFunction = main.getCertificateManager().generateCertificateAuthority;

            certFunction(Ext.bind(function(result)
            {
            this.certGeneratorWindow.close();
            refreshDisplay = this.updateCertificateDisplay();

                if (result)
                {
                Ext.MessageBox.alert(i18n._("Success"), i18n._("Certificate Authority generation successfully completed. Click OK to continue."), refreshDisplay);
                }
                else
                {
                Ext.MessageBox.alert(i18n._("Failure"), this.i18n._("Error during Certificate Authority generation.  Click OK to continue."), refreshDisplay);
                }

            }, this), certSubject, altNames);
        }

        // deal with restarting apache when creating a new server certificate
        if (certMode === "SERVER")
        {
        certFunction = main.getCertificateManager().generateServerCertificate;

            certFunction(Ext.bind(function(result)
            {
            this.certGeneratorWindow.close();

                if (result)
                {
                // stop the metric manager so we don't get a session timeout error
                Ung.MetricManager.stop();

                // create a restart window
                var restartWindow = Ext.create('Ext.window.MessageBox', { minProgressWidth: 360 });

                // the cert manager will reload apache to activate the new cert
                // so we show a please wait message and then click to continue
                restartWindow.wait(i18n._("Generating server certificate and restarting web server..."), i18n._("Please Wait"), {
                    interval: 1000,
                    increment: 15,
                    duration: 15000,
                    scope: this,
                    fn: function() {
                        restartWindow.hide();
                        Ext.MessageBox.alert(i18n._("Success"), i18n._("Certificate generation successfully completed. Click OK to return to the main page."), Ung.Util.goToStartPage);
                        }
                    });
                }

                else
                {
                    Ext.MessageBox.alert(i18n._("Failure"), this.i18n._("Error during certificate generation."));
                }
            }, this), certSubject, altNames);
        }
    },

    handleCertificateUpload: function() {
        master = this;
        popup = new Ext.Window({
            title: this.i18n._("Import Signed Server Certificate"),
            layout: 'fit',
            width: 600,
            height: 120,
            border: true,
            xtype: 'form',
            items: [{
                xtype: "form",
                id: "upload_signed_cert_form",
                url: "upload",
                border: false,
                items: [{
                    xtype: 'filefield',
                    fieldLabel: this.i18n._("File"),
                    name: "filename",
                    id: "filename",
                    margin: "10 10 10 10",
                    width: 560,
                    size: 50,
                    labelWidth: 50,
                    allowBlank: false
                }, {
                    xtype: "button",
                    text: this.i18n._("Upload Certificate"),
                    name: "Upload Certificate",
                    width: 200,
                    margin: "10 10 10 80",
                    handler: Ext.bind(function() {
                        this.handleFileUpload();
                    }, this)
                }, {
                    xtype: "button",
                    text: this.i18n._("Cancel"),
                    name: "Cancel",
                    width: 200,
                    margin: "10 10 10 10",
                    handler: Ext.bind(function() {
                        popup.close();
                    }, this)
                }, {
                    xtype: "hidden",
                    name: "type",
                    value: "server_cert"
                    }]
                }]
        });

        popup.show();
    },

    handleFileUpload: function()
    {
        var prova = Ext.getCmp("upload_signed_cert_form");
        var fileText = prova.items.get(0);
        var form = prova.getForm();

        if (fileText.getValue().length === 0)
        {
            Ext.MessageBox.alert(this.i18n._("Invalid or missing File"), this.i18n._("Please select a certificate to upload."));
            return false;
        }

        form.submit({
            success: function(form, action) {
                popup.close();

                Ung.MetricManager.stop();

                // create a restart window
                var restartWindow = Ext.create('Ext.window.MessageBox', { minProgressWidth: 360 });

                // the cert manager will reload apache to activate the new cert
                // so we show a please wait message and then click to continue
                restartWindow.wait(i18n._("Uploading server certificate and restarting web server..."), i18n._("Please Wait"), {
                    interval: 1000,
                    increment: 15,
                    duration: 15000,
                    scope: this,
                    fn: function() {
                        restartWindow.hide();
                        Ext.MessageBox.alert(i18n._("Success"), i18n._("Certificate upload successfully completed. Click OK to return to the main page."), Ung.Util.goToStartPage);
                        }
                    });
                },

            failure: function(form, action) {
                popup.close();
                Ext.MessageBox.alert(i18n._("Failure"), action.result.msg);
                }
            });

        return true;
    },

    updateCertificateDisplay: function()
    {
        var certInfo = this.getCertificateInformation(true);
        if (certInfo != null)
        {
            Ext.getCmp('rootca_status_notBefore').setValue(i18n.timestampFormat(certInfo.rootcaDateValid));
            Ext.getCmp('rootca_status_notAfter').setValue(i18n.timestampFormat(certInfo.rootcaDateExpires));
            Ext.getCmp('rootca_status_subjectDN').setValue(certInfo.rootcaSubject);

            Ext.getCmp('server_status_notBefore').setValue(i18n.timestampFormat(certInfo.serverDateValid));
            Ext.getCmp('server_status_notAfter').setValue(i18n.timestampFormat(certInfo.serverDateExpires));
            Ext.getCmp('server_status_subjectDN').setValue(certInfo.serverSubject);
            Ext.getCmp('server_status_issuerDN').setValue(certInfo.serverIssuer);
        }
    },

    buildSnmp: function() {
        var passwordValidator = function () {
            var name = this.name;
            var confirmPos = name.search("Confirm");
            if( confirmPos != -1 ){
                name = name.substring( 0, confirmPos );
            }
            var panel = this.up("panel");
            var pwd = panel.down('textfield[name="' + name + '"]');
            var confirmPwd = panel.down('textfield[name="' + name + 'Confirm"]');
            if(pwd.getValue() != confirmPwd.getValue()) {
                pwd.markInvalid();
                return i18n._('Passwords do not match');
            }
            if( pwd.getValue().length < 8 ){
                pwd.markInvalid();
                return i18n._('Password is too short.');                    
            }
            pwd.clearInvalid();
            confirmPwd.clearInvalid();
            return true;
        };

        this.panelSnmp = Ext.create('Ext.panel.Panel',{
            name: 'panelSnmp',
            helpSource: 'administration_snmp',
            // private fields
            parentId: this.getId(),
            title: this.i18n._('SNMP'),
            cls: 'ung-panel',
            autoScroll: true,
            defaults: {
                xtype: 'fieldset'
            },
            items: [{
                title: this.i18n._('SNMP'),
                defaults: {
                    labelWidth: 200
                },
                items: [{
                    xtype: 'checkbox',
                    boxLabel: this.i18n._('Enable SNMP Monitoring'),
                    hideLabel: true,
                    name: 'snmpEnabled',
                    checked: this.getSystemSettings().snmpSettings.enabled,
                    listeners: {
                        "change": {
                            fn: Ext.bind(function(elem, checked) {
                                this.getSystemSettings().snmpSettings.enabled = checked;
                                if (checked) {
                                    Ext.getCmp('administration_snmp_communityString').enable();
                                    Ext.getCmp('administration_snmp_sysContact').enable();
                                    Ext.getCmp('administration_snmp_sysLocation').enable();
                                    Ext.getCmp('administration_snmp_v3enabled').enable();
                                    var v3EnabledCmp = Ext.getCmp('administration_snmp_v3enabled');
                                    if (v3EnabledCmp.getValue()) {
                                        Ext.getCmp('administration_snmp_v3required').enable();
                                        Ext.getCmp('administration_snmp_v3username').enable();
                                        Ext.getCmp('administration_snmp_v3authenticationProtocol').enable();
                                        Ext.getCmp('administration_snmp_v3authenticationPassphrase').enable();
                                        Ext.getCmp('administration_snmp_v3authenticationPassphraseConfirm').enable();
                                        Ext.getCmp('administration_snmp_v3privacyProtocol').enable();
                                        Ext.getCmp('administration_snmp_v3privacyPassphrase').enable();
                                        Ext.getCmp('administration_snmp_v3privacyPassphraseConfirm').enable();
                                    }
                                    Ext.getCmp('administration_snmp_sendTraps').enable();
                                    var sendTrapsCmp = Ext.getCmp('administration_snmp_sendTraps');
                                    if (sendTrapsCmp.getValue()) {
                                        Ext.getCmp('administration_snmp_trapCommunity').enable();
                                        Ext.getCmp('administration_snmp_trapHost').enable();
                                        Ext.getCmp('administration_snmp_trapPort').enable();
                                    }
                                } else {
                                    Ext.getCmp('administration_snmp_communityString').disable();
                                    Ext.getCmp('administration_snmp_sysContact').disable();
                                    Ext.getCmp('administration_snmp_sysLocation').disable();
                                    Ext.getCmp('administration_snmp_v3enabled').disable();
                                    Ext.getCmp('administration_snmp_v3required').disable();
                                    Ext.getCmp('administration_snmp_v3username').disable();
                                    Ext.getCmp('administration_snmp_v3authenticationProtocol').disable();
                                    Ext.getCmp('administration_snmp_v3authenticationPassphrase').disable();
                                    Ext.getCmp('administration_snmp_v3authenticationPassphraseConfirm').disable();
                                    Ext.getCmp('administration_snmp_v3privacyProtocol').disable();
                                    Ext.getCmp('administration_snmp_v3privacyPassphrase').disable();
                                    Ext.getCmp('administration_snmp_v3privacyPassphraseConfirm').disable();
                                    Ext.getCmp('administration_snmp_sendTraps').disable();
                                    Ext.getCmp('administration_snmp_trapCommunity').disable();
                                    Ext.getCmp('administration_snmp_trapHost').disable();
                                    Ext.getCmp('administration_snmp_trapPort').disable();
                                }
                            }, this)
                        }
                    }
                },{
                    xtype: 'textfield',
                    fieldLabel: this.i18n._('Community'),
                    name: 'communityString',
//                        itemCls: 'left-indent-1',
                    id: 'administration_snmp_communityString',
                    value: this.getSystemSettings().snmpSettings.communityString == 'CHANGE_ME' ? this.i18n._('CHANGE_ME'): this.getSystemSettings().snmpSettings.communityString,
                    allowBlank: false,
                    blankText: this.i18n._("An SNMP \"Community\" must be specified."),
                    disabled: !this.getSystemSettings().snmpSettings.enabled
                },{
                    xtype: 'textfield',
                    fieldLabel: this.i18n._('System Contact'),
                    name: 'sysContact',
                    id: 'administration_snmp_sysContact',
                    value: this.getSystemSettings().snmpSettings.sysContact == 'MY_CONTACT_INFO' ? this.i18n._('MY_CONTACT_INFO'): this.getSystemSettings().snmpSettings.sysContact,
                    disabled: !this.getSystemSettings().snmpSettings.enabled
                    //vtype: 'email'
                },{
                    xtype: 'textfield',
                    fieldLabel: this.i18n._('System Location'),
                    name: 'sysLocation',
                    id: 'administration_snmp_sysLocation',
                    value: this.getSystemSettings().snmpSettings.sysLocation == 'MY_LOCATION' ? this.i18n._('MY_LOCATION'): this.getSystemSettings().snmpSettings.sysLocation,
                    disabled: !this.getSystemSettings().snmpSettings.enabled
                },{
                    xtype: 'checkbox',
                    boxLabel: this.i18n._('Enable Traps'),
                    hideLabel: true,
                    name: 'sendTraps',
                    id: 'administration_snmp_sendTraps',
                    checked: this.getSystemSettings().snmpSettings.sendTraps,
                    disabled: !this.getSystemSettings().snmpSettings.enabled,
                    listeners: {
                        "change": {
                            fn: Ext.bind(function(elem, checked) {
                                this.getSystemSettings().snmpSettings.sendTraps = checked;
                                if (checked) {
                                    Ext.getCmp('administration_snmp_trapCommunity').enable();
                                    Ext.getCmp('administration_snmp_trapHost').enable();
                                    Ext.getCmp('administration_snmp_trapPort').enable();
                                } else {
                                    Ext.getCmp('administration_snmp_trapCommunity').disable();
                                    Ext.getCmp('administration_snmp_trapHost').disable();
                                    Ext.getCmp('administration_snmp_trapPort').disable();
                                }
                            }, this)
                        }
                    }
                },{
                    xtype: 'textfield',
                    fieldLabel: this.i18n._('Community'),
                    name: 'trapCommunity',
                    id: 'administration_snmp_trapCommunity',
                    value: this.getSystemSettings().snmpSettings.trapCommunity == 'MY_TRAP_COMMUNITY' ? this.i18n._('MY_TRAP_COMMUNITY'): this.getSystemSettings().snmpSettings.trapCommunity,
                    allowBlank: false,
                    blankText: this.i18n._("An Trap \"Community\" must be specified."),
                    disabled: !this.getSystemSettings().snmpSettings.enabled || !this.getSystemSettings().snmpSettings.sendTraps
                },{
                    xtype: 'textfield',
                    fieldLabel: this.i18n._('Host'),
                    name: 'trapHost',
                    id: 'administration_snmp_trapHost',
                    value: this.getSystemSettings().snmpSettings.trapHost == 'MY_TRAP_HOST' ? this.i18n._('MY_TRAP_HOST'): this.getSystemSettings().snmpSettings.trapHost,
                    allowBlank: false,
                    blankText: this.i18n._("An Trap \"Host\" must be specified."),
                    disabled: !this.getSystemSettings().snmpSettings.enabled || !this.getSystemSettings().snmpSettings.sendTraps
                },{
                    xtype: 'numberfield',
                    fieldLabel: this.i18n._('Port'),
                    name: 'trapPort',
                    id: 'administration_snmp_trapPort',
                    value: this.getSystemSettings().snmpSettings.trapPort,
                    allowDecimals: false,
                    minValue: 0,
                    allowBlank: false,
                    blankText: this.i18n._("You must provide a valid port."),
                    vtype: 'port',
                    disabled: !this.getSystemSettings().snmpSettings.enabled || !this.getSystemSettings().snmpSettings.sendTraps
                },{
                    xtype: 'checkbox',
                    boxLabel: this.i18n._('Enable SNMP v3'),
                    hideLabel: true,
                    name: 'snmpv3Enabled',
                    id: 'administration_snmp_v3enabled',
                    checked: this.getSystemSettings().snmpSettings.v3Enabled,
                    disabled: !this.getSystemSettings().snmpSettings.enabled,
                    listeners: {
                        "change": {
                            fn: Ext.bind(function(elem, checked) {
                                this.getSystemSettings().snmpSettings.v3enabled = checked;
                                if (checked) {
                                        Ext.getCmp('administration_snmp_v3required').enable();
                                        Ext.getCmp('administration_snmp_v3username').enable();
                                        Ext.getCmp('administration_snmp_v3authenticationPassphrase').enable();
                                        Ext.getCmp('administration_snmp_v3authenticationProtocol').enable();
                                        Ext.getCmp('administration_snmp_v3authenticationPassphraseConfirm').enable();
                                        Ext.getCmp('administration_snmp_v3privacyProtocol').enable();
                                        Ext.getCmp('administration_snmp_v3privacyPassphrase').enable();
                                        Ext.getCmp('administration_snmp_v3privacyPassphraseConfirm').enable();
                                } else {
                                    Ext.getCmp('administration_snmp_v3required').disable();
                                    Ext.getCmp('administration_snmp_v3username').disable();
                                    Ext.getCmp('administration_snmp_v3authenticationProtocol').disable();
                                    Ext.getCmp('administration_snmp_v3authenticationPassphrase').disable();
                                    Ext.getCmp('administration_snmp_v3authenticationPassphraseConfirm').disable();
                                    Ext.getCmp('administration_snmp_v3privacyProtocol').disable();
                                    Ext.getCmp('administration_snmp_v3privacyPassphrase').disable();
                                    Ext.getCmp('administration_snmp_v3privacyPassphraseConfirm').disable();
                                }
                            }, this)
                        }
                    }
                },{
                    xtype: 'textfield',
                    itemCls: 'left-indent-2',
                    fieldLabel: this.i18n._('Username'),
                    name: 'snmpv3Username',
                    id: 'administration_snmp_v3username',
                    value: this.getSystemSettings().snmpSettings.v3Username,
                    allowBlank: false,
                    blankText: this.i18n._("Username must be specified."),
                    disabled: !this.getSystemSettings().snmpSettings.v3Enabled || !this.getSystemSettings().snmpSettings.enabled 
                },{    
                    xtype: 'combo',
                    fieldLabel: this.i18n._('Authentication Protocol'),
                    name: "snmpv3AuthenticationProtocol",
                    id: "administration_snmp_v3authenticationProtocol",
                    store: [
                        ["sha", this.i18n._("SHA") ],
                        ["md5", this.i18n._("MD5") ]
                    ],
                    editable: false,
                    queryMode: 'local',
                    selectOnFocus: true,
                    value: this.getSystemSettings().snmpSettings.v3AuthenticationProtocol ? this.getSystemSettings().snmpSettings.v3AuthenticationProtocol : "sha",
                    disabled: !this.getSystemSettings().snmpSettings.v3Enabled || !this.getSystemSettings().snmpSettings.enabled,
                    listeners: {
                        "select": {
                            fn: Ext.bind(function(elem, record) {
//                                    this.getSkinSettings().skinName = record[0].data.name;
                            }, this)
                        }
                    }
                },{
                    xtype: 'textfield',
                    inputType: 'password',
                    fieldLabel: this.i18n._('Authentication Passphrase'),
                    name: 'snmpv3AuthenticationPassphrase',
                    id: 'administration_snmp_v3authenticationPassphrase',
                    value: this.getSystemSettings().snmpSettings.v3AuthenticationPassphrase,
                    allowBlank: false,
                    blankText: this.i18n._("Authentication Passphrase must be specified."),
                    validator: passwordValidator,
                    disabled: !this.getSystemSettings().snmpSettings.v3Enabled || !this.getSystemSettings().snmpSettings.enabled 
                },{
                    xtype: 'textfield',
                    inputType: 'password',
                    fieldLabel: this.i18n._('Confirm Authentication Passphrase'),
                    name: 'snmpv3AuthenticationPassphraseConfirm',
                    id: 'administration_snmp_v3authenticationPassphraseConfirm',
//                        value: this.getSystemSettings().snmpSettings.v3AuthenticationPassphrase,
                    allowBlank: false,
                    blankText: this.i18n._("Confirm Authentication Passphrase must be specified."),
                    validator: passwordValidator,
                    disabled: !this.getSystemSettings().snmpSettings.v3Enabled || !this.getSystemSettings().snmpSettings.enabled 
                },{    
                    xtype: 'combo',
                    fieldLabel: this.i18n._('Privacy Protocol'),
                    name: "snmpv3PrivacyProtocol",
                    id: "administration_snmp_v3privacyProtocol",
                    store: [
                        ["des", this.i18n._("DES") ],
                        ["aes", this.i18n._("AES") ]
                    ],
                    editable: false,
                    queryMode: 'local',
                    selectOnFocus: true,
                    value: this.getSystemSettings().snmpSettings.v3PrivacyProtocol ? this.getSystemSettings().snmpSettings.v3PrivacyProtocol : "des",
                    disabled: !this.getSystemSettings().snmpSettings.v3Enabled || !this.getSystemSettings().snmpSettings.enabled,
                    listeners: {
                        "select": {
                            fn: Ext.bind(function(elem, record) {
//                                    this.getSkinSettings().skinName = record[0].data.name;
                            }, this)
                        }
                    }
                },{
                    xtype: 'textfield',
                    inputType: 'password',
                    fieldLabel: this.i18n._('Privacy Passphrase'),
                    name: 'snmpv3PrivacyPassphrase',
                    id: 'administration_snmp_v3privacyPassphrase',
                    value: this.getSystemSettings().snmpSettings.v3PrivacyPassphrase,
                    allowBlank: false,
                    blankText: this.i18n._("Privacy Passphrase must be specified."),
                    validator: passwordValidator,
                    disabled: !this.getSystemSettings().snmpSettings.v3Enabled || !this.getSystemSettings().snmpSettings.enabled 
                },{
                    xtype: 'textfield',
                    inputType: 'password',
                    fieldLabel: this.i18n._('Confirm Privacy Passphrase'),
                    name: 'snmpv3PrivacyPassphraseConfirm',
                    id: 'administration_snmp_v3privacyPassphraseConfirm',
//                        value: this.getSystemSettings().snmpSettings.v3PrivacyPassphrase,
                    allowBlank: false,
                    blankText: this.i18n._("Confirm Privacy Passphrase must be specified."),
                    validator: passwordValidator,
                    disabled: !this.getSystemSettings().snmpSettings.v3Enabled || !this.getSystemSettings().snmpSettings.enabled 
                },{
                    xtype: 'checkbox',
                    hideEmptyLabel: false,
                    boxLabel: this.i18n._('Require only SNMP v3'),
                    name: 'snmpv3Require',
                    id: 'administration_snmp_v3required',
                    checked: this.getSystemSettings().snmpSettings.v3Required,
                    validator: passwordValidator,
                    disabled: !this.getSystemSettings().snmpSettings.v3Enabled || !this.getSystemSettings().snmpSettings.enabled 
                }]
            }]
        });
    },

    buildSkins: function() {
        // keep initial skin settings
        var adminSkinsStore = Ext.create("Ext.data.Store",{
            fields: [{
                name: 'name'
            },{
                name: 'displayName',
                convert: Ext.bind(function(v) {
                    if ( v == "Default" ) return this.i18n._("Default");
                    return v;
                }, this)
            }],
            proxy: Ext.create("Ext.data.proxy.Server",{
                doRequest: function(operation, callback, scope) {
                    rpc.skinManager.getSkinsList(Ext.bind(function(result, exception) {
                        if(Ung.Util.handleException(exception)) return;
                        this.processResponse(exception==null, operation, null, result, callback, scope);
                    }, this));
                },
                reader: {
                    type: 'json',
                    root: 'list'
                }
            })
        });

        this.skinManager.addRefreshableStore( adminSkinsStore );

        this.panelSkins = Ext.create('Ext.panel.Panel',{
            name: "panelSkins",
            helpSource: 'administration_skins',
            // private fields
            parentId: this.getId(),
            title: this.i18n._('Skins'),
            cls: 'ung-panel',
            autoScroll: true,
            defaults: {
                xtype: 'fieldset',
                buttonAlign: 'left'
            },
            items: [{
                title: this.i18n._('Administration Skin'),
                items: [{
                    xtype: 'combo',
                    name: "skinName",
                    id: "administration_admin_client_skin_combo",
                    store: adminSkinsStore,
                    displayField: 'displayName',
                    valueField: 'name',
                    forceSelection: true,
                    editable: false,
                    queryMode: 'local',
                    selectOnFocus: true,
                    hideLabel: true,
                    width: 300,
                    listeners: {
                        "select": {
                            fn: Ext.bind(function(elem, record) {
                                this.getSkinSettings().skinName = record[0].data.name;
                            }, this)
                        }
                    }
                }]
            },{
                title: this.i18n._('Upload New Skin'),
                items: {
                    xtype: 'form',
                    id: 'upload_skin_form',
                    url: 'upload',
                    border: false,
                    items: [{
                        xtype: 'filefield',
                        fieldLabel: this.i18n._('File'),
                        name: 'upload_skin_textfield',
                        width: 500,
                        size: 50,
                        labelWidth: 50,
                        allowBlank: false
                    },{
                        xtype: 'button',
                        text: this.i18n._("Upload"),
                        handler: Ext.bind(function() {
                            this.panelSkins.onUpload();
                        }, this)
                    },{
                        xtype: 'hidden',
                        name: 'type',
                        value: 'skin'
                    }]
                }
            }],
            onUpload: function() {
                var prova = Ext.getCmp('upload_skin_form');
                var cmp = Ext.getCmp(this.parentId);
                var form = prova.getForm();

                cmp.skinManager.uploadSkin( cmp, form );
            }
        });
        adminSkinsStore.load({
            callback: Ext.bind(function() {
                var skinCombo=Ext.getCmp('administration_admin_client_skin_combo');
                if(skinCombo!=null) {
                    skinCombo.setValue(this.getSkinSettings().skinName);
                    skinCombo.clearDirty();
                }
            }, this)
        });
    },

    // validation function
    validate: function() {
        return  this.validateAdminAccounts() && this.validatePublicAddress() && this.validateSnmp();
    },

    //validate Admin Accounts
    validateAdminAccounts: function() {
        var listAdminAccounts = this.gridAdminAccounts.getPageList();
        var oneWritableAccount = false;

        // verify that the username is not duplicated
        for(var i=0; i<listAdminAccounts.length;i++) {
            for(var j=i+1; j<listAdminAccounts.length;j++) {
                if (listAdminAccounts[i].username == listAdminAccounts[j].username) {
                    Ext.MessageBox.alert(this.i18n._('Warning'), Ext.String.format(this.i18n._("The username name: \"{0}\" in row: {1}  already exists."), listAdminAccounts[j].username, j+1),
                        Ext.bind(function () {
                            this.tabs.setActiveTab(this.panelAdmin);
                        }, this)
                    );
                    return false;
                }
            }

            if (!listAdminAccounts[i].readOnly) {
                oneWritableAccount = true;
            }

        }

        // verify that there is at least one valid entry after all operations
        if(listAdminAccounts.length <= 0 ) {
            Ext.MessageBox.alert(this.i18n._('Warning'), this.i18n._("There must always be at least one valid account."),
                Ext.bind(function () {
                    this.tabs.setActiveTab(this.panelAdmin);
                }, this)
            );
            return false;
        }

        // verify that there was at least one non-read-only account
        if(!oneWritableAccount) {
            Ext.MessageBox.alert(this.i18n._('Warning'), this.i18n._("There must always be at least one non-read-only (writable) account."),
                Ext.bind(function () {
                    this.tabs.setActiveTab(this.panelAdmin);
                }, this)
            );
            return false;
        }

        return true;
    },

    //validate Public Address
    validatePublicAddress: function() {
        if (this.getSystemSettings().publicUrlMethod == "address_and_port") {
            var publicUrlAddressCmp = this.panelPublicAddress.down('textfield[name="publicUrlAddress"]');
            if (!publicUrlAddressCmp.isValid()) {
                Ext.MessageBox.alert(this.i18n._('Warning'), this.i18n._("You must provide a valid IP Address or hostname."),
                    Ext.bind(function () {
                        this.tabs.setActiveTab(this.panelPublicAddress);
                        publicUrlAddressCmp.focus(true);
                    }, this)
                );
                return false;
            }
            var publicUrlPortCmp = this.panelPublicAddress.down('numberfield[name="publicUrlPort"]');
            if (!publicUrlPortCmp.isValid()) {
                Ext.MessageBox.alert(this.i18n._('Warning'), Ext.String.format(this.i18n._("The port must be an integer number between {0} and {1}."), 1, 65535),
                    Ext.bind(function () {
                        this.tabs.setActiveTab(this.panelPublicAddress);
                        publicUrlPortCmp.focus(true);
                    }, this)
                );
                return false;
            }
            //prepare for save
            this.getSystemSettings().publicUrlAddress = publicUrlAddressCmp.getValue();
            this.getSystemSettings().publicUrlPort = publicUrlPortCmp.getValue();
        }

        return true;
    },

    //validate SNMP
    validateSnmp: function() {
        var isSnmpEnabled = this.getSystemSettings().snmpSettings.enabled;
        if (isSnmpEnabled) {
            var snmpCommunityCmp = Ext.getCmp('administration_snmp_communityString');
            if (!snmpCommunityCmp.isValid()) {
                Ext.MessageBox.alert(this.i18n._('Warning'), this.i18n._("An SNMP \"Community\" must be specified."),
                    Ext.bind(function () {
                        this.tabs.setActiveTab(this.panelSnmp);
                        snmpCommunityCmp.focus(true);
                    }, this)
                );
                return false;
            }

            var sendTrapsCmp = Ext.getCmp('administration_snmp_sendTraps');
            var isTrapEnabled = sendTrapsCmp.getValue();
            var snmpTrapCommunityCmp, snmpTrapHostCmp, snmpTrapPortCmp;
            if (isTrapEnabled) {
                snmpTrapCommunityCmp = Ext.getCmp('administration_snmp_trapCommunity');
                if (!snmpTrapCommunityCmp.isValid()) {
                    Ext.MessageBox.alert(this.i18n._('Warning'), this.i18n._("An Trap \"Community\" must be specified."),
                        Ext.bind(function () {
                            this.tabs.setActiveTab(this.panelSnmp);
                            snmpTrapCommunityCmp.focus(true);
                        }, this)
                    );
                    return false;
                }

                snmpTrapHostCmp = Ext.getCmp('administration_snmp_trapHost');
                if (!snmpTrapHostCmp.isValid()) {
                    Ext.MessageBox.alert(this.i18n._('Warning'), this.i18n._("An Trap \"Host\" must be specified."),
                        Ext.bind(function () {
                            this.tabs.setActiveTab(this.panelSnmp);
                            snmpTrapHostCmp.focus(true);
                        }, this)
                    );
                    return false;
                }

                snmpTrapPortCmp = Ext.getCmp('administration_snmp_trapPort');
                if (!snmpTrapPortCmp.isValid()) {
                    Ext.MessageBox.alert(this.i18n._('Warning'), Ext.String.format(this.i18n._("The port must be an integer number between {0} and {1}."), 1, 65535),
                        Ext.bind(function () {
                            this.tabs.setActiveTab(this.panelSnmp);
                            snmpTrapPortCmp.focus(true);
                        }, this)
                    );
                    return false;
                }
            }

            var v3EnabledCmp = Ext.getCmp('administration_snmp_v3enabled');
            var isV3Enabled = v3EnabledCmp.getValue();

            //prepare for save
            var snmpSysContactCmp = Ext.getCmp('administration_snmp_sysContact');
            var snmpSysLocationCmp = Ext.getCmp('administration_snmp_sysLocation');

            this.getSystemSettings().snmpSettings.communityString = snmpCommunityCmp.getValue();
            this.getSystemSettings().snmpSettings.sysContact = snmpSysContactCmp.getValue();
            this.getSystemSettings().snmpSettings.sysLocation = snmpSysLocationCmp.getValue();
            this.getSystemSettings().snmpSettings.sendTraps = isTrapEnabled;
            if (isTrapEnabled) {
                this.getSystemSettings().snmpSettings.trapCommunity = snmpTrapCommunityCmp.getValue();
                this.getSystemSettings().snmpSettings.trapHost = snmpTrapHostCmp.getValue();
                this.getSystemSettings().snmpSettings.trapPort = snmpTrapPortCmp.getValue();
            }

            this.getSystemSettings().snmpSettings.v3Enabled = isV3Enabled;
            if( isV3Enabled ){    
                this.getSystemSettings().snmpSettings.v3Required = Ext.getCmp('administration_snmp_v3required').getValue();
                this.getSystemSettings().snmpSettings.v3Username = Ext.getCmp('administration_snmp_v3username').getValue();
                this.getSystemSettings().snmpSettings.v3AuthenticationProtocol = Ext.getCmp('administration_snmp_v3authenticationProtocol').getValue();
                this.getSystemSettings().snmpSettings.v3AuthenticationPassphrase = Ext.getCmp('administration_snmp_v3authenticationPassphrase').getValue();
                this.getSystemSettings().snmpSettings.v3PrivacyProtocol = Ext.getCmp('administration_snmp_v3privacyProtocol').getValue();
                this.getSystemSettings().snmpSettings.v3PrivacyPassphrase = Ext.getCmp('administration_snmp_v3privacyPassphrase').getValue();
            }

        }
        return true;
    },
    beforeSave: function(isApply, handler) {
        handler.call(this, isApply);
    },
    save: function(isApply) {
        this.saveSemaphore = 2;
        Ext.MessageBox.wait(i18n._("Saving..."), i18n._("Please wait"));

        this.getAdminSettings().users.list=this.gridAdminAccounts.getPageList();

        rpc.adminManager.setSettings(Ext.bind(function(result, exception) {
            this.afterSave(exception, isApply);
        }, this), this.getAdminSettings());

        rpc.skinManager.setSettings(Ext.bind(function(result, exception) {
            this.afterSave(exception, isApply);
        }, this), this.getSkinSettings());
    },
    afterSave: function(exception, isApply) {
        if(Ung.Util.handleException(exception)) return;
        this.saveSemaphore--;
        if (this.saveSemaphore == 0) {
            // access settings should be saved last as saving these changes may disconnect the user from the Untangle box
            rpc.systemManager.setSettings(Ext.bind(function(result, exception) {
                if(Ung.Util.handleException(exception)) return;
                //If skin changed it needs a refresh
                if(this.initialSkin != this.getSkinSettings().skinName) {
                    Ung.Util.goToStartPage();
                    return;
                }
                if(isApply) {
                    this.gridAdminAccounts.reload({data: this.getAdminSettings(true).users.list});
                    this.initialSystemSettings = Ung.Util.clone(this.getSystemSettings(true));
                    this.getCertificateInformation(true);
                    this.getHostname(true);
                    this.clearDirty();
                    Ext.MessageBox.hide();
                } else {
                    Ext.MessageBox.hide();
                    this.closeWindow();
                }
            }, this), this.getSystemSettings());
        }
    }
});
//# sourceURL=administration.js
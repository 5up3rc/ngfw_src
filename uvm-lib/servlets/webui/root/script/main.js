Ext.namespace('Ung');
// Global Variables
// the main object instance
var main=null;
// Main object class
Ung.Main=function() {
}
Ung.Main.prototype = {
    debugMode: false,
    disableThreads: false, // in development environment is useful to disable
                            // threads.
    leftTabs: null,
    appsSemaphore: null,
    apps: null,
    appsLastState: null,
    config: null,
    nodes: null,
    // the Ext.Viewport object for the application
    viewport: null,
    initSemaphore: null,
    policySemaphore: null,
    contentLeftWidth: null,
    // the application build version
    version: null,
    iframeWin: null,
    upgradeStatus:null,
    upgradeLastCheckTime: null,
    firstTimeRun: null,
    // init function
    init: function() {
        if (Ext.isGecko) {
            document.onkeypress = function(e) {
                if (e.keyCode==27) {
                    return false;
                }
                return true;
            }
        }
        this.firstTimeRun=Ung.Util.getQueryStringParam("firstTimeRun");
        this.appsLastState={};
    	JSONRpcClient.toplevel_ex_handler = Ung.Util.rpcExHandler;
        this.initSemaphore=11;
        rpc = {};
        // get JSONRpcClient
        rpc.jsonrpc = new JSONRpcClient("/webui/JSON-RPC");
        // get language manager
        rpc.jsonrpc.RemoteUvmContext.languageManager(function (result, exception) {
            if(Ung.Util.handleException(exception)) return;
            rpc.languageManager=result;
            // get translations for main module
            rpc.languageManager.getTranslations(function (result, exception) {
                if(Ung.Util.handleException(exception)) return;
                i18n=new Ung.I18N({"map":result.map});
                Ext.MessageBox.wait(i18n._("Initializing..."), i18n._("Please wait"));
                this.postinit();// 1
            }.createDelegate(this),"untangle-libuvm");
            // get language settings
            rpc.languageManager.getLanguageSettings(function (result, exception) {
                if(Ung.Util.handleException(exception)) return;
                rpc.languageSettings=result;
                this.postinit();// 2
            }.createDelegate(this));
            
        }.createDelegate(this));
        // get skin manager
        rpc.jsonrpc.RemoteUvmContext.skinManager(function (result, exception) {
            if(Ung.Util.handleException(exception)) return;
            rpc.skinManager=result;
            // Load Current Skin
            rpc.skinManager.getSkinSettings(function (result, exception) {
                if(Ung.Util.handleException(exception)) return;
                var skinSettings=result;
                Ung.Util.loadCss("/skins/"+skinSettings.administrationClientSkin+"/css/ext-skin.css");
                Ung.Util.loadCss("/skins/"+skinSettings.administrationClientSkin+"/css/admin.css");
                this.postinit();// 3
            }.createDelegate(this));
        }.createDelegate(this));
        // get node manager
        rpc.jsonrpc.RemoteUvmContext.nodeManager(function (result, exception) {
            if(Ung.Util.handleException(exception)) return;
            rpc.nodeManager=result;
            this.postinit();// 4
        }.createDelegate(this));
        // get policy manager
        rpc.jsonrpc.RemoteUvmContext.policyManager(function (result, exception) {
            if(Ung.Util.handleException(exception)) return;
            rpc.policyManager=result;
            this.postinit();// 5
        }.createDelegate(this));
        // get toolbox manager
        rpc.jsonrpc.RemoteUvmContext.toolboxManager(function (result, exception) {
            if(Ung.Util.handleException(exception)) return;
            rpc.toolboxManager=result;
            this.postinit();// 6
        }.createDelegate(this));
        // get admin manager
        rpc.jsonrpc.RemoteUvmContext.adminManager(function (result, exception) {
            if(Ung.Util.handleException(exception)) return;
            rpc.adminManager=result;
            this.postinit();// 7
        }.createDelegate(this));
        // get version
        rpc.jsonrpc.RemoteUvmContext.version(function (result, exception) {
            if(Ung.Util.handleException(exception)) return;
            rpc.version=result;
            this.postinit();// 8
        }.createDelegate(this));
        // get network manager
        rpc.jsonrpc.RemoteUvmContext.networkManager(function (result, exception) {
            if(Ung.Util.handleException(exception)) return;
            rpc.networkManager=result;
            this.postinit();// 9
        }.createDelegate(this));
        // get message manager & message key
        rpc.jsonrpc.RemoteUvmContext.messageManager(function (result, exception) {
            if(Ung.Util.handleException(exception)) return;
            rpc.messageManager=result;
            rpc.messageManager.getMessageKey(function (result, exception) {
                if(Ung.Util.handleException(exception)) return;
                rpc.messageKey=result;
                this.postinit();// 10
            }.createDelegate(this));
        }.createDelegate(this));
        // get branding manager
        rpc.jsonrpc.RemoteUvmContext.brandingManager(function (result, exception) {
            if(Ung.Util.handleException(exception)) return;
            rpc.brandingManager=result;
            rpc.brandingManager.getBaseSettings(function (result, exception) {
                if(Ung.Util.handleException(exception)) return;
                rpc.brandingBaseSettings=result;
                document.title=rpc.brandingBaseSettings.companyName;
                this.postinit();// 11
            }.createDelegate(this));
        }.createDelegate(this));

    },
    postinit: function() {
        this.initSemaphore--;
        if(this.initSemaphore!=0) {
            return;
        }
       	this.startApplication()
    },
    warnOnUpgrades : function(handler) {
    	if(main.upgradeStatus!=null && main.upgradeStatus.upgradesAvailable) {
            main.warnOnUpgradesCallback(main.upgradeStatus,handler);
    	} else {
            if(main.upgradeLastCheckTime!=null && (new Date()).getTime()-main.upgradeLastCheckTime<300000 && main.upgradeStatus!=null) {
                main.warnOnUpgradesCallback(main.upgradeStatus,handler);
            } else {
                Ext.MessageBox.wait(i18n._("Checking for available upgrades..."), i18n._("Please wait"));
                rpc.toolboxManager.getUpgradeStatus(function(result, exception,opt,handler) {
                	main.upgradeLastCheckTime=(new Date()).getTime();
                    Ext.MessageBox.hide();
                    if(Ung.Util.handleException(exception, function() {
                        Ext.MessageBox.alert(i18n._("Failed"), exception.message, function (handler) {
                            main.upgradeStatus={};
                            main.warnOnUpgradesCallback(main.upgradeStatus,handler);
                        }.createDelegate(this,[handler]));
                    }.createDelegate(this),"noAlert")) return;
                    
                    main.upgradeStatus=result;
                    main.warnOnUpgradesCallback(main.upgradeStatus,handler);
                }.createDelegate(this,[handler],true),true);
    		}
    	}
    },
    warnOnUpgradesCallback : function (upgradeStatus,handler) {
        if(upgradeStatus!=null) {
            if(upgradeStatus.upgrading) {
                Ext.MessageBox.alert(i18n._("Failed"), "Upgrade in progress.");
                return;
            } else if(upgradeStatus.upgradesAvailable){
            	Ext.getCmp("configItem_upgrade").setIconCls("icon-config-upgrade-available");
            	Ext.Msg.show({
                    title:i18n._("Upgrades warning"),
                    msg: i18n._("Upgrades are available. You must perform all possible upgrades before downloading from the library. Please click OK to open Upgrade panel."),
                    buttons: Ext.Msg.OKCANCEL,
                    fn: function (btn, text) {
                        if (btn == 'ok'){
                            main.leftTabs.activate('leftTabConfig');
                            Ext.getCmp("configItem_upgrade").onClick();
                        }
                    },
                    icon: Ext.MessageBox.QUESTION
                });
                return;
            }
		}
        handler.call(this);
    },
    resetAppLastState: function(displayName) {
    	main.appsLastState[displayName]=null
    },
    setAppLastState: function(displayName,state,options,download) {
    	if(state==null) {
    		main.appsLastState[displayName]=null;
    	} else {
    		main.appsLastState[displayName]={state:state, options:options, download:download};
    	}
    },
    startApplication: function() {
    	Ext.MessageBox.wait(i18n._("Starting..."), i18n._("Please wait"));
        this.initExtI18n();
        this.initExtGlobal();
        this.initExtVTypes();
        Ext.EventManager.onWindowResize(Ung.Util.resizeWindows);
        // initialize viewport object
        var contentRightArr=[
            '<div id="content-right">',
                '<div id="racks" style="display:none;">',
                    '<div id="rack-list"><div id="rack-select-container"></div>',
                    '</div>',
                    '<div id="rack-nodes">',
                        '<div id="security_nodes"></div>',
                        '<div id="nodes-separator" style="display:none;"><div id="nodes-separator-text"></div></div>',
                        '<div id="other_nodes"></div>',
                    '</div>',
                '</div>',
            '</div>'];

        var cssRule = Ext.util.CSS.getRule(".content-left",true);
        this.contentLeftWidth = ( cssRule ) ? parseInt( cssRule.style.width ) : 214;

        this.viewport = new Ext.Viewport({
            layout:'border',
            items:[{
                    region:'west',
                    id: 'west',
                    //split : true,
                    buttonAlign : 'center',
                    cls:"content-left",
                    border : false,
                    width: this.contentLeftWidth,
                    bodyStyle: 'background-color: transparent;',
                    footer : false,
					buttonAlign:'left',
                    items:[{
                    	cls: "logo",
                        html: '<img src="/images/BrandingLogo.gif?'+(new Date()).getTime()+'" border="0"/>',
                        border: false,
                        bodyStyle: 'background-color: transparent;'
                    }, {
                    	layout:"anchor",
                    	border: false,
                    	cls: "left-tabs",
                    	items: this.leftTabs = new Ext.TabPanel({
                            activeTab: 0,
                            height: 400,
                            anchor:"100% 100%",
                            autoWidth : true,
                            layoutOnTabChange : true,
                            deferredRender:false,
                            defaults: {
                                anchor: '100% 100%',
                                autoWidth : true,
                                autoScroll: true
                            },
                            items:[{
                                title: i18n._('Apps'),
                                id:'leftTabApps',
                                helpSource: 'apps',
                                html:'<div id="appsItems"></div>',name:'Apps'
                            },{
                                title:i18n._('Config'),
                                id:'leftTabConfig',
                                html:'<div id="configItems"></div>',
                                helpSource: 'config',
                                name:'Config'
                            }],
                            listeners : {
                                "render" : {
                                    fn : function() {
                                        this.addNamesToPanels();
                                    }
                                }
                            }
                        })
                    }],
                    buttons:[{
                        name: 'Help',
                        iconCls: 'icon-help',
                        text: i18n._('Help'),
                        handler: function() {
                            var helpSource=main.leftTabs.getActiveTab().helpSource;
                            main.openHelp(helpSource);
                        }
					},{
                        name: 'Logout',
                        iconCls: 'icon-logout',
                        text: i18n._('Logout'),
                        handler: function() {
                            window.location.href = '/auth/logout?url=/webui&realm=Administrator';
                        }
					}]
                 },{
                    region:'center',
                    id: 'center',
                    html: contentRightArr.join(""),
                    border: false,
                    cls: 'center-region',
                    bodyStyle: 'background-color: transparent;',
                    autoScroll: true
                }
             ]
        });
        Ext.QuickTips.init();

        main.systemStats=new Ung.SystemStats({});
        Ext.getCmp("west").on("resize", function() {
            var newHeight=Math.max(this.getEl().getHeight()-175,100);
            main.leftTabs.setHeight(newHeight);
        });
        
        Ext.getCmp("west").fireEvent("resize");
        buttonCmp=new Ext.Button({
            name: 'What Apps should I use?',
            id: 'help_empty_rack',
            renderTo: 'content-right',
            iconCls: 'icon-help',
            text: i18n._('What Apps should I use?'),
            show : function() {
            	Ung.Button.prototype.show.call(this);
                this.getEl().alignTo("content-right","c-c");
            }, 
            handler: function() {
    	        main.warnOnUpgrades(function() {
                     main.openStore("wizard",i18n._('What Apps should I use?'));
                }.createDelegate(this));
            }.createDelegate(this)
        });
        buttonCmp.hide();
        buttonCmp=new Ext.Button({
            id: "my_account_button",
            name: "My Account",
            height: '42px',
            renderTo: 'appsItems',
            text: i18n._("My Account"),
            show : function() {
                Ung.Button.prototype.show.call(this);
                this.getEl().alignTo("appsItems","c-c",[0,10]);
            }, 
            handler: function() {
            	main.warnOnUpgrades(function() {
                     main.openStore("my_account",i18n._("My Account"));
                }.createDelegate(this));
            }
        });
        buttonCmp.hide();
        this.loadConfig();
        this.loadPolicies();
    },
    
    openStore : function (action,title) {
        var currentLocation = window.location;
        var query = "host=" + currentLocation.hostname;
        query += "&port=" + currentLocation.port;
        query += "&protocol=" + currentLocation.protocol.replace(/:$/, "");
        query += "&action="+action;

        this.openWindow( query, storeWindowName, title );
    },
    openStoreToLibItem : function (libItemName, title) {
        var currentLocation = window.location;
        var query = "host=" + currentLocation.hostname;
        query += "&port=" + currentLocation.port;
        query += "&protocol=" + currentLocation.protocol.replace(/:$/, "");
        query += "&action=browse";
        query += "&libitem=" + libItemName;

        this.openWindow( query, storeWindowName, title );
    },

    openWindow : function( query, windowName, title )
    {
        var url = "../library/launcher?" + query;

        /* browser specific code ... we has it. */
        if ( this.isNotRunningIE()) {
            this.openIFrame( url, title );
            return;
        }
       
        /** This code is not used for now we just open in an iframe as above */
        /* If we decide to go back to a new window for whatever then use this */ 
        var w = window.open( url, windowName, "location=0, resizable=1, scrollbars=1" );
        
        var m = String.format( i18n._( "Click {1}here{2} or disable your pop-up blocker and try again." ),
                               '<br/>', "<a href='" + url + "' target='" + windowName + "'>", '</a>' );
        
        if ( w == null ) {
            Ext.MessageBox.show({
                title : i18n._( "Unable to open a new window" ),
                msg : m,
                buttons : Ext.MessageBox.OK,
                icon : Ext.MessageBox.INFO
            });
        } else {
            if ( w ) w.focus();
        }
    },
    
    openIFrame : function( url, title )
    {
        var iframeWin = main.getIframeWin();
        iframeWin.show();
        iframeWin.setTitle(title);
        window.frames["iframeWin_iframe"].location.href = url;
    },
    
    initExtI18n: function(){
    	var locale = rpc.languageSettings.language;
    	if(locale) {
    	   Ung.Util.loadScript('/ext/source/locale/ext-lang-' + locale + '.js')
    	}
    },
    initExtGlobal: function(){
    	
    	// init quick tips
    	Ext.QuickTips.init();

    	//hide/unhide Field and label
        Ext.override(Ext.form.Field, {
            showContainer: function() {
                this.enable();
                this.show();
                this.getEl().up('.x-form-item').setDisplayed(true); // show entire container and children (including label if applicable)
            },
            
            hideContainer: function() {
                this.disable(); // for validation
                this.hide();
                this.getEl().up('.x-form-item').setDisplayed(false); // hide container and children (including label if applicable)
            },
            
            setContainerVisible: function(visible) {
                if (visible) {
                    this.showContainer();
                } else {
                    this.hideContainer();
                }
                return this;
            }
        });
    },
    // Add the additional 'advanced' VTypes
    initExtVTypes: function(){
        Ext.apply(Ext.form.VTypes, {
          ipAddress: function(val, field) {
            var ipAddrMaskRe = /^(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$/;
            return ipAddrMaskRe.test(val);
          },

          ipAddressText: i18n._('Invalid IP Address.'),

          ipAddressMatcher: function(val, field) {
            var ipAddrMaskRe = /^(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$/;
            return ipAddrMaskRe.test(val);
          },

          ipAddressMatcherText: i18n._('Invalid IP Address.'),

          port: function(val, field) {
            var minValue = 1;
            var maxValue = 65535;
            return minValue <= val && val <= maxValue;
          },

          portText: String.format(i18n._("The port must be an integer number between {0} and {1}."), 1, 65535),

          portMatcher: function(val, field) {
            var minValue = 1;
            var maxValue = 65535;
            return (minValue <= val && val <= maxValue) || (val == 'any' || val == 'all' || val == 'n/a' || val == 'none');
          },

          portMatcherText: String.format(i18n._("The port must be an integer number between {0} and {1} or one of the following values: any, all, n/a, none."), 1, 65535),

          password: function(val, field) {
            if (field.initialPassField) {
              var pwd = Ext.getCmp(field.initialPassField);
              return (val == pwd.getValue());
            }
            return true;
          },

          passwordText: i18n._('Passwords do not match')
        });
    },
    upgrade : function () {
        Ext.MessageBox.wait(i18n._("Downloading updates..."), i18n._("Please wait"));
        Ung.MessageManager.startUpgradeMode();
        rpc.toolboxManager.upgrade(function(result, exception) {
            if(Ung.Util.handleException(exception)) return;
        }.createDelegate(this));
    },
    getLoggingManager : function(forceReload) {
        if (forceReload || rpc.loggingManager === undefined) {
        	try {
                rpc.loggingManager = rpc.jsonrpc.RemoteUvmContext.loggingManager();
            } catch (e) {
                Ung.Util.rpcExHandler(e);
            }
                
        }
        return rpc.loggingManager;
    },

    getAppServerManager : function(forceReload) {
        if (forceReload || rpc.appServerManager === undefined) {
        	try {
                rpc.appServerManager = rpc.jsonrpc.RemoteUvmContext.appServerManager();
            } catch (e) {
                Ung.Util.rpcExHandler(e);
            }
            
        }
        return rpc.appServerManager;
    },

    getBrandingManager : function(forceReload) {
        if (forceReload || rpc.brandingManager === undefined) {
        	try {
                rpc.brandingManager = rpc.jsonrpc.RemoteUvmContext.brandingManager();
            } catch (e) {
                Ung.Util.rpcExHandler(e);
            }
            
        }
        return rpc.brandingManager;
    },
    
    // get branding settings
    getBrandingBaseSettings : function(forceReload) {
        if (forceReload || rpc.brandingBaseSettings === undefined) {
            rpc.brandingBaseSettings = main.getBrandingManager().getBaseSettings();
        }
        return rpc.brandingBaseSettings;
    },        

    getLicenseManager : function(forceReload) {
    	// default functionality is to reload license manager as it might change in uvm
    	if (typeof forceReload === 'undefined') {
    		forceReload = true;
    	}
        if (forceReload || rpc.licenseManager === undefined) {
        	try {
                rpc.licenseManager = rpc.jsonrpc.RemoteUvmContext.licenseManager()
            } catch (e) {
                Ung.Util.rpcExHandler(e);
            }
        }
        return rpc.licenseManager;
    },

    getAppAddressBook : function(forceReload) {
        if (forceReload || rpc.appAddressBook === undefined) {
            try {
                rpc.appAddressBook = rpc.jsonrpc.RemoteUvmContext.appAddressBook();
            } catch (e) {
                Ung.Util.rpcExHandler(e);
            }
        }
        return rpc.appAddressBook;
    },

    getMailSender : function(forceReload) {
        if (forceReload || rpc.mailSender === undefined) {
        	try {
                rpc.mailSender = rpc.jsonrpc.RemoteUvmContext.mailSender();
        	} catch (e) {
        		Ung.Util.rpcExHandler(e);
        	}
        }
        return rpc.mailSender;
    },

    unactivateNode: function(mackageDesc) {
    	Ung.AppItem.updateState(mackageDesc.displayName,"unactivating");
        rpc.nodeManager.nodeInstances(function (result, exception) {
                if(Ung.Util.handleException(exception)) return;
                var tids=result;
                if(tids.length>0) {
                	Ung.AppItem.updateState(this.displayName);
                    Ext.MessageBox.alert(this.name+" "+i18n._("Warning"),
                    String.format(i18n._("{0} cannot be removed because it is being used by the following rack:{1}You must remove the product from all racks first."), this.displayName,"<br><b>"+tids[0].policy.name+"</b><br><br>"));
                    return;
                } else {
                    rpc.toolboxManager.uninstall(function (result, exception) {
                       if(Ung.Util.handleException(exception)) return;
                       main.setAppLastState(this.displayName);
                       main.loadApps();
                        /*
                        rpc.toolboxManager.unregister(function (result, exception) {
                            if(Ung.Util.handleException(exception)) return;
                            main.loadApps();
                        }.createDelegate(this), this.name);
                        */
                    }.createDelegate(this), this.name);
                }
        }.createDelegate(mackageDesc), mackageDesc.name);
    },
    // open context sensitive help 
    openHelp: function(source) {
		var url = "../library/launcher?";
		url += "action=help";
        if(source) {
            url += "&source=" + source;
        }
        window.open(url);
    },

    // load policies list
    loadPolicies: function() {
    	Ext.MessageBox.wait(i18n._("Loading Rack..."), i18n._("Please wait"));
        rpc.policyManager.getPolicies( function (result, exception) {
            if(Ung.Util.handleException(exception)) return;
            rpc.policies=result;
            this.buildPolicies();
        }.createDelegate(this));
    },
    getNodeMackageDesc: function(Tid) {
        var i;
        if(this.myApps!==null) {
            for(i=0;i<this.myApps.length;i++) {
                if(this.myApps[i].name==Tid.nodeName) {
                    return this.myApps[i];
                }
            }
        }
        return null;
    },
    createNode: function (nodeDesc, statDesc, licenseStatus, runState) {
        var node={};
        node.tid=nodeDesc.tid.id;
        node.Tid=nodeDesc.tid;
        node.md=nodeDesc.mackageDesc;
        node.hasPowerButton=nodeDesc.hasPowerButton
        node.name=nodeDesc.mackageDesc.name;
        node.displayName=nodeDesc.mackageDesc.displayName;
        node.licenseStatus=licenseStatus;
        node.image='image?name='+node.name;
        node.blingers=statDesc;
        node.runState=runState;
        return node;
    },
    buildApps: function () {
        //destroy Apps
        if(main.apps!=null) {
            for(var i=0; i<main.apps.length; i++) {
                Ext.destroy(main.apps[i]);
            }
            this.apps=null;
        }
        //build Apps
        this.apps=[];
        for(var i=0;i<rpc.rackView.applications.list.length;i++) {
            var application=rpc.rackView.applications.list[i];
            var appCmp=new Ung.AppItem(application);
            if(appCmp.isValid) {
                this.apps.push(appCmp);
            }
        }
        if(this.apps.length>0) {
        	Ext.getCmp("my_account_button").hide();
        } else {
        	Ext.getCmp("my_account_button").show();
        }
    },
    findLibItemDisplayName: function(libItemName) {
        if(main.apps!=null) {
            for(var i=0; i<main.apps.length; i++) {
                if(main.apps[i].libItem!=null && main.apps[i].libItem.name==libItemName) {
                	return main.apps[i].libItem.displayName
                }
            }
        }
    	return null;
    },
    buildNodes: function() {
        //build nodes
        Ung.MessageManager.stop();
        this.destoyNodes();
        this.nodes=[];
        for(var i=0;i<rpc.rackView.instances.list.length;i++) {
            var nodeDesc=rpc.rackView.instances.list[i];
            var node=this.createNode(nodeDesc,
                rpc.rackView.statDescs.map[nodeDesc.tid.id],
                rpc.rackView.licenseStatus.map[nodeDesc.mackageDesc.name],
                rpc.rackView.runStates.map[nodeDesc.tid.id]);
            this.nodes.push(node);
        }
        this.updateSeparator();
        for(var i=0;i<this.nodes.length;i++) {
            var node=this.nodes[i];
            this.addNode.defer(1,this,[node]);
        }
        if(!main.disableThreads) {
            Ung.MessageManager.start(true);
        }
        if(Ext.MessageBox.isVisible() && Ext.MessageBox.getDialog().title==i18n._("Please wait")) {
            Ext.MessageBox.hide.defer(30,Ext.MessageBox);
        }
    },
    // load the rack view for current policy
    loadRackView: function() {
        var callback = function (result, exception) {
            if(Ung.Util.handleException(exception)) return;
            rpc.rackView=result;
            main.buildApps();
            main.buildNodes();
        }.createDelegate(this);
        
        Ung.Util.RetryHandler.retry( rpc.toolboxManager.getRackView, rpc.toolboxManager,
                                     [ rpc.currentPolicy ], callback, 1500, 10 );
    },
    loadApps: function() {
    	if(Ung.MessageManager.installInProgress>0) {
    	    return;
    	}
        var callback = function(result,exception) {
            if(Ung.Util.handleException(exception)) return;
            rpc.rackView=result;
            main.buildApps();
        }.createDelegate(this);
        
        Ung.Util.RetryHandler.retry( rpc.toolboxManager.getRackView, rpc.toolboxManager,
                                     [ rpc.currentPolicy ], callback, 1500, 10 );
    },
    loadLicenseStatus: function() {
        var callback = function(result,exception)
        {
            if(Ung.Util.handleException(exception)) return;
            rpc.rackView=result;
            for (var i = 0; i < main.nodes.length; i++) {
                var nodeCmp = Ung.Node.getCmp(main.nodes[i].tid);
                if (nodeCmp && nodeCmp.licenseStatus) {
                    nodeCmp.updateLicenseStatus(rpc.rackView.licenseStatus.map[nodeCmp.name]);
                }
            }
        }.createDelegate(this);

        Ung.Util.RetryHandler.retry( rpc.toolboxManager.getRackView, rpc.toolboxManager,
                                     [ rpc.currentPolicy ], callback, 1500, 10 );
    },

    installNode: function(mackageDesc, appItem) {
        if(mackageDesc!==null) {
	    if(main.getNode(mackageDesc.name)!=null) {
        	appItem.hide();
        	return;
            }
            Ung.AppItem.updateState(mackageDesc.displayName, "installing");
            rpc.nodeManager.instantiateAndStart(function (result, exception) {
                if(Ung.Util.handleException(exception)) return;
            }.createDelegate(this), mackageDesc.name, rpc.currentPolicy);
        }
    },
    getIframeWin: function() {
        if(this.iframeWin==null) {
            this.iframeWin=new Ung.Window({
                id: 'iframeWin',
                title:'',
                layout: 'fit',
                items: {
                    html: '<iframe id="iframeWin_iframe" name="iframeWin_iframe" width="100%" height="100%" />'
                },
                closeAction:'closeActionFn',
                closeActionFn: function() {
                    this.hide();
                    window.frames["iframeWin_iframe"].location.href="/webui/blank.html";
                    if (this.breadcrumbs){
                        Ext.destroy(this.breadcrumbs);
                    }
                }

            });
            this.iframeWin.render();
        }
        return this.iframeWin;
    },
    openInRightFrame : function(title, url) {
        var iframeWin=main.getIframeWin();
        iframeWin.show();
        if (typeof title == 'string') {
            iframeWin.setTitle(title);
        } else { // the title represents breadcrumbs
            iframeWin.setTitle('<span id="title_' + iframeWin.getId() + '"></span>');
            iframeWin.breadcrumbs = new Ung.Breadcrumbs({
		renderTo : 'title_' + iframeWin.getId(),
		elements : title
	    })            
        }
        window.frames["iframeWin_iframe"].location.href=url;
    },
    // load Config
    loadConfig: function() {
        this.config =
            [{"name":"networking","displayName":i18n._("Networking"),"iconClass":"icon-config-network","helpSource":"networking_config",handler : main.openNetworking},
            {"name":"administration","displayName":i18n._("Administration"),"iconClass":"icon-config-admin","helpSource":"administration_config", className:"Ung.Administration", scriptFile:"administration.js", handler : main.openConfig},
            {"name":"email","displayName":i18n._("Email"),"iconClass":"icon-config-email","helpSource":"email_config", className:"Ung.Email", scriptFile:"email.js", handler : main.openConfig},
            {"name":"localDirectory","displayName":i18n._("Local Directory"),"iconClass":"icon-config-directory","helpSource":"local_directory_config", className:"Ung.LocalDirectory", scriptFile:"localDirectory.js", handler : main.openConfig},
            {"name":"upgrade","displayName":i18n._("Upgrade"),"iconClass":"icon-config-upgrade","helpSource":"upgrade_config", className:"Ung.Upgrade", scriptFile:"upgrade.js", handler : main.openConfig},
            {"name":"system","displayName":i18n._("System"),"iconClass":"icon-config-setup","helpSource":"system_config", className:"Ung.System", scriptFile:"system.js", handler : main.openConfig},
            {"name":"systemInfo","displayName":i18n._("System Info"),"iconClass":"icon-config-support","helpSource":"system_info_config", className:"Ung.SystemInfo", scriptFile:"systemInfo.js", handler : main.openConfig}];
        this.buildConfig();
    },
    // build config buttons
    buildConfig: function() {
        var out=[];
        for(var i=0;i<this.config.length;i++) {
            var item=this.config[i];
            var appItemCmp=new Ung.ConfigItem({
            	item:item
            });
        }
    },
    checkForUpgrades: function (handler) {
        //check for upgrades
        rpc.toolboxManager.getUpgradeStatus(function(result, exception,opt,handler) {
            if(handler) {
                handler.call(this);
            }
            if(Ung.Util.handleException(exception, function() {
                main.upgradeLastCheckTime=(new Date()).getTime();
                Ext.MessageBox.alert(i18n._("Failed"), exception.message, function () {
                    main.upgradeStatus={};
                }.createDelegate(this));
            }.createDelegate(this),"noAlert")) return;
            main.upgradeStatus=result;
            if(main.upgradeStatus.upgradesAvailable) {
                Ext.getCmp("configItem_upgrade").setIconCls("icon-config-upgrade-available");
            }
        }.createDelegate(this,[handler],true),true);
    },
    openNetworking : function() {
        var alpacaUrl = "/alpaca/";
        var breadcrumbs = [{
            title : i18n._("Configuration"),
            action : function() {
                main.iframeWin.closeActionFn();
            }.createDelegate(this)
        }, {
            title : i18n._('Networking')
        }];
        
        main.openInRightFrame(breadcrumbs, alpacaUrl);
    
    },
    openConfig: function(configItem) {
    	Ext.MessageBox.wait(i18n._("Loading Config..."), i18n._("Please wait"));
        Ung.Util.loadResourceAndExecute.defer(1, this, [configItem.className,Ung.Util.getScriptSrc("script/config/"+configItem.scriptFile), function() {
            eval('main.configWin = new ' + this.className + '(this);');
            main.configWin.show();
            Ext.MessageBox.hide();
        }.createDelegate(configItem)]);
    },

    destoyNodes: function () {
        if(this.nodes!==null) {
            for(var i=0;i<this.nodes.length;i++) {
                var node=this.nodes[i];
                var cmp=Ung.Node.getCmp(this.nodes[i].tid);
                if(cmp) {
                    cmp.destroy();
                    cmp=null;
                }
            }
        }
    },

    getNodePosition: function(place, viewPosition) {
        var placeEl=document.getElementById(place);
        var position=0;
        if(placeEl.hasChildNodes()) {
            for(var i=0;i<placeEl.childNodes.length;i++) {
                if(placeEl.childNodes[i].getAttribute('viewPosition')-viewPosition<0) {
                    position=i+1;
                } else {
                    break;
                }
            }
        }
        return position;
    },
    addNode: function (node) {
        var nodeWidget=new Ung.Node(node);
        var place=(node.md.type=="NODE")?'security_nodes':'other_nodes';
        var position=this.getNodePosition(place,node.md.viewPosition);
        nodeWidget.render(place,position);
        Ung.AppItem.updateState(node.displayName, null);
    },
    getNode : function(nodeName) {
    	if(main.nodes) {
            for (var i = 0; i < main.nodes.length; i++) {
                if (nodeName == main.nodes[i].name) {
                    return main.nodes[i];
                    break;
                }
            }
    	}
        return null;
    },
    isNodeRunning : function(nodeName) {
    	var node = main.getNode(nodeName);
    	if (node != null) {
    		 var nodeCmp = Ung.Node.getCmp(node.tid);
    		 if (nodeCmp != null && nodeCmp.isRunning()){
    		 	return true;
    		 }
    	}
    	return false;
    },
    // Show - hide Services header in the rack
    updateSeparator: function() {
    	if(this.nodes.length==0) {
    	    document.getElementById("racks").style.display="none";
    	    Ext.getCmp("help_empty_rack").show();
    	} else {
    		Ext.getCmp("help_empty_rack").hide();
    		document.getElementById("racks").style.display="";
            var hasUtil=false;
            var hasService=false;
            for(var i=0;i<this.nodes.length;i++) {
                if(this.nodes[i].md.type!="NODE") {
            	   hasService=true;
            	   if(this.nodes[i].md.type!="SERVICE") {
            	       hasUtil=true
            	   }
            	}
            }
            document.getElementById("nodes-separator-text").innerHTML=(hasService && hasUtil)?i18n._("Services & Utilities"):hasService?i18n._("Services"):"";
            document.getElementById("nodes-separator").style.display= hasService?"":"none";
            if(hasService) {
                document.getElementById("racks").style.backgroundPosition="0px 100px";
            } else {
                document.getElementById("racks").style.backgroundPosition="0px 50px";
            }
    	}
    },
    // build policies select box
    buildPolicies: function () {
		if(main.rackSelect!=null) {
			Ext.destroy(main.rackSelect);
			Ext.get('rack-select-container').dom.innerHTML = '';
		}
        var items=[];
		var selVirtualRackIndex = 0;
		for(var i=0;i<rpc.policies.length;i++) {
            selVirtualRackIndex = rpc.policies[i]["default"]===true ? i :selVirtualRackIndex;
			items.push({text:rpc.policies[i]["default"]===true ? i18n._("Default Rack"): i18n._(rpc.policies[i].name),
                    value:rpc.policies[i].id,index:i,handler:main.changePolicy, hideDelay :0});

            if(rpc.policies[i]["default"]===true) {
                rpc.currentPolicy=rpc.policies[i];
            }
        }
		items.push('-');
		items.push({text:i18n._('Show Policy Manager'),value:'SHOW_POLICY_MANAGER',handler:main.changePolicy, hideDelay :0});
		main.rackSelect = new Ext.SplitButton({
			renderTo: 'rack-select-container', // the container id
		   	text: items[selVirtualRackIndex].text,
			id:'rack-select',
		   	//handler: Ung.Main.changePolicy, // handle a click on the button itself
		   	menu: new Ext.menu.Menu({
		   		hideDelay: 0,
		        items: items
		   	})
		});
        if(this.firstTimeRun) {
            this.checkForUpgrades(main.loadRackView);
        } else {
            main.loadRackView();
            this.checkForUpgrades.defer(900,this,[null]);
        }
        
    },
    // change current policy
    changePolicy: function () {
        if(this.value=='SHOW_POLICY_MANAGER'){
        	Ext.MessageBox.wait(i18n._("Loading Config..."), i18n._("Please wait"));
            Ung.Util.loadResourceAndExecute.defer(1,this,["Ung.PolicyManager",Ung.Util.getScriptSrc("script/config/policyManager.js"), function() {
                main.policyManagerWin=new Ung.PolicyManager({"name":"policyManager", "helpSource":"policy_manager"});
                main.policyManagerWin.show();
                Ext.MessageBox.hide();
            }]);		
        }else{
            Ext.getCmp('rack-select').setText(this.text);		
            rpc.currentPolicy=rpc.policies[this.index];
            main.loadRackView();		
        }
    },

    /* browser specific code ... we has it. */
    isNotRunningIE : function()
    {
        //if ( navigator.userAgent == "Mozilla/5.0 (X11; U; Linux i686; en-US; rv:1.8.0.7) Gecko/20060830 Firefox/1.5.0.7 (Debian-1.5.dfsg+1.5.0.7-2~bpo.1)" ) {
        var re = new RegExp(".*MSIE.*Windows.*");
        var matches =  re.exec(navigator.userAgent);
        if ( matches == null || matches[0] == null || matches[0] == "" ) {
            return true;
        }

        return false;
    }
};

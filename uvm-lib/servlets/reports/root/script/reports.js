Ext.namespace('Ung');

var rpc = null;
var reports = null;
function getWinHeight(){
    if(!window.innerHeight){
        return window.screen.height - 190;
    }
    return window.innerHeight;
}

Ext.onReady(function()
            {
              reports = new Ung.Reports({});
            });

function handleTimeout(ex)
{
  if (ex instanceof JSONRpcClient.Exception) {
    if (ex.code == 550) {
      setTimeout(function ()
                 {
                   location.reload(true);
                 }, 300);
      return true;
    }
  }
  return false;
}

JSONRpcClient.toplevel_ex_handler = function (ex) {
    handleTimeout(ex);
};

/**
 * Extended memory proxy to support local pagination
 *
 */
Ext.data.PagedMemoryProxy = function(data){
    Ext.data.PagedMemoryProxy.superclass.constructor.call(this);
    this.data = data;
};

Ext.extend(Ext.data.PagedMemoryProxy, Ext.data.MemoryProxy, {

    load : function(params, reader, callback, scope, arg){
        params = params || {};
        var result;
        try {
            result = reader.readRecords(this.data);
        }catch(e){
            this.fireEvent("loadexception", this, arg, null, e);
            callback.call(scope, null, arg, false);
            return;
        }

        if (params.limit && params.start != null) {
            result.records = result.records.slice(params.start, params.start + params.limit);
        }
        callback.call(scope, result, arg, true);
    }
});

// Main object class
Ung.Reports = Ext.extend(Object,{
    //The selected reports date
    reportsDate:null,
    // the table of contents data for the left side
    tableOfContents:null,
    //the selected node from the left side tree
    selectedNode: null,
    //the selected application/system node from the left side tree
    selectedApplication: null,
    //report details object
    reportDetails:null,
    // breadcrumbs object for the report details
    breadcrumbs: null,
    //progress bar for various actions
    progressBar : null,

    appNames: { },

    constructor : function(config)
    {
        Ext.apply(this, config);
        this.init();
    },
    init : function()
    {
        this.initSemaphore = 3;
        this.progressBar = Ext.MessageBox;
        this.treeNodes =[];
        rpc = {};
        rpc.jsonrpc = new JSONRpcClient("/reports/JSON-RPC");

        rpc.jsonrpc.ReportsContext.languageManager(this.completeLanguageManager.createDelegate(this));
        rpc.jsonrpc.ReportsContext.skinManager(this.completeSkinManager.createDelegate(this));
        rpc.jsonrpc.ReportsContext.reportingManager(this.completeReportingManager.createDelegate(this));
    },

    completeLanguageManager : function( result, exception )
    {
        if (exception) {
            if (!handleTimeout(exception)) {
                Ext.MessageBox.alert("Failed", exception.message);
            }
        }

        rpc.languageManager = result;
        // get translations for main module
        rpc.languageManager.getTranslations(this.completeGetTranslations.createDelegate(this),
                                            "untangle-libuvm");
    },

    completeGetTranslations : function( result, exception )
    {
        if (exception) {
            if (!handleTimeout(exception)) {
                Ext.MessageBox.alert("Failed", exception.message);
            }
            return;
        }

        i18n = new Ung.I18N({ "map" : result.map });

        // i18n strings
        i18n._('Monday');
        i18n._('Tuesday');
        i18n._('Wednesday');
        i18n._('Thursday');
        i18n._('Friday');
        i18n._('Saturday');
        i18n._('Sunday');
        i18n._('January');
        i18n._('February');
        i18n._('March');
        i18n._('April');
        i18n._('May');
        i18n._('June');
        i18n._('July');
        i18n._('August');
        i18n._('September');
        i18n._('October');
        i18n._('November');
        i18n._('December');

        this.postinit();
    },

    completeSkinManager : function(result,exception)
    {
        if (exception) {
            if (!handleTimeout(exception)) {
                Ext.MessageBox.alert("Failed", exception.message);
            }
        }

        rpc.skinManager = result;
        rpc.skinManager.getSkinSettings(this.completeGetSkinSettings.createDelegate(this));
    },

    completeGetSkinSettings : function( result, exception)
    {
        if (exception) {
            if (!handleTimeout(exception)) {
                Ext.MessageBox.alert("Failed", exception.message);
            }
            return;
        }
        rpc.skinSettings = result;
        Ung.Util.loadCss("/skins/" + rpc.skinSettings.userPagesSkin + "/css/ext-skin.css");
        Ung.Util.loadCss("/skins/"+rpc.skinSettings.userPagesSkin+"/css/reports.css");
        this.postinit();
    },

    completeReportingManager : function( result, exception )
    {
        if (exception) {
            if (!handleTimeout(exception)) {
                Ext.MessageBox.alert("Failed", exception.message);
            }
        }
        rpc.reportingManager = result;
        rpc.reportingManager.getDates(this.completeGetDates.createDelegate( this ));
    },

    completeGetDates : function( result, exception )
    {
        if (exception) {
            if (!handleTimeout(exception)) {
                Ext.MessageBox.alert("Failed", exception.message);
            }
            return;
        }
        rpc.dates = result;
        this.postinit();
    },

    postinit : function()
    {
        this.initSemaphore--;
        if (this.initSemaphore != 0) {
            return;
        }
        this.startApplication();
    },
    startApplication : function()
    {
        this.reportDatesItems = [];
        for (var i = 0; i < rpc.dates.list.length; i++) {
            this.reportDatesItems.push({
                text : i18n.dateFormat(rpc.dates.list[i].date),
                dt : rpc.dates.list[i].date,                
                numDays :rpc.dates.list[i].numDays,
                handler : function()
                {
                    reports.changeDate(this.dt);
                }
            });
        }

        var panel = new Ext.Panel({
            renderTo : 'base',
            cls : "base-container",
            layout : 'border',
            height : getWinHeight()-80,
            defaults : { border : false,
                         bodyStyle : 'background-color: transparent;'
                       },
            items : [{
                region : 'north',
                layout : 'border',
                style : 'padding: 7px 5px 7px 7px;',
                height : 65,
                defaults : {
                    border : false,
                    bodyStyle : 'background-color: transparent;'
                },
                items : [{
                    html: '<img src="/images/BrandingLogo.gif?'+(new Date()).getTime()+'" border="0" height="50"/>',
                    region : 'west',
                    width : 100
                },{
                    html : '<h1>'+i18n._('Reports')+'</h1>',
                    region : 'center'
                },{
                    region : 'east',
                    layout : 'fit',
                    width : 500,
                    cls   : 'dateRange',
                    items : [{
                        xtype : "fieldset",
                        border : false,
                        cls : 'dateContainer',
                        id : 'rangeFieldSet',
                        items : [{
                            xtype : 'label',
                            id : 'report-day-menu',
                            html : i18n._('View Other Reports'),
                            listeners : {
                                "render" : {
                                    fn : function(comp) {
                                       comp.getEl().on("click",this.showAvailableReports,this);
                                    }.createDelegate(this)
                                }
                            }                            
                        },/*{
                            xtype : 'splitbutton',
                            id : 'report-day-menu',
                            text : this.reportDatesItems[0].text,
                            menu : new Ext.menu.Menu({ items : this.reportDatesItems })
                        },*/{
                            xtype : 'label',
                            id : 'report-date-range',
                            html : reports.getDateRangeText(this.reportDatesItems[0]),
                            height:20
                        }]
                    }]
                }]
            },{
                region : "center",
                layout : 'border',
                width : 960,
                height : getWinHeight() - 30,//'auto',//window.innerHeight-30,
                items : [{
                    xtype : 'treepanel',
                    id : 'tree-panel',
                    region : 'center',
                    margins : '2 2 0 2',
                    autoScroll : true,
                    rootVisible : false,
                    title : i18n._('Reports'),
                    enableDD: false,
                    enableDrag: false,
                    root : new Ext.tree.AsyncTreeNode({
                        draggable : false,
                        //id : 'source',
                        children : []
                    }),
                    loader : new Ext.tree.TreeLoader(),
                    listeners : {
                        'load' : function(node)
                        {
                            // Select the firs element form the tableOfContent tree to load it's report details
                            Ext.getCmp('tree-panel').getSelectionModel().select(Ext.getCmp('tree-panel').getRootNode().firstChild);
                        },
                        'render' : function(tp)
                        {
                            tp.getSelectionModel().on('selectionchange',
                                                      function(tree, node)
                                                      {
                                                          if(node!=null) {
                                                              if (node.attributes.name == 'applications') {
                                                                  return;
                                                              }

                                                              reports.selectedNode=node;
                                                              if (node.attributes.name != 'users' && node.attributes.name != 'hosts'
                                                                  && node.attributes.name != 'emails') {
                                                                  reports.selectedApplication = node.attributes.name;
                                                              }
                                                              reports.breadcrumbs=[];
                                                              rpc.drilldownType = null;
                                                              rpc.drilldownValue = null;
                                                              reports.getApplicationData(node.attributes.name);
                                                          }
                                                      });

                            p = Ext.urlDecode(window.location.search.substring(1));
                            qsDate = p.date;
                            if (qsDate) {
                                dp = qsDate.split('-');
                                d = new Date(parseInt(dp[0]), parseInt(dp[1]) - 1, parseInt(dp[2]));

                                reports.changeDate({
                                    javaClass: 'java.util.Date',
                                    ime: d.getTime()
                                });
                            } else if (rpc.dates && rpc.dates.list.length > 0) {
                                reports.changeDate(rpc.dates.list[0].date);
                            }
                        }
                    }
                },{
                    region : 'east',
                    title : 'Report Details&nbsp;<span id="breadcrumbs" class="breadcrumbs"></span>',
                    id : 'report-details',
                    layout:"anchor",
                    autoScroll : true,
                    collapsible : false,
                    split : true,
                    margins : '2 2 0 2',
                    cmargins : '2 2 2 2',
                    width : "80%",
                    defaults: { border: false },
                    items : [{ html:"" }]
                }]
            }]
        });
    },
    getAvailableReportsData : function (){
        return this.reportDatesItems;            
    },
    showReportFor : function(value){
        var found = -1,i ;
        for(i=0;i<this.reportDatesItems.length;i++){
            if(value==this.reportDatesItems[i].dt.time){
                found = i;
                break;        
            }
        }
        if(found == -1){
            Ext.MessageBox("Unable to load reports","Could not load the selected report");
        }else{
            this.availableReportsWindow.hide();
            this.changeDate(this.reportDatesItems[found].dt);            
        }
    },
    showAvailableReports : function(){
        if(!this.availableReportsWindow){
            this.datesGrid = new Ung.EditorGrid({
                paginated : false,
                hasReorder : false,
                hasEdit : false,
                hasDelete : false,
                width : 950,
                height : getWinHeight()-60,                
                hasAdd : false,
                data : this.getAvailableReportsData(),
                autoExpandColumn : "_dateRange",
                title : i18n._( "Report Details" ),
                fields :  [{
                    name : "dt"
                },{
                    name : "numDays"
                },{
                    name : "text"
                }],
                columns : [{
                    id : "_generated",
                    header : i18n._( "Generated" ),
                    width : 70,
                    dataIndex : "text",
                    renderer : function (value){
                        return i18n._(value);   
                    }
                },{
                    id : "_dateRange",
                    header : i18n._( "Date Range" ),
                    width : 470,
                    dataIndex : "dt",
                    renderer : function (value,meta,record){
                        return reports.getDateRangeText(record.data);
                    }
                },{
                    id : "_view",
                    header : i18n._( "View" ),
                    width : 85,
                    dataIndex : "dt",                    
                    renderer : function(value,meta,record){
                        return '<a href="javascript:reports.showReportFor('+value.time+')">'+i18n._("View Report")+'</a>';
                    }
                    
                },{
                    id : "_rangeSize",
                    header : i18n._( "Range Size (days)" ),
                    width : 150,
                    dataIndex : "numDays",
                    renderer : function(value){
                        return value; 
                    }
                },{
                    id : "_dynamic",
                    header : i18n._( "Dynamic Reports Available" ),
                    width : 168,
                    dataIndex : "dt",                    
                    renderer : function (){
                        return i18n._("Yes");
                    }
                }]               
            });
            
            this.availableReportsWindow = new Ext.Window({
                applyTo : 'window-container',
                layout : 'fit',
                title : i18n._("Available Reports"),
                width : 960,
                resizable : false,
                modal : true,
                draggable : false,                
                height : getWinHeight()-30,
                closeAction :'hide',
                plain : true,
                items : new Ext.Panel({
                    deferredRender : false,
                    border : false,
                    items : this.datesGrid
                }),
                buttons: [{
                    text : i18n._('Close'),
                    handler : function(){
                        this.availableReportsWindow.hide();
                    }.createDelegate(this)
                }]
            });                
        }
        
        this.availableReportsWindow.show();                        
    },
    getTreeNodesFromTableOfContent : function(tableOfContents)
    {
        var treeNodes = [];
        if (tableOfContents.platform != null) {
            treeNodes.push({
                text : i18n._(tableOfContents.platform.title),
                name : tableOfContents.platform.name,
                leaf: true,
                icon : "./node-icons/untangle-vm.png"
			},{
		    text : i18n._("Server"),
			name : "untangle-node-reporting",
			leaf: true,
			icon : "./node-icons/server.png"

		});
        }

        if (tableOfContents.applications != null) {
            var tn = {
                text : i18n._("Applications"),
                name : "applications"
            };
            var tc = tableOfContents.applications;

            if (tc.list != null && tc.list.length > 0) {
                tn.leaf = false;
                tn.children = [];
                for (var i = 0; i < tc.list.length; i++) {
                    this.appNames[tc.list[i].name] = tc.list[i].title;
                    tn.children.push({
                        text : i18n._(tc.list[i].title),
                        name : tc.list[i].name,
                        leaf : true,
                        icon : "./node-icons/" + tc.list[i].name + ".png"
                    });
                    tn.expanded = true;
                }
            } else {
                tn.leaf = true;
            }

            treeNodes.push(tn);
        }

        if (tableOfContents.users != null) {
            treeNodes.push({
                text : i18n._("Users"),
                name : "users",
                leaf: true,
                icon : "./node-icons/users.png",
                listeners : {
                    'click' : this.refreshContentPane
                }
            });
        }

        if (tableOfContents.hosts != null) {
            treeNodes.push({
                text : i18n._("Hosts"),
                name : "hosts",
                leaf: true,
                icon : "./node-icons/hosts.png",
                listeners : {
                    'click' : this.refreshContentPane
                }
            });
        }

        if ( tableOfContents.emails!=null ) {
            treeNodes.push({
                text : i18n._("Emails"),
                name : "emails",
                leaf: true,
                icon : "./node-icons/emails.png",
                listeners : {
                    'click' : this.refreshContentPane
                }
            });
        }

        return treeNodes;
    },
    /**
      * Refreshes the content pane when a selected node is clicked
      * again */
    refreshContentPane : function(node,e)
    {
        //check if someone's clicking on the selected node
        var selModel = Ext.getCmp('tree-panel').getSelectionModel();
        if(selModel.getSelectedNode().id == node.id){
            //refresh the content pane
            selModel.fireEvent('selectionchange',selModel,node);
        }
    },
    changeDate : function(date)
    {
        this.reportsDate=date;

        for (var i = 0; i < this.reportDatesItems.length; i++) {
            var item = this.reportDatesItems[i];
            var found = false;

            if (item.dt.time == date.time) {
                //Ext.getCmp('report-day-menu').setText(item.text);
                Ext.getCmp('report-date-range').setText(reports.getDateRangeText(item));
                found = true;
                break;
            }
        }

        rpc.reportingManager.getTableOfContents(function(result, exception)
                                                {
                                                    if (exception) {
                                                        if (!handleTimeout(exception)) {
                                                            Ext.MessageBox.alert("Failed", exception.message);
                                                        }
                                                        return;
                                                    }

                                                    this.tableOfContents = result;
                                                    var treeNodes = this.getTreeNodesFromTableOfContent(this.tableOfContents);
                                                    Ext.getCmp('tree-panel').getSelectionModel().clearSelections();
                                                    var root= Ext.getCmp('tree-panel').getRootNode();
                                                    root.collapse(true);
                                                    root.attributes.children=treeNodes;
                                                    Ext.getCmp('tree-panel').getLoader().load(root);
                                                }.createDelegate(this), this.reportsDate, 1);
    },
    getDateRangeText : function(selectedDate){
        var oneDay = 24*3600*1000,
        toDate =new Date(selectedDate.dt.time - oneDay),
        fromDate = new Date(selectedDate.dt.time - ((selectedDate.numDays+1)*oneDay)),
        formatString = 'l, F j Y';
        return i18n.dateLongFormat(fromDate,formatString) + " - "  +   i18n.dateLongFormat(toDate,formatString);
    },

    getApplicationData: function(nodeName) {
        reports.progressBar.wait(i18n._("Please Wait"));
        rpc.reportingManager.getApplicationData(function (result, exception)
                                                {
                                                    if (exception) {
                                                        if (!handleTimeout(exception)) {
                                                            Ext.MessageBox.alert("Failed",exception.message);
                                                        }
                                                        return;
                                                    }
                                                    rpc.applicationData=result;
                                                    reports.breadcrumbs.push({ text: this.selectedNode.attributes.text,
                                                                               handler: this.getApplicationData.createDelegate(this, [nodeName])
                                                                             });

                                                    Ung.Util.loadModuleTranslations( nodeName, i18n,
                                                                                     function(){
                                                                                         try{
                                                                                             reports.reportDetails = new Ung.ReportDetails({reportType: nodeName});
                                                                                             reports.progressBar.hide();
                                                                                         }catch(e){
                                                                                             alert(e.message);
                                                                                         }
                                                                                     }
                                                                                   );
                                                }.createDelegate(this), reports.reportsDate, 1, nodeName);
    },

    getDrilldownTableOfContents: function(fnName, type, value)
    {
        rpc.drilldownType = type;
        rpc.drilldownValue = value;
        reports.progressBar.wait(i18n._("Please Wait"));
        rpc.reportingManager[fnName](function (result, exception)
                                     {
                                         if (exception) {
                                             if (!handleTimeout(exception)) {
                                                 Ext.MessageBox.alert(this.i18n._("Failed"),exception.message);
                                             }
                                         }
                                         rpc.applicationData=result;
                                         reports.breadcrumbs.push({
                                             text: value +" "+i18n._("Reports"),
                                             handler: this.getDrilldownTableOfContents.createDelegate(this, [fnName, type, value])
                                         });
                                         this.reportDetails.buildReportDetails(); // XXX take to correct page
                                         reports.progressBar.hide();
                                     }.createDelegate(this), reports.reportsDate, 1, value);
    },

    getTableOfContentsForUser: function(user)
    {
        return this.getDrilldownTableOfContents('getTableOfContentsForUser', 'user', user);
    },

    getTableOfContentsForHost: function(host)
    {
        return this.getDrilldownTableOfContents('getTableOfContentsForHost', 'host', host);
    },

    getTableOfContentsForEmail: function(email)
    {
        return this.getDrilldownTableOfContents('getTableOfContentsForEmail', 'email', email);
    },

    getDrilldownApplicationData: function(fnName, app, type, value)
    {
        rpc.drilldownType = type;
        rpc.drilldownValue = value;
        this.selectedApplication = app;
        reports.progressBar.wait(i18n._("Please Wait"));
        rpc.reportingManager[fnName](function (result, exception)
                                     {
                                         if (exception) {
                                             if (!handleTimeout(exception)) {
                                                 Ext.MessageBox.alert(i18n._("Failed"),exception.message);
                                             }
                                             return;
                                         }
                                         if(result==null){
                                            Ext.MessageBox.alert(i18n._("No Data Available"),i18n._("The report detail you selected does not contain any data. \n This is most likely because its not possible to drill down any further into some reports."));
                                            return;
                                         }                                         
                                         rpc.applicationData=result;
                                         reports.breadcrumbs.push({ text: i18n.sprintf("%s: %s reports ", value, this.appNames[app]),
                                                                    handler: this[fnName].createDelegate(this,[app, type, value])
                                                                  });
                                         this.reportDetails.buildReportDetails(); // XXX take to correct page
                                         reports.progressBar.hide();
                                     }.createDelegate(this), reports.reportsDate, 1, app, value);
    },

    getApplicationDataForUser: function(app, user)
    {
        this.getDrilldownApplicationData('getApplicationDataForUser', app, 'user', user);
    },

    getApplicationDataForHost: function(app, host)
    {
        this.getDrilldownApplicationData('getApplicationDataForHost', app, 'host', host);
    },

    getApplicationDataForEmail: function(app, email)
    {
        this.getDrilldownApplicationData('getApplicationDataForEmail', app, 'email', email);
    },

    openBreadcrumb: function(breadcrumbIndex) {
        if (this.breadcrumbs.length>breadcrumbIndex) {
            var breadcrumb = this.breadcrumbs[breadcrumbIndex];
            reports.breadcrumbs.splice(breadcrumbIndex, this.breadcrumbs.length-breadcrumbIndex);
            breadcrumb.handler.call(this);
        }
    }
});

// Right section object class
Ung.ReportDetails = Ext.extend(Object, {
    reportType : null,
    constructor : function(config) {
        Ext.apply(this, config);
        // this.i18n should be used in ReportDetails to have i18n context based
        this.appName = reports.selectedNode.attributes.name;
        this.application = reports.selectedApplication;
        this.i18n = Ung.i18nModuleInstances[reports.selectedNode.attributes.name];
        this.reportType = config.reportType;
        this.buildReportDetails();
    },

    buildDrilldownTableOfContents : function(type)
    {
        var upperName = type.substring(0,1).toUpperCase() + type.substr(1);

        var data = [];
        var i = 0;
        var list = rpc.applicationData.applications.list;


        for (i=0; i<list.length; i++) {
            data.push([list[i].javaClass,list[i].name,list[i].title]);
        }

        return new Ext.grid.GridPanel({
            store: new Ext.data.SimpleStore({
                fields: [
                    { name: 'javaClass' },
                    { name: 'name' },
                    { name: 'title' }
                ],
                data: data
            }),
            columns: [{
                id:'title',
                header: "Application Name",
                width: 500,
                sortable: false,
                dataIndex: 'title',
                renderer: function(value, medata, record) {
                    return '<a href="javascript:reports.getApplicationDataFor' + upperName + '(\'' + record.data.name + '\', \'' + rpc.drilldownValue + '\')">' + value + '</a>';
                }.createDelegate(this)
            }],
            title:this.i18n._('Application List'),
            stripeRows: true,
            hideHeaders: true,
            enableHdMenu : false,
            enableColumnMove: false
        });
    },

    buildUserTableOfContents : function()
    {
        return this.buildDrilldownTableOfContents('user');
    },

    buildHostTableOfContents : function()
    {
        return this.buildDrilldownTableOfContents('host');
    },

    buildEmailTableOfContents : function()
    {
        return this.buildDrilldownTableOfContents('email');
    },

    buildDrilldownList : function(type, title, listTitle)
    {
        var pluralName = type + 's';
        var upperName = type.substring(0,1).toUpperCase() + type.substr(1);

        var data = [];
        var i = 0;

        for(i=0;i<reports.tableOfContents[pluralName].list.length;i++){
            data.push([reports.tableOfContents[pluralName].list[i].javaClass,
                       reports.tableOfContents[pluralName].list[i].name,null]);
        }

        return new Ext.grid.GridPanel({
            store: new Ext.data.SimpleStore({
                fields: [
                    {name: 'javaClass'},
                    {name: 'name'},
                    {name: 'linkType'} //this is not used currently
                ],
                data: data }),
            columns: [{
                id:'name',
                header: title,
                width: 500,
                sortable: false,
                dataIndex: 'name',
                renderer: function(value, medata, record) {
                    return '<a href="javascript:reports.getTableOfContentsFor' + upperName + '(\''+ value + '\')">' + value + '</a>';
                }.createDelegate(this)
            }], title:listTitle,
            stripeRows: true,
            hideHeaders: true,
            enableHdMenu : false,
            enableColumnMove: false
        });
    },

    buildUserList: function()
    {
        return this.buildDrilldownList('user', this.i18n._('User'),
                                       this.i18n._('User List'));
    },

    buildHostList: function()
    {
        return this.buildDrilldownList('host', this.i18n._('Host'),
                                       this.i18n._('Host List'));
    },

    buildEmailList: function()
    {
        return this.buildDrilldownList('email', this.i18n._('Email'),
                                       this.i18n._('Email List'));
    },

    buildReportDetails: function()
    {
        var reportDetails = Ext.getCmp("report-details");
        while (reportDetails.items.length!=0) {
            reportDetails.remove(reportDetails.items.get(0));
        }

        var itemsArray=[],i;
        //TODO rpc.applicationData should never be null
        if (rpc.applicationData != null) {
            if(rpc.applicationData.sections != null){
                for(i=0;i<rpc.applicationData.sections.list.length ;i++) {
                    var section=rpc.applicationData.sections.list[i];
                    var sectionPanel=this.buildSection(rpc.applicationData.name, section);
                    itemsArray.push(sectionPanel);
                }
            }
        }

        //create breadcrums item
        var breadcrumbArr=[];
        for(i=0;i<reports.breadcrumbs.length;i++) {
            if(i+1==reports.breadcrumbs.length) {
                breadcrumbArr.push(reports.breadcrumbs[i].text);
            } else {
                breadcrumbArr.push('<a href="javascript:reports.openBreadcrumb('+i+')">'+reports.breadcrumbs[i].text+'</a>');
            }
        }
        document.getElementById("breadcrumbs").innerHTML='<span class="icon-breadcrumbs-separator">&nbsp;&nbsp;&nbsp;&nbsp;</span>'+breadcrumbArr.join('<span class="icon-breadcrumbs-separator">&nbsp;&nbsp;&nbsp;&nbsp;</span>');
        if (itemsArray && itemsArray.length > 0) {
            this.tabPanel=new Ext.TabPanel({
                anchor: '100% 100%',
                autoWidth : true,
                defaults: {
                    anchor: '100% 100%',
                    autoWidth : true,
                    autoScroll: true
                },

                height : 400,
                activeTab : 0,
                frame : true,
                items : itemsArray,
                layoutOnTabChange : true
            });
            reportDetails.add(this.tabPanel);
        } else if(this.reportType != null) {
            var selectedType = 'toc';
            var reportTypeMap = {
                'users': {
                    'toc' : this.buildUserList.createDelegate(this),
                    'com.untangle.uvm.reports.TableOfContents' : this.buildUserTableOfContents.createDelegate(this)
                },
                'hosts': {
                    'toc' : this.buildHostList.createDelegate(this),
                    'com.untangle.uvm.reports.TableOfContents' : this.buildHostTableOfContents.createDelegate(this)
                },
                'emails': {
                    'toc' : this.buildEmailList.createDelegate(this),
                    'com.untangle.uvm.reports.TableOfContents' : this.buildEmailTableOfContents.createDelegate(this)
                }
            };
            if (reportTypeMap[this.reportType] != null) {
                if (rpc.applicationData != null && reportTypeMap[this.reportType][rpc.applicationData.javaClass] != null) {
                    selectedType = rpc.applicationData.javaClass;
                }
            }
            reportDetails.add(reportTypeMap[this.reportType][selectedType]());
        }
        reportDetails.doLayout();
    },

    buildSection: function(appName, section) {
        var sectionPanel=null;
        if (section.javaClass=="com.untangle.uvm.reports.SummarySection") {
            sectionPanel=this.buildSummarySection(appName, section);
        } else if (section.javaClass=="com.untangle.uvm.reports.DetailSection") {
            sectionPanel=this.buildDetailSection(appName, section);
        }

        return sectionPanel;
    },

    buildSummarySection: function (appName, section) {
        var items = [];

        for (var i = 0; i < section.summaryItems.list.length; i++) {
            var summaryItem = section.summaryItems.list[i];
            // graph
            items.push({html:'<img src="'+summaryItem.imageUrl+'"/>', bodyStyle:'padding:20px'});
            // key statistics

            colors = summaryItem.colors.map;

            columns = [];
            var data = [],columnTwoWidth=175;
            for (var j=0; j<summaryItem.keyStatistics.list.length; j++) {
                var keyStatistic = summaryItem.keyStatistics.list[j];
                data.push([keyStatistic.label, keyStatistic.value, keyStatistic.unit, keyStatistic.linkType, colors[keyStatistic.label]]);
            }

            columns = [];

            if (summaryItem.plotType == 'pie-chart') {
                columnTwoWidth = 150;
                columns.push({
                    id:'color',
                    header: "Color",
                    width: 25,
                    sortable: false,
                    dataIndex: 'color',
                    renderer: function(value, medata, record) {
                        return '<div style="position:absolute;height:8px;width:8px;margin-top:2px;background-color:#'+value+'">&nbsp;</div>';
                        //return value;
                    }.createDelegate(this)
                });
            }

            columns.push({
                id:'label',
                header: "Label",
                width: columnTwoWidth,
                sortable: false,
                dataIndex: 'label',
                renderer: function(value, medata, record) {
                    var linkType = record.data.linkType;
                    if (linkType == "UserLink") {
                        return '<a href="javascript:reports.getApplicationDataForUser(\'' + appName + '\', \'' + value + '\')">' + value + '</a>';
                    } else if (linkType == "HostLink") {
                        return '<a href="javascript:reports.getApplicationDataForHost(\'' + appName + '\', \'' + value + '\')">' + value + '</a>';
                    } else if (linkType == "EmailLink") {
                        return '<a href="javascript:reports.getApplicationDataForEmail(\'' + appName + '\', \'' + value + '\')">' + value + '</a>';
                    } else if (linkType == "URLLink") {
                        return '<a href="http://' + value + '" target="_new">' + value + '</a>';
                    } else {
                        return this.i18n._(value);
                    }
                }.createDelegate(this)
            });

            columns.push({
                header: "Value",
                width: 150,
                sortable: false,
                dataIndex: 'value',
                renderer: function (value, medata, record) {
                    var unit = record.data.unit,s;
                    if (unit && unit.indexOf('bytes') == 0) {
                        if (value < 1000000) {
                            value = Math.round(value/1000);
                            s = unit.split("/");
                            s[0] = "KB";
                            unit = s.join("/");
                        } else if (value < 1000000000) {
                            value = Math.round(value/1000000);
                            s = unit.split("/");
                            s[0] = "MB";
                            unit = s.join("/");
                        } else {
                            value = Math.round(value/1000000000);
                            s = unit.split("/");
                            s[0] = "GB";
                            unit = s.join("/");
                        }
                    }

                    var v = this.i18n.numberFormat(value);

                    return unit == null ? v : (v + " " + this.i18n._(unit));
                }.createDelegate(this)
            });

            items.push(new Ext.grid.GridPanel({
                store: new Ext.data.SimpleStore({
                    fields: [
                        {name: 'label'},
                        {name: 'value'},
                        {name: 'unit'},
                        {name: 'linkType'},
                        {name: 'color'}
                    ],
                    data: data
                }),
                columns: columns,
                // inline toolbars
                tbar:[{
                    tooltip:this.i18n._('Export Excel'),
                    iconCls:'export-excel',
                    handler : new Function("window.open('" + summaryItem.csvUrl + "');")
                }
                      //                                                                                                                '-',
                      //                                                                                                                { tooltip:this.i18n._('Export Printer'),
                      //                                                                                                                  iconCls:'export-printer',
                      //                                                                                                                  handler : new Function("window.open('" + summaryItem.printerUrl + "');")
                      //                                                                                                                }
                     ],
                title:this.i18n._('Key Statistics'),
                stripeRows: true,
                hideHeaders: true,
                enableHdMenu : false,
                enableColumnMove: false
            })
                      );
        }
        return new Ext.Panel({
            title : section.title,
            layout:'table',
            defaults: {
                border: false,
                columnWidth: 0.5
            },
            layoutConfig: {
                columns: 2
            },
            items : items
        });
    },

    buildDetailSection: function (appName, section)
    {
        var columns = [];
        var fields = [];
        var c = null;

        for (var i = 0; i < section.columns.list.length; i++) {
            c = section.columns.list[i];
            //TODO this case should not occur
            if (c == null || c == undefined) { break; }
            var col = { header:this.i18n._(c.title), dataIndex:c.name };

            if (c.type == "Date") {
                col.renderer = function(value) {
                    if (!value) {
                        return i18n._('None');
                    } else {
                        return i18n.timestampFormat(value);
                    }
                };
                col.width = 140;
            } else if (c.type == "URL") {
                col.renderer = function(value) {
                    if (!value) {
                        return i18n._('None');
                    } else {
                        return '<a href="' + value + '" target="_new">' + value + '</a>';
                    }
                };
                col.width = 160;
            } else if (c.type == "UserLink") {
                col.renderer = function(value) {
                    if (!value) {
                        return i18n._('None');
                    } else {
                        return '<a href="javascript:reports.getApplicationDataForUser(\'' + appName + '\', \'' + value + '\')">' + value + '</a>';
                    }
                };
                col.width = 100;
            } else if (c.type == "HostLink") {
                col.renderer = function(value) {
                    if (!value) {
                        return i18n._('None');
                    } else {
                        return '<a href="javascript:reports.getApplicationDataForHost(\'' + appName + '\', \'' + value + '\')">' + value + '</a>';
                    }
                };
                col.width = 100;
            } else if (c.type == "EmailLink") {
                col.renderer = function(value) {
                    if (!value) {
                        return i18n._('None');
                    } else {
                        return '<a href="javascript:reports.getApplicationDataForEmail(\'' + appName + '\', \'' + value + '\')">' + value + '</a>';
                    }
                };
                col.width = 180;
            } else if (c.type == "URLLink") {
                col.renderer = function(value) {
                    if (!value) {
                        return i18n._('None');
                    } else {
                        return '<a href="http://' + value + '" target="_new">' + value + '</a>';
                    }
                };
            } else {
                col.renderer = function(value) {
                    if (!value) {
                        return i18n._('None');
                    } else {
                        return value;
                    }
                };
            }
            columns.push(col);
            fields.push({ name: c.name });
        }

        var store = new Ext.data.Store({reader : new Ext.data.ArrayReader({},fields),remoteSort:true,/*fields: fields, */data: [] ,autoLoad: {params: {start: 0, limit: 40}} , proxy:new Ext.data.PagedMemoryProxy()}),
        pagingBar = new Ext.PagingToolbar({
            pageSize: 40,
            store: store,
            displayInfo: true,
            displayMsg: 'Displaying  items {0} - {1} of {2}',
            emptyMsg: "No items to display",

            items:['-']
        });
        var detailSection=new Ext.grid.GridPanel({
            title : section.title,
            enableHdMenu : false,
            enableColumnMove: false,
            store: store,
            columns: columns,
            tbar: [{
                tooltip:this.i18n._('Export Excel'),
                iconCls:'export-excel',
                handler: function() {
                    var rd = new Date(reports.reportsDate.time);
                    var d = rd.getFullYear() + "-" + (rd.getMonth() + 1) + "-" + rd.getDate();
                    var u = 'csv?date=' + d + '&app=' + appName + '&detail=' + section.name + '&numDays=' + reports.getAvailableReportsData()[0].numDays;
                    var t = store.initialData.drilldownType;
                    if (t) {
                        u += '&type=' + t;
                    }
                    var v = store.initialData.drilldownValue;
                    if (v) {
                        u += "&value=" + v;
                    }
                    window.open(u);
                }
            }],
            bbar : pagingBar,
            listeners: {
                'activate': function (panel){
                    if(panel.store.initialData.loaded ==false){
                        reports.progressBar.wait(i18n._("Please Wait"));
                        var store = panel.store;
                        rpc.reportingManager.getDetailData(function(result, exception) {
                            if (exception) {
                                if (!handleTimeout(exception)) {
                                    Ext.MessageBox.alert("Failed", exception.message);
                                }
                                return;
                            }

                            var data = [];

                            for (var i = 0; i < result.list.length; i++) {
                                data.push(result.list[i].list);
                            }
                            //store.reader.readRecords(data);
                            //store.reader.read({rows:data.length,list:data});
                            store.proxy.data = data;
                            store.load({params:{start:0, limit:40}});
                            //store.loadData(data);
                            store.initialData.loaded = true;
                            reports.progressBar.hide();
                        }.createDelegate(this), store.initialData.reportsDate, 1, store.initialData.selectedApplication, store.initialData.name, store.initialData.drilldownType, store.initialData.drilldownValue);
                    }
                }.createDelegate(this)
            }
        });
        store.initialData = {};
        if(section.name=='Summary Report'){
            store.initialData.loaded = true;
            rpc.reportingManager.getDetailData(function(result, exception) {
                if (exception) {
                    if (!handleTimeout(exception)) {
                        Ext.MessageBox.alert("Failed", exception.message);
                    }
                    return;
                }

                var data = [];

                for (var i = 0; i < result.list.length; i++) {
                    data.push(result.list[i].list);
                }

                store.loadData(data);
            }.createDelegate(this), reports.reportsDate, 1, reports.selectedApplication, section.name, rpc.drilldownType, rpc.drilldownValue);
        }else{
            store.initialData.loaded = false;
            store.initialData.reportsDate = reports.reportsDate;
            store.initialData.selectedApplication = reports.selectedApplication;
            store.initialData.name = section.name;
            store.initialData.drilldownType = rpc.drilldownType;
            store.initialData.drilldownValue = rpc.drilldownValue;
        }
        return detailSection;
    }
});

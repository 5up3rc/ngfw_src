// Copyright (c) 2006 Metavize Inc.
// All rights reserved.

function Portal(shell) {
   if (0 == arguments.length) {
      return;
   }

   this._shell = shell;

   this._shell.addControlListener(new AjxListener(this, this._shellListener));

   DwtComposite.call(this, this._shell, "Portal");

   this._loadApps();

   this._navBar = new NavigationBar(this);
   this._navBar.zShow(true);

   this._portalPanel = new PortalPanel(this);
   var l = new AjxListener(this, this._bookmarkSelectionListener);
   this._portalPanel.addSelectionListener(l);
   this._portalPanel.zShow(true);
   this._mainPanel = this._portalPanel;

   this.refresh();
   this.addControlListener(new AjxListener(this, this._controlListener));

   this.layout();
   this.zShow(true);
}

Portal.prototype = new DwtComposite();
Portal.prototype.constructor = Portal;

// portal api -----------------------------------------------------------------

Portal.prototype.showApplicationUrl = function(url, bookmark)
{
   this.removeChild(this._mainPanel);
   this._mainPanel = new ApplicationIframe(this, url);
   this._mainPanel.zShow(true);
   this.layout();

   this._navBar.applicationMode(bookmark);
}

Portal.prototype.splitUrl = function(url)
{
   var o = new Object();

   if (0 == url.indexOf("//")) {
      o.proto = location.protocol;
      if (":" == o.proto[o.proto.length - 1]) {
         o.proto = o.proto.substring(0, o.proto.length - 1);
      }
      var i = url.indexOf("/", 2);
      o.host = url.substring(2, i);
      o.path = url.substring(i);
   } else if (0 == url.indexOf("/")) {
      o.proto = location.protocol;
      if (":" == o.proto[o.proto.length - 1]) {
         o.proto = o.proto.substring(0, o.proto.length - 1);
      }
      o.host = location.host;
      o.path = url;
   } else {
      var i = url.indexOf(":");
      if (0 > i) {
         o.proto = location.protocol;
         if (":" == o.proto[o.proto.length - 1]) {
            o.proto = o.proto.substring(0, o.proto.length - 1);
         }
         o.host = location.hostname;
         var p = location.pathname;
         for (var k = p.length - 1; 0 <= k; k--) {
            if ("/" == p[k]) {
               p = p.substring(0, k + 1);
               break;
            }
         }
         o.path = p + url;
      } else {
         o.proto = url.substring(0, i);
         i = i + 3;
         var j = url.indexOf('/', i);
         o.host = url.substring(i, j);
         o.path = url.substring(j);
      }
   }

   return o;
}

// public methods -------------------------------------------------------------

Portal.prototype.showPortalPanel = function()
{
   if (this._mainPanel) {
      this._mainPanel.zShow(false)
      this._mainPanel.dispose();
      this._mainPanel = this._portalPanel;
   }
   this._portalPanel.zShow(true);
   this.layout();
}

Portal.prototype.refresh = function()
{
   this._loadApps();
   this._portalPanel.refresh();
}

Portal.prototype.layout = function()
{
   var size = this._shell.getSize();
   var width = size.x;
   var height = size.y;

   var x = 0;
   var y = 0;

   this._navBar.setLocation(x, y);
   var size = this._navBar.getSize();
   y += size.y;

   this._mainPanel.setBounds(x, y, width, height - y);
}

// init -----------------------------------------------------------------------

Portal.prototype._loadApps = function()
{
   AjxRpc.invoke(null, "secure/application?command=ls", null,
                 new AjxCallback(this, this._refreshAppsCallback,
                                 new Object()), true);
}

// shell ----------------------------------------------------------------------

Portal.prototype._shellListener = function(ev)
{
   if (ev.oldWidth != ev.newWidth || ev.oldHeight != ev.newHeight) {
      this.layout();
   }
}

// util -----------------------------------------------------------------------

Portal._mkSrcDestCommand = function(command, src, dest)
{
   var url = "exec?command=" + command;

   for (var i = 0; i < src.length; i++) {
      url += "&src=" + src[i].url; // XXX does this get escaped ?
   }

   url += "&dest=" + dest.url; // XXX does this get escaped ?

   return url;
}

// callbacks ------------------------------------------------------------------

Portal.prototype._controlListener = function()
{
   this.layout();
}

Portal.prototype._bookmarkSelectionListener = function(ev) {
   switch (ev.detail) {
      case DwtListView.ITEM_DBL_CLICKED:
      var item = ev.item;
      var app = this._appMap[item.app];
      // XXX if null?
      app.openBookmark(this, item);
      break;
   }
}

Portal.prototype._refreshAppsCallback = function(obj, results) {
   var root = results.xml.getElementsByTagName("applications")[0];

   var children = root.childNodes;

   this._apps = new Array();
   this._appMap = new Object();

   for (var i = 0; i < children.length; i++) {
      var child = children[i];

      if ("application" == child.tagName) {
         var name = child.getAttribute("name");
         var appJs = child.getElementsByTagName("appJs")[0].firstChild.data;
         var app = new Application(name, appJs);
         this._apps.push(app);
         this._appMap[name] = app;
         DBG.println("this._appMap[" + name + "] = " + app);
      }
   }
}

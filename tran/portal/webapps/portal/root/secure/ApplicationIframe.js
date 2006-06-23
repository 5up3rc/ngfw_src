// Copyright (c) 2006 Metavize Inc.
// All rights reserved.

function ApplicationIframe(parent)
{
   if (0 == arguments.length) {
      return;
   }

   DwtComposite.call(this, parent, "ApplicationIframe", DwtControl.ABSOLUTE_STYLE);
}

ApplicationIframe.prototype = new DwtComposite();
ApplicationIframe.prototype.constructor = ApplicationIframe;

// public methods -------------------------------------------------------------

ApplicationIframe.prototype.loadUrl = function(url)
{
   this.setContent("<iframe src='" + url + "'></iframe>");
}
<!--
 * Copyright (c) 2005 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
-->
<%@ taglib uri="/WEB-INF/taglibs/quarantine_euv.tld" prefix="quarantine" %>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>

<!-- HEADING -->
  <title>Metavize | Request Quarantine Digest Email</title>
  <meta http-equiv="Content-Type" content="text/html; charset=iso-8859-1"/>
  <link rel="stylesheet" href="styles/style.css" type="text/css"/>
</head>
<body>

<center>
<table border="0" cellpadding="0" cellspacing="0" width="904">

<!-- TOP THIRD -->
  <tbody>
    <tr>
      <td id="table_main_top_left"><img src="images/spacer.gif" alt=" " height="23" width="23"/><br/>
      </td>
      
      <td id="table_main_top" width="100%"><img src="images/spacer.gif" alt=" " height="1" width="1"/><br/>
      </td>

      <td id="table_main_top_right"> <img src="images/spacer.gif" alt=" " height="23" width="23"/><br/>
      </td>
    </tr>

    <!-- END TOP THIRD -->

    <!-- MIDDLE THIRD -->
    <tr>
      <td id="table_main_left"><img src="images/spacer.gif" alt=" " height="1" width="1"/></td>

      <td id="table_main_center">
        <table width="100%">
          <tbody>
            <tr>
              <td valign="middle" width="96">
                <a href="http://www.metavize.com">
                  <img src="images/logo_no_text_shiny_96x96.gif" border="0" alt="Metavize logo"/>
                </a>
              </td>
              
              <td style="padding: 0px 0px 0px 10px;" class="page_header_title" align="left" valign="middle">
                Request Quarantine Digest Email
              </td>
            </tr>
          </tbody>
        </table>
      </td>

      <td id="table_main_right"> <img src="images/spacer.gif" alt=" " height="1" width="1"/></td>
    </tr>

    <!-- END MIDDLE THIRD -->
    <!-- CONTENT AREA -->
    <tr>
      
      <td id="table_main_left"></td>

      <!-- CENTER CELL --> 
      <td id="table_main_center" style="padding: 8px 0px 0px;">
        <hr size="1" width="100%"/>

		<!-- INTRO MESSAGE -->
		This page is used to request an email to your inbox, listing any quarantined emails.<br/><br/>
                Please enter your email address into the form below.<br/>
		You will then receive an email with links for you to view your quarantined messages.<br/>
		You can then release or delete any of your quarantined messages.<br/>

		<!-- MAIN MESSAGE -->
		<br/>
		<center>
		<table>
              <quarantine:hasMessages type="info">
		<tr><td>
                  <ul class="messageText">
                    <quarantine:forEachMessage type="info">
                      <li><quarantine:message/></li>
                    </quarantine:forEachMessage>
                  </ul>
		</td></tr>
              </quarantine:hasMessages>
              <quarantine:hasMessages type="error">
		<tr><td>
                  <ul class="errortext">
                    <quarantine:forEachMessage type="error">
                      <li><quarantine:message/></li>
                    </quarantine:forEachMessage>
                  </ul>
		</td></tr>
              </quarantine:hasMessages>   
		</table>
		</center>

		<!-- INPUT FORM -->
            <form name="form1" method="POST" action="requestdigest">
		<center>
                  <table style="border: 1px solid; padding: 10px">
                    <tr>
                      <td>Email Address:&nbsp;
                      </td>
                      <td>
                        <input type="text" name="<quarantine:constants keyName="draddr"/>"/>
                      </td>
                    </tr>
                    <tr>
                      <td class="paddedButton" colspan="2" align="center">
                        <input type="submit" value="submit"/>
                      </td>
                    </tr>
                  </table>
		</center>
            </form>

		<br/>
	<center>Powered by Metavize&reg; EdgeGuard&reg;</center>

          <hr size="1" width="100%"/>
        </td>
      <!-- END CENTER CELL -->
      <td id="table_main_right"></td>
    </tr>
    <!-- END CONTENT AREA -->
    
    <!-- BOTTOM THIRD -->
    <tr>
      <td id="table_main_bottom_left"><img src="images/spacer.gif" alt=" " height="23" width="23"/><br/>
      </td>
      <td id="table_main_bottom"><img src="images/spacer.gif" alt=" " height="1" width="1"/><br/>
      </td>
      <td id="table_main_bottom_right"> <img src="images/spacer.gif" alt=" " height="23" width="23"/><br/>
      </td>
    </tr>
    <!-- END BOTTOM THIRD -->
  </tbody>
</table>

</center>

<!-- END BRUSHED METAL PAGE BACKGROUND -->

</body>
</html>




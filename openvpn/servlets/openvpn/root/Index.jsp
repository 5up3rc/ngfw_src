<%@page language="java" import="com.untangle.uvm.*"%>

<%
UvmLocalContext uvm = UvmContextFactory.context();
BrandingSettings bs = uvm.brandingManager().getBrandingSettings();
String company = bs.getCompanyName();
String companyUrl = bs.getCompanyUrl();

boolean isValid;
String debuggingMessages;
String commonName;

try {
isValid = (Boolean)request.getAttribute( Util.VALID_ATTR );
debuggingMessages = (String)request.getAttribute( Util.DEBUGGING_ATTR );
commonName = (String)request.getAttribute( Util.COMMON_NAME_ATTR );
if ( commonName == null ) {
  commonName = "";
  isValid = false;
}
} catch ( Exception e ) {
isValid = false;
debuggingMessages = "";
commonName = "";
/* If any of these occur there was an error processing the page, user is doing something wrong */
response.setStatus( HttpServletResponse.SC_FORBIDDEN );
}
%>

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
<title><%=company%> | OpenVPN</title>
<meta http-equiv="Content-Type" content="text/html; charset=iso-8859-1" />
<style type="text/css">
/* <![CDATA[ */
@import url(/images/base.css);
/* ]]> */
</style>
</head>
<body>
<div id="main">    
 <!-- Box Start -->
 <div class="main-top-left"></div><div class="main-top-right"></div><div class="main-mid-left"><div class="main-mid-right"><div class="main-mid">
 <!-- Content Start -->
	
	
	<div class="page_head">
		<a href="<%=companyUrl%>"><img src="/images/BrandingLogo.gif" alt="<%=company%> Logo" /></a> <div> OpenVPN<br />Client Download Utility</div>
	</div>
	
	
	
    <hr />
	
	
	
	<center>
	<div style="padding: 10px 0; margin: 0 auto; width: 440px;">
    <% if ( isValid ) { %>
               <span class="page_sub_title">Download</span>

	               <b>Common Name:</b>   <%= commonName %>
				   <br /><br />
				   
                  Please select one of the following files:

                  <a href="<%= response.encodeURL( "setup.exe" ) %>">Windows Installer</a><br />
                  <a href="<%= response.encodeURL( "config.zip" ) %>">Configuration Files</a><br />
    <% } else { // if ( isValid ) %>
            <span class="page_sub_title">Warning</span>
                  The files that you requested are no longer available,
                  please contact your network administrator for more information.
    <% } // else { if ( isValid ) %>
		
	</div>
	</center>

	
	
	<hr />

	
 <!-- Content End -->
 </div></div></div><div class="main-bot-left"></div><div class="main-bot-right"></div>
 <!-- Box End -->
</div>	

</body>
</html>


<jsp:useBean id="wikipage" scope="request" type="wiki.data.Page" />
<jsp:useBean id="message" scope="request" type="java.util.Map" class="java.util.HashMap" />

<html>
<head>
   <title>Wiki</title>
   <link rel="stylesheet" type="text/css" href="../styles.css" />
</head>
<body>

<table border="0" cellspacing="0" cellpadding="0">
   <tr>
      <td><img src="../logo.gif"></td>
   </tr>
   <tr>
      <td id="upper-bar"> 
         <div id="upper-menu">
            <a href="../edit/${wikipage.name}">edit</a>
            |
            <% 
               if (wikipage.isPublished()) {
            %>
            <a href="../unpublish/${wikipage.name}">unpublish</a>
            <%
               } else {
            %>
            <a href="../publish/${wikipage.name}">publish</a>
            <%
               }
            %>
         </div>
      </td>
   </tr>
   <tr>
      <td id="lower-bar">
         <div id="layout">
         <%
         	if(message.containsKey("message")) {
         		out.println("<span>" + message.get("message") + "</span>");
         	}
         %>
            ${wikipage.content}
         </div>
      </td>
   </tr>
</table>

</body>
</html>
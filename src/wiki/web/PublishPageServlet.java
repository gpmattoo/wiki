package wiki.web;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.Socket;
import java.util.HashMap;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.output.XMLOutputter;

import wiki.data.Page;
import wiki.data.PageDAO;

public class PublishPageServlet extends HttpServlet {
	
	private Logger logger = Logger.getLogger(this.getClass());
	private RequestDispatcher jsp;
	private RequestDispatcher jsp2;
	
	public void init(ServletConfig config) throws ServletException {
		
		ServletContext context = config.getServletContext();
		jsp = context.getRequestDispatcher("/WEB-INF/jsp/publish-page.jsp");
		jsp2 = context.getRequestDispatcher("/WEB-INF/jsp/view-page.jsp");
	}
	
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		
		logger.debug("doGet for publish page");
		String pathInfo = req.getPathInfo();
		String name = pathInfo.substring(1);
		logger.debug("Page requested: " + name);
		Page page = new PageDAO().find(name);
		if(page == null) {
			logger.debug("page doesn't exist, creating an empty page");
			page = new Page();
			page.setName(name);
			page.setContent("");
			page.setPublished(false);
		}
		req.setAttribute("wikipage", page);
		jsp.forward(req, resp);
	}
	
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		
		logger.debug("doPost for publish page");
		
		//extract form data
		String pageName = req.getParameter("name");
		PageDAO pageDAO = new PageDAO();
		Page page = pageDAO.find(pageName);
		
		//check for cancel button
		String cancelButton = req.getParameter("cancel-button");
		if(cancelButton != null) {
			//resp.sendRedirect("../view/" + pageName);
			HashMap<String, String> message = new HashMap<String, String>();
			req.setAttribute("message", message);
			req.setAttribute("wikipage", page);
			message.put("message", "CANCELLED");
			jsp2.forward(req, resp);
			return;
		}
				
		//don't do anything if page doesn't exist or is already published
		if(page == null || page.isPublished()){
			resp.sendRedirect("../view/" + pageName);
			return;
		}
		
		//invoke remote web service to publish the page
		logger.debug("invoking web service");
		try{
			String publishedId = publish(page);
			page.setPublishedId(publishedId);
			page.setPublished(true);
		} catch(Exception e) {
			logger.error(e);
			throw new RuntimeException(e);
		}
		
		//update page
		pageDAO.update(page);
		resp.sendRedirect("../view/" + page.getName());	
	}
	
	private String publish(Page page) throws IOException{
		
		logger.debug("publish()");
		String pageName = page.getName();
		String pageUrl = "http://localhost:8080/wiki/view/" + pageName;
		
		SAXBuilder builder = new SAXBuilder();
		Element titleElement = new Element("title");
		titleElement.addContent(pageName);
		Element linkElement = new Element("link");
		linkElement.addContent(pageUrl);
		
		Element root = new Element("item");
		root.addContent(titleElement);
		root.addContent(linkElement);
		Document document = new Document(root);
		StringWriter sw = new StringWriter();
		XMLOutputter outputter = new XMLOutputter();
		//write the xml document to the writer
		outputter.output(document, sw);
		//this string which contains the xml document will be sent to the publish service
		String docString = sw.toString();
		
		byte[] docBytes = docString.getBytes("UTF-8");
		String contentLengthHeader = "Content-length: " + docBytes.length + "\r\n";
		String contentTypeHeader = "Content-type: text/xml\r\n";
		String hostHeader = "Host: localhost\r\n";
		String connectionHeader = "Connection: close\r\n";
		String requestLine = "POST /NewsFeedPublisher/publish HTTP/1.1\r\n";
		
		//send the HTTP request message to the publisher application
		Socket socket = new Socket("localhost", 8080);
		OutputStream os =socket.getOutputStream();
		os.write(requestLine.getBytes("US-ASCII"));
	    os.write(hostHeader.getBytes("US-ASCII"));
	    os.write(contentTypeHeader.getBytes("US-ASCII"));
	    os.write(contentLengthHeader.getBytes("US-ASCII"));
	    os.write(connectionHeader.getBytes("US-ASCII"));
        os.write("\r\n".getBytes("US-ASCII"));
	    os.write(docBytes);
	    os.flush();

	    //read the response message returned by the service
	    InputStream is = socket.getInputStream();
	    //convert the incoming stream of bytes into a stream of characters.
	    InputStreamReader isr = new InputStreamReader(is);
	    //wrap the input stream reader in a buffered reader, which allows us to read a line at a time
	    BufferedReader br = new BufferedReader(isr);
	    
	    //read through the header lines of the HTTP response message
	    while(true) {
	    	String line = br.readLine();
	    	if(line.length() == 0)
	    		break;
	    }
	    
	    Document responseDoc = null;
	    try {
	    	//parse the XML document that the service is returning.
	    	responseDoc = builder.build(br);
	    } catch(JDOMException jdome) {
	    	throw new RuntimeException(jdome);
	    }
	    
	    //extract the text contents of its root element to obtain the id of the newly created news item
	    Element idElement = responseDoc.getRootElement();
	    String id = idElement.getText();
	    br.close();
	    
	    return id;
	}
}
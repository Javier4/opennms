package org.opennms.netmgt.notifd;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class MattermostNotificationStrategyTestServlet extends HttpServlet {

	private static final long serialVersionUID = 2125954127457631594L;
	private static JSONObject m_inputJson;

    @Override protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        if (! "application/json".equals(req.getContentType())) {
        	squawk(resp, "Invalid content type " + req.getContentType());
        }
        
        m_inputJson = null;
        JSONParser jp = new JSONParser();
        try {
			Object inStuff = jp.parse(req.getReader());
			if (inStuff instanceof JSONObject) {
				m_inputJson = (JSONObject)inStuff;
			}
		} catch (ParseException e1) {
			squawk(resp, "Input is not well-formed JSON");
			return;
		}
        
        if ((! m_inputJson.containsKey("text")) || "".equals(m_inputJson.get("text"))) {
        	squawk(resp, "No text specified");
        	return;
        }
        
        if ((! m_inputJson.containsKey("username")) || "".equals(m_inputJson.get("username"))) {
        	squawk(resp, "No username specified");
        	return;
        }
        
        final String responseText = "ok";
        final ServletOutputStream os = resp.getOutputStream();
        os.print(responseText);
        os.close();
        resp.setContentType("text/plain");
        resp.setContentLength(responseText.length());
    }
    
    @SuppressWarnings("unchecked")
	private void squawk(final HttpServletResponse resp, String reason) throws IOException {
    	JSONObject errorJson = new JSONObject();
    	errorJson.put("message", reason);
    	errorJson.put("detailed_error", "");
    	errorJson.put("request_id", "deadbeefcafebabe");
    	errorJson.put("status_code", 500);
    	errorJson.put("isoauth", false);
    	
    	final String responseText = errorJson.toJSONString();
    	final ServletOutputStream os = resp.getOutputStream();
    	os.print(responseText);
    	os.close();
    	resp.setContentType("application/json");
    	resp.setContentLength(responseText.length());
    	resp.setStatus(500);
    }
    
    public static JSONObject getInputJson() {
    	return m_inputJson;
    }
}
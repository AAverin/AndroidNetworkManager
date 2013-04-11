package aaverin.android.net;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import nimbleschedule.android.ApplicationContext;
import nimbleschedule.android.CurrentUser;
import nimbleschedule.android.sql.models.DBNetworkMessage;

import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;

import com.google.gson.JsonObject;


public class NetworkMessage {
	
	public static interface MessageParameters {
		public final static String JSON_DATA = "json_data";
	}

	private String method;
	private URI uri;
	private List<NameValuePair> parametersList = null;
	private String rawPostBody = null;
	private boolean isCacheable = true;
	
	private HttpRequestBase request = null;
	
	private boolean deleteOnFailure = false;
	
	public NetworkMessage(boolean deleteOnFailure) {
		this.deleteOnFailure = deleteOnFailure;
	}
	
	public NetworkMessage(DBNetworkMessage dbMessage, boolean deleteOnFailure) {
		this.uri = URI.create(dbMessage.getUri());
		this.method = dbMessage.getMethod();
		this.deleteOnFailure = deleteOnFailure;
	}
	
	public boolean shouldDeleteOnFailure() {
	    return deleteOnFailure;
	}
	
	public void setMethod(String method) {
		this.method = method;
	}
	
	public void setURI(URI uri) {
		this.uri = uri;
	}
	
	public void setParametersList(List<NameValuePair> parameters) {
		this.parametersList = parameters;
	}
	
	public List<NameValuePair> getParametersList() {
		return this.parametersList;
	}
	
	public void setRawPostBody(String rawString) {
		this.rawPostBody = rawString;
	}
	
	public String getRawPostBody() {
		return this.rawPostBody;
	}
	
	public String getMethod() {
	    return this.method;
	}
	
	public URI getURI() {
	    return this.uri;
	}
	
	public void setCacheable(boolean cacheable) {
		isCacheable = cacheable;
	}
	
	public boolean isCacheable() {
		return isCacheable;
	}
	
	public String asJson() {
		//TODO: reimplement asJson()
//		HashMap<String, String> packedMessage = new HashMap<String, String>();
//		packedMessage.put("uri", this.uri.toString());
//		packedMessage.put("encryptedData", this.data);
//		return MessageBuilder.getInstance().mapToJson(packedMessage);
		return "";
	}
	
	public DBNetworkMessage asDbMessage() {
		//TODO add new parameters for db storing
		//return new DBNetworkMessage(this.uri.toString(), this.data, this.method, this.deleteOnFailure);
		return null;
	}
	
	public HttpRequestBase getHttpRequest() {
        try {
            if (request == null) {
            	if (this.parametersList == null) {
                	this.parametersList = new ArrayList<NameValuePair>();
                }

                //WARNING: This is really not the best practice, even on HTTPS. Authorization header with encrypted data should be used instead
                CurrentUser currentUser = ApplicationContext.getInstance().getCurrentUser();
                if (currentUser != null && currentUser.getEmployeeInfo() != null) {
                	parametersList.add(new BasicNameValuePair("username", currentUser.getUsername()));
                    parametersList.add(new BasicNameValuePair("password", currentUser.getPassword()));                	
                }
                
                if (method.equals("PUT")) {
                    request = new HttpPut(uri);
                } else if (method.equals("POST")) {
                	String url = uri.toString() + "/?" + URLEncodedUtils.format(this.parametersList, "UTF-8").replace("%3A", ":");
                    request = new HttpPost(url);
                    if (this.rawPostBody != null) {
                    	((HttpEntityEnclosingRequestBase) request).setEntity(new StringEntity(this.rawPostBody, HTTP.UTF_8));
                    }
                    //Leftowers of pervious sending method. Not used in this project.
//                    else {
//                    	((HttpEntityEnclosingRequestBase) request).setEntity(new UrlEncodedFormEntity(this.parametersList));                    	
//                    }

                } else if (method.equals("GET")) {
                	String url = uri.toString() + "/?" + URLEncodedUtils.format(this.parametersList, "UTF-8").replace("%3A", ":");
                    request = new HttpGet(url);
                }
            }
        } catch (UnsupportedEncodingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return request;
	}
	
	public URLConnection getHttpURLConnection() {
	    URLConnection connection = null;
	    try {
	    	
	    	if (this.parametersList == null) {
            	this.parametersList = new ArrayList<NameValuePair>();
            }
	    	
	    	//WARNING: This is really not the best practice, even on HTTPS. Authorization header with encrypted data should be used instead
	    	if (!this.uri.toString().contains(NetworkURIs.AUTHENTICATE_URI)) {
	    		CurrentUser currentUser = ApplicationContext.getInstance().getCurrentUser();
	            if (currentUser != null && currentUser.getEmployeeInfo() != null) {
	            	parametersList.add(new BasicNameValuePair("username", currentUser.getUsername()));
	                parametersList.add(new BasicNameValuePair("password", currentUser.getPassword()));                	
	            }	    		
	    	}
             
	        String query = URLEncodedUtils.format(this.parametersList, "UTF-8").replace("%3A", ":");
	        
            if (method.equals("GET")) {
                String url = uri.toString() + "/?" + query;
                connection = new URL(url).openConnection();;
                if (!isCacheable()) {
                	connection.addRequestProperty("Cache-Control", "no-cache");
                }
            } else if (method.equals("POST")) {
            	String url = uri.toString() + "/?" + query;
                connection = new URL(url.toString()).openConnection();
                if (!isCacheable()) {
                	connection.addRequestProperty("Cache-Control", "no-cache");
                }
                connection.setDoOutput(true); // Triggers POST.
                connection.setRequestProperty("Accept-Charset", "UTF-8");
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=" + "UTF-8");
                OutputStream output = null;
                try {
                     output = connection.getOutputStream();
                     if (this.rawPostBody != null) {
                    	 output.write(this.rawPostBody.getBytes("UTF-8"));
                     } else {
                    	 output.write(query.getBytes("UTF-8"));                    	 
                     }
                } finally {
                     if (output != null) try { output.close(); } catch (IOException logOrIgnore) {}
                }
            }
	    } catch (UnsupportedEncodingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (MalformedURLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
	    return connection;
    }
}

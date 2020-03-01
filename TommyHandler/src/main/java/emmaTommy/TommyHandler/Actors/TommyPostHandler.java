package emmaTommy.TommyHandler.Actors;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.typed.PostStop;
import emmaTommy.TommyHandler.ActorsMessages.PostData;
import emmaTommy.TommyHandler.ActorsMessages.startPosting;


public class TommyPostHandler extends AbstractActor {
	
	protected org.apache.logging.log4j.Logger logger = LogManager.getLogger(this.getClass().getSimpleName());
	
	protected String tommyURL;
	protected String associazione;
	protected String servizioRestName;
	protected String username;
	protected String psswd;
	protected int startingServizioCode;
	protected Boolean POST;
	
	public static Props props(String text, String confPath) {
        return Props.create(TommyPostHandler.class, text, confPath);
    }

	private TommyPostHandler(String confPath) {
		
		// Logger Method Name
		String method_name = "::TommyPostHandler(): ";
		
		// Define and Load Configuration File
		Properties props = new Properties();
		logger.trace(method_name + "Loading Properties FileName: " + confPath);
		FileInputStream fileStream = null;
		try {
			fileStream = new FileInputStream(confPath);
		} catch (FileNotFoundException e) {
			logger.fatal(method_name + e.getMessage());
		}
		try {
			props.load(fileStream);
		    logger.trace(method_name + props.toString());
		} catch (IOException e) {
			logger.fatal(method_name + e.getMessage());
		}
		
		// Load Configuration Data
		this.tommyURL = props.getProperty("tommyURL");
		this.associazione = props.getProperty("associazione");
		this.servizioRestName = props.getProperty("servizioRestName");
		this.username = props.getProperty("username");
		this.psswd = props.getProperty("psswd");
		this.startingServizioCode = Integer.parseInt(props.getProperty("startingServizioCode"));
		
		// Set Conversion cycle to false
		this.POST = false;
		
	}
	
	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(startPosting.class, this::onStart)
				.match(PostData.class, this::postElaborator)
				.match(PostStop.class, signal -> onPostStop())
				.match(String.class, s -> {
					logger.info(this.getClass().getSimpleName() + " Received String message: {}", s);
	             })
				.matchAny(o -> logger.warn(this.getClass().getSimpleName() + " received unknown message"))
				.build();
	}
	
	protected void onStart(startPosting startPost) {
		
		// Logger Method Name
		String method_name = "::onStart(): ";
		logger.info(method_name + "Received Start Posting Event");
		
		
	}
	
	protected void postElaborator(PostData postData) {
		String method_name = "::consume(): ";
		logger.trace(method_name + "Received a post msg");
		
		String json = postData.getJsonServizi();
		
		// Build url
		String restUrl = tommyURL 
				+ "/" + associazione 
				+ "/" + servizioRestName + "/" + "run.php?" 
				+ "&user=" + username 
				+ "&pwd=" + psswd 
				+ "&json=" + json;
		
		try {
            URI uri = new URI(restUrl);
            logger.trace(method_name + "URI created: " + uri.toString());
            try {    			
            	String response = this.post(uri, json);
     			logger.info(method_name + "Rest Service Answer: " + response);  
            } catch (MalformedURLException e) {
            	logger.error(method_name + "Url Malformed Error: " + e.getMessage());
    		} catch (IOException e) {
    			logger.error("Failed to post the following servizi for automezzo " + postData.getCodiceMezzo());
    			logger.error(method_name + e.getMessage());
    		}
           
        }
        catch (URISyntaxException e) {
        	logger.error(method_name + "URI Syntax Error: " + e.getMessage());
        }
		
	}	
	
	protected String post(URI restUri, String jsonServizi) throws MalformedURLException, IOException {
		String method_name = "::post(): ";
        try {
            URL restUrl = restUri.toURL();
            logger.trace(method_name + "URL from URI: " + restUrl);
            return this.post(restUrl, jsonServizi);
        }
        catch (MalformedURLException e) {
            throw e;
        }
		
	}
	
	protected String post(URL restUrl, String jsonServizi) throws IOException {
		
		String method_name = "::post(): ";
		logger.trace(method_name + "Posting to " + this.tommyURL);
		logger.trace(restUrl);
		
		// Create Connection
		URLConnection connection = restUrl.openConnection();
		connection.setDoOutput(true); // Triggers POST action
		connection.setRequestProperty("Content-Type", "application/json");
		// connection.setConnectTimeout(5000);
		// connection.setReadTimeout(5000);
		try (OutputStream output = connection.getOutputStream()) {
			OutputStreamWriter out = new OutputStreamWriter(connection.getOutputStream());
			out.write(jsonServizi);
			out.close();
		}
		
		// Connect
		logger.info(method_name + "Sending POST Request");
		connection.connect();
		
		// Analize Response		
		HttpURLConnection httpConnection = (HttpURLConnection) connection;
		int responseCode = httpConnection.getResponseCode();
		if (responseCode == HttpURLConnection.HTTP_OK) {
			logger.info(method_name + "Http Request Status: " + responseCode);
		} else {
			logger.error(method_name + "Http Request Status: " + responseCode);
		}
		/**
		logger.trace(method_name + "Loggin Headers");
		
		for (Entry<String, List<String>> header : connection.getHeaderFields().entrySet()) {
		    logger.info(method_name + header.getKey() + "=" + header.getValue());
		}
		*/
		
		// Build Response String
		InputStream responseInputStream = connection.getInputStream();
		InputStreamReader responseInputStreamReader = new InputStreamReader(responseInputStream);
		BufferedReader responseInputStreamBufferReader = new BufferedReader(responseInputStreamReader);
	    StringBuffer responseStringBuffer = new StringBuffer();
	    String str;
        while((str = responseInputStreamBufferReader.readLine())!= null){
        	responseStringBuffer.append(str);
        }
		return responseStringBuffer.toString();
		
		// Error: "tipo":"ERR"
		
	}
	
	protected void onPostStop() {
		String method_name = "::onPostStop(): ";
		logger.info(method_name + "Received Stop Event");		
		
	}
	
	
	public static void main(String[] args) {
		
		// Logger
		String method_name = "::main(): ";
		org.apache.logging.log4j.Logger logger = LogManager.getLogger("TommyPostHandler");
		
		// Create Actor System
		logger.info(method_name + "Creating ActorSystem ...");
		ActorSystem system = ActorSystem.create("test-system");
		logger.info(method_name + system.name() + " ActorSystem is Active");
		
		// Create TommyPostHandler Actor
		logger.info(method_name + "Creating TommyPostHandler Actor ...");
		ActorRef tommyPoster = system.actorOf(Props.create(TommyPostHandler.class, "../conf/tommy_refs.conf"), "TommyPostHandler");
		logger.info(method_name + " TommyPostHandler Actor is Active");
		
		// Send Start to TommyPostHandler
		logger.info(method_name + "Sending TommyPostHandler Actor the Start Posting Msg ...");
		tommyPoster.tell(new startPosting(), ActorRef.noSender());
		logger.info(method_name + "Sent :)");
				
		try {
			
			//Path path = Paths.get("../docs/RestTommy/test.json");
			// int servizioCode = 213000000;
			int servizioCode = 213003231;
			String codiceMezzo = "VOLCAL_106";
			Path path = Paths.get("../data_json_test/" + servizioCode + ".json");
			String json = Files.readString(path, StandardCharsets.US_ASCII);
			tommyPoster.tell(new PostData(codiceMezzo, servizioCode, json), ActorRef.noSender());
			
			
		} catch (Exception e) {
			logger.error(method_name + "Error while Posting: " + e.getMessage());
		}
 
			
	}
}

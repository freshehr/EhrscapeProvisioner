package ehrscapeProvisioner.model;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.bean.CsvToBean;
import au.com.bytecode.opencsv.bean.HeaderColumnNameTranslateMappingStrategy;
import ehrscapeProvisioner.ImportCsvResource;

/**
 * This class contains methods for accessing Ehrscape/openEHR API by generating HTTP requests, 
 * and methods for moving and manipulating dummy data assets from the resources folder where they are
 * needed in said requests
 */
public class EhrscapeRequest {

	Gson gson = new Gson();

	HttpClient client = HttpClientBuilder.create().build();
	
	public EhrscapeConfig config = new EhrscapeConfig(); //For passing info to subsequent requests
	
	/**
	 * Gets a resource file and outputs its contents as a String
	 * @param fileName File to get
	 * @return String - Contents of file
	 */
	public String getFileAsString(String fileName) {

		StringBuilder result = new StringBuilder("");

		// Get file from resources folder
		ClassLoader classLoader = getClass().getClassLoader();
		
		// Use URLDecoder.decode() on the pathname string below if dealing with
		// whitespace in the filenames
		// however this slowed down requests a lot so instead try and avoid
		// whitespace in filenames.
		File file = new File(classLoader.getResource(fileName).getFile());

		try {
			BufferedReader bReader = new BufferedReader(new FileReader(file));

			String line;
			while ((line = bReader.readLine()) != null) {
				result.append(line).append("\n"); 
			}
			bReader.close();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}

		return result.toString();

	}

	// OPENEHR / EHRSCAPE REQUESTS

	/**
	 * Creates an EHR-Session by sending a POST request to the configured baseUrl which 
	 * locates the domain server
	 * @param username String
	 * @param password String
	 * @return HTTP Response Object
	 * @throws ClientProtocolException
	 * @throws IOException
	 * @throws URISyntaxException
	 */
	public Response getSession(String username, String password)
			throws ClientProtocolException, IOException, URISyntaxException {

		String url; 

		config.setUsername(username);
		config.setPassword(password);

		URIBuilder ub = new URIBuilder(config.getBaseUrl() + "session");
		ub.addParameter("username", username);
		ub.addParameter("password", password);
		url = ub.toString();

		HttpPost request = new HttpPost(url);

		HttpResponse response = client.execute(request);

		HttpEntity entity = response.getEntity();
		String result = EntityUtils.toString(entity);

		if (response.getStatusLine().getStatusCode() == 201 || response.getStatusLine().getStatusCode() == 200) {
			JsonObject jsonObject = (new JsonParser()).parse(result.toString()).getAsJsonObject();
			config.setSessionId(jsonObject.get("sessionId").getAsString());
			return Response.status(response.getStatusLine().getStatusCode()).entity(result)
					.type(MediaType.APPLICATION_JSON).build();

		} else {
			return Response.status(response.getStatusLine().getStatusCode()).entity(result)
					.type(MediaType.APPLICATION_JSON).build();
		}

	}
	
	
	/**
	 * "Pings" a Session to refresh it by sending a PUT request with the SessionID to the specified domain - can be used to 
	 * verify a session token too
	 * @param sessionId String
	 * @return HTTP Response Object
	 * @throws URISyntaxException
	 * @throws ClientProtocolException
	 * @throws IOException
	 */
	public Response pingSession(String sessionId) throws URISyntaxException, ClientProtocolException, IOException {
		String url;
		URIBuilder ub = new URIBuilder(config.getBaseUrl() + "session");
		url = ub.toString();
		HttpPut request = new HttpPut(url);
		request.addHeader("Ehr-Session", sessionId);
		HttpResponse response = client.execute(request);
		int responseCode = response.getStatusLine().getStatusCode();
		String result;
		if (responseCode == 204) {
			// indicates session exists and has been refreshed
			JsonObject jsonResponse = new JsonObject();
			jsonResponse.addProperty("Ehr-session", sessionId);
			result = jsonResponse.toString();
		} else {
			// if session doesn't exist
			HttpEntity entity = response.getEntity();
			result = EntityUtils.toString(entity);
		}
		return Response.status(responseCode).entity(result).type(MediaType.APPLICATION_JSON).build();
	}

	// create patient demographic
	/**
	 * Create a Marand Demographic patient resource with a POST request to the Marand service, 
	 * with only a Marand JSON formatted party object 
	 * specifying the patients personal information.
	 * @param body String marand JSON party 
	 * @return Response Object
	 * @throws ClientProtocolException
	 * @throws IOException
	 * @throws URISyntaxException
	 */
	public Response createMarandPatientDemographic(String body)
			throws ClientProtocolException, IOException, URISyntaxException {
		URIBuilder ub = new URIBuilder(config.getBaseUrl() + "demographics/party");
		String url = ub.toString(); 
		HttpPost request = new HttpPost(url);
		request.addHeader("Ehr-Session", config.getSessionId());
		request.addHeader("Content-Type", "application/json");
		request.setEntity(new StringEntity(body));
		HttpResponse response = client.execute(request);

		HttpEntity entity = response.getEntity();
		String result = EntityUtils.toString(entity);

		if (response.getStatusLine().getStatusCode() == 200 || response.getStatusLine().getStatusCode() == 201) {
			JsonObject jsonObject = (new JsonParser()).parse(result.toString()).getAsJsonObject();
			JsonObject jsonSubObject = jsonObject.getAsJsonObject("meta");
			String partyStringHref = jsonSubObject.get("href").getAsString();
			String partyID = partyStringHref.substring(partyStringHref.lastIndexOf("/") + 1);
			config.setSubjectId(partyID);
			return Response.status(response.getStatusLine().getStatusCode()).entity(result)
					.type(MediaType.APPLICATION_JSON).build();
		} else {
			JsonObject jsonResponse = new JsonObject();
			jsonResponse.addProperty("error-message", "demographic not created");
			jsonResponse.addProperty("response-code", response.getStatusLine().getStatusCode());
			jsonResponse.addProperty("message", response.getStatusLine().getReasonPhrase());
			result = jsonResponse.toString();
			return Response.status(response.getStatusLine().getStatusCode()).entity(result)
					.type(MediaType.APPLICATION_JSON).build();
		}
	}

	// create patient demographic
	
	/**
	 * Create a "default" Marand patient demographic reosurce - used in single patient provisioner for convenience
	 * by sending a POST request to this Marand service
	 * @return HTTP Response object
	 * @throws ClientProtocolException
	 * @throws IOException
	 * @throws URISyntaxException
	 */
	public Response createPatientDefault() throws ClientProtocolException, IOException, URISyntaxException {
		String body = getFileAsString("assets/sample_requests/party.json");
		Response response = createMarandPatientDemographic(body);
		return response;
	}
	
	
	/**
	 * Create an EHR given a SubjectID to uniquely identify the owner, and a commiter name, by sending a POST request to the domain
	 * @param subjectID String
	 * @param commiter String
	 * @return HTTP Response object
	 * @throws ClientProtocolException
	 * @throws IOException
	 * @throws URISyntaxException
	 */
	public Response createEhr(String subjectID, String commiter)
			throws ClientProtocolException, IOException, URISyntaxException {
		String url; 

		URIBuilder ub = new URIBuilder(config.getBaseUrl() + "ehr");
		ub.addParameter("subjectId", subjectID);
		ub.addParameter("subjectNamespace", config.getSubjectNamespace());
		ub.addParameter("commiterName", commiter);
		url = ub.toString();

		HttpPost request = new HttpPost(url);
		request.addHeader("Ehr-Session", config.getSessionId());
		HttpResponse response = client.execute(request);
		int responseCode = response.getStatusLine().getStatusCode();
		if (responseCode == 200 || responseCode == 201) {
			HttpEntity entity = response.getEntity();
			String result = EntityUtils.toString(entity);

			JsonObject jsonObject = (new JsonParser()).parse(result.toString()).getAsJsonObject();

			config.setEhrId(jsonObject.get("ehrId").getAsString());
			config.setSubjectId(subjectID);
			return Response.status(responseCode).entity(result).type(MediaType.APPLICATION_JSON).build();
		} else {
			HttpEntity entity = response.getEntity();
			String result = EntityUtils.toString(entity);
			return Response.status(responseCode).entity(result).type(MediaType.APPLICATION_JSON).build();
		}

	}

	/**
	 * Retrieve an EHR given a subjectID and subject namespace with a GET request
	 * @param subjectId String
	 * @param subjectNamespace String
	 * @return HTTP Response object
	 * @throws URISyntaxException
	 * @throws ClientProtocolException
	 * @throws IOException
	 */
	public Response getEhrWithSubjectId(String subjectId, String subjectNamespace)
			throws URISyntaxException, ClientProtocolException, IOException {
		String url;
		URIBuilder ub = new URIBuilder(config.getBaseUrl() + "ehr");
		ub.addParameter("subjectId", subjectId);
		ub.addParameter("subjectNamespace", subjectNamespace);
		url = ub.toString();

		HttpGet request = new HttpGet(url);
		request.addHeader("Ehr-Session", config.getSessionId());
		HttpResponse response = client.execute(request);
		int responseCode = response.getStatusLine().getStatusCode();
		String result;

		if (responseCode == 200) {
			HttpEntity entity = response.getEntity();
			result = EntityUtils.toString(entity);
			JsonObject jsonObject = (new JsonParser()).parse(result.toString()).getAsJsonObject();
			config.setEhrId(jsonObject.get("ehrId").getAsString());
			return Response.status(responseCode).entity(result).type(MediaType.APPLICATION_JSON).build();
		} else {
			JsonObject jsonResult = new JsonObject();
			jsonResult.addProperty("Ehrscape Request", "Get Ehr with SubjectId and Namespace");
			jsonResult.addProperty("Response Status", responseCode);

			return Response.status(responseCode).entity(jsonResult.toString()).type(MediaType.APPLICATION_JSON).build();
		}
	}

	/**
	 * Get an ehr given an EhrID with a GET request
	 * @param ehrId String
	 * @return HTTP Response object
	 * @throws URISyntaxException
	 * @throws ClientProtocolException
	 * @throws IOException
	 */
	public Response getEhrWithEhrId(String ehrId) throws URISyntaxException, ClientProtocolException, IOException {
		String url;
		URIBuilder ub = new URIBuilder(config.getBaseUrl() + "ehr/" + ehrId);
		url = ub.toString();
		HttpGet request = new HttpGet(url);
		request.addHeader("Ehr-Session", config.getSessionId());
		HttpResponse response = client.execute(request);
		int responseCode = response.getStatusLine().getStatusCode();
		String result;
		HttpEntity entity = response.getEntity();
		result = EntityUtils.toString(entity);
		return Response.status(responseCode).entity(result).type(MediaType.APPLICATION_JSON).build();
	}

	/**
	 * Update an EHR given a new EHR body string and the ID of the record to update, using a PUT request
	 * @param body String new EHR contents
	 * @param ehrId String
	 * @return HTTP Response object
	 * @throws URISyntaxException
	 * @throws ClientProtocolException
	 * @throws IOException
	 */
	public Response updateEhr(String body, String ehrId)
			throws URISyntaxException, ClientProtocolException, IOException {
		String url;
		URIBuilder ub = new URIBuilder(config.getBaseUrl() + "ehr/status/" + ehrId);
		url = ub.toString();
		HttpPut request = new HttpPut(url);
		request.addHeader("Ehr-Session", config.getSessionId());
		request.addHeader("Content-Type", "application/json");
		request.setEntity(new StringEntity(body));
		HttpResponse response = client.execute(request);
		int responseCode = response.getStatusLine().getStatusCode();
		String result;
		if (responseCode == 200) {
			HttpEntity entity = response.getEntity();
			result = EntityUtils.toString(entity);
		} else {
			HttpEntity entity = response.getEntity();
			result = EntityUtils.toString(entity);
		}
		return Response.status(responseCode).entity(result).type(MediaType.APPLICATION_JSON).build();
	}

	// templates
	
	/**
	 * Upload a template onto a domain given its XML representation as a String, using a POST request
	 * @param body String template XML
	 * @return
	 * @throws IOException
	 * @throws URISyntaxException
	 */
	public Response uploadTemplate(String body) throws IOException, URISyntaxException {
		
		String url;

		URIBuilder ub = new URIBuilder(config.getBaseUrl() + "template/");
		url = ub.toString();

		HttpPost request = new HttpPost(url);
		request.addHeader("Ehr-Session", config.getSessionId());
		request.addHeader("Content-Type", "application/xml");
		request.setEntity(new StringEntity(body));

		HttpResponse response = client.execute(request);
		int responseCode = response.getStatusLine().getStatusCode();

		if (responseCode == 200 || responseCode == 201) {
			HttpEntity entity = response.getEntity();
			String result = EntityUtils.toString(entity);
			return Response.status(responseCode).entity(result).type(MediaType.APPLICATION_JSON).build();
		} else {
			HttpEntity entity = response.getEntity();
			String result = EntityUtils.toString(entity);
			return Response.status(responseCode).entity(result).type(MediaType.APPLICATION_JSON).build();
		}

	}
	
	
	/**
	 * Upload the 'default' template used (vitals) used in single patient provisioning - convenience method
	 * @return HTTP Response object
	 * @throws IOException
	 * @throws URISyntaxException
	 */
	public Response uploadDefaultTemplate() throws IOException, URISyntaxException {
		String body = getFileAsString("assets/sample_requests/vital-signs/vital-signs-template.xml");
		Response response = uploadTemplate(body);
		return response;
	}

	// Composition

	// for the import csv tool
	
	/**
	 * Upload a composition given all the attributes required by the API - this method is for the 
	 * import CSV tool which needs to enter these manually not using a config object, and for uploading
	 * compositions which are not describing vitals - needed in the multi patient provisioning scripts
	 * @param body Composition JSON representation
	 * @param sessionId
	 * @param templateId of the template this composition fits into to
	 * @param commiterName
	 * @param ehrId
	 * @return HTTP Response Object
	 * @throws URISyntaxException
	 * @throws ClientProtocolException
	 * @throws IOException
	 */
	public Response uploadComposition(String body, String sessionId, String templateId, String commiterName,
			String ehrId) throws URISyntaxException, ClientProtocolException, IOException {
		String url;
		URIBuilder ub = new URIBuilder(config.getBaseUrl() + "composition");
		ub.addParameter("ehrId", ehrId);
		ub.addParameter("templateId", templateId);
		ub.addParameter("format", "FLAT");
		ub.addParameter("comitterId", commiterName);
		url = ub.toString();
		HttpPost request = new HttpPost(url);
		request.addHeader("Ehr-Session", sessionId);
		request.addHeader("Content-Type", "application/json");
		request.setEntity(new StringEntity(body));
		HttpResponse response = client.execute(request);
		int responseCode = response.getStatusLine().getStatusCode();
		if (responseCode == 201 || responseCode == 200) {
			HttpEntity entity = response.getEntity();
			String result = EntityUtils.toString(entity);
			return Response.status(responseCode).entity(result).type(MediaType.APPLICATION_JSON).build();
		} else {
			HttpEntity entity = response.getEntity();
			String result = EntityUtils.toString(entity);
			return Response.status(responseCode).entity(result).type(MediaType.APPLICATION_JSON).build();
		}
	}

	/**
	 * Upload a composition given only a JSON representation - other required info passed along 
	 * with the config object - mostly for the single patient provisioning - convenience method
	 * @param body JSON representation of Composition
	 * @return HTTP Response object
	 * @throws ClientProtocolException
	 * @throws IOException
	 * @throws URISyntaxException
	 */
	public Response uploadComposition(String body) throws ClientProtocolException, IOException, URISyntaxException {
		// get the composition body
		String url = config.getBaseUrl() + "composition?ehrId=" + config.getEhrId() + "&templateId="
				+ config.getTemplateId() + "&committerName=" + config.getCommiterName();
		URIBuilder ub = new URIBuilder(config.getBaseUrl() + "composition");
		ub.addParameter("ehrId", config.getEhrId());
		ub.addParameter("templateId", config.getTemplateId());
		ub.addParameter("format", "FLAT");
		ub.addParameter("comitterId", config.getCommiterName());
		url = ub.toString();

		HttpPost request = new HttpPost(url);
		request.addHeader("Ehr-Session", config.getSessionId());
		request.addHeader("Content-Type", "application/json");
		request.setEntity(new StringEntity(body));

		HttpResponse response = client.execute(request);
		int responseCode = response.getStatusLine().getStatusCode();
		if (responseCode == 201 || responseCode == 200) {
			HttpEntity entity = response.getEntity();
			String result = EntityUtils.toString(entity);
			JsonObject jsonObject = (new JsonParser()).parse(result.toString()).getAsJsonObject();
			config.setCompositionId(jsonObject.get("compositionUid").getAsString());
			return Response.status(responseCode).entity(result).type(MediaType.APPLICATION_JSON).build();
		} else {
			HttpEntity entity = response.getEntity();
			String result = EntityUtils.toString(entity);
			return Response.status(responseCode).entity(result).type(MediaType.APPLICATION_JSON).build();
		}
	}
	
	
	/**
	 * Uploads a 'default' vitals composition for single patient provisioner - convenience method 
	 * @return HTTP Response object
	 * @throws ClientProtocolException
	 * @throws IOException
	 * @throws URISyntaxException
	 */
	public Response uploadDefaultComposition() throws ClientProtocolException, IOException, URISyntaxException {
		String body = getFileAsString("assets/sample_requests/vital-signs/vital-signs-composition.json");
		Response response = uploadComposition(body);
		return response;
	}

	// FHIR Demographic call
	
	/**
	 * Create a FHIR formatted XML patient resource, and upload it into openEMPI demographics server implmented
	 * by UCL MSc SSE Students
	 * @param fhirBaseUrl String - location of the openEMPI server
	 * @param body FHIR patient resource
	 * @return HTTP Response object
	 * @throws URISyntaxException
	 * @throws ClientProtocolException
	 * @throws IOException
	 */
	public Response createFhirPatientDemographic(String fhirBaseUrl, String body)
			throws URISyntaxException, ClientProtocolException, IOException {
		String url;
		URIBuilder ub = new URIBuilder(fhirBaseUrl + "Patient");
		url = ub.toString();
		HttpPost request = new HttpPost(url);
		request.addHeader("Content-Type", "application/xml");
		request.setEntity(new StringEntity(body));
		
		HttpResponse response = client.execute(request);
		HttpEntity entity = response.getEntity();
		@SuppressWarnings("unused") // this needs to be here or else the multi patient 
		// for loop gets stuck after a couple of rows
		String result = EntityUtils.toString(entity);
		int responseCode = response.getStatusLine().getStatusCode();
		if (responseCode == 200 || responseCode == 201) {
			/*
			 * Example response header set from this server:
			 * 
			 * Key : Date ,Value : Sat, 05 Aug 2017 14:24:36 GMT Key :
			 * X-Powered-By ,Value : HAPI FHIR 2.5 REST Server (FHIR Server;
			 * FHIR 1.0.2/DSTU2) Key : ETag ,Value : W/"1" Key :
			 * Content-Location ,Value :
			 * http://51.140.57.74:8090/fhir/Patient/453/_history/1 Key :
			 * Location ,Value :
			 * http://51.140.57.74:8090/fhir/Patient/453/_history/1 Key : Server
			 * ,Value : Jetty(9.2.22.v20170606)
			 * 
			 */

			String locationUrl = response.getFirstHeader("Location").getValue();
			// cut off all of the url so all that is left is: Patient/{id}
			String trimmedUrl = locationUrl.substring(locationUrl.lastIndexOf("Patient"),
					locationUrl.lastIndexOf("/_history/"));
			// then look at what's left after the forward slash
			String fhirPatientId = trimmedUrl.substring(trimmedUrl.lastIndexOf("/") + 1);

			config.setSubjectId(fhirPatientId);

			JsonObject jsonResponse = new JsonObject();

			Header[] headers = response.getAllHeaders();
			for (Header header : headers) {
				jsonResponse.addProperty(header.getName(), header.getValue());
			}

			// get location and set this as SubjectId
			return Response.status(responseCode).entity(jsonResponse.toString()).type(MediaType.APPLICATION_JSON)
					.build();

		} else {
			System.out.println("error");
			JsonObject jsonResponse = new JsonObject();
			jsonResponse.addProperty("errorMessage", "Error creating this FHIR Resource");
			// TODO add more info to this error message
			return Response.status(responseCode).entity(jsonResponse.toString()).type(MediaType.APPLICATION_JSON)
					.build();
		}
	}
	
	
	/**
	 * Create a 'default' FHIR patient used in single patient provisioner - convenience method
	 * @return HTTP Response object
	 * @throws URISyntaxException
	 * @throws ClientProtocolException
	 * @throws IOException
	 */
	public Response createDefaultFhirPatientDemographic()
			throws URISyntaxException, ClientProtocolException, IOException {
		String fhirPatientBody = getFileAsString("assets/sample_requests/defaultFhirPatient.xml");
		Response response = createFhirPatientDemographic(config.getFhirDemographicBaseUrl(), fhirPatientBody);
		return response;
	}

	// Methods for MULTIPLE PATIENT Script
	
	/**
	 * Turn a CSV file of patient demographic data, and convert it into a list of PatientDemographic objects,
	 * which can then be easily formatted into marand of fhir representations
	 * @param fileName of the patient asset file 
	 * @return HTTP Response object
	 * @throws IOException
	 */
	public List<PatientDemographic> readPatientCsvToObjectlist(String fileName) throws IOException {

		ClassLoader classLoader = getClass().getClassLoader();
		File file = new File(classLoader.getResource(fileName).getFile());

		CsvToBean<PatientDemographic> csvToBean = new CsvToBean<PatientDemographic>();
		// CSV Header:
		// [Key, , Forename, Surname, Address_1, Address_2, Address_3, Postcode,
		// Telephone,
		// DateofBirth, Gender, NHSNumber, PasNumber, Department, GPNumber]
		Map<String, String> columnMapping = new HashMap<String, String>();

		columnMapping.put("Key", "Key");
		columnMapping.put("Forename", "Forename");
		columnMapping.put("Surname", "Surname");
		columnMapping.put("Address_1", "Address_1");
		columnMapping.put("Address_2", "Address_2");
		columnMapping.put("Address_3", "Address_3");
		columnMapping.put("Postcode", "Postcode");
		columnMapping.put("Telephone", "Telephone");
		columnMapping.put("DateofBirth", "DateofBirth");
		columnMapping.put("Gender", "Gender");
		columnMapping.put("NHSNumber", "NHSNumber");
		columnMapping.put("PasNumber", "PasNumber");
		columnMapping.put("Department", "Department");
		columnMapping.put("GPNumber", "GPNumber");
		columnMapping.put("", "Prefix"); // the prefix columns in the dummy data
											// have no title atm.

		HeaderColumnNameTranslateMappingStrategy<PatientDemographic> strategy = new HeaderColumnNameTranslateMappingStrategy<PatientDemographic>();
		strategy.setType(PatientDemographic.class);
		strategy.setColumnMapping(columnMapping);

		List<PatientDemographic> list = null;
		CSVReader reader = new CSVReader(new FileReader(file), ',', '"', 0);
		list = csvToBean.parse(strategy, reader);

		return list;

	}
	
	
	private String[] getAllCompositionFileNamesFromFolder(String folderName) {
		ClassLoader classLoader = getClass().getClassLoader();
		String path = classLoader.getResource(folderName).getFile();
		File directory = new File(path);
		String[] files = directory.list(new FilenameFilter() {
			public boolean accept(File directory, String fileName) {
				return fileName.endsWith(".json");
			}
		});

		return files;
	}

	/**
	 * Upload all compositions of the specified types, using booleans to choose what templates, into a specified EHR
	 * @param ehrId String
	 * @param doAllergies boolean
	 * @param doOrders boolean
	 * @param doProblems boolean
	 * @param doProcedures boolean
	 * @param doLabResults
	 * @return √
	 * @throws ClientProtocolException
	 * @throws IOException
	 * @throws URISyntaxException
	 */
	public Response uploadMultipleCompositionsDefaultFolders(String ehrId, boolean doAllergies, boolean doOrders,
			boolean doProblems, boolean doProcedures, boolean doLabResults)
			throws ClientProtocolException, IOException, URISyntaxException {

		// TODO Handle file not found errors
		// Get the files with the dummy data

		// change the config.EhrId calls to using the parameter ehrID

		JsonObject result = new JsonObject();
		int counter = 0;

		String baseFile = "assets/sample_requests/";

		if (doAllergies) {
			String folder = baseFile + "allergies/";
			// Get all .json files in the folder assuming they are all
			// compositions
			String fileNamesToUpload[] = getAllCompositionFileNamesFromFolder(folder);
			for (String fileName : fileNamesToUpload) {
				String compositionBody = getFileAsString(folder + fileName);
				Response commitCompResponse = uploadComposition(compositionBody, config.getSessionId(),
						"IDCR Allergies List.v0", config.getCommiterName(), config.getEhrId());
				int responseCode = commitCompResponse.getStatus();
				result.addProperty("Commit Composition: " + fileName, responseCode + " Response Status");
				if (responseCode == 201) {
					counter++;
				}
			}
		}
		if (doOrders) {
			String folder = baseFile + "orders/";
			String fileNamesToUpload[] = getAllCompositionFileNamesFromFolder(folder);
			for (String fileName : fileNamesToUpload) {
				String compositionBody = getFileAsString(folder + fileName);
				Response commitCompResponse = uploadComposition(compositionBody, config.getSessionId(),
						"IDCR - Laboratory Order.v0", config.getCommiterName(), config.getEhrId());
				int responseCode = commitCompResponse.getStatus();
				result.addProperty("Commit Composition: " + fileName, responseCode + " Response Status");
				if (responseCode == 201) {
					counter++;
				}
			}
		}
		if (doProblems) {
			String folder = baseFile + "problems/";
			String fileNamesToUpload[] = getAllCompositionFileNamesFromFolder(folder);
			for (String fileName : fileNamesToUpload) {
				String compositionBody = getFileAsString(folder + fileName);
				Response commitCompResponse = uploadComposition(compositionBody, config.getSessionId(),
						"IDCR Problem List.v1", config.getCommiterName(), config.getEhrId());
				int responseCode = commitCompResponse.getStatus();
				result.addProperty("Commit Composition: " + fileName, responseCode + " Response Status");
				if (responseCode == 201) {
					counter++;
				}
			}
		}
		if (doProcedures) {
			String folder = baseFile + "procedures/";
			String fileNamesToUpload[] = getAllCompositionFileNamesFromFolder(folder);
			for (String fileName : fileNamesToUpload) {
				String compositionBody = getFileAsString(folder + fileName);
				Response commitCompResponse = uploadComposition(compositionBody, config.getSessionId(),
						"IDCR Procedures List.v0", config.getCommiterName(), config.getEhrId());
				int responseCode = commitCompResponse.getStatus();
				result.addProperty("Commit Composition: " + fileName, responseCode + " Response Status");
				if (responseCode == 201) {
					counter++;
				}
			}
		}
		if (doLabResults) {
			String folder = baseFile + "lab-results/";
			String fileNamesToUpload[] = getAllCompositionFileNamesFromFolder(folder);
			for (String fileName : fileNamesToUpload) {
				String compositionBody = getFileAsString(folder + fileName);
				Response commitCompResponse = uploadComposition(compositionBody, config.getSessionId(),
						"IDCR - Laboratory Test Report.v0", config.getCommiterName(), config.getEhrId());
				int responseCode = commitCompResponse.getStatus();
				result.addProperty("Commit Composition: " + fileName, responseCode + " Response Status");
				if (responseCode == 201) {
					counter++;
				}
			}
		}

		result.addProperty("Total number of Compositions commited", counter);

		return Response.status(200).entity(result.toString()).type(MediaType.APPLICATION_JSON).build();

	}
	
	
	/**
	 * use the impiort csv resource to upload multiple vitals compopsitions, outlined in an asset 
	 * vitals CSV file in the resources folder
	 * @param fileName
	 * @return HTTP Response object
	 * @throws IOException
	 * @throws URISyntaxException
	 */
	public Response importCsv(String fileName) throws IOException, URISyntaxException {
		String body = getFileAsString(fileName);
		ImportCsvResource importer = new ImportCsvResource();
		Response importResponse = importer.csvToCompositions(config.getSessionId(), body, config.getBaseUrl());
		return importResponse;
	}

}
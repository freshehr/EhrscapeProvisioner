package ehrscapeProvisioner.model;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.logging.Logger;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
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

public class EhrscapeRequest {

	Gson gson = new Gson();

	private final static Logger logger = Logger.getLogger(EhrscapeRequest.class.getName());

	HttpClient client = HttpClientBuilder.create().build();

	public static EhrscapeConfig config = new EhrscapeConfig();

	private String getFileAsString(String fileName) {

		StringBuilder result = new StringBuilder("");

		// Get file from resources folder
		ClassLoader classLoader = getClass().getClassLoader();
		// ClassLoader classLoader =
		// Thread.currentThread().getContextClassLoader();
		File file = new File(classLoader.getResource(fileName).getFile());

		try (Scanner scanner = new Scanner(file)) {

			while (scanner.hasNextLine()) {
				String line = scanner.nextLine();
				result.append(line).append("\n");
			}

			scanner.close();

		} catch (IOException e) {
			e.printStackTrace();
		}

		return result.toString();

	}

	// SINGLE PATIENT

	public String getSession(String username, String password)
			throws ClientProtocolException, IOException, URISyntaxException {

		String url; // = config.getBaseUrl() + "session?username=" + username +
					// "&password=" + password + "";

		config.setUsername(username);
		config.setPassword(password);

		URIBuilder ub = new URIBuilder(config.getBaseUrl() + "session");
		ub.addParameter("username", username);
		ub.addParameter("password", password);
		url = ub.toString();

		HttpPost request = new HttpPost(url);

		List<NameValuePair> params = ub.getQueryParams();

		HttpResponse response = client.execute(request);
		String finalUrl = request.getRequestLine().toString();
		System.out.println("Response Code : " + response.getStatusLine().getStatusCode() + "\n URL: " + finalUrl + " "
				+ params.toString());

		logger.info("Response status logged: " + response.getStatusLine().getStatusCode());

		HttpEntity entity = response.getEntity();
		String result = EntityUtils.toString(entity);
		System.out.println(result);

		JsonObject jsonObject = (new JsonParser()).parse(result.toString()).getAsJsonObject();

		logger.info("" + jsonObject.get("sessionId"));

		config.setSessionId(jsonObject.get("sessionId").getAsString());
		return result.toString();// jsonResponse;

	}

	// create patient demographic
	public String createMarandPatientDemographic(String filename)
			throws ClientProtocolException, IOException, URISyntaxException {
		String body = getFileAsString(filename);
		URIBuilder ub = new URIBuilder(config.getBaseUrl() + "demographics/party");
		String url = ub.toString(); // = config.getBaseUrl() +
									// "demographics/party";
		HttpPost request = new HttpPost(url);
		request.addHeader("Ehr-Session", config.getSessionId());
		request.addHeader("Content-Type", "application/json");
		request.setEntity(new StringEntity(body));
		HttpResponse response = client.execute(request);
		System.out.println("Response Code : " + response.getStatusLine().getStatusCode());

		HttpEntity entity = response.getEntity();
		String result = EntityUtils.toString(entity);
		// System.out.println(result);

		JsonObject jsonObject = (new JsonParser()).parse(result.toString()).getAsJsonObject();
		JsonObject jsonSubObject = jsonObject.getAsJsonObject("meta");
		String partyStringHref = jsonSubObject.get("href").getAsString();
		String partyID = partyStringHref.substring(partyStringHref.lastIndexOf("/") + 1);
		System.out.println(partyID);
		config.setSubjectId(partyID);
		return result;
	}

	// create patient demographic
	public String createPatientDefault() throws ClientProtocolException, IOException, URISyntaxException {
		String response = createMarandPatientDemographic("assets/sample_requests/party.json");
		return response;
	}

	// TODO skip provisioning step by deciding how to handle the subjectIDs
	// maybe use the sessionID as the subjectID? too much of a hack perhaps
	// need some way of finding an unused subjectID from the server or perhaps
	// if we are provisioning 500 patients simply increment each time
	// Could check the subjectIDs manually but is this overkill
	// for now use sessionID maybe and a random number unique id concatenated

	// Could create a uniqueID for now too, and test if an ehr exists for that
	// if not try a new id
	// get the response code back and then take appropriate action
	// and then create the ehr

	public String createEhr(String subjectID, String commiter)
			throws ClientProtocolException, IOException, URISyntaxException {
		String url; // = config.getBaseUrl() + "ehr?subjectId=" + subjectID +
					// "&subjectNamespace=" + config.getSubjectNamespace() +
					// "&commiterName=" + commiter;

		URIBuilder ub = new URIBuilder(config.getBaseUrl() + "ehr");
		ub.addParameter("subjectId", config.getSubjectId());
		ub.addParameter("subjectNamespace", config.getSubjectNamespace());
		ub.addParameter("commiterName", commiter);
		url = ub.toString();

		HttpPost request = new HttpPost(url);
		request.addHeader("Ehr-Session", config.getSessionId());
		logger.info("The current session is" + config.getSessionId());
		String finalUrl = request.getRequestLine().toString();
		System.out.println(finalUrl);
		HttpResponse response = client.execute(request);
		System.out.println("Response Code : " + response.getStatusLine().getStatusCode() + "\n URL: " + finalUrl);
		HttpEntity entity = response.getEntity();
		String result = EntityUtils.toString(entity);
		System.out.println(result);

		JsonObject jsonObject = (new JsonParser()).parse(result.toString()).getAsJsonObject();
		logger.info("" + jsonObject.get("ehrId"));

		config.setEhrId(jsonObject.get("ehrId").getAsString());
		config.setSubjectId(subjectID);

		return result.toString();

	}

	public String uploadTemplate(String filename) throws IOException, URISyntaxException {
		// get the template
		String body = getFileAsString(filename);
		System.out.println(body.length());
		String url; // = config.getBaseUrl() + "template/";

		URIBuilder ub = new URIBuilder(config.getBaseUrl() + "template/");
		url = ub.toString();

		HttpPost request = new HttpPost(url);
		request.addHeader("Ehr-Session", config.getSessionId());
		request.addHeader("Content-Type", "application/xml");
		request.setEntity(new StringEntity(body));

		logger.info("The current session is" + config.getSessionId());
		String finalUrl = request.getRequestLine().toString();
		System.out.println(finalUrl);

		HttpResponse response = client.execute(request);
		System.out.println("Response Code : " + response.getStatusLine().getStatusCode());

		HttpEntity entity = response.getEntity();
		String result = EntityUtils.toString(entity);
		System.out.println(result);

		// JsonObject jsonObject = (new
		// JsonParser()).parse(result.toString()).getAsJsonObject();
		return result.toString();
	}

	public String uploadDefaultTemplate() throws IOException, URISyntaxException {
		String response = uploadTemplate("assets/sample_requests/vital-signs/vital-signs-template.xml");
		return response;
	}

	// Composition

	public String uploadComposition(String filename) throws ClientProtocolException, IOException, URISyntaxException {
		String body = getFileAsString(filename);
		System.out.println(body.length());
		System.out.println(config.getEhrId());
		System.out.println(config.getTemplateId());
		System.out.println(config.getCommiterName());
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

		logger.info("The current session is" + config.getSessionId());
		String finalUrl = request.getRequestLine().toString();
		System.out.println(finalUrl);

		HttpResponse response = client.execute(request);
		System.out.println("Response Code : " + response.getStatusLine().getStatusCode());

		HttpEntity entity = response.getEntity();
		String result = EntityUtils.toString(entity);
		System.out.println(result);

		JsonObject jsonObject = (new JsonParser()).parse(result.toString()).getAsJsonObject();
		logger.info("" + jsonObject.get("compositionUid"));

		config.setCompositionId(jsonObject.get("compositionUid").getAsString());

		return result.toString();
	}
	
	public String uploadDefaultComposition() throws ClientProtocolException, IOException, URISyntaxException {
		String response = uploadComposition("assets/sample_requests/vital-signs/vital-signs-composition.json");
		return response;
	}

	// FHIR Demographic call

	public String createDeafultFhirPatientDemographic() {
		return "In progress";
	}

	public List<PatientDemographic> readPatientCsvToObjectlist(String fileName) throws IOException {

		ClassLoader classLoader = getClass().getClassLoader();
		File file = new File(classLoader.getResource(fileName).getFile());

		CsvToBean<PatientDemographic> csvToBean = new CsvToBean<PatientDemographic>();
		// https://stackoverflow.com/questions/13505653/opencsv-how-to-map-selected-columns-to-java-bean-regardless-of-order/14976689#14976689
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

		// System.out.println(list.get(0).toString());
		// System.out.println(list.get(0).getPrefix());
		// System.out.println(list.get(1).encodeInFhirFormat(""));
		System.out.println(list.get(0).toMarandPartyJson());

		return list;

	}

	// MULTIPLE PATIENT

}
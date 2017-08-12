package ehrscapeProvisioner;

import java.io.IOException;
import java.net.URISyntaxException;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.http.client.ClientProtocolException;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ehrscapeProvisioner.model.EhrscapeRequest;

/**
 * Root resource (exposed at "provision" path)
 */
@Path("provision")
public class PatientProvisionerResource {
	
	// TODO change these strings to Response objects and use the constituent responses to return relevant errors
	// return feedback if the requests fail
	// TODO make a new resource class with the invidual requests for the frontend to access directly
	
	@POST
	@Path("single-provision-no-demographic")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public String singleProvision(String inputBody) throws ClientProtocolException, IOException, URISyntaxException {
		EhrscapeRequest req =  new EhrscapeRequest();
		
		Gson gson = new Gson();
		JsonObject jsonInput = (new JsonParser()).parse(inputBody.toString()).getAsJsonObject();
		System.out.println(jsonInput.get("username").getAsString());
		System.out.println(jsonInput.get("password").getAsString());
		
		// Check if user wants to overwrite the base url
		if (jsonInput.has("baseUrl")) {
			EhrscapeRequest.config.setBaseUrl(jsonInput.get("baseUrl").getAsString());
		}
		
		Response getSessionResponse = req.getSession(jsonInput.get("username").getAsString(),jsonInput.get("password").getAsString()); 
		System.out.println(EhrscapeRequest.config.getSessionId());
		Response createEhrResponse = req.createEhr(EhrscapeRequest.config.getSessionId(), "JarrodEhrscapeProvisioner");
		Response uploadTemplateResponse = req.uploadDefaultTemplate();
		Response uploadCompResponse = req.uploadDefaultComposition();
		
		// put the final response stuff here
		
		//JsonObject jsonOutput = new JsonObject();
		//jsonOutput.addProperty("num", 123);
		//jsonOutput.addProperty("testKey", "testVal"); // for a custom response later if needed
		
		String finalConfig = gson.toJson(EhrscapeRequest.config);
		//System.out.println(jsonInput.toString());
		return finalConfig; //gson.toJson(jsonOutput);
	}
	
	@POST
	@Path("single-provision-marand")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public String singleProvisionDemographic(String inputBody) throws ClientProtocolException, IOException, URISyntaxException {
		EhrscapeRequest req =  new EhrscapeRequest();
		
		Gson gson = new Gson();
		JsonObject jsonInput = (new JsonParser()).parse(inputBody.toString()).getAsJsonObject();
		System.out.println(jsonInput.get("username").getAsString());
		System.out.println(jsonInput.get("password").getAsString());
		
		// Check if user wants to overwrite the base url
		if (jsonInput.has("baseUrl")) {
			EhrscapeRequest.config.setBaseUrl(jsonInput.get("baseUrl").getAsString());
		}
		
		Response getSessionResponse = req.getSession(jsonInput.get("username").getAsString(),jsonInput.get("password").getAsString()); 
		Response createPatientDemographicResponse = req.createPatientDefault();
		Response createEhrResponse = req.createEhr(EhrscapeRequest.config.getSubjectId(), "JarrodEhrscapeProvisioner"); 
		// replace uk.nhs.nhs_number , let that be a user input
		// make the default https://fhir.nhs.uk/Id/nhs-number but make this a customisable input
		Response uploadTemplateResponse = req.uploadDefaultTemplate();
		Response uploadCompResponse = req.uploadDefaultComposition();
		
		// put the final response stuff here
		
		//JsonObject jsonOutput = new JsonObject();
		//jsonOutput.addProperty("num", 123);
		//jsonOutput.addProperty("testKey", "testVal"); // for a custom response later if needed
		
		String finalConfig = gson.toJson(EhrscapeRequest.config);
		//System.out.println(jsonInput.toString());
		return finalConfig; //gson.toJson(jsonOutput);
	}
	
	@POST
	@Path("single-provision-fhir")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public String singleProvisionFhirDemographic(String inputBody) throws ClientProtocolException, IOException, URISyntaxException {
		EhrscapeRequest req =  new EhrscapeRequest();
		
		Gson gson = new Gson();
		JsonObject jsonInput = (new JsonParser()).parse(inputBody.toString()).getAsJsonObject();
		System.out.println(jsonInput.get("username").getAsString());
		System.out.println(jsonInput.get("password").getAsString());
		
		// Check if user wants to overwrite the base url
		if (jsonInput.has("baseUrl")) {
			EhrscapeRequest.config.setBaseUrl(jsonInput.get("baseUrl").getAsString());
		}
		
		Response getSessionResponse = req.getSession(jsonInput.get("username").getAsString(),jsonInput.get("password").getAsString()); 
		Response createPatientDemographicResponse = req.createDefaultFhirPatientDemographic();
		Response createEhrResponse = req.createEhr(EhrscapeRequest.config.getSubjectId(), "JarrodEhrscapeProvisioner"); 
		// replace uk.nhs.nhs_number , let that be a user input
		// make the default https://fhir.nhs.uk/Id/nhs-number but make this a customisable input
		Response uploadTemplateResponse = req.uploadDefaultTemplate();
		Response uploadCompResponse = req.uploadDefaultComposition();
		
		// put the final response stuff here
		
		//JsonObject jsonOutput = new JsonObject();
		//jsonOutput.addProperty("num", 123);
		//jsonOutput.addProperty("testKey", "testVal"); // for a custom response later if needed
		
		String finalConfig = gson.toJson(EhrscapeRequest.config);
		//System.out.println(jsonInput.toString());
		return finalConfig; //gson.toJson(jsonOutput);
	}
	
	@POST
	@Path("multi-patient-default")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response multiplePatientProvisionDefault(String inputBody) throws ClientProtocolException, IOException, URISyntaxException {
		
		EhrscapeRequest req =  new EhrscapeRequest();
		
		// parse the request body
		Gson gson = new Gson();
		JsonObject jsonInput = (new JsonParser()).parse(inputBody.toString()).getAsJsonObject();
		String user = jsonInput.get("username").getAsString();
		String pass = jsonInput.get("password").getAsString();
		System.out.println(user);
		System.out.println(pass);
		
		// Check if user wants to overwrite the base url
		if (jsonInput.has("baseUrl")) {
			EhrscapeRequest.config.setBaseUrl(jsonInput.get("baseUrl").getAsString());
		}
		
		// prepare the response 
		JsonObject finalJsonResponse = new JsonObject();
		
		// create Session
		Response createSessionRes = req.getSession(user, pass);
		if (createSessionRes.getStatus() == 400 || createSessionRes.getStatus() == 401) {
			return createSessionRes;
		}
		// upload templates
		String allergiesTemplateBody = req.getFileAsString("");
		String problemsTemplateBody = req.getFileAsString("");
		String ordersTemplateBody = req.getFileAsString("");
		String proceduresTemplateBody = req.getFileAsString("");
		String labResultsTemplateBody = req.getFileAsString("");
		
		Response allergiesUploadTemplate = req.uploadTemplate(allergiesTemplateBody);
		Response problemsUploadTemplate = req.uploadTemplate(problemsTemplateBody);
		Response ordersUploadTemplate = req.uploadTemplate(ordersTemplateBody);
		Response proceduresUploadTemplate = req.uploadTemplate(proceduresTemplateBody);
		Response labResultsUploadTemplate = req.uploadTemplate(labResultsTemplateBody);
		
		// go through patients csv file
		// for each patient:
		
		// demographics
		
		// ehr
		
		// compositions
		
		// vitals + import csv
		
		return Response.ok("In Progress - Got to end of this call.", MediaType.APPLICATION_JSON).build();
	}
	
	
	
}

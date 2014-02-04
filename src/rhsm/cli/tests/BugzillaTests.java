package rhsm.cli.tests;

import java.io.File;
import java.io.IOException;

import java.math.BigInteger;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.xmlrpc.XmlRpcException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterGroups;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeGroups;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.redhat.qe.Assert;
import com.redhat.qe.auto.bugzilla.BzChecker;
import com.redhat.qe.auto.tcms.ImplementsNitrateTest;
import com.redhat.qe.auto.testng.TestNGUtils;

import rhsm.base.CandlepinType;
import rhsm.base.ConsumerType;
import rhsm.base.SubscriptionManagerBaseTestScript;
import rhsm.base.SubscriptionManagerCLITestScript;
import rhsm.cli.tasks.CandlepinTasks;
import rhsm.data.ConsumerCert;
import rhsm.data.ContentNamespace;
import rhsm.data.EntitlementCert;
import rhsm.data.InstalledProduct;
import rhsm.data.OrderNamespace;
import rhsm.data.ProductCert;
import rhsm.data.ProductSubscription;
import rhsm.data.Repo;
import rhsm.data.RevokedCert;
import rhsm.data.SubscriptionPool;
import rhsm.data.YumRepo;
import com.redhat.qe.tools.RemoteFileTasks;
import com.redhat.qe.tools.SSHCommandResult;
import com.redhat.qe.tools.SSHCommandRunner;

/**
 * @author skallesh
 * 
 * 
 */
@Test(groups = { "BugzillaTests" })
public class BugzillaTests extends SubscriptionManagerCLITestScript {
	protected String ownerKey="";
	protected String randomAvailableProductId;
	protected EntitlementCert expiringCert = null;
	protected String EndingDate;
	protected final String importCertificatesDir = "/tmp/sm-importExpiredCertificatesDir"
			.toLowerCase();
	protected final String myEmptyCaCertFile = "/etc/rhsm/ca/myemptycert.pem";
	protected Integer configuredHealFrequency = null;
	protected Integer configuredCertFrequency = null;
	protected String configuredHostname=null;
	protected String factname="system.entitlements_valid";
	protected String SystemDateOnClient=null;
	protected String SystemDateOnServer=null;
	List<String> providedProducts = new ArrayList<String>();
	protected List<File> entitlementCertFiles = new ArrayList<File>();
	protected final String importCertificatesDir1 = "/tmp/sm-importV1CertificatesDir".toLowerCase();
	
	
	
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(	description="verify End date and start date of the subscription is appropriate one when you attach a future subscription and then  heal after 1 min",
			groups={"VerifyStartEndDateOfSubscription","blockedByBug-994853"},
			enabled=true)
	public void VerifyStartEndDateOfSubscription() throws Exception {
		moveProductCertFiles("*");
		client.runCommandAndWait("cp /root/temp1/100000000000002.pem "+clienttasks.productCertDir);
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, true, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		Map<String, String> factsMap = new HashMap<String, String>();
		factsMap.put("cpu.cpu_socket(s)", String.valueOf(4));
		clienttasks.createFactsFileWithOverridingValues(factsMap);
		clienttasks.autoheal(null, null, true, null, null, null);
		for(SubscriptionPool AvailablePools:clienttasks.getCurrentlyAvailableSubscriptionPools()){
			 if(AvailablePools.productId.equals("awesomeos-x86_64")){
				 clienttasks.subscribe(null, null, AvailablePools.poolId, null, null, "1", null, null, null, null, null);
			}
		}
		for(InstalledProduct installedproduct:clienttasks.getCurrentlyInstalledProducts() ){
			for(ProductSubscription pool:clienttasks.getCurrentlyConsumedProductSubscriptions()){
				Assert.assertEquals(installedproduct.startDate, pool.startDate);
			}
		}
		clienttasks.autoheal(null, true, null, null, null, null);
		clienttasks.restart_rhsmcertd(null, null, false, true);
		
		for(InstalledProduct installedproduct:clienttasks.getCurrentlyInstalledProducts() ){
			for(ProductSubscription pool:clienttasks.getCurrentlyConsumedProductSubscriptions()){
				Assert.assertEquals(installedproduct.startDate, pool.startDate);
				if(!pool.isActive){
					Assert.assertEquals(installedproduct.endDate, pool.endDate);
				}
			}
		}
	}
	
	
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(	description="verify status check and response from server after addition and deletion of product from /etc/pki/product/",
			groups={"VerifyStatusCheck","blockedByBug-921870"},
			enabled=true)
	public void VerifyStatusCheck() throws Exception {
/* takes too much time; calling configureTmpProductCertDirWithInstalledProductCerts instead
		for (InstalledProduct installed : clienttasks.getCurrentlyInstalledProducts()) {
			if(!(installed.productId.equals("32060"))){
				moveProductCertFiles("*");
			}
		}
*/
		String result,expectedStatus;
		ProductCert installedProductCert32060 = ProductCert.findFirstInstanceWithMatchingFieldFromList("productId", "32060", clienttasks.getCurrentProductCerts());
		Assert.assertNotNull(installedProductCert32060, "Found installed product cert 32060 needed for this test.");
		configureTmpProductCertDirWithInstalledProductCerts(Arrays.asList(new ProductCert[]{}));
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, true, null, null,
				(String) null, null, null, null, true, false, null, null, null);
		Assert.assertTrue(clienttasks.getCurrentProductCertFiles().isEmpty(),"No product certs are installed.");
		result=clienttasks.status(null, null, null, null).getStdout();
		expectedStatus = "Overall Status: Current";
		Assert.assertTrue(result.contains(expectedStatus), "System status displays '"+expectedStatus+"' because no products are installed.");	
		client.runCommandAndWait("cp "+installedProductCert32060.file+" "+tmpProductCertDir);	// OR THIS IS VALID TOO configureTmpProductCertDirWithInstalledProductCerts(Arrays.asList(new ProductCert[]{installedProductCert32060}));
		result=clienttasks.status(null, null, null, null).getStdout();
		// The test behavior demonstrated in https://bugzilla.redhat.com/show_bug.cgi?id=921870 seems to have improved; commenting out the following few lines...
//		expectedStatus = "Overall Status: Current";
//		Assert.assertTrue(result.contains(expectedStatus), "System status displays '"+expectedStatus+"' after manually installing a product cert because this is the cached value.  Rhsmcertd needs to be triggered.");
//		clienttasks.run_rhsmcertd_worker(false);
//		result=clienttasks.status(null, null, null, null).getStdout();
		expectedStatus = "Overall Status: Invalid";
		Assert.assertTrue(result.contains(expectedStatus), "System status displays '"+expectedStatus+"' after manully installing a product cert and running the rhsmcertd worker.");
		clienttasks.run_rhsmcertd_worker(true);
		result=clienttasks.status(null, null, null, null).getStdout();
		expectedStatus = "Overall Status: Current";
		Assert.assertTrue(result.contains(expectedStatus), "System status displays '"+expectedStatus+"' after finally running rhsmcertd worker with auto-healing.");
	}
	
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(	description="verify if Status displays product name multiple times when the system had inactive stack subscriptions",
			groups={"VerifyIfStatusDisplaysProductNameMultipleTimes","blockedByBug-972752"},
			enabled=true)
	public void VerifyIfStatusDisplaysProductNameMultipleTimes() throws Exception {
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		int sockets = 4;
		clienttasks.autoheal(null, null, true, null, null, null);
		Map<String, String> factsMap = new HashMap<String, String>();
		factsMap.put("cpu.cpu_socket(s)", String.valueOf(sockets));
		clienttasks.createFactsFileWithOverridingValues(factsMap);
		clienttasks.facts(null, true, null, null, null);
		 for(SubscriptionPool AvailablePools:clienttasks.getCurrentlyAvailableSubscriptionPools()){
			 if(AvailablePools.productId.equals("awesomeos-x86_64")){
			 clienttasks.subscribe(null, null, AvailablePools.poolId, null, null, "1", null, null, null, null, null);
			 }
		 
		 }
		 String result=clienttasks.status(null, null, null, null).getStdout();
		 clienttasks.autoheal(null, true, null, null, null, null);
	}
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(	description="verify if update facts button won't recreate facts.json file",
			groups={"VerifyFactsFileExistenceAfterUpdate","blockedByBug-627707"},
			enabled=true)
	public void VerifyFactsFileExistenceAfterUpdate() throws Exception {
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		client.runCommandAndWait("rm -rf "+clienttasks.rhsmFactsJsonFile);
		Assert.assertFalse(RemoteFileTasks.testFileExists(client, clienttasks.rhsmFactsJsonFile)==1, "rhsm facts json file '"+clienttasks.rhsmFactsJsonFile+"' exists");
		clienttasks.facts(null, true, null, null, null);
		Assert.assertTrue(RemoteFileTasks.testFileExists(client, clienttasks.rhsmFactsJsonFile)==1, "rhsm facts json file '"+clienttasks.rhsmFactsJsonFile+"' exists");

	}
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(	description="verify if unsubscribe does not delete entitlement cert from location /etc/pki/entitlement/product for consumer type candlepin ",
			groups={"unsubscribeTheRegisteredConsumerTypeCandlepin","blockedByBug-621962"},
			enabled=true)
	public void unsubscribeTheRegisteredConsumerTypeCandlepin() throws Exception {
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, ConsumerType.candlepin, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		 clienttasks.subscribe(true, null,(String)null, null, null, null, null, null, null, null, null);
		 List<File> files=clienttasks.getCurrentEntitlementCertFiles();
		 Assert.assertNotNull(files.size());	
		 clienttasks.unsubscribeFromAllOfTheCurrentlyConsumedProductSubscriptions();
		 files=clienttasks.getCurrentEntitlementCertFiles();
		 Assert.assertTrue(files.isEmpty());
	}
	
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(	description="verify if you can unsubscribe from imported cert",
			groups={"unsubscribeImportedcert","blockedByBug-691784"},
			enabled=true)
	public void unsubscribeImportedcert() throws Exception {
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		clienttasks.autoheal(null, null, true, null, null, null);	// disable
/* let's make this more efficient...
		String pool = null;
		client.runCommand("mkdir "+importCertificatesDir1);

		 for(SubscriptionPool AvailablePools:clienttasks.getCurrentlyAvailableSubscriptionPools()){
			 pool=AvailablePools.poolId;
		 }
		
		 clienttasks.subscribe(null, null, pool, null, null, null, null, null, null, null, null);
		 entitlementCertFiles = clienttasks.getCurrentEntitlementCertFiles();
		 File importEntitlementCertFile = entitlementCertFiles.get(randomGenerator.nextInt(entitlementCertFiles.size()));
*/
		SubscriptionPool pool = getRandomSubsetOfList(clienttasks.getCurrentlyAvailableSubscriptionPools(),1).get(0);
		File importEntitlementCertFile = clienttasks.subscribeToSubscriptionPool(pool,sm_clientUsername, sm_clientPassword,sm_serverUrl);
		 File importEntitlementKeyFile = clienttasks.getEntitlementCertKeyFileCorrespondingToEntitlementCertFile(importEntitlementCertFile);
		 File importCertificateFile = new File(importCertificatesDir1+File.separator+importEntitlementCertFile.getName());
		 client.runCommandAndWait("mkdir -p "+importCertificatesDir1);
		 client.runCommandAndWait("cat "+importEntitlementCertFile+" "+importEntitlementKeyFile+" > "+importCertificateFile);
		 String path =importCertificateFile.getPath();
		 clienttasks.clean(null, null, null);
		 clienttasks.importCertificate(path);
         String result=clienttasks.unsubscribe(true,(BigInteger)null, null, null, null).getStdout();
         String expected_result="1 subscriptions removed from this system.";
		 Assert.assertEquals(result.trim(), expected_result);
	}
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(	description="verify if subscription manager CLI does not display all facts",
			groups={"SystemFactsInCLI","blockedByBug-722239"},
			enabled=true)
	public void SystemFactsInCLI() throws Exception {
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		Map<String, String> result=clienttasks.getFacts("system");
		Assert.assertNotNull(result.get("system.certificate_version"));
	//	Assert.assertNotNull(result.get("system.name"));

	
	}
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(	description="verify if Registering with an activation key which has run out of susbcriptions results in a system, but no identity certificate",
			groups={"RegisterWithActivationKeyWithExpiredPool","blockedByBug-803814"},
			enabled=true)
	public void RegisterWithActivationKeyWithExpiredPool() throws Exception {
		System.out.println(getProperty("sm.server.admin.password",""));
		int endingMinutesFromNow = 1;
		Integer addQuantity=1;
		String name = String.format("%s_%s-ActivationKey%s", sm_clientUsername,
				sm_clientOrg, System.currentTimeMillis());
		Map<String, String> mapActivationKeyRequest = new HashMap<String, String>();
		mapActivationKeyRequest.put("name", name);
		JSONObject jsonActivationKeyRequest = new JSONObject(
				mapActivationKeyRequest);
		JSONObject jsonActivationKey = new JSONObject(
				CandlepinTasks.postResourceUsingRESTfulAPI(sm_clientUsername,
						sm_clientPassword, sm_serverUrl, "/owners/"
								+ sm_clientOrg + "/activation_keys",
								jsonActivationKeyRequest.toString()));
		
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		String consumerId = clienttasks.getCurrentConsumerId();
		ownerKey = CandlepinTasks.getOwnerKeyOfConsumerId(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, consumerId);
		String expiringPoolId = createTestPool(-60*24,endingMinutesFromNow);
		Calendar endCalendar = new GregorianCalendar();
		endCalendar.add(Calendar.MINUTE, endingMinutesFromNow);
		DateFormat yyyy_MM_dd_DateFormat = new SimpleDateFormat("M/d/yy h:mm aaa");
		String EndingDate=yyyy_MM_dd_DateFormat.format(endCalendar.getTime());
		sleep(endingMinutesFromNow*60*1000);
		new JSONObject(CandlepinTasks.postResourceUsingRESTfulAPI(sm_clientUsername, sm_clientPassword, sm_serverUrl, "/activation_keys/" + jsonActivationKey.getString("id") + "/pools/" +expiringPoolId+(addQuantity==null?"":"?quantity="+addQuantity), null));
		clienttasks.unregister(null, null, null);
		String result=clienttasks.register_(null, null, sm_clientOrg, null, null, null, null, null, null, null, name, null, null, null, true, null, null, null, null).getStderr();			
		System.out.println(EndingDate);
		String expected_message="Unable to attach pool with ID '"+expiringPoolId+"'.: Subscriptions for "+randomAvailableProductId+" expired on: "+EndingDate+".";
		Assert.assertEquals(result.trim(), expected_message);
		result=clienttasks.identity_(null, null, null, null, null, null, null).getStdout();
		Assert.assertEquals(result.trim(), clienttasks.msg_ConsumerNotRegistered);

	}
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(	description="verify Sytem.UUID fact is Deleted Once Consumer is Deleted",
			groups={"SytemUUIDfactShouldBeDeletedOnceConsumerDeleted","blockedByBug-1019753"},
			enabled=false)//the fact is removed from the list,hence test is disabled
	public void SytemUUIDfactShouldBeDeletedOnceConsumerDeleted() throws Exception {
	clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null).getStderr();
	String UUID=getSystemUUIDFacts();
	String consumerid=clienttasks.getCurrentConsumerId();
	Assert.assertEquals(UUID.trim(), consumerid.trim());
	CandlepinTasks.deleteResourceUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, "/consumers/"+consumerid);
	clienttasks.restart_rhsmcertd(null, null, false, null);
	sleep(2*60*1000);
	UUID=getSystemUUIDFacts();
	Assert.assertEquals(UUID, "");
	
	}
	
	
	public String getSystemUUIDFacts(){
	String value=null;
	value=client.runCommandAndWait("subscription-manager facts --list | grep -v dmi | grep system.uuid | cut -d ' ' -f2").getStdout();
//	value=client.runCommandAndWait("echo $factsList | cut -d ' ' -f2").getStdout();
	/*Map<String,String> factsMap = new HashMap<String,String>();
	List<String> factNames = new ArrayList<String>();
	String factNameRegex="^[\\w\\.\\(\\)-:]+: ";
	Pattern pattern = Pattern.compile(factNameRegex, Pattern.MULTILINE);
	Matcher matcher = pattern.matcher(factsListAsString);
	String factsListAsString = factsList.getStdout().trim();
	while (matcher.find()) {
		matcher.group();
		factNames.add(matcher.group());
	}
	
	int fromIndex=0;
	for (int f = 0; f < factNames.size(); f++) {
		String thisFactName = factNames.get(f);
		String thisFactValue;
		String nextFactName;
		if (f==factNames.size()-1) {
			thisFactValue = factsListAsString.substring(factsListAsString.indexOf(thisFactName,fromIndex)+thisFactName.length());	
		} else {
			nextFactName = factNames.get(f+1);
			thisFactValue = factsListAsString.substring(factsListAsString.indexOf(thisFactName,fromIndex)+thisFactName.length(), factsListAsString.indexOf(nextFactName,fromIndex));
		}
		fromIndex = factsListAsString.indexOf(thisFactName,fromIndex)+thisFactName.length();
		thisFactName = thisFactName.replaceFirst(": $", "");
		thisFactValue = thisFactValue.replaceFirst("\n$","");
		value=thisFactValue;
	}*/
	return value;
	}
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(	description="verify if Wrong DMI structures Error is filtered from the stderr of subscription-manager command line calls",
			groups={"WrongDMIstructuresError","blockedByBug-706552"},
			enabled=true)
	public void WrongDMIstructuresError() throws Exception {
	String result=	clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null).getStderr();
	Assert.assertContainsNoMatch(result, "Wrong DMI");
	 result=	clienttasks.facts(true, null, null, null, null).getStderr();
		Assert.assertContainsNoMatch(result, "Wrong DMI");
	}
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(	description="verify if subscription manager cli uses product name comparisons in the list command ",
			groups={"blockedByBug-709412","AcceptanceTests","InstalledProductMultipliesAfterSubscription"},
			enabled=true)
	public void InstalledProductMultipliesAfterSubscription() throws Exception {
		client.runCommandAndWait("mkdir /root/generatedCertsFolder");
		String serverurl="subscription.rhn.stage.redhat.com:443/subscription";
		String clientUsername="stage_test_12";
		if(!sm_serverType.equals(CandlepinType.hosted)) throw new SkipException("To be run against Stage only");
		
		clienttasks.register(clientUsername, sm_rhuiPassword,null, null, null, null, null, null, null, null,
				(String) null, serverurl, null, null, true, null, null, null, null).getStdout();
		moveProductCertFiles("*");
		client.runCommandAndWait("cp " + "/usr/share/rhsm/product/RHEL-*/Server*.pem" + " "
				+ "/root/generatedCertsFolder");
		client.runCommandAndWait("mv" + "/root/generatedCertsFolder" + " "+ clienttasks.productCertDir);
		List<InstalledProduct> InstalledProducts=clienttasks.getCurrentlyInstalledProducts();

		 List<SubscriptionPool> AvailablePools=clienttasks.getCurrentlyAllAvailableSubscriptionPools();
		 for(SubscriptionPool pools:AvailablePools){
			 clienttasks.subscribe(null, null,pools.poolId, null, null, null, null, null, null, null, null);
			 List<InstalledProduct> InstalledProductsAfterSubscribing=clienttasks.getCurrentlyInstalledProducts();
				Assert.assertEquals(InstalledProducts.size(), InstalledProductsAfterSubscribing.size());
				clienttasks.unsubscribeFromTheCurrentlyConsumedProductSubscriptionsCollectively();
		 }
		}
	
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(	description="verify Stacking of a future subscription and present subsciption make the product compliant ",
			groups={"StackingFutureSubscriptionWithCurrentSubscription","blockedByBug-966069"},
			enabled=true)
	public void StackingFutureSubscriptionWithCurrentSubscription() throws Exception {
		
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		clienttasks.autoheal(null, null, true, null, null, null);
		int sockets = 4;
		Map<String, String> factsMap = new HashMap<String, String>();
		factsMap.put("virt.is_guest", Boolean.FALSE.toString());	// stacking subscriptions based on sockets is only applicable on Physical systems now that vcpu compliance has been added to candlepin
		factsMap.put("cpu.cpu_socket(s)", String.valueOf(sockets));
		clienttasks.createFactsFileWithOverridingValues(factsMap);
		clienttasks.facts(null, true, null, null, null);
		Calendar now = new GregorianCalendar();
		DateFormat yyyy_MM_dd_DateFormat = new SimpleDateFormat("yyyy-MM-dd");
		now.add(Calendar.YEAR, 1);
		now.add(Calendar.DATE, 5);
		String onDateToTest = yyyy_MM_dd_DateFormat.format(now.getTime());
		for(SubscriptionPool availOnDate :getAvailableFutureSubscriptionsOndate(onDateToTest)){
			if(availOnDate.productId.equals("awesomeos-x86_64")){
				clienttasks.subscribe(null, null, availOnDate.poolId, null, null,null, null, null, null, null, null);
			}
		}
		for(SubscriptionPool pool :clienttasks.getCurrentlyAvailableSubscriptionPools()){
			if(pool.productId.equals("awesomeos-x86_64")){
				clienttasks.subscribe(null, null, pool.poolId, null, null,"2", null, null, null, null, null);
			}	
		}
		boolean testComplete=false;
		for (InstalledProduct installed : clienttasks.getCurrentlyInstalledProducts()) {
			if(installed.productId.equals("100000000000002")){
				Assert.assertEquals(installed.status, "Partially Subscribed");
				testComplete = true;
			}
		}
		if (!testComplete) throw new SkipException("Expected product from candlepin TESTDATA was not found to perform this test.");
	}
	
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(	description="verify the system compliance after deleting the consumer",
			groups={"ComplianceAfterConsumerDeletion"},
			enabled=true)
	public void ComplianceAfterConsumerDeletion() throws Exception {
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		String consumerid=clienttasks.getCurrentConsumerId();
		CandlepinTasks.deleteResourceUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, "/consumers/"+consumerid);
		String complianceStatus = CandlepinTasks.getConsumerCompliance(sm_serverAdminUsername, sm_serverAdminPassword, SubscriptionManagerBaseTestScript.sm_serverUrl, consumerid).getString("displayMessage");
	
		String message="Consumer "+consumerid+" has been deleted";
		if (!clienttasks.workaroundForBug876764(sm_serverType)) message = "Unit "+consumerid+" has been deleted";
		
		Assert.assertContainsMatch(message, complianceStatus);
		
	}
	
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(	description="verify if ipV4_Address is unknown in the facts list",
			groups={"VerifyIfIPV4_AddressIsUnknown","blockedByBug-694662"},
			enabled=true)
	public void VerifyIfIPV4_AddressIsUnknown() throws Exception {
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		Map<String, String> ipv4=clienttasks.getFacts("ipv4_address");
		Assert.assertFalse(ipv4.containsValue("unknown"));
	}
	
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(	description="verify if the whitespace in the title line of man subscription-manager-gui is completely consumed",
			groups={"ManPagesTest","blockedByBug-694662"},
			enabled=true)
	public void ManPagesTest() throws Exception {
		String expectedResult="subscription-manager-gui(8)  Subscription Manager  subscription-manager-gui(8)";
		String result=client.runCommandAndWait("man "+clienttasks.command+"-gui | head -1").getStdout();
		Assert.assertEquals(result.trim(), expectedResult);
	}
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(	description="verify if configurations like manage_repos have default values",
			groups={"defaultValueForManageRepos","blockedByBug-807721"},
			enabled=true)
	public void defaultValueForManageReposConfiguration() throws Exception {
		
	String result =clienttasks.config(true, null, null, (String[])null).getStdout();
	clienttasks.commentConfFileParameter(clienttasks.rhsmConfFile, "manage_repos");
	String resultAfterCommentingtheParameter=clienttasks.config(true, null, null, (String[])null).getStdout();
	Assert.assertEquals(result, resultAfterCommentingtheParameter);
	clienttasks.uncommentConfFileParameter(clienttasks.rhsmConfFile, "manage_repos");
		
	}
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(	description="verify if refresh pools will not notice change in provided products",
			groups={"RefreshPoolAfterChangeInProvidedProducts","blockedByBug-665118"},
			enabled=false)
	public void RefreshPoolAfterChangeInProvidedProducts() throws Exception {
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		
		String consumerId = clienttasks.getCurrentConsumerId();
		ownerKey = CandlepinTasks.getOwnerKeyOfConsumerId(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, consumerId);
		String name,productId;
		List<String> providedProductIds = new ArrayList<String>();
		name = "Test product to check pool refresh";
		productId = "test-product";
		Map<String,String> attributes = new HashMap<String,String>();
		attributes.clear();
		attributes.put("version", "1.0");
		attributes.put("variant", "server");
		attributes.put("arch", "ALL");
		attributes.put("warning_period", "30");
		attributes.put("type", "MKT");
		attributes.put("type", "SVC");
		String ProvidedProductTobeDeleted="37068";
		providedProductIds.add("37060");
		providedProductIds.add("37065");
		providedProductIds.add(ProvidedProductTobeDeleted);
		Integer contractNumber = getRandInt();
		Integer accountNumber = getRandInt();
		Calendar endCalendar = new GregorianCalendar();
		endCalendar.set(Calendar.HOUR_OF_DAY, 0);endCalendar.set(Calendar.MINUTE, 0);endCalendar.set(Calendar.SECOND, 0);	// avoid times in the middle of the day
		endCalendar.add(Calendar.MINUTE, 15*24*60);		// 15 days from today
		Date endDate = endCalendar.getTime();
		Calendar startCalendar = new GregorianCalendar();
		startCalendar.set(Calendar.HOUR_OF_DAY, 0);startCalendar.set(Calendar.MINUTE, 0);startCalendar.set(Calendar.SECOND, 0);	// avoid times in the middle of the day
		startCalendar.add(Calendar.MINUTE, -1*24*60);	// 1 day ago

		Date startDate = startCalendar.getTime();		
	//	CandlepinTasks.deleteSubscriptionsAndRefreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, sm_clientOrg, productId);
	//	CandlepinTasks.deleteResourceUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, "/products/"+productId);
		CandlepinTasks.createProductUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, name, productId, 1, attributes, null);
		String requestBody = CandlepinTasks.createSubscriptionRequestBody(20, startDate, endDate, productId, contractNumber, accountNumber, providedProductIds).toString();
		JSONObject jsonSubscription = new JSONObject(CandlepinTasks.postResourceUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword,sm_serverUrl,"/owners/" + ownerKey + "/subscriptions",requestBody));
		JSONObject jobDetail = CandlepinTasks.refreshPoolsUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword,sm_serverUrl,ownerKey);
		jobDetail = CandlepinTasks.waitForJobDetailStateUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword,sm_serverUrl,jobDetail,"FINISHED", 5*1000, 1);
		

		
		String poolId=null;
		
		for(SubscriptionPool pools:clienttasks.getCurrentlyAvailableSubscriptionPools()){
			if(pools.productId.equals(productId)){
				poolId=pools.poolId;
			}
		}
		System.out.println(poolId);
		
	String  result = (CandlepinTasks.getResourceUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword,sm_serverUrl, "/owners/admin/pools/")).toString();
		result=client.runCommandAndWait(result+" | grep "+poolId+" -B8 -A57").getStdout();
		System.out.println(result);

		if(!(poolId.equals(null))){		
			server.runCommandAndWait("su - postgres");
			server.runCommandAndWait("psql candlepin");
			server.runCommandAndWait("select * from cp_subscription_products where subscription_id='"+poolId+"';");
			server.runCommandAndWait("delete from cp_subscription_products where subscription_id='"+poolId+"' and product_id='"+ProvidedProductTobeDeleted+"';");
			jobDetail = CandlepinTasks.refreshPoolsUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword,sm_serverUrl,ownerKey);
			jobDetail = CandlepinTasks.waitForJobDetailStateUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword,sm_serverUrl,jobDetail,"FINISHED", 5*1000, 1);
			
			}
			else throw new SkipException("no pools available to test");
			jobDetail = CandlepinTasks.refreshPoolsUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword,sm_serverUrl,ownerKey);
			jobDetail = CandlepinTasks.waitForJobDetailStateUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword,sm_serverUrl,jobDetail,"FINISHED", 5*1000, 1);
						
			String resultafterRemoval = (CandlepinTasks.getResourceUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword,sm_serverUrl, "/owners/admin/pools/")).toString();
			resultafterRemoval=client.runCommandAndWait(result+" | grep "+poolId+" -B8 -A57").getStdout();
			System.out.println(resultafterRemoval );
	}
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(	description="verify Remote Server Exception is getting displayed for Server 500 Error",
			groups={"DisplayOfRemoteServerExceptionForServer500Error","blockedByBug-668814"},
			enabled=true)
	public void DisplayOfRemoteServerExceptionForServer500Error() throws Exception {
		String prefixValueBeforeExecution=clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "prefix");
		List<String[]> listOfSectionNameValues = new ArrayList<String[]>();
		listOfSectionNameValues.add(new String[] { "server","hostname".toLowerCase(),configuredHostname});
		listOfSectionNameValues.add(new String[] { "server","port".toLowerCase(), "8443" });
		listOfSectionNameValues.add(new String[] { "server","prefix", "/footestprefix" });
		clienttasks.config(null, null, true, listOfSectionNameValues);
		String RemoteError=clienttasks.register_(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null).getStdout();
		String Expected_Message=clienttasks.msg_NetworkErrorCheckConnection;
		listOfSectionNameValues.add(new String[] { "server",
				"prefix".toLowerCase(), prefixValueBeforeExecution.trim() });
		clienttasks.config(null, null, true, listOfSectionNameValues);
		Assert.assertEquals(RemoteError.trim(), Expected_Message);
		
	}
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(	description="verify Manual Changes To Redhat.Repo is sticky",
			groups={"ManualChangesToRedhat_Repo","blockedByBug-797243"},
			enabled=true)
	public void ManualChangesToRedhat_Repo() throws Exception {
		List<String[]> listOfSectionNameValues = new ArrayList<String[]>();
		listOfSectionNameValues.add(new String[] { "rhsmcertd",
				"autoAttachInterval".toLowerCase(), "1440" });
		clienttasks.config(null, null, true, listOfSectionNameValues);
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, false, null, null, null);
		List<SubscriptionPool> Availablepools=clienttasks.getCurrentlyAvailableSubscriptionPools();
		SubscriptionPool pool =Availablepools.get(randomGenerator.nextInt(Availablepools.size()));
		clienttasks.subscribeToSubscriptionPool(pool);
		for(Repo repo : clienttasks.getCurrentlySubscribedRepos()){
			if(repo.repoId.equals("always-enabled-content")){
				
				Assert.assertTrue(repo.enabled);
			}
		}		client.runCommandAndWait("sed -i \"/\\[always-enabled-content]/,/\\[/s/^enabled\\s*=.*/Enabled: false/\" /etc/yum.repos.d/redhat.repo");	
		for(Repo repo : clienttasks.getCurrentlySubscribedRepos()){
			if(repo.repoId.equals("always-enabled-content")){
				Assert.assertFalse(repo.enabled);
			}
		}
		client.runCommandAndWait(" yum repolist enabled");
		clienttasks.unsubscribeFromAllOfTheCurrentlyConsumedProductSubscriptions();
		String expected_message="This system has no repositories available through subscriptions.";
		String reposlist=clienttasks.repos(true, (String)null, null, null, null, null).getStdout();
			Assert.assertEquals(reposlist.trim(), expected_message);
		clienttasks.unregister(null, null, null);
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		reposlist=clienttasks.repos(true, (String)null, null, null, null, null).getStdout();
		Assert.assertEquals(reposlist.trim(), expected_message);
		clienttasks.subscribe(true, null,(String)null, null, null, null, null, null, null, null, null);
		for(Repo repo : clienttasks.getCurrentlySubscribedRepos()){
			if(repo.repoId.equals("always-enabled-content")){
				Assert.assertTrue(repo.enabled);
			}
		}
		}
		
		
	
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(	description="Extraneous / InRequest urls in the rhsm.log file",
			groups={"ExtraneousSlashInRequesturls","blockedByBug-848836"},
			enabled=true)
	public void ExtraneousSlashInRequesturls () throws Exception {
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		Boolean actual =false;
		String LogMarker = System.currentTimeMillis()+" Testing ***************************************************************";
		List<String[]> listOfSectionNameValues = new ArrayList<String[]>();
		listOfSectionNameValues.add(new String[] { "server",
				"prefix".toLowerCase(), "/candlepin" });
		clienttasks.config(null, null, true,listOfSectionNameValues);
		Boolean flag = RegexInRhsmLog("//",RemoteFileTasks.getTailFromMarkedFile(client, clienttasks.rhsmLogFile, LogMarker,"GET"));
		Assert.assertEquals(flag, actual);

	}
	
	
	
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(	description="verify the first system is unregistered when the second system is registered using consumerid of the first",
			groups={"SubscriptionManagerAccess","blockedByBug-878588"},
			enabled=false)
	public void SubscriptionManagerAccess() throws Exception {
		/*String username="testlogin";
		//String passwords="123testpassword";
		client.runCommandAndWait("useradd "+username);
		//client.runCommandAndWait("echo "+passwords+" | passwd "+username + " --stdin");
	//	String result=client.runCommandAndWait("su - "+username+" -c subscription-manager").getStderr();

	//	client.runCommandAndWait("su - "+username);
	//	System.out.println(result + "is the result");
		String expectedMessage="Error: this command requires root access to execute";
	//	Assert.assertEquals(client.getStderr().trim(), expectedMessage);
		//SSHCommandResult result=client.runCommandAndWait("su "+username);
		//result=client.runCommandAndWait(clienttasks.command);
		//client.runCommandAndWait("logout");
	//	client=new SSHCommandRunner(sm_clientHostname, sm_sshUser, sm_sshKeyPrivate,sm_sshkeyPassphrase,null);
		client.runCommandAndWait("userdel -r "+username);*/
		
		
	}
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(	description="verify the first system is unregistered when the second system is registered using consumerid of the first",
			groups={"RegisterTwoClientsUsingSameConsumerId","blockedByBug-949990"},
			enabled=false)
	public void RegisterTwoClientsUsingSameConsumerId() throws Exception {
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		String consumerid=clienttasks.getCurrentConsumerId();
		clienttasks.subscribe(true, null,(String)null, null, null,  null, null, null, null, null, null);
		List<ProductSubscription> consumedSubscriptionOnFirstMachine=clienttasks.getCurrentlyConsumedProductSubscriptions();
		client2tasks.register(sm_clientUsername, sm_clientPassword, null, null, null, null, consumerid, null, null, null,(String)null, null, null, null, true, null, null, null, null);
		String result=clienttasks.identity(null, null, null, null, null, null, null).getStdout();
		List<ProductSubscription> consumedSubscriptionOnSecondMachine=clienttasks.getCurrentlyConsumedProductSubscriptions();
		Assert.assertEquals(consumedSubscriptionOnFirstMachine, consumedSubscriptionOnSecondMachine);
		result=clienttasks.getCurrentConsumerId();
		Assert.assertEquals(result.trim(),consumerid);
		Assert.assertEquals(result.trim(), clienttasks.msg_ConsumerNotRegistered);
		
	}
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(	description="verify proxy option in repos list ",
			groups={"ProxyOptionForRepos"},
			enabled=true)
	public void ProxyOptionForRepos() throws Exception {
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		clienttasks.subscribe(true, null,(String)null, null, null, null, null, null, null, null, null);
		String result=clienttasks.repos_(true,(String)null,(String)null, sm_basicauthproxyHostname+":"+sm_basicauthproxyPort, null, null).getStdout();
		String expectedMessage="Network error, unable to connect to server."+"\n"+"Please see /var/log/rhsm/rhsm.log for more information.";
		Assert.assertNotSame(result.trim(), expectedMessage);
	}
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(	description="verify Future subscription added to the activation key ",
			groups={"AddingFutureSubscriptionToActivationKey"},
			enabled=true)
	public void AddingFutureSubscriptionToActivationKey() throws Exception {
		Integer addQuantity=1;
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		clienttasks.autoheal(null, null, true, null, null, null);
		Calendar now = new GregorianCalendar();
		DateFormat yyyy_MM_dd_DateFormat = new SimpleDateFormat("yyyy-MM-dd");
		now.add(Calendar.YEAR, 1);
		now.add(Calendar.DATE, 1);
		String onDateToTest = yyyy_MM_dd_DateFormat.format(now.getTime());
		List<SubscriptionPool> availOnDate = getAvailableFutureSubscriptionsOndate(onDateToTest);
		if(availOnDate.size()==0) throw new SkipException("Sufficient future pools are not available");
/* this breaks when the random available SubscriptionPool does not provide any products (e.g. Subscription Name: Management Add-On)
		int i= randomGenerator.nextInt(availOnDate.size());
*/
		int i=-1;
		availOnDate = getRandomSubsetOfList(availOnDate, availOnDate.size());	// randomize the order of the future available SubscriptionPools
		for (int j=0; j<availOnDate.size(); j++) {
			if (!availOnDate.get(j).provides.isEmpty()) {
				i=j;
				break;
			}
		}
		Assert.assertTrue(i!=-1, "Found a future available pool that provides products.");
	
		List<String> providedProductIds = CandlepinTasks.getPoolProvidedProductIds(sm_clientUsername,sm_clientPassword, sm_serverUrl, availOnDate.get(i).poolId);
		String name = String.format("%s_%s-ActivationKey%s", sm_clientUsername,
				sm_clientOrg, System.currentTimeMillis());
		Map<String, String> mapActivationKeyRequest = new HashMap<String, String>();
		mapActivationKeyRequest.put("name", name);
		JSONObject jsonActivationKeyRequest = new JSONObject(
				mapActivationKeyRequest);
		JSONObject jsonActivationKey = new JSONObject(
				CandlepinTasks.postResourceUsingRESTfulAPI(sm_clientUsername,
						sm_clientPassword, sm_serverUrl, "/owners/"
								+ sm_clientOrg + "/activation_keys",
								jsonActivationKeyRequest.toString()));
		
		new JSONObject(CandlepinTasks.postResourceUsingRESTfulAPI(sm_clientUsername, sm_clientPassword, sm_serverUrl, "/activation_keys/" + jsonActivationKey.getString("id") + "/pools/" +availOnDate.get(i).poolId+(addQuantity==null?"":"?quantity="+addQuantity), null));
		clienttasks.unregister(null, null, null);
		clienttasks.register(null, null, sm_clientOrg, null, null, null, null, null, null, null, name, null, null, null, true, null, null, null, null);			
		clienttasks.autoheal(null, null, true, null, null, null);
		for(InstalledProduct result:clienttasks.getCurrentlyInstalledProducts()){
/* we can be more thorough than this
			if(result.productId.equals(providedPools.get(randomGenerator.nextInt(providedProductIds.size())))){
*/
			if (providedProductIds.contains(result.productId)) {
				Assert.assertEquals(result.status, "Future Subscription");
			}
		}
	}
	
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(	description="verify Facts for change in OS ",
			groups={"createVirtOnlyPool"},
			enabled=false)
	public void createVirtOnlyPool () throws Exception {
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		clienttasks.getCurrentlyAllAvailableSubscriptionPools();
	String consumerId = clienttasks.getCurrentConsumerId();
	ownerKey = CandlepinTasks.getOwnerKeyOfConsumerId(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, consumerId);
	String name,productId;
	List<String> providedProductIds = new ArrayList<String>();
	name = "virt-only-product";
	productId = "virt-only test-product";
	Map<String,String> attributes = new HashMap<String,String>();
	attributes.put("version", "1.0");
	attributes.put("variant", "server");
	attributes.put("arch", "ALL");
	attributes.put("warning_period", "30");
	attributes.put("type", "MKT");
	attributes.put("type", "SVC");
	attributes.put("virt_limit", "4");
	CandlepinTasks.createProductUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, name+" BITS", productId, 1, attributes, null);
	CandlepinTasks.createSubscriptionAndRefreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, ownerKey, 20, -1*24*60/*1 day ago*/, 15*24*60/*15 days from now*/, getRandInt(), getRandInt(), productId, providedProductIds);
	clienttasks.getCurrentlyAllAvailableSubscriptionPools();
	CandlepinTasks.deleteSubscriptionsAndRefreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, sm_clientOrg, productId);
	CandlepinTasks.deleteResourceUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, "/products/"+productId);
	}
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(	description="verify Facts for change in OS ",
			groups={"FactsForChangeIn_OS"},
			enabled=true)
	@ImplementsNitrateTest(caseId=56387)
	public void FactsForChangeIn_OS () throws Exception {
		String originalhostname=clienttasks.hostname;
		String changedHostname="redhat";
		String result=clienttasks.getFactValue("network.hostname");
		Assert.assertEquals(result, originalhostname," Fact matches the hostname");
		client.runCommandAndWait("hostname "+changedHostname);
		result=clienttasks.getFactValue("network.hostname");
		Assert.assertEquals(result, changedHostname," Fact matches the hostname(After changing the hostname..)");
		
	}
	@AfterGroups(groups={"setup"},value="FactsForChangeIn_OS")
	public void restoreHostnameAfterFactsForChangeIn_OS() {
		if (clienttasks!=null) client.runCommandAndWait("hostname "+clienttasks.hostname);
	}
	
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(	description="Consumer unsubscribed when Subscription revoked",
			groups={"CRLTest"},
			enabled=true)
	@ImplementsNitrateTest(caseId=55355)
	public void CRLTest() throws Exception {
		clienttasks.deleteFactsFileWithOverridingValues();
		List<String[]> listOfSectionNameValues = new ArrayList<String[]>();
		listOfSectionNameValues.add(new String[] { "rhsmcertd",
				"autoAttachInterval".toLowerCase(), "1440" });
		clienttasks.config(null, null, true, listOfSectionNameValues);
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg ,null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		clienttasks.unsubscribe(true,(BigInteger)null, null, null, null);
		server.runCommandAndWait("rm -rf "+servertasks.candlepinCRLFile);
		List<SubscriptionPool> availPools = clienttasks.getCurrentlyAvailableSubscriptionPools(); 
			File entitlementCertFile=clienttasks.subscribeToSubscriptionPool(availPools.get(randomGenerator.nextInt(availPools.size())),sm_serverAdminUsername,sm_serverAdminPassword,sm_serverUrl);
			clienttasks.getEntitlementCertFromEntitlementCertFile(entitlementCertFile);
			EntitlementCert entitlementCert = clienttasks.getEntitlementCertFromEntitlementCertFile(entitlementCertFile);

			for (ProductSubscription consumed : clienttasks.getCurrentlyConsumedProductSubscriptions()) {
				System.out.println(consumed.serialNumber);
				clienttasks.unsubscribe(true,(BigInteger)null, null, null, null);
			}
			for(RevokedCert revokedCerts:servertasks.getCurrentlyRevokedCerts()){
				Assert.assertEquals(revokedCerts.serialNumber, entitlementCert.serialNumber);
			}
		
	}

	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(	description="Consumer unsubscribed when Subscription revoked",
			groups={"ConsumerUnsubscribedWhenSubscriptionRevoked","blockedByBug-947429"},
			enabled=true)
	@ImplementsNitrateTest(caseId=56025)
	public void ConsumerUnsubscribedWhenSubscriptionRevoked() throws Exception {
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		clienttasks.autoheal(null, null, true, null, null, null);
		String consumerId = clienttasks.getCurrentConsumerId();
		ownerKey = CandlepinTasks.getOwnerKeyOfConsumerId(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, consumerId);
		String name,productId;
		List<String> providedProductIds = new ArrayList<String>();
		name = "Test product to check subscription-removal";
		productId = "test-product";
		Map<String,String> attributes = new HashMap<String,String>();
		attributes.clear();
		attributes.put("version", "1.0");
		attributes.put("variant", "server");
		attributes.put("arch", "ALL");
		attributes.put("warning_period", "30");
		attributes.put("type", "MKT");
		attributes.put("type", "SVC");
		File entitlementCertFile=null;
		CandlepinTasks.deleteSubscriptionsAndRefreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, sm_clientOrg, productId);
		CandlepinTasks.deleteResourceUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, "/products/"+productId);
		clienttasks.unsubscribeFromAllOfTheCurrentlyConsumedProductSubscriptions();
		CandlepinTasks.createProductUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, name+" BITS", productId, 1, attributes, null);
		CandlepinTasks.createSubscriptionAndRefreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, sm_clientOrg, 20, -1*24*60/*1 day ago*/, 15*24*60/*15 days from now*/, getRandInt(), getRandInt(), productId, providedProductIds);
		server.runCommandAndWait("rm -rf "+servertasks.candlepinCRLFile);
		for(SubscriptionPool pool:clienttasks.getCurrentlyAllAvailableSubscriptionPools()){
			if(pool.productId.equals(productId)){
				 entitlementCertFile=	clienttasks.subscribeToSubscriptionPool(pool,sm_serverAdminUsername,sm_serverAdminPassword,sm_serverUrl);
			}
		}
		Assert.assertNotNull(entitlementCertFile,"Successfully created and subscribed to product subscription '"+productId+"' created by and needed for this test.");
		EntitlementCert entitlementCert = clienttasks.getEntitlementCertFromEntitlementCertFile(entitlementCertFile);
		List <ProductSubscription> consumedSusbscription=clienttasks.getCurrentlyConsumedProductSubscriptions();
		Assert.assertFalse(consumedSusbscription.isEmpty());
		CandlepinTasks.deleteSubscriptionsAndRefreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, sm_clientOrg, productId);
		CandlepinTasks.deleteResourceUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, "/products/"+productId);
/* this is not the right way to assert the revokedCerts contain the entitlementCert.serialNumber; re-implementing below...
		clienttasks.autoheal(null, true, null, null, null, null);
		clienttasks.restart_rhsmcertd(null, null, false, null);
		List <RevokedCert> revokedCerts=servertasks.getCurrentlyRevokedCerts();
		Assert.assertTrue(revokedCerts.contains(entitlementCert.serialNumber));
*/
		// sleep long enough for the CertificateRevocationListTask on the server to update
		//	[root@jsefler-f14-candlepin candlepin]# grep CertificateRevocationListTask.schedule /etc/candlepin/candlepin.conf 
		//	pinsetter.org.fedoraproject.candlepin.pinsetter.tasks.CertificateRevocationListTask.schedule=0 0/2 * * * ?
		//	pinsetter.org.candlepin.pinsetter.tasks.CertificateRevocationListTask.schedule=0 0/2 * * * ?
		sleep(2/*min*/*60*1000);
		
		// verify the entitlement serial has been added to the CRL on the server
		List<RevokedCert> revokedCerts=servertasks.getCurrentlyRevokedCerts();
		RevokedCert revokedCert = RevokedCert.findFirstInstanceWithMatchingFieldFromList("serialNumber", entitlementCert.serialNumber, revokedCerts);
		Assert.assertNotNull(revokedCert,"The Certificate Revocation List file on the candlepin server contains an entitlement serial '"+entitlementCert.serialNumber+"' to the product subscription '"+productId+"' that was just deleted on the candlepin server.");
		
		// trigger the rhsmcertd on the system and verify the entitlement has been removed
		clienttasks.run_rhsmcertd_worker(false);
		Assert.assertTrue(clienttasks.getCurrentlyConsumedProductSubscriptions().isEmpty(), "The revoked entitlement has been removed from the system by rhsmcertd.");
	}
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "do not persist --serverurl option values to rhsm.conf when calling subscription-manager modules: orgs, environment, service-level", 
			groups = { "blockedByBug-889573","AcceptanceTests"}, enabled = true)
	public void ServerUrloptionValuesInRHSMFile() throws JSONException,Exception {
		if(!sm_serverType.equals(CandlepinType.hosted)) throw new SkipException("To be run against Stage only");
	String clientUsername="stage_test_12";
	String serverurl="subscription.rhn.stage.redhat.com:443/subscription";
	String hostnameBeforeExecution=clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "hostname");
	String portBeforeExecution=clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "port");
	String prefixBeforeExecution=clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "prefix");
	clienttasks.register(clientUsername, sm_rhuiPassword,null, null, null, null, null, null, null, null,
			(String) null, serverurl, null, null, true, null, null, null, null).getStdout();	List<String[]> listOfSectionNameValues = new ArrayList<String[]>();
	listOfSectionNameValues.add(new String[] { "server","hostname".toLowerCase(),hostnameBeforeExecution});
	listOfSectionNameValues.add(new String[] { "server","port".toLowerCase(), "8443" });
	listOfSectionNameValues.add(new String[] { "server","prefix".toLowerCase(), "/candlepin" });
	clienttasks.config(null, null, true, listOfSectionNameValues);
	clienttasks.orgs(clientUsername, sm_rhuiPassword, serverurl, null, null, null, null);
	Assert.assertEquals(clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "hostname"), hostnameBeforeExecution);
	Assert.assertEquals(clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "port"),portBeforeExecution);
	Assert.assertEquals(clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "prefix"),prefixBeforeExecution);
	
	clienttasks.service_level(true, null, null, null, clientUsername, sm_rhuiPassword, null, serverurl, null, null, null, null);
	Assert.assertEquals(clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "hostname"), hostnameBeforeExecution);
	Assert.assertEquals(clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "port"),portBeforeExecution);
	Assert.assertEquals(clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "prefix"),prefixBeforeExecution);
	clienttasks.environments(clientUsername, sm_rhuiPassword, null, serverurl, null, null, null, null);
	Assert.assertEquals(clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "hostname"), hostnameBeforeExecution);
	Assert.assertEquals(clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "port"),portBeforeExecution);
	Assert.assertEquals(clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "prefix"),prefixBeforeExecution);
	}
	
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "verify if CLI lets you set consumer nameto empty string and defaults to sm_clientUsername", 
			groups = { "VerifyConsumerNameTest","blockedByBug-669395"}, enabled = true)
		public void VerifyConsumerNameTest() throws JSONException,Exception {
		String consumerName="tester";
		clienttasks.register(sm_clientUsername, sm_clientPassword,sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		String result=clienttasks.identity(null, null, null, null, null, null, null).getStdout();
		Assert.assertContainsMatch(result, "name: "+clienttasks.hostname);
		clienttasks.register(sm_clientUsername, sm_clientPassword,sm_clientOrg, null, null, consumerName, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		result=clienttasks.identity(null, null, null, null, null, null, null).getStdout();
		String expected="name: "+consumerName;
		Assert.assertContainsMatch(result, expected);
		String consumerId=clienttasks.getCurrentConsumerId();
		clienttasks.clean(null, null, null);
		consumerName="consumer";
		clienttasks.register(sm_clientUsername, sm_clientPassword,sm_clientOrg, null, null, consumerName, consumerId, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		result=clienttasks.identity(null, null, null, null, null, null, null).getStdout();
		Assert.assertContainsMatch(result, expected);
		clienttasks.clean(null, null, null);
		result=clienttasks.register_(sm_clientUsername, sm_clientPassword,sm_clientOrg, null, null, "", consumerId, null, null, null,
				(String) null, null, null, null, true, null, null, null, null).getStdout();
		Assert.assertEquals(result.trim(), "Error: system name can not be empty.");
		result=clienttasks.register_(sm_clientUsername, sm_clientPassword,sm_clientOrg, null, null, "", null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null).getStdout();
		Assert.assertEquals(result.trim(), "Error: system name can not be empty.");
	}
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "verify if CLI auto-subscribe tries to re-use basic auth credentials.",
			groups = { "VerifyAutosubscribeReuseBasicAuthCredntials","blockedByBug-707641","blockedByBug-919700"}, enabled = true)
		public void VerifyAutosubscribeReuseBasicAuthCredntials() throws JSONException,Exception {
		clienttasks.register(sm_clientUsername, sm_clientPassword,sm_clientOrg, null, null, null, null, true, null, null,
				(String) null, null, null, null, true, null, null, null, null);	
		String LogMarker = System.currentTimeMillis()+" Testing ***************************************************************";
		client.runCommandAndWait("ssh root@"+sm_serverHostname);
		RemoteFileTasks.markFile(server, servertasks.tomcat6LogFile, LogMarker);
		String logMessage=" Authentication check for /consumers/"+clienttasks.getCurrentConsumerId()+"/entitlements";
		Assert.assertTrue(RemoteFileTasks.getTailFromMarkedFile(server,servertasks.tomcat6LogFile, LogMarker, logMessage).trim().equals(""));

	}
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	//To be tested against stage
	@Test(description = "verify if 500 errors in stage on subscribe/unsubscribe",
			groups = {"blockedByBug-878994","AcceptanceTests"},
			enabled = true)
		public void Verify500ErrorOnStage() throws JSONException,Exception {
		if(!sm_serverType.equals(CandlepinType.hosted)) throw new SkipException("To be run against Stage only");

		//server=new SSHCommandRunner(sm_serverHostname, sm_sshUser, sm_sshKeyPrivate,sm_sshkeyPassphrase,null);
		server.runCommandAndWait("find "+sm_serverInstallDir+servertasks.generatedProductsDir+" -name '*.pem'");
		clienttasks.unregister(null, null, null);
		log.info("Fetching the generated product certs...");
		String logMessage = "remote server status code: 500";
		String serverurl="subscription.rhn.stage.redhat.com:443/subscription";
		String clientUsername="stage_test_12";
		clienttasks.register(clientUsername, sm_rhuiPassword,null, null, null, null, null, null, null, null,
				(String) null, serverurl, null, null, null, null, null, null, null).getStdout();	
		String LogMarker = System.currentTimeMillis()+" Testing ***************************************************************";
		RemoteFileTasks.markFile(client, clienttasks.rhsmLogFile, LogMarker);
		String result=clienttasks.listAvailableSubscriptionPools().getStdout();
		Assert.assertTrue(RemoteFileTasks.getTailFromMarkedFile(client, clienttasks.rhsmLogFile, LogMarker, logMessage).trim().equals(""));
		Assert.assertNoMatch(result.trim(), clienttasks.msg_NetworkErrorCheckConnection);
		RemoteFileTasks.markFile(client, clienttasks.rhsmLogFile, LogMarker);
		result=clienttasks.subscribe_(true,(String)null,(String)null,(String)null, null, null, null, null, null, null, null).getStdout();
		Assert.assertTrue(RemoteFileTasks.getTailFromMarkedFile(client, clienttasks.rhsmLogFile, LogMarker, logMessage).trim().equals(""));
		Assert.assertNoMatch(result.trim(), clienttasks.msg_NetworkErrorCheckConnection);
		RemoteFileTasks.markFile(client, clienttasks.rhsmLogFile, LogMarker);
		result=clienttasks.unregister(null, null, null).getStdout();
		Assert.assertTrue(RemoteFileTasks.getTailFromMarkedFile(client, clienttasks.rhsmLogFile, LogMarker, logMessage).trim().equals(""));
		Assert.assertNoMatch(result.trim(), clienttasks.msg_NetworkErrorCheckConnection);
	}
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "verify if redhat repo is created subscription-manager yum plugin when the repo is not present", 
			groups = { "RedhatrepoNotBeingCreated","blockedByBug-886992","blockedByBug-919700"}, enabled = true)
		public void RedhatrepoNotBeingCreated() throws JSONException,Exception {
		client.runCommandAndWait("mv /etc/yum.repos.d/redhat.repo /root/").getStdout();
		List<String[]> listOfSectionNameValues = new ArrayList<String[]>();
		listOfSectionNameValues.add(new String[] { "rhsm",
				"manage_repos".toLowerCase(), "1" });
		clienttasks.config(null, null, true, listOfSectionNameValues);
		clienttasks.register(sm_clientUsername, sm_clientPassword,sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		clienttasks.subscribe(true, null, (String)null, null, null, null, null, null, null, null, null);
		Assert.assertTrue(RemoteFileTasks.testExists(client,"/etc/yum.repos.d/redhat.repo"));
		String result=client.runCommandAndWait("yum repolist all").getStdout();
		Assert.assertContainsMatch(result, "repo id");
	}
	
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "verify if  insecure in rhsm.comf getse updated when using --insecure option if command fails", 
			groups = { "InsecureValueInRHSMConfAfterRegistrationFailure","blockedByBug-916369"}, enabled = true)
		public void InsecureValueInRHSMConfAfterRegistrationFailure() throws JSONException,Exception {
		String defaultHostname = "rhel7.com";
		String defaultPort = "8443";
		String defaultPrefix = "candlepin";
		String org="foo";
		String valueBeforeRegister=clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "insecure");
		clienttasks.register_(sm_clientUsername, sm_clientPassword,org, null, null, null, null, null, null, null,(String) null,defaultHostname+":"+defaultPort+"/"+defaultPrefix, null, null, null, null, null, null, null);
		String valueAfterRegister = clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "insecure");
		Assert.assertEquals(valueBeforeRegister, valueAfterRegister);
	}
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "verify if subscription-manager register fails with consumerid and activationkey specified", 
			groups = { "RegisterActivationKeyAndConsumerID","blockedByBug-749636"}, enabled = true)
		public void RegisterActivationKeyAndConsumerID() throws JSONException,Exception {
		String name = String.format("%s_%s-ActivationKey%s", sm_clientUsername,
				sm_clientOrg, System.currentTimeMillis());
		Map<String, String> mapActivationKeyRequest = new HashMap<String, String>();
		mapActivationKeyRequest.put("name", name);
		JSONObject jsonActivationKeyRequest = new JSONObject(
				mapActivationKeyRequest);
		JSONObject jsonActivationKey = new JSONObject(
				CandlepinTasks.postResourceUsingRESTfulAPI(sm_clientUsername,
						sm_clientPassword, sm_serverUrl, "/owners/"
								+ sm_clientOrg + "/activation_keys",
								jsonActivationKeyRequest.toString()));
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		String consumerID=clienttasks.getCurrentConsumerId();
		clienttasks.unregister(null, null, null);
		String result=clienttasks.register_(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, consumerID, null, null, null,
				jsonActivationKey.get("name").toString(), null, null, null, null, null, null, null, null).getStdout();
		String expected="Error: Activation keys do not require user credentials.";
		Assert.assertEquals(result.trim(), expected);
		
		
	}
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "verify if Product id is displayed in intsalled list", 
			groups = { "ProductIdInInstalledList","blockedByBug-803386"}, enabled = true)
		public void ProductIdInInstalledList() throws JSONException,Exception {
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		for(InstalledProduct result:clienttasks.getCurrentlyInstalledProducts()){
			Assert.assertNotNull(result.productId);
			
		}
		
	}
	
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "verify if Entitlement certs are downloaded if subscribed to expired pool", 
			groups = { "ServerURLInRHSMFile","blockedByBug-916353"}, enabled = true)
		public void ServerURLInRHSMFile() throws JSONException,Exception {
		String defaultHostname = "rhel7.com";
		String defaultPort = "8443";
		String defaultPrefix = "/candlepin";
		String org="foo";
		String valueBeforeRegister=clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "hostname");
		clienttasks.register_(sm_clientUsername, sm_clientPassword,org, null, null, null, null, null, null, null,(String) null,defaultHostname+":"+defaultPort+"/"+defaultPrefix, null, null, null, null, null, null, null);
		String valueAfterRegister = clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "hostname");
		Assert.assertEquals(valueBeforeRegister, valueAfterRegister);
		
	}
	
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "verify if Entitlement certs are downloaded if subscribed to expired pool", 
			groups = {"DipslayServicelevelWhenRegisteredToDifferentServer","blockedByBug-916362"}, enabled = true)
		public void DipslayServicelevelWhenRegisteredToDifferentServer() {
		String defaultHostname = "subscription.rhn.redhat.com";
		String defaultPort = "443";
		String defaultPrefix = "/subscription";
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		String result=clienttasks.service_level_(null, null, null, null, null, null, null,defaultHostname+":"+defaultPort+defaultPrefix , null, null, null, null).getStdout();
		String expectedResult = "You are already registered to a different system";
		if (/*bug 916362 is CLOSED NOTABUG is */true) {
			log.warning("Altering the original expected result '"+expectedResult+"' since Bug 916362 has been CLOSED NOTABUG.");
			log.warning("For more explanation see https://bugzilla.redhat.com/show_bug.cgi?id=916362#c3");
			expectedResult = "Unable to verify server's identity: tlsv1 alert unknown ca";
		}
		Assert.assertEquals(result.trim(), expectedResult);
	}
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "verify expiration of entitlement certs", 
			groups = {"ExpirationOfEntitlementCerts","blockedByBug-907638","blockedByBug-953830"}, enabled = true)
		public void ExpirationOfEntitlementCerts() throws JSONException,Exception {
		int endingMinutesFromNow =1;
/* unnecessary
		System.out.println(getProperty("sm.server.admin.password",""));
		List<String[]> listOfSectionNameValues = new ArrayList<String[]>();
		listOfSectionNameValues.add(new String[] { "rhsmcertd",
				"certCheckInterval".toLowerCase(), "1" });
		listOfSectionNameValues.add(new String[] { "rhsmcertd",
				"autoAttachInterval".toLowerCase(), "1440" });
		clienttasks.config(null, null, true, listOfSectionNameValues);
*/
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, false, null, null, null);
/* unnecessary
		String productId=null;
		clienttasks.unsubscribeFromAllOfTheCurrentlyConsumedProductSubscriptions();
*/
		String consumerId = clienttasks.getCurrentConsumerId();
		ownerKey = CandlepinTasks.getOwnerKeyOfConsumerId(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, consumerId);
		Calendar endCalendar = new GregorianCalendar();
		endCalendar.add(Calendar.MINUTE, endingMinutesFromNow);
		Date endDate = endCalendar.getTime();	// caution - if the next call to createTestPool does not occur within this minute; endDate will be 1 minute behind reality
		String expiringPoolId = createTestPool(-60*24,endingMinutesFromNow);
/* another way to do this is call SubscriptionPool.findFirstInstanceWithMatchingFieldFromList(...)
		for(SubscriptionPool pool:clienttasks.getCurrentlyAvailableSubscriptionPools()){
			if(pool.poolId.equals(expiringPoolId)){
				productId=pool.productId;
			}
		}
*/
		SubscriptionPool expiringSubscriptionPool = SubscriptionPool.findFirstInstanceWithMatchingFieldFromList("poolId", expiringPoolId, clienttasks.getCurrentlyAvailableSubscriptionPools());

		// attaching from the pool that is about to expire should still be successful
		//clienttasks.subscribe(null, null, expiringPoolId, null, null, null, null, null, null, null, null);
		File expiringEntitlementFile = clienttasks.subscribeToSubscriptionPool(expiringSubscriptionPool,sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl);
		EntitlementCert expiringEntitlementCert = clienttasks.getEntitlementCertFromEntitlementCertFile(expiringEntitlementFile);
		clienttasks.unsubscribeFromSerialNumber(expiringEntitlementCert.serialNumber);
		
		// wait for the pool to expire
		sleep(endingMinutesFromNow*60*1000);
		
		// attempt to attach an entitlement from an expired pool
		String result=clienttasks.subscribe_(null, null, expiringPoolId, null, null, null, null, null, null, null, null).getStdout();	
		// Stdout: Unable to attach pool with ID '8a908740438be86501438cd57718376c'.: Subscriptions for awesomeos-onesocketib expired on: 1/3/14 1:21 PM.
		String expiredOnDatePattern = "M/d/yy h:mm a";	//	1/3/14 1:21 PM
		DateFormat expiredOnDateFormat = new SimpleDateFormat(expiredOnDatePattern);
		String expiredOnString = expiredOnDateFormat.format(endDate.getTime());
/* 
		String expected="Unable to entitle consumer to the pool with id '"+expiringPoolId+"'.: Subscriptions for "+productId+" expired on: "+EndingDate;
*/
		String expected = String.format("Unable to attach pool with ID '%s'.: Subscriptions for %s expired on: %s.",expiringSubscriptionPool.poolId,expiringSubscriptionPool.productId,expiredOnString);
		Assert.assertEquals(result.trim(), expected);
}
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "verify if Entitlement certs are downloaded if subscribed to expired pool", 
			groups = {"SubscribeToexpiredEntitlement","blockedByBug-907638"}, enabled = true)
		public void SubscribeToexpiredEntitlement() throws JSONException,Exception {
/* unnecessary
		List<String[]> listOfSectionNameValues = new ArrayList<String[]>();
		listOfSectionNameValues.add(new String[] { "rhsmcertd",
				"certCheckInterval".toLowerCase(), "1" });
		listOfSectionNameValues.add(new String[] { "rhsmcertd",
				"autoAttachInterval".toLowerCase(), "1440" });
		clienttasks.config(null, null, true, listOfSectionNameValues);
*/
		int endingMinutesFromNow = 1;
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, false, null, null, null);
		String consumerId = clienttasks.getCurrentConsumerId();
		ownerKey = CandlepinTasks.getOwnerKeyOfConsumerId(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, consumerId);
		String expiringPoolId = createTestPool(-60*24,endingMinutesFromNow);
		sleep(endingMinutesFromNow*60*1000);
		clienttasks.subscribe_(null, null, expiringPoolId, null, null, null, null, null, null, null, null);
		Assert.assertTrue(clienttasks.getCurrentlyConsumedProductSubscriptions().isEmpty());
		Assert.assertTrue(clienttasks.getCurrentEntitlementCertFiles().isEmpty());
		}
	
	
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "verify if no multiple repos are created,if subscribed to a product that share one or more engineering subscriptions", 
			groups = { "NoMultipleReposCreated"}, enabled = true)
		public void NoMultipleReposCreated() throws JSONException,Exception {
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		String consumerId = clienttasks.getCurrentConsumerId();
		ownerKey = CandlepinTasks.getOwnerKeyOfConsumerId(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, consumerId);
		CandlepinTasks.deleteSubscriptionsAndRefreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, sm_clientOrg,"multi-stackable");
		CandlepinTasks.refreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, ownerKey);
		CandlepinTasks.deleteResourceUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword, sm_serverUrl,"/products/" + "multi-stackable");
		Calendar calendar = new GregorianCalendar();	// right now
		Date todaysDate = calendar.getTime();
		calendar.add(Calendar.YEAR, 1);
		calendar.add(Calendar.DATE, 10);
		calendar.set(Calendar.HOUR_OF_DAY, 0);calendar.set(Calendar.MINUTE, 0);calendar.set(Calendar.SECOND, 0);	// avoid times in the middle of the day
		Date futureDate = calendar.getTime();
		Map<String,String> attributes = new HashMap<String,String>();
		attributes.put("sockets", "8");
		attributes.put("arch", "ALL");
		attributes.put("type", "MKT");
		attributes.put("multi-entitlement", "yes");
		attributes.put("stacking_id", "726409");
		List<String> providedProducts = new ArrayList<String>();
		providedProducts.add("100000000000002");
		providedProducts.add("100000000000001");
		CandlepinTasks.createProductUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword, sm_serverUrl,"Multi-Stackable","multi-stackable", 1,attributes ,null);
		String requestBody = CandlepinTasks.createSubscriptionRequestBody(20, todaysDate, futureDate,"multi-stackable", Integer.valueOf(getRandInt()), Integer.valueOf(getRandInt()), providedProducts).toString();
		CandlepinTasks.postResourceUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, "/owners/" + ownerKey + "/subscriptions", requestBody);	
		JSONObject jobDetail = CandlepinTasks.refreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, ownerKey);
		CandlepinTasks.waitForJobDetailStateUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword,sm_serverUrl,jobDetail,"FINISHED", 5*1000, 1);
		sleep(3*60*1000);
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		for(SubscriptionPool pools:clienttasks.getCurrentlyAvailableSubscriptionPools()){
			if(pools.subscriptionName.equals("Multi-Stackable")){
			clienttasks.subscribe_(null, null,pools.poolId, null, null, null, null, null, null, null, null);	
			}
		}
		String productIdOne=null;
		List<Repo> originalRepos =clienttasks.getCurrentlySubscribedRepos();
		for (Repo repo : originalRepos) {
			String productIdTwo=null;
			productIdOne=repo.repoId;
			if(!(productIdTwo==null)){
			Assert.assertNotSame(repo.repoId, productIdOne);
			}
			productIdTwo=productIdOne;
		}
		CandlepinTasks.deleteSubscriptionsAndRefreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, sm_clientOrg,"multi-stackable");
		CandlepinTasks.refreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, ownerKey);
		CandlepinTasks.deleteResourceUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword, sm_serverUrl,"/products/" + "multi-stackable");
			
		}

				
	
	
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "verify if bind and unbind event is recorded in syslog", 
			groups = { "DeleteContentSourceFromProduct","blockedByBug-687970"}, enabled = true)
	public void DeleteContentSourceFromProduct() throws JSONException,Exception {
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		 List<String> modifiedProductIds=null;
		 String contentId= "99999";
		 Map<String,String> attributes = new HashMap<String,String>();
		 attributes.put("sockets", "8");
		 attributes.put("arch", "ALL");
		String requestBody = CandlepinTasks.createContentRequestBody("fooname", contentId, "foolabel", "yum", "Foo Vendor", "/foo/path", "/foo/path/gpg", null, null, null, modifiedProductIds).toString();
		CandlepinTasks.postResourceUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl,"/content", requestBody);	
		JSONObject jsonActivationKey = new JSONObject(CandlepinTasks.getResourceUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, "/content/"+contentId));
		Assert.assertContainsNoMatch(jsonActivationKey.toString(), "Content with id "+contentId+" could not be found.");
		CandlepinTasks.deleteResourceUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, "/content/"+contentId);
/* restart_rhsmcertd has nothing to do with testing the ability to DELETE a content resource
		clienttasks.restart_rhsmcertd(null, null, false, null);
		sleep(2*60*1000);
*/
		jsonActivationKey = new JSONObject(CandlepinTasks.getResourceUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, "/content/"+contentId));
		Assert.assertEquals(jsonActivationKey.getString("displayMessage"), "Content with id "+contentId+" could not be found.");
		requestBody = CandlepinTasks.createContentRequestBody("fooname", contentId, "foolabel", "yum", "Foo Vendor", "/foo/path", "/foo/path/gpg", null, null, null, modifiedProductIds).toString();
		CandlepinTasks.postResourceUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl,"/content", requestBody);
		CandlepinTasks.createProductUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword, sm_serverUrl,"fooname","fooproduct", null,attributes ,null);
		CandlepinTasks.postResourceUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl,"/products/fooproduct/content/"+contentId+"?enabled=false",null);
		CandlepinTasks.deleteResourceUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, "/content/"+contentId);
		jsonActivationKey = new JSONObject(CandlepinTasks.getResourceUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, "/content/"+contentId));
		Assert.assertContainsNoMatch(jsonActivationKey.toString(), "Content with id "+contentId+" could not be found.");
		CandlepinTasks.deleteResourceUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword, sm_serverUrl,"/products/" + "fooproduct");

	}
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "verify if bind and unbind event is recorded in syslog", 
			groups = {"VerifyBindAndUnbindInSyslog","blockedByBug-919700"}, enabled = true)
	@ImplementsNitrateTest(caseId=68740)
	public void VerifyBindAndUnbindInSyslog() throws JSONException,Exception {
		String logMarker, expectedSyslogMessage, tailFromSyslogFile;
		
		clienttasks.register(sm_clientUsername, sm_clientPassword,sm_clientOrg, null, null, null, null, null, null, null, (String) null, null, null, null, true, false, null, null, null);
		
		logMarker = System.currentTimeMillis()+" Testing Subscribe **********************";
		RemoteFileTasks.markFile(client, clienttasks.messagesLogFile, logMarker);
		List<SubscriptionPool> pools = clienttasks.getCurrentlyAvailableSubscriptionPools();
		List<String> poolIds = new ArrayList<String>(); for (SubscriptionPool pool : pools) poolIds.add(pool.poolId);
		clienttasks.subscribe(null, null, poolIds, null, null, null, null, null, null, null, null);
		tailFromSyslogFile = RemoteFileTasks.getTailFromMarkedFile(client, clienttasks.messagesLogFile, logMarker, null);
		for (SubscriptionPool pool : pools) {
			//	Feb  3 12:08:01 jsefler-7 subscription-manager: Added subscription for 'Awesome OS Stackable guest limit 4' contract '2'
			//	Feb  3 12:08:01 jsefler-7 subscription-manager: Added subscription for product 'Awesome OS Server Bits'
			expectedSyslogMessage = String.format("%s: Added subscription for '%s' contract '%s'", clienttasks.command, pool.subscriptionName,pool.contract);
			Assert.assertTrue(tailFromSyslogFile.contains(expectedSyslogMessage),"After subscribing to '"+pool.subscriptionName+"', syslog '"+clienttasks.messagesLogFile+"' contains expected message '"+expectedSyslogMessage+"'.");
			for (String providedProduct : pool.provides) {
				// TEMPORARY WORKAROUND FOR BUG: https://bugzilla.redhat.com/show_bug.cgi?id=1016300
				if (providedProduct.equals("Awesome OS Server Bundled")) {
					boolean invokeWorkaroundWhileBugIsOpen = true;
					String bugId="1016300"; 
					try {if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");SubscriptionManagerCLITestScript.addInvokedWorkaround(bugId);} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
					if (invokeWorkaroundWhileBugIsOpen) {
						log.warning("Ignoring the provided MKT product '"+providedProduct+"'.  No syslog assertion for this product will be made.");
						continue;
					}
				}
				// END OF WORKAROUND
				expectedSyslogMessage = String.format("%s: Added subscription for product '%s'", clienttasks.command, providedProduct);
				Assert.assertTrue(tailFromSyslogFile.contains(expectedSyslogMessage),"After subscribing to '"+pool.subscriptionName+"', syslog '"+clienttasks.messagesLogFile+"' contains expected message '"+expectedSyslogMessage+"'.");
			}
		}	
		
		logMarker = System.currentTimeMillis()+" Testing Unsubscribe **********************";
		RemoteFileTasks.markFile(client, clienttasks.messagesLogFile, logMarker);
		List<ProductSubscription> productSubscriptions = clienttasks.getCurrentlyConsumedProductSubscriptions();
		clienttasks.unsubscribe(true,(BigInteger)null, null, null, null);
		tailFromSyslogFile = RemoteFileTasks.getTailFromMarkedFile(client, clienttasks.messagesLogFile, logMarker, null);
		for (ProductSubscription productSubscription : productSubscriptions) {
			//	Feb  3 13:32:34 jsefler-7 subscription-manager: Removed subscription for 'Awesome OS Server Bundled (2 Sockets, Standard Support)' contract '3'
			//	Feb  3 13:32:34 jsefler-7 subscription-manager: Removed subscription for product 'Clustering Bits'
			//	Feb  3 13:32:34 jsefler-7 subscription-manager: Removed subscription for product 'Awesome OS Server Bits'
			//	Feb  3 13:32:34 jsefler-7 subscription-manager: Removed subscription for product 'Load Balancing Bits'
			//	Feb  3 13:32:34 jsefler-7 subscription-manager: Removed subscription for product 'Large File Support Bits'
			//	Feb  3 13:32:34 jsefler-7 subscription-manager: Removed subscription for product 'Shared Storage Bits'
			//	Feb  3 13:32:34 jsefler-7 subscription-manager: Removed subscription for product 'Management Bits'
			expectedSyslogMessage = String.format("%s: Removed subscription for '%s' contract '%s'", clienttasks.command, productSubscription.productName,productSubscription.contractNumber);
			Assert.assertTrue(tailFromSyslogFile.contains(expectedSyslogMessage),"After unsubscribing from '"+productSubscription.productName+"', syslog '"+clienttasks.messagesLogFile+"' contains expected message '"+expectedSyslogMessage+"'.");
			for (String providedProduct : productSubscription.provides) {
				expectedSyslogMessage = String.format("%s: Removed subscription for product '%s'", clienttasks.command, providedProduct);
				Assert.assertTrue(tailFromSyslogFile.contains(expectedSyslogMessage),"After unsubscribing from '"+productSubscription.productName+"', syslog '"+clienttasks.messagesLogFile+"' contains expected message '"+expectedSyslogMessage+"'.");
			}
		}
	}
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "verify if register and unregister event is recorded in syslog", 
			groups = {"VerifyRegisterAndUnregisterInSyslog"}, enabled = true)
	@ImplementsNitrateTest(caseId=68749)
	public void VerifyRegisterAndUnregisterInSyslog() throws JSONException,Exception {
		String logMarker, expectedSyslogMessage, tailFromSyslogFile;
		
		logMarker = System.currentTimeMillis()+" Testing Register **********************";
		RemoteFileTasks.markFile(client, clienttasks.messagesLogFile, logMarker);
		String identity = clienttasks.getCurrentConsumerId(clienttasks.register(sm_clientUsername, sm_clientPassword,sm_clientOrg, null, null, null, null, null, null, null, (String) null, null, null, null, true, false, null, null, null));
		tailFromSyslogFile = RemoteFileTasks.getTailFromMarkedFile(client, clienttasks.messagesLogFile, logMarker, null);
		//	Feb  3 12:50:47 jsefler-7 subscription-manager: Registered system with identity: eddfaf6d-e916-49e3-aa71-e33a2c54e1dd
		expectedSyslogMessage = String.format("%s: Registered system with identity: %s", clienttasks.command, identity);
		Assert.assertTrue(tailFromSyslogFile.contains(expectedSyslogMessage),"After registering', syslog '"+clienttasks.messagesLogFile+"' contains expected message '"+expectedSyslogMessage+"'.");
		
		logMarker = System.currentTimeMillis()+" Testing Unregister **********************";
		RemoteFileTasks.markFile(client, clienttasks.messagesLogFile, logMarker);
		clienttasks.unregister(null, null, null);	
		//	Feb  3 13:39:21 jsefler-7 subscription-manager: Unregistered machine with identity: 231c2b52-4bc8-4458-8d0a-252b1dd82877
		expectedSyslogMessage = String.format("%s: Unregistered machine with identity: %s", clienttasks.command, identity);
		Assert.assertTrue(tailFromSyslogFile.contains(expectedSyslogMessage),"After unregistering', syslog '"+clienttasks.messagesLogFile+"' contains expected message '"+expectedSyslogMessage+"'.");
	}
	
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "verify that Consumer Account And Contract Id are Present in the consumed list", 
			groups = { "VerifyConsumerAccountAndContractIdPresence"}, enabled = true)
	@ImplementsNitrateTest(caseId=68738)
	public void VerifyConsumerAccountAndContractIdPresence() throws JSONException,Exception {
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		for(ProductSubscription consumed:clienttasks.getCurrentlyConsumedProductSubscriptions()){
				Assert.assertNotNull(consumed.accountNumber);
				Assert.assertNotNull(consumed.contractNumber);

		}
	}
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "verify that system should not be compliant for an expired subscription", 
			groups = { "VerifySubscriptionOf"}, enabled = false)
	//@ImplementsNitrateTest(caseId=71208)
	public void VerifySubscriptionOfBestProductWithUnattendedRegistration() throws JSONException,Exception {
		Map<String,String> attributes = new HashMap<String,String>();
		List<String[]> listOfSectionNameValues = new ArrayList<String[]>();
		listOfSectionNameValues.add(new String[] { "rhsmcertd",
				"autoAttachInterval".toLowerCase(), "1440" });
		clienttasks.config(null, null, true, listOfSectionNameValues);
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		CandlepinTasks.deleteSubscriptionsAndRefreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, sm_clientOrg,"multi-stackable");
		CandlepinTasks.refreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, ownerKey);
		CandlepinTasks.deleteResourceUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword, sm_serverUrl,"/products/" + "multi-stackable");
		
		String consumerId = clienttasks.getCurrentConsumerId();
		ownerKey = CandlepinTasks.getOwnerKeyOfConsumerId(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, consumerId);
		Calendar cal = new GregorianCalendar();
		Date todaysDate = cal.getTime();
		cal.set(Calendar.HOUR_OF_DAY, 0);cal.set(Calendar.MINUTE, 0);cal.set(Calendar.SECOND, 0);	// avoid times in the middle of the day
		cal.add(Calendar.YEAR, 1);
		cal.add(Calendar.DATE, 10);
		Date futureDate = cal.getTime();	// one year and ten days from tomorrow
		attributes.put("sockets", "0");
		attributes.put("arch", "ALL");
		attributes.put("type", "MKT");
		attributes.put("multi-entitlement", "yes");
		attributes.put("stacking_id", "726409");
		List<String> providedProducts = new ArrayList<String>();
		providedProducts.add("100000000000002");
		CandlepinTasks.createProductUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword, sm_serverUrl,"Multi-Stackable for 100000000000002","multi-stackable", 1,attributes ,null);
		String requestBody = CandlepinTasks.createSubscriptionRequestBody(20, todaysDate, futureDate,"multi-stackable", Integer.valueOf(getRandInt()), Integer.valueOf(getRandInt()), providedProducts).toString();
		CandlepinTasks.postResourceUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, "/owners/" + ownerKey + "/subscriptions", requestBody);	
		JSONObject jobDetail = CandlepinTasks.refreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, ownerKey);
		jobDetail = CandlepinTasks.waitForJobDetailStateUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword,sm_serverUrl,jobDetail,"FINISHED", 5*1000, 1);
		clienttasks.unsubscribeFromAllOfTheCurrentlyConsumedProductSubscriptions();
		sleep(3*60*1000);
		int sockets=16;
		Map<String, String> factsMap = new HashMap<String, String>();
		factsMap.put("lscpu.cpu_socket(s)", String.valueOf(sockets));
		factsMap.put("cpu.cpu_socket(s)", String.valueOf(sockets));
		clienttasks.createFactsFileWithOverridingValues(factsMap);
		for (InstalledProduct installed : clienttasks.getCurrentlyInstalledProducts()) {
			if(!(installed.productId.equals("100000000000002"))){
				moveProductCertFiles(installed.productId+"_"+ ".pem");
				moveProductCertFiles(installed.productId + ".pem");
			}
		}
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, true, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		for (InstalledProduct installed : clienttasks.getCurrentlyInstalledProducts()) {
			if(installed.productId.equals("100000000000002"))
			Assert.assertEquals(installed.status, "Subscribed");

		}
		for(ProductSubscription consumed:clienttasks.getCurrentlyConsumedProductSubscriptions()){
			Assert.assertEquals(consumed.productName, "Multi-Stackable for 100000000000002");
		}
		
		
		
	}
	
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "verify that system should not be compliant for an expired subscription", 
			groups = { "VerifySystemCompliantFact"}, enabled = true)
	public void VerifySystemCompliantFactWhenAllProductsAreExpired_Test() throws JSONException,Exception {
		restoreProductCerts();
		List<String[]> listOfSectionNameValues = new ArrayList<String[]>();
		listOfSectionNameValues.add(new String[] { "rhsmcertd",
				"certCheckInterval".toLowerCase(), "1" });
		listOfSectionNameValues.add(new String[] { "rhsmcertd",
				"autoAttachInterval".toLowerCase(), "1440" });
		clienttasks.config(null, null, true, listOfSectionNameValues);
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		Assert.assertFalse(clienttasks.getCurrentlyInstalledProducts().isEmpty(),
				"Products are currently installed for which the compliance of ALL are covered by future available subscription pools.");
		Assert.assertEquals(clienttasks.getFactValue(factname), "invalid",
				"Before attempting to subscribe to any future subscription, the system should be non-compliant (see value for fact '"+factname+"').");
		
		clienttasks.unsubscribe(true, (BigInteger) null, null, null, null);
		File expectCertFile = new File(System.getProperty("automation.dir",
				null) + "/expiredcerts/Expiredcert.pem");
		RemoteFileTasks.putFile(client.getConnection(),
				expectCertFile.toString(), "/root/", "0755");
		clienttasks.importCertificate_("/root/Expiredcert.pem");
		for (InstalledProduct installed : clienttasks.getCurrentlyInstalledProducts()) {
			if(!(installed.status.equals("Expired"))){
				moveProductCertFiles(installed.productId+"_"+ ".pem");
				moveProductCertFiles(installed.productId + ".pem");
			}
		}
		Assert.assertFalse(clienttasks.getCurrentlyInstalledProducts().isEmpty());
		String actual=clienttasks.getFactValue(factname).trim();
		Assert.assertEquals(actual, "invalid");
	}
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "verify if able to entitle consumer to the pool virt_only,pool_derived,bonus pool ", 
			groups = {"VerifyVirtOnlyPoolsRemoved","blockedByBug-722977"}, enabled = true)		// TODO: jsefler does not understand how this test works - please explain
	public void VerifyVirtOnlyPoolsRemoved() throws JSONException,Exception {
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(List<String>)null, (String)null, null, null, true, null, null, null, null);
		String consumerId = clienttasks.getCurrentConsumerId();
		ownerKey = CandlepinTasks.getOwnerKeyOfConsumerId(sm_clientUsername, sm_clientPassword, sm_serverUrl, consumerId);
		CandlepinTasks.deleteSubscriptionsAndRefreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, sm_clientOrg,"virtualPool");
		CandlepinTasks.refreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, ownerKey);
		CandlepinTasks.deleteResourceUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword, sm_serverUrl,"/products/" + "virtualPool");

		Calendar cal = new GregorianCalendar();	// right now
		cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0);// avoid times in the middle of the day
		Date todaysDate = cal.getTime();
		cal.add(Calendar.YEAR, 1);
		cal.add(Calendar.DATE, 10);
		Date futureDate = cal.getTime();	// one year and 10 days from tomorrow
		Map<String,String> attributes = new HashMap<String,String>();
		attributes.put("virt_limit", "4");
		attributes.put("arch", "ALL");
		attributes.put("type", "MKT");
		attributes.put("multi-entitlement", "no");
		List<String> providedProducts = new ArrayList<String>();
		providedProducts.add("27060");
		CandlepinTasks.createProductUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword, sm_serverUrl,"virtual-product","virtualPool", 1,attributes ,null);
		String requestBody = CandlepinTasks.createSubscriptionRequestBody(10, todaysDate, futureDate,"virtualPool", Integer.valueOf(getRandInt()), Integer.valueOf(getRandInt()), providedProducts).toString();
		CandlepinTasks.postResourceUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, "/owners/" + ownerKey + "/subscriptions", requestBody);	

		JSONObject jobDetail = CandlepinTasks.refreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, ownerKey);
		CandlepinTasks.waitForJobDetailStateUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword,sm_serverUrl,jobDetail,"FINISHED", 5*1000, 1);
// unnecessary		sleep(3*60*1000);
		boolean flag=false;
		String poolId=null;
		for(SubscriptionPool pools:clienttasks.getCurrentlyAvailableSubscriptionPools()){
			if(pools.subscriptionName.equals("virtual-product")){
				flag=true;
				poolId=pools.poolId;
			}
		}
		Assert.assertTrue(flag, "Pool is created");
		
if (true) throw new SkipException("The remaining test logic in this test needs a re-write.");
/* TODO I can't figure out what is being tested here.
 * It always fails because the "virtual-product" subscription is available.
 * I don't know what the intend is for manipulating the JSON followed by the Candlepin PUT on the /owners/<ownerKey>/subscriptions
 * Please explain/re-write.
 */
		
		JSONObject jsonSubscriptions = new JSONObject(CandlepinTasks.getResourceUsingRESTfulAPI(sm_clientUsername,sm_clientPassword,sm_serverUrl,"/pools/"+poolId));	
		JSONArray jsonProduct = (JSONArray) jsonSubscriptions.get("productAttributes");
		JSONObject product=(JSONObject) jsonProduct.get(0);
		jsonProduct.remove(0);
		product.remove("value");
		product.accumulate("value", "0");
		jsonProduct.put(0, product);
		jsonSubscriptions.remove("productAttributes");
		jsonSubscriptions.accumulate("productAttributes", jsonProduct);
		CandlepinTasks.putResourceUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword,sm_serverUrl,"/owners/" + ownerKey + "/subscriptions" ,jsonSubscriptions);
		jobDetail = CandlepinTasks.refreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, ownerKey);
		CandlepinTasks.waitForJobDetailStateUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword,sm_serverUrl,jobDetail,"FINISHED", 5*1000, 1);

		flag=false;
		for(SubscriptionPool pools:clienttasks.getCurrentlyAvailableSubscriptionPools()){
			if(pools.subscriptionName.equals("virtual-product")){
				flag=true;
// unnecessary				poolId=pools.poolId;
			}
		}
		Assert.assertTrue(!flag, "Pool is no longer available");
	}
	@AfterGroups(groups = "setup", value = {"VerifyVirtOnlyPoolsRemoved"}, enabled = true)
	public void cleanupAfterVerifyVirtOnlyPoolsRemoved() throws Exception{
// TODO This is not completely accurate, but it is a good place to cleanup after VerifyVirtOnlyPoolsRemoved...
		CandlepinTasks.deleteResourceUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword,sm_serverUrl,"/subscriptions/"+"virtualPool");
		CandlepinTasks.deleteResourceUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword,sm_serverUrl,"/products/"+"virtual-pool");
		JSONObject jobDetail = CandlepinTasks.refreshPoolsUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword,sm_serverUrl,sm_clientOrg);
		jobDetail = CandlepinTasks.waitForJobDetailStateUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword,sm_serverUrl,jobDetail,"FINISHED", 5*1000, 1);
	}
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "verify if able to entitle consumer to the pool virt_only,pool_derived,bonus pool ", 
			groups = { "consumeVirtOnlyPool","blockedByBug-756628"}, enabled = true)
	public void consumeVirtOnlyPool() throws JSONException,Exception {
		String isPool_derived =null;
		Boolean virtonly=false;
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(List<String>)null,  (String)null, null, null, true, null, null, null, null);
		String isGuest=clienttasks.getFactValue("virt.is_guest");
		if(isGuest.equals("false")){
		for (SubscriptionPool availList : clienttasks
				.getCurrentlyAvailableSubscriptionPools()) {
			isPool_derived = CandlepinTasks.getPoolProductAttributeValue(sm_clientUsername,	sm_clientPassword, sm_serverUrl, availList.poolId,"pool_derived");		
			 virtonly= CandlepinTasks.isPoolVirtOnly(sm_clientUsername, sm_clientPassword, availList.poolId, sm_serverUrl);
			 if(!(isPool_derived==null) || virtonly){
				String result= clienttasks.subscribe_(null, null, availList.poolId, null, null, null, null,null,null, null,null).getStdout();
				String Expected="Pool is restricted to virtual guests: '"+availList.poolId+"'.";
				 Assert.assertEquals(result.trim(), Expected);
			 }
		}
		}
		else throw new SkipException("Cannot test on a virtual machine");
	
	}
			



	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "verify if system.entitlements_valid goes from valid to partial after oversubscribing", // TODO fix this description
			groups = {"VerifyRHELWorkstationSubscription","blockedByBug-739790"}, enabled = true)
	public void VerifyRHELWorkstationSubscription() throws JSONException,Exception {
		InstalledProduct workstation = InstalledProduct.findFirstInstanceWithMatchingFieldFromList("productId", "71", clienttasks.getCurrentlyInstalledProducts());
		if (workstation==null) throw new SkipException("This test is only applicable on a RHEL Workstation where product 71 is installed.");
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(List<String>)null, (String)null, null, null, true, false, null, null, null);
/* too time consuming; replace with subscribeToTheCurrentlyAvailableSubscriptionPoolsCollectively();	
		for (SubscriptionPool availList : clienttasks
				.getCurrentlyAvailableSubscriptionPools()) {
				clienttasks.subscribe_(null, null, availList.poolId, null, null, null, null, null, null, null, null);
		}
*/
		clienttasks.subscribeToTheCurrentlyAvailableSubscriptionPoolsCollectively();
		boolean assertedWorkstationProduct = false;
		for (InstalledProduct installed : clienttasks
				.getCurrentlyInstalledProducts()) {
			if(installed.productId.contains("Workstation")){
				Assert.assertEquals(installed.status, "subscribed");
				assertedWorkstationProduct = true;
			}
		}
		if (!assertedWorkstationProduct) throw new SkipException("Installed product to be tested is not available");
	}
	
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "verify if system.entitlements_valid goes from valid to partial after oversubscribing", 
			groups = { "ValidityAfterOversubscribing","blockedByBug-845126"}, enabled = false)
	public void systemEntitlementsValidityAfterOversubscribing() throws JSONException,Exception {
		Boolean noMultiEntitlements=true;
		int sockets=2;
		Map<String, String> factsMap = new HashMap<String, String>();
		factsMap.put("lscpu.cpu_socket(s)", String.valueOf(sockets));
		factsMap.put("cpu.cpu_socket(s)", String.valueOf(sockets));
		clienttasks.createFactsFileWithOverridingValues(factsMap);
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, true, null, null,
				(List<String>)null, (String)null, null, null, true, null, null, null, null);
		for (InstalledProduct installed : clienttasks.getCurrentlyInstalledProducts()) {
			if (installed.status.equals("Not Subscribed") || installed.status.equals("Partially Subscribed"))
				moveProductCertFiles(installed.productId + "*");
		}		
	
		clienttasks.unsubscribeFromAllOfTheCurrentlyConsumedProductSubscriptions();
		String consumerId = clienttasks.getCurrentConsumerId();
		ownerKey = CandlepinTasks.getOwnerKeyOfConsumerId(sm_clientUsername, sm_clientPassword, sm_serverUrl, consumerId);
		for (SubscriptionPool availList : clienttasks
				.getCurrentlyAvailableSubscriptionPools()) {
			boolean isMultiEntitled=CandlepinTasks.isSubscriptionMultiEntitlement(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, ownerKey , availList.poolId);
			
			if(isMultiEntitled){
				noMultiEntitlements=false;
				clienttasks.subscribe(null, null, availList.poolId, null, null, "4", null, null, null, null, null);
			}
		}
		for (InstalledProduct installed : clienttasks.getCurrentlyInstalledProducts()) {
			if (installed.status.equals("Not Subscribed")
					|| installed.status.equals("Partially Subscribed"))
				moveProductCertFiles(installed.productId + "*");

				clienttasks.refresh(null, null, null);
		}
		
		String actual=clienttasks.getFactValue(factname).trim();
		if(!noMultiEntitlements) throw new SkipException("No mutli-entitled subscriptions available for testing");
		Assert.assertEquals(actual, "valid");
	}
	
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "verify OwnerInfo is displayed only for pools that are active right now, for all the stats", // TODO, correct this description
			groups = { "certificateStacking","blockedByBug-726409"}, enabled = true)
	public void certificateStacking() throws JSONException,Exception {
		Map<String,String> attributes = new HashMap<String,String>();
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(List<String>)null, (String) null, null, null, true, null, null, null,null);
/* too time consuming; replacing with configureTmpProductCertDirWithInstalledProductCerts...
		moveProductCertFiles( "*.pem");
		client.runCommandAndWait("cp /root/temp1/100000000000002.pem "+clienttasks.productCertDir);
*/
		ProductCert installedProductCert100000000000002 = ProductCert.findFirstInstanceWithMatchingFieldFromList("productId", "100000000000002", clienttasks.getCurrentProductCerts());
		Assert.assertNotNull(installedProductCert100000000000002, "Found installed product cert 100000000000002 needed for this test.");
		configureTmpProductCertDirWithInstalledProductCerts(Arrays.asList(new ProductCert[]{installedProductCert100000000000002}));
		
		CandlepinTasks.deleteSubscriptionsAndRefreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, sm_clientOrg,"multi-stackable");
		CandlepinTasks.refreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, ownerKey);
		CandlepinTasks.deleteResourceUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword, sm_serverUrl,"/products/" + "multi-stackable");
		CandlepinTasks.deleteSubscriptionsAndRefreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, sm_clientOrg,"stackable");
		CandlepinTasks.refreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, ownerKey);
		CandlepinTasks.deleteResourceUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword, sm_serverUrl,"/products/" + "stackable");
		int sockets = 14;
		String poolid = null;
		String validity = null;
		List<String[]> listOfSectionNameValues = new ArrayList<String[]>();
		listOfSectionNameValues.add(new String[] { "rhsmcertd",
				"autoAttachInterval".toLowerCase(), "1440" });
		clienttasks.config(null, null, true, listOfSectionNameValues);
		Map<String, String> factsMap = new HashMap<String, String>();
		factsMap.put("virt.is_guest", String.valueOf(Boolean.FALSE));	// sockets based compliance now applies only to Physical systems; vcpu based compliance applies to Virtual systems
		factsMap.put("cpu.cpu_socket(s)", String.valueOf(sockets));
		clienttasks.createFactsFileWithOverridingValues(factsMap);
		clienttasks.facts(null, true, null, null, null);
		String consumerId = clienttasks.getCurrentConsumerId();
		ownerKey = CandlepinTasks.getOwnerKeyOfConsumerId(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, consumerId);
/* unnecessary
		Calendar now = new GregorianCalendar();
		now.add(Calendar.YEAR, 1);
		now.add(Calendar.DATE, 1);
*/
		attributes.put("sockets", "2");
		attributes.put("arch", "ALL");
		attributes.put("type", "MKT");
		attributes.put("multi-entitlement", "yes");
		attributes.put("stacking_id", "726409");
		List<String> providedProducts = new ArrayList<String>();
		providedProducts.add("100000000000002");
		CandlepinTasks.createProductUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, "Multi-Stackable for 100000000000002", "multi-stackable", 1, attributes, null);
		CandlepinTasks.createSubscriptionAndRefreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, sm_clientOrg, 20, -1*24*60/*1 day ago*/, 15*24*60/*15 days from now*/, getRandInt(), getRandInt(), "multi-stackable", providedProducts);
		JSONObject jobDetail = CandlepinTasks.refreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, ownerKey);
		jobDetail = CandlepinTasks.waitForJobDetailStateUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword,sm_serverUrl,jobDetail,"FINISHED", 5*1000, 1);
		attributes.put("sockets", "4");
		attributes.put("multi-entitlement", "no");
		CandlepinTasks.createProductUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, "Stackable for 100000000000002", "stackable", 1, attributes, null);
		CandlepinTasks.createSubscriptionAndRefreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, sm_clientOrg, 20, -1*24*60/*1 day ago*/, 15*24*60/*15 days from now*/, getRandInt(), getRandInt(), "stackable", providedProducts);
		jobDetail = CandlepinTasks.refreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, ownerKey);
		jobDetail = CandlepinTasks.waitForJobDetailStateUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword,sm_serverUrl,jobDetail,"FINISHED", 5*1000, 1);
		
		clienttasks.unsubscribeFromAllOfTheCurrentlyConsumedProductSubscriptions();
		for (SubscriptionPool availList : clienttasks.getCurrentlyAllAvailableSubscriptionPools()) {
			if(availList.subscriptionName.equals("Multi-Stackable for 100000000000002")){
				poolid=availList.poolId;
				clienttasks.subscribe_(null, null, availList.poolId, null, null, "3", null, null, null, null, null);	
				 validity=clienttasks.getFactValue(factname);
				Assert.assertEquals(validity.trim(), "partial");
			}else if( availList.subscriptionName.equals("Stackable for 100000000000002") ){
				clienttasks.subscribe_(null, null, availList.poolId, null, null,null, null, null, null, null, null);	
				 validity=clienttasks.getFactValue(factname);
				Assert.assertEquals(validity.trim(), "partial");

			}
		}
		clienttasks.subscribe_(null, null, poolid, null, null, "2", null, null, null, null, null);	
		clienttasks.getCurrentlyConsumedProductSubscriptions();
		validity=clienttasks.getFactValue(factname);
		Assert.assertEquals(validity.trim(), "valid");
	}
	
	
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "verify OwnerInfo is displayed only for pools that are active right now, for all the stats", 
			groups = {"OwnerInfoForActivePools","blockedByBug-710141",}, enabled = true)
	public void OwnerInfoForActivePools() throws JSONException,Exception {
		DateFormat yyyy_MM_dd_DateFormat = new SimpleDateFormat("yyyy-MM-dd");
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(List<String>)null,  (String)null, null, null, true, null, null, null, null);
		Calendar now = new GregorianCalendar();
		Calendar futureCalendar = new GregorianCalendar();
		futureCalendar.set(Calendar.HOUR_OF_DAY, 0); futureCalendar.set(Calendar.MINUTE, 0); futureCalendar.set(Calendar.SECOND, 0);	// avoid times in the middle of the day
		futureCalendar.add(Calendar.YEAR, 1);
//unnecessary		futureCalendar.add(Calendar.DATE, 1);
		
		String futurceDate = yyyy_MM_dd_DateFormat.format(futureCalendar.getTime());
		List<SubscriptionPool> availOnDate = getAvailableFutureSubscriptionsOndate(futurceDate);
		if(availOnDate.size()==0) throw new SkipException(
				"Sufficient future pools are not available");
		/* attaching entitlements has nothing to do with the assertion of pool availability dates that follow; commenting this block out
		for (SubscriptionPool subscriptions : availOnDate) {
			clienttasks.subscribe_(null, null, subscriptions.poolId, null, null,
					null, null, null, null, null, null);
		}
		*/
		/* parsing JSON should be done using JSONArrays and JSONObjects.  Then we should assert that the start/end dates of the available pools straddle the test date; commenting this block out and re-implementing
		String jsonActivationKey = CandlepinTasks.getResourceUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword, sm_serverUrl, "/owners/"+sm_clientOrg+"/pools");
		for (String Cert: jsonActivationKey.split(",",0)){
			if(Cert.contains("startDate")){
				String[] items = Cert.split("\":\"");
				items=items[1].split("-");
				Assert.assertEquals(items[0], onDate.split("-")[0]);
	 		}
	      }
		jsonActivationKey = CandlepinTasks.getResourceUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword, sm_serverUrl, "/owners/"+sm_clientOrg+"/pools?activeon="+onDateToTest);
		*/
		JSONArray jsonPools = new JSONArray(CandlepinTasks.getResourceUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword, sm_serverUrl, "/owners/"+sm_clientOrg+"/pools"));
		Assert.assertTrue (jsonPools.length()>0,"Successfully got a positive number of /owners/"+sm_clientOrg+"/pools");
		for (int i = 0; i < jsonPools.length(); i++) {
			JSONObject jsonPool = (JSONObject) jsonPools.get(i);
			String subscriptionName = jsonPool.getString("productName");
			String startDate = jsonPool.getString("startDate");
			String endDate = jsonPool.getString("endDate");
			Calendar startCalendar = parseISO8601DateString(startDate,"GMT");	// "startDate":"2014-01-06T00:00:00.000+0000"
			Calendar endCalendar = parseISO8601DateString(endDate,"GMT");	// "endDate":"2015-01-06T00:00:00.000+0000"
			Assert.assertTrue(startCalendar.before(now),"Available pool '"+subscriptionName+"' startsDate='"+startDate+"' starts before now.");
			Assert.assertTrue(endCalendar.after(now),"Available pool '"+subscriptionName+"' endDate='"+endDate+"' ends after now.");
		}
		jsonPools = new JSONArray(CandlepinTasks.getResourceUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword, sm_serverUrl, "/owners/"+sm_clientOrg+"/pools?activeon="+futurceDate));
		Assert.assertTrue (jsonPools.length()>0,"Successfully got a positive number of /owners/"+sm_clientOrg+"/pools?activeon="+futurceDate);
		for (int i = 0; i < jsonPools.length(); i++) {
			JSONObject jsonPool = (JSONObject) jsonPools.get(i);
			String subscriptionName = jsonPool.getString("productName");
			String startDate = jsonPool.getString("startDate");
			String endDate = jsonPool.getString("endDate");
			Calendar startCalendar = parseISO8601DateString(startDate,"GMT");	// "startDate":"2014-01-06T00:00:00.000+0000"
			Calendar endCalendar = parseISO8601DateString(endDate,"GMT");	// "endDate":"2015-01-06T00:00:00.000+0000"
			Assert.assertTrue(startCalendar.before(futureCalendar),"Future available pool '"+subscriptionName+"' startsDate='"+startDate+"' starts before "+futurceDate+".");
			Assert.assertTrue(endCalendar.equals(futureCalendar)||endCalendar.after(futureCalendar),"Future available pool '"+subscriptionName+"' endDate='"+endDate+"' ends on or after "+futurceDate+".");
		}
	}
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "verify if refresh Pools w/ Auto-Create Owner Fails", 
			groups = { "EnableAndDisableCertV3","blockedByBug-919700"}, enabled = false)
	public void EnableAndDisableCertV3() throws JSONException,Exception {
		String version=null;
		servertasks.updateConfigFileParameter("candlepin.enable_cert_v3", "false");
		servertasks.restartTomcat();
		SubscriptionManagerCLITestScript.sleep( 1*60 * 1000);
		clienttasks.restart_rhsmcertd(null, null, false, null);
		clienttasks.register(sm_clientUsername, sm_clientPassword,
			sm_clientOrg, null, null, null, null, true, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		for(EntitlementCert Cert: clienttasks.getCurrentEntitlementCerts()){
			version=Cert.version;
			if(version.equals("1.0")){
			Assert.assertEquals(version, "1.0");
		}else{
			servertasks.updateConfigFileParameter("candlepin.enable_cert_v3", "true");
			servertasks.restartTomcat();
			Assert.fail();
		}
			
		}
		clienttasks.unsubscribeFromAllOfTheCurrentlyConsumedProductSubscriptions();
		servertasks.updateConfigFileParameter("candlepin.enable_cert_v3", "true");
		servertasks.restartTomcat();
		clienttasks.restart_rhsmcertd(null, null, false, null);
		SubscriptionManagerCLITestScript.sleep( 1*60*1000);
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, true, null, null,
					(String) null, null, null, null, true, null, null, null, null);
		for(EntitlementCert Cert: clienttasks.getCurrentEntitlementCerts()){
			version=Cert.version;
		Assert.assertEquals(version, "3.2");
	}
	}
	
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "verify if refresh Pools w/ Auto-Create Owner Fails", 
			groups = { "RefreshPoolsWithAutoCreate","blockedByBug-720487"}, enabled = true)
	public void RefreshPoolsWithAutoCreate() throws JSONException,Exception {
		String org="newowner";
		JSONObject jsonActivationKey = new JSONObject(
				CandlepinTasks.getResourceUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword, sm_serverUrl, "/owners/"+org));
		Assert.assertEquals(jsonActivationKey.getString("displayMessage"), "Organization with id newowner could not be found.");
		new JSONObject(CandlepinTasks.putResourceUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword, sm_serverUrl,"/owners/"+org+"/subscriptions?auto_create_owner=true" ));
		jsonActivationKey = new JSONObject(CandlepinTasks.getResourceUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword, sm_serverUrl, "/owners/"+org));
		Assert.assertNotNull(jsonActivationKey.get("created")); 
		jsonActivationKey= new JSONObject(CandlepinTasks.putResourceUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword, sm_serverUrl,"/owners/AnotherOwner/subscriptions?auto_create_owner=false" ));
		Assert.assertEquals(jsonActivationKey.getString("displayMessage"),"owner with key: AnotherOwner was not found.");
		CandlepinTasks.deleteResourceUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword, sm_serverUrl, "/owners/"+org);
	}
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "verify tracebacks occur running yum repolist after subscribing to a pool", 
			groups = { "VerifyipV4Facts","blockedByBug-874147"}, enabled = true)
	public void VerifyipV4Facts() throws JSONException,Exception {
		Boolean pattern=false;
		Boolean Flag=false;
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		String result=clienttasks.facts(true, null, null, null, null).getStdout();
		Pattern p = Pattern.compile(result);
		Matcher matcher = p.matcher("Unknown");
		while (matcher.find()) {
			 pattern = matcher.find();
	}
		Assert.assertEquals(pattern, Flag);
	}
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "verify tracebacks occur running yum repolist after subscribing to a pool", 
			groups = { "VerifyRepoFileExistance","blockedByBug-886604","blockedByBug-919700"}, enabled = true)
	public void VerifyRepoFileExistance() throws JSONException,Exception {
		List<String[]> listOfSectionNameValues = new ArrayList<String[]>();
		listOfSectionNameValues.add(new String[] { "rhsm","manage_repos", "1" });
		clienttasks.config(null, null, true, listOfSectionNameValues);
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, true, null, null,
				(String) null, null, null, null, true, null, null, null, null);
			List<YumRepo> originalRepos =clienttasks.getCurrentlySubscribedYumRepos();
			Assert.assertFalse(originalRepos.isEmpty());
			listOfSectionNameValues = new ArrayList<String[]>();
			listOfSectionNameValues.add(new String[] { "rhsm","manage_repos", "0" });
			clienttasks.config(null, null, true, listOfSectionNameValues);
			clienttasks.getYumRepolist("all");	// needed to trigger subscription-manager yum plugin
			originalRepos =clienttasks.getCurrentlySubscribedYumRepos();
			Assert.assertTrue(originalRepos.isEmpty());
			 
	}
	
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "verify tracebacks occur running yum repolist after subscribing to a pool", 
			groups = { "AddingVirtualPoolToActivationKey","blockedByBug-755677"}, enabled = true)
	public void AddingVirtualPoolToActivationKey() throws JSONException,Exception {
		Integer addQuantity=1;
/* unnecessary
		int count =0;
		List<String[]> listOfSectionNameValues = new ArrayList<String[]>();
		listOfSectionNameValues.add(new String[] { "rhsmcertd",
				"autoAttachInterval".toLowerCase(), "1440" });
		clienttasks.config(null, null, true, listOfSectionNameValues);
		clienttasks.restart_rhsmcertd(null, configuredHealFrequency, false,null);
*/
/* here's a faster way to do this...
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		String consumerId = clienttasks.getCurrentConsumerId();
		JSONObject jsonConsumer = CandlepinTasks.setAutohealForConsumer(
				sm_clientUsername, sm_clientPassword, sm_serverUrl, consumerId,
				false);
	clienttasks.unsubscribeFromAllOfTheCurrentlyConsumedProductSubscriptions();
	clienttasks.getCurrentlyAllAvailableSubscriptionPools();
*/
		String consumerId = clienttasks.getCurrentConsumerId(clienttasks.register(sm_clientUsername, sm_clientPassword,sm_clientOrg, null, null, null, null, null, null, null,(String) null, null, null, null, true, false, null, null, null));
		
	ownerKey = CandlepinTasks.getOwnerKeyOfConsumerId(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, consumerId);
	String Productname,productId;
	List<String> providedProductIds = new ArrayList<String>();
	Productname = "virt-only-product to be added to activation key";
	productId = "virt-only-test-product";
	String poolId=null;
	Map<String,String> attributes = new HashMap<String,String>();
	attributes.put("version", "1.0");
	attributes.put("variant", "server");
	attributes.put("arch", "ALL");
	attributes.put("warning_period", "30");
	attributes.put("type", "MKT");
	attributes.put("type", "SVC");
	attributes.put("virt_limit", "unlimited");
	CandlepinTasks.deleteSubscriptionsAndRefreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, sm_clientOrg, productId);
	CandlepinTasks.deleteResourceUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, "/products/"+productId);
	
	CandlepinTasks.createProductUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, Productname, productId, 1, attributes, null);
	CandlepinTasks.createSubscriptionAndRefreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, ownerKey, 20, -1*24*60/*1 day ago*/, 15*24*60/*15 days from now*/, getRandInt(), getRandInt(), productId, providedProductIds);
		String name = String.format("%s_%s-ActivationKey%s", sm_clientUsername,
				sm_clientOrg, System.currentTimeMillis());
		Map<String, String> mapActivationKeyRequest = new HashMap<String, String>();
		mapActivationKeyRequest.put("name", name);
		JSONObject jsonActivationKeyRequest = new JSONObject(
				mapActivationKeyRequest);
		JSONObject jsonActivationKey = new JSONObject(
				CandlepinTasks.postResourceUsingRESTfulAPI(sm_clientUsername,
						sm_clientPassword, sm_serverUrl, "/owners/"
								+ sm_clientOrg + "/activation_keys",
								jsonActivationKeyRequest.toString()));
		
		clienttasks.unsubscribe(true, (BigInteger) null, null, null, null);
		for (SubscriptionPool availList : clienttasks.getCurrentlyAllAvailableSubscriptionPools()) {
					if(availList.subscriptionName.equals(Productname)){
						poolId=availList.poolId;
			
		}
		}
		new JSONObject(CandlepinTasks.postResourceUsingRESTfulAPI(sm_clientUsername, sm_clientPassword, sm_serverUrl, "/activation_keys/" + jsonActivationKey.getString("id") + "/pools/" +poolId+(addQuantity==null?"":"?quantity="+addQuantity), null));
		
		clienttasks.register(null, null, sm_clientOrg, null, null, null, null, null, null, null, name, null, null, null, true, false, null, null, null);
/* this loop fails to assert anything if the currentlyConsumedProductSubscriptions are empty; following is a better assertion...
		for(ProductSubscription consumed:clienttasks.getCurrentlyConsumedProductSubscriptions()){
		Assert.assertEquals(consumed.poolId, poolId);
		}
*/
		List <ProductSubscription> consumedProductSubscriptions = clienttasks.getCurrentlyConsumedProductSubscriptions();
		Assert.assertTrue(consumedProductSubscriptions.size()==1 && consumedProductSubscriptions.get(0).poolId.equals(poolId),"Registering with an activationKey named '"+name+"' should grant a single entitlement from subscription pool id '"+poolId+"'.");
		
		new JSONObject(CandlepinTasks.deleteResourceUsingRESTfulAPI(sm_clientUsername,
				sm_clientPassword, sm_serverUrl,"/activation_keys/"+name));
	}
	
	
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "verify tracebacks occur running yum repolist after subscribing to a pool", 
			groups = { "YumReposListAfterSubscription","blockedByBug-696786","blockedByBug-919700" }, enabled = true)
	public void YumReposListAfterSubscription() throws JSONException,Exception {
		Boolean pattern=false;
		Boolean Flag=false;
		String yum_cmd="yum repolist enabled --disableplugin=rhnplugin";
		String result=client.runCommandAndWait(yum_cmd).getStdout();
		clienttasks.register(sm_clientUsername, sm_clientPassword,sm_clientOrg, null, null, null, null, true, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		result=client.runCommandAndWait(yum_cmd).getStdout();
		Pattern p = Pattern.compile(result);
		Matcher matcher = p.matcher("Traceback (most recent call last):");
		while (matcher.find()) {
			 pattern = matcher.find();

		}
		Assert.assertEquals(Flag, pattern);
	}
	
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@ImplementsNitrateTest(caseId=50235)
	@Test(description = "verify rhsm log for Update With No Installed Products", 
			groups = {"UpdateWithNoInstalledProducts","blockedByBug-746241" }, enabled = true)
	public void UpdateWithNoInstalledProducts() throws JSONException,Exception {
		Boolean actual = false;
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, false, null, null, null);
/* unnecessary for this test
		clienttasks.unsubscribeFromAllOfTheCurrentlyConsumedProductSubscriptions();
		clienttasks.restart_rhsmcertd(null, null, false, null);
*/
		moveProductCertFiles("*.pem");
		String LogMarker = System.currentTimeMillis()+" Testing ***************************************************************";
		RemoteFileTasks.markFile(client, clienttasks.rhsmLogFile, LogMarker);
		String InstalledProducts=clienttasks.listInstalledProducts().getStdout();
		Assert.assertEquals(InstalledProducts.trim(), "No installed products to list");
/* unnecessary to restart rhsmcertd and sleep for a long time, call run_rhsmcertd_worker instead
		clienttasks.restart_rhsmcertd(null, null, false, null);
		SubscriptionManagerCLITestScript.sleep(2 * 60 * 1000);
*/
		clienttasks.run_rhsmcertd_worker(null);
/* not sure about RegexInRhsmLog(...) logic, replacing with doesStringContainMatches(...)
		Boolean	flag = RegexInRhsmLog("Error while updating certificates",RemoteFileTasks.getTailFromMarkedFile(client, clienttasks.rhsmLogFile, LogMarker, null));
		Assert.assertEquals(flag, actual);
		actual=true;
		flag = RegexInRhsmLog("Installed product IDs: \\[\\]",RemoteFileTasks.getTailFromMarkedFile(client, clienttasks.rhsmLogFile, LogMarker, null));
		Assert.assertEquals(flag, actual);
*/
		String tailFromMarkedFile = RemoteFileTasks.getTailFromMarkedFile(client, clienttasks.rhsmLogFile, LogMarker, null);
		Assert.assertFalse(doesStringContainMatches(tailFromMarkedFile, "Error"),"'Error' messages in rhsm.log");	// "Error while updating certificates" should NOT be in the rhsm.log
		Assert.assertTrue(doesStringContainMatches(tailFromMarkedFile, "Installed product IDs: \\[\\]"), "'Installed product IDs:' list is empty in rhsm.log");
		Assert.assertTrue(doesStringContainMatches(tailFromMarkedFile, "certs updated:"),"'certs updated:' in rhsm.log");
	}
		
	
	
	
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "verify Facts Update For Deleted Consumer", 
			groups = { "FactsUpdateForDeletedConsumer","blockedByBug-798788" }, enabled = true)
	@ImplementsNitrateTest(caseId = 148216)
	public void FactsUpdateForDeletedConsumer() throws JSONException,Exception {
		
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		clienttasks.autoheal(null, null, true, null, null, null);
		String consumerId = clienttasks.getCurrentConsumerId();
		CandlepinTasks.deleteResourceUsingRESTfulAPI(sm_serverAdminUsername,
				sm_serverAdminPassword, sm_serverUrl, "/consumers/"
						+ consumerId);
		String result=clienttasks.facts_(null, true, null, null, null).getStderr();
		String ExpectedMsg="Consumer "+consumerId+" has been deleted";
		if (!clienttasks.workaroundForBug876764(sm_serverType))
		ExpectedMsg = "Unit "+consumerId+" has been deleted";
		Assert.assertEquals(result.trim(), ExpectedMsg);
	}
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "verify if you can register using consumer id of a deleted owner", 
			groups = { "RegisterWithConsumeridOfDeletedOwner" }, enabled = true)
	@ImplementsNitrateTest(caseId = 148216)
	public void RegisterWithConsumeridOfDeletedOwner() throws JSONException,Exception {
		String orgname="testOwner1";
		servertasks.createOwnerUsingCPC(orgname);
		clienttasks.register_(sm_serverAdminUsername, sm_serverAdminPassword,
				orgname, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		String consumerId=clienttasks.getCurrentConsumerId();
		CandlepinTasks.deleteResourceUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword, sm_serverUrl,"/owners/" + orgname);
		clienttasks.clean_(null, null, null);
		SSHCommandResult result=clienttasks.register_(sm_serverAdminUsername, sm_serverAdminPassword, orgname, null, null, null, consumerId, null, null, null,(String)null, null, null, null, null, null, null, null, null);
		String expected="Consumer "+consumerId+" has been deleted";
		if (!clienttasks.workaroundForBug876764(sm_serverType)) expected = "Unit "+consumerId+" has been deleted";
		Assert.assertEquals(result.getStderr().trim(), expected);
	}

	

	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "verify if register to a deleted owner", 
			groups = { "RegisterToDeletedOwner" }, enabled = true)
	@ImplementsNitrateTest(caseId = 148216)
	public void RegisterToDeletedOwner() throws JSONException,Exception {
		String orgname="testOwner1";
		servertasks.createOwnerUsingCPC(orgname);
		CandlepinTasks.deleteResourceUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword, sm_serverUrl,"/owners/" + orgname);
		SSHCommandResult result=clienttasks.register_(sm_serverAdminUsername, sm_serverAdminPassword,orgname, null, null, null, null, null, null, null,(String) null, null, null, null, true, null, null, null, null);
		String expected="Organization "+orgname+" does not exist.";
		Assert.assertEquals(result.getStderr().trim(), expected);
	}


	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "verify if Repos List is empty for FutureSubscription", 
			groups = { "EmptyReposListForFutureSubscription","blockedByBug-958775" }, enabled = true)
	public void EmptyReposListForFutureSubscription() throws JSONException,
	Exception {
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
/* unnecessary for this test
		List<String[]> listOfSectionNameValues = new ArrayList<String[]>();
		listOfSectionNameValues.add(new String[] { "rhsmcertd",
				"autoAttachInterval".toLowerCase(), "1440" });
		clienttasks.config(null, null, true, listOfSectionNameValues);
*/
		DateFormat yyyy_MM_dd_DateFormat = new SimpleDateFormat("yyyy-MM-dd");
		Calendar now = new GregorianCalendar();
		Calendar nextYear = new GregorianCalendar();
		nextYear.add(Calendar.YEAR, 1);	nextYear.add(Calendar.DATE, -1);	// one day less than one year
		String onDateToTest = yyyy_MM_dd_DateFormat.format(nextYear.getTime());
/* unnecessary for this test
		clienttasks.unsubscribe(true, (BigInteger) null, null, null, null);
*/
/* we can improve the efficiency of this loop
		List<SubscriptionPool> availOnDate = getAvailableFutureSubscriptionsOndate(onDateToTest);
		if(availOnDate.size()==0) throw new SkipException(
				"Sufficient future pools are not available");
		for (SubscriptionPool subscriptions : availOnDate) {
			if(!(subscriptions.endDate.before(nextYear))){
				clienttasks.subscribe(null, null, subscriptions.poolId, null, null,null, null, null, null, null, null);
		}
		}
*/
		List<String> subscriptionPoolIds = new ArrayList<String>();
		for (SubscriptionPool subscription : clienttasks.getAvailableFutureSubscriptionsOndate(onDateToTest)) {
			if (!CandlepinTasks.isPoolAModifier(sm_clientUsername, sm_clientPassword, subscription.poolId, sm_serverUrl)) {
				subscriptionPoolIds.add(subscription.poolId);
			}
		}
		clienttasks.subscribe(null, null, subscriptionPoolIds, null, null, null, null, null, null, null, null);
		
		// determine if both active and inactive entitlements are being consumed
		boolean activeProductSubscriptionsConsumed = false;
		boolean inactiveProductSubscriptionsConsumed = false;
		List<ProductSubscription> currentlyConsumedProductSubscriptions = clienttasks.getCurrentlyConsumedProductSubscriptions();
		for (ProductSubscription subscriptions : currentlyConsumedProductSubscriptions) {
			if(subscriptions.isActive) activeProductSubscriptionsConsumed = true;
			if(!subscriptions.isActive) inactiveProductSubscriptionsConsumed = true;
		}
		if (!activeProductSubscriptionsConsumed || !inactiveProductSubscriptionsConsumed) {
			throw new SkipException("This test assumes that both current and future subscriptions are available on '"+onDateToTest+"' which is determined by the subscriptions loaded on the candlepin server."); 
		}
		// the following loop will remove all currently active entitlements
		Assert.assertTrue(!clienttasks.getCurrentlySubscribedRepos().isEmpty(), "There should be entitled repos since we are consuming current entitlements (indicated by Active:True) are attached.");
		for (ProductSubscription subscriptions : currentlyConsumedProductSubscriptions) {
			if(subscriptions.isActive) {
				clienttasks.unsubscribe(null, subscriptions.serialNumber, null, null, null);
			}
		}
		Assert.assertTrue(!clienttasks.getCurrentlyConsumedProductSubscriptions().isEmpty(), "We should still be consuming future entitlements (indicated by Active:False).");
		Assert.assertTrue(clienttasks.getCurrentlySubscribedRepos().isEmpty(), "There should not be any entitled repos despite the future attached entitlements (indicated by Active:False).");
	}

	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "verify Display hierarchy of owners", groups = { "VerifyHierarchyOfOwners" }, enabled = true)
	@ImplementsNitrateTest(caseId = 68737)
	public void VerifyHierarchyOfOwners() throws JSONException, Exception {
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		JSONObject jsonActivationKey = new JSONObject(
				CandlepinTasks.getResourceUsingRESTfulAPI(sm_clientUsername,
						sm_clientPassword, sm_serverUrl, "/owners/"));

		System.out.println(jsonActivationKey);
//TODO
throw new SkipException("Finish implementing this test.  Nothing beyond register is being asserted.");
	}

	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "verify if auto-subscribe and activation-key are mutually exclusive", groups = {
			"VerifyAutoSubscribeAndActivationkeyTogether",
	"blockedByBug-869729" }, enabled = true)
	public void VerifyAutoSubscribeAndActivationkeyTogether()
			throws JSONException, Exception {
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		String name = String.format("%s_%s-ActivationKey%s", sm_clientUsername,
				sm_clientOrg, System.currentTimeMillis());
		Map<String, String> mapActivationKeyRequest = new HashMap<String, String>();
		mapActivationKeyRequest.put("name", name);
		JSONObject jsonActivationKeyRequest = new JSONObject(
				mapActivationKeyRequest);
		JSONObject jsonActivationKey = new JSONObject(
				CandlepinTasks.postResourceUsingRESTfulAPI(sm_clientUsername,
						sm_clientPassword, sm_serverUrl, "/owners/"
								+ sm_clientOrg + "/activation_keys",
								jsonActivationKeyRequest.toString()));
		SSHCommandResult result = clienttasks.register_(null, null,
				sm_clientOrg, null, null, null, null, true, null, null,
				jsonActivationKey.get("name").toString(), null, null, null,
				true, null, null, null, null);
		String expected_msg = "Error: Activation keys cannot be used with --auto-attach.";
		Assert.assertEquals(result.getStdout().trim(), expected_msg);
	}

	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	/*
	 * @Test( description=
	 * "verify if entitlement certs are regenerated if certs are manually removed"
	 * , groups={"VerifyDuplicateContentsInReposList"}, enabled=true)
	 * 
	 * @ImplementsNitrateTest(caseId=50229) public void
	 * VerifyDuplicateContentsInReposList() throws JSONException, Exception {
	 * clienttasks
	 * .register(sm_clientUsername,sm_clientPassword,sm_clientOrg,null
	 * ,null,null,null,null,null,null,(String)null,null, null, true,null,null,
	 * null, null); List<String[]> listOfSectionNameValues = new
	 * ArrayList<String[]>(); listOfSectionNameValues.add(new
	 * String[]{"rhsmcertd","healFrequency".toLowerCase(), "1440"});
	 * clienttasks.config(null,null,true,listOfSectionNameValues);
	 * clienttasks.unsubscribe(true, (BigInteger)null, null, null, null);
	 * for(SubscriptionPool pool
	 * :clienttasks.getCurrentlyAllAvailableSubscriptionPools()){ List<String>
	 * providedProducts =
	 * CandlepinTasks.getPoolProvidedProductIds(sm_clientUsername,
	 * sm_clientPassword, sm_serverUrl, pool.poolId);
	 * System.out.println(providedProducts + "  providedProducts"); } }
	 */

	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "verify if entitlement certs are regenerated if certs are manually removed", groups = { "VerifyRegenrateEntitlementCert" }, enabled = true)
	@ImplementsNitrateTest(caseId = 64181)
	public void VerifyRegenrateEntitlementCert() throws JSONException,
	Exception {
		String poolId = null;
		int Certfrequeny = 1;
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		List<String[]> listOfSectionNameValues = new ArrayList<String[]>();
		listOfSectionNameValues.add(new String[] { "rhsmcertd",
				"autoAttachInterval".toLowerCase(), "1440" });
		clienttasks.config(null, null, true, listOfSectionNameValues);
		clienttasks.unsubscribe(true, (BigInteger) null, null, null, null);
		for (SubscriptionPool availList : clienttasks
				.getCurrentlyAvailableSubscriptionPools()) {
			poolId = availList.poolId;

		}
		clienttasks.subscribe_(null, null, poolId, null, null, null, null,
				null, null, null, null);
		client.runCommandAndWait("rm -rf " + clienttasks.entitlementCertDir
				+ "/*.pem");
		clienttasks.restart_rhsmcertd(Certfrequeny, null, false, null);
		SubscriptionManagerCLITestScript.sleep(Certfrequeny * 60 * 1000);
		List<File> Cert = clienttasks.getCurrentEntitlementCertFiles();
		Assert.assertNotNull(Cert.size());
	}

	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "verify if entitlement certs are downloaded if subscribed using bogus poolid", groups = { "VerifySubscribingTobogusPoolID" }, enabled = true)
	@ImplementsNitrateTest(caseId = 50223)
	public void VerifySubscribingTobogusPoolID() throws JSONException,
	Exception {
		String poolId = null;
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		List<String[]> listOfSectionNameValues = new ArrayList<String[]>();
		listOfSectionNameValues.add(new String[] { "rhsmcertd",
				"autoAttachInterval".toLowerCase(), "1440" });
		clienttasks.config(null, null, true, listOfSectionNameValues);
		clienttasks.unsubscribe(true, (BigInteger) null, null, null, null);
		for (SubscriptionPool availList : clienttasks
				.getCurrentlyAvailableSubscriptionPools()) {
			poolId = availList.poolId;

		}
		String pool = randomizeCaseOfCharactersInString(poolId);
		clienttasks.subscribe_(null, null, pool, null, null, null, null, null,
				null, null, null);
		List<File> Cert = clienttasks.getCurrentEntitlementCertFiles();
		Assert.assertEquals(Cert.size(), 0);
	}

	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "Verify Functionality Access After Unregister", groups = { "VerifyFunctionalityAccessAfterUnregister" }, enabled = true)
	@ImplementsNitrateTest(caseId = 50215)
	public void VerifyFunctionalityAccessAfterUnregister()
			throws JSONException, Exception {
		clienttasks
		.register(sm_clientUsername, sm_clientPassword, sm_clientOrg);
		String availList = clienttasks.listAllAvailableSubscriptionPools()
				.getStdout();
		Assert.assertNotNull(availList);
		clienttasks.unregister(null, null, null);
		availList = clienttasks.list_(true, true, null, null, null, null, null,
				null, null, null, null).getStdout();
		String expected = "This system is not yet registered. Try 'subscription-manager register --help' for more information.";
		Assert.assertEquals(availList.trim(), expected);
		ConsumerCert consumercert = clienttasks.getCurrentConsumerCert();
		Assert.assertNull(consumercert);
	}

	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "Verify only One Cert is downloaded Per One Subscription", groups = {"VerifyOneCertPerOneSubscription"}, enabled = true)
	@ImplementsNitrateTest(caseId = 50215)
	public void VerifyOneCertPerOneSubscription() throws JSONException,
	Exception {
		int expected = 0;
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, false, null, null, null);
/* unnecessary
		clienttasks.restart_rhsmcertd(null, null, false, null);
		clienttasks.deleteFactsFileWithOverridingValues();
		clienttasks.unsubscribe(true, (BigInteger) null, null, null, null);
*/
/* takes too much time to execute; choose a random subset of available pools
		for (SubscriptionPool subscriptionpool : clienttasks
				.getCurrentlyAvailableSubscriptionPools()) {
*/
		for (SubscriptionPool subscriptionpool : getRandomSubsetOfList(clienttasks.getCurrentlyAvailableSubscriptionPools(),5)) {
			clienttasks.subscribe_(null, null, subscriptionpool.poolId, null,
					null, null, null, null, null, null, null);
			expected = expected + 1;
			List<File> Cert = clienttasks.getCurrentEntitlementCertFiles();
			Assert.assertEquals(Cert.size(), expected, "Total number of local entitlement certs after subscribing to '"+expected+"' different pools.");
		}
	}

	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "verify content set associated with product", groups = { "VerifyUnsubscribingCertV3","blockedByBug-895447"}, enabled = false)
	@ImplementsNitrateTest(caseId = 50215)
	public void VerifyUnsubscribingCertV3() throws JSONException, Exception {

		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		File expectCertFile = new File(System.getProperty("automation.dir",
				null) + "/expiredcerts/CertV3.pem");
		RemoteFileTasks.putFile(client.getConnection(),
				expectCertFile.toString(), "/root/", "0755");
		clienttasks.importCertificate_("/root/CertV3.pem");
		String expected = "0 subscriptions removed at the server."+"\n"+"1 local certificate has been deleted.";
		String result = clienttasks.unsubscribe(true, (BigInteger) null, null,
				null, null).getStdout();
		Assert.assertEquals(result.trim(), expected);
	}

	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "verify  rhsmcertd is logging update failed (255)", groups = {
			"VerifyRHSMCertdLogging", "blockedByBug-708512","blockedByBug-907638" }, enabled = true)
	public void VerifyRHSMCertdLogging() throws JSONException, Exception {
		int autoAttachInterval = 1;
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		String Frequency = clienttasks.getConfFileParameter(
				clienttasks.rhsmConfFile, "autoAttachInterval");
		clienttasks.restart_rhsmcertd(autoAttachInterval, null, false, null);
		clienttasks.waitForRegexInRhsmcertdLog("update failed (255)", 1);
		List<String[]> listOfSectionNameValues = new ArrayList<String[]>();
		listOfSectionNameValues.add(new String[] { "rhsmcertd",
				"autoAttachInterval".toLowerCase(), Frequency });
		clienttasks.config(null, null, true, listOfSectionNameValues);

	}

	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "verify content set associated with product", groups = { "VerifycertsAfterUnsubscribeAndunregister" }, enabled = true)
	@ImplementsNitrateTest(caseId = 50215)
	public void VerifyProductCertsAfterUnsubscribeAndunregister()
			throws JSONException, Exception {

		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, false, null, null, null);
		clienttasks.subscribe_(true, null, (String) null, null, null, null,
				null, null, null, null, null);
		List<File> ProductCerts = clienttasks.getCurrentProductCertFiles();
		Assert.assertFalse(ProductCerts.isEmpty());
		clienttasks.unsubscribe(true, (BigInteger) null, null, null, null);
		List<File> certs = clienttasks.getCurrentEntitlementCertFiles();
		Assert.assertTrue(certs.isEmpty());
		ProductCerts.clear();
		ProductCerts = clienttasks.getCurrentProductCertFiles();
		Assert.assertFalse(ProductCerts.isEmpty());
		clienttasks.unregister(null, null, null);
		ConsumerCert consumerCerts = clienttasks.getCurrentConsumerCert();
		Assert.assertNull(consumerCerts);
	}

	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "verify reregister with invalid consumerid", groups = { "VerifyRegisterUsingInavlidConsumerId" }, enabled = true)
	@ImplementsNitrateTest(caseId = 61716)
	public void VerifyregisterUsingInavlidConsumerId() throws JSONException,
	Exception {
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		String consumerId = clienttasks.getCurrentConsumerId();
		String invalidconsumerId = randomGenerator.nextInt() + consumerId;
		System.out.println(invalidconsumerId + "  " + consumerId);
		SSHCommandResult result = clienttasks.register_(sm_clientUsername,
				sm_clientPassword, sm_clientOrg, null, null, null,
				invalidconsumerId, null, null, null, (String) null, null, null,
				null, true, null, null, null, null);
		Assert.assertEquals(result.getStdout().trim(), "The system with UUID "
				+ consumerId + " has been unregistered");
		Assert.assertEquals(result.getStderr().trim(), "Consumer with id "
				+ invalidconsumerId + " could not be found.");
	}

	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "verify if corrupt identity cert displays a trace back for list command", groups = {
			"VerifyCorruptIdentityCert", "blockedByBug-607162" }, enabled = true)
	public void VerifycorruptIdentityCert() throws JSONException, Exception {
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		client.runCommandAndWait("cp /etc/pki/consumer/cert.pem /etc/pki/consumer/cert.pem.save");
		RemoteFileTasks.runCommandAndAssert(
				client,
				"openssl x509 -noout -text -in "
						+ clienttasks.consumerCertFile()
						+ " > /tmp/stdout; mv /tmp/stdout -f "
						+ clienttasks.consumerCertFile(), 0);
		String result = clienttasks.list_(null, true, null, null, null, null,
				null, null, null, null, null).getStdout();
		Assert.assertEquals(result.trim(),
				clienttasks.msg_ConsumerNotRegistered);
		client.runCommandAndWait("mv -f /etc/pki/consumer/cert.pem.save /etc/pki/consumer/cert.pem");

	}

	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "subscription-manager facts --update changes update date after facts update", groups = {
			"VerifyUpdateConsumerFacts", "blockedByBug-700821" }, enabled = true)
	public void VerifyupdateConsumerFacts() throws JSONException, Exception {

		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		String consumerid = clienttasks.getCurrentConsumerId();
		JSONObject jsonConsumer = new JSONObject(
				CandlepinTasks.getResourceUsingRESTfulAPI(
						sm_serverAdminUsername, sm_serverAdminPassword,
						sm_serverUrl, "/consumers/" + consumerid));
		String createdDateBeforeUpdate = jsonConsumer.getString("created");
		String UpdateDateBeforeUpdate = jsonConsumer.getString("updated");
		clienttasks.facts(null, true, null, null, null).getStderr();
		jsonConsumer = new JSONObject(
				CandlepinTasks.getResourceUsingRESTfulAPI(
						sm_serverAdminUsername, sm_serverAdminPassword,
						sm_serverUrl, "/consumers/" + consumerid));
		String createdDateAfterUpdate = jsonConsumer.getString("created");
		String UpdateDateAfterUpdate = jsonConsumer.getString("updated");
		Assert.assertEquals(createdDateBeforeUpdate, createdDateAfterUpdate,
				"no changed in date value after facts update");
		Assert.assertNoMatch(UpdateDateBeforeUpdate, UpdateDateAfterUpdate,
				"updated date has been changed after facts update");
	}

	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "verify healing of installed products without taking future subscriptions into consideration", groups = { "VerifyHealingForFutureSubscription","blockedByBug-907638"}, enabled = true)
	public void VerifyHealingForFuturesubscription() throws JSONException,
	Exception {
		List<String> productId = new ArrayList<String>();
/* unnecessary
		int autoAttachInterval = 2;
		clienttasks.deleteFactsFileWithOverridingValues();
		clienttasks.unsubscribe(true, (BigInteger) null, null, null, null);
*/
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		clienttasks.autoheal(null, null, true, null, null, null); // disabling autoheal
/* unnecessary; already tested by VerifyAutohealAttributeDefaultsToTrueForNewSystemConsumer_Test()
		clienttasks.service_level_(null, null, null, true, null, null, null,
				null, null, null, null, null);
		String consumerId = clienttasks.getCurrentConsumerId();
		JSONObject jsonConsumer = CandlepinTasks.setAutohealForConsumer(
				sm_clientUsername, sm_clientPassword, sm_serverUrl, consumerId,
				true);
		Assert.assertTrue(jsonConsumer.getBoolean("autoheal"),
				"A consumer's autoheal attribute value=true.");
*/
/* unnecessary
		Calendar now = new GregorianCalendar();
		now.add(Calendar.YEAR, 1);
		DateFormat yyyy_MM_dd_DateFormat = new SimpleDateFormat("yyyy-MM-dd");
		String onDateToTest = yyyy_MM_dd_DateFormat.format(now.getTime());
		clienttasks.subscribe_(true, null, (String) null, null, null, null,
				null, null, null, null, null);
		for (InstalledProduct installed : clienttasks
				.getCurrentlyInstalledProducts()) {
			if (installed.status.equals("Not Subscribed")
					&& installed.status.equals("Partially Subscribed"))
				moveProductCertFiles(installed.productId + ".pem");
				moveProductCertFiles(installed.productId + "._pem");
		}
*/
/* too time consuming; replacing with a faster algorithm below...
		for (SubscriptionPool availOnDate : getAvailableFutureSubscriptionsOndate(onDateToTest)) {
			System.out.println(availOnDate.poolId + " avail on date is");
			clienttasks.subscribe_(null, null, availOnDate.poolId, null, null,
					null, null, null, null, null, null);
		}
*/
		List<String> futureSystemSubscriptionPoolIds = new ArrayList<String>();
		for (List<Object> futureSystemSubscriptionPoolsDataRow : getAllFutureSystemSubscriptionPoolsDataAsListOfLists()) {
			SubscriptionPool futureSystemSubscriptionPool = (SubscriptionPool)futureSystemSubscriptionPoolsDataRow.get(0);
			futureSystemSubscriptionPoolIds.add(futureSystemSubscriptionPool.poolId);
		}
		Assert.assertTrue(!futureSystemSubscriptionPoolIds.isEmpty(), "Found future available subscriptions needed to attempt this test.");
		clienttasks.subscribe(null, null, futureSystemSubscriptionPoolIds, null, null, null, null, null, null, null, null);

		for (InstalledProduct installedproduct : clienttasks
				.getCurrentlyInstalledProducts()) {
			if (installedproduct.status.equals("Future Subscription")) {
				productId.add(installedproduct.productId);
			}
		}
/* takes too much time; replacing with a call to run_rhsmcertd_worker with autoheal...
		clienttasks.restart_rhsmcertd(null, autoAttachInterval, false, null);
		SubscriptionManagerCLITestScript.sleep(autoAttachInterval * 60 * 1000);
*/
		clienttasks.autoheal(null, true, null, null, null, null); // enabling autoheal
		clienttasks.run_rhsmcertd_worker(true);
/* insufficient assertions; re-implementing below...
		for (InstalledProduct installedproduct : clienttasks
				.getCurrentlyInstalledProducts()) {
			for (String productid : productId) {
				if (installedproduct.productId.equals(productid)) {
					Assert.assertEquals(installedproduct.status.trim(),
							"Subscribed");

				}
			}
		}
*/
		boolean assertedFutureSubscriptionIsNowSubscribed = false;
		for (InstalledProduct installedProduct : clienttasks.getCurrentlyInstalledProducts()) {
			for (String id : productId) {
				if (installedProduct.productId.equals(id)) {
					List<String> installedProductArches = new ArrayList<String>(Arrays.asList(installedProduct.arch.trim().split(" *, *")));	// Note: the arch can be a comma separated list of values
					if (installedProductArches.contains("x86")) {installedProductArches.addAll(Arrays.asList("i386","i486","i586","i686"));}	// Note: x86 is a general alias to cover all 32-bit intel microprocessors, expand the x86 alias
					if (installedProductArches.contains(clienttasks.arch) || installedProductArches.contains("ALL")) {
						Assert.assertEquals(installedProduct.status.trim(),
								"Subscribed", "Previously installed product '"+installedProduct.productName+"' covered by a Future Subscription should now be covered by a current subscription after auto-healing.");
						assertedFutureSubscriptionIsNowSubscribed = true;
					} else {
						Assert.assertEquals(installedProduct.status.trim(),
								"Future Subscription", "Mismatching arch installed product '"+installedProduct.productName+"' (arch='"+installedProduct.arch+"') covered by a Future Subscription should remain unchanged after auto-healing.");			
					}
				}
			}
		}
		Assert.assertTrue(assertedFutureSubscriptionIsNowSubscribed,"Verified at least one previously installed product covered by a Future Subscription is now covered by a current subscription after auto-healing.");
	}

	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "Verify unsubscribe from multiple invalid serial numbers", groups = { "UnsubscribeFromInvalidMultipleEntitlements" }, enabled = true)
	@ImplementsNitrateTest(caseId = 50230)
	public void UnsubscribeFromInvalidMultipleEntitlements()
			throws JSONException, Exception {
		List<BigInteger> serialnums = new ArrayList<BigInteger>();
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		clienttasks.unsubscribe(true, (BigInteger) null, null, null, null);
		for (SubscriptionPool pool : clienttasks.getCurrentlyAvailableSubscriptionPools()) {
			clienttasks.subscribe_(null, null, pool.poolId, null, null, null, null, null, null, null, null);
		}
		if(clienttasks.getCurrentlyConsumedProductSubscriptions().isEmpty())throw new SkipException(
				"Sufficient pools are not available");
		for (ProductSubscription consumed : clienttasks.getCurrentlyConsumedProductSubscriptions()) {
			serialnums.add(consumed.serialNumber);
		}
		
		int i = randomGenerator.nextInt(serialnums.size());
		int j = randomGenerator.nextInt(serialnums.size());
		if (i == j) {
			j = randomGenerator.nextInt(serialnums.size());

		}
		BigInteger serialOne = serialnums.get(i);
		BigInteger serialTwo = serialnums.get(j);
		String result = unsubscribeFromMultipleEntitlementsUsingSerialNumber(
				serialOne.multiply(serialTwo), serialTwo.multiply(serialOne))
				.getStdout();
		String expected = "Serial numbers unsuccessfully removed at the server:" + "\n"
				+ "   " + serialOne.multiply(serialTwo)
				+ " is not a valid value for serial" + "\n" + "   "
				+ serialTwo.multiply(serialOne)
				+ " is not a valid value for serial";
		Assert.assertEquals(result.trim(), expected);
	}

	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "Verify unsubscribe from multiple subscriptions", groups = {
			"UnsubscribeFromMultipleEntitlementsTest", "blockedByBug-867766","blockedByBug-906550" }, enabled = true)
	@ImplementsNitrateTest(caseId = 50230)
	public void UnsubscribeFromMultipleEntitlements() throws JSONException,
	Exception {
		int count=0;
		List<BigInteger> serialnums = new ArrayList<BigInteger>();
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		clienttasks.unsubscribe(true, (BigInteger) null, null, null, null);
		for (SubscriptionPool pool : clienttasks.getCurrentlyAvailableSubscriptionPools()) {
			if((count<=2)){
				if (CandlepinTasks.isPoolAModifier(sm_clientUsername, sm_clientPassword, pool.poolId, sm_serverUrl)) continue;	// skip modifier pools
				count++;
			clienttasks.subscribe(null, null, pool.poolId, null, null, null, null, null, null, null, null);
			}
			
		}
		
		for (ProductSubscription consumed : clienttasks
				.getCurrentlyConsumedProductSubscriptions()) {
			serialnums.add(consumed.serialNumber);
		}
		/* does not guarantee i != j
		int i = randomGenerator.nextInt(serialnums.size());
		int j = randomGenerator.nextInt(serialnums.size());
		if (i == j) {
			j = randomGenerator.nextInt(serialnums.size());

		}
		*/
		BigInteger serialOne = serialnums.get(randomGenerator.nextInt(serialnums.size()));	// serialnums.get(i);
		serialnums.remove(serialOne);
		BigInteger serialTwo = serialnums.get(randomGenerator.nextInt(serialnums.size()));	// serialnums.get(j);
		String result = unsubscribeFromMultipleEntitlementsUsingSerialNumber(
				serialOne, serialTwo).getStdout();
		
		String expected = "Serial numbers successfully removed at the server:" + "\n" + "   "
				+ serialOne + "\n" + "   " + serialTwo+ "\n" 
				+"2 local certificates have been deleted.";
		Assert.assertEquals(result.trim(), expected);
	}

	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "verify content set associated with product", groups = { "VerifyRegisterWithConsumerIdForDifferentUser" }, enabled = true)
	@ImplementsNitrateTest(caseId = 61710)
	public void VerifyRegisterWithConsumerIdForDifferentUser()
			throws JSONException, Exception {
		if (sm_client2Username==null) throw new SkipException("This test requires valid credentials for a second user.");
		clienttasks.register(sm_client2Username, sm_client2Password,
				sm_client2Org, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		String consumerid = clienttasks.getCurrentConsumerId();
		String result = clienttasks.register_(sm_clientUsername,
				sm_clientPassword, sm_clientOrg, null, null, null, consumerid,
				null, null, null, (String) null, null, null, null, true, null,
				null, null, null).getStderr();
		System.out.println("result  " + result);
		Assert.assertNotNull(result);
	}

	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "verify content set associated with product", groups = { "VerifyFactsListByOverridingValues" }, enabled = true)
	@ImplementsNitrateTest(caseId = 56389)
	public void VerifyFactsListByOverridingValues() throws JSONException,
	Exception {

		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		String listBeforeUpdate = clienttasks.facts(true, null, null, null,
				null).getStdout();
		Map<String, String> factsMap = new HashMap<String, String>();
		Integer sockets = 4;
		factsMap.put("cpu.cpu_socket(s)", String.valueOf(sockets));
		factsMap.put("uname.machine", "i386");
		clienttasks.createFactsFileWithOverridingValues(factsMap);
		clienttasks.facts(null, true, null, null, null);
		String listAfterUpdate = clienttasks.facts(true, null, null, null,
				null).getStdout();
		Assert.assertNoMatch(listAfterUpdate, listBeforeUpdate);
/* unnecessary, AfterGroups method deleteFactsFileWithOverridingValues() "VerifyFactsListByOverridingValues" will do it
		clienttasks.deleteFactsFileWithOverridingValues();
		clienttasks.facts(null, true, null, null, null);
*/
	}

	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "verify content set associated with product", groups = { "VerifyFactsListWithOutrageousValues" }, enabled = true)
	@ImplementsNitrateTest(caseId = 56897)
	public void VerifyFactsListWithOutrageousValues() throws JSONException,	Exception {

		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		String listBeforeUpdate = clienttasks.facts(true, null, null, null,
				null).getStdout();

		client.runCommandAndWait("echo '{fuzzing :testing}' >>/var/lib/rhsm/facts/facts.json");
		clienttasks.facts(null, true, null, null, null);
		String listAfterUpdate = clienttasks.facts(true, null, null, null,
				null).getStdout();
		Assert.assertFalse(listAfterUpdate.contentEquals("fuzzing"));
		Assert.assertEquals(listAfterUpdate, listBeforeUpdate);
		client.runCommandAndWait("cp /var/lib/rhsm/facts/facts.json /var/lib/rhsm/facts/facts.json.save");
		client.runCommandAndWait("sed /'uname.machine: x86_64'/d /var/lib/rhsm/facts/facts.json");
		clienttasks.facts(null, true, null, null, null);
		listAfterUpdate = clienttasks.facts(true, null, null, null, null)
				.getStdout();
		client.runCommandAndWait("mv -f /var/lib/rhsm/facts/facts.json.save /var/lib/rhsm/facts/facts.json");
		Assert.assertEquals(listAfterUpdate, listBeforeUpdate);

	}

	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "verify content set associated with product", groups = { "Verifycontentsetassociatedwithproduct" }, enabled = true)
	@ImplementsNitrateTest(caseId = 61115)
	public void Verifycontentsetassociatedwithproduct() throws JSONException,
	Exception {
		clienttasks.unregister(null, null, null);
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		clienttasks.unsubscribe(true, (BigInteger)null, null, null, null);
		List<SubscriptionPool> pools = clienttasks
				.getCurrentlyAvailableSubscriptionPools();
		clienttasks.subscribeToSubscriptionPool(pools.get(randomGenerator
				.nextInt(pools.size())));
		List<File> certs = clienttasks.getCurrentEntitlementCertFiles();
		RemoteFileTasks.runCommandAndAssert(
				client,
				"openssl x509 -noout -text -in "
						+ certs.get(randomGenerator.nextInt(certs.size()))
						+ " > /tmp/stdout; mv /tmp/stdout -f "
						+ certs.get(randomGenerator.nextInt(certs.size())), 0);
		String consumed = clienttasks.list_(null, null, true, null, null, null,
				null, null, null, null, null).getStderr();
		Assert.assertEquals(consumed.trim(), "Error loading certificate");

	}

	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "verify if rhsmcertd process refresh the identity certificate after every restart", groups = {
			"VerifyrhsmcertdRefreshIdentityCert", "blockedByBug-827034","blockedByBug-923159",
	"blockedByBug-827035" }, enabled = false)
	// TODO disabling this test for two reasons:
	//  1. it is dangerous to change the system dates
	//  2. the network service seems to stop when the date changes breaking the ability to ssh into the system
	public void VerifyrhsmcertdRefreshIdentityCert() throws JSONException,
	Exception {
		
		
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		DateFormat yyyy_MM_dd_DateFormat = new SimpleDateFormat("yyyy-MM-dd");
		log.info(clienttasks.getCurrentConsumerCert().validityNotBefore.toString() +"   "+ clienttasks.getCurrentConsumerCert().validityNotAfter.toString() +" cert validity before regeneration");
		Calendar StartTimeBeforeRHSM = clienttasks.getCurrentConsumerCert().validityNotBefore;
		Calendar EndTimeBeforeRHSM = clienttasks.getCurrentConsumerCert().validityNotAfter;
		String EndDateBeforeRHSM = yyyy_MM_dd_DateFormat.format(clienttasks.getCurrentConsumerCert().validityNotAfter.getTime());
		String StartDateBeforeRHSM=yyyy_MM_dd_DateFormat.format(clienttasks.getCurrentConsumerCert().validityNotBefore.getTime());
		String existingCertdate = client.runCommandAndWait("ls -lart /etc/pki/consumer/cert.pem | cut -d ' ' -f6,7,8")
				.getStdout();
		String StartDate=setDate(sm_serverHostname, sm_sshUser, sm_sshKeyPrivate,
				sm_sshkeyPassphrase, "date -s '15 year 9 month' +'%F'");
		log.info("Changed the date of candlepin"
				+ client.runCommandAndWait("hostname"));
		setDate(sm_clientHostname, sm_sshUser, sm_sshKeyPrivate,
				sm_sshkeyPassphrase, "date -s '15 year 9 month' +'%F'");
		clienttasks.restart_rhsmcertd(null, null, false, null);
		SubscriptionManagerCLITestScript.sleep(2* 60 * 1000);
		log.info(clienttasks.getCurrentConsumerCert().validityNotBefore.toString() +"   "+ clienttasks.getCurrentConsumerCert().validityNotAfter.toString() +" cert validity After regeneration");
		Calendar StartTimeAfterRHSM = clienttasks.getCurrentConsumerCert().validityNotBefore;
		Calendar EndTimeAfterRHSM = clienttasks.getCurrentConsumerCert().validityNotAfter;
		String EndDateAfterRHSM = yyyy_MM_dd_DateFormat.format(clienttasks.getCurrentConsumerCert().validityNotAfter.getTime());
		String StartDateAfterRHSM=yyyy_MM_dd_DateFormat.format(clienttasks.getCurrentConsumerCert().validityNotBefore.getTime());
		String updatedCertdate = client.runCommandAndWait("ls -lart /etc/pki/consumer/cert.pem | cut -d ' ' -f6,7,8").getStderr();
		String EndDate=setDate(sm_serverHostname, sm_sshUser, sm_sshKeyPrivate,
				sm_sshkeyPassphrase, "date -s '15 year ago 9 month ago' +'%F'");
		log.info("Changed the date of candlepin"
				+ client.runCommandAndWait("hostname"));
		setDate(sm_clientHostname, sm_sshUser, sm_sshKeyPrivate,
				sm_sshkeyPassphrase, "date -s '15 year ago 9 month ago' +'%F'");
		System.out.println();
		Assert.assertEquals(StartDateAfterRHSM, StartDate);
		Assert.assertEquals(EndDateAfterRHSM, EndDate);
		Assert.assertNotSame(StartTimeBeforeRHSM.getTime(),
				StartTimeAfterRHSM.getTime());
		Assert.assertNotSame(EndTimeBeforeRHSM.getTime(),
				EndTimeAfterRHSM.getTime());
		Assert.assertNotSame(existingCertdate, updatedCertdate);

	}
	
	

	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "subscription-manager unsubscribe --all on expired subscriptions removes certs from entitlement folder", groups = {
			"VerifyUnsubscribeAllForExpiredSubscription", "blockedByBug-852630","blockedByBug-906550" }, enabled = true)
	public void VerifyUnsubscribeAllForExpiredSubscription()
			throws JSONException, Exception {
/* unnecessary
		List<String[]> listOfSectionNameValues = new ArrayList<String[]>();
		listOfSectionNameValues.add(new String[] { "rhsmcertd",
				"certCheckInterval".toLowerCase(), "1" });
		listOfSectionNameValues.add(new String[] { "rhsmcertd",
				"autoAttachInterval".toLowerCase(), "1440" });
		clienttasks.config(null, null, true, listOfSectionNameValues);
*/
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, true, null, null,
				(String) null, null, null, null, true, false, null, null, null);
/* unnecessary
		String consumed = clienttasks.list_(null, null, true, null, null, null,null, null, null, null, null).getStdout();
		clienttasks.unsubscribe(true, (BigInteger) null, null, null, null);
*/
		File expectCertFile = new File(System.getProperty("automation.dir",
				null) + "/expiredcerts/Expiredcert.pem");
		RemoteFileTasks.putFile(client.getConnection(),
				expectCertFile.toString(), "/root/", "0755");
		clienttasks.importCertificate("/root/Expiredcert.pem");
/* does not assert the expired entitlement is being consumed; added a better assertion below...
		consumed = clienttasks.list_(null, null, true, null, null, null,null, null, null, null, null).getStdout();
		Assert.assertFalse((consumed == null));
*/
		List<ProductSubscription> consumedProductSubscriptions = clienttasks.getCurrentlyConsumedProductSubscriptions();
		List<ProductSubscription> activeProductSubscriptions = ProductSubscription.findAllInstancesWithMatchingFieldFromList("isActive", Boolean.TRUE, consumedProductSubscriptions);
		List<ProductSubscription> expiredProductSubscriptions = ProductSubscription.findAllInstancesWithMatchingFieldFromList("isActive", Boolean.FALSE, consumedProductSubscriptions);
		Assert.assertEquals(expiredProductSubscriptions.size(), 1, "Found one expired entitlement (indicated by Active:False) among the list of consumed subscriptions.");
		SSHCommandResult result = clienttasks.unsubscribe(true,(BigInteger) null, null, null, null);
/* rewritten in terms of activeProductSubscriptions and expiredProductSubscriptions below...
		List<File> Entitlementcerts = clienttasks
				.getCurrentEntitlementCertFiles();
		String expected = Entitlementcerts.size()
				+ " subscriptions removed at the server."+ "\n" +"1 local certificate has been deleted.";
*/
		String expected = String.format("%d subscriptions removed at the server.\n%d local certificates have been deleted.",activeProductSubscriptions.size(),activeProductSubscriptions.size()+expiredProductSubscriptions.size());
		Assert.assertEquals(result.getStdout().trim(), expected);

	}

	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "Verify One empty certificate file in /etc/rhsm/ca causes registration failure", groups = {
			"VerifyEmptyCertCauseRegistrationFailure_Test",
	"blockedByBug-806958" }, enabled = true)
	public void VerifyEmptyCertCauseRegistrationFailure_Test()
			throws JSONException, Exception {
		clienttasks.unregister(null, null, null);
		String FilePath = myEmptyCaCertFile;
		String command = "touch " + FilePath;
		client.runCommandAndWait(command);
		String result = clienttasks.register_(sm_clientUsername,
				sm_clientPassword, sm_clientOrg, null, null, null, null, null,
				null, null, (String) null, null, null, null, null, null, null,
				null, null).getStdout();
		String Expected = "Bad CA certificate: " + FilePath;
		Assert.assertEquals(result.trim(), Expected);
		
		

	}
	
	
	

	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "Verify facts update with incorrect proxy url produces traceback.", groups = {
			"VerifyFactsWithIncorrectProxy_Test", "blockedByBug-744504" }, enabled = true)
	public void VerifyFactsWithIncorrectProxy_Test() throws JSONException,
	Exception {
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		String basicauthproxyUrl = String.format("%s:%s", "testmachine.com",
				sm_basicauthproxyPort);
		basicauthproxyUrl = basicauthproxyUrl.replaceAll(":$", "");
		String facts = clienttasks.facts_(null, true, basicauthproxyUrl, null,
				null).getStdout();
		String Expect = clienttasks.msg_NetworkErrorUnableToConnect;
		Assert.assertEquals(facts.trim(), Expect);
	}

	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "Verify Subscription Manager Leaves Broken Yum Repos After Unregister", groups = {
			"ReposListAfterUnregisterTest", "blockedByBug-674652" }, enabled = true)
	public void VerifyRepoAfterUnregisterTest() throws JSONException,
	Exception {
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, true, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		clienttasks.unsubscribe(true, (BigInteger)null, null, null, null);
		clienttasks.subscribeToTheCurrentlyAvailableSubscriptionPoolsCollectively();
		List<YumRepo> repos = clienttasks.getCurrentlySubscribedYumRepos();
		Assert.assertFalse(repos.isEmpty());
		clienttasks.unregister(null, null, null);
		List<YumRepo> repo = clienttasks.getCurrentlySubscribedYumRepos();
		Assert.assertTrue(repo.isEmpty());
	}

	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "Verify if stacking entitlements reports as distinct entries in cli list --installed", groups = {
			"VerifyDistinct", "blockedByBug-733327" }, enabled = true)
	public void VerifyDistinctStackingEntires() throws Exception {
		
/* unnecessary		
		List<String> poolId = new ArrayList<String>();
		for (InstalledProduct installed : clienttasks.getCurrentlyInstalledProducts()) {
			if (!(installed.productId.equals("100000000000002"))){
				moveProductCertFiles(installed.productId + ".pem");
			}
		}
*/
		String poolId = null;
		clienttasks.register(sm_clientUsername, sm_clientPassword,sm_clientOrg, null, null, null, null, null, null, null,(String) null, null, null, null, true, null, null, null, null);
		clienttasks.autoheal(null, null, true, null, null, null);
// unnecessary		clienttasks.restart_rhsmcertd(null, null, false, null);
		int sockets = 4;
		Map<String, String> factsMap = new HashMap<String, String>();
		factsMap.put("cpu.cpu_socket(s)", String.valueOf(sockets));
		factsMap.put("virt.is_guest", String.valueOf(Boolean.FALSE));
		clienttasks.createFactsFileWithOverridingValues(factsMap);
		clienttasks.facts(null, true, null, null, null);
// unnecessary		clienttasks.getFactValue("cpu.cpu_socket(s)");
// unnecessary		clienttasks.unsubscribe(true, (BigInteger) null, null, null, null);
		
		boolean testResourceFound=false;
		for (SubscriptionPool pool : clienttasks.getCurrentlyAvailableSubscriptionPools()) {
// unnecessary			if (pool.multiEntitlement) {
				if(pool.productId.equals("awesomeos-x86_64")){
					clienttasks.subscribe(null, null, pool.poolId, null, null,"2", null, null, null, null, null);
					poolId = pool.poolId;
					testResourceFound=true; break;
				}
//			}
		}
		Assert.assertTrue(testResourceFound, "Found a pool corresponding to productId awesomeos-x86_64 needed for this test.");
		
		testResourceFound=false;
		for (InstalledProduct installed : clienttasks.getCurrentlyInstalledProducts()) {
			if(installed.productId.equals("100000000000002")){
			Assert.assertEquals(installed.status, "Partially Subscribed");
				testResourceFound=true; break;
			}
		}
		Assert.assertTrue(testResourceFound, "Found the installed productId 100000000000002 provided by subscription productId awesomeos-x86_64 needed for this test.");
		
		clienttasks.subscribe(null, null, poolId, null, null, "2",null, null, null, null, null);
		testResourceFound=false;
		for (InstalledProduct installedProduct : clienttasks.getCurrentlyInstalledProducts()) {
			if(installedProduct.productId.equals("100000000000002")){
				Assert.assertEquals(installedProduct.status, "Subscribed");
				testResourceFound=true; break;
			}
		}
		Assert.assertTrue(testResourceFound, "Found the installed productId 100000000000002 provided by subscription productId awesomeos-x86_64 needed for this test.");
		
/* unnecessary
		sockets = 1;
		factsMap.put("cpu.cpu_socket(s)", String.valueOf(sockets));
		clienttasks.createFactsFileWithOverridingValues(factsMap);
		clienttasks.facts(null, true, null, null, null);
*/
	}

	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "Verify deletion of subscribed product", groups = {
			"DeleteProductTest", "blockedByBug-684941" }, enabled = true)
	public void VerifyDeletionOfSubscribedProduct_Test() throws JSONException,
	Exception {
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(List<String>) null, null, null, null, true, null, null, null, null);
		String consumerid=clienttasks.getCurrentConsumerId();
		clienttasks.subscribe_(true, null, null, (String) null, null, null,
				null, null, null, null, null);
		if(clienttasks.getCurrentlyConsumedProductSubscriptions().isEmpty()){
			throw new SkipException("no installed products are installed");
		}
		else{
		for (InstalledProduct installed : clienttasks.getCurrentlyInstalledProducts()) {
			
			if (installed.status.equals("Subscribed")) {
				for (SubscriptionPool AvailSub : clienttasks.getCurrentlyAvailableSubscriptionPools()) {
					if (installed.productName.contains(AvailSub.subscriptionName)) {
						String jsonConsumer = CandlepinTasks.deleteResourceUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword, sm_serverUrl,"/products/" + AvailSub.productId);
						//String result=server.runCommandAndWait("tail -5 /var/log/candlepin/candlepin.log | grep req").getStdout();
						String expect = "\"displayMessage\""+ ":"+ "\"Product with UUID '"+ AvailSub.productId+ "'"+ " cannot be deleted while subscriptions exist.\",\"requestUuid\":\"[a-f,0-9,\\-]{36}\"";
						Assert.assertContainsMatch(jsonConsumer, expect);
					}
				}
			}
		}}

	}

	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "Verify Force Registration After Consumer is Deleted", groups = {
			"ForceRegAfterDEL", "blockedByBug-853876" }, enabled = true)
	public void VerifyForceRegistrationAfterConsumerDeletion_Test()
			throws JSONException, Exception {
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(List<String>) null, null, null, null, true, null, null, null, null);
		String consumerId = clienttasks.getCurrentConsumerId();
		CandlepinTasks.deleteResourceUsingRESTfulAPI(sm_serverAdminUsername,
				sm_serverAdminPassword, sm_serverUrl, "/consumers/"
						+ consumerId);
		String result = clienttasks.register(sm_clientUsername,
				sm_clientPassword, sm_clientOrg, null, null, null, null, null,
				null, null, (List<String>) null, null, null, null, true, null,
				null, null, null).getStdout();

		Assert.assertContainsMatch(result.trim(),
				"The system has been registered with ID: [a-f,0-9,\\-]{36}");

	}

	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "verify config Server port with blank or incorrect text produces traceback", groups = { "configBlankTest","blockedByBug-744654" }, enabled = true)
	// @ImplementsNitrateTest(caseId=)
	public void ConfigSetServerPortValueToBlank_Test() {

		List<String[]> listOfSectionNameValues = new ArrayList<String[]>();
		String section = "server";
		String name = "port";
		String newValue = clienttasks.getConfFileParameter(
				clienttasks.rhsmConfFile, section, name);
		listOfSectionNameValues.add(new String[] { section, name.toLowerCase(),
		"" });
		SSHCommandResult results = clienttasks.config(null, null, true,
				listOfSectionNameValues);
		String value = clienttasks.getConfFileParameter(
				clienttasks.rhsmConfFile, section, name);
		Assert.assertEquals("", results.getStdout().trim());
		listOfSectionNameValues.add(new String[] { section, name.toLowerCase(),
				newValue });
		clienttasks.config(null, null, true, listOfSectionNameValues);
		value = clienttasks.getConfFileParameter(clienttasks.rhsmConfFile,
				section, name);
		Assert.assertEquals(value, newValue);
	}

	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "subscription-manager: register --name , setting consumer name to blank", groups = {
			"registerwithname", "blockedByBug-669395" }, enabled = true)
	public void registerWithNameBlankTest() throws JSONException, Exception {
		String name = "test";
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, name, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		ConsumerCert consumerCert = clienttasks.getCurrentConsumerCert();
		Assert.assertEquals(consumerCert.name, name);
		name = "";
		SSHCommandResult result = clienttasks.register_(sm_clientUsername,
				sm_clientPassword, sm_clientOrg, null, null, name, null, null,
				null, null, (String) null, null, null, null, true, null, null,
				null, null);
		String expectedMsg = String
				.format("Error: system name can not be empty.");
		Assert.assertEquals(result.getExitCode(), new Integer(255));
		Assert.assertEquals(result.getStdout().trim(), expectedMsg);
		consumerCert = clienttasks.getCurrentConsumerCert();
		Assert.assertNotNull(consumerCert.name);

	}

	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "subscription-manager: register --consumerid  using a different user and valid consumerId", groups = {
			"reregister", "blockedByBug-627665" }, enabled = true)
	public void registerWithConsumerid_Test() throws JSONException, Exception {
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		String consumerId = clienttasks.getCurrentConsumerId();
		clienttasks.unsubscribe(true, (BigInteger)null, null, null, null);
		List<SubscriptionPool> pools = clienttasks
				.getCurrentlyAvailableSubscriptionPools();
		if (pools.isEmpty())
			throw new SkipException(
					"Cannot randomly pick a pool for subscribing when there are no available pools for testing.");
		SubscriptionPool pool = pools
				.get(randomGenerator.nextInt(pools.size()));
		clienttasks.subscribeToSubscriptionPool(pool);
		List<ProductSubscription> consumedSubscriptionsBeforeregister = clienttasks
				.getCurrentlyConsumedProductSubscriptions();
		clienttasks.clean_(null, null, null);
		if (sm_client2Username==null) throw new SkipException("This test requires valid credentials for a second user.");
		clienttasks.register_(sm_client2Username, sm_client2Password,
				sm_client2Org, null, null, null, consumerId, null, null, null,
				(String) null, null, null, null, null, null, null, null, null);
		String consumerIdAfter = clienttasks.getCurrentConsumerId();
		Assert.assertEquals(consumerId, consumerIdAfter,
				"The consumer identity  has not changed after registering with consumerid.");
		List<ProductSubscription> consumedscriptionsAfterregister = clienttasks
				.getCurrentlyConsumedProductSubscriptions();
		Assert.assertTrue(
				consumedscriptionsAfterregister
				.containsAll(consumedSubscriptionsBeforeregister)
				&& consumedSubscriptionsBeforeregister.size() == consumedscriptionsAfterregister
				.size(),
				"The list of consumed products after reregistering is identical.");
	}

	/**
	 * @author skallesh
	 */
	@Test(description = "subscription-manager: service-level --org (without --list option)", groups = {
			"ServicelevelTest", "blockedByBug-826856" }, enabled = true)
	public void ServiceLevelWithOrgWithoutList_Test() {

		SSHCommandResult result;
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(List<String>) null, null, null, null, true, null, null, null, null);
		result = clienttasks.service_level_(null, false, null, null,
				sm_clientUsername, sm_clientPassword, "MyOrg", null, null,
				null, null, null);
		Assert.assertEquals(result.getStdout().trim(),
				"Error: --org is only supported with the --list option");
	}

	/**
	 * @author skallesh
	 */
	@Test(description = "subscription-manager: facts --update (when registered)", groups = {
			"MyTestFacts", "blockedByBug-707525" }, enabled = true)
	public void FactsUpdateWhenregistered_Test() {

		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(List<String>) null, null, null, null, true, null, null, null, null);
		SSHCommandResult result = clienttasks.facts(null, true, null, null,
				null);
		Assert.assertEquals(result.getStdout().trim(),
				"Successfully updated the system facts.");
	}

	

	/**
	 * @author skallesh
	 */
	@Test(description = "subscription-manager: attempt register to with white space in the user name should fail", groups = {
			"registeredTests", "blockedByBug-719378" }, enabled = true)
	public void AttemptregisterWithWhiteSpacesInUsername_Test() {
		SSHCommandResult result = clienttasks.register_("user name",
				"password", sm_clientOrg, null, null, null, null, null, null,
				null, (String) null, null, null, null, true, null, null, null, null);
		Assert.assertEquals(
				result.getStderr().trim(),
				servertasks.invalidCredentialsMsg(),
				"The expected stdout result when attempting to register with a username containing whitespace.");
	}

	/**
	 * @author skallesh
	 * @throws JSONException
	 * @throws Exception
	 */
	@Test(description = "Auto-heal for partial subscription", groups = {
			"autohealPartial", "blockedByBug-746218","blockedByBug-907638","blockedByBug-907400"}, enabled = true)
	public void VerifyAutohealForPartialSubscription() throws Exception {
/* not necessary; will use clienttasks.run_rhsmcertd_worker(true) to invoke an immediate autoheal
		Integer healFrequency = 3;
*/
		Integer moreSockets = 0;
		List<String> productIds = new ArrayList<String>();
		List<String> poolId = new ArrayList<String>();
		Map<String, String> factsMap = new HashMap<String, String>();
		factsMap.put("virt.is_guest", Boolean.FALSE.toString());
		clienttasks.createFactsFileWithOverridingValues(factsMap);
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, true, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		clienttasks.autoheal(null, null, true, null, null, null); 	// disable autoheal
		for (SubscriptionPool pool : clienttasks
				.getCurrentlyAvailableSubscriptionPools()) {
/* pool.multiEntitlement is not longer used; has been replaced with CandlepinTasks.isPoolProductMultiEntitlement(...)
			if (pool.multiEntitlement) {
*/
			if (CandlepinTasks.isPoolProductMultiEntitlement(sm_clientUsername, sm_clientPassword, sm_serverUrl, pool.poolId)) {
				String poolProductSocketsAttribute = CandlepinTasks
						.getPoolProductAttributeValue(sm_clientUsername,
								sm_clientPassword, sm_serverUrl, pool.poolId,
								"stacking_id");
				if ((!(poolProductSocketsAttribute == null))
						&& (poolProductSocketsAttribute.equals("1"))) {
					String SocketsCount = CandlepinTasks
							.getPoolProductAttributeValue(sm_clientUsername,
									sm_clientPassword, sm_serverUrl,
									pool.poolId, "sockets");

					poolId.add(pool.poolId);
					moreSockets += Integer.valueOf(SocketsCount);
					productIds.addAll(CandlepinTasks.getPoolProvidedProductIds(sm_clientUsername, sm_clientPassword, sm_serverUrl, pool.poolId));
				}
			}
		}
		if (moreSockets==0) throw new SkipException("Expected to find a sockets based multi-entitlement pool with stacking_id 1 for this test.");
		factsMap.put("cpu.cpu_socket(s)", String.valueOf(moreSockets+Integer.valueOf(clienttasks.sockets)));
		clienttasks.createFactsFileWithOverridingValues(factsMap);
		clienttasks.facts(null, true, null, null, null);
/* not needed
		clienttasks.restart_rhsmcertd(null, healFrequency, false, null);
		clienttasks.unsubscribe(true, (BigInteger) null, null, null, null);

		clienttasks.subscribe_(null, null, poolId, null, null, null, null,
				null, null, null, null);
*/

		for (InstalledProduct installedProduct : clienttasks
				.getCurrentlyInstalledProducts()) {
/* wrong if statement; replaced with correct if statement below
			if (installedProduct.status.equals("Partially Subscribed")) {
				productId.add(installedProduct.productId);
*/
			if (productIds.contains(installedProduct.productId)) {
				Assert.assertEquals(installedProduct.status,
						"Partially Subscribed");
			}
		}
		Assert.assertTrue(!productIds.isEmpty(),"Found installed products that are partially subscribed after adding "+moreSockets+" more cpu.cpu_socket(s).");
/* replace this time consuming sleep with an immediate trigger of rhsmcertd with autohealing
		SubscriptionManagerCLITestScript.sleep(healFrequency * 60 * 1000);
*/
		clienttasks.autoheal(null, true, null, null, null, null); 	// enable autoheal
		clienttasks.run_rhsmcertd_worker(true);	// trigger autoheal
		for (InstalledProduct installedProduct : clienttasks
				.getCurrentlyInstalledProducts()) {
			for (String productId : productIds) {
				if (productId.equals(installedProduct.productId))
					Assert.assertEquals(installedProduct.status, "Subscribed", "Status of installed product '"+installedProduct.productName+"' after auto-healing.");
			}
		}
/* unnecessary
		moreSockets = 1;
		factsMap.put("cpu.cpu_socket(s)", String.valueOf(moreSockets));
		clienttasks.createFactsFileWithOverridingValues(factsMap);
		clienttasks.facts(null, true, null, null, null);
*/
	}

	/**
	 * @author skallesh
	 * @throws JSONException
	 * @throws Exception
	 */
	@Test(description = "Auto-heal with SLA", groups = { "AutoHealWithSLA","blockedByBug-907638","blockedByBug-907400"}, enabled = true)
	public void VerifyAutohealWithSLA() throws JSONException, Exception {
		Integer autoAttachInterval = 2;
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		List<String> availableServiceLevelData = clienttasks
				.getCurrentlyAvailableServiceLevels();
		String availableService = availableServiceLevelData.get(randomGenerator
				.nextInt(availableServiceLevelData.size()));
		clienttasks.subscribe_(true, availableService, (String) null, null,
				null, null, null, null, null, null, null);
		List<EntitlementCert> certs = clienttasks.getCurrentEntitlementCerts();
		if (certs.isEmpty()) {
			availableService = availableServiceLevelData.get(randomGenerator
					.nextInt(availableServiceLevelData.size()));
			clienttasks.subscribe_(true, availableService, (String) null, null,
					null, null, null, null, null, null, null);
		}
		clienttasks.service_level_(null, null, null, null, null,
				availableService, null, null, null, null, null, null);
		clienttasks.restart_rhsmcertd(null, autoAttachInterval, false, null);
		clienttasks.unsubscribe(true, (BigInteger) null, null, null, null);
		SubscriptionManagerCLITestScript.sleep(3 * 60 * 1000);
		certs = clienttasks.getCurrentEntitlementCerts();
		Assert.assertTrue(!(certs.isEmpty()),
				"autoheal is succesfull with Service level" + availableService);

	}

	/**
	 * @author skallesh
	 * @throws Exception
	 */
	@Test(description = "verfying Auto-heal when auto-heal parameter is turned off", groups = { "AutohealTurnedOff","blockedByBug-726411" }, enabled = true)
	public void AutohealTurnedOff() throws Exception {
		Integer healFrequency = 2;
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		String consumerId = clienttasks.getCurrentConsumerId();
		JSONObject jsonConsumer = CandlepinTasks.setAutohealForConsumer(
				sm_clientUsername, sm_clientPassword, sm_serverUrl, consumerId,
				false);
		Assert.assertFalse(
				jsonConsumer.getBoolean("autoheal"),
				"A consumer's autoheal attribute value can be toggled off (expected value=false).");
		clienttasks.restart_rhsmcertd(null, healFrequency, false, null);

		SubscriptionManagerCLITestScript.sleep(healFrequency * 60 * 1000);
		List<EntitlementCert> certs = clienttasks.getCurrentEntitlementCerts();
		Assert.assertTrue((certs.isEmpty()), "autoheal is successful");

	}

	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */

	@Test(description = "Verify if Subscription manager displays incorrect status for partially subscribed subscription", groups = {
			"VerifyStatusForPartialSubscription", "blockedByBug-743710" }, enabled = true)
	@ImplementsNitrateTest(caseId = 119327)
	public void VerifyStatusForPartialSubscription() throws JSONException,
	Exception {
/* unnecessary
		List<String[]> listOfSectionNameValues = new ArrayList<String[]>();
		listOfSectionNameValues.add(new String[] { "rhsmcertd",
				"autoAttachInterval".toLowerCase(), "1440" });
		clienttasks.config(null, null, true, listOfSectionNameValues);
*/
		String Flag = "false";
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, false, null, null, null);
/* unnecessary
		List<ProductSubscription> consumed = clienttasks
				.getCurrentlyConsumedProductSubscriptions();
		if (!(consumed.isEmpty())) {
			clienttasks.unsubscribe(true, (BigInteger) null, null, null, null);
		}
*/
		Map<String, String> factsMap = new HashMap<String, String>();
		factsMap.put("virt.is_guest", String.valueOf(Boolean.FALSE));
		clienttasks.createFactsFileWithOverridingValues(factsMap);
		clienttasks.facts(null, true, null, null, null);
		for (SubscriptionPool SubscriptionPool : clienttasks
				.getCurrentlyAllAvailableSubscriptionPools()) {
/* pool.multiEntitlement is not longer used; has been replaced with CandlepinTasks.isPoolProductMultiEntitlement(...)
			if (!(SubscriptionPool.multiEntitlement)) {
*/
			if (!CandlepinTasks.isPoolProductMultiEntitlement(sm_clientUsername, sm_clientPassword, sm_serverUrl, SubscriptionPool.poolId)) {
				String poolProductSocketsAttribute = CandlepinTasks
						.getPoolProductAttributeValue(sm_clientUsername,
								sm_clientPassword, sm_serverUrl,
								SubscriptionPool.poolId, "sockets");
				if ((!(poolProductSocketsAttribute == null))
						&& (poolProductSocketsAttribute.equals("2"))) {
					clienttasks.subscribeToSubscriptionPool_(SubscriptionPool);
					Flag = "true";
				}
			}
		}
		Assert.assertTrue(Boolean.valueOf(Flag),"Found and subscribed to non-multi-entitlement 2 socket subscription pool(s) for this test.");
		Integer moreSockets = 4;
		factsMap.put("cpu.cpu_socket(s)", String.valueOf(moreSockets+Integer.valueOf(clienttasks.sockets)));
		clienttasks.createFactsFileWithOverridingValues(factsMap);
		clienttasks.facts(null, true, null, null, null);
		Flag = "false";
		for (InstalledProduct product : clienttasks
				.getCurrentlyInstalledProducts()) {
//			if (product.status.equals("Partially Subscribed")) {
			if (!product.status.equals("Not Subscribed") && !product.status.equals("Subscribed") && !product.status.equals("Unknown")) {
				Assert.assertEquals(product.status,"Partially Subscribed","Installed product '"+product.productName+"' status is Partially Subscribed.");
				Flag = "true";
			}
		}
/* not necessary
		moreSockets = 1;
		factsMap.put("cpu.cpu_socket(s)", String.valueOf(moreSockets));
		clienttasks.createFactsFileWithOverridingValues(factsMap);
*/
		Assert.assertEquals(Flag, "true","Verified Partially Subscribed installed product(s).");
	}

	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "Auto-heal for Expired subscription", groups = {
			"AutohealForExpired", "blockedByBug-746088","blockedByBug-907638","blockedByBug-907400"}, enabled = true)
	public void VerifyAutohealForExpiredSubscription() throws JSONException,
	Exception {
		int healFrequency = 2;
		List<String> Expiredproductid = new ArrayList<String>();
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		String consumerId = clienttasks.getCurrentConsumerId();
		clienttasks.autoheal(null, true, null, null, null, null);

		clienttasks.unsubscribe(true, (BigInteger) null, null, null, null);
		clienttasks.service_level_(null, null, null, true, null, null, null,
				null, null, null, null, null);
		File expectCertFile = new File(System.getProperty("automation.dir",
				null) + "/expiredcerts/Expiredcert.pem");
		RemoteFileTasks.putFile(client.getConnection(),
				expectCertFile.toString(), "/root/", "0755");
		clienttasks.importCertificate_("/root/Expiredcert.pem");
		for (InstalledProduct product : clienttasks
				.getCurrentlyInstalledProducts()) {
			if (product.status.equals("Expired"))
				Expiredproductid.add(product.productId);
		}
		if ((Expiredproductid.size() == 0)) {
			throw new SkipException(
					"No expired products are available for testing");
		} else {
			clienttasks.restart_rhsmcertd(null, healFrequency, false, null);
			SubscriptionManagerCLITestScript.sleep(3 * 60 * 1000);
			for (InstalledProduct product : clienttasks.getCurrentlyInstalledProducts()) {
				for (int i = 0; i < Expiredproductid.size(); i++) {

					if (product.productId.equals(Expiredproductid.get(i)))
						Assert.assertEquals(product.status, "Subscribed");

				}
			}
		}
	}

	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "Auto-heal for subscription", groups = {"AutoHeal","blockedByBug-907638","blockedByBug-726411","blockedByBug-907400"}, enabled = true)
	@ImplementsNitrateTest(caseId = 119327)
	public void VerifyAutohealForSubscription() throws JSONException, Exception {
/* unnecessary
		Integer healFrequency = 2;
*/
		clienttasks.register(sm_clientUsername, sm_clientPassword,sm_clientOrg, null, null, null, null, null, null, null,(String) null, null, null, null, true, null, null, null, null);
/* unnecessary; the consumer's autoheal attribute defaults to true
		clienttasks.unsubscribeFromAllOfTheCurrentlyConsumedProductSubscriptions();
		String consumerId = clienttasks.getCurrentConsumerId();
		clienttasks.autoheal(null, true, null, null, null, null);
		clienttasks.service_level_(null, null, null, true, null, null, null,null, null, null, null, null);
*/
/* takes too long; calling run_rhsmcertd_worker with autoheal instead...
		clienttasks.restart_rhsmcertd(null, healFrequency, false, true);	// argument assertCertificatesUpdate should be true for this test (and most all tests)  
		SubscriptionManagerCLITestScript.sleep(3 * 60 * 1000);
*/
		Assert.assertTrue(clienttasks.getCurrentEntitlementCerts().isEmpty(), "After immediately registering with force, there are no entitlements attached.");
		clienttasks.run_rhsmcertd_worker(true);
		List<EntitlementCert> certs = clienttasks.getCurrentEntitlementCerts();
		List<ProductSubscription> consumed = clienttasks.getCurrentlyConsumedProductSubscriptions();
		log.info("Currently the consumed products are" + consumed.size());
		// this assertion assumes that the currently available subscriptions provide coverage for the currently installed products
		Assert.assertTrue(!clienttasks.getCurrentEntitlementCerts().isEmpty(), "Asserting that entitlement certs have been granted to the system indicating that autoheal was successful invoked to cover its currently installed products.");
	}

	/**
	 * @author skallesh
	 * @throws JSONException
	 * @throws Exception
	 */
	@Test(description = "Auto-heal with SLA", groups = { "AutoHealFailForSLA" }, enabled = true)
	public void VerifyAutohealFailForSLA() throws JSONException, Exception {
		Integer healFrequency = 2;
		String filename = null;
		System.out.println(RemoteFileTasks.testExists(client, "/etc/pki/tmp1"));
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		List<String> availableServiceLevelData = clienttasks
				.getCurrentlyAvailableServiceLevels();
		String availableService = availableServiceLevelData.get(randomGenerator
				.nextInt(availableServiceLevelData.size()));
		clienttasks.service_level(null, null, availableService, null, null, null, null,
				null, null, null, null, null);
		clienttasks.unsubscribe(true,(BigInteger)null, null, null, null);
		clienttasks.subscribe(true, null, (String) null, null,
				null, null, null, null, null, null, null);
		for (InstalledProduct installedProduct : clienttasks
				.getCurrentlyInstalledProducts()) {

			if (installedProduct.status.equalsIgnoreCase("Subscribed") || installedProduct.status.equalsIgnoreCase(
							"Partially Subscribed")) {
					moveProductCertFiles(installedProduct.productId + ".pem");
					moveProductCertFiles(installedProduct.productId + "_.pem");

			}
		}
		clienttasks.unsubscribe(true,(BigInteger)null, null, null, null);
		List<EntitlementCert> certsbeforeRHSMService = clienttasks
				.getCurrentEntitlementCerts();
		log.info("cert contents are " + certsbeforeRHSMService);
		clienttasks.restart_rhsmcertd(null, healFrequency, false, null);
		SubscriptionManagerCLITestScript.sleep(healFrequency * 60 * 1000);
		List<ProductSubscription> consumed = clienttasks.getCurrentlyConsumedProductSubscriptions();
		Assert.assertTrue((consumed.isEmpty()), "autoheal has failed");
		List<EntitlementCert> certs = clienttasks.getCurrentEntitlementCerts();
		System.out.println(certs.size());
	}

	
	/**
	 * @author skallesh
	 * @throws IOException 
	 */

	@Test(description = "subscription-manager: subscribe multiple pools in incorrect format", groups = {
			"MysubscribeTest", "blockedByBug-772218" }, enabled = true)
	public void VerifyIncorrectSubscriptionFormat() throws IOException {
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		List<String> poolid = new ArrayList<String>();
		for (SubscriptionPool pool : clienttasks
				.getCurrentlyAllAvailableSubscriptionPools()) {
			poolid.add(pool.poolId);
		}
		if (poolid.isEmpty())
			throw new SkipException(
					"Cannot randomly pick a pool for subscribing when there are no available pools for testing.");
		int i = randomGenerator.nextInt(poolid.size());
		int j = randomGenerator.nextInt(poolid.size());
		if (i == j) {
			j = randomGenerator.nextInt(poolid.size());
		}
			SSHCommandResult subscribeResult = subscribeInvalidFormat_(null,
					null, poolid.get(i), poolid.get(j), null, null, null, null,
					null, null, null, null);
			Assert.assertEquals(subscribeResult.getStdout().trim(),
					"cannot parse argument: " + poolid.get(j));
		

	}

	/**
	 * @author skallesh
	 * @throws Exception
	 */
	@Test(description = "Verify that Entitlement Start Dates is the Subscription Start Date ", groups = {
			"VerifyEntitlementStartDateIsSubStartDate_Test",
	"blockedByBug-670831" }, enabled = true)
	public void VerifyEntitlementStartDate_Test() throws JSONException,
	Exception {
// unnecessary		clienttasks.autoheal(null, null, true, null, null, null);
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, false, null, null, null);
// unnecessary		clienttasks.unsubscribe(true, (BigInteger)null, null, null, null);
// takes too long		for (SubscriptionPool pool : clienttasks.getCurrentlyAvailableSubscriptionPools()) {
		for (SubscriptionPool pool : getRandomSubsetOfList(clienttasks.getCurrentlyAvailableSubscriptionPools(),5)) {	
			JSONObject jsonPool = new JSONObject(CandlepinTasks.getResourceUsingRESTfulAPI(
					sm_clientUsername, sm_clientPassword, sm_serverUrl,"/pools/" + pool.poolId));
			Calendar subStartDate = parseISO8601DateString(jsonPool.getString("startDate"), "GMT");
			EntitlementCert entitlementCert = clienttasks.getEntitlementCertFromEntitlementCertFile(clienttasks.subscribeToSubscriptionPool_(pool));
			Calendar entStartDate = entitlementCert.validityNotBefore;
			Assert.assertEquals(entStartDate,subStartDate,"The entitlement start date '"
							+ EntitlementCert.formatDateString(entStartDate)
							+ "' granted from pool " + pool.poolId
							+ " should equal its subscription start date '"
							+ OrderNamespace.formatDateString(subStartDate)
							+ "'.");
		}
	}

	/**
	 * @author skallesh
	 * @throws Exception
	 */
	@Test(description = "Verify if architecture for auto-subscribe test", groups = { "VerifyarchitectureForAutobind_Test","blockedByBug-664847" }, enabled = true)
	public void VerifyarchitectureForAutobind_Test() throws Exception {

		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		Map<String, String> result = clienttasks.getFacts();
		String arch = result.get("uname.machine");
		List<String> cpu_arch = new ArrayList<String>();
		String input = "x86_64|i686|ia64|ppc|ppc64|s390x|s390";
		String[] values = input.split("\\|");
		Boolean flag = false;
		Boolean expected = true;
		for (int i = 0; i < values.length; i++) {
			cpu_arch.add(values[i]);
		}

		Pattern p = Pattern.compile(arch);
		Matcher matcher = p.matcher(input);
		while (matcher.find()) {
			String pattern_ = matcher.group();
			cpu_arch.remove(pattern_);

		}
		String architecture = cpu_arch.get(randomGenerator.nextInt(cpu_arch
				.size()));
		for (SubscriptionPool pool : clienttasks
				.getCurrentlyAvailableSubscriptionPools()) {
			if ((pool.subscriptionName).contains(" " + architecture)) {
				flag = true;
				Assert.assertEquals(flag, expected);
			}

		}

		for (SubscriptionPool pools : clienttasks
				.getCurrentlyAllAvailableSubscriptionPools()) {
			if ((pools.subscriptionName).contains(architecture)) {
				flag = true;
				Assert.assertEquals(flag, expected);
			}

		}
		Map<String, String> factsMap = new HashMap<String, String>();
		factsMap.put("uname.machine", String.valueOf(architecture));
		clienttasks.createFactsFileWithOverridingValues(factsMap);
		clienttasks.facts(null, true, null, null, null);
	}

	/**
	 * @author skallesh
	 * @throws Exception
	 */
	@Test(description = "Verify if rhsm not logging subscriptions and products properly ", groups = { "VerifyRhsmLogging_Test","blockedByBug-668032","blockedByBug-907638" }, enabled = true)
	public void VerifyRhsmLoggingTest() throws Exception {
		Boolean actual = true;
		
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		String LogMarker = System.currentTimeMillis()+" Testing ***************************************************************";
		RemoteFileTasks.markFile(client, clienttasks.rhsmLogFile, LogMarker);
		for (SubscriptionPool pool : clienttasks.getCurrentlyAllAvailableSubscriptionPools()) {
			List<String> providedProducts = CandlepinTasks.getPoolProvidedProductIds(sm_clientUsername,sm_clientPassword, sm_serverUrl, pool.poolId);
			if (((providedProducts.size()) > 2) ||((providedProducts.size()) == 2) ) {
				actual =false;
				clienttasks.subscribe_(null, null, pool.poolId, null, null,
						null, null, null, null, null, null);
			
			}
			}
		if(actual==false){
			actual=true;
			Boolean flag = RegexInRhsmLog("@ /etc/pki/entitlement",RemoteFileTasks.getTailFromMarkedFile(client, clienttasks.rhsmLogFile, LogMarker, null));
			Assert.assertEquals(flag, actual);
		}else throw new SkipException("no bundled products available for testing");	
	}

	/**
	 * @author skallesh
	 * @throws Exception
	 */
	@Test(description = "Verify if the status of installed products match when autosubscribed,and when you subscribe all the available products ", groups = {"VerifyFuturesubscription_Test", "blockedByBug-746035" }, enabled = true)
	public void VerifyFuturesubscription_Test() throws Exception {
//unnecessary		clienttasks.unsubscribe(true, (BigInteger) null, null, null, null);
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, false, null, null, null);
		List<String> productId = new ArrayList<String>();
/* unnecessary
		Calendar now = new GregorianCalendar();
		now.add(Calendar.YEAR, 1);
		DateFormat yyyy_MM_dd_DateFormat = new SimpleDateFormat("yyyy-MM-dd");
		String onDateToTest = yyyy_MM_dd_DateFormat.format(now.getTime());
		clienttasks.subscribe_(true, null, (String) null, null, null, null,
				null, null, null, null, null);
		for (InstalledProduct installed : clienttasks
				.getCurrentlyInstalledProducts()) {
			if (installed.status.equals("Not Subscribed")
					&& installed.status.equals("Partially Subscribed"))
				moveProductCertFiles(installed.productId + "*");
		}
*/
/* too time consuming; replacing with a faster algorithm below...
		for (SubscriptionPool availOnDate : getAvailableFutureSubscriptionsOndate(onDateToTest)) {
			clienttasks.subscribe_(null, null, availOnDate.poolId, null, null,
					null, null, null, null, null, null);
		}
*/
		List<String> futureSystemSubscriptionPoolIds = new ArrayList<String>();
		for (List<Object> futureSystemSubscriptionPoolsDataRow : getAllFutureSystemSubscriptionPoolsDataAsListOfLists()) {
			SubscriptionPool futureSystemSubscriptionPool = (SubscriptionPool)futureSystemSubscriptionPoolsDataRow.get(0);
			futureSystemSubscriptionPoolIds.add(futureSystemSubscriptionPool.poolId);
		}
		Assert.assertTrue(!futureSystemSubscriptionPoolIds.isEmpty(), "Found future available subscriptions needed to attempt this test.");
		clienttasks.subscribe(null, null, futureSystemSubscriptionPoolIds, null, null, null, null, null, null, null, null);

		
		for (InstalledProduct installedproduct : clienttasks
				.getCurrentlyInstalledProducts()) {
			if (installedproduct.status.equals("Future Subscription")) {

				productId.add(installedproduct.productId);
			}
		}
		Assert.assertTrue(!productId.isEmpty(), "Found installed product(s) with a Future Subscription Status needed to attempt this test.");

		clienttasks.subscribe_(true, null, (String) null, null, null, null,
				null, null, null, null, null);
/* insufficient assertions; re-implementing below...
		for (InstalledProduct installedproduct : clienttasks
				.getCurrentlyInstalledProducts()) {
			for (String productid : productId) {
				if (installedproduct.productId.equals(productid)) {
					Assert.assertEquals(installedproduct.status.trim(),
							"Subscribed");

				}
			}
		}
*/
		boolean assertedFutureSubscriptionIsNowSubscribed = false;
		for (InstalledProduct installedProduct : clienttasks.getCurrentlyInstalledProducts()) {
			for (String id : productId) {
				if (installedProduct.productId.equals(id)) {
					List<String> installedProductArches = new ArrayList<String>(Arrays.asList(installedProduct.arch.trim().split(" *, *")));	// Note: the arch can be a comma separated list of values
					if (installedProductArches.contains("x86")) {installedProductArches.addAll(Arrays.asList("i386","i486","i586","i686"));}	// Note: x86 is a general alias to cover all 32-bit intel microprocessors, expand the x86 alias
					if (installedProductArches.contains(clienttasks.arch) || installedProductArches.contains("ALL")) {
						Assert.assertEquals(installedProduct.status.trim(),
								"Subscribed", "Previously installed product '"+installedProduct.productName+"' covered by a Future Subscription should now be covered by a current subscription after auto-subscribing.");
						assertedFutureSubscriptionIsNowSubscribed = true;
					} else {
						Assert.assertEquals(installedProduct.status.trim(),
								"Future Subscription", "Mismatching arch installed product '"+installedProduct.productName+"' (arch='"+installedProduct.arch+"') covered by a Future Subscription should remain unchanged after auto-subscribing.");			
					}
				}
			}
		}
		Assert.assertTrue(assertedFutureSubscriptionIsNowSubscribed,"Verified at least one previously installed product covered by a Future Subscription is now covered by a current subscription after auto-subscribing.");
	}

	protected Calendar parseISO8601DateString(String dateString, String timeZone) {
		String iso8601DatePattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
		String datePattern = iso8601DatePattern;
		if (timeZone == null)
			datePattern = datePattern.replaceFirst("Z$", ""); // strip off final
		// timezone
		// offset symbol
		// from
		// iso8601DatePattern
		return parseDateStringUsingDatePattern(dateString, datePattern,
				timeZone);
	}

	protected Calendar parseDateStringUsingDatePattern(String dateString,
			String datePattern, String timeZone) {
		try {
			DateFormat dateFormat = new SimpleDateFormat(datePattern); // format="yyyy-MM-dd'T'HH:mm:ss.SSSZ"
			// will
			// parse
			// dateString="2012-02-08T00:00:00.000+0000"
			if (timeZone != null)
				dateFormat.setTimeZone(TimeZone.getTimeZone(timeZone)); // timeZone="GMT"
			Calendar calendar = new GregorianCalendar();
			calendar.setTimeInMillis(dateFormat.parse(dateString).getTime());
			return calendar;
		} catch (ParseException e) {
			log.warning("Failed to parse " + (timeZone == null ? "" : timeZone)
					+ " date string '" + dateString + "' with format '"
					+ datePattern + "':\n" + e.getMessage());
			return null;
		}
	}

	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "Verify if the status of installed products match when autosubscribed,and when you subscribe all the available products ", groups = { "VerifyautosubscribeTest" }, enabled = true)
	public void VerifyautosubscribeTest() throws JSONException, Exception {

		List<String> ProductIdBeforeAuto = new ArrayList<String>();
		List<String> ProductIdAfterAuto = new ArrayList<String>();
		clienttasks.deleteFactsFileWithOverridingValues();
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		clienttasks.unsubscribe(true, (BigInteger) null, null, null, null);
		clienttasks.subscribeToTheCurrentlyAvailableSubscriptionPoolsCollectively();
		for (InstalledProduct installedProductsBeforeAuto : clienttasks
				.getCurrentlyInstalledProducts()) {
			if (installedProductsBeforeAuto.status.equals("Subscribed"))
				ProductIdBeforeAuto.add(installedProductsBeforeAuto.productId);
		}

		clienttasks.unsubscribe(true, (BigInteger) null, null, null, null);
		clienttasks.subscribe_(true, null, (String) null, null, null, null,
				null, null, null, null, null);
		for (InstalledProduct installedProductsAfterAuto : clienttasks
				.getCurrentlyInstalledProducts()) {
			if (installedProductsAfterAuto.status.equals("Subscribed"))
				ProductIdAfterAuto.add(installedProductsAfterAuto.productId);
		}
		Assert.assertEquals(ProductIdBeforeAuto.size(),
				ProductIdAfterAuto.size());
	}

	/**
	 * @author skallesh
	 * @throws Exception
	 */
	@Test(description = "Verify if autosubscribe ignores socket count on non multi-entitled subscriptions ", groups = { "VerifyautosubscribeIgnoresSocketCount_Test","blockedByBug-743704" }, enabled = true)
	public void VerifyautosubscribeIgnoresSocketCount_Test() throws Exception {
		int socketnum = 0;
		int socketvalue = 0;
		List<String> SubscriptionId = new ArrayList<String>();
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, false, null, null, null);
		for (SubscriptionPool SubscriptionPool : clienttasks.getCurrentlyAllAvailableSubscriptionPools()) {
/* pool.multiEntitlement is no longer used; has been replaced with CandlepinTasks.isPoolProductMultiEntitlement(...)
			if (!(SubscriptionPool.multiEntitlement)) {
*/
			if (!CandlepinTasks.isPoolProductMultiEntitlement(sm_clientUsername, sm_clientPassword, sm_serverUrl, SubscriptionPool.poolId)) {
				SubscriptionId.add(SubscriptionPool.subscriptionName);
				String poolProductSocketsAttribute = CandlepinTasks
						.getPoolProductAttributeValue(sm_clientUsername,
								sm_clientPassword, sm_serverUrl,
								SubscriptionPool.poolId, "sockets");
				if (!(poolProductSocketsAttribute == null)) {
					socketvalue = Integer.parseInt(poolProductSocketsAttribute);
					if (socketvalue > socketnum) {
						socketnum = socketvalue;
					}
/* unnecessary
				} else {
					socketvalue = 0;
*/
				}
			}
		}
		Map<String, String> factsMap = new HashMap<String, String>();
		factsMap.put("cpu.cpu_socket(s)", String.valueOf(socketnum + 2));
		factsMap.put("virt.is_guest", String.valueOf(Boolean.FALSE));
		clienttasks.createFactsFileWithOverridingValues(factsMap);
		clienttasks.facts(null, true, null, null, null);
		
		clienttasks.subscribe_(true, null, (String) null, null, null, null,
				null, null, null, null, null);
		
if (true) throw new SkipException("The remaining test logic in this test needs a re-write.");
/* TODO I can't figure out what is being tested here.  It will always pass.
 * It will certainly pass if the nested Assert is never reached.
 * The inner-most Assert.assertEquals("Subscribed",(installedProductsAfterAuto.status).trim()) will always pass because it is already inside decision if ((installedProductsAfterAuto.status).equalsIgnoreCase("Subscribed")).
 * The call to facts update does nothing if you don't reload the installedProductsAfterAuto variable
 */
		for (InstalledProduct installedProductsAfterAuto : clienttasks
				.getCurrentlyInstalledProducts()) {
			for (String pool : SubscriptionId) {
				if (installedProductsAfterAuto.productName.contains(pool))

					if ((installedProductsAfterAuto.status)
							.equalsIgnoreCase("Subscribed")) {
//						Map<String, String> factsMap = new HashMap<String, String>();
						factsMap.put("cpu.cpu_socket(s)", String.valueOf(1));
						clienttasks
						.createFactsFileWithOverridingValues(factsMap);
						clienttasks.facts(null, true, null, null, null);
						Assert.assertEquals("Subscribed",
								(installedProductsAfterAuto.status).trim(),
								"test  has failed");
					}
			}
		}
	}

	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(description = "subscription-manager: entitlement key files created with weak permissions", groups = {
			"MykeyTest", "blockedByBug-720360" }, enabled = true)
	public void VerifyKeyFilePermissions() throws JSONException, Exception {
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		clienttasks.subscribeToTheCurrentlyAvailableSubscriptionPoolsCollectively();
		String subscribeResult = getEntitlementCertFilesWithPermissions();
		Pattern p = Pattern.compile("[,\\s]+");
		String[] result = p.split(subscribeResult);
		for (int i = 0; i < result.length; i++) {
			Assert.assertEquals(result[i], "-rw-------.",
					"permission for etc/pki/entitlement/<serial>-key.pem is -rw-------");
			i++;
		}
	}

	@BeforeGroups(groups = "setup", value = { "VerifyDistinct",
			"VerifyStatusForPartialSubscription", "AutoHeal",
			"AutoHealFailForSLA", "VerifyautosubscribeTest",
			"BugzillaTests", "autohealPartial",
			"VerifyEntitlementStartDate_Test", "reregister" }, enabled = true)
	public void unsubscribeBeforeGroup() {
		clienttasks.unsubscribe(true, (BigInteger) null, null, null, null);
	}

	@BeforeGroups(groups = "setup", value = { "VerifyDistinct", "AutoHeal",
			"autohealPartial", "BugzillaTests" }, enabled = true)
	public void unsetServicelevelBeforeGroup() {
		clienttasks.service_level_(null, null, null, true, null, null, null,
				null, null, null, null, null);
	}
	
	

	@BeforeGroups(groups = "setup", value = { "VerifyDistinct", "AutoHeal",
			"VerifyStatusForPartialSubscription", "autohealPartial",
			"VerifyEntitlementStartDate_Test", "BugzillaTests" }, enabled = true)
	public void setHealFrequencyGroup() {
		List<String[]> listOfSectionNameValues = new ArrayList<String[]>();
		listOfSectionNameValues.add(new String[] { "rhsmcertd",
				"autoAttachInterval".toLowerCase(), "1440" });
		clienttasks.config(null, null, true, listOfSectionNameValues);
		String param = clienttasks.getConfFileParameter(
				clienttasks.rhsmConfFile, "rhsmcertd", "autoAttachInterval");

		Assert.assertEquals(param, "1440");
	}
	
	@AfterGroups(groups = "setup", value = {"VerifyRepoFileExistance"}, enabled = true)
	public void TurnonRepos(){
		List<String[]> listOfSectionNameValues = new ArrayList<String[]>();
		listOfSectionNameValues = new ArrayList<String[]>();
		listOfSectionNameValues.add(new String[] { "rhsm","manage_repos", "1" });
		clienttasks.config(null, null, true, listOfSectionNameValues);
	}

/* this methods looks more like a Test than a BeforeGroups configuration method; changing to a Test...
	@BeforeGroups(groups = "setup", value = { "BugzillaTests","VerifyEntitlementStartDateIsSubStartDate_Test","unsubscribeImportedcert" }, enabled = true)
*/
	@Test(	description = "verify that the autoheal attribute of a new system consumer defaults to true",
			groups = {},
			enabled = true)
	public void VerifyAutohealAttributeDefaultsToTrueForNewSystemConsumer_Test()
			throws Exception {
		clienttasks.register(sm_clientUsername,sm_clientPassword, sm_clientOrg, null, null, null,
				null, null, null, null, (String) null, null, null,
				null, true, null, null, null, null);
		String consumerId = clienttasks.getCurrentConsumerId();
/* unnecessary; we should be getting the consumer without setting the autoheal attribute
		JSONObject jsonConsumer = CandlepinTasks.setAutohealForConsumer(
				sm_clientUsername, sm_clientPassword, sm_serverUrl, consumerId,
				true);
*/
		JSONObject jsonConsumer = new JSONObject(CandlepinTasks.getResourceUsingRESTfulAPI(sm_clientUsername, sm_clientPassword, sm_serverUrl, "/consumers/"+consumerId));

		Assert.assertTrue(jsonConsumer.getBoolean("autoheal"),
				"A new system consumer's autoheal attribute value defaults to true.");
	}


	@BeforeClass(groups = "setup")
	public void rememberConfiguredFrequencies() {
		if (clienttasks == null) return;
		configuredHealFrequency = Integer.valueOf(clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "rhsmcertd","autoAttachInterval"));
		configuredCertFrequency = Integer.valueOf(clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "rhsmcertd","certCheckInterval"));
		configuredHostname=clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "server","hostname");
	}
	

	@BeforeGroups(groups = "setup", value = { "BugzillaTests"}, enabled = true)
	@AfterClass(groups = "setup")
	public void restoreConfiguredFrequencies() {
		if (clienttasks == null) return;
		clienttasks.restart_rhsmcertd(configuredCertFrequency, configuredHealFrequency, false,null);
	}
	
	
	@AfterGroups(groups = { "setup" }, value = { "AutoHealWithSLA",/*"VerifyFuturesubscription_Test",*/"VerifySubscriptionOf",
			"VerifySystemCompliantFact","ValidityAfterOversubscribing",/*"certificateStacking",*/
			"UpdateWithNoInstalledProducts",/*"VerifyHealingForFuturesubscription",*/
			"VerifyDistinct","BugzillaTests","VerifyStatusCheck",
			"VerifyStartEndDateOfSubscription","InstalledProductMultipliesAfterSubscription","AutoHealFailForSLA"})
	@AfterClass(groups = "setup")
	public void restoreProductCerts() throws IOException {
// client is already instantiated		client = new SSHCommandRunner(sm_clientHostname, sm_sshUser, sm_sshKeyPrivate,sm_sshkeyPassphrase,null);
		client.runCommandAndWait("mv " + "/root/temp1/*.pem" + " "
				+ clienttasks.productCertDir);
		client.runCommandAndWait("rm -rf " + "/root/temp1");
	}

	@AfterGroups(groups = { "setup" }, value = { "VerifyautosubscribeTest","VerifyStatusForPartialSubscription","certificateStacking",
	"VerifyautosubscribeIgnoresSocketCount_Test","VerifyDistinct","autohealPartial","VerifyFactsListByOverridingValues"})
	@AfterClass(groups = { "setup" })	// called after class for insurance
	public void deleteFactsFileWithOverridingValues() {
		clienttasks.deleteFactsFileWithOverridingValues();
	}
	
	protected void configureTmpProductCertDirWithInstalledProductCerts(List<ProductCert> installedProductCerts) {
		if (rhsmProductCertDir==null) {
			rhsmProductCertDir = clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "rhsm", "productCertDir");
			Assert.assertNotNull(rhsmProductCertDir);
		}
		log.info("Initializing a new product cert directory with the currently installed product certs for this test...");
		RemoteFileTasks.runCommandAndAssert(client,"mkdir -p "+tmpProductCertDir,Integer.valueOf(0));
		RemoteFileTasks.runCommandAndAssert(client,"rm -f "+tmpProductCertDir+"/*.pem",Integer.valueOf(0));
		for (ProductCert productCert : installedProductCerts) {
			RemoteFileTasks.runCommandAndAssert(client,"cp "+productCert.file+" "+tmpProductCertDir,Integer.valueOf(0));
		}
		clienttasks.updateConfFileParameter(clienttasks.rhsmConfFile, "productCertDir", tmpProductCertDir);
	}
	@AfterGroups(groups="setup", value = {"VerifyStatusCheck","certificateStacking"})
	@AfterClass(groups="setup")	// called after class for insurance
	public void restoreRhsmProductCertDir() {
		if (clienttasks==null) return;
		if (rhsmProductCertDir==null) return;	
		log.info("Restoring the originally configured product cert directory...");
		clienttasks.updateConfFileParameter(clienttasks.rhsmConfFile, "productCertDir", rhsmProductCertDir);
	}
	protected String rhsmProductCertDir = null;
	protected final String tmpProductCertDir = "/tmp/sm-tmpProductCertDir";

	// Protected methods
	// ***********************************************************************

	protected String setDate(String hostname, String user, String passphrase,
			String privatekey, String datecmd) throws IOException {
		SSHCommandRunner sshHostnameCommandRunner = new SSHCommandRunner(hostname, user, passphrase, privatekey,
				null);
		return(sshHostnameCommandRunner.runCommandAndWait(datecmd).getStdout());

	}
	protected String getDate(String hostname, String user, String passphrase,
			String privatekey,Boolean flag) throws IOException, ParseException {
		SSHCommandRunner sshHostnameCommandRunner = new SSHCommandRunner(hostname, user, passphrase, privatekey, null);
		if(flag)
			return(sshHostnameCommandRunner.runCommandAndWait("date +\"%F\"").getStdout());
		else
			return(sshHostnameCommandRunner.runCommandAndWait("date --date='yesterday' '+%F'").getStdout());
	}
	
	protected void moveProductCertFiles(String filename) throws IOException {
// client is already instantiated		client = new SSHCommandRunner(sm_clientHostname, sm_sshUser, sm_sshKeyPrivate,sm_sshkeyPassphrase,null);
		if(!(RemoteFileTasks.testExists(client, "/root/temp1/"))){
			client.runCommandAndWait("mkdir " + "/root/temp1/");
		}
		client.runCommandAndWait("mv " + clienttasks.productCertDir + "/"+ filename + " " + "/root/temp1/");
	}
	

	protected String getEntitlementCertFilesWithPermissions() throws IOException {
// client is already instantiated		client = new SSHCommandRunner(sm_clientHostname, sm_sshUser, sm_sshKeyPrivate,sm_sshkeyPassphrase,null);
		String lsFiles = client.runCommandAndWait(
				"ls -l " + clienttasks.entitlementCertDir + "/*-key.pem"
						+ " | cut -d " + "' '" + " -f1,9").getStdout();
		return lsFiles;
	}

	protected SSHCommandResult unsubscribeFromMultipleEntitlementsUsingSerialNumber(
			BigInteger SerialNumOne, BigInteger SerialNumTwo) throws IOException {
/* this task is implemented by SubscriptionManagerTasks
// client is already instantiated		client = new SSHCommandRunner(sm_clientHostname, sm_sshUser, sm_sshKeyPrivate,sm_sshkeyPassphrase,null);
		String command = clienttasks.command;
		command += " unsubscribe";
		if (SerialNumOne != null && SerialNumTwo != null)
			command += " --serial=" + SerialNumOne + " " + "--serial="
					+ SerialNumTwo;

		// run command without asserting results
		return client.runCommandAndWait(command);
*/
		return clienttasks.unsubscribe_(false, Arrays.asList(new BigInteger[]{SerialNumOne,SerialNumTwo}), null, null, null);
	}

	protected SSHCommandResult subscribeInvalidFormat_(Boolean auto,
			String servicelevel, String poolIdOne, String poolIdTwo,
			List<String> productIds, List<String> regtokens, String quantity,
			String email, String locale, String proxy, String proxyuser,
			String proxypassword) throws IOException {
// client is already instantiated		client = new SSHCommandRunner(sm_clientHostname, sm_sshUser, sm_sshKeyPrivate,sm_sshkeyPassphrase,null);
		String command = clienttasks.command;
		command += " subscribe";
		if (poolIdOne != null && poolIdTwo != null)
			command += " --pool=" + poolIdOne + " " + poolIdTwo;

		// run command without asserting results
		return client.runCommandAndWait(command);
	}

	public Boolean RegexInRhsmLog(String logRegex, String input) {
		
		Pattern pattern = Pattern.compile(logRegex, Pattern.MULTILINE);
		Matcher matcher = pattern.matcher(input);
		int count = 0;
		Boolean flag = false;
		while (matcher.find()) {
			count++;
		}
		if (count >= 2) {
			flag = true;
		}
		return flag;

	}

	/**
	 * @return list of objects representing the subscription-manager list
	 *         --avail --ondate
	 */
	public List<SubscriptionPool> getAvailableFutureSubscriptionsOndate(
			String onDateToTest) {
		return SubscriptionPool.parse(clienttasks.list_(null, true, null, null,
				null, onDateToTest, null, null, null, null, null).getStdout());
	}

	protected List<String> listFutureSubscription_OnDate(Boolean available,
			String ondate) {
		List<String> PoolId = new ArrayList<String>();
		SSHCommandResult result = clienttasks.list_(true, true, null, null,
				null, ondate, null, null, null, null, null);
		List<SubscriptionPool> Pool = SubscriptionPool
				.parse(result.getStdout());
		for (SubscriptionPool availablePool : Pool) {
			if (availablePool.multiEntitlement) {
				PoolId.add(availablePool.poolId);
			}
		}

		return PoolId;
	}

	@DataProvider(name="getPackageFromEnabledRepoAndSubscriptionPoolData")
	public Object[][] getPackageFromEnabledRepoAndSubscriptionPoolDataAs2dArray() throws JSONException, Exception {
		return TestNGUtils.convertListOfListsTo2dArray(getPackageFromEnabledRepoAndSubscriptionPoolDataAsListOfLists());
	}
	
	protected List<List<Object>> getPackageFromEnabledRepoAndSubscriptionPoolDataAsListOfLists() throws JSONException, Exception {
		List<List<Object>> ll = new ArrayList<List<Object>>(); if (!isSetupBeforeSuiteComplete) return ll;
		if (clienttasks==null) return ll;
		if (sm_clientUsername==null) return ll;
		if (sm_clientPassword==null) return ll;
		
		// get the currently installed product certs to be used when checking for conditional content tagging
		List<ProductCert> currentProductCerts = clienttasks.getCurrentProductCerts();
		
		// assure we are freshly registered and process all available subscription pools
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, ConsumerType.system, null, null, null, null, null, (String)null, null, null, null, Boolean.TRUE, false, null, null, null);
		for (SubscriptionPool pool : clienttasks.getCurrentlyAvailableSubscriptionPools()) {
			
			File entitlementCertFile = 		clienttasks.subscribeToSubscriptionPool(pool,sm_serverAdminUsername,sm_serverAdminPassword,sm_serverUrl);
			Assert.assertNotNull(entitlementCertFile, "Found the entitlement cert file that was granted after subscribing to pool: "+pool);
			EntitlementCert entitlementCert = clienttasks.getEntitlementCertFromEntitlementCertFile(entitlementCertFile);
			for (ContentNamespace contentNamespace : entitlementCert.contentNamespaces) {
				if (!contentNamespace.type.equalsIgnoreCase("yum")) continue;
				if (contentNamespace.enabled && clienttasks.areAllRequiredTagsInContentNamespaceProvidedByProductCerts(contentNamespace, currentProductCerts)) {
					String repoLabel = contentNamespace.label;
					
					// find an available package that is uniquely provided by repo
					String pkg = clienttasks.findUniqueAvailablePackageFromRepo(repoLabel);
					if (pkg==null) {
						log.warning("Could NOT find a unique available package from repo '"+repoLabel+"' after subscribing to SubscriptionPool: "+pool);
					}

					// String availableGroup, String installedGroup, String repoLabel, SubscriptionPool pool
					ll.add(Arrays.asList(new Object[]{pkg, repoLabel, pool}));
				}
			}
			clienttasks.unsubscribeFromSerialNumber(clienttasks.getSerialNumberFromEntitlementCertFile(entitlementCertFile));

			// minimize the number of dataProvided rows (useful during automated testcase development)
			if (Boolean.valueOf(getProperty("sm.debug.dataProviders.minimize","false"))) break;
		}
		
		return ll;
	}
	
	/**
	 * @param startingMinutesFromNow
	 * @param endingMinutesFromNow
	 * @return poolId to the newly available SubscriptionPool
	 * @throws JSONException
	 * @throws Exception
	 */
	protected String createTestPool(int startingMinutesFromNow, int endingMinutesFromNow) throws JSONException, Exception  {	
/* already implemented; calling CandlepinTasks.createSubscriptionAndRefreshPoolsUsingRESTfulAPI(...)
		
		Calendar startCalendar = new GregorianCalendar();
		startCalendar.add(Calendar.MINUTE, endingMinutesFromNow);
		Date endDate = startCalendar.getTime();
		startCalendar.add(Calendar.MINUTE, startingMinutesFromNow);
		Date startDate = startCalendar.getTime();
		
		// randomly choose a contract number
		Integer contractNumber = Integer.valueOf(getRandInt());
		
		// randomly choose an account number
		Integer accountNumber = Integer.valueOf(getRandInt());
		
		
		String productId = randomAvailableProductId;
		
		providedProducts.add("100000000000002");

		// create the subscription
		String requestBody = CandlepinTasks.createSubscriptionRequestBody(3, startDate, endDate, productId, contractNumber, accountNumber, providedProducts).toString();
		JSONObject jsonSubscription = new JSONObject(CandlepinTasks.postResourceUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, "/owners/" + ownerKey + "/subscriptions", requestBody));
		
		// refresh the pools
		JSONObject jobDetail = CandlepinTasks.refreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, ownerKey);
		jobDetail = CandlepinTasks.waitForJobDetailStateUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword,sm_serverUrl,jobDetail,"FINISHED", 5*1000, 1);
		
		// assemble an activeon parameter set to the start date so we can pass it on to the REST API call to find the created pool
		DateFormat iso8601DateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");	

		// "2012-02-08T00:00:00.000+0000"
		String iso8601FormatedDateString = iso8601DateFormat.format(startDate);
		iso8601FormatedDateString = iso8601FormatedDateString.replaceFirst("(..$)", ":$1");	
		
		
		// "2012-02-08T00:00:00.000+00:00"	// see https://bugzilla.redhat.com/show_bug.cgi?id=720493 // http://books.xmlschemata.org/relaxng/ch19-77049.html requires a colon in the time zone for xsd:dateTime
		String urlEncodedActiveOnDate = java.net.URLEncoder.encode(iso8601FormatedDateString, "UTF-8");	// "2012-02-08T00%3A00%3A00.000%2B00%3A00"	encode the string to escape the colons and plus signs so it can be passed as a parameter on an http call

		// loop through all pools available to owner and find the newly created poolid corresponding to the new subscription id activeon startDate
		String poolId = null;
		String subscriptionId=null;
		JSONArray jsonPools = new JSONArray(CandlepinTasks.getResourceUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword,sm_serverUrl,"/owners/"+ownerKey+"/pools"+"?activeon="+urlEncodedActiveOnDate));	
		for (int i = 0; i < jsonPools.length(); i++) {
			JSONObject jsonPool = (JSONObject) jsonPools.get(i);
			//if (contractNumber.equals(jsonPool.getInt("contractNumber"))) {
			if (jsonPool.getString("subscriptionId").equals(jsonSubscription.getString("id"))) {
				
				poolId = jsonPool.getString("id");
				break;
			}
		}
		Assert.assertNotNull(poolId,"Found newly created pool corresponding to the newly created subscription with id: "+jsonSubscription.getString("id"));
		log.info("The newly created subscription pool with id '"+poolId+"' will start '"+startingMinutesFromNow+"' minutes from now.");
		log.info("The newly created subscription pool with id '"+poolId+"' will expire '"+endingMinutesFromNow+"' minutes from now.");
		return poolId; // return poolId to the newly available SubscriptionPool
*/
		return CandlepinTasks.createSubscriptionAndRefreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, ownerKey, 3, startingMinutesFromNow, endingMinutesFromNow, getRandInt(), getRandInt(), randomAvailableProductId, null).getString("id");
	}
	
	@AfterGroups(groups = { "setup" }, value = { "VerifyrhsmcertdRefreshIdentityCert"})
	public void restoreSystemDate() throws IOException, ParseException {
		String ClientDateAfterExecution=getDate(sm_clientHostname, sm_sshUser, sm_sshKeyPrivate,
					sm_sshkeyPassphrase,true);
		String ServerDateAfterExecution=getDate(sm_serverHostname, sm_sshUser, sm_sshKeyPrivate,
				sm_sshkeyPassphrase,true);
		String ClientDateAfterExeceutionOneDayBefore=getDate(sm_clientHostname, sm_sshUser, sm_sshKeyPrivate,
				sm_sshkeyPassphrase,false);
		String ServerDateAfterExeceutionOneDayBefore=getDate(sm_serverHostname, sm_sshUser, sm_sshKeyPrivate,
				sm_sshkeyPassphrase,false);
		
		if((!(ClientDateAfterExecution.equals(SystemDateOnClient))) &&(!(ClientDateAfterExeceutionOneDayBefore.equals(SystemDateOnClient))) ){

			setDate(sm_clientHostname, sm_sshUser, sm_sshKeyPrivate,
					sm_sshkeyPassphrase, "date -s '15 year ago 9 month ago'");
			log.info("Reverted the date of client"
					+ client.runCommandAndWait("hostname"));
		}
		
		if((!(ServerDateAfterExecution.equals(SystemDateOnServer)))&&((ServerDateAfterExeceutionOneDayBefore.equals(SystemDateOnServer)))){
			setDate(sm_serverHostname, sm_sshUser, sm_sshKeyPrivate,
					sm_sshkeyPassphrase, "date -s '15 year ago 9 month ago'");
			log.info("Reverted the date of candlepin"
					+ client.runCommandAndWait("hostname"));
		}
		clienttasks.restart_rhsmcertd(null, null, false, null);
		SubscriptionManagerCLITestScript.sleep(3 * 60 * 1000);
	}
	
	
	@BeforeGroups(groups = {"setup"}, value = {"VerifyrhsmcertdRefreshIdentityCert"})
	public void rgetSystemDate() throws IOException, ParseException {
	SystemDateOnClient=getDate(sm_clientHostname, sm_sshUser, sm_sshKeyPrivate,
			sm_sshkeyPassphrase,true);
	SystemDateOnServer=getDate(sm_serverHostname, sm_sshUser, sm_sshKeyPrivate,
			sm_sshkeyPassphrase,true);
	}
	
	@AfterGroups(groups = {"setup"}, value = {"VerifyEmptyCertCauseRegistrationFailure_Test","BugzillaTests"})
	public void removeMyEmptyCaCertFile() {
		client.runCommandAndWait("rm -f "+myEmptyCaCertFile);
	}

/* unnecessary
	@AfterGroups(groups = {"setup"}, value = {"BugzillaTests","FactsUpdateForDeletedConsumer","VerifyOneCertPerOneSubscription",
	"VerifyIfStatusDisplaysProductNameMultipleTimes","unsubscribeImportedcert","StackingFutureSubscriptionWithCurrentSubscription","VerifyDistinct","VerifyEntitlementStartDateIsSubStartDate_Test"})
	public void enableAutoheal() {
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		clienttasks.autoheal(null, true, null, null, null, null);
	}
*/
	
	
	@AfterGroups(groups = {"setup"}, value = {"BugzillaTests","DisplayOfRemoteServerExceptionForServer500Error","RHELWorkstationProduct"})
	public void restoreRHSMConfFileValues() {
		clienttasks.unregister(null, null, null);
		List<String[]> listOfSectionNameValues = new ArrayList<String[]>();
		listOfSectionNameValues.add(new String[] { "server","prefix".toLowerCase(), "/candlepin" });
		clienttasks.config(null, null, true, listOfSectionNameValues);
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		}

/* unnecessary; just call it once before the class
	@BeforeGroups(groups={"setup"}, value = {"ExpirationOfEntitlementCerts","RefreshPoolAfterChangeInProvidedProducts","RegisterWithActivationKeyWithExpiredPool","SubscribeToexpiredEntitlement"})
*/
	@BeforeClass(groups={"setup"})
	public void findRandomAvailableProductIdBeforeClass() throws Exception {
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, false, null, null, null);
		List<SubscriptionPool> pools = clienttasks.getCurrentlyAvailableSubscriptionPools();
		SubscriptionPool pool = pools.get(randomGenerator.nextInt(pools.size())); 
		randomAvailableProductId = pool.productId;
	}
	
	@BeforeClass(groups = "setup")
	public void SetServerTasks() throws Exception {
	server = new SSHCommandRunner(sm_serverHostname, sm_sshUser, sm_sshKeyPrivate, sm_sshkeyPassphrase, null);
	servertasks = new rhsm.cli.tasks.CandlepinTasks(server,sm_serverInstallDir,sm_serverImportDir,sm_serverType,sm_serverBranch);
	}

}

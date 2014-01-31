package rhsm.cli.tests;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONException;
import org.json.JSONObject;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterGroups;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import rhsm.base.SubscriptionManagerCLITestScript;
import rhsm.cli.tasks.CandlepinTasks;
import rhsm.data.EntitlementCert;
import rhsm.data.InstalledProduct;
import rhsm.data.ProductCert;
import rhsm.data.SubscriptionPool;

import com.redhat.qe.Assert;
import com.redhat.qe.jul.TestRecords;
import com.redhat.qe.tools.RemoteFileTasks;
import com.redhat.qe.tools.SSHCommandRunner;

/**
 * @author skallesh
 * 
 * 
 */
@Test(groups = { "FlexibleBrandingTests" })
public class FlexibleBrandingTests extends SubscriptionManagerCLITestScript {
	protected  static String BrandType = null;
	protected String ownerKey="";
	protected String randomAvailableProductId;
	protected EntitlementCert expiringCert = null;
	protected String EndingDate;
	protected final String importCertificatesDir = "/tmp/sm-importExpiredCertificatesDir"
			.toLowerCase();
	List<String> providedProducts = new ArrayList<String>();
	protected List<File> entitlementCertFiles = new ArrayList<File>();
	protected final String Brand_Name = "/var/lib/rhsm/branded_name".toLowerCase();


	
		
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(	description="verify if brandname file is created",
			groups={"VerifyBrandFileCreation"},
			enabled=true)
	public void VerifyBrand_NameFileCreation() throws Exception {
		// reset the installed product cert to productCert32060 and reset the brand file
		clienttasks.removeAllCerts(false, false, true);
		RemoteFileTasks.runCommandAndAssert(client,"cp "+productCert32060.file+" "+tmpProductCertDir,Integer.valueOf(0));
		RemoteFileTasks.runCommandAndWait(client,"rm -f "+Brand_Name, TestRecords.action());
		String productname=productCert32060.productName;
		
// DELETEME
//		String productname=null;
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
// DELETEME
//		for (InstalledProduct installed : clienttasks.getCurrentlyInstalledProducts()) {
//			if(!(installed.productId.equals("32060"))){
//				moveProductCertFiles("*"+installed.productId+"*"+ ".pem");
//			}
//		}
//		for (InstalledProduct installed : clienttasks.getCurrentlyInstalledProducts()) {
//			
//				productname=installed.productName;
//			
//		
//		}
		clienttasks.subscribe(true, null,(String)null, null, null, null, null, null, null, null, null);
		String result=client.runCommandAndWait("cat "+Brand_Name).getStdout();
		Assert.assertEquals(result.trim(), productname.trim());
	}
	
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(	description="verify if brandname file is deleted",
			groups={"VerifyBrandFileDeletion"},
			enabled=true)
	public void VerifyBrand_NameFileNotDeletedAfterUnsubscribing() throws Exception {
		// reset the installed product cert to productCert32060 and reset the brand file
		clienttasks.removeAllCerts(false, false, true);
		RemoteFileTasks.runCommandAndAssert(client,"cp "+productCert32060.file+" "+tmpProductCertDir,Integer.valueOf(0));
		RemoteFileTasks.runCommandAndWait(client,"rm -f "+Brand_Name, TestRecords.action());
		String productname=productCert32060.productName;
		
// DELETEME
//		String productname=null;
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
// DELETEME
//		for (InstalledProduct installed : clienttasks.getCurrentlyInstalledProducts()) {
//			if(!(installed.productId.equals("32060"))){
//				moveProductCertFiles("*"+installed.productId+"*"+ ".pem");
//			}
//		}
//		for (InstalledProduct installed : clienttasks.getCurrentlyInstalledProducts()) {
//			productname=installed.productName;
//			
//		}
		clienttasks.subscribe(true, null,(String)null, null, null, null, null, null, null, null, null);
		String result=client.runCommandAndWait("cat "+Brand_Name).getStdout();
		Assert.assertEquals(result.trim(), productname.trim());
		clienttasks.unsubscribe(true,(BigInteger)null, null, null, null);
		Assert.assertEquals(result.trim(), productname.trim());
	}
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(	description="verify if brandname file are replaced",
			groups={"VerifyBrandFileContents"},
			enabled=true)
	public void VerifyBrand_nameContentsAreReplaced() throws Exception {
		// reset the installed product cert to productCert32060 and reset the brand file
		clienttasks.removeAllCerts(false, false, true);
		RemoteFileTasks.runCommandAndAssert(client,"cp "+productCert32060.file+" "+tmpProductCertDir,Integer.valueOf(0));
		RemoteFileTasks.runCommandAndWait(client,"rm -f "+Brand_Name, TestRecords.action());
		String productname=productCert32060.productName;
		
// DELETEME
//		String productname=null;
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
// DELETEME
//		for (InstalledProduct installed : clienttasks.getCurrentlyInstalledProducts()) {
//			if(!(installed.productId.equals("32060"))){
//				moveProductCertFiles("*"+installed.productId+"*"+ ".pem");
//			}
//		}
//		for (InstalledProduct installed : clienttasks.getCurrentlyInstalledProducts()) {
//			productname=installed.productName;
//			
//		}
		clienttasks.subscribe(true, null,(String)null, null, null, null, null, null, null, null, null);
		String result=client.runCommandAndWait("cat "+Brand_Name).getStdout();
		Assert.assertEquals(result.trim(), productname.trim());
		clienttasks.unsubscribe(true,(BigInteger)null, null, null, null);
		Assert.assertEquals(result.trim(), productname.trim());
// DELETEME
//		for (InstalledProduct installed : clienttasks.getCurrentlyInstalledProducts()) {
//			if(!(installed.productId.equals("37060"))){
//				moveProductCertFiles("*"+installed.productId+"*"+ ".pem");
//			}
//		}
//		for (InstalledProduct installed : clienttasks.getCurrentlyInstalledProducts()) {
//			productname=installed.productName;
//			
//		}
		clienttasks.subscribe(true, null,(String)null, null, null, null, null, null, null, null, null);
		result=client.runCommandAndWait("cat "+Brand_Name).getStdout();
		Assert.assertEquals(result.trim(), productname.trim());
		clienttasks.unsubscribe(true,(BigInteger)null, null, null, null);
		Assert.assertEquals(result.trim(), productname.trim());
	
		
	}
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(	description="verify if brandtype value is present in the entitlement cert",
			groups={"VerifyBrand_TypeValue"},
			enabled=true) 
	public void VerifyBrand_TypeValue() throws Exception {
		// reset the installed product cert to productCert32060 and reset the brand file
		clienttasks.removeAllCerts(false, false, true);
		RemoteFileTasks.runCommandAndAssert(client,"cp "+productCert32060.file+" "+tmpProductCertDir,Integer.valueOf(0));
		RemoteFileTasks.runCommandAndWait(client,"rm -f "+Brand_Name, TestRecords.action());
		String productname=productCert32060.productName;
		
// DELETEME
//		String productname=null;
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
// DELETEME
//		for (InstalledProduct installed : clienttasks.getCurrentlyInstalledProducts()) {
//			if(!(installed.productId.equals("32060"))){
//				moveProductCertFiles("*"+installed.productId+"*"+ ".pem");
//			}
//		}
//		for (InstalledProduct installed : clienttasks.getCurrentlyInstalledProducts()) {
//			productname=installed.productName;
//			
//		}
		clienttasks.subscribe(true, null,(String)null, null, null, null, null, null, null, null, null);
		client.runCommandAndWaitWithoutLogging("find "+clienttasks.entitlementCertDir+" -regex \"/.+/[0-9]+.pem\" -exec rct cat-cert {} \\;");
		String certificates = client.getStdout();
		String BrandType=parseInfo(certificates);
			Assert.assertEquals(BrandType, "OS");
		}
	
	
	

	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(	description="verify if brandname file is created for imported cert",
			groups={"CreationWithImport"},
			enabled=true) 
	public void VerifyBrand_NameFileCreationWithImportedCert() throws Exception {
		// reset the installed product cert to productCert32060 and reset the brand file
		clienttasks.removeAllCerts(false, false, true);
		RemoteFileTasks.runCommandAndAssert(client,"cp "+productCert32060.file+" "+tmpProductCertDir,Integer.valueOf(0));
		RemoteFileTasks.runCommandAndWait(client,"rm -f "+Brand_Name, TestRecords.action());
		String productname=productCert32060.productName;
		
// DELETEME
//		String productname=null;
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
// DELETEME
//		for (InstalledProduct installed : clienttasks.getCurrentlyInstalledProducts()) {
//			if(!(installed.productId.equals("32060"))){
//				moveProductCertFiles("*"+installed.productId+"*"+ ".pem");
//			}
//		}
//		for (InstalledProduct installed : clienttasks.getCurrentlyInstalledProducts()) {
//			productname=installed.productName;
//			
//		}
		clienttasks.subscribe(true, null,(String)null, null, null, null, null, null, null, null, null);
		client.runCommandAndWait("mkdir /root/importedcertDir");
		client.runCommandAndWait("cat "+clienttasks.entitlementCertDir+"/* >> /root/importedcertDir/importedcert.pem");
		client.runCommandAndWait("rm -rf "+Brand_Name);
		clienttasks.clean(null, null, null);
		clienttasks.importCertificate("/root/importedcertDir/importedcert.pem");
		String result=client.runCommandAndWait("cat "+Brand_Name).getStdout();
		client.runCommandAndWait("rm -rf /root/importedcertDir/importedcert.pem");
		Assert.assertEquals(result.trim(), productname.trim());
		clienttasks.unsubscribe(true,(BigInteger)null, null, null, null);
		Assert.assertEquals(result.trim(), productname.trim());
	}
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(	description="verify if brandname file is created when rhsmcertd service runs",
			groups={"CreationWithRHSMCERTD","blockedByBug-907638"},
			enabled=true) 
	public void VerifyBrand_NameFileCreationWithRHSMCERTD() throws Exception {
		// reset the installed product cert to productCert32060 and reset the brand file
		clienttasks.removeAllCerts(false, false, true);
		RemoteFileTasks.runCommandAndAssert(client,"cp "+productCert32060.file+" "+tmpProductCertDir,Integer.valueOf(0));
		RemoteFileTasks.runCommandAndWait(client,"rm -f "+Brand_Name, TestRecords.action());
		String productname=productCert32060.productName;
		
// DELETEME
//		String productname=null;
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, true, null, null,
				(String) null, null, null, null, null, null, null, null, null);
// DELETEME
//		client.runCommandAndWait("cp /root/temp1/32060.pem "+clienttasks.productCertDir);
//		for (InstalledProduct installed : clienttasks.getCurrentlyInstalledProducts()) {
//			productname=installed.productName;
//			
//		}
//		clienttasks.restart_rhsmcertd(null, null, false, null);
		clienttasks.run_rhsmcertd_worker(true);
		String result=client.runCommandAndWait("cat "+Brand_Name).getStdout();
		Assert.assertEquals(result.trim(), productname.trim());
		clienttasks.unsubscribe(true,(BigInteger)null, null, null, null);
		result=client.runCommandAndWait("cat "+Brand_Name).getStdout();
		Assert.assertEquals(result.trim(), productname.trim());
	}
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(	description="verify if brandname file is created when two RHEL products are installed",
			groups={"CreationWithTwoRhelproducts"},
			enabled=true) 
	public void VerifyBrand_NameFileCreationWithTwoRhelProductInstalled() throws Exception {
		// reset the installed product certs and reset the brand file
		clienttasks.removeAllCerts(false, false, true);
		RemoteFileTasks.runCommandAndAssert(client,"cp "+productCert32060.file+" "+tmpProductCertDir,Integer.valueOf(0));
		RemoteFileTasks.runCommandAndAssert(client,"cp "+productCert37060.file+" "+tmpProductCertDir,Integer.valueOf(0));
		RemoteFileTasks.runCommandAndWait(client,"rm -f "+Brand_Name, TestRecords.action());
		
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		client.runCommandAndWait("rm -f "+Brand_Name);
// DELETEME
//		for (InstalledProduct installed : clienttasks.getCurrentlyInstalledProducts()) {
//			if((!(installed.productId.equals("32060")))|| (!(installed.productId.equals("37060")))){
//				moveProductCertFiles("*"+installed.productId+"*"+ ".pem");
//			}
//		}
		clienttasks.subscribe(true, null,(String)null, null, null, null, null, null, null, null, null);
		String result=client.runCommandAndWait("cat "+Brand_Name).getStderr();
		String expectedMessage="cat: /var/lib/rhsm/branded_name: No such file or directory";
		Assert.assertEquals(result.trim(), expectedMessage);
	}
	
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(	description="verify if brandname file is created when registering with an activation key",
			groups={"CreationWithTActivationKey"},
			enabled=true) 
	public void VerifyBrand_NameFileCreationWithActivationKeys() throws Exception {
		// reset the installed product cert to productCert32060 and reset the brand file
		clienttasks.removeAllCerts(false, false, true);
		RemoteFileTasks.runCommandAndAssert(client,"cp "+productCert32060.file+" "+tmpProductCertDir,Integer.valueOf(0));
		RemoteFileTasks.runCommandAndWait(client,"rm -f "+Brand_Name, TestRecords.action());
		String productname=productCert32060.productName;
		
// DELETEME
//		String productname=null;
		Integer addQuantity=null;
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, false, null, null, null);
		if(clienttasks.getFactValue("virt.is_guest").equals("True")){
			addQuantity=1;
		}else{
			addQuantity=2;
		}
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
		String poolId=null;
// DELETEME
//		for (InstalledProduct installed : clienttasks.getCurrentlyInstalledProducts()) {
//			if(!(installed.productId.equals("32060"))){
//				moveProductCertFiles("*"+installed.productId+"*"+ ".pem");
//			}
//		}
//
//		clienttasks.unsubscribe(true, (BigInteger) null, null, null, null);
		for (SubscriptionPool availList : clienttasks.getCurrentlyAllAvailableSubscriptionPools()) {
					if(availList.subscriptionName.contains("Instance")){
						poolId=availList.poolId;
			
		}
		}
// DELETEME
//		for (InstalledProduct installed : clienttasks.getCurrentlyInstalledProducts()) {
//			productname=installed.productName;
//		}
		new JSONObject(CandlepinTasks.postResourceUsingRESTfulAPI(sm_clientUsername, sm_clientPassword, sm_serverUrl, "/activation_keys/" + jsonActivationKey.getString("id") + "/pools/" +poolId+(addQuantity==null?"":"?quantity="+addQuantity), null));
		
		clienttasks.register(null, null, sm_clientOrg, null, null, null, null, null, null, null, name, null, null, null, true, null, null, null, null);
		String result=client.runCommandAndWait("cat "+Brand_Name).getStdout();
		Assert.assertEquals(result.trim(), productname.trim());
	}
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(	description="verify if brandname file is created without product cert",
			groups={"verifyFile"},
			enabled=true)
	public void VerifyBrand_NameFileShouldNotBeCreatedWithoutProduct() throws Exception {
		// reset the installed product certs and reset the brand file
		clienttasks.removeAllCerts(false, false, true);
		RemoteFileTasks.runCommandAndWait(client,"rm -f "+Brand_Name, TestRecords.action());
		
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		
// DELETEME
//				moveProductCertFiles("*");
//			
//		client.runCommand("rm -rf "+Brand_Name);
		clienttasks.unsubscribe(true, (BigInteger)null, null, null, null);
		for(SubscriptionPool pool:clienttasks.getCurrentlyAllAvailableSubscriptionPools()){
			if(pool.subscriptionName.contains("Instance")){
				clienttasks.subscribeToSubscriptionPool(pool,sm_serverAdminUsername,sm_serverAdminPassword,sm_serverUrl);
			}
		}
		String result=client.runCommandAndWait("cat "+Brand_Name).getStderr();
		String expectedMessage="cat: /var/lib/rhsm/branded_name: No such file or directory";
		Assert.assertEquals(result.trim(), expectedMessage);
	}
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(	description="verify if Brand Name Creation During Repeated RHSMCERTD Updates",
			groups={"verifyFileduringrhsmupdates","blockedByBug-907638"},
			enabled=true)
	public void VerifyBrand_NameCreatedDuringRepeatedRHSMCERTDUpdates() throws Exception {
		// reset the installed product cert to productCert32060 and reset the brand file
		clienttasks.removeAllCerts(false, false, true);
		RemoteFileTasks.runCommandAndAssert(client,"cp "+productCert32060.file+" "+tmpProductCertDir,Integer.valueOf(0));
		RemoteFileTasks.runCommandAndWait(client,"rm -f "+Brand_Name, TestRecords.action());
		String productname=productCert32060.productName;
		
// DELETEME
//		String productname=null;
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
// DELETEME
//		for (InstalledProduct installed : clienttasks.getCurrentlyInstalledProducts()) {
//			if(!(installed.productId.equals("32060"))){
//				moveProductCertFiles("*"+installed.productId+"*"+ ".pem");
//			}
//		}
//		for (InstalledProduct installed : clienttasks.getCurrentlyInstalledProducts()) {
//			productname=installed.productName;
//			
//		}
		for (int i=1; i <= 2; i++) {	// Repeated RHSMCERTD Updates
			RemoteFileTasks.runCommandAndWait(client,"rm -f "+Brand_Name, TestRecords.action());
			clienttasks.subscribe(true, null,(String)null, null, null, null, null, null, null, null, null);
			String result=client.runCommandAndWait("cat "+Brand_Name).getStdout();
			Assert.assertEquals(result.trim(), productname.trim());
			
			clienttasks.unsubscribe(true,(BigInteger)null, null, null, null);
			result=client.runCommandAndWait("cat "+Brand_Name).getStdout();
			Assert.assertEquals(result.trim(), productname.trim());
		}
	}
	
	
	/**
	 * @author skallesh
	 * @throws Exception
	 * @throws JSONException
	 */
	@Test(	description="verify if brandname file is created cert version is 1",
			groups={"VerifyBrand_NameCreatedWithCertV1","blockedByBug-1011768"},
			enabled=false)//bug has been closed as not a bug
	public void VerifyBrand_NameCreatedWithCertV1() throws Exception {
		// reset the installed product cert to productCert32060 and reset the brand file
		clienttasks.removeAllCerts(false, false, true);
		RemoteFileTasks.runCommandAndAssert(client,"cp "+productCert32060.file+" "+tmpProductCertDir,Integer.valueOf(0));
		RemoteFileTasks.runCommandAndWait(client,"rm -f "+Brand_Name, TestRecords.action());
		String productname=productCert32060.productName;
		
// DELETEME
//		String productname=null;
		clienttasks.register(sm_clientUsername, sm_clientPassword,
				sm_clientOrg, null, null, null, null, null, null, null,
				(String) null, null, null, null, true, null, null, null, null);
		Map<String, String> factsMap = new HashMap<String, String>();
		factsMap.put("system.certificate_version", String.valueOf(1));
		clienttasks.createFactsFileWithOverridingValues(factsMap);
		clienttasks.facts(null, true, null, null, null);
// DELETEME
//		for (InstalledProduct installed : clienttasks.getCurrentlyInstalledProducts()) {
//			if(!(installed.productId.equals("32060"))){
//				moveProductCertFiles("*"+installed.productId+"*"+ ".pem");
//			}
//		}		for (InstalledProduct installed : clienttasks.getCurrentlyInstalledProducts()) {
//			productname=installed.productName;
//			
//		}
		clienttasks.subscribe(true, null,(String)null, null, null, null, null, null, null, null, null);
		String result=client.runCommandAndWait("cat "+Brand_Name).getStdout();
		clienttasks.deleteFactsFileWithOverridingValues();
		Assert.assertEquals(result.trim(), productname.trim());
	}
	
	
// DELETEME
//	protected void moveProductCertFiles(String filename) throws IOException {
//		client = new SSHCommandRunner(sm_clientHostname, sm_sshUser, sm_sshKeyPrivate,sm_sshkeyPassphrase,null);
//		if(!(RemoteFileTasks.testExists(client, "/root/temp1/"))){
//			client.runCommandAndWait("mkdir " + "/root/temp1/");
//		}
//			client.runCommandAndWait("mv " + clienttasks.productCertDir + "/"+ filename + " " + "/root/temp1/");
//	
//		}
	
// DELETEME
//	@AfterGroups(groups = { "setup" }, value = {"FlexibleBranding","verifyFile","verifyFileduringrhsmupdates","CreationWithTActivationKey","CreationWithTwoRhelproducts","CreationWithRHSMCERTD","CreationWithImport","VerifyBrand_TypeValue","VerifyBrandFileContents","VerifyBrandFileCreation","VerifyBrandFileDeletion"})
//			@AfterClass(groups = "setup")
//			public void restoreProductCerts() throws IOException {
//				client = new SSHCommandRunner(sm_clientHostname, sm_sshUser, sm_sshKeyPrivate,sm_sshkeyPassphrase,null);
//				client.runCommandAndWait("mv " + "/root/temp1/*.pem" + " "
//						+ clienttasks.productCertDir);
//				client.runCommandAndWait("rm -rf " + "/root/temp1");
//	}
	
	static public String parseInfo(String rawCertificates) {
		Map<String,String> regexes = new HashMap<String,String>();
		List certData = new ArrayList();
		regexes.put("BrandType",			"Product:(?:(?:\\n.+)+)Brand Type: (.+)");
		// split the rawCertificates process each individual rawCertificate
		String rawCertificateRegex = "\\+-+\\+\\n\\s+Entitlement Certificate\\n\\+-+\\+";
		for (String rawCertificate : rawCertificates.split(rawCertificateRegex)) {
			
			// strip leading and trailing blank lines and skip blank rawCertificates
			rawCertificate = rawCertificate.replaceAll("^\\n*","").replaceAll("\\n*$", "");
			if (rawCertificate.length()==0) continue;
			List<Map<String,String>> certDataList = new ArrayList<Map<String,String>>();
			for(String field : regexes.keySet()){
				Pattern pat = Pattern.compile(regexes.get(field), Pattern.MULTILINE);
			//	BrandTypeValue=regexes.get(field);
				addRegexMatchesToList(pat, rawCertificate, certDataList, field);
			}
			
			// assert that there is only one group of certData found in the list
			List<String> BrandTypeValue = new ArrayList<String>();
			for(Map<String,String> CertMap : certDataList) {
				// normalize newlines from productName when it spans multiple lines (introduced by bug 864177)
				String key = "BrandType",Brandtype = CertMap.get(key);
				if (Brandtype!=null) {
					CertMap.remove(key);
					BrandType = Brandtype.replaceAll("\\s*\\n\\s*", " ");
				}
				//BrandTypeValue.add(BrandType);
			}}
			return BrandType;
			
	}
		
		
	static protected boolean addRegexMatchesToList(Pattern regex, String to_parse, List<Map<String,String>> matchList, String sub_key) {
		boolean foundMatches = false;
		Matcher matcher = regex.matcher(to_parse);
		int currListElem=0;
		while (matcher.find()){
			if (matchList.size() < currListElem + 1) matchList.add(new HashMap<String,String>());
			Map<String,String> matchMap = matchList.get(currListElem);
			matchMap.put(sub_key, matcher.group(1).trim());
			matchList.set(currListElem, matchMap);
			currListElem++;
			foundMatches = true;
		}
        if (!foundMatches) {
        	//log.warning("Could not find regex '"+regex+"' match for field '"+sub_key+"' while parsing: "+to_parse );
        	log.finer("Could not find regex '"+regex+"' match for field '"+sub_key+"' while parsing: "+to_parse );
        }
		return foundMatches;
	}
	
	
	
	
	
	
	
	ProductCert productCert32060 = null;
	ProductCert productCert37060 = null;
	protected String originalProductCertDir		= null;
	protected final String tmpProductCertDir	= "/tmp/sm-fbProductCertDir";

	@BeforeClass(groups="setup")
	public void configProductCertDirBeforeClass() {
		if (clienttasks==null) return;
		
		List<ProductCert> productCerts = clienttasks.getCurrentProductCerts();
		productCert32060 = ProductCert.findFirstInstanceWithMatchingFieldFromList("productId", "32060", productCerts);
		productCert37060 = ProductCert.findFirstInstanceWithMatchingFieldFromList("productId", "37060", productCerts);
		if (productCert32060==null) throw new SkipException("Could not find expected flexible branded product cert id 32060 installed.");
		if (productCert37060==null) throw new SkipException("Could not find expected flexible branded product cert id 37060 installed.");
		
		originalProductCertDir = clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "rhsm", "productCertDir");
		Assert.assertNotNull(originalProductCertDir);
		log.info("Initializing a new product cert directory containing product 32060...");
		RemoteFileTasks.runCommandAndAssert(client,"mkdir -p "+tmpProductCertDir,Integer.valueOf(0));
		RemoteFileTasks.runCommandAndAssert(client,"rm -f "+tmpProductCertDir+"/*.pem",Integer.valueOf(0));
		clienttasks.updateConfFileParameter(clienttasks.rhsmConfFile, "productCertDir", tmpProductCertDir);
	}
	
	@AfterClass(groups="setup")
	public void unconfigProductCertDirAfterClass() {
		if (clienttasks==null) return;
		if (originalProductCertDir==null) return;	
		log.info("Restoring the originally configured product cert directory...");
		clienttasks.updateConfFileParameter(clienttasks.rhsmConfFile, "productCertDir", originalProductCertDir);
	}
	
}


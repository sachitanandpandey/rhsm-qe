package rhsm.cli.tests;

import java.io.File;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterGroups;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.redhat.qe.Assert;
import com.redhat.qe.auto.tcms.ImplementsNitrateTest;
import com.redhat.qe.auto.testng.TestNGUtils;

import rhsm.base.CandlepinType;
import rhsm.base.ConsumerType;
import rhsm.base.SubscriptionManagerCLITestScript;
import rhsm.cli.tasks.CandlepinTasks;
import rhsm.data.ContentNamespace;
import rhsm.data.EntitlementCert;
import rhsm.data.ProductCert;
import rhsm.data.ProductSubscription;
import rhsm.data.SubscriptionPool;
import rhsm.data.YumRepo;
import com.redhat.qe.tools.RemoteFileTasks;
import com.redhat.qe.tools.SSHCommandResult;

/**
 * @author jsefler
 *
 */
@Test(groups={"ContentTests"})
public class ContentTests extends SubscriptionManagerCLITestScript{

	
	// Test methods ***********************************************************************

	@Test(	description="subscription-manager Yum plugin: enable/disable",
			groups={"EnableDisableManageReposAndVerifyContentAvailable_Test","blockedByBug-804227","blockedByBug-871146","blockedByBug-905546","blockedByBug-1017866"},
			//dataProvider="getAvailableSubscriptionPoolsData",	// very thorough, but takes too long to execute and rarely finds more bugs
			dataProvider="getRandomSubsetOfAvailableSubscriptionPoolsData",
			enabled=true)
	@ImplementsNitrateTest(caseId=41696,fromPlan=2479)
	public void EnableDisableManageReposAndVerifyContentAvailable_Test(SubscriptionPool pool) throws JSONException, Exception {

		// get the currently installed product certs to be used when checking for conditional content tagging
		List<ProductCert> currentProductCerts = clienttasks.getCurrentProductCerts();

		log.info("Before beginning this test, we will stop the rhsmcertd so that it does not interfere with this test and make sure we are not subscribed...");
		clienttasks.stop_rhsmcertd();
		clienttasks.unsubscribe_(true,(BigInteger)null,null,null,null);
		
		// Enable rhsm manage_repos configuration
		clienttasks.config(null, null, true, new String[]{"rhsm","manage_repos","1"});
		
		log.info("Subscribe to the pool and start testing that yum repolist reports the expected repo id/labels...");
		File entitlementCertFile = clienttasks.subscribeToSubscriptionPool_(pool);
		Assert.assertNotNull(entitlementCertFile, "Found the entitlement cert file that was granted after subscribing to pool: "+pool);
		EntitlementCert entitlementCert = clienttasks.getEntitlementCertFromEntitlementCertFile(entitlementCertFile);
		
		// 1. Run a 'yum repolist' and get a list of all of the available repositories corresponding to your entitled products
		// 1. Repolist contains repositories corresponding to your entitled products
		ArrayList<String> repolist = clienttasks.getYumRepolist("enabled");
		for (ContentNamespace contentNamespace : entitlementCert.contentNamespaces) {
			if (!contentNamespace.type.equalsIgnoreCase("yum")) continue;
			if (!clienttasks.areAllRequiredTagsInContentNamespaceProvidedByProductCerts(contentNamespace, currentProductCerts)) {
				Assert.assertFalse(repolist.contains(contentNamespace.label),
					"Yum repolist enabled excludes repo id/label '"+contentNamespace.label+"' after having subscribed to Subscription ProductId '"+entitlementCert.orderNamespace.productId+"' with the manage_repos configuration enabled because not all requiredTags ("+contentNamespace.requiredTags+") in the contentNamespace are provided by the currently installed productCerts.");
				continue;
			}
			if (contentNamespace.enabled) {
				Assert.assertTrue(repolist.contains(contentNamespace.label),
					"Yum repolist enabled includes enabled repo id/label '"+contentNamespace.label+"' after having subscribed to Subscription ProductId '"+entitlementCert.orderNamespace.productId+"' with manage_repos configuration enabled.");
			} else {
				Assert.assertFalse(repolist.contains(contentNamespace.label),
					"Yum repolist enabled excludes disabled repo id/label '"+contentNamespace.label+"' after having subscribed to Subscription ProductId '"+entitlementCert.orderNamespace.productId+"' with manage_repos configuration enabled.");
			}
		}
		repolist = clienttasks.getYumRepolist("disabled");
		for (ContentNamespace contentNamespace : entitlementCert.contentNamespaces) {
			if (!contentNamespace.type.equalsIgnoreCase("yum")) continue;
			if (!clienttasks.areAllRequiredTagsInContentNamespaceProvidedByProductCerts(contentNamespace, currentProductCerts)) {
				Assert.assertFalse(repolist.contains(contentNamespace.label),
					"Yum repolist disabled excludes repo id/label '"+contentNamespace.label+"' after having subscribed to Subscription ProductId '"+entitlementCert.orderNamespace.productId+"' with manage_repos configuration enabled because not all requiredTags ("+contentNamespace.requiredTags+") in the contentNamespace are provided by the currently installed productCerts.");
				continue;
			}
			if (contentNamespace.enabled) {
				Assert.assertFalse(repolist.contains(contentNamespace.label),
					"Yum repolist disabled excludes enabled repo id/label '"+contentNamespace.label+"' after having subscribed to Subscription ProductId '"+entitlementCert.orderNamespace.productId+"' with manage_repos configuration enabled.");
			} else {
				Assert.assertTrue(repolist.contains(contentNamespace.label),
					"Yum repolist disabled includes disabled repo id/label '"+contentNamespace.label+"' after having subscribed to Subscription ProductId '"+entitlementCert.orderNamespace.productId+"' with manage_repos configuration enabled.");
			}
		}
		repolist = clienttasks.getYumRepolist("all");
		for (ContentNamespace contentNamespace : entitlementCert.contentNamespaces) {
			if (!contentNamespace.type.equalsIgnoreCase("yum")) continue;
			if (clienttasks.areAllRequiredTagsInContentNamespaceProvidedByProductCerts(contentNamespace, currentProductCerts)) {
				Assert.assertTrue(repolist.contains(contentNamespace.label),
					"Yum repolist all includes repo id/label '"+contentNamespace.label+"' after having subscribed to Subscription ProductId '"+entitlementCert.orderNamespace.productId+"' with manage_repos configuration enabled.");
			} else {
				Assert.assertFalse(repolist.contains(contentNamespace.label),
					"Yum repolist all excludes repo id/label '"+contentNamespace.label+"' after having subscribed to Subscription ProductId '"+entitlementCert.orderNamespace.productId+"' with manage_repos configuration enabled because not all requiredTags ("+contentNamespace.requiredTags+") in the contentNamespace are provided by the currently installed productCerts.");
			}
		}

		log.info("Unsubscribe from the pool and verify that yum repolist no longer reports the expected repo id/labels...");
		clienttasks.unsubscribeFromSerialNumber(entitlementCert.serialNumber);
		
		repolist = clienttasks.getYumRepolist("all");
		for (ContentNamespace contentNamespace : entitlementCert.contentNamespaces) {
			if (!contentNamespace.type.equalsIgnoreCase("yum")) continue;
			Assert.assertFalse(repolist.contains(contentNamespace.label),
				"Yum repolist all excludes repo id/label '"+contentNamespace.label+"' after having unsubscribed from Subscription ProductId '"+entitlementCert.orderNamespace.productId+"' with manage_repos configuration enabled.");
		}
	
		// Disable rhsm manage_repos configuration
		log.info("Now we will disable the rhsm manage_repos configuration with enabled=0..");
		clienttasks.config(null, null, true, new String[]{"rhsm","manage_repos","0"});
		
		log.info("Again let's subscribe to the same pool and verify that yum repolist does NOT report any of the entitled repo id/labels since the manage_repos has been disabled...");
		entitlementCertFile = clienttasks.subscribeToSubscriptionPool_(pool);
		Assert.assertNotNull(entitlementCertFile, "Found the entitlement cert file that was granted after subscribing to pool: "+pool);
		entitlementCert = clienttasks.getEntitlementCertFromEntitlementCertFile(entitlementCertFile);
	
		// 2. Run a 'yum repolist' and get a list of all of the available repositories corresponding to your entitled products
		// 2. Repolist does not contain repositories corresponding to your entitled products
		repolist = clienttasks.getYumRepolist("all");
		for (ContentNamespace contentNamespace : entitlementCert.contentNamespaces) {
			if (!contentNamespace.type.equalsIgnoreCase("yum")) continue;
			Assert.assertFalse(repolist.contains(contentNamespace.label),
				"Yum repolist all excludes repo id/label '"+contentNamespace.label+"' after having subscribed to Subscription ProductId '"+entitlementCert.orderNamespace.productId+"' with the manage_repos configuration disabled.");
		}
		
		log.info("Now we will enable manage_repos and expect the repo list to be updated");
		clienttasks.config(null, null, true, new String[]{"rhsm","manage_repos","1"});
		repolist = clienttasks.getYumRepolist("all");
		for (ContentNamespace contentNamespace : entitlementCert.contentNamespaces) {
			if (!contentNamespace.type.equalsIgnoreCase("yum")) continue;
			if (clienttasks.areAllRequiredTagsInContentNamespaceProvidedByProductCerts(contentNamespace, currentProductCerts)) {
				Assert.assertTrue(repolist.contains(contentNamespace.label),
					"Yum repolist all now includes repo id/label '"+contentNamespace.label+"' after having subscribed to Subscription ProductId '"+entitlementCert.orderNamespace.productId+"' followed by manage_repos configuration enabled.");
			} else {
				Assert.assertFalse(repolist.contains(contentNamespace.label),
					"Yum repolist all still excludes repo id/label '"+contentNamespace.label+"' after having subscribed to Subscription ProductId '"+entitlementCert.orderNamespace.productId+"' followed by manage_repos configuration enabled because not all requiredTags ("+contentNamespace.requiredTags+") in the contentNamespace are provided by the currently installed productCerts.");		
			}
		}
	}
	@AfterGroups(value="EnableDisableManageReposAndVerifyContentAvailable_Test", alwaysRun=true)
	protected void afterEnableDisableManageReposAndVerifyContentAvailable_Test() {
		clienttasks.config(null, null, true, new String[]{"rhsm","manage_repos","1"});
		clienttasks.restart_rhsmcertd(Integer.valueOf(clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, /*"certFrequency" WAS CHANGED BY BUG 882459 TO */ "certCheckInterval")), null, false, null);
	}
	

	
	@Test(	description="subscription-manager content flag : Default content flag should enable",
			groups={"AcceptanceTests","blockedByBug-804227","blockedByBug-871146","blockedByBug-924919","blockedByBug-962520"},
	        enabled=true)
	@ImplementsNitrateTest(caseId=47578,fromPlan=2479)
	public void VerifyYumRepoListsEnabledContent_Test() throws JSONException, Exception{
// Original code from ssalevan
//	    ArrayList<String> repos = this.getYumRepolist();
//	    
//	    for (EntitlementCert cert:clienttasks.getCurrentEntitlementCerts()){
//	    	if(cert.enabled.contains("1"))
//	    		Assert.assertTrue(repos.contains(cert.label),
//	    				"Yum reports enabled content subscribed to repo: " + cert.label);
//	    	else
//	    		Assert.assertFalse(repos.contains(cert.label),
//	    				"Yum reports enabled content subscribed to repo: " + cert.label);
//	    }
		
		List<ProductCert> currentProductCerts = clienttasks.getCurrentProductCerts();
		
		clienttasks.unregister(null, null, null);
	    clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, null, false, null, null, null);
	    if (clienttasks.subscribeToTheCurrentlyAvailableSubscriptionPoolsCollectively().size()<=0)
	    	throw new SkipException("No available subscriptions were found.  Therefore we cannot perform this test.");
	    List<EntitlementCert> entitlementCerts = clienttasks.getCurrentEntitlementCerts();
	    Assert.assertTrue(!entitlementCerts.isEmpty(),"After subscribing to all available subscription pools, there must be some entitlements."); // or maybe we should skip when nothing is consumed 
		ArrayList<String> repolist = clienttasks.getYumRepolist("enabled");
		for (EntitlementCert entitlementCert : entitlementCerts) {
			for (ContentNamespace contentNamespace : entitlementCert.contentNamespaces) {
				if (!contentNamespace.type.equalsIgnoreCase("yum")) continue;
				if (contentNamespace.enabled) {
					if (clienttasks.areAllRequiredTagsInContentNamespaceProvidedByProductCerts(contentNamespace, currentProductCerts)) {
						Assert.assertTrue(repolist.contains(contentNamespace.label),
								"Yum repolist enabled includes enabled repo id/label '"+contentNamespace.label+"' after having subscribed to Subscription ProductId '"+entitlementCert.orderNamespace.productId+"'.");
					} else {
						Assert.assertFalse(repolist.contains(contentNamespace.label),
								"Yum repolist enabled excludes enabled repo id/label '"+contentNamespace.label+"' after having subscribed to Subscription ProductId '"+entitlementCert.orderNamespace.productId+"' because not all requiredTags ("+contentNamespace.requiredTags+") in the contentNamespace are provided by the currently installed productCerts.");
					}
				} else {
					Assert.assertFalse(repolist.contains(contentNamespace.label),
						"Yum repolist enabled excludes disabled repo id/label '"+contentNamespace.label+"' after having subscribed to Subscription ProductId '"+entitlementCert.orderNamespace.productId+"'.");
				}
			}
		}
	}
	
	
	@Test(	description="subscription-manager content flag : gpgcheck value in redhat.repo should be disabled when gpg_url is empty or null",
			groups={"AcceptanceTests","blockedByBug-741293","blockedByBug-805690","blockedByBug-962520"},
	        enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void VerifyGpgCheckValuesInYumRepos_Test() throws JSONException, Exception {
		//	[root@jsefler-r63-server ~]# cat /etc/yum.repos.d/redhat.repo 
		//	#
		//	# Certificate-Based Repositories
		//	# Managed by (rhsm) subscription-manager
		//	#
		//	# If this file is empty and this system is subscribed consider 
		//	# a "yum repolist" to refresh available repos
		//	#
		//
		//	[content-label]
		//	name = content
		//	baseurl = https://cdn.redhat.com/foo/path
		//	enabled = 1
		//	gpgcheck = 1
		//	gpgkey = https://cdn.redhat.com/foo/path/gpg/
		//	sslverify = 1
		//	sslcacert = /etc/rhsm/ca/redhat-uep.pem
		//	sslclientkey = /etc/pki/entitlement/5488047145460852736-key.pem
		//	sslclientcert = /etc/pki/entitlement/5488047145460852736.pem
		//	metadata_expire = 0
		
		//	1.3.6.1.4.1.2312.9.2 (Content Namespace)
		//	1.3.6.1.4.1.2312.9.2.<content_hash> (Red Hat Enterprise Linux (core server))
		//	  1.3.6.1.4.1.2312.9.2.<content_hash>.1 (Yum repo type))
		//	    1.3.6.1.4.1.2312.9.2.<content_hash>.1.1 (Name) : Red Hat Enterprise Linux (core server)
		//	    1.3.6.1.4.1.2312.9.2.<content_hash>.1.2 (Label) : rhel-server
		//	    1.3.6.1.4.1.2312.9.2.<content_hash>.1.5 (Vendor ID): %Red_Hat_Id% or %Red_Hat_Label%
		//	    1.3.6.1.4.1.2312.9.2.<content_hash>.1.6 (Download URL): content/rhel-server/$releasever/$basearch
		//	    1.3.6.1.4.1.2312.9.2.<content_hash>.1.7 (GPG Key URL): file:///etc/pki/rpm-gpg/RPM-GPG-KEY-redhat-release
		//	    1.3.6.1.4.1.2312.9.2.<content_hash>.1.8 (Enabled): 1
		//	    1.3.6.1.4.1.2312.9.2.<content_hash>.1.9 (Metadata Expire Seconds): 604800
		//	    1.3.6.1.4.1.2312.9.2.<content_hash>.1.10 (Required Tags): TAG1,TAG2,TAG3
		
		List<ProductCert> currentProductCerts = clienttasks.getCurrentProductCerts();
		
	    clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, true, false, null, null, null);
	    if (clienttasks.subscribeToTheCurrentlyAvailableSubscriptionPoolsCollectively().size()<=0)
	    	throw new SkipException("No available subscriptions were found.  Therefore we cannot perform this test.");
	    List<EntitlementCert> entitlementCerts = clienttasks.getCurrentEntitlementCerts();
	    Assert.assertTrue(!entitlementCerts.isEmpty(),"After subscribing to all available subscription pools, there must be some entitlements."); // or maybe we should skip when nothing is consumed 

	    
	    ArrayList<String> repolist = clienttasks.getYumRepolist("enabled");
	    List<YumRepo> yumRepos = clienttasks.getCurrentlySubscribedYumRepos();
		for (EntitlementCert entitlementCert : entitlementCerts) {
			for (ContentNamespace contentNamespace : entitlementCert.contentNamespaces) {
				if (!contentNamespace.type.equalsIgnoreCase("yum")) continue;
				if (contentNamespace.enabled) {
					if (!clienttasks.areAllRequiredTagsInContentNamespaceProvidedByProductCerts(contentNamespace, currentProductCerts)) continue;
					YumRepo yumRepo = YumRepo.findFirstInstanceWithMatchingFieldFromList("id"/*label*/, contentNamespace.label, yumRepos);
					Assert.assertNotNull(yumRepo, "Found the yum repo within '"+clienttasks.redhatRepoFile+"' corresponding to the entitled content namespace label '"+contentNamespace.label+"'.");
					
					// case 1: contentNamespace.gpgKeyUrl==null
					if (contentNamespace.gpgKeyUrl==null) {
						Assert.assertFalse(yumRepo.gpgcheck,
								"gpgcheck is False for Yum repo '"+yumRepo.id+"' when corresponding entitlement contentNamespace has a null gpgKeyUrl: contentNamespace: "+contentNamespace);
						Assert.assertNull(yumRepo.gpgkey,
								"gpgkey is not set for Yum repo '"+yumRepo.id+"' when corresponding entitlement contentNamespace has a null gpgKeyUrl: contentNamespace: "+contentNamespace);
					
					// case 2: contentNamespace.gpgKeyUrl==""
					} else if (contentNamespace.gpgKeyUrl.equals("")) {
						Assert.assertFalse(yumRepo.gpgcheck,
								"gpgcheck is False for Yum repo '"+yumRepo.id+"' when corresponding entitlement contentNamespace has an empty gpgKeyUrl: contentNamespace: "+contentNamespace);
						Assert.assertNull(yumRepo.gpgkey,
								"gpgkey is not set for Yum repo '"+yumRepo.id+"' when corresponding entitlement contentNamespace has an empty gpgKeyUrl: contentNamespace: "+contentNamespace);

					// case 3: contentNamespace.gpgKeyUrl.startsWith("http")
					} else if (contentNamespace.gpgKeyUrl.startsWith("http:") || contentNamespace.gpgKeyUrl.startsWith("https:")) {
						Assert.assertTrue(yumRepo.gpgcheck,
								"gpgcheck is True for Yum repo '"+yumRepo.id+"' when corresponding entitlement contentNamespace has a non-null/empty gpgKeyUrl: contentNamespace: "+contentNamespace);
						Assert.assertEquals(yumRepo.gpgkey, contentNamespace.gpgKeyUrl,
								"gpgkey is set for Yum repo '"+yumRepo.id+"' when corresponding entitlement contentNamespace has a non-null/empty gpgKeyUrl: contentNamespace: "+contentNamespace);

					// case 4: contentNamespace.gpgKeyUrl.startsWith("file:")
					} else if (contentNamespace.gpgKeyUrl.startsWith("file:")) {
						Assert.assertTrue(yumRepo.gpgcheck,
								"gpgcheck is True for Yum repo '"+yumRepo.id+"' when corresponding entitlement contentNamespace has a non-null/empty gpgKeyUrl: contentNamespace: "+contentNamespace);
						Assert.assertEquals(yumRepo.gpgkey, contentNamespace.gpgKeyUrl,
								"gpgkey is set for Yum repo '"+yumRepo.id+"' when corresponding entitlement contentNamespace has a non-null/empty gpgKeyUrl: contentNamespace: "+contentNamespace);

					// case 5: contentNamespace.gpgKeyUrl is a relative path   
					} else {
						Assert.assertTrue(yumRepo.gpgcheck,
								"gpgcheck is True for Yum repo '"+yumRepo.id+"' when corresponding entitlement contentNamespace has a non-null/empty gpgKeyUrl: contentNamespace: "+contentNamespace);
						Assert.assertEquals(yumRepo.gpgkey, clienttasks.baseurl+contentNamespace.gpgKeyUrl,
								"gpgkey is set for Yum repo '"+yumRepo.id+"' when corresponding entitlement contentNamespace has a non-null/empty gpgKeyUrl: contentNamespace: "+contentNamespace);
					}
				}
			}
		}
		if (yumRepos.isEmpty()) throw new SkipException("Since no Red Hat repos were found in '"+clienttasks.redhatRepoFile+"', there are no gpgcheck values to verify.");
	}
	
	
	@Test(	description="subscription-manager Yum plugin: ensure content can be downloaded/installed/removed",
			groups={"AcceptanceTests","blockedByBug-701425","blockedByBug-871146","blockedByBug-962520"},
			dataProvider="getPackageFromEnabledRepoAndSubscriptionPoolData",
			enabled=true)
	@ImplementsNitrateTest(caseId=41695,fromPlan=2479)
	public void InstallAndRemovePackageFromEnabledRepoAfterSubscribingToPool_Test(String pkg, String repoLabel, SubscriptionPool pool, String quantity) throws JSONException, Exception {
		if (pkg==null) throw new SkipException("Could NOT find a unique available package from repo '"+repoLabel+"' after subscribing to SubscriptionPool: "+pool);
		
		// subscribe to this pool
		File entitlementCertFile = clienttasks.subscribeToSubscriptionPool_(pool,quantity);
		Assert.assertNotNull(entitlementCertFile, "Found the entitlement cert file that was granted after subscribing to pool: "+pool);

		// install the package and assert that it is successfully installed
		clienttasks.yumInstallPackageFromRepo(pkg, repoLabel, null); //pkgInstalled = true;
		
		// now remove the package
		clienttasks.yumRemovePackage(pkg);
	}
	
	
	@Test(	description="subscription-manager Yum plugin: ensure content can be downloaded/installed/removed after subscribing to a personal subpool",
			groups={"InstallAndRemovePackageAfterSubscribingToPersonalSubPool_Test"},
			dataProvider="getPackageFromEnabledRepoAndPersonalSubscriptionSubPoolData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=) //TODO Find a tcms caseId for
	public void InstallAndRemovePackageAfterSubscribingToPersonalSubPool_Test(String pkg, String repoLabel, SubscriptionPool pool) throws JSONException, Exception {
		InstallAndRemovePackageFromEnabledRepoAfterSubscribingToPool_Test(pkg, repoLabel, pool, null);
	}
	
	
	@Test(	description="subscription-manager Yum plugin: ensure yum groups can be downloaded/installed/removed",
			groups={},
			dataProvider="getYumGroupFromEnabledRepoAndSubscriptionPoolData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=) //TODO Find a tcms caseId for
	public void InstallAndRemoveYumGroupFromEnabledRepoAfterSubscribingToPool_Test(String availableGroup, String installedGroup, String repoLabel, SubscriptionPool pool) throws JSONException, Exception {
		if (availableGroup==null && installedGroup==null) throw new SkipException("No yum groups corresponding to enabled repo '"+repoLabel+" were found after subscribing to pool: "+pool);
				
		// unsubscribe from this pool
		if (pool.equals(lastSubscribedSubscriptionPool)) clienttasks.unsubscribeFromSerialNumber(clienttasks.getSerialNumberFromEntitlementCertFile(lastSubscribedEntitlementCertFile));
		
		// before subscribing to the pool, assert that the yum groupinfo does not exist
		for (String group : new String[]{availableGroup,installedGroup}) {
			if (group!=null) RemoteFileTasks.runCommandAndAssert(client, "yum groupinfo \""+group+"\" --disableplugin=rhnplugin", Integer.valueOf(0), null, "Warning: Group "+group+" does not exist.");
		}

		// subscribe to this pool (and remember it)
		File entitlementCertFile = clienttasks.subscribeToSubscriptionPool_(pool);
		Assert.assertNotNull(entitlementCertFile, "Found the entitlement cert file that was granted after subscribing to pool: "+pool);
		lastSubscribedEntitlementCertFile = entitlementCertFile;
		lastSubscribedSubscriptionPool = pool;
		
		// install and remove availableGroup
		if (availableGroup!=null) {
			clienttasks.yumInstallGroup(availableGroup);
			clienttasks.yumRemoveGroup(availableGroup);
		}
		
		// remove and install installedGroup
		if (installedGroup!=null) {
			clienttasks.yumRemoveGroup(installedGroup);
			clienttasks.yumInstallGroup(installedGroup);
		}

		// TODO: add asserts for the products that get installed or deleted in stdout as a result of yum group install/remove: 
		// deleting: /etc/pki/product/7.pem
		// installing: 7.pem
		// assert the list --installed "status" for the productNamespace name that corresponds to the ContentNamespace from where this repolabel came from.
	}
	protected SubscriptionPool lastSubscribedSubscriptionPool = null;
	protected File lastSubscribedEntitlementCertFile = null;
	
	
	
	@Test(	description="verify redhat.repo file does not contain an excessive (more than two) number of successive blank lines",
			groups={"blockedByBug-737145"},
			enabled=false) // Disabling... this test takes too long to execute.  VerifyRedHatRepoFileIsPurgedOfBlankLinesByYumPlugin_Test effectively provides the same test coverage.
	@Deprecated
	//@ImplementsNitrateTest(caseId=)
	public void VerifyRedHatRepoFileDoesNotContainExcessiveBlankLines_Test_DEPRECATED() {
		
		// successive blank lines in redhat.repo must not exceed N
		int N=2; String regex = "(\\n\\s*){"+(N+2)+",}"; 	//  (\n\s*){4,}
		String redhatRepoFileContents = "";
	    
	    // check for excessive blank lines after a new register
	    clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, true, null, null, null, null);
	    client.runCommandAndWait("yum -q repolist --disableplugin=rhnplugin"); // --disableplugin=rhnplugin helps avoid: up2date_client.up2dateErrors.AbuseError
		redhatRepoFileContents = client.runCommandAndWait("cat "+clienttasks.redhatRepoFile).getStdout();
		Assert.assertContainsNoMatch(redhatRepoFileContents,regex,null,"At most '"+N+"' successive blank are acceptable inside "+clienttasks.redhatRepoFile);

		// check for excessive blank lines after subscribing to each pool
	    for (SubscriptionPool pool : clienttasks.getCurrentlyAvailableSubscriptionPools()) {
    		clienttasks.subscribe_(null,null,pool.poolId,null,null,null,null,null,null,null, null);
    		client.runCommandAndWait("yum -q repolist --disableplugin=rhnplugin"); // --disableplugin=rhnplugin helps avoid: up2date_client.up2dateErrors.AbuseError		
		}
		redhatRepoFileContents = client.runCommandAndWait("cat "+clienttasks.redhatRepoFile).getStdout();
		Assert.assertContainsNoMatch(redhatRepoFileContents,regex,null,"At most '"+N+"' successive blank are acceptable inside "+clienttasks.redhatRepoFile);

		// check for excessive blank lines after unsubscribing from each serial
		List<BigInteger> serialNumbers = new ArrayList<BigInteger>();
	    for (ProductSubscription productSubscription : clienttasks.getCurrentlyConsumedProductSubscriptions()) {
	    	if (serialNumbers.contains(productSubscription.serialNumber)) continue;	// save some time by avoiding redundant unsubscribes
    		clienttasks.unsubscribe_(null, productSubscription.serialNumber, null, null, null);
    		serialNumbers.add(productSubscription.serialNumber);
    		client.runCommandAndWait("yum -q repolist --disableplugin=rhnplugin"); // --disableplugin=rhnplugin helps avoid: up2date_client.up2dateErrors.AbuseError		
		}
		redhatRepoFileContents = client.runCommandAndWait("cat "+clienttasks.redhatRepoFile).getStdout();
		Assert.assertContainsNoMatch(redhatRepoFileContents,regex,null,"At most '"+N+"' successive blank are acceptable inside "+clienttasks.redhatRepoFile);
		
		// assert the comment heading is present
		//Assert.assertContainsMatch(redhatRepoFileContents,"^# Red Hat Repositories$",null,"Comment heading \"Red Hat Repositories\" was found inside "+clienttasks.redhatRepoFile);
		Assert.assertContainsMatch(redhatRepoFileContents,"^# Certificate-Based Repositories$",null,"Comment heading \"Certificate-Based Repositories\" was found inside "+clienttasks.redhatRepoFile);
		Assert.assertContainsMatch(redhatRepoFileContents,"^# Managed by \\(rhsm\\) subscription-manager$",null,"Comment heading \"Managed by (rhsm) subscription-manager\" was found inside "+clienttasks.redhatRepoFile);		
	}
	
	@Test(	description="verify redhat.repo file is purged of successive blank lines by subscription-manager yum plugin",
			groups={"AcceptanceTests","blockedByBug-737145","blockedByBug-838113","blockedByBug-924919","blockedByBug-979492","blockedByBug-1017969"},	/* yum stdout/stderr related bugs 872310 901612 1017354 1017969 */
			enabled=true)
	//@ImplementsNitrateTest(caseId=) //TODO Find a tcms caseId for
	public void VerifyRedHatRepoFileIsPurgedOfBlankLinesByYumPlugin_Test() {
		
		// successive blank lines in redhat.repo must not exceed N
		int N=2; String regex = "(\\n\\s*){"+(N+2)+",}"; 	//  (\n\s*){4,}
		String redhatRepoFileContents = null;
	    
		// adding the following call to login and yum repolist to compensate for change of behavior introduced by Bug 781510 - 'subscription-manager clean' should delete redhat.repo
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null,(List<String>)null, null, null, null, null, null, null, null, null);
		clienttasks.subscribeToTheCurrentlyAllAvailableSubscriptionPoolsCollectively();
		client.runCommandAndWait("yum --quiet repolist --disableplugin=rhnplugin"); // --disableplugin=rhnplugin helps avoid: up2date_client.up2dateErrors.AbuseError			
		redhatRepoFileContents = client.runCommandAndWait("cat "+clienttasks.redhatRepoFile).getStdout();
		Assert.assertTrue(RemoteFileTasks.testExists(client, clienttasks.redhatRepoFile),"Expecting the redhat repo file '"+clienttasks.redhatRepoFile+"' to exist after unregistering.");
		Assert.assertContainsNoMatch(redhatRepoFileContents,regex,null,"At most '"+N+"' successive blank are acceptable inside "+clienttasks.redhatRepoFile);
	
	    // check for excessive blank lines after unregister
	    clienttasks.unregister(null,null,null);
	    client.runCommandAndWait("yum --quiet repolist --disableplugin=rhnplugin"); // --disableplugin=rhnplugin helps avoid: up2date_client.up2dateErrors.AbuseError
	    //Assert.assertTrue(client.getStderr().contains("Unable to read consumer identity"),"Yum repolist should not touch redhat.repo when there is no consumer and state in stderr 'Unable to read consumer identity'.");	// TODO 8/9/2012 FIND OUT WHAT BUG CAUSED THIS CHANGE IN EXPECTED STDERR
	    //Assert.assertEquals(client.getStderr().trim(),"","Stderr from prior command");	// changed by Bug 901612 - Subscription-manager-s yum plugin prints warning to stdout instead of stderr.
//	    String expectedStderr = "This system is not registered to Red Hat Subscription Management. You can use subscription-manager to register.";
//	    Assert.assertTrue(client.getStderr().contains(expectedStderr),"Stderr from prior command should show subscription-manager plugin warning '"+expectedStderr+"'.  See https://bugzilla.redhat.com/show_bug.cgi?id=901612 ");	// 901612 was reverted by 1017354
	    Assert.assertEquals(client.getStdout()+client.getStderr().trim(),"","Stdout+Stderr from prior command should be blank due to --quiet option.");	// Bug 1017969 - subscription manager plugin ignores yum --quiet
	    Assert.assertTrue(RemoteFileTasks.testExists(client, clienttasks.redhatRepoFile),"Expecting the redhat repo file '"+clienttasks.redhatRepoFile+"' to exist after unregistering.");
		redhatRepoFileContents = client.runCommandAndWait("cat "+clienttasks.redhatRepoFile).getStdout();
		Assert.assertContainsNoMatch(redhatRepoFileContents,regex,null,"At most '"+N+"' successive blank are acceptable inside '"+clienttasks.redhatRepoFile+"' after unregistering.");

		log.info("Inserting blank lines into the redhat.repo for testing purposes...");
		client.runCommandAndWait("for i in `seq 1 10`; do echo \"\" >> "+clienttasks.redhatRepoFile+"; done; echo \"# test for bug 737145\" >> "+clienttasks.redhatRepoFile);
		redhatRepoFileContents = client.runCommandAndWait("cat "+clienttasks.redhatRepoFile).getStdout();
		Assert.assertContainsMatch(redhatRepoFileContents,regex,null,"File "+clienttasks.redhatRepoFile+" has been infiltrated with excessive blank lines.");
	    client.runCommandAndWait("yum --quiet repolist --disableplugin=rhnplugin"); // --disableplugin=rhnplugin helps avoid: up2date_client.up2dateErrors.AbuseError
	    //Assert.assertTrue(client.getStderr().contains("Unable to read consumer identity"),"Yum repolist should not touch redhat.repo when there is no consumer and state in stderr 'Unable to read consumer identity'.");	// TODO 8/9/2012 FIND OUT WHAT BUG CAUSED THIS CHANGE IN EXPECTED STDERR
	    //Assert.assertEquals(client.getStderr().trim(),"","Stderr from prior command");	// changed by Bug 901612 - Subscription-manager-s yum plugin prints warning to stdout instead of stderr.
//	    Assert.assertTrue(client.getStderr().contains(expectedStderr),"Stderr from prior command should show subscription-manager plugin warning '"+expectedStderr+"'.  See https://bugzilla.redhat.com/show_bug.cgi?id=901612 ");	// 901612 was reverted by 1017354
	    Assert.assertEquals(client.getStdout()+client.getStderr().trim(),"","Stdout+Stderr from prior command should be blank due to --quiet option.");	// Bug 1017969 - subscription manager plugin ignores yum --quiet
		String redhatRepoFileContents2 = client.runCommandAndWait("cat "+clienttasks.redhatRepoFile).getStdout();
		Assert.assertContainsMatch(redhatRepoFileContents2,regex,null,"File "+clienttasks.redhatRepoFile+" is still infiltrated with excessive blank lines.");
		Assert.assertEquals(redhatRepoFileContents2, redhatRepoFileContents,"File "+clienttasks.redhatRepoFile+" remains unchanged when there is no consumer.");

		// trigger the yum plugin for subscription-manager (after registering again)
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null,(List<String>)null, null, null, null, null, null, null, null, null);
		log.info("Triggering the yum plugin for subscription-manager which will purge the blank lines from redhat.repo...");
	    client.runCommandAndWait("yum --quiet repolist --disableplugin=rhnplugin"); // --disableplugin=rhnplugin helps avoid: up2date_client.up2dateErrors.AbuseError
		redhatRepoFileContents = client.runCommandAndWait("cat "+clienttasks.redhatRepoFile).getStdout();
		Assert.assertContainsNoMatch(redhatRepoFileContents,regex,null,"At most '"+N+"' successive blank are acceptable inside '"+clienttasks.redhatRepoFile+"' after reregistering.");
		
		// assert the comment heading is present
		//Assert.assertContainsMatch(redhatRepoFileContents,"^# Red Hat Repositories$",null,"Comment heading \"Red Hat Repositories\" was found inside "+clienttasks.redhatRepoFile);
		Assert.assertContainsMatch(redhatRepoFileContents,"^# Certificate-Based Repositories$",null,"Comment heading \"Certificate-Based Repositories\" was found inside "+clienttasks.redhatRepoFile);
		Assert.assertContainsMatch(redhatRepoFileContents,"^# Managed by \\(rhsm\\) subscription-manager$",null,"Comment heading \"Managed by (rhsm) subscription-manager\" was found inside "+clienttasks.redhatRepoFile);		
	}
	@Test(	description="verify redhat.repo file is purged of successive blank lines by subscription-manager yum plugin",
			groups={"AcceptanceTests","blockedByBug-737145"},
			enabled=false)	// was valid before bug fix 781510
	@Deprecated
	//@ImplementsNitrateTest(caseId=) //TODO Find a tcms caseId for
	public void VerifyRedHatRepoFileIsPurgedOfBlankLinesByYumPlugin_Test_DEPRECATED() {
		
		// successive blank lines in redhat.repo must not exceed N
		int N=2; String regex = "(\\n\\s*){"+(N+2)+",}"; 	//  (\n\s*){4,}
		String redhatRepoFileContents = "";
	    
	    // check for excessive blank lines after unregister
	    clienttasks.unregister(null,null,null);
	    client.runCommandAndWait("yum -q repolist --disableplugin=rhnplugin"); // --disableplugin=rhnplugin helps avoid: up2date_client.up2dateErrors.AbuseError
		redhatRepoFileContents = client.runCommandAndWait("cat "+clienttasks.redhatRepoFile).getStdout();
		Assert.assertContainsNoMatch(redhatRepoFileContents,regex,null,"At most '"+N+"' successive blank are acceptable inside "+clienttasks.redhatRepoFile);

		log.info("Inserting blank lines into the redhat.repo for testing purposes...");
		client.runCommandAndWait("for i in `seq 1 10`; do echo \"\" >> "+clienttasks.redhatRepoFile+"; done; echo \"# test for bug 737145\" >> "+clienttasks.redhatRepoFile);
		redhatRepoFileContents = client.runCommandAndWait("cat "+clienttasks.redhatRepoFile).getStdout();
		Assert.assertContainsMatch(redhatRepoFileContents,regex,null,"File "+clienttasks.redhatRepoFile+" has been infiltrated with excessive blank lines.");

		// trigger the yum plugin for subscription-manager
		log.info("Triggering the yum plugin for subscription-manager which will purge the blank lines from redhat.repo...");
	    client.runCommandAndWait("yum -q repolist --disableplugin=rhnplugin"); // --disableplugin=rhnplugin helps avoid: up2date_client.up2dateErrors.AbuseError
		redhatRepoFileContents = client.runCommandAndWait("cat "+clienttasks.redhatRepoFile).getStdout();
		Assert.assertContainsNoMatch(redhatRepoFileContents,regex,null,"At most '"+N+"' successive blank are acceptable inside "+clienttasks.redhatRepoFile);
		
		// assert the comment heading is present
		//Assert.assertContainsMatch(redhatRepoFileContents,"^# Red Hat Repositories$",null,"Comment heading \"Red Hat Repositories\" was found inside "+clienttasks.redhatRepoFile);
		Assert.assertContainsMatch(redhatRepoFileContents,"^# Certificate-Based Repositories$",null,"Comment heading \"Certificate-Based Repositories\" was found inside "+clienttasks.redhatRepoFile);
		Assert.assertContainsMatch(redhatRepoFileContents,"^# Managed by \\(rhsm\\) subscription-manager$",null,"Comment heading \"Managed by (rhsm) subscription-manager\" was found inside "+clienttasks.redhatRepoFile);		
	}	
	
	
	
	
	

	@Test(	description="Verify that a 185 content set product subscription is always subscribable",
			groups={"SubscribabilityOfContentSetProduct_Tests","blockedByBug-871146","blockedByBug-905546"},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void VerifySubscribabilityOfSKUProvidingA185ContentSetProduct_Test() {

		Map<String,String> factsMap = new HashMap<String,String>();
		File entitlementCertFile;
		EntitlementCert entitlementCert;
		String systemCertificateVersionFactValue;
		
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, true, false, null, null, null);
		SubscriptionPool pool = SubscriptionPool.findFirstInstanceWithMatchingFieldFromList("productId", subscriptionSKUProvidingA185ContentSetProduct, clienttasks.getCurrentlyAvailableSubscriptionPools());
		Assert.assertNotNull(pool,"Found an available pool to subscribe to productId '"+subscriptionSKUProvidingA185ContentSetProduct+"': "+pool);
		
		// test that it IS subscribable when system.certificate_version: None
		factsMap.put("system.certificate_version", null);
		clienttasks.createFactsFileWithOverridingValues(factsMap);
		clienttasks.facts(null, true, null, null, null);
		Assert.assertEquals(clienttasks.getFactValue("system.certificate_version"), "None", "When the system.certificate_version fact is null, its fact value is reported as 'None'.");
		//entitlementCertFile = clienttasks.subscribeToProductId(skuTo185ContentSetProduct);
		//entitlementCert = clienttasks.getEntitlementCertFromEntitlementCertFile(entitlementCertFile);
		clienttasks.subscribe(null, null, pool.poolId, null, null, null, null, null, null, null, null);
		entitlementCert = clienttasks.getEntitlementCertCorrespondingToSubscribedPool(pool);
		entitlementCertFile = clienttasks.getEntitlementCertFileFromEntitlementCert(entitlementCert);
		Assert.assertEquals(entitlementCert.version,"1.0","When the system.certificate_version fact is null, the version of the entitlement certificate granted by candlepin is '1.0'.");
		Assert.assertEquals(entitlementCert.contentNamespaces.size(), 185, "The number of content sets provided in the version 1.0 entitlement cert parsed using the rct cat-cert tool.");
		entitlementCert = clienttasks.getEntitlementCertFromEntitlementCertFileUsingOpensslX509(entitlementCertFile);
		Assert.assertEquals(entitlementCert.contentNamespaces.size(), 185, "The number of content sets provided in this version '"+entitlementCert.version+"' entitlement cert parsed using the openssl x509 tool.");
		clienttasks.assertEntitlementCertsInYumRepolist(Arrays.asList(entitlementCert), true);
		//clienttasks.unsubscribeFromAllOfTheCurrentlyConsumedProductSubscriptions();
		clienttasks.unsubscribeFromSerialNumber(clienttasks.getSerialNumberFromEntitlementCertFile(entitlementCertFile));
		
		// test that it IS subscribable when system.certificate_version: 1.0
		factsMap.put("system.certificate_version", "1.0");
		clienttasks.createFactsFileWithOverridingValues(factsMap);
		clienttasks.facts(null, true, null, null, null);
		Assert.assertEquals(clienttasks.getFactValue("system.certificate_version"), "1.0", "When the system.certificate_version fact is 1.0, its fact value is reported as '1.0'.");
		//entitlementCertFile = clienttasks.subscribeToProductId(skuTo185ContentSetProduct);
		//entitlementCert = clienttasks.getEntitlementCertFromEntitlementCertFile(entitlementCertFile);
		clienttasks.subscribe(null, null, pool.poolId, null, null, null, null, null, null, null, null);
		entitlementCert = clienttasks.getEntitlementCertCorrespondingToSubscribedPool(pool);
		entitlementCertFile = clienttasks.getEntitlementCertFileFromEntitlementCert(entitlementCert);
		Assert.assertEquals(entitlementCert.version,"1.0","When the system.certificate_version fact is 1.0, the version of the entitlement certificate granted by candlepin is '1.0'.");
		Assert.assertEquals(entitlementCert.contentNamespaces.size(), 185, "The number of content sets provided in the version 1.0 entitlement cert parsed using the rct cat-cert tool.");
		entitlementCert = clienttasks.getEntitlementCertFromEntitlementCertFileUsingOpensslX509(entitlementCertFile);
		Assert.assertEquals(entitlementCert.contentNamespaces.size(), 185, "The number of content sets provided in this version '"+entitlementCert.version+"' entitlement cert parsed using the openssl x509 tool.");
		clienttasks.assertEntitlementCertsInYumRepolist(Arrays.asList(entitlementCert), true);
		//clienttasks.unsubscribeFromAllOfTheCurrentlyConsumedProductSubscriptions();
		clienttasks.unsubscribeFromSerialNumber(clienttasks.getSerialNumberFromEntitlementCertFile(entitlementCertFile));

		// test that it IS subscribable when system.certificate_version is the system's default value (should be >=3.0)
		clienttasks.deleteFactsFileWithOverridingValues();
		systemCertificateVersionFactValue = clienttasks.getFactValue("system.certificate_version");
		Assert.assertTrue(Float.valueOf(systemCertificateVersionFactValue)>=3.0, "The actual default system.certificate_version fact '"+systemCertificateVersionFactValue+"' is >= 3.0.");
		//entitlementCertFile = clienttasks.subscribeToProductId(skuTo185ContentSetProduct);
		//entitlementCert = clienttasks.getEntitlementCertFromEntitlementCertFile(entitlementCertFile);
		clienttasks.subscribe(null, null, pool.poolId, null, null, null, null, null, null, null, null);
		entitlementCert = clienttasks.getEntitlementCertCorrespondingToSubscribedPool(pool);
		entitlementCertFile = clienttasks.getEntitlementCertFileFromEntitlementCert(entitlementCert);
		Assert.assertTrue(Float.valueOf(entitlementCert.version)<=Float.valueOf(systemCertificateVersionFactValue),"The version of the entitlement certificate '"+entitlementCert.version+"' granted by candlepin is less than or equal to the system.certificate_version '"+systemCertificateVersionFactValue+"' which indicates the maximum certificate version this system knows how to handle.");
		Assert.assertEquals(entitlementCert.contentNamespaces.size(), 185, "The number of content sets provided in this version '"+entitlementCert.version+"' entitlement cert parsed using the rct cat-cert tool.");
		clienttasks.assertEntitlementCertsInYumRepolist(Arrays.asList(entitlementCert), true);
		//clienttasks.unsubscribeFromAllOfTheCurrentlyConsumedProductSubscriptions();
		clienttasks.unsubscribeFromSerialNumber(clienttasks.getSerialNumberFromEntitlementCertFile(entitlementCertFile));
	}
	
	
	@Test(	description="Verify that a 186 content set product subscription is subscribable only when system.certificate_version >= 3.0",
			groups={"SubscribabilityOfContentSetProduct_Tests","blockedByBug-871146","blockedByBug-905546"},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void VerifySubscribabilityOfSKUProvidingA186ContentSetProduct_Test() {
		VerifySubscribabilityOfSKUProvidingTooManyContentSets(subscriptionSKUProvidingA186ContentSetProduct,186);
	}
	
	
	@Test(	description="Verify that a subscription providing two 93 content set products is subscribable only when system.certificate_version >= 3.0",
			groups={"SubscribabilityOfContentSetProduct_Tests","blockedByBug-879022","blockedByBug-905546"},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void VerifySubscribabilityOfSKUProvidingTwo93ContentSetProducts_Test() {
		VerifySubscribabilityOfSKUProvidingTooManyContentSets(subscriptionSKUProvidingTwo93ContentSetProducts,93*2);
	}
	
	
	@Test(	description="Verify that yum vars used in a baseurl are listed in a yum repo parameter called ui_repoid_vars",
			groups={"AcceptanceTests","blockedByBug-906554"},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void VerifyYumRepoUiRepoIdVars_Test() throws JSONException, Exception {
		// register
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, true, false, null, null, null);

		// subscribe to available subscriptions
		clienttasks.subscribeToTheCurrentlyAvailableSubscriptionPoolsCollectively();
		
		// process each of the yum repos granted and assert the yum vars contained in the baseurl are listed in the ui_repoid_vars
		boolean UiRepoIdVarsTested=false;
		for (YumRepo yumRepo : clienttasks.getCurrentlySubscribedYumRepos()) {
			log.info("Asserting Yum Repo: "+yumRepo);
			Pattern p = Pattern.compile("\\$\\w+");	// baseurl = https://cdn.redhat.com/content/dist/rhel/server/5/$releasever/$basearch/jbeap/5/os
			Matcher matcher = p.matcher(yumRepo.baseurl);
			
			// first, check if the baseurl has any yum vars
			if (!matcher.find()) {
				// assert that this yumRepo has no ui_repoid_vars configuration
				Assert.assertNull(yumRepo.ui_repoid_vars, "When baseurl '"+yumRepo.baseurl+"' of yumRepo '"+yumRepo.id+"' contains no yum vars, then configuration ui_repoid_vars is not required.");
				continue;
			}
			
			// now make sure all of the yum vars in the baseurl are present in the ui_repoid_vars configuration
			matcher.reset();
			List<String> actualUiRepoidVars = Arrays.asList(yumRepo.ui_repoid_vars.trim().split("\\s+"));
			while (matcher.find()) {
				UiRepoIdVarsTested = true;
				String yumVar = matcher.group();
				
				// assert that the configured ui_repoid_vars contains this yum var
				Assert.assertTrue(actualUiRepoidVars.contains(yumVar.replaceFirst("^\\$","")), "The ui_repoid_vars configuration in repo id '"+yumRepo.id+"' contains yum var '"+yumVar+"' used in its baseurl '"+yumRepo.baseurl+"'.");
			}
			
			// TODO on RHEL7 we should learn how the yum vars get propagated to the ui when calling yum repolist  (dgregor probably knows)
			//	[root@jsefler-7 ~]# yum repolist
			//	Loaded plugins: langpacks, product-id, security, subscription-manager
			//	This system is receiving updates from Red Hat Subscription Management.
			//	repo id                                                                                           repo name                                                                               status
			//	always-enabled-content/6.92Server                                                                 always-enabled-content                                                                  0
			//	awesomeos/6.92Server/x86_64                                                                       awesomeos                                                                               0
			//	repolist: 0
			// Notice the repoid labels are appended with /$releasever and /$releasever/$basearch
		}
		if (!UiRepoIdVarsTested) throw new SkipException("Could not find any YumRepos containing yum vars to assert this test.");
	}
	
	
	@Test(	description="Verify that all content sets granted from a subscription pool that are restricted to specific arches satisfy the current system's arch.",
			groups={"AcceptanceTests","blockedByBug-706187","blockedByBug-975520","VerifyArchRestrictedContentSetsEntitledAfterSubscribeAllSatisfiesTheSystemArch_Test"},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void VerifyArchRestrictedContentSetsEntitledAfterSubscribeAllSatisfiesTheSystemArch_Test() throws JSONException, Exception {
		// get a list of all of the available poolIds that provide arch-based content sets
		List<List<Object>> subscriptionPoolsDataList = getAllAvailableSubscriptionPoolsProvidingArchBasedContentDataAsListOfLists();
		List<String> archBasedSubscriptionPoolIds = new ArrayList<String>();
		for (List<Object> subscriptionPoolsData: subscriptionPoolsDataList) {
			SubscriptionPool pool = (SubscriptionPool)subscriptionPoolsData.get(0);
			archBasedSubscriptionPoolIds.add(pool.poolId);
		}
		if (archBasedSubscriptionPoolIds.isEmpty()) throw new SkipException("No subscriptions were found providing non-empty arch-based content.");
		
		// iterate over several possible system arches
		Map<String, String> factsMap = new HashMap<String, String>();
		for (String systemArch : Arrays.asList(new String[]{"i386","i586","i686","x86_64","ppc","ppc64","ia64","arm","s390","s390x"})) {
			
			// return all current entitlements (Note: system is already registered by getAllAvailableSubscriptionPoolsProvidingArchBasedContentDataAsListOfLists())
			clienttasks.unsubscribe(true, (BigInteger)null, null, null, null);
			
			// fake the system's arch and update the facts
			log.info("Manipulating the system facts into thinking this is a '"+systemArch+"' system...");
			factsMap.put("uname.machine", String.valueOf(systemArch));
			clienttasks.createFactsFileWithOverridingValues(factsMap);
			clienttasks.facts(null, true, null, null, null);
			
			// subscribe to all the arch-based content set pools
			clienttasks.subscribe(false, null, archBasedSubscriptionPoolIds, null, null, null, null, null, null, null, null);
			
			// iterate over all of the granted entitlements
			for (EntitlementCert entitlementCert : clienttasks.getCurrentEntitlementCerts()) {
				for (ContentNamespace contentNamespace : entitlementCert.contentNamespaces) {
					if (contentNamespace.arches==null) Assert.fail("This version of subscription-manager does not appear to parse arch restricted content.  Upgrade to a newer build of subscription-manager.");
					List<String> arches = new ArrayList<String>();
					if (!contentNamespace.arches.trim().isEmpty()) arches.addAll(Arrays.asList(contentNamespace.arches.trim().split(" *, *")));	// Note: the arches field can be a comma separated list of values
					if (arches.contains("x86")) {arches.addAll(Arrays.asList("i386","i486","i586","i686"));}  // Note: x86 is a general arch to cover all 32-bit intel microprocessors 
					Assert.assertTrue(arches.isEmpty() || arches.contains("ALL") || arches.contains("noarch") || arches.contains(systemArch), "Content label '"+contentNamespace.label+"' restricted to arches '"+contentNamespace.arches+"' granted by entitlement cert '"+entitlementCert.orderNamespace.productName+"' matches the system's arch '"+systemArch+"'.");
				}
			}
		}
	}
	@AfterGroups(groups={"setup"}, value={"VerifyArchRestrictedContentSetsEntitledAfterSubscribeAllSatisfiesTheSystemArch_Test"})
	@AfterClass(groups={"setup"})	// insurance; not really needed
	public void deleteFactsFileWithOverridingValues() {
		if (clienttasks!=null) clienttasks.deleteFactsFileWithOverridingValues();
	}
	
	
	@Test(	description="Verify that all content sets granted from a subscription pool satisfy the system arch and subset the provided product's arch",
			groups={"AcceptanceTests","blockedByBug-706187","blockedByBug-975520"},
			dataProvider="getAllAvailableSubscriptionPoolsProvidingArchBasedContentData",//"getAvailableSubscriptionPoolsData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void VerifyContentSetsEntitledFromSubscriptionPoolSatisfyTheSystemArch_Test(SubscriptionPool pool) throws JSONException, Exception {
		List<String> providedProductIds = CandlepinTasks.getPoolProvidedProductIds(sm_clientUsername,sm_clientPassword,sm_serverUrl,pool.poolId);
		if (providedProductIds.isEmpty()) throw new SkipException("This test is not applicable for a pool that provides no products.");
		
		// maintain a list of expected content sets
		Set<ContentNamespace> expectedContentNamespaceSet = new HashSet<ContentNamespace>();
		// maintain a list of unexpected content sets
		Set<ContentNamespace> unexpectedContentNamespaceSet = new HashSet<ContentNamespace>();

		for (String providedProductId : providedProductIds) {
			
			// get the product
			JSONObject jsonProduct = new JSONObject(CandlepinTasks.getResourceUsingRESTfulAPI(sm_clientUsername,sm_clientPassword,sm_serverUrl,"/products/"+providedProductId));	
			
			// get the product supported arches
			JSONArray jsonProductAttributes = jsonProduct.getJSONArray("attributes");
			List<String> productSupportedArches = new ArrayList<String>();
			for (int j = 0; j < jsonProductAttributes.length(); j++) {
				JSONObject jsonProductAttribute = (JSONObject) jsonProductAttributes.get(j);
				String attributeName = jsonProductAttribute.getString("name");
				String attributeValue = jsonProductAttribute.isNull("value")? null:jsonProductAttribute.getString("value");
				if (attributeName.equals("arch")) {
					productSupportedArches.addAll(Arrays.asList(attributeValue.trim().split("\\s*,\\s*")));	// Note: the arch attribute can be a comma separated list of values
					if (productSupportedArches.contains("x86")) {productSupportedArches.addAll(Arrays.asList("i386","i486","i586","i686"));}  // Note: x86 is a general arch to cover all 32-bit intel microprocessors 
				}
			}
			
			// get the provided product contents
			JSONArray jsonProductContents = jsonProduct.getJSONArray("productContent");
			for (int j = 0; j < jsonProductContents.length(); j++) {
				JSONObject jsonProductContent = (JSONObject) jsonProductContents.get(j);
				JSONObject jsonContent = jsonProductContent.getJSONObject("content");
				Map<String,String> certData = new HashMap<String,String>();
				//certData.put("", jsonContent.getString("id"));
				if (jsonContent.has("type") && !jsonContent.isNull("type")) certData.put("type",jsonContent.getString("type"));
				if (jsonContent.has("label") && !jsonContent.isNull("label")) certData.put("label",jsonContent.getString("label"));
				if (jsonContent.has("name") && !jsonContent.isNull("name")) certData.put("name",jsonContent.getString("name"));
				if (jsonContent.has("vendorId") && !jsonContent.isNull("vendorId")) certData.put("vendorId",jsonContent.getString("vendor"));
				if (jsonContent.has("downloadUrl") && !jsonContent.isNull("downloadUrl")) certData.put("downloadUrl",jsonContent.getString("contentUrl"));
				if (jsonContent.has("requiredTags") && !jsonContent.isNull("requiredTags")) certData.put("requiredTags",jsonContent.getString("requiredTags"));
				//certData.put("", jsonContent.getString("releaseVer"));
				if (jsonContent.has("gpgKeyUrl") && !jsonContent.isNull("gpgKeyUrl")) certData.put("gpgKeyUrl",jsonContent.getString("gpgUrl"));
				if (jsonContent.has("metadataExpire") && !jsonContent.isNull("metadataExpire")) certData.put("metadataExpire",String.valueOf(jsonContent.getInt("metadataExpire")));
				//certData.put("", jsonContent.getString("modifiedProductIds"));
				if (jsonContent.has("arches") && !jsonContent.isNull("arches")) certData.put("arches",jsonContent.getString("arches"));
				ContentNamespace contentNamespace = new ContentNamespace(certData);

//				// get modifiedProductIds for each of the productContents
//				JSONArray jsonModifiedProductIds = jsonContent.getJSONArray("modifiedProductIds");
//				for (int k = 0; k < jsonModifiedProductIds.length(); k++) {
//					String modifiedProductId = (String) jsonModifiedProductIds.get(k);
//				}
				
				// get this content supported arches
				Set<String> contentSupportedArches = new HashSet<String>();
				String jsonContentArches = null;
				if (jsonContent.has("arches") && !jsonContent.isNull("arches") && !jsonContent.getString("arches").isEmpty()) {
					jsonContentArches = jsonContent.getString("arches");
					contentSupportedArches.addAll(Arrays.asList(jsonContentArches.split("\\s*,\\s*")));
					if (contentSupportedArches.contains("x86")) contentSupportedArches.addAll(Arrays.asList("i386","i486","i586","i686"));  // Note: x86 is a general arch to cover all 32-bit intel microprocessors 
					/* NOPE: THIS CONCEPT IS NOT WHAT RELEASE ENGINEERING WANTS.  DO NOT TOLERATE THIS BEHAVIOR; SEE Bug 975520 - content availability based on arches is currently too tolerant
					if (contentSupportedArches.contains("i386")) contentSupportedArches.add("x86_64");  // Note: all i386 packages are capable of running on an x86_64 system
					if (contentSupportedArches.contains("i486")) contentSupportedArches.add("x86_64");  // Note: all i486 packages are capable of running on an x86_64 system
					if (contentSupportedArches.contains("i586")) contentSupportedArches.add("x86_64");  // Note: all i586 packages are capable of running on an x86_64 system
					if (contentSupportedArches.contains("i686")) contentSupportedArches.add("x86_64");  // Note: all i686 packages are capable of running on an x86_64 system
					*/
					
					// when arches have been defined on the content set, then add contentNamespace to the expectedContentNamespaces, but only if
					// it contains an arch that matches the system
					if (contentSupportedArches.contains("ALL") || contentSupportedArches.contains("noarch") || contentSupportedArches.contains(clienttasks.arch)) {
						expectedContentNamespaceSet.add(contentNamespace);
					} else {
						unexpectedContentNamespaceSet.add(contentNamespace);
					}
					
				} else {
					// when no arches have been defined on the content set, then add it to the expectedContentLabels, but only if
					// it's providedProduct also matches the system  (we are effectively inheriting the arches defined by the product to which this content was added)
					
					// TODO: NOT SURE HOW TOLERANT WE WANT TO BE FOR CONTENT SETS THAT INHERIT FROM THEIR PRODUCTS
					if (productSupportedArches.contains("ALL") || productSupportedArches.contains(clienttasks.arch)) {
						expectedContentNamespaceSet.add(contentNamespace);
					} else {
						unexpectedContentNamespaceSet.add(contentNamespace);
					}
				}
			}
		}
		
		// conflict siuation: if a subscription provides more than one product that both provide the same content but whose product's arch differs, then it is possible to have the content in both expectedContentLabels and unexpectedContentLabels; expectedContentLabels wins!
		for (ContentNamespace expectedContentNamespace : expectedContentNamespaceSet) {
			List<ContentNamespace> unexpectedContentNamespaceList = new ArrayList<ContentNamespace>();
			unexpectedContentNamespaceList.addAll(unexpectedContentNamespaceSet);
			ContentNamespace unexpectedContentNamespace = ContentNamespace.findFirstInstanceWithMatchingFieldFromList("label", expectedContentNamespace.label, unexpectedContentNamespaceList);
			if (unexpectedContentNamespace!=null) {
				log.warning("Based on multiple products '"+providedProductIds+"' from subscription '"+pool.subscriptionName+"' with conflicting arches, content label '"+expectedContentNamespace.label+"' defined for arches '"+expectedContentNamespace.arches+"' will be provided."); 
				unexpectedContentNamespaceSet.remove(unexpectedContentNamespace);
			}
		}
		
		if (expectedContentNamespaceSet.isEmpty() && unexpectedContentNamespaceSet.isEmpty()) throw new SkipException("This test is not applicable for a pool whose provided products have no content sets.");
		
		clienttasks.unsubscribe(true,(BigInteger)null,null,null,null);
		EntitlementCert entitlementCert = clienttasks.getEntitlementCertFromEntitlementCertFile(clienttasks.subscribeToSubscriptionPool(pool,sm_serverAdminUsername,sm_serverAdminPassword,sm_serverUrl));
		
		// entitlement asserts
		List<String> actualEntitledContentLabels = new ArrayList<String>();
		for (ContentNamespace contentNamespace : entitlementCert.contentNamespaces) actualEntitledContentLabels.add(contentNamespace.label);
		for (ContentNamespace contentNamespace : expectedContentNamespaceSet) {
			Assert.assertTrue(actualEntitledContentLabels.contains(contentNamespace.label), "As expected, contentNamespace label '"+contentNamespace.label+"' defined for arches '"+contentNamespace.arches+"' requiredTags '"+contentNamespace.requiredTags+"' is included in the entitlement after subscribing to '"+pool.subscriptionName+"' on a '"+clienttasks.arch+"' system.");
		}
		for (ContentNamespace contentNamespace : unexpectedContentNamespaceSet) {
			Assert.assertTrue(!actualEntitledContentLabels.contains(contentNamespace.label), "As expected, contentNamespace label '"+contentNamespace.label+"' defined for arches '"+contentNamespace.arches+"' requiredTags '"+contentNamespace.requiredTags+"' is NOT included in the entitlement after subscribing to '"+pool.subscriptionName+"' on a '"+clienttasks.arch+"' system.");
		}
				
		// adjust the expectedContentNamespaces for requiredTags that are not provided by the installed productCerts' providedTags before checking the YumRepos
		List<ProductCert> installedProductCerts = clienttasks.getCurrentProductCerts();
		for (ContentNamespace contentNamespace : new HashSet<ContentNamespace>(expectedContentNamespaceSet)) {
			if (!clienttasks.areAllRequiredTagsProvidedByProductCerts(contentNamespace.requiredTags, installedProductCerts)) {
				log.warning("Entitled contentNamespace label '"+contentNamespace.label+"' defined for arches '"+contentNamespace.arches+"' has requiredTags '"+contentNamespace.requiredTags+"' that are NOT provided by the currently installed product certs.  This expected contentNamespace will be moved to the unexpected list when asserting the YumRepos next.");
				unexpectedContentNamespaceSet.add(contentNamespace);
				expectedContentNamespaceSet.remove(contentNamespace);
			}
		}
		
		// YumRepo asserts
		List<String> actualYumRepoLabels = new ArrayList<String>();
		for (YumRepo yumRepo : clienttasks.getCurrentlySubscribedYumRepos()) actualYumRepoLabels.add(yumRepo.id);
		for (ContentNamespace contentNamespace : expectedContentNamespaceSet) {
			Assert.assertTrue(actualYumRepoLabels.contains(contentNamespace.label), "As expected, yum repo label '"+contentNamespace.label+"' defined for arches '"+contentNamespace.arches+"' requiredTags '"+contentNamespace.requiredTags+"' is included in "+clienttasks.redhatRepoFile+" after subscribing to '"+pool.subscriptionName+"' on a '"+clienttasks.arch+"' system.");
		}
		for (ContentNamespace contentNamespace : unexpectedContentNamespaceSet) {
			Assert.assertTrue(!actualYumRepoLabels.contains(contentNamespace.label), "As expected, yum repo label '"+contentNamespace.label+"' defined for arches '"+contentNamespace.arches+"' requiredTags '"+contentNamespace.requiredTags+"' is NOT included in in "+clienttasks.redhatRepoFile+" after subscribing to '"+pool.subscriptionName+"' on a '"+clienttasks.arch+"' system.");
		}
	}
	
	
	@Test(	description="Verify that all there is at least one available RHEL subscription and that yum content is available for the installed RHEL product cert",
			groups={"AcceptanceTests"},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void VerifyRhelSubscriptionContentIsAvailable_Test() throws JSONException, Exception {
		
		// get the currently installed RHEL product cert
		ProductCert rhelProductCert = clienttasks.getCurrentRhelProductCert();
		Assert.assertNotNull(rhelProductCert, "Expecting a RHEL Product Cert to be installed.");
		log.info("RHEL product cert installed: "+rhelProductCert);
		
		// register and make sure autoheal is off
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, true, null, null, null, null);
		clienttasks.autoheal(null, null, true, null, null, null);
		
		// verify that NO yum content is available since no entitlements have been granted
		Assert.assertEquals(clienttasks.getYumRepolistPackageCount("enabled"),new Integer(0),"Expecting no enabled repo content available because no RHEL subscription has been attached.");
		
		// loop through the available pools looking for those that provide content for this rhelProductCert
		boolean rhelYumContentIsAvailable = true;
		boolean rhelSubscriptionIsAvailable = false;
		for (SubscriptionPool pool : clienttasks.getCurrentlyAvailableSubscriptionPools()) {
			if (CandlepinTasks.getPoolProvidedProductIds(sm_clientUsername,sm_clientPassword,sm_serverUrl,pool.poolId).contains(rhelProductCert.productId)) {
				
				// subscribe
				EntitlementCert rhelEntitlementCert = clienttasks.getEntitlementCertFromEntitlementCertFile(clienttasks.subscribeToSubscriptionPool(pool, sm_clientUsername, sm_clientPassword, sm_serverUrl));
				
				// verify that rhel yum content is available
				Integer yumRepolistPackageCount = clienttasks.getYumRepolistPackageCount("enabled");
				if (yumRepolistPackageCount>0) {
					Assert.assertTrue(yumRepolistPackageCount>0,"Expecting many available packages (actual='"+yumRepolistPackageCount+"') of enabled repo content because RHEL subscription '"+pool.subscriptionName+"' SKU '"+pool.productId+"' was just attached.");
				} else {
					log.warning("No enabled yum repo content packages are available after attaching RHEL subscription '"+pool.subscriptionName+"'.");
					rhelYumContentIsAvailable = false;
				}
				
				// unsubscribe
				clienttasks.unsubscribe(null, rhelEntitlementCert.serialNumber, null, null, null);
				
				rhelSubscriptionIsAvailable = true;
			}
		}
		if (!rhelSubscriptionIsAvailable && sm_serverType.equals(CandlepinType.standalone)) throw new SkipException("Skipping this test against a standalone Candlepin server that has no RHEL subscriptions available.");
		Assert.assertTrue(rhelSubscriptionIsAvailable,"Successfully subscribed to at least one available RHEL subscription that provided for our installed RHEL product cert: "+rhelProductCert);
		Assert.assertTrue(rhelYumContentIsAvailable,"All of the RHEL subscriptions subscribed provided at least one enabled yum content package applicable for our installed RHEL product cert: "+rhelProductCert+" (See WARNINGS logged above for failed subscriptions)");
	}
	
	
	
	
	
	
	
	
	// Candidates for an automated Test:
	// TODO Bug 689031 - nss needs to be able to use pem files interchangeably in a single process https://github.com/RedHatQE/rhsm-qe/issues/127
	// TODO Bug 701425 - NSS issues with more than one susbcription https://github.com/RedHatQE/rhsm-qe/issues/128
	// TODO Bug 687970 - Currently no way to delete a content source from a product https://github.com/RedHatQE/rhsm-qe/issues/129
	// how to create content (see Bug 687970): [jsefler@jsefler ~]$ curl -u admin:admin -k --request POST https://jsefler-onprem-62candlepin.usersys.redhat.com:8443/candlepin/content --header "Content-type: application/json" -d '{"contentUrl":"/foo/path","label":"foolabel","type":"yum","gpgUrl":"/foo/path/gpg","id":"fooid","name":"fooname","vendor":"Foo Vendor"}' | python -m json.tool
	// how to delete content (see Bug 687970): [jsefler@jsefler ~]$ curl -u admin:admin -k --request DELETE https://jsefler-onprem-62candlepin.usersys.redhat.com:8443/candlepin/content/fooid
	// how to get content    (see Bug 687970): [jsefler@jsefler ~]$ curl -u admin:admin -k --request GET https://jsefler-onprem-62candlepin.usersys.redhat.com:8443/candlepin/content/fooid
	// how to associate content with product   (see Bug 687970): [jsefler@jsefler ~]$ curl -u admin:admin -k --request POST https://jsefler-onprem-62candlepin.usersys.redhat.com:8443/candlepin/product/productid/content/fooid&enabled=false
	
	
	
	
	
	
	
	
	
	
	// Configuration Methods ***********************************************************************
	
	@BeforeClass(groups={"setup"})
	public void removeYumBeakerRepos() {
		client.runCommandAndWait("mkdir -p /tmp/beaker.repos; mv -f /etc/yum.repos.d/beaker*.repo /tmp/beaker.repos");
	}
	
	@BeforeClass(groups={"setup"})
	public void setManageRepos() {
		clienttasks.config(null, null, true, new String[]{"rhsm","manage_repos","1"});
	}
	
	@AfterClass(groups={"setup"})
	public void restoreYumBeakerRepos() {
		client.runCommandAndWait("mv -f /tmp/beaker.repos/beaker*.repo /etc/yum.repos.d");
	}
	
	@AfterClass(groups={"setup"})
	@AfterGroups(groups={"setup"},value="InstallAndRemovePackageAfterSubscribingToPersonalSubPool_Test", alwaysRun=true)
	public void unregisterAfterGroupsInstallAndRemovePackageAfterSubscribingToPersonalSubPool_Test() {
		// first, unregister client1 since it is a personal subpool consumer
		client1tasks.unregister_(null,null,null);
		// second, unregister client2 since it is a personal consumer
		if (client2tasks!=null) {
			client2tasks.register_(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, personalConsumerId, null, null, null, (String)null, null, null, null, Boolean.TRUE, null, null, null, null);
			client2tasks.unsubscribe_(true,(BigInteger)null, null, null, null);
			client2tasks.unregister_(null,null,null);
		}
	}

	@AfterGroups(groups={"setup"},value="SubscribabilityOfContentSetProduct_Tests")
	public void deleteFactsFileWithOverridingValuesAfterGroups() {
		if (clienttasks==null) return;
		clienttasks.deleteFactsFileWithOverridingValues();
	}
	
	@BeforeClass(groups="setup")
	public void createSubscriptionsWithVariationsOnContentSizes() throws JSONException, Exception {
		String marketingProductName,engineeringProductName,marketingProductId,engineeringProductId;
		Map<String,String> attributes = new HashMap<String,String>();
		if (server==null) {
			log.warning("Skipping createSubscriptionsWithVariationsOnContentSizes() when server is null.");
			return;	
		}
		
		// recreate a lot of content sets
		String contentIdStringFormat = "777%04d";
		for (int i = 1; i <= 200; i++) {
			String contentName = "Content Name "+i;
			String contentId = String.format(contentIdStringFormat,i);	// must be numeric (and unique)
			String contentLabel = "content-label-"+i;
			// include some "yum var"iability for testing bug 906554
			String yumVarPath="";
			if (i%5 == 0) yumVarPath+="$basearch/";
			if (i%10 == 0) yumVarPath+="$releasever/";
			if (i%15 == 0) yumVarPath+="$arch/";
			if (i%20 == 0) yumVarPath+="$uuid/";
			
			CandlepinTasks.deleteResourceUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, "/content/"+contentId);
			CandlepinTasks.createContentUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, contentName, contentId, contentLabel, "yum", "Red Hat QE, Inc.", "/content/path/to/"+yumVarPath+contentLabel, "/gpg/path/to/"+yumVarPath+contentLabel, "3600", null, null, null);
		}
		
		// recreate a lot of arch-based content sets
		String archBasedContentIdStringFormat = "888%04d";
		for (int i = 1; i <= 200; i++) {
			String contentName = "Content Set "+i;
			String contentId = String.format(archBasedContentIdStringFormat,i);	// must be numeric (and unique)
			String contentLabel = "content-set-"+i;
			// include some "yum var"iability for testing bug 906554
			String yumVarPath="";
			if (i%5 == 0) yumVarPath+="$basearch/";
			if (i%10 == 0) yumVarPath+="$releasever/";
			if (i%15 == 0) yumVarPath+="$arch/";
			if (i%20 == 0) yumVarPath+="$uuid/";
			// include some required tags for the fun of it
			String requiredTags=null;
			if (i%4 == 0) requiredTags="rhel-5";
			if (i%8 == 0) requiredTags="rhel-6";
			if (i%16 == 0) requiredTags="rhel-7";
			if (i%44 == 0) requiredTags="";
			if (i%92 == 0) requiredTags="None";	// this is a legitimate and real tag and is interpreted as a real tag - None has no special meaning here
			// include some arches for the fun of it
			//	candlepin=# select * from cp_arch ;
			//	 id | label  | created | updated
			//	----+--------+---------+---------
			//	 0  | ALL    |         |
			//	 1  | x86_64 |         |
			//	 2  | i386   |         |
			//	 3  | i486   |         |
			//	 4  | i586   |         |
			//	 5  | i686   |         |
			//	 6  | ppc    |         |
			//	 7  | ppc64  |         |
			//	 8  | ia64   |         |
			//	 9  | arm    |         |
			//	 10 | s390   |         |
			//	 11 | s390x  |         |
			// ORIGINAL CANDLEPIN DESIGN REQUIRED THAT I SPECIFY A LIST OF ARCH OBJECT IDS AS FOLLOWS: "arches":[{"id":"1"},{"id":"2"}]}
			//[jsefler@jseflerT510 ~]$ curl --stderr /dev/null --insecure --user admin:admin -/path/to/content-label-199-arch","contentUrl":"/content/path/to/content-label-199-arch","vendor":"Red Hat QE, Inc.","name":"Content Name 199 arch","label":"content-label-199-arch","type":"yum","metadataExpire":"3600","arches":[{"id":"1"},{"id":"2"}]}' --header 'accept: application/json' --header 'content-type: application/json' https://jsefler-f14-candlepin.usersys.redhat.com:8443/candlepin/content
			//{"created":"2013-05-31T16:47:03.342+0000","updated":"2013-05-31T16:47:03.342+0000","id":"27770199","type":"yum","label":"content-label-199-arch","name":"Content Name 199 arch","vendor":"Red Hat QE, Inc.","contentUrl":"/content/path/to/content-label-199-arch","requiredTags":null,"releaseVer":null,"gpgUrl":"/gpg/path/to/content-label-199-arch","metadataExpire":3600,"modifiedProductIds":[],"arches":[{"id":"2","label":"i386"},{"id":"1","label":"x86_64"}]}
			//[jsefler@jseflerT510 ~]$ 
			// CURRENT DESIGN IS SIMPLIFIED TO A COMMA SEPARATED STRING
			String arches=null;
			if (i%2 == 0) arches="ppc64,arm,s390x,x86_64";
			if (i%6 == 0) arches="ia64,x86,ppc,s390";
			if (i%9 == 0) arches="ALL";
			if (i%18 == 0) arches="noarch";
			if (i%27 == 0) arches="ALL,noarch";
			if (i%16 == 0) arches="i386,i686";
			if (i%44 == 0) arches="";
			if (i%92 == 0) arches="None";	// this is a legitimate and real tag and is interpreted as a real arch - None has no special meaning here
			
			CandlepinTasks.deleteResourceUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, "/content/"+contentId);
			CandlepinTasks.createContentUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, contentName, contentId, contentLabel, "yum", "Red Hat QE, Inc.", "/content/path/to/"+yumVarPath+contentLabel, "/gpg/path/to/"+yumVarPath+contentLabel, "3600", requiredTags, arches, null);
		}
	
		// recreate Subscription SKUs: subscriptionSKUProvidingA185ContentSetProduct, subscriptionSKUProvidingA186ContentSetProduct
		for (int N : new ArrayList<Integer>(Arrays.asList(185,186))) {	// 185 is the maximum number of content sets tolerated in a system.certificate_version < 3.0
			marketingProductName = String.format("Subscription providing a %s ContentSet Product",N);
			marketingProductId = "mktProductId-"+N;
			engineeringProductName = String.format("%s ContentSet Product",N);
			engineeringProductId = String.valueOf(N);	// must be numeric (and unique)
			attributes.clear();
			attributes.put("requires_consumer_type", "system");
			//attributes.put("sockets", "0");
			attributes.put("version", N+".0");
			//attributes.put("variant", "server");
			attributes.put("arch", "ALL");
			//attributes.put("warning_period", "30");
			// delete already existing subscription and products
			CandlepinTasks.deleteSubscriptionsAndRefreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, sm_clientOrg, marketingProductId);
			CandlepinTasks.deleteResourceUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, "/products/"+marketingProductId);
			CandlepinTasks.deleteResourceUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, "/products/"+engineeringProductId);
			// create a new marketing product (MKT), engineering product (SVC), content for the engineering product, and a subscription to the marketing product that provides the engineering product
			attributes.put("type", "MKT");
			CandlepinTasks.createProductUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, marketingProductName, marketingProductId, 1, attributes, null);
			attributes.put("type", "SVC");
			CandlepinTasks.createProductUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, engineeringProductName, engineeringProductId, 1, attributes, null);
			for (int i = 1; i <= N; i++) {
				String contentId = String.format(contentIdStringFormat,i);	// must be numeric (and unique) defined above
				CandlepinTasks.addContentToProductUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, engineeringProductId, contentId, /*randomGenerator.nextBoolean()*/i%3==0?true:false);	// WARNING: Be careful with the enabled flag! If the same content is enabled under one product and then disabled in another product, the tests to assert enabled or disabled will both fail due to conflict of interest.  Therefore use this flag with some pseudo-randomness 
			}
			CandlepinTasks.createSubscriptionAndRefreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, sm_clientOrg, 20, -1*24*60/*1 day ago*/, 15*24*60/*15 days from now*/, getRandInt(), getRandInt(), marketingProductId, Arrays.asList(engineeringProductId));
		}
		
		// recreate Subscription SKU: subscriptionSKUProvidingTwo93ContentSetProducts
		marketingProductName = "Subscription providing two 93 ContentSet Products";
		marketingProductId = subscriptionSKUProvidingTwo93ContentSetProducts;
		String engineeringProductNameA = "93 ContentSet Product A";
		String engineeringProductNameB = "93 ContentSet Product B";
		String engineeringProductIdA = "931";	// must be numeric (and unique)
		String engineeringProductIdB = "932";	// must be numeric (and unique)
		attributes.clear();
		attributes.put("requires_consumer_type", "system");
		//attributes.put("sockets", "0");
		attributes.put("version", "93.0");
		//attributes.put("variant", "server");
		attributes.put("arch", "ALL");
		//attributes.put("warning_period", "30");
		// delete already existing subscription and products
		CandlepinTasks.deleteSubscriptionsAndRefreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, sm_clientOrg, marketingProductId);
		CandlepinTasks.deleteResourceUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, "/products/"+marketingProductId);
		CandlepinTasks.deleteResourceUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, "/products/"+engineeringProductIdA);
		CandlepinTasks.deleteResourceUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, "/products/"+engineeringProductIdB);
		// create a new marketing product (MKT), engineering product (SVC), content for the engineering product, and a subscription to the marketing product that provides the engineering product
		attributes.put("type", "MKT");
		CandlepinTasks.createProductUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, marketingProductName, marketingProductId, 1, attributes, null);
		attributes.put("type", "SVC");
		CandlepinTasks.createProductUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, engineeringProductNameA, engineeringProductIdA, 1, attributes, null);
		CandlepinTasks.createProductUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, engineeringProductNameB, engineeringProductIdB, 1, attributes, null);
		for (int i = 1; i <= 93; i++) {
			String contentId = String.format(contentIdStringFormat,i);	// must be numeric (and unique) defined above
			CandlepinTasks.addContentToProductUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, engineeringProductIdA, contentId, /*randomGenerator.nextBoolean()*/i%3==0?true:false);	// WARNING: Be careful with the enabled flag! If the same content is enabled under one product and then disabled in another product, the tests to assert enabled or disabled will both fail due to conflict of interest.  Therefore use this flag with some pseudo-randomness 
			CandlepinTasks.addContentToProductUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, engineeringProductIdB, contentId, /*randomGenerator.nextBoolean()*/i%3==0?true:false);	// WARNING: Be careful with the enabled flag! If the same content is enabled under one product and then disabled in another product, the tests to assert enabled or disabled will both fail due to conflict of interest.  Therefore use this flag with some pseudo-randomness 
		}
		CandlepinTasks.createSubscriptionAndRefreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, sm_clientOrg, 20, -1*24*60/*1 day ago*/, 15*24*60/*15 days from now*/, getRandInt(), getRandInt(), marketingProductId, Arrays.asList(engineeringProductIdA,engineeringProductIdB));

		
		// recreate Subscription SKU: subscriptionSKUProvidingArchBasedContentSets
		int N = 200;
		marketingProductName = String.format("Subscription providing a Product with Arch-Based ContentSets",N);
		marketingProductId = "mktProductId-"+N;
		engineeringProductName = String.format("Product with various Arch-Based ContentSets",N);
		engineeringProductId = String.valueOf(N);	// must be numeric (and unique)
		attributes.clear();
		attributes.put("requires_consumer_type", "system");
		//attributes.put("sockets", "0");
		attributes.put("version", N+".0");
		//attributes.put("variant", "server");
		//attributes.put("arch", "ALL");
		attributes.put("arch", "x86_64,ppc64,ia64");	// give the product an arch too
		//attributes.put("warning_period", "30");
		// delete already existing subscription and products
		CandlepinTasks.deleteSubscriptionsAndRefreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, sm_clientOrg, marketingProductId);
		CandlepinTasks.deleteResourceUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, "/products/"+marketingProductId);
		CandlepinTasks.deleteResourceUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, "/products/"+engineeringProductId);
		// create a new marketing product (MKT), engineering product (SVC), content for the engineering product, and a subscription to the marketing product that provides the engineering product
		attributes.put("type", "MKT");
		CandlepinTasks.createProductUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, marketingProductName, marketingProductId, 1, attributes, null);
		attributes.put("type", "SVC");
		CandlepinTasks.createProductUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, engineeringProductName, engineeringProductId, 1, attributes, null);
		for (int i = 1; i <= N; i++) {
			String contentId = String.format(archBasedContentIdStringFormat,i);	// must be numeric (and unique) defined above
			CandlepinTasks.addContentToProductUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, engineeringProductId, contentId, /*randomGenerator.nextBoolean()*/i%3==0?true:false);	// WARNING: Be careful with the enabled flag! If the same content is enabled under one product and then disabled in another product, the tests to assert enabled or disabled will both fail due to conflict of interest.  Therefore use this flag with some pseudo-randomness 
		}
		CandlepinTasks.createSubscriptionAndRefreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, sm_clientOrg, 20, -1*24*60/*1 day ago*/, 15*24*60/*15 days from now*/, getRandInt(), getRandInt(), marketingProductId, Arrays.asList(engineeringProductId));

		
		// NOTE: To get the product certs, use the CandlepinTasks REST API:
        //"url": "/products/{product_uuid}/certificate", 
        //"GET"
	}
	
	// Protected Methods ***********************************************************************
	protected String personalConsumerId = null;
	protected String subscriptionSKUProvidingA185ContentSetProduct = "mktProductId-185";
	protected String subscriptionSKUProvidingA186ContentSetProduct = "mktProductId-186";
	protected String subscriptionSKUProvidingTwo93ContentSetProducts = "mktProductId-93x2";
	protected String subscriptionSKUProvidingArchBasedContent = "mktProductId-200";
	
	protected void VerifySubscribabilityOfSKUProvidingTooManyContentSets(String sku, int totalContentSets) {	//TODO remove parameter totalContentSets and make calls to CandepinTasks to find the totalContentSets from the sku
		
		Map<String,String> factsMap = new HashMap<String,String>();
		File entitlementCertFile;
		EntitlementCert entitlementCert;
		String systemCertificateVersionFactValue;
		SSHCommandResult sshCommandResult;
		String tooManyContentSetsMsgFormat = "Too many content sets for certificate. Please upgrade to a newer client to use subscription: %s";	// this msgid comes from Candlepin
		tooManyContentSetsMsgFormat = "Too many content sets for certificate %s. A newer client may be available to address this problem. See kbase https://access.redhat.com/knowledge/node/129003 for more information.";
		
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, true, false, null, null, null);
		SubscriptionPool pool = SubscriptionPool.findFirstInstanceWithMatchingFieldFromList("productId", sku, clienttasks.getCurrentlyAvailableSubscriptionPools());
		Assert.assertNotNull(pool,"Found an available pool to subscribe to productId '"+sku+"': "+pool);
	
		// test that it is NOT subscribable when system.certificate_version: None
		factsMap.put("system.certificate_version", null);
		clienttasks.createFactsFileWithOverridingValues(factsMap);
		clienttasks.facts(null, true, null, null, null);
		Assert.assertEquals(clienttasks.getFactValue("system.certificate_version"), "None", "When the system.certificate_version fact is null, its fact value is reported as 'None'.");
		sshCommandResult = clienttasks.subscribe_(null, null, pool.poolId, null, null, null, null, null, null, null, null);
		Assert.assertEquals(sshCommandResult.getStderr().trim(), String.format(tooManyContentSetsMsgFormat, pool.subscriptionName), "Stderr from an attempt to subscribe to '"+pool.subscriptionName+"' that provides product(s) with many content sets (totalling >185) when system.certificate_version is null");
		Assert.assertEquals(sshCommandResult.getStdout().trim(), "", "Stdout from an attempt to subscribe to '"+pool.subscriptionName+"' that provides product(s) with many content sets (totalling >185) when system.certificate_version is null");
		Assert.assertEquals(sshCommandResult.getExitCode(), new Integer(255), "Exitcode from an attempt to subscribe to '"+pool.subscriptionName+"' that provides product(s) with many content sets (totalling >185) when system.certificate_version is null");
		Assert.assertTrue(clienttasks.getCurrentlyConsumedProductSubscriptions().isEmpty(), "No entitlements should be consumed after attempt to subscribe to '"+pool.subscriptionName+"' that provides product(s) with many content sets (totalling >185) when system.certificate_version is null");
		
		// test that it is NOT subscribable when system.certificate_version: 1.0
		factsMap.put("system.certificate_version", "1.0");
		clienttasks.createFactsFileWithOverridingValues(factsMap);
		clienttasks.facts(null, true, null, null, null);
		Assert.assertEquals(clienttasks.getFactValue("system.certificate_version"), "1.0", "When the system.certificate_version fact is 1.0, its fact value is reported as '1.0'.");
		sshCommandResult = clienttasks.subscribe_(null, null, pool.poolId, null, null, null, null, null, null, null, null);
		Assert.assertEquals(sshCommandResult.getStderr().trim(), String.format(tooManyContentSetsMsgFormat, pool.subscriptionName), "Stderr from an attempt to subscribe to '"+pool.subscriptionName+"' that provides product(s) with many content sets (totalling >185) when system.certificate_version is 1.0");
		Assert.assertEquals(sshCommandResult.getStdout().trim(), "", "Stdout from an attempt to subscribe to '"+pool.subscriptionName+"' that provides product(s) with many content sets (totalling >185) when system.certificate_version is 1.0");
		Assert.assertEquals(sshCommandResult.getExitCode(), new Integer(255), "Exitcode from an attempt to subscribe to '"+pool.subscriptionName+"' that provides product(s) with many content sets (totalling >185) when system.certificate_version is 1.0");
		Assert.assertTrue(clienttasks.getCurrentlyConsumedProductSubscriptions().isEmpty(), "No entitlements should be consumed after attempt to subscribe to '"+pool.subscriptionName+"' that provides product(s) with many content sets (totalling >185) when system.certificate_version is 1.0");

		// test that it is subscribable when system.certificate_version is the system's default value (should be >=3.0)
		clienttasks.deleteFactsFileWithOverridingValues();
		systemCertificateVersionFactValue = clienttasks.getFactValue("system.certificate_version");
		Assert.assertTrue(Float.valueOf(systemCertificateVersionFactValue)>=3.0, "The actual default system.certificate_version fact '"+systemCertificateVersionFactValue+"' is >= 3.0.");
		clienttasks.subscribe(null, null, pool.poolId, null, null, null, null, null, null, null, null);
		entitlementCert = clienttasks.getEntitlementCertCorrespondingToSubscribedPool(pool);
		entitlementCertFile = clienttasks.getEntitlementCertFileFromEntitlementCert(entitlementCert);
		Assert.assertTrue(Float.valueOf(entitlementCert.version)<=Float.valueOf(systemCertificateVersionFactValue),"The version of the entitlement certificate '"+entitlementCert.version+"' granted by candlepin is less than or equal to the system.certificate_version '"+systemCertificateVersionFactValue+"' which indicates the maximum certificate version this system knows how to handle.");
		Assert.assertEquals(entitlementCert.contentNamespaces.size(), totalContentSets, "The number of content sets provided in this version '"+entitlementCert.version+"' entitlement cert parsed using the rct cat-cert tool.");
		clienttasks.assertEntitlementCertsInYumRepolist(Arrays.asList(entitlementCert), true);
		clienttasks.unsubscribeFromSerialNumber(clienttasks.getSerialNumberFromEntitlementCertFile(entitlementCertFile));
	}
	
	
	
	// Data Providers ***********************************************************************

	
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
			String quantity = null; if (pool.suggested<1) quantity = CandlepinTasks.getPoolProductAttributeValue(sm_clientUsername, sm_clientPassword, sm_serverUrl, pool.poolId, "instance_multiplier"); 	// when the Suggested quantity is 0, let's specify a quantity to avoid Stdout: Quantity '1' is not a multiple of instance multiplier '2'
			File entitlementCertFile = clienttasks.subscribeToSubscriptionPool_(pool,quantity);
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

					// String availableGroup, String installedGroup, String repoLabel, SubscriptionPool pool, String quantity
					ll.add(Arrays.asList(new Object[]{pkg, repoLabel, pool, quantity}));
				}
			}
			clienttasks.unsubscribeFromSerialNumber(clienttasks.getSerialNumberFromEntitlementCertFile(entitlementCertFile));

			// minimize the number of dataProvided rows (useful during automated testcase development)
			if (Boolean.valueOf(getProperty("sm.debug.dataProviders.minimize","false"))) break;
		}
		
		return ll;
	}
	
	
	
	@DataProvider(name="getYumGroupFromEnabledRepoAndSubscriptionPoolData")
	public Object[][] getYumGroupFromEnabledRepoAndSubscriptionPoolDataAs2dArray() throws JSONException, Exception {
		return TestNGUtils.convertListOfListsTo2dArray(getYumGroupFromEnabledRepoAndSubscriptionPoolDataAsListOfLists());
	}
	protected List<List<Object>> getYumGroupFromEnabledRepoAndSubscriptionPoolDataAsListOfLists() throws JSONException, Exception {
		List<List<Object>> ll = new ArrayList<List<Object>>(); if (!isSetupBeforeSuiteComplete) return ll;
		if (clienttasks==null) return ll;
		if (sm_clientUsername==null) return ll;
		if (sm_clientPassword==null) return ll;
		
		// get the currently installed product certs to be used when checking for conditional content tagging
		List<ProductCert> currentProductCerts = clienttasks.getCurrentProductCerts();
		
		// assure we are freshly registered and process all available subscription pools
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, ConsumerType.system, null, null, null, null, null, (String)null, null, null, null, Boolean.TRUE, false, null, null, null);
		for (SubscriptionPool pool : clienttasks.getCurrentlyAvailableSubscriptionPools()) {
			
			File entitlementCertFile = clienttasks.subscribeToSubscriptionPool_(pool);
			Assert.assertNotNull(entitlementCertFile, "Found the entitlement cert file that was granted after subscribing to pool: "+pool);
			EntitlementCert entitlementCert = clienttasks.getEntitlementCertFromEntitlementCertFile(entitlementCertFile);
			for (ContentNamespace contentNamespace : entitlementCert.contentNamespaces) {
				if (!contentNamespace.type.equalsIgnoreCase("yum")) continue;
				if (contentNamespace.enabled && clienttasks.areAllRequiredTagsInContentNamespaceProvidedByProductCerts(contentNamespace, currentProductCerts)) {
					String repoLabel = contentNamespace.label;

					// find first available group provided by this repo
					String availableGroup = clienttasks.findAnAvailableGroupFromRepo(repoLabel);
					// find first installed group provided by this repo
					String installedGroup = clienttasks.findAnInstalledGroupFromRepo(repoLabel);

					// String availableGroup, String installedGroup, String repoLabel, SubscriptionPool pool
					ll.add(Arrays.asList(new Object[]{availableGroup, installedGroup, repoLabel, pool}));
				}
			}
			clienttasks.unsubscribeFromSerialNumber(clienttasks.getSerialNumberFromEntitlementCertFile(entitlementCertFile));

			// minimize the number of dataProvided rows (useful during automated testcase development)
			if (Boolean.valueOf(getProperty("sm.debug.dataProviders.minimize","false"))) break;
		}
		
		return ll;
	}
	
	
	@DataProvider(name="getPackageFromEnabledRepoAndPersonalSubscriptionSubPoolData")
	public Object[][] getPackageFromEnabledRepoAndPersonalSubscriptionSubPoolDataAs2dArray() throws Exception {
		return TestNGUtils.convertListOfListsTo2dArray(getPackageFromEnabledRepoAndPersonalSubscriptionSubPoolDataAsListOfLists());
	}
	protected List<List<Object>> getPackageFromEnabledRepoAndPersonalSubscriptionSubPoolDataAsListOfLists() throws Exception {
		List<List<Object>> ll = new ArrayList<List<Object>>(); if (!isSetupBeforeSuiteComplete) return ll;
		if (client1tasks==null) return ll;
		if (client2tasks==null) return ll;
		if (true) {
			log.warning("Support for the Personal Subscriptions was yanked in favor of new DataCenter SKUs.");
			return ll;
		}
		
		// assure we are registered (as a person on client2 and a system on client1)
		
		// register client1 as a system under rhpersonalUsername
		client1tasks.register(sm_rhpersonalUsername, sm_rhpersonalPassword, sm_rhpersonalOrg, null, ConsumerType.system, null, null, null, null, null, (String)null, null, null, null, Boolean.TRUE, false, null, null, null);
		
		// register client2 as a person under rhpersonalUsername
		client2tasks.register(sm_rhpersonalUsername, sm_rhpersonalPassword, sm_rhpersonalOrg, null, ConsumerType.person, null, null, null, null, null, (String)null, null, null, null, Boolean.TRUE, false, null, null, null);
		
		// subscribe to the personal subscription pool to unlock the subpool
		personalConsumerId = client2tasks.getCurrentConsumerId();
		for (int j=0; j<sm_personSubscriptionPoolProductData.length(); j++) {
			JSONObject poolProductDataAsJSONObject = (JSONObject) sm_personSubscriptionPoolProductData.get(j);
			String personProductId = poolProductDataAsJSONObject.getString("personProductId");
			JSONObject subpoolProductDataAsJSONObject = poolProductDataAsJSONObject.getJSONObject("subPoolProductData");
			String systemProductId = subpoolProductDataAsJSONObject.getString("systemProductId");

			SubscriptionPool personPool = SubscriptionPool.findFirstInstanceWithMatchingFieldFromList("productId",personProductId,client2tasks.getCurrentlyAvailableSubscriptionPools());
			Assert.assertNotNull(personPool,"Personal productId '"+personProductId+"' is available to user '"+sm_rhpersonalUsername+"' registered as a person.");
			File entitlementCertFile = client2tasks.subscribeToSubscriptionPool_(personPool);
			Assert.assertNotNull(entitlementCertFile, "Found the entitlement cert file that was granted after subscribing to personal pool: "+personPool);

			
			// now the subpool is available to the system
			SubscriptionPool systemPool = SubscriptionPool.findFirstInstanceWithMatchingFieldFromList("productId",systemProductId,client1tasks.getCurrentlyAvailableSubscriptionPools());
			Assert.assertNotNull(systemPool,"Personal subPool productId'"+systemProductId+"' is available to user '"+sm_rhpersonalUsername+"' registered as a system.");
			//client1tasks.subscribeToSubscriptionPool(systemPool);
			
			entitlementCertFile = client1tasks.subscribeToSubscriptionPool_(systemPool);
			Assert.assertNotNull(entitlementCertFile, "Found the entitlement cert file that was granted after subscribing to system pool: "+systemPool);
			EntitlementCert entitlementCert = client1tasks.getEntitlementCertFromEntitlementCertFile(entitlementCertFile);
			for (ContentNamespace contentNamespace : entitlementCert.contentNamespaces) {
				if (!contentNamespace.type.equalsIgnoreCase("yum")) continue;
				if (contentNamespace.enabled) {
					String repoLabel = contentNamespace.label;
					
					// find an available package that is uniquely provided by repo
					String pkg = client1tasks.findUniqueAvailablePackageFromRepo(repoLabel);
					if (pkg==null) {
						log.warning("Could NOT find a unique available package from repo '"+repoLabel+"' after subscribing to SubscriptionSubPool: "+systemPool);
					}

					// String availableGroup, String installedGroup, String repoLabel, SubscriptionPool pool
					ll.add(Arrays.asList(new Object[]{pkg, repoLabel, systemPool}));
				}
			}
			client1tasks.unsubscribeFromSerialNumber(client1tasks.getSerialNumberFromEntitlementCertFile(entitlementCertFile));
		}
		return ll;
	}
	
	
	
	@DataProvider(name="getAllAvailableSubscriptionPoolsProvidingArchBasedContentData")
	public Object[][] getAllAvailableSubscriptionPoolsProvidingArchBasedContentDataAs2dArray() throws JSONException, Exception {
		return TestNGUtils.convertListOfListsTo2dArray(getAllAvailableSubscriptionPoolsProvidingArchBasedContentDataAsListOfLists());
	}
	protected List<List<Object>> getAllAvailableSubscriptionPoolsProvidingArchBasedContentDataAsListOfLists() throws JSONException, Exception {
		List<List<Object>> ll = new ArrayList<List<Object>>();

		for (List<Object> l : getAllAvailableSubscriptionPoolsDataAsListOfLists()) {
			SubscriptionPool pool = (SubscriptionPool)l.get(0);
			
			for (String providedProductId : CandlepinTasks.getPoolProvidedProductIds(sm_clientUsername,sm_clientPassword,sm_serverUrl,pool.poolId)) {
	
				// get the product
				JSONObject jsonProduct = new JSONObject(CandlepinTasks.getResourceUsingRESTfulAPI(sm_clientUsername,sm_clientPassword,sm_serverUrl,"/products/"+providedProductId));	
				
				// get the provided product contents
				JSONArray jsonProductContents = jsonProduct.getJSONArray("productContent");
				for (int j = 0; j < jsonProductContents.length(); j++) {
					JSONObject jsonProductContent = (JSONObject) jsonProductContents.get(j);
					JSONObject jsonContent = jsonProductContent.getJSONObject("content");
					
					// is this arch-based content?
					if (jsonContent.has("arches") && !jsonContent.isNull("arches")) {
						ll.add(l);
						break;
					}
				}
			}
		}
		
		return ll;
	}
}

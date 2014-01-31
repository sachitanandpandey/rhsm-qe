package rhsm.cli.tests;

import java.io.File;
import java.math.BigInteger;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONException;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterGroups;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeGroups;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.redhat.qe.Assert;
import com.redhat.qe.auto.bugzilla.BlockedByBzBug;
import com.redhat.qe.auto.testng.TestNGUtils;
import com.redhat.qe.jul.TestRecords;
import rhsm.base.SubscriptionManagerCLITestScript;
import rhsm.cli.tasks.CandlepinTasks;
import rhsm.data.EntitlementCert;
import rhsm.data.InstalledProduct;
import rhsm.data.ProductCert;
import rhsm.data.ProductNamespace;
import rhsm.data.ProductSubscription;
import rhsm.data.SubscriptionPool;
import com.redhat.qe.tools.RemoteFileTasks;
import com.redhat.qe.tools.SSHCommandResult;

/**
 * @author jsefler
 *
 * Note: This scribe depends on register with --autosubscribe working properly
 */


@Test(groups={"ComplianceTests","AcceptanceTests"})
public class ComplianceTests extends SubscriptionManagerCLITestScript{
	
	
	// Test Methods ***********************************************************************
	
	@Test(	description="when a subscription is attached that does not match the system's arch, assert the installed product status is blocked from going green",
			groups={"cli.tests","VerifyComplianceConsidersSystemArch_Test","blockedByBug-909467"},
			dataProvider="getSubscriptionPoolProvidingProductIdOnArchData",
			enabled=true)
	//@ImplementsTCMS(id="")
	public void VerifyComplianceConsidersSystemArch_Test(Object bugzilla, SubscriptionPool pool, String providingProductId, String poolArch) {
		clienttasks.deleteFactsFileWithOverridingValues(fakeArchFactsFilename);
		clienttasks.unsubscribe(true, (BigInteger)null, null, null, null);
		//OVERKILL InstalledProduct installedProduct = InstalledProduct.findFirstInstanceWithMatchingFieldFromList("productId", providingProductId, clienttasks.getCurrentlyInstalledProducts());
		//OVERKILL Assert.assertEquals(installedProduct.status, "Not Subscribed");
		clienttasks.subscribe(null, null, pool.poolId, null, null, null, null, null, null, null, null);
		ProductSubscription productSubscription = ProductSubscription.findFirstInstanceWithMatchingFieldFromList("poolId", pool.poolId, clienttasks.getCurrentlyConsumedProductSubscriptions());
		InstalledProduct installedProduct = InstalledProduct.findFirstInstanceWithMatchingFieldFromList("productId", providingProductId, clienttasks.getCurrentlyInstalledProducts());
		
		// expand the arches that the granted entitlement from this subscription pool covers
		List<String> poolArches = new ArrayList<String>(Arrays.asList(poolArch.trim().split(" *, *")));	// Note: the arch can be a comma separated list of values
		// expand the x86 alias
		if (poolArches.contains("x86")) {poolArches.addAll(Arrays.asList("i386","i486","i586","i686"));}  // Note: x86 is a general term to cover all 32-bit intel microprocessors 
		// expand the ALL alias
		if (poolArches.contains("ALL")) {poolArches.addAll(Arrays.asList("i386","i486","i586","i686","x86_64","ia64","ppc64","s390x"));}
		
		// expand the arches that this installed product is valid on
		List<String> installedProductArches = new ArrayList<String>(Arrays.asList(installedProduct.arch.trim().split(" *, *")));	// Note: the arch can be a comma separated list of values
		// expand the x86 alias
		if (installedProductArches.contains("x86")) {installedProductArches.addAll(Arrays.asList("i386","i486","i586","i686"));}  // Note: x86 is a general term to cover all 32-bit intel microprocessors 
		// expand the ALL alias
		if (installedProductArches.contains("ALL")) {installedProductArches.addAll(Arrays.asList("i386","i486","i586","i686","x86_64","ia64","ppc64","s390x"));}
		
		// assert the statusDetails for the consumed entitlement
		if (poolArches.contains(clienttasks.arch)) {
			Assert.assertTrue(productSubscription.statusDetails.isEmpty(), "The statusDetails from the consumed product subscription should be empty when the system's arch '"+clienttasks.arch+"' is covered by the product subscription arches '"+poolArch.trim()+"'.");
		} else {
			if (productSubscription.statusDetails.isEmpty()) log.warning("Status Details appears empty.  Is your candlepin server older than 0.8.6?");
			Assert.assertEquals(productSubscription.statusDetails.get(0)/*assumes only one detail*/, String.format("Supports architecture %s but the system is %s.", poolArch.trim(), clienttasks.arch), "The statusDetails from the consumed product subscription when the system's arch '"+clienttasks.arch+"' is NOT covered by the product subscription arches '"+poolArch.trim()+"'."); // Message changed by candlepin commit 43a17952c724374c3fee735642bce52811a1e386 covers -> supports
		}
		
		/* THIS ASSERTION BLOCK IS NOT ACCURATE SINCE THE ARCH ON THE PRODUCT CERT IS NOT CONSIDERED AT ALL
		// if the poolArches contains any one element of installedProductArches and contains the system's arch, then status should go "green", otherwise it should be "yellow"
		String expectedStatus="Partially Subscribed";	// yellow
		for (String installedProductArch : installedProductArches) {
			if (poolArches.contains(installedProductArch)) {
				if (poolArches.contains(clienttasks.arch)) {
					expectedStatus="Subscribed";	// green
				}
			}
		}
		Assert.assertEquals(installedProduct.status, expectedStatus,"When installed product '"+installedProduct.productName+"' valid on arch(es) '"+installedProduct.arch+"' and the installed system's arch '"+clienttasks.arch+"' do not match the subscription pool's product arch '"+poolArch+"', the installed product compliance achieveable is limited to yellow after subscribing to '"+pool.subscriptionName+"'.");
		REPLACING THIS ASSERTION BLOCK WITH THE FOLLOWING BLOCK */
		// assert the status and statusDetails for the installed product
		if (poolArches.contains(clienttasks.arch)) {
			Assert.assertEquals(installedProduct.status, "Subscribed", "When installed product '"+installedProduct.productName+"' is covered by subscription '"+pool.subscriptionName+"' whose arches '"+poolArch+"' cover the system's arch '"+clienttasks.arch+"', then the installed product can achieve full green compliance.");
			Assert.assertTrue(installedProduct.statusDetails.isEmpty(), "The statusDetails for installed product '"+installedProduct.productName+"' productId='"+providingProductId+"' should be empty when the system's arch '"+clienttasks.arch+"' is covered by the product subscription arches '"+poolArch.trim()+"'. (Note: the installed products arches '"+installedProduct.arch+"' are not considered)");
		} else {
			if (installedProduct.statusDetails.isEmpty()) log.warning("Status Details appears empty.  Is your candlepin server older than 0.8.6?");
			Assert.assertEquals(installedProduct.status, "Partially Subscribed", "When installed product '"+installedProduct.productName+"' is covered by subscription '"+pool.subscriptionName+"' whose arches '"+poolArch+"' do NOT cover the system's arch '"+clienttasks.arch+"', then the installed product is limited to yellow compliance.");
			Assert.assertEquals(installedProduct.statusDetails.get(0)/*assumes only one detail*/, String.format("Supports architecture %s but the system is %s.", poolArch.trim(), clienttasks.arch), "The statusDetails of the installed product '"+installedProduct.productName+"' when the system's arch '"+clienttasks.arch+"' is NOT covered by the product subscription arches '"+poolArch.trim()+"'."); // Message changed by candlepin commit 43a17952c724374c3fee735642bce52811a1e386 covers -> supports
		}
		
		// now let's fake the system's arch fact "uname.machine" forcing it to NOT match the providing productSubscription
		if (productSubscription.statusDetails.isEmpty() && !poolArches.contains("ALL")) {
			String fakeArch = "amd64";
			Map<String,String> factsMap = new HashMap<String,String>();
			factsMap.put("uname.machine",fakeArch);
			clienttasks.createFactsFileWithOverridingValues(fakeArchFactsFilename,factsMap);
			clienttasks.facts(null, true, null, null, null);
			productSubscription = ProductSubscription.findFirstInstanceWithMatchingFieldFromList("poolId", pool.poolId, clienttasks.getCurrentlyConsumedProductSubscriptions());
			if (productSubscription.statusDetails.isEmpty()) log.warning("Status Details appears empty.  Is your candlepin server older than 0.8.6?");
			Assert.assertEquals(productSubscription.statusDetails.get(0)/*assumes only one detail*/, String.format("Supports architecture %s but the system is %s.", poolArch.trim(), fakeArch), "The statusDetails from the consumed product subscription when the system's arch '"+fakeArch+"' is NOT covered by the product subscription arches '"+poolArch.trim()+"'."); // Message changed by candlepin commit 43a17952c724374c3fee735642bce52811a1e386 covers -> supports
			installedProduct = InstalledProduct.findFirstInstanceWithMatchingFieldFromList("productId", providingProductId, clienttasks.getCurrentlyInstalledProducts());
			if (installedProduct.statusDetails.isEmpty()) log.warning("Status Details appears empty.  Is your candlepin server older than 0.8.6?");
			Assert.assertEquals(installedProduct.statusDetails.get(0)/*assumes only one detail*/, String.format("Supports architecture %s but the system is %s.", poolArch.trim(), fakeArch), "The statusDetails of the installed product '"+installedProduct.productName+"' when the system's arch '"+clienttasks.arch+"' is NOT covered by the product subscription arches '"+poolArch.trim()+"'."); // Message changed by candlepin commit 43a17952c724374c3fee735642bce52811a1e386 covers -> supports
		}
	}
	@BeforeGroups(groups={"setup"},value="VerifyComplianceConsidersSystemArch_Test")
	public void createFactsFileWithOverridingValuesBeforeVerifyComplianceConsidersSystemArch_Test() {
		// these facts will prevent cores, sockets, and ram from interfering with compliance based on the system arch
		Map<String,String> factsMap = new HashMap<String,String>();
		factsMap.put("cpu.cpu_socket(s)","1");
		factsMap.put("cpu.core(s)_per_socket","1");
		factsMap.put("memory.memtotal","1");
		clienttasks.createFactsFileWithOverridingValues(factsMap);
	}
	@AfterGroups(groups={"setup"},value="VerifyComplianceConsidersSystemArch_Test")
	public void deleteFactsFileWithOverridingValuesAfterVerifyComplianceConsidersSystemArch_Test() {
		clienttasks.deleteFactsFileWithOverridingValues();
		clienttasks.deleteFactsFileWithOverridingValues(fakeArchFactsFilename);
	}
	protected final String fakeArchFactsFilename = "fake_arch.facts";
	@DataProvider(name="getSubscriptionPoolProvidingProductIdOnArchData")
	public Object[][] getSubscriptionPoolProvidingProductIdOnArchDataAs2dArray() throws JSONException, Exception {
		return TestNGUtils.convertListOfListsTo2dArray(getSubscriptionPoolProvidingProductIdOnArchDataAsListOfLists());
	}
	protected List<List<Object>> getSubscriptionPoolProvidingProductIdOnArchDataAsListOfLists() throws JSONException, Exception{
		List<List<Object>> ll = new ArrayList<List<Object>>(); if (!isSetupBeforeSuiteComplete) return ll;
		//configureProductCertDirAfterClass(); is not needed since the priority of this test is implied as 0 and run first before the other tests alter the productCertDir
		List<ProductCert> productCerts = clienttasks.getCurrentProductCerts();
		List<String> productIdArchTested = new ArrayList<String>();
		for (List<Object> allAvailableSubscriptionPoolsDataList : getAllAvailableSubscriptionPoolsDataAsListOfLists()) {
			SubscriptionPool availableSubscriptionPool = (SubscriptionPool) allAvailableSubscriptionPoolsDataList.get(0);
			List<String> providedProductIds = CandlepinTasks.getPoolProvidedProductIds(sm_clientUsername, sm_clientPassword, sm_serverUrl, availableSubscriptionPool.poolId);
			String poolProductArch = CandlepinTasks.getPoolProductAttributeValue(sm_clientUsername, sm_clientPassword, sm_serverUrl, availableSubscriptionPool.poolId, "arch");
			for (ProductCert productCert : productCerts) {
				if (providedProductIds.contains(productCert.productId)) {
					BlockedByBzBug blockedByBzBug = null;
					
					// if a row has already been added for this productId+arch combination, skip it since adding it would be redundant testing
					if (productIdArchTested.contains(productCert.productId+poolProductArch)) continue;
					
					// Bug 951633 - installed product with comma separated arch attribute fails to go green 
					if (productCert.productNamespace.arch.contains(",")) blockedByBzBug = new BlockedByBzBug("951633");
					
					ll.add(Arrays.asList(new Object[]{blockedByBzBug, availableSubscriptionPool, productCert.productId, poolProductArch}));
					
					productIdArchTested.add(productCert.productId+poolProductArch);	
				}
			}
		}

		return ll;
	}
	
	
	
	
	@Test(	description="subscription-manager: verify the system.compliant fact is True when all installed products are subscribable by more than one common service level",
			groups={"configureProductCertDirForAllProductsSubscribableByMoreThanOneCommonServiceLevel","cli.tests","blockedbyBug-859652"},
			dataProvider="getAllProductsSubscribableByMoreThanOneCommonServiceLevelValuesData",
			priority=100,
			enabled=true)
	//@ImplementsTCMS(id="")
	public void VerifySystemCompliantFactWhenAllProductsSubscribableByMoreThanOneCommonServiceLevel_Test(Object bugzilla, String servicelevel) {
		
		// test register with service level
		clienttasks.unregister_(null,null,null);
		//Assert.assertEquals(clienttasks.getFactValue(factNameForSystemCompliance), factValueForSystemNonCompliance,	// THIS ASSERTION IS NO LONGER VALID NOW THAT COMPLIANCE IS CALCULATED ON THE SERVER.  INSTEAD, THE LOCAL COMPLIANCE SHOULD BE TAKEN FROM THE SYSTEM CACHE.  THIS WORK IS CURRENTLY UNDER DEVELOPMENT 3/13/2013.
		//		"Before attempting to register with autosubscribe and a common servicelevel to become compliant for all the currently installed products, the system should be non-compliant (see value for fact '"+factNameForSystemCompliance+"').");
		clienttasks.register(sm_clientUsername,sm_clientPassword,sm_clientOrg,null,null,null,null,true,servicelevel,null,(String)null, null, null, null, Boolean.TRUE, false, null, null, null);
		Assert.assertEquals(clienttasks.getFactValue(factNameForSystemCompliance), factValueForSystemCompliance,
				"When a system has products installed for which ALL are covered by available subscription pools with a common service level, the system should become compliant (see value for fact '"+factNameForSystemCompliance+"')");
		for (ProductSubscription productSubscription : clienttasks.getCurrentlyConsumedProductSubscriptions()) {
			//CASE SENSITIVE ASSERTION Assert.assertEquals(productSubscription.serviceLevel, servicelevel, "When a system has been registered with autosubscribe specifying a common service level, then all consumed product subscriptions must provide that service level.");
			Assert.assertTrue(servicelevel.equalsIgnoreCase(productSubscription.serviceLevel),
				"When a system has been registered with autosubscribe specifying a common service level '"+servicelevel+"', then this auto consumed product subscription ("+productSubscription+") must provide case-insensitive match to the requested service level.");

		}
		Assert.assertEquals(clienttasks.getCurrentServiceLevel(), servicelevel,
				"When a system has been registered with autosubscribe specifying a common service level, then the consumer's service level prefernce should be set to that value.");
	
		// test autosubscribe (without service level) and assert that the consumed subscriptions provide the same service level as persisted during register
		clienttasks.unsubscribe(true, (BigInteger)null, null, null, null);
		Assert.assertEquals(clienttasks.getFactValue(factNameForSystemCompliance), factValueForSystemNonCompliance,
				"Before attempting to autosubscribe with a common servicelevel to become compliant for all the currently installed products, the system should be non-compliant (see value for fact '"+factNameForSystemCompliance+"').");
		clienttasks.subscribe(true, null, (List<String>)null, null, null, null, null, null, null, null, null);
		Assert.assertEquals(clienttasks.getFactValue(factNameForSystemCompliance), factValueForSystemCompliance,
				"When a system has products installed for which ALL are covered by available subscription pools with a common service level, the system should become compliant (see value for fact '"+factNameForSystemCompliance+"')");
		for (ProductSubscription productSubscription : clienttasks.getCurrentlyConsumedProductSubscriptions()) {
			//CASE SENSITIVE ASSERTION Assert.assertEquals(productSubscription.serviceLevel, servicelevel, "When a system has been registered with autosubscribe without specifying a common service level, then all consumed product subscriptions must provide the consumer's service level preference.");
			Assert.assertTrue(servicelevel.equalsIgnoreCase(productSubscription.serviceLevel),
				"When a system has been registered with autosubscribe without specifying a common service level, then this auto consumed product subscription ("+productSubscription+") must provide a case-insensitive match to the consumer's service level preference '"+servicelevel+"'.");
		}
	}
	
	@Test(	description="rhsm-complianced: verify rhsm-complianced -d -s reports a compliant status when all installed products are subscribable by more than one common service level",
			groups={"cli.tests","blockedByBug-991580"},
			priority=110,//dependsOnMethods={"VerifySystemCompliantFactWhenAllProductsSubscribableByMoreThanOneCommonServiceLevel_Test"},			
			enabled=true)
	//@ImplementsTCMS(id="")
	public void VerifyRhsmCompliancedWhenAllProductsSubscribableByMoreThanOneCommonServiceLevel_Test() {
		if (!configureProductCertDirForAllProductsSubscribableByMoreThanOneCommonServiceLevelCompleted) throw new SkipException("Unsatisfied dependency configureProductCertDirForAllProductsSubscribableByMoreThanOneCommonServiceLevelCompleted="+configureProductCertDirForAllProductsSubscribableByMoreThanOneCommonServiceLevelCompleted);
		verifyRhsmCompliancedWhenAllProductsAreSubscribable();
	}
	
	@Test(	description="when all installed products are subscribable by more than one common service level and system is compliant, auto-subscribe should abort",
			groups={"cli.tests","blockedByBug-864207"},
			priority=120,//dependsOnMethods={"VerifySystemCompliantFactWhenAllProductsSubscribableByMoreThanOneCommonServiceLevel_Test"},
			enabled=true)
	//@ImplementsTCMS(id="")
	public void VerifyAutoSubscribeAbortsWhenCompliantAndAllProductsSubscribableByMoreThanOneCommonServiceLevel_Test() {
		if (!configureProductCertDirForAllProductsSubscribableByMoreThanOneCommonServiceLevelCompleted) throw new SkipException("Unsatisfied dependency configureProductCertDirForAllProductsSubscribableByMoreThanOneCommonServiceLevelCompleted="+configureProductCertDirForAllProductsSubscribableByMoreThanOneCommonServiceLevelCompleted);
		SSHCommandResult result = clienttasks.subscribe(true, null, (List<String>)null, null, null, null, null, null, null, null, null);
		Assert.assertTrue(result.getStdout().trim().startsWith(autosubscribeCompliantMessage), "When the system is already compliant, an attempt to auto-subscribe should inform us with exactly this message: "+autosubscribeCompliantMessage);
	}
	
	@Test(	description="when the candlepin server goes offline, assert the Installed Product Status is reported from cache",
			groups={"cli.tests","VerifyListInstalledIsCachedAfterAllProductsSubscribableByMoreThanOneCommonServiceLevel_Test"},
			priority=130,//dependsOnMethods={"VerifySystemCompliantFactWhenAllProductsSubscribableByMoreThanOneCommonServiceLevel_Test"},
			enabled=true)
	//@ImplementsTCMS(id="")
	public void VerifyListInstalledIsCachedAfterAllProductsSubscribableByMoreThanOneCommonServiceLevel_Test() {
		if (!configureProductCertDirForAllProductsSubscribableByMoreThanOneCommonServiceLevelCompleted) throw new SkipException("Unsatisfied dependency configureProductCertDirForAllProductsSubscribableByMoreThanOneCommonServiceLevelCompleted="+configureProductCertDirForAllProductsSubscribableByMoreThanOneCommonServiceLevelCompleted);
		verifyListInstalledIsCachedWhenServerGoesOffline();
	}
	@AfterGroups(groups={"setup"},value="VerifyListInstalledIsCachedAfterAllProductsSubscribableByMoreThanOneCommonServiceLevel_Test")
	public void afterVerifyListInstalledIsCachedAfterAllProductsSubscribableByMoreThanOneCommonServiceLevel_Test() {
		if (serverHostname!=null) clienttasks.config(null, null, true, new String[]{"server","hostname",serverHostname});
	}
	
	
	
	
	@Test(	description="subscription-manager: verify the system.compliant fact is True when all installed products are subscribable by one common service level",
			groups={"configureProductCertDirForAllProductsSubscribableByOneCommonServiceLevel","cli.tests","blockedbyBug-859652"},
			priority=200,
			enabled=true)
	//@ImplementsTCMS(id="")
	public void VerifySystemCompliantFactWhenAllProductsSubscribableByOneCommonServiceLevel_Test() {
		VerifySystemCompliantFactWhenAllProductsSubscribableByMoreThanOneCommonServiceLevel_Test(null,allProductsSubscribableByOneCommonServiceLevelValue);
	}
	
	@Test(	description="rhsm-complianced: verify rhsm-complianced -d -s reports a compliant status when all installed products are subscribable by one common service level",
			groups={"cli.tests","blockedByBug-864383","blockedByBug-865193","blockedByBug-991580"},
			priority=210,//dependsOnMethods={"VerifySystemCompliantFactWhenAllProductsSubscribableByOneCommonServiceLevel_Test"},
			enabled=true)
	//@ImplementsTCMS(id="")
	public void VerifyRhsmCompliancedWhenAllProductsSubscribableByOneCommonServiceLevel_Test() {
		if (!configureProductCertDirForAllProductsSubscribableByOneCommonServiceLevelCompleted) throw new SkipException("Unsatisfied dependency configureProductCertDirForAllProductsSubscribableByOneCommonServiceLevelCompleted="+configureProductCertDirForAllProductsSubscribableByOneCommonServiceLevelCompleted);
		verifyRhsmCompliancedWhenAllProductsAreSubscribable();
	}
	
	@Test(	description="when all installed products are subscribable by one common service level and system is compliant, auto-subscribe should abort",
			groups={"cli.tests","blockedByBug-864207"},
			priority=220,//dependsOnMethods={"VerifySystemCompliantFactWhenAllProductsSubscribableByOneCommonServiceLevel_Test"},	
			enabled=true)
	//@ImplementsTCMS(id="")
	public void VerifyAutoSubscribeAbortsWhenCompliantAndAllProductsSubscribableByOneCommonServiceLevel_Test() {
		if (!configureProductCertDirForAllProductsSubscribableByOneCommonServiceLevelCompleted) throw new SkipException("Unsatisfied dependency configureProductCertDirForAllProductsSubscribableByOneCommonServiceLevelCompleted="+configureProductCertDirForAllProductsSubscribableByOneCommonServiceLevelCompleted);
		SSHCommandResult result = clienttasks.subscribe(true, null, (List<String>)null, null, null, null, null, null, null, null, null);
		Assert.assertTrue(result.getStdout().trim().startsWith(autosubscribeCompliantMessage), "When the system is already compliant, an attempt to auto-subscribe should inform us with exactly this message: "+autosubscribeCompliantMessage);
	}
	
	@Test(	description="when the candlepin server goes offline, assert the Installed Product Status is reported from cache",
			groups={"cli.tests","VerifyListInstalledIsCachedAfterAllProductsSubscribableByOneCommonServiceLevel_Test"},
			priority=230,//dependsOnMethods={"VerifySystemCompliantFactWhenAllProductsSubscribableByOneCommonServiceLevel_Test"},
			enabled=true)
	//@ImplementsTCMS(id="")
	public void VerifyListInstalledIsCachedAfterAllProductsSubscribableByOneCommonServiceLevel_Test() {
		if (!configureProductCertDirForAllProductsSubscribableByOneCommonServiceLevelCompleted) throw new SkipException("Unsatisfied dependency configureProductCertDirForAllProductsSubscribableByOneCommonServiceLevelCompleted="+configureProductCertDirForAllProductsSubscribableByOneCommonServiceLevelCompleted);
		verifyListInstalledIsCachedWhenServerGoesOffline();
	}
	@AfterGroups(groups={"setup"},value="VerifyListInstalledIsCachedAfterAllProductsSubscribableByOneCommonServiceLevel_Test")
	public void afterVerifyListInstalledIsCachedAfterAllProductsSubscribableByOneCommonServiceLevel_Test() {
		if (serverHostname!=null) clienttasks.config(null, null, true, new String[]{"server","hostname",serverHostname});
	}
	
	
	
	
	@Test(	description="subscription-manager: verify the system.compliant fact is False when some installed products are subscribable",
			groups={"configureProductCertDirForSomeProductsSubscribable","cli.tests"},
			priority=300,
			enabled=true)
	//@ImplementsTCMS(id="")
	public void VerifySystemCompliantFactWhenSomeProductsAreSubscribable_Test() throws JSONException, Exception {
		clienttasks.register(sm_clientUsername,sm_clientPassword,sm_clientOrg,null,null,null,null,null,null,null,(String)null, null, null, null, Boolean.TRUE, false, null, null, null);
		Assert.assertFalse(clienttasks.getCurrentlyInstalledProducts().isEmpty(),
				"Products are currently installed for which the compliance of only SOME are covered by currently available subscription pools.");
		Assert.assertEquals(clienttasks.getFactValue(factNameForSystemCompliance), factValueForSystemNonCompliance,
				"Before attempting to subscribe and become compliant for all the currently installed products, the system should be non-compliant (see value for fact '"+factNameForSystemCompliance+"').");
		clienttasks.subscribeToTheCurrentlyAvailableSubscriptionPoolsCollectively();
		clienttasks.listInstalledProducts();
		Assert.assertEquals(clienttasks.getFactValue(factNameForSystemCompliance), factValueForSystemNonCompliance,
				"When a system has products installed for which only SOME are covered by available subscription pools, the system should NOT become compliant (see value for fact '"+factNameForSystemCompliance+"') even after having subscribed to every available subscription pool.");
	}
	
	@Test(	description="rhsm-complianced: verify rhsm-complianced -d -s reports a non-compliant status when some installed products are subscribable",
			groups={"cli.tests","blockedbyBug-723336","blockedbyBug-691480","blockedbyBug-846834","blockedByBug-991580"},
			priority=310,//dependsOnMethods={"VerifySystemCompliantFactWhenSomeProductsAreSubscribable_Test"},
			enabled=true)
	//@ImplementsTCMS(id="")
	public void VerifyRhsmCompliancedWhenSomeProductsAreSubscribable_Test() {
		if (!configureProductCertDirForSomeProductsSubscribableCompleted) throw new SkipException("Unsatisfied dependency configureProductCertDirForSomeProductsSubscribableCompleted="+configureProductCertDirForSomeProductsSubscribableCompleted);
		String command = clienttasks.rhsmComplianceD+" -s -d";
		RemoteFileTasks.runCommandAndWait(client, "echo 'Testing "+command+"' >> "+clienttasks.messagesLogFile, TestRecords.action());
		SSHCommandResult sshCommandResult;
		
		// verify the stdout message
		//RemoteFileTasks.runCommandAndAssert(client, command, Integer.valueOf(0), rhsmComplianceDStdoutMessageWhenNonCompliant, null);
		sshCommandResult = client.runCommandAndWait(command);
		Assert.assertEquals(sshCommandResult.getExitCode(), Integer.valueOf(0), "ExitCode from command '"+command+"'");
		Assert.assertEquals(sshCommandResult.getStdout().trim(), rhsmComplianceDStdoutMessageWhenNonCompliant, "Stdout from command '"+command+"'");
		Assert.assertEquals(sshCommandResult.getStderr().trim(), "", "Stderr from command '"+command+"'");
		
		// also verify the /var/syslog/messages
		sleep(100);	// give the message thread time to be logged
		//RemoteFileTasks.runCommandAndAssert(client,"tail -1 "+clienttasks.varLogMessagesFile, null, rhsmComplianceDSyslogMessageWhenNonCompliant, null);
		sshCommandResult = client.runCommandAndWait(command);
		Assert.assertEquals(sshCommandResult.getExitCode(), Integer.valueOf(0), "ExitCode from command '"+command+"'");
		Assert.assertEquals(sshCommandResult.getStdout().trim(), rhsmComplianceDSyslogMessageWhenNonCompliant, "Stdout from command '"+command+"'");
		Assert.assertEquals(sshCommandResult.getStderr().trim(), "", "Stderr from command '"+command+"'");
	}
	
	@Test(	description="when some installed products are subscribable and system is NOT compliant, auto-subscribing again should try but not get any new entitlements",
			groups={"cli.tests","blockedByBug-723044"},
			priority=320,//dependsOnMethods={"VerifySystemCompliantFactWhenSomeProductsAreSubscribable_Test"},
			enabled=true)
	//@ImplementsTCMS(id="")
	public void VerifyAutoSubscribeAttemptsWhenNotCompliantAndSomeProductsAreSubscribable_Test() {
		if (!configureProductCertDirForSomeProductsSubscribableCompleted) throw new SkipException("Unsatisfied dependency configureProductCertDirForSomeProductsSubscribableCompleted="+configureProductCertDirForSomeProductsSubscribableCompleted);
		List <EntitlementCert> entitlementCertsBefore = clienttasks.getCurrentEntitlementCerts();
		SSHCommandResult result = clienttasks.subscribe(true, null, (List<String>)null, null, null, null, null, null, null, null, null);
		Assert.assertTrue(!result.getStdout().trim().startsWith(autosubscribeCompliantMessage), "When the system is not compliant, an attempt to auto-subscribe should NOT inform us with this message: "+autosubscribeCompliantMessage);
		List <EntitlementCert> entitlementCertsAfter = clienttasks.getCurrentEntitlementCerts();
		Assert.assertTrue(entitlementCertsBefore.containsAll(entitlementCertsAfter)&&entitlementCertsAfter.containsAll(entitlementCertsBefore),"The entitlement certs have not changed after an attempt to autosubscribe a second time.");
		Assert.assertEquals(entitlementCertsBefore.size(), entitlementCertsAfter.size(), "The number of entitlement certs did not change after an attempt to autosubscribe a second time.");
	}
	
	@Test(	description="when the candlepin server goes offline, assert the Installed Product Status is reported from cache",
			groups={"cli.tests","VerifyListInstalledIsCachedAfterSomeProductsAreSubscribable_Test"},
			priority=330,//dependsOnMethods={"VerifySystemCompliantFactWhenSomeProductsAreSubscribable_Test"},
			enabled=true)
	//@ImplementsTCMS(id="")
	public void VerifyListInstalledIsCachedAfterSomeProductsAreSubscribable_Test() {
		if (!configureProductCertDirForSomeProductsSubscribableCompleted) throw new SkipException("Unsatisfied dependency configureProductCertDirForSomeProductsSubscribableCompleted="+configureProductCertDirForSomeProductsSubscribableCompleted);
		verifyListInstalledIsCachedWhenServerGoesOffline();
	}
	@AfterGroups(groups={"setup"},value="VerifyListInstalledIsCachedAfterSomeProductsAreSubscribable_Test")
	public void afterVerifyListInstalledIsCachedAfterSomeProductsAreSubscribable_Test() {
		if (serverHostname!=null) clienttasks.config(null, null, true, new String[]{"server","hostname",serverHostname});
	}
	
	
	
	
	@Test(	description="subscription-manager: verify the system.compliant fact is True when all installed products are subscribable",
			groups={"configureProductCertDirForAllProductsSubscribable","cli.tests"},
			priority=400,
			enabled=true)
	//@ImplementsTCMS(id="")
	public void VerifySystemCompliantFactWhenAllProductsAreSubscribable_Test() throws JSONException, Exception {
		clienttasks.register(sm_clientUsername,sm_clientPassword,sm_clientOrg,null,null,null,null,null,null,null,(String)null, null, null, null, Boolean.TRUE, false, null, null, null);
		Assert.assertFalse(clienttasks.getCurrentlyInstalledProducts().isEmpty(),
				"Products are currently installed for which the compliance of ALL are covered by currently available subscription pools.");
		Assert.assertEquals(clienttasks.getFactValue(factNameForSystemCompliance), factValueForSystemNonCompliance,
				"Before attempting to subscribe and become compliant for all the currently installed products, the system should be non-compliant (see value for fact '"+factNameForSystemCompliance+"').");
		clienttasks.subscribeToTheCurrentlyAvailableSubscriptionPoolsCollectively();
		for (InstalledProduct installedProduct : clienttasks.getCurrentlyInstalledProducts()) {
			Assert.assertEquals(installedProduct.status, "Subscribed","When config rhsm.productcertdir is populated with product certs for which ALL are covered by the currently available subscriptions, then each installed product status should be Subscribed.");
		}
		Assert.assertEquals(clienttasks.getFactValue(factNameForSystemCompliance), factValueForSystemCompliance,
				"When a system has products installed for which ALL are covered by available subscription pools, the system should become compliant (see value for fact '"+factNameForSystemCompliance+"') after having subscribed to every available subscription pool.");
	}
	
	@Test(	description="rhsm-complianced: verify rhsm-complianced -d -s reports a compliant status when all installed products are subscribable (or an appropriate warning period status if an entitlement is within its warning period status)",
			groups={"cli.tests","blockedbyBug-723336","blockedByBug-991580"},
			priority=410,//dependsOnMethods={"VerifySystemCompliantFactWhenAllProductsAreSubscribable_Test"},
			enabled=true)
	//@ImplementsTCMS(id="")
	public void VerifyRhsmCompliancedWhenAllProductsAreSubscribable_Test() {
		if (!configureProductCertDirForAllProductsSubscribableCompleted) throw new SkipException("Unsatisfied dependency configureProductCertDirForAllProductsSubscribableCompleted="+configureProductCertDirForAllProductsSubscribableCompleted);
		verifyRhsmCompliancedWhenAllProductsAreSubscribable();
	}
	
	@Test(	description="when all installed products are subscribable and system in compliant, auto-subscribe should abort",
			groups={"cli.tests","blockedByBug-864207"},
			priority=420,//dependsOnMethods={"VerifySystemCompliantFactWhenAllProductsAreSubscribable_Test"},
			enabled=true)
	//@ImplementsTCMS(id="")
	public void VerifyAutoSubscribeAbortsWhenCompliantAndAllProductsAreSubscribable_Test() {
		if (!configureProductCertDirForAllProductsSubscribableCompleted) throw new SkipException("Unsatisfied dependency configureProductCertDirForAllProductsSubscribableCompleted="+configureProductCertDirForAllProductsSubscribableCompleted);
		SSHCommandResult result = clienttasks.subscribe(true, null, (List<String>)null, null, null, null, null, null, null, null, null);
		Assert.assertTrue(result.getStdout().trim().startsWith(autosubscribeCompliantMessage), "When the system is already compliant, an attempt to auto-subscribe should inform us with exactly this message: "+autosubscribeCompliantMessage);
	}
	
	@Test(	description="when the candlepin server goes offline, assert the Installed Product Status is reported from cache",
			groups={"cli.tests","VerifyListInstalledIsCachedAfterAllProductsSubscribable_Test"},
			priority=430,//dependsOnMethods={"VerifySystemCompliantFactWhenAllProductsAreSubscribable_Test"},
			enabled=true)
	//@ImplementsTCMS(id="")
	public void VerifyListInstalledIsCachedAfterAllProductsSubscribable_Test() {
		if (!configureProductCertDirForAllProductsSubscribableCompleted) throw new SkipException("Unsatisfied dependency configureProductCertDirForAllProductsSubscribableCompleted="+configureProductCertDirForAllProductsSubscribableCompleted);
		verifyListInstalledIsCachedWhenServerGoesOffline();
	}
	@AfterGroups(groups={"setup"},value="VerifyListInstalledIsCachedAfterAllProductsSubscribable_Test")
	public void afterVerifyListInstalledIsCachedAfterAllProductsSubscribable_Test() {
		if (serverHostname!=null) clienttasks.config(null, null, true, new String[]{"server","hostname",serverHostname});
	}
	
	
	
	
	@Test(	description="subscription-manager: verify the system.compliant fact is False when no installed products are subscribable",
			groups={"configureProductCertDirForNoProductsSubscribable","cli.tests"},
			priority=500,
			enabled=true)
	//@ImplementsTCMS(id="")
	public void VerifySystemCompliantFactWhenNoProductsAreSubscribable_Test() throws JSONException, Exception {
		clienttasks.register(sm_clientUsername,sm_clientPassword,sm_clientOrg,null,null,null,null,null,null,null,(String)null, null, null, null, Boolean.TRUE, false, null, null, null);
		Assert.assertFalse(clienttasks.getCurrentlyInstalledProducts().isEmpty(),
				"Products are currently installed for which the compliance of NONE are covered by currently available subscription pools.");
		Assert.assertEquals(clienttasks.getFactValue(factNameForSystemCompliance), factValueForSystemNonCompliance,
				"Before attempting to subscribe and become compliant for all the currently installed products, the system should be non-compliant (see value for fact '"+factNameForSystemCompliance+"').");
		clienttasks.subscribeToTheCurrentlyAvailableSubscriptionPoolsCollectively();
		for (InstalledProduct installedProduct : clienttasks.getCurrentlyInstalledProducts()) {
			Assert.assertEquals(installedProduct.status, "Not Subscribed","When config rhsm.productcertdir is populated with product certs for which NONE are covered by the currently available subscriptions, then each installed product status should be Not Subscribed.");
		}
		Assert.assertEquals(clienttasks.getFactValue(factNameForSystemCompliance), factValueForSystemNonCompliance,
				"When a system has products installed for which NONE are covered by available subscription pools, the system should NOT become compliant (see value for fact '"+factNameForSystemCompliance+"') after having subscribed to every available subscription pool.");
	}
	
	@Test(	description="rhsm-complianced: verify rhsm-complianced -d -s reports a non-compliant status when no installed products are subscribable",
			groups={"cli.tests","blockedbyBug-723336","blockedbyBug-691480","blockedbyBug-846834","blockedByBug-991580"},
			priority=510,//dependsOnMethods={"VerifySystemCompliantFactWhenNoProductsAreSubscribable_Test"},
			enabled=true)
	//@ImplementsTCMS(id="")
	public void VerifyRhsmCompliancedWhenNoProductsAreSubscribable_Test() {
		if (!configureProductCertDirForNoProductsSubscribableCompleted) throw new SkipException("Unsatisfied dependency configureProductCertDirForNoProductsSubscribableCompleted="+configureProductCertDirForNoProductsSubscribableCompleted);
		String command = clienttasks.rhsmComplianceD+" -s -d";
		RemoteFileTasks.runCommandAndWait(client, "echo 'Testing "+command+"' >> "+clienttasks.messagesLogFile, TestRecords.action());
		SSHCommandResult sshCommandResult;
		
		// verify the stdout message
		//RemoteFileTasks.runCommandAndAssert(client, command, Integer.valueOf(0), rhsmComplianceDStdoutMessageWhenNonCompliant, null);
		sshCommandResult = client.runCommandAndWait(command);
		Assert.assertEquals(sshCommandResult.getExitCode(), Integer.valueOf(0), "ExitCode from command '"+command+"'");
		Assert.assertEquals(sshCommandResult.getStdout().trim(), rhsmComplianceDStdoutMessageWhenNonCompliant, "Stdout from command '"+command+"'");
		Assert.assertEquals(sshCommandResult.getStderr().trim(), "", "Stderr from command '"+command+"'");
		
		// also verify the /var/syslog/messages
		sleep(100);	// give the message thread time to be logged
		//RemoteFileTasks.runCommandAndAssert(client,"tail -1 "+clienttasks.varLogMessagesFile, null, rhsmComplianceDSyslogMessageWhenNonCompliant, null);
		sshCommandResult = client.runCommandAndWait(command);
		Assert.assertEquals(sshCommandResult.getExitCode(), Integer.valueOf(0), "ExitCode from command '"+command+"'");
		Assert.assertEquals(sshCommandResult.getStdout().trim(), rhsmComplianceDSyslogMessageWhenNonCompliant, "Stdout from command '"+command+"'");
		Assert.assertEquals(sshCommandResult.getStderr().trim(), "", "Stderr from command '"+command+"'");
	}
	
	@Test(	description="when no installed products are subscribable and system is NOT compliant, auto-subscribing again should try but not get any new entitlements",
			groups={"cli.tests","blockedByBug-723044"},
			priority=520,//dependsOnMethods={"VerifySystemCompliantFactWhenNoProductsAreSubscribable_Test"},
			enabled=true)
	//@ImplementsTCMS(id="")
	public void VerifyAutoSubscribeAttemptsWhenNotCompliantAndNoProductsAreSubscribable_Test() {
		if (!configureProductCertDirForNoProductsSubscribableCompleted) throw new SkipException("Unsatisfied dependency configureProductCertDirForNoProductsSubscribableCompleted="+configureProductCertDirForNoProductsSubscribableCompleted);
		List <EntitlementCert> entitlementCertsBefore = clienttasks.getCurrentEntitlementCerts();
		SSHCommandResult result = clienttasks.subscribe(true, null, (List<String>)null, null, null, null, null, null, null, null, null);
		Assert.assertTrue(!result.getStdout().trim().startsWith(autosubscribeCompliantMessage), "When the system is not compliant, an attempt to auto-subscribe should NOT inform us with this message: "+autosubscribeCompliantMessage);
		List <EntitlementCert> entitlementCertsAfter = clienttasks.getCurrentEntitlementCerts();
		Assert.assertTrue(entitlementCertsBefore.containsAll(entitlementCertsAfter)&&entitlementCertsAfter.containsAll(entitlementCertsBefore),"The entitlement certs have not changed after an attempt to autosubscribe a second time.");
		Assert.assertEquals(entitlementCertsBefore.size(), entitlementCertsAfter.size(), "The number of entitlement certs did not change after an attempt to autosubscribe a second time.");
	}
	
	@Test(	description="when the candlepin server goes offline, assert the Installed Product Status is reported from cache",
			groups={"cli.tests","VerifyListInstalledIsCachedAfterNoProductsAreSubscribable_Test"},
			priority=530,//dependsOnMethods={"VerifySystemCompliantFactWhenNoProductsAreSubscribable_Test"},
			enabled=true)
	//@ImplementsTCMS(id="")
	public void VerifyListInstalledIsCachedAfterNoProductsAreSubscribable_Test() {
		if (!configureProductCertDirForNoProductsSubscribableCompleted) throw new SkipException("Unsatisfied dependency configureProductCertDirForNoProductsSubscribableCompleted="+configureProductCertDirForNoProductsSubscribableCompleted);
		verifyListInstalledIsCachedWhenServerGoesOffline();
	}
	@AfterGroups(groups={"setup"},value="VerifyListInstalledIsCachedAfterNoProductsAreSubscribable_Test")
	public void afterVerifyListInstalledIsCachedAfterNoProductsAreSubscribable_Test() {
		if (serverHostname!=null) clienttasks.config(null, null, true, new String[]{"server","hostname",serverHostname});
	}
	
	
	
	
	@Test(	description="subscription-manager: verify the system.compliant fact is True when no products are installed",
			groups={"configureProductCertDirForNoProductsInstalled","cli.tests"},
			priority=600,
			enabled=true)
	//@ImplementsTCMS(id="")
	public void VerifySystemCompliantFactWhenNoProductsAreInstalled_Test() throws JSONException, Exception {
		clienttasks.register(sm_clientUsername,sm_clientPassword,sm_clientOrg,null,null,null,null,null,null,null,(String)null, null, null, null, Boolean.TRUE, false, null, null, null);
		Assert.assertTrue(clienttasks.getCurrentlyInstalledProducts().isEmpty(),
				"No products are currently installed.");
		Assert.assertEquals(clienttasks.getFactValue(factNameForSystemCompliance), factValueForSystemCompliance,
				"Because no products are currently installed, the system should inherently be compliant (see value for fact '"+factNameForSystemCompliance+"') even without subscribing to any subscription pools.");
		clienttasks.subscribeToTheCurrentlyAvailableSubscriptionPoolsCollectively();
		clienttasks.listInstalledProducts();
		Assert.assertEquals(clienttasks.getFactValue(factNameForSystemCompliance), factValueForSystemCompliance,
				"Even after subscribing to all the available subscription pools, a system with no products installed should remain compliant (see value for fact '"+factNameForSystemCompliance+"').");
	}
	
	@Test(	description="rhsm-complianced: verify rhsm-complianced -d -s reports a compliant status when no products are installed (and a warning period status when at least one entitlement cert is within its warning period)",
			groups={"cli.tests","blockedbyBug-723336","blockedByBug-991580"},
			priority=610,//dependsOnMethods={"VerifySystemCompliantFactWhenNoProductsAreInstalled_Test"},		
			enabled=true)
	//@ImplementsTCMS(id="")
	public void VerifyRhsmCompliancedWhenNoProductsAreInstalled_Test() {
		if (!configureProductCertDirForNoProductsInstalledCompleted) throw new SkipException("Unsatisfied dependency configureProductCertDirForNoProductsInstalledCompleted="+configureProductCertDirForNoProductsInstalledCompleted);
		String command = clienttasks.rhsmComplianceD+" -s -d";
		SSHCommandResult sshCommandResult;

		// verify the rhsmcomplianced status when there are entitlement certs within their warning period
		List<EntitlementCert> entitlementCertsWithinWarningPeriod = clienttasks.getCurrentEntitlementCertsWithinWarningPeriod();
		while (!entitlementCertsWithinWarningPeriod.isEmpty()) {
			// assert the rhsmcomplianced status
			//ssh root@jsefler-onprem-5server.usersys.redhat.com /usr/libexec/rhsmd -s -d
			//Stdout: System has one or more entitlements in their warning period
			//Stderr:
			//ExitCode: 0
			log.info("Asserting RhsmComplianced while at least one of the current entitlement certs is within its warning period...");
			//RemoteFileTasks.runCommandAndAssert(client, command, Integer.valueOf(0), rhsmComplianceDStdoutMessageWhenInsideWarningPeriod, null);
			sshCommandResult = client.runCommandAndWait(command);
			Assert.assertEquals(sshCommandResult.getExitCode(), Integer.valueOf(0), "ExitCode from command '"+command+"'");
			Assert.assertEquals(sshCommandResult.getStdout().trim(), rhsmComplianceDStdoutMessageWhenInsideWarningPeriod, "Stdout from command '"+command+"'");
			Assert.assertEquals(sshCommandResult.getStderr().trim(), "", "Stderr from command '"+command+"'");
			
			// unsubscribe from the serial
			EntitlementCert entitlementCert = entitlementCertsWithinWarningPeriod.remove(0);
			clienttasks.unsubscribeFromSerialNumber(entitlementCert.serialNumber);
		}
		
		// verify the stdout message
		log.info("Asserting RhsmComplianced status before unsubscribing from all currently consumed subscriptions...");
		//RemoteFileTasks.runCommandAndAssert(client, command, Integer.valueOf(0), rhsmComplianceDStdoutMessageWhenCompliant, null);
		sshCommandResult = client.runCommandAndWait(command);
		Assert.assertEquals(sshCommandResult.getExitCode(), Integer.valueOf(0), "ExitCode from command '"+command+"'");
		Assert.assertEquals(sshCommandResult.getStdout().trim(), rhsmComplianceDStdoutMessageWhenCompliant, "Stdout from command '"+command+"'");
		Assert.assertEquals(sshCommandResult.getStderr().trim(), "", "Stderr from command '"+command+"'");
		
		// also assert RhsmComplianced when not consuming any subscriptions
		log.info("Also asserting RhsmComplianced status after unsubscribing from all currently consumed subscriptions...");
		clienttasks.unsubscribeFromAllOfTheCurrentlyConsumedProductSubscriptions();
		//RemoteFileTasks.runCommandAndAssert(client, command, Integer.valueOf(0), rhsmComplianceDStdoutMessageWhenCompliant, null);
		sshCommandResult = client.runCommandAndWait(command);
		Assert.assertEquals(sshCommandResult.getExitCode(), Integer.valueOf(0), "ExitCode from command '"+command+"'");
		Assert.assertEquals(sshCommandResult.getStdout().trim(), rhsmComplianceDStdoutMessageWhenCompliant, "Stdout from command '"+command+"'");
		Assert.assertEquals(sshCommandResult.getStderr().trim(), "", "Stderr from command '"+command+"'");
		
	}
	
	@Test(	description="when no products are installed, auto-subscribe should abort",
			groups={"cli.tests","blockedByBug-864207"},
			priority=620,//dependsOnMethods={"VerifySystemCompliantFactWhenNoProductsAreInstalled_Test"},
			enabled=true)
	//@ImplementsTCMS(id="")
	public void VerifyAutoSubscribeAbortsWhenNoProductsAreInstalled_Test() {
		if (!configureProductCertDirForNoProductsInstalledCompleted) throw new SkipException("Unsatisfied dependency configureProductCertDirForNoProductsInstalledCompleted="+configureProductCertDirForNoProductsInstalledCompleted);
		SSHCommandResult result = clienttasks.subscribe_(true, null, (List<String>)null, null, null, null, null, null, null, null, null);
		//String expectedMsg = autosubscribeCompliantMessage;
		//Assert.assertTrue(result.getStdout().trim().startsWith(expectedMsg), "When the system is already compliant, an attempt to auto-subscribe should inform us with exactly this message: "+expectedMsg);
		String expectedMsg = "No Installed products on system. No need to attach subscriptions.";
		Assert.assertEquals(result.getStdout().trim(),expectedMsg, "When the there are no installed products on the system, an attempt to auto-subscribe should be intercepted.");
	}
	
	
	
	
	@Test(	description="subscription-manager: verify the system.compliant fact when system is unregistered and has installed products (should be incompliant)",
			groups={"RHNClassicTests","cli.tests"},
			priority=700,
			enabled=true)
	//@ImplementsTCMS(id="")
	public void VerifySystemCompliantFactWhenUnregistered_Test() {
		// unregister
		clienttasks.unregister(null,null,null);
		configureProductCertDirAfterClass();
		
		// pre-test check for installed products
		if (clienttasks.getCurrentlyInstalledProducts().isEmpty()) throw new SkipException("This test requires that at least one product cert is installed.");
		
		// 3/11/2013 DESIGN CHANGE DUE TO NEW SERVER-SIDE COMPLIANCE IMPLEMENTED BY dgoodwin.  NO MORE SYSTEM FACT FOR COMPLIANCE.
		log.warning("DESIGN UPDATE! Client-side compliance determination has been eliminated and moved to the Server.  Consequently, an unregistered system by definition no longer has a compliance value.  We will simply assert that the former system compliance fact '"+factNameForSystemCompliance+"' has been eliminated.");
		Assert.assertNull( clienttasks.getFacts().get(factNameForSystemCompliance), "The system's value for fact '"+factNameForSystemCompliance+"' has been eliminated.");
		if (true) return;	// the rest of this test is no longer applicable to the new server-side compliance design
		
		// first assert that we are not compliant since we have not yet registered to RHN Classic
		Assert.assertEquals(clienttasks.getFactValue(factNameForSystemCompliance), factValueForSystemNonCompliance,
				"While at least one product cert is installed and we are NOT registered to RHN Classic, the system should NOT be compliant (see value for fact '"+factNameForSystemCompliance+"').");
	}
	
	@Test(	description="subscription-manager: verify the system.compliant fact when system is already registered to RHN Classic",
			groups={"RHNClassicTests","cli.tests","blockedByBug-742027"},
			priority=710,//dependsOnMethods={"VerifySystemCompliantFactWhenUnregistered_Test"},
			enabled=true)
	//@ImplementsTCMS(id="")
	public void VerifySystemCompliantFactWhenRegisteredToRHNClassic_Test() {
		
		// simulate registration to RHN Classic by creating a /etc/sysconfig/rhn/systemid
		log.info("Simulating registration to RHN Classic by creating an empty systemid file '"+clienttasks.rhnSystemIdFile+"'...");
		RemoteFileTasks.runCommandAndWait(client, "touch "+clienttasks.rhnSystemIdFile, TestRecords.action());
		Assert.assertTrue(RemoteFileTasks.testExists(client, clienttasks.rhnSystemIdFile), "RHN Classic systemid file '"+clienttasks.rhnSystemIdFile+"' is in place.");
		
		// 3/11/2013 DESIGN CHANGE DUE TO NEW SERVER-SIDE COMPLIANCE IMPLEMENTED BY dgoodwin.  NO MORE SYSTEM FACT FOR COMPLIANCE.
		log.warning("DESIGN UPDATE! Client-side compliance determination has been eliminated and moved to the Server.  Consequently, the system compliance fact '"+factNameForSystemCompliance+"' has been eliminated.");
		log.warning("We will simply pass this test so that the subsequent VerifyRhsmCompliancedWhenRegisteredToRHNClassic_Test will be attempted.");
		if (true) return;	// the rest of this test is no longer applicable to the new server-side compliance design
		
		// now assert compliance
		Assert.assertEquals(clienttasks.getFactValue(factNameForSystemCompliance), factValueForSystemCompliance,
				"By definition, being registered to RHN Classic implies the system IS compliant no matter what products are installed (see value for fact '"+factNameForSystemCompliance+"').");
	}
	
	@Test(	description="rhsm-complianced: verify rhsm-complianced -d -s reports a compliant status when registered to RHN Classic",
			groups={"RHNClassicTests","cli.tests"},
			priority=720,//dependsOnMethods={"VerifySystemCompliantFactWhenRegisteredToRHNClassic_Test"},
			enabled=true)
	//@ImplementsTCMS(id="")
	public void VerifyRhsmCompliancedWhenRegisteredToRHNClassic_Test() {
		String command = clienttasks.rhsmComplianceD+" -s --debug";
		SSHCommandResult sshCommandResult;

		// verify the stdout message
		//RemoteFileTasks.runCommandAndAssert(client, command, Integer.valueOf(0), rhsmComplianceDStdoutMessageWhenCompliantByRHNClassic, null);
		sshCommandResult = client.runCommandAndWait(command);
		Assert.assertEquals(sshCommandResult.getExitCode(), Integer.valueOf(0), "ExitCode from command '"+command+"'");
		Assert.assertEquals(sshCommandResult.getStdout().trim(), rhsmComplianceDStdoutMessageWhenCompliantByRHNClassic, "Stdout from command '"+command+"'");
		Assert.assertEquals(sshCommandResult.getStderr().trim(), "", "Stderr from command '"+command+"'");
	}
	
	
	
	
	
	@Test(	description="subscription-manager: verify the system.compliant fact remains False when all installed products are subscribable in the future",
			groups={"configureProductCertDirForAllProductsSubscribableInTheFuture","cli.tests","blockedbyBug-737553","blockedbyBug-649068"},
			priority=800,
			enabled=true)
	//@ImplementsTCMS(id="")
	public void VerifySystemCompliantFactWhenAllProductsAreSubscribableInTheFuture_Test() throws JSONException, Exception {
		List<ProductCert> currentProductCerts = clienttasks.getCurrentProductCerts();
		clienttasks.register(sm_clientUsername,sm_clientPassword,sm_clientOrg,null,null,null,null,null,null,null,(String)null, null, null, null, Boolean.TRUE, false, null, null, null);
		
		// initial assertions
		Assert.assertFalse(clienttasks.getCurrentlyInstalledProducts().isEmpty(),
				"Products are currently installed for which the compliance of ALL are covered by future available subscription pools.");
		Assert.assertEquals(clienttasks.getFactValue(factNameForSystemCompliance), factValueForSystemNonCompliance,
				"Before attempting to subscribe to any future subscription, the system should be non-compliant (see value for fact '"+factNameForSystemCompliance+"').");
		
		// incrementally subscribe to each future subscription pool and assert the corresponding installed product's status
		for (SubscriptionPool futureSystemSubscriptionPool : futureSystemSubscriptionPools) {
			
			// subscribe without asserting results (not necessary)
			File entitlementCertFile = clienttasks.subscribeToSubscriptionPool_(futureSystemSubscriptionPool);
			List<InstalledProduct> installedProducts = clienttasks.getCurrentlyInstalledProducts();
			
			// assert that the Status of the installed product is "Future Subscription"
//			for (ProductCert productCert : clienttasks.getCurrentProductCertsProvidedBySubscriptionPool(futureSystemSubscriptionPool)) {	// TODO not efficient; testing fix on next line
			for (ProductCert productCert : clienttasks.getProductCertsProvidedBySubscriptionPool(currentProductCerts,futureSystemSubscriptionPool)) {
				InstalledProduct installedProduct = clienttasks.getInstalledProductCorrespondingToProductCert(productCert,installedProducts);
				Assert.assertEquals(installedProduct.status, "Future Subscription", "Status of the installed product '"+productCert.productName+"' after subscribing to future subscription pool: "+futureSystemSubscriptionPool);
				// TODO assert the installedProduct start/end dates
			}
		}
		
		// simply assert that we actually did subscribe every installed product to a future subscription pool
		for (InstalledProduct installedProduct : clienttasks.getCurrentlyInstalledProducts()) {
			Assert.assertEquals(installedProduct.status, "Future Subscription", "Status of every installed product should be a Future Subscription after subscribing all installed products to a future pool.  This Installed Product: "+installedProduct);
		}
		
		// finally assert that the overall system is non-compliant
		Assert.assertEquals(clienttasks.getFactValue(factNameForSystemCompliance), factValueForSystemNonCompliance,
				"When a system has products installed for which ALL are covered by future available subscription pools, the system should remain non-compliant (see value for fact '"+factNameForSystemCompliance+"') after having subscribed to every available subscription pool.");
	}
	
	@Test(	description="rhsm-complianced: verify rhsm-complianced -d -s reports a non-compliant status when all installed products are subscribable in the future",
			groups={"cli.tests","blockedByBug-991580"},
			priority=810,//dependsOnMethods={"VerifySystemCompliantFactWhenAllProductsAreSubscribableInTheFuture_Test"},
			enabled=true)
	//@ImplementsTCMS(id="")
	public void VerifyRhsmCompliancedWhenAllProductsAreSubscribableInTheFuture_Test() {
		if (!configureProductCertDirForAllProductsSubscribableInTheFutureCompleted) throw new SkipException("Unsatisfied dependency configureProductCertDirForAllProductsSubscribableInTheFutureCompleted="+configureProductCertDirForAllProductsSubscribableInTheFutureCompleted);
		String command = clienttasks.rhsmComplianceD+" -s -d";
		SSHCommandResult sshCommandResult;

		// verify the stdout message
		//RemoteFileTasks.runCommandAndAssert(client, command, Integer.valueOf(0), rhsmComplianceDStdoutMessageWhenNonCompliant, null);
		sshCommandResult = client.runCommandAndWait(command);
		Assert.assertEquals(sshCommandResult.getExitCode(), Integer.valueOf(0), "ExitCode from command '"+command+"'");
		Assert.assertEquals(sshCommandResult.getStdout().trim(), rhsmComplianceDStdoutMessageWhenNonCompliant, "Stdout from command '"+command+"'");
		Assert.assertEquals(sshCommandResult.getStderr().trim(), "", "Stderr from command '"+command+"'");
	}
	
	@Test(	description="when the candlepin server goes offline, assert the Installed Product Status is reported from cache",
			groups={"cli.tests","VerifyListInstalledIsCachedAfterAllProductsAreSubscribableInTheFuture_Test"},
			priority=830,//dependsOnMethods={"VerifySystemCompliantFactWhenAllProductsAreSubscribableInTheFuture_Test"},
			enabled=true)
	//@ImplementsTCMS(id="")
	public void VerifyListInstalledIsCachedAfterAllProductsAreSubscribableInTheFuture_Test() {
		if (!configureProductCertDirForAllProductsSubscribableInTheFutureCompleted) throw new SkipException("Unsatisfied dependency configureProductCertDirForAllProductsSubscribableInTheFutureCompleted="+configureProductCertDirForAllProductsSubscribableInTheFutureCompleted);
		verifyListInstalledIsCachedWhenServerGoesOffline();
	}
	@AfterGroups(groups={"setup"},value="VerifyListInstalledIsCachedAfterAllProductsAreSubscribableInTheFuture_Test")
	public void afterVerifyListInstalledIsCachedAfterAllProductsAreSubscribableInTheFuture_Test() {
		if (serverHostname!=null) clienttasks.config(null, null, true, new String[]{"server","hostname",serverHostname});
	}
	
	
	
	
	
	
	
	
	
	// Candidates for an automated Test:
	// TODO INVERSE OF VerifySystemCompliantFactWhenAllProductsAreSubscribableInTheFuture_Test - should not be compliant for an expired subscription https://github.com/RedHatQE/rhsm-qe/issues/120
	// TODO Bug 727967 - Compliance Assistant Valid Until Date Detection Not Working https://github.com/RedHatQE/rhsm-qe/issues/119
	
	
	
	// Protected Class Variables ***********************************************************************

	public static final String factNameForSystemCompliance = "system.entitlements_valid"; // "system.compliant"; // changed with the removal of the word "compliance" 3/30/2011
	public static final String factValueForSystemCompliance = "valid"; 			// "True"; RHEL62
	public static final String factValueForSystemNonCompliance = "invalid"; 	// "False"; RHEL62
	public static final String factValueForSystemPartialCompliance = "partial";	// "False"; RHEL62
	public static final String autosubscribeCompliantMessage = "All installed products are covered by valid entitlements. No need to update subscriptions at this time.";

	public static final String productCertDirForSomeProductsSubscribable = "/tmp/sm-someProductsSubscribable";
	public static final String productCertDirForAllProductsSubscribable = "/tmp/sm-allProductsSubscribable";
	public static final String productCertDirForNoProductsSubscribable = "/tmp/sm-noProductsSubscribable";
	public static final String productCertDirForNoProductsinstalled = "/tmp/sm-noProductsInstalled";
	public static final String productCertDirForAllProductsSubscribableInTheFuture = "/tmp/sm-allProductsSubscribableInTheFuture";
	public static final String productCertDirForAllProductsSubscribableByOneCommonServiceLevel = "/tmp/sm-allProductsSubscribableByOneCommonServiceLevel";
	public static final String productCertDirForAllProductsSubscribableByMoreThanOneCommonServiceLevel = "/tmp/sm-allProductsSubscribableByMoreThanOneCommonServiceLevel";

	public static String allProductsSubscribableByOneCommonServiceLevelValue=null;	// the value of the service_level to expect from all of the autosubscribed pools after calling configureProductCertDirForAllProductsSubscribableByOneCommonServiceLevel
	public static List<String> allProductsSubscribableByMoreThanOneCommonServiceLevelValues= new ArrayList<String>();	// the service_level values to expect subscription-manager-gui to prompt the user to choose from when autosubscribing after calling configureProductCertDirForAllProductsSubscribableByMoreThanOneCommonServiceLevel

	protected String productCertDir = null;
	protected List<SubscriptionPool> futureSystemSubscriptionPools = null;
	
	// 8/7/2013 the following rhsmComplianceDStdoutMessages changed after ckozak altered the dbus implementation to display compliance messages in the rhsm-icon
	protected final String rhsmComplianceDStdoutMessageWhenNonCompliant = "This system is missing one or more subscriptions. Please run subscription-manager for more information.";	//"System has one or more certificates that are not valid";
	protected final String rhsmComplianceDStdoutMessageWhenCompliant = "";	//"System entitlements appear valid";
	protected final String rhsmComplianceDStdoutMessageWhenInsideWarningPeriod = "This system's subscriptions are about to expire. Please run subscription-manager for more information.";	//"System has one or more entitlements in their warning period";
	protected final String rhsmComplianceDStdoutMessageWhenCompliantByRHNClassic = "System is already registered to another entitlement system\nThis system is registered to RHN Classic.";
	protected final String rhsmComplianceDSyslogMessageWhenNonCompliant = "This system is missing one or more subscriptions. Please run subscription-manager for more information.";	// "This system is missing one or more valid entitlement certificates. Please run subscription-manager for more information.";
	
	
	
	
	// Protected Methods ***********************************************************************
	
	protected String getKeyToLongestMap (Map<String,Set<String>> map) {
		int maxLength=0;
		String maxKey=null;
		for (String key : map.keySet()) {
			if (map.get(key).size()>maxLength) {
				maxLength = map.get(key).size();
				maxKey = key;
			}
		}
		return maxKey;
	}
	protected Map<String,Set<String>> getInvertedMap(Map<String,Set<String>> serviceLevelToProductIdsMap) {
		Map<String,Set<String>> productIdsToServiceLevelsMap = new HashMap<String,Set<String>>();
		for (String serviceLevel : serviceLevelToProductIdsMap.keySet()) {
			for (String productId : serviceLevelToProductIdsMap.get(serviceLevel)) {
				if (!productIdsToServiceLevelsMap.containsKey(productId)) productIdsToServiceLevelsMap.put(productId, new HashSet<String>());
				HashSet<String> serviceLevelSet = (HashSet<String>) productIdsToServiceLevelsMap.get(productId);
				serviceLevelSet.add(serviceLevel);
			}
		}
		return productIdsToServiceLevelsMap;
	}
	protected Map<String,Set<String>> getServiceLevelToProductIdsMapFromEntitlementCerts(List<EntitlementCert> entitlementCerts) {
	
		//{Standard=[37065, 27060, 37069, 37068, 37067, 37070,        37060], 
		// None    =[37060], 
		// Premium =[37065,        37069, 37068, 37067, 37070,        37060]}
		//
		//
		//{27060=[Standard],
		// 37065=[Standard, Premium],
		// 37069=[Standard, Premium],
		// 37068=[Standard, Premium],
		// 37067=[Standard, Premium],
		// 37070=[Standard, Premium],
		// 37060=[Standard, Premium, None]}
		
		// create maps of serviceLevel-to-productIds and productIds-to-serviceLevel
		Map<String,Set<String>> serviceLevelToProductIdsMap = new HashMap<String,Set<String>>();
		for (EntitlementCert entitlementCert : entitlementCerts) {
			String serviceLevel = entitlementCert.orderNamespace.supportLevel;
			
			// skip all entitlements without a service level
			if (serviceLevel==null || serviceLevel.equals("")) continue;
			
			// skip all entitlements with an exempt service level
			if (sm_exemptServiceLevelsInUpperCase.contains(serviceLevel.toUpperCase())) continue;
			
			if (!serviceLevelToProductIdsMap.containsKey(serviceLevel)) serviceLevelToProductIdsMap.put(serviceLevel, new HashSet<String>());
			HashSet<String> productIdSet = (HashSet<String>) serviceLevelToProductIdsMap.get(serviceLevel);		
			for (ProductNamespace productNamespace : entitlementCert.productNamespaces) {
//debugTesting if (productNamespace.id.equals("27060")) continue;
				productIdSet.add(productNamespace.id);
			}
		}
		return serviceLevelToProductIdsMap;
	}
	
	protected void verifyListInstalledIsCachedWhenServerGoesOffline() {
		List<InstalledProduct> installedProducts = clienttasks.getCurrentlyInstalledProducts();
		clienttasks.config(null, null, true, new String[]{"server","hostname","offline-"+serverHostname});
		Assert.assertEquals(clienttasks.identity_(null, null, null, null, null, null, null).getExitCode(), Integer.valueOf(255), "Identity fails when system is offline");
		List<InstalledProduct> installedProductsCached = clienttasks.getCurrentlyInstalledProducts();
		for (InstalledProduct installedProduct : installedProductsCached) {
			Assert.assertTrue(!installedProduct.status.equalsIgnoreCase("Unknown"),"Installed product '"+installedProduct.productName+"' status '"+installedProduct.status+"' should NOT be Unknown when server is offline.");
		}
		Assert.assertTrue(installedProductsCached.containsAll(installedProducts)&&installedProducts.containsAll(installedProductsCached),"Installed product list should remain cached when server is offline.");
	}
	
	protected void verifyRhsmCompliancedWhenAllProductsAreSubscribable() {
		String command = clienttasks.rhsmComplianceD+" -s -d";
		SSHCommandResult sshCommandResult;

		if (clienttasks.getCurrentEntitlementCertsWithinWarningPeriod().isEmpty()) {
			// otherwise verify the rhsmcomplianced status when we should be fully compliant
			//RemoteFileTasks.runCommandAndAssert(client, command, Integer.valueOf(0), rhsmComplianceDStdoutMessageWhenCompliant, null);
			sshCommandResult = client.runCommandAndWait(command);
			Assert.assertEquals(sshCommandResult.getExitCode(), Integer.valueOf(0), "ExitCode from command '"+command+"'");
			Assert.assertEquals(sshCommandResult.getStdout().trim(), rhsmComplianceDStdoutMessageWhenCompliant, "Stdout from command '"+command+"'");
			Assert.assertEquals(sshCommandResult.getStderr().trim(), "", "Stderr from command '"+command+"'");
		} else {
			// verify the rhsmcomplianced status when there are entitlement certs within their warning period
			//RemoteFileTasks.runCommandAndAssert(client, command, Integer.valueOf(0), rhsmComplianceDStdoutMessageWhenInsideWarningPeriod, null);
			sshCommandResult = client.runCommandAndWait(command);
			Assert.assertEquals(sshCommandResult.getExitCode(), Integer.valueOf(0), "ExitCode from command '"+command+"'");
			Assert.assertEquals(sshCommandResult.getStdout().trim(), rhsmComplianceDStdoutMessageWhenInsideWarningPeriod, "Stdout from command '"+command+"'");
			Assert.assertEquals(sshCommandResult.getStderr().trim(), "", "Stderr from command '"+command+"'");
		}
	}
	
	// Configuration Methods ***********************************************************************

	@AfterGroups(groups={"setup"},value="RHNClassicTests")
	public void afterRHNClassicTests() {
		if (clienttasks!=null) {
			clienttasks.removeRhnSystemIdFile();
		}
	}
	
	protected String serverHostname=null;
	@BeforeClass(groups={"setup"})
	public void saveHostnameBeforeClass() throws ParseException, JSONException, Exception {
		if (clienttasks!=null) {
			serverHostname = clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "server", "hostname");
		}
	}
	
	@BeforeClass(groups={"setup"})
	public void setupProductCertDirsBeforeClass() throws ParseException, JSONException, Exception {
		
		// clean out the productCertDirs
		for (String productCertDir : new String[]{
				productCertDirForSomeProductsSubscribable,
				productCertDirForAllProductsSubscribable,
				productCertDirForNoProductsSubscribable,
				productCertDirForNoProductsinstalled,
				productCertDirForAllProductsSubscribableInTheFuture,
				productCertDirForAllProductsSubscribableByOneCommonServiceLevel,
				productCertDirForAllProductsSubscribableByMoreThanOneCommonServiceLevel}) {
			RemoteFileTasks.runCommandAndAssert(client, "rm -rf "+productCertDir, 0);
			RemoteFileTasks.runCommandAndAssert(client, "mkdir "+productCertDir, 0);
		}

		// register and subscribe to all available subscriptions
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, true, false, null, null, null);
		clienttasks.subscribeToTheCurrentlyAvailableSubscriptionPoolsCollectively();
		
		// get the current certs
		List<EntitlementCert> currentEntitlementCerts = clienttasks.getCurrentEntitlementCerts();
		List<ProductCert> currentProductCerts = clienttasks.getCurrentProductCerts();
		List<InstalledProduct> installedProducts = clienttasks.getCurrentlyInstalledProducts();
		
		// distribute a copy of the product certs amongst the productCertDirs based on their status
		for (ProductCert productCert : currentProductCerts) {
			InstalledProduct installedProduct = clienttasks.getInstalledProductCorrespondingToProductCert(productCert,installedProducts);
			
			if (installedProduct.status.equals("Not Subscribed")) {
				// "Not Subscribed" case...
				RemoteFileTasks.runCommandAndAssert(client, "cp "+productCert.file+" "+productCertDirForNoProductsSubscribable, 0);
				RemoteFileTasks.runCommandAndAssert(client, "cp "+productCert.file+" "+productCertDirForSomeProductsSubscribable, 0);
			} else if (installedProduct.status.equals("Subscribed")) {
				// "Subscribed" case...
				RemoteFileTasks.runCommandAndAssert(client, "cp "+productCert.file+" "+productCertDirForAllProductsSubscribable, 0);
				RemoteFileTasks.runCommandAndAssert(client, "cp "+productCert.file+" "+productCertDirForSomeProductsSubscribable, 0);
			} else {
				// TODO "Partially Subscribed" case
				//InstalledProduct installedProduct = clienttasks.getInstalledProductCorrespondingToEntitlementCert(correspondingEntitlementCert);
			}
		}
		
		
		// setup for productCertDirForAllProductsSubscribableInTheFuture
		//clienttasks.unsubscribeFromAllOfTheCurrentlyConsumedProductSubscriptions();
		List<File> productCertFilesCopied = new ArrayList<File>();
		futureSystemSubscriptionPools = new ArrayList<SubscriptionPool>();
		for (List<Object> futureSystemSubscriptionPoolsDataRow : getAllFutureSystemSubscriptionPoolsDataAsListOfLists()) {
			SubscriptionPool futureSystemSubscriptionPool = (SubscriptionPool)futureSystemSubscriptionPoolsDataRow.get(0);
//			for (ProductCert productCert : clienttasks.getCurrentProductCertsProvidedBySubscriptionPool(futureSystemSubscriptionPool)) {	// TODO not efficient; testing fix on next line
			for (ProductCert productCert : clienttasks.getProductCertsProvidedBySubscriptionPool(currentProductCerts,futureSystemSubscriptionPool)) {
				if (!productCertFilesCopied.contains(productCert.file)) {
					//RemoteFileTasks.runCommandAndAssert(client, "cp -n "+productCert.file+" "+productCertDirForAllProductsSubscribableInTheFuture, 0);	// RHEL5 does not understand cp -n  
					RemoteFileTasks.runCommandAndAssert(client, "if [ ! -e "+productCertDirForAllProductsSubscribableInTheFuture+File.separator+productCert.file.getName()+" ]; then cp "+productCert.file+" "+productCertDirForAllProductsSubscribableInTheFuture+"; fi;", 0);	// no clobber copy for both RHEL5 ad RHEL6
					productCertFilesCopied.add(productCert.file);
					if (!futureSystemSubscriptionPools.contains(futureSystemSubscriptionPool)) {
						futureSystemSubscriptionPools.add(futureSystemSubscriptionPool);
					}
				}
			}
		}
		
		
		// determine the serviceLevel and all the products that are subscribable by one common service level 
//		Map<String,Set<String>> serviceLevelToProductIdsMap = getServiceLevelToProductIdsMapFromEntitlementCerts(clienttasks.getCurrentEntitlementCerts());	// TODO not efficient; testing fix on next line
		Map<String,Set<String>> serviceLevelToProductIdsMap = getServiceLevelToProductIdsMapFromEntitlementCerts(currentEntitlementCerts);
//debugTesting serviceLevelToProductIdsMap.get("Premium").add("17000");
		Map<String,Set<String>> productIdsToServiceLevelsMap = getInvertedMap(serviceLevelToProductIdsMap);
		Set<String> allProductsSubscribableByOneCommonServiceLevelCandidates = productIdsToServiceLevelsMap.keySet();
		boolean allProductsSubscribableByOneCommonServiceLevelDeterminable=true;
		OUT: do {
			String serviceLevelCandidate = getKeyToLongestMap(serviceLevelToProductIdsMap);
			Assert.assertNotNull(serviceLevelCandidate, "If the key to the longest map of serviceLevel to ProductIds is null, then there are probably no subscriptions available.");
			// does this candidate have all candidate products?
			if (serviceLevelToProductIdsMap.get(serviceLevelCandidate).containsAll(allProductsSubscribableByOneCommonServiceLevelCandidates)) {
				// is there another serviceLevel that has all candidate products?
				for (String serviceLevel : serviceLevelToProductIdsMap.keySet()) {
					if (serviceLevel.equals(serviceLevelCandidate)) continue;
					if (serviceLevelToProductIdsMap.get(serviceLevel).size()==serviceLevelToProductIdsMap.get(serviceLevelCandidate).size()) {
						allProductsSubscribableByOneCommonServiceLevelDeterminable = false;
						break OUT;
					}
				}
				allProductsSubscribableByOneCommonServiceLevelValue = serviceLevelCandidate;
				
			} else {
				// pluck the first candidate product that is not in the serviceLevelCandidate map of products
				for (String productId : (String[])allProductsSubscribableByOneCommonServiceLevelCandidates.toArray(new String[]{})) {
					if (!serviceLevelToProductIdsMap.get(serviceLevelCandidate).contains(productId)) {
						allProductsSubscribableByOneCommonServiceLevelCandidates.remove(productId);
						for (String serviceLevel : serviceLevelToProductIdsMap.keySet()) {
							serviceLevelToProductIdsMap.get(serviceLevel).remove(productId);
						}
						break;
					}
				}
			}
		} while (allProductsSubscribableByOneCommonServiceLevelValue==null && allProductsSubscribableByOneCommonServiceLevelDeterminable);
		// copy the products to productCertDirForAllProductsSubscribableByOneCommonServiceLevel
		if (allProductsSubscribableByOneCommonServiceLevelDeterminable) {
//			for (ProductCert productCert : clienttasks.getCurrentProductCerts()) {	// TODO not efficient; testing fix on next line
			for (ProductCert productCert : currentProductCerts) {
				if (allProductsSubscribableByOneCommonServiceLevelCandidates.contains(productCert.productId)) {
					RemoteFileTasks.runCommandAndAssert(client, "cp "+productCert.file+" "+productCertDirForAllProductsSubscribableByOneCommonServiceLevel, 0);
				}
			}
		} else {
			log.warning("Cannot determine a set of products where allProductsSubscribableByOneCommonServiceLevel.");
		}
		
		
		// determine the serviceLevels and all the products that are subscribable by more than one common service level
//		serviceLevelToProductIdsMap = getServiceLevelToProductIdsMapFromEntitlementCerts(clienttasks.getCurrentEntitlementCerts());	// TODO not efficient; testing fix on next line
		serviceLevelToProductIdsMap = getServiceLevelToProductIdsMapFromEntitlementCerts(currentEntitlementCerts);

		productIdsToServiceLevelsMap = getInvertedMap(serviceLevelToProductIdsMap);
		List<String> allProductsSubscribableByMoreThanOneCommonServiceLevelCandidates = new ArrayList<String>();
		for (String productId : productIdsToServiceLevelsMap.keySet()) {
			if (productIdsToServiceLevelsMap.get(productId).size() >1) allProductsSubscribableByMoreThanOneCommonServiceLevelCandidates.add(productId);
		}
		if (!allProductsSubscribableByMoreThanOneCommonServiceLevelCandidates.isEmpty()) {

			// randomly choose the service levels from the candidates
//			allProductsSubscribableByMoreThanOneCommonServiceLevelValues = Arrays.asList(productIdsToServiceLevelsMap.get(allProductsSubscribableByMoreThanOneCommonServiceLevelCandidates.get(randomGenerator.nextInt(allProductsSubscribableByMoreThanOneCommonServiceLevelCandidates.size()))).toArray(new String[]{}));
			allProductsSubscribableByMoreThanOneCommonServiceLevelValues.addAll(productIdsToServiceLevelsMap.get(allProductsSubscribableByMoreThanOneCommonServiceLevelCandidates.get(randomGenerator.nextInt(allProductsSubscribableByMoreThanOneCommonServiceLevelCandidates.size()))));
//debugTesting allProductsSubscribableByMoreThanOneCommonServiceLevelValues = Arrays.asList(new String[]{"None", "Standard", "Premium"});
			// pluck out the productIds that do not map to all of the values in allProductsSubscribableByMoreThanOneCommonServiceLevelValues
			for (String  productId : productIdsToServiceLevelsMap.keySet()) {
				if (!productIdsToServiceLevelsMap.get(productId).containsAll(allProductsSubscribableByMoreThanOneCommonServiceLevelValues)) {
					allProductsSubscribableByMoreThanOneCommonServiceLevelCandidates.remove(productId);
				}
			}
			
			// copy the products to productCertDirForAllProductsSubscribableByMoreThanOneCommonServiceLevel
//			for (ProductCert productCert : clienttasks.getCurrentProductCerts()) {	// TODO not efficient; testing fix on next line
			for (ProductCert productCert : currentProductCerts) {
				if (allProductsSubscribableByMoreThanOneCommonServiceLevelCandidates.contains(productCert.productId)) {
					RemoteFileTasks.runCommandAndAssert(client, "cp "+productCert.file+" "+productCertDirForAllProductsSubscribableByMoreThanOneCommonServiceLevel, 0);
				}
			}
		} else {
			log.warning("Cannot determine a set of products where allProductsSubscribableByMoreThanOneCommonServiceLevel.");
		}
		
		
		this.productCertDir = clienttasks.productCertDir;
	}
	
	@AfterClass(groups={"setup"},alwaysRun=true)
	public void configureProductCertDirAfterClass() {
		if (clienttasks==null) return;
		if (this.productCertDir!=null) clienttasks.updateConfFileParameter(clienttasks.rhsmConfFile, "productCertDir", this.productCertDir);
		allProductsSubscribableByOneCommonServiceLevelValue = null;
		allProductsSubscribableByMoreThanOneCommonServiceLevelValues.clear();
	}
	
	protected boolean configureProductCertDirForSomeProductsSubscribableCompleted=false;
	@BeforeGroups(groups={"setup"},value="configureProductCertDirForSomeProductsSubscribable")
	public void configureProductCertDirForSomeProductsSubscribable() {
		clienttasks.unregister(null, null, null);
		clienttasks.updateConfFileParameter(clienttasks.rhsmConfFile, "productCertDir",productCertDirForSomeProductsSubscribable);
		SSHCommandResult r0 = client.runCommandAndWait("ls -1 "+productCertDirForSomeProductsSubscribable+" | wc -l");
		SSHCommandResult r1 = client.runCommandAndWait("ls -1 "+productCertDirForAllProductsSubscribable+" | wc -l");
		SSHCommandResult r2 = client.runCommandAndWait("ls -1 "+productCertDirForNoProductsSubscribable+" | wc -l");
		if (Integer.valueOf(r1.getStdout().trim())==0) throw new SkipException("Could not find any installed product certs that are subscribable based on the currently available subscriptions.");
		if (Integer.valueOf(r2.getStdout().trim())==0) throw new SkipException("Could not find any installed product certs that are non-subscribable based on the currently available subscriptions.");
		Assert.assertTrue(Integer.valueOf(r0.getStdout().trim())>0 && Integer.valueOf(r1.getStdout().trim())>0 && Integer.valueOf(r2.getStdout().trim())>0,
				"The "+clienttasks.rhsmConfFile+" file is currently configured with a productCertDir that contains some subscribable products based on the currently available subscriptions.");
		configureProductCertDirForSomeProductsSubscribableCompleted=true;
	}
	
	protected boolean configureProductCertDirForAllProductsSubscribableCompleted=false;	
	@BeforeGroups(groups={"setup"},value="configureProductCertDirForAllProductsSubscribable")
	public void configureProductCertDirForAllProductsSubscribable() {
		clienttasks.unregister(null, null, null);
		clienttasks.updateConfFileParameter(clienttasks.rhsmConfFile, "productCertDir",productCertDirForAllProductsSubscribable);	
		SSHCommandResult r = client.runCommandAndWait("ls -1 "+productCertDirForAllProductsSubscribable+" | wc -l");
		if (Integer.valueOf(r.getStdout().trim())==0) throw new SkipException("Could not find any installed product certs that are subscribable based on the currently available subscriptions.");
		Assert.assertTrue(Integer.valueOf(r.getStdout().trim())>0,
				"The "+clienttasks.rhsmConfFile+" file is currently configured with a productCertDir that contains all subscribable products based on the currently available subscriptions.");
		configureProductCertDirForAllProductsSubscribableCompleted=true;
	}
	
	protected boolean configureProductCertDirForNoProductsSubscribableCompleted=false;
	@BeforeGroups(groups={"setup"},value="configureProductCertDirForNoProductsSubscribable")
	public void configureProductCertDirForNoProductsSubscribable() {
		clienttasks.unregister(null, null, null);
		clienttasks.updateConfFileParameter(clienttasks.rhsmConfFile, "productCertDir",productCertDirForNoProductsSubscribable);
		SSHCommandResult r = client.runCommandAndWait("ls -1 "+productCertDirForNoProductsSubscribable+" | wc -l");
		if (Integer.valueOf(r.getStdout().trim())==0) throw new SkipException("Could not find any installed product certs that are non-subscribable based on the currently available subscriptions.");
		Assert.assertTrue(Integer.valueOf(r.getStdout().trim())>0,
				"The "+clienttasks.rhsmConfFile+" file is currently configured with a productCertDir that contains all non-subscribable products based on the currently available subscriptions.");
		configureProductCertDirForNoProductsSubscribableCompleted=true;
	}
	
	protected boolean configureProductCertDirForNoProductsInstalledCompleted=false;
	@BeforeGroups(groups={"setup"},value="configureProductCertDirForNoProductsInstalled")
	public void configureProductCertDirForNoProductsInstalled() {
		clienttasks.unregister(null, null, null);
		clienttasks.updateConfFileParameter(clienttasks.rhsmConfFile, "productCertDir",productCertDirForNoProductsinstalled);
		SSHCommandResult r = client.runCommandAndWait("ls -1 "+productCertDirForNoProductsinstalled+" | wc -l");
		Assert.assertEquals(Integer.valueOf(r.getStdout().trim()),Integer.valueOf(0),
				"The "+clienttasks.rhsmConfFile+" file is currently configured with a productCertDir that contains no products.");
		configureProductCertDirForNoProductsInstalledCompleted=true;
	}
	
	protected boolean configureProductCertDirForAllProductsSubscribableInTheFutureCompleted=false;	
	@BeforeGroups(groups={"setup"},value="configureProductCertDirForAllProductsSubscribableInTheFuture")
	public void configureProductCertDirForAllProductsSubscribableInTheFuture() throws JSONException, Exception {
		clienttasks.unregister(null, null, null);
		clienttasks.updateConfFileParameter(clienttasks.rhsmConfFile, "productCertDir",productCertDirForAllProductsSubscribableInTheFuture);	
		SSHCommandResult r = client.runCommandAndWait("ls -1 "+productCertDirForAllProductsSubscribableInTheFuture+" | wc -l");
		if (Integer.valueOf(r.getStdout().trim())==0) throw new SkipException("Could not find any installed product certs that are subscribable to future available subscriptions.");
		Assert.assertTrue(Integer.valueOf(r.getStdout().trim())>0,
				"The "+clienttasks.rhsmConfFile+" file is currently configured with a productCertDir that contains all subscribable products based on future available subscriptions.");
		configureProductCertDirForAllProductsSubscribableInTheFutureCompleted=true;
	}
	
	protected boolean configureProductCertDirForAllProductsSubscribableByOneCommonServiceLevelCompleted=false;		
	@BeforeGroups(groups={"setup"},value="configureProductCertDirForAllProductsSubscribableByOneCommonServiceLevel")
	public void configureProductCertDirForAllProductsSubscribableByOneCommonServiceLevel() {
		clienttasks.unregister(null, null, null);
		clienttasks.updateConfFileParameter(clienttasks.rhsmConfFile, "productCertDir",productCertDirForAllProductsSubscribableByOneCommonServiceLevel);	
		SSHCommandResult r = client.runCommandAndWait("ls -1 "+productCertDirForAllProductsSubscribableByOneCommonServiceLevel+" | wc -l");
		if (Integer.valueOf(r.getStdout().trim())==0) throw new SkipException("Could not find any installed product certs that are autosubscribable via one common service level.");
		Assert.assertTrue(Integer.valueOf(r.getStdout().trim())>0,
				"The "+clienttasks.rhsmConfFile+" file is currently configured with a productCertDir that contains all autosubscribable products via one common service level.");
		configureProductCertDirForAllProductsSubscribableByOneCommonServiceLevelCompleted=true;
	}
	
	protected boolean configureProductCertDirForAllProductsSubscribableByMoreThanOneCommonServiceLevelCompleted=false;		
	@BeforeGroups(groups={"setup"},value="configureProductCertDirForAllProductsSubscribableByMoreThanOneCommonServiceLevel")
	public void configureProductCertDirForAllProductsSubscribableByMoreThanOneCommonServiceLevel() {
		clienttasks.unregister(null, null, null);
		clienttasks.updateConfFileParameter(clienttasks.rhsmConfFile, "productCertDir",productCertDirForAllProductsSubscribableByMoreThanOneCommonServiceLevel);	
		SSHCommandResult r = client.runCommandAndWait("ls -1 "+productCertDirForAllProductsSubscribableByMoreThanOneCommonServiceLevel+" | wc -l");
		if (Integer.valueOf(r.getStdout().trim())==0) throw new SkipException("Could not find any installed product certs that are autosubscribable via more than one common service level.");
		Assert.assertTrue(Integer.valueOf(r.getStdout().trim())>0,
				"The "+clienttasks.rhsmConfFile+" file is currently configured with a productCertDir that contains all autosubscribable products via more than one common service level.");
		configureProductCertDirForAllProductsSubscribableByMoreThanOneCommonServiceLevelCompleted=true;
	}

	// Data Providers ***********************************************************************

	@DataProvider(name="getAllProductsSubscribableByMoreThanOneCommonServiceLevelValuesData")
	public Object[][] getAllProductsSubscribableByMoreThanOneCommonServiceLevelValuesDataAs2dArray() {
		return TestNGUtils.convertListOfListsTo2dArray(getAllProductsSubscribableByMoreThanOneCommonServiceLevelValuesDataAsListOfLists());
	}
	protected List<List<Object>> getAllProductsSubscribableByMoreThanOneCommonServiceLevelValuesDataAsListOfLists(){
		List<List<Object>> ll = new ArrayList<List<Object>>();

		for (String servicelevel : allProductsSubscribableByMoreThanOneCommonServiceLevelValues) {
			ll.add(Arrays.asList(new Object[]{null,	servicelevel}));		
		}

		return ll;
	}

}


package rhsm.cli.tests;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.xmlrpc.XmlRpcException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.testng.SkipException;
import org.testng.annotations.AfterGroups;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeGroups;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.redhat.qe.Assert;
import com.redhat.qe.auto.bugzilla.BzChecker;
import com.redhat.qe.auto.tcms.ImplementsNitrateTest;
import com.redhat.qe.auto.testng.TestNGUtils;
import rhsm.base.ConsumerType;
import rhsm.base.SubscriptionManagerCLITestScript;
import rhsm.cli.tasks.CandlepinTasks;
import rhsm.cli.tasks.SubscriptionManagerTasks;
import rhsm.data.SubscriptionPool;

import com.redhat.qe.tools.RemoteFileTasks;
import com.redhat.qe.tools.SSHCommandResult;
import com.redhat.qe.tools.SSHCommandRunner;



/**
 * @author jsefler
 * 
 * Notes: on calculation of cpu.* facts <br>
Here is a breakdown of how subscription-manager uses
/sys/devices/system/cpu/cpu[num] to determine it's facts.

cpu.cpu(s): The number of /sys/devices/system/cpu/cpu[num] found.
cpu.cpu_socket(s):
   - The number of distinct socket_ids found for each cpu above.
   - Each socket_id is determined by the contents of:
/sys/devices/system/cpu/cpu[num]/topology/physical_package_id (do not
count if this doesn't exist)
   - physical_package_id does not exist on all hypervisors (xen for example)
   - If no socket_ids can be determined, assume 1.
cpu.core(s)_per_socket: Calculated from above data: cpu.cpu(s) /
cpu.cpu_socket(s)


--mstead
 
 */
@Test(groups={"FactsTests"})
public class FactsTests extends SubscriptionManagerCLITestScript{
	
	
	// Test Methods ***********************************************************************

	/**
	 * @author skallesh
	 */
	@Test(    description="subscription-manager: facts --update (when registered)",
			            groups={"MyTestFacts","blockedByBug-707525"},
			            enabled=true)
	public void FactsUpdateWhenRegistered_Test() {
			                       
		 clienttasks.register(sm_clientUsername, sm_clientPassword,sm_clientOrg, null, null, null, null, null, null, null, (List<String>)null, null,null, null, false, null, null, null, null);
		 SSHCommandResult result = clienttasks.facts(null, true,null, null, null);
	     Assert.assertEquals(result.getStdout().trim(),"Successfully updated the system facts.");
	}
	
	
	@Test(	description="subscription-manager: facts --update (when not registered)",
			groups={"blockedByBug-654429"},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void FactsUpdateWhenNotRegistered_Test() {
		
		// make sure we are not registered
		clienttasks.unregister(null, null, null);
		
		log.info("Assert that one must be registered to update the facts...");
		for (Boolean list : new Boolean[]{true,false}) {			
			SSHCommandResult result = clienttasks.facts_(list, true, null, null, null);
			Assert.assertEquals(result.getStdout().trim(),clienttasks.msg_ConsumerNotRegistered,
				"One must be registered to update the facts.");
		}
	}
	
	
	@Test(	description="subscription-manager: facts --list (when not registered)",
			groups={"blockedByBug-654429","blockedByBug-661329","blockedByBug-666544"},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void FactsListWhenNotRegistered_Test() {
		
		// make sure we are not registered
		clienttasks.unregister(null, null, null);
		
		log.info("Assert that one need not be registered to list the facts...");		
		SSHCommandResult result = clienttasks.facts(true, false, null, null, null);
		Assert.assertContainsNoMatch(result.getStderr(),clienttasks.msg_ConsumerNotRegistered,
				"One need not be registered to list the facts.");
		Assert.assertContainsNoMatch(result.getStdout(),clienttasks.msg_ConsumerNotRegistered,
				"One need not be registered to list the facts.");
	}
	
	
	@Test(	description="subscription-manager: facts (without --list or --update)",
			groups={"blockedByBug-654429"},
			enabled=false)	// was enabled before Bug 811594 - [RFE] facts command should default to list; replaced by FactsDefaultsToFactsList_Test()
	@Deprecated
	//@ImplementsNitrateTest(caseId=)
	public void FactsWithoutListOrUpdate_Test_DEPRECATED() {
		
		log.info("Assert that one need one must specify --list or --update...");		
		SSHCommandResult result = clienttasks.facts_(false, false, null, null, null);
		Assert.assertEquals(result.getExitCode(), Integer.valueOf(255),
				"exitCode from the facts without --list or --update");
		Assert.assertEquals(result.getStdout().trim(),clienttasks.msg_NeedListOrUpdateOption,
				"stdout from facts without --list or --update");
	}
	@Test(	description="subscription-manager: facts (without --list or --update) should default to --list",
			groups={"blockedByBug-811594"},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void FactsDefaultsToFactsList_Test() {
		
		SSHCommandResult listResult = clienttasks.facts(true, null, null, null, null);
		SSHCommandResult defaultResult = clienttasks.facts(null, null, null, null, null);
		
		log.info("Asserting that that the default facts result without specifying any options is the same as the result from facts --list...");
		Assert.assertEquals(defaultResult.getExitCode(), listResult.getExitCode(),
				"exitCode from facts without options should match exitCode from the facts --list");
		Assert.assertEquals(defaultResult.getStderr(), listResult.getStderr(),
				"stderr from facts without options should match stderr from the facts --list");
		// TEMPORARY WORKAROUND FOR BUG
		String bugId = "838123"; boolean invokeWorkaroundWhileBugIsOpen = true;
		try {if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");SubscriptionManagerCLITestScript.addInvokedWorkaround(bugId);} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
		if (invokeWorkaroundWhileBugIsOpen) {
			String fact = "net.interface.sit0.mac_address";
			String factRegex = "net\\.interface\\.sit0\\.mac_address: [A-F\\d:]+\\n";
			log.warning("Fact '"+fact+"' will be extracted and disregarded during the following facts list comparison since its value is not constant.");
			Assert.assertEquals(defaultResult.getStdout().replaceFirst(factRegex, ""), listResult.getStdout().replaceFirst(factRegex, ""),
					"stdout from facts without options should match stdout from the facts --list");
		} else
		// END OF WORKAROUND
		Assert.assertEquals(defaultResult.getStdout(), listResult.getStdout(),
				"stdout from facts without options should match stdout from the facts --list");
	}
	
	
	@Test(	description="subscription-manager: facts and rules: consumer facts list",
			groups={"AcceptanceTests","blockedByBug-1017299"}, dependsOnGroups={},
			dataProvider="getClientsData",
			enabled=true)
	@ImplementsNitrateTest(caseId=56386)
	public void ConsumerFactsList_Test(SubscriptionManagerTasks smt) {
		
		// start with fresh registrations using the same clientusername user
		smt.unregister(null, null, null);
		smt.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, null, false, null, null, null);
		
		// list the system facts
		smt.facts(true, false, null, null, null);
	}
	
	
	@Test(	description="subscription-manager: facts and rules: fact check RHEL distribution",
			groups={"blockedByBug-666540"}, dependsOnGroups={},
			enabled=true)
	@ImplementsNitrateTest(caseId=56329)
	public void FactCheckRhelDistribution_Test() {
		
		// skip if client1 and client2 are not a Server and Workstation distributions
		SSHCommandRunner workClient = null,servClient = null;
		SubscriptionManagerTasks workClientTasks = null, servClientTasks = null;
		if (client1!=null && client1tasks.getRedhatRelease().startsWith("Red Hat Enterprise Linux Workstation")) {
			workClient = client1; workClientTasks = client1tasks;
		}
		if (client2!=null && client2tasks.getRedhatRelease().startsWith("Red Hat Enterprise Linux Workstation")) {
			workClient = client2; workClientTasks = client2tasks;
		}
		if (client1!=null && client1tasks.getRedhatRelease().startsWith("Red Hat Enterprise Linux Server")) {
			servClient = client1; servClientTasks = client1tasks;
		}
		if (client2!=null && client2tasks.getRedhatRelease().startsWith("Red Hat Enterprise Linux Server")) {
			servClient = client2; servClientTasks = client2tasks;
		}
		if (workClient==null || servClient==null) {
			throw new SkipException("This test requires a RHEL Workstation client and a RHEL Server client.");
		}
		
		// start with fresh registrations using the same clientusername user
		workClientTasks.unregister(null, null, null);
		servClientTasks.unregister(null, null, null);
		workClientTasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, null, false, null, null, null);
		servClientTasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, null, false, null, null, null);
		

		// get all the pools available to each client
		List<SubscriptionPool> workClientPools = workClientTasks.getCurrentlyAvailableSubscriptionPools();
		List<SubscriptionPool> servClientPools = servClientTasks.getCurrentlyAvailableSubscriptionPools();
		
		log.info("Verifying that the pools available to the Workstation consumer are not identitcal to those available to the Server consumer...");
		if (!(!workClientPools.containsAll(servClientPools) || !servClientPools.containsAll(workClientPools))) {
			// TODO This testcase needs more work.  Running on different variants of RHEL alone is not enough to assert that the available pools are different.  In fact, then should be the same if the subscriptions are all set with a variant attribute of ALL
			throw new SkipException("The info message above is not accurate... The assertion that the pools available to a Workstation consumer versus a Server consumer is applicable ONLY when the org's subscriptions includes a variant aware subscription.  In fact, if the org's subscriptions are all set with a variant attribute of ALL, then the available pools should be identical.  This automated test needs some work.");
		}

		Assert.assertTrue(!workClientPools.containsAll(servClientPools) || !servClientPools.containsAll(workClientPools),
				"Because the facts of a system client running RHEL Workstation versus RHEL Server should be different, the available subscription pools to these two systems should not be the same.");

		// FIXME TODO Verify with development that these are valid asserts
		//log.info("Verifying that the pools available to the Workstation consumer do not contain Server in the ProductName...");
		//log.info("Verifying that the pools available to the Server consumer do not contain Workstation in the ProductName...");

	}
	
	@Test(	description="subscription-manager: facts and rules: check sockets",
			groups={}, dependsOnGroups={},
			dataProvider="getClientsData",
			enabled=true)
	//@ImplementsTCMS(id="")
	public void AssertPoolsWithSocketsGreaterThanSystemsCpuSocketAreNotAvailable_Test(SubscriptionManagerTasks smt) throws Exception {
		smt.unregister(null, null, null);
		String consumerId = smt.getCurrentConsumerId(smt.register(sm_clientUsername,sm_clientPassword,sm_clientOrg,null,null,null,null, null, null, null, (String)null, null, null, null, null, false, null, null, null));
		String ownerKey = CandlepinTasks.getOwnerKeyOfConsumerId(sm_clientUsername, sm_clientPassword, sm_serverUrl, consumerId);
		
		boolean foundPoolWithSocketAttributes = false;
		boolean conclusiveTest = false;
		
		// get all the pools available to each client
		List<SubscriptionPool> clientPools = smt.getCurrentlyAvailableSubscriptionPools();
		
		// get the number of cpu_sockets for this system consumer
		String factName = "cpu.cpu_socket(s)";
//TODO need a workaround for bug 696791 when getFactValue(factName)==null when getFactValue("uname.machine").equals("s390x"); should probably treat this as though socket is 1   see http://hudson.rhq.lab.eng.bos.redhat.com:8080/hudson/job/rhsm-beaker-on-premises-RHEL5/179/TestNG_Report/
		int systemValue = Integer.valueOf(smt.getFactValue(factName));
		log.info(factName+" for this system consumer: "+systemValue);
		
		// loop through the owner's subscriptions
		JSONArray jsonSubscriptions = new JSONArray(CandlepinTasks.getResourceUsingRESTfulAPI(sm_clientUsername,sm_clientPassword,sm_serverUrl,"/owners/"+ownerKey+"/subscriptions"));	
		for (int i = 0; i < jsonSubscriptions.length(); i++) {
			JSONObject jsonSubscription = (JSONObject) jsonSubscriptions.get(i);
			String poolId = jsonSubscription.getString("id");
			JSONObject jsonProduct = (JSONObject) jsonSubscription.getJSONObject("product");
			String subscriptionName = jsonProduct.getString("name");
			String productId = jsonProduct.getString("id");
			JSONArray jsonAttributes = jsonProduct.getJSONArray("attributes");
			// loop through the attributes of this subscription looking for the "sockets" attribute
			for (int j = 0; j < jsonAttributes.length(); j++) {
				JSONObject jsonAttribute = (JSONObject) jsonAttributes.get(j);
				String attributeName = jsonAttribute.getString("name");
				if (attributeName.equals("sockets")) {
					// found the sockets attribute
					foundPoolWithSocketAttributes = true;
					SubscriptionPool pool = new SubscriptionPool(productId,poolId);
					
					// get the value of the sockets attribute
					// test if the sockets attribute value is not numeric (e.g. null)
					if (jsonAttribute.isNull("value")) {
						// do not mark productAttributesPassRulesCheck = false;
						log.info("Since this sockets value is null, Subscription Pool "+pool+" may or may not be available depending on other facts besides "+factName+" (e.g. arch).");
						break;						
					}
					// test if the sockets attribute value is not numeric (e.g. "zero")
					try {Integer.valueOf(jsonAttribute.getString("value"));}
					catch (NumberFormatException e) {
						// do not mark productAttributesPassRulesCheck = false;
						log.info("Since this sockets value '"+jsonAttribute.getString("value")+"' is a non-integer, Subscription Pool "+pool+" may or may not be available depending on other facts besides "+factName+" (e.g. arch).");
						break;
					}
					int poolValue = jsonAttribute.getInt("value");
					
					// assert that if the maximum cpu_sockets for this subscription pool is greater than the cpu_sockets facts for this consumer, then this product should NOT be available
					log.fine("Maximum sockets for this subscriptionPool name="+subscriptionName+": "+poolValue);
					if (poolValue < systemValue) {
						Assert.assertFalse(clientPools.contains(pool), "Subscription Pool "+pool+" IS NOT available since this system's "+factName+" ("+systemValue+") exceeds the maximum ("+poolValue+") for this pool to be a candidate for availability.");
						conclusiveTest = true;
					} else {
						log.info("Subscription Pool "+pool+" may or may not be available depending on other facts besides "+factName+" (e.g. arch).");
					}
					break;
				}
			}
		}
		if (jsonSubscriptions.length()==0) {
			log.warning("No owner subscriptions were found for a system registered by '"+sm_clientUsername+"' and therefore we could not attempt this test.");
			throw new SkipException("No owner subscriptions were found for a system registered by '"+sm_clientUsername+"' and therefore we could not attempt this test.");		
		}
		if (!conclusiveTest) {
			//log.warning("The facts for this system did not allow us to perform a conclusive test.");
			throw new SkipException("The facts for this system did not allow us to perform a conclusive test.");
		}
		Assert.assertTrue(foundPoolWithSocketAttributes,"At least one Subscription Pool was found for which we could attempt this test.");
	}
	
	@Test(	description="subscription-manager: facts and rules: check arch",
			groups={}, dependsOnGroups={},
			dataProvider="getClientsData",
			enabled=true)
	//@ImplementsTCMS(id="")
	public void AssertPoolsWithAnArchDifferentThanSystemsArchitectureAreNotAvailable_Test(SubscriptionManagerTasks smt) throws Exception {
		smt.unregister(null, null, null);
		String consumerId = smt.getCurrentConsumerId(smt.register(sm_clientUsername,sm_clientPassword,sm_clientOrg,null,null,null,null, null, null, null, (String)null, null, null, null, null, false, null, null, null));
		String ownerKey = CandlepinTasks.getOwnerKeyOfConsumerId(sm_clientUsername, sm_clientPassword, sm_serverUrl, consumerId);

		boolean foundPoolWithArchAttributes = false;
		boolean conclusiveTest = false;
		
		// get all the pools available to this client
		List<SubscriptionPool> clientPools = smt.getCurrentlyAvailableSubscriptionPools();
		
		// get the number of cpu_sockets for this system consumer
		String factName = "cpu.architecture";
		String systemValue = smt.getFactValue(factName);
		log.info(factName+" for this system consumer: "+systemValue);
		
		// loop through the owner's subscriptions
		JSONArray jsonSubscriptions = new JSONArray(CandlepinTasks.getResourceUsingRESTfulAPI(sm_clientUsername,sm_clientPassword,sm_serverUrl,"/owners/"+ownerKey+"/subscriptions"));	
		for (int i = 0; i < jsonSubscriptions.length(); i++) {
			JSONObject jsonSubscription = (JSONObject) jsonSubscriptions.get(i);
			String poolId = jsonSubscription.getString("id");
			JSONObject jsonProduct = (JSONObject) jsonSubscription.getJSONObject("product");
			String subscriptionName = jsonProduct.getString("name");
			String productId = jsonProduct.getString("id");
			JSONArray jsonAttributes = jsonProduct.getJSONArray("attributes");
			// loop through the attributes of this subscription looking for the "sockets" attribute
			for (int j = 0; j < jsonAttributes.length(); j++) {
				JSONObject jsonAttribute = (JSONObject) jsonAttributes.get(j);
				String attributeName = jsonAttribute.getString("name");
				if (attributeName.equals("arch")) {
					// found the arch attribute - get its value
					foundPoolWithArchAttributes = true;
					String poolValue = jsonAttribute.getString("value");
					
					// assert that if the maximum cpu_sockets for this subscription pool is greater than the cpu_sockets facts for this consumer, then this product should NOT be available
					log.fine("Arch for this subscriptionPool name="+subscriptionName+": "+poolValue);
					SubscriptionPool pool = new SubscriptionPool(productId,poolId);
					if (!poolValue.equalsIgnoreCase(systemValue) && !poolValue.equalsIgnoreCase("ALL")) {
						Assert.assertFalse(clientPools.contains(pool), "Subscription Pool "+pool+" IS NOT available since this system's "+factName+" ("+systemValue+") does not match ("+poolValue+") for this pool to be a candidate for availability.");
						conclusiveTest = true;
					} else {
						log.info("Subscription Pool "+pool+" may or may not be available depending on other facts besides "+factName+".");
					}
					break;
				}
			}
		}
		if (jsonSubscriptions.length()==0) {
			log.warning("No owner subscriptions were found for a system registered by '"+sm_clientUsername+"' and therefore we could not attempt this test.");
			throw new SkipException("No owner subscriptions were found for a system registered by '"+sm_clientUsername+"' and therefore we could not attempt this test.");		
		}
		if (!conclusiveTest) {
			log.warning("The facts for this system did not allow us to perform a conclusive test.");
			throw new SkipException("The facts for this system did not allow us to perform a conclusive test.");
		}
		Assert.assertTrue(foundPoolWithArchAttributes,"At least one Subscription Pools was found for which we could attempt this test.");
	}
	
	@Test(	description="subscription-manager: facts and rules: bypass rules due to type",
			groups={"blockedByBug-641027"}, dependsOnGroups={},
			enabled=false)	// 9/17/2013 this test has been disabled in favor of new BypassRulesDueToTypeAndCapabilities_Test
							// 9/17/2013 jsefler: as originally written, this test is deficient because a new "capabilities"
							// attribute has been added to the consumer object to help the creation of manifests for downstream
							// candlepins that may not be new enough to handle subscriptions with an attribute of:
							// cores, ram, instance_multiplier, derived_product
	@ImplementsNitrateTest(caseId=56331)
	public void BypassRulesDueToType_Test() throws Exception {
		// determine which client is a RHEL Workstation
		SSHCommandRunner client = null;
		SubscriptionManagerTasks clienttasks = null;
		if (client1!=null && client1tasks.getRedhatRelease().startsWith("Red Hat Enterprise Linux Workstation")) {
			client = client1; clienttasks = client1tasks;
		} else if (client2!=null && client2tasks.getRedhatRelease().startsWith("Red Hat Enterprise Linux Workstation")) {
			client = client2; clienttasks = client2tasks;
		} else {
			throw new SkipException("This test requires a Red Hat Enterprise Linux Workstation.");
		}

		// on a RHEL workstation register to candlepin (as type system)
		clienttasks.unregister(null, null, null);
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, ConsumerType.system, null, null, null, null, null, (String)null, null, null, null, null, false, null, null, null);

		// get a list of available pools and all available pools (for this system consumer)
		List<SubscriptionPool> compatiblePoolsAsSystemConsumer = clienttasks.getCurrentlyAvailableSubscriptionPools();
		List<SubscriptionPool> allPoolsAsSystemConsumer = clienttasks.getCurrentlyAllAvailableSubscriptionPools();
		
		Assert.assertFalse(compatiblePoolsAsSystemConsumer.containsAll(allPoolsAsSystemConsumer),
				"Without bypassing the rules, not *all* pools are available for subscribing by a type=system consumer.");
		Assert.assertTrue(allPoolsAsSystemConsumer.containsAll(compatiblePoolsAsSystemConsumer),
				"The pools available to a type=system consumer is a subset of --all --available pools.");
		
		// now register to candlepin (as type candlepin)
		clienttasks.unregister(null, null, null);
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, ConsumerType.candlepin, null, null, null, null, null, (String)null, null, null, null, null, false, null, null, null);
		
		// get a list of available pools and all available pools (for this candlepin consumer)
		List<SubscriptionPool> compatiblePoolsAsCandlepinConsumer = clienttasks.getCurrentlyAvailableSubscriptionPools();
		List<SubscriptionPool> allPoolsAsCandlepinConsumer = clienttasks.getCurrentlyAllAvailableSubscriptionPools();

		Assert.assertTrue(compatiblePoolsAsCandlepinConsumer.containsAll(allPoolsAsCandlepinConsumer) && allPoolsAsCandlepinConsumer.containsAll(compatiblePoolsAsCandlepinConsumer),
				"The pools available to a type=candlepin consumer bypass the rules (list --all --available is identical to list --available).");
	
		// now assert that all the pools can be subscribed to by the consumer (registered as type candlepin)
		clienttasks.subscribeToTheCurrentlyAvailableSubscriptionPoolsCollectively();
	}
	@Test(	description="subscription-manager: facts and rules: bypass rules due to candlepin type and capabilities",
			groups={"blockedByBug-641027","BypassRulesDueToTypeAndCapabilities_Test"}, dependsOnGroups={},
			enabled=true)
	@ImplementsNitrateTest(caseId=56331)
	public void BypassRulesDueToTypeAndCapabilities_Test() throws Exception {
				
		// this list will grow in time as candlepins are programmed to handle more subscription types (I got this list from the dev team)
		//List<String> allCapabilities = Arrays.asList(new String[]{"cores", "ram", "instance_multiplier", "derived_product", "cert_v3"});
		List<String> allCapabilities = servertasks.statusCapabilities;	// we can actually get this list from the candlepin API call to /status
		allCapabilities = getRandomSubsetOfList(allCapabilities,allCapabilities.size());	// randomly reorder this list
		
		// set minimal facts
		// these facts will prevent cores, sockets, and ram from interfering with compliance based on the system arch
		Map<String,String> factsMap = new HashMap<String,String>();
		factsMap.put("cpu.cpu_socket(s)","1");
		factsMap.put("cpu.core(s)_per_socket","1");
		factsMap.put("memory.memtotal","1");
		clienttasks.createFactsFileWithOverridingValues(factsMap);
		
		// register (as type candlepin)
		String consumerId = clienttasks.getCurrentConsumerId(clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, ConsumerType.candlepin, null, null, null, null, null, (String)null, null, null, null, true, false, null, null, null));
		
		// by default, this consumer starts out with no capabilities
		JSONObject jsonConsumer = (JSONObject) new JSONObject(CandlepinTasks.getResourceUsingRESTfulAPI(sm_clientUsername, sm_clientPassword, sm_serverUrl, "/consumers/"+consumerId));
		JSONArray jsonCapabilities = jsonConsumer.getJSONArray("capabilities");

		Assert.assertTrue(jsonCapabilities.length()==0, "By default, a freshly registered consumer of type=candlepin has no capabilities.");
		
		// get the initial list of available pools and all available pools
		List<SubscriptionPool> initialAvailablePools = clienttasks.getCurrentlyAvailableSubscriptionPools();
		List<SubscriptionPool> initialAllAvailablePools = clienttasks.getCurrentlyAllAvailableSubscriptionPools();
		
		Assert.assertTrue(initialAllAvailablePools.containsAll(initialAvailablePools),
			"The pools --available to a consumer of type=candlepin is a subset of --all --available pools.");
		Assert.assertFalse(initialAvailablePools.containsAll(initialAllAvailablePools),
				"Without any capabilities, --all --available pools contains addtional pools that are not --available for consumption by a consumer of type=candlepin (Assumes some subscriptions with attributes "+allCapabilities+" are available to org "+sm_clientOrg);
		
		// incrementally give the candlepin consumer more capabilities (starting with none)
		List<String> currentCapabilities = new ArrayList<String>();
		for (int i=0; i<=allCapabilities.size(); i++) {

			// get the current list of available pools and all available pools
			List<SubscriptionPool> currentAvailablePools = clienttasks.getCurrentlyAvailableSubscriptionPools();
			List<SubscriptionPool> currentAllAvailablePools = clienttasks.getCurrentlyAllAvailableSubscriptionPools();
			
			// loop through the unavailable pools and assert that the subscription's product attribute contains a capability that is absent from this consumer
			for (SubscriptionPool pool : currentAllAvailablePools) {
				if (!currentAvailablePools.contains(pool)) {
					SubscriptionPool unavailablePool = pool;
					// the reason this pool from allAvailablePools should not be available is because it must have a product attribute that is not among the consumer's current capabilities.  Let's test it...
					boolean unavailablePoolHasAnUnavailableCapability=false;
					for (String capability : allCapabilities) {
						
						// assume the capability is a an attribute of the pool product
						if (!currentCapabilities.contains(capability)) {
							if (CandlepinTasks.getPoolProductAttributeValue(sm_clientUsername, sm_clientPassword, sm_serverUrl, pool.poolId, capability) != null) {
							Assert.assertTrue(true,"Subscription Pool '"+pool.subscriptionName+"' is not available to a consumer of type=candlepin with capabilities "+currentCapabilities+" because this pool's product attributes includes capability '"+capability+"' and therefore requires that the candlepin consumer also possess the capability '"+capability+"'.");
								unavailablePoolHasAnUnavailableCapability=true;
							}
						}
						
						// handle "derived_product" capability a little differently
						if (!currentCapabilities.contains(capability) && capability.equals("derived_product")) {
							if (CandlepinTasks.isPoolADataCenter(sm_clientUsername, sm_clientPassword, sm_serverUrl, pool.poolId)) {
								Assert.assertTrue(true,"Subscription Pool '"+pool.subscriptionName+"' is not available to a consumer of type=candlepin with capabilities "+currentCapabilities+" because this pool will derive subpools for a different product and requires that this consumer of type=candlepin to possess the capability '"+capability+"'.");
								unavailablePoolHasAnUnavailableCapability=true;
							}
						}
						
						// TODO we probably need to handle "cert_v3" capability a little differently
					}
					Assert.assertTrue(unavailablePoolHasAnUnavailableCapability,"At least one of the capability attributes present in Subscription Pool '"+pool.subscriptionName+"' is not among the current capabilities "+currentCapabilities+" of this consumer of type=candlepin. (This is why this pool appears in list --all --available and is not just list --available.)");
				}
			}
			
			// break out of the loop when we have tested all capabilities
			if (currentCapabilities.containsAll(allCapabilities)) break;
			
			// update the consumer with another capability
			currentCapabilities.add(allCapabilities.get(i));
			CandlepinTasks.setCapabilitiesForConsumer(sm_clientUsername, sm_clientPassword, sm_serverUrl, consumerId, currentCapabilities);
		
		}
		
		// now that the consumer has all capabilities, list --available and list --all --available should be identical for a candlepin consumer
		log.info("Now that this candlepin consumer supports all the available capabilities "+allCapabilities+", the list of --available pools should be identical to --all --available pools regardless of fact rules.");
		
		List<SubscriptionPool> finalAvailablePools = clienttasks.getCurrentlyAvailableSubscriptionPools();
		List<SubscriptionPool> finalAllAvailablePools = clienttasks.getCurrentlyAllAvailableSubscriptionPools();
		
		Assert.assertTrue(finalAvailablePools.containsAll(finalAllAvailablePools) && finalAllAvailablePools.containsAll(finalAvailablePools),
			"The pools --available to a consumer of type=candlepin with all capabilities "+allCapabilities+" is identical to --all --available pools.");
		Assert.assertTrue(finalAvailablePools.containsAll(initialAllAvailablePools) && initialAllAvailablePools.containsAll(finalAvailablePools),
				"The pools --available to a consumer of type=candlepin with all capabilities "+allCapabilities+" is identical to --all --available pools when the same consumer possessed no capabilities.");
		
		
		
		// now let's compare the type=candlepin's finalAllAvailablePools to a type=system's listAllAvailablePools
		// the difference in the list should be generated subpools and DOMAIN subscriptions
		log.info("Now let's register a consumer of type=system and compare its list --all --available to the type=candlepin consumer's list --all --available.");
		
		// register (as type system)
		consumerId = clienttasks.getCurrentConsumerId(clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, ConsumerType.system, null, null, null, null, null, (String)null, null, null, null, true, false, null, null, null));
		List<SubscriptionPool> allAvailablePoolsToSystem = clienttasks.getCurrentlyAllAvailableSubscriptionPools();
		List<SubscriptionPool> allAvailablePoolsToCandlepin = finalAllAvailablePools;
		
		for (SubscriptionPool pool : allAvailablePoolsToSystem) {
			if (!allAvailablePoolsToCandlepin.contains(pool)) {
				log.warning("Pool '"+pool.subscriptionName+"' is in consumer type=system list --all --available, but NOT in consumer type=candlepin list --all --available.");
				Assert.assertTrue(CandlepinTasks.isPoolDerived(sm_clientUsername, sm_clientPassword, pool.poolId, sm_serverUrl),"Pool '"+pool.subscriptionName+"' is in consumer type=system list --all --available, but NOT in consumer type=candlepin list --all --available because this is a derived pool.");
			}
		}
		for (SubscriptionPool pool : allAvailablePoolsToCandlepin) {
			if (!allAvailablePoolsToSystem.contains(pool)) {
				log.warning("Pool '"+pool.subscriptionName+"' is in candlepin list --all --available, but NOT in system list --all --available.");
				String requiresConsumerType = CandlepinTasks.getPoolProductAttributeValue(sm_clientUsername, sm_clientPassword, sm_serverUrl, pool.poolId, "requires_consumer_type");
				Assert.assertTrue(requiresConsumerType!=null && !requiresConsumerType.equals(ConsumerType.system), "Pool '"+pool.subscriptionName+"' is in candlepin list --all --available, but NOT in system list --all --available because this pool's product requires_consumer_type '"+requiresConsumerType+"'.");
			}
		}
	}
	@AfterGroups(groups={"setup"},value="BypassRulesDueToTypeAndCapabilities_Test")
	public void deleteFactsFileWithOverridingValuesAfterBypassRulesDueToTypeAndCapabilities_Test() {
		clienttasks.deleteFactsFileWithOverridingValues();
	}
	
	
	@Test(	description="subscription-manager: assert presence of the new fact cpu.topology_source use to tell us what algorithm subscription-manager employed",
			groups={"AcceptanceTests","blockedByBug-978466"}, dependsOnGroups={},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void AssertFactForCpuTopology_Test() {
		String cpuTopologyFact = "cpu.topology_source";	// this fact was invented as a result of the fix for Bug 978466 - subscription-manager fact 'cpu.cpu_socket(s)' is missing in ppc64 and s390x
		Assert.assertNotNull(clienttasks.getFactValue(cpuTopologyFact), "The '"+cpuTopologyFact+"' is set by subscription-manager to tell us what algorthm was used to determine the facts for cpu.cpu_socket(s) and cpu.core(s)_per_socket.");
	}
	
	
	@Test(	description="subscription-manager: assert that the cpu.cpu_socket(s) fact matches lscpu.socket(s)",
			groups={"AcceptanceTests","blockedByBug-707292"/*,"blockedByBug-751205","blockedByBug-978466"*//*,"blockedByBug-844532"*/}, dependsOnGroups={},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void AssertCpuCpuSocketsMatchLscpuSockets_Test() {
		boolean assertedSockets = false;
		clienttasks.deleteFactsFileWithOverridingValues();
		
		// get the facts
		Map<String, String> factsMap = clienttasks.getFacts();
		String cpuSocketsFact = "cpu.cpu_socket(s)";
		// TEMPORARY WORKAROUND FOR BUG
		if (!factsMap.containsKey(cpuSocketsFact) && (factsMap.get("uname.machine").equalsIgnoreCase("ppc64") || factsMap.get("uname.machine").equalsIgnoreCase("s390x"))) {
			boolean invokeWorkaroundWhileBugIsOpen = true;
			String bugId="978466"; // Bug 978466 - subscription-manager fact 'cpu.cpu_socket(s)' is missing in ppc64 and s390x
			try {if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");SubscriptionManagerCLITestScript.addInvokedWorkaround(bugId);} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
			if (invokeWorkaroundWhileBugIsOpen) {
				throw new SkipException("Skipping this test on '"+factsMap.get("uname.machine")+"' while bug '"+bugId+"' is open.");
			}
		}
		// END OF WORKAROUND
		Assert.assertTrue(SubscriptionManagerCLITestScript.isInteger(factsMap.get(cpuSocketsFact)) && Integer.valueOf(factsMap.get(cpuSocketsFact))>0, "Subscription manager facts '"+cpuSocketsFact+"' value '"+factsMap.get(cpuSocketsFact)+"' is a positive integer.");
		Integer cpuSockets = Integer.valueOf(factsMap.get(cpuSocketsFact));
		
		// special case for xen-dom0 hosts
		// Bug 844532 - subscription-manager register of a RHEL 5.8 Oracle SunFire x4100M2 shows 4 sockets instead of 2 in Certificate-based Management
		// related/duplicate bugs:
		// Bug 787807 - Dom0 cpu socket count is incorrect
		// Bug 785011 - Unable to register 2 CPU system
		//	[root@sun-x4100-1 ~]# hostname
		//	sun-x4100-1.gsslab.rdu2.redhat.com
		//	[root@sun-x4100-1 ~]# uname -r
		//	2.6.18-308.16.1.el5xen
		//	[root@sun-x4100-1 ~]# virt-what
		//	xen
		//	xen-dom0
		//	[root@sun-x4100-1 ~]# 
		//  TO REBOOT THIS MACHINE INTO A DIFFERENT KERNEL, vi /boot/grub/grub.conf and change the default=# based on the list; then "reboot"
		if (client.runCommandAndWait("virt-what").getStdout().contains("xen-dom0")) {
			log.warning("Detected a xen-dom0 host.  Will use dmidecode information to assert fact "+cpuSocketsFact+" as instructed by https://bugzilla.redhat.com/show_bug.cgi?id=844532#c31");
			Integer socketsCalculatedUsingDmidecode = Integer.valueOf(client.runCommandAndWait("dmidecode -t processor | grep 'Socket Designation:' | uniq | wc -l").getStdout().trim());
			Assert.assertEquals(cpuSockets, socketsCalculatedUsingDmidecode, "The fact value for '"+cpuSocketsFact+"' should match the calculation using dmidecode on a xen-dom0 host as instructed by https://bugzilla.redhat.com/show_bug.cgi?id=844532#c31");
			return;
		}
		
		// assert sockets against the socketsCalculatedUsingLscpu when lscpu is available
		if (factsMap.get("lscpu.socket(s)")!=null) {
			Integer socketsCalculatedUsingLscpu = Integer.valueOf(factsMap.get("lscpu.socket(s)"));
			Assert.assertEquals(cpuSockets, socketsCalculatedUsingLscpu, "The fact value for '"+cpuSocketsFact+"' should match the calculation using lscpu facts.");
			assertedSockets = true;
		}
		if (factsMap.get("lscpu.book(s)")!=null && factsMap.get("lscpu.socket(s)_per_book")!=null) {
			Integer socketsCalculatedUsingLscpu = Integer.valueOf(factsMap.get("lscpu.book(s)"))*Integer.valueOf(factsMap.get("lscpu.socket(s)_per_book"));
			Assert.assertEquals(cpuSockets, socketsCalculatedUsingLscpu, "The fact value for '"+cpuSocketsFact+"' should match the calculation using lscpu facts.");
			assertedSockets = true;
		}
		
		// assert sockets against the socketsCalcualtedUsingTopology (using core_siblings_list files)
		if (!assertedSockets) {
			// determine the number of cpu_socket(s) using the topology calculation
//			client.runCommandAndWait("for cpu in `ls -1 /sys/devices/system/cpu/ | egrep cpu[[:digit:]]`; do cat /sys/devices/system/cpu/$cpu/topology/core_siblings_list; done");
			String socketsCalcualtedUsingTopology = client.runCommandAndWait("for cpu in `ls -1 /sys/devices/system/cpu/ | egrep cpu[[:digit:]]`; do cat /sys/devices/system/cpu/$cpu/topology/core_siblings_list; done | sort | uniq | wc -l").getStdout().trim();
			// FIXME: This topology algorithm will fail (probably on s390x or ppc64) when the core_siblings_list contains individually disabled cores which would affect the uniq output which assumes a symmetric topology
			if (client.getStderr().isEmpty()) {
				log.info("The expected cpu_socket(s) value calculated using the topology algorithm above is '"+socketsCalcualtedUsingTopology+"'.");
				Assert.assertEquals(factsMap.get(cpuSocketsFact), socketsCalcualtedUsingTopology, "The value of system fact '"+cpuSocketsFact+"' should match the value for 'CPU socket(s)' value='"+socketsCalcualtedUsingTopology+"' as calculated using cpu topology.");
				assertedSockets = true;
			} else {
				log.warning(client.getStderr()); // alikins says this is most likely to happen on RHEL5 running on older ppc64 hardware 
			}
		}
		
		// assert sockets against the socketsCalcualtedUsingTopology (using physical_package_id files)
		if (!assertedSockets) {
			// determine the cpu_socket(s) value using the topology calculation
//			client.runCommandAndWait("for cpu in `ls -1 /sys/devices/system/cpu/ | egrep cpu[[:digit:]]`; do echo \"cpu `cat /sys/devices/system/cpu/$cpu/topology/physical_package_id`\"; done | grep cpu").getStdout().trim();
			String socketsCalcualtedUsingTopology = client.runCommandAndWait("for cpu in `ls -1 /sys/devices/system/cpu/ | egrep cpu[[:digit:]]`; do echo \"cpu `cat /sys/devices/system/cpu/$cpu/topology/physical_package_id`\"; done | grep cpu | sort | uniq | wc -l").getStdout().trim();
			if (client.getStderr().isEmpty()) {
				log.info("The expected cpu_socket(s) value calculated using the topology algorithm above is '"+socketsCalcualtedUsingTopology+"'.");
				Assert.assertEquals(factsMap.get(cpuSocketsFact), socketsCalcualtedUsingTopology, "The value of system fact '"+cpuSocketsFact+"' should match the value for 'CPU socket(s)' value='"+socketsCalcualtedUsingTopology+"' as calculated using cpu topology.");
				assertedSockets = true;
			} else {
				log.warning(client.getStderr());
			}
		}
		
		// last resort... cpu.topology_source: fallback one socket
		if (!assertedSockets) {
			// the "fallback one socket" algorithm means that we assume 1 socket per cpu, therefore let's count the number of cpus, then cpu_socket(s) should equal cpu.cpu(s)
			//String cpusFact = "cpu.cpu(s)";
			//String cpus = factsMap.get(cpusFact);
			// determine the cpu_socket(s) value using the topology calculation (counting cpus)
			String socketsCalcualtedUsingTopology = client.runCommandAndWait("ls -1 /sys/devices/system/cpu/ | egrep cpu[[:digit:]] | sort | wc -l").getStdout().trim();
			if (client.getStderr().isEmpty()) {
				log.info("The expected cpu_socket(s) value calculated using fallback algorithm above is '"+socketsCalcualtedUsingTopology+"'.");
				Assert.assertEquals(factsMap.get(cpuSocketsFact), socketsCalcualtedUsingTopology, "The value of system fact '"+cpuSocketsFact+"' should match the value for 'CPU socket(s)' value='"+socketsCalcualtedUsingTopology+"' as calculated using cpu topology.");
				assertedSockets = true;
			} else {
				log.warning(client.getStderr());
			}
		}
		
		if (!assertedSockets) Assert.fail("Could not figure out how to assert the expected number of sockets.");
	}
	
	
	@Test(	description="subscription-manager: assert that the cores calculation using facts cpu.cpu_socket(s)*cpu.core(s)_per_socket matches the cores calculation using lscpu facts",
			groups={"AcceptanceTests"/*,"blockedByBug-751205","blockedByBug-978466"*/}, dependsOnGroups={},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void AssertCoresCalculatedUsingCpuFactsMatchCoresCalculatedUsingLscpu_Test() {
		boolean assertedCores = false;
		clienttasks.deleteFactsFileWithOverridingValues();
		
		// get the facts
		Map<String, String> factsMap = clienttasks.getFacts();
		String cpuSocketsFact = "cpu.cpu_socket(s)";
		// TEMPORARY WORKAROUND FOR BUG
		if (!factsMap.containsKey(cpuSocketsFact) && (factsMap.get("uname.machine").equalsIgnoreCase("ppc64") || factsMap.get("uname.machine").equalsIgnoreCase("s390x"))) {
			boolean invokeWorkaroundWhileBugIsOpen = true;
			String bugId="978466"; // Bug 978466 - subscription-manager fact 'cpu.cpu_socket(s)' is missing in ppc64 and s390x
			try {if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");SubscriptionManagerCLITestScript.addInvokedWorkaround(bugId);} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
			if (invokeWorkaroundWhileBugIsOpen) {
				throw new SkipException("Skipping this test on '"+factsMap.get("uname.machine")+"' while bug '"+bugId+"' is open.");
			}
		}
		// END OF WORKAROUND
		Assert.assertTrue(SubscriptionManagerCLITestScript.isInteger(factsMap.get(cpuSocketsFact)) && Integer.valueOf(factsMap.get(cpuSocketsFact))>0, "Subscription manager facts '"+cpuSocketsFact+"' value '"+factsMap.get(cpuSocketsFact)+"' is a positive integer.");
		String cpuCoresPerSocketFact = "cpu.core(s)_per_socket";
		Assert.assertTrue(SubscriptionManagerCLITestScript.isInteger(factsMap.get(cpuCoresPerSocketFact)) && Integer.valueOf(factsMap.get(cpuCoresPerSocketFact))>0, "Subscription manager facts '"+cpuCoresPerSocketFact+"' value '"+factsMap.get(cpuCoresPerSocketFact)+"' is a positive integer.");
		Integer cpuCores = Integer.valueOf(factsMap.get(cpuSocketsFact))*Integer.valueOf(factsMap.get(cpuCoresPerSocketFact));
		
		// assert cpuCores against the coresCalculatedUsingLscpu when lscpu is available
		if (factsMap.get("lscpu.socket(s)")!=null && factsMap.get("lscpu.core(s)_per_socket")!=null) {
			Integer coresCalculatedUsingLscpu = Integer.valueOf(factsMap.get("lscpu.socket(s)"))*Integer.valueOf(factsMap.get("lscpu.core(s)_per_socket"));
			Assert.assertEquals(cpuCores, coresCalculatedUsingLscpu, "The total number cores as calculated using the cpu facts '"+cpuSocketsFact+"'*'"+cpuCoresPerSocketFact+"' should match the calculation using lscpu facts.");
			assertedCores = true;
		}
		if (factsMap.get("lscpu.book(s)")!=null && factsMap.get("lscpu.socket(s)_per_book")!=null && factsMap.get("lscpu.core(s)_per_socket")!=null) {
			Integer coresCalculatedUsingLscpu = Integer.valueOf(factsMap.get("lscpu.book(s)"))*Integer.valueOf(factsMap.get("lscpu.socket(s)_per_book"))*Integer.valueOf(factsMap.get("lscpu.core(s)_per_socket"));
			Assert.assertEquals(cpuCores, coresCalculatedUsingLscpu, "The total number cores as calculated using the cpu facts '"+cpuSocketsFact+"'*'"+cpuCoresPerSocketFact+"' should match the calculation using lscpu facts.");
			assertedCores = true;
		}
		
/* THIS ALGORTITHM DOES NOT ALWAYS WORK
		// assert cpuCores against the coresCalculatedUsingDmidecode when dmidecode is available
		if (!assertedCores) {
			// determine the number of cores using dmidecode information
			Integer coresCalculatedUsingDmidecode = 0;
			SSHCommandResult dmidecodeResult = client.runCommandAndWait("dmidecode -t processor");
			if (dmidecodeResult.getExitCode().equals(Integer.valueOf(0))) { // dmidecode is not always available
				if (doesStringContainMatches(dmidecodeResult.getStdout(), "Core Count")) {
					//	[jsefler@jseflerT5400 rhsm-qe]$ sudo dmidecode -t processor
					//	# dmidecode 2.11
					//	SMBIOS 2.5 present.
					//
					//	Handle 0x0400, DMI type 4, 40 bytes
					//	Processor Information
					//		Socket Designation: CPU
					//		Type: Central Processor
					//		Family: Xeon
					//		Manufacturer: Intel
					//		ID: 7A 06 01 00 FF FB EB BF
					//		Signature: Type 0, Family 6, Model 23, Stepping 10
					//		Flags:
					//			FPU (Floating-point unit on-chip)
					//			VME (Virtual mode extension)
					//			DE (Debugging extension)
					//			PSE (Page size extension)
					//			TSC (Time stamp counter)
					//			MSR (Model specific registers)
					//			PAE (Physical address extension)
					//			MCE (Machine check exception)
					//			CX8 (CMPXCHG8 instruction supported)
					//			APIC (On-chip APIC hardware supported)
					//			SEP (Fast system call)
					//			MTRR (Memory type range registers)
					//			PGE (Page global enable)
					//			MCA (Machine check architecture)
					//			CMOV (Conditional move instruction supported)
					//			PAT (Page attribute table)
					//			PSE-36 (36-bit page size extension)
					//			CLFSH (CLFLUSH instruction supported)
					//			DS (Debug store)
					//			ACPI (ACPI supported)
					//			MMX (MMX technology supported)
					//			FXSR (FXSAVE and FXSTOR instructions supported)
					//			SSE (Streaming SIMD extensions)
					//			SSE2 (Streaming SIMD extensions 2)
					//			SS (Self-snoop)
					//			HTT (Multi-threading)
					//			TM (Thermal monitor supported)
					//			PBE (Pending break enabled)
					//		Version: Not Specified
					//		Voltage: 1.1 V
					//		External Clock: 1333 MHz
					//		Max Speed: 3800 MHz
					//		Current Speed: 2000 MHz
					//		Status: Populated, Enabled
					//		Upgrade: Socket LGA771
					//		L1 Cache Handle: 0x0700
					//		L2 Cache Handle: 0x0701
					//		L3 Cache Handle: Not Provided
					//		Serial Number: Not Specified
					//		Asset Tag: Not Specified
					//		Part Number: Not Specified
					//		Core Count: 4
					//		Core Enabled: 4
					//		Thread Count: 4
					//		Characteristics:
					//			64-bit capable
					//
					//	Handle 0x0401, DMI type 4, 40 bytes
					//	Processor Information
					//		Socket Designation: CPU
					//		Type: Central Processor
					//		Family: Xeon
					//		Manufacturer: Intel
					//		ID: 7A 06 01 00 FF FB EB BF
					//		Signature: Type 0, Family 6, Model 23, Stepping 10
					//		Flags:
					//			FPU (Floating-point unit on-chip)
					//			VME (Virtual mode extension)
					//			DE (Debugging extension)
					//			PSE (Page size extension)
					//			TSC (Time stamp counter)
					//			MSR (Model specific registers)
					//			PAE (Physical address extension)
					//			MCE (Machine check exception)
					//			CX8 (CMPXCHG8 instruction supported)
					//			APIC (On-chip APIC hardware supported)
					//			SEP (Fast system call)
					//			MTRR (Memory type range registers)
					//			PGE (Page global enable)
					//			MCA (Machine check architecture)
					//			CMOV (Conditional move instruction supported)
					//			PAT (Page attribute table)
					//			PSE-36 (36-bit page size extension)
					//			CLFSH (CLFLUSH instruction supported)
					//			DS (Debug store)
					//			ACPI (ACPI supported)
					//			MMX (MMX technology supported)
					//			FXSR (FXSAVE and FXSTOR instructions supported)
					//			SSE (Streaming SIMD extensions)
					//			SSE2 (Streaming SIMD extensions 2)
					//			SS (Self-snoop)
					//			HTT (Multi-threading)
					//			TM (Thermal monitor supported)
					//			PBE (Pending break enabled)
					//		Version: Not Specified
					//		Voltage: 1.1 V
					//		External Clock: 1333 MHz
					//		Max Speed: 3800 MHz
					//		Current Speed: 2000 MHz
					//		Status: Populated, Idle
					//		Upgrade: Socket LGA771
					//		L1 Cache Handle: 0x0702
					//		L2 Cache Handle: 0x0703
					//		L3 Cache Handle: Not Provided
					//		Serial Number: Not Specified
					//		Asset Tag: Not Specified
					//		Part Number: Not Specified
					//		Core Count: 4
					//		Core Enabled: 4
					//		Thread Count: 4
					//		Characteristics:
					//			64-bit capable
					
					// sum up the value of all the Core Counts
					coresCalculatedUsingDmidecode=0;
					for (String coreCountValue : getSubstringMatches(dmidecodeResult.getStdout(), "Core Count:\\s+\\d+")) {
						coresCalculatedUsingDmidecode += Integer.valueOf(coreCountValue.split(":")[1].trim());
					}
					
					
					//	[root@jsefler-5 ~]# dmidecode -t processor
					//	# dmidecode 2.11
					//	SMBIOS 2.4 present.
					//
					//	Handle 0x0401, DMI type 4, 32 bytes
					//	Processor Information
					//		Socket Designation: CPU 1
					//		Type: Central Processor
					//		Family: Other
					//		Manufacturer: Not Specified
					//		ID: 23 06 00 00 FD FB 8B 07
					//		Version: Not Specified
					//		Voltage: Unknown
					//		External Clock: Unknown
					//		Max Speed: Unknown
					//		Current Speed: Unknown
					//		Status: Populated, Enabled
					//		Upgrade: Other
					//		L1 Cache Handle: Not Provided
					//		L2 Cache Handle: Not Provided
					//		L3 Cache Handle: Not Provided
					
					// THIS IS JUST NOT ENOUGH INFORMATION ABOVE TO FIGURE OUT THE NUMBER OF CORES
					
				}
			}
			// assert cpuCores against the coresCalculatedUsingDmidecode when dmidecode is available
			if (coresCalculatedUsingDmidecode>0) {
				Assert.assertEquals(cpuCores, coresCalculatedUsingDmidecode, "The total number cores as calculated using the cpu facts '"+cpuSocketsFact+"'*'"+cpuCoresPerSocketFact+"' should match the calculation using dmidecode data.");
				assertedCores = true;
			} else {
				log.warning("Could not figure out how to infer the number of cores from the dmidecode output above.");
			}
		}
*/
		
		// assert cpuCores against the coresCalcualtedUsingTopology
		if (!assertedCores) {
			// determine the number of cores using the topology calculation
//			client.runCommandAndWait("for cpu in `ls -1 /sys/devices/system/cpu/ | egrep cpu[[:digit:]]`; do cat /sys/devices/system/cpu/$cpu/topology/thread_siblings_list; done");
			String coresCalcualtedUsingTopology = client.runCommandAndWait("for cpu in `ls -1 /sys/devices/system/cpu/ | egrep cpu[[:digit:]]`; do cat /sys/devices/system/cpu/$cpu/topology/thread_siblings_list; done | sort | uniq | wc -l").getStdout().trim();
			if (client.getStderr().isEmpty()) {
				log.info("The expected number of cores calculated using the topology algorithm above is '"+coresCalcualtedUsingTopology+"'.");
				Assert.assertEquals(cpuCores, Integer.valueOf(coresCalcualtedUsingTopology), "The total number cores as calculated using the cpu facts '"+cpuSocketsFact+"'*'"+cpuCoresPerSocketFact+"' should match the calculation using the topology algorithm.");	
				assertedCores = true;
			} else {
				log.warning(client.getStderr());
			}
		}
		
		// last resort... cpu.topology_source: fallback one socket
		if (!assertedCores) {
			// the "fallback one socket" algorithm means that we assume 1 core per socket and 1 socket per cpu, therefore let's count the number of cpus, then cpuCores should equal cpu.cpu(s)
			//String cpusFact = "cpu.cpu(s)";
			//String cpus = factsMap.get(cpusFact);
			// determine the cores value using the topology calculation (counting cpus)
			String coresCalcualtedUsingTopology = client.runCommandAndWait("ls -1 /sys/devices/system/cpu/ | egrep cpu[[:digit:]] | sort | wc -l").getStdout().trim();
			if (client.getStderr().isEmpty()) {
				log.info("The expected number of cores calculated using the fallback topology algorithm above is '"+coresCalcualtedUsingTopology+"'.");
				Assert.assertEquals(cpuCores, Integer.valueOf(coresCalcualtedUsingTopology), "The total number cores as calculated using the cpu facts '"+cpuSocketsFact+"'*'"+cpuCoresPerSocketFact+"' should match the calculation using the topology algorithm.");	
				assertedCores = true;
			} else {
				log.warning(client.getStderr());
			}
		}
		
		if (!assertedCores) Assert.fail("Could not figure out how to assert the expected number of cores.");
	}

	
	@Test(	description="when registering to an existing consumerid, the facts for the system should be updated automatically upon registering",
			groups={"blockedByBug-810236"}, dependsOnGroups={},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void AssertFactsUpdateWhenRegisteringWithConsumerId_Test() throws JSONException, Exception {
		if (client1==null || client2==null) throw new SkipException("This test requires two clients.");

		// give client1 a custom fact
		client1tasks.deleteFactsFileWithOverridingValues();
		Map<String,String> customFactsMap = new HashMap<String,String>();
		String client1CustomFactName = "custom.fact.client1";
		customFactsMap.clear();
		customFactsMap.put(client1CustomFactName,client1tasks.hostname);
		client1tasks.createFactsFileWithOverridingValues(customFactsMap);
		
		// register client1 and get the original facts for consumerid from client1
		String consumerId = client1tasks.getCurrentConsumerId(client1tasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, Boolean.TRUE, null, null, null, null));
		Map<String,String> client1FactsMap = client1tasks.getFacts();
		
		// get consumerid's facts from Candlepin
		Map<String,String> consumer1FactsMap = CandlepinTasks.getConsumerFacts(sm_clientUsername, sm_clientPassword, sm_serverUrl, consumerId);
		JSONObject jsonConsumer = new JSONObject (CandlepinTasks.getResourceUsingRESTfulAPI(sm_clientUsername, sm_clientPassword, sm_serverUrl, "/consumers/"+consumerId));
		log.info("Consumer '"+consumerId+"' facts on the candlepin server are: \n"+jsonConsumer.getJSONObject("facts").toString(5));
		
		// assert that the candlepin's view of consumerid's facts are identical to the local client1's system facts
		Assert.assertTrue(doSystemFactsMatchConsumerFacts(consumerId, client1FactsMap, consumer1FactsMap),"The facts on consumer '"+consumerId+"' known to the candlepin server are equivalent to the subscription-manager facts --list on client system '"+client1tasks.hostname+"'.");
		
		client1tasks.clean(null,null,null);
		
		
		// give client2 a custom fact
		client2tasks.deleteFactsFileWithOverridingValues();
		String client2CustomFactName = "custom.fact.client2";
		customFactsMap.clear();
		customFactsMap.put(client2CustomFactName,client2tasks.hostname);
		client2tasks.createFactsFileWithOverridingValues(customFactsMap);
		
		// register client2 to the existing consumerid and get the facts from client2
		Assert.assertEquals(client2tasks.getCurrentConsumerId(client2tasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, consumerId, null, null, null, (String)null, null, null, null, null, null, null, null, null)), consumerId, "Registering to an existing consumerId should return the same consumerId.");
		Map<String,String> client2FactsMap = client2tasks.getFacts();

		// get consumerid's facts from Candlepin again
		Map<String,String> consumer2FactsMap = CandlepinTasks.getConsumerFacts(sm_clientUsername, sm_clientPassword, sm_serverUrl, consumerId);
		jsonConsumer = new JSONObject (CandlepinTasks.getResourceUsingRESTfulAPI(sm_clientUsername, sm_clientPassword, sm_serverUrl, "/consumers/"+consumerId));
		log.info("Consumer '"+consumerId+"' facts on the candlepin server are now: \n"+jsonConsumer.getJSONObject("facts").toString(5));

		// now assert that candlepin's view of the consumerid facts has been automatically updated to those from client2 system facts who just registered to existing consumer
		Assert.assertTrue(doSystemFactsMatchConsumerFacts(consumerId, client2FactsMap, consumer2FactsMap),"The facts on consumer '"+consumerId+"' known to the candlepin server have automatically been updated after client system '"+client2tasks.hostname+"' registered using an existing consumerId.");
		Assert.assertTrue(!consumer2FactsMap.containsKey(client1CustomFactName),"After client2 "+client2tasks.hostname+" registered to existing consumerId '"+consumerId+"', the original custom fact '"+client1CustomFactName+"' set by original client1 system '"+client1tasks.hostname+"' is has been automatically cleaned from the consumer facts known on the candlepin server.");
	}
	
	
	@Test(	description="the facts for net.interface.sit0.mac_address and net.interface.lo.mac_address should not be listed",
			groups={"blockedByBug-838123","blockedByBug-866645"}, dependsOnGroups={},
			enabled=true)	// TODO re-implement this test after fix for Bug 866645
	//@ImplementsNitrateTest(caseId=)
	public void AssertFactsForNetInterfaceMacAddress_Test() {
		
		Map<String,String> clientFactsMap = clienttasks.getFacts();
		for (String macAddressFact : new String[]{"net.interface.sit0.mac_address","net.interface.lo.mac_address"}) {
			Assert.assertNull(clientFactsMap.get(macAddressFact), "After fix for bug 838123, the '"+macAddressFact+"' fact should not exist.");
		}
	}

	
	protected String rhsm_report_package_profile = null;
	@BeforeGroups(value={"EnablementOfReportPackageProfile_Test"}, groups={"setup"})
	public void beforeEnablementOfReportPackageProfile_Test() {
		if (clienttasks==null) return;
		rhsm_report_package_profile	= clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "rhsm", "report_package_profile");
	}
	@Test(	description="subscription-manager: assert the ability to enable/disable the reporting of the consumers package profile",
			groups={"EnablementOfReportPackageProfile_Test","blockedByBug-905922"},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void EnablementOfReportPackageProfile_Test() throws JSONException, Exception {
		boolean isPackagesSupported = servertasks.isPackagesSupported(sm_clientUsername, sm_clientPassword, sm_serverUrl);
		String packagesNotSupportedLogMsg = "Server does not support packages, skipping profile upload.";
		String reportPackageProfileOffLogMsg = "Skipping package profile upload due to report_package_profile setting.";
		String rhsmLogMarker;
		SSHCommandResult sshCommandResult;
		
		// turn on the rhsm.report_package_profile configuration and assert the rhsm logging based on isPackagesSupported on the server during a registration
		sshCommandResult = clienttasks.config(null, null, true, new String[]{"rhsm","report_package_profile","1"});
		rhsmLogMarker = System.currentTimeMillis()+" Testing EnablementOfReportPackageProfile_Test during a register...";
		RemoteFileTasks.markFile(client, clienttasks.rhsmLogFile, rhsmLogMarker);
		client1tasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, Boolean.TRUE, null, null, null, null);
		if (isPackagesSupported) {
			Assert.assertTrue(RemoteFileTasks.getTailFromMarkedFile(client, clienttasks.rhsmLogFile, rhsmLogMarker, packagesNotSupportedLogMsg).trim().isEmpty(), "When the entitlements server supports package upload, this message should NOT be logged to "+clienttasks.rhsmLogFile+": "+packagesNotSupportedLogMsg);
			Assert.assertTrue(RemoteFileTasks.getTailFromMarkedFile(client, clienttasks.rhsmLogFile, rhsmLogMarker, reportPackageProfileOffLogMsg).trim().isEmpty(), "When the entitlements server supports package upload and rhsm.report_package_profile is turned on , this message should NOT be logged to "+clienttasks.rhsmLogFile+": "+reportPackageProfileOffLogMsg);
		} else {
			Assert.assertTrue(!RemoteFileTasks.getTailFromMarkedFile(client, clienttasks.rhsmLogFile, rhsmLogMarker, packagesNotSupportedLogMsg).trim().isEmpty(), "Regardless of the rhsm.report_package_profile value, when the entitlements server does not support package upload, this expected message is logged to "+clienttasks.rhsmLogFile+": "+packagesNotSupportedLogMsg);
		}
		
		// turn off the rhsm.report_package_profile configuration and assert the rhsm logging based on isPackagesSupported on the server during a registration
		sshCommandResult = clienttasks.config(null, null, true, new String[]{"rhsm","report_package_profile","0"});
		rhsmLogMarker = System.currentTimeMillis()+" Testing DisablementOfReportPackageProfile_Test during a register...";
		RemoteFileTasks.markFile(client, clienttasks.rhsmLogFile, rhsmLogMarker);
		client1tasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, Boolean.TRUE, null, null, null, null);
		if (isPackagesSupported) {
			Assert.assertTrue(RemoteFileTasks.getTailFromMarkedFile(client, clienttasks.rhsmLogFile, rhsmLogMarker, packagesNotSupportedLogMsg).trim().isEmpty(), "When the entitlements server supports package upload, this message should NOT be logged to "+clienttasks.rhsmLogFile+": "+packagesNotSupportedLogMsg);
			Assert.assertTrue(!RemoteFileTasks.getTailFromMarkedFile(client, clienttasks.rhsmLogFile, rhsmLogMarker, reportPackageProfileOffLogMsg).trim().isEmpty(), "When the entitlements server supports package upload and rhsm.report_package_profile is turned off , this expected message should be logged to "+clienttasks.rhsmLogFile+": "+reportPackageProfileOffLogMsg);
		} else {
			Assert.assertTrue(!RemoteFileTasks.getTailFromMarkedFile(client, clienttasks.rhsmLogFile, rhsmLogMarker, packagesNotSupportedLogMsg).trim().isEmpty(), "Regardless of the rhsm.report_package_profile value, when the entitlements server does not support package upload, message '"+packagesNotSupportedLogMsg+"' is logged to "+clienttasks.rhsmLogFile+".");
		}
		
		if (!isPackagesSupported) throw new SkipException("Only limited testing of the rhsm.report_package_profile enablement parameter can be achieved when the entitlement server does not support uploading the package profile of a consumer.  This testing should be done against a Katello server.");
		
		// TODO when the server is a katello server that supports packages, we should also test these scenarios
		// 1. register with report_package_profile=1 and call http://www.katello.org/apidoc/ GET /api/systems/:id/packages and assert the list matches all the rpms installed
		// 2. register with report_package_profile=0 and call http://www.katello.org/apidoc/ GET /api/systems/:id/packages and assert the list is empty
		// 3. after registered with report_package_profile=1, add a package and remove a package, call facts update (or rhsm check) and verify GET /api/systems/:id/packages and assert the list matches the new rpms installed
		// 4. after registered with report_package_profile=0, add a package and remove a package, call facts update (or rhsm check) and verify GET /api/systems/:id/packages and assert the list was unchanged
	}
	@AfterGroups(value={"EnablementOfReportPackageProfile_Test"},groups={"setup"})
	public void afterEnablementOfReportPackageProfile_Test() {
		if (rhsm_report_package_profile!=null) clienttasks.config(null, null, true, new String[]{"rhsm","report_package_profile",rhsm_report_package_profile});
	}
	
	
	@Test(	description="verify the fact value for system.certificate_version which tells the candlepin server the maximum entitlement certificate version this system knows how to consume. ",
			groups={"blockedByBug-957218"}, dependsOnGroups={},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void AssertFactForSystemCertificateVersion_Test() {
		String actualSystemCertificateVersion = clienttasks.getFactValue("system.certificate_version");
		String expectedSystemCertificateVersion = "3.2";
		
		if (clienttasks.installedPackageVersion.get("subscription-manager").startsWith("subscription-manager-0.")) {
			expectedSystemCertificateVersion = null;
		}
		if (clienttasks.installedPackageVersion.get("subscription-manager").startsWith("subscription-manager-1.0.")) {
			expectedSystemCertificateVersion = "3.0";
		}
		if (clienttasks.installedPackageVersion.get("subscription-manager").startsWith("subscription-manager-1.1.")) {
			expectedSystemCertificateVersion = "3.1";
		}
		if (clienttasks.installedPackageVersion.get("subscription-manager").startsWith("subscription-manager-1.8.")) {
			expectedSystemCertificateVersion = "3.2";
		}
		Assert.assertEquals(actualSystemCertificateVersion, expectedSystemCertificateVersion,"fact value for system.certificate_version");
	}
	
	
	@Test(	description="subscription-manager: subscription-manager should handle malformed custom facts with grace",
			groups={"MalformedCustomFacts_Test","blockedByBug-966747"},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void MalformedCustomFacts_Test() {
		File malformedFactsFile = new File(clienttasks.factsDir+File.separatorChar+malformedFactsFilename);
		
		// mark the rhsm.log file
//		client.runCommandAndWait("rm -f "+clienttasks.rhsmLogFile);	// remove it because it occasionally gets backed up to rhsm.log.1 in the midst of a pair of calls to RemoteFileTasks.markFile(...) and RemoteFileTasks.getTailFromMarkedFile(...)
		String logMarker = System.currentTimeMillis()+" Testing MalformedCustomFacts_Test...";
		RemoteFileTasks.markFile(client, clienttasks.rhsmLogFile, logMarker);

		// create malformed facts
		Map<String,String> customFactsMap = new HashMap<String,String>();
		customFactsMap.put("malformed_fact","value\" is \"misquoted");
		clienttasks.createFactsFileWithOverridingValues(malformedFactsFilename,customFactsMap);
		log.info("Here is the contents of our malformed custom facts file...");
		client.runCommandAndWait("cat "+malformedFactsFile);
		
		// attempt to register
		SSHCommandResult result = clienttasks.register/*_*/(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, Boolean.TRUE, null, null, null, null);
		
		/* ORIGINAL FAILURE
		//	[root@jsefler-5 ~]# subscription-manager register --username=testuser1 --password=password --org=admin --force
		//	Expecting , delimiter: line 1 column 26 (char 26)
		
		// assert results
		Assert.assertEquals(result.getExitCode(),Integer.valueOf(0),"Exitcode from an attempt to register with malformed facts file '"+malformedFactsFile+"'.");
		Assert.assertEquals(result.getStdout(),"FIXME Need expected stdout message after bug 966747 is fixed","Stdout from an attempt to register with malformed facts file '"+malformedFactsFile+"'.");
		Assert.assertEquals(result.getStderr(),"FIXME Need expected stderr message after bug 966747 is fixed","Stdout from an attempt to register with malformed facts file '"+malformedFactsFile+"'.");
		*/
		
		/* FIXED BEHAVIOR */
		// 2013-06-07 13:31:08,916 [WARNING]  @facts.py:125 - Unable to load custom facts file: /etc/rhsm/facts/malformed.facts
		// get the tail of the marked rhsm.log file and assert
		String logTail = RemoteFileTasks.getTailFromMarkedFile(client, clienttasks.rhsmLogFile, logMarker, "WARNING").trim();
		String expectedLogMessage = "Unable to load custom facts file: "+malformedFactsFile;
		Assert.assertTrue(logTail.contains(expectedLogMessage), "The '"+clienttasks.rhsmLogFile+"' reports expected log message '"+expectedLogMessage+"'.");

	}
	@AfterGroups(value={"MalformedCustomFacts_Test"},groups={"setup"})
	public void afterMalformedCustomFacts_Test() {
		if (clienttasks==null) return;
		clienttasks.deleteFactsFileWithOverridingValues(malformedFactsFilename);
	}
	private final String malformedFactsFilename = "malformed.facts";
	
	
	@Test(	description="subscription-manager: subscription-manager should handle empty custom facts with grace",
			groups={"EmptyCustomFacts_Test","blockedByBug-966747"},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void EmptyCustomFacts_Test() {
		File emptyFactsFile = new File(clienttasks.factsDir+File.separatorChar+emptyFactsFilename);
		
		// create empty facts
		client.runCommandAndWait("rm -f "+emptyFactsFile+" && touch "+emptyFactsFile);
		Assert.assertTrue(RemoteFileTasks.testExists(client, emptyFactsFile.getPath()), "The empty facts file should exist.");
		
		// mark the rhsm.log file
//		client.runCommandAndWait("rm -f "+clienttasks.rhsmLogFile);	// remove it because it occasionally gets backed up to rhsm.log.1 in the midst of a pair of calls to RemoteFileTasks.markFile(...) and RemoteFileTasks.getTailFromMarkedFile(...)
		String logMarker = System.currentTimeMillis()+" Testing EmptyCustomFacts_Test...";
		RemoteFileTasks.markFile(client, clienttasks.rhsmLogFile, logMarker);
		
		// attempt to register
		SSHCommandResult result = clienttasks.register/*_*/(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, Boolean.TRUE, null, null, null, null);
		
		/* ORIGINAL FAILURE
		//	[root@jsefler-5 ~]# subscription-manager register --username=testuser1 --password=password --org=admin --force
		//	No JSON object could be decoded
		
		// assert results
		Assert.assertEquals(result.getExitCode(),Integer.valueOf(0),"Exitcode from an attempt to register with an empty facts file '"+emptyFactsFile+"'.");
		Assert.assertEquals(result.getStdout(),"FIXME Need expected stdout message after bug 966747 is fixed","Stdout from an attempt to register with an empty facts file '"+emptyFactsFile+"'.");
		Assert.assertEquals(result.getStderr(),"FIXME Need expected stderr message after bug 966747 is fixed","Stdout from an attempt to register with an empty facts file '"+emptyFactsFile+"'.");
		*/
		
		/* FIXED BEHAVIOR */
		// 2013-06-07 13:31:08,916 [WARNING]  @facts.py:125 - Unable to load custom facts file: /etc/rhsm/facts/empty.facts
		// get the tail of the marked rhsm.log file and assert
		String logTail = RemoteFileTasks.getTailFromMarkedFile(client, clienttasks.rhsmLogFile, logMarker, "WARNING").trim();
		String expectedLogMessage = "Unable to load custom facts file: "+emptyFactsFile;
		Assert.assertTrue(logTail.contains(expectedLogMessage), "The '"+clienttasks.rhsmLogFile+"' reports expected log message '"+expectedLogMessage+"'.");

	}
	@AfterGroups(value={"EmptyCustomFacts_Test"},groups={"setup"})
	public void afterEmptyCustomFacts_Test() {
		if (clienttasks==null) return;
		clienttasks.deleteFactsFileWithOverridingValues(emptyFactsFilename);
	}
	private final String emptyFactsFilename = "empty.facts";
	
	
	// Candidates for an automated Test:
	// TODO https://bugzilla.redhat.com/show_bug.cgi?id=669513
	// TODO https://bugzilla.redhat.com/show_bug.cgi?id=664847#2
	// TODO https://bugzilla.redhat.com/show_bug.cgi?id=629670
	// TODO Bug 706552 - Wrong DMI structures length: 3263 bytes announced, structures occupy 3265 bytes. https://github.com/RedHatQE/rhsm-qe/issues/137
	// TODO Bug 707525 - Facts update command displays consumed uuid https://github.com/RedHatQE/rhsm-qe/issues/138
	// TODO Bug 722239 - subscription-manager cli does not show all facts https://github.com/RedHatQE/rhsm-qe/issues/139
	// TODO Bug 700821 - Update of consumer facts does not update the update timestamp https://github.com/RedHatQE/rhsm-qe/issues/140
	// TODO Bug 866645 - [RFE] Omit interfaces from facts based on their type. https://github.com/RedHatQE/rhsm-qe/issues/141
	// TODO Bug 874147 - python-ethtool api changed causing facts to list ipv4 address as "unknown" https://github.com/RedHatQE/rhsm-qe/issues/142
	
	// TODO create tests that overrides the facts, for example....  and the uses getSystemSubscriptionPoolProductDataAsListOfLists()
	// see TODO MOVE THIS BLOCK OF TESTING INTO ITS OWN "RULES CHECK TEST" from SubscribeTests
	//String factsFile = clienttasks.factsDir+"/subscriptionTests.facts";
	//client.runCommandAndWait("echo '{\"cpu.cpu_socket(s)\": \"4\"}' > "+factsFile);	// create an override for facts  // use clienttasks.createFactsFileWithOverridingValues(...)
	//clienttasks.facts(true,true, null, null, null);

	// TODO Activation Notes
	// To enable activation on the QA env, the machine factsdmi.system.manufacturer must contain string "DELL" and the fact dmi.system.serial_number must be known on the RedHat IT backend:  put them in this overrider file: /etc/rhsm/facts
	//	<aedwards> dmi.system.manufacturer
	//	<aedwards> dmi.system.serial_number
	//	<aedwards> candlepin.subscription.activation.debug_prefix
	// To enable activation on the onpremises env set the config value in /etc/candlepin/candlepin.conf candlepin.subscription.activation.debug_prefix to a value like "activator" and then when you register use the --consumername=activator<BLAH> to see the "Activate a Subscription" button
	// [root@jsefler-onprem-server facts]# cat /etc/rhsm/facts/activator.facts 
	// {"dmi.system.manufacturer": "MyDELLManfacturer","dmi.system.serial_number":"CNZFGH6"}
	// https://engineering.redhat.com/trac/Entitlement/wiki/DellActivation
	// TODO Bug 701458 - "We are currently processing your subscription activation, please check back later." should not render as an "Error activating subscription:" https://github.com/RedHatQE/rhsm-qe/issues/143
	// TODO activate test:
	// against QA or Stage env...
	// [root@jsefler-onprem-workstation facts]# subscription-manager activate --email=jsefler@redhat.com
	// A subscription was not found for the given Dell service tag: CNZFGH6
	// [root@jsefler-onprem-workstation facts]# subscription-manager unregister
	// against QA 
	//	[root@jsefler-onprem-workstation facts]# subscription-manager activate --email=jsefler@redhat.com
	//	Your subscription activation is being processed and should be available soon. You will be notified via email once it is available. If you have any questions, additional information can be found here: https://access.redhat.com/kb/docs/DOC-53864.
	//	[root@jsefler-onprem-workstation facts]# subscription-manager activate --email=jsefler@redhat.com
	//	The Dell service tag: CNZFGH1, has already been used to activate a subscription
	//	[root@jsefler-onprem-workstation facts]# 

	
	
	
	// Configuration Methods ***********************************************************************
	@BeforeClass(groups={"setup"})
	protected void removeRhsmLogBeforeClass() {
		if (client==null) return;
		
		// remove the rhsm.log before this class to effectively reduce its size because it
		// occasionally gets backed up to rhsm.log.1 in the midst of a pair of calls to
		// RemoteFileTasks.markFile(...) and RemoteFileTasks.getTailFromMarkedFile(...)
		client.runCommandAndWait("rm -f "+clienttasks.rhsmLogFile);
	}
	
	
	
	// Protected Methods ***********************************************************************

	protected boolean doSystemFactsMatchConsumerFacts(String consumerId, Map<String,String> systemFactsMap, Map<String,String> consumerFactsMap) {
		// The following list of system facts will be dropped by the remote candlepin API when they are NOT integers >= 0.
		// This is defined in candlepin file src/main/java/org/candlepin/config/ConfigProperties.java and was introduced by candlepin commit 5d4d30753ab209b82181c267a94dd833f24b24c9
		// This was inspired by bugzillas 803757 858286
		//  public static final String NON_NEG_INTEGER_FACTS = "candlepin.positive_integer_facts";
		//	private static final String NON_NEG_INTEGER_FACT_LIST =
		//        "cpu.core(s)_per_socket," +
		//        "cpu.cpu(s)," +
		//        "cpu.cpu_socket(s)," +
		//        "lscpu.core(s)_per_socket," +
		//        "lscpu.cpu(s)," +
		//        "lscpu.numa_node(s)," +
		//        "lscpu.numa_node0_cpu(s)," +
		//        "lscpu.socket(s)," +
		//        "lscpu.thread(s)_per_core";
		// This list is configurable in /etc/candlpin/candlepin.conf by including a line like this:  candlepin.positive_integer_facts = "cpu.core(s)_per_socket,cpu.cpu(s)"
		List<String> NON_NEG_INTEGER_FACT_LIST = Arrays.asList("cpu.core(s)_per_socket","cpu.cpu(s)","cpu.cpu_socket(s)","lscpu.core(s)_per_socket","lscpu.cpu(s)","lscpu.numa_node(s)","lscpu.numa_node0_cpu(s)","lscpu.socket(s)","lscpu.thread(s)_per_core");
		boolean mapsAreEqual=true;

		for (Map<String,String> m : Arrays.asList(systemFactsMap,consumerFactsMap)) {	// normalize boolean facts
			for (String k : m.keySet()) {
				if (m.get(k).equalsIgnoreCase(Boolean.TRUE.toString())) m.put(k,Boolean.TRUE.toString());
				if (m.get(k).equalsIgnoreCase(Boolean.FALSE.toString())) m.put(k,Boolean.FALSE.toString());
			}
		}
		
		// asserting all of the facts known by the remote candlepin API are accounted for by the facts known by the local system
		for (String key : consumerFactsMap.keySet()) {
			// special skip case
			if (key.equals("system.name") || key.equals("system.uuid")) {
				log.info("Skipping comparison of extended fact '"+key+"'.");
				continue;
			}
			
			// special assert case
			if (NON_NEG_INTEGER_FACT_LIST.contains(key)) {
				// special special assert case lscpu.numa_node0_cpu(s): 0,1
				if (key.equals("lscpu.numa_node0_cpu(s)") || key.equals("lscpu.numa_node(s)")) {
					for (String subvalue : consumerFactsMap.get(key).split(",")) {
						Assert.assertTrue(isInteger(subvalue) && Integer.valueOf(subvalue)>=0, "Consumer fact '"+key+"' subvalue '"+subvalue+"' from fact "+key+":"+consumerFactsMap.get(key)+" is a non-negative integer.  ( If this test fails, then the remote candlepin API is failing to drop the fact entirely from the consumer when the system uploads this fact; see Candlepin commit 5d4d30753ab209b82181c267a94dd833f24b24c9 https://github.com/candlepin/candlepin/pull/157; see Bugzillas https://bugzilla.redhat.com/buglist.cgi?bug_id=803757%2C858286 )");
					}
				} else {
					Assert.assertTrue(isInteger(consumerFactsMap.get(key)) && Integer.valueOf(consumerFactsMap.get(key))>=0, "Consumer fact '"+key+"' value '"+consumerFactsMap.get(key)+"' is a non-negative integer.  ( If this test fails, then the remote candlepin API is failing to drop the fact entirely from the consumer when the system uploads this fact; see Candlepin commit 5d4d30753ab209b82181c267a94dd833f24b24c9 https://github.com/candlepin/candlepin/pull/157; see Bugzillas https://bugzilla.redhat.com/buglist.cgi?bug_id=803757%2C858286 )");
				}
			}
			
			if (systemFactsMap.containsKey(key) && !systemFactsMap.get(key).equals(consumerFactsMap.get(key))) {
				log.warning("Consumer '"+consumerId+"' on client "+client1tasks.hostname+" has a local system fact '"+key+"' value '"+systemFactsMap.get(key)+"' which does not match value '"+consumerFactsMap.get(key)+"' from the remote candlepin API.");
				
				// special skip case
				if (key.equals("net.interface.sit0.mac_address")) {
					log.warning("Skipping comparison of fact '"+key+"'.  The local system value appears to change unpredictably.  The current value on the system '"+systemFactsMap.get(key)+"' may be acceptably different than the value on the consumer '"+consumerFactsMap.get(key)+"';  see Bugzilla https://bugzilla.redhat.com/show_bug.cgi?id=838123");
					continue;
				}
				
				// special ignore case
				if (systemFactsMap.get(key).equals("Unknown") && consumerFactsMap.get(key).trim().equals("")) {
					log.info("Ignoring mismatch for fact '"+key+"'; see Bugzilla https://bugzilla.redhat.com/show_bug.cgi?id=722248");
					continue;
				}
				
				mapsAreEqual=false;
			} else if (!systemFactsMap.containsKey(key)) {
				log.warning("Consumer '"+consumerId+"' from the remote candlepin API has a fact '"+key+"' which is absent from the local system facts on client "+client1tasks.hostname+".");
				mapsAreEqual=false;	
			}
		}
		
		// asserting all of the facts known by the local system are accounted for by the facts known by the remote candlepin API
		for (String key : systemFactsMap.keySet()) {
			// special skip case
			if (key.equals("system.name") || key.equals("system.uuid")) {
				log.info("Skipping comparison of extended fact '"+key+"'.");
				continue;
			}
			
			if (consumerFactsMap.containsKey(key) && !consumerFactsMap.get(key).equals(systemFactsMap.get(key))) {
				log.warning("Consumer '"+consumerId+"' from the remote candlepin API has a system fact '"+key+"' value '"+consumerFactsMap.get(key)+"' which does not match value '"+systemFactsMap.get(key)+"' from the local system fact on client "+client1tasks.hostname+".");
				
				// special skip case
				if (key.equals("net.interface.sit0.mac_address")) {
					log.warning("Skipping comparison of fact '"+key+"'.  The local system value appears to change unpredictably.  The current value on the system '"+systemFactsMap.get(key)+"' may be acceptably different than the value on the consumer '"+consumerFactsMap.get(key)+"';  see Bugzilla https://bugzilla.redhat.com/show_bug.cgi?id=838123");
					continue;
				}
				
				// special ignore case
				if (systemFactsMap.get(key).equals("Unknown") && consumerFactsMap.get(key).trim().equals("")) {
					log.info("Ignoring mismatch for fact '"+key+"'; see Bugzilla https://bugzilla.redhat.com/show_bug.cgi?id=722248");
					continue;
				}
				
				mapsAreEqual=false;
			} else if (!consumerFactsMap.containsKey(key)) {
				log.warning("Consumer '"+consumerId+"' on client "+client1tasks.hostname+" has a local system fact '"+key+"' which is absent from the remote candlepin API.");
				
				// special ignore case
				if (NON_NEG_INTEGER_FACT_LIST.contains(key)) {
					if (!isInteger(systemFactsMap.get(key)) || Integer.valueOf(systemFactsMap.get(key))<0) {
						log.info("Ignoring absent fact '"+key+"' from the remote candlepin API because its value '"+systemFactsMap.get(key)+"' is NOT an integer>=0; see Candlepin commit 5d4d30753ab209b82181c267a94dd833f24b24c9 https://github.com/candlepin/candlepin/pull/157");
						continue;
					}
				}
				
				mapsAreEqual=false;	
			}
		}
		
		return mapsAreEqual;
	}

	
	
	// Data Providers ***********************************************************************
	
	@DataProvider(name="getClientsData")
	public Object[][] getClientsDataAs2dArray() {
		return TestNGUtils.convertListOfListsTo2dArray(getClientsDataAsListOfLists());
	}
	protected List<List<Object>> getClientsDataAsListOfLists(){
		List<List<Object>> ll = new ArrayList<List<Object>>();

		// SSHCommandRunner client
		if (client1!= null)	ll.add(Arrays.asList(new Object[]{client1tasks}));
		if (client2!= null)	ll.add(Arrays.asList(new Object[]{client2tasks}));

		return ll;
	}
	

}

package rhsm.cli.tests;

import java.io.File;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.xmlrpc.XmlRpcException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterGroups;
import org.testng.annotations.BeforeGroups;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import rhsm.base.ConsumerType;
import rhsm.base.SubscriptionManagerCLITestScript;
import rhsm.cli.tasks.CandlepinTasks;
import rhsm.data.EntitlementCert;
import rhsm.data.InstalledProduct;
import rhsm.data.ProductCert;
import rhsm.data.ProductNamespace;
import rhsm.data.ProductSubscription;
import rhsm.data.SubscriptionPool;

import com.redhat.qe.Assert;
import com.redhat.qe.auto.bugzilla.BlockedByBzBug;
import com.redhat.qe.auto.bugzilla.BzChecker;
import com.redhat.qe.auto.tcms.ImplementsNitrateTest;
import com.redhat.qe.auto.testng.TestNGUtils;
import com.redhat.qe.tools.RemoteFileTasks;
import com.redhat.qe.tools.SSHCommandResult;

/**
 * @author ssalevan
 * @author jsefler
 *
 */
@Test(groups={"SubscribeTests"})
public class SubscribeTests extends SubscriptionManagerCLITestScript{

	// Test methods ***********************************************************************



	@Test(	description="subscription-manager-cli: subscribe consumer to subscription pool product id",	//; and assert the subscription pool is not available when it does not match the system hardware.",
			dataProvider="getAllSystemSubscriptionPoolProductData",
			groups={"AcceptanceTests","blockedByBug-660713","blockedByBug-806986","blockedByBug-878986","blockedByBug-962520","blockedByBug-1008647","blockedByBug-1009600","blockedByBug-996993"},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void SubscribeToSubscriptionPoolProductId_Test(String productId, JSONArray bundledProductDataAsJSONArray) throws Exception {
		
		// is this system a virtual guest system or a physical system
		boolean systemIsGuest = Boolean.valueOf(clienttasks.getFactValue("virt.is_guest"));
		
		// begin test with a fresh register
		clienttasks.unregister(null, null, null);
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, null, false, null, null, null);

		// assert the subscription pool with the matching productId is available
		SubscriptionPool pool = SubscriptionPool.findFirstInstanceWithMatchingFieldFromList("productId", productId, clienttasks.getCurrentlyAllAvailableSubscriptionPools());	// clienttasks.getCurrentlyAvailableSubscriptionPools() is tested at the conclusion of this test
		if (pool==null) {	// when pool is null, the most likely error is that all of the available subscriptions from the pools are being consumed, let's check...
			for (String poolId: CandlepinTasks.getPoolIdsForProductId(sm_clientUsername, sm_clientPassword, sm_serverUrl, clienttasks.getCurrentlyRegisteredOwnerKey(), productId)) {
				int quantity = (Integer) CandlepinTasks.getPoolValue(sm_clientUsername, sm_clientPassword, sm_serverUrl, poolId, "quantity");
				int consumed = (Integer) CandlepinTasks.getPoolValue(sm_clientUsername, sm_clientPassword, sm_serverUrl, poolId, "consumed");
				if (consumed>=quantity) {
					log.warning("It appears that the total quantity '"+quantity+"' of subscriptions from poolId '"+poolId+"' for product '"+productId+"' are being consumed.");
				}
			}	
		}
		Assert.assertNotNull(pool, "Expected SubscriptionPool with ProductId '"+productId+"' is available for subscribing.");
		
		// assemble a list of expected bundled product names
		List<String> bundledProductNames = new ArrayList<String>();
		for (int j=0; j<bundledProductDataAsJSONArray.length(); j++) {
			JSONObject bundledProductAsJSONObject = (JSONObject) bundledProductDataAsJSONArray.get(j);
			String bundledProductId = bundledProductAsJSONObject.getString("productId");
			String bundledProductName = bundledProductAsJSONObject.getString("productName");
			bundledProductNames.add(bundledProductName);
		}
		
		// TEMPORARY WORKAROUND
		boolean invokeWorkaroundWhileBugIsOpen = true;
		String bugId="1016300"; // Bug 1016300 - the "Provides:" field in subscription-manager list --available should exclude "MKT" products.
		try {if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");SubscriptionManagerCLITestScript.addInvokedWorkaround(bugId);} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
		if (invokeWorkaroundWhileBugIsOpen) {
			log.warning("While bug '"+bugId+"' is open, skip assertion that the actual list of SubscriptionPool provided product names "+pool.provides+" matches the expected list of bundledProductDataNames "+bundledProductNames+".");
		} else
		// END OF WORKAROUND
		// assert that the pool's list of Provides matches the list of bundled product names after implementation of Bug 996993 - [RFE] Search for or list matching providedProducts
		Assert.assertTrue(bundledProductNames.containsAll(pool.provides) && pool.provides.containsAll(bundledProductNames), "The actual list of SubscriptionPool provided product names "+pool.provides+" matches the expected list of bundledProductDataNames "+bundledProductNames+".");
		
		List<ProductCert> currentlyInstalledProductCerts = clienttasks.getCurrentProductCerts();
		List<InstalledProduct> currentlyInstalledProducts = clienttasks.getCurrentlyInstalledProducts();
		
		// assert the installed status of the bundled products
		for (int j=0; j<bundledProductDataAsJSONArray.length(); j++) {
			JSONObject bundledProductAsJSONObject = (JSONObject) bundledProductDataAsJSONArray.get(j);
			String bundledProductId = bundledProductAsJSONObject.getString("productId");
			
			// assert the status of the installed products listed
			for (ProductCert productCert : ProductCert.findAllInstancesWithMatchingFieldFromList("productId", bundledProductId, currentlyInstalledProductCerts)) {
				InstalledProduct installedProduct = InstalledProduct.findFirstInstanceWithMatchingFieldFromList("productName", productCert.productName, currentlyInstalledProducts);
				Assert.assertNotNull(installedProduct, "The status of installed product cert with ProductName '"+productCert.productName+"' is reported in the list of installed products.");
				Assert.assertEquals(installedProduct.status, "Not Subscribed", "Before subscribing to pool for ProductId '"+productId+"', the status of Installed Product '"+productCert.productName+"' is Not Subscribed.");
			}
		}
		
		// when the Suggested quantity is 0, let's specify a quantity to avoid Stdout: Quantity '1' is not a multiple of instance multiplier '2'
		// adjust quantity for instance_multiplier pools
		String instance_multiplier = CandlepinTasks.getPoolProductAttributeValue(sm_clientUsername, sm_clientPassword, sm_serverUrl, pool.poolId, "instance_multiplier");
		String quantity = null;
		if (pool.suggested<1 && instance_multiplier!=null) {
			quantity = instance_multiplier;
		}
		
		// subscribe to the pool
		File entitlementCertFile = clienttasks.subscribeToSubscriptionPool(pool,quantity,sm_serverAdminUsername,sm_serverAdminPassword,sm_serverUrl);
		EntitlementCert entitlementCert = clienttasks.getEntitlementCertFromEntitlementCertFile(entitlementCertFile);
		
		currentlyInstalledProducts = clienttasks.getCurrentlyInstalledProducts();
		List<ProductSubscription> currentlyConsumedProductSubscriptions = clienttasks.getCurrentlyConsumedProductSubscriptions();

		// after subscribing to a pool, assert that its corresponding productSubscription is found among the currently consumed productSubscriptions
		ProductSubscription consumedProductSubscription = ProductSubscription.findFirstInstanceWithMatchingFieldFromList("productId", pool.productId, currentlyConsumedProductSubscriptions);
		Assert.assertNotNull(consumedProductSubscription, "The consumed ProductSubscription corresponding to the subscribed SubscriptionPool productId '"+pool.productId+"' was found among the list of consumed ProductSubscriptions.");
		
		// assert that the quantityUsed matches the quantitySuggested after implementation of Bug 1008647 - [RFE] bind requests that do not specify a quantity should automatically use the quantity needed to achieve compliance
		if (quantity!=null) {
			Assert.assertEquals(consumedProductSubscription.quantityUsed, Integer.valueOf(quantity), "When the attachment quantity '"+quantity+"' is specified, the quantity used from the consumed product subscription should match.");		
		} else if (pool.suggested > 1) {
			Assert.assertEquals(consumedProductSubscription.quantityUsed, pool.suggested, "When the suggested consumption quantity '"+pool.suggested+"' from the available pool is greater than one, the quantity used from the consumed product subscription should match.");
		} else {
			Assert.assertEquals(consumedProductSubscription.quantityUsed, Integer.valueOf(1), "When the suggested consumption quantity '"+pool.suggested+"' from the available pool is NOT greater than one, the quantity used from the consumed product subscription should be one.");
		}
		
		// assert that the System Type matches between the available pool and the consumed product subscription after implementation of Bug 1009600 - Show System Type in list --consumed; Show System Type in attach confirmation gui dialog.
		Assert.assertEquals(consumedProductSubscription.machineType, pool.machineType, "After subscribing from a pool with a machine type '"+pool.machineType+"', the consumed product subscription's machine type should match.");

		// assert that the consumed product subscription provides all the expected bundled products.
		Assert.assertTrue(consumedProductSubscription.provides.containsAll(bundledProductNames)&&bundledProductNames.containsAll(consumedProductSubscription.provides),"The consumed productSubscription provides all of the expected bundled product names "+bundledProductNames+" after subscribing to pool: "+pool);
		
		// assert the dates of the consumed product subscription match the originating subscription pool
		Assert.assertEquals(ProductSubscription.formatDateString(consumedProductSubscription.endDate),ProductSubscription.formatDateString(pool.endDate),
				"Consumed productSubscription expires on the same DAY as the originating subscription pool.");
		//FIXME	Assert.assertTrue(productSubscription.startDate.before(entitlementCert.validityNotBefore), "Consumed ProductSubscription Began before the validityNotBefore date of the new entitlement: "+entitlementCert);

		// assert the expected products are consumed
		for (int j=0; j<bundledProductDataAsJSONArray.length(); j++) {
			JSONObject bundledProductAsJSONObject = (JSONObject) bundledProductDataAsJSONArray.get(j);
			String bundledProductId = bundledProductAsJSONObject.getString("productId");
			String bundledProductName = bundledProductAsJSONObject.getString("productName");
			bundledProductNames.add(bundledProductName);
			
			// find the corresponding productNamespace from the entitlementCert
			ProductNamespace productNamespace = null;
			for (ProductNamespace pn : entitlementCert.productNamespaces) {
				if (pn.id.equals(bundledProductId)) productNamespace = pn;
			}
			
			// assert the installed status of the corresponding product
			if (entitlementCert.productNamespaces.isEmpty()) {
				log.warning("This product '"+productId+"' ("+bundledProductName+") does not appear to grant entitlement to any client side content.  This must be a server side management add-on product. Asserting as such...");

				Assert.assertEquals(entitlementCert.contentNamespaces.size(),0,
						"When there are no productNamespaces in the entitlementCert, there should not be any contentNamespaces.");

				// when there is no corresponding product, then there better not be an installed product status by the same product name
				Assert.assertNull(InstalledProduct.findFirstInstanceWithMatchingFieldFromList("productName", bundledProductName, currentlyInstalledProducts),
						"Should not find any installed product status matching a server side management add-on productName: "+ bundledProductName);

				// when there is no corresponding product, then there better not be an installed product cert by the same product name
				Assert.assertNull(ProductCert.findFirstInstanceWithMatchingFieldFromList("productName", bundledProductName, currentlyInstalledProductCerts),
						"Should not find any installed product certs matching a server side management add-on productName: "+ bundledProductName);

			} else {
				Assert.assertNotNull(productNamespace, "The new entitlement cert's product namespace corresponding to this expected ProductSubscription with ProductName '"+bundledProductName+"' was found.");
				
				// assert the status of the installed products listed
				List <ProductCert> productCerts = ProductCert.findAllInstancesWithMatchingFieldFromList("productId", productNamespace.id, currentlyInstalledProductCerts);  // should be a list of one or empty
				for (ProductCert productCert : productCerts) {
					List <InstalledProduct> installedProducts = InstalledProduct.findAllInstancesWithMatchingFieldFromList("productName", productCert.productName, currentlyInstalledProducts);
					Assert.assertEquals(installedProducts.size(),1, "The status of installed product '"+productCert.productName+"' should only be reported once in the list of installed products.");
					InstalledProduct installedProduct = installedProducts.get(0);
					
					// TEMPORARY WORKAROUND FOR BUG
					if (installedProduct.arch.contains(",")) {
						/*boolean*/ invokeWorkaroundWhileBugIsOpen = true;
						/*String*/ bugId="951633"; // Bug 951633 - installed product with comma separated arch attribute fails to go green
						try {if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");SubscriptionManagerCLITestScript.addInvokedWorkaround(bugId);} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
						if (invokeWorkaroundWhileBugIsOpen) {
							throw new SkipException("Verification for status of Installed Product name='"+installedProduct.productName+"' with arch='"+installedProduct.arch+"' is blocked by open bugzilla '"+bugId+"'.");
						}
					}
					// END OF WORKAROUND
					
					// decide what the status should be...  "Subscribed" or "Partially Subscribed" (SPECIAL CASE WHEN poolProductSocketsAttribute=0  or "null" SHOULD YIELD Subscribed)
					String poolProductSocketsAttribute = CandlepinTasks.getPoolProductAttributeValue(sm_clientUsername, sm_clientPassword, sm_serverUrl, pool.poolId, "sockets");
					String poolProductVcpuAttribute = CandlepinTasks.getPoolProductAttributeValue(sm_clientUsername, sm_clientPassword, sm_serverUrl, pool.poolId, "vcpu");	// introduced by 885785 [RFE] Subscription Manager should alert a user if subscription vcpu limits are lower than system vcpu allocation
					// treat a non-numeric poolProductSocketsAttribute as if it was null
					// if the sockets attribute is not numeric (e.g. "null"),  then this subscription should be available to this client
					try {Integer.valueOf(poolProductSocketsAttribute);}
					catch (NumberFormatException e) {
						// do not mark productAttributesPassRulesCheck = false;
						log.warning("Ecountered a non-numeric value for product sockets attribute sockets on productId='"+productId+"' poolId '"+pool.poolId+"'. SIMPLY IGNORING THIS ATTRIBUTE.");
						poolProductSocketsAttribute = null;
					}
					
					// consider the socket/vcpu coverage and assert the installed product's status 
					if (pool.subscriptionType!=null && pool.subscriptionType.equals("Other")) {
						Assert.fail("Encountered a subscription pool of type '"+pool.subscriptionType+"'.  Do not know how to assert the installedProduct.status after subscribing to pool: "+pool);
				    } else if (pool.multiEntitlement==null && pool.subscriptionType!=null && pool.subscriptionType.isEmpty()) {
				    	log.warning("Encountered a pool with an empty value for subscriptionType (indicative of an older candlepin server): "+pool);
				    	log.warning("After subscribing to a pool for ProductId '"+productId+", skipping assertion of the installed product status for products provided by this pool: "+pool);
					} else if (pool.multiEntitlement!=null && !pool.multiEntitlement/*ADDED AFTER IMPLEMENTATION OF BUG 1008647*/ && !systemIsGuest/*ADDED AFTER IMPLEMENTATION OF BUG 885785*/ && poolProductSocketsAttribute!=null && Integer.valueOf(poolProductSocketsAttribute)<Integer.valueOf(clienttasks.sockets) && Integer.valueOf(poolProductSocketsAttribute)>0) {
						Assert.assertEquals(installedProduct.status, "Partially Subscribed", "After subscribing this physical system to a pool for ProductId '"+productId+"' (covers '"+poolProductSocketsAttribute+"' sockets), the status of Installed Product '"+bundledProductName+"' should be Partially Subscribed since a corresponding product cert was found in "+clienttasks.productCertDir+" and the machine's sockets value ("+clienttasks.sockets+") is greater than what a single subscription from a non-multi-entitlement pool covers.");
					} else if (pool.subscriptionType!=null && (pool.subscriptionType.equals("Standard") || pool.subscriptionType.equals("Stackable only with other subscriptions"))/*ADDED AFTER IMPLEMENTATION OF BUG 1008647 AND 1029968*/ && !systemIsGuest/*ADDED AFTER IMPLEMENTATION OF BUG 885785*/ && poolProductSocketsAttribute!=null && Integer.valueOf(poolProductSocketsAttribute)<Integer.valueOf(clienttasks.sockets) && Integer.valueOf(poolProductSocketsAttribute)>0) {
						Assert.assertEquals(installedProduct.status, "Partially Subscribed", "After subscribing this physical system to a pool for ProductId '"+productId+"' (covers '"+poolProductSocketsAttribute+"' sockets), the status of Installed Product '"+bundledProductName+"' should be Partially Subscribed since a corresponding product cert was found in "+clienttasks.productCertDir+" and the machine's sockets value ("+clienttasks.sockets+") is greater than what a single subscription from a non-multi-entitlement pool covers.");
					} else if (pool.multiEntitlement!=null && !pool.multiEntitlement/*ADDED AFTER IMPLEMENTATION OF BUG 1008647*/ && systemIsGuest/*ADDED AFTER IMPLEMENTATION OF BUG 885785*/ && poolProductVcpuAttribute!=null && Integer.valueOf(poolProductVcpuAttribute)<Integer.valueOf(clienttasks.vcpu) && Integer.valueOf(poolProductVcpuAttribute)>0) {
						Assert.assertEquals(installedProduct.status, "Partially Subscribed", "After subscribing this virtual system to a pool for ProductId '"+productId+"' (covers '"+poolProductVcpuAttribute+"' vcpu), the status of Installed Product '"+bundledProductName+"' should be Partially Subscribed since a corresponding product cert was found in "+clienttasks.productCertDir+" and the machine's vcpu value ("+clienttasks.vcpu+") is greater than what a single subscription from a non-multi-entitlement pool covers.");
					} else if (pool.subscriptionType!=null && (pool.subscriptionType.equals("Standard") || pool.subscriptionType.equals("Stackable only with other subscriptions"))/*ADDED AFTER IMPLEMENTATION OF BUG 1008647 AND 1029968*/ && systemIsGuest/*ADDED AFTER IMPLEMENTATION OF BUG 885785*/ && poolProductVcpuAttribute!=null && Integer.valueOf(poolProductVcpuAttribute)<Integer.valueOf(clienttasks.vcpu) && Integer.valueOf(poolProductVcpuAttribute)>0) {
						Assert.assertEquals(installedProduct.status, "Partially Subscribed", "After subscribing this virtual system to a pool for ProductId '"+productId+"' (covers '"+poolProductVcpuAttribute+"' vcpu), the status of Installed Product '"+bundledProductName+"' should be Partially Subscribed since a corresponding product cert was found in "+clienttasks.productCertDir+" and the machine's vcpu value ("+clienttasks.vcpu+") is greater than what a single subscription from a non-multi-entitlement pool covers.");
					} else {
						Assert.assertEquals(installedProduct.status, "Subscribed", "After subscribing to a pool for ProductId '"+productId+"', the status of Installed Product '"+bundledProductName+"' is Subscribed since a corresponding product cert was found in "+clienttasks.productCertDir+" and the system's sockets/vcpu needs were met.");
					}
					
					// behavior update after fix from Bug 767619 - Date range for installed products needs to be smarter.
					//Assert.assertEquals(InstalledProduct.formatDateString(installedProduct.startDate), ProductSubscription.formatDateString(productSubscription.startDate), "Installed Product '"+bundledProductName+"' starts on the same DAY as the consumed ProductSubscription: "+productSubscription);					
					if (installedProduct.status.equals("Subscribed")) {
						// assert the valid date range on the installed product match the validity period of the product subscription
						Assert.assertEquals(InstalledProduct.formatDateString(installedProduct.endDate), ProductSubscription.formatDateString(consumedProductSubscription.endDate), "Installed Product '"+bundledProductName+"' expires on the same DAY as the consumed ProductSubscription: "+consumedProductSubscription);
						Assert.assertEquals(InstalledProduct.formatDateString(installedProduct.startDate), ProductSubscription.formatDateString(consumedProductSubscription.startDate), "Installed Product '"+bundledProductName+"' starts on the same DAY as the consumed ProductSubscription: "+consumedProductSubscription);
					} else {
						/* valid assertion before Bug 990639 - Update information displayed on My Installed Products tab when product is partially subscribed
						// assert the date range on the installed product is None
						Assert.assertNull(installedProduct.startDate, "Installed Product '"+bundledProductName+"' start date range should be None/null when today's status '"+installedProduct.status+"' is NOT fully Subscribed.");
						Assert.assertNull(installedProduct.endDate, "Installed Product '"+bundledProductName+"' end date range should be None/null when today's status '"+installedProduct.status+"' is NOT fully Subscribed.");
						*/
						// assert the date range on the installed product is NOT None
						Assert.assertNotNull(installedProduct.startDate, "Installed Product '"+bundledProductName+"' start date range should NOT be None/null even when today's status '"+installedProduct.status+"' is NOT fully Subscribed.");
						Assert.assertNotNull(installedProduct.endDate, "Installed Product '"+bundledProductName+"' end date range should NOT be None/null even when today's status '"+installedProduct.status+"' is NOT fully Subscribed.");
					}
				}
				if (productCerts.isEmpty()) {
					Assert.assertNull(InstalledProduct.findFirstInstanceWithMatchingFieldFromList("productName", bundledProductName, currentlyInstalledProducts),"There should NOT be an installed status report for '"+bundledProductName+"' since a corresponding product cert was not found in "+clienttasks.productCertDir);
				}
			}
		}
		

		
		// TODO I BELIEVE THIS FINAL BLOCK OF TESTING IS INACCURATE - jsefler 5/27/2012
		// I THINK IT SHOULD BE CHECKING HARDWARE SOCKETS AND NOT INSTALLED SOFTWARE
		/*
		// check if this subscription matches the installed software and then test for availability
		boolean subscriptionProductIdMatchesInstalledSoftware = false;
		for (ContentNamespace contentNamespace : entitlementCert.contentNamespaces) {
			if (clienttasks.areAllRequiredTagsInContentNamespaceProvidedByProductCerts(contentNamespace, currentlyInstalledProductCerts)) {
				subscriptionProductIdMatchesInstalledSoftware=true; break;
			}
		}
		clienttasks.unsubscribeFromSerialNumber(entitlementCert.serialNumber);
		pool = SubscriptionPool.findFirstInstanceWithMatchingFieldFromList("productId", productId, clienttasks.getCurrentlyAvailableSubscriptionPools());
		if (currentlyInstalledProductCerts.isEmpty()) {
			log.info("A final assertion to verify that SubscriptionPool with ProductId '"+productId+"' is available based on matching installed software is not applicable when the list of installed software is empty.");
		} else {
			if (subscriptionProductIdMatchesInstalledSoftware) {
				Assert.assertNotNull(pool, "Expected SubscriptionPool with ProductId '"+productId+"' matches the installed software and is available for subscribing when listing --available.");
			} else {
				Assert.assertNull(pool, "Expected SubscriptionPool with ProductId '"+productId+"' does NOT match the installed software and is only available for subscribing when listing --all --available.");
			}
		}
		*/
		
	}
	
	
	@Test(	description="subscription-manager-cli: subscribe consumer to an entitlement using product ID",
			groups={"blockedByBug-584137"},
			dataProvider="getAvailableSubscriptionPoolsData",
			enabled=false)	// Subscribing to a Subscription Pool using --product Id has been removed in subscription-manager-0.71-1.el6.i686.)
	@ImplementsNitrateTest(caseId=41680)
	@Deprecated
	public void SubscribeToValidSubscriptionsByProductID_Test_DEPRECATED(SubscriptionPool pool){
//		sm.unsubscribeFromEachOfTheCurrentlyConsumedProductSubscriptions();
//		sm.subscribeToEachOfTheCurrentlyAvailableSubscriptionPools();
		clienttasks.subscribeToSubscriptionPoolUsingProductId(pool);
	}
	
	
	@Test(	description="subscription-manager-cli: subscribe consumer to an entitlement using product ID",
			groups={"blockedByBug-584137"},
			enabled=false)	// old/disabled test from ssalevan
	@Deprecated
	public void SubscribeToASingleEntitlementByProductID_Test_DEPRECATED(){
		clienttasks.unsubscribeFromTheCurrentlyConsumedProductSubscriptionsIndividually();
		SubscriptionPool MCT0696 = new SubscriptionPool("MCT0696", "696");
		MCT0696.addProductID("Red Hat Directory Server");
		clienttasks.subscribeToSubscriptionPoolUsingProductId(MCT0696);
		//this.refreshSubscriptions();
		for (ProductSubscription pid:MCT0696.associatedProductIDs){
			Assert.assertTrue(clienttasks.getCurrentlyConsumedProductSubscriptions().contains(pid),
					"ProductID '"+pid.productName+"' consumed from Pool '"+MCT0696.subscriptionName+"'");
		}
	}
	
	
	@Test(	description="subscription-manager-cli: subscribe consumer to an entitlement using pool ID",
			groups={"blockedByBug-584137"},
			enabled=true,
			dataProvider="getAvailableSubscriptionPoolsData")
	@ImplementsNitrateTest(caseId=41686)
	public void SubscribeToValidSubscriptionsByPoolID_Test(SubscriptionPool pool){
// non-dataProvided test procedure
//		sm.unsubscribeFromAllOfTheCurrentlyConsumedProductSubscriptions();
//		sm.subscribeToEachOfTheCurrentlyAvailableSubscriptionPools();
		clienttasks.subscribeToSubscriptionPoolUsingPoolId(pool);
	}
	
	
	@Test(	description="subscription-manager-cli: subscribe consumer to each available subscription pool using pool ID",
			groups={"blockedByBug-584137"},
			dataProvider="getGoodRegistrationData")
	@ImplementsNitrateTest(caseId=41686)
	public void SubscribeConsumerToEachAvailableSubscriptionPoolUsingPoolId_Test(String username, String password, String owner){
		clienttasks.unregister(null, null, null);
		clienttasks.register(username, password, owner, null, ConsumerType.system, null, null, Boolean.FALSE, null, null, (String)null, null, null, null, Boolean.FALSE, false, null, null, null);
		clienttasks.subscribeToTheCurrentlyAvailableSubscriptionPoolsIndividually();
	}
	
	
	@Test(	description="subscription-manager-cli: subscribe consumer to an entitlement using registration token",
			groups={"blockedByBug-584137"},
			enabled=false)	// Bug 670823 - if subscribe with regtoken is gone, then it should be removed from cli
	@Deprecated
	@ImplementsNitrateTest(caseId=41681)
	public void SubscribeToRegToken_Test_DEPRECATED(){
		clienttasks.unsubscribeFromTheCurrentlyConsumedProductSubscriptionsIndividually();
		clienttasks.subscribeToRegToken(sm_regtoken);
	}
	
	
	@Test(	description="Subscribed for Already subscribed Entitlement.",
			groups={"blockedByBug-584137","blockedByBug-979492"},
			// dataProvider="getAvailableSubscriptionPoolsData", TAKES TOO LONG AND RARELY REVEALS A BUG - changing to getRandomSubsetOfAvailableSubscriptionPoolsData
			dataProvider="getRandomSubsetOfAvailableSubscriptionPoolsData",
			enabled=true)
	@ImplementsNitrateTest(caseId=41897)
	public void AttemptToSubscribeToAnAlreadySubscribedPool_Test(SubscriptionPool pool) throws JSONException, Exception{
		String consumerId = clienttasks.getCurrentConsumerId();
		Assert.assertNull(CandlepinTasks.getConsumersNewestEntitlementSerialCorrespondingToSubscribedPoolId(sm_clientUsername, sm_clientPassword, sm_serverUrl, consumerId, pool.poolId),"The current consumer has not been granted any entitlements from pool '"+pool.poolId+"'.");
		Assert.assertNotNull(clienttasks.subscribeToSubscriptionPool_(pool),"Authenticator '"+sm_clientUsername+"' has been granted an entitlement from pool '"+pool.poolId+"' under organization '"+sm_clientOrg+"'.");
		BigInteger serial1 = CandlepinTasks.getConsumersNewestEntitlementSerialCorrespondingToSubscribedPoolId(sm_clientUsername, sm_clientPassword, sm_serverUrl, consumerId, pool.poolId);
		SSHCommandResult subscribeResult = clienttasks.subscribe_(null,null,pool.poolId,null,null, null, null, null, null, null, null);
		String subscribeStdout = subscribeResult.getStdout().trim();
		
		subscribeStdout = clienttasks.workaroundForBug906550(subscribeStdout);
		
		if (CandlepinTasks.isPoolProductMultiEntitlement(sm_clientUsername,sm_clientPassword,sm_serverUrl,pool.poolId)) {
//			Assert.assertEquals(subscribeStdout, String.format("Successfully consumed a subscription from the pool with id %s.",pool.poolId),	// Bug 812410 - Subscription-manager subscribe CLI feedback 
//			Assert.assertEquals(subscribeStdout, String.format("Successfully consumed a subscription for: %s",pool.subscriptionName),	// changed by Bug 874804 Subscribe -> Attach
			Assert.assertEquals(subscribeStdout, String.format("Successfully attached a subscription for: %s",pool.subscriptionName),
				"subscribe command allows multi-entitlement pools to be subscribed to by the same consumer more than once.");
			BigInteger serial2 = CandlepinTasks.getConsumersNewestEntitlementSerialCorrespondingToSubscribedPoolId(sm_clientUsername, sm_clientPassword, sm_serverUrl, consumerId, pool.poolId);
			Assert.assertNotSame(serial1,serial2,
				"Upon subscribing to a multi-entitlement pool '"+pool.poolId+"' for the second time, the newly granted entilement's serial '"+serial2+"' number differs from the first '"+serial1+"'.");
			Assert.assertTrue(RemoteFileTasks.testExists(client, clienttasks.entitlementCertDir+File.separator+serial1+".pem"),
				"After subscribing to multi-entitlement pool '"+pool.poolId+"' for the second time, the first granted entilement cert file still exists.");
			Assert.assertTrue(RemoteFileTasks.testExists(client, clienttasks.entitlementCertDir+File.separator+serial2+".pem"),
				"After subscribing to multi-entitlement pool '"+pool.poolId+"' for the second time, the second granted entilement cert file exists.");
		} else {
			String expectedStdout = String.format("This consumer is already subscribed to the product matching pool with id '%s'.",pool.poolId);
			if (!clienttasks.workaroundForBug876764(sm_serverType)) expectedStdout = String.format("This unit has already had the subscription matching pool ID '%s' attached.",pool.poolId);
			Assert.assertEquals(subscribeStdout, expectedStdout,
				"subscribe command returns proper message when the same consumer attempts to subscribe to a non-multi-entitlement pool more than once.");
			Assert.assertTrue(RemoteFileTasks.testExists(client, clienttasks.entitlementCertDir+File.separator+serial1+".pem"),
				"After attempting to subscribe to pool '"+pool.poolId+"' for the second time, the first granted entilement cert file still exists.");
		}
	}
	
	
	@Test(	description="subscription-manager-cli: subscribe consumer to multiple/duplicate/bad pools in one call",
			groups={"blockedByBug-622851","blockedByBug-995597"},
			enabled=true)
	public void SubscribeToMultipleDuplicateAndBadPools_Test() throws JSONException, Exception {
		
		// begin the test with a cleanly registered system
		clienttasks.unregister(null, null, null);
	    clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, null, false, null, null, null);
	    
		// assemble a list of all the available SubscriptionPool ids with duplicates and bad ids
		List <String> poolIds = new ArrayList<String>();
		Map <String,String> poolNames= new HashMap<String,String>();
		for (SubscriptionPool pool : clienttasks.getCurrentlyAvailableSubscriptionPools()) {
			poolIds.add(pool.poolId);
			poolIds.add(pool.poolId); // add a duplicate poolid
			poolNames.put(pool.poolId, pool.subscriptionName);
		}
		String badPoolId1 = "bad123", badPoolId2 = "bad_POOLID"; 
		poolIds.add(0, badPoolId1); // insert a bad poolid
		poolIds.add(badPoolId2); // append a bad poolid
		
		// subscribe to all pool ids
		log.info("Attempting to subscribe to multiple pools with duplicate and bad pool ids...");
		SSHCommandResult subscribeResult = clienttasks.subscribe_(null, null, poolIds, null, null, null, null, null, null, null, null);
		
		/*
		No such entitlement pool: bad123
		Successfully subscribed the system to Pool 8a90f8c63159ce55013159cfd6c40303
		This consumer is already subscribed to the product matching pool with id '8a90f8c63159ce55013159cfd6c40303'.
		Successfully subscribed the system to Pool 8a90f8c63159ce55013159cfea7a06ac
		Successfully subscribed the system to Pool 8a90f8c63159ce55013159cfea7a06ac
		No such entitlement pool: bad_POOLID
		*/
		
		// assert the results...
		Assert.assertEquals(subscribeResult.getExitCode(), Integer.valueOf(0), "The exit code from the subscribe command indicates a success.");
		for (String poolId : poolIds) {
			String subscribeResultMessage;
			if (poolId.equals(badPoolId1) || poolId.equals(badPoolId2)) {
				subscribeResultMessage = String.format("No such entitlement pool: %s",poolId);
				subscribeResultMessage = String.format("Subscription pool %s does not exist.",poolId);
				subscribeResultMessage = String.format("Pool with id %s could not be found.",poolId);
				Assert.assertTrue(subscribeResult.getStdout().contains(subscribeResultMessage),"The subscribe result for an invalid pool '"+poolId+"' contains: "+subscribeResultMessage);
			}
			else if (CandlepinTasks.isPoolProductMultiEntitlement(sm_clientUsername,sm_clientPassword,sm_serverUrl,poolId)) {
				subscribeResultMessage = String.format("Successfully consumed a subscription from the pool with id %s.",poolId);	// Bug 812410 - Subscription-manager subscribe CLI feedback 
				subscribeResultMessage = String.format("Successfully consumed a subscription for: %s",poolNames.get(poolId));	// changed by Bug 874804 Subscribe -> Attach
				subscribeResultMessage = String.format("Successfully attached a subscription for: %s",poolNames.get(poolId));
				subscribeResultMessage += "\n"+subscribeResultMessage;
				Assert.assertTrue(subscribeResult.getStdout().contains(subscribeResultMessage),"The duplicate subscribe result for a multi-entitlement pool '"+poolId+"' contains: "+subscribeResultMessage);
			} else if (false) {
				// TODO case when there are no entitlements remaining for the duplicate subscribe
			} else {
				subscribeResultMessage = String.format("Successfully consumed a subscription from the pool with id %s.",poolId);	// Bug 812410 - Subscription-manager subscribe CLI feedback 
				subscribeResultMessage = String.format("Successfully consumed a subscription for: %s",poolNames.get(poolId));	// changed by Bug 874804 Subscribe -> Attach
				subscribeResultMessage = String.format("Successfully attached a subscription for: %s",poolNames.get(poolId));
				String subscribeResultSubMessage = String.format("This consumer is already subscribed to the product matching pool with id '%s'.",poolId);
				if (!clienttasks.workaroundForBug876764(sm_serverType)) subscribeResultSubMessage = String.format("This unit has already had the subscription matching pool ID '%s' attached.",poolId);
				subscribeResultMessage += "\n"+subscribeResultSubMessage;
				Assert.assertTrue(subscribeResult.getStdout().contains(subscribeResultMessage),"The duplicate subscribe result for pool '"+poolId+"' contains: "+subscribeResultMessage);			
			}
		}
	}
	
	
	@Test(	description="rhsmcertd: change certFrequency",
			dataProvider="getCertFrequencyData",
			groups={"blockedByBug-617703","blockedByBug-700952","blockedByBug-708512","blockedByBug-907638","blockedByBug-822402","blockedByBug-986572","blockedByBug-1000301","blockedByBug-1026435"},
			enabled=true)
	@ImplementsNitrateTest(caseId=41692)
	public void rhsmcertdChangeCertFrequency_Test(int minutes) {
		String errorMsg = "Either the consumer is not registered with candlepin or the certificates are corrupted. Certificate updation using daemon failed.";
		errorMsg = "Either the consumer is not registered or the certificates are corrupted. Certificate update using daemon failed.";
		
		log.info("First test with an unregistered user and verify that the rhsmcertd actually fails since it cannot self-identify itself to the candlepin server.");
		clienttasks.unregister(null, null, null);
		clienttasks.restart_rhsmcertd(minutes, null, false, false); sleep(10000); // allow 10sec for the initial update
		log.info("Appending a marker in the '"+clienttasks.rhsmcertdLogFile+"' so we can assert that the certificates are being updated every '"+minutes+"' minutes");
		String marker = "Testing rhsm.conf certFrequency="+minutes+" when unregistered..."; // https://tcms.engineering.redhat.com/case/41692/
		RemoteFileTasks.runCommandAndAssert(client,"echo \""+marker+"\" >> "+clienttasks.rhsmcertdLogFile,Integer.valueOf(0));
		RemoteFileTasks.runCommandAndAssert(client,"tail -1 "+clienttasks.rhsmcertdLogFile,Integer.valueOf(0),marker,null);
		sleep(minutes*60*1000);	// give the rhsmcertd a chance to check in with the candlepin server and update the certs
		//RemoteFileTasks.runCommandAndAssert(client,"tail -1 "+clienttasks.rhsmcertdLogFile,Integer.valueOf(0),"update failed \\(\\d+\\), retry in "+minutes+" minutes",null);
		RemoteFileTasks.runCommandAndAssert(client,"tail -1 "+clienttasks.rhsmcertdLogFile,Integer.valueOf(0),"\\(Cert Check\\) Update failed \\(255\\), retry will occur on next run.",null);
		RemoteFileTasks.runCommandAndAssert(client,"tail -1 "+clienttasks.rhsmLogFile,Integer.valueOf(0),errorMsg,null);
		
		
		log.info("Now test with a registered user whose identity is corrupt and verify that the rhsmcertd actually fails since it cannot self-identify itself to the candlepin server.");
		String consumerid = clienttasks.getCurrentConsumerId(clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, null, false, null, null, null));
		log.info("Corrupting the identity cert by borking its content...");
		RemoteFileTasks.runCommandAndAssert(client, "openssl x509 -noout -text -in "+clienttasks.consumerCertFile()+" > /tmp/stdout; mv /tmp/stdout -f "+clienttasks.consumerCertFile(), 0);
		clienttasks.restart_rhsmcertd(minutes, null, false, false); sleep(10000); // allow 10sec for the initial update
		log.info("Appending a marker in the '"+clienttasks.rhsmcertdLogFile+"' so we can assert that the certificates are being updated every '"+minutes+"' minutes");
		marker = "Testing rhsm.conf certFrequency="+minutes+" when identity is corrupted...";
		RemoteFileTasks.runCommandAndAssert(client,"echo \""+marker+"\" >> "+clienttasks.rhsmcertdLogFile,Integer.valueOf(0));
		RemoteFileTasks.runCommandAndAssert(client,"tail -1 "+clienttasks.rhsmcertdLogFile,Integer.valueOf(0),marker,null);
		sleep(minutes*60*1000);	// give the rhsmcertd a chance to check in with the candlepin server and update the certs
		//RemoteFileTasks.runCommandAndAssert(client,"tail -1 "+clienttasks.rhsmcertdLogFile,Integer.valueOf(0),"update failed \\(\\d+\\), retry in "+minutes+" minutes",null);
		RemoteFileTasks.runCommandAndAssert(client,"tail -1 "+clienttasks.rhsmcertdLogFile,Integer.valueOf(0),"\\(Cert Check\\) Update failed \\(255\\), retry will occur on next run.",null);
		RemoteFileTasks.runCommandAndAssert(client,"tail -1 "+clienttasks.rhsmLogFile,Integer.valueOf(0),errorMsg,null);

		
		log.info("Finally test with a registered user and verify that the rhsmcertd succeeds because he can identify himself to the candlepin server.");
		if (true) {	// WORKAROUND for new RHEL7 selinux-policy troubles
			// Although the following register with consumerid will successfully re-establish a good /etc/pki/consumer/cert.pem, the selinux-policy on RHEL7 is not happy with the permissions on the file after "Corrupting the identity cert by borking its content..."
			// The result is the following error in rhsm.log and the audit.log
			//
			//	2013-11-21 12:16:54,135 [WARNING] rhsmcertd-worker @identity.py:64 - possible certificate corruption
			//	2013-11-21 12:16:54,136 [ERROR] rhsmcertd-worker @identity.py:65 - [Errno 13] Permission denied: '/etc/pki/consumer/cert.pem'
			//	2013-11-21 12:16:54,136 [ERROR] rhsmcertd-worker @rhsmcertd-worker:43 - Either the consumer is not registered or the certificates are corrupted. Certificate update using daemon failed.
			//
			//	type=AVC msg=audit(1385054754.518:21794): avc:  denied  { open } for  pid=22456 comm="rhsmcertd-worke" path="/etc/pki/consumer/cert.pem" dev="dm-1" ino=25317473 scontext=system_u:system_r:rhsmcertd_t:s0 tcontext=unconfined_u:object_r:user_tmp_t:s0 tclass=file
			//	type=SYSCALL msg=audit(1385054754.518:21794): arch=c000003e syscall=2 success=no exit=-13 a0=147ab10 a1=0 a2=1b6 a3=0 items=0 ppid=20771 pid=22456 auid=4294967295 uid=0 gid=0 euid=0 suid=0 fsuid=0 egid=0 sgid=0 fsgid=0 tty=(none) ses=4294967295 comm="rhsmcertd-worke" exe="/usr/bin/python2.7" subj=system_u:system_r:rhsmcertd_t:s0 key=(null)
			//
			// The WORKAROUND is to tell subscription-manager to clean before registering with the consumerId
			clienttasks.clean(null, null, null);
		}
	    clienttasks.register(sm_clientUsername, sm_clientPassword, null, null, null, null, consumerid, null, null, null, (String)null, null, null, null, Boolean.TRUE, false, null, null, null);
		clienttasks.restart_rhsmcertd(minutes, null, false, true); sleep(10000); // allow 10sec for the initial update
		log.info("Appending a marker in the '"+clienttasks.rhsmcertdLogFile+"' so we can assert that the certificates are being updated every '"+minutes+"' minutes");
		marker = "Testing rhsm.conf certFrequency="+minutes+" when registered..."; // https://tcms.engineering.redhat.com/case/41692/
		RemoteFileTasks.runCommandAndAssert(client,"echo \""+marker+"\" >> "+clienttasks.rhsmcertdLogFile,Integer.valueOf(0));
		RemoteFileTasks.runCommandAndAssert(client,"tail -1 "+clienttasks.rhsmcertdLogFile,Integer.valueOf(0),marker,null);
		sleep(minutes*60*1000);	// give the rhsmcertd a chance to check in with the candlepin server and update the certs
		///RemoteFileTasks.runCommandAndAssert(client,"tail -1 "+clienttasks.rhsmcertdLogFile,Integer.valueOf(0),"certificates updated",null);
		RemoteFileTasks.runCommandAndAssert(client,"tail -1 "+clienttasks.rhsmcertdLogFile,Integer.valueOf(0),"\\(Cert Check\\) Certificates updated.",null);
		
		//	# tail -f /var/log/rhsm/rhsm.log
		//	2010-09-10 12:05:06,338 [ERROR] main() @certmgr.py:75 - Either the consumer is not registered with candlepin or the certificates are corrupted. Certificate updation using daemon failed.
		
		//	# tail -f /var/log/rhsm/rhsmcertd.log
		//	Fri Sep 10 11:59:50 2010: started: interval = 1 minutes
		//	Fri Sep 10 11:59:51 2010: update failed (255), retry in 1 minutes
		//	testing rhsm.conf certFrequency=1 when unregistered.
		//	Fri Sep 10 12:00:51 2010: update failed (255), retry in 1 minutes
		//	Fri Sep 10 12:01:04 2010: started: interval = 1 minutes
		//	Fri Sep 10 12:01:05 2010: certificates updated
		//	testing rhsm.conf certFrequency=1 when registered.
		//	Fri Sep 10 12:02:05 2010: certificates updated
		
		// AFTER CHANGES FROM Bug 708512 - rhsmcertd is logging "certificates updated" when it should be "update failed (255), retry in 1 minutes"
		//	# tail -f /var/log/rhsm/rhsmcertd.log
		//	Testing rhsm.conf certFrequency=2 when unregistered...
		//	Thu Aug  9 18:57:24 2012 [WARN] (Cert Check) Update failed (255), retry will occur on next run.
		//	1344553073761 Testing service rhsmcertd restart...
		//	Thu Aug  9 18:57:54 2012 [INFO] rhsmcertd is shutting down...
		//	Thu Aug  9 18:57:54 2012 [INFO] Starting rhsmcertd...
		//	Thu Aug  9 18:57:54 2012 [INFO] Healing interval: 1440.0 minute(s) [86400 second(s)]
		//	Thu Aug  9 18:57:54 2012 [INFO] Cert check interval: 2.0 minute(s) [120 second(s)]
		//	Thu Aug  9 18:57:54 2012 [INFO] Waiting 120 second(s) [2.0 minute(s)] before running updates.
		//	Thu Aug  9 18:59:54 2012 [WARN] (Healing) Update failed (255), retry will occur on next run.
		//	Thu Aug  9 18:59:54 2012 [WARN] (Cert Check) Update failed (255), retry will occur on next run.
		//	Thu Aug  9 18:59:54 2012 [WARN] (Cert Check) Update failed (255), retry will occur on next run.
		//	Testing rhsm.conf certFrequency=2 when identity is corrupted...
		//	Thu Aug  9 19:01:54 2012 [WARN] (Cert Check) Update failed (255), retry will occur on next run.
		//	1344553342931 Testing service rhsmcertd restart...
		//	Thu Aug  9 19:02:23 2012 [INFO] rhsmcertd is shutting down...
		//	Thu Aug  9 19:02:23 2012 [INFO] Starting rhsmcertd...
		//	Thu Aug  9 19:02:23 2012 [INFO] Healing interval: 1440.0 minute(s) [86400 second(s)]
		//	Thu Aug  9 19:02:23 2012 [INFO] Cert check interval: 2.0 minute(s) [120 second(s)]
		//	Thu Aug  9 19:02:23 2012 [INFO] Waiting 120 second(s) [2.0 minute(s)] before running updates.
		//	Thu Aug  9 19:04:25 2012 [INFO] (Healing) Certificates updated.
		//	Thu Aug  9 19:04:30 2012 [INFO] (Cert Check) Certificates updated.
		//	Thu Aug  9 19:04:35 2012 [INFO] (Cert Check) Certificates updated.
		//	Testing rhsm.conf certFrequency=2 when registered...
		//	Thu Aug  9 19:06:28 2012 [INFO] (Cert Check) Certificates updated.

	}
	
	
	@Test(	description="rhsmcertd: ensure certificates synchronize",
			groups={"blockedByBug-617703","blockedByBug-907638","blockedByBug-822402","blockedByBug-986572","blockedByBug-1000301","blockedByBug-1026435"},
			enabled=true)
	@ImplementsNitrateTest(caseId=41694)
	public void rhsmcertdEnsureCertificatesSynchronize_Test() throws JSONException, Exception{
		
		// start with a cleanly unregistered system
		clienttasks.unregister(null, null, null);
		
		// register a clean user
	    clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, null, false, null, null, null);
	    
	    // subscribe to all the available pools
	    clienttasks.subscribeToTheCurrentlyAvailableSubscriptionPoolsCollectively();
	    
	    // get all of the current entitlement certs and remember them
	    List<File> entitlementCertFiles = clienttasks.getCurrentEntitlementCertFiles();
	    
	    // delete all of the entitlement cert files
	    client.runCommandAndWait("rm -rf "+clienttasks.entitlementCertDir+"/*");
	    Assert.assertEquals(clienttasks.getCurrentEntitlementCertFiles().size(), 0,
	    		"All the entitlement certs have been deleted.");
		
	    // restart the rhsmcertd to run every 1 minute and wait for a refresh
		clienttasks.restart_rhsmcertd(1, null, true, true);
		
		// assert that rhsmcertd has refreshed the entitlement certs back to the original
	    Assert.assertEquals(clienttasks.getCurrentEntitlementCertFiles(), entitlementCertFiles,
	    		"All the deleted entitlement certs have been re-synchronized by rhsm cert deamon.");
	}
	
	
	@Test(	description="subscription-manager: make sure the available pools come from subscriptions that pass the hardware rules for availability.",
			groups={"AcceptanceTests"},
			dependsOnGroups={},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	// Note: The objective if this test is essentially the same as ListTests.EnsureHardwareMatchingSubscriptionsAreListedAsAvailable_Test() and ListTests.EnsureNonHardwareMatchingSubscriptionsAreNotListedAsAvailable_Test(), but its implementation is slightly different
	public void VerifyAvailablePoolsPassTheHardwareRulesCheck_Test() throws Exception {
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, true, false, null, null, null);

		subscriptionPoolProductData = getSystemSubscriptionPoolProductDataAsListOfLists(true,false);
		List<SubscriptionPool> availableSubscriptionPools = clienttasks.getCurrentlyAvailableSubscriptionPools();
		for (List<Object> subscriptionPoolProductDatum : subscriptionPoolProductData) {
			String productId = (String)subscriptionPoolProductDatum.get(0);
			SubscriptionPool subscriptionPool = SubscriptionPool.findFirstInstanceWithMatchingFieldFromList("productId", productId, availableSubscriptionPools);
			if (subscriptionPool==null) {	// when pool is null, the most likely error is that all of the available subscriptions from the pools are being consumed, let's check...
				for (String poolId: CandlepinTasks.getPoolIdsForProductId(sm_clientUsername, sm_clientPassword, sm_serverUrl, clienttasks.getCurrentlyRegisteredOwnerKey(), productId)) {
					int quantity = (Integer) CandlepinTasks.getPoolValue(sm_clientUsername, sm_clientPassword, sm_serverUrl, poolId, "quantity");
					int consumed = (Integer) CandlepinTasks.getPoolValue(sm_clientUsername, sm_clientPassword, sm_serverUrl, poolId, "consumed");
					if (consumed>=quantity) {
						log.warning("It appears that the total quantity '"+quantity+"' of subscriptions from poolId '"+poolId+"' for product '"+productId+"' are being consumed.");
					}
				}	
			}			Assert.assertNotNull(subscriptionPool, "Expecting SubscriptionPool with ProductId '"+productId+"' to be available to registered user '"+sm_clientUsername+"'.");
		}
		for (SubscriptionPool availableSubscriptionPool : availableSubscriptionPools) {
			boolean productIdFound = false;
			for (List<Object> subscriptionPoolProductDatum : subscriptionPoolProductData) {
				if (availableSubscriptionPool.productId.equals((String)subscriptionPoolProductDatum.get(0))) {
					productIdFound = true;
					break;
				}
			}
			Assert.assertTrue(productIdFound, "Available SubscriptionPool with ProductId '"+availableSubscriptionPool.productId+"' passes the hardware rules check.");
		}
	}
	protected List<List<Object>> subscriptionPoolProductData;
	
	@Test(	description="subscription-manager-cli: autosubscribe consumer and verify expected subscription pool product id are consumed",
			groups={"AcceptanceTests","AutoSubscribeAndVerify", "blockedByBug-680399", "blockedByBug-734867", "blockedByBug-740877"},
			dependsOnMethods={"VerifyAvailablePoolsPassTheHardwareRulesCheck_Test"},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void InititiateAutoSubscribe_Test() throws Exception {
		
		// re-calculate the subscriptionPoolProductData accounting for a match to installed system software
		subscriptionPoolProductData = getSystemSubscriptionPoolProductDataAsListOfLists(true,true);

		// before testing, make sure all the expected subscriptionPoolProductId are available
		clienttasks.unregister(null, null, null);
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, null, false, null, null, null);
		
		// autosubscribe
		sshCommandResultFromAutosubscribe = clienttasks.subscribe(true,null,(String)null,null,null,null,null,null,null,null,null);
		
		/* RHEL57 RHEL61 Example Results...
		# subscription-manager subscribe --auto
		Installed Products:
		   Multiplier Product Bits - Not Subscribed
		   Load Balancing Bits - Subscribed
		   Awesome OS Server Bits - Subscribed
		   Management Bits - Subscribed
		   Awesome OS Scalable Filesystem Bits - Subscribed
		   Shared Storage Bits - Subscribed
		   Large File Support Bits - Subscribed
		   Awesome OS Workstation Bits - Subscribed
		   Awesome OS Premium Architecture Bits - Not Subscribed
		   Awesome OS for S390X Bits - Not Subscribed
		   Awesome OS Developer Basic - Not Subscribed
		   Clustering Bits - Subscribed
		   Awesome OS Developer Bits - Not Subscribed
		   Awesome OS Modifier Bits - Subscribed
		*/
		
		/* Example Results...
		# subscription-manager subscribe --auto
		Installed Product Current Status:
		
		ProductName:         	Awesome OS for x86_64/ALL Bits for ZERO sockets
		Status:               	Subscribed               
		
		
		ProductName:         	Awesome OS for x86_64/ALL Bits
		Status:               	Subscribed               
		
		
		ProductName:         	Awesome OS for ppc64 Bits
		Status:               	Subscribed               
		
		
		ProductName:         	Awesome OS for i386 Bits 
		Status:               	Subscribed               
		
		
		ProductName:         	Awesome OS for x86 Bits  
		Status:               	Subscribed               
		
		
		ProductName:         	Awesome OS for ia64 Bits 
		Status:               	Subscribed               
		
		
		ProductName:         	Awesome OS Scalable Filesystem Bits
		Status:               	Subscribed               
		*/
	}
	
	
	@Test(	description="subscription-manager-cli: autosubscribe consumer and verify expected subscription pool product id are consumed",
			groups={"AcceptanceTests","AutoSubscribeAndVerify","blockedByBug-672438","blockedByBug-678049","blockedByBug-743082","blockedByBug-865193","blockedByBug-864383","blockedByBug-977321"},
			dependsOnMethods={"InititiateAutoSubscribe_Test"},
			dataProvider="getInstalledProductCertsData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void VerifyInstalledProductCertWasAutoSubscribed_Test(Object bugzilla, ProductCert productCert) throws Exception {
		
		// search the subscriptionPoolProductData for a bundledProduct matching the productCert's productName
		// (subscriptionPoolProductData was set in a prior test methods that this test depends on)
		String subscriptionPoolProductId = null;
		for (List<Object> row : subscriptionPoolProductData) {
			JSONArray bundledProductDataAsJSONArray = (JSONArray)row.get(1);
			
			for (int j=0; j<bundledProductDataAsJSONArray.length(); j++) {
				JSONObject bundledProductAsJSONObject = (JSONObject) bundledProductDataAsJSONArray.get(j);
				String bundledProductName = bundledProductAsJSONObject.getString("productName");
				String bundledProductId = bundledProductAsJSONObject.getString("productId");

				if (bundledProductId.equals(productCert.productId)) {
					subscriptionPoolProductId = (String)row.get(0); // found
					break;
				}
			}
			if (subscriptionPoolProductId!=null) break;
		}
		
		// determine what autosubscribe results to assert for this installed productCert 
		InstalledProduct installedProduct = InstalledProduct.findFirstInstanceWithMatchingFieldFromList("productName", productCert.productName, clienttasks.getCurrentlyInstalledProducts());

		// when subscriptionPoolProductId!=null, then this productCert should have been autosubscribed
		String expectedSubscribeStatus = (subscriptionPoolProductId!=null)? "Subscribed":"Not Subscribed";
		
		// assert the installed product status matches the expected status 
		Assert.assertEquals(installedProduct.status,expectedSubscribeStatus,
				"As expected, the Installed Product Status reflects that the autosubscribed ProductName '"+productCert.productName+"' is "+expectedSubscribeStatus.toLowerCase()+".  (Note: a \"Not Subscribed\" status is expected when the subscription does not match the hardware socket requirements or the required tags on all the subscription content is not satisfied by any of the installed software.)");

		// assert that the sshCommandResultOfAutosubscribe showed the expected Subscribe Status for this productCert
		// RHEL57 RHEL61		Assert.assertContainsMatch(sshCommandResultFromAutosubscribe.getStdout().trim(), "^\\s+"+productCert.productName.replaceAll("\\(", "\\\\(").replaceAll("\\)", "\\\\)"+" - "+expectedSubscribeStatus),
		//		"As expected, ProductName '"+productCert.productName+"' was reported as '"+expectedSubscribeStatus+"' in the output from register with autotosubscribe.");
		List<InstalledProduct> autosubscribedProductStatusList = InstalledProduct.parse(sshCommandResultFromAutosubscribe.getStdout());
		InstalledProduct autosubscribedProduct = InstalledProduct.findFirstInstanceWithMatchingFieldFromList("productName", productCert.productName, autosubscribedProductStatusList);
		Assert.assertEquals(autosubscribedProduct.status,expectedSubscribeStatus,
				"As expected, ProductName '"+productCert.productName+"' was reported as '"+expectedSubscribeStatus+"' in the output from register with autotosubscribe.");
	}
	SSHCommandResult sshCommandResultFromAutosubscribe;
	
	
	@Test(	description="subscription-manager: autosubscribe consumer more than once and verify we are not duplicately subscribed",
			groups={"blockedByBug-723044","blockedByBug-743082","blockedByBug-977321"},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void SubscribeWithAutoMoreThanOnce_Test() throws Exception {

		// before testing, make sure all the expected subscriptionPoolProductId are available
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, true, false, null, null, null);
		
		// autosubscribe once
		SSHCommandResult result1 = clienttasks.subscribe(Boolean.TRUE,null,(String)null,null,null,null,null,null,null, null, null);
		List<File> entitlementCertFiles1 = clienttasks.getCurrentEntitlementCertFiles();
		List<InstalledProduct> autosubscribedProductStatusList1 = InstalledProduct.parse(result1.getStdout());
		
		// autosubscribe twice
		SSHCommandResult result2 = clienttasks.subscribe(Boolean.TRUE,null,(String)null,null,null,null,null,null,null, null, null);
		List<File> entitlementCertFiles2 = clienttasks.getCurrentEntitlementCertFiles();
		List<InstalledProduct> autosubscribedProductStatusList2 = InstalledProduct.parse(result2.getStdout());
		
		// assert results
		Assert.assertEquals(entitlementCertFiles2.size(), entitlementCertFiles1.size(), "The number of granted entitlement certs is the same after a second autosubscribe.");
		Assert.assertEquals(autosubscribedProductStatusList2.size(), autosubscribedProductStatusList1.size(), "The stdout from autosubscribe reports the same number of installed product status entries after a second autosubscribe.");
		Assert.assertTrue(autosubscribedProductStatusList1.containsAll(autosubscribedProductStatusList2), "The list of installed product status entries from a second autosubscribe is the same as the first.");
	}
	
	
	@Test(	description="subscription-manager: call the Candlepin API dry_run to get the pools and quantity that would be used to complete an autosubscribe with an unavailable service level",
			groups={"blockedByBug-864508"},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void CandlepinConsumerEntitlementsDryrunWithUnavailableServiceLevel_Test() throws JSONException, Exception {
		// register with force
		String consumerId = clienttasks.getCurrentConsumerId(clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, true, false, null, null, null));

		String serviceLevel = "FOO";
		JSONObject jsonDryrunResult= new JSONObject(CandlepinTasks.getResourceUsingRESTfulAPI(sm_clientUsername, sm_clientPassword, sm_serverUrl, String.format("/consumers/%s/entitlements/dry-run%s",consumerId, serviceLevel==null?"":String.format("?service_level=%s",serviceLevel))));
		
		Assert.assertTrue(jsonDryrunResult.has("displayMessage"),"The dry-run results threw an error with a displayMessage when attempting to run wirh serviceLevel '"+serviceLevel+"' ");
		
		String expectedDryrunResult = String.format("Service level %s is not available to consumers of organization %s.","FOO",sm_clientOrg);	// valid before bug fix 864508
		expectedDryrunResult = String.format("Service level '%s' is not available to consumers of organization %s.","FOO",sm_clientOrg);
		if (!clienttasks.workaroundForBug876764(sm_serverType)) expectedDryrunResult = String.format("Service level '%s' is not available to units of organization %s.","FOO",sm_clientOrg);
		Assert.assertEquals(jsonDryrunResult.getString("displayMessage"),expectedDryrunResult, "JSON results from a Candlepin Restful API call to dry-run with an unavailable service level.");
	}
	
	
	@Test(	description="subscription-manager: call the Candlepin API dry_run to get the pools and quantity that would be used to complete an autosubscribe with a valid service level",
			groups={"AcceptanceTests","blockedByBug-859652","blockedByBug-962520"},
			dataProvider="getSubscribeWithAutoAndServiceLevelData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void CandlepinConsumerEntitlementsDryrunWithServiceLevel_Test(Object bugzilla, String serviceLevel) throws JSONException, Exception {
		// Reference: https://engineering.redhat.com/trac/Entitlement/wiki/SlaSubscribe
	    //"GET"
	    //"url": "/consumers/{consumer_uuid}/entitlements/dry-run?service_level=#{service_level}", 
		
		String consumerId = clienttasks.getCurrentConsumerId();
		
		//  on the first call to this dataProvided test, unsubscribe all subscriptions OR just unregister to a clean state
		// this will remove any prior subscribed modifier entitlements to avoid test logic errors in this test.
		if (firstcalltoCandlepinConsumerEntitlementsDryrunWithServiceLevel_Test) {
			if (consumerId!=null) clienttasks.unsubscribe(true, (BigInteger)null, null, null, null);	//OR clienttasks.unregister(null,null,null);
			firstcalltoCandlepinConsumerEntitlementsDryrunWithServiceLevel_Test = false;
		}

		// store the initial state of the system
		if (consumerId==null) consumerId = clienttasks.getCurrentConsumerId(clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, "SubscriptionServiceLevelConsumer", null, null, null, null, (String)null, null, null, null, null, false, null, null, null));
		String initialServiceLevel = clienttasks.getCurrentServiceLevel();
		List<EntitlementCert> initialEntitlementCerts = clienttasks.getCurrentEntitlementCerts();
		List<SubscriptionPool> initialAvailableSubscriptionPools = clienttasks.getCurrentlyAvailableSubscriptionPools();

		// call the candlepin API
		// curl --insecure --user testuser1:password --request GET https://jsefler-f14-candlepin.usersys.redhat.com:8443/candlepin/consumers/7033f5c0-c451-4d4c-bf88-c5061dc2c521/entitlements/dry-run?service_level=Premium | python -m simplejson/tool
		JSONArray jsonDryrunResults= new JSONArray(CandlepinTasks.getResourceUsingRESTfulAPI(sm_clientUsername, sm_clientPassword, sm_serverUrl, String.format("/consumers/%s/entitlements/dry-run%s",consumerId, serviceLevel==null?"":String.format("?service_level=%s",urlEncode(serviceLevel)))));	// urlEncode is needed to handle whitespace in the serviceLevel

		// assert that each of the dry run results match the service level and the proposed quantity is available
		//List<SubscriptionPool> dryrunSubscriptionPools = new ArrayList<SubscriptionPool>();
		for (int i = 0; i < jsonDryrunResults.length(); i++) {
			// jsonDryrunResults is an array of two values per entry: "pool" and "quantity"
			JSONObject jsonPool = ((JSONObject) jsonDryrunResults.get(i)).getJSONObject("pool");
			Integer quantity = ((JSONObject) jsonDryrunResults.get(i)).getInt("quantity");
			
			// assert that all of the pools proposed provide the requested service level
			String poolId = jsonPool.getString("id");
			SubscriptionPool subscriptionPool = SubscriptionPool.findFirstInstanceWithMatchingFieldFromList("poolId", poolId, initialAvailableSubscriptionPools);
			//dryrunSubscriptionPools.add(subscriptionPool);
			if (serviceLevel==null || serviceLevel.equals("")) {
				log.info("Pool '"+poolId+"' returned by the dry-run results (without requesting a service-level) has a value of '"+CandlepinTasks.getPoolProductAttributeValue(jsonPool, "support_level")+"'.");
			} else if (sm_exemptServiceLevelsInUpperCase.contains(CandlepinTasks.getPoolProductAttributeValue(jsonPool, "support_level").toUpperCase())) {
				log.warning("Pool '"+poolId+"' returned by the dry-run results provides the exempt service-level '"+CandlepinTasks.getPoolProductAttributeValue(jsonPool, "support_level")+"'.");
			} else {
				String support_level = CandlepinTasks.getPoolProductAttributeValue(jsonPool, "support_level");
				//CASE SENSITIVE ASSERTION Assert.assertEquals(support_level, serviceLevel,"Pool '"+poolId+"' returned by the dry-run results provides the requested service-level '"+serviceLevel+"'.");
				Assert.assertTrue(serviceLevel.equalsIgnoreCase(support_level),"Pool '"+poolId+"' returned by the dry-run results provides a case-insensitive support_level '"+support_level+"' match to the requested service-level '"+serviceLevel+"'.");
			}
			
			Assert.assertNotNull(subscriptionPool,"Pool '"+poolId+"' returned by the dry-run results for service-level '"+serviceLevel+"' was found in the list --available.");
			Assert.assertTrue(quantity<=(subscriptionPool.quantity.equalsIgnoreCase("unlimited")?quantity+1:Integer.valueOf(subscriptionPool.quantity)),"Pool '"+poolId+"' returned by the dry-run results for service-level '"+serviceLevel+"', will supply a quantity ("+quantity+") that is within the available quantity ("+subscriptionPool.quantity+").");
		}
		// TODO: This assert is not reliable unless there really is a pool that provides a product that is actually installed.
		//Assert.assertTrue(jsonDryrunResults.length()>0, "Dry-run results for service-level '"+serviceLevel+"' are not empty.");
		
		// assert the the dry-run did not change the current service level
		Assert.assertEquals(clienttasks.getCurrentServiceLevel(), initialServiceLevel,"The consumer's current service level setting was not affected by the dry-run query with serviceLevel '"+serviceLevel+"'.");
		clienttasks.identity(null, null, true, null, null, null, null);
		Assert.assertEquals(clienttasks.getCurrentServiceLevel(), initialServiceLevel,"The consumer's current service level setting was not affected by the dry-run query with serviceLevel '"+serviceLevel+"' even after an identity regeneration.");
		
		// assert that no new entitlements were actually given
		Assert.assertTrue(clienttasks.getCurrentEntitlementCerts().containsAll(initialEntitlementCerts), "This system's prior entitlements are unchanged after the dry-run.");
		
		// actually autosubscribe with this service-level
		clienttasks.subscribe(true, serviceLevel, (List<String>)null, (List<String>)null, (List<String>)null, null, null, null, null, null, null);
		//clienttasks.subscribe(true,"".equals(serviceLevel)?String.format("\"%s\"", serviceLevel):serviceLevel, (List<String>)null, (List<String>)null, (List<String>)null, null, null, null, null, null, null);
		
		// determine the newly granted entitlement certs
 		List<EntitlementCert> newlyGrantedEntitlementCerts = new ArrayList<EntitlementCert>();
		for (EntitlementCert entitlementCert : clienttasks.getCurrentEntitlementCerts()) {
			if (!initialEntitlementCerts.contains(entitlementCert)) {
				newlyGrantedEntitlementCerts.add(entitlementCert);
				if (serviceLevel==null || serviceLevel.equals("")) {
					log.info("The service level provided by the entitlement cert granted after autosubscribe (without specifying a service level) is '"+entitlementCert.orderNamespace.supportLevel+"'.");
				} else if (entitlementCert.orderNamespace.supportLevel!=null && sm_exemptServiceLevelsInUpperCase.contains(entitlementCert.orderNamespace.supportLevel.toUpperCase())) {
					log.warning("After autosubscribe with service level '"+serviceLevel+"', this autosubscribed entitlement provides an exempt service level '"+entitlementCert.orderNamespace.supportLevel+"' from entitled orderNamespace: "+entitlementCert.orderNamespace);
				} else {
					//CASE SENSITIVE ASSERTION Assert.assertEquals(entitlementCert.orderNamespace.supportLevel,serviceLevel,"The service level provided by the entitlement cert granted after autosubscribe matches the requested servicelevel.");
					Assert.assertTrue(serviceLevel.equalsIgnoreCase(entitlementCert.orderNamespace.supportLevel),"Ignoring case, the service level '"+entitlementCert.orderNamespace.supportLevel+"' provided by the entitlement cert granted after autosubscribe matches the requested servicelevel '"+serviceLevel+"'.");
				}
			}
		}
		
		// calling candlepin API with a service_level="" is actually a special case:   /consumers/<UUID>/entitlements/dry-run?service_level=
		// calling candlepin API with a service_level=null is also a special case:   /consumers/<UUID>/entitlements/dry-run
		// both of these cases will default to use the service_level that is already set on the consumer, however a call to subscribe --auto --service_level="" is not the same as calling the candlepin dry-run API with service_level="" since the CLI will actually UNSET the consumer's current service level (as requested by bug 1001169)
		// [root@jsefler-6 ~]#  curl --stderr /dev/null --insecure --user testuser1:password --request GET https://jsefler-f14-candlepin.usersys.redhat.com:8443/candlepin/consumers/6474c913-4c2f-4283-bcf5-2fc2c44da3ef/entitlements/dry-run?service_level= | python -m simplejson/tool
		if ("".equals(serviceLevel)) {
			log.warning("When testing dry-run with an empty string for service level, the jsonPools returned should match the service-level that the consumer object already has (unless the service-level granted is exempt).  This is different than calling subscription-manager subscribe --auto --service-level=\"\".");
			if (!"".equals(initialServiceLevel)) {
				for (int i = 0; i < jsonDryrunResults.length(); i++) {
					// jsonDryrunResults is an array of two values per entry: "pool" and "quantity"
					JSONObject jsonPool = ((JSONObject) jsonDryrunResults.get(i)).getJSONObject("pool");
					Integer quantity = ((JSONObject) jsonDryrunResults.get(i)).getInt("quantity");
					String supportLevelExemptValue = CandlepinTasks.getPoolProductAttributeValue(jsonPool, "support_level_exempt");
					
					// assert that all of the pools proposed provide the consumer's initial service level
					String poolId = jsonPool.getString("id");
					SubscriptionPool dryrunSubscriptionPool = SubscriptionPool.findFirstInstanceWithMatchingFieldFromList("poolId", poolId, initialAvailableSubscriptionPools);
					
					// check for an exempt service level
					if (supportLevelExemptValue==null || !Boolean.valueOf(supportLevelExemptValue)) {
						Assert.assertTrue(dryrunSubscriptionPool.serviceLevel.equalsIgnoreCase(initialServiceLevel), "When dry-run is called with an empty service-level, the actual consumer's initially set service-level '"+initialServiceLevel+"' matches the service-level '"+dryrunSubscriptionPool.serviceLevel+"' granted from the dry-run pool result: "+dryrunSubscriptionPool+". (EXCEPTION: This is not true when the service-level is exempt.)");
					} else {
						log.info("An exempt service level '"+dryrunSubscriptionPool.serviceLevel+"' was included in the dry-run pool result: "+dryrunSubscriptionPool);
					}
				}
			}
			log.info("Skipping the remaining assertions in this test when the service-level is empty.");
			return;
		}
			
		// assert that one entitlement was granted per dry-run pool result
		Assert.assertEquals(newlyGrantedEntitlementCerts.size(), jsonDryrunResults.length(),"The autosubscribe results granted the same number of entitlements as the dry-run pools returned.");

		// assert that the newly granted entitlements were actually granted from the dry-run pools
		//for (SubscriptionPool dryrunSubscriptionPool : dryrunSubscriptionPools) {
		for (int i = 0; i < jsonDryrunResults.length(); i++) {
			// jsonDryrunResults is an array of two values per entry: "pool" and "quantity"
			JSONObject jsonPool = ((JSONObject) jsonDryrunResults.get(i)).getJSONObject("pool");
			Integer quantity = ((JSONObject) jsonDryrunResults.get(i)).getInt("quantity");
			
			String poolId = jsonPool.getString("id");
			SubscriptionPool dryrunSubscriptionPool = SubscriptionPool.findFirstInstanceWithMatchingFieldFromList("poolId", poolId, initialAvailableSubscriptionPools);
			String supportLevelExemptValue = CandlepinTasks.getPoolProductAttributeValue(jsonPool, "support_level_exempt");

			EntitlementCert entitlementCert = clienttasks.getEntitlementCertCorrespondingToSubscribedPool(dryrunSubscriptionPool);
			Assert.assertNotNull(entitlementCert, "Found an entitlement cert corresponding to dry-run pool: "+dryrunSubscriptionPool);
			Assert.assertTrue(newlyGrantedEntitlementCerts.contains(entitlementCert),"This entitlement cert is among the newly granted entitlement from the autosubscribe.");
			Assert.assertEquals(Integer.valueOf(entitlementCert.orderNamespace.quantityUsed), quantity, "The actual entitlement quantityUsed matches the dry-run quantity results for pool :"+dryrunSubscriptionPool);
		}
		
		
		// for the sake of variability, let's unsubscribe from a randomly consumed subscription
		unsubscribeRandomly();
		//clienttasks.unsubscribeFromAllOfTheCurrentlyConsumedProductSubscriptions();
	}
	private boolean firstcalltoCandlepinConsumerEntitlementsDryrunWithServiceLevel_Test = true;
	
	

	
	
	@Test(	description="subscription-manager: subscribe using various good and bad values for the --quantity option",
			groups={"AcceptanceTests","blockedByBug-962520"},
			dataProvider="getSubscribeWithQuantityData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void SubscribeWithQuantity_Test(Object meta, SubscriptionPool pool, String quantity, Integer expectedExitCode, String expectedStdoutRegex, String expectedStderrRegex) {
		log.info("Testing subscription-manager subscribe using various good and bad values for the --quantity option.");
		if(pool==null) throw new SkipException(expectedStderrRegex);	// special case in the dataProvider to identify when a test pool was not available; expectedStderrRegex contains a message for what kind of test pool was being searched for.
	
		// start fresh by returning all entitlements
		clienttasks.unsubscribeFromAllOfTheCurrentlyConsumedProductSubscriptions();
		
		// subscribe with quantity
		SSHCommandResult sshCommandResult = clienttasks.subscribe_(null,null,pool.poolId,null,null,quantity,null,null,null, null, null);
		
		// assert the sshCommandResult here
		if (expectedExitCode!=null) Assert.assertEquals(sshCommandResult.getExitCode(), expectedExitCode,"ExitCode after subscribe with quantity=\""+quantity+"\" option:");
		if (expectedStdoutRegex!=null) Assert.assertContainsMatch(sshCommandResult.getStdout().trim(), expectedStdoutRegex,"Stdout after subscribe with --quantity=\""+quantity+"\" option:");
		if (expectedStderrRegex!=null) Assert.assertContainsMatch(sshCommandResult.getStderr().trim(), expectedStderrRegex,"Stderr after subscribe with --quantity=\""+quantity+"\" option:");
		
		// when successful, assert that the quantity is correctly reported in the list of consumed subscriptions
		List<ProductSubscription> subscriptionsConsumed = client1tasks.getCurrentlyConsumedProductSubscriptions();
		List<EntitlementCert> entitlementCerts = client1tasks.getCurrentEntitlementCerts();
		if (expectedExitCode==0 && expectedStdoutRegex!=null && expectedStdoutRegex.contains("Successful")) {
			Assert.assertEquals(entitlementCerts.size(), 1, "One EntitlementCert should have been downloaded to "+client1tasks.hostname+" when the attempt to subscribe is successful.");
			Assert.assertEquals(entitlementCerts.get(0).orderNamespace.quantityUsed, quantity.replaceFirst("^\\+",""), "The quantityUsed in the OrderNamespace of the downloaded EntitlementCert should match the quantity requested when we subscribed to pool '"+pool.poolId+"'.  OrderNamespace: "+entitlementCerts.get(0).orderNamespace);
			for (ProductSubscription productSubscription : subscriptionsConsumed) {
				Assert.assertEquals(productSubscription.quantityUsed, Integer.valueOf(quantity.replaceFirst("^\\+","")), "The quantityUsed reported in each consumed ProductSubscription should match the quantity requested when we subscribed to pool '"+pool.poolId+"'.  ProductSubscription: "+productSubscription);
			}
		} else {
			Assert.assertEquals(subscriptionsConsumed.size(), 0, "No subscriptions should be consumed when the attempt to subscribe is not successful.");
		}
	}
	
	
	@Test(	description="subscription-manager: subscribe using --quantity option and assert the available quantity is properly decremented/incremeneted as multiple consumers subscribe/unsubscribe.",
			groups={"blockedByBug-979492"},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void MultiConsumerSubscribeWithQuantity_Test() throws NumberFormatException, JSONException, Exception {
		
		// start by calling SubscribeWithQuantity_Test with the row from the dataProvider where quantity=2
		SubscriptionPool consumer1Pool = null;
		int consumer1Quantity=0;
		int totalPoolQuantity=0;
		for (List<Object> row : getSubscribeWithQuantityDataAsListOfLists()) {
			if (((String)(row.get(2))).equals("2") && ((String)(row.get(4))).startsWith("^Successful")) {	// find the row where quantity.equals("2")
				consumer1Pool = (SubscriptionPool) row.get(1);
				totalPoolQuantity = Integer.valueOf(consumer1Pool.quantity);
				consumer1Quantity = Integer.valueOf((String) row.get(2));
				SubscribeWithQuantity_Test(row.get(0), (SubscriptionPool)row.get(1), (String)row.get(2), (Integer)row.get(3), (String)row.get(4), (String)row.get(5));
				break;
			}
		}
		if (consumer1Pool==null) Assert.fail("Failed to initiate the first consumer for this test.");
		
		// remember the current consumerId
		String consumer1Id = clienttasks.getCurrentConsumerId(); systemConsumerIds.add(consumer1Id);
		
		// clean the client and register a second consumer
		clienttasks.clean(null,null,null);
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, "SubscriptionQuantityConsumer2", null, null, null, null, (String)null, null, null, null, false, false, null, null, null);
		
		// remember the second consumerId
		String consumer2Id = clienttasks.getCurrentConsumerId(); systemConsumerIds.add(consumer2Id);
		
		// find the pool among the available pools
		SubscriptionPool consumer2Pool = SubscriptionPool.findFirstInstanceWithMatchingFieldFromList("poolId", consumer1Pool.poolId, clienttasks.getCurrentlyAvailableSubscriptionPools()); 
		Assert.assertNotNull(consumer2Pool,"Consumer2 found the same pool from which consumer1 subscribed a quantity of "+consumer1Quantity);

		// assert that the quantity available to consumer2 is correct
		int consumer2Quantity = totalPoolQuantity-consumer1Quantity;
		Assert.assertEquals(consumer2Pool.quantity, String.valueOf(consumer2Quantity),"The pool quantity available to consumer2 has been decremented by the quantity consumer1 consumed.");
		
		// assert that consumer2 can NOT oversubscribe
		Assert.assertTrue(!clienttasks.subscribe(null,null,consumer2Pool.poolId,null,null,String.valueOf(consumer2Quantity+1),null,null,null, null, null).getStdout().startsWith("Success"),"An attempt by consumer2 to oversubscribe using the remaining pool quantity+1 should NOT succeed.");

		// assert that consumer2 can successfully consume all the remaining pool quantity
		Assert.assertTrue(clienttasks.subscribe(null,null,consumer2Pool.poolId,null,null,String.valueOf(consumer2Quantity),null,null,null, null, null).getStdout().startsWith("Success"),"An attempt by consumer2 to exactly consume the remaining pool quantity should succeed.");
		
		// start rolling back the subscribes
		
		// restore consumer1, unsubscribe, and assert remaining quantities
		clienttasks.clean(null,null,null);
		clienttasks.register(sm_clientUsername, sm_clientPassword, null, null, null, null, consumer1Id, null, null, null, (String)null, null, null, null, false, false, null, null, null);
		Assert.assertNull(SubscriptionPool.findFirstInstanceWithMatchingFieldFromList("poolId", consumer1Pool.poolId, clienttasks.getCurrentlyAvailableSubscriptionPools()),"SubscriptionPool '"+consumer1Pool.poolId+"' should NOT be available (because consumer1 is already subscribed to it).");
		clienttasks.unsubscribe(null,clienttasks.getCurrentlyConsumedProductSubscriptions().get(0).serialNumber,null,null,null);
		consumer1Pool = SubscriptionPool.findFirstInstanceWithMatchingFieldFromList("poolId", consumer1Pool.poolId, clienttasks.getCurrentlyAvailableSubscriptionPools()); 
		Assert.assertEquals(consumer1Pool.quantity, String.valueOf(totalPoolQuantity-consumer2Quantity),"The pool quantity available to consumer1 has incremented by the quantity consumer1 consumed.");
		
		// restore consumer2, unsubscribe, and assert remaining quantities
		clienttasks.register(sm_clientUsername, sm_clientPassword, null, null, null, null, consumer2Id, null, null, null, (String)null, null, null, null, true, false, null, null, null);
		consumer2Pool = SubscriptionPool.findFirstInstanceWithMatchingFieldFromList("poolId", consumer2Pool.poolId, clienttasks.getCurrentlyAvailableSubscriptionPools());
		//Assert.assertNull(consumer2Pool,"SubscriptionPool '"+consumer2Pool.poolId+"' should NOT be available (because consumer2 is already subscribed to it).");
		Assert.assertNotNull(consumer2Pool,"SubscriptionPool '"+consumer2Pool.poolId+"' should be available even though consumer2 is already subscribed to it because it is multi-entitleable.");
		Assert.assertEquals(consumer2Pool.quantity, String.valueOf(totalPoolQuantity-consumer2Quantity),"The pool quantity available to consumer2 is still decremented by the quantity consumer2 consumed.");
		clienttasks.unsubscribe(null,clienttasks.getCurrentlyConsumedProductSubscriptions().get(0).serialNumber,null,null,null);
		consumer2Pool = SubscriptionPool.findFirstInstanceWithMatchingFieldFromList("poolId", consumer2Pool.poolId, clienttasks.getCurrentlyAvailableSubscriptionPools()); 
		Assert.assertEquals(consumer2Pool.quantity, String.valueOf(totalPoolQuantity),"The pool quantity available to consumer2 has been restored to its original total quantity");
	}
	
	
	@Test(	description="subscription-manager: subscribe to multiple pools using --quantity that exceeds some pools and is under other pools.",
			groups={"blockedByBug-722975"},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void SubscribeWithQuantityToMultiplePools_Test() throws JSONException, Exception {
		
		// register
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, true, false, null, null, null);
		
		// get all the available pools
		List<SubscriptionPool> pools = clienttasks.getCurrentlyAllAvailableSubscriptionPools();
		List<String> poolIds = new ArrayList<String>();
		
		// find the poolIds and their quantities
		List<Integer> quantities = new ArrayList<Integer>();
		for (SubscriptionPool pool : pools) {
			poolIds.add(pool.poolId);
			try {Integer.valueOf(pool.quantity);} catch (NumberFormatException e) {continue;}	// ignore  "unlimited" pools
			quantities.add(Integer.valueOf(pool.quantity));
		}
		Collections.sort(quantities);
		int quantity = quantities.get(quantities.size()/2);	// choose the median as the quantity to subscribe with
		
		// collectively subscribe to all pools with --quantity
		SSHCommandResult subscribeResult = clienttasks.subscribe_(null, null, poolIds, null, null, String.valueOf(quantity), null, null, null, null, null);
		
		/*
		Multi-entitlement not supported for pool with id '8a90f8c6320e9a4401320e9be0e20480'.
		Successfully subscribed the system to Pool 8a90f8c6320e9a4401320e9be196049e
		No free entitlements are available for the pool with id '8a90f8c6320e9a4401320e9be1d404a8'.
		Multi-entitlement not supported for pool with id '8a90f8c6320e9a4401320e9be24004be'.
		Successfully subscribed the system to Pool 8a90f8c6320e9a4401320e9be2e304dd
		No free entitlements are available for the pool with id '8a90f8c6320e9a4401320e9be30c04e8'.
		Multi-entitlement not supported for pool with id '8a90f8c6320e9a4401320e9be3b80505'.
		Multi-entitlement not supported for pool with id '8a90f8c6320e9a4401320e9be4660520'.
		*/
		
		// assert that the expected pools were subscribed to based on quantity
		Assert.assertEquals(subscribeResult.getExitCode(), Integer.valueOf(0), "The exit code from the subscribe command indicates a success.");
		for (SubscriptionPool pool : pools) {
			if (quantity>1 && !CandlepinTasks.isPoolProductMultiEntitlement(sm_clientUsername, sm_clientPassword, sm_serverUrl, pool.poolId)) {
				String expectedSubscribeResultStdoutSubString = String.format("Multi-entitlement not supported for pool with id '%s'.",pool.poolId);
				if (!clienttasks.workaroundForBug876764(sm_serverType)) expectedSubscribeResultStdoutSubString = String.format("Multi-entitlement not supported for pool with ID '%s'.",pool.poolId);
				Assert.assertTrue(subscribeResult.getStdout().contains(expectedSubscribeResultStdoutSubString),"Subscribe attempt to non-multi-entitlement pool '"+pool.poolId+"' was NOT successful when subscribing with --quantity greater than one.");				
			} else if (pool.quantity.equalsIgnoreCase("unlimited") || quantity <= Integer.valueOf(pool.quantity)) {
//				Assert.assertTrue(subscribeResult.getStdout().contains(String.format("Successfully consumed a subscription from the pool with id %s.",pool.poolId)),"Subscribe to pool '"+pool.poolId+"' was successful when subscribing with --quantity less than or equal to the pool's availability.");	// Bug 812410 - Subscription-manager subscribe CLI feedback 
//				Assert.assertTrue(subscribeResult.getStdout().contains(String.format("Successfully consumed a subscription for: %s",pool.subscriptionName)),"Subscribe to pool '"+pool.poolId+"' was successful when subscribing with --quantity less than or equal to the pool's availability.");	// changed by Bug 874804 Subscribe -> Attach
				Assert.assertTrue(subscribeResult.getStdout().contains(String.format("Successfully attached a subscription for: %s",pool.subscriptionName)),"Subscribe to pool '"+pool.poolId+"' was successful when subscribing with --quantity less than or equal to the pool's availability.");
			} else {
				String expectedSubscribeResultStdoutSubString = String.format("No entitlements are available from the pool with id '%s'.",pool.poolId);	// expected string changed by bug 876758
				expectedSubscribeResultStdoutSubString = String.format("No subscriptions are available from the pool with id '%s'.",pool.poolId);
				if (!clienttasks.workaroundForBug876764(sm_serverType)) expectedSubscribeResultStdoutSubString = String.format("No subscriptions are available from the pool with ID '%s'.",pool.poolId);
				Assert.assertTrue(subscribeResult.getStdout().contains(expectedSubscribeResultStdoutSubString),"Subscribe to pool '"+pool.poolId+"' was NOT successful when subscribing with --quantity greater than the pool's availability.");
			}
		}
	}
	
		
	@Test(	description="subscription-manager: subscribe to future subscription pool",
			groups={"blockedByBug-979492"},
			dataProvider="getAllFutureSystemSubscriptionPoolsData",
			enabled=true)
			//@ImplementsNitrateTest(caseId=)
	public void SubscribeToFutureSubscriptionPool_Test(SubscriptionPool pool) throws Exception {
		
		Calendar now = new GregorianCalendar();
		now.setTimeInMillis(System.currentTimeMillis());
		
		// subscribe to the future subscription pool
		SSHCommandResult subscribeResult = clienttasks.subscribe(null,null,pool.poolId,null,null,null,null,null,null,null, null);

		// assert that the granted entitlement cert begins in the future
		EntitlementCert entitlementCert = clienttasks.getEntitlementCertCorrespondingToSubscribedPool(pool);
		Assert.assertNotNull(entitlementCert,"Found the newly granted EntitlementCert on the client after subscribing to future subscription pool '"+pool.poolId+"'.");
		Assert.assertTrue(entitlementCert.validityNotBefore.after(now), "The newly granted EntitlementCert is not valid until the future.  EntitlementCert: "+entitlementCert);
		Assert.assertTrue(entitlementCert.orderNamespace.startDate.after(now), "The newly granted EntitlementCert's OrderNamespace starts in the future.  OrderNamespace: "+entitlementCert.orderNamespace);	
	}	
	
	
	@Test(	description="subscription-manager: subscribe and attach can be used interchangably",
			groups={"blockedByBug-874804","blockedByBug-981689"},
			enabled=true)
			//@ImplementsNitrateTest(caseId=)
	public void AttachDeprecatedSubscribe_Test() throws Exception {
		SSHCommandResult result = client.runCommandAndWait(clienttasks.command+" --help");
		Assert.assertContainsMatch(result.getStdout(), "^\\s*subscribe\\s+Deprecated, see attach$");
		
		SSHCommandResult subscribeResult;
		SSHCommandResult attachResult;
		
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, true, false, null, null, null);
		subscribeResult = client.runCommandAndWait(clienttasks.command+" subscribe --pool=123");
		attachResult = client.runCommandAndWait(clienttasks.command+" attach --pool=123");
		Assert.assertEquals(subscribeResult.toString(), attachResult.toString(), "Results from 'subscribe' and 'attach' module commands should be identical.");
		clienttasks.unregister(null,null,null);
		subscribeResult = client.runCommandAndWait(clienttasks.command+" subscribe --pool=123");
		attachResult = client.runCommandAndWait(clienttasks.command+" attach --pool=123");
		Assert.assertEquals(subscribeResult.toString(), attachResult.toString(), "Results from 'subscribe' and 'attach' module commands should be identical.");
	}
	
	
	
	@BeforeGroups(groups={"setup"}, value={"VerifyOlderClientsAreDeniedEntitlementsToRamAndCoresBasedSubscriptions_Test"})
	public void createFactsFileWithOverridingValues() {
		Map<String,String> factsMap = new HashMap<String,String>();
		factsMap.put("system.certificate_version", "1.0");
		clienttasks.createFactsFileWithOverridingValues(factsMap);
	}
	@Test(	description="Make sure that older subscription-managers are denied attempts to attach a subscription based on: ram, cores",
			groups={"VerifyOlderClientsAreDeniedEntitlementsToRamAndCoresBasedSubscriptions_Test","blockedByBug-957218" },
			dataProvider="getAllAvailableRamCoresSubscriptionPoolsData",
			enabled=true)	// TODO THIS TEST IS A CANDIDATE FOR DISABLEMENT AFTER IMPLEMENTATION OF BUG 957218
	//@ImplementsNitrateTest(caseId=)
	public void VerifyOlderClientsAreDeniedEntitlementsToRamAndCoresBasedSubscriptions_Test(Object bugzilla, SubscriptionPool pool) throws JSONException, Exception {
		if (true) {
			log.warning("Effectively, this test is now obsolete due to RFE Bugzilla https://bugzilla.redhat.com/show_bug.cgi?id=888866 which reversed the original intent of this test.");
			String systemCertificateVersion = clienttasks.getFactValue("system.certificate_version");
			EntitlementCert entitlementCert = clienttasks.getEntitlementCertFromEntitlementCertFile(clienttasks.subscribeToSubscriptionPool(pool,sm_serverAdminUsername,sm_serverAdminPassword,sm_serverUrl));
			Assert.assertEquals(entitlementCert.version, "1.0", "RAM and Core based subscriptions are now granted to older subscription-manager clients regardless of version.  See RFE bugzilla https://bugzilla.redhat.com/show_bug.cgi?id=888866");
			Assert.assertNull(entitlementCert.orderNamespace.coreLimit, "Core limit included in an entitlement cert when system.certificate_version is old '"+systemCertificateVersion+"'.");
			Assert.assertNull(entitlementCert.orderNamespace.ramLimit, "RAM limit included in an entitlement cert when system.certificate_version is old '"+systemCertificateVersion+"'.");
			return;
		}
		
		/*
		The way that this works is that all attributes that are specified on a
		pool are version checked. Here are the current versions:
		ram: 3.1
		cores: 3.2
		sockets: 1.0

		If a pool has a ram attribute, the minimum required version will be 3.1.
		If a pool has a cores attribute, the minimum required version will be 3.2
		if a pool has cores AND ram attributes, the minimum required version
		will be 3.2

		Again, each attribute on the pool will be checked against the above
		versions, and the largest found will be the minimum required version
		that the client must support in order to attach that sub.

		Supported versions do not change based on stacking... it changes when
		the certificate content changes. i.e when the cores attribute was added
		to the cert. It has no relation to what our rules support.

		So both examples above are correct since the system is 1.0. They are
		dealing with RAM subscriptions, so they should be restricted to clients
		supporting certificate versions >= 3.1

		If the test subs were to include cores, it would require 3.2

		Clear as mud!?

		Ping me if you have more questions about this.

		--mstead
		*/

		//	[root@jsefler-5 ~]# subscription-manager subscribe --pool=8a90f8313e472bce013e472d22150352
		//	The client must support at least v3.1 certificates in order to use subscription: Multi-Attribute (non-stackable) (24 cores, 6 sockets, 8GB RAM). A newer client may be available to address this problem.
		//	[root@jsefler-5 ~]# 
		
		String systemCertificateVersion = clienttasks.getFactValue("system.certificate_version");
		SSHCommandResult subscribeResult = clienttasks.subscribe_(null, null, pool.poolId, null, null, null, null, null, null, null, null);
		Assert.assertEquals(subscribeResult.getExitCode(), new Integer(255), "Exitcode from an attempt to subscribe to '"+pool.subscriptionName+"' when system.certificate_version is old '"+systemCertificateVersion+"'.");
		
		// CORES-based subscriptions
		if (CandlepinTasks.getPoolProductAttributeValue(sm_clientUsername, sm_clientPassword, sm_serverUrl, pool.poolId, "cores")!=null) {
			Assert.assertEquals(subscribeResult.getStderr().trim(), String.format("The client must support at least v%s certificates in order to use subscription: %s. A newer client may be available to address this problem.","3.2",pool.subscriptionName),
					"Stderr from an attempt to subscribe to '"+pool.subscriptionName+"' a CORES-based subscription when system.certificate_version is < 3.2");
			Assert.assertEquals(subscribeResult.getStdout().trim(), "",
					"Stdout from an attempt to subscribe to '"+pool.subscriptionName+"' a CORES-based subscription when system.certificate_version is < 3.2");
			return;
		}
		
		// stackable RAM-based subscriptions
		/* I assumed this block was a valid test.  I was wrong.  See mstead's notes above...
		if (CandlepinTasks.getPoolProductAttributeValue(sm_clientUsername, sm_clientPassword, sm_serverUrl, pool.poolId, "ram")!=null &&
			CandlepinTasks.getPoolProductAttributeValue(sm_clientUsername, sm_clientPassword, sm_serverUrl, pool.poolId, "stacking_id")!=null) {
			Assert.assertEquals(subscribeResult.getStderr().trim(), String.format("The client must support at least v%s certificates in order to use subscription: %s. A newer client may be available to address this problem.","3.2",pool.subscriptionName),
					"Stderr from an attempt to subscribe to '"+pool.subscriptionName+"' a RAM-based subscription when system.certificate_version is < 3.2");
			Assert.assertEquals(subscribeResult.getStdout().trim(), "",
					"Stdout from an attempt to subscribe to '"+pool.subscriptionName+"' a RAM-based subscription when system.certificate_version is < 3.2");
			return;
		}
		*/

		
		// RAM-based subscriptions
		if (CandlepinTasks.getPoolProductAttributeValue(sm_clientUsername, sm_clientPassword, sm_serverUrl, pool.poolId, "ram")!=null) {
			Assert.assertEquals(subscribeResult.getStderr().trim(), String.format("The client must support at least v%s certificates in order to use subscription: %s. A newer client may be available to address this problem.","3.1",pool.subscriptionName),
					"Stderr from an attempt to subscribe to '"+pool.subscriptionName+"' a RAM-based subscription when system.certificate_version is < 3.1");
			Assert.assertEquals(subscribeResult.getStdout().trim(), "",
					"Stdout from an attempt to subscribe to '"+pool.subscriptionName+"' a RAM-based subscription when system.certificate_version is < 3.1");
			return;
		}
		
		Assert.fail("Do not know how to assert the attempted attachment of '"+pool.subscriptionName+"'.");
	}
	@AfterGroups(groups={"setup"}, value={"VerifyOlderClientsAreDeniedEntitlementsToRamAndCoresBasedSubscriptions_Test","AutoSubscribeOnVirtualSystemsFavorVirtualPools_Test"})
	@AfterClass(groups={"setup"})	// insurance; not really needed
	public void deleteFactsFileWithOverridingValues() {
		clienttasks.deleteFactsFileWithOverridingValues();
	}
	@DataProvider(name="getAllAvailableRamCoresSubscriptionPoolsData")
	public Object[][] getAllAvailableRamCoresSubscriptionPoolsDataAs2dArray() throws JSONException, Exception {
		return TestNGUtils.convertListOfListsTo2dArray(getAllAvailableRamCoresSubscriptionPoolsDataAsListOfLists());
	}
	protected List<List<Object>>getAllAvailableRamCoresSubscriptionPoolsDataAsListOfLists() throws JSONException, Exception {
		List<List<Object>> ll = new ArrayList<List<Object>>(); if (!isSetupBeforeSuiteComplete) return ll;
		
		for (List<Object> list : getAllAvailableSubscriptionPoolsDataAsListOfLists()) {
			SubscriptionPool pool = (SubscriptionPool) list.get(0);
			
			// include RAM-based subscriptions
			if (CandlepinTasks.getPoolProductAttributeValue(sm_clientUsername, sm_clientPassword, sm_serverUrl, pool.poolId, "ram")!=null) {
				ll.add(Arrays.asList(new Object[] {null,	pool}));
				continue;
			}
			
			// include CORES-based subscriptions
			if (CandlepinTasks.getPoolProductAttributeValue(sm_clientUsername, sm_clientPassword, sm_serverUrl, pool.poolId, "cores")!=null) {
				ll.add(Arrays.asList(new Object[] {null,	pool}));
				continue;
			}
			
		}
		
		return ll;
	}
	
	
	@Test(	description="subscription-manager: autosubscribe on a virtual system should favor virtual pools",
			groups={"blockedByBug-927101","AutoSubscribeOnVirtualSystemsFavorVirtualPools_Test"},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void AutoSubscribeOnVirtualSystemsFavorVirtualPools_Test() throws JSONException, Exception {
		
		// force the system to be virtual
		Map<String, String> factsMap = new HashMap<String, String>();
		factsMap.put("virt.is_guest", String.valueOf(Boolean.TRUE));
		clienttasks.createFactsFileWithOverridingValues(factsMap);
		
		// get the current product certs
		List<ProductCert> productCerts = clienttasks.getCurrentProductCerts();
		
		// register
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, true, false, null, null, null);
		
		// get the available pools
		List<SubscriptionPool> availablePools = clienttasks.getCurrentlyAvailableSubscriptionPools();
		
		// find available subscriptions for which both a Virtual and a Physical pool is available
		boolean testAttempted = false;
		for (SubscriptionPool virtualPool : SubscriptionPool.findAllInstancesWithMatchingFieldFromList("machineType", "Virtual", availablePools)) {
			for (SubscriptionPool physicalPool : SubscriptionPool.findAllInstancesWithMatchingFieldFromList("machineType", "Physical", availablePools)) {
				if (virtualPool.productId.equals(physicalPool.productId)) {
					// found a pair of subscriptions that we can test
					
					// get the product certs that are provided by these pools
					List<String> virtualProductIds = CandlepinTasks.getPoolProvidedProductIds(sm_clientUsername, sm_clientPassword, sm_serverUrl, virtualPool.poolId);
					List<String> physicalProductIds = CandlepinTasks.getPoolProvidedProductIds(sm_clientUsername, sm_clientPassword, sm_serverUrl, physicalPool.poolId);
					Assert.assertTrue(virtualProductIds.containsAll(physicalProductIds)&&physicalProductIds.containsAll(virtualProductIds), "Provided product ids from virtual pool '"+virtualPool.poolId+"' and physical pool '"+physicalPool.poolId+"' sharing a common productId '"+virtualPool.productId+"' should be the same.");
					
					// configure a temporary product cert directory containing only these provided product certs
					RemoteFileTasks.runCommandAndAssert(client,"mkdir -p "+tmpProductCertDir,Integer.valueOf(0));
					RemoteFileTasks.runCommandAndAssert(client,"rm -f "+tmpProductCertDir+"/*.pem",Integer.valueOf(0));
					List<String> productCertIdsFound = new ArrayList<String>();
					List<String> productCertNamesFound = new ArrayList<String>();
					for (ProductCert productCert : productCerts) {
						if (virtualProductIds.contains(productCert.productId)) {
							RemoteFileTasks.runCommandAndAssert(client,"cp "+productCert.file+" "+tmpProductCertDir,Integer.valueOf(0));
							productCertIdsFound.add(productCert.productId);
							productCertNamesFound.add(productCert.productName);
						}
					}
					clienttasks.updateConfFileParameter(clienttasks.rhsmConfFile, "productCertDir", tmpProductCertDir);
					
					if (!productCertIdsFound.isEmpty()) {
						
						// start testing... autosubscribe and assert the consumed subscriptions are Virtual
						clienttasks.unsubscribe(true, (BigInteger)null, null, null, null);
						clienttasks.subscribe(true, null, null, (String)null, null, null, null, null, null, null, null);
						for (ProductSubscription productSubscription : clienttasks.getCurrentlyConsumedProductSubscriptions()) {
							Assert.assertEquals(productSubscription.machineType, "Virtual", "Autosubscribing a virtual system should favor granting an entitlement from a Virtual pool that provides "+productCertIdsFound+" over a Physical pool that provides "+productCertIdsFound+"'.");
							Assert.assertTrue(productSubscription.provides.containsAll(productCertNamesFound), "The autosubscribed virtual subscription '"+productSubscription+"' provides for all of the installed products "+productCertNamesFound+".  (Note: This could potentially fail when the provided product names are do not exactly match the installed product cert names which is okay since the productIds are what really matter).");	// TODO We may need to comment this out or fix it if it starts failing due to changes in the subscription data.
							testAttempted = true;
						}
						//testAttempted = true;	// relocated to inside of loop above
						
					} else {
						log.info("Did not find any installed product certs provided by Virtual and Physical subscriptions '"+virtualPool.productId+"'.");
					}
				}
			}
		}
		if (!testAttempted) throw new SkipException("Did not find all the resources needed to attempt this test.");
		
	}
	@AfterGroups(groups="setup", value = {"AutoSubscribeOnVirtualSystemsFavorVirtualPools_Test"})
	public void restoreRhsmProductCertDir() {
		if (clienttasks==null) return;
		if (rhsmProductCertDir==null) return;	
		log.info("Restoring the originally configured product cert directory...");
		clienttasks.updateConfFileParameter(clienttasks.rhsmConfFile, "productCertDir", rhsmProductCertDir);
	}
	protected String rhsmProductCertDir = null;
	protected final String tmpProductCertDir = "/tmp/sm-tmpProductCertDir";
	
	
	
	
	
	
	
	// Candidates for an automated Test:
	// TODO Bug 668032 - rhsm not logging subscriptions and products properly //done --shwetha https://github.com/RedHatQE/rhsm-qe/issues/198
	// TODO Bug 670831 - Entitlement Start Dates should be the Subscription Start Date //Done --shwetha https://github.com/RedHatQE/rhsm-qe/issues/199
	// TODO Bug 664847 - Autobind logic should respect the architecture attribute //working on https://github.com/RedHatQE/rhsm-qe/issues/200
	// TODO Bug 676377 - rhsm-compliance-icon's status can be a day out of sync - could use dbus-monitor to assert that the dbus message is sent on the expected compliance changing events https://github.com/RedHatQE/rhsm-qe/issues/201
	// TODO Bug 739790 - Product "RHEL Workstation" has a valid stacking_id but its socket_limit is 0 https://github.com/RedHatQE/rhsm-qe/issues/202
	// TODO Bug 707641 - CLI auto-subscribe tries to re-use basic auth credentials. https://github.com/RedHatQE/rhsm-qe/issues/203
	
	// TODO Write an autosubscribe bug... 1. Subscribe to all avail and note the list of installed products (Subscribed, Partially, Not) 
	//									  2. Unsubscribe all  3. Autosubscribe and verfy same installed product status (Subscribed, Not)//done --shwetha https://github.com/RedHatQE/rhsm-qe/issues/204
	// TODO Bug 746035 - autosubscribe should NOT consider existing future entitlements when determining what pools and quantity should be autosubscribed //working on https://github.com/RedHatQE/rhsm-qe/issues/205
	// TODO Bug 747399 - if consumer does not have architecture then we should not check for it https://github.com/RedHatQE/rhsm-qe/issues/206
	// TODO Bug 743704 - autosubscribe ignores socket count on non multi-entitle subscriptions //done --shwetha https://github.com/RedHatQE/rhsm-qe/issues/207
	// TODO Bug 740788 - Getting error with quantity subscribe using subscription-assistance page 
	//                   Write an autosubscribe test that mimics partial subscriptions in https://bugzilla.redhat.com/show_bug.cgi?id=740788#c12 https://github.com/RedHatQE/rhsm-qe/issues/208
	// TODO Bug 720360 - subscription-manager: entitlement key files created with weak permissions // done --shwetha https://github.com/RedHatQE/rhsm-qe/issues/209
	// TODO Bug 772218 - Subscription manager silently rejects pools requested in an incorrect format.//done --shwetha https://github.com/RedHatQE/rhsm-qe/issues/211
	// TODO Bug 878994 - 500 errors in stage on subscribe/unsubscribe - NEED TO INSTALL A PRODUCT CERTS FROM TESTDATA AND MAKE SURE THEY DO NOT TRIP UP THE IT PRODUCT ADAPTERS https://github.com/RedHatQE/rhsm-qe/issues/212

	
	// Configuration Methods ***********************************************************************
	@AfterClass(groups={"setup"})
	public void unregisterAllSystemConsumerIds() {
		if (clienttasks!=null) {
			for (String systemConsumerId : systemConsumerIds) {
				clienttasks.register_(sm_clientUsername,sm_clientPassword,null,null,null,null,systemConsumerId, null, null, null, (String)null, null, null, null, Boolean.TRUE, null, null, null, null);
				clienttasks.unsubscribe_(true, (BigInteger)null, null, null, null);
				clienttasks.unregister_(null, null, null);
			}
			systemConsumerIds.clear();
		}
	}
	

	
	// Protected Methods ***********************************************************************

	protected List<String> systemConsumerIds = new ArrayList<String>();
	
	protected void unsubscribeRandomly() {
		log.info("Unsubscribing from a random selection of entitlements (for the sake of test variability)...");
		for (EntitlementCert entitlementCert: clienttasks.getCurrentEntitlementCerts()) {
			if (randomGenerator.nextInt(2)==1) {
				clienttasks.unsubscribeFromSerialNumber(entitlementCert.serialNumber);
			}
		}
	}
	
	
	// Data Providers ***********************************************************************

	@DataProvider(name="getInstalledProductCertsData")
	public Object[][] getInstalledProductCertsDataAs2dArray() {
		return TestNGUtils.convertListOfListsTo2dArray(getInstalledProductCertsDataAsListOfLists());
	}
	protected List<List<Object>> getInstalledProductCertsDataAsListOfLists() {
		List<List<Object>> ll = new ArrayList<List<Object>>();
		
		for (ProductCert productCert: clienttasks.getCurrentProductCerts()) {
			BlockedByBzBug blockedByBzBug = null;
			
			// Bug 951633 - installed product with comma separated arch attribute fails to go green 
			if (productCert.productNamespace.arch.contains(",")) blockedByBzBug = new BlockedByBzBug("951633");
			
			ll.add(Arrays.asList(new Object[]{blockedByBzBug, productCert}));
		}
		
		return ll;
	}
	
	
	
	@DataProvider(name="getCertFrequencyData")
	public Object[][] getCertFrequencyDataAs2dArray() {
		return TestNGUtils.convertListOfListsTo2dArray(getCertFrequencyDataAsListOfLists());
	}
	protected List<List<Object>> getCertFrequencyDataAsListOfLists() {
		List<List<Object>> ll = new ArrayList<List<Object>>();
		
		// int minutes
//UNCOMMENT AFTER DEBUGING		ll.add(Arrays.asList(new Object[]{2}));
		ll.add(Arrays.asList(new Object[]{1}));
		
		return ll;
	}
	
	
	@DataProvider(name="getSubscribeWithQuantityData")
	public Object[][] getSubscribeWithQuantityDataAs2dArray() throws JSONException, Exception {
		return TestNGUtils.convertListOfListsTo2dArray(getSubscribeWithQuantityDataAsListOfLists());
	}
	protected List<List<Object>>getSubscribeWithQuantityDataAsListOfLists() throws JSONException, Exception {
		List<List<Object>> ll = new ArrayList<List<Object>>(); if (!isSetupBeforeSuiteComplete) return ll;
		
		// register
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, "SubscriptionQuantityConsumer", null, null, null, null, (String)null, null, null, null, true, false, null, null, null);
		
//		// find a random testpool with a positive quantity
//		List<SubscriptionPool> pools = clienttasks.getCurrentlyAvailableSubscriptionPools();
//		SubscriptionPool testPool;
//		int i = 1000;	// avoid an infinite loop
//		do {
//			testPool = pools.get(randomGenerator.nextInt(pools.size())); // randomly pick a pool
//		} while (!testPool.quantity.equalsIgnoreCase("unlimited") && Integer.valueOf(testPool.quantity)<2 && /*avoid an infinite loop*/i-->0);

		
		// find pools with a positive quantity that have a productAttribute set for "multi-entitlement"
		SubscriptionPool poolWithMultiEntitlementNull = null;
		SubscriptionPool poolWithMultiEntitlementYes = null;
		SubscriptionPool poolWithMultiEntitlementNo = null;
		for (SubscriptionPool pool : clienttasks.getCurrentlyAvailableSubscriptionPools()) {
			if (!pool.quantity.equalsIgnoreCase("unlimited") && Integer.valueOf(pool.quantity)<2) continue;	// skip pools that don't have enough quantity left to consume
			
			Boolean isMultiEntitlementPool = null;	// indicates that the pool's product does NOT have the "multi-entitlement" attribute
			JSONObject jsonPool = new JSONObject(CandlepinTasks.getResourceUsingRESTfulAPI(sm_clientUsername, sm_clientPassword, sm_serverUrl, "/pools/"+pool.poolId));	
			JSONArray jsonProductAttributes = jsonPool.getJSONArray("productAttributes");
			// loop through the productAttributes of this pool looking for the "multi-entitlement" attribute
			for (int j = 0; j < jsonProductAttributes.length(); j++) {
				JSONObject jsonProductAttribute = (JSONObject) jsonProductAttributes.get(j);
				String productAttributeName = jsonProductAttribute.getString("name");
				if (productAttributeName.equals("multi-entitlement")) {
					//multi_entitlement = jsonProductAttribute.getBoolean("value");
					isMultiEntitlementPool = jsonProductAttribute.getString("value").equalsIgnoreCase("yes") || jsonProductAttribute.getString("value").equals("1");
					break;
				}
			}
			
			if (isMultiEntitlementPool == null) {
				poolWithMultiEntitlementNull = pool;
			} else if (isMultiEntitlementPool) {
				poolWithMultiEntitlementYes = pool;
			} else {
				poolWithMultiEntitlementNo = pool;
			}
		}
		SubscriptionPool pool;
		
		
		// Object meta, String poolId, String quantity, Integer expectedExitCode, String expectedStdoutRegex, String expectedStderrRegex
		String expectedStdout;
		pool= poolWithMultiEntitlementYes;
		if (pool!=null) {
			ll.add(Arrays.asList(new Object[] {null,							pool,	"Two",												Integer.valueOf(255),	"^Error: Quantity must be a positive number.$".replace("number","integer")/* due to bug 746262*/,	null}));
			ll.add(Arrays.asList(new Object[] {new BlockedByBzBug("722554"),	pool,	"-1",												Integer.valueOf(255),	"^Error: Quantity must be a positive number.$".replace("number","integer")/* due to bug 746262*/,	null}));
			ll.add(Arrays.asList(new Object[] {new BlockedByBzBug("722554"),	pool,	"0",												Integer.valueOf(255),	"^Error: Quantity must be a positive number.$".replace("number","integer")/* due to bug 746262*/,	null}));
//			ll.add(Arrays.asList(new Object[] {null,							pool,	"1",												Integer.valueOf(0),		"^"+String.format("Successfully consumed a subscription from the pool with id %s.",pool.poolId)+"$",	null}));	// Bug 812410 - Subscription-manager subscribe CLI feedback 
//			ll.add(Arrays.asList(new Object[] {null,							pool,	"1",												Integer.valueOf(0),		"^"+String.format("Successfully consumed a subscription for: %s",pool.subscriptionName.replaceAll("\\(","\\\\(").replaceAll("\\)","\\\\)"))+"$",	null}));	// changed by Bug 874804 Subscribe -> Attach
			ll.add(Arrays.asList(new Object[] {null,							pool,	"1",												Integer.valueOf(0),		"^"+String.format("Successfully attached a subscription for: %s",pool.subscriptionName.replaceAll("\\(","\\\\(").replaceAll("\\)","\\\\)"))+"$",	null}));
//			ll.add(Arrays.asList(new Object[] {null,							pool,	"2",												Integer.valueOf(0),		"^"+String.format("Successfully consumed a subscription from the pool with id %s.",pool.poolId)+"$",	null}));	// Bug 812410 - Subscription-manager subscribe CLI feedback 
//			ll.add(Arrays.asList(new Object[] {null,							pool,	"2",												Integer.valueOf(0),		"^"+String.format("Successfully consumed a subscription for: %s",pool.subscriptionName.replaceAll("\\(","\\\\(").replaceAll("\\)","\\\\)"))+"$",	null}));	// changed by Bug 874804 Subscribe -> Attach
			ll.add(Arrays.asList(new Object[] {null,							pool,	"2",												Integer.valueOf(0),		"^"+String.format("Successfully attached a subscription for: %s",pool.subscriptionName.replaceAll("\\(","\\\\(").replaceAll("\\)","\\\\)"))+"$",	null}));
//			ll.add(Arrays.asList(new Object[] {new BlockedByBzBug("746262"),	pool,	"+2",												Integer.valueOf(0),		"^"+String.format("Successfully consumed a subscription from the pool with id %s.",pool.poolId)+"$",	null}));	// Bug 812410 - Subscription-manager subscribe CLI feedback 
//			ll.add(Arrays.asList(new Object[] {new BlockedByBzBug("746262"),	pool,	"+2",												Integer.valueOf(0),		"^"+String.format("Successfully consumed a subscription for: %s",pool.subscriptionName.replaceAll("\\(","\\\\(").replaceAll("\\)","\\\\)"))+"$",	null}));	// changed by Bug 874804 Subscribe -> Attach
			ll.add(Arrays.asList(new Object[] {new BlockedByBzBug("746262"),	pool,	"+2",												Integer.valueOf(0),		"^"+String.format("Successfully attached a subscription for: %s",pool.subscriptionName.replaceAll("\\(","\\\\(").replaceAll("\\)","\\\\)"))+"$",	null}));
//			ll.add(Arrays.asList(new Object[] {null,							pool,	pool.quantity,										Integer.valueOf(0),		"^"+String.format("Successfully consumed a subscription from the pool with id %s.",pool.poolId)+"$",	null}));	// Bug 812410 - Subscription-manager subscribe CLI feedback 
//			ll.add(Arrays.asList(new Object[] {null,							pool,	pool.quantity,										Integer.valueOf(0),		"^"+String.format("Successfully consumed a subscription for: %s",pool.subscriptionName.replaceAll("\\(","\\\\(").replaceAll("\\)","\\\\)"))+"$",	null}));	// changed by Bug 874804 Subscribe -> Attach
			ll.add(Arrays.asList(new Object[] {null,							pool,	pool.quantity,										Integer.valueOf(0),		"^"+String.format("Successfully attached a subscription for: %s",pool.subscriptionName.replaceAll("\\(","\\\\(").replaceAll("\\)","\\\\)"))+"$",	null}));
			expectedStdout = String.format("No entitlements are available from the pool with id '%s'.",pool.poolId);	// expected string changed by bug 876758
			expectedStdout = String.format("No subscriptions are available from the pool with id '%s'.",pool.poolId);
			if (!clienttasks.workaroundForBug876764(sm_serverType)) expectedStdout = String.format("No subscriptions are available from the pool with ID '%s'.",pool.poolId);
			ll.add(Arrays.asList(new Object[] {null,							pool,	String.valueOf(Integer.valueOf(pool.quantity)+1),	Integer.valueOf(1),		"^"+expectedStdout+"$",	null}));
			ll.add(Arrays.asList(new Object[] {null,							pool,	String.valueOf(Integer.valueOf(pool.quantity)*10),	Integer.valueOf(1),		"^"+expectedStdout+"$",	null}));
		} else {
			ll.add(Arrays.asList(new Object[] {null,	null,	null,	null,	null,	"Could NOT find an available subscription pool with a \"multi-entitlement\" product attribute set to yes."}));
		}
		
		pool= poolWithMultiEntitlementNo;
		if (pool!=null) {
//			ll.add(Arrays.asList(new Object[] {null,							pool,	"1",												Integer.valueOf(0),		"^"+String.format("Successfully consumed a subscription from the pool with id %s.",pool.poolId)+"$",	null}));	// Bug 812410 - Subscription-manager subscribe CLI feedback 
//			ll.add(Arrays.asList(new Object[] {null,							pool,	"1",												Integer.valueOf(0),		"^"+String.format("Successfully consumed a subscription for: %s",pool.subscriptionName.replaceAll("\\(","\\\\(").replaceAll("\\)","\\\\)"))+"$",	null}));	// changed by Bug 874804 Subscribe -> Attach
			ll.add(Arrays.asList(new Object[] {null,							pool,	"1",												Integer.valueOf(0),		"^"+String.format("Successfully attached a subscription for: %s",pool.subscriptionName.replaceAll("\\(","\\\\(").replaceAll("\\)","\\\\)"))+"$",	null}));
			expectedStdout = String.format("Multi-entitlement not supported for pool with id '%s'.",pool.poolId);
			if (!clienttasks.workaroundForBug876764(sm_serverType))  expectedStdout = String.format("Multi-entitlement not supported for pool with ID '%s'.",pool.poolId);
			ll.add(Arrays.asList(new Object[] {new BlockedByBzBug("722975"),	pool,	"2",												Integer.valueOf(1),		"^"+expectedStdout+"$",	null}));
		} else {
			ll.add(Arrays.asList(new Object[] {null,	null,	null,	null,	null,	"Could NOT find an available subscription pool with a \"multi-entitlement\" product attribute set to no."}));
		}
		
		pool= poolWithMultiEntitlementNull;
		if (pool!=null) {
//			ll.add(Arrays.asList(new Object[] {null,							pool,	"1",												Integer.valueOf(0),		"^"+String.format("Successfully consumed a subscription from the pool with id %s.",pool.poolId)+"$",	null}));	// Bug 812410 - Subscription-manager subscribe CLI feedback 
//			ll.add(Arrays.asList(new Object[] {null,							pool,	"1",												Integer.valueOf(0),		"^"+String.format("Successfully consumed a subscription for: %s",pool.subscriptionName.replaceAll("\\(","\\\\(").replaceAll("\\)","\\\\)"))+"$",	null}));	// changed by Bug 874804 Subscribe -> Attach
			ll.add(Arrays.asList(new Object[] {null,							pool,	"1",												Integer.valueOf(0),		"^"+String.format("Successfully attached a subscription for: %s",pool.subscriptionName.replaceAll("\\(","\\\\(").replaceAll("\\)","\\\\)"))+"$",	null}));
			expectedStdout = String.format("Multi-entitlement not supported for pool with id '%s'.",pool.poolId);
			if (!clienttasks.workaroundForBug876764(sm_serverType))  expectedStdout = String.format("Multi-entitlement not supported for pool with ID '%s'.",pool.poolId);
			ll.add(Arrays.asList(new Object[] {new BlockedByBzBug("722975"),	pool,	"2",												Integer.valueOf(1),		"^"+expectedStdout+"$",	null}));
		} else {
			ll.add(Arrays.asList(new Object[] {null,	null,	null,	null,	null,	"Could NOT find an available subscription pool without a \"multi-entitlement\" product attribute."}));
		}
		
		return ll;
	}
	
	
	@DataProvider(name="getSubscribeWithAutoAndServiceLevelData")
	public Object[][] getSubscribeWithAutoAndServiceLevelDataAs2dArray() throws JSONException, Exception {
		return TestNGUtils.convertListOfListsTo2dArray(getSubscribeWithAutoAndServiceLevelDataAsListOfLists());
	}
	protected List<List<Object>>getSubscribeWithAutoAndServiceLevelDataAsListOfLists() throws JSONException, Exception {
		List<List<Object>> ll = getAllAvailableServiceLevelDataAsListOfLists(); if (!isSetupBeforeSuiteComplete) return ll;
		
		// throw in null and "" as a possible service levels
		// Object bugzilla, String org, String serviceLevel
		ll.add(Arrays.asList(new Object[] {null,	null}));
		ll.add(Arrays.asList(new Object[] {null,	""}));
		
		return ll;
	}
	
}

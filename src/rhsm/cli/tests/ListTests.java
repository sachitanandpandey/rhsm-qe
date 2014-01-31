package rhsm.cli.tests;

import java.io.File;
import java.math.BigInteger;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
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
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeGroups;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.redhat.qe.Assert;
import com.redhat.qe.auto.bugzilla.BlockedByBzBug;
import com.redhat.qe.auto.bugzilla.BzChecker;
import com.redhat.qe.auto.tcms.ImplementsNitrateTest;
import com.redhat.qe.auto.testng.TestNGUtils;

import rhsm.base.CandlepinType;
import rhsm.base.ConsumerType;
import rhsm.base.SubscriptionManagerCLITestScript;
import rhsm.cli.tasks.CandlepinTasks;
import rhsm.data.EntitlementCert;
import rhsm.data.InstalledProduct;
import rhsm.data.OrderNamespace;
import rhsm.data.ProductCert;
import rhsm.data.ProductNamespace;
import rhsm.data.ProductSubscription;
import rhsm.data.SubscriptionPool;
import com.redhat.qe.tools.SSHCommandResult;

/**
 *  @author ssalevan
 *  @author jsefler
 *
 */
@Test(groups={"ListTests"})
public class ListTests extends SubscriptionManagerCLITestScript{
	
	
	// Test Methods ***********************************************************************
	
	@Test(	description="subscription-manager-cli: list available subscriptions (when not consuming)",
			groups={},
			enabled=true)
	//@ImplementsNitrateTest(caseId=41678)
	public void EnsureAvailableSubscriptionsListed_Test() {
		clienttasks.unregister(null, null, null);
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, null, false, null, null, null);
		String availableSubscriptionPools = clienttasks.listAvailableSubscriptionPools().getStdout();
		Assert.assertContainsMatch(availableSubscriptionPools, "Available Subscriptions","" +
				"Available Subscriptions are listed for '"+sm_clientUsername+"' to consume.");
		Assert.assertContainsNoMatch(availableSubscriptionPools, "No available subscription pools to list",
				"Available Subscriptions are listed for '"+sm_clientUsername+"' to consume.");

		log.warning("These manual TCMS instructions are not really achievable in this automated test...");
		log.warning(" * List produced matches the known data contained on the Candlepin server");
		log.warning(" * Confirm that the marketing names match.. see prereq link https://engineering.redhat.com/trac/IntegratedMgmtQE/wiki/sm-prerequisites");
		log.warning(" * Match the marketing names w/ https://www.redhat.com/products/");
	}
	
	
	@Test(	description="subscription-manager-cli: list available subscriptions - verify that among all the subscriptions available to this consumer, those that satisfy the system hardware are listed as available",
			groups={"AcceptanceTests", "blockedByBug-712502","unsubscribeBeforeGroup"},
			dataProvider="getAvailableSystemSubscriptionPoolProductData",
			enabled=true)
	@ImplementsNitrateTest(caseId=41678)
	public void EnsureHardwareMatchingSubscriptionsAreListedAsAvailable_Test(String productId, JSONArray bundledProductDataAsJSONArray) throws JSONException, Exception {
//if(!productId.equals("null-sockets")) throw new SkipException("debugging...");		
		// implicitly registered in dataProvider; no need to register with force; saves time
		//clienttasks.register(clientusername, clientpassword, null, null, null, null, true, null, null, null);
		SubscriptionPool pool = SubscriptionPool.findFirstInstanceWithMatchingFieldFromList("productId", productId, clienttasks.getCurrentlyAvailableSubscriptionPools());
		if (pool==null) {	// when pool is null, the most likely error is that all of the available subscriptions from the pools are being consumed, let's check...
			for (String poolId: CandlepinTasks.getPoolIdsForProductId(sm_clientUsername, sm_clientPassword, sm_serverUrl, clienttasks.getCurrentlyRegisteredOwnerKey(), productId)) {
				int quantity = (Integer) CandlepinTasks.getPoolValue(sm_clientUsername, sm_clientPassword, sm_serverUrl, poolId, "quantity");
				int consumed = (Integer) CandlepinTasks.getPoolValue(sm_clientUsername, sm_clientPassword, sm_serverUrl, poolId, "consumed");
				if (consumed>=quantity) {
					log.warning("It appears that the total quantity '"+quantity+"' of subscriptions from poolId '"+poolId+"' for product '"+productId+"' are being consumed.");
				}
			}	
		}
		Assert.assertNotNull(pool, "Expected SubscriptionPool with ProductId '"+productId+"' is listed as available for subscribing.");
	}
	
	
	@Test(	description="subscription-manager-cli: list available subscriptions - verify that among all the subscriptions available to this consumer, those that do NOT satisfy the hardware are NOT listed as available",
			groups={"AcceptanceTests", "blockedByBug-712502","unsubscribeBeforeGroup"},
			dataProvider="getNonAvailableSystemSubscriptionPoolProductData",
			enabled=true)
	@ImplementsNitrateTest(caseId=41678)
	public void EnsureNonHardwareMatchingSubscriptionsAreNotListedAsAvailable_Test(String productId) {
		// implicitly registered in dataProvider; no need to register with force; saves time
		//clienttasks.register(clientusername, clientpassword, null, null, null, null, true, null, null, null);
		
		SubscriptionPool pool = SubscriptionPool.findFirstInstanceWithMatchingFieldFromList("productId", productId, clienttasks.getCurrentlyAvailableSubscriptionPools());
		Assert.assertNull(pool, "As expected, SubscriptionPool with ProductId '"+productId+"' is NOT listed as available for subscribing.");
	}
	
	
	@Test(	description="subscription-manager-cli: list consumed entitlements (when not consuming)",
			groups={},
			enabled=true)
	//@ImplementsNitrateTest(caseId=41679)
	public void EnsureConsumedEntitlementsListed_Test() {
		clienttasks.unregister(null, null, null);
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, null, false, null, null, null);
		String consumedProductSubscription = clienttasks.listConsumedProductSubscriptions().getStdout();
		Assert.assertContainsMatch(consumedProductSubscription, "No consumed subscription pools to list",
				"No consumed subscription pools listed for '"+sm_clientUsername+"' after registering (without autosubscribe).");
	}
	
	
	@Test(	description="subscription-manager-cli: list consumed entitlements",
			groups={},
			dataProvider="getAllSystemSubscriptionPoolProductData",
			enabled=true)
	@ImplementsNitrateTest(caseId=41679)
	public void EnsureConsumedEntitlementsListed_Test(String productId, JSONArray bundledProductDataAsJSONArray) throws JSONException, Exception {
		clienttasks.unregister(null, null, null);
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, null, false, null, null, null);
		
		SubscriptionPool pool = SubscriptionPool.findFirstInstanceWithMatchingFieldFromList("productId", productId, clienttasks.getCurrentlyAllAvailableSubscriptionPools());
		Assert.assertNotNull(pool, "SubscriptionPool with ProductId '"+productId+"' is available for subscribing.");
		EntitlementCert  entitlementCert = clienttasks.getEntitlementCertFromEntitlementCertFile(clienttasks.subscribeToSubscriptionPool_(pool));
		List<ProductSubscription> consumedProductSubscriptions = clienttasks.getCurrentlyConsumedProductSubscriptions();
		Assert.assertTrue(!consumedProductSubscriptions.isEmpty(),"The list of Consumed Product Subscription is NOT empty after subscribing to a pool with ProductId '"+productId+"'.");
		for (ProductSubscription productSubscription : consumedProductSubscriptions) {
			Assert.assertEquals(productSubscription.serialNumber, entitlementCert.serialNumber,
					"SerialNumber of Consumed Product Subscription matches the serial number from the current entitlement certificate.");
		}	
	}
	
	@Test(	description="subscription-manager-cli: list installed products",
			groups={},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void EnsureInstalledProductsListed_Test() {
		clienttasks.unregister(null, null, null);
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, null, false, null, null, null);

		List <ProductCert> productCerts = clienttasks.getCurrentProductCerts();
		String installedProductsAsString = clienttasks.listInstalledProducts().getStdout();
		//List <InstalledProduct> installedProducts = clienttasks.getCurrentlyInstalledProducts();
		List <InstalledProduct> installedProducts = InstalledProduct.parse(installedProductsAsString);

		// assert some stdout
		if (installedProducts.size()>0) {
			String bannerTitle = "Installed Product Status";
			Assert.assertTrue(installedProductsAsString.contains(bannerTitle), "The list of installed products is entitled '"+bannerTitle+"'.");
		}
		
		// assert the number of installed product matches the product certs installed
		Assert.assertEquals(installedProducts.size(), productCerts.size(), "A single product is reported as installed for each product cert found in "+clienttasks.productCertDir);

		// assert that each of the installed ProductCerts are listed as InstalledProducts with status "Not Subscribed"
		for (ProductCert productCert : productCerts) {
			InstalledProduct installedProduct = clienttasks.getInstalledProductCorrespondingToProductCert(productCert,installedProducts);
			Assert.assertNotNull(installedProduct, "The following installed product cert is included by subscription-manager in the list --installed: "+(installedProduct==null?"null":installedProduct));	
			Assert.assertEquals(installedProduct.status, "Not Subscribed", "The status of installed product when newly registered: "+installedProduct);
		}
	}
	
	
	@Test(	description="subscription-manager: ensure list [--installed] produce the same results",
			groups={},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void EnsureListAndListInstalledAreTheSame_Test() throws JSONException, Exception {
		clienttasks.unregister(null, null, null);
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, null, false, null, null, null);

		// assert same results when no subscribed to anything...
		log.info("assert list [--installed] produce same results when not subscribed to anything...");
		SSHCommandResult listResult = clienttasks.list_(null, null, null, null, null, null, null, null, null, null, null);
		SSHCommandResult listInstalledResult = clienttasks.list_(null, null, null, Boolean.TRUE, null, null, null, null, null, null, null);
		
		Assert.assertEquals(listResult.getStdout(), listInstalledResult.getStdout(), "'list' and 'list --installed' produce the same stdOut results.");
		Assert.assertEquals(listResult.getStderr(), listInstalledResult.getStderr(), "'list' and 'list --installed' produce the same stdErr results.");
		Assert.assertEquals(listResult.getExitCode(), listInstalledResult.getExitCode(), "'list' and 'list --installed' produce the same exitCode results.");
		
		
		// assert same results when subscribed to something...
		log.info("assert list [--installed] produce same results when subscribed to something...");
		List<SubscriptionPool> pools = clienttasks.getCurrentlyAvailableSubscriptionPools();
		SubscriptionPool pool = pools.get(randomGenerator.nextInt(pools.size())); // randomly pick a pool
		clienttasks.subscribeToSubscriptionPool_(pool);
		listResult = clienttasks.list_(null, null, null, null, null, null, null, null, null, null, null);
		listInstalledResult = clienttasks.list_(null, null, null, Boolean.TRUE, null, null, null, null, null, null, null);
		
		Assert.assertEquals(listResult.getStdout(), listInstalledResult.getStdout(), "'list' and 'list --installed' produce the same stdOut results.");
		Assert.assertEquals(listResult.getStderr(), listInstalledResult.getStderr(), "'list' and 'list --installed' produce the same stdErr results.");
		Assert.assertEquals(listResult.getExitCode(), listInstalledResult.getExitCode(), "'list' and 'list --installed' produce the same exitCode results.");
	}
	

	@Test(	description="subscription-manager: list of consumed entitlements should display consumed product marketing name",
			groups={},
			dataProvider="getAllEntitlementCertsData",
			enabled=false)	// this test implementation is no longer valid after the change in format for consumed product subscriptions (from many to one) - see bug 806986
	@Deprecated
	@ImplementsNitrateTest(caseId=48092, fromPlan=2481)
	public void EnsureListConsumedMatchesProductsListedInTheEntitlementCerts_Test_DEPRECATED(EntitlementCert entitlementCert) {

		// assert: The list of consumed products matches the products listed in the entitlement cert
		List<ProductSubscription> productSubscriptions = clienttasks.getCurrentlyConsumedProductSubscriptions();
		List<ProductSubscription> productSubscriptionsWithMatchingSerialNumber = ProductSubscription.findAllInstancesWithMatchingFieldFromList("serialNumber", entitlementCert.serialNumber, productSubscriptions);
		//Assert.assertTrue(productSubscriptionsWithMatchingSerialNumber.size()>0, "Found consumed product subscription(s) whose SerialNumber matches this entitlement cert: "+entitlementCert);
		//Assert.assertEquals(productSubscriptionsWithMatchingSerialNumber.size(),entitlementCert.productNamespaces.size(), "Found consumed product subscription(s) for each of the bundleProducts (total of '"+entitlementCert.productNamespaces.size()+"' expected) whose SerialNumber matches this entitlement cert: "+entitlementCert);
		int productSubscriptionsWithMatchingSerialNumberSizeExpected = entitlementCert.productNamespaces.size()==0?1:entitlementCert.productNamespaces.size(); // when there are 0 bundledProducts, we are still consuming 1 ProductSubscription
		Assert.assertEquals(productSubscriptionsWithMatchingSerialNumber.size(),productSubscriptionsWithMatchingSerialNumberSizeExpected, "Found consumed product subscription(s) for each of the bundleProducts (total of '"+productSubscriptionsWithMatchingSerialNumberSizeExpected+"' expected) whose SerialNumber matches this entitlement cert: "+entitlementCert);

		for (ProductNamespace productNamespace : entitlementCert.productNamespaces) {
			List<ProductSubscription> matchingProductSubscriptions = ProductSubscription.findAllInstancesWithMatchingFieldFromList("productName", productNamespace.name, productSubscriptionsWithMatchingSerialNumber);
			Assert.assertEquals(matchingProductSubscriptions.size(), 1, "Found one bundledProduct name '"+productNamespace.name+"' in the list of consumed product subscriptions whose SerialNumber matches this entitlement cert: "+entitlementCert);
			ProductSubscription correspondingProductSubscription = matchingProductSubscriptions.get(0);
			log.info("We are about to assert that this consumed Product Subscription: "+correspondingProductSubscription);
			log.info("...represents this ProductNamespace: "+productNamespace);
			log.info("...corresponding to this OrderNamespace: "+entitlementCert.orderNamespace);
			log.info("...from this EntitlementCert: "+entitlementCert);
			Assert.assertEquals(correspondingProductSubscription.productName, productNamespace.name, "productName from ProductSubscription in list --consumed matches productName from ProductNamespace in EntitlementCert.");
			Assert.assertEquals(correspondingProductSubscription.contractNumber, entitlementCert.orderNamespace.contractNumber, "contractNumber from ProductSubscription in list --consumed matches contractNumber from OrderNamespace in EntitlementCert.");
			Assert.assertEquals(correspondingProductSubscription.accountNumber, entitlementCert.orderNamespace.accountNumber, "accountNumber from ProductSubscription in list --consumed matches accountNumber from OrderNamespace in EntitlementCert.");
			Assert.assertEquals(correspondingProductSubscription.serialNumber, entitlementCert.serialNumber, "serialNumber from ProductSubscription in list --consumed matches serialNumber from EntitlementCert.");
			
			Calendar now = Calendar.getInstance();
			if (now.after(entitlementCert.orderNamespace.startDate) && now.before(entitlementCert.orderNamespace.endDate)) {
				Assert.assertTrue(correspondingProductSubscription.isActive, "isActive is True when the current time ("+EntitlementCert.formatDateString(now)+") is between the start/end dates in the EntitlementCert: "+entitlementCert);
			} else {
				Assert.assertFalse(correspondingProductSubscription.isActive, "isActive is False when the current time ("+EntitlementCert.formatDateString(now)+") is NOT between the start/end dates in the EntitlementCert: "+entitlementCert);
			}
			
			// TEMPORARY WORKAROUND FOR BUG: https://bugzilla.redhat.com/show_bug.cgi?id=660713 - jsefler 12/12/2010
			Boolean invokeWorkaroundWhileBugIsOpen = true;
			try {String bugId="660713"; if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");SubscriptionManagerCLITestScript.addInvokedWorkaround(bugId);} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
			if (invokeWorkaroundWhileBugIsOpen) {
				log.warning("The workaround while this bug is open is to skip the assertion that: startDates and endDates match");
			} else {
			// END OF WORKAROUND
			Assert.assertEquals(ProductSubscription.formatDateString(correspondingProductSubscription.startDate), ProductSubscription.formatDateString(entitlementCert.orderNamespace.startDate), "startDate from ProductSubscription in list --consumed matches startDate from OrderNamespace ("+OrderNamespace.formatDateString(entitlementCert.orderNamespace.startDate)+") after conversion from GMT in EntitlementCert to local time.");
			Assert.assertEquals(ProductSubscription.formatDateString(correspondingProductSubscription.endDate), ProductSubscription.formatDateString(entitlementCert.orderNamespace.endDate), "endDate from ProductSubscription in list --consumed matches endDate from OrderNamespace ("+OrderNamespace.formatDateString(entitlementCert.orderNamespace.endDate)+") after conversion from GMT in EntitlementCert to local time.");
			}
		}
	}
	@Test(	description="subscription-manager: list of consumed entitlements should display the provided product marketing names",
			groups={"blockedByBug-878986","blockedByBug-976924"},
			dataProvider="getAllEntitlementCertsData",
			enabled=true)	// this new test implementation was implemented due to change in list of consumed product subscriptions (from many to one) - see bug 806986
	@ImplementsNitrateTest(caseId=48092, fromPlan=2481)
	public void EnsureListConsumedMatchesProductsListedInTheEntitlementCerts_Test(EntitlementCert entitlementCert) {

		// find the consumed product subscription corresponding to this entitlement cert and assert there is only one found
		List<ProductSubscription> allConsumedProductSubscriptions = clienttasks.getCurrentlyConsumedProductSubscriptions();
		List<ProductSubscription> productSubscriptions = ProductSubscription.findAllInstancesWithMatchingFieldFromList("serialNumber", entitlementCert.serialNumber, allConsumedProductSubscriptions);
		Assert.assertEquals(productSubscriptions.size(),1, "Found a single consumed product subscription with a matching serialNumber from this entitlementCert: "+entitlementCert);
		ProductSubscription productSubscription = productSubscriptions.get(0);
		List<String> providedProductNames = new ArrayList<String>();
		for (ProductNamespace productNamespace : entitlementCert.productNamespaces) providedProductNames.add(productNamespace.name);
		
		// TEMPORARY WORKAROUND FOR BUG
		if (entitlementCert.orderNamespace.supportLevel==null || entitlementCert.orderNamespace.supportType==null) {
			String bugId = "842170";
			boolean invokeWorkaroundWhileBugIsOpen = true;
			try {if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");SubscriptionManagerCLITestScript.addInvokedWorkaround(bugId);} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
			if (invokeWorkaroundWhileBugIsOpen) {
				throw new SkipException("While bug "+bugId+" is open, skipping assertion of consumed product subscription in list for entitlement's with a null support level/type.");
			}
		}
		// END OF WORKAROUND
		
		// when the entitlement OID value parsed was null, it should effectively be reported as ""  (Reference related bugs 842170 847354)
		if (entitlementCert.orderNamespace.supportLevel==null) entitlementCert.orderNamespace.supportLevel="";
		if (entitlementCert.orderNamespace.supportType==null) entitlementCert.orderNamespace.supportType="";
		
		
		//	Subscription Name:    	Awesome OS Server Bundled (2 Sockets, Standard Support)
		//	Provides:             	Clustering Bits
		//	                      	Awesome OS Server Bits
		//	                      	Shared Storage Bits
		//	                      	Management Bits
		//	                      	Large File Support Bits
		//	                      	Load Balancing Bits
		//	SKU:                  	awesomeos-server-2-socket-std
		//	Contract:             	36
		//	Account:              	12331131231
		//	Serial Number:        	6683485045354827351
		//	Active:               	True
		//	Quantity Used:        	1
		//	Service Level:        	Standard
		//	Service Type:         	L1-L3
		//	Starts:               	07/20/2012
		//	Ends:                 	07/20/2013
		
		Calendar now = Calendar.getInstance();
		
		// TEMPORARY WORKAROUND FOR BUG	
		boolean invokeWorkaroundWhileBugIsOpen = true;
		try {String bugId = "883486"; if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");SubscriptionManagerCLITestScript.addInvokedWorkaround(bugId);} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
		if (invokeWorkaroundWhileBugIsOpen) {
			log.warning("The workaround while this bug is open is to compensate the expected entitlement start/end dates for daylight savings.");
			// adjust the expected entitlement dates for daylight savings time (changed by https://github.com/candlepin/subscription-manager/pull/385)
			// now.get(Calendar.DST_OFFSET) will equal 0 in the winter StandardTime; will equal 1000*60*60 in the summer DaylightSavingsTime (when the local time zone observes DST)
			entitlementCert.orderNamespace.startDate.add(Calendar.MILLISECOND, now.get(Calendar.DST_OFFSET)-entitlementCert.orderNamespace.startDate.get(Calendar.DST_OFFSET));
			entitlementCert.orderNamespace.endDate.add(Calendar.MILLISECOND, now.get(Calendar.DST_OFFSET)-entitlementCert.orderNamespace.endDate.get(Calendar.DST_OFFSET));
		}
		// END OF WORKAROUND
		
		// assert all of the product subscription's fields match the entitlement cert
		Assert.assertEquals(productSubscription.productName, entitlementCert.orderNamespace.productName, "productName from ProductSubscription in list --consumed matches productName from OrderNamespace in this entitlementCert");
		Assert.assertTrue(productSubscription.provides.containsAll(providedProductNames) && providedProductNames.containsAll(productSubscription.provides), "The consumed product subscription provides all the expected products "+providedProductNames+" from the provided ProductNamespaces in the entitlementCert.");
		Assert.assertEquals(productSubscription.productId, entitlementCert.orderNamespace.productId, "productId from ProductSubscription in list --consumed matches productId from OrderNamespace in this entitlementCert");
		Assert.assertEquals(productSubscription.contractNumber, entitlementCert.orderNamespace.contractNumber, "contractNumber from ProductSubscription in list --consumed matches contractNumber from OrderNamespace in this entitlementCert");
		Assert.assertEquals(productSubscription.accountNumber, entitlementCert.orderNamespace.accountNumber, "accountNumber from ProductSubscription in list --consumed matches accountNumber from OrderNamespace in this entitlementCert");
		if (now.after(entitlementCert.orderNamespace.startDate) && now.before(entitlementCert.orderNamespace.endDate)) {
			Assert.assertTrue(productSubscription.isActive, "isActive is True when the current time ("+EntitlementCert.formatDateString(now)+") is between the start/end dates in this entitlementCert");
		} else {
			Assert.assertFalse(productSubscription.isActive, "isActive is False when the current time ("+EntitlementCert.formatDateString(now)+") is NOT between the start/end dates in this entitlementCert");
		}
		Assert.assertEquals(productSubscription.quantityUsed.toString(), entitlementCert.orderNamespace.quantityUsed, "quantityUsed from ProductSubscription in list --consumed matches quantityUsed from OrderNamespace in this entitlementCert");
		Assert.assertEquals(productSubscription.serviceLevel, entitlementCert.orderNamespace.supportLevel, "serviceLevel from ProductSubscription in list --consumed matches supportLevel from OrderNamespace in this entitlementCert");
		Assert.assertEquals(productSubscription.serviceType, entitlementCert.orderNamespace.supportType, "serviceType from ProductSubscription in list --consumed matches serviceType from OrderNamespace in this entitlementCert");
		Assert.assertEquals(ProductSubscription.formatDateString(productSubscription.startDate), ProductSubscription.formatDateString(entitlementCert.orderNamespace.startDate), "startDate from ProductSubscription in list --consumed matches startDate from OrderNamespace ("+OrderNamespace.formatDateString(entitlementCert.orderNamespace.startDate)+") after conversion from GMT in EntitlementCert to local time.");
		Assert.assertEquals(ProductSubscription.formatDateString(productSubscription.endDate), ProductSubscription.formatDateString(entitlementCert.orderNamespace.endDate), "endDate from ProductSubscription in list --consumed matches endDate from OrderNamespace ("+OrderNamespace.formatDateString(entitlementCert.orderNamespace.endDate)+") after conversion from GMT in EntitlementCert to local time.");
	}
	
	
	@Test(	description="subscription-manager: list of consumed subscriptions should report the poolId from which the entitlement originated",
			groups={"blockedByBug-908671"},
			enabled=true)
	//@ImplementsNitrateTest(caseId=, fromPlan=)
	public void EnsureListConsumedReportsOriginatingPoolId_Test() {
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, true, false, null, null, null);
		
		// subscribe to a randomly available pool
		List<SubscriptionPool> pools = clienttasks.getCurrentlyAvailableSubscriptionPools();
		SubscriptionPool pool = pools.get(randomGenerator.nextInt(pools.size())); // randomly pick a pool
		clienttasks.subscribe(null,null,pool.poolId,null,null,null,null,null,null,null,null);
		
		// verify the list of consumed subscriptions reports the pool.poolId
		List<ProductSubscription> consumedProductSubscriptionsFromPool = ProductSubscription.findAllInstancesWithMatchingFieldFromList("poolId", pool.poolId, clienttasks.getCurrentlyConsumedProductSubscriptions());
		Assert.assertNotNull(consumedProductSubscriptionsFromPool, "Successfully found the consumed subscription reporting the poolId '"+pool.poolId+"' that we just attached.");
		Assert.assertEquals(consumedProductSubscriptionsFromPool.size(), 1, "The number of consumed subscriptions reporting the poolId '"+pool.poolId+"' that we just attached.");
		Assert.assertEquals(consumedProductSubscriptionsFromPool.get(0).poolId, pool.poolId, "Redundant assertion on matching poolId between the attached subscription and the list of consumed subscriptions.");
	}
	
	
	@Test(	description="subscription-manager: list of available subscriptions should include contract number",
			groups={"blockedByBug-1007580"},
			enabled=true)
	//@ImplementsNitrateTest(caseId=, fromPlan=)
	public void EnsureListAvailableReportsContract_Test() {
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, true, false, null, null, null);
		
		// assert the contract value in all available pools
		boolean availableSubscriptionPoolsDisplayContract=false;
		for (SubscriptionPool pool : clienttasks.getCurrentlyAllAvailableSubscriptionPools()) {
			String none = "None";
			if (pool.contract!=null) {
				Assert.assertTrue(!pool.contract.equalsIgnoreCase(none), "The contract '"+pool.contract+"' for subscription pool '"+pool.poolId+"' should not be reported as '"+none+"'.");
				availableSubscriptionPoolsDisplayContract = true;
			}
		}
		Assert.assertTrue(availableSubscriptionPoolsDisplayContract, "Successfully encountered contracts reported in the list of available subscription pools.");
	}
	
	
	@Test(	description="subscription-manager-cli: RHEL Personal should be the only available subscription to a consumer registered as type person",
			groups={"EnsureOnlyRHELPersonalIsAvailableToRegisteredPerson_Test"},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void EnsureOnlyRHELPersonalIsAvailableToRegisteredPerson_Test() throws JSONException {
//		String rhelPersonalProductId = getProperty("sm.rhpersonal.productId", "");
//		if (rhelPersonalProductId.equals("")) throw new SkipException("This testcase requires specification of a RHPERSONAL_PRODUCTID.");
		
		// decide what username and password to test with
		String username = sm_clientUsername;
		String password = sm_clientPassword;
		String owner = sm_clientOrg;
		if (!sm_rhpersonalUsername.equals("")) {
			username = sm_rhpersonalUsername;
			password = sm_rhpersonalPassword;
			owner = sm_rhpersonalOrg;
		}
		
		// register a person
		clienttasks.unregister(null, null, null);
		clienttasks.register(username, password, owner, null, ConsumerType.person, null, null, null, null, null, (String)null, null, null, null, null, false, null, null, null);

		if (true) throw new SkipException("Support for RHELPersonal Subscriptions was yanked in favor of new DataCenter SKUs.");

		// assert that subscriptions with personal productIds are available to this person consumer
		List<SubscriptionPool> subscriptionPools = clienttasks.getCurrentlyAvailableSubscriptionPools();
		for (String personProductId : getPersonProductIds()) {
			SubscriptionPool rhelPersonalPool = SubscriptionPool.findFirstInstanceWithMatchingFieldFromList("productId", personProductId, subscriptionPools);
			Assert.assertNotNull(rhelPersonalPool,"Personal ProductId '"+personProductId+"' is available to this consumer registered as type person");
		}
	
		// assert that personal subscriptions are the only available pools to this person consumer
		for (SubscriptionPool subscriptionPool : subscriptionPools) {
			Assert.assertTrue(getPersonProductIds().contains(subscriptionPool.productId), "This available ProductId '"+subscriptionPool.productId+"' available to the registered person is among the expected list of personal products that we expect to be consumable by this person.");
		}
	}
	@AfterGroups(groups={}, value="EnsureOnlyRHELPersonalIsAvailableToRegisteredPerson_Test", alwaysRun=true)
	public void teardownAfterEnsureOnlyRHELPersonalIsAvailableToRegisteredPerson_Test() {
		if (clienttasks!=null) clienttasks.unregister_(null, null, null);
	}
	
	
	@Test(	description="subscription-manager-cli: RHEL Personal should not be an available subscription to a consumer registered as type system",
			groups={"EnsureRHELPersonalIsNotAvailableToRegisteredSystem_Test"},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void EnsureRHELPersonalIsNotAvailableToRegisteredSystem_Test() throws JSONException {
		clienttasks.unregister(null, null, null);
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, ConsumerType.system, null, null, null, null, null, (String)null, null, null, null, null, false, null, null, null);
		SubscriptionPool rhelPersonalPool = null;
		
		for (String personProductId : getPersonProductIds()) {
			// assert that RHEL Personal *is not* included in --available subscription pools
			rhelPersonalPool = SubscriptionPool.findFirstInstanceWithMatchingFieldFromList("productId", personProductId, clienttasks.getCurrentlyAvailableSubscriptionPools());
			Assert.assertNull(rhelPersonalPool,"Personal ProductId '"+personProductId+"' is NOT available to this consumer from --available subscription pools when registered as type system.");
			
			/* behavior changed on list --all --available  (3/4/2011)
			// also assert that RHEL Personal *is* included in --all --available subscription pools
			rhelPersonalPool = SubscriptionPool.findFirstInstanceWithMatchingFieldFromList("productId", personProductId, clienttasks.getCurrentlyAllAvailableSubscriptionPools());
			Assert.assertNotNull(rhelPersonalPool,"Personal ProductId '"+personProductId+"' is included in --all --available subscription pools when registered as type system.");
			*/
			// also assert that RHEL Personal *is not* included in --all --available subscription pools
			rhelPersonalPool = SubscriptionPool.findFirstInstanceWithMatchingFieldFromList("productId", personProductId, clienttasks.getCurrentlyAllAvailableSubscriptionPools());
			Assert.assertNull(rhelPersonalPool,"Personal ProductId '"+personProductId+"' is NOT available to this consumer from --all --available subscription pools when registered as type system.");
		}
	}
	@AfterGroups(groups={}, value="EnsureRHELPersonalIsNotAvailableToRegisteredSystem_Test", alwaysRun=true)
	public void teardownAfterEnsureRHELPersonalIsNotAvailableToRegisteredSystem_Test() {
		if (clienttasks!=null) clienttasks.unregister_(null, null, null);
	}
	

	
	
	@Test(	description="subscription-manager: subcription manager list consumed should be permitted without being registered",
			groups={"blockedByBug-725870"},
			enabled=true)
			//@ImplementsNitrateTest(caseId=)
	public void AttemptListConsumedWithoutBeingRegistered_Test() {
		
		clienttasks.unregister(null,null,null);
		SSHCommandResult listResult = clienttasks.listConsumedProductSubscriptions();
		
		// assert redemption results
		Assert.assertEquals(listResult.getStdout().trim(), "No consumed subscription pools to list","List consumed should NOT require that the system be registered.");
		Assert.assertEquals(listResult.getExitCode(), Integer.valueOf(0),"Exit code from list consumed when executed without being registered.");
	}
	
	
	@Test(	description="subscription-manager: subcription manager list installed should be permitted without being registered",
			groups={"blockedByBug-725870"},
			enabled=true)
			//@ImplementsNitrateTest(caseId=)
	public void AttemptListInstalledWithoutBeingRegistered_Test() {
		
		clienttasks.unregister(null,null,null);
		SSHCommandResult listResult = clienttasks.listInstalledProducts();
	}
	
	
	@Test(	description="subscription-manager: subcription manager list should be permitted without being registered",
			groups={"blockedByBug-725870"},
			enabled=true)
			//@ImplementsNitrateTest(caseId=)
	public void AttemptListWithoutBeingRegistered_Test() {
		
		clienttasks.unregister(null,null,null);
		SSHCommandResult listResult = clienttasks.list_(null,null,null,null,null,null,null, null, null, null, null);
		
		Assert.assertEquals(listResult.getExitCode(), Integer.valueOf(0), "The exit code from the list command indicates a success.");
	}
	
	
	@Test(	description="subscription-manager: subcription manager list available should require being registered",
			groups={},
			enabled=true)
			//@ImplementsNitrateTest(caseId=)
	public void AttemptListAvailableWithoutBeingRegistered_Test() {
		SSHCommandResult listResult;
		clienttasks.unregister(null,null,null);
		
		listResult = clienttasks.list_(null,true,null,null,null,null,null, null, null, null, null);
		//Assert.assertEquals(listResult.getExitCode(), Integer.valueOf(1), "The exit code from the list available command indicates a problem.");
		//Assert.assertEquals(listResult.getStdout().trim(), "Error: You need to register this system by running `register` command before using this option.","Attempting to list available subscriptions should require registration.");
		// results changed after bug fix 749332
		Assert.assertEquals(listResult.getExitCode(), Integer.valueOf(255), "The exit code from the list available command indicates a problem.");
		Assert.assertEquals(listResult.getStdout().trim(), clienttasks.msg_ConsumerNotRegistered,"Attempting to list --available subscriptions should require registration.");

		listResult = clienttasks.list_(true,true,null,null,null,null,null, null, null, null, null);
		//Assert.assertEquals(listResult.getExitCode(), Integer.valueOf(1), "The exit code from the list all available command indicates a problem.");
		//Assert.assertEquals(listResult.getStdout().trim(), "Error: You need to register this system by running `register` command before using this option.","Attempting to list all available subscriptions should require registration.");
		// results changed after bug fix 749332
		Assert.assertEquals(listResult.getExitCode(), Integer.valueOf(255), "The exit code from the list all available command indicates a problem.");
		Assert.assertEquals(listResult.getStdout().trim(), clienttasks.msg_ConsumerNotRegistered,"Attempting to list --all --available subscriptions should require registration.");

	}
	
	
	@Test(	description="subscription-manager: subcription manager list future subscription pools for a system",
			groups={"blockedByBug-672562"},
			enabled=true)
			//@ImplementsNitrateTest(caseId=)
	public void ListAllAvailableWithFutureOnDate_Test() throws Exception {
		
		List<List<Object>> allFutureJSONPoolsDataAsListOfLists = getAllFutureJSONPoolsDataAsListOfLists(ConsumerType.system);
		if (allFutureJSONPoolsDataAsListOfLists.size()<1) throw new SkipException("Cannot find any future subscriptions to test");
		
		Calendar now = new GregorianCalendar();
		now.setTimeInMillis(System.currentTimeMillis());
		List<String> onDatesTested = new ArrayList<String>();
		
		
		// assemble a list of ondate strings to test
		List<String> onDatesToTest = new ArrayList<String>();
		for (List<Object> l : allFutureJSONPoolsDataAsListOfLists) {
			JSONObject futureJSONPool = (JSONObject) l.get(0);
			
			String startDate = futureJSONPool.getString("startDate");
			
			// add one day to this start date to use for subscription-manager list --ondate test (ASSUMPTION: these subscriptions last longer than one day)
			Calendar onDate = parseISO8601DateString(startDate,"GMT"); onDate.add(Calendar.DATE, 1);
			DateFormat yyyy_MM_dd_DateFormat = new SimpleDateFormat("yyyy-MM-dd");
			String onDateToTest = yyyy_MM_dd_DateFormat.format(onDate.getTime());
			
			if (!onDatesToTest.contains(onDateToTest)) onDatesToTest.add(onDateToTest);
		}
		
		// assemble a list of future subscription poolIds to verify in the subscription-manager list --available --ondate
		List<String> futurePoolIds = new ArrayList<String>();
		for (List<Object> l : allFutureJSONPoolsDataAsListOfLists) {
			JSONObject jsonPool = (JSONObject) l.get(0);
			String id = jsonPool.getString("id");
			futurePoolIds.add(id);
		}
		
		// use this list to store all of the poolIds found on a future date listing
		List<String>futurePoolIdsListedOnDate = new ArrayList<String>();
		
		for (List<Object> l : allFutureJSONPoolsDataAsListOfLists) {
			JSONObject futureJSONPool = (JSONObject) l.get(0);
			
			// add one day to this start date to use for subscription-manager list --ondate test
			Calendar onDate = parseISO8601DateString(futureJSONPool.getString("startDate"),"GMT"); onDate.add(Calendar.DATE, 1);
			DateFormat yyyy_MM_dd_DateFormat = new SimpleDateFormat("yyyy-MM-dd");
			String onDateToTest = yyyy_MM_dd_DateFormat.format(onDate.getTime());
			
			// if we already tested with this ondate string, then continue
			if (onDatesTested.contains(onDateToTest)) continue;
			
			// list all available onDateToTest
			SSHCommandResult listResult = clienttasks.list_(true,true,null,null,null,onDateToTest,null, null, null, null, null);
			Assert.assertEquals(listResult.getExitCode(), Integer.valueOf(0), "The exit code from the list --all --available --ondate command indicates a success.");

			List<SubscriptionPool> subscriptionPools = SubscriptionPool.parse(listResult.getStdout());
			Assert.assertTrue(subscriptionPools.size()>=1,"A list of SubscriptionPools was returned from the list module using a valid ondate option.");
			
			// assert that each of the SubscriptionPools listed is indeed active on the requested date
			for (SubscriptionPool subscriptionPool : subscriptionPools) {
				JSONObject jsonPool = new JSONObject(CandlepinTasks.getResourceUsingRESTfulAPI(sm_clientUsername,sm_clientPassword,sm_serverUrl,"/pools/"+subscriptionPool.poolId));
				Calendar startDate = parseISO8601DateString(jsonPool.getString("startDate"),"GMT");	// "startDate":"2012-02-08T00:00:00.000+0000"
				Calendar endDate = parseISO8601DateString(jsonPool.getString("endDate"),"GMT");	// "endDate":"2013-02-07T00:00:00.000+0000"
				Boolean activeSubscription = jsonPool.getBoolean("activeSubscription");	// TODO I don't yet understand how to test this property.  I'm assuming it is true
				Assert.assertTrue(startDate.before(onDate)&&endDate.after(onDate)&&activeSubscription,"SubscriptionPool '"+subscriptionPool.poolId+"' is indeed active and listed as available ondate='"+onDateToTest+"'.");
							
				// for follow-up assertions keep a list of all the futurePoolIds that are found on the listing date (excluding pools that are active now since they are not considered future pools)
				if (startDate.after(now)) {
					if (!futurePoolIdsListedOnDate.contains(subscriptionPool.poolId)) futurePoolIdsListedOnDate.add(subscriptionPool.poolId);
				}
			}
			
			// remember that this date was just tested
			onDatesTested.add(onDateToTest);
		}
		
		Assert.assertEquals(futurePoolIdsListedOnDate.size(), futurePoolIds.size(),"The expected count of all of the expected future subscription pools for systems was listed on future dates.");
		for (String futurePoolId : futurePoolIds) futurePoolIdsListedOnDate.remove(futurePoolId);
		Assert.assertTrue(futurePoolIdsListedOnDate.isEmpty(),"All of the expected future subscription pools for systems were listed on future dates.");
	}
	
	
	@Test(	description="subscription-manager: subcription manager list with --match-installed option",
			groups={"blockedByBug-654501"},
			enabled=true)
			//@ImplementsNitrateTest(caseId=)
	public void ListAvailableWithMatchInstalled_Test() throws JSONException, Exception {
		
 		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, false, null, null, null, null);
		clienttasks.autoheal(null, null, true, null, null, null);

		// assemble a list of currently installed product ids
		List<ProductCert> installedProductCerts = clienttasks.getCurrentProductCerts();
		List<String> installedProductIds = new ArrayList<String>(); for (ProductCert productCert : installedProductCerts) installedProductIds.add(productCert.productId);
		
		// get the available subscription pools
		List<SubscriptionPool> availableSubscriptionPools = SubscriptionPool.parse(clienttasks.list(null, true, null, null, null, null, false, null, null, null, null).getStdout());
		List<SubscriptionPool> availableSubscriptionPoolsMatchingInstalled = SubscriptionPool.parse(clienttasks.list(null, true, null, null, null, null, true, null, null, null, null).getStdout());
		
		// loop through the list of available subscription pools with match-installed and assert they really do provide at least one product that is installed.
		for (SubscriptionPool subscriptionPool : availableSubscriptionPoolsMatchingInstalled) {
			ProductCert matchedInstalledProductCert = null;
			for (String providedProductId : CandlepinTasks.getPoolProvidedProductIds(sm_clientUsername, sm_clientPassword, sm_serverUrl, subscriptionPool.poolId)) {
				matchedInstalledProductCert = ProductCert.findFirstInstanceWithMatchingFieldFromList("productId", providedProductId, installedProductCerts);
				if (matchedInstalledProductCert!=null) break;
			}
			if (matchedInstalledProductCert!=null) Assert.assertTrue(matchedInstalledProductCert!=null,"Available subscription pool '"+subscriptionPool.subscriptionName+"' provides="+subscriptionPool.provides+" includes product id '"+matchedInstalledProductCert.productId+"' which was found among the product ids of the currently installed product certs. "+installedProductIds);
			else Assert.fail("Subscription-manager list available with match-installed option erroneously reported SubscriptionPool '"+subscriptionPool.subscriptionName+"' provides="+subscriptionPool.provides+" which does NOT provide a product whose id was found among the currently installed product certs. "+installedProductIds);
		}
		
		// loop through the list of available subscription without match-installed and make sure those that provide an installed product id are included in the filtered list
		for (SubscriptionPool subscriptionPool : availableSubscriptionPools) {
			boolean providesAnInstalledProductId = false;
			for (String providedProductId : CandlepinTasks.getPoolProvidedProductIds(sm_clientUsername, sm_clientPassword, sm_serverUrl, subscriptionPool.poolId)) {
				if (installedProductIds.contains(providedProductId)) providesAnInstalledProductId = true;
			}
			if (providesAnInstalledProductId) Assert.assertTrue(availableSubscriptionPoolsMatchingInstalled.contains(subscriptionPool),"The list of available subscriptions with match-installed option includes '"+subscriptionPool.subscriptionName+"' provides="+subscriptionPool.provides);
			else Assert.assertTrue(!availableSubscriptionPoolsMatchingInstalled.contains(subscriptionPool),"The list of available subscriptions with match-installed option does NOT include '"+subscriptionPool.subscriptionName+"' provides="+subscriptionPool.provides);
		}
	}
	
	
	@Test(	description="subscription-manager: subcription manager list all with --match-installed option",
			groups={"blockedByBug-654501"},
			enabled=true)
			//@ImplementsNitrateTest(caseId=)
	public void ListAllAvailableWithMatchInstalled_Test() throws JSONException, Exception {
		
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, false, null, null, null, null);
		clienttasks.autoheal(null, null, true, null, null, null);

		// assemble a list of currently installed product ids
		List<ProductCert> installedProductCerts = clienttasks.getCurrentProductCerts();
		List<String> installedProductIds = new ArrayList<String>(); for (ProductCert productCert : installedProductCerts) installedProductIds.add(productCert.productId);
		
		// get all the available subscription pools
		List<SubscriptionPool> allAvailableSubscriptionPools = SubscriptionPool.parse(clienttasks.list(true, true, null, null, null, null, false, null, null, null, null).getStdout());
		List<SubscriptionPool> allAvailableSubscriptionPoolsMatchingInstalled = SubscriptionPool.parse(clienttasks.list(true, true, null, null, null, null, true, null, null, null, null).getStdout());
		
		// loop through the list of all available subscription pools with match-installed and assert they really do provide at least one product that is installed.
		for (SubscriptionPool subscriptionPool : allAvailableSubscriptionPoolsMatchingInstalled) {
			ProductCert matchedInstalledProductCert = null;
			for (String providedProductId : CandlepinTasks.getPoolProvidedProductIds(sm_clientUsername, sm_clientPassword, sm_serverUrl, subscriptionPool.poolId)) {
				matchedInstalledProductCert = ProductCert.findFirstInstanceWithMatchingFieldFromList("productId", providedProductId, installedProductCerts);
				if (matchedInstalledProductCert!=null) break;
			}
			if (matchedInstalledProductCert!=null) Assert.assertTrue(matchedInstalledProductCert!=null,"Available subscription pool '"+subscriptionPool.subscriptionName+"' provides="+subscriptionPool.provides+" includes product id '"+matchedInstalledProductCert.productId+"' which was found among the product ids of the currently installed product certs. "+installedProductIds);
			else Assert.fail("Subscription-manager list all available with match-installed option erroneously reported SubscriptionPool '"+subscriptionPool.subscriptionName+"' provides="+subscriptionPool.provides+" which does NOT provide a product whose id was found among the currently installed product certs. "+installedProductIds);
		}
		
		// loop through the list of all available subscription without match-installed and make sure those that provide an installed product id are included in the filtered list
		for (SubscriptionPool subscriptionPool : allAvailableSubscriptionPools) {
			boolean providesAnInstalledProductId = false;
			for (String providedProductId : CandlepinTasks.getPoolProvidedProductIds(sm_clientUsername, sm_clientPassword, sm_serverUrl, subscriptionPool.poolId)) {
				if (installedProductIds.contains(providedProductId)) providesAnInstalledProductId = true;
			}
			if (providesAnInstalledProductId) Assert.assertTrue(allAvailableSubscriptionPoolsMatchingInstalled.contains(subscriptionPool),"The list of all available subscriptions with match-installed option includes '"+subscriptionPool.subscriptionName+"' provides="+subscriptionPool.provides);
			else Assert.assertTrue(!allAvailableSubscriptionPoolsMatchingInstalled.contains(subscriptionPool),"The list of all available subscriptions with match-installed option does NOT include '"+subscriptionPool.subscriptionName+"' provides="+subscriptionPool.provides);
		}
	}
	
	
	@Test(	description="subscription-manager: subcription manager list all with --match-installed option",
			groups={"blockedByBug-654501","blockedByBug-1022622"},
			enabled=true)
			//@ImplementsNitrateTest(caseId=)
	public void ListAvailableWithNoOverlap_Test() throws JSONException, Exception {
		
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, false, null, null, null, null);
		clienttasks.autoheal(null, null, true, null, null, null);
		
		// assemble a list of currently installed product ids
		List<ProductCert> installedProductCerts = clienttasks.getCurrentProductCerts();
		List<String> installedProductIds = new ArrayList<String>(); for (ProductCert productCert : installedProductCerts) installedProductIds.add(productCert.productId);
		
		// get the available subscription pools
		List<SubscriptionPool> availableSubscriptionPools = SubscriptionPool.parse(clienttasks.list(null, true, null, null, null, null, null, false, null, null, null).getStdout());
		
		// randomly attach a subset of available subscriptions
		List<SubscriptionPool> randomAvailableSubscriptionPools = getRandomSubsetOfList(availableSubscriptionPools, randomGenerator.nextInt(availableSubscriptionPools.size()));
		List<String> poolIds = new ArrayList<String>(); for (SubscriptionPool subscriptionPool : randomAvailableSubscriptionPools) poolIds.add(subscriptionPool.poolId);
		clienttasks.subscribe(null, null, poolIds, null, null, "1", null, null, null, null, null);
		
		List<InstalledProduct> installedProducts = InstalledProduct.parse(clienttasks.list(null,null,null,true,null,null,null,null,null,null,null).getStdout());
		List<SubscriptionPool> availableSubscriptionPoolsWithoutOverlap = SubscriptionPool.parse(clienttasks.list(null, true, null, null, null, null, null, true, null, null, null).getStdout());
		//	[root@jsefler-7 ~]# subscription-manager list --help | grep no-overlap -A1
		//	  --no-overlap          shows pools which provide products that are not
		//	                        already covered; only used with --available
		
		// loop through the list of available subscription pools without overlap and assert that at least one of pools provided product is not fully subscribed.
		for (SubscriptionPool subscriptionPool : availableSubscriptionPoolsWithoutOverlap) {
			boolean noOverlapFound = false;
			List<String> providedProductIds = CandlepinTasks.getPoolProvidedProductIds(sm_clientUsername, sm_clientPassword, sm_serverUrl, subscriptionPool.poolId);
			if (providedProductIds.isEmpty()) {
				Assert.assertTrue(providedProductIds.isEmpty(),"Subscription '"+subscriptionPool.subscriptionName+"' provides="+subscriptionPool.provides+" reported in the list available with no-overlap provides no products and therefore does not overlap an already covered product.");
				noOverlapFound = true;
			}
			for (String providedProductId : providedProductIds) {
				InstalledProduct installedProduct = InstalledProduct.findFirstInstanceWithMatchingFieldFromList("productId", providedProductId, installedProducts);
				if (installedProduct!=null) {
					if (!installedProduct.status.equalsIgnoreCase("Subscribed")) {
						Assert.assertTrue(!installedProduct.status.equalsIgnoreCase("Subscribed"),"Subscription '"+subscriptionPool.subscriptionName+"' provides="+subscriptionPool.provides+" reported in the list available with no-overlap provides product id '"+providedProductId+"' which is installed with status '"+installedProduct.status+"' and therefore does not overlap an already covered product.");
						noOverlapFound = true;
					} else {
						log.warning("Subscription '"+subscriptionPool.subscriptionName+"' provides="+subscriptionPool.provides+" reported in the list available with no-overlap provides product id '"+providedProductId+"' which is installed with status '"+installedProduct.status+"'.");
					}
				} else {
					Assert.assertTrue(installedProduct==null,"Subscription '"+subscriptionPool.subscriptionName+"' provides="+subscriptionPool.provides+" reported in the list available with no-overlap provides product id '"+providedProductId+"' which is not installed and therefore does not overlap an already covered product.");
					noOverlapFound = true;
				}
			}
			Assert.assertTrue(noOverlapFound,"Subscription '"+subscriptionPool.subscriptionName+"' provides="+subscriptionPool.provides+" that is reported in the list available with no-overlap provides at least one product that is not fully Subscribed.");
		}
		
		// assert that availableSubscriptionPools that are not filtered out of the availableSubscriptionPoolsWithoutOverlap provide products that are all fully Subscribed
		availableSubscriptionPools = SubscriptionPool.parse(clienttasks.list(null, true, null, null, null, null, null, false, null, null, null).getStdout());
		for (SubscriptionPool availableSubscriptionPool : availableSubscriptionPools) {
			if (!availableSubscriptionPoolsWithoutOverlap.contains(availableSubscriptionPool)) {
				for (String providedProductId : CandlepinTasks.getPoolProvidedProductIds(sm_clientUsername, sm_clientPassword, sm_serverUrl, availableSubscriptionPool.poolId)) {
					InstalledProduct installedProduct = InstalledProduct.findFirstInstanceWithMatchingFieldFromList("productId", providedProductId, installedProducts);
					Assert.assertNotNull(installedProduct, "Subscription '"+availableSubscriptionPool.subscriptionName+"' provides="+availableSubscriptionPool.provides+" is excluded from the list available with no-overlap.  It provides product id '"+providedProductId+"' which is installed and should be covered by an active subscription.");
					Assert.assertEquals(installedProduct.status, "Subscribed", "Subscription '"+availableSubscriptionPool.subscriptionName+"' provides="+availableSubscriptionPool.provides+" is excluded from the list available with no-overlap.  It provides product id '"+providedProductId+"' which is installed and covered by an active subscription.");
				}
			}
		}
	}
	
	
	@Test(	description="subscription-manager: subcription manager list all available should filter by servicelevel when this option is passed.",
			groups={"blockedByBug-800933","blockedByBug-800999"},
			dataProvider="getListAvailableWithServicelevelData",
			enabled=true)
			@ImplementsNitrateTest(caseId=157228)
	public void ListAvailableWithServicelevel_Test(Object bugzilla, String servicelevel) throws Exception {
		SSHCommandResult listResult;
		List<SubscriptionPool> expectedSubscriptionPools, filteredSubscriptionPools;
				
		// list all available (without service level)
		listResult = clienttasks.list_(true,true,null,null,null,null,null,null,null, null, null);
		List<SubscriptionPool> allAvailableSubscriptionPools = clienttasks.getCurrentlyAllAvailableSubscriptionPools();
		
		// determine the subset of expected pools with a case-insensitive matching servicelevel
		expectedSubscriptionPools = SubscriptionPool.findAllInstancesWithCaseInsensitiveMatchingFieldFromList("serviceLevel", servicelevel, allAvailableSubscriptionPools);

		// list all available filtered by servicelevel
		listResult = clienttasks.list_(true,true,null,null,servicelevel,null,null,null,null, null, null);
		Assert.assertEquals(listResult.getExitCode(), Integer.valueOf(0), "The exit code from the list --all --available --servicelevel command indicates a success.");
		
		// assert results
		filteredSubscriptionPools = SubscriptionPool.parse(listResult.getStdout());
		Assert.assertTrue(filteredSubscriptionPools.containsAll(expectedSubscriptionPools),"The actual list of --all --available --servicelevel=\""+servicelevel+"\" SubscriptionPools contains all of the expected SubscriptionPools (the expected list contains only pools with ServiceLevel=\""+servicelevel+"\")");
		Assert.assertTrue(expectedSubscriptionPools.containsAll(filteredSubscriptionPools),"The expected list of SubscriptionPools contains all of the actual SubscriptionPools returned by list --all --available --servicelevel=\""+servicelevel+"\".");
		if (expectedSubscriptionPools.isEmpty()) Assert.assertEquals(listResult.getStdout().trim(), "No available subscription pools to list","Expected message when no subscription remain after list is filtered by --servicelevel=\""+servicelevel+"\".");
				
		// list all available (without service level)
		listResult = clienttasks.list_(false,true,null,null,null,null,null,null,null, null, null);
		List<SubscriptionPool> availableSubscriptionPools = clienttasks.getCurrentlyAvailableSubscriptionPools();
		
		// determine the subset of expected pools with a matching servicelevel
		expectedSubscriptionPools = SubscriptionPool.findAllInstancesWithCaseInsensitiveMatchingFieldFromList("serviceLevel", servicelevel, availableSubscriptionPools);
		
		// list available filtered by servicelevel
		listResult = clienttasks.list_(false,true,null,null,servicelevel,null,null,null,null, null, null);
		Assert.assertEquals(listResult.getExitCode(), Integer.valueOf(0), "The exit code from the list --all --available --servicelevel command indicates a success.");
		
		// assert results
		filteredSubscriptionPools = SubscriptionPool.parse(listResult.getStdout());
		Assert.assertTrue(filteredSubscriptionPools.containsAll(expectedSubscriptionPools),"The actual list of --available --servicelevel=\""+servicelevel+"\" SubscriptionPools contains all of the expected SubscriptionPools (the expected list contains only pools with ServiceLevel=\""+servicelevel+"\")");
		Assert.assertTrue(expectedSubscriptionPools.containsAll(filteredSubscriptionPools),"The expected list of SubscriptionPools contains all of the actual SubscriptionPools returned by list --available --servicelevel=\""+servicelevel+"\".");
		if (expectedSubscriptionPools.isEmpty()) Assert.assertEquals(listResult.getStdout().trim(), "No available subscription pools to list","Expected message when no subscription remain after list is filtered by --servicelevel=\""+servicelevel+"\".");
	}

	
	@Test(	description="subscription-manager: subcription manager list consumed should filter by servicelevel when this option is passed.",
			groups={"blockedByBug-800933","blockedByBug-800999"},
			dataProvider="getConsumedWithServicelevelData",
			enabled=true)
			//@ImplementsNitrateTest(caseId=)
	public void ListConsumedWithServicelevel_Test(Object bugzilla, String servicelevel) throws Exception {
		SSHCommandResult listResult;
		List<ProductSubscription> expectedProductSubscriptions, filteredProductSubscriptions;
				
		// list consumed (without service level)
		listResult = clienttasks.list_(false,false,true,null,null,null,null,null,null, null, null);
		List<ProductSubscription> allConsumedProductSubscriptions = clienttasks.getCurrentlyConsumedProductSubscriptions();
		
		// determine the subset of expected pools with a matching servicelevel
		// CASE SENSITIVE expectedProductSubscriptions = ProductSubscription.findAllInstancesWithMatchingFieldFromList("serviceLevel", servicelevel, allConsumedProductSubscriptions);
		expectedProductSubscriptions = ProductSubscription.findAllInstancesWithCaseInsensitiveMatchingFieldFromList("serviceLevel", servicelevel, allConsumedProductSubscriptions);

		// list consumed filtered by servicelevel
		listResult = clienttasks.list_(false,false,true,null,servicelevel,null,null,null,null, null, null);
		Assert.assertEquals(listResult.getExitCode(), Integer.valueOf(0), "The exit code from the list --consumed --servicelevel command indicates a success.");
		
		// assert results
		filteredProductSubscriptions = ProductSubscription.parse(listResult.getStdout());
		Assert.assertTrue(filteredProductSubscriptions.containsAll(expectedProductSubscriptions),"The actual list of --consumed --servicelevel=\""+servicelevel+"\" ProductSubscriptions contains all of the expected ProductSubscriptions (the expected list contains only consumptions with ServiceLevel=\""+servicelevel+"\")");
		Assert.assertTrue(expectedProductSubscriptions.containsAll(filteredProductSubscriptions),"The expected list of ProductSubscriptions contains all of the actual ProductSubscriptions returned by list --consumed --servicelevel=\""+servicelevel+"\".");
		if (expectedProductSubscriptions.isEmpty()) Assert.assertEquals(listResult.getStdout().trim(), "No consumed subscription pools to list","Expected message when no consumed subscriptions remain after list is filtered by --servicelevel=\""+servicelevel+"\".");
	}
	
	
    protected String subscriptionNameForSubscriptionContainingUTF8Character = "Subscription name containing UTF–8 character \\u2013";	// the \u2013 character is between "UTF" and "8"
	protected String productIdForSubscriptionContainingUTF8Character = "utf8-subscription-sku";
	protected SubscriptionPool poolForSubscriptionContainingUTF8Character = null;
	@BeforeGroups(groups={"setup"},value="SubscriptionContainingUTF8CharacterTests")
	public void beforeGroupForSubscriptionContainingUTF8CharacterTests() throws JSONException, Exception {
		List<String> providedProductIds = new ArrayList<String>(); providedProductIds.clear();
		Map<String,String> attributes = new HashMap<String,String>(); attributes.clear();
		attributes.put("version", "2013");
		// delete already existing subscription and products
		CandlepinTasks.deleteSubscriptionsAndRefreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, sm_clientOrg, productIdForSubscriptionContainingUTF8Character);
		CandlepinTasks.deleteResourceUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, "/products/"+productIdForSubscriptionContainingUTF8Character);
		// create a new engineering product, marketing product that provides the engineering product, and a subscription for the marketing product
		attributes.put("type", "MKT");
		// TEMPORARY WORKAROUND FOR BUG
		String bugId = "919584";	// Bug 919584 - 'ascii' codec can't decode byte 0xc3 in position 3: ordinal not in range(128)
		boolean invokeWorkaroundWhileBugIsOpen = true;
		try {if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");SubscriptionManagerCLITestScript.addInvokedWorkaround(bugId);} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
		if (invokeWorkaroundWhileBugIsOpen) {
			log.warning("Skipping the creation of subscription name containing UTF8 character: "+subscriptionNameForSubscriptionContainingUTF8Character);
			return;
		}
		// END OF WORKAROUND
		CandlepinTasks.createProductUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, subscriptionNameForSubscriptionContainingUTF8Character, productIdForSubscriptionContainingUTF8Character, 1, attributes, null);
		CandlepinTasks.createSubscriptionAndRefreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, sm_clientOrg, 20, -1*24*60/*1 day ago*/, 15*24*60/*15 days from now*/, getRandInt(), getRandInt(), productIdForSubscriptionContainingUTF8Character, providedProductIds);
	}
	@Test(	description="subscription-manager: subcription manager list available should display subscriptions containing UTF-8 character(s)",
			groups={"SubscriptionContainingUTF8CharacterTests","blockedByBug-880070","blockedByBug-919584","blockedByBug-977535"},
			priority=110,
			enabled=true)
			//@ImplementsNitrateTest(caseId=)
	public void ListSubscriptionContainingUTF8Character_Test() {
		
		//	201212051805:22.585 - FINE: ssh root@jsefler-6.usersys.redhat.com subscription-manager list --available (com.redhat.qe.tools.SSHCommandRunner.run)
		//	201212051805:24.606 - FINE: Stdout: 
		//	+-------------------------------------------+
		//	    Available Subscriptions
		//	+-------------------------------------------+
		//	201212051805:24.770 - FINE: Stderr: 'ascii' codec can't encode character u'\u2013' in position 55: ordinal not in range(128) (com.redhat.qe.tools.SSHCommandRunner.runCommandAndWait)
		//	201212051805:24.773 - FINE: ExitCode: 255 (com.redhat.qe.tools.SSHCommandRunner.runCommandAndWait)
		
		//	[root@jsefler-6 ~]# LANG=en_US subscription-manager list --avail
		//	+-------------------------------------------+
		//	    Available Subscriptions
		//	+-------------------------------------------+
		//	'latin-1' codec can't encode character u'\u2013' in position 55: ordinal not in range(256)
		//	[root@jsefler-6 ~]# 
		
		
		// 5/29/2013 Notes: the fix for bug 919584 actually reverted the fix in https://bugzilla.redhat.com/show_bug.cgi?id=800323#c7
		// which means this test is failing to run subscription-manager within the ssh sub-shell that we use for cli automation.
		// If I manually edit /usr/sbin/subscription-manager and paste this patch after the imports, then life is good..
		// If dev does not fix this, then I'm not yet sure what a good work-around solution is for this problem.
		/*
		import codecs
		# Change encoding of output streams when no encoding is forced via
		# $PYTHONIOENCODING or setting in lib/python{version}/site-packages
		if sys.getdefaultencoding() == 'ascii':
		    writer_class = codecs.getwriter('utf-8')
		if sys.stdout.encoding == None:
		    sys.stdout = writer_class(sys.stdout)
		if sys.stderr.encoding == None:
		    sys.stderr = writer_class(sys.stderr)
		*/
		
		// register and list --available to find the subscription containing a \u2013 character
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, true, false, null, null, null);
		
		//List<SubscriptionPool> availableSubscriptionPools = clienttasks.getCurrentlyAvailableSubscriptionPools();
		//	9/9/2013 Notes: During the RHEL65 test cycle, it appears that the following PYTHONIOENCODING=ascii workaround is
		//	no longer needed (last altered by Bug fix 977535).  In fact if the workaround remains in place, the codec error is thrown:
		//	'ascii' codec can't encode character u'\u2013' in position 51: ordinal not in range(128)
		//	Reverting back to the original method for listing available pools without using PYTHONIOENCODING=ascii on the command line...
		//List<SubscriptionPool> availableSubscriptionPools = SubscriptionPool.parse(clienttasks.runCommandWithLang(null, clienttasks.command+" list --available").getStdout());
		List<SubscriptionPool> availableSubscriptionPools = clienttasks.getCurrentlyAvailableSubscriptionPools();
		poolForSubscriptionContainingUTF8Character = SubscriptionPool.findFirstInstanceWithCaseInsensitiveMatchingFieldFromList("productId", productIdForSubscriptionContainingUTF8Character, availableSubscriptionPools);
		Assert.assertNotNull(poolForSubscriptionContainingUTF8Character, "Found subscription product '"+productIdForSubscriptionContainingUTF8Character+"' from the list of available subscriptions whose name contains a UTF8 character.");
		Assert.assertEquals(poolForSubscriptionContainingUTF8Character.subscriptionName, subscriptionNameForSubscriptionContainingUTF8Character, "asserting the subscription name.");
	}
	@Test(	description="subscription-manager: subcription manager attach a subscription containing UTF-8 character(s)",
			groups={"SubscriptionContainingUTF8CharacterTests","blockedByBug-889204","blockedByBug-981689"},
			dependsOnMethods={"ListSubscriptionContainingUTF8Character_Test"},
			priority=120,
			enabled=true)
			//@ImplementsNitrateTest(caseId=)
	public void AttachSubscriptionContainingUTF8Character_Test() throws JSONException, Exception {
		// TEMPORARY WORKAROUND FOR BUG
		String bugId="889204";	// Bug 889204 - encountering the following stderr msg when subscription name contains UTF8 chars: [priority,] message string
		Boolean invokeWorkaroundWhileBugIsOpen = true;
		try {if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");SubscriptionManagerCLITestScript.addInvokedWorkaround(bugId);} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
		if (invokeWorkaroundWhileBugIsOpen) {
			// must cleanup utf8-subscription-sku to avoid contaminating other tests; then skip this test
			afterGroupForSubscriptionContainingUTF8CharacterTests();
			throw new SkipException("Skipping test while bug '"+bugId+"' is open.");
		}
		// END OF WORKAROUND
			
		SSHCommandResult sshCommandResult = clienttasks.runCommandWithLang(null, clienttasks.command+" attach --pool "+poolForSubscriptionContainingUTF8Character.poolId);
		Assert.assertEquals(sshCommandResult.getStdout().trim(), String.format("Successfully attached a subscription for: %s",subscriptionNameForSubscriptionContainingUTF8Character), "Stdout from an attempt to attach '"+subscriptionNameForSubscriptionContainingUTF8Character+"'.");
		Assert.assertEquals(sshCommandResult.getStderr().trim(), "", "Stderr from an attempt to attach '"+subscriptionNameForSubscriptionContainingUTF8Character+"'.");
		Assert.assertEquals(sshCommandResult.getExitCode(), Integer.valueOf(0), "ExitCode from an attempt to attach '"+subscriptionNameForSubscriptionContainingUTF8Character+"'.");
	}
	@Test(	description="rct: cat-cert an entitlement containing UTF-8 character(s)",
			groups={"SubscriptionContainingUTF8CharacterTests","blockedByBug-890296","blockedByBug-1048325"},
			dependsOnMethods={"AttachSubscriptionContainingUTF8Character_Test"},
			priority=130,
			enabled=true)
			//@ImplementsNitrateTest(caseId=)
	public void CatCertContainingUTF8Character_Test() throws JSONException, Exception {
		// TEMPORARY WORKAROUND FOR BUG
		String bugId="890296";	// Bug 890296 - 'ascii' codec can't encode character u'\u2013'.
		Boolean invokeWorkaroundWhileBugIsOpen = true;
		try {if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");SubscriptionManagerCLITestScript.addInvokedWorkaround(bugId);} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
		if (invokeWorkaroundWhileBugIsOpen) {
			// must cleanup utf8-subscription-sku to avoid contaminating other tests; then skip this test
			afterGroupForSubscriptionContainingUTF8CharacterTests();
			throw new SkipException("Skipping test while bug '"+bugId+"' is open.");
		}
		// END OF WORKAROUND
		
		List<File> entitlementCertFiles = clienttasks.getCurrentEntitlementCertFiles();
		Assert.assertEquals(entitlementCertFiles.size(), 1, "Expecting only one entitlement cert being consumed.");
		
		//	[root@jsefler-6 ~]# rct cat-cert /etc/pki/entitlement/6487424643396210003.pem 
		//	Traceback (most recent call last):
		//	  File "/usr/bin/rct", line 44, in <module>
		//	    sys.exit(abs(main() or 0))
		//	  File "/usr/bin/rct", line 39, in main
		//	    return RctCLI().main()
		//	  File "/usr/share/rhsm/subscription_manager/cli.py", line 156, in main
		//	    return cmd.main()
		//	  File "/usr/share/rhsm/rct/commands.py", line 44, in main
		//	    return_code = self._do_command()
		//	  File "/usr/share/rhsm/rct/commands.py", line 92, in _do_command
		//	    skip_products=self.options.no_products)
		//	  File "/usr/share/rhsm/rct/printing.py", line 196, in printc
		//	    printer.printc(cert)
		//	  File "/usr/share/rhsm/rct/printing.py", line 105, in printc
		//	    print self.cert_to_str(cert)
		//	  File "/usr/share/rhsm/rct/printing.py", line 166, in cert_to_str
		//	    order_printer.as_str(cert.order), "\n".join(s))
		//	  File "/usr/share/rhsm/rct/printing.py", line 47, in as_str
		//	    s.append("\t%s: %s" % (_("Name"), xstr(order.name)))
		//	  File "/usr/share/rhsm/rct/printing.py", line 26, in xstr
		//	    return str(value)
		//	UnicodeEncodeError: 'ascii' codec can't encode character u'\u2013' in position 32: ordinal not in range(128)
		//	[root@jsefler-6 ~]# echo $?
		//	1
		
		//SSHCommandResult sshCommandResult = clienttasks.runCommandWithLang(null/* null will cause command to be prefixed with PYTHONIOENCODING=ascii */, "rct cat-cert "+entitlementCertFiles.get(0));	// need for PYTHONIOENCODING=ascii workaround was eliminated by bug 1048325
		SSHCommandResult sshCommandResult = client.runCommandAndWait("rct cat-cert "+entitlementCertFiles.get(0));
		Assert.assertEquals(sshCommandResult.getExitCode(), Integer.valueOf(0), "ExitCode from an attempt to run rct cat-cert on an entitlement containing UTF-8 character(s)");
		Assert.assertEquals(sshCommandResult.getStderr().trim(), "", "Stderr from an attempt to run rct cat-cert on an entitlement containing UTF-8 character(s)");
	}
	@Test(	description="subscription-manager: subcription manager remove a consumed subscription containing UTF-8 character(s)",
			groups={"SubscriptionContainingUTF8CharacterTests","blockedByBug-889204"},
			dependsOnMethods={"CatCertContainingUTF8Character_Test"},
			priority=140,
			enabled=true)
			//@ImplementsNitrateTest(caseId=)
	public void RemoveSubscriptionContainingUTF8Character_Test() {
		// SURPRISED THAT THIS DID NOT WORK List AND THE FOLLOWING DOES <ProductSubscription> consumedProductSubscriptions = ProductSubscription.parse(clienttasks.runCommandWithLang(null, clienttasks.command+" list --consumed").getStdout());
		List<ProductSubscription> consumedProductSubscriptions = ProductSubscription.parse(client.runCommandAndWait(clienttasks.command+" list --consumed").getStdout());
		ProductSubscription consumedProductSubscription = ProductSubscription.findFirstInstanceWithCaseInsensitiveMatchingFieldFromList("productId", productIdForSubscriptionContainingUTF8Character, consumedProductSubscriptions);
		Assert.assertNotNull(consumedProductSubscription, "Found subscription product '"+productIdForSubscriptionContainingUTF8Character+"' from the list of consumed subscriptions whose name contains a UTF8 character.");
		Assert.assertEquals(consumedProductSubscription.productName, subscriptionNameForSubscriptionContainingUTF8Character, "asserting the consumed subscription name.");
		clienttasks.unsubscribeFromSerialNumber(consumedProductSubscription.serialNumber);
	}
	@AfterClass(groups={"setup"})	// needed since @AfterGroups will skip when some of the tests within the group are skipped even when alwaysRun=true
	@AfterGroups(groups={"setup"},value="SubscriptionContainingUTF8CharacterTests",alwaysRun=true /* does not seem to have any effect */)
	public void afterGroupForSubscriptionContainingUTF8CharacterTests() throws JSONException, Exception {
		clienttasks.unregister_(null, null, null);	// to return any consumed subscriptions containing UTF8 characters
		if (productIdForSubscriptionContainingUTF8Character!=null && !sm_serverType.equals(CandlepinType.hosted)/* we do NOT create/delete subscriptions against hosted */) CandlepinTasks.deleteSubscriptionsAndRefreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, sm_clientOrg, productIdForSubscriptionContainingUTF8Character);
	}
	
	
	
	
	
	// Candidates for an automated Test:
	// TODO Bug 709412 - subscription manager cli uses product name comparisons in the list command https://github.com/RedHatQE/rhsm-qe/issues/166
	// TODO Bug 710141 - OwnerInfo needs to only show info for pools that are active right now, for all the stats https://github.com/RedHatQE/rhsm-qe/issues/167
	// TODO Bug 734880 - subscription-manager list --installed reports differently between LEGACY vs NEW SKU subscriptions  (Note: Bryan says that this had nothing to do with Legacy vs Non Legacy - it was simply a regression in bundled products when stacking was introduced) https://github.com/RedHatQE/rhsm-qe/issues/168
	// TODO Bug 803386 - display product ID in product details pane on sm-gui and cli https://github.com/RedHatQE/rhsm-qe/issues/169
	// TODO Bug 805415 - s390x Partially Subscribed  (THIS IS EFFECTIVELY A COMPLIANCE TEST ON A 0 SOCKET SUBSCRIPTION/PRODUCT) https://github.com/RedHatQE/rhsm-qe/issues/170
	
	
	// Configuration methods ***********************************************************************
	
	
	
	@BeforeClass(groups="setup")
	public void registerBeforeClass() {
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, null, false, null, null, null);
	}
	
	// NOTE: This method is not necessary, but adds a little more spice to ListAvailableWithFutureOnDate_Test
	@BeforeClass(groups="setup", dependsOnMethods="registerBeforeClass")
	public void createFutureSubscriptionPoolBeforeClass() throws Exception {
		// don't bother attempting to create a subscription unless onPremises
		//if (!sm_serverType.equals(CandlepinType.standalone)) return;
		if (server==null) {
			log.warning("Skipping createFutureSubscriptionPoolBeforeClass() when server is null.");
			return;	
		}
		
		// find a randomly available product id
		List<SubscriptionPool> pools = clienttasks.getCurrentlyAvailableSubscriptionPools();
		if (pools.isEmpty()) {
			log.warning("Skipping createFutureSubscriptionPoolBeforeClass() when no pools are available.");
			return;		
		}
		SubscriptionPool pool = pools.get(randomGenerator.nextInt(pools.size())); // randomly pick a pool
		String randomAvailableProductId = pool.productId;
		
		// create a future subscription and refresh pools for it
		JSONObject futureJSONPool = CandlepinTasks.createSubscriptionAndRefreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, sm_clientOrg, 15, 5/*years*/*365*24*60, 6/*years*/*365*24*60, getRandInt(), getRandInt(), randomAvailableProductId, null);
	}
	
	
	@BeforeClass(groups="setup")
	public void createSubscriptionsWithVariationsOnProductAttributeSockets() throws JSONException, Exception {
		String name,productId;
		List<String> providedProductIds = new ArrayList<String>();
		Map<String,String> attributes = new HashMap<String,String>();
		JSONObject jsonEngProduct, jsonMktProduct, jsonSubscription;
		if (server==null) {
			log.warning("Skipping createSubscriptionsWithVariationsOnProductAttributeSockets() when server is null.");
			return;	
		}
	
		// Awesome OS for 0 sockets
		name = "Awesome OS for systems with sockets value=0";
		productId = "0-sockets";
		providedProductIds.clear();
		providedProductIds.add("90001");
		attributes.clear();
		attributes.put("sockets", "0");
		attributes.put("version", "1.0");
		attributes.put("variant", "server");
		attributes.put("arch", "ALL");
		attributes.put("warning_period", "30");
		// delete already existing subscription and products
		CandlepinTasks.deleteSubscriptionsAndRefreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, sm_clientOrg, productId);
		CandlepinTasks.deleteResourceUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, "/products/"+productId);
		CandlepinTasks.deleteResourceUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, "/products/"+providedProductIds.get(0));
		// create a new engineering product, marketing product that provides the engineering product, and a subscription for the marketing product
		attributes.put("type", "SVC");
		CandlepinTasks.createProductUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, name+" BITS", providedProductIds.get(0), 1, attributes, null);
		attributes.put("type", "MKT");
		CandlepinTasks.createProductUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, name, productId, 1, attributes, null);
		CandlepinTasks.createSubscriptionAndRefreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, sm_clientOrg, 20, -1*24*60/*1 day ago*/, 15*24*60/*15 days from now*/, getRandInt(), getRandInt(), productId, providedProductIds);

	
		// Awesome OS for no sockets
		name = "Awesome OS for systems with no sockets";
		productId = "no-sockets";
		providedProductIds.clear();
		providedProductIds.add("90002");
		attributes.clear();
		attributes.remove("sockets");
		attributes.put("version", "0.0");
		attributes.put("variant", "workstation");
		attributes.put("arch", "ALL");
		attributes.put("warning_period", "30");
		// delete already existing subscription and products
		CandlepinTasks.deleteSubscriptionsAndRefreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, sm_clientOrg, productId);
		CandlepinTasks.deleteResourceUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, "/products/"+productId);
		CandlepinTasks.deleteResourceUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, "/products/"+providedProductIds.get(0));
		// create a new engineering product, marketing product that provides the engineering product, and a subscription for the marketing product
		attributes.put("type", "SVC");
		CandlepinTasks.createProductUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, name+" BITS", providedProductIds.get(0), 1, attributes, null);
		attributes.put("type", "MKT");
		CandlepinTasks.createProductUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, name, productId, 1, attributes, null);
		CandlepinTasks.createSubscriptionAndRefreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, sm_clientOrg, 20, -1*24*60/*1 day ago*/, 15*24*60/*15 days from now*/, getRandInt(), getRandInt(), productId, providedProductIds);
		
		
		// Awesome OS for "zero" sockets
		name = "Awesome OS for systems with sockets value=\"zero\"";
		productId = "zero-sockets";
		providedProductIds.clear();
		providedProductIds.add("90003");
		attributes.clear();
		attributes.put("sockets", "zero");
		attributes.put("version", "0.0");
		attributes.put("variant", "workstation");
		attributes.put("arch", "ALL");
		attributes.put("warning_period", "30");
		// TEMPORARY WORKAROUND FOR BUG
		String bugId = "795552";	// Bug 795552 - invalid literal for int() with base 10: 'null'
		boolean invokeWorkaroundWhileBugIsOpen = true;
		try {if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");SubscriptionManagerCLITestScript.addInvokedWorkaround(bugId);} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
		if (invokeWorkaroundWhileBugIsOpen) {
			log.warning("Skipping the creation of product: "+name);
		} else {
		// END OF WORKAROUND
		// TEMPORARY WORKAROUND FOR BUG
		bugId = "858286";	// Bug 858286 - Runtime Error For input string: "zero" at java.lang.NumberFormatException.forInputString:65
		invokeWorkaroundWhileBugIsOpen = true;
		try {if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");SubscriptionManagerCLITestScript.addInvokedWorkaround(bugId);} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
		BUG_858286:	if (invokeWorkaroundWhileBugIsOpen) {
			log.warning("Skipping the creation of product: "+name);
		} else {
		// END OF WORKAROUND
		// delete already existing subscription and products
		CandlepinTasks.deleteSubscriptionsAndRefreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, sm_clientOrg, productId);
		CandlepinTasks.deleteResourceUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, "/products/"+productId);
		CandlepinTasks.deleteResourceUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, "/products/"+providedProductIds.get(0));
		// create a new engineering product, marketing product that provides the engineering product, and a subscription for the marketing product
		attributes.put("type", "SVC");
		
		// after the fix for bug 858286, the sockets attribute value MUST be a non-negative attribute	// 1/25/2013 TODO move this block of code to create 'Awesome OS for "zero" sockets' to its own testcase
		try {
		CandlepinTasks.createProductUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, name+" BITS", providedProductIds.get(0), 1, attributes, null);
		} catch (java.lang.AssertionError ae) {
			String expectedProductCreationErrorMsg = "The attribute 'sockets' must be an integer value.";
			Assert.assertTrue(ae.getMessage().contains(expectedProductCreationErrorMsg), ae.getMessage()+" contains expected product creation failure message '"+expectedProductCreationErrorMsg+"'.");
			break BUG_858286; 
		}
				
		attributes.put("type", "MKT");
		CandlepinTasks.createProductUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, name, productId, 1, attributes, null);
		CandlepinTasks.createSubscriptionAndRefreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, sm_clientOrg, 20, -1*24*60/*1 day ago*/, 15*24*60/*15 days from now*/, getRandInt(), getRandInt(), productId, providedProductIds);
		}}
		
		
		// Awesome OS for null sockets
		name = "Awesome OS for systems with sockets value=null";
		productId = "null-sockets";
		providedProductIds.clear();
		providedProductIds.add("90004");
		attributes.clear();
		attributes.put("sockets", null);
		attributes.put("version", "0.0");
		attributes.put("variant", "server");
		attributes.put("arch", "ALL");
		attributes.put("warning_period", "30");
		// TEMPORARY WORKAROUND FOR BUG
		bugId = "807452";	// Bug 807452 - refresh pools FAILS WITH: org.quartz.SchedulerException: Job threw an unhandled exception. [See nested exception: org.mozilla.javascript.WrappedException: Wrapped java.lang.NullPointerException (rules#737)]
		invokeWorkaroundWhileBugIsOpen = true;
		try {if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");SubscriptionManagerCLITestScript.addInvokedWorkaround(bugId);} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
		if (invokeWorkaroundWhileBugIsOpen) {
			log.warning("Skipping the creation of product: "+name);
		} else {
		// END OF WORKAROUND
		// TEMPORARY WORKAROUND FOR BUG
		bugId = "813529";	// Bug 813529 - refresh pools FAILS WITH: org.quartz.SchedulerException: Job threw an unhandled exception. [See nested exception: org.mozilla.javascript.WrappedException: Wrapped java.lang.NullPointerException (rules#846)]
		invokeWorkaroundWhileBugIsOpen = true;
		try {if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");SubscriptionManagerCLITestScript.addInvokedWorkaround(bugId);} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
		if (invokeWorkaroundWhileBugIsOpen) {
			log.warning("Skipping the creation of product: "+name);
		} else {
		// delete already existing subscription and products
		CandlepinTasks.deleteSubscriptionsAndRefreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, sm_clientOrg, productId);
		CandlepinTasks.deleteResourceUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, "/products/"+productId);
		CandlepinTasks.deleteResourceUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, "/products/"+providedProductIds.get(0));
		// create a new engineering product, marketing product that provides the engineering product, and a subscription for the marketing product
		attributes.put("type", "SVC");
		CandlepinTasks.createProductUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, name+" BITS", providedProductIds.get(0), 1, attributes, null);
		attributes.put("type", "MKT");
		CandlepinTasks.createProductUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, name, productId, 1, attributes, null);
		CandlepinTasks.createSubscriptionAndRefreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, sm_clientOrg, 20, -1*24*60/*1 day ago*/, 15*24*60/*15 days from now*/, getRandInt(), getRandInt(), productId, providedProductIds);
		}}
		
		// TODO: To get the product certs, use the CandlepinTasks REST API:
        //"url": "/products/{product_uuid}/certificate", 
        //"GET"
	}	
	
	
	
	
	@BeforeGroups(groups="setup",value="unsubscribeBeforeGroup")
	public void unsubscribeBeforeGroup() {
		//clienttasks.unsubscribeFromAllOfTheCurrentlyConsumedProductSubscriptions();
		clienttasks.unsubscribe_(true, (BigInteger)null, null, null, null);
	}
	
	
	
	
	
	// Data Providers ***********************************************************************
	
	
	
	@DataProvider(name="getListAvailableWithServicelevelData")
	public Object[][] getListAvailableWithServicelevelDataAs2dArray() throws JSONException, Exception {
		return TestNGUtils.convertListOfListsTo2dArray(getListAvailableWithServicelevelDataAsListOfLists());
	}
	protected List<List<Object>>getListAvailableWithServicelevelDataAsListOfLists() throws JSONException, Exception {
		List<List<Object>> ll = new ArrayList<List<Object>>(); if (!isSetupBeforeSuiteComplete) return ll;
		
		// register with force (so we can find the org to which the sm_clientUsername belongs in case sm_clientOrg is null)
		String org = sm_clientOrg;
		if (org==null) {
			String consumerId = clienttasks.getCurrentConsumerId(clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, "SubscriptionServicelevelConsumer", null, null, null, null, (String)null, null, null, null, true, false, null, null, null));
			org = CandlepinTasks.getOwnerKeyOfConsumerId(sm_clientUsername,sm_clientPassword,sm_serverUrl,consumerId);
		}
		
		// get all the valid service levels available to this org	
		for (String serviceLevel : CandlepinTasks.getServiceLevelsForOrgKey(sm_clientUsername, sm_clientPassword, sm_serverUrl, org)) {
			BlockedByBzBug blockedByBug=null;
			
			// Bug 1011234 - subscription-manager list --avail should catch nil service-levels and report the absense of a service-level rather than "None"
			if (serviceLevel.equalsIgnoreCase("none")) blockedByBug=new BlockedByBzBug("1011234");
			
			ll.add(Arrays.asList(new Object[] {blockedByBug,	serviceLevel}));
			ll.add(Arrays.asList(new Object[] {blockedByBug,	randomizeCaseOfCharactersInString(serviceLevel)}));	// run again with the serviceLevel case randomized
		}
		ll.add(Arrays.asList(new Object[] {new BlockedByBzBug("1011234"),	""}));
		ll.add(Arrays.asList(new Object[] {null,							"FOO"}));

		
		return ll;
	}
	
	
	
	@DataProvider(name="getConsumedWithServicelevelData")
	public Object[][] getConsumedWithServicelevelDataAs2dArray() throws JSONException, Exception {
		return TestNGUtils.convertListOfListsTo2dArray(getConsumedWithServicelevelDataAsListOfLists());
	}
	protected List<List<Object>>getConsumedWithServicelevelDataAsListOfLists() throws JSONException, Exception {
		List<List<Object>> ll = new ArrayList<List<Object>>(); if (!isSetupBeforeSuiteComplete) return ll;
		
		// register with force and subscribe to all available subscriptions collectively
		String consumerId = clienttasks.getCurrentConsumerId(clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, "SubscriptionServicelevelConsumer", null, null, null, null, (String)null, null, null, null, true, false, null, null, null));
		String org = CandlepinTasks.getOwnerKeyOfConsumerId(sm_clientUsername,sm_clientPassword,sm_serverUrl,consumerId);
		clienttasks.subscribeToTheCurrentlyAllAvailableSubscriptionPoolsCollectively();
		
		// get all the valid service levels available to this org	
		for (String serviceLevel : CandlepinTasks.getServiceLevelsForOrgKey(sm_clientUsername, sm_clientPassword, sm_serverUrl, org)) {
			Object bugzilla = null;
			if (serviceLevel.equals("None")) bugzilla = new BlockedByBzBug(new String[]{"842170","976924"});
			ll.add(Arrays.asList(new Object[] {bugzilla,	serviceLevel}));
		}
		ll.add(Arrays.asList(new Object[] {new BlockedByBzBug(new String[]{"842170","976924"}),	""}));
		ll.add(Arrays.asList(new Object[] {null, "FOO"}));

		
		return ll;
	}
}

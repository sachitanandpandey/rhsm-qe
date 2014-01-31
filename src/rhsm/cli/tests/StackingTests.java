package rhsm.cli.tests;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.testng.annotations.AfterClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import rhsm.base.SubscriptionManagerCLITestScript;
import rhsm.cli.tasks.CandlepinTasks;
import rhsm.data.EntitlementCert;
import rhsm.data.InstalledProduct;
import rhsm.data.SubscriptionPool;

import com.redhat.qe.Assert;
import com.redhat.qe.auto.bugzilla.BlockedByBzBug;
import com.redhat.qe.auto.testng.TestNGUtils;

/**
 * @author jsefler
 *
 *
 */
@Test(groups={"StackingTests"})
public class StackingTests extends SubscriptionManagerCLITestScript {

	
	// Test methods ***********************************************************************
	
	@Test(	description="subscription-manager: subscribe to each pool with the same stacking_id to achieve compliance",
			enabled=true,
			groups={"AcceptanceTests","blockedByBug-739671", "blockedByBug-740377", "blockedByBug-861993", "blockedByBug-955142"},
			dataProvider="getAvailableStackableAttributeSubscriptionPoolsData")
	//@ImplementsNitrateTest(caseId=)
	public void StackEachPoolToAchieveAttributeCompliance_Test(Object bugzilla, String attribute, boolean systemIsGuest, List<SubscriptionPool> stackableAttributeSubscriptionPools) throws JSONException, Exception{
		
		// The strategy in this test is to simulate the facts on the systems so that the attribute being tested ("cores","ram",or "sockets", or "vcpu")
		// will achieve full compliance for all of the provided products after attaching a quantity of one entitlement
		// from each pool in the list of stackable subscription pools.  As we incrementally attach from each pool, we will assert
		// a partial compliance until the final subscription is attached which should achieve full compliance.
		
		// loop through the pools to determine the minimum attribute count for which one
		// of each stackable pool is needed to achieve compliance of the provided products
		// also keep a list of all the provided productIds
		Integer minimumAttributeValue=0;
		Integer minimumSocketsValue=0;
		Set<String> productIdsProvidedForByAllStackableSubscriptionPools = new HashSet<String>();
		Map<String,Integer> poolProductAttributeValueMap = new HashMap<String,Integer>();
		for (SubscriptionPool pool : stackableAttributeSubscriptionPools) {
			String attributeValue = CandlepinTasks.getPoolProductAttributeValue(sm_clientUsername, sm_clientPassword, sm_serverUrl, pool.poolId, attribute);
			productIdsProvidedForByAllStackableSubscriptionPools.addAll(CandlepinTasks.getPoolProvidedProductIds(sm_clientUsername, sm_clientPassword, sm_serverUrl, pool.poolId));
			minimumAttributeValue+=Integer.valueOf(attributeValue);
			poolProductAttributeValueMap.put(pool.poolId, Integer.valueOf(attributeValue));
		}
		
		// override the system facts setting the attribute count to a value for which all the stackable subscriptions are needed to achieve compliance
		Map<String,String> factsMap = new HashMap<String,String>();
		factsMap.put("virt.is_guest", Boolean.valueOf(systemIsGuest).toString());	// force the system to be physical or virtual
		factsMap.put("memory.memtotal", "1");
		factsMap.put("cpu.cpu_socket(s)", "1");
		factsMap.put("cpu.core(s)_per_socket", "1");
		if (attribute.equals("ram")) {
			factsMap.put("memory.memtotal", String.valueOf(minimumAttributeValue*1048576)); // "memory.memtotal" is stored in Kilobytes; "ram" is specified in Gigabytes; for conversions, see http://www.whatsabyte.com/P1/byteconverter.htm
		}
		if (attribute.equals("sockets")) {
			factsMap.put("cpu.cpu_socket(s)", String.valueOf(minimumAttributeValue));
		}
		if (attribute.equals("cores") || attribute.equals("vcpu")) {	// vcpu (on a virt system) maps to cores (on a physical system) 
			if (Integer.valueOf(minimumAttributeValue)%2==0) {	// configure facts for an even number of cores
				factsMap.put("cpu.core(s)_per_socket", "2");
				minimumSocketsValue = Integer.valueOf(minimumAttributeValue)/2;
				factsMap.put("cpu.cpu_socket(s)", String.valueOf(minimumSocketsValue));
			} else {	// configure facts for an odd number of cores
				factsMap.put("cpu.core(s)_per_socket", "1");
				minimumSocketsValue = Integer.valueOf(minimumAttributeValue);
				factsMap.put("cpu.cpu_socket(s)",  String.valueOf(minimumSocketsValue));
			}
		}
		clienttasks.createFactsFileWithOverridingValues(factsMap);
		
		// register the system which has now been instrumented with facts to test the stack
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null,null,null,null,null,null,null,(List<String>)null,null,null,null,true,false,null,null,null);
		
		// assert installed product status for all the products that the stacked subscriptions will provide for
		List<InstalledProduct> currentlyInstalledProducts = clienttasks.getCurrentlyInstalledProducts();
		for (String productId : productIdsProvidedForByAllStackableSubscriptionPools) {
			List<InstalledProduct> installedProducts = InstalledProduct.findAllInstancesWithMatchingFieldFromList("productId", productId, currentlyInstalledProducts);
			if (installedProducts.isEmpty()) continue; // this productIdProvidedFor is not installed
			if (installedProducts.size()>1) Assert.fail("Something is seriously wrong.  Found multiple InstalledProduct "+installedProducts+" with a common productId '"+productId+"'.");	// this should be impossible because the would all share the same /etc/pki/product/<productId>.pem file name
			InstalledProduct installedProduct = installedProducts.get(0);
			List<String> expectedStatusDetails = Arrays.asList(new String[]{"Not supported by a valid subscription."});	// Message changed by candlepin commit 43a17952c724374c3fee735642bce52811a1e386 covers -> supports
			Assert.assertEquals(installedProduct.status,"Not Subscribed","Prior to subscribing to any of the stackable subscription pools, Installed product '"+installedProduct.productName+"' which is provided for by the subscription stack should have this status.");
			if (installedProduct.statusDetails.isEmpty()) log.warning("Status Details appears empty.  Is your candlepin server older than 0.8.6?");
			Assert.assertEquals(installedProduct.statusDetails,expectedStatusDetails,"Prior to subscribing to any of the stackable subscription pools, Installed product '"+installedProduct.productName+"' which is provided for by the subscription stack should have these status details: "+expectedStatusDetails);
			//Assert.assertTrue(isEqualNoOrder(installedProduct.statusDetails,expectedStatusDetails),"Prior to subscribing to any of the stackable subscription pools, Installed product '"+installedProduct.productName+"' which is provided for by the subscription stack should have these status details: "+expectedStatusDetails);
		}
		
		// incrementally attach one entitlement from each pool in the stack asserting the installed product's status and details along the way
		// the final attachment should achieve full compliance for the provided products in the stack
		int s=0;
		Integer attributeValueStackedThusFar = 0;
		Integer socketsValueStackedThusFar = 0;
		Integer vcpuValueStackedThusFar = 0;
		Set<String> productIdsProvidedForThusFar = new HashSet<String>();
		for (SubscriptionPool pool : stackableAttributeSubscriptionPools) {
			clienttasks.subscribe(null,null,pool.poolId,null,null,"1",null,null,null,null,null);
			
			// add some test coverage for bugs 861993 and 955142
			EntitlementCert entitlementCert = clienttasks.getEntitlementCertCorrespondingToSubscribedPool(pool);
			if (attribute.equals("ram")) {
				Assert.assertEquals(entitlementCert.orderNamespace.ramLimit,poolProductAttributeValueMap.get(pool.poolId).toString(),"rct cat-cert tool reports the expected RAM Limit value in the Order for subscription '"+pool.subscriptionName+"'.");
			}
			if (attribute.equals("sockets")) {
				Assert.assertEquals(entitlementCert.orderNamespace.socketLimit,poolProductAttributeValueMap.get(pool.poolId).toString(),"rct cat-cert tool reports the expected Socket Limit value in the Order for subscription '"+pool.subscriptionName+"'.");
			}
			if (attribute.equals("cores")) {
				Assert.assertEquals(entitlementCert.orderNamespace.coreLimit,poolProductAttributeValueMap.get(pool.poolId).toString(),"rct cat-cert tool reports the expected Core Limit value in the Order for subscription '"+pool.subscriptionName+"'.");
			}
			/* TODO Open a bug to include vcpu in the order repo
			 * Bug 1055617 - [RFE] rct cat-cert should also report the "VCPU Limit" attribute for an Order
			if (attribute.equals("vcpu")) {
				Assert.assertEquals(entitlementCert.orderNamespace.vcpuLimit,poolProductAttributeValueMap.get(pool.poolId).toString(),"rct cat-cert tool reports the expected VCPU Limit value in the Order for subscription '"+pool.subscriptionName+"'.");
			}
			*/
			
			// keep a running total of how much of the stackable attribute our entitlements have covered thus far
			attributeValueStackedThusFar += poolProductAttributeValueMap.get(pool.poolId);
			
			// keep a running total of how much socket coverage our stacked entitlements have covered thus far
			String socketsValue = CandlepinTasks.getPoolProductAttributeValue(sm_clientUsername, sm_clientPassword, sm_serverUrl, pool.poolId, "sockets");
			if (socketsValue!=null) {
				socketsValueStackedThusFar += Integer.valueOf(socketsValue);
			}
			// keep a running total of how much vcpu coverage our stacked entitlements have covered thus far
			String vcpuValue = CandlepinTasks.getPoolProductAttributeValue(sm_clientUsername, sm_clientPassword, sm_serverUrl, pool.poolId, "vcpu");
			if (vcpuValue!=null) {
				vcpuValueStackedThusFar += Integer.valueOf(vcpuValue);
			}
			
			// keep a running set of which productIdsProvidedFor have been covered by the subscriptions thus far
			productIdsProvidedForThusFar.addAll(CandlepinTasks.getPoolProvidedProductIds(sm_clientUsername, sm_clientPassword, sm_serverUrl, pool.poolId));
							
			// assert the installed products that have been provided for by the stack of subscriptions thus far are Partially Subscribed
			for (InstalledProduct installedProduct : clienttasks.getCurrentlyInstalledProducts()) {
				if (productIdsProvidedForThusFar.contains(installedProduct.productId)) {
					List<String> expectedStatusDetails = new ArrayList<String>();
					if (attribute.equals("ram") && attributeValueStackedThusFar<minimumAttributeValue) {
						expectedStatusDetails.add(String.format("Only supports %sGB of %sGB of RAM.", attributeValueStackedThusFar,minimumAttributeValue)); // Message changed by candlepin commit 43a17952c724374c3fee735642bce52811a1e386 covers -> supports
					}
					if (attribute.equals("sockets") && !systemIsGuest && attributeValueStackedThusFar<minimumAttributeValue) {
						expectedStatusDetails.add(String.format("Only supports %s of %s sockets.", attributeValueStackedThusFar,minimumAttributeValue)); // Message changed by candlepin commit 43a17952c724374c3fee735642bce52811a1e386 covers -> supports
					}
					if (attribute.equals("cores") && !systemIsGuest && attributeValueStackedThusFar<minimumAttributeValue) {
						expectedStatusDetails.add(String.format("Only supports %s of %s cores.", attributeValueStackedThusFar,minimumAttributeValue)); // Message changed by candlepin commit 43a17952c724374c3fee735642bce52811a1e386 covers -> supports
					}
					if (attribute.equals("vcpu") && systemIsGuest && attributeValueStackedThusFar<minimumAttributeValue) {
						expectedStatusDetails.add(String.format("Only supports %s of %s vCPUs.", attributeValueStackedThusFar,minimumAttributeValue)); // Message changed by candlepin commit 43a17952c724374c3fee735642bce52811a1e386 covers -> supports
					}
					if (attribute.equals("cores") && !systemIsGuest && socketsValueStackedThusFar>0 && socketsValueStackedThusFar<minimumSocketsValue) {	// when a cores stack also includes sockets (on a physical system), we will have more status details
						expectedStatusDetails.add(String.format("Only supports %s of %s sockets.", socketsValueStackedThusFar,minimumSocketsValue)); // Message changed by candlepin commit 43a17952c724374c3fee735642bce52811a1e386 covers -> supports
					}
					if (attribute.equals("cores") && systemIsGuest && vcpuValueStackedThusFar>0 && vcpuValueStackedThusFar<minimumAttributeValue) {	// when a cores stack also includes vcpu (on a virtual system), we will have more status details
						expectedStatusDetails.add(String.format("Only supports %s of %s vCPUs.", vcpuValueStackedThusFar,minimumAttributeValue)); // Message changed by candlepin commit 43a17952c724374c3fee735642bce52811a1e386 covers -> supports
					}
					if (attribute.equals("sockets") && systemIsGuest && vcpuValueStackedThusFar>0 && vcpuValueStackedThusFar<minimumAttributeValue) {	// when a sockets stack also includes vcpu (on a virtual system), we will have more status details
						expectedStatusDetails.add(String.format("Only supports %s of %s vCPUs.", vcpuValueStackedThusFar,minimumAttributeValue)); // Message changed by candlepin commit 43a17952c724374c3fee735642bce52811a1e386 covers -> supports
					}
					if (attribute.equals("vcpu") && !systemIsGuest && socketsValueStackedThusFar>0 && socketsValueStackedThusFar<minimumSocketsValue) {	// when a vcpu stack also includes sockets (on a physical system), we will have more status details
						expectedStatusDetails.add(String.format("Only supports %s of %s sockets.", socketsValueStackedThusFar,minimumSocketsValue)); // Message changed by candlepin commit 43a17952c724374c3fee735642bce52811a1e386 covers -> supports
					}
					
					if (expectedStatusDetails.isEmpty()) {
						Assert.assertEquals(installedProduct.status,"Subscribed","After an incremental attachment of one stackable '"+attribute+"' subscription for '"+pool.subscriptionName+"' poolId="+pool.poolId+", Installed product '"+installedProduct.productName+"' which is provided for by the subscription stack should have this status.");
					} else {
						if (installedProduct.statusDetails.isEmpty()) log.warning("Status Details appears empty.  Is your candlepin server older than 0.8.6?");
						Assert.assertTrue(isEqualNoOrder(installedProduct.statusDetails,expectedStatusDetails), "After an incremental attachment of one stackable '"+attribute+"' subscription for '"+pool.subscriptionName+"' poolId="+pool.poolId+", Installed product '"+installedProduct.productName+"' which is provided for by the subscription stack should have status details "+expectedStatusDetails+" (actual= "+installedProduct.statusDetails+")");
						Assert.assertEquals(installedProduct.status,"Partially Subscribed","After an incremental attachment of one stackable '"+attribute+"' subscription for '"+pool.subscriptionName+"' poolId="+pool.poolId+", Installed product '"+installedProduct.productName+"' which is provided for by the subscription stack should have this status.");
					}
					
				} else {
					if (productIdsProvidedForByAllStackableSubscriptionPools.contains(installedProduct.productId)) {
						List<String> expectedStatusDetails = Arrays.asList(new String[]{"Not supported by a valid subscription."}); // Message changed by candlepin commit 43a17952c724374c3fee735642bce52811a1e386 covers -> supports
						if (installedProduct.statusDetails.isEmpty()) log.warning("Status Details appears empty.  Is your candlepin server older than 0.8.6?");
						Assert.assertEquals(installedProduct.status,"Not Subscribed","After an incremental attachment of one stackable subscription for '"+pool.subscriptionName+"' poolId="+pool.poolId+", Installed product '"+installedProduct.productName+"' which is NOT YET provided for by the subscription stack THUS FAR should have this status.");
						Assert.assertEquals(installedProduct.statusDetails,expectedStatusDetails, "After an incremental attachment of one stackable subscription for '"+pool.subscriptionName+"' poolId="+pool.poolId+", Installed product '"+installedProduct.productName+"' which is NOT YET provided for by the subscription stack THUS FAR should have these status details: "+expectedStatusDetails);
						//Assert.assertTrue(isEqualNoOrder(installedProduct.statusDetails,expectedStatusDetails), "After an incremental attachment of one stackable subscription for '"+pool.subscriptionName+"' poolId="+pool.poolId+", Installed product '"+installedProduct.productName+"' which is NOT YET provided for by the subscription stack THUS FAR should have these status details: "+expectedStatusDetails);
					} else {
						/* These asserts are valid, but not really relevant to this test.  Commented out to reduce noisy logging.
						List<String> expectedStatusDetails = Arrays.asList(new String[]{"Not supported by a valid subscription."}); // Message changed by candlepin commit 43a17952c724374c3fee735642bce52811a1e386 covers -> supports
						Assert.assertEquals(installedProduct.status,"Not Subscribed","After subscribing to stackable subscription for '"+pool.subscriptionName+"' poolId="+pool.poolId+", Installed product '"+installedProduct.productName+"' which is NOT provided for by the subscription stack should have this status.");
						Assert.assertTrue(isEqualNoOrder(installedProduct.statusDetails,expectedStatusDetails), "After subscribing to stackable subscription for '"+pool.subscriptionName+"' poolId="+pool.poolId+", Installed product '"+installedProduct.productName+"' which is NOT provided for by the subscription stack should have these status details: "+expectedStatusDetails);
						*/
					}
				}
			}
		}
	}
	
	@DataProvider(name="getAvailableStackableAttributeSubscriptionPoolsData")
	public Object[][] getAvailableStackableAttributeSubscriptionPoolsDataAs2dArray() throws JSONException, Exception {
		return TestNGUtils.convertListOfListsTo2dArray(getAvailableStackableAttributeSubscriptionPoolsDataAsListOfLists());
	}
	protected List<List<Object>> getAvailableStackableAttributeSubscriptionPoolsDataAsListOfLists() throws JSONException, Exception {
		List<List<Object>> ll = new ArrayList<List<Object>>(); if (!isSetupBeforeSuiteComplete) return ll;
		Map<String,List<SubscriptionPool>> stackableSubscriptionPoolsMap = new HashMap<String,List<SubscriptionPool>>();
		
		// find all the SubscriptionPools with the same stacking_id
		for (List<Object> l : getAvailableSubscriptionPoolsDataAsListOfLists()) {
			SubscriptionPool pool = (SubscriptionPool)l.get(0);
			String stacking_id = CandlepinTasks.getPoolProductAttributeValue(sm_clientUsername, sm_clientPassword, sm_serverUrl, pool.poolId, "stacking_id");
			
			// skip non-stackable pools
			if (stacking_id==null) continue;
			
			// skip instance-based pools (these are covered in InstanceTests.java) 
			if (CandlepinTasks.isPoolProductInstanceBased(sm_clientUsername, sm_clientPassword, sm_serverUrl, pool.poolId)) continue;
			
			// add this available stackable pool to the stackableSubscriptionPoolsMap
			if (!stackableSubscriptionPoolsMap.containsKey(stacking_id)) stackableSubscriptionPoolsMap.put(stacking_id, new ArrayList<SubscriptionPool>());
			stackableSubscriptionPoolsMap.get(stacking_id).add(pool);
		}
		
		// assemble the rows of data
		for (String stacking_id : stackableSubscriptionPoolsMap.keySet()) {
			List<SubscriptionPool> stackableSubscriptionPools = stackableSubscriptionPoolsMap.get(stacking_id);
			
			for (String attribute : new String[]{"sockets","cores","ram","vcpu"}) {
				List<SubscriptionPool> stackableAttributeSubscriptionPools = new ArrayList<SubscriptionPool>();

				for (SubscriptionPool stackableSubscriptionPool : stackableSubscriptionPools) {
					if (CandlepinTasks.getPoolProductAttributeValue(sm_clientUsername, sm_clientPassword, sm_serverUrl, stackableSubscriptionPool.poolId, attribute)!=null) {
						stackableAttributeSubscriptionPools.add(stackableSubscriptionPool);
					}
				}
				
				if (!stackableAttributeSubscriptionPools.isEmpty()) {
					BlockedByBzBug blockedByBzBug = null;
					if (attribute.equals("vcpu")) blockedByBzBug = new BlockedByBzBug(new String[]{"885785","871602"});	// Bug 871602 - [RFE] Virtual Architecture Independence  HAS EFFECTIVELY BEEN FIXED AS A DUP OF Bug 885785 - [RFE] Subscription Manager should alert a user if subscription vcpu limits are lower than system vcpu allocation
					
					// Object bugzilla, String attribute, String systemIsGuest, List<SubscriptionPool> stackableAttributeSubscriptionPools
					ll.add(Arrays.asList(new Object[]{blockedByBzBug, attribute, true, stackableAttributeSubscriptionPools}));
					ll.add(Arrays.asList(new Object[]{blockedByBzBug, attribute, false, stackableAttributeSubscriptionPools}));	
				}
			}
		}
		
		return ll;
	}
	
	
	
	
	
	// Candidates for an automated Test:
	// TODO Bug 733327 - stacking entitlements reports as distinct entries in cli list --installed https://github.com/RedHatQE/rhsm-qe/issues/193
	// TODO Bug 740377 - Stacking Partially Compliant / Yellow State is Broken https://github.com/RedHatQE/rhsm-qe/issues/194
	// TODO Bug 743710 - Subscription manager displays incorrect status for partially subscribed subscription https://github.com/RedHatQE/rhsm-qe/issues/195
	//      MAYBE THIS ONE BELONGS IN COMPLIANCE TESTS?
	// TODO Bug 726409 - [RFE] Support for certificate stacking https://github.com/RedHatQE/rhsm-qe/issues/196
	// TODO Bug 845126 - system.entitlements_valid goes from valid to partial after oversubscribing https://github.com/RedHatQE/rhsm-qe/issues/197
		
	// Configuration methods ***********************************************************************
	
	@AfterClass(groups={"setup"},alwaysRun=true)
	public void cleanupAfterClass() {
		if (clienttasks==null) return;
		
		// remove overriding test facts from last test
		clienttasks.deleteFactsFileWithOverridingValues();
	}
	
	// Protected methods ***********************************************************************
	
	
	// Data Providers ***********************************************************************
	
	
	// FIXME DELETEME? NOT BEING USED
	@DataProvider(name="getAllStackableJSONPoolsData")
	public Object[][] getAllStackableJSONPoolsDataAs2dArray() throws Exception {
		return TestNGUtils.convertListOfListsTo2dArray(getAllStackableJSONPoolsDataAsListOfLists());
	}
	protected List<List<Object>> getAllStackableJSONPoolsDataAsListOfLists() throws Exception {
		List<List<Object>> ll = new ArrayList<List<Object>>(); if (!isSetupBeforeSuiteComplete) return ll;
		Map<String,List<JSONObject>> stackableJSONPoolsMap = new HashMap<String,List<JSONObject>>();
		
		for (List<Object> row : getAllJSONPoolsDataAsListOfLists()) {
			JSONObject jsonPool = (JSONObject) row.get(0);
			
			
			// loop through all the productAttributes looking for stacking_id
			JSONArray jsonProductAttributes = jsonPool.getJSONArray("productAttributes");
			for (int j = 0; j < jsonProductAttributes.length(); j++) {	// loop product attributes to find a stacking_id
				if (((JSONObject) jsonProductAttributes.get(j)).getString("name").equals("stacking_id")) {
					String stacking_id = ((JSONObject) jsonProductAttributes.get(j)).getString("value");
					
					// we found a stackable pool, let's add it to the stackableJSONPoolsMap
					if (!stackableJSONPoolsMap.containsKey(stacking_id)) stackableJSONPoolsMap.put(stacking_id, new ArrayList<JSONObject>());
					stackableJSONPoolsMap.get(stacking_id).add(jsonPool);
					break;
				}
			}
		}
		
		for (String stacking_id : stackableJSONPoolsMap.keySet()) {
			List<JSONObject> stackableJSONPools = stackableJSONPoolsMap.get(stacking_id);
			ll.add(Arrays.asList(new Object[]{stackableJSONPools}));
		}
		
		return ll;
	}
	
	// FIXME DELETEME? NOT BEING USED
	@DataProvider(name="getAvailableStackableSubscriptionPoolsData")
	public Object[][] getAvailableStackableSubscriptionPoolsDataAs2dArray() throws JSONException, Exception {
		return TestNGUtils.convertListOfListsTo2dArray(getAvailableStackableSubscriptionPoolsDataAsListOfLists());
	}
	protected List<List<Object>> getAvailableStackableSubscriptionPoolsDataAsListOfLists() throws JSONException, Exception {
		List<List<Object>> ll = new ArrayList<List<Object>>(); if (!isSetupBeforeSuiteComplete) return ll;
		Map<String,List<SubscriptionPool>> stackableSubscriptionPoolsMap = new HashMap<String,List<SubscriptionPool>>();
		
		// find all the SubscriptionPools with the same stacking_id
		for (List<Object> l : getAvailableSubscriptionPoolsDataAsListOfLists()) {
			SubscriptionPool pool = (SubscriptionPool)l.get(0);
			String stacking_id = CandlepinTasks.getPoolProductAttributeValue(sm_clientUsername, sm_clientPassword, sm_serverUrl, pool.poolId, "stacking_id");
			
			if (stacking_id==null) continue; // this pool is not stackable
			
			// add this available stackable pool to the stackableSubscriptionPoolsMap
			if (!stackableSubscriptionPoolsMap.containsKey(stacking_id)) stackableSubscriptionPoolsMap.put(stacking_id, new ArrayList<SubscriptionPool>());
			stackableSubscriptionPoolsMap.get(stacking_id).add(pool);
		}
		
		// assemble the rows of data
		for (String stacking_id : stackableSubscriptionPoolsMap.keySet()) {
			List<SubscriptionPool> stackableSubscriptionPools = stackableSubscriptionPoolsMap.get(stacking_id);
			
			// List<SubscriptionPool> stackableSubscriptionPools
			ll.add(Arrays.asList(new Object[]{stackableSubscriptionPools}));
		}
		
		return ll;
	}
	
}


// 12/20/2013 OLD IMPLEMENTATION THAT DID NOT COVER VCPU STACKING/COMPLIANCE
//public class StackingTests extends SubscriptionManagerCLITestScript {
//
//	
//	// Test methods ***********************************************************************
//	
//	@Test(	description="subscription-manager: subscribe to each pool with the same stacking_id to achieve compliance",
//			enabled=true,
//			groups={"AcceptanceTests","blockedByBug-739671", "blockedByBug-740377", "blockedByBug-861993", "blockedByBug-955142"},
//			dataProvider="getAvailableStackableAttributeSubscriptionPoolsData")
//	//@ImplementsNitrateTest(caseId=)
//	public void StackEachPoolToAchieveAttributeCompliance_Test(Object bugzilla, String attribute, List<SubscriptionPool> stackableAttributeSubscriptionPools) throws JSONException, Exception{
//		
//		// The strategy in this test is to simulate the facts on the systems so that the attribute being tested ("cores","ram",or "sockets")
//		// will achieve full compliance for all of the provided products after attaching a quantity of one entitlement
//		// from each pool in the list of stackable subscription pools.  As wee incrementally attach from each pool, we will assert
//		// a partial compliance until the final subscription is attached which should achieve full compliance.
//		
//		// loop through the pools to determine the minimum attribute count for which one
//		// of each stackable pool is needed to achieve compliance of the provided products
//		// also keep a list of all the provided productIds
//		Integer minimumAttributeValue=0;
//		Integer minimumSocketsValue=0;
//		Set<String> productIdsProvidedForByAllStackableSubscriptionPools = new HashSet<String>();
//		Map<String,Integer> poolProductAttributeValueMap = new HashMap<String,Integer>();
//		for (SubscriptionPool pool : stackableAttributeSubscriptionPools) {
//			String attributeValue = CandlepinTasks.getPoolProductAttributeValue(sm_clientUsername, sm_clientPassword, sm_serverUrl, pool.poolId, attribute);
//			productIdsProvidedForByAllStackableSubscriptionPools.addAll(CandlepinTasks.getPoolProvidedProductIds(sm_clientUsername, sm_clientPassword, sm_serverUrl, pool.poolId));
//			minimumAttributeValue+=Integer.valueOf(attributeValue);
//			poolProductAttributeValueMap.put(pool.poolId, Integer.valueOf(attributeValue));
//		}
//		
//		// override the system facts setting the attribute count to a value for which all the stackable subscriptions are needed to achieve compliance
//		Map<String,String> factsMap = new HashMap<String,String>();
//		factsMap.put("memory.memtotal", "1");
//		factsMap.put("cpu.cpu_socket(s)", "1");
//		factsMap.put("cpu.core(s)_per_socket", "1");
//		if (attribute.equals("ram")) {
//			factsMap.put("memory.memtotal", String.valueOf(minimumAttributeValue*1048576)); // "memory.memtotal" is stored in Kilobytes; "ram" is specified in Gigabytes; for conversions, see http://www.whatsabyte.com/P1/byteconverter.htm
//		}
//		if (attribute.equals("sockets")) {
//			factsMap.put("cpu.cpu_socket(s)", String.valueOf(minimumAttributeValue));
//		}
//		if (attribute.equals("cores")) {
//			if (Integer.valueOf(minimumAttributeValue)%2==0) {	// configure facts for an even number of cores
//				factsMap.put("cpu.core(s)_per_socket", "2");
//				minimumSocketsValue = Integer.valueOf(minimumAttributeValue)/2;
//				factsMap.put("cpu.cpu_socket(s)", String.valueOf(minimumSocketsValue));
//			} else {	// configure facts for an odd number of cores
//				factsMap.put("cpu.core(s)_per_socket", "1");
//				minimumSocketsValue = Integer.valueOf(minimumAttributeValue);
//				factsMap.put("cpu.cpu_socket(s)",  String.valueOf(minimumSocketsValue));
//			}
//		}
//		clienttasks.createFactsFileWithOverridingValues(factsMap);
//		
//		// register the system which has now been instrumented with facts to test the stack
//		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null,null,null,null,null,null,null,(List<String>)null,null,null,null,true,false,null,null,null);
//		
//		// assert installed product status for all the products that the stacked subscriptions will provide for
//		List<InstalledProduct> currentlyInstalledProducts = clienttasks.getCurrentlyInstalledProducts();
//		for (String productId : productIdsProvidedForByAllStackableSubscriptionPools) {
//			List<InstalledProduct> installedProducts = InstalledProduct.findAllInstancesWithMatchingFieldFromList("productId", productId, currentlyInstalledProducts);
//			if (installedProducts.isEmpty()) continue; // this productIdProvidedFor is not installed
//			if (installedProducts.size()>1) Assert.fail("Something is seriously wrong.  Found multiple InstalledProduct "+installedProducts+" with a common productId '"+productId+"'.");	// this should be impossible because the would all share the same /etc/pki/product/<productId>.pem file name
//			InstalledProduct installedProduct = installedProducts.get(0);
//			List<String> expectedStatusDetails = Arrays.asList(new String[]{"Not covered by a valid subscription."});
//			Assert.assertEquals(installedProduct.status,"Not Subscribed","Prior to subscribing to any of the stackable subscription pools, Installed product '"+installedProduct.productName+"' which is provided for by the subscription stack should have this status.");
//			if (installedProduct.statusDetails.isEmpty()) log.warning("Status Details appears empty.  Is your candlepin server older than 0.8.6?");
//			Assert.assertEquals(installedProduct.statusDetails,expectedStatusDetails,"Prior to subscribing to any of the stackable subscription pools, Installed product '"+installedProduct.productName+"' which is provided for by the subscription stack should have these status details: "+expectedStatusDetails);
//			//Assert.assertTrue(isEqualNoOrder(installedProduct.statusDetails,expectedStatusDetails),"Prior to subscribing to any of the stackable subscription pools, Installed product '"+installedProduct.productName+"' which is provided for by the subscription stack should have these status details: "+expectedStatusDetails);
//		}
//		
//		// incrementally attach one entitlement from each pool in the stack asserting the installed product's status and details along the way
//		// the final attachment should achieve full compliance for the provided products in the stack
//		int s=0;
//		Integer attributeValueStackedThusFar = 0;
//		Integer socketsValueStackedThusFar = 0;
//		Set<String> productIdsProvidedForThusFar = new HashSet<String>();
//		for (SubscriptionPool pool : stackableAttributeSubscriptionPools) {
//			clienttasks.subscribe(null,null,pool.poolId,null,null,"1",null,null,null,null,null);
//			
//			// add some test coverage for bugs 861993 and 955142
//			EntitlementCert entitlementCert = clienttasks.getEntitlementCertCorrespondingToSubscribedPool(pool);
//			if (attribute.equals("ram")) {
//				Assert.assertEquals(entitlementCert.orderNamespace.ramLimit,poolProductAttributeValueMap.get(pool.poolId).toString(),"rct cat-cert tool reports the expected RAM Limit value in the Order for subscription '"+pool.subscriptionName+"'.");
//			}
//			if (attribute.equals("sockets")) {
//				Assert.assertEquals(entitlementCert.orderNamespace.socketLimit,poolProductAttributeValueMap.get(pool.poolId).toString(),"rct cat-cert tool reports the expected Socket Limit value in the Order for subscription '"+pool.subscriptionName+"'.");
//			}
//			if (attribute.equals("cores")) {
//				Assert.assertEquals(entitlementCert.orderNamespace.coreLimit,poolProductAttributeValueMap.get(pool.poolId).toString(),"rct cat-cert tool reports the expected Core Limit value in the Order for subscription '"+pool.subscriptionName+"'.");
//			}
//			
//			// keep a running total of how much of the stackable attribute our entitlements have covered thus far
//			attributeValueStackedThusFar += poolProductAttributeValueMap.get(pool.poolId);
//			
//			// special case: when testing cores, we also need to track the sockets stacked thus far since the subscription may potentially includes a sockets attribute too
//			if (attribute.equals("cores")) {
//				String socketsValue = CandlepinTasks.getPoolProductAttributeValue(sm_clientUsername, sm_clientPassword, sm_serverUrl, pool.poolId, "sockets");
//				if (socketsValue!=null) {
//					socketsValueStackedThusFar += Integer.valueOf(socketsValue);
//				}
//			}
//			
//			// keep a running set of which productIdsProvidedFor have been covered by the subscriptions thus far
//			productIdsProvidedForThusFar.addAll(CandlepinTasks.getPoolProvidedProductIds(sm_clientUsername, sm_clientPassword, sm_serverUrl, pool.poolId));
//			
//			if (++s < stackableAttributeSubscriptionPools.size()) {	// are we still indexing through each pool in the stack (therefore partially compliant)?
//				
//				// assert the installed products that have been provided for by the stack of subscriptions thus far are Partially Subscribed
//				for (InstalledProduct installedProduct : clienttasks.getCurrentlyInstalledProducts()) {
//					if (productIdsProvidedForThusFar.contains(installedProduct.productId)) {
//						List<String> expectedStatusDetails = new ArrayList<String>();
//						if (attribute.equals("ram")) {
//							expectedStatusDetails.add(String.format("Only covers %sGB of %sGB of RAM.", attributeValueStackedThusFar,minimumAttributeValue));
//						}
//						if (attribute.equals("sockets")) {
//							expectedStatusDetails.add(String.format("Only covers %s of %s sockets.", attributeValueStackedThusFar,minimumAttributeValue));
//						}
//						if (attribute.equals("cores")) {
//							expectedStatusDetails.add(String.format("Only covers %s of %s cores.", attributeValueStackedThusFar,minimumAttributeValue));
//						}
//						if (attribute.equals("cores") && socketsValueStackedThusFar>0 && socketsValueStackedThusFar<minimumSocketsValue) {	// when a cores stack also includes sockets, we will have more status details
//							expectedStatusDetails.add(String.format("Only covers %s of %s sockets.", socketsValueStackedThusFar,minimumSocketsValue));
//						}
//						if (installedProduct.statusDetails.isEmpty()) log.warning("Status Details appears empty.  Is your candlepin server older than 0.8.6?");
//						Assert.assertEquals(installedProduct.status,"Partially Subscribed","After an incremental attachment of one stackable subscription for '"+pool.subscriptionName+"' poolId="+pool.poolId+", Installed product '"+installedProduct.productName+"' which is provided for by the subscription stack should have this status.");
//						Assert.assertTrue(isEqualNoOrder(installedProduct.statusDetails,expectedStatusDetails), "After an incremental attachment of one stackable subscription for '"+pool.subscriptionName+"' poolId="+pool.poolId+", Installed product '"+installedProduct.productName+"' which is provided for by the subscription stack should have these status details: "+expectedStatusDetails);
//						
//					} else {
//						if (productIdsProvidedForByAllStackableSubscriptionPools.contains(installedProduct.productId)) {
//							List<String> expectedStatusDetails = Arrays.asList(new String[]{"Not covered by a valid subscription."});
//							if (installedProduct.statusDetails.isEmpty()) log.warning("Status Details appears empty.  Is your candlepin server older than 0.8.6?");
//							Assert.assertEquals(installedProduct.status,"Not Subscribed","After an incremental attachment of one stackable subscription for '"+pool.subscriptionName+"' poolId="+pool.poolId+", Installed product '"+installedProduct.productName+"' which is NOT YET provided for by the subscription stack THUS FAR should have this status.");
//							Assert.assertEquals(installedProduct.statusDetails,expectedStatusDetails, "After an incremental attachment of one stackable subscription for '"+pool.subscriptionName+"' poolId="+pool.poolId+", Installed product '"+installedProduct.productName+"' which is NOT YET provided for by the subscription stack THUS FAR should have these status details: "+expectedStatusDetails);
//							//Assert.assertTrue(isEqualNoOrder(installedProduct.statusDetails,expectedStatusDetails), "After an incremental attachment of one stackable subscription for '"+pool.subscriptionName+"' poolId="+pool.poolId+", Installed product '"+installedProduct.productName+"' which is NOT YET provided for by the subscription stack THUS FAR should have these status details: "+expectedStatusDetails);
//						} else {
//							/* These asserts are valid, but not really relevant to this test.  Commented out to reduce noisy logging.
//							List<String> expectedStatusDetails = Arrays.asList(new String[]{"Not covered by a valid subscription."});
//							Assert.assertEquals(installedProduct.status,"Not Subscribed","After subscribing to stackable subscription for '"+pool.subscriptionName+"' poolId="+pool.poolId+", Installed product '"+installedProduct.productName+"' which is NOT provided for by the subscription stack should have this status.");
//							Assert.assertTrue(isEqualNoOrder(installedProduct.statusDetails,expectedStatusDetails), "After subscribing to stackable subscription for '"+pool.subscriptionName+"' poolId="+pool.poolId+", Installed product '"+installedProduct.productName+"' which is NOT provided for by the subscription stack should have these status details: "+expectedStatusDetails);
//							*/
//						}
//					}
//				}
//			} else {	// we have now attached the final entitlement (each pool in the stack has been subscribed)
//				
//				// assert all of the installed products provided for by the subscription stack are now fully Subscribed
//				for (InstalledProduct installedProduct : clienttasks.getCurrentlyInstalledProducts()) {
//					if (productIdsProvidedForByAllStackableSubscriptionPools.contains(installedProduct.productId)) {
//						
//						// special case: when sockets are also stacked with cores, we will have more status details and may not yet have met socket compliance
//						if (attribute.equals("cores") && socketsValueStackedThusFar>0 && socketsValueStackedThusFar<minimumSocketsValue) {
//							List<String> expectedStatusDetails = Arrays.asList(new String[]{String.format("Only covers %s of %s sockets.", socketsValueStackedThusFar,minimumSocketsValue)});
//							if (installedProduct.statusDetails.isEmpty()) log.warning("Status Details appears empty.  Is your candlepin server older than 0.8.6?");
//							Assert.assertEquals(installedProduct.status,"Partially Subscribed","After attaching the final stackable subscription for '"+pool.subscriptionName+"' poolId="+pool.poolId+", all system cores should be covered excepty sockets for Installed product '"+installedProduct.productName+"'.");
//							Assert.assertEquals(installedProduct.statusDetails,expectedStatusDetails,"After attaching the final stackable subscription for '"+pool.subscriptionName+"' poolId="+pool.poolId+", all system cores should be covered except sockets for Installed product '"+installedProduct.productName+"'.  Expecting status details: "+expectedStatusDetails);
//							//Assert.assertTrue(isEqualNoOrder(installedProduct.statusDetails,expectedStatusDetails), "After subscribing to stackable subscription pool '"+pool.subscriptionName+"' id="+pool.poolId+", All system cores should be covered, but not the sockets for Installed product '"+installedProduct.productName+"'.  Expecting status details: "+expectedStatusDetails);
//						} else {
//							
//							// assert this installed product is now fully subscribed
//							Assert.assertEquals(installedProduct.status,"Subscribed","After incrementally attaching the final stackable subscription for '"+pool.subscriptionName+"' poolId="+pool.poolId+", Installed product '"+installedProduct.productName+"' which is provided for by the subscription stack should have this status.");
//							Assert.assertTrue(installedProduct.statusDetails.isEmpty(),"The status details of Installed product '"+installedProduct.productName+"' which is fully subscribed should now be empty.");
//						}
//					} else {
//						// installed product should be Not Subscribed since it is not affected by the consumed stack of subscription
//						Assert.assertEquals(installedProduct.status,"Not Subscribed","The stackable subscriptions being tested do NOT provide for installed product '"+installedProduct.productName+"'.");
//					}
//				}
//			}
//		}
//	}
//	
//	@DataProvider(name="getAvailableStackableAttributeSubscriptionPoolsData")
//	public Object[][] getAvailableStackableAttributeSubscriptionPoolsDataAs2dArray() throws JSONException, Exception {
//		return TestNGUtils.convertListOfListsTo2dArray(getAvailableStackableAttributeSubscriptionPoolsDataAsListOfLists());
//	}
//	protected List<List<Object>> getAvailableStackableAttributeSubscriptionPoolsDataAsListOfLists() throws JSONException, Exception {
//		List<List<Object>> ll = new ArrayList<List<Object>>(); if (!isSetupBeforeSuiteComplete) return ll;
//		Map<String,List<SubscriptionPool>> stackableSubscriptionPoolsMap = new HashMap<String,List<SubscriptionPool>>();
//		
//		// find all the SubscriptionPools with the same stacking_id
//		for (List<Object> l : getAvailableSubscriptionPoolsDataAsListOfLists()) {
//			SubscriptionPool pool = (SubscriptionPool)l.get(0);
//			String stacking_id = CandlepinTasks.getPoolProductAttributeValue(sm_clientUsername, sm_clientPassword, sm_serverUrl, pool.poolId, "stacking_id");
//			
//			// skip non-stackable pools
//			if (stacking_id==null) continue;
//			
//			// skip instance-based pools (these are covered in InstanceTests.java) 
//			if (CandlepinTasks.isPoolProductInstanceBased(sm_clientUsername, sm_clientPassword, sm_serverUrl, pool.poolId)) continue;
//			
//			// add this available stackable pool to the stackableSubscriptionPoolsMap
//			if (!stackableSubscriptionPoolsMap.containsKey(stacking_id)) stackableSubscriptionPoolsMap.put(stacking_id, new ArrayList<SubscriptionPool>());
//			stackableSubscriptionPoolsMap.get(stacking_id).add(pool);
//		}
//		
//		// assemble the rows of data
//		for (String stacking_id : stackableSubscriptionPoolsMap.keySet()) {
//			List<SubscriptionPool> stackableSubscriptionPools = stackableSubscriptionPoolsMap.get(stacking_id);
//			
//			for (String attribute : new String[]{"sockets","cores","ram"}) {
//				List<SubscriptionPool> stackableAttributeSubscriptionPools = new ArrayList<SubscriptionPool>();
//
//				for (SubscriptionPool stackableSubscriptionPool : stackableSubscriptionPools) {
//					if (CandlepinTasks.getPoolProductAttributeValue(sm_clientUsername, sm_clientPassword, sm_serverUrl, stackableSubscriptionPool.poolId, attribute)!=null) {
//						stackableAttributeSubscriptionPools.add(stackableSubscriptionPool);
//					}
//				}
//				
//				if (!stackableAttributeSubscriptionPools.isEmpty()) {
//					// Object bugzilla, String attribute, List<SubscriptionPool> stackableAttributeSubscriptionPools
//					ll.add(Arrays.asList(new Object[]{null, attribute, stackableAttributeSubscriptionPools}));					
//				}
//			}
//		}
//		
//		return ll;
//	}
//	
//	
//	
//	
//	
//	// Candidates for an automated Test:
//	// TODO Bug 733327 - stacking entitlements reports as distinct entries in cli list --installed https://github.com/RedHatQE/rhsm-qe/issues/193
//	// TODO Bug 740377 - Stacking Partially Compliant / Yellow State is Broken https://github.com/RedHatQE/rhsm-qe/issues/194
//	// TODO Bug 743710 - Subscription manager displays incorrect status for partially subscribed subscription https://github.com/RedHatQE/rhsm-qe/issues/195
//	//      MAYBE THIS ONE BELONGS IN COMPLIANCE TESTS?
//	// TODO Bug 726409 - [RFE] Support for certificate stacking https://github.com/RedHatQE/rhsm-qe/issues/196
//	// TODO Bug 845126 - system.entitlements_valid goes from valid to partial after oversubscribing https://github.com/RedHatQE/rhsm-qe/issues/197
//		
//	// Configuration methods ***********************************************************************
//	
//	@AfterClass(groups={"setup"},alwaysRun=true)
//	public void cleanupAfterClass() {
//		if (clienttasks==null) return;
//		
//		// remove overriding test facts from last test
//		clienttasks.deleteFactsFileWithOverridingValues();
//	}
//	
//	// Protected methods ***********************************************************************
//	
//	
//	// Data Providers ***********************************************************************
//	
//	
//	// FIXME DELETEME? NOT BEING USED
//	@DataProvider(name="getAllStackableJSONPoolsData")
//	public Object[][] getAllStackableJSONPoolsDataAs2dArray() throws Exception {
//		return TestNGUtils.convertListOfListsTo2dArray(getAllStackableJSONPoolsDataAsListOfLists());
//	}
//	protected List<List<Object>> getAllStackableJSONPoolsDataAsListOfLists() throws Exception {
//		List<List<Object>> ll = new ArrayList<List<Object>>(); if (!isSetupBeforeSuiteComplete) return ll;
//		Map<String,List<JSONObject>> stackableJSONPoolsMap = new HashMap<String,List<JSONObject>>();
//		
//		for (List<Object> row : getAllJSONPoolsDataAsListOfLists()) {
//			JSONObject jsonPool = (JSONObject) row.get(0);
//			
//			
//			// loop through all the productAttributes looking for stacking_id
//			JSONArray jsonProductAttributes = jsonPool.getJSONArray("productAttributes");
//			for (int j = 0; j < jsonProductAttributes.length(); j++) {	// loop product attributes to find a stacking_id
//				if (((JSONObject) jsonProductAttributes.get(j)).getString("name").equals("stacking_id")) {
//					String stacking_id = ((JSONObject) jsonProductAttributes.get(j)).getString("value");
//					
//					// we found a stackable pool, let's add it to the stackableJSONPoolsMap
//					if (!stackableJSONPoolsMap.containsKey(stacking_id)) stackableJSONPoolsMap.put(stacking_id, new ArrayList<JSONObject>());
//					stackableJSONPoolsMap.get(stacking_id).add(jsonPool);
//					break;
//				}
//			}
//		}
//		
//		for (String stacking_id : stackableJSONPoolsMap.keySet()) {
//			List<JSONObject> stackableJSONPools = stackableJSONPoolsMap.get(stacking_id);
//			ll.add(Arrays.asList(new Object[]{stackableJSONPools}));
//		}
//		
//		return ll;
//	}
//	
//	// FIXME DELETEME? NOT BEING USED
//	@DataProvider(name="getAvailableStackableSubscriptionPoolsData")
//	public Object[][] getAvailableStackableSubscriptionPoolsDataAs2dArray() throws JSONException, Exception {
//		return TestNGUtils.convertListOfListsTo2dArray(getAvailableStackableSubscriptionPoolsDataAsListOfLists());
//	}
//	protected List<List<Object>> getAvailableStackableSubscriptionPoolsDataAsListOfLists() throws JSONException, Exception {
//		List<List<Object>> ll = new ArrayList<List<Object>>(); if (!isSetupBeforeSuiteComplete) return ll;
//		Map<String,List<SubscriptionPool>> stackableSubscriptionPoolsMap = new HashMap<String,List<SubscriptionPool>>();
//		
//		// find all the SubscriptionPools with the same stacking_id
//		for (List<Object> l : getAvailableSubscriptionPoolsDataAsListOfLists()) {
//			SubscriptionPool pool = (SubscriptionPool)l.get(0);
//			String stacking_id = CandlepinTasks.getPoolProductAttributeValue(sm_clientUsername, sm_clientPassword, sm_serverUrl, pool.poolId, "stacking_id");
//			
//			if (stacking_id==null) continue; // this pool is not stackable
//			
//			// add this available stackable pool to the stackableSubscriptionPoolsMap
//			if (!stackableSubscriptionPoolsMap.containsKey(stacking_id)) stackableSubscriptionPoolsMap.put(stacking_id, new ArrayList<SubscriptionPool>());
//			stackableSubscriptionPoolsMap.get(stacking_id).add(pool);
//		}
//		
//		// assemble the rows of data
//		for (String stacking_id : stackableSubscriptionPoolsMap.keySet()) {
//			List<SubscriptionPool> stackableSubscriptionPools = stackableSubscriptionPoolsMap.get(stacking_id);
//			
//			// List<SubscriptionPool> stackableSubscriptionPools
//			ll.add(Arrays.asList(new Object[]{stackableSubscriptionPools}));
//		}
//		
//		return ll;
//	}
//	
//	
//
//}
package rhsm.cli.tests;

import java.math.BigInteger;
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
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.redhat.qe.Assert;
import com.redhat.qe.auto.bugzilla.BlockedByBzBug;
import com.redhat.qe.auto.bugzilla.BzChecker;
import com.redhat.qe.auto.testng.TestNGUtils;
import rhsm.base.ConsumerType;
import rhsm.base.SubscriptionManagerCLITestScript;
import rhsm.cli.tasks.CandlepinTasks;
import rhsm.data.InstalledProduct;
import rhsm.data.ProductSubscription;
import rhsm.data.SubscriptionPool;

import com.redhat.qe.tools.SSHCommandResult;

/**
 * @author jsefler
 *
 */
@Test(groups={"ActivationKeyTests"})
public class ActivationKeyTests extends SubscriptionManagerCLITestScript {
	
	
	// Test methods ***********************************************************************

	@Test(	description="create an activation key named with an international character, add a pool to it (without specifying a quantity), and then register with the activation key",
			groups={},
			dataProvider="getRegisterWithUnknownActivationKeyData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void AttemptRegisterWithUnknownActivationKey_Test(Object blockedByBug, String unknownActivationKeyName, String org) {
		
		//SSHCommandResult sshCommandResult = clienttasks.register_(null, null, org, null, null, null, null, null, null, null, unknownActivationKeyName, null, null, true, null, null, null, null);
		SSHCommandResult sshCommandResult = clienttasks.runCommandWithLang(null,String.format("%s register --force --org=%s --activationkey=\"%s\"", clienttasks.command, org, unknownActivationKeyName));
		
		/* FIXME isSimpleASCII is a really crappy workaround for this case which I don't know how to fix properly:
		 * ACTUAL Stderr: Activation key 'ak_na_testov�n�' not found for organization 'admin'.
		 * EXPECTED Stderr: Activation key 'ak_na_testování' not found for organization 'admin'.
		Assert.assertEquals(sshCommandResult.getStderr().trim(), String.format("Activation key '%s' not found for organization '%s'.",unknownActivationKeyName, org), "Stderr message from an attempt to register with an unknown activation key.");
		 */
		/* 
		if (isStringSimpleASCII(unknownActivationKeyName)) {
			Assert.assertEquals(sshCommandResult.getStderr().trim(), String.format("Activation key '%s' not found for organization '%s'.",unknownActivationKeyName, org), "Stderr message from an attempt to register with an unknown activation key.");
		} else {
			Assert.assertContainsMatch(sshCommandResult.getStderr().trim(), String.format("Activation key '%s' not found for organization '%s'.",".*", org), "Stderr message from an attempt to register with an unknown activation key.");
		}
		FIXME UPDATE: THE CRAPPY WORKAROUND ABOVE APPEARS TO HAVE BEEN FIXED (BY BUG 800323 ?), REVERTING BACK TO ORIGINAL ASSERT... */
		
		Assert.assertEquals(sshCommandResult.getStderr().trim(), String.format("Activation key '%s' not found for organization '%s'.",unknownActivationKeyName, org), "Stderr message from an attempt to register with an unknown activation key.");
		Assert.assertEquals(sshCommandResult.getExitCode(), Integer.valueOf(255));
	}
	
	@Test(	description="use the candlepin api to create valid activation keys",
			groups={},
			dataProvider="getRegisterCredentialsExcludingNullOrgData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void ActivationKeyCreationDeletion_Test(String username, String password, String org) throws JSONException, Exception {
		// generate a unique name for this test
		String name = String.format("%s_%s-ActivationKey%s", username,org,System.currentTimeMillis());
		
		// create a JSON object to represent the request body
		Map<String,String> mapActivationKeyRequest = new HashMap<String,String>();
		mapActivationKeyRequest.put("name", name);
		JSONObject jsonActivationKeyRequest = new JSONObject(mapActivationKeyRequest);

		// call the candlepin api to create an activation key
		JSONObject jsonActivationKey = new JSONObject(CandlepinTasks.postResourceUsingRESTfulAPI(username, password, sm_serverUrl, "/owners/" + org + "/activation_keys", jsonActivationKeyRequest.toString()));

		// assert that the creation was successful (does not contain a displayMessage)
		if (jsonActivationKey.has("displayMessage")) {
			Assert.fail("The creation of an activation key appears to have failed: "+jsonActivationKey.getString("displayMessage"));
		}
		Assert.assertTrue(true,"The absense of a displayMessage indicates the activation key creation was probably successful.");

		// assert that the created key is listed
		// process all of the subscriptions belonging to ownerKey
		JSONArray jsonActivationKeys = new JSONArray(CandlepinTasks.getResourceUsingRESTfulAPI(username,password,sm_serverUrl,"/owners/"+org+"/activation_keys"));	
		JSONObject jsonActivationKeyI = null;
		for (int i = 0; i < jsonActivationKeys.length(); i++) {
			jsonActivationKeyI = (JSONObject) jsonActivationKeys.get(i);
			//{
			//    "created": "2011-08-04T21:38:23.902+0000", 
			//    "id": "8a90f8c63196bb20013196bba01e0008", 
			//    "name": "default_key", 
			//    "owner": {
			//        "displayName": "Admin Owner", 
			//        "href": "/owners/admin", 
			//        "id": "8a90f8c63196bb20013196bb9e210006", 
			//        "key": "admin"
			//    }, 
			//    "pools": [], 
			//    "updated": "2011-08-04T21:38:23.902+0000"
			//}
			
			// break out when the created activation key is found
			if (jsonActivationKeyI.getString("name").equals(name)) break;
		}
		Assert.assertNotNull(jsonActivationKeyI, "Successfully listed keys for owner '"+org+"'.");
		Assert.assertEquals(jsonActivationKey.toString(), jsonActivationKeyI.toString(), "Successfully found newly created activation key with credentials '"+username+"'/'"+password+"' under /owners/"+org+"/activation_keys .");
		
		// now assert that the activation key is found under /candlepin/activation_keys/<id>
		JSONObject jsonActivationKeyJ = new JSONObject(CandlepinTasks.getResourceUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword,sm_serverUrl,"/activation_keys/"+jsonActivationKey.getString("id")));
		Assert.assertEquals(jsonActivationKey.toString(), jsonActivationKeyJ.toString(), "Successfully found newly created activation key among all activation keys under /activation_keys.");

		// now attempt to delete the key
		CandlepinTasks.deleteResourceUsingRESTfulAPI(username, password, sm_serverUrl, "/activation_keys/"+jsonActivationKey.getString("id"));
		// assert that it is no longer found under /activation_keys
		jsonActivationKeys = new JSONArray(CandlepinTasks.getResourceUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword,sm_serverUrl,"/activation_keys"));
		jsonActivationKeyI = null;
		for (int i = 0; i < jsonActivationKeys.length(); i++) {
			jsonActivationKeyI = (JSONObject) jsonActivationKeys.get(i);
			if (jsonActivationKeyI.getString("id").equals(jsonActivationKey.getString("id"))) {
				Assert.fail("After attempting to delete activation key id '"+jsonActivationKey.getString("id")+"', it was still found in the /activation_keys list.");
			}
		}
		Assert.assertTrue(true,"Deleted activation key with id '"+jsonActivationKey.getString("id")+"' is no longer found in the /activation_keys list.");
	}

	
	@Test(	description="use the candlepin api to attempt creation of an activation key with a bad name",
			groups={},
			dataProvider="getActivationKeyCreationWithBadNameData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void AttemptActivationKeyCreationWithBadNameData_Test(Object blockedByBug, String badName) throws JSONException, Exception {
		
		// create a JSON object to represent the request body (with bad data)
		Map<String,String> mapActivationKeyRequest = new HashMap<String,String>();
		mapActivationKeyRequest.put("name", badName);
		JSONObject jsonActivationKeyRequest = new JSONObject(mapActivationKeyRequest);
		
		// call the candlepin api to create an activation key
		JSONObject jsonActivationKey = new JSONObject(CandlepinTasks.postResourceUsingRESTfulAPI(sm_clientUsername, sm_clientPassword, sm_serverUrl, "/owners/" + sm_clientOrg + "/activation_keys", jsonActivationKeyRequest.toString()));

		// assert that the creation was NOT successful (contains a displayMessage)
		if (jsonActivationKey.has("displayMessage")) {
			String displayMessage = jsonActivationKey.getString("displayMessage");
			//Assert.assertEquals(displayMessage, "Activation key names must be alphanumeric or the characters '-' or '_'. ["+badName+"]","Expected the creation of this activation key named '"+badName+"' to fail.");
			Assert.assertEquals(displayMessage, "The activation key name '"+badName+"' must be alphanumeric or include the characters - or _","Expected the creation of this activation key named '"+badName+"' to fail.");
		} else {
			log.warning("The absense of a displayMessage indicates the activation key creation was probably successful when we expected it to fail due to an invalid name '"+badName+"'.");
			Assert.assertFalse (badName.equals(jsonActivationKey.getString("name")),"The following activation key should not have been created with badName '"+badName+"': "+jsonActivationKey);
		}
	}
	
	
	@Test(	description="use the candlepin api to attempt to create a duplicate activation key",
			groups={"blockedByBug-728636"},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void AttemptActivationKeyCreationInDuplicate_Test() throws JSONException, Exception {

		// generate a unique name for this test
		String name = String.format("%s_%s-DuplicateActivationKey%s", sm_clientUsername,sm_clientOrg,System.currentTimeMillis());
		
		// create a JSON object to represent the request body
		Map<String,String> mapActivationKeyRequest = new HashMap<String,String>();
		mapActivationKeyRequest.put("name", name);
		JSONObject jsonActivationKeyRequest = new JSONObject(mapActivationKeyRequest);
		
		// call the candlepin api to create an activation key
		JSONObject jsonActivationKey = new JSONObject(CandlepinTasks.postResourceUsingRESTfulAPI(sm_clientUsername, sm_clientPassword, sm_serverUrl, "/owners/" + sm_clientOrg + "/activation_keys", jsonActivationKeyRequest.toString()));
		Assert.assertEquals(jsonActivationKey.getString("name"), name, "First activation key creation attempt appears successful.  Activation key: "+jsonActivationKey);

		// attempt to create another key by the same name
		jsonActivationKey = new JSONObject(CandlepinTasks.postResourceUsingRESTfulAPI(sm_clientUsername, sm_clientPassword, sm_serverUrl, "/owners/" + sm_clientOrg + "/activation_keys", jsonActivationKeyRequest.toString()));
		
		// assert that the creation was NOT successful (contains a displayMessage)
		if (jsonActivationKey.has("displayMessage")) {
			String displayMessage = jsonActivationKey.getString("displayMessage");
			// Activation key name [dupkey] is already in use for owner [admin]
			//Assert.assertEquals(displayMessage,"Activation key name ["+name+"] is already in use for owner ["+sm_clientOrg+"]","Expected the attempted creation of a duplicate activation key named '"+name+"' for owner '"+sm_clientOrg+"' to fail.");
			Assert.assertEquals(displayMessage,"The activation key name '"+name+"' is already in use for owner "+sm_clientOrg+"","Expected the attempted creation of a duplicate activation key named '"+name+"' for owner '"+sm_clientOrg+"' to fail.");
		} else {
			log.warning("The absense of a displayMessage indicates the activation key creation was probably successful when we expected it to fail due to a duplicate name '"+name+"'.");
			Assert.assertFalse (name.equals(jsonActivationKey.getString("name")),"The following activation key should not have been created with a duplicate name '"+name+"': "+jsonActivationKey);
		}
	}
	
	
	/**
	 * @param blockedByBug
	 * @param keyName
	 * @param jsonPool
	 * @param addQuantity
	 * @return If an attempt to register with the proposed activation key (made with the given keyName, jsonPool, and addQuantity) is made, then the result from the register is returned.  If no attempt is made, then null is returned.
	 * @throws JSONException
	 * @throws Exception
	 */
	@Test(	description="create an activation key, add a pool to it with a quantity, and then register with the activation key",
			groups={"blockedByBug-973838"},
			dataProvider="getRegisterWithActivationKeyContainingPoolWithQuantity_TestData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public SSHCommandResult RegisterWithActivationKeyContainingPoolWithQuantity_Test(Object blockedByBug, String keyName, JSONObject jsonPool, Integer addQuantity) throws JSONException, Exception {
//if (!jsonPool.getString("productId").equals("awesomeos-virt-4")) throw new SkipException("debugging...");
//if (jsonPool.getInt("quantity")!=-1) throw new SkipException("debugging...");
//if (!jsonPool.getString("productId").equals("awesomeos-virt-unlimited")) throw new SkipException("debugging...");
		String poolId = jsonPool.getString("id");
				
		// TEMPORARY WORKAROUND FOR BUG: https://bugzilla.redhat.com/show_bug.cgi?id=728721 - jsefler 8/6/2011
		if (CandlepinTasks.isPoolProductConsumableByConsumerType(sm_clientUsername, sm_clientPassword, sm_serverUrl, poolId, ConsumerType.person)) {
			boolean invokeWorkaroundWhileBugIsOpen = true;
			String bugId="728721"; 
			try {if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");SubscriptionManagerCLITestScript.addInvokedWorkaround(bugId);} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
			if (invokeWorkaroundWhileBugIsOpen) {
				throw new SkipException("Skipping this test while bug '"+bugId+"' is open. (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");
			}
		}
		// END OF WORKAROUND
		
		// generate a unique activation key name for this test
		//String keyName = String.format("ActivationKey%s_ForPool%s", System.currentTimeMillis(), poolId);
		
		// create a JSON object to represent the request body
		Map<String,String> mapActivationKeyRequest = new HashMap<String,String>();
		mapActivationKeyRequest.put("name", keyName);
		JSONObject jsonActivationKeyRequest = new JSONObject(mapActivationKeyRequest);
		
		// call the candlepin api to create an activation key
		JSONObject jsonActivationKey = new JSONObject(CandlepinTasks.postResourceUsingRESTfulAPI(sm_clientUsername, sm_clientPassword, sm_serverUrl, "/owners/" + sm_clientOrg + "/activation_keys", jsonActivationKeyRequest.toString()));
		Assert.assertEquals(jsonActivationKey.getString("name"), keyName, "Activation key creation attempt appears successful.  Activation key: "+jsonActivationKey);

		// add the pool with a random available quantity (?quantity=#) to the activation key
		int quantityAvail = jsonPool.getInt("quantity")-jsonPool.getInt("consumed");
		JSONObject jsonAddedPool = new JSONObject(CandlepinTasks.postResourceUsingRESTfulAPI(sm_clientUsername, sm_clientPassword, sm_serverUrl, "/activation_keys/" + jsonActivationKey.getString("id") + "/pools/" + poolId +(addQuantity==null?"":"?quantity="+addQuantity), null));
		if (addQuantity==null) {
			//addQuantity=1;	// this was true before Bug 1023568 - [RFE] bind requests using activation keys that do not specify a quantity should automatically use the quantity needed to achieve compliance
		}

		// handle the case when the pool productAttributes contain name:"requires_consumer_type" value:"person"
		if (ConsumerType.person.toString().equals(CandlepinTasks.getPoolProductAttributeValue(sm_clientUsername, sm_clientPassword, sm_serverUrl, poolId, "requires_consumer_type"))) {

			// assert that the adding of the pool to the key was NOT successful (contains a displayMessage from some thrown exception)
			if (jsonAddedPool.has("displayMessage")) {
				String displayMessage = jsonAddedPool.getString("displayMessage");
				//Assert.assertEquals(displayMessage,"Pools requiring a 'person' consumer should not be added to an activation key since a consumer type of 'person' cannot be used with activation keys","Expected the addition of a requires consumer type person pool '"+poolId+"' to activation key named '"+keyName+"' with quantity '"+addQuantity+"' to be blocked.");
				Assert.assertEquals(displayMessage,"Cannot add pools restricted to consumer type 'person' to activation keys.","Expected the addition of a requires consumer type person pool '"+poolId+"' to activation key named '"+keyName+"' with quantity '"+addQuantity+"' to be blocked.");
			} else {
				log.warning("The absense of a displayMessage indicates the activation key creation was probably successful when we expected it to fail since we should be blocked from adding pools that require consumer type person to an activation key.");
				Assert.assertFalse (keyName.equals(jsonActivationKey.getString("name")),"Pool '"+poolId+"' which requires a consumer type 'person' should NOT have been added to the following activation key with any quantity: "+jsonActivationKey);
			}
			return null;
		}
		
		// handle the case when the pool is NOT multi_entitlement and we tried to add the pool to the key with a quantity > 1
		if (!CandlepinTasks.isPoolProductMultiEntitlement(sm_clientUsername, sm_clientPassword, sm_serverUrl, poolId) && addQuantity!=null && addQuantity>1) {

			// assert that the adding of the pool to the key was NOT successful (contains a displayMessage from some thrown exception)
			if (jsonAddedPool.has("displayMessage")) {
				String displayMessage = jsonAddedPool.getString("displayMessage");
				Assert.assertEquals(displayMessage,"Error: Only pools with multi-entitlement product subscriptions can be added to the activation key with a quantity greater than one.","Expected the addition of a non-multi-entitlement pool '"+poolId+"' to activation key named '"+keyName+"' with quantity '"+addQuantity+"' to be blocked.");
			} else {
				log.warning("The absense of a displayMessage indicates the activation key creation was probably successful when we expected it to fail due to greater than one quantity '"+addQuantity+"'.");
				Assert.assertFalse (keyName.equals(jsonActivationKey.getString("name")),"Non multi-entitlement pool '"+poolId+"' should NOT have been added to the following activation key with a quantity '"+addQuantity+"' greater than one: "+jsonActivationKey);
			}
			return null;
		}
		
		// handle the case when the quantity is excessive
		if (addQuantity!=null && addQuantity>jsonPool.getInt("quantity") && addQuantity>1) {

			// assert that adding the pool to the key was NOT successful (contains a displayMessage)
			if (jsonAddedPool.has("displayMessage")) {
				String displayMessage = jsonAddedPool.getString("displayMessage");
				Assert.assertEquals(displayMessage,"The quantity must not be greater than the total allowed for the pool", "Expected the addition of multi-entitlement pool '"+poolId+"' to activation key named '"+keyName+"' with an excessive quantity '"+addQuantity+"' to be blocked.");
			} else {
				log.warning("The absense of a displayMessage indicates the activation key creation was probably successful when we expected it to fail due to an excessive quantity '"+addQuantity+"'.");
				Assert.assertFalse (keyName.equals(jsonActivationKey.getString("name")),"Pool '"+poolId+"' should NOT have been added to the following activation key with an excessive quantity '"+addQuantity+"': "+jsonActivationKey);
			}
			return null;
		}
		
		// handle the case when the quantity is insufficient (less than one)
		if (addQuantity!=null && addQuantity<1) {

			// assert that adding the pool to the key was NOT successful (contains a displayMessage)
			if (jsonAddedPool.has("displayMessage")) {
				String displayMessage = jsonAddedPool.getString("displayMessage");
				Assert.assertEquals(displayMessage,"The quantity must be greater than 0", "Expected the addition of pool '"+poolId+"' to activation key named '"+keyName+"' with quantity '"+addQuantity+"' less than one be blocked.");
			} else {
				log.warning("The absense of a displayMessage indicates the activation key creation was probably successful when we expected it to fail due to insufficient quantity '"+addQuantity+"'.");
				Assert.assertFalse (keyName.equals(jsonActivationKey.getString("name")),"Pool '"+poolId+"' should NOT have been added to the following activation key with insufficient quantity '"+addQuantity+"': "+jsonActivationKey);
			}
			return null;
		}
		
		// assert the pool is added
		jsonActivationKey = new JSONObject(CandlepinTasks.getResourceUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword,sm_serverUrl,"/activation_keys/"+jsonActivationKey.getString("id")));
		//# curl -k -u admin:admin https://jsefler-onprem-62candlepin.usersys.redhat.com:8443/candlepin/activation_keys/8a90f8c63196bb2001319f66afa83cb4 | python -mjson.tool
		//{
		//    "created": "2011-08-06T14:02:12.264+0000", 
		//    "id": "8a90f8c63196bb2001319f66afa83cb4", 
		//    "name": "ActivationKey1312639332183_ForPool8a90f8c63196bb20013196bc7f6302dc", 
		//    "owner": {
		//        "displayName": "Admin Owner", 
		//        "href": "/owners/admin", 
		//        "id": "8a90f8c63196bb20013196bb9e210006", 
		//        "key": "admin"
		//    }, 
		//    "pools": [
		//        {
		//            "created": "2011-08-06T14:02:12.419+0000", 
		//            "id": "8a90f8c63196bb2001319f66b0433cb6", 
		//            "pool": {
		//                "href": "/pools/8a90f8c63196bb20013196bc7f6302dc", 
		//                "id": "8a90f8c63196bb20013196bc7f6302dc"
		//            }, 
		//            "quantity": 1, 
		//            "updated": "2011-08-06T14:02:12.419+0000"
		//        }
		//    ], 
		//    "updated": "2011-08-06T14:02:12.264+0000"
		//}
		String addedPoolId = ((JSONObject) jsonActivationKey.getJSONArray("pools").get(0)).getJSONObject("pool").getString("id");	// get(0) since there should only be one pool added
		Assert.assertEquals(addedPoolId, poolId, "Pool id '"+poolId+"' appears to be successfully added to activation key: "+jsonActivationKey);
		if (addQuantity!=null) {
			Integer addedQuantity = ((JSONObject) jsonActivationKey.getJSONArray("pools").get(0)).getInt("quantity");	// get(0) since there should only be one pool added
			Assert.assertEquals(addedQuantity, addQuantity, "Pool id '"+poolId+"' appears to be successfully added with quantity '"+addQuantity+"' to activation key: "+jsonActivationKey);
		} else {
			// only possible after Bug 1023568 - [RFE] bind requests using activation keys that do not specify a quantity should automatically use the quantity needed to achieve compliance
			Assert.assertTrue(((JSONObject) jsonActivationKey.getJSONArray("pools").get(0)).isNull("quantity"), "Pool id '"+poolId+"' appears to be successfully added with a null quantity to activation key: "+jsonActivationKey);		
		}
		// register with the activation key
		SSHCommandResult registerResult = clienttasks.register_(null, null, sm_clientOrg, null, null, null, null, null, null, null, jsonActivationKey.getString("name"), null, null, null, true, null, null, null, null);
		
		// handle the case when "Consumers of this type are not allowed to subscribe to the pool with id '"+poolId+"'."
		ConsumerType type = null;
		if (!CandlepinTasks.isPoolProductConsumableByConsumerType(sm_clientUsername, sm_clientPassword, sm_serverUrl, poolId, ConsumerType.system)) {
			String expectedStderr = String.format("Consumers of this type are not allowed to subscribe to the pool with id '%s'.", poolId);
			if (!clienttasks.workaroundForBug876764(sm_serverType)) expectedStderr = String.format("Units of this type are not allowed to attach the pool with ID '%s'.", poolId);
			Assert.assertEquals(registerResult.getStderr().trim(), expectedStderr, "Registering a system consumer using an activationKey containing a pool that requires a non-system consumer type should fail.");
			Assert.assertEquals(registerResult.getExitCode(), Integer.valueOf(255), "The exitCode from registering a system consumer using an activationKey containing a pool that requires a non-system consumer type should fail.");
			Assert.assertNull(clienttasks.getCurrentConsumerCert(), "There should be no consumer cert on the system when register with activation key fails.");	// make sure there is no consumer cert - register with activation key should be 100% successful - if any one part fails, the whole operation fails

			// now register with the same activation key using the needed ConsumerType
			type = ConsumerType.valueOf(CandlepinTasks.getPoolProductAttributeValue(sm_clientUsername, sm_clientPassword, sm_serverUrl, poolId, "requires_consumer_type"));
			registerResult = clienttasks.register_(null, null, sm_clientOrg, null, type, null, null, null, null, null, jsonActivationKey.getString("name"), null, null, null, false /*was already unregistered by force above*/, null, null, null, null);
		}
		
		// handle the case when "A consumer type of 'person' cannot be used with activation keys"
		// resolution to: Bug 728721 - NullPointerException thrown when registering with an activation key bound to a pool that requires_consumer_type person
		if (ConsumerType.person.equals(type)) {
			Assert.assertEquals(registerResult.getStderr().trim(), "A consumer type of 'person' cannot be used with activation keys", "Registering with an activationKey containing a pool that requires_consumer_type=person should fail.");
			Assert.assertEquals(registerResult.getExitCode(), Integer.valueOf(255), "The exitCode from registering with an activationKey containing a pool that requires a person consumer should fail.");
			Assert.assertNull(clienttasks.getCurrentConsumerCert(), "There should be no consumer cert on the system when register with activation key fails.");	// make sure there is no consumer cert - register with activation key should be 100% successful - if any one part fails, the whole operation fails
			Assert.assertEquals(clienttasks.getCurrentlyConsumedProductSubscriptions().size(),0,"No subscriptions should be consumed after attempting to register with an activationKey containing a pool that requires a person consumer type.");
			return registerResult;
		}
		
		// handle the case when our quantity request exceeds the quantityAvail (when pool quantity is NOT unlimited)
		if ((addQuantity!=null) && (addQuantity>quantityAvail) && (jsonPool.getInt("quantity")!=-1/*exclude unlimited pools*/)) {
			//Assert.assertEquals(registerResult.getStderr().trim(), String.format("No entitlements are available from the pool with id '%s'.",poolId), "Registering with an activationKey containing a pool for which not enough entitlements remain should fail.");	// expected string changed by bug 876758
			String expectedStderr = String.format("No subscriptions are available from the pool with id '%s'.",poolId);
			if (!clienttasks.workaroundForBug876764(sm_serverType)) expectedStderr = String.format("No subscriptions are available from the pool with ID '%s'.",poolId);
			Assert.assertEquals(registerResult.getStderr().trim(), expectedStderr, "Registering with an activationKey containing a pool for which not enough entitlements remain should fail.");
			Assert.assertEquals(registerResult.getExitCode(), Integer.valueOf(255), "The exitCode from registering with an activationKey containing a pool for which not enough entitlements remain should fail.");
			Assert.assertNull(clienttasks.getCurrentConsumerCert(), "There should be no consumer cert on the system when register with activation key fails.");	// make sure there is no consumer cert - register with activation key should be 100% successful - if any one part fails, the whole operation fails
			return registerResult;
		}
		
		// handle the case when our candlepin is standalone and we have attempted a subscribe to a pool_derived virt_only pool (for which we have not registered our host system)
		if (servertasks.statusStandalone) {
			String pool_derived = CandlepinTasks.getPoolAttributeValue(jsonPool, "pool_derived");
			String virt_only = CandlepinTasks.getPoolAttributeValue(jsonPool, "virt_only");
			if (pool_derived!=null && virt_only!=null && Boolean.valueOf(pool_derived) && Boolean.valueOf(virt_only)) {

				// TEMPORARY WORKAROUND FOR BUG: https://bugzilla.redhat.com/show_bug.cgi?id=756628
				boolean invokeWorkaroundWhileBugIsOpen = true;
				String bugId="756628"; 
				try {if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");SubscriptionManagerCLITestScript.addInvokedWorkaround(bugId);} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
				if (invokeWorkaroundWhileBugIsOpen) {
					// 201111232226:08.420 - FINE: ssh root@jsefler-onprem-5server.usersys.redhat.com subscription-manager register --org=admin --activationkey=ActivationKey1322105167469_ForPool8a90f85733d31add0133d337f9410c52 --force
					// 201111232226:10.299 - FINE: Stdout: The system with UUID bd0271b6-2a0c-41b5-bbb8-df0ad4c7a088 has been unregistered
					// 201111232226:10.299 - FINE: Stderr: Unable to entitle consumer to the pool with id '8a90f85733d31add0133d337f9410c52'.: virt.guest.host.does.not.match.pool.owner
					// 201111232226:10.300 - FINE: ExitCode: 255
					Assert.assertTrue(registerResult.getStderr().trim().startsWith("Unable to entitle consumer to the pool with id '"+poolId+"'."), "Expected stderr to start with: \"Unable to entitle consumer to the pool with id '"+poolId+"'.\" because the host has not registered.");
					Assert.assertEquals(registerResult.getExitCode(), Integer.valueOf(255), "The exitCode from registering with an activationKey containing a virt_only derived_pool on a standalone candlepin server for which our system's host is not registered.");
					Assert.assertNull(clienttasks.getCurrentConsumerCert(), "There should be no consumer cert on the system when register with activation key fails.");	// make sure there is no consumer cert - register with activation key should be 100% successful - if any one part fails, the whole operation fails
					return registerResult;
				}
				// END OF WORKAROUND
				
				//201112021710:28.900 - FINE: ssh root@jsefler-onprem-5server.usersys.redhat.com subscription-manager register --org=admin --activationkey=ActivationKey1322863828312_ForPool8a90f85733fc4df80133fc6f6bf50e29 --force
				//201112021710:31.298 - FINE: Stdout: The system with UUID fc463d3d-dacb-4581-a2c6-2f4d69c7c457 has been unregistered
				//201112021710:31.299 - FINE: Stderr: Guest's host does not match owner of pool: '8a90f85733fc4df80133fc6f6bf50e29'.
				//201112021710:31.299 - FINE: ExitCode: 255
				Assert.assertEquals(registerResult.getStderr().trim(),"Guest's host does not match owner of pool: '"+poolId+"'.");
				Assert.assertEquals(registerResult.getExitCode(), Integer.valueOf(255), "The exitCode from registering with an activationKey containing a virt_only derived_pool on a standalone candlepin server for which our system's host is not registered.");
				Assert.assertNull(clienttasks.getCurrentConsumerCert(), "There should be no consumer cert on the system when register with activation key fails.");	// make sure there is no consumer cert - register with activation key should be 100% successful - if any one part fails, the whole operation fails

				return registerResult;
			}
		}
		
		// assert success
		Assert.assertEquals(registerResult.getStderr().trim(), "");
		Assert.assertNotSame(registerResult.getExitCode(), Integer.valueOf(255), "The exit code from the register command does not indicate a failure.");
		
		// assert that only the pool's providedProducts (excluding type=MKT products) are consumed (unless it is a ManagementAddOn product - indicated by no providedProducts)
		assertProvidedProductsFromPoolAreWithinConsumedProductSubscriptionsUsingQuantity(jsonPool, clienttasks.getCurrentlyConsumedProductSubscriptions(), addQuantity, true);
		
		// assert that the YumRepos immediately reflect the entitled contentNamespace labels // added for the benefit of Bug 973838 - subscription-manager needs to refresh redhat.repo when registering against katello
		verifyCurrentEntitlementCertsAreReflectedInCurrentlySubscribedYumRepos(clienttasks.getCurrentProductCerts());
		
		return registerResult;
	}
	
	
	@Test(	description="create an activation key, add it to a pool with an quantity outside the total possible available range",
			groups={"blockedByBug-729125"},
			dataProvider="getAllMultiEntitlementJSONPoolsData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void RegisterWithActivationKeyContainingPoolWithQuantityOutsideAvailableQuantity_Test(Object blockedByBug, String keyName, JSONObject jsonPool) throws JSONException, Exception {

		// choose a random pool quantity > totalPoolQuantity)
		Integer excessiveQuantity = jsonPool.getInt("quantity") + randomGenerator.nextInt(10) +1;
		//String keyName = String.format("ActivationKey%s_ForPool%s_", System.currentTimeMillis(), jsonPool.getString("id"));

		RegisterWithActivationKeyContainingPoolWithQuantity_Test(blockedByBug, keyName+"_Quantity"+excessiveQuantity, jsonPool, excessiveQuantity);
		RegisterWithActivationKeyContainingPoolWithQuantity_Test(blockedByBug, keyName+"_Quantity0", jsonPool, 0);
		RegisterWithActivationKeyContainingPoolWithQuantity_Test(blockedByBug, keyName+"_Quantity-1", jsonPool, -1);
		RegisterWithActivationKeyContainingPoolWithQuantity_Test(blockedByBug, keyName+"_Quantity-"+excessiveQuantity, jsonPool, -1*excessiveQuantity);
	}
	
	
	@Test(	description="create an activation key, add a pool to it (without specifying a quantity), and then register with the activation key",
			groups={"blockedByBug-878986","blockedByBug-979492","blockedByBug-1023568"},
			dataProvider="getRegisterWithActivationKeyContainingPool_TestData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void RegisterWithActivationKeyContainingPool_Test(Object blockedByBug, String keyName, JSONObject jsonPool) throws JSONException, Exception {
		RegisterWithActivationKeyContainingPoolWithQuantity_Test(blockedByBug, keyName, jsonPool, null);
	}
	
	/*
	 * CANDLEPIN DOES NOT PERMIT CREATION OF AN INTERNATIONAL KEY {"displayMessage":"Activation key names must be alphanumeric or the characters '-' or '_'. [ak_na_testovÃ¡nÃ­]"}
	 * HOWEVER, SAM/KATELLO DOES PERMIT CREATION OF INTERNATIONAL ACTIVATION KEYS  See https://bugzilla.redhat.com/show_bug.cgi?id=803773#c12
	 * @Test enabled=false
	 */
	@Test(	description="create an activation key named with an international character, add a pool to it (without specifying a quantity), and then register with the activation key",
			groups={"blockedByBug-803773","blockedByBug-1023568"},
			dataProvider="getRegisterWithInternationalActivationKeyContainingPool_TestData",
			enabled=false)
	@Deprecated
	//@ImplementsNitrateTest(caseId=)	
	public void RegisterWithInternationalActivationKeyContainingPool_Test(Object blockedByBug, String keyName, JSONObject jsonPool) throws JSONException, Exception {
		RegisterWithActivationKeyContainingPoolWithQuantity_Test(blockedByBug, keyName, jsonPool, null);
	}
	
	@Test(	description="create an activation key for each org and then attempt to register with the activation key using a different org",
			groups={},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void RegisterWithActivationKeyUsingWrongOrg_Test() throws JSONException, Exception {
		
		// loop through existing owners and remember the orgs
		JSONArray jsonOwners = new JSONArray(CandlepinTasks.getResourceUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword,sm_serverUrl,"/owners"));
		if (jsonOwners.length()<2) throw new SkipException("This test requires at least two orgs on your candlepin server.");
		List<String> orgs = new ArrayList<String>();
		for (int j = 0; j < jsonOwners.length(); j++) {
			JSONObject jsonOwner = (JSONObject) jsonOwners.get(j);
			// {
			//    "contentPrefix": null, 
			//    "created": "2011-07-01T06:39:58.740+0000", 
			//    "displayName": "Snow White", 
			//    "href": "/owners/snowwhite", 
			//    "id": "8a90f8c630e46c7e0130e46ce114000a", 
			//    "key": "snowwhite", 
			//    "parentOwner": null, 
			//    "updated": "2011-07-01T06:39:58.740+0000", 
			//    "upstreamUuid": null
			// }
			orgs.add(jsonOwner.getString("key"));
		}
		
		// now loop through the orgs and create an activation key and attempt to register using a different org
		for (String org : orgs) {
				
			// generate a unique activationkey name for this org
			String activationKeyName = String.format("ActivationKey%sForOrg_%s", System.currentTimeMillis(),org);
			
			// create a JSON object to represent the request body
			Map<String,String> mapActivationKeyRequest = new HashMap<String,String>();
			mapActivationKeyRequest.put("name", activationKeyName);
			JSONObject jsonActivationKeyRequest = new JSONObject(mapActivationKeyRequest);

			// call the candlepin api to create an activation key
			JSONObject jsonActivationKey = new JSONObject(CandlepinTasks.postResourceUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, "/owners/" + org + "/activation_keys",jsonActivationKeyRequest.toString()));

			// assert that the creation was successful (does not contain a displayMessage)
			if (jsonActivationKey.has("displayMessage")) {
				String displayMessage = jsonActivationKey.getString("displayMessage");
				Assert.fail("The creation of an activation key appears to have failed: "+displayMessage);
			}
			Assert.assertTrue(true,"The absense of a displayMessage indicates the activation key creation was probably successful.");
			
			// now assert that the new activation key is found under /candlepin/activation_keys/<id>
			JSONObject jsonActivationKeyJ = new JSONObject(CandlepinTasks.getResourceUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword,sm_serverUrl,"/activation_keys/"+jsonActivationKey.getString("id")));
			Assert.assertEquals(jsonActivationKey.toString(), jsonActivationKeyJ.toString(), "Successfully found newly created activation key among all activation keys under /activation_keys.");

			// now let's attempt to register with the activation key using a different org
			for (String differentOrg : orgs) {
				if (differentOrg.equals(org)) continue;
				
				SSHCommandResult registerResult = clienttasks.register_(null,null,differentOrg,null,null,null,null,null,null,null,activationKeyName,null,null, null, true, null, null, null, null);

				// assert the sshCommandResult here
				Assert.assertEquals(registerResult.getExitCode(), Integer.valueOf(255), "The expected exit code from the register attempt with activationKey using the wrong org.");
				//Assert.assertEquals(registerResult.getStdout().trim(), "", "The expected stdout result the register attempt with activationKey using the wrong org.");
				Assert.assertEquals(registerResult.getStderr().trim(), "Activation key '"+activationKeyName+"' not found for organization '"+differentOrg+"'.", "The expected stderr result from the register attempt with activationKey using the wrong org.");
				Assert.assertNull(clienttasks.getCurrentConsumerCert(), "There should be no consumer cert on the system when register with activation key fails.");	// make sure there is no consumer cert - register with activation key should be 100% successful - if any one part fails, the whole operation fails

			}
		}
	}
	
	
	@Test(	description="create an activation key with a valid quantity and attempt to register with it when not enough entitlements remain",
			groups={},
			dataProvider="getAllMultiEntitlementJSONPoolsData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void RegisterWithActivationKeyContainingPoolForWhichNotEnoughQuantityRemains_Test(Object blockedByBug, String keyName, JSONObject jsonPool) throws JSONException, Exception {
		
		// first, figure out how many entitlements remain
		int quantityAvail = jsonPool.getInt("quantity")-jsonPool.getInt("consumed");
		if (quantityAvail<1) throw new SkipException("Cannot do this test until there is an available entitlement for pool '"+jsonPool.getString("id")+"'.");

		// skip this pool when our candlepin is standalone and this is a pool_derived virt_only pool (for which we have not registered our host system)
		if (servertasks.statusStandalone) {
			String pool_derived = CandlepinTasks.getPoolAttributeValue(jsonPool, "pool_derived");
			String virt_only = CandlepinTasks.getPoolAttributeValue(jsonPool, "virt_only");
			if (pool_derived!=null && virt_only!=null && Boolean.valueOf(pool_derived) && Boolean.valueOf(virt_only)) {
				throw new SkipException("Skipping this virt_only derived_pool '"+jsonPool.getString("id")+"' on a standalone candlepin server since our system's host is not registered.");
			}
		}
		
		// now consume an entitlement from the pool
		String requires_consumer_type = CandlepinTasks.getPoolProductAttributeValue(sm_clientUsername, sm_clientPassword, sm_serverUrl, jsonPool.getString("id"), "requires_consumer_type");
		ConsumerType consumerType = requires_consumer_type==null?null:ConsumerType.valueOf(requires_consumer_type);
		String consumer1Id = clienttasks.getCurrentConsumerId(clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, consumerType, null, null, null, null, null, (String)null, null, null, null, true, null, null, null, null));
		SubscriptionPool subscriptionPool = SubscriptionPool.findFirstInstanceWithMatchingFieldFromList("poolId", jsonPool.getString("id"), clienttasks.getCurrentlyAllAvailableSubscriptionPools());
		clienttasks.subscribe(null, null, jsonPool.getString("id"), null, null, null, null, null, null, null, null);

		// remember the consuming consumerId
		// String consumer1Id = clienttasks.getCurrentConsumerId();
		systemConsumerIds.add(consumer1Id);
		
		// clean the system of all data (will not return the consumed entitlement)
		clienttasks.clean(null, null, null);
		
		// assert that the current pool recognizes an increment in consumption
		JSONObject jsonCurrentPool = new JSONObject(CandlepinTasks.getResourceUsingRESTfulAPI(sm_clientUsername, sm_clientPassword, sm_serverUrl, "/pools/"+jsonPool.getString("id")));
		//Assert.assertEquals(jsonCurrentPool.getInt("consumed"),jsonPool.getInt("consumed")+1,"The consumed entitlement from Pool '"+jsonPool.getString("id")+"' has incremented by one to an expected total of '"+(jsonPool.getInt("consumed")+1)+"' consumed.");	// valid before Bug 1008557 and Bug 1008647
		Integer suggested = subscriptionPool.suggested; Integer expectedIncrement = suggested>0? suggested:1;	// when subscriptionPool.suggested is zero, subscribe should still attach 1.
		Assert.assertEquals(jsonCurrentPool.getInt("consumed"),jsonPool.getInt("consumed")+expectedIncrement, "The consumed entitlement from Pool '"+jsonPool.getString("id")+"' has incremented by the suggested quantity '"+subscriptionPool.suggested+"' to an expected total of '"+(jsonPool.getInt("consumed")+expectedIncrement)+"' consumed (Except when suggested quantity is zero, then subscribe should still attach one entitlement).");
		
		// finally do the test...
		// create an activation key, add the current pool to the activation key with this valid quantity, and attempt to register with it.
		SSHCommandResult registerResult = RegisterWithActivationKeyContainingPoolWithQuantity_Test(blockedByBug, keyName, jsonCurrentPool, quantityAvail);
		
		String expectedStderr = String.format("No entitlements are available from the pool with id '%s'.", jsonCurrentPool.getString("id"));
		expectedStderr = String.format("No subscriptions are available from the pool with id '%s'.", jsonCurrentPool.getString("id"));	// string changed by bug 876758
		if (!clienttasks.workaroundForBug876764(sm_serverType)) expectedStderr = String.format("No subscriptions are available from the pool with ID '%s'.", jsonCurrentPool.getString("id"));
		Assert.assertEquals(registerResult.getStderr().trim(), expectedStderr, "Registering a with an activationKey containing a pool for which not enough entitlements remain should fail.");
		Assert.assertEquals(registerResult.getExitCode(), Integer.valueOf(255), "The exitCode from registering with an activationKey containing a pool for which non enough entitlements remain should fail.");
		Assert.assertNull(clienttasks.getCurrentConsumerCert(), "There should be no consumer cert on the system when register with activation key fails.");	// make sure there is no consumer cert - register with activation key should be 100% successful - if any one part fails, the whole operation fails
	}
	
	
	@Test(	description="create an activation key and add many pools to it and then register asserting all the pools get consumed",
			groups={/*"blockedByBug-1040101"*/},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void RegisterWithActivationKeyContainingMultiplePools_Test() throws JSONException, Exception {
		
		// get all of the pools belonging to ownerKey
		JSONArray jsonPools = new JSONArray(CandlepinTasks.getResourceUsingRESTfulAPI(sm_clientUsername,sm_clientPassword,sm_serverUrl,"/owners/"+sm_clientOrg+"/pools?listall=true"));	
		if (!(jsonPools.length()>1)) throw new SkipException("This test requires more than one pool for org '"+sm_clientOrg+"'."); 
		jsonPools = clienttasks.workaroundForBug1040101(jsonPools);
		
		// create an activation key
		String activationKeyName = String.format("ActivationKey%sWithMultiplePoolsForOrg_%s", System.currentTimeMillis(),sm_clientOrg);
		Map<String,String> mapActivationKeyRequest = new HashMap<String,String>();
		mapActivationKeyRequest.put("name", activationKeyName);
		JSONObject jsonActivationKeyRequest = new JSONObject(mapActivationKeyRequest);
		JSONObject jsonActivationKey = new JSONObject(CandlepinTasks.postResourceUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, "/owners/" + sm_clientOrg + "/activation_keys",jsonActivationKeyRequest.toString()));

		// process each of the pools adding them to the activation key
//		List<String> addedPoolIds = new ArrayList<String>();
		Integer addQuantity=null;
		JSONArray jsonPoolsAddedToActivationKey = new JSONArray();
		for (int i = 0; i < jsonPools.length(); i++) {
			JSONObject jsonPool = (JSONObject) jsonPools.get(i);
			
			// for the purpose of this test, skip pools with no available entitlements (consumed>=quantity) (quantity=-1 is unlimited)
			if (jsonPool.getInt("quantity")>0 && jsonPool.getInt("consumed")>=jsonPool.getInt("quantity")) continue;
			
			// for the purpose of this test, skip non-system pools otherwise the register will fail with "Consumers of this type are not allowed to subscribe to the pool with id '8a90f8c631ab7ccc0131ab7e46ca0619'."
			if (!CandlepinTasks.isPoolProductConsumableByConsumerType(sm_clientUsername,sm_clientPassword,sm_serverUrl,jsonPool.getString("id"), ConsumerType.system)) continue;
			
			// for the purpose of this test, skip virt_only derived_pool when server is standalone otherwise the register will fail with "Unable to entitle consumer to the pool with id '8a90f85733d86b130133d88c09410e5e'.: virt.guest.host.does.not.match.pool.owner"
			if (servertasks.statusStandalone) {
				String pool_derived = CandlepinTasks.getPoolAttributeValue(jsonPool, "pool_derived");
				String virt_only = CandlepinTasks.getPoolAttributeValue(jsonPool, "virt_only");
				if (pool_derived!=null && virt_only!=null && Boolean.valueOf(pool_derived) && Boolean.valueOf(virt_only)) {
					continue;
				}
			}
			
			// add the pool to the activation key
//			int quantityAvail = jsonPool.getInt("quantity")-jsonPool.getInt("consumed");
//			int bindQuantity = Math.max(1,randomGenerator.nextInt(quantityAvail+1));	// avoid a bindQuantity < 1 see https://bugzilla.redhat.com/show_bug.cgi?id=729125
			JSONObject jsonPoolAddedToActivationKey = new JSONObject(CandlepinTasks.postResourceUsingRESTfulAPI(sm_clientUsername, sm_clientPassword, sm_serverUrl, "/activation_keys/" + jsonActivationKey.getString("id") + "/pools/" + jsonPool.getString("id") + (addQuantity==null?"":"?quantity="+addQuantity), null));
			if (jsonPoolAddedToActivationKey.has("displayMessage")) {
				Assert.fail("Failed to add pool '"+jsonPool.getString("productId")+"' '"+jsonPool.getString("id")+"' to activation key '"+jsonActivationKey.getString("id")+"'.  DisplayMessage: "+jsonPoolAddedToActivationKey.getString("displayMessage"));
			}
//			addedPoolIds.add(poolId);
			jsonPoolsAddedToActivationKey.put(jsonPoolAddedToActivationKey);
		}
		if (addQuantity==null) addQuantity=1;
		jsonActivationKey = new JSONObject(CandlepinTasks.getResourceUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword,sm_serverUrl,"/activation_keys/"+jsonActivationKey.getString("id")));
		Assert.assertTrue(jsonActivationKey.getJSONArray("pools").length()>0,"MultiplePools have been added to the activation key: "+jsonActivationKey);
		Assert.assertEquals(jsonActivationKey.getJSONArray("pools").length(), jsonPoolsAddedToActivationKey.length(),"The number of attempted pools added equals the number of pools retrieved from the activation key: "+jsonActivationKey);
		
		// register with the activation key
		SSHCommandResult registerResult = clienttasks.register(null, null, sm_clientOrg, null, null, null, null, null, null, null, jsonActivationKey.getString("name"), null, null, null, true, null, null, null, null);
		
		// assert that all the pools were consumed
		List<ProductSubscription> consumedProductSubscriptions = clienttasks.getCurrentlyConsumedProductSubscriptions();
		for (int i = 0; i < jsonPoolsAddedToActivationKey.length(); i++) {
			JSONObject jsonPoolAdded = (JSONObject) jsonPoolsAddedToActivationKey.get(i);
						
			// assert that the pool's providedProducts (excluding type=MKT products) are consumed (unless it is a ManagementAddOn product - indicated by no providedProducts)
			assertProvidedProductsFromPoolAreWithinConsumedProductSubscriptionsUsingQuantity(jsonPoolAdded, consumedProductSubscriptions, addQuantity, false);
		}
		Assert.assertEquals(clienttasks.getCurrentEntitlementCertFiles().size(), jsonActivationKey.getJSONArray("pools").length(), "Expecting a new entitlement cert file in '"+clienttasks.entitlementCertDir+"' for each of the pools added to the activation key.");
	}
	
	
	@Test(	description="create many activation keys with one added pool per key and then register with --activationkey=comma_separated_string_of_keys asserting all the pools get consumed",
			groups={"blockedByBug-878986","blockedByBug-979492"/*,"blockedByBug-1040101"*/},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void RegisterWithListOfCommaSeparatedActivationKeys_Test() throws JSONException, Exception {
		
		// get all of the pools belonging to ownerKey
		JSONArray jsonPools = new JSONArray(CandlepinTasks.getResourceUsingRESTfulAPI(sm_clientUsername,sm_clientPassword,sm_serverUrl,"/owners/"+sm_clientOrg+"/pools?listall=true"));	
		if (!(jsonPools.length()>1)) throw new SkipException("This test requires more than one pool for org '"+sm_clientOrg+"'."); 
		jsonPools = clienttasks.workaroundForBug1040101(jsonPools);
		
		// process each of the pools adding them to an individual activation key
		List<String> activationKeyNames = new ArrayList<String>();
		Integer addQuantity=null;
		JSONArray jsonPoolsAddedToActivationKey = new JSONArray();
		for (int i = 0; i < jsonPools.length(); i++) {
			JSONObject jsonPool = (JSONObject) jsonPools.get(i);
			
			// for the purpose of this test, skip pools with no available entitlements (consumed>=quantity) (quantity=-1 is unlimited)
			if (jsonPool.getInt("quantity")>0 && jsonPool.getInt("consumed")>=jsonPool.getInt("quantity")) continue;
			
			// for the purpose of this test, skip non-system pools otherwise the register will fail with "Consumers of this type are not allowed to subscribe to the pool with id '8a90f8c631ab7ccc0131ab7e46ca0619'."
			if (!CandlepinTasks.isPoolProductConsumableByConsumerType(sm_clientUsername,sm_clientPassword,sm_serverUrl,jsonPool.getString("id"), ConsumerType.system)) continue;

			// for the purpose of this test, skip virt_only derived_pool when server is standalone otherwise the register will fail with "Unable to entitle consumer to the pool with id '8a90f85733d86b130133d88c09410e5e'.: virt.guest.host.does.not.match.pool.owner"
			if (servertasks.statusStandalone) {
				String pool_derived = CandlepinTasks.getPoolAttributeValue(jsonPool, "pool_derived");
				String virt_only = CandlepinTasks.getPoolAttributeValue(jsonPool, "virt_only");
				if (pool_derived!=null && virt_only!=null && Boolean.valueOf(pool_derived) && Boolean.valueOf(virt_only)) {
					continue;
				}
			}
			
			// create an activation key
			String activationKeyName = String.format("ActivationKey%sWithPool%sForOrg_%s", System.currentTimeMillis(),jsonPool.getString("id"),sm_clientOrg);
			Map<String,String> mapActivationKeyRequest = new HashMap<String,String>();
			mapActivationKeyRequest.put("name", activationKeyName);
			JSONObject jsonActivationKeyRequest = new JSONObject(mapActivationKeyRequest);
			JSONObject jsonActivationKey = new JSONObject(CandlepinTasks.postResourceUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, "/owners/" + sm_clientOrg + "/activation_keys",jsonActivationKeyRequest.toString()));
			
			// add the pool to the activation key
			JSONObject jsonPoolAddedToActivationKey = new JSONObject(CandlepinTasks.postResourceUsingRESTfulAPI(sm_clientUsername, sm_clientPassword, sm_serverUrl, "/activation_keys/" + jsonActivationKey.getString("id") + "/pools/" + jsonPool.getString("id") + (addQuantity==null?"":"?quantity="+addQuantity), null));
			if (jsonPoolAddedToActivationKey.has("displayMessage")) {
				Assert.fail("Failed to add pool '"+jsonPool.getString("productId")+"' '"+jsonPool.getString("id")+"' to activation key '"+jsonActivationKey.getString("id")+"'.  DisplayMessage: "+jsonPoolAddedToActivationKey.getString("displayMessage"));
			}
			jsonPoolsAddedToActivationKey.put(jsonPoolAddedToActivationKey);
			activationKeyNames.add(activationKeyName);
		}
		if (addQuantity==null) addQuantity=1;

		// assemble the comma separated list of activation key names
		String commaSeparatedActivationKeyNames = "";
		for (String activationKeyName : activationKeyNames) commaSeparatedActivationKeyNames+=activationKeyName+",";
		commaSeparatedActivationKeyNames = commaSeparatedActivationKeyNames.replaceFirst(",$", ""); // strip off trailing comma
		
		// register with the activation key specified as a single string
		SSHCommandResult registerResult = clienttasks.register(null, null, sm_clientOrg, null, null, null, null, null, null, null, commaSeparatedActivationKeyNames, null, null, null, true, null, null, null, null);
		
		// assert that all the pools were consumed
		List<ProductSubscription> consumedProductSubscriptions = clienttasks.getCurrentlyConsumedProductSubscriptions();
		for (int i = 0; i < jsonPoolsAddedToActivationKey.length(); i++) {
			JSONObject jsonPoolAdded = (JSONObject) jsonPoolsAddedToActivationKey.get(i);
						
			// assert that the pool's providedProducts (excluding type=MKT products) are consumed (unless it is a ManagementAddOn product - indicated by no providedProducts)
			assertProvidedProductsFromPoolAreWithinConsumedProductSubscriptionsUsingQuantity(jsonPoolAdded, consumedProductSubscriptions, addQuantity, false);
		}
		Assert.assertEquals(clienttasks.getCurrentEntitlementCertFiles().size(), activationKeyNames.size(), "Expecting a new entitlement cert file in '"+clienttasks.entitlementCertDir+"' for each of the single pooled activation keys used during register.");
	}
	
	
	@Test(	description="create many activation keys with one added pool per key and then register with a sequence of many --activationkey parameters asserting each pool per key gets consumed",
			groups={"blockedByBug-878986","blockedByBug-979492"/*,"blockedByBug-1040101"*/},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void RegisterWithSequenceOfMultipleActivationKeys_Test() throws JSONException, Exception {
		
		// get all of the pools belonging to ownerKey
		JSONArray jsonPools = new JSONArray(CandlepinTasks.getResourceUsingRESTfulAPI(sm_clientUsername,sm_clientPassword,sm_serverUrl,"/owners/"+sm_clientOrg+"/pools?listall=true"));	
		if (!(jsonPools.length()>1)) throw new SkipException("This test requires more than one pool for org '"+sm_clientOrg+"'."); 
		jsonPools = clienttasks.workaroundForBug1040101(jsonPools);
		
		// process each of the pools adding them to an individual activation key
		List<String> activationKeyNames = new ArrayList<String>();
		Integer addQuantity=null;
		JSONArray jsonPoolsAddedToActivationKey = new JSONArray();
		for (int i = 0; i < jsonPools.length(); i++) {
			JSONObject jsonPool = (JSONObject) jsonPools.get(i);
			
			// for the purpose of this test, skip pools with no available entitlements (consumed>=quantity) (quantity=-1 is unlimited)
			if (jsonPool.getInt("quantity")>0 && jsonPool.getInt("consumed")>=jsonPool.getInt("quantity")) continue;
			
			// for the purpose of this test, skip non-system pools otherwise the register will fail with "Consumers of this type are not allowed to subscribe to the pool with id '8a90f8c631ab7ccc0131ab7e46ca0619'."
			if (!CandlepinTasks.isPoolProductConsumableByConsumerType(sm_clientUsername,sm_clientPassword,sm_serverUrl,jsonPool.getString("id"), ConsumerType.system)) continue;
			
			// for the purpose of this test, skip virt_only derived_pool when server is standalone otherwise the register will fail with "Unable to entitle consumer to the pool with id '8a90f85733d86b130133d88c09410e5e'.: virt.guest.host.does.not.match.pool.owner"
			if (servertasks.statusStandalone) {
				String pool_derived = CandlepinTasks.getPoolAttributeValue(jsonPool, "pool_derived");
				String virt_only = CandlepinTasks.getPoolAttributeValue(jsonPool, "virt_only");
				if (pool_derived!=null && virt_only!=null && Boolean.valueOf(pool_derived) && Boolean.valueOf(virt_only)) {
					continue;
				}
			}
			
			// create an activation key
			String activationKeyName = String.format("ActivationKey%sWithPool%sForOrg_%s", System.currentTimeMillis(),jsonPool.getString("id"),sm_clientOrg);
			Map<String,String> mapActivationKeyRequest = new HashMap<String,String>();
			mapActivationKeyRequest.put("name", activationKeyName);
			JSONObject jsonActivationKeyRequest = new JSONObject(mapActivationKeyRequest);
			JSONObject jsonActivationKey = new JSONObject(CandlepinTasks.postResourceUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, "/owners/" + sm_clientOrg + "/activation_keys",jsonActivationKeyRequest.toString()));
			
			// add the pool to the activation key
			String path = "/activation_keys/" + jsonActivationKey.getString("id") + "/pools/" + jsonPool.getString("id") + (addQuantity==null?"":"?quantity="+addQuantity);
			JSONObject jsonPoolAddedToActivationKey = new JSONObject(CandlepinTasks.postResourceUsingRESTfulAPI(sm_clientUsername, sm_clientPassword, sm_serverUrl, path, null));
			if (jsonPoolAddedToActivationKey.has("displayMessage")) {
				Assert.fail("Failed to add pool '"+jsonPool.getString("productId")+"' '"+jsonPool.getString("id")+"' to activation key '"+jsonActivationKey.getString("id")+"'.  DisplayMessage: "+jsonPoolAddedToActivationKey.getString("displayMessage"));
			}
			jsonPoolsAddedToActivationKey.put(jsonPoolAddedToActivationKey);
			activationKeyNames.add(activationKeyName);
		}
		if (addQuantity==null) addQuantity=1;
		
		// register with the activation key specified as a single string
		SSHCommandResult registerResult = clienttasks.register(null, null, sm_clientOrg, null, null, null, null, null, null, null, activationKeyNames, null, null, null, true, null, null, null, null);
		
		// assert that all the pools were consumed
		List<ProductSubscription> consumedProductSubscriptions = clienttasks.getCurrentlyConsumedProductSubscriptions();
		for (int i = 0; i < jsonPoolsAddedToActivationKey.length(); i++) {
			JSONObject jsonPoolAdded = (JSONObject) jsonPoolsAddedToActivationKey.get(i);
						
			// assert that the pool's providedProducts (excluding type=MKT products) are consumed (unless it is a ManagementAddOn product - indicated by no providedProducts)
			assertProvidedProductsFromPoolAreWithinConsumedProductSubscriptionsUsingQuantity(jsonPoolAdded, consumedProductSubscriptions, addQuantity, false);
		}
		Assert.assertEquals(clienttasks.getCurrentEntitlementCertFiles().size(), activationKeyNames.size(), "Expecting a new entitlement cert file in '"+clienttasks.entitlementCertDir+"' for each of the single pooled activation keys used during register.");
	}
	
	
	// Candidates for an automated Test:
	// TODO Bug 755677 - failing to add a virt unlimited pool to an activation key  (SHOULD CREATE AN UNLIMITED POOL IN A BEFORE CLASS FOR THIS BUG TO AVOID RESTARTING CANDLEPIN IN standalone=false) https://github.com/RedHatQE/rhsm-qe/issues/113
	// TODO Bug 749636 - subscription-manager register fails with consumerid and activationkey specified https://github.com/RedHatQE/rhsm-qe/issues/114
	// TODO Bug 803814 - Registering with an activation key which has run out of susbcriptions results in a system in SAM, but no identity certificate https://github.com/RedHatQE/rhsm-qe/issues/115
	
	
	
	// Protected Class Variables ***********************************************************************
	
	
	// Configuration methods ***********************************************************************

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
		clienttasks.restart_rhsmcertd(null,null,false, null);
	}

	@BeforeClass(groups="setup")
	public void setupBeforeClass() throws Exception {
		if (sm_clientOrg!=null) return;
		// alternative to dependsOnGroups={"RegisterWithCredentials_Test"}
		// This allows us to satisfy a dependency on registrationDataList making TestNG add unwanted Test results.
		// This also allows us to individually run this Test Class on Hudson.
		RegisterWithCredentials_Test(); // needed to populate registrationDataList
		clienttasks.stop_rhsmcertd();	// needed to prevent autoheal from subscribing to pools that the activation keys are supposed to be subscribing
	}
	
	// Protected methods ***********************************************************************

	protected List<String> systemConsumerIds = new ArrayList<String>();
	
	// THIS IS THE ORIGINAL METHOD VALID PRIOR TO THE CHANGE IN list --consumed BEHAVIOR MODIFIED BY BUG 801187
	protected void assertProvidedProductsFromPoolAreWithinConsumedProductSubscriptionsUsingQuantity_OLD (JSONObject jsonPool, List<ProductSubscription> consumedProductSubscriptions, Integer addQuantity, boolean assertConsumptionIsLimitedToThisPoolOnly) throws Exception {

		// assert that only the pool's providedProducts (excluding type=MKT products) are consumed (unless it is a ManagementAddOn product - indicated by no providedProducts)
		JSONArray jsonProvidedProducts = jsonPool.getJSONArray("providedProducts");
		// pluck out (remove) the providedProducts that have an attribute type=MKT products
		for (int j = 0; j < jsonProvidedProducts.length(); j++) {
			JSONObject jsonProvidedProduct = (JSONObject) jsonProvidedProducts.get(j);
			JSONObject jsonProduct = new JSONObject(CandlepinTasks.getResourceUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword,sm_serverUrl,"/products/"+jsonProvidedProduct.getString("productId")));
			JSONArray jsonAttributes = jsonProduct.getJSONArray("attributes");
			for (int k = 0; k < jsonAttributes.length(); k++) {
				JSONObject jsonAttribute = (JSONObject) jsonAttributes.get(k);
				if (jsonAttribute.getString("name").equals("type")) {
					if (jsonAttribute.getString("value").equals("MKT")) {
						log.info("Found a providedProduct '"+jsonProvidedProduct.getString("productName")+"' from the pool added to the activation key that is actually a Marketing product (type=\"MKT\").  Therefore this provided product will be excluded from the expected consumed ProductSubscriptions assertions that will follow...");
						jsonProvidedProduct/*Plucked*/ = (JSONObject) jsonProvidedProducts.remove(j--);
						break;
					}
				}
			}
		}
	
		//List<ProductSubscription> consumedProductSubscriptions = clienttasks.getCurrentlyConsumedProductSubscriptions();
		if (jsonProvidedProducts.length()>0) { 
			if (assertConsumptionIsLimitedToThisPoolOnly)	Assert.assertEquals(consumedProductSubscriptions.size(), jsonProvidedProducts.length(), "The number of providedProducts from the pool added to the activation key should match the number of consumed product subscriptions.");
			else											Assert.assertTrue(consumedProductSubscriptions.size()>=jsonProvidedProducts.length(), "The number of providedProducts from the pool added to the activation key should match (at least) the number of consumed product subscriptions.");
			for (int j = 0; j < jsonProvidedProducts.length(); j++) {
				JSONObject jsonProvidedProduct = (JSONObject) jsonProvidedProducts.get(j);
				//{
				//    "created": "2011-08-04T21:39:21.059+0000", 
				//    "id": "8a90f8c63196bb20013196bc7f6402e7", 
				//    "productId": "37060", 
				//    "productName": "Awesome OS Server Bits", 
				//    "updated": "2011-08-04T21:39:21.059+0000"
				//}
				String providedProductName = jsonProvidedProduct.getString("productName");
				if (assertConsumptionIsLimitedToThisPoolOnly) {
					ProductSubscription consumedProductSubscription = ProductSubscription.findFirstInstanceWithMatchingFieldFromList("productName", providedProductName, consumedProductSubscriptions);
					Assert.assertNotNull(consumedProductSubscription,"Found a consumed product subscription whose productName '"+providedProductName+"' is included in the providedProducts added in the activation key.");
					Assert.assertEquals(consumedProductSubscription.accountNumber.longValue(), jsonPool.getLong("accountNumber"), "The consumed product subscription comes from the same accountNumber as the pool added in the activation key.");
					Assert.assertEquals(consumedProductSubscription.contractNumber.intValue(), jsonPool.getInt("contractNumber"), "The consumed product subscription comes from the same contractNumber as the pool added in the activation key.");
					Assert.assertEquals(consumedProductSubscription.quantityUsed, addQuantity, "The consumed product subscription is using the same quantity as requested by the pool added in the activation key.");
				} else {
					List<ProductSubscription> subsetOfConsumedProductSubscriptions = ProductSubscription.findAllInstancesWithMatchingFieldFromList("productName", providedProductName, consumedProductSubscriptions);
					ProductSubscription consumedProductSubscription = ProductSubscription.findFirstInstanceWithMatchingFieldFromList("contractNumber", jsonPool.getInt("contractNumber"), subsetOfConsumedProductSubscriptions);
					Assert.assertNotNull(consumedProductSubscription,"Found a consumed product subscription whose productName '"+providedProductName+"' AND contract number '"+jsonPool.getInt("contractNumber")+"' is included in the providedProducts added to the activation key.");
					Assert.assertEquals(consumedProductSubscription.accountNumber.longValue(), jsonPool.getLong("accountNumber"), "The consumed product subscription comes from the same accountNumber as the pool added in the activation key.");
					Assert.assertEquals(consumedProductSubscription.contractNumber.intValue(), jsonPool.getInt("contractNumber"), "The consumed product subscription comes from the same contractNumber as the pool added in the activation key.");
					Assert.assertEquals(consumedProductSubscription.quantityUsed, addQuantity, "The consumed product subscription is using the same quantity as requested by the pool added in the activation key.");
				}
			}
		} else {	// this pool provides a subscription to a Management AddOn product (indicated by no providedProducts)
			if (assertConsumptionIsLimitedToThisPoolOnly)	Assert.assertEquals(consumedProductSubscriptions.size(), 1, "When a ManagementAddOn product is added to the activation key, then the number of consumed product subscriptions should be one.");
			else											Assert.assertTrue(consumedProductSubscriptions.size()>=1, "When a ManagementAddOn product is added to the activation key, then the number of consumed product subscriptions should be (at least) one.");
			if (assertConsumptionIsLimitedToThisPoolOnly) {
				ProductSubscription consumedProductSubscription = ProductSubscription.findFirstInstanceWithMatchingFieldFromList("productName", jsonPool.getString("productName"), consumedProductSubscriptions);
				Assert.assertNotNull(consumedProductSubscription,"Found a consumed product subscription whose productName '"+jsonPool.getString("productName")+"' matches the pool's productName added in the activation key.");
				Assert.assertEquals(consumedProductSubscription.accountNumber.longValue(), jsonPool.getLong("accountNumber"), "The consumed product subscription comes from the same accountNumber as the pool added in the activation key.");
				Assert.assertEquals(consumedProductSubscription.contractNumber.intValue(), jsonPool.getInt("contractNumber"), "The consumed product subscription comes from the same contractNumber as the pool added in the activation key.");
				Assert.assertEquals(consumedProductSubscription.quantityUsed, addQuantity, "The consumed product subscription is using the same quantity as requested by the pool added in the activation key.");
			} else {
				List<ProductSubscription> subsetOfConsumedProductSubscriptions = ProductSubscription.findAllInstancesWithMatchingFieldFromList("productName", jsonPool.getString("productName"), consumedProductSubscriptions);
				ProductSubscription consumedProductSubscription = ProductSubscription.findFirstInstanceWithMatchingFieldFromList("contractNumber", jsonPool.getInt("contractNumber"), subsetOfConsumedProductSubscriptions);
				Assert.assertNotNull(consumedProductSubscription,"Found a consumed product subscription whose productName '"+jsonPool.getString("productName")+"' AND contract number '"+jsonPool.getInt("contractNumber")+"' matches a pool's productName and contractNumber added to the activation key.");
				Assert.assertEquals(consumedProductSubscription.accountNumber.longValue(), jsonPool.getLong("accountNumber"), "The consumed product subscription comes from the same accountNumber as the pool added in the activation key.");
				Assert.assertEquals(consumedProductSubscription.contractNumber.intValue(), jsonPool.getInt("contractNumber"), "The consumed product subscription comes from the same contractNumber as the pool added in the activation key.");
				Assert.assertEquals(consumedProductSubscription.quantityUsed, addQuantity, "The consumed product subscription is using the same quantity as requested by the pool added in the activation key.");
			}
		}
	}
	protected void assertProvidedProductsFromPoolAreWithinConsumedProductSubscriptionsUsingQuantity (JSONObject jsonPool, List<ProductSubscription> consumedProductSubscriptions, Integer addQuantity, boolean assertConsumptionIsLimitedToThisPoolOnly) throws Exception {
		
		// assert that only the pool's providedProducts (excluding type=MKT products) are consumed (unless it is a ManagementAddOn product - indicated by no providedProducts)
		JSONArray jsonProvidedProducts = jsonPool.getJSONArray("providedProducts");
		// pluck out (remove) the providedProducts that have an attribute type=MKT products
		for (int j = 0; j < jsonProvidedProducts.length(); j++) {
			JSONObject jsonProvidedProduct = (JSONObject) jsonProvidedProducts.get(j);
			JSONObject jsonProduct = new JSONObject(CandlepinTasks.getResourceUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword,sm_serverUrl,"/products/"+jsonProvidedProduct.getString("productId")));
			JSONArray jsonAttributes = jsonProduct.getJSONArray("attributes");
			for (int k = 0; k < jsonAttributes.length(); k++) {
				JSONObject jsonAttribute = (JSONObject) jsonAttributes.get(k);
				if (jsonAttribute.getString("name").equals("type")) {
					if (jsonAttribute.getString("value").equals("MKT")) {
						log.warning("Found a providedProduct '"+jsonProvidedProduct.getString("productName")+"' from the pool added to the activation key that is actually a Marketing product (attribute type=\"MKT\").  Therefore this provided product will be excluded from the expected consumed ProductSubscriptions assertions that will follow...");
						jsonProvidedProduct/*Plucked*/ = (JSONObject) jsonProvidedProducts.remove(j--);
						break;
					}
				}
			}
		}
		// translate the names of the jsonProvidedProducts into a list of string
		List<String> providedProductNamesFromActivationKeyPool = new ArrayList<String>();
		List<String> providedProductIdsFromActivationKeyPool = new ArrayList<String>();
		for (int j = 0; j < jsonProvidedProducts.length(); j++) {
			JSONObject jsonProvidedProduct = (JSONObject) jsonProvidedProducts.get(j);
			providedProductNamesFromActivationKeyPool.add(jsonProvidedProduct.getString("productName"));
			providedProductIdsFromActivationKeyPool.add(jsonProvidedProduct.getString("productId"));
		}
		
		if (assertConsumptionIsLimitedToThisPoolOnly) {
			Assert.assertEquals(consumedProductSubscriptions.size(),1,"Expecting only one consumed product subscription.");
			ProductSubscription consumedProductSubscription = consumedProductSubscriptions.get(0);
			Assert.assertEquals(consumedProductSubscription.accountNumber.longValue(), jsonPool.getLong("accountNumber"), "The consumed product subscription comes from the same accountNumber as the pool added in the activation key.");
			Assert.assertEquals(consumedProductSubscription.contractNumber.intValue(), jsonPool.getInt("contractNumber"), "The consumed product subscription comes from the same contractNumber as the pool added in the activation key.");
			if (addQuantity!=null) {
				Assert.assertEquals(consumedProductSubscription.quantityUsed, addQuantity, "The consumed product subscription is using the same quantity as requested by the pool added in the activation key.");
			} else {
				// valid after Bug 1023568 - [RFE] bind requests using activation keys that do not specify a quantity should automatically use the quantity needed to achieve compliance
				Assert.assertTrue(consumedProductSubscription.quantityUsed>=1, "The actual consumed product subscription quantity of '"+consumedProductSubscription.quantityUsed+"' is >= 1 to achieve compliance since the quantity requested by the pool added in the activation key was null (result of RFE bugzilla 1023568).");
				// if this subscription was stackable, then assert that all of the installed products provided by this subscription are fully subscribed
				if (consumedProductSubscription.subscriptionType.equals("Stackable")) {
					for (InstalledProduct installedProduct : clienttasks.getCurrentlyInstalledProducts()) {
						if (providedProductIdsFromActivationKeyPool.contains(installedProduct.productId)) {
							Assert.assertEquals(installedProduct.status,"Subscribed", "Installed Product '"+installedProduct.productName+"' provided by pool '"+consumedProductSubscription.productName+"' attached from a Smart ActivationKey (quantity='"+addQuantity/*null*/+"') should be fully compliant.");
						}
					}
				}
			}
			Assert.assertTrue(consumedProductSubscription.provides.containsAll(providedProductNamesFromActivationKeyPool)&&providedProductNamesFromActivationKeyPool.containsAll(consumedProductSubscription.provides), "The consumed product subscription provides all the expected products "+providedProductNamesFromActivationKeyPool+" from the provided products of the pool added in the activation key.");
		} else {
// after implementation of bug 908671, these three lines are replaced more efficiently by two lines
//			List<ProductSubscription> subsetOfConsumedProductSubscriptions = ProductSubscription.findAllInstancesWithMatchingFieldFromList("accountNumber", new BigInteger(jsonPool.getString("accountNumber")), consumedProductSubscriptions);
//			ProductSubscription consumedProductSubscription = ProductSubscription.findFirstInstanceWithMatchingFieldFromList("contractNumber", new Integer(jsonPool.getString("contractNumber")), subsetOfConsumedProductSubscriptions);
//			Assert.assertNotNull(consumedProductSubscription,"Found a consumed product subscription whose account number '"+jsonPool.getLong("accountNumber")+"' AND contract number '"+jsonPool.getInt("contractNumber")+"' match the pool added to the activation key.");
			ProductSubscription consumedProductSubscription = ProductSubscription.findFirstInstanceWithMatchingFieldFromList("poolId", jsonPool.getString("id"), consumedProductSubscriptions);
			Assert.assertNotNull(consumedProductSubscription,"Found a consumed product subscription that came from pool id '"+ jsonPool.getString("id")+"' that was added to the activation key.");
			Assert.assertTrue(consumedProductSubscription.provides.containsAll(providedProductNamesFromActivationKeyPool)&&providedProductNamesFromActivationKeyPool.containsAll(consumedProductSubscription.provides), "The consumed product subscription provides all the expected products "+providedProductNamesFromActivationKeyPool+" from the provided products of the pool added in the activation key.");
		}
	}
	
	
	
	
	// Data Providers ***********************************************************************
	
	@DataProvider(name="getAllMultiEntitlementJSONPoolsData")
	public Object[][] getAllMultiEntitlementJSONPoolsDataAs2dArray() throws Exception {
		return TestNGUtils.convertListOfListsTo2dArray(getAllMultiEntitlementJSONPoolsDataAsListOfLists());
	}
	protected List<List<Object>> getAllMultiEntitlementJSONPoolsDataAsListOfLists() throws Exception {
		List<List<Object>> ll = new ArrayList<List<Object>>(); if (!isSetupBeforeSuiteComplete) return ll;
		clienttasks.unregister_(null,null,null);	// so as to return all entitlements consumed by the current consumer
		for (List<Object> l : getAllJSONPoolsDataAsListOfLists()) {
			JSONObject jsonPool = (JSONObject)l.get(0);
			String keyName = String.format("ActivationKey%s_ForPool%s", System.currentTimeMillis(), jsonPool.getString("id"));
		
			if (CandlepinTasks.isPoolProductMultiEntitlement(sm_clientUsername, sm_clientPassword, sm_serverUrl, jsonPool.getString("id"))) {

				// Object blockedByBug, JSONObject jsonPool)
				ll.add(Arrays.asList(new Object[] {null, keyName, jsonPool}));
				
				// minimize the number of dataProvided rows (useful during automated testcase development)
				if (Boolean.valueOf(getProperty("sm.debug.dataProviders.minimize","false"))) break;
			}
		}
		return ll;
	}
	
	@DataProvider(name="getRegisterWithActivationKeyContainingPool_TestData")
	public Object[][] getRegisterWithActivationKeyContainingPool_TestDataAs2dArray() throws Exception {
		return TestNGUtils.convertListOfListsTo2dArray(getRegisterWithActivationKeyContainingPool_TestDataAsListOfLists());
	}
	protected List<List<Object>> getRegisterWithActivationKeyContainingPool_TestDataAsListOfLists() throws Exception {
		List<List<Object>> ll = new ArrayList<List<Object>>();
//		for (List<Object> l : getAllJSONPoolsDataAsListOfLists()) {	// takes a long time and rarely reveals a bug, limiting the loop to a random subset...
		for (List<Object> l : getRandomSubsetOfList(getAllJSONPoolsDataAsListOfLists(),10)) {
			JSONObject jsonPool = (JSONObject)l.get(0);
			String keyName = String.format("ActivationKey%s_ForPool%s", System.currentTimeMillis(), jsonPool.getString("id"));

			// Object blockedByBug, String keyName, JSONObject jsonPool)
			ll.add(Arrays.asList(new Object[] {null, keyName, jsonPool}));
		}
		return ll;
	}
	
	@DataProvider(name="getRegisterWithInternationalActivationKeyContainingPool_TestData")
	public Object[][] getRegisterWithInternationalActivationKeyContainingPool_TestDataAs2dArray() throws Exception {
		return TestNGUtils.convertListOfListsTo2dArray(getRegisterWithInternationalActivationKeyContainingPool_TestDataAsListOfLists());
	}
	protected List<List<Object>> getRegisterWithInternationalActivationKeyContainingPool_TestDataAsListOfLists() throws Exception {
		List<List<Object>> ll = new ArrayList<List<Object>>();
		
		// randomly choose a pool
		List<List<Object>> allJSONPoolsDataAsListOfLists = getAllJSONPoolsDataAsListOfLists();
		JSONObject jsonPool = (JSONObject)allJSONPoolsDataAsListOfLists.get(randomGenerator.nextInt(allJSONPoolsDataAsListOfLists.size())).get(0);  // randomly pick a pool
		
		// Object blockedByBug, String keyName, JSONObject jsonPool)
		ll.add(Arrays.asList(new Object[] {null,	"ak_na_testování", jsonPool}));

		return ll;
	}
	
	@DataProvider(name="getRegisterWithActivationKeyContainingPoolWithQuantity_TestData")
	public Object[][] getRegisterWithActivationKeyContainingPoolWithQuantity_TestDataAs2dArray() throws Exception {
		return TestNGUtils.convertListOfListsTo2dArray(getRegisterWithActivationKeyContainingPoolWithQuantity_TestDataAsListOfLists());
	}
	protected List<List<Object>> getRegisterWithActivationKeyContainingPoolWithQuantity_TestDataAsListOfLists() throws Exception {
		List<List<Object>> ll = new ArrayList<List<Object>>();
		for (List<Object> l : getAllJSONPoolsDataAsListOfLists()) {
			JSONObject jsonPool = (JSONObject)l.get(0);
			int quantity = jsonPool.getInt("quantity");
			
			// does this pool provide an unlimited quantity of entitlements?
			if (quantity==-1) {
				log.info("Assuming that pool '"+jsonPool.getString("id")+"' provides an unlimited quantity of entitlements.");
				quantity = jsonPool.getInt("consumed") + 10;	// assume any quantity greater than what is currently consumed
			}
			
			// choose a random valid pool quantity (1<=quantity<=totalPoolQuantity)
			int quantityAvail = quantity-jsonPool.getInt("consumed");
			int addQuantity = Math.max(1,randomGenerator.nextInt(quantityAvail+1));	// avoid a addQuantity < 1 see https://bugzilla.redhat.com/show_bug.cgi?id=729125
			
			// is this pool known to be blocked by any activation key bugs?
			BlockedByBzBug blockedByBugs = null;
			List<String> bugids = new ArrayList<String>();
			if (ConsumerType.person.toString().equals(CandlepinTasks.getPoolProductAttributeValue(sm_clientUsername, sm_clientPassword, sm_serverUrl, jsonPool.getString("id"), "requires_consumer_type")))
				bugids.add("732538");
			if (!CandlepinTasks.isPoolProductMultiEntitlement(sm_clientUsername, sm_clientPassword, sm_serverUrl, jsonPool.getString("id")) && addQuantity>1)
				bugids.add("729070");			
			if (!bugids.isEmpty()) blockedByBugs = new BlockedByBzBug(bugids.toArray(new String[]{}));
			
			String keyName = String.format("ActivationKey%s_ForPool%s_WithQuantity%s", System.currentTimeMillis(), jsonPool.getString("id"), addQuantity);

			// Object blockedByBug, String keyName, JSONObject jsonPool
			ll.add(Arrays.asList(new Object[] {blockedByBugs,	keyName, jsonPool,	addQuantity}));
			
			// minimize the number of dataProvided rows (useful during automated testcase development)
			if (Boolean.valueOf(getProperty("sm.debug.dataProviders.minimize","false"))) break;

		}
		return ll;
	}
	
	@DataProvider(name="getActivationKeyCreationWithBadNameData")
	public Object[][] getActivationKeyCreationWithBadNameDataAs2dArray() throws Exception {
		return TestNGUtils.convertListOfListsTo2dArray(getActivationKeyCreationWithBadNameDataAsListOfLists());
	}
	protected List<List<Object>> getActivationKeyCreationWithBadNameDataAsListOfLists() throws Exception {
		List<List<Object>> ll = new ArrayList<List<Object>>();
		String name;
		
		name = ".period.";				ll.add(Arrays.asList(new Object[] {null,	name}));
		
		name = "[openingBracket[";		ll.add(Arrays.asList(new Object[] {new BlockedByBzBug("728624"),	name}));
		name = "]closingBracket]";		ll.add(Arrays.asList(new Object[] {new BlockedByBzBug("728624"),	name}));
		name = "{openingBrace{";		ll.add(Arrays.asList(new Object[] {null,	name}));
		name = "}closingBrace}";		ll.add(Arrays.asList(new Object[] {null,	name}));
		name = "(openingParenthesis(";	ll.add(Arrays.asList(new Object[] {null,	name}));
		name = ")closingParenthesis)";	ll.add(Arrays.asList(new Object[] {null,	name}));
		name = "?questionMark?";		ll.add(Arrays.asList(new Object[] {null,	name}));
		name = "@at@";					ll.add(Arrays.asList(new Object[] {null,	name}));
		name = "!exclamationPoint!";	ll.add(Arrays.asList(new Object[] {null,	name}));
		name = "`backTick`";			ll.add(Arrays.asList(new Object[] {new BlockedByBzBug("728624"),	name}));
		name = "'singleQuote'";			ll.add(Arrays.asList(new Object[] {null,	name}));
		name = "pound#sign";			ll.add(Arrays.asList(new Object[] {null,	name}));

		name = "\"doubleQuotes\"";		ll.add(Arrays.asList(new Object[] {null,	name}));
		name = "$dollarSign$";			ll.add(Arrays.asList(new Object[] {null,	name}));
		name = "^caret^";				ll.add(Arrays.asList(new Object[] {new BlockedByBzBug("728624"),	name}));
		name = "<lessThan<";			ll.add(Arrays.asList(new Object[] {null,	name}));
		name = ">greaterThan>";			ll.add(Arrays.asList(new Object[] {null,	name}));
		name = "|verticalBar|";			ll.add(Arrays.asList(new Object[] {null,	name}));
		name = "+plus+";				ll.add(Arrays.asList(new Object[] {null,	name}));
		name = "%percent%";				ll.add(Arrays.asList(new Object[] {null,	name}));
		name = "/slash/";				ll.add(Arrays.asList(new Object[] {null,	name}));
		name = ";semicolon;";			ll.add(Arrays.asList(new Object[] {null,	name}));
		name = ":colon:";				ll.add(Arrays.asList(new Object[] {null,	name}));
		name = ",comma,";				ll.add(Arrays.asList(new Object[] {null,	name}));
		name = "\\backslash\\";			ll.add(Arrays.asList(new Object[] {new BlockedByBzBug("728624"),	name}));
		name = "*asterisk*";			ll.add(Arrays.asList(new Object[] {null,	name}));
		name = "=equal=";				ll.add(Arrays.asList(new Object[] {null,	name}));
		name = "~tilde~";				ll.add(Arrays.asList(new Object[] {null,	name}));

		name = "s p a c e s";			ll.add(Arrays.asList(new Object[] {null,	name}));

		name = "#poundSign";			ll.add(Arrays.asList(new Object[] {null,	name}));
		
		return ll;
	}
	
	@DataProvider(name="getRegisterWithUnknownActivationKeyData")
	public Object[][] getRegisterWithUnknownActivationKeyDataAs2dArray() throws Exception {
		return TestNGUtils.convertListOfListsTo2dArray(getRegisterWithUnknownActivationKeyDataAsListOfLists());
	}
	protected List<List<Object>> getRegisterWithUnknownActivationKeyDataAsListOfLists() throws Exception {
		List<List<Object>> ll = new ArrayList<List<Object>>();
		String name;
		
		name = " ";						ll.add(Arrays.asList(new Object[] {null,	name, sm_clientOrg}));
		name = "unknown";				ll.add(Arrays.asList(new Object[] {null,	name, sm_clientOrg}));
		name = "ak_na_testování";		ll.add(Arrays.asList(new Object[] {new BlockedByBzBug(new String[]{"803773","906000","919584"}),	name, sm_clientOrg}));
		name = "使 용 Ф ব্ ಬ ഉ ବ୍ டு వా Й Ó";	ll.add(Arrays.asList(new Object[] {new BlockedByBzBug(new String[]{"803773","906000","919584"}),	name, sm_clientOrg}));
		
		return ll;
	}
	
}

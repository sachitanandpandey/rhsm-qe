package com.redhat.qe.sm.cli.tests;

import java.util.List;

import org.testng.annotations.Test;

import com.redhat.qe.auto.tcms.ImplementsTCMS;
import com.redhat.qe.auto.testng.Assert;
import com.redhat.qe.auto.testng.LogMessageUtil;
import com.redhat.qe.sm.base.SubscriptionManagerCLITestScript;
import com.redhat.qe.sm.data.ConsumerCert;
import com.redhat.qe.sm.data.ProductSubscription;
import com.redhat.qe.sm.data.SubscriptionPool;
import com.redhat.qe.tools.RemoteFileTasks;

/**
 * @author jsefler
 *
 */
@Test(groups={"reregister"})
public class ReregisterTests extends SubscriptionManagerCLITestScript {

// FIXME DELETE THIS FILE   THIS MODULE IS DEAD	
	
	/**
	 * https://tcms.engineering.redhat.com/case/56327/?from_plan=2476
		Actions:

			* register a client to candlepin
			* subscribe to a pool
			* list consumed
			* reregister

	    Expected Results:

	 		* check the identity cert has not changed
	        * check the consumed entitlements have not changed
	 */
	@Test(	description="subscription-manager-cli: reregister basic registration",
			groups={"blockedByBug-636843"},
			enabled=true)
	@ImplementsTCMS(id="56327")
	public void ReregisterBasicRegistration_Test() {
		
		// start fresh by unregistering and registering
		clienttasks.unregister();
		String consumerIdBefore = clienttasks.getCurrentConsumerId(clienttasks.register(clientusername,clientpassword,null,null,null,null, null));
		
		// take note of your identity cert before reregister
		ConsumerCert consumerCertBefore = clienttasks.getCurrentConsumerCert();
		
		// subscribe to a random pool
		List<SubscriptionPool> pools = clienttasks.getCurrentlyAvailableSubscriptionPools();
		SubscriptionPool pool = pools.get(randomGenerator.nextInt(pools.size())); // randomly pick a pool
		clienttasks.subscribeToSubscriptionPoolUsingPoolId(pool);
		
		// get a list of the consumed products
		List<ProductSubscription> consumedProductSubscriptionsBefore = clienttasks.getCurrentlyConsumedProductSubscriptions();
		
		// reregister
		//clienttasks.reregister(null,null,null);
		clienttasks.reregisterToExistingConsumer(clientusername,clientpassword,consumerIdBefore);
		
		// assert that the identity cert has not changed
		ConsumerCert consumerCertAfter = clienttasks.getCurrentConsumerCert();
		Assert.assertEquals(consumerCertBefore, consumerCertAfter, "The consumer identity cert has not changed after reregistering with consumerid.");
		
		// assert that the user is still consuming the same products
		List<ProductSubscription> consumedProductSubscriptionsAfter = clienttasks.getCurrentlyConsumedProductSubscriptions();
		Assert.assertTrue(
				consumedProductSubscriptionsAfter.containsAll(consumedProductSubscriptionsBefore) &&
				consumedProductSubscriptionsBefore.size()==consumedProductSubscriptionsAfter.size(),
				"The list of consumed products after reregistering is identical.");
	}
	
	
	/**
	 * https://tcms.engineering.redhat.com/case/56328/?from_plan=2476
	 * 
		Actions:

	 		* register a client to candlepin (take note of the uuid returned)
	 		* take note of your identity cert info using openssl x509
	 		* subscribe to a pool
	 		* list consumed
	 		* ls /etc/pki/entitlement/products
	 		* Now.. mess up your identity..  mv /etc/pki/consumer/cert.pem /bak
	 		* run the "reregister" command w/ username and passwd AND w/consumerid=<uuid>

		Expected Results:

	 		* after running reregister you should have a new identity cert
	 		* after registering you should still the same products consumed (list consumed)
	 		* the entitlement serials should be the same as before the registration
	 */
	@Test(	description="subscription-manager-cli: bad identity cert",
			groups={/*"blockedByBug-624106"*/},
			enabled=true)
	@ImplementsTCMS(id="56328")
	public void ReregisterWithBadIdentityCert_Test() {
		
		// start fresh by unregistering and registering
		clienttasks.unregister();
		clienttasks.register(clientusername,clientpassword,null,null,null,null, null);
		
		// take note of your identity cert
		ConsumerCert consumerCertBefore = clienttasks.getCurrentConsumerCert();
		
		// subscribe to a random pool
		List<SubscriptionPool> pools = clienttasks.getCurrentlyAvailableSubscriptionPools();
		SubscriptionPool pool = pools.get(randomGenerator.nextInt(pools.size())); // randomly pick a pool
		clienttasks.subscribeToSubscriptionPoolUsingPoolId(pool);
		
		// get a list of the consumed products
		List<ProductSubscription> consumedProductSubscriptionsBefore = clienttasks.getCurrentlyConsumedProductSubscriptions();
		
		// Now.. mess up your identity..  by borking its content
		log.info("Messing up the identity cert by borking its content...");
		RemoteFileTasks.runCommandAndAssert(client, "openssl x509 -noout -text -in "+clienttasks.consumerCertFile+" > /tmp/stdout; mv /tmp/stdout -f "+clienttasks.consumerCertFile, 0);
		
		// reregister w/ username, password, and consumerid
		//clienttasks.reregister(client1username,client1password,consumerCertBefore.consumerid);
		log.warning("The subscription-manager-cli reregister module has been eliminated and replaced by register --consumerid (b3c728183c7259841100eeacb7754c727dc523cd)...");
		clienttasks.register(clientusername,clientpassword,null,null,consumerCertBefore.consumerid,null, Boolean.TRUE);
		
		// assert that the identity cert has not changed
		ConsumerCert consumerCertAfter = clienttasks.getCurrentConsumerCert();
		Assert.assertEquals(consumerCertBefore, consumerCertAfter, "The consumer identity cert has not changed after reregistering with consumerid.");
	
		// assert that the user is still consuming the same products
		List<ProductSubscription> consumedProductSubscriptionsAfter = clienttasks.getCurrentlyConsumedProductSubscriptions();
		Assert.assertTrue(
				consumedProductSubscriptionsAfter.containsAll(consumedProductSubscriptionsBefore) &&
				consumedProductSubscriptionsBefore.size()==consumedProductSubscriptionsAfter.size(),
				"The list of consumed products after reregistering is identical.");
	}
	
// TODO Automation Candidates for Reregister tests: 
//		https://bugzilla.redhat.com/show_bug.cgi?id=627685
//		https://bugzilla.redhat.com/show_bug.cgi?id=627681
//		https://bugzilla.redhat.com/show_bug.cgi?id=627665

	
	
	
	// Configuration methods ***********************************************************************
	

	
	// Protected methods ***********************************************************************


	
	// Data Providers ***********************************************************************


}

package rhsm.cli.tests;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.json.JSONException;
import org.testng.SkipException;
import org.testng.annotations.Test;

import rhsm.base.SubscriptionManagerCLITestScript;
import rhsm.cli.tasks.CandlepinTasks;
import rhsm.data.EntitlementCert;
import rhsm.data.ProductCert;
import rhsm.data.ProductNamespace;
import rhsm.data.ProductSubscription;
import rhsm.data.SubscriptionPool;

import com.redhat.qe.Assert;
import com.redhat.qe.tools.RemoteFileTasks;

/**
 * @author jsefler
 * 
 * References
 *  Design Doc: https://mojo.redhat.com/docs/DOC-186259
 *  Dev Test Doc: https://mojo.redhat.com/docs/DOC-21827
 *  TCMS plan link https://tcms.engineering.redhat.com/plan/11066/flex-branding-test-cases
 *  
 * Brandbot is owned by the initscripts package and developed by Bill Nottingham
 * [root@jsefler-7 ~]# rpm -ql initscripts | grep brandbot
 * /usr/lib/systemd/system/brandbot.path
 * /usr/lib/systemd/system/brandbot.service
 * /usr/lib/systemd/system/multi-user.target.wants/brandbot.path
 * /usr/sbin/brandbot
 * 
 * Be wary of this error when trying to update the branding file too fast
 * [root@jsefler-7 ~]# tail -f /var/log/messages | grep brandbot
 * Dec  4 14:23:59 jsefler-7 systemd: brandbot.service start request repeated too quickly, refusing to start.
 * Dec  4 14:23:59 jsefler-7 systemd: Unit brandbot.service entered failed state.
 */
@Test(groups = { "BrandingTests" })
public class BrandingTests extends SubscriptionManagerCLITestScript {
	
	@Test(	description="assert that brandbot service is running",
			groups={"AcceptanceTests"},
			enabled=false)	// TODO not sure how this works... the status of this service is inactive, yet it appears to be automatically started/stopped as needed NEEDINFO from notting
	public void BrandbotServiceShouldBeRunning_Test() {
		RemoteFileTasks.runCommandAndAssert(client, "systemctl is-active brandbot.service", Integer.valueOf(0), "^active$", null);
	}
	
	
	@Test(	description="incrementally attach all available subscriptions and verify tests for Flexible Branding",
			groups={"AttachSubscriptionsForFlexibleBranding_Test","AcceptanceTests"},
			priority=100,
			enabled=true)
	public void AttachSubscriptionsForFlexibleBranding_Test() throws JSONException, Exception {
		// register
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null,(List<String>)null, null, null, null, true, false, null, null, null);
		
		// we will start out by removing the current brand name.
		sleep(5000); // before echo to avoid tail -f /var/log/messages | grep brandbot...   systemd: brandbot.service start request repeated too quickly, refusing to start.
		client.runCommandAndWait("rm -f "+brandingFile);
		 
		// loop through all the available subscription pools searching for FlexibleBranded subscriptions
		boolean flexibleBrandedSubscriptionsFound = false;
		// too time consuming for (SubscriptionPool pool : clienttasks.getCurrentlyAvailableSubscriptionPools()) {
		for (SubscriptionPool pool : getRandomSubsetOfList(clienttasks.getCurrentlyAvailableSubscriptionPools(),10)) {
			if (CandlepinTasks.isPoolAModifier(sm_clientUsername, sm_clientPassword, pool.poolId, sm_serverUrl)) continue; // skip modifier pools
			String brandNameBeforeSubscribing = getCurrentBrandName();
			String brandNameStatBeforeSubscribing = getCurrentBrandNameFileStat();
			String prettyNameBeforeSubscribing = getCurrentPrettyName();
			log.info("Currently, the flexible brand name prior to subscribing to pool '"+pool.subscriptionName+"' is '"+brandNameBeforeSubscribing+"'.");
			clienttasks.subscribe(null, null, pool.poolId, null, null, null, null, null, null, null, null);
			if (verifyCurrentEntitlementsForFlexibleBrandingAfterEvent(brandNameBeforeSubscribing,brandNameStatBeforeSubscribing,prettyNameBeforeSubscribing,"subscribing to pool '"+pool.subscriptionName+"'")) flexibleBrandedSubscriptionsFound=true;
		}
		
		// throw SkipException when no flexible branding was tested
		if (!flexibleBrandedSubscriptionsFound) throw new SkipException("No flexible branded subscriptions were found among the available subscriptions for testing.");
	}
	
	
	@Test(	description="incrementally remove attached subscriptions and verify tests for Flexible Branding",
			//depend on priority instead of dependsOnMethods={"AttachSubscriptionsForFlexibleBranding_Test"},
			priority=101,
			groups={"AcceptanceTests"},
			enabled=true)
	public void RemoveSubscriptionsForFlexibleBranding_Test() {
		
		// loop through all the attached subscriptions and remove them while running tests for branding
		for (ProductSubscription productSubscription : clienttasks.getCurrentlyConsumedProductSubscriptions()) {
			String brandNameBeforeUnsubscribing = getCurrentBrandName();
			String brandNameStatBeforeUnsubscribing = getCurrentBrandNameFileStat();
			String prettyNameBeforeUnsubscribing = getCurrentPrettyName();
			log.info("Currently, the flexible brand name prior to unsubscribing from subscription '"+productSubscription.productName+"' is '"+brandNameBeforeUnsubscribing+"'.");
			clienttasks.unsubscribe(null,productSubscription.serialNumber, null, null, null);
			verifyCurrentEntitlementsForFlexibleBrandingAfterEvent(brandNameBeforeUnsubscribing,brandNameStatBeforeUnsubscribing,prettyNameBeforeUnsubscribing,"unsubscribing from '"+productSubscription.productName+"'");
		}
	}
	
	
	@Test(	description="autosubscribe and verify tests for Flexible Branding",
			dependsOnGroups={},
			priority=200,
			groups={"AcceptanceTests","AutoSubscribeForFlexibleBranding_Test"},
			enabled=true)
	public void AutoSubscribeForFlexibleBranding_Test() {
		// we will start out by unregistering and removing the current brand name.
		clienttasks.unregister(null, null, null);
		client.runCommandAndWait("rm -f "+brandingFile);
		
		String brandNameBeforeRegisteringWithAutosubscribe = getCurrentBrandName();
		String brandNameStatBeforeRegisteringWithAutosubscribe = getCurrentBrandNameFileStat();
		String prettyNameBeforeRegisteringWithAutosubscribe = getCurrentPrettyName();
		log.info("Currently, the flexible brand name prior to registering with autosubscribe is '"+brandNameBeforeRegisteringWithAutosubscribe+"'.");

		// register with autosubscribe
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, true, null, null,(List<String>)null, null, null, null, null, null, null, null, null);
		
		// tests for branding
		if (!verifyCurrentEntitlementsForFlexibleBrandingAfterEvent(brandNameBeforeRegisteringWithAutosubscribe,brandNameStatBeforeRegisteringWithAutosubscribe,prettyNameBeforeRegisteringWithAutosubscribe,"registering with autosubscribe")) {
			throw new SkipException("No flexible branded subscriptions were found after registering with autosubscribe.");
		}
	}
	
	
	@Test(	description="run an rhsmcertd event and verify tests for Flexible Branding",
			//depend on priority instead of dependsOnMethods={"AutoSubscribeForFlexibleBranding_Test"},
			priority=201,
			groups={"AcceptanceTests","blockedByBug-1038664"},
			enabled=true)
	public void RhsmcertdCheckForFlexibleBranding_Test() {
		// we will start out by removing the current brand name and the current entitlements.
		clienttasks.removeAllCerts(false, true, false);
		client.runCommandAndWait("rm -f "+brandingFile);

		String brandNameBeforeRunningRhsmcertdCheck = getCurrentBrandName();
		String brandNameStatBeforeRunningRhsmcertdCheck = getCurrentBrandNameFileStat();
		String prettyNameBeforeRunningRhsmcertdCheck = getCurrentPrettyName();
		log.info("Currently, the flexible brand name prior to an rhsmcertd check is '"+brandNameBeforeRunningRhsmcertdCheck+"'.");
		
		// run rhsmcertd
		clienttasks.run_rhsmcertd_worker(false);
		
		// tests for branding
		if (!verifyCurrentEntitlementsForFlexibleBrandingAfterEvent(brandNameBeforeRunningRhsmcertdCheck,brandNameStatBeforeRunningRhsmcertdCheck,prettyNameBeforeRunningRhsmcertdCheck,"running the rhsmcertd-worker check")) {
			Assert.fail("Expected the rhsmcertd-worker to restore the consumer's entitlements from the prior AutoSubscribeForFlexibleBranding_Test.");
		}
	}
	
	
	@Test(	description="run an rhsmcertd healing event and verify tests for Flexible Branding",
			//depend on priority instead of dependsOnMethods={"AutoSubscribeForFlexibleBranding_Test"},
			priority=202,
			groups={"AcceptanceTests"},
			enabled=true)
	public void RhsmcertdHealingUpdateForFlexibleBranding_Test() {
		// we will start out by removing the current brand name and removing the current entitlements.
		clienttasks.unsubscribe(true, (BigInteger)null, null, null, null);
		client.runCommandAndWait("rm -f "+brandingFile);

		String brandNameBeforeRunningRhsmcertdHealCheck = getCurrentBrandName();
		String brandNameStatBeforeRunningRhsmcertdHealCheck = getCurrentBrandNameFileStat();
		String prettyNameBeforeRunningRhsmcertdHealCheck = getCurrentPrettyName();
		log.info("Currently, the flexible brand name prior to an rhsmcertd heal check is '"+brandNameBeforeRunningRhsmcertdHealCheck+"'.");
		
		// run rhsmcertd check for healing
		clienttasks.run_rhsmcertd_worker(true);
		
		// tests for branding
		if (!verifyCurrentEntitlementsForFlexibleBrandingAfterEvent(brandNameBeforeRunningRhsmcertdHealCheck,brandNameStatBeforeRunningRhsmcertdHealCheck,prettyNameBeforeRunningRhsmcertdHealCheck,"running the rhsmcertd-worker healing check")) {
			throw new SkipException("No flexible branded subscriptions were found after running the rhsmcertd-worker check for healing.");
		}
	}
	
	
	@Test(	description="assert that brandbot only reads the first line of the branding file",
			groups={"blockedByBug-1031490"},
			enabled=true)
	public void BrandbotShouldHandleMultiLineBrandingFile_Test() {
		String actualBrandName,actualPrettyName;
		
		log.info("Testing a single line branding file...");
		sleep(5000); // before echo to avoid tail -f /var/log/messages | grep brandbot...   systemd: brandbot.service start request repeated too quickly, refusing to start.
		RemoteFileTasks.runCommandAndAssert(client, "echo 'RHEL/Branded OS (line 1)' > "+brandingFile, 0);
		actualBrandName = getCurrentBrandName();
		Assert.assertEquals(actualBrandName, "RHEL/Branded OS (line 1)", "The brand name contained within the first line of the brand file '"+brandingFile+"'.");
		actualPrettyName = getCurrentPrettyName();
		Assert.assertEquals(actualPrettyName, "RHEL/Branded OS (line 1)", "The PRETTY_NAME contained within the os-release file '"+osReleaseFile+"'.");

		log.info("Testing a multi-line branding file...");
		sleep(5000); // before echo to avoid tail -f /var/log/messages | grep brandbot...   systemd: brandbot.service start request repeated too quickly, refusing to start.
		RemoteFileTasks.runCommandAndAssert(client, "echo 'RHEL/SubBranded OS (line 2)' >> "+brandingFile, 0);
		actualBrandName = getCurrentBrandName();
		Assert.assertEquals(actualBrandName, "RHEL/Branded OS (line 1)", "The brand name contained within the first line of the brand file '"+brandingFile+"'.");
		actualPrettyName = getCurrentPrettyName();
		Assert.assertEquals(actualPrettyName, "RHEL/Branded OS (line 1)", "The PRETTY_NAME contained within the os-release file '"+osReleaseFile+"' (Should not contain any new line characters).");
	}
	
	
	@Test(	description="assert that brandbot trims white space from the first line of the branding file",
			groups={},
			enabled=true)
	public void BrandbotShouldTrimWhiteSpaceFromBrandingFile_Test() {
		String actualBrandName,actualPrettyName;
		
		log.info("Testing a single line branding file with leading and trailing white space...");
		sleep(5000); // before echo to avoid tail -f /var/log/messages | grep brandbot...   systemd: brandbot.service start request repeated too quickly, refusing to start.
		RemoteFileTasks.runCommandAndAssert(client, "echo '  RHEL-x.y Branded OS  ' > "+brandingFile, 0);
		actualBrandName = getCurrentBrandName();
		Assert.assertEquals(actualBrandName, "  RHEL-x.y Branded OS  ", "The brand name contained within the first line of the brand file '"+brandingFile+"' (should contain leading and trailing white space).");
		actualPrettyName = getCurrentPrettyName();
		Assert.assertEquals(actualPrettyName, "RHEL-x.y Branded OS", "The PRETTY_NAME contained within the os-release file '"+osReleaseFile+"' (should NOT contain leading nor trailing white space).");
	}
	
	
	@Test(	description="assert that brandbot does nothing when the brand file is removed",	//  Brandbot SHOULD handle /var/lib/rhsm/branded_name not existing.
			groups={},
			enabled=true)
	public void BrandbotShouldHandleNonExistantBrandingFile_Test() {
		//	> If /var/lib/rhsm/branded_name is removed, what should PRETTY_NAME be? In
		//	> initscripts-9.49.11-1.el7.x86_64 it retains its prior value before the
		//	> branded_name was removed.  I don't know if that is correct.
		//
		//	(The answer is likely 'whatever PM wants to happen'. But aside from that...)
		//	Bill
				
		String actualBrandName,actualPrettyName;
		
		log.info("Testing a single line branding to make sure PRETTY_NAME exists before testing the non-existant branding file...");
		sleep(5000); // before echo to avoid tail -f /var/log/messages | grep brandbot...   systemd: brandbot.service start request repeated too quickly, refusing to start.
		RemoteFileTasks.runCommandAndAssert(client, "echo 'RHEL Branded OS' > "+brandingFile, 0);
		actualBrandName = getCurrentBrandName();
		Assert.assertEquals(actualBrandName, "RHEL Branded OS", "The brand name contained within the first line of the brand file '"+brandingFile+"'.");
		actualPrettyName = getCurrentPrettyName();
		Assert.assertEquals(actualPrettyName, "RHEL Branded OS", "The PRETTY_NAME contained within the os-release file '"+osReleaseFile+"'.");
		
		log.info("Testing a non-existant branding file...");
		sleep(5000); // before echo to avoid tail -f /var/log/messages | grep brandbot...   systemd: brandbot.service start request repeated too quickly, refusing to start.
		RemoteFileTasks.runCommandAndAssert(client, "rm -f "+brandingFile, 0);
		Assert.assertTrue(!RemoteFileTasks.testExists(client, brandingFile), "The brand file '"+brandingFile+"' should not exist for this test.");
		actualBrandName = getCurrentBrandName();
		Assert.assertNull(actualBrandName, "The brand name is not defined when the brand file '"+brandingFile+"' does not exist.");
		Assert.assertEquals(getCurrentPrettyName(),actualPrettyName, "The PRETTY_NAME contained within the os-release file '"+osReleaseFile+"' (should remain unchanged when the brand file is removed).");
	}
	
	
	@Test(	description="assert that brandbot removes PRETTY_NAME when the first line of the branding file is empty",	//  Brandbot SHOULD handle /var/lib/rhsm/branded_name being empty.
			groups={"blockedByBug-1031490"},
			enabled=true)
	public void BrandbotShouldHandleEmptyBrandingFile_Test() {
		//	> If /var/lib/rhsm/branded_name is an empty file, what should PRETTY_NAME
		//	> be?  In initscripts-9.49.11-1.el7.x86_64 PRETTY_NAME is removed from
		//	> /etc/os-release.  I think it should be PRETTY_NAME = "".
		//	>
		//	> What is the consequence of PRETTY_NAME missing from /etc/os-release?
		//
		//	Logical behaviors would be:
		//
		//	1) removing PRETTY_NAME
		//	2) reverting it to the unbranded name
		//
		//	Since we don't (AFAIK) have the data to do #2, #1 makes sense to me.
		//
		//	Bill
		
		String actualBrandName,actualPrettyName;
		
		log.info("Testing an empty branding file...");
		sleep(5000); // before echo to avoid tail -f /var/log/messages | grep brandbot...   systemd: brandbot.service start request repeated too quickly, refusing to start.
		RemoteFileTasks.runCommandAndAssert(client, "rm -f "+brandingFile+" && touch "+brandingFile, 0);
		actualBrandName = getCurrentBrandName();
		Assert.assertEquals(actualBrandName, "", "The brand name contained within the first line of the brand file '"+brandingFile+"' (should be an empty string).");
		actualPrettyName = getCurrentPrettyName();
		Assert.assertNull(actualPrettyName, "The PRETTY_NAME contained within the os-release file '"+osReleaseFile+"' (should NOT be present when the brand file is empty).");
		
		log.info("Testing a non-empty branding file, but the first line is empty...");
		sleep(5000); // before echo to avoid tail -f /var/log/messages | grep brandbot...   systemd: brandbot.service start request repeated too quickly, refusing to start.
		RemoteFileTasks.runCommandAndAssert(client, "echo '' >> "+brandingFile, 0);
		RemoteFileTasks.runCommandAndAssert(client, "echo 'RHEL SubBranded OS (second line)' >> "+brandingFile, 0);
		actualBrandName = getCurrentBrandName();
		Assert.assertEquals(actualBrandName, "", "The brand name contained within the first line of the brand file '"+brandingFile+"' (should be an empty string).");
		actualPrettyName = getCurrentPrettyName();
		Assert.assertNull(actualPrettyName, "The PRETTY_NAME contained within the os-release file '"+osReleaseFile+"' (should NOT be present when the first line of the brand file is empty).");
	}
	
	
	
	
	// Candidates for an automated Test:
	
	
	
	
	
	// Configuration Methods ***********************************************************************
	
	
	// Protected Methods ***********************************************************************
	protected final String brandingFile = "/var/lib/rhsm/branded_name";
	protected final String osReleaseFile = "/etc/os-release";
	
	/**
	 * @return the eligible brand names based on the currently attached entitlement certs and the currently installed product certs
	 */
	Set<String> getEligibleBrandNamesFromCurrentEntitlements() {
		
		// Rules:
		//  - eligible brand names come from the currently entitled productNamespaces and are identified by a brandType = "OS"
		//  - the corresponding productId must be among the currently installed product certs to be eligible
		
		List<ProductCert> currentProductCerts = clienttasks.getCurrentProductCerts();
		List<String> eligibleBrandNamesList = new ArrayList<String>();
		Set<String> eligibleBrandNamesSet = new HashSet<String>();
		for (EntitlementCert entitlementCert : clienttasks.getCurrentEntitlementCerts()) {
			for (ProductNamespace productNamespace : entitlementCert.productNamespaces) {
				if (ProductCert.findFirstInstanceWithMatchingFieldFromList("productId", productNamespace.id, currentProductCerts) != null) {
					if (productNamespace.brandType != null) {
						if (productNamespace.brandType.equals("OS")) {
							eligibleBrandNamesList.add(productNamespace.name);
							eligibleBrandNamesSet.add(productNamespace.name);
						}
					}
				}
			}
		}
		if (eligibleBrandNamesList.size() > eligibleBrandNamesSet.size()) {
			log.warning("Currently there are multiple entitled OS brand products by the same name "+eligibleBrandNamesList+".  This can happen when multiple OS subscriptions have been stacked.");
		}
		return eligibleBrandNamesSet;
		

	}
	
	/**
	 * @return the first line contained within the branding file; null if the branding file does not exist
	 */
	String getCurrentBrandName() {
		if (!RemoteFileTasks.testExists(client, brandingFile)) return null;
		
		// Rules:
		//  - the current brand name should only be the first line of the brand file (Bug 1031490)
		
		//String brandName = client.runCommandAndWait("head -1 "+brandNameFile).getStdout().trim();
		String brandName = client.runCommandAndWait("cat "+brandingFile).getStdout();
		brandName = brandName.replaceAll("\n.*", "");	// strip off all secondary lines
		return brandName;
	}
	
	/**
	 * @return a string representing the creation/modification/change time of the branding file
	 */
	String getCurrentBrandNameFileStat() {
		return client.runCommandAndWait("stat -c 'File= %n Created= %w Modified= %y Changed= %z' "+brandingFile).getStdout().trim();
	}
	
	/**
	 * @return the value of PRETTY_NAME from /etc/os-release which is under the control of /usr/lib/systemd/system/brandbot.service (owned by package initscripts); null if not found
	 */
	String getCurrentPrettyName() {
		//	[root@jsefler-7 ~]# cat /etc/os-release
		//	NAME="Red Hat Enterprise Linux Server"
		//	VERSION="7.0 (Maipo)"
		//	ID="rhel"
		//	VERSION_ID="7.0"
		//	PRETTY_NAME="Red Hat Enterprise Linux Server 7.0 (Maipo)"
		//	ANSI_COLOR="0;31"
		//	CPE_NAME="cpe:/o:redhat:enterprise_linux:7.0:beta:server"
		//
		//	REDHAT_BUGZILLA_PRODUCT="Red Hat Enterprise Linux 7"
		//	REDHAT_BUGZILLA_PRODUCT_VERSION=7.0
		//	REDHAT_SUPPORT_PRODUCT="Red Hat Enterprise Linux"
		//	REDHAT_SUPPORT_PRODUCT_VERSION=7.0
		
		String osReleaseContents = client.runCommandAndWait("cat "+osReleaseFile).getStdout().trim();
		String prettyNameRegex = "(PRETTY_NAME\\s*=\\s*(\\\"(.*?(\\n.*?)*)\\\"))|(PRETTY_NAME\\s*=\\s*\\w+)";
		List<String> prettyNameMatches = getSubstringMatches(osReleaseContents, prettyNameRegex);
		if (prettyNameMatches.isEmpty()) return null;
		if (prettyNameMatches.size()>1) Assert.fail("Encountered more than one PRETTY_NAME in "+osReleaseFile);	// this should not happen
		String prettyName = prettyNameMatches.get(0);
		prettyName = prettyName.replaceFirst("PRETTY_NAME\\s*=\\s*", "");	// strip off PRETTY_NAME =
		prettyName = prettyName.replaceAll("(^\"|\"$)", "");	// strip off surrounding double quotes
		return prettyName;
	}
	

	/**
	 * This routine performs the expected assertions based on the current entitlements after an event has occurred.
	 * It will assert the contents of the branding file and the PRETTY_NAME in /etc/os-release. 
	 * An event like subscribe or unsubscribe should be called immediately before this verification method is called.
	 * @param brandNameBeforeEvent - value from getCurrentBrandName() before the event is called
	 * @param brandNameStatBeforeEvent - value from getCurrentBrandNameFileStat() before the event is called
	 * @param prettyNameBeforeEvent - value from getCurrentPrettyName() before the event is called
	 * @param afterEventDescription - a description of the event like "after subscribing to pool 'Awesome OS'."
	 * @return - whether or not a flexible branded subscription is currently entitled
	 */
	protected boolean verifyCurrentEntitlementsForFlexibleBrandingAfterEvent(String brandNameBeforeEvent, String brandNameStatBeforeEvent, String prettyNameBeforeEvent, String afterEventDescription) { 
		
		boolean flexibleBrandedSubscriptionsFound = false;
		Set<String> eligibleBrandNames = getEligibleBrandNamesFromCurrentEntitlements();
		
		// determine the expected brand name after subscribing
		// Rules:
		//  - eligible brand names come from the currently entitled productNamespaces
		//  - the corresponding productId must be among the currently installed product certs to be eligible
		//  - if more than one brand name is eligible, no update is made to the branding file
		//  - if only one brand name is eligible and it does NOT already equal the current brand name, then an update is made
		//  - if no brand name is eligible, no update is made
		//  - updates are sought out when entitlements are refreshed
		// 
		// Design Doc: https://mojo.redhat.com/docs/DOC-186259
		// Developer Test Notes: https://mojo.redhat.com/docs/DOC-21827
		String expectedBrandNameAfterSubscribing=null;
		if (eligibleBrandNames.size()>1) {
			log.warning("Currently there are multiple eligible brand names "+eligibleBrandNames+", therefore the actual brand name should remain unchanged.");
			expectedBrandNameAfterSubscribing = brandNameBeforeEvent;
			flexibleBrandedSubscriptionsFound=true;
		}
		if (eligibleBrandNames.isEmpty()) {
			log.warning("Currently there are no eligible brand names based on the attached entitlements, therefore the actual brand name should remain unchanged.");
			expectedBrandNameAfterSubscribing = brandNameBeforeEvent;
		}
		if (eligibleBrandNames.size()==1) {
			expectedBrandNameAfterSubscribing = (String) eligibleBrandNames.toArray()[0];
			log.info("Currently there is only one eligible brand name based on the attached entitlements, therefore the actual brand name should be '"+expectedBrandNameAfterSubscribing+"'.");
			flexibleBrandedSubscriptionsFound=true;
		}
	
		// verify the actualBrandNameAfterSubscribing = expectedBrandNameAfterSubscribing
		String actualBrandNameAfterSubscribing = getCurrentBrandName();
		Assert.assertEquals(actualBrandNameAfterSubscribing, expectedBrandNameAfterSubscribing, "The brand name contained within the first line of the brand file '"+brandingFile+"' after "+afterEventDescription);
		
		// verify that the brand file was NOT altered when the expectedBrandNameAfterSubscribing = brandNameBeforeSubscribing
		if (expectedBrandNameAfterSubscribing!=null && expectedBrandNameAfterSubscribing.equals(brandNameBeforeEvent)) {
			String brandNameStatAfterSubscribing = getCurrentBrandNameFileStat();
			Assert.assertEquals(brandNameStatAfterSubscribing, brandNameStatBeforeEvent, "After "+afterEventDescription+", if the brand name should remain unchanged, then the modification and change stats on the brand file '"+brandingFile+"' should remain unaltered.");
		}
		if (expectedBrandNameAfterSubscribing!=null && !expectedBrandNameAfterSubscribing.equals(brandNameBeforeEvent)) {
			String brandNameStatAfterSubscribing = getCurrentBrandNameFileStat();
			Assert.assertTrue(!brandNameStatAfterSubscribing.equals(brandNameStatBeforeEvent), "After "+afterEventDescription+", if the brand name should change, then the modification/change stats on the brand file '"+brandingFile+"' should obviously change too.");
		}
		
		// verify that /usr/sbin/brandbot has updated PRETTY_NAME in /etc/os-release
		String actualPrettyNameAfterSubscribing = getCurrentPrettyName();
		if (expectedBrandNameAfterSubscribing!=null) {
			if (expectedBrandNameAfterSubscribing.isEmpty()) {
				// see BrandbotShouldHandleEmptyBrandingFile_Test
				Assert.assertNull(actualPrettyNameAfterSubscribing, "The PRETTY_NAME in '"+osReleaseFile+"' governed by /usr/lib/systemd/system/brandbot.service should be removed when the first line of the brand file '"+brandingFile+"' is empty after "+afterEventDescription+".");
			} else {
				Assert.assertEquals(actualPrettyNameAfterSubscribing, expectedBrandNameAfterSubscribing, "The PRETTY_NAME in '"+osReleaseFile+"' governed by /usr/lib/systemd/system/brandbot.service should match the first line of the brand file '"+brandingFile+"' after "+afterEventDescription+".");
			}
		} else {
			// see BrandbotShouldHandleNonExistantBrandingFile_Test
			Assert.assertEquals(actualPrettyNameAfterSubscribing,prettyNameBeforeEvent, "The PRETTY_NAME contained within the os-release file '"+osReleaseFile+"' should remain unchanged when the expected brand name is null after "+afterEventDescription+".");
		}
		
		return flexibleBrandedSubscriptionsFound;
	}
	
	
	// Data Providers ***********************************************************************
}


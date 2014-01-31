package rhsm.cli.tests;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.xmlrpc.XmlRpcException;
import org.testng.annotations.AfterGroups;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import rhsm.base.SubscriptionManagerCLITestScript;
import rhsm.data.SubscriptionPool;

import com.redhat.qe.Assert;
import com.redhat.qe.auto.bugzilla.BzChecker;
import com.redhat.qe.tools.RemoteFileTasks;
import com.redhat.qe.tools.SSHCommandResult;
import com.redhat.qe.tools.SSHCommandRunner;

/**
 * @author jsefler
 *
 *
 */
@Test(groups={"RhsmDebugTests"})
public class RhsmDebugTests extends SubscriptionManagerCLITestScript {

	
	// Test methods ***********************************************************************
	
	@Test(	description="attempt to call rhsm-debug system without being registered",
			groups={},
			enabled=false)	// already covered by a dataProvider row in GeneralTests.getNegativeFunctionalityDataAsListOfLists()
	//@ImplementsNitrateTest(caseId=)
	public void RhsmDebugSystemWithoutBeingRegistered_Test() {
		
		// make sure we are not registered
		clienttasks.unregister_(null, null, null);
		
		// attempt to run rhsm-debug system without being registered
		String rhsmDebugSystemCommand = clienttasks.rhsmDebugSystemCommand(null, null, null, null, null);
		SSHCommandResult result = client.runCommandAndWait(rhsmDebugSystemCommand);
		
		// assert results
		Assert.assertEquals(result.getExitCode(), new Integer(255), "The exit code from an attempt to run '"+rhsmDebugSystemCommand+"' without being registered.");
		Assert.assertEquals(result.getStdout().trim(), "This system is not yet registered. Try 'subscription-manager register --help' for more information.", "The stdout from an attempt to run '"+rhsmDebugSystemCommand+"' without being registered.");
		Assert.assertEquals(result.getStderr().trim(), "", "The stderr from an attempt to run '"+rhsmDebugSystemCommand+"' without being registered.");
	}
	
	
	@Test(	description="after registering and subscribing, call rhsm-debug system and verify the expected contents of the written debug file",
			groups={"AcceptanceTests","blockedByBug-1038206","blockedByBug-1040338"},
			enabled=true)
			//@ImplementsNitrateTest(caseId=)
	public void RhsmDebugSystem_Test() {
		
		// run the rhsmDebugSystemTest with no options
		verifyRhsmDebugSystemTestWithOptions(null,null);
	}
	
	
	@Test(	description="after registering and subscribing, call rhsm-debug system with --no-archive option and verify the results",
			groups={},
			enabled=true)
			//@ImplementsNitrateTest(caseId=)
	public void RhsmDebugSystemWithNoArchive_Test() {
		
		// run the rhsmDebugSystemTest with a valid destination
		verifyRhsmDebugSystemTestWithOptions(null,true);
	}
	
	
	@Test(	description="after registering and subscribing, call rhsm-debug system with --destination option and verify the results",
			groups={"blockedByBug-1040338"},
			enabled=true)
			//@ImplementsNitrateTest(caseId=)
	public void RhsmDebugSystemWithDestination_Test() {
		
		// create a valid destination directory
		SSHCommandResult result = client.runCommandAndWait("echo $HOME");
		String destination = result.getStdout().trim().replaceFirst("/$", "")+"/rhsmDebugDestination/";
		client.runCommandAndWait("rm -rf "+destination+" && mkdir -p "+destination);

		// run the rhsmDebugSystemTest with a valid destination
		verifyRhsmDebugSystemTestWithOptions(destination,null);
	}
	
	
	@Test(	description="after registering and subscribing, call rhsm-debug system with both --no-archive and --destination option and verify the results",
			groups={"blockedByBug-1040338"},
			enabled=true)
			//@ImplementsNitrateTest(caseId=)
	public void RhsmDebugSystemWithDestinationAndNoArchive_Test() {
		
		// create a valid destination directory
		SSHCommandResult result = client.runCommandAndWait("echo $HOME");
		String destination = result.getStdout().trim().replaceFirst("/$", "")+"/rhsmDebugDestination/";
		client.runCommandAndWait("rm -rf "+destination+" && mkdir -p "+destination);

		// run the rhsmDebugSystemTest with a valid destination
		verifyRhsmDebugSystemTestWithOptions(destination,true);
	}
	
	
	@Test(	description="after registering, call rhsm-debug system with a non-existent --destination option",
			groups={},
			enabled=true)
			//@ImplementsNitrateTest(caseId=)
	public void RhsmDebugSystemWithNonExistentDestination_Test() {
		
		// establish a non-existent destination directory
		String destination = "/tmp/rhsmDebugNonExistantDestination/";	// this directory should NOT exist
		Assert.assertTrue(!RemoteFileTasks.testExists(client, destination), "Destination directory '"+destination+"' should not exist.");
		
		// register
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, true, null, null, null, null);
		
		// run rhsm-debug system with a non-existent destination
		String rhsmDebugSystemCommand = clienttasks.rhsmDebugSystemCommand(destination, null, null, null, null);
		SSHCommandResult result = client.runCommandAndWait(rhsmDebugSystemCommand);
		
		// assert results
		Assert.assertEquals(result.getExitCode(), new Integer(0), "The exit code from an attempt to run '"+rhsmDebugSystemCommand+"'.");
		Assert.assertEquals(result.getStderr().trim(), "", "The stderr from an attempt to run '"+rhsmDebugSystemCommand+"'.");
		Assert.assertEquals(result.getStdout().trim(), "The destination directory for the archive must already exist.", "The stdout from an attempt to run '"+rhsmDebugSystemCommand+"'.");
	}
	
	
	@Test(	description="after registering, call rhsm-debug system with a bad (already existing as a file) --destination option",
			groups={},
			enabled=true)
			//@ImplementsNitrateTest(caseId=)
	public void RhsmDebugSystemWithBadDestination_Test() {
		
		// establish a bad destination
		String destination = "/tmp/foo";	// create this as a file
		client.runCommandAndWait("rm -rf "+destination+" && touch "+destination);
		Assert.assertTrue(RemoteFileTasks.testExists(client, destination), "Destination file '"+destination+"' should already exist.");
		
		// register
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, true, null, null, null, null);
		
		// run rhsm-debug system with a bad destination
		String rhsmDebugSystemCommand = clienttasks.rhsmDebugSystemCommand(destination, null, null, null, null);
		SSHCommandResult result = client.runCommandAndWait(rhsmDebugSystemCommand);
		
		//	[root@jsefler-7 ~]# rhsm-debug system --destination /tmp/foo
		//	[Errno 20] Not a directory: '/tmp/foo/rhsm-debug-system-20140121-342280.tar.gz'
		//	[root@jsefler-7 ~]# echo $?
		//	255
		String expectedStderr = "[Errno 20] Not a directory:";	// [Errno 20] Not a directory: '/tmp/foo/rhsm-debug-system-20140121-342280.tar.gz'

		// assert results
		Assert.assertEquals(result.getExitCode(), new Integer(255), "The exit code from an attempt to run '"+rhsmDebugSystemCommand+"'.");
		Assert.assertTrue(result.getStderr().trim().startsWith(expectedStderr), "The stderr from an attempt to run '"+rhsmDebugSystemCommand+"' should indicate '"+expectedStderr+"'.");
		Assert.assertEquals(result.getStdout().trim(), "", "The stdout from an attempt to run '"+rhsmDebugSystemCommand+"'.");
	}
	
	
	@Test(	description="exercise the rhsm-debug tool with non-default configurations for consumerCertDir entitlementCertDir and productCertDir",
			groups={"RhsmDebugSystemWithNonDefaultCertDirs1_Test","blockedByBug-1040546"},
			enabled=true)
			//@ImplementsNitrateTest(caseId=)
	public void RhsmDebugSystemWithNonDefaultCertDirs1_Test() {
		List<File> originalProductCertFiles = getRandomSubsetOfList(clienttasks.getCurrentProductCertFiles(),2);	// 2 is sufficient
		
		// remember the original configurations
		/* already taken care of in saveOriginalCertDirConfigurationsBeforeClass
		if (originalProductCertDir==null) originalProductCertDir = clienttasks.productCertDir;
		if (originalConsumerCertDir==null) originalConsumerCertDir = clienttasks.consumerCertDir;
		if (originalEntitlementCertDir==null) originalEntitlementCertDir = clienttasks.entitlementCertDir;
		*/
		
		// configure non-default rhsm cert directories
		String rhsmDebugProductCertDir = "/tmp/rhsmDebugProductCertDir";
		String rhsmDebugConsumerCertDir = "/tmp/rhsmDebugConsumerCertDir";
		String rhsmDebugEntitlementCertDir = "/tmp/rhsmDebugEntitlementCertDir";
		client.runCommandAndWait("rm -rf "+rhsmDebugProductCertDir+" && mkdir -p "+rhsmDebugProductCertDir);
		client.runCommandAndWait("rm -rf "+rhsmDebugConsumerCertDir+" && mkdir -p "+rhsmDebugConsumerCertDir);
		client.runCommandAndWait("rm -rf "+rhsmDebugEntitlementCertDir+" && mkdir -p "+rhsmDebugEntitlementCertDir);
		clienttasks.updateConfFileParameter(clienttasks.rhsmConfFile, "productCertDir", rhsmDebugProductCertDir);
		clienttasks.updateConfFileParameter(clienttasks.rhsmConfFile, "consumerCertDir", rhsmDebugConsumerCertDir);
		clienttasks.updateConfFileParameter(clienttasks.rhsmConfFile, "entitlementCertDir", rhsmDebugEntitlementCertDir);
		
		// copy a few product certs to rhsmDebugProductCertDir
		for (File originalProductCertFile : originalProductCertFiles) {
			RemoteFileTasks.runCommandAndAssert(client, "cp "+originalProductCertFile+" "+rhsmDebugProductCertDir, new Integer(0));
		}
		
		// run the basic rhsm-debug system tests (with non-default rhsm cert directories)
		verifyRhsmDebugSystemTestWithOptions(null,null);
	}
	@AfterGroups(groups="setup", value="RhsmDebugSystemWithNonDefaultCertDirs1_Test")
	public void afterRhsmDebugSystemWithNonDefaultCertDirs1() {
		if (clienttasks==null) return;
		log.info("Restoring the original rhsm cert directory configurations...");
		if (originalProductCertDir!=null)		clienttasks.updateConfFileParameter(clienttasks.rhsmConfFile, "productCertDir", originalProductCertDir);
		if (originalConsumerCertDir!=null)		clienttasks.updateConfFileParameter(clienttasks.rhsmConfFile, "consumerCertDir", originalConsumerCertDir);
		if (originalEntitlementCertDir!=null)	clienttasks.updateConfFileParameter(clienttasks.rhsmConfFile, "entitlementCertDir", originalEntitlementCertDir);
	}
	
	
	@Test(	description="exercise the rhsm-debug tool with non-default configurations for ca_cert_dir pluginDir pluginConfDir",
			groups={"RhsmDebugSystemWithNonDefaultCertDirs2_Test","blockedByBug-1055664"},
			enabled=true)
			//@ImplementsNitrateTest(caseId=)
	public void RhsmDebugSystemWithNonDefaultCertDirs2_Test() {
		
		// remember the original configurations
		/* already taken care of in saveOriginalCertDirConfigurationsBeforeClass
		if (originalCaCertDir==null) originalCaCertDir = clienttasks.caCertDir;
		if (originalPluginDir==null) originalPluginDir = clienttasks.pluginDir;
		if (originalPluginConfDir==null) originalPluginConfDir = clienttasks.pluginConfDir;
		*/
		
		// configure non-default rhsm cert directories
		String rhsmDebugCaCertDir = "/tmp/rhsmDebugCaCertDir";
		String rhsmDebugPluginDir = "/tmp/rhsmDebugPluginDir";
		String rhsmDebugPluginConfDir = "/tmp/rhsmDebugPluginConfDir";
		client.runCommandAndWait("rm -rf "+rhsmDebugCaCertDir+" && mkdir -p "+rhsmDebugCaCertDir);
		client.runCommandAndWait("rm -rf "+rhsmDebugPluginDir+" && mkdir -p "+rhsmDebugPluginDir);
		client.runCommandAndWait("rm -rf "+rhsmDebugPluginConfDir+" && mkdir -p "+rhsmDebugPluginConfDir);
		clienttasks.updateConfFileParameter(clienttasks.rhsmConfFile, "ca_cert_dir", rhsmDebugCaCertDir);
		clienttasks.updateConfFileParameter(clienttasks.rhsmConfFile, "pluginDir", rhsmDebugPluginDir);
		clienttasks.updateConfFileParameter(clienttasks.rhsmConfFile, "pluginConfDir", rhsmDebugPluginConfDir);
		
		// copy the original config dir files to the tmp config dirs 
		client.runCommandAndWait("cp "+originalCaCertDir.replaceFirst("/$", "")+"/* "+rhsmDebugCaCertDir);
		client.runCommandAndWait("cp "+originalPluginDir.replaceFirst("/$", "")+"/* "+rhsmDebugPluginDir);
		client.runCommandAndWait("cp "+originalPluginConfDir.replaceFirst("/$", "")+"/* "+rhsmDebugPluginConfDir);
		
		// run the basic rhsm-debug system tests (with non-default rhsm cert directories)
		verifyRhsmDebugSystemTestWithOptions(null,null);
	}
	@AfterGroups(groups="setup", value="RhsmDebugSystemWithNonDefaultCertDirs2_Test")
	public void afterRhsmDebugSystemWithNonDefaultCertDirs2() {
		if (clienttasks==null) return;
		log.info("Restoring the original rhsm cert directory configurations...");
		if (originalCaCertDir!=null)		clienttasks.updateConfFileParameter(clienttasks.rhsmConfFile, "ca_cert_dir", originalCaCertDir);
		if (originalPluginDir!=null)		clienttasks.updateConfFileParameter(clienttasks.rhsmConfFile, "pluginDir", originalPluginDir);
		if (originalPluginConfDir!=null)	clienttasks.updateConfFileParameter(clienttasks.rhsmConfFile, "pluginConfDir", originalPluginConfDir);
	}
	
	
	
	
	
	// Candidates for an automated Test:
	
	
	
	// Configuration methods ***********************************************************************

	@BeforeClass(groups="setup")
	public void saveOriginalCertDirConfigurationsBeforeClass() {
		if (clienttasks==null) return;
		log.info("Remembering the original rhsm cert directory configurations...");
		
		if (originalProductCertDir==null)		originalProductCertDir		= clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, /*"rhsm",*/ "productCertDir");
		if (originalConsumerCertDir==null)		originalConsumerCertDir		= clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, /*"rhsm",*/ "consumerCertDir");
		if (originalEntitlementCertDir==null)	originalEntitlementCertDir	= clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, /*"rhsm",*/ "entitlementCertDir");

		if (originalCaCertDir==null)			originalCaCertDir			= clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, /*"rhsm",*/ "ca_cert_dir");
		if (originalPluginDir==null)			originalPluginDir			= clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, /*"rhsm",*/ "pluginDir");
		if (originalPluginConfDir==null)		originalPluginConfDir		= clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, /*"rhsm",*/ "pluginConfDir");
	}
	
	
	
	// Protected methods ***********************************************************************
	
	protected String originalProductCertDir=null;
	protected String originalConsumerCertDir=null;
	protected String originalEntitlementCertDir=null;
	
	protected String originalCaCertDir=null;
	protected String originalPluginDir=null;
	protected String originalPluginConfDir=null;
	
	public static boolean isFile(SSHCommandRunner sshCommandRunner, String filePath) {
		String command = "if [ -f \""+filePath+"\" ]; then echo true; else echo false; fi";
		SSHCommandResult result = sshCommandRunner.runCommandAndWait(command);
		return Boolean.valueOf(result.getStdout().trim());
	}
	
	public static boolean isDirectory(SSHCommandRunner sshCommandRunner, String dirPath) {
		String command = "if [ -d \""+dirPath+"\" ]; then echo true; else echo false; fi";
		SSHCommandResult result = sshCommandRunner.runCommandAndWait(command);
		return Boolean.valueOf(result.getStdout().trim());
	}
	
	protected void verifyRhsmDebugSystemTestWithOptions(String destination, Boolean noArchive) {
		
		// register
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, true, false, null, null, null);
		
		// attach some random entitlements
		List<String> poolIds = new ArrayList<String>(); 
		List<SubscriptionPool> subscriptionPools = getRandomSubsetOfList(clienttasks.getCurrentlyAvailableSubscriptionPools(), 3);	// 3 is sufficient
		for (SubscriptionPool subscriptionPool : subscriptionPools) poolIds.add(subscriptionPool.poolId);
		clienttasks.subscribe(null, null, poolIds, null, null, null, null, null, null, null, null);

		// run rhsm-debug system
		String rhsmDebugSystemCommand = clienttasks.rhsmDebugSystemCommand(destination, noArchive, null, null, null);
		SSHCommandResult result = client.runCommandAndWait(rhsmDebugSystemCommand);
		
		// assert results
		Assert.assertEquals(result.getExitCode(), new Integer(0), "The exit code from an attempt to run '"+rhsmDebugSystemCommand+"'.");
		Assert.assertEquals(result.getStderr().trim(), "", "The stderr from an attempt to run '"+rhsmDebugSystemCommand+"'.");
		
		// assert the --destination feedback	// Bug 1040338 - Display successful stdout feedback to the user with the actual --destination value used by "rhsm-debug system" command
		if (destination!=null) {
			//	[root@jsefler-7 ~]# rhsm-debug system --destination /tmp/dir
			//	Wrote: /tmp/dir/rhsm-debug-system-20140121-625114.tar.gz
			String expectedStartsWith = "Wrote: "+destination;
			Assert.assertTrue(result.getStdout().trim().startsWith(expectedStartsWith), "The stdout from an attempt to run '"+rhsmDebugSystemCommand+"' should indicate that it '"+expectedStartsWith+"'.");

		} else {
			//	[root@jsefler-7 ~]# rhsm-debug system
			//	Wrote: /tmp/rhsm-debug-system-20140121-804300.tar.gz
			//	[root@jsefler-7 ~]# rhsm-debug system --help | grep -A2 -- --destination
			//	  --destination=DESTINATION
			//	                        the destination location of the result; default is /tmp
			String expectedStartsWith = "Wrote: "+"/tmp/";
			Assert.assertTrue(result.getStdout().trim().startsWith(expectedStartsWith), "The stdout from an attempt to run '"+rhsmDebugSystemCommand+"' should indicate that it '"+expectedStartsWith+"'.");
		}
		
		// get the rhsmDebugFile from stdout (and the existence of the rhsmDebugFile)
		File rhsmDebugSystemFile = new File(result.getStdout().split(":")[1].trim());	// Wrote: /tmp/rhsm-debug-system-20140115-457636.tar.gz
		
		// assert the --no-archive feedback
		if (noArchive!=null && noArchive) {
			//	[root@jsefler-7 ~]# rhsm-debug system --help | grep -A1 -- --no-archive
			//	  --no-archive          data will be in an uncompressed directory
			//	[root@jsefler-7 ~]# rhsm-debug system --no-archive 
			//	Wrote: /tmp/rhsm-debug-system-20140121-141587
			String expectedEndsWith = ".tar.gz";
			Assert.assertTrue(!result.getStdout().trim().endsWith(expectedEndsWith), "The stdout from an attempt to run '"+rhsmDebugSystemCommand+"' should indicate that the written file does NOT end with '"+expectedEndsWith+"'.");
			Assert.assertTrue(isDirectory(client, rhsmDebugSystemFile.getPath()), "The result of '"+rhsmDebugSystemCommand+"' is an existing directory.");
		} else {
			String expectedEndsWith = ".tar.gz";
			Assert.assertTrue(result.getStdout().trim().endsWith(expectedEndsWith), "The stdout from an attempt to run '"+rhsmDebugSystemCommand+"' should indicate that the written file ends with '"+expectedEndsWith+"'.");
			Assert.assertTrue(isFile(client, rhsmDebugSystemFile.getPath()), "The result of '"+rhsmDebugSystemCommand+"' is an existing file.");
		}
		
		// explode the rhsmDebugFile
		String explodeDir= destination==null? "/tmp/rhsmDebugSystemTestDir":destination;
		String explodeSubDir=null;
		String explodeListing=null;
		SSHCommandResult explodeResult;
		if (noArchive!=null && noArchive) {
			if (destination==null) explodeDir = rhsmDebugSystemFile.getPath().replaceFirst("/$", "");
			explodeResult = client.runCommandAndWait("find "+rhsmDebugSystemFile);
			Assert.assertEquals(explodeResult.getExitCode(), new Integer(0), "The exit code from finding files in '"+rhsmDebugSystemFile+"'.");
		} else {
			if (destination==null) client.runCommandAndWait("rm -rf "+explodeDir+" && mkdir -p "+explodeDir);
			explodeResult = client.runCommandAndWait("tar --verbose --extract --directory="+explodeDir+" --file="+rhsmDebugSystemFile);
			Assert.assertEquals(explodeResult.getExitCode(), new Integer(0), "The exit code from extracting '"+rhsmDebugSystemFile+"'.");
		}
		explodeListing = explodeResult.getStdout()/*do not .trim()*/;
		explodeSubDir = explodeListing.split("\n")[0].replaceFirst("/$", ""); // rhsm-debug-system-20140115-968966	// strip any trailing "/"
		
		// assert the presence of expected files...
		List<String> expectedFiles = new ArrayList<String>();
		//	[root@jsefler-7 ~]# tar --verbose --extract --directory=/tmp --file=/tmp/rhsm-debug-system-20140115-968966.tar.gz
		//	rhsm-debug-system-20140115-968966/
		//	rhsm-debug-system-20140115-968966/compliance.json
		//	rhsm-debug-system-20140115-968966/consumer.json
		//	rhsm-debug-system-20140115-968966/entitlements.json
		//	rhsm-debug-system-20140115-968966/pools.json
		//	rhsm-debug-system-20140115-968966/subscriptions.json
		//	rhsm-debug-system-20140115-968966/version.json
		//	rhsm-debug-system-20140115-968966/etc/
		//	rhsm-debug-system-20140115-968966/etc/pki/
		//	rhsm-debug-system-20140115-968966/etc/pki/consumer/
		//	rhsm-debug-system-20140115-968966/etc/pki/consumer/cert.pem
		//	rhsm-debug-system-20140115-968966/etc/pki/consumer/key.pem
		//	rhsm-debug-system-20140115-968966/etc/pki/entitlement/
		//	rhsm-debug-system-20140115-968966/etc/pki/entitlement/1712352679740801257-key.pem
		//	rhsm-debug-system-20140115-968966/etc/pki/entitlement/1712352679740801257.pem
		//	rhsm-debug-system-20140115-968966/etc/pki/product/
		//	rhsm-debug-system-20140115-968966/etc/pki/product/69.pem
		//	rhsm-debug-system-20140115-968966/etc/rhsm/
		//	rhsm-debug-system-20140115-968966/etc/rhsm/ca/
		//	rhsm-debug-system-20140115-968966/etc/rhsm/ca/candlepin-stage.pem
		//	rhsm-debug-system-20140115-968966/etc/rhsm/ca/jsefler-f14-7candlepin.pem
		//	rhsm-debug-system-20140115-968966/etc/rhsm/ca/jsefler-f14-candlepin.pem
		//	rhsm-debug-system-20140115-968966/etc/rhsm/ca/redhat-uep.pem
		//	rhsm-debug-system-20140115-968966/etc/rhsm/facts/
		//	rhsm-debug-system-20140115-968966/etc/rhsm/pluginconf.d/
		//	rhsm-debug-system-20140115-968966/etc/rhsm/pluginconf.d/all_slots_test.AllSlotsTestPlugin.conf
		//	rhsm-debug-system-20140115-968966/etc/rhsm/pluginconf.d/auto_attach_test.AutoAttachTestPlugin.conf
		//	rhsm-debug-system-20140115-968966/etc/rhsm/pluginconf.d/dbus_event.DbusEventPlugin.conf
		//	rhsm-debug-system-20140115-968966/etc/rhsm/pluginconf.d/facts_collection_test.FactsCollectionTestPlugin.conf
		//	rhsm-debug-system-20140115-968966/etc/rhsm/pluginconf.d/product_id_install_test.ProductIdInstallTestPlugin.conf
		//	rhsm-debug-system-20140115-968966/etc/rhsm/pluginconf.d/register_consumer_test1.RegisterConsumerTestPlugin.conf
		//	rhsm-debug-system-20140115-968966/etc/rhsm/pluginconf.d/register_consumer_test2.RegisterConsumerTestPlugin.conf
		//	rhsm-debug-system-20140115-968966/etc/rhsm/pluginconf.d/subscribe_test.SubscribeTestPlugin.conf
		//	rhsm-debug-system-20140115-968966/etc/rhsm/rhsm.conf
		//	rhsm-debug-system-20140115-968966/etc/rhsm/rhsm.conf.rpmsave
		//	rhsm-debug-system-20140115-968966/var/
		//	rhsm-debug-system-20140115-968966/var/lib/
		//	rhsm-debug-system-20140115-968966/var/lib/rhsm/
		//	rhsm-debug-system-20140115-968966/var/lib/rhsm/branded_name
		//	rhsm-debug-system-20140115-968966/var/lib/rhsm/cache/
		//	rhsm-debug-system-20140115-968966/var/lib/rhsm/cache/content_overrides.json
		//	rhsm-debug-system-20140115-968966/var/lib/rhsm/cache/entitlement_status.json
		//	rhsm-debug-system-20140115-968966/var/lib/rhsm/cache/installed_products.json
		//	rhsm-debug-system-20140115-968966/var/lib/rhsm/cache/product_status.json
		//	rhsm-debug-system-20140115-968966/var/lib/rhsm/facts/
		//	rhsm-debug-system-20140115-968966/var/lib/rhsm/facts/facts.json
		//	rhsm-debug-system-20140115-968966/var/lib/rhsm/packages/
		//	rhsm-debug-system-20140115-968966/var/lib/rhsm/productid.js
		//	rhsm-debug-system-20140115-968966/var/log/
		//	rhsm-debug-system-20140115-968966/var/log/rhsm/
		//	rhsm-debug-system-20140115-968966/var/log/rhsm/.rhsm.log.swp
		//	rhsm-debug-system-20140115-968966/var/log/rhsm/rhsm.log
		//	rhsm-debug-system-20140115-968966/var/log/rhsm/rhsm.log-20131223
		//	rhsm-debug-system-20140115-968966/var/log/rhsm/rhsm.log.1
		//	rhsm-debug-system-20140115-968966/var/log/rhsm/rhsm.log.2
		//	rhsm-debug-system-20140115-968966/var/log/rhsm/rhsm.log.3
		//	rhsm-debug-system-20140115-968966/var/log/rhsm/rhsm.log.4
		//	rhsm-debug-system-20140115-968966/var/log/rhsm/rhsm.log.5
		//	rhsm-debug-system-20140115-968966/var/log/rhsm/rhsmcertd.log
		//	rhsm-debug-system-20140115-968966/var/log/rhsm/rhsmcertd.log-20131223
		
		// current candlepin files
		expectedFiles.add("/compliance.json");
		expectedFiles.add("/consumer.json");
		expectedFiles.add("/entitlements.json");
		expectedFiles.add("/pools.json");
		expectedFiles.add("/subscriptions.json");
		expectedFiles.add("/version.json");
		
		// current /etc/rhsm files
		for (String expectedFile : client.runCommandAndWait("find /etc/rhsm").getStdout().trim().split("\n")) {
			if (!expectedFiles.contains(expectedFile)) expectedFiles.add(expectedFile);
		}
		
		// current /var/lib/rhsm files
		for (String expectedFile : client.runCommandAndWait("find /var/lib/rhsm").getStdout().trim().split("\n")) {
			if (!expectedFiles.contains(expectedFile)) expectedFiles.add(expectedFile);
		}
		
		// current /var/log/rhsm files
		for (String expectedFile : client.runCommandAndWait("find /var/log/rhsm").getStdout().trim().split("\n")) {
			if (!expectedFiles.contains(expectedFile)) expectedFiles.add(expectedFile);
		}
		
		// current consumer cert files
		String consumerCertDir = clienttasks.getConfParameter("consumerCertDir");
		expectedFiles.add(consumerCertDir+"/key.pem");
		expectedFiles.add(consumerCertDir+"/cert.pem");
		
		// current entitlement cert files
		String entitlementCertDir = clienttasks.getConfParameter("entitlementCertDir");
		for (File entitlementCertFile : clienttasks.getCurrentEntitlementCertFiles()) {
			expectedFiles.add(entitlementCertFile.getPath());
			expectedFiles.add(clienttasks.getEntitlementCertKeyFileCorrespondingToEntitlementCertFile(entitlementCertFile).getPath());
		}
		
		// current product cert files
		String productCertDir = clienttasks.getConfParameter("productCertDir");
		for (File productCertFile : clienttasks.getCurrentProductCertFiles()) {
			expectedFiles.add(productCertFile.getPath());
		}
		
		// current ca cert files
		String caCertDir = clienttasks.getConfParameter("ca_cert_dir");
		for (String expectedFile : client.runCommandAndWait("find "+caCertDir).getStdout().trim().split("\n")) {
			if (!expectedFiles.contains(expectedFile)) expectedFiles.add(expectedFile);
		}
		
		// current plugin files
		String pluginDir = clienttasks.getConfParameter("pluginDir");
		// TEMPORARY WORKAROUND FOR BUG: https://bugzilla.redhat.com/show_bug.cgi?id=1055664 - jsefler 1/20/2014
		Boolean invokeWorkaroundWhileBugIsOpen = true;
		try {String bugId="1055664"; if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");SubscriptionManagerCLITestScript.addInvokedWorkaround(bugId);} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
		if (invokeWorkaroundWhileBugIsOpen) {
			log.warning("The workaround while this bug is open is to skip the expected files in rhsm.pluginDir '"+pluginDir+"'.");
		} else	// do the for (String expectedFile : client.runCommandAndWait("find "+pluginConfDir).getStdout().trim().split("\n")) loop
		// END OF WORKAROUND
		for (String expectedFile : client.runCommandAndWait("find "+pluginDir).getStdout().trim().split("\n")) {
			if (!expectedFiles.contains(expectedFile)) expectedFiles.add(expectedFile);
		}
		
		// current plugin config files
		String pluginConfDir = clienttasks.getConfParameter("pluginConfDir");
		for (String expectedFile : client.runCommandAndWait("find "+pluginConfDir).getStdout().trim().split("\n")) {
			if (!expectedFiles.contains(expectedFile)) expectedFiles.add(expectedFile);
		}
		
		// assert the presence of expected files... (within the verbose output from tar -xvf)
		boolean expectedFilesFound = true;
		for (String expectedFile : expectedFiles) {
			if (explodeListing.contains(expectedFile)) {
				Assert.assertTrue(explodeListing.contains(expectedFile), "Explosion of '"+rhsmDebugSystemFile+"' appears to contain expected file '"+expectedFile+"'.");			
			} else {
				log.warning("Explosion of '"+rhsmDebugSystemFile+"' does NOT contain expected file '"+expectedFile+"'.");
				expectedFilesFound = false;
			}
		}
		Assert.assertTrue(expectedFilesFound, "Explosion of '"+rhsmDebugSystemFile+"' appears to contain all the expected files (see WARNINGS above when false).");
		
		// assert the presence of expected files... (from find untarDir)
		String findCommand = "find "+explodeDir;
		SSHCommandResult findResult = client.runCommandAndWait(findCommand);
		for (String expectedFile : expectedFiles) {
			Assert.assertTrue(findResult.getStdout().contains(expectedFile), "'"+findCommand+"' reports expected file '"+expectedFile+"'.");
		}
		
		// check for any unexpected files included in the tar file
		String tmpListing = explodeListing;
		for (String expectedFile : expectedFiles) {
			tmpListing = tmpListing.replaceFirst(explodeSubDir+expectedFile+"/?\\n", "");
		}
		tmpListing = tmpListing.replaceFirst(explodeSubDir+"/?\\n", "");
		tmpListing = tmpListing.replaceFirst(explodeSubDir+"/etc/?\\n", "");
		tmpListing = tmpListing.replaceFirst(explodeSubDir+"/etc/pki/?\\n", "");
		tmpListing = tmpListing.replaceFirst(explodeSubDir+caCertDir+"/?\\n", "");
		tmpListing = tmpListing.replaceFirst(explodeSubDir+pluginDir+"/?\\n", "");
		tmpListing = tmpListing.replaceFirst(explodeSubDir+pluginConfDir+"/?\\n", "");
		tmpListing = tmpListing.replaceFirst(explodeSubDir+productCertDir+"/?\\n", "");
		tmpListing = tmpListing.replaceFirst(explodeSubDir+entitlementCertDir+"/?\\n", "");
		tmpListing = tmpListing.replaceFirst(explodeSubDir+consumerCertDir+"/?\\n", "");
		tmpListing = tmpListing.replaceFirst(explodeSubDir+"/var/?\\n", "");
		tmpListing = tmpListing.replaceFirst(explodeSubDir+"/var/log/?\\n", "");
		tmpListing = tmpListing.replaceFirst(explodeSubDir+"/var/lib/?\\n", "");
		tmpListing = tmpListing.replaceFirst(explodeSubDir+"/tmp/?\\n", "");	// needed for RhsmDebugSystemWithNonDefaultCertDirs_Test
		tmpListing = tmpListing.trim();
		if (!tmpListing.isEmpty()) Assert.fail("Found the following unexpected files included in the rhsm-debug output file '"+rhsmDebugSystemFile+"' :\n"+tmpListing);
	}
	
	// Data Providers ***********************************************************************
	
	
	
}

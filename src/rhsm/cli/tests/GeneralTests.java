package rhsm.cli.tests;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONException;
import org.testng.SkipException;
import org.testng.annotations.AfterGroups;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import rhsm.base.SubscriptionManagerCLITestScript;

import com.redhat.qe.Assert;
import com.redhat.qe.auto.bugzilla.BlockedByBzBug;
import com.redhat.qe.auto.tcms.ImplementsNitrateTest;
import com.redhat.qe.auto.testng.TestNGUtils;
import com.redhat.qe.tools.RemoteFileTasks;
import com.redhat.qe.tools.SSHCommandResult;

/**
 * @author ssalevan
 * @author jsefler
 *
 */
@Test(groups={"GeneralTests"})
public class GeneralTests extends SubscriptionManagerCLITestScript{
	
	
	// Test Methods ***********************************************************************
	
	
	@Test(	description="subscription-manager-cli: attempt to access functionality without registering",
			groups={"blockedByBug-749332"},
			dataProvider="UnregisteredCommandData")
	@ImplementsNitrateTest(caseId=50215)
	public void AttemptingCommandsWithoutBeingRegistered_Test(String command) {
		log.info("Testing subscription-manager-cli command without being registered, expecting it to fail: "+ command);
		clienttasks.unregister(null, null, null);
		//RemoteFileTasks.runCommandAndAssert(client,command,1,"^Error: You need to register this system by running `register` command before using this option.",null);	// results changed after bug fix 749332
		RemoteFileTasks.runCommandAndAssert(client,command,255,"^"+clienttasks.msg_ConsumerNotRegistered,null);

	}
	
	
	@Test(	description="subscription-manager-cli: attempt to access functionality that does not exist",
			groups={},
			dataProvider="NegativeFunctionalityData")
	public void AttemptingCommandsThatAreInvalid_Test(Object blockedByBug, String command, Integer expectedExitCode, String expectedStdout, String expectedStderr) {
		log.info("Testing subscription-manager-cli command that is invalid, expecting it to fail: "+ command);
		SSHCommandResult result = client.runCommandAndWait(command);
		if (expectedExitCode!=null)	Assert.assertEquals(result.getExitCode(), expectedExitCode, "The expected exit code.");
		if (expectedStdout!=null)	Assert.assertEquals(result.getStdout().trim(), expectedStdout, "The expected stdout message.");
		if (expectedStderr!=null)	Assert.assertEquals(result.getStderr().trim(), expectedStderr, "The expected stderr message.");
	}
	
	
	@Test(	description="assert the exit code from service rhsmcertd status when running and stopped",
			groups={"blockedByBug-895263","blockedByBug-824680","blockedByBug-842464"})
	public void VerifyExitCodeStatusForRhmscertd_Test() {
		Integer expectedStoppedStatus = new Integer(3);
		Integer expectedRunningStatus = new Integer(0);
		log.info("When service "+clienttasks.rhsmCertD+" is stopped, the expected service status exit code is: "+expectedStoppedStatus);
		log.info("When service "+clienttasks.rhsmCertD+" is running, the expected service status exit code is: "+expectedRunningStatus);
		RemoteFileTasks.runCommandAndAssert(client, "service "+clienttasks.rhsmCertD+" stop  && service "+clienttasks.rhsmCertD+" status", new Integer(3));
		RemoteFileTasks.runCommandAndAssert(client, "service "+clienttasks.rhsmCertD+" start && service "+clienttasks.rhsmCertD+" status", new Integer(0));
	}
	
	
	@Test(	description="assert the exit code from service rhsmcertd status when running and stopped",
			groups={"AcceptanceTests","blockedByBug-913118","blockedByBug-912707","blockedByBug-914113"})
	protected void verifyRhsmcertdDoesNotThrowDeprecationWarnings_Test() throws JSONException, Exception {
		clienttasks.unregister(null, null, null);
		String marker = System.currentTimeMillis()+" Testing verifyRhsmcertdDoesNotThrowDeprecationWarnings_Test...";
		RemoteFileTasks.markFile(client, clienttasks.messagesLogFile, marker);
		
		String command = clienttasks.rhsmComplianceD+" -s";
		SSHCommandResult result = client.runCommandAndWait(command);
		Assert.assertEquals(result.getExitCode(), Integer.valueOf(0),"ExitCode from command '"+command+"'.");
		Assert.assertTrue(result.getStdout().isEmpty(),"Stdout from command '"+command+"' is empty.");
		Assert.assertTrue(result.getStderr().isEmpty(),"Stderr from command '"+command+"' is empty.");
		
		String rhsmcertdLogResult = RemoteFileTasks.getTailFromMarkedFile(client, clienttasks.messagesLogFile, marker, null).trim();
		String expectedMessage = "In order for Subscription Manager to provide your system with updates, your system must be registered with the Customer Portal. Please enter your Red Hat login to ensure your system is up-to-date.";
		Assert.assertTrue(rhsmcertdLogResult.contains(expectedMessage),"Syslog contains expected message '"+expectedMessage+"'.");
		String unexpectedMessage = "DeprecationWarning";
		Assert.assertTrue(!rhsmcertdLogResult.contains(unexpectedMessage),"Syslog does not contain message '"+unexpectedMessage+"'.");
		
		clienttasks.register(sm_clientUsername,sm_clientPassword,sm_clientOrg,null,null,null,null,null,null,null,(List<String>)null,null,null,null,null,null,null,null,null);
		/*
		List<SubscriptionPool> pools = clienttasks.getCurrentlyAvailableSubscriptionPools();
		if (pools.isEmpty()) throw new SkipException("Cannot randomly pick a pool for subscribing when there are no available pools for testing."); 
		SubscriptionPool pool = pools.get(randomGenerator.nextInt(pools.size())); // randomly pick a pool
		clienttasks.subscribe(null,null,pool.poolId,null,null,null,null,null,null,null,null);
		*/
		clienttasks.subscribeToTheCurrentlyAvailableSubscriptionPoolsCollectively();
		// TODO: I can only seem to reproduce the error after consuming an entitlement from stage - don't know why - maybe it requires an entitlement with a warning period - let's just subscribe to all the pools.
		result = client.runCommandAndWait(command);
		//	[root@rhsm-accept-rhel5 ~]# /usr/libexec/rhsmd -s
		//	/usr/lib64/python2.4/site-packages/rhsm/certificate.py:123: DeprecationWarning: Call to deprecated function: hasNow
		//	  category=DeprecationWarning)
		//	[root@rhsm-accept-rhel5 ~]#
		Assert.assertEquals(result.getExitCode(), Integer.valueOf(0),"ExitCode from command '"+command+"'.");
		Assert.assertTrue(result.getStdout().isEmpty(),"Stdout from command '"+command+"' is empty.");
		Assert.assertTrue(result.getStderr().isEmpty(),"Stderr from command '"+command+"' is empty.");
	}
	
	
	@Test(	description="assert rhsmd is logged to both /var/log/rhsm/rhsm.log and /var/log/messages",
			groups={"blockedbyBug-976868"},
			enabled=true)
	//@ImplementsTCMS(id="")
	public void VerifyRhsmdForceSignalsToRhsmlogAndSyslog_Test() {
		clienttasks.register(sm_clientUsername,sm_clientPassword,sm_clientOrg,null,null,null,null,null,null,null,(List<String>)null,null,null,null,null,null,null,null,null);
		
		Map<String,String> signalMap = new HashMap<String,String>();
		signalMap.put("valid", "");
		signalMap.put("expired", "This system is missing one or more subscriptions. Please run subscription-manager for more information.");
		signalMap.put("warning", "This system's subscriptions are about to expire. Please run subscription-manager for more information.");
		signalMap.put("partial", "This system is missing one or more subscriptions to fully cover its products. Please run subscription-manager for more information.");
		
		for (String signal : signalMap.keySet()) {
			String command = clienttasks.rhsmComplianceD+" -s -d -i -f "+signal;
			String marker = System.currentTimeMillis()+" Testing VerifyRhsmdLogsToRhsmlogAndSyslog_Test...";
			RemoteFileTasks.markFile(client, clienttasks.rhsmLogFile, marker);
			RemoteFileTasks.markFile(client, clienttasks.messagesLogFile, marker);

			// run and verify the command result
			String expectedStdout = "forcing status signal from cli arg";
			RemoteFileTasks.runCommandAndAssert(client, command, Integer.valueOf(0), expectedStdout, null);
			
			// verify the logs
			sleep(1000);	// give the message thread time to be logged
			String logResult;
			if (signalMap.get(signal).isEmpty()) {
				String unExpectedMessage = "Please run subscription-manager for more information.";
				logResult = RemoteFileTasks.getTailFromMarkedFile(client, clienttasks.messagesLogFile, marker, null).trim();
				Assert.assertTrue(!logResult.contains(unExpectedMessage),clienttasks.messagesLogFile+" does NOT contain message '"+unExpectedMessage+"'.");
				logResult = RemoteFileTasks.getTailFromMarkedFile(client, clienttasks.rhsmLogFile, marker, null).trim();
				Assert.assertTrue(!logResult.contains(unExpectedMessage),clienttasks.rhsmLogFile+" does NOT contain message '"+unExpectedMessage+"'.");
				Assert.assertTrue(logResult.contains(expectedStdout),clienttasks.rhsmLogFile+" contains expected message '"+expectedStdout+"'.");

			} else {
				String expectedMessage = signalMap.get(signal);
				logResult = RemoteFileTasks.getTailFromMarkedFile(client, clienttasks.messagesLogFile, marker, null).trim();
				Assert.assertTrue(logResult.contains(expectedMessage),clienttasks.messagesLogFile+" contains expected message '"+expectedMessage+"'.");
				logResult = RemoteFileTasks.getTailFromMarkedFile(client, clienttasks.rhsmLogFile, marker, null).trim();
				Assert.assertTrue(logResult.contains(expectedMessage),clienttasks.rhsmLogFile+" contains expected message '"+expectedMessage+"'.");
				Assert.assertTrue(logResult.contains(expectedStdout),clienttasks.rhsmLogFile+" contains expected message '"+expectedStdout+"'.");
			}
		}
	}
	
	
	@Test(	description="assert permissions on /etc/cron.daily/rhsmd",
			groups={"blockedbyBug-1012566"},
			enabled=true)
	//@ImplementsTCMS(id="")
	public void VerifyPermissionsOnEtcCronDailyRhsmd_Test() {
		//	[root@jsefler-6 ~]# ls -l /etc/cron.daily/rhsmd 
		//	-rwxr-xr-x. 1 root root 256 Sep 23 14:53 /etc/cron.daily/rhsmd
		
		// Bug 1012566 - /etc/cron.daily/rhsmd breaks rule GEN003080 in Red Hat security guide
		// It should have permissions 0700.
		// http://people.redhat.com/sgrubb/files/stig-2011/stig-2011-checklist.html#item-SV-978r7_rule
		
		File cronDailyFile = new File("/etc/cron.daily/rhsmd");
		RemoteFileTasks.runCommandAndAssert(client, "ls -l "+cronDailyFile, Integer.valueOf(0), "-rwx------\\. 1 root root .* "+cronDailyFile+"\\n", null);
	}
	
	
	@Test(	description="verify enablement of yum plugin for subscription-manager in /etc/yum/pluginconf.d/subscription-manager.conf ",
			groups={"VerifyYumPluginForSubscriptionManagerEnablement_Test", "blockedByBug-1017354"},
			enabled=true)
	//@ImplementsTCMS(id="")
	public void VerifyYumPluginForSubscriptionManagerEnablement_Test() {
		SSHCommandResult sshCommandResult;
		String expectedMsg;
		
		// test /etc/yum/pluginconf.d/subscription-manager.conf enabled=1 and enabled=0 (UNREGISTERED)
		clienttasks.unregister(null, null, null);
		clienttasks.updateConfFileParameter(clienttasks.rhsmPluginConfFile, "enabled", "1");
		expectedMsg = "This system is not registered to Red Hat Subscription Management. You can use subscription-manager to register.";
		sshCommandResult = client.runCommandAndWait("yum repolist --disableplugin=rhnplugin"); // --disableplugin=rhnplugin helps avoid: up2date_client.up2dateErrors.AbuseError
//		Assert.assertTrue(sshCommandResult.getStderr().contains(expectedMsg), "Yum repolist with subscription-manager.conf enabled=1 displays expected stderr message '"+expectedMsg+"'.");
		Assert.assertTrue(sshCommandResult.getStdout().contains(expectedMsg), "Yum repolist with subscription-manager.conf enabled=1 displays expected stdout message '"+expectedMsg+"'.");
		clienttasks.updateConfFileParameter(clienttasks.rhsmPluginConfFile, "enabled", "0");
		sshCommandResult = client.runCommandAndWait("yum repolist --disableplugin=rhnplugin"); // --disableplugin=rhnplugin helps avoid: up2date_client.up2dateErrors.AbuseError
		Assert.assertFalse((sshCommandResult.getStdout()+sshCommandResult.getStderr()).contains(expectedMsg), "Yum repolist with subscription-manager.conf enabled=0 displays expected stderr message '"+expectedMsg+"'.");
		
		// test /etc/yum/pluginconf.d/subscription-manager.conf enabled=1 and enabled=0 (REGISTERED)
		clienttasks.register(sm_clientUsername,sm_clientPassword,sm_clientOrg,null,null,null,null,null,null,null,(List<String>)null,null,null,null,null,false,null,null,null);
		clienttasks.updateConfFileParameter(clienttasks.rhsmPluginConfFile, "enabled", "1");
		expectedMsg = "This system is registered to Red Hat Subscription Management, but is not receiving updates. You can use subscription-manager to assign subscriptions.";
		sshCommandResult = client.runCommandAndWait("yum repolist --disableplugin=rhnplugin"); // --disableplugin=rhnplugin helps avoid: up2date_client.up2dateErrors.AbuseError
//		Assert.assertTrue(sshCommandResult.getStderr().contains(expectedMsg), "Yum repolist with subscription-manager.conf enabled=1 displays expected stderr message '"+expectedMsg+"'.");
		Assert.assertTrue(sshCommandResult.getStdout().contains(expectedMsg), "Yum repolist with subscription-manager.conf enabled=1 displays expected stdout message '"+expectedMsg+"'.");
		clienttasks.updateConfFileParameter(clienttasks.rhsmPluginConfFile, "enabled", "0");
		sshCommandResult = client.runCommandAndWait("yum repolist --disableplugin=rhnplugin"); // --disableplugin=rhnplugin helps avoid: up2date_client.up2dateErrors.AbuseError
		Assert.assertFalse((sshCommandResult.getStdout()+sshCommandResult.getStderr()).contains(expectedMsg), "Yum repolist with subscription-manager.conf enabled=0 displays expected stderr message '"+expectedMsg+"'.");
	}
	@AfterGroups(value="VerifyYumPluginForSubscriptionManagerEnablement_Test", alwaysRun=true)
	protected void afterVerifyYumPluginForSubscriptionManagerEnablement_Test() {
		clienttasks.updateConfFileParameter(clienttasks.rhsmPluginConfFile, "enabled", "1");
	}
	
	
	@Test(	description="check the rpm requires list for changes to python-rhsm",
			groups={"blockedByBug-1006748","blockedByBug-800732"},
			enabled=true)
	//@ImplementsTCMS(id="")
	public void VerifyRpmRequireListForPythonRhsm_Test() {
		String pkg = "python-rhsm";
		if (!clienttasks.isPackageInstalled(pkg)) throw new SkipException("This test require that package '"+pkg+"' be installed.");
		SSHCommandResult sshCommandResult = client.runCommandAndWait("rpm --query --requires "+pkg+" --verbose | egrep '(^manual:|^preun:|^postun:|^post:)'");
		
		List<String> actualRequiresList = new ArrayList<String>();
		for (String requires : Arrays.asList(sshCommandResult.getStdout().trim().split("\\n"))) {
			if (!requires.trim().isEmpty()) actualRequiresList.add(requires.trim());
		}

		List<String> expecetdRequiresList = new ArrayList<String>();
		if (clienttasks.redhatReleaseX.equals("5")) { 
			// TODO
		}
		if (clienttasks.redhatReleaseX.equals("6") || clienttasks.redhatReleaseX.equals("7")) {
			expecetdRequiresList.addAll(Arrays.asList(new String[]{
					"manual: m2crypto",
					"manual: python-iniparse",
					//"manual: python-simplejson",	// removed by bug 1006748
					"manual: rpm-python"
			}));
		}
		
		for (String expectedRequires : expecetdRequiresList) if (!actualRequiresList.contains(expectedRequires)) log.warning("The actual requires list is missing expected requires '"+expectedRequires+"'.");
		for (String actualRequires : actualRequiresList) if (!expecetdRequiresList.contains(actualRequires)) log.warning("The expected requires list does not include the actual requires '"+actualRequires+"'  Is this a new requirement?");
		Assert.assertTrue(expecetdRequiresList.containsAll(actualRequiresList) && actualRequiresList.containsAll(expecetdRequiresList), "The actual requires list of packages for '"+pkg+"' matches the expected list "+expecetdRequiresList);
	}
	
	
	@Test(	description="check the rpm requires list for changes to subscription-manager",
			groups={"blockedbyBug-801280","blockedByBug-1006748","blockedByBug-800744"},
			enabled=true)
	//@ImplementsTCMS(id="")
	public void VerifyRpmRequireListForSubscriptionManager_Test() {
		String pkg = "subscription-manager";
		if (!clienttasks.isPackageInstalled(pkg)) throw new SkipException("This test require that package '"+pkg+"' be installed.");
		SSHCommandResult sshCommandResult = client.runCommandAndWait("rpm --query --requires "+pkg+" --verbose | egrep '(^manual:|^preun:|^postun:|^post:)'");
		
		List<String> actualRequiresList = new ArrayList<String>();
		for (String requires : Arrays.asList(sshCommandResult.getStdout().trim().split("\\n"))) {
			if (!requires.trim().isEmpty()) actualRequiresList.add(requires.trim());
		}
		
		List<String> expecetdRequiresList = new ArrayList<String>();
		if (clienttasks.redhatReleaseX.equals("5")) { 
			// TODO
		}
		if (clienttasks.redhatReleaseX.equals("6")) {
			expecetdRequiresList.addAll(Arrays.asList(new String[]{
					"manual: config(subscription-manager) = "+clienttasks.installedPackageVersion.get("subscription-manager").replace("subscription-manager-", "").replaceFirst("\\."+clienttasks.arch, ""),	// "manual: config(subscription-manager) = 1.9.11-1.el6",
					"post: /bin/sh",
					"preun: /bin/sh",
					"postun: /bin/sh",
					"post: chkconfig",
					"preun: chkconfig",
					"manual: dbus-python",
					"preun: initscripts",
					"manual: pygobject2",
					"manual: python-dateutil",
					"manual: python-dmidecode",
					"manual: python-ethtool",
					"manual: python-iniparse",
					//"manual: python-rhsm >= 1.9.1-1"/*RHEL65*/,
					"manual: python-rhsm >= 1.10.9"/*RHEL70*/,
					//"manual: python-simplejson",	// removed by bug 1006748
					"manual: usermode",
					"manual: virt-what",
					"manual: yum >= 3.2.19-15"
			}));
		}
		if (clienttasks.redhatReleaseX.equals("7")) {
			expecetdRequiresList.addAll(Arrays.asList(new String[]{
					"post: systemd-units",
					"preun: systemd-units",
					"postun: systemd-units",
					//"manual: config(subscription-manager) = "+clienttasks.installedPackageVersion.get("subscription-manager").replace("subscription-manager-", "").replaceFirst("\\."+clienttasks.arch, ""),	//"manual: config(subscription-manager) = 1.9.11-1.el6",
					//"post: /bin/sh",
					//"preun: /bin/sh",
					//"postun: /bin/sh",
					"post: chkconfig",
					"preun: chkconfig",
					"manual: dbus-python",
					"preun: initscripts",
					"manual: pygobject2",
					"manual: python-dateutil",
					"manual: python-dmidecode",
					"manual: python-ethtool",
					"manual: python-iniparse",
					//"manual: python-rhsm >= 1.9.1-1"/*RHEL65*/,
					"manual: python-rhsm >= 1.10.9"/*RHEL70*/,
					//"manual: python-simplejson",	// removed by bug 1006748
					"manual: usermode",
					"manual: virt-what",
					"manual: yum >= 3.2.19-15"
			}));
		}
		
		for (String expectedRequires : expecetdRequiresList) if (!actualRequiresList.contains(expectedRequires)) log.warning("The actual requires list is missing expected requires '"+expectedRequires+"'.");
		for (String actualRequires : actualRequiresList) if (!expecetdRequiresList.contains(actualRequires)) log.warning("The expected requires list does not include the actual requires '"+actualRequires+"'  Is this a new requirement?");
		Assert.assertTrue(expecetdRequiresList.containsAll(actualRequiresList) && actualRequiresList.containsAll(expecetdRequiresList), "The actual requires list of packages for '"+pkg+"' matches the expected list "+expecetdRequiresList);
	}
	
	
	@Test(	description="check the rpm requires list for changes to subscription-manager-gui",
			groups={"blockedbyBug-1004908"},
			enabled=true)
	//@ImplementsTCMS(id="")
	public void VerifyRpmRequireListForSubscriptionManagerGui_Test() {
		String pkg = "subscription-manager-gui";
		if (!clienttasks.isPackageInstalled(pkg)) throw new SkipException("This test require that package '"+pkg+"' be installed.");
		SSHCommandResult sshCommandResult = client.runCommandAndWait("rpm --query --requires "+pkg+" --verbose | egrep '(^manual:|^preun:|^postun:|^post:)'");
		
		List<String> actualRequiresList = new ArrayList<String>();
		for (String requires : Arrays.asList(sshCommandResult.getStdout().trim().split("\\n"))) {
			if (!requires.trim().isEmpty()) actualRequiresList.add(requires.trim());
		}
		
		List<String> expecetdRequiresList = new ArrayList<String>();
		if (clienttasks.redhatReleaseX.equals("5")) { 
			// TODO
		}
		if (clienttasks.redhatReleaseX.equals("6")) {
			expecetdRequiresList.addAll(Arrays.asList(new String[]{
					"manual: subscription-manager = "+clienttasks.installedPackageVersion.get("subscription-manager").replace("subscription-manager-", "").replaceFirst("\\."+clienttasks.arch, ""),	//"manual: subscription-manager = 1.9.11-1.el6",
					"manual: librsvg2("+clienttasks.arch.replace("_","-")+")",	//"manual: librsvg2(x86-64)",
					"post: /bin/sh",
					"postun: /bin/sh",
					"manual: dbus-x11",
					"manual: gnome-python2",
					"manual: gnome-python2-canvas",
					"manual: pygtk2",
					"manual: pygtk2-libglade",
					"post: scrollkeeper",
					"postun: scrollkeeper",
					"manual: usermode-gtk"
			}));
		}
		if (clienttasks.redhatReleaseX.equals("7")) {
			expecetdRequiresList.addAll(Arrays.asList(new String[]{
					"manual: subscription-manager = "+clienttasks.installedPackageVersion.get("subscription-manager").replace("subscription-manager-", "").replaceFirst("\\."+clienttasks.arch, ""),	//"manual: subscription-manager = 1.9.11-1.el6",
					"manual: librsvg2("+clienttasks.arch.replace("_","-")+")",	//"manual: librsvg2(x86-64)",
					//"post: /bin/sh",
					//"postun: /bin/sh",
					"manual: dbus-x11",
					"manual: gnome-python2",
					"manual: gnome-python2-canvas",
					"manual: pygtk2",
					"manual: pygtk2-libglade",
					"post: scrollkeeper",
					"postun: scrollkeeper",
					"manual: usermode-gtk"
			}));
		}
		
		for (String expectedRequires : expecetdRequiresList) if (!actualRequiresList.contains(expectedRequires)) log.warning("The actual requires list is missing expected requires '"+expectedRequires+"'.");
		for (String actualRequires : actualRequiresList) if (!expecetdRequiresList.contains(actualRequires)) log.warning("The expected requires list does not include the actual requires '"+actualRequires+"'  Is this a new requirement?");
		Assert.assertTrue(expecetdRequiresList.containsAll(actualRequiresList) && actualRequiresList.containsAll(expecetdRequiresList), "The actual requires list of packages for '"+pkg+"' matches the expected list "+expecetdRequiresList);
	}
	
	
	@Test(	description="check the rpm requires list for changes to subscription-manager-firstboot",
			groups={"blockedbyBug-1004908"},
			enabled=true)
	//@ImplementsTCMS(id="")
	public void VerifyRpmRequireListForSubscriptionManagerFirstboot_Test() {
		String pkg = "subscription-manager-firstboot";
		if (!clienttasks.isPackageInstalled(pkg)) throw new SkipException("This test require that package '"+pkg+"' be installed.");
		SSHCommandResult sshCommandResult = client.runCommandAndWait("rpm --query --requires "+pkg+" --verbose | egrep '(^manual:|^preun:|^postun:|^post:)'");
		
		List<String> actualRequiresList = new ArrayList<String>();
		for (String requires : Arrays.asList(sshCommandResult.getStdout().trim().split("\\n"))) {
			if (!requires.trim().isEmpty()) actualRequiresList.add(requires.trim());
		}
		
		List<String> expecetdRequiresList = new ArrayList<String>();
		if (clienttasks.redhatReleaseX.equals("5")) { 
			// TODO
		}
		if (clienttasks.redhatReleaseX.equals("6")) {
			expecetdRequiresList.addAll(Arrays.asList(new String[]{
					"manual: subscription-manager-gui = "+clienttasks.installedPackageVersion.get("subscription-manager-gui").replace("subscription-manager-gui-", "").replaceFirst("\\."+clienttasks.arch, ""),	//"manual: subscription-manager-gui = 1.9.11-1.el6",
					"manual: librsvg2",
					"manual: rhn-setup-gnome"
			}));
		}
		if (clienttasks.redhatReleaseX.equals("7")) {
			expecetdRequiresList.addAll(Arrays.asList(new String[]{
					"manual: subscription-manager-gui = "+clienttasks.installedPackageVersion.get("subscription-manager-gui").replace("subscription-manager-gui-", "").replaceFirst("\\."+clienttasks.arch, ""),	//"manual: subscription-manager-gui = 1.9.11-1.el6",
					"manual: librsvg2",
					//"manual: rhn-setup-gnome"	// removed by git commit 72e37ab24d5ba1ea9ff8ccc756246df721e204b1 Fix firstboot on Fedora 19. (will help fix Bug 1021013 - RHEL7 firstboot: missing option to skip Subscription Management Registration)
			}));
		}
		
		for (String expectedRequires : expecetdRequiresList) if (!actualRequiresList.contains(expectedRequires)) log.warning("The actual requires list is missing expected requires '"+expectedRequires+"'.");
		for (String actualRequires : actualRequiresList) if (!expecetdRequiresList.contains(actualRequires)) log.warning("The expected requires list does not include the actual requires '"+actualRequires+"'  Is this a new requirement?");
		Assert.assertTrue(expecetdRequiresList.containsAll(actualRequiresList) && actualRequiresList.containsAll(expecetdRequiresList), "The actual requires list of packages for '"+pkg+"' matches the expected list "+expecetdRequiresList);
	}
	
	
	@Test(	description="check the rpm requires list for changes to subscription-manager-migration",
			groups={"blockedByBug-1049037"},
			enabled=true)
	//@ImplementsTCMS(id="")
	public void VerifyRpmRequireListForSubscriptionManagerMigration_Test() {
		String pkg = "subscription-manager-migration";
		if (!clienttasks.isPackageInstalled(pkg)) throw new SkipException("This test require that package '"+pkg+"' be installed.");
		SSHCommandResult sshCommandResult = client.runCommandAndWait("rpm --query --requires "+pkg+" --verbose | egrep '(^manual:|^preun:|^postun:|^post:)'");
		
		List<String> actualRequiresList = new ArrayList<String>();
		for (String requires : Arrays.asList(sshCommandResult.getStdout().trim().split("\\n"))) {
			if (!requires.trim().isEmpty()) actualRequiresList.add(requires.trim());
		}
		
		List<String> expecetdRequiresList = new ArrayList<String>();
		if (clienttasks.redhatReleaseX.equals("5")) { 
			// TODO
		}
		if (clienttasks.redhatReleaseX.equals("6") || clienttasks.redhatReleaseX.equals("7")) {
			expecetdRequiresList.addAll(Arrays.asList(new String[]{
					"manual: subscription-manager = "+clienttasks.installedPackageVersion.get("subscription-manager").replace("subscription-manager-", "").replaceFirst("\\."+clienttasks.arch, ""),	// "manual: subscription-manager = 1.9.11-1.el6"
					"manual: subscription-manager-migration-data",	// Bug 1049037 - subscription-manager-migration should require subscription-manager-migration-data
					"manual: rhnlib"
			}));
		}
		
		for (String expectedRequires : expecetdRequiresList) if (!actualRequiresList.contains(expectedRequires)) log.warning("The actual requires list is missing expected requires '"+expectedRequires+"'.");
		for (String actualRequires : actualRequiresList) if (!expecetdRequiresList.contains(actualRequires)) log.warning("The expected requires list does not include the actual requires '"+actualRequires+"'  Is this a new requirement?");
		Assert.assertTrue(expecetdRequiresList.containsAll(actualRequiresList) && actualRequiresList.containsAll(expecetdRequiresList), "The actual requires list of packages for '"+pkg+"' matches the expected list "+expecetdRequiresList);
	}
	
	
	@Test(	description="check the rpm requires list for changes to subscription-manager-migration-data",
			groups={},
			enabled=true)
	//@ImplementsTCMS(id="")
	public void VerifyRpmRequireListForSubscriptionManagerMigrationData_Test() {
		String pkg = "subscription-manager-migration-data";
		if (!clienttasks.isPackageInstalled(pkg)) throw new SkipException("This test require that package '"+pkg+"' be installed.");
		SSHCommandResult sshCommandResult = client.runCommandAndWait("rpm --query --requires "+pkg+" --verbose | egrep '(^manual:|^preun:|^postun:|^post:)'");
		
		List<String> actualRequiresList = new ArrayList<String>();
		for (String requires : Arrays.asList(sshCommandResult.getStdout().trim().split("\\n"))) {
			if (!requires.trim().isEmpty()) actualRequiresList.add(requires.trim());
		}
		
		List<String> expecetdRequiresList = new ArrayList<String>();
		if (clienttasks.redhatReleaseX.equals("5")) { 
			// TODO
		}
		if (clienttasks.redhatReleaseX.equals("6")) {
			// empty list
		}
		if (clienttasks.redhatReleaseX.equals("7")) {
			// empty list
		}
		
		for (String expectedRequires : expecetdRequiresList) if (!actualRequiresList.contains(expectedRequires)) log.warning("The actual requires list is missing expected requires '"+expectedRequires+"'.");
		for (String actualRequires : actualRequiresList) if (!expecetdRequiresList.contains(actualRequires)) log.warning("The expected requires list does not include the actual requires '"+actualRequires+"'  Is this a new requirement?");
		Assert.assertTrue(expecetdRequiresList.containsAll(actualRequiresList) && actualRequiresList.containsAll(expecetdRequiresList), "The actual requires list of packages for '"+pkg+"' matches the expected list "+expecetdRequiresList);
	}
	
	
	// Candidates for an automated Test:
	// TODO Bug 688469 - subscription-manager <module> --help does not work in localized environment. https://github.com/RedHatQE/rhsm-qe/issues/144
	// TODO Bug 684941 - Deleting a product with a subscription gives ugly error https://github.com/RedHatQE/rhsm-qe/issues/145
	// TODO Bug 629708 - import/export validation error wrapped https://github.com/RedHatQE/rhsm-qe/issues/146
	// TODO Bug 744536 - [ALL LANG] [RHSM CLI] unsubscribe module _unexpected 'ascii' code can't decode ...message. https://github.com/RedHatQE/rhsm-qe/issues/147
	
	
	
	
	
	
	// Data Providers ***********************************************************************
	
	
	
	@DataProvider(name="UnregisteredCommandData")
	public Object[][] getUnregisteredCommandDataAs2dArray() {
		return TestNGUtils.convertListOfListsTo2dArray(getUnregisteredCommandDataAsListOfLists());
	}
	public List<List<Object>> getUnregisteredCommandDataAsListOfLists() {
		List<List<Object>> ll = new ArrayList<List<Object>>();
		if (clienttasks==null) return ll;
		
		//ll.add(Arrays.asList(new Object[]{clienttasks.command+" facts --update"}));  test moved to FactsTests.FactsWhenNotRegistered_Test()
		//ll.add(Arrays.asList(new Object[]{clienttasks.command+" identity"}));  test moved to IdentityTests.IdentityWhenNotRegistered_Test()
		//ll.add(Arrays.asList(new Object[]{clienttasks.command+" list"}));	restriction lifted by https://bugzilla.redhat.com/show_bug.cgi?id=725870
		ll.add(Arrays.asList(new Object[]{clienttasks.command+" list --available --all"}));
		ll.add(Arrays.asList(new Object[]{clienttasks.command+" list --available"}));
		//ll.add(Arrays.asList(new Object[]{clienttasks.command+" list --consumed"}));	restriction lifted by https://bugzilla.redhat.com/show_bug.cgi?id=725870
		ll.add(Arrays.asList(new Object[]{clienttasks.command+" refresh"}));
// this functionality appears to have been removed: subscription-manager-0.71-1.el6.i686  - jsefler 7/21/2010
//		ll.add(Arrays.asList(new Object[]{clienttasks.command+" subscribe --product=FOO"}));
// this functionality appears to have been removed: subscription-manager-0.93.14-1.el6.x86_64 - jsefler 1/21/2011
//		ll.add(Arrays.asList(new Object[]{clienttasks.command+" subscribe --regtoken=FOO"}));
		ll.add(Arrays.asList(new Object[]{clienttasks.command+" subscribe --pool=FOO"}));
// ability to unsubscribe without being registered was added after fix for bug 735338  jsefler 9/13/2011
//		ll.add(Arrays.asList(new Object[]{clienttasks.command+" unsubscribe"}));
//		ll.add(Arrays.asList(new Object[]{clienttasks.command+" unsubscribe --all"}));
//		ll.add(Arrays.asList(new Object[]{clienttasks.command+" unsubscribe --serial=FOO"}));
// this functionality appears to have been removed: subscription-manager-0.68-1.el6.i686  - jsefler 7/12/2010
//		ll.add(Arrays.asList(new Object[]{clienttasks.command+" unsubscribe --product=FOO"}));
//		ll.add(Arrays.asList(new Object[]{clienttasks.command+" unsubscribe --regtoken=FOO"}));
//		ll.add(Arrays.asList(new Object[]{clienttasks.command+" unsubscribe --pool=FOO"}));
		ll.add(Arrays.asList(new Object[]{clienttasks.command+" redeem"}));

		return ll;
	}
	
	@DataProvider(name="NegativeFunctionalityData")
	public Object[][] getNegativeFunctionalityDataAs2dArray() {
		return TestNGUtils.convertListOfListsTo2dArray(getNegativeFunctionalityDataAsListOfLists());
	}
	protected List<List<Object>> getNegativeFunctionalityDataAsListOfLists() {
		List<List<Object>> ll = new ArrayList<List<Object>>();
		if (clienttasks==null) return ll;
		
		// due to design changes, this is a decent place to dump old commands that have been removed
		
		// String command, int expectedExitCode, String expectedStdoutRegex, String expectedStderrRegex
		
		// negative tests that require the system to be unregistered first...
		clienttasks.unregister(null,null,null);
		ll.add(Arrays.asList(new Object[]{null,							clienttasks.command+" unsubscribe --product=FOO",							new Integer(2),		clienttasks.command+": error: no such option: --product", "Usage: subscription-manager unsubscribe [OPTIONS]"}));
		ll.add(Arrays.asList(new Object[]{null,							clienttasks.command+" unsubscribe --regtoken=FOO",							new Integer(2),		clienttasks.command+": error: no such option: --regtoken", "Usage: subscription-manager unsubscribe [OPTIONS]"}));
		ll.add(Arrays.asList(new Object[]{null,							clienttasks.command+" unsubscribe --pool=FOO",								new Integer(2),		clienttasks.command+": error: no such option: --pool", "Usage: subscription-manager unsubscribe [OPTIONS]"}));
		ll.add(Arrays.asList(new Object[]{null,							clienttasks.command+" repo-override --repo",								new Integer(2),		clienttasks.command+": error: --repo option requires an argument",	"Usage: subscription-manager repo-override [OPTIONS]"}));
		ll.add(Arrays.asList(new Object[]{null,							clienttasks.command+" repo-override --remove",								new Integer(2),		clienttasks.command+": error: --remove option requires an argument",	"Usage: subscription-manager repo-override [OPTIONS]"}));
		ll.add(Arrays.asList(new Object[]{null,							clienttasks.command+" repo-override --add",									new Integer(2),		clienttasks.command+": error: --add option requires an argument",	"Usage: subscription-manager repo-override [OPTIONS]"}));
		ll.add(Arrays.asList(new Object[]{null,							clienttasks.command+" repo-override --add=foo",								new Integer(2),		clienttasks.command+": error: --add arguments should be in the form of \"name:value\"",	"Usage: subscription-manager repo-override [OPTIONS]"}));
		ll.add(Arrays.asList(new Object[]{null,							clienttasks.command+" register --servicelevel=foo",							new Integer(255),	"Error: Must use --auto-attach with --servicelevel.", ""}));	// changed by bug 874804,876305		ll.add(Arrays.asList(new Object[]{clienttasks.command+" register --servicelevel=foo",				new Integer(255),	"Error: Must use --autosubscribe with --servicelevel.", ""}));
		ll.add(Arrays.asList(new Object[]{new BlockedByBzBug("856236"),	clienttasks.command+" register --activationkey=foo --org=foo --env=foo",	new Integer(255),	"Error: Activation keys do not allow environments to be specified.", ""}));
		ll.add(Arrays.asList(new Object[]{null,							clienttasks.command+" list --match-installed",								new Integer(255),	"Error: --match-installed is only applicable with --available", ""}));
		ll.add(Arrays.asList(new Object[]{null,							clienttasks.command+" list --no-overlap",									new Integer(255),	"Error: --no-overlap is only applicable with --available", ""}));
		ll.add(Arrays.asList(new Object[]{null,							clienttasks.command+" list --installed --servicelevel=foo",					new Integer(255),	"Error: --servicelevel is only applicable with --available or --consumed", ""}));
		ll.add(Arrays.asList(new Object[]{null,							clienttasks.command+" repo-override --repo=foo",							new Integer(255),	"Error: The --repo option must be used with --list or --add or --remove.",	""}));
		ll.add(Arrays.asList(new Object[]{null,							clienttasks.command+" repo-override --remove=foo",							new Integer(255),	"Error: You must specify a repository to modify",	""}));
		ll.add(Arrays.asList(new Object[]{null,							clienttasks.command+" repo-override --add=foo:bar",							new Integer(255),	"Error: You must specify a repository to modify",	""}));
		ll.add(Arrays.asList(new Object[]{null,							clienttasks.command+" repo-override --list --remove-all",					new Integer(255),	"Error: You may not use --list with --remove-all",	""}));
		ll.add(Arrays.asList(new Object[]{null,							clienttasks.command+" repo-override --remove-all --add=foo:bar --repo=fb",	new Integer(255),	"Error: You may not use --add or --remove with --remove-all and --list",	""}));
		ll.add(Arrays.asList(new Object[]{null,							clienttasks.command+" repo-override --remove-all --remove=foo --repo=fb",	new Integer(255),	"Error: You may not use --add or --remove with --remove-all and --list",	""}));
		ll.add(Arrays.asList(new Object[]{null,							clienttasks.command+" repo-override --list --remove=foo --repo=fb",			new Integer(255),	"Error: You may not use --add or --remove with --remove-all and --list",	""}));
		ll.add(Arrays.asList(new Object[]{null,							clienttasks.command+" repo-override --list --remove=foo --repo=fb",			new Integer(255),	"Error: You may not use --add or --remove with --remove-all and --list",	""}));
		ll.add(Arrays.asList(new Object[]{null,							clienttasks.command+" subscribe",											new Integer(255),	"This system is not yet registered. Try 'subscription-manager register --help' for more information.",	""}));
		ll.add(Arrays.asList(new Object[]{null,							clienttasks.command+" repo-override --remove=foo --repo=foobar",			new Integer(255),	"This system is not yet registered. Try 'subscription-manager register --help' for more information.",	""}));
		ll.add(Arrays.asList(new Object[]{null,							clienttasks.command+" repo-override --add=foo:bar --repo=foobar",			new Integer(255),	"This system is not yet registered. Try 'subscription-manager register --help' for more information.",	""}));
		ll.add(Arrays.asList(new Object[]{null,							clienttasks.command+" repo-override --remove-all",							new Integer(255),	"This system is not yet registered. Try 'subscription-manager register --help' for more information.",	""}));
		ll.add(Arrays.asList(new Object[]{null,							clienttasks.command+" repo-override --list",								new Integer(255),	"This system is not yet registered. Try 'subscription-manager register --help' for more information.",	""}));
		ll.add(Arrays.asList(new Object[]{null,							clienttasks.command+" repo-override",										new Integer(255),	"This system is not yet registered. Try 'subscription-manager register --help' for more information.",	""}));
		ll.add(Arrays.asList(new Object[]{null,							clienttasks.rhsmDebugSystemCommand(null, null, null, null, null),					new Integer(255),	"This system is not yet registered. Try 'subscription-manager register --help' for more information.",	""}));

		
		// negative tests that require the system to be registered before attempting the test...
		ll.add(Arrays.asList(new Object[]{null,							clienttasks.command+" register --username "+sm_clientUsername+" --password "+sm_clientPassword+(sm_clientOrg==null?"":" --org "+sm_clientOrg),									new Integer(0),	null,	""}));
		ll.add(Arrays.asList(new Object[]{null,							clienttasks.command+" subscribe",											new Integer(255),	"Error: This command requires that you specify a pool with --pool or use --auto.",	""}));
		ll.add(Arrays.asList(new Object[]{null,							clienttasks.command+" subscribe --pool=123 --auto",							new Integer(255),	"Error: Only one of --pool or --auto may be used with this command.", ""}));
		ll.add(Arrays.asList(new Object[]{null,							clienttasks.command+" subscribe --pool=123 --servicelevel=foo",				new Integer(255),	"Error: Must use --auto with --servicelevel.", ""}));
		ll.add(Arrays.asList(new Object[]{null,							clienttasks.command+" subscribe --pool=123 --servicelevel=foo",				new Integer(255),	"Error: Must use --auto with --servicelevel.", ""}));

		return ll;
	}
}

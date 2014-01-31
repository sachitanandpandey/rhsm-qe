package rhsm.cli.tests;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterGroups;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeGroups;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.redhat.qe.Assert;
import com.redhat.qe.auto.bugzilla.BlockedByBzBug;
import com.redhat.qe.auto.testng.TestNGUtils;
import com.redhat.qe.jul.TestRecords;
import rhsm.base.CandlepinType;
import rhsm.base.SubscriptionManagerCLITestScript;
import rhsm.data.SubscriptionPool;
import com.redhat.qe.tools.RemoteFileTasks;
import com.redhat.qe.tools.SSHCommandResult;
import com.redhat.qe.tools.SSHCommandRunner;

/**
 * @author jsefler
 *
 * http://gibson.usersys.redhat.com/agilo/ticket/4618
 * automate tests for subscription manager modules with basic and noauth proxy servers
 * 		register
 * 		subscribe
 * 		unsubscribe
 * 		unregister
 * 		clean (https://bugzilla.redhat.com/show_bug.cgi?id=664581)
 * 		facts
 * 		identity
 * 		refresh
 * 		list
 */
@Test(groups={"ProxyTests"})
public class ProxyTests extends SubscriptionManagerCLITestScript {


	// Test methods ***********************************************************************
	
	
	// REGISTER Test methods ***********************************************************************
	
	@Test(	description="subscription-manager : register using a proxy server (Positive and Negative Variations)",
			groups={"AcceptanceTests"},
			dataProvider="getRegisterAttemptsUsingProxyServerData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void RegisterAttemptsUsingProxyServer_Test(Object blockedByBug, String username, String password, String org, String proxy, String proxyuser, String proxypassword, Integer exitCode, String stdout, String stderr) {
		String moduleTask = "register";
		
		SSHCommandResult attemptResult = clienttasks.register_(username, password, org, null, null, null, null, null, null, null, (String)null, null, null, null, null, null, proxy, proxyuser, proxypassword);
		if (exitCode!=null)	Assert.assertEquals(attemptResult.getExitCode(), exitCode, "The exit code from an attempt to "+moduleTask+" using a proxy server.");
		if (stdout!=null)	Assert.assertEquals(attemptResult.getStdout().trim(), stdout, "The stdout from an attempt to "+moduleTask+" using a proxy server.");
		if (stderr!=null)	Assert.assertEquals(attemptResult.getStderr().trim(), stderr, "The stderr from an attempt to "+moduleTask+" using a proxy server.");
	}
	
	
	@Test(	description="subscription-manager : register using a proxy server after setting rhsm.config parameters (Positive and Negative Variations)",
			groups={},
			dataProvider="getRegisterAttemptsUsingProxyServerViaRhsmConfigData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void RegisterAttemptsUsingProxyServerViaRhsmConfig_Test(Object blockedByBug, String username, String password, String org, String proxy, String proxyuser, String proxypassword, String proxy_hostnameConfig, String proxy_portConfig, String proxy_userConfig, String proxy_passwordConfig, Integer exitCode, String stdout, String stderr, SSHCommandRunner proxyRunner, String proxyLog, String proxyLogGrepPattern) {
		String moduleTask = "register";

		// pad the tail of basicauthproxyLog with a message
		String proxyLogMarker = System.currentTimeMillis()+" Testing "+moduleTask+" AttemptsUsingProxyServerViaRhsmConfig_Test from "+clienttasks.hostname+"...";
		//RemoteFileTasks.runCommandAndAssert(proxyRunner,"echo '"+proxyLogMarker+"'  >> "+proxyLog, Integer.valueOf(0));
		RemoteFileTasks.markFile(proxyRunner, proxyLog, proxyLogMarker);
		
		// set the config parameters
		updateConfFileProxyParameters(proxy_hostnameConfig, proxy_portConfig, proxy_userConfig, proxy_passwordConfig);
		RemoteFileTasks.runCommandAndWait(client,"grep proxy "+clienttasks.rhsmConfFile,TestRecords.action());

		// attempt to register
		SSHCommandResult attemptResult = clienttasks.register_(username, password, org, null, null, null, null, null, null, null, (String)null, null, null, null, null, null, proxy, proxyuser, proxypassword);
		if (exitCode!=null)	Assert.assertEquals(attemptResult.getExitCode(), exitCode, "The exit code from a negative attempt to "+moduleTask+" using a proxy server.");
		if (stdout!=null)	Assert.assertEquals(attemptResult.getStdout().trim(), stdout, "The stdout from an attempt to "+moduleTask+" using a proxy server.");
		if (stderr!=null)	Assert.assertEquals(attemptResult.getStderr().trim(), stderr, "The stderr from an attempt to "+moduleTask+" using a proxy server.");

		// assert the tail of proxyLog shows the proxyLogGrepPattern (BASIC AUTH)
		// 1292545301.350    418 10.16.120.247 TCP_MISS/200 1438 CONNECT jsefler-f12-candlepin.usersys.redhat.com:8443 redhat DIRECT/10.16.120.146 -
		// 1292551602.625      0 10.16.120.247 TCP_DENIED/407 3840 CONNECT jsefler-f12-candlepin.usersys.redhat.com:8443 - NONE/- text/html

		// assert the tail of proxyLog shows the proxyLogGrepPattern (NO AUTH)
		// CONNECT   Dec 17 18:56:22 [20793]: Connect (file descriptor 7):  [10.16.120.248]
		// CONNECT   Dec 17 18:56:22 [20793]: Request (file descriptor 7): CONNECT jsefler-f12-candlepin.usersys.redhat.com:8443 HTTP/1.1
		// INFO      Dec 17 18:56:22 [20793]: No proxy for jsefler-f12-candlepin.usersys.redhat.com
		// CONNECT   Dec 17 18:56:22 [20793]: Established connection to host "jsefler-f12-candlepin.usersys.redhat.com" using file descriptor 8.
		// INFO      Dec 17 18:56:22 [20793]: Not sending client headers to remote machine
		// INFO      Dec 17 18:56:22 [20793]: Closed connection between local client (fd:7) and remote client (fd:8)
		
		if (proxyLogGrepPattern!=null) {
			//SSHCommandResult proxyLogResult = RemoteFileTasks.runCommandAndAssert(proxyRunner,"tail -1 "+proxyLog, Integer.valueOf(0));
			//SSHCommandResult proxyLogResult = proxyRunner.runCommandAndWait("(LINES=''; IFS=$'\n'; for line in $(tac "+proxyLog+"); do if [[ $line = '"+proxyLogMarker+"' ]]; then break; fi; LINES=${LINES}'\n'$line; done; echo -e $LINES) | grep "+clienttasks.ipaddr);	// accounts for multiple tests hitting the same proxy simultaneously
			//Assert.assertContainsMatch(proxyLogResult.getStdout(), proxyLogGrepPattern, "The proxy server appears to be logging the expected connection attempts to the candlepin server.");
			String proxyLogResult = RemoteFileTasks.getTailFromMarkedFile(proxyRunner, proxyLog, proxyLogMarker, clienttasks.ipaddr);	// accounts for multiple tests hitting the same proxy server simultaneously
			//Assert.assertContainsMatch(proxyLogResult, proxyLogGrepPattern, "The proxy server appears to be logging the expected connection attempts to the candlepin server.");	// TOO MUCH LOGGING
			Assert.assertTrue(proxyLogResult.contains(proxyLogGrepPattern), "The tail of proxy server log '"+proxyLog+"' following marker '"+proxyLogMarker+"' contains expected connection '"+proxyLogGrepPattern+"' attempts from "+clienttasks.ipaddr+" to the candlepin server.");
		}
	}
	
	
	
	
	// UNREGISTER Test methods ***********************************************************************

	@Test(	description="subscription-manager : unregister using a proxy server (Positive and Negative Variations)",
			groups={},
			dataProvider="getUnregisterAttemptsUsingProxyServerData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void UnregisterAttemptsUsingProxyServer_Test(Object blockedByBug, String username, String password, String org, String proxy, String proxyuser, String proxypassword, Integer exitCode, String stdout, String stderr) {
		// setup for test
		String moduleTask = "unregister";
		if (!username.equals(sm_clientUsername) || !password.equals(sm_clientPassword)) throw new SkipException("These dataProvided parameters are either superfluous or not meaningful for this test.");
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, null, false, null, null, null);
		
		SSHCommandResult attemptResult = clienttasks.unregister_(proxy, proxyuser, proxypassword);
		if (exitCode!=null)	Assert.assertEquals(attemptResult.getExitCode(), exitCode, "The exit code from an attempt to "+moduleTask+" using a proxy server.");
		if (stdout!=null)	Assert.assertEquals(attemptResult.getStdout().trim(), stdout, "The stdout from an attempt to "+moduleTask+" using a proxy server.");
		if (stderr!=null)	Assert.assertEquals(attemptResult.getStderr().trim(), stderr, "The stderr from an attempt to "+moduleTask+" using a proxy server.");
	}
	
	
	@Test(	description="subscription-manager : unregister using a proxy server after setting rhsm.config parameters (Positive and Negative Variations)",
			groups={},
			dataProvider="getUnregisterAttemptsUsingProxyServerViaRhsmConfigData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void UnregisterAttemptsUsingProxyServerViaRhsmConfig_Test(Object blockedByBug, String username, String password, String org, String proxy, String proxyuser, String proxypassword, String proxy_hostnameConfig, String proxy_portConfig, String proxy_userConfig, String proxy_passwordConfig, Integer exitCode, String stdout, String stderr, SSHCommandRunner proxyRunner, String proxyLog, String proxyLogGrepPattern) {
		// setup for test
		String moduleTask = "unregister";
		if (!username.equals(sm_clientUsername) || !password.equals(sm_clientPassword)) throw new SkipException("These dataProvided parameters are either superfluous or not meaningful for this test.");
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, null, false, null, null, null);
	
		// pad the tail of basicauthproxyLog with a message
		String proxyLogMarker = System.currentTimeMillis()+" Testing "+moduleTask+" AttemptsUsingProxyServerViaRhsmConfig_Test from "+clienttasks.hostname+"...";
		//RemoteFileTasks.runCommandAndAssert(proxyRunner,"echo '"+proxyLogMarker+"'  >> "+proxyLog, Integer.valueOf(0));
		RemoteFileTasks.markFile(proxyRunner, proxyLog, proxyLogMarker);

		// set the config parameters
		updateConfFileProxyParameters(proxy_hostnameConfig, proxy_portConfig, proxy_userConfig, proxy_passwordConfig);
		RemoteFileTasks.runCommandAndWait(client,"grep proxy "+clienttasks.rhsmConfFile,TestRecords.action());

		// attempt the moduleTask with the proxy options
		SSHCommandResult attemptResult = clienttasks.unregister_(proxy, proxyuser, proxypassword);
		if (exitCode!=null)	Assert.assertEquals(attemptResult.getExitCode(), exitCode, "The exit code from an attempt to "+moduleTask+" using a proxy server.");
		if (stdout!=null)	Assert.assertEquals(attemptResult.getStdout().trim(), stdout, "The stdout from an attempt to "+moduleTask+" using a proxy server.");
		if (stderr!=null)	Assert.assertEquals(attemptResult.getStderr().trim(), stderr, "The stderr from an attempt to "+moduleTask+" using a proxy server.");

		// assert the tail of proxyLog shows the proxyLogGrepPattern
		if (proxyLogGrepPattern!=null) {
			//SSHCommandResult proxyLogResult = RemoteFileTasks.runCommandAndAssert(proxyRunner,"tail -1 "+proxyLog, Integer.valueOf(0));
			//SSHCommandResult proxyLogResult = proxyRunner.runCommandAndWait("(LINES=''; IFS=$'\n'; for line in $(tac "+proxyLog+"); do if [[ $line = '"+proxyLogMarker+"' ]]; then break; fi; LINES=${LINES}'\n'$line; done; echo -e $LINES) | grep "+clienttasks.ipaddr);	// accounts for multiple tests hitting the same proxy simultaneously
			//Assert.assertContainsMatch(proxyLogResult.getStdout(), proxyLogGrepPattern, "The proxy server appears to be logging the expected connection attempts to the candlepin server.");
			String proxyLogResult = RemoteFileTasks.getTailFromMarkedFile(proxyRunner, proxyLog, proxyLogMarker, clienttasks.ipaddr);	// accounts for multiple tests hitting the same proxy server simultaneously
			//Assert.assertContainsMatch(proxyLogResult, proxyLogGrepPattern, "The proxy server appears to be logging the expected connection attempts to the candlepin server.");	// TOO MUCH LOGGING
			Assert.assertTrue(proxyLogResult.contains(proxyLogGrepPattern), "The tail of proxy server log '"+proxyLog+"' following marker '"+proxyLogMarker+"' contains expected connection '"+proxyLogGrepPattern+"' attempts from "+clienttasks.ipaddr+" to the candlepin server.");
		}
	}
	
	
	
	// IDENTITY Test methods ***********************************************************************

	@Test(	description="subscription-manager : identity using a proxy server (Positive and Negative Variations)",
			groups={},
			dataProvider="getIdentityAttemptsUsingProxyServerData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void IdentityAttemptsUsingProxyServer_Test(Object blockedByBug, String username, String password, String org, String proxy, String proxyuser, String proxypassword, Integer exitCode, String stdout, String stderr) {
		// setup for test
		String moduleTask = "identity";
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, null, false, null, null, null);
		
		SSHCommandResult attemptResult = clienttasks.identity_(username, password, Boolean.TRUE, Boolean.TRUE, proxy, proxyuser, proxypassword);
		if (exitCode!=null)	Assert.assertEquals(attemptResult.getExitCode(), exitCode, "The exit code from an attempt to "+moduleTask+" using a proxy server.");
		if (stdout!=null)	Assert.assertEquals(attemptResult.getStdout().trim(), stdout, "The stdout from an attempt to "+moduleTask+" using a proxy server.");
		if (stderr!=null)	Assert.assertEquals(attemptResult.getStderr().trim(), stderr, "The stderr from an attempt to "+moduleTask+" using a proxy server.");
	}

	
	@Test(	description="subscription-manager : identity using a proxy server after setting rhsm.config parameters (Positive and Negative Variations)",
			groups={},
			dataProvider="getIdentityAttemptsUsingProxyServerViaRhsmConfigData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void IdentityAttemptsUsingProxyServerViaRhsmConfig_Test(Object blockedByBug, String username, String password, String org, String proxy, String proxyuser, String proxypassword, String proxy_hostnameConfig, String proxy_portConfig, String proxy_userConfig, String proxy_passwordConfig, Integer exitCode, String stdout, String stderr, SSHCommandRunner proxyRunner, String proxyLog, String proxyLogGrepPattern) {
		// setup for test
		String moduleTask = "identity";
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, null, false, null, null, null);
	
		// pad the tail of basicauthproxyLog with a message
		String proxyLogMarker = System.currentTimeMillis()+" Testing "+moduleTask+" AttemptsUsingProxyServerViaRhsmConfig_Test from "+clienttasks.hostname+"...";
		//RemoteFileTasks.runCommandAndAssert(proxyRunner,"echo '"+proxyLogMarker+"'  >> "+proxyLog, Integer.valueOf(0));
		RemoteFileTasks.markFile(proxyRunner, proxyLog, proxyLogMarker);
		
		// set the config parameters
		updateConfFileProxyParameters(proxy_hostnameConfig, proxy_portConfig, proxy_userConfig, proxy_passwordConfig);
		RemoteFileTasks.runCommandAndWait(client,"grep proxy "+clienttasks.rhsmConfFile,TestRecords.action());

		// attempt the moduleTask with the proxy options
		SSHCommandResult attemptResult = clienttasks.identity_(username, password, Boolean.TRUE, Boolean.TRUE, proxy, proxyuser, proxypassword);
		if (exitCode!=null)	Assert.assertEquals(attemptResult.getExitCode(), exitCode, "The exit code from an attempt to "+moduleTask+" using a proxy server.");
		if (stdout!=null)	Assert.assertEquals(attemptResult.getStdout().trim(), stdout, "The stdout from an attempt to "+moduleTask+" using a proxy server.");
		if (stderr!=null)	Assert.assertEquals(attemptResult.getStderr().trim(), stderr, "The stderr from an attempt to "+moduleTask+" using a proxy server.");

		// assert the tail of proxyLog shows the proxyLogGrepPattern
		if (proxyLogGrepPattern!=null) {
			//SSHCommandResult proxyLogResult = RemoteFileTasks.runCommandAndAssert(proxyRunner,"tail -1 "+proxyLog, Integer.valueOf(0));
			//SSHCommandResult proxyLogResult = proxyRunner.runCommandAndWait("(LINES=''; IFS=$'\n'; for line in $(tac "+proxyLog+"); do if [[ $line = '"+proxyLogMarker+"' ]]; then break; fi; LINES=${LINES}'\n'$line; done; echo -e $LINES) | grep "+clienttasks.ipaddr);	// accounts for multiple tests hitting the same proxy simultaneously
			//Assert.assertContainsMatch(proxyLogResult.getStdout(), proxyLogGrepPattern, "The proxy server appears to be logging the expected connection attempts to the candlepin server.");
			String proxyLogResult = RemoteFileTasks.getTailFromMarkedFile(proxyRunner, proxyLog, proxyLogMarker, clienttasks.ipaddr);	// accounts for multiple tests hitting the same proxy server simultaneously
			//Assert.assertContainsMatch(proxyLogResult, proxyLogGrepPattern, "The proxy server appears to be logging the expected connection attempts to the candlepin server.");	// TOO MUCH LOGGING
			Assert.assertTrue(proxyLogResult.contains(proxyLogGrepPattern), "The tail of proxy server log '"+proxyLog+"' following marker '"+proxyLogMarker+"' contains expected connection '"+proxyLogGrepPattern+"' attempts from "+clienttasks.ipaddr+" to the candlepin server.");
		}
	}
	
	
	
	// ORGS Test methods ***********************************************************************

	@Test(	description="subscription-manager : orgs using a proxy server (Positive and Negative Variations)",
			groups={},
			dataProvider="getOrgsAttemptsUsingProxyServerData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void OrgsAttemptsUsingProxyServer_Test(Object blockedByBug, String username, String password, String org, String proxy, String proxyuser, String proxypassword, Integer exitCode, String stdout, String stderr) {
		// setup for test
		String moduleTask = "orgs";
		
		SSHCommandResult attemptResult = clienttasks.orgs_(username, password, null, null, proxy, proxyuser, proxypassword);
		if (exitCode!=null)	Assert.assertEquals(attemptResult.getExitCode(), exitCode, "The exit code from an attempt to "+moduleTask+" using a proxy server.");
		if (stdout!=null)	Assert.assertEquals(attemptResult.getStdout().trim(), stdout, "The stdout from an attempt to "+moduleTask+" using a proxy server.");
		if (stderr!=null)	Assert.assertEquals(attemptResult.getStderr().trim(), stderr, "The stderr from an attempt to "+moduleTask+" using a proxy server.");
	}

	
	@Test(	description="subscription-manager : orgs using a proxy server after setting rhsm.config parameters (Positive and Negative Variations)",
			groups={},
			dataProvider="getOrgsAttemptsUsingProxyServerViaRhsmConfigData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void OrgsAttemptsUsingProxyServerViaRhsmConfig_Test(Object blockedByBug, String username, String password, String org, String proxy, String proxyuser, String proxypassword, String proxy_hostnameConfig, String proxy_portConfig, String proxy_userConfig, String proxy_passwordConfig, Integer exitCode, String stdout, String stderr, SSHCommandRunner proxyRunner, String proxyLog, String proxyLogGrepPattern) {
		// setup for test
		String moduleTask = "orgs";
	
		// pad the tail of basicauthproxyLog with a message
		String proxyLogMarker = System.currentTimeMillis()+" Testing "+moduleTask+" AttemptsUsingProxyServerViaRhsmConfig_Test from "+clienttasks.hostname+"...";
		//RemoteFileTasks.runCommandAndAssert(proxyRunner,"echo '"+proxyLogMarker+"'  >> "+proxyLog, Integer.valueOf(0));
		RemoteFileTasks.markFile(proxyRunner, proxyLog, proxyLogMarker);
		
		// set the config parameters
		updateConfFileProxyParameters(proxy_hostnameConfig, proxy_portConfig, proxy_userConfig, proxy_passwordConfig);
		RemoteFileTasks.runCommandAndWait(client,"grep proxy "+clienttasks.rhsmConfFile,TestRecords.action());

		// attempt the moduleTask with the proxy options
		SSHCommandResult attemptResult = clienttasks.orgs_(username, password, null, null, proxy, proxyuser, proxypassword);
		if (exitCode!=null)	Assert.assertEquals(attemptResult.getExitCode(), exitCode, "The exit code from an attempt to "+moduleTask+" using a proxy server.");
		if (stdout!=null)	Assert.assertEquals(attemptResult.getStdout().trim(), stdout, "The stdout from an attempt to "+moduleTask+" using a proxy server.");
		if (stderr!=null)	Assert.assertEquals(attemptResult.getStderr().trim(), stderr, "The stderr from an attempt to "+moduleTask+" using a proxy server.");

		// assert the tail of proxyLog shows the proxyLogGrepPattern
		if (proxyLogGrepPattern!=null) {
			//SSHCommandResult proxyLogResult = RemoteFileTasks.runCommandAndAssert(proxyRunner,"tail -1 "+proxyLog, Integer.valueOf(0));
			//SSHCommandResult proxyLogResult = proxyRunner.runCommandAndWait("(LINES=''; IFS=$'\n'; for line in $(tac "+proxyLog+"); do if [[ $line = '"+proxyLogMarker+"' ]]; then break; fi; LINES=${LINES}'\n'$line; done; echo -e $LINES) | grep "+clienttasks.ipaddr);	// accounts for multiple tests hitting the same proxy simultaneously
			//Assert.assertContainsMatch(proxyLogResult.getStdout(), proxyLogGrepPattern, "The proxy server appears to be logging the expected connection attempts to the candlepin server.");
			String proxyLogResult = RemoteFileTasks.getTailFromMarkedFile(proxyRunner, proxyLog, proxyLogMarker, clienttasks.ipaddr);	// accounts for multiple tests hitting the same proxy server simultaneously
			//Assert.assertContainsMatch(proxyLogResult, proxyLogGrepPattern, "The proxy server appears to be logging the expected connection attempts to the candlepin server.");	// TOO MUCH LOGGING
			Assert.assertTrue(proxyLogResult.contains(proxyLogGrepPattern), "The tail of proxy server log '"+proxyLog+"' following marker '"+proxyLogMarker+"' contains expected connection '"+proxyLogGrepPattern+"' attempts from "+clienttasks.ipaddr+" to the candlepin server.");
		}
	}
	
	
	
	// SERVICE-LEVEL Test methods ***********************************************************************

	@Test(	description="subscription-manager : service-level using a proxy server (Positive and Negative Variations)",
			groups={},
			dataProvider="getServiceLevelAttemptsUsingProxyServerData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void ServiceLevelAttemptsUsingProxyServer_Test(Object blockedByBug, String username, String password, String org, String proxy, String proxyuser, String proxypassword, Integer exitCode, String stdout, String stderr) {
		// setup for test
		String moduleTask = "service-level";
		
		SSHCommandResult attemptResult = clienttasks.service_level_(null, true, null, null, username, password, org, null, null, proxy, proxyuser, proxypassword);
		if (exitCode!=null)	Assert.assertEquals(attemptResult.getExitCode(), exitCode, "The exit code from an attempt to "+moduleTask+" using a proxy server.");
		if (stdout!=null)	Assert.assertEquals(attemptResult.getStdout().trim(), stdout, "The stdout from an attempt to "+moduleTask+" using a proxy server.");
		if (stderr!=null)	Assert.assertEquals(attemptResult.getStderr().trim(), stderr, "The stderr from an attempt to "+moduleTask+" using a proxy server.");
	}

	
	@Test(	description="subscription-manager : service-level using a proxy server after setting rhsm.config parameters (Positive and Negative Variations)",
			groups={},
			dataProvider="getServiceLevelAttemptsUsingProxyServerViaRhsmConfigData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void ServiceLevelAttemptsUsingProxyServerViaRhsmConfig_Test(Object blockedByBug, String username, String password, String org, String proxy, String proxyuser, String proxypassword, String proxy_hostnameConfig, String proxy_portConfig, String proxy_userConfig, String proxy_passwordConfig, Integer exitCode, String stdout, String stderr, SSHCommandRunner proxyRunner, String proxyLog, String proxyLogGrepPattern) {
		// setup for test
		String moduleTask = "service-level";
	
		// pad the tail of basicauthproxyLog with a message
		String proxyLogMarker = System.currentTimeMillis()+" Testing "+moduleTask+" AttemptsUsingProxyServerViaRhsmConfig_Test from "+clienttasks.hostname+"...";
		//RemoteFileTasks.runCommandAndAssert(proxyRunner,"echo '"+proxyLogMarker+"'  >> "+proxyLog, Integer.valueOf(0));
		RemoteFileTasks.markFile(proxyRunner, proxyLog, proxyLogMarker);
		
		// set the config parameters
		updateConfFileProxyParameters(proxy_hostnameConfig, proxy_portConfig, proxy_userConfig, proxy_passwordConfig);
		RemoteFileTasks.runCommandAndWait(client,"grep proxy "+clienttasks.rhsmConfFile,TestRecords.action());

		// attempt the moduleTask with the proxy options
		SSHCommandResult attemptResult = clienttasks.service_level_(null, true, null, null, username, password, org, null, null, proxy, proxyuser, proxypassword);
		if (exitCode!=null)	Assert.assertEquals(attemptResult.getExitCode(), exitCode, "The exit code from an attempt to "+moduleTask+" using a proxy server.");
		if (stdout!=null)	Assert.assertEquals(attemptResult.getStdout().trim(), stdout, "The stdout from an attempt to "+moduleTask+" using a proxy server.");
		if (stderr!=null)	Assert.assertEquals(attemptResult.getStderr().trim(), stderr, "The stderr from an attempt to "+moduleTask+" using a proxy server.");

		// assert the tail of proxyLog shows the proxyLogGrepPattern
		if (proxyLogGrepPattern!=null) {
			//SSHCommandResult proxyLogResult = RemoteFileTasks.runCommandAndAssert(proxyRunner,"tail -1 "+proxyLog, Integer.valueOf(0));
			//SSHCommandResult proxyLogResult = proxyRunner.runCommandAndWait("(LINES=''; IFS=$'\n'; for line in $(tac "+proxyLog+"); do if [[ $line = '"+proxyLogMarker+"' ]]; then break; fi; LINES=${LINES}'\n'$line; done; echo -e $LINES) | grep "+clienttasks.ipaddr);	// accounts for multiple tests hitting the same proxy simultaneously
			//Assert.assertContainsMatch(proxyLogResult.getStdout(), proxyLogGrepPattern, "The proxy server appears to be logging the expected connection attempts to the candlepin server.");
			String proxyLogResult = RemoteFileTasks.getTailFromMarkedFile(proxyRunner, proxyLog, proxyLogMarker, clienttasks.ipaddr);	// accounts for multiple tests hitting the same proxy server simultaneously
			//Assert.assertContainsMatch(proxyLogResult, proxyLogGrepPattern, "The proxy server appears to be logging the expected connection attempts to the candlepin server.");	// TOO MUCH LOGGING
			Assert.assertTrue(proxyLogResult.contains(proxyLogGrepPattern), "The tail of proxy server log '"+proxyLog+"' following marker '"+proxyLogMarker+"' contains expected connection '"+proxyLogGrepPattern+"' attempts from "+clienttasks.ipaddr+" to the candlepin server.");
		}
	}
	
	
	
	// ENVIRONMENTS Test methods ***********************************************************************

	@Test(	description="subscription-manager : environments using a proxy server (Positive and Negative Variations)",
			groups={"blockedByBug-728380"},
			dataProvider="getEnvironmentsAttemptsUsingProxyServerData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void EnvironmentsAttemptsUsingProxyServer_Test(Object blockedByBug, String username, String password, String org, String proxy, String proxyuser, String proxypassword, Integer exitCode, String stdout, String stderr) {
		// setup for test
		String moduleTask = "environments";
		
		SSHCommandResult attemptResult = clienttasks.environments_(username, password, org, null, null, proxy, proxyuser, proxypassword);
		if (exitCode!=null)	Assert.assertEquals(attemptResult.getExitCode(), exitCode, "The exit code from an attempt to "+moduleTask+" using a proxy server.");
		if (stdout!=null)	Assert.assertEquals(attemptResult.getStdout().trim(), stdout, "The stdout from an attempt to "+moduleTask+" using a proxy server.");
		if (stderr!=null)	Assert.assertEquals(attemptResult.getStderr().trim(), stderr, "The stderr from an attempt to "+moduleTask+" using a proxy server.");
	}

	
	@Test(	description="subscription-manager : environments using a proxy server after setting rhsm.config parameters (Positive and Negative Variations)",
			groups={"blockedByBug-728380"},
			dataProvider="getEnvironmentsAttemptsUsingProxyServerViaRhsmConfigData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void EnvironmentsAttemptsUsingProxyServerViaRhsmConfig_Test(Object blockedByBug, String username, String password, String org, String proxy, String proxyuser, String proxypassword, String proxy_hostnameConfig, String proxy_portConfig, String proxy_userConfig, String proxy_passwordConfig, Integer exitCode, String stdout, String stderr, SSHCommandRunner proxyRunner, String proxyLog, String proxyLogGrepPattern) {
		// setup for test
		String moduleTask = "environments";
	
		// pad the tail of basicauthproxyLog with a message
		String proxyLogMarker = System.currentTimeMillis()+" Testing "+moduleTask+" AttemptsUsingProxyServerViaRhsmConfig_Test from "+clienttasks.hostname+"...";
		//RemoteFileTasks.runCommandAndAssert(proxyRunner,"echo '"+proxyLogMarker+"'  >> "+proxyLog, Integer.valueOf(0));
		RemoteFileTasks.markFile(proxyRunner, proxyLog, proxyLogMarker);
		
		// set the config parameters
		updateConfFileProxyParameters(proxy_hostnameConfig, proxy_portConfig, proxy_userConfig, proxy_passwordConfig);
		RemoteFileTasks.runCommandAndWait(client,"grep proxy "+clienttasks.rhsmConfFile,TestRecords.action());

		// attempt the moduleTask with the proxy options
		SSHCommandResult attemptResult = clienttasks.environments_(username, password, org, null, null, proxy, proxyuser, proxypassword);
		if (exitCode!=null)	Assert.assertEquals(attemptResult.getExitCode(), exitCode, "The exit code from an attempt to "+moduleTask+" using a proxy server.");
		if (stdout!=null)	Assert.assertEquals(attemptResult.getStdout().trim(), stdout, "The stdout from an attempt to "+moduleTask+" using a proxy server.");
		if (stderr!=null)	Assert.assertEquals(attemptResult.getStderr().trim(), stderr, "The stderr from an attempt to "+moduleTask+" using a proxy server.");

		// assert the tail of proxyLog shows the proxyLogGrepPattern
		if (proxyLogGrepPattern!=null) {
			//SSHCommandResult proxyLogResult = RemoteFileTasks.runCommandAndAssert(proxyRunner,"tail -1 "+proxyLog, Integer.valueOf(0));
			//SSHCommandResult proxyLogResult = proxyRunner.runCommandAndWait("(LINES=''; IFS=$'\n'; for line in $(tac "+proxyLog+"); do if [[ $line = '"+proxyLogMarker+"' ]]; then break; fi; LINES=${LINES}'\n'$line; done; echo -e $LINES) | grep "+clienttasks.ipaddr);	// accounts for multiple tests hitting the same proxy simultaneously
			//Assert.assertContainsMatch(proxyLogResult.getStdout(), proxyLogGrepPattern, "The proxy server appears to be logging the expected connection attempts to the candlepin server.");
			String proxyLogResult = RemoteFileTasks.getTailFromMarkedFile(proxyRunner, proxyLog, proxyLogMarker, clienttasks.ipaddr);	// accounts for multiple tests hitting the same proxy server simultaneously
			//Assert.assertContainsMatch(proxyLogResult, proxyLogGrepPattern, "The proxy server appears to be logging the expected connection attempts to the candlepin server.");	// TOO MUCH LOGGING
			Assert.assertTrue(proxyLogResult.contains(proxyLogGrepPattern), "The tail of proxy server log '"+proxyLog+"' following marker '"+proxyLogMarker+"' contains expected connection '"+proxyLogGrepPattern+"' attempts from "+clienttasks.ipaddr+" to the candlepin server.");
		}
	}
	
	
	
	// LIST Test methods ***********************************************************************

	@Test(	description="subscription-manager : list using a proxy server (Positive and Negative Variations)",
			groups={},
			dataProvider="getListAttemptsUsingProxyServerData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void ListAttemptsUsingProxyServer_Test(Object blockedByBug, String username, String password, String org, String proxy, String proxyuser, String proxypassword, Integer exitCode, String stdout, String stderr) {
		// setup for test
		String moduleTask = "list";
		if (!username.equals(sm_clientUsername) || !password.equals(sm_clientPassword)) throw new SkipException("These dataProvided parameters are either superfluous or not meaningful for this test.");
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, null, false, null, null, null);
		
		SSHCommandResult attemptResult = clienttasks.list_(null,Boolean.TRUE,null,null,null, null, null, null, proxy, proxyuser, proxypassword);
		if (exitCode!=null)	Assert.assertEquals(attemptResult.getExitCode(), exitCode, "The exit code from an attempt to "+moduleTask+" using a proxy server.");
		if (stdout!=null)	Assert.assertEquals(attemptResult.getStdout().trim(), stdout, "The stdout from an attempt to "+moduleTask+" using a proxy server.");
		if (stderr!=null)	Assert.assertEquals(attemptResult.getStderr().trim(), stderr, "The stderr from an attempt to "+moduleTask+" using a proxy server.");
	}

	
	@Test(	description="subscription-manager : list using a proxy server after setting rhsm.config parameters (Positive and Negative Variations)",
			groups={},
			dataProvider="getListAttemptsUsingProxyServerViaRhsmConfigData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void ListAttemptsUsingProxyServerViaRhsmConfig_Test(Object blockedByBug, String username, String password, String org, String proxy, String proxyuser, String proxypassword, String proxy_hostnameConfig, String proxy_portConfig, String proxy_userConfig, String proxy_passwordConfig, Integer exitCode, String stdout, String stderr, SSHCommandRunner proxyRunner, String proxyLog, String proxyLogGrepPattern) {
		// setup for test
		String moduleTask = "list";
		if (!username.equals(sm_clientUsername) || !password.equals(sm_clientPassword)) throw new SkipException("These dataProvided parameters are either superfluous or not meaningful for this test.");
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, null, false, null, null, null);
	
		// pad the tail of basicauthproxyLog with a message
		String proxyLogMarker = System.currentTimeMillis()+" Testing "+moduleTask+" AttemptsUsingProxyServerViaRhsmConfig_Test from "+clienttasks.hostname+"...";
		//RemoteFileTasks.runCommandAndAssert(proxyRunner,"echo '"+proxyLogMarker+"'  >> "+proxyLog, Integer.valueOf(0));
		RemoteFileTasks.markFile(proxyRunner, proxyLog, proxyLogMarker);
		
		// set the config parameters
		updateConfFileProxyParameters(proxy_hostnameConfig, proxy_portConfig, proxy_userConfig, proxy_passwordConfig);
		RemoteFileTasks.runCommandAndWait(client,"grep proxy "+clienttasks.rhsmConfFile,TestRecords.action());

		// attempt the moduleTask with the proxy options
		SSHCommandResult attemptResult = clienttasks.list_(null,Boolean.TRUE,null,null,null, null, null, null, proxy, proxyuser, proxypassword);
		if (exitCode!=null)	Assert.assertEquals(attemptResult.getExitCode(), exitCode, "The exit code from an attempt to "+moduleTask+" using a proxy server.");
		if (stdout!=null)	Assert.assertEquals(attemptResult.getStdout().trim(), stdout, "The stdout from an attempt to "+moduleTask+" using a proxy server.");
		if (stderr!=null)	Assert.assertEquals(attemptResult.getStderr().trim(), stderr, "The stderr from an attempt to "+moduleTask+" using a proxy server.");

		// assert the tail of proxyLog shows the proxyLogGrepPattern
		if (proxyLogGrepPattern!=null) {
			//SSHCommandResult proxyLogResult = RemoteFileTasks.runCommandAndAssert(proxyRunner,"tail -1 "+proxyLog, Integer.valueOf(0));
			//SSHCommandResult proxyLogResult = proxyRunner.runCommandAndWait("(LINES=''; IFS=$'\n'; for line in $(tac "+proxyLog+"); do if [[ $line = '"+proxyLogMarker+"' ]]; then break; fi; LINES=${LINES}'\n'$line; done; echo -e $LINES) | grep "+clienttasks.ipaddr);	// accounts for multiple tests hitting the same proxy simultaneously
			//Assert.assertContainsMatch(proxyLogResult.getStdout(), proxyLogGrepPattern, "The proxy server appears to be logging the expected connection attempts to the candlepin server.");
			String proxyLogResult = RemoteFileTasks.getTailFromMarkedFile(proxyRunner, proxyLog, proxyLogMarker, clienttasks.ipaddr);	// accounts for multiple tests hitting the same proxy server simultaneously
			//Assert.assertContainsMatch(proxyLogResult, proxyLogGrepPattern, "The proxy server appears to be logging the expected connection attempts to the candlepin server.");	// TOO MUCH LOGGING
			Assert.assertTrue(proxyLogResult.contains(proxyLogGrepPattern), "The tail of proxy server log '"+proxyLog+"' following marker '"+proxyLogMarker+"' contains expected connection '"+proxyLogGrepPattern+"' attempts from "+clienttasks.ipaddr+" to the candlepin server.");
		}
	}
	
	
	
	// RELEASE Test methods ***********************************************************************

	@Test(	description="subscription-manager : release using a proxy server (Positive and Negative Variations)",
			groups={},
			dataProvider="getReleaseAttemptsUsingProxyServerData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void ReleaseAttemptsUsingProxyServer_Test(Object blockedByBug, String username, String password, String org, String proxy, String proxyuser, String proxypassword, Integer exitCode, String stdout, String stderr) {
		// setup for test
		String moduleTask = "release";
		if (!username.equals(sm_clientUsername) || !password.equals(sm_clientPassword)) throw new SkipException("These dataProvided parameters are either superfluous or not meaningful for this test.");
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, null, false, null, null, null);
		
		SSHCommandResult attemptResult = clienttasks.release_(null, null, null, null, proxy, proxyuser, proxypassword);
		if (exitCode!=null)	Assert.assertEquals(attemptResult.getExitCode(), exitCode, "The exit code from an attempt to "+moduleTask+" using a proxy server.");
		if (stdout!=null)	Assert.assertEquals(attemptResult.getStdout().trim(), stdout, "The stdout from an attempt to "+moduleTask+" using a proxy server.");
		if (stderr!=null)	Assert.assertEquals(attemptResult.getStderr().trim(), stderr, "The stderr from an attempt to "+moduleTask+" using a proxy server.");
	}
	
	
	@Test(	description="subscription-manager : release using a proxy server after setting rhsm.config parameters (Positive and Negative Variations)",
			groups={},
			dataProvider="getReleaseAttemptsUsingProxyServerViaRhsmConfigData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void ReleaseAttemptsUsingProxyServerViaRhsmConfig_Test(Object blockedByBug, String username, String password, String org, String proxy, String proxyuser, String proxypassword, String proxy_hostnameConfig, String proxy_portConfig, String proxy_userConfig, String proxy_passwordConfig, Integer exitCode, String stdout, String stderr, SSHCommandRunner proxyRunner, String proxyLog, String proxyLogGrepPattern) {
		// setup for test
		String moduleTask = "release";
		if (!username.equals(sm_clientUsername) || !password.equals(sm_clientPassword)) throw new SkipException("These dataProvided parameters are either superfluous or not meaningful for this test.");
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, null, false, null, null, null);
	
		// pad the tail of basicauthproxyLog with a message
		String proxyLogMarker = System.currentTimeMillis()+" Testing "+moduleTask+" AttemptsUsingProxyServerViaRhsmConfig_Test from "+clienttasks.hostname+"...";
		//RemoteFileTasks.runCommandAndAssert(proxyRunner,"echo '"+proxyLogMarker+"'  >> "+proxyLog, Integer.valueOf(0));
		RemoteFileTasks.markFile(proxyRunner, proxyLog, proxyLogMarker);
		
		// set the config parameters
		updateConfFileProxyParameters(proxy_hostnameConfig, proxy_portConfig, proxy_userConfig, proxy_passwordConfig);
		RemoteFileTasks.runCommandAndWait(client,"grep proxy "+clienttasks.rhsmConfFile,TestRecords.action());

		// attempt the moduleTask with the proxy options
		SSHCommandResult attemptResult = clienttasks.release_(null, null, null, null, proxy, proxyuser, proxypassword);
		if (exitCode!=null)	Assert.assertEquals(attemptResult.getExitCode(), exitCode, "The exit code from an attempt to "+moduleTask+" using a proxy server.");
		if (stdout!=null)	Assert.assertEquals(attemptResult.getStdout().trim(), stdout, "The stdout from an attempt to "+moduleTask+" using a proxy server.");
		if (stderr!=null)	Assert.assertEquals(attemptResult.getStderr().trim(), stderr, "The stderr from an attempt to "+moduleTask+" using a proxy server.");

		// assert the tail of proxyLog shows the proxyLogGrepPattern
		if (proxyLogGrepPattern!=null) {
			//SSHCommandResult proxyLogResult = RemoteFileTasks.runCommandAndAssert(proxyRunner,"tail -1 "+proxyLog, Integer.valueOf(0));
			//SSHCommandResult proxyLogResult = proxyRunner.runCommandAndWait("(LINES=''; IFS=$'\n'; for line in $(tac "+proxyLog+"); do if [[ $line = '"+proxyLogMarker+"' ]]; then break; fi; LINES=${LINES}'\n'$line; done; echo -e $LINES) | grep "+clienttasks.ipaddr);	// accounts for multiple tests hitting the same proxy simultaneously
			//Assert.assertContainsMatch(proxyLogResult.getStdout(), proxyLogGrepPattern, "The proxy server appears to be logging the expected connection attempts to the candlepin server.");
			String proxyLogResult = RemoteFileTasks.getTailFromMarkedFile(proxyRunner, proxyLog, proxyLogMarker, clienttasks.ipaddr);	// accounts for multiple tests hitting the same proxy server simultaneously
			//Assert.assertContainsMatch(proxyLogResult, proxyLogGrepPattern, "The proxy server appears to be logging the expected connection attempts to the candlepin server.");	// TOO MUCH LOGGING
			Assert.assertTrue(proxyLogResult.contains(proxyLogGrepPattern), "The tail of proxy server log '"+proxyLog+"' following marker '"+proxyLogMarker+"' contains expected connection '"+proxyLogGrepPattern+"' attempts from "+clienttasks.ipaddr+" to the candlepin server.");
		}
	}
	
	
	
	
	@Test(	description="subscription-manager : release --list using a proxy server (Positive and Negative Variations)",
			groups={"blockedByBug-822965","blockedByBug-824530"},
			dataProvider="getReleaseAttemptsUsingProxyServerData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void ReleaseListAttemptsUsingProxyServer_Test(Object blockedByBug, String username, String password, String org, String proxy, String proxyuser, String proxypassword, Integer exitCode, String stdout, String stderr) {
		// setup for test
		String moduleTask = "release --list";
		if (!username.equals(sm_clientUsername) || !password.equals(sm_clientPassword)) throw new SkipException("These dataProvided parameters are either superfluous or not meaningful for this test.");
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, true, null, null, (String)null, null, null, null, null, false, null, null, null);
		
		// alter the expected feedback when there is no subscribed RHEL product
		if (!exitCode.equals(new Integer(255))) {
			if (!clienttasks.isRhelProductCertSubscribed()) {
				log.warning("Altering the expected feedback from release --list when there is no RHEL product installed with status Subscribed");
				exitCode = new Integer(255);
				stdout = "";
				stderr = "No release versions available, please check subscriptions.";
			}
		}			
		
		SSHCommandResult attemptResult = clienttasks.release_(null, true, null, null, proxy, proxyuser, proxypassword);
		if (exitCode!=null)	Assert.assertEquals(attemptResult.getExitCode(), exitCode, "The exit code from an attempt to "+moduleTask+" using a proxy server.");
		if (stdout!=null)	Assert.assertEquals(attemptResult.getStdout().trim(), stdout, "The stdout from an attempt to "+moduleTask+" using a proxy server.");
		if (stderr!=null)	Assert.assertEquals(attemptResult.getStderr().trim(), stderr, "The stderr from an attempt to "+moduleTask+" using a proxy server.");
	}
	
	
	@Test(	description="subscription-manager : release using a proxy server after setting rhsm.config parameters (Positive and Negative Variations)",
			groups={"blockedByBug-822965","blockedByBug-824530"},
			dataProvider="getReleaseAttemptsUsingProxyServerViaRhsmConfigData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void ReleaseListAttemptsUsingProxyServerViaRhsmConfig_Test(Object blockedByBug, String username, String password, String org, String proxy, String proxyuser, String proxypassword, String proxy_hostnameConfig, String proxy_portConfig, String proxy_userConfig, String proxy_passwordConfig, Integer exitCode, String stdout, String stderr, SSHCommandRunner proxyRunner, String proxyLog, String proxyLogGrepPattern) {
		// setup for test
		String moduleTask = "release --list";
		if (!username.equals(sm_clientUsername) || !password.equals(sm_clientPassword)) throw new SkipException("These dataProvided parameters are either superfluous or not meaningful for this test.");
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, true, null, null, (String)null, null, null, null, null, false, null, null, null);
	
		// alter the expected feedback when there is no subscribed RHEL product
		if (!exitCode.equals(new Integer(255))) {
			if (!clienttasks.isRhelProductCertSubscribed()) {
				log.warning("Altering the expected feedback from release --list when there is no RHEL product installed with status Subscribed");
				exitCode = new Integer(255);
				stdout = "";
				stderr = "No release versions available, please check subscriptions.";
			}
		}
		
		// pad the tail of basicauthproxyLog with a message
		String proxyLogMarker = System.currentTimeMillis()+" Testing "+moduleTask+" AttemptsUsingProxyServerViaRhsmConfig_Test from "+clienttasks.hostname+"...";
		//RemoteFileTasks.runCommandAndAssert(proxyRunner,"echo '"+proxyLogMarker+"'  >> "+proxyLog, Integer.valueOf(0));
		RemoteFileTasks.markFile(proxyRunner, proxyLog, proxyLogMarker);
		
		// set the config parameters
		updateConfFileProxyParameters(proxy_hostnameConfig, proxy_portConfig, proxy_userConfig, proxy_passwordConfig);
		RemoteFileTasks.runCommandAndWait(client,"grep proxy "+clienttasks.rhsmConfFile,TestRecords.action());

		// attempt the moduleTask with the proxy options
		SSHCommandResult attemptResult = clienttasks.release_(null, true, null, null, proxy, proxyuser, proxypassword);
		if (exitCode!=null)	Assert.assertEquals(attemptResult.getExitCode(), exitCode, "The exit code from an attempt to "+moduleTask+" using a proxy server.");
		if (stdout!=null)	Assert.assertEquals(attemptResult.getStdout().trim(), stdout, "The stdout from an attempt to "+moduleTask+" using a proxy server.");
		if (stderr!=null)	Assert.assertEquals(attemptResult.getStderr().trim(), stderr, "The stderr from an attempt to "+moduleTask+" using a proxy server.");

		// assert the tail of proxyLog shows the proxyLogGrepPattern
		if (proxyLogGrepPattern!=null) {
			//SSHCommandResult proxyLogResult = RemoteFileTasks.runCommandAndAssert(proxyRunner,"tail -1 "+proxyLog, Integer.valueOf(0));
			//SSHCommandResult proxyLogResult = proxyRunner.runCommandAndWait("(LINES=''; IFS=$'\n'; for line in $(tac "+proxyLog+"); do if [[ $line = '"+proxyLogMarker+"' ]]; then break; fi; LINES=${LINES}'\n'$line; done; echo -e $LINES) | grep "+clienttasks.ipaddr);	// accounts for multiple tests hitting the same proxy simultaneously
			//Assert.assertContainsMatch(proxyLogResult.getStdout(), proxyLogGrepPattern, "The proxy server appears to be logging the expected connection attempts to the candlepin server.");
			String proxyLogResult = RemoteFileTasks.getTailFromMarkedFile(proxyRunner, proxyLog, proxyLogMarker, clienttasks.ipaddr);	// accounts for multiple tests hitting the same proxy server simultaneously
			//Assert.assertContainsMatch(proxyLogResult, proxyLogGrepPattern, "The proxy server appears to be logging the expected connection attempts to the candlepin server.");	// TOO MUCH LOGGING
			Assert.assertTrue(proxyLogResult.contains(proxyLogGrepPattern), "The tail of proxy server log '"+proxyLog+"' following marker '"+proxyLogMarker+"' contains expected connection '"+proxyLogGrepPattern+"' attempts from "+clienttasks.ipaddr+" to the candlepin server.");
		}
	}
	
	
	// AUTO-HEAL Test methods ***********************************************************************
	
	@Test(	description="subscription-manager : auto-heal using a proxy server (Positive and Negative Variations)",
			groups={},
			dataProvider="getAutoHealAttemptsUsingProxyServerData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void AutoHealAttemptsUsingProxyServer_Test(Object blockedByBug, String username, String password, String org, String proxy, String proxyuser, String proxypassword, Integer exitCode, String stdout, String stderr) {
		// setup for test
		String moduleTask = "autoheal";
		if (!username.equals(sm_clientUsername) || !password.equals(sm_clientPassword)) throw new SkipException("These dataProvided parameters are either superfluous or not meaningful for this test.");
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, null, null, null, null, null);
		
		SSHCommandResult attemptResult = clienttasks.autoheal_(null, null, true, proxy, proxyuser, proxypassword);
		if (exitCode!=null)	Assert.assertEquals(attemptResult.getExitCode(), exitCode, "The exit code from an attempt to "+moduleTask+" using a proxy server.");
		if (stdout!=null)	Assert.assertEquals(attemptResult.getStdout().trim(), stdout, "The stdout from an attempt to "+moduleTask+" using a proxy server.");
		if (stderr!=null)	Assert.assertEquals(attemptResult.getStderr().trim(), stderr, "The stderr from an attempt to "+moduleTask+" using a proxy server.");
	}
	
	
	@Test(	description="subscription-manager : auto-heal using a proxy server after setting rhsm.config parameters (Positive and Negative Variations)",
			groups={},
			dataProvider="getAutoHealAttemptsUsingProxyServerViaRhsmConfigData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void AutoHealAttemptsUsingProxyServerViaRhsmConfig_Test(Object blockedByBug, String username, String password, String org, String proxy, String proxyuser, String proxypassword, String proxy_hostnameConfig, String proxy_portConfig, String proxy_userConfig, String proxy_passwordConfig, Integer exitCode, String stdout, String stderr, SSHCommandRunner proxyRunner, String proxyLog, String proxyLogGrepPattern) {
		// setup for test
		String moduleTask = "autoheal";
		if (!username.equals(sm_clientUsername) || !password.equals(sm_clientPassword)) throw new SkipException("These dataProvided parameters are either superfluous or not meaningful for this test.");
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, null, null, null, null, null);
	
		// pad the tail of basicauthproxyLog with a message
		String proxyLogMarker = System.currentTimeMillis()+" Testing "+moduleTask+" AttemptsUsingProxyServerViaRhsmConfig_Test from "+clienttasks.hostname+"...";
		//RemoteFileTasks.runCommandAndAssert(proxyRunner,"echo '"+proxyLogMarker+"'  >> "+proxyLog, Integer.valueOf(0));
		RemoteFileTasks.markFile(proxyRunner, proxyLog, proxyLogMarker);
		
		// set the config parameters
		updateConfFileProxyParameters(proxy_hostnameConfig, proxy_portConfig, proxy_userConfig, proxy_passwordConfig);
		RemoteFileTasks.runCommandAndWait(client,"grep proxy "+clienttasks.rhsmConfFile,TestRecords.action());

		// attempt the moduleTask with the proxy options
		SSHCommandResult attemptResult = clienttasks.autoheal_(null, null, true, proxy, proxyuser, proxypassword);
		if (exitCode!=null)	Assert.assertEquals(attemptResult.getExitCode(), exitCode, "The exit code from an attempt to "+moduleTask+" using a proxy server.");
		if (stdout!=null)	Assert.assertEquals(attemptResult.getStdout().trim(), stdout, "The stdout from an attempt to "+moduleTask+" using a proxy server.");
		if (stderr!=null)	Assert.assertEquals(attemptResult.getStderr().trim(), stderr, "The stderr from an attempt to "+moduleTask+" using a proxy server.");

		// assert the tail of proxyLog shows the proxyLogGrepPattern
		if (proxyLogGrepPattern!=null) {
			//SSHCommandResult proxyLogResult = RemoteFileTasks.runCommandAndAssert(proxyRunner,"tail -1 "+proxyLog, Integer.valueOf(0));
			//SSHCommandResult proxyLogResult = proxyRunner.runCommandAndWait("(LINES=''; IFS=$'\n'; for line in $(tac "+proxyLog+"); do if [[ $line = '"+proxyLogMarker+"' ]]; then break; fi; LINES=${LINES}'\n'$line; done; echo -e $LINES) | grep "+clienttasks.ipaddr);	// accounts for multiple tests hitting the same proxy simultaneously
			//Assert.assertContainsMatch(proxyLogResult.getStdout(), proxyLogGrepPattern, "The proxy server appears to be logging the expected connection attempts to the candlepin server.");
			String proxyLogResult = RemoteFileTasks.getTailFromMarkedFile(proxyRunner, proxyLog, proxyLogMarker, clienttasks.ipaddr);	// accounts for multiple tests hitting the same proxy server simultaneously
			//Assert.assertContainsMatch(proxyLogResult, proxyLogGrepPattern, "The proxy server appears to be logging the expected connection attempts to the candlepin server.");	// TOO MUCH LOGGING
			Assert.assertTrue(proxyLogResult.contains(proxyLogGrepPattern), "The tail of proxy server log '"+proxyLog+"' following marker '"+proxyLogMarker+"' contains expected connection '"+proxyLogGrepPattern+"' attempts from "+clienttasks.ipaddr+" to the candlepin server.");
		}
	}
	
	
	// STATUS Test methods ***********************************************************************
	
	@Test(	description="subscription-manager : status using a proxy server (Positive and Negative Variations)",
			groups={"blockedByBug-977481"},
			dataProvider="getStatusAttemptsUsingProxyServerData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void StatusAttemptsUsingProxyServer_Test(Object blockedByBug, String username, String password, String org, String proxy, String proxyuser, String proxypassword, Integer exitCode, String stdout, String stderr) {
		// setup for test
		String moduleTask = "status";
		if (!username.equals(sm_clientUsername) || !password.equals(sm_clientPassword)) throw new SkipException("These dataProvided parameters are either superfluous or not meaningful for this test.");
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, null, null, null, null, null);
		
		// NOTE: Because the status module should return a cached status report when connectivity has been interrupted, this call should always pass
		SSHCommandResult attemptResult = clienttasks.status/*_*/(null, proxy, proxyuser, proxypassword);
		if (exitCode!=null)	Assert.assertEquals(attemptResult.getExitCode(), exitCode, "The exit code from an attempt to "+moduleTask+" using a proxy server.");
		if (stdout!=null)	Assert.assertEquals(attemptResult.getStdout().trim(), stdout, "The stdout from an attempt to "+moduleTask+" using a proxy server.");
		if (stderr!=null)	Assert.assertEquals(attemptResult.getStderr().trim(), stderr, "The stderr from an attempt to "+moduleTask+" using a proxy server.");
	}
	
	
	@Test(	description="subscription-manager : status using a proxy server after setting rhsm.config parameters (Positive and Negative Variations)",
			groups={"blockedByBug-977481"},
			dataProvider="getStatusAttemptsUsingProxyServerViaRhsmConfigData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void StatusAttemptsUsingProxyServerViaRhsmConfig_Test(Object blockedByBug, String username, String password, String org, String proxy, String proxyuser, String proxypassword, String proxy_hostnameConfig, String proxy_portConfig, String proxy_userConfig, String proxy_passwordConfig, Integer exitCode, String stdout, String stderr, SSHCommandRunner proxyRunner, String proxyLog, String proxyLogGrepPattern) {
		// setup for test
		String moduleTask = "status";
		if (!username.equals(sm_clientUsername) || !password.equals(sm_clientPassword)) throw new SkipException("These dataProvided parameters are either superfluous or not meaningful for this test.");
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, null, null, null, null, null);
	
		// pad the tail of basicauthproxyLog with a message
		String proxyLogMarker = System.currentTimeMillis()+" Testing "+moduleTask+" AttemptsUsingProxyServerViaRhsmConfig_Test from "+clienttasks.hostname+"...";
		//RemoteFileTasks.runCommandAndAssert(proxyRunner,"echo '"+proxyLogMarker+"'  >> "+proxyLog, Integer.valueOf(0));
		RemoteFileTasks.markFile(proxyRunner, proxyLog, proxyLogMarker);
		
		// set the config parameters
		updateConfFileProxyParameters(proxy_hostnameConfig, proxy_portConfig, proxy_userConfig, proxy_passwordConfig);
		RemoteFileTasks.runCommandAndWait(client,"grep proxy "+clienttasks.rhsmConfFile,TestRecords.action());

		// attempt the moduleTask with the proxy options
		// NOTE: Because the status module should return a cached status report when connectivity has been interrupted, this call should always pass
		SSHCommandResult attemptResult = clienttasks.status/*_*/(null, proxy, proxyuser, proxypassword);
		if (exitCode!=null)	Assert.assertEquals(attemptResult.getExitCode(), exitCode, "The exit code from an attempt to "+moduleTask+" using a proxy server.");
		if (stdout!=null)	Assert.assertEquals(attemptResult.getStdout().trim(), stdout, "The stdout from an attempt to "+moduleTask+" using a proxy server.");
		if (stderr!=null)	Assert.assertEquals(attemptResult.getStderr().trim(), stderr, "The stderr from an attempt to "+moduleTask+" using a proxy server.");

		// assert the tail of proxyLog shows the proxyLogGrepPattern
		if (proxyLogGrepPattern!=null) {
			//SSHCommandResult proxyLogResult = RemoteFileTasks.runCommandAndAssert(proxyRunner,"tail -1 "+proxyLog, Integer.valueOf(0));
			//SSHCommandResult proxyLogResult = proxyRunner.runCommandAndWait("(LINES=''; IFS=$'\n'; for line in $(tac "+proxyLog+"); do if [[ $line = '"+proxyLogMarker+"' ]]; then break; fi; LINES=${LINES}'\n'$line; done; echo -e $LINES) | grep "+clienttasks.ipaddr);	// accounts for multiple tests hitting the same proxy simultaneously
			//Assert.assertContainsMatch(proxyLogResult.getStdout(), proxyLogGrepPattern, "The proxy server appears to be logging the expected connection attempts to the candlepin server.");
			String proxyLogResult = RemoteFileTasks.getTailFromMarkedFile(proxyRunner, proxyLog, proxyLogMarker, clienttasks.ipaddr);	// accounts for multiple tests hitting the same proxy server simultaneously
			//Assert.assertContainsMatch(proxyLogResult, proxyLogGrepPattern, "The proxy server appears to be logging the expected connection attempts to the candlepin server.");	// TOO MUCH LOGGING
			Assert.assertTrue(proxyLogResult.contains(proxyLogGrepPattern), "The tail of proxy server log '"+proxyLog+"' following marker '"+proxyLogMarker+"' contains expected connection '"+proxyLogGrepPattern+"' attempts from "+clienttasks.ipaddr+" to the candlepin server.");
		}
	}
	
	
	// VERSION Test methods ***********************************************************************
	
	@Test(	description="subscription-manager : version using a proxy server (Positive and Negative Variations)",
			groups={"blockedByBug-977481"},
			dataProvider="getVersionAttemptsUsingProxyServerData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void VersionAttemptsUsingProxyServer_Test(Object blockedByBug, String username, String password, String org, String proxy, String proxyuser, String proxypassword, Integer exitCode, String stdout, String stderr) {
		// setup for test
		String moduleTask = "version";
		if (!username.equals(sm_clientUsername) || !password.equals(sm_clientPassword)) throw new SkipException("These dataProvided parameters are either superfluous or not meaningful for this test.");
		
		// NOTE: Because the status module should return a cached status report when connectivity has been interrupted, this call should always pass
		SSHCommandResult attemptResult = clienttasks.version/*_*/(proxy, proxyuser, proxypassword);
		if (exitCode!=null)	Assert.assertEquals(attemptResult.getExitCode(), exitCode, "The exit code from an attempt to "+moduleTask+" using a proxy server.");
		if (stdout!=null)	Assert.assertTrue(attemptResult.getStdout().contains(stdout), "The stdout from an attempt to "+moduleTask+" using a proxy server contains expected report '"+stdout+"'.");
		if (stderr!=null)	Assert.assertEquals(attemptResult.getStderr().trim(), stderr, "The stderr from an attempt to "+moduleTask+" using a proxy server.");
	}
	
	
	@Test(	description="subscription-manager : status using a proxy server after setting rhsm.config parameters (Positive and Negative Variations)",
			groups={"blockedByBug-977481"},
			dataProvider="getVersionAttemptsUsingProxyServerViaRhsmConfigData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void VersionAttemptsUsingProxyServerViaRhsmConfig_Test(Object blockedByBug, String username, String password, String org, String proxy, String proxyuser, String proxypassword, String proxy_hostnameConfig, String proxy_portConfig, String proxy_userConfig, String proxy_passwordConfig, Integer exitCode, String stdout, String stderr, SSHCommandRunner proxyRunner, String proxyLog, String proxyLogGrepPattern) {
		// setup for test
		String moduleTask = "version";
		
		// pad the tail of basicauthproxyLog with a message
		String proxyLogMarker = System.currentTimeMillis()+" Testing "+moduleTask+" AttemptsUsingProxyServerViaRhsmConfig_Test from "+clienttasks.hostname+"...";
		//RemoteFileTasks.runCommandAndAssert(proxyRunner,"echo '"+proxyLogMarker+"'  >> "+proxyLog, Integer.valueOf(0));
		RemoteFileTasks.markFile(proxyRunner, proxyLog, proxyLogMarker);
		
		// set the config parameters
		updateConfFileProxyParameters(proxy_hostnameConfig, proxy_portConfig, proxy_userConfig, proxy_passwordConfig);
		RemoteFileTasks.runCommandAndWait(client,"grep proxy "+clienttasks.rhsmConfFile,TestRecords.action());

		// attempt the moduleTask with the proxy options
		// NOTE: Because the status module should return a cached status report when connectivity has been interrupted, this call should always pass
		SSHCommandResult attemptResult = clienttasks.version/*_*/(proxy, proxyuser, proxypassword);
		if (exitCode!=null)	Assert.assertEquals(attemptResult.getExitCode(), exitCode, "The exit code from an attempt to "+moduleTask+" using a proxy server.");
		if (stdout!=null)	Assert.assertTrue(attemptResult.getStdout().contains(stdout), "The stdout from an attempt to "+moduleTask+" using a proxy server contains expected report '"+stdout+"'.");
		if (stderr!=null)	Assert.assertEquals(attemptResult.getStderr().trim(), stderr, "The stderr from an attempt to "+moduleTask+" using a proxy server.");

		// assert the tail of proxyLog shows the proxyLogGrepPattern
		if (proxyLogGrepPattern!=null) {
			//SSHCommandResult proxyLogResult = RemoteFileTasks.runCommandAndAssert(proxyRunner,"tail -1 "+proxyLog, Integer.valueOf(0));
			//SSHCommandResult proxyLogResult = proxyRunner.runCommandAndWait("(LINES=''; IFS=$'\n'; for line in $(tac "+proxyLog+"); do if [[ $line = '"+proxyLogMarker+"' ]]; then break; fi; LINES=${LINES}'\n'$line; done; echo -e $LINES) | grep "+clienttasks.ipaddr);	// accounts for multiple tests hitting the same proxy simultaneously
			//Assert.assertContainsMatch(proxyLogResult.getStdout(), proxyLogGrepPattern, "The proxy server appears to be logging the expected connection attempts to the candlepin server.");
			String proxyLogResult = RemoteFileTasks.getTailFromMarkedFile(proxyRunner, proxyLog, proxyLogMarker, clienttasks.ipaddr);	// accounts for multiple tests hitting the same proxy server simultaneously
			//Assert.assertContainsMatch(proxyLogResult, proxyLogGrepPattern, "The proxy server appears to be logging the expected connection attempts to the candlepin server.");	// TOO MUCH LOGGING
			Assert.assertTrue(proxyLogResult.contains(proxyLogGrepPattern), "The tail of proxy server log '"+proxyLog+"' following marker '"+proxyLogMarker+"' contains expected connection '"+proxyLogGrepPattern+"' attempts from "+clienttasks.ipaddr+" to the candlepin server.");
		}
	}
	
	
	// REDEEM Test methods ***********************************************************************

	@Test(	description="subscription-manager : redeem using a proxy server (Positive and Negative Variations)",
			groups={"ProxyRedeemTests"},
			dataProvider="getRedeemAttemptsUsingProxyServerData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void RedeemAttemptsUsingProxyServer_Test(Object blockedByBug, String username, String password, String org, String proxy, String proxyuser, String proxypassword, Integer exitCode, String stdout, String stderr) {
		// setup for test
		String moduleTask = "redeem";
		if (!username.equals(sm_clientUsername) || !password.equals(sm_clientPassword)) throw new SkipException("These dataProvided parameters are either superfluous or not meaningful for this test.");
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, null, false, null, null, null);
		
		SSHCommandResult attemptResult = clienttasks.redeem_("proxytester@redhat.com","en-us",null, proxy, proxyuser, proxypassword);
		if (exitCode!=null)	Assert.assertEquals(attemptResult.getExitCode(), exitCode, "The exit code from an attempt to "+moduleTask+" using a proxy server.");
		if (stdout!=null)	Assert.assertEquals(attemptResult.getStdout().trim(), stdout, "The stdout from an attempt to "+moduleTask+" using a proxy server.");
		if (stderr!=null)	Assert.assertEquals(attemptResult.getStderr().trim(), stderr, "The stderr from an attempt to "+moduleTask+" using a proxy server.");
	}

	
	@Test(	description="subscription-manager : redeem using a proxy server after setting rhsm.config parameters (Positive and Negative Variations)",
			groups={"ProxyRedeemTests"},
			dataProvider="getRedeemAttemptsUsingProxyServerViaRhsmConfigData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void RedeemAttemptsUsingProxyServerViaRhsmConfig_Test(Object blockedByBug, String username, String password, String org, String proxy, String proxyuser, String proxypassword, String proxy_hostnameConfig, String proxy_portConfig, String proxy_userConfig, String proxy_passwordConfig, Integer exitCode, String stdout, String stderr, SSHCommandRunner proxyRunner, String proxyLog, String proxyLogGrepPattern) {
		// setup for test
		String moduleTask = "redeem";
		if (!username.equals(sm_clientUsername) || !password.equals(sm_clientPassword)) throw new SkipException("These dataProvided parameters are either superfluous or not meaningful for this test.");
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, null, false, null, null, null);
	
		// pad the tail of basicauthproxyLog with a message
		String proxyLogMarker = System.currentTimeMillis()+" Testing "+moduleTask+" AttemptsUsingProxyServerViaRhsmConfig_Test from "+clienttasks.hostname+"...";
		//RemoteFileTasks.runCommandAndAssert(proxyRunner,"echo '"+proxyLogMarker+"'  >> "+proxyLog, Integer.valueOf(0));
		RemoteFileTasks.markFile(proxyRunner, proxyLog, proxyLogMarker);
		
		// set the config parameters
		updateConfFileProxyParameters(proxy_hostnameConfig, proxy_portConfig, proxy_userConfig, proxy_passwordConfig);
		RemoteFileTasks.runCommandAndWait(client,"grep proxy "+clienttasks.rhsmConfFile,TestRecords.action());

		// attempt the moduleTask with the proxy options
		SSHCommandResult attemptResult = clienttasks.redeem_("proxytester@redhat.com","en-us",null, proxy, proxyuser, proxypassword);
		if (exitCode!=null)	Assert.assertEquals(attemptResult.getExitCode(), exitCode, "The exit code from an attempt to "+moduleTask+" using a proxy server.");
		if (stdout!=null)	Assert.assertEquals(attemptResult.getStdout().trim(), stdout, "The stdout from an attempt to "+moduleTask+" using a proxy server.");
		if (stderr!=null)	Assert.assertEquals(attemptResult.getStderr().trim(), stderr, "The stderr from an attempt to "+moduleTask+" using a proxy server.");

		// assert the tail of proxyLog shows the proxyLogGrepPattern
		if (proxyLogGrepPattern!=null) {
			//SSHCommandResult proxyLogResult = RemoteFileTasks.runCommandAndAssert(proxyRunner,"tail -1 "+proxyLog, Integer.valueOf(0));
			//SSHCommandResult proxyLogResult = proxyRunner.runCommandAndWait("(LINES=''; IFS=$'\n'; for line in $(tac "+proxyLog+"); do if [[ $line = '"+proxyLogMarker+"' ]]; then break; fi; LINES=${LINES}'\n'$line; done; echo -e $LINES) | grep "+clienttasks.ipaddr);	// accounts for multiple tests hitting the same proxy simultaneously
			//Assert.assertContainsMatch(proxyLogResult.getStdout(), proxyLogGrepPattern, "The proxy server appears to be logging the expected connection attempts to the candlepin server.");
			String proxyLogResult = RemoteFileTasks.getTailFromMarkedFile(proxyRunner, proxyLog, proxyLogMarker, clienttasks.ipaddr);	// accounts for multiple tests hitting the same proxy server simultaneously
			//Assert.assertContainsMatch(proxyLogResult, proxyLogGrepPattern, "The proxy server appears to be logging the expected connection attempts to the candlepin server.");	// TOO MUCH LOGGING
			Assert.assertTrue(proxyLogResult.contains(proxyLogGrepPattern), "The tail of proxy server log '"+proxyLog+"' following marker '"+proxyLogMarker+"' contains expected connection '"+proxyLogGrepPattern+"' attempts from "+clienttasks.ipaddr+" to the candlepin server.");
		}
	}
	
	
	
	// FACTS Test methods ***********************************************************************

	@Test(	description="subscription-manager : facts using a proxy server (Positive and Negative Variations)",
			groups={},
			dataProvider="getFactsAttemptsUsingProxyServerData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void FactsAttemptsUsingProxyServer_Test(Object blockedByBug, String username, String password, String org, String proxy, String proxyuser, String proxypassword, Integer exitCode, String stdout, String stderr) {
		// setup for test
		String moduleTask = "facts";
		if (!username.equals(sm_clientUsername) || !password.equals(sm_clientPassword)) throw new SkipException("These dataProvided parameters are either superfluous or not meaningful for this test.");
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, null, false, null, null, null);
		
		SSHCommandResult attemptResult = clienttasks.facts_(null,Boolean.TRUE,proxy, proxyuser, proxypassword);
		if (exitCode!=null)	Assert.assertEquals(attemptResult.getExitCode(), exitCode, "The exit code from an attempt to "+moduleTask+" using a proxy server.");
		if (stdout!=null)	Assert.assertEquals(attemptResult.getStdout().trim(), stdout, "The stdout from an attempt to "+moduleTask+" using a proxy server.");
		if (stderr!=null)	Assert.assertEquals(attemptResult.getStderr().trim(), stderr, "The stderr from an attempt to "+moduleTask+" using a proxy server.");
	}

	
	@Test(	description="subscription-manager : facts using a proxy server after setting rhsm.config parameters (Positive and Negative Variations)",
			groups={},
			dataProvider="getFactsAttemptsUsingProxyServerViaRhsmConfigData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void FactsAttemptsUsingProxyServerViaRhsmConfig_Test(Object blockedByBug, String username, String password, String org, String proxy, String proxyuser, String proxypassword, String proxy_hostnameConfig, String proxy_portConfig, String proxy_userConfig, String proxy_passwordConfig, Integer exitCode, String stdout, String stderr, SSHCommandRunner proxyRunner, String proxyLog, String proxyLogGrepPattern) {
		// setup for test
		String moduleTask = "facts";
		if (!username.equals(sm_clientUsername) || !password.equals(sm_clientPassword)) throw new SkipException("These dataProvided parameters are either superfluous or not meaningful for this test.");
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, null, false, null, null, null);
	
		// pad the tail of basicauthproxyLog with a message
		String proxyLogMarker = System.currentTimeMillis()+" Testing "+moduleTask+" AttemptsUsingProxyServerViaRhsmConfig_Test from "+clienttasks.hostname+"...";
		//RemoteFileTasks.runCommandAndAssert(proxyRunner,"echo '"+proxyLogMarker+"'  >> "+proxyLog, Integer.valueOf(0));
		RemoteFileTasks.markFile(proxyRunner, proxyLog, proxyLogMarker);
		
		// set the config parameters
		updateConfFileProxyParameters(proxy_hostnameConfig, proxy_portConfig, proxy_userConfig, proxy_passwordConfig);
		RemoteFileTasks.runCommandAndWait(client,"grep proxy "+clienttasks.rhsmConfFile,TestRecords.action());

		// attempt the moduleTask with the proxy options
		SSHCommandResult attemptResult = clienttasks.facts_(null,Boolean.TRUE,proxy, proxyuser, proxypassword);
		if (exitCode!=null)	Assert.assertEquals(attemptResult.getExitCode(), exitCode, "The exit code from an attempt to "+moduleTask+" using a proxy server.");
		if (stdout!=null)	Assert.assertEquals(attemptResult.getStdout().trim(), stdout, "The stdout from an attempt to "+moduleTask+" using a proxy server.");
		if (stderr!=null)	Assert.assertEquals(attemptResult.getStderr().trim(), stderr, "The stderr from an attempt to "+moduleTask+" using a proxy server.");

		// assert the tail of proxyLog shows the proxyLogGrepPattern
		if (proxyLogGrepPattern!=null) {
			//SSHCommandResult proxyLogResult = RemoteFileTasks.runCommandAndAssert(proxyRunner,"tail -1 "+proxyLog, Integer.valueOf(0));
			//SSHCommandResult proxyLogResult = proxyRunner.runCommandAndWait("(LINES=''; IFS=$'\n'; for line in $(tac "+proxyLog+"); do if [[ $line = '"+proxyLogMarker+"' ]]; then break; fi; LINES=${LINES}'\n'$line; done; echo -e $LINES) | grep "+clienttasks.ipaddr);	// accounts for multiple tests hitting the same proxy simultaneously
			//Assert.assertContainsMatch(proxyLogResult.getStdout(), proxyLogGrepPattern, "The proxy server appears to be logging the expected connection attempts to the candlepin server.");
			String proxyLogResult = RemoteFileTasks.getTailFromMarkedFile(proxyRunner, proxyLog, proxyLogMarker, clienttasks.ipaddr);	// accounts for multiple tests hitting the same proxy server simultaneously
			//Assert.assertContainsMatch(proxyLogResult, proxyLogGrepPattern, "The proxy server appears to be logging the expected connection attempts to the candlepin server.");	// TOO MUCH LOGGING
			Assert.assertTrue(proxyLogResult.contains(proxyLogGrepPattern), "The tail of proxy server log '"+proxyLog+"' following marker '"+proxyLogMarker+"' contains expected connection '"+proxyLogGrepPattern+"' attempts from "+clienttasks.ipaddr+" to the candlepin server.");
		}
	}
	
	
	
	// REFRESH Test methods ***********************************************************************

	@Test(	description="subscription-manager : refresh using a proxy server (Positive and Negative Variations)",
			groups={"blockedByBug-664548"},
			dataProvider="getRefreshAttemptsUsingProxyServerData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void RefreshAttemptsUsingProxyServer_Test(Object blockedByBug, String username, String password, String org, String proxy, String proxyuser, String proxypassword, Integer exitCode, String stdout, String stderr) {
		// setup for test
		String moduleTask = "refresh";
		if (!username.equals(sm_clientUsername) || !password.equals(sm_clientPassword)) throw new SkipException("These dataProvided parameters are either superfluous or not meaningful for this test.");
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, null, false, null, null, null);
		
		SSHCommandResult attemptResult = clienttasks.refresh_(proxy, proxyuser, proxypassword);
		if (exitCode!=null)	Assert.assertEquals(attemptResult.getExitCode(), exitCode, "The exit code from an attempt to "+moduleTask+" using a proxy server.");
		if (stdout!=null)	Assert.assertEquals(attemptResult.getStdout().trim(), stdout, "The stdout from an attempt to "+moduleTask+" using a proxy server.");
		if (stderr!=null)	Assert.assertEquals(attemptResult.getStderr().trim(), stderr, "The stderr from an attempt to "+moduleTask+" using a proxy server.");
	}

	
	@Test(	description="subscription-manager : refresh using a proxy server after setting rhsm.config parameters (Positive and Negative Variations)",
			groups={"blockedByBug-664548"},
			dataProvider="getRefreshAttemptsUsingProxyServerViaRhsmConfigData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void RefreshAttemptsUsingProxyServerViaRhsmConfig_Test(Object blockedByBug, String username, String password, String org, String proxy, String proxyuser, String proxypassword, String proxy_hostnameConfig, String proxy_portConfig, String proxy_userConfig, String proxy_passwordConfig, Integer exitCode, String stdout, String stderr, SSHCommandRunner proxyRunner, String proxyLog, String proxyLogGrepPattern) {
		// setup for test
		String moduleTask = "refresh";
		if (!username.equals(sm_clientUsername) || !password.equals(sm_clientPassword)) throw new SkipException("These dataProvided parameters are either superfluous or not meaningful for this test.");
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, null, false, null, null, null);
	
		// pad the tail of basicauthproxyLog with a message
		String proxyLogMarker = System.currentTimeMillis()+" Testing "+moduleTask+" AttemptsUsingProxyServerViaRhsmConfig_Test from "+clienttasks.hostname+"...";
		//RemoteFileTasks.runCommandAndAssert(proxyRunner,"echo '"+proxyLogMarker+"'  >> "+proxyLog, Integer.valueOf(0));
		RemoteFileTasks.markFile(proxyRunner, proxyLog, proxyLogMarker);
		
		// set the config parameters
		updateConfFileProxyParameters(proxy_hostnameConfig, proxy_portConfig, proxy_userConfig, proxy_passwordConfig);
		RemoteFileTasks.runCommandAndWait(client,"grep proxy "+clienttasks.rhsmConfFile,TestRecords.action());

		// attempt the moduleTask with the proxy options
		SSHCommandResult attemptResult = clienttasks.refresh_(proxy, proxyuser, proxypassword);
		if (exitCode!=null)	Assert.assertEquals(attemptResult.getExitCode(), exitCode, "The exit code from an attempt to "+moduleTask+" using a proxy server.");
		if (stdout!=null)	Assert.assertEquals(attemptResult.getStdout().trim(), stdout, "The stdout from an attempt to "+moduleTask+" using a proxy server.");
		if (stderr!=null)	Assert.assertEquals(attemptResult.getStderr().trim(), stderr, "The stderr from an attempt to "+moduleTask+" using a proxy server.");

		// assert the tail of proxyLog shows the proxyLogGrepPattern
		if (proxyLogGrepPattern!=null) {
			//SSHCommandResult proxyLogResult = RemoteFileTasks.runCommandAndAssert(proxyRunner,"tail -1 "+proxyLog, Integer.valueOf(0));
			//SSHCommandResult proxyLogResult = proxyRunner.runCommandAndWait("(LINES=''; IFS=$'\n'; for line in $(tac "+proxyLog+"); do if [[ $line = '"+proxyLogMarker+"' ]]; then break; fi; LINES=${LINES}'\n'$line; done; echo -e $LINES) | grep "+clienttasks.ipaddr);	// accounts for multiple tests hitting the same proxy simultaneously
			//Assert.assertContainsMatch(proxyLogResult.getStdout(), proxyLogGrepPattern, "The proxy server appears to be logging the expected connection attempts to the candlepin server.");
			String proxyLogResult = RemoteFileTasks.getTailFromMarkedFile(proxyRunner, proxyLog, proxyLogMarker, clienttasks.ipaddr);	// accounts for multiple tests hitting the same proxy server simultaneously
			//Assert.assertContainsMatch(proxyLogResult, proxyLogGrepPattern, "The proxy server appears to be logging the expected connection attempts to the candlepin server.");	// TOO MUCH LOGGING
			Assert.assertTrue(proxyLogResult.contains(proxyLogGrepPattern), "The tail of proxy server log '"+proxyLog+"' following marker '"+proxyLogMarker+"' contains expected connection '"+proxyLogGrepPattern+"' attempts from "+clienttasks.ipaddr+" to the candlepin server.");
		}
	}
	
	
	
	// REPOS Test methods ***********************************************************************

	@Test(	description="subscription-manager : repos using a proxy server (Positive and Negative Variations)",
			groups={"blockedByBug-906642","blockedByBug-909778"},
			dataProvider="getReposAttemptsUsingProxyServerData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void ReposAttemptsUsingProxyServer_Test(Object blockedByBug, String username, String password, String org, String proxy, String proxyuser, String proxypassword, Integer exitCode, String stdout, String stderr) {
		// setup for test
		String moduleTask = "repos";
		if (!username.equals(sm_clientUsername) || !password.equals(sm_clientPassword)) throw new SkipException("These dataProvided parameters are either superfluous or not meaningful for this test.");
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, null, false, null, null, null);
		List<SubscriptionPool> pools = clienttasks.getCurrentlyAvailableSubscriptionPools();
		SubscriptionPool pool = pools.get(randomGenerator.nextInt(pools.size())); // randomly pick a pool
		clienttasks.subscribe(null,null,pool.poolId,null,null,null, null, null, null, null, null);

		SSHCommandResult attemptResult = clienttasks.repos_(true,(List<String>)null,(List<String>)null,proxy, proxyuser, proxypassword);
		if (exitCode!=null)	Assert.assertEquals(attemptResult.getExitCode(), exitCode, "The exit code from an attempt to "+moduleTask+" --list using a proxy server.");
		if (stdout!=null)	Assert.assertEquals(attemptResult.getStdout().trim(), stdout, "The stdout from an attempt to "+moduleTask+" --list using a proxy server.");
		if (stderr!=null)	Assert.assertEquals(attemptResult.getStderr().trim(), stderr, "The stderr from an attempt to "+moduleTask+" --list using a proxy server.");
	}

	
	@Test(	description="subscription-manager : repos using a proxy server after setting rhsm.config parameters (Positive and Negative Variations)",
			groups={"blockedByBug-906642","blockedByBug-909778"},
			dataProvider="getReposAttemptsUsingProxyServerViaRhsmConfigData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void ReposAttemptsUsingProxyServerViaRhsmConfig_Test(Object blockedByBug, String username, String password, String org, String proxy, String proxyuser, String proxypassword, String proxy_hostnameConfig, String proxy_portConfig, String proxy_userConfig, String proxy_passwordConfig, Integer exitCode, String stdout, String stderr, SSHCommandRunner proxyRunner, String proxyLog, String proxyLogGrepPattern) {
		// setup for test
		String moduleTask = "repos";
		if (!username.equals(sm_clientUsername) || !password.equals(sm_clientPassword)) throw new SkipException("These dataProvided parameters are either superfluous or not meaningful for this test.");
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, null, false, null, null, null);
		List<SubscriptionPool> pools = clienttasks.getCurrentlyAvailableSubscriptionPools();
		SubscriptionPool pool = pools.get(randomGenerator.nextInt(pools.size())); // randomly pick a pool
		clienttasks.subscribe(null,null,pool.poolId,null,null,null, null, null, null, null, null);
		
		// pad the tail of basicauthproxyLog with a message
		String proxyLogMarker = System.currentTimeMillis()+" Testing "+moduleTask+" ReposAttemptsUsingProxyServerViaRhsmConfig_Test from "+clienttasks.hostname+"...";
		//RemoteFileTasks.runCommandAndAssert(proxyRunner,"echo '"+proxyLogMarker+"'  >> "+proxyLog, Integer.valueOf(0));
		RemoteFileTasks.markFile(proxyRunner, proxyLog, proxyLogMarker);

		// set the config parameters
		updateConfFileProxyParameters(proxy_hostnameConfig, proxy_portConfig, proxy_userConfig, proxy_passwordConfig);
		RemoteFileTasks.runCommandAndWait(client,"grep proxy "+clienttasks.rhsmConfFile,TestRecords.action());
		
		// attempt the moduleTask with the proxy options
		SSHCommandResult attemptResult = clienttasks.repos_(true,(List<String>)null,(List<String>)null,proxy, proxyuser, proxypassword);
		if (exitCode!=null)	Assert.assertEquals(attemptResult.getExitCode(), exitCode, "The exit code from an attempt to "+moduleTask+" --list using a proxy server.");
		if (stdout!=null)	Assert.assertEquals(attemptResult.getStdout().trim(), stdout, "The stdout from an attempt to "+moduleTask+" --list using a proxy server.");
		if (stderr!=null)	Assert.assertEquals(attemptResult.getStderr().trim(), stderr, "The stderr from an attempt to "+moduleTask+" --list using a proxy server.");

		// assert the tail of proxyLog shows the proxyLogGrepPattern
		if (proxyLogGrepPattern!=null) {
			//SSHCommandResult proxyLogResult = RemoteFileTasks.runCommandAndAssert(proxyRunner,"tail -1 "+proxyLog, Integer.valueOf(0));
			//SSHCommandResult proxyLogResult = proxyRunner.runCommandAndWait("(LINES=''; IFS=$'\n'; for line in $(tac "+proxyLog+"); do if [[ $line = '"+proxyLogMarker+"' ]]; then break; fi; LINES=${LINES}'\n'$line; done; echo -e $LINES) | grep "+clienttasks.ipaddr);	// accounts for multiple tests hitting the same proxy simultaneously
			//Assert.assertContainsMatch(proxyLogResult.getStdout(), proxyLogGrepPattern, "The proxy server appears to be logging the expected connection attempts to the candlepin server.");
			String proxyLogResult = RemoteFileTasks.getTailFromMarkedFile(proxyRunner, proxyLog, proxyLogMarker, clienttasks.ipaddr);	// accounts for multiple tests hitting the same proxy server simultaneously
			//Assert.assertContainsMatch(proxyLogResult, proxyLogGrepPattern, "The proxy server appears to be logging the expected connection attempts to the candlepin server.");	// TOO MUCH LOGGING
			Assert.assertTrue(proxyLogResult.contains(proxyLogGrepPattern), "The tail of proxy server log '"+proxyLog+"' following marker '"+proxyLogMarker+"' contains expected connection '"+proxyLogGrepPattern+"' attempts from "+clienttasks.ipaddr+" to the candlepin server.");
		}
	}
	
	
	
	// REPO-OVERRIDE Test methods ***********************************************************************

	@Test(	description="subscription-manager : repo-override --list using a proxy server (Positive and Negative Variations)",
			groups={},
			dataProvider="getRepoOverrideAttemptsUsingProxyServerData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void RepoOverrideAttemptsUsingProxyServer_Test(Object blockedByBug, String username, String password, String org, String proxy, String proxyuser, String proxypassword, Integer exitCode, String stdout, String stderr) {
		// setup for test
		String moduleTask = "repo-override";
		if (!username.equals(sm_clientUsername) || !password.equals(sm_clientPassword)) throw new SkipException("These dataProvided parameters are either superfluous or not meaningful for this test.");
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, null, false, null, null, null);
//		List<SubscriptionPool> pools = clienttasks.getCurrentlyAvailableSubscriptionPools();
//		SubscriptionPool pool = pools.get(randomGenerator.nextInt(pools.size())); // randomly pick a pool
//		clienttasks.subscribe(null,null,pool.poolId,null,null,null, null, null, null, null, null);
		
		SSHCommandResult attemptResult = clienttasks.repo_override_(true,null,(List<String>)null,(List<String>)null,null,proxy, proxyuser, proxypassword);
		if (exitCode!=null)	Assert.assertEquals(attemptResult.getExitCode(), exitCode, "The exit code from an attempt to "+moduleTask+" --list using a proxy server.");
		if (stdout!=null)	Assert.assertEquals(attemptResult.getStdout().trim(), stdout, "The stdout from an attempt to "+moduleTask+" --list using a proxy server.");
		if (stderr!=null)	Assert.assertEquals(attemptResult.getStderr().trim(), stderr, "The stderr from an attempt to "+moduleTask+" --list using a proxy server.");
	}
	
	
	@Test(	description="subscription-manager : repo-override --list using a proxy server after setting rhsm.config parameters (Positive and Negative Variations)",
			groups={},
			dataProvider="getRepoOverrideAttemptsUsingProxyServerViaRhsmConfigData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void RepoOverrideAttemptsUsingProxyServerViaRhsmConfig_Test(Object blockedByBug, String username, String password, String org, String proxy, String proxyuser, String proxypassword, String proxy_hostnameConfig, String proxy_portConfig, String proxy_userConfig, String proxy_passwordConfig, Integer exitCode, String stdout, String stderr, SSHCommandRunner proxyRunner, String proxyLog, String proxyLogGrepPattern) {
		// setup for test
		String moduleTask = "repo-override";
		if (!username.equals(sm_clientUsername) || !password.equals(sm_clientPassword)) throw new SkipException("These dataProvided parameters are either superfluous or not meaningful for this test.");
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, null, false, null, null, null);
//		List<SubscriptionPool> pools = clienttasks.getCurrentlyAvailableSubscriptionPools();
//		SubscriptionPool pool = pools.get(randomGenerator.nextInt(pools.size())); // randomly pick a pool
//		clienttasks.subscribe(null,null,pool.poolId,null,null,null, null, null, null, null, null);
		
		// pad the tail of basicauthproxyLog with a message
		String proxyLogMarker = System.currentTimeMillis()+" Testing "+moduleTask+" ReposAttemptsUsingProxyServerViaRhsmConfig_Test from "+clienttasks.hostname+"...";
		//RemoteFileTasks.runCommandAndAssert(proxyRunner,"echo '"+proxyLogMarker+"'  >> "+proxyLog, Integer.valueOf(0));
		RemoteFileTasks.markFile(proxyRunner, proxyLog, proxyLogMarker);
		
		// set the config parameters
		updateConfFileProxyParameters(proxy_hostnameConfig, proxy_portConfig, proxy_userConfig, proxy_passwordConfig);
		RemoteFileTasks.runCommandAndWait(client,"grep proxy "+clienttasks.rhsmConfFile,TestRecords.action());
		
		// attempt the moduleTask with the proxy options
		SSHCommandResult attemptResult = clienttasks.repo_override_(true,null,(List<String>)null,(List<String>)null,null,proxy, proxyuser, proxypassword);
		if (exitCode!=null)	Assert.assertEquals(attemptResult.getExitCode(), exitCode, "The exit code from an attempt to "+moduleTask+" --list using a proxy server.");
		if (stdout!=null)	Assert.assertEquals(attemptResult.getStdout().trim(), stdout, "The stdout from an attempt to "+moduleTask+" --list using a proxy server.");
		if (stderr!=null)	Assert.assertEquals(attemptResult.getStderr().trim(), stderr, "The stderr from an attempt to "+moduleTask+" --list using a proxy server.");
		
		// assert the tail of proxyLog shows the proxyLogGrepPattern
		if (proxyLogGrepPattern!=null) {
			//SSHCommandResult proxyLogResult = RemoteFileTasks.runCommandAndAssert(proxyRunner,"tail -1 "+proxyLog, Integer.valueOf(0));
			//SSHCommandResult proxyLogResult = proxyRunner.runCommandAndWait("(LINES=''; IFS=$'\n'; for line in $(tac "+proxyLog+"); do if [[ $line = '"+proxyLogMarker+"' ]]; then break; fi; LINES=${LINES}'\n'$line; done; echo -e $LINES) | grep "+clienttasks.ipaddr);	// accounts for multiple tests hitting the same proxy simultaneously
			//Assert.assertContainsMatch(proxyLogResult.getStdout(), proxyLogGrepPattern, "The proxy server appears to be logging the expected connection attempts to the candlepin server.");
			String proxyLogResult = RemoteFileTasks.getTailFromMarkedFile(proxyRunner, proxyLog, proxyLogMarker, clienttasks.ipaddr);	// accounts for multiple tests hitting the same proxy server simultaneously
			//Assert.assertContainsMatch(proxyLogResult, proxyLogGrepPattern, "The proxy server appears to be logging the expected connection attempts to the candlepin server.");	// TOO MUCH LOGGING
			Assert.assertTrue(proxyLogResult.contains(proxyLogGrepPattern), "The tail of proxy server log '"+proxyLog+"' following marker '"+proxyLogMarker+"' contains expected connection '"+proxyLogGrepPattern+"' attempts from "+clienttasks.ipaddr+" to the candlepin server.");
		}
	}
	
	
	
	// SUBSCRIBE Test methods ***********************************************************************

	@Test(	description="subscription-manager : subscribe using a proxy server (Positive and Negative Variations)",
			groups={"blockedByBug-664603"},
			dataProvider="getSubscribeAttemptsUsingProxyServerData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void SubscribeAttemptsUsingProxyServer_Test(Object blockedByBug, String username, String password, String org, String proxy, String proxyuser, String proxypassword, Integer exitCode, String stdout, String stderr) {
		// setup for test
		String moduleTask = "subscribe";
		if (!username.equals(sm_clientUsername) || !password.equals(sm_clientPassword)) throw new SkipException("These dataProvided parameters are either superfluous or not meaningful for this test.");
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, null, false, null, null, null);
		List<SubscriptionPool> pools = clienttasks.getCurrentlyAvailableSubscriptionPools();
		SubscriptionPool pool = pools.get(randomGenerator.nextInt(pools.size())); // randomly pick a pool

		SSHCommandResult attemptResult = clienttasks.subscribe_(null,null,pool.poolId,null,null,null, null, null, proxy, proxyuser, proxypassword);
		if (exitCode!=null)	Assert.assertEquals(attemptResult.getExitCode(), exitCode, "The exit code from an attempt to "+moduleTask+" using a proxy server.");
		if (stdout!=null)	Assert.assertEquals(attemptResult.getStdout().trim(), stdout, "The stdout from an attempt to "+moduleTask+" using a proxy server.");
		if (stderr!=null)	Assert.assertEquals(attemptResult.getStderr().trim(), stderr, "The stderr from an attempt to "+moduleTask+" using a proxy server.");
	}

	
	@Test(	description="subscription-manager : subscribe using a proxy server after setting rhsm.config parameters (Positive and Negative Variations)",
			groups={"blockedByBug-664603"},
			dataProvider="getSubscribeAttemptsUsingProxyServerViaRhsmConfigData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void SubscribeAttemptsUsingProxyServerViaRhsmConfig_Test(Object blockedByBug, String username, String password, String org, String proxy, String proxyuser, String proxypassword, String proxy_hostnameConfig, String proxy_portConfig, String proxy_userConfig, String proxy_passwordConfig, Integer exitCode, String stdout, String stderr, SSHCommandRunner proxyRunner, String proxyLog, String proxyLogGrepPattern) {
		// setup for test
		String moduleTask = "subscribe";
		if (!username.equals(sm_clientUsername) || !password.equals(sm_clientPassword)) throw new SkipException("These dataProvided parameters are either superfluous or not meaningful for this test.");
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, null, false, null, null, null);
		List<SubscriptionPool> pools = clienttasks.getCurrentlyAvailableSubscriptionPools();
		SubscriptionPool pool = pools.get(randomGenerator.nextInt(pools.size())); // randomly pick a pool
		
		// pad the tail of basicauthproxyLog with a message
		String proxyLogMarker = System.currentTimeMillis()+" Testing "+moduleTask+" AttemptsUsingProxyServerViaRhsmConfig_Test from "+clienttasks.hostname+"...";
		//RemoteFileTasks.runCommandAndAssert(proxyRunner,"echo '"+proxyLogMarker+"'  >> "+proxyLog, Integer.valueOf(0));
		RemoteFileTasks.markFile(proxyRunner, proxyLog, proxyLogMarker);

		// set the config parameters
		updateConfFileProxyParameters(proxy_hostnameConfig, proxy_portConfig, proxy_userConfig, proxy_passwordConfig);
		RemoteFileTasks.runCommandAndWait(client,"grep proxy "+clienttasks.rhsmConfFile,TestRecords.action());
		
		// attempt the moduleTask with the proxy options
		SSHCommandResult attemptResult = clienttasks.subscribe_(null,null,pool.poolId,null,null,null, null, null, proxy, proxyuser, proxypassword);
		if (exitCode!=null)	Assert.assertEquals(attemptResult.getExitCode(), exitCode, "The exit code from an attempt to "+moduleTask+" using a proxy server.");
		if (stdout!=null)	Assert.assertEquals(attemptResult.getStdout().trim(), stdout, "The stdout from an attempt to "+moduleTask+" using a proxy server.");
		if (stderr!=null)	Assert.assertEquals(attemptResult.getStderr().trim(), stderr, "The stderr from an attempt to "+moduleTask+" using a proxy server.");

		// assert the tail of proxyLog shows the proxyLogGrepPattern
		if (proxyLogGrepPattern!=null) {
			//SSHCommandResult proxyLogResult = RemoteFileTasks.runCommandAndAssert(proxyRunner,"tail -1 "+proxyLog, Integer.valueOf(0));
			//SSHCommandResult proxyLogResult = proxyRunner.runCommandAndWait("(LINES=''; IFS=$'\n'; for line in $(tac "+proxyLog+"); do if [[ $line = '"+proxyLogMarker+"' ]]; then break; fi; LINES=${LINES}'\n'$line; done; echo -e $LINES) | grep "+clienttasks.ipaddr);	// accounts for multiple tests hitting the same proxy simultaneously
			//Assert.assertContainsMatch(proxyLogResult.getStdout(), proxyLogGrepPattern, "The proxy server appears to be logging the expected connection attempts to the candlepin server.");
			String proxyLogResult = RemoteFileTasks.getTailFromMarkedFile(proxyRunner, proxyLog, proxyLogMarker, clienttasks.ipaddr);	// accounts for multiple tests hitting the same proxy server simultaneously
			//Assert.assertContainsMatch(proxyLogResult, proxyLogGrepPattern, "The proxy server appears to be logging the expected connection attempts to the candlepin server.");	// TOO MUCH LOGGING
			Assert.assertTrue(proxyLogResult.contains(proxyLogGrepPattern), "The tail of proxy server log '"+proxyLog+"' following marker '"+proxyLogMarker+"' contains expected connection '"+proxyLogGrepPattern+"' attempts from "+clienttasks.ipaddr+" to the candlepin server.");
		}
	}
	
	
	
	// UNSUBSCRIBE Test methods ***********************************************************************

	@Test(	description="subscription-manager : unsubscribe using a proxy server (Positive and Negative Variations)",
			groups={"blockedByBug-664603"},
			dataProvider="getUnsubscribeAttemptsUsingProxyServerData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void UnsubscribeAttemptsUsingProxyServer_Test(Object blockedByBug, String username, String password, String org, String proxy, String proxyuser, String proxypassword, Integer exitCode, String stdout, String stderr) {
		// setup for test
		String moduleTask = "unsubscribe";
		if (!username.equals(sm_clientUsername) || !password.equals(sm_clientPassword)) throw new SkipException("These dataProvided parameters are either superfluous or not meaningful for this test.");
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, null, false, null, null, null);
		
		SSHCommandResult attemptResult = clienttasks.unsubscribe_(true, (BigInteger)null,proxy, proxyuser, proxypassword);
		if (exitCode!=null)	Assert.assertEquals(attemptResult.getExitCode(), exitCode, "The exit code from an attempt to "+moduleTask+" using a proxy server.");
		if (stdout!=null)	Assert.assertEquals(attemptResult.getStdout().trim(), stdout, "The stdout from an attempt to "+moduleTask+" using a proxy server.");
		if (stderr!=null)	Assert.assertEquals(attemptResult.getStderr().trim(), stderr, "The stderr from an attempt to "+moduleTask+" using a proxy server.");
	}

	
	@Test(	description="subscription-manager : subscribe using a proxy server after setting rhsm.config parameters (Positive and Negative Variations)",
			groups={"blockedByBug-664603"},
			dataProvider="getUnsubscribeAttemptsUsingProxyServerViaRhsmConfigData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void UnsubscribeAttemptsUsingProxyServerViaRhsmConfig_Test(Object blockedByBug, String username, String password, String org, String proxy, String proxyuser, String proxypassword, String proxy_hostnameConfig, String proxy_portConfig, String proxy_userConfig, String proxy_passwordConfig, Integer exitCode, String stdout, String stderr, SSHCommandRunner proxyRunner, String proxyLog, String proxyLogGrepPattern) {
		// setup for test
		String moduleTask = "unsubscribe";
		if (!username.equals(sm_clientUsername) || !password.equals(sm_clientPassword)) throw new SkipException("These dataProvided parameters are either superfluous or not meaningful for this test.");
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, null, false, null, null, null);
		
		// pad the tail of basicauthproxyLog with a message
		String proxyLogMarker = System.currentTimeMillis()+" Testing "+moduleTask+" AttemptsUsingProxyServerViaRhsmConfig_Test from "+clienttasks.hostname+"...";
		//RemoteFileTasks.runCommandAndAssert(proxyRunner,"echo '"+proxyLogMarker+"'  >> "+proxyLog, Integer.valueOf(0));
		RemoteFileTasks.markFile(proxyRunner, proxyLog, proxyLogMarker);
		
		// set the config parameters
		updateConfFileProxyParameters(proxy_hostnameConfig, proxy_portConfig, proxy_userConfig, proxy_passwordConfig);
		RemoteFileTasks.runCommandAndWait(client,"grep proxy "+clienttasks.rhsmConfFile,TestRecords.action());

		// attempt the moduleTask with the proxy options
		SSHCommandResult attemptResult = clienttasks.unsubscribe_(true, (BigInteger)null,proxy, proxyuser, proxypassword);
		if (exitCode!=null)	Assert.assertEquals(attemptResult.getExitCode(), exitCode, "The exit code from an attempt to "+moduleTask+" using a proxy server.");
		if (stdout!=null)	Assert.assertEquals(attemptResult.getStdout().trim(), stdout, "The stdout from an attempt to "+moduleTask+" using a proxy server.");
		if (stderr!=null)	Assert.assertEquals(attemptResult.getStderr().trim(), stderr, "The stderr from an attempt to "+moduleTask+" using a proxy server.");

		// assert the tail of proxyLog shows the proxyLogGrepPattern
		if (proxyLogGrepPattern!=null) {
			//SSHCommandResult proxyLogResult = RemoteFileTasks.runCommandAndAssert(proxyRunner,"tail -1 "+proxyLog, Integer.valueOf(0));
			//SSHCommandResult proxyLogResult = proxyRunner.runCommandAndWait("(LINES=''; IFS=$'\n'; for line in $(tac "+proxyLog+"); do if [[ $line = '"+proxyLogMarker+"' ]]; then break; fi; LINES=${LINES}'\n'$line; done; echo -e $LINES) | grep "+clienttasks.ipaddr);	// accounts for multiple tests hitting the same proxy simultaneously
			//Assert.assertContainsMatch(proxyLogResult.getStdout(), proxyLogGrepPattern, "The proxy server appears to be logging the expected connection attempts to the candlepin server.");
			String proxyLogResult = RemoteFileTasks.getTailFromMarkedFile(proxyRunner, proxyLog, proxyLogMarker, clienttasks.ipaddr);	// accounts for multiple tests hitting the same proxy server simultaneously
			//Assert.assertContainsMatch(proxyLogResult, proxyLogGrepPattern, "The proxy server appears to be logging the expected connection attempts to the candlepin server.");	// TOO MUCH LOGGING
			Assert.assertTrue(proxyLogResult.contains(proxyLogGrepPattern), "The tail of proxy server log '"+proxyLog+"' following marker '"+proxyLogMarker+"' contains expected connection '"+proxyLogGrepPattern+"' attempts from "+clienttasks.ipaddr+" to the candlepin server.");
		}
	}
	
	
	
	// RHSM-DEBUG SYSTEM Test methods ***********************************************************************

	@Test(	description="rhsm-debug : system using a proxy server (Positive and Negative Variations)",
			groups={},
			dataProvider="getRhsmDebugSystemAttemptsUsingProxyServerData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void RhsmDebugSystemAttemptsUsingProxyServer_Test(Object blockedByBug, String username, String password, String org, String proxy, String proxyuser, String proxypassword, Integer exitCode, String stdout, String stderr) {
		// setup for test
		String moduleTask = "rhsm-debug system";
		if (!username.equals(sm_clientUsername) || !password.equals(sm_clientPassword)) throw new SkipException("These dataProvided parameters are either superfluous or not meaningful for this test.");
		clienttasks.register_(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, null, null, null, null, null);
		
		SSHCommandResult attemptResult = client.runCommandAndWait(clienttasks.rhsmDebugSystemCommand(null, null, proxy, proxyuser, proxypassword));
		if (exitCode!=null)	Assert.assertEquals(attemptResult.getExitCode(), exitCode, "The exit code from an attempt to "+moduleTask+" using a proxy server.");
		if (stdout!=null)	Assert.assertEquals(attemptResult.getStdout().trim(), stdout, "The stdout from an attempt to "+moduleTask+" using a proxy server.");
		if (stderr!=null)	Assert.assertEquals(attemptResult.getStderr().trim(), stderr, "The stderr from an attempt to "+moduleTask+" using a proxy server.");
	}
	
	
	@Test(	description="rhsm-debug : system using a proxy server after setting rhsm.config parameters (Positive and Negative Variations)",
			groups={},
			dataProvider="getRhsmDebugSystemAttemptsUsingProxyServerViaRhsmConfigData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void RhsmDebugSystemAttemptsUsingProxyServerViaRhsmConfig_Test(Object blockedByBug, String username, String password, String org, String proxy, String proxyuser, String proxypassword, String proxy_hostnameConfig, String proxy_portConfig, String proxy_userConfig, String proxy_passwordConfig, Integer exitCode, String stdout, String stderr, SSHCommandRunner proxyRunner, String proxyLog, String proxyLogGrepPattern) {
		// setup for test
		String moduleTask = "rhsm-debug system";
		if (!username.equals(sm_clientUsername) || !password.equals(sm_clientPassword)) throw new SkipException("These dataProvided parameters are either superfluous or not meaningful for this test.");
		clienttasks.register_(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, null, null, null, null, null);
		
		// pad the tail of basicauthproxyLog with a message
		String proxyLogMarker = System.currentTimeMillis()+" Testing "+moduleTask+" AttemptsUsingProxyServerViaRhsmConfig_Test from "+clienttasks.hostname+"...";
		//RemoteFileTasks.runCommandAndAssert(proxyRunner,"echo '"+proxyLogMarker+"'  >> "+proxyLog, Integer.valueOf(0));
		RemoteFileTasks.markFile(proxyRunner, proxyLog, proxyLogMarker);
		
		// set the config parameters
		updateConfFileProxyParameters(proxy_hostnameConfig, proxy_portConfig, proxy_userConfig, proxy_passwordConfig);
		RemoteFileTasks.runCommandAndWait(client,"grep proxy "+clienttasks.rhsmConfFile,TestRecords.action());
		
		// attempt the moduleTask with the proxy options
		SSHCommandResult attemptResult = client.runCommandAndWait(clienttasks.rhsmDebugSystemCommand(null, null, proxy, proxyuser, proxypassword));
		if (exitCode!=null)	Assert.assertEquals(attemptResult.getExitCode(), exitCode, "The exit code from an attempt to "+moduleTask+" using a proxy server.");
		if (stdout!=null)	Assert.assertEquals(attemptResult.getStdout().trim(), stdout, "The stdout from an attempt to "+moduleTask+" using a proxy server.");
		if (stderr!=null)	Assert.assertEquals(attemptResult.getStderr().trim(), stderr, "The stderr from an attempt to "+moduleTask+" using a proxy server.");
		
		// assert the tail of proxyLog shows the proxyLogGrepPattern
		if (proxyLogGrepPattern!=null) {
			//SSHCommandResult proxyLogResult = RemoteFileTasks.runCommandAndAssert(proxyRunner,"tail -1 "+proxyLog, Integer.valueOf(0));
			//SSHCommandResult proxyLogResult = proxyRunner.runCommandAndWait("(LINES=''; IFS=$'\n'; for line in $(tac "+proxyLog+"); do if [[ $line = '"+proxyLogMarker+"' ]]; then break; fi; LINES=${LINES}'\n'$line; done; echo -e $LINES) | grep "+clienttasks.ipaddr);	// accounts for multiple tests hitting the same proxy simultaneously
			//Assert.assertContainsMatch(proxyLogResult.getStdout(), proxyLogGrepPattern, "The proxy server appears to be logging the expected connection attempts to the candlepin server.");
			String proxyLogResult = RemoteFileTasks.getTailFromMarkedFile(proxyRunner, proxyLog, proxyLogMarker, clienttasks.ipaddr);	// accounts for multiple tests hitting the same proxy server simultaneously
			//Assert.assertContainsMatch(proxyLogResult, proxyLogGrepPattern, "The proxy server appears to be logging the expected connection attempts to the candlepin server.");	// TOO MUCH LOGGING
			Assert.assertTrue(proxyLogResult.contains(proxyLogGrepPattern), "The tail of proxy server log '"+proxyLog+"' following marker '"+proxyLogMarker+"' contains expected connection '"+proxyLogGrepPattern+"' attempts from "+clienttasks.ipaddr+" to the candlepin server.");
		}
	}
	
	
	
	
	
	
	// More Test methods ***********************************************************************
	
	@Test(	description="subscription-manager :  register with proxy configurations commented out of rhsm.conf",
			groups={},
			dataProvider="getRegisterWithProxyConfigurationsCommentedOutOfRhsmConfigData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void RegisterWithProxyConfigurationsCommentedOutOfRhsmConfig_Test(Object meta, String[] proxyConfigs) {
		
		// comment out each of the config proxy parameters
		for (String proxyConfig : proxyConfigs) clienttasks.commentConfFileParameter(clienttasks.rhsmConfFile, proxyConfig);
		
		log.info("Following are the current proxy parameters configured in config file: "+clienttasks.rhsmConfFile);
		RemoteFileTasks.runCommandAndWait(client, "grep proxy_ "+clienttasks.rhsmConfFile, TestRecords.action());
		
		log.info("Attempt to register with the above proxy config parameters configured (expecting success)...");
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, null, false, null, null, null);
	}
	
	
	@Test(	description="subscription-manager : repos list with proxy set to a real server that is not truely a proxy (e.g. www.redhat.com)",
			groups={"blockedByBug-968820"},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void ReposListWithProxyTimeoutBug968820_Test() {
		// register
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, true, false, null, null, null);

		// subscribe
		List<SubscriptionPool> pools = clienttasks.getCurrentlyAvailableSubscriptionPools();
		if (pools.isEmpty()) log.warning("Cound not find an available pool.");
		SubscriptionPool pool = pools.get(randomGenerator.nextInt(pools.size()));	// randomly pick a pool
		//clienttasks.subscribeToSubscriptionPool(pool);
		clienttasks.subscribe(null,null,pool.poolId,null,null,null,null,null,null,null,null);
		
		// repos --list --proxy=www.redhat.com
		String command = clienttasks.command+" repos --list --proxy=www.redhat.com";
		Long timeoutMS = Long.valueOf(2/*min*/*60*1000);	// do not wait any longer than this many milliseconds
		SSHCommandResult result= client.runCommandAndWait(command, timeoutMS);
		
		// assert results
		Assert.assertEquals(result.getStdout().trim(), nErrMsg, "Stdout from command '"+command+"' with a timeout of '"+timeoutMS+"' MS.");
		Assert.assertEquals(result.getStderr().trim(), "", "Stderr from command '"+command+"' with a timeout of '"+timeoutMS+"' MS.");
		Assert.assertEquals(result.getExitCode(), Integer.valueOf(255), "ExitCode from command '"+command+"' with a timeout of '"+timeoutMS+"' MS.");
	}
	
	
	@Test(	description="subscription-manager : register using a proxy server defined by an environment variable (Positive and Negative Variations)",
			groups={"blockedByBug-1031755"},
			dataProvider="getRegisterAttemptsUsingProxyServerViaRhsmConfigData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)	
	public void RegisterAttemptsUsingProxyServerDefinedByAnEnvironmentVariable_Test(Object blockedByBug, String username, String password, String org, String proxy, String proxyuser, String proxypassword, String proxy_hostnameConfig, String proxy_portConfig, String proxy_userConfig, String proxy_passwordConfig, Integer exitCode, String stdout, String stderr, SSHCommandRunner proxyRunner, String proxyLog, String proxyLogGrepPattern) {
		String moduleTask = "register";
		String httpProxyEnvVar;
		String proxyLogMarker;
		SSHCommandResult attemptResult;
		
		// Notes: proxy precedence rules as defined in Sprint Review 68
		// a. proxy values set in the subscription-manager CLI options override all
		// b. proxy values set in the rhsm.conf file override the proxy environment variable
		// c. finally the proxy environment variable applies when no proxy values are set in the CLI options nor rhsm.conf
		
		// randomly test using one of these valid environment variables 
		List<String> validHttpProxyEnvVars = Arrays.asList(new String[]{"HTTPS_PROXY","https_proxy","HTTP_PROXY","http_proxy"});
		
		
		// TEST PART 1: let's assert that setting the proxy via an environment variable works
		
		// assemble the value of the httpProxyVar
		// HTTPS_PROXY=https://proxyserver
		// HTTPS_PROXY=https://proxyserver:proxyport
		// HTTPS_PROXY=https://username:password@proxyserver:proxyport
		// proxy configurations intended for CLI proxy options take precedence over the proxy configurations intended for rhsm.conf file
		httpProxyEnvVar = validHttpProxyEnvVars.get(randomGenerator.nextInt(validHttpProxyEnvVars.size())) + "=https://";
		if (proxyuser!=null)							httpProxyEnvVar += proxyuser;			else if (proxy_userConfig!=null)		httpProxyEnvVar += proxy_userConfig;
		if (proxypassword!=null)						httpProxyEnvVar += ":"+proxypassword;	else if (proxy_passwordConfig!=null)	httpProxyEnvVar += ":"+proxy_passwordConfig;
		if (proxyuser!=null || proxy_userConfig!=null)	httpProxyEnvVar += "@";
		if (proxy!=null)								httpProxyEnvVar += proxy;				else {if (proxy_hostnameConfig!=null)	httpProxyEnvVar += proxy_hostnameConfig; if (proxy_portConfig!=null)	httpProxyEnvVar += ":"+proxy_portConfig;}
		
		// reset the config parameters
		updateConfFileProxyParameters("", "", "", "");
		RemoteFileTasks.runCommandAndWait(client,"grep proxy "+clienttasks.rhsmConfFile,TestRecords.action());
		
		// pad the tail of proxyLog with a message
		proxyLogMarker = System.currentTimeMillis()+" Testing "+moduleTask+" AttemptsUsingProxyServerDefinedByAnEnvironmentVariable_Test from "+clienttasks.hostname+"...";
		RemoteFileTasks.markFile(proxyRunner, proxyLog, proxyLogMarker);
		
		// attempt to register using a proxy server defined by an environment variable (no CLI option nor rhsm.conf [sever] proxy configurations set)
		attemptResult = client.runCommandAndWait(httpProxyEnvVar+" "+clienttasks.registerCommand(username, password, org, null, null, null, null, null, null, null, (List<String>)null, null, null, null, null, null, null, null, null));
		if (exitCode!=null)	Assert.assertEquals(attemptResult.getExitCode(), exitCode, "The exit code from a negative attempt to "+moduleTask+" using a proxy server defined by an environment variable '"+httpProxyEnvVar+"'.");
		if (stdout!=null)	Assert.assertEquals(attemptResult.getStdout().trim(), stdout, "The stdout from an attempt to "+moduleTask+" using a proxy server defined by an environment variable '"+httpProxyEnvVar+"'.");
		if (stderr!=null)	Assert.assertEquals(attemptResult.getStderr().trim(), stderr, "The stderr from an attempt to "+moduleTask+" using a proxy server defined by an environment variable '"+httpProxyEnvVar+"'.");
		
		// assert the tail of proxyLog shows the proxyLogGrepPattern (BASIC AUTH)
		// 1292545301.350    418 10.16.120.247 TCP_MISS/200 1438 CONNECT jsefler-f12-candlepin.usersys.redhat.com:8443 redhat DIRECT/10.16.120.146 -
		// 1292551602.625      0 10.16.120.247 TCP_DENIED/407 3840 CONNECT jsefler-f12-candlepin.usersys.redhat.com:8443 - NONE/- text/html
		
		// assert the tail of proxyLog shows the proxyLogGrepPattern (NO AUTH)
		// CONNECT   Dec 17 18:56:22 [20793]: Connect (file descriptor 7):  [10.16.120.248]
		// CONNECT   Dec 17 18:56:22 [20793]: Request (file descriptor 7): CONNECT jsefler-f12-candlepin.usersys.redhat.com:8443 HTTP/1.1
		// INFO      Dec 17 18:56:22 [20793]: No proxy for jsefler-f12-candlepin.usersys.redhat.com
		// CONNECT   Dec 17 18:56:22 [20793]: Established connection to host "jsefler-f12-candlepin.usersys.redhat.com" using file descriptor 8.
		// INFO      Dec 17 18:56:22 [20793]: Not sending client headers to remote machine
		// INFO      Dec 17 18:56:22 [20793]: Closed connection between local client (fd:7) and remote client (fd:8)
		
		if (proxyLogGrepPattern!=null) {
			String proxyLogResult = RemoteFileTasks.getTailFromMarkedFile(proxyRunner, proxyLog, proxyLogMarker, clienttasks.ipaddr);	// accounts for multiple tests hitting the same proxy server simultaneously
			Assert.assertTrue(proxyLogResult.contains(proxyLogGrepPattern), "The tail of proxy server log '"+proxyLog+"' following marker '"+proxyLogMarker+"' contains expected connection '"+proxyLogGrepPattern+"' attempts from "+clienttasks.ipaddr+" to the candlepin server.");
		}
		
		// cleanup
		clienttasks.unregister_(null, null, null);
		
		
		// TEST PART 2: now let's assert that setting the proxy via CLI option or rhsm.conf take precedence over the environment variable
		
		// assemble the value of a httpProxyVar that will get overridden
		httpProxyEnvVar = validHttpProxyEnvVars.get(randomGenerator.nextInt(validHttpProxyEnvVars.size())) + "=https://";
		httpProxyEnvVar += "proxy.example.com:911";	// provided in https://bugzilla.redhat.com/show_bug.cgi?id=1031755#c0	Note: this does not have to be a working proxy to make this a valid test
		
		// set the config parameters
		updateConfFileProxyParameters(proxy_hostnameConfig, proxy_portConfig, proxy_userConfig, proxy_passwordConfig);
		RemoteFileTasks.runCommandAndWait(client,"grep proxy "+clienttasks.rhsmConfFile,TestRecords.action());
		
		// pad the tail of proxyLog with a message
		proxyLogMarker = System.currentTimeMillis()+" Testing "+moduleTask+" AttemptsUsingProxyServerDefinedByAnEnvironmentVariable_Test from "+clienttasks.hostname+"...";
		RemoteFileTasks.markFile(proxyRunner, proxyLog, proxyLogMarker);
		
		// attempt to register using a proxy server defined by an environment variable (no CLI option nor rhsm.conf [sever] proxy configurations set)
		attemptResult = client.runCommandAndWait(httpProxyEnvVar+" "+clienttasks.registerCommand(username, password, org, null, null, null, null, null, null, null, (List<String>)null, null, null, null, null, null, proxy, proxyuser, proxypassword));
		if (exitCode!=null)	Assert.assertEquals(attemptResult.getExitCode(), exitCode, "The exit code from a negative attempt to "+moduleTask+" using a proxy server to override environment variable '"+httpProxyEnvVar+"'.");
		if (stdout!=null)	Assert.assertEquals(attemptResult.getStdout().trim(), stdout, "The stdout from an attempt to "+moduleTask+" using a proxy server to override environment variable '"+httpProxyEnvVar+"'.");
		if (stderr!=null)	Assert.assertEquals(attemptResult.getStderr().trim(), stderr, "The stderr from an attempt to "+moduleTask+" using a proxy server to override environment variable '"+httpProxyEnvVar+"'.");
		
		// assert the tail of proxyLog shows the proxyLogGrepPattern (BASIC AUTH)
		// 1292545301.350    418 10.16.120.247 TCP_MISS/200 1438 CONNECT jsefler-f12-candlepin.usersys.redhat.com:8443 redhat DIRECT/10.16.120.146 -
		// 1292551602.625      0 10.16.120.247 TCP_DENIED/407 3840 CONNECT jsefler-f12-candlepin.usersys.redhat.com:8443 - NONE/- text/html
		
		// assert the tail of proxyLog shows the proxyLogGrepPattern (NO AUTH)
		// CONNECT   Dec 17 18:56:22 [20793]: Connect (file descriptor 7):  [10.16.120.248]
		// CONNECT   Dec 17 18:56:22 [20793]: Request (file descriptor 7): CONNECT jsefler-f12-candlepin.usersys.redhat.com:8443 HTTP/1.1
		// INFO      Dec 17 18:56:22 [20793]: No proxy for jsefler-f12-candlepin.usersys.redhat.com
		// CONNECT   Dec 17 18:56:22 [20793]: Established connection to host "jsefler-f12-candlepin.usersys.redhat.com" using file descriptor 8.
		// INFO      Dec 17 18:56:22 [20793]: Not sending client headers to remote machine
		// INFO      Dec 17 18:56:22 [20793]: Closed connection between local client (fd:7) and remote client (fd:8)
		
		if (proxyLogGrepPattern!=null) {
			String proxyLogResult = RemoteFileTasks.getTailFromMarkedFile(proxyRunner, proxyLog, proxyLogMarker, clienttasks.ipaddr);	// accounts for multiple tests hitting the same proxy server simultaneously
			Assert.assertTrue(proxyLogResult.contains(proxyLogGrepPattern), "The tail of proxy server log '"+proxyLog+"' following marker '"+proxyLogMarker+"' contains expected connection '"+proxyLogGrepPattern+"' attempts from "+clienttasks.ipaddr+" to the candlepin server.");
		}
	}
	
	
	
	
	
	
	
	
	// Candidates for an automated Test:
	// TODO Bug 744504 - [ALL LANG] [RHSM CLI] facts module - Run facts update with incorrect proxy url produces traceback. https://github.com/RedHatQE/rhsm-qe/issues/179
	
	
	// Configuration methods ***********************************************************************
	
	public static SSHCommandRunner basicauthproxy = null;
	public static SSHCommandRunner noauthproxy = null;
	public static String nErrMsg = null;

	@BeforeClass(groups={"setup"})
	public void setupBeforeClass() throws IOException {
		basicauthproxy = new SSHCommandRunner(sm_basicauthproxyHostname, sm_sshUser, sm_sshKeyPrivate, sm_sshkeyPassphrase, null);
		noauthproxy = new SSHCommandRunner(sm_noauthproxyHostname, sm_sshUser, sm_sshKeyPrivate, sm_sshkeyPassphrase, null);
		if (clienttasks!=null) nErrMsg = clienttasks.msg_NetworkErrorUnableToConnect;
	}
	
	@BeforeMethod(groups={"setup"})
	public void cleanRhsmConfigAndUnregisterBeforeMethod() {
		uncommentConfFileProxyParameters();
		updateConfFileProxyParameters("","","","");
		clienttasks.unregister(null, null, null);
	}
	
	@AfterClass(groups={"setup"})
	public void cleanRhsmConfigAfterClass() throws IOException {
		cleanRhsmConfigAndUnregisterBeforeMethod();
	}
	
	@BeforeGroups(value={"ProxyRedeemTests"}, groups={"setup"})
	public void createMockAssetFactsFile () {
		// create a facts file with a serialNumber that will clobber the true system facts
		Map<String,String> facts = new HashMap<String,String>();
		facts.put("dmi.system.manufacturer", "Dell Inc.");
		facts.put("dmi.system.serial_number", "5ABCDEF");	// Mock Setup For: "Your subscription activation is being processed and should be available soon. You will be notified via email once it is available. If you have any questions, additional information can be found here: https://access.redhat.com/kb/docs/DOC-53864."
		clienttasks.createFactsFileWithOverridingValues(facts);
	}
	
	@AfterGroups(value={"ProxyRedeemTests"}, groups={"setup"}, alwaysRun=true)
	public void deleteMockAssetFactsFile () {
		clienttasks.deleteFactsFileWithOverridingValues();
	}
	
	
	
	// Protected methods ***********************************************************************
	
	protected void uncommentConfFileProxyParameters() {
		clienttasks.uncommentConfFileParameter(clienttasks.rhsmConfFile, "proxy_hostname");
		clienttasks.uncommentConfFileParameter(clienttasks.rhsmConfFile, "proxy_port");
		clienttasks.uncommentConfFileParameter(clienttasks.rhsmConfFile, "proxy_user");
		clienttasks.uncommentConfFileParameter(clienttasks.rhsmConfFile, "proxy_password");
	}
	protected void updateConfFileProxyParameters(String proxy_hostname, String proxy_port, String proxy_user, String proxy_password) {
		clienttasks.updateConfFileParameter(clienttasks.rhsmConfFile, "proxy_hostname", proxy_hostname);
		clienttasks.updateConfFileParameter(clienttasks.rhsmConfFile, "proxy_port", proxy_port);
		clienttasks.updateConfFileParameter(clienttasks.rhsmConfFile, "proxy_user", proxy_user);
		clienttasks.updateConfFileParameter(clienttasks.rhsmConfFile, "proxy_password", proxy_password);
	}

	
	// Data Providers ***********************************************************************
	
	
	@DataProvider(name="getRegisterWithProxyConfigurationsCommentedOutOfRhsmConfigData")
	public Object[][] getRegisterWithProxyConfigurationsCommentedOutOfRhsmConfigDataAs2dArray() {
		return TestNGUtils.convertListOfListsTo2dArray(getRegisterWithProxyConfigurationsCommentedOutOfRhsmConfigDataAsListOfLists());
	}
	protected List<List<Object>> getRegisterWithProxyConfigurationsCommentedOutOfRhsmConfigDataAsListOfLists() {
		List<List<Object>> ll = new ArrayList<List<Object>>();

		// String[] proxyConfigs

		ll.add(Arrays.asList(new Object[] {null,	new String[]{"proxy_hostname"}  }));
		ll.add(Arrays.asList(new Object[] {null,	new String[]{"proxy_port"}  }));
		ll.add(Arrays.asList(new Object[] {new BlockedByBzBug("667829"), new String[]{"proxy_hostname", "proxy_port"} }));
		ll.add(Arrays.asList(new Object[] {null,	new String[]{"proxy_user"}  }));
		ll.add(Arrays.asList(new Object[] {null,	new String[]{"proxy_password"}  }));
		ll.add(Arrays.asList(new Object[] {null,	new String[]{"proxy_user", "proxy_password"}  }));
		ll.add(Arrays.asList(new Object[] {null,	new String[]{"proxy_hostname", "proxy_port", "proxy_user", "proxy_password"}  }));


		return ll;
	}
	
	
	@DataProvider(name="getRegisterAttemptsUsingProxyServerData")
	public Object[][] getRegisterAttemptsUsingProxyServerDataAs2dArray() {
		return TestNGUtils.convertListOfListsTo2dArray(getRegisterAttemptsUsingProxyServerDataAsListOfLists());
	}
	protected List<List<Object>> getRegisterAttemptsUsingProxyServerDataAsListOfLists() {
		List<List<Object>> ll = new ArrayList<List<Object>>(); if (!isSetupBeforeSuiteComplete) return ll;
		if (clienttasks==null) return ll;
		
		String basicauthproxyUrl = String.format("%s:%s", sm_basicauthproxyHostname,sm_basicauthproxyPort); basicauthproxyUrl = basicauthproxyUrl.replaceAll(":$", "");
		String noauthproxyUrl = String.format("%s:%s", sm_noauthproxyHostname,sm_noauthproxyPort); noauthproxyUrl = noauthproxyUrl.replaceAll(":$", "");
//		String nErrMsg = "Network error, unable to connect to server. Please see "+clienttasks.rhsmLogFile+" for more information.";
		String uErrMsg = servertasks.invalidCredentialsMsg(); //"Invalid username or password";
		String oErrMsg = /*"Organization/Owner bad-org does not exist."*/"Organization bad-org does not exist.";
		if (sm_serverType.equals(CandlepinType.katello))	oErrMsg = "Couldn't find organization 'bad-org'";
		
		// Object blockedByBug, String username, String password, String org, String proxy, String proxyuser, String proxypassword, Integer exitCode, String stdout, String stderr

		// basic auth proxy test data...
		ll.add(Arrays.asList(new Object[]{	new BlockedByBzBug(new String[]{"755258","838242"}),	sm_clientUsername,	sm_clientPassword,	sm_clientOrg,	basicauthproxyUrl,		sm_basicauthproxyUsername,		sm_basicauthproxyPassword,	Integer.valueOf(0),		null,		null}));
//debugTesting if(true) return ll;
		ll.add(Arrays.asList(new Object[]{	null,													sm_clientUsername,	sm_clientPassword,	sm_clientOrg,	"bad-proxy",			sm_basicauthproxyUsername,		sm_basicauthproxyPassword,	Integer.valueOf(255),	nErrMsg,	null}));
		ll.add(Arrays.asList(new Object[]{	null,													sm_clientUsername,	sm_clientPassword,	sm_clientOrg,	basicauthproxyUrl+"0",	sm_basicauthproxyUsername,		sm_basicauthproxyPassword,	Integer.valueOf(255),	nErrMsg,	null}));
		ll.add(Arrays.asList(new Object[]{	new BlockedByBzBug("755258"),							sm_clientUsername,	sm_clientPassword,	sm_clientOrg,	basicauthproxyUrl,		"bad-username",					sm_basicauthproxyPassword,	Integer.valueOf(255),	nErrMsg,	null}));
		ll.add(Arrays.asList(new Object[]{	new BlockedByBzBug("755258"),							sm_clientUsername,	sm_clientPassword,	sm_clientOrg,	basicauthproxyUrl,		sm_basicauthproxyUsername,		"bad-password",				Integer.valueOf(255),	nErrMsg,	null}));
		ll.add(Arrays.asList(new Object[]{	new BlockedByBzBug("755258"),							sm_clientUsername,	sm_clientPassword,	sm_clientOrg,	basicauthproxyUrl,		"bad-username",					"bad-password",				Integer.valueOf(255),	nErrMsg,	null}));
		ll.add(Arrays.asList(new Object[]{	new BlockedByBzBug("755258"),							sm_clientUsername,	sm_clientPassword,	sm_clientOrg,	basicauthproxyUrl,		null,							null,						Integer.valueOf(255),	nErrMsg,	null}));
		ll.add(Arrays.asList(new Object[]{	new BlockedByBzBug(new String[]{"755258","838242"}),	"bad-username",		sm_clientPassword,	sm_clientOrg,	basicauthproxyUrl,		sm_basicauthproxyUsername,		sm_basicauthproxyPassword,	Integer.valueOf(255),	null,		uErrMsg}));
		ll.add(Arrays.asList(new Object[]{	new BlockedByBzBug(new String[]{"755258","838242"}),	sm_clientUsername,	"bad-password",		sm_clientOrg,	basicauthproxyUrl,		sm_basicauthproxyUsername,		sm_basicauthproxyPassword,	Integer.valueOf(255),	null,		uErrMsg}));
		ll.add(Arrays.asList(new Object[]{	new BlockedByBzBug(new String[]{"755258","838242"}),	sm_clientUsername,	sm_clientPassword,	"bad-org",		basicauthproxyUrl,		sm_basicauthproxyUsername,		sm_basicauthproxyPassword,	Integer.valueOf(255),	null,		oErrMsg}));

		// no auth proxy test data...
		ll.add(Arrays.asList(new Object[]{	new BlockedByBzBug("755258"),							sm_clientUsername,	sm_clientPassword,	sm_clientOrg,	noauthproxyUrl,			null,						null,					Integer.valueOf(0),		null,		null}));
		ll.add(Arrays.asList(new Object[]{	new BlockedByBzBug("755258"),							sm_clientUsername,	sm_clientPassword,	sm_clientOrg,	noauthproxyUrl,			"ignored-username",			"ignored-password",		Integer.valueOf(0),		null,		null}));
		ll.add(Arrays.asList(new Object[]{	null,													sm_clientUsername,	sm_clientPassword,	sm_clientOrg,	noauthproxyUrl+"0",		null,						null,					Integer.valueOf(255),	nErrMsg,	null}));
		ll.add(Arrays.asList(new Object[]{	new BlockedByBzBug("755258"),							"bad-username",		sm_clientPassword,	sm_clientOrg,	noauthproxyUrl,			null,						null,					Integer.valueOf(255),	null,		uErrMsg}));
		ll.add(Arrays.asList(new Object[]{	new BlockedByBzBug("755258"),							sm_clientUsername,	"bad-password",		sm_clientOrg,	noauthproxyUrl,			null,						null,					Integer.valueOf(255),	null,		uErrMsg}));
		ll.add(Arrays.asList(new Object[]{	new BlockedByBzBug("755258"),							sm_clientUsername,	sm_clientPassword,	"bad-org",		noauthproxyUrl,			null,						null,					Integer.valueOf(255),	null,		oErrMsg}));

		return ll;
	}

	
	@DataProvider(name="getRegisterAttemptsUsingProxyServerViaRhsmConfigData")
	public Object[][] getRegisterAttemptsUsingProxyServerViaRhsmConfigDataAs2dArray() {
		return TestNGUtils.convertListOfListsTo2dArray(getRegisterAttemptsUsingProxyServerViaRhsmConfigDataAsListOfLists());
	}
	protected List<List<Object>> getRegisterAttemptsUsingProxyServerViaRhsmConfigDataAsListOfLists() {
		List<List<Object>> ll = new ArrayList<List<Object>>(); if (!isSetupBeforeSuiteComplete) return ll;
		if (clienttasks==null) return ll;
		
		String basicauthproxyUrl = String.format("%s:%s", sm_basicauthproxyHostname,sm_basicauthproxyPort); basicauthproxyUrl = basicauthproxyUrl.replaceAll(":$", "");
		String noauthproxyUrl = String.format("%s:%s", sm_noauthproxyHostname,sm_noauthproxyPort); noauthproxyUrl = noauthproxyUrl.replaceAll(":$", "");
//		String nErrMsg = "Network error, unable to connect to server. Please see "+clienttasks.rhsmLogFile+" for more information.";
		String uErrMsg = servertasks.invalidCredentialsMsg(); //"Invalid username or password";
		String oErrMsg = /*"Organization/Owner bad-org does not exist."*/"Organization bad-org does not exist.";
		if (sm_serverType.equals(CandlepinType.katello))	oErrMsg = "Couldn't find organization 'bad-org'";
		
		
		// Object blockedByBug, String username, String password, Sring org, String proxy, String proxyuser, String proxypassword, String proxy_hostnameConfig, String proxy_portConfig, String proxy_userConfig, String proxy_passwordConfig, Integer exitCode, String stdout, String stderr, SSHCommandRunner proxyRunner, String proxyLog, String proxyLogGrepPattern
		// 									blockedByBug,											username,			password,			org,			proxy,				proxyuser,					proxypassword,				proxy_hostnameConfig,		proxy_portConfig,			proxy_userConfig,			proxy_passwordConfig,		exitCode,				stdout,		stderr,		proxyRunner,	proxyLog,				proxyLogGrepPattern
		// basic auth proxy test data...
		ll.add(Arrays.asList(new Object[]{	new BlockedByBzBug("755258"),							sm_clientUsername,	sm_clientPassword,	sm_clientOrg,	null,				null,						null,						sm_basicauthproxyHostname,	sm_basicauthproxyPort,		sm_basicauthproxyUsername,	sm_basicauthproxyPassword,	Integer.valueOf(0),		null,		null,		basicauthproxy,	sm_basicauthproxyLog,	"TCP_MISS"}));
//debugTesting if(true) return ll;
		ll.add(Arrays.asList(new Object[]{	null,													sm_clientUsername,	sm_clientPassword,	sm_clientOrg,	null,				null,						null,						"bad-proxy",				sm_basicauthproxyPort,		sm_basicauthproxyUsername,	sm_basicauthproxyPassword,	Integer.valueOf(255),	nErrMsg,	null,		basicauthproxy,	sm_basicauthproxyLog,	null}));
		ll.add(Arrays.asList(new Object[]{	null,													sm_clientUsername,	sm_clientPassword,	sm_clientOrg,	null,				null,						null,						sm_basicauthproxyHostname,	sm_basicauthproxyPort+"0",	sm_basicauthproxyUsername,	sm_basicauthproxyPassword,	Integer.valueOf(255),	nErrMsg,	null,		basicauthproxy,	sm_basicauthproxyLog,	null}));
		ll.add(Arrays.asList(new Object[]{	new BlockedByBzBug("755258"),							sm_clientUsername,	sm_clientPassword,	sm_clientOrg,	null,				null,						null,						sm_basicauthproxyHostname,	sm_basicauthproxyPort,		"bad-username",				sm_basicauthproxyPassword,	Integer.valueOf(255),	nErrMsg,	null,		basicauthproxy,	sm_basicauthproxyLog,	"TCP_DENIED"}));
		ll.add(Arrays.asList(new Object[]{	new BlockedByBzBug("755258"),							sm_clientUsername,	sm_clientPassword,	sm_clientOrg,	null,				null,						null,						sm_basicauthproxyHostname,	sm_basicauthproxyPort,		sm_basicauthproxyUsername,	"bad-password",				Integer.valueOf(255),	nErrMsg,	null,		basicauthproxy,	sm_basicauthproxyLog,	"TCP_DENIED"}));
		ll.add(Arrays.asList(new Object[]{	new BlockedByBzBug("755258"),							sm_clientUsername,	sm_clientPassword,	sm_clientOrg,	null,				null,						null,						sm_basicauthproxyHostname,	sm_basicauthproxyPort,		"bad-username",				"bad-password",				Integer.valueOf(255),	nErrMsg,	null,		basicauthproxy,	sm_basicauthproxyLog,	"TCP_DENIED"}));
		ll.add(Arrays.asList(new Object[]{	new BlockedByBzBug("755258"),							sm_clientUsername,	sm_clientPassword,	sm_clientOrg,	null,				null,						null,						sm_basicauthproxyHostname,	sm_basicauthproxyPort,		""/*no username*/,			""/*no password*/,			Integer.valueOf(255),	nErrMsg,	null,		basicauthproxy,	sm_basicauthproxyLog,	"TCP_DENIED"}));
		ll.add(Arrays.asList(new Object[]{	new BlockedByBzBug("755258"),							sm_clientUsername,	sm_clientPassword,	sm_clientOrg,	basicauthproxyUrl,	null,						null,						"bad-proxy",				sm_basicauthproxyPort+"0",	sm_basicauthproxyUsername,	sm_basicauthproxyPassword,	Integer.valueOf(0),		null,		null,		basicauthproxy,	sm_basicauthproxyLog,	"TCP_MISS"}));
		ll.add(Arrays.asList(new Object[]{	new BlockedByBzBug("755258"),							sm_clientUsername,	sm_clientPassword,	sm_clientOrg,	basicauthproxyUrl,	sm_basicauthproxyUsername,	null,						"bad-proxy",				sm_basicauthproxyPort+"0",	"bad-username",				sm_basicauthproxyPassword,	Integer.valueOf(0),		null,		null,		basicauthproxy,	sm_basicauthproxyLog,	"TCP_MISS"}));
		ll.add(Arrays.asList(new Object[]{	new BlockedByBzBug(new String[]{"755258","838242"}),	sm_clientUsername,	sm_clientPassword,	sm_clientOrg,	basicauthproxyUrl,	sm_basicauthproxyUsername,	sm_basicauthproxyPassword,	"bad-proxy",				sm_basicauthproxyPort+"0",	"bad-username",				"bad-password",				Integer.valueOf(0),		null,		null,		basicauthproxy,	sm_basicauthproxyLog,	"TCP_MISS"}));
		ll.add(Arrays.asList(new Object[]{	new BlockedByBzBug("755258"),							sm_clientUsername,	sm_clientPassword,	sm_clientOrg,	null,				null,						null,						sm_basicauthproxyHostname,	sm_basicauthproxyPort,		sm_basicauthproxyUsername,	sm_basicauthproxyPassword,	Integer.valueOf(0),		null,		null,		basicauthproxy,	sm_basicauthproxyLog,	"TCP_MISS"}));
		ll.add(Arrays.asList(new Object[]{	new BlockedByBzBug("755258"),							"bad-username",		sm_clientPassword,	sm_clientOrg,	null,				null,						null,						sm_basicauthproxyHostname,	sm_basicauthproxyPort,		sm_basicauthproxyUsername,	sm_basicauthproxyPassword,	Integer.valueOf(255),	null,		uErrMsg,	basicauthproxy,	sm_basicauthproxyLog,	"TCP_MISS"}));
		ll.add(Arrays.asList(new Object[]{	new BlockedByBzBug("755258"),							sm_clientUsername,	"bad-password",		sm_clientOrg,	null,				null,						null,						sm_basicauthproxyHostname,	sm_basicauthproxyPort,		sm_basicauthproxyUsername,	sm_basicauthproxyPassword,	Integer.valueOf(255),	null,		uErrMsg,	basicauthproxy,	sm_basicauthproxyLog,	"TCP_MISS"}));
		ll.add(Arrays.asList(new Object[]{	new BlockedByBzBug("755258"),							sm_clientUsername,	sm_clientPassword,	"bad-org",		null,				null,						null,						sm_basicauthproxyHostname,	sm_basicauthproxyPort,		sm_basicauthproxyUsername,	sm_basicauthproxyPassword,	Integer.valueOf(255),	null,		oErrMsg,	basicauthproxy,	sm_basicauthproxyLog,	"TCP_MISS"}));

		// no auth proxy test data...
		ll.add(Arrays.asList(new Object[]{	new BlockedByBzBug("755258"),							sm_clientUsername,	sm_clientPassword,	sm_clientOrg,	null,				null,						null,						sm_noauthproxyHostname,	sm_noauthproxyPort,		"",							"",						Integer.valueOf(0),		null,		null,		noauthproxy,	sm_noauthproxyLog,		"Connect"}));
		ll.add(Arrays.asList(new Object[]{	new BlockedByBzBug("755258"),							sm_clientUsername,	sm_clientPassword,	sm_clientOrg,	null,				null,						null,						sm_noauthproxyHostname,	sm_noauthproxyPort,		"ignored-username",			"ignored-password",		Integer.valueOf(0),		null,		null,		noauthproxy,	sm_noauthproxyLog,		"Connect"}));
		ll.add(Arrays.asList(new Object[]{	null,													sm_clientUsername,	sm_clientPassword,	sm_clientOrg,	null,				null,						null,						sm_noauthproxyHostname,	sm_noauthproxyPort+"0",	"",							"",						Integer.valueOf(255),	nErrMsg,	null,		noauthproxy,	sm_noauthproxyLog,		null}));
		ll.add(Arrays.asList(new Object[]{	null,													sm_clientUsername,	sm_clientPassword,	sm_clientOrg,	null,				null,						null,						"bad-proxy",			sm_noauthproxyPort,		"",							"",						Integer.valueOf(255),	nErrMsg,	null,		noauthproxy,	sm_noauthproxyLog,		null}));
		ll.add(Arrays.asList(new Object[]{	new BlockedByBzBug("755258"),							"bad-username",		sm_clientPassword,	sm_clientOrg,	null,				null,						null,						sm_noauthproxyHostname,	sm_noauthproxyPort,		"",							"",						Integer.valueOf(255),	null,		uErrMsg,	noauthproxy,	sm_noauthproxyLog,		"Connect"}));
		ll.add(Arrays.asList(new Object[]{	new BlockedByBzBug("755258"),							sm_clientUsername,	"bad-password",		sm_clientOrg,	null,				null,						null,						sm_noauthproxyHostname,	sm_noauthproxyPort,		"",							"",						Integer.valueOf(255),	null,		uErrMsg,	noauthproxy,	sm_noauthproxyLog,		"Connect"}));
		ll.add(Arrays.asList(new Object[]{	new BlockedByBzBug("755258"),							sm_clientUsername,	sm_clientPassword,	"bad-org",		null,				null,						null,						sm_noauthproxyHostname,	sm_noauthproxyPort,		"",							"",						Integer.valueOf(255),	null,		oErrMsg,	noauthproxy,	sm_noauthproxyLog,		"Connect"}));
		ll.add(Arrays.asList(new Object[]{	new BlockedByBzBug("755258"),							sm_clientUsername,	sm_clientPassword,	sm_clientOrg,	noauthproxyUrl,		null,						null,						"bad-proxy",			sm_noauthproxyPort+"0",	"",							"",						Integer.valueOf(0),		null,		null,		noauthproxy,	sm_noauthproxyLog,		"Connect"}));
		ll.add(Arrays.asList(new Object[]{	new BlockedByBzBug("755258"),							sm_clientUsername,	sm_clientPassword,	sm_clientOrg,	noauthproxyUrl,		"ignored-username",			"ignored-password",			"bad-proxy",			sm_noauthproxyPort+"0",	"bad-username",				"bad-password",			Integer.valueOf(0),		null,		null,		noauthproxy,	sm_noauthproxyLog,		"Connect"}));
		ll.add(Arrays.asList(new Object[]{	null,													sm_clientUsername,	sm_clientPassword,	sm_clientOrg,	"bad-proxy",		null,						null,						sm_noauthproxyHostname,	sm_noauthproxyPort,		"",							"",						Integer.valueOf(255),	nErrMsg,	null,		noauthproxy,	sm_noauthproxyLog,		null}));


		return ll;
	}
	protected List<List<Object>> getValidRegisterAttemptsUsingProxyServerDataAsListOfLists() {
		List<List<Object>> ll = new ArrayList<List<Object>>();
		for (List<Object> l : getRegisterAttemptsUsingProxyServerDataAsListOfLists()) {
			// only include dataProvided rows where username, password, and org are valid
			if (!(l.get(1).equals(sm_clientUsername) && l.get(2).equals(sm_clientPassword) && l.get(3)==sm_clientOrg)) continue;
			if (l.get(8)==nErrMsg) l.set(0,new BlockedByBzBug("838264"));

			ll.add(l);
		}
		return ll;
	}
	protected List<List<Object>> getValidRegisterAttemptsUsingProxyServerViaRhsmConfigDataAsListOfLists() {
		List<List<Object>> ll = new ArrayList<List<Object>>();
		for (List<Object> l : getRegisterAttemptsUsingProxyServerViaRhsmConfigDataAsListOfLists()) {
			// only include dataProvided rows where username, password, and org are valid
			if (!(l.get(1).equals(sm_clientUsername) && l.get(2).equals(sm_clientPassword) && l.get(3)==sm_clientOrg)) continue;
			if (l.get(12)==nErrMsg) l.set(0,new BlockedByBzBug("838264"));
			
			ll.add(l);
		}
		return ll;
	}
	
	
	
	@DataProvider(name="getIdentityAttemptsUsingProxyServerData")
	public Object[][] getIdentityAttemptsUsingProxyServerDataAs2dArray() {
		return TestNGUtils.convertListOfListsTo2dArray(getIdentityAttemptsUsingProxyServerDataAsListOfLists());
	}
	protected List<List<Object>> getIdentityAttemptsUsingProxyServerDataAsListOfLists() {
		List<List<Object>> ll = new ArrayList<List<Object>>();
		for (List<Object> l : getRegisterAttemptsUsingProxyServerDataAsListOfLists()) {
			// only include dataProvided rows where org is valid
			if (l.get(3).equals(sm_clientOrg)) {
				
				// get the existing BlockedByBzBug
				BlockedByBzBug blockedByBzBug = (BlockedByBzBug) l.get(0);
				List<String> bugIds = blockedByBzBug==null?new ArrayList<String>():new ArrayList<String>(Arrays.asList(blockedByBzBug.getBugIds()));
				// add more BlockedByBzBug to rows that are expecting a network error
				if (l.get(8)==nErrMsg) {
					bugIds.add("848195");	// Bug 848195 	Error while checking server version: Proxy connection failed: 407 
					bugIds.add("848190");	// Bug 848190 	Error while checking server version: (111, 'Connection refused') 
					bugIds.add("848184");	// Bug 848184 	Error while checking server version: (-2, 'Name or service not known') 
				}
				blockedByBzBug = new BlockedByBzBug(bugIds.toArray(new String[]{}));
				
				ll.add(Arrays.asList(new Object[]{	blockedByBzBug,	l.get(1),	l.get(2),	l.get(3),	l.get(4),	l.get(5),	l.get(6),	l.get(7),	l.get(8),	l.get(9)}));
			}
		}
		return ll;
	}
	
	
	@DataProvider(name="getIdentityAttemptsUsingProxyServerViaRhsmConfigData")
	public Object[][] getIdentityAttemptsUsingProxyServerViaRhsmConfigDataAs2dArray() {
		return TestNGUtils.convertListOfListsTo2dArray(getIdentityAttemptsUsingProxyServerViaRhsmConfigDataAsListOfLists());
	}
	protected List<List<Object>> getIdentityAttemptsUsingProxyServerViaRhsmConfigDataAsListOfLists() {
		List<List<Object>> ll = new ArrayList<List<Object>>();
		for (List<Object> l : getRegisterAttemptsUsingProxyServerViaRhsmConfigDataAsListOfLists()) {
			// only include dataProvided rows where org is valid
			if (l.get(3).equals(sm_clientOrg)) {
				
				// get the existing BlockedByBzBug
				BlockedByBzBug blockedByBzBug = (BlockedByBzBug) l.get(0);
				List<String> bugIds = blockedByBzBug==null?new ArrayList<String>():new ArrayList<String>(Arrays.asList(blockedByBzBug.getBugIds()));
				// add more BlockedByBzBug to rows that are expecting a network error
				if (l.get(12)==nErrMsg) {
					bugIds.add("848195");	// Bug 848195 	Error while checking server version: Proxy connection failed: 407 
					bugIds.add("848190");	// Bug 848190 	Error while checking server version: (111, 'Connection refused') 
					bugIds.add("848184");	// Bug 848184 	Error while checking server version: (-2, 'Name or service not known') 
				}
				blockedByBzBug = new BlockedByBzBug(bugIds.toArray(new String[]{}));
				
				ll.add(Arrays.asList(new Object[]{	blockedByBzBug,	l.get(1),	l.get(2),	l.get(3),	l.get(4),	l.get(5),	l.get(6),	l.get(7),	l.get(8),	l.get(9),	l.get(10),	l.get(11),	l.get(12),	l.get(13),	l.get(14),	l.get(15),	l.get(16)}));
			}
		}
		return ll;
	}
	
	
	@DataProvider(name="getOrgsAttemptsUsingProxyServerData")
	public Object[][] getOrgsAttemptsUsingProxyServerDataAs2dArray() {
		return TestNGUtils.convertListOfListsTo2dArray(getOrgsAttemptsUsingProxyServerDataAsListOfLists());
	}
	protected List<List<Object>> getOrgsAttemptsUsingProxyServerDataAsListOfLists() {
		List<List<Object>> ll = new ArrayList<List<Object>>();
		for (List<Object> l : getRegisterAttemptsUsingProxyServerDataAsListOfLists()) {
			// only include dataProvided rows where org is valid
			if (l.get(3).equals(sm_clientOrg)) ll.add(l);
		}
		return ll;
	}
	
	
	@DataProvider(name="getOrgsAttemptsUsingProxyServerViaRhsmConfigData")
	public Object[][] getOrgsAttemptsUsingProxyServerViaRhsmConfigDataAs2dArray() {
		return TestNGUtils.convertListOfListsTo2dArray(getOrgsAttemptsUsingProxyServerViaRhsmConfigDataAsListOfLists());
	}
	protected List<List<Object>> getOrgsAttemptsUsingProxyServerViaRhsmConfigDataAsListOfLists() {
		List<List<Object>> ll = new ArrayList<List<Object>>();
		for (List<Object> l : getRegisterAttemptsUsingProxyServerViaRhsmConfigDataAsListOfLists()) {
			// only include dataProvided rows where org is valid
			if (l.get(3).equals(sm_clientOrg)) ll.add(l);
		}
		return ll;
	}
	
	
	@DataProvider(name="getServiceLevelAttemptsUsingProxyServerData")
	public Object[][] getServiceLevelAttemptsUsingProxyServerDataAs2dArray() {
		return TestNGUtils.convertListOfListsTo2dArray(getServiceLevelAttemptsUsingProxyServerDataAsListOfLists());
	}
	protected List<List<Object>> getServiceLevelAttemptsUsingProxyServerDataAsListOfLists() {
		List<List<Object>> ll = new ArrayList<List<Object>>();
		for (List<Object> l : getRegisterAttemptsUsingProxyServerDataAsListOfLists()) {
			// only include dataProvided rows where org is valid
			if (l.get(3).equals(sm_clientOrg)) ll.add(l);
		}
		return ll;
	}
	
	
	@DataProvider(name="getServiceLevelAttemptsUsingProxyServerViaRhsmConfigData")
	public Object[][] getServiceLevelAttemptsUsingProxyServerViaRhsmConfigDataAs2dArray() {
		return TestNGUtils.convertListOfListsTo2dArray(getServiceLevelAttemptsUsingProxyServerViaRhsmConfigDataAsListOfLists());
	}
	protected List<List<Object>> getServiceLevelAttemptsUsingProxyServerViaRhsmConfigDataAsListOfLists() {
		List<List<Object>> ll = new ArrayList<List<Object>>();
		for (List<Object> l : getRegisterAttemptsUsingProxyServerViaRhsmConfigDataAsListOfLists()) {
			// only include dataProvided rows where org is valid
			if (l.get(3).equals(sm_clientOrg)) ll.add(l);
		}
		return ll;
	}
	
	
	@DataProvider(name="getEnvironmentsAttemptsUsingProxyServerData")
	public Object[][] getEnvironmentsAttemptsUsingProxyServerDataAs2dArray() {
		return TestNGUtils.convertListOfListsTo2dArray(getEnvironmentsAttemptsUsingProxyServerDataAsListOfLists());
	}
	protected List<List<Object>> getEnvironmentsAttemptsUsingProxyServerDataAsListOfLists() {
		List<List<Object>> ll = new ArrayList<List<Object>>();
		for (List<Object> l : getRegisterAttemptsUsingProxyServerDataAsListOfLists()) {
			// only include dataProvided rows where org is valid
			//FIXME? if (l.get(3).equals(sm_clientOrg)) ll.add(l); continue;
			
			//	ll.add(Arrays.asList(new Object[]{	new BlockedByBzBug("755258"),	sm_clientUsername,	sm_clientPassword,	"bad-org",		basicauthproxyUrl,		sm_basicauthproxyUsername,		sm_basicauthproxyPassword,	Integer.valueOf(255),	null,		oErrMsg}));
		//	if (!sm_serverType.equals("katello") && !l.get(3).equals(sm_clientOrg)) {
			if (!sm_serverType.equals("katello") && (!l.get(1).equals(sm_clientUsername) || !l.get(2).equals(sm_clientPassword) || !l.get(3).equals(sm_clientOrg))) {
				// subscription-manager environments --username=testuser1 --password=password --org=bad-org
				// Stdout: This system does not support environments.
				// Stderr:
				// ExitCode: 0
				l.set(7, Integer.valueOf(0));	// exitCode
				l.set(8,"Error: Server does not support environments.");
				l.set(9,"");
			}
			ll.add(l);
		}
		return ll;
	}
	
	
	@DataProvider(name="getEnvironmentsAttemptsUsingProxyServerViaRhsmConfigData")
	public Object[][] getEnvironmentsAttemptsUsingProxyServerViaRhsmConfigDataAs2dArray() {
		return TestNGUtils.convertListOfListsTo2dArray(getEnvironmentsAttemptsUsingProxyServerViaRhsmConfigDataAsListOfLists());
	}
	protected List<List<Object>> getEnvironmentsAttemptsUsingProxyServerViaRhsmConfigDataAsListOfLists() {
		List<List<Object>> ll = new ArrayList<List<Object>>();
		for (List<Object> l : getRegisterAttemptsUsingProxyServerViaRhsmConfigDataAsListOfLists()) {
			// only include dataProvided rows where org is valid
			//FIXME? if (l.get(3).equals(sm_clientOrg)) ll.add(l); continue;
			
			// ll.add(Arrays.asList(new Object[]{	new BlockedByBzBug("755258"),	sm_clientUsername,	sm_clientPassword,	"bad-org",		null,				null,						null,						sm_basicauthproxyHostname,	sm_basicauthproxyPort,		sm_basicauthproxyUsername,	sm_basicauthproxyPassword,	Integer.valueOf(255),	null,		oErrMsg,	basicauthproxy,	sm_basicauthproxyLog,	"TCP_MISS"}));
		//	if (!sm_serverType.equals("katello") && !l.get(3).equals(sm_clientOrg)) {
			if (!sm_serverType.equals("katello") && (!l.get(1).equals(sm_clientUsername) || !l.get(2).equals(sm_clientPassword) || !l.get(3).equals(sm_clientOrg))) {
				// subscription-manager environments --username=testuser1 --password=password --org=bad-org --proxy=auto-services.usersys.redhat.com:3128 --proxyuser=redhat --proxypassword=redhat
				// Stdout: This system does not support environments.
				// Stderr:
				// ExitCode: 0
				l.set(11, Integer.valueOf(0));	// exitCode
				l.set(12,"Error: Server does not support environments.");
				l.set(13,"");
			}
			ll.add(l);
		}
		return ll;
	}
	
	
	@DataProvider(name="getUnregisterAttemptsUsingProxyServerData")
	public Object[][] getUnregisterAttemptsUsingProxyServerDataAs2dArray() {
		return TestNGUtils.convertListOfListsTo2dArray(getValidRegisterAttemptsUsingProxyServerDataAsListOfLists());
	}
	
	
	@DataProvider(name="getUnregisterAttemptsUsingProxyServerViaRhsmConfigData")
	public Object[][] getUnregisterAttemptsUsingProxyServerViaRhsmConfigDataAs2dArray() {
		return TestNGUtils.convertListOfListsTo2dArray(getValidRegisterAttemptsUsingProxyServerViaRhsmConfigDataAsListOfLists());
	}
	
	
	@DataProvider(name="getListAttemptsUsingProxyServerData")
	public Object[][] getListAttemptsUsingProxyServerDataAs2dArray() {
		return TestNGUtils.convertListOfListsTo2dArray(getValidRegisterAttemptsUsingProxyServerDataAsListOfLists());
	}
	
	
	@DataProvider(name="getListAttemptsUsingProxyServerViaRhsmConfigData")
	public Object[][] getListAttemptsUsingProxyServerViaRhsmConfigDataAs2dArray() {
		return TestNGUtils.convertListOfListsTo2dArray(getValidRegisterAttemptsUsingProxyServerViaRhsmConfigDataAsListOfLists());
	}
	
	
	@DataProvider(name="getStatusAttemptsUsingProxyServerData")
	public Object[][] getStatusAttemptsUsingProxyServerDataAs2dArray() {
		//return TestNGUtils.convertListOfListsTo2dArray(getValidRegisterAttemptsUsingProxyServerDataAsListOfLists());
		List<List<Object>> ll = new ArrayList<List<Object>>();
		for (List<Object> l : getValidRegisterAttemptsUsingProxyServerDataAsListOfLists()) {			
			BlockedByBzBug blockedByBzBug = null;	// nullify the blockedByBug parameter since this function was originally not blocked by any bug
			// Note: Because the status module should return cached results when it fails to connect to the server, the exitCode should always be 0 and we'll null out the asserts on stdout and stderr
			ll.add(Arrays.asList(new Object[]{	blockedByBzBug,	l.get(1),	l.get(2),	l.get(3),	l.get(4),	l.get(5),	l.get(6),	Integer.valueOf(0)/*exitCode*/,null/*stdout*/,null/*stderr*/}));
		}
		return TestNGUtils.convertListOfListsTo2dArray(ll);
	}
	
	
	@DataProvider(name="getStatusAttemptsUsingProxyServerViaRhsmConfigData")
	public Object[][] getStatusAttemptsUsingProxyServerViaRhsmConfigDataAs2dArray() {
		//return TestNGUtils.convertListOfListsTo2dArray(getValidRegisterAttemptsUsingProxyServerViaRhsmConfigDataAsListOfLists());
		List<List<Object>> ll = new ArrayList<List<Object>>();
		for (List<Object> l : getValidRegisterAttemptsUsingProxyServerViaRhsmConfigDataAsListOfLists()) {
			BlockedByBzBug blockedByBzBug = null;	// nullify the blockedByBug parameter since this function was originally not blocked by any bug
			// Note: Because the status module should return cached results when it fails to connect to the server, the exitCode should always be 0 and we'll null out the asserts on stdout and stderr
			ll.add(Arrays.asList(new Object[]{	blockedByBzBug,	l.get(1),	l.get(2),	l.get(3),	l.get(4),	l.get(5),	l.get(6),	l.get(7),	l.get(8),	l.get(9),	l.get(10),	Integer.valueOf(0)/*exitCode*/,null/*stdout*/,null/*stderr*/,	l.get(14),	l.get(15),	l.get(16)}));
		}
		return TestNGUtils.convertListOfListsTo2dArray(ll);
	}
	
	
	@DataProvider(name="getVersionAttemptsUsingProxyServerData")
	public Object[][] getVersionAttemptsUsingProxyServerDataAs2dArray() {
		//return TestNGUtils.convertListOfListsTo2dArray(getValidRegisterAttemptsUsingProxyServerDataAsListOfLists());
		List<List<Object>> ll = new ArrayList<List<Object>>();
		for (List<Object> l : getValidRegisterAttemptsUsingProxyServerDataAsListOfLists()) {			
			BlockedByBzBug blockedByBzBug = null;	// nullify the blockedByBug parameter since this function was originally not blocked by any bug
			// Note: The version module should always succeed, yet report subscription management server: Unknown when connection fails to the server
			if (l.get(8)==nErrMsg) {
				ll.add(Arrays.asList(new Object[]{	blockedByBzBug,	l.get(1),	l.get(2),	l.get(3),	l.get(4),	l.get(5),	l.get(6),	Integer.valueOf(0)/*exitCode*/,"subscription management server: Unknown"/*stdout*/,""/*stderr*/}));
			} else {
				ll.add(Arrays.asList(new Object[]{	blockedByBzBug,	l.get(1),	l.get(2),	l.get(3),	l.get(4),	l.get(5),	l.get(6),	Integer.valueOf(0)/*exitCode*/,"subscription management server: "+servertasks.statusVersion/*stdout*/,""/*stderr*/}));
			}
		}
		return TestNGUtils.convertListOfListsTo2dArray(ll);
	}
	
	
	@DataProvider(name="getVersionAttemptsUsingProxyServerViaRhsmConfigData")
	public Object[][] getVersionAttemptsUsingProxyServerViaRhsmConfigDataAs2dArray() {
		//return TestNGUtils.convertListOfListsTo2dArray(getValidRegisterAttemptsUsingProxyServerViaRhsmConfigDataAsListOfLists());
		List<List<Object>> ll = new ArrayList<List<Object>>();
		// Note: The version module should always succeed, yet report subscription management server: Unknown when connection fails to the server
		for (List<Object> l : getValidRegisterAttemptsUsingProxyServerViaRhsmConfigDataAsListOfLists()) {
			BlockedByBzBug blockedByBzBug = null;	// nullify the blockedByBug parameter since this function was originally not blocked by any bug
			// Note: The version module should always succeed, yet report subscription management server: Unknown when connection fails to the server
			if (l.get(12)==nErrMsg) {
				ll.add(Arrays.asList(new Object[]{	blockedByBzBug,	l.get(1),	l.get(2),	l.get(3),	l.get(4),	l.get(5),	l.get(6),	l.get(7),	l.get(8),	l.get(9),	l.get(10),	Integer.valueOf(0)/*exitCode*/,"subscription management server: Unknown"/*stdout*/,""/*stderr*/,	l.get(14),	l.get(15),	l.get(16)}));
			} else {
				ll.add(Arrays.asList(new Object[]{	blockedByBzBug,	l.get(1),	l.get(2),	l.get(3),	l.get(4),	l.get(5),	l.get(6),	l.get(7),	l.get(8),	l.get(9),	l.get(10),	Integer.valueOf(0)/*exitCode*/,"subscription management server: "+servertasks.statusVersion/*stdout*/,""/*stderr*/,	l.get(14),	l.get(15),	l.get(16)}));
			}
		}
		return TestNGUtils.convertListOfListsTo2dArray(ll);
	}
	
	
	@DataProvider(name="getAutoHealAttemptsUsingProxyServerData")
	public Object[][] getAutoHealAttemptsUsingProxyServerDataAs2dArray() {
		//return TestNGUtils.convertListOfListsTo2dArray(getValidRegisterAttemptsUsingProxyServerDataAsListOfLists());
		List<List<Object>> ll = new ArrayList<List<Object>>();
		for (List<Object> l : getValidRegisterAttemptsUsingProxyServerDataAsListOfLists()) {
			
			BlockedByBzBug blockedByBzBug = null;	// nullify the blockedByBug parameter since this function was originally not blocked by any bug
//			List<String> bugIds = blockedByBzBug==null?new ArrayList<String>():new ArrayList<String>(Arrays.asList(blockedByBzBug.getBugIds()));
//			// add BlockedByBzBug to rows that are expecting a network error
//			if (l.get(8)==nErrMsg) {
//				bugIds.add("848195");	// Bug 848195 	Error while checking server version: Proxy connection failed: 407 
//				bugIds.add("848190");	// Bug 848190 	Error while checking server version: (111, 'Connection refused') 
//				bugIds.add("848184");	// Bug 848184 	Error while checking server version: (-2, 'Name or service not known') 
//			}
//			blockedByBzBug = new BlockedByBzBug(bugIds.toArray(new String[]{}));
			
			ll.add(Arrays.asList(new Object[]{	blockedByBzBug,	l.get(1),	l.get(2),	l.get(3),	l.get(4),	l.get(5),	l.get(6),	l.get(7),	l.get(8),	l.get(9)}));
		}
		return TestNGUtils.convertListOfListsTo2dArray(ll);
	}
	
	
	@DataProvider(name="getAutoHealAttemptsUsingProxyServerViaRhsmConfigData")
	public Object[][] getAutoHealAttemptsUsingProxyServerViaRhsmConfigDataAs2dArray() {
		//return TestNGUtils.convertListOfListsTo2dArray(getValidRegisterAttemptsUsingProxyServerViaRhsmConfigDataAsListOfLists());
		List<List<Object>> ll = new ArrayList<List<Object>>();
		for (List<Object> l : getValidRegisterAttemptsUsingProxyServerViaRhsmConfigDataAsListOfLists()) {
			
			BlockedByBzBug blockedByBzBug = null;	// nullify the blockedByBug parameter since this function was originally not blocked by any bug
//			List<String> bugIds = blockedByBzBug==null?new ArrayList<String>():new ArrayList<String>(Arrays.asList(blockedByBzBug.getBugIds()));
//			// add BlockedByBzBug to rows that are expecting a network error
//			if (l.get(12)==nErrMsg) {
//				bugIds.add("848195");	// Bug 848195 	Error while checking server version: Proxy connection failed: 407 
//				bugIds.add("848190");	// Bug 848190 	Error while checking server version: (111, 'Connection refused') 
//				bugIds.add("848184");	// Bug 848184 	Error while checking server version: (-2, 'Name or service not known') 
//			}
//			blockedByBzBug = new BlockedByBzBug(bugIds.toArray(new String[]{}));
			
			ll.add(Arrays.asList(new Object[]{	blockedByBzBug,	l.get(1),	l.get(2),	l.get(3),	l.get(4),	l.get(5),	l.get(6),	l.get(7),	l.get(8),	l.get(9),	l.get(10),	l.get(11),	l.get(12),	l.get(13),	l.get(14),	l.get(15),	l.get(16)}));
		}
		return TestNGUtils.convertListOfListsTo2dArray(ll);
	}
	
	
	@DataProvider(name="getReleaseAttemptsUsingProxyServerData")
	public Object[][] getReleaseAttemptsUsingProxyServerDataAs2dArray() {
		//return TestNGUtils.convertListOfListsTo2dArray(getValidRegisterAttemptsUsingProxyServerDataAsListOfLists());
		List<List<Object>> ll = new ArrayList<List<Object>>();
		for (List<Object> l : getValidRegisterAttemptsUsingProxyServerDataAsListOfLists()) {
			
			BlockedByBzBug blockedByBzBug = null;	// nullify the blockedByBug parameter since this function was originally not blocked by any bug
			List<String> bugIds = blockedByBzBug==null?new ArrayList<String>():new ArrayList<String>(Arrays.asList(blockedByBzBug.getBugIds()));
			// add BlockedByBzBug to rows that are expecting a network error
			if (l.get(8)==nErrMsg) {
				bugIds.add("848195");	// Bug 848195 	Error while checking server version: Proxy connection failed: 407 
				bugIds.add("848190");	// Bug 848190 	Error while checking server version: (111, 'Connection refused') 
				bugIds.add("848184");	// Bug 848184 	Error while checking server version: (-2, 'Name or service not known') 
			}
			blockedByBzBug = new BlockedByBzBug(bugIds.toArray(new String[]{}));
			
			ll.add(Arrays.asList(new Object[]{	blockedByBzBug,	l.get(1),	l.get(2),	l.get(3),	l.get(4),	l.get(5),	l.get(6),	l.get(7),	l.get(8),	l.get(9)}));
		}
		return TestNGUtils.convertListOfListsTo2dArray(ll);
	}
	
	
	@DataProvider(name="getReleaseAttemptsUsingProxyServerViaRhsmConfigData")
	public Object[][] getReleaseAttemptsUsingProxyServerViaRhsmConfigDataAs2dArray() {
		//return TestNGUtils.convertListOfListsTo2dArray(getValidRegisterAttemptsUsingProxyServerViaRhsmConfigDataAsListOfLists());
		List<List<Object>> ll = new ArrayList<List<Object>>();
		for (List<Object> l : getValidRegisterAttemptsUsingProxyServerViaRhsmConfigDataAsListOfLists()) {
			
			BlockedByBzBug blockedByBzBug = null;	// nullify the blockedByBug parameter since this function was originally not blocked by any bug
			List<String> bugIds = blockedByBzBug==null?new ArrayList<String>():new ArrayList<String>(Arrays.asList(blockedByBzBug.getBugIds()));
			// add BlockedByBzBug to rows that are expecting a network error
			if (l.get(12)==nErrMsg) {
				bugIds.add("848195");	// Bug 848195 	Error while checking server version: Proxy connection failed: 407 
				bugIds.add("848190");	// Bug 848190 	Error while checking server version: (111, 'Connection refused') 
				bugIds.add("848184");	// Bug 848184 	Error while checking server version: (-2, 'Name or service not known') 
			}
			blockedByBzBug = new BlockedByBzBug(bugIds.toArray(new String[]{}));
			
			ll.add(Arrays.asList(new Object[]{	blockedByBzBug,	l.get(1),	l.get(2),	l.get(3),	l.get(4),	l.get(5),	l.get(6),	l.get(7),	l.get(8),	l.get(9),	l.get(10),	l.get(11),	l.get(12),	l.get(13),	l.get(14),	l.get(15),	l.get(16)}));
		}
		return TestNGUtils.convertListOfListsTo2dArray(ll);
	}
	
	
	@DataProvider(name="getRedeemAttemptsUsingProxyServerData")
	public Object[][] getRedeemAttemptsUsingProxyServerDataAs2dArray() {
		return TestNGUtils.convertListOfListsTo2dArray(getRedeemAttemptsUsingProxyServerDataAsListOfLists());
	}
	protected List<List<Object>> getRedeemAttemptsUsingProxyServerDataAsListOfLists() {
		List<List<Object>> ll = new ArrayList<List<Object>>();
		for (List<Object> l : getValidRegisterAttemptsUsingProxyServerDataAsListOfLists()) {
			// only block rows where stdout != null with BlockedByBzBug("732499")
			if (l.get(8)!=null) {
				// get the existing BlockedByBzBug 
				BlockedByBzBug blockedByBzBug = (BlockedByBzBug) l.get(0);
				List<String> bugIds = blockedByBzBug==null?new ArrayList<String>():new ArrayList<String>(Arrays.asList(blockedByBzBug.getBugIds()));
				// add Bug 732499 - 'gaierror' object has no attribute 'code' / 'error' object has no attribute 'code'
				bugIds.add("732499");
				blockedByBzBug = new BlockedByBzBug(bugIds.toArray(new String[]{}));
				
				ll.add(Arrays.asList(new Object[]{	blockedByBzBug,	l.get(1),	l.get(2),	l.get(3),	l.get(4),	l.get(5),	l.get(6),	l.get(7),	l.get(8),	l.get(9)}));
			} else {
				ll.add(l);
			}
		}
		return ll;
	}

	
	@DataProvider(name="getRedeemAttemptsUsingProxyServerViaRhsmConfigData")
	public Object[][] getRedeemAttemptsUsingProxyServerViaRhsmConfigDataAs2dArray() {
		return TestNGUtils.convertListOfListsTo2dArray(getRedeemAttemptsUsingProxyServerViaRhsmConfigDataAsListOfLists());
	}
	protected List<List<Object>> getRedeemAttemptsUsingProxyServerViaRhsmConfigDataAsListOfLists() {
		List<List<Object>> ll = new ArrayList<List<Object>>();
		for (List<Object> l : getValidRegisterAttemptsUsingProxyServerViaRhsmConfigDataAsListOfLists()) {
			// only block rows where stdout != null with BlockedByBzBug("732499")
			if (l.get(12)!=null) {
				// get the existing BlockedByBzBug 
				BlockedByBzBug blockedByBzBug = (BlockedByBzBug) l.get(0);
				List<String> bugIds = blockedByBzBug==null?new ArrayList<String>():new ArrayList<String>(Arrays.asList(blockedByBzBug.getBugIds()));
				// add Bug 732499 - 'gaierror' object has no attribute 'code' / 'error' object has no attribute 'code'
				bugIds.add("732499");
				blockedByBzBug = new BlockedByBzBug(bugIds.toArray(new String[]{}));
				
				ll.add(Arrays.asList(new Object[]{	blockedByBzBug,	l.get(1),	l.get(2),	l.get(3),	l.get(4),	l.get(5),	l.get(6),	l.get(7),	l.get(8),	l.get(9),	l.get(10),	l.get(11),	l.get(12),	l.get(13),	l.get(14),	l.get(15),	l.get(16)}));
			} else {
				ll.add(l);
			}
		}
		return ll;
	}
	
	
	@DataProvider(name="getFactsAttemptsUsingProxyServerData")
	public Object[][] getFactsAttemptsUsingProxyServerDataAs2dArray() {
		return TestNGUtils.convertListOfListsTo2dArray(getFactsAttemptsUsingProxyServerDataAsListOfLists());
	}
	protected List<List<Object>> getFactsAttemptsUsingProxyServerDataAsListOfLists() {
		List<List<Object>> ll = new ArrayList<List<Object>>();
		for (List<Object> l : getValidRegisterAttemptsUsingProxyServerDataAsListOfLists()) {
			if (l.get(8)!=null) {
//				ll.add(Arrays.asList(new Object[]{	l.get(0),	l.get(1),	l.get(2),	l.get(3),	l.get(4),	l.get(5),	l.get(6),	l.get(7),	null,	"Error updating system data, see /var/log/rhsm/rhsm.log for more details."}));
				ll.add(Arrays.asList(new Object[]{	l.get(0),	l.get(1),	l.get(2),	l.get(3),	l.get(4),	l.get(5),	l.get(6),	l.get(7),	null,	"Error updating system data on the server, see /var/log/rhsm/rhsm.log for more details."}));
			} else {
				ll.add(l);
			}
		}
		return ll;
	}
	
	@DataProvider(name="getFactsAttemptsUsingProxyServerViaRhsmConfigData")
	public Object[][] getFactsAttemptsUsingProxyServerViaRhsmConfigDataAs2dArray() {
		return TestNGUtils.convertListOfListsTo2dArray(getFactsAttemptsUsingProxyServerViaRhsmConfigDataAsListOfLists());
	}
	protected List<List<Object>> getFactsAttemptsUsingProxyServerViaRhsmConfigDataAsListOfLists() {
		List<List<Object>> ll = new ArrayList<List<Object>>();
		for (List<Object> l : getValidRegisterAttemptsUsingProxyServerViaRhsmConfigDataAsListOfLists()) {
			if (l.get(12)!=null) {
//				ll.add(Arrays.asList(new Object[]{	l.get(0),	l.get(1),	l.get(2),	l.get(3),	l.get(4),	l.get(5),	l.get(6),	l.get(7),	l.get(8),	l.get(9),	l.get(10),	l.get(11),	null,	"Error updating system data, see /var/log/rhsm/rhsm.log for more details.",	l.get(14),	l.get(15),	l.get(16)}));
				ll.add(Arrays.asList(new Object[]{	l.get(0),	l.get(1),	l.get(2),	l.get(3),	l.get(4),	l.get(5),	l.get(6),	l.get(7),	l.get(8),	l.get(9),	l.get(10),	l.get(11),	null,	"Error updating system data on the server, see /var/log/rhsm/rhsm.log for more details.",	l.get(14),	l.get(15),	l.get(16)}));
			} else {
				ll.add(l);
			}
		}
		return ll;
	}
	
	@DataProvider(name="getRefreshAttemptsUsingProxyServerData")
	public Object[][] getRefreshAttemptsUsingProxyServerDataAs2dArray() {
		//return TestNGUtils.convertListOfListsTo2dArray(getValidRegisterAttemptsUsingProxyServerDataAsListOfLists());
		List<List<Object>> ll = new ArrayList<List<Object>>();
		for (List<Object> l : getValidRegisterAttemptsUsingProxyServerDataAsListOfLists()) {
			
			// get the existing BlockedByBzBug
			BlockedByBzBug blockedByBzBug = (BlockedByBzBug) l.get(0);
			List<String> bugIds = blockedByBzBug==null?new ArrayList<String>():new ArrayList<String>(Arrays.asList(blockedByBzBug.getBugIds()));
			// add more BlockedByBzBug to rows that are expecting a network error
			if (l.get(8)==nErrMsg) {
				bugIds.add("975164");	// Bug 975164 - subscription-manager refresh with --proxy is silently failing in rhsm.log 
			}
			blockedByBzBug = new BlockedByBzBug(bugIds.toArray(new String[]{}));
			
			ll.add(Arrays.asList(new Object[]{	blockedByBzBug,	l.get(1),	l.get(2),	l.get(3),	l.get(4),	l.get(5),	l.get(6),	l.get(7),	l.get(8),	l.get(9)}));
		}
		return TestNGUtils.convertListOfListsTo2dArray(ll);
	}
	
	
	@DataProvider(name="getRefreshAttemptsUsingProxyServerViaRhsmConfigData")
	public Object[][] getRefreshAttemptsUsingProxyServerViaRhsmConfigDataAs2dArray() {
		//return TestNGUtils.convertListOfListsTo2dArray(getValidRegisterAttemptsUsingProxyServerViaRhsmConfigDataAsListOfLists());
		List<List<Object>> ll = new ArrayList<List<Object>>();
		for (List<Object> l : getValidRegisterAttemptsUsingProxyServerViaRhsmConfigDataAsListOfLists()) {
			
			// get the existing BlockedByBzBug
			BlockedByBzBug blockedByBzBug = (BlockedByBzBug) l.get(0);
			List<String> bugIds = blockedByBzBug==null?new ArrayList<String>():new ArrayList<String>(Arrays.asList(blockedByBzBug.getBugIds()));
			// add more BlockedByBzBug to rows that are expecting a network error
			if (l.get(12)==nErrMsg) {
				bugIds.add("975164");	// Bug 975164 - subscription-manager refresh with --proxy is silently failing in rhsm.log 
			}
			blockedByBzBug = new BlockedByBzBug(bugIds.toArray(new String[]{}));
			
			ll.add(Arrays.asList(new Object[]{	blockedByBzBug,	l.get(1),	l.get(2),	l.get(3),	l.get(4),	l.get(5),	l.get(6),	l.get(7),	l.get(8),	l.get(9),	l.get(10),	l.get(11),	l.get(12),	l.get(13),	l.get(14),	l.get(15),	l.get(16)}));
		}
		return TestNGUtils.convertListOfListsTo2dArray(ll);
	}
	
	
	@DataProvider(name="getReposAttemptsUsingProxyServerData")
	public Object[][] getReposAttemptsUsingProxyServerDataAs2dArray() {
		//return TestNGUtils.convertListOfListsTo2dArray(getValidRegisterAttemptsUsingProxyServerDataAsListOfLists());
		List<List<Object>> ll = new ArrayList<List<Object>>();
		for (List<Object> l : getValidRegisterAttemptsUsingProxyServerDataAsListOfLists()) {
			
			// get the existing BlockedByBzBug
			BlockedByBzBug blockedByBzBug = (BlockedByBzBug) l.get(0);
			List<String> bugIds = blockedByBzBug==null?new ArrayList<String>():new ArrayList<String>(Arrays.asList(blockedByBzBug.getBugIds()));
			// add more BlockedByBzBug to rows that are expecting a network error
			if (l.get(8)==nErrMsg) {
				bugIds.add("919255");	// Bug 919255 - negative proxy testing against subscription-manager repos --list 
			}
			if (l.get(4)!=null && l.get(4).equals("bad-proxy")) {
				bugIds.add("975186");	// Bug 975186 - subscription-manager repos --list is failing when specifying a bad --proxy
			}
			blockedByBzBug = new BlockedByBzBug(bugIds.toArray(new String[]{}));
			
			ll.add(Arrays.asList(new Object[]{	blockedByBzBug,	l.get(1),	l.get(2),	l.get(3),	l.get(4),	l.get(5),	l.get(6),	l.get(7),	l.get(8),	l.get(9)}));
		}
		return TestNGUtils.convertListOfListsTo2dArray(ll);
	}
	
	
	@DataProvider(name="getReposAttemptsUsingProxyServerViaRhsmConfigData")
	public Object[][] getReposAttemptsUsingProxyServerViaRhsmConfigDataAs2dArray() {
		//return TestNGUtils.convertListOfListsTo2dArray(getValidRegisterAttemptsUsingProxyServerViaRhsmConfigDataAsListOfLists());
		List<List<Object>> ll = new ArrayList<List<Object>>();
		for (List<Object> l : getValidRegisterAttemptsUsingProxyServerViaRhsmConfigDataAsListOfLists()) {
			
			// get the existing BlockedByBzBug
			BlockedByBzBug blockedByBzBug = (BlockedByBzBug) l.get(0);
			List<String> bugIds = blockedByBzBug==null?new ArrayList<String>():new ArrayList<String>(Arrays.asList(blockedByBzBug.getBugIds()));
			// add more BlockedByBzBug to rows that are expecting a network error
			if (l.get(12)==nErrMsg) {
				bugIds.add("919255");	// Bug 919255 - negative proxy testing against subscription-manager repos --list 
			}
			if (l.get(4)==null && l.get(7)!=null && l.get(7).equals("bad-proxy")) {
				bugIds.add("975186");	// Bug 975186 - subscription-manager repos --list is failing when specifying a bad --proxy
			}
			if (l.get(4)!=null && l.get(4).equals("bad-proxy") && l.get(7)!=null && l.get(7).equals(sm_noauthproxyHostname)) {
				bugIds.add("975186");	// Bug 975186 - subscription-manager repos --list is failing when specifying a bad --proxy
			}
			blockedByBzBug = new BlockedByBzBug(bugIds.toArray(new String[]{}));
			
			ll.add(Arrays.asList(new Object[]{	blockedByBzBug,	l.get(1),	l.get(2),	l.get(3),	l.get(4),	l.get(5),	l.get(6),	l.get(7),	l.get(8),	l.get(9),	l.get(10),	l.get(11),	l.get(12),	l.get(13),	l.get(14),	l.get(15),	l.get(16)}));
		}
		return TestNGUtils.convertListOfListsTo2dArray(ll);
	}
	
	
	@DataProvider(name="getRepoOverrideAttemptsUsingProxyServerData")
	public Object[][] getRepoOverrideAttemptsUsingProxyServerDataAs2dArray() {
		//return TestNGUtils.convertListOfListsTo2dArray(getValidRegisterAttemptsUsingProxyServerDataAsListOfLists());
		List<List<Object>> ll = new ArrayList<List<Object>>();
		for (List<Object> l : getValidRegisterAttemptsUsingProxyServerDataAsListOfLists()) {
			
			// get the existing BlockedByBzBug
			BlockedByBzBug blockedByBzBug = (BlockedByBzBug) l.get(0);
			List<String> bugIds = blockedByBzBug==null?new ArrayList<String>():new ArrayList<String>(Arrays.asList(blockedByBzBug.getBugIds()));
			// add more BlockedByBzBug to rows that are....
			blockedByBzBug = new BlockedByBzBug(bugIds.toArray(new String[]{}));
			blockedByBzBug = null;	// nullify the blockedByBug parameter since this function was originally not blocked by any bug
			blockedByBzBug = new BlockedByBzBug("1034396");
			l.set(0, blockedByBzBug);
			
			// Object blockedByBug, String username, String password, String org, String proxy, String proxyuser, String proxypassword, Integer exitCode, String stdout, String stderr
			
			if (l.get(7).equals(new Integer(0))) {
				/* valid prior to bug 1034396 
				l.set(7,new Integer(1));
				l.set(8,"This system does not have any subscriptions.");
				l.set(9,"");
				*/
				l.set(7,new Integer(0));
				l.set(8,"This system does not have any content overrides applied to it.");
				l.set(9,"");
			}
			ll.add(l);
		}
		return TestNGUtils.convertListOfListsTo2dArray(ll);
	}
	
	
	@DataProvider(name="getRepoOverrideAttemptsUsingProxyServerViaRhsmConfigData")
	public Object[][] getRepoOverrideAttemptsUsingProxyServerViaRhsmConfigDataAs2dArray() {
		//return TestNGUtils.convertListOfListsTo2dArray(getValidRegisterAttemptsUsingProxyServerViaRhsmConfigDataAsListOfLists());
		List<List<Object>> ll = new ArrayList<List<Object>>();
		for (List<Object> l : getValidRegisterAttemptsUsingProxyServerViaRhsmConfigDataAsListOfLists()) {
			
			// get the existing BlockedByBzBug
			BlockedByBzBug blockedByBzBug = (BlockedByBzBug) l.get(0);
			List<String> bugIds = blockedByBzBug==null?new ArrayList<String>():new ArrayList<String>(Arrays.asList(blockedByBzBug.getBugIds()));
			// add more BlockedByBzBug to rows that are....
			blockedByBzBug = new BlockedByBzBug(bugIds.toArray(new String[]{}));
			blockedByBzBug = null;	// nullify the blockedByBug parameter since this function was originally not blocked by any bug
			blockedByBzBug = new BlockedByBzBug("1034396");
			l.set(0, blockedByBzBug);
			
			// Object blockedByBug, String username, String password, Sring org, String proxy, String proxyuser, String proxypassword, String proxy_hostnameConfig, String proxy_portConfig, String proxy_userConfig, String proxy_passwordConfig, Integer exitCode, String stdout, String stderr, SSHCommandRunner proxyRunner, String proxyLog, String proxyLogGrepPattern
			
			if (l.get(11).equals(new Integer(0))) {
				/* valid prior to bug 1034396 
				l.set(11,new Integer(1));
				l.set(12,"This system does not have any subscriptions.");
				l.set(13,"");
				*/
				l.set(11,new Integer(0));
				l.set(12,"This system does not have any content overrides applied to it.");
				l.set(13,"");
			}
			ll.add(l);
		}
		return TestNGUtils.convertListOfListsTo2dArray(ll);
	}
	
	
	@DataProvider(name="getSubscribeAttemptsUsingProxyServerData")
	public Object[][] getSubscribeAttemptsUsingProxyServerDataAs2dArray() {
		//return TestNGUtils.convertListOfListsTo2dArray(getValidRegisterAttemptsUsingProxyServerDataAsListOfLists());
		return TestNGUtils.convertListOfListsTo2dArray(getSubscribeAttemptsUsingProxyServerDataAsListOfLists());
	}
	protected List<List<Object>> getSubscribeAttemptsUsingProxyServerDataAsListOfLists() {
		List<List<Object>> ll = new ArrayList<List<Object>>();
		for (List<Object> l : getValidRegisterAttemptsUsingProxyServerDataAsListOfLists()) {
			if (l.get(8)==nErrMsg) l.set(0,new BlockedByBzBug("869046"));
			ll.add(l);
		}
		return ll;
	}
	
	
	@DataProvider(name="getSubscribeAttemptsUsingProxyServerViaRhsmConfigData")
	public Object[][] getSubscribeAttemptsUsingProxyServerViaRhsmConfigDataAs2dArray() {
		//return TestNGUtils.convertListOfListsTo2dArray(getValidRegisterAttemptsUsingProxyServerViaRhsmConfigDataAsListOfLists());
		return TestNGUtils.convertListOfListsTo2dArray(getSubscribeAttemptsUsingProxyServerViaRhsmConfigDataAsListOfLists());
	}
	protected List<List<Object>> getSubscribeAttemptsUsingProxyServerViaRhsmConfigDataAsListOfLists() {
		List<List<Object>> ll = new ArrayList<List<Object>>();
		for (List<Object> l : getValidRegisterAttemptsUsingProxyServerViaRhsmConfigDataAsListOfLists()) {
			if (l.get(12)!=null) {
				if (l.get(12)==nErrMsg) l.set(0,new BlockedByBzBug("869046"));
				ll.add(l);
			}
		}
		return ll;
	}
	
	@DataProvider(name="getUnsubscribeAttemptsUsingProxyServerData")
	public Object[][] getUnsubscribeAttemptsUsingProxyServerDataAs2dArray() {
		return TestNGUtils.convertListOfListsTo2dArray(getValidRegisterAttemptsUsingProxyServerDataAsListOfLists());
	}
	
	
	@DataProvider(name="getUnsubscribeAttemptsUsingProxyServerViaRhsmConfigData")
	public Object[][] getUnsubscribeAttemptsUsingProxyServerViaRhsmConfigDataAs2dArray() {
		return TestNGUtils.convertListOfListsTo2dArray(getValidRegisterAttemptsUsingProxyServerViaRhsmConfigDataAsListOfLists());
	}
	
	
	@DataProvider(name="getRhsmDebugSystemAttemptsUsingProxyServerData")
	public Object[][] getRhsmDebugSystemAttemptsUsingProxyServerDataAs2dArray() {
		return TestNGUtils.convertListOfListsTo2dArray(getValidRegisterAttemptsUsingProxyServerDataAsListOfLists());
	}
	
	
	@DataProvider(name="getRhsmDebugSystemAttemptsUsingProxyServerViaRhsmConfigData")
	public Object[][] getRhsmDebugSystemAttemptsUsingProxyServerViaRhsmConfigDataAs2dArray() {
		return TestNGUtils.convertListOfListsTo2dArray(getValidRegisterAttemptsUsingProxyServerViaRhsmConfigDataAsListOfLists());
	}
	
	

}

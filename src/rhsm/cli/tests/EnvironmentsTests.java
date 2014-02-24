package rhsm.cli.tests;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.testng.SkipException;
import org.testng.annotations.AfterGroups;
import org.testng.annotations.BeforeGroups;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.redhat.qe.Assert;
import com.redhat.qe.auto.testng.TestNGUtils;

import rhsm.base.CandlepinType;
import rhsm.base.SubscriptionManagerCLITestScript;
import rhsm.cli.tasks.CandlepinTasks;
import rhsm.data.Environment;

import com.redhat.qe.tools.SSHCommandResult;

/**
 * @author jsefler
 *
 *
 */
@Test(groups={"EnvironmentsTests"})
public class EnvironmentsTests extends SubscriptionManagerCLITestScript {

	// Test methods ***********************************************************************
	
	
	@Test(	description="subscription-manager: verify that an on-premises candlepin server does NOT support environments",
			groups={},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void VerifyThatCandlepinDoesNotSupportEnvironments_Test() throws JSONException, Exception {
		
		// ask the candlepin server if it supports environment
		boolean supportsEnvironments = CandlepinTasks.isEnvironmentsSupported(sm_clientUsername, sm_clientPassword, sm_serverUrl);
		
		// skip this test when candlepin supports environments
		if (supportsEnvironments) throw new SkipException("Candlepin server '"+sm_serverHostname+"' appears to support environments, therefore this test is not applicable.");

		Assert.assertFalse(supportsEnvironments,"Candlepin server '"+sm_serverHostname+"' does not support environments.");
	}
	
	
	@Test(	description="subscription-manager: run the environments module while prompting for user credentials interactively",
			groups={"blockedbyBug-878986"},
			dataProvider = "getInteractiveCredentialsForNonSupportedEnvironmentsData",
			dependsOnMethods={"VerifyThatCandlepinDoesNotSupportEnvironments_Test"},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void EnvironmentsWithInteractivePromptingForCredentials_Test(Object bugzilla, String promptedUsername, String promptedPassword, String commandLineUsername, String commandLinePassword, Integer expectedExitCode, String expectedStdoutRegex, String expectedStderrRegex) {

		// call environments while providing a valid username at the interactive prompt
		String command;
		if (client.runCommandAndWait("rpm -q expect").getExitCode().intValue()==0) {	// is expect installed?
			// assemble an ssh command using expect to simulate an interactive supply of credentials to the environments command
			String promptedUsernames=""; if (promptedUsername!=null) for (String username : promptedUsername.split("\\n")) {
				promptedUsernames += "expect \\\"*Username:\\\"; send "+username+"\\\r;";
			}
			String promptedPasswords=""; if (promptedPassword!=null) for (String password : promptedPassword.split("\\n")) {
				promptedPasswords += "expect \\\"*Password:\\\"; send "+password+"\\\r;";
			}
			// [root@jsefler-onprem-5server ~]# expect -c "spawn subscription-manager environments --org foo; expect \"*Username:\"; send qa@redhat.com\r; expect \"*Password:\"; send CHANGE-ME\r; expect eof; catch wait reason; exit [lindex \$reason 3]"
			command = String.format("expect -c \"spawn %s environments --org foo %s %s; %s %s expect eof; catch wait reason; exit [lindex \\$reason 3]\"",
					clienttasks.command,
					commandLineUsername==null?"":"--username="+commandLineUsername,
					commandLinePassword==null?"":"--password="+commandLinePassword,
					promptedUsernames,
					promptedPasswords);
		} else {
			// assemble an ssh command using echo and pipe to simulate an interactive supply of credentials to the environments command
			String echoUsername= promptedUsername==null?"":promptedUsername;
			String echoPassword = promptedPassword==null?"":promptedPassword;
			String n = (promptedPassword!=null&&promptedUsername!=null)? "\n":"";
			command = String.format("echo -e \"%s\" | %s environments --org foo %s %s",
					echoUsername+n+echoPassword,
					clienttasks.command,
					commandLineUsername==null?"":"--username="+commandLineUsername,
					commandLinePassword==null?"":"--password="+commandLinePassword);
		}
		
		// attempt environments with the interactive credentials
		SSHCommandResult sshCommandResult = client.runCommandAndWait(command);
		
		// assert the sshCommandResult here
		if (expectedExitCode!=null) Assert.assertEquals(sshCommandResult.getExitCode(), expectedExitCode, "The expected exit code from the environments attempt.");
		if (expectedStdoutRegex!=null) Assert.assertContainsMatch(sshCommandResult.getStdout(), expectedStdoutRegex, "The expected stdout result from environments while supplying interactive credentials.");
		if (expectedStderrRegex!=null) Assert.assertContainsMatch(sshCommandResult.getStderr(), expectedStderrRegex, "The expected stderr result from environments while supplying interactive credentials.");
	}
	
	
	@Test(	description="subscription-manager: attempt environments without --org option",
			groups={"blockedByBug-849105"},
			enabled=false)	// 2/5/2013 this test is obsoleted by implementation of Bug 727092 - [RFE]: Enhance subscription-manager to prompt the user for an Org Name.
	//@ImplementsNitrateTest(caseId=)
	public void AttemptEnvironmentsWithoutOrg_Test() {
		
		SSHCommandResult environmentsResult = clienttasks.environments_(sm_clientUsername,sm_clientPassword,null,null,null,null, null, null);
		Assert.assertEquals(environmentsResult.getStderr().trim(), "", "Stderr from environments without specifying the --org option.");
		//Assert.assertEquals(environmentsResult.getStdout().trim(), "you must specify an --org", "Stdout from environments without specifying the --org option.");
		Assert.assertEquals(environmentsResult.getStdout().trim(), "Error: This command requires that you specify an organization with --org", "Stdout from environments without specifying the --org option.");
		Assert.assertEquals(environmentsResult.getExitCode(), Integer.valueOf(255),"Exit code from environments when executed without an org option.");

		environmentsResult = clienttasks.environments_(null,null,null,null,null,null, null, null);
		Assert.assertEquals(environmentsResult.getStderr().trim(), "", "Stderr from environments without specifying the --org option.");
		//Assert.assertEquals(environmentsResult.getStdout().trim(), "you must specify an --org", "Stdout from environments without specifying the --org option.");
		Assert.assertEquals(environmentsResult.getStdout().trim(), "Error: This command requires that you specify an organization with --org", "Stdout from environments without specifying the --org option.");
		Assert.assertEquals(environmentsResult.getExitCode(), Integer.valueOf(255),"Exit code from environments when executed without an org option.");
	}
	
	
	
	protected String server_hostname = null;
	protected String server_port = null;
	protected String server_prefix = null;
	protected String clientOrg = null;
	@BeforeGroups(value={"EnvironmentsWithServerurl_Test"}, groups={"setup"})
	public void beforeEnvironmentsWithServerurl_Test() {
		if (clienttasks==null) return;
		server_hostname	= clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "server", "hostname");
		server_port		= clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "server", "port");
		server_prefix	= clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "server", "prefix");
		clientOrg 		= clienttasks.getOrgs(sm_clientUsername,sm_clientPassword).get(0).orgKey;	// use the first org
	}
	@Test(	description="subscription-manager: service-level --list with --serverurl",
			dataProvider="getServerurl_TestData",
			groups={"EnvironmentsWithServerurl_Test"},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void EnvironmentsWithServerurl_Test(Object bugzilla, String serverurl, String expectedHostname, String expectedPort, String expectedPrefix, Integer expectedExitCode, String expectedStdoutRegex, String expectedStderrMatch) {
		// get original server at the beginning of this test
		String hostnameBeforeTest	= clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "hostname");
		String portBeforeTest		= clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "port");
		String prefixBeforeTest		= clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "prefix");
		
		// environments with a serverurl
		SSHCommandResult sshCommandResult = clienttasks.environments_(sm_clientUsername,sm_clientPassword,clientOrg,serverurl, null, null, null, null);
		
		// assert the sshCommandResult here
		if (expectedExitCode!=null)	Assert.assertEquals(sshCommandResult.getExitCode(), expectedExitCode,"ExitCode after register with --serverurl="+serverurl+" and other options:");
		if (expectedStdoutRegex!=null)	Assert.assertContainsMatch(sshCommandResult.getStdout().trim(), expectedStdoutRegex,"Stdout after register with --serverurl="+serverurl+" and other options:");
		if (expectedStderrMatch!=null)	Assert.assertContainsMatch(sshCommandResult.getStderr().trim(), expectedStderrMatch,"Stderr after register with --serverurl="+serverurl+" and other options:");
		Assert.assertContainsNoMatch(sshCommandResult.getStderr().trim(), "Traceback.*","Stderr after register with --serverurl="+serverurl+" and other options should not contain a Traceback.");
		
		// negative testcase assertions........
		if (expectedExitCode.equals(new Integer(255))) {
			// assert that the current config remains unchanged when the expectedExitCode is 255
			Assert.assertEquals(clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "hostname"), hostnameBeforeTest, "The "+clienttasks.rhsmConfFile+" configuration for [server] hostname should remain unchanged when attempting to register with an invalid serverurl.");
			Assert.assertEquals(clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "port"),	portBeforeTest, "The "+clienttasks.rhsmConfFile+" configuration for [server] port should remain unchanged when attempting to register with an invalid serverurl.");
			Assert.assertEquals(clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "prefix"), prefixBeforeTest, "The "+clienttasks.rhsmConfFile+" configuration for [server] prefix should remain unchanged when attempting to register with an invalid serverurl.");
						
			return;	// nothing more to do after these negative testcase assertions
		}
		
		// positive testcase assertions........
		Assert.assertEquals(clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "hostname"), expectedHostname, "The "+clienttasks.rhsmConfFile+" configuration for [server] hostname has been updated from the specified --serverurl "+serverurl);
		Assert.assertEquals(clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "port"), expectedPort, "The "+clienttasks.rhsmConfFile+" configuration for [server] port has been updated from the specified --serverurl "+serverurl);
		Assert.assertEquals(clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "prefix"), expectedPrefix, "The "+clienttasks.rhsmConfFile+" configuration for [server] prefix has been updated from the specified --serverurl "+serverurl);
	}
	@AfterGroups(value={"EnvironmentsWithServerurl_Test"},groups={"setup"})
	public void afterRegisterWithServerurl_Test() {
		if (server_hostname!=null)	clienttasks.config(null,null,true,new String[]{"server","hostname",server_hostname});
		if (server_port!=null)		clienttasks.config(null,null,true,new String[]{"server","port",server_port});
		if (server_prefix!=null)	clienttasks.config(null,null,true,new String[]{"server","prefix",server_prefix});
	}
	
	
	
	protected String rhsm_ca_cert_dir = null;
	@BeforeGroups(value={"EnvironmentsWithInsecure_Test"}, groups={"setup"})
	public void beforeEnvironmentsWithInsecure_Test() {
		if (clienttasks==null) return;
		rhsm_ca_cert_dir	= clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "rhsm", "ca_cert_dir");
	}
	@Test(	description="subscription-manager: environments with --insecure",
			groups={"EnvironmentsWithInsecure_Test","blockedByBug-844411","blockedByBug-993202"},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void EnvironmentsWithInsecure_Test() {
		SSHCommandResult sshCommandResult;
		
		// calling environments without insecure should pass
		sshCommandResult = clienttasks.environments(sm_clientUsername,sm_clientPassword,sm_clientOrg, null, false, null, null, null);
		
		// change the rhsm.ca_cert_dir configuration to simulate a missing candlepin ca cert
		client.runCommandAndWait("mkdir -p /tmp/emptyDir");
		sshCommandResult = clienttasks.config(null, null, true, new String[]{"rhsm","ca_cert_dir","/tmp/emptyDir"});
		
		// calling environments without insecure should now fail (throwing stderr "certificate verify failed")
		sshCommandResult = clienttasks.environments_(sm_clientUsername,sm_clientPassword,sm_clientOrg, null, false, null, null, null);
		/* changed by subscription-manager commit 3366b1c734fd27faf48313adf60cf051836af115
		Assert.assertEquals(sshCommandResult.getStderr().trim(), "certificate verify failed", "Stderr from the environments command when configuration rhsm.ca_cert_dir has been falsified.");
		Assert.assertEquals(sshCommandResult.getStdout().trim(), "", "Stdout from the environments command when configuration rhsm.ca_cert_dir has been falsified.");
		*/
		Assert.assertEquals(sshCommandResult.getStderr().trim(), "", "Stderr from the environments command when configuration rhsm.ca_cert_dir has been falsified.");
		Assert.assertEquals(sshCommandResult.getStdout().trim(), "Unable to verify server's identity: certificate verify failed", "Stdout from the environments command when configuration rhsm.ca_cert_dir has been falsified.");
		Assert.assertEquals(sshCommandResult.getExitCode(), Integer.valueOf(255), "Exitcode from the environments command when configuration rhsm.ca_cert_dir has been falsified.");
	
		// calling environments with insecure should now pass
		sshCommandResult = clienttasks.environments(sm_clientUsername,sm_clientPassword,sm_clientOrg, null, true, null, null, null);
		
		// assert that option --insecure did NOT persist to rhsm.conf
		Assert.assertEquals(clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "server", "insecure"), "0", "Expected value of "+clienttasks.rhsmConfFile+" server.insecure configuration.  Use of the --insecure option when calling the environments module should NOT be persisted to rhsm.conf as true.");
	}
	@AfterGroups(value={"EnvironmentsWithInsecure_Test"},groups={"setup"})
	public void afterEnvironmentsWithInsecure_Test() {
		if (rhsm_ca_cert_dir!=null) clienttasks.config(null, null, true, new String[]{"rhsm","ca_cert_dir",rhsm_ca_cert_dir});
	}
	
	@Test(	description="subscription-manager: run the environments module with valid user credentials and verify the expected environments are listed",
			groups={"blockedByBug-1063491"},
			dataProvider="getEnvironmentsForOrgsData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void EnvironmentsWithCredentials_Test(String username, String password, String org, List<Environment> expectedEnvironments) {
		log.info("Testing subscription-manager environments module using username="+username+" password="+password+" org="+org+" and expecting environmnets="+expectedEnvironments+" ...");
		
		// use subscription-manager to get the organizations for which the user has access
		SSHCommandResult environmentsResult = clienttasks.environments_(username, password, org, null, null, null, null, null);
		
		// parse the actual Environments from the environmentsResult
		List<Environment> actualEnvironments = Environment.parse(environmentsResult.getStdout());
		
		// assert that all of the expectedEnvironments are included in the actualEnvironments
		for (Environment expectedEnvironment : expectedEnvironments) {
			//if (expectedEnvironment.envDescription==null)  expectedEnvironment.envDescription="None";	// temporarily ignoring bug 1063491
			Assert.assertTrue(actualEnvironments.contains(expectedEnvironment), "The list of environments returned by subscription-manager for user '"+username+"' under org '"+org+"' includes expected environment: "+expectedEnvironment);
		}
		Assert.assertEquals(actualEnvironments.size(), expectedEnvironments.size(),"The number of environments returned by subscription-manager for user '"+username+"' under org '"+org+"'.");
	}
	
	@BeforeGroups(value={"Libraryas_env"}, groups={"setup"})
	@Test(description="Library as an Env",enabled=true)
	public void Libraryas_env(){
		SSHCommandResult sshCommandResult;
		//register client to katello server 
		sshCommandResult=clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg);
		
		sshCommandResult=clienttasks.identity(null, null, null, null, null, null, null);
		List<String> outputlist = new ArrayList<String>();
		for (String consoleoutput : sshCommandResult.getStdout().split("\\+-+\\+")[sshCommandResult.getStdout().split("\\+-+\\+").length-1].trim().split("\\n")) {
			outputlist.add(consoleoutput);
		}
		Assert.assertContains(outputlist, "environment name: Library");
		sshCommandResult=clienttasks.unregister(null,null, null);

		
//		Assert.assertEquals(outputlist.get(4).trim(), "environment name: Library", "Library reconize as Environmenet");
//		
		
	}
	
	protected String envname=null;
	@BeforeGroups(value={"Register with All Environment"}, groups={"setup"})
	@Test(description="Register with env other than Library",enabled=true)
	
<<<<<<< HEAD
	@BeforeGroups(value={"Libraryas_env"}, groups={"setup"})
	@Test(description="Library as an Env",enabled=true)
	public void Libraryas_env(){
		SSHCommandResult sshCommandResult;
		// added by jsefler 
		if (!CandlepinType.katello.equals(sm_serverType)) throw new SkipException("This test is only applicable against a server that supports environments.");

		//register client to katello server 
		sshCommandResult=clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg);
		
		sshCommandResult=clienttasks.identity(null, null, null, null, null, null, null);
		List<String> outputlist = new ArrayList<String>();
		for (String consoleoutput : sshCommandResult.getStdout().split("\\+-+\\+")[sshCommandResult.getStdout().split("\\+-+\\+").length-1].trim().split("\\n")) {
			outputlist.add(consoleoutput);
		}
		Assert.assertContains(outputlist, "environment name: Library");
		sshCommandResult=clienttasks.unregister(null,null, null);

		
//		Assert.assertEquals(outputlist.get(4).trim(), "environment name: Library", "Library reconize as Environmenet");
//		
		
	}
=======
	public void All_Env_Register(){
		SSHCommandResult sshCommandResult;

		clientOrg=clienttasks.getOrgs(sm_clientUsername, sm_clientPassword).get(0).orgKey;
		sshCommandResult = clienttasks.environments(sm_clientUsername, sm_clientPassword, clientOrg, null, null, null, null, null);

		for (String consoleoutput : sshCommandResult.getStdout().split("\\+-+\\+")[sshCommandResult.getStdout().split("\\+-+\\+").length-1].trim().split("\\n")) 
		{
			String[] cout = consoleoutput.split(":");
			if(cout[0].trim().equals("Name"))
						{
						envname = cout[1].trim().replaceAll("\\s", "");

						sshCommandResult=clienttasks.register_(sm_clientUsername, sm_clientPassword, clientOrg, envname, null, null, null, null, null, null, (String)null, null, null, null, null, null, null, null, null);
						Assert.assertEquals(sshCommandResult.getExitCode(),Integer.valueOf(0),"Client sucessfully registred with "+envname+"environment");
						sshCommandResult=clienttasks.unregister(null, null, null);
						}
		}
			
//		System.out.println(envlist{"Name"});
//		for (String name : envlist){
//				if (name.contains("Description")){
					
//				}
//				else {
//					List<Map<String,String>> envname = new ArrayList<Map<String,String>>();
//					System.out.println(envname);
//					
//				}
//			
//		}
			
}
>>>>>>> 476d0334b5cc169445e3dd2d71c468a2e7cc8386
	
	protected String envname=null;
	@BeforeGroups(value={"Register with All Environment"}, groups={"setup"})
	@Test(description="Register with env other than Library",enabled=true)
	
	public void All_Env_Register(){
		SSHCommandResult sshCommandResult;
		// added by jsefler 
		if (!CandlepinType.katello.equals(sm_serverType)) throw new SkipException("This test is only applicable against a server that supports environments.");


		clientOrg=clienttasks.getOrgs(sm_clientUsername, sm_clientPassword).get(0).orgKey;
		sshCommandResult = clienttasks.environments(sm_clientUsername, sm_clientPassword, clientOrg, null, null, null, null, null);

		for (String consoleoutput : sshCommandResult.getStdout().split("\\+-+\\+")[sshCommandResult.getStdout().split("\\+-+\\+").length-1].trim().split("\\n")) 
		{
			String[] cout = consoleoutput.split(":");
			if(cout[0].trim().equals("Name"))
						{
						envname = cout[1].trim().replaceAll("\\s", "");

						sshCommandResult=clienttasks.register(sm_clientUsername, sm_clientPassword, clientOrg, envname, null, null, null, null, null, null, (String)null, null, null, null, null, null, null, null, null);
						//Assert.assertEquals(sshCommandResult.getExitCode(),Integer.valueOf(0),"Client sucessfully registred with "+envname+"environment"); as per jsefler suggestion
						sshCommandResult=clienttasks.unregister(null, null, null);
						}}
		}
	
	
	// Candidates for an automated Test:
	// TODO Bug 734474 - better error message for missing enviroment on katello https://github.com/RedHatQE/rhsm-qe/issues/130
	// TODO Bug 738322 - Katello returning inaccurate message via subscription-manager when user has incorrect credentials https://github.com/RedHatQE/rhsm-qe/issues/131
	// TODO Bug 872351 - subscription-manager identity and facts gui dialog should display the registered consumer's environment value
	// TODO Bug 901479 - [RFE] Not able to see environment to which is system registered to
	// TODO Bug 795541 - Subscription Manager should filter out library instead of locker
	// TODO Bug 963579 - `subscription-manager environments` does not recognize Library as an env.
	// Configuration methods ***********************************************************************
	
	
	
	// Protected methods ***********************************************************************


	
	// Data Providers ***********************************************************************

	
	@DataProvider(name="getEnvironmentsForOrgsData")
	public Object[][] getEnvironmentsForOrgsDataDataAs2dArray() throws JSONException, Exception {
		return TestNGUtils.convertListOfListsTo2dArray(getEnvironmentsForOrgsDataDataAsListOfLists());
	}
	protected List<List<Object>> getEnvironmentsForOrgsDataDataAsListOfLists() throws JSONException, Exception {
		List<List<Object>> ll = new ArrayList<List<Object>>(); if (!isSetupBeforeSuiteComplete) return ll;
		
		// if the candlepin server does not support environments, then there is no data to return
		if (!CandlepinTasks.isEnvironmentsSupported(sm_serverAdminUsername,sm_serverAdminPassword, sm_serverUrl)) return ll;
		
		// get all of the candlepin users
		JSONArray jsonUsers = new JSONArray(CandlepinTasks.getResourceUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword,sm_serverUrl,"/users"));	
		for (int i = 0; i < jsonUsers.length(); i++) {
			JSONObject jsonUser = (JSONObject) jsonUsers.get(i);
			//[root@jsefler-7 ~]# curl --stderr /dev/null --insecure --user admin:admin --request GET https://katellosach.usersys.redhat.com/katello/api/users | python -m simplejson/tool
			//	[
			//	    {
			//	        "created_at": "2014-01-21T21:13:16Z",
			//	        "default_environment": null,
			//	        "default_environment_id": null,
			//	        "default_organization": null,
			//	        "disabled": false,
			//	        "email": "root@localhost",
			//	        "foreman_id": null,
			//	        "helptips_enabled": true,
			//	        "hidden": false,
			//	        "id": 1,
			//	        "page_size": 25,
			//	        "password": "2d7bf191eb6b50f4b4018b019aa347aee4e31b71182d35e8fed1596fc08a33ea1828dc162d5bd2a31321574ce9a5e75e5a1192e6436cb9ca42243de844d832d89mbMTimoY7NCsNDPTeReEPO7Cga5DCGKWNUgxffn8sAr5vfHA03z45YvHS4KRG94",
			//	        "password_reset_sent_at": null,
			//	        "password_reset_token": null,
			//	        "preferences": {},
			//	        "remote_id": "admin",
			//	        "updated_at": "2014-01-21T21:13:16Z",
			//	        "username": "admin"
			//	    }
			//	]
			
			//Boolean isSuperAdmin = jsonUser.getBoolean("superAdmin");
			String username = jsonUser.getString("username");
			String password = sm_clientPasswordDefault;
			if (username.equals(sm_serverAdminUsername)) password = sm_serverAdminPassword;
			
			// get the user's owners
			JSONArray jsonUserOwners = new JSONArray(CandlepinTasks.getResourceUsingRESTfulAPI(username,password,sm_serverUrl,"/users/"+username+"/owners"));	
			for (int j = 0; j < jsonUserOwners.length(); j++) {
				JSONObject jsonOwner = (JSONObject) jsonUserOwners.get(j);
				//[root@jsefler-7 ~]# curl --stderr /dev/null --insecure --user admin:admin --request GET https://katellosach.usersys.redhat.com/katello/api/users/admin/owners | python -m simplejson/tool
				//	[
				//	    {
				//	        "displayName": "redhat",
				//	        "key": "redhat"
				//	    },
				//	    {
				//	        "displayName": "ACME_Corporation",
				//	        "key": "ACME_Corporation"
				//	    }
				//	]

				String org = jsonOwner.getString("key");
				String orgName = jsonOwner.getString("displayName");
				
				// get the user's owner's environments
				List<Environment> environments = new ArrayList<Environment>();
				JSONArray jsonUserOwnersEnvironments = new JSONArray(CandlepinTasks.getResourceUsingRESTfulAPI(username,password,sm_serverUrl,"/owners/"+org+"/environments"));	
				for (int k = 0; k < jsonUserOwnersEnvironments.length(); k++) {
					JSONObject jsonOwnerEnvironment = (JSONObject) jsonUserOwnersEnvironments.get(k);
					//[root@jsefler-7 ~]# curl --stderr /dev/null --insecure --user admin:admin --request GET https://katellosach.usersys.redhat.com/katello/api/owners/redhat/environments | python -m simplejson/tool
					//	[
					//	    {
					//	        "description": null,
					//	        "display_name": "Library",
					//	        "id": "2",
					//	        "name": "Library"
					//	    },
					//	    {
					//	        "description": "",
					//	        "display_name": "dev",
					//	        "id": "3-3",
					//	        "name": "dev/pubcv1"
					//	    },
					//	    {
					//	        "description": "",
					//	        "display_name": "Library",
					//	        "id": "2-3",
					//	        "name": "Library/pubcv1"
					//	    }
					//	]
					
					String envName = jsonOwnerEnvironment.getString("name");
					String envDescription = jsonOwnerEnvironment.isNull("description")? null:jsonOwnerEnvironment.getString("description");
					
					environments.add(new Environment(envName, envDescription));
				}
				
				// String username, String password, String org, List<Environment> environments
				ll.add(Arrays.asList(new Object[]{username,password,org,environments}));
			}
		}
		return ll;
	}
	
	
	@DataProvider(name="getInvalidCredentialsForOrgsData")
	public Object[][] getInvalidCredentialsForOrgsDataAs2dArray() {
		return TestNGUtils.convertListOfListsTo2dArray(getInvalidCredentialsForOrgsDataAsListOfLists());
	}
	protected List<List<Object>> getInvalidCredentialsForOrgsDataAsListOfLists() {
		List<List<Object>> ll = new ArrayList<List<Object>>(); if (!isSetupBeforeSuiteComplete) return ll;
		String x = String.valueOf(getRandInt());
		
		// String username, String password
		ll.add(Arrays.asList(new Object[]{	sm_clientUsername+x,	sm_clientPassword}));
		ll.add(Arrays.asList(new Object[]{	sm_clientUsername,		sm_clientPassword+x}));
		ll.add(Arrays.asList(new Object[]{	sm_clientUsername+x,	sm_clientPassword+x}));
		
		return ll;
	}
	
	
	// NOTE: Assumes
	@DataProvider(name="getInteractiveCredentialsForNonSupportedEnvironmentsData")
	public Object[][] getInteractiveCredentialsForNonSupportedEnvironmentsDataAs2dArray() {
		return TestNGUtils.convertListOfListsTo2dArray(getInteractiveCredentialsForNonSupportedEnvironmentsDataAsListOfLists());
	}
	protected List<List<Object>> getInteractiveCredentialsForNonSupportedEnvironmentsDataAsListOfLists() {
		List<List<Object>> ll = new ArrayList<List<Object>>(); if (!isSetupBeforeSuiteComplete) return ll;
		if (servertasks==null) return ll;
		if (clienttasks==null) return ll;
		
		//String stdoutMsg = "ERROR: Server does not support environments.";
		String stdoutMsg = "Error: Server does not support environments.";
		String uErrMsg = servertasks.invalidCredentialsRegexMsg();
		String x = String.valueOf(getRandInt());
		if (client.runCommandAndWait("rpm -q expect").getExitCode().intValue()==0) {	// is expect installed?
			// Object bugzilla, String promptedUsername, String promptedPassword, String commandLineUsername, String commandLinePassword, Integer expectedExitCode, String expectedStdoutRegex, String expectedStderrRegex
			ll.add(Arrays.asList(new Object[] {	null,	sm_clientUsername,			null,						null,				sm_clientPassword,	new Integer(0),		stdoutMsg,			null}));
			ll.add(Arrays.asList(new Object[] {	null,	sm_clientUsername+x,		null,						null,				sm_clientPassword,	new Integer(0),		stdoutMsg,			null}));	//ll.add(Arrays.asList(new Object[] {	null,	sm_clientUsername+x,		null,						null,				sm_clientPassword,	new Integer(255),	uErrMsg,			null}));
			ll.add(Arrays.asList(new Object[] {	null,	null,						sm_clientPassword,			sm_clientUsername,	null,				new Integer(0),		stdoutMsg,			null}));
			ll.add(Arrays.asList(new Object[] {	null,	null,						sm_clientPassword+x,		sm_clientUsername,	null,				new Integer(0),		stdoutMsg,			null}));	//ll.add(Arrays.asList(new Object[] {	null,	null,						sm_clientPassword+x,		sm_clientUsername,	null,				new Integer(255),	uErrMsg,			null}));
			ll.add(Arrays.asList(new Object[] {	null,	sm_clientUsername,			sm_clientPassword,			null,				null,				new Integer(0),		stdoutMsg,			null}));
			ll.add(Arrays.asList(new Object[] {	null,	sm_clientUsername+x,		sm_clientPassword+x,		null,				null,				new Integer(0),		stdoutMsg,			null}));	//ll.add(Arrays.asList(new Object[] {	null,	sm_clientUsername+x,		sm_clientPassword+x,		null,				null,				new Integer(255),	uErrMsg,			null}));
			ll.add(Arrays.asList(new Object[] {	null,	"\n\n"+sm_clientUsername,	"\n\n"+sm_clientPassword,	null,				null,				new Integer(0),		"(\nUsername: ){3}"+sm_clientUsername+"(\nPassword: ){3}"+"\n"+stdoutMsg,	null}));
		} else {
			// Object bugzilla, String promptedUsername, String promptedPassword, String commandLineUsername, String commandLinePassword, Integer expectedExitCode, String expectedStdoutRegex, String expectedStderrRegex
			ll.add(Arrays.asList(new Object[] {	null,	sm_clientUsername,			null,						null,				sm_clientPassword,	new Integer(0),		stdoutMsg,			null}));	
			ll.add(Arrays.asList(new Object[] {	null,	sm_clientUsername+x,		null,						null,				sm_clientPassword,	new Integer(0),		stdoutMsg,			null}));	//ll.add(Arrays.asList(new Object[] {	null,	sm_clientUsername+x,		null,						null,				sm_clientPassword,	new Integer(255),	null,				uErrMsg}));
			ll.add(Arrays.asList(new Object[] {	null,	null,						sm_clientPassword,			sm_clientUsername,	null,				new Integer(0),		stdoutMsg,			null}));
			ll.add(Arrays.asList(new Object[] {	null,	null,						sm_clientPassword+x,		sm_clientUsername,	null,				new Integer(0),		stdoutMsg,			null}));	//ll.add(Arrays.asList(new Object[] {	null,	null,						sm_clientPassword+x,		sm_clientUsername,	null,				new Integer(255),	null,				uErrMsg}));
			ll.add(Arrays.asList(new Object[] {	null,	sm_clientUsername,			sm_clientPassword,			null,				null,				new Integer(0),		stdoutMsg,			null}));
			ll.add(Arrays.asList(new Object[] {	null,	sm_clientUsername+x,		sm_clientPassword+x,		null,				null,				new Integer(0),		stdoutMsg,			null}));	//ll.add(Arrays.asList(new Object[] {	null,	sm_clientUsername+x,		sm_clientPassword+x,		null,				null,				new Integer(255),	null,				uErrMsg}));
			ll.add(Arrays.asList(new Object[] {	null,	"\n\n"+sm_clientUsername,	"\n\n"+sm_clientPassword,	null,				null,				new Integer(0),		"(Username: ){3}"+stdoutMsg,	"(Warning: Password input may be echoed.\nPassword: \n){3}"}));
		
		}
		return ll;
	}

}

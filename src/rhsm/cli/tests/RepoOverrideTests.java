package rhsm.cli.tests;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.testng.SkipException;
import org.testng.annotations.AfterGroups;
import org.testng.annotations.Test;

import rhsm.base.SubscriptionManagerCLITestScript;
import rhsm.cli.tasks.SubscriptionManagerTasks;
import rhsm.data.ProductSubscription;
import rhsm.data.SubscriptionPool;
import rhsm.data.YumRepo;

import com.redhat.qe.Assert;
import com.redhat.qe.tools.RemoteFileTasks;
import com.redhat.qe.tools.SSHCommandResult;

/**
 *  @author jsefler
 *
 */
@Test(groups={"RepoOverrideTests"})
public class RepoOverrideTests extends SubscriptionManagerCLITestScript{
	
	
	// Test Methods ***********************************************************************
	
	
	@Test(	description="when subscription-manager repos-override is run with no args, it should default to --list option",
			groups={"blockedByBug-1034396"},
			enabled=true)
			//@ImplementsNitrateTest(caseId=)
	public void ListRepoOverridesIsTheDefault_Test() {
		
		// register
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, true, false, null, null, null);
		
		// without any subscriptions attached...
		SSHCommandResult defaultResult = clienttasks.repo_override_(null,null,(String)null,(String)null,null,null,null,null);
		SSHCommandResult listResult = clienttasks.repo_override_(true,null,(String)null,(String)null,null,null,null,null);
		Assert.assertEquals(listResult.toString().trim(), defaultResult.toString().trim(), "The result from running module repo-override without any options should default to the --list result (with no subscriptions attached and no overrides)");
		// valid prior to bug 1034396	Assert.assertEquals(listResult.getStdout().trim(), "This system does not have any subscriptions.", "Stdout from repo-override --list without any subscriptions attached and no overrides.");
		Assert.assertEquals(listResult.getStdout().trim(), "This system does not have any content overrides applied to it.", "Stdout from repo-override --list without any subscriptions attached and no overrides.");
		Assert.assertEquals(listResult.getStderr().trim(), "", "Stderr from repo-override --list without any subscriptions attached and no overrides.");
		Assert.assertEquals(listResult.getExitCode(), Integer.valueOf(/*1*/0), "ExitCode from repo-override --list without any subscriptions attached and no overrides.");
		
		// subscribe to a random pool (so as to consume an entitlement)
		List<SubscriptionPool> pools = clienttasks.getCurrentlyAvailableSubscriptionPools();
		if (pools.isEmpty()) throw new SkipException("Cannot randomly pick a pool for subscribing when there are no available pools for testing."); 
		SubscriptionPool pool = pools.get(randomGenerator.nextInt(pools.size())); // randomly pick a pool
		clienttasks.subscribeToSubscriptionPool(pool);
		
		// with a subscription attached...
		defaultResult = clienttasks.repo_override_(null,null,(String)null,(String)null,null,null,null,null);
		listResult = clienttasks.repo_override_(true,null,(String)null,(String)null,null,null,null,null);
		Assert.assertEquals(listResult.toString().trim(), defaultResult.toString().trim(), "The result from running module repo-override without any options should default to the --list result (with subscriptions attached)");
		Assert.assertEquals(listResult.getStdout().trim(), "This system does not have any content overrides applied to it.", "Stdout from repo-override --list without any overrides.");
		Assert.assertEquals(listResult.getStderr().trim(), "", "Stderr from repo-override --list without any overrides.");
		Assert.assertEquals(listResult.getExitCode(), Integer.valueOf(0), "ExitCode from repo-override --list without any overrides.");
	}
	
	@Test(	description="attempt to override a baseurl using subscription-manager repos-override",
			groups={"blockedByBug-1030604"},
			enabled=true)
			//@ImplementsNitrateTest(caseId=)
	public void AttemptToOverrideBaseurl_Test() {
		
		// register
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, true, false, null, null, null);
		
		// subscribe to a random pool (so as to consume an entitlement) and remember the original list of YumRepos read from the redhat.repo file
		List<YumRepo> yumRepos = attachRandomSubscriptionThatProvidesYumRepos();
		
		// attempt to override the baseurl
		YumRepo yumRepo = yumRepos.get(randomGenerator.nextInt(yumRepos.size())); // randomly pick a YumRepo
		Map<String,String> repoOverrideNameValueMap = new HashMap<String,String>();
		repoOverrideNameValueMap.put("baseurl", "https://cdn.redhat.com/repo-override-testing/$releasever/$basearch");
		repoOverrideNameValueMap.put("test", "value");
		SSHCommandResult result = clienttasks.repo_override_(null, null, Arrays.asList(yumRepo.id, "foo-bar"), null, repoOverrideNameValueMap, null, null, null);
		Assert.assertEquals(result.getExitCode(), Integer.valueOf(1), "ExitCode from an attempt to repo-override the baseurl of yumRepo: "+yumRepo);		
		Assert.assertEquals(result.getStderr().trim(), "", "Stderr from an attempt to repo-override the baseurl of yumRepo: "+yumRepo);
		Assert.assertEquals(result.getStdout().trim(), "Not allowed to override values for: baseurl", "Stdout from an attempt to repo-override the baseurl of yumRepo: "+yumRepo);
	}
	
	@Test(	description="attempt to override a bASeUrL (note the case) using subscription-manager repos-override",
			groups={"blockedByBug-1030604","blockedByBug-1034375"},
			enabled=true)
			//@ImplementsNitrateTest(caseId=)
	public void AttemptToOverrideBaseurlInMixedCases_Test() {
		
		// register
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, true, false, null, null, null);
		
		// subscribe to a random pool (so as to consume an entitlement) and remember the original list of YumRepos read from the redhat.repo file
		List<YumRepo> yumRepos = attachRandomSubscriptionThatProvidesYumRepos();
		
		// attempt to override the baseUrl (note the uppercase character)
		YumRepo yumRepo = yumRepos.get(randomGenerator.nextInt(yumRepos.size())); // randomly pick a YumRepo
		Map<String,String> repoOverrideNameValueMap = new HashMap<String,String>();
		// attempt to override bASeUrL
		String baseUrl = "bASeUrL";	// contains mixed case characters
		repoOverrideNameValueMap.put(baseUrl, "https://cdn.redhat.com/repo-override-testing/$releasever/$basearch");
		SSHCommandResult result = clienttasks.repo_override_(null, null, Arrays.asList(yumRepo.id, "foo-bar"), null, repoOverrideNameValueMap, null, null, null);
		Assert.assertEquals(result.getExitCode(), Integer.valueOf(1), "ExitCode from an attempt to repo-override the "+baseUrl+" (note the case) of yumRepo '"+yumRepo.id+"'.");		
		Assert.assertEquals(result.getStderr().trim(), "", "Stderr from an attempt to repo-override the '"+baseUrl+"' (note the case) of yumRepo '"+yumRepo.id+"'.");
		Assert.assertEquals(result.getStdout().trim(), "Not allowed to override values for: "+baseUrl, "Stdout from an attempt to repo-override the '"+baseUrl+"' (note the case) of yumRepo '"+yumRepo.id+"'.");
		// attempt to override two bASeUrL and baseurl
		repoOverrideNameValueMap.put("baseurl", "https://cdn.redhat.com/repo-override-testing/$releasever/$basearch");
		repoOverrideNameValueMap.put("mirrorlist_expire", "10");	// include another valid parameter for the fun of it
		result = clienttasks.repo_override_(null, null, Arrays.asList(yumRepo.id, "foo-bar"), null, repoOverrideNameValueMap, null, null, null);
		Assert.assertEquals(result.getExitCode(), Integer.valueOf(1), "ExitCode from an attempt to repo-override "+baseUrl+" (note the case) and 'baseurl' of yumRepo '"+yumRepo.id+"'.");		
		Assert.assertEquals(result.getStderr().trim(), "", "Stderr from an attempt to repo-override the '"+baseUrl+"' (note the case) and 'baseurl' of yumRepo '"+yumRepo.id+"'.");
		Assert.assertEquals(result.getStdout().trim(), "Not allowed to override values for: "+baseUrl+", baseurl", "Stdout from an attempt to repo-override the '"+baseUrl+"' (note the case) and 'baseurl' of yumRepo '"+yumRepo.id+"'.");
		
		// verify that no repo overrides have been added (including the valid parameter for the fun of it)
		result = clienttasks.repo_override(true,null,(String)null,(String)null,null,null,null,null);
		Assert.assertEquals(result.getStdout().trim(), "This system does not have any content overrides applied to it.", "Stdout from repo-override --list after attempts to add baseurl overrides.");
		Assert.assertEquals(result.getStderr().trim(), "", "Stderr from repo-override --list after attempts to add baseurl overrides.");
		Assert.assertEquals(result.getExitCode(), Integer.valueOf(/*1*/0), "ExitCode from repo-override --list after attempts to add baseurl overrides.");
	}
	
	@Test(	description="attempt to add an override for a name baseurl and label using subscription-manager repos-override",
			groups={"blockedByBug-1030604","blockedByBug-1034396"},
			enabled=true)
			//@ImplementsNitrateTest(caseId=)
	public void AttemptToAddOverrideForBaseurlNameAndLabel_Test() {
		
		// register
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, true, false, null, null, null);
		
		// attempt to add overrides for name, label, and baseurl to multiple repoids
		List<String> repoids = Arrays.asList(new String[]{"repo1","repo2","repo3"});
		SSHCommandResult result;
		Map<String,String> repoOverrideNameValueMap = new HashMap<String,String>();
		repoOverrideNameValueMap.put("enabled", "0");	// it is okay to add an override for "enabled"
		
		repoOverrideNameValueMap.put("name", "Repo Name");
		result = clienttasks.repo_override_(null, null, repoids, null, repoOverrideNameValueMap, null, null, null);
		Assert.assertEquals(result.getExitCode(), Integer.valueOf(1), "ExitCode from an attempt to add repo-overrides for baseurl, name, and label for repoids: "+repoids);
		Assert.assertEquals(result.getStderr().trim(), "", "Stderr from an attempt add a repo-overrides for baseurl, name, and label for repoids: "+repoids);
		Assert.assertEquals(result.getStdout().trim(), "Not allowed to override values for: name", "Stdout from an attempt add a repo-overrides for baseurl, name, and label for repoids: "+repoids);
		
		repoOverrideNameValueMap.put("label", "repo-label");
		result = clienttasks.repo_override_(null, null, repoids, null, repoOverrideNameValueMap, null, null, null);
		Assert.assertEquals(result.getExitCode(), Integer.valueOf(1), "ExitCode from an attempt to add repo-overrides for baseurl, name, and label for repoids: "+repoids);
		Assert.assertEquals(result.getStderr().trim(), "", "Stderr from an attempt add a repo-overrides for baseurl, name, and label for repoids: "+repoids);
		Assert.assertEquals(result.getStdout().trim(), "Not allowed to override values for: name, label", "Stdout from an attempt add a repo-overrides for baseurl, name, and label for repoids: "+repoids);
		
		repoOverrideNameValueMap.put("baseurl", "https://cdn.redhat.com/repo-override-baseurl/$releasever/$basearch");
		result = clienttasks.repo_override_(null, null, repoids, null, repoOverrideNameValueMap, null, null, null);
		Assert.assertEquals(result.getExitCode(), Integer.valueOf(1), "ExitCode from an attempt to add repo-overrides for baseurl, name, and label for repoids: "+repoids);
		Assert.assertEquals(result.getStderr().trim(), "", "Stderr from an attempt add a repo-overrides for baseurl, name, and label for repoids: "+repoids);
		Assert.assertEquals(result.getStdout().trim(), "Not allowed to override values for: name, label, baseurl", "Stdout from an attempt add a repo-overrides for baseurl, name, and label for repoids: "+repoids);
	}
	
	@Test(	description="attempt to add an override for a name and value that exceed 255 chars",
			groups={"blockedByBug-1034396","blockedByBug-1033583","blockedByBug-1049001"},
			enabled=true)
			//@ImplementsNitrateTest(caseId=)
	public void AttemptToAddOverridesExceeding255Chars_Test() {
		
		// register
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, true, false, null, null, null);
		
		SSHCommandResult result;
		Map<String,String> repoOverrideNameValueMap = new HashMap<String,String>();
		
		// attempt to create a very long value override
		repoOverrideNameValueMap.clear();
		repoOverrideNameValueMap.put("param", "value_7890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456");
		result = clienttasks.repo_override_(null, null, "repo1", null, repoOverrideNameValueMap, null, null, null);
		//	[root@jsefler-7 ~]# subscription-manager repo-override --repo=repo1 --add=param_7890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456:value
		//	Runtime Error Could not execute JDBC batch update at org.postgresql.jdbc2.AbstractJdbc2Statement$BatchResultHandler.handleError:2,598
		// Bug 1033583 ^
		//	[root@jsefler-7 ~]# subscription-manager repo-override --repo=repo1 --add=param:value_7890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456
		//	Name and value of the override must not exceed 255 characters.
		Assert.assertEquals(result.getExitCode(), Integer.valueOf(1), "ExitCode from an attempt to add a repo-override with a value exceeding 255 chars.");
		Assert.assertEquals(result.getStderr().trim(), "", "Stderr from an attempt to add a repo-override with a value exceeding 255 chars.");
		Assert.assertEquals(result.getStdout().trim(), "Name and value of the override must not exceed 255 characters.", "Stdout from an attempt to add a repo-override with a value exceeding 255 chars.");
		
		// attempt to create a very long parameter override
		repoOverrideNameValueMap.clear();
		repoOverrideNameValueMap.put("param_7890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456", "value");
		result = clienttasks.repo_override_(null, null, "repo1", null, repoOverrideNameValueMap, null, null, null);
		//	[root@jsefler-7 ~]# subscription-manager repo-override --repo=repo1 --add=param:value_7890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456
		//	Runtime Error Batch entry 0 update cp_consumer_content_override set created='2013-12-09 11:03:23.169000 -05:00:00', updated='2013-12-09 11:04:42.821000 -05:00:00', consumer_id='8a90874042bf59cd0142c9fe0de12d1c', content_label='repo1', name='param', value='value_7890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456' where id='8a90874042bf59cd0142d81941a1401a' was aborted.  Call getNextException to see the cause. at org.postgresql.jdbc2.AbstractJdbc2Statement$BatchResultHandler.handleError:2,598
		// Bug 1033583 ^
		//	[root@jsefler-7 ~]# subscription-manager repo-override --repo=repo1 --add=param_7890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456:value
		//	Name and value of the override must not exceed 255 characters.
		Assert.assertEquals(result.getExitCode(), Integer.valueOf(1), "ExitCode from an attempt to add a repo-override with a value exceeding 255 chars.");
		Assert.assertEquals(result.getStderr().trim(), "", "Stderr from an attempt to add a repo-override with a value exceeding 255 chars.");
		Assert.assertEquals(result.getStdout().trim(), "Name and value of the override must not exceed 255 characters.", "Stdout from an attempt to add a repo-override with a value exceeding 255 chars.");
	}
	
	@Test(	description="attempt to add an override to a non-existant repo (while NOT consuming entitlements)",
			groups={"blockedByBug-1032673","blockedByBug-1034396"},
			enabled=true)
			//@ImplementsNitrateTest(caseId=)
	public void AddOverrideWithoutEntitlementsAttached_Test() {
		
		// register
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, true, false, null, null, null);
		
		// attempt to add an override to non-existent-repos (without entitlements attached)
		List<String> repos = Arrays.asList(new String[]{"any-repo-1","Any-Repo-2"});
		Map<String,String> repoOverrideNameValueMap = new HashMap<String,String>();
		repoOverrideNameValueMap.put("any_parameter", "value");
		SSHCommandResult result = clienttasks.repo_override_(null, null, repos, null, repoOverrideNameValueMap, null, null, null);
		Assert.assertEquals(result.getStderr().trim(), "", "Stderr from an attempt to add repo-overrides to non-existant repos "+repos);
		for (String repo : repos) {
			String expectedStdoutMessage = String.format("Repository '%s' does not currently exist, but the override has been added.",repo);
			Assert.assertTrue(result.getStdout().trim().contains(expectedStdoutMessage), "Stdout from the attempt to add an override to a non-existant repo contains the expected feedback message '"+expectedStdoutMessage+"'.");
		}
		Assert.assertEquals(result.getExitCode(), Integer.valueOf(0), "The exit code from the repo-override command indicates a success.");
		
		// assert the repoOverrideNameValueMap were actually added to the list
		if (repoOverrideNameValueMap!=null && !repoOverrideNameValueMap.isEmpty()) {
			SSHCommandResult listResult = clienttasks.repo_override(true,null,(String)null,(String)null,null,null,null,null);
			for (String repoId : repos) {
				for (String name : repoOverrideNameValueMap.keySet()) {
					String value = repoOverrideNameValueMap.get(name);
					String regex = String.format(SubscriptionManagerTasks.repoOverrideListRepositoryNameValueRegexFormat,repoId,name,value.replace("*", "\\*").replace("?", "\\?"));	// notice that we have to escape glob characters from the value so they don't get interpreted as regex chars
					Assert.assertTrue(SubscriptionManagerCLITestScript.doesStringContainMatches(listResult.getStdout(), regex),"After adding a repo-override, the subscription-manager repo-override list reports override repo='"+repoId+"' name='"+name+"' value='"+value+"'.");
				}
			}
		}
		
		// re-verify no entitlements are attached
		Assert.assertTrue(clienttasks.getCurrentlyConsumedProductSubscriptions().isEmpty(), "No entitlements should be attached.");
	}
	
	@Test(	description="attempt to add an override to a non-existant repo (while consuming entitlements)",
			groups={"blockedByBug-1032673"},
			enabled=true)
			//@ImplementsNitrateTest(caseId=)
	public void AddOverrideToNonExistantRepo_Test() {
		
		// register
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, true, false, null, null, null);
		
		// subscribe to a random pool (so as to consume an entitlement)
		attachRandomSubscriptionThatProvidesYumRepos();
		
		// attempt to add an override to non-existant-repos
		List<String> repos = Arrays.asList(new String[]{"non-existant-repo-1","Non-Existant-Repo-2"});
		Map<String,String> repoOverrideNameValueMap = new HashMap<String,String>();
		repoOverrideNameValueMap.put("test_parameter", "value");
		SSHCommandResult result = clienttasks.repo_override_(null, null, repos, null, repoOverrideNameValueMap, null, null, null);
		Assert.assertEquals(result.getStderr().trim(), "", "Stderr from an attempt to add repo-overrides to non-existant repos "+repos);
		for (String repo : repos) {
			String expectedStdoutMessage = String.format("Repository '%s' does not currently exist, but the override has been added.",repo);
			Assert.assertTrue(result.getStdout().trim().contains(expectedStdoutMessage), "Stdout from the attempt to add an override to a non-existant repo contains the expected feedback message '"+expectedStdoutMessage+"'.");
		}
		Assert.assertEquals(result.getExitCode(), Integer.valueOf(0), "The exit code from the repo-override command indicates a success.");
	}
	
	@Test(	description="attempt to add overrides in mixed cases - add parameters should be lowercased - repoid names can be mixed case",
			groups={"blockedByBug-1034375"},
			enabled=true)
			//@ImplementsNitrateTest(caseId=)
	public void AttemptToAddOverrideInMixedCases_Test() {
		
		// register
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, true, false, null, null, null);
		
		// subscribe to a random pool (so as to consume an entitlement)
		attachRandomSubscriptionThatProvidesYumRepos();
		
		// attempt to add an override to upper and lower cases
		List<String> repos = Arrays.asList(new String[]{"REPO-1","repo-1","repo-2","REPO-2"});
		Map<String,String> repoOverrideNameValueMap = new HashMap<String,String>();
		repoOverrideNameValueMap.put("parameter_1", "1");
		repoOverrideNameValueMap.put("PARAMETER_1", "1");
		repoOverrideNameValueMap.put("PARAMETER_2", "2");
		repoOverrideNameValueMap.put("parameter_2", "2");
		SSHCommandResult result = clienttasks.repo_override_(null, null, repos, null, repoOverrideNameValueMap, null, null, null);
		Assert.assertEquals(result.getStderr().trim(), "", "Stderr from an attempt to add multi-case repo-overrides to repos "+repos);
		Assert.assertEquals(result.getExitCode(),  Integer.valueOf(0), "ExitCode from an attempt to add multi-case repo-overrides to repos "+repos);
		
		String name, value;
		SSHCommandResult listResult = clienttasks.repo_override(true,null,(String)null,(String)null,null,null,null,null);
		for (String repoId : repos) {
			value = "1";
			name = "parameter_1";
			Assert.assertTrue(SubscriptionManagerCLITestScript.doesStringContainMatches(listResult.getStdout(), String.format(SubscriptionManagerTasks.repoOverrideListRepositoryNameValueRegexFormat,repoId,name.toLowerCase(),value)),"After adding repo-override parameter '"+name+"', the subscription-manager repo-override --list should show that it was lower cased and added to repo='"+repoId+"' name='"+name.toLowerCase()+"'.");
			name = "PARAMETER_1";
			Assert.assertTrue(SubscriptionManagerCLITestScript.doesStringContainMatches(listResult.getStdout(), String.format(SubscriptionManagerTasks.repoOverrideListRepositoryNameValueRegexFormat,repoId,name.toLowerCase(),value)),"After adding repo-override parameter '"+name+"', the subscription-manager repo-override --list should show that it was lower cased and added to repo='"+repoId+"' name='"+name.toLowerCase()+"'.");
			Assert.assertTrue(!SubscriptionManagerCLITestScript.doesStringContainMatches(listResult.getStdout(), String.format(SubscriptionManagerTasks.repoOverrideListRepositoryNameValueRegexFormat,repoId,name,value)),"After adding repo-override parameter '"+name+"' containing uppercase characters, subscription-manager repo-override should automatically lowercase it and add it to repo='"+repoId+"' as name='"+name.toLowerCase()+"'.");
			value = "2";
			name = "parameter_2";
			Assert.assertTrue(SubscriptionManagerCLITestScript.doesStringContainMatches(listResult.getStdout(), String.format(SubscriptionManagerTasks.repoOverrideListRepositoryNameValueRegexFormat,repoId,name.toLowerCase(),value)),"After adding repo-override parameter '"+name+"', the subscription-manager repo-override --list should show that it was lower cased and added to repo='"+repoId+"' name='"+name.toLowerCase()+"'.");
			name = "PARAMETER_2";
			Assert.assertTrue(SubscriptionManagerCLITestScript.doesStringContainMatches(listResult.getStdout(), String.format(SubscriptionManagerTasks.repoOverrideListRepositoryNameValueRegexFormat,repoId,name.toLowerCase(),value)),"After adding repo-override parameter '"+name+"', the subscription-manager repo-override --list should show that it was lower cased and added to repo='"+repoId+"' name='"+name.toLowerCase()+"'.");
			Assert.assertTrue(!SubscriptionManagerCLITestScript.doesStringContainMatches(listResult.getStdout(), String.format(SubscriptionManagerTasks.repoOverrideListRepositoryNameValueRegexFormat,repoId,name,value)),"After adding repo-override parameter '"+name+"' containing uppercase characters, subscription-manager repo-override should automatically lowercase it and add it to repo='"+repoId+"' as name='"+name.toLowerCase()+"'.");
		}	
	}
	
	@Test(	description="add yum repo overrides, verify they persist, and remove them one repo at a time",
			groups={"blockedByBug-1034396"},
			enabled=true)
			//@ImplementsNitrateTest(caseId=)
	public void AddAndRemoveRepoOverridesOneRepoAtATime_Test() {

		// register
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, true, false, null, null, null);
		
		// subscribe to a random pool (so as to consume an entitlement) and remember the original list of YumRepos read from the redhat.repo file
		List<YumRepo> originalYumRepos = attachRandomSubscriptionThatProvidesYumRepos();
		
		// choose a random small subset of repos to test repo-override
		List<YumRepo> originalYumReposSubset = getRandomSubsetOfList(originalYumRepos, 5);
		
		// add several repo overrides (one repo at a time)
		Map<String,Map<String,String>> repoOverridesMapOfMaps = new HashMap<String,Map<String,String>>();
		for (YumRepo yumRepo : originalYumReposSubset) {
			String repoId = yumRepo.id;
			Map<String,String> repoOverrideNameValueMap = new HashMap<String,String>();
			repoOverrideNameValueMap.put("enabled", "true");
			repoOverrideNameValueMap.put("gpgcheck", "false");
			repoOverrideNameValueMap.put("exclude", "foo-bar");
			repoOverrideNameValueMap.put("retries", "5");
			repoOverrideNameValueMap.put("ui_repoid_vars", "releasever basearch foo");
			repoOverrideNameValueMap.put("sslverify", "false");
			repoOverrideNameValueMap.put("sslcacert", "/overridden/candlepin.pem");
			repoOverrideNameValueMap.put("sslclientkey", "/overridden/serial-key.pem");
			repoOverrideNameValueMap.put("sslclientcert", "/overridden/serial.pem");
			repoOverridesMapOfMaps.put(repoId, repoOverrideNameValueMap);
			clienttasks.repo_override(null, null, repoId, null, repoOverrideNameValueMap, null, null, null);
		}

		// verify the current YumRepos read from the redhat.repo file actually contain the overrides
		verifyCurrentYumReposReflectRepoOverrides(originalYumRepos,repoOverridesMapOfMaps, true);
		
		// unsubscribe/resubscribe
		SSHCommandResult listResultBeforeUnsubscribe = clienttasks.repo_override_(true,null,(String)null,(String)null,null,null,null,null);
		List<String> poolIds = new ArrayList<String>();
		for (ProductSubscription productSubscription : clienttasks.getCurrentlyConsumedProductSubscriptions()) {
			clienttasks.unsubscribe(true, productSubscription.serialNumber, null, null, null);
			poolIds.add(productSubscription.poolId);
		}
		SSHCommandResult listResultAfterUnsubscribe = clienttasks.repo_override_(true,null,(String)null,(String)null,null,null,null,null);
		// valid prior to bug 1034396	Assert.assertEquals(listResultAfterUnsubscribe.getStdout().trim(), "This system does not have any subscriptions.", "Stdout from repo-override --list without any subscriptions attached (but should still have overrides cached in the consumer).");
		Assert.assertEquals(listResultAfterUnsubscribe.getStdout(), listResultBeforeUnsubscribe.getStdout(), "Stdout from repo-override --list without any subscriptions attached should be identical to the list when subscriptions were attached.");
		Assert.assertTrue(clienttasks.getCurrentlySubscribedYumRepos().isEmpty(), "The YumRepos in '"+clienttasks.redhatRepoFile+"' should be empty after unsubscribing from each serial.");
		clienttasks.subscribe(null, null, poolIds, null, null, null, null, null, null, null, null);
		
		// ...and verify the YumRepos read from the redhat.repo file persists the overrides
		verifyCurrentYumReposReflectRepoOverrides(originalYumRepos,repoOverridesMapOfMaps, false/*because after unsubscribe/resubscribe the current yum repos come from a new entitlement and therefore cannot be equal to the originalYumRepo value for sslclientcert and sslclientkey*/);
		
		// remove names from one repo override and verify the list
		String repoId = (String) repoOverridesMapOfMaps.keySet().toArray()[0];	// choose one repoId
		clienttasks.repo_override(null,null,Arrays.asList(new String[]{repoId}),Arrays.asList(new String[]{"name"}),null,null,null,null);
		repoOverridesMapOfMaps.get(repoId).remove("name");
		clienttasks.repo_override(null,null,Arrays.asList(new String[]{repoId}),Arrays.asList(new String[]{"name","enabled","ui_repoid_vars"}),null,null,null,null);
		repoOverridesMapOfMaps.get(repoId).remove("name");
		repoOverridesMapOfMaps.get(repoId).remove("enabled");
		repoOverridesMapOfMaps.get(repoId).remove("ui_repoid_vars");
		//clienttasks.repo_override(null,null,Arrays.asList(new String[]{repoId}),Arrays.asList(new String[]{"gpgcheck","exclude","retries"}),null,null,null,null);	// for test variability, let's not delete these
		//repoOverridesMapOfMaps.get(repoId).remove("gpgcheck");
		//repoOverridesMapOfMaps.get(repoId).remove("exclude");
		//repoOverridesMapOfMaps.get(repoId).remove("retries");
		clienttasks.repo_override(null,null,Arrays.asList(new String[]{repoId}),Arrays.asList(new String[]{"sslverify","sslcacert","sslclientkey","sslclientcert"}),null,null,null,null);
		repoOverridesMapOfMaps.get(repoId).remove("sslverify");
		repoOverridesMapOfMaps.get(repoId).remove("sslcacert");
		repoOverridesMapOfMaps.get(repoId).remove("sslclientkey");
		repoOverridesMapOfMaps.get(repoId).remove("sslclientcert");
		//repoOverridesMapOfMaps.remove(repoid);	// remove one repoid from the overrides Map
		
		// verify the current YumRepos read from the redhat.repo file no longer contains the removed override (the original should be restored)
		verifyCurrentYumReposReflectRepoOverrides(originalYumRepos,repoOverridesMapOfMaps, false);

		// remove all of the repo overrides
		clienttasks.repo_override(null,true,(String)null,(String)null,null,null,null,null);	
		repoOverridesMapOfMaps.clear();
		
		// verify the current YumRepos read from the redhat.repo file no longer contains any overrides (the original should be restored)
		verifyCurrentYumReposReflectRepoOverrides(originalYumRepos,repoOverridesMapOfMaps, false);
	}
	
	@Test(	description="add yum repo overrides, verify they persist, and remove them across multiple repo ids simultaneously (use multiple --repo args)",
			groups={"blockedByBug-1034396"},
			enabled=true)
			//@ImplementsNitrateTest(caseId=)
	public void AddAndRemoveRepoOverridesUsingMultipleRepos_Test() {
		
		// register
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, true, false, null, null, null);
		
		// subscribe to a random pool (so as to consume an entitlement) and remember the original list of YumRepos read from the redhat.repo file
		List<YumRepo> originalYumRepos = attachRandomSubscriptionThatProvidesYumRepos();
		
		// choose a random small subset of repos to test repo-override
		List<YumRepo> originalYumReposSubset = getRandomSubsetOfList(originalYumRepos, 5);
		
		// add several repo overrides (specifying all repos in multiplicity)
		Map<String,Map<String,String>> repoOverridesMapOfMaps = new HashMap<String,Map<String,String>>();
		Map<String,String> repoOverrideNameValueMap = new HashMap<String,String>();
		repoOverrideNameValueMap.put("enabled", "true");
		repoOverrideNameValueMap.put("gpgcheck", "false");
		repoOverrideNameValueMap.put("exclude", "foo-bar");
		repoOverrideNameValueMap.put("retries", "5");
		repoOverrideNameValueMap.put("ui_repoid_vars", "releasever basearch foo");
		repoOverrideNameValueMap.put("sslverify", "false");
		repoOverrideNameValueMap.put("sslcacert", "/overridden/candlepin.pem");
		repoOverrideNameValueMap.put("sslclientkey", "/overridden/serial-key.pem");
		repoOverrideNameValueMap.put("sslclientcert", "/overridden/serial.pem");
		for (YumRepo yumRepo : originalYumReposSubset) repoOverridesMapOfMaps.put(yumRepo.id, repoOverrideNameValueMap);
		List<String> repoIds = new ArrayList<String>(repoOverridesMapOfMaps.keySet());
		clienttasks.repo_override(null, null, repoIds, null, repoOverrideNameValueMap, null, null, null);
		
		// verify the current YumRepos read from the redhat.repo file actually contain the overrides
		verifyCurrentYumReposReflectRepoOverrides(originalYumRepos,repoOverridesMapOfMaps, true);
		
		// unsubscribe/resubscribe
		SSHCommandResult listResultBeforeUnsubscribe = clienttasks.repo_override_(true,null,(String)null,(String)null,null,null,null,null);
		List<String> poolIds = new ArrayList<String>();
		for (ProductSubscription productSubscription : clienttasks.getCurrentlyConsumedProductSubscriptions()) {
			clienttasks.unsubscribe(true, productSubscription.serialNumber, null, null, null);
			poolIds.add(productSubscription.poolId);
		}
		SSHCommandResult listResultAfterUnsubscribe = clienttasks.repo_override_(true,null,(String)null,(String)null,null,null,null,null);
		// valid prior to bug 1034396	Assert.assertEquals(listResultAfterUnsubscribe.getStdout().trim(), "This system does not have any subscriptions.", "Stdout from repo-override --list without any subscriptions attached (but should still have overrides cached in the consumer).");
		Assert.assertEquals(listResultAfterUnsubscribe.getStdout(), listResultBeforeUnsubscribe.getStdout(), "Stdout from repo-override --list without any subscriptions attached should be identical to the list when subscriptions were attached.");
		Assert.assertTrue(clienttasks.getCurrentlySubscribedYumRepos().isEmpty(), "The YumRepos in '"+clienttasks.redhatRepoFile+"' should be empty after unsubscribing from each serial.");
		clienttasks.subscribe(null, null, poolIds, null, null, null, null, null, null, null, null);
		
		// ...and verify the YumRepos read from the redhat.repo file persists the overrides
		verifyCurrentYumReposReflectRepoOverrides(originalYumRepos,repoOverridesMapOfMaps, false/*because after unsubscribe/resubscribe the current yum repos come from a new entitlement and therefore cannot be equal to the originalYumRepo value for sslclientcert and sslclientkey*/);
		
		// remove names from multiple repos using repo-override and verify the list
		clienttasks.repo_override(null,null,repoIds,Arrays.asList(new String[]{"name"}),null,null,null,null);
		clienttasks.repo_override(null,null,repoIds,Arrays.asList(new String[]{"name","enabled","ui_repoid_vars"}),null,null,null,null);
		// clienttasks.repo_override(null,null,repoids,Arrays.asList(new String[]{"gpgcheck","exclude","retries"}),null,null,null,null);	// for test variability, let's not delete these
		clienttasks.repo_override(null,null,repoIds,Arrays.asList(new String[]{"sslverify","sslcacert","sslclientkey","sslclientcert"}),null,null,null,null);
		for (String repoId : repoIds) {
			repoOverridesMapOfMaps.get(repoId).remove("name");
			repoOverridesMapOfMaps.get(repoId).remove("enabled");
			repoOverridesMapOfMaps.get(repoId).remove("ui_repoid_vars");
			//repoOverridesMapOfMaps.get(repoId).remove("gpgcheck");
			//repoOverridesMapOfMaps.get(repoId).remove("exclude");
			//repoOverridesMapOfMaps.get(repoId).remove("retries");
			repoOverridesMapOfMaps.get(repoId).remove("sslverify");
			repoOverridesMapOfMaps.get(repoId).remove("sslcacert");
			repoOverridesMapOfMaps.get(repoId).remove("sslclientkey");
			repoOverridesMapOfMaps.get(repoId).remove("sslclientcert");
		}
		
		// verify the current YumRepos read from the redhat.repo file no longer contains the removed override (the original should be restored)
		verifyCurrentYumReposReflectRepoOverrides(originalYumRepos,repoOverridesMapOfMaps, false);

		// remove all of the repo overrides
		clienttasks.repo_override(null,true,(List<String>)null,(List<String>)null,null,null,null,null);
		repoOverridesMapOfMaps.clear();
		
		// verify the current YumRepos read from the redhat.repo file no longer contains any overrides (the original should be restored)
		verifyCurrentYumReposReflectRepoOverrides(originalYumRepos,repoOverridesMapOfMaps, false);
	}
	
	
	@Test(	description="verify that the rhsm.full_refresh_on_yum is working properly",
			groups={"VerifyRhsmConfigurationForFullRefreshOnYum_Test"},
			enabled=true)
			//@ImplementsNitrateTest(caseId=)
	public void VerifyRhsmConfigurationForFullRefreshOnYum_Test() {
		
		// There is a new rhsm.conf parameter called full_refresh_on_yum = 0 (default) or 1 that is a boolean for
		// telling the subscription-manager yum plugin whether or not to use the repo overrides defined in
		// /var/lib/rhsm/cache/content_overrides.json (when full_refresh_on_yum=0) or if the subscription-manager
		// yum plugin should go to the candlepin server and fetch the latest overrides defined for this consumer
		// at the candlepin server and then complete the yum transaction.
		
		// We will test the functionality of this configuration by adding repo overrides, then deleting the cache
		// followed by a yum transaction to verify that the override is no longer present in the redhat.repo file.
		// Then by changing to full_refresh_on_yum=1, the overrides will re-appear in the redhat.repo file after
		// running a yum transaction
		
		// remember the original configured value for rhsm.full_refresh_on_yum so we can restore it after the test
		rhsmFullRefreshOnYumConfigured = clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "rhsm", "full_refresh_on_yum");
		
		// assume we are starting with the default rhsm.full_refresh_on_yum=0
		Assert.assertEquals(clienttasks.getConfParameter("full_refresh_on_yum"),"0", "The expected default value for configuration parameter rhsm.full_refresh_on_yum.");
		
		// register
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, true, null, null, null, null);
		
		// subscribe to a random pool (so as to consume an entitlement) and remember the original list of YumRepos read from the redhat.repo file
		List<YumRepo> originalYumRepos = attachRandomSubscriptionThatProvidesYumRepos();
		
		// choose one random repo
		YumRepo originalYumRepo = getRandomSubsetOfList(originalYumRepos, 1).get(0);
		YumRepo currentYumRepo;
		
		// add several repo overrides
		String repoId = originalYumRepo.id;
		String name;
		Map<String,Map<String,String>> repoOverridesMapOfMaps = new HashMap<String,Map<String,String>>();
		Map<String,String> repoOverrideNameValueMap = new HashMap<String,String>();
		repoOverrideNameValueMap.put("retries", "5");
		repoOverrideNameValueMap.put("timeout", "40");
		repoOverrideNameValueMap.put("enabled", originalYumRepo.enabled? Boolean.FALSE.toString():Boolean.TRUE.toString());	// put the opposite of originalYumRepo.enabled
		repoOverridesMapOfMaps.put(repoId, repoOverrideNameValueMap);
		clienttasks.repo_override(null, null, repoId, null, repoOverrideNameValueMap, null, null, null);
		
		// verify the current YumRepos read from the redhat.repo file actually contain the overrides
		clienttasks.getYumRepolist("all");
		currentYumRepo = YumRepo.findFirstInstanceWithMatchingFieldFromList("id", repoId,  clienttasks.getCurrentlySubscribedYumRepos());
		name = "retries"; Assert.assertEquals(currentYumRepo.retries,            repoOverrideNameValueMap.get(name), "After adding a repo-override for repoId '"+repoId+"' parameter '"+name+"' and running a yum transaction, the subscription-manager yum plugin should read the repo overrides from cache file '"+clienttasks.rhsmCacheRepoOverridesFile+"' and apply them to '"+clienttasks.redhatRepoFile+"'.");
		name = "timeout"; Assert.assertEquals(currentYumRepo.timeout,            repoOverrideNameValueMap.get(name), "After adding a repo-override for repoId '"+repoId+"' parameter '"+name+"' and running a yum transaction, the subscription-manager yum plugin should read the repo overrides from cache file '"+clienttasks.rhsmCacheRepoOverridesFile+"' and apply them to '"+clienttasks.redhatRepoFile+"'.");
		name = "enabled"; Assert.assertEquals(currentYumRepo.enabled.toString(), repoOverrideNameValueMap.get(name), "After adding a repo-override for repoId '"+repoId+"' parameter '"+name+"' and running a yum transaction, the subscription-manager yum plugin should read the repo overrides from cache file '"+clienttasks.rhsmCacheRepoOverridesFile+"' and apply them to '"+clienttasks.redhatRepoFile+"'.");
		
		// now let's delete the cache for /var/lib/rhsm/cache/content_overrides.json and verify that
		// after running a yum transaction, the override is no longer present (because full_refresh_on_yum=0)
		client.runCommandAndWait("rm -f "+clienttasks.rhsmCacheRepoOverridesFile);
		clienttasks.getYumRepolist("all");
		currentYumRepo = YumRepo.findFirstInstanceWithMatchingFieldFromList("id", repoId,  clienttasks.getCurrentlySubscribedYumRepos());
		name = "retries"; Assert.assertEquals(currentYumRepo.retries,            originalYumRepo.retries, "After removing '"+clienttasks.rhsmCacheRepoOverridesFile+"' and running a yum transaction, the repo overrides in the '"+clienttasks.redhatRepoFile+"' should be gone; orginal values should be restored for repoId '"+repoId+"' parameter '"+name+"'.");
		name = "timeout"; Assert.assertEquals(currentYumRepo.timeout,            originalYumRepo.timeout, "After removing '"+clienttasks.rhsmCacheRepoOverridesFile+"' and running a yum transaction, the repo overrides in the '"+clienttasks.redhatRepoFile+"' should be gone; orginal values should be restored for repoId '"+repoId+"' parameter '"+name+"'.");
		name = "enabled"; Assert.assertEquals(currentYumRepo.enabled,            originalYumRepo.enabled, "After removing '"+clienttasks.rhsmCacheRepoOverridesFile+"' and running a yum transaction, the repo overrides in the '"+clienttasks.redhatRepoFile+"' should be gone; orginal values should be restored for repoId '"+repoId+"' parameter '"+name+"'.");
		Assert.assertTrue(!RemoteFileTasks.testExists(client, clienttasks.rhsmCacheRepoOverridesFile),"Re-asserting cache file '"+clienttasks.rhsmCacheRepoOverridesFile+"' has been deleted.");
		
		// now let's set rhsm.full_refresh_on_yum to 1 and test...
		clienttasks.config(null, null, true, new String[]{"rhsm","full_refresh_on_yum","1"});
		clienttasks.getYumRepolist("all");
		currentYumRepo = YumRepo.findFirstInstanceWithMatchingFieldFromList("id", repoId,  clienttasks.getCurrentlySubscribedYumRepos());
		name = "retries"; Assert.assertEquals(currentYumRepo.retries,            repoOverrideNameValueMap.get(name), "After setting rhsm.full_refresh_on_yum=1 and invoking a yum transaction, repoId '"+repoId+"' override for parameter '"+name+"' should have been restorted in the '"+clienttasks.redhatRepoFile+"'.");
		name = "timeout"; Assert.assertEquals(currentYumRepo.timeout,            repoOverrideNameValueMap.get(name), "After setting rhsm.full_refresh_on_yum=1 and invoking a yum transaction, repoId '"+repoId+"' override for parameter '"+name+"' should have been restorted in the '"+clienttasks.redhatRepoFile+"'.");
		name = "enabled"; Assert.assertEquals(currentYumRepo.enabled.toString(), repoOverrideNameValueMap.get(name), "After setting rhsm.full_refresh_on_yum=1 and invoking a yum transaction, repoId '"+repoId+"' override for parameter '"+name+"' should have been restorted in the '"+clienttasks.redhatRepoFile+"'.");
		Assert.assertTrue(RemoteFileTasks.testExists(client, clienttasks.rhsmCacheRepoOverridesFile),"Testing for the restoration of cache file '"+clienttasks.rhsmCacheRepoOverridesFile+"'.");
		
		// now let's test that a trigger of rhsmcertd will restore the cache even when rhsm.full_refresh_on_yum=0
		clienttasks.config(null, null, true, new String[]{"rhsm","full_refresh_on_yum","0"});
		client.runCommandAndWait("rm -f "+clienttasks.rhsmCacheRepoOverridesFile);
		Assert.assertFalse(RemoteFileTasks.testExists(client, clienttasks.rhsmCacheRepoOverridesFile));
		clienttasks.run_rhsmcertd_worker(null);	//clienttasks.restart_rhsmcertd(null, null, false, true);	// use run_rhsmcertd_worker as a faster alternative to restart_rhsmcertd
		Assert.assertTrue(RemoteFileTasks.testExists(client, clienttasks.rhsmCacheRepoOverridesFile),"Testing for the restoration of cache file '"+clienttasks.rhsmCacheRepoOverridesFile+"' after running rhsmcertd_worker to trigger a certificates update.");
		clienttasks.getYumRepolist("all");
		currentYumRepo = YumRepo.findFirstInstanceWithMatchingFieldFromList("id", repoId,  clienttasks.getCurrentlySubscribedYumRepos());
		name = "retries"; Assert.assertEquals(currentYumRepo.retries,            repoOverrideNameValueMap.get(name), "After setting rhsm.full_refresh_on_yum=0, deleting the cache file '"+clienttasks.rhsmCacheRepoOverridesFile+"', and restarting the rhsmcertd service, repoId '"+repoId+"' override for parameter '"+name+"' should have been restorted in the '"+clienttasks.redhatRepoFile+"'.");
		name = "timeout"; Assert.assertEquals(currentYumRepo.timeout,            repoOverrideNameValueMap.get(name), "After setting rhsm.full_refresh_on_yum=0, deleting the cache file '"+clienttasks.rhsmCacheRepoOverridesFile+"', and restarting the rhsmcertd service, repoId '"+repoId+"' override for parameter '"+name+"' should have been restorted in the '"+clienttasks.redhatRepoFile+"'.");
		name = "enabled"; Assert.assertEquals(currentYumRepo.enabled.toString(), repoOverrideNameValueMap.get(name), "After setting rhsm.full_refresh_on_yum=0, deleting the cache file '"+clienttasks.rhsmCacheRepoOverridesFile+"', and restarting the rhsmcertd service, repoId '"+repoId+"' override for parameter '"+name+"' should have been restorted in the '"+clienttasks.redhatRepoFile+"'.");
	}
	@AfterGroups(value={"VerifyRhsmConfigurationForFullRefreshOnYum_Test"},groups={"setup"})
	public void afterVerifyRhsmConfigurationForFullRefreshOnYum_Test() {
		if (rhsmFullRefreshOnYumConfigured!=null) clienttasks.config(null,null,true, new String[]{"rhsm","full_refresh_on_yum",rhsmFullRefreshOnYumConfigured});
	}
	protected String rhsmFullRefreshOnYumConfigured = null;
	
	
	
	
	
	
	
	
	
	
	// Candidates for an automated Test:
	
	
	
	
	// Configuration methods ***********************************************************************
	
	
	
	// Protected methods ***********************************************************************
	
	/**
	 * @return a list of YumRepos that result after randomly attaching a subscription
	 */
	protected List<YumRepo> attachRandomSubscriptionThatProvidesYumRepos() {
		List<SubscriptionPool> pools = clienttasks.getCurrentlyAvailableSubscriptionPools();
		if (pools.isEmpty()) throw new SkipException("Cannot randomly pick a pool for subscribing when there are no available pools for testing."); 
		List<YumRepo> yumRepos;	// = new ArrayList<YumRepo>();
		do {
			//not important clienttasks.unsubscribe_(true, (BigInteger)null, null, null, null);
			SubscriptionPool pool = pools.get(randomGenerator.nextInt(pools.size())); // randomly pick a pool
			pools.remove(pool);
			clienttasks.subscribeToSubscriptionPool(pool);
			yumRepos = clienttasks.getCurrentlySubscribedYumRepos();
		} while (yumRepos.isEmpty() && !pools.isEmpty());
		if (yumRepos.isEmpty()) throw new SkipException("Could not find a pool that provided YumRepos after subscribing."); 
		return yumRepos;
	}
	
	protected void verifyCurrentYumReposReflectRepoOverrides(List<YumRepo> originalYumRepos, Map<String,Map<String,String>> repoOverridesMapOfMaps, boolean includeVerificationOfNames_sslclient) {
		
		// verify the current YumRepos read from the redhat.repo file actually contain the overrides
		List<YumRepo> currentYumRepos = clienttasks.getCurrentlySubscribedYumRepos();
		Assert.assertEquals(currentYumRepos.size(), originalYumRepos.size(), "The number of yum repos in "+clienttasks.redhatRepoFile+" should remain the same after applying subscription-manager repo-overrides.");
		for (YumRepo currentYumRepo : currentYumRepos) {
			String name, repoId = currentYumRepo.id;
			YumRepo originalYumRepo = YumRepo.findFirstInstanceWithMatchingFieldFromList("id", repoId, originalYumRepos);
			
			// this list of names should include all the fields from public class YumRepo
			name = "id";					if (repoOverridesMapOfMaps.containsKey(repoId) && repoOverridesMapOfMaps.get(repoId).containsKey(name)) {Assert.assertEquals(currentYumRepo.id==null?null:currentYumRepo.id.toString(), repoOverridesMapOfMaps.get(repoId).get(name), "The repo-override for repoId='"+repoId+"' name='"+name+"'.");} else {Assert.assertEquals(currentYumRepo.id==null?null:currentYumRepo.id.toString(), originalYumRepo.id==null?null:originalYumRepo.id.toString(), "There should NOT be a repo-override for repoId='"+repoId+"' name='"+name+"'.");}
			name = "name";					if (repoOverridesMapOfMaps.containsKey(repoId) && repoOverridesMapOfMaps.get(repoId).containsKey(name)) {Assert.assertEquals(currentYumRepo.name==null?null:currentYumRepo.name.toString(), repoOverridesMapOfMaps.get(repoId).get(name), "The repo-override for repoId='"+repoId+"' name='"+name+"'.");} else {Assert.assertEquals(currentYumRepo.name==null?null:currentYumRepo.name.toString(), originalYumRepo.name==null?null:originalYumRepo.name.toString(), "There should NOT be a repo-override for repoId='"+repoId+"' name='"+name+"'.");}
			name = "baseurl";				if (repoOverridesMapOfMaps.containsKey(repoId) && repoOverridesMapOfMaps.get(repoId).containsKey(name)) {Assert.assertEquals(currentYumRepo.baseurl==null?null:currentYumRepo.baseurl.toString(), repoOverridesMapOfMaps.get(repoId).get(name), "The repo-override for repoId='"+repoId+"' name='"+name+"'.");} else {Assert.assertEquals(currentYumRepo.baseurl==null?null:currentYumRepo.baseurl.toString(), originalYumRepo.baseurl==null?null:originalYumRepo.baseurl.toString(), "There should NOT be a repo-override for repoId='"+repoId+"' name='"+name+"'.");}
			name = "enabled";				if (repoOverridesMapOfMaps.containsKey(repoId) && repoOverridesMapOfMaps.get(repoId).containsKey(name)) {Assert.assertEquals(currentYumRepo.enabled==null?null:currentYumRepo.enabled.toString(), repoOverridesMapOfMaps.get(repoId).get(name), "The repo-override for repoId='"+repoId+"' name='"+name+"'.");} else {Assert.assertEquals(currentYumRepo.enabled==null?null:currentYumRepo.enabled.toString(), originalYumRepo.enabled==null?null:originalYumRepo.enabled.toString(), "There should NOT be a repo-override for repoId='"+repoId+"' name='"+name+"'.");}
			name = "gpgcheck";				if (repoOverridesMapOfMaps.containsKey(repoId) && repoOverridesMapOfMaps.get(repoId).containsKey(name)) {Assert.assertEquals(currentYumRepo.gpgcheck==null?null:currentYumRepo.gpgcheck.toString(), repoOverridesMapOfMaps.get(repoId).get(name), "The repo-override for repoId='"+repoId+"' name='"+name+"'.");} else {Assert.assertEquals(currentYumRepo.gpgcheck==null?null:currentYumRepo.gpgcheck.toString(), originalYumRepo.gpgcheck==null?null:originalYumRepo.gpgcheck.toString(), "There should NOT be a repo-override for repoId='"+repoId+"' name='"+name+"'.");}
			name = "sslcacert";				if (repoOverridesMapOfMaps.containsKey(repoId) && repoOverridesMapOfMaps.get(repoId).containsKey(name)) {Assert.assertEquals(currentYumRepo.sslcacert==null?null:currentYumRepo.sslcacert.toString(), repoOverridesMapOfMaps.get(repoId).get(name), "The repo-override for repoId='"+repoId+"' name='"+name+"'.");} else {Assert.assertEquals(currentYumRepo.sslcacert==null?null:currentYumRepo.sslcacert.toString(), originalYumRepo.sslcacert==null?null:originalYumRepo.sslcacert.toString(), "There should NOT be a repo-override for repoId='"+repoId+"' name='"+name+"'.");}
			name = "sslverify";				if (repoOverridesMapOfMaps.containsKey(repoId) && repoOverridesMapOfMaps.get(repoId).containsKey(name)) {Assert.assertEquals(currentYumRepo.sslverify==null?null:currentYumRepo.sslverify.toString(), repoOverridesMapOfMaps.get(repoId).get(name), "The repo-override for repoId='"+repoId+"' name='"+name+"'.");} else {Assert.assertEquals(currentYumRepo.sslverify==null?null:currentYumRepo.sslverify.toString(), originalYumRepo.sslverify==null?null:originalYumRepo.sslverify.toString(), "There should NOT be a repo-override for repoId='"+repoId+"' name='"+name+"'.");}
			/* these two options are verified below to account for boolean includeVerificationOfNames_sslclient
			name = "sslclientcert";			if (repoOverridesMapOfMaps.containsKey(repoId) && repoOverridesMapOfMaps.get(repoId).containsKey(name)) {Assert.assertEquals(currentYumRepo.sslclientcert==null?null:currentYumRepo.sslclientcert.toString(), repoOverridesMapOfMaps.get(repoId).get(name), "The repo-override for repoId='"+repoId+"' name='"+name+"'.");} else {Assert.assertEquals(currentYumRepo.sslclientcert==null?null:currentYumRepo.sslclientcert.toString(), originalYumRepo.sslclientcert==null?null:originalYumRepo.sslclientcert.toString(), "There should NOT be a repo-override for repoId='"+repoId+"' name='"+name+"'.");}
			name = "sslclientkey";			if (repoOverridesMapOfMaps.containsKey(repoId) && repoOverridesMapOfMaps.get(repoId).containsKey(name)) {Assert.assertEquals(currentYumRepo.sslclientkey==null?null:currentYumRepo.sslclientkey.toString(), repoOverridesMapOfMaps.get(repoId).get(name), "The repo-override for repoId='"+repoId+"' name='"+name+"'.");} else {Assert.assertEquals(currentYumRepo.sslclientkey==null?null:currentYumRepo.sslclientkey.toString(), originalYumRepo.sslclientkey==null?null:originalYumRepo.sslclientkey.toString(), "There should NOT be a repo-override for repoId='"+repoId+"' name='"+name+"'.");}
			*/
			name = "metadata_expire";		if (repoOverridesMapOfMaps.containsKey(repoId) && repoOverridesMapOfMaps.get(repoId).containsKey(name)) {Assert.assertEquals(currentYumRepo.metadata_expire==null?null:currentYumRepo.metadata_expire.toString(), repoOverridesMapOfMaps.get(repoId).get(name), "The repo-override for repoId='"+repoId+"' name='"+name+"'.");} else {Assert.assertEquals(currentYumRepo.metadata_expire==null?null:currentYumRepo.metadata_expire.toString(), originalYumRepo.metadata_expire==null?null:originalYumRepo.metadata_expire.toString(), "There should NOT be a repo-override for repoId='"+repoId+"' name='"+name+"'.");}
			name = "metalink";				if (repoOverridesMapOfMaps.containsKey(repoId) && repoOverridesMapOfMaps.get(repoId).containsKey(name)) {Assert.assertEquals(currentYumRepo.metalink==null?null:currentYumRepo.metalink.toString(), repoOverridesMapOfMaps.get(repoId).get(name), "The repo-override for repoId='"+repoId+"' name='"+name+"'.");} else {Assert.assertEquals(currentYumRepo.metalink==null?null:currentYumRepo.metalink.toString(), originalYumRepo.metalink==null?null:originalYumRepo.metalink.toString(), "There should NOT be a repo-override for repoId='"+repoId+"' name='"+name+"'.");}
			name = "mirrorlist";			if (repoOverridesMapOfMaps.containsKey(repoId) && repoOverridesMapOfMaps.get(repoId).containsKey(name)) {Assert.assertEquals(currentYumRepo.mirrorlist==null?null:currentYumRepo.mirrorlist.toString(), repoOverridesMapOfMaps.get(repoId).get(name), "The repo-override for repoId='"+repoId+"' name='"+name+"'.");} else {Assert.assertEquals(currentYumRepo.mirrorlist==null?null:currentYumRepo.mirrorlist.toString(), originalYumRepo.mirrorlist==null?null:originalYumRepo.mirrorlist.toString(), "There should NOT be a repo-override for repoId='"+repoId+"' name='"+name+"'.");}
			name = "repo_gpgcheck";			if (repoOverridesMapOfMaps.containsKey(repoId) && repoOverridesMapOfMaps.get(repoId).containsKey(name)) {Assert.assertEquals(currentYumRepo.repo_gpgcheck==null?null:currentYumRepo.repo_gpgcheck.toString(), repoOverridesMapOfMaps.get(repoId).get(name), "The repo-override for repoId='"+repoId+"' name='"+name+"'.");} else {Assert.assertEquals(currentYumRepo.repo_gpgcheck==null?null:currentYumRepo.repo_gpgcheck.toString(), originalYumRepo.repo_gpgcheck==null?null:originalYumRepo.repo_gpgcheck.toString(), "There should NOT be a repo-override for repoId='"+repoId+"' name='"+name+"'.");}
			name = "gpgcakey";				if (repoOverridesMapOfMaps.containsKey(repoId) && repoOverridesMapOfMaps.get(repoId).containsKey(name)) {Assert.assertEquals(currentYumRepo.gpgcakey==null?null:currentYumRepo.gpgcakey.toString(), repoOverridesMapOfMaps.get(repoId).get(name), "The repo-override for repoId='"+repoId+"' name='"+name+"'.");} else {Assert.assertEquals(currentYumRepo.gpgcakey==null?null:currentYumRepo.gpgcakey.toString(), originalYumRepo.gpgcakey==null?null:originalYumRepo.gpgcakey.toString(), "There should NOT be a repo-override for repoId='"+repoId+"' name='"+name+"'.");}
			name = "exclude";				if (repoOverridesMapOfMaps.containsKey(repoId) && repoOverridesMapOfMaps.get(repoId).containsKey(name)) {Assert.assertEquals(currentYumRepo.exclude==null?null:currentYumRepo.exclude.toString(), repoOverridesMapOfMaps.get(repoId).get(name), "The repo-override for repoId='"+repoId+"' name='"+name+"'.");} else {Assert.assertEquals(currentYumRepo.exclude==null?null:currentYumRepo.exclude.toString(), originalYumRepo.exclude==null?null:originalYumRepo.exclude.toString(), "There should NOT be a repo-override for repoId='"+repoId+"' name='"+name+"'.");}
			name = "includepkgs";			if (repoOverridesMapOfMaps.containsKey(repoId) && repoOverridesMapOfMaps.get(repoId).containsKey(name)) {Assert.assertEquals(currentYumRepo.includepkgs==null?null:currentYumRepo.includepkgs.toString(), repoOverridesMapOfMaps.get(repoId).get(name), "The repo-override for repoId='"+repoId+"' name='"+name+"'.");} else {Assert.assertEquals(currentYumRepo.includepkgs==null?null:currentYumRepo.includepkgs.toString(), originalYumRepo.includepkgs==null?null:originalYumRepo.includepkgs.toString(), "There should NOT be a repo-override for repoId='"+repoId+"' name='"+name+"'.");}
			name = "enablegroups";			if (repoOverridesMapOfMaps.containsKey(repoId) && repoOverridesMapOfMaps.get(repoId).containsKey(name)) {Assert.assertEquals(currentYumRepo.enablegroups==null?null:currentYumRepo.enablegroups.toString(), repoOverridesMapOfMaps.get(repoId).get(name), "The repo-override for repoId='"+repoId+"' name='"+name+"'.");} else {Assert.assertEquals(currentYumRepo.enablegroups==null?null:currentYumRepo.enablegroups.toString(), originalYumRepo.enablegroups==null?null:originalYumRepo.enablegroups.toString(), "There should NOT be a repo-override for repoId='"+repoId+"' name='"+name+"'.");}
			name = "failovermethod";		if (repoOverridesMapOfMaps.containsKey(repoId) && repoOverridesMapOfMaps.get(repoId).containsKey(name)) {Assert.assertEquals(currentYumRepo.failovermethod==null?null:currentYumRepo.failovermethod.toString(), repoOverridesMapOfMaps.get(repoId).get(name), "The repo-override for repoId='"+repoId+"' name='"+name+"'.");} else {Assert.assertEquals(currentYumRepo.failovermethod==null?null:currentYumRepo.failovermethod.toString(), originalYumRepo.failovermethod==null?null:originalYumRepo.failovermethod.toString(), "There should NOT be a repo-override for repoId='"+repoId+"' name='"+name+"'.");}
			name = "keepalive";				if (repoOverridesMapOfMaps.containsKey(repoId) && repoOverridesMapOfMaps.get(repoId).containsKey(name)) {Assert.assertEquals(currentYumRepo.keepalive==null?null:currentYumRepo.keepalive.toString(), repoOverridesMapOfMaps.get(repoId).get(name), "The repo-override for repoId='"+repoId+"' name='"+name+"'.");} else {Assert.assertEquals(currentYumRepo.keepalive==null?null:currentYumRepo.keepalive.toString(), originalYumRepo.keepalive==null?null:originalYumRepo.keepalive.toString(), "There should NOT be a repo-override for repoId='"+repoId+"' name='"+name+"'.");}
			name = "timeout";				if (repoOverridesMapOfMaps.containsKey(repoId) && repoOverridesMapOfMaps.get(repoId).containsKey(name)) {Assert.assertEquals(currentYumRepo.timeout==null?null:currentYumRepo.timeout.toString(), repoOverridesMapOfMaps.get(repoId).get(name), "The repo-override for repoId='"+repoId+"' name='"+name+"'.");} else {Assert.assertEquals(currentYumRepo.timeout==null?null:currentYumRepo.timeout.toString(), originalYumRepo.timeout==null?null:originalYumRepo.timeout.toString(), "There should NOT be a repo-override for repoId='"+repoId+"' name='"+name+"'.");}
			name = "http_caching";			if (repoOverridesMapOfMaps.containsKey(repoId) && repoOverridesMapOfMaps.get(repoId).containsKey(name)) {Assert.assertEquals(currentYumRepo.http_caching==null?null:currentYumRepo.http_caching.toString(), repoOverridesMapOfMaps.get(repoId).get(name), "The repo-override for repoId='"+repoId+"' name='"+name+"'.");} else {Assert.assertEquals(currentYumRepo.http_caching==null?null:currentYumRepo.http_caching.toString(), originalYumRepo.http_caching==null?null:originalYumRepo.http_caching.toString(), "There should NOT be a repo-override for repoId='"+repoId+"' name='"+name+"'.");}
			name = "retries";				if (repoOverridesMapOfMaps.containsKey(repoId) && repoOverridesMapOfMaps.get(repoId).containsKey(name)) {Assert.assertEquals(currentYumRepo.retries==null?null:currentYumRepo.retries.toString(), repoOverridesMapOfMaps.get(repoId).get(name), "The repo-override for repoId='"+repoId+"' name='"+name+"'.");} else {Assert.assertEquals(currentYumRepo.retries==null?null:currentYumRepo.retries.toString(), originalYumRepo.retries==null?null:originalYumRepo.retries.toString(), "There should NOT be a repo-override for repoId='"+repoId+"' name='"+name+"'.");}
			name = "throttle";				if (repoOverridesMapOfMaps.containsKey(repoId) && repoOverridesMapOfMaps.get(repoId).containsKey(name)) {Assert.assertEquals(currentYumRepo.throttle==null?null:currentYumRepo.throttle.toString(), repoOverridesMapOfMaps.get(repoId).get(name), "The repo-override for repoId='"+repoId+"' name='"+name+"'.");} else {Assert.assertEquals(currentYumRepo.throttle==null?null:currentYumRepo.throttle.toString(), originalYumRepo.throttle==null?null:originalYumRepo.throttle.toString(), "There should NOT be a repo-override for repoId='"+repoId+"' name='"+name+"'.");}
			name = "bandwidth";				if (repoOverridesMapOfMaps.containsKey(repoId) && repoOverridesMapOfMaps.get(repoId).containsKey(name)) {Assert.assertEquals(currentYumRepo.bandwidth==null?null:currentYumRepo.bandwidth.toString(), repoOverridesMapOfMaps.get(repoId).get(name), "The repo-override for repoId='"+repoId+"' name='"+name+"'.");} else {Assert.assertEquals(currentYumRepo.bandwidth==null?null:currentYumRepo.bandwidth.toString(), originalYumRepo.bandwidth==null?null:originalYumRepo.bandwidth.toString(), "There should NOT be a repo-override for repoId='"+repoId+"' name='"+name+"'.");}
			name = "mirrorlist_expire";		if (repoOverridesMapOfMaps.containsKey(repoId) && repoOverridesMapOfMaps.get(repoId).containsKey(name)) {Assert.assertEquals(currentYumRepo.mirrorlist_expire==null?null:currentYumRepo.mirrorlist_expire.toString(), repoOverridesMapOfMaps.get(repoId).get(name), "The repo-override for repoId='"+repoId+"' name='"+name+"'.");} else {Assert.assertEquals(currentYumRepo.mirrorlist_expire==null?null:currentYumRepo.mirrorlist_expire.toString(), originalYumRepo.mirrorlist_expire==null?null:originalYumRepo.mirrorlist_expire.toString(), "There should NOT be a repo-override for repoId='"+repoId+"' name='"+name+"'.");}
			name = "proxy";					if (repoOverridesMapOfMaps.containsKey(repoId) && repoOverridesMapOfMaps.get(repoId).containsKey(name)) {Assert.assertEquals(currentYumRepo.proxy==null?null:currentYumRepo.proxy.toString(), repoOverridesMapOfMaps.get(repoId).get(name), "The repo-override for repoId='"+repoId+"' name='"+name+"'.");} else {Assert.assertEquals(currentYumRepo.proxy==null?null:currentYumRepo.proxy.toString(), originalYumRepo.proxy==null?null:originalYumRepo.proxy.toString(), "There should NOT be a repo-override for repoId='"+repoId+"' name='"+name+"'.");}
			name = "proxy_username";		if (repoOverridesMapOfMaps.containsKey(repoId) && repoOverridesMapOfMaps.get(repoId).containsKey(name)) {Assert.assertEquals(currentYumRepo.proxy_username==null?null:currentYumRepo.proxy_username.toString(), repoOverridesMapOfMaps.get(repoId).get(name), "The repo-override for repoId='"+repoId+"' name='"+name+"'.");} else {Assert.assertEquals(currentYumRepo.proxy_username==null?null:currentYumRepo.proxy_username.toString(), originalYumRepo.proxy_username==null?null:originalYumRepo.proxy_username.toString(), "There should NOT be a repo-override for repoId='"+repoId+"' name='"+name+"'.");}
			name = "proxy_password";		if (repoOverridesMapOfMaps.containsKey(repoId) && repoOverridesMapOfMaps.get(repoId).containsKey(name)) {Assert.assertEquals(currentYumRepo.proxy_password==null?null:currentYumRepo.proxy_password.toString(), repoOverridesMapOfMaps.get(repoId).get(name), "The repo-override for repoId='"+repoId+"' name='"+name+"'.");} else {Assert.assertEquals(currentYumRepo.proxy_password==null?null:currentYumRepo.proxy_password.toString(), originalYumRepo.proxy_password==null?null:originalYumRepo.proxy_password.toString(), "There should NOT be a repo-override for repoId='"+repoId+"' name='"+name+"'.");}
			name = "username";				if (repoOverridesMapOfMaps.containsKey(repoId) && repoOverridesMapOfMaps.get(repoId).containsKey(name)) {Assert.assertEquals(currentYumRepo.username==null?null:currentYumRepo.username.toString(), repoOverridesMapOfMaps.get(repoId).get(name), "The repo-override for repoId='"+repoId+"' name='"+name+"'.");} else {Assert.assertEquals(currentYumRepo.username==null?null:currentYumRepo.username.toString(), originalYumRepo.username==null?null:originalYumRepo.username.toString(), "There should NOT be a repo-override for repoId='"+repoId+"' name='"+name+"'.");}
			name = "password";				if (repoOverridesMapOfMaps.containsKey(repoId) && repoOverridesMapOfMaps.get(repoId).containsKey(name)) {Assert.assertEquals(currentYumRepo.password==null?null:currentYumRepo.password.toString(), repoOverridesMapOfMaps.get(repoId).get(name), "The repo-override for repoId='"+repoId+"' name='"+name+"'.");} else {Assert.assertEquals(currentYumRepo.password==null?null:currentYumRepo.password.toString(), originalYumRepo.password==null?null:originalYumRepo.password.toString(), "There should NOT be a repo-override for repoId='"+repoId+"' name='"+name+"'.");}
			name = "cost";					if (repoOverridesMapOfMaps.containsKey(repoId) && repoOverridesMapOfMaps.get(repoId).containsKey(name)) {Assert.assertEquals(currentYumRepo.cost==null?null:currentYumRepo.cost.toString(), repoOverridesMapOfMaps.get(repoId).get(name), "The repo-override for repoId='"+repoId+"' name='"+name+"'.");} else {Assert.assertEquals(currentYumRepo.cost==null?null:currentYumRepo.cost.toString(), originalYumRepo.cost==null?null:originalYumRepo.cost.toString(), "There should NOT be a repo-override for repoId='"+repoId+"' name='"+name+"'.");}
			name = "skip_if_unavailable";	if (repoOverridesMapOfMaps.containsKey(repoId) && repoOverridesMapOfMaps.get(repoId).containsKey(name)) {Assert.assertEquals(currentYumRepo.skip_if_unavailable==null?null:currentYumRepo.skip_if_unavailable.toString(), repoOverridesMapOfMaps.get(repoId).get(name), "The repo-override for repoId='"+repoId+"' name='"+name+"'.");} else {Assert.assertEquals(currentYumRepo.skip_if_unavailable==null?null:currentYumRepo.skip_if_unavailable.toString(), originalYumRepo.skip_if_unavailable==null?null:originalYumRepo.skip_if_unavailable.toString(), "There should NOT be a repo-override for repoId='"+repoId+"' name='"+name+"'.");}
			name = "ui_repoid_vars";		if (repoOverridesMapOfMaps.containsKey(repoId) && repoOverridesMapOfMaps.get(repoId).containsKey(name)) {Assert.assertEquals(currentYumRepo.ui_repoid_vars==null?null:currentYumRepo.ui_repoid_vars.toString(), repoOverridesMapOfMaps.get(repoId).get(name), "The repo-override for repoId='"+repoId+"' name='"+name+"'.");} else {Assert.assertEquals(currentYumRepo.ui_repoid_vars==null?null:currentYumRepo.ui_repoid_vars.toString(), originalYumRepo.ui_repoid_vars==null?null:originalYumRepo.ui_repoid_vars.toString(), "There should NOT be a repo-override for repoId='"+repoId+"' name='"+name+"'.");}
			name = "priority";				if (repoOverridesMapOfMaps.containsKey(repoId) && repoOverridesMapOfMaps.get(repoId).containsKey(name)) {Assert.assertEquals(currentYumRepo.priority==null?null:currentYumRepo.priority.toString(), repoOverridesMapOfMaps.get(repoId).get(name), "The repo-override for repoId='"+repoId+"' name='"+name+"'.");} else {Assert.assertEquals(currentYumRepo.priority==null?null:currentYumRepo.priority.toString(), originalYumRepo.priority==null?null:originalYumRepo.priority.toString(), "There should NOT be a repo-override for repoId='"+repoId+"' name='"+name+"'.");}
			
			name = "sslclientcert";			if (repoOverridesMapOfMaps.containsKey(repoId) && repoOverridesMapOfMaps.get(repoId).containsKey(name)) {Assert.assertEquals(currentYumRepo.sslclientcert==null?null:currentYumRepo.sslclientcert.toString(), repoOverridesMapOfMaps.get(repoId).get(name), "The repo-override for repoId='"+repoId+"' name='"+name+"'.");} else {if (includeVerificationOfNames_sslclient) Assert.assertEquals(currentYumRepo.sslclientcert==null?null:currentYumRepo.sslclientcert.toString(), originalYumRepo.sslclientcert==null?null:originalYumRepo.sslclientcert.toString(), "There should NOT be a repo-override for repoId='"+repoId+"' name='"+name+"'.");}
			name = "sslclientkey";			if (repoOverridesMapOfMaps.containsKey(repoId) && repoOverridesMapOfMaps.get(repoId).containsKey(name)) {Assert.assertEquals(currentYumRepo.sslclientkey==null?null:currentYumRepo.sslclientkey.toString(), repoOverridesMapOfMaps.get(repoId).get(name), "The repo-override for repoId='"+repoId+"' name='"+name+"'.");} else {if (includeVerificationOfNames_sslclient) Assert.assertEquals(currentYumRepo.sslclientkey==null?null:currentYumRepo.sslclientkey.toString(), originalYumRepo.sslclientkey==null?null:originalYumRepo.sslclientkey.toString(), "There should NOT be a repo-override for repoId='"+repoId+"' name='"+name+"'.");}
		}
	}
	
	
	// Data Providers ***********************************************************************
	
}

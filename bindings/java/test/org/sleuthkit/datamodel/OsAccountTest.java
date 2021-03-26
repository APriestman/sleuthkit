/*
 * Sleuth Kit Data Model
 *
 * Copyright 2021 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.datamodel;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * Tests OsAccount apis.
 *
 */
public class OsAccountTest {
	
	private static final Logger LOGGER = Logger.getLogger(OsAccountTest.class.getName());

	private static SleuthkitCase caseDB;

	private final static String TEST_DB = "OsAccountApiTest.db";


	private static String dbPath = null;
	
	private static Image image;
	
	private static FileSystem fs = null;

	public OsAccountTest (){

	}
	
	@BeforeClass
	public static void setUpClass() {
		String tempDirPath = System.getProperty("java.io.tmpdir");
		try {
			dbPath = Paths.get(tempDirPath, TEST_DB).toString();

			// Delete the DB file, in case
			java.io.File dbFile = new java.io.File(dbPath);
			dbFile.delete();
			if (dbFile.getParentFile() != null) {
				dbFile.getParentFile().mkdirs();
			}

			// Create new case db
			caseDB = SleuthkitCase.newCase(dbPath);

			SleuthkitCase.CaseDbTransaction trans = caseDB.beginTransaction();

			image = caseDB.addImage(TskData.TSK_IMG_TYPE_ENUM.TSK_IMG_TYPE_DETECT, 512, 1024, "", Collections.emptyList(), "America/NewYork", null, null, null, "first", trans);

			fs = caseDB.addFileSystem(image.getId(), 0, TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_RAW, 0, 0, 0, 0, 0, "", trans);

			trans.commit();


			System.out.println("OsAccount Test DB created at: " + dbPath);
		} catch (TskCoreException ex) {
			LOGGER.log(Level.SEVERE, "Failed to create new case", ex);
		}
	}


	@AfterClass
	public static void tearDownClass() {

	}
	
	@Before
	public void setUp() {
	}

	@After
	public void tearDown() {
	}

	@Test 
	public void hostTests() throws TskCoreException {
		
		try {
			String HOSTNAME1 = "host11";
			
			// Test: create a host
			Host host1 = caseDB.getHostManager().createHost(HOSTNAME1);
			assertEquals(host1.getName().equalsIgnoreCase(HOSTNAME1), true );
			
			
			// Test: get a host we just created.
			Optional<Host> optionalhost1 = caseDB.getHostManager().getHost(HOSTNAME1);
			assertEquals(optionalhost1.isPresent(), true );
			
			
			String HOSTNAME2 = "host22";
			
			// Get a host not yet created
			Optional<Host> optionalhost2 = caseDB.getHostManager().getHost(HOSTNAME2);
			assertEquals(optionalhost2.isPresent(), false );
			
			
			// now create the second host
			Host host2 = caseDB.getHostManager().createHost(HOSTNAME2);
			assertEquals(host2.getName().equalsIgnoreCase(HOSTNAME2), true );
			
			
			// now get it again, should be found this time
			optionalhost2 = caseDB.getHostManager().getHost(HOSTNAME2);
			assertEquals(optionalhost2.isPresent(), true);
			
			// create a host that already exists - should transperently return the existting host.
			Host host2_2 = caseDB.getHostManager().createHost(HOSTNAME2);
			assertEquals(host2_2.getName().equalsIgnoreCase(HOSTNAME2), true );
			
		}
		catch(Exception ex) {
			
		}
	
	}
	
	@Test 
	public void personTests() throws TskCoreException {
		String personName1 = "John Doe";
		String personName2 = "Jane Doe";
		
		org.sleuthkit.datamodel.PersonManager pm = caseDB.getPersonManager();
		
		Person p1 = pm.createPerson(personName1);
		assertEquals(personName1.equals(p1.getName()), true);
		
		Optional<Person> p1opt = pm.getPerson(personName1.toLowerCase());
		assertEquals(p1opt.isPresent(), true);
		
		p1.setName(personName2);
		assertEquals(personName2.equals(p1.getName()), true);
		
		pm.updatePerson(p1);
		Optional<Person> p2opt = pm.getPerson(personName2.toUpperCase());
		assertEquals(p2opt.isPresent(), true);
		
		pm.deletePerson(p1.getName());
		p2opt = pm.getPerson(personName2);
		assertEquals(p2opt.isPresent(), false);
	}
		
	@Test
	public void mergeHostTests() throws TskCoreException, OsAccountManager.NotUserSIDException {
		
		// Host 1 will be merged into Host 2
		String host1Name = "host1forHostMergeTest";
		String host2Name = "host2forHostMergeTest";
		Host host1 = caseDB.getHostManager().createHost(host1Name);
		Host host2 = caseDB.getHostManager().createHost(host2Name);
		
		// Data source is originally associated with host1
		org.sleuthkit.datamodel.SleuthkitCase.CaseDbTransaction trans = caseDB.beginTransaction();
		DataSource ds = caseDB.addLocalFilesDataSource("devId", "pathToFiles", "EST", host1, trans);
		trans.commit();
        
		String sid3 = "S-1-5-27-777777777-854245398-1060284298-7777";
		String sid4 = "S-1-5-27-788888888-854245398-1060284298-8888";
		String sid5 = "S-1-5-27-799999999-854245398-1060284298-9999";
		String sid6 = "S-1-5-27-711111111-854245398-1060284298-1111";
		String sid7 = "S-1-5-27-733333333-854245398-1060284298-3333";
		String sid8 = "S-1-5-27-744444444-854245398-1060284298-4444";
		
		String realmName1 = "hostMergeRealm1";
		String realmName2 = "hostMergeRealm2";
		String realmName4 = "hostMergeRealm4";
		String realmName5 = "hostMergeRealm5";
		String realmName6 = "hostMergeRealm6";
		String realmName7 = "hostMergeRealm7";
		String realmName8 = "hostMergeRealm8";
		
		String realm8AcctName = "hostMergeUniqueRealm8Account";
		String realm10AcctName = "hostMergeUniqueRealm10Account";
		
		// Save the created realms/accounts so we can query them later by object ID (the objects themselves will end up out-of-date)
		OsAccountRealmManager realmManager = caseDB.getOsAccountRealmManager();
		
		// 1 - Should get moved
		OsAccountRealm realm1 = realmManager.createWindowsRealm(null, realmName1, host1, OsAccountRealm.RealmScope.LOCAL);
		
		// 2 - Should be merged into 5
		OsAccountRealm realm2 = realmManager.createWindowsRealm(null, realmName2, host1, OsAccountRealm.RealmScope.LOCAL);
		
		// 3 - Should be merged into 5
		OsAccountRealm realm3 = realmManager.createWindowsRealm(sid3, null, host1, OsAccountRealm.RealmScope.LOCAL); 
		
		// 4 - Should get moved - not merged into 6 since addrs are different
		OsAccountRealm realm4 = realmManager.createWindowsRealm(sid4, realmName4, host1, OsAccountRealm.RealmScope.LOCAL); 

		// 5 - 2 and 3 should get merged in
		OsAccountRealm realm5 = realmManager.createWindowsRealm(sid3, realmName2, host2, OsAccountRealm.RealmScope.LOCAL);

		// 6 - Should not get merged with 4
		OsAccountRealm realm6 = realmManager.createWindowsRealm(sid5, realmName4, host2, OsAccountRealm.RealmScope.LOCAL);

		// 7 - Should be unchanged
		OsAccountRealm realm7 = realmManager.createWindowsRealm(null, realmName5, host2, OsAccountRealm.RealmScope.LOCAL);

		// 8, 9, 10 - 8 should be merged into 9 and then 10 should be merged into 9
		OsAccountRealm realm8 = realmManager.createWindowsRealm(null, realmName6, host2, OsAccountRealm.RealmScope.LOCAL); 
		OsAccount realm8acct = caseDB.getOsAccountManager().createWindowsAccount(null, realm8AcctName, realmName6, host2, OsAccountRealm.RealmScope.LOCAL);
		OsAccountRealm realm9 = realmManager.createWindowsRealm(sid6, null, host2, OsAccountRealm.RealmScope.LOCAL);
		OsAccountRealm realm10 = realmManager.createWindowsRealm(sid6, realmName6, host1, OsAccountRealm.RealmScope.LOCAL);
		OsAccount realm10acct = caseDB.getOsAccountManager().createWindowsAccount(null, realm10AcctName, realmName6, host1, OsAccountRealm.RealmScope.LOCAL);

		// 11, 12 - 11 should get merged into 12, adding the addr "sid8" to 12
		OsAccountRealm realm11 = realmManager.createWindowsRealm(sid8, realmName7, host1, OsAccountRealm.RealmScope.LOCAL);
		OsAccountRealm realm12 = realmManager.createWindowsRealm(null, realmName7, host2, OsAccountRealm.RealmScope.LOCAL);

		// 13,14 - 13 should get merged into 14, name for 14 should not change
		OsAccountRealm realm13 = realmManager.createWindowsRealm(sid7, "notRealm8", host1, OsAccountRealm.RealmScope.LOCAL);
		OsAccountRealm realm14 = realmManager.createWindowsRealm(sid7, realmName8, host2, OsAccountRealm.RealmScope.LOCAL);
		
		// Do the merge
		caseDB.getHostManager().mergeHosts(host1, host2);
		
		// Test the realms
		try (org.sleuthkit.datamodel.SleuthkitCase.CaseDbConnection connection = caseDB.getConnection()) {
			// Expected change: host is now host2
			testUpdatedRealm(realm1, OsAccountRealm.RealmDbStatus.ACTIVE, realm1.getRealmAddr(), realm1.getRealmNames(), Optional.of(host2), connection);
			
			// Expected change: should be marked as merged
			testUpdatedRealm(realm2, OsAccountRealm.RealmDbStatus.MERGED, null, null, null, connection);
			
			// Expected change: should be marked as merged
			testUpdatedRealm(realm3, OsAccountRealm.RealmDbStatus.MERGED, null, null, null, connection);
			
			// Expected change: should still be active and be moved to host2
			testUpdatedRealm(realm4, OsAccountRealm.RealmDbStatus.ACTIVE, realm4.getRealmAddr(), realm4.getRealmNames(), Optional.of(host2), connection);
			
			// Expected change: nothing
			testUpdatedRealm(realm7, realm7.getDbStatus(), realm7.getRealmAddr(), realm7.getRealmNames(), realm7.getScopeHost(), connection);
			
			// Expected change: should be marked as merged
			testUpdatedRealm(realm8, OsAccountRealm.RealmDbStatus.MERGED, null, null, null, connection);
			
			// Expected change: should have gained the name of realm 8
			testUpdatedRealm(realm9, OsAccountRealm.RealmDbStatus.ACTIVE, realm9.getRealmAddr(), realm8.getRealmNames(), realm9.getScopeHost(), connection);
			
			// Expected change: should have gained the addr of realm 11
			testUpdatedRealm(realm12, OsAccountRealm.RealmDbStatus.ACTIVE, realm11.getRealmAddr(), realm12.getRealmNames(), Optional.of(host2), connection);
			
			// "notRealm8" should not return any hits for either host (realm13 is marked as merged and the name was not copied to realm14)
			Optional<OsAccountRealm> optRealm = realmManager.getRealmByName("notRealm8", host1, connection);
			assertEquals(optRealm.isPresent(), false);
			optRealm = realmManager.getRealmByName("notRealm8", host2, connection);
			assertEquals(optRealm.isPresent(), false);
			
			// The realm8 and realm10 accounts should both be in realm9 now
			OsAccount acct = caseDB.getOsAccountManager().getOsAccount(realm8acct.getId(), connection);
			assertEquals(acct.getRealmId() == realm9.getId(), true);
			acct = caseDB.getOsAccountManager().getOsAccount(realm10acct.getId(), connection);
			assertEquals(acct.getRealmId() == realm9.getId(), true);
		}
			
		// The data source should now reference host2
		Host host = caseDB.getHostManager().getHost(ds);
		assertEquals(host.getId() == host2.getId(), true);

		// We should get no results on a search for host1
		Optional<Host> optHost = caseDB.getHostManager().getHost(host1Name);
		assertEquals(optHost.isPresent(), false);
		
		// If we attempt to make a new host with the same name host1 had, we should get a new object Id
		host = caseDB.getHostManager().createHost(host1Name);
		assertEquals(host.getId() != host1.getId(), true);
	}
	
	/**
	 * Retrieve the new version of a realm from the database and compare with expected values.
	 * Addr, name, and host can be passed in as null to skip comparison.
	 */
	private void testUpdatedRealm(OsAccountRealm origRealm, OsAccountRealm.RealmDbStatus expectedStatus, Optional<String> expectedAddr,
			List<String> expectedNames, Optional<Host> expectedHost, org.sleuthkit.datamodel.SleuthkitCase.CaseDbConnection connection) throws TskCoreException {
		
		OsAccountRealm realm = caseDB.getOsAccountRealmManager().getRealmById(origRealm.getId(), connection);
		assertEquals(realm.getDbStatus().equals(expectedStatus), true);	
		if (expectedAddr != null) {
			assertEquals(realm.getRealmAddr().equals(expectedAddr), true);
		}
		if(expectedNames != null && !expectedNames.isEmpty()){
			assertEquals(realm.getRealmNames().get(0).equals(expectedNames.get(0)), true);
		}
		if (expectedHost != null) {
			assertEquals(realm.getScopeHost().equals(expectedHost), true);
		}
	}
	
	@Test 
	public void mergeRealmsTests() throws TskCoreException, OsAccountManager.NotUserSIDException {
		Host host = caseDB.getHostManager().createHost("mergeTestHost");
		
		String destRealmName = "mergeTestDestRealm";
		String srcRealmName = "mergeTestSourceRealm";
		
		String sid1 = "S-1-5-21-222222222-222222222-1060284298-2222";
        String sid2 = "S-1-5-21-555555555-555555555-1060284298-5555";   
		
		String uniqueRealm2Name = "uniqueRealm2Account";
		String matchingName = "matchingNameAccount";
		String fullName1 = "FullName1";
		long creationTime1 = 555;
		
		OsAccountRealm srcRealm = caseDB.getOsAccountRealmManager().createWindowsRealm(null, srcRealmName, host, OsAccountRealm.RealmScope.LOCAL);
		OsAccountRealm destRealm = caseDB.getOsAccountRealmManager().createWindowsRealm(null, destRealmName, host, OsAccountRealm.RealmScope.LOCAL);
		
		OsAccount account1 = caseDB.getOsAccountManager().createWindowsAccount(null, "uniqueRealm1Account", destRealmName, host, OsAccountRealm.RealmScope.LOCAL);
		OsAccount account2 = caseDB.getOsAccountManager().createWindowsAccount(null, matchingName, destRealmName, host, OsAccountRealm.RealmScope.LOCAL);
		OsAccount account3 = caseDB.getOsAccountManager().createWindowsAccount(null, uniqueRealm2Name, srcRealmName, host, OsAccountRealm.RealmScope.LOCAL);
		OsAccount account4 = caseDB.getOsAccountManager().createWindowsAccount(null, matchingName, srcRealmName, host, OsAccountRealm.RealmScope.LOCAL);
		account4.setFullName(fullName1);
		account4.setCreationTime(creationTime1);
		caseDB.getOsAccountManager().updateAccount(account4);
		OsAccount account5 = caseDB.getOsAccountManager().createWindowsAccount(sid1, null, destRealmName, host, OsAccountRealm.RealmScope.LOCAL);
		OsAccount account6 = caseDB.getOsAccountManager().createWindowsAccount(sid1, null, srcRealmName, host, OsAccountRealm.RealmScope.LOCAL);  
		OsAccount account7 = caseDB.getOsAccountManager().createWindowsAccount(sid2, null, destRealmName, host, OsAccountRealm.RealmScope.LOCAL);
		OsAccount account8 = caseDB.getOsAccountManager().createWindowsAccount(null, "nameForCombining", destRealmName, host, OsAccountRealm.RealmScope.LOCAL);
		OsAccount account9 = caseDB.getOsAccountManager().createWindowsAccount(sid2, "nameForCombining", srcRealmName, host, OsAccountRealm.RealmScope.LOCAL);
		
		// Test that we can currently get the source realm by name
		Optional<OsAccountRealm> optRealm = caseDB.getOsAccountRealmManager().getWindowsRealm(null, srcRealmName, host);
		assertEquals(optRealm.isPresent(), true);
		
		// Test that there are currently two account associated with sid1
		List<OsAccount> accounts = caseDB.getOsAccountManager().getAccounts().stream().filter(p -> p.getUniqueIdWithinRealm().isPresent() && p.getUniqueIdWithinRealm().get().equals(sid1)).collect(Collectors.toList());
		assertEquals(accounts.size() == 2, true);
		
		// Expected results of the merge:
		// - account 4 will be merged into account 2 (and extra fields should be copied)
		// - account 6 will be merged into account 5
		// - account 8 will be merged into account 7 (due to account 9 containing matches for both)
		// - account 9 will be merged into account 7
		caseDB.getOsAccountRealmManager().mergeRealms(srcRealm, destRealm);
		
		// Test that the source realm is no longer returned by a search by name
		optRealm = caseDB.getOsAccountRealmManager().getWindowsRealm(null, srcRealmName, host);
		assertEquals(optRealm.isPresent(), false);
		
		// Test that there is now only one account associated with sid1
		accounts = caseDB.getOsAccountManager().getAccounts().stream().filter(p -> p.getUniqueIdWithinRealm().isPresent() && p.getUniqueIdWithinRealm().get().equals(sid1)).collect(Collectors.toList());
		assertEquals(accounts.size() == 1, true);
		
		// Test that account 3 got moved into the destination realm
		Optional<OsAccount> optAcct = caseDB.getOsAccountManager().getOsAccountByLoginName(uniqueRealm2Name, destRealm);
		assertEquals(optAcct.isPresent(), true);
		
		// Test that data from account 4 was merged into account 2
		optAcct = caseDB.getOsAccountManager().getOsAccountByLoginName(matchingName, destRealm);
		assertEquals(optAcct.isPresent(), true);
		if (optAcct.isPresent()) {
			assertEquals(optAcct.get().getCreationTime().isPresent() &&  optAcct.get().getCreationTime().get() == creationTime1, true);
			assertEquals(optAcct.get().getFullName().isPresent() && fullName1.equalsIgnoreCase(optAcct.get().getFullName().get()), true);
		}
	}
	
	@Test 
	public void hostAddressTests() throws TskCoreException {
		
		
		// lets add a file 
		long dataSourceObjectId = fs.getDataSource().getId();
		
		SleuthkitCase.CaseDbTransaction trans = caseDB.beginTransaction();
		
		// Add a root folder
		FsContent _root = caseDB.addFileSystemFile(dataSourceObjectId, fs.getId(), "", 0, 0,
				TskData.TSK_FS_ATTR_TYPE_ENUM.TSK_FS_ATTR_TYPE_DEFAULT, 0, TskData.TSK_FS_NAME_FLAG_ENUM.ALLOC,
				(short) 0, 200, 0, 0, 0, 0, null, null, null, false, fs, null, null, Collections.emptyList(), trans);

		// Add a dir - no attributes 
		FsContent _windows = caseDB.addFileSystemFile(dataSourceObjectId, fs.getId(), "Windows", 0, 0,
				TskData.TSK_FS_ATTR_TYPE_ENUM.TSK_FS_ATTR_TYPE_DEFAULT, 0, TskData.TSK_FS_NAME_FLAG_ENUM.ALLOC,
				(short) 0, 200, 0, 0, 0, 0, null, null, null, false, _root, "S-1-5-80-956008885-3418522649-1831038044-1853292631-227147846", null, Collections.emptyList(), trans);

		// add another no attribute file to same folder
		FsContent _abcTextFile = caseDB.addFileSystemFile(dataSourceObjectId, fs.getId(), "abc.txt", 0, 0,
					TskData.TSK_FS_ATTR_TYPE_ENUM.TSK_FS_ATTR_TYPE_DEFAULT, 0, TskData.TSK_FS_NAME_FLAG_ENUM.ALLOC,
					(short) 0, 200, 0, 0, 0, 0, null, null, "Text/Plain", true, _windows, null, null, Collections.emptyList(), trans);
		
		trans.commit();
			
		
		
		String ipv4Str = "11.22.33.44";
		String ipv6Str = "2001:0db8:85a3:0000:0000:8a2e:0370:6666";
		String hostnameStr = "basis.com";
		
		// Test creation
		HostAddress ipv4addr = caseDB.getHostAddressManager().createHostAddress(HostAddress.HostAddressType.IPV4, ipv4Str);
		assertEquals(ipv4addr.getAddress().equalsIgnoreCase(ipv4Str), true);
		
		HostAddress addr2 = caseDB.getHostAddressManager().createHostAddress(HostAddress.HostAddressType.DNS_AUTO, ipv6Str);
		assertEquals(addr2.getAddress().equalsIgnoreCase(ipv6Str), true);
		assertEquals(HostAddress.HostAddressType.IPV6.equals(addr2.getAddressType()), true);
		
		HostAddress hostAddr = caseDB.getHostAddressManager().createHostAddress(HostAddress.HostAddressType.DNS_AUTO, hostnameStr);
		assertEquals(hostAddr.getAddress().equalsIgnoreCase(hostnameStr), true);
		assertEquals(HostAddress.HostAddressType.HOSTNAME.equals(hostAddr.getAddressType()), true);
		
		// Test get
		Optional<HostAddress> addr4opt = caseDB.getHostAddressManager().getHostAddress(HostAddress.HostAddressType.IPV4, ipv4Str);
		assertEquals(addr4opt.isPresent(), true);
		
		// Test host map
		Host host = caseDB.getHostManager().createHost("TestHostAddress");
		
		trans = caseDB.beginTransaction();
		DataSource ds = caseDB.addLocalFilesDataSource("devId", "pathToFiles", "EST", null, trans);
		trans.commit();
		
		caseDB.getHostAddressManager().assignHostToAddress(host, ipv4addr, (long) 0, ds);
		List<HostAddress> hostAddrs = caseDB.getHostAddressManager().getHostAddressesAssignedTo(host);
		assertEquals(hostAddrs.size() == 1, true);
		
		// Test IP mapping
		caseDB.getHostAddressManager().addHostNameAndIpMapping(hostAddr, ipv4addr, (long) 0, ds);
		List<HostAddress> ipForHostSet = caseDB.getHostAddressManager().getIpAddress(hostAddr.getAddress());
		assertEquals(ipForHostSet.size() == 1, true);
		List<HostAddress> hostForIpSet = caseDB.getHostAddressManager().getHostNameByIp(ipv4addr.getAddress());
		assertEquals(hostForIpSet.size() == 1, true);
		
		
		// add address usage
		caseDB.getHostAddressManager().addUsage(_abcTextFile, ipv4addr);
		caseDB.getHostAddressManager().addUsage(_abcTextFile, addr2);
		caseDB.getHostAddressManager().addUsage(_abcTextFile, hostAddr);
		
		//test get addressUsed methods
		List<HostAddress> addrUsedByAbc = caseDB.getHostAddressManager().getHostAddressesUsedByContent(_abcTextFile);
		assertEquals(addrUsedByAbc.size() == 3, true);
		
		List<HostAddress> addrUsedByRoot = caseDB.getHostAddressManager().getHostAddressesUsedByContent(_root);
		assertEquals(addrUsedByRoot.isEmpty(), true);
		
		List<HostAddress> addrUsedOnDataSource = caseDB.getHostAddressManager().getHostAddressesUsedOnDataSource(_root.getDataSource());
		assertEquals(addrUsedOnDataSource.size() == 3, true);
		
	}
	
	@Test
	public void osAccountRealmTests() throws TskCoreException, OsAccountManager.NotUserSIDException {
		
		try {
		// TEST: create a DOMAIN realm 
		
		String HOSTNAME1 = "host1";
		Host host1 = caseDB.getHostManager().createHost(HOSTNAME1);
			
		String realmName1 = "basis";
		String realmSID1 =  "S-1-5-21-1111111111-2222222222-3333333333";
		String realmAddr1 = "S-1-5-21-1111111111-2222222222";	
		
		OsAccountRealm domainRealm1 = caseDB.getOsAccountRealmManager().createWindowsRealm(realmSID1, realmName1, host1, OsAccountRealm.RealmScope.DOMAIN);
		
		assertEquals(domainRealm1.getRealmNames().get(0).equalsIgnoreCase(realmName1), true );
		assertEquals(domainRealm1.getScopeConfidence(), OsAccountRealm.ScopeConfidence.KNOWN);
		assertEquals(domainRealm1.getRealmAddr().orElse(null), realmAddr1); 
		
	
		//TEST: create a new LOCAL realm with a single host
		String realmSID2 = "S-1-5-18-2033736216-1234567890-5432109876";
		String realmAddr2 = "S-1-5-18-2033736216-1234567890";	
		String realmName2 = "win-raman-abcd";
		String hostName2 = "host2";
		
		Host host2 = caseDB.getHostManager().createHost(hostName2);
		OsAccountRealm localRealm2 = caseDB.getOsAccountRealmManager().createWindowsRealm(realmSID2, null, host2, OsAccountRealm.RealmScope.LOCAL);
		assertEquals(localRealm2.getRealmAddr().orElse("").equalsIgnoreCase(realmAddr2), true );
		assertEquals(localRealm2.getScopeHost().orElse(null).getName().equalsIgnoreCase(hostName2), true);
		
		// update the a realm name on a existing realm.
		localRealm2.addRealmName(realmName2);
		OsAccountRealm updatedRealm2 = caseDB.getOsAccountRealmManager().updateRealm(localRealm2);
		assertEquals(updatedRealm2.getRealmAddr().orElse("").equalsIgnoreCase(realmAddr2), true );
		assertEquals(updatedRealm2.getRealmNames().get(0).equalsIgnoreCase(realmName2), true );
		
		
		
		// TEST get an existing DOMAIN realm - new SID  on a new host but same sub authority as previously created realm
		String realmSID3 = realmAddr1 + "-88888888";
		
		String hostName3 = "host3";
		Host host3 = caseDB.getHostManager().createHost(hostName3);
		
		// expect this to return realm1
		Optional<OsAccountRealm> existingRealm3 = caseDB.getOsAccountRealmManager().getWindowsRealm(realmSID3, null, host3); 
		assertEquals(existingRealm3.isPresent(), true);
		assertEquals(existingRealm3.get().getRealmAddr().orElse("").equalsIgnoreCase(realmAddr1), true );
		assertEquals(existingRealm3.get().getRealmNames().get(0).equalsIgnoreCase(realmName1), true );
		
		
		// TEST get a existing LOCAL realm by addr, BUT on a new referring host.
		String hostName4 = "host4";
		Host host4 = caseDB.getHostManager().createHost(hostName4);
		
		// Although the realm exists with this addr, it should  NOT match since the host is different from what the realm was created with
		Optional<OsAccountRealm> realm4 = caseDB.getOsAccountRealmManager().getWindowsRealm(realmSID2, null, host4);
		
		assertEquals(realm4.isPresent(), false);
				
		}
		finally {

		}
		
		
	}
	
	@Test
	public void basicOsAccountTests() throws TskCoreException, OsAccountManager.NotUserSIDException {

		try {
			//String ownerUid1 = "S-1-5-32-544"; // special short SIDS not handled yet
			
			// Create an account in a local scoped realm.
			
			String ownerUid1 = "S-1-5-21-111111111-222222222-3333333333-1001";
			String loginName1 = "jay";
			String realmName1 = "local";
			
			String hostname1 = "host1";
			Host host1 = caseDB.getHostManager().createHost(hostname1);
			
			//OsAccountRealm localRealm1 = caseDB.getOsAccountRealmManager().createWindowsRealm(ownerUid1, realmName1, host1, OsAccountRealm.RealmScope.LOCAL);
			OsAccount osAccount1 = caseDB.getOsAccountManager().createWindowsAccount(ownerUid1, loginName1, realmName1, host1, OsAccountRealm.RealmScope.LOCAL);
			
			assertEquals(osAccount1.getUniqueIdWithinRealm().orElse("").equalsIgnoreCase(ownerUid1), true);
			assertEquals(caseDB.getOsAccountRealmManager().getRealmById(osAccount1.getRealmId()).getRealmNames().get(0).equalsIgnoreCase(realmName1), true);
			
			// Create another account - with same SID on the same host - should return the existing account
			String loginName11 = "BlueJay";
			String realmName11 = "DESKTOP-9TO5";
			OsAccount osAccount11 = caseDB.getOsAccountManager().createWindowsAccount(ownerUid1, loginName11, realmName11, host1, OsAccountRealm.RealmScope.DOMAIN);
			
			// account should be the same as osAccount1
			assertEquals(osAccount11.getUniqueIdWithinRealm().orElse("").equalsIgnoreCase(ownerUid1), true);	
			assertEquals(caseDB.getOsAccountRealmManager().getRealmById(osAccount11.getRealmId()).getRealmNames().get(0).equalsIgnoreCase(realmName1), true);
			assertEquals(osAccount11.getLoginName().orElse("").equalsIgnoreCase(loginName1), true);	
			
			
			// Let's update osAccount1
			String fullName1 = "Johnny Depp";
			Long creationTime1 = 1611858618L;
			boolean isChanged = osAccount1.setCreationTime(creationTime1);
			assertEquals(isChanged, true);
			
			osAccount1.setFullName(fullName1);
			
			
			osAccount1 = caseDB.getOsAccountManager().updateAccount(osAccount1);
			assertEquals(osAccount1.getCreationTime().orElse(null), creationTime1);
			assertEquals(osAccount1.getFullName().orElse(null).equalsIgnoreCase(fullName1), true );
			
			
			// now try and create osAccount1 again - it should return the existing account
			OsAccount osAccount1_copy1 = caseDB.getOsAccountManager().createWindowsAccount(ownerUid1, null, realmName1, host1, OsAccountRealm.RealmScope.LOCAL);
			
			
			assertEquals(osAccount1_copy1.getUniqueIdWithinRealm().orElse("").equalsIgnoreCase(ownerUid1), true);
			assertEquals(caseDB.getOsAccountRealmManager().getRealmById(osAccount1_copy1.getRealmId()).getRealmNames().get(0).equalsIgnoreCase(realmName1), true);
			
			
			assertEquals(osAccount1_copy1.getFullName().orElse("").equalsIgnoreCase(fullName1), true);
			assertEquals(osAccount1.getCreationTime().orElse(null), creationTime1);
			
			
			// Test that getContentById() returns the same account
			Content content = caseDB.getContentById(osAccount1.getId());
			assertEquals(content != null, true);
			assertEquals(content instanceof OsAccount, true);
			OsAccount osAccount1_copy2 = (OsAccount) content;
			assertEquals(osAccount1_copy2.getUniqueIdWithinRealm().orElse("").equalsIgnoreCase(ownerUid1), true);
			
			
			
			// Create two new accounts on a new domain realm
			String ownerUid2 = "S-1-5-21-725345543-854245398-1060284298-1003";
			String ownerUid3 = "S-1-5-21-725345543-854245398-1060284298-1004";
	
			String realmName2 = "basis";
			
			String hostname2 = "host2";
			String hostname3 = "host3";
			Host host2 = caseDB.getHostManager().createHost(hostname2);
			Host host3 = caseDB.getHostManager().createHost(hostname3);
		
			OsAccountRealm domainRealm1 = caseDB.getOsAccountRealmManager().createWindowsRealm(ownerUid2, realmName2, host2, OsAccountRealm.RealmScope.DOMAIN);
		
			// create accounts in this domain scoped realm
			OsAccount osAccount2 = caseDB.getOsAccountManager().createWindowsAccount(ownerUid2, null, realmName2, host2, OsAccountRealm.RealmScope.DOMAIN);
			OsAccount osAccount3 = caseDB.getOsAccountManager().createWindowsAccount(ownerUid3, null, realmName2, host3, OsAccountRealm.RealmScope.DOMAIN);
			
			assertEquals(osAccount2.getUniqueIdWithinRealm().orElse("").equalsIgnoreCase(ownerUid2), true);
			assertEquals(caseDB.getOsAccountRealmManager().getRealmById(osAccount2.getRealmId()).getRealmNames().get(0).equalsIgnoreCase(realmName2), true);
			
			
			assertEquals(osAccount3.getUniqueIdWithinRealm().orElse("").equalsIgnoreCase(ownerUid3), true);
			assertEquals(caseDB.getOsAccountRealmManager().getRealmById(osAccount3.getRealmId()).getRealmNames().get(0).equalsIgnoreCase(realmName2), true);
			
		}
		
		finally {
			
		}

	}
	
	
	@Test
	public void windowsSpecialAccountTests() throws TskCoreException, OsAccountManager.NotUserSIDException {

		try {
			
			String SPECIAL_WINDOWS_REALM_ADDR = "SPECIAL_WINDOWS_ACCOUNTS";
			
			
			// TEST create accounts with special SIDs on host2
			{
				String hostname2 = "host222";
				Host host2 = caseDB.getHostManager().createHost(hostname2);

				String specialSid1 = "S-1-5-18";
				String specialSid2 = "S-1-5-19";
				String specialSid3 = "S-1-5-20";

				OsAccount specialAccount1 = caseDB.getOsAccountManager().createWindowsAccount(specialSid1, null, null, host2, OsAccountRealm.RealmScope.UNKNOWN);
				OsAccount specialAccount2 = caseDB.getOsAccountManager().createWindowsAccount(specialSid2, null, null, host2, OsAccountRealm.RealmScope.UNKNOWN);
				OsAccount specialAccount3 = caseDB.getOsAccountManager().createWindowsAccount(specialSid3, null, null, host2, OsAccountRealm.RealmScope.UNKNOWN);

				assertEquals(caseDB.getOsAccountRealmManager().getRealmById(specialAccount1.getRealmId()).getRealmAddr().orElse("").equalsIgnoreCase(SPECIAL_WINDOWS_REALM_ADDR), true);
				assertEquals(caseDB.getOsAccountRealmManager().getRealmById(specialAccount2.getRealmId()).getRealmAddr().orElse("").equalsIgnoreCase(SPECIAL_WINDOWS_REALM_ADDR), true);
				assertEquals(caseDB.getOsAccountRealmManager().getRealmById(specialAccount3.getRealmId()).getRealmAddr().orElse("").equalsIgnoreCase(SPECIAL_WINDOWS_REALM_ADDR), true);
			}
			
			
			// TEST create accounts with special SIDs on host3 - should create their own realm 
			{
				String hostname3 = "host333";
				Host host3 = caseDB.getHostManager().createHost(hostname3);

				String specialSid1 = "S-1-5-18";
				String specialSid2 = "S-1-5-19";
				String specialSid3 = "S-1-5-20";

				OsAccount specialAccount1 = caseDB.getOsAccountManager().createWindowsAccount(specialSid1, null, null, host3, OsAccountRealm.RealmScope.UNKNOWN);
				OsAccount specialAccount2 = caseDB.getOsAccountManager().createWindowsAccount(specialSid2, null, null, host3, OsAccountRealm.RealmScope.UNKNOWN);
				OsAccount specialAccount3 = caseDB.getOsAccountManager().createWindowsAccount(specialSid3, null, null, host3, OsAccountRealm.RealmScope.UNKNOWN);

				assertEquals(caseDB.getOsAccountRealmManager().getRealmById(specialAccount1.getRealmId()).getRealmAddr().orElse("").equalsIgnoreCase(SPECIAL_WINDOWS_REALM_ADDR), true);
				assertEquals(caseDB.getOsAccountRealmManager().getRealmById(specialAccount2.getRealmId()).getRealmAddr().orElse("").equalsIgnoreCase(SPECIAL_WINDOWS_REALM_ADDR), true);
				assertEquals(caseDB.getOsAccountRealmManager().getRealmById(specialAccount3.getRealmId()).getRealmAddr().orElse("").equalsIgnoreCase(SPECIAL_WINDOWS_REALM_ADDR), true);
				
				// verify a new local realm with host3 was created for these account even they've been seen previously on another host
				assertEquals(caseDB.getOsAccountRealmManager().getRealmById(specialAccount1.getRealmId()).getScopeHost().orElse(null).getName().equalsIgnoreCase(hostname3), true);
				assertEquals(caseDB.getOsAccountRealmManager().getRealmById(specialAccount1.getRealmId()).getScopeHost().orElse(null).getName().equalsIgnoreCase(hostname3), true);
				assertEquals(caseDB.getOsAccountRealmManager().getRealmById(specialAccount1.getRealmId()).getScopeHost().orElse(null).getName().equalsIgnoreCase(hostname3), true);
			}

			
			// Test some other special account.
			{
				String hostname4 = "host444";
				Host host4 = caseDB.getHostManager().createHost(hostname4);

				String specialSid1 = "S-1-5-80-3696737894-3623014651-202832235-645492566-13622391";
				String specialSid2 = "S-1-5-82-4003674586-223046494-4022293810-2417516693-151509167";
				String specialSid3 = "S-1-5-90-0-2";
				String specialSid4 = "S-1-5-96-0-3";
				

				OsAccount specialAccount1 = caseDB.getOsAccountManager().createWindowsAccount(specialSid1, null, null, host4, OsAccountRealm.RealmScope.UNKNOWN);
				OsAccount specialAccount2 = caseDB.getOsAccountManager().createWindowsAccount(specialSid2, null, null, host4, OsAccountRealm.RealmScope.UNKNOWN);
				OsAccount specialAccount3 = caseDB.getOsAccountManager().createWindowsAccount(specialSid3, null, null, host4, OsAccountRealm.RealmScope.UNKNOWN);
				OsAccount specialAccount4 = caseDB.getOsAccountManager().createWindowsAccount(specialSid4, null, null, host4, OsAccountRealm.RealmScope.UNKNOWN);
				

				assertEquals(caseDB.getOsAccountRealmManager().getRealmById(specialAccount1.getRealmId()).getRealmAddr().orElse("").equalsIgnoreCase(SPECIAL_WINDOWS_REALM_ADDR), true);
				assertEquals(caseDB.getOsAccountRealmManager().getRealmById(specialAccount2.getRealmId()).getRealmAddr().orElse("").equalsIgnoreCase(SPECIAL_WINDOWS_REALM_ADDR), true);
				assertEquals(caseDB.getOsAccountRealmManager().getRealmById(specialAccount3.getRealmId()).getRealmAddr().orElse("").equalsIgnoreCase(SPECIAL_WINDOWS_REALM_ADDR), true);
				assertEquals(caseDB.getOsAccountRealmManager().getRealmById(specialAccount4.getRealmId()).getRealmAddr().orElse("").equalsIgnoreCase(SPECIAL_WINDOWS_REALM_ADDR), true);
				
				
			}
			
			// TEST: create accounts with a invalid user SIDs - these should generate an exception
			{
				String hostname5 = "host555";
				String realmName5 = "realmName555";
				Host host5 = caseDB.getHostManager().createHost(hostname5);

				try {
					String sid1 = "S-1-5-32-544"; // builtin Administrators
					OsAccount osAccount1 = caseDB.getOsAccountManager().createWindowsAccount(sid1, null, realmName5, host5, OsAccountRealm.RealmScope.UNKNOWN);
					
					// above should raise an exception
					assertEquals(true, false);
				}
				catch (OsAccountManager.NotUserSIDException ex) {
					// continue
				}
				
				try {
					String sid2 = "S-1-5-21-725345543-854245398-1060284298-512"; //  domain admins group
					OsAccount osAccount2 = caseDB.getOsAccountManager().createWindowsAccount(sid2, null, realmName5, host5, OsAccountRealm.RealmScope.UNKNOWN);
					
					// above should raise an exception
					assertEquals(true, false);
				}
				catch (OsAccountManager.NotUserSIDException ex) {
					// continue
				}
				
				try {
					String sid3 = "S-1-1-0"; //  Everyone
					OsAccount osAccount3 = caseDB.getOsAccountManager().createWindowsAccount(sid3, null, realmName5, host5, OsAccountRealm.RealmScope.UNKNOWN);
					
					// above should raise an exception
					assertEquals(true, false);
				}
				catch (OsAccountManager.NotUserSIDException ex) {
					// continue
				}

			}
		}
		
		finally {
			
		}

	}
	
	
	@Test
	public void osAccountInstanceTests() throws TskCoreException, OsAccountManager.NotUserSIDException {

		String ownerUid1 = "S-1-5-21-111111111-222222222-3333333333-0001";
		String realmName1 = "realm1111";

		String hostname1 = "host1111";
		Host host1 = caseDB.getHostManager().createHost(hostname1);

		OsAccountRealm localRealm1 = caseDB.getOsAccountRealmManager().createWindowsRealm(ownerUid1, realmName1, host1, OsAccountRealm.RealmScope.LOCAL);
		OsAccount osAccount1 = caseDB.getOsAccountManager().createWindowsAccount(ownerUid1, null, realmName1, host1, OsAccountRealm.RealmScope.LOCAL);

		// Test: add an instance
		caseDB.getOsAccountManager().createOsAccountInstance(osAccount1, image, OsAccountInstance.OsAccountInstanceType.LAUNCHED);

		// Test: add an existing instance - should be a no-op.
		caseDB.getOsAccountManager().createOsAccountInstance(osAccount1, image, OsAccountInstance.OsAccountInstanceType.LAUNCHED);

		// Test: create account instance on a new host
		String hostname2 = "host2222";
		Host host2 = caseDB.getHostManager().createHost(hostname2);
		caseDB.getOsAccountManager().createOsAccountInstance(osAccount1, image, OsAccountInstance.OsAccountInstanceType.LAUNCHED);
	
		
		List<OsAccountAttribute> accountAttributes = new ArrayList<>();
		Long resetTime1 = 1611859999L;	
		
		// TBD: perhaps add some files to the case and then use one of the files as the source of attributes.
		
		OsAccountAttribute attrib1 = new OsAccountAttribute(caseDB.getAttributeType(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_PASSWORD_RESET.getTypeID()), resetTime1, osAccount1, null, image);
		accountAttributes.add(attrib1);
		
		String hint = "HINT";
		OsAccountAttribute attrib2 = new OsAccountAttribute(caseDB.getAttributeType(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PASSWORD_HINT.getTypeID()), hint, osAccount1, host2, image);
		accountAttributes.add(attrib2);
		
		// add attributes to account.
		osAccount1.addAttributes(accountAttributes);
		
		
		// now get the account with same sid,  and get its attribuites and verify.
		Optional<OsAccount> existingAccount1 = caseDB.getOsAccountManager().getOsAccountByUniqueId(osAccount1.getUniqueIdWithinRealm().get(), caseDB.getOsAccountRealmManager().getRealmById(osAccount1.getRealmId()));
		List<OsAccountAttribute> existingAccountAttribs  = existingAccount1.get().getOsAccountAttributes();
		
		
		assertEquals(existingAccountAttribs.size(), 2);
		for (OsAccountAttribute attr: existingAccountAttribs) {
			if (attr.getAttributeType().getTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_PASSWORD_RESET.getTypeID()) {
				assertEquals(attr.getValueLong(), resetTime1.longValue() );
				
			} else if (attr.getAttributeType().getTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PASSWORD_HINT.getTypeID()) {
				assertEquals(attr.getValueString().equalsIgnoreCase(hint), true );
			}
			
		}
		
		
	}
	
	
}

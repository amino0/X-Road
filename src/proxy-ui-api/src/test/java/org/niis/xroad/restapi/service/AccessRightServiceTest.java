/*
 *  The MIT License
 *  Copyright (c) 2018 Estonian Information System Authority (RIA),
 *  Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 *  Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package org.niis.xroad.restapi.service;

import ee.ria.xroad.common.conf.globalconf.GlobalGroupInfo;
import ee.ria.xroad.common.conf.globalconf.MemberInfo;
import ee.ria.xroad.common.conf.serverconf.model.AccessRightType;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.conf.serverconf.model.EndpointType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.GlobalGroupId;
import ee.ria.xroad.common.identifier.LocalGroupId;
import ee.ria.xroad.common.identifier.XRoadId;

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.niis.xroad.restapi.dto.ServiceClientAccessRightDto;
import org.niis.xroad.restapi.dto.ServiceClientDto;
import org.niis.xroad.restapi.facade.GlobalConfFacade;
import org.niis.xroad.restapi.repository.ClientRepository;
import org.niis.xroad.restapi.util.PersistenceTestUtil;
import org.niis.xroad.restapi.util.TestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.niis.xroad.restapi.util.TestUtils.OBSOLETE_GGROUP_ID;
import static org.niis.xroad.restapi.util.TestUtils.OBSOLETE_SCS_BASE_ENDPOINT_ID;
import static org.niis.xroad.restapi.util.TestUtils.OBSOLETE_SCS_FULL_SERVICE_CODE;
import static org.niis.xroad.restapi.util.TestUtils.OBSOLETE_SUBSYSTEM_ID;

/**
 * test access rights service
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureTestDatabase
@Slf4j
@Transactional
@WithMockUser
public class AccessRightServiceTest {
    private List<MemberInfo> memberInfos = new ArrayList<>(Arrays.asList(
            TestUtils.getMemberInfo(TestUtils.INSTANCE_FI, TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M1, null),
            TestUtils.getMemberInfo(TestUtils.INSTANCE_EE, TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M1,
                    TestUtils.SUBSYSTEM1),
            TestUtils.getMemberInfo(TestUtils.INSTANCE_FI, TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M1,
                    TestUtils.SUBSYSTEM1),
            TestUtils.getMemberInfo(TestUtils.INSTANCE_FI, TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M1,
                    TestUtils.SUBSYSTEM2),
            TestUtils.getMemberInfo(TestUtils.INSTANCE_FI, TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M1,
                    "SS5")
    ));
    private List<GlobalGroupInfo> globalGroupInfos = new ArrayList<>(Arrays.asList(
            TestUtils.getGlobalGroupInfo(TestUtils.INSTANCE_FI, TestUtils.DB_GLOBALGROUP_CODE),
            TestUtils.getGlobalGroupInfo(TestUtils.INSTANCE_FI, TestUtils.GLOBALGROUP_CODE1),
            TestUtils.getGlobalGroupInfo(TestUtils.INSTANCE_EE, TestUtils.DB_GLOBALGROUP_CODE)));
    private List<String> instanceIdentifiers = new ArrayList<>(Arrays.asList(
            TestUtils.INSTANCE_FI,
            TestUtils.INSTANCE_EE));

    @Autowired
    AccessRightService accessRightService;

    @MockBean
    GlobalConfFacade globalConfFacade;

    @MockBean
    GlobalConfService globalConfService;

    @Autowired
    EndpointService endpointService;

    @Autowired
    ClientRepository clientRepository;

    @Autowired
    private PersistenceTestUtil persistenceTestUtil;

    @Before
    public void setup() {
        when(globalConfFacade.getMembers()).thenReturn(memberInfos);
        when(globalConfFacade.getInstanceIdentifier()).thenReturn(TestUtils.INSTANCE_FI);
        when(globalConfFacade.getInstanceIdentifiers()).thenReturn(instanceIdentifiers);
        when(globalConfFacade.getGlobalGroups(any())).thenAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            if (args.length == 0) {
                return globalGroupInfos;
            }
            List<String> foundInstanceIdentifiers = new ArrayList<>(Arrays.asList(
                    Arrays.copyOf(args, args.length, String[].class)));
            return globalGroupInfos.stream()
                    .filter(globalGroupInfo -> foundInstanceIdentifiers
                            .contains(globalGroupInfo.getId().getXRoadInstance()))
                    .collect(Collectors.toList());
        });

        // note that these do not match globalConfFacade.getMembers and
        // globalConfFacade.getGlobalGroups mocks
        // they just check if item is predefined obsolete id
        when(globalConfService.clientsExist(any())).thenAnswer(invocation -> {
            Collection<XRoadId> identifiers = (Collection<XRoadId>) invocation.getArguments()[0];
            if (identifiers == null) return true; // some further mocking later causes this null
            return !identifiers.contains(OBSOLETE_SUBSYSTEM_ID);
        });
        when(globalConfService.globalGroupsExist(any())).thenAnswer(invocation -> {
            Collection<XRoadId> identifiers = (Collection<XRoadId>) invocation.getArguments()[0];
            if (identifiers == null) return true; // some further mocking later causes this null
            return !identifiers.contains(OBSOLETE_GGROUP_ID);
        });
    }

    private long countIdentifiers() {
        return persistenceTestUtil.countRows(XRoadId.class);
    }

    private long countAccessRights() {
        return persistenceTestUtil.countRows(AccessRightType.class);
    }

    private int countServiceClients(ClientId serviceOwnerId) {
        ClientType owner = clientRepository.getClient(serviceOwnerId);
        return owner.getAcl().stream().map(acl -> acl.getSubjectId())
                .collect(Collectors.toSet())
                .size();
    }

    @Test
    public void findAllAccessRightHolderCandidates() throws Throwable {
        List<ServiceClientDto> dtos = accessRightService.findAccessRightHolderCandidates(TestUtils.getM1Ss1ClientId(),
                null, null, null, null, null, null);
        assertEquals(10, dtos.size());
    }

    @Test
    public void findAccessRightHolderCandidatesByMemberOrGroupCode() throws Throwable {
        List<ServiceClientDto> dtos = accessRightService.findAccessRightHolderCandidates(TestUtils.getM1Ss1ClientId(),
                null, null, null, null, "1", null);
        assertEquals(6, dtos.size());
    }

    @Test
    public void findAccessRightHolderCandidatesByMemberOrGroupCodeNoResults() throws Throwable {
        List<ServiceClientDto> dtos = accessRightService.findAccessRightHolderCandidates(TestUtils.getM1Ss1ClientId(),
                null, null, null, null, "öäöäöäöäöäöä", null);
        assertEquals(0, dtos.size());
    }

    @Test
    public void findAccessRightHolderCandidatesByInstance() throws Throwable {
        List<ServiceClientDto> dtos = accessRightService.findAccessRightHolderCandidates(TestUtils.getM1Ss1ClientId(),
                null, null, TestUtils.INSTANCE_EE, null, null, null);
        assertEquals(5, dtos.size());
    }

    @Test
    public void findAccessRightHolderCandidatesByInstanceAndSubSystem() throws Throwable {
        List<ServiceClientDto> dtos = accessRightService.findAccessRightHolderCandidates(TestUtils.getM1Ss1ClientId(),
                null, null, TestUtils.INSTANCE_FI, null, null, TestUtils.SUBSYSTEM1);
        assertEquals(1, dtos.size());
    }

    @Test
    public void removeObsoleteServiceClientAccessRights() throws Exception {
        ClientId serviceOwner = TestUtils.getM1Ss1ClientId();
        Set<String> serviceCodes = new HashSet<>(Arrays.asList(
                "serviceWithObsoleteScs"));

        int initialServiceClients = countServiceClients(serviceOwner);
        long initialAccessRights = countAccessRights();

        // delete subsystem access
        accessRightService.deleteServiceClientAccessRights(serviceOwner,
                new HashSet<>(serviceCodes),
                OBSOLETE_SUBSYSTEM_ID);
        assertEquals(initialServiceClients - 1, countServiceClients(serviceOwner));
        assertEquals(initialAccessRights - 1, countAccessRights());

        // delete group access
        accessRightService.deleteServiceClientAccessRights(serviceOwner,
                new HashSet<>(serviceCodes),
                OBSOLETE_GGROUP_ID);
        assertEquals(initialServiceClients - 2, countServiceClients(serviceOwner));
        assertEquals(initialAccessRights - 2, countAccessRights());

        // can't remove what has already been removed
        try {
            accessRightService.deleteServiceClientAccessRights(serviceOwner,
                    new HashSet<>(serviceCodes),
                    OBSOLETE_GGROUP_ID);
            fail("should throw exception");
        } catch (AccessRightService.AccessRightNotFoundException expected) {
        }
    }

    @Test
    public void removeObsoleteEndpointAccessRights() throws Exception {
        ClientId serviceOwner = TestUtils.getM1Ss1ClientId();

        int initialServiceClients = countServiceClients(serviceOwner);
        long initialAccessRights = countAccessRights();

        // delete subsystem access
        accessRightService.deleteEndpointAccessRights(OBSOLETE_SCS_BASE_ENDPOINT_ID,
                new HashSet<XRoadId>(Arrays.asList(OBSOLETE_SUBSYSTEM_ID)));
        assertEquals(initialServiceClients - 1, countServiceClients(serviceOwner));
        assertEquals(initialAccessRights - 1, countAccessRights());

        // delete group access
        accessRightService.deleteEndpointAccessRights(OBSOLETE_SCS_BASE_ENDPOINT_ID,
                new HashSet<XRoadId>(Arrays.asList(OBSOLETE_GGROUP_ID)));
        assertEquals(initialServiceClients - 2, countServiceClients(serviceOwner));
        assertEquals(initialAccessRights - 2, countAccessRights());

        // can't remove what has already been removed
        try {
            accessRightService.deleteEndpointAccessRights(OBSOLETE_SCS_BASE_ENDPOINT_ID,
                    new HashSet<XRoadId>(Arrays.asList(OBSOLETE_GGROUP_ID)));
            fail("should throw exception");
        } catch (AccessRightService.AccessRightNotFoundException expected) {
        }
    }
    @Test
    public void removeObsoleteSoapServiceAccessRights() throws Exception {
        ClientId serviceOwner = TestUtils.getM1Ss1ClientId();

        int initialServiceClients = countServiceClients(serviceOwner);
        long initialAccessRights = countAccessRights();

        // delete subsystem access
        accessRightService.deleteSoapServiceAccessRights(serviceOwner, OBSOLETE_SCS_FULL_SERVICE_CODE,
                new HashSet<XRoadId>(Arrays.asList(OBSOLETE_SUBSYSTEM_ID)));
        assertEquals(initialServiceClients - 1, countServiceClients(serviceOwner));
        assertEquals(initialAccessRights - 1, countAccessRights());

        // delete group access
        accessRightService.deleteSoapServiceAccessRights(serviceOwner, OBSOLETE_SCS_FULL_SERVICE_CODE,
                new HashSet<XRoadId>(Arrays.asList(OBSOLETE_GGROUP_ID)));
        assertEquals(initialServiceClients - 2, countServiceClients(serviceOwner));
        assertEquals(initialAccessRights - 2, countAccessRights());

        // can't remove what has already been removed
        try {
            accessRightService.deleteSoapServiceAccessRights(serviceOwner, OBSOLETE_SCS_FULL_SERVICE_CODE,
                    new HashSet<XRoadId>(Arrays.asList(OBSOLETE_GGROUP_ID)));
            fail("should throw exception");
        } catch (AccessRightService.AccessRightNotFoundException expected) {
        }
    }

    @Test
    public void removeServiceClientAccessRights() throws Exception {
        // remove items from addServiceClientAccessRights

        ClientId serviceOwner = TestUtils.getM1Ss1ClientId();
        Set<String> serviceCodes = new HashSet<>(Arrays.asList(
                "calculatePrime", "openapi-servicecode", "rest-servicecode"));
        XRoadId subsystemId = TestUtils.getClientId(TestUtils.CLIENT_ID_SS5);
        int initialServiceClients = countServiceClients(serviceOwner);
        long initialAccessRights = countAccessRights();
        int initialAclSize = clientRepository.getClient(serviceOwner).getAcl().size();
        List<ServiceClientAccessRightDto> dtos = accessRightService.addServiceClientAccessRights(
                serviceOwner, serviceCodes, subsystemId);
        assertEquals(3, dtos.size());
        assertEquals(initialServiceClients + 1, countServiceClients(serviceOwner));
        assertEquals(initialAccessRights + 3, countAccessRights());
        assertEquals(initialAclSize + 3, clientRepository.getClient(serviceOwner).getAcl().size());

        // delete 2/3 of the added
        accessRightService.deleteServiceClientAccessRights(serviceOwner,
                new HashSet<>(Arrays.asList("openapi-servicecode", "rest-servicecode")),
                subsystemId);
        assertEquals(initialServiceClients + 1, countServiceClients(serviceOwner));
        assertEquals(initialAclSize + 1, clientRepository.getClient(serviceOwner).getAcl().size());
        assertEquals(initialAccessRights + 1, countAccessRights());

        // delete 1/3 remaining added
        accessRightService.deleteServiceClientAccessRights(serviceOwner,
                new HashSet<>(Arrays.asList("calculatePrime")),
                subsystemId);
        assertEquals(initialServiceClients, countServiceClients(serviceOwner));
        assertEquals(initialAccessRights, countAccessRights());
    }


    @Test
    public void removeServiceClientAccessRightsRemovesAllEndpoints() throws Exception {
        // openapi3-test has endpoints 8 (base), 9, 10, 11, 12
        // subject 8 = M2 / SS6 has access to 8, 9, 10, 11
        // add another service client access to some endpoints, then remove sc 8
        long initialAccessRights = countAccessRights();
        ClientId serviceOwner = ClientId.create("FI", "GOV", "M2", "SS6");
        int initialServiceClients = countServiceClients(serviceOwner);

        // add access to test-globalgroup
        Set<XRoadId> globalGroupSubject = new HashSet<>(Arrays.asList(
                GlobalGroupId.create("FI", "test-globalgroup")));
        accessRightService.addEndpointAccessRights(11L, globalGroupSubject);
        accessRightService.addEndpointAccessRights(12L, globalGroupSubject);
        assertEquals(initialAccessRights + 2, countAccessRights());
        assertEquals(initialServiceClients + 1, countServiceClients(serviceOwner));

        XRoadId ss6Id = serviceOwner;

        // remove access from ss6
        accessRightService.deleteServiceClientAccessRights(serviceOwner,
                new HashSet<>(Arrays.asList("openapi3-test")), ss6Id);
        assertEquals(initialAccessRights + 2 - 4, countAccessRights());
        assertEquals(initialServiceClients + 1 - 1, countServiceClients(serviceOwner));

    }

    @Test
    public void removeServiceClientServiceDoesNotExist() throws Exception {
        long initialAccessRights = countAccessRights();
        ClientId serviceOwner = ClientId.create("FI", "GOV", "M2", "SS6");
        int initialServiceClientsSs6 = countServiceClients(serviceOwner);

        XRoadId ss6Id = serviceOwner;

        // remove access from ss6. But ss6 does not have calculatePrime service
        try {
            accessRightService.deleteServiceClientAccessRights(serviceOwner,
                    new HashSet<>(Arrays.asList("openapi3-test", "calculatePrime")), ss6Id);
            fail("should throw exception");
        } catch (ServiceNotFoundException expected) {
        }
        assertEquals(initialAccessRights, countAccessRights());
        assertEquals(initialServiceClientsSs6, countServiceClients(serviceOwner));
    }

    @Test
    public void removeServiceClientAccessRightDoesNotExistSimple() throws Exception {


        ClientId serviceOwner = ClientId.create("FI", "GOV", "M2", "SS6");
        XRoadId ss6Id = serviceOwner;

        // openapi3-test base endpoint access has been granted only to subject #8 = ss6
        // try to remove from subject ss1, which should fail
        ClientId ss1Id = TestUtils.getM1Ss1ClientId();
        try {
            accessRightService.deleteServiceClientAccessRights(serviceOwner,
                    new HashSet<>(Arrays.asList("openapi3-test")), ss1Id);
            fail("should throw exception");
        } catch (AccessRightService.AccessRightNotFoundException expected) {
        }
    }

    @Test
    public void removeServiceClientOneAccessRightDoesNotExist() throws Exception {
        long initialAccessRights = countAccessRights();
        ClientId ss1Id = TestUtils.getM1Ss1ClientId();

        // prepare access to ss5 for ss1.getRandom and ss1.calculatePrime, but no
        // ss1.openapi-servicecode
        ClientId ss5Id = ClientId.create("FI", "GOV", "M2", "SS5");
        int initialServiceClientsSs1 = countServiceClients(ss1Id);
        List<ServiceClientAccessRightDto> dtos = accessRightService.addServiceClientAccessRights(
                ss1Id, new HashSet<>(Arrays.asList("getRandom", "calculatePrime")), ss5Id);
        assertEquals(2, dtos.size());
        assertEquals(initialAccessRights + 2, countAccessRights());
        assertEquals(initialServiceClientsSs1 + 1, countServiceClients(ss1Id));
        try {
            accessRightService.deleteServiceClientAccessRights(ss1Id,
                    new HashSet<>(Arrays.asList("getRandom", "calculatePrime", "openapi-servicecode")),
                    ss5Id);
            fail("should throw exception since ss1 does not have access to openapi-servicecode");
        } catch (AccessRightService.AccessRightNotFoundException expected) {
        }
    }

    @Test
    public void removeServiceClientTwoAccessRightsExist() throws Exception {
        // complement of removeServiceClientOneAccessRightDoesNotExist:
        // test that remove succeeds when we dont try to remove openapi-servicecode
        // separate test since previous deleteServiceClientAccessRights
        // is not rolled back if in same test


        long initialAccessRights = countAccessRights();
        ClientId ss1Id = TestUtils.getM1Ss1ClientId();

        // prepare access to ss5 for ss1.getRandom and ss1.calculatePrime, but no
        // ss1.openapi-servicecode
        ClientId ss5Id = ClientId.create("FI", "GOV", "M2", "SS5");
        int initialServiceClientsSs1 = countServiceClients(ss1Id);
        List<ServiceClientAccessRightDto> dtos = accessRightService.addServiceClientAccessRights(
                ss1Id, new HashSet<>(Arrays.asList("getRandom", "calculatePrime")), ss5Id);
        assertEquals(2, dtos.size());
        assertEquals(initialAccessRights + 2, countAccessRights());
        assertEquals(initialServiceClientsSs1 + 1, countServiceClients(ss1Id));

        accessRightService.deleteServiceClientAccessRights(ss1Id,
                new HashSet<>(Arrays.asList("getRandom", "calculatePrime")),
                ss5Id);
        assertEquals(initialAccessRights, countAccessRights());
        assertEquals(initialServiceClientsSs1, countServiceClients(ss1Id));
        assertEquals(initialAccessRights, countAccessRights());
        assertEquals(initialServiceClientsSs1, countServiceClients(ss1Id));
    }

    @Test
    public void removeServiceClientAccessRightForOtherClientsLocalGroup() throws Exception {
        // try to remove access rights from a local group that belongs to some other client than service owner

        String localGroupCode = "group2";
        // ss6 has service openapi3-test
        ClientId ss6Id = ClientId.create("FI", "GOV", "M2", "SS6");
        // ss1 has service getRandom, and group "group2"
        ClientId ss1Id = ClientId.create("FI", "GOV", "M1", "SS1");
        LocalGroupId group2Id = LocalGroupId.create(localGroupCode);

        // try to remove group2 access rights from ss6 services
        try {
            accessRightService.deleteServiceClientAccessRights(ss6Id,
                    new HashSet<>(Arrays.asList("openapi3-test")), group2Id);
            fail("should throw exception");
        } catch (AccessRightService.AccessRightNotFoundException expected) {
        }
    }

    @Test
    public void removeServiceClientAccessRightWrongObjectType() throws Exception {
        ClientId serviceOwner = TestUtils.getM1Ss1ClientId();
        Set<String> serviceCodes = new HashSet<>(Arrays.asList(
                "calculatePrime", "openapi-servicecode", "rest-servicecode"));
        XRoadId memberId = TestUtils.getClientId(TestUtils.OWNER_ID);
        try {
            accessRightService.deleteServiceClientAccessRights(serviceOwner, serviceCodes, memberId);
            fail("should have thrown exception");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void removeServiceClientAccessRightFromWrongServiceOwner() throws Exception {


        // bodyMassIndexOld belongs to ss2
        ClientId ss1Id = TestUtils.getM1Ss1ClientId();
        ClientId ss2Id = TestUtils.getM1Ss2ClientId(); // ss2
        Set<String> getRandomCode = new HashSet<>(Arrays.asList("bodyMassIndexOld"));
        XRoadId subsystemId = TestUtils.getClientId(TestUtils.CLIENT_ID_SS5);
        accessRightService.addServiceClientAccessRights(ss2Id, getRandomCode, subsystemId);

        try {
            accessRightService.deleteServiceClientAccessRights(ss1Id, getRandomCode, subsystemId);
            fail("should have thrown exception");
        } catch (ServiceNotFoundException expected) {
        }
    }

    @Test
    public void addServiceClientAccessRightsForObsoleteFails() throws Exception {

        ClientId serviceOwner = TestUtils.getM1Ss1ClientId();
        Set<String> serviceCodes = new HashSet<>(Arrays.asList(
                "calculatePrime", "openapi-servicecode", "rest-servicecode"));

        // try to add access to obsolete subsystem
        try {
            accessRightService.addServiceClientAccessRights(serviceOwner, serviceCodes,
                    OBSOLETE_SUBSYSTEM_ID);
            fail("should throw exception");
        } catch (ServiceClientNotFoundException expected) {
        }

        // try to add access to obsolete globalgroup
        try {
            accessRightService.addServiceClientAccessRights(serviceOwner, serviceCodes,
                    OBSOLETE_GGROUP_ID);
            fail("should throw exception");
        } catch (ServiceClientNotFoundException expected) {
        }
    }

    @Test
    public void addServiceClientAccessRights() throws Exception {
        long identifiers = countIdentifiers();

        ClientId serviceOwner = TestUtils.getM1Ss1ClientId();
        Set<String> serviceCodes = new HashSet<>(Arrays.asList(
                "calculatePrime", "openapi-servicecode", "rest-servicecode"));
        XRoadId subsystemId = TestUtils.getClientId(TestUtils.CLIENT_ID_SS5);
        List<ServiceClientAccessRightDto> dtos = accessRightService.addServiceClientAccessRights(
                serviceOwner, serviceCodes, subsystemId);
        assertEquals(3, dtos.size());

        ServiceClientAccessRightDto accessRightDto = dtos.stream()
                .filter(a -> a.getServiceCode().equals("calculatePrime"))
                .findFirst().get();
        assertEquals("calculatePrime-title", accessRightDto.getTitle());
        assertNotNull(accessRightDto.getRightsGiven());

        XRoadId localGroupId = LocalGroupId.create("group2");
        dtos = accessRightService.addServiceClientAccessRights(serviceOwner, serviceCodes, localGroupId);
        assertEquals(3, dtos.size());

        XRoadId globalGroupId = GlobalGroupId.create(TestUtils.INSTANCE_FI, TestUtils.DB_GLOBALGROUP_CODE);
        dtos = accessRightService.addServiceClientAccessRights(serviceOwner, serviceCodes, globalGroupId);
        assertEquals(3, dtos.size());

        // all subjects already had identifiers in DB, no new ones should have been added
        assertEquals(identifiers, countIdentifiers());

        // adding duplicates should throw exceptions
        Set<String> duplicateServiceCode = new HashSet<>(Arrays.asList("calculatePrime"));
        try {
            accessRightService.addServiceClientAccessRights(serviceOwner, duplicateServiceCode, subsystemId);
            fail("should throw exception");
        } catch (AccessRightService.DuplicateAccessRightException expected) {
        }
        try {
            accessRightService.addServiceClientAccessRights(serviceOwner, duplicateServiceCode, localGroupId);
            fail("should throw exception");
        } catch (AccessRightService.DuplicateAccessRightException expected) {
        }
        try {
            accessRightService.addServiceClientAccessRights(serviceOwner, duplicateServiceCode, globalGroupId);
            fail("should throw exception");
        } catch (AccessRightService.DuplicateAccessRightException expected) {
        }
    }

    @Test
    public void addAccessRightsInternal() throws Exception {
        ClientId serviceOwner = TestUtils.getM1Ss1ClientId();
        Set<String> serviceCodes = new HashSet<>(Arrays.asList(
                "calculatePrime", "openapi-servicecode", "rest-servicecode"));

        XRoadId subsystemId = TestUtils.getClientId(TestUtils.CLIENT_ID_SS5);
        XRoadId localGroupId = LocalGroupId.create("group2");
        XRoadId globalGroupId = GlobalGroupId.create(TestUtils.INSTANCE_FI, TestUtils.DB_GLOBALGROUP_CODE);
        Set<XRoadId> subjectIds = new HashSet<>(Arrays.asList(subsystemId, localGroupId, globalGroupId));

        ClientType ownerClient = clientRepository.getClient(serviceOwner);

        List<EndpointType> endpoints = serviceCodes.stream()
                .map(code -> {
                    try {
                        return endpointService.getServiceBaseEndpoint(ownerClient, code);
                    } catch (EndpointNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                }).collect(Collectors.toList());

        Map<XRoadId, List<ServiceClientAccessRightDto>> dtosById = accessRightService.addAccessRightsInternal(
                subjectIds, ownerClient, endpoints);

        // should have 3 subjects with 3 identical access rights each
        assertEquals(3, dtosById.size());
        for (XRoadId subjectId: dtosById.keySet()) {
            List<ServiceClientAccessRightDto> accessRights = dtosById.get(subjectId);
            assertNotNull(accessRights);
            assertEquals(3, accessRights.size());
            ServiceClientAccessRightDto dto = findServiceClientAccessRightDto("calculatePrime", accessRights);
            assertNotNull(dto);
            assertEquals("calculatePrime-title", dto.getTitle());
            assertNotNull(dto.getRightsGiven());
            assertNotNull(findServiceClientAccessRightDto("openapi-servicecode", accessRights));
            assertNotNull(findServiceClientAccessRightDto("rest-servicecode", accessRights));
        }

    }

    private ServiceClientAccessRightDto findServiceClientAccessRightDto(String serviceCode,
            List<ServiceClientAccessRightDto> accessRights) {
        return accessRights.stream()
                .filter(dto -> dto.getServiceCode().equals(serviceCode))
                .findFirst()
                .orElse(null);
    }

    @Test
    public void addServiceClientAccessRightsForOtherClientsLocalGroup() throws Exception {
        // if we try to add access right to client X's service for client Y's local group,
        // we should get IdentifierNotFoundException

        ClientId serviceOwner = TestUtils.getM1Ss2ClientId(); // ss2
        Set<String> serviceCodes = new HashSet<>(Arrays.asList("bodyMassIndexOld")); // belongs to ss2
        XRoadId localGroupId = LocalGroupId.create("group2"); // belongs to ss1
        try {
            accessRightService.addServiceClientAccessRights(serviceOwner, serviceCodes, localGroupId);
            fail("should have thrown exception");
        } catch (ServiceClientNotFoundException expected) {
        }
    }

    @Test
    public void addServiceClientAccessRightsWrongObjectType() throws Exception {
        ClientId serviceOwner = TestUtils.getM1Ss1ClientId();
        Set<String> serviceCodes = new HashSet<>(Arrays.asList(
                "calculatePrime", "openapi-servicecode", "rest-servicecode"));
        XRoadId memberId = TestUtils.getClientId(TestUtils.OWNER_ID);
        try {
            accessRightService.addServiceClientAccessRights(serviceOwner, serviceCodes, memberId);
            fail("should have thrown exception");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void addServiceClientAccessRightsForWrongServiceOwner() throws Exception {
        // if service owner does not match, should receive EndpointNotFoundExceptions
        ClientId serviceOwner = TestUtils.getM1Ss1ClientId();
        // bodyMassIndexOld belongs to ss2, calculatePrime and openapi-servicecode to ss1
        Set<String> serviceCodes = new HashSet<>(Arrays.asList(
                "calculatePrime", "openapi-servicecode", "bodyMassIndexOld"));
        XRoadId subsystemId = TestUtils.getClientId(TestUtils.CLIENT_ID_SS5);
        try {
            accessRightService.addServiceClientAccessRights(serviceOwner, serviceCodes, subsystemId);
            fail("should have thrown exception");
        } catch (ServiceNotFoundException expected) {
        }

        XRoadId localGroupId = LocalGroupId.create("group2");
        //SS2 does not have local group group2
        try {
            accessRightService.addServiceClientAccessRights(serviceOwner, serviceCodes, localGroupId);
            fail("should have thrown exception");
        } catch (ServiceNotFoundException expected) {
        }

        XRoadId globalGroupId = GlobalGroupId.create(TestUtils.INSTANCE_FI, TestUtils.DB_GLOBALGROUP_CODE);
        try {
            accessRightService.addServiceClientAccessRights(serviceOwner, serviceCodes, globalGroupId);
            fail("should have thrown exception");
        } catch (ServiceNotFoundException expected) {
        }
    }

    @Test
    public void addServiceClientAccessRightsForBadSubjects() throws Exception {
        when(globalConfService.clientsExist(any())).thenReturn(false);
        when(globalConfService.globalGroupsExist(any())).thenReturn(false);

        ClientId serviceOwner = TestUtils.getM1Ss1ClientId();
        Set<String> serviceCodes = new HashSet<>(Arrays.asList(
                "calculatePrime", "openapi-servicecode", "rest-servicecode"));
        XRoadId subsystemId = TestUtils.getClientId(TestUtils.CLIENT_ID_SS5);
        try {
            accessRightService.addServiceClientAccessRights(serviceOwner, serviceCodes, subsystemId);
            fail("should have thrown exception");
        } catch (ServiceClientNotFoundException expected) {
        }

        XRoadId localGroupId = LocalGroupId.create("nonexistent-group");
        try {
            accessRightService.addServiceClientAccessRights(serviceOwner, serviceCodes, localGroupId);
            fail("should have thrown exception");
        } catch (ServiceClientNotFoundException expected) {
        }

        XRoadId globalGroupId = GlobalGroupId.create(TestUtils.INSTANCE_FI, TestUtils.DB_GLOBALGROUP_CODE);
        try {
            accessRightService.addServiceClientAccessRights(serviceOwner, serviceCodes, globalGroupId);
            fail("should have thrown exception");
        } catch (ServiceClientNotFoundException expected) {
        }
    }

    @Test
    public void addServiceClientAccessRightsDuplicate() throws Exception {
        ClientId serviceOwner = TestUtils.getM1Ss1ClientId();
        Set<String> serviceCodes = new HashSet<>(Arrays.asList("calculatePrime"));
        XRoadId subsystemId = TestUtils.getClientId(TestUtils.CLIENT_ID_SS5);
        List<ServiceClientAccessRightDto> dtos = accessRightService.addServiceClientAccessRights(
                serviceOwner, serviceCodes, subsystemId);
        assertEquals(1, dtos.size());

        // duplicates are detected also when non-duplicates are also included
        serviceCodes = new HashSet<>(Arrays.asList("openapi-servicecode", "calculatePrime"));
        try {
            accessRightService.addServiceClientAccessRights(serviceOwner, serviceCodes, subsystemId);
            fail("should throw exception");
        } catch (AccessRightService.DuplicateAccessRightException expected) {
        }
    }

    @Test
    public void addServiceClientAccessRightsAddsNewIdentifiers() throws Throwable {
        long identifiers = countIdentifiers();

        ClientId serviceOwner = TestUtils.getM1Ss1ClientId();
        Set<String> serviceCodes = new HashSet<>(Arrays.asList(
                "calculatePrime", "openapi-servicecode", "rest-servicecode"));
        XRoadId subsystemId = TestUtils.getClientId("FI:GOV:M1:SS-NEW");
        List<ServiceClientAccessRightDto> dtos = accessRightService.addServiceClientAccessRights(
                serviceOwner, serviceCodes, subsystemId);
        assertEquals(3, dtos.size());

        ServiceClientAccessRightDto accessRightDto = dtos.stream()
                .filter(a -> a.getServiceCode().equals("calculatePrime"))
                .findFirst().get();
        assertEquals("calculatePrime-title", accessRightDto.getTitle());
        assertNotNull(accessRightDto.getRightsGiven());

        XRoadId localGroupId = LocalGroupId.create("identifier-less-group");
        dtos = accessRightService.addServiceClientAccessRights(serviceOwner, serviceCodes, localGroupId);
        assertEquals(3, dtos.size());

        XRoadId globalGroupId = GlobalGroupId.create(TestUtils.INSTANCE_FI, TestUtils.GLOBALGROUP_CODE1);
        dtos = accessRightService.addServiceClientAccessRights(serviceOwner, serviceCodes, globalGroupId);
        assertEquals(3, dtos.size());

        // 3 new subjects added - 3 identifiers created
        assertEquals(3, (countIdentifiers() - identifiers));
    }


    @Test
    public void addSoapServiceAccessRightsForObsoleteFails() throws Exception {
        ClientId serviceOwner = TestUtils.getM1Ss1ClientId();
        Set<String> serviceCodes = new HashSet<>(Arrays.asList(
                "calculatePrime", "openapi-servicecode", "rest-servicecode"));

        // try to add access to obsolete subsystem
        try {
            accessRightService.addSoapServiceAccessRights(serviceOwner, OBSOLETE_SCS_FULL_SERVICE_CODE,
                    new HashSet<>(Arrays.asList(OBSOLETE_SUBSYSTEM_ID)));
            fail("should throw exception");
        } catch (ServiceClientNotFoundException expected) {
        }

        // try to add access to obsolete globalgroup
        try {
            accessRightService.addSoapServiceAccessRights(serviceOwner, OBSOLETE_SCS_FULL_SERVICE_CODE,
                    new HashSet<>(Arrays.asList(OBSOLETE_GGROUP_ID)));
            fail("should throw exception");
        } catch (ServiceClientNotFoundException expected) {
        }
    }

    @Test
    public void addEndpointAccessRightsForObsoleteFails() throws Exception {
        Set<String> serviceCodes = new HashSet<>(Arrays.asList(
                "calculatePrime", "openapi-servicecode", "rest-servicecode"));

        // try to add access to obsolete subsystem
        try {
            accessRightService.addEndpointAccessRights(OBSOLETE_SCS_BASE_ENDPOINT_ID,
                    new HashSet<>(Arrays.asList(OBSOLETE_SUBSYSTEM_ID)));
            fail("should throw exception");
        } catch (ServiceClientNotFoundException expected) {
        }

        // try to add access to obsolete globalgroup
        try {
            accessRightService.addEndpointAccessRights(OBSOLETE_SCS_BASE_ENDPOINT_ID,
                    new HashSet<>(Arrays.asList(OBSOLETE_GGROUP_ID)));
            fail("should throw exception");
        } catch (ServiceClientNotFoundException expected) {
        }
    }

    @Test
    public void addAccessRights() throws Throwable {
        ClientId clientId = TestUtils.getM1Ss1ClientId();
        Set<XRoadId> subjectIds = new HashSet<>();
        subjectIds.add(ClientId.create(TestUtils.INSTANCE_FI, TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M1,
                TestUtils.SUBSYSTEM2));
        subjectIds.add(GlobalGroupId.create(TestUtils.INSTANCE_FI, TestUtils.DB_GLOBALGROUP_CODE));
        subjectIds.add(LocalGroupId.create("group2"));
        List<ServiceClientDto> dtos = accessRightService.addSoapServiceAccessRights(clientId,
                TestUtils.SERVICE_CALCULATE_PRIME, subjectIds);
        assertEquals(4, dtos.size());
    }

    @Test
    public void addAccessRightsForNonLocalSubsystem() throws Throwable {
        ClientId clientId = TestUtils.getM1Ss1ClientId();
        Set<XRoadId> subjectIds = new HashSet<>();
        ClientId ss3 = ClientId.create(TestUtils.INSTANCE_FI, TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M1,
                TestUtils.SUBSYSTEM3);
        Long ss3Pk = (Long) ReflectionTestUtils.getField(ss3, "id");
        assertNull(ss3Pk); // SS3 does not exists (no primary key) but it will be created
        subjectIds.add(ss3);
        subjectIds.add(GlobalGroupId.create(TestUtils.INSTANCE_FI, TestUtils.DB_GLOBALGROUP_CODE));
        subjectIds.add(LocalGroupId.create("group2"));
        List<ServiceClientDto> dtos = accessRightService.addSoapServiceAccessRights(clientId,
                TestUtils.SERVICE_CALCULATE_PRIME, subjectIds);
        assertEquals(4, dtos.size());
        ServiceClientDto persistedSs3 = dtos.stream()
                .filter(accessRightHolderDto -> accessRightHolderDto.getSubjectId().equals(ss3))
                .findFirst().get();
        ClientId ss3PersistedSubjectId = (ClientId) persistedSs3.getSubjectId();
        Long ss3PersistedPk = (Long) ReflectionTestUtils.getField(ss3PersistedSubjectId, "id");
        assertNotNull(ss3PersistedPk); // SS3 has a primary key
    }

    @Test(expected = ServiceClientNotFoundException.class)
    public void addAccessRightsForNonExistingClient() throws Throwable {
        when(globalConfService.clientsExist(any())).thenReturn(false);
        when(globalConfService.globalGroupsExist(any())).thenReturn(false);
        ClientId clientId = TestUtils.getM1Ss1ClientId();
        Set<XRoadId> subjectIds = new HashSet<>();
        subjectIds.add(ClientId.create(TestUtils.INSTANCE_FI, TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M1,
                "nope"));
        subjectIds.add(LocalGroupId.create("group2"));
        accessRightService.addSoapServiceAccessRights(clientId, TestUtils.SERVICE_CALCULATE_PRIME, subjectIds);
    }

    @Test(expected = ServiceClientNotFoundException.class)
    public void addDuplicateAccessRights() throws Throwable {
        when(globalConfService.clientsExist(any())).thenReturn(false);
        when(globalConfService.globalGroupsExist(any())).thenReturn(false);
        ClientId clientId = TestUtils.getM1Ss1ClientId();
        Set<XRoadId> subjectIds = new HashSet<>();
        subjectIds.add(ClientId.create(TestUtils.INSTANCE_FI, TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M1,
                "nope"));
        subjectIds.add(LocalGroupId.create("group1"));
        accessRightService.addSoapServiceAccessRights(clientId, TestUtils.SERVICE_CALCULATE_PRIME, subjectIds);
    }

    @Test
    public void addAccessRightsToLocalGroup() throws Throwable {
        ClientId clientId = TestUtils.getM1Ss1ClientId();
        Set<XRoadId> subjectIds = new HashSet<>();
        subjectIds.add(LocalGroupId.create("group1")); // this is a LocalGroup with groupCode 'group1' in data.sql
        List<ServiceClientDto> aclHolders = accessRightService.addSoapServiceAccessRights(clientId,
                TestUtils.SERVICE_CALCULATE_PRIME, subjectIds);
        Optional<ServiceClientDto> addedLocalGroupServiceClient = aclHolders.stream()
                .filter(s -> "group1".equals(s.getLocalGroupCode()))
                .findFirst();
        assertTrue(addedLocalGroupServiceClient.isPresent());
        assertEquals(LocalGroupId.create(TestUtils.DB_LOCAL_GROUP_CODE), addedLocalGroupServiceClient.get()
                .getSubjectId());
    }

    @Test(expected = ServiceClientNotFoundException.class)
    public void addAccessRightsToOtherClientsLocalGroup() throws Throwable {
        ClientId clientId = TestUtils.getM1Ss2ClientId();
        Set<XRoadId> subjectIds = new HashSet<>();
        subjectIds.add(LocalGroupId.create("group1"));
        accessRightService.addSoapServiceAccessRights(clientId, TestUtils.SERVICE_BMI_OLD, subjectIds);
    }

}

package alien4cloud.deployment;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.Resource;
import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.collections4.MapUtils;
import org.elasticsearch.index.query.QueryBuilders;
import org.springframework.stereotype.Service;

import alien4cloud.application.ApplicationEnvironmentService;
import alien4cloud.application.ApplicationVersionService;
import alien4cloud.application.TopologyCompositionService;
import alien4cloud.common.AlienConstants;
import alien4cloud.dao.IGenericSearchDAO;
import alien4cloud.deployment.matching.services.location.TopologyLocationUtils;
import alien4cloud.deployment.model.DeploymentConfiguration;
import alien4cloud.deployment.model.DeploymentSubstitutionConfiguration;
import alien4cloud.exception.NotFoundException;
import alien4cloud.model.application.ApplicationEnvironment;
import alien4cloud.model.application.ApplicationVersion;
import alien4cloud.model.components.AbstractPropertyValue;
import alien4cloud.model.deployment.DeploymentTopology;
import alien4cloud.model.orchestrators.locations.Location;
import alien4cloud.model.orchestrators.locations.LocationResourceTemplate;
import alien4cloud.model.topology.AbstractPolicy;
import alien4cloud.model.topology.LocationPlacementPolicy;
import alien4cloud.model.topology.NodeGroup;
import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.model.topology.Topology;
import alien4cloud.orchestrators.locations.services.LocationResourceService;
import alien4cloud.orchestrators.locations.services.LocationService;
import alien4cloud.security.AuthorizationUtil;
import alien4cloud.security.model.DeployerRole;
import alien4cloud.topology.TopologyServiceCore;
import alien4cloud.tosca.properties.constraints.exception.ConstraintValueDoNotMatchPropertyTypeException;
import alien4cloud.tosca.properties.constraints.exception.ConstraintViolationException;
import alien4cloud.utils.ReflectionUtil;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * Manages the deployment topology handling.
 */
@Service
@Slf4j
public class DeploymentTopologyService {
    @Resource(name = "alien-es-dao")
    private IGenericSearchDAO alienDAO;
    @Inject
    private ApplicationVersionService appVersionService;
    @Inject
    private ApplicationEnvironmentService appEnvironmentServices;
    @Inject
    private LocationService locationService;
    @Inject
    private LocationResourceService locationResourceService;
    @Inject
    private ApplicationVersionService applicationVersionService;
    @Inject
    private ApplicationEnvironmentService applicationEnvironmentService;
    @Inject
    private InputsPreProcessorService inputsPreProcessorService;
    @Inject
    private DeploymentInputService deploymentInputService;
    @Inject
    private TopologyCompositionService topologyCompositionService;
    @Inject
    private TopologyServiceCore topologyServiceCore;
    @Inject
    private DeploymentNodeSubstitutionService deploymentNodeSubstitutionService;

    public void save(DeploymentTopology deploymentTopology) {
        deploymentTopology.setLastUpdateDate(new Date());
        alienDAO.save(deploymentTopology);
    }

    /**
     * Get a deployment topology from it's id or throw a NotFoundException if none exists for this id.
     *
     * @param id The id of the deployment topology to get.
     * @return The deployment topology matching the given id.
     */
    public DeploymentTopology getOrFail(String id) {
        DeploymentTopology deploymentTopology = alienDAO.findById(DeploymentTopology.class, id);
        if (deploymentTopology == null) {
            throw new NotFoundException("Deployment topology [" + id + "] doesn't exists.");
        }
        return deploymentTopology;
    }

    private DeploymentSubstitutionConfiguration getAvailableNodeSubstitutions(DeploymentTopology deploymentTopology) {
        Map<String, List<LocationResourceTemplate>> availableSubstitutions = deploymentNodeSubstitutionService.getAvailableSubstitutions(deploymentTopology);
        DeploymentSubstitutionConfiguration dsc = new DeploymentSubstitutionConfiguration();
        Map<String, Set<String>> availableSubstitutionsIds = Maps.newHashMap();
        Map<String, LocationResourceTemplate> templates = Maps.newHashMap();
        for (Map.Entry<String, List<LocationResourceTemplate>> availableSubstitutionsEntry : availableSubstitutions.entrySet()) {
            Set<String> existingIds = availableSubstitutionsIds.get(availableSubstitutionsEntry.getKey());
            if (existingIds == null) {
                existingIds = Sets.newHashSet();
                availableSubstitutionsIds.put(availableSubstitutionsEntry.getKey(), existingIds);
            }
            for (LocationResourceTemplate template : availableSubstitutionsEntry.getValue()) {
                existingIds.add(template.getId());
                templates.put(template.getId(), template);
            }
        }
        dsc.setAvailableSubstitutions(availableSubstitutionsIds);
        dsc.setSubstitutionsTemplates(templates);
        dsc.setSubstitutionTypes(locationResourceService.getLocationResourceTypes(templates.values()));
        return dsc;
    }

    public DeploymentConfiguration getDeploymentConfiguration(String environmentId) {
        DeploymentTopology deploymentTopology = getOrCreateDeploymentTopology(environmentId);
        return getDeploymentConfiguration(deploymentTopology);
    }

    public DeploymentConfiguration getDeploymentConfiguration(DeploymentTopology deploymentTopology) {
        DeploymentSubstitutionConfiguration substitutionConfiguration = getAvailableNodeSubstitutions(deploymentTopology);
        Map<String, Set<String>> availableSubstitutions = substitutionConfiguration.getAvailableSubstitutions();
        Map<String, String> existingSubstitutions = deploymentTopology.getSubstitutedNodes();
        // Handle the case when new resources added
        // TODO In the case when resource is updated / deleted on the location we should update everywhere where they are used
        if (availableSubstitutions.size() != existingSubstitutions.size()) {
            updateDeploymentTopology(deploymentTopology);
        }
        return new DeploymentConfiguration(deploymentTopology, substitutionConfiguration);
    }

    /**
     * Get or create if not yet existing the {@link DeploymentTopology}. This method will check if the initial topology has been updated, if so it will try to
     * re-synchronize the topology and the deployment topology
     *
     * @param environment the environment
     * @return the related or created deployment topology
     */
    private DeploymentTopology getOrCreateDeploymentTopology(ApplicationEnvironment environment, String topologyId) {
        String id = DeploymentTopology.generateId(environment.getCurrentVersionId(), environment.getId());
        DeploymentTopology deploymentTopology = alienDAO.findById(DeploymentTopology.class, id);
        Topology topology = topologyServiceCore.getOrFail(topologyId);
        if (deploymentTopology == null) {
            deploymentTopology = generateDeploymentTopology(id, environment, topology, new DeploymentTopology());
        } else {
            Map<String, String> locationIds = TopologyLocationUtils.getLocationIds(deploymentTopology);
            boolean locationsInvalid = false;
            Map<String, Location> locations = Maps.newHashMap();
            if (!MapUtils.isEmpty(locationIds)) {
                try {
                    locations = getLocations(locationIds);
                } catch (NotFoundException ignored) {
                    locationsInvalid = true;
                }
            }
            if (locationsInvalid) {
                // Generate the deployment topology if none exist or if locations are not valid anymore
                deploymentTopology = generateDeploymentTopology(id, environment, topology, new DeploymentTopology());
            } else if (checkIfTopologyOrLocationHasChanged(deploymentTopology, locations.values(), topology)) {
                // Re-generate the deployment topology if the initial topology has been changed
                generateDeploymentTopology(id, environment, topology, deploymentTopology);
            }
        }
        return deploymentTopology;
    }

    private boolean checkIfTopologyOrLocationHasChanged(DeploymentTopology deploymentTopology, Collection<Location> locations, Topology topology) {
        if (deploymentTopology.getLastDeploymentTopologyUpdateDate().before(topology.getLastUpdateDate())) {
            return true;
        }
        for (Location location : locations) {
            if (deploymentTopology.getLastDeploymentTopologyUpdateDate().before(location.getLastUpdateDate())) {
                return true;
            }
        }
        return false;
    }

    private DeploymentTopology generateDeploymentTopology(String id, ApplicationEnvironment environment, Topology topology,
            DeploymentTopology deploymentTopology) {
        // TODO first check the initial topology is valid before doing this
        deploymentTopology.setVersionId(environment.getCurrentVersionId());
        deploymentTopology.setEnvironmentId(environment.getId());
        deploymentTopology.setInitialTopologyId(topology.getId());
        deploymentTopology.setId(id);
        doUpdateDeploymentTopology(deploymentTopology, topology, environment);
        return deploymentTopology;
    }

    /**
     * Deployment configuration has been changed, in this case must re-synchronize the deployment topology
     *
     * @param deploymentTopology the deployment topology to update
     */
    public void updateDeploymentTopology(DeploymentTopology deploymentTopology) {
        ApplicationEnvironment environment = appEnvironmentServices.getOrFail(deploymentTopology.getEnvironmentId());
        Topology topology = topologyServiceCore.getOrFail(deploymentTopology.getInitialTopologyId());
        doUpdateDeploymentTopology(deploymentTopology, topology, environment);
    }

    private void doUpdateDeploymentTopology(DeploymentTopology deploymentTopology, Topology topology, ApplicationEnvironment environment) {
        deploymentTopology.setLastDeploymentTopologyUpdateDate(topology.getLastUpdateDate());
        Map<String, NodeTemplate> previousNodeTemplates = deploymentTopology.getNodeTemplates();
        ReflectionUtil.mergeObject(topology, deploymentTopology, "id");
        topologyCompositionService.processTopologyComposition(deploymentTopology);
        deploymentInputService.processInputProperties(deploymentTopology);
        inputsPreProcessorService.processGetInput(deploymentTopology, environment, topology);
        deploymentInputService.processInputArtifacts(deploymentTopology);
        deploymentInputService.processProviderDeploymentProperties(deploymentTopology);
        deploymentNodeSubstitutionService.processNodesSubstitution(deploymentTopology, previousNodeTemplates);
        save(deploymentTopology);
    }

    /**
     * Update the deployment topology's input and save it. This should always be called when the deployment setup has changed
     *
     * @param deploymentTopology the the deployment topology
     */
    public void updateDeploymentTopologyInputsAndSave(DeploymentTopology deploymentTopology) {
        ApplicationEnvironment environment = appEnvironmentServices.getOrFail(deploymentTopology.getEnvironmentId());
        Topology topology = topologyServiceCore.getOrFail(deploymentTopology.getInitialTopologyId());
        deploymentInputService.processInputProperties(deploymentTopology);
        inputsPreProcessorService.processGetInput(deploymentTopology, environment, topology);
        deploymentInputService.processInputArtifacts(deploymentTopology);
        deploymentInputService.processProviderDeploymentProperties(deploymentTopology);
        save(deploymentTopology);
    }

    private LocationResourceTemplate getSubstitution(DeploymentTopology deploymentTopology, String nodeId) {
        if (MapUtils.isEmpty(deploymentTopology.getSubstitutedNodes())) {
            throw new NotFoundException("Topology does not have any node substituted");
        }
        String substitutionId = deploymentTopology.getSubstitutedNodes().get(nodeId);
        if (substitutionId == null) {
            throw new NotFoundException("Topology does not have the node <" + nodeId + "> substituted");
        }
        return locationResourceService.getOrFail(substitutionId);
    }

    public void updateSubstitutionProperty(DeploymentTopology deploymentTopology, String nodeId, String propertyName, Object propertyValue) {
        LocationResourceTemplate substitution = getSubstitution(deploymentTopology, nodeId);
        NodeTemplate substituted = deploymentTopology.getNodeTemplates().get(nodeId);
        locationResourceService.setTemplateProperty(substitution, propertyName, propertyValue);
        if (substituted.getProperties() == null) {
            substituted.setProperties(Maps.<String, AbstractPropertyValue> newHashMap());
        }
        substituted.getProperties().put(propertyName, substitution.getTemplate().getProperties().get(propertyName));
        updateDeploymentTopologyInputsAndSave(deploymentTopology);
    }

    public void updateSubstitutionCapabilityProperty(DeploymentTopology deploymentTopology, String nodeId, String capabilityName, String propertyName,
            Object propertyValue) throws ConstraintViolationException, ConstraintValueDoNotMatchPropertyTypeException {
        LocationResourceTemplate substitution = getSubstitution(deploymentTopology, nodeId);
        NodeTemplate substituted = deploymentTopology.getNodeTemplates().get(nodeId);
        locationResourceService.setTemplateCapabilityProperty(substitution, capabilityName, propertyName, propertyValue);
        Map<String, AbstractPropertyValue> substitutedCapabilityProperties = substituted.getCapabilities().get(capabilityName).getProperties();
        if (substitutedCapabilityProperties == null) {
            substitutedCapabilityProperties = Maps.newHashMap();
            substituted.getCapabilities().get(capabilityName).setProperties(substitutedCapabilityProperties);
        }
        substitutedCapabilityProperties.put(propertyName, substitution.getTemplate().getCapabilities().get(capabilityName).getProperties().get(propertyName));
        updateDeploymentTopologyInputsAndSave(deploymentTopology);
    }

    public void deleteByEnvironmentId(String environmentId) {
        alienDAO.delete(DeploymentTopology.class, QueryBuilders.termQuery("environmentId", environmentId));
    }

    /**
     * Set the location policies of a deployment
     *
     * @param environmentId the environment's id
     * @param groupsToLocations group to location mapping
     * @return the updated deployment topology
     */
    public DeploymentConfiguration setLocationPolicies(String environmentId, String orchestratorId, Map<String, String> groupsToLocations) {
        // Change of locations will trigger re-generation of deployment topology
        // Set to new locations and process generation of all default properties
        ApplicationEnvironment environment = appEnvironmentServices.getOrFail(environmentId);
        ApplicationVersion appVersion = appVersionService.getOrFail(environment.getCurrentVersionId());
        DeploymentTopology deploymentTopology = new DeploymentTopology();
        deploymentTopology.setOrchestratorId(orchestratorId);
        addLocationPolicies(deploymentTopology, groupsToLocations);
        Topology topology = topologyServiceCore.getOrFail(appVersion.getTopologyId());
        generateDeploymentTopology(DeploymentTopology.generateId(appVersion.getId(), environmentId), environment, topology, deploymentTopology);
        return getDeploymentConfiguration(deploymentTopology);
    }

    /**
     * Get location map from the deployment topology
     *
     * @param deploymentTopology the deploymentTopology
     * @return map of location group id to location
     */
    public Map<String, Location> getLocations(DeploymentTopology deploymentTopology) {
        Map<String, String> locationIds = TopologyLocationUtils.getLocationIdsOrFail(deploymentTopology);
        return getLocations(locationIds);
    }

    /**
     * Get location map from the deployment topology
     *
     * @param locationIds map of group id to location id
     * @return map of location group id to location
     */
    public Map<String, Location> getLocations(Map<String, String> locationIds) {
        Map<String, Location> locations = locationService.getMultiple(locationIds.values());
        Map<String, Location> locationMap = Maps.newHashMap();
        for (Map.Entry<String, String> locationIdsEntry : locationIds.entrySet()) {
            locationMap.put(locationIdsEntry.getKey(), locations.get(locationIdsEntry.getValue()));
        }
        if (locations.size() < locationIds.size()) {
            throw new NotFoundException("Some locations could not be found " + locationIds);
        }
        return locationMap;
    }

    /**
     * Get or create if not yet existing the {@link DeploymentTopology}
     *
     * @param environmentId environment's id
     * @return the existing deployment topology or new created one
     */
    private DeploymentTopology getOrCreateDeploymentTopology(String environmentId) {
        ApplicationEnvironment environment = appEnvironmentServices.getOrFail(environmentId);
        ApplicationVersion version = applicationVersionService.getOrFail(environment.getCurrentVersionId());
        return getOrCreateDeploymentTopology(environment, version.getTopologyId());
    }

    /**
     * Add location policies in the deploymentTopology
     *
     * @param deploymentTopology the deployment topology
     * @param groupsLocationsMapping the mapping group name to location policy
     */
    private void addLocationPolicies(DeploymentTopology deploymentTopology, Map<String, String> groupsLocationsMapping) {

        if (MapUtils.isEmpty(groupsLocationsMapping)) {
            return;
        }

        // TODO For now, we only support one location policy for all nodes. So we have a group _ALL that represents all compute nodes in the topology
        // To improve later on for multiple groups support
        // throw an exception if multiple location policies provided: not yet supported
        if (groupsLocationsMapping.size() > 1) {
            throw new UnsupportedOperationException("Multiple Location policies not yet supported");
        }

        for (Entry<String, String> matchEntry : groupsLocationsMapping.entrySet()) {
            String locationId = matchEntry.getValue();
            Location location = locationService.getOrFail(locationId);
            AuthorizationUtil.checkAuthorizationForLocation(location, DeployerRole.values());
            deploymentTopology.getLocationDependencies().addAll(location.getDependencies());
            LocationPlacementPolicy locationPolicy = new LocationPlacementPolicy(locationId);
            locationPolicy.setName("Location policy");
            // put matchEntry.getKey() instead for multi location support
            String groupName = AlienConstants.GROUP_ALL;
            Map<String, NodeGroup> groups = deploymentTopology.getLocationGroups();
            NodeGroup group = new NodeGroup();
            group.setName(groupName);
            group.setPolicies(Lists.<AbstractPolicy> newArrayList());
            group.getPolicies().add(locationPolicy);
            groups.put(groupName, group);
        }
    }

    /**
     * Get all deployment topology linked to a topology
     *
     * @param topologyId the topology id
     * @return all deployment topology that is linked to this topology
     */
    public DeploymentTopology[] getByTopologyId(String topologyId) {
        List<DeploymentTopology> deploymentTopologies = Lists.newArrayList();
        ApplicationVersion version = applicationVersionService.getByTopologyId(topologyId);
        if (version != null) {
            ApplicationEnvironment[] environments = applicationEnvironmentService.getByVersionId(version.getId());
            if (environments != null && environments.length > 0) {
                for (ApplicationEnvironment environment : environments) {
                    deploymentTopologies.add(getOrCreateDeploymentTopology(environment, version.getTopologyId()));
                }
            }
        }
        return deploymentTopologies.toArray(new DeploymentTopology[deploymentTopologies.size()]);
    }

    /**
     * Finalize the deployment topology processing and get it ready to deploy
     *
     * @param deploymentTopology
     * @return
     */
    public DeploymentTopology processForDeployment(DeploymentTopology deploymentTopology) {
        // if a property defined as getInput didn't found a value after processing, set it to null
        inputsPreProcessorService.setUnprocessedGetInputToNullValue(deploymentTopology);
        return deploymentTopology;
    }
}

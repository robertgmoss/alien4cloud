package alien4cloud.deployment;

import java.util.Map;

import javax.inject.Inject;

import org.springframework.stereotype.Service;

import alien4cloud.model.deployment.Deployment;
import alien4cloud.model.deployment.DeploymentTopology;
import alien4cloud.model.orchestrators.locations.Location;
import alien4cloud.paas.model.PaaSTopology;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import alien4cloud.paas.plan.TopologyTreeBuilderService;

/**
 * Utility to build the deployment context.
 */
@Service
public class DeploymentContextService {
    @Inject
    private TopologyTreeBuilderService topologyTreeBuilderService;

    /**
     * Build a topology deployment context from a given topology and deployment.
     *
     * @param deployment The deployment object.
     * @param topology The topology that will be processed.
     * @return A PaaSTopologyDeploymentContext that contians
     */
    public PaaSTopologyDeploymentContext buildTopologyDeploymentContext(Deployment deployment, Map<String, Location> locations, DeploymentTopology topology) {
        PaaSTopologyDeploymentContext topologyDeploymentContext = new PaaSTopologyDeploymentContext();
        topologyDeploymentContext.setLocations(locations);
        topologyDeploymentContext.setDeployment(deployment);
        PaaSTopology paaSTopology = topologyTreeBuilderService.buildPaaSTopology(topology);
        topologyDeploymentContext.setPaaSTopology(paaSTopology);
        topologyDeploymentContext.setDeploymentTopology(topology);
        topologyDeploymentContext.setDeployment(deployment);
        return topologyDeploymentContext;
    }
}
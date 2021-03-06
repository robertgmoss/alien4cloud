package alien4cloud.model.orchestrators.locations;

import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;
import alien4cloud.model.components.IndexedCapabilityType;
import alien4cloud.model.components.IndexedNodeType;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@Getter
@Setter
@ApiModel("Contains the types and templates of elements configured for a given location.")
public class LocationResources {
    @ApiModelProperty(value = "Map of node types id, node type used to configure a given location.")
    private Map<String, IndexedNodeType> configurationTypes;
    @ApiModelProperty(value = "Map of node types id, node type used to configure the templates of on-demand resources in a location.")
    private Map<String, IndexedNodeType> nodeTypes;
    @ApiModelProperty(value = "Map that contains the capability types used by the configuration types or node types.")
    private Map<String, IndexedCapabilityType> capabilityTypes;
    @ApiModelProperty(value = "List of configuration templates already configured for the location. Usually abstract  types.")
    private List<LocationResourceTemplate> configurationTemplates;
    @ApiModelProperty(value = "List of node templates already configured for the location.")
    private List<LocationResourceTemplate> nodeTemplates;
}